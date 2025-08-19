/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Spannable
import android.text.TextUtils
import android.util.Base64
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.collection.LongSparseArray
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.util.isEmpty
import androidx.core.util.size
import androidx.core.view.inputmethod.InputContentInfoCompat
import org.telegram.messenger.MediaController.SearchImage
import org.telegram.messenger.MessagesStorage.LongCallback
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.SendMessagesHelper.LocationProvider.LocationProviderDelegate
import org.telegram.messenger.UserObject.isReplyUser
import org.telegram.messenger.audioinfo.AudioInfo
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.messageobject.SendAnimationData
import org.telegram.messenger.support.SparseLongArray
import org.telegram.messenger.utils.getImageDimensions
import org.telegram.tgnet.*
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC.BotInlineResult
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.EncryptedChat
import org.telegram.tgnet.TLRPC.InputCheckPasswordSRP
import org.telegram.tgnet.TLRPC.InputDocument
import org.telegram.tgnet.TLRPC.InputEncryptedFile
import org.telegram.tgnet.TLRPC.InputMedia
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.KeyboardButton
import org.telegram.tgnet.TLRPC.Message
import org.telegram.tgnet.TLRPC.MessageEntity
import org.telegram.tgnet.TLRPC.MessageMedia
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.ReplyMarkup
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell.Companion.getMessageSize
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedFileDrawable
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import org.telegram.ui.Components.Reactions.ReactionsUtils
import org.telegram.ui.Components.Reactions.VisibleReaction
import org.telegram.ui.TwoStepVerificationActivity
import org.telegram.ui.TwoStepVerificationSetupActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class SendMessagesHelper(instance: Int) : BaseController(instance), NotificationCenterDelegate {
	private val delayedMessages = HashMap<String, MutableList<DelayedMessage>>()
	private val unsentMessages = SparseArray<MessageObject>()
	private val sendingMessages = SparseArray<Message>()
	private val editingMessages = SparseArray<Message>()
	private val uploadMessages = SparseArray<Message>()
	private val sendingMessagesIdDialogs = LongSparseArray<Int>()
	private val uploadingMessagesIdDialogs = LongSparseArray<Int>()
	private val waitingForLocation = mutableMapOf<String, MessageObject>()
	private val waitingForCallback = mutableMapOf<String, Boolean>()
	private val waitingForVote = mutableMapOf<String, ByteArray>()
	private val voteSendTime = LongSparseArray<Long>()
	private val importingHistoryFiles = mutableMapOf<String, ImportingHistory>()
	private val importingHistoryMap = LongSparseArray<ImportingHistory>()
	private val importingStickersFiles = mutableMapOf<String, ImportingStickers>()
	private val importingStickersMap = mutableMapOf<String, ImportingStickers>()

	inner class ImportingHistory {
		var historyPath: String? = null
		var mediaPaths = mutableListOf<Uri>()
		var uploadSet = mutableSetOf<String>()
		private var uploadProgresses = mutableMapOf<String, Float>()
		private var uploadSize = mutableMapOf<String, Long>()
		var uploadMedia = mutableListOf<String>()
		var peer: InputPeer? = null
		var totalCount: Long = 0
		var uploadedCount: Long = 0
		var dialogId: Long = 0
		private var importId: Long = 0
		private var estimatedUploadSpeed = 0.0
		private var lastUploadTime: Long = 0
		private var lastUploadSize: Long = 0

		@JvmField
		var uploadProgress = 0

		@JvmField
		var timeUntilFinish = Int.MAX_VALUE

		fun initImport(inputFile: TLRPC.InputFile) {
			val req = TLRPC.TLMessagesInitHistoryImport()
			req.file = inputFile
			req.mediaCount = mediaPaths.size
			req.peer = peer

			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					if (response is TLRPC.TLMessagesHistoryImport) {
						importId = response.id
						uploadSet.remove(historyPath)

						notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId)

						if (uploadSet.isEmpty()) {
							startImport()
						}

						lastUploadTime = SystemClock.elapsedRealtime()

						for (media in uploadMedia) {
							fileLoader.uploadFile(media, encrypted = false, small = true, type = ConnectionsManager.FileTypeFile)
						}
					}
					else {
						importingHistoryMap.remove(dialogId)
						notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId, req, error)
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}

		fun onFileFailedToUpload(path: String) {
			if (path == historyPath) {
				importingHistoryMap.remove(dialogId)

				val error = TLRPC.TLError()
				error.code = 400
				error.text = "IMPORT_UPLOAD_FAILED"

				notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId, TLRPC.TLMessagesInitHistoryImport(), error)
			}
			else {
				uploadSet.remove(path)
			}
		}

		fun addUploadProgress(path: String, sz: Long, progress: Float) {
			uploadProgresses[path] = progress
			uploadSize[path] = sz
			uploadedCount = 0

			for ((_, value) in uploadSize) {
				uploadedCount += value
			}

			val newTime = SystemClock.elapsedRealtime()

			if (path != historyPath && uploadedCount != lastUploadSize && newTime != lastUploadTime) {
				val dt = (newTime - lastUploadTime) / 1000.0
				val uploadSpeed = (uploadedCount - lastUploadSize) / dt

				estimatedUploadSpeed = if (estimatedUploadSpeed == 0.0) {
					uploadSpeed
				}
				else {
					val coef = 0.01
					coef * uploadSpeed + (1 - coef) * estimatedUploadSpeed
				}

				timeUntilFinish = ((totalCount - uploadedCount) * 1000 / estimatedUploadSpeed).toInt()
				lastUploadSize = uploadedCount
				lastUploadTime = newTime
			}

			val pr = uploadedCount / totalCount.toFloat()
			val newProgress = (pr * 100).toInt()

			if (uploadProgress != newProgress) {
				uploadProgress = newProgress
				notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId)
			}
		}

		fun onMediaImport(path: String, size: Long, inputFile: TLRPC.InputFile) {
			addUploadProgress(path, size, 1.0f)

			val req = TLRPC.TLMessagesUploadImportedMedia()
			req.peer = peer
			req.importId = importId
			req.fileName = File(path).name

			val myMime = MimeTypeMap.getSingleton()
			var ext = "txt"
			val idx = req.fileName?.lastIndexOf('.') ?: -1

			if (idx != -1) {
				ext = req.fileName?.substring(idx + 1)?.lowercase() ?: "txt"
			}

			var mimeType = myMime.getMimeTypeFromExtension(ext)

			if (mimeType == null) {
				mimeType = when (ext) {
					"opus" -> "audio/opus"
					"webp" -> "image/webp"
					else -> "text/plain"
				}
			}

			if (mimeType == "image/jpg" || mimeType == "image/jpeg") {
				val inputMediaUploadedPhoto = TLRPC.TLInputMediaUploadedPhoto()
				inputMediaUploadedPhoto.file = inputFile

				req.media = inputMediaUploadedPhoto
			}
			else {
				val inputMediaDocument = TLRPC.TLInputMediaUploadedDocument()
				inputMediaDocument.file = inputFile
				inputMediaDocument.mimeType = mimeType

				req.media = inputMediaDocument
			}

			connectionsManager.sendRequest(req, { _, _ ->
				AndroidUtilities.runOnUIThread {
					uploadSet.remove(path)

					notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId)

					if (uploadSet.isEmpty()) {
						startImport()
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}

		private fun startImport() {
			val req = TLRPC.TLMessagesStartHistoryImport()
			req.peer = peer
			req.importId = importId

			connectionsManager.sendRequest(req) { _, error ->
				AndroidUtilities.runOnUIThread {
					importingHistoryMap.remove(dialogId)

					if (error == null) {
						notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId)
					}
					else {
						notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId, req, error)
					}
				}
			}
		}

		fun setImportProgress(value: Int) {
			if (value == 100) {
				importingHistoryMap.remove(dialogId)
			}

			notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId)
		}
	}

	class ImportingSticker {
		@JvmField
		var path: String? = null

		@JvmField
		var emoji: String? = null

		@JvmField
		var validated = false

		@JvmField
		var mimeType: String? = null

		@JvmField
		var animated = false

		var item: TLRPC.TLInputStickerSetItem? = null

		fun uploadMedia(account: Int, inputFile: TLRPC.InputFile, onFinish: Runnable) {
			val req = TLRPC.TLMessagesUploadMedia()
			req.peer = TLRPC.TLInputPeerSelf()

			req.media = TLRPC.TLInputMediaUploadedDocument().also {
				it.file = inputFile
				it.mimeType = mimeType
			}

			ConnectionsManager.getInstance(account).sendRequest(req, { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (response is TLRPC.TLMessageMediaDocument) {
						val doc = response.document

						if (doc is TLRPC.TLDocument) {
							item = TLRPC.TLInputStickerSetItem()

							item?.document = TLRPC.TLInputDocument().also {
								it.id = doc.id
								it.accessHash = doc.accessHash
								it.fileReference = doc.fileReference
							}

							item?.emoji = emoji ?: ""

							mimeType = doc.mimeType
						}
					}
					else if (animated) {
						mimeType = "application/x-bad-tgsticker"
					}

					onFinish.run()
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
	}

	inner class ImportingStickers {
		var uploadSet = mutableMapOf<String, ImportingSticker>()
		private var uploadProgresses = mutableMapOf<String, Float>()
		private var uploadSize = mutableMapOf<String, Long>()
		var uploadMedia = mutableListOf<ImportingSticker>()
		var shortName: String? = null
		var title: String? = null
		var software: String? = null
		var totalCount: Long = 0
		var uploadedCount: Long = 0
		private var estimatedUploadSpeed = 0.0
		private var lastUploadTime: Long = 0
		private var lastUploadSize: Long = 0

		@JvmField
		var uploadProgress = 0

		@JvmField
		var timeUntilFinish = Int.MAX_VALUE

		fun initImport() {
			notificationCenter.postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName)
			lastUploadTime = SystemClock.elapsedRealtime()

			for (media in uploadMedia) {
				fileLoader.uploadFile(media.path, encrypted = false, small = true, type = ConnectionsManager.FileTypeFile)
			}
		}

		fun onFileFailedToUpload(path: String) {
			val file = uploadSet.remove(path)

			if (file != null) {
				uploadMedia.remove(file)
			}
		}

		fun addUploadProgress(path: String, sz: Long, progress: Float) {
			uploadProgresses[path] = progress
			uploadSize[path] = sz
			uploadedCount = 0

			for ((_, value) in uploadSize) {
				uploadedCount += value
			}

			val newTime = SystemClock.elapsedRealtime()

			if (uploadedCount != lastUploadSize && newTime != lastUploadTime) {
				val dt = (newTime - lastUploadTime) / 1000.0
				val uploadSpeed = (uploadedCount - lastUploadSize) / dt

				estimatedUploadSpeed = if (estimatedUploadSpeed == 0.0) {
					uploadSpeed
				}
				else {
					val coef = 0.01
					coef * uploadSpeed + (1 - coef) * estimatedUploadSpeed
				}

				timeUntilFinish = ((totalCount - uploadedCount) * 1000 / estimatedUploadSpeed).toInt()
				lastUploadSize = uploadedCount
				lastUploadTime = newTime
			}

			val pr = uploadedCount / totalCount.toFloat()
			val newProgress = (pr * 100).toInt()

			if (uploadProgress != newProgress) {
				uploadProgress = newProgress
				notificationCenter.postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName)
			}
		}

		fun onMediaImport(path: String, size: Long, inputFile: TLRPC.InputFile) {
			addUploadProgress(path, size, 1.0f)

			val file = uploadSet[path] ?: return

			file.uploadMedia(currentAccount, inputFile) {
				uploadSet.remove(path)

				notificationCenter.postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName)

				if (uploadSet.isEmpty()) {
					startImport()
				}
			}
		}

		fun startImport() {
			val req = TLRPC.TLStickersCreateStickerSet()
			req.userId = TLRPC.TLInputUserSelf()
			req.title = title
			req.shortName = shortName
			req.animated = uploadMedia[0].animated

			if (software != null) {
				req.software = software
				req.flags = req.flags or 8
			}

			for (file in uploadMedia) {
				val item = file.item ?: continue
				req.stickers.add(item)
			}

			connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					importingStickersMap.remove(shortName)

					if (error == null) {
						notificationCenter.postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName)
					}
					else {
						notificationCenter.postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName, req, error)
					}

					if (response is TLRPC.TLMessagesStickerSet) {
						if (notificationCenter.hasObservers(NotificationCenter.stickersImportComplete)) {
							notificationCenter.postNotificationName(NotificationCenter.stickersImportComplete, response)
						}
						else {
							mediaDataController.toggleStickerSet(ApplicationLoader.applicationContext, response, 2, null, showSettings = false, showTooltip = false)
						}
					}
				}
			}
		}

		fun setImportProgress(value: Int) {
			if (value == 100) {
				importingStickersMap.remove(shortName)
			}

			notificationCenter.postNotificationName(NotificationCenter.stickersImportProgressChanged, shortName)
		}
	}

	private class MediaSendPrepareWorker {
		@Volatile
		var photo: TLRPC.TLPhoto? = null

		@Volatile
		var parentObject: String? = null

		var sync: CountDownLatch? = null
	}

	private val locationProvider = LocationProvider(object : LocationProviderDelegate {
		override fun onLocationAcquired(location: Location) {
			sendLocation(location)
			waitingForLocation.clear()
		}

		override fun onUnableLocationAcquire() {
			val waitingForLocationCopy = HashMap(waitingForLocation)
			notificationCenter.postNotificationName(NotificationCenter.wasUnableToFindCurrentLocation, waitingForLocationCopy)
			waitingForLocation.clear()
		}
	})

	class SendingMediaInfo {
		var paintPath: String? = null
		var forceImage = false

		@JvmField
		var uri: Uri? = null

		@JvmField
		var path: String? = null

		@JvmField
		var caption: String? = null

		@JvmField
		var thumbPath: String? = null

		@JvmField
		var ttl = 0

		@JvmField
		var entities: List<MessageEntity>? = null

		@JvmField
		var masks: ArrayList<InputDocument>? = null

		@JvmField
		var videoEditedInfo: VideoEditedInfo? = null

		@JvmField
		var searchImage: SearchImage? = null

		@JvmField
		var inlineResult: BotInlineResult? = null

		@JvmField
		var params: HashMap<String, String>? = null

		@JvmField
		var isVideo = false

		@JvmField
		var canDeleteAfter = false

		@JvmField
		var updateStickersOrder = false
	}

	open class LocationProvider(private var delegate: LocationProviderDelegate?) {
		interface LocationProviderDelegate {
			fun onLocationAcquired(location: Location)
			fun onUnableLocationAcquire()
		}

		private var locationManager: LocationManager? = null
		private val gpsLocationListener: GpsLocationListener = GpsLocationListener()
		private val networkLocationListener: GpsLocationListener = GpsLocationListener()
		private var locationQueryCancelRunnable: Runnable? = null
		private var lastKnownLocation: Location? = null

		private inner class GpsLocationListener : LocationListener {
			override fun onLocationChanged(location: Location) {
				if (locationQueryCancelRunnable == null) {
					return
				}

				FileLog.d("found location $location")

				lastKnownLocation = location

				if (location.accuracy < 100) {
					delegate?.onLocationAcquired(location)

					locationQueryCancelRunnable?.let {
						AndroidUtilities.cancelRunOnUIThread(it)
					}

					cleanup()
				}
			}

			@Deprecated("Deprecated in Java")
			override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
			}

			override fun onProviderEnabled(provider: String) {}
			override fun onProviderDisabled(provider: String) {}
		}

		fun setDelegate(locationProviderDelegate: LocationProviderDelegate?) {
			delegate = locationProviderDelegate
		}

		private fun cleanup() {
			locationManager?.removeUpdates(gpsLocationListener)
			locationManager?.removeUpdates(networkLocationListener)
			lastKnownLocation = null
			locationQueryCancelRunnable = null
		}

		fun start() {
			if (locationManager == null) {
				locationManager = ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
			}
			try {
				if (ActivityCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
					locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0f, gpsLocationListener)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			try {
				if (ActivityCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
					locationManager!!.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 0f, networkLocationListener)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			try {
				if (ActivityCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(ApplicationLoader.applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
					lastKnownLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)

					if (lastKnownLocation == null) {
						lastKnownLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			locationQueryCancelRunnable?.let {
				AndroidUtilities.cancelRunOnUIThread(it)
			}

			locationQueryCancelRunnable = Runnable {
				if (delegate != null) {
					val lastKnownLocation = lastKnownLocation

					if (lastKnownLocation != null) {
						delegate?.onLocationAcquired(lastKnownLocation)
					}
					else {
						delegate?.onUnableLocationAcquire()
					}
				}

				cleanup()
			}

			AndroidUtilities.runOnUIThread(locationQueryCancelRunnable, 5000)
		}

		open fun stop() {
			if (locationManager == null) {
				return
			}

			locationQueryCancelRunnable?.let {
				AndroidUtilities.cancelRunOnUIThread(it)
			}

			cleanup()
		}
	}

	inner class DelayedMessageSendAfterRequest {
		var request: TLObject? = null
		var msgObj: MessageObject? = null
		var msgObjs: ArrayList<MessageObject>? = null
		var originalPath: String? = null
		var originalPaths: ArrayList<String?>? = null
		var parentObjects: ArrayList<Any?>? = null
		var delayedMessage: DelayedMessage? = null
		var parentObject: Any? = null
		var scheduled = false
	}

	inner class DelayedMessage(var peer: Long) {
		var requests: ArrayList<DelayedMessageSendAfterRequest>? = null
		var sendRequest: TLObject? = null
		var sendEncryptedRequest: TLObject? = null
		var originalPath: String? = null
		var locationParent: TLObject? = null
		var httpLocation: String? = null
		var videoEditedInfo: VideoEditedInfo? = null
		var performMediaUpload = false
		var retriedToSend = false
		var inputUploadMedia: InputMedia? = null
		var locations: ArrayList<PhotoSize?>? = null
		var httpLocations: ArrayList<String?>? = null
		var videoEditedInfos: ArrayList<VideoEditedInfo?>? = null
		var parentObjects: ArrayList<Any?>? = null
		var inputMedias: ArrayList<InputMedia?>? = null
		var groupId: Long = 0
		var finalGroupMessage = 0
		var scheduled = false
		var parentObject: Any? = null

		@JvmField
		var type = 0

		@JvmField
		var photoSize: PhotoSize? = null

		@JvmField
		var obj: MessageObject? = null

		@JvmField
		var encryptedChat: EncryptedChat? = null

		@JvmField
		var topMessageId = 0

		@JvmField
		var messageObjects: ArrayList<MessageObject>? = null

		@JvmField
		var messages: ArrayList<Message?>? = null

		@JvmField
		var originalPaths: ArrayList<String?>? = null

		@JvmField
		var extraHashMap: HashMap<Any, Any>? = null

		fun initForGroup(id: Long) {
			type = 4
			groupId = id
			messageObjects = ArrayList()
			messages = ArrayList()
			inputMedias = ArrayList()
			originalPaths = ArrayList()
			parentObjects = ArrayList()
			extraHashMap = HashMap()
			locations = ArrayList()
			httpLocations = ArrayList()
			videoEditedInfos = ArrayList()
		}

		fun addDelayedRequest(req: TLObject?, msgObj: MessageObject?, originalPath: String?, parentObject: Any?, delayedMessage: DelayedMessage?, scheduled: Boolean) {
			val request = DelayedMessageSendAfterRequest()
			request.request = req
			request.msgObj = msgObj
			request.originalPath = originalPath
			request.delayedMessage = delayedMessage
			request.parentObject = parentObject
			request.scheduled = scheduled

			if (requests == null) {
				requests = ArrayList()
			}

			requests?.add(request)
		}

		fun addDelayedRequest(req: TLObject?, msgObjs: List<MessageObject>?, originalPaths: List<String?>?, parentObjects: List<Any?>?, delayedMessage: DelayedMessage?, scheduled: Boolean) {
			val request = DelayedMessageSendAfterRequest()
			request.request = req
			request.msgObjs = msgObjs?.let { ArrayList(it) }
			request.originalPaths = originalPaths?.let { ArrayList(it) }
			request.delayedMessage = delayedMessage
			request.parentObjects = parentObjects?.let { ArrayList(it) }
			request.scheduled = scheduled

			if (requests == null) {
				requests = ArrayList()
			}

			requests?.add(request)
		}

		fun sendDelayedRequests() {
			val requests = requests

			if (requests == null || type != 4 && type != 0) {
				return
			}

			val size = requests.size

			for (a in 0 until size) {
				val request = requests[a]

				when (val innerRequest = request.request) {
					// MARK: uncomment to enable secret chats
//					is TLRPC.TLMessagesSendEncryptedMultiMedia -> {
//						secretChatHelper.performSendEncryptedRequest(innerRequest, this)
//					}

					is TLRPC.TLMessagesSendMultiMedia -> {
						performSendMessageRequestMulti(innerRequest, request.msgObjs, request.originalPaths, request.parentObjects, request.delayedMessage, request.scheduled)
					}

					else -> {
						performSendMessageRequest(innerRequest, request.msgObj, request.originalPath, request.delayedMessage, request.parentObject, request.scheduled)
					}
				}
			}

			this.requests = null
		}

		fun markAsError() {
			if (type == 4) {
				messageObjects?.forEach { obj ->
					messagesStorage.markMessageAsSendError(obj.messageOwner, obj.scheduled)
					obj.messageOwner?.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
					notificationCenter.postNotificationName(NotificationCenter.messageSendError, obj.id)
					processSentMessage(obj.id)
					removeFromUploadingMessages(obj.id, scheduled)
				}

				delayedMessages.remove("group_$groupId")
			}
			else {
				messagesStorage.markMessageAsSendError(obj!!.messageOwner, obj!!.scheduled)
				obj?.messageOwner?.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
				notificationCenter.postNotificationName(NotificationCenter.messageSendError, obj!!.id)
				processSentMessage(obj!!.id)
				removeFromUploadingMessages(obj!!.id, scheduled)
			}

			sendDelayedRequests()
		}
	}

	fun cleanup() {
		delayedMessages.clear()
		unsentMessages.clear()
		sendingMessages.clear()
		editingMessages.clear()
		sendingMessagesIdDialogs.clear()
		uploadMessages.clear()
		uploadingMessagesIdDialogs.clear()
		waitingForLocation.clear()
		waitingForCallback.clear()
		waitingForVote.clear()
		importingHistoryFiles.clear()
		importingHistoryMap.clear()
		importingStickersFiles.clear()
		importingStickersMap.clear()
		locationProvider.stop()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.fileUploadProgressChanged -> {
				val fileName = args[0] as String
				val importingHistory = importingHistoryFiles[fileName]

				if (importingHistory != null) {
					val loadedSize = args[1] as Long
					val totalSize = args[2] as Long
					importingHistory.addUploadProgress(fileName, loadedSize, loadedSize / totalSize.toFloat())
				}
				val importingStickers = importingStickersFiles[fileName]
				if (importingStickers != null) {
					val loadedSize = args[1] as Long
					val totalSize = args[2] as Long
					importingStickers.addUploadProgress(fileName, loadedSize, loadedSize / totalSize.toFloat())
				}
			}

			NotificationCenter.fileUploaded -> {
				val location = args[0] as String
				val file = args[1] as? TLRPC.InputFile
				val encryptedFile = args[2] as? InputEncryptedFile
				val importingHistory = importingHistoryFiles[location]

				if (importingHistory != null && file != null) {
					if (location == importingHistory.historyPath) {
						importingHistory.initImport(file)
					}
					else {
						importingHistory.onMediaImport(location, args[5] as Long, file)
					}
				}

				if (file != null) {
					val importingStickers = importingStickersFiles[location]
					importingStickers?.onMediaImport(location, args[5] as Long, file)
				}

				val arr = delayedMessages[location]

				if (arr != null) {
					var a = 0

					while (a < arr.size) {
						val message = arr[a]
						var media: InputMedia? = null

						when (val sendRequest = message.sendRequest) {
							is TLRPC.TLMessagesSendMedia -> {
								media = sendRequest.media
							}

							is TLRPC.TLMessagesEditMessage -> {
								media = sendRequest.media
							}

							is TLRPC.TLMessagesSendMultiMedia -> {
								media = message.extraHashMap?.get(location) as? InputMedia

								if (media == null) {
									media = sendRequest.multiMedia.firstOrNull()?.media
								}
							}
						}

						if (file != null && media != null) {
							if (message.type == 0) {
								media.file = file
								performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, message, true, null, message.parentObject, message.scheduled)
							}
							else if (message.type == 1) {
								if (media.file == null) {
									media.file = file

									if (media.thumb == null && message.photoSize?.location != null) {
										performSendDelayedMessage(message)
									}
									else {
										performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, message.scheduled)
									}
								}
								else {
									media.thumb = file
									media.flags = media.flags or 4

									performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, message.scheduled)
								}
							}
							else if (message.type == 2) {
								if (media.file == null) {
									media.file = file

									if (media.thumb == null && message.photoSize?.location != null) {
										performSendDelayedMessage(message)
									}
									else {
										performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, message.scheduled)
									}
								}
								else {
									media.thumb = file
									media.flags = media.flags or 4
									performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, message.scheduled)
								}
							}
							else if (message.type == 3) {
								media.file = file
								performSendMessageRequest(message.sendRequest, message.obj, message.originalPath, null, message.parentObject, message.scheduled)
							}
							else if (message.type == 4) {
								if (media is TLRPC.TLInputMediaUploadedDocument) {
									if (media.file == null) {
										media.file = file

										val messageObject = message.extraHashMap!![location + "_i"] as? MessageObject
										val index = message.messageObjects!!.indexOf(messageObject)

										if (index >= 0) {
											stopVideoService(message.messageObjects!![index].messageOwner?.attachPath)
										}

										message.photoSize = message.extraHashMap!![location + "_t"] as PhotoSize?

										if (media.thumb == null && message.photoSize?.location != null) {
											message.performMediaUpload = true
											performSendDelayedMessage(message, index)
										}
										else {
											uploadMultiMedia(message, media, null, location)
										}
									}
									else {
										media.thumb = file
										media.flags = media.flags or 4
										uploadMultiMedia(message, media, null, message.extraHashMap!![location + "_o"] as String?)
									}
								}
								else {
									media.file = file
									uploadMultiMedia(message, media, null, location)
								}
							}

							arr.removeAt(a)

							a--
						}
						// MARK: uncomment to enable secret chats
//						else if (encryptedFile != null && message.sendEncryptedRequest != null) {
//							var decryptedMessage: TLRPC.TLDecryptedMessage? = null
//
//							if (message.type == 4) {
//								val req = message.sendEncryptedRequest as TLRPC.TLMessagesSendEncryptedMultiMedia?
//								val inputEncryptedFile = message.extraHashMap!![location] as InputEncryptedFile?
//								val index = req!!.files.indexOf(inputEncryptedFile)
//
//								if (index >= 0) {
//									req.files[index] = encryptedFile
//
//									if (inputEncryptedFile!!.id == 1L) {
//										message.photoSize = message.extraHashMap!![location + "_t"] as? PhotoSize
//										stopVideoService(message.messageObjects!![index].messageOwner?.attachPath)
//									}
//
//									decryptedMessage = req.messages[index]
//								}
//							}
//							else {
//								decryptedMessage = message.sendEncryptedRequest as TLRPC.TLDecryptedMessage?
//							}
//
//							if (decryptedMessage != null) {
//								if (decryptedMessage.media is TLRPC.TLDecryptedMessageMediaVideo || decryptedMessage.media is TLRPC.TLDecryptedMessageMediaPhoto || decryptedMessage.media is TLRPC.TLDecryptedMessageMediaDocument) {
//									decryptedMessage.media.size = args[5] as Long
//								}
//
//								decryptedMessage.media.key = args[3] as ByteArray
//								decryptedMessage.media.iv = args[4] as ByteArray
//
//								if (message.type == 4) {
//									uploadMultiMedia(message, null, encryptedFile, location)
//								}
//								else {
//									secretChatHelper.performSendEncryptedRequest(decryptedMessage, message.obj!!.messageOwner, message.encryptedChat, encryptedFile, message.originalPath, message.obj)
//								}
//							}
//
//							arr.removeAt(a)
//							a--
//						}

						a++
					}

					if (arr.isEmpty()) {
						delayedMessages.remove(location)
					}
				}
			}

			NotificationCenter.fileUploadFailed -> {
				val location = args[0] as String
				val enc = args[1] as Boolean

				val importingHistory = importingHistoryFiles[location]
				importingHistory?.onFileFailedToUpload(location)

				val importingStickers = importingStickersFiles[location]
				importingStickers?.onFileFailedToUpload(location)

				val arr = delayedMessages[location]

				if (arr != null) {
					var a = 0

					while (a < arr.size) {
						val obj = arr[a]

						if (enc && obj.sendEncryptedRequest != null || !enc && obj.sendRequest != null) {
							obj.markAsError()
							arr.removeAt(a)
							a--
						}

						a++
					}

					if (arr.isEmpty()) {
						delayedMessages.remove(location)
					}
				}
			}

			NotificationCenter.filePreparingStarted -> {
				val messageObject = args[0] as MessageObject

				if (messageObject.id == 0) {
					return
				}

				val arr = delayedMessages[messageObject.messageOwner?.attachPath]

				if (arr != null) {
					for (a in arr.indices) {
						val message = arr[a]

						if (message.type == 4) {
							val index = message.messageObjects!!.indexOf(messageObject)
							message.photoSize = message.extraHashMap!![messageObject.messageOwner?.attachPath + "_t"] as PhotoSize?
							message.performMediaUpload = true
							performSendDelayedMessage(message, index)
							arr.removeAt(a)
							break
						}
						else if (message.obj === messageObject) {
							message.videoEditedInfo = null
							performSendDelayedMessage(message)
							arr.removeAt(a)
							break
						}
					}

					if (arr.isEmpty()) {
						delayedMessages.remove(messageObject.messageOwner?.attachPath)
					}
				}
			}

			NotificationCenter.fileNewChunkAvailable -> {
				val messageObject = args[0] as MessageObject

				if (messageObject.id == 0) {
					return
				}

				val finalPath = args[1] as String
				val availableSize = args[2] as Long
				val finalSize = args[3] as Long
				val isEncrypted = DialogObject.isEncryptedDialog(messageObject.dialogId)

				fileLoader.checkUploadNewDataAvailable(finalPath, isEncrypted, availableSize, finalSize)

				if (finalSize != 0L) {
					stopVideoService(messageObject.messageOwner?.attachPath)

					val arr = delayedMessages[messageObject.messageOwner?.attachPath]

					if (arr != null) {
						for (a in arr.indices) {
							val message = arr[a]

							if (message.type == 4) {
								for (b in message.messageObjects!!.indices) {
									val obj = message.messageObjects!![b]

									if (obj === messageObject) {
										message.obj?.shouldRemoveVideoEditedInfo = true
										obj.messageOwner?.params?.remove("ve")
										obj.messageOwner?.media?.document?.size = finalSize

										messagesStorage.putMessages(listOf(obj.messageOwner!!), false, true, false, 0, obj.scheduled)

										break
									}
								}
							}
							else if (message.obj === messageObject) {
								message.obj!!.shouldRemoveVideoEditedInfo = true
								message.obj!!.messageOwner?.params?.remove("ve")
								message.obj!!.messageOwner?.media?.document?.size = finalSize

								messagesStorage.putMessages(listOf(message.obj!!.messageOwner!!), false, true, false, 0, message.obj!!.scheduled)

								break
							}
						}
					}
				}
			}

			NotificationCenter.filePreparingFailed -> {
				val messageObject = args[0] as MessageObject

				if (messageObject.id == 0) {
					return
				}

				val finalPath = args[1] as String

				stopVideoService(messageObject.messageOwner?.attachPath)

				val arr = delayedMessages[finalPath]

				if (arr != null) {
					var a = 0

					while (a < arr.size) {
						val message = arr[a]

						if (message.type == 4) {
							for (b in message.messages!!.indices) {
								if (message.messageObjects!![b] === messageObject) {
									message.markAsError()
									arr.removeAt(a)
									a--
									break
								}
							}
						}
						else if (message.obj === messageObject) {
							message.markAsError()
							arr.removeAt(a)
							a--
						}

						a++
					}

					if (arr.isEmpty()) {
						delayedMessages.remove(finalPath)
					}
				}
			}

			NotificationCenter.httpFileDidLoad -> {
				val path = args[0] as String
				val arr = delayedMessages[path]

				if (arr != null) {
					for (a in arr.indices) {
						val message = arr[a]
						val messageObject: MessageObject?
						var fileType = -1

						when (message.type) {
							0 -> {
								fileType = 0
								messageObject = message.obj
							}

							2 -> {
								fileType = 1
								messageObject = message.obj
							}

							4 -> {
								messageObject = message.extraHashMap!![path] as? MessageObject

								fileType = if (messageObject?.document != null) {
									1
								}
								else {
									0
								}
							}

							else -> {
								messageObject = null
							}
						}
						if (fileType == 0) {
							val md5 = Utilities.MD5(path) + "." + ImageLoader.getHttpUrlExtension(path, "file")
							val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5)

							Utilities.globalQueue.postRunnable {
								val photo = generatePhotoSizes(cacheFile.toString(), null)

								AndroidUtilities.runOnUIThread {
									if (photo != null) {
										messageObject?.messageOwner?.media?.photo = photo
										messageObject?.messageOwner?.attachPath = cacheFile.toString()

										messagesStorage.putMessages(listOf(messageObject!!.messageOwner!!), false, true, false, 0, messageObject.scheduled)

										notificationCenter.postNotificationName(NotificationCenter.updateMessageMedia, messageObject.messageOwner)

										message.photoSize = photo.sizes[photo.sizes.size - 1]
										message.locationParent = photo
										message.httpLocation = null

										if (message.type == 4) {
											message.performMediaUpload = true
											performSendDelayedMessage(message, message.messageObjects!!.indexOf(messageObject))
										}
										else {
											performSendDelayedMessage(message)
										}
									}
									else {
										FileLog.e("can't load image $path to file $cacheFile")
										message.markAsError()
									}
								}
							}
						}
						else if (fileType == 1) {
							val md5 = Utilities.MD5(path) + ".gif"
							val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5)

							Utilities.globalQueue.postRunnable {
								val document = message.obj?.document

								if (document != null) {
									if (document.thumbs.isNullOrEmpty() || document.thumbs?.firstOrNull()?.location is TLRPC.TLFileLocationUnavailable) {
										try {
											val bitmap = ImageLoader.loadBitmap(cacheFile.absolutePath, null, 90f, 90f, true)

											if (bitmap != null) {
												document.thumbs?.clear()
												document.thumbs?.add(ImageLoader.scaleAndSaveImage(bitmap, 90f, 90f, 55, message.sendEncryptedRequest != null))

												bitmap.recycle()
											}
										}
										catch (e: Exception) {
											document.thumbs?.clear()
											FileLog.e(e)
										}
									}
								}

								AndroidUtilities.runOnUIThread {
									message.httpLocation = null
									message.obj?.messageOwner?.attachPath = cacheFile.toString()

									if (!document?.thumbs.isNullOrEmpty()) {
										val photoSize = document?.thumbs?.firstOrNull()

										if (photoSize !is TLRPC.TLPhotoStrippedSize) {
											message.photoSize = photoSize
											message.locationParent = document
										}
									}

									messagesStorage.putMessages(listOf(messageObject!!.messageOwner!!), false, true, false, 0, messageObject.scheduled)

									message.performMediaUpload = true

									performSendDelayedMessage(message)

									notificationCenter.postNotificationName(NotificationCenter.updateMessageMedia, message.obj!!.messageOwner)
								}
							}
						}
					}

					delayedMessages.remove(path)
				}
			}

			NotificationCenter.fileLoaded -> {
				val path = args[0] as String
				val arr = delayedMessages[path]

				if (arr != null) {
					for (a in arr.indices) {
						performSendDelayedMessage(arr[a])
					}

					delayedMessages.remove(path)
				}
			}

			NotificationCenter.httpFileDidFailedLoad, NotificationCenter.fileLoadFailed -> {
				val path = args[0] as String
				val arr = delayedMessages[path]

				if (arr != null) {
					for (a in arr.indices) {
						arr[a].markAsError()
					}

					delayedMessages.remove(path)
				}
			}
		}
	}

	private fun revertEditingMessageObject(`object`: MessageObject) {
		`object`.cancelEditing = true

		`object`.messageOwner?.media = `object`.previousMedia
		`object`.messageOwner?.message = `object`.previousMessage
		`object`.messageOwner?.entities = `object`.previousMessageEntities
		`object`.messageOwner?.attachPath = `object`.previousAttachPath
		`object`.messageOwner?.sendState = MessageObject.MESSAGE_SEND_STATE_SENT

		if (`object`.messageOwner?.entities != null) {
			`object`.messageOwner?.flags = `object`.messageOwner!!.flags or 128
		}
		else {
			`object`.messageOwner?.flags = `object`.messageOwner!!.flags and 128.inv()
		}

		`object`.previousMedia = null
		`object`.previousMessage = null
		`object`.previousMessageEntities = null
		`object`.previousAttachPath = null
		`object`.videoEditedInfo = null
		`object`.type = -1
		`object`.setType()
		`object`.caption = null

		if (`object`.type != MessageObject.TYPE_COMMON) {
			`object`.generateCaption()
		}
		else {
			`object`.resetLayout()
		}

		messagesStorage.putMessages(listOf(`object`.messageOwner!!), false, true, false, 0, `object`.scheduled)

		val arrayList = ArrayList<MessageObject?>()
		arrayList.add(`object`)

		notificationCenter.postNotificationName(NotificationCenter.replaceMessagesObjects, `object`.dialogId, arrayList)
	}

	fun cancelSendingMessage(`object`: MessageObject) {
		val arrayList = ArrayList<MessageObject>()
		arrayList.add(`object`)
		cancelSendingMessage(arrayList)
	}

	fun cancelSendingMessage(objects: List<MessageObject>) {
		val keysToRemove = mutableListOf<String>()
		val checkReadyToSendGroups = mutableListOf<DelayedMessage>()
		val messageIds = mutableListOf<Int>()
		var enc = false
		var scheduled = false
		var dialogId: Long = 0

		for (c in objects.indices) {
			val `object` = objects[c]

			if (`object`.scheduled) {
				scheduled = true
			}

			dialogId = `object`.dialogId
			messageIds.add(`object`.id)

			val sendingMessage = removeFromSendingMessages(`object`.id, `object`.scheduled)

			if (sendingMessage != null) {
				connectionsManager.cancelRequest(sendingMessage.reqId, true)
			}

			for ((key, messages) in delayedMessages) {
				for (a in messages.indices) {
					val message = messages[a]

					if (message.type == 4) {
						var index = -1
						var messageObject: MessageObject? = null

						for (b in message.messageObjects!!.indices) {
							messageObject = message.messageObjects!![b]

							if (messageObject.id == `object`.id) {
								index = b
								removeFromUploadingMessages(`object`.id, `object`.scheduled)
								break
							}
						}

						if (index >= 0) {
							message.messageObjects!!.removeAt(index)
							message.messages!!.removeAt(index)
							message.originalPaths!!.removeAt(index)

							if (message.parentObjects!!.isNotEmpty()) {
								message.parentObjects!!.removeAt(index)
							}

							if (message.sendRequest != null) {
								val request = message.sendRequest as TLRPC.TLMessagesSendMultiMedia?
								request!!.multiMedia.removeAt(index)
							}
							// MARK: uncomment to enable secret chats
//							else {
//								val request = message.sendEncryptedRequest as TLRPC.TLMessagesSendEncryptedMultiMedia?
//								request!!.messages.removeAt(index)
//								request.files.removeAt(index)
//							}

							MediaController.getInstance().cancelVideoConvert(`object`)

							val keyToRemove = message.extraHashMap?.get(messageObject as Any) as? String

							if (keyToRemove != null) {
								keysToRemove.add(keyToRemove)
							}

							if (message.messageObjects!!.isEmpty()) {
								message.sendDelayedRequests()
							}
							else {
								if (message.finalGroupMessage == `object`.id) {
									val prevMessage = message.messageObjects!![message.messageObjects!!.size - 1]

									message.finalGroupMessage = prevMessage.id

									prevMessage.messageOwner?.params?.put("final", "1")

									val messagesRes = TLRPC.TLMessagesMessages()
									messagesRes.messages.add(prevMessage.messageOwner!!)

									messagesStorage.putMessages(messagesRes, message.peer, -2, 0, false, scheduled)
								}

								if (!checkReadyToSendGroups.contains(message)) {
									checkReadyToSendGroups.add(message)
								}
							}
						}
						break
					}
					else if (message.obj!!.id == `object`.id) {
						removeFromUploadingMessages(`object`.id, `object`.scheduled)
						messages.removeAt(a)
						message.sendDelayedRequests()
						MediaController.getInstance().cancelVideoConvert(message.obj)

						if (messages.isEmpty()) {
							keysToRemove.add(key)

							if (message.sendEncryptedRequest != null) {
								enc = true
							}
						}

						break
					}
				}
			}
		}

		for (a in keysToRemove.indices) {
			val key = keysToRemove[a]

			if (key.startsWith("http")) {
				ImageLoader.getInstance().cancelLoadHttpFile(key)
			}
			else {
				fileLoader.cancelFileUpload(key, enc)
			}

			stopVideoService(key)

			delayedMessages.remove(key)
		}

		for (message in checkReadyToSendGroups) {
			sendReadyToSendGroup(message, add = false, check = true)
		}

		if (objects.size == 1 && objects[0].isEditing && objects[0].previousMedia != null) {
			revertEditingMessageObject(objects[0])
		}
		else {
			messagesController.deleteMessages(messageIds, null, null, dialogId, false, scheduled)
		}
	}

	fun retrySendMessage(messageObject: MessageObject, unsent: Boolean): Boolean {
		if (messageObject.id >= 0) {
			if (messageObject.isEditing) {
				editMessage(messageObject, null, null, null, null, null, true, messageObject)
			}

			return false
		}

		// MARK: uncomment to enable secret chats
//		if (messageObject.messageOwner?.action is TLRPC.TLMessageEncryptedAction) {
//			val encId = DialogObject.getEncryptedChatId(messageObject.dialogId)
//			val encryptedChat = messagesController.getEncryptedChat(encId)
//
//			if (encryptedChat == null) {
//				messagesStorage.markMessageAsSendError(messageObject.messageOwner, messageObject.scheduled)
//				messageObject.messageOwner?.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
//				notificationCenter.postNotificationName(NotificationCenter.messageSendError, messageObject.id)
//				processSentMessage(messageObject.id)
//				return false
//			}
//
//			if (messageObject.messageOwner?.randomId == 0L) {
//				messageObject.messageOwner?.randomId = nextRandomId
//			}
//
//			when (messageObject.messageOwner?.action?.encryptedAction) {
//				is TLRPC.TLDecryptedMessageActionSetMessageTTL -> {
//					secretChatHelper.sendTTLMessage(encryptedChat, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionDeleteMessages -> {
//					secretChatHelper.sendMessagesDeleteMessage(encryptedChat, null, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionFlushHistory -> {
//					secretChatHelper.sendClearHistoryMessage(encryptedChat, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionNotifyLayer -> {
//					secretChatHelper.sendNotifyLayerMessage(encryptedChat, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionReadMessages -> {
//					secretChatHelper.sendMessagesReadMessage(encryptedChat, null, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionScreenshotMessages -> {
//					secretChatHelper.sendScreenshotMessage(encryptedChat, null, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionTyping -> {
//					// unused
//				}
//
//				is TLRPC.TLDecryptedMessageActionResend -> {
//					secretChatHelper.sendResendMessage(encryptedChat, 0, 0, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionCommitKey -> {
//					secretChatHelper.sendCommitKeyMessage(encryptedChat, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionAbortKey -> {
//					secretChatHelper.sendAbortKeyMessage(encryptedChat, messageObject.messageOwner, 0)
//				}
//
//				is TLRPC.TLDecryptedMessageActionRequestKey -> {
//					secretChatHelper.sendRequestKeyMessage(encryptedChat, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionAcceptKey -> {
//					secretChatHelper.sendAcceptKeyMessage(encryptedChat, messageObject.messageOwner)
//				}
//
//				is TLRPC.TLDecryptedMessageActionNoop -> {
//					secretChatHelper.sendNoopMessage(encryptedChat, messageObject.messageOwner)
//				}
//			}
//
//			return true
//		}
//		else
		if (messageObject.messageOwner?.action is TLRPC.TLMessageActionScreenshotTaken) {
			val user = messagesController.getUser(messageObject.dialogId)
			sendScreenshotMessage(user, messageObject.replyMsgId, messageObject.messageOwner)
		}

		if (unsent) {
			unsentMessages.put(messageObject.id, messageObject)
		}

		sendMessage(messageObject)

		return true
	}

	fun processSentMessage(id: Int) {
		val prevSize = unsentMessages.size

		unsentMessages.remove(id)

		if (prevSize != 0 && unsentMessages.isEmpty()) {
			checkUnsentMessages()
		}
	}

	private fun processForwardFromMyName(messageObject: MessageObject?, did: Long) {
		if (messageObject == null) {
			return
		}

		if (messageObject.messageOwner?.media != null && messageObject.messageOwner?.media !is TLRPC.TLMessageMediaEmpty && messageObject.messageOwner?.media !is TLRPC.TLMessageMediaWebPage && messageObject.messageOwner?.media !is TLRPC.TLMessageMediaGame && messageObject.messageOwner?.media !is TLRPC.TLMessageMediaInvoice) {
			var params: HashMap<String, String>? = null

			if (DialogObject.isEncryptedDialog(did) && messageObject.messageOwner?.peerId != null && (messageObject.messageOwner?.media?.photo is TLRPC.TLPhoto || messageObject.messageOwner?.media?.document is TLRPC.TLDocument)) {
				params = HashMap()
				params["parentObject"] = "sent_" + messageObject.messageOwner?.peerId?.channelId + "_" + messageObject.id
			}

			if (messageObject.messageOwner?.media?.photo is TLRPC.TLPhoto) {
				sendMessage(messageObject.messageOwner?.media?.photo as TLRPC.TLPhoto, null, did, messageObject.replyMessageObject, null, messageObject.messageOwner?.message, messageObject.messageOwner?.entities, null, params, true, 0, messageObject.messageOwner?.media?.ttlSeconds ?: 0, messageObject, false)
			}
			else if (messageObject.messageOwner?.media?.document is TLRPC.TLDocument) {
				sendMessage(messageObject.messageOwner?.media?.document as TLRPC.TLDocument, null, messageObject.messageOwner?.attachPath, did, messageObject.replyMessageObject, null, messageObject.messageOwner?.message, messageObject.messageOwner?.entities, null, params, true, 0, messageObject.messageOwner?.media?.ttlSeconds ?: 0, messageObject, null, false)
			}
			else if (messageObject.messageOwner?.media is TLRPC.TLMessageMediaVenue || messageObject.messageOwner?.media is TLRPC.TLMessageMediaGeo) {
				sendMessage(messageObject.messageOwner?.media, did, messageObject.replyMessageObject, null, null, null, true, 0)
			}
			// MARK: we do not have phone contacts in scheme
//			else if (messageObject.messageOwner?.media?.phoneNumber != null) {
//				val user: User = TLRPC.TLUserContact_old2()
//				// user.phone = messageObject.messageOwner.media.phoneNumber;
//				user.firstName = messageObject.messageOwner?.media?.firstName
//				user.lastName = messageObject.messageOwner?.media?.lastName
//				user.id = messageObject.messageOwner?.media?.userId ?: 0
//
//				sendMessage(user, did, messageObject.replyMessageObject, null, null, null, true, 0, messageObject.isMediaSale, messageObject.mediaSaleHash)
//			}
			else if (!DialogObject.isEncryptedDialog(did)) {
				sendMessage(listOf(messageObject), did, forwardFromMyName = true, hideCaption = false, notify = true, scheduleDate = 0)
			}
		}
		else if (messageObject.messageOwner?.message != null) {
			val webPage = (messageObject.messageOwner?.media as? TLRPC.TLMessageMediaWebPage)?.webpage
			val entities: List<MessageEntity>?

			if (!messageObject.messageOwner?.entities.isNullOrEmpty()) {
				entities = mutableListOf()

				for (a in messageObject.messageOwner?.entities!!.indices) {
					val entity = messageObject.messageOwner?.entities!![a]

					if (entity is TLRPC.TLMessageEntityBold || entity is TLRPC.TLMessageEntityItalic || entity is TLRPC.TLMessageEntityPre || entity is TLRPC.TLMessageEntityCode || entity is TLRPC.TLMessageEntityTextUrl || entity is TLRPC.TLMessageEntitySpoiler || entity is TLRPC.TLMessageEntityCustomEmoji) {
						entities.add(entity)
					}
				}
			}
			else {
				entities = null
			}

			sendMessage(messageObject.messageOwner?.message, did, messageObject.replyMessageObject, null, webPage, true, entities, null, null, true, 0, null, false)
		}
		else if (DialogObject.isEncryptedDialog(did)) {
			val arrayList = ArrayList<MessageObject>()
			arrayList.add(messageObject)
			sendMessage(arrayList, did, forwardFromMyName = true, hideCaption = false, notify = true, scheduleDate = 0)
		}
	}

	fun sendScreenshotMessage(user: User?, messageId: Int, resendMessage: Message?) {
		if (user == null || messageId == 0 || user.id == userConfig.getClientUserId() || user !is TLRPC.TLUser) {
			return
		}

		val req = TLRPC.TLMessagesSendScreenshotNotification()

		req.peer = TLRPC.TLInputPeerUser().also {
			it.accessHash = user.accessHash
			it.userId = user.id
		}

		val message: Message

		if (resendMessage != null) {
			message = resendMessage

			req.replyToMsgId = messageId
			req.randomId = resendMessage.randomId
		}
		else {
			message = TLRPC.TLMessageService()
			message.randomId = nextRandomId
			message.dialogId = user.id
			message.unread = true
			message.out = true
			message.id = userConfig.newMessageId
			message.localId = message.id

			message.fromId = TLRPC.TLPeerUser().also { it.userId = userConfig.getClientUserId() }

			message.flags = message.flags or 256
			message.flags = message.flags or 8

			message.replyTo = TLRPC.TLMessageReplyHeader().also { it.replyToMsgId = messageId }

			message.peerId = TLRPC.TLPeerUser().also { it.userId = user.id }

			message.date = connectionsManager.currentTime
			message.action = TLRPC.TLMessageActionScreenshotTaken()

			userConfig.saveConfig(false)
		}

		req.randomId = message.randomId

		val newMsgObj = MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = true)
		newMsgObj.messageOwner?.sendState = MessageObject.MESSAGE_SEND_STATE_SENDING
		newMsgObj.wasJustSent = true

		val objArr = ArrayList<MessageObject>()
		objArr.add(newMsgObj)

		messagesController.updateInterfaceWithMessages(message.dialogId, objArr, false)
		notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

		val arr = ArrayList<Message>()
		arr.add(message)

		messagesStorage.putMessages(arr, false, true, false, 0, false)

		performSendMessageRequest(req, newMsgObj, null, null, null, false)
	}

	fun sendSticker(document: TLRPC.Document?, query: String?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, parentObject: Any?, sendAnimationData: SendAnimationData?, notify: Boolean, scheduleDate: Int, updateStickersOrder: Boolean) {
		@Suppress("NAME_SHADOWING") var document = document as? TLRPC.TLDocument ?: return

		if (DialogObject.isEncryptedDialog(peer)) {
			val encryptedId = DialogObject.getEncryptedChatId(peer)

			messagesController.getEncryptedChat(encryptedId) ?: return

			val newDocument = TLRPC.TLDocument()
			newDocument.id = document.id
			newDocument.accessHash = document.accessHash
			newDocument.date = document.date
			newDocument.mimeType = document.mimeType
			newDocument.fileReference = document.fileReference

			if (newDocument.fileReference == null) {
				newDocument.fileReference = ByteArray(0)
			}

			newDocument.size = document.size
			newDocument.dcId = document.dcId
			newDocument.attributes.addAll(document.attributes)

			if (newDocument.mimeType == null) {
				newDocument.mimeType = ""
			}

			var thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)

			if (thumb is TLRPC.PhotoSize || thumb is TLRPC.TLPhotoSizeProgressive) {
				val file = FileLoader.getInstance(currentAccount).getPathToAttach(thumb, true)

				if (file.exists()) {
					try {
						// val len = file.length().toInt()
						val arr = ByteArray(file.length().toInt())

						val reader = RandomAccessFile(file, "r")
						reader.readFully(arr)

						val newThumb = TLRPC.TLPhotoCachedSize()

//						val fileLocation = TLRPC.TLFileLocation()
//						fileLocation.dcId = (thumb.location as? TLRPC.TLFileLocation)?.dcId ?: 0
//						fileLocation.volumeId = thumb.location?.volumeId ?: 0L
//						fileLocation.localId = thumb.location?.localId ?: 0
						//fileLocation.secret = thumb.location?.secret ?: 0L

//						newThumb.location = fileLocation
						newThumb.size = thumb.size
						newThumb.w = thumb.w
						newThumb.h = thumb.h
						newThumb.type = thumb.type
						newThumb.bytes = arr

						newDocument.thumbs.add(newThumb)
						newDocument.flags = newDocument.flags or 1
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			if (newDocument.thumbs.isEmpty()) {
				thumb = TLRPC.TLPhotoSizeEmpty()
				thumb.type = "s"

				newDocument.thumbs.add(thumb)
			}

			document = newDocument
		}

		if (MessageObject.isGifDocument(document)) {
			mediaSendQueue.postRunnable {
				val bitmapFinal = arrayOfNulls<Bitmap>(1)
				val keyFinal = arrayOfNulls<String>(1)
				val mediaLocationKey = ImageLocation.getForDocument(document)?.getKey(null, null, false)

				val docExt = when (document.mimeType) {
					"video/mp4" -> ".mp4"
					"video/x-matroska" -> ".mkv"
					else -> ""
				}

				var docFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), mediaLocationKey + docExt)

				if (!docFile.exists()) {
					docFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_VIDEO), mediaLocationKey + docExt)
				}

				ensureMediaThumbExists(accountInstance, false, document, docFile.absolutePath, null, 0)

				keyFinal[0] = getKeyForPhotoSize(accountInstance, FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320), bitmapFinal, blur = true, forceCache = true)

				AndroidUtilities.runOnUIThread {
					if (bitmapFinal[0] != null && keyFinal[0] != null) {
						ImageLoader.getInstance().putImageToCache(bitmapFinal[0]?.toDrawable(ApplicationLoader.applicationContext.resources), keyFinal[0], false)
					}

					sendMessage(document, null, null, peer, replyToMsg, replyToTopMsg, null, null, null, null, notify, scheduleDate, 0, parentObject, sendAnimationData, updateStickersOrder = false)
				}
			}
		}
		else {
			val params: HashMap<String, String>?

			if (!query.isNullOrEmpty()) {
				params = HashMap()
				params["query"] = query
			}
			else {
				params = null
			}

			sendMessage(document, null, null, peer, replyToMsg, replyToTopMsg, null, null, null, params, notify, scheduleDate, 0, parentObject, sendAnimationData, updateStickersOrder)
		}
	}

	fun sendMessage(messages: List<MessageObject>?, peer: Long, forwardFromMyName: Boolean, hideCaption: Boolean, notify: Boolean, scheduleDate: Int): Int {
		if (messages.isNullOrEmpty()) {
			return 0
		}

		var sendResult = 0
		val myId = userConfig.getClientUserId()
		var isChannel = false

		if (!DialogObject.isEncryptedDialog(peer)) {
			val peerId = messagesController.getPeer(peer)
			var isSignature = false
			var canSendStickers = true
			var canSendMedia = true
			var canSendPolls = true
			var canSendPreview = true
			var canSendVoiceMessages = true
			var rank: String? = null
			var linkedToGroup: Long = 0

			val chat: Chat?

			if (DialogObject.isUserDialog(peer)) {
				messagesController.getUser(peer) ?: return 0

				chat = null

				val userFull = messagesController.getUserFull(peer)

				if (userFull != null) {
					canSendVoiceMessages = !userFull.voiceMessagesForbidden
				}
			}
			else {
				chat = messagesController.getChat(-peer)

				if (ChatObject.isChannel(chat)) {
					isSignature = chat.signatures
					isChannel = !chat.megagroup

					if (isChannel && chat.hasLink) {
						val chatFull = messagesController.getChatFull(chat.id)

						if (chatFull != null) {
							linkedToGroup = chatFull.linkedChatId
						}
					}
				}

				if (chat != null) {
					rank = messagesController.getAdminRank(chat.id, myId)
				}

				canSendStickers = ChatObject.canSendStickers(chat)
				canSendMedia = ChatObject.canSendMedia(chat)
				canSendPreview = ChatObject.canSendEmbed(chat)
				canSendPolls = ChatObject.canSendPolls(chat)
			}

			val groupsMap = LongSparseArray<Long>()
			var objArr = ArrayList<MessageObject>()
			var arr = ArrayList<Message>()
			var randomIds = ArrayList<Long>()
			var ids = ArrayList<Int>()
			var messagesByRandomIds = LongSparseArray<Message>()
			val inputPeer = messagesController.getInputPeer(peer)
			// val lastDialogId: Long = 0
			val toMyself = peer == myId
			// var lastGroupedId = 0L

			for (a in messages.indices) {
				val msgObj = messages[a]

				if (msgObj.id <= 0 || msgObj.needDrawBluredPreview()) {
					if (msgObj.type == MessageObject.TYPE_COMMON && !TextUtils.isEmpty(msgObj.messageText)) {
						val webPage = msgObj.messageOwner?.media?.webpage
						sendMessage(msgObj.messageText.toString(), peer, null, null, webPage, webPage != null, msgObj.messageOwner?.entities, null, null, notify, scheduleDate, null, false)
					}

					continue
				}

				val mediaIsSticker = msgObj.isSticker || msgObj.isAnimatedSticker || msgObj.isGif || msgObj.isGame

				if (!canSendStickers && mediaIsSticker) {
					if (sendResult == 0) {
						sendResult = if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_STICKERS)) 4 else 1
					}

					continue
				}
				else if (!canSendMedia && (msgObj.messageOwner?.media is TLRPC.TLMessageMediaPhoto || msgObj.messageOwner?.media is TLRPC.TLMessageMediaDocument) && !mediaIsSticker) {
					if (sendResult == 0) {
						sendResult = if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MEDIA)) 5 else 2
					}

					continue
				}
				else if (!canSendPolls && msgObj.messageOwner?.media is TLRPC.TLMessageMediaPoll) {
					if (sendResult == 0) {
						sendResult = if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_POLLS)) 6 else 3
					}

					continue
				}
				else if (!canSendVoiceMessages && MessageObject.isVoiceMessage(msgObj.messageOwner)) {
					if (sendResult == 0) {
						sendResult = 7
					}

					continue
				}
				else if (!canSendVoiceMessages && MessageObject.isRoundVideoMessage(msgObj.messageOwner)) {
					if (sendResult == 0) {
						sendResult = 8
					}

					continue
				}

				val newMsg = TLRPC.TLMessage()

				if (!forwardFromMyName) {
					val forwardFromSaved = msgObj.dialogId == myId && msgObj.isFromUser && msgObj.messageOwner?.fromId?.userId == myId

					if (msgObj.isForwarded) {
						newMsg.fwdFrom = TLRPC.TLMessageFwdHeader().also {
							if (msgObj.messageOwner!!.fwdFrom!!.flags and 1 != 0) {
								it.flags = it.flags or 1
								it.fromId = msgObj.messageOwner!!.fwdFrom!!.fromId
							}

							if (msgObj.messageOwner!!.fwdFrom!!.flags and 32 != 0) {
								it.flags = it.flags or 32
								it.fromName = msgObj.messageOwner!!.fwdFrom!!.fromName
							}

							if (msgObj.messageOwner!!.fwdFrom!!.flags and 4 != 0) {
								it.flags = it.flags or 4
								it.channelPost = msgObj.messageOwner!!.fwdFrom!!.channelPost
							}

							if (msgObj.messageOwner!!.fwdFrom!!.flags and 8 != 0) {
								it.flags = it.flags or 8
								it.postAuthor = msgObj.messageOwner!!.fwdFrom!!.postAuthor
							}

							if ((peer == myId || isChannel) && msgObj.messageOwner!!.fwdFrom!!.flags and 16 != 0 && !isReplyUser(msgObj.dialogId)) {
								it.flags = it.flags or 16
								it.savedFromPeer = msgObj.messageOwner!!.fwdFrom!!.savedFromPeer
								it.savedFromMsgId = msgObj.messageOwner!!.fwdFrom!!.savedFromMsgId
							}

							it.date = msgObj.messageOwner!!.fwdFrom!!.date
						}

						newMsg.flags = TLRPC.MESSAGE_FLAG_FWD
					}
					else if (!forwardFromSaved) { //if (!toMyself || !msgObj.isOutOwner())
						val fromId = msgObj.fromChatId

						newMsg.fwdFrom = TLRPC.TLMessageFwdHeader().also {
							it.channelPost = msgObj.id
							it.flags = it.flags or 4

							if (msgObj.isFromUser) {
								it.fromId = msgObj.messageOwner!!.fromId
								it.flags = it.flags or 1
							}
							else {
								it.fromId = TLRPC.TLPeerChannel().also {
									it.channelId = msgObj.messageOwner!!.peerId!!.channelId
								}

								it.flags = it.flags or 1

								if (msgObj.messageOwner!!.post && fromId > 0) {
									it.fromId = msgObj.messageOwner?.fromId ?: msgObj.messageOwner?.peerId
								}
							}

							if (msgObj.messageOwner?.postAuthor != null) {
								// newMsg.fwdFrom.postAuthor = msgObj.messageOwner.postAuthor
								// newMsg.fwdFrom.flags |= 8
							}
							else if (!msgObj.isOutOwner && fromId > 0 && msgObj.messageOwner!!.post) {
								val signUser = messagesController.getUser(fromId)

								if (signUser != null) {
									it.postAuthor = ContactsController.formatName(signUser.firstName, signUser.lastName)
									it.flags = it.flags or 8
								}
							}
						}

						newMsg.date = msgObj.messageOwner!!.date
						newMsg.flags = TLRPC.MESSAGE_FLAG_FWD
					}

					if (peer == myId && newMsg.fwdFrom != null) {
						newMsg.fwdFrom?.flags = newMsg.fwdFrom!!.flags or 16
						newMsg.fwdFrom?.savedFromMsgId = msgObj.id
						newMsg.fwdFrom?.savedFromPeer = msgObj.messageOwner!!.peerId

						if (newMsg.fwdFrom?.savedFromPeer?.userId == myId) {
							newMsg.fwdFrom?.savedFromPeer?.userId = msgObj.dialogId
						}
					}
				}

				newMsg.params = mutableMapOf<String, String>().also {
					it["fwd_id"] = "" + msgObj.id
					it["fwd_peer"] = "" + msgObj.dialogId
				}

				if (!msgObj.messageOwner!!.restrictionReason.isNullOrEmpty()) {
					newMsg.restrictionReason.clear()
					newMsg.restrictionReason.addAll(msgObj.messageOwner!!.restrictionReason!!)
					newMsg.flags = newMsg.flags or 4194304
				}

				if (!canSendPreview && msgObj.messageOwner?.media is TLRPC.TLMessageMediaWebPage) {
					newMsg.media = TLRPC.TLMessageMediaEmpty()
				}
				else {
					newMsg.media = msgObj.messageOwner?.media
				}

				if (newMsg.media != null) {
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
				}

				if (msgObj.messageOwner?.viaBotId != 0L) {
					newMsg.viaBotId = msgObj.messageOwner!!.viaBotId
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_BOT_ID
				}

				if (linkedToGroup != 0L) {
					newMsg.replies = TLRPC.TLMessageReplies()
					newMsg.replies?.comments = true
					newMsg.replies?.channelId = linkedToGroup
					newMsg.replies?.flags = newMsg.replies!!.flags or 1

					newMsg.flags = newMsg.flags or 8388608
				}

				if (!hideCaption || newMsg.media == null) {
					newMsg.message = msgObj.messageOwner?.message
				}

				if (newMsg.message == null) {
					newMsg.message = ""
				}

				newMsg.fwdMsgId = msgObj.id
				newMsg.attachPath = msgObj.messageOwner?.attachPath

				msgObj.messageOwner?.entities?.let {
					newMsg.entities.addAll(it)
				}

				if (msgObj.messageOwner?.replyMarkup is TLRPC.TLReplyInlineMarkup) {
					newMsg.replyMarkup = TLRPC.TLReplyInlineMarkup()

					var dropMarkup = false
					var b = 0
					val n = msgObj.messageOwner?.replyMarkup?.rows?.size ?: 0

					while (b < n) {
						val oldRow = msgObj.messageOwner!!.replyMarkup!!.rows[b]
						var newRow: TLRPC.TLKeyboardButtonRow? = null
						var c = 0
						val n2 = oldRow.buttons.size

						while (c < n2) {
							var button = oldRow.buttons[c]

							if (button is TLRPC.TLKeyboardButtonUrlAuth || button is TLRPC.TLKeyboardButtonUrl || button is TLRPC.TLKeyboardButtonSwitchInline || button is TLRPC.TLKeyboardButtonBuy) {
								if (button is TLRPC.TLKeyboardButtonUrlAuth) {
									val auth = TLRPC.TLKeyboardButtonUrlAuth()
									auth.flags = button.flags

									if (button.fwdText != null) {
										auth.fwdText = button.fwdText
										auth.text = auth.fwdText
									}
									else {
										auth.text = button.text
									}

									auth.url = button.url
									auth.buttonId = button.buttonId
									button = auth
								}

								if (newRow == null) {
									newRow = TLRPC.TLKeyboardButtonRow()
									newMsg.replyMarkup?.rows?.add(newRow)
								}

								newRow.buttons.add(button)
							}
							else {
								dropMarkup = true
								break
							}

							c++
						}

						if (dropMarkup) {
							break
						}

						b++
					}

					if (!dropMarkup) {
						newMsg.flags = newMsg.flags or 64
					}
					else {
						msgObj.messageOwner?.replyMarkup = null
						newMsg.flags = newMsg.flags and 64.inv()
					}
				}

				if (newMsg.entities.isNotEmpty()) {
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
				}

				if (newMsg.attachPath == null) {
					newMsg.attachPath = ""
				}

				newMsg.id = userConfig.newMessageId
				newMsg.localId = newMsg.id
				newMsg.out = true

				if (msgObj.messageOwner!!.groupedId != 0L) {
					var gId = groupsMap[msgObj.messageOwner!!.groupedId]

					if (gId == null) {
						gId = Utilities.random.nextLong()
						groupsMap.put(msgObj.messageOwner!!.groupedId, gId)
					}

					newMsg.groupedId = gId
					newMsg.flags = newMsg.flags or 131072
				}

				if (peerId.channelId != 0L && isChannel) {
					if (isSignature) {
						newMsg.fromId = TLRPC.TLPeerUser()
						newMsg.fromId?.userId = myId
					}
					else {
						newMsg.fromId = peerId
					}

					newMsg.post = true
				}
				else {
					val fromPeerId = ChatObject.getSendAsPeerId(chat, messagesController.getChatFull(-peer), true)

					if (fromPeerId == myId) {
						newMsg.fromId = TLRPC.TLPeerUser()
						newMsg.fromId?.userId = myId
						newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_FROM_ID
					}
					else {
						newMsg.fromId = messagesController.getPeer(fromPeerId)

						if (rank != null) {
							newMsg.postAuthor = rank
							newMsg.flags = newMsg.flags or 65536
						}
					}
				}

				if (newMsg.randomId == 0L) {
					newMsg.randomId = nextRandomId
				}

				randomIds.add(newMsg.randomId)

				messagesByRandomIds.put(newMsg.randomId, newMsg)

				ids.add(newMsg.fwdMsgId)

				newMsg.date = if (scheduleDate != 0) scheduleDate else connectionsManager.currentTime

				if (inputPeer is TLRPC.TLInputPeerChannel && isChannel) {
					if (scheduleDate == 0) {
						newMsg.views = 1
						newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_VIEWS
					}
				}
				else {
					if (msgObj.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_HAS_VIEWS != 0) {
						if (scheduleDate == 0) {
							newMsg.views = msgObj.messageOwner!!.views
							newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_VIEWS
						}
					}

					newMsg.unread = true
				}

				newMsg.dialogId = peer
				newMsg.peerId = peerId

				if (MessageObject.isVoiceMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
					if (inputPeer is TLRPC.TLInputPeerChannel && msgObj.channelId != 0L) {
						newMsg.mediaUnread = msgObj.isContentUnread
					}
					else {
						newMsg.mediaUnread = true
					}
				}

				val newMsgObj = MessageObject(currentAccount, newMsg, generateLayout = true, checkMediaExists = true)
				newMsgObj.scheduled = scheduleDate != 0
				newMsgObj.messageOwner?.sendState = MessageObject.MESSAGE_SEND_STATE_SENDING
				newMsgObj.wasJustSent = true

				objArr.add(newMsgObj)

				arr.add(newMsg)

				if (msgObj.replyMessageObject != null) {
					for (i in messages.indices) {
						if (messages[i].id == msgObj.replyMessageObject!!.id) {
							newMsgObj.messageOwner?.replyMessage = msgObj.replyMessageObject!!.messageOwner
							newMsgObj.replyMessageObject = msgObj.replyMessageObject
							break
						}
					}
				}

				putToSendingMessages(newMsg, scheduleDate != 0)

				//val differentDialog = false

				FileLog.d("forward message user_id = " + inputPeer.userId + " chat_id = " + inputPeer.chatId + " channel_id = " + inputPeer.channelId + " access_hash = " + inputPeer.accessHash)

				if (arr.size == 100 || a == messages.size - 1 || a != messages.size - 1 && messages[a + 1].dialogId != msgObj.dialogId) {
					messagesStorage.putMessages(ArrayList(arr), false, true, false, 0, scheduleDate != 0)
					messagesController.updateInterfaceWithMessages(peer, objArr, scheduleDate != 0)
					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
					userConfig.saveConfig(false)

					val req = TLRPC.TLMessagesForwardMessages()
					req.toPeer = inputPeer
					req.silent = !notify || MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_$peer", false)

					if (scheduleDate != 0) {
						req.scheduleDate = scheduleDate
						req.flags = req.flags or 1024
					}

					if (msgObj.messageOwner?.peerId is TLRPC.TLPeerChannel) {
						val channel = messagesController.getChat(msgObj.messageOwner?.peerId?.channelId)

						req.fromPeer = TLRPC.TLInputPeerChannel().also {
							it.channelId = msgObj.messageOwner?.peerId?.channelId ?: 0L
						}

						if (channel != null) {
							req.fromPeer?.accessHash = channel.accessHash
						}
					}
					else {
						req.fromPeer = TLRPC.TLInputPeerEmpty()
					}

					req.randomId.addAll(randomIds)
					req.id.addAll(ids)
					req.dropAuthor = forwardFromMyName
					req.dropMediaCaptions = hideCaption
					// req.withMyScore = messages.size == 1 && ((messages[0].messageOwner as? TLRPC.TLMessage)?.withMyScore ?: false)

					val newMsgObjArr = arr
					val newMsgArr = ArrayList(objArr)
					val messagesByRandomIdsFinal = messagesByRandomIds
					val scheduledOnline = scheduleDate == 0x7FFFFFFE

					connectionsManager.sendRequest(req, { response, error ->
						if (error == null) {
							val newMessagesByIds = SparseLongArray()
							val updates = response as Updates

							run {
								var a1 = 0

								while (a1 < updates.updates.size) {
									val update = updates.updates[a1]

									if (update is TLRPC.TLUpdateMessageID) {
										newMessagesByIds.put(update.id, update.randomId)
										updates.updates.removeAt(a1)
										a1--
									}

									a1++
								}
							}

							var value = messagesController.dialogs_read_outbox_max[peer]

							if (value == null) {
								value = messagesStorage.getDialogReadMax(true, peer)
								messagesController.dialogs_read_outbox_max[peer] = value
							}

							var sentCount = 0
							var a1 = 0

							while (a1 < updates.updates.size) {
								val update = updates.updates[a1]

								if (update is TLRPC.TLUpdateNewMessage || update is TLRPC.TLUpdateNewChannelMessage || update is TLRPC.TLUpdateNewScheduledMessage) {
									var currentSchedule = scheduleDate != 0

									updates.updates.removeAt(a1)

									a1--

									val message: Message?

									when (update) {
										is TLRPC.TLUpdateNewMessage -> {
											message = update.message
											messagesController.processNewDifferenceParams(-1, update.pts, -1, update.ptsCount)
										}

										is TLRPC.TLUpdateNewScheduledMessage -> {
											message = update.message
										}

										else -> {
											val updateNewChannelMessage = update as TLRPC.TLUpdateNewChannelMessage
											message = updateNewChannelMessage.message
											messagesController.processNewChannelDifferenceParams(updateNewChannelMessage.pts, updateNewChannelMessage.ptsCount, message?.peerId?.channelId ?: 0L)
										}
									}

									if (message != null) {
										if (scheduledOnline && message.date != 0x7FFFFFFE) {
											currentSchedule = false
										}

										ImageLoader.saveMessageThumbs(message)

										if (!currentSchedule) {
											message.unread = value < message.id
										}

										if (toMyself) {
											message.out = true
											message.unread = false
											message.mediaUnread = false
										}

										val randomId = newMessagesByIds[message.id]

										if (randomId != 0L) {
											val newMsgObj1 = messagesByRandomIdsFinal[randomId]

											if (newMsgObj1 == null) {
												a1++
												continue
											}

											val index = newMsgObjArr.indexOf(newMsgObj1)

											if (index == -1) {
												a1++
												continue
											}

											val msgObj1 = newMsgArr[index]

											newMsgObjArr.removeAt(index)
											newMsgArr.removeAt(index)

											val oldId = newMsgObj1.id

											val sentMessages = ArrayList<Message>()
											sentMessages.add(message)

											msgObj1.messageOwner?.postAuthor = message.postAuthor

											if (message.flags and 33554432 != 0) {
												msgObj1.messageOwner?.ttlPeriod = message.ttlPeriod
												msgObj1.messageOwner?.flags = msgObj1.messageOwner!!.flags or 33554432
											}

											updateMediaPaths(msgObj1, message, message.id, null, true)

											val existFlags = msgObj1.mediaExistanceFlags

											newMsgObj1.id = message.id

											sentCount++

											if (scheduleDate != 0 && !currentSchedule) {
												AndroidUtilities.runOnUIThread {
													val messageIds = ArrayList<Int>()
													messageIds.add(oldId)

													messagesController.deleteMessages(messageIds, null, null, newMsgObj1.dialogId, forAll = false, scheduled = true)

													messagesStorage.storageQueue.postRunnable {
														messagesStorage.putMessages(sentMessages, true, false, false, 0, false)

														AndroidUtilities.runOnUIThread {
															messagesController.updateInterfaceWithMessages(newMsgObj1.dialogId, listOf(MessageObject(msgObj.currentAccount, msgObj.messageOwner!!, generateLayout = true, checkMediaExists = true)), false)
															mediaDataController.increasePeerRating(newMsgObj1.dialogId)
															processSentMessage(oldId)
															removeFromSendingMessages(oldId, scheduleDate != 0)
														}
													}
												}
											}
											else {
												messagesStorage.storageQueue.postRunnable {
													messagesStorage.updateMessageStateAndId(newMsgObj1.randomId, MessageObject.getPeerId(peerId), oldId, newMsgObj1.id, 0, false, if (scheduleDate != 0) 1 else 0)
													messagesStorage.putMessages(sentMessages, true, false, false, 0, scheduleDate != 0)

													AndroidUtilities.runOnUIThread {
														newMsgObj1.sendState = MessageObject.MESSAGE_SEND_STATE_SENT
														mediaDataController.increasePeerRating(peer)
														notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, message.id, message, peer, 0L, existFlags, scheduleDate != 0)
														processSentMessage(oldId)
														removeFromSendingMessages(oldId, scheduleDate != 0)
													}
												}
											}
										}
									}
								}

								a1++
							}

							if (updates.updates.isNotEmpty()) {
								messagesController.processUpdates(updates, false)
							}

							statsController.incrementSentItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_MESSAGES, sentCount)
						}
						else {
							AndroidUtilities.runOnUIThread {
								AlertsCreator.processError(currentAccount, error, null, req)
							}
						}

						for (a1 in newMsgObjArr.indices) {
							val newMsgObj1 = newMsgObjArr[a1]

							messagesStorage.markMessageAsSendError(newMsgObj1, scheduleDate != 0)

							AndroidUtilities.runOnUIThread {
								newMsgObj1.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
								notificationCenter.postNotificationName(NotificationCenter.messageSendError, newMsgObj1.id)
								processSentMessage(newMsgObj1.id)
								removeFromSendingMessages(newMsgObj1.id, scheduleDate != 0)
							}
						}
					}, ConnectionsManager.RequestFlagCanCompress or ConnectionsManager.RequestFlagInvokeAfter)

					if (a != messages.size - 1) {
						objArr = ArrayList()
						arr = ArrayList()
						randomIds = ArrayList()
						ids = ArrayList()
						messagesByRandomIds = LongSparseArray()
					}
				}
			}
		}
		else {
			var canSendVoiceMessages = true
			val encryptedChat = messagesController.getEncryptedChat(peer.toInt())
			val userId = encryptedChat?.userId ?: 0

			if (DialogObject.isUserDialog(userId)) {
				val sendToUser = messagesController.getUser(userId)

				if (sendToUser != null) {
					val userFull = messagesController.getUserFull(userId)

					if (userFull != null) {
						canSendVoiceMessages = !userFull.voiceMessagesForbidden
					}
				}
			}

			for (msgObj in messages) {
				if (!canSendVoiceMessages && MessageObject.isVoiceMessage(msgObj.messageOwner)) {
					if (sendResult == 0) {
						sendResult = 7
					}
				}
				else if (!canSendVoiceMessages && MessageObject.isRoundVideoMessage(msgObj.messageOwner)) {
					if (sendResult == 0) {
						sendResult = 8
					}
				}
			}

			if (sendResult == 0) {
				for (a in messages.indices) {
					processForwardFromMyName(messages[a], peer)
				}
			}
		}

		return sendResult
	}

	private fun writePreviousMessageData(message: Message?, data: SerializedData?) {
		if (message == null || data == null) {
			return
		}

		if (message.media == null) {
			val media = TLRPC.TLMessageMediaEmpty()
			media.serializeToStream(data)
		}
		else {
			message.media?.serializeToStream(data)
		}

		data.writeString(message.message ?: "")
		data.writeString(message.attachPath ?: "")

		val entities = message.entities

		if (!entities.isNullOrEmpty()) {
			data.writeInt32(entities.size)

			for (entity in entities) {
				entity.serializeToStream(data)
			}
		}
		else {
			data.writeInt32(0)
		}
	}

	fun editMessage(messageObject: MessageObject?, photo: TLRPC.TLPhoto?, videoEditedInfo: VideoEditedInfo?, document: TLRPC.TLDocument?, path: String?, params: MutableMap<String, String>?, retry: Boolean, parentObject: Any?) {
		@Suppress("NAME_SHADOWING") var photo = photo
		@Suppress("NAME_SHADOWING") var videoEditedInfo = videoEditedInfo
		@Suppress("NAME_SHADOWING") var document = document
		@Suppress("NAME_SHADOWING") var path = path
		@Suppress("NAME_SHADOWING") var params = params
		@Suppress("NAME_SHADOWING") var parentObject = parentObject

		if (messageObject == null) {
			return
		}

		if (params == null) {
			params = mutableMapOf()
		}

		val newMsg = messageObject.messageOwner!!
		messageObject.cancelEditing = false

		if (messageObject.editingMessage.isNullOrEmpty() && MediaDataController.getMediaType(messageObject.messageOwner) == MediaDataController.TEXT_ONLY) {
			Toast.makeText(ApplicationLoader.applicationContext, ApplicationLoader.applicationContext.getString(R.string.txt_error_empty_message), Toast.LENGTH_SHORT).show()
			return
		}

		try {
			var type = -1
			var delayedMessage: DelayedMessage? = null
			val peer = messageObject.dialogId
			var supportsSendingNewEntities = true

			if (DialogObject.isEncryptedDialog(peer)) {
				val encryptedId = DialogObject.getEncryptedChatId(peer)
				val encryptedChat = messagesController.getEncryptedChat(encryptedId)

				if (encryptedChat == null || AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) < 101) {
					supportsSendingNewEntities = false
				}
			}
			if (retry) {
				when (messageObject.messageOwner?.media) {
					is TLRPC.TLMessageMediaWebPage, null, is TLRPC.TLMessageMediaEmpty -> {
						type = 1
					}

					is TLRPC.TLMessageMediaPhoto -> {
						photo = messageObject.messageOwner?.media?.photo as? TLRPC.TLPhoto
						type = 2
					}

					is TLRPC.TLMessageMediaDocument -> {
						document = messageObject.messageOwner?.media?.document as? TLRPC.TLDocument

						type = if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
							3
						}
						else {
							7
						}

						videoEditedInfo = messageObject.videoEditedInfo
					}
				}

				params = newMsg.params

				if (parentObject == null && params != null && params.containsKey("parentObject")) {
					parentObject = params["parentObject"]
				}

				messageObject.editingMessage = newMsg.message
				messageObject.editingMessageEntities = newMsg.entities
				path = newMsg.attachPath
			}
			else {
				messageObject.previousMedia = newMsg.media
				messageObject.previousMessage = newMsg.message
				messageObject.previousMessageEntities = newMsg.entities
				messageObject.previousAttachPath = newMsg.attachPath

//				var media = newMsg.media
//
//				if (media == null) {
//					media = TLRPC.TLMessageMediaEmpty()
//				}

				val serializedDataCalc = SerializedData(true)

				writePreviousMessageData(newMsg, serializedDataCalc)

				val prevMessageData = SerializedData(serializedDataCalc.length())
				writePreviousMessageData(newMsg, prevMessageData)

				params["prevMedia"] = Base64.encodeToString(prevMessageData.toByteArray(), Base64.DEFAULT)

				prevMessageData.cleanup()

				if (photo != null) {
					newMsg.media = TLRPC.TLMessageMediaPhoto()
					newMsg.media?.flags = newMsg.media!!.flags or 3
					newMsg.media?.photo = photo

					type = 2

					if (!path.isNullOrEmpty() && path.startsWith("http")) {
						newMsg.attachPath = path
					}
					else {
						val location1 = photo.sizes[photo.sizes.size - 1].location
						newMsg.attachPath = FileLoader.getInstance(currentAccount).getPathToAttach(location1, true).toString()
					}
				}
				else if (document != null) {
					newMsg.media = TLRPC.TLMessageMediaDocument()
					newMsg.media?.flags = newMsg.media!!.flags or 3
					newMsg.media?.document = document

					type = if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
						3
					}
					else {
						7
					}

					if (videoEditedInfo != null) {
						val ve = videoEditedInfo.string
						params["ve"] = ve
					}

					newMsg.attachPath = path
				}
				else {
					type = 1
				}

				newMsg.params = params
				newMsg.sendState = MessageObject.MESSAGE_SEND_STATE_EDITING
			}

			if (newMsg.attachPath == null) {
				newMsg.attachPath = ""
			}

			newMsg.localId = 0

			if (messageObject.type == MessageObject.TYPE_VIDEO || videoEditedInfo != null || messageObject.type == MessageObject.TYPE_VOICE && !newMsg.attachPath.isNullOrEmpty()) {
				messageObject.attachPathExists = true
			}

			if (messageObject.videoEditedInfo != null && videoEditedInfo == null) {
				videoEditedInfo = messageObject.videoEditedInfo
			}

			if (!retry) {
				if (messageObject.editingMessage != null) {
					val oldMessage = newMsg.message

					newMsg.message = messageObject.editingMessage?.toString()

					messageObject.caption = null

					if (type == 1) {
						if (messageObject.editingMessageEntities != null) {
							newMsg.entities = ArrayList(messageObject.editingMessageEntities!!)
							newMsg.flags = newMsg.flags or 128
						}
						else if (!TextUtils.equals(oldMessage, newMsg.message)) {
							newMsg.flags = newMsg.flags and 128.inv()
						}
					}
					else {
						if (messageObject.editingMessageEntities != null) {
							newMsg.entities = ArrayList(messageObject.editingMessageEntities!!)
							newMsg.flags = newMsg.flags or 128
						}
						else {
							val message = arrayOf(messageObject.editingMessage)
							val entities = mediaDataController.getEntities(message, supportsSendingNewEntities)

							if (!entities.isNullOrEmpty()) {
								newMsg.entities = ArrayList(entities)
								newMsg.flags = newMsg.flags or 128
							}
							else if (!TextUtils.equals(oldMessage, newMsg.message)) {
								newMsg.flags = newMsg.flags and 128.inv()
							}
						}

						messageObject.generateCaption()
					}
				}

				messagesStorage.putMessages(listOf(newMsg), false, true, false, 0, messageObject.scheduled)

				messageObject.type = -1
				messageObject.setType()

				if (type == 1) {
					if (messageObject.messageOwner?.media is TLRPC.TLMessageMediaPhoto || messageObject.messageOwner?.media is TLRPC.TLMessageMediaDocument) {
						messageObject.generateCaption()
					}
					else {
						messageObject.resetLayout()
						messageObject.checkLayout()
					}
				}

				messageObject.createMessageSendInfo()

				notificationCenter.postNotificationName(NotificationCenter.replaceMessagesObjects, peer, listOf(messageObject))
			}

			var originalPath: String? = null

			if (params != null && params.containsKey("originalPath")) {
				originalPath = params["originalPath"]
			}

			var performMediaUpload = false

			if (type in 1..3 || type in 5..8) {
				var inputMedia: InputMedia? = null

				if (type == 1) {
					// unused
				}
				else if (type == 2) {
					val uploadedPhoto = TLRPC.TLInputMediaUploadedPhoto()

					if (params != null) {
						val masks = params["masks"]

						if (masks != null) {
							val serializedData = SerializedData(Utilities.hexToBytes(masks))
							val count = serializedData.readInt32(false)

							for (a in 0 until count) {
								uploadedPhoto.stickers.add(InputDocument.deserialize(serializedData, serializedData.readInt32(false), false) ?: TLRPC.TLInputDocumentEmpty())
							}

							uploadedPhoto.flags = uploadedPhoto.flags or 1
							serializedData.cleanup()
						}
					}

					if (photo!!.accessHash == 0L) {
						inputMedia = uploadedPhoto
						performMediaUpload = true
					}
					else {
						val media = TLRPC.TLInputMediaPhoto()

						media.id = TLRPC.TLInputPhoto().also {
							it.id = photo.id
							it.accessHash = photo.accessHash
							it.fileReference = photo.fileReference ?: ByteArray(0)
						}

						inputMedia = media
					}

					delayedMessage = DelayedMessage(peer)
					delayedMessage.type = 0
					delayedMessage.obj = messageObject
					delayedMessage.originalPath = originalPath
					delayedMessage.parentObject = parentObject
					delayedMessage.inputUploadMedia = uploadedPhoto
					delayedMessage.performMediaUpload = performMediaUpload

					if (!path.isNullOrEmpty() && path.startsWith("http")) {
						delayedMessage.httpLocation = path
					}
					else {
						delayedMessage.photoSize = photo.sizes[photo.sizes.size - 1]
						delayedMessage.locationParent = photo
					}
				}
				else if (type == 3 && document != null) {
					val uploadedDocument = TLRPC.TLInputMediaUploadedDocument()

					if (params != null) {
						val masks = params["masks"]

						if (masks != null) {
							val serializedData = SerializedData(Utilities.hexToBytes(masks))
							val count = serializedData.readInt32(false)

							for (a in 0 until count) {
								uploadedDocument.stickers.add(InputDocument.deserialize(serializedData, serializedData.readInt32(false), false) ?: TLRPC.TLInputDocumentEmpty())
							}

							uploadedDocument.flags = uploadedDocument.flags or 1

							serializedData.cleanup()
						}
					}

					uploadedDocument.mimeType = document.mimeType
					uploadedDocument.attributes.addAll(document.attributes)

					if (!messageObject.isGif && (videoEditedInfo == null || !videoEditedInfo.muted)) {
						uploadedDocument.nosoundVideo = true
						FileLog.d("nosound_video = true")
					}

					if (document.accessHash == 0L) {
						inputMedia = uploadedDocument
						performMediaUpload = true
					}
					else {
						val media = TLRPC.TLInputMediaDocument()

						media.id = TLRPC.TLInputDocument().also {
							it.id = document.id
							it.accessHash = document.accessHash
							it.fileReference = document.fileReference ?: ByteArray(0)
						}

						inputMedia = media
					}

					delayedMessage = DelayedMessage(peer)
					delayedMessage.type = 1
					delayedMessage.obj = messageObject
					delayedMessage.originalPath = originalPath
					delayedMessage.parentObject = parentObject
					delayedMessage.inputUploadMedia = uploadedDocument
					delayedMessage.performMediaUpload = performMediaUpload

					if (document.thumbs.isNotEmpty()) {
						val photoSize = document.thumbs[0]

						if (photoSize !is TLRPC.TLPhotoStrippedSize) {
							delayedMessage.photoSize = photoSize
							delayedMessage.locationParent = document
						}
					}

					delayedMessage.videoEditedInfo = videoEditedInfo
				}
				else if (type == 7 && document != null) {
					val http = false

					val uploadedDocument = TLRPC.TLInputMediaUploadedDocument()
					uploadedDocument.mimeType = document.mimeType
					uploadedDocument.attributes.addAll(document.attributes)

					if (document.accessHash == 0L) {
						inputMedia = uploadedDocument
						performMediaUpload = true
					}
					else {
						val media = TLRPC.TLInputMediaDocument()

						media.id = TLRPC.TLInputDocument().also {
							it.id = document.id
							it.accessHash = document.accessHash
							it.fileReference = document.fileReference ?: ByteArray(0)
						}

						inputMedia = media
					}

					if (!http) {
						delayedMessage = DelayedMessage(peer)
						delayedMessage.originalPath = originalPath
						delayedMessage.type = 2
						delayedMessage.obj = messageObject

						if (document.thumbs.isNotEmpty()) {
							val photoSize = document.thumbs[0]

							if (photoSize !is TLRPC.TLPhotoStrippedSize) {
								delayedMessage.photoSize = photoSize
								delayedMessage.locationParent = document
							}
						}

						delayedMessage.parentObject = parentObject
						delayedMessage.inputUploadMedia = uploadedDocument
						delayedMessage.performMediaUpload = performMediaUpload
					}
				}

				val reqSend: TLObject

				val request = TLRPC.TLMessagesEditMessage()
				request.id = messageObject.id
				request.peer = messagesController.getInputPeer(peer)

				if (inputMedia != null) {
					request.flags = request.flags or 16384
					request.media = inputMedia
				}

				if (messageObject.scheduled) {
					request.scheduleDate = messageObject.messageOwner!!.date
					request.flags = request.flags or 32768
				}

				if (messageObject.editingMessage != null) {
					request.message = messageObject.editingMessage.toString()
					request.flags = request.flags or 2048
					request.noWebpage = !messageObject.editingMessageSearchWebPage

					if (messageObject.editingMessageEntities != null) {
						request.entities.addAll(messageObject.editingMessageEntities!!)
						request.flags = request.flags or 8
					}
					else {
						val message = arrayOf(messageObject.editingMessage)
						val entities = mediaDataController.getEntities(message, supportsSendingNewEntities)

						if (!entities.isNullOrEmpty()) {
							request.entities.addAll(entities)
							request.flags = request.flags or 8
						}
					}

					messageObject.editingMessage = null
					messageObject.editingMessageEntities = null
				}

				if (delayedMessage != null) {
					delayedMessage.sendRequest = request
				}

				reqSend = request

				if (type == 1) {
					performSendMessageRequest(reqSend, messageObject, null, delayedMessage, parentObject, messageObject.scheduled)
				}
				else if (type == 2) {
					if (performMediaUpload) {
						performSendDelayedMessage(delayedMessage)
					}
					else {
						performSendMessageRequest(reqSend, messageObject, originalPath, null, true, delayedMessage, parentObject, messageObject.scheduled)
					}
				}
				else if (type == 3) {
					if (performMediaUpload) {
						performSendDelayedMessage(delayedMessage)
					}
					else {
						performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, messageObject.scheduled)
					}
				}
				else if (type == 6) {
					performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, messageObject.scheduled)
				}
				else if (type == 7) {
					if (performMediaUpload) {
						performSendDelayedMessage(delayedMessage)
					}
					else {
						performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, messageObject.scheduled)
					}
				}
				else if (type == 8) {
					if (performMediaUpload) {
						performSendDelayedMessage(delayedMessage)
					}
					else {
						performSendMessageRequest(reqSend, messageObject, originalPath, delayedMessage, parentObject, messageObject.scheduled)
					}
				}
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
			revertEditingMessageObject(messageObject)
		}
	}

	fun editMessage(messageObject: MessageObject, message: String?, searchLinks: Boolean, fragment: BaseFragment?, entities: ArrayList<MessageEntity>?, scheduleDate: Int): Int {
		if (fragment == null || fragment.parentActivity == null) {
			return 0
		}

		val req = TLRPC.TLMessagesEditMessage()
		req.peer = messagesController.getInputPeer(messageObject.dialogId)

		if (message != null) {
			req.message = message
			req.flags = req.flags or 2048
			req.noWebpage = !searchLinks
		}

		req.id = messageObject.id

		if (!entities.isNullOrEmpty()) {
			req.entities.addAll(entities)
			req.flags = req.flags or 8
		}

		if (scheduleDate != 0) {
			req.scheduleDate = scheduleDate
			req.flags = req.flags or 32768
		}

		return connectionsManager.sendRequest(req) { response, error ->
			if (error == null) {
				messagesController.processUpdates(response as Updates?, false)
			}
			else {
				AndroidUtilities.runOnUIThread {
					AlertsCreator.processError(currentAccount, error, fragment, req)
				}
			}
		}
	}

	private fun sendLocation(location: Location) {
		val mediaGeo = TLRPC.TLMessageMediaGeo()

		mediaGeo.geo = TLRPC.TLGeoPoint().also {
			it.lat = AndroidUtilities.fixLocationCoordinate(location.latitude)
			it.lon = AndroidUtilities.fixLocationCoordinate(location.longitude)
		}

		for ((_, messageObject) in waitingForLocation) {
			sendMessage(mediaGeo, messageObject.dialogId, messageObject, null, null, null, true, 0)
		}
	}

	fun sendCurrentLocation(messageObject: MessageObject?, button: KeyboardButton?) {
		if (messageObject == null || button == null) {
			return
		}

		val key = messageObject.dialogId.toString() + "_" + messageObject.id + "_" + Utilities.bytesToHex(button.data) + "_" + if (button is TLRPC.TLKeyboardButtonGame) "1" else "0"

		waitingForLocation[key] = messageObject

		locationProvider.start()
	}

	fun isSendingCurrentLocation(messageObject: MessageObject?, button: KeyboardButton?): Boolean {
		if (messageObject == null || button == null) {
			return false
		}

		val key = messageObject.dialogId.toString() + "_" + messageObject.id + "_" + Utilities.bytesToHex(button.data) + "_" + if (button is TLRPC.TLKeyboardButtonGame) "1" else "0"

		return waitingForLocation.containsKey(key)
	}

	fun sendNotificationCallback(dialogId: Long, msgId: Int, data: ByteArray?) {
		AndroidUtilities.runOnUIThread {
			val key = dialogId.toString() + "_" + msgId + "_" + Utilities.bytesToHex(data) + "_" + 0

			waitingForCallback[key] = true

			if (DialogObject.isUserDialog(dialogId)) {
				var user = messagesController.getUser(dialogId)

				if (user == null) {
					user = messagesStorage.getUserSync(dialogId)

					if (user != null) {
						messagesController.putUser(user, true)
					}
				}
			}
			else {
				var chat = messagesController.getChat(-dialogId)

				if (chat == null) {
					chat = messagesStorage.getChatSync(-dialogId)

					if (chat != null) {
						messagesController.putChat(chat, true)
					}
				}
			}

			val req = TLRPC.TLMessagesGetBotCallbackAnswer()
			req.peer = messagesController.getInputPeer(dialogId)
			req.msgId = msgId
			req.game = false

			if (data != null) {
				req.flags = req.flags or 1
				req.data = data
			}

			connectionsManager.sendRequest(req, { _, _ ->
				AndroidUtilities.runOnUIThread {
					waitingForCallback.remove(key)
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)

			messagesController.markDialogAsRead(dialogId, msgId, msgId, 0, false, 0, 0, true, 0)
		}
	}

	fun isSendingVote(messageObject: MessageObject?): ByteArray? {
		if (messageObject == null) {
			return null
		}

		val key = "poll_" + messageObject.pollId

		return waitingForVote[key]
	}

	fun sendVote(messageObject: MessageObject?, answers: List<TLRPC.TLPollAnswer>?, finishRunnable: Runnable?): Int {
		if (messageObject == null) {
			return 0
		}

		val key = "poll_" + messageObject.pollId

		if (waitingForCallback.containsKey(key)) {
			return 0
		}

		val req = TLRPC.TLMessagesSendVote()
		req.msgId = messageObject.id
		req.peer = messagesController.getInputPeer(messageObject.dialogId)

		val options: ByteArray

		if (answers != null) {
			options = ByteArray(answers.size)

			for (a in answers.indices) {
				val answer = answers[a]

				answer.option?.let {
					req.options.add(it)
					options[a] = it[0]
				}
			}
		}
		else {
			options = ByteArray(0)
		}

		waitingForVote[key] = options

		return connectionsManager.sendRequest(req) { response: TLObject?, error: TLRPC.TLError? ->
			if (error == null) {
				voteSendTime.put(messageObject.pollId, 0L)
				messagesController.processUpdates(response as Updates?, false)
				voteSendTime.put(messageObject.pollId, SystemClock.elapsedRealtime())
			}
			AndroidUtilities.runOnUIThread {
				waitingForVote.remove(key)
				finishRunnable?.run()
			}
		}
	}

	fun getVoteSendTime(pollId: Long): Long {
		return voteSendTime.get(pollId, 0L)
	}

	fun like(message: Message, callback: Runnable?) {
		val channelId = message.peerId?.channelId ?: run {
			callback?.run()
			return
		}

		val req = ElloRpc.likeMessage(messageId = message.id, userId = UserConfig.getInstance(currentAccount).clientUserId, channelId = channelId)
		val likesCount = message.likes

		connectionsManager.sendRequest(req) { response, error ->
			var ok = false

			if (response is TLRPC.TLBizDataRaw) {
				val res = response.readData<ElloRpc.SimpleStringStatusResponse>()

				if (res?.status == "success") {
					ok = true
				}
			}
			else if (error != null) {
				ok = error.text?.contains("already") == true
			}

			if (ok) {
				AndroidUtilities.runOnUIThread {
					message.likes = likesCount + 1
					message.isLiked = true

					messagesStorage.putMessages(java.util.ArrayList(listOf(message)), true, true, true, 0, false)

					callback?.run()
				}
			}
		}
	}

	fun dislike(message: Message, callback: Runnable?) {
		val channelId = message.peerId?.channelId ?: run {
			callback?.run()
			return
		}

		val req = ElloRpc.revokeLikeFromMessage(messageId = message.id, userId = UserConfig.getInstance(currentAccount).clientUserId, channelId = channelId)
		val likesCount = message.likes

		connectionsManager.sendRequest(req) { response, error ->
			var ok = false

			if (response is TLRPC.TLBizDataRaw) {
				val res = response.readData<ElloRpc.SimpleStringStatusResponse>()

				if (res?.status == "success") {
					ok = true
				}
			}
			else if (error != null) {
				ok = error.text?.contains("already") == true
			}

			if (ok) {
				AndroidUtilities.runOnUIThread {
					message.likes = (likesCount - 1).coerceAtLeast(0)
					message.isLiked = false

					messagesStorage.putMessages(java.util.ArrayList(listOf(message)), true, true, true, 0, false)

					callback?.run()
				}
			}
		}
	}

	fun sendReaction(messageObject: MessageObject?, visibleReactions: List<VisibleReaction>?, addedReaction: VisibleReaction?, big: Boolean, addToRecent: Boolean, parentFragment: ChatActivity?, callback: Runnable?) {
		if (messageObject == null || parentFragment == null) {
			return
		}

		val req = TLRPC.TLMessagesSendReaction()

		if (messageObject.messageOwner!!.isThreadMessage && messageObject.messageOwner!!.fwdFrom != null) {
			req.peer = messagesController.getInputPeer(messageObject.fromChatId)
			req.msgId = messageObject.messageOwner?.fwdFrom?.savedFromMsgId ?: 0
		}
		else {
			req.peer = messagesController.getInputPeer(messageObject.dialogId)
			req.msgId = messageObject.id
		}

		req.addToRecent = addToRecent

		if (addToRecent && addedReaction != null) {
			MediaDataController.getInstance(currentAccount).recentReactions.add(0, ReactionsUtils.toTLReaction(addedReaction))
		}

		if (!visibleReactions.isNullOrEmpty()) {
			for (i in visibleReactions.indices) {
				val visibleReaction = visibleReactions[i]

				if (visibleReaction.documentId != 0L) {
					val reactionCustomEmoji = TLRPC.TLReactionCustomEmoji()
					reactionCustomEmoji.documentId = visibleReaction.documentId
					req.reaction.add(reactionCustomEmoji)
					req.flags = req.flags or 1
				}
				else if (visibleReaction.emojicon != null) {
					val defaultReaction = TLRPC.TLReactionEmoji()
					defaultReaction.emoticon = visibleReaction.emojicon
					req.reaction.add(defaultReaction)
					req.flags = req.flags or 1
				}
			}
		}

		if (big) {
			req.flags = req.flags or 2
			req.big = true
		}

		connectionsManager.sendRequest(req) { response, _ ->
			if (response != null) {
				messagesController.processUpdates(response as? Updates, false)

				if (callback != null) {
					AndroidUtilities.runOnUIThread(callback)
				}
			}
		}
	}

	fun requestUrlAuth(url: String, parentFragment: ChatActivity, ask: Boolean) {
		val req = TLRPC.TLMessagesRequestUrlAuth()
		req.url = url
		req.flags = req.flags or 4

		connectionsManager.sendRequest(req, { response, _ ->
			if (response != null) {
				when (response) {
					is TLRPC.TLUrlAuthResultRequest -> {
						parentFragment.showRequestUrlAlert(response, req, url, ask)
					}

					is TLRPC.TLUrlAuthResultAccepted -> {
						response.url?.let {
							AlertsCreator.showOpenUrlAlert(parentFragment, it, punycode = false, ask = false)
						}
					}

					is TLRPC.TLUrlAuthResultDefault -> {
						AlertsCreator.showOpenUrlAlert(parentFragment, url, false, ask)
					}
				}
			}
			else {
				AlertsCreator.showOpenUrlAlert(parentFragment, url, false, ask)
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	fun sendCallback(cache: Boolean, messageObject: MessageObject?, button: KeyboardButton?, parentFragment: ChatActivity?) {
		sendCallback(cache, messageObject, button, null, null, parentFragment)
	}

	fun sendCallback(cache: Boolean, messageObject: MessageObject?, button: KeyboardButton?, srp: InputCheckPasswordSRP?, passwordFragment: TwoStepVerificationActivity?, parentFragment: ChatActivity?) {
		if (messageObject == null || button == null || parentFragment == null) {
			return
		}

		val cacheFinal: Boolean
		val type: Int

		when (button) {
			is TLRPC.TLKeyboardButtonUrlAuth -> {
				cacheFinal = false
				type = 3
			}

			is TLRPC.TLKeyboardButtonGame -> {
				cacheFinal = false
				type = 1
			}

			else -> {
				cacheFinal = cache
				type = if (button is TLRPC.TLKeyboardButtonBuy) {
					2
				}
				else {
					0
				}
			}
		}

		val key = messageObject.dialogId.toString() + "_" + messageObject.id + "_" + Utilities.bytesToHex(button.data) + "_" + type

		waitingForCallback[key] = true

		val request = arrayOfNulls<TLObject>(1)

		val requestDelegate = RequestDelegate { response, error ->
			AndroidUtilities.runOnUIThread {
				waitingForCallback.remove(key)

				if (cacheFinal && response == null) {
					sendCallback(false, messageObject, button, parentFragment)
				}
				else if (response != null) {
					if (passwordFragment != null) {
						passwordFragment.needHideProgress()
						passwordFragment.finishFragment()
					}

					var uid = messageObject.fromChatId

					if (messageObject.messageOwner?.viaBotId != 0L) {
						uid = messageObject.messageOwner!!.viaBotId
					}

					var name: String? = null

					if (uid > 0) {
						val user = messagesController.getUser(uid)

						if (user != null) {
							name = ContactsController.formatName(user.firstName, user.lastName)
						}
					}
					else {
						val chat = messagesController.getChat(-uid)

						if (chat != null) {
							name = chat.title
						}
					}

					if (name == null) {
						name = "bot"
					}

					if (button is TLRPC.TLKeyboardButtonUrlAuth) {
						when (response) {
							is TLRPC.TLUrlAuthResultRequest -> {
								button.url?.let {
									parentFragment.showRequestUrlAlert(response, request[0] as TLRPC.TLMessagesRequestUrlAuth, it, false)
								}
							}

							is TLRPC.TLUrlAuthResultAccepted -> {
								response.url?.let {
									AlertsCreator.showOpenUrlAlert(parentFragment, it, punycode = false, ask = false)
								}
							}

							is TLRPC.TLUrlAuthResultDefault -> {
								button.url?.let {
									AlertsCreator.showOpenUrlAlert(parentFragment, it, punycode = false, ask = true)
								}
							}
						}
					}
					else if (button is TLRPC.TLKeyboardButtonBuy) {
						// unused
					}
					else {
						val res = response as? TLRPC.TLMessagesBotCallbackAnswer

						if (!cacheFinal && res?.cacheTime != 0 && !button.requiresPassword) {
							messagesStorage.saveBotCache(key, res)
						}

						if (res?.message != null) {
							if (res.alert) {
								if (parentFragment.parentActivity == null) {
									return@runOnUIThread
								}

								val builder = AlertDialog.Builder(parentFragment.parentActivity!!)
								builder.setTitle(name)
								builder.setPositiveButton(parentFragment.context?.getString(R.string.OK), null)
								builder.setMessage(res.message)

								parentFragment.showDialog(builder.create())
							}
							else {
								parentFragment.showAlert(name, res.message)
							}
						}
						else if (res?.url != null) {
							if (parentFragment.parentActivity == null) {
								return@runOnUIThread
							}

							val user = messagesController.getUser(uid)
							val verified = user != null && user.verified

							if (button is TLRPC.TLKeyboardButtonGame) {
								val game = (messageObject.messageOwner?.media as? TLRPC.TLMessageMediaGame)?.game ?: return@runOnUIThread
								parentFragment.showOpenGameAlert(game, messageObject, res.url, !verified && MessagesController.getNotificationsSettings(currentAccount).getBoolean("askgame_$uid", true), uid)
							}
							else {
								res.url?.let {
									AlertsCreator.showOpenUrlAlert(parentFragment, it, punycode = false, ask = false)
								}
							}
						}
					}
				}
				else if (error != null) {
					val parentActivity = parentFragment.parentActivity ?: return@runOnUIThread

					if ("PASSWORD_HASH_INVALID" == error.text) {
						if (srp == null) {
							val builder = AlertDialog.Builder(parentActivity)
							builder.setTitle(parentActivity.getString(R.string.BotOwnershipTransfer))
							builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BotOwnershipTransferReadyAlertText", R.string.BotOwnershipTransferReadyAlertText)))

							builder.setPositiveButton(parentActivity.getString(R.string.BotOwnershipTransferChangeOwner)) { _, _ ->
								val fragment = TwoStepVerificationActivity()

								fragment.setDelegate { password ->
									sendCallback(cache, messageObject, button, password, fragment, parentFragment)
								}

								parentFragment.presentFragment(fragment)
							}

							builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

							parentFragment.showDialog(builder.create())
						}
					}
					else if ("PASSWORD_MISSING" == error.text || error.text?.startsWith("PASSWORD_TOO_FRESH_") == true || error.text?.startsWith("SESSION_TOO_FRESH_") == true) {
						passwordFragment?.needHideProgress()

						val builder = AlertDialog.Builder(parentFragment.parentActivity!!)
						builder.setTitle(ApplicationLoader.applicationContext.getString(R.string.EditAdminTransferAlertTitle))

						val linearLayout = LinearLayout(parentFragment.parentActivity)
						linearLayout.setPadding(AndroidUtilities.dp(24f), AndroidUtilities.dp(2f), AndroidUtilities.dp(24f), 0)
						linearLayout.orientation = LinearLayout.VERTICAL

						builder.setView(linearLayout)

						var messageTextView = TextView(parentFragment.parentActivity)
						messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
						messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
						messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
						messageTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("BotOwnershipTransferAlertText", R.string.BotOwnershipTransferAlertText))

						linearLayout.addView(messageTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

						var linearLayout2 = LinearLayout(parentFragment.parentActivity)
						linearLayout2.orientation = LinearLayout.HORIZONTAL

						linearLayout.addView(linearLayout2, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 11f, 0f, 0f))

						var dotImageView = ImageView(parentFragment.parentActivity)
						dotImageView.setImageResource(R.drawable.list_circle)
						dotImageView.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(11f) else 0, AndroidUtilities.dp(9f), if (LocaleController.isRTL) 0 else AndroidUtilities.dp(11f), 0)
						dotImageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.text, null), PorterDuff.Mode.SRC_IN)

						messageTextView = TextView(parentFragment.parentActivity)
						messageTextView.setTextColor(ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.text, null))
						messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
						messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
						messageTextView.text = AndroidUtilities.replaceTags(ApplicationLoader.applicationContext.getString(R.string.EditAdminTransferAlertText1))

						if (LocaleController.isRTL) {
							linearLayout2.addView(messageTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
							linearLayout2.addView(dotImageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT))
						}
						else {
							linearLayout2.addView(dotImageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
							linearLayout2.addView(messageTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
						}

						linearLayout2 = LinearLayout(parentFragment.parentActivity)
						linearLayout2.orientation = LinearLayout.HORIZONTAL

						linearLayout.addView(linearLayout2, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 11f, 0f, 0f))

						dotImageView = ImageView(parentFragment.parentActivity)
						dotImageView.setImageResource(R.drawable.list_circle)
						dotImageView.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(11f) else 0, AndroidUtilities.dp(9f), if (LocaleController.isRTL) 0 else AndroidUtilities.dp(11f), 0)
						dotImageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.text, null), PorterDuff.Mode.SRC_IN)

						messageTextView = TextView(parentFragment.parentActivity)
						messageTextView.setTextColor(ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.text, null))
						messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
						messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
						messageTextView.text = AndroidUtilities.replaceTags(ApplicationLoader.applicationContext.getString(R.string.EditAdminTransferAlertText2))

						if (LocaleController.isRTL) {
							linearLayout2.addView(messageTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
							linearLayout2.addView(dotImageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT))
						}
						else {
							linearLayout2.addView(dotImageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
							linearLayout2.addView(messageTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
						}

						if ("PASSWORD_MISSING" == error.text) {
							builder.setPositiveButton(ApplicationLoader.applicationContext.getString(R.string.EditAdminTransferSetPassword)) { _, _ ->
								parentFragment.presentFragment(TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null))
							}

							builder.setNegativeButton(ApplicationLoader.applicationContext.getString(R.string.Cancel), null)
						}
						else {
							messageTextView = TextView(parentFragment.parentActivity)
							messageTextView.setTextColor(ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.text, null))
							messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
							messageTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
							messageTextView.text = ApplicationLoader.applicationContext.getString(R.string.EditAdminTransferAlertText3)

							linearLayout.addView(messageTextView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 11f, 0f, 0f))

							builder.setNegativeButton(ApplicationLoader.applicationContext.getString(R.string.OK), null)
						}

						parentFragment.showDialog(builder.create())
					}
					else if ("SRP_ID_INVALID" == error.text) {
						val getPasswordReq = TLRPC.TLAccountGetPassword()

						ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, { response2, error2 ->
							AndroidUtilities.runOnUIThread {
								if (error2 == null) {
									val currentPassword = response2 as? TLRPC.TLAccountPassword
									passwordFragment?.setCurrentPasswordInfo(null, currentPassword)
									TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword)
									sendCallback(cache, messageObject, button, passwordFragment?.newSrpPassword, passwordFragment, parentFragment)
								}
							}
						}, ConnectionsManager.RequestFlagWithoutLogin)
					}
					else {
						if (passwordFragment != null) {
							passwordFragment.needHideProgress()
							passwordFragment.finishFragment()
						}
					}
				}
			}
		}

		if (cacheFinal) {
			messagesStorage.getBotCache(key, requestDelegate)
		}
		else {
			if (button is TLRPC.TLKeyboardButtonUrlAuth) {
				val req = TLRPC.TLMessagesRequestUrlAuth()
				req.peer = messagesController.getInputPeer(messageObject.dialogId)
				req.msgId = messageObject.id
				req.buttonId = button.buttonId
				req.flags = req.flags or 2

				request[0] = req

				connectionsManager.sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else if (button is TLRPC.TLKeyboardButtonBuy) {
				// MARK: uncomment to enable invoices
//				if (messageObject.messageOwner!!.media!!.flags and 4 == 0) {
//					val req = TLRPC.TLPaymentsGetPaymentForm()
//
//					val inputInvoice = TLRPC.TLInputInvoiceMessage()
//					inputInvoice.msgId = messageObject.id
//					inputInvoice.peer = messagesController.getInputPeer(messageObject.messageOwner?.peerId)
//
//					req.invoice = inputInvoice
//
//					try {
//						val jsonObject = JSONObject()
//						jsonObject.put("bg_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.background, null))
//						jsonObject.put("text_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.text, null))
//						jsonObject.put("hint_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.hint, null))
//						jsonObject.put("link_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.brand, null))
//						jsonObject.put("button_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.brand, null))
//						jsonObject.put("button_text_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.white, null))
//
//						req.theme_params = TLRPC.TLDataJSON()
//						req.theme_params.data = jsonObject.toString()
//						req.flags = req.flags or 1
//					}
//					catch (e: Exception) {
//						FileLog.e(e)
//					}
//
//					connectionsManager.sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors)
//				}
//				else {
//					val req = TLRPC.TLPaymentsGetPaymentReceipt()
//					req.msgId = messageObject.messageOwner!!.media!!.receipt_msg_id
//					req.peer = messagesController.getInputPeer(messageObject.messageOwner?.peerId)
//
//					connectionsManager.sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors)
//				}
			}
			else {
				val req = TLRPC.TLMessagesGetBotCallbackAnswer()
				req.peer = messagesController.getInputPeer(messageObject.dialogId)
				req.msgId = messageObject.id
				req.game = button is TLRPC.TLKeyboardButtonGame

				if (button.requiresPassword) {
					req.password = srp ?: TLRPC.TLInputCheckPasswordEmpty()
					req.password = req.password
					req.flags = req.flags or 4
				}

				if (button.data != null) {
					req.flags = req.flags or 1
					req.data = button.data
				}

				connectionsManager.sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
		}
	}

	fun isSendingCallback(messageObject: MessageObject?, button: KeyboardButton?): Boolean {
		if (messageObject == null || button == null) {
			return false
		}

		val type = when (button) {
			is TLRPC.TLKeyboardButtonUrlAuth -> 3
			is TLRPC.TLKeyboardButtonGame -> 1
			is TLRPC.TLKeyboardButtonBuy -> 2
			else -> 0
		}

		val key = messageObject.dialogId.toString() + "_" + messageObject.id + "_" + Utilities.bytesToHex(button.data) + "_" + type

		return waitingForCallback.containsKey(key)
	}

	fun sendGame(peer: InputPeer?, game: TLRPC.TLInputMediaGame?, randomId: Long, taskId: Long) {
		if (peer == null || game == null) {
			return
		}

		val request = TLRPC.TLMessagesSendMedia()
		request.peer = peer

		when (request.peer) {
			is TLRPC.TLInputPeerChannel -> {
				request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + -peer.channelId, false)
			}

			is TLRPC.TLInputPeerChat -> {
				request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + -peer.chatId, false)
			}

			else -> {
				request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer.userId, false)
			}
		}

		request.randomId = if (randomId != 0L) randomId else nextRandomId
		request.message = ""
		request.media = game

		val fromId = ChatObject.getSendAsPeerId(messagesController.getChat(peer.chatId), messagesController.getChatFull(peer.chatId))

		if (fromId != UserConfig.getInstance(currentAccount).getClientUserId()) {
			request.sendAs = messagesController.getInputPeer(fromId)
		}

		val newTaskId: Long

		if (taskId == 0L) {
			var data: NativeByteBuffer? = null

			try {
				data = NativeByteBuffer(peer.objectSize + game.objectSize + 4 + 8)
				data.writeInt32(3)
				data.writeInt64(randomId)
				peer.serializeToStream(data)
				game.serializeToStream(data)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			newTaskId = messagesStorage.createPendingTask(data)
		}
		else {
			newTaskId = taskId
		}

		connectionsManager.sendRequest(request) { response, error ->
			if (error == null) {
				messagesController.processUpdates(response as? Updates, false)
			}

			if (newTaskId != 0L) {
				messagesStorage.removePendingTask(newTaskId)
			}
		}
	}

	fun sendMessage(retryMessageObject: MessageObject) {
		sendMessage(null, null, null, null, null, null, null, null, null, null, retryMessageObject.dialogId, retryMessageObject.messageOwner?.attachPath, null, null, null, true, retryMessageObject, null, retryMessageObject.messageOwner?.replyMarkup, retryMessageObject.messageOwner?.params, retryMessageObject.messageOwner?.silent != true, if (retryMessageObject.scheduled) retryMessageObject.messageOwner!!.date else 0, 0, null, null, false)
	}

	fun sendMessage(user: User?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int) {
		sendMessage(null, null, null, null, null, user, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false)
	}

	fun sendMessage(invoice: TLRPC.TLMessageMediaInvoice?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int) {
		sendMessage(null, null, null, null, null, null, null, null, null, invoice, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false)
	}

	fun sendMessage(document: TLRPC.TLDocument?, videoEditedInfo: VideoEditedInfo?, path: String?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: String?, entities: List<MessageEntity>?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, ttl: Int, parentObject: Any?, sendAnimationData: SendAnimationData?, updateStickersOrder: Boolean) {
		sendMessage(null, caption, null, null, videoEditedInfo, null, document, null, null, null, peer, path, replyToMsg, replyToTopMsg, null, true, null, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, sendAnimationData, updateStickersOrder)
	}

	fun sendMessage(message: String?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, webPage: WebPage?, searchLinks: Boolean, entities: List<MessageEntity>?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, sendAnimationData: SendAnimationData?, updateStickersOrder: Boolean) {
		sendMessage(message, null, null, null, null, null, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, webPage, searchLinks, null, entities, replyMarkup, params, notify, scheduleDate, 0, null, sendAnimationData, updateStickersOrder)
	}

	fun sendMessage(location: MessageMedia?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int) {
		sendMessage(null, null, location, null, null, null, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false)
	}

	fun sendMessage(poll: TLRPC.TLMessageMediaPoll?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int) {
		sendMessage(null, null, null, null, null, null, null, null, poll, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false)
	}

	fun sendMessage(game: TLRPC.TLGame?, peer: Long, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int) {
		sendMessage(null, null, null, null, null, null, null, game, null, null, peer, null, null, null, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false)
	}

	fun sendMessage(photo: TLRPC.TLPhoto?, path: String?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: String?, entities: List<MessageEntity>?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, ttl: Int, parentObject: Any?, updateStickersOrder: Boolean) {
		sendMessage(null, caption, null, photo, null, null, null, null, null, null, peer, path, replyToMsg, replyToTopMsg, null, true, null, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, null, updateStickersOrder)
	}

	private fun sendMessage(message: String?, caption: String?, location: MessageMedia?, photo: TLRPC.TLPhoto?, videoEditedInfo: VideoEditedInfo?, user: User?, document: TLRPC.TLDocument?, game: TLRPC.TLGame?, poll: TLRPC.TLMessageMediaPoll?, invoice: TLRPC.TLMessageMediaInvoice?, peer: Long, path: String?, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, webPage: WebPage?, searchLinks: Boolean, retryMessageObject: MessageObject?, entities: List<MessageEntity>?, replyMarkup: ReplyMarkup?, params: MutableMap<String, String>?, notify: Boolean, scheduleDate: Int, ttl: Int, parentObject: Any?, sendAnimationData: SendAnimationData?, updateStickersOrder: Boolean) {
		if (peer == 0L) {
			return
		}

		@Suppress("NAME_SHADOWING") var message = message
		@Suppress("NAME_SHADOWING") var caption = caption
		@Suppress("NAME_SHADOWING") var location = location
		@Suppress("NAME_SHADOWING") var photo = photo
		@Suppress("NAME_SHADOWING") var videoEditedInfo = videoEditedInfo
		@Suppress("NAME_SHADOWING") var document = document
		@Suppress("NAME_SHADOWING") var poll = poll
		@Suppress("NAME_SHADOWING") var params = params
		@Suppress("NAME_SHADOWING") var ttl = ttl
		@Suppress("NAME_SHADOWING") var parentObject = parentObject
		@Suppress("NAME_SHADOWING") val entities = entities?.toSet()?.toList()

		if (message == null && caption == null) {
			caption = ""
		}

		var originalPath: String? = null

		if (params != null && params.containsKey("originalPath")) {
			originalPath = params["originalPath"]
		}

		var newMsg: Message? = null
		var newMsgObj: MessageObject? = null
		var delayedMessage: DelayedMessage? = null
		var type = -1
		var isChannel = false
		var forceNoSoundVideo = false
		var fromPeer: Peer? = null
		val rank: String? = null
		var linkedToGroup: Long = 0
		var encryptedChat: EncryptedChat? = null
		val sendToPeer = if (!DialogObject.isEncryptedDialog(peer)) messagesController.getInputPeer(peer) else null
		val myId = userConfig.getClientUserId()

		if (DialogObject.isEncryptedDialog(peer)) {
			encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(peer))

			if (encryptedChat == null) {
				if (retryMessageObject != null) {
					messagesStorage.markMessageAsSendError(retryMessageObject.messageOwner, retryMessageObject.scheduled)
					retryMessageObject.messageOwner?.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
					notificationCenter.postNotificationName(NotificationCenter.messageSendError, retryMessageObject.id)
					processSentMessage(retryMessageObject.id)
				}
				return
			}
		}
		else if (sendToPeer is TLRPC.TLInputPeerChannel) {
			val chat = messagesController.getChat(sendToPeer.channelId)
			val chatFull = messagesController.getChatFull(chat?.id)

			isChannel = chat != null && !chat.megagroup

			if (isChannel && chat?.hasLink == true && chatFull != null) {
				linkedToGroup = chatFull.linkedChatId
			}

			fromPeer = messagesController.getPeer(ChatObject.getSendAsPeerId(chat, chatFull, true))
		}

		try {
			if (retryMessageObject != null) {
				newMsg = retryMessageObject.messageOwner

				if (parentObject == null && params != null && params.containsKey("parentObject")) {
					parentObject = params["parentObject"]
				}

				if (retryMessageObject.isForwarded || params != null && params.containsKey("fwd_id")) {
					type = 4
				}
				else {
					if (retryMessageObject.isDice) {
						type = 11
						message = retryMessageObject.diceEmoji
						caption = ""
					}
					else if (retryMessageObject.type == MessageObject.TYPE_COMMON || retryMessageObject.isAnimatedEmoji) {
						if (retryMessageObject.messageOwner?.media is TLRPC.TLMessageMediaGame) {
							//game = retryMessageObject.messageOwner.media.game;
						}
						else {
							message = newMsg?.message
						}
						type = 0
					}
					else if (retryMessageObject.type == MessageObject.TYPE_GEO) {
						location = newMsg?.media
						type = 1
					}
					else if (retryMessageObject.type == MessageObject.TYPE_PHOTO) {
						photo = newMsg?.media?.photo as? TLRPC.TLPhoto

						if (retryMessageObject.messageOwner?.message != null) {
							caption = retryMessageObject.messageOwner?.message
						}

						type = 2
					}
					else if (retryMessageObject.type == MessageObject.TYPE_VIDEO || retryMessageObject.type == MessageObject.TYPE_ROUND_VIDEO || retryMessageObject.videoEditedInfo != null) {
						type = 3
						document = newMsg?.media?.document as? TLRPC.TLDocument

						if (retryMessageObject.messageOwner?.message != null) {
							caption = retryMessageObject.messageOwner?.message
						}
					}
					else if (retryMessageObject.type == 12) {
						// MARK: old unsupported models
//						user = TLRPC.TLUserRequest_old2()
//						// user.phone = newMsg.media.phoneNumber;
//						user.firstName = newMsg?.media?.firstName
//						user.lastName = newMsg?.media?.lastName
//
//						val reason = TLRPC.TLRestrictionReason()
//						reason.platform = ""
//						reason.reason = ""
//						reason.text = newMsg?.media?.vcard
//
//						user.restrictionReason.add(reason)
//						user.id = newMsg?.media?.userId ?: 0
//
//						type = 6
					}
					else if (retryMessageObject.type == 8 || retryMessageObject.type == 9 || retryMessageObject.type == MessageObject.TYPE_STICKER || retryMessageObject.type == MessageObject.TYPE_MUSIC || retryMessageObject.type == MessageObject.TYPE_ANIMATED_STICKER) {
						document = newMsg?.media?.document as? TLRPC.TLDocument
						type = 7

						if (retryMessageObject.messageOwner?.message != null) {
							caption = retryMessageObject.messageOwner?.message
						}
					}
					else if (retryMessageObject.type == MessageObject.TYPE_VOICE) {
						document = newMsg?.media?.document as? TLRPC.TLDocument
						type = 8

						if (retryMessageObject.messageOwner?.message != null) {
							caption = retryMessageObject.messageOwner?.message
						}
					}
					else if (retryMessageObject.type == MessageObject.TYPE_POLL) {
						poll = newMsg?.media as TLRPC.TLMessageMediaPoll
						type = 10
					}

					if (params != null && params.containsKey("query_id")) {
						type = 9
					}

					if ((newMsg?.media?.ttlSeconds ?: 0) > 0) {
						ttl = newMsg?.media?.ttlSeconds ?: 0
					}
				}
			}
			else {
				var canSendStickers = true

				if (DialogObject.isChatDialog(peer)) {
					val chat = messagesController.getChat(-peer)
					canSendStickers = ChatObject.canSendStickers(chat)
				}

				if (message != null) {
					// MARK: uncomment to enable secret chats
//					newMsg = if (encryptedChat != null) {
//						TLRPC.TLMessageSecret()
//					}
//					else {
//						TLRPC.TLMessage()
//					}
					newMsg = TLRPC.TLMessage() // MARK: and remove this

					// MARK: uncomment to enable secret chats
//					if (encryptedChat != null && webPage is TLRPC.TLWebPagePending) {
//						if (webPage.url != null) {
//							val newWebPage: WebPage = TLRPC.TLWebPageUrlPending()
//							newWebPage.url = webPage.url
//							webPage = newWebPage
//						}
//						else {
//							webPage = null
//						}
//					}
					if (canSendStickers && message.length < 30 && webPage == null && entities.isNullOrEmpty() && messagesController.diceEmojies?.contains(message.replace("\ufe0f", "")) == true && encryptedChat == null && scheduleDate == 0) {
						val mediaDice = TLRPC.TLMessageMediaDice()
						mediaDice.emoticon = message
						mediaDice.value = -1

						newMsg.media = mediaDice

						type = 11
						caption = ""
					}
					else {
						if (webPage == null) {
							newMsg.media = TLRPC.TLMessageMediaEmpty()
						}
						else {
							newMsg.media = TLRPC.TLMessageMediaWebPage()
							newMsg.media?.webpage = webPage
						}

						type = if (params != null && params.containsKey("query_id")) {
							9
						}
						else {
							0
						}

						newMsg.message = message
					}
				}
				else if (poll != null) {
					// MARK: uncomment to enable secret chats
//					newMsg = if (encryptedChat != null) {
//						TLRPC.TLMessage_secret()
//					}
//					else {
//						TLRPC.TLMessage()
//					}
					newMsg = TLRPC.TLMessage() // MARK: and remove this

					newMsg.media = poll
					type = 10
				}
				else if (location != null) {
// MARK: uncomment to enable secret chats
//					newMsg = if (encryptedChat != null) {
//						TLRPC.TLMessage_secret()
//					}
//					else {
//						TLRPC.TLMessage()
//					}
					newMsg = TLRPC.TLMessage() // MARK: and remove this

					newMsg.media = location

					type = if (params != null && params.containsKey("query_id")) {
						9
					}
					else {
						1
					}
				}
				else if (photo != null) {
					// MARK: uncomment to enable secret chats
//					newMsg = if (encryptedChat != null) {
//						TLRPC.TLMessage_secret()
//					}
//					else {
//						TLRPC.TLMessage()
//					}
					newMsg = TLRPC.TLMessage() // MARK: and remove this

					newMsg.media = TLRPC.TLMessageMediaPhoto()
					newMsg.media!!.flags = newMsg.media!!.flags or 3

					if (entities != null) {
						newMsg.entities.addAll(entities)
					}

					if (ttl != 0) {
						newMsg.media!!.ttlSeconds = ttl
						newMsg.ttl = newMsg.media!!.ttlSeconds
						newMsg.media!!.flags = newMsg.media!!.flags or 4
					}

					newMsg.media!!.photo = photo

					type = if (params != null && params.containsKey("query_id")) {
						9
					}
					else {
						2
					}

					if (!path.isNullOrEmpty() && path.startsWith("http")) {
						newMsg.attachPath = path
					}
					else {
						val location1 = photo.sizes[photo.sizes.size - 1].location
						newMsg.attachPath = FileLoader.getInstance(currentAccount).getPathToAttach(location1, true).toString()
					}
				}
				else if (game != null) {
					newMsg = TLRPC.TLMessage()

					newMsg.media = TLRPC.TLMessageMediaGame().also {
						it.game = game
					}

					if (params != null && params.containsKey("query_id")) {
						type = 9
					}
				}
				else if (invoice != null) {
					newMsg = TLRPC.TLMessage()
					newMsg.media = invoice

					if (params != null && params.containsKey("query_id")) {
						type = 9
					}
				}
				else if (user != null) {
					// MARK: uncomment to enable secret chats
//					newMsg = if (encryptedChat != null) {
//						TLRPC.TLMessage_secret()
//					}
//					else {
//						TLRPC.TLMessage()
//					}
					newMsg = TLRPC.TLMessage() // MARK: and remove this

					newMsg.media = TLRPC.TLMessageMediaContact().also {
						it.firstName = user.firstName
						it.lastName = user.lastName
						it.userId = user.id

						if (user.restrictionReason?.firstOrNull()?.text?.startsWith("BEGIN:VCARD") == true) {
							it.vcard = user.restrictionReason?.firstOrNull()?.text ?: ""
						}
						else {
							it.vcard = ""
						}

						if (it.firstName == null) {
							it.firstName = ""
							user.firstName = it.firstName
						}

						if (it.lastName == null) {
							it.lastName = ""
							user.lastName = it.lastName
						}
					}

					type = if (params != null && params.containsKey("query_id")) {
						9
					}
					else {
						6
					}
				}
				else if (document != null) {
					// MARK: uncomment to enable secret chats
//					newMsg = if (encryptedChat != null) {
//						TLRPC.TLMessage_secret()
//					}
//					else {
//						TLRPC.TLMessage()
//					}
					newMsg = TLRPC.TLMessage() // MARK: and remove this

					if (DialogObject.isChatDialog(peer)) {
						if (!canSendStickers) {
							var a = 0
							val n = document.attributes.size

							while (a < n) {
								if (document.attributes[a] is TLRPC.TLDocumentAttributeAnimated) {
									document.attributes.removeAt(a)
									forceNoSoundVideo = true
									break
								}

								a++
							}
						}
					}

					newMsg.media = TLRPC.TLMessageMediaDocument()
					newMsg.media!!.flags = newMsg.media!!.flags or 3

					if (ttl != 0) {
						newMsg.media!!.ttlSeconds = ttl
						newMsg.ttl = newMsg.media!!.ttlSeconds
						newMsg.media!!.flags = newMsg.media!!.flags or 4
					}

					newMsg.media!!.document = document

					type = if (params != null && params.containsKey("query_id")) {
						9
					}
					else if (!MessageObject.isVideoSticker(document) && (MessageObject.isVideoDocument(document) || MessageObject.isRoundVideoDocument(document) || videoEditedInfo != null)) {
						3
					}
					else if (MessageObject.isVoiceDocument(document)) {
						8
					}
					else {
						7
					}

					if (videoEditedInfo != null) {
						val ve = videoEditedInfo.string

						if (params == null) {
							params = HashMap()
						}

						params["ve"] = ve
					}

					if (encryptedChat != null && document.dcId > 0 && !MessageObject.isStickerDocument(document) && !MessageObject.isAnimatedStickerDocument(document, true)) {
						newMsg.attachPath = FileLoader.getInstance(currentAccount).getPathToAttach(document).toString()
					}
					else {
						newMsg.attachPath = path
					}

					if (encryptedChat != null && (MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true))) {
						for (a in document.attributes.indices) {
							val attribute = document.attributes[a]

							if (attribute is TLRPC.TLDocumentAttributeSticker) {
								document.attributes.removeAt(a)

								val attributeSticker = TLRPC.TLDocumentAttributeSticker()

								document.attributes.add(attributeSticker)

								attributeSticker.alt = attribute.alt

								if (attribute.stickerset != null) {
									val name = if (attribute.stickerset is TLRPC.TLInputStickerSetShortName) {
										(attribute.stickerset as? TLRPC.TLInputStickerSetShortName)?.shortName
									}
									else {
										mediaDataController.getStickerSetName((attribute.stickerset as? TLRPC.TLInputStickerSetID)?.id)
									}

									if (!name.isNullOrEmpty()) {
										attributeSticker.stickerset = TLRPC.TLInputStickerSetShortName().also {
											it.shortName = name
										}
									}
									else {
										if (attribute.stickerset is TLRPC.TLInputStickerSetID) {
											delayedMessage = DelayedMessage(peer)
											delayedMessage.encryptedChat = encryptedChat
											delayedMessage.locationParent = attributeSticker
											delayedMessage.type = 5
											delayedMessage.parentObject = attribute.stickerset
										}

										attributeSticker.stickerset = TLRPC.TLInputStickerSetEmpty()
									}
								}
								else {
									attributeSticker.stickerset = TLRPC.TLInputStickerSetEmpty()
								}

								break
							}
						}
					}
				}

				if (!entities.isNullOrEmpty()) {
					newMsg?.entities = ArrayList(entities)
					newMsg?.flags = newMsg!!.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
				}

				if (caption != null) {
					newMsg?.message = caption
				}
				else if (newMsg?.message == null) {
					newMsg?.message = ""
				}

				if (newMsg?.attachPath == null) {
					newMsg?.attachPath = ""
				}

				newMsg?.id = userConfig.newMessageId
				newMsg?.localId = newMsg?.id ?: 0
				newMsg?.out = true

				if (isChannel && sendToPeer != null) {
					newMsg?.fromId = TLRPC.TLPeerChannel().also {
						it.channelId = sendToPeer.channelId
					}
				}
				else if (fromPeer != null) {
					newMsg?.fromId = fromPeer

					if (rank != null) {
						newMsg?.postAuthor = rank
						newMsg?.flags = newMsg!!.flags or 65536
					}
				}
				else {
					newMsg?.fromId = TLRPC.TLPeerUser()
					newMsg?.fromId?.userId = myId
					newMsg?.flags = newMsg!!.flags or TLRPC.MESSAGE_FLAG_HAS_FROM_ID
				}

				userConfig.saveConfig(false)
			}

			newMsg!!.silent = !notify || MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_$peer", false)

			if (newMsg.randomId == 0L) {
				newMsg.randomId = nextRandomId
			}

			if (params != null && params.containsKey("bot")) {
				// MARK: uncomment to enable secret chats
//				if (encryptedChat != null) {
//					newMsg.viaBotName = params["bot_name"]
//
//					if (newMsg.viaBotName == null) {
//						newMsg.viaBotName = ""
//					}
//				}
//				else {
				newMsg.viaBotId = Utilities.parseInt(params["bot"]).toLong()
//				}

				newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_BOT_ID
			}

			newMsg.params = params

			if (retryMessageObject == null || !retryMessageObject.resendAsIs) {
				newMsg.date = if (scheduleDate != 0) scheduleDate else connectionsManager.currentTime

				if (sendToPeer is TLRPC.TLInputPeerChannel) {
					if (scheduleDate == 0 && isChannel) {
						newMsg.views = 1
						newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_VIEWS
					}

					val chat = messagesController.getChat(sendToPeer.channelId)

					if (chat != null) {
						if (chat.megagroup) {
							newMsg.unread = true
						}
						else {
							newMsg.post = true

							if (chat.signatures) {
								newMsg.fromId = TLRPC.TLPeerUser()
								newMsg.fromId!!.userId = myId
							}
						}
					}
				}
				else {
					newMsg.unread = true
				}
			}

			newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
			newMsg.dialogId = peer

			if (replyToMsg != null) {
				newMsg.replyTo = TLRPC.TLMessageReplyHeader()

				if (encryptedChat != null && replyToMsg.messageOwner?.randomId != 0L) {
					newMsg.replyTo!!.replyToRandomId = replyToMsg.messageOwner!!.randomId
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_REPLY
				}
				else {
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_REPLY
				}

				newMsg.replyTo!!.replyToMsgId = replyToMsg.id

				if (replyToTopMsg != null && replyToTopMsg !== replyToMsg) {
					newMsg.replyTo!!.replyToTopId = replyToTopMsg.id
					newMsg.replyTo!!.flags = newMsg.replyTo!!.flags or 2
				}
			}

			if (linkedToGroup != 0L) {
				newMsg.replies = TLRPC.TLMessageReplies().also {
					it.comments = true
					it.channelId = linkedToGroup
					it.flags = it.flags or 1
				}

				newMsg.flags = newMsg.flags or 8388608
			}

			if (replyMarkup != null && encryptedChat == null) {
				newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_MARKUP
				newMsg.replyMarkup = replyMarkup

				val bot = params!!["bot"]

				if (bot != null) {
					newMsg.viaBotId = bot.toLong()
				}
			}

			if (!DialogObject.isEncryptedDialog(peer)) {
				newMsg.peerId = messagesController.getPeer(peer)

				if (DialogObject.isUserDialog(peer)) {
					val sendToUser = messagesController.getUser(peer)

					if (sendToUser == null) {
						processSentMessage(newMsg.id)
						return
					}

					if ((sendToUser as? TLRPC.TLUser)?.bot == true) {
						newMsg.unread = false
					}
				}
			}
			else {
				newMsg.peerId = TLRPC.TLPeerUser()

				if (encryptedChat!!.participantId == myId) {
					newMsg.peerId!!.userId = encryptedChat.adminId
				}
				else {
					newMsg.peerId!!.userId = encryptedChat.participantId
				}

				if (ttl != 0) {
					newMsg.ttl = ttl
				}
				else {
					newMsg.ttl = encryptedChat.ttl

					if (newMsg.ttl != 0 && newMsg.media != null) {
						newMsg.media!!.ttlSeconds = newMsg.ttl
						newMsg.media!!.flags = newMsg.media!!.flags or 4
					}
				}

				if (newMsg.ttl != 0 && newMsg.media?.document != null) {
					if (MessageObject.isVoiceMessage(newMsg)) {
						var duration = 0

						val attributes = (newMsg.media?.document as? TLRPC.TLDocument)?.attributes

						if (!attributes.isNullOrEmpty()) {
							for (attribute in attributes) {
								if (attribute is TLRPC.TLDocumentAttributeAudio) {
									duration = attribute.duration
									break
								}
							}
						}

						newMsg.ttl = max(newMsg.ttl, duration + 1)
					}
					else if (MessageObject.isVideoMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
						var duration = 0

						val attributes = (newMsg.media?.document as? TLRPC.TLDocument)?.attributes

						if (!attributes.isNullOrEmpty()) {
							for (attribute in attributes) {
								if (attribute is TLRPC.TLDocumentAttributeVideo) {
									duration = attribute.duration
									break
								}
							}
						}

						newMsg.ttl = max(newMsg.ttl, duration + 1)
					}
				}
			}

			if (MessageObject.isVoiceMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
				newMsg.mediaUnread = true
			}

			if (newMsg.fromId == null) {
				newMsg.fromId = newMsg.peerId
			}

			newMsg.sendState = MessageObject.MESSAGE_SEND_STATE_SENDING

			var groupId: Long = 0
			var isFinalGroupMedia = false

			if (params != null) {
				val groupIdStr = params["groupId"]

				if (groupIdStr != null) {
					groupId = Utilities.parseLong(groupIdStr)

					newMsg.groupedId = groupId
					newMsg.flags = newMsg.flags or 131072
				}

				isFinalGroupMedia = params["final"] != null
			}

			newMsgObj = MessageObject(currentAccount, newMsg, replyToMsg, generateLayout = true, checkMediaExists = true)
			newMsgObj.sendAnimationData = sendAnimationData
			newMsgObj.wasJustSent = true
			newMsgObj.scheduled = scheduleDate != 0

			if (!newMsgObj.isForwarded && (newMsgObj.type == MessageObject.TYPE_VIDEO || videoEditedInfo != null || newMsgObj.type == MessageObject.TYPE_VOICE) && !TextUtils.isEmpty(newMsg.attachPath)) {
				newMsgObj.attachPathExists = true
			}

			if (newMsgObj.videoEditedInfo != null && videoEditedInfo == null) {
				videoEditedInfo = newMsgObj.videoEditedInfo
			}

			if (groupId == 0L) {
				val objArr = ArrayList<MessageObject>()
				objArr.add(newMsgObj)

				val arr = ArrayList<Message?>()
				arr.add(newMsg)

				MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0, scheduleDate != 0)
				MessagesController.getInstance(currentAccount).updateInterfaceWithMessages(peer, objArr, scheduleDate != 0)

				if (scheduleDate == 0) {
					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload)
				}
			}
			else {
				val key = "group_$groupId"
				val arrayList = delayedMessages[key]

				if (arrayList != null) {
					delayedMessage = arrayList[0]
				}

				if (delayedMessage == null) {
					delayedMessage = DelayedMessage(peer)
					delayedMessage.initForGroup(groupId)
					delayedMessage.encryptedChat = encryptedChat
					delayedMessage.scheduled = scheduleDate != 0
				}

				delayedMessage.performMediaUpload = false
				delayedMessage.photoSize = null
				delayedMessage.videoEditedInfo = null
				delayedMessage.httpLocation = null

				if (isFinalGroupMedia) {
					delayedMessage.finalGroupMessage = newMsg.id
				}
			}

			if (sendToPeer != null) {
				FileLog.d("send message user_id = " + sendToPeer.userId + " chat_id = " + sendToPeer.chatId + " channel_id = " + sendToPeer.channelId + " access_hash = " + sendToPeer.accessHash + " notify = " + notify + " silent = " + MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_$peer", false))
			}

			var performMediaUpload = false

			if (type == 0 || type == 9 && message != null && encryptedChat != null) {
				if (encryptedChat == null) {
					val reqSend = TLRPC.TLMessagesSendMessage()
					reqSend.message = message
					reqSend.clearDraft = retryMessageObject == null
					reqSend.silent = newMsg.silent
					reqSend.peer = sendToPeer
					reqSend.randomId = newMsg.randomId

					if (updateStickersOrder) {
						reqSend.updateStickersetsOrder = true
					}

					if (newMsg.fromId != null) {
						reqSend.sendAs = messagesController.getInputPeer(newMsg.fromId)
					}

					if (newMsg.replyTo != null && newMsg.replyTo?.replyToMsgId != 0) {
						reqSend.flags = reqSend.flags or 1
						reqSend.replyToMsgId = newMsg.replyTo?.replyToMsgId ?: 0
					}

					if (!searchLinks) {
						reqSend.noWebpage = true
					}

					if (!entities.isNullOrEmpty()) {
						reqSend.entities.addAll(entities)
						reqSend.flags = reqSend.flags or 8
					}

					if (scheduleDate != 0) {
						reqSend.scheduleDate = scheduleDate
						reqSend.flags = reqSend.flags or 1024
					}

					performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, scheduleDate != 0)

					if (retryMessageObject == null) {
						mediaDataController.cleanDraft(peer, replyToTopMsg?.id ?: 0, false)
					}
				}
				// MARK: uncomment to enable secrect chats
//				else {
//					val reqSend = TLRPC.TLDecryptedMessage()
//					reqSend.ttl = newMsg.ttl
//
//					if (!entities.isNullOrEmpty()) {
//						reqSend.entities = ArrayList(entities)
//						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
//					}
//
//					if (newMsg.replyTo != null && newMsg.replyTo?.replyToRandomId != 0L) {
//						reqSend.replyToRandomId = newMsg.replyTo?.replyToRandomId ?: 0
//						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_REPLY
//					}
//
//					if (params != null && params["bot_name"] != null) {
//						reqSend.viaBotName = params["bot_name"]
//						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_BOT_ID
//					}
//
//					reqSend.silent = newMsg.silent
//					reqSend.randomId = newMsg.randomId
//					reqSend.message = message
//
//					if (webPage?.url != null) {
//						reqSend.media = TLRPC.TLDecryptedMessageMediaWebPage()
//						reqSend.media.url = webPage.url
//						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
//					}
//					else {
//						reqSend.media = TLRPC.TLDecryptedMessageMediaEmpty()
//					}
//
//					secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj)
//
//					if (retryMessageObject == null) {
//						mediaDataController.cleanDraft(peer, replyToTopMsg?.id ?: 0, false)
//					}
//				}
			}
			else if (type in 1..3 || type in 5..8 || type == 9 && encryptedChat != null || type == 10 || type == 11) {
				if (encryptedChat == null) {
					var inputMedia: InputMedia? = null

					if (type == 1) {
						if (location is TLRPC.TLMessageMediaVenue) {
							inputMedia = TLRPC.TLInputMediaVenue().also {
								it.address = location.address
								it.title = location.title
								it.provider = location.provider
								it.venueId = location.venueId
								it.venueType = ""
							}
						}
						else if (location is TLRPC.TLMessageMediaGeoLive) {
							inputMedia = TLRPC.TLInputMediaGeoLive().also {
								it.period = location.period
								it.flags = it.flags or 2

								if (location.heading != 0) {
									it.heading = location.heading
									it.flags = it.flags or 4
								}

								if (location.proximityNotificationRadius != 0) {
									it.proximityNotificationRadius = location.proximityNotificationRadius
									it.flags = it.flags or 8
								}
							}
						}
						else {
							inputMedia = TLRPC.TLInputMediaGeoPoint()
						}

						inputMedia.geoPoint = TLRPC.TLInputGeoPoint().also {
							(location?.geo as? TLRPC.TLGeoPoint)?.let { geoPoint ->
								it.lat = geoPoint.lat
								it.lon = geoPoint.lon
							}

						}
					}
					else if (type == 2 || type == 9 && photo != null) {
						val uploadedPhoto = TLRPC.TLInputMediaUploadedPhoto()

						if (ttl != 0) {
							uploadedPhoto.ttlSeconds = ttl
							newMsg.ttl = uploadedPhoto.ttlSeconds
							uploadedPhoto.flags = uploadedPhoto.flags or 2
						}

						if (params != null) {
							val masks = params["masks"]

							if (masks != null) {
								val serializedData = SerializedData(Utilities.hexToBytes(masks))
								val count = serializedData.readInt32(false)

								for (a in 0 until count) {
									uploadedPhoto.stickers.add(InputDocument.deserialize(serializedData, serializedData.readInt32(false), false) ?: TLRPC.TLInputDocumentEmpty())
								}

								uploadedPhoto.flags = uploadedPhoto.flags or 1
								serializedData.cleanup()
							}
						}

						if (photo?.accessHash == 0L) {
							inputMedia = uploadedPhoto
							performMediaUpload = true
						}
						else {
							val media = TLRPC.TLInputMediaPhoto()

							media.id = TLRPC.TLInputPhoto().also {
								it.id = photo!!.id
								it.accessHash = photo.accessHash
								it.fileReference = photo.fileReference ?: ByteArray(0)
							}

							inputMedia = media
						}

						if (delayedMessage == null) {
							delayedMessage = DelayedMessage(peer)
							delayedMessage.type = 0
							delayedMessage.obj = newMsgObj
							delayedMessage.originalPath = originalPath
							delayedMessage.scheduled = scheduleDate != 0
						}

						delayedMessage.inputUploadMedia = uploadedPhoto
						delayedMessage.performMediaUpload = performMediaUpload

						if (!path.isNullOrEmpty() && path.startsWith("http")) {
							delayedMessage.httpLocation = path
						}
						else {
							delayedMessage.photoSize = photo?.sizes?.lastOrNull()
							delayedMessage.locationParent = photo
						}
					}
					else if (type == 3) {
						val uploadedDocument = TLRPC.TLInputMediaUploadedDocument()
						uploadedDocument.mimeType = document!!.mimeType
						uploadedDocument.attributes.addAll(document.attributes)

						if (forceNoSoundVideo || !MessageObject.isRoundVideoDocument(document) && (videoEditedInfo == null || !videoEditedInfo.muted && !videoEditedInfo.roundVideo)) {
							uploadedDocument.nosoundVideo = true

							FileLog.d("nosound_video = true")
						}

						if (ttl != 0) {
							uploadedDocument.ttlSeconds = ttl
							newMsg.ttl = uploadedDocument.ttlSeconds
							uploadedDocument.flags = uploadedDocument.flags or 2
						}

						if (params != null) {
							val masks = params["masks"]

							if (masks != null) {
								val serializedData = SerializedData(Utilities.hexToBytes(masks))
								val count = serializedData.readInt32(false)

								for (a in 0 until count) {
									uploadedDocument.stickers.add(InputDocument.deserialize(serializedData, serializedData.readInt32(false), false) ?: TLRPC.TLInputDocumentEmpty())
								}

								uploadedDocument.flags = uploadedDocument.flags or 1
								serializedData.cleanup()
							}
						}

						if (document.accessHash == 0L) {
							inputMedia = uploadedDocument
							performMediaUpload = true
						}
						else {
							val media = TLRPC.TLInputMediaDocument()

							media.id = TLRPC.TLInputDocument().also {
								it.id = document.id
								it.accessHash = document.accessHash
								it.fileReference = document.fileReference ?: ByteArray(0)
							}

							if (params != null && params.containsKey("query")) {
								media.query = params["query"]
								media.flags = media.flags or 2
							}

							inputMedia = media
						}

						if (delayedMessage == null) {
							delayedMessage = DelayedMessage(peer)
							delayedMessage.type = 1
							delayedMessage.obj = newMsgObj
							delayedMessage.originalPath = originalPath
							delayedMessage.parentObject = parentObject
							delayedMessage.scheduled = scheduleDate != 0
						}

						delayedMessage.inputUploadMedia = uploadedDocument
						delayedMessage.performMediaUpload = performMediaUpload

						if (document.thumbs.isNotEmpty()) {
							val photoSize = document.thumbs[0]

							if (photoSize !is TLRPC.TLPhotoStrippedSize) {
								delayedMessage.photoSize = photoSize
								delayedMessage.locationParent = document
							}
						}

						delayedMessage.videoEditedInfo = videoEditedInfo
					}
					else if (type == 6) {
						inputMedia = TLRPC.TLInputMediaContact()
						inputMedia.firstName = user!!.firstName
						inputMedia.lastName = user.lastName
						inputMedia.phoneNumber = String.format("@%s", user.username) // MARK: this is workaround to get contacts work on chat screen // Nik
						inputMedia.userId = user.id

						if (user.restrictionReason?.firstOrNull()?.text?.startsWith("BEGIN:VCARD") == true) {
							inputMedia.vcard = user.restrictionReason?.firstOrNull()?.text ?: ""
						}
						else {
							inputMedia.vcard = ""
						}
					}
					else if (type == 7 || type == 9) {
						val http = false
						val uploadedMedia: TLRPC.TLInputMediaUploadedDocument?

						if (originalPath != null || path != null || document!!.accessHash == 0L) {
							uploadedMedia = TLRPC.TLInputMediaUploadedDocument()

							if (ttl != 0) {
								uploadedMedia.ttlSeconds = ttl
								newMsg.ttl = uploadedMedia.ttlSeconds
								uploadedMedia.flags = uploadedMedia.flags or 2
							}

							if (forceNoSoundVideo || !TextUtils.isEmpty(path) && path!!.lowercase().endsWith("mp4") && (params == null || params.containsKey("forceDocument"))) {
								uploadedMedia.nosoundVideo = true
							}

							uploadedMedia.forceFile = params != null && params.containsKey("forceDocument")
							uploadedMedia.mimeType = document!!.mimeType
							uploadedMedia.attributes.addAll(document.attributes)
						}
						else {
							uploadedMedia = null
						}

						if (document.accessHash == 0L) {
							inputMedia = uploadedMedia
							performMediaUpload = uploadedMedia is TLRPC.TLInputMediaUploadedDocument
						}
						else {
							val media = TLRPC.TLInputMediaDocument()

							media.id = TLRPC.TLInputDocument().also {
								it.id = document.id
								it.accessHash = document.accessHash
								it.fileReference = document.fileReference ?: ByteArray(0)
							}

							if (params != null && params.containsKey("query")) {
								media.query = params["query"]
								media.flags = media.flags or 2
							}

							inputMedia = media
						}

						if (!http && uploadedMedia != null) {
							if (delayedMessage == null) {
								delayedMessage = DelayedMessage(peer)
								delayedMessage.type = 2
								delayedMessage.obj = newMsgObj
								delayedMessage.originalPath = originalPath
								delayedMessage.parentObject = parentObject
								delayedMessage.scheduled = scheduleDate != 0
							}

							delayedMessage.inputUploadMedia = uploadedMedia
							delayedMessage.performMediaUpload = performMediaUpload

							if (document.thumbs.isNotEmpty()) {
								val photoSize = document.thumbs[0]

								if (photoSize !is TLRPC.TLPhotoStrippedSize) {
									delayedMessage.photoSize = photoSize
									delayedMessage.locationParent = document
								}
							}
						}
					}
					else if (type == 8) {
						val uploadedDocument = TLRPC.TLInputMediaUploadedDocument()
						uploadedDocument.mimeType = document!!.mimeType
						uploadedDocument.attributes.addAll(document.attributes)

						if (ttl != 0) {
							uploadedDocument.ttlSeconds = ttl
							newMsg.ttl = uploadedDocument.ttlSeconds
							uploadedDocument.flags = uploadedDocument.flags or 2
						}

						if (document.accessHash == 0L) {
							inputMedia = uploadedDocument
							performMediaUpload = true
						}
						else {
							val media = TLRPC.TLInputMediaDocument()

							media.id = TLRPC.TLInputDocument().also {
								it.id = document.id
								it.accessHash = document.accessHash
								it.fileReference = document.fileReference ?: ByteArray(0)
							}

							if (params != null && params.containsKey("query")) {
								media.query = params["query"]
								media.flags = media.flags or 2
							}

							inputMedia = media
						}

						delayedMessage = DelayedMessage(peer)
						delayedMessage.type = 3
						delayedMessage.obj = newMsgObj
						delayedMessage.parentObject = parentObject
						delayedMessage.inputUploadMedia = uploadedDocument
						delayedMessage.performMediaUpload = performMediaUpload
						delayedMessage.scheduled = scheduleDate != 0
					}
					else if (type == 10) {
						val inputMediaPoll = TLRPC.TLInputMediaPoll()
						inputMediaPoll.poll = poll!!.poll

						if (params != null && params.containsKey("answers")) {
							val answers = Utilities.hexToBytes(params["answers"])

							if (answers.isNotEmpty()) {
								for (answer in answers) {
									inputMediaPoll.correctAnswers.add(byteArrayOf(answer))
								}

								inputMediaPoll.flags = inputMediaPoll.flags or 1
							}
						}

						if (poll.results != null && !TextUtils.isEmpty(poll.results?.solution)) {
							inputMediaPoll.solution = poll.results?.solution
							inputMediaPoll.solutionEntities.addAll(poll.results!!.solutionEntities)
							inputMediaPoll.flags = inputMediaPoll.flags or 2
						}

						inputMedia = inputMediaPoll
					}
					else if (type == 11) {
						val inputMediaDice = TLRPC.TLInputMediaDice()
						inputMediaDice.emoticon = message
						inputMedia = inputMediaDice
					}

					val reqSend: TLObject?

					if (groupId != 0L) {
						var request: TLRPC.TLMessagesSendMultiMedia? = null

						if (delayedMessage?.sendRequest != null) {
							request = delayedMessage.sendRequest as? TLRPC.TLMessagesSendMultiMedia
						}

						if (request == null) {
							request = TLRPC.TLMessagesSendMultiMedia()
							request.peer = sendToPeer
							request.silent = newMsg.silent

							if (newMsg.replyTo != null && newMsg.replyTo?.replyToMsgId != 0) {
								request.flags = request.flags or 1
								request.replyToMsgId = newMsg.replyTo?.replyToMsgId ?: 0
							}

							if (scheduleDate != 0) {
								request.scheduleDate = scheduleDate
								request.flags = request.flags or 1024
							}

							delayedMessage?.sendRequest = request
						}

						delayedMessage?.messageObjects?.add(newMsgObj)
						delayedMessage?.parentObjects?.add(parentObject)
						delayedMessage?.locations?.add(delayedMessage.photoSize)
						delayedMessage?.videoEditedInfos?.add(delayedMessage.videoEditedInfo)
						delayedMessage?.httpLocations?.add(delayedMessage.httpLocation)
						delayedMessage?.inputMedias?.add(delayedMessage.inputUploadMedia)
						delayedMessage?.messages?.add(newMsg)
						delayedMessage?.originalPaths?.add(originalPath)

						val inputSingleMedia = TLRPC.TLInputSingleMedia()
						inputSingleMedia.randomId = newMsg.randomId
						inputSingleMedia.media = inputMedia
						inputSingleMedia.message = caption

						if (!entities.isNullOrEmpty()) {
							inputSingleMedia.entities.addAll(entities)
							inputSingleMedia.flags = inputSingleMedia.flags or 1
						}

						request.multiMedia.add(inputSingleMedia)
						reqSend = request
					}
					else {
						val request = TLRPC.TLMessagesSendMedia()
						request.peer = sendToPeer
						request.silent = newMsg.silent

						if (newMsg.replyTo != null && newMsg.replyTo?.replyToMsgId != 0) {
							request.flags = request.flags or 1
							request.replyToMsgId = newMsg.replyTo?.replyToMsgId ?: 0
						}

						request.randomId = newMsg.randomId

						if (newMsg.fromId != null) {
							request.sendAs = messagesController.getInputPeer(newMsg.fromId)
						}

						request.media = inputMedia
						request.message = caption

						if (!entities.isNullOrEmpty()) {
							request.entities.addAll(ArrayList(entities))
							request.flags = request.flags or 8
						}

						if (scheduleDate != 0) {
							request.scheduleDate = scheduleDate
							request.flags = request.flags or 1024
						}

						if (updateStickersOrder) {
							request.updateStickersetsOrder = true
						}

						if (delayedMessage != null) {
							delayedMessage.sendRequest = request
						}

						reqSend = request

//						if (updateStickersOrder) {
//                            if (MessageObject.getStickerSetId(document) != -1) {
//                                TLRPC.TLRPC.TLUpdateMoveStickerSetToTop update = new TLRPC.TLRPC.TLUpdateMoveStickerSetToTop();
//                                update.masks = false;
//                                update.emojis = false;
//                                update.stickerset = MessageObject.getStickerSetId(document);
//
//                                ArrayList<TLRPC.Update> updates = new ArrayList<>();
//                                updates.add(update);
//                                getMessagesController().processUpdateArray(updates, null, null, false, 0);
//                            }
//						}
					}

					if (groupId != 0L) {
						performSendDelayedMessage(delayedMessage)
					}
					else if (type == 1) {
						performSendMessageRequest(reqSend, newMsgObj, null, delayedMessage, parentObject, scheduleDate != 0)
					}
					else if (type == 2) {
						if (performMediaUpload) {
							performSendDelayedMessage(delayedMessage)
						}
						else {
							performSendMessageRequest(reqSend, newMsgObj, originalPath, null, true, delayedMessage, parentObject, scheduleDate != 0)
						}
					}
					else if (type == 3) {
						if (performMediaUpload) {
							performSendDelayedMessage(delayedMessage)
						}
						else {
							performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, scheduleDate != 0)
						}
					}
					else if (type == 6) {
						performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, scheduleDate != 0)
					}
					else if (type == 7) {
						if (performMediaUpload && delayedMessage != null) {
							performSendDelayedMessage(delayedMessage)
						}
						else {
							performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, scheduleDate != 0)
						}
					}
					else if (type == 8) {
						if (performMediaUpload) {
							performSendDelayedMessage(delayedMessage)
						}
						else {
							performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, scheduleDate != 0)
						}
					}
					else if (type == 10 || type == 11) {
						performSendMessageRequest(reqSend, newMsgObj, originalPath, delayedMessage, parentObject, scheduleDate != 0)
					}
				}
				else {
					// MARK: uncomment to enable secret chats
//					val reqSend: TLRPC.TLDecryptedMessage
//
//					if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 73) {
//						reqSend = TLRPC.TLDecryptedMessage()
//
//						if (groupId != 0L) {
//							reqSend.grouped_id = groupId
//							reqSend.flags = reqSend.flags or 131072
//						}
//					}
//					else {
//						reqSend = TLRPC.TLDecryptedMessage_layer45()
//					}
//
//					reqSend.ttl = newMsg.ttl
//
//					if (!entities.isNullOrEmpty()) {
//						reqSend.entities = ArrayList(entities)
//						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
//					}
//
//					if (newMsg.replyTo != null && newMsg.replyTo?.replyToRandomId != 0L) {
//						reqSend.replyToRandomId = newMsg.replyTo?.replyToRandomId ?: 0
//						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_REPLY
//					}
//
//					reqSend.silent = newMsg.silent
//					reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
//
//					if (params != null && params["bot_name"] != null) {
//						reqSend.viaBotName = params["bot_name"]
//						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_BOT_ID
//					}
//
//					reqSend.randomId = newMsg.randomId
//					reqSend.message = ""
//
//					if (type == 1) {
//						if (location is TLRPC.TLMessageMediaVenue) {
//							reqSend.media = TLRPC.TLDecryptedMessageMediaVenue()
//							reqSend.media.address = location.address
//							reqSend.media.title = location.title
//							reqSend.media.provider = location.provider
//							reqSend.media.venue_id = location.venue_id
//						}
//						else {
//							reqSend.media = TLRPC.TLDecryptedMessageMediaGeoPoint()
//						}
//
//						reqSend.media.lat = location!!.geo.lat
//						reqSend.media._long = location.geo._long
//
//						secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj)
//					}
//					else if (type == 2 || type == 9 && photo != null) {
//						val small = photo!!.sizes[0]
//						val big = photo.sizes[photo.sizes.size - 1]
//
//						ImageLoader.fillPhotoSizeWithBytes(small)
//
//						reqSend.media = TLRPC.TLDecryptedMessageMediaPhoto()
//						reqSend.media.caption = caption
//
//						if (small.bytes != null) {
//							(reqSend.media as TLRPC.TLDecryptedMessageMediaPhoto).thumb = small.bytes
//						}
//						else {
//							(reqSend.media as TLRPC.TLDecryptedMessageMediaPhoto).thumb = ByteArray(0)
//						}
//
//						reqSend.media.thumb_h = small.h
//						reqSend.media.thumb_w = small.w
//						reqSend.media.w = big.w
//						reqSend.media.h = big.h
//						reqSend.media.size = big.size.toLong()
//
//						if (big.location.key == null || groupId != 0L) {
//							if (delayedMessage == null) {
//								delayedMessage = DelayedMessage(peer)
//								delayedMessage.encryptedChat = encryptedChat
//								delayedMessage.type = 0
//								delayedMessage.originalPath = originalPath
//								delayedMessage.sendEncryptedRequest = reqSend
//								delayedMessage.obj = newMsgObj
//
//								if (params != null && params.containsKey("parentObject")) {
//									delayedMessage.parentObject = params["parentObject"]
//								}
//								else {
//									delayedMessage.parentObject = parentObject
//								}
//
//								delayedMessage.performMediaUpload = true
//								delayedMessage.scheduled = scheduleDate != 0
//							}
//
//							if (!path.isNullOrEmpty() && path.startsWith("http")) {
//								delayedMessage.httpLocation = path
//							}
//							else {
//								delayedMessage.photoSize = photo.sizes[photo.sizes.size - 1]
//								delayedMessage.locationParent = photo
//							}
//
//							if (groupId == 0L) {
//								performSendDelayedMessage(delayedMessage)
//							}
//						}
//						else {
//							val encryptedFile = TLRPC.TLInputEncryptedFile()
//							encryptedFile.id = big.location.volumeId
//							encryptedFile.accessHash = big.location.secret
//
//							reqSend.media.key = big.location.key
//							reqSend.media.iv = big.location.iv
//
//							secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj)
//						}
//					}
//					else if (type == 3) {
//						val thumb = getThumbForSecretChat(document!!.thumbs)
//
//						ImageLoader.fillPhotoSizeWithBytes(thumb)
//
//						if (MessageObject.isNewGifDocument(document) || MessageObject.isRoundVideoDocument(document)) {
//							reqSend.media = TLRPC.TLDecryptedMessageMediaDocument()
//							reqSend.media.attributes = document.attributes
//
//							if (thumb?.bytes != null) {
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaDocument).thumb = thumb.bytes
//							}
//							else {
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaDocument).thumb = ByteArray(0)
//							}
//						}
//						else {
//							reqSend.media = TLRPC.TLDecryptedMessageMediaVideo()
//
//							if (thumb?.bytes != null) {
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaVideo).thumb = thumb.bytes
//							}
//							else {
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaVideo).thumb = ByteArray(0)
//							}
//						}
//
//						reqSend.media.caption = caption
//						reqSend.media.mimeType = "video/mp4"
//						reqSend.media.size = document.size
//
//						for (attribute in document.attributes) {
//							if (attribute is TLRPC.TLDocumentAttributeVideo) {
//								reqSend.media.w = attribute.w
//								reqSend.media.h = attribute.h
//								reqSend.media.duration = attribute.duration
//								break
//							}
//						}
//
//						reqSend.media.thumb_h = thumb!!.h
//						reqSend.media.thumb_w = thumb.w
//
//						if (document.key == null || groupId != 0L) {
//							if (delayedMessage == null) {
//								delayedMessage = DelayedMessage(peer)
//								delayedMessage.encryptedChat = encryptedChat
//								delayedMessage.type = 1
//								delayedMessage.sendEncryptedRequest = reqSend
//								delayedMessage.originalPath = originalPath
//								delayedMessage.obj = newMsgObj
//
//								if (params != null && params.containsKey("parentObject")) {
//									delayedMessage.parentObject = params["parentObject"]
//								}
//								else {
//									delayedMessage.parentObject = parentObject
//								}
//
//								delayedMessage.performMediaUpload = true
//								delayedMessage.scheduled = scheduleDate != 0
//							}
//
//							delayedMessage.videoEditedInfo = videoEditedInfo
//
//							if (groupId == 0L) {
//								performSendDelayedMessage(delayedMessage)
//							}
//						}
//						else {
//							val encryptedFile = TLRPC.TLInputEncryptedFile()
//							encryptedFile.id = document.id
//							encryptedFile.accessHash = document.accessHash
//
//							reqSend.media.key = document.key
//							reqSend.media.iv = document.iv
//
//							secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj)
//						}
//					}
//					else if (type == 6) {
//						reqSend.media = TLRPC.TLDecryptedMessageMediaContact()
//						// reqSend.media.phoneNumber = user.phone;
//						reqSend.media.firstName = user!!.firstName
//						reqSend.media.lastName = user.lastName
//						reqSend.media.userId = user.id
//
//						secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj)
//					}
//					else if (type == 7 || type == 9 && document != null) {
//						if (document!!.accessHash != 0L && (MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true))) {
//							reqSend.media = TLRPC.TLDecryptedMessageMediaExternalDocument()
//							reqSend.media.id = document.id
//							reqSend.media.date = document.date
//							reqSend.media.accessHash = document.accessHash
//							reqSend.media.mimeType = document.mimeType
//							reqSend.media.size = document.size
//							reqSend.media.dcId = document.dcId
//							reqSend.media.attributes = document.attributes
//
//							val thumb = getThumbForSecretChat(document.thumbs)
//
//							if (thumb != null) {
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaExternalDocument).thumb = thumb
//							}
//							else {
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaExternalDocument).thumb = TLRPC.TLPhotoSizeEmpty()
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaExternalDocument).thumb.type = "s"
//							}
//
//							if (delayedMessage != null && delayedMessage.type == 5) {
//								delayedMessage.sendEncryptedRequest = reqSend
//								delayedMessage.obj = newMsgObj
//								performSendDelayedMessage(delayedMessage)
//							}
//							else {
//								secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj)
//							}
//						}
//						else {
//							reqSend.media = TLRPC.TLDecryptedMessageMediaDocument()
//							reqSend.media.attributes = document.attributes
//							reqSend.media.caption = caption
//
//							val thumb = getThumbForSecretChat(document.thumbs)
//
//							if (thumb != null) {
//								ImageLoader.fillPhotoSizeWithBytes(thumb)
//
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaDocument).thumb = thumb.bytes
//
//								reqSend.media.thumb_h = thumb.h
//								reqSend.media.thumb_w = thumb.w
//							}
//							else {
//								(reqSend.media as TLRPC.TLDecryptedMessageMediaDocument).thumb = ByteArray(0)
//
//								reqSend.media.thumb_h = 0
//								reqSend.media.thumb_w = 0
//							}
//
//							reqSend.media.size = document.size
//							reqSend.media.mimeType = document.mimeType
//
//							if (document.key == null || groupId != 0L) {
//								if (delayedMessage == null) {
//									delayedMessage = DelayedMessage(peer)
//									delayedMessage.encryptedChat = encryptedChat
//									delayedMessage.type = 2
//									delayedMessage.sendEncryptedRequest = reqSend
//									delayedMessage.originalPath = originalPath
//									delayedMessage.obj = newMsgObj
//
//									if (params != null && params.containsKey("parentObject")) {
//										delayedMessage.parentObject = params["parentObject"]
//									}
//									else {
//										delayedMessage.parentObject = parentObject
//									}
//
//									delayedMessage.performMediaUpload = true
//									delayedMessage.scheduled = scheduleDate != 0
//								}
//
//								if (!path.isNullOrEmpty() && path.startsWith("http")) {
//									delayedMessage.httpLocation = path
//								}
//
//								if (groupId == 0L) {
//									performSendDelayedMessage(delayedMessage)
//								}
//							}
//							else {
//								val encryptedFile = TLRPC.TLInputEncryptedFile()
//								encryptedFile.id = document.id
//								encryptedFile.accessHash = document.accessHash
//
//								reqSend.media.key = document.key
//								reqSend.media.iv = document.iv
//
//								secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj)
//							}
//						}
//					}
//					else if (type == 8) {
//						delayedMessage = DelayedMessage(peer)
//						delayedMessage.encryptedChat = encryptedChat
//						delayedMessage.sendEncryptedRequest = reqSend
//						delayedMessage.obj = newMsgObj
//						delayedMessage.type = 3
//						delayedMessage.parentObject = parentObject
//						delayedMessage.performMediaUpload = true
//						delayedMessage.scheduled = scheduleDate != 0
//
//						reqSend.media = TLRPC.TLDecryptedMessageMediaDocument()
//						reqSend.media.attributes = document!!.attributes
//						reqSend.media.caption = caption
//
//						val thumb = getThumbForSecretChat(document.thumbs)
//
//						if (thumb != null) {
//							ImageLoader.fillPhotoSizeWithBytes(thumb)
//							(reqSend.media as TLRPC.TLDecryptedMessageMediaDocument).thumb = thumb.bytes
//							reqSend.media.thumb_h = thumb.h
//							reqSend.media.thumb_w = thumb.w
//						}
//						else {
//							(reqSend.media as TLRPC.TLDecryptedMessageMediaDocument).thumb = ByteArray(0)
//							reqSend.media.thumb_h = 0
//							reqSend.media.thumb_w = 0
//						}
//
//						reqSend.media.mimeType = document.mimeType
//						reqSend.media.size = document.size
//
//						delayedMessage.originalPath = originalPath
//
//						performSendDelayedMessage(delayedMessage)
//					}
//
//					if (groupId != 0L) {
//						val request: TLRPC.TLMessagesSendEncryptedMultiMedia?
//
//						if (delayedMessage!!.sendEncryptedRequest != null) {
//							request = delayedMessage.sendEncryptedRequest as TLRPC.TLMessagesSendEncryptedMultiMedia?
//						}
//						else {
//							request = TLRPC.TLMessagesSendEncryptedMultiMedia()
//							delayedMessage.sendEncryptedRequest = request
//						}
//
//						delayedMessage.messageObjects!!.add(newMsgObj)
//						delayedMessage.messages!!.add(newMsg)
//						delayedMessage.originalPaths!!.add(originalPath!!)
//						delayedMessage.performMediaUpload = true
//
//						request!!.messages.add(reqSend)
//
//						val encryptedFile = TLRPC.TLInputEncryptedFile()
//						encryptedFile.id = (if (type == 3 || type == 7) 1 else 0).toLong()
//
//						request.files.add(encryptedFile)
//
//						performSendDelayedMessage(delayedMessage)
//					}
//
//					if (retryMessageObject == null) {
//						mediaDataController.cleanDraft(peer, replyToTopMsg?.id ?: 0, false)
//					}
				}
			}
			else if (type == 4) {
				val reqSend = TLRPC.TLMessagesForwardMessages()
				reqSend.toPeer = sendToPeer
				// reqSend.withMyScore = retryMessageObject!!.messageOwner!!.withMyScore

				if (params != null && params.containsKey("fwd_id")) {
					val fwdId = Utilities.parseInt(params["fwd_id"])

					reqSend.dropAuthor = true

					val peerId = Utilities.parseLong(params["fwd_peer"])

					if (peerId < 0) {
						val chat = messagesController.getChat(-peerId)

						if (ChatObject.isChannel(chat)) {
							reqSend.fromPeer = TLRPC.TLInputPeerChannel().also {
								it.channelId = chat.id
								it.accessHash = chat.accessHash
							}
						}
						else {
							reqSend.fromPeer = TLRPC.TLInputPeerEmpty()
						}
					}
					else {
						reqSend.fromPeer = TLRPC.TLInputPeerEmpty()
					}

					reqSend.id.add(fwdId)
				}
				else {
					reqSend.fromPeer = TLRPC.TLInputPeerEmpty()
				}

				reqSend.silent = newMsg.silent

				if (scheduleDate != 0) {
					reqSend.scheduleDate = scheduleDate
					reqSend.flags = reqSend.flags or 1024
				}

				reqSend.randomId.add(newMsg.randomId)

				if (retryMessageObject != null) {
					if (retryMessageObject.id >= 0) {
						reqSend.id.add(retryMessageObject.id)
					}
					else {
						if (retryMessageObject.messageOwner?.fwdMsgId != 0) {
							reqSend.id.add(retryMessageObject.messageOwner!!.fwdMsgId)
						}
						else if (retryMessageObject.messageOwner?.fwdFrom != null) {
							reqSend.id.add(retryMessageObject.messageOwner?.fwdFrom?.channelPost ?: 0)
						}
					}
				}

				performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, scheduleDate != 0)
			}
			else if (type == 9) {
				val reqSend = TLRPC.TLMessagesSendInlineBotResult()
				reqSend.peer = sendToPeer
				reqSend.randomId = newMsg.randomId

				if (newMsg.fromId != null) {
					reqSend.sendAs = messagesController.getInputPeer(newMsg.fromId)
				}

				reqSend.hideVia = !params!!.containsKey("bot")

				if (newMsg.replyTo != null && newMsg.replyTo?.replyToMsgId != 0) {
					reqSend.flags = reqSend.flags or 1
					reqSend.replyToMsgId = newMsg.replyTo?.replyToMsgId ?: 0
				}

				reqSend.silent = newMsg.silent

				if (scheduleDate != 0) {
					reqSend.scheduleDate = scheduleDate
					reqSend.flags = reqSend.flags or 1024
				}

				reqSend.queryId = Utilities.parseLong(params["query_id"])
				reqSend.id = params["id"]

				if (retryMessageObject == null) {
					reqSend.clearDraft = true
					mediaDataController.cleanDraft(peer, replyToTopMsg?.id ?: 0, false)
				}

				performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, scheduleDate != 0)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)

			messagesStorage.markMessageAsSendError(newMsg, scheduleDate != 0)

			newMsgObj?.messageOwner?.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR

			notificationCenter.postNotificationName(NotificationCenter.messageSendError, newMsg!!.id)
			processSentMessage(newMsg.id)
		}
	}

//	private fun getThumbForSecretChat(arrayList: ArrayList<PhotoSize?>?): PhotoSize? {
//		if (arrayList.isNullOrEmpty()) {
//			return null
//		}
//
//		var a = 0
//		val n = arrayList.size
//
//		while (a < n) {
//			val size = arrayList[a]
//
//			if (size == null || size is TLRPC.TLPhotoStrippedSize || size is TLRPC.TLPhotoPathSize || size is TLRPC.TLPhotoSizeEmpty || size.location == null) {
//				a++
//				continue
//			}
//
//			val photoSize = TLRPC.TLPhotoSizeLayer127()
//			photoSize.type = size.type
//			photoSize.w = size.w
//			photoSize.h = size.h
//			photoSize.size = size.size
//			photoSize.bytes = size.bytes
//
//			if (photoSize.bytes == null) {
//				photoSize.bytes = ByteArray(0)
//			}
//
//			photoSize.location = TL_fileLocation_layer82()
//			photoSize.location.dc_id = size.location.dc_id
//			photoSize.location.volume_id = size.location.volume_id
//			photoSize.location.local_id = size.location.local_id
//			photoSize.location.secret = size.location.secret
//
//			return photoSize
//		}
//
//		return null
//	}

	private fun performSendDelayedMessage(message: DelayedMessage?, index: Int = -1) {
		@Suppress("NAME_SHADOWING") var index = index

		if (message?.type == 0) {
			if (message.httpLocation != null) {
				putToDelayedMessages(message.httpLocation, message)
				ImageLoader.getInstance().loadHttpFile(message.httpLocation, "file", currentAccount)
			}
			else {
				if (message.sendRequest != null) {
					val location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize).toString()
					putToDelayedMessages(location, message)
					fileLoader.uploadFile(location, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)
					putToUploadingMessages(message.obj)
				}
				else {
					var location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize).toString()

					if (message.sendEncryptedRequest != null && message.photoSize?.location?.dcId != 0) {
						var file = File(location)

						if (!file.exists()) {
							location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize, true).toString()
							file = File(location)
						}

						if (!file.exists()) {
							putToDelayedMessages(FileLoader.getAttachFileName(message.photoSize), message)
							fileLoader.loadFile(ImageLocation.getForObject(message.photoSize, message.locationParent), message.parentObject, "jpg", FileLoader.PRIORITY_HIGH, 0)
							return
						}
					}

					putToDelayedMessages(location, message)

					fileLoader.uploadFile(location, encrypted = true, small = true, type = ConnectionsManager.FileTypePhoto)

					putToUploadingMessages(message.obj)
				}
			}
		}
		else if (message?.type == 1) {
			if (message.videoEditedInfo != null && message.videoEditedInfo!!.needConvert()) {
				var location = message.obj?.messageOwner?.attachPath
				val document = message.obj?.document

				if (location == null) {
					location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + document!!.id + ".mp4"
				}

				putToDelayedMessages(location, message)
				MediaController.getInstance().scheduleVideoConvert(message.obj)
				putToUploadingMessages(message.obj)
			}
			else {
				if (message.videoEditedInfo != null) {
					if (message.videoEditedInfo?.file != null) {
						val media = (message.sendRequest as? TLRPC.TLMessagesSendMedia)?.media ?: (message.sendRequest as? TLRPC.TLMessagesEditMessage)?.media
						media?.file = message.videoEditedInfo?.file

						message.videoEditedInfo?.file = null
					}
					// MARK: uncomment to enable secret chats
//					else if (message.videoEditedInfo?.encryptedFile != null) {
//						val decryptedMessage = message.sendEncryptedRequest as? TLRPC.TLDecryptedMessage
//						decryptedMessage!!.media.size = message.videoEditedInfo!!.estimatedSize
//						decryptedMessage.media.key = message.videoEditedInfo!!.key
//						decryptedMessage.media.iv = message.videoEditedInfo!!.iv
//						secretChatHelper.performSendEncryptedRequest(decryptedMessage, message.obj!!.messageOwner, message.encryptedChat, message.videoEditedInfo!!.encryptedFile, message.originalPath, message.obj)
//						message.videoEditedInfo!!.encryptedFile = null
//						return
//					}
				}

				if (message.sendRequest != null) {
					val media = if (message.sendRequest is TLRPC.TLMessagesSendMedia) {
						(message.sendRequest as TLRPC.TLMessagesSendMedia?)!!.media
					}
					else {
						(message.sendRequest as TLRPC.TLMessagesEditMessage?)!!.media
					}

					if (media?.file == null) {
						var location = message.obj?.messageOwner?.attachPath
						val document = message.obj?.document

						if (location == null) {
							location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + document?.id + ".mp4"
						}

						putToDelayedMessages(location, message)

						if (message.obj?.videoEditedInfo?.needConvert() == true) {
							fileLoader.uploadFile(location, encrypted = false, small = false, estimatedSize = document?.size ?: 0, type = ConnectionsManager.FileTypeVideo, forceSmallFile = false)
						}
						else {
							fileLoader.uploadFile(location, encrypted = false, small = false, type = ConnectionsManager.FileTypeVideo)
						}

						putToUploadingMessages(message.obj)
					}
					else {
						val location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + message.photoSize?.location?.volumeId + "_" + message.photoSize?.location?.localId + ".jpg"
						putToDelayedMessages(location, message)
						fileLoader.uploadFile(location, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)
						putToUploadingMessages(message.obj)
					}
				}
				else {
					var location = message.obj?.messageOwner?.attachPath
					val document = message.obj?.document

					if (location == null) {
						location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + document?.id + ".mp4"
					}

					if (message.sendEncryptedRequest != null && document?.dcId != 0) {
						val file = File(location)

						if (!file.exists()) {
							putToDelayedMessages(FileLoader.getAttachFileName(document), message)
							fileLoader.loadFile(document, message.parentObject, FileLoader.PRIORITY_HIGH, 0)
							return
						}
					}

					putToDelayedMessages(location, message)

					if (message.obj?.videoEditedInfo?.needConvert() == true) {
						fileLoader.uploadFile(location, encrypted = true, small = false, estimatedSize = document?.size ?: 0, type = ConnectionsManager.FileTypeVideo, forceSmallFile = false)
					}
					else {
						fileLoader.uploadFile(location, encrypted = true, small = false, type = ConnectionsManager.FileTypeVideo)
					}

					putToUploadingMessages(message.obj)
				}
			}
		}
		else if (message?.type == 2) {
			if (message.httpLocation != null) {
				putToDelayedMessages(message.httpLocation, message)
				ImageLoader.getInstance().loadHttpFile(message.httpLocation, "gif", currentAccount)
			}
			else {
				if (message.sendRequest != null) {
					val media = if (message.sendRequest is TLRPC.TLMessagesSendMedia) {
						(message.sendRequest as TLRPC.TLMessagesSendMedia?)!!.media
					}
					else {
						(message.sendRequest as TLRPC.TLMessagesEditMessage?)!!.media
					}

					if (media?.file == null) {
						val location = message.obj?.messageOwner?.attachPath

						putToDelayedMessages(location, message)

						fileLoader.uploadFile(location, message.sendRequest == null, false, ConnectionsManager.FileTypeFile)

						putToUploadingMessages(message.obj)
					}
					else if (media.thumb == null && message.photoSize != null && message.photoSize !is TLRPC.TLPhotoStrippedSize) {
						val location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + message.photoSize?.location?.volumeId + "_" + message.photoSize?.location?.localId + ".jpg"
						putToDelayedMessages(location, message)
						fileLoader.uploadFile(location, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)
						putToUploadingMessages(message.obj)
					}
				}
				else {
					val location = message.obj?.messageOwner?.attachPath
					val document = message.obj?.document

					if (location != null && message.sendEncryptedRequest != null && document?.dcId != 0) {
						val file = File(location)

						if (!file.exists()) {
							putToDelayedMessages(FileLoader.getAttachFileName(document), message)
							fileLoader.loadFile(document, message.parentObject, FileLoader.PRIORITY_HIGH, 0)
							return
						}
					}

					putToDelayedMessages(location, message)
					fileLoader.uploadFile(location, encrypted = true, small = false, type = ConnectionsManager.FileTypeFile)
					putToUploadingMessages(message.obj)
				}
			}
		}
		else if (message?.type == 3) {
			val location = message.obj?.messageOwner?.attachPath
			putToDelayedMessages(location, message)
			fileLoader.uploadFile(location, message.sendRequest == null, true, ConnectionsManager.FileTypeAudio)
			putToUploadingMessages(message.obj)
		}
		else if (message?.type == 4) {
			val add = index < 0

			if (message.performMediaUpload) {
				if (index < 0) {
					index = message.messageObjects!!.size - 1
				}

				val messageObject = message.messageObjects!![index]

				if (messageObject.document != null) {
					if (message.videoEditedInfo != null) {
						var location = messageObject.messageOwner?.attachPath
						val document = messageObject.document!!

						if (location == null) {
							location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + document.id + ".mp4"
						}

						putToDelayedMessages(location, message)

						message.extraHashMap!![messageObject] = location
						message.extraHashMap!![location + "_i"] = messageObject

						if (message.photoSize?.location != null) {
							message.extraHashMap!![location + "_t"] = message.photoSize!!
						}

						MediaController.getInstance().scheduleVideoConvert(messageObject)
						message.obj = messageObject
						putToUploadingMessages(messageObject)
					}
					else {
						val document = messageObject.document
						var documentLocation = messageObject.messageOwner?.attachPath

						if (documentLocation == null) {
							documentLocation = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + document?.id + ".mp4"
						}

						if (message.sendRequest != null) {
							val request = message.sendRequest as? TLRPC.TLMessagesSendMultiMedia
							val media = request!!.multiMedia[index].media

							if (media?.file == null) {
								putToDelayedMessages(documentLocation, message)

								message.extraHashMap!![messageObject] = documentLocation

								if (media != null) {
									message.extraHashMap!![documentLocation] = media
								}

								message.extraHashMap!![documentLocation + "_i"] = messageObject

								if (message.photoSize?.location != null) {
									message.extraHashMap!![documentLocation + "_t"] = message.photoSize!!
								}

								if (messageObject.videoEditedInfo?.needConvert() == true) {
									fileLoader.uploadFile(documentLocation, encrypted = false, small = false, estimatedSize = document?.size ?: 0, type = ConnectionsManager.FileTypeVideo, forceSmallFile = false)
								}
								else {
									fileLoader.uploadFile(documentLocation, encrypted = false, small = false, type = ConnectionsManager.FileTypeVideo)
								}

								putToUploadingMessages(messageObject)
							}
							else if (message.photoSize != null) {
								val location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + message.photoSize?.location?.volumeId + "_" + message.photoSize?.location?.localId + ".jpg"
								putToDelayedMessages(location, message)

								message.extraHashMap!![location + "_o"] = documentLocation
								message.extraHashMap!![messageObject] = location

								message.extraHashMap!![location] = media

								fileLoader.uploadFile(location, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)

								putToUploadingMessages(messageObject)
							}
						}
						// MARK: uncomment to enable secret chats
//						else {
//							val request = message.sendEncryptedRequest as TLRPC.TLMessagesSendEncryptedMultiMedia?
//
//							putToDelayedMessages(documentLocation, message)
//
//							message.extraHashMap!![messageObject] = documentLocation
//							message.extraHashMap!![documentLocation] = request!!.files[index]
//							message.extraHashMap!![documentLocation + "_i"] = messageObject
//
//							if (message.photoSize?.location != null) {
//								message.extraHashMap!![documentLocation + "_t"] = message.photoSize!!
//							}
//
//							if (messageObject.videoEditedInfo?.needConvert() == true) {
//								fileLoader.uploadFile(documentLocation, encrypted = true, small = false, estimatedSize = document?.size ?: 0, type = ConnectionsManager.FileTypeVideo, forceSmallFile = false)
//							}
//							else {
//								fileLoader.uploadFile(documentLocation, encrypted = true, small = false, type = ConnectionsManager.FileTypeVideo)
//							}
//
//							putToUploadingMessages(messageObject)
//						}
					}

					message.videoEditedInfo = null
					message.photoSize = null
				}
				else {
					if (message.httpLocation != null) {
						putToDelayedMessages(message.httpLocation, message)
						message.extraHashMap!![messageObject] = message.httpLocation!!
						message.extraHashMap!![message.httpLocation!!] = messageObject
						ImageLoader.getInstance().loadHttpFile(message.httpLocation!!, "file", currentAccount)
						message.httpLocation = null
					}
					else {
						val inputMedia = if (message.sendRequest != null) {
							val request = message.sendRequest as? TLRPC.TLMessagesSendMultiMedia
							request?.multiMedia?.get(index)?.media
						}
						else {
							null
							// MARK: uncomment to enable secret chats
//							val request = message.sendEncryptedRequest as? TLRPC.TLMessagesSendEncryptedMultiMedia
//							request?.files?.get(index)
						}

						val location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize).toString()

						putToDelayedMessages(location, message)

						if (inputMedia != null) {
							message.extraHashMap!![location] = inputMedia
						}

						message.extraHashMap!![messageObject] = location

						fileLoader.uploadFile(location, message.sendEncryptedRequest != null, true, ConnectionsManager.FileTypePhoto)

						putToUploadingMessages(messageObject)

						message.photoSize = null
					}
				}

				message.performMediaUpload = false
			}
			else if (!message.messageObjects.isNullOrEmpty()) {
				putToSendingMessages(message.messageObjects!![message.messageObjects!!.size - 1].messageOwner, message.finalGroupMessage != 0)
			}

			sendReadyToSendGroup(message, add, true)
		}
		else if (message?.type == 5) {
			val key = "stickerset_" + message.obj!!.id

			val req = TLRPC.TLMessagesGetStickerSet()
			req.stickerset = message.parentObject as InputStickerSet?

			connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					var found = false

					if (response != null) {
						val set = response as? TLRPC.TLMessagesStickerSet
						mediaDataController.storeTempStickerSet(set)

						val attributeSticker = message.locationParent as? TLRPC.TLDocumentAttributeSticker

						attributeSticker?.stickerset = TLRPC.TLInputStickerSetShortName().also {
							it.shortName = set?.set?.shortName
						}

						found = true
					}

					val arrayList = delayedMessages.remove(key)

					if (!arrayList.isNullOrEmpty()) {
						if (found) {
							messagesStorage.replaceMessageIfExists(arrayList[0].obj!!.messageOwner, null, null, false)
						}

						// MARK: uncomment to enable secret chats
						// secretChatHelper.performSendEncryptedRequest(message.sendEncryptedRequest as DecryptedMessage?, message.obj!!.messageOwner, message.encryptedChat, null, null, message.obj)
					}
				}
			}

			putToDelayedMessages(key, message)
		}
	}

	private fun uploadMultiMedia(message: DelayedMessage?, inputMedia: InputMedia?, inputEncryptedFile: InputEncryptedFile?, key: String?) {
		if (inputMedia != null) {
			val multiMedia = message!!.sendRequest as? TLRPC.TLMessagesSendMultiMedia

			for (a in multiMedia!!.multiMedia.indices) {
				if (multiMedia.multiMedia[a].media === inputMedia) {
					putToSendingMessages(message.messages!![a], message.scheduled)
					notificationCenter.postNotificationName(NotificationCenter.fileUploadProgressChanged, key, -1L, -1L, false)
					break
				}
			}

			val req = TLRPC.TLMessagesUploadMedia()
			req.media = inputMedia
			req.peer = (message.sendRequest as TLRPC.TLMessagesSendMultiMedia?)!!.peer

			connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					var newInputMedia: InputMedia? = null

					if (response != null) {
						val messageMedia = response as MessageMedia

						if (inputMedia is TLRPC.TLInputMediaUploadedPhoto && messageMedia is TLRPC.TLMessageMediaPhoto) {
							val inputMediaPhoto = TLRPC.TLInputMediaPhoto()

							inputMediaPhoto.id = TLRPC.TLInputPhoto().also {
								it.id = messageMedia.photo?.id ?: 0
								it.accessHash = messageMedia.photo?.accessHash ?: 0
								it.fileReference = messageMedia.photo?.fileReference
							}

							newInputMedia = inputMediaPhoto

							FileLog.d("set uploaded photo")
						}
						else if (inputMedia is TLRPC.TLInputMediaUploadedDocument && messageMedia is TLRPC.TLMessageMediaDocument) {
							val document = messageMedia.document as? TLRPC.TLDocument

							if (document != null) {
								val inputMediaDocument = TLRPC.TLInputMediaDocument()

								inputMediaDocument.id = TLRPC.TLInputDocument().also {
									it.id = document.id
									it.accessHash = document.accessHash
									it.fileReference = document.fileReference
								}

								newInputMedia = inputMediaDocument

								FileLog.d("set uploaded document")
							}
						}
					}

					if (newInputMedia != null) {
						if (inputMedia.ttlSeconds != 0) {
							newInputMedia.ttlSeconds = inputMedia.ttlSeconds
							newInputMedia.flags = newInputMedia.flags or 1
						}

						val req1 = message.sendRequest as? TLRPC.TLMessagesSendMultiMedia

						for (a in req1!!.multiMedia.indices) {
							if (req1.multiMedia[a].media === inputMedia) {
								req1.multiMedia[a].media = newInputMedia
								break
							}
						}

						sendReadyToSendGroup(message, add = false, check = true)
					}
					else {
						message.markAsError()
					}
				}
			}
		}
		// MARK: uncomment to enable secret chats
//		else if (inputEncryptedFile != null) {
//			val multiMedia = message!!.sendEncryptedRequest as TLRPC.TLMessagesSendEncryptedMultiMedia?
//
//			for (a in multiMedia!!.files.indices) {
//				if (multiMedia.files[a] === inputEncryptedFile) {
//					putToSendingMessages(message.messages!![a], message.scheduled)
//					notificationCenter.postNotificationName(NotificationCenter.fileUploadProgressChanged, key, -1L, -1L, false)
//					break
//				}
//			}
//
//			sendReadyToSendGroup(message, add = false, check = true)
//		}
	}

	private fun sendReadyToSendGroup(message: DelayedMessage?, add: Boolean, check: Boolean) {
		if (message!!.messageObjects!!.isEmpty()) {
			message.markAsError()
			return
		}

		val key = "group_" + message.groupId

		if (message.finalGroupMessage != message.messageObjects?.lastOrNull()?.id) {
			if (add) {
				FileLog.d("final message not added, add")
				putToDelayedMessages(key, message)
			}
			else {
				FileLog.d("final message not added")
			}

			return
		}
		else if (add) {
			delayedMessages.remove(key)

			messagesStorage.putMessages(message.messages, false, true, false, 0, message.scheduled)

			messagesController.updateInterfaceWithMessages(message.peer, message.messageObjects, message.scheduled)

			if (!message.scheduled) {
				notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
			}

			FileLog.d("add message")
		}

		if (message.sendRequest is TLRPC.TLMessagesSendMultiMedia) {
			val request = message.sendRequest as TLRPC.TLMessagesSendMultiMedia?

			for (a in request!!.multiMedia.indices) {
				val inputMedia = request.multiMedia[a].media

				if (inputMedia is TLRPC.TLInputMediaUploadedPhoto || inputMedia is TLRPC.TLInputMediaUploadedDocument) {
					FileLog.d("multi media not ready")
					return
				}
			}

			if (check) {
				val maxDelayedMessage = findMaxDelayedMessageForMessageId(message.finalGroupMessage, message.peer)

				if (maxDelayedMessage != null) {
					maxDelayedMessage.addDelayedRequest(message.sendRequest, message.messageObjects, message.originalPaths, message.parentObjects, message, message.scheduled)

					if (message.requests != null) {
						maxDelayedMessage.requests!!.addAll(message.requests!!)
					}

					FileLog.d("has maxDelayedMessage, delay")

					return
				}
			}
		}
		else {
			// MARK: uncomment to enable secret chats
//			val request = message.sendEncryptedRequest as TLRPC.TLMessagesSendEncryptedMultiMedia?
//
//			for (a in request!!.files.indices) {
//				val inputMedia = request.files[a]
//
//				if (inputMedia is TLRPC.TLInputEncryptedFile) {
//					return
//				}
//			}
		}

		if (message.sendRequest is TLRPC.TLMessagesSendMultiMedia) {
			performSendMessageRequestMulti(message.sendRequest as? TLRPC.TLMessagesSendMultiMedia, message.messageObjects, message.originalPaths, message.parentObjects, message, message.scheduled)
		}
		else {
			// MARK: uncomment to enable secret chats
			// secretChatHelper.performSendEncryptedRequest(message.sendEncryptedRequest as TLRPC.TLMessagesSendEncryptedMultiMedia?, message)
		}

		message.sendDelayedRequests()
	}

	fun stopVideoService(path: String?) {
		messagesStorage.storageQueue.postRunnable {
			AndroidUtilities.runOnUIThread {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.stopEncodingService, path, currentAccount)
			}
		}
	}

	fun putToSendingMessages(message: Message?, scheduled: Boolean) {
		if (message == null) {
			return
		}

		if (Thread.currentThread() !== ApplicationLoader.applicationHandler?.looper?.thread) {
			AndroidUtilities.runOnUIThread {
				putToSendingMessages(message, scheduled, true)
			}
		}
		else {
			putToSendingMessages(message, scheduled, true)
		}
	}

	private fun putToSendingMessages(message: Message?, scheduled: Boolean, notify: Boolean) {
		if (message == null) {
			return
		}

		if (message.id > 0) {
			editingMessages.put(message.id, message)
		}
		else {
			val contains = sendingMessages.indexOfKey(message.id) >= 0

			removeFromUploadingMessages(message.id, scheduled)

			sendingMessages.put(message.id, message)

			if (!scheduled && !contains) {
				val did = MessageObject.getDialogId(message)

				sendingMessagesIdDialogs.put(did, sendingMessagesIdDialogs.get(did, 0) + 1)

				if (notify) {
					notificationCenter.postNotificationName(NotificationCenter.sendingMessagesChanged)
				}
			}
		}
	}

	fun removeFromSendingMessages(mid: Int, scheduled: Boolean): Message? {
		val message: Message?

		if (mid > 0) {
			message = editingMessages[mid]

			if (message != null) {
				editingMessages.remove(mid)
			}
		}
		else {
			message = sendingMessages[mid]

			if (message != null) {
				sendingMessages.remove(mid)

				if (!scheduled) {
					val did = MessageObject.getDialogId(message)
					val currentCount = sendingMessagesIdDialogs[did]

					if (currentCount != null) {
						val count = currentCount - 1

						if (count <= 0) {
							sendingMessagesIdDialogs.remove(did)
						}
						else {
							sendingMessagesIdDialogs.put(did, count)
						}

						notificationCenter.postNotificationName(NotificationCenter.sendingMessagesChanged)
					}
				}
			}
		}

		return message
	}

	fun getSendingMessageId(did: Long): Int {
		for (a in 0 until sendingMessages.size) {
			val message = sendingMessages.valueAt(a)

			if (message.dialogId == did) {
				return message.id
			}
		}

		for (a in 0 until uploadMessages.size) {
			val message = uploadMessages.valueAt(a)

			if (message.dialogId == did) {
				return message.id
			}
		}

		return 0
	}

	private fun putToUploadingMessages(obj: MessageObject?) {
		if (obj == null || obj.id > 0 || obj.scheduled) {
			return
		}

		val message = obj.messageOwner
		val contains = uploadMessages.indexOfKey(message?.id ?: 0) >= 0

		uploadMessages.put((message?.id ?: 0), message)

		if (!contains) {
			val did = MessageObject.getDialogId(message)
			uploadingMessagesIdDialogs.put(did, uploadingMessagesIdDialogs.get(did, 0) + 1)
			notificationCenter.postNotificationName(NotificationCenter.sendingMessagesChanged)
		}
	}

	private fun removeFromUploadingMessages(mid: Int, scheduled: Boolean) {
		if (mid > 0 || scheduled) {
			return
		}

		val message = uploadMessages[mid]

		if (message != null) {
			uploadMessages.remove(mid)

			val did = MessageObject.getDialogId(message)
			val currentCount = uploadingMessagesIdDialogs[did]

			if (currentCount != null) {
				val count = currentCount - 1

				if (count <= 0) {
					uploadingMessagesIdDialogs.remove(did)
				}
				else {
					uploadingMessagesIdDialogs.put(did, count)
				}

				notificationCenter.postNotificationName(NotificationCenter.sendingMessagesChanged)
			}
		}
	}

	fun isSendingMessage(mid: Int): Boolean {
		return sendingMessages.indexOfKey(mid) >= 0 || editingMessages.indexOfKey(mid) >= 0
	}

	fun isSendingMessageIdDialog(did: Long): Boolean {
		return sendingMessagesIdDialogs.get(did, 0) > 0
	}

	fun isUploadingMessageIdDialog(did: Long): Boolean {
		return uploadingMessagesIdDialogs.get(did, 0) > 0
	}

	fun performSendMessageRequestMulti(req: TLRPC.TLMessagesSendMultiMedia?, msgObjs: List<MessageObject>?, originalPaths: List<String?>?, parentObjects: List<Any?>?, delayedMessage: DelayedMessage?, scheduled: Boolean) {
		if (req == null) {
			return
		}

		if (msgObjs.isNullOrEmpty()) {
			return
		}

		var a = 0
		val size = msgObjs.size

		while (a < size) {
			putToSendingMessages(msgObjs[a].messageOwner, scheduled)
			a++
		}

		connectionsManager.sendRequest(req, { response, error ->
			if (error != null && FileRefController.isFileRefError(error.text)) {
				if (parentObjects != null) {
					val arrayList = ArrayList(parentObjects)
					fileRefController.requestReference(arrayList, req, msgObjs, originalPaths, arrayList, delayedMessage, scheduled)
					return@sendRequest
				}
				else if (delayedMessage != null && !delayedMessage.retriedToSend) {
					delayedMessage.retriedToSend = true

					AndroidUtilities.runOnUIThread {
						var hasEmptyFile = false
						@Suppress("NAME_SHADOWING") var a = 0
						@Suppress("NAME_SHADOWING") val size = req.multiMedia.size

						while (a < size) {
							if (delayedMessage.parentObjects!![a] == null) {
								a++
								continue
							}

							removeFromSendingMessages(msgObjs[a].id, scheduled)

							val request = req.multiMedia[a]

							if (request.media is TLRPC.TLInputMediaPhoto) {
								request.media = delayedMessage.inputMedias!![a]
							}
							else if (request.media is TLRPC.TLInputMediaDocument) {
								request.media = delayedMessage.inputMedias!![a]
							}

							delayedMessage.videoEditedInfo = delayedMessage.videoEditedInfos!![a]
							delayedMessage.httpLocation = delayedMessage.httpLocations!![a]
							delayedMessage.photoSize = delayedMessage.locations!![a]
							delayedMessage.performMediaUpload = true

							if (request.media?.file == null || delayedMessage.photoSize != null) {
								hasEmptyFile = true
							}

							performSendDelayedMessage(delayedMessage, a)

							a++
						}

						if (!hasEmptyFile) {
							for (i in msgObjs.indices) {
								val newMsgObj = msgObjs[i].messageOwner!!
								messagesStorage.markMessageAsSendError(newMsgObj, scheduled)
								newMsgObj.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
								notificationCenter.postNotificationName(NotificationCenter.messageSendError, newMsgObj.id)
								processSentMessage(newMsgObj.id)
								removeFromSendingMessages(newMsgObj.id, scheduled)
							}
						}
					}

					return@sendRequest
				}
			}

			AndroidUtilities.runOnUIThread {
				var isSentError = false

				if (error == null) {
					val newMessages = SparseArray<Message>()
					val newIds = LongSparseArray<Int>()
					val updates = response as Updates
					val updatesArr = response.updates
					var channelReplies: LongSparseArray<SparseArray<TLRPC.TLMessageReplies>>? = null

					@Suppress("NAME_SHADOWING") var a = 0

					while (a < updatesArr.size) {
						when (val update = updatesArr[a]) {
							is TLRPC.TLUpdateMessageID -> {
								newIds.put(update.randomId, update.id)
								updatesArr.removeAt(a)
								a--
							}

							is TLRPC.TLUpdateNewMessage -> {
								newMessages.put(update.message!!.id, update.message)

								Utilities.stageQueue.postRunnable {
									messagesController.processNewDifferenceParams(-1, update.pts, -1, update.ptsCount)
								}

								updatesArr.removeAt(a)

								a--
							}

							is TLRPC.TLUpdateNewChannelMessage -> {
								val channelId = MessagesController.getUpdateChannelId(update)
								val chat = messagesController.getChat(channelId)

								if ((chat == null || chat.megagroup) && update.message?.replyTo != null && (update.message?.replyTo?.replyToTopId != 0 || update.message?.replyTo?.replyToMsgId != 0)) {
									if (channelReplies == null) {
										channelReplies = LongSparseArray()
									}

									val did = MessageObject.getDialogId(update.message)
									var replies = channelReplies[did]

									if (replies == null) {
										replies = SparseArray()
										channelReplies.put(did, replies)
									}

									val id = (if (update.message?.replyTo?.replyToTopId != 0) update.message?.replyTo?.replyToTopId else update.message?.replyTo?.replyToMsgId) ?: 0
									var messageReplies = replies[id]

									if (messageReplies == null) {
										messageReplies = TLRPC.TLMessageReplies()
										replies.put(id, messageReplies)
									}

									update.message?.fromId?.let {
										messageReplies.recentRepliers.add(0, it)
									}

									messageReplies.replies++
								}

								newMessages.put(update.message!!.id, update.message)

								Utilities.stageQueue.postRunnable {
									messagesController.processNewChannelDifferenceParams(update.pts, update.ptsCount, update.message?.peerId?.channelId ?: 0)
								}

								updatesArr.removeAt(a)

								a--
							}

							is TLRPC.TLUpdateNewScheduledMessage -> {
								newMessages.put(update.message!!.id, update.message)
								updatesArr.removeAt(a)
								a--
							}
						}

						a++
					}

					if (channelReplies != null) {
						messagesStorage.putChannelViews(null, null, channelReplies, true)
						notificationCenter.postNotificationName(NotificationCenter.didUpdateMessagesViews, null, null, channelReplies, true)
					}

					for (i in msgObjs.indices) {
						val msgObj = msgObjs[i]
						val originalPath = originalPaths!![i]
						val newMsgObj = msgObj.messageOwner!!
						val oldId = newMsgObj.id
						val sentMessages = ArrayList<Message>()
						// val attachPath = newMsgObj.attachPath
						val groupedId: Long
						val existFlags: Int
						val id = newIds[newMsgObj.randomId]

						if (id != null) {
							val message = newMessages[id]

							if (message != null) {
								MessageObject.getDialogId(message)

								sentMessages.add(message)

								if (message.flags and 33554432 != 0) {
									msgObj.messageOwner?.ttlPeriod = message.ttlPeriod
									msgObj.messageOwner?.flags = msgObj.messageOwner!!.flags or 33554432
								}

								updateMediaPaths(msgObj, message, message.id, originalPath, false)
								existFlags = msgObj.mediaExistanceFlags
								newMsgObj.id = message.id
								groupedId = message.groupedId

								if (!scheduled) {
									var value = messagesController.dialogs_read_outbox_max[message.dialogId]

									if (value == null) {
										value = messagesStorage.getDialogReadMax(message.out, message.dialogId)
										messagesController.dialogs_read_outbox_max[message.dialogId] = value
									}

									message.unread = value < message.id
								}
							}
							else {
								isSentError = true
								break
							}
						}
						else {
							isSentError = true
							break
						}

						if (!isSentError) {
							statsController.incrementSentItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_MESSAGES, 1)

							newMsgObj.sendState = MessageObject.MESSAGE_SEND_STATE_SENT

							notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialogId, groupedId, existFlags, scheduled)

							messagesStorage.storageQueue.postRunnable {
								messagesStorage.updateMessageStateAndId(newMsgObj.randomId, MessageObject.getPeerId(newMsgObj.peerId), oldId, newMsgObj.id, 0, false, if (scheduled) 1 else 0)
								messagesStorage.putMessages(sentMessages, true, false, false, 0, scheduled)

								AndroidUtilities.runOnUIThread {
									mediaDataController.increasePeerRating(newMsgObj.dialogId)
									notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialogId, groupedId, existFlags, scheduled)
									processSentMessage(oldId)
									removeFromSendingMessages(oldId, scheduled)
								}
							}
						}
					}

					Utilities.stageQueue.postRunnable {
						messagesController.processUpdates(updates, false)
					}
				}
				else {
					AlertsCreator.processError(currentAccount, error, null, req)
					isSentError = true
				}

				if (isSentError) {
					for (i in msgObjs.indices) {
						val newMsgObj = msgObjs[i].messageOwner!!
						messagesStorage.markMessageAsSendError(newMsgObj, scheduled)
						newMsgObj.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
						notificationCenter.postNotificationName(NotificationCenter.messageSendError, newMsgObj.id)
						processSentMessage(newMsgObj.id)
						removeFromSendingMessages(newMsgObj.id, scheduled)
					}
				}
			}
		}, null, ConnectionsManager.RequestFlagCanCompress or ConnectionsManager.RequestFlagInvokeAfter)
	}

	private fun performSendMessageRequest(req: TLObject?, msgObj: MessageObject?, originalPath: String?, delayedMessage: DelayedMessage?, parentObject: Any?, scheduled: Boolean) {
		performSendMessageRequest(req, msgObj, originalPath, null, false, delayedMessage, parentObject, scheduled)
	}

	private fun findMaxDelayedMessageForMessageId(messageId: Int, dialogId: Long): DelayedMessage? {
		var maxDelayedMessage: DelayedMessage? = null
		var maxDelayedMessageId = Int.MIN_VALUE

		for ((_, messages) in delayedMessages) {
			val size = messages.size

			for (a in 0 until size) {
				val delayedMessage = messages[a]

				if ((delayedMessage.type == 4 || delayedMessage.type == 0) && delayedMessage.peer == dialogId) {
					var mid = 0

					if (delayedMessage.obj != null) {
						mid = delayedMessage.obj!!.id
					}
					else if (!delayedMessage.messageObjects.isNullOrEmpty()) {
						mid = delayedMessage.messageObjects!![delayedMessage.messageObjects!!.size - 1].id
					}

					if (mid != 0 && mid > messageId) {
						if (maxDelayedMessage == null && maxDelayedMessageId < mid) {
							maxDelayedMessage = delayedMessage
							maxDelayedMessageId = mid
						}
					}
				}
			}
		}

		return maxDelayedMessage
	}

	fun performSendMessageRequest(req: TLObject?, msgObj: MessageObject?, originalPath: String?, parentMessage: DelayedMessage?, check: Boolean, delayedMessage: DelayedMessage?, parentObject: Any?, scheduled: Boolean) {
		if (req == null) {
			return
		}

		if (req !is TLRPC.TLMessagesEditMessage) {
			if (check) {
				val maxDelayedMessage = findMaxDelayedMessageForMessageId(msgObj!!.id, msgObj.dialogId)

				if (maxDelayedMessage != null) {
					maxDelayedMessage.addDelayedRequest(req, msgObj, originalPath, parentObject, delayedMessage, parentMessage?.scheduled == true)

					if (parentMessage?.requests != null) {
						maxDelayedMessage.requests?.addAll(parentMessage.requests!!)
					}

					return
				}
			}
		}

		val newMsgObj = msgObj!!.messageOwner!!
		putToSendingMessages(newMsgObj, scheduled)

		newMsgObj.reqId = connectionsManager.sendRequest(req, { response, error ->
			if (error != null && (req is TLRPC.TLMessagesSendMedia || req is TLRPC.TLMessagesEditMessage) && FileRefController.isFileRefError(error.text)) {
				if (parentObject != null) {
					fileRefController.requestReference(parentObject, req, msgObj, originalPath, parentMessage, check, delayedMessage, scheduled)
					return@sendRequest
				}
				else if (delayedMessage != null) {
					AndroidUtilities.runOnUIThread {
						removeFromSendingMessages(newMsgObj.id, scheduled)

						if (req is TLRPC.TLMessagesSendMedia) {
							if (req.media is TLRPC.TLInputMediaPhoto) {
								req.media = delayedMessage.inputUploadMedia
							}
							else if (req.media is TLRPC.TLInputMediaDocument) {
								req.media = delayedMessage.inputUploadMedia
							}
						}
						else if (req is TLRPC.TLMessagesEditMessage) {
							if (req.media is TLRPC.TLInputMediaPhoto) {
								req.media = delayedMessage.inputUploadMedia
							}
							else if (req.media is TLRPC.TLInputMediaDocument) {
								req.media = delayedMessage.inputUploadMedia
							}
						}

						delayedMessage.performMediaUpload = true

						performSendDelayedMessage(delayedMessage)
					}

					return@sendRequest
				}
			}

			if (req is TLRPC.TLMessagesEditMessage) {
				AndroidUtilities.runOnUIThread {
					if (error == null) {
						val attachPath = newMsgObj.attachPath
						val updates = response as Updates
						val updatesArr = response.updates
						var message: Message? = null

						for (a in updatesArr.indices) {
							val update = updatesArr[a]

							if (update is TLRPC.TLUpdateEditMessage) {
								message = update.message
								break
							}
							else if (update is TLRPC.TLUpdateEditChannelMessage) {
								message = update.message
								break
							}
							else if (update is TLRPC.TLUpdateNewScheduledMessage) {
								message = update.message
								break
							}
						}

						if (message != null) {
							ImageLoader.saveMessageThumbs(message)
							updateMediaPaths(msgObj, message, message.id, originalPath, false)
						}

						Utilities.stageQueue.postRunnable {
							messagesController.processUpdates(updates, false)

							AndroidUtilities.runOnUIThread {
								processSentMessage(newMsgObj.id)
								removeFromSendingMessages(newMsgObj.id, scheduled)
							}
						}

						if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
							stopVideoService(attachPath)
						}
					}
					else {
						AlertsCreator.processError(currentAccount, error, null, req)

						if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
							stopVideoService(newMsgObj.attachPath)
						}

						removeFromSendingMessages(newMsgObj.id, scheduled)
						revertEditingMessageObject(msgObj)
					}
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					var currentSchedule = scheduled
					var isSentError = false

					if (error == null) {
						val oldId = newMsgObj.id
						val sentMessages = ArrayList<Message>()
						val attachPath = newMsgObj.attachPath
						val existFlags: Int
						val scheduledOnline = newMsgObj.date == 0x7FFFFFFE

						if (response is TLRPC.TLUpdateShortSentMessage) {
							updateMediaPaths(msgObj, null, response.id, null, false)

							existFlags = msgObj.mediaExistanceFlags

							newMsgObj.id = response.id
							newMsgObj.localId = newMsgObj.id
							newMsgObj.date = response.date
							newMsgObj.entities = response.entities
							newMsgObj.out = response.out

							if (response.flags and 33554432 != 0) {
								newMsgObj.ttlPeriod = response.ttlPeriod
								newMsgObj.flags = newMsgObj.flags or 33554432
							}

							if (response.media != null) {
								newMsgObj.media = response.media
								newMsgObj.flags = newMsgObj.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
								ImageLoader.saveMessageThumbs(newMsgObj)
							}

							if ((response.media is TLRPC.TLMessageMediaGame || response.media is TLRPC.TLMessageMediaInvoice) && !TextUtils.isEmpty(response.message)) {
								newMsgObj.message = response.message
							}

							if (!newMsgObj.entities.isNullOrEmpty()) {
								newMsgObj.flags = newMsgObj.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
							}

							currentSchedule = false

							if (!currentSchedule) {
								var value = messagesController.dialogs_read_outbox_max[newMsgObj.dialogId]

								if (value == null) {
									value = messagesStorage.getDialogReadMax(newMsgObj.out, newMsgObj.dialogId)
									messagesController.dialogs_read_outbox_max[newMsgObj.dialogId] = value
								}

								newMsgObj.unread = value < newMsgObj.id
							}

							Utilities.stageQueue.postRunnable {
								messagesController.processNewDifferenceParams(-1, response.pts, response.date, response.ptsCount)
							}

							sentMessages.add(newMsgObj)
						}
						else if (response is Updates) {
							val updatesArr = response.updates
							var message: Message? = null
							var channelReplies: LongSparseArray<SparseArray<TLRPC.TLMessageReplies>>? = null

							for (a in updatesArr.indices) {
								val update = updatesArr[a]

								if (update is TLRPC.TLUpdateNewMessage) {
									sentMessages.add(update.message!!.also {
										message = it
									})

									Utilities.stageQueue.postRunnable {
										messagesController.processNewDifferenceParams(-1, update.pts, -1, update.ptsCount)
									}

									updatesArr.removeAt(a)

									break
								}
								else if (update is TLRPC.TLUpdateNewChannelMessage) {
									val channelId = MessagesController.getUpdateChannelId(update)
									val chat = messagesController.getChat(channelId)

									if ((chat == null || chat.megagroup) && update.message?.replyTo != null && (update.message?.replyTo?.replyToTopId != 0 || update.message?.replyTo?.replyToMsgId != 0)) {
										channelReplies = LongSparseArray()

										val did = MessageObject.getDialogId(update.message)
										var replies = channelReplies[did]

										if (replies == null) {
											replies = SparseArray()
											channelReplies.put(did, replies)
										}

										val id = (if (update.message?.replyTo?.replyToTopId != 0) update.message?.replyTo?.replyToTopId else update.message?.replyTo?.replyToMsgId) ?: 0
										var messageReplies = replies[id]

										if (messageReplies == null) {
											messageReplies = TLRPC.TLMessageReplies()
											replies.put(id, messageReplies)
										}

										update.message?.fromId?.let {
											messageReplies.recentRepliers.add(0, it)
										}

										messageReplies.replies++
									}

									sentMessages.add(update.message!!.also {
										message = it
									})

									Utilities.stageQueue.postRunnable {
										messagesController.processNewChannelDifferenceParams(update.pts, update.ptsCount, update.message?.peerId?.channelId ?: 0)
									}

									updatesArr.removeAt(a)

									break
								}
								else if (update is TLRPC.TLUpdateNewScheduledMessage) {
									sentMessages.add(update.message!!.also { message = it })
									updatesArr.removeAt(a)
									break
								}
							}

							if (channelReplies != null) {
								messagesStorage.putChannelViews(null, null, channelReplies, true)
								notificationCenter.postNotificationName(NotificationCenter.didUpdateMessagesViews, null, null, channelReplies, true)
							}

							if (message != null) {
								MessageObject.getDialogId(message)

								if (scheduledOnline && message!!.date != 0x7FFFFFFE) {
									currentSchedule = false
								}

								ImageLoader.saveMessageThumbs(message)

								if (!currentSchedule) {
									var value = messagesController.dialogs_read_outbox_max[message!!.dialogId]

									if (value == null) {
										value = messagesStorage.getDialogReadMax(message!!.out, message!!.dialogId)
										messagesController.dialogs_read_outbox_max[message!!.dialogId] = value
									}

									message!!.unread = value < message!!.id
								}

								msgObj.messageOwner?.postAuthor = message?.postAuthor

								if (message!!.flags and 33554432 != 0) {
									msgObj.messageOwner?.ttlPeriod = message!!.ttlPeriod
									msgObj.messageOwner?.flags = msgObj.messageOwner!!.flags or 33554432
								}

								msgObj.messageOwner?.entities = message?.entities

								updateMediaPaths(msgObj, message, message!!.id, originalPath, false)
								existFlags = msgObj.mediaExistanceFlags
								newMsgObj.id = message!!.id
							}
							else {
								isSentError = true
								existFlags = 0
							}

							Utilities.stageQueue.postRunnable {
								messagesController.processUpdates(response, false)
							}
						}
						else {
							existFlags = 0
						}

						if (MessageObject.isLiveLocationMessage(newMsgObj) && newMsgObj.viaBotId == 0L) { // MARK: uncomment to enable secret chats:  && TextUtils.isEmpty(newMsgObj.viaBotName)) {
							locationController.addSharingLocation(newMsgObj)
						}

						if (!isSentError) {
							statsController.incrementSentItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_MESSAGES, 1)
							newMsgObj.sendState = MessageObject.MESSAGE_SEND_STATE_SENT

							if (scheduled && !currentSchedule) {
								messagesController.deleteMessages(listOf(oldId), null, null, newMsgObj.dialogId, forAll = false, scheduled = true)

								messagesStorage.storageQueue.postRunnable {
									messagesStorage.putMessages(sentMessages, true, false, false, 0, false)

									AndroidUtilities.runOnUIThread {
										messagesController.updateInterfaceWithMessages(newMsgObj.dialogId, listOf(MessageObject(msgObj.currentAccount, msgObj.messageOwner!!, generateLayout = true, checkMediaExists = true)), false)
										mediaDataController.increasePeerRating(newMsgObj.dialogId)
										processSentMessage(oldId)
										removeFromSendingMessages(oldId, true)
									}

									if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
										stopVideoService(attachPath)
									}
								}
							}
							else {
								notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialogId, 0L, existFlags, scheduled)

								messagesStorage.storageQueue.postRunnable {
									messagesStorage.updateMessageStateAndId(newMsgObj.randomId, MessageObject.getPeerId(newMsgObj.peerId), oldId, newMsgObj.id, 0, false, if (scheduled) 1 else 0)
									messagesStorage.putMessages(sentMessages, true, false, false, 0, scheduled)

									AndroidUtilities.runOnUIThread {
										mediaDataController.increasePeerRating(newMsgObj.dialogId)
										notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialogId, 0L, existFlags, scheduled)
										processSentMessage(oldId)
										removeFromSendingMessages(oldId, scheduled)
									}

									if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
										stopVideoService(attachPath)
									}
								}
							}
						}
					}
					else {
						AlertsCreator.processError(currentAccount, error, null, req)
						isSentError = true
					}

					if (isSentError) {
						messagesStorage.markMessageAsSendError(newMsgObj, scheduled)
						newMsgObj.sendState = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
						notificationCenter.postNotificationName(NotificationCenter.messageSendError, newMsgObj.id)
						processSentMessage(newMsgObj.id)

						if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
							stopVideoService(newMsgObj.attachPath)
						}

						removeFromSendingMessages(newMsgObj.id, scheduled)
					}

					if (error?.text?.lowercase()?.contains("blocked") == true) {
						notificationCenter.postNotificationName(NotificationCenter.chatIsBlocked, abs(newMsgObj.dialogId), NotificationCenter.ERROR_CHAT_BLOCKED)
					}
				}
			}
		}, {
			AndroidUtilities.runOnUIThread {
				newMsgObj.sendState = MessageObject.MESSAGE_SEND_STATE_SENT
				notificationCenter.postNotificationName(NotificationCenter.messageReceivedByAck, newMsgObj.id)
			}
		}, ConnectionsManager.RequestFlagCanCompress or ConnectionsManager.RequestFlagInvokeAfter or if (req is TLRPC.TLMessagesSendMessage) ConnectionsManager.RequestFlagNeedQuickAck else 0)

		parentMessage?.sendDelayedRequests()
	}

	private fun updateMediaPaths(newMsgObj: MessageObject, sentMessage: Message?, newMsgId: Int, originalPath: String?, post: Boolean) {
		val newMsg = newMsgObj.messageOwner
		var strippedNew: PhotoSize? = null

		if (newMsg?.media != null) {
			var strippedOld: PhotoSize? = null
			var photoObject: TLObject? = null

			if (newMsgObj.isLiveLocation && sentMessage?.media is TLRPC.TLMessageMediaGeoLive) {
				newMsg.media?.period = sentMessage.media?.period ?: 0
			}
			else if (newMsgObj.isDice) {
				val mediaDice = newMsg.media as TLRPC.TLMessageMediaDice
				val mediaDiceNew = sentMessage!!.media as TLRPC.TLMessageMediaDice
				mediaDice.value = mediaDiceNew.value
			}
			else if (newMsg.media?.photo != null) {
				strippedOld = FileLoader.getClosestPhotoSizeWithSize(newMsg.media?.photo?.sizes, 40)

				strippedNew = if (sentMessage?.media?.photo != null) {
					FileLoader.getClosestPhotoSizeWithSize(sentMessage.media?.photo?.sizes, 40)
				}
				else {
					strippedOld
				}

				photoObject = newMsg.media?.photo
			}
			else if (newMsg.media?.document != null) {
				strippedOld = FileLoader.getClosestPhotoSizeWithSize(newMsg.media?.document?.thumbs, 40)

				strippedNew = if (sentMessage?.media?.document != null) {
					FileLoader.getClosestPhotoSizeWithSize(sentMessage.media?.document?.thumbs, 40)
				}
				else {
					strippedOld
				}

				photoObject = newMsg.media?.document
			}
			else if (newMsg.media?.webpage != null) {
				if (newMsg.media?.webpage?.photo != null) {
					strippedOld = FileLoader.getClosestPhotoSizeWithSize(newMsg.media?.webpage?.photo?.sizes, 40)

					strippedNew = if (sentMessage?.media?.webpage?.photo != null) {
						FileLoader.getClosestPhotoSizeWithSize(sentMessage.media?.webpage?.photo?.sizes, 40)
					}
					else {
						strippedOld
					}

					photoObject = newMsg.media?.webpage?.photo
				}
				else if (newMsg.media?.webpage?.document != null) {
					strippedOld = FileLoader.getClosestPhotoSizeWithSize(newMsg.media?.webpage?.document?.thumbs, 40)

					strippedNew = if (sentMessage?.media?.webpage?.document != null) {
						FileLoader.getClosestPhotoSizeWithSize(sentMessage.media?.webpage?.document?.thumbs, 40)
					}
					else {
						strippedOld
					}

					photoObject = newMsg.media?.webpage?.document
				}
			}

			if (strippedNew is TLRPC.TLPhotoStrippedSize && strippedOld is TLRPC.TLPhotoStrippedSize) {
				val oldKey = "stripped" + FileRefController.getKeyForParentObject(newMsgObj)

				val newKey = if (sentMessage != null) {
					"stripped" + FileRefController.getKeyForParentObject(sentMessage)
				}
				else {
					"stripped" + "message" + newMsgId + "_" + newMsgObj.channelId + "_" + newMsgObj.scheduled
				}

				ImageLocation.getForObject(strippedNew, photoObject)?.let {
					ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, it, post)
				}
			}
		}

		if (sentMessage == null) {
			return
		}

		val sentMessageMedia = sentMessage.media

		if (sentMessageMedia is TLRPC.TLMessageMediaPhoto && sentMessageMedia.photo != null && newMsg?.media is TLRPC.TLMessageMediaPhoto && newMsg.media?.photo != null) {
			if (sentMessageMedia.ttlSeconds == 0 && !newMsgObj.scheduled) {
				messagesStorage.putSentFile(originalPath, sentMessageMedia.photo, 0, "sent_" + sentMessage.peerId?.channelId + "_" + sentMessage.id)
			}

			if (newMsg.media?.photo?.sizes?.size == 1 && newMsg.media?.photo?.sizes?.get(0)?.location is TLRPC.TLFileLocationUnavailable) {
				newMsg.media?.photo?.sizes?.let { szs ->
					szs.clear()

					sentMessageMedia.photo?.sizes?.let {
						szs.addAll(it)
					}
				}
			}
			else {
				val photoSizes = newMsg.media?.photo?.sizes

				if (photoSizes != null) {
					for (b in photoSizes.indices) {
						val size2 = photoSizes[b]

						if (size2.location == null || size2.type == null) {
							continue
						}

						var found = false
						val sentPhotoSizes = sentMessageMedia.photo?.sizes

						if (!sentPhotoSizes.isNullOrEmpty()) {
							for (size in sentPhotoSizes) {
								if (size.location == null || size is TLRPC.TLPhotoSizeEmpty || size.type == null) {
									continue
								}

								if (size2.location?.volumeId == Int.MIN_VALUE.toLong() && size.type == size2.type || size.w == size2.w && size.h == size2.h) {
									found = true
									val fileName = size2.location?.volumeId?.toString() + "_" + size2.location?.localId
									val fileName2 = size.location?.volumeId?.toString() + "_" + size.location?.localId

									if (fileName == fileName2) {
										break
									}

									val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName.jpg")

									val cacheFile2 = if (sentMessageMedia.ttlSeconds == 0 && (sentMessageMedia.photo?.sizes?.size == 1 || size.w > 90 || size.h > 90)) {
										FileLoader.getInstance(currentAccount).getPathToAttach(size)
									}
									else {
										File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName2.jpg")
									}

									cacheFile.renameTo(cacheFile2)

									ImageLocation.getForPhoto(size, sentMessageMedia.photo)?.let {
										ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, it, post)
									}

									size2.location = size.location
									size2.size = size.size

									break
								}
							}
						}

						if (!found) {
							val fileName = size2.location?.volumeId?.toString() + "_" + size2.location?.localId
							val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName.jpg")

							cacheFile.delete()

							if ("s" == size2.type && strippedNew != null) {
								newMsg.media?.photo?.sizes?.set(b, strippedNew)

								val location = ImageLocation.getForPhoto(strippedNew, sentMessageMedia.photo)
								val key = location?.getKey(sentMessage, null, false)

								if (location != null && key != null) {
									ImageLoader.getInstance().replaceImageInCache(fileName, key, location, post)
								}
							}
						}
					}
				}
			}

			newMsg.message = sentMessage.message

			sentMessage.attachPath = newMsg.attachPath

			newMsg.media?.photo?.id = sentMessageMedia.photo?.id ?: 0
			newMsg.media?.photo?.dcId = sentMessageMedia.photo?.dcId ?: 0
			newMsg.media?.photo?.accessHash = sentMessageMedia.photo?.accessHash ?: 0
		}
		else if (sentMessageMedia is TLRPC.TLMessageMediaDocument && sentMessageMedia.document != null && newMsg?.media is TLRPC.TLMessageMediaDocument && newMsg.media?.document != null) {
			if (sentMessageMedia.ttlSeconds == 0 && (newMsgObj.videoEditedInfo == null || newMsgObj.videoEditedInfo?.mediaEntities == null && newMsgObj.videoEditedInfo?.paintPath.isNullOrEmpty() && newMsgObj.videoEditedInfo?.cropState == null)) {
				val isVideo = MessageObject.isVideoMessage(sentMessage)

				if ((isVideo || MessageObject.isGifMessage(sentMessage)) && MessageObject.isGifDocument(sentMessageMedia.document) == MessageObject.isGifDocument(newMsg.media?.document)) {
					if (!newMsgObj.scheduled) {
						messagesStorage.putSentFile(originalPath, sentMessageMedia.document, 2, "sent_" + sentMessage.peerId?.channelId + "_" + sentMessage.id)
					}
					if (isVideo) {
						sentMessage.attachPath = newMsg.attachPath
					}
				}
				else if (!MessageObject.isVoiceMessage(sentMessage) && !MessageObject.isRoundVideoMessage(sentMessage) && !newMsgObj.scheduled) {
					messagesStorage.putSentFile(originalPath, sentMessageMedia.document, 1, "sent_" + sentMessage.peerId?.channelId + "_" + sentMessage.id)
				}
			}

			val size2 = FileLoader.getClosestPhotoSizeWithSize(newMsg.media?.document?.thumbs, 320)
			val size = FileLoader.getClosestPhotoSizeWithSize(sentMessageMedia.document?.thumbs, 320)

			if (size2?.location != null && size2.location?.volumeId == Int.MIN_VALUE.toLong() && size != null && size.location != null && size !is TLRPC.TLPhotoSizeEmpty && size2 !is TLRPC.TLPhotoSizeEmpty) {
				val fileName = size2.location?.volumeId?.toString() + "_" + size2.location?.localId
				val fileName2 = size.location?.volumeId?.toString() + "_" + size.location?.localId

				if (fileName != fileName2) {
					val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName.jpg")
					val cacheFile2 = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName2.jpg")

					cacheFile.renameTo(cacheFile2)

					ImageLocation.getForDocument(size, sentMessageMedia.document)?.let {
						ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, it, post)
					}

					size2.location = size.location
					size2.size = size.size
				}
			}
			else if (size != null && size2 != null && MessageObject.isStickerMessage(sentMessage) && size2.location != null) {
				size.location = size2.location
			}
			else if (size2 == null || size2.location is TLRPC.TLFileLocationUnavailable || size2 is TLRPC.TLPhotoSizeEmpty) {
				(newMsg.media?.document as? TLRPC.TLDocument)?.thumbs?.let {
					it.clear()

					sentMessageMedia.document?.thumbs?.let { thumbs ->
						it.addAll(thumbs)
					}
				}
			}

			newMsg.media?.document?.dcId = sentMessageMedia.document?.dcId ?: 0
			newMsg.media?.document?.id = sentMessageMedia.document?.id ?: 0
			newMsg.media?.document?.accessHash = sentMessageMedia.document?.accessHash ?: 0

			var oldWaveform: ByteArray? = null

			val attributes = (newMsg.media?.document as? TLRPC.TLDocument)?.attributes

			if (!attributes.isNullOrEmpty()) {
				for (attribute in attributes) {
					if (attribute is TLRPC.TLDocumentAttributeAudio) {
						oldWaveform = attribute.waveform
						break
					}
				}
			}

			attributes?.clear()

			(sentMessageMedia.document as? TLRPC.TLDocument)?.attributes?.let {
				attributes?.addAll(it)
			}

			if (oldWaveform != null) {
				@Suppress("NAME_SHADOWING") val attributes = (newMsg.media?.document as? TLRPC.TLDocument)?.attributes

				if (!attributes.isNullOrEmpty()) {
					for (attribute in attributes) {
						if (attribute is TLRPC.TLDocumentAttributeAudio) {
							attribute.waveform = oldWaveform
							attribute.flags = attribute.flags or 4
						}
					}
				}
			}

			newMsg.media?.document?.size = sentMessageMedia.document?.size ?: 0
			newMsg.media?.document?.mimeType = sentMessageMedia.document?.mimeType

			if (sentMessage.flags and TLRPC.MESSAGE_FLAG_FWD == 0 && MessageObject.isOut(sentMessage)) {
				if (MessageObject.isNewGifDocument(sentMessageMedia.document)) {
					val save = if (MessageObject.isDocumentHasAttachedStickers(sentMessageMedia.document)) {
						messagesController.saveGifsWithStickers
					}
					else {
						true
					}

					if (save) {
						mediaDataController.addRecentGif(sentMessageMedia.document, sentMessage.date, true)
					}
				}
				else if (MessageObject.isStickerDocument(sentMessageMedia.document) || MessageObject.isAnimatedStickerDocument(sentMessageMedia.document, true)) {
					sentMessageMedia.document?.let {
						mediaDataController.addRecentSticker(MediaDataController.TYPE_IMAGE, sentMessage, it, sentMessage.date, false)
					}
				}
			}

			if (newMsg.attachPath != null && newMsg.attachPath!!.startsWith(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)!!.absolutePath)) {
				val cacheFile = File(newMsg.attachPath!!)
				val cacheFile2 = FileLoader.getInstance(currentAccount).getPathToAttach(sentMessageMedia.document, sentMessageMedia.ttlSeconds != 0)

				if (!cacheFile.renameTo(cacheFile2)) {
					if (cacheFile.exists()) {
						sentMessage.attachPath = newMsg.attachPath
					}
					else {
						newMsgObj.attachPathExists = false
					}

					newMsgObj.mediaExists = cacheFile2.exists()

					sentMessage.message = newMsg.message
				}
				else {
					if (MessageObject.isVideoMessage(sentMessage)) {
						newMsgObj.attachPathExists = true
					}
					else {
						newMsgObj.mediaExists = newMsgObj.attachPathExists
						newMsgObj.attachPathExists = false
						newMsg.attachPath = ""

						if (originalPath != null && originalPath.startsWith("http")) {
							messagesStorage.addRecentLocalFile(originalPath, cacheFile2.toString(), newMsg.media?.document)
						}
					}
				}
			}
			else {
				sentMessage.attachPath = newMsg.attachPath
				sentMessage.message = newMsg.message
			}
		}
		else if (sentMessageMedia is TLRPC.TLMessageMediaContact && newMsg?.media is TLRPC.TLMessageMediaContact) {
			newMsg.media = sentMessageMedia
		}
		else if (sentMessageMedia is TLRPC.TLMessageMediaWebPage) {
			newMsg?.media = sentMessageMedia
		}
		else if (sentMessageMedia is TLRPC.TLMessageMediaGeo) {
			val sentGeo = sentMessageMedia.geo as? TLRPC.TLGeoPoint
			val newMsgGeo = newMsg?.media?.geo as? TLRPC.TLGeoPoint

			sentGeo?.lat = newMsgGeo?.lat ?: 0.0
			sentGeo?.lon = newMsgGeo?.lon ?: 0.0
		}
		else if (sentMessageMedia is TLRPC.TLMessageMediaGame || sentMessageMedia is TLRPC.TLMessageMediaInvoice) {
			newMsg?.media = sentMessageMedia

			if (!sentMessage.message.isNullOrEmpty()) {
				newMsg?.entities = sentMessage.entities
				newMsg?.message = sentMessage.message
			}

			if (sentMessage.replyMarkup != null) {
				newMsg?.replyMarkup = sentMessage.replyMarkup
				newMsg?.flags = newMsg!!.flags or TLRPC.MESSAGE_FLAG_HAS_MARKUP
			}
		}
		else if (sentMessageMedia is TLRPC.TLMessageMediaPoll) {
			newMsg?.media = sentMessageMedia
		}
	}

	private fun putToDelayedMessages(location: String?, message: DelayedMessage?) {
		if (location == null || message == null) {
			return
		}

		var arrayList = delayedMessages[location]

		if (arrayList == null) {
			arrayList = ArrayList()
			delayedMessages[location] = arrayList
		}

		arrayList.add(message)
	}

	fun getDelayedMessages(location: String?): List<DelayedMessage>? {
		if (location == null) {
			return null
		}

		return delayedMessages[location]
	}

	val nextRandomId: Long
		get() {
			var `val` = 0L

			while (`val` == 0L) {
				`val` = Utilities.random.nextLong()
			}

			return `val`
		}

	fun checkUnsentMessages() {
		messagesStorage.getUnsentMessages(1000)
	}

	fun processUnsentMessages(messages: ArrayList<Message>, scheduledMessages: ArrayList<Message>?, users: ArrayList<User>?, chats: ArrayList<Chat>?, encryptedChats: ArrayList<EncryptedChat>?) {
		AndroidUtilities.runOnUIThread {
			messagesController.putUsers(users, true)
			messagesController.putChats(chats, true)
			messagesController.putEncryptedChats(encryptedChats, true)

			var a = 0
			val n = messages.size

			while (a < n) {
				val messageObject = MessageObject(currentAccount, messages[a], generateLayout = false, checkMediaExists = true)
				val groupId = messageObject.groupId

				if (groupId != 0L && messageObject.messageOwner?.params != null && !messageObject.messageOwner!!.params!!.containsKey("final")) {
					if (a == n - 1 || messages[a + 1].groupedId != groupId) {
						messageObject.messageOwner!!.params!!["final"] = "1"
					}
				}

				retrySendMessage(messageObject, true)

				a++
			}

			scheduledMessages?.forEach {
				val messageObject = MessageObject(currentAccount, it, generateLayout = false, checkMediaExists = true)
				messageObject.scheduled = true
				retrySendMessage(messageObject, true)
			}
		}
	}

	fun getImportingStickers(shortName: String?): ImportingStickers? {
		return importingStickersMap[shortName]
	}

	fun getImportingHistory(dialogId: Long): ImportingHistory? {
		return importingHistoryMap[dialogId]
	}

	val isImportingStickers: Boolean
		get() = importingStickersMap.isNotEmpty()

	val isImportingHistory: Boolean
		get() = importingHistoryMap.size() != 0

	fun prepareImportHistory(dialogId: Long, uri: Uri?, mediaUris: ArrayList<Uri>?, onStartImport: LongCallback) {
		if (importingHistoryMap[dialogId] != null) {
			onStartImport.run(0)
			return
		}

		if (DialogObject.isChatDialog(dialogId)) {
			val chat = messagesController.getChat(-dialogId)

			if (chat != null && !chat.megagroup) {
				messagesController.convertToMegaGroup(null, -dialogId, null, { chatId ->
					if (chatId != 0L) {
						prepareImportHistory(-chatId, uri, mediaUris, onStartImport)
					}
					else {
						onStartImport.run(0)
					}
				}, null)

				return
			}
		}

		Thread(Runnable {
			val uris = mediaUris ?: ArrayList()

			val importingHistory = ImportingHistory()
			importingHistory.mediaPaths = uris
			importingHistory.dialogId = dialogId
			importingHistory.peer = messagesController.getInputPeer(dialogId)

			val files = HashMap<String, ImportingHistory>()
			var a = 0
			val n = uris.size

			while (a < n + 1) {
				val mediaUri = if (a == 0) {
					uri
				}
				else {
					uris[a - 1]
				}

				if (mediaUri == null || AndroidUtilities.isInternalUri(mediaUri)) {
					if (a == 0) {
						AndroidUtilities.runOnUIThread { onStartImport.run(0) }
						return@Runnable
					}

					a++

					continue
				}

				val path = MediaController.copyFileToCache(mediaUri, "txt")

				if (path == null) {
					a++
					continue
				}

				val f = File(path)
				var size = 0L

				if (!f.exists() || f.length().also { size = it } == 0L) {
					if (a == 0) {
						AndroidUtilities.runOnUIThread { onStartImport.run(0) }
						return@Runnable
					}

					a++

					continue
				}

				importingHistory.totalCount += size

				if (a == 0) {
					if (size > 32 * 1024 * 1024) {
						f.delete()

						AndroidUtilities.runOnUIThread {
							Toast.makeText(ApplicationLoader.applicationContext, ApplicationLoader.applicationContext.getString(R.string.ImportFileTooLarge), Toast.LENGTH_SHORT).show()
							onStartImport.run(0)
						}

						return@Runnable
					}

					importingHistory.historyPath = path
				}
				else {
					importingHistory.uploadMedia.add(path)
				}

				importingHistory.uploadSet.add(path)

				files[path] = importingHistory

				a++
			}

			AndroidUtilities.runOnUIThread {
				importingHistoryFiles.putAll(files)
				importingHistoryMap.put(dialogId, importingHistory)

				fileLoader.uploadFile(importingHistory.historyPath, encrypted = false, small = true, estimatedSize = 0, type = ConnectionsManager.FileTypeFile, forceSmallFile = true)

				notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId)

				onStartImport.run(dialogId)

				val intent = Intent(ApplicationLoader.applicationContext, ImportingService::class.java)

				try {
					ApplicationLoader.applicationContext.startService(intent)
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
		}).start()
	}

	fun prepareImportStickers(title: String, shortName: String, software: String?, paths: List<ImportingSticker>, onStartImport: MessagesStorage.StringCallback) {
		if (importingStickersMap[shortName] != null) {
			onStartImport.run(null)
			return
		}

		Thread(Runnable {
			val importingStickers = ImportingStickers()
			importingStickers.title = title
			importingStickers.shortName = shortName
			importingStickers.software = software

			val files = HashMap<String, ImportingStickers>()
			var a = 0
			val n = paths.size

			while (a < n) {
				val sticker = paths[a]
				val path = sticker.path
				val f = path?.let { File(it) }
				val size = f?.length() ?: 0L

				if (path.isNullOrEmpty() || f == null || !f.exists() || size == 0L) {
					if (a == 0) {
						AndroidUtilities.runOnUIThread { onStartImport.run(null) }
						return@Runnable
					}

					a++

					continue
				}

				importingStickers.totalCount += size
				importingStickers.uploadMedia.add(sticker)
				importingStickers.uploadSet[path] = sticker

				files[path] = importingStickers

				a++
			}

			AndroidUtilities.runOnUIThread {
				if (importingStickers.uploadMedia[0].item != null) {
					importingStickers.startImport()
				}
				else {
					importingStickersFiles.putAll(files)
					importingStickersMap[shortName] = importingStickers
					importingStickers.initImport()
					notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, shortName)
					onStartImport.run(shortName)
				}

				val intent = Intent(ApplicationLoader.applicationContext, ImportingService::class.java)

				try {
					ApplicationLoader.applicationContext.startService(intent)
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
		}).start()
	}

	fun generatePhotoSizes(path: String?, imageUri: Uri?): TLRPC.TLPhoto? {
		return generatePhotoSizes(null, path, imageUri)
	}

	private fun generatePhotoSizes(photo: TLRPC.TLPhoto?, path: String?, imageUri: Uri?): TLRPC.TLPhoto? {
		@Suppress("NAME_SHADOWING") var photo = photo

		var bitmap = ImageLoader.loadBitmap(path, imageUri, AndroidUtilities.getPhotoSize().toFloat(), AndroidUtilities.getPhotoSize().toFloat(), true)

		if (bitmap == null) {
			bitmap = ImageLoader.loadBitmap(path, imageUri, 800f, 800f, true)
		}

		val sizes = ArrayList<PhotoSize>()
		var size = ImageLoader.scaleAndSaveImage(bitmap, 90f, 90f, 55, true)

		if (size != null) {
			sizes.add(size)
		}

		size = ImageLoader.scaleAndSaveImage(bitmap, AndroidUtilities.getPhotoSize().toFloat(), AndroidUtilities.getPhotoSize().toFloat(), true, 80, false, 101, 101)

		if (size != null) {
			sizes.add(size)
		}

		bitmap?.recycle()

		return if (sizes.isEmpty()) {
			null
		}
		else {
			userConfig.saveConfig(false)

			if (photo == null) {
				photo = TLRPC.TLPhoto()
			}

			photo.date = connectionsManager.currentTime
			photo.sizes.clear()
			photo.sizes.addAll(sizes)
			photo.fileReference = ByteArray(0)
			photo
		}
	}

	init {
		AndroidUtilities.runOnUIThread {
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.fileUploaded)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.fileUploadProgressChanged)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.fileUploadFailed)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.filePreparingStarted)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.fileNewChunkAvailable)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.filePreparingFailed)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.httpFileDidFailedLoad)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.httpFileDidLoad)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.fileLoaded)
			notificationCenter.addObserver(this@SendMessagesHelper, NotificationCenter.fileLoadFailed)
		}
	}

	companion object {
		@JvmStatic
		fun checkUpdateStickersOrder(text: CharSequence?): Boolean {
			if (text is Spannable) {
				val spans = text.getSpans(0, text.length, AnimatedEmojiSpan::class.java)

				for (span in spans) {
					if (span.fromEmojiKeyboard) {
						return true
					}
				}
			}

			return false
		}

		private val mediaSendQueue = DispatchQueue("mediaSendQueue")
		private val mediaSendThreadPool: ThreadPoolExecutor

		init {
			val cores = Runtime.getRuntime().availableProcessors()
			mediaSendThreadPool = ThreadPoolExecutor(cores, cores, 60, TimeUnit.SECONDS, LinkedBlockingQueue())
		}

		private val Instance = arrayOfNulls<SendMessagesHelper>(UserConfig.MAX_ACCOUNT_COUNT)

		@JvmStatic
		fun getInstance(num: Int): SendMessagesHelper {
			var localInstance = Instance[num]

			if (localInstance == null) {
				synchronized(SendMessagesHelper::class.java) {
					localInstance = Instance[num]

					if (localInstance == null) {
						localInstance = SendMessagesHelper(num)
						Instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		private const val ERROR_TYPE_UNSUPPORTED = 1
		private const val ERROR_TYPE_FILE_TOO_LARGE = 2

		private fun prepareSendingDocumentInternal(accountInstance: AccountInstance, path: String?, originalPath: String?, uri: Uri?, mime: String?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: CharSequence?, entities: List<MessageEntity>?, editingMessageObject: MessageObject?, groupId: LongArray?, isGroupFinal: Boolean, forceDocument: Boolean, notify: Boolean, scheduleDate: Int, docType: Array<Int?>?): Int {
			@Suppress("NAME_SHADOWING") var path = path
			@Suppress("NAME_SHADOWING") var originalPath = originalPath

			if (path.isNullOrEmpty() && uri == null) {
				return ERROR_TYPE_UNSUPPORTED
			}

			if (uri != null && AndroidUtilities.isInternalUri(uri)) {
				return ERROR_TYPE_UNSUPPORTED
			}

			if (path != null && AndroidUtilities.isInternalUri(Uri.fromFile(File(path)))) {
				return ERROR_TYPE_UNSUPPORTED
			}

			val myMime = MimeTypeMap.getSingleton()
			var attributeAudio: TLRPC.TLDocumentAttributeAudio? = null
			var extension: String? = null

			if (uri != null && path == null) {
				if (checkFileSize(accountInstance, uri)) {
					return ERROR_TYPE_FILE_TOO_LARGE
				}

				var hasExt = false

				if (mime != null) {
					extension = myMime.getExtensionFromMimeType(mime)
				}

				if (extension == null) {
					extension = "txt"
				}
				else {
					hasExt = true
				}

				path = MediaController.copyFileToCache(uri, extension)

				if (path == null) {
					return ERROR_TYPE_UNSUPPORTED
				}

				if (!hasExt) {
					extension = null
				}
			}

			if (path.isNullOrEmpty()) {
				return ERROR_TYPE_UNSUPPORTED
			}

			val f = File(path)

			if (!f.exists() || f.length() == 0L) {
				return ERROR_TYPE_UNSUPPORTED
			}

			if (!FileLoader.checkUploadFileSize(accountInstance.currentAccount, f.length())) {
				return ERROR_TYPE_FILE_TOO_LARGE
			}

			val isEncrypted = DialogObject.isEncryptedDialog(dialogId)
			val name = f.name
			var ext = ""

			if (extension != null) {
				ext = extension
			}
			else {
				val idx = path.lastIndexOf('.')

				if (idx != -1) {
					ext = path.substring(idx + 1)
				}
			}

			val extL = ext.lowercase()
			var permormer: String? = null
			var title: String? = null
			var isVoice = false
			var duration = 0

			if (extL == "mp3" || extL == "m4a") {
				val audioInfo = AudioInfo.getAudioInfo(f)

				if (audioInfo != null) {
					val d = audioInfo.duration

					if (d != 0L) {
						permormer = audioInfo.artist
						title = audioInfo.title
						duration = (d / 1000).toInt()
					}
				}
			}
			else if (extL == "opus" || extL == "ogg" || extL == "flac") {
				runCatching {
					MediaMetadataRetriever().use {
						it.setDataSource(f.absolutePath)

						val d = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

						if (d != null) {
							duration = ceil((d.toLong() / 1000.0f).toDouble()).toInt()
							title = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
							permormer = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
						}

						if (editingMessageObject == null && extL == "ogg" && MediaController.isOpusFile(f.absolutePath) == 1) {
							isVoice = true
						}
					}
				}.onFailure {
					FileLog.e(it)
				}
			}

			if (duration != 0) {
				attributeAudio = TLRPC.TLDocumentAttributeAudio()
				attributeAudio.duration = duration
				attributeAudio.title = title
				attributeAudio.performer = permormer

				if (attributeAudio.title == null) {
					attributeAudio.title = ""
				}

				attributeAudio.flags = attributeAudio.flags or 1

				if (attributeAudio.performer == null) {
					attributeAudio.performer = ""
				}

				attributeAudio.flags = attributeAudio.flags or 2

				if (isVoice) {
					attributeAudio.voice = true
				}
			}

			var sendNew = false

			if (originalPath != null) {
				if (originalPath.endsWith("attheme")) {
					sendNew = true
				}
				else if (attributeAudio != null) {
					originalPath += "audio" + f.length()
				}
				else {
					originalPath += "" + f.length()
				}
			}

			var document: TLRPC.TLDocument? = null
			var parentObject: String? = null

			if (!sendNew && !isEncrypted) {
				var sentData = accountInstance.messagesStorage.getSentFile(originalPath, if (!isEncrypted) 1 else 4)

				if (sentData != null && sentData[0] is TLRPC.TLDocument) {
					document = sentData[0] as TLRPC.TLDocument
					parentObject = sentData[1] as String
				}

				if (document == null && path != originalPath && !isEncrypted) {
					sentData = accountInstance.messagesStorage.getSentFile(path + f.length(), if (!isEncrypted) 1 else 4)

					if (sentData != null && sentData[0] is TLRPC.TLDocument) {
						document = sentData[0] as TLRPC.TLDocument
						parentObject = sentData[1] as String
					}
				}

				ensureMediaThumbExists(accountInstance, false, document, path, null, 0)
			}

			if (document == null) {
				document = TLRPC.TLDocument()
				document.id = 0
				document.date = accountInstance.connectionsManager.currentTime

				val fileName = TLRPC.TLDocumentAttributeFilename()
				fileName.fileName = name

				document.fileReference = ByteArray(0)
				document.attributes.add(fileName)
				document.size = f.length()
				document.dcId = 0

				if (attributeAudio != null) {
					document.attributes.add(attributeAudio)
				}

				if (ext.isNotEmpty()) {
					when (extL) {
						"webp" -> document.mimeType = "image/webp"
						"opus" -> document.mimeType = "audio/opus"
						"mp3" -> document.mimeType = "audio/mpeg"
						"m4a" -> document.mimeType = "audio/m4a"
						"ogg" -> document.mimeType = "audio/ogg"
						"flac" -> document.mimeType = "audio/flac"
						else -> {
							val mimeType = myMime.getMimeTypeFromExtension(extL)

							if (mimeType != null) {
								document.mimeType = mimeType
							}
							else {
								document.mimeType = "application/octet-stream"
							}
						}
					}
				}
				else {
					document.mimeType = "application/octet-stream"
				}

				if (!forceDocument && document.mimeType == "image/gif" && (editingMessageObject == null || editingMessageObject.groupIdForUse == 0L)) {
					try {
						val bitmap = ImageLoader.loadBitmap(f.absolutePath, null, 90f, 90f, true)

						if (bitmap != null) {
							fileName.fileName = "animation.gif"

							document.attributes.add(TLRPC.TLDocumentAttributeAnimated())

							val thumb = ImageLoader.scaleAndSaveImage(bitmap, 90f, 90f, 55, isEncrypted)

							if (thumb != null) {
								document.thumbs.add(thumb)
								document.flags = document.flags or 1
							}

							bitmap.recycle()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				if (document.mimeType == "image/webp" && editingMessageObject == null) {
					val bmOptions = BitmapFactory.Options()

					try {
						bmOptions.inJustDecodeBounds = true
						val file = RandomAccessFile(path, "r")
						val buffer: ByteBuffer = file.channel.map(FileChannel.MapMode.READ_ONLY, 0, path.length.toLong())
						Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true)
						file.close()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					if (bmOptions.outWidth != 0 && bmOptions.outHeight != 0 && bmOptions.outWidth <= 800 && bmOptions.outHeight <= 800) {
						val attributeSticker = TLRPC.TLDocumentAttributeSticker()
						attributeSticker.alt = ""
						attributeSticker.stickerset = TLRPC.TLInputStickerSetEmpty()

						document.attributes.add(attributeSticker)

						val attributeImageSize = TLRPC.TLDocumentAttributeImageSize()
						attributeImageSize.w = bmOptions.outWidth
						attributeImageSize.h = bmOptions.outHeight

						document.attributes.add(attributeImageSize)
					}
				}
				else if (document.mimeType?.startsWith("image/") == true && editingMessageObject == null) {
					val (width, height) = getImageDimensions(path)

					if (width != 0 && height != 0) {
						val attributeImageSize = TLRPC.TLDocumentAttributeImageSize()
						attributeImageSize.w = width
						attributeImageSize.h = height

						document.attributes.add(attributeImageSize)
					}
				}
			}

			val captionFinal = caption?.toString() ?: ""
			val documentFinal: TLRPC.TLDocument = document
			val pathFinal = path
			val parentFinal = parentObject
			val params = HashMap<String, String>()

			if (originalPath != null) {
				params["originalPath"] = originalPath
			}

			if (forceDocument && attributeAudio == null) {
				params["forceDocument"] = "1"
			}

			if (parentFinal != null) {
				params["parentObject"] = parentFinal
			}

			var prevType: Int? = 0
			var isSticker = false

			if (docType != null) {
				prevType = docType[0]

				if (document.mimeType?.lowercase()?.startsWith("image/webp") == true) {
					docType[0] = -1
					isSticker = true
				}
				else if ((document.mimeType?.lowercase()?.startsWith("image/") == true || document.mimeType?.lowercase()?.startsWith("video/mp4") == true) || MessageObject.canPreviewDocument(document)) {
					docType[0] = 1
				}
				else if (attributeAudio != null) {
					docType[0] = 2
				}
				else {
					docType[0] = 0
				}
			}

			if (!isEncrypted && groupId != null) {
				if (docType != null && prevType != null && prevType != docType[0]) {
					finishGroup(accountInstance, groupId[0], scheduleDate)
					groupId[0] = Utilities.random.nextLong()
				}

				if (!isSticker) {
					params["groupId"] = "" + groupId[0]

					if (isGroupFinal) {
						params["final"] = "1"
					}
				}
			}

			AndroidUtilities.runOnUIThread {
				if (editingMessageObject != null) {
					accountInstance.sendMessagesHelper.editMessage(editingMessageObject, null, null, documentFinal, pathFinal, params, false, parentFinal)
				}
				else {
					accountInstance.sendMessagesHelper.sendMessage(documentFinal, null, pathFinal, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, 0, parentFinal, null, false)
				}
			}

			return 0
		}

		private fun checkFileSize(accountInstance: AccountInstance, uri: Uri): Boolean {
			var len: Long = 0

			runCatching {
				ApplicationLoader.applicationContext.contentResolver.openAssetFileDescriptor(uri, "r", null)?.use {
					ApplicationLoader.applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
						val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
						it.moveToFirst()
						len = it.getLong(sizeIndex)
					}
				}
			}.onFailure {
				FileLog.e(it)
			}

			return !FileLoader.checkUploadFileSize(accountInstance.currentAccount, len)
		}

		@JvmStatic
		@UiThread
		fun prepareSendingDocument(accountInstance: AccountInstance, path: String?, originalPath: String?, uri: Uri?, caption: String?, mine: String?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, inputContent: InputContentInfoCompat?, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int) {
			if ((path == null || originalPath == null) && uri == null) {
				return
			}

			val paths = mutableListOf<String>()
			val originalPaths = mutableListOf<String?>()
			var uris: MutableList<Uri>? = null

			if (uri != null) {
				uris = mutableListOf()
				uris.add(uri)
			}

			if (path != null) {
				paths.add(path)
				originalPaths.add(originalPath)
			}

			prepareSendingDocuments(accountInstance, paths, originalPaths, uris, caption, mine, dialogId, replyToMsg, replyToTopMsg, inputContent, editingMessageObject, notify, scheduleDate)
		}

		@JvmStatic
		@UiThread
		fun prepareSendingAudioDocuments(accountInstance: AccountInstance, messageObjects: ArrayList<MessageObject>?, caption: CharSequence?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int) {
			Thread(Runnable {
				val count = messageObjects?.size ?: return@Runnable
				var groupId: Long = 0
				var mediaCount = 0

				for (a in 0 until count) {
					val messageObject = messageObjects[a]
					var originalPath = messageObject.messageOwner?.attachPath
					val f = File(originalPath)
					val isEncrypted = DialogObject.isEncryptedDialog(dialogId)

					if (!isEncrypted && count > 1 && mediaCount % 10 == 0) {
						groupId = Utilities.random.nextLong()
						mediaCount = 0
					}

					if (originalPath != null) {
						originalPath += "audio" + f.length()
					}

					var document: TLRPC.TLDocument? = null
					var parentObject: String? = null

					if (!isEncrypted) {
						val sentData = accountInstance.messagesStorage.getSentFile(originalPath, 1)

						if (sentData != null && sentData[0] is TLRPC.TLDocument) {
							document = sentData[0] as TLRPC.TLDocument
							parentObject = sentData[1] as String
							ensureMediaThumbExists(accountInstance, false, document, originalPath, null, 0)
						}
					}

					if (document == null) {
						document = messageObject.messageOwner?.media?.document as? TLRPC.TLDocument
					}

					if (isEncrypted) {
						val encryptedChatId = DialogObject.getEncryptedChatId(dialogId)
						accountInstance.messagesController.getEncryptedChat(encryptedChatId) ?: return@Runnable
					}

					val documentFinal = document
					val parentFinal = parentObject
					val text = arrayOf(caption)
					val entities = if (a == 0) accountInstance.mediaDataController.getEntities(text, true) else null
					val captionFinal = if (a == 0) text[0].toString() else null
					val params = HashMap<String, String>()

					if (originalPath != null) {
						params["originalPath"] = originalPath
					}

					if (parentFinal != null) {
						params["parentObject"] = parentFinal
					}

					mediaCount++
					params["groupId"] = "" + groupId

					if (mediaCount == 10 || a == count - 1) {
						params["final"] = "1"
					}

					AndroidUtilities.runOnUIThread {
						if (editingMessageObject != null) {
							accountInstance.sendMessagesHelper.editMessage(editingMessageObject, null, null, documentFinal, messageObject.messageOwner?.attachPath, params, false, parentFinal)
						}
						else {
							accountInstance.sendMessagesHelper.sendMessage(documentFinal, null, messageObject.messageOwner?.attachPath, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, 0, parentFinal, null, false)
						}
					}
				}
			}).start()
		}

		private fun finishGroup(accountInstance: AccountInstance, groupId: Long, scheduleDate: Int) {
			AndroidUtilities.runOnUIThread {
				val instance = accountInstance.sendMessagesHelper
				val arrayList = instance.delayedMessages["group_$groupId"]

				if (!arrayList.isNullOrEmpty()) {
					val message = arrayList[0]
					val prevMessage = message.messageObjects!![message.messageObjects!!.size - 1]
					message.finalGroupMessage = prevMessage.id
					prevMessage.messageOwner?.params!!["final"] = "1"
					val messagesRes = TLRPC.TLMessagesMessages()
					messagesRes.messages.add(prevMessage.messageOwner!!)
					accountInstance.messagesStorage.putMessages(messagesRes, message.peer, -2, 0, false, scheduleDate != 0)
					instance.sendReadyToSendGroup(message, add = true, check = true)
				}
			}
		}

		@JvmStatic
		@UiThread
		fun prepareSendingDocuments(accountInstance: AccountInstance, paths: List<String>?, originalPaths: List<String?>?, uris: List<Uri>?, caption: String?, mime: String?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, inputContent: InputContentInfoCompat?, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int) {
			if (paths == null && originalPaths == null && uris == null || paths != null && originalPaths != null && paths.size != originalPaths.size) {
				return
			}

			Utilities.globalQueue.postRunnable {
				var error = 0
				val groupId = LongArray(1)
				var mediaCount = 0
				val docType = arrayOfNulls<Int>(1)
				val isEncrypted = DialogObject.isEncryptedDialog(dialogId)

				if (paths != null) {
					val count = paths.size

					for (a in 0 until count) {
						val captionFinal = if (a == 0) caption else null

						if (!isEncrypted && count > 1 && mediaCount % 10 == 0) {
							if (groupId[0] != 0L) {
								finishGroup(accountInstance, groupId[0], scheduleDate)
							}

							groupId[0] = Utilities.random.nextLong()
							mediaCount = 0
						}

						mediaCount++

						val prevGroupId = groupId[0]

						error = prepareSendingDocumentInternal(accountInstance, paths[a], originalPaths?.get(a), null, mime, dialogId, replyToMsg, replyToTopMsg, captionFinal, null, editingMessageObject, groupId, mediaCount == 10 || a == count - 1, inputContent == null, notify, scheduleDate, docType)

						if (prevGroupId != groupId[0] || groupId[0] == -1L) {
							mediaCount = 1
						}
					}
				}

				if (uris != null) {
					groupId[0] = 0
					mediaCount = 0

					val count = uris.size

					for (a in uris.indices) {
						val captionFinal = if (a == 0 && (paths.isNullOrEmpty())) caption else null

						if (!isEncrypted && count > 1 && mediaCount % 10 == 0) {
							if (groupId[0] != 0L) {
								finishGroup(accountInstance, groupId[0], scheduleDate)
							}

							groupId[0] = Utilities.random.nextLong()
							mediaCount = 0
						}

						mediaCount++

						val prevGroupId = groupId[0]

						error = prepareSendingDocumentInternal(accountInstance, null, null, uris[a], mime, dialogId, replyToMsg, replyToTopMsg, captionFinal, null, editingMessageObject, groupId, mediaCount == 10 || a == count - 1, inputContent == null, notify, scheduleDate, docType)

						if (prevGroupId != groupId[0] || groupId[0] == -1L) {
							mediaCount = 1
						}
					}
				}

				inputContent?.releasePermission()

				handleError(error, accountInstance)
			}
		}

		private fun handleError(error: Int, accountInstance: AccountInstance) {
			if (error != 0) {
				AndroidUtilities.runOnUIThread {
					try {
						if (error == ERROR_TYPE_UNSUPPORTED) {
							NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, ApplicationLoader.applicationContext.getString(R.string.UnsupportedAttachment))
						}
						else if (error == ERROR_TYPE_FILE_TOO_LARGE) {
							NotificationCenter.getInstance(accountInstance.currentAccount).postNotificationName(NotificationCenter.currentUserShowLimitReachedDialog, LimitReachedBottomSheet.TYPE_LARGE_FILE)
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
		}

		@JvmStatic
		@UiThread
		fun prepareSendingPhoto(accountInstance: AccountInstance, imageFilePath: String?, imageUri: Uri?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: CharSequence?, entities: ArrayList<MessageEntity>?, stickers: List<InputDocument>?, inputContent: InputContentInfoCompat?, ttl: Int, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int) {
			prepareSendingPhoto(accountInstance, imageFilePath, null, imageUri, dialogId, replyToMsg, replyToTopMsg, caption, entities, stickers, inputContent, ttl, editingMessageObject, null, notify, scheduleDate, false)
		}

		@JvmStatic
		@UiThread
		fun prepareSendingPhoto(accountInstance: AccountInstance, imageFilePath: String?, thumbFilePath: String?, imageUri: Uri?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: CharSequence?, entities: List<MessageEntity>?, stickers: List<InputDocument>?, inputContent: InputContentInfoCompat?, ttl: Int, editingMessageObject: MessageObject?, videoEditedInfo: VideoEditedInfo?, notify: Boolean, scheduleDate: Int, forceDocument: Boolean) {
			val info = SendingMediaInfo()
			info.path = imageFilePath
			info.thumbPath = thumbFilePath
			info.uri = imageUri

			if (caption != null) {
				info.caption = caption.toString()
			}

			info.entities = entities
			info.ttl = ttl

			if (stickers != null) {
				info.masks = ArrayList(stickers)
			}

			info.videoEditedInfo = videoEditedInfo

			val infos = ArrayList<SendingMediaInfo>()

			infos.add(info)

			prepareSendingMedia(accountInstance, infos, dialogId, replyToMsg, replyToTopMsg, inputContent, forceDocument, false, editingMessageObject, notify, scheduleDate, false)
		}

		@JvmStatic
		@UiThread
		fun prepareSendingBotContextResult(fragment: BaseFragment?, accountInstance: AccountInstance, result: BotInlineResult?, params: HashMap<String, String>?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, notify: Boolean, scheduleDate: Int) {
			if (result == null) {
				return
			}

			if (result.sendMessage is TLRPC.TLBotInlineMessageMediaAuto) {
				Thread(Runnable {
					val isEncrypted = DialogObject.isEncryptedDialog(dialogId)
					var finalPath: String? = null
					var document: TLRPC.TLDocument? = null
					var photo: TLRPC.TLPhoto? = null
					var game: TLRPC.TLGame? = null

					if ("game" == result.type) {
						if (isEncrypted) {
							return@Runnable   //doesn't work in secret chats for now
						}

						game = TLRPC.TLGame()
						game.title = result.title
						game.description = result.description
						game.shortName = result.id
						game.photo = (result as? TLRPC.TLBotInlineMediaResult)?.photo

						if (game.photo == null) {
							game.photo = TLRPC.TLPhotoEmpty()
						}

						if (result.document is TLRPC.TLDocument) {
							game.document = result.document
							game.flags = game.flags or 1
						}
					}
					else if (result is TLRPC.TLBotInlineMediaResult) {
						if (result.document != null) {
							if (result.document is TLRPC.TLDocument) {
								document = result.document as TLRPC.TLDocument
							}
						}
						else if (result.photo != null) {
							if (result.photo is TLRPC.TLPhoto) {
								photo = result.photo as TLRPC.TLPhoto
							}
						}
					}
					else if (result.content != null) {
						var ext = ImageLoader.getHttpUrlExtension(result.content?.url, null)

						ext = if (TextUtils.isEmpty(ext)) {
							FileLoader.getExtensionByMimeType(result.content?.mimeType)
						}
						else {
							".$ext"
						}

						var f = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.content?.url) + ext)

						finalPath = if (f.exists()) {
							f.absolutePath
						}
						else {
							result.content?.url
						}

						when (result.type) {
							"audio", "voice", "file", "video", "sticker", "gif" -> {
								document = TLRPC.TLDocument()
								document.id = 0
								document.size = 0
								document.dcId = 0
								document.mimeType = result.content?.mimeType
								document.fileReference = ByteArray(0)
								document.date = accountInstance.connectionsManager.currentTime

								val fileName = TLRPC.TLDocumentAttributeFilename()

								document.attributes.add(fileName)

								when (result.type) {
									"gif" -> {
										fileName.fileName = "animation.gif"

										if (finalPath?.endsWith("mp4") == true) {
											document.mimeType = "video/mp4"
											document.attributes.add(TLRPC.TLDocumentAttributeAnimated())
										}
										else {
											document.mimeType = "image/gif"
										}

										try {
											val side = if (isEncrypted) 90 else 320
											var bitmap: Bitmap?

											if (finalPath?.endsWith("mp4") == true) {
												bitmap = createVideoThumbnail(finalPath, MediaStore.Video.Thumbnails.MINI_KIND)

												if (bitmap == null && result.thumb is TLRPC.TLWebDocument && "video/mp4" == result.thumb?.mimeType) {
													ext = ImageLoader.getHttpUrlExtension(result.thumb?.url, null)

													ext = if (TextUtils.isEmpty(ext)) {
														FileLoader.getExtensionByMimeType(result.thumb?.mimeType)
													}
													else {
														".$ext"
													}

													f = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb?.url) + ext)

													bitmap = createVideoThumbnail(f.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
												}
											}
											else {
												bitmap = ImageLoader.loadBitmap(finalPath, null, side.toFloat(), side.toFloat(), true)
											}

											if (bitmap != null) {
												val thumb = ImageLoader.scaleAndSaveImage(bitmap, side.toFloat(), side.toFloat(), if (side > 90) 80 else 55, false)

												if (thumb != null) {
													document.thumbs.add(thumb)
													document.flags = document.flags or 1
												}

												bitmap.recycle()
											}
										}
										catch (e: Throwable) {
											FileLog.e(e)
										}
									}

									"voice" -> {
										val audio = TLRPC.TLDocumentAttributeAudio()
										audio.duration = MessageObject.getInlineResultDuration(result)
										audio.voice = true
										fileName.fileName = "audio.ogg"
										document.attributes.add(audio)
									}

									"audio" -> {
										val audio = TLRPC.TLDocumentAttributeAudio()
										audio.duration = MessageObject.getInlineResultDuration(result)
										audio.title = result.title
										audio.flags = audio.flags or 1

										if (result.description != null) {
											audio.performer = result.description
											audio.flags = audio.flags or 2
										}

										fileName.fileName = "audio.mp3"

										document.attributes.add(audio)
									}

									"file" -> {
										val idx = result.content?.mimeType?.lastIndexOf('/') ?: -1

										if (idx != -1) {
											fileName.fileName = "file." + result.content?.mimeType?.substring(idx + 1)
										}
										else {
											fileName.fileName = "file"
										}
									}

									"video" -> {
										fileName.fileName = "video.mp4"

										val attributeVideo = TLRPC.TLDocumentAttributeVideo()
										val wh = MessageObject.getInlineResultWidthAndHeight(result)

										attributeVideo.w = wh[0]
										attributeVideo.h = wh[1]
										attributeVideo.duration = MessageObject.getInlineResultDuration(result)
										attributeVideo.supportsStreaming = true

										document.attributes.add(attributeVideo)

										try {
											if (result.thumb != null) {
												val thumbPath = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb?.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb?.url, "jpg")).absolutePath
												val bitmap = ImageLoader.loadBitmap(thumbPath, null, 90f, 90f, true)

												if (bitmap != null) {
													val thumb = ImageLoader.scaleAndSaveImage(bitmap, 90f, 90f, 55, false)

													if (thumb != null) {
														document.thumbs.add(thumb)
														document.flags = document.flags or 1
													}

													bitmap.recycle()
												}
											}
										}
										catch (e: Throwable) {
											FileLog.e(e)
										}
									}

									"sticker" -> {
										val attributeSticker = TLRPC.TLDocumentAttributeSticker()
										attributeSticker.alt = ""
										attributeSticker.stickerset = TLRPC.TLInputStickerSetEmpty()

										document.attributes.add(attributeSticker)

										val attributeImageSize = TLRPC.TLDocumentAttributeImageSize()
										val wh = MessageObject.getInlineResultWidthAndHeight(result)

										attributeImageSize.w = wh[0]
										attributeImageSize.h = wh[1]

										document.attributes.add(attributeImageSize)

										fileName.fileName = "sticker.webp"

										try {
											if (result.thumb != null) {
												val thumbPath = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb?.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb?.url, "webp")).absolutePath
												val bitmap = ImageLoader.loadBitmap(thumbPath, null, 90f, 90f, true)

												if (bitmap != null) {
													val thumb = ImageLoader.scaleAndSaveImage(bitmap, 90f, 90f, 55, false)

													if (thumb != null) {
														document.thumbs.add(thumb)
														document.flags = document.flags or 1
													}

													bitmap.recycle()
												}
											}
										}
										catch (e: Throwable) {
											FileLog.e(e)
										}
									}
								}

								if (fileName.fileName == null) {
									fileName.fileName = "file"
								}

								if (document.mimeType == null) {
									document.mimeType = "application/octet-stream"
								}

								if (document.thumbs.isEmpty()) {
									val thumb: PhotoSize = TLRPC.TLPhotoSize()
									val wh = MessageObject.getInlineResultWidthAndHeight(result)

									thumb.w = wh[0]
									thumb.h = wh[1]
									thumb.size = 0
									thumb.location = TLRPC.TLFileLocationUnavailable()
									thumb.type = "x"

									document.thumbs.add(thumb)
									document.flags = document.flags or 1
								}
							}

							"photo" -> {
								if (f.exists()) {
									photo = accountInstance.sendMessagesHelper.generatePhotoSizes(finalPath, null)
								}

								if (photo == null) {
									photo = TLRPC.TLPhoto()
									photo.date = accountInstance.connectionsManager.currentTime
									photo.fileReference = ByteArray(0)

									val photoSize = TLRPC.TLPhotoSize()
									val wh = MessageObject.getInlineResultWidthAndHeight(result)

									photoSize.w = wh[0]
									photoSize.h = wh[1]
									photoSize.size = 1
									photoSize.location = TLRPC.TLFileLocationUnavailable()
									photoSize.type = "x"

									photo.sizes.add(photoSize)
								}
							}
						}
					}

					val finalPathFinal = finalPath
					val finalDocument = document
					val finalPhoto = photo
					val finalGame = game

					if (params != null) {
						result.content?.url?.let {
							params["originalPath"] = it
						}
					}

					val precachedThumb = arrayOfNulls<Bitmap>(1)
					val precachedKey = arrayOfNulls<String>(1)

					if (MessageObject.isGifDocument(document)) {
						val photoSizeThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320)
						var gifFile = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(document)

						if (!gifFile.exists()) {
							gifFile = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(document, true)
						}

						ensureMediaThumbExists(accountInstance, isEncrypted, document, gifFile.absolutePath, null, 0)
						precachedKey[0] = getKeyForPhotoSize(accountInstance, photoSizeThumb, precachedThumb, blur = true, forceCache = true)
					}

					val sendToPeer = if (!DialogObject.isEncryptedDialog(dialogId)) accountInstance.messagesController.getInputPeer(dialogId) else null

					if (sendToPeer != null && sendToPeer.userId != 0L && accountInstance.messagesController.getUserFull(sendToPeer.userId) != null && accountInstance.messagesController.getUserFull(sendToPeer.userId)?.voiceMessagesForbidden == true && document != null) {
						if (MessageObject.isVoiceDocument(finalDocument)) {
							AndroidUtilities.runOnUIThread {
								AlertsCreator.showSendMediaAlert(7, fragment)
							}
						}
						else if (MessageObject.isRoundVideoDocument(finalDocument)) {
							AndroidUtilities.runOnUIThread {
								AlertsCreator.showSendMediaAlert(8, fragment)
							}
						}

						return@Runnable
					}

					AndroidUtilities.runOnUIThread {
						if (finalDocument != null) {
							if (precachedThumb[0] != null && precachedKey[0] != null) {
								ImageLoader.getInstance().putImageToCache(precachedThumb[0]?.toDrawable(ApplicationLoader.applicationContext.resources), precachedKey[0], false)
							}

							accountInstance.sendMessagesHelper.sendMessage(finalDocument, null, finalPathFinal, dialogId, replyToMsg, replyToTopMsg, result.sendMessage?.message, result.sendMessage?.entities, result.sendMessage?.replyMarkup, params, notify, scheduleDate, 0, result, null, updateStickersOrder = false)
						}
						else if (finalPhoto != null) {
							accountInstance.sendMessagesHelper.sendMessage(finalPhoto, result.content?.url, dialogId, replyToMsg, replyToTopMsg, result.sendMessage?.message, result.sendMessage?.entities, result.sendMessage?.replyMarkup, params, notify, scheduleDate, 0, result, updateStickersOrder = false)
						}
						else if (finalGame != null) {
							accountInstance.sendMessagesHelper.sendMessage(finalGame, dialogId, result.sendMessage?.replyMarkup, params, notify, scheduleDate)
						}
					}
				}).start()
			}
			else if (result.sendMessage is TLRPC.TLBotInlineMessageText) {
				var webPage: WebPage? = null
				val resultSendMessage = result.sendMessage as TLRPC.TLBotInlineMessageText

				if (DialogObject.isEncryptedDialog(dialogId)) {
					for (a in resultSendMessage.entities.indices) {
						val entity = resultSendMessage.entities[a]

						if (entity is TLRPC.TLMessageEntityUrl) {
							webPage = TLRPC.TLWebPagePending()
							webPage.url = resultSendMessage.message?.substring(entity.offset, entity.offset + entity.length)
							break
						}
					}
				}

				accountInstance.sendMessagesHelper.sendMessage(resultSendMessage.message, dialogId, replyToMsg, replyToTopMsg, webPage, !resultSendMessage.noWebpage, resultSendMessage.entities, resultSendMessage.replyMarkup, params, notify, scheduleDate, null, updateStickersOrder = false)
			}
			else if (result.sendMessage is TLRPC.TLBotInlineMessageMediaVenue) {
				val resultSendMessage = result.sendMessage as TLRPC.TLBotInlineMessageMediaVenue

				val venue = TLRPC.TLMessageMediaVenue()
				venue.geo = resultSendMessage.geo
				venue.address = resultSendMessage.address
				venue.title = resultSendMessage.title
				venue.provider = resultSendMessage.provider
				venue.venueId = resultSendMessage.venueId
				venue.venueType = resultSendMessage.venueType ?: ""

				accountInstance.sendMessagesHelper.sendMessage(venue, dialogId, replyToMsg, replyToTopMsg, resultSendMessage.replyMarkup, params, notify, scheduleDate)
			}
			else if (result.sendMessage is TLRPC.TLBotInlineMessageMediaGeo) {
				val resultSendMessage = result.sendMessage as TLRPC.TLBotInlineMessageMediaGeo

				if (resultSendMessage.period != 0 || resultSendMessage.proximityNotificationRadius != 0) {
					val location = TLRPC.TLMessageMediaGeoLive()
					location.period = if (resultSendMessage.period != 0) resultSendMessage.period else 900
					location.geo = resultSendMessage.geo
					location.heading = resultSendMessage.heading
					location.proximityNotificationRadius = resultSendMessage.proximityNotificationRadius

					accountInstance.sendMessagesHelper.sendMessage(location, dialogId, replyToMsg, replyToTopMsg, resultSendMessage.replyMarkup, params, notify, scheduleDate)
				}
				else {
					val location = TLRPC.TLMessageMediaGeo()
					location.geo = resultSendMessage.geo
					// location.heading = resultSendMessage.heading

					accountInstance.sendMessagesHelper.sendMessage(location, dialogId, replyToMsg, replyToTopMsg, resultSendMessage.replyMarkup, params, notify, scheduleDate)
				}
			}
			else if (result.sendMessage is TLRPC.TLBotInlineMessageMediaContact) {
				val resultSendMessage = result.sendMessage as TLRPC.TLBotInlineMessageMediaContact

				val user = TLRPC.TLUser()
				user.firstName = resultSendMessage.firstName
				user.lastName = resultSendMessage.lastName
				user.username = resultSendMessage.phoneNumber

				val reason = TLRPC.TLRestrictionReason()
				reason.text = resultSendMessage.vcard
				reason.platform = ""
				reason.reason = ""

				user.restrictionReason.add(reason)

				accountInstance.sendMessagesHelper.sendMessage(user, dialogId, replyToMsg, replyToTopMsg, resultSendMessage.replyMarkup, params, notify, scheduleDate)
			}
			else if (result.sendMessage is TLRPC.TLBotInlineMessageMediaInvoice) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					return  //doesn't work in secret chats for now
				}

				val invoice = result.sendMessage as TLRPC.TLBotInlineMessageMediaInvoice

				val messageMediaInvoice = TLRPC.TLMessageMediaInvoice()
				messageMediaInvoice.shippingAddressRequested = invoice.shippingAddressRequested
				messageMediaInvoice.test = invoice.test
				messageMediaInvoice.title = invoice.title
				messageMediaInvoice.description = invoice.description

				if (invoice.photo != null) {
					messageMediaInvoice.photo = invoice.photo
					messageMediaInvoice.flags = messageMediaInvoice.flags or 1
				}

				messageMediaInvoice.currency = invoice.currency
				messageMediaInvoice.totalAmount = invoice.totalAmount
				messageMediaInvoice.startParam = ""

				accountInstance.sendMessagesHelper.sendMessage(messageMediaInvoice, dialogId, replyToMsg, replyToTopMsg, result.sendMessage?.replyMarkup, params, notify, scheduleDate)
			}
		}

		private fun getTrimmedString(src: String): String {
			@Suppress("NAME_SHADOWING") var src = src

			val result = src.trim { it <= ' ' }

			if (result.isEmpty()) {
				return result
			}

			while (src.startsWith("\n")) {
				src = src.substring(1)
			}

			while (src.endsWith("\n")) {
				src = src.substring(0, src.length - 1)
			}

			return src
		}

		@JvmStatic
		@UiThread
		fun prepareSendingText(accountInstance: AccountInstance, text: String, dialogId: Long, notify: Boolean, scheduleDate: Int) {
			accountInstance.messagesStorage.storageQueue.postRunnable {
				Utilities.stageQueue.postRunnable {
					AndroidUtilities.runOnUIThread {
						val textFinal = getTrimmedString(text)

						if (textFinal.isNotEmpty()) {
							val count = ceil((textFinal.length / 4096.0f).toDouble()).toInt()

							for (a in 0 until count) {
								val mess = textFinal.substring(a * 4096, min((a + 1) * 4096, textFinal.length))
								accountInstance.sendMessagesHelper.sendMessage(mess, dialogId, null, null, null, true, null, null, null, notify, scheduleDate, null, updateStickersOrder = false)
							}
						}
					}
				}
			}
		}

		fun ensureMediaThumbExists(accountInstance: AccountInstance, isEncrypted: Boolean, `object`: TLObject?, path: String?, uri: Uri?, startTime: Long) {
			if (`object` is TLRPC.TLPhoto) {
				val smallExists: Boolean
				val smallSize = FileLoader.getClosestPhotoSizeWithSize(`object`.sizes, 90)

				smallExists = if (smallSize is TLRPC.TLPhotoStrippedSize || smallSize is TLRPC.TLPhotoPathSize) {
					true
				}
				else {
					val smallFile = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(smallSize, true)
					smallFile.exists()
				}

				val bigSize = FileLoader.getClosestPhotoSizeWithSize(`object`.sizes, AndroidUtilities.getPhotoSize())
				val bigFile = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(bigSize, false)
				val bigExists = bigFile.exists()

				if (!smallExists || !bigExists) {
					var bitmap = ImageLoader.loadBitmap(path, uri, AndroidUtilities.getPhotoSize().toFloat(), AndroidUtilities.getPhotoSize().toFloat(), true)

					if (bitmap == null) {
						bitmap = ImageLoader.loadBitmap(path, uri, 800f, 800f, true)
					}

					if (!bigExists) {
						val size = ImageLoader.scaleAndSaveImage(bigSize, bitmap, Bitmap.CompressFormat.JPEG, AndroidUtilities.getPhotoSize().toFloat(), AndroidUtilities.getPhotoSize().toFloat(), 80, false, 101, 101, false)

						if (size !== bigSize) {
							`object`.sizes.add(0, size)
						}
					}

					if (!smallExists) {
						val size = ImageLoader.scaleAndSaveImage(smallSize, bitmap, 90f, 90f, 55, true, false)

						if (size !== smallSize) {
							`object`.sizes.add(0, size)
						}
					}

					bitmap?.recycle()
				}
			}
			else if (`object` is TLRPC.TLDocument) {
				if ((MessageObject.isVideoDocument(`object`) || MessageObject.isNewGifDocument(`object`)) && MessageObject.isDocumentHasThumb(`object`)) {
					val photoSize = FileLoader.getClosestPhotoSizeWithSize(`object`.thumbs, 320)

					if (photoSize is TLRPC.TLPhotoStrippedSize || photoSize is TLRPC.TLPhotoPathSize) {
						return
					}

					val smallFile = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(photoSize, true)

					if (!smallFile.exists()) {
						if (path != null) {
							var thumb = createVideoThumbnailAtTime(path, startTime)

							if (thumb == null) {
								thumb = createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
							}

							val side = if (isEncrypted) 90 else 320

							`object`.thumbs[0] = ImageLoader.scaleAndSaveImage(photoSize, thumb, side.toFloat(), side.toFloat(), if (side > 90) 80 else 55, false, true)
						}
					}
				}
			}
		}

		fun getKeyForPhotoSize(accountInstance: AccountInstance, photoSize: PhotoSize?, bitmap: Array<Bitmap?>?, blur: Boolean, forceCache: Boolean): String? {
			if (photoSize?.location == null) {
				return null
			}

			val point = getMessageSize(photoSize.w, photoSize.h)

			if (bitmap != null) {
				try {
					val opts = BitmapFactory.Options()
					opts.inJustDecodeBounds = true

					val file = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(photoSize, forceCache)

					FileInputStream(file).use {
						BitmapFactory.decodeStream(it, null, opts)
					}

					val photoW = opts.outWidth.toFloat()
					val photoH = opts.outHeight.toFloat()
					var scaleFactor = max(photoW / point.x, photoH / point.y)

					if (scaleFactor < 1) {
						scaleFactor = 1f
					}

					opts.inJustDecodeBounds = false
					opts.inSampleSize = scaleFactor.toInt()
					opts.inPreferredConfig = Bitmap.Config.RGB_565

					FileInputStream(file).use {
						bitmap[0] = BitmapFactory.decodeStream(it, null, opts)
					}
				}
				catch (e: Throwable) {
					// ignored
				}
			}

			return String.format(Locale.US, if (blur) "%d_%d@%d_%d_b" else "%d_%d@%d_%d", photoSize.location?.volumeId, photoSize.location?.localId, (point.x / AndroidUtilities.density).toInt(), (point.y / AndroidUtilities.density).toInt())
		}

		fun shouldSendWebPAsSticker(path: String?, uri: Uri?): Boolean {
			val bmOptions = BitmapFactory.Options()
			bmOptions.inJustDecodeBounds = true

			try {
				if (path != null) {
					val file = RandomAccessFile(path, "r")
					val buffer: ByteBuffer = file.channel.map(FileChannel.MapMode.READ_ONLY, 0, path.length.toLong())

					Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true)

					file.close()
				}
				else if (uri != null) {
					runCatching {
						ApplicationLoader.applicationContext.contentResolver.openInputStream(uri)?.use {
							BitmapFactory.decodeStream(it, null, bmOptions)
						}
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			return bmOptions.outWidth < 800 && bmOptions.outHeight < 800
		}

		@JvmStatic
		@UiThread
		fun prepareSendingMedia(accountInstance: AccountInstance, media: List<SendingMediaInfo>, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, inputContent: InputContentInfoCompat?, forceDocument: Boolean, groupMedia: Boolean, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int, updateStickersOrder: Boolean) {
			@Suppress("NAME_SHADOWING") var groupMedia = groupMedia

			if (media.isEmpty()) {
				return
			}

			var a = 0
			val n = media.size

			while (a < n) {
				if (media[a].ttl > 0) {
					groupMedia = false
					break
				}
				a++
			}

			val groupMediaFinal = groupMedia

			mediaSendQueue.postRunnable {
				val beginTime = System.currentTimeMillis()
				val workers: HashMap<SendingMediaInfo, MediaSendPrepareWorker>?
				val count = media.size
				val isEncrypted = DialogObject.isEncryptedDialog(dialogId)

				if (!forceDocument && groupMediaFinal) {
					workers = HashMap()

					for (@Suppress("NAME_SHADOWING") a in 0 until count) {
						val info = media[a]

						if (info.searchImage == null && !info.isVideo && info.videoEditedInfo == null) {
							var originalPath = info.path
							var tempPath = info.path

							if (tempPath == null && info.uri != null) {
								tempPath = AndroidUtilities.getPath(info.uri)
								originalPath = info.uri.toString()
							}

							var isWebP = false

							if (tempPath != null && info.ttl <= 0 && (tempPath.endsWith(".gif") || tempPath.endsWith(".webp").also { isWebP = it })) {
								if (media.size <= 1 && (!isWebP || shouldSendWebPAsSticker(tempPath, null))) {
									continue
								}
								else {
									info.forceImage = true
								}
							}
							else if (ImageLoader.shouldSendImageAsDocument(info.path, info.uri)) {
								continue
							}
							else if (tempPath == null && info.uri != null) {
								if (MediaController.isGif(info.uri) || MediaController.isWebp(info.uri).also { isWebP = it }) {
									if (media.size <= 1 && (!isWebP || shouldSendWebPAsSticker(null, info.uri))) {
										continue
									}
									else {
										info.forceImage = true
									}
								}
							}

							if (tempPath != null) {
								val temp = File(tempPath)
								originalPath += temp.length().toString() + "_" + temp.lastModified()
							}
							else {
								originalPath = null
							}

							var photo: TLRPC.TLPhoto? = null
							var parentObject: String? = null

							if (!isEncrypted && info.ttl == 0) {
								var sentData = accountInstance.messagesStorage.getSentFile(originalPath, if (!isEncrypted) 0 else 3)

								if (sentData != null && sentData[0] is TLRPC.TLPhoto) {
									photo = sentData[0] as TLRPC.TLPhoto
									parentObject = sentData[1] as String
								}
								if (photo == null && info.uri != null) {
									sentData = accountInstance.messagesStorage.getSentFile(AndroidUtilities.getPath(info.uri), if (!isEncrypted) 0 else 3)

									if (sentData != null && sentData[0] is TLRPC.TLPhoto) {
										photo = sentData[0] as TLRPC.TLPhoto
										parentObject = sentData[1] as String
									}
								}

								ensureMediaThumbExists(accountInstance, isEncrypted, photo, info.path, info.uri, 0)
							}

							val worker = MediaSendPrepareWorker()

							workers[info] = worker

							if (photo != null) {
								worker.parentObject = parentObject
								worker.photo = photo
							}
							else {
								worker.sync = CountDownLatch(1)

								mediaSendThreadPool.execute {
									worker.photo = accountInstance.sendMessagesHelper.generatePhotoSizes(info.path, info.uri)

									if (isEncrypted && info.canDeleteAfter) {
										info.path?.let {
											File(it).delete()
										}
									}

									worker.sync?.countDown()
								}
							}
						}
					}
				}
				else {
					workers = null
				}

				var groupId: Long = 0
				var lastGroupId: Long = 0
				var sendAsDocuments: ArrayList<String?>? = null
				var sendAsDocumentsOriginal: ArrayList<String?>? = null
				var sendAsDocumentsUri: ArrayList<Uri?>? = null
				var sendAsDocumentsCaptions: ArrayList<String?>? = null
				var sendAsDocumentsEntities: ArrayList<List<MessageEntity>?>? = null
				var extension: String? = null
				var mediaCount = 0

				for (@Suppress("NAME_SHADOWING") a in 0 until count) {
					val info = media[a]

					if (groupMediaFinal && count > 1 && mediaCount % 10 == 0) {
						groupId = Utilities.random.nextLong()
						lastGroupId = groupId
						mediaCount = 0
					}

					if (info.searchImage != null && info.videoEditedInfo == null) {
						if (info.searchImage!!.type == 1) {
							val params = HashMap<String, String>()
							var document: TLRPC.TLDocument? = null
							val parentObject: String? = null
							var cacheFile: File?

							if (info.searchImage!!.document is TLRPC.TLDocument) {
								document = info.searchImage!!.document as TLRPC.TLDocument
								cacheFile = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(document, true)
							}
							else {/*if (!isEncrypted) {
                                Object[] sentData = getMessagesStorage().getSentFile(info.searchImage.imageUrl, !isEncrypted ? 1 : 4);
                                if (sentData != null && sentData[0] instanceof TLRPC.TLRPC.TLDocument) {
                                    document = (TLRPC.TLRPC.TLDocument) sentData[0];
                                    parentObject = (String) sentData[1];
                                }
                            }*/
								val md5 = Utilities.MD5(info.searchImage!!.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage!!.imageUrl, "jpg")
								cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5)
							}

							if (document == null) {
								var thumbFile: File? = null

								document = TLRPC.TLDocument()
								document.id = 0
								document.fileReference = ByteArray(0)
								document.date = accountInstance.connectionsManager.currentTime

								val fileName = TLRPC.TLDocumentAttributeFilename()
								fileName.fileName = "animation.gif"

								document.attributes.add(fileName)
								document.size = info.searchImage!!.size.toLong()
								document.dcId = 0

								if (!forceDocument && cacheFile.toString().endsWith("mp4")) {
									document.mimeType = "video/mp4"
									document.attributes.add(TLRPC.TLDocumentAttributeAnimated())
								}
								else {
									document.mimeType = "image/gif"
								}

								if (cacheFile.exists()) {
									thumbFile = cacheFile
								}
								else {
									cacheFile = null
								}

								if (thumbFile == null) {
									val thumb = Utilities.MD5(info.searchImage!!.thumbUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage!!.thumbUrl, "jpg")

									thumbFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), thumb)

									if (!thumbFile.exists()) {
										thumbFile = null
									}
								}

								if (thumbFile != null) {
									try {
										val side = if (isEncrypted || info.ttl != 0) 90 else 320

										val bitmap = if (thumbFile.absolutePath.endsWith("mp4")) {
											createVideoThumbnail(thumbFile.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
										}
										else {
											ImageLoader.loadBitmap(thumbFile.absolutePath, null, side.toFloat(), side.toFloat(), true)
										}

										if (bitmap != null) {
											val thumb = ImageLoader.scaleAndSaveImage(bitmap, side.toFloat(), side.toFloat(), if (side > 90) 80 else 55, isEncrypted)

											if (thumb != null) {
												document.thumbs.add(thumb)
												document.flags = document.flags or 1
											}

											bitmap.recycle()
										}
									}
									catch (e: Exception) {
										FileLog.e(e)
									}
								}

								if (document.thumbs.isEmpty()) {
									val thumb = TLRPC.TLPhotoSize()
									thumb.w = info.searchImage!!.width
									thumb.h = info.searchImage!!.height
									thumb.size = 0
									thumb.location = TLRPC.TLFileLocationUnavailable()
									thumb.type = "x"

									document.thumbs.add(thumb)
									document.flags = document.flags or 1
								}
							}

							val documentFinal: TLRPC.TLDocument = document
							// val originalPathFinal = info.searchImage!!.imageUrl
							val pathFinal = cacheFile?.toString() ?: info.searchImage!!.imageUrl

							if (info.searchImage!!.imageUrl != null) {
								params["originalPath"] = info.searchImage!!.imageUrl
							}

							if (parentObject != null) {
								params["parentObject"] = parentObject
							}

							AndroidUtilities.runOnUIThread {
								if (editingMessageObject != null) {
									accountInstance.sendMessagesHelper.editMessage(editingMessageObject, null, null, documentFinal, pathFinal, params, false, parentObject)
								}
								else {
									accountInstance.sendMessagesHelper.sendMessage(documentFinal, null, pathFinal, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, 0, parentObject, null, false)
								}
							}
						}
						else {
							var needDownloadHttp = true
							var photo: TLRPC.TLPhoto? = null
							val parentObject: String? = null

							if (info.searchImage!!.photo is TLRPC.TLPhoto) {
								photo = info.searchImage!!.photo as TLRPC.TLPhoto
							}
//							else {
//								if (!isEncrypted && info.ttl == 0) {/*Object[] sentData = getMessagesStorage().getSentFile(info.searchImage.imageUrl, !isEncrypted ? 0 : 3);
//                                if (sentData != null) {
//                                    photo = (TLRPC.TLRPC.TLPhoto) sentData[0];
//                                    parentObject = (String) sentData[1];
//                                    ensureMediaThumbExists(currentAccount, photo, );
//                                }*/
//								}
//							}

							if (photo == null) {
								var md5 = Utilities.MD5(info.searchImage!!.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage!!.imageUrl, "jpg")
								var cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5)

								if (cacheFile.exists() && cacheFile.length() != 0L) {
									photo = accountInstance.sendMessagesHelper.generatePhotoSizes(cacheFile.toString(), null)

									if (photo != null) {
										needDownloadHttp = false
									}
								}

								if (photo == null) {
									md5 = Utilities.MD5(info.searchImage!!.thumbUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage!!.thumbUrl, "jpg")
									cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5)

									if (cacheFile.exists()) {
										photo = accountInstance.sendMessagesHelper.generatePhotoSizes(cacheFile.toString(), null)
									}

									if (photo == null) {
										photo = TLRPC.TLPhoto()
										photo.date = accountInstance.connectionsManager.currentTime
										photo.fileReference = ByteArray(0)

										val photoSize = TLRPC.TLPhotoSize()
										photoSize.w = info.searchImage!!.width
										photoSize.h = info.searchImage!!.height
										photoSize.size = 0
										photoSize.location = TLRPC.TLFileLocationUnavailable()
										photoSize.type = "x"

										photo.sizes.add(photoSize)
									}
								}
							}

							val photoFinal: TLRPC.TLPhoto = photo
							val needDownloadHttpFinal = needDownloadHttp
							val params = HashMap<String, String>()
							if (info.searchImage!!.imageUrl != null) {
								params["originalPath"] = info.searchImage!!.imageUrl
							}
							if (parentObject != null) {
								params["parentObject"] = parentObject
							}
							if (groupMediaFinal) {
								mediaCount++
								params["groupId"] = "" + groupId
								if (mediaCount == 10 || a == count - 1) {
									params["final"] = "1"
									lastGroupId = 0
								}
							}
							AndroidUtilities.runOnUIThread {
								if (editingMessageObject != null) {
									accountInstance.sendMessagesHelper.editMessage(editingMessageObject, photoFinal, null, null, if (needDownloadHttpFinal) info.searchImage!!.imageUrl else null, params, false, parentObject)
								}
								else {
									accountInstance.sendMessagesHelper.sendMessage(photoFinal, if (needDownloadHttpFinal) info.searchImage!!.imageUrl else null, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentObject, false)
								}
							}
						}
					}
					else {
						if (info.isVideo || info.videoEditedInfo != null) {
							var thumb: Bitmap? = null
							var thumbKey: String? = null

							val videoEditedInfo = if (forceDocument) {
								null
							}
							else {
								if (info.videoEditedInfo != null) {
									info.videoEditedInfo
								}
								else {
									info.path?.let {
										createCompressionSettings(it)
									}
								}
							}

							if (!forceDocument && (videoEditedInfo != null || info.path!!.endsWith("mp4"))) {
								if (info.path == null && info.searchImage != null) {
									if (info.searchImage!!.photo is TLRPC.TLPhoto) {
										info.path = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(info.searchImage!!.photo, true).absolutePath
									}
									else {
										val md5 = Utilities.MD5(info.searchImage!!.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage!!.imageUrl, "jpg")
										info.path = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5).absolutePath
									}
								}

								var path = info.path
								var originalPath = info.path!!
								val temp = File(originalPath)
								var startTime: Long = 0
								var muted = false

								originalPath += temp.length().toString() + "_" + temp.lastModified()

								if (videoEditedInfo != null) {
									muted = videoEditedInfo.muted
									originalPath += videoEditedInfo.estimatedDuration.toString() + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime + if (videoEditedInfo.muted) "_m" else ""

									if (videoEditedInfo.resultWidth != videoEditedInfo.originalWidth) {
										originalPath += "_" + videoEditedInfo.resultWidth
									}

									startTime = if (videoEditedInfo.startTime >= 0) videoEditedInfo.startTime else 0
								}

								var document: TLRPC.TLDocument? = null
								var parentObject: String? = null

								if (!isEncrypted && info.ttl == 0 && (videoEditedInfo == null || videoEditedInfo.filterState == null && videoEditedInfo.paintPath == null && videoEditedInfo.mediaEntities == null && videoEditedInfo.cropState == null)) {
									val sentData = accountInstance.messagesStorage.getSentFile(originalPath, if (!isEncrypted) 2 else 5)

									if (sentData != null && sentData[0] is TLRPC.TLDocument) {
										document = sentData[0] as TLRPC.TLDocument
										parentObject = sentData[1] as String
										ensureMediaThumbExists(accountInstance, isEncrypted, document, info.path, null, startTime)
									}
								}

								if (document == null) {
									if (info.thumbPath != null) {
										thumb = BitmapFactory.decodeFile(info.thumbPath)
									}

									if (thumb == null) {
										val infoPath = info.path

										if (infoPath != null) {
											thumb = createVideoThumbnailAtTime(infoPath, startTime)

											if (thumb == null) {
												thumb = createVideoThumbnail(infoPath, MediaStore.Video.Thumbnails.MINI_KIND)
											}
										}
									}

									var size: PhotoSize? = null
									var localPath: String? = null

									if (thumb != null) {
										val side = if (isEncrypted || info.ttl != 0) 90 else max(thumb.width, thumb.height)

										size = ImageLoader.scaleAndSaveImage(thumb, side.toFloat(), side.toFloat(), if (side > 90) 80 else 55, isEncrypted)
										thumbKey = getKeyForPhotoSize(accountInstance, size, null, blur = true, forceCache = false)

										val fileName = size?.location?.volumeId?.toString() + "_" + size?.location?.localId + ".jpg"
										val fileDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)

										localPath = File(fileDir, fileName).absolutePath
									}

									document = TLRPC.TLDocument()
									document.fileReference = ByteArray(0)
									document.localPath = localPath

									if (size != null) {
										document.thumbs.add(size)
										document.flags = document.flags or 1
									}

									document.mimeType = "video/mp4"

									accountInstance.userConfig.saveConfig(false)

									var attributeVideo: TLRPC.TLDocumentAttributeVideo

									if (isEncrypted) {
										attributeVideo = TLRPC.TLDocumentAttributeVideo()
									}
									else {
										attributeVideo = TLRPC.TLDocumentAttributeVideo()
										attributeVideo.supportsStreaming = true
									}

									document.attributes.add(attributeVideo)

									if (videoEditedInfo != null && (videoEditedInfo.needConvert() || !info.isVideo)) {
										if (info.isVideo && videoEditedInfo.muted) {
											info.path?.let {
												fillVideoAttribute(it, attributeVideo, videoEditedInfo)
											}

											videoEditedInfo.originalWidth = attributeVideo.w
											videoEditedInfo.originalHeight = attributeVideo.h
										}
										else {
											attributeVideo.duration = (videoEditedInfo.estimatedDuration / 1000).toInt()
										}

										var w: Int
										var h: Int

										val rotation = videoEditedInfo.rotationValue

										if (videoEditedInfo.cropState != null) {
											w = videoEditedInfo.cropState.transformWidth
											h = videoEditedInfo.cropState.transformHeight
										}
										else {
											w = videoEditedInfo.resultWidth
											h = videoEditedInfo.resultHeight
										}

										if (rotation == 90 || rotation == 270) {
											attributeVideo.w = h
											attributeVideo.h = w
										}
										else {
											attributeVideo.w = w
											attributeVideo.h = h
										}

										document.size = videoEditedInfo.estimatedSize
									}
									else {
										if (temp.exists()) {
											document.size = temp.length().toInt().toLong()
										}

										info.path?.let {
											fillVideoAttribute(it, attributeVideo, null)
										}
									}
								}
								if (videoEditedInfo != null && videoEditedInfo.muted) {
									if (document.attributes.find { it is TLRPC.TLDocumentAttributeAnimated } == null) {
										document.attributes.add(TLRPC.TLDocumentAttributeAnimated())
									}
								}

								if (videoEditedInfo != null && (videoEditedInfo.needConvert() || !info.isVideo)) {
									val fileName = Int.MIN_VALUE.toString() + "_" + SharedConfig.getLastLocalId() + ".mp4"
									val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName)

									SharedConfig.saveConfig()

									path = cacheFile.absolutePath
								}

								val videoFinal: TLRPC.TLDocument = document
								val parentFinal = parentObject
								val finalPath = path
								val params = HashMap<String, String>()
								val thumbFinal = thumb
								val thumbKeyFinal = thumbKey

								params["originalPath"] = originalPath

								if (parentFinal != null) {
									params["parentObject"] = parentFinal
								}

								if (!muted && groupMediaFinal) {
									mediaCount++
									params["groupId"] = "" + groupId

									if (mediaCount == 10 || a == count - 1) {
										params["final"] = "1"
										lastGroupId = 0
									}
								}

								if (!isEncrypted && info.masks != null && info.masks!!.isNotEmpty()) {
									document.attributes.add(TLRPC.TLDocumentAttributeHasStickers())

									val serializedData = SerializedData(4 + info.masks!!.size * 20)
									serializedData.writeInt32(info.masks!!.size)

									for (b in info.masks!!.indices) {
										info.masks!![b].serializeToStream(serializedData)
									}

									params["masks"] = Utilities.bytesToHex(serializedData.toByteArray())

									serializedData.cleanup()
								}

								AndroidUtilities.runOnUIThread {
									if (thumbFinal != null && thumbKeyFinal != null) {
										ImageLoader.getInstance().putImageToCache(thumbFinal.toDrawable(ApplicationLoader.applicationContext.resources), thumbKeyFinal, false)
									}

									if (editingMessageObject != null) {
										accountInstance.sendMessagesHelper.editMessage(editingMessageObject, null, videoEditedInfo, videoFinal, finalPath, params, false, parentFinal)
									}
									else {
										accountInstance.sendMessagesHelper.sendMessage(videoFinal, videoEditedInfo, finalPath, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentFinal, null, false)
									}
								}
							}
							else {
								if (sendAsDocuments == null) {
									sendAsDocuments = ArrayList()
									sendAsDocumentsOriginal = ArrayList()
									sendAsDocumentsCaptions = ArrayList()
									sendAsDocumentsEntities = ArrayList()
									sendAsDocumentsUri = ArrayList()
								}

								sendAsDocuments.add(info.path)
								sendAsDocumentsOriginal?.add(info.path)
								sendAsDocumentsUri?.add(info.uri)
								sendAsDocumentsCaptions?.add(info.caption)
								sendAsDocumentsEntities?.add(info.entities)
								//prepareSendingDocumentInternal(accountInstance, info.path, info.path, null, null, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, editingMessageObject, null, false, forceDocument, notify, scheduleDate, null);
							}
						}
						else {
							var originalPath = info.path
							var tempPath = info.path

							if (tempPath == null && info.uri != null) {
								tempPath = if (Build.VERSION.SDK_INT >= 30 && "content" == info.uri!!.scheme) {
									null
								}
								else {
									AndroidUtilities.getPath(info.uri)
								}

								originalPath = info.uri.toString()
							}

							var isDocument = false

							if (inputContent != null && info.uri != null) {
								val description = inputContent.description

								if (description.hasMimeType("image/png")) {
									runCatching {
										val bmOptions = BitmapFactory.Options()

										ApplicationLoader.applicationContext.contentResolver.openInputStream(info.uri!!)?.use {
											val b = BitmapFactory.decodeStream(it, null, bmOptions)
											val fileName = Int.MIN_VALUE.toString() + "_" + SharedConfig.getLastLocalId() + ".webp"
											val fileDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)
											val cacheFile = File(fileDir, fileName)

											FileOutputStream(cacheFile).use { fos ->
												b?.compress(Bitmap.CompressFormat.WEBP, 100, fos)
											}

											SharedConfig.saveConfig()

											info.uri = Uri.fromFile(cacheFile)
										}
									}.onFailure {
										FileLog.e(it)
									}
								}
							}

							if (forceDocument || ImageLoader.shouldSendImageAsDocument(info.path, info.uri)) {
								isDocument = true
								extension = if (tempPath != null) FileLoader.getFileExtension(File(tempPath)) else ""
							}
							else if (!info.forceImage && tempPath != null && (tempPath.endsWith(".gif") || tempPath.endsWith(".webp")) && info.ttl <= 0) {
								extension = if (tempPath.endsWith(".gif")) {
									"gif"
								}
								else {
									"webp"
								}

								isDocument = true
							}
							else if (!info.forceImage && tempPath == null && info.uri != null) {
								if (MediaController.isGif(info.uri)) {
									isDocument = true
									originalPath = info.uri.toString()
									tempPath = MediaController.copyFileToCache(info.uri, "gif")
									extension = "gif"
								}
								else if (MediaController.isWebp(info.uri)) {
									isDocument = true
									originalPath = info.uri.toString()
									tempPath = MediaController.copyFileToCache(info.uri, "webp")
									extension = "webp"
								}
							}

							if (isDocument) {
								if (sendAsDocuments == null) {
									sendAsDocuments = ArrayList()
									sendAsDocumentsOriginal = ArrayList()
									sendAsDocumentsCaptions = ArrayList()
									sendAsDocumentsEntities = ArrayList()
									sendAsDocumentsUri = ArrayList()
								}

								sendAsDocuments.add(tempPath)
								sendAsDocumentsOriginal?.add(originalPath)
								sendAsDocumentsUri?.add(info.uri)
								sendAsDocumentsCaptions?.add(info.caption)
								sendAsDocumentsEntities?.add(info.entities)
							}
							else {
								if (tempPath != null) {
									val temp = File(tempPath)
									originalPath += temp.length().toString() + "_" + temp.lastModified()
								}
								else {
									originalPath = null
								}

								var photo: TLRPC.TLPhoto? = null
								var parentObject: String? = null

								if (workers != null) {
									val worker = workers[info]

									photo = worker!!.photo
									parentObject = worker.parentObject

									if (photo == null) {
										try {
											worker.sync!!.await()
										}
										catch (e: Exception) {
											FileLog.e(e)
										}

										photo = worker.photo
										parentObject = worker.parentObject
									}
								}
								else {
									if (!isEncrypted && info.ttl == 0) {
										var sentData = accountInstance.messagesStorage.getSentFile(originalPath, if (!isEncrypted) 0 else 3)

										if (sentData != null && sentData[0] is TLRPC.TLPhoto) {
											photo = sentData[0] as TLRPC.TLPhoto
											parentObject = sentData[1] as String
										}

										if (photo == null && info.uri != null) {
											sentData = accountInstance.messagesStorage.getSentFile(AndroidUtilities.getPath(info.uri), if (!isEncrypted) 0 else 3)

											if (sentData != null && sentData[0] is TLRPC.TLPhoto) {
												photo = sentData[0] as TLRPC.TLPhoto
												parentObject = sentData[1] as String
											}
										}

										ensureMediaThumbExists(accountInstance, isEncrypted, photo, info.path, info.uri, 0)
									}

									if (photo == null) {
										photo = accountInstance.sendMessagesHelper.generatePhotoSizes(info.path, info.uri)

										if (isEncrypted && info.canDeleteAfter) {
											info.path?.let {
												File(it).delete()
											}
										}
									}
								}

								if (photo != null) {
									val photoFinal: TLRPC.TLPhoto = photo
									val parentFinal = parentObject
									val params = HashMap<String, String>()
									val bitmapFinal = arrayOfNulls<Bitmap>(1)
									val keyFinal = arrayOfNulls<String>(1)

									if (info.masks != null && !info.masks!!.isEmpty().also { photo.hasStickers = it }) {
										val serializedData = SerializedData(4 + info.masks!!.size * 20)

										serializedData.writeInt32(info.masks!!.size)

										for (b in info.masks!!.indices) {
											info.masks!![b].serializeToStream(serializedData)
										}

										params["masks"] = Utilities.bytesToHex(serializedData.toByteArray())
										serializedData.cleanup()
									}

									if (originalPath != null) {
										params["originalPath"] = originalPath
									}

									if (parentFinal != null) {
										params["parentObject"] = parentFinal
									}

									try {
										if (!groupMediaFinal || media.size == 1) {
											val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoFinal.sizes, AndroidUtilities.getPhotoSize())

											if (currentPhotoObject != null) {
												keyFinal[0] = getKeyForPhotoSize(accountInstance, currentPhotoObject, bitmapFinal, blur = false, forceCache = false)
											}
										}
									}
									catch (e: Exception) {
										FileLog.e(e)
									}

									if (groupMediaFinal) {
										mediaCount++
										params["groupId"] = "" + groupId

										if (mediaCount == 10 || a == count - 1) {
											params["final"] = "1"
											lastGroupId = 0
										}
									}

									AndroidUtilities.runOnUIThread {
										if (bitmapFinal[0] != null && keyFinal[0] != null) {
											ImageLoader.getInstance().putImageToCache(bitmapFinal[0]?.toDrawable(ApplicationLoader.applicationContext.resources), keyFinal[0], false)
										}

										if (editingMessageObject != null) {
											accountInstance.sendMessagesHelper.editMessage(editingMessageObject, photoFinal, null, null, null, params, false, parentFinal)
										}
										else {
											accountInstance.sendMessagesHelper.sendMessage(photoFinal, null, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentFinal, updateStickersOrder)
										}
									}
								}
								else {
									if (sendAsDocuments == null) {
										sendAsDocuments = ArrayList()
										sendAsDocumentsOriginal = ArrayList()
										sendAsDocumentsCaptions = ArrayList()
										sendAsDocumentsEntities = ArrayList()
										sendAsDocumentsUri = ArrayList()
									}

									sendAsDocuments.add(tempPath)
									sendAsDocumentsOriginal?.add(originalPath)
									sendAsDocumentsUri?.add(info.uri)
									sendAsDocumentsCaptions?.add(info.caption)
									sendAsDocumentsEntities?.add(info.entities)
								}
							}
						}
					}
				}

				if (lastGroupId != 0L) {
					finishGroup(accountInstance, lastGroupId, scheduleDate)
				}

				inputContent?.releasePermission()

				if (!sendAsDocuments.isNullOrEmpty()) {
					val groupId2 = LongArray(1)
					val documentsCount = sendAsDocuments.size

					for (@Suppress("NAME_SHADOWING") a in 0 until documentsCount) {
						if (forceDocument && !isEncrypted && count > 1 && mediaCount % 10 == 0) {
							groupId2[0] = Utilities.random.nextLong()
							mediaCount = 0
						}

						mediaCount++

						val error = prepareSendingDocumentInternal(accountInstance, sendAsDocuments[a], sendAsDocumentsOriginal!![a], sendAsDocumentsUri!![a], extension, dialogId, replyToMsg, replyToTopMsg, sendAsDocumentsCaptions!![a], sendAsDocumentsEntities!![a], editingMessageObject, groupId2, mediaCount == 10 || a == documentsCount - 1, forceDocument, notify, scheduleDate, null)

						handleError(error, accountInstance)
					}
				}

				FileLog.d("total send time = " + (System.currentTimeMillis() - beginTime))
			}
		}

		private fun fillVideoAttribute(videoPath: String, attributeVideo: TLRPC.TLDocumentAttributeVideo, videoEditedInfo: VideoEditedInfo?) {
			var infoObtained = false

			runCatching {
				MediaMetadataRetriever().use {
					it.setDataSource(videoPath)

					val width = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)

					if (width != null) {
						attributeVideo.w = width.toInt()
					}
					val height = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

					if (height != null) {
						attributeVideo.h = height.toInt()
					}
					val duration = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

					if (duration != null) {
						attributeVideo.duration = ceil((duration.toLong() / 1000.0f).toDouble()).toInt()
					}

					val rotation = it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

					if (rotation != null) {
						val `val` = Utilities.parseInt(rotation)

						if (videoEditedInfo != null) {
							videoEditedInfo.rotationValue = `val`
						}
						else if (`val` == 90 || `val` == 270) {
							val temp = attributeVideo.w
							attributeVideo.w = attributeVideo.h
							attributeVideo.h = temp
						}
					}

					infoObtained = true
				}
			}.onFailure {
				FileLog.e(it)
			}

			if (!infoObtained) {
				try {
					val mp = MediaPlayer.create(ApplicationLoader.applicationContext, Uri.fromFile(File(videoPath)))

					if (mp != null) {
						attributeVideo.duration = ceil((mp.duration / 1000.0f).toDouble()).toInt()
						attributeVideo.w = mp.videoWidth
						attributeVideo.h = mp.videoHeight
						mp.release()
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		@JvmStatic
		fun createVideoThumbnail(filePath: String, kind: Int): Bitmap? {
			val size = when (kind) {
				MediaStore.Video.Thumbnails.FULL_SCREEN_KIND -> 1920f
				MediaStore.Video.Thumbnails.MICRO_KIND -> 96f
				else -> 512f
			}

			var bitmap = createVideoThumbnailAtTime(filePath, 0)

			if (bitmap != null) {
				var w = bitmap.width
				var h = bitmap.height

				if (w > size || h > size) {
					val scale = max(w, h) / size

					w = (w.toFloat() / scale).toInt()
					h = (h.toFloat() / scale).toInt()

					bitmap = bitmap.scale(w, h)
				}
			}
			return bitmap
		}

		@JvmStatic
		@JvmOverloads
		fun createVideoThumbnailAtTime(filePath: String, time: Long, orientation: IntArray? = null, precise: Boolean = false): Bitmap? {
			var bitmap: Bitmap? = null

			if (precise) {
				val fileDrawable = AnimatedFileDrawable(File(filePath), true, 0, null, null, null, 0, 0, true, null)

				bitmap = fileDrawable.getFrameAtTime(time, true)

				if (orientation != null) {
					orientation[0] = fileDrawable.orientation
				}

				fileDrawable.recycle()

				if (bitmap == null) {
					return createVideoThumbnailAtTime(filePath, time, orientation, false)
				}
			}
			else {
				runCatching {
					MediaMetadataRetriever().use {
						it.setDataSource(filePath)

						bitmap = it.getFrameAtTime(time, MediaMetadataRetriever.OPTION_NEXT_SYNC)

						if (bitmap == null) {
							bitmap = it.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)
						}
					}
				}.onFailure {
					// Assume this is a corrupt video file.
					FileLog.e(it)
				}
			}

			return bitmap
		}

		private fun createCompressionSettings(videoPath: String): VideoEditedInfo? {
			val params = IntArray(AnimatedFileDrawable.PARAM_NUM_COUNT)
			AnimatedFileDrawable.getVideoInfo(videoPath, params)

			if (params[AnimatedFileDrawable.PARAM_NUM_SUPPORTED_VIDEO_CODEC] == 0) {
				FileLog.d("video hasn't avc1 atom")
				return null
			}

			var originalBitrate = MediaController.getVideoBitrate(videoPath)

			if (originalBitrate == -1) {
				originalBitrate = params[AnimatedFileDrawable.PARAM_NUM_BITRATE]
			}

			var bitrate = originalBitrate
			val videoDuration = params[AnimatedFileDrawable.PARAM_NUM_DURATION].toFloat()
			// val videoFramesSize = params[AnimatedFileDrawable.PARAM_NUM_VIDEO_FRAME_SIZE].toLong()
			val audioFramesSize = params[AnimatedFileDrawable.PARAM_NUM_AUDIO_FRAME_SIZE].toLong()
			val videoFramerate = params[AnimatedFileDrawable.PARAM_NUM_FRAMERATE]

			val videoEditedInfo = VideoEditedInfo()
			videoEditedInfo.startTime = -1
			videoEditedInfo.endTime = -1
			videoEditedInfo.bitrate = bitrate
			videoEditedInfo.originalPath = videoPath
			videoEditedInfo.framerate = videoFramerate
			videoEditedInfo.estimatedDuration = ceil(videoDuration.toDouble()).toLong()
			videoEditedInfo.originalWidth = params[AnimatedFileDrawable.PARAM_NUM_WIDTH]
			videoEditedInfo.resultWidth = videoEditedInfo.originalWidth
			videoEditedInfo.originalHeight = params[AnimatedFileDrawable.PARAM_NUM_HEIGHT]
			videoEditedInfo.resultHeight = videoEditedInfo.originalHeight
			videoEditedInfo.rotationValue = params[AnimatedFileDrawable.PARAM_NUM_ROTATION]
			videoEditedInfo.originalDuration = (videoDuration * 1000).toLong()

			var maxSize = max(videoEditedInfo.originalWidth, videoEditedInfo.originalHeight).toFloat()

			val compressionsCount = if (maxSize > 1280) {
				4
			}
			else if (maxSize > 854) {
				3
			}
			else if (maxSize > 640) {
				2
			}
			else {
				1
			}.toLong()

			var selectedCompression = (DownloadController.getInstance(UserConfig.selectedAccount).maxVideoBitrate / (100f / compressionsCount)).roundToLong()

			if (selectedCompression > compressionsCount) {
				selectedCompression = compressionsCount
			}

			var needCompress = false

			if (File(videoPath).length() < 1024L * 1024L * 1000L) {
				if (selectedCompression != compressionsCount || max(videoEditedInfo.originalWidth, videoEditedInfo.originalHeight) > 1280) {
					needCompress = true
					maxSize = when (selectedCompression) {
						1L -> 432.0f
						2L -> 640.0f
						3L -> 848.0f
						else -> 1280.0f
					}

					val scale = if (videoEditedInfo.originalWidth > videoEditedInfo.originalHeight) maxSize / videoEditedInfo.originalWidth else maxSize / videoEditedInfo.originalHeight
					videoEditedInfo.resultWidth = (videoEditedInfo.originalWidth * scale / 2).roundToInt() * 2
					videoEditedInfo.resultHeight = (videoEditedInfo.originalHeight * scale / 2).roundToInt() * 2
				}

				bitrate = MediaController.makeVideoBitrate(videoEditedInfo.originalHeight, videoEditedInfo.originalWidth, originalBitrate, videoEditedInfo.resultHeight, videoEditedInfo.resultWidth)
			}

			if (!needCompress) {
				videoEditedInfo.resultWidth = videoEditedInfo.originalWidth
				videoEditedInfo.resultHeight = videoEditedInfo.originalHeight
				videoEditedInfo.bitrate = bitrate
			}
			else {
				videoEditedInfo.bitrate = bitrate
			}

			videoEditedInfo.estimatedSize = (audioFramesSize + videoDuration / 1000.0f * bitrate / 8).toLong()

			if (videoEditedInfo.estimatedSize == 0L) {
				videoEditedInfo.estimatedSize = 1
			}

			return videoEditedInfo
		}

		@JvmStatic
		@UiThread
		fun prepareSendingVideo(accountInstance: AccountInstance, videoPath: String?, info: VideoEditedInfo?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: CharSequence?, entities: List<MessageEntity>?, ttl: Int, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int, forceDocument: Boolean) {
			if (videoPath.isNullOrEmpty()) {
				return
			}

			Thread(Runnable {
				val videoEditedInfo = info ?: createCompressionSettings(videoPath)
				val isEncrypted = DialogObject.isEncryptedDialog(dialogId)
				val isRound = videoEditedInfo != null && videoEditedInfo.roundVideo
				var thumb: Bitmap? = null
				var thumbKey: String? = null

				if (videoEditedInfo != null || videoPath.endsWith("mp4") || isRound) {
					var path: String = videoPath
					var originalPath = videoPath
					val temp = File(originalPath)
					var startTime: Long = 0

					originalPath += temp.length().toString() + "_" + temp.lastModified()

					if (videoEditedInfo != null) {
						if (!isRound) {
							originalPath += videoEditedInfo.estimatedDuration.toString() + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime + if (videoEditedInfo.muted) "_m" else ""

							if (videoEditedInfo.resultWidth != videoEditedInfo.originalWidth) {
								originalPath += "_" + videoEditedInfo.resultWidth
							}
						}
						startTime = if (videoEditedInfo.startTime >= 0) videoEditedInfo.startTime else 0
					}

					var document: TLRPC.TLDocument? = null
					var parentObject: String? = null

					if (!isEncrypted && ttl == 0 && (videoEditedInfo == null || videoEditedInfo.filterState == null && videoEditedInfo.paintPath == null && videoEditedInfo.mediaEntities == null && videoEditedInfo.cropState == null)) {
						val sentData = accountInstance.messagesStorage.getSentFile(originalPath, 2)

						if (sentData != null && sentData[0] is TLRPC.TLDocument) {
							document = sentData[0] as TLRPC.TLDocument
							parentObject = sentData[1] as String
							ensureMediaThumbExists(accountInstance, false, document, videoPath, null, startTime)
						}
					}

					if (document == null) {
						thumb = createVideoThumbnailAtTime(videoPath, startTime)

						if (thumb == null) {
							thumb = createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND)
						}

						val side = if (isEncrypted || ttl != 0) 90 else 320
						val size = ImageLoader.scaleAndSaveImage(thumb, side.toFloat(), side.toFloat(), if (side > 90) 80 else 55, isEncrypted)

						if (thumb != null && size != null) {
							if (isRound) {
								if (isEncrypted) {
									thumb = thumb.scale(90, 90)

									Utilities.blurBitmap(thumb, 7, 1, thumb.width, thumb.height, thumb.rowBytes)
									Utilities.blurBitmap(thumb, 7, 1, thumb.width, thumb.height, thumb.rowBytes)
									Utilities.blurBitmap(thumb, 7, 1, thumb.width, thumb.height, thumb.rowBytes)

									thumbKey = String.format(size.location?.volumeId?.toString() + "_" + size.location?.localId + "@%d_%d_b2", (AndroidUtilities.roundMessageSize / AndroidUtilities.density).toInt(), (AndroidUtilities.roundMessageSize / AndroidUtilities.density).toInt())
								}
								else {
									Utilities.blurBitmap(thumb, 3, 1, thumb.width, thumb.height, thumb.rowBytes)
									thumbKey = String.format(size.location?.volumeId?.toString() + "_" + size.location?.localId + "@%d_%d_b", (AndroidUtilities.roundMessageSize / AndroidUtilities.density).toInt(), (AndroidUtilities.roundMessageSize / AndroidUtilities.density).toInt())
								}
							}
							else {
								thumb = null
							}
						}

						document = TLRPC.TLDocument()

						if (size != null) {
							document.thumbs.add(size)
							document.flags = document.flags or 1
						}

						document.fileReference = ByteArray(0)
						document.mimeType = "video/mp4"

						accountInstance.userConfig.saveConfig(false)

						val attributeVideo: TLRPC.TLDocumentAttributeVideo

						if (isEncrypted) {
							val encryptedChatId = DialogObject.getEncryptedChatId(dialogId)
							accountInstance.messagesController.getEncryptedChat(encryptedChatId) ?: return@Runnable
							attributeVideo = TLRPC.TLDocumentAttributeVideo()
						}
						else {
							attributeVideo = TLRPC.TLDocumentAttributeVideo()
							attributeVideo.supportsStreaming = true
						}

						attributeVideo.roundMessage = isRound

						document.attributes.add(attributeVideo)

						if (videoEditedInfo != null && videoEditedInfo.needConvert()) {
							if (videoEditedInfo.muted) {
								document.attributes.add(TLRPC.TLDocumentAttributeAnimated())

								fillVideoAttribute(videoPath, attributeVideo, videoEditedInfo)

								videoEditedInfo.originalWidth = attributeVideo.w
								videoEditedInfo.originalHeight = attributeVideo.h
							}
							else {
								attributeVideo.duration = (videoEditedInfo.estimatedDuration / 1000).toInt()
							}

							val w: Int
							val h: Int
							var rotation = videoEditedInfo.rotationValue

							if (videoEditedInfo.cropState != null) {
								w = videoEditedInfo.cropState.transformWidth
								h = videoEditedInfo.cropState.transformHeight
								rotation += videoEditedInfo.cropState.transformRotation
							}
							else {
								w = videoEditedInfo.resultWidth
								h = videoEditedInfo.resultHeight
							}

							if (rotation == 90 || rotation == 270) {
								attributeVideo.w = h
								attributeVideo.h = w
							}
							else {
								attributeVideo.w = w
								attributeVideo.h = h
							}

							document.size = videoEditedInfo.estimatedSize
						}
						else {
							if (temp.exists()) {
								document.size = temp.length().toInt().toLong()
							}

							fillVideoAttribute(videoPath, attributeVideo, null)
						}
					}

					if (videoEditedInfo != null && videoEditedInfo.needConvert()) {
						val fileName = Int.MIN_VALUE.toString() + "_" + SharedConfig.getLastLocalId() + ".mp4"
						val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName)

						SharedConfig.saveConfig()

						path = cacheFile.absolutePath
					}

					val videoFinal: TLRPC.TLDocument = document
					val parentFinal = parentObject
					val finalPath = path
					val params = HashMap<String, String>()
					val thumbFinal = thumb
					val thumbKeyFinal = thumbKey
					val captionFinal = caption?.toString() ?: ""

					params["originalPath"] = originalPath

					if (parentFinal != null) {
						params["parentObject"] = parentFinal
					}

					AndroidUtilities.runOnUIThread {
						if (thumbFinal != null && thumbKeyFinal != null) {
							ImageLoader.getInstance().putImageToCache(thumbFinal.toDrawable(ApplicationLoader.applicationContext.resources), thumbKeyFinal, false)
						}

						if (editingMessageObject != null) {
							accountInstance.sendMessagesHelper.editMessage(editingMessageObject, null, videoEditedInfo, videoFinal, finalPath, params, false, parentFinal)
						}
						else {
							accountInstance.sendMessagesHelper.sendMessage(videoFinal, videoEditedInfo, finalPath, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, ttl, parentFinal, null, false)
						}
					}
				}
				else {
					prepareSendingDocumentInternal(accountInstance, videoPath, videoPath, null, null, dialogId, replyToMsg, replyToTopMsg, caption, entities, editingMessageObject, null, false, forceDocument, notify, scheduleDate, null)
				}
			}).start()
		}
	}
}
