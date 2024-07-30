/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import android.graphics.Bitmap
import android.os.SystemClock
import android.text.TextUtils
import android.util.SparseArray
import androidx.annotation.IntDef
import androidx.collection.LongSparseArray
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.voip.Instance
import org.telegram.messenger.voip.VoIPService.Companion.sharedInstance
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.ChatPhoto
import org.telegram.tgnet.TLRPC.GroupCall
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.TLRPC.TL_channel
import org.telegram.tgnet.TLRPC.TL_channelForbidden
import org.telegram.tgnet.TLRPC.TL_channel_layer48
import org.telegram.tgnet.TLRPC.TL_channel_layer67
import org.telegram.tgnet.TLRPC.TL_channel_layer72
import org.telegram.tgnet.TLRPC.TL_channel_layer77
import org.telegram.tgnet.TLRPC.TL_channel_layer92
import org.telegram.tgnet.TLRPC.TL_channel_old
import org.telegram.tgnet.tlrpc.TL_chatBannedRights
import org.telegram.tgnet.TLRPC.TL_chatEmpty
import org.telegram.tgnet.TLRPC.TL_chatForbidden
import org.telegram.tgnet.TLRPC.TL_chatPhotoEmpty
import org.telegram.tgnet.TLRPC.TL_chatReactionsAll
import org.telegram.tgnet.TLRPC.TL_chatReactionsSome
import org.telegram.tgnet.TLRPC.TL_chat_layer92
import org.telegram.tgnet.TLRPC.TL_chat_old
import org.telegram.tgnet.TLRPC.TL_chat_old2
import org.telegram.tgnet.TLRPC.TL_groupCallParticipant
import org.telegram.tgnet.TLRPC.TL_groupCallParticipantVideo
import org.telegram.tgnet.TLRPC.TL_groupCallParticipantVideoSourceGroup
import org.telegram.tgnet.TLRPC.TL_groupCallStreamChannel
import org.telegram.tgnet.TLRPC.TL_inputGroupCall
import org.telegram.tgnet.TLRPC.TL_inputPeerChannel
import org.telegram.tgnet.TLRPC.TL_inputPeerChat
import org.telegram.tgnet.TLRPC.TL_inputPeerUser
import org.telegram.tgnet.TLRPC.TL_peerChannel
import org.telegram.tgnet.TLRPC.TL_peerChat
import org.telegram.tgnet.TLRPC.TL_peerUser
import org.telegram.tgnet.TLRPC.TL_phone_editGroupCallTitle
import org.telegram.tgnet.TLRPC.TL_phone_getGroupCall
import org.telegram.tgnet.TLRPC.TL_phone_getGroupParticipants
import org.telegram.tgnet.TLRPC.TL_phone_groupCall
import org.telegram.tgnet.TLRPC.TL_phone_groupParticipants
import org.telegram.tgnet.TLRPC.TL_phone_toggleGroupCallRecord
import org.telegram.tgnet.tlrpc.TL_reactionEmoji
import org.telegram.tgnet.TLRPC.TL_updateGroupCall
import org.telegram.tgnet.TLRPC.TL_updateGroupCallParticipants
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.ui.group.GroupCallActivity
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalContracts::class)
object ChatObject {
	const val CHAT_TYPE_CHAT = 0
	const val CHAT_TYPE_CHANNEL = 2

	// const val CHAT_TYPE_USER = 3
	const val CHAT_TYPE_MEGAGROUP = 4
	const val ACTION_PIN = 0
	const val ACTION_CHANGE_INFO = 1
	private const val ACTION_BLOCK_USERS = 2
	const val ACTION_INVITE = 3
	private const val ACTION_ADD_ADMINS = 4
	private const val ACTION_POST = 5
	const val ACTION_SEND = 6
	const val ACTION_SEND_MEDIA = 7
	const val ACTION_SEND_STICKERS = 8
	private const val ACTION_EMBED_LINKS = 9
	const val ACTION_SEND_POLLS = 10
	const val ACTION_VIEW = 11
	private const val ACTION_EDIT_MESSAGES = 12
	const val ACTION_DELETE_MESSAGES = 13
	private const val ACTION_MANAGE_CALLS = 14
	const val VIDEO_FRAME_NO_FRAME = 0
	const val VIDEO_FRAME_REQUESTING = 1
	const val VIDEO_FRAME_HAS_FRAME = 2
	private const val MAX_PARTICIPANTS_COUNT = 5000

	@JvmStatic
	fun reactionIsAvailable(chatInfo: ChatFull?, reaction: String?): Boolean {
		if (chatInfo?.available_reactions is TL_chatReactionsAll) {
			return true
		}

		if (chatInfo?.available_reactions is TL_chatReactionsSome) {
			val someReactions = chatInfo.available_reactions as TL_chatReactionsSome

			for (i in someReactions.reactions.indices) {
				if (someReactions.reactions[i] is TL_reactionEmoji && TextUtils.equals((someReactions.reactions[i] as TL_reactionEmoji).emoticon, reaction)) {
					return true
				}
			}
		}

		return false
	}

	@JvmStatic
	fun getParticipantVolume(participant: TL_groupCallParticipant): Int {
		return if (participant.flags and 128 != 0) participant.volume else 10000
	}

	private fun isBannableAction(action: Int): Boolean {
		when (action) {
			ACTION_PIN, ACTION_CHANGE_INFO, ACTION_INVITE, ACTION_SEND, ACTION_SEND_MEDIA, ACTION_SEND_STICKERS, ACTION_EMBED_LINKS, ACTION_SEND_POLLS, ACTION_VIEW -> return true
		}
		return false
	}

	private fun isAdminAction(action: Int): Boolean {
		when (action) {
			ACTION_PIN, ACTION_CHANGE_INFO, ACTION_INVITE, ACTION_ADD_ADMINS, ACTION_POST, ACTION_EDIT_MESSAGES, ACTION_DELETE_MESSAGES, ACTION_BLOCK_USERS -> return true
		}

		return false
	}

	private fun getBannedRight(rights: TL_chatBannedRights?, action: Int): Boolean {
		if (rights == null) {
			return false
		}

		return when (action) {
			ACTION_PIN -> rights.pin_messages
			ACTION_CHANGE_INFO -> rights.change_info
			ACTION_INVITE -> rights.invite_users
			ACTION_SEND -> rights.send_messages
			ACTION_SEND_MEDIA -> rights.send_media
			ACTION_SEND_STICKERS -> rights.send_stickers
			ACTION_EMBED_LINKS -> rights.embed_links
			ACTION_SEND_POLLS -> rights.send_polls
			ACTION_VIEW -> rights.view_messages
			else -> false
		}
	}

	@JvmStatic
	fun isActionBannedByDefault(chat: Chat?, action: Int): Boolean {
		return if (getBannedRight(chat?.banned_rights, action)) {
			false
		}
		else {
			getBannedRight(chat?.default_banned_rights, action)
		}
	}

	@JvmStatic
	fun isActionBanned(chat: Chat?, action: Int): Boolean {
		return chat != null && (getBannedRight(chat.banned_rights, action) || getBannedRight(chat.default_banned_rights, action))
	}

	@JvmStatic
	fun canUserDoAdminAction(chat: Chat?, action: Int): Boolean {
		contract {
			returns(true) implies (chat != null)
		}

		if (chat == null) {
			return false
		}

		if (chat.creator) {
			return true
		}

		if (chat.admin_rights != null) {
			return when (action) {
				ACTION_PIN -> chat.admin_rights.pin_messages
				ACTION_CHANGE_INFO -> chat.admin_rights.change_info
				ACTION_INVITE -> chat.admin_rights.invite_users
				ACTION_ADD_ADMINS -> chat.admin_rights.add_admins
				ACTION_POST -> chat.admin_rights.post_messages
				ACTION_EDIT_MESSAGES -> chat.admin_rights.edit_messages
				ACTION_DELETE_MESSAGES -> chat.admin_rights.delete_messages
				ACTION_BLOCK_USERS -> chat.admin_rights.ban_users
				ACTION_MANAGE_CALLS -> chat.admin_rights.manage_call
				else -> false
			}
		}

		return false
	}

