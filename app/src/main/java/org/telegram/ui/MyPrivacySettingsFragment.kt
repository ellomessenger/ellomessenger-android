/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022.
 */
package org.telegram.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.ContactsController
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_CALLS
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_FORWARDS
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_INVITE
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_LAST_SEEN
import org.telegram.messenger.ContactsController.Companion.PRIVACY_RULES_TYPE_PHOTO
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.utils.reload
import org.telegram.tgnet.TLRPC.TL_privacyValueAllowAll
import org.telegram.tgnet.TLRPC.TL_privacyValueAllowChatParticipants
import org.telegram.tgnet.TLRPC.TL_privacyValueAllowUsers
import org.telegram.tgnet.TLRPC.TL_privacyValueDisallowAll
import org.telegram.tgnet.TLRPC.TL_privacyValueDisallowChatParticipants
import org.telegram.tgnet.TLRPC.TL_privacyValueDisallowUsers
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter

class MyPrivacySettingsFragment : BaseFragment(), NotificationCenterDelegate {
	private var listAdapter: ListAdapter? = null
	private var listView: RecyclerListView? = null
	private var blockedRow = 0
	private var privacySectionRow = 0
	private var lastSeenRow = 0
	private var groupsRow = 0
	private var callsRow = 0
	private var profilePhotoRow = 0
	private var forwardsRow = 0
	private var rowCount = 0

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		contactsController.loadPrivacySettings()
		messagesController.getBlockedPeers(true)

		updateRows()

