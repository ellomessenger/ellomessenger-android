/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import android.os.SystemClock
import android.util.SparseArray
import androidx.annotation.UiThread
import kotlin.math.max
import kotlin.math.min

class NotificationCenter private constructor(private val currentAccount: Int) {
	private val addAfterBroadcast = SparseArray<ArrayList<NotificationCenterDelegate>>()
	private val allowedNotifications = mutableMapOf<Int, AllowedNotifications>()
	private val delayedPosts = ArrayList<DelayedPost>(10)
	private val delayedPostsTmp = ArrayList<DelayedPost>(10)
	private val delayedRunnables = ArrayList<Runnable>(10)
	private val delayedRunnablesTmp = ArrayList<Runnable>(10)
	private val heavyOperationsCounter = mutableSetOf<Int>()
	private val observers = SparseArray<ArrayList<NotificationCenterDelegate>>()
	private val postponeCallbackList = ArrayList<PostponeNotificationCallback>(10)
	private val removeAfterBroadcast = SparseArray<ArrayList<NotificationCenterDelegate>>()
	private var animationInProgressCount = 0
	private var animationInProgressPointer = 1
	private var broadcasting = 0
	private var checkForExpiredNotifications: Runnable? = null

	fun interface NotificationCenterDelegate {
		fun didReceivedNotification(id: Int, account: Int, vararg args: Any?)
	}

	private class DelayedPost(val id: Int, vararg val args: Any?)

	var currentHeavyOperationFlags = 0
		private set

	fun setAnimationInProgress(oldIndex: Int, allowedNotifications: IntArray?): Int {
		return setAnimationInProgress(oldIndex, allowedNotifications, true)
	}

	fun setAnimationInProgress(oldIndex: Int, allowedNotifications: IntArray?, stopHeavyOperations: Boolean): Int {
		onAnimationFinish(oldIndex)

		if (heavyOperationsCounter.isEmpty() && stopHeavyOperations) {
			globalInstance.postNotificationName(stopAllHeavyOperations, 512)
		}

		animationInProgressCount++
		animationInProgressPointer++

		if (stopHeavyOperations) {
			heavyOperationsCounter.add(animationInProgressPointer)
		}

		val notifications = AllowedNotifications()
		notifications.allowedIds = allowedNotifications

		this.allowedNotifications[animationInProgressPointer] = notifications

		if (checkForExpiredNotifications == null) {
			AndroidUtilities.runOnUIThread(Runnable {
				checkForExpiredNotifications()
			}.also {
				checkForExpiredNotifications = it
			}, EXPIRE_NOTIFICATIONS_TIME)
		}

		return animationInProgressPointer
	}

	private fun checkForExpiredNotifications() {
		checkForExpiredNotifications = null

		if (allowedNotifications.isEmpty()) {
			return
		}

		var minTime = Long.MAX_VALUE
		val currentTime = SystemClock.elapsedRealtime()
		var expiredIndices: ArrayList<Int>? = null

		for ((key, allowedNotification) in allowedNotifications) {
			if (currentTime - allowedNotification.time > 1000) {
				if (expiredIndices == null) {
					expiredIndices = ArrayList()
				}

				expiredIndices.add(key)
			}
			else {
				minTime = min(allowedNotification.time, minTime)
			}
		}

		if (expiredIndices != null) {
			for (i in expiredIndices.indices) {
				onAnimationFinish(expiredIndices[i])
			}
		}

		if (minTime != Long.MAX_VALUE) {
			val time = EXPIRE_NOTIFICATIONS_TIME - (currentTime - minTime)

			AndroidUtilities.runOnUIThread({
				checkForExpiredNotifications = Runnable {
					checkForExpiredNotifications()
				}
			}, max(17, time))
		}
	}

	fun updateAllowedNotifications(transitionAnimationIndex: Int, allowedNotifications: IntArray?) {
		val notifications = this.allowedNotifications[transitionAnimationIndex]

		if (notifications != null) {
			notifications.allowedIds = allowedNotifications
		}
	}

