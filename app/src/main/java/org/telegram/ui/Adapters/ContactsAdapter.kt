/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.collection.LongSparseArray
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.expires
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DividerCell
import org.telegram.ui.Cells.LetterSectionCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.Components.CombinedDrawable
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SectionsAdapter

open class ContactsAdapter(private val context: Context, private val onlyUsers: Int, private val ignoreUsers: LongSparseArray<User>?, flags: Int, private val hasGps: Boolean, private var onClickListener: (View) -> Unit = {}) : SectionsAdapter() {
	private val currentAccount = UserConfig.selectedAccount
	private val isAdmin = flags != 0
	private val isChannel = flags == 2
	private var checkedMap: LongSparseArray<*>? = null
	private var onlineContacts: MutableList<TLRPC.TLContact>? = null
	private var scrolling = false
	private var sortType = SORT_TYPE_DEFAULT
	private var disableSections = false
	private var isEmpty = false

	fun setOnClickListener(item: (View) -> Unit) {
		onClickListener = item
	}

	fun setDisableSections(value: Boolean) {
		disableSections = value
	}

	fun setSortType(value: Int, force: Boolean) {
		sortType = value

		if (sortType == SORT_TYPE_LAST_SEEN) {
			if (onlineContacts.isNullOrEmpty() || force) {
				onlineContacts = ContactsController.getInstance(currentAccount).contacts.also {
					val selfId = UserConfig.getInstance(currentAccount).clientUserId
					it.removeAll { user -> user.userId == selfId }
				}.toMutableList()
			}

			sortOnlineContacts()
		}
		else {
			notifyDataSetChanged()
		}
	}

	fun sortOnlineContacts() {
		if (onlineContacts == null) {
			return
		}

		try {
			val currentTime = ConnectionsManager.getInstance(currentAccount).currentTime
			val messagesController = MessagesController.getInstance(currentAccount)

			onlineContacts?.sortWith { o1, o2 ->
				val user1 = messagesController.getUser(o2.userId) as? TLRPC.TLUser
				val user2 = messagesController.getUser(o1.userId) as? TLRPC.TLUser
				var status1 = 0
				var status2 = 0

				if (user1 != null) {
					if (user1.isSelf) {
						status1 = currentTime + 50000
					}
					else if (user1.status != null) {
						status1 = user1.status?.expires ?: 0
					}
				}

				if (user2 != null) {
					if (user2.isSelf) {
						status2 = currentTime + 50000
					}
					else if (user2.status != null) {
						status2 = user2.status?.expires ?: 0
					}
				}

				if (status1 > 0 && status2 > 0) {
					if (status1 > status2) {
						return@sortWith 1
					}
					else if (status1 < status2) {
						return@sortWith -1
					}

					return@sortWith 0
				}
				else if (status1 < 0 && status2 < 0) {
					if (status1 > status2) {
						return@sortWith 1
					}
					else if (status1 < status2) {
						return@sortWith -1
					}

					return@sortWith 0
				}
				else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
					return@sortWith -1
				}
				else if (status2 < 0 && status1 > 0 || status2 == 0 && status1 != 0) {
					return@sortWith 1
				}

				0
			}

			notifyDataSetChanged()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	fun setCheckedMap(map: LongSparseArray<*>?) {
		checkedMap = map
	}

	fun setIsScrolling(value: Boolean) {
		scrolling = value
	}

	override fun getItem(section: Int, position: Int): Any? {
		val usersSectionsDict = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).usersMutualSectionsDict
		}
		else {
			ContactsController.getInstance(currentAccount).usersSectionsDict
		}

