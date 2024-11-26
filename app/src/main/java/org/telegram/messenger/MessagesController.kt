/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Telegram, 2013-2024.
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.messenger

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Base64
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.util.SparseIntArray
import androidx.collection.LongSparseArray
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.util.isNotEmpty
import androidx.core.util.valueIterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.telegram.SQLite.SQLiteException
import org.telegram.messenger.ImageLoader.MessageThumb
import org.telegram.messenger.MessagesStorage.BooleanCallback
import org.telegram.messenger.MessagesStorage.LongCallback
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.PushListenerController.PushType
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.support.LongSparseIntArray
import org.telegram.messenger.support.LongSparseLongArray
import org.telegram.messenger.voip.VoIPPreNotificationService
import org.telegram.messenger.voip.VoIPService
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.RequestDelegate
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC.*
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_channels_channelParticipants
import org.telegram.tgnet.tlrpc.TL_channels_editBanned
import org.telegram.tgnet.tlrpc.TL_chatBannedRights
import org.telegram.tgnet.tlrpc.TL_config
import org.telegram.tgnet.tlrpc.TL_error
import org.telegram.tgnet.tlrpc.TL_folders_editPeerFolders
import org.telegram.tgnet.tlrpc.TL_inputPhotoEmpty
import org.telegram.tgnet.tlrpc.TL_message
import org.telegram.tgnet.tlrpc.TL_messageEntityMentionName
import org.telegram.tgnet.tlrpc.TL_messages_channelMessages
import org.telegram.tgnet.tlrpc.TL_messages_chatFull
import org.telegram.tgnet.tlrpc.TL_messages_dialogs
import org.telegram.tgnet.tlrpc.TL_messages_getDialogs
import org.telegram.tgnet.tlrpc.TL_messages_messages
import org.telegram.tgnet.tlrpc.TL_messages_messagesNotModified
import org.telegram.tgnet.tlrpc.TL_messages_peerDialogs
import org.telegram.tgnet.tlrpc.TL_photo
import org.telegram.tgnet.tlrpc.TL_photos_deletePhotos
import org.telegram.tgnet.tlrpc.TL_photos_photo
import org.telegram.tgnet.tlrpc.TL_photos_photos
import org.telegram.tgnet.tlrpc.TL_photos_updateProfilePhoto
import org.telegram.tgnet.tlrpc.TL_reactionEmoji
import org.telegram.tgnet.tlrpc.TL_updateMessageReactions
import org.telegram.tgnet.tlrpc.TL_userProfilePhotoEmpty
import org.telegram.tgnet.tlrpc.TL_users_userFull
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.UserFull
import org.telegram.tgnet.tlrpc.Vector
import org.telegram.tgnet.tlrpc.contacts_Blocked
import org.telegram.tgnet.tlrpc.messages_Dialogs
import org.telegram.tgnet.tlrpc.messages_Messages
import org.telegram.tgnet.tlrpc.photos_Photos
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.Theme.OverrideWallpaperInfo
import org.telegram.ui.ActionBar.Theme.ThemeAccent
import org.telegram.ui.ActionBar.Theme.ThemeInfo
import org.telegram.ui.ChatActivity
import org.telegram.ui.ChatReactionsEditActivity
import org.telegram.ui.ChatRightsEditActivity.Companion.emptyAdminRights
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.JoinCallAlert
import org.telegram.ui.Components.MotionBackgroundDrawable
import org.telegram.ui.Components.SwipeGestureSettingsView
import org.telegram.ui.Components.TranscribeButton
import org.telegram.ui.DialogsActivity
import org.telegram.ui.EditWidgetActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.LaunchActivity.Companion.clearFragments
import org.telegram.ui.PremiumPreviewFragment
import org.telegram.ui.ProfileActivity
import org.telegram.ui.feed.FeedFragment
import java.io.File
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MessagesController(num: Int) : BaseController(num), NotificationCenterDelegate {
	var hintDialogs = ArrayList<RecentMeUrl>()
	var dialogsForward = ArrayList<Dialog>()
	var dialogsCanAddUsers = ArrayList<Dialog>()
	var dialogsMyChannels = ArrayList<Dialog>()
	var dialogsMyGroups = ArrayList<Dialog>()
	var dialogsChannelsOnly = ArrayList<Dialog>()
	var dialogsUsersOnly = ArrayList<Dialog>()
	var dialogsForBlock = ArrayList<Dialog>()
	var dialogsGroupsOnly = ArrayList<Dialog>()
	var selectedDialogFilter = arrayOfNulls<DialogFilter>(2)
	var unreadUnmutedDialogs = 0
	var deletedHistory = LongSparseIntArray()
	var dialogMessagesByIds = SparseArray<MessageObject>()
	var printingUsers = ConcurrentHashMap<Long, ConcurrentHashMap<Int, ArrayList<PrintingUser>>>(20, 1.0f, 2)
	var printingStrings = LongSparseArray<SparseArray<CharSequence>>()
	var printingStringsTypes = LongSparseArray<SparseIntArray>()
	var sendingTypings: Array<LongSparseArray<SparseBooleanArray>?> = arrayOfNulls(12)
	var loadingBlockedPeers = false
	var blockedPeers = LongSparseIntArray()
	var totalBlockedCount = -1
	var blockedEndReached = false
	var dialogsLoaded = false
	var gettingDifference = false
	var updatingState = false
	var firstGettingTask = false
	var registeringForPush = false
	var faqSearchArray = ArrayList<FaqSearchResult>()
	var faqWebPage: WebPage? = null
	var promoDialogType: Int
	var promoPsaMessage: String?
	var promoPsaType: String?
	var secretWebpagePreview: Int
	var suggestContacts = true
	var maxGroupCount: Int
	var maxBroadcastCount = 100
	var maxMegagroupCount: Int
	var minGroupConvertSize = 200
	var callReceiveTimeout: Int
	var callRingTimeout: Int
	var callConnectTimeout: Int
	var callPacketTimeout: Int
	var maxPinnedDialogsCount: Int
	var maxFolderPinnedDialogsCount: Int
	var updateCheckDelay: Int
	var chatReadMarkExpirePeriod: Int
	var maxMessageLength: Int
	var blockedCountry: Boolean
	var animatedEmojisZoom: Float
	var filtersEnabled: Boolean
	var showFiltersTooltip: Boolean
	var dcDomainName: String?
	var suggestedLangCode: String?
	var qrLoginCamera: Boolean
	var saveGifsWithStickers: Boolean
	var exportUri: MutableSet<String>?
	var exportGroupUri: MutableSet<String>?
	var exportPrivateUri: MutableSet<String>?
	var autoarchiveAvailable: Boolean
	var suggestStickersApiOnly: Boolean
	var diceEmojies: Set<String>? = null
	var diceSuccess = HashMap<String, DiceFrameSuccess>()
	var emojiSounds = HashMap<String, EmojiSound>()
	var emojiInteractions = HashMap<Long, ArrayList<TL_sendMessageEmojiInteraction>>()
	var remoteConfigLoaded: Boolean
	var authDomains: Set<String>?
	var reactionsUserMaxDefault: Int
	var reactionsUserMaxPremium: Int
	var reactionsInChatMax: Int
	var directPaymentsCurrency = mutableListOf<String>()
	private val encryptedChats = ConcurrentHashMap<Int, EncryptedChat>(10, 1.0f, 2)
	private val objectsByUsernames = ConcurrentHashMap<String, TLObject>(100, 1.0f, 2)
	private val activeVoiceChatsMap = HashMap<Long, Chat>()
	private val joiningToChannels = ArrayList<Long>()
	private val exportedChats = LongSparseArray<TL_chatInviteExported>()
	private var dialogsLoadedTillDate = Int.MAX_VALUE
	private var lastPrintingStringCount = 0
	private var dialogsInTransaction = false
	private val loadingPeerSettings = LongSparseArray<Boolean>()
	private val createdDialogIds = mutableListOf<Long>()
	private val createdScheduledDialogIds = mutableListOf<Long>()
	private val createdDialogMainThreadIds = mutableListOf<Long>()
	private val visibleDialogMainThreadIds = mutableListOf<Long>()
	private val visibleScheduledDialogMainThreadIds = mutableListOf<Long>()
	private val shortPollChannels = LongSparseIntArray()
	private val needShortPollChannels = LongSparseArray<MutableList<Int>>()
	private val shortPollOnlines = LongSparseIntArray()
	private val needShortPollOnlines = LongSparseArray<MutableList<Int>>()
	private val deletingDialogs = LongSparseArray<Dialog>()
	private val clearingHistoryDialogs = LongSparseArray<Dialog>()
	private val channelViewsToSend = LongSparseArray<MutableList<Int>>()
	private val pollsToCheck = LongSparseArray<SparseArray<MessageObject>>()
	private var pollsToCheckSize = 0
	private var lastViewsCheckTime: Long = 0
	private var loadingSuggestedFilters = false
	private var loadingRemoteFilters = false
	private val updatesQueueChannels = LongSparseArray<MutableList<Updates>>()
	private val updatesStartWaitTimeChannels = LongSparseLongArray()
	private val channelsPts = LongSparseIntArray()
	private val gettingDifferenceChannels = LongSparseArray<Boolean>()
	private val gettingChatInviters = LongSparseArray<Boolean>()
	private val gettingUnknownChannels = LongSparseArray<Boolean>()
	private val gettingUnknownDialogs = LongSparseArray<Boolean>()
	private val checkingLastMessagesDialogs = LongSparseArray<Boolean>()
	private val updatesQueueSeq = mutableListOf<Updates>()
	private val updatesQueuePts = mutableListOf<Updates>()
	private val updatesQueueQts = mutableListOf<Updates>()
	private var updatesStartWaitTimeSeq: Long = 0
	private var updatesStartWaitTimePts: Long = 0
	private var updatesStartWaitTimeQts: Long = 0
	private val fullUsers = LongSparseArray<UserFull>()
	private val fullChats = LongSparseArray<ChatFull>()
	private val groupCalls = LongSparseArray<ChatObject.Call>()
	private val groupCallsByChatId = LongSparseArray<ChatObject.Call>()
	private val loadingFullUsers = mutableSetOf<Long>()
	private val loadedFullUsers = mutableSetOf<Long>()
	private val loadingFullChats = mutableSetOf<Long>()
	private val loadingGroupCalls = mutableSetOf<Long>()
	private val loadingFullParticipants = mutableSetOf<Long>()
	private val loadedFullParticipants = mutableSetOf<Long>()
	private val loadedFullChats = mutableSetOf<Long>()
	private val channelAdmins = LongSparseArray<LongSparseArray<ChannelParticipant>?>()
	private val loadingChannelAdmins = LongSparseIntArray()
	private val migratedChats = SparseIntArray()
	private val sponsoredMessages = LongSparseArray<SponsoredMessagesInfo>()
	private val sendAsPeers = LongSparseArray<SendAsPeersInfo>()
	private val reloadingWebpages = mutableMapOf<String, MutableList<MessageObject>>()
	private val reloadingWebpagesPending = LongSparseArray<MutableList<MessageObject>>()
	private val reloadingScheduledWebpages = mutableMapOf<String, MutableList<MessageObject>>()
	private val reloadingScheduledWebpagesPending = LongSparseArray<MutableList<MessageObject>>()
	private val lastScheduledServerQueryTime = LongSparseArray<Long>()
	private val lastServerQueryTime = LongSparseArray<Long>()
	private val reloadingMessages = LongSparseArray<ArrayList<Int>>()
	private val readTasks = mutableListOf<ReadTask>()
	private val readTasksMap = LongSparseArray<ReadTask>()
	private val repliesReadTasks = mutableListOf<ReadTask>()
	private val threadsReadTasksMap = mutableMapOf<String, ReadTask>()
	private var gettingNewDeleteTask = false
	private var currentDeletingTaskTime = 0
	private var currentDeletingTaskMids: LongSparseArray<List<Int>>? = null
	private var currentDeletingTaskMediaMids: LongSparseArray<List<Int>>? = null
	private var currentDeleteTaskRunnable: Runnable? = null
	private val nextDialogsCacheOffset = SparseIntArray()
	private val loadingDialogs = SparseBooleanArray()
	private val dialogsEndReached = SparseBooleanArray()
	private val serverDialogsEndReached = SparseBooleanArray()
	private var loadingUnreadDialogs = false
	private var migratingDialogs = false
	private var getDifferenceFirstSync = true
	private var lastPushRegisterSendTime: Long = 0
	private var resettingDialogs = false
	private var resetDialogsPinned: TL_messages_peerDialogs? = null
	private var resetDialogsAll: messages_Dialogs? = null
	private val loadingPinnedDialogs = SparseIntArray()
	private var loadingNotificationSettings = 0
	private var loadingNotificationSignUpSettings = false
	private var nextPromoInfoCheckTime: Int
	private var checkingPromoInfo = false
	private var checkingPromoInfoRequestId = 0
	private var lastCheckPromoId = 0
	private var promoDialog: Dialog? = null
	private var isLeftPromoChannel = false
	private var promoDialogId: Long
	private var proxyDialogAddress: String?
	private var checkingTosUpdate = false
	private var nextTosCheckTime: Int
	private val themeCheckRunnable = Runnable { Theme.checkAutoNightThemeConditions() }
	private val passwordCheckRunnable = Runnable { userConfig.checkSavedPassword() }
	private var lastStatusUpdateTime: Long = 0
	private var statusRequest = 0
	private var statusSettingState = 0
	private var offlineSent = false
	private var uploadingAvatar: String? = null
	private val uploadingThemes = HashMap<String, Any>()
	private var uploadingWallpaper: String? = null
	private var uploadingWallpaperInfo: OverrideWallpaperInfo? = null
	private var loadingAppConfig = false
	private var installReferer: String?
	private var recentEmojiStatusUpdateRunnableTimeout: Long = 0
	private var recentEmojiStatusUpdateRunnableTime: Long = 0
	private var recentEmojiStatusUpdateRunnable: Runnable? = null
	private val emojiStatusUntilValues = LongSparseArray<Int>()
	private var sortingDialogFilter: DialogFilter? = null
	private val notificationsPreferences: SharedPreferences
	private val mainPreferences: SharedPreferences
	private val emojiPreferences: SharedPreferences
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	@JvmField
	var dialogsByFolder = SparseArray<ArrayList<Dialog>>()

	@JvmField
	var dialogsServerOnly = ArrayList<Dialog>()

	@JvmField
	var dialogs_read_inbox_max = ConcurrentHashMap<Long, Int>(100, 1.0f, 2)

	var dialogs_read_outbox_max = ConcurrentHashMap<Long, Int>(100, 1.0f, 2)

	@JvmField
	var dialogs_dict = LongSparseArray<Dialog>()

	@JvmField
	var dialogMessage = LongSparseArray<MessageObject>()

	@JvmField
	var dialogMessagesByRandomIds = LongSparseArray<MessageObject>()

	@JvmField
	var onlinePrivacy = ConcurrentHashMap<Long, Int>(20, 1.0f, 2)

	@JvmField
	var premiumFeaturesTypesToPosition = SparseIntArray()

	@JvmField
	var dialogFilters = ArrayList<DialogFilter>()

	@JvmField
	var dialogFiltersById = SparseArray<DialogFilter>()

	var dialogFiltersLoaded = false

	@JvmField
	var suggestedFilters = ArrayList<TL_dialogFilterSuggested>()

	@JvmField
	var enableJoined: Boolean

	@JvmField
	var linkPrefix: String = ""

	@JvmField
	var maxEditTime: Int

	@JvmField
	var ratingDecay: Int

	@JvmField
	var revokeTimeLimit: Int = 0

	@JvmField
	var revokeTimePmLimit: Int = 0

	@JvmField
	var canRevokePmInbox: Boolean = false

	@JvmField
	var maxRecentStickersCount: Int

	@JvmField
	var maxFaveStickersCount: Int

	@JvmField
	var maxRecentGifsCount: Int

	@JvmField
	var mapProvider: Int

	@JvmField
	var availableMapProviders: Int

	@JvmField
	var chatReadMarkSizeThreshold: Int

	@JvmField
	var mapKey: String?

	@JvmField
	var maxCaptionLength: Int

	@JvmField
	var roundVideoSize: Int

	@JvmField
	var roundVideoBitrate: Int

	@JvmField
	var roundAudioBitrate: Int

	@JvmField
	var preloadFeaturedStickers: Boolean

	@JvmField
	var youtubePipType: String?

	@JvmField
	var keepAliveService: Boolean

	@JvmField
	var backgroundConnection: Boolean

	@JvmField
	var getfileExperimentalParams: Boolean

	@JvmField
	var venueSearchBot: String?

	@JvmField
	var gifSearchBot: String?

	@JvmField
	var imageSearchBot: String?

	@JvmField
	var webFileDatacenterId: Int

	@JvmField
	var pendingSuggestions: MutableSet<String>?

	@JvmField
	var groupCallVideoMaxParticipants: Int

	@JvmField
	var gifSearchEmojies = ArrayList<String>()

	@JvmField
	var autologinDomains: Set<String>?

	@JvmField
	var autologinToken: String?

	@JvmField
	var ringtoneDurationMax: Int

	@JvmField
	var ringtoneSizeMax: Int

	@JvmField
	var channelsLimitDefault: Int

	@JvmField
	var channelsLimitPremium: Int

	@JvmField
	var savedGifsLimitDefault: Int

	@JvmField
	var savedGifsLimitPremium: Int

	@JvmField
	var stickersFavedLimitDefault: Int

	@JvmField
	var stickersFavedLimitPremium: Int

	@JvmField
	var dialogFiltersLimitDefault: Int

	@JvmField
	var dialogFiltersLimitPremium: Int

	@JvmField
	var dialogFiltersChatsLimitDefault: Int

	@JvmField
	var dialogFiltersChatsLimitPremium: Int

	@JvmField
	var dialogFiltersPinnedLimitDefault: Int

	@JvmField
	var dialogFiltersPinnedLimitPremium: Int

	@JvmField
	var publicLinksLimitDefault: Int

	@JvmField
	var publicLinksLimitPremium: Int

	@JvmField
	var captionLengthLimitDefault: Int

	@JvmField
	var captionLengthLimitPremium: Int

	@JvmField
	var aboutLengthLimitDefault: Int

	@JvmField
	var aboutLengthLimitPremium: Int

	@JvmField
	var uploadMaxFileParts: Int

	@JvmField
	var uploadMaxFilePartsPremium: Int

	@JvmField
	var premiumBotUsername: String?

	@JvmField
	var premiumInvoiceSlug: String?

	@Volatile
	var ignoreSetOnline = false

	@JvmField
	var premiumLocked: Boolean

	@JvmField
	var newMessageCallback: NewMessageCallback? = null

	@JvmField
	var allDialogs = ArrayList<Dialog>()

	@JvmField
	val chats = ConcurrentHashMap<Long, Chat>(100, 1.0f, 2)

	@JvmField
	val users = ConcurrentHashMap<Long, User>(100, 1.0f, 2)

	private val dialogDateComparator = Comparator { dialog1: Dialog?, dialog2: Dialog? ->
		val sortingDialogFilter = sortingDialogFilter ?: return@Comparator 0

		val pinnedNum1 = sortingDialogFilter.pinnedDialogs[dialog1!!.id, Int.MIN_VALUE]
		val pinnedNum2 = sortingDialogFilter.pinnedDialogs[dialog2!!.id, Int.MIN_VALUE]

		if (dialog1 is TL_dialogFolder && dialog2 !is TL_dialogFolder) {
			return@Comparator -1
		}
		else if (dialog1 !is TL_dialogFolder && dialog2 is TL_dialogFolder) {
			return@Comparator 1
		}
		else if (pinnedNum1 == Int.MIN_VALUE && pinnedNum2 != Int.MIN_VALUE) {
			return@Comparator 1
		}
		else if (pinnedNum1 != Int.MIN_VALUE && pinnedNum2 == Int.MIN_VALUE) {
			return@Comparator -1
		}
		else if (pinnedNum1 != Int.MIN_VALUE) {
			return@Comparator pinnedNum1.compareTo(pinnedNum2)
		}

		val mediaDataController = mediaDataController
		val date1 = DialogObject.getLastMessageOrDraftDate(dialog1, mediaDataController.getDraft(dialog1.id, 0))
		val date2 = DialogObject.getLastMessageOrDraftDate(dialog2, mediaDataController.getDraft(dialog2.id, 0))

		if (date1 < date2) {
			return@Comparator 1
		}
		else if (date1 > date2) {
			return@Comparator -1
		}
		else {
			return@Comparator 0
		}
	}

	private val dialogComparator = Comparator { dialog1: Dialog?, dialog2: Dialog? ->
		if (dialog1 is TL_dialogFolder && dialog2 !is TL_dialogFolder) {
			return@Comparator -1
		}
		else if (dialog1 !is TL_dialogFolder && dialog2 is TL_dialogFolder) {
			return@Comparator 1
		}
		else if (!dialog1!!.pinned && dialog2!!.pinned) {
			return@Comparator 1
		}
		else if (dialog1.pinned && !dialog2!!.pinned) {
			return@Comparator -1
		}
		else if (dialog1.pinned) {
			return@Comparator dialog2!!.pinnedNum.compareTo(dialog1.pinnedNum)
		}

		val mediaDataController = mediaDataController
		val date1 = DialogObject.getLastMessageOrDraftDate(dialog1, mediaDataController.getDraft(dialog1.id, 0))
		val date2 = DialogObject.getLastMessageOrDraftDate(dialog2, mediaDataController.getDraft(dialog2!!.id, 0))

		if (date1 < date2) {
			return@Comparator 1
		}
		else if (date1 > date2) {
			return@Comparator -1
		}
		else {
			return@Comparator 0
		}
	}

	private val updatesComparator = Comparator { lhs: Update, rhs: Update ->
		val ltype = getUpdateType(lhs)
		val rtype = getUpdateType(rhs)

		if (ltype != rtype) {
			return@Comparator AndroidUtilities.compare(ltype, rtype)
		}
		else if (ltype == 0) {
			return@Comparator AndroidUtilities.compare(getUpdatePts(lhs), getUpdatePts(rhs))
		}
		else if (ltype == 1) {
			return@Comparator AndroidUtilities.compare(getUpdateQts(lhs), getUpdateQts(rhs))
		}
		else if (ltype == 2) {
			val lChannel = getUpdateChannelId(lhs)
			val rChannel = getUpdateChannelId(rhs)

			if (lChannel == rChannel) {
				return@Comparator AndroidUtilities.compare(getUpdatePts(lhs), getUpdatePts(rhs))
			}
			else {
				return@Comparator AndroidUtilities.compare(lChannel, rChannel)
			}
		}
		else {
			return@Comparator 0
		}
	}

	private var gettingAppChangelog = false

	init {
		ImageLoader.instance
		messagesStorage
		locationController

		AndroidUtilities.runOnUIThread {
			val messagesController = messagesController
			notificationCenter.addObserver(messagesController, NotificationCenter.fileUploaded)
			notificationCenter.addObserver(messagesController, NotificationCenter.fileUploadFailed)
			notificationCenter.addObserver(messagesController, NotificationCenter.fileLoaded)
			notificationCenter.addObserver(messagesController, NotificationCenter.fileLoadFailed)
			notificationCenter.addObserver(messagesController, NotificationCenter.messageReceivedByServer)
			notificationCenter.addObserver(messagesController, NotificationCenter.updateMessageMedia)
		}

		addSupportUser()

		if (currentAccount == 0) {
			notificationsPreferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE)
			mainPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
			emojiPreferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Activity.MODE_PRIVATE)
		}
		else {
			notificationsPreferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications$currentAccount", Activity.MODE_PRIVATE)
			mainPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig$currentAccount", Activity.MODE_PRIVATE)
			emojiPreferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji$currentAccount", Activity.MODE_PRIVATE)
		}

		enableJoined = notificationsPreferences.getBoolean("EnableContactJoined", true)
		remoteConfigLoaded = mainPreferences.getBoolean("remoteConfigLoaded", false)
		secretWebpagePreview = mainPreferences.getInt("secretWebpage2", 2)
		maxGroupCount = mainPreferences.getInt("maxGroupCount", 200)
		maxMegagroupCount = mainPreferences.getInt("maxMegagroupCount", 10000)
		maxRecentGifsCount = mainPreferences.getInt("maxRecentGifsCount", 200)
		maxRecentStickersCount = mainPreferences.getInt("maxRecentStickersCount", 30)
		maxFaveStickersCount = mainPreferences.getInt("maxFaveStickersCount", 5)
		maxEditTime = mainPreferences.getInt("maxEditTime", 3600)
		ratingDecay = mainPreferences.getInt("ratingDecay", 2419200)
		linkPrefix = mainPreferences.getString("linkPrefix", ApplicationLoader.applicationContext.getString(R.string.domain)) ?: ApplicationLoader.applicationContext.getString(R.string.domain)
		callReceiveTimeout = mainPreferences.getInt("callReceiveTimeout", 20000)
		callRingTimeout = mainPreferences.getInt("callRingTimeout", 90000)
		callConnectTimeout = mainPreferences.getInt("callConnectTimeout", 30000)
		callPacketTimeout = mainPreferences.getInt("callPacketTimeout", 10000)
		updateCheckDelay = mainPreferences.getInt("updateCheckDelay", 24 * 60 * 60)
		maxPinnedDialogsCount = mainPreferences.getInt("maxPinnedDialogsCount", 5)
		maxFolderPinnedDialogsCount = mainPreferences.getInt("maxFolderPinnedDialogsCount", 100)
		maxMessageLength = mainPreferences.getInt("maxMessageLength", 4096)
		maxCaptionLength = mainPreferences.getInt("maxCaptionLength", 1024)
		mapProvider = mainPreferences.getInt("mapProvider", MAP_PROVIDER_NONE)
		availableMapProviders = mainPreferences.getInt("availableMapProviders", 3)
		mapKey = mainPreferences.getString("pk", ApplicationLoader.applicationContext.getString(R.string.static_maps_key))
		installReferer = mainPreferences.getString("installReferer", null)
		revokeTimeLimit = mainPreferences.getInt("revokeTimeLimit", revokeTimeLimit)
		revokeTimePmLimit = mainPreferences.getInt("revokeTimePmLimit", revokeTimePmLimit)
		canRevokePmInbox = mainPreferences.getBoolean("canRevokePmInbox", canRevokePmInbox)
		preloadFeaturedStickers = mainPreferences.getBoolean("preloadFeaturedStickers", false)
		youtubePipType = mainPreferences.getString("youtubePipType", "disabled")
		keepAliveService = mainPreferences.getBoolean("keepAliveService", false)
		backgroundConnection = mainPreferences.getBoolean("keepAliveService", false)
		promoDialogId = mainPreferences.getLong("proxy_dialog", 0)
		nextPromoInfoCheckTime = mainPreferences.getInt("nextPromoInfoCheckTime", 0)
		promoDialogType = mainPreferences.getInt("promo_dialog_type", 0)
		promoPsaMessage = mainPreferences.getString("promo_psa_message", null)
		promoPsaType = mainPreferences.getString("promo_psa_type", null)
		proxyDialogAddress = mainPreferences.getString("proxyDialogAddress", null)
		nextTosCheckTime = notificationsPreferences.getInt("nextTosCheckTime", 0)
		venueSearchBot = mainPreferences.getString("venueSearchBot", "foursquare")
		gifSearchBot = mainPreferences.getString("gifSearchBot", "gif")
		imageSearchBot = mainPreferences.getString("imageSearchBot", "pic")
		blockedCountry = mainPreferences.getBoolean("blockedCountry", false)
		dcDomainName = mainPreferences.getString("dcDomainName2", "apv3.stel.com")
		webFileDatacenterId = mainPreferences.getInt("webFileDatacenterId", ConnectionsManager.DEFAULT_DATACENTER_ID)
		suggestedLangCode = mainPreferences.getString("suggestedLangCode", "en")
		animatedEmojisZoom = mainPreferences.getFloat("animatedEmojisZoom", 0.625f)
		qrLoginCamera = mainPreferences.getBoolean("qrLoginCamera", false)
		saveGifsWithStickers = mainPreferences.getBoolean("saveGifsWithStickers", false)
		filtersEnabled = mainPreferences.getBoolean("filtersEnabled", false)
		getfileExperimentalParams = mainPreferences.getBoolean("getfileExperimentalParams", false)
		showFiltersTooltip = mainPreferences.getBoolean("showFiltersTooltip", false)
		autoarchiveAvailable = mainPreferences.getBoolean("autoarchiveAvailable", false)
		groupCallVideoMaxParticipants = mainPreferences.getInt("groipCallVideoMaxParticipants", 30)
		chatReadMarkSizeThreshold = mainPreferences.getInt("chatReadMarkSizeThreshold", 100)
		chatReadMarkExpirePeriod = mainPreferences.getInt("chatReadMarkExpirePeriod", 7 * 86400)
		ringtoneDurationMax = mainPreferences.getInt("ringtoneDurationMax", 5)
		ringtoneSizeMax = mainPreferences.getInt("ringtoneSizeMax", 102400)
		chatReadMarkExpirePeriod = mainPreferences.getInt("chatReadMarkExpirePeriod", 7 * 86400)
		suggestStickersApiOnly = mainPreferences.getBoolean("suggestStickersApiOnly", false)
		roundVideoSize = mainPreferences.getInt("roundVideoSize", 384)
		roundVideoBitrate = mainPreferences.getInt("roundVideoBitrate", 1000)
		roundAudioBitrate = mainPreferences.getInt("roundAudioBitrate", 64)
		pendingSuggestions = mainPreferences.getStringSet("pendingSuggestions", null)
		channelsLimitDefault = mainPreferences.getInt("channelsLimitDefault", 500)
		channelsLimitPremium = mainPreferences.getInt("channelsLimitPremium", 2 * channelsLimitDefault)
		savedGifsLimitDefault = mainPreferences.getInt("savedGifsLimitDefault", 200)
		savedGifsLimitPremium = mainPreferences.getInt("savedGifsLimitPremium", 400)
		stickersFavedLimitDefault = mainPreferences.getInt("stickersFavedLimitDefault", 5)
		stickersFavedLimitPremium = mainPreferences.getInt("stickersFavedLimitPremium", 200)
		dialogFiltersLimitDefault = mainPreferences.getInt("dialogFiltersLimitDefault", 10)
		dialogFiltersLimitPremium = mainPreferences.getInt("dialogFiltersLimitPremium", 20)
		dialogFiltersChatsLimitDefault = mainPreferences.getInt("dialogFiltersChatsLimitDefault", 100)
		dialogFiltersChatsLimitPremium = mainPreferences.getInt("dialogFiltersChatsLimitPremium", 200)
		dialogFiltersPinnedLimitDefault = mainPreferences.getInt("dialogFiltersPinnedLimitDefault", 5)
		dialogFiltersPinnedLimitPremium = mainPreferences.getInt("dialogFiltersPinnedLimitPremium", 10)
		publicLinksLimitDefault = mainPreferences.getInt("publicLinksLimitDefault", 10)
		publicLinksLimitPremium = mainPreferences.getInt("publicLinksLimitPremium", 20)
		captionLengthLimitDefault = mainPreferences.getInt("captionLengthLimitDefault", 1024)
		captionLengthLimitPremium = mainPreferences.getInt("captionLengthLimitPremium", 4096)
		aboutLengthLimitDefault = mainPreferences.getInt("aboutLengthLimitDefault", 255)
		aboutLengthLimitPremium = mainPreferences.getInt("aboutLengthLimitPremium", 255)
		reactionsUserMaxDefault = mainPreferences.getInt("reactionsUserMaxDefault", 1)
		reactionsUserMaxPremium = mainPreferences.getInt("reactionsUserMaxPremium", 3)
		reactionsInChatMax = mainPreferences.getInt("reactionsInChatMax", 3)
		uploadMaxFileParts = mainPreferences.getInt("uploadMaxFileParts", (FileLoader.DEFAULT_MAX_FILE_SIZE / 1024L / 512L).toInt())
		uploadMaxFilePartsPremium = mainPreferences.getInt("uploadMaxFilePartsPremium", uploadMaxFileParts * 2)
		premiumInvoiceSlug = mainPreferences.getString("premiumInvoiceSlug", null)
		premiumBotUsername = mainPreferences.getString("premiumBotUsername", null)
		premiumLocked = mainPreferences.getBoolean("premiumLocked", false)

		BuildVars.GOOGLE_AUTH_CLIENT_ID = mainPreferences.getString("googleAuthClientId", BuildVars.GOOGLE_AUTH_CLIENT_ID)!!

		val currencySet = mainPreferences.getStringSet("directPaymentsCurrency", null)

		if (currencySet != null) {
			directPaymentsCurrency.clear()
			directPaymentsCurrency.addAll(currencySet)
		}

		loadPremiumFeaturesPreviewOrder(mainPreferences.getString("premiumFeaturesTypesToPosition", null))

		pendingSuggestions = pendingSuggestions?.let { HashSet(it) } ?: HashSet()

		exportUri = mainPreferences.getStringSet("exportUri2", null)

		exportUri = exportUri?.let { HashSet(it) } ?: HashSet<String>().apply {
			add("content://(\\d+@)?com\\.whatsapp\\.provider\\.media/export_chat/")
			add("content://(\\d+@)?com\\.whatsapp\\.w4b\\.provider\\.media/export_chat/")
			add("content://jp\\.naver\\.line\\.android\\.line\\.common\\.FileProvider/export-chat/")
			add(".*WhatsApp.*\\.txt$")
		}

		exportGroupUri = mainPreferences.getStringSet("exportGroupUri", null)

		exportGroupUri = exportGroupUri?.let { HashSet(it) } ?: HashSet<String>().apply {
			add("@g.us/")
		}

		exportPrivateUri = mainPreferences.getStringSet("exportPrivateUri", null)

		exportPrivateUri = exportPrivateUri?.let { HashSet(it) } ?: HashSet<String>().apply {
			add("@s.whatsapp.net/")
		}

		autologinDomains = mainPreferences.getStringSet("autologinDomains", null)
		autologinDomains = autologinDomains?.let { HashSet(it) } ?: HashSet()

		authDomains = mainPreferences.getStringSet("authDomains", null)
		authDomains = authDomains?.let { HashSet(it) } ?: HashSet()

		autologinToken = mainPreferences.getString("autologinToken", null)

		val emojies = mainPreferences.getStringSet("diceEmojies", null)

		diceEmojies = if (emojies == null) {
			HashSet<String>().apply {
				add("\uD83C\uDFB2")
				add("\uD83C\uDFAF")
			}
		}
		else {
			HashSet(emojies)
		}

		var text = mainPreferences.getString("diceSuccess", null)

		if (text == null) {
			diceSuccess["\uD83C\uDFAF"] = DiceFrameSuccess(62, 6)
		}
		else {
			try {
				val bytes = Base64.decode(text, Base64.DEFAULT)

				if (bytes != null) {
					val data = SerializedData(bytes)
					val count = data.readInt32(true)

					for (a in 0 until count) {
						val key = data.readString(true) ?: continue
						diceSuccess[key] = DiceFrameSuccess(data.readInt32(true), data.readInt32(true))
					}

					data.cleanup()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		text = mainPreferences.getString("emojiSounds", null)

		if (text != null) {
			try {
				val bytes = Base64.decode(text, Base64.DEFAULT)

				if (bytes != null) {
					val data = SerializedData(bytes)
					val count = data.readInt32(true)

					for (a in 0 until count) {
						val key = data.readString(true) ?: continue
						emojiSounds[key] = EmojiSound(data.readInt64(true), data.readInt64(true), data.readByteArray(true))
					}

					data.cleanup()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		text = mainPreferences.getString("gifSearchEmojies", null)

		if (text == null) {
			gifSearchEmojies.add("👍")
			gifSearchEmojies.add("👎")
			gifSearchEmojies.add("😍")
			gifSearchEmojies.add("😂")
			gifSearchEmojies.add("😮")
			gifSearchEmojies.add("🙄")
			gifSearchEmojies.add("😥")
			gifSearchEmojies.add("😡")
			gifSearchEmojies.add("🥳")
			gifSearchEmojies.add("😎")
		}
		else {
			try {
				val bytes = Base64.decode(text, Base64.DEFAULT)

				if (bytes != null) {
					val data = SerializedData(bytes)
					val count = data.readInt32(true)

					for (a in 0 until count) {
						data.readString(true)?.let {
							gifSearchEmojies.add(it)
						}
					}

					data.cleanup()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		if (BuildConfig.DEBUG) {
			AndroidUtilities.runOnUIThread({
				loadAppConfig()
			}, 2000)
		}
	}

	fun getNextReactionMention(dialogId: Long, count: Int, callback: Consumer<Int>) {
		val messagesStorage = messagesStorage

		messagesStorage.storageQueue.postRunnable {
			var needRequest = true

			try {
				val cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT message_id FROM reaction_mentions WHERE state = 1 AND dialog_id = %d LIMIT 1", dialogId))
				var messageId = 0

				if (cursor.next()) {
					messageId = cursor.intValue(0)
					needRequest = false
				}

				cursor.dispose()

				if (messageId != 0) {
					messagesStorage.markMessageReactionsAsRead(dialogId, messageId, false)

					val finalMessageId = messageId

					AndroidUtilities.runOnUIThread {
						callback.accept(finalMessageId)
					}
				}
			}
			catch (e: SQLiteException) {
				FileLog.e(e)
			}

			if (needRequest) {
				val req = TL_messages_getUnreadReactions()
				req.peer = messagesController.getInputPeer(dialogId)
				req.limit = 1
				req.add_offset = count - 1

				connectionsManager.sendRequest(req) { response, _ ->
					val messageId = (response as? messages_Messages)?.messages?.firstOrNull()?.id ?: 0

					AndroidUtilities.runOnUIThread {
						callback.accept(messageId)
					}
				}
			}
		}
	}

	fun updatePremium(premium: Boolean) {
		if (dialogFilters.isEmpty()) {
			return
		}

		if (!premium) {
			if (!dialogFilters[0].isDefault) {
				for (i in 1 until dialogFilters.size) {
					if (dialogFilters[i].isDefault) {
						val defaultFilter = dialogFilters.removeAt(i)
						dialogFilters.add(0, defaultFilter)
						break
					}
				}
			}
			lockFiltersInternal()
		}
		else {
			dialogFilters.forEach {
				it.locked = false
			}
		}

		messagesStorage.saveDialogFiltersOrder()
		notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)
	}

	fun lockFiltersInternal() {
		var changed = false

		if (!userConfig.isPremium && dialogFilters.size - 1 > dialogFiltersLimitDefault) {
			val n = dialogFilters.size - 1 - dialogFiltersLimitDefault
			val filtersSortedById = ArrayList(dialogFilters)

			filtersSortedById.reverse()

			for (i in filtersSortedById.indices) {
				if (i < n) {
					if (!filtersSortedById[i]!!.locked) {
						changed = true
					}

					filtersSortedById[i]!!.locked = true
				}
				else {
					if (filtersSortedById[i]!!.locked) {
						changed = true
					}

					filtersSortedById[i]!!.locked = false
				}
			}
		}

		if (changed) {
			notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)
		}
	}

	val captionMaxLengthLimit: Int
		get() = if (userConfig.isPremium) captionLengthLimitPremium else captionLengthLimitDefault

	val aboutLimit: Int
		get() = if (userConfig.isPremium) aboutLengthLimitPremium else aboutLengthLimitDefault

	val maxUserReactionsCount: Int
		get() = if (userConfig.isPremium) reactionsUserMaxPremium else reactionsUserMaxDefault

	val chatReactionsCount: Int
		get() = if (userConfig.isPremium) reactionsInChatMax else 1

	fun isPremiumUser(currentUser: User?): Boolean {
		return !premiumLocked && currentUser?.premium == true
	}

	fun filterPremiumStickers(stickerSets: MutableList<TL_messages_stickerSet>): MutableList<TL_messages_stickerSet> {
		if (!premiumLocked) {
			return stickerSets
		}

		var i = 0

		while (i < stickerSets.size) {
			val newSet = getInstance(currentAccount).filterPremiumStickers(stickerSets[i])

			if (newSet == null) {
				stickerSets.removeAt(i)
				i--
			}
			else {
				stickerSets[i] = newSet
			}

			i++
		}

		return stickerSets
	}

	fun filterPremiumStickers(stickerSet: TL_messages_stickerSet?): TL_messages_stickerSet? {
		@Suppress("NAME_SHADOWING") var stickerSet = stickerSet

		if (!premiumLocked || stickerSet == null) {
			return stickerSet
		}
		try {
			var hasPremiumSticker = false

			for (i in stickerSet.documents.indices) {
				if (MessageObject.isPremiumSticker(stickerSet.documents[i])) {
					hasPremiumSticker = true
					break
				}
			}

			if (hasPremiumSticker) {
				val nativeByteBuffer = NativeByteBuffer(stickerSet.objectSize)

				stickerSet.serializeToStream(nativeByteBuffer)

				nativeByteBuffer.position(0)

				val newStickersSet = TL_messages_stickerSet()

				nativeByteBuffer.readInt32(true)

				newStickersSet.readParams(nativeByteBuffer, true)

				nativeByteBuffer.reuse()

				stickerSet = newStickersSet

				var i = 0

				while (i < stickerSet.documents.size) {
					if (MessageObject.isPremiumSticker(stickerSet.documents[i])) {
						stickerSet.documents.removeAt(i)
						stickerSet.packs.removeAt(i)

						i--

						if (stickerSet.documents.isEmpty()) {
							return null
						}
					}

					i++
				}
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return stickerSet
	}

	fun clearQueryTime() {
		lastServerQueryTime.clear()
		lastScheduledServerQueryTime.clear()
	}

	private fun sendLoadPeersRequest(req: TLObject, requests: ArrayList<TLObject>, pinnedDialogs: messages_Dialogs, pinnedRemoteDialogs: messages_Dialogs, users: ArrayList<User>, chats: ArrayList<Chat>, filtersToSave: ArrayList<DialogFilter>, filtersToDelete: SparseArray<DialogFilter>, filtersOrder: ArrayList<Int>, filterDialogRemovals: HashMap<Int, HashSet<Long>>, filterUserRemovals: HashMap<Int, HashSet<Long>>, filtersUnreadCounterReset: HashSet<Int>) {
		connectionsManager.sendRequest(req) { response, _ ->
			when (response) {
				is TL_messages_chats -> {
					chats.addAll(response.chats)
				}

				is Vector -> {
					response.objects.forEach {
						if (it is User) {
							users.add(it)
						}
					}
				}

				is TL_messages_peerDialogs -> {
					pinnedDialogs.dialogs.addAll(response.dialogs)
					pinnedDialogs.messages.addAll(response.messages)

					pinnedRemoteDialogs.dialogs.addAll(response.dialogs)
					pinnedRemoteDialogs.messages.addAll(response.messages)

					users.addAll(response.users)
					chats.addAll(response.chats)
				}
			}

			requests.remove(req)

			if (requests.isEmpty()) {
				messagesStorage.processLoadedFilterPeers(pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
			}
		}
	}

	fun loadFilterPeers(dialogsToLoadMap: HashMap<Long, InputPeer>, usersToLoadMap: HashMap<Long, InputPeer>, chatsToLoadMap: HashMap<Long, InputPeer>, pinnedDialogs: messages_Dialogs, pinnedRemoteDialogs: messages_Dialogs, users: ArrayList<User>, chats: ArrayList<Chat>, filtersToSave: ArrayList<DialogFilter>, filtersToDelete: SparseArray<DialogFilter>, filtersOrder: ArrayList<Int>, filterDialogRemovals: HashMap<Int, HashSet<Long>>, filterUserRemovals: HashMap<Int, HashSet<Long>>, filtersUnreadCounterReset: HashSet<Int>) {
		Utilities.stageQueue.postRunnable {
			val requests = ArrayList<TLObject>()
			var req: TL_users_getUsers? = null

			for ((_, value) in usersToLoadMap) {
				if (req == null) {
					req = TL_users_getUsers()
					requests.add(req)
				}

				req.id.add(getInputUser(value))

				if (req.id.size == 100) {
					sendLoadPeersRequest(req, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
					req = null
				}
			}

			req?.let {
				sendLoadPeersRequest(it, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
			}

			var req2: TL_messages_getChats? = null
			var req3: TL_channels_getChannels? = null

			for ((key, inputPeer) in chatsToLoadMap) {
				if (inputPeer.chat_id != 0L) {
					if (req2 == null) {
						req2 = TL_messages_getChats()
						requests.add(req2)
					}

					req2.id.add(key)

					if (req2.id.size == 100) {
						sendLoadPeersRequest(req2, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
						req2 = null
					}
				}
				else if (inputPeer.channel_id != 0L) {
					if (req3 == null) {
						req3 = TL_channels_getChannels()
						requests.add(req3)
					}

					req3.id.add(getInputChannel(inputPeer))

					if (req3.id.size == 100) {
						sendLoadPeersRequest(req3, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
						req3 = null
					}
				}
			}

			req2?.let {
				sendLoadPeersRequest(it, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
			}

			req3?.let {
				sendLoadPeersRequest(it, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
			}

			var req4: TL_messages_getPeerDialogs? = null

			for ((_, value) in dialogsToLoadMap) {
				if (req4 == null) {
					req4 = TL_messages_getPeerDialogs()
					requests.add(req4)
				}

				val inputDialogPeer = TL_inputDialogPeer()
				inputDialogPeer.peer = value

				req4.peers.add(inputDialogPeer)

				if (req4.peers.size == 100) {
					sendLoadPeersRequest(req4, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
					req4 = null
				}
			}

			req4?.let {
				sendLoadPeersRequest(it, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset)
			}
		}
	}

	fun processLoadedDialogFilters(filters: ArrayList<DialogFilter>, pinnedDialogs: messages_Dialogs, pinnedRemoteDialogs: messages_Dialogs?, users: ArrayList<User>?, chats: ArrayList<Chat>?, encryptedChats: ArrayList<EncryptedChat>?, remote: Int) {
		Utilities.stageQueue.postRunnable {
			val newDialogsDict = LongSparseArray<Dialog>()
			val encChatsDict: SparseArray<EncryptedChat?>?
			val newDialogMessage = LongSparseArray<MessageObject>()
			val usersDict = LongSparseArray<User>()
			val chatsDict = LongSparseArray<Chat>()

			pinnedDialogs.users.forEach {
				usersDict.put(it.id, it)
			}

			pinnedDialogs.chats.forEach {
				chatsDict.put(it.id, it)
			}

			if (encryptedChats != null) {
				encChatsDict = SparseArray()

				for (encryptedChat in encryptedChats) {
					encChatsDict.put(encryptedChat.id, encryptedChat)
				}
			}
			else {
				encChatsDict = null
			}

			val newMessages = mutableListOf<MessageObject>()

			for (message in pinnedDialogs.messages) {
				val peerId = message.peer_id

				if (peerId != null) {
					if (peerId.channel_id != 0L) {
						val chat = chatsDict[peerId.channel_id]

						if (chat != null && chat.left && (promoDialogId == 0L || promoDialogId != -chat.id)) {
							continue
						}
					}
					else if (peerId.chat_id != 0L) {
						val chat = chatsDict[peerId.chat_id]

						if (chat?.migrated_to != null) {
							continue
						}
					}
				}

				val messageObject = MessageObject(currentAccount, message, usersDict, chatsDict, generateLayout = false, checkMediaExists = false)

				newMessages.add(messageObject)

				// MARK: remove check for service messages
				if (message !is TL_messageService) {
					// MARK: but keep this line
					newDialogMessage.put(messageObject.dialogId, messageObject)
				}
			}

			fileLoader.checkMediaExistence(newMessages)

			for (d in pinnedDialogs.dialogs) {
				DialogObject.initDialog(d)

				if (d.id == 0L) {
					continue
				}

				if (DialogObject.isEncryptedDialog(d.id) && encChatsDict != null) {
					if (encChatsDict[DialogObject.getEncryptedChatId(d.id)] == null) {
						continue
					}
				}

				if (promoDialogId != 0L && promoDialogId == d.id) {
					promoDialog = d
				}

				if (d.last_message_date == 0) {
					val mess = newDialogMessage[d.id]

					if (mess != null) {
						d.last_message_date = mess.messageOwner!!.date
					}
				}

				if (DialogObject.isChannel(d)) {
					val chat = chatsDict[-d.id]

					if (chat != null) {
						if (chat.left && (promoDialogId == 0L || promoDialogId != d.id)) {
							continue
						}
					}

					channelsPts.put(-d.id, d.pts)
				}
				else if (d.id < 0) {
					val chat = chatsDict[-d.id]

					if (chat?.migrated_to != null) {
						continue
					}
				}

				newDialogsDict.put(d.id, d)

				var value = dialogs_read_inbox_max[d.id]

				if (value == null) {
					value = 0
				}

				dialogs_read_inbox_max[d.id] = max(value, d.read_inbox_max_id)

				value = dialogs_read_outbox_max[d.id]

				if (value == null) {
					value = 0
				}

				dialogs_read_outbox_max[d.id] = max(value, d.read_outbox_max_id)
			}

			if (pinnedRemoteDialogs != null && pinnedRemoteDialogs.dialogs.isNotEmpty()) {
				ImageLoader.saveMessagesThumbs(pinnedRemoteDialogs.messages)

				for (a in pinnedRemoteDialogs.messages.indices) {
					val message = pinnedRemoteDialogs.messages[a]

					if (message.action is TL_messageActionChatDeleteUser) {
						val user = usersDict[message.action?.user_id ?: Long.MIN_VALUE]

						if (user?.bot == true) {
							message.reply_markup = TL_replyKeyboardHide()
							message.flags = message.flags or 64
						}
					}

					if (message.action is TL_messageActionChatMigrateTo || message.action is TL_messageActionChannelCreate) {
						message.unread = false
						message.media_unread = false
					}
					else {
						val readMax = if (message.out) dialogs_read_outbox_max else dialogs_read_inbox_max
						var value = readMax[message.dialog_id]

						if (value == null) {
							value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
							readMax[message.dialog_id] = value
						}

						message.unread = value < message.id
					}
				}

				messagesStorage.putDialogs(pinnedRemoteDialogs, 0)
			}

			AndroidUtilities.runOnUIThread {
				if (remote != 2) {
					dialogFilters = filters
					dialogFiltersById.clear()

					for (filter in dialogFilters) {
						dialogFiltersById.put(filter.id, filter)
					}

					dialogFilters.sortBy { it.order }

					putUsers(users, true)
					putChats(chats, true)

					dialogFiltersLoaded = true

					notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)

					if (remote == 0) {
						loadRemoteFilters(false)
					}

					if (pinnedRemoteDialogs != null && pinnedRemoteDialogs.dialogs.isNotEmpty()) {
						applyDialogsNotificationsSettings(pinnedRemoteDialogs.dialogs)
					}

					if (encryptedChats != null) {
						for (a in encryptedChats.indices) {
							val encryptedChat = encryptedChats[a]

							if (encryptedChat is TL_encryptedChat && AndroidUtilities.getMyLayerVersion(encryptedChat.layer) < SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
								secretChatHelper.sendNotifyLayerMessage(encryptedChat, null)
							}

							putEncryptedChat(encryptedChat, true)
						}
					}

					for (a in 0 until newDialogsDict.size()) {
						val key = newDialogsDict.keyAt(a)
						val value = newDialogsDict.valueAt(a)
						val currentDialog = dialogs_dict[key]

						if (pinnedRemoteDialogs != null && pinnedRemoteDialogs.dialogs.contains(value)) {
							if (value.draft is TL_draftMessage) {
								mediaDataController.saveDraft(value.id, 0, value.draft, null, false)
							}

							currentDialog?.notify_settings = value.notify_settings
						}

						val newMsg = newDialogMessage[value.id]

						if (currentDialog == null) {
							dialogs_dict.put(key, value)
							dialogMessage.put(key, newMsg)

							if (newMsg != null && newMsg.messageOwner?.peer_id?.channel_id == 0L) {
								dialogMessagesByIds.put(newMsg.id, newMsg)

								if (newMsg.messageOwner?.random_id != 0L) {
									dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
								}
							}
						}
						else {
							currentDialog.pinned = value.pinned
							currentDialog.pinnedNum = value.pinnedNum

							val oldMsg = dialogMessage[key]

							if (oldMsg != null && oldMsg.deleted || oldMsg == null || currentDialog.top_message > 0) {
								if (value.top_message >= currentDialog.top_message) {
									dialogs_dict.put(key, value)
									dialogMessage.put(key, newMsg)

									if (oldMsg != null) {
										if (oldMsg.messageOwner?.peer_id?.channel_id == 0L) {
											dialogMessagesByIds.remove(oldMsg.id)
										}

										if (oldMsg.messageOwner?.random_id != 0L) {
											dialogMessagesByRandomIds.remove(oldMsg.messageOwner!!.random_id)
										}
									}

									if (newMsg != null && newMsg.messageOwner?.peer_id?.channel_id == 0L) {
										if (oldMsg != null && oldMsg.id == newMsg.id) {
											newMsg.deleted = oldMsg.deleted
										}

										dialogMessagesByIds.put(newMsg.id, newMsg)

										if (newMsg.messageOwner?.random_id != 0L) {
											dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
										}
									}
								}
							}
							else {
								if (newMsg == null || newMsg.messageOwner!!.date > oldMsg.messageOwner!!.date) {
									dialogs_dict.put(key, value)
									dialogMessage.put(key, newMsg)

									if (oldMsg.messageOwner?.peer_id?.channel_id == 0L) {
										dialogMessagesByIds.remove(oldMsg.id)
									}

									if (newMsg != null) {
										if (oldMsg.id == newMsg.id) {
											newMsg.deleted = oldMsg.deleted
										}

										if (newMsg.messageOwner?.peer_id?.channel_id == 0L) {
											dialogMessagesByIds.put(newMsg.id, newMsg)

											if (newMsg.messageOwner?.random_id != 0L) {
												dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
											}
										}
									}

									if (oldMsg.messageOwner?.random_id != 0L) {
										dialogMessagesByRandomIds.remove(oldMsg.messageOwner!!.random_id)
									}
								}
							}
						}
					}

					allDialogs.clear()

					var a = 0
					val size = dialogs_dict.size()

					while (a < size) {
						val dialog = dialogs_dict.valueAt(a)

						if (deletingDialogs.indexOfKey(dialog!!.id) >= 0) {
							a++
							continue
						}

						allDialogs.add(dialog)

						a++
					}

					sortDialogs(null)

					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
				}

				if (remote != 0) {
					userConfig.filtersLoaded = true
					userConfig.saveConfig(false)
					loadingRemoteFilters = false
					notificationCenter.postNotificationName(NotificationCenter.filterSettingsUpdated)
				}

				lockFiltersInternal()
			}
		}
	}

	fun loadSuggestedFilters() {
		if (loadingSuggestedFilters) {
			return
		}

		loadingSuggestedFilters = true

		val req = TL_messages_getSuggestedDialogFilters()

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				loadingSuggestedFilters = false
				suggestedFilters.clear()

				if (response is Vector) {
					response.objects.forEach {
						if (it is TL_dialogFilterSuggested) {
							suggestedFilters.add(it)
						}
					}
				}

				notificationCenter.postNotificationName(NotificationCenter.suggestedFiltersLoaded)
			}
		}
	}

	fun loadRemoteFilters(force: Boolean) {
		if (loadingRemoteFilters || !userConfig.isClientActivated || !force && userConfig.filtersLoaded) {
			return
		}

		if (force) {
			userConfig.filtersLoaded = false
			userConfig.saveConfig(false)
		}

		val req = TL_messages_getDialogFilters()

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is Vector) {
				messagesStorage.checkLoadedRemoteFilters(response)
			}
			else {
				AndroidUtilities.runOnUIThread {
					loadingRemoteFilters = false
				}
			}
		}
	}

	fun selectDialogFilter(filter: DialogFilter, index: Int) {
		if (selectedDialogFilter[index] === filter) {
			return
		}

		val prevFilter = selectedDialogFilter[index]

		selectedDialogFilter[index] = filter

		if (selectedDialogFilter[if (index == 0) 1 else 0] === filter) {
			selectedDialogFilter[if (index == 0) 1 else 0] = null
		}

		if (selectedDialogFilter[index] == null) {
			prevFilter?.dialogs?.clear()
		}
		else {
			sortDialogs(null)
		}
	}

	fun onFilterUpdate(filter: DialogFilter) {
		for (a in 0..1) {
			if (selectedDialogFilter[a] === filter) {
				sortDialogs(null)
				notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload, true)
				break
			}
		}
	}

	fun addFilter(filter: DialogFilter, atBegin: Boolean) {
		if (atBegin) {
			val order = dialogFilters.minOfOrNull { it.order } ?: 254

			filter.order = order - 1

			if (dialogFilters[0].isDefault && !userConfig.isPremium) {
				dialogFilters.add(1, filter)
			}
			else {
				dialogFilters.add(0, filter)
			}
		}
		else {
			val order = dialogFilters.maxOfOrNull { it.order } ?: 0
			filter.order = order + 1
			dialogFilters.add(filter)
		}

		dialogFiltersById.put(filter.id, filter)

		if (dialogFilters.size == 1 && SharedConfig.getChatSwipeAction(currentAccount) != SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS) {
			SharedConfig.updateChatListSwipeSetting(SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS)
		}

		lockFiltersInternal()
	}

	fun removeFilter(filter: DialogFilter) {
		dialogFilters.remove(filter)
		dialogFiltersById.remove(filter.id)
		notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)
	}

	fun loadAppConfig() {
		if (loadingAppConfig) {
			return
		}

		loadingAppConfig = true

		val req = TL_help_getAppConfig()

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is TL_jsonObject) {
					val editor = mainPreferences.edit()
					var changed = false
					var keelAliveChanged = false

					resetAppConfig()

					var a = 0
					val n = response.value.size

					while (a < n) {
						val value = response.value[a]

						when (value.key) {
							"login_google_oauth_client_id" -> {
								if (value.value is TL_jsonString) {
									val str = (value.value as TL_jsonString).value

									if (BuildVars.GOOGLE_AUTH_CLIENT_ID != str) {
										BuildVars.GOOGLE_AUTH_CLIENT_ID = str
										editor.putString("googleAuthClientId", BuildVars.GOOGLE_AUTH_CLIENT_ID)
										changed = true
									}
								}
							}

							"premium_playmarket_direct_currency_list" -> {
								if (value.value is TL_jsonArray) {
									val arr = value.value as TL_jsonArray
									val currencySet = HashSet<String>()

									for (el in arr.value) {
										if (el is TL_jsonString) {
											val currency = el.value
											currencySet.add(currency)
										}
									}

									if (!(HashSet(directPaymentsCurrency).containsAll(currencySet) && currencySet.containsAll(directPaymentsCurrency))) {
										directPaymentsCurrency.clear()
										directPaymentsCurrency.addAll(currencySet)

										editor.putStringSet("directPaymentsCurrency", currencySet)

										changed = true

										NotificationCenter.globalInstance.postNotificationName(NotificationCenter.billingProductDetailsUpdated)
									}
								}
							}

							"premium_purchase_blocked" -> {
								if (value.value is TL_jsonBool) {
									if (premiumLocked != (value.value as TL_jsonBool).value) {
										premiumLocked = (value.value as TL_jsonBool).value
										editor.putBoolean("premiumLocked", premiumLocked)
										changed = true
									}
								}
							}

							"premium_bot_username" -> {
								if (value.value is TL_jsonString) {
									val string = (value.value as TL_jsonString).value

									if (string != premiumBotUsername) {
										premiumBotUsername = string
										editor.putString("premiumBotUsername", premiumBotUsername)
										changed = true
									}
								}
							}

							"premium_invoice_slug" -> {
								if (value.value is TL_jsonString) {
									val string = (value.value as TL_jsonString).value

									if (string != premiumInvoiceSlug) {
										premiumInvoiceSlug = string
										editor.putString("premiumInvoiceSlug", premiumInvoiceSlug)
										changed = true
									}
								}
							}

							"premium_promo_order" -> {
								if (value.value is TL_jsonArray) {
									val order = value.value as TL_jsonArray
									changed = savePremiumFeaturesPreviewOrder(editor, order.value)
								}
							}

							"emojies_animated_zoom" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (animatedEmojisZoom.toDouble() != number.value) {
										animatedEmojisZoom = number.value.toFloat()
										editor.putFloat("animatedEmojisZoom", animatedEmojisZoom)
										changed = true
									}
								}
							}

							"getfile_experimental_params" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != getfileExperimentalParams) {
										getfileExperimentalParams = bool.value
										editor.putBoolean("getfileExperimentalParams", getfileExperimentalParams)
										changed = true
									}
								}
							}

							"dialog_filters_enabled" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != filtersEnabled) {
										filtersEnabled = bool.value
										editor.putBoolean("filtersEnabled", filtersEnabled)
										changed = true
									}
								}
							}

							"dialog_filters_tooltip" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != showFiltersTooltip) {
										showFiltersTooltip = bool.value
										editor.putBoolean("showFiltersTooltip", showFiltersTooltip)
										changed = true
										notificationCenter.postNotificationName(NotificationCenter.filterSettingsUpdated)
									}
								}
							}

							"youtube_pip" -> {
								if (value.value is TL_jsonString) {
									val string = value.value as TL_jsonString

									if (string.value != youtubePipType) {
										youtubePipType = string.value
										editor.putString("youtubePipType", youtubePipType)
										changed = true
									}
								}
							}

							"background_connection" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != backgroundConnection) {
										backgroundConnection = bool.value
										editor.putBoolean("backgroundConnection", backgroundConnection)
										changed = true
										keelAliveChanged = true
									}
								}
							}

							"keep_alive_service" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != keepAliveService) {
										keepAliveService = bool.value
										editor.putBoolean("keepAliveService", keepAliveService)
										changed = true
										keelAliveChanged = true
									}
								}
							}

							"qr_login_camera" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != qrLoginCamera) {
										qrLoginCamera = bool.value
										editor.putBoolean("qrLoginCamera", qrLoginCamera)
										changed = true
									}
								}
							}

							"save_gifs_with_stickers" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != saveGifsWithStickers) {
										saveGifsWithStickers = bool.value
										editor.putBoolean("saveGifsWithStickers", saveGifsWithStickers)
										changed = true
									}
								}
							}

							"url_auth_domains" -> {
								val newDomains = mutableSetOf<String>()

								if (value.value is TL_jsonArray) {
									for (domain in (value.value as TL_jsonArray).value) {
										if (domain is TL_jsonString) {
											newDomains.add(domain.value)
										}
									}
								}

								if (authDomains != newDomains) {
									authDomains = newDomains
									editor.putStringSet("authDomains", authDomains)
									changed = true
								}
							}

							"autologin_domains" -> {
								val newDomains = mutableSetOf<String>()

								if (value.value is TL_jsonArray) {
									for (domain in (value.value as TL_jsonArray).value) {
										if (domain is TL_jsonString) {
											newDomains.add(domain.value)
										}
									}
								}

								if (autologinDomains != newDomains) {
									autologinDomains = newDomains
									editor.putStringSet("autologinDomains", autologinDomains)
									changed = true
								}
							}

							"autologin_token" -> {
								if (value.value is TL_jsonString) {
									val string = value.value as TL_jsonString

									if (string.value != autologinToken) {
										autologinToken = string.value
										editor.putString("autologinToken", autologinToken)
										changed = true
									}
								}
							}

							"emojies_send_dice" -> {
								val newEmojies = mutableSetOf<String>()

								if (value.value is TL_jsonArray) {
									for (emoji in (value.value as TL_jsonArray).value) {
										if (emoji is TL_jsonString) {
											newEmojies.add(emoji.value.replace("\uFE0F", ""))
										}
									}
								}

								if (diceEmojies != newEmojies) {
									diceEmojies = newEmojies
									editor.putStringSet("diceEmojies", diceEmojies)
									changed = true
								}
							}

							"gif_search_emojies" -> {
								val newEmojies = ArrayList<String>()

								if (value.value is TL_jsonArray) {
									for (emoji in (value.value as TL_jsonArray).value) {
										if (emoji is TL_jsonString) {
											newEmojies.add(emoji.value.replace("\uFE0F", ""))
										}
									}
								}

								if (gifSearchEmojies != newEmojies) {
									gifSearchEmojies = newEmojies

									val serializedData = SerializedData()
									serializedData.writeInt32(gifSearchEmojies.size)

									for (e in gifSearchEmojies) {
										serializedData.writeString(e)
									}

									editor.putString("gifSearchEmojies", Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT))

									serializedData.cleanup()

									changed = true
								}
							}

							"emojies_send_dice_success" -> {
								try {
									val newEmojies = HashMap<String, DiceFrameSuccess>()

									if (value.value is TL_jsonObject) {
										val jsonObject = value.value as TL_jsonObject
										var b = 0
										val n2 = jsonObject.value.size

										while (b < n2) {
											val `val` = jsonObject.value[b]

											if (`val`.value is TL_jsonObject) {
												val jsonObject2 = `val`.value as TL_jsonObject
												var n = Int.MAX_VALUE
												var f = Int.MAX_VALUE
												var c = 0
												val n3 = jsonObject2.value.size

												while (c < n3) {
													val val2 = jsonObject2.value[c]

													if (val2.value is TL_jsonNumber) {
														if ("value" == val2.key) {
															n = (val2.value as TL_jsonNumber).value.toInt()
														}
														else if ("frame_start" == val2.key) {
															f = (val2.value as TL_jsonNumber).value.toInt()
														}
													}

													c++
												}

												if (f != Int.MAX_VALUE && n != Int.MAX_VALUE) {
													newEmojies[`val`.key.replace("\uFE0F", "")] = DiceFrameSuccess(f, n)
												}
											}

											b++
										}
									}

									if (diceSuccess != newEmojies) {
										diceSuccess = newEmojies

										val serializedData = SerializedData()
										serializedData.writeInt32(diceSuccess.size)

										for ((key, frameSuccess) in diceSuccess) {
											serializedData.writeString(key)
											serializedData.writeInt32(frameSuccess.frame)
											serializedData.writeInt32(frameSuccess.num)
										}

										editor.putString("diceSuccess", Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT))

										serializedData.cleanup()

										changed = true
									}
								}
								catch (e: Exception) {
									FileLog.e(e)
								}
							}

							"autoarchive_setting_available" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != autoarchiveAvailable) {
										autoarchiveAvailable = bool.value
										editor.putBoolean("autoarchiveAvailable", autoarchiveAvailable)
										changed = true
									}
								}
							}

							"groupcall_video_participants_max" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != groupCallVideoMaxParticipants.toDouble()) {
										groupCallVideoMaxParticipants = number.value.toInt()
										editor.putInt("groipCallVideoMaxParticipants", groupCallVideoMaxParticipants)
										changed = true
									}
								}
							}

							"chat_read_mark_size_threshold" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != chatReadMarkSizeThreshold.toDouble()) {
										chatReadMarkSizeThreshold = number.value.toInt()
										editor.putInt("chatReadMarkSizeThreshold", chatReadMarkSizeThreshold)
										changed = true
									}
								}
							}

							"chat_read_mark_expire_period" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != chatReadMarkExpirePeriod.toDouble()) {
										chatReadMarkExpirePeriod = number.value.toInt()
										editor.putInt("chatReadMarkExpirePeriod", chatReadMarkExpirePeriod)
										changed = true
									}
								}
							}

							"inapp_update_check_delay" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != updateCheckDelay.toDouble()) {
										updateCheckDelay = number.value.toInt()
										editor.putInt("updateCheckDelay", updateCheckDelay)
										changed = true
									}
								}
								else if (value.value is TL_jsonString) {
									val number = value.value as TL_jsonString
									val delay = Utilities.parseInt(number.value)

									if (delay != updateCheckDelay) {
										updateCheckDelay = delay
										editor.putInt("updateCheckDelay", updateCheckDelay)
										changed = true
									}
								}
							}

							"round_video_encoding" -> {
								if (value.value is TL_jsonObject) {
									val jsonObject = value.value as TL_jsonObject
									var b = 0
									val n2 = jsonObject.value.size

									while (b < n2) {
										val value2 = jsonObject.value[b]

										when (value2.key) {
											"diameter" -> {
												if (value2.value is TL_jsonNumber) {
													val number = value2.value as TL_jsonNumber

													if (number.value != roundVideoSize.toDouble()) {
														roundVideoSize = number.value.toInt()
														editor.putInt("roundVideoSize", roundVideoSize)
														changed = true
													}
												}
											}

											"video_bitrate" -> {
												if (value2.value is TL_jsonNumber) {
													val number = value2.value as TL_jsonNumber

													if (number.value != roundVideoBitrate.toDouble()) {
														roundVideoBitrate = number.value.toInt()
														editor.putInt("roundVideoBitrate", roundVideoBitrate)
														changed = true
													}
												}
											}

											"audio_bitrate" -> {
												if (value2.value is TL_jsonNumber) {
													val number = value2.value as TL_jsonNumber

													if (number.value != roundAudioBitrate.toDouble()) {
														roundAudioBitrate = number.value.toInt()
														editor.putInt("roundAudioBitrate", roundAudioBitrate)
														changed = true
													}
												}
											}
										}

										b++
									}
								}
							}

							"stickers_emoji_suggest_only_api" -> {
								if (value.value is TL_jsonBool) {
									val bool = value.value as TL_jsonBool

									if (bool.value != suggestStickersApiOnly) {
										suggestStickersApiOnly = bool.value
										editor.putBoolean("suggestStickersApiOnly", suggestStickersApiOnly)
										changed = true
									}
								}
							}

							"export_regex" -> {
								val newExport = HashSet<String>()

								if (value.value is TL_jsonArray) {
									for (regex in (value.value as TL_jsonArray).value) {
										if (regex is TL_jsonString) {
											newExport.add(regex.value)
										}
									}
								}

								if (exportUri != newExport) {
									exportUri = newExport
									editor.putStringSet("exportUri2", exportUri)
									changed = true
								}
							}

							"export_group_urls" -> {
								val newExport = mutableSetOf<String>()

								if (value.value is TL_jsonArray) {
									for (url in (value.value as TL_jsonArray).value) {
										if (url is TL_jsonString) {
											newExport.add(url.value)
										}
									}
								}

								if (exportGroupUri != newExport) {
									exportGroupUri = newExport
									editor.putStringSet("exportGroupUri", exportGroupUri)
									changed = true
								}
							}

							"export_private_urls" -> {
								val newExport = mutableSetOf<String>()

								if (value.value is TL_jsonArray) {
									for (url in (value.value as TL_jsonArray).value) {
										if (url is TL_jsonString) {
											newExport.add(url.value)
										}
									}
								}

								if (exportPrivateUri != newExport) {
									exportPrivateUri = newExport
									editor.putStringSet("exportPrivateUri", exportPrivateUri)
									changed = true
								}
							}

							"pending_suggestions" -> {
								val newSuggestions = mutableSetOf<String>()

								if (value.value is TL_jsonArray) {
									for (suggestion in (value.value as TL_jsonArray).value) {
										if (suggestion is TL_jsonString) {
											newSuggestions.add(suggestion.value)
										}
									}
								}

								if (pendingSuggestions != newSuggestions) {
									pendingSuggestions = newSuggestions
									editor.putStringSet("pendingSuggestions", pendingSuggestions)
									notificationCenter.postNotificationName(NotificationCenter.newSuggestionsAvailable)
									changed = true
								}
							}

							"emojies_sounds" -> {
								try {
									val newEmojies = HashMap<String, EmojiSound>()

									if (value.value is TL_jsonObject) {
										val jsonObject = value.value as TL_jsonObject
										var b = 0
										val n2 = jsonObject.value.size

										while (b < n2) {
											val `val` = jsonObject.value[b]

											if (`val`.value is TL_jsonObject) {
												val jsonObject2 = `val`.value as TL_jsonObject
												var i: Long = 0
												var ah: Long = 0
												var fr: String? = null
												var c = 0
												val n3 = jsonObject2.value.size

												while (c < n3) {
													val val2 = jsonObject2.value[c]

													if (val2.value is TL_jsonString) {
														when (val2.key) {
															"id" -> {
																i = Utilities.parseLong((val2.value as TL_jsonString).value)
															}

															"access_hash" -> {
																ah = Utilities.parseLong((val2.value as TL_jsonString).value)
															}

															"file_reference_base64" -> {
																fr = (val2.value as TL_jsonString).value
															}
														}
													}

													c++
												}

												if (i != 0L && ah != 0L && fr != null) {
													newEmojies[`val`.key.replace("\uFE0F", "")] = EmojiSound(i, ah, fr)
												}
											}

											b++
										}
									}

									if (emojiSounds != newEmojies) {
										emojiSounds = newEmojies

										val serializedData = SerializedData()
										serializedData.writeInt32(emojiSounds.size)

										for ((key, emojiSound) in emojiSounds) {
											serializedData.writeString(key)
											serializedData.writeInt64(emojiSound.id)
											serializedData.writeInt64(emojiSound.accessHash)
											serializedData.writeByteArray(emojiSound.fileReference)
										}

										editor.putString("emojiSounds", Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT))

										serializedData.cleanup()

										changed = true
									}
								}
								catch (e: Exception) {
									FileLog.e(e)
								}
							}

							"ringtone_size_max" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != ringtoneSizeMax.toDouble()) {
										ringtoneSizeMax = number.value.toInt()
										editor.putInt("ringtoneSizeMax", ringtoneSizeMax)
										changed = true
									}
								}
							}

							"ringtone_duration_max" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != ringtoneDurationMax.toDouble()) {
										ringtoneDurationMax = number.value.toInt()
										editor.putInt("ringtoneDurationMax", ringtoneDurationMax)
										changed = true
									}
								}
							}

							"channels_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != channelsLimitDefault.toDouble()) {
										channelsLimitDefault = number.value.toInt()
										editor.putInt("channelsLimitDefault", channelsLimitDefault)
										changed = true
									}
								}
							}

							"channels_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != channelsLimitPremium.toDouble()) {
										channelsLimitPremium = number.value.toInt()
										editor.putInt("channelsLimitPremium", channelsLimitPremium)
										changed = true
									}
								}
							}

							"saved_gifs_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != savedGifsLimitDefault.toDouble()) {
										savedGifsLimitDefault = number.value.toInt()
										editor.putInt("savedGifsLimitDefault", savedGifsLimitDefault)
										changed = true
									}
								}
							}

							"saved_gifs_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != savedGifsLimitPremium.toDouble()) {
										savedGifsLimitPremium = number.value.toInt()
										editor.putInt("savedGifsLimitPremium", savedGifsLimitPremium)
										changed = true
									}
								}
							}

							"stickers_faved_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != stickersFavedLimitDefault.toDouble()) {
										stickersFavedLimitDefault = number.value.toInt()
										editor.putInt("stickersFavedLimitDefault", stickersFavedLimitDefault)
										changed = true
									}
								}
							}

							"stickers_faved_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != stickersFavedLimitPremium.toDouble()) {
										stickersFavedLimitPremium = number.value.toInt()
										editor.putInt("stickersFavedLimitPremium", stickersFavedLimitPremium)
										changed = true
									}
								}
							}

							"dialog_filters_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != dialogFiltersLimitDefault.toDouble()) {
										dialogFiltersLimitDefault = number.value.toInt()
										editor.putInt("dialogFiltersLimitDefault", dialogFiltersLimitDefault)
										changed = true
									}
								}
							}

							"dialog_filters_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != dialogFiltersLimitPremium.toDouble()) {
										dialogFiltersLimitPremium = number.value.toInt()
										editor.putInt("dialogFiltersLimitPremium", dialogFiltersLimitPremium)
										changed = true
									}
								}
							}

							"dialog_filters_chats_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != dialogFiltersChatsLimitDefault.toDouble()) {
										dialogFiltersChatsLimitDefault = number.value.toInt()
										editor.putInt("dialogFiltersChatsLimitDefault", dialogFiltersChatsLimitDefault)
										changed = true
									}
								}
							}

							"dialog_filters_chats_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != dialogFiltersChatsLimitPremium.toDouble()) {
										dialogFiltersChatsLimitPremium = number.value.toInt()
										editor.putInt("dialogFiltersChatsLimitPremium", dialogFiltersChatsLimitPremium)
										changed = true
									}
								}
							}

							"dialog_filters_pinned_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != dialogFiltersPinnedLimitDefault.toDouble()) {
										dialogFiltersPinnedLimitDefault = number.value.toInt()
										editor.putInt("dialogFiltersPinnedLimitDefault", dialogFiltersPinnedLimitDefault)
										changed = true
									}
								}
							}

							"dialog_filters_pinned_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != dialogFiltersPinnedLimitPremium.toDouble()) {
										dialogFiltersPinnedLimitPremium = number.value.toInt()
										editor.putInt("dialogFiltersPinnedLimitPremium", dialogFiltersPinnedLimitPremium)
										changed = true
									}
								}
							}

							"upload_max_fileparts_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != uploadMaxFileParts.toDouble()) {
										uploadMaxFileParts = number.value.toInt()
										editor.putInt("uploadMaxFileParts", uploadMaxFileParts)
										changed = true
									}
								}
							}

							"upload_max_fileparts_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != uploadMaxFilePartsPremium.toDouble()) {
										uploadMaxFilePartsPremium = number.value.toInt()
										editor.putInt("uploadMaxFilePartsPremium", uploadMaxFilePartsPremium)
										changed = true
									}
								}
							}

							"channels_public_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != publicLinksLimitDefault.toDouble()) {
										publicLinksLimitDefault = number.value.toInt()
										editor.putInt("publicLinksLimit", publicLinksLimitDefault)
										changed = true
									}
								}
							}

							"channels_public_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != publicLinksLimitPremium.toDouble()) {
										publicLinksLimitPremium = number.value.toInt()
										editor.putInt("publicLinksLimitPremium", publicLinksLimitPremium)
										changed = true
									}
								}
							}

							"caption_length_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != captionLengthLimitDefault.toDouble()) {
										captionLengthLimitDefault = number.value.toInt()
										editor.putInt("captionLengthLimitDefault", captionLengthLimitDefault)
										changed = true
									}
								}
							}

							"caption_length_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != captionLengthLimitPremium.toDouble()) {
										captionLengthLimitPremium = number.value.toInt()
										editor.putInt("captionLengthLimitPremium", captionLengthLimitPremium)
										changed = true
									}
								}
							}

							"about_length_limit_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != aboutLengthLimitDefault.toDouble()) {
										aboutLengthLimitDefault = number.value.toInt()
										editor.putInt("aboutLengthLimitDefault", aboutLengthLimitDefault)
										changed = true
									}
								}
							}

							"about_length_limit_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != aboutLengthLimitPremium.toDouble()) {
										aboutLengthLimitPremium = number.value.toInt()
										editor.putInt("aboutLengthLimitPremium", aboutLengthLimitPremium)
										changed = true
									}
								}
							}

							"reactions_user_max_default" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != reactionsUserMaxDefault.toDouble()) {
										reactionsUserMaxDefault = number.value.toInt()
										editor.putInt("reactionsUserMaxDefault", reactionsUserMaxDefault)
										changed = true
									}
								}
							}

							"reactions_user_max_premium" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != reactionsUserMaxPremium.toDouble()) {
										reactionsUserMaxPremium = number.value.toInt()
										editor.putInt("reactionsUserMaxPremium", reactionsUserMaxPremium)
										changed = true
									}
								}
							}

							"reactions_in_chat_max" -> {
								if (value.value is TL_jsonNumber) {
									val number = value.value as TL_jsonNumber

									if (number.value != reactionsInChatMax.toDouble()) {
										reactionsInChatMax = number.value.toInt()
										editor.putInt("reactionsInChatMax", reactionsInChatMax)
										changed = true
									}
								}
							}
						}

						a++
					}

					if (changed) {
						editor.commit()
					}

					if (keelAliveChanged) {
						ApplicationLoader.startPushService()
						val connectionsManager = connectionsManager
						connectionsManager.isPushConnectionEnabled = connectionsManager.isPushConnectionEnabled
					}
				}

				loadingAppConfig = false
			}
		}
	}

	private fun resetAppConfig() {
		getfileExperimentalParams = false
		mainPreferences.edit().remove("getfileExperimentalParams").commit()
	}

	private fun savePremiumFeaturesPreviewOrder(editor: SharedPreferences.Editor, value: ArrayList<JSONValue>): Boolean {
		val stringBuilder = StringBuilder()

		premiumFeaturesTypesToPosition.clear()

		for (i in value.indices) {
			var s: String? = null

			if (value[i] is TL_jsonString) {
				s = (value[i] as TL_jsonString).value
			}

			if (s != null) {
				val type = PremiumPreviewFragment.serverStringToFeatureType(s)

				if (type >= 0) {
					premiumFeaturesTypesToPosition.put(type, i)

					if (stringBuilder.isNotEmpty()) {
						stringBuilder.append('_')
					}

					stringBuilder.append(type)
				}
			}
		}

		val changed: Boolean

		if (stringBuilder.isNotEmpty()) {
			val string = stringBuilder.toString()
			changed = string != mainPreferences.getString("premiumFeaturesTypesToPosition", null)
			editor.putString("premiumFeaturesTypesToPosition", string)
		}
		else {
			editor.remove("premiumFeaturesTypesToPosition")
			changed = mainPreferences.getString("premiumFeaturesTypesToPosition", null) != null
		}

		return changed
	}

	private fun loadPremiumFeaturesPreviewOrder(string: String?) {
		premiumFeaturesTypesToPosition.clear()

		if (string != null) {
			val types = string.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

			for (i in types.indices) {
				val type = types[i].toInt()
				premiumFeaturesTypesToPosition.put(type, i)
			}
		}
	}

	fun removeSuggestion(did: Long, suggestion: String?) {
		if (suggestion.isNullOrEmpty()) {
			return
		}

		if (did == 0L) {
			if (pendingSuggestions?.remove(suggestion) == true) {
				val editor = mainPreferences.edit()
				editor.putStringSet("pendingSuggestions", pendingSuggestions)
				editor.commit()

				notificationCenter.postNotificationName(NotificationCenter.newSuggestionsAvailable)
			}
			else {
				return
			}
		}

		val req = TL_help_dismissSuggestion()
		req.suggestion = suggestion

		if (did == 0L) {
			req.peer = TL_inputPeerEmpty()
		}
		else {
			req.peer = getInputPeer(did)
		}

		connectionsManager.sendRequest(req)
	}

	fun updateConfig(config: TL_config) {
		AndroidUtilities.runOnUIThread {
			downloadController.loadAutoDownloadConfig(false)

			loadAppConfig()

			remoteConfigLoaded = true
			maxMegagroupCount = config.megagroup_size_max
			maxGroupCount = config.chat_size_max
			maxEditTime = config.edit_time_limit
			ratingDecay = config.rating_e_decay
			maxRecentGifsCount = config.saved_gifs_limit
			maxRecentStickersCount = config.stickers_recent_limit
			maxFaveStickersCount = config.stickers_faved_limit
			revokeTimeLimit = config.revoke_time_limit
			revokeTimePmLimit = config.revoke_pm_time_limit
			canRevokePmInbox = config.revoke_pm_inbox
			linkPrefix = config.me_url_prefix ?: ""

			val forceTryIpV6 = config.force_try_ipv6

			if (linkPrefix.endsWith("/")) {
				linkPrefix = linkPrefix.substring(0, linkPrefix.length - 1)
			}

			if (linkPrefix.startsWith("https://")) {
				linkPrefix = linkPrefix.substring(8)
			}
			else if (linkPrefix.startsWith("http://")) {
				linkPrefix = linkPrefix.substring(7)
			}

			callReceiveTimeout = config.call_receive_timeout_ms
			callRingTimeout = config.call_ring_timeout_ms
			callConnectTimeout = config.call_connect_timeout_ms
			callPacketTimeout = config.call_packet_timeout_ms
			maxPinnedDialogsCount = config.pinned_dialogs_count_max
			maxFolderPinnedDialogsCount = config.pinned_infolder_count_max
			maxMessageLength = config.message_length_max
			maxCaptionLength = config.caption_length_max
			preloadFeaturedStickers = config.preload_featured_stickers

			if (config.venue_search_username != null) {
				venueSearchBot = config.venue_search_username
			}

			if (config.gif_search_username != null) {
				gifSearchBot = config.gif_search_username
			}

			if (imageSearchBot != null) {
				imageSearchBot = config.img_search_username
			}

			blockedCountry = config.blocked_mode
			dcDomainName = config.dc_txt_domain_name
			webFileDatacenterId = config.webfile_dc_id

			if (config.suggested_lang_code != null) {
				val loadRemote = suggestedLangCode == null || suggestedLangCode != config.suggested_lang_code

				suggestedLangCode = config.suggested_lang_code

				if (loadRemote) {
					LocaleController.getInstance().loadRemoteLanguages(currentAccount)
				}
			}

			// MARK: disabled themes loading as we do not support them
			// Theme.loadRemoteThemes(currentAccount, false)
			// Theme.checkCurrentRemoteTheme(false)

			if (config.static_maps_provider == null) {
				config.static_maps_provider = "ello"
			}

			mapKey = null
			mapProvider = MAP_PROVIDER_ELLO
			availableMapProviders = 0

			FileLog.d("map providers = " + config.static_maps_provider)

			val providers = config.static_maps_provider?.split(",".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()

			providers?.forEachIndexed { index, provider ->
				val mapArgs = provider.split("\\+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

				if (mapArgs.isNotEmpty()) {
					val typeAndKey = mapArgs[0].split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

					if (typeAndKey.isNotEmpty()) {
						when (typeAndKey[0]) {
							"yandex" -> {
								if (index == 0) {
									mapProvider = if (mapArgs.size > 1) {
										MAP_PROVIDER_YANDEX_WITH_ARGS
									}
									else {
										MAP_PROVIDER_YANDEX_NO_ARGS
									}
								}

								availableMapProviders = availableMapProviders or 4
							}

							"google" -> {
								if (index == 0) {
									if (mapArgs.size > 1) {
										mapProvider = MAP_PROVIDER_GOOGLE
									}
								}

								availableMapProviders = availableMapProviders or 1
							}

							"ello", "telegram" -> {
								if (index == 0) {
									mapProvider = MAP_PROVIDER_ELLO
								}

								availableMapProviders = availableMapProviders or 2
							}
						}

						if (typeAndKey.size > 1) {
							mapKey = typeAndKey[1]
						}
					}
				}
			}

			val editor = mainPreferences.edit()
			editor.putBoolean("remoteConfigLoaded", remoteConfigLoaded)
			editor.putInt("maxGroupCount", maxGroupCount)
			editor.putInt("maxMegagroupCount", maxMegagroupCount)
			editor.putInt("maxEditTime", maxEditTime)
			editor.putInt("ratingDecay", ratingDecay)
			editor.putInt("maxRecentGifsCount", maxRecentGifsCount)
			editor.putInt("maxRecentStickersCount", maxRecentStickersCount)
			editor.putInt("maxFaveStickersCount", maxFaveStickersCount)
			editor.putInt("callReceiveTimeout", callReceiveTimeout)
			editor.putInt("callRingTimeout", callRingTimeout)
			editor.putInt("callConnectTimeout", callConnectTimeout)
			editor.putInt("callPacketTimeout", callPacketTimeout)
			editor.putString("linkPrefix", linkPrefix)
			editor.putInt("maxPinnedDialogsCount", maxPinnedDialogsCount)
			editor.putInt("maxFolderPinnedDialogsCount", maxFolderPinnedDialogsCount)
			editor.putInt("maxMessageLength", maxMessageLength)
			editor.putInt("maxCaptionLength", maxCaptionLength)
			editor.putBoolean("preloadFeaturedStickers", preloadFeaturedStickers)
			editor.putInt("revokeTimeLimit", revokeTimeLimit)
			editor.putInt("revokeTimePmLimit", revokeTimePmLimit)
			editor.putInt("mapProvider", mapProvider)

			if (mapKey != null) {
				editor.putString("pk", mapKey)
			}
			else {
				editor.remove("pk")
			}

			editor.putBoolean("canRevokePmInbox", canRevokePmInbox)
			editor.putBoolean("blockedCountry", blockedCountry)
			editor.putString("venueSearchBot", venueSearchBot)
			editor.putString("gifSearchBot", gifSearchBot)
			editor.putString("imageSearchBot", imageSearchBot)
			editor.putString("dcDomainName2", dcDomainName)
			editor.putInt("webFileDatacenterId", webFileDatacenterId)
			editor.putString("suggestedLangCode", suggestedLangCode)
			editor.putBoolean("forceTryIpV6", forceTryIpV6)

			editor.commit()

			connectionsManager.setForceTryIpV6(forceTryIpV6)

			LocaleController.getInstance().checkUpdateForCurrentRemoteLocale(currentAccount, config.lang_pack_version, config.base_lang_pack_version)

			notificationCenter.postNotificationName(NotificationCenter.configLoaded)
		}
	}

	fun addSupportUser() {
		var user = TL_userForeign_old2()
		// user.phone = "333";
		user.id = 333000
		user.first_name = "Ello"
		user.last_name = ""
		user.status = null
		user.photo = TL_userProfilePhotoEmpty()

		putUser(user, true)

		user = TL_userForeign_old2()
		// user.phone = "42777";
		user.id = 777000
		user.verified = true
		user.first_name = "Ello"
		user.last_name = "Notifications"
		user.status = null
		user.photo = TL_userProfilePhotoEmpty()

		putUser(user, true)
	}

	fun getInputUser(user: User?): InputUser {
		if (user == null) {
			return TL_inputUserEmpty()
		}

		val inputUser: InputUser

		if (user.id == userConfig.getClientUserId()) {
			inputUser = TL_inputUserSelf()
		}
		else {
			inputUser = TL_inputUser()
			inputUser.user_id = user.id
			inputUser.access_hash = user.access_hash
		}

		return inputUser
	}

	fun getInputUser(peer: InputPeer?): InputUser {
		if (peer == null) {
			return TL_inputUserEmpty()
		}

		if (peer is TL_inputPeerSelf) {
			return TL_inputUserSelf()
		}

		val inputUser = TL_inputUser()
		inputUser.user_id = peer.user_id
		inputUser.access_hash = peer.access_hash

		return inputUser
	}

	fun getInputUser(userId: Long): InputUser {
		return getInputUser(getUser(userId))
	}

	fun getInputChannel(chatId: Long?): InputChannel {
		return getInputChannel(getChat(chatId))
	}

	fun getInputPeer(peer: Peer?): InputPeer {
		val inputPeer: InputPeer

		if (peer is TL_peerChat) {
			inputPeer = TL_inputPeerChat()
			inputPeer.chat_id = peer.chat_id
		}
		else if (peer is TL_peerChannel) {
			inputPeer = TL_inputPeerChannel()
			inputPeer.channel_id = peer.channel_id

			val chat = getChat(peer.channel_id)

			if (chat != null) {
				inputPeer.access_hash = chat.access_hash
			}
		}
		else {
			inputPeer = TL_inputPeerUser()
			inputPeer.user_id = peer?.user_id ?: 0

			val user = getUser(peer?.user_id)

			if (user != null) {
				inputPeer.access_hash = user.access_hash
			}
		}

		return inputPeer
	}

	fun getInputPeer(id: Long): InputPeer {
		val inputPeer: InputPeer

		if (id < 0) {
			val chat = getChat(-id)

			if (ChatObject.isChannel(chat)) {
				inputPeer = TL_inputPeerChannel()
				inputPeer.channel_id = -id
				inputPeer.access_hash = chat.access_hash
			}
			else {
				inputPeer = TL_inputPeerChat()
				inputPeer.chat_id = -id
			}
		}
		else {
			val user = getUser(id)

			inputPeer = TL_inputPeerUser()
			inputPeer.user_id = id

			if (user != null) {
				inputPeer.access_hash = user.access_hash
			}
		}

		return inputPeer
	}

	fun getPeer(id: Long): Peer {
		val inputPeer: Peer

		if (id < 0) {
			val chat = getChat(-id)

			if (chat is TL_channel || chat is TL_channelForbidden) {
				inputPeer = TL_peerChannel()
				inputPeer.channel_id = -id
			}
			else {
				inputPeer = TL_peerChat()
				inputPeer.chat_id = -id
			}
		}
		else {
			inputPeer = TL_peerUser()
			inputPeer.user_id = id
		}

		return inputPeer
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.fileUploaded -> {
				val location = args[0] as String
				val file = args[1] as InputFile

				if (uploadingAvatar != null && uploadingAvatar == location) {
					val req = TL_photos_uploadProfilePhoto()
					req.file = file
					req.flags = req.flags or 1

					connectionsManager.sendRequest(req) { response, error ->
						if (error == null) {
							var user = getUser(userConfig.getClientUserId())

							if (user == null) {
								user = userConfig.getCurrentUser()
								putUser(user, true)
							}
							else {
								userConfig.setCurrentUser(user)
							}

							if (user == null) {
								return@sendRequest
							}

							val photo = response as? TL_photos_photo
							val sizes = photo?.photo?.sizes
							val smallSize = FileLoader.getClosestPhotoSizeWithSize(sizes, 100)
							val bigSize = FileLoader.getClosestPhotoSizeWithSize(sizes, 1000)

							user.photo = TL_userProfilePhoto()
							user.photo?.photo_id = photo?.photo?.id ?: 0L

							if (smallSize != null) {
								user.photo?.photo_small = smallSize.location
							}

							if (bigSize != null) {
								user.photo?.photo_big = bigSize.location
							}

							messagesStorage.clearUserPhotos(user.id)

							val users = ArrayList<User>()
							users.add(user)

							messagesStorage.putUsersAndChats(users, null, false, true)

							AndroidUtilities.runOnUIThread {
								notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
								notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_AVATAR)
								userConfig.saveConfig(true)
							}
						}
					}
				}
				else if (uploadingWallpaper != null && uploadingWallpaper == location) {
					val req = TL_account_uploadWallPaper()
					req.file = file
					req.mime_type = "image/jpeg"

					val overrideWallpaperInfo = uploadingWallpaperInfo

					val settings = TL_wallPaperSettings()
					settings.blur = overrideWallpaperInfo!!.isBlurred
					settings.motion = overrideWallpaperInfo.isMotion

					req.settings = settings

					connectionsManager.sendRequest(req) { response, _ ->
						val wallPaper = response as WallPaper?
						val path = File(ApplicationLoader.filesDirFixed, overrideWallpaperInfo.originalFileName)

						if (wallPaper != null) {
							runCatching {
								AndroidUtilities.copyFile(path, fileLoader.getPathToAttach(wallPaper.document, true))
							}
						}

						AndroidUtilities.runOnUIThread {
							if (uploadingWallpaper != null && wallPaper != null) {
								wallPaper.settings = settings
								wallPaper.flags = wallPaper.flags or 4

								overrideWallpaperInfo.slug = wallPaper.slug
								overrideWallpaperInfo.saveOverrideWallpaper()

								val wallpapers = ArrayList<WallPaper>()
								wallpapers.add(wallPaper)

								messagesStorage.putWallpapers(wallpapers, 2)

								val image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 320)

								if (image != null) {
									val newKey = image.location.volume_id.toString() + "_" + image.location.local_id + "@100_100"
									val oldKey = Utilities.MD5(path.absolutePath) + "@100_100"

									ImageLocation.getForDocument(image, wallPaper.document)?.let {
										ImageLoader.instance.replaceImageInCache(oldKey, newKey, it, false)
									}
								}

								NotificationCenter.globalInstance.postNotificationName(NotificationCenter.wallpapersNeedReload, wallPaper.slug)
							}
						}
					}
				}
				else {
					val `object` = uploadingThemes[location]
					val themeInfo: ThemeInfo?
					val accent: ThemeAccent?
					val uploadedThumb: InputFile?
					val uploadedFile: InputFile?

					when (`object`) {
						is ThemeInfo -> {
							themeInfo = `object`
							accent = null

							if (location == themeInfo.uploadingThumb) {
								themeInfo.uploadedThumb = file
								themeInfo.uploadingThumb = null
							}
							else if (location == themeInfo.uploadingFile) {
								themeInfo.uploadedFile = file
								themeInfo.uploadingFile = null
							}

							uploadedThumb = themeInfo.uploadedThumb
							uploadedFile = themeInfo.uploadedFile
						}

						is ThemeAccent -> {
							accent = `object`

							if (location == accent.uploadingThumb) {
								accent.uploadedThumb = file
								accent.uploadingThumb = null
							}
							else if (location == accent.uploadingFile) {
								accent.uploadedFile = file
								accent.uploadingFile = null
							}

							themeInfo = accent.parentTheme
							uploadedThumb = accent.uploadedThumb
							uploadedFile = accent.uploadedFile
						}

						else -> {
							themeInfo = null
							accent = null
							uploadedThumb = null
							uploadedFile = null
						}
					}

					uploadingThemes.remove(location)

					if (uploadedFile != null && uploadedThumb != null) {
						val req = TL_account_uploadTheme()
						req.mime_type = "application/x-tgtheme-android"
						req.file_name = "theme.attheme"
						req.file = uploadedFile
						req.file.name = "theme.attheme"
						req.thumb = uploadedThumb
						req.thumb.name = "theme-preview.jpg"
						req.flags = req.flags or 1

						val info: TL_theme?
						val settings: TL_inputThemeSettings?

						if (accent != null) {
							accent.uploadedFile = null
							accent.uploadedThumb = null

							info = accent.info

							settings = TL_inputThemeSettings()
							settings.base_theme = Theme.getBaseThemeByKey(themeInfo!!.name)
							settings.accent_color = accent.accentColor

							if (accent.accentColor2 != 0) {
								settings.flags = settings.flags or 8
								settings.outbox_accent_color = accent.accentColor2
							}

							if (accent.myMessagesAccentColor != 0) {
								settings.message_colors.add(accent.myMessagesAccentColor)
								settings.flags = settings.flags or 1

								if (accent.myMessagesGradientAccentColor1 != 0) {
									settings.message_colors.add(accent.myMessagesGradientAccentColor1)

									if (accent.myMessagesGradientAccentColor2 != 0) {
										settings.message_colors.add(accent.myMessagesGradientAccentColor2)

										if (accent.myMessagesGradientAccentColor3 != 0) {
											settings.message_colors.add(accent.myMessagesGradientAccentColor3)
										}
									}
								}

								settings.message_colors_animated = accent.myMessagesAnimated
							}

							settings.flags = settings.flags or 2
							settings.wallpaper_settings = TL_wallPaperSettings()

							if (!TextUtils.isEmpty(accent.patternSlug)) {
								val inputWallPaperSlug = TL_inputWallPaperSlug()
								inputWallPaperSlug.slug = accent.patternSlug

								settings.wallpaper = inputWallPaperSlug
								settings.wallpaper_settings.intensity = (accent.patternIntensity * 100).toInt()
								settings.wallpaper_settings.flags = settings.wallpaper_settings.flags or 8
							}
							else {
								val inputWallPaperNoFile = TL_inputWallPaperNoFile()
								inputWallPaperNoFile.id = 0
								settings.wallpaper = inputWallPaperNoFile
							}

							settings.wallpaper_settings.motion = accent.patternMotion

							if (accent.backgroundOverrideColor != 0L) {
								settings.wallpaper_settings.background_color = accent.backgroundOverrideColor.toInt()
								settings.wallpaper_settings.flags = settings.wallpaper_settings.flags or 1
							}

							if (accent.backgroundGradientOverrideColor1 != 0L) {
								settings.wallpaper_settings.second_background_color = accent.backgroundGradientOverrideColor1.toInt()
								settings.wallpaper_settings.flags = settings.wallpaper_settings.flags or 16
								settings.wallpaper_settings.rotation = AndroidUtilities.getWallpaperRotation(accent.backgroundRotation, true)
							}

							if (accent.backgroundGradientOverrideColor2 != 0L) {
								settings.wallpaper_settings.third_background_color = accent.backgroundGradientOverrideColor2.toInt()
								settings.wallpaper_settings.flags = settings.wallpaper_settings.flags or 32
							}

							if (accent.backgroundGradientOverrideColor3 != 0L) {
								settings.wallpaper_settings.fourth_background_color = accent.backgroundGradientOverrideColor3.toInt()
								settings.wallpaper_settings.flags = settings.wallpaper_settings.flags or 64
							}
						}
						else {
							themeInfo?.uploadedFile = null
							themeInfo?.uploadedThumb = null
							info = themeInfo?.info
							settings = null
						}

						connectionsManager.sendRequest(req) { response, _ ->
							val title = info?.title ?: themeInfo?.getName() ?: ""
							val index = title.lastIndexOf(".attheme")
							val n = if (index > 0) title.substring(0, index) else title

							if (response != null) {
								val document = response as Document

								val inputDocument = TL_inputDocument()
								inputDocument.access_hash = document.access_hash
								inputDocument.id = document.id
								inputDocument.file_reference = document.file_reference

								if (info == null || !info.creator) {
									val req2 = TL_account_createTheme()
									req2.document = inputDocument
									req2.flags = req2.flags or 4
									req2.slug = info?.slug ?: ""
									req2.title = n

									if (settings != null) {
										req2.settings = settings
										req2.flags = req2.flags or 8
									}

									connectionsManager.sendRequest(req2) { response1, _ ->
										AndroidUtilities.runOnUIThread {
											if (response1 is TL_theme) {
												Theme.setThemeUploadInfo(themeInfo, accent, response1 as TL_theme?, currentAccount, false)
												installTheme(themeInfo, accent, themeInfo === Theme.getCurrentNightTheme())
												notificationCenter.postNotificationName(NotificationCenter.themeUploadedToServer, themeInfo, accent)
											}
											else {
												notificationCenter.postNotificationName(NotificationCenter.themeUploadError, themeInfo, accent)
											}
										}
									}
								}
								else {
									val req2 = TL_account_updateTheme()

									val inputTheme = TL_inputTheme()
									inputTheme.id = info.id
									inputTheme.access_hash = info.access_hash

									req2.theme = inputTheme
									req2.slug = info.slug
									req2.flags = req2.flags or 1
									req2.title = n
									req2.flags = req2.flags or 2
									req2.document = inputDocument
									req2.flags = req2.flags or 4

									if (settings != null) {
										req2.settings = settings
										req2.flags = req2.flags or 8
									}

									req2.format = "android"

									connectionsManager.sendRequest(req2) { response1, _ ->
										AndroidUtilities.runOnUIThread {
											if (response1 is TL_theme) {
												Theme.setThemeUploadInfo(themeInfo, accent, response1 as TL_theme?, currentAccount, false)
												notificationCenter.postNotificationName(NotificationCenter.themeUploadedToServer, themeInfo, accent)
											}
											else {
												notificationCenter.postNotificationName(NotificationCenter.themeUploadError, themeInfo, accent)
											}
										}
									}
								}
							}
							else {
								AndroidUtilities.runOnUIThread {
									notificationCenter.postNotificationName(NotificationCenter.themeUploadError, themeInfo, accent)
								}
							}
						}
					}
				}
			}

			NotificationCenter.fileUploadFailed -> {
				val location = args[0] as String

				if (uploadingAvatar != null && uploadingAvatar == location) {
					uploadingAvatar = null
				}
				else if (uploadingWallpaper != null && uploadingWallpaper == location) {
					uploadingWallpaper = null
					uploadingWallpaperInfo = null
				}
				else {
					val `object` = uploadingThemes.remove(location)

					if (`object` is ThemeInfo) {
						`object`.uploadedFile = null
						`object`.uploadedThumb = null
						notificationCenter.postNotificationName(NotificationCenter.themeUploadError, `object`, null)
					}
					else if (`object` is ThemeAccent) {
						`object`.uploadingThumb = null
						notificationCenter.postNotificationName(NotificationCenter.themeUploadError, `object`.parentTheme, `object`)
					}
				}
			}

			NotificationCenter.messageReceivedByServer -> {
				val scheduled = args[6] as Boolean

				if (scheduled) {
					return
				}

				val msgId = args[0] as Int
				val newMsgId = args[1] as Int
				val did = args[3] as Long
				var obj = dialogMessage[did]

				if (obj != null && (obj.id == msgId || obj.messageOwner?.local_id == msgId)) {
					obj.messageOwner?.id = newMsgId
					obj.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SENT
				}

				val dialog = dialogs_dict[did]

				if (dialog != null && dialog.top_message == msgId) {
					dialog.top_message = newMsgId
					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
				}

				obj = dialogMessagesByIds[msgId]

				if (obj != null) {
					dialogMessagesByIds.remove(msgId)
					dialogMessagesByIds.put(newMsgId, obj)
				}

				if (DialogObject.isChatDialog(did)) {
					val chatFull = fullChats[-did]
					val chat = getChat(-did)

					if (chat != null && !ChatObject.hasAdminRights(chat) && chatFull != null && chatFull.slowmode_seconds != 0) {
						chatFull.slowmode_next_send_date = connectionsManager.currentTime + chatFull.slowmode_seconds
						chatFull.flags = chatFull.flags or 262144

						messagesStorage.updateChatInfo(chatFull, false)
					}
				}
			}

			NotificationCenter.updateMessageMedia -> {
				val message = args[0] as Message

				if (message.peer_id?.channel_id == 0L) {
					val existMessageObject = dialogMessagesByIds[message.id]

					if (existMessageObject != null) {
						val media = MessageObject.getMedia(message)

						existMessageObject.messageOwner?.media = media

						if (media != null) {
							if (media.ttl_seconds != 0 && (media.photo is TL_photoEmpty || media.document is TL_documentEmpty)) {
								existMessageObject.setType()
								notificationCenter.postNotificationName(NotificationCenter.notificationsSettingsUpdated)
							}
						}
					}
				}
			}
		}
	}

	fun cleanup() {
		mainScope.coroutineContext.cancelChildren()
		ioScope.coroutineContext.cancelChildren()

		contactsController.cleanup()
		walletHelper.cleanup()

		messagesStorage.deleteFeedCache()

		MediaController.getInstance().cleanup()

		notificationsController.cleanup()
		sendMessagesHelper.cleanup()
		secretChatHelper.cleanup()
		locationController.cleanup()
		mediaDataController.cleanup()

		showFiltersTooltip = false

		DialogsActivity.dialogsLoaded[currentAccount] = false

		var editor = notificationsPreferences.edit()
		editor.clear().commit()

		editor = emojiPreferences.edit()
		editor.putLong("lastGifLoadTime", 0).putLong("lastStickersLoadTime", 0).putLong("lastStickersLoadTimeMask", 0).putLong("lastStickersLoadTimeFavs", 0).commit()

		editor = mainPreferences.edit()
		editor.remove("archivehint").remove("proximityhint").remove("archivehint_l").remove("gifhint").remove("reminderhint").remove("soundHint").remove("dcDomainName2").remove("webFileDatacenterId").remove("themehint").remove("showFiltersTooltip").commit()
		editor.remove(FeedFragment.LAST_READ_ID).remove(FeedFragment.FEED_POSITION).remove(FeedFragment.FEED_OFFSET).remove(FeedFragment.CURRENT_PAGE).commit()

		val preferences = ApplicationLoader.applicationContext.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE)
		var widgetEditor: SharedPreferences.Editor? = null
		var appWidgetManager: AppWidgetManager? = null
		var chatsWidgets: ArrayList<Int>? = null
		var contactsWidgets: ArrayList<Int>? = null
		val values = preferences.all

		for ((key, value1) in values) {
			if (key.startsWith("account")) {
				val value = value1 as Int

				if (value == currentAccount) {
					val widgetId = Utilities.parseInt(key)

					if (widgetEditor == null) {
						widgetEditor = preferences.edit()
						appWidgetManager = AppWidgetManager.getInstance(ApplicationLoader.applicationContext)
					}

					widgetEditor?.putBoolean("deleted$widgetId", true)

					if (preferences.getInt("type$widgetId", 0) == EditWidgetActivity.TYPE_CHATS) {
						if (chatsWidgets == null) {
							chatsWidgets = ArrayList()
						}

						chatsWidgets.add(widgetId)
					}
					else {
						if (contactsWidgets == null) {
							contactsWidgets = ArrayList()
						}

						contactsWidgets.add(widgetId)
					}
				}
			}
		}

		widgetEditor?.commit()

		if (chatsWidgets != null) {
			for (widget in chatsWidgets) {
				ChatsWidgetProvider.updateWidget(ApplicationLoader.applicationContext, appWidgetManager, widget)
			}
		}

		if (contactsWidgets != null) {
			for (widget in contactsWidgets) {
				ContactsWidgetProvider.updateWidget(ApplicationLoader.applicationContext, appWidgetManager, widget)
			}
		}

		lastScheduledServerQueryTime.clear()
		lastServerQueryTime.clear()
		reloadingWebpages.clear()
		reloadingWebpagesPending.clear()
		reloadingScheduledWebpages.clear()
		reloadingScheduledWebpagesPending.clear()
		sponsoredMessages.clear()
		sendAsPeers.clear()
		dialogs_dict.clear()
		dialogs_read_inbox_max.clear()
		loadingPinnedDialogs.clear()
		dialogs_read_outbox_max.clear()
		exportedChats.clear()
		fullUsers.clear()
		fullChats.clear()
		activeVoiceChatsMap.clear()
		loadingGroupCalls.clear()
		groupCallsByChatId.clear()
		dialogsByFolder.clear()

		unreadUnmutedDialogs = 0

		joiningToChannels.clear()
		migratedChats.clear()
		channelViewsToSend.clear()
		pollsToCheck.clear()
		pollsToCheckSize = 0
		dialogsServerOnly.clear()
		dialogsForward.clear()
		allDialogs.clear()

		dialogsLoadedTillDate = Int.MAX_VALUE

		dialogsCanAddUsers.clear()
		dialogsMyChannels.clear()
		dialogsMyGroups.clear()
		dialogsChannelsOnly.clear()
		dialogsGroupsOnly.clear()
		dialogsUsersOnly.clear()
		dialogsForBlock.clear()
		dialogMessagesByIds.clear()
		dialogMessagesByRandomIds.clear()
		channelAdmins.clear()
		loadingChannelAdmins.clear()
		users.clear()
		objectsByUsernames.clear()
		chats.clear()
		dialogMessage.clear()
		deletedHistory.clear()
		printingUsers.clear()
		printingStrings.clear()
		printingStringsTypes.clear()
		onlinePrivacy.clear()
		loadingPeerSettings.clear()
		deletingDialogs.clear()
		clearingHistoryDialogs.clear()

		lastPrintingStringCount = 0

		selectedDialogFilter[0] = null
		selectedDialogFilter[1] = null

		dialogFilters.clear()
		dialogFiltersById.clear()

		loadingSuggestedFilters = false
		loadingRemoteFilters = false

		suggestedFilters.clear()

		gettingAppChangelog = false
		dialogFiltersLoaded = false
		ignoreSetOnline = false

		Utilities.stageQueue.postRunnable {
			readTasks.clear()
			readTasksMap.clear()
			repliesReadTasks.clear()
			threadsReadTasksMap.clear()
			updatesQueueSeq.clear()
			updatesQueuePts.clear()
			updatesQueueQts.clear()
			gettingUnknownChannels.clear()
			gettingUnknownDialogs.clear()

			updatesStartWaitTimeSeq = 0
			updatesStartWaitTimePts = 0
			updatesStartWaitTimeQts = 0

			createdDialogIds.clear()
			createdScheduledDialogIds.clear()

			gettingDifference = false
			resetDialogsPinned = null
			resetDialogsAll = null
		}

		createdDialogMainThreadIds.clear()
		visibleDialogMainThreadIds.clear()
		visibleScheduledDialogMainThreadIds.clear()
		blockedPeers.clear()

		sendingTypings.forEach {
			it?.clear()
		}

		loadingFullUsers.clear()
		loadedFullUsers.clear()
		reloadingMessages.clear()
		loadingFullChats.clear()
		loadingFullParticipants.clear()
		loadedFullParticipants.clear()
		loadedFullChats.clear()

		dialogsLoaded = false

		nextDialogsCacheOffset.clear()
		loadingDialogs.clear()
		dialogsEndReached.clear()
		serverDialogsEndReached.clear()

		loadingAppConfig = false
		checkingTosUpdate = false
		nextTosCheckTime = 0
		nextPromoInfoCheckTime = 0
		checkingPromoInfo = false
		loadingUnreadDialogs = false
		currentDeletingTaskTime = 0
		currentDeletingTaskMids = null
		currentDeletingTaskMediaMids = null
		gettingNewDeleteTask = false
		loadingBlockedPeers = false
		totalBlockedCount = -1
		blockedEndReached = false
		firstGettingTask = false
		updatingState = false
		resettingDialogs = false
		lastStatusUpdateTime = 0
		offlineSent = false
		registeringForPush = false
		getDifferenceFirstSync = true
		uploadingAvatar = null
		uploadingWallpaper = null
		uploadingWallpaperInfo = null

		uploadingThemes.clear()
		gettingChatInviters.clear()
		statusRequest = 0
		statusSettingState = 0

		Utilities.stageQueue.postRunnable {
			connectionsManager.setIsUpdating(false)
			updatesQueueChannels.clear()
			updatesStartWaitTimeChannels.clear()
			gettingDifferenceChannels.clear()
			channelsPts.clear()
			shortPollChannels.clear()
			needShortPollChannels.clear()
			shortPollOnlines.clear()
			needShortPollOnlines.clear()
		}

		if (currentDeleteTaskRunnable != null) {
			Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable)
			currentDeleteTaskRunnable = null
		}

		addSupportUser()

		AndroidUtilities.runOnUIThread {
			notificationCenter.postNotificationName(NotificationCenter.suggestedFiltersLoaded)
			notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)
			notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
		}
	}

	fun isChatNoForwards(chat: Chat?): Boolean {
		if (chat == null) {
			return false
		}

		val migratedTo = getChat(chat.migrated_to?.channel_id)

		if (migratedTo != null) {
			return migratedTo.noforwards
		}

		if (ChatObject.isPaidChannel(chat)) {
			return true
		}

		return chat.noforwards
	}

	fun isChatNoForwards(chatId: Long?): Boolean {
		return if (chatId == null) {
			true
		}
		else {
			isChatNoForwards(getChat(chatId))
		}
	}

	fun getUser(id: Long?): User? {
		if (id == null) {
			return null
		}

		return if (id == 0L) {
			userConfig.getCurrentUser()
		}
		else {
			users[id]
		}
	}

	fun getUserOrChat(username: String?): TLObject? {
		if (username.isNullOrEmpty()) {
			return null
		}

		return objectsByUsernames[username.lowercase()]
	}

	fun getChat(id: Long?): Chat? {
		if (id == null) {
			return null
		}

		return chats[id]
	}

	fun getEncryptedChat(id: Int): EncryptedChat? {
		return encryptedChats[id]
	}

	fun getEncryptedChatDB(chatId: Int, created: Boolean): EncryptedChat? {
		var chat = encryptedChats[chatId]

		if (chat == null || created && (chat is TL_encryptedChatWaiting || chat is TL_encryptedChatRequested)) {
			val countDownLatch = CountDownLatch(1)

			val result = ArrayList<TLObject>()

			messagesStorage.getEncryptedChat(chatId.toLong(), countDownLatch, result)

			try {
				countDownLatch.await()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (result.size == 2) {
				chat = result[0] as EncryptedChat

				val user = result[1] as User

				putEncryptedChat(chat, false)
				putUser(user, true)
			}
		}

		return chat
	}

	fun isDialogVisible(dialogId: Long, scheduled: Boolean): Boolean {
		return if (scheduled) visibleScheduledDialogMainThreadIds.contains(dialogId) else visibleDialogMainThreadIds.contains(dialogId)
	}

	fun setLastVisibleDialogId(dialogId: Long, scheduled: Boolean, set: Boolean) {
		val arrayList = if (scheduled) visibleScheduledDialogMainThreadIds else visibleDialogMainThreadIds

		if (set) {
			if (arrayList.contains(dialogId)) {
				return
			}

			arrayList.add(dialogId)
		}
		else {
			arrayList.remove(dialogId)
		}
	}

	fun setLastCreatedDialogId(dialogId: Long, scheduled: Boolean, set: Boolean) {
		if (!scheduled) {
			val arrayList = createdDialogMainThreadIds

			if (set) {
				if (arrayList.contains(dialogId)) {
					return
				}

				arrayList.add(dialogId)
			}
			else {
				arrayList.remove(dialogId)

				pollsToCheck[dialogId]?.valueIterator()?.forEach {
					it.pollVisibleOnScreen = false
				}
			}
		}

		Utilities.stageQueue.postRunnable {
			val arrayList2 = if (scheduled) createdScheduledDialogIds else createdDialogIds

			if (set) {
				if (arrayList2.contains(dialogId)) {
					return@postRunnable
				}

				arrayList2.add(dialogId)
			}
			else {
				arrayList2.remove(dialogId)
			}
		}
	}

	fun getExportedInvite(chatId: Long): TL_chatInviteExported? {
		return exportedChats[chatId]
	}

	fun putUser(user: User?, fromCache: Boolean): Boolean {
		@Suppress("NAME_SHADOWING") var fromCache = fromCache

		if (user == null) {
			return false
		}

		fromCache = fromCache && user.id / 1000 != 333L && user.id != BuildConfig.NOTIFICATIONS_BOT_ID

		val oldUser = users[user.id]

		if (oldUser === user) {
			return false
		}

		oldUser?.username?.takeIf { it.isNotEmpty() }?.let {
			objectsByUsernames.remove(it.lowercase())
		}

		user.username?.takeIf { it.isNotEmpty() }?.let {
			objectsByUsernames[it.lowercase()] = user
		}

		updateEmojiStatusUntilUpdate(user.id, user.emoji_status)

		if (user.min) {
			if (oldUser != null) {
				if (!fromCache) {
					if (user.bot) {
						if (user.username != null) {
							oldUser.username = user.username
							oldUser.flags = oldUser.flags or 8
						}
						else {
							oldUser.flags = oldUser.flags and 8.inv()
							oldUser.username = null
						}
					}

					if (user.apply_min_photo) {
						if (user.photo != null) {
							oldUser.photo = user.photo
							oldUser.flags = oldUser.flags or 32
						}
						else {
							oldUser.flags = oldUser.flags and 32.inv()
							oldUser.photo = null
						}
					}
				}
			}
			else {
				users[user.id] = user
			}
		}
		else {
			if (!fromCache) {
				val existingUser = users[user.id]

				if (existingUser == null || !existingUser.contact || user.contact) {
					users[user.id] = user
				}

				if (user.id == userConfig.getClientUserId()) {
					userConfig.setCurrentUser(user)
					userConfig.saveConfig(true)
				}

				return oldUser != null && user.status != null && oldUser.status != null && user.status!!.expires != oldUser.status!!.expires
			}
			else if (oldUser == null) {
				users[user.id] = user
			}
			else if (oldUser.min) {
				if (oldUser.bot) {
					if (oldUser.username != null) {
						user.username = oldUser.username
						user.flags = user.flags or 8
					}
					else {
						user.flags = user.flags and 8.inv()
						user.username = null
					}
				}
				if (oldUser.apply_min_photo) {
					if (oldUser.photo != null) {
						user.photo = oldUser.photo
						user.flags = user.flags or 32
					}
					else {
						user.flags = user.flags and 32.inv()
						user.photo = null
					}
				}

				users[user.id] = user
			}
		}

		return false
	}

	fun putUsers(users: List<User>?, fromCache: Boolean) {
		if (users.isNullOrEmpty()) {
			return
		}

		var updateStatus = false

		for (user in users) {
			if (putUser(user, fromCache)) {
				updateStatus = true
			}
		}

		if (updateStatus) {
			AndroidUtilities.runOnUIThread {
				notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS)
			}
		}
	}

	fun putChat(chat: Chat?, fromCache: Boolean) {
		if (chat == null) {
			return
		}

		val oldChat = chats[chat.id]

		if (oldChat === chat) {
			return
		}

		oldChat?.username?.takeIf { it.isNotEmpty() }?.let {
			objectsByUsernames.remove(it.lowercase())
		}

		chat.username?.takeIf { it.isNotEmpty() }?.let {
			objectsByUsernames[it.lowercase()] = chat
		}

		if (chat.min) {
			if (oldChat != null) {
				if (!fromCache) {
					oldChat.title = chat.title
					oldChat.photo = chat.photo
					oldChat.broadcast = chat.broadcast
					oldChat.verified = chat.verified
					oldChat.megagroup = chat.megagroup
					oldChat.call_not_empty = chat.call_not_empty
					oldChat.call_active = chat.call_active

					if (chat.default_banned_rights != null) {
						oldChat.default_banned_rights = chat.default_banned_rights
						oldChat.flags = oldChat.flags or 262144
					}

					if (chat.admin_rights != null) {
						oldChat.admin_rights = chat.admin_rights
						oldChat.flags = oldChat.flags or 16384
					}

					if (chat.banned_rights != null) {
						oldChat.banned_rights = chat.banned_rights
						oldChat.flags = oldChat.flags or 32768
					}

					if (chat.username != null) {
						oldChat.username = chat.username
						oldChat.flags = oldChat.flags or 64
					}
					else {
						oldChat.flags = oldChat.flags and 64.inv()
						oldChat.username = null
					}

					if (chat.participants_count != 0) {
						oldChat.participants_count = chat.participants_count
					}

					addOrRemoveActiveVoiceChat(oldChat)
				}
			}
			else {
				chats[chat.id] = chat
				addOrRemoveActiveVoiceChat(chat)
			}
		}
		else {
			if (!fromCache) {
				if (oldChat != null) {
					if (chat.version != oldChat.version) {
						loadedFullChats.remove(chat.id)
					}

					if (oldChat.participants_count != 0 && chat.participants_count == 0) {
						chat.participants_count = oldChat.participants_count
						chat.flags = chat.flags or 131072
					}

					val oldFlags = if (oldChat.banned_rights != null) oldChat.banned_rights.flags else 0
					val newFlags = if (chat.banned_rights != null) chat.banned_rights.flags else 0
					val oldFlags2 = if (oldChat.default_banned_rights != null) oldChat.default_banned_rights.flags else 0
					val newFlags2 = if (chat.default_banned_rights != null) chat.default_banned_rights.flags else 0

					oldChat.default_banned_rights = chat.default_banned_rights

					if (oldChat.default_banned_rights == null) {
						oldChat.flags = oldChat.flags and 262144.inv()
					}
					else {
						oldChat.flags = oldChat.flags or 262144
					}

					oldChat.banned_rights = chat.banned_rights
					if (oldChat.banned_rights == null) {
						oldChat.flags = oldChat.flags and 32768.inv()
					}
					else {
						oldChat.flags = oldChat.flags or 32768
					}

					oldChat.admin_rights = chat.admin_rights

					if (oldChat.admin_rights == null) {
						oldChat.flags = oldChat.flags and 16384.inv()
					}
					else {
						oldChat.flags = oldChat.flags or 16384
					}

					if (oldFlags != newFlags || oldFlags2 != newFlags2) {
						AndroidUtilities.runOnUIThread {
							notificationCenter.postNotificationName(NotificationCenter.channelRightsUpdated, chat)
						}
					}
				}

				chats[chat.id] = chat
			}
			else if (oldChat == null) {
				chats[chat.id] = chat
			}
			else if (oldChat.min) {
				chat.title = oldChat.title
				chat.photo = oldChat.photo
				chat.broadcast = oldChat.broadcast
				chat.verified = oldChat.verified
				chat.megagroup = oldChat.megagroup

				if (oldChat.default_banned_rights != null) {
					chat.default_banned_rights = oldChat.default_banned_rights
					chat.flags = chat.flags or 262144
				}

				if (oldChat.admin_rights != null) {
					chat.admin_rights = oldChat.admin_rights
					chat.flags = chat.flags or 16384
				}

				if (oldChat.banned_rights != null) {
					chat.banned_rights = oldChat.banned_rights
					chat.flags = chat.flags or 32768
				}

				if (oldChat.username != null) {
					chat.username = oldChat.username
					chat.flags = chat.flags or 64
				}
				else {
					chat.flags = chat.flags and 64.inv()
					chat.username = null
				}

				if (oldChat.participants_count != 0 && chat.participants_count == 0) {
					chat.participants_count = oldChat.participants_count
					chat.flags = chat.flags or 131072
				}

				chats[chat.id] = chat
			}

			addOrRemoveActiveVoiceChat(chat)
		}
	}

	fun putChats(chats: List<Chat>?, fromCache: Boolean) {
		if (chats.isNullOrEmpty()) {
			return
		}

		for (chat in chats) {
			putChat(chat, fromCache)
		}
	}

	private fun addOrRemoveActiveVoiceChat(chat: Chat) {
		if (Thread.currentThread() !== Looper.getMainLooper().thread) {
			AndroidUtilities.runOnUIThread { addOrRemoveActiveVoiceChatInternal(chat) }
		}
		else {
			addOrRemoveActiveVoiceChatInternal(chat)
		}
	}

	private fun addOrRemoveActiveVoiceChatInternal(chat: Chat) {
		val currentChat = activeVoiceChatsMap[chat.id]

		if (chat.call_active && chat.call_not_empty && chat.migrated_to == null && !ChatObject.isNotInChat(chat)) {
			if (currentChat != null) {
				return
			}

			activeVoiceChatsMap[chat.id] = chat

			notificationCenter.postNotificationName(NotificationCenter.activeGroupCallsUpdated)
		}
		else {
			if (currentChat == null) {
				return
			}

			activeVoiceChatsMap.remove(chat.id)

			notificationCenter.postNotificationName(NotificationCenter.activeGroupCallsUpdated)
		}
	}

	val activeGroupCalls: List<Long>
		get() = activeVoiceChatsMap.keys.toList()

	fun setReferer(referer: String?) {
		if (referer.isNullOrEmpty()) {
			return
		}

		installReferer = referer

		mainPreferences.edit().putString("installReferer", referer).commit()
	}

	fun putEncryptedChat(encryptedChat: EncryptedChat?, fromCache: Boolean) {
		if (encryptedChat == null) {
			return
		}

		if (fromCache) {
			encryptedChats.putIfAbsent(encryptedChat.id, encryptedChat)
		}
		else {
			encryptedChats[encryptedChat.id] = encryptedChat
		}
	}

	fun putEncryptedChats(encryptedChats: List<EncryptedChat?>?, fromCache: Boolean) {
		if (encryptedChats.isNullOrEmpty()) {
			return
		}

		for (encryptedChat in encryptedChats) {
			putEncryptedChat(encryptedChat, fromCache)
		}
	}

	fun getUserFull(uid: Long?): UserFull? {
		if (uid == null) {
			return null
		}

		return fullUsers[uid]
	}

	fun getChatFull(chatId: Long?): ChatFull? {
		if (chatId == null) {
			return null
		}

		return fullChats[chatId]
	}

	fun putGroupCall(chatId: Long, call: ChatObject.Call) {
		groupCalls.put(call.call!!.id, call)
		groupCallsByChatId.put(chatId, call)

		val chatFull = getChatFull(chatId)
		chatFull?.call = call.inputGroupCall

		notificationCenter.postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.call?.id ?: 0L, false)

		loadFullChat(chatId, 0, true)
	}

	fun getGroupCall(chatId: Long?, load: Boolean): ChatObject.Call? {
		return getGroupCall(chatId, load, null)
	}

	fun getGroupCall(chatId: Long?, load: Boolean, onLoad: Runnable?): ChatObject.Call? {
		if (chatId == null) {
			return null
		}

		val chatFull = getChatFull(chatId)

		if (chatFull?.call == null) {
			return null
		}

		val result = groupCalls[chatFull.call.id]

		if (result == null && load && !loadingGroupCalls.contains(chatId)) {
			loadingGroupCalls.add(chatId)

			if (chatFull.call != null) {
				val req = TL_phone_getGroupCall()
				req.call = chatFull.call
				req.limit = 20

				connectionsManager.sendRequest(req) { response, _ ->
					AndroidUtilities.runOnUIThread {
						if (response != null) {
							val groupCall = response as TL_phone_groupCall

							putUsers(groupCall.users, false)
							putChats(groupCall.chats, false)

							val call = ChatObject.Call()
							call.setCall(accountInstance, chatId, groupCall)

							groupCalls.put(groupCall.call.id, call)
							groupCallsByChatId.put(chatId, call)

							notificationCenter.postNotificationName(NotificationCenter.groupCallUpdated, chatId, groupCall.call.id, false)

							onLoad?.run()
						}

						loadingGroupCalls.remove(chatId)
					}
				}
			}
		}

		if (result != null && result.call is TL_groupCallDiscarded) {
			return null
		}

		return result
	}

	fun cancelLoadFullUser(userId: Long) {
		loadingFullUsers.remove(userId)
	}

	fun cancelLoadFullChat(chatId: Long) {
		loadingFullChats.remove(chatId)
	}

	fun clearFullUsers() {
		loadedFullUsers.clear()
		loadedFullChats.clear()
	}

	private fun reloadDialogsReadValue(dialogs: List<Dialog>?, did: Long) {
		if (did == 0L && dialogs.isNullOrEmpty()) {
			return
		}

		val req = TL_messages_getPeerDialogs()

		if (dialogs != null) {
			for (a in dialogs.indices) {
				val inputPeer = getInputPeer(dialogs[a].id)

				if (inputPeer is TL_inputPeerChannel && inputPeer.access_hash == 0L) {
					continue
				}

				val inputDialogPeer = TL_inputDialogPeer()
				inputDialogPeer.peer = inputPeer

				req.peers.add(inputDialogPeer)
			}
		}
		else {
			val inputPeer = getInputPeer(did)

			if (inputPeer is TL_inputPeerChannel && inputPeer.access_hash == 0L) {
				return
			}

			val inputDialogPeer = TL_inputDialogPeer()
			inputDialogPeer.peer = inputPeer

			req.peers.add(inputDialogPeer)
		}

		if (req.peers.isEmpty()) {
			return
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_messages_peerDialogs) {
				val updateArray = mutableListOf<Update>()

				for (dialog in response.dialogs) {
					DialogObject.initDialog(dialog)

					var value = dialogs_read_inbox_max[dialog.id]

					if (value == null) {
						value = 0
					}

					dialogs_read_inbox_max[dialog.id] = max(dialog.read_inbox_max_id, value)

					if (value == 0) {
						if (dialog.peer.channel_id != 0L) {
							val update = TL_updateReadChannelInbox()
							update.channel_id = dialog.peer.channel_id
							update.max_id = dialog.read_inbox_max_id
							update.still_unread_count = dialog.unread_count
							updateArray.add(update)
						}
						else {
							val update = TL_updateReadHistoryInbox()
							update.peer = dialog.peer
							update.max_id = dialog.read_inbox_max_id
							updateArray.add(update)
						}
					}

					value = dialogs_read_outbox_max[dialog.id]

					if (value == null) {
						value = 0
					}

					dialogs_read_outbox_max[dialog.id] = max(dialog.read_outbox_max_id, value)

					if (dialog.read_outbox_max_id > value) {
						if (dialog.peer.channel_id != 0L) {
							val update = TL_updateReadChannelOutbox()
							update.channel_id = dialog.peer.channel_id
							update.max_id = dialog.read_outbox_max_id

							updateArray.add(update)
						}
						else {
							val update = TL_updateReadHistoryOutbox()
							update.peer = dialog.peer
							update.max_id = dialog.read_outbox_max_id

							updateArray.add(update)
						}
					}
				}

				if (updateArray.isNotEmpty()) {
					processUpdateArray(updateArray, null, null, false, 0)
				}
			}
		}
	}

	fun getAdminInChannel(uid: Long, chatId: Long): ChannelParticipant? {
		val array = channelAdmins[chatId] ?: return null
		return array[uid]
	}

	fun getAdminRank(chatId: Long?, uid: Long): String? {
		if (chatId == null) {
			return null
		}

		val array = channelAdmins[chatId] ?: return null
		val participant = array[uid] ?: return null
		return participant.rank ?: ""
	}

	fun isChannelAdminsLoaded(chatId: Long): Boolean {
		return channelAdmins[chatId] != null
	}

	fun loadChannelAdmins(chatId: Long, cache: Boolean) {
		val loadTime = loadingChannelAdmins[chatId]

		if (SystemClock.elapsedRealtime() - loadTime < 60) {
			return
		}

		loadingChannelAdmins.put(chatId, (SystemClock.elapsedRealtime() / 1000).toInt())

		if (cache) {
			messagesStorage.loadChannelAdmins(chatId)
		}
		else {
			val req = TL_channels_getParticipants()
			req.channel = getInputChannel(chatId)
			req.limit = 100
			req.filter = TL_channelParticipantsAdmins()

			connectionsManager.sendRequest(req) { response, error ->
				if (response is TL_channels_channelParticipants) {
					processLoadedAdminsResponse(chatId, response)
				}
				else if (error != null) {
					if (error.text?.lowercase()?.contains("blocked") == true) {
						AndroidUtilities.runOnUIThread {
							notificationCenter.postNotificationName(NotificationCenter.chatIsBlocked, chatId, NotificationCenter.ERROR_CHAT_BLOCKED)
						}
					}
				}
			}
		}
	}

	fun processLoadedAdminsResponse(chatId: Long, participants: TL_channels_channelParticipants) {
		val array1 = LongSparseArray<ChannelParticipant>(participants.participants.size)

		for (participant in participants.participants) {
			array1.put(MessageObject.getPeerId(participant.peer), participant)
		}

		processLoadedChannelAdmins(array1, chatId, false)
	}

	fun processLoadedChannelAdmins(array: LongSparseArray<ChannelParticipant>, chatId: Long, cache: Boolean) {
		if (!cache) {
			messagesStorage.putChannelAdmins(chatId, array)
		}

		AndroidUtilities.runOnUIThread {
			channelAdmins.put(chatId, array)

			if (cache) {
				loadingChannelAdmins.delete(chatId)
				loadChannelAdmins(chatId, false)
				notificationCenter.postNotificationName(NotificationCenter.didLoadChatAdmins, chatId)
			}
		}
	}

	fun loadFullChat(chatId: Long, classGuid: Int, force: Boolean) {
		val loaded = loadedFullChats.contains(chatId)

		if (loadingFullChats.contains(chatId) || (!force && loaded)) {
			return
		}

		loadingFullChats.add(chatId)

		val request: TLObject
		val dialogId = -chatId
		val chat = getChat(chatId)

		if (ChatObject.isChannel(chat)) {
			val req = TL_channels_getFullChannel()
			req.channel = getInputChannel(chat)

			request = req

			loadChannelAdmins(chatId, !loaded)
		}
		else {
			val req = TL_messages_getFullChat()
			req.chat_id = chatId

			request = req

			if (dialogs_read_inbox_max[dialogId] == null || dialogs_read_outbox_max[dialogId] == null) {
				reloadDialogsReadValue(null, dialogId)
			}
		}

		val reqId = connectionsManager.sendRequest(request) { response, error ->
			var ok = false

			if (error == null) {
				if (response is TL_messages_chatFull) {
					ok = true

					messagesStorage.putUsersAndChats(response.users, response.chats, true, true)
					messagesStorage.updateChatInfo(response.fullChat, false)

					if (ChatObject.isChannel(chat)) {
						var value = dialogs_read_inbox_max[dialogId]

						if (value == null) {
							value = messagesStorage.getDialogReadMax(false, dialogId)
						}

						val fullChat = response.fullChat

						if (fullChat != null) {
							dialogs_read_inbox_max[dialogId] = max(fullChat.read_inbox_max_id, value)

							if (fullChat.read_inbox_max_id > value) {
								val update = TL_updateReadChannelInbox()
								update.channel_id = chatId
								update.max_id = fullChat.read_inbox_max_id
								update.still_unread_count = fullChat.unread_count

								processUpdateArray(listOf(update), null, null, false, 0)
							}

							value = dialogs_read_outbox_max[dialogId]

							if (value == null) {
								value = messagesStorage.getDialogReadMax(true, dialogId)
							}

							dialogs_read_outbox_max[dialogId] = max(fullChat.read_outbox_max_id, value)

							if (fullChat.read_outbox_max_id > value) {
								val update = TL_updateReadChannelOutbox()
								update.channel_id = chatId
								update.max_id = fullChat.read_outbox_max_id

								processUpdateArray(listOf(update), null, null, false, 0)
							}
						}
					}

					AndroidUtilities.runOnUIThread {
						val old = fullChats[chatId]

						if (old != null) {
							response.fullChat?.inviterId = old.inviterId
						}

						fullChats.put(chatId, response.fullChat)

						applyDialogNotificationsSettings(-chatId, response.fullChat?.notify_settings)

						response.fullChat?.bot_info?.forEach {
							mediaDataController.putBotInfo(-chatId, it)
						}

						val index = blockedPeers.indexOfKey(-chatId)

						if (response.fullChat?.blocked == true) {
							if (index < 0) {
								blockedPeers.put(-chatId, 1)
								notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)
							}
						}
						else {
							if (index >= 0) {
								blockedPeers.removeAt(index)
								notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)
							}
						}

						exportedChats.put(chatId, response.fullChat?.exported_invite)

						loadingFullChats.remove(chatId)
						loadedFullChats.add(chatId)

						putUsers(response.users, false)
						putChats(response.chats, false)

						if (response.fullChat?.stickerset != null) {
							mediaDataController.getGroupStickerSetById(response.fullChat?.stickerset)
						}

						notificationCenter.postNotificationName(NotificationCenter.chatInfoDidLoad, response.fullChat, classGuid, false, true)

						if ((response.fullChat?.flags ?: 0) and 2048 != 0) {
							val dialog = dialogs_dict[-chatId]

							if (dialog != null && dialog.folder_id != response.fullChat?.folder_id) {
								dialog.folder_id = response.fullChat?.folder_id ?: 0
								sortDialogs(null)
								notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
							}
						}
					}
				}
			}

			if (!ok) {
				AndroidUtilities.runOnUIThread {
					checkChannelError(error?.text, chatId)
					loadingFullChats.remove(chatId)
				}
			}
		}

		if (classGuid != 0) {
			connectionsManager.bindRequestToGuid(reqId, classGuid)
		}
	}

	fun loadFullUser(user: User?, classGuid: Int, force: Boolean) {
		if (user == null || loadingFullUsers.contains(user.id)) {
			return
		}

		if (!force && loadedFullUsers.contains(user.id)) {
			val userFull = getUserFull(user.id)

			if (userFull != null) {
				ioScope.launch {
					delay(500)

					mainScope.launch {
						notificationCenter.postNotificationName(NotificationCenter.userInfoDidLoad, user.id, userFull)
					}
				}

				return
			}
		}

		loadingFullUsers.add(user.id)

		val req = TL_users_getFullUser()
		req.id = getInputUser(user)

		val dialogId = user.id

		if (dialogs_read_inbox_max[dialogId] == null || dialogs_read_outbox_max[dialogId] == null) {
			reloadDialogsReadValue(null, dialogId)
		}

		connectionsManager.sendRequest(req) { response, error ->
			if (error == null) {
				val res = response as? TL_users_userFull

				if (res == null) {
					AndroidUtilities.runOnUIThread {
						loadingFullUsers.remove(user.id)
					}

					return@sendRequest
				}

				val userFull = res.fullUser

				putUsers(res.users, false)
				putChats(res.chats, false)

				userFull?.user = getUser(userFull?.id)

				messagesStorage.updateUserInfo(userFull, false)

				AndroidUtilities.runOnUIThread {
					if (userFull != null) {
						savePeerSettings(userFull.user?.id ?: 0L, userFull.settings)
						applyDialogNotificationsSettings(user.id, userFull.notify_settings)

						if (userFull.bot_info is TL_botInfo) {
							userFull.bot_info?.user_id = user.id
							mediaDataController.putBotInfo(user.id, userFull.bot_info)
						}

						val index = blockedPeers.indexOfKey(user.id)

						if (userFull.blocked) {
							if (index < 0) {
								blockedPeers.put(user.id, 1)
								notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)
							}
						}
						else {
							if (index >= 0) {
								blockedPeers.removeAt(index)
								notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)
							}
						}

						fullUsers.put(user.id, userFull)
					}

					loadingFullUsers.remove(user.id)
					loadedFullUsers.add(user.id)

					val names = user.first_name + user.last_name + user.username

					userFull?.user?.let {
						val users = listOf(it)
						putUsers(users, false)
						messagesStorage.putUsersAndChats(users, null, false, true)
					}

					if (names != userFull?.user?.first_name + userFull?.user?.last_name + userFull?.user?.username) {
						notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_NAME)
					}

					if (userFull?.user?.photo?.has_video == true) {
						notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_AVATAR)
					}

					if (userFull?.bot_info is TL_botInfo) {
						userFull.bot_info?.user_id = userFull.id
						notificationCenter.postNotificationName(NotificationCenter.botInfoDidLoad, userFull.bot_info, classGuid)
					}

					notificationCenter.postNotificationName(NotificationCenter.userInfoDidLoad, user.id, userFull)

					if ((userFull?.flags ?: 0) and 2048 != 0) {
						val dialog = dialogs_dict[user.id]

						if (dialog != null && dialog.folder_id != userFull?.folder_id) {
							dialog.folder_id = userFull?.folder_id ?: 0
							sortDialogs(null)
							notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
						}
					}
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					loadingFullUsers.remove(user.id)
				}
			}
		}
	}

	private fun reloadMessages(mids: List<Int>, dialogId: Long, scheduled: Boolean) {
		if (mids.isEmpty()) {
			return
		}

		val request: TLObject
		val result = ArrayList<Int>()

		val chat = if (DialogObject.isChatDialog(dialogId)) {
			getChat(-dialogId)
		}
		else {
			null
		}

		if (ChatObject.isChannel(chat)) {
			val req = TL_channels_getMessages()
			req.channel = getInputChannel(chat)
			req.id = result

			request = req
		}
		else {
			val req = TL_messages_getMessages()
			req.id = result

			request = req
		}

		var arrayList = reloadingMessages[dialogId]

		for (a in mids.indices) {
			val mid = mids[a]

			if (arrayList != null && arrayList.contains(mid)) {
				continue
			}

			result.add(mid)
		}

		if (result.isEmpty()) {
			return
		}

		if (arrayList == null) {
			arrayList = ArrayList()
			reloadingMessages.put(dialogId, arrayList)
		}

		arrayList.addAll(result)

		connectionsManager.sendRequest(request) { response, _ ->
			if (response is messages_Messages) {
				val usersLocal = LongSparseArray<User>()

				for (u in response.users) {
					usersLocal.put(u.id, u)
				}

				val chatsLocal = LongSparseArray<Chat>()

				for (c in response.chats) {
					chatsLocal.put(c.id, c)
				}

				var inboxValue = dialogs_read_inbox_max[dialogId]

				if (inboxValue == null) {
					inboxValue = messagesStorage.getDialogReadMax(false, dialogId)
					dialogs_read_inbox_max[dialogId] = inboxValue
				}

				var outboxValue = dialogs_read_outbox_max[dialogId]

				if (outboxValue == null) {
					outboxValue = messagesStorage.getDialogReadMax(true, dialogId)
					dialogs_read_outbox_max[dialogId] = outboxValue
				}

				val objects = mutableListOf<MessageObject>()

				for (a in response.messages.indices) {
					val message = response.messages[a]
					message.dialog_id = dialogId

					if (!scheduled) {
						message.unread = (if (message.out) outboxValue else inboxValue) < message.id
					}

					objects.add(MessageObject(currentAccount, message, usersLocal, chatsLocal, generateLayout = true, checkMediaExists = true))
				}

				ImageLoader.saveMessagesThumbs(response.messages)

				messagesStorage.putMessages(response, dialogId, -1, 0, false, scheduled)

				AndroidUtilities.runOnUIThread {
					val arrayList1 = reloadingMessages[dialogId]

					if (arrayList1 != null) {
						arrayList1.removeAll(result.toSet())

						if (arrayList1.isEmpty()) {
							reloadingMessages.remove(dialogId)
						}
					}

					val dialogObj = dialogMessage[dialogId]

					if (dialogObj != null) {
						for (a in objects.indices) {
							val obj = objects[a]

							if (dialogObj.id == obj.id) {
								dialogMessage.put(dialogId, obj)

								if (obj.messageOwner?.peer_id?.channel_id == 0L) {
									val obj2 = dialogMessagesByIds[obj.id]

									dialogMessagesByIds.remove(obj.id)

									if (obj2 != null) {
										dialogMessagesByIds.put(obj2.id, obj2)
									}
								}

								notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

								break
							}
						}
					}

					notificationCenter.postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, objects)
				}
			}
		}
	}

	fun hidePeerSettingsBar(dialogId: Long, currentUser: User?, currentChat: Chat?) {
		if (currentUser == null && currentChat == null) {
			return
		}

		val editor = notificationsPreferences.edit()
		editor.putInt("dialog_bar_vis3$dialogId", 3)
		editor.remove("dialog_bar_invite$dialogId")
		editor.commit()

		if (!DialogObject.isEncryptedDialog(dialogId)) {
			val req = TL_messages_hidePeerSettingsBar()

			if (currentUser != null) {
				req.peer = getInputPeer(currentUser.id)
			}
			else {
				req.peer = getInputPeer(-currentChat!!.id)
			}

			connectionsManager.sendRequest(req)
		}
	}

	fun reportSpam(dialogId: Long, currentUser: User?, currentChat: Chat?, currentEncryptedChat: EncryptedChat?, geo: Boolean) {
		if (currentUser == null && currentChat == null && currentEncryptedChat == null) {
			return
		}

		val editor = notificationsPreferences.edit()
		editor.putInt("dialog_bar_vis3$dialogId", 3)
		editor.commit()

		if (DialogObject.isEncryptedDialog(dialogId)) {
			if (currentEncryptedChat == null || currentEncryptedChat.access_hash == 0L) {
				return
			}

			val req = TL_messages_reportEncryptedSpam()
			req.peer = TL_inputEncryptedChat()
			req.peer.chat_id = currentEncryptedChat.id
			req.peer.access_hash = currentEncryptedChat.access_hash

			connectionsManager.sendRequest(req, null, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
		else {
			if (geo) {
				val req = TL_account_reportPeer()

				if (currentChat != null) {
					req.peer = getInputPeer(-currentChat.id)
				}
				else if (currentUser != null) {
					req.peer = getInputPeer(currentUser.id)
				}

				req.message = ""
				req.reason = TL_inputReportReasonGeoIrrelevant()

				connectionsManager.sendRequest(req, null, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else {
				val req = TL_messages_reportSpam()

				if (currentChat != null) {
					req.peer = getInputPeer(-currentChat.id)
				}
				else if (currentUser != null) {
					req.peer = getInputPeer(currentUser.id)
				}

				connectionsManager.sendRequest(req, null, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
		}
	}

	private fun savePeerSettings(dialogId: Long, settings: TL_peerSettings?) {
		if (settings == null || notificationsPreferences.getInt("dialog_bar_vis3$dialogId", 0) == 3) {
			return
		}

		val editor = notificationsPreferences.edit()
		val barHidden = !settings.report_spam && !settings.add_contact && !settings.block_contact && !settings.share_contact && !settings.report_geo && !settings.invite_members

		if (BuildConfig.DEBUG) {
			FileLog.d("peer settings loaded for " + dialogId + " add = " + settings.add_contact + " block = " + settings.block_contact + " spam = " + settings.report_spam + " share = " + settings.share_contact + " geo = " + settings.report_geo + " hide = " + barHidden + " distance = " + settings.geo_distance + " invite = " + settings.invite_members)
		}

		editor.putInt("dialog_bar_vis3$dialogId", if (barHidden) 1 else 2)
		editor.putBoolean("dialog_bar_share$dialogId", settings.share_contact)
		editor.putBoolean("dialog_bar_report$dialogId", settings.report_spam)
		editor.putBoolean("dialog_bar_add$dialogId", settings.add_contact)
		editor.putBoolean("dialog_bar_block$dialogId", settings.block_contact)
		editor.putBoolean("dialog_bar_exception$dialogId", settings.need_contacts_exception)
		editor.putBoolean("dialog_bar_location$dialogId", settings.report_geo)
		editor.putBoolean("dialog_bar_archived$dialogId", settings.autoarchived)
		editor.putBoolean("dialog_bar_invite$dialogId", settings.invite_members)
		editor.putString("dialog_bar_chat_with_admin_title$dialogId", settings.request_chat_title)
		editor.putBoolean("dialog_bar_chat_with_channel$dialogId", settings.request_chat_broadcast)
		editor.putInt("dialog_bar_chat_with_date$dialogId", settings.request_chat_date)

		if (notificationsPreferences.getInt("dialog_bar_distance$dialogId", -1) != -2) {
			if (settings.flags and 64 != 0) {
				editor.putInt("dialog_bar_distance$dialogId", settings.geo_distance)
			}
			else {
				editor.remove("dialog_bar_distance$dialogId")
			}
		}

		editor.commit()

		notificationCenter.postNotificationName(NotificationCenter.peerSettingsDidLoad, dialogId)
	}

	fun loadPeerSettings(currentUser: User?, currentChat: Chat?) {
		if (currentUser == null && currentChat == null) {
			return
		}

		val dialogId = currentUser?.id ?: -(currentChat?.id ?: 0L)

		if (loadingPeerSettings.indexOfKey(dialogId) >= 0) {
			return
		}

		loadingPeerSettings.put(dialogId, true)

		if (BuildConfig.DEBUG) {
			FileLog.d("request spam button for $dialogId")
		}

		val vis = notificationsPreferences.getInt("dialog_bar_vis3$dialogId", 0)

		if (vis == 1 || vis == 3) {
			if (BuildConfig.DEBUG) {
				FileLog.d("dialog bar already hidden for $dialogId")
			}

			return
		}

		val req = TL_messages_getPeerSettings()

		if (currentUser != null) {
			req.peer = getInputPeer(currentUser.id)
		}
		else {
			req.peer = getInputPeer(-currentChat!!.id)
		}

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				loadingPeerSettings.remove(dialogId)

				if (response is TL_messages_peerSettings) {
					putUsers(response.users, false)
					putChats(response.chats, false)

					savePeerSettings(dialogId, response.settings)
				}
			}
		}
	}

	fun processNewChannelDifferenceParams(pts: Int, ptsCount: Int, channelId: Long) {
		if (BuildConfig.DEBUG) {
			FileLog.d("processNewChannelDifferenceParams pts = $pts pts_count = $ptsCount channeldId = $channelId")
		}

		var channelPts = channelsPts[channelId]

		if (channelPts == 0) {
			channelPts = messagesStorage.getChannelPtsSync(channelId)

			if (channelPts == 0) {
				channelPts = 1
			}

			channelsPts.put(channelId, channelPts)
		}

		if (channelPts + ptsCount == pts) {
			if (BuildConfig.DEBUG) {
				FileLog.d("APPLY CHANNEL PTS")
			}

			channelsPts.put(channelId, pts)
			messagesStorage.saveChannelPts(channelId, pts)
		}
		else if (channelPts != pts) {
			val updatesStartWaitTime = updatesStartWaitTimeChannels[channelId]
			val gettingDifferenceChannel = gettingDifferenceChannels[channelId, false]

			if (gettingDifferenceChannel || updatesStartWaitTime == 0L || abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500) {
				if (BuildConfig.DEBUG) {
					FileLog.d("ADD CHANNEL UPDATE TO QUEUE pts = $pts pts_count = $ptsCount")
				}

				if (updatesStartWaitTime == 0L) {
					updatesStartWaitTimeChannels.put(channelId, System.currentTimeMillis())
				}

				val updates = UserActionUpdatesPts()
				updates.pts = pts
				updates.pts_count = ptsCount
				updates.chat_id = channelId

				var arrayList = updatesQueueChannels[channelId]

				if (arrayList == null) {
					arrayList = ArrayList()
					updatesQueueChannels.put(channelId, arrayList)
				}

				arrayList.add(updates)
			}
			else {
				getChannelDifference(channelId)
			}
		}
	}

	fun processNewDifferenceParams(seq: Int, pts: Int, date: Int, ptsCount: Int) {
		if (pts != -1) {
			if (messagesStorage.lastPtsValue + ptsCount == pts) {
				messagesStorage.lastPtsValue = pts
				messagesStorage.saveDiffParams(messagesStorage.lastSeqValue, messagesStorage.lastPtsValue, messagesStorage.lastDateValue, messagesStorage.lastQtsValue)
			}
			else if (messagesStorage.lastPtsValue != pts) {
				if (gettingDifference || updatesStartWaitTimePts == 0L || abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
					if (updatesStartWaitTimePts == 0L) {
						updatesStartWaitTimePts = System.currentTimeMillis()
					}

					val updates = UserActionUpdatesPts()
					updates.pts = pts
					updates.pts_count = ptsCount

					updatesQueuePts.add(updates)
				}
				else {
					getDifference()
				}
			}
		}
		if (seq != -1) {
			if (messagesStorage.lastSeqValue + 1 == seq) {
				messagesStorage.lastSeqValue = seq

				if (date != -1) {
					messagesStorage.lastDateValue = date
				}

				messagesStorage.saveDiffParams(messagesStorage.lastSeqValue, messagesStorage.lastPtsValue, messagesStorage.lastDateValue, messagesStorage.lastQtsValue)
			}
			else if (messagesStorage.lastSeqValue != seq) {
				if (gettingDifference || updatesStartWaitTimeSeq == 0L || abs(System.currentTimeMillis() - updatesStartWaitTimeSeq) <= 1500) {
					if (updatesStartWaitTimeSeq == 0L) {
						updatesStartWaitTimeSeq = System.currentTimeMillis()
					}

					val updates = UserActionUpdatesSeq()
					updates.seq = seq

					updatesQueueSeq.add(updates)
				}
				else {
					getDifference()
				}
			}
		}
	}

	fun didAddedNewTask(minDate: Int, dialogId: Long, mids: SparseArray<ArrayList<Int>>?) {
		Utilities.stageQueue.postRunnable {
			if (currentDeletingTaskMids == null && currentDeletingTaskMediaMids == null && !gettingNewDeleteTask || currentDeletingTaskTime != 0 && minDate < currentDeletingTaskTime) {
				getNewDeleteTask(null, null)
			}
		}

		if (mids != null) {
			AndroidUtilities.runOnUIThread {
				notificationCenter.postNotificationName(NotificationCenter.didCreatedNewDeleteTask, dialogId, mids)
			}
		}
	}

	fun getNewDeleteTask(oldTask: LongSparseArray<List<Int>>?, oldTaskMedia: LongSparseArray<List<Int>>?) {
		Utilities.stageQueue.postRunnable {
			gettingNewDeleteTask = true
			messagesStorage.getNewTask(oldTask, oldTaskMedia)
		}
	}

	private fun checkDeletingTask(runnable: Boolean): Boolean {
		val currentServerTime = connectionsManager.currentTime

		if ((currentDeletingTaskMids != null || currentDeletingTaskMediaMids != null) && (runnable || currentDeletingTaskTime != 0 && currentDeletingTaskTime <= currentServerTime)) {
			currentDeletingTaskTime = 0

			if (currentDeleteTaskRunnable != null && !runnable) {
				Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable)
			}

			currentDeleteTaskRunnable = null

			val task = currentDeletingTaskMids?.clone()
			val taskMedia = currentDeletingTaskMediaMids?.clone()

			AndroidUtilities.runOnUIThread {
				if (task != null) {
					for (a in 0 until task.size()) {
						val mids = task.valueAt(a)
						deleteMessages(mids, null, null, task.keyAt(a), forAll = true, scheduled = false, cacheOnly = mids.isNotEmpty() && mids[0] > 0)
					}
				}

				if (taskMedia != null) {
					for (a in 0 until taskMedia.size()) {
						messagesStorage.emptyMessagesMedia(taskMedia.keyAt(a), taskMedia.valueAt(a))
					}
				}

				Utilities.stageQueue.postRunnable {
					getNewDeleteTask(task, taskMedia)
					currentDeletingTaskTime = 0
					currentDeletingTaskMids = null
					currentDeletingTaskMediaMids = null
				}
			}

			return true
		}

		return false
	}

	fun processLoadedDeleteTask(taskTime: Int, task: LongSparseArray<List<Int>>?, taskMedia: LongSparseArray<List<Int>>?) {
		Utilities.stageQueue.postRunnable {
			gettingNewDeleteTask = false

			if (task != null || taskMedia != null) {
				currentDeletingTaskTime = taskTime
				currentDeletingTaskMids = task
				currentDeletingTaskMediaMids = taskMedia

				if (currentDeleteTaskRunnable != null) {
					Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable)
					currentDeleteTaskRunnable = null
				}

				if (!checkDeletingTask(false)) {
					currentDeleteTaskRunnable = Runnable { checkDeletingTask(true) }

					val currentServerTime = connectionsManager.currentTime

					Utilities.stageQueue.postRunnable(currentDeleteTaskRunnable, abs(currentServerTime - currentDeletingTaskTime).toLong() * 1000)
				}
			}
			else {
				currentDeletingTaskTime = 0
				currentDeletingTaskMids = null
				currentDeletingTaskMediaMids = null
			}
		}
	}

	fun loadDialogPhotos(did: Long, count: Int, maxId: Int, fromCache: Boolean, classGuid: Int) {
		if (fromCache) {
			messagesStorage.getDialogPhotos(did, count, maxId, classGuid)
		}
		else {
			if (did > 0) {
				val user = getUser(did) ?: return

				val req = TL_photos_getUserPhotos()
				req.limit = count
				req.offset = 0
				req.max_id = maxId.toLong()
				req.user_id = getInputUser(user)

				val reqId = connectionsManager.sendRequest(req) { response, _ ->
					if (response is photos_Photos) {
						processLoadedUserPhotos(response, null, did, count, maxId, false, classGuid)
					}
				}

				connectionsManager.bindRequestToGuid(reqId, classGuid)
			}
			else if (did < 0) {
				val req = TL_messages_search()
				req.filter = TL_inputMessagesFilterChatPhotos()
				req.limit = count
				req.offset_id = maxId
				req.q = ""
				req.peer = getInputPeer(did)

				val reqId = connectionsManager.sendRequest(req) { response, _ ->
					if (response is messages_Messages) {
						val res = TL_photos_photos()
						res.count = response.count
						res.users.addAll(response.users)

						val arrayList = ArrayList<Message>()

						for (a in response.messages.indices) {
							val message = response.messages[a]
							val photo = message.action?.photo ?: continue

							res.photos.add(photo)

							arrayList.add(message)
						}

						processLoadedUserPhotos(res, arrayList, did, count, maxId, false, classGuid)
					}
				}

				connectionsManager.bindRequestToGuid(reqId, classGuid)
			}
		}
	}

	fun blockPeer(id: Long) {
		var user: User? = null
		var chat: Chat? = null

		if (id > 0) {
			user = getUser(id)

			if (user == null) {
				return
			}
		}
		else {
			chat = getChat(-id)

			if (chat == null) {
				return
			}
		}

		if (blockedPeers.indexOfKey(id) >= 0) {
			return
		}

		blockedPeers.put(id, 1)

		if (user != null) {
			if (user.bot) {
				mediaDataController.removeInline(id)
			}
			else {
				mediaDataController.removePeer(id)
			}
		}

		if (totalBlockedCount >= 0) {
			totalBlockedCount++
		}

		notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)

		val req = TL_contacts_block()

		if (user != null) {
			req.id = getInputPeer(user)
		}
		else {
			req.id = getInputPeer(chat)
		}

		connectionsManager.sendRequest(req)
	}

	fun setParticipantBannedRole(chatId: Long, user: User?, chat: Chat?, rights: TL_chatBannedRights?, isChannel: Boolean, parentFragment: BaseFragment?) {
		if (user == null && chat == null || rights == null) {
			return
		}

		val req = TL_channels_editBanned()
		req.channel = getInputChannel(chatId)

		if (user != null) {
			req.participant = getInputPeer(user)
		}
		else {
			req.participant = getInputPeer(chat)
		}

		req.banned_rights = rights

		connectionsManager.sendRequest(req) { response, error ->
			if (response is Updates) {
				processUpdates(response, false)

				AndroidUtilities.runOnUIThread({
					loadFullChat(chatId, 0, true)
				}, 1000)
			}
			else {
				AndroidUtilities.runOnUIThread {
					AlertsCreator.processError(currentAccount, error, parentFragment, req, isChannel)
				}
			}
		}
	}

	fun setChannelSlowMode(chatId: Long, seconds: Int) {
		val req = TL_channels_toggleSlowMode()
		req.seconds = seconds
		req.channel = getInputChannel(chatId)

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is Updates) {
				messagesController.processUpdates(response, false)

				AndroidUtilities.runOnUIThread({
					loadFullChat(chatId, 0, true)
				}, 1000)
			}
		}
	}

	fun setDefaultBannedRole(chatId: Long, rights: TL_chatBannedRights?, isChannel: Boolean, parentFragment: BaseFragment?) {
		if (rights == null) {
			return
		}

		val req = TL_messages_editChatDefaultBannedRights()
		req.peer = getInputPeer(-chatId)
		req.banned_rights = rights

		connectionsManager.sendRequest(req) { response, error ->
			if (response is Updates) {
				processUpdates(response, false)

				AndroidUtilities.runOnUIThread({
					loadFullChat(chatId, 0, true)
				}, 1000)
			}
			else {
				AndroidUtilities.runOnUIThread {
					AlertsCreator.processError(currentAccount, error, parentFragment, req, isChannel)
				}
			}
		}
	}

	@JvmOverloads
	fun setUserAdminRole(chatId: Long, user: User?, rights: TL_chatAdminRights?, rank: String?, isChannel: Boolean, parentFragment: BaseFragment?, addingNew: Boolean, forceAdmin: Boolean, botHash: String?, onSuccess: Runnable?, onError: ErrorDelegate? = null) {
		if (user == null || rights == null) {
			return
		}

		val chat = getChat(chatId)

		if (ChatObject.isChannel(chat)) {
			val req = TL_channels_editAdmin()

			val channelInput: InputChannel = TL_inputChannel()
			channelInput.channel_id = chatId
			channelInput.access_hash = chat.access_hash

			req.channel = channelInput
			req.user_id = getInputUser(user)
			req.admin_rights = rights
			req.rank = rank

			val requestDelegate = RequestDelegate { response, error ->
				if (response is Updates) {
					processUpdates(response, false)

					AndroidUtilities.runOnUIThread({
						loadFullChat(chatId, 0, true)
						onSuccess?.run()
					}, 1000)
				}
				else {
					AndroidUtilities.runOnUIThread {
						AlertsCreator.processError(currentAccount, error, parentFragment, req, isChannel)
					}

					if (onError != null) {
						AndroidUtilities.runOnUIThread {
							onError.run(error)
						}
					}
				}
			}

			if (chat.megagroup && addingNew || !botHash.isNullOrEmpty()) {
				addUserToChat(chatId, user, 0, botHash, parentFragment, true, { connectionsManager.sendRequest(req, requestDelegate) }, onError)
			}
			else {
				connectionsManager.sendRequest(req, requestDelegate)
			}
		}
		else {
			val req = TL_messages_editChatAdmin()
			req.chat_id = chatId
			req.user_id = getInputUser(user)
			req.is_admin = forceAdmin || rights.change_info || rights.delete_messages || rights.ban_users || rights.invite_users || rights.pin_messages || rights.add_admins || rights.manage_call

			val requestDelegate = RequestDelegate { _, error ->
				if (error == null) {
					AndroidUtilities.runOnUIThread({
						loadFullChat(chatId, 0, true)
						onSuccess?.run()
					}, 1000)
				}
				else {
					AndroidUtilities.runOnUIThread {
						AlertsCreator.processError(currentAccount, error, parentFragment, req, false)
					}

					if (onError != null) {
						AndroidUtilities.runOnUIThread {
							onError.run(error)
						}
					}
				}
			}
			if (req.is_admin || addingNew || !TextUtils.isEmpty(botHash)) {
				addUserToChat(chatId, user, 0, botHash, parentFragment, true, { connectionsManager.sendRequest(req, requestDelegate) }, onError)
			}
			else {
				connectionsManager.sendRequest(req, requestDelegate)
			}
		}
	}

	fun unblockPeer(id: Long) {
		val req = TL_contacts_unblock()
		var user: User? = null
		var chat: Chat? = null

		if (id > 0) {
			user = getUser(id)

			if (user == null) {
				return
			}
		}
		else {
			chat = getChat(-id)

			if (chat == null) {
				return
			}
		}

		totalBlockedCount--
		blockedPeers.delete(id)

		if (user != null) {
			req.id = getInputPeer(user)
		}
		else {
			req.id = getInputPeer(chat)
		}

		notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)

		connectionsManager.sendRequest(req)
	}

	fun getBlockedPeers(reset: Boolean) {
		if (!userConfig.isClientActivated || loadingBlockedPeers) {
			return
		}

		loadingBlockedPeers = true

		val req = TL_contacts_getBlocked()
		req.offset = if (reset) 0 else blockedPeers.size()
		req.limit = if (reset) 20 else 100

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is contacts_Blocked) {
					putUsers(response.users, false)
					putChats(response.chats, false)

					messagesStorage.putUsersAndChats(response.users, response.chats, true, true)

					if (reset) {
						blockedPeers.clear()
					}

					totalBlockedCount = max(response.count, response.blocked.size)
					blockedEndReached = response.blocked.size < req.limit

					for (peer in response.blocked) {
						blockedPeers.put(MessageObject.getPeerId(peer.peer_id), 1)
					}

					loadingBlockedPeers = false

					notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)
				}
			}
		}
	}

	@JvmOverloads
	fun deleteUserPhoto(photo: InputPhoto?, isLastPhoto: Boolean = false) {
		if (photo == null) {
			userConfig.getCurrentUser()?.photo = TL_userProfilePhotoEmpty()

			var user = getUser(userConfig.getClientUserId())

			if (user == null) {
				user = userConfig.getCurrentUser()
			}

			if (user == null) {
				return
			}

			if (user.photo != null) {
				messagesStorage.clearUserPhoto(user.id, user.photo?.photo_id ?: 0)
			}

			user.photo = userConfig.getCurrentUser()?.photo

			notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
			notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_ALL)

			val req = TL_photos_updateProfilePhoto()
			req.id = TL_inputPhotoEmpty()

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_photos_photo) {
					AndroidUtilities.runOnUIThread {
						messagesStorage.clearUserPhotos(user.id)

						var user1 = getUser(userConfig.getClientUserId())

						if (user1 == null) {
							user1 = userConfig.getCurrentUser()
							putUser(user1, false)
						}
						else {
							userConfig.setCurrentUser(user1)
						}

						if (user1 == null) {
							return@runOnUIThread
						}

						if (response.photo is TL_photo && !isLastPhoto) {
							user1.photo = TL_userProfilePhoto()
							user1.photo?.has_video = !response.photo?.video_sizes.isNullOrEmpty()
							user1.photo?.photo_id = response.photo?.id ?: 0L
							user1.photo?.photo_small = FileLoader.getClosestPhotoSizeWithSize(response.photo?.sizes, 150)?.location
							user1.photo?.photo_big = FileLoader.getClosestPhotoSizeWithSize(response.photo?.sizes, 800)?.location
							user1.photo?.dc_id = response.photo?.dc_id ?: 0
						}
						else {
							user1.photo = TL_userProfilePhotoEmpty()
						}

						messagesStorage.putUsersAndChats(listOf(user1), null, false, false)

						val userFull = getUserFull(user1.id)

						if (isLastPhoto) {
							userFull?.profile_photo = null
						}
						else {
							userFull?.profile_photo = response.photo
						}

						messagesStorage.updateUserInfo(userFull, false)

						userConfig.getCurrentUser()?.photo = user1.photo

						userConfig.saveConfig(true)

						notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
						notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_ALL)
					}
				}
			}
		}
		else {
			val req = TL_photos_deletePhotos()
			req.id.add(photo)

			connectionsManager.sendRequest(req) { _, error ->
				if (error == null && isLastPhoto) {
					AndroidUtilities.runOnUIThread {
						deleteUserPhoto(null, true)
					}
				}
			}
		}
	}

	fun processLoadedUserPhotos(res: photos_Photos?, messages: ArrayList<Message>?, did: Long, count: Int, maxId: Int, fromCache: Boolean, classGuid: Int) {
		if (!fromCache && res != null) {
			messagesStorage.putUsersAndChats(res.users, null, true, true)
			messagesStorage.putDialogPhotos(did, res, messages)
		}
		else if (res == null || res.photos.isEmpty()) {
			loadDialogPhotos(did, count, maxId, false, classGuid)
			return
		}

		AndroidUtilities.runOnUIThread {
			putUsers(res.users, fromCache)
			notificationCenter.postNotificationName(NotificationCenter.dialogPhotosLoaded, did, count, fromCache, classGuid, res.photos, messages)
		}
	}

	fun uploadAndApplyUserAvatar(location: FileLocation?) {
		if (location == null) {
			return
		}

		uploadingAvatar = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + location.volume_id + "_" + location.local_id + ".jpg"

		fileLoader.uploadFile(uploadingAvatar, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)
	}

	fun saveTheme(themeInfo: ThemeInfo?, accent: ThemeAccent?, night: Boolean, unsave: Boolean) {
		val info = accent?.info ?: themeInfo?.info

		if (info != null) {
			val req = TL_account_saveTheme()

			val inputTheme = TL_inputTheme()
			inputTheme.id = info.id
			inputTheme.access_hash = info.access_hash

			req.theme = inputTheme
			req.unsave = unsave

			connectionsManager.sendRequest(req)
			connectionsManager.resumeNetworkMaybe()
		}

		if (!unsave) {
			installTheme(themeInfo, accent, night)
		}
	}

	fun installTheme(themeInfo: ThemeInfo?, accent: ThemeAccent?, night: Boolean) {
		val info = accent?.info ?: themeInfo?.info
		val slug = accent?.patternSlug ?: themeInfo?.slug
		val isBlurred = accent == null && themeInfo?.isBlured == true
		val isMotion = accent?.patternMotion ?: themeInfo?.isMotion ?: false

		val req = TL_account_installTheme()
		req.dark = night

		if (info != null) {
			req.format = "android"

			val inputTheme = TL_inputTheme()
			inputTheme.id = info.id
			inputTheme.access_hash = info.access_hash

			req.theme = inputTheme
			req.flags = req.flags or 2
		}

		connectionsManager.sendRequest(req)

		if (!slug.isNullOrEmpty()) {
			val req2 = TL_account_installWallPaper()

			val inputWallPaperSlug = TL_inputWallPaperSlug()
			inputWallPaperSlug.slug = slug

			req2.wallpaper = inputWallPaperSlug
			req2.settings = TL_wallPaperSettings()
			req2.settings.blur = isBlurred
			req2.settings.motion = isMotion

			connectionsManager.sendRequest(req2)
		}
	}

	fun saveThemeToServer(themeInfo: ThemeInfo?, accent: ThemeAccent?) {
		if (themeInfo == null) {
			return
		}

		val key: String?
		val pathToWallpaper: File?

		if (accent != null) {
			key = accent.saveToFile().absolutePath
			pathToWallpaper = accent.pathToWallpaper
		}
		else {
			key = themeInfo.pathToFile
			pathToWallpaper = null
		}

		if (key == null) {
			return
		}

		if (uploadingThemes.containsKey(key)) {
			return
		}

		uploadingThemes[key] = accent ?: themeInfo

		Utilities.globalQueue.postRunnable {
			val thumbPath = Theme.createThemePreviewImage(key, pathToWallpaper?.absolutePath, accent)

			AndroidUtilities.runOnUIThread {
				if (thumbPath == null) {
					uploadingThemes.remove(key)
					return@runOnUIThread
				}

				uploadingThemes[thumbPath] = accent ?: themeInfo

				if (accent == null) {
					themeInfo.uploadingFile = key
					themeInfo.uploadingThumb = thumbPath
				}
				else {
					accent.uploadingFile = key
					accent.uploadingThumb = thumbPath
				}

				fileLoader.uploadFile(key, encrypted = false, small = true, type = ConnectionsManager.FileTypeFile)
				fileLoader.uploadFile(thumbPath, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)
			}
		}
	}

	fun saveWallpaperToServer(path: File?, info: OverrideWallpaperInfo, install: Boolean, taskId: Long) {
		if (uploadingWallpaper != null) {
			val finalPath = File(ApplicationLoader.filesDirFixed, info.originalFileName)

			if (path != null && (path.absolutePath == uploadingWallpaper || path == finalPath)) {
				uploadingWallpaperInfo = info
				return
			}

			fileLoader.cancelFileUpload(uploadingWallpaper!!, false)

			uploadingWallpaper = null
			uploadingWallpaperInfo = null
		}

		if (path != null) {
			uploadingWallpaper = path.absolutePath
			uploadingWallpaperInfo = info

			fileLoader.uploadFile(uploadingWallpaper, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)
		}
		else if (!info.isDefault && !info.isColor && info.wallpaperId > 0 && !info.isTheme) {
			val inputWallPaper: InputWallPaper

			if (info.wallpaperId > 0) {
				val inputWallPaperId = TL_inputWallPaper()
				inputWallPaperId.id = info.wallpaperId
				inputWallPaperId.access_hash = info.accessHash
				inputWallPaper = inputWallPaperId
			}
			else {
				val inputWallPaperSlug = TL_inputWallPaperSlug()
				inputWallPaperSlug.slug = info.slug
				inputWallPaper = inputWallPaperSlug
			}

			val settings = TL_wallPaperSettings()
			settings.blur = info.isBlurred
			settings.motion = info.isMotion

			if (info.color != 0) {
				settings.background_color = info.color and 0x00ffffff
				settings.flags = settings.flags or 1
				settings.intensity = (info.intensity * 100).toInt()
				settings.flags = settings.flags or 8
			}

			if (info.gradientColor1 != 0) {
				settings.second_background_color = info.gradientColor1 and 0x00ffffff
				settings.rotation = AndroidUtilities.getWallpaperRotation(info.rotation, true)
				settings.flags = settings.flags or 16
			}

			if (info.gradientColor2 != 0) {
				settings.third_background_color = info.gradientColor2 and 0x00ffffff
				settings.flags = settings.flags or 32
			}

			if (info.gradientColor3 != 0) {
				settings.fourth_background_color = info.gradientColor3 and 0x00ffffff
				settings.flags = settings.flags or 64
			}

			val req = if (install) {
				val request = TL_account_installWallPaper()
				request.wallpaper = inputWallPaper
				request.settings = settings
				request
			}
			else {
				val request = TL_account_saveWallPaper()
				request.wallpaper = inputWallPaper
				request.settings = settings
				request
			}

			val newTaskId = if (taskId != 0L) {
				taskId
			}
			else {
				var data: NativeByteBuffer? = null

				try {
					data = NativeByteBuffer(1024)
					data.writeInt32(21)
					data.writeBool(info.isBlurred)
					data.writeBool(info.isMotion)
					data.writeInt32(info.color)
					data.writeInt32(info.gradientColor1)
					data.writeInt32(info.rotation)
					data.writeDouble(info.intensity.toDouble())
					data.writeBool(install)
					data.writeString(info.slug)
					data.writeString(info.originalFileName)
					data.limit(data.position())
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				messagesStorage.createPendingTask(data)
			}

			connectionsManager.sendRequest(req) { _, _ ->
				messagesStorage.removePendingTask(newTaskId)
			}
		}

		if ((info.isColor || info.gradientColor2 != 0) && info.wallpaperId <= 0) {
			val wallPaper: WallPaper

			if (info.isColor) {
				wallPaper = TL_wallPaperNoFile()
			}
			else {
				wallPaper = TL_wallPaper()
				wallPaper.slug = info.slug
				wallPaper.document = TL_documentEmpty()
			}

			if (info.wallpaperId == 0L) {
				wallPaper.id = Utilities.random.nextLong()

				if (wallPaper.id > 0) {
					wallPaper.id = -wallPaper.id
				}
			}
			else {
				wallPaper.id = info.wallpaperId
			}

			wallPaper.dark = MotionBackgroundDrawable.isDark(info.color, info.gradientColor1, info.gradientColor2, info.gradientColor3)
			wallPaper.flags = wallPaper.flags or 4
			wallPaper.settings = TL_wallPaperSettings()
			wallPaper.settings.blur = info.isBlurred
			wallPaper.settings.motion = info.isMotion

			if (info.color != 0) {
				wallPaper.settings.background_color = info.color
				wallPaper.settings.flags = wallPaper.settings.flags or 1
				wallPaper.settings.intensity = (info.intensity * 100).toInt()
				wallPaper.settings.flags = wallPaper.settings.flags or 8
			}

			if (info.gradientColor1 != 0) {
				wallPaper.settings.second_background_color = info.gradientColor1
				wallPaper.settings.rotation = AndroidUtilities.getWallpaperRotation(info.rotation, true)
				wallPaper.settings.flags = wallPaper.settings.flags or 16
			}

			if (info.gradientColor2 != 0) {
				wallPaper.settings.third_background_color = info.gradientColor2
				wallPaper.settings.flags = wallPaper.settings.flags or 32
			}

			if (info.gradientColor3 != 0) {
				wallPaper.settings.fourth_background_color = info.gradientColor3
				wallPaper.settings.flags = wallPaper.settings.flags or 64
			}

			val arrayList = ArrayList<WallPaper>()
			arrayList.add(wallPaper)

			messagesStorage.putWallpapers(arrayList, -3)
			messagesStorage.getWallpapers()
		}
	}

	fun markDialogMessageAsDeleted(dialogId: Long, messages: List<Int>?) {
		if (messages.isNullOrEmpty()) {
			return
		}

		val obj = dialogMessage[dialogId]

		if (obj != null) {
			for (id in messages) {
				if (obj.id == id) {
					obj.deleted = true
					break
				}
			}
		}
	}

	@JvmOverloads
	fun deleteMessages(messages: List<Int>?, randoms: ArrayList<Long>?, encryptedChat: EncryptedChat?, dialogId: Long, forAll: Boolean, scheduled: Boolean, cacheOnly: Boolean = false, taskId: Long = 0, taskRequest: TLObject? = null) {
		if (messages.isNullOrEmpty() && taskId == 0L) {
			return
		}

		var toSend: ArrayList<Int>? = null
		val channelId: Long

		if (taskId == 0L) {
			channelId = if (dialogId != 0L && DialogObject.isChatDialog(dialogId)) {
				val chat = getChat(-dialogId)
				if (ChatObject.isChannel(chat)) chat.id else 0
			}
			else {
				0
			}

			if (!cacheOnly) {
				toSend = ArrayList()

				messages?.forEach {
					if (it > 0) {
						toSend.add(it)
					}
				}
			}

			if (scheduled) {
				messagesStorage.markMessagesAsDeleted(dialogId, messages, true, false, true)
			}
			else {
				if (channelId == 0L) {
					messages?.forEach {
						dialogMessagesByIds[it]?.deleted = true
					}
				}
				else {
					markDialogMessageAsDeleted(dialogId, messages)
				}

				messagesStorage.markMessagesAsDeleted(dialogId, messages, true, forAll, false)
				messagesStorage.updateDialogsWithDeletedMessages(dialogId, channelId, messages, null, true)
			}

			notificationCenter.postNotificationName(NotificationCenter.messagesDeleted, messages, channelId, scheduled)
		}
		else {
			channelId = if (taskRequest is TL_channels_deleteMessages) {
				taskRequest.channel.channel_id
			}
			else {
				0
			}
		}

		if (cacheOnly) {
			return
		}

		val newTaskId: Long

		if (scheduled) {
			val req: TL_messages_deleteScheduledMessages

			if (taskRequest is TL_messages_deleteScheduledMessages) {
				req = taskRequest
				newTaskId = taskId
			}
			else {
				req = TL_messages_deleteScheduledMessages()
				req.id = toSend
				req.peer = getInputPeer(dialogId)

				val data = try {
					NativeByteBuffer(12 + req.objectSize).apply {
						writeInt32(24)
						writeInt64(dialogId)
						req.serializeToStream(this)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
					null
				}

				newTaskId = messagesStorage.createPendingTask(data)
			}

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is Updates) {
					processUpdates(response, false)
				}

				if (newTaskId != 0L) {
					messagesStorage.removePendingTask(newTaskId)
				}
			}
		}
		else if (channelId != 0L) {
			val req: TL_channels_deleteMessages

			if (taskRequest != null) {
				req = taskRequest as TL_channels_deleteMessages
				newTaskId = taskId
			}
			else {
				req = TL_channels_deleteMessages()
				req.id = toSend
				req.channel = getInputChannel(channelId)

				val data = try {
					NativeByteBuffer(12 + req.objectSize).apply {
						writeInt32(24)
						writeInt64(dialogId)
						req.serializeToStream(this)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
					null
				}

				newTaskId = messagesStorage.createPendingTask(data)
			}

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_messages_affectedMessages) {
					processNewChannelDifferenceParams(response.pts, response.pts_count, channelId)
				}

				if (newTaskId != 0L) {
					messagesStorage.removePendingTask(newTaskId)
				}
			}
		}
		else {
			if (randoms != null && encryptedChat != null && randoms.isNotEmpty()) {
				secretChatHelper.sendMessagesDeleteMessage(encryptedChat, randoms, null)
			}

			val req: TL_messages_deleteMessages

			if (taskRequest is TL_messages_deleteMessages) {
				req = taskRequest
				newTaskId = taskId
			}
			else {
				req = TL_messages_deleteMessages()
				req.id = toSend
				req.revoke = forAll

				val data = try {
					NativeByteBuffer(12 + req.objectSize).apply {
						writeInt32(24)
						writeInt64(dialogId)
						req.serializeToStream(this)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
					null
				}

				newTaskId = messagesStorage.createPendingTask(data)
			}

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_messages_affectedMessages) {
					processNewDifferenceParams(-1, response.pts, -1, response.pts_count)
				}

				if (newTaskId != 0L) {
					messagesStorage.removePendingTask(newTaskId)
				}
			}
		}
	}

	fun unpinAllMessages(chat: Chat?, user: User?) {
		if (chat == null && user == null) {
			return
		}

		val peerId = (if (chat != null) -chat.id else user?.id) ?: return

		val req = TL_messages_unpinAllMessages()
		req.peer = getInputPeer(peerId)

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_messages_affectedHistory) {
				if (ChatObject.isChannel(chat)) {
					processNewChannelDifferenceParams(response.pts, response.pts_count, chat.id)
				}
				else {
					processNewDifferenceParams(-1, response.pts, -1, response.pts_count)
				}

				messagesStorage.updatePinnedMessages(peerId, null, false, 0, 0, false, null)
			}
		}
	}

	fun pinMessage(chat: Chat?, user: User?, id: Int, unpin: Boolean, oneSide: Boolean, notify: Boolean) {
		if (chat == null && user == null) {
			return
		}

		val peerId = (if (chat != null) -chat.id else user?.id) ?: return

		val req = TL_messages_updatePinnedMessage()
		req.peer = getInputPeer(peerId)
		req.id = id
		req.unpin = unpin
		req.silent = !notify
		req.pm_oneside = oneSide

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is Updates) {
				messagesStorage.updatePinnedMessages(peerId, listOf(id), !unpin, -1, 0, false, null)
				processUpdates(response, false)
			}
		}
	}

	fun deleteUserChannelHistory(currentChat: Chat, fromUser: User?, fromChat: Chat?, offset: Int) {
		var fromId = 0L

		if (fromUser != null) {
			fromId = fromUser.id
		}
		else if (fromChat != null) {
			fromId = fromChat.id
		}

		if (offset == 0) {
			messagesStorage.deleteUserChatHistory(-currentChat.id, fromId)
		}

		val req = TL_channels_deleteParticipantHistory()
		req.channel = getInputChannel(currentChat)
		req.participant = if (fromUser != null) getInputPeer(fromUser) else getInputPeer(fromChat)

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_messages_affectedHistory) {
				if (response.offset > 0) {
					deleteUserChannelHistory(currentChat, fromUser, fromChat, response.offset)
				}

				processNewChannelDifferenceParams(response.pts, response.pts_count, currentChat.id)
			}
		}
	}

	fun putDialogsEndReachedAfterRegistration() {
		dialogsEndReached.put(0, true)
		serverDialogsEndReached.put(0, true)
	}

	fun isDialogsEndReached(folderId: Int): Boolean {
		return dialogsEndReached[folderId]
	}

	fun isLoadingDialogs(folderId: Int): Boolean {
		return loadingDialogs[folderId]
	}

	fun isServerDialogsEndReached(folderId: Int): Boolean {
		return serverDialogsEndReached[folderId]
	}

	fun hasHiddenArchive(): Boolean {
		return SharedConfig.archiveHidden && dialogs_dict[DialogObject.makeFolderDialogId(1)] != null
	}

	fun getDialogs(folderId: Int): ArrayList<Dialog> {
		return dialogsByFolder[folderId] ?: return ArrayList()
	}

	val allFoldersDialogsCount: Int
		get() {
			var count = 0

			for (i in 0 until dialogsByFolder.size()) {
				val dialogs: List<Dialog?>? = dialogsByFolder[dialogsByFolder.keyAt(i)]

				if (dialogs != null) {
					count += dialogs.size
				}
			}

			return count
		}

	val totalDialogsCount: Int
		get() {
			var count = 0
			val dialogs = dialogsByFolder[0]

			if (dialogs != null) {
				count += dialogs.size
			}

			return count
		}

	fun putAllNeededDraftDialogs() {
		val drafts = mediaDataController.drafts
		var i = 0
		val size = drafts.size()

		while (i < size) {
			val threads = drafts.valueAt(i)
			val draftMessage = threads[0]

			if (draftMessage == null) {
				i++
				continue
			}

			putDraftDialogIfNeed(drafts.keyAt(i), draftMessage)

			i++
		}
	}

	fun putDraftDialogIfNeed(dialogId: Long, draftMessage: DraftMessage) {
		if (dialogs_dict.indexOfKey(dialogId) < 0) {
			val mediaDataController = mediaDataController
			val dialogsCount = allDialogs.size

			if (dialogsCount > 0) {
				val dialog = allDialogs[dialogsCount - 1]
				val minDate = DialogObject.getLastMessageOrDraftDate(dialog, mediaDataController.getDraft(dialog.id, 0))

				if (draftMessage.date < minDate) {
					return
				}
			}

			val dialog = TL_dialog()
			dialog.id = dialogId
			dialog.draft = draftMessage
			dialog.folder_id = mediaDataController.getDraftFolderId(dialogId)
			dialog.flags = if (dialogId < 0 && ChatObject.isChannel(getChat(-dialogId))) 1 else 0

			dialogs_dict.put(dialogId, dialog)
			allDialogs.add(dialog)

			sortDialogs(null)
		}
	}

	fun removeDraftDialogIfNeed(dialogId: Long) {
		val dialog = dialogs_dict[dialogId]

		if (dialog != null && dialog.top_message == 0) {
			dialogs_dict.remove(dialog.id)
			allDialogs.remove(dialog)
		}
	}

	private fun removeDialog(dialog: Dialog?) {
		if (dialog == null) {
			return
		}

		val did = dialog.id

		if (dialogsServerOnly.remove(dialog) && DialogObject.isChannel(dialog)) {
			Utilities.stageQueue.postRunnable {
				channelsPts.delete(-did)
				shortPollChannels.delete(-did)
				needShortPollChannels.remove(-did)
				shortPollOnlines.delete(-did)
				needShortPollOnlines.remove(-did)
			}
		}

		allDialogs.remove(dialog)
		dialogsMyChannels.remove(dialog)
		dialogsMyGroups.remove(dialog)
		dialogsCanAddUsers.remove(dialog)
		dialogsChannelsOnly.remove(dialog)
		dialogsGroupsOnly.remove(dialog)
		dialogsUsersOnly.remove(dialog)
		dialogsForBlock.remove(dialog)
		dialogsForward.remove(dialog)

		for (dialogFilter in selectedDialogFilter) {
			dialogFilter?.dialogs?.remove(dialog)
		}

		dialogs_dict.remove(did)

		val dialogs = dialogsByFolder[dialog.folder_id]

		dialogs?.remove(dialog)
	}

	fun hidePromoDialog() {
		val promoDialog = promoDialog ?: return

		val req = TL_help_hidePromoData()
		req.peer = getInputPeer(promoDialog.id)

		connectionsManager.sendRequest(req)

		Utilities.stageQueue.postRunnable {
			promoDialogId = 0
			proxyDialogAddress = null
			nextPromoInfoCheckTime = connectionsManager.currentTime + 60 * 60
			mainPreferences.edit().putLong("proxy_dialog", promoDialogId).remove("proxyDialogAddress").putInt("nextPromoInfoCheckTime", nextPromoInfoCheckTime).commit()
		}

		removePromoDialog()
	}

	@JvmOverloads
	fun deleteDialog(did: Long, onlyHistory: Int, revoke: Boolean = false) {
		deleteDialog(did, 1, onlyHistory, 0, revoke, null, 0)
	}

	fun setDialogHistoryTTL(did: Long, ttl: Int) {
		val req = TL_messages_setHistoryTTL()
		req.peer = getInputPeer(did)
		req.period = ttl

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is Updates) {
				processUpdates(response, false)
			}
		}

		var chatFull: ChatFull? = null
		var userFull: UserFull? = null

		if (did > 0) {
			userFull = getUserFull(did)

			if (userFull == null) {
				return
			}

			userFull.ttl_period = ttl
			userFull.flags = userFull.flags or 16384
		}
		else {
			chatFull = getChatFull(-did)

			if (chatFull == null) {
				return
			}

			chatFull.ttl_period = ttl

			if (chatFull is TL_channelFull) {
				chatFull.flags = chatFull.flags or 16777216
			}
			else {
				chatFull.flags = chatFull.flags or 16384
			}
		}

		if (chatFull != null) {
			notificationCenter.postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false)
		}
		else {
			notificationCenter.postNotificationName(NotificationCenter.userInfoDidLoad, did, userFull)
		}
	}

	fun setDialogsInTransaction(transaction: Boolean) {
		dialogsInTransaction = transaction

		if (!transaction) {
			notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload, true)
		}
	}

	fun deleteDialog(did: Long, first: Int, onlyHistory: Int, maxId: Int, revoke: Boolean, peer: InputPeer?, taskId: Long) {
		@Suppress("NAME_SHADOWING") var peer = peer

		if (onlyHistory == 2) {
			messagesStorage.deleteDialog(did, onlyHistory)
			return
		}

		for (i in 0 until sendAsPeers.size()) {
			val sendAsInfo = sendAsPeers.valueAt(i)
			val peers = sendAsInfo.sendAsPeers

			if (peers != null) {
				for (j in peers.chats.indices) {
					if (peers.chats[j].id == -did) {
						peers.chats.removeAt(j)
						break
					}
				}

				for (j in peers.peers.indices) {
					if (peers.peers[j].peer.channel_id == -did || peers.peers[j].peer.chat_id == -did) {
						peers.peers.removeAt(j)
						break
					}
				}
			}
		}

		sendAsPeers.remove(did)

		if (first == 1 && maxId == 0) {
			val peerFinal = peer

			messagesStorage.getDialogMaxMessageId(did) {
				AndroidUtilities.runOnUIThread {
					deleteDialog(did, 2, onlyHistory, max(0, it), revoke, peerFinal, taskId)
					checkIfFolderEmpty(1)
				}
			}

			return
		}

		if (onlyHistory == 0 || onlyHistory == 3) {
			mediaDataController.uninstallShortcut(did)
		}

		var maxIdDelete = maxId

		if (first != 0) {
			var isPromoDialog = false

			messagesStorage.deleteDialog(did, onlyHistory)

			val dialog = dialogs_dict[did]

			if (onlyHistory == 0 || onlyHistory == 3) {
				notificationCenter.postNotificationName(NotificationCenter.dialogDeleted, did)
				notificationsController.deleteNotificationChannel(did)

				JoinCallAlert.processDeletedChat(currentAccount, did)
			}

			if (onlyHistory == 0) {
				mediaDataController.cleanDraft(did, 0, false)
			}

			if (dialog != null) {
				if (first == 2) {
					maxIdDelete = max(0, dialog.top_message)
					maxIdDelete = max(maxIdDelete, dialog.read_inbox_max_id)
					maxIdDelete = max(maxIdDelete, dialog.read_outbox_max_id)
				}

				if (onlyHistory == 0 || onlyHistory == 3) {
					if ((promoDialog != null && promoDialog?.id == did).also { isPromoDialog = it }) {
						isLeftPromoChannel = true

						if (promoDialog!!.id < 0) {
							val chat = getChat(-promoDialog!!.id)

							if (chat != null) {
								chat.left = true
							}
						}

						sortDialogs(null)
					}
					else {
						removeDialog(dialog)

						val offset = nextDialogsCacheOffset[dialog.folder_id, 0]

						if (offset > 0) {
							nextDialogsCacheOffset.put(dialog.folder_id, offset - 1)
						}
					}
				}
				else {
					dialog.unread_count = 0
				}

				if (!isPromoDialog) {
					val lastMessageId: Int
					var `object` = dialogMessage[dialog.id]

					dialogMessage.remove(dialog.id)

					if (`object` != null) {
						lastMessageId = `object`.id

						if (`object`.messageOwner?.peer_id?.channel_id == 0L) {
							dialogMessagesByIds.remove(`object`.id)
						}
					}
					else {
						lastMessageId = dialog.top_message
						`object` = dialogMessagesByIds[dialog.top_message]

						if (`object` != null && `object`.messageOwner?.peer_id?.channel_id == 0L) {
							dialogMessagesByIds.remove(dialog.top_message)
						}
					}

					if (`object` != null && `object`.messageOwner?.random_id != 0L) {
						dialogMessagesByRandomIds.remove(`object`.messageOwner!!.random_id)
					}

					if (onlyHistory == 1 && !DialogObject.isEncryptedDialog(did) && lastMessageId > 0) {
						val message = TL_messageService()
						message.id = dialog.top_message
						message.out = userConfig.getClientUserId() == did
						message.from_id = TL_peerUser()
						message.from_id?.user_id = userConfig.getClientUserId()
						message.flags = message.flags or 256
						message.action = TL_messageActionHistoryClear()
						message.date = dialog.last_message_date
						message.dialog_id = did
						message.peer_id = getPeer(did)

						val isDialogCreated = createdDialogIds.contains(message.dialog_id)
						val obj = MessageObject(currentAccount, message, isDialogCreated, isDialogCreated)

						updateInterfaceWithMessages(did, listOf(obj), false)

						messagesStorage.putMessages(listOf(message), false, true, false, 0, false)
					}
					else {
						dialog.top_message = 0
					}
				}
			}
			if (first == 2) {
				var max = dialogs_read_inbox_max[did]

				if (max != null) {
					maxIdDelete = max(max, maxIdDelete)
				}

				max = dialogs_read_outbox_max[did]

				if (max != null) {
					maxIdDelete = max(max, maxIdDelete)
				}
			}

			if (!dialogsInTransaction) {
				if (isPromoDialog) {
					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload, true)
				}
				else {
					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
					notificationCenter.postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, false, null)
				}
			}

			messagesStorage.storageQueue.postRunnable {
				AndroidUtilities.runOnUIThread {
					notificationsController.removeNotificationsForDialog(did)
				}
			}
		}

		if (onlyHistory == 3) {
			return
		}

		if (!DialogObject.isEncryptedDialog(did)) {
			if (peer == null) {
				peer = getInputPeer(did)
			}

			val newTaskId: Long

			if (peer !is TL_inputPeerChannel || onlyHistory != 0) {
				if (maxIdDelete > 0 && maxIdDelete != Int.MAX_VALUE) {
					val current = deletedHistory[did, 0]
					deletedHistory.put(did, max(current, maxIdDelete))
				}

				if (taskId == 0L) {
					var data: NativeByteBuffer? = null

					try {
						data = NativeByteBuffer(4 + 8 + 4 + 4 + 4 + 4 + peer.objectSize)
						data.writeInt32(13)
						data.writeInt64(did)
						data.writeBool(first != 0)
						data.writeInt32(onlyHistory)
						data.writeInt32(maxIdDelete)
						data.writeBool(revoke)

						peer.serializeToStream(data)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					newTaskId = messagesStorage.createPendingTask(data)
				}
				else {
					newTaskId = taskId
				}
			}
			else {
				newTaskId = taskId
			}

			if (peer is TL_inputPeerChannel) {
				if (onlyHistory == 0) {
					if (newTaskId != 0L) {
						messagesStorage.removePendingTask(newTaskId)
					}

					return
				}

				val req = TL_channels_deleteHistory()
				req.channel = TL_inputChannel()
				req.for_everyone = revoke
				req.channel.channel_id = peer.channel_id
				req.channel.access_hash = peer.access_hash
				req.max_id = if (maxIdDelete > 0) maxIdDelete else Int.MAX_VALUE

				connectionsManager.sendRequest(req, { response, _ ->
					if (newTaskId != 0L) {
						messagesStorage.removePendingTask(newTaskId)
					}

					if (response is Updates) {
						processUpdates(response, false)
					}
				}, ConnectionsManager.RequestFlagInvokeAfter)
			}
			else {
				val req = TL_messages_deleteHistory()
				req.peer = peer
				req.max_id = if (maxIdDelete > 0) maxIdDelete else Int.MAX_VALUE
				req.just_clear = onlyHistory != 0
				req.revoke = revoke

				connectionsManager.sendRequest(req, { response, _ ->
					if (newTaskId != 0L) {
						messagesStorage.removePendingTask(newTaskId)
					}

					if (response is TL_messages_affectedHistory) {
						if (response.offset > 0) {
							deleteDialog(did, 0, onlyHistory, maxIdDelete, revoke, peer, 0)
						}

						processNewDifferenceParams(-1, response.pts, -1, response.pts_count)

						messagesStorage.onDeleteQueryComplete(did)
					}
				}, ConnectionsManager.RequestFlagInvokeAfter)
			}
		}
		else {
			val encryptedId = DialogObject.getEncryptedChatId(did)

			if (onlyHistory == 1) {
				secretChatHelper.sendClearHistoryMessage(getEncryptedChat(encryptedId), null)
			}
			else {
				secretChatHelper.declineSecretChat(encryptedId, revoke)
			}
		}
	}

	fun saveGif(parentObject: Any?, document: Document?) {
		if (parentObject == null || !MessageObject.isGifDocument(document)) {
			return
		}

		val req = TL_messages_saveGif()
		req.id = TL_inputDocument()
		req.id?.id = document.id
		req.id?.access_hash = document.access_hash
		req.id?.file_reference = document.file_reference

		if (req.id?.file_reference == null) {
			req.id?.file_reference = ByteArray(0)
		}

		req.unsave = false

		connectionsManager.sendRequest(req) { _, error ->
			if (error != null && FileRefController.isFileRefError(error.text)) {
				fileRefController.requestReference(parentObject, req)
			}
		}
	}

	fun saveRecentSticker(parentObject: Any?, document: Document?, asMask: Boolean) {
		if (parentObject == null || document == null) {
			return
		}

		val req = TL_messages_saveRecentSticker()
		req.id = TL_inputDocument()
		req.id.id = document.id
		req.id.access_hash = document.access_hash
		req.id.file_reference = document.file_reference

		if (req.id.file_reference == null) {
			req.id.file_reference = ByteArray(0)
		}

		req.unsave = false
		req.attached = asMask

		connectionsManager.sendRequest(req) { _, error ->
			if (error != null && FileRefController.isFileRefError(error.text)) {
				fileRefController.requestReference(parentObject, req)
			}
		}
	}

	fun loadChannelParticipants(chatId: Long) {
		if (loadingFullParticipants.contains(chatId) || loadedFullParticipants.contains(chatId)) {
			return
		}

		loadingFullParticipants.add(chatId)

		val req = TL_channels_getParticipants()
		req.channel = getInputChannel(chatId)
		req.filter = TL_channelParticipantsRecent()
		req.offset = 0
		req.limit = 32

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is TL_channels_channelParticipants) {
					putUsers(response.users, false)
					putChats(response.chats, false)

					messagesStorage.putUsersAndChats(response.users, response.chats, true, true)
					messagesStorage.updateChannelUsers(chatId, response.participants)

					loadedFullParticipants.add(chatId)
				}

				loadingFullParticipants.remove(chatId)
			}
		}
	}

	fun putChatFull(chatFull: ChatFull) {
		fullChats.put(chatFull.id, chatFull)
	}

	fun processChatInfo(chatId: Long, info: ChatFull?, usersArr: List<User>?, fromCache: Boolean, force: Boolean, byChannelUsers: Boolean, pinnedMessages: List<Int>?, pinnedMessagesMap: HashMap<Int, MessageObject>?, totalPinnedCount: Int, pinnedEndReached: Boolean) {
		AndroidUtilities.runOnUIThread {
			if (fromCache && chatId > 0 && !byChannelUsers) {
				loadFullChat(chatId, 0, force)
			}

			if (info != null) {
				if (fullChats[chatId] == null) {
					fullChats.put(chatId, info)
				}

				putUsers(usersArr, fromCache)

				if (info.stickerset != null) {
					mediaDataController.getGroupStickerSetById(info.stickerset)
				}

				notificationCenter.postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, byChannelUsers, false)
			}

			if (pinnedMessages != null) {
				notificationCenter.postNotificationName(NotificationCenter.pinnedInfoDidLoad, -chatId, pinnedMessages, pinnedMessagesMap, totalPinnedCount, pinnedEndReached)
			}
		}
	}

	fun loadUserInfo(user: User?, force: Boolean, classGuid: Int) {
		messagesStorage.loadUserInfo(user, force, classGuid)
	}

	fun processUserInfo(user: User, info: UserFull?, fromCache: Boolean, force: Boolean, classGuid: Int, pinnedMessages: ArrayList<Int>?, pinnedMessagesMap: HashMap<Int, MessageObject>?, totalPinnedCount: Int, pinnedEndReached: Boolean) {
		AndroidUtilities.runOnUIThread {
			if (fromCache) {
				loadFullUser(user, classGuid, force)
			}

			if (info != null) {
				if (fullUsers[user.id] == null) {
					fullUsers.put(user.id, info)

					val index = blockedPeers.indexOfKey(user.id)

					if (info.blocked) {
						if (index < 0) {
							blockedPeers.put(user.id, 1)
							notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)
						}
					}
					else {
						if (index >= 0) {
							blockedPeers.removeAt(index)
							notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)
						}
					}
				}

				notificationCenter.postNotificationName(NotificationCenter.userInfoDidLoad, user.id, info)
			}

			if (pinnedMessages != null) {
				notificationCenter.postNotificationName(NotificationCenter.pinnedInfoDidLoad, user.id, pinnedMessages, pinnedMessagesMap, totalPinnedCount, pinnedEndReached)
			}
		}
	}

	fun updateTimerProc() {
		val currentTime = System.currentTimeMillis()

		checkDeletingTask(false)
		checkReadTasks()

		if (userConfig.isClientActivated) {
			if (!ignoreSetOnline && connectionsManager.pauseTime == 0L && ApplicationLoader.isScreenOn && !ApplicationLoader.mainInterfacePausedStageQueue) {
				if (ApplicationLoader.mainInterfacePausedStageQueueTime != 0L && abs(ApplicationLoader.mainInterfacePausedStageQueueTime - System.currentTimeMillis()) > 1000) {
					if (statusSettingState != 1 && (lastStatusUpdateTime == 0L || abs(System.currentTimeMillis() - lastStatusUpdateTime) >= 55000 || offlineSent)) {
						statusSettingState = 1

						if (statusRequest != 0) {
							connectionsManager.cancelRequest(statusRequest, true)
						}

						val req = TL_account_updateStatus()
						req.offline = false

						statusRequest = connectionsManager.sendRequest(req) { _, error ->
							if (error == null) {
								lastStatusUpdateTime = System.currentTimeMillis()
								offlineSent = false
								statusSettingState = 0
							}
							else {
								if (lastStatusUpdateTime != 0L) {
									lastStatusUpdateTime += 5000
								}
							}

							statusRequest = 0
						}
					}
				}
			}
			else if (statusSettingState != 2 && !offlineSent && abs(System.currentTimeMillis() - connectionsManager.pauseTime) >= 2000) {
				statusSettingState = 2

				if (statusRequest != 0) {
					connectionsManager.cancelRequest(statusRequest, true)
				}

				val req = TL_account_updateStatus()
				req.offline = true

				statusRequest = connectionsManager.sendRequest(req) { _, error ->
					if (error == null) {
						offlineSent = true
					}
					else {
						if (lastStatusUpdateTime != 0L) {
							lastStatusUpdateTime += 5000
						}
					}

					statusRequest = 0
				}
			}

			if (updatesQueueChannels.size() != 0) {
				for (a in 0 until updatesQueueChannels.size()) {
					val key = updatesQueueChannels.keyAt(a)
					val updatesStartWaitTime = updatesStartWaitTimeChannels.valueAt(a)

					if (abs(currentTime - updatesStartWaitTime) >= 1500) {
						processChannelsUpdatesQueue(key, 0)
					}
				}
			}

			for (a in 0..2) {
				if (getUpdatesStartTime(a) != 0L && abs(currentTime - getUpdatesStartTime(a)) >= 1500) {
					processUpdatesQueue(a, 0)
				}
			}
		}

		val currentServerTime = connectionsManager.currentTime

		if (abs(System.currentTimeMillis() - lastViewsCheckTime) >= 5000) {
			lastViewsCheckTime = System.currentTimeMillis()

			if (channelViewsToSend.size() != 0) {
				for (a in 0 until channelViewsToSend.size()) {
					val key = channelViewsToSend.keyAt(a)

					val req = TL_messages_getMessagesViews()
					req.peer = getInputPeer(key)
					req.id = ArrayList(channelViewsToSend.valueAt(a))
					req.increment = a == 0

					connectionsManager.sendRequest(req) { response, _ ->
						if (response is TL_messages_messageViews) {
							val channelViews = LongSparseArray<SparseIntArray>()
							val channelForwards = LongSparseArray<SparseIntArray>()
							val channelReplies = LongSparseArray<SparseArray<MessageReplies>>()
							var views = channelViews[key]
							var forwards = channelForwards[key]
							var replies = channelReplies[key]

							for (a1 in req.id.indices) {
								if (a1 >= response.views.size) {
									break
								}

								val messageViews = response.views[a1]

								if (messageViews.flags and 1 != 0) {
									if (views == null) {
										views = SparseIntArray()
										channelViews.put(key, views)
									}

									views.put(req.id[a1], messageViews.views)
								}

								if (messageViews.flags and 2 != 0) {
									if (forwards == null) {
										forwards = SparseIntArray()
										channelForwards.put(key, forwards)
									}

									forwards.put(req.id[a1], messageViews.forwards)
								}

								if (messageViews.flags and 4 != 0) {
									if (replies == null) {
										replies = SparseArray()
										channelReplies.put(key, replies)
									}

									replies.put(req.id[a1], messageViews.replies)
								}
							}

							messagesStorage.putUsersAndChats(response.users, response.chats, true, true)
							messagesStorage.putChannelViews(channelViews, channelForwards, channelReplies, false)

							AndroidUtilities.runOnUIThread {
								putUsers(response.users, false)
								putChats(response.chats, false)

								notificationCenter.postNotificationName(NotificationCenter.didUpdateMessagesViews, channelViews, channelForwards, channelReplies, false)
							}
						}
					}
				}

				channelViewsToSend.clear()
			}

			if (pollsToCheckSize > 0) {
				AndroidUtilities.runOnUIThread {
					val time = SystemClock.elapsedRealtime()
					var minExpireTime = Int.MAX_VALUE
					var a = 0
					var n = pollsToCheck.size()

					while (a < n) {
						val array = pollsToCheck.valueAt(a)

						if (array == null) {
							a++
							continue
						}

						var b = 0
						var n2 = array.size()

						while (b < n2) {
							val messageObject = array.valueAt(b)
							val mediaPoll = messageObject.messageOwner?.media as TL_messageMediaPoll
							var timeout = 30000
							var expired: Boolean

							if ((mediaPoll.poll.close_date != 0 && !mediaPoll.poll.closed).also { expired = it }) {
								if (mediaPoll.poll.close_date <= currentServerTime) {
									timeout = 1000
								}
								else {
									minExpireTime = min(minExpireTime, mediaPoll.poll.close_date - currentServerTime)
								}
							}

							if (abs(time - messageObject.pollLastCheckTime) < timeout) {
								if (!messageObject.pollVisibleOnScreen && !expired) {
									array.remove(messageObject.id)
									n2--
									b--
								}
							}
							else {
								messageObject.pollLastCheckTime = time

								val req = TL_messages_getPollResults()
								req.peer = getInputPeer(messageObject.dialogId)
								req.msg_id = messageObject.id

								connectionsManager.sendRequest(req) { response, _ ->
									if (response is Updates) {
										if (expired) {
											for (i in response.updates.indices) {
												val update = response.updates[i]

												if (update is TL_updateMessagePoll) {
													if (update.poll != null && !update.poll.closed) {
														lastViewsCheckTime = System.currentTimeMillis() - 4000
													}
												}
											}
										}

										processUpdates(response, false)
									}
								}
							}

							b++
						}

						if (minExpireTime < 5) {
							lastViewsCheckTime = min(lastViewsCheckTime, System.currentTimeMillis() - (5L - minExpireTime) * 1000L)
						}

						if (array.size() == 0) {
							pollsToCheck.remove(pollsToCheck.keyAt(a))
							n--
							a--
						}

						a++
					}

					pollsToCheckSize = pollsToCheck.size()
				}
			}
		}

		if (!onlinePrivacy.isEmpty()) {
			var toRemove: ArrayList<Long>? = null

			for ((key, value) in onlinePrivacy) {
				if (value < currentServerTime - 30) {
					if (toRemove == null) {
						toRemove = ArrayList()
					}

					toRemove.add(key)
				}
			}

			if (toRemove != null) {
				for (uid in toRemove) {
					onlinePrivacy.remove(uid)
				}

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS)
				}
			}
		}

		if (shortPollChannels.size() != 0) {
			var a = 0

			while (a < shortPollChannels.size()) {
				val key = shortPollChannels.keyAt(a)
				val timeout = shortPollChannels.valueAt(a)

				if (timeout < System.currentTimeMillis() / 1000) {
					shortPollChannels.delete(key)
					a--

					if (needShortPollChannels.indexOfKey(key) >= 0) {
						getChannelDifference(key)
					}
				}

				a++
			}
		}

		if (shortPollOnlines.size() != 0) {
			val time = SystemClock.elapsedRealtime() / 1000
			var a = 0

			while (a < shortPollOnlines.size()) {
				val key = shortPollOnlines.keyAt(a)
				val timeout = shortPollOnlines.valueAt(a)

				if (timeout < time) {
					if (needShortPollChannels.indexOfKey(key) >= 0) {
						shortPollOnlines.put(key, (time + 60 * 5).toInt())
					}
					else {
						shortPollOnlines.delete(key)
						a--
					}

					val req = TL_messages_getOnlines()
					req.peer = getInputPeer(-key)

					connectionsManager.sendRequest(req) { response, _ ->
						if (response is TL_chatOnlines) {
							messagesStorage.updateChatOnlineCount(key, response.onlines)

							AndroidUtilities.runOnUIThread {
								notificationCenter.postNotificationName(NotificationCenter.chatOnlineCountDidLoad, key, response.onlines)
							}
						}
					}
				}

				a++
			}
		}

		if (!printingUsers.isEmpty() || lastPrintingStringCount != printingUsers.size) {
			var updated = false
			val dialogKeys = ArrayList(printingUsers.keys)
			var b = 0

			while (b < dialogKeys.size) {
				val dialogKey = dialogKeys[b]
				val threads = printingUsers[dialogKey]

				if (threads != null) {
					val threadKeys = ArrayList(threads.keys)
					var c = 0

					while (c < threadKeys.size) {
						val threadKey = threadKeys[c]
						val arr = threads[threadKey]

						if (arr != null) {
							var a = 0

							while (a < arr.size) {
								val user = arr[a]

								val timeToRemove = if (user.action is TL_sendMessageGamePlayAction) {
									30000
								}
								else {
									5900
								}

								if (user.lastTime + timeToRemove < currentTime) {
									updated = true
									arr.remove(user)
									a--
								}

								a++
							}
						}

						if (arr.isNullOrEmpty()) {
							threads.remove(threadKey)
							threadKeys.removeAt(c)
							c--
						}

						c++
					}
				}

				if (threads.isNullOrEmpty()) {
					printingUsers.remove(dialogKey)
					dialogKeys.removeAt(b)
					b--
				}

				b++
			}

			updatePrintingStrings()

			if (updated) {
				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT)
				}
			}
		}

		if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED && abs(currentTime - lastThemeCheckTime) >= 60) {
			AndroidUtilities.runOnUIThread(themeCheckRunnable)
			lastThemeCheckTime = currentTime
		}

		if (userConfig.savedPasswordHash != null && abs(currentTime - lastPasswordCheckTime) >= 60) {
			AndroidUtilities.runOnUIThread(passwordCheckRunnable)
			lastPasswordCheckTime = currentTime
		}

		if (lastPushRegisterSendTime != 0L && abs(SystemClock.elapsedRealtime() - lastPushRegisterSendTime) >= 3 * 60 * 60 * 1000) {
			PushListenerController.sendRegistrationToServer(SharedConfig.pushType, SharedConfig.pushString)
		}

		locationController.update()

		checkPromoInfoInternal(false)
		checkTosUpdate()
	}

	private fun checkTosUpdate() {
		if (nextTosCheckTime > connectionsManager.currentTime || checkingTosUpdate || !userConfig.isClientActivated) {
			return
		}

		checkingTosUpdate = true

		val req = TL_help_getTermsOfServiceUpdate()

		connectionsManager.sendRequest(req) { response, _ ->
			checkingTosUpdate = false

			when (response) {
				is TL_help_termsOfServiceUpdateEmpty -> {
					nextTosCheckTime = response.expires
				}

				is TL_help_termsOfServiceUpdate -> {
					nextTosCheckTime = response.expires

					AndroidUtilities.runOnUIThread {
						notificationCenter.postNotificationName(NotificationCenter.needShowAlert, AlertDialog.AlertReason.TERMS_UPDATED, response.terms_of_service)
					}
				}

				else -> {
					nextTosCheckTime = connectionsManager.currentTime + 60 * 60
				}
			}

			notificationsPreferences.edit().putInt("nextTosCheckTime", nextTosCheckTime).commit()
		}
	}

	fun checkPromoInfo(reset: Boolean) {
		Utilities.stageQueue.postRunnable {
			checkPromoInfoInternal(reset)
		}
	}

	private fun checkPromoInfoInternal(reset: Boolean) {
		if (reset && checkingPromoInfo) {
			checkingPromoInfo = false
		}

		if (!reset && nextPromoInfoCheckTime > connectionsManager.currentTime || checkingPromoInfo) {
			return
		}

		if (checkingPromoInfoRequestId != 0) {
			connectionsManager.cancelRequest(checkingPromoInfoRequestId, true)
			checkingPromoInfoRequestId = 0
		}

		val preferences: SharedPreferences = getGlobalMainSettings()
		// boolean enabled = preferences.getBoolean("proxy_enabled", false);
		val proxyAddress = preferences.getString("proxy_ip", "")
		val proxySecret = preferences.getString("proxy_secret", "")
		var removeCurrent = 0

		if (promoDialogId != 0L && promoDialogType == PROMO_TYPE_PROXY && proxyDialogAddress != null && proxyDialogAddress != proxyAddress + proxySecret) {
			removeCurrent = 1
		}

		lastCheckPromoId++
		checkingPromoInfo = true

		val checkPromoId = lastCheckPromoId

		val req = TL_help_getPromoData()

		checkingPromoInfoRequestId = connectionsManager.sendRequest(req) { response, _ ->
			if (checkPromoId != lastCheckPromoId) {
				return@sendRequest
			}

			var noDialog = false

			if (response is TL_help_promoDataEmpty) {
				nextPromoInfoCheckTime = response.expires
				noDialog = true
			}
			else if (response is TL_help_promoData) {
				val did: Long

				if (response.peer.user_id != 0L) {
					did = response.peer.user_id
				}
				else if (response.peer.chat_id != 0L) {
					did = -response.peer.chat_id

					for (a in response.chats.indices) {
						val chat = response.chats[a]

						if (chat.id == response.peer.chat_id) {
							if (chat.kicked || chat.restricted) {
								noDialog = true
							}

							break
						}
					}
				}
				else {
					did = -response.peer.channel_id

					for (a in response.chats.indices) {
						val chat = response.chats[a]

						if (chat.id == response.peer.channel_id) {
							if (chat.kicked || chat.restricted) {
								noDialog = true
							}

							break
						}
					}
				}

				promoDialogId = did

				if (response.proxy) {
					promoDialogType = PROMO_TYPE_PROXY
				}
				else if (!TextUtils.isEmpty(response.psa_type)) {
					promoDialogType = PROMO_TYPE_PSA
					promoPsaType = response.psa_type
				}
				else {
					promoDialogType = PROMO_TYPE_OTHER
				}

				proxyDialogAddress = proxyAddress + proxySecret
				promoPsaMessage = response.psa_message
				nextPromoInfoCheckTime = response.expires

				val editor: SharedPreferences.Editor = getGlobalMainSettings().edit()
				editor.putLong("proxy_dialog", promoDialogId)
				editor.putString("proxyDialogAddress", proxyDialogAddress)
				editor.putInt("promo_dialog_type", promoDialogType)

				if (promoPsaMessage != null) {
					editor.putString("promo_psa_message", promoPsaMessage)
				}
				else {
					editor.remove("promo_psa_message")
				}

				if (promoPsaType != null) {
					editor.putString("promo_psa_type", promoPsaType)
				}
				else {
					editor.remove("promo_psa_type")
				}

				editor.putInt("nextPromoInfoCheckTime", nextPromoInfoCheckTime)
				editor.commit()

				if (!noDialog) {
					AndroidUtilities.runOnUIThread {
						if (promoDialog != null && did != promoDialog!!.id) {
							removePromoDialog()
						}

						promoDialog = dialogs_dict[did]

						if (promoDialog != null) {
							checkingPromoInfo = false
							sortDialogs(null)
							notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload, true)
						}
						else {
							val usersDict = LongSparseArray<User>()
							val chatsDict = LongSparseArray<Chat>()

							for (a in response.users.indices) {
								val u = response.users[a]
								usersDict.put(u.id, u)
							}

							for (a in response.chats.indices) {
								val c = response.chats[a]
								chatsDict.put(c.id, c)
							}

							val req1 = TL_messages_getPeerDialogs()
							val peer = TL_inputDialogPeer()

							if (response.peer.user_id != 0L) {
								peer.peer = TL_inputPeerUser()
								peer.peer.user_id = response.peer.user_id

								val user = usersDict[response.peer.user_id]

								if (user != null) {
									peer.peer.access_hash = user.access_hash
								}
							}
							else if (response.peer.chat_id != 0L) {
								peer.peer = TL_inputPeerChat()
								peer.peer.chat_id = response.peer.chat_id

								val chat = chatsDict[response.peer.chat_id]

								if (chat != null) {
									peer.peer.access_hash = chat.access_hash
								}
							}
							else {
								peer.peer = TL_inputPeerChannel()
								peer.peer.channel_id = response.peer.channel_id

								val chat = chatsDict[response.peer.channel_id]

								if (chat != null) {
									peer.peer.access_hash = chat.access_hash
								}
							}

							req1.peers.add(peer)

							checkingPromoInfoRequestId = connectionsManager.sendRequest(req1) { response1, _ ->
								if (checkPromoId != lastCheckPromoId) {
									return@sendRequest
								}

								checkingPromoInfoRequestId = 0

								val res2 = response1 as? TL_messages_peerDialogs

								if (res2 != null && res2.dialogs.isNotEmpty()) {
									messagesStorage.putUsersAndChats(response.users, response.chats, true, true)

									val dialogs = TL_messages_dialogs()
									dialogs.chats = res2.chats
									dialogs.users = res2.users
									dialogs.dialogs = res2.dialogs
									dialogs.messages = res2.messages

									messagesStorage.putDialogs(dialogs, 2)

									AndroidUtilities.runOnUIThread {
										putUsers(response.users, false)
										putChats(response.chats, false)
										putUsers(res2.users, false)
										putChats(res2.chats, false)

										promoDialog?.let { promoDialog ->
											if (promoDialog.id < 0) {
												val chat = getChat(-promoDialog.id)

												if (ChatObject.isNotInChat(chat) || chat?.restricted == true) {
													removeDialog(promoDialog)
												}
											}
											else {
												removeDialog(promoDialog)
											}
										}

										promoDialog = res2.dialogs[0]
										promoDialog?.id = did
										promoDialog?.folder_id = 0

										if (DialogObject.isChannel(promoDialog)) {
											channelsPts.put(-promoDialog!!.id, promoDialog!!.pts)
										}

										var value = dialogs_read_inbox_max[promoDialog?.id]

										if (value == null) {
											value = 0
										}

										dialogs_read_inbox_max[promoDialog!!.id] = max(value, promoDialog!!.read_inbox_max_id)

										value = dialogs_read_outbox_max[promoDialog!!.id]

										if (value == null) {
											value = 0
										}

										dialogs_read_outbox_max[promoDialog!!.id] = max(value, promoDialog!!.read_outbox_max_id)
										dialogs_dict.put(did, promoDialog)

										if (res2.messages.isNotEmpty()) {
											val usersDict1 = LongSparseArray<User>()
											val chatsDict1 = LongSparseArray<Chat>()

											for (a in res2.users.indices) {
												val u = res2.users[a]
												usersDict1.put(u.id, u)
											}

											for (a in res2.chats.indices) {
												val c = res2.chats[a]
												chatsDict1.put(c.id, c)
											}

											val messageObject = MessageObject(currentAccount, res2.messages[0], usersDict1, chatsDict1, generateLayout = false, checkMediaExists = true)

											dialogMessage.put(did, messageObject)

											if (promoDialog?.last_message_date == 0) {
												promoDialog?.last_message_date = messageObject.messageOwner!!.date
											}
										}

										sortDialogs(null)

										notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload, true)
									}
								}
								else {
									AndroidUtilities.runOnUIThread {
										promoDialog?.let {
											if (it.id < 0) {
												val chat = getChat(-it.id)

												if (ChatObject.isNotInChat(chat) || chat?.restricted == true) {
													removeDialog(promoDialog)
												}
											}
											else {
												removeDialog(promoDialog)
											}

											promoDialog = null

											sortDialogs(null)

											notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
										}
									}
								}

								checkingPromoInfo = false
							}
						}
					}
				}
			}
			else {
				nextPromoInfoCheckTime = connectionsManager.currentTime + 60 * 60
				noDialog = true
			}

			if (noDialog) {
				promoDialogId = 0
				getGlobalMainSettings().edit().putLong("proxy_dialog", promoDialogId).remove("proxyDialogAddress").putInt("nextPromoInfoCheckTime", nextPromoInfoCheckTime).commit()
				checkingPromoInfoRequestId = 0
				checkingPromoInfo = false

				AndroidUtilities.runOnUIThread {
					removePromoDialog()
				}
			}
		}

		if (removeCurrent != 0) {
			promoDialogId = 0
			proxyDialogAddress = null
			nextPromoInfoCheckTime = connectionsManager.currentTime + 60 * 60
			getGlobalMainSettings().edit().putLong("proxy_dialog", promoDialogId).remove("proxyDialogAddress").putInt("nextPromoInfoCheckTime", nextPromoInfoCheckTime).commit()

			AndroidUtilities.runOnUIThread {
				removePromoDialog()
			}
		}
	}

	private fun removePromoDialog() {
		val promoDialog = promoDialog ?: return

		if (promoDialog.id < 0) {
			val chat = getChat(-promoDialog.id)

			if (ChatObject.isNotInChat(chat) || chat?.restricted == true) {
				removeDialog(promoDialog)
			}
		}
		else {
			removeDialog(promoDialog)
		}

		this.promoDialog = null

		sortDialogs(null)

		notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
	}

	fun isPromoDialog(did: Long, checkLeft: Boolean): Boolean {
		return promoDialog?.id == did && (!checkLeft || isLeftPromoChannel)
	}

	private fun getUserNameForTyping(user: User?): String? {
		if (user == null) {
			return ""
		}

		return if (!user.first_name.isNullOrEmpty()) {
			user.first_name
		}
		else if (!user.last_name.isNullOrEmpty()) {
			user.last_name
		}
		else {
			""
		}
	}

	private fun updatePrintingStrings() {
		val newStrings = LongSparseArray<SparseArray<CharSequence>>()
		val newTypes = LongSparseArray<SparseIntArray>()

		for ((key, threads) in printingUsers) {
			val isEncryptedChat = DialogObject.isEncryptedDialog(key)

			for ((threadId, arr) in threads) {
				val newPrintingStrings = SparseArray<CharSequence>()
				val newPrintingStringsTypes = SparseIntArray()

				newStrings.put(key, newPrintingStrings)
				newTypes.put(key, newPrintingStringsTypes)

				if (key > 0 || isEncryptedChat || arr.size == 1) {
					val pu = arr[0]
					val user = getUser(pu.userId) ?: continue

					when (pu.action) {
						is TL_sendMessageRecordAudioAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsRecordingAudio", R.string.IsRecordingAudio, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.RecordingAudio))
							}

							newPrintingStringsTypes.put(threadId, 1)
						}

						is TL_sendMessageRecordRoundAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsRecordingRound", R.string.IsRecordingRound, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.RecordingRound))
							}

							newPrintingStringsTypes.put(threadId, 4)
						}

						is TL_sendMessageUploadRoundAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingVideo", R.string.IsSendingVideo, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.SendingVideoStatus))
							}

							newPrintingStringsTypes.put(threadId, 4)
						}

						is TL_sendMessageUploadAudioAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingAudio", R.string.IsSendingAudio, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.SendingAudio))
							}

							newPrintingStringsTypes.put(threadId, 2)
						}

						is TL_sendMessageUploadVideoAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingVideo", R.string.IsSendingVideo, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.SendingVideoStatus))
							}

							newPrintingStringsTypes.put(threadId, 2)
						}

						is TL_sendMessageRecordVideoAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsRecordingVideo", R.string.IsRecordingVideo, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.RecordingVideoStatus))
							}

							newPrintingStringsTypes.put(threadId, 2)
						}

						is TL_sendMessageUploadDocumentAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingFile", R.string.IsSendingFile, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.SendingFile))
							}

							newPrintingStringsTypes.put(threadId, 2)
						}

						is TL_sendMessageUploadPhotoAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingPhoto", R.string.IsSendingPhoto, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.SendingPhoto))
							}

							newPrintingStringsTypes.put(threadId, 2)
						}

						is TL_sendMessageGamePlayAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingGame", R.string.IsSendingGame, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.SendingGame))
							}

							newPrintingStringsTypes.put(threadId, 3)
						}

						is TL_sendMessageGeoLocationAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsSelectingLocation", R.string.IsSelectingLocation, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.SelectingLocation))
							}

							newPrintingStringsTypes.put(threadId, 0)
						}

						is TL_sendMessageChooseContactAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsSelectingContact", R.string.IsSelectingContact, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.SelectingContact))
							}

							newPrintingStringsTypes.put(threadId, 0)
						}

						is TL_sendMessageEmojiInteractionSeen -> {
							val emoji = (pu.action as? TL_sendMessageEmojiInteractionSeen)?.emoticon

							val printingString = if (key < 0 && !isEncryptedChat) {
								LocaleController.formatString("IsEnjoyngAnimations", R.string.IsEnjoyngAnimations, getUserNameForTyping(user), emoji)
							}
							else {
								LocaleController.formatString("EnjoyngAnimations", R.string.EnjoyngAnimations, emoji)
							}

							newPrintingStrings.put(threadId, printingString)
							newPrintingStringsTypes.put(threadId, 5)
						}

						is TL_sendMessageChooseStickerAction -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsChoosingSticker", R.string.IsChoosingSticker, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.ChoosingSticker))
							}

							newPrintingStringsTypes.put(threadId, 5)
						}

						else -> {
							if (key < 0 && !isEncryptedChat) {
								newPrintingStrings.put(threadId, LocaleController.formatString("IsTypingGroup", R.string.IsTypingGroup, getUserNameForTyping(user)))
							}
							else {
								newPrintingStrings.put(threadId, ApplicationLoader.applicationContext.getString(R.string.Typing))
							}

							newPrintingStringsTypes.put(threadId, 0)
						}
					}
				}
				else {
					var count = 0

					val label = buildString {
						for (pu in arr) {
							val user = getUser(pu.userId)

							if (user != null) {
								if (isNotEmpty()) {
									append(", ")
								}

								append(getUserNameForTyping(user))

								count++
							}

							if (count == 2) {
								break
							}
						}
					}

					if (label.isNotEmpty()) {
						if (count == 1) {
							newPrintingStrings.put(threadId, LocaleController.formatString("IsTypingGroup", R.string.IsTypingGroup, label))
						}
						else {
							if (arr.size > 2) {
								val plural = LocaleController.getPluralString("AndMoreTypingGroup", arr.size - 2)

								try {
									newPrintingStrings.put(threadId, String.format(plural, label, arr.size - 2))
								}
								catch (e: Exception) {
									newPrintingStrings.put(threadId, "LOC_ERR: AndMoreTypingGroup")
								}
							}
							else {
								newPrintingStrings.put(threadId, LocaleController.formatString("AreTypingGroup", R.string.AreTypingGroup, label))
							}
						}

						newPrintingStringsTypes.put(threadId, 0)
					}
				}
			}
		}

		lastPrintingStringCount = newStrings.size()

		AndroidUtilities.runOnUIThread {
			printingStrings = newStrings
			printingStringsTypes = newTypes
		}
	}

	fun cancelTyping(action: Int, dialogId: Long, threadMsgId: Int) {
		if (action < 0 || action >= sendingTypings.size || sendingTypings[action] == null) {
			return
		}

		val dialogs = sendingTypings[action]
		val threads = dialogs?.get(dialogId) ?: return

		threads.delete(threadMsgId)

		if (threads.size() == 0) {
			dialogs.remove(dialogId)
		}
	}

	fun sendTyping(dialogId: Long, threadMsgId: Int, action: Int, classGuid: Int): Boolean {
		return sendTyping(dialogId, threadMsgId, action, null, classGuid)
	}

	fun sendTyping(dialogId: Long, threadMsgId: Int, action: Int, emojicon: String?, classGuid: Int): Boolean {
		if (action < 0 || action >= sendingTypings.size || dialogId == 0L) {
			return false
		}

		if (dialogId < 0) {
			if (ChatObject.getSendAsPeerId(getChat(-dialogId), getChatFull(-dialogId)) != userConfig.getClientUserId()) {
				return false
			}
		}
		else {
			val user = getUser(dialogId)

			if (user != null) {
				if (user.id == userConfig.getClientUserId()) {
					return false
				}

				val expires = user.status?.expires

				if (expires != null && expires != -100 && !onlinePrivacy.containsKey(user.id)) {
					val time = connectionsManager.currentTime

					if (expires <= time - 30) {
						return false
					}
				}
			}
		}

		var dialogs = sendingTypings[action]

		if (dialogs == null) {
			sendingTypings[action] = LongSparseArray()
			dialogs = sendingTypings[action]
		}

		var threads = dialogs?.get(dialogId)

		if (threads == null) {
			dialogs?.put(dialogId, SparseBooleanArray().also { threads = it })
		}

		if ((threads?.indexOfKey(threadMsgId) ?: Int.MIN_VALUE) >= 0) {
			return false
		}

		if (!DialogObject.isEncryptedDialog(dialogId)) {
			val req = TL_messages_setTyping()

			if (threadMsgId != 0) {
				req.top_msg_id = threadMsgId
				req.flags = req.flags or 1
			}

			req.peer = getInputPeer(dialogId)

			if (req.peer is TL_inputPeerChannel) {
				val chat = getChat(req.peer.channel_id)

				if (chat == null || !chat.megagroup) {
					return false
				}
			}

			if (req.peer == null) {
				return false
			}

			when (action) {
				0 -> {
					req.action = TL_sendMessageTypingAction()
				}

				1 -> {
					req.action = TL_sendMessageRecordAudioAction()
				}

				2 -> {
					req.action = TL_sendMessageCancelAction()
				}

				3 -> {
					req.action = TL_sendMessageUploadDocumentAction()
				}

				4 -> {
					req.action = TL_sendMessageUploadPhotoAction()
				}

				5 -> {
					req.action = TL_sendMessageUploadVideoAction()
				}

				6 -> {
					req.action = TL_sendMessageGamePlayAction()
				}

				7 -> {
					req.action = TL_sendMessageRecordRoundAction()
				}

				8 -> {
					req.action = TL_sendMessageUploadRoundAction()
				}

				9 -> {
					req.action = TL_sendMessageUploadAudioAction()
				}

				10 -> {
					req.action = TL_sendMessageChooseStickerAction()
				}

				11 -> {
					val interactionSeen = TL_sendMessageEmojiInteractionSeen()
					interactionSeen.emoticon = emojicon
					req.action = interactionSeen
				}
			}

			threads?.put(threadMsgId, true)

			val reqId = connectionsManager.sendRequest(req, { _, _ ->
				AndroidUtilities.runOnUIThread {
					cancelTyping(action, dialogId, threadMsgId)
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)

			if (classGuid != 0) {
				connectionsManager.bindRequestToGuid(reqId, classGuid)
			}
		}
		else {
			if (action != 0) {
				return false
			}

			val chat = getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))

			if (chat?.auth_key != null && chat.auth_key.size > 1 && chat is TL_encryptedChat) {
				val req = TL_messages_setEncryptedTyping()
				req.peer = TL_inputEncryptedChat()
				req.peer.chat_id = chat.id
				req.peer.access_hash = chat.access_hash
				req.typing = true

				threads?.put(threadMsgId, true)

				val reqId = connectionsManager.sendRequest(req, { _, _ ->
					AndroidUtilities.runOnUIThread {
						cancelTyping(action, dialogId, threadMsgId)
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors)

				if (classGuid != 0) {
					connectionsManager.bindRequestToGuid(reqId, classGuid)
				}
			}
		}

		return true
	}

	fun removeDeletedMessagesFromArray(dialogId: Long, messages: ArrayList<Message>) {
		val maxDeletedId = deletedHistory[dialogId, 0]

		if (maxDeletedId == 0) {
			return
		}

		var a = 0
		var n = messages.size

		while (a < n) {
			val message = messages[a]

			if (message.id <= maxDeletedId) {
				messages.removeAt(a)
				a--
				n--
			}

			a++
		}
	}

	fun loadMessages(dialogId: Long, mergeDialogId: Long, loadInfo: Boolean, count: Int, maxId: Int, offsetDate: Int, fromCache: Boolean, midDate: Int, classGuid: Int, loadType: Int, lastMessageId: Int, mode: Int, threadMessageId: Int, replyFirstUnread: Int, loadIndex: Int) {
		loadMessages(dialogId, mergeDialogId, loadInfo, count, maxId, offsetDate, fromCache, midDate, classGuid, loadType, lastMessageId, mode, threadMessageId, loadIndex, if (threadMessageId != 0) replyFirstUnread else 0, 0, 0, false, 0)
	}

	fun loadMessages(dialogId: Long, mergeDialogId: Long, loadInfo: Boolean, count: Int, maxId: Int, offsetDate: Int, fromCache: Boolean, midDate: Int, classGuid: Int, loadType: Int, lastMessageId: Int, mode: Int, threadMessageId: Int, loadIndex: Int, firstUnread: Int, unreadCount: Int, lastDate: Int, queryFromServer: Boolean, mentionsCount: Int) {
		loadMessagesInternal(dialogId, mergeDialogId, loadInfo, count, maxId, offsetDate, fromCache, midDate, classGuid, loadType, lastMessageId, mode, threadMessageId, loadIndex, firstUnread, unreadCount, lastDate, queryFromServer, mentionsCount, loadDialog = true, processMessages = true)
	}

	private fun loadMessagesInternal(dialogId: Long, mergeDialogId: Long, loadInfo: Boolean, count: Int, maxId: Int, offsetDate: Int, fromCache: Boolean, minDate: Int, classGuid: Int, loadType: Int, lastMessageId: Int, mode: Int, threadMessageId: Int, loadIndex: Int, firstUnread: Int, unreadCount: Int, lastDate: Int, queryFromServer: Boolean, mentionsCount: Int, loadDialog: Boolean, processMessages: Boolean) {
		if (threadMessageId == 0 && mode != 2 && (fromCache || DialogObject.isEncryptedDialog(dialogId))) {
			messagesStorage.getMessages(dialogId, mergeDialogId, loadInfo, count, maxId, offsetDate, minDate, classGuid, loadType, mode == 1, threadMessageId, loadIndex, processMessages)
		}
		else {
			if (threadMessageId != 0) {
				if (mode != 0) {
					return
				}

				val req = TL_messages_getReplies()
				req.peer = getInputPeer(dialogId)
				req.msg_id = threadMessageId
				req.offset_date = offsetDate
				req.limit = count
				req.offset_id = maxId

				if (loadType == 4) {
					req.add_offset = -count + 5
				}
				else if (loadType == 3) {
					req.add_offset = -count / 2
				}
				else if (loadType == 1) {
					req.add_offset = -count - 1
				}
				else if (loadType == 2 && maxId != 0) {
					req.add_offset = -count + 10
				}
				else {
					if (dialogId < 0 && maxId != 0) {
						val chat = getChat(-dialogId)

						if (ChatObject.isChannel(chat)) {
							req.add_offset = -1
							req.limit += 1
						}
					}
				}

				val reqId = connectionsManager.sendRequest(req) { response, error ->
					if (response is messages_Messages) {
						var mid = maxId
						var fnid = 0

						if (response.messages.isNotEmpty()) {
							if (offsetDate != 0) {
								mid = response.messages[response.messages.size - 1].id

								for (a in response.messages.indices.reversed()) {
									val message = response.messages[a]

									if (message.date > offsetDate) {
										mid = message.id
										break
									}
								}
							}
							else if (firstUnread != 0 && loadType == 2 && maxId > 0) {
								for (a in response.messages.indices.reversed()) {
									val message = response.messages[a]

									if (message.id > firstUnread && !message.out) {
										fnid = message.id
										break
									}
								}
							}
						}

						processLoadedMessages(response, response.messages.size, dialogId, mergeDialogId, count, mid, offsetDate, false, classGuid, fnid, lastMessageId, unreadCount, lastDate, loadType, false, 0, threadMessageId, loadIndex, queryFromServer, mentionsCount, processMessages)
					}
					else {
						AndroidUtilities.runOnUIThread {
							notificationCenter.postNotificationName(NotificationCenter.loadingMessagesFailed, classGuid, req, error)
						}
					}
				}

				connectionsManager.bindRequestToGuid(reqId, classGuid)
			}
			else if (mode == 2) {
				// unused
			}
			else if (mode == 1) {
				val req = TL_messages_getScheduledHistory()
				req.peer = getInputPeer(dialogId)
				req.hash = minDate.toLong()

				val reqId = connectionsManager.sendRequest(req) { response, _ ->
					if (response is messages_Messages) {
						if (response is TL_messages_messagesNotModified) {
							return@sendRequest
						}

						var mid = maxId

						if (offsetDate != 0 && response.messages.isNotEmpty()) {
							mid = response.messages[response.messages.size - 1].id

							for (a in response.messages.indices.reversed()) {
								val message = response.messages[a]

								if (message.date > offsetDate) {
									mid = message.id
									break
								}
							}
						}

						processLoadedMessages(response, response.messages.size, dialogId, mergeDialogId, count, mid, offsetDate, false, classGuid, firstUnread, lastMessageId, unreadCount, lastDate, loadType, false, mode, threadMessageId, loadIndex, queryFromServer, mentionsCount, processMessages)
					}
				}

				connectionsManager.bindRequestToGuid(reqId, classGuid)
			}
			else {
				if (loadDialog && (loadType == 3 || loadType == 2) && lastMessageId == 0) {
					val req = TL_messages_getPeerDialogs()
					val inputPeer = getInputPeer(dialogId)

					val inputDialogPeer = TL_inputDialogPeer()
					inputDialogPeer.peer = inputPeer

					req.peers.add(inputDialogPeer)

					connectionsManager.sendRequest(req) { response, error ->
						if (response is TL_messages_peerDialogs) {
							if (response.dialogs.isNotEmpty()) {
								val dialog = response.dialogs[0]

								if (dialog.top_message != 0) {
									val dialogs = TL_messages_dialogs()
									dialogs.chats = response.chats
									dialogs.users = response.users
									dialogs.dialogs = response.dialogs
									dialogs.messages = response.messages

									messagesStorage.putDialogs(dialogs, 2)
								}

								loadMessagesInternal(dialogId, mergeDialogId, loadInfo, count, maxId, offsetDate, false, minDate, classGuid, loadType, dialog.top_message, 0, threadMessageId, loadIndex, firstUnread, dialog.unread_count, lastDate, queryFromServer, dialog.unread_mentions_count, false, processMessages)
							}
						}
						else {
							AndroidUtilities.runOnUIThread {
								notificationCenter.postNotificationName(NotificationCenter.loadingMessagesFailed, classGuid, req, error)
							}
						}
					}

					return
				}

				val req = TL_messages_getHistory()
				req.peer = getInputPeer(dialogId)

				if (loadType == 4) {
					req.add_offset = -count + 5
				}
				else if (loadType == 3) {
					req.add_offset = -count / 2
				}
				else if (loadType == 1) {
					req.add_offset = -count - 1
				}
				else if (loadType == 2 && maxId != 0) {
					req.add_offset = -count + 6
				}
				else {
					if (dialogId < 0 && maxId != 0) {
						val chat = getChat(-dialogId)

						if (ChatObject.isChannel(chat)) {
							req.add_offset = -1
							req.limit += 1
						}
					}
				}

				req.limit = count
				req.offset_id = maxId
				req.offset_date = offsetDate

				val reqId = connectionsManager.sendRequest(req) { response, error ->
					if (response is messages_Messages) {
						removeDeletedMessagesFromArray(dialogId, response.messages)

						if (response.messages.size > count) {
							response.messages.removeAt(0)
						}

						var mid = maxId

						if (offsetDate != 0 && response.messages.isNotEmpty()) {
							mid = response.messages[response.messages.size - 1].id

							for (a in response.messages.indices.reversed()) {
								val message = response.messages[a]

								if (message.date > offsetDate) {
									mid = message.id
									break
								}
							}
						}

						processLoadedMessages(response, response.messages.size, dialogId, mergeDialogId, count, mid, offsetDate, false, classGuid, firstUnread, lastMessageId, unreadCount, lastDate, loadType, false, 0, threadMessageId, loadIndex, queryFromServer, mentionsCount, processMessages)
					}
					else {
						AndroidUtilities.runOnUIThread {
							notificationCenter.postNotificationName(NotificationCenter.loadingMessagesFailed, classGuid, req, error)
						}
					}
				}

				connectionsManager.bindRequestToGuid(reqId, classGuid)
			}
		}
	}

	fun reloadWebPages(dialogId: Long, webpagesToReload: MutableMap<String, MutableList<MessageObject>>, scheduled: Boolean) {
		val map = if (scheduled) reloadingScheduledWebpages else reloadingWebpages
		val array = if (scheduled) reloadingScheduledWebpagesPending else reloadingWebpagesPending

		for ((url, messages) in webpagesToReload) {
			var arrayList = map[url]

			if (arrayList == null) {
				arrayList = mutableListOf()
				map[url] = arrayList
			}

			arrayList.addAll(messages)

			val req = TL_messages_getWebPagePreview()
			req.message = url

			connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					val arrayList1 = map.remove(url) ?: return@runOnUIThread
					val messagesRes = TL_messages_messages()

					if (response !is TL_messageMediaWebPage) {
						for (a in arrayList1.indices) {
							arrayList1[a].messageOwner?.media?.webpage = TL_webPageEmpty()
							messagesRes.messages.add(arrayList1[a].messageOwner!!)
						}
					}
					else {
						if (response.webpage is TL_webPage || response.webpage is TL_webPageEmpty) {
							for (a in arrayList1.indices) {
								arrayList1[a].messageOwner?.media?.webpage = response.webpage

								if (a == 0) {
									ImageLoader.saveMessageThumbs(arrayList1[a].messageOwner!!)
								}

								messagesRes.messages.add(arrayList1[a].messageOwner!!)
							}
						}
						else {
							array.put(response.webpage.id, arrayList1)
						}
					}

					if (messagesRes.messages.isNotEmpty()) {
						messagesStorage.putMessages(messagesRes, dialogId, -2, 0, false, scheduled)
						notificationCenter.postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, arrayList1)
					}
				}
			}
		}
	}

	fun processLoadedMessages(messagesRes: messages_Messages, resCount: Int, dialogId: Long, mergeDialogId: Long, count: Int, maxId: Int, offsetDate: Int, isCache: Boolean, classGuid: Int, firstUnread: Int, lastMessageId: Int, unreadCount: Int, lastDate: Int, loadType: Int, isEnd: Boolean, mode: Int, threadMessageId: Int, loadIndex: Int, queryFromServer: Boolean, mentionsCount: Int, needProcess: Boolean) {
		var createDialog = false

		if (messagesRes is TL_messages_channelMessages) {
			val channelId = -dialogId

			if (mode == 0 && threadMessageId == 0) {
				var channelPts = channelsPts[channelId]

				if (channelPts == 0) {
					channelPts = messagesStorage.getChannelPtsSync(channelId)

					if (channelPts == 0) {
						channelsPts.put(channelId, messagesRes.pts)

						createDialog = true

						if (needShortPollChannels.indexOfKey(channelId) >= 0 && shortPollChannels.indexOfKey(channelId) < 0) {
							getChannelDifference(channelId, 2, 0, null)
						}
						else {
							getChannelDifference(channelId)
						}
					}
				}
			}
		}

		if (!isCache) {
			ImageLoader.saveMessagesThumbs(messagesRes.messages)
		}

		val isInitialLoading = offsetDate == 0 && maxId == 0
		var reload: Boolean

		if (mode == 1) {
			reload = SystemClock.elapsedRealtime() - lastScheduledServerQueryTime[dialogId, 0L] > 60 * 1000
		}
		else {
			reload = resCount == 0 && (!isInitialLoading || SystemClock.elapsedRealtime() - lastServerQueryTime[dialogId, 0L] > 60 * 1000)

			if (mode == 0 && isCache && dialogId < 0 && !dialogs_dict.containsKey(dialogId) && SystemClock.elapsedRealtime() - lastServerQueryTime[dialogId, 0L] > 24 * 60 * 60 * 1000) {
				messagesRes.messages.clear()
				reload = true
			}
		}

		if (!DialogObject.isEncryptedDialog(dialogId) && isCache && reload) {
			val hash: Int

			if (mode == 2) {
				hash = 0
			}
			else if (mode == 1) {
				lastScheduledServerQueryTime.put(dialogId, SystemClock.elapsedRealtime())

				var h: Long = 0
				var a = 0
				val n = messagesRes.messages.size

				while (a < n) {
					val message = messagesRes.messages[a]

					if (message.id < 0) {
						a++
						continue
					}

					h = MediaDataController.calcHash(h, message.id.toLong())
					h = MediaDataController.calcHash(h, message.edit_date.toLong())
					h = MediaDataController.calcHash(h, message.date.toLong())

					a++
				}

				hash = h.toInt() - 1
			}
			else {
				lastServerQueryTime.put(dialogId, SystemClock.elapsedRealtime())
				hash = 0
			}

			AndroidUtilities.runOnUIThread {
				loadMessagesInternal(dialogId, mergeDialogId, false, count, if (loadType == 2 && queryFromServer) firstUnread else maxId, offsetDate, false, hash, classGuid, loadType, lastMessageId, mode, threadMessageId, loadIndex, firstUnread, unreadCount, lastDate, queryFromServer, mentionsCount, true, needProcess)
			}

			if (messagesRes.messages.isEmpty()) {
				return
			}
		}

		val usersDict = LongSparseArray<User>()
		val chatsDict = LongSparseArray<Chat>()

		for (a in messagesRes.users.indices) {
			val u = messagesRes.users[a]
			usersDict.put(u.id, u)
		}

		for (a in messagesRes.chats.indices) {
			val c = messagesRes.chats[a]
			chatsDict.put(c.id, c)
		}

		val size = messagesRes.messages.size

		if (!isCache) {
			var inboxValue = dialogs_read_inbox_max[dialogId]

			if (inboxValue == null) {
				inboxValue = messagesStorage.getDialogReadMax(false, dialogId)
				dialogs_read_inbox_max[dialogId] = inboxValue
			}

			var outboxValue = dialogs_read_outbox_max[dialogId]

			if (outboxValue == null) {
				outboxValue = messagesStorage.getDialogReadMax(true, dialogId)
				dialogs_read_outbox_max[dialogId] = outboxValue
			}

			for (a in 0 until size) {
				val message = messagesRes.messages[a]

				if (mode == 0) {
					val action = message.action

					if (action is TL_messageActionChatDeleteUser) {
						val user = usersDict[action.user_id]

						if (user != null && user.bot) {
							message.reply_markup = TL_replyKeyboardHide()
							message.flags = message.flags or 64
						}
					}

					if (action is TL_messageActionChatMigrateTo || action is TL_messageActionChannelCreate) {
						message.unread = false
						message.media_unread = false
					}
					else if (threadMessageId == 0) {
						message.unread = (if (message.out) outboxValue else inboxValue) < message.id
					}
					else {
						message.unread = true
					}
				}
			}

			if (threadMessageId == 0) {
				messagesStorage.putMessages(messagesRes, dialogId, loadType, maxId, createDialog, mode == 1)
			}
		}

		if (!needProcess && DialogObject.isEncryptedDialog(dialogId)) {
			AndroidUtilities.runOnUIThread {
				notificationCenter.postNotificationName(NotificationCenter.messagesDidLoadWithoutProcess, classGuid, messagesRes.messages.size, isCache, isEnd, lastMessageId)
			}

			return
		}

		val objects = mutableListOf<MessageObject>()
		val messagesToReload = mutableListOf<Int>()
		val webpagesToReload = mutableMapOf<String, MutableList<MessageObject>>()
		var fileProcessTime: Long = 0

		for (a in 0 until size) {
			val message = messagesRes.messages[a]
			message.dialog_id = dialogId

			val checkFileTime = SystemClock.elapsedRealtime()

			val messageObject = MessageObject(currentAccount, message, usersDict, chatsDict, generateLayout = true, checkMediaExists = false)
			messageObject.createStrippedThumb()

			fileProcessTime += SystemClock.elapsedRealtime() - checkFileTime

			messageObject.scheduled = mode == 1

			objects.add(messageObject)

			if (isCache) {
				val media = MessageObject.getMedia(message)

				if (message.legacy && message.layer < LAYER) {
					messagesToReload.add(message.id)
				}
				else if (media is TL_messageMediaUnsupported) {
					val bytes = media.bytes

					if (bytes != null && (bytes.isEmpty() || (bytes.size == 1 && bytes[0] < LAYER) || (bytes.size == 4 && Utilities.bytesToInt(bytes) < LAYER))) {
						messagesToReload.add(message.id)
					}
				}

				if (media is TL_messageMediaWebPage) {
					if (media.webpage is TL_webPagePending && media.webpage.date <= connectionsManager.currentTime) {
						messagesToReload.add(message.id)
					}
					else if (media.webpage is TL_webPageUrlPending) {
						var arrayList = webpagesToReload[media.webpage.url]

						if (arrayList == null) {
							arrayList = ArrayList()
							webpagesToReload[media.webpage.url] = arrayList
						}

						arrayList.add(messageObject)
					}
				}
			}
		}

		fileLoader.checkMediaExistence(objects)

		AndroidUtilities.runOnUIThread {
			putUsers(messagesRes.users, isCache)
			putChats(messagesRes.chats, isCache)

			if (messagesRes.animatedEmoji != null) {
				AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).processDocuments(messagesRes.animatedEmoji)
			}

			var firstUnreadFinal: Int

			if (mode == 1) {
				firstUnreadFinal = 0
			}
			else {
				firstUnreadFinal = Int.MAX_VALUE

				if (queryFromServer && loadType == 2) {
					for (a in messagesRes.messages.indices) {
						val message = messagesRes.messages[a]

						if ((!message.out || message.from_scheduled) && message.id > firstUnread && message.id < firstUnreadFinal) {
							firstUnreadFinal = message.id
						}
					}
				}

				if (firstUnreadFinal == Int.MAX_VALUE) {
					firstUnreadFinal = firstUnread
				}
			}

			if (mode == 1 && count == 1) {
				notificationCenter.postNotificationName(NotificationCenter.scheduledMessagesUpdated, dialogId, objects.size)
			}

			if (!DialogObject.isEncryptedDialog(dialogId)) {
				mediaDataController.loadReplyMessagesForMessages(objects, dialogId, mode == 1) {
					if (!needProcess) {
						notificationCenter.postNotificationName(NotificationCenter.messagesDidLoadWithoutProcess, classGuid, resCount, isCache, isEnd, lastMessageId)
					}
					else {
						notificationCenter.postNotificationName(NotificationCenter.messagesDidLoad, dialogId, count, objects, isCache, firstUnreadFinal, lastMessageId, unreadCount, lastDate, loadType, isEnd, classGuid, loadIndex, maxId, mentionsCount, mode)
					}
				}
			}
			else {
				notificationCenter.postNotificationName(NotificationCenter.messagesDidLoad, dialogId, count, objects, isCache, firstUnreadFinal, lastMessageId, unreadCount, lastDate, loadType, isEnd, classGuid, loadIndex, maxId, mentionsCount, mode)
			}

			if (messagesToReload.isNotEmpty()) {
				reloadMessages(messagesToReload, dialogId, mode == 1)
			}

			if (webpagesToReload.isNotEmpty()) {
				reloadWebPages(dialogId, webpagesToReload, mode == 1)
			}
		}
	}

	fun loadHintDialogs() {
		if (hintDialogs.isNotEmpty() || installReferer.isNullOrEmpty()) {
			return
		}

		val req = TL_help_getRecentMeUrls()
		req.referer = installReferer

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_help_recentMeUrls) {
				AndroidUtilities.runOnUIThread {
					putUsers(response.users, false)
					putChats(response.chats, false)

					hintDialogs.clear()
					hintDialogs.addAll(response.urls)

					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
				}
			}
		}
	}

	private fun ensureFolderDialogExists(folderId: Int, folderCreated: BooleanArray?): TL_dialogFolder? {
		if (folderId == 0) {
			return null
		}

		val folderDialogId = DialogObject.makeFolderDialogId(folderId)
		val dialog = dialogs_dict[folderDialogId]

		if (dialog is TL_dialogFolder) {
			if (folderCreated != null) {
				folderCreated[0] = false
			}

			return dialog
		}

		if (folderCreated != null) {
			folderCreated[0] = true
		}

		val dialogFolder = TL_dialogFolder()
		dialogFolder.id = folderDialogId
		dialogFolder.peer = TL_peerUser()
		dialogFolder.folder = TL_folder()
		dialogFolder.folder.id = folderId
		dialogFolder.folder.title = ApplicationLoader.applicationContext.getString(R.string.ArchivedChats)
		dialogFolder.pinned = true

		var maxPinnedNum = 0

		for (d in allDialogs) {
			if (!d.pinned) {
				if (d.id != promoDialogId) {
					break
				}

				continue
			}

			maxPinnedNum = max(d.pinnedNum, maxPinnedNum)
		}

		dialogFolder.pinnedNum = maxPinnedNum + 1

		val dialogs = TL_messages_dialogs()
		dialogs.dialogs.add(dialogFolder)

		messagesStorage.putDialogs(dialogs, 1)

		dialogs_dict.put(folderDialogId, dialogFolder)

		allDialogs.add(0, dialogFolder)

		return dialogFolder
	}

	private fun removeFolder(folderId: Int) {
		val dialogId = DialogObject.makeFolderDialogId(folderId)
		val dialog = dialogs_dict[dialogId] ?: return

		dialogs_dict.remove(dialogId)
		allDialogs.remove(dialog)

		sortDialogs(null)

		notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
		notificationCenter.postNotificationName(NotificationCenter.folderBecomeEmpty, folderId)
	}

	fun onFolderEmpty(folderId: Int) {
		val dialogsLoadOffset = userConfig.getDialogLoadOffsets(folderId)

		if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] == Int.MAX_VALUE.toLong()) {
			removeFolder(folderId)
		}
		else {
			loadDialogs(folderId, 0, 10, false) {
				removeFolder(folderId)
			}
		}
	}

	fun checkIfFolderEmpty(folderId: Int) {
		if (folderId == 0) {
			return
		}

		messagesStorage.checkIfFolderEmpty(folderId)
	}

	fun addDialogToFolder(dialogId: Long, folderId: Int, pinnedNum: Int, taskId: Long): Int {
		return addDialogToFolder(listOf(dialogId), folderId, pinnedNum, null, taskId)
	}

	fun addDialogToFolder(dialogIds: List<Long>?, folderId: Int, pinnedNum: Int, peers: ArrayList<TL_inputFolderPeer>?, taskId: Long): Int {
		val req = TL_folders_editPeerFolders()
		var folderCreated: BooleanArray? = null
		val newTaskId: Long

		if (taskId == 0L) {
			var added = false
			val selfUserId = userConfig.getClientUserId()
			var size = 0
			var a = 0
			val n = dialogIds?.size ?: 0

			while (a < n) {
				val dialogId = dialogIds?.get(a)

				if (dialogId == null) {
					a++
					continue
				}

				if (!DialogObject.isChatDialog(dialogId) && !DialogObject.isUserDialog(dialogId) && !DialogObject.isEncryptedDialog(dialogId)) {
					a++
					continue
				}

				if (folderId == 1 && (dialogId == selfUserId || dialogId == BuildConfig.NOTIFICATIONS_BOT_ID || isPromoDialog(dialogId, false))) {
					a++
					continue
				}

				val dialog = dialogs_dict[dialogId]

				if (dialog == null) {
					a++
					continue
				}

				added = true

				dialog.folder_id = folderId

				if (pinnedNum > 0) {
					dialog.pinned = true
					dialog.pinnedNum = pinnedNum
				}
				else {
					dialog.pinned = false
					dialog.pinnedNum = 0
				}

				if (folderCreated == null) {
					folderCreated = BooleanArray(1)
					ensureFolderDialogExists(folderId, folderCreated)
				}

				if (DialogObject.isEncryptedDialog(dialogId)) {
					messagesStorage.setDialogsFolderId(null, null, dialogId, folderId)
				}
				else {
					val folderPeer = TL_inputFolderPeer()
					folderPeer.folder_id = folderId
					folderPeer.peer = getInputPeer(dialogId)

					req.folder_peers.add(folderPeer)

					size += folderPeer.objectSize
				}

				a++
			}

			if (!added) {
				return 0
			}

			sortDialogs(null)

			notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

			if (size != 0) {
				val data = try {
					NativeByteBuffer(4 + 4 + 4 + size).apply {
						writeInt32(17)
						writeInt32(folderId)
						writeInt32(req.folder_peers.size)

						for (folderPeer in req.folder_peers) {
							folderPeer.serializeToStream(this)
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
					null
				}

				newTaskId = messagesStorage.createPendingTask(data)
			}
			else {
				newTaskId = 0
			}
		}
		else {
			req.folder_peers = peers ?: ArrayList()
			newTaskId = taskId
		}

		if (req.folder_peers.isNotEmpty()) {
			connectionsManager.sendRequest(req) { response, _ ->
				if (response is Updates) {
					processUpdates(response, false)
				}

				if (newTaskId != 0L) {
					messagesStorage.removePendingTask(newTaskId)
				}
			}

			messagesStorage.setDialogsFolderId(null, req.folder_peers, 0, folderId)
		}

		return if (folderCreated == null) 0 else if (folderCreated[0]) 2 else 1
	}

	@JvmOverloads
	fun loadDialogs(folderId: Int, offset: Int, count: Int, fromCache: Boolean, onEmptyCallback: Runnable? = null) {
		loadDialogs(folderId, offset, count, fromCache, false, onEmptyCallback)
	}

	@JvmOverloads
	fun loadDialogs(folderId: Int, offset: Int, count: Int, fromCache: Boolean, force: Boolean, onEmptyCallback: Runnable? = null) {
		if (loadingDialogs[folderId] || resettingDialogs) {
			return
		}

		loadingDialogs.put(folderId, true)

		notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

		if (fromCache) {
			messagesStorage.getDialogs(folderId, if (offset == 0) 0 else nextDialogsCacheOffset[folderId, 0], count, folderId == 0 && offset == 0)
		}
		else {
			val req = TL_messages_getDialogs()
			req.limit = count
			req.excludePinned = false

			if (folderId != 0) {
				req.flags = req.flags or 2
				req.folderId = folderId
			}

			val dialogsLoadOffset = userConfig.getDialogLoadOffsets(folderId)

			if (force) {
				Arrays.fill(dialogsLoadOffset, 0L)
			}

			if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1L) {
				if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] == Int.MAX_VALUE.toLong()) {
					dialogsEndReached.put(folderId, true)
					serverDialogsEndReached.put(folderId, true)
					loadingDialogs.put(folderId, false)

					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

					return
				}

				req.offsetId = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId].toInt()
				req.offsetDate = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetDate].toInt()

				if (req.offsetId == 0) {
					req.offsetPeer = TL_inputPeerEmpty()
				}
				else {
					if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetChannelId] != 0L) {
						req.offsetPeer = TL_inputPeerChannel()
						req.offsetPeer?.channel_id = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetChannelId]
					}
					else if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetUserId] != 0L) {
						req.offsetPeer = TL_inputPeerUser()
						req.offsetPeer?.user_id = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetUserId]
					}
					else {
						req.offsetPeer = TL_inputPeerChat()
						req.offsetPeer?.chat_id = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetChatId]
					}

					req.offsetPeer?.access_hash = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetAccess]
				}
			}
			else {
				var found = false
				val dialogs = getDialogs(folderId)

				for (a in dialogs.indices.reversed()) {
					val dialog = dialogs[a]

					if (dialog.pinned) {
						continue
					}

					if (!DialogObject.isEncryptedDialog(dialog.id) && dialog.top_message > 0) {
						val message = dialogMessage[dialog.id]

						if (message != null && message.id > 0) {
							req.offsetDate = message.messageOwner!!.date
							req.offsetId = message.messageOwner!!.id

							val id = if (message.messageOwner!!.peer_id!!.channel_id != 0L) {
								-message.messageOwner!!.peer_id!!.channel_id
							}
							else if (message.messageOwner!!.peer_id!!.chat_id != 0L) {
								-message.messageOwner!!.peer_id!!.chat_id
							}
							else {
								message.messageOwner!!.peer_id!!.user_id
							}

							req.offsetPeer = getInputPeer(id)

							found = true

							break
						}
					}
				}

				if (!found) {
					req.offsetPeer = TL_inputPeerEmpty()
				}
			}

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is messages_Dialogs) {
					processLoadedDialogs(response, null, folderId, 0, count, 0, resetEnd = false, migrate = false, fromCache = false)

					if (onEmptyCallback != null && response.dialogs.isEmpty()) {
						AndroidUtilities.runOnUIThread(onEmptyCallback)
					}
				}
			}
		}
	}

	fun loadGlobalNotificationsSettings() {
		if (loadingNotificationSettings == 0 && !userConfig.notificationsSettingsLoaded) {
			val preferences = getNotificationsSettings(currentAccount)
			var editor1: SharedPreferences.Editor? = null

			if (preferences.contains("EnableGroup")) {
				val enabled = preferences.getBoolean("EnableGroup", true)

				editor1 = preferences.edit()

				if (!enabled) {
					editor1.putInt("EnableGroup2", Int.MAX_VALUE)
					editor1.putInt("EnableChannel2", Int.MAX_VALUE)
				}

				editor1.remove("EnableGroup").commit()
			}

			if (preferences.contains("EnableAll")) {
				val enabled = preferences.getBoolean("EnableAll", true)

				if (editor1 == null) {
					editor1 = preferences.edit()
				}

				if (!enabled) {
					editor1?.putInt("EnableAll2", Int.MAX_VALUE)
				}

				editor1?.remove("EnableAll")?.commit()
			}

			editor1?.commit()

			loadingNotificationSettings = 3

			for (a in 0..2) {
				val req = TL_account_getNotifySettings()

				when (a) {
					0 -> req.peer = TL_inputNotifyChats()
					1 -> req.peer = TL_inputNotifyUsers()
					else -> req.peer = TL_inputNotifyBroadcasts()
				}

				connectionsManager.sendRequest(req) { response, _ ->
					AndroidUtilities.runOnUIThread {
						if (response is TL_peerNotifySettings) {
							loadingNotificationSettings--

							val editor = notificationsPreferences.edit()

							if (a == 0) {
								if (response.flags and 1 != 0) {
									editor.putBoolean("EnablePreviewGroup", response.show_previews)
								}

								if (response.flags and 4 != 0) {
									editor.putInt("EnableGroup2", response.mute_until)
								}
							}
							else if (a == 1) {
								if (response.flags and 1 != 0) {
									editor.putBoolean("EnablePreviewAll", response.show_previews)
								}

								if (response.flags and 4 != 0) {
									editor.putInt("EnableAll2", response.mute_until)
								}
							}
							else {
								if (response.flags and 1 != 0) {
									editor.putBoolean("EnablePreviewChannel", response.show_previews)
								}

								if (response.flags and 4 != 0) {
									editor.putInt("EnableChannel2", response.mute_until)
								}
							}

							applySoundSettings(response.android_sound, editor, 0, a, false)

							editor.commit()

							if (loadingNotificationSettings == 0) {
								userConfig.notificationsSettingsLoaded = true
								userConfig.saveConfig(false)
							}
						}
					}
				}
			}
		}

		if (!userConfig.notificationsSignUpSettingsLoaded) {
			loadSignUpNotificationsSettings()
		}
	}

	fun loadSignUpNotificationsSettings() {
		if (!loadingNotificationSignUpSettings) {
			loadingNotificationSignUpSettings = true

			val req = TL_account_getContactSignUpNotification()

			connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					loadingNotificationSignUpSettings = false

					val editor = notificationsPreferences.edit()

					enableJoined = response is TL_boolFalse

					editor.putBoolean("EnableContactJoined", enableJoined)
					editor.commit()

					userConfig.notificationsSignUpSettingsLoaded = true

					userConfig.saveConfig(false)
				}
			}
		}
	}

	fun forceResetDialogs() {
		resetDialogs(true, messagesStorage.lastSeqValue, messagesStorage.lastPtsValue, messagesStorage.lastDateValue, messagesStorage.lastQtsValue)
		notificationsController.deleteAllNotificationChannels()
	}

	fun loadUnknownDialog(peer: InputPeer?, taskId: Long) {
		if (peer == null) {
			return
		}

		val dialogId = DialogObject.getPeerDialogId(peer)

		if (gettingUnknownDialogs.indexOfKey(dialogId) >= 0) {
			return
		}

		gettingUnknownDialogs.put(dialogId, true)

		val req = TL_messages_getPeerDialogs()

		val inputDialogPeer = TL_inputDialogPeer()
		inputDialogPeer.peer = peer

		req.peers.add(inputDialogPeer)

		val newTaskId: Long

		if (taskId == 0L) {
			val data = try {
				NativeByteBuffer(4 + peer.objectSize).apply {
					writeInt32(15)
					peer.serializeToStream(this)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
				null
			}

			newTaskId = messagesStorage.createPendingTask(data)
		}
		else {
			newTaskId = taskId
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_messages_peerDialogs) {
				if (response.dialogs.isNotEmpty()) {
					val dialog = response.dialogs[0] as TL_dialog

					val dialogs = TL_messages_dialogs()
					dialogs.dialogs.addAll(response.dialogs)
					dialogs.messages.addAll(response.messages)
					dialogs.users.addAll(response.users)
					dialogs.chats.addAll(response.chats)

					processLoadedDialogs(dialogs, null, dialog.folder_id, 0, 1, DIALOGS_LOAD_TYPE_UNKNOWN, resetEnd = false, migrate = false, fromCache = false)
				}
			}

			if (newTaskId != 0L) {
				messagesStorage.removePendingTask(newTaskId)
			}

			gettingUnknownDialogs.remove(dialogId)
		}
	}

	private fun fetchFolderInLoadedPinnedDialogs(res: TL_messages_peerDialogs) {
		var a = 0
		val n = res.dialogs.size

		while (a < n) {
			val dialog = res.dialogs[a]

			if (dialog is TL_dialogFolder) {
				val folderTopDialogId = DialogObject.getPeerDialogId(dialog.peer)

				if (dialog.top_message == 0 || folderTopDialogId == 0L) {
					res.dialogs.remove(dialog)
					a++
					continue
				}

				var b = 0
				val n2 = res.messages.size

				while (b < n2) {
					val message = res.messages[b]
					val messageDialogId = MessageObject.getDialogId(message)

					if (folderTopDialogId == messageDialogId && dialog.top_message == message.id) {
						val newDialog = TL_dialog()
						newDialog.peer = dialog.peer
						newDialog.top_message = dialog.top_message
						newDialog.folder_id = dialog.folder.id
						newDialog.flags = newDialog.flags or 16

						res.dialogs.add(newDialog)

						val inputPeer: InputPeer

						if (dialog.peer is TL_peerChannel) {
							inputPeer = TL_inputPeerChannel()
							inputPeer.channel_id = dialog.peer.channel_id

							var c = 0
							val n3 = res.chats.size

							while (c < n3) {
								val chat = res.chats[c]

								if (chat.id == inputPeer.channel_id) {
									inputPeer.access_hash = chat.access_hash
									break
								}

								c++
							}
						}
						else if (dialog.peer is TL_peerChat) {
							inputPeer = TL_inputPeerChat()
							inputPeer.chat_id = dialog.peer.chat_id
						}
						else {
							inputPeer = TL_inputPeerUser()
							inputPeer.user_id = dialog.peer.user_id

							var c = 0
							val n3 = res.users.size

							while (c < n3) {
								val user = res.users[c]

								if (user.id == inputPeer.user_id) {
									inputPeer.access_hash = user.access_hash
									break
								}

								c++
							}
						}

						loadUnknownDialog(inputPeer, 0)

						break
					}

					b++
				}

				break
			}

			a++
		}
	}

	private fun resetDialogs(query: Boolean, seq: Int, newPts: Int, date: Int, qts: Int) {
		if (query) {
			if (resettingDialogs) {
				return
			}

			userConfig.setPinnedDialogsLoaded(1, false)

			resettingDialogs = true

			val req = TL_messages_getPinnedDialogs()

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_messages_peerDialogs) {
					resetDialogsPinned = response

					resetDialogsPinned?.dialogs?.forEach {
						it.pinned = true
					}

					resetDialogs(false, seq, newPts, date, qts)
				}
			}

			val req2 = TL_messages_getDialogs()
			req2.limit = 100
			req2.excludePinned = false
			req2.offsetPeer = TL_inputPeerEmpty()

			connectionsManager.sendRequest(req2) { response, _ ->
				if (response is messages_Dialogs) {
					resetDialogsAll = response
					resetDialogs(false, seq, newPts, date, qts)
				}
			}
		}
		else {
			val resetDialogsPinned = resetDialogsPinned
			val resetDialogsAll = resetDialogsAll

			if (resetDialogsPinned != null && resetDialogsAll != null) {
				val messagesCount = resetDialogsAll.messages.size
				val dialogsCount = resetDialogsAll.dialogs.size

				fetchFolderInLoadedPinnedDialogs(resetDialogsPinned)

				resetDialogsAll.dialogs.addAll(resetDialogsPinned.dialogs)
				resetDialogsAll.messages.addAll(resetDialogsPinned.messages)
				resetDialogsAll.users.addAll(resetDialogsPinned.users)
				resetDialogsAll.chats.addAll(resetDialogsPinned.chats)

				val newDialogsDict = LongSparseArray<Dialog>()
				val newDialogMessage = LongSparseArray<MessageObject>()
				val usersDict = LongSparseArray<User>()
				val chatsDict = LongSparseArray<Chat>()

				for (u in resetDialogsAll.users) {
					usersDict.put(u.id, u)
				}

				for (c in resetDialogsAll.chats) {
					chatsDict.put(c.id, c)
				}

				var lastMessage: Message? = null

				for (a in resetDialogsAll.messages.indices) {
					val message = resetDialogsAll.messages[a]

					if (a < messagesCount) {
						if (lastMessage == null || message.date < lastMessage.date) {
							lastMessage = message
						}
					}

					if (message.peer_id!!.channel_id != 0L) {
						val chat = chatsDict[message.peer_id!!.channel_id]

						if (chat != null && chat.left) {
							continue
						}
					}
					else if (message.peer_id!!.chat_id != 0L) {
						val chat = chatsDict[message.peer_id!!.chat_id]

						if (chat?.migrated_to != null) {
							continue
						}
					}

					// MARK: remove check for service messages
					if (message is TL_messageService) {
						continue
					}

					val messageObject = MessageObject(currentAccount, message, usersDict, chatsDict, generateLayout = false, checkMediaExists = true)

					newDialogMessage.put(messageObject.dialogId, messageObject)
				}

				for (d in resetDialogsAll.dialogs) {
					DialogObject.initDialog(d)

					if (d.id == 0L) {
						continue
					}

					if (d.last_message_date == 0) {
						val mess = newDialogMessage[d.id]

						if (mess != null) {
							d.last_message_date = mess.messageOwner!!.date
						}
					}

					if (DialogObject.isChannel(d)) {
						val chat = chatsDict[-d.id]

						if (chat != null && chat.left) {
							continue
						}

						channelsPts.put(-d.id, d.pts)
					}
					else if (DialogObject.isChatDialog(d.id)) {
						val chat = chatsDict[-d.id]

						if (chat?.migrated_to != null) {
							continue
						}
					}

					newDialogsDict.put(d.id, d)

					var value = dialogs_read_inbox_max[d.id]

					if (value == null) {
						value = 0
					}

					dialogs_read_inbox_max[d.id] = max(value, d.read_inbox_max_id)

					value = dialogs_read_outbox_max[d.id]

					if (value == null) {
						value = 0
					}

					dialogs_read_outbox_max[d.id] = max(value, d.read_outbox_max_id)
				}

				ImageLoader.saveMessagesThumbs(resetDialogsAll.messages)

				for (message in resetDialogsAll.messages) {
					val action = message.action

					if (action is TL_messageActionChatDeleteUser) {
						val user = usersDict[action.user_id]

						if (user != null && user.bot) {
							message.reply_markup = TL_replyKeyboardHide()
							message.flags = message.flags or 64
						}
					}

					if (action is TL_messageActionChatMigrateTo || action is TL_messageActionChannelCreate) {
						message.unread = false
						message.media_unread = false
					}
					else {
						val readMax = if (message.out) dialogs_read_outbox_max else dialogs_read_inbox_max
						var value = readMax[message.dialog_id]

						if (value == null) {
							value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
							readMax[message.dialog_id] = value
						}

						message.unread = value < message.id
					}
				}

				messagesStorage.resetDialogs(resetDialogsAll, seq, newPts, date, qts, newDialogsDict, newDialogMessage, lastMessage, dialogsCount)

				this.resetDialogsPinned = null
				this.resetDialogsAll = null
			}
		}
	}

	fun completeDialogsReset(dialogsRes: messages_Dialogs, newPts: Int, date: Int, qts: Int, newDialogsDict: LongSparseArray<Dialog>, newDialogMessage: LongSparseArray<MessageObject?>) {
		Utilities.stageQueue.postRunnable {
			gettingDifference = false

			messagesStorage.lastPtsValue = newPts
			messagesStorage.lastDateValue = date
			messagesStorage.lastQtsValue = qts

			getDifference()

			AndroidUtilities.runOnUIThread {
				resettingDialogs = false

				applyDialogsNotificationsSettings(dialogsRes.dialogs)

				val mediaDataController = mediaDataController
				mediaDataController.clearAllDrafts(false)
				mediaDataController.loadDraftsIfNeed()

				putUsers(dialogsRes.users, false)
				putChats(dialogsRes.chats, false)

				for (oldDialog in allDialogs) {
					if (!DialogObject.isEncryptedDialog(oldDialog.id)) {
						dialogs_dict.remove(oldDialog.id)

						val messageObject = dialogMessage[oldDialog.id]

						dialogMessage.remove(oldDialog.id)

						if (messageObject != null) {
							if (messageObject.messageOwner!!.peer_id!!.channel_id == 0L) {
								dialogMessagesByIds.remove(messageObject.id)
							}

							if (messageObject.messageOwner!!.random_id != 0L) {
								dialogMessagesByRandomIds.remove(messageObject.messageOwner!!.random_id)
							}
						}
					}
				}

				for (a in 0 until newDialogsDict.size()) {
					val key = newDialogsDict.keyAt(a)
					val value = newDialogsDict.valueAt(a)

					if (value.draft is TL_draftMessage) {
						mediaDataController.saveDraft(value.id, 0, value.draft, null, false)
					}

					dialogs_dict.put(key, value)

					val messageObject = newDialogMessage[value.id]

					dialogMessage.put(key, messageObject)

					if (messageObject != null && messageObject.messageOwner!!.peer_id!!.channel_id == 0L) {
						dialogMessagesByIds.put(messageObject.id, messageObject)
						dialogsLoadedTillDate = min(dialogsLoadedTillDate, messageObject.messageOwner!!.date)

						if (messageObject.messageOwner!!.random_id != 0L) {
							dialogMessagesByRandomIds.put(messageObject.messageOwner!!.random_id, messageObject)
						}
					}
				}

				allDialogs.clear()
				var a = 0
				val size = dialogs_dict.size()

				while (a < size) {
					val dialog = dialogs_dict.valueAt(a)

					if (deletingDialogs.indexOfKey(dialog!!.id) >= 0) {
						a++
						continue
					}

					allDialogs.add(dialog)

					a++
				}

				sortDialogs(null)

				dialogsEndReached.put(0, true)
				serverDialogsEndReached.put(0, false)
				dialogsEndReached.put(1, true)
				serverDialogsEndReached.put(1, false)

				val totalDialogsLoadCount = userConfig.getTotalDialogsCount(0)
				val dialogsLoadOffset = userConfig.getDialogLoadOffsets(0)

				if (totalDialogsLoadCount < 400 && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1L && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != Int.MAX_VALUE.toLong()) {
					loadDialogs(0, 0, 100, false)
				}

				notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
			}
		}
	}

	private fun migrateDialogs(offset: Int, offsetDate: Int, offsetUser: Long, offsetChat: Long, offsetChannel: Long, accessPeer: Long) {
		if (migratingDialogs || offset == -1) {
			return
		}

		migratingDialogs = true

		val req = TL_messages_getDialogs()
		req.excludePinned = false
		req.limit = 100
		req.offsetId = offset
		req.offsetDate = offsetDate

		if (offset == 0) {
			req.offsetPeer = TL_inputPeerEmpty()
		}
		else {
			if (offsetChannel != 0L) {
				req.offsetPeer = TL_inputPeerChannel()
				req.offsetPeer?.channel_id = offsetChannel
			}
			else if (offsetUser != 0L) {
				req.offsetPeer = TL_inputPeerUser()
				req.offsetPeer?.user_id = offsetUser
			}
			else {
				req.offsetPeer = TL_inputPeerChat()
				req.offsetPeer?.chat_id = offsetChat
			}

			req.offsetPeer?.access_hash = accessPeer
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is messages_Dialogs) {
				messagesStorage.storageQueue.postRunnable {
					try {
						var offsetId: Int
						val totalDialogsLoadCount = userConfig.getTotalDialogsCount(0)

						userConfig.setTotalDialogsCount(0, totalDialogsLoadCount + response.dialogs.size)

						var lastMessage: Message? = null

						for (a in response.messages.indices) {
							val message = response.messages[a]

							if (lastMessage == null || message.date < lastMessage.date) {
								lastMessage = message
							}
						}

						offsetId = if (response.dialogs.size >= 100) {
							lastMessage?.id ?: -1
						}
						else {
							for (i in 0..1) {
								userConfig.setDialogsLoadOffset(i, Int.MAX_VALUE, userConfig.migrateOffsetDate, userConfig.migrateOffsetUserId, userConfig.migrateOffsetChatId, userConfig.migrateOffsetChannelId, userConfig.migrateOffsetAccess)
							}

							-1
						}

						val dids = StringBuilder(response.dialogs.size * 12)
						val dialogHashMap = LongSparseArray<Dialog>()

						for (a in response.dialogs.indices) {
							val dialog = response.dialogs[a]
							DialogObject.initDialog(dialog)

							if (dids.isNotEmpty()) {
								dids.append(",")
							}

							dids.append(dialog.id)

							dialogHashMap.put(dialog.id, dialog)
						}

						var cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT did, folder_id FROM dialogs WHERE did IN (%s)", dids))

						while (cursor.next()) {
							val did = cursor.longValue(0)
							val folderId = cursor.intValue(1)
							val dialog = dialogHashMap[did]

							if (dialog != null) {
								if (dialog.folder_id != folderId) {
									continue
								}

								response.dialogs.remove(dialog)

								var a = 0

								while (a < response.messages.size) {
									val message = response.messages[a]

									if (MessageObject.getDialogId(message) != did) {
										a++
										continue
									}

									response.messages.removeAt(a)

									a--

									if (message.id == dialog.top_message) {
										dialog.top_message = 0
										break
									}

									a++
								}
							}

							dialogHashMap.remove(did)
						}

						cursor.dispose()

						cursor = messagesStorage.database.queryFinalized("SELECT min(date) FROM dialogs WHERE date != 0 AND did >> 32 NOT IN (536870912, 1073741824)")

						if (cursor.next()) {
							val date = max(1441062000, cursor.intValue(0))
							var a = 0

							while (a < response.messages.size) {
								val message = response.messages[a]

								if (message.date < date) {
									if (offset != -1) {
										for (i in 0..1) {
											userConfig.setDialogsLoadOffset(i, userConfig.migrateOffsetId, userConfig.migrateOffsetDate, userConfig.migrateOffsetUserId, userConfig.migrateOffsetChatId, userConfig.migrateOffsetChannelId, userConfig.migrateOffsetAccess)
										}

										offsetId = -1
									}

									response.messages.removeAt(a)

									a--

									val did = MessageObject.getDialogId(message)
									val dialog = dialogHashMap[did]

									dialogHashMap.remove(did)

									if (dialog != null) {
										response.dialogs.remove(dialog)
									}
								}

								a++
							}

							if (lastMessage != null && lastMessage.date < date && offset != -1) {
								for (i in 0..1) {
									userConfig.setDialogsLoadOffset(i, userConfig.migrateOffsetId, userConfig.migrateOffsetDate, userConfig.migrateOffsetUserId, userConfig.migrateOffsetChatId, userConfig.migrateOffsetChannelId, userConfig.migrateOffsetAccess)
								}

								offsetId = -1
							}
						}

						cursor.dispose()

						if (lastMessage != null) {
							userConfig.migrateOffsetDate = lastMessage.date

							if (lastMessage.peer_id!!.channel_id != 0L) {
								userConfig.migrateOffsetChannelId = lastMessage.peer_id!!.channel_id
								userConfig.migrateOffsetChatId = 0
								userConfig.migrateOffsetUserId = 0

								for (a in response.chats.indices) {
									val chat = response.chats[a]

									if (chat.id == userConfig.migrateOffsetChannelId) {
										userConfig.migrateOffsetAccess = chat.access_hash
										break
									}
								}
							}
							else if (lastMessage.peer_id!!.chat_id != 0L) {
								userConfig.migrateOffsetChatId = lastMessage.peer_id!!.chat_id
								userConfig.migrateOffsetChannelId = 0
								userConfig.migrateOffsetUserId = 0

								for (a in response.chats.indices) {
									val chat = response.chats[a]

									if (chat.id == userConfig.migrateOffsetChatId) {
										userConfig.migrateOffsetAccess = chat.access_hash
										break
									}
								}
							}
							else if (lastMessage.peer_id!!.user_id != 0L) {
								userConfig.migrateOffsetUserId = lastMessage.peer_id!!.user_id
								userConfig.migrateOffsetChatId = 0
								userConfig.migrateOffsetChannelId = 0

								for (a in response.users.indices) {
									val user = response.users[a]

									if (user.id == userConfig.migrateOffsetUserId) {
										userConfig.migrateOffsetAccess = user.access_hash
										break
									}
								}
							}
						}

						processLoadedDialogs(response, null, 0, offsetId, 0, 0, resetEnd = false, migrate = true, fromCache = false)
					}
					catch (e: Exception) {
						FileLog.e(e)

						AndroidUtilities.runOnUIThread {
							migratingDialogs = false
						}
					}
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					migratingDialogs = false
				}
			}
		}
	}

	fun processLoadedDialogs(dialogsRes: messages_Dialogs, encChats: List<EncryptedChat>?, folderId: Int, offset: Int, count: Int, loadType: Int, resetEnd: Boolean, migrate: Boolean, fromCache: Boolean) {
		Utilities.stageQueue.postRunnable {
			if (!firstGettingTask) {
				getNewDeleteTask(null, null)
				firstGettingTask = true
			}

			val dialogsLoadOffset = userConfig.getDialogLoadOffsets(folderId)

			if (loadType == DIALOGS_LOAD_TYPE_CACHE && dialogsRes.dialogs.isEmpty()) {
				AndroidUtilities.runOnUIThread {
					putUsers(dialogsRes.users, true)

					loadingDialogs.put(folderId, false)

					if (resetEnd) {
						dialogsEndReached.put(folderId, false)
						serverDialogsEndReached.put(folderId, false)
					}
					else if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] == Int.MAX_VALUE.toLong()) {
						dialogsEndReached.put(folderId, true)
						serverDialogsEndReached.put(folderId, true)
					}
					else {
						loadDialogs(folderId, 0, count, false)
					}

					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
				}

				return@postRunnable
			}

			val newDialogsDict = LongSparseArray<Dialog>()
			val encChatsDict: SparseArray<EncryptedChat>?
			val newDialogMessage = LongSparseArray<MessageObject>()
			val usersDict = LongSparseArray<User>()
			val chatsDict = LongSparseArray<Chat>()

			for (user in dialogsRes.users) {
				usersDict.put(user.id, user)
			}

			for (chat in dialogsRes.chats) {
				chatsDict.put(chat.id, chat)
			}

			if (encChats != null) {
				encChatsDict = SparseArray()

				for (encryptedChat in encChats) {
					encChatsDict.put(encryptedChat.id, encryptedChat)
				}
			}
			else {
				encChatsDict = null
			}

			if (loadType == DIALOGS_LOAD_TYPE_CACHE) {
				nextDialogsCacheOffset.put(folderId, offset + count)
			}

			var lastMessage: Message? = null
			val newMessages = ArrayList<MessageObject>()

			for (message in dialogsRes.messages) {
				if (lastMessage == null || message.date < lastMessage.date) {
					lastMessage = message
				}

				if (message.peer_id!!.channel_id != 0L) {
					val chat = chatsDict[message.peer_id!!.channel_id]

					if (chat != null && chat.left && (promoDialogId == 0L || promoDialogId != -chat.id)) {
						continue
					}
				}
				else if (message.peer_id!!.chat_id != 0L) {
					val chat = chatsDict[message.peer_id!!.chat_id]

					if (chat?.migrated_to != null) {
						continue
					}
				}

				val messageObject = MessageObject(currentAccount, message, usersDict, chatsDict, generateLayout = false, checkMediaExists = false)

				newMessages.add(messageObject)

				// MARK: remove check for service messages
				if (message !is TL_messageService) {
					// MARK: but keep this line
					newDialogMessage.put(messageObject.dialogId, messageObject)
				}
			}

			fileLoader.checkMediaExistence(newMessages)

			if (!fromCache && !migrate && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1L && loadType == 0) {
				var totalDialogsLoadCount = userConfig.getTotalDialogsCount(folderId)
				val dialogsLoadOffsetId: Int
				var dialogsLoadOffsetDate = 0
				var dialogsLoadOffsetChannelId: Long = 0
				var dialogsLoadOffsetChatId: Long = 0
				var dialogsLoadOffsetUserId: Long = 0
				var dialogsLoadOffsetAccess: Long = 0

				if (lastMessage != null && lastMessage.id.toLong() != dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId]) {
					totalDialogsLoadCount += dialogsRes.dialogs.size
					dialogsLoadOffsetId = lastMessage.id
					dialogsLoadOffsetDate = lastMessage.date

					if (lastMessage.peer_id!!.channel_id != 0L) {
						dialogsLoadOffsetChannelId = lastMessage.peer_id!!.channel_id
						dialogsLoadOffsetChatId = 0
						dialogsLoadOffsetUserId = 0

						for (chat in dialogsRes.chats) {
							if (chat.id == dialogsLoadOffsetChannelId) {
								dialogsLoadOffsetAccess = chat.access_hash
								break
							}
						}
					}
					else if (lastMessage.peer_id!!.chat_id != 0L) {
						dialogsLoadOffsetChatId = lastMessage.peer_id!!.chat_id
						dialogsLoadOffsetChannelId = 0
						dialogsLoadOffsetUserId = 0

						for (chat in dialogsRes.chats) {
							if (chat.id == dialogsLoadOffsetChatId) {
								dialogsLoadOffsetAccess = chat.access_hash
								break
							}
						}
					}
					else if (lastMessage.peer_id!!.user_id != 0L) {
						dialogsLoadOffsetUserId = lastMessage.peer_id!!.user_id
						dialogsLoadOffsetChatId = 0
						dialogsLoadOffsetChannelId = 0

						for (user in dialogsRes.users) {
							if (user.id == dialogsLoadOffsetUserId) {
								dialogsLoadOffsetAccess = user.access_hash
								break
							}
						}
					}
				}
				else {
					dialogsLoadOffsetId = Int.MAX_VALUE
				}

				userConfig.setDialogsLoadOffset(folderId, dialogsLoadOffsetId, dialogsLoadOffsetDate, dialogsLoadOffsetUserId, dialogsLoadOffsetChatId, dialogsLoadOffsetChannelId, dialogsLoadOffsetAccess)
				userConfig.setTotalDialogsCount(folderId, totalDialogsLoadCount)
				userConfig.saveConfig(false)
			}

			val dialogsToReload = mutableListOf<Dialog>()

			for (d in dialogsRes.dialogs) {
				DialogObject.initDialog(d)

				if (d.id == 0L) {
					continue
				}

				if (DialogObject.isEncryptedDialog(d.id) && encChatsDict != null) {
					if (encChatsDict[DialogObject.getEncryptedChatId(d.id)] == null) {
						continue
					}
				}

				if (promoDialogId != 0L && promoDialogId == d.id) {
					promoDialog = d
				}

				if (d.last_message_date == 0) {
					val mess = newDialogMessage[d.id]

					if (mess != null) {
						d.last_message_date = mess.messageOwner!!.date
					}
				}

				var allowCheck = true

				if (DialogObject.isChannel(d)) {
					val chat = chatsDict[-d.id]

					if (chat != null) {
						if (!chat.megagroup) {
							allowCheck = false
						}

						if (ChatObject.isNotInChat(chat) && (promoDialogId == 0L || promoDialogId != d.id)) {
							continue
						}
					}

					channelsPts.put(-d.id, d.pts)
				}
				else if (DialogObject.isChatDialog(d.id)) {
					val chat = chatsDict[-d.id]

					if (chat != null && (chat.migrated_to != null || ChatObject.isNotInChat(chat))) {
						continue
					}
				}

				newDialogsDict.put(d.id, d)

				if (allowCheck && loadType == DIALOGS_LOAD_TYPE_CACHE && (d.read_outbox_max_id == 0 || d.read_inbox_max_id == 0) && d.top_message != 0) {
					dialogsToReload.add(d)
				}

				var value = dialogs_read_inbox_max[d.id]

				if (value == null) {
					value = 0
				}

				dialogs_read_inbox_max[d.id] = max(value, d.read_inbox_max_id)

				value = dialogs_read_outbox_max[d.id]

				if (value == null) {
					value = 0
				}

				dialogs_read_outbox_max[d.id] = max(value, d.read_outbox_max_id)
			}

			if (loadType != DIALOGS_LOAD_TYPE_CACHE) {
				ImageLoader.saveMessagesThumbs(dialogsRes.messages)

				for (message in dialogsRes.messages) {
					val action = message.action

					if (action is TL_messageActionChatDeleteUser) {
						val user = usersDict[action.user_id]

						if (user != null && user.bot) {
							message.reply_markup = TL_replyKeyboardHide()
							message.flags = message.flags or 64
						}
					}

					if (action is TL_messageActionChatMigrateTo || action is TL_messageActionChannelCreate) {
						message.unread = false
						message.media_unread = false
					}
					else {
						val readMax = if (message.out) dialogs_read_outbox_max else dialogs_read_inbox_max
						var value = readMax[message.dialog_id]

						if (value == null) {
							value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
							readMax[message.dialog_id] = value
						}

						message.unread = value < message.id
					}
				}

				messagesStorage.putDialogs(dialogsRes, if (loadType == DIALOGS_LOAD_TYPE_UNKNOWN) 3 else 0)
			}

			if (loadType == DIALOGS_LOAD_TYPE_CHANNEL) {
				val chat = dialogsRes.chats[0]
				getChannelDifference(chat.id)

				AndroidUtilities.runOnUIThread {
					checkChatInviter(chat.id, true)
				}
			}

			val lastMessageFinal = lastMessage

			AndroidUtilities.runOnUIThread {
				dialogsLoadedTillDate = if (lastMessageFinal != null) {
					min(dialogsLoadedTillDate, lastMessageFinal.date)
				}
				else {
					Int.MIN_VALUE
				}

				if (loadType != DIALOGS_LOAD_TYPE_CACHE) {
					applyDialogsNotificationsSettings(dialogsRes.dialogs)
					mediaDataController.loadDraftsIfNeed()
				}

				putUsers(dialogsRes.users, loadType == DIALOGS_LOAD_TYPE_CACHE)
				putChats(dialogsRes.chats, loadType == DIALOGS_LOAD_TYPE_CACHE)

				if (encChats != null) {
					for (encryptedChat in encChats) {
						if (encryptedChat is TL_encryptedChat && AndroidUtilities.getMyLayerVersion(encryptedChat.layer) < SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
							secretChatHelper.sendNotifyLayerMessage(encryptedChat, null)
						}

						putEncryptedChat(encryptedChat, true)
					}
				}

				if (!migrate && loadType != DIALOGS_LOAD_TYPE_UNKNOWN && loadType != DIALOGS_LOAD_TYPE_CHANNEL) {
					loadingDialogs.put(folderId, false)
				}

				var added = false

				dialogsLoaded = true

				var archivedDialogsCount = 0
				val lastDialogDate = if (migrate && allDialogs.isNotEmpty()) allDialogs.last().last_message_date else 0

				for (a in 0 until newDialogsDict.size()) {
					val key = newDialogsDict.keyAt(a)
					val value = newDialogsDict.valueAt(a)
					val currentDialog = dialogs_dict[key]

					if (migrate && currentDialog != null) {
						currentDialog.folder_id = value.folder_id
					}

					if (migrate && lastDialogDate != 0 && value.last_message_date < lastDialogDate) {
						continue
					}

					if (loadType != DIALOGS_LOAD_TYPE_CACHE && value.draft is TL_draftMessage) {
						mediaDataController.saveDraft(value.id, 0, value.draft, null, false)
					}

					if (value.folder_id != folderId) {
						archivedDialogsCount++
					}

					val newMsg = newDialogMessage[value.id]

					if (currentDialog == null) {
						added = true

						dialogs_dict.put(key, value)
						dialogMessage.put(key, newMsg)

						if (newMsg != null && newMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
							dialogMessagesByIds.put(newMsg.id, newMsg)

							if (newMsg.messageOwner!!.random_id != 0L) {
								dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
							}
						}
					}
					else {
						if (loadType != DIALOGS_LOAD_TYPE_CACHE) {
							currentDialog.notify_settings = value.notify_settings
						}

						currentDialog.pinned = value.pinned
						currentDialog.pinnedNum = value.pinnedNum

						val oldMsg = dialogMessage[key]

						if (oldMsg != null && oldMsg.deleted || oldMsg == null || currentDialog.top_message > 0) {
							if (value.top_message >= currentDialog.top_message) {
								dialogs_dict.put(key, value)
								dialogMessage.put(key, newMsg)

								if (oldMsg != null) {
									if (oldMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
										dialogMessagesByIds.remove(oldMsg.id)
									}

									if (oldMsg.messageOwner!!.random_id != 0L) {
										dialogMessagesByRandomIds.remove(oldMsg.messageOwner!!.random_id)
									}
								}

								if (newMsg != null) {
									if (oldMsg != null && oldMsg.id == newMsg.id) {
										newMsg.deleted = oldMsg.deleted
									}

									if (newMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
										dialogMessagesByIds.put(newMsg.id, newMsg)

										if (newMsg.messageOwner!!.random_id != 0L) {
											dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
										}
									}
								}
							}
						}
						else {
							if (newMsg == null && oldMsg.id > 0 || newMsg != null && newMsg.messageOwner!!.date > oldMsg.messageOwner!!.date) {
								dialogs_dict.put(key, value)
								dialogMessage.put(key, newMsg)

								if (oldMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
									dialogMessagesByIds.remove(oldMsg.id)
								}

								if (newMsg != null) {
									if (newMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
										dialogMessagesByIds.put(newMsg.id, newMsg)

										if (newMsg.messageOwner!!.random_id != 0L) {
											dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
										}
									}
								}

								if (oldMsg.messageOwner!!.random_id != 0L) {
									dialogMessagesByRandomIds.remove(oldMsg.messageOwner!!.random_id)
								}
							}
						}
					}
				}

				allDialogs.clear()

				var a = 0
				val size = dialogs_dict.size()

				while (a < size) {
					val dialog = dialogs_dict.valueAt(a)

					if (deletingDialogs.indexOfKey(dialog!!.id) >= 0) {
						a++
						continue
					}

					allDialogs.add(dialog)

					a++
				}

				sortDialogs(if (migrate) chatsDict else null)

				putAllNeededDraftDialogs()

				if (loadType != DIALOGS_LOAD_TYPE_CHANNEL && loadType != DIALOGS_LOAD_TYPE_UNKNOWN) {
					if (!migrate) {
						dialogsEndReached.put(folderId, (dialogsRes.dialogs.size == 0 || dialogsRes.dialogs.size != count) && loadType == 0)

						if (archivedDialogsCount in 1..19 && folderId == 0) {
							dialogsEndReached.put(1, true)

							val dialogsLoadOffsetArchived = userConfig.getDialogLoadOffsets(folderId)

							if (dialogsLoadOffsetArchived[UserConfig.i_dialogsLoadOffsetId] == Int.MAX_VALUE.toLong()) {
								serverDialogsEndReached.put(1, true)
							}
						}

						if (!fromCache) {
							serverDialogsEndReached.put(folderId, (dialogsRes.dialogs.size == 0 || dialogsRes.dialogs.size != count) && loadType == 0)
						}
					}
				}

				val totalDialogsLoadCount = userConfig.getTotalDialogsCount(folderId)
				val dialogsLoadOffset2 = userConfig.getDialogLoadOffsets(folderId)

				if (!fromCache && !migrate && totalDialogsLoadCount < 400 && dialogsLoadOffset2[UserConfig.i_dialogsLoadOffsetId] != -1L && dialogsLoadOffset2[UserConfig.i_dialogsLoadOffsetId] != Int.MAX_VALUE.toLong()) {
					loadDialogs(folderId, 0, 100, false)
				}

				notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

				if (migrate) {
					userConfig.migrateOffsetId = offset
					userConfig.saveConfig(false)

					migratingDialogs = false

					notificationCenter.postNotificationName(NotificationCenter.needReloadRecentDialogsSearch)
				}
				else {
					generateUpdateMessage()

					if (!added && loadType == DIALOGS_LOAD_TYPE_CACHE && dialogsEndReached[folderId]) {
						loadDialogs(folderId, 0, count, false)
					}
				}

				migrateDialogs(userConfig.migrateOffsetId, userConfig.migrateOffsetDate, userConfig.migrateOffsetUserId, userConfig.migrateOffsetChatId, userConfig.migrateOffsetChannelId, userConfig.migrateOffsetAccess)

				if (dialogsToReload.isNotEmpty()) {
					reloadDialogsReadValue(dialogsToReload, 0)
				}

				loadUnreadDialogs()
			}
		}
	}

	private fun applyDialogNotificationsSettings(dialogId: Long, notifySettings: PeerNotifySettings?) {
		if (notifySettings == null) {
			return
		}

		val currentValue = notificationsPreferences.getInt("notify2_$dialogId", -1)
		val currentValue2 = notificationsPreferences.getInt("notifyuntil_$dialogId", 0)
		val editor = notificationsPreferences.edit()
		var updated = false
		val dialog = dialogs_dict[dialogId]

		if (dialog != null) {
			dialog.notify_settings = notifySettings
		}

		if (notifySettings.flags and 2 != 0) {
			editor.putBoolean("silent_$dialogId", notifySettings.silent)
		}
		else {
			editor.remove("silent_$dialogId")
		}

		if (notifySettings.flags and 4 != 0) {
			if (notifySettings.mute_until > connectionsManager.currentTime) {
				var until = 0

				if (notifySettings.mute_until > connectionsManager.currentTime + 60 * 60 * 24 * 365) {
					if (currentValue != 2) {
						updated = true

						editor.putInt("notify2_$dialogId", 2)

						dialog?.notify_settings?.mute_until = Int.MAX_VALUE
					}
				}
				else {
					if (currentValue != 3 || currentValue2 != notifySettings.mute_until) {
						updated = true

						editor.putInt("notify2_$dialogId", 3)
						editor.putInt("notifyuntil_$dialogId", notifySettings.mute_until)

						dialog?.notify_settings?.mute_until = 0
					}

					until = notifySettings.mute_until
				}

				messagesStorage.setDialogFlags(dialogId, until.toLong() shl 32 or 1L)

				notificationsController.removeNotificationsForDialog(dialogId)
			}
			else {
				if (currentValue != 0 && currentValue != 1) {
					updated = true
					dialog?.notify_settings?.mute_until = 0

					editor.putInt("notify2_$dialogId", 0)
				}

				messagesStorage.setDialogFlags(dialogId, 0)
			}
		}
		else {
			if (currentValue != -1) {
				updated = true
				dialog?.notify_settings?.mute_until = 0

				editor.remove("notify2_$dialogId")
			}

			messagesStorage.setDialogFlags(dialogId, 0)
		}

		applySoundSettings(notifySettings.android_sound, editor, dialogId, 0, false)

		editor.commit()

		if (updated) {
			notificationCenter.postNotificationName(NotificationCenter.notificationsSettingsUpdated)
		}
	}

	private fun applyDialogsNotificationsSettings(dialogs: List<Dialog>) {
		var editor: SharedPreferences.Editor? = null

		for (dialog in dialogs) {
			if (dialog.peer != null && dialog.notify_settings is TL_peerNotifySettings) {
				if (editor == null) {
					editor = notificationsPreferences.edit()
				}

				val dialogId = MessageObject.getPeerId(dialog.peer)

				if (dialog.notify_settings.flags and 2 != 0) {
					editor?.putBoolean("silent_$dialogId", dialog.notify_settings.silent)
				}
				else {
					editor?.remove("silent_$dialogId")
				}

				if (dialog.notify_settings.flags and 4 != 0) {
					if (dialog.notify_settings.mute_until > connectionsManager.currentTime) {
						if (dialog.notify_settings.mute_until > connectionsManager.currentTime + 60 * 60 * 24 * 365) {
							editor?.putInt("notify2_$dialogId", 2)
							dialog.notify_settings.mute_until = Int.MAX_VALUE
						}
						else {
							editor?.putInt("notify2_$dialogId", 3)
							editor?.putInt("notifyuntil_$dialogId", dialog.notify_settings.mute_until)
						}
					}
					else {
						editor?.putInt("notify2_$dialogId", 0)
					}
				}
				else {
					editor?.remove("notify2_$dialogId")
				}
			}
		}

		editor?.commit()
	}

	fun reloadMentionsCountForChannel(peer: InputPeer, taskId: Long) {
		val newTaskId: Long

		if (taskId == 0L) {
			val data = try {
				NativeByteBuffer(4 + peer.objectSize).apply {
					writeInt32(22)
					peer.serializeToStream(this)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
				null
			}

			newTaskId = messagesStorage.createPendingTask(data)
		}
		else {
			newTaskId = taskId
		}

		val req = TL_messages_getUnreadMentions()
		req.peer = peer
		req.limit = 1

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is messages_Messages) {
				val newCount = if (response.count != 0) {
					response.count
				}
				else {
					response.messages.size
				}

				messagesStorage.resetMentionsCount(-peer.channel_id, newCount)
			}

			if (newTaskId != 0L) {
				messagesStorage.removePendingTask(newTaskId)
			}
		}
	}

	fun reloadMentionsCountForChannels(ids: List<Long>) {
		AndroidUtilities.runOnUIThread {
			for (dialogId in ids) {
				reloadMentionsCountForChannel(getInputPeer(-dialogId), 0)
			}
		}
	}

	fun processDialogsUpdateRead(dialogsToUpdate: LongSparseIntArray?, dialogsMentionsToUpdate: LongSparseIntArray?) {
		AndroidUtilities.runOnUIThread {
			var filterDialogsChanged = false

			if (dialogsToUpdate != null) {
				for (a in 0 until dialogsToUpdate.size()) {
					val dialogId = dialogsToUpdate.keyAt(a)
					var currentDialog = dialogs_dict[dialogId]

					if (currentDialog == null) {
						for (i in allDialogs.indices) {
							if (allDialogs[i].id == dialogId) {
								dialogs_dict.put(dialogId, allDialogs[i])
								currentDialog = allDialogs[i]
								break
							}
						}
					}

					if (currentDialog != null) {
						val prevCount = currentDialog.unread_count

						currentDialog.unread_count = dialogsToUpdate.valueAt(a)

						if (prevCount != 0 && currentDialog.unread_count == 0) {
							if (!isDialogMuted(dialogId)) {
								unreadUnmutedDialogs--
							}

							if (!filterDialogsChanged) {
								for (dialogFilter in selectedDialogFilter) {
									if (dialogFilter != null && dialogFilter.flags and DIALOG_FILTER_FLAG_EXCLUDE_READ != 0) {
										filterDialogsChanged = true
										break
									}
								}
							}
						}
						else if (prevCount == 0 && !currentDialog.unread_mark && currentDialog.unread_count != 0) {
							if (!isDialogMuted(dialogId)) {
								unreadUnmutedDialogs++
							}

							if (!filterDialogsChanged) {
								for (dialogFilter in selectedDialogFilter) {
									if (dialogFilter != null && dialogFilter.flags and DIALOG_FILTER_FLAG_EXCLUDE_READ != 0) {
										filterDialogsChanged = true
										break
									}
								}
							}
						}
					}
				}
			}

			if (dialogsMentionsToUpdate != null) {
				for (a in 0 until dialogsMentionsToUpdate.size()) {
					val dialogId = dialogsMentionsToUpdate.keyAt(a)
					val currentDialog = dialogs_dict[dialogId]

					if (currentDialog != null) {
						currentDialog.unread_mentions_count = dialogsMentionsToUpdate.valueAt(a)

						if (createdDialogMainThreadIds.contains(currentDialog.id)) {
							notificationCenter.postNotificationName(NotificationCenter.updateMentionsCount, currentDialog.id, currentDialog.unread_mentions_count)
						}

						if (!filterDialogsChanged) {
							for (dialogFilter in selectedDialogFilter) {
								if (dialogFilter != null && (dialogFilter.flags and DIALOG_FILTER_FLAG_EXCLUDE_MUTED != 0 || dialogFilter.flags and DIALOG_FILTER_FLAG_EXCLUDE_READ != 0)) {
									filterDialogsChanged = true
									break
								}
							}
						}
					}
				}
			}

			if (filterDialogsChanged) {
				sortDialogs(null)
				notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
			}

			notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE)

			if (dialogsToUpdate != null) {
				notificationsController.processDialogsUpdateRead(dialogsToUpdate)
			}
		}
	}

	fun checkLastDialogMessage(dialog: Dialog, peer: InputPeer?, taskId: Long) {
		if (DialogObject.isEncryptedDialog(dialog.id) || checkingLastMessagesDialogs.indexOfKey(dialog.id) >= 0) {
			return
		}

		val req = TL_messages_getHistory()
		req.peer = peer ?: getInputPeer(dialog.id)

		if (req.peer == null) {
			return
		}

		req.limit = 1

		checkingLastMessagesDialogs.put(dialog.id, true)

		val newTaskId: Long

		if (taskId == 0L) {
			var data: NativeByteBuffer? = null

			try {
				data = NativeByteBuffer(60 + req.peer.objectSize)
				data.writeInt32(14)
				data.writeInt64(dialog.id)
				data.writeInt32(dialog.top_message)
				data.writeInt32(dialog.read_inbox_max_id)
				data.writeInt32(dialog.read_outbox_max_id)
				data.writeInt32(dialog.unread_count)
				data.writeInt32(dialog.last_message_date)
				data.writeInt32(dialog.pts)
				data.writeInt32(dialog.flags)
				data.writeBool(dialog.pinned)
				data.writeInt32(dialog.pinnedNum)
				data.writeInt32(dialog.unread_mentions_count)
				data.writeBool(dialog.unread_mark)
				data.writeInt32(dialog.folder_id)
				req.peer.serializeToStream(data)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			newTaskId = messagesStorage.createPendingTask(data)
		}
		else {
			newTaskId = taskId
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is messages_Messages) {
				removeDeletedMessagesFromArray(dialog.id, response.messages)

				if (response.messages.isNotEmpty()) {
					val dialogs = TL_messages_dialogs()
					val newMessage = response.messages[0]

					val newDialog: Dialog = TL_dialog()
					newDialog.flags = dialog.flags
					newDialog.top_message = newMessage.id
					newDialog.last_message_date = newMessage.date
					newDialog.notify_settings = dialog.notify_settings
					newDialog.pts = dialog.pts
					newDialog.unread_count = dialog.unread_count
					newDialog.unread_mark = dialog.unread_mark
					newDialog.unread_mentions_count = dialog.unread_mentions_count
					newDialog.unread_reactions_count = dialog.unread_reactions_count
					newDialog.read_inbox_max_id = dialog.read_inbox_max_id
					newDialog.read_outbox_max_id = dialog.read_outbox_max_id
					newDialog.pinned = dialog.pinned
					newDialog.pinnedNum = dialog.pinnedNum
					newDialog.folder_id = dialog.folder_id
					newDialog.id = dialog.id

					newMessage.dialog_id = newDialog.id

					dialogs.users.addAll(response.users)
					dialogs.chats.addAll(response.chats)
					dialogs.dialogs.add(newDialog)
					dialogs.messages.addAll(response.messages)
					dialogs.count = 1

					processDialogsUpdate(dialogs, false)

					messagesStorage.putMessages(response.messages, true, true, false, downloadController.autodownloadMask, true, false)
				}
				else {
					AndroidUtilities.runOnUIThread {
						if (mediaDataController.getDraft(dialog.id, 0) == null) {
							val currentDialog = dialogs_dict[dialog.id]

							if (currentDialog == null) {
								messagesStorage.isDialogHasTopMessage(dialog.id) { deleteDialog(dialog.id, 3) }
							}
							else {
								if (currentDialog.top_message == 0) {
									deleteDialog(dialog.id, 3)
								}
							}
						}
					}
				}
			}

			if (newTaskId != 0L) {
				messagesStorage.removePendingTask(newTaskId)
			}

			AndroidUtilities.runOnUIThread {
				checkingLastMessagesDialogs.remove(dialog.id)
			}
		}
	}

	fun processDialogsUpdate(dialogsRes: messages_Dialogs, fromCache: Boolean) {
		Utilities.stageQueue.postRunnable {
			val newDialogsDict = LongSparseArray<Dialog>()
			val newDialogMessage = LongSparseArray<MessageObject>()
			val usersDict = LongSparseArray<User>(dialogsRes.users.size)
			val chatsDict = LongSparseArray<Chat>(dialogsRes.chats.size)
			val dialogsToUpdate = LongSparseIntArray()

			for (user in dialogsRes.users) {
				usersDict.put(user.id, user)
			}

			for (chat in dialogsRes.chats) {
				chatsDict.put(chat.id, chat)
			}

			val newMessages = mutableListOf<MessageObject>()

			for (a in dialogsRes.messages.indices) {
				val message = dialogsRes.messages[a]

				if (promoDialogId == 0L || promoDialogId != message.dialog_id) {
					if (message.peer_id!!.channel_id != 0L) {
						val chat = chatsDict[message.peer_id!!.channel_id]

						if (chat != null && ChatObject.isNotInChat(chat)) {
							continue
						}
					}
					else if (message.peer_id!!.chat_id != 0L) {
						val chat = chatsDict[message.peer_id!!.chat_id]

						if (chat != null && (chat.migrated_to != null || ChatObject.isNotInChat(chat))) {
							continue
						}
					}
				}

				val messageObject = MessageObject(currentAccount, message, usersDict, chatsDict, generateLayout = false, checkMediaExists = false)

				newMessages.add(messageObject)

				// MARK: remove check for service messages
				if (message !is TL_messageService) {
					// MARK: but keep this line
					newDialogMessage.put(messageObject.dialogId, messageObject)
				}
			}

			fileLoader.checkMediaExistence(newMessages)

			for (a in dialogsRes.dialogs.indices) {
				val d = dialogsRes.dialogs[a]

				DialogObject.initDialog(d)

				if (promoDialogId == 0L || promoDialogId != d.id) {
					if (DialogObject.isChannel(d)) {
						val chat = chatsDict[-d.id]

						if (chat != null && ChatObject.isNotInChat(chat)) {
							continue
						}
					}
					else if (DialogObject.isChatDialog(d.id)) {
						val chat = chatsDict[-d.id]

						if (chat != null && (chat.migrated_to != null || ChatObject.isNotInChat(chat))) {
							continue
						}
					}
				}

				if (d.last_message_date == 0) {
					val mess = newDialogMessage[d.id]

					if (mess != null) {
						d.last_message_date = mess.messageOwner!!.date
					}
				}

				newDialogsDict.put(d.id, d)

				dialogsToUpdate.put(d.id, d.unread_count)

				var value = dialogs_read_inbox_max[d.id]

				if (value == null) {
					value = 0
				}

				if (d.read_inbox_max_id > d.top_message) {
					d.read_inbox_max_id = d.top_message
				}

				if (value > d.top_message) {
					value = d.top_message
				}

				dialogs_read_inbox_max[d.id] = max(value, d.read_inbox_max_id)

				value = dialogs_read_outbox_max[d.id]

				if (value == null) {
					value = 0
				}

				dialogs_read_outbox_max[d.id] = max(value, d.read_outbox_max_id)
			}

			AndroidUtilities.runOnUIThread {
				putUsers(dialogsRes.users, true)
				putChats(dialogsRes.chats, true)

				for (a in 0 until newDialogsDict.size()) {
					val key = newDialogsDict.keyAt(a)
					val value = newDialogsDict.valueAt(a)
					val currentDialog = dialogs_dict[key]
					val newMsg = newDialogMessage[value.id]

					if (currentDialog == null) {
						val offset = nextDialogsCacheOffset[value.folder_id, 0] + 1

						nextDialogsCacheOffset.put(value.folder_id, offset)
						dialogs_dict.put(key, value)
						dialogMessage.put(key, newMsg)

						if (newMsg == null) {
							if (fromCache) {
								checkLastDialogMessage(value, null, 0)
							}
						}
						else if (newMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
							dialogMessagesByIds.put(newMsg.id, newMsg)
							dialogsLoadedTillDate = min(dialogsLoadedTillDate, newMsg.messageOwner!!.date)

							if (newMsg.messageOwner!!.random_id != 0L) {
								dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
							}
						}
					}
					else {
						currentDialog.unread_count = value.unread_count

						if (currentDialog.unread_mentions_count != value.unread_mentions_count) {
							currentDialog.unread_mentions_count = value.unread_mentions_count

							if (createdDialogMainThreadIds.contains(currentDialog.id)) {
								notificationCenter.postNotificationName(NotificationCenter.updateMentionsCount, currentDialog.id, currentDialog.unread_mentions_count)
							}
						}

						if (currentDialog.unread_reactions_count != value.unread_reactions_count) {
							currentDialog.unread_reactions_count = value.unread_reactions_count
							notificationCenter.postNotificationName(NotificationCenter.dialogsUnreadReactionsCounterChanged, currentDialog.id, currentDialog.unread_reactions_count, null)
						}

						val oldMsg = dialogMessage[key]

						if (oldMsg == null || currentDialog.top_message > 0) {
							if (oldMsg != null && oldMsg.deleted || value.top_message > currentDialog.top_message) {
								dialogs_dict.put(key, value)
								dialogMessage.put(key, newMsg)

								if (oldMsg != null && oldMsg.messageOwner?.peer_id?.channel_id == 0L) {
									dialogMessagesByIds.remove(oldMsg.id)

									if (oldMsg.messageOwner!!.random_id != 0L) {
										dialogMessagesByRandomIds.remove(oldMsg.messageOwner!!.random_id)
									}
								}

								if (newMsg != null) {
									if (oldMsg != null && oldMsg.id == newMsg.id) {
										newMsg.deleted = oldMsg.deleted
									}

									if (newMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
										dialogMessagesByIds.put(newMsg.id, newMsg)
										dialogsLoadedTillDate = min(dialogsLoadedTillDate, newMsg.messageOwner!!.date)

										if (newMsg.messageOwner!!.random_id != 0L) {
											dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
										}
									}
								}
							}

							if (fromCache && newMsg == null) {
								checkLastDialogMessage(value, null, 0)
							}
						}
						else {
							if (oldMsg.deleted || newMsg == null || newMsg.messageOwner!!.date > oldMsg.messageOwner!!.date) {
								dialogs_dict.put(key, value)
								dialogMessage.put(key, newMsg)

								if (oldMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
									dialogMessagesByIds.remove(oldMsg.id)
								}

								if (newMsg != null) {
									if (oldMsg.id == newMsg.id) {
										newMsg.deleted = oldMsg.deleted
									}

									if (newMsg.messageOwner!!.peer_id!!.channel_id == 0L) {
										dialogMessagesByIds.put(newMsg.id, newMsg)
										dialogsLoadedTillDate = min(dialogsLoadedTillDate, newMsg.messageOwner!!.date)

										if (newMsg.messageOwner!!.random_id != 0L) {
											dialogMessagesByRandomIds.put(newMsg.messageOwner!!.random_id, newMsg)
										}
									}
								}

								if (oldMsg.messageOwner?.random_id != 0L) {
									dialogMessagesByRandomIds.remove(oldMsg.messageOwner!!.random_id)
								}
							}
						}
					}
				}

				allDialogs.clear()
				var a = 0
				val size = dialogs_dict.size()

				while (a < size) {
					val dialog = dialogs_dict.valueAt(a)

					if (deletingDialogs.indexOfKey(dialog!!.id) >= 0) {
						a++
						continue
					}

					allDialogs.add(dialog)

					a++
				}

				sortDialogs(null)

				notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

				notificationsController.processDialogsUpdateRead(dialogsToUpdate)
			}
		}
	}

	fun addToViewsQueue(messageObject: MessageObject) {
		Utilities.stageQueue.postRunnable {
			val peer = messageObject.dialogId
			val id = messageObject.id
			var ids = channelViewsToSend[peer]

			if (ids == null) {
				ids = ArrayList()
				channelViewsToSend.put(peer, ids)
			}

			if (!ids.contains(id)) {
				ids.add(id)
			}
		}
	}

	fun loadExtendedMediaForMessages(dialogId: Long, visibleObjects: List<MessageObject>) {
		if (visibleObjects.isEmpty()) {
			return
		}

		val req = TL_messages_getExtendedMedia()
		req.peer = getInputPeer(dialogId)

		for (messageObject in visibleObjects) {
			req.id.add(messageObject.id)
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is Updates) {
				processUpdates(response, false)
			}
		}
	}

	fun loadReactionsForMessages(dialogId: Long, visibleObjects: List<MessageObject>) {
		if (visibleObjects.isEmpty()) {
			return
		}

		val req = TL_messages_getMessagesReactions()
		req.peer = getInputPeer(dialogId)

		for (messageObject in visibleObjects) {
			req.id.add(messageObject.id)
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is Updates) {
				response.updates?.forEach {
					(it as? TL_updateMessageReactions)?.updateUnreadState = false
				}

				processUpdates(response, false)
			}
		}
	}

	fun addToPollsQueue(dialogId: Long, visibleObjects: List<MessageObject>) {
		var array = pollsToCheck[dialogId]

		if (array == null) {
			array = SparseArray()
			pollsToCheck.put(dialogId, array)
			pollsToCheckSize++
		}

		run {
			var a = 0
			val n = array.size()

			while (a < n) {
				val `object` = array.valueAt(a)
				`object`.pollVisibleOnScreen = false
				a++
			}
		}

		val time = connectionsManager.currentTime
		var minExpireTime = Int.MAX_VALUE
		var hasExpiredPolls = false
		var a = 0
		val n = visibleObjects.size

		while (a < n) {
			val messageObject = visibleObjects[a]

			if (messageObject.type != MessageObject.TYPE_POLL) {
				a++
				continue
			}

			val mediaPoll = messageObject.messageOwner?.media as? TL_messageMediaPoll

			if (!mediaPoll!!.poll.closed && mediaPoll.poll.close_date != 0) {
				if (mediaPoll.poll.close_date <= time) {
					hasExpiredPolls = true
				}
				else {
					minExpireTime = min(minExpireTime, mediaPoll.poll.close_date - time)
				}
			}

			val id = messageObject.id
			val `object` = array[id]

			if (`object` != null) {
				`object`.pollVisibleOnScreen = true
			}
			else {
				array.put(id, messageObject)
			}

			a++
		}

		if (hasExpiredPolls) {
			lastViewsCheckTime = 0
		}
		else if (minExpireTime < 5) {
			lastViewsCheckTime = min(lastViewsCheckTime, System.currentTimeMillis() - (5L - minExpireTime) * 1000L)
		}
	}

	fun markMessageContentAsRead(messageObject: MessageObject) {
		if (messageObject.scheduled) {
			return
		}

		if (messageObject.messageOwner?.mentioned == true) {
			messagesStorage.markMentionMessageAsRead(-messageObject.messageOwner!!.peer_id!!.channel_id, messageObject.id, messageObject.dialogId)
		}

		val arrayList = listOf(messageObject.id)
		val dialogId = messageObject.dialogId

		messagesStorage.markMessagesContentAsRead(dialogId, arrayList, 0)

		notificationCenter.postNotificationName(NotificationCenter.messagesReadContent, dialogId, arrayList)

		if (messageObject.id < 0) {
			markMessageAsRead(messageObject.dialogId, messageObject.messageOwner!!.random_id, Int.MIN_VALUE)
		}
		else {
			if (messageObject.messageOwner?.peer_id?.channel_id != 0L) {
				val req = TL_channels_readMessageContents()
				req.channel = getInputChannel(messageObject.messageOwner?.peer_id?.channel_id)

				if (req.channel == null) {
					return
				}

				req.id.add(messageObject.id)

				connectionsManager.sendRequest(req)
			}
			else {
				val req = TL_messages_readMessageContents()
				req.id.add(messageObject.id)

				connectionsManager.sendRequest(req) { response, _ ->
					if (response is TL_messages_affectedMessages) {
						processNewDifferenceParams(-1, response.pts, -1, response.pts_count)
					}
				}
			}
		}
	}

	fun markMentionMessageAsRead(mid: Int, channelId: Long, did: Long) {
		messagesStorage.markMentionMessageAsRead(-channelId, mid, did)

		if (channelId != 0L) {
			val req = TL_channels_readMessageContents()
			req.channel = getInputChannel(channelId)

			if (req.channel == null) {
				return
			}

			req.id.add(mid)

			connectionsManager.sendRequest(req)
		}
		else {
			val req = TL_messages_readMessageContents()
			req.id.add(mid)

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_messages_affectedMessages) {
					processNewDifferenceParams(-1, response.pts, -1, response.pts_count)
				}
			}
		}
	}

	fun markMessageAsRead2(dialogId: Long, mid: Int, inputChannel: InputChannel?, ttl: Int, taskId: Long) {
		@Suppress("NAME_SHADOWING") var inputChannel = inputChannel

		if (mid == 0 || ttl <= 0) {
			return
		}

		if (DialogObject.isChatDialog(dialogId) && inputChannel == null) {
			inputChannel = getInputChannel(dialogId)
		}

		val newTaskId: Long

		if (taskId == 0L) {
			var data: NativeByteBuffer? = null

			try {
				data = NativeByteBuffer(20 + (inputChannel?.objectSize ?: 0))
				data.writeInt32(23)
				data.writeInt64(dialogId)
				data.writeInt32(mid)
				data.writeInt32(ttl)

				inputChannel?.serializeToStream(data)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			newTaskId = messagesStorage.createPendingTask(data)
		}
		else {
			newTaskId = taskId
		}

		val time = connectionsManager.currentTime

		messagesStorage.createTaskForMid(dialogId, mid, time, time, ttl, false)

		if (inputChannel != null) {
			val req = TL_channels_readMessageContents()
			req.channel = inputChannel
			req.id.add(mid)

			connectionsManager.sendRequest(req) { _, _ ->
				if (newTaskId != 0L) {
					messagesStorage.removePendingTask(newTaskId)
				}
			}
		}
		else {
			val req = TL_messages_readMessageContents()
			req.id.add(mid)

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_messages_affectedMessages) {
					processNewDifferenceParams(-1, response.pts, -1, response.pts_count)
				}

				if (newTaskId != 0L) {
					messagesStorage.removePendingTask(newTaskId)
				}
			}
		}
	}

	fun markMessageAsRead(dialogId: Long, randomId: Long, ttl: Int) {
		if (randomId == 0L || dialogId == 0L || ttl <= 0 && ttl != Int.MIN_VALUE) {
			return
		}

		if (!DialogObject.isEncryptedDialog(dialogId)) {
			return
		}

		val chat = getEncryptedChat(DialogObject.getEncryptedChatId(dialogId)) ?: return

		val randomIds = ArrayList<Long>()
		randomIds.add(randomId)

		secretChatHelper.sendMessagesReadMessage(chat, randomIds, null)

		if (ttl > 0) {
			val time = connectionsManager.currentTime

			messagesStorage.createTaskForSecretChat(chat.id, time, time, 0, randomIds)
		}
	}

	private fun completeReadTask(task: ReadTask) {
		if (task.replyId != 0L) {
			val req = TL_messages_readDiscussion()
			req.msg_id = task.replyId.toInt()
			req.peer = getInputPeer(task.dialogId)
			req.read_max_id = task.maxId

			connectionsManager.sendRequest(req)
		}
		else if (!DialogObject.isEncryptedDialog(task.dialogId)) {
			val inputPeer = getInputPeer(task.dialogId)
			val req: TLObject

			if (inputPeer is TL_inputPeerChannel) {
				val request = TL_channels_readHistory()
				request.channel = getInputChannel(-task.dialogId)
				request.max_id = task.maxId

				req = request
			}
			else {
				val request = TL_messages_readHistory()
				request.peer = inputPeer
				request.max_id = task.maxId

				req = request
			}

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_messages_affectedMessages) {
					processNewDifferenceParams(-1, response.pts, -1, response.pts_count)
				}
			}
		}
		else {
			val chat = getEncryptedChat(DialogObject.getEncryptedChatId(task.dialogId))

			if (chat?.auth_key != null && chat.auth_key.size > 1 && chat is TL_encryptedChat) {
				val req = TL_messages_readEncryptedHistory()
				req.peer = TL_inputEncryptedChat()
				req.peer.chat_id = chat.id
				req.peer.access_hash = chat.access_hash
				req.max_date = task.maxDate

				connectionsManager.sendRequest(req)
			}
		}
	}

	private fun checkReadTasks() {
		val time = SystemClock.elapsedRealtime()

		run {
			var a = 0
			var size = readTasks.size

			while (a < size) {
				val task = readTasks[a]

				if (task.sendRequestTime > time) {
					a++
					continue
				}

				completeReadTask(task)

				readTasks.removeAt(a)
				readTasksMap.remove(task.dialogId)

				a--
				size--
				a++
			}
		}

		var a = 0
		var size = repliesReadTasks.size

		while (a < size) {
			val task = repliesReadTasks[a]

			if (task.sendRequestTime > time) {
				a++
				continue
			}

			completeReadTask(task)
			repliesReadTasks.removeAt(a)
			threadsReadTasksMap.remove(task.dialogId.toString() + "_" + task.replyId)

			a--
			size--
			a++
		}
	}

	fun markDialogAsReadNow(dialogId: Long, replyId: Int) {
		Utilities.stageQueue.postRunnable {
			if (replyId != 0) {
				val key = dialogId.toString() + "_" + replyId
				val currentReadTask = threadsReadTasksMap[key] ?: return@postRunnable
				completeReadTask(currentReadTask)
				repliesReadTasks.remove(currentReadTask)
				threadsReadTasksMap.remove(key)
			}
			else {
				val currentReadTask = readTasksMap[dialogId] ?: return@postRunnable
				completeReadTask(currentReadTask)
				readTasks.remove(currentReadTask)
				readTasksMap.remove(dialogId)
			}
		}
	}

	fun markMentionsAsRead(dialogId: Long) {
		if (DialogObject.isEncryptedDialog(dialogId)) {
			return
		}

		messagesStorage.resetMentionsCount(dialogId, 0)

		val req = TL_messages_readMentions()
		req.peer = getInputPeer(dialogId)

		connectionsManager.sendRequest(req)
	}

	fun markDialogAsRead(dialogId: Long, maxPositiveId: Int, maxNegativeId: Int, maxDate: Int, popup: Boolean, threadId: Int, countDiff: Int, readNow: Boolean, scheduledCount: Int) {
		val createReadTask: Boolean

		if (threadId != 0) {
			createReadTask = maxPositiveId != Int.MAX_VALUE
		}
		else {
			if (!DialogObject.isEncryptedDialog(dialogId)) {
				if (maxPositiveId == 0) {
					return
				}

				var value = dialogs_read_inbox_max[dialogId]

				if (value == null) {
					value = 0
				}

				dialogs_read_inbox_max[dialogId] = max(value, maxPositiveId)

				messagesStorage.processPendingRead(dialogId, maxPositiveId, maxNegativeId, scheduledCount)

				messagesStorage.storageQueue.postRunnable {
					AndroidUtilities.runOnUIThread {
						val dialog = dialogs_dict[dialogId]

						if (dialog != null) {
							val prevCount = dialog.unread_count

							if (countDiff == 0 || maxPositiveId >= dialog.top_message) {
								dialog.unread_count = 0
							}
							else {
								dialog.unread_count = max(dialog.unread_count - countDiff, 0)

								if (maxPositiveId != Int.MIN_VALUE && dialog.unread_count > dialog.top_message - maxPositiveId) {
									dialog.unread_count = dialog.top_message - maxPositiveId
								}
							}

							var wasUnread: Boolean

							if (dialog.unread_mark.also { wasUnread = it }) {
								dialog.unread_mark = false

								messagesStorage.setDialogUnread(dialog.id, false)
							}

							if ((prevCount != 0 || wasUnread) && dialog.unread_count == 0) {
								if (!isDialogMuted(dialogId)) {
									unreadUnmutedDialogs--
								}

								for (dialogFilter in selectedDialogFilter) {
									if (dialogFilter != null && dialogFilter.flags and DIALOG_FILTER_FLAG_EXCLUDE_READ != 0) {
										sortDialogs(null)
										notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
										break
									}
								}
							}

							notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE)
						}

						if (!popup) {
							notificationsController.processReadMessages(null, dialogId, 0, maxPositiveId, false)

							val dialogsToUpdate = LongSparseIntArray(1)
							dialogsToUpdate.put(dialogId, 0)

							notificationsController.processDialogsUpdateRead(dialogsToUpdate)
						}
						else {
							notificationsController.processReadMessages(null, dialogId, 0, maxPositiveId, true)

							val dialogsToUpdate = LongSparseIntArray(1)
							dialogsToUpdate.put(dialogId, -1)

							notificationsController.processDialogsUpdateRead(dialogsToUpdate)
						}
					}
				}

				createReadTask = maxPositiveId != Int.MAX_VALUE
			}
			else {
				if (maxDate == 0) {
					return
				}

				createReadTask = true

				val chat = getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))

				messagesStorage.processPendingRead(dialogId, maxPositiveId, maxNegativeId, scheduledCount)

				messagesStorage.storageQueue.postRunnable {
					AndroidUtilities.runOnUIThread {
						notificationsController.processReadMessages(null, dialogId, maxDate, 0, popup)

						val dialog = dialogs_dict[dialogId]

						if (dialog != null) {
							val prevCount = dialog.unread_count

							if (countDiff == 0 || maxNegativeId <= dialog.top_message) {
								dialog.unread_count = 0
							}
							else {
								dialog.unread_count = max(dialog.unread_count - countDiff, 0)

								if (maxNegativeId != Int.MAX_VALUE && dialog.unread_count > maxNegativeId - dialog.top_message) {
									dialog.unread_count = maxNegativeId - dialog.top_message
								}
							}

							var wasUnread: Boolean

							if (dialog.unread_mark.also { wasUnread = it }) {
								dialog.unread_mark = false
								messagesStorage.setDialogUnread(dialog.id, false)
							}

							if ((prevCount != 0 || wasUnread) && dialog.unread_count == 0) {
								if (!isDialogMuted(dialogId)) {
									unreadUnmutedDialogs--
								}

								for (dialogFilter in selectedDialogFilter) {
									if (dialogFilter != null && dialogFilter.flags and DIALOG_FILTER_FLAG_EXCLUDE_READ != 0) {
										sortDialogs(null)
										notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
										break
									}
								}
							}

							notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE)
						}

						val dialogsToUpdate = LongSparseIntArray(1)
						dialogsToUpdate.put(dialogId, 0)

						notificationsController.processDialogsUpdateRead(dialogsToUpdate)
					}
				}

				if (chat != null && chat.ttl > 0) {
					val serverTime = max(connectionsManager.currentTime, maxDate)
					messagesStorage.createTaskForSecretChat(chat.id, maxDate, serverTime, 0, null)
				}
			}
		}

		if (createReadTask) {
			Utilities.stageQueue.postRunnable {
				var currentReadTask = if (threadId != 0) {
					threadsReadTasksMap[dialogId.toString() + "_" + threadId]
				}
				else {
					readTasksMap[dialogId]
				}

				if (currentReadTask == null) {
					currentReadTask = ReadTask()
					currentReadTask.dialogId = dialogId
					currentReadTask.replyId = threadId.toLong()
					currentReadTask.sendRequestTime = SystemClock.elapsedRealtime() + 5000

					if (!readNow) {
						if (threadId != 0) {
							threadsReadTasksMap[dialogId.toString() + "_" + threadId] = currentReadTask
							repliesReadTasks.add(currentReadTask)
						}
						else {
							readTasksMap.put(dialogId, currentReadTask)
							readTasks.add(currentReadTask)
						}
					}
				}

				currentReadTask.maxDate = maxDate
				currentReadTask.maxId = maxPositiveId

				if (readNow) {
					completeReadTask(currentReadTask)
				}
			}
		}
	}

	fun createChat(title: String?, selectedContacts: List<Long>, about: String?, type: Int, forImport: Boolean, location: Location?, locationAddress: String?, fragment: BaseFragment?, adult: Boolean, payType: Int, country: String?, cost: Double, category: String?, channelName: String?): Int {
		return createChat(title, selectedContacts, about, type, forImport, location, locationAddress, fragment, adult, payType, country, cost, category, 0, 0, null, null, channelName)
	}

	fun createChat(title: String?, selectedContacts: List<Long>, about: String?, type: Int, forImport: Boolean, location: Location?, locationAddress: String?, fragment: BaseFragment?, adult: Boolean, payType: Int, country: String?, cost: Double, category: String?, startDate: Long, endDate: Long, genre: String?, subgenre: String?, channelName: String?): Int {
		if (type == ChatObject.CHAT_TYPE_CHAT && !forImport) {
			val req = TL_messages_createChat()
			req.title = title

			for (contact in selectedContacts) {
				val user = getUser(contact) ?: continue
				req.users.add(getInputUser(user))
			}

			return connectionsManager.sendRequest(req, { response, error ->
				if (error != null) {
					AndroidUtilities.runOnUIThread {
						AlertsCreator.processError(currentAccount, error, fragment, req)
						notificationCenter.postNotificationName(NotificationCenter.chatDidFailCreate)
					}

					return@sendRequest
				}

				if (response !is Updates) {
					return@sendRequest
				}

				processUpdates(response, false)

				AndroidUtilities.runOnUIThread {
					putUsers(response.users, false)
					putChats(response.chats, false)

					if (!response.chats.isNullOrEmpty()) {
						notificationCenter.postNotificationName(NotificationCenter.chatDidCreated, response.chats.first().id)
					}
					else {
						notificationCenter.postNotificationName(NotificationCenter.chatDidFailCreate)
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
		else if (forImport || type == ChatObject.CHAT_TYPE_CHANNEL || type == ChatObject.CHAT_TYPE_MEGAGROUP) {
			val req = TL_channels_createChannel()
			req.title = title
			req.about = about ?: ""
			req.for_import = forImport
			req.adult = adult
			req.pay_type = payType
			req.country = country ?: ""
			req.cost = cost
			req.category = category
			req.startDate = startDate
			req.endDate = endDate
			req.genre = genre
			req.sub_genre = subgenre
			req.channel_name = channelName

			if (forImport || type == ChatObject.CHAT_TYPE_MEGAGROUP) {
				req.megagroup = true
			}
			else {
				req.broadcast = true
			}

			if (location != null) {
				req.geo_point = TL_inputGeoPoint()
				req.geo_point.lat = location.latitude
				req.geo_point._long = location.longitude
				req.address = locationAddress
				req.flags = req.flags or 4
			}

			return connectionsManager.sendRequest(req, { response, error ->
				if (error != null) {
					AndroidUtilities.runOnUIThread {
						AlertsCreator.processError(currentAccount, error, fragment, req)
						notificationCenter.postNotificationName(NotificationCenter.chatDidFailCreate)
					}

					return@sendRequest
				}

				val updates = response as? Updates

				processUpdates(updates, false)

				AndroidUtilities.runOnUIThread {
					if (updates == null) {
						notificationCenter.postNotificationName(NotificationCenter.chatDidFailCreate)
						return@runOnUIThread
					}

					putUsers(updates.users, false)
					putChats(updates.chats, false)

					if (updates.chats != null && updates.chats.isNotEmpty()) {
						if (type == ChatObject.CHAT_TYPE_MEGAGROUP) {
							val usersToAdd = ArrayList<InputUser>()

							for (a in selectedContacts.indices) {
								val user = getInputUser(selectedContacts[a])

								if (user !is TL_inputUserEmpty) {
									usersToAdd.add(user)
								}
							}

							if (usersToAdd.isNotEmpty()) {
								addUsersToChannel(updates.chats.first().id, usersToAdd, null)
							}
						}

						notificationCenter.postNotificationName(NotificationCenter.chatDidCreated, updates.chats.first().id)
					}
					else {
						notificationCenter.postNotificationName(NotificationCenter.chatDidFailCreate)
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
		return 0
	}

	@JvmOverloads
	fun convertToMegaGroup(context: Context?, chatId: Long, fragment: BaseFragment?, convertRunnable: LongCallback? = null, errorRunnable: Runnable? = null) {
		val req = TL_messages_migrateChat()
		req.chat_id = chatId

		val progressDialog = context?.let { AlertDialog(it, 3) }

		val reqId = connectionsManager.sendRequest(req) { response, error ->
			if (error == null) {
				if (context != null) {
					AndroidUtilities.runOnUIThread {
						if ((context as? Activity)?.isFinishing == false) {
							try {
								progressDialog?.dismiss()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}
					}
				}

				val updates = response as? Updates

				processUpdates(updates, false)

				AndroidUtilities.runOnUIThread {
					if (convertRunnable != null && updates != null) {
						for (chat in updates.chats) {
							if (ChatObject.isChannel(chat)) {
								convertRunnable.run(chat.id)
								break
							}
						}
					}
				}
			}
			else {
				errorRunnable?.run()

				AndroidUtilities.runOnUIThread {
					convertRunnable?.run(0)

					if (context is Activity) {
						if (!context.isFinishing) {
							try {
								progressDialog?.dismiss()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}

							AlertsCreator.processError(currentAccount, error, fragment, req, false)
						}
					}
				}
			}
		}

		if (progressDialog != null) {
			progressDialog.setOnCancelListener(DialogInterface.OnCancelListener {
				connectionsManager.cancelRequest(reqId, true)
			})

			runCatching {
				progressDialog.show()
			}
		}
	}

	fun convertToGigaGroup(context: Context?, chat: Chat?, fragment: BaseFragment?, convertRunnable: BooleanCallback?) {
		val req = TL_channels_convertToGigagroup()
		req.channel = getInputChannel(chat)

		val progressDialog = context?.let { AlertDialog(it, 3) }

		val reqId = connectionsManager.sendRequest(req) { response, error ->
			if (error == null) {
				if (context is Activity) {
					AndroidUtilities.runOnUIThread {
						if (!context.isFinishing) {
							try {
								progressDialog?.dismiss()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}
					}
				}

				processUpdates(response as? Updates, false)

				AndroidUtilities.runOnUIThread {
					convertRunnable?.run(true)
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					convertRunnable?.run(false)

					if (context is Activity) {
						if (!context.isFinishing) {
							try {
								progressDialog?.dismiss()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}

							AlertsCreator.processError(currentAccount, error, fragment, req, false)
						}
					}
				}
			}
		}

		if (progressDialog != null) {
			progressDialog.setOnCancelListener(DialogInterface.OnCancelListener {
				connectionsManager.cancelRequest(reqId, true)
			})

			runCatching {
				progressDialog.showDelayed(400)
			}
		}
	}

	fun addUsersToChannel(chatId: Long, users: ArrayList<InputUser>?, fragment: BaseFragment?) {
		if (users.isNullOrEmpty()) {
			return
		}

		val req = TL_channels_inviteToChannel()
		req.channel = getInputChannel(chatId)
		req.users = users

		connectionsManager.sendRequest(req) { response, error ->
			if (error != null) {
				AndroidUtilities.runOnUIThread {
					AlertsCreator.processError(currentAccount, error, fragment, req, true)
				}

				return@sendRequest
			}

			processUpdates(response as? Updates, false)
		}
	}

	fun setDefaultSendAs(chatId: Long, newPeer: Long) {
		val cachedFull = getChatFull(-chatId)

		if (cachedFull != null) {
			cachedFull.default_send_as = getPeer(newPeer)

			messagesStorage.updateChatInfo(cachedFull, false)

			notificationCenter.postNotificationName(NotificationCenter.updateDefaultSendAsPeer, chatId, cachedFull.default_send_as)
		}

		val req = TL_messages_saveDefaultSendAs()
		req.peer = getInputPeer(chatId)
		req.send_as = getInputPeer(newPeer)

		connectionsManager.sendRequest(req, { response, error ->
			if (response is TL_boolTrue) {
				val full = getChatFull(-chatId)

				if (full == null) {
					loadFullChat(-chatId, 0, true)
				}
			}
			else if (error?.code == 400) {
				loadFullChat(-chatId, 0, true)
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun toggleChatNoForwards(chatId: Long, enabled: Boolean) {
		val req = TL_messages_toggleNoForwards()
		req.peer = getInputPeer(-chatId)
		req.enabled = enabled

		connectionsManager.sendRequest(req, { response, _ ->
			if (response != null) {
				processUpdates(response as? Updates, false)

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT)
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun toggleChatJoinToSend(chatId: Long, enabled: Boolean, onSuccess: Runnable?, onError: Runnable?) {
		val req = TL_channels_toggleJoinToSend()
		req.channel = getInputChannel(chatId)
		req.enabled = enabled

		connectionsManager.sendRequest(req, { response, error ->
			if (response != null) {
				processUpdates(response as? Updates, false)

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT)
				}

				onSuccess?.run()
			}
			else if (error != null && "CHAT_NOT_MODIFIED" != error.text) {
				onError?.run()
			}
			else {
				onSuccess?.run()
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun toggleChatJoinRequest(chatId: Long, enabled: Boolean, onSuccess: Runnable?, onError: Runnable?) {
		val req = TL_channels_toggleJoinRequest()
		req.channel = getInputChannel(chatId)
		req.enabled = enabled

		connectionsManager.sendRequest(req, { response, error ->
			if (response != null) {
				processUpdates(response as? Updates, false)

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT)
				}

				onSuccess?.run()
			}
			else if (error != null && "CHAT_NOT_MODIFIED" != error.text) {
				onError?.run()
			}
			else {
				onSuccess?.run()
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun toggleChannelSignatures(chatId: Long, enabled: Boolean) {
		val req = TL_channels_toggleSignatures()
		req.channel = getInputChannel(chatId)
		req.enabled = enabled

		connectionsManager.sendRequest(req, { response, _ ->
			if (response != null) {
				processUpdates(response as? Updates, false)

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT)
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun toggleChannelInvitesHistory(chatId: Long, enabled: Boolean) {
		val req = TL_channels_togglePreHistoryHidden()
		req.channel = getInputChannel(chatId)
		req.enabled = enabled

		connectionsManager.sendRequest(req, { response, _ ->
			if (response != null) {
				processUpdates(response as? Updates, false)

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT)
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun toggleGroupInvitesHistory(chatId: Long, historyEnabled: Boolean) {
		val req = TL_messages_migrateChat()
		req.chat_id = chatId
		req.show_history = historyEnabled

		connectionsManager.sendRequest(req, { response, _ ->
			if (response != null) {
				processUpdates(response as? Updates, false)

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT)
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun updateChatAbout(chatId: Long, about: String?, info: ChatFull?) {
		val req = TL_messages_editChatAbout()
		req.peer = getInputPeer(-chatId)
		req.about = about

		connectionsManager.sendRequest(req, { response, _ ->
			if (response is TL_boolTrue && info != null) {
				AndroidUtilities.runOnUIThread {
					info.about = about
					messagesStorage.updateChatInfo(info, false)
					notificationCenter.postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false)
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun updateChannelUserName(chatId: Long, userName: String) {
		val req = TL_channels_updateUsername()

		if (getChat(chatId) is TL_chat) {
			val inputChat: InputChannel = TL_inputChannel()
			inputChat.channel_id = chatId
			req.channel = inputChat
		}
		else {
			req.channel = getInputChannel(chatId)
		}

		req.username = userName

		connectionsManager.sendRequest(req, { response, _ ->
			if (response is TL_boolTrue) {
				AndroidUtilities.runOnUIThread {
					val chat = getChat(chatId) ?: return@runOnUIThread

					if (userName.isNotEmpty()) {
						chat.flags = chat.flags or CHAT_FLAG_IS_PUBLIC
					}
					else {
						chat.flags = chat.flags and CHAT_FLAG_IS_PUBLIC.inv()
					}

					chat.username = userName

					messagesStorage.putUsersAndChats(null, listOf(chat), true, true)

					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT)
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun sendBotStart(user: User?, botHash: String?) {
		if (user == null) {
			return
		}

		val req = TL_messages_startBot()
		req.bot = getInputUser(user)
		req.peer = getInputPeer(user.id)
		req.start_param = botHash
		req.random_id = Utilities.random.nextLong()

		connectionsManager.sendRequest(req) { response, error ->
			if (error != null) {
				return@sendRequest
			}

			processUpdates(response as? Updates, false)
		}
	}

	fun isJoiningChannel(chatId: Long): Boolean {
		return joiningToChannels.contains(chatId)
	}

	fun addUserToChat(chatId: Long, user: User?, forwardCount: Int, botHash: String?, fragment: BaseFragment?, onFinishRunnable: Runnable?) {
		addUserToChat(chatId, user, forwardCount, botHash, fragment, false, onFinishRunnable, null)
	}

	fun addUserToChat(chatId: Long, user: User?, forwardCount: Int, botHash: String?, fragment: BaseFragment?, ignoreIfAlreadyExists: Boolean, onFinishRunnable: Runnable?, onError: ErrorDelegate?) {
		if (user == null) {
			onError?.run(null)
			return
		}

		val request: TLObject
		val isChannel = ChatObject.isChannel(chatId, currentAccount)
		val isMegagroup = isChannel && getChat(chatId)?.megagroup == true
		val inputUser = getInputUser(user)

		if (botHash == null || isChannel && !isMegagroup) {
			if (isChannel) {
				if (inputUser is TL_inputUserSelf) {
					if (joiningToChannels.contains(chatId)) {
						onError?.run(null)
						return
					}

					val req = TL_channels_joinChannel()
					req.channel = getInputChannel(chatId)

					request = req

					joiningToChannels.add(chatId)
				}
				else {
					val req = TL_channels_inviteToChannel()
					req.channel = getInputChannel(chatId)
					req.users.add(inputUser)

					request = req
				}
			}
			else {
				val req = TL_messages_addChatUser()
				req.chat_id = chatId
				req.fwd_limit = forwardCount
				req.user_id = inputUser

				request = req
			}
		}
		else {
			val req = TL_messages_startBot()
			req.bot = inputUser

			if (isChannel) {
				req.peer = getInputPeer(-chatId)
			}
			else {
				req.peer = TL_inputPeerChat()
				req.peer.chat_id = chatId
			}

			req.start_param = botHash
			req.random_id = Utilities.random.nextLong()

			request = req
		}

		connectionsManager.sendRequest(request) { response, error ->
			if (isChannel && inputUser is TL_inputUserSelf) {
				AndroidUtilities.runOnUIThread {
					joiningToChannels.remove(chatId)
				}
			}

			if (error != null) {
				if ("USER_ALREADY_PARTICIPANT" == error.text && ignoreIfAlreadyExists) {
					if (onFinishRunnable != null) {
						AndroidUtilities.runOnUIThread(onFinishRunnable)
					}

					return@sendRequest
				}
				if (onError != null) {
					AndroidUtilities.runOnUIThread {
						val handleErrors = onError.run(error)

						if (handleErrors) {
							AlertsCreator.processError(currentAccount, error, fragment, request, isChannel && !isMegagroup)
						}
					}
				}

				AndroidUtilities.runOnUIThread {
					if (onError == null) {
						AlertsCreator.processError(currentAccount, error, fragment, request, isChannel && !isMegagroup)
					}

					if (isChannel && inputUser is TL_inputUserSelf) {
						notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT)
					}
				}

				return@sendRequest
			}

			var hasJoinMessage = false
			val updates = response as? Updates

			if (updates != null) {
				for (a in updates.updates.indices) {
					val update = updates.updates[a]

					if (update is TL_updateNewChannelMessage) {
						if (update.message.action is TL_messageActionChatAddUser) {
							hasJoinMessage = true
							break
						}
					}
				}
			}

			processUpdates(updates, false)

			if (isChannel) {
				if (!hasJoinMessage && inputUser is TL_inputUserSelf) {
					generateJoinMessage(chatId, true)
				}

				AndroidUtilities.runOnUIThread({
					loadFullChat(chatId, 0, true)
				}, 1000)
			}

			if (isChannel && inputUser is TL_inputUserSelf) {
				messagesStorage.updateDialogsWithDeletedMessages(-chatId, chatId, listOf(), null, true)

				AndroidUtilities.runOnUIThread({
					loadDialogs(0, 0, 100, true)
				}, 1000)
			}

			if (onFinishRunnable != null) {
				AndroidUtilities.runOnUIThread(onFinishRunnable)
			}
		}
	}

	@JvmOverloads
	fun deleteParticipantFromChat(chatId: Long, user: User?, chat: Chat? = null, forceDelete: Boolean = false, revoke: Boolean = false) {
		if (user == null && chat == null) {
			return
		}

		val inputPeer = if (user != null) {
			getInputPeer(user)
		}
		else {
			getInputPeer(chat)
		}

		val request: TLObject
		val ownerChat = getChat(chatId)
		val isChannel = ChatObject.isChannel(ownerChat)

		if (isChannel) {
			if (UserObject.isUserSelf(user)) {
				if (ownerChat?.creator == true && forceDelete) {
					val req = TL_channels_deleteChannel()
					req.channel = getInputChannel(ownerChat)

					request = req
				}
				else {
					val req = TL_channels_leaveChannel()
					req.channel = getInputChannel(ownerChat)

					request = req
				}
			}
			else {
				val req = TL_channels_editBanned()
				req.channel = getInputChannel(ownerChat)
				req.participant = inputPeer
				req.banned_rights = TL_chatBannedRights()
				req.banned_rights?.view_messages = true
				req.banned_rights?.send_media = true
				req.banned_rights?.send_messages = true
				req.banned_rights?.send_stickers = true
				req.banned_rights?.send_gifs = true
				req.banned_rights?.send_games = true
				req.banned_rights?.send_inline = true
				req.banned_rights?.embed_links = true
				req.banned_rights?.pin_messages = true
				req.banned_rights?.send_polls = true
				req.banned_rights?.invite_users = true
				req.banned_rights?.change_info = true

				request = req
			}
		}
		else {
			if (forceDelete) {
				val req = TL_messages_deleteChat()
				req.chat_id = chatId

				connectionsManager.sendRequest(req)

				return
			}

			val req = TL_messages_deleteChatUser()
			req.chat_id = chatId
			req.user_id = getInputUser(user)
			req.revoke_history = true

			request = req
		}

		if (UserObject.isUserSelf(user)) {
			deleteDialog(-chatId, 0, revoke)
		}

		connectionsManager.sendRequest(request, { response, error ->
			if (error != null) {
				return@sendRequest
			}

			val updates = response as? Updates

			processUpdates(updates, false)

			if (isChannel && !UserObject.isUserSelf(user)) {
				AndroidUtilities.runOnUIThread({
					loadFullChat(chatId, 0, true)
				}, 1000)
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	@JvmOverloads
	fun changeChatTitle(chatId: Long, title: String?, delegate: RequestDelegate? = null) {
		val request: TLObject

		if (ChatObject.isChannel(chatId, currentAccount)) {
			val req = TL_channels_editTitle()
			req.channel = getInputChannel(chatId)
			req.title = title

			request = req
		}
		else {
			val req = TL_messages_editChatTitle()
			req.chat_id = chatId
			req.title = title

			request = req
		}

		connectionsManager.sendRequest(request, { response, error ->
			delegate?.run(response, error)

			if (error != null) {
				return@sendRequest
			}

			processUpdates(response as? Updates, false)
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun changeChatAvatar(chatId: Long, oldPhoto: TL_inputChatPhoto?, inputPhoto: InputFile?, inputVideo: InputFile?, videoStartTimestamp: Double, videoPath: String?, smallSize: FileLocation?, bigSize: FileLocation?, callback: Runnable?) {
		val request: TLObject
		val inputChatPhoto: InputChatPhoto

		if (oldPhoto != null) {
			inputChatPhoto = oldPhoto
		}
		else if (inputPhoto != null || inputVideo != null) {
			val uploadedPhoto = TL_inputChatUploadedPhoto()

			if (inputPhoto != null) {
				uploadedPhoto.file = inputPhoto
				uploadedPhoto.flags = uploadedPhoto.flags or 1
			}

			if (inputVideo != null) {
				uploadedPhoto.video = inputVideo
				uploadedPhoto.flags = uploadedPhoto.flags or 2
				uploadedPhoto.video_start_ts = videoStartTimestamp
				uploadedPhoto.flags = uploadedPhoto.flags or 4
			}

			inputChatPhoto = uploadedPhoto
		}
		else {
			inputChatPhoto = TL_inputChatPhotoEmpty()
		}

		if (ChatObject.isChannel(chatId, currentAccount)) {
			val req = TL_channels_editPhoto()
			req.channel = getInputChannel(chatId)
			req.photo = inputChatPhoto

			request = req
		}
		else {
			val req = TL_messages_editChatPhoto()
			req.chat_id = chatId
			req.photo = inputChatPhoto

			request = req
		}

		connectionsManager.sendRequest(request, { response, error ->
			if (error != null) {
				return@sendRequest
			}

			val updates = response as? Updates ?: return@sendRequest

			if (oldPhoto == null) {
				var photo: Photo? = null
				var a = 0
				val n = updates.updates.size

				while (a < n) {
					val update = updates.updates[a]

					if (update is TL_updateNewChannelMessage) {
						val message = update.message
						if (message.action is TL_messageActionChatEditPhoto && message.action?.photo is TL_photo) {
							photo = message.action?.photo
							break
						}
					}
					else if (update is TL_updateNewMessage) {
						val message = update.message

						if (message.action is TL_messageActionChatEditPhoto && message.action?.photo is TL_photo) {
							photo = message.action?.photo
							break
						}
					}

					a++
				}

				if (photo != null) {
					val small = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 150)
					val videoSize = if (photo.video_sizes.isEmpty()) null else photo.video_sizes[0]

					if (small != null && smallSize != null) {
						val destFile = fileLoader.getPathToAttach(small, true)

						val src = fileLoader.getPathToAttach(smallSize, true)
						src.renameTo(destFile)

						val oldKey = smallSize.volume_id.toString() + "_" + smallSize.local_id + "@50_50"
						val newKey = small.location.volume_id.toString() + "_" + small.location.local_id + "@50_50"

						ImageLocation.getForPhoto(small, photo)?.let {
							ImageLoader.instance.replaceImageInCache(oldKey, newKey, it, true)
						}
					}

					val big = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800)

					if (big != null && bigSize != null) {
						val destFile = fileLoader.getPathToAttach(big, true)

						val src = fileLoader.getPathToAttach(bigSize, true)
						src.renameTo(destFile)
					}

					if (videoSize != null && videoPath != null) {
						val destFile = fileLoader.getPathToAttach(videoSize, "mp4", true)

						val src = File(videoPath)
						src.renameTo(destFile)
					}
				}
			}

			processUpdates(updates, false)

			AndroidUtilities.runOnUIThread {
				callback?.run()
				notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_AVATAR)
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	fun unregisterPush() {
		if (userConfig.registeredForPush && SharedConfig.pushString.isEmpty()) {
			val req = TL_account_unregisterDevice()
			req.token = SharedConfig.pushString
			req.token_type = SharedConfig.pushType

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				val userConfig = UserConfig.getInstance(a)

				if (a != currentAccount && userConfig.isClientActivated) {
					req.other_uids.add(userConfig.getClientUserId())
				}
			}

			connectionsManager.sendRequest(req)
		}
	}

	fun performLogout(type: Int) {
		if (type == 1) {
			unregisterPush()

			val req = TL_auth_logOut()

			connectionsManager.sendRequest(req) { response, _ ->
				connectionsManager.cleanup(false)

				AndroidUtilities.runOnUIThread {
					if (response is TL_auth_loggedOut) {
						if (response.future_auth_token != null) {
							val preferences = ApplicationLoader.applicationContext.getSharedPreferences("saved_tokens", Context.MODE_PRIVATE)
							val count = preferences.getInt("count", 0)
							val data = SerializedData(response.objectSize)

							response.serializeToStream(data)

							preferences.edit().putString("log_out_token_$count", Utilities.bytesToHex(data.toByteArray())).putInt("count", count + 1).commit()
						}
					}
				}
			}
		}
		else {
			connectionsManager.cleanup(type == 2)
		}

		userConfig.clearConfig()

		SharedPrefsHelper.cleanupAccount(currentAccount)

		var shouldHandle = true
		val observers = notificationCenter.getObservers(NotificationCenter.appDidLogout)

		if (observers != null) {
			for (observer in observers) {
				if (observer is LaunchActivity) {
					shouldHandle = false
					break
				}
			}
		}

		if (shouldHandle) {
			if (UserConfig.selectedAccount == currentAccount) {
				var account = -1

				for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
					if (UserConfig.getInstance(a).isClientActivated) {
						account = a
						break
					}
				}

				if (account != -1) {
					UserConfig.selectedAccount = account
					UserConfig.getInstance(0).saveConfig(false)

					clearFragments()
				}
			}
		}

		notificationCenter.postNotificationName(NotificationCenter.appDidLogout)

		messagesStorage.cleanup(false)

		cleanup()

		contactsController.deleteUnknownAppAccounts()
	}

	fun generateUpdateMessage() {
		if (gettingAppChangelog || BuildConfig.DEBUG || SharedConfig.lastUpdateVersion == null || SharedConfig.lastUpdateVersion == BuildConfig.VERSION_NAME) {
			return
		}

		gettingAppChangelog = true

		val req = TL_help_getAppChangelog()
		req.prev_app_version = SharedConfig.lastUpdateVersion

		connectionsManager.sendRequest(req) { response, error ->
			if (error == null) {
				SharedConfig.lastUpdateVersion = BuildConfig.VERSION_NAME
				SharedConfig.saveConfig()
			}

			if (response is Updates) {
				processUpdates(response, false)
			}
		}
	}

	fun registerForPush(@PushType pushType: Int, regid: String?) {
		if (regid.isNullOrEmpty() || registeringForPush || userConfig.getClientUserId() == 0L) {
			return
		}

		if (userConfig.registeredForPush && regid == SharedConfig.pushString) {
			return
		}

		registeringForPush = true
		lastPushRegisterSendTime = SystemClock.elapsedRealtime()

		if (SharedConfig.pushAuthKey == null) {
			SharedConfig.pushAuthKey = ByteArray(256)
			Utilities.random.nextBytes(SharedConfig.pushAuthKey)
			SharedConfig.saveConfig()
		}

		val req = TL_account_registerDevice()
		req.token_type = pushType
		req.token = regid
		req.no_muted = false
		req.secret = SharedConfig.pushAuthKey

		for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
			val userConfig = UserConfig.getInstance(a)

			if (a != currentAccount && userConfig.isClientActivated) {
				val uid = userConfig.getClientUserId()
				req.other_uids.add(uid)
			}
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_boolTrue) {
				userConfig.registeredForPush = true

				SharedConfig.pushString = regid
				SharedConfig.pushType = pushType

				userConfig.saveConfig(false)
			}

			AndroidUtilities.runOnUIThread {
				registeringForPush = false
			}
		}
	}

	fun loadCurrentState() {
		if (updatingState) {
			return
		}

		updatingState = true

		val req = TL_updates_getState()

		connectionsManager.sendRequest(req) { response, error ->
			updatingState = false

			if (error == null) {
				val res = response as? TL_updates_state ?: return@sendRequest

				messagesStorage.lastDateValue = res.date
				messagesStorage.lastPtsValue = res.pts
				messagesStorage.lastSeqValue = res.seq
				messagesStorage.lastQtsValue = res.qts

				for (a in 0..2) {
					processUpdatesQueue(a, 2)
				}

				messagesStorage.saveDiffParams(messagesStorage.lastSeqValue, messagesStorage.lastPtsValue, messagesStorage.lastDateValue, messagesStorage.lastQtsValue)
			}
			else {
				if (error.code != 401) {
					loadCurrentState()
				}
			}
		}
	}

	private fun getUpdateSeq(updates: Updates): Int {
		return (updates as? TL_updatesCombined)?.seq_start ?: updates.seq
	}

	private fun setUpdatesStartTime(type: Int, time: Long) {
		when (type) {
			0 -> updatesStartWaitTimeSeq = time
			1 -> updatesStartWaitTimePts = time
			2 -> updatesStartWaitTimeQts = time
		}
	}

	fun getUpdatesStartTime(type: Int): Long {
		return when (type) {
			0 -> updatesStartWaitTimeSeq
			1 -> updatesStartWaitTimePts
			2 -> updatesStartWaitTimeQts
			else -> 0
		}
	}

	private fun isValidUpdate(updates: Updates, type: Int): Int {
		when (type) {
			0 -> {
				val seq = getUpdateSeq(updates)

				return if (messagesStorage.lastSeqValue + 1 == seq || messagesStorage.lastSeqValue == seq) {
					0
				}
				else if (messagesStorage.lastSeqValue < seq) {
					1
				}
				else {
					2
				}
			}

			1 -> {
				return if (updates.pts <= messagesStorage.lastPtsValue) {
					2
				}
				else if (messagesStorage.lastPtsValue + updates.pts_count == updates.pts) {
					0
				}
				else {
					1
				}
			}

			2 -> {
				return if (updates.pts <= messagesStorage.lastQtsValue) {
					2
				}
				else if (messagesStorage.lastQtsValue + updates.updates.size == updates.pts) {
					0
				}
				else {
					1
				}
			}

			else -> {
				return 0
			}
		}
	}

	private fun processChannelsUpdatesQueue(channelId: Long, state: Int) {
		val updatesQueue = updatesQueueChannels[channelId] ?: return
		val channelPts = channelsPts[channelId]

		if (updatesQueue.isEmpty() || channelPts == 0) {
			updatesQueueChannels.remove(channelId)
			return
		}

		updatesQueue.sortWith { updates, updates2 ->
			AndroidUtilities.compare(updates.pts, updates2.pts)
		}

		var anyProceed = false

		if (state == 2) {
			channelsPts.put(channelId, updatesQueue[0].pts)
		}

		var a = 0

		while (a < updatesQueue.size) {
			val updates = updatesQueue[a]

			val updateState = if (updates.pts <= channelPts) {
				2
			}
			else if (channelPts + updates.pts_count == updates.pts) {
				0
			}
			else {
				1
			}

			when (updateState) {
				0 -> {
					processUpdates(updates, true)
					anyProceed = true
					updatesQueue.removeAt(a)
					a--
				}

				1 -> {
					val updatesStartWaitTime = updatesStartWaitTimeChannels[channelId]

					if (updatesStartWaitTime != 0L && (anyProceed || abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500)) {
						if (BuildConfig.DEBUG) {
							FileLog.d("HOLE IN CHANNEL $channelId UPDATES QUEUE - will wait more time")
						}

						if (anyProceed) {
							updatesStartWaitTimeChannels.put(channelId, System.currentTimeMillis())
						}
					}
					else {
						if (BuildConfig.DEBUG) {
							FileLog.d("HOLE IN CHANNEL $channelId UPDATES QUEUE - getChannelDifference ")
						}

						updatesStartWaitTimeChannels.delete(channelId)
						updatesQueueChannels.remove(channelId)

						getChannelDifference(channelId)
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

		updatesQueueChannels.remove(channelId)
		updatesStartWaitTimeChannels.delete(channelId)

		if (BuildConfig.DEBUG) {
			FileLog.d("UPDATES CHANNEL $channelId QUEUE PROCEED - OK")
		}
	}

	private fun processUpdatesQueue(type: Int, state: Int) {
		var updatesQueue: MutableList<Updates>? = null

		when (type) {
			0 -> {
				updatesQueue = updatesQueueSeq

				updatesQueue.sortWith { updates, updates2 ->
					AndroidUtilities.compare(getUpdateSeq(updates), getUpdateSeq(updates2))
				}
			}

			1 -> {
				updatesQueue = updatesQueuePts

				updatesQueue.sortWith { updates, updates2 ->
					AndroidUtilities.compare(updates.pts, updates2.pts)
				}
			}

			2 -> {
				updatesQueue = updatesQueueQts

				updatesQueue.sortWith { updates, updates2 ->
					AndroidUtilities.compare(updates.pts, updates2.pts)
				}
			}
		}

		if (!updatesQueue.isNullOrEmpty()) {
			var anyProceed = false

			if (state == 2) {
				val updates = updatesQueue[0]

				when (type) {
					0 -> messagesStorage.lastSeqValue = getUpdateSeq(updates)
					1 -> messagesStorage.lastPtsValue = updates.pts
					else -> messagesStorage.lastQtsValue = updates.pts
				}
			}

			var a = 0

			while (a < updatesQueue.size) {
				val updates = updatesQueue[a]

				when (isValidUpdate(updates, type)) {
					0 -> {
						processUpdates(updates, true)
						anyProceed = true
						updatesQueue.removeAt(a)
						a--
					}

					1 -> {
						if (getUpdatesStartTime(type) != 0L && (anyProceed || abs(System.currentTimeMillis() - getUpdatesStartTime(type)) <= 1500)) {
							if (BuildConfig.DEBUG) {
								FileLog.d("HOLE IN UPDATES QUEUE - will wait more time")
							}

							if (anyProceed) {
								setUpdatesStartTime(type, System.currentTimeMillis())
							}
						}
						else {
							if (BuildConfig.DEBUG) {
								FileLog.d("HOLE IN UPDATES QUEUE - getDifference")
							}

							setUpdatesStartTime(type, 0)
							updatesQueue.clear()
							getDifference()
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
				FileLog.d("UPDATES QUEUE PROCEED - OK")
			}
		}

		setUpdatesStartTime(type, 0)
	}

	fun loadUnknownChannel(channel: Chat, taskId: Long) {
		if (channel !is TL_channel || gettingUnknownChannels.indexOfKey(channel.id) >= 0) {
			return
		}

		if (channel.access_hash == 0L) {
			if (taskId != 0L) {
				messagesStorage.removePendingTask(taskId)
			}

			return
		}

		val inputPeer = TL_inputPeerChannel()
		inputPeer.channel_id = channel.id
		inputPeer.access_hash = channel.access_hash

		gettingUnknownChannels.put(channel.id, true)

		val req = TL_messages_getPeerDialogs()

		val inputDialogPeer = TL_inputDialogPeer()
		inputDialogPeer.peer = inputPeer

		req.peers.add(inputDialogPeer)

		val newTaskId: Long

		if (taskId == 0L) {
			val data = try {
				NativeByteBuffer(4 + channel.objectSize).apply {
					writeInt32(0)
					channel.serializeToStream(this)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
				null
			}

			newTaskId = messagesStorage.createPendingTask(data)
		}
		else {
			newTaskId = taskId
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_messages_peerDialogs) {
				if (response.dialogs.isNotEmpty() && response.chats.isNotEmpty()) {
					val dialog = response.dialogs[0] as TL_dialog

					val dialogs = TL_messages_dialogs()
					dialogs.dialogs.addAll(response.dialogs)
					dialogs.messages.addAll(response.messages)
					dialogs.users.addAll(response.users)
					dialogs.chats.addAll(response.chats)

					processLoadedDialogs(dialogs, null, dialog.folder_id, 0, 1, DIALOGS_LOAD_TYPE_CHANNEL, resetEnd = false, migrate = false, fromCache = false)
				}
			}

			if (newTaskId != 0L) {
				messagesStorage.removePendingTask(newTaskId)
			}

			gettingUnknownChannels.remove(channel.id)
		}
	}

	fun startShortPoll(chat: Chat?, guid: Int, stop: Boolean) {
		if (chat == null) {
			return
		}

		Utilities.stageQueue.postRunnable {
			var guids = needShortPollChannels[chat.id]
			var onlineGuids = needShortPollOnlines[chat.id]

			if (stop) {
				guids?.remove(guid)

				if (guids.isNullOrEmpty()) {
					needShortPollChannels.remove(chat.id)
				}

				if (chat.megagroup) {
					onlineGuids?.remove(guid)

					if (onlineGuids.isNullOrEmpty()) {
						needShortPollOnlines.remove(chat.id)
					}
				}
			}
			else {
				if (guids == null) {
					guids = ArrayList()
					needShortPollChannels.put(chat.id, guids)
				}

				if (!guids.contains(guid)) {
					guids.add(guid)
				}

				if (shortPollChannels.indexOfKey(chat.id) < 0) {
					getChannelDifference(chat.id, 3, 0, null)
				}

				if (chat.megagroup) {
					if (onlineGuids == null) {
						onlineGuids = ArrayList()
						needShortPollOnlines.put(chat.id, onlineGuids)
					}

					if (!onlineGuids.contains(guid)) {
						onlineGuids.add(guid)
					}

					if (shortPollOnlines.indexOfKey(chat.id) < 0) {
						shortPollOnlines.put(chat.id, 0)
					}
				}
			}
		}
	}

	private fun getChannelDifference(channelId: Long) {
		getChannelDifference(channelId, 0, 0, null)
	}

	fun getChannelDifference(channelId: Long, newDialogType: Int, taskId: Long, inputChannel: InputChannel?) {
		@Suppress("NAME_SHADOWING") var inputChannel = inputChannel

		val gettingDifferenceChannel = gettingDifferenceChannels[channelId, false]

		if (gettingDifferenceChannel) {
			return
		}

		var limit = 100
		var channelPts: Int

		if (newDialogType == 1) {
			channelPts = channelsPts[channelId]

			if (channelPts != 0) {
				return
			}

			channelPts = 1
			limit = 1
		}
		else {
			channelPts = channelsPts[channelId]

			if (channelPts == 0) {
				channelPts = messagesStorage.getChannelPtsSync(channelId)

				if (channelPts != 0) {
					channelsPts.put(channelId, channelPts)
				}

				if (channelPts == 0 && (newDialogType == 2 || newDialogType == 3)) {
					return
				}
			}

			if (channelPts == 0) {
				return
			}
		}

		if (inputChannel == null) {
			var chat = getChat(channelId)

			if (chat == null) {
				chat = messagesStorage.getChatSync(channelId)

				if (chat != null) {
					putChat(chat, true)
				}
			}

			inputChannel = getInputChannel(chat)
		}

		if (inputChannel.access_hash == 0L) {
			if (taskId != 0L) {
				messagesStorage.removePendingTask(taskId)
			}

			return
		}

		val newTaskId: Long

		if (taskId == 0L) {
			val data = try {
				NativeByteBuffer(16 + inputChannel.objectSize).apply {
					writeInt32(25)
					writeInt64(channelId)
					writeInt32(newDialogType)

					inputChannel.serializeToStream(this)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
				null
			}

			newTaskId = messagesStorage.createPendingTask(data)
		}
		else {
			newTaskId = taskId
		}

		gettingDifferenceChannels.put(channelId, true)

		val req = TL_updates_getChannelDifference()
		req.channel = inputChannel
		req.filter = TL_channelMessagesFilterEmpty()
		req.pts = channelPts
		req.limit = limit
		req.force = newDialogType != 3

		if (BuildConfig.DEBUG) {
			FileLog.d("start getChannelDifference with pts = $channelPts channelId = $channelId")
		}

		connectionsManager.sendRequest(req) { response, error ->
			if (response is updates_ChannelDifference) {
				val usersDict = LongSparseArray<User>()

				for (user in response.users) {
					usersDict.put(user.id, user)
				}

				var channel: Chat? = null

				for (chat in response.chats) {
					if (chat.id == channelId) {
						channel = chat
						break
					}
				}

				val channelFinal = channel
				val msgUpdates = mutableListOf<TL_updateMessageID>()

				if (response.other_updates.isNotEmpty()) {
					var a = 0

					while (a < response.other_updates.size) {
						val upd = response.other_updates[a]

						if (upd is TL_updateMessageID) {
							msgUpdates.add(upd)
							response.other_updates.removeAt(a)
							a--
						}

						a++
					}
				}

				messagesStorage.putUsersAndChats(response.users, response.chats, true, true)

				AndroidUtilities.runOnUIThread {
					putUsers(response.users, false)
					putChats(response.chats, false)
				}

				messagesStorage.storageQueue.postRunnable {
					if (msgUpdates.isNotEmpty()) {
						val corrected = SparseArray<LongArray>()

						for (update in msgUpdates) {
							val ids = messagesStorage.updateMessageStateAndId(update.random_id, -channelId, null, update.id, 0, false, -1)

							if (ids != null) {
								corrected.put(update.id, ids)
							}
						}

						if (corrected.size() != 0) {
							AndroidUtilities.runOnUIThread {
								for (a in 0 until corrected.size()) {
									val newId = corrected.keyAt(a)
									val ids = corrected.valueAt(a)

									sendMessagesHelper.processSentMessage(ids[1].toInt())

									notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, ids[1].toInt(), newId, null, ids[0], 0L, -1, false)
								}
							}
						}
					}

					Utilities.stageQueue.postRunnable {
						if (response is TL_updates_channelDifference || response is TL_updates_channelDifferenceEmpty) {
							if (response.new_messages.isNotEmpty()) {
								val messages = LongSparseArray<ArrayList<MessageObject>>()

								ImageLoader.saveMessagesThumbs(response.new_messages)

								val pushMessages = ArrayList<MessageObject>()
								val dialogId = -channelId
								var inboxValue = dialogs_read_inbox_max[dialogId]

								if (inboxValue == null) {
									inboxValue = messagesStorage.getDialogReadMax(false, dialogId)
									dialogs_read_inbox_max[dialogId] = inboxValue
								}

								var outboxValue = dialogs_read_outbox_max[dialogId]

								if (outboxValue == null) {
									outboxValue = messagesStorage.getDialogReadMax(true, dialogId)
									dialogs_read_outbox_max[dialogId] = outboxValue
								}

								for (a in response.new_messages.indices) {
									val message = response.new_messages[a]

									if (message is TL_messageEmpty) {
										continue
									}

									message.unread = !(channelFinal != null && channelFinal.left || (if (message.out) outboxValue else inboxValue) >= message.id || message.action is TL_messageActionChannelCreate)

									val isDialogCreated = createdDialogIds.contains(dialogId)
									val obj = MessageObject(currentAccount, message, usersDict, isDialogCreated, isDialogCreated)

									if ((!obj.isOut || obj.messageOwner?.from_scheduled == true) && obj.isUnread) {
										pushMessages.add(obj)
									}

									val uid = -channelId
									var arr = messages[uid]

									if (arr == null) {
										arr = ArrayList()
										messages.put(uid, arr)
									}

									arr.add(obj)
								}

								AndroidUtilities.runOnUIThread {
									for (a in 0 until messages.size()) {
										val key = messages.keyAt(a)
										val value = messages.valueAt(a)

										updateInterfaceWithMessages(key, value, false)
									}

									notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
								}

								messagesStorage.storageQueue.postRunnable {
									if (pushMessages.isNotEmpty()) {
										AndroidUtilities.runOnUIThread {
											notificationsController.processNewMessages(pushMessages, isLast = true, isFcm = false, countDownLatch = null)
										}
									}

									messagesStorage.putMessages(response.new_messages, true, false, false, downloadController.autodownloadMask, false)
								}
							}

							if (response.other_updates.isNotEmpty()) {
								processUpdateArray(response.other_updates, response.users, response.chats, true, 0)
							}

							processChannelsUpdatesQueue(channelId, 1)

							messagesStorage.saveChannelPts(channelId, response.pts)
						}
						else if (response is TL_updates_channelDifferenceTooLong) {
							val dialogId = -channelId
							var inboxValue = dialogs_read_inbox_max[dialogId]

							if (inboxValue == null) {
								inboxValue = messagesStorage.getDialogReadMax(false, dialogId)
								dialogs_read_inbox_max[dialogId] = inboxValue
							}

							var outboxValue = dialogs_read_outbox_max[dialogId]

							if (outboxValue == null) {
								outboxValue = messagesStorage.getDialogReadMax(true, dialogId)
								dialogs_read_outbox_max[dialogId] = outboxValue
							}

							for (a in response.messages.indices) {
								val message = response.messages[a]
								message.dialog_id = -channelId
								message.unread = !(message.action is TL_messageActionChannelCreate || channelFinal != null && channelFinal.left || (if (message.out) outboxValue else inboxValue) >= message.id)
							}

							messagesStorage.overwriteChannel(channelId, response, newDialogType)
						}

						gettingDifferenceChannels.remove(channelId)

						channelsPts.put(channelId, response.pts)

						if (response.flags and 2 != 0) {
							shortPollChannels.put(channelId, (System.currentTimeMillis() / 1000).toInt() + response.timeout)
						}

						if (!response.isFinal) {
							getChannelDifference(channelId)
						}

						if (BuildConfig.DEBUG) {
							FileLog.d("received channel difference with pts = " + response.pts + " channelId = " + channelId)
							FileLog.d("new_messages = " + response.new_messages.size + " messages = " + response.messages.size + " users = " + response.users.size + " chats = " + response.chats.size + " other updates = " + response.other_updates.size)
						}

						if (newTaskId != 0L) {
							messagesStorage.removePendingTask(newTaskId)
						}
					}
				}
			}
			else if (error != null) {
				AndroidUtilities.runOnUIThread {
					checkChannelError(error.text, channelId)
				}

				gettingDifferenceChannels.remove(channelId)

				if (newTaskId != 0L) {
					messagesStorage.removePendingTask(newTaskId)
				}
			}
		}
	}

	private fun checkChannelError(text: String?, channelId: Long) {
		when (text) {
			"CHANNEL_PRIVATE" -> notificationCenter.postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 0)
			"CHANNEL_PUBLIC_GROUP_NA" -> notificationCenter.postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 1)
			"USER_BANNED_IN_CHANNEL" -> notificationCenter.postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 2)
		}
	}

	fun getDifference() {
		getDifference(messagesStorage.lastPtsValue, messagesStorage.lastDateValue, messagesStorage.lastQtsValue, false)
	}

	fun getDifference(pts: Int, date: Int, qts: Int, slice: Boolean) {
		registerForPush(SharedConfig.pushType, SharedConfig.pushString)

		if (messagesStorage.lastPtsValue == 0) {
			loadCurrentState()
			return
		}

		if (!slice && gettingDifference) {
			return
		}

		gettingDifference = true

		val req = TL_updates_getDifference()
		req.pts = pts
		req.date = date
		req.qts = qts

		if (getDifferenceFirstSync) {
			req.flags = req.flags or 1

			if (ApplicationLoader.isConnectedOrConnectingToWiFi) {
				req.pts_total_limit = 5000
			}
			else {
				req.pts_total_limit = 1000
			}

			getDifferenceFirstSync = false
		}

		if (req.date == 0) {
			req.date = connectionsManager.currentTime
		}

		if (BuildConfig.DEBUG) {
			FileLog.d("start getDifference with date = $date pts = $pts qts = $qts")
		}

		connectionsManager.setIsUpdating(true)

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is updates_Difference) {

				if (response is TL_updates_differenceTooLong) {
					AndroidUtilities.runOnUIThread {
						loadedFullUsers.clear()
						loadedFullChats.clear()

						resetDialogs(true, messagesStorage.lastSeqValue, response.pts, date, qts)
					}
				}
				else {
					if (response is TL_updates_differenceSlice) {
						getDifference(response.intermediate_state.pts, response.intermediate_state.date, response.intermediate_state.qts, true)
					}

					val usersDict = LongSparseArray<User>()
					val chatsDict = LongSparseArray<Chat>()

					for (user in response.users) {
						usersDict.put(user.id, user)
					}

					for (chat in response.chats) {
						chatsDict.put(chat.id, chat)
					}

					val msgUpdates = mutableListOf<TL_updateMessageID>()

					if (response.other_updates.isNotEmpty()) {
						var a = 0

						while (a < response.other_updates.size) {
							val upd = response.other_updates[a]

							if (upd is TL_updateMessageID) {
								msgUpdates.add(upd)
								response.other_updates.removeAt(a)
								a--
							}
							else if (getUpdateType(upd) == 2) {
								val channelId = getUpdateChannelId(upd)
								var channelPts = channelsPts[channelId]

								if (channelPts == 0) {
									channelPts = messagesStorage.getChannelPtsSync(channelId)

									if (channelPts != 0) {
										channelsPts.put(channelId, channelPts)
									}
								}

								if (channelPts != 0 && getUpdatePts(upd) <= channelPts) {
									response.other_updates.removeAt(a)
									a--
								}
							}

							a++
						}
					}

					AndroidUtilities.runOnUIThread {
						loadedFullUsers.clear()
						loadedFullChats.clear()

						putUsers(response.users, false)
						putChats(response.chats, false)
					}

					messagesStorage.storageQueue.postRunnable {
						messagesStorage.putUsersAndChats(response.users, response.chats, true, false)

						if (msgUpdates.isNotEmpty()) {
							val corrected = SparseArray<LongArray>()

							for (a in msgUpdates.indices) {
								val update = msgUpdates[a]
								val ids = messagesStorage.updateMessageStateAndId(update.random_id, 0, null, update.id, 0, false, -1)

								if (ids != null) {
									corrected.put(update.id, ids)
								}
							}

							if (corrected.size() != 0) {
								AndroidUtilities.runOnUIThread {
									for (a in 0 until corrected.size()) {
										val newId = corrected.keyAt(a)
										val ids = corrected.valueAt(a)

										sendMessagesHelper.processSentMessage(ids[1].toInt())

										notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, ids[1].toInt(), newId, null, ids[0], 0L, -1, false)
									}
								}
							}
						}

						Utilities.stageQueue.postRunnable {
							if (response.new_messages.isNotEmpty() || response.new_encrypted_messages.isNotEmpty()) {
								val messages = LongSparseArray<ArrayList<MessageObject>>()

								for (encryptedMessage in response.new_encrypted_messages) {
									val decryptedMessages = secretChatHelper.decryptMessage(encryptedMessage)

									if (!decryptedMessages.isNullOrEmpty()) {
										response.new_messages.addAll(decryptedMessages)
									}
								}

								ImageLoader.saveMessagesThumbs(response.new_messages)

								val pushMessages = ArrayList<MessageObject>()
								val clientUserId = userConfig.getClientUserId()

								for (message in response.new_messages) {
									if (message is TL_messageEmpty) {
										continue
									}

									MessageObject.getDialogId(message)

									if (!DialogObject.isEncryptedDialog(message.dialog_id)) {
										val action = message.action

										if (action is TL_messageActionChatDeleteUser) {
											val user = usersDict[action.user_id]

											if (user != null && user.bot) {
												message.reply_markup = TL_replyKeyboardHide()
												message.flags = message.flags or 64
											}
										}

										if (action is TL_messageActionChatMigrateTo || action is TL_messageActionChannelCreate) {
											message.unread = false
											message.media_unread = false
										}
										else {
											val readMax = if (message.out) dialogs_read_outbox_max else dialogs_read_inbox_max
											var value = readMax[message.dialog_id]

											if (value == null) {
												value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
												readMax[message.dialog_id] = value
											}

											message.unread = value < message.id
										}
									}

									if (message.dialog_id == clientUserId) {
										message.unread = false
										message.media_unread = false
										message.out = true
									}

									val isDialogCreated = createdDialogIds.contains(message.dialog_id)
									val obj = MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated)

									if ((!obj.isOut || obj.messageOwner?.from_scheduled == true) && obj.isUnread) {
										pushMessages.add(obj)
									}

									var arr = messages[message.dialog_id]

									if (arr == null) {
										arr = ArrayList()
										messages.put(message.dialog_id, arr)
									}

									arr.add(obj)
								}

								messagesStorage.storageQueue.postRunnable {
									if (pushMessages.isNotEmpty()) {
										AndroidUtilities.runOnUIThread {
											notificationsController.processNewMessages(pushMessages, response !is TL_updates_differenceSlice, false, null)
										}
									}

									messagesStorage.putMessages(response.new_messages, true, false, false, downloadController.autodownloadMask, false)

									for (a in 0 until messages.size()) {
										val dialogId = messages.keyAt(a)
										val arr = messages.valueAt(a)

										mediaDataController.loadReplyMessagesForMessages(arr, dialogId, false) {
											AndroidUtilities.runOnUIThread {
												updateInterfaceWithMessages(dialogId, arr, false)
												notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
											}
										}
									}
								}
								secretChatHelper.processPendingEncMessages()
							}

							if (response.other_updates.isNotEmpty()) {
								processUpdateArray(response.other_updates, response.users, response.chats, true, 0)
							}

							when (response) {
								is TL_updates_difference -> {
									gettingDifference = false

									messagesStorage.lastSeqValue = response.state.seq
									messagesStorage.lastDateValue = response.state.date
									messagesStorage.lastPtsValue = response.state.pts
									messagesStorage.lastQtsValue = response.state.qts

									connectionsManager.setIsUpdating(false)

									for (a in 0..2) {
										processUpdatesQueue(a, 1)
									}
								}

								is TL_updates_differenceSlice -> {
									messagesStorage.lastDateValue = response.intermediate_state.date
									messagesStorage.lastPtsValue = response.intermediate_state.pts
									messagesStorage.lastQtsValue = response.intermediate_state.qts
								}

								is TL_updates_differenceEmpty -> {
									gettingDifference = false

									messagesStorage.lastSeqValue = response.seq
									messagesStorage.lastDateValue = response.date

									connectionsManager.setIsUpdating(false)

									for (a in 0..2) {
										processUpdatesQueue(a, 1)
									}
								}
							}

							messagesStorage.saveDiffParams(messagesStorage.lastSeqValue, messagesStorage.lastPtsValue, messagesStorage.lastDateValue, messagesStorage.lastQtsValue)

							if (BuildConfig.DEBUG) {
								FileLog.d("received difference with date = " + messagesStorage.lastDateValue + " pts = " + messagesStorage.lastPtsValue + " seq = " + messagesStorage.lastSeqValue + " messages = " + response.new_messages.size + " users = " + response.users.size + " chats = " + response.chats.size + " other updates = " + response.other_updates.size)
							}
						}
					}
				}
			}
			else {
				gettingDifference = false
				connectionsManager.setIsUpdating(false)
			}
		}
	}

	fun markDialogAsUnread(dialogId: Long, peer: InputPeer?, taskId: Long) {
		@Suppress("NAME_SHADOWING") var peer = peer
		val dialog = dialogs_dict[dialogId]

		if (dialog != null) {
			dialog.unread_mark = true

			if (dialog.unread_count == 0 && !isDialogMuted(dialogId)) {
				unreadUnmutedDialogs++
			}

			notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE)

			messagesStorage.setDialogUnread(dialogId, true)

			for (dialogFilter in selectedDialogFilter) {
				if (dialogFilter != null && dialogFilter.flags and DIALOG_FILTER_FLAG_EXCLUDE_READ != 0) {
					sortDialogs(null)
					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
					break
				}
			}
		}

		if (!DialogObject.isEncryptedDialog(dialogId)) {
			if (peer == null) {
				peer = getInputPeer(dialogId)
			}

			if (peer is TL_inputPeerEmpty) {
				return
			}

			val req = TL_messages_markDialogUnread()
			req.unread = true

			val inputDialogPeer = TL_inputDialogPeer()
			inputDialogPeer.peer = peer

			req.peer = inputDialogPeer

			val newTaskId: Long

			if (taskId == 0L) {
				val data = try {
					NativeByteBuffer(12 + peer.objectSize).apply {
						writeInt32(9)
						writeInt64(dialogId)

						peer.serializeToStream(this)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
					null
				}

				newTaskId = messagesStorage.createPendingTask(data)
			}
			else {
				newTaskId = taskId
			}

			connectionsManager.sendRequest(req) { _, _ ->
				if (newTaskId != 0L) {
					messagesStorage.removePendingTask(newTaskId)
				}
			}
		}
	}

	fun loadUnreadDialogs() {
		if (loadingUnreadDialogs || userConfig.unreadDialogsLoaded) {
			return
		}

		loadingUnreadDialogs = true

		val req = TL_messages_getDialogUnreadMarks()

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is Vector) {
					var a = 0
					val size = response.objects.size

					while (a < size) {
						val peer = response.objects[a] as DialogPeer

						if (peer is TL_dialogPeer) {
							val did = if (peer.peer.user_id != 0L) {
								peer.peer.user_id
							}
							else if (peer.peer.chat_id != 0L) {
								-peer.peer.chat_id
							}
							else {
								-peer.peer.channel_id
							}

							messagesStorage.setDialogUnread(did, true)

							val dialog = dialogs_dict[did]

							if (dialog != null && !dialog.unread_mark) {
								dialog.unread_mark = true

								if (dialog.unread_count == 0 && !isDialogMuted(did)) {
									unreadUnmutedDialogs++
								}
							}
						}

						a++
					}

					userConfig.unreadDialogsLoaded = true
					userConfig.saveConfig(false)

					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE)

					loadingUnreadDialogs = false
				}
			}
		}
	}

	fun reorderPinnedDialogs(folderId: Int, order: ArrayList<InputDialogPeer>?, taskId: Long) {
		val req = TL_messages_reorderPinnedDialogs()
		req.folder_id = folderId
		req.force = true

		val newTaskId: Long

		if (taskId == 0L) {
			val dialogs = getDialogs(folderId)

			if (dialogs.isEmpty()) {
				return
			}

			var size = 0
			val dids = ArrayList<Long>()
			val pinned = ArrayList<Int>()
			var a = 0
			val n = dialogs.size

			while (a < n) {
				val dialog = dialogs[a]

				if (dialog is TL_dialogFolder) {
					a++
					continue
				}

				if (!dialog.pinned) {
					if (dialog.id != promoDialogId) {
						break
					}

					a++

					continue
				}

				dids.add(dialog.id)
				pinned.add(dialog.pinnedNum)

				if (!DialogObject.isEncryptedDialog(dialog.id)) {
					val inputPeer = getInputPeer(dialog.id)

					val inputDialogPeer = TL_inputDialogPeer()
					inputDialogPeer.peer = inputPeer

					req.order.add(inputDialogPeer)

					size += inputDialogPeer.objectSize
				}

				a++
			}

			messagesStorage.setDialogsPinned(dids, pinned)

			val data = try {
				NativeByteBuffer(4 + 4 + 4 + size).apply {
					writeInt32(16)
					writeInt32(folderId)
					writeInt32(req.order.size)

					req.order.forEach {
						it.serializeToStream(this)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
				null
			}

			newTaskId = messagesStorage.createPendingTask(data)
		}
		else {
			req.order = order
			newTaskId = taskId
		}

		connectionsManager.sendRequest(req) { _, _ ->
			if (newTaskId != 0L) {
				messagesStorage.removePendingTask(newTaskId)
			}
		}
	}

	fun pinDialog(dialogId: Long, pin: Boolean, peer: InputPeer?, taskId: Long): Boolean {
		@Suppress("NAME_SHADOWING") var peer = peer
		val dialog = dialogs_dict[dialogId]

		if (dialog == null || dialog.pinned == pin) {
			return dialog != null
		}

		val folderId = dialog.folder_id
		val dialogs = getDialogs(folderId)

		dialog.pinned = pin

		if (pin) {
			var maxPinnedNum = 0

			for (a in dialogs.indices) {
				val d = dialogs[a]

				if (d is TL_dialogFolder) {
					continue
				}

				if (!d.pinned) {
					if (d.id != promoDialogId) {
						break
					}

					continue
				}

				maxPinnedNum = max(d.pinnedNum, maxPinnedNum)
			}

			dialog.pinnedNum = maxPinnedNum + 1
		}
		else {
			dialog.pinnedNum = 0
		}

		sortDialogs(null)

		if (!pin && dialogs.isNotEmpty() && dialogs[dialogs.size - 1] === dialog && !dialogsEndReached[folderId]) {
			dialogs.removeAt(dialogs.size - 1)
		}

		notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

		if (!DialogObject.isEncryptedDialog(dialogId)) {
			if (taskId != -1L) {
				if (peer == null) {
					peer = getInputPeer(dialogId)
				}

				if (peer is TL_inputPeerEmpty) {
					return false
				}

				val req = TL_messages_toggleDialogPin()
				req.pinned = pin

				val inputDialogPeer = TL_inputDialogPeer()
				inputDialogPeer.peer = peer

				req.peer = inputDialogPeer

				val newTaskId: Long

				if (taskId == 0L) {
					val data = try {
						NativeByteBuffer(16 + peer.objectSize).apply {
							writeInt32(4)
							writeInt64(dialogId)
							writeBool(pin)

							peer.serializeToStream(this)
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
						null
					}

					newTaskId = messagesStorage.createPendingTask(data)
				}
				else {
					newTaskId = taskId
				}

				connectionsManager.sendRequest(req) { _, _ ->
					if (newTaskId != 0L) {
						messagesStorage.removePendingTask(newTaskId)
					}
				}
			}
		}

		messagesStorage.setDialogPinned(dialogId, dialog.pinnedNum)

		return true
	}

	fun loadPinnedDialogs(folderId: Int) {
		if (loadingPinnedDialogs.indexOfKey(folderId) >= 0 || userConfig.isPinnedDialogsLoaded(folderId)) {
			return
		}

		loadingPinnedDialogs.put(folderId, 1)

		val req = TL_messages_getPinnedDialogs()
		req.folder_id = folderId

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_messages_peerDialogs) {
				val newPinnedDialogs = ArrayList(response.dialogs)

				fetchFolderInLoadedPinnedDialogs(response)

				val toCache = TL_messages_dialogs()
				toCache.users.addAll(response.users)
				toCache.chats.addAll(response.chats)
				toCache.dialogs.addAll(response.dialogs)
				toCache.messages.addAll(response.messages)

				val newDialogMessage = LongSparseArray<MessageObject>()
				val usersDict = LongSparseArray<User>()
				val chatsDict = LongSparseArray<Chat>()

				for (user in response.users) {
					usersDict.put(user.id, user)
				}

				for (chat in response.chats) {
					chatsDict.put(chat.id, chat)
				}

				val newMessages = mutableListOf<MessageObject>()

				for (message in response.messages) {
					val peerId = message.peer_id ?: continue

					if (peerId.channel_id != 0L) {
						val chat = chatsDict[peerId.channel_id]

						if (chat != null && chat.left) {
							continue
						}
					}
					else if (peerId.chat_id != 0L) {
						val chat = chatsDict[peerId.chat_id]

						if (chat?.migrated_to != null) {
							continue
						}
					}

					val messageObject = MessageObject(currentAccount, message, usersDict, chatsDict, generateLayout = false, checkMediaExists = false)

					newMessages.add(messageObject)

					// MARK: remove check for service messages
					if (message !is TL_messageService) {
						// MARK: but keep this line
						newDialogMessage.put(messageObject.dialogId, messageObject)
					}
				}

				fileLoader.checkMediaExistence(newMessages)

				val firstIsFolder = newPinnedDialogs.isNotEmpty() && newPinnedDialogs[0] is TL_dialogFolder
				var a = 0
				val n = newPinnedDialogs.size

				while (a < n) {
					val d = newPinnedDialogs[a]

					d?.pinned = true

					DialogObject.initDialog(d)

					if (DialogObject.isChannel(d)) {
						val chat = chatsDict[-d.id]

						if (chat != null && chat.left) {
							a++
							continue
						}
					}
					else if (DialogObject.isChatDialog(d.id)) {
						val chat = chatsDict[-d.id]

						if (chat?.migrated_to != null) {
							a++
							continue
						}
					}

					if (d.last_message_date == 0) {
						val mess = newDialogMessage[d.id]
						if (mess != null) {
							d.last_message_date = mess.messageOwner!!.date
						}
					}

					var value = dialogs_read_inbox_max[d.id]

					if (value == null) {
						value = 0
					}

					dialogs_read_inbox_max[d.id] = max(value, d.read_inbox_max_id)

					value = dialogs_read_outbox_max[d.id]

					if (value == null) {
						value = 0
					}

					dialogs_read_outbox_max[d.id] = max(value, d.read_outbox_max_id)

					a++
				}

				messagesStorage.storageQueue.postRunnable {
					AndroidUtilities.runOnUIThread {
						loadingPinnedDialogs.delete(folderId)

						applyDialogsNotificationsSettings(newPinnedDialogs)

						var changed = false
						var added = false
						var maxPinnedNum = 0
						val dialogs = getDialogs(folderId)
						var pinnedNum = if (firstIsFolder) 1 else 0

						for (dialog in dialogs) {
							if (dialog is TL_dialogFolder) {
								continue
							}

							if (DialogObject.isEncryptedDialog(dialog.id)) {
								if (pinnedNum < newPinnedDialogs.size) {
									newPinnedDialogs.add(pinnedNum, dialog)
								}
								else {
									newPinnedDialogs.add(dialog)
								}

								pinnedNum++

								continue
							}

							if (!dialog.pinned) {
								if (dialog.id != promoDialogId) {
									break
								}

								continue
							}

							maxPinnedNum = max(dialog.pinnedNum, maxPinnedNum)

							dialog.pinned = false
							dialog.pinnedNum = 0

							changed = true

							pinnedNum++
						}

						val pinnedDialogs = mutableListOf<Long>()

						if (newPinnedDialogs.isNotEmpty()) {
							putUsers(response.users, false)
							putChats(response.chats, false)
							val dids = ArrayList<Long>()
							val pinned = ArrayList<Int>()
							var a = 0
							val n = newPinnedDialogs.size

							while (a < n) {
								val dialog = newPinnedDialogs[a]

								dialog.pinnedNum = n - a + maxPinnedNum

								pinnedDialogs.add(dialog.id)

								val d = dialogs_dict[dialog.id]

								if (d != null) {
									d.pinned = true
									d.pinnedNum = dialog.pinnedNum

									dids.add(dialog.id)

									pinned.add(dialog.pinnedNum)
								}
								else {
									added = true

									dialogs_dict.put(dialog.id, dialog)

									val messageObject = newDialogMessage[dialog.id]

									dialogMessage.put(dialog.id, messageObject)

									if (messageObject != null && messageObject.messageOwner?.peer_id?.channel_id == 0L) {
										dialogMessagesByIds.put(messageObject.id, messageObject)

										dialogsLoadedTillDate = min(dialogsLoadedTillDate, messageObject.messageOwner!!.date)

										if (messageObject.messageOwner!!.random_id != 0L) {
											dialogMessagesByRandomIds.put(messageObject.messageOwner!!.random_id, messageObject)
										}
									}
								}

								changed = true

								a++
							}

							messagesStorage.setDialogsPinned(dids, pinned)
						}

						if (changed) {
							if (added) {
								allDialogs.clear()

								var a = 0
								val size = dialogs_dict.size()

								while (a < size) {
									val dialog = dialogs_dict.valueAt(a)

									if (dialog != null) {
										if (deletingDialogs.indexOfKey(dialog.id) >= 0) {
											a++
											continue
										}

										allDialogs.add(dialog)
									}

									a++
								}
							}

							sortDialogs(null)

							notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
						}

						messagesStorage.unpinAllDialogsExceptNew(pinnedDialogs, folderId)
						messagesStorage.putDialogs(toCache, 1)

						userConfig.setPinnedDialogsLoaded(folderId, true)
						userConfig.saveConfig(false)
					}
				}
			}
		}
	}

	fun generateJoinMessage(chatId: Long, ignoreLeft: Boolean) {
		val chat = getChat(chatId)

		if (chat == null || !ChatObject.isChannel(chatId, currentAccount) || (chat.left || chat.kicked) && !ignoreLeft) {
			return
		}

		val message = TL_messageService()
		message.flags = MESSAGE_FLAG_HAS_FROM_ID
		message.id = userConfig.newMessageId
		message.local_id = message.id
		message.date = connectionsManager.currentTime
		message.from_id = TL_peerUser()
		message.from_id?.user_id = userConfig.getClientUserId()
		message.peer_id = TL_peerChannel()
		message.peer_id?.channel_id = chatId
		message.dialog_id = -chatId
		message.post = true
		message.action = TL_messageActionChatAddUser()
		message.action?.users?.add(userConfig.getClientUserId())

		userConfig.saveConfig(false)

		val messagesArr = listOf(message)

		val obj = MessageObject(currentAccount, message, generateLayout = true, checkMediaExists = false)

		val pushMessages = listOf(obj)

		messagesStorage.storageQueue.postRunnable {
			AndroidUtilities.runOnUIThread {
				notificationsController.processNewMessages(pushMessages, isLast = true, isFcm = false, countDownLatch = null)
				messagesController.markMessageContentAsRead(obj)
			}
		}

		messagesStorage.putMessages(messagesArr, true, true, false, 0, false)

		AndroidUtilities.runOnUIThread {
			updateInterfaceWithMessages(-chatId, pushMessages, false)
			notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
		}
	}

	fun deleteMessagesByPush(dialogId: Long, ids: List<Int>, channelId: Long) {
		messagesStorage.storageQueue.postRunnable {
			AndroidUtilities.runOnUIThread {
				notificationCenter.postNotificationName(NotificationCenter.messagesDeleted, ids, channelId, false)

				if (channelId == 0L) {
					var b = 0
					val size2 = ids.size

					while (b < size2) {
						val id = ids[b]

						val obj = dialogMessagesByIds[id]
						obj?.deleted = true

						b++
					}
				}
				else {
					val obj = dialogMessage[-channelId]

					if (obj != null) {
						var b = 0
						val size2 = ids.size

						while (b < size2) {
							if (obj.id == ids[b]) {
								obj.deleted = true
								break
							}

							b++
						}
					}
				}
			}

			messagesStorage.deletePushMessages(dialogId, ids)

			val dialogIds = messagesStorage.markMessagesAsDeleted(dialogId, ids, false, true, false)

			messagesStorage.updateDialogsWithDeletedMessages(dialogId, channelId, ids, dialogIds, false)
		}
	}

	fun checkChatInviter(chatId: Long, createMessage: Boolean) {
		val chat = getChat(chatId)

		if (chat == null || !ChatObject.isChannel(chat) || chat.creator || gettingChatInviters.indexOfKey(chatId) >= 0) {
			return
		}

		gettingChatInviters.put(chatId, true)

		val req = TL_channels_getParticipant()
		req.channel = getInputChannel(chatId)
		req.participant = getInputPeer(userConfig.getClientUserId())

		connectionsManager.sendRequest(req) { response, _ ->
			val res = response as TL_channels_channelParticipant?

			if (res != null && res.participant is TL_channelParticipantSelf) {
				val selfParticipant = res.participant as TL_channelParticipantSelf

				if (selfParticipant.inviter_id != userConfig.getClientUserId() || selfParticipant.via_invite) {
					if (chat.megagroup && messagesStorage.isMigratedChat(chat.id)) {
						return@sendRequest
					}

					AndroidUtilities.runOnUIThread {
						putUsers(res.users, false)
						putChats(res.chats, false)
					}

					messagesStorage.putUsersAndChats(res.users, res.chats, true, true)

					val pushMessages: ArrayList<MessageObject>?

					if (createMessage && abs(connectionsManager.currentTime - res.participant.date) < 24 * 60 * 60 && !messagesStorage.hasInviteMeMessage(chatId)) {
						val message = TL_messageService()
						message.media_unread = true
						message.unread = true
						message.flags = MESSAGE_FLAG_HAS_FROM_ID
						message.post = true
						message.id = userConfig.newMessageId
						message.local_id = message.id
						message.date = res.participant.date

						if (selfParticipant.inviter_id != userConfig.getClientUserId()) {
							message.action = TL_messageActionChatAddUser()
						}
						else if (selfParticipant.via_invite) {
							message.action = TL_messageActionChatJoinedByRequest()
						}

						message.from_id = TL_peerUser()
						message.from_id?.user_id = res.participant.inviter_id
						message.action?.users?.add(userConfig.getClientUserId())
						message.peer_id = TL_peerChannel()
						message.peer_id?.channel_id = chatId
						message.dialog_id = -chatId

						userConfig.saveConfig(false)

						pushMessages = ArrayList()

						val usersDict = ConcurrentHashMap<Long, User>()

						for (user in res.users) {
							usersDict[user.id] = user
						}

						val messagesArr = listOf(message)
						val obj = MessageObject(currentAccount, message, usersDict, generateLayout = true, checkMediaExists = false)

						pushMessages.add(obj)

						messagesStorage.storageQueue.postRunnable {
							AndroidUtilities.runOnUIThread {
								notificationsController.processNewMessages(pushMessages, isLast = true, isFcm = false, countDownLatch = null)
							}
						}

						messagesStorage.putMessages(messagesArr, true, true, false, 0, false)
					}
					else {
						pushMessages = null
					}

					messagesStorage.saveChatInviter(chatId, res.participant.inviter_id)

					AndroidUtilities.runOnUIThread {
						gettingChatInviters.remove(chatId)

						if (pushMessages != null) {
							updateInterfaceWithMessages(-chatId, pushMessages, false)
							notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
						}

						notificationCenter.postNotificationName(NotificationCenter.didLoadChatInviter, chatId, res.participant.inviter_id)
					}
				}
			}
		}
	}

	private fun getUpdateType(update: Update): Int {
		return when (update) {
			is TL_updateNewMessage, is TL_updateReadMessagesContents, is TL_updateReadHistoryInbox, is TL_updateReadHistoryOutbox, is TL_updateDeleteMessages, is TL_updateWebPage, is TL_updateEditMessage, is TL_updateFolderPeers, is TL_updatePinnedMessages -> {
				0
			}

			is TL_updateNewEncryptedMessage -> {
				1
			}

			is TL_updateNewChannelMessage, is TL_updateDeleteChannelMessages, is TL_updateEditChannelMessage, is TL_updateChannelWebPage, is TL_updatePinnedChannelMessages -> {
				2
			}

			else -> {
				3
			}
		}
	}

	fun processUpdates(updates: Updates?, fromQueue: Boolean) {
		var needGetChannelsDiff: ArrayList<Long>? = null
		var needGetDiff = false
		var needReceivedQueue = false
		var updateStatus = false

		when (updates) {
			is TL_updateShort -> {
				processUpdateArray(listOf(updates.update), null, null, false, updates.date)
			}

			is TL_updateShortChatMessage, is TL_updateShortMessage -> {
				val userId = (updates as? TL_updateShortChatMessage)?.from_id ?: updates.user_id
				var user = getUser(userId)
				var user2: User? = null
				var user3: User? = null
				var channel: Chat? = null

				FileLog.d("update message short userId = $userId")

				if (user == null || user.min) {
					user = messagesStorage.getUserSync(userId)

					if (user != null && user.min) {
						user = null
					}

					putUser(user, true)
				}

				var needFwdUser = false

				if (updates.fwd_from != null) {
					when (updates.fwd_from.from_id) {
						is TL_peerUser -> {
							user2 = getUser(updates.fwd_from.from_id.user_id)

							if (user2 == null) {
								user2 = messagesStorage.getUserSync(updates.fwd_from.from_id.user_id)
								putUser(user2, true)
							}

							needFwdUser = true
						}

						is TL_peerChannel -> {
							channel = getChat(updates.fwd_from.from_id.channel_id)

							if (channel == null) {
								channel = messagesStorage.getChatSync(updates.fwd_from.from_id.channel_id)
								putChat(channel, true)
							}

							needFwdUser = true
						}

						is TL_peerChat -> {
							channel = getChat(updates.fwd_from.from_id.chat_id)

							if (channel == null) {
								channel = messagesStorage.getChatSync(updates.fwd_from.from_id.chat_id)
								putChat(channel, true)
							}

							needFwdUser = true
						}
					}
				}

				var needBotUser = false

				if (updates.via_bot_id != 0L) {
					user3 = getUser(updates.via_bot_id)

					if (user3 == null) {
						user3 = messagesStorage.getUserSync(updates.via_bot_id)
						putUser(user3, true)
					}

					needBotUser = true
				}

				var missingData: Boolean

				if (updates is TL_updateShortMessage) {
					missingData = user == null || needFwdUser && user2 == null && channel == null || needBotUser && user3 == null
				}
				else {
					var chat = getChat(updates.chat_id)

					if (chat == null) {
						chat = messagesStorage.getChatSync(updates.chat_id)
						putChat(chat, true)
					}

					missingData = chat == null || user == null || needFwdUser && user2 == null && channel == null || needBotUser && user3 == null
				}

				if (!missingData && updates.entities.isNotEmpty()) {
					for (a in updates.entities.indices) {
						val entity = updates.entities[a]

						if (entity is TL_messageEntityMentionName) {
							val uid = entity.userId
							var entityUser = getUser(uid)

							if (entityUser == null || entityUser.min) {
								entityUser = messagesStorage.getUserSync(uid)

								if (entityUser != null && entityUser.min) {
									entityUser = null
								}

								if (entityUser == null) {
									missingData = true
									break
								}

								putUser(user, true)
							}
						}
					}
				}

				if (!updates.out && user != null && user.status != null && user.status!!.expires <= 0 && abs(connectionsManager.currentTime - updates.date) < 30) {
					onlinePrivacy[user.id] = updates.date
					updateStatus = true
				}

				if (missingData) {
					needGetDiff = true
				}
				else {
					if (messagesStorage.lastPtsValue + updates.pts_count == updates.pts) {
						val message = TL_message()
						message.id = updates.id

						val clientUserId = userConfig.getClientUserId()

						if (updates is TL_updateShortMessage) {
							message.from_id = TL_peerUser()

							if (updates.out) {
								message.from_id?.user_id = clientUserId
							}
							else {
								message.from_id?.user_id = userId
							}

							message.peer_id = TL_peerUser()
							message.peer_id?.user_id = userId
							message.dialog_id = userId
						}
						else {
							message.from_id = TL_peerUser()
							message.from_id?.user_id = userId
							message.peer_id = TL_peerChat()
							message.peer_id?.chat_id = updates.chat_id
							message.dialog_id = -updates.chat_id
						}

						message.fwd_from = updates.fwd_from
						message.silent = updates.silent
						message.out = updates.out
						message.mentioned = updates.mentioned
						message.media_unread = updates.media_unread
						message.entities = updates.entities
						message.message = updates.message
						message.date = updates.date
						message.via_bot_id = updates.via_bot_id
						message.flags = updates.flags or MESSAGE_FLAG_HAS_FROM_ID
						message.reply_to = updates.reply_to
						message.ttl_period = updates.ttl_period
						message.media = TL_messageMediaEmpty()

						val readMax = if (message.out) dialogs_read_outbox_max else dialogs_read_inbox_max

						var value = readMax[message.dialog_id]

						if (value == null) {
							value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
							readMax[message.dialog_id] = value
						}

						message.unread = value < message.id

						if (message.dialog_id == clientUserId) {
							message.unread = false
							message.media_unread = false
							message.out = true
						}

						messagesStorage.lastPtsValue = updates.pts

						val isDialogCreated = createdDialogIds.contains(message.dialog_id)
						val obj = MessageObject(currentAccount, message, isDialogCreated, isDialogCreated)
						val objArr = listOf(obj)
						val arr = listOf(message)

						if (updates is TL_updateShortMessage) {
							val printUpdate = !updates.out && updatePrintingUsersWithNewMessages(updates.user_id, objArr)

							if (printUpdate) {
								updatePrintingStrings()
							}

							AndroidUtilities.runOnUIThread {
								if (printUpdate) {
									notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT)
								}

								updateInterfaceWithMessages(userId, objArr, false)

								notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
							}
						}
						else {
							val printUpdate = updatePrintingUsersWithNewMessages(-updates.chat_id, objArr)

							if (printUpdate) {
								updatePrintingStrings()
							}

							AndroidUtilities.runOnUIThread {
								if (printUpdate) {
									notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT)
								}

								updateInterfaceWithMessages(-updates.chat_id, objArr, false)

								notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
							}
						}

						if (!obj.isOut) {
							messagesStorage.storageQueue.postRunnable {
								AndroidUtilities.runOnUIThread {
									notificationsController.processNewMessages(objArr, isLast = true, isFcm = false, countDownLatch = null)
								}
							}
						}

						messagesStorage.putMessages(arr, false, true, false, 0, false)
					}
					else if (messagesStorage.lastPtsValue != updates.pts) {
						if (BuildConfig.DEBUG) {
							FileLog.d("need get diff short message, pts: " + messagesStorage.lastPtsValue + " " + updates.pts + " count = " + updates.pts_count)
						}

						if (gettingDifference || updatesStartWaitTimePts == 0L || abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
							if (updatesStartWaitTimePts == 0L) {
								updatesStartWaitTimePts = System.currentTimeMillis()
							}

							if (BuildConfig.DEBUG) {
								FileLog.d("add to queue")
							}

							updatesQueuePts.add(updates)
						}
						else {
							needGetDiff = true
						}
					}
				}
			}

			is TL_updatesCombined, is TL_updates -> {
				var minChannels: LongSparseArray<Chat>? = null

				for (a in updates.chats.indices) {
					val chat = updates.chats[a]

					if (chat is TL_channel) {
						if (chat.min) {
							var existChat = getChat(chat.id)

							if (existChat == null || existChat.min) {
								val cacheChat = messagesStorage.getChatSync(updates.chat_id)
								putChat(cacheChat, true)
								existChat = cacheChat
							}

							if (existChat == null || existChat.min) {
								if (minChannels == null) {
									minChannels = LongSparseArray()
								}

								minChannels.put(chat.id, chat)
							}
						}
					}
				}

				if (minChannels != null) {
					for (a in updates.updates.indices) {
						val update = updates.updates[a]

						if (update is TL_updateNewChannelMessage) {
							val message = update.message
							val channelId = message.peer_id!!.channel_id

							if (minChannels.indexOfKey(channelId) >= 0) {
								if (BuildConfig.DEBUG) {
									FileLog.d("need get diff because of min channel $channelId")
								}

								needGetDiff = true

								break
							}

							/*if (message.fwd_from != null && message.fwd_from.channel_id != 0) {
								channelId = message.fwd_from.channel_id;
								if (minChannels.containsKey(channelId)) {
									if (BuildConfig.DEBUG) {
										FileLog.e("need get diff because of min forward channel " + channelId);
									}
									needGetDiff = true;
									break;
								}
							}*/
						}
					}
				}

				if (!needGetDiff) {
					messagesStorage.putUsersAndChats(updates.users, updates.chats, true, true)

					Collections.sort(updates.updates, updatesComparator)

					val a = 0

					while (a < updates.updates.size) {
						val update = updates.updates[a]

						if (getUpdateType(update) == 0) {
							val updatesNew = TL_updates()
							updatesNew.updates.add(update)
							updatesNew.pts = getUpdatePts(update)
							updatesNew.pts_count = getUpdatePtsCount(update)

							var b = a + 1

							while (b < updates.updates.size) {
								val update2 = updates.updates[b]
								val pts2 = getUpdatePts(update2)
								val count2 = getUpdatePtsCount(update2)

								if (getUpdateType(update2) == 0 && updatesNew.pts + count2 == pts2) {
									updatesNew.updates.add(update2)
									updatesNew.pts = pts2
									updatesNew.pts_count += count2

									updates.updates.removeAt(b)

									b--
								}
								else {
									break
								}

								b++
							}

							if (messagesStorage.lastPtsValue + updatesNew.pts_count == updatesNew.pts) {
								if (!processUpdateArray(updatesNew.updates, updates.users, updates.chats, false, updates.date)) {
									if (BuildConfig.DEBUG) {
										FileLog.d("need get diff inner TL_updates, pts: " + messagesStorage.lastPtsValue + " " + updates.seq)
									}

									needGetDiff = true
								}
								else {
									messagesStorage.lastPtsValue = updatesNew.pts
								}
							}
							else if (messagesStorage.lastPtsValue != updatesNew.pts) {
								if (BuildConfig.DEBUG) {
									FileLog.d(update.toString() + " need get diff, pts: " + messagesStorage.lastPtsValue + " " + updatesNew.pts + " count = " + updatesNew.pts_count)
								}

								if (gettingDifference || updatesStartWaitTimePts == 0L || abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
									if (updatesStartWaitTimePts == 0L) {
										updatesStartWaitTimePts = System.currentTimeMillis()
									}

									if (BuildConfig.DEBUG) {
										FileLog.d("add to queue")
									}

									updatesQueuePts.add(updatesNew)
								}
								else {
									needGetDiff = true
								}
							}
						}
						else if (getUpdateType(update) == 1) {
							val updatesNew = TL_updates()
							updatesNew.updates.add(update)
							updatesNew.pts = getUpdateQts(update)

							var b = a + 1

							while (b < updates.updates.size) {
								val update2 = updates.updates[b]
								val qts2 = getUpdateQts(update2)

								if (getUpdateType(update2) == 1 && updatesNew.pts + 1 == qts2) {
									updatesNew.updates.add(update2)
									updatesNew.pts = qts2

									updates.updates.removeAt(b)

									b--
								}
								else {
									break
								}

								b++
							}

							if (messagesStorage.lastQtsValue == 0 || messagesStorage.lastQtsValue + updatesNew.updates.size == updatesNew.pts) {
								processUpdateArray(updatesNew.updates, updates.users, updates.chats, false, updates.date)
								messagesStorage.lastQtsValue = updatesNew.pts
								needReceivedQueue = true
							}
							else if (messagesStorage.lastPtsValue != updatesNew.pts) {
								if (BuildConfig.DEBUG) {
									FileLog.d(update.toString() + " need get diff, qts: " + messagesStorage.lastQtsValue + " " + updatesNew.pts)
								}

								if (gettingDifference || updatesStartWaitTimeQts == 0L || abs(System.currentTimeMillis() - updatesStartWaitTimeQts) <= 1500) {
									if (updatesStartWaitTimeQts == 0L) {
										updatesStartWaitTimeQts = System.currentTimeMillis()
									}

									if (BuildConfig.DEBUG) {
										FileLog.d("add to queue")
									}

									updatesQueueQts.add(updatesNew)
								}
								else {
									needGetDiff = true
								}
							}
						}
						else if (getUpdateType(update) == 2) {
							val channelId = getUpdateChannelId(update)
							var skipUpdate = false
							var channelPts = channelsPts[channelId]

							if (channelPts == 0) {
								channelPts = messagesStorage.getChannelPtsSync(channelId)

								if (channelPts == 0) {
									for (chat in updates.chats) {
										if (chat.id == channelId) {
											loadUnknownChannel(chat, 0)
											skipUpdate = true
											break
										}
									}
								}
								else {
									channelsPts.put(channelId, channelPts)
								}
							}

							val updatesNew = TL_updates()
							updatesNew.updates.add(update)
							updatesNew.pts = getUpdatePts(update)
							updatesNew.pts_count = getUpdatePtsCount(update)

							var b = a + 1

							while (b < updates.updates.size) {
								val update2 = updates.updates[b]
								val pts2 = getUpdatePts(update2)
								val count2 = getUpdatePtsCount(update2)

								if (getUpdateType(update2) == 2 && channelId == getUpdateChannelId(update2) && updatesNew.pts + count2 == pts2) {
									updatesNew.updates.add(update2)
									updatesNew.pts = pts2
									updatesNew.pts_count += count2

									updates.updates.removeAt(b)

									b--
								}
								else {
									break
								}

								b++
							}

							if (!skipUpdate) {
								if (channelPts + updatesNew.pts_count == updatesNew.pts) {
									if (!processUpdateArray(updatesNew.updates, updates.users, updates.chats, false, updates.date)) {
										if (BuildConfig.DEBUG) {
											FileLog.d("need get channel diff inner TL_updates, channel_id = $channelId")
										}

										if (needGetChannelsDiff == null) {
											needGetChannelsDiff = ArrayList()
										}
										else if (!needGetChannelsDiff.contains(channelId)) {
											needGetChannelsDiff.add(channelId)
										}
									}
									else {
										channelsPts.put(channelId, updatesNew.pts)
										messagesStorage.saveChannelPts(channelId, updatesNew.pts)
									}
								}
								else if (channelPts != updatesNew.pts) {
									if (BuildConfig.DEBUG) {
										FileLog.d(update.toString() + " need get channel diff, pts: " + channelPts + " " + updatesNew.pts + " count = " + updatesNew.pts_count + " channelId = " + channelId)
									}

									val updatesStartWaitTime = updatesStartWaitTimeChannels[channelId]
									val gettingDifferenceChannel = gettingDifferenceChannels[channelId, false]

									if (gettingDifferenceChannel || updatesStartWaitTime == 0L || abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500) {
										if (updatesStartWaitTime == 0L) {
											updatesStartWaitTimeChannels.put(channelId, System.currentTimeMillis())
										}

										if (BuildConfig.DEBUG) {
											FileLog.d("add to queue")
										}

										var arrayList = updatesQueueChannels[channelId]

										if (arrayList == null) {
											arrayList = ArrayList()
											updatesQueueChannels.put(channelId, arrayList)
										}

										arrayList.add(updatesNew)
									}
									else {
										if (needGetChannelsDiff == null) {
											needGetChannelsDiff = ArrayList()
										}
										else if (!needGetChannelsDiff.contains(channelId)) {
											needGetChannelsDiff.add(channelId)
										}
									}
								}
							}
							else {
								if (BuildConfig.DEBUG) {
									FileLog.d("need load unknown channel = $channelId")
								}
							}
						}
						else {
							break
						}

						updates.updates.removeAt(a)
					}

					val processUpdate = if (updates is TL_updatesCombined) {
						messagesStorage.lastSeqValue + 1 == updates.seq_start || messagesStorage.lastSeqValue == updates.seq_start
					}
					else {
						messagesStorage.lastSeqValue + 1 == updates.seq || updates.seq == 0 || updates.seq == messagesStorage.lastSeqValue
					}

					if (processUpdate) {
						processUpdateArray(updates.updates, updates.users, updates.chats, false, updates.date)

						if (updates.seq != 0) {
							if (updates.date != 0) {
								messagesStorage.lastDateValue = updates.date
							}

							messagesStorage.lastSeqValue = updates.seq
						}
					}
					else {
						if (BuildConfig.DEBUG) {
							if (updates is TL_updatesCombined) {
								FileLog.d("need get diff TL_updatesCombined, seq: " + messagesStorage.lastSeqValue + " " + updates.seq_start)
							}
							else {
								FileLog.d("need get diff TL_updates, seq: " + messagesStorage.lastSeqValue + " " + updates.seq)
							}
						}

						if (gettingDifference || updatesStartWaitTimeSeq == 0L || abs(System.currentTimeMillis() - updatesStartWaitTimeSeq) <= 1500) {
							if (updatesStartWaitTimeSeq == 0L) {
								updatesStartWaitTimeSeq = System.currentTimeMillis()
							}

							if (BuildConfig.DEBUG) {
								FileLog.d("add TL_updates/Combined to queue")
							}

							updatesQueueSeq.add(updates)
						}
						else {
							needGetDiff = true
						}
					}
				}
			}

			is TL_updatesTooLong -> {
				if (BuildConfig.DEBUG) {
					FileLog.d("need get diff TL_updatesTooLong")
				}

				needGetDiff = true
			}

			is UserActionUpdatesSeq -> {
				messagesStorage.lastSeqValue = updates.seq
			}

			is UserActionUpdatesPts -> {
				if (updates.chat_id != 0L) {
					channelsPts.put(updates.chat_id, updates.pts)
					messagesStorage.saveChannelPts(updates.chat_id, updates.pts)
				}
				else {
					messagesStorage.lastPtsValue = updates.pts
				}
			}
		}

		secretChatHelper.processPendingEncMessages()

		if (!fromQueue) {
			for (a in 0 until updatesQueueChannels.size()) {
				val key = updatesQueueChannels.keyAt(a)

				if (needGetChannelsDiff != null && needGetChannelsDiff.contains(key)) {
					getChannelDifference(key)
				}
				else {
					processChannelsUpdatesQueue(key, 0)
				}
			}

			if (needGetDiff) {
				getDifference()
			}
			else {
				for (a in 0..2) {
					processUpdatesQueue(a, 0)
				}
			}
		}

		if (needReceivedQueue) {
			val req = TL_messages_receivedQueue()
			req.max_qts = messagesStorage.lastQtsValue

			connectionsManager.sendRequest(req)
		}

		if (updateStatus) {
			AndroidUtilities.runOnUIThread {
				notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS)
			}
		}

		messagesStorage.saveDiffParams(messagesStorage.lastSeqValue, messagesStorage.lastPtsValue, messagesStorage.lastDateValue, messagesStorage.lastQtsValue)
	}

	private fun applyFoldersUpdates(folderUpdates: List<TL_updateFolderPeers>?): Boolean {
		if (folderUpdates == null) {
			return false
		}

		var updated = false

		for (update in folderUpdates) {
			for (folderPeer in update.folder_peers) {
				val dialogId = DialogObject.getPeerDialogId(folderPeer.peer)
				val dialog = dialogs_dict[dialogId] ?: continue

				if (dialog.folder_id != folderPeer.folder_id) {
					dialog.pinned = false
					dialog.pinnedNum = 0
					dialog.folder_id = folderPeer.folder_id

					ensureFolderDialogExists(folderPeer.folder_id, null)
				}
			}

			updated = true

			messagesStorage.setDialogsFolderId(update.folder_peers, null, 0, 0)
		}

		return updated
	}

	fun processUpdateArray(updates: List<Update>, usersArr: List<User>?, chatsArr: List<Chat>?, fromGetDifference: Boolean, date: Int): Boolean {
		if (updates.isEmpty()) {
			if (usersArr != null || chatsArr != null) {
				AndroidUtilities.runOnUIThread {
					putUsers(usersArr, false)
					putChats(chatsArr, false)
				}
			}

			return true
		}

		val currentTime = System.currentTimeMillis()
		var printChanged = false

		val messages = LongSparseArray<ArrayList<MessageObject>>()
		val scheduledMessages = LongSparseArray<ArrayList<MessageObject>>()
		val webPages = LongSparseArray<WebPage>()
		var pushMessages: ArrayList<MessageObject>? = null
		var messagesArr: ArrayList<Message>? = null
		var scheduledMessagesArr: ArrayList<Message>? = null
		val editingMessages = LongSparseArray<ArrayList<MessageObject>>()
		val channelViews = LongSparseArray<SparseIntArray>()
		val channelForwards = LongSparseArray<SparseIntArray>()
		val channelReplies = LongSparseArray<SparseArray<MessageReplies>>()
		val markAsReadMessagesInbox = LongSparseIntArray()
		val stillUnreadMessagesCount = LongSparseIntArray()
		val markAsReadMessagesOutbox = LongSparseIntArray()
		val markContentAsReadMessages = LongSparseArray<ArrayList<Int>>()
		val markAsReadEncrypted = SparseIntArray()
		val deletedMessages = LongSparseArray<ArrayList<Int>>()
		val scheduledDeletedMessages = LongSparseArray<ArrayList<Int>>()
		val groupSpeakingActions = LongSparseArray<ArrayList<Long>>()
		val importingActions = LongSparseIntArray()
		val clearHistoryMessages = LongSparseIntArray()
		val chatInfoToUpdate = mutableListOf<ChatParticipants>()
		val updatesOnMainThread = mutableListOf<Update>()
		val folderUpdates = mutableListOf<TL_updateFolderPeers>()
		val tasks = mutableListOf<TL_updateEncryptedMessagesRead>()
		val contactsIds = mutableListOf<Long>()
		val messageThumbs = mutableListOf<MessageThumb>()

		val usersDict: ConcurrentHashMap<Long, User>
		val chatsDict: ConcurrentHashMap<Long, Chat>

		if (usersArr != null) {
			usersDict = ConcurrentHashMap()

			for (user in usersArr) {
				usersDict[user.id] = user
			}
		}
		else {
			usersDict = users
		}

		if (chatsArr != null) {
			chatsDict = ConcurrentHashMap()

			for (chat in chatsArr) {
				chatsDict[chat.id] = chat
			}
		}
		else {
			chatsDict = chats
		}

		if (usersArr != null || chatsArr != null) {
			AndroidUtilities.runOnUIThread {
				putUsers(usersArr, false)
				putChats(chatsArr, false)
			}
		}

		var interfaceUpdateMask = 0
		val clientUserId = userConfig.getClientUserId()

		for (baseUpdate in updates) {
			// val baseUpdate = updates[c]

			if (BuildConfig.DEBUG) {
				FileLog.d("process update $baseUpdate")
			}

			when (baseUpdate) {
				is TL_updateNewMessage, is TL_updateNewChannelMessage, is TL_updateNewScheduledMessage -> {
					var message: Message

					if (baseUpdate is TL_updateNewMessage) {
						message = baseUpdate.message
					}
					else if (baseUpdate is TL_updateNewScheduledMessage) {
						message = baseUpdate.message
					}
					else {
						message = (baseUpdate as TL_updateNewChannelMessage).message

						if (BuildConfig.DEBUG) {
							FileLog.d(baseUpdate.toString() + " channelId = " + message.peer_id?.channel_id)
						}

						if (!message.out && message.from_id is TL_peerUser && message.from_id?.user_id == userConfig.getClientUserId()) {
							message.out = true
						}
					}

					if (message is TL_messageEmpty) {
						continue
					}

					if (newMessageCallback != null && newMessageCallback!!.onMessageReceived(message)) {
						newMessageCallback = null
					}

					var chat: Chat? = null
					var chatId: Long = 0
					var userId: Long = 0

					if (message.peer_id!!.channel_id != 0L) {
						chatId = message.peer_id!!.channel_id
					}
					else if (message.peer_id!!.chat_id != 0L) {
						chatId = message.peer_id!!.chat_id
					}
					else if (message.peer_id!!.user_id != 0L) {
						userId = message.peer_id!!.user_id
					}

					if (chatId != 0L) {
						chat = chatsDict[chatId]

						if (chat == null || chat.min) {
							chat = getChat(chatId)
						}

						if (chat == null || chat.min) {
							chat = messagesStorage.getChatSync(chatId)
							putChat(chat, true)
						}
					}

					if (!fromGetDifference) {
						if (chatId != 0L) {
							if (chat == null) {
								if (BuildConfig.DEBUG) {
									FileLog.d("not found chat $chatId")
								}

								return false
							}
						}

						val count = 3 + message.entities.size

						for (a in 0 until count) {
							var allowMin = false

							if (a != 0) {
								if (a == 1) {
									userId = if (message.from_id is TL_peerUser) message.from_id!!.user_id else 0

									if (message.post) {
										allowMin = true
									}
								}
								else if (a == 2) {
									userId = if (message.fwd_from != null && message.fwd_from!!.from_id is TL_peerUser) message.fwd_from!!.from_id.user_id else 0
								}
								else {
									val entity = message.entities[a - 3]
									userId = if (entity is TL_messageEntityMentionName) entity.userId else 0
								}
							}

							if (userId > 0) {
								var user = usersDict[userId]

								if (user == null || !allowMin && user.min) {
									user = getUser(userId)
								}

								if (user == null || !allowMin && user.min) {
									user = messagesStorage.getUserSync(userId)

									if (user != null && !allowMin && user.min) {
										user = null
									}

									putUser(user, true)
								}

								if (user == null) {
									if (BuildConfig.DEBUG) {
										FileLog.d("not found user $userId")
									}

									return false
								}

								if (!message.out && a == 1 && user.status != null && user.status!!.expires <= 0 && abs(connectionsManager.currentTime - message.date) < 30) {
									onlinePrivacy[userId] = message.date
									interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_STATUS
								}
							}
						}
					}

					val messageAction = message.action

					if (messageAction is TL_messageActionChatDeleteUser) {
						val user = usersDict[messageAction.user_id]

						if (user != null && user.bot) {
							message.reply_markup = TL_replyKeyboardHide()
							message.flags = message.flags or 64
						}
						else if (message.from_id is TL_peerUser && message.from_id?.user_id == clientUserId && messageAction.user_id == clientUserId) {
							continue
						}
					}

					ImageLoader.saveMessageThumbs(message)

					MessageObject.getDialogId(message)

					if (baseUpdate is TL_updateNewChannelMessage && message.reply_to != null && message.action !is TL_messageActionPinMessage) {
						var replies = channelReplies[message.dialog_id]

						if (replies == null) {
							replies = SparseArray()
							channelReplies.put(message.dialog_id, replies)
						}

						val id = if (message.reply_to!!.reply_to_top_id != 0) message.reply_to!!.reply_to_top_id else message.reply_to!!.reply_to_msg_id
						var messageReplies = replies[id]

						if (messageReplies == null) {
							messageReplies = TL_messageReplies()
							replies.put(id, messageReplies)
						}

						if (message.from_id != null) {
							messageReplies.recent_repliers.add(0, message.from_id)
						}

						messageReplies.replies++
					}

					if (createdDialogIds.contains(message.dialog_id) && message.realGroupId == 0L) {
						val messageThumb = ImageLoader.generateMessageThumb(message)

						if (messageThumb != null) {
							messageThumbs.add(messageThumb)
						}
					}

					if (baseUpdate is TL_updateNewScheduledMessage) {
						if (scheduledMessagesArr == null) {
							scheduledMessagesArr = ArrayList()
						}

						scheduledMessagesArr.add(message)

						val isDialogCreated = createdScheduledDialogIds.contains(message.dialog_id)

						val obj = MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated)
						obj.scheduled = true

						var arr = scheduledMessages[message.dialog_id]

						if (arr == null) {
							arr = ArrayList()
							scheduledMessages.put(message.dialog_id, arr)
						}

						arr.add(obj)
					}
					else {
						if (messagesArr == null) {
							messagesArr = ArrayList()
						}

						messagesArr.add(message)

						val readMax = if (message.out) dialogs_read_outbox_max else dialogs_read_inbox_max
						var value = readMax[message.dialog_id]

						if (value == null) {
							value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
							readMax[message.dialog_id] = value
						}

						message.unread = !(value >= message.id || (chat != null && ChatObject.isNotInChat(chat)) || message.action is TL_messageActionChatMigrateTo || message.action is TL_messageActionChannelCreate)

						if (message.dialog_id == clientUserId) {
							if (!message.from_scheduled) {
								message.unread = false
							}

							message.media_unread = false
							message.out = true
						}

						val isDialogCreated = createdDialogIds.contains(message.dialog_id)
						val obj = MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated)

						if (obj.type == 11) {
							interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_CHAT_AVATAR
						}
						else if (obj.type == 10) {
							interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_CHAT_NAME
						}

						var arr = messages[message.dialog_id]

						if (arr == null) {
							arr = ArrayList()
							messages.put(message.dialog_id, arr)
						}

						arr.add(obj)

						if ((!obj.isOut || obj.messageOwner?.from_scheduled == true) && obj.isUnread && (chat == null || !ChatObject.isNotInChat(chat) && !chat.min)) {
							if (pushMessages == null) {
								pushMessages = ArrayList()
							}

							pushMessages.add(obj)
						}
					}
				}

				is TL_updateReadMessagesContents -> {
					var ids = markContentAsReadMessages[0]

					if (ids == null) {
						ids = ArrayList()
						markContentAsReadMessages.put(0, ids)
					}

					ids.addAll(baseUpdate.messages)
				}

				is TL_updateChannelReadMessagesContents -> {
					val dialogId = -baseUpdate.channel_id
					var ids = markContentAsReadMessages[dialogId]

					if (ids == null) {
						ids = ArrayList()
						markContentAsReadMessages.put(dialogId, ids)
					}

					ids.addAll(baseUpdate.messages)
				}

				is TL_updateReadHistoryInbox -> {
					val dialogId = if (baseUpdate.peer.chat_id != 0L) {
						markAsReadMessagesInbox.put(-baseUpdate.peer.chat_id, baseUpdate.max_id)
						-baseUpdate.peer.chat_id
					}
					else {
						markAsReadMessagesInbox.put(baseUpdate.peer.user_id, baseUpdate.max_id)
						baseUpdate.peer.user_id
					}

					var value = dialogs_read_inbox_max[dialogId]

					if (value == null) {
						value = messagesStorage.getDialogReadMax(false, dialogId)
					}

					dialogs_read_inbox_max[dialogId] = max(value, baseUpdate.max_id)
				}

				is TL_updateReadHistoryOutbox -> {
					val dialogId: Long

					if (baseUpdate.peer.chat_id != 0L) {
						markAsReadMessagesOutbox.put(-baseUpdate.peer.chat_id, baseUpdate.max_id)
						dialogId = -baseUpdate.peer.chat_id
					}
					else {
						markAsReadMessagesOutbox.put(baseUpdate.peer.user_id, baseUpdate.max_id)
						dialogId = baseUpdate.peer.user_id

						val user = getUser(baseUpdate.peer.user_id)

						if (user?.status != null && user.status!!.expires <= 0 && abs(connectionsManager.currentTime - date) < 30) {
							onlinePrivacy[baseUpdate.peer.user_id] = date
							interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_STATUS
						}
					}

					var value = dialogs_read_outbox_max[dialogId]

					if (value == null) {
						value = messagesStorage.getDialogReadMax(true, dialogId)
					}

					dialogs_read_outbox_max[dialogId] = max(value, baseUpdate.max_id)
				}

				is TL_updateDeleteMessages -> {
					var arrayList = deletedMessages[0]

					if (arrayList == null) {
						arrayList = ArrayList()
						deletedMessages.put(0, arrayList)
					}

					arrayList.addAll(baseUpdate.messages)
				}

				is TL_updateDeleteScheduledMessages -> {
					val id = MessageObject.getPeerId(baseUpdate.peer)
					var arrayList = scheduledDeletedMessages[MessageObject.getPeerId(baseUpdate.peer)]

					if (arrayList == null) {
						arrayList = ArrayList()
						scheduledDeletedMessages.put(id, arrayList)
					}

					arrayList.addAll(baseUpdate.messages)
				}

				is TL_updateUserTyping, is TL_updateChatUserTyping, is TL_updateChannelUserTyping -> {
					val userId: Long
					val chatId: Long
					val threadId: Int
					val action: SendMessageAction

					if (baseUpdate is TL_updateChannelUserTyping) {
						userId = if (baseUpdate.from_id.user_id != 0L) {
							baseUpdate.from_id.user_id
						}
						else if (baseUpdate.from_id.channel_id != 0L) {
							-baseUpdate.from_id.channel_id
						}
						else {
							-baseUpdate.from_id.chat_id
						}

						chatId = baseUpdate.channel_id
						action = baseUpdate.action
						threadId = baseUpdate.top_msg_id
					}
					else if (baseUpdate is TL_updateUserTyping) {
						userId = baseUpdate.user_id
						action = baseUpdate.action
						chatId = 0
						threadId = 0

						if (baseUpdate.action is TL_sendMessageEmojiInteraction) {
							AndroidUtilities.runOnUIThread {
								notificationCenter.postNotificationName(NotificationCenter.onEmojiInteractionsReceived, baseUpdate.user_id, baseUpdate.action)
							}

							continue
						}
					}
					else if (baseUpdate is TL_updateChatUserTyping) {
						chatId = baseUpdate.chat_id

						userId = if (baseUpdate.from_id.user_id != 0L) {
							baseUpdate.from_id.user_id
						}
						else if (baseUpdate.from_id.channel_id != 0L) {
							-baseUpdate.from_id.channel_id
						}
						else {
							-baseUpdate.from_id.chat_id
						}

						action = baseUpdate.action
						threadId = 0

						if (baseUpdate.action is TL_sendMessageEmojiInteraction) {
							AndroidUtilities.runOnUIThread {
								notificationCenter.postNotificationName(NotificationCenter.onEmojiInteractionsReceived, -baseUpdate.chat_id, baseUpdate.action)
							}

							continue
						}
					}
					else {
						continue
					}

					var uid = -chatId

					if (uid == 0L) {
						uid = userId
					}

					if (action is TL_sendMessageHistoryImportAction) {
						importingActions.put(uid, action.progress)
					}
					else if (userId != userConfig.getClientUserId()) {
						if (action is TL_speakingInGroupCallAction) {
							if (chatId != 0L) {
								var uids = groupSpeakingActions[chatId]

								if (uids == null) {
									uids = ArrayList()
									groupSpeakingActions.put(chatId, uids)
								}

								uids.add(userId)
							}
						}
						else {
							var threads = printingUsers[uid]
							var arr = threads?.get(threadId)

							if (action is TL_sendMessageCancelAction) {
								if (arr != null) {
									var a = 0
									val size = arr.size

									while (a < size) {
										val pu = arr[a]

										if (pu.userId == userId) {
											arr.removeAt(a)
											printChanged = true
											break
										}

										a++
									}

									if (arr.isEmpty()) {
										threads?.remove(threadId)

										if (threads.isNullOrEmpty()) {
											printingUsers.remove(uid)
										}
									}
								}
							}
							else {
								if (threads == null) {
									threads = ConcurrentHashMap()
									printingUsers[uid] = threads
								}

								if (arr == null) {
									arr = ArrayList()
									threads[threadId] = arr
								}

								var exist = false

								for (u in arr) {
									if (u.userId == userId) {
										exist = true

										u.lastTime = currentTime

										if (u.action?.javaClass != action.javaClass) {
											printChanged = true
										}

										u.action = action

										break
									}
								}

								if (!exist) {
									val newUser = PrintingUser()
									newUser.userId = userId
									newUser.lastTime = currentTime
									newUser.action = action

									arr.add(newUser)

									printChanged = true
								}
							}
						}

						if (abs(connectionsManager.currentTime - date) < 30) {
							onlinePrivacy[userId] = date
						}
					}
				}

				is TL_updateChatParticipants -> {
					interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_CHAT_MEMBERS
					chatInfoToUpdate.add(baseUpdate.participants)
				}

				is TL_updateUserStatus -> {
					interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_STATUS
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateUserEmojiStatus -> {
					interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_EMOJI_STATUS
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateUserName -> {
					interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_NAME
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateUserPhoto -> {
					interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_AVATAR
					messagesStorage.clearUserPhotos(baseUpdate.user_id)
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateUserPhone -> {
					interfaceUpdateMask = interfaceUpdateMask or UPDATE_MASK_PHONE
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updatePeerSettings -> {
					if (baseUpdate.peer is TL_peerUser) {
						val user = usersDict[baseUpdate.peer.user_id]

						if (user != null) {
							if (user.contact) {
								val idx = contactsIds.indexOf(-baseUpdate.peer.user_id)

								if (idx != -1) {
									contactsIds.removeAt(idx)
								}

								if (!contactsIds.contains(baseUpdate.peer.user_id)) {
									contactsIds.add(baseUpdate.peer.user_id)
								}
							}
							else {
								val idx = contactsIds.indexOf(baseUpdate.peer.user_id)

								if (idx != -1) {
									contactsIds.removeAt(idx)
								}

								if (!contactsIds.contains(baseUpdate.peer.user_id)) {
									contactsIds.add(-baseUpdate.peer.user_id)
								}
							}
						}
					}

					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateNewEncryptedMessage -> {
					val decryptedMessages = secretChatHelper.decryptMessage(baseUpdate.message)

					if (decryptedMessages != null && decryptedMessages.isNotEmpty()) {
						val cid = baseUpdate.message.chat_id
						val uid = DialogObject.makeEncryptedDialogId(cid.toLong())
						var arr = messages[uid]

						if (arr == null) {
							arr = ArrayList()
							messages.put(uid, arr)
						}

						for (message in decryptedMessages) {
							ImageLoader.saveMessageThumbs(message)

							if (messagesArr == null) {
								messagesArr = ArrayList()
							}

							messagesArr.add(message)

							val isDialogCreated = createdDialogIds.contains(uid)
							val obj = MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated)

							arr.add(obj)

							if (pushMessages == null) {
								pushMessages = ArrayList()
							}

							pushMessages.add(obj)
						}
					}
				}

				is TL_updateEncryptedChatTyping -> {
					val encryptedChat = getEncryptedChatDB(baseUpdate.chat_id, true)

					if (encryptedChat != null) {
						val uid = DialogObject.makeEncryptedDialogId(baseUpdate.chat_id.toLong())
						var threads = printingUsers[uid]

						if (threads == null) {
							threads = ConcurrentHashMap()
							printingUsers[uid] = threads
						}

						var arr = threads[0]

						if (arr == null) {
							arr = ArrayList()
							threads[0] = arr
						}

						var exist = false

						for (u in arr) {
							if (u.userId == encryptedChat.user_id) {
								exist = true
								u.lastTime = currentTime
								u.action = TL_sendMessageTypingAction()
								break
							}
						}

						if (!exist) {
							val newUser = PrintingUser()
							newUser.userId = encryptedChat.user_id
							newUser.lastTime = currentTime
							newUser.action = TL_sendMessageTypingAction()

							arr.add(newUser)

							printChanged = true
						}

						if (abs(connectionsManager.currentTime - date) < 30) {
							onlinePrivacy[encryptedChat.user_id] = date
						}
					}
				}

				is TL_updateEncryptedMessagesRead -> {
					markAsReadEncrypted.put(baseUpdate.chat_id, baseUpdate.max_date)
					tasks.add(baseUpdate)
				}

				is TL_updateChatParticipantAdd -> {
					messagesStorage.updateChatInfo(baseUpdate.chat_id, baseUpdate.user_id, 0, baseUpdate.inviter_id, baseUpdate.version)
				}

				is TL_updateChatParticipantDelete -> {
					messagesStorage.updateChatInfo(baseUpdate.chat_id, baseUpdate.user_id, 1, 0, baseUpdate.version)
				}

				is TL_updateDcOptions, is TL_updateConfig -> {
					connectionsManager.updateDcSettings()
				}

				is TL_updateEncryption -> {
					secretChatHelper.processUpdateEncryption(baseUpdate, usersDict)
				}

				is TL_updatePeerBlocked -> {
					messagesStorage.storageQueue.postRunnable {
						AndroidUtilities.runOnUIThread {
							val id = MessageObject.getPeerId(baseUpdate.peer_id)

							if (baseUpdate.blocked) {
								if (blockedPeers.indexOfKey(id) < 0) {
									blockedPeers.put(id, 1)
								}
							}
							else {
								blockedPeers.delete(id)
							}

							notificationCenter.postNotificationName(NotificationCenter.blockedUsersDidLoad)
						}
					}
				}

				is TL_updateNotifySettings -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateServiceNotification -> {
					if (baseUpdate.popup && !baseUpdate.message.isNullOrEmpty()) {
						AndroidUtilities.runOnUIThread {
							notificationCenter.postNotificationName(NotificationCenter.needShowAlert, AlertDialog.AlertReason.SERVICE_NOTIFICATION, baseUpdate.message, baseUpdate.type)
						}
					}

					if (baseUpdate.flags and 2 != 0) {
						val newMessage = TL_message()
						newMessage.id = userConfig.newMessageId
						newMessage.local_id = newMessage.id

						userConfig.saveConfig(false)

						newMessage.unread = true
						newMessage.flags = MESSAGE_FLAG_HAS_FROM_ID

						if (baseUpdate.inbox_date != 0) {
							newMessage.date = baseUpdate.inbox_date
						}
						else {
							newMessage.date = (System.currentTimeMillis() / 1000).toInt()
						}

						newMessage.from_id = TL_peerUser()
						newMessage.from_id?.user_id = 777000
						newMessage.peer_id = TL_peerUser()
						newMessage.peer_id?.user_id = userConfig.getClientUserId()
						newMessage.dialog_id = 777000

						if (baseUpdate.media != null) {
							newMessage.media = baseUpdate.media
							newMessage.flags = newMessage.flags or MESSAGE_FLAG_HAS_MEDIA
						}

						newMessage.message = baseUpdate.message

						if (baseUpdate.entities != null) {
							newMessage.entities = baseUpdate.entities
							newMessage.flags = newMessage.flags or 128
						}

						if (messagesArr == null) {
							messagesArr = ArrayList()
						}

						messagesArr.add(newMessage)

						val isDialogCreated = createdDialogIds.contains(newMessage.dialog_id)
						val obj = MessageObject(currentAccount, newMessage, usersDict, chatsDict, isDialogCreated, isDialogCreated)
						var arr = messages[newMessage.dialog_id]

						if (arr == null) {
							arr = ArrayList()
							messages.put(newMessage.dialog_id, arr)
						}

						arr.add(obj)

						if (pushMessages == null) {
							pushMessages = ArrayList()
						}

						pushMessages.add(obj)
					}
				}

				is TL_updateDialogPinned -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updatePinnedDialogs -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateFolderPeers -> {
					folderUpdates.add(baseUpdate)
				}

				is TL_updatePrivacy -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateWebPage -> {
					webPages.put(baseUpdate.webpage.id, baseUpdate.webpage)
				}

				is TL_updateChannelWebPage -> {
					webPages.put(baseUpdate.webpage.id, baseUpdate.webpage)
				}

				is TL_updateChannelTooLong -> {
					if (BuildConfig.DEBUG) {
						FileLog.d(baseUpdate.toString() + " channelId = " + baseUpdate.channel_id)
					}

					var channelPts = channelsPts[baseUpdate.channel_id, 0]

					if (channelPts == 0) {
						channelPts = messagesStorage.getChannelPtsSync(baseUpdate.channel_id)

						if (channelPts == 0) {
							var chat = chatsDict[baseUpdate.channel_id]

							if (chat == null || chat.min) {
								chat = getChat(baseUpdate.channel_id)
							}

							if (chat == null || chat.min) {
								chat = messagesStorage.getChatSync(baseUpdate.channel_id)
								putChat(chat, true)
							}

							if (chat != null && !chat.min) {
								loadUnknownChannel(chat, 0)
							}
						}
						else {
							channelsPts.put(baseUpdate.channel_id, channelPts)
						}
					}

					if (channelPts != 0) {
						if (baseUpdate.flags and 1 != 0) {
							if (baseUpdate.pts > channelPts) {
								getChannelDifference(baseUpdate.channel_id)
							}
						}
						else {
							getChannelDifference(baseUpdate.channel_id)
						}
					}
				}

				is TL_updateReadChannelInbox -> {
					val dialogId = -baseUpdate.channel_id
					var value = dialogs_read_inbox_max[dialogId]

					if (value == null) {
						value = messagesStorage.getDialogReadMax(false, dialogId)
					}

					markAsReadMessagesInbox.put(dialogId, baseUpdate.max_id)
					stillUnreadMessagesCount.put(dialogId, baseUpdate.still_unread_count)

					dialogs_read_inbox_max[dialogId] = max(value, baseUpdate.max_id)

					FileLog.d("TL_updateReadChannelInbox " + dialogId + "  new unread " + baseUpdate.still_unread_count + " from get diff" + fromGetDifference)
				}

				is TL_updateReadChannelOutbox -> {
					if (BuildConfig.DEBUG) {
						FileLog.d(baseUpdate.toString() + " channelId = " + baseUpdate.channel_id)
					}

					val dialogId = -baseUpdate.channel_id

					markAsReadMessagesOutbox.put(dialogId, baseUpdate.max_id)

					var value = dialogs_read_outbox_max[dialogId]

					if (value == null) {
						value = messagesStorage.getDialogReadMax(true, dialogId)
					}

					dialogs_read_outbox_max[dialogId] = max(value, baseUpdate.max_id)
				}

				is TL_updateDeleteChannelMessages -> {
					if (BuildConfig.DEBUG) {
						FileLog.d(baseUpdate.toString() + " channelId = " + baseUpdate.channel_id)
					}

					val dialogId = -baseUpdate.channel_id
					var arrayList = deletedMessages[dialogId]

					if (arrayList == null) {
						arrayList = ArrayList()
						deletedMessages.put(dialogId, arrayList)
					}

					arrayList.addAll(baseUpdate.messages)
				}

				is TL_updateChannel -> {
					if (BuildConfig.DEBUG) {
						FileLog.d(baseUpdate.toString() + " channelId = " + baseUpdate.channel_id)
					}

					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateChat -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateChannelMessageViews -> {
					if (BuildConfig.DEBUG) {
						FileLog.d(baseUpdate.toString() + " channelId = " + baseUpdate.channel_id)
					}

					val dialogId = -baseUpdate.channel_id
					var array = channelViews[dialogId]

					if (array == null) {
						array = SparseIntArray()
						channelViews.put(dialogId, array)
					}

					array.put(baseUpdate.id, baseUpdate.views)
				}

				is TL_updateChannelMessageForwards -> {
					if (BuildConfig.DEBUG) {
						FileLog.d(baseUpdate.toString() + " channelId = " + baseUpdate.channel_id)
					}

					val dialogId = -baseUpdate.channel_id
					var array = channelForwards[dialogId]

					if (array == null) {
						array = SparseIntArray()
						channelForwards.put(dialogId, array)
					}

					array.put(baseUpdate.id, baseUpdate.forwards)
				}

				is TL_updateChatParticipantAdmin -> {
					messagesStorage.updateChatInfo(baseUpdate.chat_id, baseUpdate.user_id, 2, (if (baseUpdate.is_admin) 1 else 0).toLong(), baseUpdate.version)
				}

				is TL_updateChatDefaultBannedRights -> {
					val chatId = if (baseUpdate.peer.channel_id != 0L) {
						baseUpdate.peer.channel_id
					}
					else {
						baseUpdate.peer.chat_id
					}

					messagesStorage.updateChatDefaultBannedRights(chatId, baseUpdate.default_banned_rights, baseUpdate.version)

					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateStickerSets -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateStickerSetsOrder -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateNewStickerSet -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateDraftMessage -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateMoveStickerSetToTop -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateSavedGifs -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateEditChannelMessage, is TL_updateEditMessage -> {
					val message: Message

					if (baseUpdate is TL_updateEditChannelMessage) {
						message = baseUpdate.message

						var chat = chatsDict[message.peer_id!!.channel_id]

						if (chat == null) {
							chat = getChat(message.peer_id!!.channel_id)
						}

						if (chat == null) {
							chat = messagesStorage.getChatSync(message.peer_id!!.channel_id)
							putChat(chat, true)
						}
					}
					else {
						message = (baseUpdate as TL_updateEditMessage).message

						if (message.dialog_id == clientUserId) {
							message.unread = false
							message.media_unread = false
							message.out = true
						}
					}

					if (!message.out && message.from_id is TL_peerUser && message.from_id?.user_id == clientUserId) {
						message.out = true
					}

					if (!fromGetDifference) {
						for (entity in message.entities) {
							if (entity is TL_messageEntityMentionName) {
								val userId = entity.userId
								var user = usersDict[userId]

								if (user == null || user.min) {
									user = getUser(userId)
								}

								if (user == null || user.min) {
									user = messagesStorage.getUserSync(userId)

									if (user != null && user.min) {
										user = null
									}

									putUser(user, true)
								}

								if (user == null) {
									return false
								}
							}
						}
					}

					MessageObject.getDialogId(message)

					val readMax = if (message.out) dialogs_read_outbox_max else dialogs_read_inbox_max
					var value = readMax[message.dialog_id]

					if (value == null) {
						value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
						readMax[message.dialog_id] = value
					}

					message.unread = value < message.id

					if (message.dialog_id == clientUserId) {
						message.out = true
						message.unread = false
						message.media_unread = false
					}

					if (message.out && message.message == null) {
						message.message = ""
						message.attachPath = ""
					}

					ImageLoader.saveMessageThumbs(message)

					val isDialogCreated = createdDialogIds.contains(message.dialog_id)
					val obj = MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated)
					var arr = editingMessages[message.dialog_id]

					if (arr == null) {
						arr = ArrayList()
						editingMessages.put(message.dialog_id, arr)
					}

					arr.add(obj)
				}

				is TL_updatePinnedChannelMessages -> {
					if (BuildConfig.DEBUG) {
						FileLog.d(baseUpdate.toString() + " channelId = " + baseUpdate.channel_id)
					}

					messagesStorage.updatePinnedMessages(-baseUpdate.channel_id, baseUpdate.messages, baseUpdate.pinned, -1, 0, false, null)
				}

				is TL_updatePinnedMessages -> {
					messagesStorage.updatePinnedMessages(MessageObject.getPeerId(baseUpdate.peer), baseUpdate.messages, baseUpdate.pinned, -1, 0, false, null)
				}

				is TL_updateReadFeaturedStickers -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateReadFeaturedEmojiStickers -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updatePhoneCall -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateGroupCallParticipants -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateGroupCall -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateGroupCallConnection -> {
					// unused
				}

				is TL_updateBotCommands -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updatePhoneCallSignalingData -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateLangPack -> {
					AndroidUtilities.runOnUIThread {
						LocaleController.getInstance().saveRemoteLocaleStringsForCurrentLocale(baseUpdate.difference, currentAccount)
					}
				}

				is TL_updateLangPackTooLong -> {
					LocaleController.getInstance().reloadCurrentRemoteLocale(currentAccount, baseUpdate.lang_code, false)
				}

				is TL_updateRecentReactions -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateFavedStickers -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateContactsReset -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateChannelAvailableMessages -> {
					val dialogId = -baseUpdate.channel_id
					val currentValue = clearHistoryMessages[dialogId, 0]

					if (currentValue == 0 || currentValue < baseUpdate.available_min_id) {
						clearHistoryMessages.put(dialogId, baseUpdate.available_min_id)
					}
				}

				is TL_updateDialogUnreadMark -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateMessagePoll -> {
					val time = sendMessagesHelper.getVoteSendTime(baseUpdate.poll_id)

					if (abs(SystemClock.elapsedRealtime() - time) < 600) {
						continue
					}

					messagesStorage.updateMessagePollResults(baseUpdate.poll_id, baseUpdate.poll, baseUpdate.results)

					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateMessageReactions -> {
					val dialogId = MessageObject.getPeerId(baseUpdate.peer)

					messagesStorage.updateMessageReactions(dialogId, baseUpdate.msgId, baseUpdate.reactions)

					if (baseUpdate.updateUnreadState) {
						val sparseBooleanArray = SparseBooleanArray()
						sparseBooleanArray.put(baseUpdate.msgId, MessageObject.hasUnreadReactions(baseUpdate.reactions))
						checkUnreadReactions(dialogId, sparseBooleanArray)
					}

					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateMessageExtendedMedia -> {
					if (baseUpdate.extended_media is TL_messageExtendedMedia) {
						val msg = messagesStorage.getMessage(DialogObject.getPeerDialogId(baseUpdate.peer), baseUpdate.msg_id.toLong())

						if (msg != null) {
							msg.media?.extended_media = baseUpdate.extended_media

							if (messagesArr == null) {
								messagesArr = ArrayList()
							}

							messagesArr.add(msg)
						}

						updatesOnMainThread.add(baseUpdate)
					}
				}

				is TL_updatePeerLocated -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateTheme -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateGeoLiveViewed -> {
					locationController.setNewLocationEndWatchTime()
				}

				is TL_updateDialogFilter -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateDialogFilterOrder -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateDialogFilters -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateRecentEmojiStatuses -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateWebViewResultSent -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateAttachMenuBots -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateBotMenuButton -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateReadChannelDiscussionInbox -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateReadChannelDiscussionOutbox -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updatePeerHistoryTTL -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updatePendingJoinRequests -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateSavedRingtones -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateTranscribeAudio -> {
					updatesOnMainThread.add(baseUpdate)
				}

				is TL_updateTranscribedAudio -> {
					updatesOnMainThread.add(baseUpdate)
				}
			}
		}

		if (!messages.isEmpty) {
			var a = 0
			val size = messages.size()

			while (a < size) {
				val key = messages.keyAt(a)
				val value = messages.valueAt(a)

				if (updatePrintingUsersWithNewMessages(key, value)) {
					printChanged = true
				}

				a++
			}
		}

		if (printChanged) {
			updatePrintingStrings()
		}

		val interfaceUpdateMaskFinal = interfaceUpdateMask
		val printChangedArg = printChanged

		if (contactsIds.isNotEmpty()) {
			contactsController.processContactsUpdates(contactsIds, usersDict)
		}

		if (pushMessages != null) {
			messagesStorage.storageQueue.postRunnable {
				AndroidUtilities.runOnUIThread {
					notificationsController.processNewMessages(pushMessages, isLast = true, isFcm = false, countDownLatch = null)
				}
			}
		}

		if (scheduledMessagesArr != null) {
			messagesStorage.putMessages(scheduledMessagesArr, true, true, false, downloadController.autodownloadMask, true)
		}

		if (messagesArr != null) {
			statsController.incrementReceivedItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_MESSAGES, messagesArr.size)
			messagesStorage.putMessages(messagesArr, true, true, false, downloadController.autodownloadMask, false)
		}

		if (!editingMessages.isEmpty) {
			var b = 0
			val size = editingMessages.size()

			while (b < size) {
				val messagesRes = TL_messages_messages()
				val messageObjects = editingMessages.valueAt(b)
				var a = 0
				val size2 = messageObjects.size

				while (a < size2) {
					messagesRes.messages.add(messageObjects[a].messageOwner!!)
					a++
				}

				messagesStorage.putMessages(messagesRes, editingMessages.keyAt(b), -2, 0, false, false)

				b++
			}

			messagesStorage.storageQueue.postRunnable {
				AndroidUtilities.runOnUIThread {
					notificationsController.processEditedMessages(editingMessages)
				}
			}
		}

		if (!channelViews.isEmpty || !channelForwards.isEmpty || !channelReplies.isEmpty) {
			messagesStorage.putChannelViews(channelViews, channelForwards, channelReplies, true)
		}

		if (folderUpdates.isNotEmpty()) {
			for (fu in folderUpdates) {
				messagesStorage.setDialogsFolderId(fu.folder_peers, null, 0, 0)
			}
		}

		AndroidUtilities.runOnUIThread {
			var updateMask = interfaceUpdateMaskFinal
			var forceDialogsUpdate = false
			var updateDialogFiltersFlags = 0

			if (updatesOnMainThread.isNotEmpty()) {
				val dbUsers = mutableListOf<User>()
				val dbUsersStatus = mutableListOf<User>()
				var editor: SharedPreferences.Editor? = null

				for (baseUpdate in updatesOnMainThread) {
					when (baseUpdate) {
						is TL_updatePrivacy -> {
							when (baseUpdate.key) {
								is TL_privacyKeyStatusTimestamp -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_LAST_SEEN)
								}

								is TL_privacyKeyChatInvite -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_INVITE)
								}

								is TL_privacyKeyPhoneCall -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_CALLS)
								}

								is TL_privacyKeyPhoneP2P -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_P2P)
								}

								is TL_privacyKeyProfilePhoto -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_PHOTO)
								}

								is TL_privacyKeyForwards -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_FORWARDS)
								}

								is TL_privacyKeyPhoneNumber -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_PHONE)
								}

								is TL_privacyKeyAddedByPhone -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE)
								}

								is TL_privacyKeyVoiceMessages -> {
									contactsController.setPrivacyRules(baseUpdate.rules, ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES)
								}
							}
						}

						is TL_updateUserStatus -> {
							val currentUser = getUser(baseUpdate.user_id)

							when (baseUpdate.status) {
								is TL_userStatusRecently -> baseUpdate.status.expires = -100
								is TL_userStatusLastWeek -> baseUpdate.status.expires = -101
								is TL_userStatusLastMonth -> baseUpdate.status.expires = -102
							}

							if (currentUser != null) {
								currentUser.id = baseUpdate.user_id
								currentUser.status = baseUpdate.status
							}

							val toDbUser: User = TL_user()
							toDbUser.id = baseUpdate.user_id
							toDbUser.status = baseUpdate.status

							dbUsersStatus.add(toDbUser)

							if (baseUpdate.user_id == userConfig.getClientUserId()) {
								notificationsController.setLastOnlineFromOtherDevice(baseUpdate.status.expires)
							}
						}

						is TL_updateUserEmojiStatus -> {
							val currentUser = getUser(baseUpdate.user_id)

							if (currentUser != null) {
								currentUser.id = baseUpdate.user_id
								currentUser.emoji_status = baseUpdate.emoji_status

								if (UserObject.isUserSelf(currentUser)) {
									notificationCenter.postNotificationName(NotificationCenter.userEmojiStatusUpdated, currentUser)
								}
							}

							val toDbUser: User = TL_user()
							toDbUser.id = baseUpdate.user_id
							toDbUser.emoji_status = baseUpdate.emoji_status

							dbUsers.add(toDbUser)
						}

						is TL_updateUserName -> {
							val currentUser = getUser(baseUpdate.user_id)

							if (currentUser != null) {
								if (!UserObject.isContact(currentUser)) {
									currentUser.first_name = baseUpdate.first_name
									currentUser.last_name = baseUpdate.last_name
								}

								if (!currentUser.username.isNullOrEmpty()) {
									objectsByUsernames.remove(currentUser.username)
								}

								if (!baseUpdate.username.isNullOrEmpty()) {
									objectsByUsernames[baseUpdate.username] = currentUser
								}

								currentUser.username = baseUpdate.username
							}

							val toDbUser: User = TL_user()
							toDbUser.id = baseUpdate.user_id
							toDbUser.first_name = baseUpdate.first_name
							toDbUser.last_name = baseUpdate.last_name
							toDbUser.username = baseUpdate.username

							dbUsers.add(toDbUser)
						}

						is TL_updateDialogPinned -> {
							val did = if (baseUpdate.peer is TL_dialogPeer) {
								val dialogPeer = baseUpdate.peer as TL_dialogPeer
								DialogObject.getPeerDialogId(dialogPeer.peer)
							}
							else {
								0L
							}

							if (!pinDialog(did, baseUpdate.pinned, null, -1)) {
								userConfig.setPinnedDialogsLoaded(baseUpdate.folder_id, false)
								userConfig.saveConfig(false)
								loadPinnedDialogs(baseUpdate.folder_id)
							}
						}

						is TL_updatePinnedDialogs -> {
							userConfig.setPinnedDialogsLoaded(baseUpdate.folder_id, false)
							userConfig.saveConfig(false)

							val order: ArrayList<Long>?

							if (baseUpdate.flags and 1 != 0) {
								order = ArrayList()

								for (dialogPeer in baseUpdate.order) {
									val did = if (dialogPeer is TL_dialogPeer) {
										val peer = dialogPeer.peer

										if (peer.user_id != 0L) {
											peer.user_id
										}
										else if (peer.chat_id != 0L) {
											-peer.chat_id
										}
										else {
											-peer.channel_id
										}
									}
									else {
										0
									}

									order.add(did)
								}
							}
							else {
								order = null
							}

							loadPinnedDialogs(baseUpdate.folder_id)
						}

						is TL_updateUserPhoto -> {
							val currentUser = getUser(baseUpdate.user_id)

							if (currentUser != null) {
								currentUser.photo = baseUpdate.photo
							}

							val toDbUser: User = TL_user()
							toDbUser.id = baseUpdate.user_id
							toDbUser.photo = baseUpdate.photo

							dbUsers.add(toDbUser)

							if (UserObject.isUserSelf(currentUser)) {
								notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
							}
						}

						is TL_updateUserPhone -> {
							val currentUser = getUser(baseUpdate.user_id)

							if (currentUser != null) {
								// currentUser.phone = update.phone;
								// Utilities.phoneBookQueue.postRunnable(() -> getContactsController().addContactToPhoneBook(currentUser, true));

								if (UserObject.isUserSelf(currentUser)) {
									notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
								}
							}

							val toDbUser: User = TL_user()
							toDbUser.id = baseUpdate.user_id
							// toDbUser.phone = update.phone;
							dbUsers.add(toDbUser)
						}

						is TL_updateNotifySettings -> {
							if (baseUpdate.notify_settings is TL_peerNotifySettings) {
								updateDialogFiltersFlags = updateDialogFiltersFlags or DIALOG_FILTER_FLAG_EXCLUDE_MUTED

								if (editor == null) {
									editor = notificationsPreferences.edit()
								}

								val currentTime1 = connectionsManager.currentTime

								if (baseUpdate.peer is TL_notifyPeer) {
									val notifyPeer = baseUpdate.peer as TL_notifyPeer

									val dialogId = if (notifyPeer.peer.user_id != 0L) {
										notifyPeer.peer.user_id
									}
									else if (notifyPeer.peer.chat_id != 0L) {
										-notifyPeer.peer.chat_id
									}
									else {
										-notifyPeer.peer.channel_id
									}

									val dialog = dialogs_dict[dialogId]

									if (dialog != null) {
										dialog.notify_settings = baseUpdate.notify_settings
									}

									if (baseUpdate.notify_settings.flags and 2 != 0) {
										editor?.putBoolean("silent_$dialogId", baseUpdate.notify_settings.silent)
									}
									else {
										editor?.remove("silent_$dialogId")
									}

									if (baseUpdate.notify_settings.flags and 4 != 0) {
										if (baseUpdate.notify_settings.mute_until > currentTime1) {
											var until = 0

											if (baseUpdate.notify_settings.mute_until > currentTime1 + 60 * 60 * 24 * 365) {
												editor?.putInt("notify2_$dialogId", 2)

												if (dialog != null) {
													baseUpdate.notify_settings.mute_until = Int.MAX_VALUE
												}
											}
											else {
												until = baseUpdate.notify_settings.mute_until

												editor?.putInt("notify2_$dialogId", 3)
												editor?.putInt("notifyuntil_$dialogId", baseUpdate.notify_settings.mute_until)

												if (dialog != null) {
													baseUpdate.notify_settings.mute_until = until
												}
											}

											messagesStorage.setDialogFlags(dialogId, until.toLong() shl 32 or 1L)

											notificationsController.removeNotificationsForDialog(dialogId)
										}
										else {
											if (dialog != null) {
												baseUpdate.notify_settings.mute_until = 0
											}

											editor?.putInt("notify2_$dialogId", 0)

											messagesStorage.setDialogFlags(dialogId, 0)
										}
									}
									else {
										if (dialog != null) {
											baseUpdate.notify_settings.mute_until = 0
										}

										editor?.remove("notify2_$dialogId")

										messagesStorage.setDialogFlags(dialogId, 0)
									}

									applySoundSettings(baseUpdate.notify_settings.android_sound, editor!!, dialogId, 0, true)
								}
								else if (baseUpdate.peer is TL_notifyChats) {
									if (baseUpdate.notify_settings.flags and 1 != 0) {
										editor?.putBoolean("EnablePreviewGroup", baseUpdate.notify_settings.show_previews)
									}

									// if ((update.notify_settings.flags & 2) != 0) {
									/*if (update.notify_settings.silent) {
											editor.putString("GroupSoundPath", "NoSound");
										} else {
											editor.remove("GroupSoundPath");
										}*/
									// }

									if (baseUpdate.notify_settings.flags and 4 != 0) {
										if (notificationsPreferences.getInt("EnableGroup2", 0) != baseUpdate.notify_settings.mute_until) {
											editor?.putInt("EnableGroup2", baseUpdate.notify_settings.mute_until)
											editor?.putBoolean("overwrite_group", true)

											AndroidUtilities.runOnUIThread {
												notificationsController.deleteNotificationChannelGlobal(NotificationsController.TYPE_GROUP)
											}
										}
									}

									applySoundSettings(baseUpdate.notify_settings.android_sound, editor!!, 0, NotificationsController.TYPE_GROUP, false)
								}
								else if (baseUpdate.peer is TL_notifyUsers) {
									if (baseUpdate.notify_settings.flags and 1 != 0) {
										editor?.putBoolean("EnablePreviewAll", baseUpdate.notify_settings.show_previews)
									}

									// if ((update.notify_settings.flags & 2) != 0) {
									/*if (update.notify_settings.silent) {
											editor.putString("GlobalSoundPath", "NoSound");
										} else {
											editor.remove("GlobalSoundPath");
										}*/
									// }

									applySoundSettings(baseUpdate.notify_settings.android_sound, editor!!, 0, NotificationsController.TYPE_PRIVATE, false)

									if (baseUpdate.notify_settings.flags and 4 != 0) {
										if (notificationsPreferences.getInt("EnableAll2", 0) != baseUpdate.notify_settings.mute_until) {
											editor.putInt("EnableAll2", baseUpdate.notify_settings.mute_until)
											editor.putBoolean("overwrite_private", true)

											AndroidUtilities.runOnUIThread {
												notificationsController.deleteNotificationChannelGlobal(NotificationsController.TYPE_PRIVATE)
											}
										}
									}
								}
								else if (baseUpdate.peer is TL_notifyBroadcasts) {
									if (baseUpdate.notify_settings.flags and 1 != 0) {
										editor?.putBoolean("EnablePreviewChannel", baseUpdate.notify_settings.show_previews)
									}

									// if ((update.notify_settings.flags & 2) != 0) {
									/*if (update.notify_settings.silent) {
											editor.putString("ChannelSoundPath", "NoSound");
										} else {
											editor.remove("ChannelSoundPath");
										}*/
									// }

									if (baseUpdate.notify_settings.flags and 4 != 0) {
										if (notificationsPreferences.getInt("EnableChannel2", 0) != baseUpdate.notify_settings.mute_until) {
											editor?.putInt("EnableChannel2", baseUpdate.notify_settings.mute_until)
											editor?.putBoolean("overwrite_channel", true)

											AndroidUtilities.runOnUIThread {
												notificationsController.deleteNotificationChannelGlobal(NotificationsController.TYPE_CHANNEL)
											}
										}
									}

									applySoundSettings(baseUpdate.notify_settings.android_sound, editor!!, 0, NotificationsController.TYPE_CHANNEL, false)
								}

								messagesStorage.updateMutedDialogsFiltersCounters()
							}
						}

						is TL_updateChannel -> {
							val dialog = dialogs_dict[-baseUpdate.channel_id]
							val chat = getChat(baseUpdate.channel_id)

							if (chat != null) {
								if (dialog == null && chat is TL_channel && !chat.left) {
									Utilities.stageQueue.postRunnable {
										getChannelDifference(baseUpdate.channel_id, 1, 0, null)
									}
								}
								else if (ChatObject.isNotInChat(chat) && dialog != null && (promoDialog == null || promoDialog!!.id != dialog.id)) {
									deleteDialog(dialog.id, 0)
								}

								if (chat is TL_channelForbidden || chat.kicked) {
									val call = getGroupCall(chat.id, false)

									if (call != null) {
										val updateGroupCall = TL_updateGroupCall()
										updateGroupCall.chat_id = chat.id
										updateGroupCall.call = TL_groupCallDiscarded()
										updateGroupCall.call.id = call.call?.id ?: 0
										updateGroupCall.call.access_hash = call.call?.access_hash ?: 0L

										call.processGroupCallUpdate(updateGroupCall)

										VoIPService.sharedInstance?.onGroupCallUpdated(updateGroupCall.call)
									}
								}
							}

							updateMask = updateMask or UPDATE_MASK_CHAT

							loadFullChat(baseUpdate.channel_id, 0, true)
						}

						is TL_updateChat -> {
							val chat = getChat(baseUpdate.chat_id)

							if (chat != null && (chat is TL_chatForbidden || chat.kicked)) {
								val call = getGroupCall(chat.id, false)

								if (call != null) {
									val updateGroupCall = TL_updateGroupCall()
									updateGroupCall.chat_id = chat.id
									updateGroupCall.call = TL_groupCallDiscarded()
									updateGroupCall.call.id = call.call?.id ?: 0
									updateGroupCall.call.access_hash = call.call?.access_hash ?: 0

									call.processGroupCallUpdate(updateGroupCall)

									VoIPService.sharedInstance?.onGroupCallUpdated(updateGroupCall.call)
								}

								val dialog = dialogs_dict[-chat.id]

								if (dialog != null) {
									deleteDialog(dialog.id, 0)
								}
							}

							updateMask = updateMask or UPDATE_MASK_CHAT

							loadFullChat(baseUpdate.chat_id, 0, true)
						}

						is TL_updateChatDefaultBannedRights -> {
							val chatId = if (baseUpdate.peer.channel_id != 0L) {
								baseUpdate.peer.channel_id
							}
							else {
								baseUpdate.peer.chat_id
							}

							val chat = getChat(chatId)

							if (chat != null) {
								chat.default_banned_rights = baseUpdate.default_banned_rights

								AndroidUtilities.runOnUIThread {
									notificationCenter.postNotificationName(NotificationCenter.channelRightsUpdated, chat)
								}
							}
						}

						is TL_updateBotCommands -> {
							mediaDataController.updateBotInfo(MessageObject.getPeerId(baseUpdate.peer), baseUpdate)
						}

						is TL_updateStickerSets -> {
							mediaDataController.loadStickers(MediaDataController.TYPE_IMAGE, cache = false, useHash = true, retry = true)
						}

						is TL_updateStickerSetsOrder -> {
							val type = if (baseUpdate.masks) {
								MediaDataController.TYPE_MASK
							}
							else if (baseUpdate.emojis) {
								MediaDataController.TYPE_EMOJIPACKS
							}
							else {
								MediaDataController.TYPE_IMAGE
							}

							mediaDataController.reorderStickers(type, baseUpdate.order, false)
						}

						is TL_updateRecentReactions -> {
							mediaDataController.loadRecentAndTopReactions(true)
						}

						is TL_updateFavedStickers -> {
							mediaDataController.loadRecents(MediaDataController.TYPE_FAVE, gif = false, cache = false, force = true)
						}

						is TL_updateContactsReset -> {
							// getContactsController().forceImportContacts();
						}

						is TL_updateNewStickerSet -> {
							mediaDataController.addNewStickerSet(baseUpdate.stickerset)
						}

						is TL_updateSavedGifs -> {
							val editor2 = getGlobalEmojiSettings().edit()
							editor2.putLong("lastGifLoadTime", 0).commit()
						}

						is TL_updateRecentStickers -> {
							val editor2 = getGlobalEmojiSettings().edit()
							editor2.putLong("lastStickersLoadTime", 0).commit()
						}

						is TL_updateDraftMessage -> {
							forceDialogsUpdate = true

							val peer = baseUpdate.peer

							val did = if (peer.user_id != 0L) {
								peer.user_id
							}
							else if (peer.channel_id != 0L) {
								-peer.channel_id
							}
							else {
								-peer.chat_id
							}

							mediaDataController.saveDraft(did, 0, baseUpdate.draft, null, true)
						}

						is TL_updateReadFeaturedStickers -> {
							mediaDataController.markFeaturedStickersAsRead(emoji = false, query = false)
						}

						is TL_updateReadFeaturedEmojiStickers -> {
							mediaDataController.markFeaturedStickersAsRead(emoji = true, query = false)
						}

						is TL_updateMoveStickerSetToTop -> {
							mediaDataController.moveStickerSetToTop(baseUpdate.stickerset, baseUpdate.emojis, baseUpdate.masks)
						}

						is TL_updatePhoneCallSignalingData -> {
							VoIPService.sharedInstance?.onSignalingData(baseUpdate)
						}

						is TL_updateGroupCallParticipants -> {
							val call = groupCalls[baseUpdate.call.id]
							call?.processParticipantsUpdate(baseUpdate, false)
							VoIPService.sharedInstance?.onGroupCallParticipantsUpdate(baseUpdate)
						}

						is TL_updateGroupCall -> {
							val call = groupCalls[baseUpdate.call.id]

							if (call != null) {
								call.processGroupCallUpdate(baseUpdate)

								val chat = getChat(call.chatId)

								if (chat != null) {
									chat.call_active = baseUpdate.call is TL_groupCall
								}
							}
							else {
								val chatFull = getChatFull(baseUpdate.chat_id)

								if (chatFull != null && (chatFull.call == null || chatFull.call != null && chatFull.call.id != baseUpdate.call.id)) {
									loadFullChat(baseUpdate.chat_id, 0, true)
								}
							}

							VoIPService.sharedInstance?.onGroupCallUpdated(baseUpdate.call)
						}

						is TL_updatePhoneCall -> {
							if (ContextCompat.checkSelfPermission(ApplicationLoader.applicationContext, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
								val call = baseUpdate.phone_call
								val svc = VoIPService.sharedInstance

								FileLog.d("Received call in update: $call")
								FileLog.d("call id " + call.id)

								if (call is TL_phoneCallRequested) {
									if ((call.date + callRingTimeout / 1000) < connectionsManager.currentTime) {
										FileLog.d("ignoring too old call")
										continue
									}

									var notificationsDisabled = false

									if (!NotificationManagerCompat.from(ApplicationLoader.applicationContext).areNotificationsEnabled()) {
										notificationsDisabled = true

										if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
											FileLog.d("Ignoring incoming call because notifications are disabled in system")
											continue
										}
									}

									val discardCallInvocation = {
										FileLog.d("Auto-declining call " + call.id + " because there's already active one")

										val req = TL_phone_discardCall()
										req.peer = TL_inputPhoneCall()
										req.peer.access_hash = call.access_hash
										req.peer.id = call.id
										req.reason = TL_phoneCallDiscardReasonBusy()

										connectionsManager.sendRequest(req) { response, _ ->
											if (response is Updates) {
												processUpdates(response, false)
											}
										}
									}

									val tm = ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
									var callStateIsIdle = true

									try {
										if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
											// TODO: check
											if (ActivityCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
												callStateIsIdle = tm.callStateForSubscription == TelephonyManager.CALL_STATE_IDLE
											}
										}
										else {
											callStateIsIdle = tm.callState == TelephonyManager.CALL_STATE_IDLE
										}
									}
									catch (e: Throwable) {
										FileLog.e(e)
									}

									if (svc != null || (VoIPService.callIShouldHavePutIntoIntent != null && VoIPService.callIShouldHavePutIntoIntent !is TL_phoneCallDiscarded) || !callStateIsIdle) {
										if (VoIPService.callIShouldHavePutIntoIntent?.id == call.id) {
											continue
										}

										FileLog.d("Will discard call with params:")
										FileLog.d("svc = $svc")
										FileLog.d("callIShouldHavePutIntoIntent = ${VoIPService.callIShouldHavePutIntoIntent}")
										FileLog.d("callStateIsIdle = $callStateIsIdle")

										discardCallInvocation()

										continue
									}

									val callerId = if (call.participant_id == userConfig.getClientUserId()) call.admin_id else call.participant_id
									val isMuted = isDialogMuted(callerId)

									FileLog.d("Starting service for call " + call.id)

									VoIPService.callIShouldHavePutIntoIntent = call

									val intent = Intent(ApplicationLoader.applicationContext, VoIPService::class.java)
									intent.putExtra("is_outgoing", false)
									intent.putExtra("user_id", callerId)
									intent.putExtra("account", currentAccount)
									intent.putExtra("notifications_disabled", notificationsDisabled)
									intent.putExtra("is_muted", isMuted)

									val continuation = {
										try {
											if (Build.VERSION.SDK_INT >= 33) {
												intent.putExtra("accept", true)
												VoIPPreNotificationService.show(ApplicationLoader.applicationContext, intent, call)
											}
											else if (!notificationsDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
												ApplicationLoader.applicationContext.startForegroundService(intent)
											}
											else {
												ApplicationLoader.applicationContext.startService(intent)
											}

											if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
												ignoreSetOnline = true
											}
										}
										catch (e: Throwable) {
											FileLog.e(e)

											if (VoIPService.callIShouldHavePutIntoIntent != null) {
												discardCallInvocation()
											}

											VoIPService.callIShouldHavePutIntoIntent = null
										}
									}

									val caller = getUser(callerId)

									if (caller == null) {
										ioScope.launch {
											loadUser(callerId, 0, true)

											mainScope.launch {
												continuation.invoke()
											}
										}
									}
									else {
										continuation.invoke()
									}
								}
								else {
									if (svc != null && call != null) {
										svc.onCallUpdated(call)
									}
									else {
										if (call is TL_phoneCallDiscarded) {
											VoIPPreNotificationService.dismiss(ApplicationLoader.applicationContext)
										}

										if (VoIPService.callIShouldHavePutIntoIntent != null) {
											FileLog.d("Updated the call while the service is starting")

											if (call.id == VoIPService.callIShouldHavePutIntoIntent?.id) {
												VoIPService.callIShouldHavePutIntoIntent = if (call is TL_phoneCallDiscarded) null else call
											}
										}
									}
								}
							}
						}

						is TL_updateDialogUnreadMark -> {
							val did = if (baseUpdate.peer is TL_dialogPeer) {
								val dialogPeer = baseUpdate.peer as TL_dialogPeer

								if (dialogPeer.peer.user_id != 0L) {
									dialogPeer.peer.user_id
								}
								else if (dialogPeer.peer.chat_id != 0L) {
									-dialogPeer.peer.chat_id
								}
								else {
									-dialogPeer.peer.channel_id
								}
							}
							else {
								0
							}

							messagesStorage.setDialogUnread(did, baseUpdate.unread)

							val dialog = dialogs_dict[did]

							if (dialog != null && dialog.unread_mark != baseUpdate.unread) {
								dialog.unread_mark = baseUpdate.unread

								if (dialog.unread_count == 0 && !isDialogMuted(did)) {
									if (dialog.unread_mark) {
										unreadUnmutedDialogs++
									}
									else {
										unreadUnmutedDialogs--
									}
								}

								updateMask = updateMask or UPDATE_MASK_READ_DIALOG_MESSAGE
								updateDialogFiltersFlags = updateDialogFiltersFlags or DIALOG_FILTER_FLAG_EXCLUDE_READ
							}
						}

						is TL_updateMessagePoll -> {
							notificationCenter.postNotificationName(NotificationCenter.didUpdatePollResults, baseUpdate.poll_id, baseUpdate.poll, baseUpdate.results)
						}

						is TL_updatePeerSettings -> {
							val dialogId = when (baseUpdate.peer) {
								is TL_peerUser -> baseUpdate.peer.user_id
								is TL_peerChat -> -baseUpdate.peer.chat_id
								else -> -baseUpdate.peer.channel_id
							}

							savePeerSettings(dialogId, baseUpdate.settings)
						}

						is TL_updatePeerLocated -> {
							notificationCenter.postNotificationName(NotificationCenter.newPeopleNearbyAvailable, baseUpdate)
						}

						is TL_updateMessageReactions -> {
							val dialogId = MessageObject.getPeerId(baseUpdate.peer)
							notificationCenter.postNotificationName(NotificationCenter.didUpdateReactions, dialogId, baseUpdate.msgId, baseUpdate.reactions)
						}

						is TL_updateMessageExtendedMedia -> {
							notificationCenter.postNotificationName(NotificationCenter.didUpdateExtendedMedia, DialogObject.getPeerDialogId(baseUpdate.peer), baseUpdate.msg_id, baseUpdate.extended_media)
						}

						is TL_updateTheme -> {
							val theme = baseUpdate.theme as TL_theme
							Theme.setThemeUploadInfo(null, null, theme, currentAccount, true)
						}

						is TL_updateDialogFilter -> {
							loadRemoteFilters(true)
						}

						is TL_updateDialogFilterOrder -> {
							loadRemoteFilters(true)
						}

						is TL_updateDialogFilters -> {
							loadRemoteFilters(true)
						}

						is TL_updateRecentEmojiStatuses -> {
							notificationCenter.postNotificationName(NotificationCenter.recentEmojiStatusesUpdate)
						}

						is TL_updateWebViewResultSent -> {
							notificationCenter.postNotificationName(NotificationCenter.webViewResultSent, baseUpdate.query_id)
						}

						is TL_updateAttachMenuBots -> {
							mediaDataController.loadAttachMenuBots(cache = false, force = true)
						}

						is TL_updateBotMenuButton -> {
							notificationCenter.postNotificationName(NotificationCenter.updateBotMenuButton, baseUpdate.bot_id, baseUpdate.button)
						}

						is TL_updateReadChannelDiscussionInbox -> {
							notificationCenter.postNotificationName(NotificationCenter.threadMessagesRead, -baseUpdate.channel_id, baseUpdate.top_msg_id, baseUpdate.read_max_id, 0)

							if (baseUpdate.flags and 1 != 0) {
								messagesStorage.updateRepliesMaxReadId(baseUpdate.broadcast_id, baseUpdate.broadcast_post, baseUpdate.read_max_id, true)
								notificationCenter.postNotificationName(NotificationCenter.commentsRead, baseUpdate.broadcast_id, baseUpdate.broadcast_post, baseUpdate.read_max_id)
							}
						}

						is TL_updateReadChannelDiscussionOutbox -> {
							notificationCenter.postNotificationName(NotificationCenter.threadMessagesRead, -baseUpdate.channel_id, baseUpdate.top_msg_id, 0, baseUpdate.read_max_id)
						}

						is TL_updatePeerHistoryTTL -> {
							val peerId = MessageObject.getPeerId(baseUpdate.peer)
							var chatFull: ChatFull? = null
							var userFull: UserFull? = null

							if (peerId > 0) {
								userFull = getUserFull(peerId)

								if (userFull != null) {
									userFull.ttl_period = baseUpdate.ttl_period

									if (userFull.ttl_period == 0) {
										userFull.flags = userFull.flags and 16384.inv()
									}
									else {
										userFull.flags = userFull.flags or 16384
									}
								}
							}
							else {
								chatFull = getChatFull(-peerId)

								if (chatFull != null) {
									chatFull.ttl_period = baseUpdate.ttl_period

									if (chatFull is TL_channelFull) {
										if (chatFull.ttl_period == 0) {
											chatFull.flags = chatFull.flags and 16777216.inv()
										}
										else {
											chatFull.flags = chatFull.flags or 16777216
										}
									}
									else {
										if (chatFull.ttl_period == 0) {
											chatFull.flags = chatFull.flags and 16384.inv()
										}
										else {
											chatFull.flags = chatFull.flags or 16384
										}
									}
								}
							}

							if (chatFull != null) {
								notificationCenter.postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false)
								messagesStorage.updateChatInfo(chatFull, false)
							}
							else if (userFull != null) {
								notificationCenter.postNotificationName(NotificationCenter.userInfoDidLoad, peerId, userFull)
								messagesStorage.updateUserInfo(userFull, false)
							}
						}

						is TL_updatePendingJoinRequests -> {
							memberRequestsController.onPendingRequestsUpdated(baseUpdate)
						}

						is TL_updateSavedRingtones -> {
							mediaDataController.ringtoneDataStore.loadUserRingtones()
						}

						is TL_updateTranscribeAudio -> {
							FileLog.e("Received legacy TL_updateTranscribeAudio update")
						}

						is TL_updateTranscribedAudio -> {
							if (BuildConfig.DEBUG) {
								FileLog.d("Transcription update received, pending=" + baseUpdate.pending + " id=" + baseUpdate.transcription_id + " text=" + baseUpdate.text)
							}

							if (!(baseUpdate.pending && baseUpdate.text.isNullOrEmpty())) {
								if (baseUpdate.pending || !TranscribeButton.finishTranscription(null, baseUpdate.transcription_id, baseUpdate.text)) {
									messagesStorage.updateMessageVoiceTranscription(DialogObject.getPeerDialogId(baseUpdate.peer), baseUpdate.msg_id, baseUpdate.text, baseUpdate.transcription_id, !baseUpdate.pending)
									notificationCenter.postNotificationName(NotificationCenter.voiceTranscriptionUpdate, null, baseUpdate.transcription_id, baseUpdate.text, null, !baseUpdate.pending)
								}
							}
						}
					}
				}

				if (editor != null) {
					editor.commit()
					notificationCenter.postNotificationName(NotificationCenter.notificationsSettingsUpdated)
				}

				messagesStorage.updateUsers(dbUsersStatus, true, true, true)
				messagesStorage.updateUsers(dbUsers, false, true, true)
			}

			if (!groupSpeakingActions.isEmpty) {
				var a = 0
				val n = groupSpeakingActions.size()

				while (a < n) {
					val chatId = groupSpeakingActions.keyAt(a)
					val call = groupCallsByChatId[chatId]
					call?.processTypingsUpdate(groupSpeakingActions.valueAt(a), date)
					a++
				}
			}

			if (importingActions.isNotEmpty) {
				var a = 0
				val n = importingActions.size()

				while (a < n) {
					val did = importingActions.keyAt(a)
					val importingHistory = sendMessagesHelper.getImportingHistory(did)

					if (importingHistory == null) {
						a++
						continue
					}

					importingHistory.setImportProgress(importingActions.valueAt(a))

					a++
				}
			}

			if (!webPages.isEmpty) {
				notificationCenter.postNotificationName(NotificationCenter.didReceivedWebpagesInUpdates, webPages)

				for (i in 0..1) {
					// val map = if (i == 1) reloadingScheduledWebpages else reloadingWebpages
					val array = if (i == 1) reloadingScheduledWebpagesPending else reloadingWebpagesPending
					var b = 0
					val size = webPages.size()

					while (b < size) {
						val key = webPages.keyAt(b)

						val arrayList = array[key]
						array.remove(key)

						if (arrayList != null) {
							val webpage = webPages.valueAt(b)
							val arr = ArrayList<Message>()
							var dialogId: Long = 0

							if (webpage is TL_webPage || webpage is TL_webPageEmpty) {
								var a = 0
								val size2 = arrayList.size

								while (a < size2) {
									arrayList[a].messageOwner?.media?.webpage = webpage

									if (a == 0) {
										dialogId = arrayList[a].dialogId
										ImageLoader.saveMessageThumbs(arrayList[a].messageOwner!!)
									}

									arr.add(arrayList[a].messageOwner!!)

									a++
								}
							}
							else {
								array.put(webpage.id, arrayList)
							}

							if (arr.isNotEmpty()) {
								messagesStorage.putMessages(arr, true, true, false, downloadController.autodownloadMask, i == 1)
								notificationCenter.postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, arrayList)
							}
						}

						b++
					}
				}
			}

			if (updateDialogFiltersFlags != 0) {
				for (dialogFilter in selectedDialogFilter) {
					if (dialogFilter != null && dialogFilter.flags and updateDialogFiltersFlags != 0) {
						forceDialogsUpdate = true
						break
					}
				}
			}

			var updateDialogs = false

			if (!messages.isEmpty) {
				var sorted = false
				var a = 0
				val size = messages.size()

				while (a < size) {
					val key = messages.keyAt(a)
					val value = messages.valueAt(a)

					if (updateInterfaceWithMessages(key, value, false)) {
						sorted = true
					}

					a++
				}

				val applied = applyFoldersUpdates(folderUpdates)

				if (applied || !sorted && forceDialogsUpdate) {
					sortDialogs(null)
				}

				updateDialogs = true
			}
			else {
				val applied = applyFoldersUpdates(folderUpdates)

				if (forceDialogsUpdate || applied) {
					sortDialogs(null)
					updateDialogs = true
				}
			}

			if (!scheduledMessages.isEmpty) {
				var a = 0
				val size = scheduledMessages.size()

				while (a < size) {
					val key = scheduledMessages.keyAt(a)
					val value = scheduledMessages.valueAt(a)

					updateInterfaceWithMessages(key, value, true)

					a++
				}
			}

			if (!editingMessages.isEmpty) {
				var b = 0
				val size = editingMessages.size()

				while (b < size) {
					val dialogId = editingMessages.keyAt(b)
					var unreadReactions: SparseBooleanArray? = null
					val arrayList = editingMessages.valueAt(b)

					for (messageObject in arrayList) {
						if (dialogId > 0) {
							if (unreadReactions == null) {
								unreadReactions = SparseBooleanArray()
							}

							unreadReactions.put(messageObject.id, MessageObject.hasUnreadReactions(messageObject.messageOwner))
						}
					}

					if (dialogId > 0) {
						checkUnreadReactions(dialogId, unreadReactions)
					}

					val oldObject = dialogMessage[dialogId]

					if (oldObject != null) {
						for (newMessage in arrayList) {
							if (oldObject.id == newMessage.id) {
								dialogMessage.put(dialogId, newMessage)

								if (newMessage.messageOwner?.peer_id != null && newMessage.messageOwner?.peer_id?.channel_id == 0L) {
									dialogMessagesByIds.put(newMessage.id, newMessage)
								}

								updateDialogs = true

								break
							}
							else if (oldObject.dialogId == newMessage.dialogId && oldObject.messageOwner?.action is TL_messageActionPinMessage && oldObject.replyMessageObject != null && oldObject.replyMessageObject?.id == newMessage.id) {
								oldObject.replyMessageObject = newMessage
								oldObject.generatePinMessageText(null, null)
								updateDialogs = true
								break
							}
						}
					}

					mediaDataController.loadReplyMessagesForMessages(arrayList, dialogId, false, null)
					notificationCenter.postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, arrayList, false)

					b++
				}
			}

			if (updateDialogs) {
				notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
			}

			if (printChangedArg) {
				updateMask = updateMask or UPDATE_MASK_USER_PRINT
			}

			if (contactsIds.isNotEmpty()) {
				updateMask = updateMask or UPDATE_MASK_NAME
				updateMask = updateMask or UPDATE_MASK_USER_PHONE
			}

			if (chatInfoToUpdate.isNotEmpty()) {
				for (info in chatInfoToUpdate) {
					messagesStorage.updateChatParticipants(info)
				}
			}

			if (!channelViews.isEmpty || !channelForwards.isEmpty || !channelReplies.isEmpty) {
				notificationCenter.postNotificationName(NotificationCenter.didUpdateMessagesViews, channelViews, channelForwards, channelReplies, true)
			}

			if (updateMask != 0) {
				notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, updateMask)
			}

			if (messageThumbs.isNotEmpty()) {
				ImageLoader.instance.putThumbsToCache(messageThumbs)
			}
		}

		messagesStorage.storageQueue.postRunnable {
			AndroidUtilities.runOnUIThread {
				var updateMask = 0

				if (markAsReadMessagesInbox.isNotEmpty || markAsReadMessagesOutbox.isNotEmpty) {
					notificationCenter.postNotificationName(NotificationCenter.messagesRead, markAsReadMessagesInbox, markAsReadMessagesOutbox)

					if (markAsReadMessagesInbox.isNotEmpty) {
						notificationsController.processReadMessages(markAsReadMessagesInbox, 0, 0, 0, false)

						val editor = notificationsPreferences.edit()
						var b = 0
						val size = markAsReadMessagesInbox.size()

						while (b < size) {
							val key = markAsReadMessagesInbox.keyAt(b)
							val messageId = markAsReadMessagesInbox.valueAt(b)
							val dialog = dialogs_dict[key]

							if (dialog != null && dialog.top_message > 0 && dialog.top_message <= messageId) {
								val obj = dialogMessage[dialog.id]

								if (obj != null && !obj.isOut) {
									obj.setIsRead()
									updateMask = updateMask or UPDATE_MASK_READ_DIALOG_MESSAGE
								}
							}

							if (key != userConfig.getClientUserId()) {
								editor.remove("diditem$key")
								editor.remove("diditemo$key")
							}

							b++
						}

						editor.commit()
					}

					if (markAsReadMessagesOutbox.isNotEmpty) {
						var b = 0
						val size = markAsReadMessagesOutbox.size()

						while (b < size) {
							val key = markAsReadMessagesOutbox.keyAt(b)
							val messageId = markAsReadMessagesOutbox.valueAt(b)
							val dialog = dialogs_dict[key]

							if (dialog != null && dialog.top_message > 0 && dialog.top_message <= messageId) {
								val obj = dialogMessage[dialog.id]

								if (obj != null && obj.isOut) {
									obj.setIsRead()
									updateMask = updateMask or UPDATE_MASK_READ_DIALOG_MESSAGE
								}
							}

							b++
						}
					}
				}

				if (markAsReadEncrypted.isNotEmpty()) {
					var a = 0
					val size = markAsReadEncrypted.size()

					while (a < size) {
						val key = markAsReadEncrypted.keyAt(a)
						val value = markAsReadEncrypted.valueAt(a)

						notificationCenter.postNotificationName(NotificationCenter.messagesReadEncrypted, key, value)

						val dialogId = DialogObject.makeEncryptedDialogId(key.toLong())
						val dialog = dialogs_dict[dialogId]

						if (dialog != null) {
							val message = dialogMessage[dialogId]

							if (message != null && message.messageOwner!!.date <= value) {
								message.setIsRead()
								updateMask = updateMask or UPDATE_MASK_READ_DIALOG_MESSAGE
							}
						}

						a++
					}
				}

				if (!markContentAsReadMessages.isEmpty) {
					var a = 0
					val size = markContentAsReadMessages.size()

					while (a < size) {
						val key = markContentAsReadMessages.keyAt(a)
						val value = markContentAsReadMessages.valueAt(a)

						notificationCenter.postNotificationName(NotificationCenter.messagesReadContent, key, value)

						a++
					}
				}

				if (!deletedMessages.isEmpty) {
					var a = 0
					val size = deletedMessages.size()

					while (a < size) {
						val dialogId = deletedMessages.keyAt(a)
						val arrayList = deletedMessages.valueAt(a)

						if (arrayList == null) {
							a++
							continue
						}

						notificationCenter.postNotificationName(NotificationCenter.messagesDeleted, arrayList, -dialogId, false)

						if (dialogId == 0L) {
							var b = 0
							val size2 = arrayList.size

							while (b < size2) {
								val id = arrayList[b]
								val obj = dialogMessagesByIds[id]

								if (obj != null) {
									if (BuildConfig.DEBUG) {
										FileLog.d("mark messages " + obj.id + " deleted")
									}

									obj.deleted = true
								}
								b++
							}
						}
						else {
							val obj = dialogMessage[dialogId]

							if (obj != null) {
								var b = 0
								val size2 = arrayList.size

								while (b < size2) {
									if (obj.id == arrayList[b]) {
										obj.deleted = true
										break
									}

									b++
								}
							}
						}

						a++
					}

					notificationsController.removeDeletedMessagesFromNotifications(deletedMessages, false)
				}

				if (!scheduledDeletedMessages.isEmpty) {
					var a = 0
					val size = scheduledDeletedMessages.size()

					while (a < size) {
						val key = scheduledDeletedMessages.keyAt(a)
						val arrayList = scheduledDeletedMessages.valueAt(a)

						if (arrayList == null) {
							a++
							continue
						}

						notificationCenter.postNotificationName(NotificationCenter.messagesDeleted, arrayList, if (DialogObject.isChatDialog(key) && ChatObject.isChannel(getChat(-key))) -key else 0, true)

						a++
					}
				}

				if (clearHistoryMessages.isNotEmpty) {
					var a = 0
					val size = clearHistoryMessages.size()

					while (a < size) {
						val key = clearHistoryMessages.keyAt(a)
						val id = clearHistoryMessages.valueAt(a)
						val did = -key

						notificationCenter.postNotificationName(NotificationCenter.historyCleared, did, id)

						val obj = dialogMessage[did]

						if (obj != null) {
							if (obj.id <= id) {
								obj.deleted = true
								break
							}
						}

						a++
					}

					notificationsController.removeDeletedHistoryFromNotifications(clearHistoryMessages)
				}

				if (updateMask != 0) {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, updateMask)
				}
			}
		}

		if (!webPages.isEmpty) {
			messagesStorage.putWebPages(webPages)
		}

		if (markAsReadMessagesInbox.isNotEmpty || markAsReadMessagesOutbox.isNotEmpty || markAsReadEncrypted.isNotEmpty() || !markContentAsReadMessages.isEmpty || stillUnreadMessagesCount.isNotEmpty) {
			if (markAsReadMessagesInbox.isNotEmpty || markAsReadMessagesOutbox.isNotEmpty || !markContentAsReadMessages.isEmpty || stillUnreadMessagesCount.isNotEmpty) {
				messagesStorage.updateDialogsWithReadMessages(markAsReadMessagesInbox, markAsReadMessagesOutbox, markContentAsReadMessages, stillUnreadMessagesCount, true)
			}

			messagesStorage.markMessagesAsRead(markAsReadMessagesInbox, markAsReadMessagesOutbox, markAsReadEncrypted, true)
		}

		if (!markContentAsReadMessages.isEmpty) {
			val time = connectionsManager.currentTime
			var a = 0
			val size = markContentAsReadMessages.size()

			while (a < size) {
				val key = markContentAsReadMessages.keyAt(a)
				val arrayList = markContentAsReadMessages.valueAt(a)

				messagesStorage.markMessagesContentAsRead(key, arrayList, time)

				a++
			}
		}

		if (!deletedMessages.isEmpty) {
			var a = 0
			val size = deletedMessages.size()

			while (a < size) {
				val key = deletedMessages.keyAt(a)
				val arrayList = deletedMessages.valueAt(a)

				messagesStorage.storageQueue.postRunnable {
					val dialogIds = messagesStorage.markMessagesAsDeleted(key, arrayList, false, true, false)
					messagesStorage.updateDialogsWithDeletedMessages(key, -key, arrayList, dialogIds, false)
				}

				a++
			}
		}

		if (!scheduledDeletedMessages.isEmpty) {
			var a = 0
			val size = scheduledDeletedMessages.size()

			while (a < size) {
				val key = scheduledDeletedMessages.keyAt(a)
				val arrayList = scheduledDeletedMessages.valueAt(a)

				messagesStorage.markMessagesAsDeleted(key, arrayList, true, false, true)

				a++
			}
		}

		if (clearHistoryMessages.isNotEmpty) {
			var a = 0
			val size = clearHistoryMessages.size()

			while (a < size) {
				val key = clearHistoryMessages.keyAt(a)
				val id = clearHistoryMessages.valueAt(a)

				messagesStorage.storageQueue.postRunnable {
					val dialogIds = messagesStorage.markMessagesAsDeleted(key, id, false, true)
					messagesStorage.updateDialogsWithDeletedMessages(key, -key, ArrayList(), dialogIds, false)
				}

				a++
			}
		}

		for (update in tasks) {
			messagesStorage.createTaskForSecretChat(update.chat_id, update.max_date, update.date, 1, null)
		}

		return true
	}

	fun checkUnreadReactions(dialogId: Long, unreadReactions: SparseBooleanArray?) {
		messagesStorage.storageQueue.postRunnable {
			var needReload = false
			var changed = false
			val newUnreadMessages = mutableListOf<Int>()

			val stringBuilder = buildString {
				if (unreadReactions != null) {
					for (i in 0 until unreadReactions.size()) {
						val messageId = unreadReactions.keyAt(i)

						if (isNotEmpty()) {
							append(", ")
						}

						append(messageId)
					}
				}
			}

			val reactionsMentionsMessageIds = SparseBooleanArray()

			try {
				val cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT message_id, state FROM reaction_mentions WHERE message_id IN (%s) AND dialog_id = %d", stringBuilder, dialogId))

				while (cursor.next()) {
					val messageId = cursor.intValue(0)
					val hasUnreadReactions = cursor.intValue(1) == 1
					reactionsMentionsMessageIds.put(messageId, hasUnreadReactions)
				}

				cursor.dispose()
			}
			catch (e: SQLiteException) {
				FileLog.e(e)
			}

			var newUnreadCount = 0

			if (unreadReactions != null) {
				for (i in 0 until unreadReactions.size()) {
					val messageId = unreadReactions.keyAt(i)
					val hasUnreadReaction = unreadReactions.valueAt(i)

					if (reactionsMentionsMessageIds.indexOfKey(messageId) >= 0) {
						if (reactionsMentionsMessageIds[messageId] != hasUnreadReaction) {
							newUnreadCount += if (hasUnreadReaction) 1 else -1
							changed = true
						}
					}
					else {
						needReload = true
					}

					if (hasUnreadReaction) {
						newUnreadMessages.add(messageId)
					}

					try {
						val state = messagesStorage.database.executeFast("REPLACE INTO reaction_mentions VALUES(?, ?, ?)")
						state.requery()
						state.bindInteger(1, messageId)
						state.bindInteger(2, if (hasUnreadReaction) 1 else 0)
						state.bindLong(3, dialogId)
						state.step()
						state.dispose()
					}
					catch (e: SQLiteException) {
						FileLog.e(e)
					}
				}
			}

			if (needReload) {
				val req = TL_messages_getPeerDialogs()

				val inputDialogPeer = TL_inputDialogPeer()
				inputDialogPeer.peer = getInputPeer(dialogId)

				req.peers.add(inputDialogPeer)

				connectionsManager.sendRequest(req) { response, _ ->
					if (response is TL_messages_peerDialogs) {
						val count = if (response.dialogs.size == 0) 0 else response.dialogs[0].unread_reactions_count

						AndroidUtilities.runOnUIThread {
							val dialog = dialogs_dict[dialogId]

							if (dialog == null) {
								messagesStorage.updateDialogUnreadReactions(dialogId, count, false)
								return@runOnUIThread
							}

							dialog.unread_reactions_count = count

							messagesStorage.updateUnreadReactionsCount(dialogId, count)

							notificationCenter.postNotificationName(NotificationCenter.dialogsUnreadReactionsCounterChanged, dialogId, count, newUnreadMessages)
						}
					}
				}
			}
			else if (changed) {
				AndroidUtilities.runOnUIThread {
					val dialog = dialogs_dict[dialogId]

					if (dialog == null) {
						messagesStorage.updateDialogUnreadReactions(dialogId, newUnreadCount, true)
						return@runOnUIThread
					}

					dialog.unread_reactions_count += newUnreadCount

					if (dialog.unread_reactions_count < 0) {
						dialog.unread_reactions_count = 0
					}

					messagesStorage.updateUnreadReactionsCount(dialogId, dialog.unread_reactions_count)

					notificationCenter.postNotificationName(NotificationCenter.dialogsUnreadReactionsCounterChanged, dialogId, dialog.unread_reactions_count, newUnreadMessages)
				}
			}
		}
	}

	fun isDialogMuted(dialogId: Long): Boolean {
		return isDialogMuted(dialogId, null)
	}

	fun isDialogNotificationsSoundEnabled(dialogId: Long): Boolean {
		return notificationsPreferences.getBoolean("sound_enabled_$dialogId", true)
	}

	fun isDialogMuted(dialogId: Long, chat: Chat?): Boolean {
		val muteType = notificationsPreferences.getInt("notify2_$dialogId", -1)

		if (muteType == -1) {
			val forceChannel = if (chat != null) {
				ChatObject.isChannel(chat) && !chat.megagroup
			}
			else {
				null
			}

			return !notificationsController.isGlobalNotificationsEnabled(dialogId, forceChannel)
		}
		if (muteType == 2) {
			return true
		}
		else if (muteType == 3) {
			val muteUntil = notificationsPreferences.getInt("notifyuntil_$dialogId", 0)
			return muteUntil >= connectionsManager.currentTime
		}

		return false
	}

	fun markReactionsAsRead(dialogId: Long) {
		val dialog = dialogs_dict[dialogId]

		if (dialog != null) {
			dialog.unread_reactions_count = 0
		}

		messagesStorage.updateUnreadReactionsCount(dialogId, 0)

		val req = TL_messages_readReactions()
		req.peer = getInputPeer(dialogId)

		connectionsManager.sendRequest(req)
	}

	fun getSponsoredMessages(dialogId: Long): List<MessageObject>? {
		var info = sponsoredMessages[dialogId]

		if (info != null && (info.loading || abs(SystemClock.elapsedRealtime() - info.loadTime) <= 5 * 60 * 1000)) {
			return info.messages
		}

		val chat = getChat(-dialogId)

		if (!ChatObject.isChannel(chat)) {
			return null
		}

		info = SponsoredMessagesInfo()
		info.loading = true

		sponsoredMessages.put(dialogId, info)

		val infoFinal: SponsoredMessagesInfo = info

		val req = TL_channels_getSponsoredMessages()
		req.channel = getInputChannel(chat)

		connectionsManager.sendRequest(req) { response, _ ->
			val result: ArrayList<MessageObject>?

			if (response is TL_messages_sponsoredMessages) {
				if (response.messages.isEmpty()) {
					result = null
				}
				else {
					result = ArrayList()

					AndroidUtilities.runOnUIThread {
						putUsers(response.users, false)
						putChats(response.chats, false)
					}

					val usersDict = LongSparseArray<User>()
					val chatsDict = LongSparseArray<Chat>()

					for (user in response.users) {
						usersDict.put(user.id, user)
					}

					response.chats.forEach {
						chatsDict.put(it.id, it)
					}

					var messageId = -10000000

					for (sponsoredMessage in response.messages) {
						val message = TL_message()
						message.message = sponsoredMessage.message

						if (sponsoredMessage.entities.isNotEmpty()) {
							message.entities = sponsoredMessage.entities
							message.flags = message.flags or 128
						}

						message.peer_id = getPeer(dialogId)
						message.from_id = sponsoredMessage.from_id
						message.flags = message.flags or 256
						message.date = connectionsManager.currentTime
						message.id = messageId--

						val messageObject = MessageObject(currentAccount, message, usersDict, chatsDict, generateLayout = true, checkMediaExists = true)
						messageObject.sponsoredId = sponsoredMessage.random_id
						messageObject.botStartParam = sponsoredMessage.start_param
						messageObject.sponsoredChannelPost = sponsoredMessage.channel_post
						messageObject.sponsoredChatInvite = sponsoredMessage.chat_invite
						messageObject.sponsoredChatInviteHash = sponsoredMessage.chat_invite_hash

						result.add(messageObject)
					}
				}
			}
			else {
				result = null
			}

			AndroidUtilities.runOnUIThread {
				if (result == null) {
					sponsoredMessages.remove(dialogId)
				}
				else {
					infoFinal.loadTime = SystemClock.elapsedRealtime()
					infoFinal.messages = result

					notificationCenter.postNotificationName(NotificationCenter.didLoadSponsoredMessages, dialogId, result)
				}
			}
		}

		return null
	}

	fun getSendAsPeers(dialogId: Long): TL_channels_sendAsPeers? {
		var info = sendAsPeers[dialogId]

		if (info != null && (info.loading || abs(SystemClock.elapsedRealtime() - info.loadTime) <= 5 * 60 * 1000)) {
			return info.sendAsPeers
		}

		val chat = getChat(-dialogId)

		if (chat == null || !ChatObject.canSendAsPeers(chat)) {
			return null
		}

		info = SendAsPeersInfo()
		info.loading = true

		sendAsPeers.put(dialogId, info)

		val infoFinal: SendAsPeersInfo = info

		val req = TL_channels_getSendAs()
		req.peer = getInputPeer(dialogId)

		connectionsManager.sendRequest(req) { response, _ ->
			val result: TL_channels_sendAsPeers?

			if (response is TL_channels_sendAsPeers) {
				if (response.peers.isEmpty()) {
					result = null
				}
				else {
					result = response

					AndroidUtilities.runOnUIThread {
						putUsers(response.users, false)
						putChats(response.chats, false)
					}

					val usersDict = LongSparseArray<User>()
					val chatsDict = LongSparseArray<Chat>()

					for (user in response.users) {
						usersDict.put(user.id, user)
					}

					response.chats.forEach {
						chatsDict.put(it.id, it)
					}
				}
			}
			else {
				result = null
			}

			AndroidUtilities.runOnUIThread {
				if (result == null) {
					sendAsPeers.remove(dialogId)
				}
				else {
					infoFinal.loadTime = SystemClock.elapsedRealtime()
					infoFinal.sendAsPeers = result

					notificationCenter.postNotificationName(NotificationCenter.didLoadSendAsPeers, dialogId, result)
				}
			}
		}

		return null
	}

	fun getPrintingString(dialogId: Long, threadId: Int, isDialog: Boolean): CharSequence? {
		if (isDialog && DialogObject.isUserDialog(dialogId)) {
			val user = getUser(dialogId)

			if (user?.status != null && user.status!!.expires < 0) {
				return null
			}
		}

		val threads = printingStrings[dialogId] ?: return null

		return threads[threadId]
	}

	fun getPrintingStringType(dialogId: Long, threadId: Int): Int? {
		val threads = printingStringsTypes[dialogId] ?: return null
		return threads[threadId]
	}

	private fun updatePrintingUsersWithNewMessages(uid: Long, messages: List<MessageObject>): Boolean {
		if (uid > 0) {
			val arr = printingUsers[uid]

			if (arr != null) {
				printingUsers.remove(uid)
				return true
			}
		}
		else if (uid < 0) {
			val messagesUsers = mutableListOf<Long>()

			for (message in messages) {
				if (message.isFromUser && !messagesUsers.contains(message.messageOwner?.from_id?.user_id)) {
					messagesUsers.add(message.messageOwner!!.from_id!!.user_id)
				}
			}

			val threads = printingUsers[uid]
			var changed = false

			if (threads != null) {
				var threadsToRemove: ArrayList<Int?>? = null

				for ((threadId, arr) in threads) {
					var a = 0

					while (a < arr.size) {
						val user = arr[a]

						if (messagesUsers.contains(user.userId)) {
							arr.removeAt(a)
							a--

							if (arr.isEmpty()) {
								if (threadsToRemove == null) {
									threadsToRemove = ArrayList()
								}

								threadsToRemove.add(threadId)
							}

							changed = true
						}

						a++
					}
				}

				if (threadsToRemove != null) {
					for (t in threadsToRemove) {
						threads.remove(t)
					}

					if (threads.isEmpty()) {
						printingUsers.remove(uid)
					}
				}
			}

			return changed
		}

		return false
	}

	fun updateInterfaceWithMessages(dialogId: Long, messages: List<MessageObject>?, scheduled: Boolean): Boolean {
		if (messages.isNullOrEmpty()) {
			return false
		}

		val isEncryptedChat = DialogObject.isEncryptedDialog(dialogId)
		var lastMessage: MessageObject? = null
		var channelId: Long = 0
		var updateRating = false
		var hasNotOutMessage = false

		if (!scheduled) {
			for (a in messages.indices) {
				val message = messages[a]

				if (lastMessage == null || !isEncryptedChat && message.id > lastMessage.id || (isEncryptedChat || message.id < 0 && lastMessage.id < 0) && message.id < lastMessage.id || message.messageOwner!!.date > lastMessage.messageOwner!!.date) {
					lastMessage = message

					if (message.messageOwner?.peer_id?.channel_id != 0L) {
						channelId = message.messageOwner?.peer_id?.channel_id ?: 0L
					}
				}

				if (message.messageOwner?.action is TL_messageActionGroupCall) {
					val chatFull = getChatFull(message.messageOwner?.peer_id?.channel_id)

					if (chatFull != null && (chatFull.call == null || chatFull.call.id != message.messageOwner?.action?.call?.id)) {
						loadFullChat(message.messageOwner?.peer_id?.channel_id ?: 0L, 0, true)
					}
				}

				if (!hasNotOutMessage && !message.isOut) {
					hasNotOutMessage = true
				}

				if (message.isOut && !message.isSending && !message.isForwarded) {
					if (message.isNewGif) {
						val save = if (MessageObject.isDocumentHasAttachedStickers(message.messageOwner?.media?.document)) {
							messagesController.saveGifsWithStickers
						}
						else {
							true
						}

						if (save) {
							mediaDataController.addRecentGif(message.messageOwner?.media?.document, message.messageOwner!!.date, true)
						}
					}
					else if (!message.isAnimatedEmoji && (message.isSticker || message.isAnimatedSticker)) {
						message.messageOwner?.media?.document?.let {
							mediaDataController.addRecentSticker(MediaDataController.TYPE_IMAGE, message, it, message.messageOwner?.date ?: 0, false)
						}
					}
				}

				if (message.isOut && message.isSent) {
					updateRating = true
				}
			}
		}

		mediaDataController.loadReplyMessagesForMessages(messages, dialogId, scheduled, null)

		notificationCenter.postNotificationName(NotificationCenter.didReceiveNewMessages, dialogId, messages, scheduled)

		if (lastMessage == null || scheduled) {
			return false
		}

		var dialog = dialogs_dict[dialogId] as? TL_dialog

		if (lastMessage.messageOwner?.action is TL_messageActionChatMigrateTo) {
			if (dialog != null) {
				allDialogs.remove(dialog)
				dialogsServerOnly.remove(dialog)
				dialogsCanAddUsers.remove(dialog)
				dialogsMyGroups.remove(dialog)
				dialogsMyChannels.remove(dialog)
				dialogsChannelsOnly.remove(dialog)
				dialogsGroupsOnly.remove(dialog)

				for (dialogFilter in selectedDialogFilter) {
					dialogFilter?.dialogs?.remove(dialog)
				}

				dialogsUsersOnly.remove(dialog)
				dialogsForBlock.remove(dialog)
				dialogsForward.remove(dialog)
				dialogs_dict.remove(dialog.id)
				dialogs_read_inbox_max.remove(dialog.id)
				dialogs_read_outbox_max.remove(dialog.id)

				val offset = nextDialogsCacheOffset[dialog.folder_id, 0]

				if (offset > 0) {
					nextDialogsCacheOffset.put(dialog.folder_id, offset - 1)
				}

				dialogMessage.remove(dialog.id)

				val dialogs = dialogsByFolder[dialog.folder_id]
				dialogs?.remove(dialog)

				val `object` = dialogMessagesByIds[dialog.top_message]

				if (`object` != null && `object`.messageOwner?.peer_id?.channel_id == 0L) {
					dialogMessagesByIds.remove(dialog.top_message)
				}

				if (`object` != null && `object`.messageOwner!!.random_id != 0L) {
					dialogMessagesByRandomIds.remove(`object`.messageOwner!!.random_id)
				}

				dialog.top_message = 0

				notificationsController.removeNotificationsForDialog(dialog.id)

				notificationCenter.postNotificationName(NotificationCenter.needReloadRecentDialogsSearch)
			}

			if (DialogObject.isChatDialog(dialogId)) {
				val call = getGroupCall(-dialogId, false)

				if (call != null) {
					val chat = getChat(lastMessage.messageOwner?.action?.channel_id)

					if (chat != null) {
						call.migrateToChat(chat)
					}
				}
			}

			return false
		}

		var changed = false

		if (dialog == null) {
			val chat = getChat(channelId)

			if (channelId != 0L && chat == null || chat != null && (ChatObject.isNotInChat(chat) || chat.min)) {
				return false
			}

			if (BuildConfig.DEBUG) {
				FileLog.d("not found dialog with id " + dialogId + " dictCount = " + dialogs_dict.size() + " allCount = " + allDialogs.size)
			}

			dialog = TL_dialog()
			dialog.id = dialogId
			dialog.top_message = lastMessage.id

			val mid = dialog.top_message

			dialog.last_message_date = lastMessage.messageOwner!!.date
			dialog.flags = if (ChatObject.isChannel(chat)) 1 else 0

			dialogs_dict.put(dialogId, dialog)
			allDialogs.add(dialog)
			dialogMessage.put(dialogId, lastMessage)

			if (lastMessage.messageOwner?.peer_id?.channel_id == 0L) {
				dialogMessagesByIds.put(lastMessage.id, lastMessage)

				if (lastMessage.messageOwner?.random_id != 0L) {
					dialogMessagesByRandomIds.put(lastMessage.messageOwner!!.random_id, lastMessage)
				}
			}

			changed = true

			val dialogFinal: Dialog = dialog

			messagesStorage.getDialogFolderId(dialogId) {
				if (it != -1) {
					if (it != 0) {
						dialogFinal.folder_id = it
						sortDialogs(null)
						notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload, true)
					}
				}
				else if (mid > 0) {
					if (!DialogObject.isEncryptedDialog(dialogId)) {
						loadUnknownDialog(getInputPeer(dialogId), 0)
					}
				}
			}
		}
		else {
			if (dialog.top_message > 0 && lastMessage.id > 0 && lastMessage.id > dialog.top_message || dialog.top_message < 0 && lastMessage.id < 0 && lastMessage.id < dialog.top_message || dialogMessage.indexOfKey(dialogId) < 0 || dialog.top_message < 0 || dialog.last_message_date <= lastMessage.messageOwner!!.date) {
				val `object` = dialogMessagesByIds[dialog.top_message]

				if (`object` != null && `object`.messageOwner?.peer_id?.channel_id == 0L) {
					dialogMessagesByIds.remove(dialog.top_message)
				}

				if (`object` != null && `object`.messageOwner?.random_id != 0L) {
					dialogMessagesByRandomIds.remove(`object`.messageOwner!!.random_id)
				}

				dialog.top_message = lastMessage.id
				dialog.last_message_date = lastMessage.messageOwner!!.date

				changed = true

				dialogMessage.put(dialogId, lastMessage)

				if (lastMessage.messageOwner?.peer_id?.channel_id == 0L) {
					dialogMessagesByIds.put(lastMessage.id, lastMessage)

					if (lastMessage.messageOwner?.random_id != 0L) {
						dialogMessagesByRandomIds.put(lastMessage.messageOwner!!.random_id, lastMessage)
					}
				}
			}
		}

		if (changed) {
			sortDialogs(null)
		}

		if (updateRating) {
			mediaDataController.increasePeerRating(dialogId)
		}

		return changed
	}

	fun addDialogAction(did: Long, clean: Boolean) {
		val dialog = dialogs_dict[did] ?: return

		if (clean) {
			clearingHistoryDialogs.put(did, dialog)
		}
		else {
			deletingDialogs.put(did, dialog)
			allDialogs.remove(dialog)
			sortDialogs(null)
		}

		notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload, true)
	}

	fun removeDialogAction(did: Long, clean: Boolean, apply: Boolean) {
		val dialog = dialogs_dict[did] ?: return

		if (clean) {
			clearingHistoryDialogs.remove(did)
		}
		else {
			deletingDialogs.remove(did)

			if (!apply) {
				allDialogs.add(dialog)
				sortDialogs(null)
			}
		}

		if (!apply) {
			notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload, true)
		}
	}

	fun isClearingDialog(did: Long): Boolean {
		return clearingHistoryDialogs[did] != null
	}

	fun sortDialogs(chatsDict: LongSparseArray<Chat>?) {
		dialogsServerOnly.clear()
		dialogsCanAddUsers.clear()
		dialogsMyGroups.clear()
		dialogsMyChannels.clear()
		dialogsChannelsOnly.clear()
		dialogsGroupsOnly.clear()

		for (filter in selectedDialogFilter) {
			filter?.dialogs?.clear()
		}

		dialogsUsersOnly.clear()
		dialogsForBlock.clear()
		dialogsForward.clear()

		for (a in 0 until dialogsByFolder.size()) {
			val arrayList = dialogsByFolder.valueAt(a)
			arrayList?.clear()
		}

		unreadUnmutedDialogs = 0

		var selfAdded = false
		val selfId = userConfig.getClientUserId()

		if (selectedDialogFilter[0] != null || selectedDialogFilter[1] != null) {
			for (dialogFilter in selectedDialogFilter) {
				sortingDialogFilter = dialogFilter

				if (sortingDialogFilter == null) {
					continue
				}

				Collections.sort(allDialogs, dialogDateComparator)

				val dialogsByFilter = sortingDialogFilter!!.dialogs

				for (d in allDialogs) {
					if (d is TL_dialog) {
						var dialogId = d.id

						if (DialogObject.isEncryptedDialog(dialogId)) {
							val encryptedChat = getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))

							if (encryptedChat != null) {
								dialogId = encryptedChat.user_id
							}
						}

						if (sortingDialogFilter!!.includesDialog(accountInstance, dialogId, d)) {
							dialogsByFilter.add(d)
						}
					}
				}
			}
		}

		runCatching {
			Collections.sort(allDialogs, dialogComparator)
		}

		isLeftPromoChannel = true

		if (promoDialog != null && promoDialog!!.id < 0) {
			val chat = getChat(-promoDialog!!.id)

			if (chat != null && !chat.left) {
				isLeftPromoChannel = false
			}
		}

		run {
			var a = 0
			var n = allDialogs.size

			while (a < n) {
				val d = allDialogs[a]

				if (d is TL_dialog) {
					val messageObject = dialogMessage[d.id]

					if (messageObject != null && messageObject.messageOwner!!.date < dialogsLoadedTillDate) {
						a++
						continue
					}

					var canAddToForward = true

					if (!DialogObject.isEncryptedDialog(d.id)) {
						dialogsServerOnly.add(d)

						if (DialogObject.isChannel(d)) {
							val chat = getChat(-d.id)

							if (chat != null && (chat.creator || chat.megagroup && (chat.admin_rights != null && (chat.admin_rights.post_messages || chat.admin_rights.add_admins) || chat.default_banned_rights == null || !chat.default_banned_rights.invite_users) || !chat.megagroup && chat.admin_rights != null && chat.admin_rights.add_admins)) {
								if (chat.creator || chat.megagroup && chat.admin_rights != null || !chat.megagroup && chat.admin_rights != null) {
									if (chat.megagroup) {
										dialogsMyGroups.add(d)
									}
									else {
										dialogsMyChannels.add(d)
									}
								}
								else {
									dialogsCanAddUsers.add(d)
								}
							}

							canAddToForward = if (chat != null && chat.megagroup) {
								dialogsGroupsOnly.add(d)
								!chat.gigagroup || ChatObject.hasAdminRights(chat)
							}
							else {
								dialogsChannelsOnly.add(d)
								ChatObject.hasAdminRights(chat) && ChatObject.canPost(chat)
							}
						}
						else if (d.id < 0) {
							if (chatsDict != null) {
								val chat = chatsDict[-d.id]

								if (chat?.migrated_to != null) {
									allDialogs.removeAt(a)
									n--
									continue
								}
							}

							val chat = getChat(-d.id)

							if (chat != null && (chat.admin_rights != null && (chat.admin_rights.add_admins || chat.admin_rights.invite_users) || chat.creator)) {
								if (chat.creator) {
									dialogsMyGroups.add(d)
								}
								else {
									dialogsCanAddUsers.add(d)
								}
							}

							if (ChatObject.isChannel(chat)) {
								canAddToForward = ChatObject.hasAdminRights(chat) && ChatObject.canPost(chat)
							}

							dialogsGroupsOnly.add(d)
						}
						else if (d.id != selfId) {
							dialogsUsersOnly.add(d)

							if (d.id == BuildConfig.AI_BOT_ID || d.id == 333000L || d.id == BuildConfig.NOTIFICATIONS_BOT_ID || d.id == 42777L) {
								canAddToForward = false
							}

							if (!UserObject.isReplyUser(d.id) && d.id != BuildConfig.AI_BOT_ID && d.id != BuildConfig.SUPPORT_BOT_ID) {
								dialogsForBlock.add(d)
							}
						}
					}

					if (canAddToForward && d.folder_id == 0) {
						if (d.id == selfId) {
							dialogsForward.add(0, d)
							selfAdded = true
						}
						else {
							dialogsForward.add(d)
						}
					}
				}

				if ((d.unread_count != 0 || d.unread_mark) && !isDialogMuted(d.id)) {
					unreadUnmutedDialogs++
				}

				if (promoDialog != null && d.id == promoDialog!!.id && isLeftPromoChannel) {
					allDialogs.removeAt(a)
					n--
					continue
				}

				addDialogToItsFolder(-1, d)

				a++
			}
		}

		if (isLeftPromoChannel) {
			promoDialog?.let {
				allDialogs.add(0, it)
				addDialogToItsFolder(-2, it)
			}
		}

		if (!selfAdded) {
			val user = userConfig.getCurrentUser()

			if (user != null) {
				val dialog: Dialog = TL_dialog()
				dialog.id = user.id
				dialog.notify_settings = TL_peerNotifySettings()
				dialog.peer = TL_peerUser()
				dialog.peer.user_id = user.id

				dialogsForward.add(0, dialog)
			}
		}

		for (a in 0 until dialogsByFolder.size()) {
			val folderId = dialogsByFolder.keyAt(a)
			val dialogs = dialogsByFolder.valueAt(a)

			if (dialogs.isEmpty()) {
				dialogsByFolder.remove(folderId)
			}
		}
	}

	private fun addDialogToItsFolder(index: Int, dialog: Dialog) {
		val folderId = if (dialog is TL_dialogFolder) {
			0
		}
		else {
			dialog.folder_id
		}

		var dialogs = dialogsByFolder[folderId]

		if (dialogs == null) {
			dialogs = ArrayList()

			dialogsByFolder.put(folderId, dialogs)
		}

		if (index == -1) {
			dialogs.add(dialog)
		}
		else if (index == -2) {
			if (dialogs.isEmpty() || dialogs[0] !is TL_dialogFolder) {
				dialogs.add(0, dialog)
			}
			else {
				dialogs.add(1, dialog)
			}
		}
		else {
			dialogs.add(index, dialog)
		}
	}

	@JvmOverloads
	fun checkCanOpenChat(bundle: Bundle?, fragment: BaseFragment?, originalMessage: MessageObject? = null): Boolean {
		if (bundle == null || fragment == null) {
			return true
		}

		var user: User? = null
		var chat: Chat? = null
		val userId = bundle.getLong("user_id", 0)
		val chatId = bundle.getLong("chat_id", 0)
		val messageId = bundle.getInt("message_id", 0)

		if (userId != 0L) {
			user = getUser(userId)
		}
		else if (chatId != 0L) {
			chat = getChat(chatId)
		}

		if (user == null && chat == null) {
			return true
		}

		val reason = if (chat != null) {
			getRestrictionReason(chat.restriction_reason)
		}
		else {
			getRestrictionReason(user!!.restriction_reason)
		}

		if (reason != null) {
			showCantOpenAlert(fragment, reason)
			return false
		}

		if (messageId != 0 && originalMessage != null && chat != null && chat.access_hash == 0L) {
			val did = originalMessage.dialogId

			if (!DialogObject.isEncryptedDialog(did)) {
				val progressDialog = AlertDialog(fragment.parentActivity!!, 3)
				val req: TLObject

				if (did < 0) {
					chat = getChat(-did)
				}

				if (did > 0 || !ChatObject.isChannel(chat)) {
					val request = TL_messages_getMessages()
					request.id.add(originalMessage.id)

					req = request
				}
				else {
					chat = getChat(-did)

					val request = TL_channels_getMessages()
					request.channel = getInputChannel(chat)
					request.id.add(originalMessage.id)

					req = request
				}

				val reqId = connectionsManager.sendRequest(req) { response, _ ->
					if (response is messages_Messages) {
						AndroidUtilities.runOnUIThread {
							try {
								progressDialog.dismiss()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}

							putUsers(response.users, false)
							putChats(response.chats, false)

							messagesStorage.putUsersAndChats(response.users, response.chats, true, true)

							fragment.presentFragment(ChatActivity(bundle), true)
						}
					}
				}

				progressDialog.setOnCancelListener {
					connectionsManager.cancelRequest(reqId, true)
					fragment.visibleDialog = null
				}

				fragment.visibleDialog = progressDialog

				progressDialog.show()

				return false
			}
		}

		return true
	}

	fun openByUserName(username: String?, fragment: BaseFragment?, type: Int) {
		if (username == null || fragment == null) {
			return
		}

		val `object` = getUserOrChat(username)
		var user: User? = null
		var chat: Chat? = null

		if (`object` is User) {
			user = `object`

			if (user.min) {
				user = null
			}
		}
		else if (`object` is Chat) {
			chat = `object`

			if (chat.min) {
				chat = null
			}
		}

		if (user != null) {
			openChatOrProfileWith(user, null, fragment, type, false)
		}
		else if (chat != null) {
			openChatOrProfileWith(null, chat, fragment, 1, false)
		}
		else {
			val parentActivity = fragment.parentActivity ?: return
			var progressDialog: AlertDialog? = AlertDialog(parentActivity, 3)

			val req = TL_contacts_resolveUsername()
			req.username = username

			val reqId = connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					runCatching {
						progressDialog?.dismiss()
					}

					progressDialog = null

					fragment.visibleDialog = null

					if (response is TL_contacts_resolvedPeer) {
						putUsers(response.users, false)
						putChats(response.chats, false)

						messagesStorage.putUsersAndChats(response.users, response.chats, false, true)

						if (response.chats.isNotEmpty()) {
							openChatOrProfileWith(null, response.chats[0], fragment, 1, false)
						}
						else if (response.users.isNotEmpty()) {
							openChatOrProfileWith(response.users[0], null, fragment, type, false)
						}
					}
					else {
						try {
							BulletinFactory.of(fragment).createErrorBulletin(parentActivity.getString(R.string.NoUsernameFound)).show()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
			}

			AndroidUtilities.runOnUIThread({
				val dialog = progressDialog ?: return@runOnUIThread

				dialog.setOnCancelListener {
					connectionsManager.cancelRequest(reqId, true)
				}

				fragment.showDialog(dialog)
			}, 500)
		}
	}

	fun ensureMessagesLoaded(dialogId: Long, messageId: Int, callback: MessagesLoadedCallback?) {
		@Suppress("NAME_SHADOWING") var messageId = messageId
		val sharedPreferences = getNotificationsSettings(currentAccount)

		if (messageId == 0) {
			messageId = sharedPreferences.getInt("diditem$dialogId", 0)
		}

		val finalMessageId = messageId
		val classGuid = ConnectionsManager.generateClassGuid()

		val chatId = if (DialogObject.isChatDialog(dialogId)) {
			-dialogId
		}
		else {
			0
		}

		val currentChat: Chat?

		if (chatId != 0L) {
			currentChat = messagesController.getChat(chatId)

			if (currentChat == null) {
				val messagesStorage = messagesStorage

				messagesStorage.storageQueue.postRunnable {
					val chat = messagesStorage.getChat(chatId)

					AndroidUtilities.runOnUIThread {
						if (chat != null) {
							messagesController.putChat(chat, true)
							ensureMessagesLoaded(dialogId, finalMessageId, callback)
						}
						else {
							callback?.onError()
						}
					}
				}
				return
			}
		}

		val count = if (AndroidUtilities.isTablet()) 30 else 20

		val delegate: NotificationCenterDelegate = object : NotificationCenterDelegate {
			override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
				if (id == NotificationCenter.messagesDidLoadWithoutProcess && args[0] as Int == classGuid) {
					val size = args[1] as Int
					val isCache = args[2] as Boolean
					val isEnd = args[3] as Boolean
					val lastMessageId = args[4] as Int

					if (size < count / 2 && !isEnd && isCache) {
						if (finalMessageId != 0) {
							loadMessagesInternal(dialogId, 0, false, count, finalMessageId, 0, false, 0, classGuid, 3, lastMessageId, 0, 0, -1, 0, 0, 0, false, 0, loadDialog = true, processMessages = false)
						}
						else {
							loadMessagesInternal(dialogId, 0, false, count, finalMessageId, 0, false, 0, classGuid, 2, lastMessageId, 0, 0, -1, 0, 0, 0, false, 0, loadDialog = true, processMessages = false)
						}
					}
					else {
						notificationCenter.removeObserver(this, NotificationCenter.messagesDidLoadWithoutProcess)
						notificationCenter.removeObserver(this, NotificationCenter.loadingMessagesFailed)

						callback?.onMessagesLoaded(isCache)
					}
				}
				else if (id == NotificationCenter.loadingMessagesFailed && args[0] as Int == classGuid) {
					notificationCenter.removeObserver(this, NotificationCenter.messagesDidLoadWithoutProcess)
					notificationCenter.removeObserver(this, NotificationCenter.loadingMessagesFailed)

					callback?.onError()
				}
			}
		}

		notificationCenter.addObserver(delegate, NotificationCenter.messagesDidLoadWithoutProcess)
		notificationCenter.addObserver(delegate, NotificationCenter.loadingMessagesFailed)

		if (messageId != 0) {
			loadMessagesInternal(dialogId, 0, true, count, finalMessageId, 0, true, 0, classGuid, 3, 0, 0, 0, -1, 0, 0, 0, false, 0, loadDialog = true, processMessages = false)
		}
		else {
			loadMessagesInternal(dialogId, 0, true, count, finalMessageId, 0, true, 0, classGuid, 2, 0, 0, 0, -1, 0, 0, 0, false, 0, loadDialog = true, processMessages = false)
		}
	}

	fun getChatPendingRequestsOnClosed(chatId: Long): Int {
		return mainPreferences.getInt("chatPendingRequests$chatId", 0)
	}

	fun setChatPendingRequestsOnClose(chatId: Long, count: Int) {
		mainPreferences.edit().putInt("chatPendingRequests$chatId", count).commit()
	}

	fun markSponsoredAsRead(dialogId: Long, `object`: MessageObject?) {
		// sponsoredMessages.remove(dialogId)
	}

	fun deleteMessagesRange(dialogId: Long, channelId: Long, minDate: Int, maxDate: Int, forAll: Boolean, callback: Runnable) {
		val req = TL_messages_deleteHistory()
		req.peer = getInputPeer(dialogId)
		req.flags = 1 shl 2 or (1 shl 3)
		req.min_date = minDate
		req.max_date = maxDate
		req.revoke = forAll

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_messages_affectedHistory) {
				processNewDifferenceParams(-1, response.pts, -1, response.pts_count)

				messagesStorage.storageQueue.postRunnable {
					val dbMessages = messagesStorage.getCachedMessagesInRange(dialogId, minDate, maxDate)

					messagesStorage.markMessagesAsDeleted(dialogId, dbMessages, false, true, false)
					messagesStorage.updateDialogsWithDeletedMessages(dialogId, 0, dbMessages, null, false)

					AndroidUtilities.runOnUIThread {
						notificationCenter.postNotificationName(NotificationCenter.messagesDeleted, dbMessages, channelId, false)
						callback.run()
					}
				}
			}
			else {
				AndroidUtilities.runOnUIThread(callback)
			}
		}
	}

	fun setChatReactions(chatId: Long, type: Int, reactions: List<String>) {
		val req = TL_messages_setChatAvailableReactions()
		req.peer = getInputPeer(-chatId)

		when (type) {
			ChatReactionsEditActivity.SELECT_TYPE_NONE -> {
				req.available_reactions = TL_chatReactionsNone()
			}

			ChatReactionsEditActivity.SELECT_TYPE_ALL -> {
				req.available_reactions = TL_chatReactionsAll()
			}

			else -> {
				val someReactions = TL_chatReactionsSome()
				req.available_reactions = someReactions

				for (i in reactions.indices) {
					val emojiReaction = TL_reactionEmoji()
					emojiReaction.emoticon = reactions[i]

					someReactions.reactions.add(emojiReaction)
				}
			}
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response != null) {
				processUpdates(response as? Updates, false)

				val full = getChatFull(chatId)

				if (full != null) {
					if (full is TL_chatFull) {
						full.flags = full.flags or 262144
					}

					if (full is TL_channelFull) {
						full.flags = full.flags or 1073741824
					}

					full.available_reactions = req.available_reactions

					messagesStorage.updateChatInfo(full, false)
				}

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.chatAvailableReactionsUpdated, chatId)
				}
			}
		}
	}

	fun checkIsInChat(chat: Chat?, user: User?, callback: IsInChatCheckedCallback?) {
		if (chat == null || user == null) {
			callback?.run(false, null, null)
			return
		}

		if (chat.megagroup || ChatObject.isChannel(chat)) {
			val req = TL_channels_getParticipant()
			req.channel = getInputChannel(chat.id)
			req.participant = getInputPeer(user)

			connectionsManager.sendRequest(req) { response, error ->
				if (callback != null) {
					val participant = (response as? TL_channels_channelParticipant)?.participant
					callback.run(error == null && participant != null && !participant.left, participant?.admin_rights, participant?.rank)
				}
			}
		}
		else {
			val chatFull = getChatFull(chat.id)

			if (chatFull != null) {
				var userParticipant: ChatParticipant? = null

				if (chatFull.participants?.participants != null) {
					val count = chatFull.participants.participants.size

					for (i in 0 until count) {
						val participant = chatFull.participants.participants[i]

						if (participant != null && participant.user_id == user.id) {
							userParticipant = participant
							break
						}
					}
				}

				callback?.run(userParticipant != null, if (chatFull.participants != null && chatFull.participants.admin_id == user.id) emptyAdminRights(true) else null, null)
			}
			else {
				callback?.run(false, null, null)
			}
		}
	}

	private fun applySoundSettings(settings: NotificationSound?, editor: SharedPreferences.Editor, dialogId: Long, globalType: Int, serverUpdate: Boolean) {
		if (settings == null) {
			return
		}

		val soundPref: String
		val soundPathPref: String
		val soundDocPref: String

		if (dialogId != 0L) {
			soundPref = "sound_$dialogId"
			soundPathPref = "sound_path_$dialogId"
			soundDocPref = "sound_document_id_$dialogId"
		}
		else {
			when (globalType) {
				NotificationsController.TYPE_GROUP -> {
					soundPref = "GroupSound"
					soundDocPref = "GroupSoundDocId"
					soundPathPref = "GroupSoundPath"
				}

				NotificationsController.TYPE_PRIVATE -> {
					soundPref = "GlobalSound"
					soundDocPref = "GlobalSoundDocId"
					soundPathPref = "GlobalSoundPath"
				}

				else -> {
					soundPref = "ChannelSound"
					soundDocPref = "ChannelSoundDocId"
					soundPathPref = "ChannelSoundPath"
				}
			}
		}

		when (settings) {
			is TL_notificationSoundDefault -> {
				editor.putString(soundPref, "Default")
				editor.putString(soundPathPref, "Default")
				editor.remove(soundDocPref)
			}

			is TL_notificationSoundNone -> {
				editor.putString(soundPref, "NoSound")
				editor.putString(soundPathPref, "NoSound")
				editor.remove(soundDocPref)
			}

			is TL_notificationSoundLocal -> {
				editor.putString(soundPref, settings.title)
				editor.putString(soundPathPref, settings.data)
				editor.remove(soundDocPref)
			}

			is TL_notificationSoundRingtone -> {
				editor.putLong(soundDocPref, settings.id)

				mediaDataController.checkRingtones()

				if (serverUpdate && dialogId != 0L) {
					editor.putBoolean("custom_$dialogId", true)
				}

				mediaDataController.ringtoneDataStore.getDocument(settings.id)
			}
		}
	}

	fun updateEmojiStatusUntilUpdate(userId: Long, status: EmojiStatus?) {
		if (status is TL_emojiStatusUntil) {
			emojiStatusUntilValues.put(userId, status.until)
		}
		else {
			if (!emojiStatusUntilValues.containsKey(userId)) {
				return
			}

			emojiStatusUntilValues.remove(userId)
		}

		updateEmojiStatusUntil()
	}

	fun updateEmojiStatusUntil() {
		val now = (System.currentTimeMillis() / 1000L).toInt()
		var timeout: Long? = null
		var i = 0

		while (i < emojiStatusUntilValues.size()) {
			val until = emojiStatusUntilValues.valueAt(i)

			if (until > now) {
				timeout = min(timeout ?: Long.MAX_VALUE, (until - now).toLong())
			}
			else {
				emojiStatusUntilValues.removeAt(i--)
			}

			++i
		}

		if (timeout != null) {
			timeout += 2

			if (now + timeout != recentEmojiStatusUpdateRunnableTime + recentEmojiStatusUpdateRunnableTimeout) {
				AndroidUtilities.cancelRunOnUIThread(recentEmojiStatusUpdateRunnable)

				recentEmojiStatusUpdateRunnableTime = now.toLong()

				recentEmojiStatusUpdateRunnableTimeout = timeout

				AndroidUtilities.runOnUIThread(Runnable {
					notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_EMOJI_STATUS)
					updateEmojiStatusUntil()
				}.also {
					recentEmojiStatusUpdateRunnable = it
				}, timeout * 1000)
			}
		}
		else if (recentEmojiStatusUpdateRunnable != null) {
			recentEmojiStatusUpdateRunnableTime = -1
			recentEmojiStatusUpdateRunnableTimeout = -1

			AndroidUtilities.cancelRunOnUIThread(recentEmojiStatusUpdateRunnable)
		}
	}

	fun interface ErrorDelegate {
		// if returns true, a delegate allows to show default alert
		fun run(error: TL_error?): Boolean
	}

	fun interface IsInChatCheckedCallback {
		fun run(isInChat: Boolean, currentAdminRights: TL_chatAdminRights?, rank: String?)
	}

	interface MessagesLoadedCallback {
		fun onMessagesLoaded(fromCache: Boolean)
		fun onError()
	}

	fun interface NewMessageCallback {
		fun onMessageReceived(message: Message?): Boolean
	}

	class FaqSearchResult(var title: String, var path: Array<String>?, var url: String) {
		var num = 0

		override fun equals(other: Any?): Boolean {
			if (other !is FaqSearchResult) {
				return false
			}

			return title == other.title
		}

		override fun toString(): String {
			val data = SerializedData()
			data.writeInt32(num)
			data.writeInt32(0)
			data.writeString(title)
			data.writeInt32(path?.size ?: 0)

			path?.forEach {
				data.writeString(it)
			}

			data.writeString(url)

			return Utilities.bytesToHex(data.toByteArray())
		}

		override fun hashCode(): Int {
			var result = title.hashCode()
			result = 31 * result + (path?.contentHashCode() ?: 0)
			result = 31 * result + url.hashCode()
			result = 31 * result + num
			return result
		}
	}

	class EmojiSound {
		@JvmField
		var id: Long

		@JvmField
		var accessHash: Long

		@JvmField
		var fileReference: ByteArray

		constructor(i: Long, ah: Long, fr: String) {
			id = i
			accessHash = ah
			fileReference = Base64.decode(fr, Base64.URL_SAFE)
		}

		constructor(i: Long, ah: Long, fr: ByteArray) {
			id = i
			accessHash = ah
			fileReference = fr
		}

		override fun equals(other: Any?): Boolean {
			if (other !is EmojiSound) {
				return false
			}

			return id == other.id && accessHash == other.accessHash && fileReference.contentEquals(other.fileReference)
		}

		override fun hashCode(): Int {
			var result = id.hashCode()
			result = 31 * result + accessHash.hashCode()
			result = 31 * result + fileReference.contentHashCode()
			return result
		}
	}

	class DiceFrameSuccess(var frame: Int, var num: Int) {
		override fun equals(other: Any?): Boolean {
			if (other !is DiceFrameSuccess) {
				return false
			}

			return frame == other.frame && num == other.num
		}

		override fun hashCode(): Int {
			var result = frame
			result = 31 * result + num
			return result
		}
	}

	private class UserActionUpdatesSeq : Updates()
	private class UserActionUpdatesPts : Updates()

	private class ReadTask {
		var dialogId: Long = 0
		var replyId: Long = 0
		var maxId = 0
		var maxDate = 0
		var sendRequestTime: Long = 0
	}

	class PrintingUser {
		var lastTime: Long = 0
		var userId: Long = 0
		var action: SendMessageAction? = null
	}

	class DialogFilter {
		@JvmField
		var id = 0

		@JvmField
		var name: String? = null

		@JvmField
		var unreadCount = 0

		@JvmField
		@Volatile
		var pendingUnreadCount = 0

		@JvmField
		var order = 0

		@JvmField
		var flags = 0

		@JvmField
		var alwaysShow = ArrayList<Long>()

		@JvmField
		var neverShow = ArrayList<Long>()

		@JvmField
		var pinnedDialogs = LongSparseIntArray()

		@JvmField
		var dialogs = ArrayList<Dialog>()
		var localId = dialogFilterPointer++

		@JvmField
		var locked = false

		fun includesDialog(accountInstance: AccountInstance, dialogId: Long): Boolean {
			val messagesController = accountInstance.messagesController
			val dialog = messagesController.dialogs_dict[dialogId] ?: return false
			return includesDialog(accountInstance, dialogId, dialog)
		}

		fun includesDialog(accountInstance: AccountInstance, dialogId: Long, d: Dialog): Boolean {
			if (neverShow.contains(dialogId)) {
				return false
			}

			if (alwaysShow.contains(dialogId)) {
				return true
			}

			if (d.folder_id != 0 && flags and DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED != 0) {
				return false
			}

			val messagesController = accountInstance.messagesController
			val contactsController = accountInstance.contactsController

			if (flags and DIALOG_FILTER_FLAG_EXCLUDE_MUTED != 0 && messagesController.isDialogMuted(d.id) && d.unread_mentions_count == 0 || flags and DIALOG_FILTER_FLAG_EXCLUDE_READ != 0 && d.unread_count == 0 && !d.unread_mark && d.unread_mentions_count == 0) {
				return false
			}

			if (dialogId > 0) {
				val user = messagesController.getUser(dialogId)

				if (user != null) {
					return if (!user.bot) {
						if (user.self || user.contact || contactsController.isContact(dialogId)) {
							flags and DIALOG_FILTER_FLAG_CONTACTS != 0
						}
						else {
							flags and DIALOG_FILTER_FLAG_NON_CONTACTS != 0
						}
					}
					else {
						flags and DIALOG_FILTER_FLAG_BOTS != 0
					}
				}
			}
			else if (dialogId < 0) {
				val chat = messagesController.getChat(-dialogId)

				if (chat != null) {
					return if (ChatObject.isChannel(chat) && !chat.megagroup) {
						flags and DIALOG_FILTER_FLAG_CHANNELS != 0
					}
					else {
						flags and DIALOG_FILTER_FLAG_GROUPS != 0
					}
				}
			}

			return false
		}

		fun alwaysShow(currentAccount: Int, dialog: Dialog?): Boolean {
			if (dialog == null) {
				return false
			}

			var dialogId = dialog.id

			if (DialogObject.isEncryptedDialog(dialog.id)) {
				val encryptedChat = getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))

				if (encryptedChat != null) {
					dialogId = encryptedChat.user_id
				}
			}

			return alwaysShow.contains(dialogId)
		}

		val isDefault: Boolean
			get() = id == 0

		companion object {
			private var dialogFilterPointer = 10
		}
	}

	private class SponsoredMessagesInfo {
		var messages: ArrayList<MessageObject>? = null
		var loadTime: Long = 0
		var loading = false
	}

	private class SendAsPeersInfo {
		var sendAsPeers: TL_channels_sendAsPeers? = null
		var loadTime: Long = 0
		var loading = false
	}

	suspend fun loadChat(chatId: Long, classGuid: Int, force: Boolean): Chat? {
		return suspendCoroutine { continuation ->
			runBlocking(mainScope.coroutineContext) {
				var observer: NotificationCenterDelegate? = null

				observer = NotificationCenterDelegate { id, _, args ->
					if (id == NotificationCenter.chatInfoDidLoad) {
						observer?.let {
							notificationCenter.removeObserver(it, NotificationCenter.chatInfoDidLoad)
						}

						val chatFull = args[0] as ChatFull
						var chat: Chat? = null

						if (chatFull.id == chatId) {
							chat = getChat(chatId)
						}

						continuation.resume(chat)
					}
				}

				notificationCenter.addObserver(observer, NotificationCenter.chatInfoDidLoad)
			}

			loadFullChat(chatId, classGuid, force)
		}
	}

	suspend fun loadUser(userId: Long, classGuid: Int, force: Boolean): User? {
		return suspendCoroutine { continuation ->
			runBlocking(mainScope.coroutineContext) {
				var observer: NotificationCenterDelegate? = null

				observer = NotificationCenterDelegate { id, _, args ->
					if (id == NotificationCenter.userInfoDidLoad) {
						observer?.let {
							notificationCenter.removeObserver(it, NotificationCenter.userInfoDidLoad)
						}

						val uid = args[0] as Long
						var resultingUser: User? = null

						if (uid == userId) {
							resultingUser = (args[1] as? UserFull)?.user
						}

						continuation.resume(resultingUser)
					}
				}

				notificationCenter.addObserver(observer, NotificationCenter.userInfoDidLoad)
			}

			loadFullUser(TL_user().apply { id = userId }, classGuid, force)
		}
	}

	companion object {
		private val lockObjects = Array(UserConfig.MAX_ACCOUNT_COUNT) { Any() }

		@JvmField
		var UPDATE_MASK_NAME = 1

		@JvmField
		var UPDATE_MASK_AVATAR = 2

		@JvmField
		var UPDATE_MASK_STATUS = 4

		@JvmField
		var UPDATE_MASK_CHAT_AVATAR = 8

		@JvmField
		var UPDATE_MASK_CHAT_NAME = 16

		@JvmField
		var UPDATE_MASK_CHAT_MEMBERS = 32

		@JvmField
		var UPDATE_MASK_USER_PRINT = 64
		var UPDATE_MASK_USER_PHONE = 128

		@JvmField
		var UPDATE_MASK_READ_DIALOG_MESSAGE = 256

		var UPDATE_MASK_SELECT_DIALOG = 512
		var UPDATE_MASK_PHONE = 1024

		@JvmField
		var UPDATE_MASK_NEW_MESSAGE = 2048

		var UPDATE_MASK_SEND_STATE = 4096
		var UPDATE_MASK_CHAT = 8192

		//public static int UPDATE_MASK_CHAT_ADMINS = 16384;
		var UPDATE_MASK_MESSAGE_TEXT = 32768
		var UPDATE_MASK_CHECK = 65536
		var UPDATE_MASK_REORDER = 131072
		var UPDATE_MASK_EMOJI_INTERACTIONS = 262144

		@JvmField
		var UPDATE_MASK_EMOJI_STATUS = 524288

		@JvmField
		var UPDATE_MASK_ALL = UPDATE_MASK_AVATAR or UPDATE_MASK_STATUS or UPDATE_MASK_NAME or UPDATE_MASK_CHAT_AVATAR or UPDATE_MASK_CHAT_NAME or UPDATE_MASK_CHAT_MEMBERS or UPDATE_MASK_USER_PRINT or UPDATE_MASK_USER_PHONE or UPDATE_MASK_READ_DIALOG_MESSAGE or UPDATE_MASK_PHONE

		var PROMO_TYPE_PROXY = 0
		var PROMO_TYPE_PSA = 1
		var PROMO_TYPE_OTHER = 2

		@JvmField
		var DIALOG_FILTER_FLAG_CONTACTS = 0x00000001

		@JvmField
		var DIALOG_FILTER_FLAG_NON_CONTACTS = 0x00000002

		@JvmField
		var DIALOG_FILTER_FLAG_GROUPS = 0x00000004

		@JvmField
		var DIALOG_FILTER_FLAG_CHANNELS = 0x00000008

		@JvmField
		var DIALOG_FILTER_FLAG_BOTS = 0x00000010

		@JvmField
		var DIALOG_FILTER_FLAG_EXCLUDE_MUTED = 0x00000020

		@JvmField
		var DIALOG_FILTER_FLAG_EXCLUDE_READ = 0x00000040

		@JvmField
		var DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED = 0x00000080

		@JvmField
		var DIALOG_FILTER_FLAG_ONLY_ARCHIVED = 0x00000100

		@JvmField
		var DIALOG_FILTER_FLAG_ALL_CHATS = DIALOG_FILTER_FLAG_CONTACTS or DIALOG_FILTER_FLAG_NON_CONTACTS or DIALOG_FILTER_FLAG_GROUPS or DIALOG_FILTER_FLAG_CHANNELS or DIALOG_FILTER_FLAG_BOTS

		@Volatile
		private var lastThemeCheckTime: Long = 0

		@Volatile
		private var lastPasswordCheckTime: Long = 0

		private val Instance = arrayOfNulls<MessagesController>(UserConfig.MAX_ACCOUNT_COUNT)
		const val MAP_PROVIDER_UNDEFINED = -1
		const val MAP_PROVIDER_NONE = 0
		const val MAP_PROVIDER_YANDEX_NO_ARGS = 1
		const val MAP_PROVIDER_ELLO = 2
		const val MAP_PROVIDER_YANDEX_WITH_ARGS = 3
		const val MAP_PROVIDER_GOOGLE = 4

		private const val DIALOGS_LOAD_TYPE_CACHE = 1
		private const val DIALOGS_LOAD_TYPE_CHANNEL = 2
		private const val DIALOGS_LOAD_TYPE_UNKNOWN = 3

		@JvmStatic
		fun getInstance(num: Int): MessagesController {
			var localInstance = Instance[num]

			if (localInstance == null) {
				synchronized(lockObjects[num]) {
					localInstance = Instance[num]

					if (localInstance == null) {
						localInstance = MessagesController(num)
						Instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		@JvmStatic
		fun getNotificationsSettings(account: Int): SharedPreferences {
			return getInstance(account).notificationsPreferences
		}

		@JvmStatic
		fun getGlobalNotificationsSettings(): SharedPreferences {
			return getInstance(0).notificationsPreferences
		}

		@JvmStatic
		fun getMainSettings(account: Int): SharedPreferences {
			return getInstance(account).mainPreferences
		}

		@JvmStatic
		fun getGlobalMainSettings(): SharedPreferences {
			return getInstance(0).mainPreferences
		}

		@JvmStatic
		fun getEmojiSettings(account: Int): SharedPreferences {
			return getInstance(account).emojiPreferences
		}

		@JvmStatic
		fun getGlobalEmojiSettings(): SharedPreferences {
			return getInstance(0).emojiPreferences
		}

		@JvmStatic
		fun getInputChannel(chat: Chat?): InputChannel {
			return if (chat is TL_channel || chat is TL_channelForbidden) {
				val inputChat: InputChannel = TL_inputChannel()
				inputChat.channel_id = chat.id
				inputChat.access_hash = chat.access_hash
				inputChat
			}
			else {
				TL_inputChannelEmpty()
			}
		}

		fun getInputChannel(peer: InputPeer): InputChannel {
			val inputChat = TL_inputChannel()
			inputChat.channel_id = peer.channel_id
			inputChat.access_hash = peer.access_hash
			return inputChat
		}

		@JvmStatic
		fun getInputPeer(chat: Chat?): InputPeer {
			val inputPeer: InputPeer

			if (ChatObject.isChannel(chat)) {
				inputPeer = TL_inputPeerChannel()
				inputPeer.channel_id = chat.id
				inputPeer.access_hash = chat.access_hash
			}
			else {
				inputPeer = TL_inputPeerChat()
				inputPeer.chat_id = chat!!.id
			}

			return inputPeer
		}

		@JvmStatic
		fun getInputPeer(user: User): InputPeer {
			val inputPeer: InputPeer = TL_inputPeerUser()
			inputPeer.user_id = user.id
			inputPeer.access_hash = user.access_hash
			return inputPeer
		}

		val savedLogOutTokens: ArrayList<TL_auth_loggedOut>?
			get() {
				val preferences = ApplicationLoader.applicationContext.getSharedPreferences("saved_tokens", Context.MODE_PRIVATE)
				val count = preferences.getInt("count", 0)

				if (count == 0) {
					return null
				}

				val tokens = ArrayList<TL_auth_loggedOut>()

				for (i in 0 until count) {
					val value = preferences.getString("log_out_token_$i", "")
					val serializedData = SerializedData(Utilities.hexToBytes(value))
					val token = TL_auth_loggedOut.TLdeserialize(serializedData, serializedData.readInt32(true), true)

					if (token != null) {
						tokens.add(token)
					}
				}

				return tokens
			}

		fun saveLogOutTokens(tokens: ArrayList<TL_auth_loggedOut>) {
			val preferences = ApplicationLoader.applicationContext.getSharedPreferences("saved_tokens", Context.MODE_PRIVATE)
			preferences.edit().clear().commit()

			val activeTokens = ArrayList<TL_auth_loggedOut>()

			for (i in 0 until min(20, tokens.size)) {
				activeTokens.add(tokens[i])
			}

			if (activeTokens.size > 0) {
				val editor = preferences.edit()
				editor.putInt("count", activeTokens.size)

				for (i in activeTokens.indices) {
					val data = SerializedData(activeTokens[i].objectSize)
					activeTokens[i].serializeToStream(data)
					editor.putString("log_out_token_$i", Utilities.bytesToHex(data.toByteArray()))
				}

				editor.commit()
			}
		}

		@JvmStatic
		fun isSupportUser(user: User?): Boolean {
			return user != null && (user.support || user.id == BuildConfig.NOTIFICATIONS_BOT_ID || user.id == 333000L || user.id == 4240000L || user.id == 4244000L || user.id == 4245000L || user.id == 4246000L || user.id == 410000L || user.id == 420000L || user.id == 431000L || user.id == 431415000L || user.id == 434000L || user.id == 4243000L || user.id == 439000L || user.id == 449000L || user.id == 450000L || user.id == 452000L || user.id == 454000L || user.id == 4254000L || user.id == 455000L || user.id == 460000L || user.id == 470000L || user.id == 479000L || user.id == 796000L || user.id == 482000L || user.id == 490000L || user.id == 496000L || user.id == 497000L || user.id == 498000L || user.id == 4298000L)
		}

		private fun getUpdatePts(update: Update): Int {
			return when (update) {
				is TL_updateDeleteMessages -> update.pts
				is TL_updateNewChannelMessage -> update.pts
				is TL_updateReadHistoryOutbox -> update.pts
				is TL_updateNewMessage -> update.pts
				is TL_updateEditMessage -> update.pts
				is TL_updateWebPage -> update.pts
				is TL_updateReadHistoryInbox -> update.pts
				is TL_updateChannelWebPage -> update.pts
				is TL_updateDeleteChannelMessages -> update.pts
				is TL_updateEditChannelMessage -> update.pts
				is TL_updateReadMessagesContents -> update.pts
				is TL_updateChannelTooLong -> update.pts
				is TL_updateFolderPeers -> update.pts
				is TL_updatePinnedChannelMessages -> update.pts
				is TL_updatePinnedMessages -> update.pts
				else -> 0
			}
		}

		private fun getUpdatePtsCount(update: Update): Int {
			return when (update) {
				is TL_updateDeleteMessages -> update.pts_count
				is TL_updateNewChannelMessage -> update.pts_count
				is TL_updateReadHistoryOutbox -> update.pts_count
				is TL_updateNewMessage -> update.pts_count
				is TL_updateEditMessage -> update.pts_count
				is TL_updateWebPage -> update.pts_count
				is TL_updateReadHistoryInbox -> update.pts_count
				is TL_updateChannelWebPage -> update.pts_count
				is TL_updateDeleteChannelMessages -> update.pts_count
				is TL_updateEditChannelMessage -> update.pts_count
				is TL_updateReadMessagesContents -> update.pts_count
				is TL_updateFolderPeers -> update.pts_count
				is TL_updatePinnedChannelMessages -> update.pts_count
				is TL_updatePinnedMessages -> update.pts_count
				else -> 0
			}
		}

		private fun getUpdateQts(update: Update): Int {
			return if (update is TL_updateNewEncryptedMessage) {
				update.qts
			}
			else {
				0
			}
		}

		fun getUpdateChannelId(update: Update): Long {
			return when (update) {
				is TL_updateNewChannelMessage -> update.message.peer_id!!.channel_id
				is TL_updateEditChannelMessage -> update.message.peer_id!!.channel_id
				is TL_updateReadChannelOutbox -> update.channel_id
				is TL_updateChannelMessageViews -> update.channel_id
				is TL_updateChannelMessageForwards -> update.channel_id
				is TL_updateChannelTooLong -> update.channel_id
				is TL_updateChannelReadMessagesContents -> update.channel_id
				is TL_updateChannelAvailableMessages -> update.channel_id
				is TL_updateChannel -> update.channel_id
				is TL_updateChannelWebPage -> update.channel_id
				is TL_updateDeleteChannelMessages -> update.channel_id
				is TL_updateReadChannelInbox -> update.channel_id
				is TL_updateReadChannelDiscussionInbox -> update.channel_id
				is TL_updateReadChannelDiscussionOutbox -> update.channel_id
				is TL_updateChannelUserTyping -> update.channel_id
				is TL_updatePinnedChannelMessages -> update.channel_id
				else -> 0
			}
		}

		@JvmStatic
		fun getRestrictionReason(reasons: ArrayList<TL_restrictionReason>?): String? {
			if (reasons.isNullOrEmpty()) {
				return null
			}

			for (reason in reasons) {
				if ("all" == reason.platform || "android" == reason.platform) {
					return reason.text
				}
			}

			return null
		}

		private fun showCantOpenAlert(fragment: BaseFragment?, reason: String) {
			if (fragment == null) {
				return
			}

			val parentActivity = fragment.parentActivity ?: return

			val builder = AlertDialog.Builder(parentActivity)
			builder.setTitle(parentActivity.getString(R.string.AppName))
			builder.setPositiveButton(parentActivity.getString(R.string.OK), null)
			builder.setMessage(reason)

			fragment.showDialog(builder.create())
		}

		@JvmStatic
		fun openChatOrProfileWith(user: User?, chat: Chat?, fragment: BaseFragment?, type: Int, closeLast: Boolean) {
			@Suppress("NAME_SHADOWING") var type = type
			@Suppress("NAME_SHADOWING") var closeLast = closeLast

			if ((user == null && chat == null) || fragment == null) {
				return
			}

			val reason: String?

			if (chat != null) {
				reason = getRestrictionReason(chat.restriction_reason)
			}
			else if (user != null) {
				reason = getRestrictionReason(user.restriction_reason)

				if (type != 3 && user.bot) {
					type = 1
					closeLast = true
				}
			}
			else {
				return
			}

			if (reason != null) {
				showCantOpenAlert(fragment, reason)
			}
			else {
				val args = Bundle()

				if (chat != null) {
					args.putLong("chat_id", chat.id)
				}
				else {
					args.putLong("user_id", user!!.id)
				}

				when (type) {
					0 -> fragment.presentFragment(ProfileActivity(args))
					2 -> fragment.presentFragment(ChatActivity(args), true, true)
					else -> fragment.presentFragment(ChatActivity(args), closeLast)
				}
			}
		}
	}
}
