/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Adapters

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import androidx.collection.LongSparseArray
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import org.telegram.PhoneFormat.PhoneFormat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.Adapters.SearchAdapterHelper.SearchAdapterHelperDelegate
import org.telegram.ui.Cells.GraySectionCell
import org.telegram.ui.Cells.ProfileSearchCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import java.util.*

open class SearchAdapter(private val context: Context, private val ignoreUsers: LongSparseArray<User>?, private val allowUsernameSearch: Boolean, private val onlyMutual: Boolean, private val allowChats: Boolean, private val allowBots: Boolean, self: Boolean, phones: Boolean, searchChannelId: Int) : SelectionAdapter() {
	private val searchAdapterHelper: SearchAdapterHelper
	private val allowSelf: Boolean
	private val allowPhoneNumbers: Boolean
	private val channelId: Long
	private var searchResult = ArrayList<Any>()
	private var searchResultNames = ArrayList<CharSequence>()
	private var checkedMap: LongSparseArray<*>? = null
	private var searchTimer: Timer? = null
	private var useUserCell = false
	private var searchInProgress = false
	private var searchReqId = 0
	private var searchPointer = 0

	init {
		channelId = searchChannelId.toLong()
		allowSelf = self
		allowPhoneNumbers = phones
		searchAdapterHelper = SearchAdapterHelper(true)
		searchAdapterHelper.setDelegate(object : SearchAdapterHelperDelegate {
			override fun onDataSetChanged(searchId: Int) {
				notifyDataSetChanged()

				if (searchId != 0) {
					onSearchProgressChanged()
				}
			}

			override val excludeUsers: LongSparseArray<User>?
				get() = ignoreUsers
		})
	}

	fun setCheckedMap(map: LongSparseArray<*>?) {
		checkedMap = map
	}

	fun setUseUserCell(value: Boolean) {
		useUserCell = value
	}

