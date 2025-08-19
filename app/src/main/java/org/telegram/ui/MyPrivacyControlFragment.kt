/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_CALLS
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_FORWARDS
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_INVITE
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_LAST_SEEN
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_PHOTO
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TLAccountPrivacyRules
import org.telegram.tgnet.TLRPC.TLAccountSetPrivacy
import org.telegram.tgnet.TLRPC.TLInputPrivacyKeyChatInvite
import org.telegram.tgnet.TLRPC.TLInputPrivacyKeyForwards
import org.telegram.tgnet.TLRPC.TLInputPrivacyKeyPhoneCall
import org.telegram.tgnet.TLRPC.TLInputPrivacyKeyProfilePhoto
import org.telegram.tgnet.TLRPC.TLInputPrivacyKeyStatusTimestamp
import org.telegram.tgnet.TLRPC.TLInputPrivacyValueAllowAll
import org.telegram.tgnet.TLRPC.TLInputPrivacyValueAllowChatParticipants
import org.telegram.tgnet.TLRPC.TLInputPrivacyValueAllowContacts
import org.telegram.tgnet.TLRPC.TLInputPrivacyValueAllowUsers
import org.telegram.tgnet.TLRPC.TLInputPrivacyValueDisallowAll
import org.telegram.tgnet.TLRPC.TLInputPrivacyValueDisallowChatParticipants
import org.telegram.tgnet.TLRPC.TLInputPrivacyValueDisallowUsers
import org.telegram.tgnet.TLRPC.TLPrivacyValueAllowAll
import org.telegram.tgnet.TLRPC.TLPrivacyValueAllowChatParticipants
import org.telegram.tgnet.TLRPC.TLPrivacyValueAllowUsers
import org.telegram.tgnet.TLRPC.TLPrivacyValueDisallowAll
import org.telegram.tgnet.TLRPC.TLPrivacyValueDisallowChatParticipants
import org.telegram.tgnet.TLRPC.TLPrivacyValueDisallowUsers
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Cells.CheckmarkCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter

class MyPrivacyControlFragment @JvmOverloads constructor(private val rulesType: Int, load: Boolean = false) : BaseFragment(), NotificationCenterDelegate {
	private var listAdapter: ListAdapter? = null
	private var listView: RecyclerListView? = null
	private val currentPlus = mutableListOf<Long>()
	private val currentMinus = mutableListOf<Long>()
	private var currentType = 0
	private var sectionRow = 0
	private var everybodyRow = 0
	private var myContactsRow = 0
	private var nobodyRow = 0
	private var detailRow = 0
	private var rowCount = 0