		notificationCenter.addObserver(this, NotificationCenter.privacyRulesUpdated)
		notificationCenter.addObserver(this, NotificationCenter.blockedUsersDidLoad)

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		notificationCenter.removeObserver(this, NotificationCenter.privacyRulesUpdated)
		notificationCenter.removeObserver(this, NotificationCenter.blockedUsersDidLoad)
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(R.string.PrivacyTitle))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == -1) {
					finishFragment()
				}
			}
		})

		fragmentView = FrameLayout(context)

		val frameLayout = fragmentView as FrameLayout
		frameLayout.setBackgroundResource(R.color.light_background)

		listAdapter = ListAdapter(context)

		listView = RecyclerListView(context)

		listView?.layoutManager = object : LinearLayoutManager(context, VERTICAL, false) {
			override fun supportsPredictiveItemAnimations(): Boolean {
				return false
			}
		}

		listView?.isVerticalScrollBarEnabled = false
		listView?.layoutAnimation = null

		frameLayout.addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.adapter = listAdapter

		listView?.setOnItemClickListener { _, position ->
			// TODO: check if this is proper
//			if (!view.isEnabled) {
//				return@OnItemClickListener
//			}

			when (position) {
				blockedRow -> {
					presentFragment(PrivacyUsersActivity())
				}
				lastSeenRow -> {
					presentFragment(MyPrivacyControlFragment(PRIVACY_RULES_TYPE_LAST_SEEN))
				}
				groupsRow -> {
					presentFragment(MyPrivacyControlFragment(PRIVACY_RULES_TYPE_INVITE))
				}
				callsRow -> {
					presentFragment(MyPrivacyControlFragment(PRIVACY_RULES_TYPE_CALLS))
				}
				profilePhotoRow -> {
					presentFragment(MyPrivacyControlFragment(PRIVACY_RULES_TYPE_PHOTO))
				}
				forwardsRow -> {
					presentFragment(MyPrivacyControlFragment(PRIVACY_RULES_TYPE_FORWARDS))
				}
			}
		}

		return fragmentView
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.privacyRulesUpdated -> {
				listAdapter?.reload(0, listAdapter?.itemCount ?: 0, listAdapter?.itemCount ?: 0)
			}
			NotificationCenter.blockedUsersDidLoad -> {
				listAdapter?.notifyItemChanged(blockedRow)
			}
		}
	}

	private fun updateRows(notify: Boolean = true) {
		rowCount = 0

		blockedRow = rowCount++
		privacySectionRow = rowCount++
		lastSeenRow = rowCount++
		groupsRow = rowCount++
		callsRow = rowCount++
		profilePhotoRow = rowCount++
		forwardsRow = rowCount++

		if (notify) {
			listAdapter?.reload(0, listAdapter?.itemCount ?: 0, listAdapter?.itemCount ?: 0)
		}
	}

	override fun onResume() {
		super.onResume()
		listAdapter?.reload(0, listAdapter?.itemCount ?: 0, listAdapter?.itemCount ?: 0)
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val position = holder.adapterPosition
			return position == blockedRow || (position == groupsRow && !contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_INVITE)) || (position == lastSeenRow && !contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_LAST_SEEN)) || (position == callsRow && !contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_CALLS)) || (position == profilePhotoRow && !contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_PHOTO)) || (position == forwardsRow && !contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_FORWARDS))
		}

		override fun getItemCount(): Int {
			return rowCount
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = when (viewType) {
				0 -> {
					TextSettingsCell(mContext).also {
						it.setBackgroundResource(R.color.background)
					}
				}
				2 -> {
					HeaderCell(mContext).also {
						it.setBackgroundResource(R.color.light_background)
					}
				}
				else -> {
					throw IllegalArgumentException("Wrong viewType: $viewType")
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					var showLoading = false
					var value: String? = null
					var loadingLen = 16
					val animated = holder.itemView.tag != null && holder.itemView.tag as Int == position

					holder.itemView.tag = position

					val textCell = holder.itemView as TextSettingsCell
					textCell.setIcon(0)

					if (position == blockedRow) {
						val totalCount = messagesController.totalBlockedCount

						textCell.setIcon(R.drawable.slash)

						if (totalCount == 0) {
							textCell.setTextAndValueAndIcon(LocaleController.getString("BlockedUsers", R.string.BlockedUsers), LocaleController.getString("BlockedEmpty", R.string.BlockedEmpty), R.drawable.right_arrow_2, animated = false, divider = true)
						}
						else if (totalCount > 0) {
							textCell.setTextAndValueAndIcon(LocaleController.getString("BlockedUsers", R.string.BlockedUsers), String.format("%d", totalCount), R.drawable.right_arrow_2, animated = false, divider = true)
						}
						else {
							showLoading = true

							textCell.setText(LocaleController.getString("BlockedUsers", R.string.BlockedUsers), true)
						}
					}
					else if (position == lastSeenRow) {
						if (contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_LAST_SEEN)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, PRIVACY_RULES_TYPE_LAST_SEEN)
						}

						textCell.setTextAndValueAndIcon(LocaleController.getString("PrivacyLastSeen", R.string.PrivacyLastSeen), value, R.drawable.right_arrow_2, animated = false, divider = true)
					}
					else if (position == groupsRow) {
						if (contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_INVITE)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, PRIVACY_RULES_TYPE_INVITE)
						}

						textCell.setTextAndValueAndIcon(LocaleController.getString("GroupsAndChannels", R.string.GroupsAndChannels), value, R.drawable.right_arrow_2, animated = false, divider = true)
					}
					else if (position == callsRow) {
						if (contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_CALLS)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, PRIVACY_RULES_TYPE_CALLS)
						}

						textCell.setTextAndValueAndIcon(LocaleController.getString("Calls", R.string.Calls), value, R.drawable.right_arrow_2, animated = false, divider = true)
					}
					else if (position == profilePhotoRow) {
						if (contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_PHOTO)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, PRIVACY_RULES_TYPE_PHOTO)
						}

						textCell.setTextAndValueAndIcon(LocaleController.getString("PrivacyProfilePhoto", R.string.PrivacyProfilePhoto), value, R.drawable.right_arrow_2, animated = false, divider = true)
					}
					else if (position == forwardsRow) {
						if (contactsController.getLoadingPrivacyInfo(PRIVACY_RULES_TYPE_FORWARDS)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, PRIVACY_RULES_TYPE_FORWARDS)
						}

						textCell.setTextAndValueAndIcon(LocaleController.getString("PrivacyForwards", R.string.PrivacyForwards), value, R.drawable.right_arrow_2, animated = false, divider = true)
					}

					textCell.setDrawLoading(showLoading, loadingLen, animated)
				}
				2 -> {
					val headerCell = holder.itemView as HeaderCell

					when (position) {
						privacySectionRow -> {
							headerCell.setText(LocaleController.getString("PrivacyTitle", R.string.PrivacyTitle))
						}
					}
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				lastSeenRow, blockedRow, groupsRow -> 0
				privacySectionRow -> 2
				else -> 0
			}
		}
	}

	companion object {
		fun formatRulesString(accountInstance: AccountInstance, rulesType: Int): String {
			val privacyRules = accountInstance.contactsController.getPrivacyRules(rulesType)

			if (privacyRules == null || privacyRules.size == 0) {
				return if (rulesType == 3) {
					LocaleController.getString("P2PNobody", R.string.P2PNobody)
				}
				else {
					LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody)
				}
			}

			var type = -1
			var plus = 0
			var minus = 0

			for (a in privacyRules.indices) {
				val rule = privacyRules[a]

				if (rule is TL_privacyValueAllowChatParticipants) {
					var b = 0
					val n = rule.chats.size

					while (b < n) {
						val chat = accountInstance.messagesController.getChat(rule.chats[b])

						if (chat == null) {
							b++
							continue
						}

						plus += chat.participants_count

						b++
					}
				}
				else if (rule is TL_privacyValueDisallowChatParticipants) {
					var b = 0
					val n = rule.chats.size

					while (b < n) {
						val chat = accountInstance.messagesController.getChat(rule.chats[b])

						if (chat == null) {
							b++
							continue
						}

						minus += chat.participants_count

						b++
					}
				}
				else if (rule is TL_privacyValueAllowUsers) {
					plus += rule.users.size
				}
				else if (rule is TL_privacyValueDisallowUsers) {
					minus += rule.users.size
				}
				else if (type == -1) {
					type = when (rule) {
						is TL_privacyValueAllowAll -> {
							0
						}
						is TL_privacyValueDisallowAll -> {
							1
						}
						else -> {
							2
						}
					}
				}
			}

			if (type == 0 || type == -1 && minus > 0) {
				return if (rulesType == 3) {
					if (minus == 0) {
						LocaleController.getString("P2PEverybody", R.string.P2PEverybody)
					}
					else {
						LocaleController.formatString("P2PEverybodyMinus", R.string.P2PEverybodyMinus, minus)
					}
				}
				else {
					if (minus == 0) {
						LocaleController.getString("LastSeenEverybody", R.string.LastSeenEverybody)
					}
					else {
						LocaleController.formatString("LastSeenEverybodyMinus", R.string.LastSeenEverybodyMinus, minus)
					}
				}
			}
			else if (type == 2 || type == -1 && minus > 0 && plus > 0) {
				return if (rulesType == 3) {
					if (plus == 0 && minus == 0) {
						LocaleController.getString("P2PContacts", R.string.P2PContacts)
					}
					else {
						if (plus != 0 && minus != 0) {
							LocaleController.formatString("P2PContactsMinusPlus", R.string.P2PContactsMinusPlus, minus, plus)
						}
						else if (minus != 0) {
							LocaleController.formatString("P2PContactsMinus", R.string.P2PContactsMinus, minus)
						}
						else {
							LocaleController.formatString("P2PContactsPlus", R.string.P2PContactsPlus, plus)
						}
					}
				}
				else {
					if (plus == 0 && minus == 0) {
						LocaleController.getString("LastSeenContacts", R.string.LastSeenContacts)
					}
					else {
						if (plus != 0 && minus != 0) {
							LocaleController.formatString("LastSeenContactsMinusPlus", R.string.LastSeenContactsMinusPlus, minus, plus)
						}
						else if (minus != 0) {
							LocaleController.formatString("LastSeenContactsMinus", R.string.LastSeenContactsMinus, minus)
						}
						else {
							LocaleController.formatString("LastSeenContactsPlus", R.string.LastSeenContactsPlus, plus)
						}
					}
				}
			}
			else if (type == 1 || plus > 0) {
				return if (rulesType == 3) {
					if (plus == 0) {
						LocaleController.getString("P2PNobody", R.string.P2PNobody)
					}
					else {
						LocaleController.formatString("P2PNobodyPlus", R.string.P2PNobodyPlus, plus)
					}
				}
				else {
					if (plus == 0) {
						LocaleController.getString("LastSeenNobody", R.string.LastSeenNobody)
					}
					else {
						LocaleController.formatString("LastSeenNobodyPlus", R.string.LastSeenNobodyPlus, plus)
					}
				}
			}

			return "unknown"
		}
	}
}