		val sortedUsersSectionsArray = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray
		}
		else {
			ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
		}

		if (onlyUsers != 0 && !isAdmin) {
			if (section < sortedUsersSectionsArray.size) {
				val arr = usersSectionsDict[sortedUsersSectionsArray[section]]!!

				if (position < arr.size) {
					return MessagesController.getInstance(currentAccount).getUser(arr[position].userId)
				}
			}

			return null
		}
		else {
			if (section == 0) {
				return null
			}
			else {
				if (sortType == SORT_TYPE_LAST_SEEN) {
					if (section == 1) {
						return if (position < (onlineContacts?.size ?: 0)) {
							MessagesController.getInstance(currentAccount).getUser(onlineContacts!![position].userId)
						}
						else {
							null
						}
					}
				}
				else {
					if (section - 1 < sortedUsersSectionsArray.size) {
						val arr = usersSectionsDict[sortedUsersSectionsArray[section - 1]]!!

						return if (position < arr.size) {
							MessagesController.getInstance(currentAccount).getUser(arr[position].userId)
						}
						else {
							null
						}
					}
				}
			}
		}

		return null
	}

	override fun isEnabled(holder: RecyclerView.ViewHolder, section: Int, row: Int): Boolean {
		val usersSectionsDict = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).usersMutualSectionsDict
		}
		else {
			ContactsController.getInstance(currentAccount).usersSectionsDict
		}

		val sortedUsersSectionsArray = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray
		}
		else {
			ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
		}

		if (onlyUsers != 0 && !isAdmin) {
			if (isEmpty) {
				return false
			}

			val arr = usersSectionsDict[sortedUsersSectionsArray[section]]!!

			return row < arr.size
		}
		else {
			if (section == 0) {
				return if (isAdmin) {
					row != 1
				}
				else {
					row != 3
				}
			}
			else {
				if (isEmpty) {
					return false
				}

				if (sortType == SORT_TYPE_LAST_SEEN) {
					if (section == 1) {
						return row < (onlineContacts?.size ?: 0)
					}
				}
				else {
					if (section - 1 < sortedUsersSectionsArray.size) {
						val arr = usersSectionsDict[sortedUsersSectionsArray[section - 1]]!!
						return row < arr.size
					}
				}
			}
		}

		return true
	}

	override fun getSectionCount(): Int {
		var count: Int

		isEmpty = false

		if (sortType == SORT_TYPE_LAST_SEEN) {
			count = 1
			isEmpty = onlineContacts.isNullOrEmpty()
		}
		else {
			val sortedUsersSectionsArray = if (onlyUsers == 2) {
				ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray
			}
			else {
				ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
			}

			count = sortedUsersSectionsArray.size

			if (count == 0) {
				isEmpty = true
				// count = 1 // MARK: I don't know WTF this was 1, but because of it
				// MARK: contacts screen was not showing empty view
				// MARK: when contacts were empty
			}
		}

		if (onlyUsers == 0) {
			count++
		}

		if (isAdmin) {
			count++
		}

		return count
	}

	override fun getCountForSection(section: Int): Int {
		val usersSectionsDict = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).usersMutualSectionsDict
		}
		else {
			ContactsController.getInstance(currentAccount).usersSectionsDict
		}

		val sortedUsersSectionsArray = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray
		}
		else {
			ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
		}

		if (onlyUsers != 0 && !isAdmin) {
			if (isEmpty) {
				return 0
			}

			if (section < sortedUsersSectionsArray.size) {
				val arr = sortedUsersSectionsArray.getOrNull(section)?.let { usersSectionsDict[it] }
				val count = arr?.size ?: 0

				if (section != sortedUsersSectionsArray.size - 1) {
					count - 1
				}

				return count
			}
		}
		else {
			if (section == 0) {
				return if (isAdmin) {
					2
				}
				else {
					4
				}
			}
			else {
				if (isEmpty) {
					return 1
				}

				if (sortType == SORT_TYPE_LAST_SEEN) {
					if (section == 1) {
						return if (onlineContacts!!.isEmpty()) 0 else onlineContacts!!.size + 1
					}
				}
				else {
					if (section - 1 < sortedUsersSectionsArray.size) {
						val arr = usersSectionsDict[sortedUsersSectionsArray[section - 1]]!!
						var count = arr.size

						if (section - 1 != sortedUsersSectionsArray.size - 1) {
							count++
						}

						return count
					}
				}
			}
		}

		return 0
	}