	init {
		if (load) {
			ContactsController.getInstance(currentAccount).loadPrivacySettings()
		}
	}

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()
		checkPrivacy()
		updateRows(false)
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.privacyRulesUpdated)
		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.privacyRulesUpdated)
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		when (rulesType) {
			PRIVACY_RULES_TYPE_FORWARDS -> {
				actionBar?.setTitle(LocaleController.getString("PrivacyForwards", R.string.PrivacyForwards))
			}

			PRIVACY_RULES_TYPE_PHOTO -> {
				actionBar?.setTitle(LocaleController.getString("PrivacyProfilePhoto", R.string.PrivacyProfilePhoto))
			}

			PRIVACY_RULES_TYPE_CALLS -> {
				actionBar?.setTitle(LocaleController.getString("Calls", R.string.Calls))
			}

			PRIVACY_RULES_TYPE_INVITE -> {
				actionBar?.setTitle(LocaleController.getString("GroupsAndChannels", R.string.GroupsAndChannels))
			}

			PRIVACY_RULES_TYPE_LAST_SEEN -> {
				actionBar?.setTitle(LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen))
			}
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		listAdapter = ListAdapter(context)

		fragmentView = FrameLayout(context)

		val frameLayout = fragmentView as FrameLayout
		frameLayout.setBackgroundResource(R.color.light_background)

		listView = RecyclerListView(context)
		listView?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		listView?.isVerticalScrollBarEnabled = false

		(listView?.itemAnimator as? DefaultItemAnimator)?.setDelayAnimations(false)

		frameLayout.addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.adapter = listAdapter

		listView?.setOnItemClickListener(RecyclerListView.OnItemClickListener { _, position ->
			if (position == nobodyRow || position == everybodyRow || position == myContactsRow) {
				val newType = when (position) {
					nobodyRow -> TYPE_NOBODY
					everybodyRow -> TYPE_EVERYBODY
					else -> TYPE_CONTACTS
				}

				if (newType == currentType) {
					return@OnItemClickListener
				}

				currentType = newType

				updateRows(true)

				applyCurrentPrivacySettings()
			}
		})

		return fragmentView
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.privacyRulesUpdated -> checkPrivacy()
			NotificationCenter.emojiLoaded -> listView?.invalidateViews()
		}
	}

	private fun applyCurrentPrivacySettings() {
		val req = TLAccountSetPrivacy()

		when (rulesType) {
			PRIVACY_RULES_TYPE_FORWARDS -> {
				req.key = TLInputPrivacyKeyForwards()
			}

			PRIVACY_RULES_TYPE_PHOTO -> {
				req.key = TLInputPrivacyKeyProfilePhoto()
			}

			PRIVACY_RULES_TYPE_CALLS -> {
				req.key = TLInputPrivacyKeyPhoneCall()
			}

			PRIVACY_RULES_TYPE_INVITE -> {
				req.key = TLInputPrivacyKeyChatInvite()
			}

			PRIVACY_RULES_TYPE_LAST_SEEN -> {
				req.key = TLInputPrivacyKeyStatusTimestamp()
			}
		}

		if (currentType != 0 && currentPlus.isNotEmpty()) {
			val usersRule = TLInputPrivacyValueAllowUsers()
			val chatsRule = TLInputPrivacyValueAllowChatParticipants()

			for (a in currentPlus.indices) {
				val id = currentPlus[a]

				if (DialogObject.isUserDialog(id)) {
					val user = MessagesController.getInstance(currentAccount).getUser(id)

					if (user != null) {
						val inputUser = MessagesController.getInstance(currentAccount).getInputUser(user)

						if (inputUser != null) {
							usersRule.users.add(inputUser)
						}
					}
				}
				else {
					chatsRule.chats.add(-id)
				}
			}

			req.rules.add(usersRule)
			req.rules.add(chatsRule)
		}

		if (currentType != 1 && currentMinus.size > 0) {
			val usersRule = TLInputPrivacyValueDisallowUsers()
			val chatsRule = TLInputPrivacyValueDisallowChatParticipants()

			for (a in currentMinus.indices) {
				val id = currentMinus[a]

				if (DialogObject.isUserDialog(id)) {
					val user = messagesController.getUser(id)

					if (user != null) {
						val inputUser = messagesController.getInputUser(user)

						if (inputUser != null) {
							usersRule.users.add(inputUser)
						}
					}
				}
				else {
					chatsRule.chats.add(-id)
				}
			}

			req.rules.add(usersRule)
			req.rules.add(chatsRule)
		}

		when (currentType) {
			TYPE_EVERYBODY -> req.rules.add(TLInputPrivacyValueAllowAll())
			TYPE_NOBODY -> req.rules.add(TLInputPrivacyValueDisallowAll())
			TYPE_CONTACTS -> req.rules.add(TLInputPrivacyValueAllowContacts())
		}

		var progressDialog: AlertDialog? = null

		parentActivity?.let {
			progressDialog = AlertDialog(it, 3)
			progressDialog?.setCanCancel(false)
			progressDialog?.show()
		}

		val progressDialogFinal = progressDialog

		ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				try {
					progressDialogFinal?.dismiss()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				if (error == null) {
					val privacyRules = response as TLAccountPrivacyRules
					MessagesController.getInstance(currentAccount).putUsers(privacyRules.users, false)
					MessagesController.getInstance(currentAccount).putChats(privacyRules.chats, false)
					ContactsController.getInstance(currentAccount).setPrivacyRules(privacyRules.rules, rulesType)

					// finishFragment()
				}
				else {
					showErrorAlert()
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun showErrorAlert() {
		val parentActivity = parentActivity ?: return

		val builder = AlertDialog.Builder(parentActivity)
		builder.setTitle(parentActivity.getString(R.string.AppName))
		builder.setMessage(parentActivity.getString(R.string.PrivacyFloodControlError))
		builder.setPositiveButton(parentActivity.getString(R.string.OK), null)

		showDialog(builder.create())
	}

	private fun checkPrivacy() {
		currentPlus.clear()
		currentMinus.clear()

		val privacyRules = ContactsController.getInstance(currentAccount).getPrivacyRules(rulesType)

		if (privacyRules.isNullOrEmpty()) {
			currentType = TYPE_NOBODY
		}
		else {
			var type = -1

			for (a in privacyRules.indices) {
				val rule = privacyRules[a]

				if (rule is TLPrivacyValueAllowChatParticipants) {
					var b = 0
					val n = rule.chats.size

					while (b < n) {
						currentPlus.add(-rule.chats[b])
						b++
					}
				}
				else if (rule is TLPrivacyValueDisallowChatParticipants) {
					var b = 0
					val n = rule.chats.size

					while (b < n) {
						currentMinus.add(-rule.chats[b])
						b++
					}
				}
				else if (rule is TLPrivacyValueAllowUsers) {
					currentPlus.addAll(rule.users)
				}
				else if (rule is TLPrivacyValueDisallowUsers) {
					currentMinus.addAll(rule.users)
				}
				else if (type == -1) {
					type = when (rule) {
						is TLPrivacyValueAllowAll -> 0
						is TLPrivacyValueDisallowAll -> 1
						else -> 2
					}
				}
			}

			if (type == TYPE_EVERYBODY || type == -1 && currentMinus.isNotEmpty()) {
				currentType = TYPE_EVERYBODY
			}
			else if (type == TYPE_CONTACTS || type == -1 && currentMinus.isNotEmpty() && currentPlus.isNotEmpty()) {
				currentType = TYPE_CONTACTS
			}
			else if (type == TYPE_NOBODY || type == -1 && currentPlus.isNotEmpty()) {
				currentType = TYPE_NOBODY
			}
		}

		updateRows(false)
	}

	private fun updateRows(animated: Boolean) {
		rowCount = 0

		sectionRow = rowCount++
		everybodyRow = rowCount++
		myContactsRow = rowCount++

		nobodyRow = if (rulesType != PRIVACY_RULES_TYPE_LAST_SEEN && rulesType != PRIVACY_RULES_TYPE_CALLS && rulesType != PRIVACY_RULES_TYPE_FORWARDS) {
			-1
		}
		else {
			rowCount++
		}

		detailRow = rowCount++

		if (listAdapter != null) {
			if (animated) {
				val count = listView?.childCount ?: 0

				for (a in 0 until count) {
					val child = listView?.getChildAt(a) as? CheckmarkCell ?: continue
					val holder = listView?.findContainingViewHolder(child) ?: continue
					val position = holder.adapterPosition

					if (position == everybodyRow || position == myContactsRow || position == nobodyRow) {
						val checkedType = when (position) {
							everybodyRow -> TYPE_EVERYBODY
							myContactsRow -> TYPE_CONTACTS
							nobodyRow -> TYPE_NOBODY
							else -> throw IllegalArgumentException("Invalid position")
						}

						child.setChecked(currentType == checkedType, true)
					}
				}
			}
			else {
				listAdapter?.notifyDataSetChanged()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		listAdapter?.notifyDataSetChanged()
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val position = holder.adapterPosition
			return position == nobodyRow || position == everybodyRow || position == myContactsRow
		}

		override fun getItemCount(): Int {
			return rowCount
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = when (viewType) {
				1 -> {
					TextInfoPrivacyCell(mContext)
				}

				2 -> {
					val margin = if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
						16
					}
					else {
						0
					}

					HeaderCell(mContext, topMargin = margin, bottomMargin = margin)
				}

				3 -> {
					CheckmarkCell(mContext)
				}

				else -> {
					throw IllegalArgumentException("Wrong viewType")
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				1 -> {
					val privacyCell = holder.itemView as TextInfoPrivacyCell

					when (position) {
						detailRow -> {
							when (rulesType) {
								PRIVACY_RULES_TYPE_FORWARDS -> {
									privacyCell.setText(LocaleController.getString("PrivacyForwardsInfo", R.string.PrivacyForwardsInfo))
								}

								PRIVACY_RULES_TYPE_PHOTO -> {
									privacyCell.setText(LocaleController.getString("PrivacyProfilePhotoInfo", R.string.PrivacyProfilePhotoInfo))
								}

								PRIVACY_RULES_TYPE_CALLS -> {
									privacyCell.setText(LocaleController.getString("WhoCanCallMeInfo", R.string.WhoCanCallMeInfo))
								}

								PRIVACY_RULES_TYPE_INVITE -> {
									privacyCell.setText(LocaleController.getString("WhoCanAddMeInfo", R.string.WhoCanAddMeInfo))
								}

								PRIVACY_RULES_TYPE_LAST_SEEN -> {
									privacyCell.setText(LocaleController.getString("CustomHelp", R.string.CustomHelp))
								}
							}
						}
					}
				}

				2 -> {
					val headerCell = holder.itemView as HeaderCell

					when (position) {
						sectionRow -> {
							when (rulesType) {
								PRIVACY_RULES_TYPE_FORWARDS -> {
									headerCell.setText(LocaleController.getString("PrivacyForwardsTitle", R.string.PrivacyForwardsTitle))
								}

								PRIVACY_RULES_TYPE_PHOTO -> {
									headerCell.setText(LocaleController.getString("PrivacyProfilePhotoTitle", R.string.PrivacyProfilePhotoTitle))
								}

								PRIVACY_RULES_TYPE_CALLS -> {
									headerCell.setText(LocaleController.getString("WhoCanCallMe", R.string.WhoCanCallMe))
								}

								PRIVACY_RULES_TYPE_INVITE -> {
									headerCell.setText(LocaleController.getString("WhoCanAddMe", R.string.WhoCanAddMe))
								}

								PRIVACY_RULES_TYPE_LAST_SEEN -> {
									headerCell.setText(LocaleController.getString("LastSeenTitle", R.string.LastSeenTitle))
								}
							}
						}
					}
				}

				3 -> {
					val checkmarkCell = holder.itemView as CheckmarkCell

					if (position == everybodyRow || position == myContactsRow || position == nobodyRow) {
						when (position) {
							everybodyRow -> {
								checkmarkCell.setRoundedType(roundTop = true, roundBottom = false)
								checkmarkCell.setText(LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody), currentType == TYPE_EVERYBODY, true)
							}

							myContactsRow -> {
								val needDivider = if (rowCount == 4) {
									checkmarkCell.setRoundedType(roundTop = false, roundBottom = true)
									false
								}
								else {
									checkmarkCell.setRoundedType(roundTop = false, roundBottom = false)
									true
								}

								checkmarkCell.setText(LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts), currentType == TYPE_CONTACTS, needDivider)
							}

							nobodyRow -> {
								checkmarkCell.setRoundedType(roundTop = false, roundBottom = true)
								checkmarkCell.setText(LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody), currentType == TYPE_NOBODY, false)
							}
						}
					}
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				detailRow -> 1
				sectionRow -> 2
				everybodyRow, myContactsRow, nobodyRow -> 3
				else -> throw RuntimeException("Unsupported position")
			}
		}
	}

	companion object {
		const val TYPE_EVERYBODY = 0
		const val TYPE_NOBODY = 1
		const val TYPE_CONTACTS = 2
	}
}
