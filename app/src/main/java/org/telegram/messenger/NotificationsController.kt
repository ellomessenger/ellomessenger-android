/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.PostProcessor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.SparseArray
import android.util.SparseBooleanArray
import androidx.collection.LongSparseArray
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.FileProvider
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.support.LongSparseIntArray
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.EncryptedChat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.TL_account_updateNotifySettings
import org.telegram.tgnet.TLRPC.TL_inputNotifyBroadcasts
import org.telegram.tgnet.TLRPC.TL_inputNotifyChats
import org.telegram.tgnet.TLRPC.TL_inputNotifyPeer
import org.telegram.tgnet.TLRPC.TL_inputNotifyUsers
import org.telegram.tgnet.TLRPC.TL_inputPeerNotifySettings
import org.telegram.tgnet.TLRPC.TL_keyboardButtonCallback
import org.telegram.tgnet.TLRPC.TL_keyboardButtonRow
import org.telegram.tgnet.TLRPC.TL_messageActionChannelCreate
import org.telegram.tgnet.TLRPC.TL_messageActionChannelMigrateFrom
import org.telegram.tgnet.TLRPC.TL_messageActionChatAddUser
import org.telegram.tgnet.TLRPC.TL_messageActionChatCreate
import org.telegram.tgnet.TLRPC.TL_messageActionChatDeletePhoto
import org.telegram.tgnet.TLRPC.TL_messageActionChatDeleteUser
import org.telegram.tgnet.TLRPC.TL_messageActionChatEditPhoto
import org.telegram.tgnet.TLRPC.TL_messageActionChatEditTitle
import org.telegram.tgnet.TLRPC.TL_messageActionChatJoinedByLink
import org.telegram.tgnet.TLRPC.TL_messageActionChatJoinedByRequest
import org.telegram.tgnet.TLRPC.TL_messageActionChatMigrateTo
import org.telegram.tgnet.TLRPC.TL_messageActionContactSignUp
import org.telegram.tgnet.TLRPC.TL_messageActionEmpty
import org.telegram.tgnet.TLRPC.TL_messageActionGameScore
import org.telegram.tgnet.TLRPC.TL_messageActionGeoProximityReached
import org.telegram.tgnet.TLRPC.TL_messageActionGroupCall
import org.telegram.tgnet.TLRPC.TL_messageActionGroupCallScheduled
import org.telegram.tgnet.TLRPC.TL_messageActionInviteToGroupCall
import org.telegram.tgnet.TLRPC.TL_messageActionLoginUnknownLocation
import org.telegram.tgnet.TLRPC.TL_messageActionPaymentSent
import org.telegram.tgnet.TLRPC.TL_messageActionPhoneCall
import org.telegram.tgnet.TLRPC.TL_messageActionPinMessage
import org.telegram.tgnet.TLRPC.TL_messageActionScreenshotTaken
import org.telegram.tgnet.TLRPC.TL_messageActionSetChatTheme
import org.telegram.tgnet.TLRPC.TL_messageActionSetMessagesTTL
import org.telegram.tgnet.TLRPC.TL_messageActionUserJoined
import org.telegram.tgnet.TLRPC.TL_messageActionUserUpdatedPhoto
import org.telegram.tgnet.TLRPC.TL_messageMediaContact
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument
import org.telegram.tgnet.TLRPC.TL_messageMediaGame
import org.telegram.tgnet.TLRPC.TL_messageMediaGeo
import org.telegram.tgnet.TLRPC.TL_messageMediaGeoLive
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto
import org.telegram.tgnet.TLRPC.TL_messageMediaPoll
import org.telegram.tgnet.TLRPC.TL_messageMediaVenue
import org.telegram.tgnet.TLRPC.TL_messageService
import org.telegram.tgnet.TLRPC.TL_notificationSoundDefault
import org.telegram.tgnet.TLRPC.TL_notificationSoundLocal
import org.telegram.tgnet.TLRPC.TL_notificationSoundNone
import org.telegram.tgnet.TLRPC.TL_notificationSoundRingtone
import org.telegram.tgnet.TLRPC.TL_peerNotifySettings
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.TL_messageEntitySpoiler
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.BubbleActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.PopupNotificationActivity
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class NotificationsController(instance: Int) : BaseController(instance) {
	private val pushMessages = ArrayList<MessageObject>()
	private val delayedPushMessages = ArrayList<MessageObject>()
	private val pushMessagesDict = LongSparseArray<SparseArray<MessageObject>>()
	private val fcmRandomMessagesDict = LongSparseArray<MessageObject>()
	private val smartNotificationsDialogs = LongSparseArray<Point>()
	private val pushDialogs = LongSparseArray<Int>()
	private val wearNotificationsIds = LongSparseArray<Int>()
	private val lastWearNotifiedMessageId = LongSparseArray<Int>()
	private val pushDialogsOverrideMention = LongSparseArray<Int>()
	private val openedInBubbleDialogs = HashSet<Long>()
	private var openedDialogId: Long = 0
	private var lastButtonId = 5000
	private var totalUnreadCount = 0
	private var personalCount = 0
	private var notifyCheck = false
	private var lastOnlineFromOtherDevice = 0
	private var inChatSoundEnabled: Boolean
	private var lastBadgeCount = -1
	private val notificationDelayRunnable: Runnable
	private var notificationDelayWakelock: PowerManager.WakeLock? = null
	private var lastSoundPlay: Long = 0
	private var lastSoundOutPlay: Long = 0
	private var soundPool: SoundPool? = null
	private var soundIn = 0
	private var soundOut = 0
	private var soundInLoaded = false
	private var soundOutLoaded = false
	private var alarmManager: AlarmManager? = null
	private val notificationId = currentAccount + 1
	private val notificationGroup = "messages" + if (currentAccount == 0) "" else currentAccount
	private var groupsCreated: Boolean? = null
	private var channelGroupsCreated = false
	private val classGuid = ConnectionsManager.generateClassGuid()
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	@JvmField
	var popupMessages = ArrayList<MessageObject>()

	@JvmField
	var popupReplyMessages = ArrayList<MessageObject>()

	@JvmField
	var lastNotificationChannelCreateTime: Long = 0

	@JvmField
	var showBadgeNumber: Boolean

	@JvmField
	var showBadgeMuted: Boolean

	@JvmField
	var showBadgeMessages: Boolean

	fun muteUntil(did: Long, selectedTimeInSeconds: Int) {
		if (did != 0L) {
			val preferences = MessagesController.getNotificationsSettings(currentAccount)
			val editor = preferences.edit()
			val flags: Long
			val defaultEnabled = getInstance(currentAccount).isGlobalNotificationsEnabled(did)

			flags = if (selectedTimeInSeconds == Int.MAX_VALUE) {
				if (!defaultEnabled) {
					editor.remove("notify2_$did")
					0L
				}
				else {
					editor.putInt("notify2_$did", 2)
					1L
				}
			}
			else {
				editor.putInt("notify2_$did", 3)
				editor.putInt("notifyuntil_$did", connectionsManager.currentTime + selectedTimeInSeconds)
				selectedTimeInSeconds.toLong() shl 32 or 1L
			}

			getInstance(currentAccount).removeNotificationsForDialog(did)

			messagesStorage.setDialogFlags(did, flags)

			editor.commit()

			val dialog = messagesController.dialogs_dict[did]

			if (dialog != null) {
				dialog.notify_settings = TL_peerNotifySettings()

				if (selectedTimeInSeconds != Int.MAX_VALUE || defaultEnabled) {
					dialog.notify_settings.mute_until = selectedTimeInSeconds
				}
			}

			getInstance(currentAccount).updateServerNotificationsSettings(did)
		}
	}

	fun cleanup() {
		popupMessages.clear()
		popupReplyMessages.clear()
		channelGroupsCreated = false

		notificationsQueue.postRunnable {
			openedDialogId = 0
			totalUnreadCount = 0
			personalCount = 0
			pushMessages.clear()
			pushMessagesDict.clear()
			fcmRandomMessagesDict.clear()
			pushDialogs.clear()
			wearNotificationsIds.clear()
			lastWearNotifiedMessageId.clear()
			openedInBubbleDialogs.clear()
			delayedPushMessages.clear()
			notifyCheck = false
			lastBadgeCount = 0

			try {
				if (notificationDelayWakelock?.isHeld == true) {
					notificationDelayWakelock?.release()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			dismissNotification()

			broadcastUnreadMessagesNumber()

			val preferences = accountInstance.notificationsSettings

			val editor = preferences.edit()
			editor.clear()
			editor.commit()

			if (Build.VERSION.SDK_INT >= 26) {
				try {
					systemNotificationManager?.deleteNotificationChannelGroup("channels$currentAccount")
					systemNotificationManager?.deleteNotificationChannelGroup("groups$currentAccount")
					systemNotificationManager?.deleteNotificationChannelGroup("private$currentAccount")
					systemNotificationManager?.deleteNotificationChannelGroup("other$currentAccount")

					val keyStart = currentAccount.toString() + "channel"
					val list = systemNotificationManager?.notificationChannels

					if (!list.isNullOrEmpty()) {
						for (channel in list) {
							val id = channel.id

							if (id.startsWith(keyStart)) {
								try {
									systemNotificationManager?.deleteNotificationChannel(id)
								}
								catch (e: Exception) {
									FileLog.e(e)
								}
							}
						}
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
		}
	}

	fun setInChatSoundEnabled(value: Boolean) {
		inChatSoundEnabled = value
	}

	fun setOpenedDialogId(dialogId: Long) {
		notificationsQueue.postRunnable { openedDialogId = dialogId }
	}

	fun setOpenedInBubble(dialogId: Long, opened: Boolean) {
		notificationsQueue.postRunnable {
			if (opened) {
				openedInBubbleDialogs.add(dialogId)
			}
			else {
				openedInBubbleDialogs.remove(dialogId)
			}
		}
	}

	fun setLastOnlineFromOtherDevice(time: Int) {
		notificationsQueue.postRunnable {
			FileLog.d("set last online from other device = $time")
			lastOnlineFromOtherDevice = time
		}
	}

	fun removeNotificationsForDialog(did: Long) {
		processReadMessages(null, did, 0, Int.MAX_VALUE, false)
		val dialogsToUpdate = LongSparseIntArray()
		dialogsToUpdate.put(did, 0)
		processDialogsUpdateRead(dialogsToUpdate)
	}

	private fun hasMessagesToReply(): Boolean {
		for (messageObject in pushMessages) {
			val dialogId = messageObject.dialogId

			if (messageObject.messageOwner?.mentioned == true && messageObject.messageOwner?.action is TL_messageActionPinMessage || DialogObject.isEncryptedDialog(dialogId) || messageObject.messageOwner?.peer_id?.channel_id != 0L && !messageObject.isSupergroup) {
				continue
			}

			return true
		}

		return false
	}

	fun forceShowPopupForReply() {
		notificationsQueue.postRunnable {
			val popupArray = ArrayList<MessageObject>()

			for (messageObject in pushMessages) {
				val dialogId = messageObject.dialogId

				if (messageObject.messageOwner?.mentioned == true && messageObject.messageOwner?.action is TL_messageActionPinMessage || DialogObject.isEncryptedDialog(dialogId) || messageObject.messageOwner?.peer_id?.channel_id != 0L && !messageObject.isSupergroup) {
					continue
				}

				popupArray.add(0, messageObject)
			}

			if (popupArray.isNotEmpty() && !AndroidUtilities.needShowPasscode() && !SharedConfig.isWaitingForPasscodeEnter) {
				AndroidUtilities.runOnUIThread {
					popupReplyMessages = popupArray

					val popupIntent = Intent(ApplicationLoader.applicationContext, PopupNotificationActivity::class.java)
					popupIntent.putExtra("force", true)
					popupIntent.putExtra("currentAccount", currentAccount)
					popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_FROM_BACKGROUND)

					ApplicationLoader.applicationContext.startActivity(popupIntent)

					val it = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)

					ApplicationLoader.applicationContext.sendBroadcast(it)
				}
			}
		}
	}

	fun removeDeletedMessagesFromNotifications(deletedMessages: LongSparseArray<ArrayList<Int>>, isReactions: Boolean) {
		val popupArrayRemove = ArrayList<MessageObject>(0)

		notificationsQueue.postRunnable {
			val oldUnreadCount = totalUnreadCount

			for (a in 0 until deletedMessages.size()) {
				val key = deletedMessages.keyAt(a)
				val sparseArray = pushMessagesDict[key] ?: continue
				val mids = deletedMessages[key]

				if (!mids.isNullOrEmpty()) {
					var b = 0
					val N = mids.size

					while (b < N) {
						val mid = mids[b]
						val messageObject = sparseArray[mid]

						if (messageObject != null) {
							if (isReactions && !messageObject.isReactionPush) {
								b++
								continue
							}

							val dialogId = messageObject.dialogId
							var currentCount = pushDialogs[dialogId]

							if (currentCount == null) {
								currentCount = 0
							}

							var newCount = currentCount - 1

							if (newCount <= 0) {
								newCount = 0
								smartNotificationsDialogs.remove(dialogId)
							}

							if (newCount != currentCount) {
								totalUnreadCount -= currentCount
								totalUnreadCount += newCount
								pushDialogs.put(dialogId, newCount)
							}

							if (newCount == 0) {
								pushDialogs.remove(dialogId)
								pushDialogsOverrideMention.remove(dialogId)
							}

							sparseArray.remove(mid)

							delayedPushMessages.remove(messageObject)
							pushMessages.remove(messageObject)

							if (isPersonalMessage(messageObject)) {
								personalCount--
							}

							popupArrayRemove.add(messageObject)
						}

						b++
					}
				}

				if (sparseArray.size() == 0) {
					pushMessagesDict.remove(key)
				}
			}

			if (popupArrayRemove.isNotEmpty()) {
				AndroidUtilities.runOnUIThread {
					var a = 0
					val size = popupArrayRemove.size

					while (a < size) {
						popupMessages.remove(popupArrayRemove[a])
						a++
					}

					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.pushMessagesUpdated)
				}
			}

			if (oldUnreadCount != totalUnreadCount) {
				if (!notifyCheck) {
					delayedPushMessages.clear()

					runBlocking {
						showOrUpdateNotification(notifyCheck)
					}
				}
				else {
					scheduleNotificationDelay(lastOnlineFromOtherDevice > connectionsManager.currentTime)
				}

				val pushDialogsCount = pushDialogs.size()

				AndroidUtilities.runOnUIThread {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount)
					notificationCenter.postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount)
				}
			}

			notifyCheck = false

			broadcastUnreadMessagesNumber()
		}
	}

	fun removeDeletedHistoryFromNotifications(deletedMessages: LongSparseIntArray) {
		val popupArrayRemove = ArrayList<MessageObject>(0)

		notificationsQueue.postRunnable {
			val oldUnreadCount = totalUnreadCount

			for (a in 0 until deletedMessages.size()) {
				val key = deletedMessages.keyAt(a)
				val dialogId = -key
				val id = deletedMessages[key].toLong()
				var currentCount = pushDialogs[dialogId]

				if (currentCount == null) {
					currentCount = 0
				}

				var newCount: Int = currentCount
				var c = 0

				while (c < pushMessages.size) {
					val messageObject = pushMessages[c]

					if (messageObject.dialogId == dialogId && messageObject.id <= id) {
						val sparseArray = pushMessagesDict[dialogId]

						if (sparseArray != null) {
							sparseArray.remove(messageObject.id)

							if (sparseArray.size() == 0) {
								pushMessagesDict.remove(dialogId)
							}
						}

						delayedPushMessages.remove(messageObject)
						pushMessages.remove(messageObject)

						c--

						if (isPersonalMessage(messageObject)) {
							personalCount--
						}

						popupArrayRemove.add(messageObject)
						newCount--
					}

					c++
				}

				if (newCount <= 0) {
					newCount = 0
					smartNotificationsDialogs.remove(dialogId)
				}

				if (newCount != currentCount) {
					totalUnreadCount -= currentCount
					totalUnreadCount += newCount
					pushDialogs.put(dialogId, newCount)
				}

				if (newCount == 0) {
					pushDialogs.remove(dialogId)
					pushDialogsOverrideMention.remove(dialogId)
				}
			}

			if (popupArrayRemove.isEmpty()) {
				AndroidUtilities.runOnUIThread {
					var a = 0
					val size = popupArrayRemove.size

					while (a < size) {
						popupMessages.remove(popupArrayRemove[a])
						a++
					}

					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.pushMessagesUpdated)
				}
			}

			if (oldUnreadCount != totalUnreadCount) {
				if (!notifyCheck) {
					delayedPushMessages.clear()

					runBlocking {
						showOrUpdateNotification(notifyCheck)
					}
				}
				else {
					scheduleNotificationDelay(lastOnlineFromOtherDevice > connectionsManager.currentTime)
				}

				val pushDialogsCount = pushDialogs.size()

				AndroidUtilities.runOnUIThread {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount)
					notificationCenter.postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount)
				}
			}

			notifyCheck = false

			broadcastUnreadMessagesNumber()
		}
	}

	fun processReadMessages(inbox: LongSparseIntArray?, dialogId: Long, maxDate: Int, maxId: Int, isPopup: Boolean) {
		val popupArrayRemove = mutableSetOf<MessageObject>()

		notificationsQueue.postRunnable {
			if (inbox != null) {
				for (b in 0 until inbox.size()) {
					val key = inbox.keyAt(b)
					val messageId = inbox[key]
					var a = 0

					while (a < pushMessages.size) {
						val messageObject = pushMessages[a]

						if (!messageObject.messageOwner!!.from_scheduled && messageObject.dialogId == key && messageObject.id <= messageId) {
							if (isPersonalMessage(messageObject)) {
								personalCount--
							}

							popupArrayRemove.add(messageObject)

							val did = messageObject.messageOwner?.peer_id?.channel_id?.takeIf { it != 0L }?.let { -it } ?: 0
							val sparseArray = pushMessagesDict[did]

							if (sparseArray != null) {
								sparseArray.remove(messageObject.id)

								if (sparseArray.size() == 0) {
									pushMessagesDict.remove(did)
								}
							}

							delayedPushMessages.remove(messageObject)
							pushMessages.removeAt(a)

							a--
						}

						a++
					}
				}
			}

			if (dialogId != 0L && (maxId != 0 || maxDate != 0)) {
				var a = 0

				while (a < pushMessages.size) {
					val messageObject = pushMessages[a]

					if (messageObject.dialogId == dialogId) {
						var remove = false

						if (maxDate != 0) {
							if (messageObject.messageOwner!!.date <= maxDate) {
								remove = true
							}
						}
						else {
							if (!isPopup) {
								if (messageObject.id <= maxId || maxId < 0) {
									remove = true
								}
							}
							else {
								if (messageObject.id == maxId || maxId < 0) {
									remove = true
								}
							}
						}

						if (remove) {
							if (isPersonalMessage(messageObject)) {
								personalCount--
							}

							val did = messageObject.messageOwner?.peer_id?.channel_id?.takeIf { it != 0L }?.let { -it } ?: 0L
							val sparseArray = pushMessagesDict[did]

							if (sparseArray != null) {
								sparseArray.remove(messageObject.id)

								if (sparseArray.size() == 0) {
									pushMessagesDict.remove(did)
								}
							}

							pushMessages.removeAt(a)
							delayedPushMessages.remove(messageObject)

							popupArrayRemove.add(messageObject)

							a--
						}
					}

					a++
				}
			}

			if (popupArrayRemove.isNotEmpty()) {
				AndroidUtilities.runOnUIThread {
					popupMessages.removeAll(popupArrayRemove)
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.pushMessagesUpdated)
				}
			}
		}
	}

	private fun addToPopupMessages(popupArrayAdd: ArrayList<MessageObject>, messageObject: MessageObject, dialogId: Long, isChannel: Boolean, preferences: SharedPreferences): Int {
		var popup = 0

		if (!DialogObject.isEncryptedDialog(dialogId)) {
			if (preferences.getBoolean("custom_$dialogId", false)) {
				popup = preferences.getInt("popup_$dialogId", 0)
			}

			when (popup) {
				0 -> {
					popup = if (isChannel) {
						preferences.getInt("popupChannel", 0)
					}
					else {
						preferences.getInt(if (DialogObject.isChatDialog(dialogId)) "popupGroup" else "popupAll", 0)
					}
				}

				1 -> {
					popup = 3
				}

				2 -> {
					popup = 0
				}
			}
		}

		if (popup != 0 && messageObject.messageOwner?.peer_id?.channel_id != 0L && !messageObject.isSupergroup) {
			popup = 0
		}

		if (popup != 0) {
			popupArrayAdd.add(0, messageObject)
		}

		return popup
	}

	fun processEditedMessages(editedMessages: LongSparseArray<ArrayList<MessageObject>>) {
		if (editedMessages.size() == 0) {
			return
		}

		notificationsQueue.postRunnable {
			var updated = false
			var a = 0
			val N = editedMessages.size()

			while (a < N) {
				val dialogId = editedMessages.keyAt(a)

				if (pushDialogs.indexOfKey(dialogId) < 0) {
					a++
					continue
				}

				val messages = editedMessages.valueAt(a)
				var b = 0
				val N2 = messages.size

				while (b < N2) {
					val messageObject = messages[b]
					val did = messageObject.messageOwner?.peer_id?.channel_id?.takeIf { it != 0L }?.let { -it } ?: 0L
					val sparseArray = pushMessagesDict[did] ?: break
					var oldMessage = sparseArray[messageObject.id]

					if (oldMessage != null && oldMessage.isReactionPush) {
						oldMessage = null
					}

					if (oldMessage != null) {
						updated = true

						sparseArray.put(messageObject.id, messageObject)

						var idx = pushMessages.indexOf(oldMessage)

						if (idx >= 0) {
							pushMessages[idx] = messageObject
						}

						idx = delayedPushMessages.indexOf(oldMessage)

						if (idx >= 0) {
							delayedPushMessages[idx] = messageObject
						}
					}

					b++
				}

				a++
			}

			if (updated) {
				mainScope.launch {
					showOrUpdateNotification(false)
				}
			}
		}
	}

	fun processNewMessages(messageObjects: List<MessageObject>, isLast: Boolean, isFcm: Boolean, countDownLatch: CountDownLatch?) {
		if (messageObjects.isEmpty()) {
			countDownLatch?.countDown()
			return
		}

		val popupArrayAdd = ArrayList<MessageObject>(0)

		notificationsQueue.postRunnable {
			var added = false
			var edited = false
			val settingsCache = LongSparseArray<Boolean>()
			val preferences = accountInstance.notificationsSettings
			val allowPinned = preferences.getBoolean("PinnedMessages", true)
			var popup = 0
			var hasScheduled = false

			for (a in messageObjects.indices) {
				val messageObject = messageObjects[a]

				if (messageObject.wasUnread && messageObject.messageOwner is TL_messageService) {
					runBlocking(mainScope.coroutineContext) {
						messagesController.markMessageContentAsRead(messageObject)
					}
				}

				if (messageObject.messageOwner != null && (messageObject.isImportedForward || messageObject.messageOwner?.action is TL_messageActionSetMessagesTTL || messageObject.messageOwner?.silent == true && (messageObject.messageOwner?.action is TL_messageActionContactSignUp || messageObject.messageOwner?.action is TL_messageActionUserJoined))) {
					continue
				}

				val mid = messageObject.id
				val randomId = if (messageObject.isFcmMessage) messageObject.messageOwner!!.random_id else 0
				var dialogId = messageObject.dialogId

				val isChannel = if (messageObject.isFcmMessage) {
					messageObject.localChannel
				}
				else if (DialogObject.isChatDialog(dialogId)) {
					val chat = messagesController.getChat(-dialogId)
					ChatObject.isChannel(chat) && !chat.megagroup
				}
				else {
					false
				}

				val did = messageObject.messageOwner?.peer_id?.channel_id?.takeIf { it != 0L }?.let { -it } ?: 0L
				var sparseArray = pushMessagesDict[did]
				var oldMessageObject = sparseArray?.get(mid)

				if (oldMessageObject == null && messageObject.messageOwner?.random_id != 0L) {
					oldMessageObject = fcmRandomMessagesDict[messageObject.messageOwner!!.random_id]

					if (oldMessageObject != null) {
						fcmRandomMessagesDict.remove(messageObject.messageOwner!!.random_id)
					}
				}

				if (oldMessageObject != null) {
					if (oldMessageObject.isFcmMessage) {
						if (sparseArray == null) {
							sparseArray = SparseArray()
							pushMessagesDict.put(did, sparseArray)
						}

						sparseArray.put(mid, messageObject)

						val idxOld = pushMessages.indexOf(oldMessageObject)

						if (idxOld >= 0) {
							pushMessages[idxOld] = messageObject
							popup = addToPopupMessages(popupArrayAdd, messageObject, dialogId, isChannel, preferences)
						}

						if (isFcm && messageObject.localEdit.also { edited = it }) {
							messagesStorage.putPushMessage(messageObject)
						}
					}

					continue
				}

				if (edited) {
					continue
				}

				if (isFcm) {
					messagesStorage.putPushMessage(messageObject)
				}

				val originalDialogId = dialogId

				if (dialogId == openedDialogId && ApplicationLoader.isScreenOn) {
					if (!isFcm) {
						playInChatSound()
					}

					continue
				}

				if (messageObject.messageOwner?.mentioned == true) {
					if (!allowPinned && messageObject.messageOwner?.action is TL_messageActionPinMessage) {
						continue
					}

					dialogId = messageObject.fromChatId
				}

				if (isPersonalMessage(messageObject)) {
					personalCount++
				}

				added = true

				val index = settingsCache.indexOfKey(dialogId)
				var value: Boolean

				if (index >= 0) {
					value = settingsCache.valueAt(index)
				}
				else {
					val notifyOverride = getNotifyOverride(preferences, dialogId)

					value = if (notifyOverride == -1) {
						isGlobalNotificationsEnabled(dialogId, isChannel)
					}
					else {
						notifyOverride != 2
					}

					settingsCache.put(dialogId, value)
				}

				if (value) {
					if (!isFcm) {
						popup = addToPopupMessages(popupArrayAdd, messageObject, dialogId, isChannel, preferences)
					}

					if (!hasScheduled) {
						hasScheduled = messageObject.messageOwner!!.from_scheduled
					}

					delayedPushMessages.add(messageObject)
					pushMessages.add(0, messageObject)

					if (mid != 0) {
						if (sparseArray == null) {
							sparseArray = SparseArray()
							pushMessagesDict.put(did, sparseArray)
						}

						sparseArray.put(mid, messageObject)
					}
					else if (randomId != 0L) {
						fcmRandomMessagesDict.put(randomId, messageObject)
					}

					if (originalDialogId != dialogId) {
						val current = pushDialogsOverrideMention[originalDialogId]
						pushDialogsOverrideMention.put(originalDialogId, if (current == null) 1 else current + 1)
					}
				}

				if (messageObject.isReactionPush) {
					val sparseBooleanArray = SparseBooleanArray()
					sparseBooleanArray.put(mid, true)
					messagesController.checkUnreadReactions(dialogId, sparseBooleanArray)
				}
			}

			if (added) {
				notifyCheck = isLast
			}

			if (popupArrayAdd.isNotEmpty() && !AndroidUtilities.needShowPasscode() && !SharedConfig.isWaitingForPasscodeEnter) {
				val popupFinal = popup

				AndroidUtilities.runOnUIThread {
					popupMessages.addAll(0, popupArrayAdd)

					if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
						if (popupFinal == 3 || popupFinal == 1 && ApplicationLoader.isScreenOn || popupFinal == 2 && !ApplicationLoader.isScreenOn) {
							val popupIntent = Intent(ApplicationLoader.applicationContext, PopupNotificationActivity::class.java)
							popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_FROM_BACKGROUND)

							runCatching {
								ApplicationLoader.applicationContext.startActivity(popupIntent)
							}
						}
					}
				}
			}

			if (isFcm || hasScheduled) {
				if (edited) {
					delayedPushMessages.clear()

					runBlocking {
						showOrUpdateNotification(notifyCheck)
					}
				}
				else if (added) {
					val messageObject = messageObjects[0]
					val dialogId = messageObject.dialogId

					val isChannel = if (messageObject.isFcmMessage) {
						messageObject.localChannel
					}
					else {
						null
					}

					val oldUnreadCount = totalUnreadCount
					val notifyOverride = getNotifyOverride(preferences, dialogId)

					var canAddValue = if (notifyOverride == -1) {
						isGlobalNotificationsEnabled(dialogId, isChannel)
					}
					else {
						notifyOverride != 2
					}

					val currentCount = pushDialogs[dialogId]
					var newCount = if (currentCount != null) currentCount + 1 else 1

					if (notifyCheck && !canAddValue) {
						val override = pushDialogsOverrideMention[dialogId]

						if (override != null && override != 0) {
							canAddValue = true
							newCount = override
						}
					}

					if (canAddValue) {
						if (currentCount != null) {
							totalUnreadCount -= currentCount
						}

						totalUnreadCount += newCount

						pushDialogs.put(dialogId, newCount)
					}

					if (oldUnreadCount != totalUnreadCount) {
						delayedPushMessages.clear()

						runBlocking {
							showOrUpdateNotification(notifyCheck)
						}

						val pushDialogsCount = pushDialogs.size()

						AndroidUtilities.runOnUIThread {
							NotificationCenter.globalInstance.postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount)
							notificationCenter.postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount)
						}
					}

					notifyCheck = false

					broadcastUnreadMessagesNumber()
				}
			}

			countDownLatch?.countDown()
		}
	}

	fun processDialogsUpdateRead(dialogsToUpdate: LongSparseIntArray) {
		val popupArrayToRemove = mutableSetOf<MessageObject>()

		notificationsQueue.postRunnable {
			val oldUnreadCount = totalUnreadCount
			val preferences = accountInstance.notificationsSettings

			for (b in 0 until dialogsToUpdate.size()) {
				val dialogId = dialogsToUpdate.keyAt(b)
				val currentCount = pushDialogs[dialogId]
				var newCount = dialogsToUpdate[dialogId]

				if (DialogObject.isChatDialog(dialogId)) {
					val chat = messagesController.getChat(-dialogId)

					if (chat == null || chat.min || ChatObject.isNotInChat(chat)) {
						newCount = 0
					}
				}

				val notifyOverride = getNotifyOverride(preferences, dialogId)

				var canAddValue = if (notifyOverride == -1) {
					isGlobalNotificationsEnabled(dialogId)
				}
				else {
					notifyOverride != 2
				}

				if (notifyCheck && !canAddValue) {
					val override = pushDialogsOverrideMention[dialogId]

					if (override != null && override != 0) {
						canAddValue = true
						newCount = override
					}
				}

				if (newCount == 0) {
					smartNotificationsDialogs.remove(dialogId)
				}

				if (newCount < 0) {
					if (currentCount == null) {
						continue
					}

					newCount += currentCount
				}

				if (canAddValue || newCount == 0) {
					if (currentCount != null) {
						totalUnreadCount -= currentCount
					}
				}

				if (newCount == 0) {
					pushDialogs.remove(dialogId)
					pushDialogsOverrideMention.remove(dialogId)

					var a = 0

					while (a < pushMessages.size) {
						val messageObject = pushMessages[a]

						if (messageObject.messageOwner?.from_scheduled == false && messageObject.dialogId == dialogId) {
							if (isPersonalMessage(messageObject)) {
								personalCount--
							}

							pushMessages.removeAt(a)

							a--

							delayedPushMessages.remove(messageObject)

							val did = messageObject.messageOwner?.peer_id?.channel_id?.takeIf { it != 0L }?.let { -it } ?: 0L
							val sparseArray = pushMessagesDict[did]

							if (sparseArray != null) {
								sparseArray.remove(messageObject.id)

								if (sparseArray.size() == 0) {
									pushMessagesDict.remove(did)
								}
							}

							popupArrayToRemove.add(messageObject)
						}

						a++
					}
				}
				else if (canAddValue) {
					totalUnreadCount += newCount
					pushDialogs.put(dialogId, newCount)
				}
			}

			if (popupArrayToRemove.isNotEmpty()) {
				AndroidUtilities.runOnUIThread {
					popupMessages.removeAll(popupArrayToRemove)
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.pushMessagesUpdated)
				}
			}

			if (oldUnreadCount != totalUnreadCount) {
				if (!notifyCheck) {
					delayedPushMessages.clear()

					runBlocking {
						showOrUpdateNotification(notifyCheck)
					}
				}
				else {
					scheduleNotificationDelay(lastOnlineFromOtherDevice > connectionsManager.currentTime)
				}

				val pushDialogsCount = pushDialogs.size()

				AndroidUtilities.runOnUIThread {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount)
					notificationCenter.postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount)
				}
			}

			notifyCheck = false

			broadcastUnreadMessagesNumber()
		}
	}

	fun processLoadedUnreadMessages(dialogs: LongSparseArray<Int>, messages: ArrayList<Message?>?, push: ArrayList<MessageObject>?, users: ArrayList<User>?, chats: ArrayList<Chat>?, encryptedChats: ArrayList<EncryptedChat?>?) {
		messagesController.putUsers(users, true)
		messagesController.putChats(chats, true)
		messagesController.putEncryptedChats(encryptedChats, true)

		notificationsQueue.postRunnable {
			pushDialogs.clear()
			pushMessages.clear()
			pushMessagesDict.clear()

			totalUnreadCount = 0
			personalCount = 0

			val preferences = accountInstance.notificationsSettings
			val settingsCache = LongSparseArray<Boolean>()

			if (messages != null) {
				for (a in messages.indices) {
					val message = messages[a] ?: continue

					if (message.fwd_from?.imported == true || message.action is TL_messageActionSetMessagesTTL || message.silent && (message.action is TL_messageActionContactSignUp || message.action is TL_messageActionUserJoined)) {
						continue
					}

					val did = message.peer_id?.channel_id?.takeIf { it != 0L }?.let { -it } ?: 0L
					var sparseArray = pushMessagesDict[did]

					if (sparseArray != null && sparseArray.indexOfKey(message.id) >= 0) {
						continue
					}

					val messageObject = MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = false)

					if (isPersonalMessage(messageObject)) {
						personalCount++
					}
					var dialogId = messageObject.dialogId
					val originalDialogId = dialogId

					if (messageObject.messageOwner?.mentioned == true) {
						dialogId = messageObject.fromChatId
					}

					val index = settingsCache.indexOfKey(dialogId)
					var value: Boolean

					if (index >= 0) {
						value = settingsCache.valueAt(index)
					}
					else {
						val notifyOverride = getNotifyOverride(preferences, dialogId)

						value = if (notifyOverride == -1) {
							isGlobalNotificationsEnabled(dialogId)
						}
						else {
							notifyOverride != 2
						}

						settingsCache.put(dialogId, value)
					}

					if (!value || dialogId == openedDialogId && ApplicationLoader.isScreenOn) {
						continue
					}

					if (sparseArray == null) {
						sparseArray = SparseArray()
						pushMessagesDict.put(did, sparseArray)
					}

					sparseArray.put(message.id, messageObject)

					pushMessages.add(0, messageObject)

					if (originalDialogId != dialogId) {
						val current = pushDialogsOverrideMention[originalDialogId]
						pushDialogsOverrideMention.put(originalDialogId, if (current == null) 1 else current + 1)
					}
				}
			}

			for (a in 0 until dialogs.size()) {
				val dialogId = dialogs.keyAt(a)
				val index = settingsCache.indexOfKey(dialogId)
				var value: Boolean

				if (index >= 0) {
					value = settingsCache.valueAt(index)
				}
				else {
					val notifyOverride = getNotifyOverride(preferences, dialogId)

					value = if (notifyOverride == -1) {
						isGlobalNotificationsEnabled(dialogId)
					}
					else {
						notifyOverride != 2
					}

					settingsCache.put(dialogId, value)
				}

				if (!value) {
					continue
				}

				val count = dialogs.valueAt(a)

				pushDialogs.put(dialogId, count)

				totalUnreadCount += count
			}

			if (push != null) {
				for (a in push.indices) {
					val messageObject = push[a]
					val mid = messageObject.id

					if (pushMessagesDict.indexOfKey(mid.toLong()) >= 0) {
						continue
					}

					if (isPersonalMessage(messageObject)) {
						personalCount++
					}

					var dialogId = messageObject.dialogId
					val originalDialogId = dialogId
					val randomId = messageObject.messageOwner!!.random_id

					if (messageObject.messageOwner!!.mentioned) {
						dialogId = messageObject.fromChatId
					}

					val index = settingsCache.indexOfKey(dialogId)
					var value: Boolean

					if (index >= 0) {
						value = settingsCache.valueAt(index)
					}
					else {
						val notifyOverride = getNotifyOverride(preferences, dialogId)

						value = if (notifyOverride == -1) {
							isGlobalNotificationsEnabled(dialogId)
						}
						else {
							notifyOverride != 2
						}

						settingsCache.put(dialogId, value)
					}

					if (!value || dialogId == openedDialogId && ApplicationLoader.isScreenOn) {
						continue
					}

					if (mid != 0) {
						val did = messageObject.messageOwner?.peer_id?.channel_id?.takeIf { it != 0L }?.let { -it } ?: 0L
						var sparseArray = pushMessagesDict[did]

						if (sparseArray == null) {
							sparseArray = SparseArray()
							pushMessagesDict.put(did, sparseArray)
						}

						sparseArray.put(mid, messageObject)
					}
					else if (randomId != 0L) {
						fcmRandomMessagesDict.put(randomId, messageObject)
					}

					pushMessages.add(0, messageObject)

					if (originalDialogId != dialogId) {
						val current = pushDialogsOverrideMention[originalDialogId]
						pushDialogsOverrideMention.put(originalDialogId, if (current == null) 1 else current + 1)
					}

					val currentCount = pushDialogs[dialogId]
					val newCount = if (currentCount != null) currentCount + 1 else 1

					if (currentCount != null) {
						totalUnreadCount -= currentCount
					}

					totalUnreadCount += newCount

					pushDialogs.put(dialogId, newCount)
				}
			}

			val pushDialogsCount = pushDialogs.size()

			AndroidUtilities.runOnUIThread {
				if (totalUnreadCount == 0) {
					popupMessages.clear()
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.pushMessagesUpdated)
				}

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount)
				notificationCenter.postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount)
			}

			runBlocking {
				showOrUpdateNotification(SystemClock.elapsedRealtime() / 1000 < 60)
			}

			broadcastUnreadMessagesNumber()
		}
	}

	fun getTotalUnreadCount(account: Int): Int {
		return getInstance(account).totalUnreadCount
	}

	fun getUnreadCount(account: Int, force: Boolean): Int {
		val controller = getInstance(account)
		var count = 0

		if (controller.showBadgeNumber || force) {
			if (controller.showBadgeMessages || force) {
				if (controller.showBadgeMuted || force) {
					try {
						val dialogs = MessagesController.getInstance(account).allDialogs.toList()

						for (dialog in dialogs) {
							if (DialogObject.isChatDialog(dialog.id)) {
								val chat = messagesController.getChat(-dialog.id)

								if (ChatObject.isNotInChat(chat)) {
									continue
								}
							}

							count += dialog.unread_count
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				else {
					count += controller.totalUnreadCount
				}
			}
			else {
				if (controller.showBadgeMuted) {
					try {
						val dialogs = MessagesController.getInstance(account).allDialogs.toList()

						for (dialog in dialogs) {
							if (DialogObject.isChatDialog(dialog.id)) {
								val chat = messagesController.getChat(-dialog.id)

								if (ChatObject.isNotInChat(chat)) {
									continue
								}
							}

							count += dialog.unread_count
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				else {
					count += controller.pushDialogs.size()
				}
			}
		}

		return count
	}

	private fun getTotalAllUnreadCount(force: Boolean): Int {
		var count = 0

		for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
			if (UserConfig.getInstance(a).isClientActivated) {
				count += getUnreadCount(a, force)
			}
		}

		return count
	}

	fun updateBadge() {
		notificationsQueue.postRunnable { broadcastUnreadMessagesNumber() }
	}

	private fun broadcastUnreadMessagesNumber() {
		val totalUnread = getTotalAllUnreadCount(false)

		if (showBadgeNumber) {
			setBadge(totalUnread)
		}

		val thisAccountUnread = getUnreadCount(currentAccount, true)

		AndroidUtilities.runOnUIThread {
			notificationCenter.postNotificationName(NotificationCenter.updateUnreadBadge, thisAccountUnread)
			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.updateUnreadBadge, -1)
		}
	}

	private fun setBadge(count: Int) {
		if (lastBadgeCount == count) {
			return
		}

		lastBadgeCount = count

		NotificationBadge.applyCount(count)
	}

	private suspend fun getShortStringForMessage(messageObject: MessageObject, userName: Array<String?>, preview: BooleanArray?): String? {
		if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
			return ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenMessage)
		}

		var dialogId = messageObject.messageOwner?.dialog_id ?: 0L
		val chatId = messageObject.messageOwner?.peer_id?.chat_id?.takeIf { it != 0L } ?: messageObject.messageOwner?.peer_id?.channel_id ?: 0L
		var fromId = messageObject.messageOwner?.peer_id?.user_id ?: 0L

		if (preview != null) {
			preview[0] = true
		}

		val preferences = accountInstance.notificationsSettings
		val dialogPreviewEnabled = preferences.getBoolean("content_preview_$dialogId", true)

		if (messageObject.isFcmMessage) {
			if (chatId == 0L && fromId != 0L) {
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
					userName[0] = messageObject.localName
				}

				if (!dialogPreviewEnabled || !preferences.getBoolean("EnablePreviewAll", true)) {
					if (preview != null) {
						preview[0] = false
					}

					return ApplicationLoader.applicationContext.getString(R.string.Message)
				}
			}
			else if (chatId != 0L) {
				if (messageObject.messageOwner?.peer_id?.channel_id == 0L || messageObject.isSupergroup) {
					userName[0] = messageObject.localUserName
				}
				else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
					userName[0] = messageObject.localName
				}

				if (!dialogPreviewEnabled || !messageObject.localChannel && !preferences.getBoolean("EnablePreviewGroup", true) || messageObject.localChannel && !preferences.getBoolean("EnablePreviewChannel", true)) {
					if (preview != null) {
						preview[0] = false
					}

					return if (messageObject.messageOwner?.peer_id?.channel_id != 0L && !messageObject.isSupergroup) {
						LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, messageObject.localName)
					}
					else {
						LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, messageObject.localUserName, messageObject.localName)
					}
				}
			}

			return replaceSpoilers(messageObject)
		}

		val selfUsedId = userConfig.getClientUserId()

		if (fromId == 0L) {
			fromId = messageObject.fromChatId

			if (fromId == 0L) {
				fromId = -chatId
			}
		}
		else if (fromId == selfUsedId) {
			fromId = messageObject.fromChatId
		}

		if (dialogId == 0L) {
			if (chatId != 0L) {
				dialogId = -chatId
			}
			else if (fromId != 0L) {
				dialogId = fromId
			}
		}

		var name: String? = null

		if (UserObject.isReplyUser(dialogId) && messageObject.messageOwner?.fwd_from?.from_id != null) {
			fromId = MessageObject.getPeerId(messageObject.messageOwner?.fwd_from?.from_id)
		}

		if (fromId > 0) {
			val user = messagesController.getUser(fromId) ?: messagesController.loadUser(fromId, classGuid, true)

			if (user != null) {
				name = UserObject.getUserName(user)

				if (chatId != 0L) {
					userName[0] = name
				}
				else {
					if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
						userName[0] = name
					}
					else {
						userName[0] = null
					}
				}
			}
		}
		else {
			val chat = messagesController.getChat(-fromId) ?: messagesController.loadChat(-fromId, classGuid, true)

			if (chat != null) {
				name = chat.title
				userName[0] = name
			}
		}

		if (name != null && fromId > 0 && UserObject.isReplyUser(dialogId) && messageObject.messageOwner?.fwd_from?.saved_from_peer != null) {
			val id = MessageObject.getPeerId(messageObject.messageOwner?.fwd_from?.saved_from_peer)

			if (DialogObject.isChatDialog(id)) {
				val chat = messagesController.getChat(-id)

				if (chat != null) {
					name += " @ " + chat.title

					if (userName[0] != null) {
						userName[0] = name
					}
				}
			}
		}

		if (name == null) {
			return null
		}

		var chat: Chat? = null

		if (chatId != 0L) {
			chat = messagesController.getChat(chatId)

			if (chat == null) {
				return null
			}
			else if (ChatObject.isChannel(chat) && !chat.megagroup) {
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
					userName[0] = null
				}
			}
		}

		val msg: String

		if (DialogObject.isEncryptedDialog(dialogId)) {
			userName[0] = null
			return ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenMessage)
		}
		else {
			val isChannel = ChatObject.isChannel(chat) && !chat.megagroup

			if (dialogPreviewEnabled && (chatId == 0L && fromId != 0L && preferences.getBoolean("EnablePreviewAll", true) || chatId != 0L && (!isChannel && preferences.getBoolean("EnablePreviewGroup", true) || isChannel && preferences.getBoolean("EnablePreviewChannel", true)))) {
				if (messageObject.messageOwner is TL_messageService) {
					userName[0] = null

					when (messageObject.messageOwner?.action) {
						is TL_messageActionGeoProximityReached -> {
							return messageObject.messageText.toString()
						}

						is TL_messageActionUserJoined, is TL_messageActionContactSignUp -> {
							return LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, name)
						}

						is TL_messageActionUserUpdatedPhoto -> {
							return LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, name)
						}

						is TL_messageActionLoginUnknownLocation -> {
							val date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(messageObject.messageOwner!!.date.toLong() * 1000), LocaleController.getInstance().formatterDay.format(messageObject.messageOwner!!.date.toLong() * 1000))
							return LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, userConfig.getCurrentUser()?.first_name, date, messageObject.messageOwner?.action?.title, messageObject.messageOwner?.action?.address)
						}

						is TL_messageActionGameScore, is TL_messageActionPaymentSent -> {
							return messageObject.messageText.toString()
						}

						is TL_messageActionPhoneCall -> {
							return if (messageObject.messageOwner?.action?.video == true) {
								ApplicationLoader.applicationContext.getString(R.string.CallMessageVideoIncomingMissed)
							}
							else {
								ApplicationLoader.applicationContext.getString(R.string.CallMessageIncomingMissed)
							}
						}

						is TL_messageActionChatAddUser -> {
							var singleUserId = messageObject.messageOwner?.action?.user_id ?: 0L

							if (singleUserId == 0L && messageObject.messageOwner?.action?.users?.size == 1) {
								singleUserId = messageObject.messageOwner?.action?.users?.firstOrNull() ?: 0L
							}

							return if (singleUserId != 0L) {
								if (messageObject.messageOwner?.peer_id?.channel_id != 0L && chat?.megagroup != true) {
									LocaleController.formatString("ChannelAddedByNotification", R.string.ChannelAddedByNotification, name, chat?.title)
								}
								else {
									if (singleUserId == selfUsedId) {
										LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, name, chat?.title)
									}
									else {
										val u2 = messagesController.getUser(singleUserId) ?: return null

										if (fromId == u2.id) {
											if (chat?.megagroup == true) {
												LocaleController.formatString("NotificationGroupAddSelfMega", R.string.NotificationGroupAddSelfMega, name, chat.title)
											}
											else {
												LocaleController.formatString("NotificationGroupAddSelf", R.string.NotificationGroupAddSelf, name, chat?.title)
											}
										}
										else {
											LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, name, chat?.title, UserObject.getUserName(u2))
										}
									}
								}
							}
							else {
								val names = buildString {
									messageObject.messageOwner?.action?.users?.forEach {
										val user = messagesController.getUser(it)

										if (user != null) {
											val name2 = UserObject.getUserName(user)

											if (isNotEmpty()) {
												append(", ")
											}

											append(name2)
										}
									}
								}

								LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, name, chat?.title, names)
							}
						}

						is TL_messageActionGroupCall -> {
							return LocaleController.formatString("NotificationGroupCreatedCall", R.string.NotificationGroupCreatedCall, name, chat?.title)
						}

						is TL_messageActionGroupCallScheduled -> {
							return messageObject.messageText?.toString()
						}

						is TL_messageActionInviteToGroupCall -> {
							var singleUserId = messageObject.messageOwner?.action?.user_id ?: 0L

							if (singleUserId == 0L && messageObject.messageOwner?.action?.users?.size == 1) {
								singleUserId = messageObject.messageOwner?.action?.users?.firstOrNull() ?: 0L
							}
							return if (singleUserId != 0L) {
								if (singleUserId == selfUsedId) {
									LocaleController.formatString("NotificationGroupInvitedYouToCall", R.string.NotificationGroupInvitedYouToCall, name, chat?.title)
								}
								else {
									val u2 = messagesController.getUser(singleUserId) ?: return null
									LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, name, chat?.title, UserObject.getUserName(u2))
								}
							}
							else {
								val names = buildString {
									messageObject.messageOwner?.action?.users?.forEach {
										val user = messagesController.getUser(it)

										if (user != null) {
											val name2 = UserObject.getUserName(user)

											if (isNotEmpty()) {
												append(", ")
											}

											append(name2)
										}
									}
								}

								LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, name, chat?.title, names)
							}
						}

						is TL_messageActionChatJoinedByLink -> {
							return LocaleController.formatString("NotificationInvitedToGroupByLink", R.string.NotificationInvitedToGroupByLink, name, chat?.title)
						}

						is TL_messageActionChatEditTitle -> {
							return LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, name, messageObject.messageOwner?.action?.title)
						}

						is TL_messageActionChatEditPhoto, is TL_messageActionChatDeletePhoto -> {
							return if (messageObject.messageOwner?.peer_id?.channel_id != 0L && chat?.megagroup != true) {
								if (messageObject.isVideoAvatar) {
									LocaleController.formatString("ChannelVideoEditNotification", R.string.ChannelVideoEditNotification, chat?.title)
								}
								else {
									LocaleController.formatString("ChannelPhotoEditNotification", R.string.ChannelPhotoEditNotification, chat?.title)
								}
							}
							else {
								if (messageObject.isVideoAvatar) {
									LocaleController.formatString("NotificationEditedGroupVideo", R.string.NotificationEditedGroupVideo, name, chat?.title)
								}
								else {
									LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, name, chat?.title)
								}
							}
						}

						is TL_messageActionChatDeleteUser -> {
							return when (messageObject.messageOwner?.action?.user_id) {
								selfUsedId -> {
									LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, name, chat?.title)
								}

								fromId -> {
									LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, name, chat?.title)
								}

								else -> {
									val u2 = messagesController.getUser(messageObject.messageOwner?.action?.user_id) ?: return null
									LocaleController.formatString("NotificationGroupKickMember", R.string.NotificationGroupKickMember, name, chat?.title, UserObject.getUserName(u2))
								}
							}
						}

						is TL_messageActionChatCreate -> {
							return messageObject.messageText?.toString()
						}

						is TL_messageActionChannelCreate -> {
							return messageObject.messageText?.toString()
						}

						is TL_messageActionChatMigrateTo -> {
							return LocaleController.formatString("ActionMigrateFromGroupNotify", R.string.ActionMigrateFromGroupNotify, chat?.title)
						}

						is TL_messageActionChannelMigrateFrom -> {
							return LocaleController.formatString("ActionMigrateFromGroupNotify", R.string.ActionMigrateFromGroupNotify, messageObject.messageOwner?.action?.title)
						}

						is TL_messageActionScreenshotTaken -> {
							return messageObject.messageText?.toString()
						}

						is TL_messageActionPinMessage -> {
							return if (chat != null && (!ChatObject.isChannel(chat) || chat.megagroup)) {
								if (messageObject.replyMessageObject == null) {
									LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, name, chat.title)
								}
								else {
									val `object` = messageObject.replyMessageObject

									if (`object`?.isMusic == true) {
										LocaleController.formatString("NotificationActionPinnedMusic", R.string.NotificationActionPinnedMusic, name, chat.title)
									}
									else if (`object`?.isVideo == true) {
										if (`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCF9 " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedVideo", R.string.NotificationActionPinnedVideo, name, chat.title)
										}
									}
									else if (`object`?.isGif == true) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83C\uDFAC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedGif", R.string.NotificationActionPinnedGif, name, chat.title)
										}
									}
									else if (`object`?.isVoice == true) {
										LocaleController.formatString("NotificationActionPinnedVoice", R.string.NotificationActionPinnedVoice, name, chat.title)
									}
									else if (`object`?.isRoundVideo == true) {
										LocaleController.formatString("NotificationActionPinnedRound", R.string.NotificationActionPinnedRound, name, chat.title)
									}
									else if (`object`?.isSticker == true || `object`?.isAnimatedSticker == true) {
										val emoji = `object`.stickerEmoji

										if (emoji != null) {
											LocaleController.formatString("NotificationActionPinnedStickerEmoji", R.string.NotificationActionPinnedStickerEmoji, name, chat.title, emoji)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedSticker", R.string.NotificationActionPinnedSticker, name, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaDocument) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCCE " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedFile", R.string.NotificationActionPinnedFile, name, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeo || `object`?.messageOwner?.media is TL_messageMediaVenue) {
										LocaleController.formatString("NotificationActionPinnedGeo", R.string.NotificationActionPinnedGeo, name, chat.title)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeoLive) {
										LocaleController.formatString("NotificationActionPinnedGeoLive", R.string.NotificationActionPinnedGeoLive, name, chat.title)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaContact) {
										val mediaContact = `object`.messageOwner?.media as? TL_messageMediaContact
										LocaleController.formatString("NotificationActionPinnedContact2", R.string.NotificationActionPinnedContact2, name, chat.title, ContactsController.formatName(mediaContact?.first_name, mediaContact?.last_name))
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPoll) {
										val mediaPoll = `object`.messageOwner?.media as? TL_messageMediaPoll

										if (mediaPoll?.poll?.quiz == true) {
											LocaleController.formatString("NotificationActionPinnedQuiz2", R.string.NotificationActionPinnedQuiz2, name, chat.title, mediaPoll.poll?.question)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPoll2", R.string.NotificationActionPinnedPoll2, name, chat.title, mediaPoll?.poll?.question)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPhoto) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDDBC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPhoto", R.string.NotificationActionPinnedPhoto, name, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGame) {
										LocaleController.formatString("NotificationActionPinnedGame", R.string.NotificationActionPinnedGame, name, chat.title)
									}
									else if (!`object`?.messageText.isNullOrEmpty()) {
										var message = `object`?.messageText ?: ""

										if (message.length > 20) {
											message = message.subSequence(0, 20).toString() + "…"
										}

										LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title)
									}
									else {
										LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, name, chat.title)
									}
								}
							}
							else if (chat != null) {
								if (messageObject.replyMessageObject == null) {
									LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, chat.title)
								}
								else {
									val `object` = messageObject.replyMessageObject

									if (`object`?.isMusic == true) {
										LocaleController.formatString("NotificationActionPinnedMusicChannel", R.string.NotificationActionPinnedMusicChannel, chat.title)
									}
									else if (`object`?.isVideo == true) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCF9 " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedVideoChannel", R.string.NotificationActionPinnedVideoChannel, chat.title)
										}
									}
									else if (`object`?.isGif == true) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83C\uDFAC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedGifChannel", R.string.NotificationActionPinnedGifChannel, chat.title)
										}
									}
									else if (`object`?.isVoice == true) {
										LocaleController.formatString("NotificationActionPinnedVoiceChannel", R.string.NotificationActionPinnedVoiceChannel, chat.title)
									}
									else if (`object`?.isRoundVideo == true) {
										LocaleController.formatString("NotificationActionPinnedRoundChannel", R.string.NotificationActionPinnedRoundChannel, chat.title)
									}
									else if (`object`?.isSticker == true || `object`?.isAnimatedSticker == true) {
										val emoji = `object`.stickerEmoji

										if (emoji != null) {
											LocaleController.formatString("NotificationActionPinnedStickerEmojiChannel", R.string.NotificationActionPinnedStickerEmojiChannel, chat.title, emoji)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedStickerChannel", R.string.NotificationActionPinnedStickerChannel, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaDocument) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCCE " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedFileChannel", R.string.NotificationActionPinnedFileChannel, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeo || `object`?.messageOwner?.media is TL_messageMediaVenue) {
										LocaleController.formatString("NotificationActionPinnedGeoChannel", R.string.NotificationActionPinnedGeoChannel, chat.title)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeoLive) {
										LocaleController.formatString("NotificationActionPinnedGeoLiveChannel", R.string.NotificationActionPinnedGeoLiveChannel, chat.title)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaContact) {
										val mediaContact = `object`.messageOwner?.media as? TL_messageMediaContact
										LocaleController.formatString("NotificationActionPinnedContactChannel2", R.string.NotificationActionPinnedContactChannel2, chat.title, ContactsController.formatName(mediaContact?.first_name, mediaContact?.last_name))
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPoll) {
										val mediaPoll = `object`.messageOwner?.media as? TL_messageMediaPoll

										if (mediaPoll?.poll?.quiz == true) {
											LocaleController.formatString("NotificationActionPinnedQuizChannel2", R.string.NotificationActionPinnedQuizChannel2, chat.title, mediaPoll.poll?.question)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPollChannel2", R.string.NotificationActionPinnedPollChannel2, chat.title, mediaPoll?.poll?.question)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPhoto) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDDBC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPhotoChannel", R.string.NotificationActionPinnedPhotoChannel, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGame) {
										LocaleController.formatString("NotificationActionPinnedGameChannel", R.string.NotificationActionPinnedGameChannel, chat.title)
									}
									else if (!`object`?.messageText.isNullOrEmpty()) {
										var message = `object`?.messageText ?: ""

										if (message.length > 20) {
											message = message.subSequence(0, 20).toString() + "…"
										}

										LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
									}
									else {
										LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, chat.title)
									}
								}
							}
							else {
								if (messageObject.replyMessageObject == null) {
									LocaleController.formatString("NotificationActionPinnedNoTextUser", R.string.NotificationActionPinnedNoTextUser, name)
								}
								else {
									val `object` = messageObject.replyMessageObject

									if (`object`?.isMusic == true) {
										LocaleController.formatString("NotificationActionPinnedMusicUser", R.string.NotificationActionPinnedMusicUser, name)
									}
									else if (`object`?.isVideo == true) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCF9 " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedVideoUser", R.string.NotificationActionPinnedVideoUser, name)
										}
									}
									else if (`object`?.isGif == true) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83C\uDFAC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedGifUser", R.string.NotificationActionPinnedGifUser, name)
										}
									}
									else if (`object`?.isVoice == true) {
										LocaleController.formatString("NotificationActionPinnedVoiceUser", R.string.NotificationActionPinnedVoiceUser, name)
									}
									else if (`object`?.isRoundVideo == true) {
										LocaleController.formatString("NotificationActionPinnedRoundUser", R.string.NotificationActionPinnedRoundUser, name)
									}
									else if (`object`?.isSticker == true || `object`?.isAnimatedSticker == true) {
										val emoji = `object`.stickerEmoji

										if (emoji != null) {
											LocaleController.formatString("NotificationActionPinnedStickerEmojiUser", R.string.NotificationActionPinnedStickerEmojiUser, name, emoji)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedStickerUser", R.string.NotificationActionPinnedStickerUser, name)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaDocument) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCCE " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedFileUser", R.string.NotificationActionPinnedFileUser, name)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeo || `object`?.messageOwner?.media is TL_messageMediaVenue) {
										LocaleController.formatString("NotificationActionPinnedGeoUser", R.string.NotificationActionPinnedGeoUser, name)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeoLive) {
										LocaleController.formatString("NotificationActionPinnedGeoLiveUser", R.string.NotificationActionPinnedGeoLiveUser, name)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaContact) {
										val mediaContact = `object`.messageOwner?.media as? TL_messageMediaContact
										LocaleController.formatString("NotificationActionPinnedContactUser", R.string.NotificationActionPinnedContactUser, name, ContactsController.formatName(mediaContact?.first_name, mediaContact?.last_name))
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPoll) {
										val mediaPoll = `object`.messageOwner?.media as? TL_messageMediaPoll

										if (mediaPoll?.poll?.quiz == true) {
											LocaleController.formatString("NotificationActionPinnedQuizUser", R.string.NotificationActionPinnedQuizUser, name, mediaPoll.poll?.question)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPollUser", R.string.NotificationActionPinnedPollUser, name, mediaPoll?.poll?.question)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPhoto) {
										if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDDBC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPhotoUser", R.string.NotificationActionPinnedPhotoUser, name)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGame) {
										LocaleController.formatString("NotificationActionPinnedGameUser", R.string.NotificationActionPinnedGameUser, name)
									}
									else if (!`object`?.messageText.isNullOrEmpty()) {
										var message = `object`?.messageText ?: ""

										if (message.length > 20) {
											message = message.subSequence(0, 20).toString() + "…"
										}

										LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message)
									}
									else {
										LocaleController.formatString("NotificationActionPinnedNoTextUser", R.string.NotificationActionPinnedNoTextUser, name)
									}
								}
							}
						}

						is TL_messageActionSetChatTheme -> {
							val emoticon = (messageObject.messageOwner?.action as? TL_messageActionSetChatTheme)?.emoticon

							msg = if (emoticon.isNullOrEmpty()) {
								if (dialogId == selfUsedId) LocaleController.formatString("ChatThemeDisabledYou", R.string.ChatThemeDisabledYou)
								else LocaleController.formatString("ChatThemeDisabled", R.string.ChatThemeDisabled, name, emoticon)
							}
							else {
								if (dialogId == selfUsedId) LocaleController.formatString("ChangedChatThemeYou", R.string.ChatThemeChangedYou, emoticon)
								else LocaleController.formatString("ChangedChatThemeTo", R.string.ChatThemeChangedTo, name, emoticon)
							}

							return msg
						}

						is TL_messageActionChatJoinedByRequest -> {
							return messageObject.messageText?.toString()
						}
					}
				}
				else {
					return if (messageObject.isMediaEmpty) {
						if (!messageObject.messageOwner?.message.isNullOrEmpty()) {
							replaceSpoilers(messageObject)
						}
						else {
							ApplicationLoader.applicationContext.getString(R.string.Message)
						}
					}
					else if (messageObject.messageOwner?.media is TL_messageMediaPhoto) {
						if (!messageObject.messageOwner?.message.isNullOrEmpty()) {
							"\uD83D\uDDBC " + replaceSpoilers(messageObject)
						}
						else if (messageObject.messageOwner?.media?.ttl_seconds != 0) {
							ApplicationLoader.applicationContext.getString(R.string.AttachDestructingPhoto)
						}
						else {
							ApplicationLoader.applicationContext.getString(R.string.AttachPhoto)
						}
					}
					else if (messageObject.isVideo) {
						if (!messageObject.messageOwner?.message.isNullOrEmpty()) {
							"\uD83D\uDCF9 " + replaceSpoilers(messageObject)
						}
						else if (messageObject.messageOwner?.media?.ttl_seconds != 0) {
							ApplicationLoader.applicationContext.getString(R.string.AttachDestructingVideo)
						}
						else {
							ApplicationLoader.applicationContext.getString(R.string.AttachVideo)
						}
					}
					else if (messageObject.isGame) {
						ApplicationLoader.applicationContext.getString(R.string.AttachGame)
					}
					else if (messageObject.isVoice) {
						ApplicationLoader.applicationContext.getString(R.string.AttachAudio)
					}
					else if (messageObject.isRoundVideo) {
						ApplicationLoader.applicationContext.getString(R.string.AttachRound)
					}
					else if (messageObject.isMusic) {
						ApplicationLoader.applicationContext.getString(R.string.AttachMusic)
					}
					else if (messageObject.messageOwner?.media is TL_messageMediaContact) {
						ApplicationLoader.applicationContext.getString(R.string.AttachContact)
					}
					else if (messageObject.messageOwner?.media is TL_messageMediaPoll) {
						if ((messageObject.messageOwner?.media as? TL_messageMediaPoll)?.poll?.quiz == true) {
							ApplicationLoader.applicationContext.getString(R.string.QuizPoll)
						}
						else {
							ApplicationLoader.applicationContext.getString(R.string.Poll)
						}
					}
					else if (messageObject.messageOwner?.media is TL_messageMediaGeo || messageObject.messageOwner?.media is TL_messageMediaVenue) {
						ApplicationLoader.applicationContext.getString(R.string.AttachLocation)
					}
					else if (messageObject.messageOwner?.media is TL_messageMediaGeoLive) {
						ApplicationLoader.applicationContext.getString(R.string.AttachLiveLocation)
					}
					else if (messageObject.messageOwner?.media is TL_messageMediaDocument) {
						if (messageObject.isSticker || messageObject.isAnimatedSticker) {
							val emoji = messageObject.stickerEmoji

							if (emoji != null) {
								emoji + " " + ApplicationLoader.applicationContext.getString(R.string.AttachSticker)
							}
							else {
								ApplicationLoader.applicationContext.getString(R.string.AttachSticker)
							}
						}
						else if (messageObject.isGif) {
							if (!messageObject.messageOwner?.message.isNullOrEmpty()) {
								"\uD83C\uDFAC " + replaceSpoilers(messageObject)
							}
							else {
								ApplicationLoader.applicationContext.getString(R.string.AttachGif)
							}
						}
						else {
							if (!messageObject.messageOwner?.message.isNullOrEmpty()) {
								"\uD83D\uDCCE " + replaceSpoilers(messageObject)
							}
							else {
								ApplicationLoader.applicationContext.getString(R.string.AttachDocument)
							}
						}
					}
					else if (!messageObject.messageText.isNullOrEmpty()) {
						replaceSpoilers(messageObject)
					}
					else {
						ApplicationLoader.applicationContext.getString(R.string.Message)
					}
				}
			}
			else {
				if (preview != null) {
					preview[0] = false
				}

				return ApplicationLoader.applicationContext.getString(R.string.Message)
			}
		}

		return null
	}

	private val spoilerChars = charArrayOf('⠌', '⡢', '⢑', '⠨')

	private fun replaceSpoilers(messageObject: MessageObject?): String? {
		val text = messageObject?.messageOwner?.message

		if (text == null || messageObject.messageOwner == null || messageObject.messageOwner?.entities == null) {
			return null
		}

		val stringBuilder = StringBuilder(text)

		messageObject.messageOwner?.entities?.forEach {
			if (it is TL_messageEntitySpoiler) {
				for (j in 0 until it.length) {
					stringBuilder.setCharAt(it.offset + j, spoilerChars[j % spoilerChars.size])
				}
			}
		}

		return stringBuilder.toString()
	}

	private fun getStringForMessage(messageObject: MessageObject, shortMessage: Boolean, text: BooleanArray, preview: BooleanArray?): String? {
		if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
			return ApplicationLoader.applicationContext.getString(R.string.YouHaveNewMessage)
		}

		var dialogId = messageObject.messageOwner?.dialog_id ?: 0L
		val chatId = messageObject.messageOwner?.peer_id?.chat_id?.takeIf { it != 0L } ?: messageObject.messageOwner?.peer_id?.channel_id ?: 0L
		var fromId = messageObject.messageOwner?.peer_id?.user_id ?: 0L

		if (preview != null) {
			preview[0] = true
		}

		val preferences = accountInstance.notificationsSettings
		val dialogPreviewEnabled = preferences.getBoolean("content_preview_$dialogId", true)

		if (messageObject.isFcmMessage) {
			if (chatId == 0L && fromId != 0L) {
				if (!dialogPreviewEnabled || !preferences.getBoolean("EnablePreviewAll", true)) {
					if (preview != null) {
						preview[0] = false
					}

					return LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, messageObject.localName)
				}
			}
			else if (chatId != 0L) {
				if (!dialogPreviewEnabled || !messageObject.localChannel && !preferences.getBoolean("EnablePreviewGroup", true) || messageObject.localChannel && !preferences.getBoolean("EnablePreviewChannel", true)) {
					if (preview != null) {
						preview[0] = false
					}

					return if (messageObject.messageOwner?.peer_id?.channel_id != 0L && !messageObject.isSupergroup) {
						LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, messageObject.localName)
					}
					else {
						LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, messageObject.localUserName, messageObject.localName)
					}
				}
			}

			text[0] = true

			return messageObject.messageText as String
		}

		val selfUsedId = userConfig.getClientUserId()

		if (fromId == 0L) {
			fromId = messageObject.fromChatId

			if (fromId == 0L) {
				fromId = -chatId
			}
		}
		else if (fromId == selfUsedId) {
			fromId = messageObject.fromChatId
		}

		if (dialogId == 0L) {
			if (chatId != 0L) {
				dialogId = -chatId
			}
			else if (fromId != 0L) {
				dialogId = fromId
			}
		}

		var name: String? = null

		if (fromId > 0) {
			if (messageObject.messageOwner?.from_scheduled == true) {
				name = if (dialogId == selfUsedId) {
					ApplicationLoader.applicationContext.getString(R.string.MessageScheduledReminderNotification)
				}
				else {
					ApplicationLoader.applicationContext.getString(R.string.NotificationMessageScheduledName)
				}
			}
			else {
				val user = messagesController.getUser(fromId)

				if (user != null) {
					name = UserObject.getUserName(user)
				}
			}
		}
		else {
			val chat = messagesController.getChat(-fromId)

			if (chat != null) {
				name = chat.title
			}
		}

		if (name == null) {
			return null
		}

		var chat: Chat? = null

		if (chatId != 0L) {
			chat = messagesController.getChat(chatId)

			if (chat == null) {
				return null
			}
		}

		var msg: String? = null

		if (DialogObject.isEncryptedDialog(dialogId)) {
			msg = ApplicationLoader.applicationContext.getString(R.string.YouHaveNewMessage)
		}
		else {
			if (chatId == 0L && fromId != 0L) {
				if (dialogPreviewEnabled && preferences.getBoolean("EnablePreviewAll", true)) {
					if (messageObject.messageOwner is TL_messageService) {
						when (messageObject.messageOwner?.action) {
							is TL_messageActionGeoProximityReached -> {
								msg = messageObject.messageText?.toString()
							}

							is TL_messageActionUserJoined, is TL_messageActionContactSignUp -> {
								msg = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, name)
							}

							is TL_messageActionUserUpdatedPhoto -> {
								msg = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, name)
							}

							is TL_messageActionLoginUnknownLocation -> {
								val date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(messageObject.messageOwner!!.date.toLong() * 1000), LocaleController.getInstance().formatterDay.format(messageObject.messageOwner!!.date.toLong() * 1000))
								msg = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, userConfig.getCurrentUser()?.first_name, date, messageObject.messageOwner?.action?.title, messageObject.messageOwner?.action?.address)
							}

							is TL_messageActionGameScore, is TL_messageActionPaymentSent -> {
								msg = messageObject.messageText.toString()
							}

							is TL_messageActionPhoneCall -> {
								msg = if (messageObject.messageOwner?.action?.video == true) {
									ApplicationLoader.applicationContext.getString(R.string.CallMessageVideoIncomingMissed)
								}
								else {
									ApplicationLoader.applicationContext.getString(R.string.CallMessageIncomingMissed)
								}
							}

							is TL_messageActionSetChatTheme -> {
								val emoticon = (messageObject.messageOwner?.action as? TL_messageActionSetChatTheme)?.emoticon

								msg = if (emoticon.isNullOrEmpty()) {
									if (dialogId == selfUsedId) LocaleController.formatString("ChatThemeDisabledYou", R.string.ChatThemeDisabledYou) else LocaleController.formatString("ChatThemeDisabled", R.string.ChatThemeDisabled, name, emoticon)
								}
								else {
									if (dialogId == selfUsedId) LocaleController.formatString("ChangedChatThemeYou", R.string.ChatThemeChangedYou, emoticon) else LocaleController.formatString("ChangedChatThemeTo", R.string.ChatThemeChangedTo, name, emoticon)
								}

								text[0] = true
							}
						}
					}
					else {
						if (messageObject.isMediaEmpty) {
							if (!shortMessage) {
								if (!messageObject.messageOwner?.message.isNullOrEmpty()) {
									msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, messageObject.messageOwner?.message)
									text[0] = true
								}
								else {
									msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name)
								}
							}
							else {
								msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name)
							}
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaPhoto) {
							if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
								msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDDBC " + messageObject.messageOwner?.message)
								text[0] = true
							}
							else {
								msg = if (messageObject.messageOwner?.media?.ttl_seconds != 0) {
									LocaleController.formatString("NotificationMessageSDPhoto", R.string.NotificationMessageSDPhoto, name)
								}
								else {
									LocaleController.formatString("NotificationMessagePhoto", R.string.NotificationMessagePhoto, name)
								}
							}
						}
						else if (messageObject.isVideo) {
							if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
								msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDCF9 " + messageObject.messageOwner?.message)
								text[0] = true
							}
							else {
								msg = if (messageObject.messageOwner?.media?.ttl_seconds != 0) {
									LocaleController.formatString("NotificationMessageSDVideo", R.string.NotificationMessageSDVideo, name)
								}
								else {
									LocaleController.formatString("NotificationMessageVideo", R.string.NotificationMessageVideo, name)
								}
							}
						}
						else if (messageObject.isGame) {
							msg = LocaleController.formatString("NotificationMessageGame", R.string.NotificationMessageGame, name, messageObject.messageOwner?.media?.game?.title)
						}
						else if (messageObject.isVoice) {
							msg = LocaleController.formatString("NotificationMessageAudio", R.string.NotificationMessageAudio, name)
						}
						else if (messageObject.isRoundVideo) {
							msg = LocaleController.formatString("NotificationMessageRound", R.string.NotificationMessageRound, name)
						}
						else if (messageObject.isMusic) {
							msg = LocaleController.formatString("NotificationMessageMusic", R.string.NotificationMessageMusic, name)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaContact) {
							val mediaContact = messageObject.messageOwner?.media as? TL_messageMediaContact
							msg = LocaleController.formatString("NotificationMessageContact2", R.string.NotificationMessageContact2, name, ContactsController.formatName(mediaContact?.first_name, mediaContact?.last_name))
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaPoll) {
							val mediaPoll = messageObject.messageOwner?.media as? TL_messageMediaPoll

							msg = if (mediaPoll?.poll?.quiz == true) {
								LocaleController.formatString("NotificationMessageQuiz2", R.string.NotificationMessageQuiz2, name, mediaPoll.poll?.question)
							}
							else {
								LocaleController.formatString("NotificationMessagePoll2", R.string.NotificationMessagePoll2, name, mediaPoll?.poll?.question)
							}
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaGeo || messageObject.messageOwner?.media is TL_messageMediaVenue) {
							msg = LocaleController.formatString("NotificationMessageMap", R.string.NotificationMessageMap, name)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaGeoLive) {
							msg = LocaleController.formatString("NotificationMessageLiveLocation", R.string.NotificationMessageLiveLocation, name)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaDocument) {
							if (messageObject.isSticker || messageObject.isAnimatedSticker) {
								val emoji = messageObject.stickerEmoji

								msg = if (emoji != null) {
									LocaleController.formatString("NotificationMessageStickerEmoji", R.string.NotificationMessageStickerEmoji, name, emoji)
								}
								else {
									LocaleController.formatString("NotificationMessageSticker", R.string.NotificationMessageSticker, name)
								}
							}
							else if (messageObject.isGif) {
								if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
									msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83C\uDFAC " + messageObject.messageOwner?.message)
									text[0] = true
								}
								else {
									msg = LocaleController.formatString("NotificationMessageGif", R.string.NotificationMessageGif, name)
								}
							}
							else {
								if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
									msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDCCE " + messageObject.messageOwner?.message)
									text[0] = true
								}
								else {
									msg = LocaleController.formatString("NotificationMessageDocument", R.string.NotificationMessageDocument, name)
								}
							}
						}
						else {
							if (!shortMessage && !messageObject.messageText.isNullOrEmpty()) {
								msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, messageObject.messageText)
								text[0] = true
							}
							else {
								msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name)
							}
						}
					}
				}
				else {
					if (preview != null) {
						preview[0] = false
					}

					msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name)
				}
			}
			else if (chatId != 0L) {
				val isChannel = ChatObject.isChannel(chat) && !chat.megagroup

				if (dialogPreviewEnabled && (!isChannel && preferences.getBoolean("EnablePreviewGroup", true) || isChannel && preferences.getBoolean("EnablePreviewChannel", true))) {
					if (messageObject.messageOwner is TL_messageService) {
						if (messageObject.messageOwner?.action is TL_messageActionChatAddUser) {
							var singleUserId = messageObject.messageOwner?.action?.user_id ?: 0L

							if (singleUserId == 0L && messageObject.messageOwner?.action?.users?.size == 1) {
								singleUserId = messageObject.messageOwner?.action?.users?.firstOrNull() ?: 0L
							}

							msg = if (singleUserId != 0L) {
								if (messageObject.messageOwner?.peer_id?.channel_id != 0L && chat?.megagroup != true) {
									LocaleController.formatString("ChannelAddedByNotification", R.string.ChannelAddedByNotification, name, chat?.title)
								}
								else {
									if (singleUserId == selfUsedId) {
										LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, name, chat?.title)
									}
									else {
										val u2 = messagesController.getUser(singleUserId) ?: return null

										if (fromId == u2.id) {
											if (chat?.megagroup == true) {
												LocaleController.formatString("NotificationGroupAddSelfMega", R.string.NotificationGroupAddSelfMega, name, chat.title)
											}
											else {
												LocaleController.formatString("NotificationGroupAddSelf", R.string.NotificationGroupAddSelf, name, chat?.title)
											}
										}
										else {
											LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, name, chat?.title, UserObject.getUserName(u2))
										}
									}
								}
							}
							else {
								val names = buildString {
									messageObject.messageOwner?.action?.users?.forEach {
										val user = messagesController.getUser(it)

										if (user != null) {
											val name2 = UserObject.getUserName(user)

											if (isNotEmpty()) {
												append(", ")
											}

											append(name2)
										}
									}
								}

								LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, name, chat?.title, names)
							}
						}
						else if (messageObject.messageOwner?.action is TL_messageActionGroupCall) {
							msg = LocaleController.formatString("NotificationGroupCreatedCall", R.string.NotificationGroupCreatedCall, name, chat?.title)
						}
						else if (messageObject.messageOwner?.action is TL_messageActionGroupCallScheduled) {
							msg = messageObject.messageText?.toString()
						}
						else if (messageObject.messageOwner?.action is TL_messageActionInviteToGroupCall) {
							var singleUserId = messageObject.messageOwner?.action?.user_id ?: 0L

							if (singleUserId == 0L && messageObject.messageOwner?.action?.users?.size == 1) {
								singleUserId = messageObject.messageOwner?.action?.users?.firstOrNull() ?: 0L
							}
							msg = if (singleUserId != 0L) {
								if (singleUserId == selfUsedId) {
									LocaleController.formatString("NotificationGroupInvitedYouToCall", R.string.NotificationGroupInvitedYouToCall, name, chat?.title)
								}
								else {
									val u2 = messagesController.getUser(singleUserId) ?: return null
									LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, name, chat?.title, UserObject.getUserName(u2))
								}
							}
							else {
								val names = buildString {
									messageObject.messageOwner?.action?.users?.forEach {
										val user = messagesController.getUser(it)

										if (user != null) {
											val name2 = UserObject.getUserName(user)

											if (isNotEmpty()) {
												append(", ")
											}

											append(name2)
										}
									}
								}

								LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, name, chat?.title, names)
							}
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChatJoinedByLink) {
							msg = LocaleController.formatString("NotificationInvitedToGroupByLink", R.string.NotificationInvitedToGroupByLink, name, chat?.title)
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChatEditTitle) {
							msg = LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, name, messageObject.messageOwner?.action?.title)
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChatEditPhoto || messageObject.messageOwner?.action is TL_messageActionChatDeletePhoto) {
							msg = if (messageObject.messageOwner?.peer_id?.channel_id != 0L && chat?.megagroup != true) {
								if (messageObject.isVideoAvatar) {
									LocaleController.formatString("ChannelVideoEditNotification", R.string.ChannelVideoEditNotification, chat?.title)
								}
								else {
									LocaleController.formatString("ChannelPhotoEditNotification", R.string.ChannelPhotoEditNotification, chat?.title)
								}
							}
							else {
								if (messageObject.isVideoAvatar) {
									LocaleController.formatString("NotificationEditedGroupVideo", R.string.NotificationEditedGroupVideo, name, chat?.title)
								}
								else {
									LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, name, chat?.title)
								}
							}
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChatDeleteUser) {
							msg = when (messageObject.messageOwner?.action?.user_id) {
								selfUsedId -> {
									LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, name, chat?.title)
								}

								fromId -> {
									LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, name, chat?.title)
								}

								else -> {
									val u2 = messagesController.getUser(messageObject.messageOwner?.action?.user_id) ?: return null
									LocaleController.formatString("NotificationGroupKickMember", R.string.NotificationGroupKickMember, name, chat?.title, UserObject.getUserName(u2))
								}
							}
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChatCreate) {
							msg = messageObject.messageText?.toString()
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChannelCreate) {
							msg = messageObject.messageText?.toString()
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChatMigrateTo) {
							msg = LocaleController.formatString("ActionMigrateFromGroupNotify", R.string.ActionMigrateFromGroupNotify, chat?.title)
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChannelMigrateFrom) {
							msg = LocaleController.formatString("ActionMigrateFromGroupNotify", R.string.ActionMigrateFromGroupNotify, messageObject.messageOwner?.action?.title)
						}
						else if (messageObject.messageOwner?.action is TL_messageActionScreenshotTaken) {
							msg = messageObject.messageText?.toString()
						}
						else if (messageObject.messageOwner?.action is TL_messageActionPinMessage) {
							if (!ChatObject.isChannel(chat) || chat.megagroup) {
								if (messageObject.replyMessageObject == null) {
									msg = LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, name, chat?.title)
								}
								else {
									val `object` = messageObject.replyMessageObject

									if (`object`?.isMusic == true) {
										msg = LocaleController.formatString("NotificationActionPinnedMusic", R.string.NotificationActionPinnedMusic, name, chat?.title)
									}
									else if (`object`?.isVideo == true) {
										msg = if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCF9 " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat?.title)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedVideo", R.string.NotificationActionPinnedVideo, name, chat?.title)
										}
									}
									else if (`object`?.isGif == true) {
										msg = if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83C\uDFAC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat?.title)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedGif", R.string.NotificationActionPinnedGif, name, chat?.title)
										}
									}
									else if (`object`?.isVoice == true) {
										msg = LocaleController.formatString("NotificationActionPinnedVoice", R.string.NotificationActionPinnedVoice, name, chat?.title)
									}
									else if (`object`?.isRoundVideo == true) {
										msg = LocaleController.formatString("NotificationActionPinnedRound", R.string.NotificationActionPinnedRound, name, chat?.title)
									}
									else if (`object`?.isSticker == true || `object`?.isAnimatedSticker == true) {
										val emoji = `object`.stickerEmoji

										msg = if (emoji != null) {
											LocaleController.formatString("NotificationActionPinnedStickerEmoji", R.string.NotificationActionPinnedStickerEmoji, name, chat?.title, emoji)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedSticker", R.string.NotificationActionPinnedSticker, name, chat?.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaDocument) {
										msg = if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCCE " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat?.title)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedFile", R.string.NotificationActionPinnedFile, name, chat?.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeo || `object`?.messageOwner?.media is TL_messageMediaVenue) {
										msg = LocaleController.formatString("NotificationActionPinnedGeo", R.string.NotificationActionPinnedGeo, name, chat?.title)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeoLive) {
										msg = LocaleController.formatString("NotificationActionPinnedGeoLive", R.string.NotificationActionPinnedGeoLive, name, chat?.title)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaContact) {
										val mediaContact = messageObject.messageOwner?.media as? TL_messageMediaContact
										msg = LocaleController.formatString("NotificationActionPinnedContact2", R.string.NotificationActionPinnedContact2, name, chat?.title, ContactsController.formatName(mediaContact?.first_name, mediaContact?.last_name))
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPoll) {
										val mediaPoll = `object`.messageOwner?.media as? TL_messageMediaPoll

										msg = if (mediaPoll?.poll?.quiz == true) {
											LocaleController.formatString("NotificationActionPinnedQuiz2", R.string.NotificationActionPinnedQuiz2, name, chat?.title, mediaPoll.poll?.question)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPoll2", R.string.NotificationActionPinnedPoll2, name, chat?.title, mediaPoll?.poll?.question)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPhoto) {
										msg = if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDDBC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat?.title)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPhoto", R.string.NotificationActionPinnedPhoto, name, chat?.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGame) {
										msg = LocaleController.formatString("NotificationActionPinnedGame", R.string.NotificationActionPinnedGame, name, chat?.title)
									}
									else if (!`object`?.messageText.isNullOrEmpty()) {
										var message = `object`?.messageText ?: ""

										if (message.length > 20) {
											message = message.subSequence(0, 20).toString() + "…"
										}

										msg = LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat?.title)
									}
									else {
										msg = LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, name, chat?.title)
									}
								}
							}
							else {
								if (messageObject.replyMessageObject == null) {
									msg = LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, chat.title)
								}
								else {
									val `object` = messageObject.replyMessageObject

									if (`object`?.isMusic == true) {
										msg = LocaleController.formatString("NotificationActionPinnedMusicChannel", R.string.NotificationActionPinnedMusicChannel, chat.title)
									}
									else if (`object`?.isVideo == true) {
										msg = if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCF9 " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedVideoChannel", R.string.NotificationActionPinnedVideoChannel, chat.title)
										}
									}
									else if (`object`?.isGif == true) {
										msg = if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83C\uDFAC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedGifChannel", R.string.NotificationActionPinnedGifChannel, chat.title)
										}
									}
									else if (`object`?.isVoice == true) {
										msg = LocaleController.formatString("NotificationActionPinnedVoiceChannel", R.string.NotificationActionPinnedVoiceChannel, chat.title)
									}
									else if (`object`?.isRoundVideo == true) {
										msg = LocaleController.formatString("NotificationActionPinnedRoundChannel", R.string.NotificationActionPinnedRoundChannel, chat.title)
									}
									else if (`object`?.isSticker == true || `object`?.isAnimatedSticker == true) {
										val emoji = `object`.stickerEmoji

										msg = if (emoji != null) {
											LocaleController.formatString("NotificationActionPinnedStickerEmojiChannel", R.string.NotificationActionPinnedStickerEmojiChannel, chat.title, emoji)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedStickerChannel", R.string.NotificationActionPinnedStickerChannel, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaDocument) {
										msg = if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDCCE " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedFileChannel", R.string.NotificationActionPinnedFileChannel, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeo || `object`?.messageOwner?.media is TL_messageMediaVenue) {
										msg = LocaleController.formatString("NotificationActionPinnedGeoChannel", R.string.NotificationActionPinnedGeoChannel, chat.title)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGeoLive) {
										msg = LocaleController.formatString("NotificationActionPinnedGeoLiveChannel", R.string.NotificationActionPinnedGeoLiveChannel, chat.title)
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaContact) {
										val mediaContact = messageObject.messageOwner?.media as TL_messageMediaContact?
										msg = LocaleController.formatString("NotificationActionPinnedContactChannel2", R.string.NotificationActionPinnedContactChannel2, chat.title, ContactsController.formatName(mediaContact?.first_name, mediaContact?.last_name))
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPoll) {
										val mediaPoll = `object`.messageOwner?.media as? TL_messageMediaPoll

										msg = if (mediaPoll?.poll?.quiz == true) {
											LocaleController.formatString("NotificationActionPinnedQuizChannel2", R.string.NotificationActionPinnedQuizChannel2, chat.title, mediaPoll.poll?.question)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPollChannel2", R.string.NotificationActionPinnedPollChannel2, chat.title, mediaPoll?.poll?.question)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaPhoto) {
										msg = if (!`object`.messageOwner?.message.isNullOrEmpty()) {
											val message = "\uD83D\uDDBC " + `object`.messageOwner?.message
											LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
										}
										else {
											LocaleController.formatString("NotificationActionPinnedPhotoChannel", R.string.NotificationActionPinnedPhotoChannel, chat.title)
										}
									}
									else if (`object`?.messageOwner?.media is TL_messageMediaGame) {
										msg = LocaleController.formatString("NotificationActionPinnedGameChannel", R.string.NotificationActionPinnedGameChannel, chat.title)
									}
									else if (!`object`?.messageText.isNullOrEmpty()) {
										var message = `object`?.messageText ?: ""

										if (message.length > 20) {
											message = message.subSequence(0, 20).toString() + "…"
										}

										msg = LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message)
									}
									else {
										msg = LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, chat.title)
									}
								}
							}
						}
						else if (messageObject.messageOwner?.action is TL_messageActionGameScore) {
							msg = messageObject.messageText?.toString()
						}
						else if (messageObject.messageOwner?.action is TL_messageActionSetChatTheme) {
							val emoticon = (messageObject.messageOwner?.action as? TL_messageActionSetChatTheme)?.emoticon

							msg = if (emoticon.isNullOrEmpty()) {
								if (dialogId == selfUsedId) LocaleController.formatString("ChatThemeDisabledYou", R.string.ChatThemeDisabledYou) else LocaleController.formatString("ChatThemeDisabled", R.string.ChatThemeDisabled, name, emoticon)
							}
							else {
								if (dialogId == selfUsedId) LocaleController.formatString("ChangedChatThemeYou", R.string.ChatThemeChangedYou, emoticon) else LocaleController.formatString("ChangedChatThemeTo", R.string.ChatThemeChangedTo, name, emoticon)
							}
						}
						else if (messageObject.messageOwner?.action is TL_messageActionChatJoinedByRequest) {
							msg = messageObject.messageText?.toString()
						}
					}
					else if (ChatObject.isChannel(chat) && !chat.megagroup) {
						if (messageObject.isMediaEmpty) {
							if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
								msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, messageObject.messageOwner?.message)
								text[0] = true
							}
							else {
								msg = LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, name)
							}
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaPhoto) {
							if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
								msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDDBC " + messageObject.messageOwner?.message)
								text[0] = true
							}
							else {
								msg = LocaleController.formatString("ChannelMessagePhoto", R.string.ChannelMessagePhoto, name)
							}
						}
						else if (messageObject.isVideo) {
							if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
								msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDCF9 " + messageObject.messageOwner?.message)
								text[0] = true
							}
							else {
								msg = LocaleController.formatString("ChannelMessageVideo", R.string.ChannelMessageVideo, name)
							}
						}
						else if (messageObject.isVoice) {
							msg = LocaleController.formatString("ChannelMessageAudio", R.string.ChannelMessageAudio, name)
						}
						else if (messageObject.isRoundVideo) {
							msg = LocaleController.formatString("ChannelMessageRound", R.string.ChannelMessageRound, name)
						}
						else if (messageObject.isMusic) {
							msg = LocaleController.formatString("ChannelMessageMusic", R.string.ChannelMessageMusic, name)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaContact) {
							val mediaContact = messageObject.messageOwner?.media as? TL_messageMediaContact
							msg = LocaleController.formatString("ChannelMessageContact2", R.string.ChannelMessageContact2, name, ContactsController.formatName(mediaContact?.first_name, mediaContact?.last_name))
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaPoll) {
							val mediaPoll = messageObject.messageOwner?.media as? TL_messageMediaPoll

							msg = if (mediaPoll?.poll?.quiz == true) {
								LocaleController.formatString("ChannelMessageQuiz2", R.string.ChannelMessageQuiz2, name, mediaPoll.poll?.question)
							}
							else {
								LocaleController.formatString("ChannelMessagePoll2", R.string.ChannelMessagePoll2, name, mediaPoll?.poll?.question)
							}
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaGeo || messageObject.messageOwner?.media is TL_messageMediaVenue) {
							msg = LocaleController.formatString("ChannelMessageMap", R.string.ChannelMessageMap, name)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaGeoLive) {
							msg = LocaleController.formatString("ChannelMessageLiveLocation", R.string.ChannelMessageLiveLocation, name)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaDocument) {
							if (messageObject.isSticker || messageObject.isAnimatedSticker) {
								val emoji = messageObject.stickerEmoji

								msg = if (emoji != null) {
									LocaleController.formatString("ChannelMessageStickerEmoji", R.string.ChannelMessageStickerEmoji, name, emoji)
								}
								else {
									LocaleController.formatString("ChannelMessageSticker", R.string.ChannelMessageSticker, name)
								}
							}
							else if (messageObject.isGif) {
								if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
									msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83C\uDFAC " + messageObject.messageOwner?.message)
									text[0] = true
								}
								else {
									msg = LocaleController.formatString("ChannelMessageGIF", R.string.ChannelMessageGIF, name)
								}
							}
							else {
								if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
									msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDCCE " + messageObject.messageOwner?.message)
									text[0] = true
								}
								else {
									msg = LocaleController.formatString("ChannelMessageDocument", R.string.ChannelMessageDocument, name)
								}
							}
						}
						else {
							if (!shortMessage && !messageObject.messageText.isNullOrEmpty()) {
								msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, messageObject.messageText)
								text[0] = true
							}
							else {
								msg = LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, name)
							}
						}
					}
					else {
						msg = if (messageObject.isMediaEmpty) {
							if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
								LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat?.title, messageObject.messageOwner?.message)
							}
							else {
								LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, name, chat?.title)
							}
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaPhoto) {
							if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
								LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat?.title, "\uD83D\uDDBC " + messageObject.messageOwner?.message)
							}
							else {
								LocaleController.formatString("NotificationMessageGroupPhoto", R.string.NotificationMessageGroupPhoto, name, chat?.title)
							}
						}
						else if (messageObject.isVideo) {
							if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
								LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat?.title, "\uD83D\uDCF9 " + messageObject.messageOwner?.message)
							}
							else {
								LocaleController.formatString(" ", R.string.NotificationMessageGroupVideo, name, chat?.title)
							}
						}
						else if (messageObject.isVoice) {
							LocaleController.formatString("NotificationMessageGroupAudio", R.string.NotificationMessageGroupAudio, name, chat?.title)
						}
						else if (messageObject.isRoundVideo) {
							LocaleController.formatString("NotificationMessageGroupRound", R.string.NotificationMessageGroupRound, name, chat?.title)
						}
						else if (messageObject.isMusic) {
							LocaleController.formatString("NotificationMessageGroupMusic", R.string.NotificationMessageGroupMusic, name, chat?.title)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaContact) {
							val mediaContact = messageObject.messageOwner?.media as? TL_messageMediaContact
							LocaleController.formatString("NotificationMessageGroupContact2", R.string.NotificationMessageGroupContact2, name, chat?.title, ContactsController.formatName(mediaContact?.first_name, mediaContact?.last_name))
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaPoll) {
							val mediaPoll = messageObject.messageOwner?.media as? TL_messageMediaPoll

							if (mediaPoll?.poll?.quiz == true) {
								LocaleController.formatString("NotificationMessageGroupQuiz2", R.string.NotificationMessageGroupQuiz2, name, chat?.title, mediaPoll.poll?.question)
							}
							else {
								LocaleController.formatString("NotificationMessageGroupPoll2", R.string.NotificationMessageGroupPoll2, name, chat?.title, mediaPoll?.poll?.question)
							}
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaGame) {
							LocaleController.formatString("NotificationMessageGroupGame", R.string.NotificationMessageGroupGame, name, chat?.title, messageObject.messageOwner?.media?.game?.title)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaGeo || messageObject.messageOwner?.media is TL_messageMediaVenue) {
							LocaleController.formatString("NotificationMessageGroupMap", R.string.NotificationMessageGroupMap, name, chat?.title)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaGeoLive) {
							LocaleController.formatString("NotificationMessageGroupLiveLocation", R.string.NotificationMessageGroupLiveLocation, name, chat?.title)
						}
						else if (messageObject.messageOwner?.media is TL_messageMediaDocument) {
							if (messageObject.isSticker || messageObject.isAnimatedSticker) {
								val emoji = messageObject.stickerEmoji

								if (emoji != null) {
									LocaleController.formatString("NotificationMessageGroupStickerEmoji", R.string.NotificationMessageGroupStickerEmoji, name, chat?.title, emoji)
								}
								else {
									LocaleController.formatString("NotificationMessageGroupSticker", R.string.NotificationMessageGroupSticker, name, chat?.title)
								}
							}
							else if (messageObject.isGif) {
								if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
									LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat?.title, "\uD83C\uDFAC " + messageObject.messageOwner?.message)
								}
								else {
									LocaleController.formatString("NotificationMessageGroupGif", R.string.NotificationMessageGroupGif, name, chat?.title)
								}
							}
							else {
								if (!shortMessage && !messageObject.messageOwner?.message.isNullOrEmpty()) {
									LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat?.title, "\uD83D\uDCCE " + messageObject.messageOwner?.message)
								}
								else {
									LocaleController.formatString("NotificationMessageGroupDocument", R.string.NotificationMessageGroupDocument, name, chat?.title)
								}
							}
						}
						else {
							if (!shortMessage && !messageObject.messageText.isNullOrEmpty()) {
								LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat?.title, messageObject.messageText)
							}
							else {
								LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, name, chat?.title)
							}
						}
					}
				}
				else {
					if (preview != null) {
						preview[0] = false
					}

					msg = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, name)
					}
					else {
						LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, name, chat?.title)
					}
				}
			}
		}

		return msg
	}

	private fun scheduleNotificationRepeat() {
		try {
			val intent = Intent(ApplicationLoader.applicationContext, NotificationRepeat::class.java)
			intent.putExtra("currentAccount", currentAccount)

			val pintent = PendingIntent.getService(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

			val preferences = accountInstance.notificationsSettings
			val minutes = preferences.getInt("repeat_messages", 60)

			if (minutes > 0 && personalCount > 0) {
				alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + minutes.toLong() * 60 * 1000, pintent)
			}
			else {
				alarmManager?.cancel(pintent)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun isPersonalMessage(messageObject: MessageObject): Boolean {
		return messageObject.messageOwner?.peer_id?.chat_id == 0L && messageObject.messageOwner?.peer_id?.channel_id == 0L && (messageObject.messageOwner?.action == null || messageObject.messageOwner?.action is TL_messageActionEmpty)
	}

	private fun getNotifyOverride(preferences: SharedPreferences, dialogId: Long): Int {
		var notifyOverride = preferences.getInt("notify2_$dialogId", -1)

		if (notifyOverride == 3) {
			val muteUntil = preferences.getInt("notifyuntil_$dialogId", 0)

			if (muteUntil >= connectionsManager.currentTime) {
				notifyOverride = 2
			}
		}

		return notifyOverride
	}

	fun showNotifications() {
		notificationsQueue.postRunnable {
			mainScope.launch {
				showOrUpdateNotification(false)
			}
		}
	}

	fun hideNotifications() {
		notificationsQueue.postRunnable {
			notificationManager?.cancel(notificationId)

			lastWearNotifiedMessageId.clear()

			for (a in 0 until wearNotificationsIds.size()) {
				notificationManager?.cancel(wearNotificationsIds.valueAt(a))
			}

			wearNotificationsIds.clear()
		}
	}

	private fun dismissNotification() {
		try {
			notificationManager?.cancel(notificationId)

			pushMessages.clear()
			pushMessagesDict.clear()
			lastWearNotifiedMessageId.clear()

			for (a in 0 until wearNotificationsIds.size()) {
				val did = wearNotificationsIds.keyAt(a)

				if (openedInBubbleDialogs.contains(did)) {
					continue
				}

				notificationManager?.cancel(wearNotificationsIds.valueAt(a))
			}

			wearNotificationsIds.clear()

			AndroidUtilities.runOnUIThread {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.pushMessagesUpdated)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun playInChatSound() {
		if (!inChatSoundEnabled || MediaController.getInstance().isRecordingAudio) {
			return
		}

		try {
			if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) {
				return
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			val preferences = accountInstance.notificationsSettings
			val notifyOverride = getNotifyOverride(preferences, openedDialogId)

			if (notifyOverride == 2) {
				return
			}

			notificationsQueue.postRunnable {
				if (abs(SystemClock.elapsedRealtime() - lastSoundPlay) <= 500) {
					return@postRunnable
				}

				try {
					if (soundPool == null) {
						soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_SYSTEM).build()).build()

						soundPool?.setOnLoadCompleteListener { soundPool, sampleId, status ->
							if (status == 0) {
								try {
									soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f)
								}
								catch (e: Exception) {
									FileLog.e(e)
								}
							}
						}
					}

					if (soundIn == 0 && !soundInLoaded) {
						soundInLoaded = true
						soundIn = soundPool?.load(ApplicationLoader.applicationContext, R.raw.sound_in, 1) ?: 0
					}

					if (soundIn != 0) {
						try {
							lastSoundPlay = SystemClock.elapsedRealtime()
							soundPool?.play(soundIn, 1.0f, 1.0f, 1, 0, 1.0f)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun scheduleNotificationDelay(onlineReason: Boolean) {
		try {
			if (BuildConfig.DEBUG) {
				FileLog.d("delay notification start, onlineReason = $onlineReason")
			}

			notificationDelayWakelock?.acquire(10000)

			notificationsQueue.cancelRunnable(notificationDelayRunnable)
			notificationsQueue.postRunnable(notificationDelayRunnable, (if (onlineReason) 3 * 1000 else 1000).toLong())
		}
		catch (e: Exception) {
			FileLog.e(e)

			runBlocking {
				showOrUpdateNotification(notifyCheck)
			}
		}
	}

	fun repeatNotificationMaybe() {
		notificationsQueue.postRunnable {
			val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]

			if (hour in 11..22) {
				notificationManager?.cancel(notificationId)

				mainScope.launch {
					showOrUpdateNotification(true)
				}
			}
			else {
				scheduleNotificationRepeat()
			}
		}
	}

	@OptIn(ExperimentalContracts::class)
	private fun isEmptyVibration(pattern: LongArray?): Boolean {
		contract {
			returns(true) implies (pattern != null)
		}

		if (pattern == null || pattern.isEmpty()) {
			return false
		}

		for (l in pattern) {
			if (l != 0L) {
				return false
			}
		}

		return true
	}

	private fun deleteNotificationChannelInternal(dialogId: Long, what: Int) {
		if (Build.VERSION.SDK_INT < 26) {
			return
		}

		try {
			val preferences = accountInstance.notificationsSettings
			val editor = preferences.edit()

			if (what == 0 || what == -1) {
				val key = "com.beint.elloapp.key$dialogId"
				val channelId = preferences.getString(key, null)

				if (channelId != null) {
					editor.remove(key).remove(key + "_s")

					try {
						systemNotificationManager?.deleteNotificationChannel(channelId)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			if (what == 1 || what == -1) {
				val key = "com.beint.elloapp.keyia$dialogId"
				val channelId = preferences.getString(key, null)

				if (channelId != null) {
					editor.remove(key).remove(key + "_s")

					try {
						systemNotificationManager?.deleteNotificationChannel(channelId)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			editor.commit()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	@JvmOverloads
	fun deleteNotificationChannel(dialogId: Long, what: Int = -1) {
		if (Build.VERSION.SDK_INT < 26) {
			return
		}

		notificationsQueue.postRunnable {
			deleteNotificationChannelInternal(dialogId, what)
		}
	}

	private fun deleteNotificationChannelGlobalInternal(type: Int, what: Int) {
		if (Build.VERSION.SDK_INT < 26) {
			return
		}

		try {
			val preferences = accountInstance.notificationsSettings
			val editor = preferences.edit()

			if (what == 0 || what == -1) {
				val key = when (type) {
					TYPE_CHANNEL -> "channels"
					TYPE_GROUP -> "groups"
					else -> "private"
				}

				val channelId = preferences.getString(key, null)

				if (channelId != null) {
					editor.remove(key).remove(key + "_s")

					try {
						systemNotificationManager?.deleteNotificationChannel(channelId)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			if (what == 1 || what == -1) {
				val key = when (type) {
					TYPE_CHANNEL -> "channels_ia"
					TYPE_GROUP -> "groups_ia"
					else -> "private_ia"
				}

				val channelId = preferences.getString(key, null)

				if (channelId != null) {
					editor.remove(key).remove(key + "_s")

					try {
						systemNotificationManager?.deleteNotificationChannel(channelId)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			val overwriteKey = when (type) {
				TYPE_CHANNEL -> "overwrite_channel"
				TYPE_GROUP -> "overwrite_group"
				else -> "overwrite_private"
			}

			editor.remove(overwriteKey)
			editor.commit()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	@JvmOverloads
	fun deleteNotificationChannelGlobal(type: Int, what: Int = -1) {
		if (Build.VERSION.SDK_INT < 26) {
			return
		}

		notificationsQueue.postRunnable {
			deleteNotificationChannelGlobalInternal(type, what)
		}
	}

	fun deleteAllNotificationChannels() {
		if (Build.VERSION.SDK_INT < 26) {
			return
		}

		notificationsQueue.postRunnable {
			try {
				val preferences = accountInstance.notificationsSettings
				val values = preferences.all
				val editor = preferences.edit()

				for ((key, value) in values) {
					if (key.startsWith("com.beint.elloapp.key")) {
						if (!key.endsWith("_s")) {
							val id = value as String
							systemNotificationManager?.deleteNotificationChannel(id)
						}

						editor.remove(key)
					}
				}

				editor.commit()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun unsupportedNotificationShortcut(): Boolean {
		return Build.VERSION.SDK_INT < 29 || !SharedConfig.chatBubbles
	}

	@SuppressLint("RestrictedApi")
	private fun createNotificationShortcut(builder: NotificationCompat.Builder, did: Long, name: String, user: User?, chat: Chat?, person: Person?): String? {
		if (unsupportedNotificationShortcut() || ChatObject.isChannel(chat) && !chat.megagroup) {
			return null
		}

		try {
			val id = "ndid_$did"

			val shortcutIntent = Intent(ApplicationLoader.applicationContext, OpenChatReceiver::class.java)
			shortcutIntent.setAction("com.tmessages.openchat" + Math.random() + Int.MAX_VALUE)

			if (did > 0) {
				shortcutIntent.putExtra("userId", did)
			}
			else {
				shortcutIntent.putExtra("chatId", -did)
			}

			val shortcutBuilder = ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, id).setShortLabel(if (chat != null) name else UserObject.getFirstName(user)).setLongLabel(name).setIntent(Intent(Intent.ACTION_DEFAULT)).setIntent(shortcutIntent).setLongLived(true).setLocusId(LocusIdCompat(id))
			var avatar: Bitmap? = null

			if (person != null) {
				shortcutBuilder.setPerson(person)
				shortcutBuilder.setIcon(person.icon)

				if (person.icon != null) {
					avatar = person.icon?.bitmap
				}
			}

			val shortcut = shortcutBuilder.build()

			ShortcutManagerCompat.pushDynamicShortcut(ApplicationLoader.applicationContext, shortcut)

			builder.setShortcutInfo(shortcut)

			val intent = Intent(ApplicationLoader.applicationContext, BubbleActivity::class.java)
			intent.setAction("com.tmessages.openchat" + Math.random() + Int.MAX_VALUE)

			if (DialogObject.isUserDialog(did)) {
				intent.putExtra("userId", did)
			}
			else {
				intent.putExtra("chatId", -did)
			}

			intent.putExtra("currentAccount", currentAccount)

			val icon = if (avatar != null) {
				IconCompat.createWithAdaptiveBitmap(avatar)
			}
			else if (user != null) {
				IconCompat.createWithResource(ApplicationLoader.applicationContext, if (user.bot) R.drawable.book_bot else R.drawable.book_user)
			}
			else {
				IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_group)
			}

			val bubbleBuilder = NotificationCompat.BubbleMetadata.Builder(PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE), icon)
			bubbleBuilder.setSuppressNotification(openedDialogId == did)
			bubbleBuilder.setAutoExpandBubble(false)
			bubbleBuilder.setDesiredHeight(AndroidUtilities.dp(640f))

			builder.setBubbleMetadata(bubbleBuilder.build())

			return id
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return null
	}

	@TargetApi(26)
	private fun ensureGroupsCreated() {
		val preferences = accountInstance.notificationsSettings

		if (groupsCreated == null) {
			groupsCreated = preferences.getBoolean("groupsCreated4", false)
		}

		if (groupsCreated != true) {
			try {
				val keyStart = currentAccount.toString() + "channel"
				val list = systemNotificationManager?.notificationChannels ?: emptyList()
				val count = list.size
				var editor: SharedPreferences.Editor? = null

				for (a in 0 until count) {
					val channel = list[a]
					val id = channel.id

					if (id.startsWith(keyStart)) {
						val importance = channel.importance

						if (importance != NotificationManager.IMPORTANCE_HIGH && importance != NotificationManager.IMPORTANCE_MAX) { //TODO remove after some time, 7.3.0 bug fix
							if (id.contains("_ia_")) {
								//do nothing
							}
							else if (id.contains("_channels_")) {
								if (editor == null) {
									editor = accountInstance.notificationsSettings.edit()
								}

								editor?.let {
									it.remove("priority_channel")
									it.remove("vibrate_channel")
									it.remove("ChannelSoundPath")
									it.remove("ChannelSound")
								}
							}
							else if (id.contains("_groups_")) {
								if (editor == null) {
									editor = accountInstance.notificationsSettings.edit()
								}

								editor?.let {
									it.remove("priority_group")
									it.remove("vibrate_group")
									it.remove("GroupSoundPath")
									it.remove("GroupSound")
								}
							}
							else if (id.contains("_private_")) {
								if (editor == null) {
									editor = accountInstance.notificationsSettings.edit()
								}

								editor?.let {
									it.remove("priority_messages")
									it.remove("priority_group")
									it.remove("vibrate_messages")
									it.remove("GlobalSoundPath")
									it.remove("GlobalSound")
								}
							}
							else {
								val dialogId = Utilities.parseLong(id.substring(9, id.indexOf('_', 9)))

								if (dialogId != 0L) {
									if (editor == null) {
										editor = accountInstance.notificationsSettings.edit()
									}

									editor?.let {
										it.remove("priority_$dialogId")
										it.remove("vibrate_$dialogId")
										it.remove("sound_path_$dialogId")
										it.remove("sound_$dialogId")
									}
								}
							}
						}

						systemNotificationManager?.deleteNotificationChannel(id)
					}
				}
				editor?.commit()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			preferences.edit().putBoolean("groupsCreated4", true).commit()

			groupsCreated = true
		}

		if (!channelGroupsCreated) {
			val list = systemNotificationManager?.notificationChannelGroups ?: emptyList()
			var channelsId: String? = "channels$currentAccount"
			var groupsId: String? = "groups$currentAccount"
			var privateId: String? = "private$currentAccount"
			var otherId: String? = "other$currentAccount"

			var a = 0
			val N = list.size

			while (a < N) {
				val id = list[a].id

				if (channelsId != null && channelsId == id) {
					channelsId = null
				}
				else if (groupsId != null && groupsId == id) {
					groupsId = null
				}
				else if (privateId != null && privateId == id) {
					privateId = null
				}
				else if (otherId != null && otherId == id) {
					otherId = null
				}

				if (channelsId == null && groupsId == null && privateId == null && otherId == null) {
					break
				}

				a++
			}

			if (channelsId != null || groupsId != null || privateId != null || otherId != null) {
				val user = messagesController.getUser(userConfig.getClientUserId())

				if (user == null) {
					userConfig.getCurrentUser()
				}

				val userName = if (user != null) {
					" (" + ContactsController.formatName(user.first_name, user.last_name) + ")"
				}
				else {
					""
				}

				val channelGroups = ArrayList<NotificationChannelGroup>()

				if (channelsId != null) {
					channelGroups.add(NotificationChannelGroup(channelsId, ApplicationLoader.applicationContext.getString(R.string.NotificationsChannels) + userName))
				}

				if (groupsId != null) {
					channelGroups.add(NotificationChannelGroup(groupsId, ApplicationLoader.applicationContext.getString(R.string.NotificationsGroups) + userName))
				}

				if (privateId != null) {
					channelGroups.add(NotificationChannelGroup(privateId, ApplicationLoader.applicationContext.getString(R.string.NotificationsPrivateChats) + userName))
				}

				if (otherId != null) {
					channelGroups.add(NotificationChannelGroup(otherId, ApplicationLoader.applicationContext.getString(R.string.NotificationsOther) + userName))
				}

				systemNotificationManager?.createNotificationChannelGroups(channelGroups)
			}

			channelGroupsCreated = true
		}
	}

	@TargetApi(26)
	private fun validateChannelId(dialogId: Long, name: String?, vibrationPattern: LongArray?, ledColor: Int, importance: Int, isDefault: Boolean, isInApp: Boolean, isSilent: Boolean, type: Int): String {
		var name: String? = name
		var vibrationPattern = vibrationPattern
		var ledColor = ledColor
		ensureGroupsCreated()
		val preferences = accountInstance.notificationsSettings
		var key: String
		val groupId: String
		val overwriteKey: String?
		val sound = Uri.parse("android.resource://" + ApplicationLoader.applicationContext.packageName + "/" + R.raw.message_notification_sound)

		if (isSilent) {
			groupId = "other$currentAccount"
			overwriteKey = null
		}
		else {
			when (type) {
				TYPE_CHANNEL -> {
					groupId = "channels$currentAccount"
					overwriteKey = "overwrite_channel"
				}

				TYPE_GROUP -> {
					groupId = "groups$currentAccount"
					overwriteKey = "overwrite_group"
				}

				else -> {
					groupId = "private$currentAccount"
					overwriteKey = "overwrite_private"
				}
			}
		}

		val secretChat = !isDefault && DialogObject.isEncryptedDialog(dialogId)
		val shouldOverwrite = !isInApp && overwriteKey != null && preferences.getBoolean(overwriteKey, false)
		var soundHash = Utilities.MD5(sound?.toString() ?: "NoSound")

		if (soundHash != null && soundHash.length > 5) {
			soundHash = soundHash.substring(0, 5)
		}

		if (isSilent) {
			name = ApplicationLoader.applicationContext.getString(R.string.NotificationsSilent)
			key = "silent"
		}
		else if (isDefault) {
			name = if (isInApp) ApplicationLoader.applicationContext.getString(R.string.NotificationsInAppDefault) else ApplicationLoader.applicationContext.getString(R.string.NotificationsDefault)

			key = if (type == TYPE_CHANNEL) {
				if (isInApp) "channels_ia" else "channels"
			}
			else if (type == TYPE_GROUP) {
				if (isInApp) "groups_ia" else "groups"
			}
			else {
				if (isInApp) "private_ia" else "private"
			}
		}
		else {
			if (isInApp) {
				name = LocaleController.formatString("NotificationsChatInApp", R.string.NotificationsChatInApp, name)
			}

			key = (if (isInApp) "com.beint.elloapp.keyia" else "com.beint.elloapp.key") + dialogId
		}

		key += "_$soundHash"

		var channelId = preferences.getString(key, null)
		var settings = preferences.getString(key + "_s", null)
		var edited = false
		val newSettings = StringBuilder()
		var newSettingsHash: String? = null

		if (channelId != null) {
			val existingChannel = systemNotificationManager?.getNotificationChannel(channelId)

			if (existingChannel != null) {
				if (!isSilent && !shouldOverwrite) {
					val channelImportance = existingChannel.importance
					val channelSound = existingChannel.sound
					var channelVibrationPattern = existingChannel.vibrationPattern
					val vibrate = existingChannel.shouldVibrate()

					if (!vibrate && channelVibrationPattern == null) {
						channelVibrationPattern = longArrayOf(0, 0)
					}

					val channelLedColor = existingChannel.lightColor

					if (channelVibrationPattern != null) {
						for (l in channelVibrationPattern) {
							newSettings.append(l)
						}
					}

					newSettings.append(channelLedColor)

					if (channelSound != null) {
						newSettings.append(channelSound)
					}

					newSettings.append(channelImportance)

					if (!isDefault && secretChat) {
						newSettings.append("secret")
					}

					newSettingsHash = Utilities.MD5(newSettings.toString())

					newSettings.setLength(0)

					if (newSettingsHash != settings) {
						var editor: SharedPreferences.Editor? = null

						if (channelImportance == NotificationManager.IMPORTANCE_NONE) {
							editor = preferences.edit()

							if (isDefault) {
								if (!isInApp) {
									editor.putInt(getGlobalNotificationsKey(type), Int.MAX_VALUE)
									updateServerNotificationsSettings(type)
								}
							}
							else {
								editor.putInt("notify2_$dialogId", 2)
								updateServerNotificationsSettings(dialogId, true)
							}

							edited = true
						}
						else if (channelImportance != importance) {
							if (!isInApp) {
								editor = preferences.edit()

								val priority = when (channelImportance) {
									NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> 1
									NotificationManager.IMPORTANCE_MIN -> 4
									NotificationManager.IMPORTANCE_LOW -> 5
									else -> 0
								}

								if (isDefault) {
									editor.putInt(getGlobalNotificationsKey(type), 0).commit()

									when (type) {
										TYPE_CHANNEL -> {
											editor.putInt("priority_channel", priority)
										}

										TYPE_GROUP -> {
											editor.putInt("priority_group", priority)
										}

										else -> {
											editor.putInt("priority_messages", priority)
										}
									}
								}
								else {
									editor.putInt("notify2_$dialogId", 0)
									editor.remove("notifyuntil_$dialogId")
									editor.putInt("priority_$dialogId", priority)
								}
							}
							edited = true
						}

						val hasVibration = !isEmptyVibration(vibrationPattern)

						if (hasVibration != vibrate) {
							if (!isInApp) {
								if (editor == null) {
									editor = preferences.edit()
								}

								if (isDefault) {
									when (type) {
										TYPE_CHANNEL -> {
											editor?.putInt("vibrate_channel", if (vibrate) 0 else 2)
										}

										TYPE_GROUP -> {
											editor?.putInt("vibrate_group", if (vibrate) 0 else 2)
										}

										else -> {
											editor?.putInt("vibrate_messages", if (vibrate) 0 else 2)
										}
									}
								}
								else {
									editor?.putInt("vibrate_$dialogId", if (vibrate) 0 else 2)
								}
							}

							vibrationPattern = channelVibrationPattern
							edited = true
						}

						if (channelLedColor != ledColor) {
							if (!isInApp) {
								if (editor == null) {
									editor = preferences.edit()
								}

								if (isDefault) {
									when (type) {
										TYPE_CHANNEL -> {
											editor?.putInt("ChannelLed", channelLedColor)
										}

										TYPE_GROUP -> {
											editor?.putInt("GroupLed", channelLedColor)
										}

										else -> {
											editor?.putInt("MessagesLed", channelLedColor)
										}
									}
								}
								else {
									editor?.putInt("color_$dialogId", channelLedColor)
								}
							}

							ledColor = channelLedColor

							edited = true
						}

						editor?.commit()
					}
				}
			}
			else {
				channelId = null
				settings = null
			}
		}

		if (edited && newSettingsHash != null) {
			preferences.edit().putString(key, channelId).putString(key + "_s", newSettingsHash).commit()
		}
		else if (shouldOverwrite || newSettingsHash == null || !isInApp || !isDefault) {
			vibrationPattern?.forEach {
				newSettings.append(it)
			}

			newSettings.append(ledColor)

			if (sound != null) {
				newSettings.append(sound)
			}

			newSettings.append(importance)

			if (!isDefault && secretChat) {
				newSettings.append("secret")
			}

			newSettingsHash = Utilities.MD5(newSettings.toString())

			if (!isSilent && channelId != null && (shouldOverwrite || settings != newSettingsHash)) {
				try {
					systemNotificationManager?.deleteNotificationChannel(channelId)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				channelId = null
			}
		}

		if (channelId == null) {
			channelId = if (isDefault) {
				currentAccount.toString() + "channel_" + key + "_" + Utilities.random.nextLong()
			}
			else {
				currentAccount.toString() + "channel_" + dialogId + "_" + Utilities.random.nextLong()
			}

			val notificationChannel = NotificationChannel(channelId, if (secretChat) ApplicationLoader.applicationContext.getString(R.string.SecretChatName) else name, importance)
			notificationChannel.group = groupId

			if (ledColor != 0) {
				notificationChannel.enableLights(true)
				notificationChannel.lightColor = ledColor
			}
			else {
				notificationChannel.enableLights(false)
			}

			if (!isEmptyVibration(vibrationPattern)) {
				notificationChannel.enableVibration(true)

				if (vibrationPattern?.isNotEmpty() == true) {
					notificationChannel.vibrationPattern = vibrationPattern
				}
			}
			else {
				notificationChannel.enableVibration(false)
			}

			val builder = AudioAttributes.Builder()
			builder.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
			builder.setUsage(AudioAttributes.USAGE_NOTIFICATION)

			if (sound != null) {
				notificationChannel.setSound(sound, builder.build())
			}
			else {
				notificationChannel.setSound(null, null)
			}

			lastNotificationChannelCreateTime = SystemClock.elapsedRealtime()

			systemNotificationManager?.createNotificationChannel(notificationChannel)

			preferences.edit().putString(key, channelId).putString(key + "_s", newSettingsHash).commit()
		}

		return channelId
	}

	private suspend fun showOrUpdateNotification(notifyAboutLast: Boolean) {
		if (!userConfig.isClientActivated || pushMessages.isEmpty() || !SharedConfig.showNotificationsForAllAccounts && currentAccount != UserConfig.selectedAccount) {
			dismissNotification()
			return
		}

		try {
			connectionsManager.resumeNetworkMaybe()

			val lastMessageObject = pushMessages[0]
			val preferences = accountInstance.notificationsSettings
			val dismissDate = preferences.getInt("dismissDate", 0)

			if (lastMessageObject.messageOwner!!.date <= dismissDate) {
				dismissNotification()
				return
			}

			val dialogId = lastMessageObject.dialogId
			var isChannel = false
			var overrideDialogId = dialogId

			if (lastMessageObject.messageOwner?.mentioned == true) {
				overrideDialogId = lastMessageObject.fromChatId
			}

			val chatId = lastMessageObject.messageOwner?.peer_id?.chat_id?.takeIf { it != 0L } ?: lastMessageObject.messageOwner?.peer_id?.channel_id ?: 0L
			var userId = lastMessageObject.messageOwner?.peer_id?.user_id ?: 0L

			if (lastMessageObject.isFromUser && (userId == 0L || userId == userConfig.getClientUserId())) {
				userId = lastMessageObject.messageOwner?.from_id?.user_id ?: 0L
			}

			val user = messagesController.getUser(userId) ?: messagesController.loadUser(userId, classGuid, true)

			var chat: Chat? = null

			if (chatId != 0L) {
				chat = messagesController.getChat(chatId) ?: messagesController.loadChat(chatId, classGuid, true)

				isChannel = if (chat == null && lastMessageObject.isFcmMessage) {
					lastMessageObject.localChannel
				}
				else {
					ChatObject.isChannel(chat) && !chat.megagroup
				}
			}

			var photoPath: FileLocation? = null
			var notifyDisabled = false
			var vibrate = 0
			var soundPath: String? = null
			var isInternalSoundFile = false
			var ledColor = -0xffff01
			var importance = 0
			val notifyOverride = getNotifyOverride(preferences, overrideDialogId)

			val value = if (notifyOverride == -1) {
				isGlobalNotificationsEnabled(dialogId, isChannel)
			}
			else {
				notifyOverride != 2
			}

			val name: String
			var replace = true

			val chatName = if ((chatId != 0L && chat == null || user == null) && lastMessageObject.isFcmMessage) {
				lastMessageObject.localName
			}
			else if (chat != null) {
				chat.title
			}
			else {
				UserObject.getUserName(user)
			}

			val passcode = AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter

			if (DialogObject.isEncryptedDialog(dialogId) || pushDialogs.size() > 1 || passcode) {
				name = if (passcode) {
					if (chatId != 0L) {
						ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenChatName)
					}
					else {
						ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenName)
					}
				}
				else {
					ApplicationLoader.applicationContext.getString(R.string.AppName)
				}

				replace = false
			}
			else {
				name = chatName ?: ""
			}

			var detailText = if (UserConfig.activatedAccountsCount > 1) {
				if (pushDialogs.size() == 1) {
					UserObject.getFirstName(userConfig.getCurrentUser())
				}
				else {
					UserObject.getFirstName(userConfig.getCurrentUser()) + "・"
				}
			}
			else {
				""
			}

			if (pushDialogs.size() != 1) {
				detailText += if (pushDialogs.size() == 1) {
					LocaleController.formatPluralString("NewMessages", totalUnreadCount)
				}
				else {
					LocaleController.formatString("NotificationMessagesPeopleDisplayOrder", R.string.NotificationMessagesPeopleDisplayOrder, LocaleController.formatPluralString("NewMessages", totalUnreadCount), LocaleController.formatPluralString("FromChats", pushDialogs.size()))
				}
			}

			val mBuilder = NotificationCompat.Builder(ApplicationLoader.applicationContext)
			var silent = 2
			var lastMessage: String? = null

			if (pushMessages.size == 1) {
				val messageObject = pushMessages[0]
				val text = BooleanArray(1)

				lastMessage = getStringForMessage(messageObject, false, text, null)

				var message = lastMessage

				silent = if (isSilentMessage(messageObject)) 1 else 0

				if (message == null) {
					return
				}
				if (replace) {
					message = if (chat != null) {
						message.replace(" @ $name", "")
					}
					else {
						if (text[0]) {
							message.replace("$name: ", "")
						}
						else {
							message.replace("$name ", "")
						}
					}
				}

				mBuilder.setContentText(message)
				mBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
			}
			else {
				mBuilder.setContentText(detailText)

				val inboxStyle = NotificationCompat.InboxStyle()
				inboxStyle.setBigContentTitle(name)

				val count = min(10, pushMessages.size)
				val text = BooleanArray(1)

				for (i in 0 until count) {
					val messageObject = pushMessages[i]
					var message = getStringForMessage(messageObject, false, text, null)

					if (message == null || messageObject.messageOwner!!.date <= dismissDate) {
						continue
					}

					if (silent == 2) {
						lastMessage = message
						silent = if (isSilentMessage(messageObject)) 1 else 0
					}

					if (pushDialogs.size() == 1) {
						if (replace) {
							message = if (chat != null) {
								message.replace(" @ $name", "")
							}
							else {
								if (text[0]) {
									message.replace("$name: ", "")
								}
								else {
									message.replace("$name ", "")
								}
							}
						}
					}

					inboxStyle.addLine(message)
				}

				inboxStyle.setSummaryText(detailText)

				mBuilder.setStyle(inboxStyle)
			}

			if (!notifyAboutLast || !value || MediaController.getInstance().isRecordingAudio || silent == 1) {
				notifyDisabled = true
			}

			if (!notifyDisabled && dialogId == overrideDialogId && chat != null) {
				val notifyMaxCount: Int
				val notifyDelay: Int

				if (preferences.getBoolean("custom_$dialogId", false)) {
					notifyMaxCount = preferences.getInt("smart_max_count_$dialogId", 2)
					notifyDelay = preferences.getInt("smart_delay_$dialogId", 3 * 60)
				}
				else {
					notifyMaxCount = 2
					notifyDelay = 3 * 60
				}

				if (notifyMaxCount != 0) {
					var dialogInfo = smartNotificationsDialogs[dialogId]

					if (dialogInfo == null) {
						dialogInfo = Point(1, (SystemClock.elapsedRealtime() / 1000).toInt())

						smartNotificationsDialogs.put(dialogId, dialogInfo)
					}
					else {
						val lastTime = dialogInfo.y

						if (lastTime + notifyDelay < SystemClock.elapsedRealtime() / 1000) {
							dialogInfo[1] = (SystemClock.elapsedRealtime() / 1000).toInt()
						}
						else {
							val count = dialogInfo.x

							if (count < notifyMaxCount) {
								dialogInfo[count + 1] = (SystemClock.elapsedRealtime() / 1000).toInt()
							}
							else {
								notifyDisabled = true
							}
						}
					}
				}
			}

			if (!notifyDisabled && !preferences.getBoolean("sound_enabled_$dialogId", true)) {
				notifyDisabled = true
			}

			val defaultPath = Settings.System.DEFAULT_NOTIFICATION_URI.path
			var isDefault = true
			val isInApp = !ApplicationLoader.mainInterfacePaused
			var chatType = TYPE_PRIVATE
			val customSoundPath: String?
			var customIsInternalSound = false
			val customVibrate: Int
			val customImportance: Int
			val customLedColor: Int?

			if (preferences.getBoolean("custom_$dialogId", false)) {
				customVibrate = preferences.getInt("vibrate_$dialogId", 0)
				customImportance = preferences.getInt("priority_$dialogId", 3)

				val soundDocumentId = preferences.getLong("sound_document_id_$dialogId", 0)

				if (soundDocumentId != 0L) {
					customIsInternalSound = true
					customSoundPath = mediaDataController.ringtoneDataStore.getSoundPath(soundDocumentId)
				}
				else {
					customSoundPath = preferences.getString("sound_path_$dialogId", null)
				}

				customLedColor = if (preferences.contains("color_$dialogId")) {
					preferences.getInt("color_$dialogId", 0)
				}
				else {
					null
				}
			}
			else {
				customVibrate = 0
				customImportance = 3
				customSoundPath = null
				customLedColor = null
			}

			var vibrateOnlyIfSilent = false

			if (chatId != 0L) {
				if (isChannel) {
					val soundDocumentId = preferences.getLong("ChannelSoundDocId", 0)

					if (soundDocumentId != 0L) {
						isInternalSoundFile = true
						soundPath = mediaDataController.ringtoneDataStore.getSoundPath(soundDocumentId)
					}
					else {
						soundPath = preferences.getString("ChannelSoundPath", defaultPath)
					}

					vibrate = preferences.getInt("vibrate_channel", 0)
					importance = preferences.getInt("priority_channel", 1)
					ledColor = preferences.getInt("ChannelLed", -0xffff01)

					chatType = TYPE_CHANNEL
				}
				else {
					val soundDocumentId = preferences.getLong("GroupSoundDocId", 0)

					if (soundDocumentId != 0L) {
						isInternalSoundFile = true
						soundPath = mediaDataController.ringtoneDataStore.getSoundPath(soundDocumentId)
					}
					else {
						soundPath = preferences.getString("GroupSoundPath", defaultPath)
					}

					vibrate = preferences.getInt("vibrate_group", 0)
					importance = preferences.getInt("priority_group", 1)
					ledColor = preferences.getInt("GroupLed", -0xffff01)

					chatType = TYPE_GROUP
				}
			}
			else if (userId != 0L) {
				val soundDocumentId = preferences.getLong("GlobalSoundDocId", 0)

				if (soundDocumentId != 0L) {
					isInternalSoundFile = true
					soundPath = mediaDataController.ringtoneDataStore.getSoundPath(soundDocumentId)
				}
				else {
					soundPath = preferences.getString("GlobalSoundPath", defaultPath)
				}

				vibrate = preferences.getInt("vibrate_messages", 0)
				importance = preferences.getInt("priority_messages", 1)
				ledColor = preferences.getInt("MessagesLed", -0xffff01)

				chatType = TYPE_PRIVATE
			}

			if (vibrate == 4) {
				vibrateOnlyIfSilent = true
				vibrate = 0
			}

			if (!customSoundPath.isNullOrEmpty() && !TextUtils.equals(soundPath, customSoundPath)) {
				isInternalSoundFile = customIsInternalSound
				soundPath = customSoundPath
				isDefault = false
			}

			if (customImportance != 3 && importance != customImportance) {
				importance = customImportance
				isDefault = false
			}

			if (customLedColor != null && customLedColor != ledColor) {
				ledColor = customLedColor
				isDefault = false
			}

			if (customVibrate != 0 && customVibrate != 4 && customVibrate != vibrate) {
				vibrate = customVibrate
				isDefault = false
			}

			if (isInApp) {
				if (!preferences.getBoolean("EnableInAppSounds", true)) {
					soundPath = null
				}

				if (!preferences.getBoolean("EnableInAppVibrate", true)) {
					vibrate = 2
				}

				if (!preferences.getBoolean("EnableInAppPriority", false)) {
					importance = 0
				}
				else if (importance == 2) {
					importance = 1
				}
			}

			if (vibrateOnlyIfSilent && vibrate != 2) {
				try {
					val mode = audioManager?.ringerMode

					if (mode != AudioManager.RINGER_MODE_SILENT && mode != AudioManager.RINGER_MODE_VIBRATE) {
						vibrate = 2
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			if (notifyDisabled) {
				vibrate = 0
				importance = 0
				ledColor = 0
				soundPath = null
			}

			val intent = Intent(ApplicationLoader.applicationContext, LaunchActivity::class.java)
			intent.setAction("com.tmessages.openchat" + Math.random() + Int.MAX_VALUE)
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

			//intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

			if (!DialogObject.isEncryptedDialog(dialogId)) {
				if (pushDialogs.size() == 1) {
					if (chatId != 0L) {
						intent.putExtra("chatId", chatId)
					}
					else if (userId != 0L) {
						intent.putExtra("userId", userId)
					}
				}

				if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
					photoPath = null
				}
				else {
					if (pushDialogs.size() == 1 && Build.VERSION.SDK_INT < 28) {
						if (chat != null) {
							if (chat.photo?.photo_small?.volume_id != 0L && chat.photo?.photo_small?.local_id != 0) {
								photoPath = chat.photo?.photo_small
							}
						}
						else if (user != null) {
							if (user.photo?.photo_small?.volume_id != 0L && user.photo?.photo_small?.local_id != 0) {
								photoPath = user.photo?.photo_small
							}
						}
					}
				}
			}
			else {
				if (pushDialogs.size() == 1 && dialogId != globalSecretChatId) {
					intent.putExtra("encId", DialogObject.getEncryptedChatId(dialogId))
				}
			}

			intent.putExtra("currentAccount", currentAccount)

			val contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)

			mBuilder.setContentTitle(name).setSmallIcon(R.drawable.notification).setAutoCancel(true).setNumber(totalUnreadCount).setContentIntent(contentIntent).setGroup(notificationGroup).setGroupSummary(true).setShowWhen(true).setWhen(lastMessageObject.messageOwner!!.date.toLong() * 1000).setColor(ApplicationLoader.applicationContext.getColor(R.color.brand))

			var vibrationPattern: LongArray? = null
			var sound: Uri? = null

			mBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE)

			val dismissIntent = Intent(ApplicationLoader.applicationContext, NotificationDismissReceiver::class.java)
			dismissIntent.putExtra("messageDate", lastMessageObject.messageOwner!!.date)
			dismissIntent.putExtra("currentAccount", currentAccount)

			mBuilder.setDeleteIntent(PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 1, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

			if (photoPath != null) {
				val img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50")

				if (img != null) {
					mBuilder.setLargeIcon(img.bitmap)
				}
				else {
					runCatching {
						val file = fileLoader.getPathToAttach(photoPath, true)

						if (file.exists()) {
							val scaleFactor = 160.0f / AndroidUtilities.dp(50f)

							val options = BitmapFactory.Options()
							options.inSampleSize = if (scaleFactor < 1) 1 else scaleFactor.toInt()

							val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)

							if (bitmap != null) {
								mBuilder.setLargeIcon(bitmap)
							}
						}
					}
				}
			}

			var configImportance = 0

			if (!notifyAboutLast || silent == 1) {
				mBuilder.setPriority(NotificationCompat.PRIORITY_LOW)

				if (Build.VERSION.SDK_INT >= 26) {
					configImportance = NotificationManager.IMPORTANCE_LOW
				}
			}
			else {
				if (importance == 0) {
					mBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT)

					if (Build.VERSION.SDK_INT >= 26) {
						configImportance = NotificationManager.IMPORTANCE_DEFAULT
					}
				}
				else if (importance == 1 || importance == 2) {
					mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH)

					if (Build.VERSION.SDK_INT >= 26) {
						configImportance = NotificationManager.IMPORTANCE_HIGH
					}
				}
				else if (importance == 4) {
					mBuilder.setPriority(NotificationCompat.PRIORITY_MIN)

					if (Build.VERSION.SDK_INT >= 26) {
						configImportance = NotificationManager.IMPORTANCE_MIN
					}
				}
				else if (importance == 5) {
					mBuilder.setPriority(NotificationCompat.PRIORITY_LOW)

					if (Build.VERSION.SDK_INT >= 26) {
						configImportance = NotificationManager.IMPORTANCE_LOW
					}
				}
			}

			if (silent != 1 && !notifyDisabled) {
				if (!isInApp || preferences.getBoolean("EnableInAppPreview", true)) {
					if (!lastMessage.isNullOrEmpty()) {
						if (lastMessage.length > 100) {
							lastMessage = lastMessage.substring(0, 100).replace('\n', ' ').trim() + "…"
						}
					}

					mBuilder.setTicker(lastMessage)
				}

				if (soundPath != null && soundPath != "NoSound") {
					if (Build.VERSION.SDK_INT >= 26) {
						if (soundPath == "Default" || soundPath == defaultPath) {
							sound = Settings.System.DEFAULT_NOTIFICATION_URI
						}
						else {
							if (isInternalSoundFile) {
								sound = FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.applicationId + ".provider", File(soundPath))
								ApplicationLoader.applicationContext.grantUriPermission("com.android.systemui", sound, Intent.FLAG_GRANT_READ_URI_PERMISSION)
							}
							else {
								sound = Uri.parse(soundPath)
							}
						}
					}
					else {
						if (soundPath == defaultPath) {
							mBuilder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioManager.STREAM_NOTIFICATION)
						}
						else {
							if (Build.VERSION.SDK_INT >= 24 && soundPath.startsWith("file://") && !AndroidUtilities.isInternalUri(Uri.parse(soundPath))) {
								try {
									val uri = FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.applicationId + ".provider", File(soundPath.replace("file://", "")))
									ApplicationLoader.applicationContext.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
									mBuilder.setSound(uri, AudioManager.STREAM_NOTIFICATION)
								}
								catch (e: Exception) {
									mBuilder.setSound(Uri.parse(soundPath), AudioManager.STREAM_NOTIFICATION)
								}
							}
							else {
								mBuilder.setSound(Uri.parse(soundPath), AudioManager.STREAM_NOTIFICATION)
							}
						}
					}
				}

				if (ledColor != 0) {
					mBuilder.setLights(ledColor, 1000, 1000)
				}

				when (vibrate) {
					2 -> {
						mBuilder.setVibrate(longArrayOf(0, 0).also { vibrationPattern = it })
					}

					1 -> {
						mBuilder.setVibrate(longArrayOf(0, 100, 0, 100).also { vibrationPattern = it })
					}

					0, 4 -> {
						mBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
						vibrationPattern = longArrayOf()
					}

					3 -> {
						mBuilder.setVibrate(longArrayOf(0, 1000).also { vibrationPattern = it })
					}
				}
			}
			else {
				mBuilder.setVibrate(longArrayOf(0, 0).also { vibrationPattern = it })
			}

			var hasCallback = false

			if (!AndroidUtilities.needShowPasscode() && !SharedConfig.isWaitingForPasscodeEnter && lastMessageObject.dialogId == 777000L) {
				if (lastMessageObject.messageOwner?.reply_markup != null) {
					val rows = lastMessageObject.messageOwner?.reply_markup?.rows ?: emptyList()
					var a = 0
					val size = rows.size

					while (a < size) {
						val row = rows[a]
						var b = 0
						val size2 = row.buttons.size

						while (b < size2) {
							val button = row.buttons[b]

							if (button is TL_keyboardButtonCallback) {
								val callbackIntent = Intent(ApplicationLoader.applicationContext, NotificationCallbackReceiver::class.java)
								callbackIntent.putExtra("currentAccount", currentAccount)
								callbackIntent.putExtra("did", dialogId)

								if (button.data != null) {
									callbackIntent.putExtra("data", button.data)
								}

								callbackIntent.putExtra("mid", lastMessageObject.id)

								mBuilder.addAction(0, button.text, PendingIntent.getBroadcast(ApplicationLoader.applicationContext, lastButtonId++, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

								hasCallback = true
							}

							b++
						}

						a++
					}
				}
			}

			if (!hasCallback && Build.VERSION.SDK_INT < 24 && SharedConfig.passcodeHash.isEmpty() && hasMessagesToReply()) {
				val replyIntent = Intent(ApplicationLoader.applicationContext, PopupReplyReceiver::class.java)
				replyIntent.putExtra("currentAccount", currentAccount)

				mBuilder.addAction(R.drawable.ic_ab_reply, ApplicationLoader.applicationContext.getString(R.string.Reply), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 2, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
			}

			showExtraNotifications(mBuilder, detailText, dialogId, chatName, vibrationPattern, ledColor, sound, configImportance, isDefault, isInApp, notifyDisabled, chatType)

			scheduleNotificationRepeat()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun isSilentMessage(messageObject: MessageObject): Boolean {
		return messageObject.messageOwner?.silent == true || messageObject.isReactionPush
	}

	@SuppressLint("NewApi")
	private fun setNotificationChannel(mainNotification: Notification, builder: NotificationCompat.Builder, useSummaryNotification: Boolean) {
		if (useSummaryNotification) {
			OTHER_NOTIFICATIONS_CHANNEL?.let {
				builder.setChannelId(it)
			}
		}
		else {
			builder.setChannelId(mainNotification.channelId)
		}
	}

	private fun resetNotificationSound(notificationBuilder: NotificationCompat.Builder, dialogId: Long, chatName: String?, vibrationPattern: LongArray?, ledColor: Int, sound: Uri?, importance: Int, isDefault: Boolean, isInApp: Boolean, isSilent: Boolean, chatType: Int) {
		var sound = sound
		val defaultSound = Settings.System.DEFAULT_RINGTONE_URI

		if (defaultSound != null && sound != null && !TextUtils.equals(defaultSound.toString(), sound.toString())) {
			val preferences = accountInstance.notificationsSettings
			val editor = preferences.edit()
			val newSound = defaultSound.toString()
			val ringtoneName = ApplicationLoader.applicationContext.getString(R.string.DefaultRingtone)

			if (isDefault) {
				when (chatType) {
					TYPE_CHANNEL -> {
						editor.putString("ChannelSound", ringtoneName)
					}

					TYPE_GROUP -> {
						editor.putString("GroupSound", ringtoneName)
					}

					else -> {
						editor.putString("GlobalSound", ringtoneName)
					}
				}

				when (chatType) {
					TYPE_CHANNEL -> {
						editor.putString("ChannelSoundPath", newSound)
					}

					TYPE_GROUP -> {
						editor.putString("GroupSoundPath", newSound)
					}

					else -> {
						editor.putString("GlobalSoundPath", newSound)
					}
				}

				notificationsController.deleteNotificationChannelGlobalInternal(chatType, -1)
			}
			else {
				editor.putString("sound_$dialogId", ringtoneName)
				editor.putString("sound_path_$dialogId", newSound)

				deleteNotificationChannelInternal(dialogId, -1)
			}

			editor.commit()

			sound = Settings.System.DEFAULT_RINGTONE_URI

			notificationBuilder.setChannelId(validateChannelId(dialogId, chatName, vibrationPattern, ledColor, importance, isDefault, isInApp, isSilent, chatType))

			if (ActivityCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
				// TODO: Consider calling
				//    ActivityCompat#requestPermissions
				// here to request the missing permissions, and then overriding
				//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
				//                                          int[] grantResults)
				// to handle the case where the user grants the permission. See the documentation
				// for ActivityCompat#requestPermissions for more details.
				return
			}

			notificationManager?.notify(notificationId, notificationBuilder.build())
		}
	}

	@SuppressLint("InlinedApi")
	private suspend fun showExtraNotifications(notificationBuilder: NotificationCompat.Builder, summary: String?, lastDialogId: Long, chatName: String?, vibrationPattern: LongArray?, ledColor: Int, sound: Uri?, importance: Int, isDefault: Boolean, isInApp: Boolean, isSilent: Boolean, chatType: Int) {
		if (Build.VERSION.SDK_INT >= 26) {
			notificationBuilder.setChannelId(validateChannelId(lastDialogId, chatName, vibrationPattern, ledColor, importance, isDefault, isInApp, isSilent, chatType))
		}

		val mainNotification = notificationBuilder.build()
		val preferences = accountInstance.notificationsSettings
		val sortedDialogs = ArrayList<Long>()
		val messagesByDialogs = LongSparseArray<ArrayList<MessageObject>>()

		for (a in pushMessages.indices) {
			val messageObject = pushMessages[a]
			val dialogId = messageObject.dialogId
			val dismissDate = preferences.getInt("dismissDate$dialogId", 0)

			if (messageObject.messageOwner!!.date <= dismissDate) {
				continue
			}

			var arrayList = messagesByDialogs[dialogId]

			if (arrayList == null) {
				arrayList = ArrayList()
				messagesByDialogs.put(dialogId, arrayList)
				sortedDialogs.add(dialogId)
			}

			arrayList.add(messageObject)
		}

		val oldIdsWear = LongSparseArray<Int>()

		for (i in 0 until wearNotificationsIds.size()) {
			oldIdsWear.put(wearNotificationsIds.keyAt(i), wearNotificationsIds.valueAt(i))
		}

		wearNotificationsIds.clear()

		class NotificationHolder(val id: Int, val dialogId: Long, val name: String, val user: User?, val chat: Chat?, val notification: NotificationCompat.Builder) {
			fun call() {
				try {
					notificationManager?.notify(id, notification.build())
				}
				catch (e: SecurityException) {
					FileLog.e(e)
					resetNotificationSound(notification, dialogId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType)
				}
			}
		}

		val holders = ArrayList<NotificationHolder>()
		val useSummaryNotification = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 || sortedDialogs.size > 1

		if (useSummaryNotification && Build.VERSION.SDK_INT >= 26) {
			checkOtherNotificationsChannel()
		}

		val selfUserId = userConfig.getClientUserId()
		val waitingForPasscode = AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter
		val maxCount = 7
		val personCache = LongSparseArray<Person>()

		run {
			var b = 0
			val size = sortedDialogs.size

			while (b < size) {
				if (holders.size >= maxCount) {
					break
				}

				val dialogId = sortedDialogs[b]
				val messageObjects = messagesByDialogs[dialogId]!!
				val maxId = messageObjects[0].id
				var internalId = oldIdsWear[dialogId]

				if (internalId == null) {
					internalId = dialogId.toInt() + (dialogId shr 32).toInt()
				}
				else {
					oldIdsWear.remove(dialogId)
				}

				val lastMessageObject = messageObjects[0]
				var maxDate = 0

				for (i in messageObjects.indices) {
					if (maxDate < messageObjects[i].messageOwner!!.date) {
						maxDate = messageObjects[i].messageOwner!!.date
					}
				}

				var chat: Chat? = null
				var user: User? = null
				var isChannel = false
				var isSupergroup = false
				var name: String?
				var photoPath: FileLocation? = null
				var avatarBitmap: Bitmap? = null
				var avatarFile: File? = null
				var canReply: Boolean

				if (!DialogObject.isEncryptedDialog(dialogId)) {
					canReply = dialogId != BuildConfig.NOTIFICATIONS_BOT_ID

					if (DialogObject.isUserDialog(dialogId)) {
						user = messagesController.getUser(dialogId) ?: messagesController.loadUser(dialogId, classGuid, true)

						if (user == null) {
							name = if (lastMessageObject.isFcmMessage) {
								lastMessageObject.localName
							}
							else {
								b++
								continue
							}
						}
						else {
							name = UserObject.getUserName(user)

							if (user.photo?.photo_small?.volume_id != 0L && user.photo?.photo_small?.local_id != 0) {
								photoPath = user.photo?.photo_small
							}
						}

						if (UserObject.isReplyUser(dialogId)) {
							name = ApplicationLoader.applicationContext.getString(R.string.RepliesTitle)
						}
						else if (dialogId == selfUserId) {
							name = ApplicationLoader.applicationContext.getString(R.string.MessageScheduledReminderNotification)
						}
					}
					else {
						chat = messagesController.getChat(-dialogId) ?: messagesController.loadChat(-dialogId, classGuid, true)

						if (chat == null) {
							if (lastMessageObject.isFcmMessage) {
								isSupergroup = lastMessageObject.isSupergroup
								name = lastMessageObject.localName
								isChannel = lastMessageObject.localChannel
							}
							else {
								b++
								continue
							}
						}
						else {
							isSupergroup = chat.megagroup
							isChannel = ChatObject.isChannel(chat) && !chat.megagroup
							name = chat.title

							if (chat.photo?.photo_small?.volume_id != 0L && chat.photo?.photo_small?.local_id != 0) {
								photoPath = chat.photo?.photo_small
							}
						}
					}
				}
				else {
					canReply = false

					if (dialogId != globalSecretChatId) {
						val encryptedChatId = DialogObject.getEncryptedChatId(dialogId)
						val encryptedChat = messagesController.getEncryptedChat(encryptedChatId)

						if (encryptedChat == null) {
							b++
							continue
						}

						user = messagesController.getUser(encryptedChat.user_id) ?: messagesController.loadUser(encryptedChat.user_id, classGuid, true)

						if (user == null) {
							b++
							continue
						}
					}

					name = ApplicationLoader.applicationContext.getString(R.string.SecretChatName)
					photoPath = null
				}

				if (waitingForPasscode) {
					name = if (DialogObject.isChatDialog(dialogId)) {
						ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenChatName)
					}
					else {
						ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenName)
					}

					photoPath = null
					canReply = false
				}

				if (photoPath != null) {
					avatarFile = fileLoader.getPathToAttach(photoPath, true)

					if (Build.VERSION.SDK_INT < 28) {
						val img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50")

						if (img != null) {
							avatarBitmap = img.bitmap
						}
						else {
							runCatching {
								if (avatarFile.exists()) {
									val scaleFactor = 160.0f / AndroidUtilities.dp(50f)

									val options = BitmapFactory.Options()
									options.inSampleSize = if (scaleFactor < 1) 1 else scaleFactor.toInt()

									avatarBitmap = BitmapFactory.decodeFile(avatarFile.absolutePath, options)
								}
							}
						}
					}
				}

				if (chat != null) {
					val personBuilder = Person.Builder().setName(name)

					if (avatarFile != null && avatarFile.exists() && Build.VERSION.SDK_INT >= 28) {
						loadRoundAvatar(avatarFile, personBuilder)
					}

					personCache.put(-chat.id, personBuilder.build())
				}

				var wearReplyAction: NotificationCompat.Action? = null

				if ((!isChannel || isSupergroup) && canReply && !SharedConfig.isWaitingForPasscodeEnter && selfUserId != dialogId && !UserObject.isReplyUser(dialogId)) {
					val replyIntent = Intent(ApplicationLoader.applicationContext, WearReplyReceiver::class.java)
					replyIntent.putExtra("dialog_id", dialogId)
					replyIntent.putExtra("max_id", maxId)
					replyIntent.putExtra("currentAccount", currentAccount)

					val replyPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
					val remoteInputWear = RemoteInput.Builder(EXTRA_VOICE_REPLY).setLabel(ApplicationLoader.applicationContext.getString(R.string.Reply)).build()

					val replyToString = if (DialogObject.isChatDialog(dialogId)) {
						LocaleController.formatString("ReplyToGroup", R.string.ReplyToGroup, name)
					}
					else {
						LocaleController.formatString("ReplyToUser", R.string.ReplyToUser, name)
					}

					wearReplyAction = NotificationCompat.Action.Builder(R.drawable.ic_reply_icon, replyToString, replyPendingIntent).setAllowGeneratedReplies(true).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).addRemoteInput(remoteInputWear).setShowsUserInterface(false).build()
				}

				var count = pushDialogs[dialogId]

				if (count == null) {
					count = 0
				}

				val n = max(count, messageObjects.size)

				val conversationName = if (n <= 1 || Build.VERSION.SDK_INT >= 28) {
					name
				}
				else {
					String.format(Locale.getDefault(), "%1\$s (%2\$d)", name, n)
				}

				var selfPerson = personCache[selfUserId]

				if (Build.VERSION.SDK_INT >= 28 && selfPerson == null) {
					var sender = messagesController.getUser(selfUserId)

					if (sender == null) {
						sender = userConfig.getCurrentUser()
					}

					try {
						if (sender?.photo?.photo_small != null && sender.photo?.photo_small?.volume_id != 0L && sender.photo?.photo_small?.local_id != 0) {
							val personBuilder = Person.Builder().setName(ApplicationLoader.applicationContext.getString(R.string.FromYou))
							val avatar = fileLoader.getPathToAttach(sender.photo?.photo_small, true)

							loadRoundAvatar(avatar, personBuilder)

							selfPerson = personBuilder.build()

							personCache.put(selfUserId, selfPerson)
						}
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}

				val needAddPerson = lastMessageObject.messageOwner?.action !is TL_messageActionChatJoinedByRequest

				val messagingStyle = if (selfPerson != null && needAddPerson) {
					NotificationCompat.MessagingStyle(selfPerson)
				}
				else {
					NotificationCompat.MessagingStyle("")
				}

				if (Build.VERSION.SDK_INT < 28 || DialogObject.isChatDialog(dialogId) && !isChannel || UserObject.isReplyUser(dialogId)) {
					messagingStyle.setConversationTitle(conversationName)
				}

				messagingStyle.setGroupConversation(Build.VERSION.SDK_INT < 28 || !isChannel && DialogObject.isChatDialog(dialogId) || UserObject.isReplyUser(dialogId))

				val text = StringBuilder()
				val senderName = arrayOfNulls<String>(1)
				val preview = BooleanArray(1)
				var rows: ArrayList<TL_keyboardButtonRow>? = null
				var rowsMid = 0

				for (a in messageObjects.indices.reversed()) {
					val messageObject = messageObjects[a]
					var message = getShortStringForMessage(messageObject, senderName, preview)

					if (dialogId == selfUserId) {
						senderName[0] = name
					}
					else if (DialogObject.isChatDialog(dialogId) && messageObject.messageOwner?.from_scheduled == true) {
						senderName[0] = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageScheduledName)
					}

					if (message == null) {
						continue
					}

					if (text.isNotEmpty()) {
						text.append("\n\n")
					}

					if (dialogId != selfUserId && messageObject.messageOwner?.from_scheduled == true && DialogObject.isUserDialog(dialogId)) {
						message = String.format("%1\$s: %2\$s", ApplicationLoader.applicationContext.getString(R.string.NotificationMessageScheduledName), message)
						text.append(message)
					}
					else {
						if (senderName[0] != null) {
							text.append(String.format("%1\$s: %2\$s", senderName[0], message))
						}
						else {
							text.append(message)
						}
					}

					val uid = if (DialogObject.isUserDialog(dialogId)) {
						dialogId
					}
					else if (isChannel) {
						-dialogId
					}
					else if (DialogObject.isChatDialog(dialogId)) {
						messageObject.senderId
					}
					else {
						dialogId
					}

					var person = personCache[uid]
					var personName: String? = ""

					if (senderName[0] == null) {
						if (waitingForPasscode) {
							if (DialogObject.isChatDialog(dialogId)) {
								if (isChannel) {
									if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
										personName = ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenChatName)
									}
								}
								else {
									personName = ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenChatUserName)
								}
							}
							else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
								personName = ApplicationLoader.applicationContext.getString(R.string.NotificationHiddenName)
							}
						}
					}
					else {
						personName = senderName[0]
					}

					if (person == null || !TextUtils.equals(person.name, personName)) {
						val personBuilder = Person.Builder().setName(personName)

						if (preview[0] && !DialogObject.isEncryptedDialog(dialogId) && Build.VERSION.SDK_INT >= 28) {
							var avatar: File? = null

							if (DialogObject.isUserDialog(dialogId) || isChannel) {
								avatar = avatarFile
							}
							else {
								val fromId = messageObject.senderId
								var sender = messagesController.getUser(fromId)

								if (sender == null) {
									sender = messagesStorage.getUserSync(fromId)

									if (sender != null) {
										messagesController.putUser(sender, true)
									}
								}

								if (sender?.photo?.photo_small != null && sender.photo?.photo_small?.volume_id != 0L && sender.photo?.photo_small?.local_id != 0) {
									avatar = fileLoader.getPathToAttach(sender.photo?.photo_small, true)
								}
							}

							loadRoundAvatar(avatar, personBuilder)
						}

						person = personBuilder.build()

						personCache.put(uid, person)
					}

					if (!DialogObject.isEncryptedDialog(dialogId)) {
						var setPhoto = false

						if (preview[0] && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !(ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice) {
							if (!waitingForPasscode && !messageObject.isSecretMedia && (messageObject.type == MessageObject.TYPE_PHOTO || messageObject.isSticker)) {
								val attach = fileLoader.getPathToMessage(messageObject.messageOwner)
								val msg = NotificationCompat.MessagingStyle.Message(message, messageObject.messageOwner!!.date.toLong() * 1000L, person)
								val mimeType = if (messageObject.isSticker) "image/webp" else "image/jpeg"

								val uri = if (attach.exists()) {
									try {
										FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.applicationId + ".provider", attach)
									}
									catch (e: Exception) {
										FileLog.e(e)
										null
									}
								}
								else if (fileLoader.isLoadingFile(attach.name)) {
									Uri.Builder().apply {
										scheme("content")
										authority(NotificationImageProvider.getAuthority())
										appendPath("msg_media_raw")
										appendPath(currentAccount.toString() + "")
										appendPath(attach.name)
										appendQueryParameter("final_path", attach.absolutePath)
									}.build()
								}
								else {
									null
								}

								if (uri != null) {
									msg.setData(mimeType, uri)
									messagingStyle.addMessage(msg)

									val uriFinal: Uri = uri

									ApplicationLoader.applicationContext.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

									AndroidUtilities.runOnUIThread({
										ApplicationLoader.applicationContext.revokeUriPermission(uriFinal, Intent.FLAG_GRANT_READ_URI_PERMISSION)
									}, 20000)

									if (!messageObject.caption.isNullOrEmpty()) {
										messagingStyle.addMessage(messageObject.caption, messageObject.messageOwner!!.date.toLong() * 1000, person)
									}

									setPhoto = true
								}
							}
						}

						if (!setPhoto) {
							messagingStyle.addMessage(message, messageObject.messageOwner!!.date.toLong() * 1000, person)
						}

						if (preview[0] && !waitingForPasscode && messageObject.isVoice) {
							val messages = messagingStyle.messages

							if (messages.isNotEmpty()) {
								val f = fileLoader.getPathToMessage(messageObject.messageOwner)

								val uri = if (Build.VERSION.SDK_INT >= 24) {
									try {
										FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.applicationId + ".provider", f)
									}
									catch (ignore: Exception) {
										null
									}
								}
								else {
									Uri.fromFile(f)
								}

								if (uri != null) {
									val addedMessage = messages[messages.size - 1]
									addedMessage.setData("audio/ogg", uri)
								}
							}
						}
					}
					else {
						messagingStyle.addMessage(message, messageObject.messageOwner!!.date.toLong() * 1000, person)
					}

					if (dialogId == BuildConfig.NOTIFICATIONS_BOT_ID && messageObject.messageOwner?.reply_markup != null) {
						rows = messageObject.messageOwner?.reply_markup?.rows
						rowsMid = messageObject.id
					}
				}

				val intent = Intent(ApplicationLoader.applicationContext, LaunchActivity::class.java)
				intent.setAction("com.tmessages.openchat" + Math.random() + Int.MAX_VALUE)
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
				intent.addCategory(Intent.CATEGORY_LAUNCHER)

				if (DialogObject.isEncryptedDialog(dialogId)) {
					intent.putExtra("encId", DialogObject.getEncryptedChatId(dialogId))
				}
				else if (DialogObject.isUserDialog(dialogId)) {
					intent.putExtra("userId", dialogId)
				}
				else {
					intent.putExtra("chatId", -dialogId)
				}

				intent.putExtra("currentAccount", currentAccount)

				val contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
				val wearableExtender = NotificationCompat.WearableExtender()

				if (wearReplyAction != null) {
					wearableExtender.addAction(wearReplyAction)
				}

				val msgHeardIntent = Intent(ApplicationLoader.applicationContext, AutoMessageHeardReceiver::class.java)
				msgHeardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
				msgHeardIntent.setAction("org.telegram.messenger.ACTION_MESSAGE_HEARD")
				msgHeardIntent.putExtra("dialog_id", dialogId)
				msgHeardIntent.putExtra("max_id", maxId)
				msgHeardIntent.putExtra("currentAccount", currentAccount)

				val readPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, msgHeardIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
				val readAction = NotificationCompat.Action.Builder(R.drawable.msg_markread, ApplicationLoader.applicationContext.getString(R.string.MarkAsRead), readPendingIntent).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build()

				val dismissalID = if (!DialogObject.isEncryptedDialog(dialogId)) {
					if (DialogObject.isUserDialog(dialogId)) {
						"tguser" + dialogId + "_" + maxId
					}
					else {
						"tgchat" + -dialogId + "_" + maxId
					}
				}
				else if (dialogId != globalSecretChatId) {
					"tgenc" + DialogObject.getEncryptedChatId(dialogId) + "_" + maxId
				}
				else {
					null
				}

				if (dismissalID != null) {
					wearableExtender.setDismissalId(dismissalID)

					val summaryExtender = NotificationCompat.WearableExtender()
					summaryExtender.setDismissalId("summary_$dismissalID")

					notificationBuilder.extend(summaryExtender)
				}

				wearableExtender.setBridgeTag("tgaccount$selfUserId")

				val date = messageObjects[0].messageOwner!!.date.toLong() * 1000
				val builder = NotificationCompat.Builder(ApplicationLoader.applicationContext).setContentTitle(name).setSmallIcon(R.drawable.notification).setContentText(text.toString()).setAutoCancel(true).setNumber(messageObjects.size).setColor(ApplicationLoader.applicationContext.getColor(R.color.brand)).setGroupSummary(false).setWhen(date).setShowWhen(true).setStyle(messagingStyle).setContentIntent(contentIntent).extend(wearableExtender).setSortKey((Long.MAX_VALUE - date).toString()).setCategory(NotificationCompat.CATEGORY_MESSAGE)

				val dismissIntent = Intent(ApplicationLoader.applicationContext, NotificationDismissReceiver::class.java)
				dismissIntent.putExtra("messageDate", maxDate)
				dismissIntent.putExtra("dialogId", dialogId)
				dismissIntent.putExtra("currentAccount", currentAccount)

				builder.setDeleteIntent(PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

				if (useSummaryNotification) {
					builder.setGroup(notificationGroup)
					builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
				}

				if (wearReplyAction != null) {
					builder.addAction(wearReplyAction)
				}

				if (!waitingForPasscode) {
					builder.addAction(readAction)
				}

				if (sortedDialogs.size == 1 && !summary.isNullOrEmpty()) {
					builder.setSubText(summary)
				}

				if (DialogObject.isEncryptedDialog(dialogId)) {
					builder.setLocalOnly(true)
				}

				if (avatarBitmap != null) {
					builder.setLargeIcon(avatarBitmap)
				}

				if (!AndroidUtilities.needShowPasscode(false) && !SharedConfig.isWaitingForPasscodeEnter) {
					if (rows != null) {
						var r = 0
						val rc = rows.size

						while (r < rc) {
							val row = rows[r]
							var c = 0
							val cc = row.buttons.size

							while (c < cc) {
								val button = row.buttons[c]

								if (button is TL_keyboardButtonCallback) {
									val callbackIntent = Intent(ApplicationLoader.applicationContext, NotificationCallbackReceiver::class.java)
									callbackIntent.putExtra("currentAccount", currentAccount)
									callbackIntent.putExtra("did", dialogId)

									if (button.data != null) {
										callbackIntent.putExtra("data", button.data)
									}

									callbackIntent.putExtra("mid", rowsMid)

									builder.addAction(0, button.text, PendingIntent.getBroadcast(ApplicationLoader.applicationContext, lastButtonId++, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
								}

								c++
							}

							r++
						}
					}
				}

				if (Build.VERSION.SDK_INT >= 26) {
					setNotificationChannel(mainNotification, builder, useSummaryNotification)
				}

				holders.add(NotificationHolder(internalId, dialogId, name ?: "", user, chat, builder))

				wearNotificationsIds.put(dialogId, internalId)

				b++
			}
		}

		if (useSummaryNotification) {
			try {
				notificationManager?.notify(notificationId, mainNotification)
			}
			catch (e: SecurityException) {
				FileLog.e(e)
				resetNotificationSound(notificationBuilder, lastDialogId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType)
			}
		}
		else {
			if (openedInBubbleDialogs.isEmpty()) {
				notificationManager?.cancel(notificationId)
			}
		}

		for (a in 0 until oldIdsWear.size()) {
			val did = oldIdsWear.keyAt(a)

			if (openedInBubbleDialogs.contains(did)) {
				continue
			}

			val id = oldIdsWear.valueAt(a)

			notificationManager?.cancel(id)
		}

		val ids = ArrayList<String>(holders.size)
		var a = 0
		val size = holders.size

		while (a < size) {
			val holder = holders[a]

			ids.clear()

			if (Build.VERSION.SDK_INT >= 29 && !DialogObject.isEncryptedDialog(holder.dialogId)) {
				val shortcutId = createNotificationShortcut(holder.notification, holder.dialogId, holder.name, holder.user, holder.chat, personCache[holder.dialogId])

				if (shortcutId != null) {
					ids.add(shortcutId)
				}
			}

			holder.call()

			if (!unsupportedNotificationShortcut() && ids.isNotEmpty()) {
				ShortcutManagerCompat.removeDynamicShortcuts(ApplicationLoader.applicationContext, ids)
			}

			a++
		}
	}

	@TargetApi(Build.VERSION_CODES.P)
	private fun loadRoundAvatar(avatar: File?, personBuilder: Person.Builder) {
		if (avatar != null) {
			runCatching {
				val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(avatar)) { decoder, _, _ ->
					decoder.postProcessor = PostProcessor { canvas: Canvas ->
						val path = Path()
						path.fillType = Path.FillType.INVERSE_EVEN_ODD

						val width = canvas.width
						val height = canvas.height

						path.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), (width / 2).toFloat(), (width / 2).toFloat(), Path.Direction.CW)

						val paint = Paint()
						paint.isAntiAlias = true
						paint.color = Color.TRANSPARENT
						paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC))

						canvas.drawPath(path, paint)

						PixelFormat.TRANSLUCENT
					}
				}

				val icon = IconCompat.createWithBitmap(bitmap)

				personBuilder.setIcon(icon)
			}
		}
	}

	fun playOutChatSound() {
		if (!inChatSoundEnabled || MediaController.getInstance().isRecordingAudio) {
			return
		}

		try {
			if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) {
				return
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		notificationsQueue.postRunnable {
			try {
				if (abs(SystemClock.elapsedRealtime() - lastSoundOutPlay) <= 100) {
					return@postRunnable
				}

				lastSoundOutPlay = SystemClock.elapsedRealtime()

				if (soundPool == null) {
					soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_SYSTEM).build()).build()

					soundPool?.setOnLoadCompleteListener { soundPool, sampleId, status ->
						if (status == 0) {
							try {
								soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f)
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}
					}
				}

				if (soundOut == 0 && !soundOutLoaded) {
					soundOutLoaded = true
					soundOut = soundPool?.load(ApplicationLoader.applicationContext, R.raw.sound_out, 1) ?: 0
				}

				if (soundOut != 0) {
					try {
						soundPool?.play(soundOut, 1.0f, 1.0f, 1, 0, 1.0f)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun clearDialogNotificationsSettings(did: Long) {
		val preferences = accountInstance.notificationsSettings

		val editor = preferences.edit()
		editor.remove("notify2_$did").remove("custom_$did")

		messagesStorage.setDialogFlags(did, 0)

		val dialog = messagesController.dialogs_dict[did]
		dialog?.notify_settings = TL_peerNotifySettings()

		editor.commit()

		notificationsController.updateServerNotificationsSettings(did, true)
	}

	fun setDialogNotificationsSettings(dialogId: Long, setting: Int) {
		val preferences = accountInstance.notificationsSettings
		val editor = preferences.edit()
		val dialog = messagesController.dialogs_dict[dialogId]

		if (setting == SETTING_MUTE_UNMUTE) {
			val defaultEnabled = isGlobalNotificationsEnabled(dialogId)

			if (defaultEnabled) {
				editor.remove("notify2_$dialogId")
			}
			else {
				editor.putInt("notify2_$dialogId", 0)
			}

			messagesStorage.setDialogFlags(dialogId, 0)

			dialog?.notify_settings = TL_peerNotifySettings()
		}
		else {
			var untilTime = connectionsManager.currentTime

			when (setting) {
				SETTING_MUTE_HOUR -> {
					untilTime += 60 * 60
				}

				SETTING_MUTE_8_HOURS -> {
					untilTime += 60 * 60 * 8
				}

				SETTING_MUTE_2_DAYS -> {
					untilTime += 60 * 60 * 48
				}

				SETTING_MUTE_FOREVER -> {
					untilTime = Int.MAX_VALUE
				}
			}

			val flags = if (setting == SETTING_MUTE_FOREVER) {
				editor.putInt("notify2_$dialogId", 2)
				1L
			}
			else {
				editor.putInt("notify2_$dialogId", 3)
				editor.putInt("notifyuntil_$dialogId", untilTime)
				untilTime.toLong() shl 32 or 1L
			}

			getInstance(UserConfig.selectedAccount).removeNotificationsForDialog(dialogId)

			messagesStorage.setDialogFlags(dialogId, flags)

			dialog?.notify_settings = TL_peerNotifySettings().apply {
				mute_until = untilTime
			}
		}

		editor.commit()
		updateServerNotificationsSettings(dialogId)
	}

	@JvmOverloads
	fun updateServerNotificationsSettings(dialogId: Long, post: Boolean = true) {
		if (post) {
			notificationCenter.postNotificationName(NotificationCenter.notificationsSettingsUpdated)
		}

		if (DialogObject.isEncryptedDialog(dialogId)) {
			return
		}

		val preferences = accountInstance.notificationsSettings

		val req = TL_account_updateNotifySettings()
		req.settings = TL_inputPeerNotifySettings()
		req.settings.flags = req.settings.flags or 1
		req.settings.show_previews = preferences.getBoolean("content_preview_$dialogId", true)
		req.settings.flags = req.settings.flags or 2
		req.settings.silent = preferences.getBoolean("silent_$dialogId", false)

		val muteType = preferences.getInt("notify2_$dialogId", -1)

		if (muteType != -1) {
			req.settings.flags = req.settings.flags or 4

			if (muteType == 3) {
				req.settings.mute_until = preferences.getInt("notifyuntil_$dialogId", 0)
			}
			else {
				req.settings.mute_until = if (muteType != 2) 0 else Int.MAX_VALUE
			}
		}

		val soundDocumentId = preferences.getLong("sound_document_id_$dialogId", 0)
		val soundPath = preferences.getString("sound_path_$dialogId", null)

		req.settings.flags = req.settings.flags or 8

		if (soundDocumentId != 0L) {
			val ringtoneSound = TL_notificationSoundRingtone()
			ringtoneSound.id = soundDocumentId

			req.settings.sound = ringtoneSound
		}
		else if (soundPath != null) {
			if (soundPath == "NoSound") {
				req.settings.sound = TL_notificationSoundNone()
			}
			else {
				val localSound = TL_notificationSoundLocal()
				localSound.title = preferences.getString("sound_$dialogId", null)
				localSound.data = soundPath

				req.settings.sound = localSound
			}
		}
		else {
			req.settings.sound = TL_notificationSoundDefault()
		}

		req.peer = TL_inputNotifyPeer().apply {
			peer = messagesController.getInputPeer(dialogId)
		}

		connectionsManager.sendRequest(req)
	}

	init {
		val preferences = accountInstance.notificationsSettings

		inChatSoundEnabled = preferences.getBoolean("EnableInChatSound", true)
		showBadgeNumber = preferences.getBoolean("badgeNumber", true)
		showBadgeMuted = preferences.getBoolean("badgeNumberMuted", false)
		showBadgeMessages = preferences.getBoolean("badgeNumberMessages", true)
		notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext)

		systemNotificationManager = ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		try {
			audioManager = ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			alarmManager = ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			val pm = ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

			notificationDelayWakelock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "elloapp:notification_delay_lock")
			notificationDelayWakelock?.setReferenceCounted(false)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		notificationDelayRunnable = Runnable {
			if (delayedPushMessages.isNotEmpty()) {
				runBlocking {
					showOrUpdateNotification(true)
				}

				delayedPushMessages.clear()
			}

			try {
				if (notificationDelayWakelock?.isHeld == true) {
					notificationDelayWakelock?.release()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun updateServerNotificationsSettings(type: Int) {
		val preferences = accountInstance.notificationsSettings

		val req = TL_account_updateNotifySettings()
		req.settings = TL_inputPeerNotifySettings()
		req.settings.flags = 5

		val soundDocumentIdPref: String
		val soundPathPref: String
		val soundNamePref: String

		when (type) {
			TYPE_GROUP -> {
				req.peer = TL_inputNotifyChats()
				req.settings.mute_until = preferences.getInt("EnableGroup2", 0)
				req.settings.show_previews = preferences.getBoolean("EnablePreviewGroup", true)

				soundNamePref = "GroupSound"
				soundDocumentIdPref = "GroupSoundDocId"
				soundPathPref = "GroupSoundPath"
			}

			TYPE_PRIVATE -> {
				req.peer = TL_inputNotifyUsers()
				req.settings.mute_until = preferences.getInt("EnableAll2", 0)
				req.settings.show_previews = preferences.getBoolean("EnablePreviewAll", true)

				soundNamePref = "GlobalSound"
				soundDocumentIdPref = "GlobalSoundDocId"
				soundPathPref = "GlobalSoundPath"
			}

			else -> {
				req.peer = TL_inputNotifyBroadcasts()
				req.settings.mute_until = preferences.getInt("EnableChannel2", 0)
				req.settings.show_previews = preferences.getBoolean("EnablePreviewChannel", true)

				soundNamePref = "ChannelSound"
				soundDocumentIdPref = "ChannelSoundDocId"
				soundPathPref = "ChannelSoundPath"
			}
		}

		req.settings.flags = req.settings.flags or 8

		val soundDocumentId = preferences.getLong(soundDocumentIdPref, 0)
		val soundPath = preferences.getString(soundPathPref, "NoSound")

		if (soundDocumentId != 0L) {
			val ringtoneSound = TL_notificationSoundRingtone()
			ringtoneSound.id = soundDocumentId

			req.settings.sound = ringtoneSound
		}
		else if (soundPath != null) {
			if (soundPath == "NoSound") {
				req.settings.sound = TL_notificationSoundNone()
			}
			else {
				val localSound = TL_notificationSoundLocal()
				localSound.title = preferences.getString(soundNamePref, null)
				localSound.data = soundPath

				req.settings.sound = localSound
			}
		}
		else {
			req.settings.sound = TL_notificationSoundDefault()
		}

		connectionsManager.sendRequest(req)
	}

	@JvmOverloads
	fun isGlobalNotificationsEnabled(dialogId: Long, forceChannel: Boolean? = null): Boolean {
		val type = if (DialogObject.isChatDialog(dialogId)) {
			if (forceChannel != null) {
				if (forceChannel) {
					TYPE_CHANNEL
				}
				else {
					TYPE_GROUP
				}
			}
			else {
				val chat = messagesController.getChat(-dialogId)

				if (ChatObject.isChannel(chat) && !chat.megagroup) {
					TYPE_CHANNEL
				}
				else {
					TYPE_GROUP
				}
			}
		}
		else {
			TYPE_PRIVATE
		}

		return isGlobalNotificationsEnabled(type)
	}

	fun isGlobalNotificationsEnabled(type: Int): Boolean {
		return accountInstance.notificationsSettings.getInt(getGlobalNotificationsKey(type), 0) < connectionsManager.currentTime
	}

	fun setGlobalNotificationsEnabled(type: Int, time: Int) {
		accountInstance.notificationsSettings.edit().putInt(getGlobalNotificationsKey(type), time).commit()
		updateServerNotificationsSettings(type)
		messagesStorage.updateMutedDialogsFiltersCounters()
		deleteNotificationChannelGlobal(type)
	}

	fun muteDialog(dialogId: Long, mute: Boolean) {
		if (mute) {
			getInstance(currentAccount).muteUntil(dialogId, Int.MAX_VALUE)
		}
		else {
			val defaultEnabled = getInstance(currentAccount).isGlobalNotificationsEnabled(dialogId)
			val preferences = MessagesController.getNotificationsSettings(currentAccount)
			val editor = preferences.edit()

			if (defaultEnabled) {
				editor.remove("notify2_$dialogId")
			}
			else {
				editor.putInt("notify2_$dialogId", 0)
			}

			messagesStorage.setDialogFlags(dialogId, 0)

			editor.commit()

			val dialog = messagesController.dialogs_dict[dialogId]
			dialog?.notify_settings = TL_peerNotifySettings()

			updateServerNotificationsSettings(dialogId)
		}
	}

	companion object {
		private val notificationsQueue = DispatchQueue("notificationsQueue")
		private var notificationManager: NotificationManagerCompat? = null
		private var systemNotificationManager: NotificationManager? = null
		private val Instance = arrayOfNulls<NotificationsController>(UserConfig.MAX_ACCOUNT_COUNT)
		private val lockObjects = Array(UserConfig.MAX_ACCOUNT_COUNT) { Any() }
		const val EXTRA_VOICE_REPLY = "extra_voice_reply"
		const val SETTING_SOUND_ON = 0
		const val SETTING_SOUND_OFF = 1
		const val SETTING_MUTE_HOUR = 0
		const val SETTING_MUTE_8_HOURS = 1
		const val SETTING_MUTE_2_DAYS = 2
		const val SETTING_MUTE_FOREVER = 3
		const val SETTING_MUTE_UNMUTE = 4
		const val SETTING_MUTE_CUSTOM = 5
		const val TYPE_GROUP = 0
		const val TYPE_PRIVATE = 1
		const val TYPE_CHANNEL = 2

		@JvmField
		var OTHER_NOTIFICATIONS_CHANNEL: String? = null

		@JvmField
		var globalSecretChatId = DialogObject.makeEncryptedDialogId(1)

		@JvmField
		var audioManager: AudioManager? = null

		init {
			if (Build.VERSION.SDK_INT >= 26) {
				notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext)
				systemNotificationManager = ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
				checkOtherNotificationsChannel()
			}

			audioManager = ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
		}

		@JvmStatic
		fun getInstance(num: Int): NotificationsController {
			var localInstance = Instance[num]

			if (localInstance == null) {
				synchronized(lockObjects[num]) {
					localInstance = Instance[num]

					if (localInstance == null) {
						localInstance = NotificationsController(num)
						Instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		@JvmStatic
		fun checkOtherNotificationsChannel() {
			if (Build.VERSION.SDK_INT < 26) {
				return
			}

			var preferences: SharedPreferences? = null

			if (OTHER_NOTIFICATIONS_CHANNEL == null) {
				preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE)
				OTHER_NOTIFICATIONS_CHANNEL = preferences.getString("OtherKey", "Other3")
			}

			var notificationChannel = systemNotificationManager?.getNotificationChannel(OTHER_NOTIFICATIONS_CHANNEL)

			if (notificationChannel != null && notificationChannel.importance != NotificationManager.IMPORTANCE_DEFAULT) {
				systemNotificationManager?.deleteNotificationChannel(OTHER_NOTIFICATIONS_CHANNEL)
				OTHER_NOTIFICATIONS_CHANNEL = null
				notificationChannel = null
			}

			if (OTHER_NOTIFICATIONS_CHANNEL == null) {
				if (preferences == null) {
					preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE)
				}

				OTHER_NOTIFICATIONS_CHANNEL = "Other" + Utilities.random.nextLong()

				preferences?.edit()?.putString("OtherKey", OTHER_NOTIFICATIONS_CHANNEL)?.commit()
			}

			if (notificationChannel == null) {
				notificationChannel = NotificationChannel(OTHER_NOTIFICATIONS_CHANNEL, "Internal notifications", NotificationManager.IMPORTANCE_DEFAULT)
				notificationChannel.enableLights(false)
				notificationChannel.enableVibration(false)
				notificationChannel.setSound(null, null)

				try {
					systemNotificationManager?.createNotificationChannel(notificationChannel)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		@JvmStatic
		fun getGlobalNotificationsKey(type: Int): String {
			return when (type) {
				TYPE_GROUP -> "EnableGroup2"
				TYPE_PRIVATE -> "EnableAll2"
				else -> "EnableChannel2"
			}
		}
	}
}