	fun onAnimationFinish(index: Int) {
		val allowed = allowedNotifications.remove(index)

		if (allowed != null) {
			animationInProgressCount--

			if (heavyOperationsCounter.isNotEmpty()) {
				heavyOperationsCounter.remove(index)

				if (heavyOperationsCounter.isEmpty()) {
					globalInstance.postNotificationName(startAllHeavyOperations, 512)
				}
			}

			if (animationInProgressCount == 0) {
				runDelayedNotifications()
			}
		}

		if (checkForExpiredNotifications != null && allowedNotifications.isEmpty()) {
			AndroidUtilities.cancelRunOnUIThread(checkForExpiredNotifications)
			checkForExpiredNotifications = null
		}
	}

	fun runDelayedNotifications() {
		if (delayedPosts.isNotEmpty()) {
			delayedPostsTmp.clear()
			delayedPostsTmp.addAll(delayedPosts)
			delayedPosts.clear()

			for (a in delayedPostsTmp.indices) {
				val delayedPost = delayedPostsTmp[a]
				postNotificationNameInternal(delayedPost.id, true, *delayedPost.args)
			}

			delayedPostsTmp.clear()
		}

		if (delayedRunnables.isNotEmpty()) {
			delayedRunnablesTmp.clear()
			delayedRunnablesTmp.addAll(delayedRunnables)
			delayedRunnables.clear()

			for (a in delayedRunnablesTmp.indices) {
				AndroidUtilities.runOnUIThread(delayedRunnablesTmp[a])
			}

			delayedRunnablesTmp.clear()
		}
	}

	val isAnimationInProgress: Boolean
		get() = animationInProgressCount > 0

	fun getObservers(id: Int): ArrayList<NotificationCenterDelegate>? {
		return observers[id]
	}

	fun postNotificationName(id: Int, vararg args: Any?) {
		var allowDuringAnimation = id == startAllHeavyOperations || id == stopAllHeavyOperations || id == didReplacedPhotoInMemCache || id == closeChats || id == invalidateMotionBackground
		var expiredIndices: ArrayList<Int>? = null

		if (!allowDuringAnimation && allowedNotifications.isNotEmpty()) {
			val size = allowedNotifications.size
			var allowedCount = 0
			val currentTime = SystemClock.elapsedRealtime()

			for ((key, allowedNotification) in allowedNotifications) {
				if (currentTime - allowedNotification.time > EXPIRE_NOTIFICATIONS_TIME) {
					if (expiredIndices == null) {
						expiredIndices = ArrayList()
					}

					expiredIndices.add(key)
				}

				val allowed = allowedNotification.allowedIds

				if (allowed != null) {
					for (i in allowed) {
						if (i == id) {
							allowedCount++
							break
						}
					}
				}
				else {
					break
				}
			}

			allowDuringAnimation = size == allowedCount
		}

		if (id == startAllHeavyOperations) {
			val flags = args[0] as Int
			currentHeavyOperationFlags = currentHeavyOperationFlags and flags.inv()
		}
		else if (id == stopAllHeavyOperations) {
			val flags = args[0] as Int
			currentHeavyOperationFlags = currentHeavyOperationFlags or flags
		}

		postNotificationNameInternal(id, allowDuringAnimation, *args)

		if (expiredIndices != null) {
			for (i in expiredIndices.indices) {
				onAnimationFinish(expiredIndices[i])
			}
		}
	}