//	override fun getItemCount() = (0..getSectionCount())
//		.reduce { _, section -> getCountForSection(section) }

	override fun getSectionHeaderView(section: Int, view: View?): View {
		@Suppress("NAME_SHADOWING") var view = view

		val sortedUsersSectionsArray = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray
		}
		else {
			ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
		}

		if (view == null) {
			view = LetterSectionCell(context)
		}

		val cell = view as LetterSectionCell

		if (sortType == SORT_TYPE_LAST_SEEN || disableSections || isEmpty) {
			cell.setLetter("")
		}
		else {
			if (onlyUsers != 0 && !isAdmin) {
				if (section < sortedUsersSectionsArray.size) {
					cell.setLetter(sortedUsersSectionsArray[section])
				}
				else {
					cell.setLetter("")
				}
			}
			else {
				if (section == 0) {
					cell.setLetter("")
				}
				else if (section - 1 < sortedUsersSectionsArray.size) {
					cell.setLetter(sortedUsersSectionsArray[section - 1])
				}
				else {
					cell.setLetter("")
				}
			}
		}

		return view
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val view: View
		when (viewType) {
			0 -> {
				view = UserCell(context, 58, 1, false)
			}

			1 -> {
				view = TextCell(context, large = true, fullDivider = true)
			}

			2 -> {
				view = LayoutInflater.from(context).inflate(R.layout.item_invite_others_to_chat, parent, false)
			}

			3 -> {
				view = DividerCell(context = context, padding = 0f, shouldDraw = false)
				// view.setPadding(AndroidUtilities.dp(if (LocaleController.isRTL) 28f else 72f), AndroidUtilities.dp(8f), AndroidUtilities.dp(if (LocaleController.isRTL) 72f else 28f), AndroidUtilities.dp(8f))
			}

			4 -> {
				view = object : FrameLayout(context) {
					override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
						var height = MeasureSpec.getSize(heightMeasureSpec)

						if (height == 0) {
							height = parent.measuredHeight
						}

						if (height == 0) {
							height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight
						}

						val cellHeight = AndroidUtilities.dp(50f)
						var totalHeight = if (onlyUsers != 0) 0 else cellHeight + AndroidUtilities.dp(30f)

						if (hasGps) {
							totalHeight += cellHeight
						}

						if (!isAdmin) {
							totalHeight += cellHeight
						}

						height = if (totalHeight < height) {
							height - totalHeight
						}
						else {
							0
						}

						super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
					}
				}
			}

			5 -> {
				view = LayoutInflater.from(context).inflate(R.layout.item_invite_others_to_chat, parent, false)
			}

			else -> {
				view = ShadowSectionCell(context)

				val drawable = Theme.getThemedDrawable(context, R.drawable.greydivider, context.getColor(R.color.shadow))
				val combinedDrawable = CombinedDrawable(context.getColor(R.color.light_background).toDrawable(), drawable)
				combinedDrawable.setFullSize(true)
				view.setBackground(combinedDrawable)
			}
		}

		return RecyclerListView.Holder(view)
	}

	override fun onBindViewHolder(section: Int, position: Int, holder: RecyclerView.ViewHolder) {
		when (holder.itemViewType) {
			0 -> {
				val userCell = holder.itemView as UserCell
				userCell.setAvatarPadding(if (sortType == SORT_TYPE_LAST_SEEN || disableSections) 6 else 58)

				val arr = if (sortType == SORT_TYPE_LAST_SEEN) {
					onlineContacts
				}
				else {
					val usersSectionsDict = if (onlyUsers == 2) {
						ContactsController.getInstance(currentAccount).usersMutualSectionsDict
					}
					else {
						ContactsController.getInstance(currentAccount).usersSectionsDict
					}

					val sortedUsersSectionsArray = if (onlyUsers == 2) {
						ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray
					}
					else {
						ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
					}

					usersSectionsDict[sortedUsersSectionsArray[section - (if (onlyUsers != 0 && !isAdmin) 0 else 1)]]
				}

				val user = MessagesController.getInstance(currentAccount).getUser(arr?.getOrNull(position)?.userId)
				userCell.setData(user, null, null, 0, true)

				if (checkedMap != null) {
					userCell.setChecked(checkedMap!!.indexOfKey(user?.id ?: Long.MIN_VALUE) >= 0, !scrolling)
				}

				if (ignoreUsers != null) {
					if (ignoreUsers.indexOfKey(user?.id ?: Long.MIN_VALUE) >= 0) {
						userCell.alpha = 0.5f
					}
					else {
						userCell.alpha = 1.0f
					}
				}
			}

			1 -> {
				val textCell = holder.itemView as TextCell
				textCell.setTextSize(15)

				if (section == 0) {
					if (isAdmin) {
						if (isChannel) {
							textCell.setTextAndIcon(textCell.context.getString(R.string.ChannelInviteViaLink), R.drawable.msg_link2, false)
						}
						else {
							textCell.setTextAndIcon(textCell.context.getString(R.string.InviteToGroupByLink), R.drawable.msg_link2, false)
						}
					}
					else {
						when (position) {
							0 -> {
								// textCell.setBold(true)
								textCell.setTextSize(16)
								textCell.setTextAndIcon(textCell.context.getString(R.string.NewGroup), R.drawable.ic_new_group, true)
							}

							1 -> {
								// MARK: this was original code for secret chats
								// textCell.setTextAndIcon(LocaleController.getString("NewSecretChat", R.string.NewSecretChat), R.drawable.msg_secret, false)

								// textCell.setBold(true)
								textCell.setTextSize(16)
								textCell.setTextAndIcon(textCell.context.getString(R.string.NewChannel), R.drawable.ic_new_channel, true)
							}

							2 -> {
								// textCell.setBold(true)
								textCell.setTextSize(16)
								textCell.setTextAndIcon(textCell.context.getString(R.string.masterclass), R.drawable.ic_online_course, false)

								// textCell.setTextAndIcon(LocaleController.getString("NewChannel", R.string.NewChannel), R.drawable.msg_channel, false)
							}
						}
					}
				}
				else {
					// MARK: this can't be used anymore because we do not have access to phone book
//					val contact = ContactsController.getInstance(currentAccount).phoneBookContacts[position]
//
//					if (contact.firstName != null && contact.lastName != null) {
//						textCell.setText(contact.firstName + " " + contact.lastName, false)
//					}
//					else if (contact.firstName != null && contact.lastName == null) {
//						textCell.setText(contact.firstName, false)
//					}
//					else {
//						textCell.setText(contact.lastName, false)
//					}
				}
			}

			2 -> {
				val btInviteOthers = holder.itemView.findViewById<MaterialButton>(R.id.bt_invite_others)
				val tvTitle = holder.itemView.findViewById<TextView>(R.id.tv_title)
				val tvContacts = holder.itemView.findViewById<TextView>(R.id.tv_contacts)

				when (sortType) {
					SORT_TYPE_DEFAULT -> {
						btInviteOthers.setOnClickListener {
							onClickListener(it)
						}
					}

					SORT_TYPE_NAME -> {
						tvTitle.text = context.getString(R.string.SortedByName)
						btInviteOthers.gone()
						tvContacts.gone()
					}

					else -> {
						tvTitle.text = context.getString(R.string.SortedByLastSeen)
						btInviteOthers.gone()
						tvContacts.gone()
					}
				}
			}

			5 -> {
				val btInviteOthers = holder.itemView.findViewById<MaterialButton>(R.id.bt_invite_others)

				val tvContacts = holder.itemView.findViewById<TextView>(R.id.tv_contacts)
				tvContacts.gone()

				btInviteOthers.setOnClickListener {
					onClickListener(it)
				}
			}
		}
	}

	override fun getItemViewType(section: Int, position: Int): Int {
		val usersSectionsDict = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).usersMutualSectionsDict
		}
		else {
			ContactsController.getInstance(currentAccount).usersSectionsDict
		}

		val sortedUsersSectionsArray = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray
		}
		else {
			ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
		}

		if (onlyUsers != 0 && !isAdmin) {
			if (isEmpty) {
				return 4
			}

			val arr = usersSectionsDict[sortedUsersSectionsArray[section]]!!

			return if (position < arr.size) 0 else 3
		}
		else {
			if (section == 0) {
				if (isAdmin) {
					if (position == 1) {
						return 2
					}
				}
				else if (position == 3) {
					return if (isEmpty) 5 else 2
				}
			}
			else {
				if (isEmpty) {
					return 4
				}

				if (sortType == SORT_TYPE_LAST_SEEN) {
					if (section == 1) {
						return if (position < onlineContacts!!.size) 0 else 3
					}
				}
				else {
					if (section - 1 < sortedUsersSectionsArray.size) {
						val arr = usersSectionsDict[sortedUsersSectionsArray[section - 1]]!!
						return if (position < arr.size) 0 else 3
					}
				}
			}
		}

		return 1
	}

	override fun getLetter(position: Int): String? {
		if (sortType == SORT_TYPE_LAST_SEEN || isEmpty) {
			return null
		}

		val sortedUsersSectionsArray = if (onlyUsers == 2) {
			ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray
		}
		else {
			ContactsController.getInstance(currentAccount).sortedUsersSectionsArray
		}

		var section = getSectionForPosition(position)

		if (section == -1) {
			section = sortedUsersSectionsArray.size - 1
		}

		if (onlyUsers != 0 && !isAdmin) {
			if (section >= 0 && section < sortedUsersSectionsArray.size) {
				return sortedUsersSectionsArray[section]
			}
		}
		else {
			if (section > 0 && section <= sortedUsersSectionsArray.size) {
				return sortedUsersSectionsArray[section - 1]
			}
		}

		return null
	}

	override fun getPositionForScrollProgress(listView: RecyclerListView, progress: Float, position: IntArray) {
		position[0] = (itemCount * progress).toInt()
		position[1] = 0
	}

	companion object {
		const val SORT_TYPE_DEFAULT = 0
		const val SORT_TYPE_NAME = 1
		const val SORT_TYPE_LAST_SEEN = 2
	}
}
