/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser.MediaItem
import android.media.session.MediaSession
import android.media.session.MediaSession.QueueItem
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Process
import android.os.SystemClock
import android.service.media.MediaBrowserService
import androidx.collection.LongSparseArray
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.photoSmall
import org.telegram.tgnet.readAttachPath
import org.telegram.ui.LaunchActivity
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale

class MusicBrowserService : MediaBrowserService(), NotificationCenterDelegate {
	private var mediaSession: MediaSession? = null
	private val currentAccount = UserConfig.selectedAccount
	private var chatsLoaded = false
	private var loadingChats = false
	private val dialogs = mutableListOf<Long>()
	private val users = LongSparseArray<User>()
	private val chats = LongSparseArray<Chat>()
	private val musicObjects = LongSparseArray<MutableList<MessageObject>>()
	private val musicQueues = LongSparseArray<MutableList<QueueItem>>()
	private val roundPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
	private val bitmapRect by lazy { RectF() }
	private var serviceStarted = false
	private var lastSelectedDialog: Long = 0
	private val delayedStopHandler = DelayedStopHandler(this)

	override fun onCreate() {
		super.onCreate()

		ApplicationLoader.postInitApplication()

		lastSelectedDialog = AndroidUtilities.getPrefIntOrLong(MessagesController.getNotificationsSettings(currentAccount), "auto_lastSelectedDialog", 0)

		mediaSession = MediaSession(this, "MusicService")

		sessionToken = mediaSession?.sessionToken

		mediaSession?.setCallback(MediaSessionCallback())
		mediaSession?.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

		val context = applicationContext
		val intent = Intent(context, LaunchActivity::class.java)

		val pi = PendingIntent.getActivity(context, 99, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

		mediaSession?.setSessionActivity(pi)

		val extras = Bundle()
		extras.putBoolean(SLOT_RESERVATION_QUEUE, true)
		extras.putBoolean(SLOT_RESERVATION_SKIP_TO_PREV, true)
		extras.putBoolean(SLOT_RESERVATION_SKIP_TO_NEXT, true)

		mediaSession?.setExtras(extras)

		updatePlaybackState(null)

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.addObserver(this, NotificationCenter.messagePlayingDidStart)
			it.addObserver(this, NotificationCenter.messagePlayingDidReset)
		}
	}

	override fun onStartCommand(startIntent: Intent, flags: Int, startId: Int): Int {
		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		handleStopRequest(null)
		delayedStopHandler.removeCallbacksAndMessages(null)
		mediaSession?.release()
	}

	override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
		if (Process.SYSTEM_UID != clientUid && Process.myUid() != clientUid && clientPackageName != "com.google.android.mediasimulator" && clientPackageName != "com.google.android.projection.gearhead") {
			return null
		}