	private fun canUserDoAction(chat: Chat?, action: Int): Boolean {
		if (chat == null) {
			return true
		}

		if (canUserDoAdminAction(chat, action)) {
			return true
		}

		if (getBannedRight(chat.banned_rights, action)) {
			return false
		}

		if (isBannableAction(action)) {
			if (chat.admin_rights != null && !isAdminAction(action)) {
				return true
			}
			if (chat.default_banned_rights == null && (chat is TL_chat_layer92 || chat is TL_chat_old || chat is TL_chat_old2 || chat is TL_channel_layer92 || chat is TL_channel_layer77 || chat is TL_channel_layer72 || chat is TL_channel_layer67 || chat is TL_channel_layer48 || chat is TL_channel_old)) {
				return true
			}

			val isBannedRight = getBannedRight(chat.default_banned_rights, action)

			// MARK: This is new variant
			return !isBannedRight

			// MARK: This was old variant
//			if (chat.default_banned_rights == null || getBannedRight(chat.default_banned_rights, action)) {
//				return false;
//			}
		}

		return false
	}

	@JvmStatic
	fun isLeftFromChat(chat: Chat?): Boolean {
		return chat == null || chat is TL_chatEmpty || chat is TL_chatForbidden || chat is TL_channelForbidden || chat.left || chat.deactivated
	}

	fun isKickedFromChat(chat: Chat?): Boolean {
		return chat == null || chat is TL_chatEmpty || chat is TL_chatForbidden || chat is TL_channelForbidden || chat.kicked || chat.deactivated || chat.banned_rights != null && chat.banned_rights.view_messages
	}

	@JvmStatic
	fun isNotInChat(chat: Chat?): Boolean {
		return chat == null || chat is TL_chatEmpty || chat is TL_chatForbidden || chat is TL_channelForbidden || chat.left || chat.kicked || chat.deactivated
	}

	@JvmStatic
	fun canSendAsPeers(chat: Chat?): Boolean {
		return isChannel(chat) && chat.megagroup && (!chat.username.isNullOrEmpty() || chat.has_geo || chat.has_link)
	}

	@JvmStatic
	fun isChannel(chat: Chat?): Boolean {
		contract {
			returns(true) implies (chat != null && (chat is TL_channel || chat is TL_channelForbidden))
		}

		return chat is TL_channel || chat is TL_channelForbidden
	}

	@JvmStatic
	fun isChannelOrGiga(chat: Chat?): Boolean {
		return (chat is TL_channel || chat is TL_channelForbidden) && (!chat.megagroup || chat.gigagroup)
	}

	@JvmStatic
	fun isSubscriptionChannel(chat: Chat?): Boolean {
		return (chat is TL_channel || chat is TL_channelForbidden) && (chat.pay_type == Chat.PAY_TYPE_SUBSCRIBE)
	}

	@JvmStatic
	fun isOnlineCourse(chat: Chat?): Boolean {
		return (chat is TL_channel || chat is TL_channelForbidden) && (chat.pay_type == Chat.PAY_TYPE_BASE)
	}

	@JvmStatic
	fun isPaidChannel(chat: Chat?): Boolean {
		return (chat is TL_channel || chat is TL_channelForbidden) && (chat.pay_type > Chat.PAY_TYPE_NONE)
	}

	@JvmStatic
	fun isMegagroup(chat: Chat?): Boolean {
		return (chat is TL_channel || chat is TL_channelForbidden) && chat.megagroup
	}

	@JvmStatic
	fun isChannelAndNotMegaGroup(chat: Chat?): Boolean {
		contract {
			returns(true) implies (chat != null)
		}

		return isChannel(chat) && !isMegagroup(chat)
	}

	@JvmStatic
	fun isMegagroup(currentAccount: Int, chatId: Long): Boolean {
		val chat = MessagesController.getInstance(currentAccount).getChat(chatId)
		return isChannel(chat) && chat.megagroup
	}

	@JvmStatic
	fun hasAdminRights(chat: Chat?): Boolean {
		return chat != null && (chat.creator || chat.admin_rights != null && chat.admin_rights.flags != 0)
	}

