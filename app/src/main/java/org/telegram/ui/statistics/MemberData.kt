/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
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
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.TL_chatBannedRights
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.ChatRightsEditActivity
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.ProfileActivity

class MemberData {
	var user: User? = null
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

		val items = ArrayList<String>()
		val actions = ArrayList<Int>()
		val icons = ArrayList<Int>()
		var currentParticipant: TLRPC.TL_chatChannelParticipant? = null
		var currentUser: TLRPC.TL_chatChannelParticipant? = null
		val context = ApplicationLoader.applicationContext

		if (userIsPracticant && chat.participants?.participants != null) {
			val n = chat.participants.participants?.size ?: 0

			for (i in 0 until n) {
				val participant = chat.participants.participants[i]

				if (participant.user_id == user?.id) {
					if (participant is TLRPC.TL_chatChannelParticipant) {
						currentParticipant = participant
					}
				}

				if (participant.user_id == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
					if (participant is TLRPC.TL_chatChannelParticipant) {
						currentUser = participant
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

			val request = TLRPC.TL_channels_getParticipant()
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

					if (error == null && response is TLRPC.TL_channels_channelParticipant) {
						val chatChannelParticipant = TLRPC.TL_chatChannelParticipant()
						chatChannelParticipant.channelParticipant = response.participant
						chatChannelParticipant.user_id = user!!.id

						chat.participants.participants.add(0, chatChannelParticipant)

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

			val request = TLRPC.TL_channels_getParticipant()
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

					if (error == null && response is TLRPC.TL_channels_channelParticipant) {
						val chatChannelParticipant = TLRPC.TL_chatChannelParticipant()
						chatChannelParticipant.channelParticipant = response.participant
						chatChannelParticipant.user_id = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId
						chat.participants.participants.add(0, chatChannelParticipant)

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

		if (currentUser != null && currentParticipant != null && currentUser.user_id != currentParticipant.user_id) {
			val channelParticipant = currentParticipant.channelParticipant
			var canEditAdmin = currentUser.channelParticipant.admin_rights != null && currentUser.channelParticipant.admin_rights.add_admins

			if (canEditAdmin && (channelParticipant is TLRPC.TL_channelParticipantCreator || channelParticipant is TLRPC.TL_channelParticipantAdmin && !channelParticipant.can_edit)) {
				canEditAdmin = false
			}

			if (canEditAdmin) {
				isAdmin = channelParticipant.admin_rights == null
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

				val newFragment: ChatRightsEditActivity = object : ChatRightsEditActivity(user!!.id, chat.id, finalCurrentParticipant!!.channelParticipant.admin_rights, null, finalCurrentParticipant.channelParticipant.banned_rights, finalCurrentParticipant.channelParticipant.rank, TYPE_ADMIN, true, finalIsAdmin, null) {
					override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
						if (!isOpen && backward && needShowBulletin[0] && BulletinFactory.canShowBulletin(fragment)) {
							BulletinFactory.createPromoteToAdminBulletin(fragment, user!!.first_name).show()
						}
					}
				}

				newFragment.setDelegate(object : ChatRightsEditActivity.ChatRightsEditActivityDelegate {
					override fun didSetRights(rights: Int, rightsAdmin: TLRPC.TL_chatAdminRights?, rightsBanned: TL_chatBannedRights?, rank: String?) {
						if (rights == 0) {
							finalCurrentParticipant.channelParticipant.admin_rights = null
							finalCurrentParticipant.channelParticipant.rank = ""
						}
						else {
							finalCurrentParticipant.channelParticipant.admin_rights = rightsAdmin
							finalCurrentParticipant.channelParticipant.rank = rank

							if (finalIsAdmin) {
								needShowBulletin[0] = true
							}
						}
					}

					override fun didChangeOwner(user: User) {
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
		fun from(poster: TLRPC.TL_statsGroupTopPoster, users: ArrayList<User>): MemberData {
			val data = MemberData()
			data.userId = poster.user_id
			data.user = find(data.userId, users)

			data.description = buildString {
				if (poster.messages > 0) {
					append(LocaleController.formatPluralString("messages", poster.messages))
				}

				if (poster.avg_chars > 0) {
					if (isNotEmpty()) {
						append(", ")
					}

					append(LocaleController.formatString("CharactersPerMessage", R.string.CharactersPerMessage, LocaleController.formatPluralString("Characters", poster.avg_chars)))
				}
			}

			return data
		}

		fun from(admin: TLRPC.TL_statsGroupTopAdmin, users: ArrayList<User>): MemberData {
			val data = MemberData()
			data.userId = admin.user_id
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

		fun from(inviter: TLRPC.TL_statsGroupTopInviter, users: ArrayList<User>): MemberData {
			val data = MemberData()
			data.userId = inviter.user_id
			data.user = find(data.userId, users)

			if (inviter.invitations > 0) {
				data.description = LocaleController.formatPluralString("Invitations", inviter.invitations)
			}
			else {
				data.description = ""
			}

			return data
		}

		fun find(userId: Long, users: ArrayList<User>): User? {
			return users.find { it.id == userId }
		}
	}
}