	fun searchDialogs(query: String?) {
		try {
			searchTimer?.cancel()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		searchResult.clear()
		searchResultNames.clear()

		if (allowUsernameSearch) {
			searchAdapterHelper.queryServerSearch(null, true, allowChats, allowBots, allowSelf, false, channelId, 0, 0)
		}

		notifyDataSetChanged()

		if (!query.isNullOrEmpty()) {
			searchTimer = Timer().apply {
				schedule(object : TimerTask() {
					override fun run() {
						try {
							searchTimer?.cancel()
							searchTimer = null
						}
						catch (e: Exception) {
							FileLog.e(e)
						}

						processSearch(query)
					}
				}, 200, 300)
			}
		}
	}

	private fun processSearch(query: String) {
		AndroidUtilities.runOnUIThread {
			if (allowUsernameSearch) {
				searchAdapterHelper.queryServerSearch(query, true, allowChats, allowBots, allowSelf, false, channelId, -1, 1)
			}

			val currentAccount = UserConfig.selectedAccount
			val contactsCopy = ArrayList(ContactsController.getInstance(currentAccount).contacts)

			searchInProgress = true
			searchReqId = searchPointer++

			val searchReqIdFinal = searchReqId

			Utilities.searchQueue.postRunnable {
				val search1 = query.trim { it <= ' ' }.lowercase(Locale.getDefault())

				if (search1.isEmpty()) {
					updateSearchResults(searchReqIdFinal, ArrayList(), ArrayList())
					return@postRunnable
				}

				var search2 = LocaleController.getInstance().getTranslitString(search1)

				if (search1 == search2 || search2.isNullOrEmpty()) {
					search2 = null
				}

				val search = arrayOfNulls<String>(1 + if (search2 != null) 1 else 0)

				search[0] = search1

				if (search2 != null) {
					search[1] = search2
				}

				val resultArray = ArrayList<Any>()
				val resultArrayNames = ArrayList<CharSequence>()

				for (a in contactsCopy.indices) {
					val contact = contactsCopy[a]
					val user = MessagesController.getInstance(currentAccount).getUser(contact.user_id) ?: continue

					if (!allowSelf && user.self || onlyMutual && !user.mutual_contact || ignoreUsers != null && ignoreUsers.indexOfKey(contact.user_id) >= 0) {
						continue
					}

					val names = arrayOfNulls<String>(3)
					names[0] = ContactsController.formatName(user.first_name, user.last_name).lowercase(Locale.getDefault())
					names[1] = LocaleController.getInstance().getTranslitString(names[0])

					if (names[0] == names[1]) {
						names[1] = null
					}

					if (UserObject.isReplyUser(user)) {
						names[2] = context.getString(R.string.RepliesTitle).lowercase(Locale.getDefault())
					}
					else if (user.self) {
						names[2] = context.getString(R.string.SavedMessages).lowercase(Locale.getDefault())
					}

					var found = 0

					for (q in search) {
						if (q.isNullOrEmpty()) {
							continue
						}

						for (name in names) {
							if (name != null && (name.startsWith(q) || name.contains(" $q"))) {
								found = 1
								break
							}
						}

						if (found == 0 && user.username?.startsWith(q) == true) {
							found = 2
						}

						if (found != 0) {
							if (found == 1) {
								resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q))
							}
							else {
								resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@$q"))
							}

							resultArray.add(user)

							break
						}
					}
				}

				updateSearchResults(searchReqIdFinal, resultArray, resultArrayNames)
			}
		}
	}

	private fun updateSearchResults(searchReqIdFinal: Int, users: ArrayList<Any>, names: ArrayList<CharSequence>) {
		AndroidUtilities.runOnUIThread {
			if (searchReqIdFinal == searchReqId) {
				searchResult = users
				searchResultNames = names
				searchAdapterHelper.mergeResults(users)
				searchInProgress = false
				notifyDataSetChanged()
				onSearchProgressChanged()
			}
		}
	}

	protected open fun onSearchProgressChanged() {
		// unused
	}

	fun searchInProgress(): Boolean {
		return searchInProgress || searchAdapterHelper.isSearchInProgress
	}

	override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
		val type = holder.itemViewType
		return type == 0 || type == 2
	}

	override fun getItemCount(): Int {
		var count = searchResult.size

		val globalCount = searchAdapterHelper.globalSearch.size

		if (globalCount != 0) {
			count += globalCount + 1
		}

		return count
	}

	fun isGlobalSearch(i: Int): Boolean {
		val localCount = searchResult.size
		val globalCount = searchAdapterHelper.globalSearch.size

		return if (i in 0 until localCount) {
			false
		}
		else {
			i > localCount && i <= globalCount + localCount
		}
	}

	fun getItem(i: Int): Any? {
		@Suppress("NAME_SHADOWING") var i = i
		val localCount = searchResult.size
		val globalCount = searchAdapterHelper.globalSearch.size

		if (i in 0 until localCount) {
			return searchResult[i]
		}
		else {
			i -= localCount



			if (i in 1..globalCount) {
				return searchAdapterHelper.globalSearch[i - 1]
			}
		}

		return null
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val view: View

		when (viewType) {
			0 -> if (useUserCell) {
				view = UserCell(context, 1, 1, false)

				if (checkedMap != null) {
					view.setChecked(checked = false, animated = false)
				}
			}
			else {
				view = ProfileSearchCell(context)
			}

			1 -> {
				view = GraySectionCell(context)
			}

			2 -> {
				view = TextCell(context, leftPadding = 16)
			}

			else -> {
				view = TextCell(context, leftPadding = 16)
			}
		}

		return RecyclerListView.Holder(view)
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (holder.itemViewType) {
			0 -> {
				val `object` = getItem(position) as TLObject?

				if (`object` != null) {
					var id: Long = 0
					var un: String? = null
					var self = false

					if (`object` is User) {
						un = `object`.username
						id = `object`.id
						self = `object`.self
					}
					else if (`object` is Chat) {
						un = `object`.username
						id = `object`.id
					}

					var username: CharSequence? = null
					var name: CharSequence? = null

					if (position < searchResult.size) {
						name = searchResultNames.getOrNull(position)

						if (name != null && !un.isNullOrEmpty()) {
							if (name.toString().startsWith("@$un")) {
								username = name
								name = null
							}
						}
					}
					else if (position > searchResult.size && un != null) {
						var foundUserName = searchAdapterHelper.lastFoundUsername

						if (foundUserName?.startsWith("@") == true) {
							foundUserName = foundUserName.substring(1)
						}

						try {
							var index = 0
							val spannableStringBuilder = SpannableStringBuilder()
							spannableStringBuilder.append("@")
							spannableStringBuilder.append(un)

							if (foundUserName != null && AndroidUtilities.indexOfIgnoreCase(un, foundUserName).also { index = it } != -1) {
								var len = foundUserName.length

								if (index == 0) {
									len++
								}
								else {
									index++
								}

								// spannableStringBuilder.setSpan(ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
								spannableStringBuilder.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
							}

							username = spannableStringBuilder
						}
						catch (e: Exception) {
							username = un
							FileLog.e(e)
						}
					}

					if (useUserCell) {
						val userCell = holder.itemView as UserCell
						userCell.setData(`object`, name, username, 0)

						checkedMap?.run {
							userCell.setChecked(indexOfKey(id) >= 0, false)
						}
					}
					else {
						val profileSearchCell = holder.itemView as ProfileSearchCell

						if (self) {
							name = context.getString(R.string.SavedMessages)
						}

						profileSearchCell.setData(`object`, null, name, username, false, self)
						profileSearchCell.useSeparator = position != itemCount - 1 && position != searchResult.size - 1
					}
				}
			}

			1 -> {
				val cell = holder.itemView as GraySectionCell

				if (getItem(position) == null) {
					cell.setText(context.getString(R.string.GlobalSearch))
				}
				else {
					cell.setText(context.getString(R.string.PhoneNumberSearch))
				}
			}

			2 -> {
				val str = getItem(position) as String?
				val cell = holder.itemView as TextCell
				cell.setColors(null, ResourcesCompat.getColor(context.resources, R.color.brand, null))
				cell.setText(context.getString(R.string.AddContactByPhone, PhoneFormat.getInstance().format("+$str")), false)
			}
		}
	}

	override fun getItemViewType(i: Int): Int {
		val item = getItem(i)

		if (item == null) {
			return 1
		}
		else if (item is String) {
			return if ("section" == item) {
				1
			}
			else {
				2
			}
		}

		return 0
	}
}
