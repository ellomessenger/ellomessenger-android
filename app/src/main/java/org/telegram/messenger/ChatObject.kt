/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
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
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.ChatPhoto
import org.telegram.tgnet.TLRPC.GroupCall
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.channelId
import org.telegram.tgnet.userId
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
		if (chatInfo?.availableReactions is TLRPC.TLChatReactionsAll) {
			return true
		}

		if (chatInfo?.availableReactions is TLRPC.TLChatReactionsSome) {
			val someReactions = chatInfo.availableReactions as TLRPC.TLChatReactionsSome

			for (i in someReactions.reactions.indices) {
				if (someReactions.reactions[i] is TLRPC.TLReactionEmoji && TextUtils.equals((someReactions.reactions[i] as TLRPC.TLReactionEmoji).emoticon, reaction)) {
					return true
				}
			}
		}

		return false
	}

	@JvmStatic
	fun getParticipantVolume(participant: TLRPC.TLGroupCallParticipant?): Int {
		if (participant == null) {
			return 1000
		}

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

	private fun getBannedRight(rights: TLRPC.TLChatBannedRights?, action: Int): Boolean {
		if (rights == null) {
			return false
		}

		return when (action) {
			ACTION_PIN -> rights.pinMessages
			ACTION_CHANGE_INFO -> rights.changeInfo
			ACTION_INVITE -> rights.inviteUsers
			ACTION_SEND -> rights.sendMessages
			ACTION_SEND_MEDIA -> rights.sendMedia
			ACTION_SEND_STICKERS -> rights.sendStickers
			ACTION_EMBED_LINKS -> rights.embedLinks
			ACTION_SEND_POLLS -> rights.sendPolls
			ACTION_VIEW -> rights.viewMessages
			else -> false
		}
	}

	@JvmStatic
	fun isActionBannedByDefault(chat: Chat?, action: Int): Boolean {
		return if (getBannedRight(chat?.bannedRights, action)) {
			false
		}
		else {
			getBannedRight(chat?.defaultBannedRights, action)
		}
	}

	@JvmStatic
	fun isActionBanned(chat: Chat?, action: Int): Boolean {
		return chat != null && (getBannedRight(chat.bannedRights, action) || getBannedRight(chat.defaultBannedRights, action))
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

		val adminRights = chat.adminRights

		if (adminRights != null) {
			return when (action) {
				ACTION_PIN -> adminRights.pinMessages
				ACTION_CHANGE_INFO -> adminRights.changeInfo
				ACTION_INVITE -> adminRights.inviteUsers
				ACTION_ADD_ADMINS -> adminRights.addAdmins
				ACTION_POST -> adminRights.postMessages
				ACTION_EDIT_MESSAGES -> adminRights.editMessages
				ACTION_DELETE_MESSAGES -> adminRights.deleteMessages
				ACTION_BLOCK_USERS -> adminRights.banUsers
				ACTION_MANAGE_CALLS -> adminRights.manageCall
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

		if (getBannedRight(chat.bannedRights, action)) {
			return false
		}

		if (isBannableAction(action)) {
			if (chat.adminRights != null && !isAdminAction(action)) {
				return true
			}

			val isBannedRight = getBannedRight(chat.defaultBannedRights, action)

			// MARK: This is new variant
			return !isBannedRight

			// MARK: This was old variant
//			if (chat.defaultBannedRights == null || getBannedRight(chat.defaultBannedRights, action)) {
//				return false;
//			}
		}

		return false
	}

	@JvmStatic
	fun isLeftFromChat(chat: Chat?): Boolean {
		return chat == null || chat is TLRPC.TLChatEmpty || chat is TLRPC.TLChatForbidden || chat is TLRPC.TLChannelForbidden || chat.left || (chat as? TLRPC.TLChat)?.deactivated == true
	}

	fun isKickedFromChat(chat: Chat?): Boolean {
		return chat == null || chat is TLRPC.TLChatEmpty || chat is TLRPC.TLChatForbidden || chat is TLRPC.TLChannelForbidden || (chat as? TLRPC.TLChat)?.deactivated == true || chat.bannedRights?.viewMessages == true
	}

	@JvmStatic
	fun isNotInChat(chat: Chat?): Boolean {
		return chat == null || chat is TLRPC.TLChatEmpty || chat is TLRPC.TLChatForbidden || chat is TLRPC.TLChannelForbidden || chat.left || (chat as? TLRPC.TLChat)?.deactivated == true
	}

	@JvmStatic
	fun canSendAsPeers(chat: Chat?): Boolean {
		return isChannel(chat) && chat.megagroup && (!chat.username.isNullOrEmpty() || chat.hasGeo || chat.hasLink)
	}

	@JvmStatic
	fun isChannel(chat: Chat?): Boolean {
		contract {
			returns(true) implies (chat != null && (chat is TLRPC.TLChannel || chat is TLRPC.TLChannelForbidden))
		}

		return chat is TLRPC.TLChannel || chat is TLRPC.TLChannelForbidden
	}

	@JvmStatic
	fun isChannelOrGiga(chat: Chat?): Boolean {
		return (chat is TLRPC.TLChannel || chat is TLRPC.TLChannelForbidden) && (!chat.megagroup || chat.gigagroup)
	}

	@JvmStatic
	fun isSubscriptionChannel(chat: Chat?): Boolean {
		return (chat is TLRPC.TLChannel || chat is TLRPC.TLChannelForbidden) && (chat.payType == TLRPC.PAY_TYPE_SUBSCRIBE)
	}

	@JvmStatic
	fun isMasterclass(chat: Chat?): Boolean {
		return (chat is TLRPC.TLChannel || chat is TLRPC.TLChannelForbidden) && (chat.payType == TLRPC.PAY_TYPE_BASE)
	}

	@JvmStatic
	fun isPaidChannel(chat: Chat?): Boolean {
		return (chat is TLRPC.TLChannel || chat is TLRPC.TLChannelForbidden) && (chat.payType > TLRPC.PAY_TYPE_NONE)
	}

	@JvmStatic
	fun isMegagroup(chat: Chat?): Boolean {
		return (chat is TLRPC.TLChannel || chat is TLRPC.TLChannelForbidden) && chat.megagroup
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
		return chat != null && (chat.creator || chat.adminRights != null && chat.adminRights!!.flags != 0)
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
		return chat?.adminRights?.anonymous == true
	}

	@JvmStatic
	fun getSendAsPeerId(chat: Chat?, chatFull: ChatFull?): Long {
		return getSendAsPeerId(chat, chatFull, false)
	}

	@JvmStatic
	fun getSendAsPeerId(chat: Chat?, chatFull: ChatFull?, invertChannel: Boolean): Long {
		if (chat != null && chatFull != null && chatFull.defaultSendAs != null) {
			val p = chatFull.defaultSendAs ?: return 0L
			return if (p.userId != 0L) p.userId else if (invertChannel) -p.channelId else p.channelId
		}

		return if (chat?.adminRights?.anonymous == true) {
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
			if (chat.megagroup && (chat.adminRights != null && (chat.adminRights?.postMessages == true || chat.adminRights?.addAdmins == true) || chat.creator)) {
				return true
			}
		}
		else {
			if ((chat as? TLRPC.TLChat)?.migratedTo == null) {
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
//		return canUserDoAction(chat, ACTION_PIN) || isChannel(chat) && !chat.megagroup && chat.adminRights != null && chat.adminRights.edit_messages
	}

	@JvmStatic
	fun isChannel(chatId: Long, currentAccount: Int): Boolean {
		val chat = MessagesController.getInstance(currentAccount).getChat(chatId)
		return chat is TLRPC.TLChannel || chat is TLRPC.TLChannelForbidden
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

		return !isChannel(chat) || chat.creator || chat.adminRights != null && chat.adminRights?.postMessages == true || !chat.broadcast && !chat.gigagroup || chat.gigagroup && hasAdminRights(chat)
	}

	fun getBannedRightsString(bannedRights: TLRPC.TLChatBannedRights?): String {
		var currentBannedRights = ""

		if (bannedRights != null) {
			currentBannedRights += if (bannedRights.viewMessages) 1 else 0
			currentBannedRights += if (bannedRights.sendMessages) 1 else 0
			currentBannedRights += if (bannedRights.sendMedia) 1 else 0
			currentBannedRights += if (bannedRights.sendStickers) 1 else 0
			currentBannedRights += if (bannedRights.sendGifs) 1 else 0
			currentBannedRights += if (bannedRights.sendGames) 1 else 0
			currentBannedRights += if (bannedRights.sendInline) 1 else 0
			currentBannedRights += if (bannedRights.embedLinks) 1 else 0
			currentBannedRights += if (bannedRights.sendPolls) 1 else 0
			currentBannedRights += if (bannedRights.inviteUsers) 1 else 0
			currentBannedRights += if (bannedRights.changeInfo) 1 else 0
			currentBannedRights += if (bannedRights.pinMessages) 1 else 0
			currentBannedRights += bannedRights.untilDate
		}

		return currentBannedRights
	}

	private fun hasPhoto(chat: Chat?): Boolean {
		return chat?.photo != null && chat.photo !is TLRPC.TLChatPhotoEmpty
	}

	fun getPhoto(chat: Chat?): ChatPhoto? {
		return if (hasPhoto(chat)) chat?.photo else null
	}

	class Call {
		@Retention(AnnotationRetention.SOURCE)
		@IntDef(RECORD_TYPE_AUDIO, RECORD_TYPE_VIDEO_PORTRAIT, RECORD_TYPE_VIDEO_LANDSCAPE)
		annotation class RecordType

		var participantsByVideoSources = SparseArray<TLRPC.TLGroupCallParticipant>()
		var participantsByPresentationSources = SparseArray<TLRPC.TLGroupCallParticipant>()
		private var nextLoadOffset: String? = null
		private var activeVideos = 0
		var rtmpStreamParticipant: VideoParticipant? = null
		var loadedRtmpStreamParticipant = false
		private var speakingMembersCount = 0
		private var typingUpdateRunnableScheduled = false
		private var lastLoadGuid = 0
		private val loadingGuids = HashSet<Int>()
		private val updatesQueue = mutableListOf<TLRPC.TLUpdateGroupCallParticipants>()
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
		var participants = LongSparseArray<TLRPC.TLGroupCallParticipant>()

		@JvmField
		val sortedParticipants = ArrayList<TLRPC.TLGroupCallParticipant>()

		@JvmField
		val visibleVideoParticipants = ArrayList<VideoParticipant>()

		@JvmField
		val visibleParticipants = ArrayList<TLRPC.TLGroupCallParticipant>()

		@JvmField
		val thumbs = HashMap<String, Bitmap>()

		private val videoParticipantsCache = HashMap<String, VideoParticipant>()

		@JvmField
		var invitedUsers = ArrayList<Long>()

		@JvmField
		var invitedUsersMap = HashSet<Long>()

		@JvmField
		var participantsBySources = SparseArray<TLRPC.TLGroupCallParticipant>()

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
			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallTypingsUpdated)
		}

		@JvmField
		var selfPeer: Peer? = null

		@JvmField
		val currentSpeakingPeers = LongSparseArray<TLRPC.TLGroupCallParticipant?>()

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
							FileLog.d("GroupCall: remove from speaking " + key + " " + user?.firstName)
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

		fun setCall(account: AccountInstance?, chatId: Long, groupCall: TLRPC.TLPhoneGroupCall?) {
			this.chatId = chatId

			currentAccount = account
			call = groupCall?.call
			recording = (call is TLRPC.TLGroupCall) && (call as? TLRPC.TLGroupCall)?.recordStartDate != 0

			var date = Int.MAX_VALUE

			groupCall?.participants?.forEach { participant ->
				participants.put(MessageObject.getPeerId(participant.peer), participant)
				sortedParticipants.add(participant)
				processAllSources(participant, true)
				date = min(date, participant.date)
			}

			sortParticipants()

			nextLoadOffset = groupCall?.participantsNextOffset

			loadMembers(true)

			createNoVideoParticipant()

			if ((call as? TLRPC.TLGroupCall)?.rtmpStream == true) {
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
		//                if (response instanceof TLRPC.TLRPC.TLPhoneGroupCallStreamChannels) {
		//                    TLRPC.TLRPC.TLPhoneGroupCallStreamChannels streamChannels = (TLRPC.TLRPC.TLPhoneGroupCallStreamChannels) response;
		//                    createRtmpStreamParticipant(streamChannels.channels);
		//                    loadedRtmpStreamParticipant = true;
		//                }
		//            }, ConnectionsManager.RequestFlagFailOnServerErrors, ConnectionsManager.ConnectionTypeDownload, call.stream_dc_id);
		//        }

		fun createRtmpStreamParticipant(channels: List<TLRPC.TLGroupCallStreamChannel>?) {
			if (loadedRtmpStreamParticipant && rtmpStreamParticipant != null) {
				return
			}

			val participant = rtmpStreamParticipant?.participant ?: TLRPC.TLGroupCallParticipant()
			participant.peer = TLRPC.TLPeerChat().also { it.chatId = chatId } // MARK: was participant.peer.channelId = chatId
			participant.video = TLRPC.TLGroupCallParticipantVideo()

			val sourceGroup = TLRPC.TLGroupCallParticipantVideoSourceGroup()
			sourceGroup.semantics = "SIM"

			channels?.forEach {
				sourceGroup.sources.add(it.channel)
			}

			participant.video?.sourceGroups?.add(sourceGroup)
			participant.video?.endpoint = "unified"
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

			val noVideoParticipant = TLRPC.TLGroupCallParticipant()
			noVideoParticipant.peer = TLRPC.TLPeerChannel().also { it.channelId = chatId }
			noVideoParticipant.muted = true

			noVideoParticipant.video = TLRPC.TLGroupCallParticipantVideo().also {
				it.paused = true
				it.endpoint = ""
			}

			videoNotAvailableParticipant = VideoParticipant(noVideoParticipant, presentation = false, hasSame = false)
		}

		fun addSelfDummyParticipant(notify: Boolean) {
			val selfId = selfId

			if (participants.indexOfKey(selfId) >= 0) {
				return
			}

			val selfDummyParticipant = TLRPC.TLGroupCallParticipant()
			selfDummyParticipant.peer = selfPeer
			selfDummyParticipant.muted = true
			selfDummyParticipant.isSelf = true
			selfDummyParticipant.videoJoined = (call as? TLRPC.TLGroupCall)?.canStartVideo == true

			val chat = currentAccount?.messagesController?.getChat(chatId)

			selfDummyParticipant.canSelfUnmute = (call as? TLRPC.TLGroupCall)?.joinMuted != true || canManageCalls(chat)
			selfDummyParticipant.date = currentAccount!!.connectionsManager.currentTime

			if (canManageCalls(chat) || !isChannel(chat) || chat.megagroup || selfDummyParticipant.canSelfUnmute) {
				selfDummyParticipant.activeDate = currentAccount?.connectionsManager?.currentTime ?: 0
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
			val call = (call as? TLRPC.TLGroupCall) ?: return false
			return call.participantsCount > 0 || call.rtmpStream || isScheduled
		}

		val isScheduled: Boolean
			get() = ((call as? TLRPC.TLGroupCall)?.flags ?: 0) and 128 != 0

		private val selfId: Long
			get() = if (selfPeer != null) {
				MessageObject.getPeerId(selfPeer)
			}
			else {
				currentAccount?.userConfig?.getClientUserId() ?: 0L
			}

		private fun onParticipantsLoad(loadedParticipants: List<TLRPC.TLGroupCallParticipant>, fromBegin: Boolean, reqOffset: String?, nextOffset: String?, version: Int, participantCount: Int) {
			var old: LongSparseArray<TLRPC.TLGroupCallParticipant>? = null
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
				(call as? TLRPC.TLGroupCall)?.let {
					it.version = version
					it.participantsCount = participantCount

					if (BuildConfig.DEBUG) {
						FileLog.d("new participants count ${it.participantsCount}")
					}
				}
			}

			val time = SystemClock.elapsedRealtime()

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time)

			var hasSelf = false
			var a = 0
			val n = loadedParticipants.size

			while (a <= n) {
				var participant: TLRPC.TLGroupCallParticipant

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

					if (participant.isSelf) {
						hasSelf = true
					}
				}

				var oldParticipant = participants[MessageObject.getPeerId(participant.peer)]

				if (oldParticipant != null) {
					sortedParticipants.remove(oldParticipant)

					processAllSources(oldParticipant, false)

					if (oldParticipant.isSelf) {
						participant.lastTypingDate = oldParticipant.activeDate
					}
					else {
						participant.lastTypingDate = max(participant.activeDate, oldParticipant.activeDate)
					}

					if (time != participant.lastVisibleDate) {
						participant.activeDate = participant.lastTypingDate
					}
				}
				else if (old != null) {
					oldParticipant = old[MessageObject.getPeerId(participant.peer)]

					if (oldParticipant != null) {
						if (oldParticipant.isSelf) {
							participant.lastTypingDate = oldParticipant.activeDate
						}
						else {
							participant.lastTypingDate = max(participant.activeDate, oldParticipant.activeDate)
						}

						if (time != participant.lastVisibleDate) {
							participant.activeDate = participant.lastTypingDate
						}
						else {
							participant.activeDate = oldParticipant.activeDate
						}
					}
				}

				participants.put(MessageObject.getPeerId(participant.peer), participant)
				sortedParticipants.add(participant)
				processAllSources(participant, true)

				a++
			}

			if (((call as? TLRPC.TLGroupCall)?.participantsCount ?: 0) < participants.size()) {
				(call as? TLRPC.TLGroupCall)?.participantsCount = participants.size()
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

			val req = TLRPC.TLPhoneGetGroupParticipants()
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
						val groupParticipants = response as TLRPC.TLPhoneGroupParticipants

						currentAccount?.messagesController?.putUsers(groupParticipants.users, false)
						currentAccount?.messagesController?.putChats(groupParticipants.chats, false)

						onParticipantsLoad(groupParticipants.participants, fromBegin, req.offset, groupParticipants.nextOffset, groupParticipants.version, groupParticipants.count)
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
			val req = TLRPC.TLPhoneEditGroupCallTitle()
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
							participant.activeDate = date
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

			val req = TLRPC.TLPhoneGetGroupParticipants()
			req.call = inputGroupCall

			for (uid in participantsToLoad) {
				if (isIds) {
					if (uid > 0) {
						val peerUser = TLRPC.TLInputPeerUser()
						peerUser.userId = uid

						req.ids.add(peerUser)
					}
					else {
						val chat = currentAccount?.messagesController?.getChat(-uid)
						var inputPeer: InputPeer

						if (chat == null || isChannel(chat)) {
							inputPeer = TLRPC.TLInputPeerChannel()
							inputPeer.channelId = -uid
						}
						else {
							inputPeer = TLRPC.TLInputPeerChat()
							inputPeer.chatId = -uid
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
						val groupParticipants = response as TLRPC.TLPhoneGroupParticipants

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

						(call as? TLRPC.TLGroupCall)?.let {
							if (it.participantsCount < participants.size()) {
								it.participantsCount = participants.size()
							}
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

		private fun processAllSources(participant: TLRPC.TLGroupCallParticipant, add: Boolean) {
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
					if (data.flags and 2 != 0 && data.audioSource != 0) {
						if (add) {
							participantsBySources.put(data.audioSource, participant)
						}
						else {
							participantsBySources.remove(data.audioSource)
						}
					}

					val sourcesArray = if (c == 0) participantsByVideoSources else participantsByPresentationSources
					var a = 0
					val n = data.sourceGroups.size

					while (a < n) {
						val sourceGroup = data.sourceGroups[a]
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
								participant.activeDate = currentTime
							}

							participant.lastTypingDate = currentTime

							updated = true
						}

						participant.lastSpeakTime = uptime
						participant.amplitude = levels[a]

						if (currentSpeakingPeers.get(peerId, null) == null) {
							if (peerId > 0) {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(peerId)
								FileLog.d("GroupCall: add to current speaking " + peerId + " " + user?.firstName)
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
							if (currentSpeakingPeers.get(peerId, null) != null) {
								currentSpeakingPeers.remove(peerId)

								if (peerId > 0) {
									val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(peerId)
									FileLog.d("GroupCall: remove from speaking " + peerId + " " + user?.firstName)
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

		private fun isValidUpdate(update: TLRPC.TLUpdateGroupCallParticipants): Int {
			val call = call as? TLRPC.TLGroupCall ?: return 2

			return if (call.version + 1 == update.version || call.version == update.version) {
				0
			}
			else if (call.version < update.version) {
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
					is TLRPC.TLInputPeerUser -> {
						selfPeer = TLRPC.TLPeerUser().also { it.userId = peer.userId }
					}

					is TLRPC.TLInputPeerChat -> {
						selfPeer = TLRPC.TLPeerChat().also { it.chatId = peer.chatId }
					}

					else -> {
						selfPeer = TLRPC.TLPeerChannel().also { it.channelId = peer.channelId }
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
			val req = TLRPC.TLPhoneGetGroupCall()
			req.call = inputGroupCall
			req.limit = 100

			currentAccount?.connectionsManager?.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (response is TLRPC.TLPhoneGroupCall) {
						val call = response.call.also { this.call = it } as? TLRPC.TLGroupCall

						currentAccount?.messagesController?.putUsers(response.users, false)
						currentAccount?.messagesController?.putChats(response.chats, false)

						onParticipantsLoad(response.participants, true, "", response.participantsNextOffset, call?.version ?: 0, call?.participantsCount ?: 0)
					}
				}
			}
		}

		private fun loadGroupCall() {
			if (loadingGroupCall || SystemClock.elapsedRealtime() - lastGroupCallReloadTime < 30000) {
				return
			}

			loadingGroupCall = true

			val req = TLRPC.TLPhoneGetGroupParticipants()
			req.call = inputGroupCall
			req.offset = ""
			req.limit = 1

			currentAccount?.connectionsManager?.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					lastGroupCallReloadTime = SystemClock.elapsedRealtime()
					loadingGroupCall = false

					if (response != null) {
						val res = response as TLRPC.TLPhoneGroupParticipants

						currentAccount?.messagesController?.putUsers(res.users, false)
						currentAccount?.messagesController?.putChats(res.chats, false)

						val call = call as? TLRPC.TLGroupCall

						if (call?.participantsCount != res.count) {
							call?.participantsCount = res.count

							if (BuildConfig.DEBUG) {
								FileLog.d("new participants reload count ${call?.participantsCount}")
							}

							currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
						}
					}
				}
			}
		}

		fun processParticipantsUpdate(update: TLRPC.TLUpdateGroupCallParticipants, fromQueue: Boolean) {
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

				if (versioned && ((call as? TLRPC.TLGroupCall)?.version ?: 0) + 1 < update.version) {
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

				if (versioned && update.version < ((call as? TLRPC.TLGroupCall)?.version ?: 0)) {
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
					if (oldParticipant == null && update.version == ((call as? TLRPC.TLGroupCall)?.version ?: 0)) {
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

						if (currentSpeakingPeers.get(pid, null) != null) {
							if (pid > 0) {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(pid)
								FileLog.d("GroupCall: left, remove from speaking " + pid + " " + user?.firstName)
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

					(call as? TLRPC.TLGroupCall)?.let {
						it.participantsCount--

						if (it.participantsCount < 0) {
							it.participantsCount = 0
						}
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

						if (participant.muted && currentSpeakingPeers.get(pid, null) != null) {
							currentSpeakingPeers.remove(pid)

							if (pid > 0) {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getUser(pid)
								FileLog.d("GroupCall: muted remove from speaking " + pid + " " + user?.firstName)
							}
							else {
								val user = MessagesController.getInstance(currentAccount!!.currentAccount).getChat(-pid)
								FileLog.d("GroupCall: muted remove from speaking " + pid + " " + user?.title)
							}

							speakingUpdated = true
						}

						if (!participant.min) {
							oldParticipant.volume = participant.volume
							oldParticipant.mutedByYou = participant.mutedByYou
						}
						else {
							if (participant.flags and 128 != 0 && oldParticipant.flags and 128 == 0) {
								participant.flags = participant.flags and 128.inv()
							}

							if (participant.volumeByAdmin && oldParticipant.volumeByAdmin) {
								oldParticipant.volume = participant.volume
							}
						}

						oldParticipant.flags = participant.flags
						oldParticipant.canSelfUnmute = participant.canSelfUnmute
						oldParticipant.videoJoined = participant.videoJoined

						if (oldParticipant.raiseHandRating == 0L && participant.raiseHandRating != 0L) {
							oldParticipant.lastRaiseHandDate = SystemClock.elapsedRealtime()
						}

						oldParticipant.raiseHandRating = participant.raiseHandRating
						oldParticipant.date = participant.date
						oldParticipant.lastTypingDate = max(oldParticipant.activeDate, participant.activeDate)

						if (time != oldParticipant.lastVisibleDate) {
							oldParticipant.activeDate = oldParticipant.lastTypingDate
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
							oldParticipant.video?.paused = participant.video?.paused == true
						}
					}
					else {
						if (participant.justJoined) {
							if (pid != selfId) {
								justJoinedId = pid
							}

							(call as? TLRPC.TLGroupCall)?.let { call ->
								call.participantsCount++

								if (update.version == call.version) {
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
						}

						if (participant.raiseHandRating != 0L) {
							participant.lastRaiseHandDate = SystemClock.elapsedRealtime()
						}

						if (pid == selfId || sortedParticipants.size < 20 || participant.date <= lastParticipantDate || participant.activeDate != 0 || participant.canSelfUnmute || !participant.muted || !participant.min || membersLoadEndReached) {
							sortedParticipants.add(participant)
						}

						participants.put(pid, participant)

						processAllSources(participant, true)
					}

					if (pid == selfId && participant.activeDate == 0 && (participant.canSelfUnmute || !participant.muted)) {
						participant.activeDate = currentAccount!!.connectionsManager.currentTime
					}

					changedOrAdded = true
					updated = true
				}

				if (pid == selfId) {
					selfUpdated = true
				}

				a++
			}


			if (update.version > ((call as? TLRPC.TLGroupCall)?.version ?: 0)) {
				(call as? TLRPC.TLGroupCall)?.version = update.version

				if (!fromQueue) {
					processUpdatesQueue()
				}
			}

			if (((call as? TLRPC.TLGroupCall)?.participantsCount ?: 0) < participants.size()) {
				(call as? TLRPC.TLGroupCall)?.participantsCount = participants.size()
			}

			if (BuildConfig.DEBUG) {
				FileLog.d("new participants count after update " + (call as? TLRPC.TLGroupCall)?.participantsCount)
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

		private fun isSameVideo(oldVideo: TLRPC.TLGroupCallParticipantVideo?, newVideo: TLRPC.TLGroupCallParticipantVideo?): Boolean {
			if (oldVideo == null && newVideo != null || oldVideo != null && newVideo == null) {
				return false
			}

			if (oldVideo == null || newVideo == null) {
				return true
			}

			if (!TextUtils.equals(oldVideo.endpoint, newVideo.endpoint)) {
				return false
			}

			if (oldVideo.sourceGroups.size != newVideo.sourceGroups.size) {
				return false
			}

			var a = 0
			val n = oldVideo.sourceGroups.size

			while (a < n) {
				val oldGroup = oldVideo.sourceGroups[a]
				val newGroup = newVideo.sourceGroups[a]

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

		fun processGroupCallUpdate(update: TLRPC.TLUpdateGroupCall) {
			if (((call as? TLRPC.TLGroupCall)?.version ?: 0) < ((update.call as? TLRPC.TLGroupCall)?.version ?: 0)) {
				nextLoadOffset = null
				loadMembers(true)
			}

			call = update.call
			recording = (call as? TLRPC.TLGroupCall)?.recordStartDate != 0

			currentAccount?.notificationCenter?.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call!!.id, false)
		}

		val inputGroupCall: TLRPC.TLInputGroupCall
			get() {
				val inputGroupCall = TLRPC.TLInputGroupCall()
				inputGroupCall.id = call?.id ?: 0L
				inputGroupCall.accessHash = call?.accessHash ?: 0L
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

				if (!participant.isSelf && (cameraActive || screenActive)) {
					activeVideos++
				}

				if (cameraActive || screenActive) {
					hasAnyVideo = true

					if (canStreamVideo) {
						if (participant.videoIndex == 0) {
							if (participant.isSelf) {
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
				else if ((participant.isSelf || !canStreamVideo || participant.video == null) && participant.presentation == null) {
					participant.videoIndex = 0
				}

				i++
			}

			val comparator = Comparator { o1: TLRPC.TLGroupCallParticipant, o2: TLRPC.TLGroupCallParticipant ->
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

				if (o1.activeDate != 0 && o2.activeDate != 0) {
					return@Comparator o2.activeDate.compareTo(o1.activeDate)
				}
				else if (o1.activeDate != 0) {
					return@Comparator -1
				}
				else if (o2.activeDate != 0) {
					return@Comparator 1
				}

				if (MessageObject.getPeerId(o1.peer) == selfId) {
					return@Comparator -1
				}
				else if (MessageObject.getPeerId(o2.peer) == selfId) {
					return@Comparator 1
				}

				if (isAdmin) {
					if (o1.raiseHandRating != 0L && o2.raiseHandRating != 0L) {
						return@Comparator o2.raiseHandRating.compareTo(o1.raiseHandRating)
					}
					else if (o1.raiseHandRating != 0L) {
						return@Comparator -1
					}
					else if (o2.raiseHandRating != 0L) {
						return@Comparator 1
					}
				}

				if ((call as? TLRPC.TLGroupCall)?.joinDateAsc == true) {
					return@Comparator o1.date.compareTo(o2.date)
				}
				else {
					return@Comparator o2.date.compareTo(o1.date)
				}
			}

			sortedParticipants.sortWith(comparator)

			val lastParticipant = if (sortedParticipants.isEmpty()) null else sortedParticipants[sortedParticipants.size - 1]

			if (videoIsActive(lastParticipant, false, this) || videoIsActive(lastParticipant, true, this)) {
				if (((call as? TLRPC.TLGroupCall)?.unmutedVideoCount ?: 0) > activeVideos) {
					activeVideos = (call as? TLRPC.TLGroupCall)?.unmutedVideoCount ?: 0

					val voIPService = sharedInstance

					if (voIPService != null && voIPService.groupCall === this) {
						if (voIPService.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || voIPService.getVideoState(true) == Instance.VIDEO_STATE_ACTIVE) {
							activeVideos--
						}
					}
				}
			}

			if (sortedParticipants.size > MAX_PARTICIPANTS_COUNT && (!canManageCalls(chat) || lastParticipant!!.raiseHandRating == 0L)) {
				var a = MAX_PARTICIPANTS_COUNT
				@Suppress("NAME_SHADOWING") val n = sortedParticipants.size

				while (a < n) {
					val p = sortedParticipants[MAX_PARTICIPANTS_COUNT]

					if (p.raiseHandRating != 0L) {
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
					if (!participant.isSelf && videoIsActive(participant, true, this) && videoIsActive(participant, false, this)) {
						var videoParticipant = videoParticipantsCache[participant.videoEndpoint]

						if (videoParticipant == null) {
							videoParticipant = VideoParticipant(participant, presentation = false, hasSame = true)
							videoParticipantsCache[participant.videoEndpoint ?: ""] = videoParticipant
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
						if (participant.isSelf) {
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
								videoParticipantsCache[(if (presentation) participant.presentationEndpoint else participant.videoEndpoint) ?: ""] = videoParticipant
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
				activeVideos < ((call as? TLRPC.TLGroupCall)?.unmutedVideoLimit ?: 0)
			}
		}

		fun saveActiveDates() {
			for (p in sortedParticipants) {
				p.lastActiveDate = p.activeDate.toLong()
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
				val diff = currentTime - participant.activeDate

				if (diff < 5) {
					speakingMembersCount++
					minDiff = min(diff, minDiff)
				}

				if (max(participant.date, participant.activeDate) <= currentTime - 5) {
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

			val req = TLRPC.TLPhoneToggleGroupCallRecord()
			req.call = inputGroupCall
			req.start = recording

			if (title != null) {
				req.title = title
				req.flags = req.flags or 2
			}

			if (type == RECORD_TYPE_VIDEO_PORTRAIT || type == RECORD_TYPE_VIDEO_LANDSCAPE) {
				req.flags = req.flags or 4
				req.video = true
				req.videoPortrait = type == RECORD_TYPE_VIDEO_PORTRAIT
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
			fun videoIsActive(participant: TLRPC.TLGroupCallParticipant?, presentation: Boolean, call: Call): Boolean {
				if (participant == null) {
					return false
				}

				val service = sharedInstance ?: return false

				return if (participant.isSelf) {
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

	class VideoParticipant(@JvmField var participant: TLRPC.TLGroupCallParticipant, @JvmField var presentation: Boolean, var hasSame: Boolean) {
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