	fun canChangeChatInfo(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_CHANGE_INFO)
	}

	@JvmStatic
	fun canAddAdmins(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_ADD_ADMINS)
	}

	@JvmStatic
	fun canBlockUsers(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_BLOCK_USERS)
	}

	@JvmStatic
	fun canManageCalls(chat: Chat?): Boolean {
		contract {
			returns(true) implies (chat != null)
		}

		return canUserDoAction(chat, ACTION_MANAGE_CALLS)
	}

	@JvmStatic
	fun canSendStickers(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_SEND_STICKERS)
	}

	@JvmStatic
	fun canSendEmbed(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_EMBED_LINKS)
	}

	@JvmStatic
	fun canSendMedia(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_SEND_MEDIA)
	}

	@JvmStatic
	fun canSendPolls(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_SEND_POLLS)
	}

	@JvmStatic
	fun canSendMessages(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_SEND)
	}

	@JvmStatic
	fun canPost(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_POST)
	}

	@JvmStatic
	fun canAddUsers(chat: Chat?): Boolean {
		return canUserDoAction(chat, ACTION_INVITE)
	}

	fun shouldSendAnonymously(chat: Chat?): Boolean {
		return chat?.admin_rights?.anonymous == true
	}

	@JvmStatic
	fun getSendAsPeerId(chat: Chat?, chatFull: ChatFull?): Long {
		return getSendAsPeerId(chat, chatFull, false)
	}

	@JvmStatic
	fun getSendAsPeerId(chat: Chat?, chatFull: ChatFull?, invertChannel: Boolean): Long {
		if (chat != null && chatFull != null && chatFull.default_send_as != null) {
			val p = chatFull.default_send_as
			return if (p.user_id != 0L) p.user_id else if (invertChannel) -p.channel_id else p.channel_id
		}

		return if (chat?.admin_rights?.anonymous == true) {
			if (invertChannel) -chat.id else chat.id
		}
		else {
			UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()
		}
	}

	fun canAddBotsToChat(chat: Chat?): Boolean {
		if (chat == null) {
			return false
		}

		if (isChannel(chat)) {
			if (chat.megagroup && (chat.admin_rights != null && (chat.admin_rights.post_messages || chat.admin_rights.add_admins) || chat.creator)) {
				return true
			}
		}
		else {
			if (chat.migrated_to == null) {
				return true
			}
		}

		return false
	}

	@JvmStatic
	fun canPinMessages(chat: Chat?): Boolean {
		// MARK: remove to enable pinning
		return false
//		if (chat == null) {
//			return false
//		}
//
//		return canUserDoAction(chat, ACTION_PIN) || isChannel(chat) && !chat.megagroup && chat.admin_rights != null && chat.admin_rights.edit_messages
	}

	@JvmStatic
	fun isChannel(chatId: Long, currentAccount: Int): Boolean {
		val chat = MessagesController.getInstance(currentAccount).getChat(chatId)
		return chat is TL_channel || chat is TL_channelForbidden
	}

	@JvmStatic
	fun isChannelAndNotMegaGroup(chatId: Long, currentAccount: Int): Boolean {
		val chat = MessagesController.getInstance(currentAccount).getChat(chatId)
		return isChannelAndNotMegaGroup(chat)
	}

	@JvmStatic
	fun isCanWriteToChannel(chatId: Long, currentAccount: Int): Boolean {
		val chat = MessagesController.getInstance(currentAccount).getChat(chatId)
		return canSendMessages(chat) || chat?.megagroup == true
	}

	@JvmStatic
	fun canWriteToChat(chat: Chat?): Boolean {
		if (chat == null) {
			return false
		}

		return !isChannel(chat) || chat.creator || chat.admin_rights != null && chat.admin_rights.post_messages || !chat.broadcast && !chat.gigagroup || chat.gigagroup && hasAdminRights(chat)
	}

	@JvmStatic
	fun getBannedRightsString(bannedRights: TL_chatBannedRights?): String {
		var currentBannedRights = ""

		if (bannedRights != null) {
			currentBannedRights += if (bannedRights.view_messages) 1 else 0
			currentBannedRights += if (bannedRights.send_messages) 1 else 0
			currentBannedRights += if (bannedRights.send_media) 1 else 0
			currentBannedRights += if (bannedRights.send_stickers) 1 else 0
			currentBannedRights += if (bannedRights.send_gifs) 1 else 0
			currentBannedRights += if (bannedRights.send_games) 1 else 0
			currentBannedRights += if (bannedRights.send_inline) 1 else 0
			currentBannedRights += if (bannedRights.embed_links) 1 else 0
			currentBannedRights += if (bannedRights.send_polls) 1 else 0
			currentBannedRights += if (bannedRights.invite_users) 1 else 0
			currentBannedRights += if (bannedRights.change_info) 1 else 0
			currentBannedRights += if (bannedRights.pin_messages) 1 else 0
			currentBannedRights += bannedRights.until_date
		}

		return currentBannedRights
	}

	private fun hasPhoto(chat: Chat?): Boolean {
		return chat?.photo != null && chat.photo !is TL_chatPhotoEmpty
	}

	fun getPhoto(chat: Chat?): ChatPhoto? {
		return if (hasPhoto(chat)) chat?.photo else null
	}

	class Call {
		@Retention(AnnotationRetention.SOURCE)
		@IntDef(RECORD_TYPE_AUDIO, RECORD_TYPE_VIDEO_PORTRAIT, RECORD_TYPE_VIDEO_LANDSCAPE)
		annotation class RecordType

		var participantsByVideoSources = SparseArray<TL_groupCallParticipant>()
		var participantsByPresentationSources = SparseArray<TL_groupCallParticipant>()
		private var nextLoadOffset: String? = null
		private var activeVideos = 0
		var rtmpStreamParticipant: VideoParticipant? = null
		var loadedRtmpStreamParticipant = false
		private var speakingMembersCount = 0
		private var typingUpdateRunnableScheduled = false
		private var lastLoadGuid = 0
		private val loadingGuids = HashSet<Int>()
		private val updatesQueue = mutableListOf<TL_updateGroupCallParticipants>()
		private var updatesStartWaitTime: Long = 0
		private val loadingUids = HashSet<Long>()
		private val loadingSsrcs = HashSet<Long>()
		private var checkQueueRunnable: Runnable? = null
		private var lastGroupCallReloadTime: Long = 0
		private var loadingGroupCall = false

		@JvmField
		var call: GroupCall? = null

		@JvmField
		var chatId: Long = 0

		@JvmField
		var participants = LongSparseArray<TL_groupCallParticipant>()

		@JvmField
		val sortedParticipants = ArrayList<TL_groupCallParticipant>()

		@JvmField
		val visibleVideoParticipants = ArrayList<VideoParticipant>()

		@JvmField
		val visibleParticipants = ArrayList<TL_groupCallParticipant>()

		@JvmField
		val thumbs = HashMap<String, Bitmap>()

		private val videoParticipantsCache = HashMap<String, VideoParticipant>()

		@JvmField
		var invitedUsers = ArrayList<Long>()

		@JvmField
		var invitedUsersMap = HashSet<Long>()

		@JvmField
		var participantsBySources = SparseArray<TL_groupCallParticipant>()

		@JvmField
		var membersLoadEndReached = false

		@JvmField
		var loadingMembers = false

		private var reloadingMembers = false

		@JvmField
		var recording = false

		@JvmField
		var canStreamVideo = false

		@JvmField
		var videoNotAvailableParticipant: VideoParticipant? = null

		@JvmField
		var currentAccount: AccountInstance? = null

		private val typingUpdateRunnable = Runnable {
			typingUpdateRunnableScheduled = false
			checkOnlineParticipants()
			currentAccount!!.notificationCenter.postNotificationName(NotificationCenter.groupCallTypingsUpdated)
		}

		@JvmField
		var selfPeer: Peer? = null

		@JvmField
		val currentSpeakingPeers = LongSparseArray<TL_groupCallParticipant?>()

		private val updateCurrentSpeakingRunnable = object : Runnable {
			override fun run() {
				val uptime = SystemClock.uptimeMillis()
				var update = false
				var i = 0

				while (i < currentSpeakingPeers.size()) {
					val key = currentSpeakingPeers.keyAt(i)
					val participant = currentSpeakingPeers[key]

					if (uptime - participant!!.lastSpeakTime >= 500) {
						update = true

						currentSpeakingPeers.remove(key)

						if (key > 0) {
							val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(key)
							FileLog.d("GroupCall: remove from speaking " + key + " " + user?.first_name)
						}
						else {
							val user = MessagesController.getInstance(currentAccount!!.currentAccount).getChat(-key)
							FileLog.d("GroupCall: remove from speaking " + key + " " + user?.title)
						}

						i--
					}

					i++
				}

				if (currentSpeakingPeers.size() > 0) {
					AndroidUtilities.runOnUIThread(this, 550)
				}

				if (update) {
					currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallSpeakingUsersUpdated, chatId, call!!.id, false)
				}
			}
		}

		fun setCall(account: AccountInstance?, chatId: Long, groupCall: TL_phone_groupCall?) {
			this.chatId = chatId

			currentAccount = account
			call = groupCall?.call
			recording = (call != null && call?.record_start_date != 0)

			var date = Int.MAX_VALUE

			groupCall?.participants?.forEach { participant ->
				participants.put(MessageObject.getPeerId(participant.peer), participant)
				sortedParticipants.add(participant)
				processAllSources(participant, true)
				date = min(date, participant.date)
			}

			sortParticipants()

			nextLoadOffset = groupCall?.participants_next_offset

			loadMembers(true)

			createNoVideoParticipant()

			if (call?.rtmp_stream == true) {
				createRtmpStreamParticipant(emptyList())
			}
		}

		//        public void loadRtmpStreamChannels() {
		//            if (call == null || loadedRtmpStreamParticipant) {
		//                return;
		//            }
		//            TLRPC.TL_phone_getGroupCallStreamChannels getGroupCallStreamChannels = new TLRPC.TL_phone_getGroupCallStreamChannels();
		//            getGroupCallStreamChannels.call = getInputGroupCall();
		//            currentAccount.getConnectionsManager().sendRequest(getGroupCallStreamChannels, (response, error, timestamp) -> {
		//                if (response instanceof TLRPC.TL_phone_groupCallStreamChannels) {
		//                    TLRPC.TL_phone_groupCallStreamChannels streamChannels = (TLRPC.TL_phone_groupCallStreamChannels) response;
		//                    createRtmpStreamParticipant(streamChannels.channels);
		//                    loadedRtmpStreamParticipant = true;
		//                }
		//            }, ConnectionsManager.RequestFlagFailOnServerErrors, ConnectionsManager.ConnectionTypeDownload, call.stream_dc_id);
		//        }

		fun createRtmpStreamParticipant(channels: List<TL_groupCallStreamChannel>?) {
			if (loadedRtmpStreamParticipant && rtmpStreamParticipant != null) {
				return
			}

			val participant = if (rtmpStreamParticipant != null) rtmpStreamParticipant!!.participant else TL_groupCallParticipant()
			participant.peer = TL_peerChat()
			participant.peer.channel_id = chatId
			participant.video = TL_groupCallParticipantVideo()

			val sourceGroup = TL_groupCallParticipantVideoSourceGroup()
			sourceGroup.semantics = "SIM"

			channels?.forEach {
				sourceGroup.sources.add(it.channel)
			}

			participant.video.source_groups.add(sourceGroup)
			participant.video.endpoint = "unified"
			participant.videoEndpoint = "unified"

			rtmpStreamParticipant = VideoParticipant(participant, presentation = false, hasSame = false)

			sortParticipants()

			AndroidUtilities.runOnUIThread {
				currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
			}
		}

		fun createNoVideoParticipant() {
			if (videoNotAvailableParticipant != null) {
				return
			}

			val noVideoParticipant = TL_groupCallParticipant()
			noVideoParticipant.peer = TL_peerChannel()
			noVideoParticipant.peer.channel_id = chatId
			noVideoParticipant.muted = true
			noVideoParticipant.video = TL_groupCallParticipantVideo()
			noVideoParticipant.video.paused = true
			noVideoParticipant.video.endpoint = ""

			videoNotAvailableParticipant = VideoParticipant(noVideoParticipant, presentation = false, hasSame = false)
		}

		fun addSelfDummyParticipant(notify: Boolean) {
			val selfId = selfId

			if (participants.indexOfKey(selfId) >= 0) {
				return
			}

			val selfDummyParticipant = TL_groupCallParticipant()
			selfDummyParticipant.peer = selfPeer
			selfDummyParticipant.muted = true
			selfDummyParticipant.self = true
			selfDummyParticipant.video_joined = call!!.can_start_video

			val chat = currentAccount!!.messagesController.getChat(chatId)

			selfDummyParticipant.can_self_unmute = !call!!.join_muted || canManageCalls(chat)
			selfDummyParticipant.date = currentAccount!!.connectionsManager.currentTime

			if (canManageCalls(chat) || !isChannel(chat) || chat.megagroup || selfDummyParticipant.can_self_unmute) {
				selfDummyParticipant.active_date = currentAccount!!.connectionsManager.currentTime
			}

			if (selfId > 0) {
				val userFull = MessagesController.getInstance(currentAccount!!.currentAccount).getUserFull(selfId)

				if (userFull != null) {
					selfDummyParticipant.about = userFull.about
				}
			}
			else {
				val chatFull = MessagesController.getInstance(currentAccount!!.currentAccount).getChatFull(-selfId)

				if (chatFull != null) {
					selfDummyParticipant.about = chatFull.about
				}
			}

			participants.put(selfId, selfDummyParticipant)
			sortedParticipants.add(selfDummyParticipant)

			sortParticipants()

			if (notify) {
				currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
			}
		}

		fun migrateToChat(chat: Chat) {
			chatId = chat.id

			val voIPService = sharedInstance

			if (voIPService != null && voIPService.getAccount() == currentAccount!!.currentAccount && voIPService.getChat() != null && voIPService.getChat()!!.id == -chatId) {
				voIPService.migrateToChat(chat)
			}
		}

		fun shouldShowPanel(): Boolean {
			return call!!.participants_count > 0 || call!!.rtmp_stream || isScheduled
		}

		val isScheduled: Boolean
			get() = call!!.flags and 128 != 0

		private val selfId: Long
			get() = if (selfPeer != null) {
				MessageObject.getPeerId(selfPeer)
			}
			else {
				currentAccount?.userConfig?.getClientUserId() ?: 0L
			}

		private fun onParticipantsLoad(loadedParticipants: ArrayList<TL_groupCallParticipant>, fromBegin: Boolean, reqOffset: String?, nextOffset: String?, version: Int, participantCount: Int) {
			var old: LongSparseArray<TL_groupCallParticipant>? = null
			val selfId = selfId
			val oldSelf = participants[selfId]

			if (reqOffset.isNullOrEmpty()) {
				if (participants.size() != 0) {
					old = participants
					participants = LongSparseArray()
				}
				else {
					participants.clear()
				}

				sortedParticipants.clear()
				participantsBySources.clear()
				participantsByVideoSources.clear()
				participantsByPresentationSources.clear()
				loadingGuids.clear()
			}

			nextLoadOffset = nextOffset

			if (loadedParticipants.isEmpty() || nextLoadOffset.isNullOrEmpty()) {
				membersLoadEndReached = true
			}

			if (reqOffset.isNullOrEmpty()) {
				call?.version = version
				call?.participants_count = participantCount

				if (BuildConfig.DEBUG) {
					FileLog.d("new participants count ${call?.participants_count ?: 0}")
				}
			}

			val time = SystemClock.elapsedRealtime()

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time)

			var hasSelf = false
			var a = 0
			val n = loadedParticipants.size

			while (a <= n) {
				var participant: TL_groupCallParticipant

				if (a == n) {
					if (fromBegin && oldSelf != null && !hasSelf) {
						participant = oldSelf
					}
					else {
						a++
						continue
					}
				}
				else {
					participant = loadedParticipants[a]

					if (participant.self) {
						hasSelf = true
					}
				}

				var oldParticipant = participants[MessageObject.getPeerId(participant.peer)]

				if (oldParticipant != null) {
					sortedParticipants.remove(oldParticipant)

					processAllSources(oldParticipant, false)

					if (oldParticipant.self) {
						participant.lastTypingDate = oldParticipant.active_date
					}
					else {
						participant.lastTypingDate = max(participant.active_date, oldParticipant.active_date)
					}

					if (time != participant.lastVisibleDate) {
						participant.active_date = participant.lastTypingDate
					}
				}
				else if (old != null) {
					oldParticipant = old[MessageObject.getPeerId(participant.peer)]

					if (oldParticipant != null) {
						if (oldParticipant.self) {
							participant.lastTypingDate = oldParticipant.active_date
						}
						else {
							participant.lastTypingDate = max(participant.active_date, oldParticipant.active_date)
						}

						if (time != participant.lastVisibleDate) {
							participant.active_date = participant.lastTypingDate
						}
						else {
							participant.active_date = oldParticipant.active_date
						}
					}
				}

				participants.put(MessageObject.getPeerId(participant.peer), participant)
				sortedParticipants.add(participant)
				processAllSources(participant, true)

				a++
			}

			if (call!!.participants_count < participants.size()) {
				call!!.participants_count = participants.size()
			}

			sortParticipants()

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)

			setParticipantsVolume()
		}

		fun loadMembers(fromBegin: Boolean) {
			if (fromBegin) {
				if (reloadingMembers) {
					return
				}

				membersLoadEndReached = false
				nextLoadOffset = null
			}

			if (membersLoadEndReached || sortedParticipants.size > MAX_PARTICIPANTS_COUNT) {
				return
			}

			if (fromBegin) {
				reloadingMembers = true
			}

			loadingMembers = true

			val req = TL_phone_getGroupParticipants()
			req.call = inputGroupCall
			req.offset = nextLoadOffset ?: ""
			req.limit = 20

			currentAccount?.connectionsManager?.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					loadingMembers = false

					if (fromBegin) {
						reloadingMembers = false
					}

					if (response != null) {
						val groupParticipants = response as TL_phone_groupParticipants

						currentAccount?.messagesController?.putUsers(groupParticipants.users, false)
						currentAccount?.messagesController?.putChats(groupParticipants.chats, false)

						onParticipantsLoad(groupParticipants.participants, fromBegin, req.offset, groupParticipants.next_offset, groupParticipants.version, groupParticipants.count)
					}
				}
			}
		}

		private fun setParticipantsVolume() {
			val voIPService = sharedInstance

			if (voIPService != null && voIPService.getAccount() == currentAccount?.currentAccount && voIPService.getChat()?.id == -chatId) {
				voIPService.setParticipantsVolume()
			}
		}

		fun setTitle(title: String?) {
			val req = TL_phone_editGroupCallTitle()
			req.call = inputGroupCall
			req.title = title

			currentAccount?.connectionsManager?.sendRequest(req) { response, _ ->
				if (response != null) {
					val res = response as Updates
					currentAccount?.messagesController?.processUpdates(res, false)
				}
			}
		}

		fun addInvitedUser(uid: Long) {
			if (participants[uid] != null || invitedUsersMap.contains(uid)) {
				return
			}

			invitedUsersMap.add(uid)
			invitedUsers.add(uid)
		}

		fun processTypingsUpdate(uids: List<Long>, date: Int) {
			var updated = false
			var participantsToLoad: MutableList<Long>? = null
			val time = SystemClock.elapsedRealtime()

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time)

			for (id in uids) {
				val participant = participants[id]

				if (participant != null) {
					if (date - participant.lastTypingDate > 10) {
						if (participant.lastVisibleDate != date.toLong()) {
							participant.active_date = date
						}

						participant.lastTypingDate = date

						updated = true
					}
				}
				else {
					if (participantsToLoad == null) {
						participantsToLoad = mutableListOf()
					}

					participantsToLoad.add(id)
				}
			}

			if (!participantsToLoad.isNullOrEmpty()) {
				loadUnknownParticipants(participantsToLoad, true, null)
			}

			if (updated) {
				sortParticipants()
				currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
			}
		}

		private fun loadUnknownParticipants(participantsToLoad: MutableList<Long>, isIds: Boolean, onLoad: OnParticipantsLoad?) {
			val set = if (isIds) loadingUids else loadingSsrcs

			var a = 0
			var n = participantsToLoad.size

			while (a < n) {
				if (set.contains(participantsToLoad[a])) {
					participantsToLoad.removeAt(a)
					a--
					n--
				}

				a++
			}

			if (participantsToLoad.isEmpty()) {
				return
			}

			val guid = ++lastLoadGuid

			loadingGuids.add(guid)

			set.addAll(participantsToLoad)

			val req = TL_phone_getGroupParticipants()
			req.call = inputGroupCall

			for (uid in participantsToLoad) {
				if (isIds) {
					if (uid > 0) {
						val peerUser = TL_inputPeerUser()
						peerUser.user_id = uid
						req.ids.add(peerUser)
					}
					else {
						val chat = currentAccount!!.messagesController.getChat(-uid)
						var inputPeer: InputPeer
						if (chat == null || isChannel(chat)) {
							inputPeer = TL_inputPeerChannel()
							inputPeer.channel_id = -uid
						}
						else {
							inputPeer = TL_inputPeerChat()
							inputPeer.chat_id = -uid
						}
						req.ids.add(inputPeer)
					}
				}
				else {
					req.sources.add(uid.toInt())
				}
			}

			req.offset = ""
			req.limit = 100

			currentAccount?.connectionsManager?.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (!loadingGuids.remove(guid)) {
						return@runOnUIThread
					}

					if (response != null) {
						val groupParticipants = response as TL_phone_groupParticipants

						currentAccount?.messagesController?.putUsers(groupParticipants.users, false)
						currentAccount?.messagesController?.putChats(groupParticipants.chats, false)

						groupParticipants.participants.forEach { participant ->
							val pid = MessageObject.getPeerId(participant.peer)
							val oldParticipant = participants[pid]

							if (oldParticipant != null) {
								sortedParticipants.remove(oldParticipant)
								processAllSources(oldParticipant, false)
							}

							participants.put(pid, participant)

							sortedParticipants.add(participant)

							processAllSources(participant, true)

							if (invitedUsersMap.contains(pid)) {
								invitedUsersMap.remove(pid)
								invitedUsers.remove(pid)
							}
						}

						if (call!!.participants_count < participants.size()) {
							call!!.participants_count = participants.size()
						}

						sortParticipants()

						currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)

						if (onLoad != null) {
							onLoad.onLoad(participantsToLoad)
						}
						else {
							setParticipantsVolume()
						}
					}

					set.removeAll(participantsToLoad.toSet())
				}
			}
		}

		private fun processAllSources(participant: TL_groupCallParticipant, add: Boolean) {
			if (participant.source != 0) {
				if (add) {
					participantsBySources.put(participant.source, participant)
				}
				else {
					participantsBySources.remove(participant.source)
				}
			}

			for (c in 0..1) {
				val data = if (c == 0) participant.video else participant.presentation

				if (data != null) {
					if (data.flags and 2 != 0 && data.audio_source != 0) {
						if (add) {
							participantsBySources.put(data.audio_source, participant)
						}
						else {
							participantsBySources.remove(data.audio_source)
						}
					}

					val sourcesArray = if (c == 0) participantsByVideoSources else participantsByPresentationSources
					var a = 0
					val n = data.source_groups.size

					while (a < n) {
						val sourceGroup = data.source_groups[a]
						var b = 0
						val n2 = sourceGroup.sources.size

						while (b < n2) {
							val source = sourceGroup.sources[b]

							if (add) {
								sourcesArray.put(source, participant)
							}
							else {
								sourcesArray.remove(source)
							}
							b++
						}

						a++
					}

					if (add) {
						if (c == 0) {
							participant.videoEndpoint = data.endpoint
						}
						else {
							participant.presentationEndpoint = data.endpoint
						}
					}
					else {
						if (c == 0) {
							participant.videoEndpoint = null
						}
						else {
							participant.presentationEndpoint = null
						}
					}
				}
			}
		}

		fun processVoiceLevelsUpdate(ssrc: IntArray, levels: FloatArray, voice: BooleanArray) {
			var updated = false
			var updateCurrentSpeakingList = false
			val currentTime = currentAccount!!.connectionsManager.currentTime
			var participantsToLoad: MutableList<Long>? = null
			val time = SystemClock.elapsedRealtime()
			val uptime = SystemClock.uptimeMillis()

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time)

			for (a in ssrc.indices) {
				val participant = if (ssrc[a] == 0) {
					participants[selfId]
				}
				else {
					participantsBySources[ssrc[a]]
				}

				if (participant != null) {
					participant.hasVoice = voice[a]

					if (voice[a] || time - participant.lastVoiceUpdateTime > 500) {
						participant.hasVoiceDelayed = voice[a]
						participant.lastVoiceUpdateTime = time
					}

					val peerId = MessageObject.getPeerId(participant.peer)

					if (levels[a] > 0.1f) {
						if (voice[a] && participant.lastTypingDate + 1 < currentTime) {
							if (time != participant.lastVisibleDate) {
								participant.active_date = currentTime
							}

							participant.lastTypingDate = currentTime

							updated = true
						}

						participant.lastSpeakTime = uptime
						participant.amplitude = levels[a]

						if (currentSpeakingPeers[peerId, null] == null) {
							if (peerId > 0) {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(peerId)
								FileLog.d("GroupCall: add to current speaking " + peerId + " " + user?.first_name)
							}
							else {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getChat(-peerId)
								FileLog.d("GroupCall: add to current speaking " + peerId + " " + user?.title)
							}

							currentSpeakingPeers.put(peerId, participant)

							updateCurrentSpeakingList = true
						}
					}
					else {
						if (uptime - participant.lastSpeakTime >= 500) {
							if (currentSpeakingPeers[peerId, null] != null) {
								currentSpeakingPeers.remove(peerId)

								if (peerId > 0) {
									val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(peerId)
									FileLog.d("GroupCall: remove from speaking " + peerId + " " + user?.first_name)
								}
								else {
									val user = MessagesController.getInstance(currentAccount!!.currentAccount).getChat(-peerId)
									FileLog.d("GroupCall: remove from speaking " + peerId + " " + user?.title)
								}

								updateCurrentSpeakingList = true
							}
						}

						participant.amplitude = 0f
					}
				}
				else if (ssrc[a] != 0) {
					if (participantsToLoad == null) {
						participantsToLoad = mutableListOf()
					}

					participantsToLoad.add(ssrc[a].toLong())
				}
			}

			if (participantsToLoad != null) {
				loadUnknownParticipants(participantsToLoad, false, null)
			}

			if (updated) {
				sortParticipants()
				currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
			}

			if (updateCurrentSpeakingList) {
				if (currentSpeakingPeers.size() > 0) {
					AndroidUtilities.cancelRunOnUIThread(updateCurrentSpeakingRunnable)
					AndroidUtilities.runOnUIThread(updateCurrentSpeakingRunnable, 550)
				}

				currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallSpeakingUsersUpdated, chatId, call!!.id, false)
			}
		}

		fun updateVisibleParticipants() {
			sortParticipants()
			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false, 0L)
		}

		fun clearVideFramesInfo() {
			for (p in sortedParticipants) {
				p.hasCameraFrame = VIDEO_FRAME_NO_FRAME
				p.hasPresentationFrame = VIDEO_FRAME_NO_FRAME
				p.videoIndex = 0
			}

			sortParticipants()
		}

		fun interface OnParticipantsLoad {
			fun onLoad(ssrcs: List<Long>?)
		}

		fun processUnknownVideoParticipants(ssrc: IntArray, onLoad: OnParticipantsLoad) {
			var participantsToLoad: MutableList<Long>? = null

			for (a in ssrc.indices) {
				if (participantsBySources[ssrc[a]] != null || participantsByVideoSources[ssrc[a]] != null || participantsByPresentationSources[ssrc[a]] != null) {
					continue
				}
				if (participantsToLoad == null) {
					participantsToLoad = mutableListOf()
				}

				participantsToLoad.add(ssrc[a].toLong())
			}

			if (participantsToLoad != null) {
				loadUnknownParticipants(participantsToLoad, false, onLoad)
			}
			else {
				onLoad.onLoad(null)
			}
		}

		private fun isValidUpdate(update: TL_updateGroupCallParticipants): Int {
			return if (call!!.version + 1 == update.version || call!!.version == update.version) {
				0
			}
			else if (call!!.version < update.version) {
				1
			}
			else {
				2
			}
		}

		fun setSelfPeer(peer: InputPeer?) {
			if (peer == null) {
				selfPeer = null
			}
			else {
				when (peer) {
					is TL_inputPeerUser -> {
						selfPeer = TL_peerUser()
						selfPeer?.user_id = peer.user_id
					}

					is TL_inputPeerChat -> {
						selfPeer = TL_peerChat()
						selfPeer?.chat_id = peer.chat_id
					}

					else -> {
						selfPeer = TL_peerChannel()
						selfPeer?.channel_id = peer.channel_id
					}
				}
			}
		}

		private fun processUpdatesQueue() {
			updatesQueue.sortWith { updates, updates2 ->
				AndroidUtilities.compare(updates.version, updates2.version)
			}

			if (updatesQueue.isNotEmpty()) {
				var anyProceed = false
				var a = 0

				while (a < updatesQueue.size) {
					val update = updatesQueue[a]

					when (isValidUpdate(update)) {
						0 -> {
							processParticipantsUpdate(update, true)
							anyProceed = true
							updatesQueue.removeAt(a)
							a--
						}

						1 -> {
							if (updatesStartWaitTime != 0L && (anyProceed || abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500)) {
								if (BuildConfig.DEBUG) {
									FileLog.d("HOLE IN GROUP CALL UPDATES QUEUE - will wait more time")
								}

								if (anyProceed) {
									updatesStartWaitTime = System.currentTimeMillis()
								}
							}
							else {
								if (BuildConfig.DEBUG) {
									FileLog.d("HOLE IN GROUP CALL UPDATES QUEUE - reload participants")
								}

								updatesStartWaitTime = 0
								updatesQueue.clear()
								nextLoadOffset = null

								loadMembers(true)
							}

							return
						}

						else -> {
							updatesQueue.removeAt(a)
							a--
						}
					}
					a++
				}

				updatesQueue.clear()

				if (BuildConfig.DEBUG) {
					FileLog.d("GROUP CALL UPDATES QUEUE PROCEED - OK")
				}
			}

			updatesStartWaitTime = 0
		}

		private fun checkQueue() {
			checkQueueRunnable = null

			if (updatesStartWaitTime != 0L && System.currentTimeMillis() - updatesStartWaitTime >= 1500) {
				if (BuildConfig.DEBUG) {
					FileLog.d("QUEUE GROUP CALL UPDATES WAIT TIMEOUT - CHECK QUEUE")
				}

				processUpdatesQueue()
			}

			if (updatesQueue.isNotEmpty()) {
				AndroidUtilities.runOnUIThread(Runnable { checkQueue() }.also { checkQueueRunnable = it }, 1000)
			}
		}

		fun reloadGroupCall() {
			val req = TL_phone_getGroupCall()
			req.call = inputGroupCall
			req.limit = 100

			currentAccount?.connectionsManager?.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (response is TL_phone_groupCall) {
						call = response.call

						currentAccount?.messagesController?.putUsers(response.users, false)
						currentAccount?.messagesController?.putChats(response.chats, false)

						onParticipantsLoad(response.participants, true, "", response.participants_next_offset, response.call.version, response.call.participants_count)
					}
				}
			}
		}

		private fun loadGroupCall() {
			if (loadingGroupCall || SystemClock.elapsedRealtime() - lastGroupCallReloadTime < 30000) {
				return
			}

			loadingGroupCall = true

			val req = TL_phone_getGroupParticipants()
			req.call = inputGroupCall
			req.offset = ""
			req.limit = 1

			currentAccount?.connectionsManager?.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					lastGroupCallReloadTime = SystemClock.elapsedRealtime()
					loadingGroupCall = false

					if (response != null) {
						val res = response as TL_phone_groupParticipants

						currentAccount?.messagesController?.putUsers(res.users, false)
						currentAccount?.messagesController?.putChats(res.chats, false)

						if (call?.participants_count != res.count) {
							call?.participants_count = res.count

							if (BuildConfig.DEBUG) {
								FileLog.d("new participants reload count ${call?.participants_count}")
							}

							currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
						}
					}
				}
			}
		}

		fun processParticipantsUpdate(update: TL_updateGroupCallParticipants, fromQueue: Boolean) {
			if (!fromQueue) {
				var versioned = false
				var a = 0
				val n = update.participants.size

				while (a < n) {
					val participant = update.participants[a]

					if (participant.versioned) {
						versioned = true
						break
					}

					a++
				}

				if (versioned && call!!.version + 1 < update.version) {
					if (reloadingMembers || updatesStartWaitTime == 0L || abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500) {
						if (updatesStartWaitTime == 0L) {
							updatesStartWaitTime = System.currentTimeMillis()
						}

						if (BuildConfig.DEBUG) {
							FileLog.d("add TL_updateGroupCallParticipants to queue " + update.version)
						}

						updatesQueue.add(update)

						if (checkQueueRunnable == null) {
							AndroidUtilities.runOnUIThread(Runnable { checkQueue() }.also { checkQueueRunnable = it }, 1500)
						}
					}
					else {
						nextLoadOffset = null
						loadMembers(true)
					}

					return
				}

				if (versioned && update.version < call!!.version) {
					if (BuildConfig.DEBUG) {
						FileLog.d("ignore processParticipantsUpdate because of version")
					}

					return
				}
			}

			var reloadCall = false
			var updated = false
			var selfUpdated = false
			var changedOrAdded = false
			var speakingUpdated = false
			val selfId = selfId
			val time = SystemClock.elapsedRealtime()
			var justJoinedId: Long = 0

			val lastParticipantDate = if (sortedParticipants.isNotEmpty()) {
				sortedParticipants[sortedParticipants.size - 1].date
			}
			else {
				0
			}

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time)

			var a = 0
			val n = update.participants.size

			while (a < n) {
				val participant = update.participants[a]
				val pid = MessageObject.getPeerId(participant.peer)

				if (BuildConfig.DEBUG) {
					FileLog.d("process participant " + pid + " left = " + participant.left + " versioned " + participant.versioned + " flags = " + participant.flags + " self = " + selfId + " volume = " + participant.volume)
				}

				val oldParticipant = participants[pid]

				if (participant.left) {
					if (oldParticipant == null && update.version == call!!.version) {
						if (BuildConfig.DEBUG) {
							FileLog.d("Unknown participant left, reload call")
						}

						reloadCall = true
					}

					if (oldParticipant != null) {
						participants.remove(pid)

						processAllSources(oldParticipant, false)

						sortedParticipants.remove(oldParticipant)
						visibleParticipants.remove(oldParticipant)

						if (currentSpeakingPeers[pid, null] != null) {
							if (pid > 0) {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(pid)
								FileLog.d("GroupCall: left, remove from speaking " + pid + " " + user?.first_name)
							}
							else {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getChat(-pid)
								FileLog.d("GroupCall: left, remove from speaking " + pid + " " + user?.title)
							}

							currentSpeakingPeers.remove(pid)

							speakingUpdated = true
						}

						var i = 0

						while (i < visibleVideoParticipants.size) {
							val videoParticipant = visibleVideoParticipants[i]

							if (MessageObject.getPeerId(videoParticipant.participant.peer) == MessageObject.getPeerId(oldParticipant.peer)) {
								visibleVideoParticipants.removeAt(i)
								i--
							}

							i++
						}
					}

					call!!.participants_count--

					if (call!!.participants_count < 0) {
						call!!.participants_count = 0
					}

					updated = true
				}
				else {
					if (invitedUsersMap.contains(pid)) {
						invitedUsersMap.remove(pid)
						invitedUsers.remove(pid)
					}

					if (oldParticipant != null) {
						if (BuildConfig.DEBUG) {
							FileLog.d("new participant, update old")
						}

						oldParticipant.muted = participant.muted

						if (participant.muted && currentSpeakingPeers[pid, null] != null) {
							currentSpeakingPeers.remove(pid)

							if (pid > 0) {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(pid)
								FileLog.d("GroupCall: muted remove from speaking " + pid + " " + user?.first_name)
							}
							else {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getChat(-pid)
								FileLog.d("GroupCall: muted remove from speaking " + pid + " " + user?.title)
							}

							speakingUpdated = true
						}

						if (!participant.min) {
							oldParticipant.volume = participant.volume
							oldParticipant.muted_by_you = participant.muted_by_you
						}
						else {
							if (participant.flags and 128 != 0 && oldParticipant.flags and 128 == 0) {
								participant.flags = participant.flags and 128.inv()
							}

							if (participant.volume_by_admin && oldParticipant.volume_by_admin) {
								oldParticipant.volume = participant.volume
							}
						}

						oldParticipant.flags = participant.flags
						oldParticipant.can_self_unmute = participant.can_self_unmute
						oldParticipant.video_joined = participant.video_joined

						if (oldParticipant.raise_hand_rating == 0L && participant.raise_hand_rating != 0L) {
							oldParticipant.lastRaiseHandDate = SystemClock.elapsedRealtime()
						}

						oldParticipant.raise_hand_rating = participant.raise_hand_rating
						oldParticipant.date = participant.date
						oldParticipant.lastTypingDate = max(oldParticipant.active_date, participant.active_date)

						if (time != oldParticipant.lastVisibleDate) {
							oldParticipant.active_date = oldParticipant.lastTypingDate
						}

						if (oldParticipant.source != participant.source || !isSameVideo(oldParticipant.video, participant.video) || !isSameVideo(oldParticipant.presentation, participant.presentation)) {
							processAllSources(oldParticipant, false)

							oldParticipant.video = participant.video
							oldParticipant.presentation = participant.presentation
							oldParticipant.source = participant.source

							processAllSources(oldParticipant, true)

							participant.presentationEndpoint = oldParticipant.presentationEndpoint
							participant.videoEndpoint = oldParticipant.videoEndpoint
							participant.videoIndex = oldParticipant.videoIndex
						}
						else if (oldParticipant.video != null && participant.video != null) {
							oldParticipant.video.paused = participant.video.paused
						}
					}
					else {
						if (participant.just_joined) {
							if (pid != selfId) {
								justJoinedId = pid
							}

							call!!.participants_count++

							if (update.version == call!!.version) {
								reloadCall = true

								if (BuildConfig.DEBUG) {
									FileLog.d("new participant, just joined, reload call")
								}
							}
							else {
								if (BuildConfig.DEBUG) {
									FileLog.d("new participant, just joined")
								}
							}
						}

						if (participant.raise_hand_rating != 0L) {
							participant.lastRaiseHandDate = SystemClock.elapsedRealtime()
						}

						if (pid == selfId || sortedParticipants.size < 20 || participant.date <= lastParticipantDate || participant.active_date != 0 || participant.can_self_unmute || !participant.muted || !participant.min || membersLoadEndReached) {
							sortedParticipants.add(participant)
						}

						participants.put(pid, participant)

						processAllSources(participant, true)
					}

					if (pid == selfId && participant.active_date == 0 && (participant.can_self_unmute || !participant.muted)) {
						participant.active_date = currentAccount!!.connectionsManager.currentTime
					}

					changedOrAdded = true
					updated = true
				}

				if (pid == selfId) {
					selfUpdated = true
				}

				a++
			}

			if (update.version > call!!.version) {
				call!!.version = update.version

				if (!fromQueue) {
					processUpdatesQueue()
				}
			}

			if (call!!.participants_count < participants.size()) {
				call!!.participants_count = participants.size()
			}

			if (BuildConfig.DEBUG) {
				FileLog.d("new participants count after update " + call!!.participants_count)
			}

			if (reloadCall) {
				loadGroupCall()
			}

			if (updated) {
				if (changedOrAdded) {
					sortParticipants()
				}

				currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, selfUpdated, justJoinedId)
			}

			if (speakingUpdated) {
				currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallSpeakingUsersUpdated, chatId, call!!.id, false)
			}
		}

		private fun isSameVideo(oldVideo: TL_groupCallParticipantVideo?, newVideo: TL_groupCallParticipantVideo?): Boolean {
			if (oldVideo == null && newVideo != null || oldVideo != null && newVideo == null) {
				return false
			}

			if (oldVideo == null || newVideo == null) {
				return true
			}

			if (!TextUtils.equals(oldVideo.endpoint, newVideo.endpoint)) {
				return false
			}

			if (oldVideo.source_groups.size != newVideo.source_groups.size) {
				return false
			}

			var a = 0
			val n = oldVideo.source_groups.size

			while (a < n) {
				val oldGroup = oldVideo.source_groups[a]
				val newGroup = newVideo.source_groups[a]

				if (!TextUtils.equals(oldGroup.semantics, newGroup.semantics)) {
					return false
				}

				if (oldGroup.sources.size != newGroup.sources.size) {
					return false
				}

				var b = 0
				val n2 = oldGroup.sources.size

				while (b < n2) {
					if (!newGroup.sources.contains(oldGroup.sources[b])) {
						return false
					}

					b++
				}

				a++
			}

			return true
		}

		fun processGroupCallUpdate(update: TL_updateGroupCall) {
			if (call!!.version < update.call.version) {
				nextLoadOffset = null
				loadMembers(true)
			}

			call = update.call
			recording = call!!.record_start_date != 0

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
		}

		val inputGroupCall: TL_inputGroupCall
			get() {
				val inputGroupCall = TL_inputGroupCall()
				inputGroupCall.id = call!!.id
				inputGroupCall.access_hash = call!!.access_hash
				return inputGroupCall
			}

		fun sortParticipants() {
			visibleVideoParticipants.clear()
			visibleParticipants.clear()

			val chat = currentAccount!!.messagesController.getChat(chatId)
			val isAdmin = canManageCalls(chat)

			if (rtmpStreamParticipant != null) {
				visibleVideoParticipants.add(rtmpStreamParticipant!!)
			}

			val selfId = selfId
			// val service = sharedInstance
			// val selfParticipant = participants[selfId]
			canStreamVideo = true //selfParticipant != null && selfParticipant.video_joined || BuildVars.DEBUG_PRIVATE_VERSION;
			// var allowedVideoCount: Boolean

			var hasAnyVideo = false

			activeVideos = 0

			var i = 0
			val n = sortedParticipants.size

			while (i < n) {
				val participant = sortedParticipants[i]
				val cameraActive = videoIsActive(participant, false, this)
				val screenActive = videoIsActive(participant, true, this)

				if (!participant.self && (cameraActive || screenActive)) {
					activeVideos++
				}

				if (cameraActive || screenActive) {
					hasAnyVideo = true

					if (canStreamVideo) {
						if (participant.videoIndex == 0) {
							if (participant.self) {
								participant.videoIndex = Int.MAX_VALUE
							}
							else {
								participant.videoIndex = ++videoPointer
							}
						}
					}
					else {
						participant.videoIndex = 0
					}
				}
				else if ((participant.self || !canStreamVideo || participant.video == null) && participant.presentation == null) {
					participant.videoIndex = 0
				}

				i++
			}

			val comparator = Comparator { o1: TL_groupCallParticipant, o2: TL_groupCallParticipant ->
				val videoActive1 = o1.videoIndex > 0
				val videoActive2 = o2.videoIndex > 0

				if (videoActive1 && videoActive2) {
					return@Comparator o2.videoIndex - o1.videoIndex
				}
				else if (videoActive1) {
					return@Comparator -1
				}
				else if (videoActive2) {
					return@Comparator 1
				}

				if (o1.active_date != 0 && o2.active_date != 0) {
					return@Comparator o2.active_date.compareTo(o1.active_date)
				}
				else if (o1.active_date != 0) {
					return@Comparator -1
				}
				else if (o2.active_date != 0) {
					return@Comparator 1
				}

				if (MessageObject.getPeerId(o1.peer) == selfId) {
					return@Comparator -1
				}
				else if (MessageObject.getPeerId(o2.peer) == selfId) {
					return@Comparator 1
				}

				if (isAdmin) {
					if (o1.raise_hand_rating != 0L && o2.raise_hand_rating != 0L) {
						return@Comparator o2.raise_hand_rating.compareTo(o1.raise_hand_rating)
					}
					else if (o1.raise_hand_rating != 0L) {
						return@Comparator -1
					}
					else if (o2.raise_hand_rating != 0L) {
						return@Comparator 1
					}
				}

				if (call!!.join_date_asc) {
					return@Comparator o1.date.compareTo(o2.date)
				}
				else {
					return@Comparator o2.date.compareTo(o1.date)
				}
			}

			sortedParticipants.sortWith(comparator)

			val lastParticipant = if (sortedParticipants.isEmpty()) null else sortedParticipants[sortedParticipants.size - 1]

			if (videoIsActive(lastParticipant, false, this) || videoIsActive(lastParticipant, true, this)) {
				if (call!!.unmuted_video_count > activeVideos) {
					activeVideos = call!!.unmuted_video_count

					val voIPService = sharedInstance

					if (voIPService != null && voIPService.groupCall === this) {
						if (voIPService.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || voIPService.getVideoState(true) == Instance.VIDEO_STATE_ACTIVE) {
							activeVideos--
						}
					}
				}
			}

			if (sortedParticipants.size > MAX_PARTICIPANTS_COUNT && (!canManageCalls(chat) || lastParticipant!!.raise_hand_rating == 0L)) {
				var a = MAX_PARTICIPANTS_COUNT
				@Suppress("NAME_SHADOWING") val n = sortedParticipants.size

				while (a < n) {
					val p = sortedParticipants[MAX_PARTICIPANTS_COUNT]

					if (p.raise_hand_rating != 0L) {
						a++
						continue
					}

					processAllSources(p, false)
					participants.remove(MessageObject.getPeerId(p.peer))
					sortedParticipants.removeAt(MAX_PARTICIPANTS_COUNT)

					a++
				}
			}

			checkOnlineParticipants()

			if (!canStreamVideo && hasAnyVideo && videoNotAvailableParticipant != null) {
				visibleVideoParticipants.add(videoNotAvailableParticipant!!)
			}

			var wideVideoIndex = 0

			sortedParticipants.forEach { participant ->
				if (canStreamVideo && participant.videoIndex != 0) {
					if (!participant.self && videoIsActive(participant, true, this) && videoIsActive(participant, false, this)) {
						var videoParticipant = videoParticipantsCache[participant.videoEndpoint]

						if (videoParticipant == null) {
							videoParticipant = VideoParticipant(participant, presentation = false, hasSame = true)
							videoParticipantsCache[participant.videoEndpoint] = videoParticipant
						}
						else {
							videoParticipant.participant = participant
							videoParticipant.presentation = false
							videoParticipant.hasSame = true
						}

						var presentationParticipant = videoParticipantsCache[participant.presentationEndpoint]

						if (presentationParticipant == null) {
							presentationParticipant = VideoParticipant(participant, presentation = true, hasSame = true)
						}
						else {
							presentationParticipant.participant = participant
							presentationParticipant.presentation = true
							presentationParticipant.hasSame = true
						}

						visibleVideoParticipants.add(videoParticipant)

						if (videoParticipant.aspectRatio > 1f) {
							wideVideoIndex = visibleVideoParticipants.size - 1
						}

						visibleVideoParticipants.add(presentationParticipant)

						if (presentationParticipant.aspectRatio > 1f) {
							wideVideoIndex = visibleVideoParticipants.size - 1
						}
					}
					else {
						if (participant.self) {
							if (videoIsActive(participant, true, this)) {
								visibleVideoParticipants.add(VideoParticipant(participant, presentation = true, hasSame = false))
							}
							if (videoIsActive(participant, false, this)) {
								visibleVideoParticipants.add(VideoParticipant(participant, presentation = false, hasSame = false))
							}
						}
						else {
							val presentation = videoIsActive(participant, true, this)
							var videoParticipant = videoParticipantsCache[if (presentation) participant.presentationEndpoint else participant.videoEndpoint]

							if (videoParticipant == null) {
								videoParticipant = VideoParticipant(participant, presentation, false)
								videoParticipantsCache[if (presentation) participant.presentationEndpoint else participant.videoEndpoint] = videoParticipant
							}
							else {
								videoParticipant.participant = participant
								videoParticipant.presentation = presentation
								videoParticipant.hasSame = false
							}

							visibleVideoParticipants.add(videoParticipant)

							if (videoParticipant.aspectRatio > 1f) {
								wideVideoIndex = visibleVideoParticipants.size - 1
							}
						}
					}
				}
				else {
					visibleParticipants.add(participant)
				}
			}

			if (!GroupCallActivity.isLandscapeMode && visibleVideoParticipants.size % 2 == 1) {
				val videoParticipant = visibleVideoParticipants.removeAt(wideVideoIndex)
				visibleVideoParticipants.add(videoParticipant)
			}
		}

		fun canRecordVideo(): Boolean {
			if (!canStreamVideo) {
				return false
			}

			val voIPService = sharedInstance

			return if (voIPService != null && voIPService.groupCall === this && (voIPService.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || voIPService.getVideoState(true) == Instance.VIDEO_STATE_ACTIVE)) {
				true
			}
			else {
				activeVideos < call!!.unmuted_video_limit
			}
		}

		fun saveActiveDates() {
			for (p in sortedParticipants) {
				p.lastActiveDate = p.active_date.toLong()
			}
		}

		private fun checkOnlineParticipants() {
			if (typingUpdateRunnableScheduled) {
				AndroidUtilities.cancelRunOnUIThread(typingUpdateRunnable)
				typingUpdateRunnableScheduled = false
			}

			speakingMembersCount = 0

			val currentTime = currentAccount!!.connectionsManager.currentTime
			var minDiff = Int.MAX_VALUE
			var a = 0
			val n = sortedParticipants.size

			while (a < n) {
				val participant = sortedParticipants[a]
				val diff = currentTime - participant.active_date

				if (diff < 5) {
					speakingMembersCount++
					minDiff = min(diff, minDiff)
				}

				if (max(participant.date, participant.active_date) <= currentTime - 5) {
					break
				}

				a++
			}

			if (minDiff != Int.MAX_VALUE) {
				AndroidUtilities.runOnUIThread(typingUpdateRunnable, (minDiff * 1000).toLong())
				typingUpdateRunnableScheduled = true
			}
		}

		fun toggleRecord(title: String?, @RecordType type: Int) {
			recording = !recording

			val req = TL_phone_toggleGroupCallRecord()
			req.call = inputGroupCall
			req.start = recording

			if (title != null) {
				req.title = title
				req.flags = req.flags or 2
			}

			if (type == RECORD_TYPE_VIDEO_PORTRAIT || type == RECORD_TYPE_VIDEO_LANDSCAPE) {
				req.flags = req.flags or 4
				req.video = true
				req.video_portrait = type == RECORD_TYPE_VIDEO_PORTRAIT
			}

			currentAccount?.connectionsManager?.sendRequest(req) { response, _ ->
				if (response != null) {
					val res = response as Updates
					currentAccount?.messagesController?.processUpdates(res, false)
				}
			}

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
		}

		companion object {
			const val RECORD_TYPE_AUDIO = 0
			const val RECORD_TYPE_VIDEO_PORTRAIT = 1
			const val RECORD_TYPE_VIDEO_LANDSCAPE = 2
			private var videoPointer = 0

			@JvmStatic
			fun videoIsActive(participant: TL_groupCallParticipant?, presentation: Boolean, call: Call): Boolean {
				if (participant == null) {
					return false
				}

				val service = sharedInstance ?: return false

				return if (participant.self) {
					service.getVideoState(presentation) == Instance.VIDEO_STATE_ACTIVE
				}
				else {
					if ((call.rtmpStreamParticipant != null && call.rtmpStreamParticipant!!.participant === participant || call.videoNotAvailableParticipant != null) && call.videoNotAvailableParticipant!!.participant === participant || call.participants[MessageObject.getPeerId(participant.peer)] != null) {
						if (presentation) {
							participant.presentation != null // && participant.hasPresentationFrame == 2;
						}
						else {
							participant.video != null // && participant.hasCameraFrame == 2;
						}
					}
					else {
						false
					}
				}
			}
		}
	}

	class VideoParticipant(@JvmField var participant: TL_groupCallParticipant, @JvmField var presentation: Boolean, var hasSame: Boolean) {
		@JvmField
		var aspectRatio = 0f // w / h
		private var aspectRatioFromWidth = 0
		private var aspectRatioFromHeight = 0

		override fun equals(other: Any?): Boolean {
			if (this === other) {
				return true
			}

			if (other == null || other !is VideoParticipant) {
				return false
			}

			return presentation == other.presentation && MessageObject.getPeerId(participant.peer) == MessageObject.getPeerId(other.participant.peer)
		}

		fun setAspectRatio(width: Int, height: Int, call: Call) {
			aspectRatioFromWidth = width
			aspectRatioFromHeight = height
			setAspectRatio(width / height.toFloat(), call)
		}

		private fun setAspectRatio(aspectRatio: Float, call: Call) {
			if (this.aspectRatio != aspectRatio) {
				this.aspectRatio = aspectRatio

				if (!GroupCallActivity.isLandscapeMode && call.visibleVideoParticipants.size % 2 == 1) {
					call.updateVisibleParticipants()
				}
			}
		}

		override fun hashCode(): Int {
			var result = participant.hashCode()
			result = 31 * result + presentation.hashCode()
			result = 31 * result + hasSame.hashCode()
			result = 31 * result + aspectRatio.hashCode()
			result = 31 * result + aspectRatioFromWidth
			result = 31 * result + aspectRatioFromHeight
			return result
		}
	}
}
