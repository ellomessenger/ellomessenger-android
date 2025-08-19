/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.statistics

import android.os.Bundle
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLChatChannelParticipant
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.bannedRights
import org.telegram.tgnet.channelParticipant
import org.telegram.tgnet.participants
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.ChatRightsEditActivity
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.ProfileActivity

class MemberData {
	var user: TLRPC.User? = null
	var userId = 0L
	var description: String? = null

	fun onClick(fragment: BaseFragment) {
		val user = user ?: return

		val bundle = Bundle()
		bundle.putLong("user_id", user.id)

		MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false)

		fragment.presentFragment(ProfileActivity(bundle))
	}

	fun onLongClick(chat: TLRPC.ChatFull, fragment: StatisticActivity, progressDialog: Array<AlertDialog?>) {
		onLongClick(chat, fragment, progressDialog, true)
	}

	private fun onLongClick(chat: TLRPC.ChatFull, fragment: StatisticActivity, progressDialog: Array<AlertDialog?>, userIsPracticant: Boolean) {
		MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false)

		val items = mutableListOf<String>()
		val actions = mutableListOf<Int>()
		val icons = mutableListOf<Int>()
		var currentParticipant: TLRPC.ChatParticipant? = null
		var currentUser: TLRPC.ChatParticipant? = null
		val context = ApplicationLoader.applicationContext

		if (userIsPracticant) {
			val participants = chat.participants?.participants

			if (participants != null) {
				for (participant in participants) {
					if (participant.userId == user?.id) {
						if (participant is TLChatChannelParticipant) {
							currentParticipant = participant
						}
					}

					if (participant.userId == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
						if (participant is TLChatChannelParticipant) {
							currentUser = participant
						}
					}
				}
			}
		}

		items.add(context.getString(R.string.StatisticOpenProfile))
		icons.add(R.drawable.msg_openprofile)
		actions.add(2)
		items.add(context.getString(R.string.StatisticSearchUserHistory))
		icons.add(R.drawable.msg_msgbubble3)
		actions.add(1)

		if (userIsPracticant && currentParticipant == null) {
			if (progressDialog[0] == null) {
				progressDialog[0] = AlertDialog(context, 3)
				progressDialog[0]?.showDelayed(300)
			}

			val request = TLRPC.TLChannelsGetParticipant()
			request.channel = MessagesController.getInstance(UserConfig.selectedAccount).getInputChannel(chat.id)
			request.participant = MessagesController.getInputPeer(user!!)

			ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (fragment.isFinishing || fragment.fragmentView == null) {
						return@runOnUIThread
					}

					if (progressDialog[0] == null) {
						return@runOnUIThread
					}

					if (error == null && response is TLRPC.TLChannelsChannelParticipant) {
						val chatChannelParticipant = TLChatChannelParticipant()
						chatChannelParticipant.channelParticipant = response.participant
						chatChannelParticipant.userId = user?.id ?: 0

						chat.participants?.participants?.add(0, chatChannelParticipant)

						onLongClick(chat, fragment, progressDialog)
					}
					else {
						onLongClick(chat, fragment, progressDialog, false)
					}
				}
			}
			return
		}

		if (userIsPracticant && currentUser == null) {
			if (progressDialog[0] == null) {
				progressDialog[0] = AlertDialog(context, 3)
				progressDialog[0]?.showDelayed(300)
			}

			val request = TLRPC.TLChannelsGetParticipant()
			request.channel = MessagesController.getInstance(UserConfig.selectedAccount).getInputChannel(chat.id)
			request.participant = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(UserConfig.getInstance(UserConfig.selectedAccount).clientUserId)

			ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (fragment.isFinishing || fragment.fragmentView == null) {
						return@runOnUIThread
					}

					if (progressDialog[0] == null) {
						return@runOnUIThread
					}

					if (error == null && response is TLRPC.TLChannelsChannelParticipant) {
						val chatChannelParticipant = TLChatChannelParticipant()
						chatChannelParticipant.channelParticipant = response.participant
						chatChannelParticipant.userId = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId

						chat.participants?.participants?.add(0, chatChannelParticipant)

						onLongClick(chat, fragment, progressDialog)
					}
					else {
						onLongClick(chat, fragment, progressDialog, false)
					}
				}
			}
			return
		}

		if (progressDialog[0] != null) {
			progressDialog[0]?.dismiss()
			progressDialog[0] = null
		}

		var isAdmin = false

		if (currentUser != null && currentParticipant != null && currentUser.userId != currentParticipant.userId) {
			val channelParticipant = currentParticipant.channelParticipant
			var canEditAdmin = currentUser.channelParticipant?.adminRights?.addAdmins == true

			if (canEditAdmin && (channelParticipant is TLRPC.TLChannelParticipantCreator || channelParticipant is TLRPC.TLChannelParticipantAdmin && !channelParticipant.canEdit)) {
				canEditAdmin = false
			}

			if (canEditAdmin) {
				isAdmin = channelParticipant?.adminRights == null
				items.add(if (isAdmin) context.getString(R.string.SetAsAdmin) else context.getString(R.string.EditAdminRights))
				icons.add(if (isAdmin) R.drawable.msg_admins else R.drawable.msg_permissions)
				actions.add(0)
			}
		}

		val builder = AlertDialog.Builder(context)
		val finalCurrentParticipant = currentParticipant
		val finalIsAdmin = isAdmin

		builder.setItems(items.toTypedArray(), AndroidUtilities.toIntArray(icons)) { _, i ->
			if (actions[i] == 0) {
				val needShowBulletin = BooleanArray(1)

				val newFragment: ChatRightsEditActivity = object : ChatRightsEditActivity(user!!.id, chat.id, finalCurrentParticipant?.channelParticipant?.adminRights, null, finalCurrentParticipant?.channelParticipant?.bannedRights, finalCurrentParticipant?.channelParticipant?.rank, TYPE_ADMIN, true, finalIsAdmin, null) {
					override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
						if (!isOpen && backward && needShowBulletin[0] && BulletinFactory.canShowBulletin(fragment)) {
							BulletinFactory.createPromoteToAdminBulletin(fragment, user?.firstName).show()
						}
					}
				}

				newFragment.setDelegate(object : ChatRightsEditActivity.ChatRightsEditActivityDelegate {
					override fun didSetRights(rights: Int, rightsAdmin: TLRPC.TLChatAdminRights?, rightsBanned: TLRPC.TLChatBannedRights?, rank: String?) {
						if (rights == 0) {
							finalCurrentParticipant?.channelParticipant?.let {
								it.adminRights = null
								it.rank = ""
							}
						}
						else {
							finalCurrentParticipant?.channelParticipant?.let {
								it.adminRights = rightsAdmin
								it.rank = rank
							}

							if (finalIsAdmin) {
								needShowBulletin[0] = true
							}
						}
					}

					override fun didChangeOwner(user: TLRPC.User) {
						// unused
					}
				})

				fragment.presentFragment(newFragment)
			}
			else if (actions[i] == 2) {
				onClick(fragment)
			}
			else {
				val bundle = Bundle()
				bundle.putLong("chat_id", chat.id)
				bundle.putLong("search_from_user_id", user!!.id)
				fragment.presentFragment(ChatActivity(bundle))
			}
		}

		val alertDialog = builder.create()

		fragment.showDialog(alertDialog)
	}

	companion object {
		fun from(poster: TLRPC.TLStatsGroupTopPoster, users: List<TLRPC.User>): MemberData {
			val data = MemberData()
			data.userId = poster.userId
			data.user = find(data.userId, users)

			data.description = buildString {
				if (poster.messages > 0) {
					append(LocaleController.formatPluralString("messages", poster.messages))
				}

				if (poster.avgChars > 0) {
					if (isNotEmpty()) {
						append(", ")
					}

					append(LocaleController.formatString("CharactersPerMessage", R.string.CharactersPerMessage, LocaleController.formatPluralString("Characters", poster.avgChars)))
				}
			}

			return data
		}

		fun from(admin: TLRPC.TLStatsGroupTopAdmin, users: List<TLRPC.User>): MemberData {
			val data = MemberData()
			data.userId = admin.userId
			data.user = find(data.userId, users)

			data.description = buildString {
				if (admin.deleted > 0) {
					append(LocaleController.formatPluralString("Deletions", admin.deleted))
				}
				if (admin.banned > 0) {
					if (isNotEmpty()) {
						append(", ")
					}

					append(LocaleController.formatPluralString("Bans", admin.banned))
				}

				if (admin.kicked > 0) {
					if (isNotEmpty()) {
						append(", ")
					}

					append(LocaleController.formatPluralString("Restrictions", admin.kicked))
				}
			}

			return data
		}

		fun from(inviter: TLRPC.TLStatsGroupTopInviter, users: List<TLRPC.User>): MemberData {
			val data = MemberData()
			data.userId = inviter.userId
			data.user = find(data.userId, users)

			if (inviter.invitations > 0) {
				data.description = LocaleController.formatPluralString("Invitations", inviter.invitations)
			}
			else {
				data.description = ""
			}

			return data
		}

		fun find(userId: Long, users: List<TLRPC.User>): TLRPC.User? {
			return users.find { it.id == userId }
		}
	}
}