		return BrowserRoot(MEDIA_ID_ROOT, null)
	}

	override fun onLoadChildren(parentMediaId: String, result: Result<List<MediaItem>>) {
		if (chatsLoaded) {
			loadChildrenImpl(parentMediaId, result)
			return
		}

		result.detach()

		if (loadingChats) {
			return
		}

		loadingChats = true

		val messagesStorage = MessagesStorage.getInstance(currentAccount)

		messagesStorage.storageQueue.postRunnable {
			try {
				val usersToLoad = mutableListOf<Long>()
				val chatsToLoad = mutableListOf<Long>()
				var cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT DISTINCT uid FROM media_v4 WHERE uid != 0 AND mid > 0 AND type = %d", MediaDataController.MEDIA_MUSIC))

				while (cursor.next()) {
					val dialogId = cursor.longValue(0)

					if (DialogObject.isEncryptedDialog(dialogId)) {
						continue
					}

					dialogs.add(dialogId)

					if (DialogObject.isUserDialog(dialogId)) {
						usersToLoad.add(dialogId)
					}
					else {
						chatsToLoad.add(-dialogId)
					}
				}

				cursor.dispose()

				if (dialogs.isNotEmpty()) {
					val ids = dialogs.joinToString(",")

					cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT uid, data, mid FROM media_v4 WHERE uid IN (%s) AND mid > 0 AND type = %d ORDER BY date DESC, mid DESC", ids, MediaDataController.MEDIA_MUSIC))

					while (cursor.next()) {
						val data = cursor.byteBufferValue(1)

						if (data != null) {
							val message = TLRPC.Message.deserialize(data, data.readInt32(false), false)
							message?.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId)

							data.reuse()

							if (MessageObject.isMusicMessage(message)) {
								val did = cursor.longValue(0)

								message.id = cursor.intValue(2)
								message.dialogId = did

								var arrayList = musicObjects[did]
								var arrayList1 = musicQueues[did]!!

								if (arrayList == null) {
									arrayList = mutableListOf()

									musicObjects.put(did, arrayList)
									arrayList1 = mutableListOf()

									musicQueues.put(did, arrayList1)
								}

								val messageObject = MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = true)

								arrayList.add(0, messageObject)

								val builder = MediaDescription.Builder().setMediaId(did.toString() + "_" + arrayList.size)
								builder.setTitle(messageObject.musicTitle)
								builder.setSubtitle(messageObject.musicAuthor)

								arrayList1.add(0, QueueItem(builder.build(), arrayList1.size.toLong()))
							}
						}
					}

					cursor.dispose()

					if (usersToLoad.isNotEmpty()) {
						val usersArrayList = mutableListOf<User>()

						messagesStorage.getUsersInternal(usersToLoad.joinToString(","), usersArrayList)

						for (user in usersArrayList) {
							users.put(user.id, user)
						}
					}

					if (chatsToLoad.isNotEmpty()) {
						val chatsArrayList = mutableListOf<Chat>()

						messagesStorage.getChatsInternal(chatsToLoad.joinToString(","), chatsArrayList)

						for (chat in chatsArrayList) {
							chats.put(chat.id, chat)
						}
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			AndroidUtilities.runOnUIThread {
				chatsLoaded = true
				loadingChats = false

				loadChildrenImpl(parentMediaId, result)

				if (lastSelectedDialog == 0L && dialogs.isNotEmpty()) {
					lastSelectedDialog = dialogs[0]
				}

				if (lastSelectedDialog != 0L) {
					val arrayList = musicObjects[lastSelectedDialog]
					val arrayList1 = musicQueues[lastSelectedDialog]!!

					if (!arrayList.isNullOrEmpty()) {
						mediaSession?.setQueue(arrayList1)

						if (lastSelectedDialog > 0) {
							val user = users[lastSelectedDialog]

							if (user != null) {
								mediaSession?.setQueueTitle(ContactsController.formatName(user.firstName, user.lastName))
							}
							else {
								mediaSession?.setQueueTitle(getString(R.string.deleted_user).uppercase())
							}
						}
						else {
							val chat = chats[-lastSelectedDialog]

							if (chat != null) {
								mediaSession?.setQueueTitle(chat.title)
							}
							else {
								mediaSession?.setQueueTitle(getString(R.string.deleted_chat).uppercase())
							}
						}

						val messageObject = arrayList[0]

						val builder = MediaMetadata.Builder()
						builder.putLong(MediaMetadata.METADATA_KEY_DURATION, (messageObject.duration * 1000).toLong())
						builder.putString(MediaMetadata.METADATA_KEY_ARTIST, messageObject.musicAuthor)
						builder.putString(MediaMetadata.METADATA_KEY_TITLE, messageObject.musicTitle)

						mediaSession?.setMetadata(builder.build())
					}
				}

				updatePlaybackState(null)
			}
		}
	}

	private fun loadChildrenImpl(parentMediaId: String?, result: Result<List<MediaItem>>) {
		val mediaItems: MutableList<MediaItem> = ArrayList()

		if (MEDIA_ID_ROOT == parentMediaId) {
			for (a in dialogs.indices) {
				val dialogId = dialogs[a]
				val builder = MediaDescription.Builder().setMediaId("__CHAT_$dialogId")
				var avatar: FileLocation? = null

				if (DialogObject.isUserDialog(dialogId)) {
					val user = users[dialogId] as? TLRPC.TLUser

					if (user != null) {
						builder.setTitle(ContactsController.formatName(user.firstName, user.lastName))
						avatar = user.photo?.photoSmall
					}
					else {
						builder.setTitle(getString(R.string.deleted_user).uppercase())
					}
				}
				else {
					val chat = chats[-dialogId]

					if (chat != null) {
						builder.setTitle(chat.title)
						avatar = chat.photo?.photoSmall
					}
					else {
						builder.setTitle(getString(R.string.deleted_chat).uppercase())
					}
				}

				var bitmap: Bitmap? = null

				if (avatar != null) {
					bitmap = createRoundBitmap(FileLoader.getInstance(currentAccount).getPathToAttach(avatar, true))

					if (bitmap != null) {
						builder.setIconBitmap(bitmap)
					}
				}

				if (avatar == null || bitmap == null) {
					builder.setIconUri(("android.resource://" + applicationContext.packageName + "/drawable/contact_blue").toUri())
				}

				mediaItems.add(MediaItem(builder.build(), MediaItem.FLAG_BROWSABLE))
			}
		}
		else if (parentMediaId != null && parentMediaId.startsWith("__CHAT_")) {
			var did = 0

			try {
				did = parentMediaId.replace("__CHAT_", "").toInt()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			val arrayList = musicObjects[did.toLong()]

			if (arrayList != null) {
				for (a in arrayList.indices) {
					val messageObject = arrayList[a]

					val builder = MediaDescription.Builder().setMediaId(did.toString() + "_" + a)
					builder.setTitle(messageObject.musicTitle)
					builder.setSubtitle(messageObject.musicAuthor)

					mediaItems.add(MediaItem(builder.build(), MediaItem.FLAG_PLAYABLE))
				}
			}
		}

		result.sendResult(mediaItems)
	}

	private fun createRoundBitmap(path: File): Bitmap? {
		try {
			val options = BitmapFactory.Options()
			options.inSampleSize = 2

			val bitmap = BitmapFactory.decodeFile(path.toString(), options)

			if (bitmap != null) {
				val result = createBitmap(bitmap.width, bitmap.height)
				result.eraseColor(Color.TRANSPARENT)

				val canvas = Canvas(result)
				val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

				roundPaint.setShader(shader)
				bitmapRect.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

				canvas.drawRoundRect(bitmapRect, bitmap.width.toFloat(), bitmap.height.toFloat(), roundPaint)

				return result
			}
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}

		return null
	}

	private inner class MediaSessionCallback : MediaSession.Callback() {
		override fun onPlay() {
			val messageObject = MediaController.getInstance().playingMessageObject

			if (messageObject == null) {
				onPlayFromMediaId(lastSelectedDialog.toString() + "_" + 0, null)
			}
			else {
				MediaController.getInstance().playMessage(messageObject)
			}
		}

		override fun onSkipToQueueItem(queueId: Long) {
			MediaController.getInstance().playMessageAtIndex(queueId.toInt())
			handlePlayRequest()
		}

		override fun onSeekTo(position: Long) {
			val messageObject = MediaController.getInstance().playingMessageObject

			if (messageObject != null) {
				MediaController.getInstance().seekToProgress(messageObject, position / 1000 / messageObject.duration.toFloat())
			}
		}

		override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
			val args = mediaId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

			if (args.size != 2) {
				return
			}

			try {
				val did = args[0].toLong()
				val id = args[1].toInt()
				val arrayList = musicObjects[did]
				val arrayList1 = musicQueues[did]

				if (arrayList == null || id < 0 || id >= arrayList.size) {
					return
				}

				lastSelectedDialog = did

				MessagesController.getNotificationsSettings(currentAccount).edit { putLong("auto_lastSelectedDialog", did) }
				MediaController.getInstance().setPlaylist(arrayList, arrayList[id], 0, false, null)

				mediaSession?.setQueue(arrayList1)

				if (did > 0) {
					val user = users[did]

					if (user != null) {
						mediaSession?.setQueueTitle(ContactsController.formatName(user.firstName, user.lastName))
					}
					else {
						mediaSession?.setQueueTitle(getString(R.string.deleted_user).uppercase())
					}
				}
				else {
					val chat = chats[-did]

					if (chat != null) {
						mediaSession?.setQueueTitle(chat.title)
					}
					else {
						mediaSession?.setQueueTitle(getString(R.string.deleted_chat).uppercase())
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			handlePlayRequest()
		}

		override fun onPause() {
			handlePauseRequest()
		}

		override fun onStop() {
			handleStopRequest(null)
		}

		override fun onSkipToNext() {
			MediaController.getInstance().playNextMessage()
		}

		override fun onSkipToPrevious() {
			MediaController.getInstance().playPreviousMessage()
		}

		override fun onPlayFromSearch(query: String?, extras: Bundle) {
			@Suppress("NAME_SHADOWING") val query = query?.lowercase()

			if (query.isNullOrEmpty()) {
				return
			}

			for (a in dialogs.indices) {
				val did = dialogs[a]

				if (DialogObject.isUserDialog(did)) {
					val user = users[did] ?: continue

					if (user.firstName?.lowercase()?.startsWith(query) == true || user.lastName?.lowercase()?.startsWith(query) == true) {
						onPlayFromMediaId(did.toString() + "_" + 0, null)
						break
					}
				}
				else {
					val chat = chats[-did] ?: continue

					if (chat.title?.lowercase()?.contains(query) == true) {
						onPlayFromMediaId(did.toString() + "_" + 0, null)
						break
					}
				}
			}
		}
	}

	private fun updatePlaybackState(error: String?) {
		var position = PlaybackState.PLAYBACK_POSITION_UNKNOWN
		val playingMessageObject = MediaController.getInstance().playingMessageObject

		if (playingMessageObject != null) {
			position = playingMessageObject.audioProgressSec * 1000L
		}

		val stateBuilder = PlaybackState.Builder().setActions(availableActions)

		var state = if (playingMessageObject == null) {
			PlaybackState.STATE_STOPPED
		}
		else {
			if (MediaController.getInstance().isDownloadingCurrentMessage) {
				PlaybackState.STATE_BUFFERING
			}
			else {
				if (MediaController.getInstance().isMessagePaused) {
					PlaybackState.STATE_PAUSED
				}
				else {
					PlaybackState.STATE_PLAYING
				}
			}
		}

		if (error != null) {
			stateBuilder.setErrorMessage(error)
			state = PlaybackState.STATE_ERROR
		}

		stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime())

		if (playingMessageObject != null) {
			stateBuilder.setActiveQueueItemId(MediaController.getInstance().playingMessageObjectNum.toLong())
		}
		else {
			stateBuilder.setActiveQueueItemId(0)
		}

		mediaSession?.setPlaybackState(stateBuilder.build())
	}

	private val availableActions: Long
		get() {
			var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH
			val playingMessageObject = MediaController.getInstance().playingMessageObject

			if (playingMessageObject != null) {
				if (!MediaController.getInstance().isMessagePaused) {
					actions = actions or PlaybackState.ACTION_PAUSE
				}

				actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
				actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
			}

			return actions
		}

	private fun handleStopRequest(withError: String?) {
		delayedStopHandler.removeCallbacksAndMessages(null)
		delayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())

		updatePlaybackState(withError)
		stopSelf()

		serviceStarted = false

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.removeObserver(this, NotificationCenter.messagePlayingDidStart)
			it.removeObserver(this, NotificationCenter.messagePlayingDidReset)
		}
	}

	private fun handlePlayRequest() {
		delayedStopHandler.removeCallbacksAndMessages(null)

		if (!serviceStarted) {
			try {
				startService(Intent(applicationContext, MusicBrowserService::class.java))
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			serviceStarted = true
		}

		if (mediaSession?.isActive != true) {
			mediaSession?.isActive = true
		}

		val messageObject = MediaController.getInstance().playingMessageObject ?: return

		val builder = MediaMetadata.Builder()
		builder.putLong(MediaMetadata.METADATA_KEY_DURATION, (messageObject.duration * 1000).toLong())
		builder.putString(MediaMetadata.METADATA_KEY_ARTIST, messageObject.musicAuthor)
		builder.putString(MediaMetadata.METADATA_KEY_TITLE, messageObject.musicTitle)

		val audioInfo = MediaController.getInstance().audioInfo

		if (audioInfo != null) {
			val bitmap = audioInfo.cover

			if (bitmap != null) {
				builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
			}
		}

		mediaSession?.setMetadata(builder.build())
	}

	private fun handlePauseRequest() {
		MediaController.getInstance().pauseMessage(MediaController.getInstance().playingMessageObject)

		delayedStopHandler.removeCallbacksAndMessages(null)
		delayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		updatePlaybackState(null)
		handlePlayRequest()
	}

	private class DelayedStopHandler(service: MusicBrowserService) : Handler() {
		private val mWeakReference = WeakReference(service)

		override fun handleMessage(msg: Message) {
			val service = mWeakReference.get()

			if (service != null) {
				val messageObject = MediaController.getInstance().playingMessageObject

				if (messageObject != null && !MediaController.getInstance().isMessagePaused) {
					return
				}

				service.stopSelf()
				service.serviceStarted = false
			}
		}
	}

	companion object {
		private const val SLOT_RESERVATION_SKIP_TO_NEXT = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT"
		private const val SLOT_RESERVATION_SKIP_TO_PREV = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS"
		private const val SLOT_RESERVATION_QUEUE = "com.google.android.gms.car.media.ALWAYS_RESERVE_SPACE_FOR.ACTION_QUEUE"
		private const val MEDIA_ID_ROOT = "__ROOT__"
		private const val STOP_DELAY = 30000
	}
}