	@UiThread
	private fun postNotificationNameInternal(id: Int, allowDuringAnimation: Boolean, vararg args: Any?) {
		if (BuildConfig.DEBUG) {
			if (Thread.currentThread() !== ApplicationLoader.applicationHandler?.looper?.thread) {
				throw RuntimeException("postNotificationName allowed only from MAIN thread")
			}
		}

		if (!allowDuringAnimation && isAnimationInProgress) {
			val delayedPost = DelayedPost(id, *args)
			delayedPosts.add(delayedPost)
			FileLog.e("delay post notification " + id + " with args count = " + args.size)
			return
		}

		if (postponeCallbackList.isNotEmpty()) {
			for (i in postponeCallbackList.indices) {
				if (postponeCallbackList[i].needPostpone(id, currentAccount, *args)) {
					delayedPosts.add(DelayedPost(id, *args))
					return
				}
			}
		}

		broadcasting++

		val objects = observers[id]

		if (!objects.isNullOrEmpty()) {
			for (a in objects.indices) {
				val obj = objects[a]
				obj.didReceivedNotification(id, currentAccount, *args)
			}
		}

		broadcasting--

		if (broadcasting == 0) {
			if (removeAfterBroadcast.size() != 0) {
				for (a in 0 until removeAfterBroadcast.size()) {
					val key = removeAfterBroadcast.keyAt(a)
					val arrayList = removeAfterBroadcast[key]

					for (b in arrayList.indices) {
						removeObserver(arrayList[b], key)
					}
				}

				removeAfterBroadcast.clear()
			}

			if (addAfterBroadcast.size() != 0) {
				for (a in 0 until addAfterBroadcast.size()) {
					val key = addAfterBroadcast.keyAt(a)
					val arrayList = addAfterBroadcast[key]

					for (b in arrayList.indices) {
						addObserver(arrayList[b], key)
					}
				}

				addAfterBroadcast.clear()
			}
		}
	}

	fun addObserver(observer: NotificationCenterDelegate, id: Int) {
		if (BuildConfig.DEBUG) {
			if (Thread.currentThread() !== ApplicationLoader.applicationHandler?.looper?.thread) {
				throw RuntimeException("addObserver allowed only from MAIN thread")
			}
		}

		if (broadcasting != 0) {
			var arrayList = addAfterBroadcast[id]

			if (arrayList == null) {
				arrayList = ArrayList()
				addAfterBroadcast.put(id, arrayList)
			}

			arrayList.add(observer)

			return
		}

		var objects = observers[id]

		if (objects == null) {
			objects = ArrayList()
			observers.put(id, objects)
		}

		if (objects.contains(observer)) {
			return
		}

		objects.add(observer)
	}

	fun removeObserver(observer: NotificationCenterDelegate, id: Int) {
		if (BuildConfig.DEBUG) {
			if (Thread.currentThread() !== ApplicationLoader.applicationHandler?.looper?.thread) {
				throw RuntimeException("removeObserver allowed only from MAIN thread")
			}
		}

		if (broadcasting != 0) {
			var arrayList = removeAfterBroadcast[id]

			if (arrayList == null) {
				arrayList = ArrayList()
				removeAfterBroadcast.put(id, arrayList)
			}

			arrayList.add(observer)

			return
		}

		observers[id]?.remove(observer)
	}

	fun hasObservers(id: Int): Boolean {
		return observers.indexOfKey(id) >= 0
	}

	fun addPostponeNotificationsCallback(callback: PostponeNotificationCallback) {
		if (BuildConfig.DEBUG) {
			if (Thread.currentThread() !== ApplicationLoader.applicationHandler?.looper?.thread) {
				throw RuntimeException("PostponeNotificationsCallback allowed only from MAIN thread")
			}
		}

		if (!postponeCallbackList.contains(callback)) {
			postponeCallbackList.add(callback)
		}
	}

	fun removePostponeNotificationsCallback(callback: PostponeNotificationCallback) {
		if (BuildConfig.DEBUG) {
			if (Thread.currentThread() !== ApplicationLoader.applicationHandler?.looper?.thread) {
				throw RuntimeException("removePostponeNotificationsCallback allowed only from MAIN thread")
			}
		}

		if (postponeCallbackList.remove(callback)) {
			runDelayedNotifications()
		}
	}

	fun interface PostponeNotificationCallback {
		fun needPostpone(id: Int, currentAccount: Int, vararg args: Any?): Boolean
	}

	fun doOnIdle(runnable: Runnable) {
		if (isAnimationInProgress) {
			delayedRunnables.add(runnable)
		}
		else {
			runnable.run()
		}
	}

	fun removeDelayed(runnable: Runnable) {
		delayedRunnables.remove(runnable)
	}

	private class AllowedNotifications {
		var allowedIds: IntArray? = null
		val time = SystemClock.elapsedRealtime()
	}

	companion object {
		private const val EXPIRE_NOTIFICATIONS_TIME: Long = 5017
		private var totalEvents = 1
		const val ERROR_CHAT_BLOCKED = "ERROR_CHAT_BLOCKED"

		@JvmField
		val didReceiveNewMessages = totalEvents++

		@JvmField
		val updateInterfaces = totalEvents++

		@JvmField
		val dialogsNeedReload = totalEvents++

		@JvmField
		val closeChats = totalEvents++

		@JvmField
		val messagesDeleted = totalEvents++

		@JvmField
		val historyCleared = totalEvents++

		@JvmField
		val messagesRead = totalEvents++

		@JvmField
		val threadMessagesRead = totalEvents++

		@JvmField
		val commentsRead = totalEvents++

		@JvmField
		val changeRepliesCounter = totalEvents++

		@JvmField
		val messagesDidLoad = totalEvents++

		@JvmField
		val didLoadSponsoredMessages = totalEvents++

		@JvmField
		val didLoadSendAsPeers = totalEvents++

		@JvmField
		val updateDefaultSendAsPeer = totalEvents++

		@JvmField
		val messagesDidLoadWithoutProcess = totalEvents++

		@JvmField
		val loadingMessagesFailed = totalEvents++

		@JvmField
		val messageReceivedByAck = totalEvents++

		@JvmField
		val messageReceivedByServer = totalEvents++

		@JvmField
		val messageSendError = totalEvents++
		val forceImportContactsStart = totalEvents++

		@JvmField
		val contactsDidLoad = totalEvents++

		@JvmField
		val chatDidCreated = totalEvents++

		@JvmField
		val chatDidFailCreate = totalEvents++

		@JvmField
		val chatInfoDidLoad = totalEvents++

		@JvmField
		val chatInfoCantLoad = totalEvents++

		@JvmField
		val mediaDidLoad = totalEvents++

		@JvmField
		val mediaCountDidLoad = totalEvents++

		@JvmField
		val mediaCountsDidLoad = totalEvents++

		@JvmField
		val encryptedChatUpdated = totalEvents++

		@JvmField
		val messagesReadEncrypted = totalEvents++

		@JvmField
		val encryptedChatCreated = totalEvents++

		@JvmField
		val dialogPhotosLoaded = totalEvents++

		@JvmField
		val reloadDialogPhotos = totalEvents++

		@JvmField
		val folderBecomeEmpty = totalEvents++

		@JvmField
		val removeAllMessagesFromDialog = totalEvents++

		@JvmField
		val notificationsSettingsUpdated = totalEvents++

		@JvmField
		val blockedUsersDidLoad = totalEvents++

		@JvmField
		val openedChatChanged = totalEvents++

		@JvmField
		val didCreatedNewDeleteTask = totalEvents++

		@JvmField
		val mainUserInfoChanged = totalEvents++

		@JvmField
		val privacyRulesUpdated = totalEvents++

		@JvmField
		val updateMessageMedia = totalEvents++

		@JvmField
		val replaceMessagesObjects = totalEvents++

		@JvmField
		val didSetPasscode = totalEvents++

		@JvmField
		val twoStepPasswordChanged = totalEvents++

		@JvmField
		val didSetOrRemoveTwoStepPassword = totalEvents++

		@JvmField
		val didRemoveTwoStepPassword = totalEvents++

		@JvmField
		val replyMessagesDidLoad = totalEvents++

		@JvmField
		val didLoadPinnedMessages = totalEvents++
		val newSessionReceived = totalEvents++

		@JvmField
		val didReceivedWebpages = totalEvents++

		@JvmField
		val didReceivedWebpagesInUpdates = totalEvents++

		@JvmField
		val stickersDidLoad = totalEvents++

		@JvmField
		val diceStickersDidLoad = totalEvents++

		@JvmField
		val featuredStickersDidLoad = totalEvents++

		@JvmField
		val featuredEmojiDidLoad = totalEvents++

		@JvmField
		val groupStickersDidLoad = totalEvents++

		@JvmField
		val messagesReadContent = totalEvents++

		@JvmField
		val botInfoDidLoad = totalEvents++

		@JvmField
		val userInfoDidLoad = totalEvents++

		@JvmField
		val pinnedInfoDidLoad = totalEvents++

		@JvmField
		val botKeyboardDidLoad = totalEvents++

		@JvmField
		val chatSearchResultsAvailable = totalEvents++

		@JvmField
		val chatSearchResultsLoading = totalEvents++

		@JvmField
		val musicDidLoad = totalEvents++

		@JvmField
		val moreMusicDidLoad = totalEvents++

		@JvmField
		val needShowAlert = totalEvents++
		val needShowPlayServicesAlert = totalEvents++

		@JvmField
		val didUpdateMessagesViews = totalEvents++

		@JvmField
		val needReloadRecentDialogsSearch = totalEvents++

		@JvmField
		val peerSettingsDidLoad = totalEvents++
		val wasUnableToFindCurrentLocation = totalEvents++

		@JvmField
		val reloadHints = totalEvents++

		@JvmField
		val reloadInlineHints = totalEvents++

		@JvmField
		val newDraftReceived = totalEvents++

		@JvmField
		val recentDocumentsDidLoad = totalEvents++

		@JvmField
		val needAddArchivedStickers = totalEvents++

		@JvmField
		val archivedStickersCountDidLoad = totalEvents++

		@JvmField
		val paymentFinished = totalEvents++

		@JvmField
		val channelRightsUpdated = totalEvents++

		@JvmField
		val openArticle = totalEvents++

		@JvmField
		val updateMentionsCount = totalEvents++

		@JvmField
		val didUpdatePollResults = totalEvents++

		@JvmField
		val chatOnlineCountDidLoad = totalEvents++

		@JvmField
		val videoLoadingStateChanged = totalEvents++

		@JvmField
		val newPeopleNearbyAvailable = totalEvents++

		@JvmField
		val stopAllHeavyOperations = totalEvents++

		@JvmField
		val startAllHeavyOperations = totalEvents++

		@JvmField
		val stopSpoilers = totalEvents++

		@JvmField
		val startSpoilers = totalEvents++
		val sendingMessagesChanged = totalEvents++

		@JvmField
		val didUpdateReactions = totalEvents++

		@JvmField
		val didUpdateExtendedMedia = totalEvents++

		@JvmField
		val didVerifyMessagesStickers = totalEvents++

		@JvmField
		val scheduledMessagesUpdated = totalEvents++

		@JvmField
		val newSuggestionsAvailable = totalEvents++

		@JvmField
		val didLoadChatInviter = totalEvents++

		@JvmField
		val didLoadChatAdmins = totalEvents++

		@JvmField
		val historyImportProgressChanged = totalEvents++

		@JvmField
		val stickersImportProgressChanged = totalEvents++
		val stickersImportComplete = totalEvents++

		@JvmField
		val dialogDeleted = totalEvents++

		@JvmField
		val webViewResultSent = totalEvents++

		@JvmField
		val voiceTranscriptionUpdate = totalEvents++

		@JvmField
		val animatedEmojiDocumentLoaded = totalEvents++

		@JvmField
		val recentEmojiStatusesUpdate = totalEvents++

		@JvmField
		val didGenerateFingerprintKeyPair = totalEvents++

		// public static final int walletPendingTransactionsChanged = totalEvents++;
		// public static final int walletSyncProgressChanged = totalEvents++;
		@JvmField
		val httpFileDidLoad = totalEvents++

		@JvmField
		val httpFileDidFailedLoad = totalEvents++

		@JvmField
		val didUpdateConnectionState = totalEvents++

		@JvmField
		val fileUploaded = totalEvents++

		@JvmField
		val fileUploadFailed = totalEvents++

		@JvmField
		val fileUploadProgressChanged = totalEvents++

		@JvmField
		val fileLoadProgressChanged = totalEvents++

		@JvmField
		val fileLoaded = totalEvents++

		@JvmField
		val fileLoadFailed = totalEvents++

		@JvmField
		val filePreparingStarted = totalEvents++

		@JvmField
		val fileNewChunkAvailable = totalEvents++

		@JvmField
		val filePreparingFailed = totalEvents++

		@JvmField
		val dialogsUnreadCounterChanged = totalEvents++

		@JvmField
		val messagePlayingProgressDidChanged = totalEvents++

		@JvmField
		val messagePlayingDidReset = totalEvents++

		@JvmField
		val messagePlayingPlayStateChanged = totalEvents++

		@JvmField
		val messagePlayingDidStart = totalEvents++

		@JvmField
		val messagePlayingDidSeek = totalEvents++

		@JvmField
		val messagePlayingGoingToStop = totalEvents++

		@JvmField
		val recordProgressChanged = totalEvents++

		@JvmField
		val recordStarted = totalEvents++

		@JvmField
		val recordStartError = totalEvents++

		@JvmField
		val recordStopped = totalEvents++

		@JvmField
		val screenshotTook = totalEvents++

		@JvmField
		val albumsDidLoad = totalEvents++

		@JvmField
		val audioDidSent = totalEvents++

		@JvmField
		val audioRecordTooShort = totalEvents++

		@JvmField
		val audioRouteChanged = totalEvents++

		@JvmField
		val didStartedCall = totalEvents++

		@JvmField
		val groupCallUpdated = totalEvents++

		@JvmField
		val groupCallSpeakingUsersUpdated = totalEvents++

		@JvmField
		val groupCallScreencastStateChanged = totalEvents++

		@JvmField
		val activeGroupCallsUpdated = totalEvents++

		@JvmField
		val applyGroupCallVisibleParticipants = totalEvents++

		@JvmField
		val groupCallTypingsUpdated = totalEvents++

		@JvmField
		val didEndCall = totalEvents++
		val closeInCallActivity = totalEvents++

		@JvmField
		val groupCallVisibilityChanged = totalEvents++

		@JvmField
		val appDidLogout = totalEvents++

		@JvmField
		val configLoaded = totalEvents++

		@JvmField
		val needDeleteDialog = totalEvents++

		@JvmField
		val newEmojiSuggestionsAvailable = totalEvents++

		@JvmField
		val themeUploadedToServer = totalEvents++

		@JvmField
		val themeUploadError = totalEvents++

		@JvmField
		val dialogFiltersUpdated = totalEvents++

		@JvmField
		val filterSettingsUpdated = totalEvents++

		@JvmField
		val suggestedFiltersLoaded = totalEvents++

		@JvmField
		val updateBotMenuButton = totalEvents++

		@JvmField
		val didUpdatePremiumGiftStickers = totalEvents++

		//global
		@JvmField
		val pushMessagesUpdated = totalEvents++

		@JvmField
		val stopEncodingService = totalEvents++

		@JvmField
		val wallpapersDidLoad = totalEvents++

		@JvmField
		val wallpapersNeedReload = totalEvents++

		@JvmField
		val didReceiveSmsCode = totalEvents++

		@JvmField
		val didReceiveCall = totalEvents++

		@JvmField
		val emojiLoaded = totalEvents++

		@JvmField
		val invalidateMotionBackground = totalEvents++

		@JvmField
		val closeOtherAppActivities = totalEvents++

		@JvmField
		val cameraInitied = totalEvents++

		@JvmField
		val didReplacedPhotoInMemCache = totalEvents++

		@JvmField
		val didSetNewTheme = totalEvents++

		@JvmField
		val themeListUpdated = totalEvents++

		@JvmField
		val didApplyNewTheme = totalEvents++

		@JvmField
		val themeAccentListUpdated = totalEvents++

		@JvmField
		val needCheckSystemBarColors = totalEvents++

		@JvmField
		val needShareTheme = totalEvents++

		@JvmField
		val needSetDayNightTheme = totalEvents++

		@JvmField
		val goingToPreviewTheme = totalEvents++

		@JvmField
		val locationPermissionGranted = totalEvents++

		@JvmField
		val locationPermissionDenied = totalEvents++

		@JvmField
		val reloadInterface = totalEvents++

		@JvmField
		val suggestedLangpack = totalEvents++

		@JvmField
		val didSetNewWallpapper = totalEvents++

		@JvmField
		val proxySettingsChanged = totalEvents++

		@JvmField
		val proxyCheckDone = totalEvents++

		@JvmField
		val liveLocationsChanged = totalEvents++

		@JvmField
		val newLocationAvailable = totalEvents++

		@JvmField
		val liveLocationsCacheChanged = totalEvents++

		@JvmField
		val notificationsCountUpdated = totalEvents++

		@JvmField
		val playerDidStartPlaying = totalEvents++

		@JvmField
		val closeSearchByActiveAction = totalEvents++

		@JvmField
		val messagePlayingSpeedChanged = totalEvents++

		@JvmField
		val screenStateChanged = totalEvents++

		@JvmField
		val didClearDatabase = totalEvents++
		val voipServiceCreated = totalEvents++

		@JvmField
		val webRtcMicAmplitudeEvent = totalEvents++

		@JvmField
		val webRtcSpeakerAmplitudeEvent = totalEvents++

		@JvmField
		val showBulletin = totalEvents++

		// public static final int appUpdateAvailable = totalEvents++;
		@JvmField
		val onDatabaseMigration = totalEvents++

		@JvmField
		val onEmojiInteractionsReceived = totalEvents++

		@JvmField
		val emojiPreviewThemesChanged = totalEvents++

		@JvmField
		val reactionsDidLoad = totalEvents++

		@JvmField
		val attachMenuBotsDidLoad = totalEvents++

		@JvmField
		val chatAvailableReactionsUpdated = totalEvents++

		@JvmField
		val dialogsUnreadReactionsCounterChanged = totalEvents++

		@JvmField
		val onDatabaseOpened = totalEvents++

		@JvmField
		val onDownloadingFilesChanged = totalEvents++

		@JvmField
		val onActivityResultReceived = totalEvents++

		@JvmField
		val onRequestPermissionResultReceived = totalEvents++

		@JvmField
		val onUserRingtonesUpdated = totalEvents++

		@JvmField
		val currentUserPremiumStatusChanged = totalEvents++

		@JvmField
		val premiumPromoUpdated = totalEvents++

		@JvmField
		val premiumStatusChangedGlobal = totalEvents++
		val currentUserShowLimitReachedDialog = totalEvents++

		@JvmField
		val billingProductDetailsUpdated = totalEvents++

		@JvmField
		val premiumStickersPreviewLoaded = totalEvents++

		@JvmField
		val userEmojiStatusUpdated = totalEvents++
		val requestPermissions = totalEvents++
		val permissionsGranted = totalEvents++

		@JvmField
		val aiSubscriptionSuccess = totalEvents++
		val aiSubscriptionError = totalEvents++

		@JvmField
		val aiSubscriptionStatusReceived = totalEvents++

		@JvmField
		val aiBotStarted = totalEvents++

		@JvmField
		val aiBotStopped = totalEvents++

		@JvmField
		val aiBotRequestFailed = totalEvents++

		@JvmField
		val aiBotUpdate = totalEvents++

		@JvmField
		val updateUnreadBadge = totalEvents++

		val chatIsBlocked = totalEvents++

		private val Instance = arrayOfNulls<NotificationCenter>(UserConfig.MAX_ACCOUNT_COUNT)

		@JvmStatic
		val globalInstance = NotificationCenter(-1)

		@JvmStatic
		@UiThread
		fun getInstance(num: Int): NotificationCenter {
			var localInstance = Instance[num]

			if (localInstance == null) {
				synchronized(NotificationCenter::class.java) {
					localInstance = Instance[num]

					if (localInstance == null) {
						localInstance = NotificationCenter(num)
						Instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}
	}
}
