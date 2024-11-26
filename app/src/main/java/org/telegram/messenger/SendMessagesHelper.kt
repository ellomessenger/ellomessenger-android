/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
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
import android.graphics.drawable.BitmapDrawable
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
import androidx.core.view.inputmethod.InputContentInfoCompat
import org.json.JSONObject
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
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.RequestDelegate
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInlineResult
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.DecryptedMessage
import org.telegram.tgnet.TLRPC.EncryptedChat
import org.telegram.tgnet.TLRPC.InputCheckPasswordSRP
import org.telegram.tgnet.TLRPC.InputDocument
import org.telegram.tgnet.TLRPC.InputEncryptedFile
import org.telegram.tgnet.TLRPC.InputMedia
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.KeyboardButton
import org.telegram.tgnet.TLRPC.MessageMedia
import org.telegram.tgnet.TLRPC.MessageReplies
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.ReplyMarkup
import org.telegram.tgnet.TLRPC.TL_account_getPassword
import org.telegram.tgnet.TLRPC.TL_botInlineMediaResult
import org.telegram.tgnet.TLRPC.TL_botInlineMessageMediaAuto
import org.telegram.tgnet.TLRPC.TL_botInlineMessageMediaContact
import org.telegram.tgnet.TLRPC.TL_botInlineMessageMediaGeo
import org.telegram.tgnet.TLRPC.TL_botInlineMessageMediaInvoice
import org.telegram.tgnet.TLRPC.TL_botInlineMessageMediaVenue
import org.telegram.tgnet.TLRPC.TL_botInlineMessageText
import org.telegram.tgnet.TLRPC.TL_dataJSON
import org.telegram.tgnet.TLRPC.TL_decryptedMessage
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionAbortKey
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionAcceptKey
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionCommitKey
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionDeleteMessages
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionFlushHistory
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionNoop
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionNotifyLayer
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionReadMessages
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionRequestKey
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionResend
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionScreenshotMessages
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionSetMessageTTL
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionTyping
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaContact
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaDocument
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaEmpty
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaExternalDocument
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaGeoPoint
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaPhoto
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaVenue
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaVideo
import org.telegram.tgnet.TLRPC.TL_decryptedMessageMediaWebPage
import org.telegram.tgnet.TLRPC.TL_decryptedMessage_layer45
import org.telegram.tgnet.TLRPC.TL_document
import org.telegram.tgnet.TLRPC.TL_documentAttributeAnimated
import org.telegram.tgnet.TLRPC.TL_documentAttributeAudio
import org.telegram.tgnet.TLRPC.TL_documentAttributeFilename
import org.telegram.tgnet.TLRPC.TL_documentAttributeHasStickers
import org.telegram.tgnet.TLRPC.TL_documentAttributeImageSize
import org.telegram.tgnet.TLRPC.TL_documentAttributeSticker
import org.telegram.tgnet.TLRPC.TL_documentAttributeSticker_layer55
import org.telegram.tgnet.TLRPC.TL_documentAttributeVideo
import org.telegram.tgnet.TLRPC.TL_document_layer82
import org.telegram.tgnet.tlrpc.TL_error
import org.telegram.tgnet.TLRPC.TL_fileLocationUnavailable
import org.telegram.tgnet.TLRPC.TL_fileLocation_layer82
import org.telegram.tgnet.TLRPC.TL_game
import org.telegram.tgnet.TLRPC.TL_geoPoint
import org.telegram.tgnet.TLRPC.TL_inputCheckPasswordEmpty
import org.telegram.tgnet.TLRPC.TL_inputDocument
import org.telegram.tgnet.TLRPC.TL_inputEncryptedFile
import org.telegram.tgnet.TLRPC.TL_inputGeoPoint
import org.telegram.tgnet.TLRPC.TL_inputInvoiceMessage
import org.telegram.tgnet.TLRPC.TL_inputMediaContact
import org.telegram.tgnet.TLRPC.TL_inputMediaDice
import org.telegram.tgnet.TLRPC.TL_inputMediaDocument
import org.telegram.tgnet.TLRPC.TL_inputMediaGame
import org.telegram.tgnet.TLRPC.TL_inputMediaGeoLive
import org.telegram.tgnet.TLRPC.TL_inputMediaGeoPoint
import org.telegram.tgnet.TLRPC.TL_inputMediaPhoto
import org.telegram.tgnet.TLRPC.TL_inputMediaPoll
import org.telegram.tgnet.TLRPC.TL_inputMediaUploadedDocument
import org.telegram.tgnet.TLRPC.TL_inputMediaUploadedPhoto
import org.telegram.tgnet.TLRPC.TL_inputMediaVenue
import org.telegram.tgnet.TLRPC.TL_inputPeerChannel
import org.telegram.tgnet.TLRPC.TL_inputPeerChat
import org.telegram.tgnet.TLRPC.TL_inputPeerEmpty
import org.telegram.tgnet.TLRPC.TL_inputPeerSelf
import org.telegram.tgnet.TLRPC.TL_inputPeerUser
import org.telegram.tgnet.TLRPC.TL_inputPhoto
import org.telegram.tgnet.TLRPC.TL_inputSingleMedia
import org.telegram.tgnet.TLRPC.TL_inputStickerSetEmpty
import org.telegram.tgnet.TLRPC.TL_inputStickerSetID
import org.telegram.tgnet.TLRPC.TL_inputStickerSetItem
import org.telegram.tgnet.TLRPC.TL_inputStickerSetShortName
import org.telegram.tgnet.TLRPC.TL_inputUserSelf
import org.telegram.tgnet.TLRPC.TL_keyboardButtonBuy
import org.telegram.tgnet.TLRPC.TL_keyboardButtonGame
import org.telegram.tgnet.TLRPC.TL_keyboardButtonRow
import org.telegram.tgnet.TLRPC.TL_keyboardButtonSwitchInline
import org.telegram.tgnet.TLRPC.TL_keyboardButtonUrl
import org.telegram.tgnet.TLRPC.TL_keyboardButtonUrlAuth
import org.telegram.tgnet.TLRPC.TL_messageActionScreenshotTaken
import org.telegram.tgnet.TLRPC.TL_messageEncryptedAction
import org.telegram.tgnet.TLRPC.TL_messageFwdHeader
import org.telegram.tgnet.TLRPC.TL_messageMediaContact
import org.telegram.tgnet.TLRPC.TL_messageMediaDice
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument
import org.telegram.tgnet.TLRPC.TL_messageMediaEmpty
import org.telegram.tgnet.TLRPC.TL_messageMediaGame
import org.telegram.tgnet.TLRPC.TL_messageMediaGeo
import org.telegram.tgnet.TLRPC.TL_messageMediaGeoLive
import org.telegram.tgnet.TLRPC.TL_messageMediaInvoice
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto
import org.telegram.tgnet.TLRPC.TL_messageMediaPoll
import org.telegram.tgnet.TLRPC.TL_messageMediaVenue
import org.telegram.tgnet.TLRPC.TL_messageMediaWebPage
import org.telegram.tgnet.TLRPC.TL_messageReplies
import org.telegram.tgnet.TLRPC.TL_messageReplyHeader
import org.telegram.tgnet.TLRPC.TL_messageService
import org.telegram.tgnet.TLRPC.TL_message_secret
import org.telegram.tgnet.TLRPC.TL_messages_botCallbackAnswer
import org.telegram.tgnet.TLRPC.TL_messages_forwardMessages
import org.telegram.tgnet.TLRPC.TL_messages_getBotCallbackAnswer
import org.telegram.tgnet.TLRPC.TL_messages_getStickerSet
import org.telegram.tgnet.TLRPC.TL_messages_historyImport
import org.telegram.tgnet.TLRPC.TL_messages_initHistoryImport
import org.telegram.tgnet.TLRPC.TL_messages_requestUrlAuth
import org.telegram.tgnet.TLRPC.TL_messages_sendEncryptedMultiMedia
import org.telegram.tgnet.TLRPC.TL_messages_sendInlineBotResult
import org.telegram.tgnet.TLRPC.TL_messages_sendMedia
import org.telegram.tgnet.TLRPC.TL_messages_sendMessage
import org.telegram.tgnet.TLRPC.TL_messages_sendMultiMedia
import org.telegram.tgnet.TLRPC.TL_messages_sendReaction
import org.telegram.tgnet.TLRPC.TL_messages_sendScreenshotNotification
import org.telegram.tgnet.TLRPC.TL_messages_sendVote
import org.telegram.tgnet.TLRPC.TL_messages_startHistoryImport
import org.telegram.tgnet.TLRPC.TL_messages_stickerSet
import org.telegram.tgnet.TLRPC.TL_messages_uploadImportedMedia
import org.telegram.tgnet.TLRPC.TL_messages_uploadMedia
import org.telegram.tgnet.TLRPC.TL_payments_getPaymentForm
import org.telegram.tgnet.TLRPC.TL_payments_getPaymentReceipt
import org.telegram.tgnet.TLRPC.TL_payments_paymentForm
import org.telegram.tgnet.TLRPC.TL_payments_paymentReceipt
import org.telegram.tgnet.TLRPC.TL_peerChannel
import org.telegram.tgnet.TLRPC.TL_peerUser
import org.telegram.tgnet.tlrpc.TL_photo
import org.telegram.tgnet.TLRPC.TL_photoCachedSize
import org.telegram.tgnet.TLRPC.TL_photoEmpty
import org.telegram.tgnet.TLRPC.TL_photoPathSize
import org.telegram.tgnet.TLRPC.TL_photoSize
import org.telegram.tgnet.TLRPC.TL_photoSizeEmpty
import org.telegram.tgnet.TLRPC.TL_photoSizeProgressive
import org.telegram.tgnet.TLRPC.TL_photoSize_layer127
import org.telegram.tgnet.TLRPC.TL_photoStrippedSize
import org.telegram.tgnet.TLRPC.TL_pollAnswer
import org.telegram.tgnet.TLRPC.TL_replyInlineMarkup
import org.telegram.tgnet.TLRPC.TL_restrictionReason
import org.telegram.tgnet.TLRPC.TL_stickers_createStickerSet
import org.telegram.tgnet.TLRPC.TL_updateEditChannelMessage
import org.telegram.tgnet.TLRPC.TL_updateEditMessage
import org.telegram.tgnet.TLRPC.TL_updateMessageID
import org.telegram.tgnet.TLRPC.TL_updateNewChannelMessage
import org.telegram.tgnet.TLRPC.TL_updateNewMessage
import org.telegram.tgnet.TLRPC.TL_updateNewScheduledMessage
import org.telegram.tgnet.TLRPC.TL_updateShortSentMessage
import org.telegram.tgnet.TLRPC.TL_urlAuthResultAccepted
import org.telegram.tgnet.TLRPC.TL_urlAuthResultDefault
import org.telegram.tgnet.TLRPC.TL_urlAuthResultRequest
import org.telegram.tgnet.TLRPC.TL_user
import org.telegram.tgnet.TLRPC.TL_userContact_old2
import org.telegram.tgnet.TLRPC.TL_userRequest_old2
import org.telegram.tgnet.TLRPC.TL_webDocument
import org.telegram.tgnet.TLRPC.TL_webPagePending
import org.telegram.tgnet.TLRPC.TL_webPageUrlPending
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.tgnet.TLRPC.account_Password
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.MessageEntity
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_message
import org.telegram.tgnet.tlrpc.TL_messageEntityBold
import org.telegram.tgnet.tlrpc.TL_messageEntityCode
import org.telegram.tgnet.tlrpc.TL_messageEntityCustomEmoji
import org.telegram.tgnet.tlrpc.TL_messageEntityItalic
import org.telegram.tgnet.tlrpc.TL_messageEntityPre
import org.telegram.tgnet.tlrpc.TL_messageEntitySpoiler
import org.telegram.tgnet.tlrpc.TL_messageEntityTextUrl
import org.telegram.tgnet.tlrpc.TL_messageEntityUrl
import org.telegram.tgnet.tlrpc.TL_messages_editMessage
import org.telegram.tgnet.tlrpc.TL_messages_messages
import org.telegram.tgnet.tlrpc.TL_reactionCustomEmoji
import org.telegram.tgnet.tlrpc.TL_reactionEmoji
import org.telegram.tgnet.tlrpc.User
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
import org.telegram.ui.PaymentFormActivity
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
	private val delayedMessages = HashMap<String, ArrayList<DelayedMessage>>()
	private val unsentMessages = SparseArray<MessageObject>()
	private val sendingMessages = SparseArray<Message>()
	private val editingMessages = SparseArray<Message>()
	private val uploadMessages = SparseArray<Message>()
	private val sendingMessagesIdDialogs = LongSparseArray<Int>()
	private val uploadingMessagesIdDialogs = LongSparseArray<Int>()
	private val waitingForLocation = HashMap<String, MessageObject>()
	private val waitingForCallback = HashMap<String, Boolean>()
	private val waitingForVote = HashMap<String, ByteArray>()
	private val voteSendTime = LongSparseArray<Long>()
	private val importingHistoryFiles = HashMap<String, ImportingHistory>()
	private val importingHistoryMap = LongSparseArray<ImportingHistory?>()
	private val importingStickersFiles = HashMap<String, ImportingStickers>()
	private val importingStickersMap = HashMap<String, ImportingStickers?>()

	inner class ImportingHistory {
		var historyPath: String? = null
		var mediaPaths = ArrayList<Uri>()
		var uploadSet = HashSet<String>()
		private var uploadProgresses = HashMap<String, Float>()
		private var uploadSize = HashMap<String, Long>()
		var uploadMedia = ArrayList<String>()
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
			val req = TL_messages_initHistoryImport()
			req.file = inputFile
			req.media_count = mediaPaths.size
			req.peer = peer

			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					if (response is TL_messages_historyImport) {
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

				val error = TL_error()
				error.code = 400
				error.text = "IMPORT_UPLOAD_FAILED"

				notificationCenter.postNotificationName(NotificationCenter.historyImportProgressChanged, dialogId, TL_messages_initHistoryImport(), error)
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

			val req = TL_messages_uploadImportedMedia()
			req.peer = peer
			req.import_id = importId
			req.file_name = File(path).name

			val myMime = MimeTypeMap.getSingleton()
			var ext = "txt"
			val idx = req.file_name.lastIndexOf('.')

			if (idx != -1) {
				ext = req.file_name.substring(idx + 1).lowercase(Locale.getDefault())
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
				val inputMediaUploadedPhoto = TL_inputMediaUploadedPhoto()
				inputMediaUploadedPhoto.file = inputFile

				req.media = inputMediaUploadedPhoto
			}
			else {
				val inputMediaDocument = TL_inputMediaUploadedDocument()
				inputMediaDocument.file = inputFile
				inputMediaDocument.mime_type = mimeType

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
			val req = TL_messages_startHistoryImport()
			req.peer = peer
			req.import_id = importId

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

		var item: TL_inputStickerSetItem? = null

		fun uploadMedia(account: Int, inputFile: TLRPC.InputFile, onFinish: Runnable) {
			val req = TL_messages_uploadMedia()
			req.peer = TL_inputPeerSelf()
			req.media = TL_inputMediaUploadedDocument()
			req.media.file = inputFile
			req.media.mime_type = mimeType

			ConnectionsManager.getInstance(account).sendRequest(req, { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (response is TL_messageMediaDocument) {
						item = TL_inputStickerSetItem()
						item?.document = TL_inputDocument()
						item?.document?.id = response.document.id
						item?.document?.access_hash = response.document.access_hash
						item?.document?.file_reference = response.document.file_reference
						item?.emoji = if (emoji != null) emoji else ""

						mimeType = response.document.mime_type
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
		var uploadSet = HashMap<String, ImportingSticker>()
		private var uploadProgresses = HashMap<String, Float>()
		private var uploadSize = HashMap<String, Long>()
		var uploadMedia = ArrayList<ImportingSticker>()
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
			val req = TL_stickers_createStickerSet()
			req.user_id = TL_inputUserSelf()
			req.title = title
			req.short_name = shortName
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

					if (response is TL_messages_stickerSet) {
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
		var photo: TL_photo? = null

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
					is TL_messages_sendEncryptedMultiMedia -> {
						secretChatHelper.performSendEncryptedRequest(innerRequest, this)
					}

					is TL_messages_sendMultiMedia -> {
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
					obj.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
					notificationCenter.postNotificationName(NotificationCenter.messageSendError, obj.id)
					processSentMessage(obj.id)
					removeFromUploadingMessages(obj.id, scheduled)
				}

				delayedMessages.remove("group_$groupId")
			}
			else {
				messagesStorage.markMessageAsSendError(obj!!.messageOwner, obj!!.scheduled)
				obj?.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
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
		if (id == NotificationCenter.fileUploadProgressChanged) {
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
		else if (id == NotificationCenter.fileUploaded) {
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
						is TL_messages_sendMedia -> {
							media = sendRequest.media
						}

						is TL_messages_editMessage -> {
							media = sendRequest.media
						}

						is TL_messages_sendMultiMedia -> {
							media = message.extraHashMap?.get(location) as? InputMedia

							if (media == null) {
								media = sendRequest.multi_media?.firstOrNull()?.media
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

								if (media.thumb == null && message.photoSize != null && message.photoSize!!.location != null) {
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

								if (media.thumb == null && message.photoSize != null && message.photoSize!!.location != null) {
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
							if (media is TL_inputMediaUploadedDocument) {
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
					else if (encryptedFile != null && message.sendEncryptedRequest != null) {
						var decryptedMessage: TL_decryptedMessage? = null

						if (message.type == 4) {
							val req = message.sendEncryptedRequest as TL_messages_sendEncryptedMultiMedia?
							val inputEncryptedFile = message.extraHashMap!![location] as InputEncryptedFile?
							val index = req!!.files.indexOf(inputEncryptedFile)

							if (index >= 0) {
								req.files[index] = encryptedFile

								if (inputEncryptedFile!!.id == 1L) {
									message.photoSize = message.extraHashMap!![location + "_t"] as? PhotoSize
									stopVideoService(message.messageObjects!![index].messageOwner?.attachPath)
								}

								decryptedMessage = req.messages[index]
							}
						}
						else {
							decryptedMessage = message.sendEncryptedRequest as TL_decryptedMessage?
						}

						if (decryptedMessage != null) {
							if (decryptedMessage.media is TL_decryptedMessageMediaVideo || decryptedMessage.media is TL_decryptedMessageMediaPhoto || decryptedMessage.media is TL_decryptedMessageMediaDocument) {
								decryptedMessage.media.size = args[5] as Long
							}

							decryptedMessage.media.key = args[3] as ByteArray
							decryptedMessage.media.iv = args[4] as ByteArray

							if (message.type == 4) {
								uploadMultiMedia(message, null, encryptedFile, location)
							}
							else {
								secretChatHelper.performSendEncryptedRequest(decryptedMessage, message.obj!!.messageOwner, message.encryptedChat, encryptedFile, message.originalPath, message.obj)
							}
						}

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
		else if (id == NotificationCenter.fileUploadFailed) {
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
		else if (id == NotificationCenter.filePreparingStarted) {
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
		else if (id == NotificationCenter.fileNewChunkAvailable) {
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

									val messages = ArrayList<Message>()
									messages.add(obj.messageOwner!!)

									messagesStorage.putMessages(messages, false, true, false, 0, obj.scheduled)

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
		else if (id == NotificationCenter.filePreparingFailed) {
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
		else if (id == NotificationCenter.httpFileDidLoad) {
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

							if (document?.thumbs.isNullOrEmpty() || document.thumbs?.firstOrNull()?.location is TL_fileLocationUnavailable) {
								try {
									val bitmap = ImageLoader.loadBitmap(cacheFile.absolutePath, null, 90f, 90f, true)

									if (bitmap != null) {
										document?.thumbs?.clear()
										document?.thumbs?.add(ImageLoader.scaleAndSaveImage(bitmap, 90f, 90f, 55, message.sendEncryptedRequest != null))

										bitmap.recycle()
									}
								}
								catch (e: Exception) {
									document?.thumbs?.clear()
									FileLog.e(e)
								}
							}
							AndroidUtilities.runOnUIThread {
								message.httpLocation = null
								message.obj?.messageOwner?.attachPath = cacheFile.toString()

								if (!document?.thumbs.isNullOrEmpty()) {
									val photoSize = document.thumbs?.firstOrNull()

									if (photoSize !is TL_photoStrippedSize) {
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
		else if (id == NotificationCenter.fileLoaded) {
			val path = args[0] as String
			val arr = delayedMessages[path]

			if (arr != null) {
				for (a in arr.indices) {
					performSendDelayedMessage(arr[a])
				}

				delayedMessages.remove(path)
			}
		}
		else if (id == NotificationCenter.httpFileDidFailedLoad || id == NotificationCenter.fileLoadFailed) {
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

	private fun revertEditingMessageObject(`object`: MessageObject) {
		`object`.cancelEditing = true

		`object`.messageOwner?.media = `object`.previousMedia
		`object`.messageOwner?.message = `object`.previousMessage
		`object`.messageOwner?.entities = `object`.previousMessageEntities ?: arrayListOf()
		`object`.messageOwner?.attachPath = `object`.previousAttachPath
		`object`.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SENT

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
								val request = message.sendRequest as TL_messages_sendMultiMedia?
								request!!.multi_media.removeAt(index)
							}
							else {
								val request = message.sendEncryptedRequest as TL_messages_sendEncryptedMultiMedia?
								request!!.messages.removeAt(index)
								request.files.removeAt(index)
							}

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

									val messagesRes = TL_messages_messages()
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

		if (messageObject.messageOwner?.action is TL_messageEncryptedAction) {
			val encId = DialogObject.getEncryptedChatId(messageObject.dialogId)
			val encryptedChat = messagesController.getEncryptedChat(encId)

			if (encryptedChat == null) {
				messagesStorage.markMessageAsSendError(messageObject.messageOwner, messageObject.scheduled)
				messageObject.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
				notificationCenter.postNotificationName(NotificationCenter.messageSendError, messageObject.id)
				processSentMessage(messageObject.id)
				return false
			}

			if (messageObject.messageOwner?.random_id == 0L) {
				messageObject.messageOwner?.random_id = nextRandomId
			}

			when (messageObject.messageOwner?.action?.encryptedAction) {
				is TL_decryptedMessageActionSetMessageTTL -> {
					secretChatHelper.sendTTLMessage(encryptedChat, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionDeleteMessages -> {
					secretChatHelper.sendMessagesDeleteMessage(encryptedChat, null, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionFlushHistory -> {
					secretChatHelper.sendClearHistoryMessage(encryptedChat, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionNotifyLayer -> {
					secretChatHelper.sendNotifyLayerMessage(encryptedChat, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionReadMessages -> {
					secretChatHelper.sendMessagesReadMessage(encryptedChat, null, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionScreenshotMessages -> {
					secretChatHelper.sendScreenshotMessage(encryptedChat, null, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionTyping -> {
					// unused
				}

				is TL_decryptedMessageActionResend -> {
					secretChatHelper.sendResendMessage(encryptedChat, 0, 0, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionCommitKey -> {
					secretChatHelper.sendCommitKeyMessage(encryptedChat, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionAbortKey -> {
					secretChatHelper.sendAbortKeyMessage(encryptedChat, messageObject.messageOwner, 0)
				}

				is TL_decryptedMessageActionRequestKey -> {
					secretChatHelper.sendRequestKeyMessage(encryptedChat, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionAcceptKey -> {
					secretChatHelper.sendAcceptKeyMessage(encryptedChat, messageObject.messageOwner)
				}

				is TL_decryptedMessageActionNoop -> {
					secretChatHelper.sendNoopMessage(encryptedChat, messageObject.messageOwner)
				}
			}

			return true
		}
		else if (messageObject.messageOwner?.action is TL_messageActionScreenshotTaken) {
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
		val prevSize = unsentMessages.size()

		unsentMessages.remove(id)

		if (prevSize != 0 && unsentMessages.size() == 0) {
			checkUnsentMessages()
		}
	}

	private fun processForwardFromMyName(messageObject: MessageObject?, did: Long) {
		if (messageObject == null) {
			return
		}

		if (messageObject.messageOwner?.media != null && messageObject.messageOwner?.media !is TL_messageMediaEmpty && messageObject.messageOwner?.media !is TL_messageMediaWebPage && messageObject.messageOwner?.media !is TL_messageMediaGame && messageObject.messageOwner?.media !is TL_messageMediaInvoice) {
			var params: HashMap<String, String>? = null

			if (DialogObject.isEncryptedDialog(did) && messageObject.messageOwner?.peer_id != null && (messageObject.messageOwner?.media?.photo is TL_photo || messageObject.messageOwner?.media?.document is TL_document)) {
				params = HashMap()
				params["parentObject"] = "sent_" + messageObject.messageOwner?.peer_id?.channel_id + "_" + messageObject.id
			}

			if (messageObject.messageOwner?.media?.photo is TL_photo) {
				sendMessage(messageObject.messageOwner?.media?.photo as TL_photo, null, did, messageObject.replyMessageObject, null, messageObject.messageOwner?.message, messageObject.messageOwner?.entities, null, params, true, 0, messageObject.messageOwner?.media?.ttl_seconds ?: 0, messageObject, false, messageObject.isMediaSale, messageObject.mediaSaleHash)
			}
			else if (messageObject.messageOwner?.media?.document is TL_document) {
				sendMessage(messageObject.messageOwner?.media?.document as TL_document, null, messageObject.messageOwner?.attachPath, did, messageObject.replyMessageObject, null, messageObject.messageOwner?.message, messageObject.messageOwner?.entities, null, params, true, 0, messageObject.messageOwner?.media?.ttl_seconds ?: 0, messageObject, null, false, messageObject.isMediaSale, messageObject.mediaSaleHash)
			}
			else if (messageObject.messageOwner?.media is TL_messageMediaVenue || messageObject.messageOwner?.media is TL_messageMediaGeo) {
				sendMessage(messageObject.messageOwner?.media, did, messageObject.replyMessageObject, null, null, null, true, 0, messageObject.isMediaSale, messageObject.mediaSaleHash)
			}
			else if (messageObject.messageOwner?.media?.phone_number != null) {
				val user: User = TL_userContact_old2()
				// user.phone = messageObject.messageOwner.media.phone_number;
				user.first_name = messageObject.messageOwner?.media?.first_name
				user.last_name = messageObject.messageOwner?.media?.last_name
				user.id = messageObject.messageOwner?.media?.user_id ?: 0

				sendMessage(user, did, messageObject.replyMessageObject, null, null, null, true, 0, messageObject.isMediaSale, messageObject.mediaSaleHash)
			}
			else if (!DialogObject.isEncryptedDialog(did)) {
				val arrayList = ArrayList<MessageObject>()
				arrayList.add(messageObject)
				sendMessage(arrayList, did, forwardFromMyName = true, hideCaption = false, notify = true, scheduleDate = 0)
			}
		}
		else if (messageObject.messageOwner?.message != null) {
			var webPage: WebPage? = null

			if (messageObject.messageOwner?.media is TL_messageMediaWebPage) {
				webPage = messageObject.messageOwner?.media?.webpage
			}

			val entities: ArrayList<MessageEntity>?

			if (!messageObject.messageOwner?.entities.isNullOrEmpty()) {
				entities = ArrayList()

				for (a in messageObject.messageOwner?.entities!!.indices) {
					val entity = messageObject.messageOwner?.entities!![a]

					if (entity is TL_messageEntityBold || entity is TL_messageEntityItalic || entity is TL_messageEntityPre || entity is TL_messageEntityCode || entity is TL_messageEntityTextUrl || entity is TL_messageEntitySpoiler || entity is TL_messageEntityCustomEmoji) {
						entities.add(entity)
					}
				}
			}
			else {
				entities = null
			}

			sendMessage(messageObject.messageOwner?.message, did, messageObject.replyMessageObject, null, webPage, true, entities, null, null, true, 0, null, false, messageObject.isMediaSale, messageObject.mediaSaleHash)
		}
		else if (DialogObject.isEncryptedDialog(did)) {
			val arrayList = ArrayList<MessageObject>()
			arrayList.add(messageObject)
			sendMessage(arrayList, did, forwardFromMyName = true, hideCaption = false, notify = true, scheduleDate = 0)
		}
	}

	fun sendScreenshotMessage(user: User?, messageId: Int, resendMessage: Message?) {
		if (user == null || messageId == 0 || user.id == userConfig.getClientUserId()) {
			return
		}

		val req = TL_messages_sendScreenshotNotification()
		req.peer = TL_inputPeerUser()
		req.peer.access_hash = user.access_hash
		req.peer.user_id = user.id

		val message: Message

		if (resendMessage != null) {
			message = resendMessage

			req.reply_to_msg_id = messageId
			req.random_id = resendMessage.random_id
		}
		else {
			message = TL_messageService()
			message.random_id = nextRandomId
			message.dialog_id = user.id
			message.unread = true
			message.out = true
			message.id = userConfig.newMessageId
			message.local_id = message.id
			message.from_id = TL_peerUser()
			message.from_id?.user_id = userConfig.getClientUserId()
			message.flags = message.flags or 256
			message.flags = message.flags or 8
			message.reply_to = TL_messageReplyHeader()
			message.reply_to?.reply_to_msg_id = messageId
			message.peer_id = TL_peerUser()
			message.peer_id?.user_id = user.id
			message.date = connectionsManager.currentTime
			message.action = TL_messageActionScreenshotTaken()

			userConfig.saveConfig(false)
		}

		req.random_id = message.random_id

		val newMsgObj = MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = true)
		newMsgObj.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING
		newMsgObj.wasJustSent = true

		val objArr = ArrayList<MessageObject>()
		objArr.add(newMsgObj)

		messagesController.updateInterfaceWithMessages(message.dialog_id, objArr, false)
		notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)

		val arr = ArrayList<Message>()
		arr.add(message)

		messagesStorage.putMessages(arr, false, true, false, 0, false)

		performSendMessageRequest(req, newMsgObj, null, null, null, false)
	}

	fun sendSticker(document: TLRPC.Document?, query: String?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, parentObject: Any?, sendAnimationData: SendAnimationData?, notify: Boolean, scheduleDate: Int, updateStickersOrder: Boolean) {
		@Suppress("NAME_SHADOWING") var document = document ?: return

		if (DialogObject.isEncryptedDialog(peer)) {
			val encryptedId = DialogObject.getEncryptedChatId(peer)

			messagesController.getEncryptedChat(encryptedId) ?: return

			val newDocument = TL_document_layer82()
			newDocument.id = document.id
			newDocument.access_hash = document.access_hash
			newDocument.date = document.date
			newDocument.mime_type = document.mime_type
			newDocument.file_reference = document.file_reference

			if (newDocument.file_reference == null) {
				newDocument.file_reference = ByteArray(0)
			}

			newDocument.size = document.size
			newDocument.dc_id = document.dc_id
			newDocument.attributes = ArrayList(document.attributes)

			if (newDocument.mime_type == null) {
				newDocument.mime_type = ""
			}

			var thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)

			if (thumb is TL_photoSize || thumb is TL_photoSizeProgressive) {
				val file = FileLoader.getInstance(currentAccount).getPathToAttach(thumb, true)

				if (file.exists()) {
					try {
						// val len = file.length().toInt()
						val arr = ByteArray(file.length().toInt())

						val reader = RandomAccessFile(file, "r")
						reader.readFully(arr)

						val newThumb: PhotoSize = TL_photoCachedSize()

						val fileLocation = TL_fileLocation_layer82()
						fileLocation.dc_id = thumb.location.dc_id
						fileLocation.volume_id = thumb.location.volume_id
						fileLocation.local_id = thumb.location.local_id
						fileLocation.secret = thumb.location.secret

						newThumb.location = fileLocation
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
				thumb = TL_photoSizeEmpty()
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

				val docExt = when (document.mime_type) {
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
						ImageLoader.getInstance().putImageToCache(BitmapDrawable(ApplicationLoader.applicationContext.resources, bitmapFinal[0]), keyFinal[0], false)
					}

					sendMessage(document as TL_document, null, null, peer, replyToMsg, replyToTopMsg, null, null, null, null, notify, scheduleDate, 0, parentObject, sendAnimationData, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
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

			sendMessage(document as TL_document, null, null, peer, replyToMsg, replyToTopMsg, null, null, null, params, notify, scheduleDate, 0, parentObject, sendAnimationData, updateStickersOrder, false, null)
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
					canSendVoiceMessages = !userFull.voice_messages_forbidden
				}
			}
			else {
				chat = messagesController.getChat(-peer)

				if (ChatObject.isChannel(chat)) {
					isSignature = chat.signatures
					isChannel = !chat.megagroup

					if (isChannel && chat.has_link) {
						val chatFull = messagesController.getChatFull(chat.id)

						if (chatFull != null) {
							linkedToGroup = chatFull.linked_chat_id
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
			var randomIds = ArrayList<Long?>()
			var ids = ArrayList<Int?>()
			var messagesByRandomIds = LongSparseArray<Message?>()
			val inputPeer = messagesController.getInputPeer(peer)
			// val lastDialogId: Long = 0
			val toMyself = peer == myId
			// var lastGroupedId = 0L

			for (a in messages.indices) {
				val msgObj = messages[a]

				if (msgObj.id <= 0 || msgObj.needDrawBluredPreview()) {
					if (msgObj.type == MessageObject.TYPE_COMMON && !TextUtils.isEmpty(msgObj.messageText)) {
						val webPage = msgObj.messageOwner?.media?.webpage
						sendMessage(msgObj.messageText.toString(), peer, null, null, webPage, webPage != null, msgObj.messageOwner?.entities, null, null, notify, scheduleDate, null, false, msgObj.isMediaSale, msgObj.mediaSaleHash)
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
				else if (!canSendMedia && (msgObj.messageOwner?.media is TL_messageMediaPhoto || msgObj.messageOwner?.media is TL_messageMediaDocument) && !mediaIsSticker) {
					if (sendResult == 0) {
						sendResult = if (ChatObject.isActionBannedByDefault(chat, ChatObject.ACTION_SEND_MEDIA)) 5 else 2
					}

					continue
				}
				else if (!canSendPolls && msgObj.messageOwner?.media is TL_messageMediaPoll) {
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

				val newMsg: Message = TL_message()

				if (!forwardFromMyName) {
					val forwardFromSaved = msgObj.dialogId == myId && msgObj.isFromUser && msgObj.messageOwner?.from_id?.user_id == myId

					if (msgObj.isForwarded) {
						newMsg.fwd_from = TL_messageFwdHeader()

						if (msgObj.messageOwner!!.fwd_from!!.flags and 1 != 0) {
							newMsg.fwd_from?.flags = newMsg.fwd_from!!.flags or 1
							newMsg.fwd_from!!.from_id = msgObj.messageOwner!!.fwd_from!!.from_id
						}

						if (msgObj.messageOwner!!.fwd_from!!.flags and 32 != 0) {
							newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 32
							newMsg.fwd_from!!.from_name = msgObj.messageOwner!!.fwd_from!!.from_name
						}

						if (msgObj.messageOwner!!.fwd_from!!.flags and 4 != 0) {
							newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 4
							newMsg.fwd_from!!.channel_post = msgObj.messageOwner!!.fwd_from!!.channel_post
						}

						if (msgObj.messageOwner!!.fwd_from!!.flags and 8 != 0) {
							newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 8
							newMsg.fwd_from!!.post_author = msgObj.messageOwner!!.fwd_from!!.post_author
						}

						if ((peer == myId || isChannel) && msgObj.messageOwner!!.fwd_from!!.flags and 16 != 0 && !isReplyUser(msgObj.dialogId)) {
							newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 16
							newMsg.fwd_from!!.saved_from_peer = msgObj.messageOwner!!.fwd_from!!.saved_from_peer
							newMsg.fwd_from!!.saved_from_msg_id = msgObj.messageOwner!!.fwd_from!!.saved_from_msg_id
						}

						newMsg.fwd_from!!.date = msgObj.messageOwner!!.fwd_from!!.date
						newMsg.flags = TLRPC.MESSAGE_FLAG_FWD
					}
					else if (!forwardFromSaved) { //if (!toMyself || !msgObj.isOutOwner())
						val fromId = msgObj.fromChatId

						newMsg.fwd_from = TL_messageFwdHeader()
						newMsg.fwd_from!!.channel_post = msgObj.id
						newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 4

						if (msgObj.isFromUser) {
							newMsg.fwd_from!!.from_id = msgObj.messageOwner!!.from_id
							newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 1
						}
						else {
							newMsg.fwd_from!!.from_id = TL_peerChannel()
							newMsg.fwd_from!!.from_id.channel_id = msgObj.messageOwner!!.peer_id!!.channel_id
							newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 1

							if (msgObj.messageOwner!!.post && fromId > 0) {
								newMsg.fwd_from!!.from_id = if (msgObj.messageOwner!!.from_id != null) msgObj.messageOwner!!.from_id else msgObj.messageOwner!!.peer_id
							}
						}

						if (msgObj.messageOwner?.post_author != null) {
							// newMsg.fwd_from.post_author = msgObj.messageOwner.post_author;
							// newMsg.fwd_from.flags |= 8;
						}
						else if (!msgObj.isOutOwner && fromId > 0 && msgObj.messageOwner!!.post) {
							val signUser = messagesController.getUser(fromId)

							if (signUser != null) {
								newMsg.fwd_from!!.post_author = ContactsController.formatName(signUser.first_name, signUser.last_name)
								newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 8
							}
						}

						newMsg.date = msgObj.messageOwner!!.date
						newMsg.flags = TLRPC.MESSAGE_FLAG_FWD
					}

					if (peer == myId && newMsg.fwd_from != null) {
						newMsg.fwd_from!!.flags = newMsg.fwd_from!!.flags or 16
						newMsg.fwd_from!!.saved_from_msg_id = msgObj.id
						newMsg.fwd_from!!.saved_from_peer = msgObj.messageOwner!!.peer_id

						if (newMsg.fwd_from!!.saved_from_peer.user_id == myId) {
							newMsg.fwd_from!!.saved_from_peer.user_id = msgObj.dialogId
						}
					}
				}

				newMsg.params = HashMap()
				newMsg.params!!["fwd_id"] = "" + msgObj.id
				newMsg.params!!["fwd_peer"] = "" + msgObj.dialogId

				if (msgObj.messageOwner!!.restriction_reason.isNotEmpty()) {
					newMsg.restriction_reason = msgObj.messageOwner!!.restriction_reason
					newMsg.flags = newMsg.flags or 4194304
				}

				if (!canSendPreview && msgObj.messageOwner?.media is TL_messageMediaWebPage) {
					newMsg.media = TL_messageMediaEmpty()
				}
				else {
					newMsg.media = msgObj.messageOwner?.media
				}

				if (newMsg.media != null) {
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
				}

				if (msgObj.messageOwner?.via_bot_id != 0L) {
					newMsg.via_bot_id = msgObj.messageOwner!!.via_bot_id
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_BOT_ID
				}

				if (linkedToGroup != 0L) {
					newMsg.replies = TL_messageReplies()
					newMsg.replies!!.comments = true
					newMsg.replies!!.channel_id = linkedToGroup
					newMsg.replies!!.flags = newMsg.replies!!.flags or 1
					newMsg.flags = newMsg.flags or 8388608
				}

				if (!hideCaption || newMsg.media == null) {
					newMsg.message = msgObj.messageOwner?.message
				}

				if (newMsg.message == null) {
					newMsg.message = ""
				}

				newMsg.fwd_msg_id = msgObj.id
				newMsg.attachPath = msgObj.messageOwner?.attachPath
				newMsg.entities = msgObj.messageOwner?.entities ?: arrayListOf()

				if (msgObj.messageOwner?.reply_markup is TL_replyInlineMarkup) {
					newMsg.reply_markup = TL_replyInlineMarkup()

					var dropMarkup = false
					var b = 0
					val n = msgObj.messageOwner?.reply_markup?.rows?.size ?: 0

					while (b < n) {
						val oldRow = msgObj.messageOwner!!.reply_markup!!.rows[b]
						var newRow: TL_keyboardButtonRow? = null
						var c = 0
						val n2 = oldRow.buttons.size

						while (c < n2) {
							var button = oldRow.buttons[c]

							if (button is TL_keyboardButtonUrlAuth || button is TL_keyboardButtonUrl || button is TL_keyboardButtonSwitchInline || button is TL_keyboardButtonBuy) {
								if (button is TL_keyboardButtonUrlAuth) {
									val auth = TL_keyboardButtonUrlAuth()
									auth.flags = button.flags

									if (button.fwd_text != null) {
										auth.fwd_text = button.fwd_text
										auth.text = auth.fwd_text
									}
									else {
										auth.text = button.text
									}

									auth.url = button.url
									auth.button_id = button.button_id
									button = auth
								}

								if (newRow == null) {
									newRow = TL_keyboardButtonRow()
									newMsg.reply_markup?.rows?.add(newRow)
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
						msgObj.messageOwner?.reply_markup = null
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
				newMsg.local_id = newMsg.id
				newMsg.out = true

				if (msgObj.messageOwner!!.realGroupId != 0L) {
					var gId = groupsMap[msgObj.messageOwner!!.realGroupId]

					if (gId == null) {
						gId = Utilities.random.nextLong()
						groupsMap.put(msgObj.messageOwner!!.realGroupId, gId)
					}

					newMsg.groupId = gId
					newMsg.flags = newMsg.flags or 131072
				}

				if (peerId.channel_id != 0L && isChannel) {
					if (isSignature) {
						newMsg.from_id = TL_peerUser()
						newMsg.from_id?.user_id = myId
					}
					else {
						newMsg.from_id = peerId
					}

					newMsg.post = true
				}
				else {
					val fromPeerId = ChatObject.getSendAsPeerId(chat, messagesController.getChatFull(-peer), true)

					if (fromPeerId == myId) {
						newMsg.from_id = TL_peerUser()
						newMsg.from_id?.user_id = myId
						newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_FROM_ID
					}
					else {
						newMsg.from_id = messagesController.getPeer(fromPeerId)

						if (rank != null) {
							newMsg.post_author = rank
							newMsg.flags = newMsg.flags or 65536
						}
					}
				}

				if (newMsg.random_id == 0L) {
					newMsg.random_id = nextRandomId
				}

				randomIds.add(newMsg.random_id)

				messagesByRandomIds.put(newMsg.random_id, newMsg)

				ids.add(newMsg.fwd_msg_id)

				newMsg.date = if (scheduleDate != 0) scheduleDate else connectionsManager.currentTime

				if (inputPeer is TL_inputPeerChannel && isChannel) {
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

				newMsg.dialog_id = peer
				newMsg.peer_id = peerId

				if (MessageObject.isVoiceMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
					if (inputPeer is TL_inputPeerChannel && msgObj.channelId != 0L) {
						newMsg.media_unread = msgObj.isContentUnread
					}
					else {
						newMsg.media_unread = true
					}
				}

				val newMsgObj = MessageObject(currentAccount, newMsg, generateLayout = true, checkMediaExists = true)
				newMsgObj.scheduled = scheduleDate != 0
				newMsgObj.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING
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

				FileLog.d("forward message user_id = " + inputPeer.user_id + " chat_id = " + inputPeer.chat_id + " channel_id = " + inputPeer.channel_id + " access_hash = " + inputPeer.access_hash)

				if (arr.size == 100 || a == messages.size - 1 || a != messages.size - 1 && messages[a + 1].dialogId != msgObj.dialogId) {
					messagesStorage.putMessages(ArrayList(arr), false, true, false, 0, scheduleDate != 0)
					messagesController.updateInterfaceWithMessages(peer, objArr, scheduleDate != 0)
					notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
					userConfig.saveConfig(false)

					val req = TL_messages_forwardMessages()
					req.to_peer = inputPeer
					req.silent = !notify || MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_$peer", false)

					if (scheduleDate != 0) {
						req.schedule_date = scheduleDate
						req.flags = req.flags or 1024
					}

					if (msgObj.messageOwner?.peer_id is TL_peerChannel) {
						val channel = messagesController.getChat(msgObj.messageOwner?.peer_id?.channel_id)

						req.from_peer = TL_inputPeerChannel()
						req.from_peer.channel_id = msgObj.messageOwner?.peer_id?.channel_id ?: 0L

						if (channel != null) {
							req.from_peer.access_hash = channel.access_hash
						}
					}
					else {
						req.from_peer = TL_inputPeerEmpty()
					}

					req.random_id = randomIds
					req.id = ids
					req.drop_author = forwardFromMyName
					req.drop_media_captions = hideCaption
					req.with_my_score = messages.size == 1 && messages[0].messageOwner!!.with_my_score

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

									if (update is TL_updateMessageID) {
										newMessagesByIds.put(update.id, update.random_id)
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

								if (update is TL_updateNewMessage || update is TL_updateNewChannelMessage || update is TL_updateNewScheduledMessage) {
									var currentSchedule = scheduleDate != 0

									updates.updates.removeAt(a1)

									a1--

									val message: Message

									when (update) {
										is TL_updateNewMessage -> {
											message = update.message
											messagesController.processNewDifferenceParams(-1, update.pts, -1, update.pts_count)
										}

										is TL_updateNewScheduledMessage -> {
											message = update.message
										}

										else -> {
											val updateNewChannelMessage = update as TL_updateNewChannelMessage
											message = updateNewChannelMessage.message
											messagesController.processNewChannelDifferenceParams(updateNewChannelMessage.pts, updateNewChannelMessage.pts_count, message.peer_id?.channel_id ?: 0L)
										}
									}

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
										message.media_unread = false
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

										msgObj1.messageOwner?.post_author = message.post_author

										if (message.flags and 33554432 != 0) {
											msgObj1.messageOwner?.ttl_period = message.ttl_period
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

												messagesController.deleteMessages(messageIds, null, null, newMsgObj1.dialog_id, forAll = false, scheduled = true)

												messagesStorage.storageQueue.postRunnable {
													messagesStorage.putMessages(sentMessages, true, false, false, 0, false)

													AndroidUtilities.runOnUIThread {
														messagesController.updateInterfaceWithMessages(newMsgObj1.dialog_id, listOf(MessageObject(msgObj.currentAccount, msgObj.messageOwner!!, generateLayout = true, checkMediaExists = true)), false)
														mediaDataController.increasePeerRating(newMsgObj1.dialog_id)
														processSentMessage(oldId)
														removeFromSendingMessages(oldId, scheduleDate != 0)
													}
												}
											}
										}
										else {
											messagesStorage.storageQueue.postRunnable {
												messagesStorage.updateMessageStateAndId(newMsgObj1.random_id, MessageObject.getPeerId(peerId), oldId, newMsgObj1.id, 0, false, if (scheduleDate != 0) 1 else 0)
												messagesStorage.putMessages(sentMessages, true, false, false, 0, scheduleDate != 0)

												AndroidUtilities.runOnUIThread {
													newMsgObj1.send_state = MessageObject.MESSAGE_SEND_STATE_SENT
													mediaDataController.increasePeerRating(peer)
													notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, message.id, message, peer, 0L, existFlags, scheduleDate != 0)
													processSentMessage(oldId)
													removeFromSendingMessages(oldId, scheduleDate != 0)
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
								newMsgObj1.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
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
			val userId = encryptedChat?.user_id ?: 0

			if (DialogObject.isUserDialog(userId)) {
				val sendToUser = messagesController.getUser(userId)

				if (sendToUser != null) {
					val userFull = messagesController.getUserFull(userId)

					if (userFull != null) {
						canSendVoiceMessages = !userFull.voice_messages_forbidden
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
			val media = TL_messageMediaEmpty()
			media.serializeToStream(data)
		}
		else {
			message.media?.serializeToStream(data)
		}

		data.writeString(if (message.message != null) message.message else "")
		data.writeString(if (message.attachPath != null) message.attachPath else "")

		var count: Int

		data.writeInt32(message.entities.size.also { count = it })

		for (a in 0 until count) {
			message.entities[a].serializeToStream(data)
		}
	}

	fun editMessage(messageObject: MessageObject?, photo: TL_photo?, videoEditedInfo: VideoEditedInfo?, document: TL_document?, path: String?, params: HashMap<String, String>?, retry: Boolean, parentObject: Any?) {
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
			params = HashMap()
		}

		val newMsg = messageObject.messageOwner
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
					is TL_messageMediaWebPage, null, is TL_messageMediaEmpty -> {
						type = 1
					}

					is TL_messageMediaPhoto -> {
						photo = messageObject.messageOwner?.media?.photo as? TL_photo
						type = 2
					}

					is TL_messageMediaDocument -> {
						document = messageObject.messageOwner?.media?.document as? TL_document

						type = if (MessageObject.isVideoDocument(document) || videoEditedInfo != null) {
							3
						}
						else {
							7
						}

						videoEditedInfo = messageObject.videoEditedInfo
					}
				}

				params = newMsg?.params

				if (parentObject == null && params != null && params.containsKey("parentObject")) {
					parentObject = params["parentObject"]
				}

				messageObject.editingMessage = newMsg?.message
				messageObject.editingMessageEntities = newMsg?.entities
				path = newMsg?.attachPath
			}
			else {
				messageObject.previousMedia = newMsg?.media
				messageObject.previousMessage = newMsg?.message
				messageObject.previousMessageEntities = newMsg?.entities
				messageObject.previousAttachPath = newMsg?.attachPath

//				var media = newMsg.media
//
//				if (media == null) {
//					media = TL_messageMediaEmpty()
//				}

				val serializedDataCalc = SerializedData(true)

				writePreviousMessageData(newMsg, serializedDataCalc)

				val prevMessageData = SerializedData(serializedDataCalc.length())
				writePreviousMessageData(newMsg, prevMessageData)

				params["prevMedia"] = Base64.encodeToString(prevMessageData.toByteArray(), Base64.DEFAULT)

				prevMessageData.cleanup()

				if (photo != null) {
					newMsg?.media = TL_messageMediaPhoto()
					newMsg?.media?.flags = newMsg.media!!.flags or 3
					newMsg?.media?.photo = photo

					type = 2

					if (!path.isNullOrEmpty() && path.startsWith("http")) {
						newMsg?.attachPath = path
					}
					else {
						val location1 = photo.sizes[photo.sizes.size - 1].location
						newMsg?.attachPath = FileLoader.getInstance(currentAccount).getPathToAttach(location1, true).toString()
					}
				}
				else if (document != null) {
					newMsg?.media = TL_messageMediaDocument()
					newMsg?.media?.flags = newMsg.media!!.flags or 3
					newMsg?.media?.document = document

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

					newMsg?.attachPath = path
				}
				else {
					type = 1
				}

				newMsg?.params = params
				newMsg?.send_state = MessageObject.MESSAGE_SEND_STATE_EDITING
			}

			if (newMsg?.attachPath == null) {
				newMsg?.attachPath = ""
			}

			newMsg?.local_id = 0

			if (messageObject.type == MessageObject.TYPE_VIDEO || videoEditedInfo != null || messageObject.type == MessageObject.TYPE_VOICE && !newMsg?.attachPath.isNullOrEmpty()) {
				messageObject.attachPathExists = true
			}

			if (messageObject.videoEditedInfo != null && videoEditedInfo == null) {
				videoEditedInfo = messageObject.videoEditedInfo
			}

			if (!retry) {
				if (messageObject.editingMessage != null) {
					val oldMessage = newMsg?.message

					newMsg?.message = messageObject.editingMessage.toString()

					messageObject.caption = null

					if (type == 1) {
						if (messageObject.editingMessageEntities != null) {
							newMsg?.entities = ArrayList(messageObject.editingMessageEntities!!)
							newMsg?.flags = newMsg.flags or 128
						}
						else if (!TextUtils.equals(oldMessage, newMsg?.message)) {
							newMsg?.flags = newMsg.flags and 128.inv()
						}
					}
					else {
						if (messageObject.editingMessageEntities != null) {
							newMsg?.entities = ArrayList(messageObject.editingMessageEntities!!)
							newMsg?.flags = newMsg.flags or 128
						}
						else {
							val message = arrayOf(messageObject.editingMessage)
							val entities = mediaDataController.getEntities(message, supportsSendingNewEntities)

							if (!entities.isNullOrEmpty()) {
								newMsg?.entities = ArrayList(entities)
								newMsg?.flags = newMsg.flags or 128
							}
							else if (!TextUtils.equals(oldMessage, newMsg?.message)) {
								newMsg?.flags = newMsg.flags and 128.inv()
							}
						}

						messageObject.generateCaption()
					}
				}

				messagesStorage.putMessages(listOf(newMsg), false, true, false, 0, messageObject.scheduled)

				messageObject.type = -1
				messageObject.setType()

				if (type == 1) {
					if (messageObject.messageOwner?.media is TL_messageMediaPhoto || messageObject.messageOwner?.media is TL_messageMediaDocument) {
						messageObject.generateCaption()
					}
					else {
						messageObject.resetLayout()
						messageObject.checkLayout()
					}
				}

				messageObject.createMessageSendInfo()

				val arrayList = ArrayList<MessageObject>()
				arrayList.add(messageObject)

				notificationCenter.postNotificationName(NotificationCenter.replaceMessagesObjects, peer, arrayList)
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
					val uploadedPhoto = TL_inputMediaUploadedPhoto()

					if (params != null) {
						val masks = params["masks"]

						if (masks != null) {
							val serializedData = SerializedData(Utilities.hexToBytes(masks))
							val count = serializedData.readInt32(false)

							for (a in 0 until count) {
								uploadedPhoto.stickers.add(InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false))
							}

							uploadedPhoto.flags = uploadedPhoto.flags or 1
							serializedData.cleanup()
						}
					}

					if (photo!!.access_hash == 0L) {
						inputMedia = uploadedPhoto
						performMediaUpload = true
					}
					else {
						val media = TL_inputMediaPhoto()
						media.id = TL_inputPhoto()
						media.id.id = photo.id
						media.id.access_hash = photo.access_hash
						media.id.file_reference = photo.file_reference

						if (media.id.file_reference == null) {
							media.id.file_reference = ByteArray(0)
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
				else if (type == 3) {
					val uploadedDocument = TL_inputMediaUploadedDocument()

					if (params != null) {
						val masks = params["masks"]

						if (masks != null) {
							val serializedData = SerializedData(Utilities.hexToBytes(masks))
							val count = serializedData.readInt32(false)

							for (a in 0 until count) {
								uploadedDocument.stickers.add(InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false))
							}

							uploadedDocument.flags = uploadedDocument.flags or 1

							serializedData.cleanup()
						}
					}

					uploadedDocument.mime_type = document!!.mime_type
					uploadedDocument.attributes = document.attributes

					if (!messageObject.isGif && (videoEditedInfo == null || !videoEditedInfo.muted)) {
						uploadedDocument.nosound_video = true
						FileLog.d("nosound_video = true")
					}

					if (document.access_hash == 0L) {
						inputMedia = uploadedDocument
						performMediaUpload = true
					}
					else {
						val media = TL_inputMediaDocument()
						media.id = TL_inputDocument()
						media.id.id = document.id
						media.id.access_hash = document.access_hash
						media.id.file_reference = document.file_reference

						if (media.id.file_reference == null) {
							media.id.file_reference = ByteArray(0)
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

						if (photoSize !is TL_photoStrippedSize) {
							delayedMessage.photoSize = photoSize
							delayedMessage.locationParent = document
						}
					}

					delayedMessage.videoEditedInfo = videoEditedInfo
				}
				else if (type == 7) {
					val http = false

					val uploadedDocument: InputMedia = TL_inputMediaUploadedDocument()
					uploadedDocument.mime_type = document!!.mime_type
					uploadedDocument.attributes = document.attributes

					if (document.access_hash == 0L) {
						inputMedia = uploadedDocument
						performMediaUpload = uploadedDocument is TL_inputMediaUploadedDocument
					}
					else {
						val media = TL_inputMediaDocument()
						media.id = TL_inputDocument()
						media.id.id = document.id
						media.id.access_hash = document.access_hash
						media.id.file_reference = document.file_reference

						if (media.id.file_reference == null) {
							media.id.file_reference = ByteArray(0)
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

							if (photoSize !is TL_photoStrippedSize) {
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

				val request = TL_messages_editMessage()
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
						request.entities = messageObject.editingMessageEntities!!
						request.flags = request.flags or 8
					}
					else {
						val message = arrayOf(messageObject.editingMessage)
						val entities = mediaDataController.getEntities(message, supportsSendingNewEntities)

						if (!entities.isNullOrEmpty()) {
							request.entities = entities
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

		val req = TL_messages_editMessage()
		req.peer = messagesController.getInputPeer(messageObject.dialogId)

		if (message != null) {
			req.message = message
			req.flags = req.flags or 2048
			req.noWebpage = !searchLinks
		}

		req.id = messageObject.id

		if (entities != null) {
			req.entities = entities
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
		val mediaGeo = TL_messageMediaGeo()
		mediaGeo.geo = TL_geoPoint()
		mediaGeo.geo.lat = AndroidUtilities.fixLocationCoordinate(location.latitude)
		mediaGeo.geo._long = AndroidUtilities.fixLocationCoordinate(location.longitude)

		for ((_, messageObject) in waitingForLocation) {
			sendMessage(mediaGeo, messageObject.dialogId, messageObject, null, null, null, true, 0, false, null)
		}
	}

	fun sendCurrentLocation(messageObject: MessageObject?, button: KeyboardButton?) {
		if (messageObject == null || button == null) {
			return
		}

		val key = messageObject.dialogId.toString() + "_" + messageObject.id + "_" + Utilities.bytesToHex(button.data) + "_" + if (button is TL_keyboardButtonGame) "1" else "0"

		waitingForLocation[key] = messageObject

		locationProvider.start()
	}

	fun isSendingCurrentLocation(messageObject: MessageObject?, button: KeyboardButton?): Boolean {
		if (messageObject == null || button == null) {
			return false
		}

		val key = messageObject.dialogId.toString() + "_" + messageObject.id + "_" + Utilities.bytesToHex(button.data) + "_" + if (button is TL_keyboardButtonGame) "1" else "0"

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

			val req = TL_messages_getBotCallbackAnswer()
			req.peer = messagesController.getInputPeer(dialogId)
			req.msg_id = msgId
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

	fun sendVote(messageObject: MessageObject?, answers: ArrayList<TL_pollAnswer>?, finishRunnable: Runnable?): Int {
		if (messageObject == null) {
			return 0
		}

		val key = "poll_" + messageObject.pollId

		if (waitingForCallback.containsKey(key)) {
			return 0
		}

		val req = TL_messages_sendVote()
		req.msg_id = messageObject.id
		req.peer = messagesController.getInputPeer(messageObject.dialogId)

		val options: ByteArray

		if (answers != null) {
			options = ByteArray(answers.size)

			for (a in answers.indices) {
				val answer = answers[a]
				req.options.add(answer.option)
				options[a] = answer.option[0]
			}
		}
		else {
			options = ByteArray(0)
		}

		waitingForVote[key] = options

		return connectionsManager.sendRequest(req) { response: TLObject?, error: TL_error? ->
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
		return voteSendTime[pollId, 0L]
	}

	fun like(message: Message, callback: Runnable?) {
		val channelId = message.peer_id?.channel_id ?: run {
			callback?.run()
			return
		}

		val req = ElloRpc.likeMessage(messageId = message.id, userId = UserConfig.getInstance(currentAccount).clientUserId, channelId = channelId)
		val likesCount = message.likes

		connectionsManager.sendRequest(req) { response, error ->
			var ok = false

			if (response is TLRPC.TL_biz_dataRaw) {
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
					message.is_liked = true

					messagesStorage.putMessages(java.util.ArrayList(listOf(message)), true, true, true, 0, false)

					callback?.run()
				}
			}
		}
	}

	fun dislike(message: Message, callback: Runnable?) {
		val channelId = message.peer_id?.channel_id ?: run {
			callback?.run()
			return
		}

		val req = ElloRpc.revokeLikeFromMessage(messageId = message.id, userId = UserConfig.getInstance(currentAccount).clientUserId, channelId = channelId)
		val likesCount = message.likes

		connectionsManager.sendRequest(req) { response, error ->
			var ok = false

			if (response is TLRPC.TL_biz_dataRaw) {
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
					message.is_liked = false

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

		val req = TL_messages_sendReaction()

		if (messageObject.messageOwner!!.isThreadMessage && messageObject.messageOwner!!.fwd_from != null) {
			req.peer = messagesController.getInputPeer(messageObject.fromChatId)
			req.msg_id = messageObject.messageOwner?.fwd_from?.saved_from_msg_id ?: 0
		}
		else {
			req.peer = messagesController.getInputPeer(messageObject.dialogId)
			req.msg_id = messageObject.id
		}

		req.add_to_recent = addToRecent

		if (addToRecent && addedReaction != null) {
			MediaDataController.getInstance(currentAccount).recentReactions.add(0, ReactionsUtils.toTLReaction(addedReaction))
		}

		if (!visibleReactions.isNullOrEmpty()) {
			for (i in visibleReactions.indices) {
				val visibleReaction = visibleReactions[i]

				if (visibleReaction.documentId != 0L) {
					val reactionCustomEmoji = TL_reactionCustomEmoji()
					reactionCustomEmoji.documentId = visibleReaction.documentId
					req.reaction.add(reactionCustomEmoji)
					req.flags = req.flags or 1
				}
				else if (visibleReaction.emojicon != null) {
					val defaultReaction = TL_reactionEmoji()
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
		val req = TL_messages_requestUrlAuth()
		req.url = url
		req.flags = req.flags or 4

		connectionsManager.sendRequest(req, { response, _ ->
			if (response != null) {
				when (response) {
					is TL_urlAuthResultRequest -> {
						parentFragment.showRequestUrlAlert(response, req, url, ask)
					}

					is TL_urlAuthResultAccepted -> {
						AlertsCreator.showOpenUrlAlert(parentFragment, response.url, punycode = false, ask = false)
					}

					is TL_urlAuthResultDefault -> {
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
			is TL_keyboardButtonUrlAuth -> {
				cacheFinal = false
				type = 3
			}

			is TL_keyboardButtonGame -> {
				cacheFinal = false
				type = 1
			}

			else -> {
				cacheFinal = cache
				type = if (button is TL_keyboardButtonBuy) {
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

					if (messageObject.messageOwner?.via_bot_id != 0L) {
						uid = messageObject.messageOwner!!.via_bot_id
					}

					var name: String? = null

					if (uid > 0) {
						val user = messagesController.getUser(uid)

						if (user != null) {
							name = ContactsController.formatName(user.first_name, user.last_name)
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

					if (button is TL_keyboardButtonUrlAuth) {
						when (response) {
							is TL_urlAuthResultRequest -> {
								parentFragment.showRequestUrlAlert(response, request[0] as TL_messages_requestUrlAuth, button.url, false)
							}

							is TL_urlAuthResultAccepted -> {
								AlertsCreator.showOpenUrlAlert(parentFragment, response.url, punycode = false, ask = false)
							}

							is TL_urlAuthResultDefault -> {
								AlertsCreator.showOpenUrlAlert(parentFragment, button.url, punycode = false, ask = true)
							}
						}
					}
					else if (button is TL_keyboardButtonBuy) {
						if (response is TL_payments_paymentForm) {
							messagesController.putUsers(response.users, false)
							parentFragment.presentFragment(PaymentFormActivity(response, messageObject, parentFragment))
						}
						else if (response is TL_payments_paymentReceipt) {
							parentFragment.presentFragment(PaymentFormActivity(response as TL_payments_paymentReceipt?))
						}
					}
					else {
						val res = response as TL_messages_botCallbackAnswer

						if (!cacheFinal && res.cache_time != 0 && !button.requires_password) {
							messagesStorage.saveBotCache(key, res)
						}

						if (res.message != null) {
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
						else if (res.url != null) {
							if (parentFragment.parentActivity == null) {
								return@runOnUIThread
							}

							val user = messagesController.getUser(uid)
							val verified = user != null && user.verified

							if (button is TL_keyboardButtonGame) {
								val game = (if (messageObject.messageOwner?.media is TL_messageMediaGame) messageObject.messageOwner?.media?.game else null) ?: return@runOnUIThread
								parentFragment.showOpenGameAlert(game, messageObject, res.url, !verified && MessagesController.getNotificationsSettings(currentAccount).getBoolean("askgame_$uid", true), uid)
							}
							else {
								AlertsCreator.showOpenUrlAlert(parentFragment, res.url, punycode = false, ask = false)
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
						val getPasswordReq = TL_account_getPassword()

						ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, { response2, error2 ->
							AndroidUtilities.runOnUIThread {
								if (error2 == null) {
									val currentPassword = response2 as? account_Password
									passwordFragment!!.setCurrentPasswordInfo(null, currentPassword)
									TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword)
									sendCallback(cache, messageObject, button, passwordFragment.newSrpPassword, passwordFragment, parentFragment)
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
			if (button is TL_keyboardButtonUrlAuth) {
				val req = TL_messages_requestUrlAuth()
				req.peer = messagesController.getInputPeer(messageObject.dialogId)
				req.msg_id = messageObject.id
				req.button_id = button.button_id
				req.flags = req.flags or 2

				request[0] = req

				connectionsManager.sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors)
			}
			else if (button is TL_keyboardButtonBuy) {
				if (messageObject.messageOwner!!.media!!.flags and 4 == 0) {
					val req = TL_payments_getPaymentForm()

					val inputInvoice = TL_inputInvoiceMessage()
					inputInvoice.msg_id = messageObject.id
					inputInvoice.peer = messagesController.getInputPeer(messageObject.messageOwner?.peer_id)

					req.invoice = inputInvoice

					try {
						val jsonObject = JSONObject()
						jsonObject.put("bg_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.background, null))
						jsonObject.put("text_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.text, null))
						jsonObject.put("hint_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.hint, null))
						jsonObject.put("link_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.brand, null))
						jsonObject.put("button_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.brand, null))
						jsonObject.put("button_text_color", ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.white, null))

						req.theme_params = TL_dataJSON()
						req.theme_params.data = jsonObject.toString()
						req.flags = req.flags or 1
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					connectionsManager.sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors)
				}
				else {
					val req = TL_payments_getPaymentReceipt()
					req.msg_id = messageObject.messageOwner!!.media!!.receipt_msg_id
					req.peer = messagesController.getInputPeer(messageObject.messageOwner?.peer_id)

					connectionsManager.sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors)
				}
			}
			else {
				val req = TL_messages_getBotCallbackAnswer()
				req.peer = messagesController.getInputPeer(messageObject.dialogId)
				req.msg_id = messageObject.id
				req.game = button is TL_keyboardButtonGame

				if (button.requires_password) {
					req.password = srp ?: TL_inputCheckPasswordEmpty()
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
			is TL_keyboardButtonUrlAuth -> 3
			is TL_keyboardButtonGame -> 1
			is TL_keyboardButtonBuy -> 2
			else -> 0
		}

		val key = messageObject.dialogId.toString() + "_" + messageObject.id + "_" + Utilities.bytesToHex(button.data) + "_" + type

		return waitingForCallback.containsKey(key)
	}

	fun sendGame(peer: InputPeer?, game: TL_inputMediaGame?, randomId: Long, taskId: Long) {
		if (peer == null || game == null) {
			return
		}

		val request = TL_messages_sendMedia()
		request.peer = peer

		when (request.peer) {
			is TL_inputPeerChannel -> {
				request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + -peer.channel_id, false)
			}

			is TL_inputPeerChat -> {
				request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + -peer.chat_id, false)
			}

			else -> {
				request.silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + peer.user_id, false)
			}
		}

		request.random_id = if (randomId != 0L) randomId else nextRandomId
		request.message = ""
		request.media = game

		val fromId = ChatObject.getSendAsPeerId(messagesController.getChat(peer.chat_id), messagesController.getChatFull(peer.chat_id))

		if (fromId != UserConfig.getInstance(currentAccount).getClientUserId()) {
			request.send_as = messagesController.getInputPeer(fromId)
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
		sendMessage(null, null, null, null, null, null, null, null, null, null, retryMessageObject.dialogId, retryMessageObject.messageOwner?.attachPath, null, null, null, true, retryMessageObject, null, retryMessageObject.messageOwner?.reply_markup, retryMessageObject.messageOwner?.params, retryMessageObject.messageOwner?.silent != true, if (retryMessageObject.scheduled) retryMessageObject.messageOwner!!.date else 0, 0, null, null, false, retryMessageObject.isMediaSale, retryMessageObject.mediaSaleHash)
	}

	fun sendMessage(user: User?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
		sendMessage(null, null, null, null, null, user, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false, isMediaSale, mediaSaleHash)
	}

	fun sendMessage(invoice: TL_messageMediaInvoice?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
		sendMessage(null, null, null, null, null, null, null, null, null, invoice, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false, isMediaSale, mediaSaleHash)
	}

	fun sendMessage(document: TL_document?, videoEditedInfo: VideoEditedInfo?, path: String?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: String?, entities: List<MessageEntity>?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, ttl: Int, parentObject: Any?, sendAnimationData: SendAnimationData?, updateStickersOrder: Boolean, isMediaSale: Boolean, mediaSaleHash: String?) {
		sendMessage(null, caption, null, null, videoEditedInfo, null, document, null, null, null, peer, path, replyToMsg, replyToTopMsg, null, true, null, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, sendAnimationData, updateStickersOrder, isMediaSale, mediaSaleHash)
	}

	fun sendMessage(message: String?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, webPage: WebPage?, searchLinks: Boolean, entities: List<MessageEntity>?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, sendAnimationData: SendAnimationData?, updateStickersOrder: Boolean, isMediaSale: Boolean, mediaSaleHash: String?) {
		sendMessage(message, null, null, null, null, null, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, webPage, searchLinks, null, entities, replyMarkup, params, notify, scheduleDate, 0, null, sendAnimationData, updateStickersOrder, isMediaSale, mediaSaleHash)
	}

	fun sendMessage(location: MessageMedia?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
		sendMessage(null, null, location, null, null, null, null, null, null, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false, isMediaSale, mediaSaleHash)
	}

	fun sendMessage(poll: TL_messageMediaPoll?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
		sendMessage(null, null, null, null, null, null, null, null, poll, null, peer, null, replyToMsg, replyToTopMsg, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false, isMediaSale, mediaSaleHash)
	}

	fun sendMessage(game: TL_game?, peer: Long, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
		sendMessage(null, null, null, null, null, null, null, game, null, null, peer, null, null, null, null, true, null, null, replyMarkup, params, notify, scheduleDate, 0, null, null, false, isMediaSale, mediaSaleHash)
	}

	fun sendMessage(photo: TL_photo?, path: String?, peer: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: String?, entities: List<MessageEntity>?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, ttl: Int, parentObject: Any?, updateStickersOrder: Boolean, isMediaSale: Boolean, mediaSaleHash: String?) {
		sendMessage(null, caption, null, photo, null, null, null, null, null, null, peer, path, replyToMsg, replyToTopMsg, null, true, null, entities, replyMarkup, params, notify, scheduleDate, ttl, parentObject, null, updateStickersOrder, isMediaSale, mediaSaleHash)
	}

	private fun sendMessage(message: String?, caption: String?, location: MessageMedia?, photo: TL_photo?, videoEditedInfo: VideoEditedInfo?, user: User?, document: TL_document?, game: TL_game?, poll: TL_messageMediaPoll?, invoice: TL_messageMediaInvoice?, peer: Long, path: String?, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, webPage: WebPage?, searchLinks: Boolean, retryMessageObject: MessageObject?, entities: List<MessageEntity>?, replyMarkup: ReplyMarkup?, params: HashMap<String, String>?, notify: Boolean, scheduleDate: Int, ttl: Int, parentObject: Any?, sendAnimationData: SendAnimationData?, updateStickersOrder: Boolean, isMediaSale: Boolean, mediaSaleHash: String?) {
		if (peer == 0L) {
			return
		}

		@Suppress("NAME_SHADOWING") var message = message
		@Suppress("NAME_SHADOWING") var caption = caption
		@Suppress("NAME_SHADOWING") var location = location
		@Suppress("NAME_SHADOWING") var photo = photo
		@Suppress("NAME_SHADOWING") var videoEditedInfo = videoEditedInfo
		@Suppress("NAME_SHADOWING") var user = user
		@Suppress("NAME_SHADOWING") var document = document
		@Suppress("NAME_SHADOWING") var poll = poll
		@Suppress("NAME_SHADOWING") var webPage = webPage
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
					retryMessageObject.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
					notificationCenter.postNotificationName(NotificationCenter.messageSendError, retryMessageObject.id)
					processSentMessage(retryMessageObject.id)
				}
				return
			}
		}
		else if (sendToPeer is TL_inputPeerChannel) {
			val chat = messagesController.getChat(sendToPeer.channel_id)
			val chatFull = messagesController.getChatFull(chat?.id)

			isChannel = chat != null && !chat.megagroup

			if (isChannel && chat?.has_link == true && chatFull != null) {
				linkedToGroup = chatFull.linked_chat_id
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
						if (retryMessageObject.messageOwner?.media is TL_messageMediaGame) {
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
						photo = newMsg?.media?.photo as? TL_photo

						if (retryMessageObject.messageOwner?.message != null) {
							caption = retryMessageObject.messageOwner?.message
						}

						type = 2
					}
					else if (retryMessageObject.type == MessageObject.TYPE_VIDEO || retryMessageObject.type == MessageObject.TYPE_ROUND_VIDEO || retryMessageObject.videoEditedInfo != null) {
						type = 3
						document = newMsg?.media?.document as? TL_document

						if (retryMessageObject.messageOwner?.message != null) {
							caption = retryMessageObject.messageOwner?.message
						}
					}
					else if (retryMessageObject.type == 12) {
						user = TL_userRequest_old2()
						// user.phone = newMsg.media.phone_number;
						user.first_name = newMsg?.media?.first_name
						user.last_name = newMsg?.media?.last_name

						val reason = TL_restrictionReason()
						reason.platform = ""
						reason.reason = ""
						reason.text = newMsg?.media?.vcard

						user.restriction_reason.add(reason)
						user.id = newMsg?.media?.user_id ?: 0

						type = 6
					}
					else if (retryMessageObject.type == 8 || retryMessageObject.type == 9 || retryMessageObject.type == MessageObject.TYPE_STICKER || retryMessageObject.type == MessageObject.TYPE_MUSIC || retryMessageObject.type == MessageObject.TYPE_ANIMATED_STICKER) {
						document = newMsg?.media?.document as? TL_document
						type = 7

						if (retryMessageObject.messageOwner?.message != null) {
							caption = retryMessageObject.messageOwner?.message
						}
					}
					else if (retryMessageObject.type == MessageObject.TYPE_VOICE) {
						document = newMsg?.media?.document as? TL_document
						type = 8

						if (retryMessageObject.messageOwner?.message != null) {
							caption = retryMessageObject.messageOwner?.message
						}
					}
					else if (retryMessageObject.type == MessageObject.TYPE_POLL) {
						poll = newMsg?.media as TL_messageMediaPoll
						type = 10
					}

					if (params != null && params.containsKey("query_id")) {
						type = 9
					}

					if ((newMsg?.media?.ttl_seconds ?: 0) > 0) {
						ttl = newMsg?.media?.ttl_seconds ?: 0
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
					newMsg = if (encryptedChat != null) {
						TL_message_secret()
					}
					else {
						TL_message()
					}

					if (encryptedChat != null && webPage is TL_webPagePending) {
						if (webPage.url != null) {
							val newWebPage: WebPage = TL_webPageUrlPending()
							newWebPage.url = webPage.url
							webPage = newWebPage
						}
						else {
							webPage = null
						}
					}
					if (canSendStickers && message.length < 30 && webPage == null && entities.isNullOrEmpty() && messagesController.diceEmojies?.contains(message.replace("\ufe0f", "")) == true && encryptedChat == null && scheduleDate == 0) {
						val mediaDice = TL_messageMediaDice()
						mediaDice.emoticon = message
						mediaDice.value = -1

						newMsg.media = mediaDice

						type = 11
						caption = ""
					}
					else {
						if (webPage == null) {
							newMsg.media = TL_messageMediaEmpty()
						}
						else {
							newMsg.media = TL_messageMediaWebPage()
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
					newMsg = if (encryptedChat != null) {
						TL_message_secret()
					}
					else {
						TL_message()
					}

					newMsg.media = poll
					type = 10
				}
				else if (location != null) {
					newMsg = if (encryptedChat != null) {
						TL_message_secret()
					}
					else {
						TL_message()
					}

					newMsg.media = location

					type = if (params != null && params.containsKey("query_id")) {
						9
					}
					else {
						1
					}
				}
				else if (photo != null) {
					newMsg = if (encryptedChat != null) {
						TL_message_secret()
					}
					else {
						TL_message()
					}

					newMsg.media = TL_messageMediaPhoto()
					newMsg.media!!.flags = newMsg.media!!.flags or 3

					if (entities != null) {
						newMsg.entities = ArrayList(entities)
					}

					if (ttl != 0) {
						newMsg.media!!.ttl_seconds = ttl
						newMsg.ttl = newMsg.media!!.ttl_seconds
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
					newMsg = TL_message()
					newMsg.media = TL_messageMediaGame()
					newMsg.media?.game = game

					if (params != null && params.containsKey("query_id")) {
						type = 9
					}
				}
				else if (invoice != null) {
					newMsg = TL_message()
					newMsg.media = invoice

					if (params != null && params.containsKey("query_id")) {
						type = 9
					}
				}
				else if (user != null) {
					newMsg = if (encryptedChat != null) {
						TL_message_secret()
					}
					else {
						TL_message()
					}

					newMsg.media = TL_messageMediaContact()
					newMsg.media?.first_name = user.first_name
					newMsg.media?.last_name = user.last_name
					newMsg.media?.user_id = user.id

					if (user.restriction_reason.isNotEmpty() && user.restriction_reason[0].text.startsWith("BEGIN:VCARD")) {
						newMsg.media?.vcard = user.restriction_reason[0].text
					}
					else {
						newMsg.media?.vcard = ""
					}

					if (newMsg.media?.first_name == null) {
						newMsg.media?.first_name = ""
						user.first_name = newMsg.media?.first_name
					}

					if (newMsg.media?.last_name == null) {
						newMsg.media?.last_name = ""
						user.last_name = newMsg.media?.last_name
					}

					type = if (params != null && params.containsKey("query_id")) {
						9
					}
					else {
						6
					}
				}
				else if (document != null) {
					newMsg = if (encryptedChat != null) {
						TL_message_secret()
					}
					else {
						TL_message()
					}

					if (DialogObject.isChatDialog(peer)) {
						if (!canSendStickers) {
							var a = 0
							val n = document.attributes.size

							while (a < n) {
								if (document.attributes[a] is TL_documentAttributeAnimated) {
									document.attributes.removeAt(a)
									forceNoSoundVideo = true
									break
								}

								a++
							}
						}
					}

					newMsg.media = TL_messageMediaDocument()
					newMsg.media!!.flags = newMsg.media!!.flags or 3

					if (ttl != 0) {
						newMsg.media!!.ttl_seconds = ttl
						newMsg.ttl = newMsg.media!!.ttl_seconds
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

					if (encryptedChat != null && document.dc_id > 0 && !MessageObject.isStickerDocument(document) && !MessageObject.isAnimatedStickerDocument(document, true)) {
						newMsg.attachPath = FileLoader.getInstance(currentAccount).getPathToAttach(document).toString()
					}
					else {
						newMsg.attachPath = path
					}

					if (encryptedChat != null && (MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true))) {
						for (a in document.attributes.indices) {
							val attribute = document.attributes[a]

							if (attribute is TL_documentAttributeSticker) {
								document.attributes.removeAt(a)

								val attributeSticker = TL_documentAttributeSticker_layer55()

								document.attributes.add(attributeSticker)

								attributeSticker.alt = attribute.alt

								if (attribute.stickerset != null) {
									val name = if (attribute.stickerset is TL_inputStickerSetShortName) {
										attribute.stickerset.short_name
									}
									else {
										mediaDataController.getStickerSetName(attribute.stickerset.id)
									}

									if (!name.isNullOrEmpty()) {
										attributeSticker.stickerset = TL_inputStickerSetShortName()
										attributeSticker.stickerset.short_name = name
									}
									else {
										if (attribute.stickerset is TL_inputStickerSetID) {
											delayedMessage = DelayedMessage(peer)
											delayedMessage.encryptedChat = encryptedChat
											delayedMessage.locationParent = attributeSticker
											delayedMessage.type = 5
											delayedMessage.parentObject = attribute.stickerset
										}

										attributeSticker.stickerset = TL_inputStickerSetEmpty()
									}
								}
								else {
									attributeSticker.stickerset = TL_inputStickerSetEmpty()
								}

								break
							}
						}
					}
				}

				if (!entities.isNullOrEmpty()) {
					newMsg?.entities = ArrayList(entities)
					newMsg?.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
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
				newMsg?.local_id = newMsg.id
				newMsg?.out = true

				if (isChannel && sendToPeer != null) {
					newMsg?.from_id = TL_peerChannel()
					newMsg?.from_id?.channel_id = sendToPeer.channel_id
				}
				else if (fromPeer != null) {
					newMsg?.from_id = fromPeer

					if (rank != null) {
						newMsg?.post_author = rank
						newMsg?.flags = newMsg.flags or 65536
					}
				}
				else {
					newMsg?.from_id = TL_peerUser()
					newMsg?.from_id?.user_id = myId
					newMsg?.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_FROM_ID
				}

				userConfig.saveConfig(false)
			}

			newMsg!!.silent = !notify || MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_$peer", false)

			if (newMsg.random_id == 0L) {
				newMsg.random_id = nextRandomId
			}

			if (params != null && params.containsKey("bot")) {
				if (encryptedChat != null) {
					newMsg.via_bot_name = params["bot_name"]

					if (newMsg.via_bot_name == null) {
						newMsg.via_bot_name = ""
					}
				}
				else {
					newMsg.via_bot_id = Utilities.parseInt(params["bot"]).toLong()
				}

				newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_BOT_ID
			}

			newMsg.params = params

			if (retryMessageObject == null || !retryMessageObject.resendAsIs) {
				newMsg.date = if (scheduleDate != 0) scheduleDate else connectionsManager.currentTime

				if (sendToPeer is TL_inputPeerChannel) {
					if (scheduleDate == 0 && isChannel) {
						newMsg.views = 1
						newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_VIEWS
					}

					val chat = messagesController.getChat(sendToPeer.channel_id)

					if (chat != null) {
						if (chat.megagroup) {
							newMsg.unread = true
						}
						else {
							newMsg.post = true

							if (chat.signatures) {
								newMsg.from_id = TL_peerUser()
								newMsg.from_id!!.user_id = myId
							}
						}
					}
				}
				else {
					newMsg.unread = true
				}
			}

			newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
			newMsg.dialog_id = peer

			if (replyToMsg != null) {
				newMsg.reply_to = TL_messageReplyHeader()

				if (encryptedChat != null && replyToMsg.messageOwner?.random_id != 0L) {
					newMsg.reply_to!!.reply_to_random_id = replyToMsg.messageOwner!!.random_id
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_REPLY
				}
				else {
					newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_REPLY
				}

				newMsg.reply_to!!.reply_to_msg_id = replyToMsg.id

				if (replyToTopMsg != null && replyToTopMsg !== replyToMsg) {
					newMsg.reply_to!!.reply_to_top_id = replyToTopMsg.id
					newMsg.reply_to!!.flags = newMsg.reply_to!!.flags or 2
				}
			}

			if (linkedToGroup != 0L) {
				newMsg.replies = TL_messageReplies()
				newMsg.replies!!.comments = true
				newMsg.replies!!.channel_id = linkedToGroup
				newMsg.replies!!.flags = newMsg.replies!!.flags or 1
				newMsg.flags = newMsg.flags or 8388608
			}

			if (replyMarkup != null && encryptedChat == null) {
				newMsg.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_MARKUP
				newMsg.reply_markup = replyMarkup

				val bot = params!!["bot"]

				if (bot != null) {
					newMsg.via_bot_id = bot.toLong()
				}
			}

			if (!DialogObject.isEncryptedDialog(peer)) {
				newMsg.peer_id = messagesController.getPeer(peer)

				if (DialogObject.isUserDialog(peer)) {
					val sendToUser = messagesController.getUser(peer)

					if (sendToUser == null) {
						processSentMessage(newMsg.id)
						return
					}

					if (sendToUser.bot) {
						newMsg.unread = false
					}
				}
			}
			else {
				newMsg.peer_id = TL_peerUser()

				if (encryptedChat!!.participant_id == myId) {
					newMsg.peer_id!!.user_id = encryptedChat.admin_id
				}
				else {
					newMsg.peer_id!!.user_id = encryptedChat.participant_id
				}

				if (ttl != 0) {
					newMsg.ttl = ttl
				}
				else {
					newMsg.ttl = encryptedChat.ttl

					if (newMsg.ttl != 0 && newMsg.media != null) {
						newMsg.media!!.ttl_seconds = newMsg.ttl
						newMsg.media!!.flags = newMsg.media!!.flags or 4
					}
				}

				if (newMsg.ttl != 0 && newMsg.media?.document != null) {
					if (MessageObject.isVoiceMessage(newMsg)) {
						var duration = 0

						for (a in newMsg.media!!.document.attributes.indices) {
							val attribute = newMsg.media!!.document.attributes[a]

							if (attribute is TL_documentAttributeAudio) {
								duration = attribute.duration
								break
							}
						}

						newMsg.ttl = max(newMsg.ttl, duration + 1)
					}
					else if (MessageObject.isVideoMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
						var duration = 0

						for (a in newMsg.media!!.document.attributes.indices) {
							val attribute = newMsg.media!!.document.attributes[a]

							if (attribute is TL_documentAttributeVideo) {
								duration = attribute.duration
								break
							}
						}

						newMsg.ttl = max(newMsg.ttl, duration + 1)
					}
				}
			}

			if (MessageObject.isVoiceMessage(newMsg) || MessageObject.isRoundVideoMessage(newMsg)) {
				newMsg.media_unread = true
			}

			if (newMsg.from_id == null) {
				newMsg.from_id = newMsg.peer_id
			}

			newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING

			var groupId: Long = 0
			var isFinalGroupMedia = false

			if (params != null) {
				val groupIdStr = params["groupId"]

				if (groupIdStr != null) {
					groupId = Utilities.parseLong(groupIdStr)

					newMsg.groupId = groupId
					newMsg.flags = newMsg.flags or 131072
				}

				isFinalGroupMedia = params["final"] != null
			}

			newMsgObj = MessageObject(currentAccount, newMsg, replyToMsg, generateLayout = true, checkMediaExists = true)
			newMsgObj.sendAnimationData = sendAnimationData
			newMsgObj.wasJustSent = true
			newMsgObj.scheduled = scheduleDate != 0
			newMsgObj.isMediaSale = isMediaSale
			newMsgObj.mediaSaleHash = mediaSaleHash

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
				FileLog.d("send message user_id = " + sendToPeer.user_id + " chat_id = " + sendToPeer.chat_id + " channel_id = " + sendToPeer.channel_id + " access_hash = " + sendToPeer.access_hash + " notify = " + notify + " silent = " + MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_$peer", false))
			}

			var performMediaUpload = false

			if (type == 0 || type == 9 && message != null && encryptedChat != null) {
				if (encryptedChat == null) {
					val reqSend = TL_messages_sendMessage()
					reqSend.message = message
					reqSend.clear_draft = retryMessageObject == null
					reqSend.silent = newMsg.silent
					reqSend.peer = sendToPeer
					reqSend.random_id = newMsg.random_id

					if (updateStickersOrder) {
						reqSend.update_stickersets_order = true
					}

					if (newMsg.from_id != null) {
						reqSend.send_as = messagesController.getInputPeer(newMsg.from_id)
					}

					if (newMsg.reply_to != null && newMsg.reply_to?.reply_to_msg_id != 0) {
						reqSend.flags = reqSend.flags or 1
						reqSend.reply_to_msg_id = newMsg.reply_to?.reply_to_msg_id ?: 0
					}

					if (!searchLinks) {
						reqSend.no_webpage = true
					}

					if (!entities.isNullOrEmpty()) {
						reqSend.entities = ArrayList(entities)
						reqSend.flags = reqSend.flags or 8
					}

					if (scheduleDate != 0) {
						reqSend.schedule_date = scheduleDate
						reqSend.flags = reqSend.flags or 1024
					}

					performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, scheduleDate != 0)

					if (retryMessageObject == null) {
						mediaDataController.cleanDraft(peer, replyToTopMsg?.id ?: 0, false)
					}
				}
				else {
					val reqSend = TL_decryptedMessage()
					reqSend.ttl = newMsg.ttl

					if (!entities.isNullOrEmpty()) {
						reqSend.entities = ArrayList(entities)
						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
					}

					if (newMsg.reply_to != null && newMsg.reply_to?.reply_to_random_id != 0L) {
						reqSend.reply_to_random_id = newMsg.reply_to?.reply_to_random_id ?: 0
						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_REPLY
					}

					if (params != null && params["bot_name"] != null) {
						reqSend.via_bot_name = params["bot_name"]
						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_BOT_ID
					}

					reqSend.silent = newMsg.silent
					reqSend.random_id = newMsg.random_id
					reqSend.message = message

					if (webPage?.url != null) {
						reqSend.media = TL_decryptedMessageMediaWebPage()
						reqSend.media.url = webPage.url
						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
					}
					else {
						reqSend.media = TL_decryptedMessageMediaEmpty()
					}

					secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj)

					if (retryMessageObject == null) {
						mediaDataController.cleanDraft(peer, replyToTopMsg?.id ?: 0, false)
					}
				}
			}
			else if (type in 1..3 || type in 5..8 || type == 9 && encryptedChat != null || type == 10 || type == 11) {
				if (encryptedChat == null) {
					var inputMedia: InputMedia? = null

					if (type == 1) {
						if (location is TL_messageMediaVenue) {
							inputMedia = TL_inputMediaVenue()
							inputMedia.address = location.address
							inputMedia.title = location.title
							inputMedia.provider = location.provider
							inputMedia.venue_id = location.venue_id
							inputMedia.venue_type = ""
						}
						else if (location is TL_messageMediaGeoLive) {
							inputMedia = TL_inputMediaGeoLive()
							inputMedia.period = location.period
							inputMedia.flags = inputMedia.flags or 2

							if (location.heading != 0) {
								inputMedia.heading = location.heading
								inputMedia.flags = inputMedia.flags or 4
							}

							if (location.proximity_notification_radius != 0) {
								inputMedia.proximity_notification_radius = location.proximity_notification_radius
								inputMedia.flags = inputMedia.flags or 8
							}
						}
						else {
							inputMedia = TL_inputMediaGeoPoint()
						}

						inputMedia.geo_point = TL_inputGeoPoint()
						inputMedia.geo_point.lat = location!!.geo.lat
						inputMedia.geo_point._long = location.geo._long
					}
					else if (type == 2 || type == 9 && photo != null) {
						val uploadedPhoto = TL_inputMediaUploadedPhoto()

						if (ttl != 0) {
							uploadedPhoto.ttl_seconds = ttl
							newMsg.ttl = uploadedPhoto.ttl_seconds
							uploadedPhoto.flags = uploadedPhoto.flags or 2
						}

						if (params != null) {
							val masks = params["masks"]

							if (masks != null) {
								val serializedData = SerializedData(Utilities.hexToBytes(masks))
								val count = serializedData.readInt32(false)

								for (a in 0 until count) {
									uploadedPhoto.stickers.add(InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false))
								}

								uploadedPhoto.flags = uploadedPhoto.flags or 1
								serializedData.cleanup()
							}
						}

						if (photo?.access_hash == 0L) {
							inputMedia = uploadedPhoto
							performMediaUpload = true
						}
						else {
							val media = TL_inputMediaPhoto()
							media.id = TL_inputPhoto()
							media.id.id = photo!!.id
							media.id.access_hash = photo.access_hash
							media.id.file_reference = photo.file_reference

							if (media.id.file_reference == null) {
								media.id.file_reference = ByteArray(0)
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
							delayedMessage.photoSize = photo.sizes[photo.sizes.size - 1]
							delayedMessage.locationParent = photo
						}
					}
					else if (type == 3) {
						val uploadedDocument = TL_inputMediaUploadedDocument()
						uploadedDocument.mime_type = document!!.mime_type
						uploadedDocument.attributes = document.attributes

						if (forceNoSoundVideo || !MessageObject.isRoundVideoDocument(document) && (videoEditedInfo == null || !videoEditedInfo.muted && !videoEditedInfo.roundVideo)) {
							uploadedDocument.nosound_video = true

							FileLog.d("nosound_video = true")
						}

						if (ttl != 0) {
							uploadedDocument.ttl_seconds = ttl
							newMsg.ttl = uploadedDocument.ttl_seconds
							uploadedDocument.flags = uploadedDocument.flags or 2
						}

						if (params != null) {
							val masks = params["masks"]

							if (masks != null) {
								val serializedData = SerializedData(Utilities.hexToBytes(masks))
								val count = serializedData.readInt32(false)

								for (a in 0 until count) {
									uploadedDocument.stickers.add(InputDocument.TLdeserialize(serializedData, serializedData.readInt32(false), false))
								}

								uploadedDocument.flags = uploadedDocument.flags or 1
								serializedData.cleanup()
							}
						}

						if (document.access_hash == 0L) {
							inputMedia = uploadedDocument
							performMediaUpload = true
						}
						else {
							val media = TL_inputMediaDocument()
							media.id = TL_inputDocument()
							media.id.id = document.id
							media.id.access_hash = document.access_hash
							media.id.file_reference = document.file_reference

							if (media.id.file_reference == null) {
								media.id.file_reference = ByteArray(0)
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

							if (photoSize !is TL_photoStrippedSize) {
								delayedMessage.photoSize = photoSize
								delayedMessage.locationParent = document
							}
						}

						delayedMessage.videoEditedInfo = videoEditedInfo
					}
					else if (type == 6) {
						inputMedia = TL_inputMediaContact()
						inputMedia.first_name = user!!.first_name
						inputMedia.last_name = user.last_name
						inputMedia.phone_number = String.format("@%s", user.username) // MARK: this is workaround to get contacts work on chat screen // Nik
						inputMedia.user_id = user.id

						if (user.restriction_reason.isNotEmpty() && user.restriction_reason[0].text.startsWith("BEGIN:VCARD")) {
							inputMedia.vcard = user.restriction_reason[0].text
						}
						else {
							inputMedia.vcard = ""
						}
					}
					else if (type == 7 || type == 9) {
						val http = false
						val uploadedMedia: InputMedia?

						if (originalPath != null || path != null || document!!.access_hash == 0L) {
							uploadedMedia = TL_inputMediaUploadedDocument()

							if (ttl != 0) {
								uploadedMedia.ttl_seconds = ttl
								newMsg.ttl = uploadedMedia.ttl_seconds
								uploadedMedia.flags = uploadedMedia.flags or 2
							}

							if (forceNoSoundVideo || !TextUtils.isEmpty(path) && path!!.lowercase(Locale.getDefault()).endsWith("mp4") && (params == null || params.containsKey("forceDocument"))) {
								uploadedMedia.nosound_video = true
							}

							uploadedMedia.force_file = params != null && params.containsKey("forceDocument")
							uploadedMedia.mime_type = document!!.mime_type
							uploadedMedia.attributes = document.attributes
						}
						else {
							uploadedMedia = null
						}

						if (document.access_hash == 0L) {
							inputMedia = uploadedMedia
							performMediaUpload = uploadedMedia is TL_inputMediaUploadedDocument
						}
						else {
							val media = TL_inputMediaDocument()
							media.id = TL_inputDocument()
							media.id.id = document.id
							media.id.access_hash = document.access_hash
							media.id.file_reference = document.file_reference

							if (media.id.file_reference == null) {
								media.id.file_reference = ByteArray(0)
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

								if (photoSize !is TL_photoStrippedSize) {
									delayedMessage.photoSize = photoSize
									delayedMessage.locationParent = document
								}
							}
						}
					}
					else if (type == 8) {
						val uploadedDocument = TL_inputMediaUploadedDocument()
						uploadedDocument.mime_type = document!!.mime_type
						uploadedDocument.attributes = document.attributes

						if (ttl != 0) {
							uploadedDocument.ttl_seconds = ttl
							newMsg.ttl = uploadedDocument.ttl_seconds
							uploadedDocument.flags = uploadedDocument.flags or 2
						}

						if (document.access_hash == 0L) {
							inputMedia = uploadedDocument
							performMediaUpload = true
						}
						else {
							val media = TL_inputMediaDocument()
							media.id = TL_inputDocument()
							media.id.id = document.id
							media.id.access_hash = document.access_hash
							media.id.file_reference = document.file_reference

							if (media.id.file_reference == null) {
								media.id.file_reference = ByteArray(0)
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
						val inputMediaPoll = TL_inputMediaPoll()
						inputMediaPoll.poll = poll!!.poll

						if (params != null && params.containsKey("answers")) {
							val answers = Utilities.hexToBytes(params["answers"])

							if (answers.isNotEmpty()) {
								for (answer in answers) {
									inputMediaPoll.correct_answers.add(byteArrayOf(answer))
								}

								inputMediaPoll.flags = inputMediaPoll.flags or 1
							}
						}

						if (poll.results != null && !TextUtils.isEmpty(poll.results.solution)) {
							inputMediaPoll.solution = poll.results.solution
							inputMediaPoll.solution_entities = poll.results.solution_entities
							inputMediaPoll.flags = inputMediaPoll.flags or 2
						}

						inputMedia = inputMediaPoll
					}
					else if (type == 11) {
						val inputMediaDice = TL_inputMediaDice()
						inputMediaDice.emoticon = message
						inputMedia = inputMediaDice
					}

					val reqSend: TLObject?

					if (groupId != 0L) {
						var request: TL_messages_sendMultiMedia? = null

						if (delayedMessage?.sendRequest != null) {
							request = delayedMessage.sendRequest as? TL_messages_sendMultiMedia
						}

						if (request == null) {
							request = TL_messages_sendMultiMedia()
							request.peer = sendToPeer
							request.silent = newMsg.silent
							request.is_media_sale = isMediaSale
							request.media_sale_hash = mediaSaleHash

							if (isMediaSale) {
								newMsg.is_media_sale = true
								newMsg.noforwards = true
							}

							if (newMsg.reply_to != null && newMsg.reply_to?.reply_to_msg_id != 0) {
								request.flags = request.flags or 1
								request.reply_to_msg_id = newMsg.reply_to?.reply_to_msg_id ?: 0
							}

							if (scheduleDate != 0) {
								request.schedule_date = scheduleDate
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

						val inputSingleMedia = TL_inputSingleMedia()
						inputSingleMedia.random_id = newMsg.random_id
						inputSingleMedia.media = inputMedia
						inputSingleMedia.message = caption

						if (!entities.isNullOrEmpty()) {
							inputSingleMedia.entities = ArrayList(entities)
							inputSingleMedia.flags = inputSingleMedia.flags or 1
						}

						request.multi_media.add(inputSingleMedia)
						reqSend = request
					}
					else {
						val request = TL_messages_sendMedia()
						request.peer = sendToPeer
						request.silent = newMsg.silent
						request.media_sale_hash = mediaSaleHash
						request.is_media_sale = isMediaSale

						if (isMediaSale) {
							newMsg.is_media_sale = true
							newMsg.noforwards = true

							request.noforwards = true
						}

						if (newMsg.reply_to != null && newMsg.reply_to?.reply_to_msg_id != 0) {
							request.flags = request.flags or 1
							request.reply_to_msg_id = newMsg.reply_to?.reply_to_msg_id ?: 0
						}

						request.random_id = newMsg.random_id

						if (newMsg.from_id != null) {
							request.send_as = messagesController.getInputPeer(newMsg.from_id)
						}

						request.media = inputMedia
						request.message = caption

						if (!entities.isNullOrEmpty()) {
							request.entities = ArrayList(entities)
							request.flags = request.flags or 8
						}

						if (scheduleDate != 0) {
							request.schedule_date = scheduleDate
							request.flags = request.flags or 1024
						}

						if (updateStickersOrder) {
							request.update_stickersets_order = true
						}

						if (delayedMessage != null) {
							delayedMessage.sendRequest = request
						}

						reqSend = request

//						if (updateStickersOrder) {
//                            if (MessageObject.getStickerSetId(document) != -1) {
//                                TLRPC.TL_updateMoveStickerSetToTop update = new TLRPC.TL_updateMoveStickerSetToTop();
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
					val reqSend: TL_decryptedMessage

					if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 73) {
						reqSend = TL_decryptedMessage()

						if (groupId != 0L) {
							reqSend.grouped_id = groupId
							reqSend.flags = reqSend.flags or 131072
						}
					}
					else {
						reqSend = TL_decryptedMessage_layer45()
					}

					reqSend.ttl = newMsg.ttl

					if (!entities.isNullOrEmpty()) {
						reqSend.entities = ArrayList(entities)
						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
					}

					if (newMsg.reply_to != null && newMsg.reply_to?.reply_to_random_id != 0L) {
						reqSend.reply_to_random_id = newMsg.reply_to?.reply_to_random_id ?: 0
						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_REPLY
					}

					reqSend.silent = newMsg.silent
					reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA

					if (params != null && params["bot_name"] != null) {
						reqSend.via_bot_name = params["bot_name"]
						reqSend.flags = reqSend.flags or TLRPC.MESSAGE_FLAG_HAS_BOT_ID
					}

					reqSend.random_id = newMsg.random_id
					reqSend.message = ""

					if (type == 1) {
						if (location is TL_messageMediaVenue) {
							reqSend.media = TL_decryptedMessageMediaVenue()
							reqSend.media.address = location.address
							reqSend.media.title = location.title
							reqSend.media.provider = location.provider
							reqSend.media.venue_id = location.venue_id
						}
						else {
							reqSend.media = TL_decryptedMessageMediaGeoPoint()
						}

						reqSend.media.lat = location!!.geo.lat
						reqSend.media._long = location.geo._long

						secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj)
					}
					else if (type == 2 || type == 9 && photo != null) {
						val small = photo!!.sizes[0]
						val big = photo.sizes[photo.sizes.size - 1]

						ImageLoader.fillPhotoSizeWithBytes(small)

						reqSend.media = TL_decryptedMessageMediaPhoto()
						reqSend.media.caption = caption

						if (small.bytes != null) {
							(reqSend.media as TL_decryptedMessageMediaPhoto).thumb = small.bytes
						}
						else {
							(reqSend.media as TL_decryptedMessageMediaPhoto).thumb = ByteArray(0)
						}

						reqSend.media.thumb_h = small.h
						reqSend.media.thumb_w = small.w
						reqSend.media.w = big.w
						reqSend.media.h = big.h
						reqSend.media.size = big.size.toLong()

						if (big.location.key == null || groupId != 0L) {
							if (delayedMessage == null) {
								delayedMessage = DelayedMessage(peer)
								delayedMessage.encryptedChat = encryptedChat
								delayedMessage.type = 0
								delayedMessage.originalPath = originalPath
								delayedMessage.sendEncryptedRequest = reqSend
								delayedMessage.obj = newMsgObj

								if (params != null && params.containsKey("parentObject")) {
									delayedMessage.parentObject = params["parentObject"]
								}
								else {
									delayedMessage.parentObject = parentObject
								}

								delayedMessage.performMediaUpload = true
								delayedMessage.scheduled = scheduleDate != 0
							}

							if (!path.isNullOrEmpty() && path.startsWith("http")) {
								delayedMessage.httpLocation = path
							}
							else {
								delayedMessage.photoSize = photo.sizes[photo.sizes.size - 1]
								delayedMessage.locationParent = photo
							}

							if (groupId == 0L) {
								performSendDelayedMessage(delayedMessage)
							}
						}
						else {
							val encryptedFile = TL_inputEncryptedFile()
							encryptedFile.id = big.location.volume_id
							encryptedFile.access_hash = big.location.secret

							reqSend.media.key = big.location.key
							reqSend.media.iv = big.location.iv

							secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj)
						}
					}
					else if (type == 3) {
						val thumb = getThumbForSecretChat(document!!.thumbs)

						ImageLoader.fillPhotoSizeWithBytes(thumb)

						if (MessageObject.isNewGifDocument(document) || MessageObject.isRoundVideoDocument(document)) {
							reqSend.media = TL_decryptedMessageMediaDocument()
							reqSend.media.attributes = document.attributes

							if (thumb?.bytes != null) {
								(reqSend.media as TL_decryptedMessageMediaDocument).thumb = thumb.bytes
							}
							else {
								(reqSend.media as TL_decryptedMessageMediaDocument).thumb = ByteArray(0)
							}
						}
						else {
							reqSend.media = TL_decryptedMessageMediaVideo()

							if (thumb?.bytes != null) {
								(reqSend.media as TL_decryptedMessageMediaVideo).thumb = thumb.bytes
							}
							else {
								(reqSend.media as TL_decryptedMessageMediaVideo).thumb = ByteArray(0)
							}
						}

						reqSend.media.caption = caption
						reqSend.media.mime_type = "video/mp4"
						reqSend.media.size = document.size

						for (attribute in document.attributes) {
							if (attribute is TL_documentAttributeVideo) {
								reqSend.media.w = attribute.w
								reqSend.media.h = attribute.h
								reqSend.media.duration = attribute.duration
								break
							}
						}

						reqSend.media.thumb_h = thumb!!.h
						reqSend.media.thumb_w = thumb.w

						if (document.key == null || groupId != 0L) {
							if (delayedMessage == null) {
								delayedMessage = DelayedMessage(peer)
								delayedMessage.encryptedChat = encryptedChat
								delayedMessage.type = 1
								delayedMessage.sendEncryptedRequest = reqSend
								delayedMessage.originalPath = originalPath
								delayedMessage.obj = newMsgObj

								if (params != null && params.containsKey("parentObject")) {
									delayedMessage.parentObject = params["parentObject"]
								}
								else {
									delayedMessage.parentObject = parentObject
								}

								delayedMessage.performMediaUpload = true
								delayedMessage.scheduled = scheduleDate != 0
							}

							delayedMessage.videoEditedInfo = videoEditedInfo

							if (groupId == 0L) {
								performSendDelayedMessage(delayedMessage)
							}
						}
						else {
							val encryptedFile = TL_inputEncryptedFile()
							encryptedFile.id = document.id
							encryptedFile.access_hash = document.access_hash

							reqSend.media.key = document.key
							reqSend.media.iv = document.iv

							secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj)
						}
					}
					else if (type == 6) {
						reqSend.media = TL_decryptedMessageMediaContact()
						// reqSend.media.phone_number = user.phone;
						reqSend.media.first_name = user!!.first_name
						reqSend.media.last_name = user.last_name
						reqSend.media.user_id = user.id

						secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj)
					}
					else if (type == 7 || type == 9 && document != null) {
						if (document!!.access_hash != 0L && (MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true))) {
							reqSend.media = TL_decryptedMessageMediaExternalDocument()
							reqSend.media.id = document.id
							reqSend.media.date = document.date
							reqSend.media.access_hash = document.access_hash
							reqSend.media.mime_type = document.mime_type
							reqSend.media.size = document.size
							reqSend.media.dc_id = document.dc_id
							reqSend.media.attributes = document.attributes

							val thumb = getThumbForSecretChat(document.thumbs)

							if (thumb != null) {
								(reqSend.media as TL_decryptedMessageMediaExternalDocument).thumb = thumb
							}
							else {
								(reqSend.media as TL_decryptedMessageMediaExternalDocument).thumb = TL_photoSizeEmpty()
								(reqSend.media as TL_decryptedMessageMediaExternalDocument).thumb.type = "s"
							}

							if (delayedMessage != null && delayedMessage.type == 5) {
								delayedMessage.sendEncryptedRequest = reqSend
								delayedMessage.obj = newMsgObj
								performSendDelayedMessage(delayedMessage)
							}
							else {
								secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, null, null, newMsgObj)
							}
						}
						else {
							reqSend.media = TL_decryptedMessageMediaDocument()
							reqSend.media.attributes = document.attributes
							reqSend.media.caption = caption

							val thumb = getThumbForSecretChat(document.thumbs)

							if (thumb != null) {
								ImageLoader.fillPhotoSizeWithBytes(thumb)

								(reqSend.media as TL_decryptedMessageMediaDocument).thumb = thumb.bytes

								reqSend.media.thumb_h = thumb.h
								reqSend.media.thumb_w = thumb.w
							}
							else {
								(reqSend.media as TL_decryptedMessageMediaDocument).thumb = ByteArray(0)

								reqSend.media.thumb_h = 0
								reqSend.media.thumb_w = 0
							}

							reqSend.media.size = document.size
							reqSend.media.mime_type = document.mime_type

							if (document.key == null || groupId != 0L) {
								if (delayedMessage == null) {
									delayedMessage = DelayedMessage(peer)
									delayedMessage.encryptedChat = encryptedChat
									delayedMessage.type = 2
									delayedMessage.sendEncryptedRequest = reqSend
									delayedMessage.originalPath = originalPath
									delayedMessage.obj = newMsgObj

									if (params != null && params.containsKey("parentObject")) {
										delayedMessage.parentObject = params["parentObject"]
									}
									else {
										delayedMessage.parentObject = parentObject
									}

									delayedMessage.performMediaUpload = true
									delayedMessage.scheduled = scheduleDate != 0
								}

								if (!path.isNullOrEmpty() && path.startsWith("http")) {
									delayedMessage.httpLocation = path
								}

								if (groupId == 0L) {
									performSendDelayedMessage(delayedMessage)
								}
							}
							else {
								val encryptedFile = TL_inputEncryptedFile()
								encryptedFile.id = document.id
								encryptedFile.access_hash = document.access_hash

								reqSend.media.key = document.key
								reqSend.media.iv = document.iv

								secretChatHelper.performSendEncryptedRequest(reqSend, newMsgObj.messageOwner, encryptedChat, encryptedFile, null, newMsgObj)
							}
						}
					}
					else if (type == 8) {
						delayedMessage = DelayedMessage(peer)
						delayedMessage.encryptedChat = encryptedChat
						delayedMessage.sendEncryptedRequest = reqSend
						delayedMessage.obj = newMsgObj
						delayedMessage.type = 3
						delayedMessage.parentObject = parentObject
						delayedMessage.performMediaUpload = true
						delayedMessage.scheduled = scheduleDate != 0

						reqSend.media = TL_decryptedMessageMediaDocument()
						reqSend.media.attributes = document!!.attributes
						reqSend.media.caption = caption

						val thumb = getThumbForSecretChat(document.thumbs)

						if (thumb != null) {
							ImageLoader.fillPhotoSizeWithBytes(thumb)
							(reqSend.media as TL_decryptedMessageMediaDocument).thumb = thumb.bytes
							reqSend.media.thumb_h = thumb.h
							reqSend.media.thumb_w = thumb.w
						}
						else {
							(reqSend.media as TL_decryptedMessageMediaDocument).thumb = ByteArray(0)
							reqSend.media.thumb_h = 0
							reqSend.media.thumb_w = 0
						}

						reqSend.media.mime_type = document.mime_type
						reqSend.media.size = document.size

						delayedMessage.originalPath = originalPath

						performSendDelayedMessage(delayedMessage)
					}

					if (groupId != 0L) {
						val request: TL_messages_sendEncryptedMultiMedia?

						if (delayedMessage!!.sendEncryptedRequest != null) {
							request = delayedMessage.sendEncryptedRequest as TL_messages_sendEncryptedMultiMedia?
						}
						else {
							request = TL_messages_sendEncryptedMultiMedia()
							delayedMessage.sendEncryptedRequest = request
						}

						delayedMessage.messageObjects!!.add(newMsgObj)
						delayedMessage.messages!!.add(newMsg)
						delayedMessage.originalPaths!!.add(originalPath!!)
						delayedMessage.performMediaUpload = true

						request!!.messages.add(reqSend)

						val encryptedFile = TL_inputEncryptedFile()
						encryptedFile.id = (if (type == 3 || type == 7) 1 else 0).toLong()

						request.files.add(encryptedFile)

						performSendDelayedMessage(delayedMessage)
					}

					if (retryMessageObject == null) {
						mediaDataController.cleanDraft(peer, replyToTopMsg?.id ?: 0, false)
					}
				}
			}
			else if (type == 4) {
				val reqSend = TL_messages_forwardMessages()
				reqSend.to_peer = sendToPeer
				reqSend.with_my_score = retryMessageObject!!.messageOwner!!.with_my_score

				if (params != null && params.containsKey("fwd_id")) {
					val fwdId = Utilities.parseInt(params["fwd_id"])

					reqSend.drop_author = true

					val peerId = Utilities.parseLong(params["fwd_peer"])

					if (peerId < 0) {
						val chat = messagesController.getChat(-peerId)

						if (ChatObject.isChannel(chat)) {
							reqSend.from_peer = TL_inputPeerChannel()
							reqSend.from_peer.channel_id = chat.id
							reqSend.from_peer.access_hash = chat.access_hash
						}
						else {
							reqSend.from_peer = TL_inputPeerEmpty()
						}
					}
					else {
						reqSend.from_peer = TL_inputPeerEmpty()
					}

					reqSend.id.add(fwdId)
				}
				else {
					reqSend.from_peer = TL_inputPeerEmpty()
				}

				reqSend.silent = newMsg.silent

				if (scheduleDate != 0) {
					reqSend.schedule_date = scheduleDate
					reqSend.flags = reqSend.flags or 1024
				}

				reqSend.random_id.add(newMsg.random_id)

				if (retryMessageObject.id >= 0) {
					reqSend.id.add(retryMessageObject.id)
				}
				else {
					if (retryMessageObject.messageOwner?.fwd_msg_id != 0) {
						reqSend.id.add(retryMessageObject.messageOwner!!.fwd_msg_id)
					}
					else if (retryMessageObject.messageOwner?.fwd_from != null) {
						reqSend.id.add(retryMessageObject.messageOwner?.fwd_from?.channel_post ?: 0)
					}
				}

				performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, scheduleDate != 0)
			}
			else if (type == 9) {
				val reqSend = TL_messages_sendInlineBotResult()
				reqSend.peer = sendToPeer
				reqSend.random_id = newMsg.random_id

				if (newMsg.from_id != null) {
					reqSend.send_as = messagesController.getInputPeer(newMsg.from_id)
				}

				reqSend.hide_via = !params!!.containsKey("bot")

				if (newMsg.reply_to != null && newMsg.reply_to?.reply_to_msg_id != 0) {
					reqSend.flags = reqSend.flags or 1
					reqSend.reply_to_msg_id = newMsg.reply_to?.reply_to_msg_id ?: 0
				}

				reqSend.silent = newMsg.silent

				if (scheduleDate != 0) {
					reqSend.schedule_date = scheduleDate
					reqSend.flags = reqSend.flags or 1024
				}

				reqSend.query_id = Utilities.parseLong(params["query_id"])
				reqSend.id = params["id"]

				if (retryMessageObject == null) {
					reqSend.clear_draft = true
					mediaDataController.cleanDraft(peer, replyToTopMsg?.id ?: 0, false)
				}

				performSendMessageRequest(reqSend, newMsgObj, null, null, parentObject, scheduleDate != 0)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)

			messagesStorage.markMessageAsSendError(newMsg, scheduleDate != 0)

			newMsgObj?.messageOwner?.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR

			notificationCenter.postNotificationName(NotificationCenter.messageSendError, newMsg!!.id)
			processSentMessage(newMsg.id)
		}
	}

	private fun getThumbForSecretChat(arrayList: ArrayList<PhotoSize?>?): PhotoSize? {
		if (arrayList.isNullOrEmpty()) {
			return null
		}

		var a = 0
		val n = arrayList.size

		while (a < n) {
			val size = arrayList[a]

			if (size == null || size is TL_photoStrippedSize || size is TL_photoPathSize || size is TL_photoSizeEmpty || size.location == null) {
				a++
				continue
			}

			val photoSize: TL_photoSize = TL_photoSize_layer127()
			photoSize.type = size.type
			photoSize.w = size.w
			photoSize.h = size.h
			photoSize.size = size.size
			photoSize.bytes = size.bytes

			if (photoSize.bytes == null) {
				photoSize.bytes = ByteArray(0)
			}

			photoSize.location = TL_fileLocation_layer82()
			photoSize.location.dc_id = size.location.dc_id
			photoSize.location.volume_id = size.location.volume_id
			photoSize.location.local_id = size.location.local_id
			photoSize.location.secret = size.location.secret

			return photoSize
		}

		return null
	}

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

					if (message.sendEncryptedRequest != null && message.photoSize!!.location.dc_id != 0) {
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
						val media = (message.sendRequest as? TL_messages_sendMedia)?.media ?: (message.sendRequest as? TL_messages_editMessage)?.media
						media?.file = message.videoEditedInfo?.file

						message.videoEditedInfo?.file = null
					}
					else if (message.videoEditedInfo?.encryptedFile != null) {
						val decryptedMessage = message.sendEncryptedRequest as? TL_decryptedMessage
						decryptedMessage!!.media.size = message.videoEditedInfo!!.estimatedSize
						decryptedMessage.media.key = message.videoEditedInfo!!.key
						decryptedMessage.media.iv = message.videoEditedInfo!!.iv
						secretChatHelper.performSendEncryptedRequest(decryptedMessage, message.obj!!.messageOwner, message.encryptedChat, message.videoEditedInfo!!.encryptedFile, message.originalPath, message.obj)
						message.videoEditedInfo!!.encryptedFile = null
						return
					}
				}

				if (message.sendRequest != null) {
					val media = if (message.sendRequest is TL_messages_sendMedia) {
						(message.sendRequest as TL_messages_sendMedia?)!!.media
					}
					else {
						(message.sendRequest as TL_messages_editMessage?)!!.media
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
						val location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + message.photoSize!!.location.volume_id + "_" + message.photoSize!!.location.local_id + ".jpg"
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

					if (message.sendEncryptedRequest != null && document?.dc_id != 0) {
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
					val media = if (message.sendRequest is TL_messages_sendMedia) {
						(message.sendRequest as TL_messages_sendMedia?)!!.media
					}
					else {
						(message.sendRequest as TL_messages_editMessage?)!!.media
					}

					if (media?.file == null) {
						val location = message.obj?.messageOwner?.attachPath

						putToDelayedMessages(location, message)

						fileLoader.uploadFile(location, message.sendRequest == null, false, ConnectionsManager.FileTypeFile)

						putToUploadingMessages(message.obj)
					}
					else if (media.thumb == null && message.photoSize != null && message.photoSize !is TL_photoStrippedSize) {
						val location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + message.photoSize!!.location.volume_id + "_" + message.photoSize!!.location.local_id + ".jpg"
						putToDelayedMessages(location, message)
						fileLoader.uploadFile(location, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)
						putToUploadingMessages(message.obj)
					}
				}
				else {
					val location = message.obj?.messageOwner?.attachPath
					val document = message.obj?.document

					if (location != null && message.sendEncryptedRequest != null && document?.dc_id != 0) {
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
							val request = message.sendRequest as? TL_messages_sendMultiMedia
							val media = request!!.multi_media[index].media

							if (media.file == null) {
								putToDelayedMessages(documentLocation, message)

								message.extraHashMap!![messageObject] = documentLocation
								message.extraHashMap!![documentLocation] = media
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
								val location = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).toString() + "/" + message.photoSize!!.location.volume_id + "_" + message.photoSize!!.location.local_id + ".jpg"
								putToDelayedMessages(location, message)

								message.extraHashMap!![location + "_o"] = documentLocation
								message.extraHashMap!![messageObject] = location
								message.extraHashMap!![location] = media

								fileLoader.uploadFile(location, encrypted = false, small = true, type = ConnectionsManager.FileTypePhoto)

								putToUploadingMessages(messageObject)
							}
						}
						else {
							val request = message.sendEncryptedRequest as TL_messages_sendEncryptedMultiMedia?

							putToDelayedMessages(documentLocation, message)

							message.extraHashMap!![messageObject] = documentLocation
							message.extraHashMap!![documentLocation] = request!!.files[index]
							message.extraHashMap!![documentLocation + "_i"] = messageObject

							if (message.photoSize?.location != null) {
								message.extraHashMap!![documentLocation + "_t"] = message.photoSize!!
							}

							if (messageObject.videoEditedInfo?.needConvert() == true) {
								fileLoader.uploadFile(documentLocation, encrypted = true, small = false, estimatedSize = document?.size ?: 0, type = ConnectionsManager.FileTypeVideo, forceSmallFile = false)
							}
							else {
								fileLoader.uploadFile(documentLocation, encrypted = true, small = false, type = ConnectionsManager.FileTypeVideo)
							}

							putToUploadingMessages(messageObject)
						}
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
							val request = message.sendRequest as TL_messages_sendMultiMedia?
							request!!.multi_media[index].media
						}
						else {
							val request = message.sendEncryptedRequest as TL_messages_sendEncryptedMultiMedia?
							request!!.files[index]
						}

						val location = FileLoader.getInstance(currentAccount).getPathToAttach(message.photoSize).toString()

						putToDelayedMessages(location, message)

						message.extraHashMap!![location] = inputMedia
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

			val req = TL_messages_getStickerSet()
			req.stickerset = message.parentObject as InputStickerSet?

			connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					var found = false

					if (response != null) {
						val set = response as TL_messages_stickerSet
						mediaDataController.storeTempStickerSet(set)
						val attributeSticker = message.locationParent as TL_documentAttributeSticker_layer55?
						attributeSticker!!.stickerset = TL_inputStickerSetShortName()
						attributeSticker.stickerset.short_name = set.set.short_name
						found = true
					}

					val arrayList = delayedMessages.remove(key)

					if (!arrayList.isNullOrEmpty()) {
						if (found) {
							messagesStorage.replaceMessageIfExists(arrayList[0].obj!!.messageOwner, null, null, false)
						}

						secretChatHelper.performSendEncryptedRequest(message.sendEncryptedRequest as DecryptedMessage?, message.obj!!.messageOwner, message.encryptedChat, null, null, message.obj)
					}
				}
			}

			putToDelayedMessages(key, message)
		}
	}

	private fun uploadMultiMedia(message: DelayedMessage?, inputMedia: InputMedia?, inputEncryptedFile: InputEncryptedFile?, key: String?) {
		if (inputMedia != null) {
			val multiMedia = message!!.sendRequest as? TL_messages_sendMultiMedia

			for (a in multiMedia!!.multi_media.indices) {
				if (multiMedia.multi_media[a].media === inputMedia) {
					putToSendingMessages(message.messages!![a], message.scheduled)
					notificationCenter.postNotificationName(NotificationCenter.fileUploadProgressChanged, key, -1L, -1L, false)
					break
				}
			}

			val req = TL_messages_uploadMedia()
			req.media = inputMedia
			req.peer = (message.sendRequest as TL_messages_sendMultiMedia?)!!.peer

			connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					var newInputMedia: InputMedia? = null

					if (response != null) {
						val messageMedia = response as MessageMedia

						if (inputMedia is TL_inputMediaUploadedPhoto && messageMedia is TL_messageMediaPhoto) {
							val inputMediaPhoto = TL_inputMediaPhoto()
							inputMediaPhoto.id = TL_inputPhoto()
							inputMediaPhoto.id.id = messageMedia.photo.id
							inputMediaPhoto.id.access_hash = messageMedia.photo.access_hash
							inputMediaPhoto.id.file_reference = messageMedia.photo.file_reference

							newInputMedia = inputMediaPhoto

							FileLog.d("set uploaded photo")
						}
						else if (inputMedia is TL_inputMediaUploadedDocument && messageMedia is TL_messageMediaDocument) {
							val inputMediaDocument = TL_inputMediaDocument()
							inputMediaDocument.id = TL_inputDocument()
							inputMediaDocument.id.id = messageMedia.document.id
							inputMediaDocument.id.access_hash = messageMedia.document.access_hash
							inputMediaDocument.id.file_reference = messageMedia.document.file_reference

							newInputMedia = inputMediaDocument

							FileLog.d("set uploaded document")
						}
					}

					if (newInputMedia != null) {
						if (inputMedia.ttl_seconds != 0) {
							newInputMedia.ttl_seconds = inputMedia.ttl_seconds
							newInputMedia.flags = newInputMedia.flags or 1
						}

						val req1 = message.sendRequest as? TL_messages_sendMultiMedia

						for (a in req1!!.multi_media.indices) {
							if (req1.multi_media[a].media === inputMedia) {
								req1.multi_media[a].media = newInputMedia
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
		else if (inputEncryptedFile != null) {
			val multiMedia = message!!.sendEncryptedRequest as TL_messages_sendEncryptedMultiMedia?

			for (a in multiMedia!!.files.indices) {
				if (multiMedia.files[a] === inputEncryptedFile) {
					putToSendingMessages(message.messages!![a], message.scheduled)
					notificationCenter.postNotificationName(NotificationCenter.fileUploadProgressChanged, key, -1L, -1L, false)
					break
				}
			}

			sendReadyToSendGroup(message, add = false, check = true)
		}
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

		if (message.sendRequest is TL_messages_sendMultiMedia) {
			val request = message.sendRequest as TL_messages_sendMultiMedia?

			for (a in request!!.multi_media.indices) {
				val inputMedia = request.multi_media[a].media

				if (inputMedia is TL_inputMediaUploadedPhoto || inputMedia is TL_inputMediaUploadedDocument) {
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
			val request = message.sendEncryptedRequest as TL_messages_sendEncryptedMultiMedia?

			for (a in request!!.files.indices) {
				val inputMedia = request.files[a]

				if (inputMedia is TL_inputEncryptedFile) {
					return
				}
			}
		}

		if (message.sendRequest is TL_messages_sendMultiMedia) {
			performSendMessageRequestMulti(message.sendRequest as? TL_messages_sendMultiMedia, message.messageObjects, message.originalPaths, message.parentObjects, message, message.scheduled)
		}
		else {
			secretChatHelper.performSendEncryptedRequest(message.sendEncryptedRequest as TL_messages_sendEncryptedMultiMedia?, message)
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

				sendingMessagesIdDialogs.put(did, sendingMessagesIdDialogs[did, 0] + 1)

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
		for (a in 0 until sendingMessages.size()) {
			val message = sendingMessages.valueAt(a)

			if (message.dialog_id == did) {
				return message.id
			}
		}

		for (a in 0 until uploadMessages.size()) {
			val message = uploadMessages.valueAt(a)

			if (message.dialog_id == did) {
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
			uploadingMessagesIdDialogs.put(did, uploadingMessagesIdDialogs[did, 0] + 1)
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
		return sendingMessagesIdDialogs[did, 0] > 0
	}

	fun isUploadingMessageIdDialog(did: Long): Boolean {
		return uploadingMessagesIdDialogs[did, 0] > 0
	}

	fun performSendMessageRequestMulti(req: TL_messages_sendMultiMedia?, msgObjs: List<MessageObject>?, originalPaths: List<String?>?, parentObjects: List<Any?>?, delayedMessage: DelayedMessage?, scheduled: Boolean) {
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
						@Suppress("NAME_SHADOWING") val size = req.multi_media.size

						while (a < size) {
							if (delayedMessage.parentObjects!![a] == null) {
								a++
								continue
							}

							removeFromSendingMessages(msgObjs[a].id, scheduled)

							val request = req.multi_media[a]

							if (request.media is TL_inputMediaPhoto) {
								request.media = delayedMessage.inputMedias!![a]
							}
							else if (request.media is TL_inputMediaDocument) {
								request.media = delayedMessage.inputMedias!![a]
							}

							delayedMessage.videoEditedInfo = delayedMessage.videoEditedInfos!![a]
							delayedMessage.httpLocation = delayedMessage.httpLocations!![a]
							delayedMessage.photoSize = delayedMessage.locations!![a]
							delayedMessage.performMediaUpload = true

							if (request.media.file == null || delayedMessage.photoSize != null) {
								hasEmptyFile = true
							}

							performSendDelayedMessage(delayedMessage, a)

							a++
						}

						if (!hasEmptyFile) {
							for (i in msgObjs.indices) {
								val newMsgObj = msgObjs[i].messageOwner!!
								messagesStorage.markMessageAsSendError(newMsgObj, scheduled)
								newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
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
					var channelReplies: LongSparseArray<SparseArray<MessageReplies>>? = null

					@Suppress("NAME_SHADOWING") var a = 0

					while (a < updatesArr.size) {
						when (val update = updatesArr[a]) {
							is TL_updateMessageID -> {
								newIds.put(update.random_id, update.id)
								updatesArr.removeAt(a)
								a--
							}

							is TL_updateNewMessage -> {
								newMessages.put(update.message.id, update.message)

								Utilities.stageQueue.postRunnable {
									messagesController.processNewDifferenceParams(-1, update.pts, -1, update.pts_count)
								}

								updatesArr.removeAt(a)

								a--
							}

							is TL_updateNewChannelMessage -> {
								val channelId = MessagesController.getUpdateChannelId(update)
								val chat = messagesController.getChat(channelId)

								if ((chat == null || chat.megagroup) && update.message.reply_to != null && (update.message.reply_to?.reply_to_top_id != 0 || update.message.reply_to?.reply_to_msg_id != 0)) {
									if (channelReplies == null) {
										channelReplies = LongSparseArray()
									}

									val did = MessageObject.getDialogId(update.message)
									var replies = channelReplies[did]

									if (replies == null) {
										replies = SparseArray()
										channelReplies.put(did, replies)
									}

									val id = (if (update.message.reply_to?.reply_to_top_id != 0) update.message.reply_to?.reply_to_top_id else update.message.reply_to?.reply_to_msg_id) ?: 0
									var messageReplies = replies[id]

									if (messageReplies == null) {
										messageReplies = TL_messageReplies()
										replies.put(id, messageReplies)
									}

									if (update.message.from_id != null) {
										messageReplies.recent_repliers.add(0, update.message.from_id)
									}

									messageReplies.replies++
								}

								newMessages.put(update.message.id, update.message)

								Utilities.stageQueue.postRunnable {
									messagesController.processNewChannelDifferenceParams(update.pts, update.pts_count, update.message.peer_id?.channel_id ?: 0)
								}

								updatesArr.removeAt(a)

								a--
							}

							is TL_updateNewScheduledMessage -> {
								newMessages.put(update.message.id, update.message)
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
						val id = newIds[newMsgObj.random_id]

						if (id != null) {
							val message = newMessages[id]

							if (message != null) {
								MessageObject.getDialogId(message)

								sentMessages.add(message)

								if (message.flags and 33554432 != 0) {
									msgObj.messageOwner?.ttl_period = message.ttl_period
									msgObj.messageOwner?.flags = msgObj.messageOwner!!.flags or 33554432
								}

								updateMediaPaths(msgObj, message, message.id, originalPath, false)
								existFlags = msgObj.mediaExistanceFlags
								newMsgObj.id = message.id
								groupedId = message.realGroupId

								if (!scheduled) {
									var value = messagesController.dialogs_read_outbox_max[message.dialog_id]

									if (value == null) {
										value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
										messagesController.dialogs_read_outbox_max[message.dialog_id] = value
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

							newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT

							notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, groupedId, existFlags, scheduled)

							messagesStorage.storageQueue.postRunnable {
								messagesStorage.updateMessageStateAndId(newMsgObj.random_id, MessageObject.getPeerId(newMsgObj.peer_id), oldId, newMsgObj.id, 0, false, if (scheduled) 1 else 0)
								messagesStorage.putMessages(sentMessages, true, false, false, 0, scheduled)

								AndroidUtilities.runOnUIThread {
									mediaDataController.increasePeerRating(newMsgObj.dialog_id)
									notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, groupedId, existFlags, scheduled)
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
						newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
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

		if (req !is TL_messages_editMessage) {
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

		val newMsgObj = msgObj!!.messageOwner
		putToSendingMessages(newMsgObj, scheduled)

		newMsgObj?.reqId = connectionsManager.sendRequest(req, { response, error ->
			if (error != null && (req is TL_messages_sendMedia || req is TL_messages_editMessage) && FileRefController.isFileRefError(error.text)) {
				if (parentObject != null) {
					fileRefController.requestReference(parentObject, req, msgObj, originalPath, parentMessage, check, delayedMessage, scheduled)
					return@sendRequest
				}
				else if (delayedMessage != null) {
					AndroidUtilities.runOnUIThread {
						removeFromSendingMessages(newMsgObj.id, scheduled)

						if (req is TL_messages_sendMedia) {
							if (req.media is TL_inputMediaPhoto) {
								req.media = delayedMessage.inputUploadMedia
							}
							else if (req.media is TL_inputMediaDocument) {
								req.media = delayedMessage.inputUploadMedia
							}
						}
						else if (req is TL_messages_editMessage) {
							if (req.media is TL_inputMediaPhoto) {
								req.media = delayedMessage.inputUploadMedia
							}
							else if (req.media is TL_inputMediaDocument) {
								req.media = delayedMessage.inputUploadMedia
							}
						}

						delayedMessage.performMediaUpload = true

						performSendDelayedMessage(delayedMessage)
					}

					return@sendRequest
				}
			}

			if (req is TL_messages_editMessage) {
				AndroidUtilities.runOnUIThread {
					if (error == null) {
						val attachPath = newMsgObj.attachPath
						val updates = response as Updates
						val updatesArr = response.updates
						var message: Message? = null

						for (a in updatesArr.indices) {
							val update = updatesArr[a]

							if (update is TL_updateEditMessage) {
								message = update.message
								break
							}
							else if (update is TL_updateEditChannelMessage) {
								message = update.message
								break
							}
							else if (update is TL_updateNewScheduledMessage) {
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

						if (response is TL_updateShortSentMessage) {
							updateMediaPaths(msgObj, null, response.id, null, false)

							existFlags = msgObj.mediaExistanceFlags

							newMsgObj.id = response.id
							newMsgObj.local_id = newMsgObj.id
							newMsgObj.date = response.date
							newMsgObj.entities = response.entities
							newMsgObj.out = response.out

							if (response.flags and 33554432 != 0) {
								newMsgObj.ttl_period = response.ttl_period
								newMsgObj.flags = newMsgObj.flags or 33554432
							}

							if (response.media != null) {
								newMsgObj.media = response.media
								newMsgObj.flags = newMsgObj.flags or TLRPC.MESSAGE_FLAG_HAS_MEDIA
								ImageLoader.saveMessageThumbs(newMsgObj)
							}

							if ((response.media is TL_messageMediaGame || response.media is TL_messageMediaInvoice) && !TextUtils.isEmpty(response.message)) {
								newMsgObj.message = response.message
							}

							if (newMsgObj.entities.isNotEmpty()) {
								newMsgObj.flags = newMsgObj.flags or TLRPC.MESSAGE_FLAG_HAS_ENTITIES
							}

							currentSchedule = false

							if (!currentSchedule) {
								var value = messagesController.dialogs_read_outbox_max[newMsgObj.dialog_id]

								if (value == null) {
									value = messagesStorage.getDialogReadMax(newMsgObj.out, newMsgObj.dialog_id)
									messagesController.dialogs_read_outbox_max[newMsgObj.dialog_id] = value
								}

								newMsgObj.unread = value < newMsgObj.id
							}

							Utilities.stageQueue.postRunnable {
								messagesController.processNewDifferenceParams(-1, response.pts, response.date, response.pts_count)
							}

							sentMessages.add(newMsgObj)
						}
						else if (response is Updates) {
							val updatesArr = response.updates
							var message: Message? = null
							var channelReplies: LongSparseArray<SparseArray<MessageReplies>>? = null

							for (a in updatesArr.indices) {
								val update = updatesArr[a]

								if (update is TL_updateNewMessage) {
									sentMessages.add(update.message.also {
										message = it
									})

									Utilities.stageQueue.postRunnable {
										messagesController.processNewDifferenceParams(-1, update.pts, -1, update.pts_count)
									}

									updatesArr.removeAt(a)

									break
								}
								else if (update is TL_updateNewChannelMessage) {
									val channelId = MessagesController.getUpdateChannelId(update)
									val chat = messagesController.getChat(channelId)

									if ((chat == null || chat.megagroup) && update.message.reply_to != null && (update.message.reply_to?.reply_to_top_id != 0 || update.message.reply_to?.reply_to_msg_id != 0)) {
										channelReplies = LongSparseArray()

										val did = MessageObject.getDialogId(update.message)
										var replies = channelReplies[did]

										if (replies == null) {
											replies = SparseArray()
											channelReplies.put(did, replies)
										}

										val id = (if (update.message.reply_to?.reply_to_top_id != 0) update.message.reply_to?.reply_to_top_id else update.message.reply_to?.reply_to_msg_id) ?: 0
										var messageReplies = replies[id]

										if (messageReplies == null) {
											messageReplies = TL_messageReplies()
											replies.put(id, messageReplies)
										}

										if (update.message.from_id != null) {
											messageReplies.recent_repliers.add(0, update.message.from_id)
										}

										messageReplies.replies++
									}

									sentMessages.add(update.message.also {
										message = it
									})

									Utilities.stageQueue.postRunnable {
										messagesController.processNewChannelDifferenceParams(update.pts, update.pts_count, update.message.peer_id?.channel_id ?: 0)
									}

									updatesArr.removeAt(a)

									break
								}
								else if (update is TL_updateNewScheduledMessage) {
									sentMessages.add(update.message.also { message = it })
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

								if (scheduledOnline && message.date != 0x7FFFFFFE) {
									currentSchedule = false
								}

								ImageLoader.saveMessageThumbs(message)

								if (!currentSchedule) {
									var value = messagesController.dialogs_read_outbox_max[message.dialog_id]

									if (value == null) {
										value = messagesStorage.getDialogReadMax(message.out, message.dialog_id)
										messagesController.dialogs_read_outbox_max[message.dialog_id] = value
									}

									message.unread = value < message.id
								}

								msgObj.messageOwner?.post_author = message.post_author

								if (message.flags and 33554432 != 0) {
									msgObj.messageOwner?.ttl_period = message.ttl_period
									msgObj.messageOwner?.flags = msgObj.messageOwner!!.flags or 33554432
								}

								msgObj.messageOwner?.entities = message.entities

								updateMediaPaths(msgObj, message, message.id, originalPath, false)
								existFlags = msgObj.mediaExistanceFlags
								newMsgObj.id = message.id
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

						if (MessageObject.isLiveLocationMessage(newMsgObj) && newMsgObj.via_bot_id == 0L && TextUtils.isEmpty(newMsgObj.via_bot_name)) {
							locationController.addSharingLocation(newMsgObj)
						}

						if (!isSentError) {
							statsController.incrementSentItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_MESSAGES, 1)
							newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT

							if (scheduled && !currentSchedule) {
								messagesController.deleteMessages(listOf(oldId), null, null, newMsgObj.dialog_id, forAll = false, scheduled = true)

								messagesStorage.storageQueue.postRunnable {
									messagesStorage.putMessages(sentMessages, true, false, false, 0, false)

									AndroidUtilities.runOnUIThread {
										messagesController.updateInterfaceWithMessages(newMsgObj.dialog_id, listOf(MessageObject(msgObj.currentAccount, msgObj.messageOwner!!, generateLayout = true, checkMediaExists = true)), false)
										mediaDataController.increasePeerRating(newMsgObj.dialog_id)
										processSentMessage(oldId)
										removeFromSendingMessages(oldId, true)
									}

									if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
										stopVideoService(attachPath)
									}
								}
							}
							else {
								notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, 0L, existFlags, scheduled)

								messagesStorage.storageQueue.postRunnable {
									messagesStorage.updateMessageStateAndId(newMsgObj.random_id, MessageObject.getPeerId(newMsgObj.peer_id), oldId, newMsgObj.id, 0, false, if (scheduled) 1 else 0)
									messagesStorage.putMessages(sentMessages, true, false, false, 0, scheduled)

									AndroidUtilities.runOnUIThread {
										mediaDataController.increasePeerRating(newMsgObj.dialog_id)
										notificationCenter.postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newMsgObj.id, newMsgObj, newMsgObj.dialog_id, 0L, existFlags, scheduled)
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
						newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR
						notificationCenter.postNotificationName(NotificationCenter.messageSendError, newMsgObj.id)
						processSentMessage(newMsgObj.id)

						if (MessageObject.isVideoMessage(newMsgObj) || MessageObject.isRoundVideoMessage(newMsgObj) || MessageObject.isNewGifMessage(newMsgObj)) {
							stopVideoService(newMsgObj.attachPath)
						}

						removeFromSendingMessages(newMsgObj.id, scheduled)
					}

					if (error?.text?.lowercase()?.contains("blocked") == true) {
						newMsgObj.dialog_id.let {
							notificationCenter.postNotificationName(NotificationCenter.chatIsBlocked, abs(it), NotificationCenter.ERROR_CHAT_BLOCKED)
						}
					}
				}
			}
		}, {
			val msgId = newMsgObj.id

			AndroidUtilities.runOnUIThread {
				newMsgObj.send_state = MessageObject.MESSAGE_SEND_STATE_SENT
				notificationCenter.postNotificationName(NotificationCenter.messageReceivedByAck, msgId)
			}
		}, ConnectionsManager.RequestFlagCanCompress or ConnectionsManager.RequestFlagInvokeAfter or if (req is TL_messages_sendMessage) ConnectionsManager.RequestFlagNeedQuickAck else 0)

		parentMessage?.sendDelayedRequests()
	}

	private fun updateMediaPaths(newMsgObj: MessageObject, sentMessage: Message?, newMsgId: Int, originalPath: String?, post: Boolean) {
		val newMsg = newMsgObj.messageOwner
		var strippedNew: PhotoSize? = null

		if (newMsg?.media != null) {
			var strippedOld: PhotoSize? = null
			var photoObject: TLObject? = null

			if (newMsgObj.isLiveLocation && sentMessage!!.media is TL_messageMediaGeoLive) {
				newMsg.media?.period = sentMessage.media?.period ?: 0
			}
			else if (newMsgObj.isDice) {
				val mediaDice = newMsg.media as TL_messageMediaDice
				val mediaDiceNew = sentMessage!!.media as TL_messageMediaDice
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

			if (strippedNew is TL_photoStrippedSize && strippedOld is TL_photoStrippedSize) {
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

		if (sentMessage.media is TL_messageMediaPhoto && sentMessage.media?.photo != null && newMsg?.media is TL_messageMediaPhoto && newMsg.media?.photo != null) {
			if (sentMessage.media?.ttl_seconds == 0 && !newMsgObj.scheduled) {
				messagesStorage.putSentFile(originalPath, sentMessage.media?.photo, 0, "sent_" + sentMessage.peer_id?.channel_id + "_" + sentMessage.id)
			}

			if (newMsg.media?.photo?.sizes?.size == 1 && newMsg.media?.photo?.sizes?.get(0)?.location is TL_fileLocationUnavailable) {
				newMsg.media?.photo?.sizes = sentMessage.media!!.photo.sizes
			}
			else {
				for (b in newMsg.media!!.photo.sizes.indices) {
					val size2 = newMsg.media!!.photo.sizes[b]

					if (size2?.location == null || size2.type == null) {
						continue
					}

					var found = false

					for (a in sentMessage.media!!.photo.sizes.indices) {
						val size = sentMessage.media!!.photo.sizes[a]

						if (size?.location == null || size is TL_photoSizeEmpty || size.type == null) {
							continue
						}

						if (size2.location?.volume_id == Int.MIN_VALUE.toLong() && size.type == size2.type || size.w == size2.w && size.h == size2.h) {
							found = true
							val fileName = size2.location.volume_id.toString() + "_" + size2.location.local_id
							val fileName2 = size.location.volume_id.toString() + "_" + size.location.local_id

							if (fileName == fileName2) {
								break
							}

							val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName.jpg")

							val cacheFile2 = if (sentMessage.media?.ttl_seconds == 0 && (sentMessage.media?.photo?.sizes?.size == 1 || size.w > 90 || size.h > 90)) {
								FileLoader.getInstance(currentAccount).getPathToAttach(size)
							}
							else {
								File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName2.jpg")
							}

							cacheFile.renameTo(cacheFile2)

							ImageLocation.getForPhoto(size, sentMessage.media?.photo)?.let {
								ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, it, post)
							}

							size2.location = size.location
							size2.size = size.size

							break
						}
					}

					if (!found) {
						val fileName = size2.location.volume_id.toString() + "_" + size2.location.local_id
						val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName.jpg")

						cacheFile.delete()

						if ("s" == size2.type && strippedNew != null) {
							newMsg.media?.photo?.sizes?.set(b, strippedNew)

							val location = ImageLocation.getForPhoto(strippedNew, sentMessage.media?.photo)
							val key = location?.getKey(sentMessage, null, false)

							if (location != null && key != null) {
								ImageLoader.getInstance().replaceImageInCache(fileName, key, location, post)
							}
						}
					}
				}
			}

			newMsg.message = sentMessage.message

			sentMessage.attachPath = newMsg.attachPath

			newMsg.media!!.photo.id = sentMessage.media!!.photo.id
			newMsg.media!!.photo.dc_id = sentMessage.media!!.photo.dc_id
			newMsg.media!!.photo.access_hash = sentMessage.media!!.photo.access_hash
		}
		else if (sentMessage.media is TL_messageMediaDocument && sentMessage.media?.document != null && newMsg?.media is TL_messageMediaDocument && newMsg.media?.document != null) {
			if (sentMessage.media!!.ttl_seconds == 0 && (newMsgObj.videoEditedInfo == null || newMsgObj.videoEditedInfo?.mediaEntities == null && newMsgObj.videoEditedInfo?.paintPath.isNullOrEmpty() && newMsgObj.videoEditedInfo?.cropState == null)) {
				val isVideo = MessageObject.isVideoMessage(sentMessage)

				if ((isVideo || MessageObject.isGifMessage(sentMessage)) && MessageObject.isGifDocument(sentMessage.media?.document) == MessageObject.isGifDocument(newMsg.media?.document)) {
					if (!newMsgObj.scheduled) {
						messagesStorage.putSentFile(originalPath, sentMessage.media?.document, 2, "sent_" + sentMessage.peer_id?.channel_id + "_" + sentMessage.id)
					}
					if (isVideo) {
						sentMessage.attachPath = newMsg.attachPath
					}
				}
				else if (!MessageObject.isVoiceMessage(sentMessage) && !MessageObject.isRoundVideoMessage(sentMessage) && !newMsgObj.scheduled) {
					messagesStorage.putSentFile(originalPath, sentMessage.media?.document, 1, "sent_" + sentMessage.peer_id?.channel_id + "_" + sentMessage.id)
				}
			}

			val size2 = FileLoader.getClosestPhotoSizeWithSize(newMsg.media?.document?.thumbs, 320)
			val size = FileLoader.getClosestPhotoSizeWithSize(sentMessage.media?.document?.thumbs, 320)

			if (size2?.location != null && size2.location.volume_id == Int.MIN_VALUE.toLong() && size != null && size.location != null && size !is TL_photoSizeEmpty && size2 !is TL_photoSizeEmpty) {
				val fileName = size2.location.volume_id.toString() + "_" + size2.location.local_id
				val fileName2 = size.location.volume_id.toString() + "_" + size.location.local_id

				if (fileName != fileName2) {
					val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName.jpg")
					val cacheFile2 = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$fileName2.jpg")

					cacheFile.renameTo(cacheFile2)

					ImageLocation.getForDocument(size, sentMessage.media?.document)?.let {
						ImageLoader.getInstance().replaceImageInCache(fileName, fileName2, it, post)
					}

					size2.location = size.location
					size2.size = size.size
				}
			}
			else if (size != null && size2 != null && MessageObject.isStickerMessage(sentMessage) && size2.location != null) {
				size.location = size2.location
			}
			else if (size2 == null || size2.location is TL_fileLocationUnavailable || size2 is TL_photoSizeEmpty) {
				newMsg.media?.document?.thumbs = sentMessage.media?.document?.thumbs
			}

			newMsg.media?.document?.dc_id = sentMessage.media!!.document.dc_id
			newMsg.media?.document?.id = sentMessage.media!!.document.id
			newMsg.media?.document?.access_hash = sentMessage.media!!.document.access_hash

			var oldWaveform: ByteArray? = null

			for (a in newMsg.media!!.document.attributes.indices) {
				val attribute = newMsg.media!!.document.attributes[a]

				if (attribute is TL_documentAttributeAudio) {
					oldWaveform = attribute.waveform
					break
				}
			}

			newMsg.media!!.document.attributes = sentMessage.media!!.document.attributes

			if (oldWaveform != null) {
				for (a in newMsg.media!!.document.attributes.indices) {
					val attribute = newMsg.media!!.document.attributes[a]

					if (attribute is TL_documentAttributeAudio) {
						attribute.waveform = oldWaveform
						attribute.flags = attribute.flags or 4
					}
				}
			}

			newMsg.media!!.document.size = sentMessage.media!!.document.size
			newMsg.media!!.document.mime_type = sentMessage.media!!.document.mime_type

			if (sentMessage.flags and TLRPC.MESSAGE_FLAG_FWD == 0 && MessageObject.isOut(sentMessage)) {
				if (MessageObject.isNewGifDocument(sentMessage.media!!.document)) {
					val save = if (MessageObject.isDocumentHasAttachedStickers(sentMessage.media?.document)) {
						messagesController.saveGifsWithStickers
					}
					else {
						true
					}

					if (save) {
						mediaDataController.addRecentGif(sentMessage.media?.document, sentMessage.date, true)
					}
				}
				else if (MessageObject.isStickerDocument(sentMessage.media?.document) || MessageObject.isAnimatedStickerDocument(sentMessage.media?.document, true)) {
					sentMessage.media?.document?.let {
						mediaDataController.addRecentSticker(MediaDataController.TYPE_IMAGE, sentMessage, it, sentMessage.date, false)
					}
				}
			}

			if (newMsg.attachPath != null && newMsg.attachPath!!.startsWith(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)!!.absolutePath)) {
				val cacheFile = File(newMsg.attachPath!!)
				val cacheFile2 = FileLoader.getInstance(currentAccount).getPathToAttach(sentMessage.media?.document, sentMessage.media?.ttl_seconds != 0)

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
		else if (sentMessage.media is TL_messageMediaContact && newMsg?.media is TL_messageMediaContact) {
			newMsg.media = sentMessage.media
		}
		else if (sentMessage.media is TL_messageMediaWebPage) {
			newMsg?.media = sentMessage.media
		}
		else if (sentMessage.media is TL_messageMediaGeo) {
			sentMessage.media?.geo?.lat = newMsg?.media?.geo?.lat ?: 0.0
			sentMessage.media?.geo?._long = newMsg?.media?.geo?._long ?: 0.0
		}
		else if (sentMessage.media is TL_messageMediaGame || sentMessage.media is TL_messageMediaInvoice) {
			newMsg?.media = sentMessage.media

			if (!sentMessage.message.isNullOrEmpty()) {
				newMsg?.entities = sentMessage.entities
				newMsg?.message = sentMessage.message
			}

			if (sentMessage.reply_markup != null) {
				newMsg?.reply_markup = sentMessage.reply_markup
				newMsg?.flags = newMsg.flags or TLRPC.MESSAGE_FLAG_HAS_MARKUP
			}
		}
		else if (sentMessage.media is TL_messageMediaPoll) {
			newMsg?.media = sentMessage.media
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

	fun getDelayedMessages(location: String?): ArrayList<DelayedMessage>? {
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
					if (a == n - 1 || messages[a + 1].realGroupId != groupId) {
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

	fun generatePhotoSizes(path: String?, imageUri: Uri?): TL_photo? {
		return generatePhotoSizes(null, path, imageUri)
	}

	private fun generatePhotoSizes(photo: TL_photo?, path: String?, imageUri: Uri?): TL_photo? {
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
				photo = TL_photo()
			}

			photo.date = connectionsManager.currentTime
			photo.sizes = sizes
			photo.file_reference = ByteArray(0)
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

		private fun prepareSendingDocumentInternal(accountInstance: AccountInstance, path: String?, originalPath: String?, uri: Uri?, mime: String?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: CharSequence?, entities: List<MessageEntity>?, editingMessageObject: MessageObject?, groupId: LongArray?, isGroupFinal: Boolean, forceDocument: Boolean, notify: Boolean, scheduleDate: Int, docType: Array<Int?>?, isMediaSale: Boolean, mediaSaleHash: String?): Int {
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
			var attributeAudio: TL_documentAttributeAudio? = null
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

			val extL = ext.lowercase(Locale.getDefault())
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
				attributeAudio = TL_documentAttributeAudio()
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

			var document: TL_document? = null
			var parentObject: String? = null

			if (!sendNew && !isEncrypted) {
				var sentData = accountInstance.messagesStorage.getSentFile(originalPath, if (!isEncrypted) 1 else 4)

				if (sentData != null && sentData[0] is TL_document) {
					document = sentData[0] as TL_document
					parentObject = sentData[1] as String
				}

				if (document == null && path != originalPath && !isEncrypted) {
					sentData = accountInstance.messagesStorage.getSentFile(path + f.length(), if (!isEncrypted) 1 else 4)

					if (sentData != null && sentData[0] is TL_document) {
						document = sentData[0] as TL_document
						parentObject = sentData[1] as String
					}
				}

				ensureMediaThumbExists(accountInstance, false, document, path, null, 0)
			}

			if (document == null) {
				document = TL_document()
				document.id = 0
				document.date = accountInstance.connectionsManager.currentTime

				val fileName = TL_documentAttributeFilename()
				fileName.file_name = name

				document.file_reference = ByteArray(0)
				document.attributes.add(fileName)
				document.size = f.length()
				document.dc_id = 0

				if (attributeAudio != null) {
					document.attributes.add(attributeAudio)
				}

				if (ext.isNotEmpty()) {
					when (extL) {
						"webp" -> document.mime_type = "image/webp"
						"opus" -> document.mime_type = "audio/opus"
						"mp3" -> document.mime_type = "audio/mpeg"
						"m4a" -> document.mime_type = "audio/m4a"
						"ogg" -> document.mime_type = "audio/ogg"
						"flac" -> document.mime_type = "audio/flac"
						else -> {
							val mimeType = myMime.getMimeTypeFromExtension(extL)

							if (mimeType != null) {
								document.mime_type = mimeType
							}
							else {
								document.mime_type = "application/octet-stream"
							}
						}
					}
				}
				else {
					document.mime_type = "application/octet-stream"
				}

				if (!forceDocument && document.mime_type == "image/gif" && (editingMessageObject == null || editingMessageObject.groupIdForUse == 0L)) {
					try {
						val bitmap = ImageLoader.loadBitmap(f.absolutePath, null, 90f, 90f, true)

						if (bitmap != null) {
							fileName.file_name = "animation.gif"

							document.attributes.add(TL_documentAttributeAnimated())

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

				if (document.mime_type == "image/webp" && editingMessageObject == null) {
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
						val attributeSticker = TL_documentAttributeSticker()
						attributeSticker.alt = ""
						attributeSticker.stickerset = TL_inputStickerSetEmpty()

						document.attributes.add(attributeSticker)

						val attributeImageSize = TL_documentAttributeImageSize()
						attributeImageSize.w = bmOptions.outWidth
						attributeImageSize.h = bmOptions.outHeight

						document.attributes.add(attributeImageSize)
					}
				}
				else if (document.mime_type?.startsWith("image/") == true && editingMessageObject == null) {
					val (width, height) = getImageDimensions(path)

					if (width != 0 && height != 0) {
						val attributeImageSize = TL_documentAttributeImageSize()
						attributeImageSize.w = width
						attributeImageSize.h = height

						document.attributes.add(attributeImageSize)
					}
				}
			}

			val captionFinal = caption?.toString() ?: ""
			val documentFinal: TL_document = document
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

				if (document.mime_type != null && document.mime_type.lowercase(Locale.getDefault()).startsWith("image/webp")) {
					docType[0] = -1
					isSticker = true
				}
				else if (document.mime_type != null && (document.mime_type.lowercase(Locale.getDefault()).startsWith("image/") || document.mime_type.lowercase(Locale.getDefault()).startsWith("video/mp4")) || MessageObject.canPreviewDocument(document)) {
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
					accountInstance.sendMessagesHelper.sendMessage(documentFinal, null, pathFinal, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, 0, parentFinal, null, false, isMediaSale, mediaSaleHash)
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
		fun prepareSendingDocument(accountInstance: AccountInstance, path: String?, originalPath: String?, uri: Uri?, caption: String?, mine: String?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, inputContent: InputContentInfoCompat?, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
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

			prepareSendingDocuments(accountInstance, paths, originalPaths, uris, caption, mine, dialogId, replyToMsg, replyToTopMsg, inputContent, editingMessageObject, notify, scheduleDate, isMediaSale, mediaSaleHash)
		}

		@JvmStatic
		@UiThread
		fun prepareSendingAudioDocuments(accountInstance: AccountInstance, messageObjects: ArrayList<MessageObject>?, caption: CharSequence?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
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

					var document: TL_document? = null
					var parentObject: String? = null

					if (!isEncrypted) {
						val sentData = accountInstance.messagesStorage.getSentFile(originalPath, 1)

						if (sentData != null && sentData[0] is TL_document) {
							document = sentData[0] as TL_document
							parentObject = sentData[1] as String
							ensureMediaThumbExists(accountInstance, false, document, originalPath, null, 0)
						}
					}

					if (document == null) {
						document = messageObject.messageOwner?.media?.document as? TL_document
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
							accountInstance.sendMessagesHelper.sendMessage(documentFinal, null, messageObject.messageOwner?.attachPath, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, 0, parentFinal, null, false, isMediaSale, mediaSaleHash)
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
					val messagesRes = TL_messages_messages()
					messagesRes.messages.add(prevMessage.messageOwner!!)
					accountInstance.messagesStorage.putMessages(messagesRes, message.peer, -2, 0, false, scheduleDate != 0)
					instance.sendReadyToSendGroup(message, add = true, check = true)
				}
			}
		}

		@JvmStatic
		@UiThread
		fun prepareSendingDocuments(accountInstance: AccountInstance, paths: List<String>?, originalPaths: List<String?>?, uris: List<Uri>?, caption: String?, mime: String?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, inputContent: InputContentInfoCompat?, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
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

						error = prepareSendingDocumentInternal(accountInstance, paths[a], originalPaths?.get(a), null, mime, dialogId, replyToMsg, replyToTopMsg, captionFinal, null, editingMessageObject, groupId, mediaCount == 10 || a == count - 1, inputContent == null, notify, scheduleDate, docType, isMediaSale, mediaSaleHash)

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

						error = prepareSendingDocumentInternal(accountInstance, null, null, uris[a], mime, dialogId, replyToMsg, replyToTopMsg, captionFinal, null, editingMessageObject, groupId, mediaCount == 10 || a == count - 1, inputContent == null, notify, scheduleDate, docType, isMediaSale, mediaSaleHash)

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
		fun prepareSendingPhoto(accountInstance: AccountInstance, imageFilePath: String?, imageUri: Uri?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: CharSequence?, entities: ArrayList<MessageEntity>?, stickers: List<InputDocument>?, inputContent: InputContentInfoCompat?, ttl: Int, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int, isMediaSale: Boolean, mediaSaleHash: String?) {
			prepareSendingPhoto(accountInstance, imageFilePath, null, imageUri, dialogId, replyToMsg, replyToTopMsg, caption, entities, stickers, inputContent, ttl, editingMessageObject, null, notify, scheduleDate, false, isMediaSale, mediaSaleHash)
		}

		@JvmStatic
		@UiThread
		fun prepareSendingPhoto(accountInstance: AccountInstance, imageFilePath: String?, thumbFilePath: String?, imageUri: Uri?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: CharSequence?, entities: List<MessageEntity>?, stickers: List<InputDocument>?, inputContent: InputContentInfoCompat?, ttl: Int, editingMessageObject: MessageObject?, videoEditedInfo: VideoEditedInfo?, notify: Boolean, scheduleDate: Int, forceDocument: Boolean, isMediaSale: Boolean, mediaSaleHash: String?) {
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

			prepareSendingMedia(accountInstance, infos, dialogId, replyToMsg, replyToTopMsg, inputContent, forceDocument, false, editingMessageObject, notify, scheduleDate, false, isMediaSale, mediaSaleHash)
		}

		@JvmStatic
		@UiThread
		fun prepareSendingBotContextResult(fragment: BaseFragment?, accountInstance: AccountInstance, result: BotInlineResult?, params: HashMap<String, String>?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, notify: Boolean, scheduleDate: Int) {
			if (result == null) {
				return
			}

			if (result.send_message is TL_botInlineMessageMediaAuto) {
				Thread(Runnable {
					val isEncrypted = DialogObject.isEncryptedDialog(dialogId)
					var finalPath: String? = null
					var document: TL_document? = null
					var photo: TL_photo? = null
					var game: TL_game? = null

					if ("game" == result.type) {
						if (isEncrypted) {
							return@Runnable   //doesn't work in secret chats for now
						}

						game = TL_game()
						game.title = result.title
						game.description = result.description
						game.short_name = result.id
						game.photo = result.photo

						if (game.photo == null) {
							game.photo = TL_photoEmpty()
						}

						if (result.document is TL_document) {
							game.document = result.document
							game.flags = game.flags or 1
						}
					}
					else if (result is TL_botInlineMediaResult) {
						if (result.document != null) {
							if (result.document is TL_document) {
								document = result.document as TL_document
							}
						}
						else if (result.photo != null) {
							if (result.photo is TL_photo) {
								photo = result.photo as TL_photo
							}
						}
					}
					else if (result.content != null) {
						var ext = ImageLoader.getHttpUrlExtension(result.content.url, null)

						ext = if (TextUtils.isEmpty(ext)) {
							FileLoader.getExtensionByMimeType(result.content.mime_type)
						}
						else {
							".$ext"
						}

						var f = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.content.url) + ext)

						finalPath = if (f.exists()) {
							f.absolutePath
						}
						else {
							result.content.url
						}

						when (result.type) {
							"audio", "voice", "file", "video", "sticker", "gif" -> {
								document = TL_document()
								document.id = 0
								document.size = 0
								document.dc_id = 0
								document.mime_type = result.content.mime_type
								document.file_reference = ByteArray(0)
								document.date = accountInstance.connectionsManager.currentTime

								val fileName = TL_documentAttributeFilename()

								document.attributes.add(fileName)

								when (result.type) {
									"gif" -> {
										fileName.file_name = "animation.gif"

										if (finalPath.endsWith("mp4")) {
											document.mime_type = "video/mp4"
											document.attributes.add(TL_documentAttributeAnimated())
										}
										else {
											document.mime_type = "image/gif"
										}

										try {
											val side = if (isEncrypted) 90 else 320
											var bitmap: Bitmap?

											if (finalPath.endsWith("mp4")) {
												bitmap = createVideoThumbnail(finalPath, MediaStore.Video.Thumbnails.MINI_KIND)

												if (bitmap == null && result.thumb is TL_webDocument && "video/mp4" == result.thumb.mime_type) {
													ext = ImageLoader.getHttpUrlExtension(result.thumb.url, null)

													ext = if (TextUtils.isEmpty(ext)) {
														FileLoader.getExtensionByMimeType(result.thumb.mime_type)
													}
													else {
														".$ext"
													}

													f = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + ext)

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
										val audio = TL_documentAttributeAudio()
										audio.duration = MessageObject.getInlineResultDuration(result)
										audio.voice = true
										fileName.file_name = "audio.ogg"
										document.attributes.add(audio)
									}

									"audio" -> {
										val audio = TL_documentAttributeAudio()
										audio.duration = MessageObject.getInlineResultDuration(result)
										audio.title = result.title
										audio.flags = audio.flags or 1

										if (result.description != null) {
											audio.performer = result.description
											audio.flags = audio.flags or 2
										}

										fileName.file_name = "audio.mp3"

										document.attributes.add(audio)
									}

									"file" -> {
										val idx = result.content.mime_type.lastIndexOf('/')

										if (idx != -1) {
											fileName.file_name = "file." + result.content.mime_type.substring(idx + 1)
										}
										else {
											fileName.file_name = "file"
										}
									}

									"video" -> {
										fileName.file_name = "video.mp4"

										val attributeVideo = TL_documentAttributeVideo()
										val wh = MessageObject.getInlineResultWidthAndHeight(result)

										attributeVideo.w = wh[0]
										attributeVideo.h = wh[1]
										attributeVideo.duration = MessageObject.getInlineResultDuration(result)
										attributeVideo.supports_streaming = true

										document.attributes.add(attributeVideo)

										try {
											if (result.thumb != null) {
												val thumbPath = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb.url, "jpg")).absolutePath
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
										val attributeSticker = TL_documentAttributeSticker()
										attributeSticker.alt = ""
										attributeSticker.stickerset = TL_inputStickerSetEmpty()

										document.attributes.add(attributeSticker)

										val attributeImageSize = TL_documentAttributeImageSize()
										val wh = MessageObject.getInlineResultWidthAndHeight(result)

										attributeImageSize.w = wh[0]
										attributeImageSize.h = wh[1]

										document.attributes.add(attributeImageSize)

										fileName.file_name = "sticker.webp"

										try {
											if (result.thumb != null) {
												val thumbPath = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result.thumb.url) + "." + ImageLoader.getHttpUrlExtension(result.thumb.url, "webp")).absolutePath
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

								if (fileName.file_name == null) {
									fileName.file_name = "file"
								}

								if (document.mime_type == null) {
									document.mime_type = "application/octet-stream"
								}

								if (document.thumbs.isEmpty()) {
									val thumb: PhotoSize = TL_photoSize()
									val wh = MessageObject.getInlineResultWidthAndHeight(result)

									thumb.w = wh[0]
									thumb.h = wh[1]
									thumb.size = 0
									thumb.location = TL_fileLocationUnavailable()
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
									photo = TL_photo()
									photo.date = accountInstance.connectionsManager.currentTime
									photo.file_reference = ByteArray(0)

									val photoSize = TL_photoSize()
									val wh = MessageObject.getInlineResultWidthAndHeight(result)

									photoSize.w = wh[0]
									photoSize.h = wh[1]
									photoSize.size = 1
									photoSize.location = TL_fileLocationUnavailable()
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

					if (params != null && result.content != null) {
						params["originalPath"] = result.content.url
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

					if (sendToPeer != null && sendToPeer.user_id != 0L && accountInstance.messagesController.getUserFull(sendToPeer.user_id) != null && accountInstance.messagesController.getUserFull(sendToPeer.user_id)?.voice_messages_forbidden == true && document != null) {
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
								ImageLoader.getInstance().putImageToCache(BitmapDrawable(ApplicationLoader.applicationContext.resources, precachedThumb[0]), precachedKey[0], false)
							}

							accountInstance.sendMessagesHelper.sendMessage(finalDocument, null, finalPathFinal, dialogId, replyToMsg, replyToTopMsg, result.send_message.message, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, 0, result, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
						}
						else if (finalPhoto != null) {
							accountInstance.sendMessagesHelper.sendMessage(finalPhoto, if (result.content != null) result.content.url else null, dialogId, replyToMsg, replyToTopMsg, result.send_message.message, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, 0, result, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
						}
						else if (finalGame != null) {
							accountInstance.sendMessagesHelper.sendMessage(finalGame, dialogId, result.send_message.reply_markup, params, notify, scheduleDate, false, null)
						}
					}
				}).start()
			}
			else if (result.send_message is TL_botInlineMessageText) {
				var webPage: WebPage? = null

				if (DialogObject.isEncryptedDialog(dialogId)) {
					for (a in result.send_message.entities.indices) {
						val entity = result.send_message.entities[a]

						if (entity is TL_messageEntityUrl) {
							webPage = TL_webPagePending()
							webPage.url = result.send_message.message.substring(entity.offset, entity.offset + entity.length)
							break
						}
					}
				}

				accountInstance.sendMessagesHelper.sendMessage(result.send_message.message, dialogId, replyToMsg, replyToTopMsg, webPage, !result.send_message.no_webpage, result.send_message.entities, result.send_message.reply_markup, params, notify, scheduleDate, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
			}
			else if (result.send_message is TL_botInlineMessageMediaVenue) {
				val venue = TL_messageMediaVenue()
				venue.geo = result.send_message.geo
				venue.address = result.send_message.address
				venue.title = result.send_message.title
				venue.provider = result.send_message.provider
				venue.venue_id = result.send_message.venue_id
				venue.venue_id = result.send_message.venue_type
				venue.venue_type = venue.venue_id

				if (venue.venue_type == null) {
					venue.venue_type = ""
				}

				accountInstance.sendMessagesHelper.sendMessage(venue, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, false, null)
			}
			else if (result.send_message is TL_botInlineMessageMediaGeo) {
				if (result.send_message.period != 0 || result.send_message.proximity_notification_radius != 0) {
					val location = TL_messageMediaGeoLive()
					location.period = if (result.send_message.period != 0) result.send_message.period else 900
					location.geo = result.send_message.geo
					location.heading = result.send_message.heading
					location.proximity_notification_radius = result.send_message.proximity_notification_radius

					accountInstance.sendMessagesHelper.sendMessage(location, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, false, null)
				}
				else {
					val location = TL_messageMediaGeo()
					location.geo = result.send_message.geo
					location.heading = result.send_message.heading

					accountInstance.sendMessagesHelper.sendMessage(location, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, false, null)
				}
			}
			else if (result.send_message is TL_botInlineMessageMediaContact) {
				val user: User = TL_user()
				user.first_name = result.send_message.first_name
				user.last_name = result.send_message.last_name
				user.username = result.send_message.phone_number

				val reason = TL_restrictionReason()
				reason.text = result.send_message.vcard
				reason.platform = ""
				reason.reason = ""

				user.restriction_reason.add(reason)

				accountInstance.sendMessagesHelper.sendMessage(user, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, false, null)
			}
			else if (result.send_message is TL_botInlineMessageMediaInvoice) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					return  //doesn't work in secret chats for now
				}

				val invoice = result.send_message as TL_botInlineMessageMediaInvoice

				val messageMediaInvoice = TL_messageMediaInvoice()
				messageMediaInvoice.shipping_address_requested = invoice.shipping_address_requested
				messageMediaInvoice.test = invoice.test
				messageMediaInvoice.title = invoice.title
				messageMediaInvoice.description = invoice.description

				if (invoice.photo != null) {
					messageMediaInvoice.photo = invoice.photo
					messageMediaInvoice.flags = messageMediaInvoice.flags or 1
				}

				messageMediaInvoice.currency = invoice.currency
				messageMediaInvoice.total_amount = invoice.total_amount
				messageMediaInvoice.start_param = ""

				accountInstance.sendMessagesHelper.sendMessage(messageMediaInvoice, dialogId, replyToMsg, replyToTopMsg, result.send_message.reply_markup, params, notify, scheduleDate, false, null)
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
								accountInstance.sendMessagesHelper.sendMessage(mess, dialogId, null, null, null, true, null, null, null, notify, scheduleDate, null, updateStickersOrder = false, isMediaSale = false, mediaSaleHash = null)
							}
						}
					}
				}
			}
		}

		fun ensureMediaThumbExists(accountInstance: AccountInstance, isEncrypted: Boolean, `object`: TLObject?, path: String?, uri: Uri?, startTime: Long) {
			if (`object` is TL_photo) {
				val smallExists: Boolean
				val smallSize = FileLoader.getClosestPhotoSizeWithSize(`object`.sizes, 90)

				smallExists = if (smallSize is TL_photoStrippedSize || smallSize is TL_photoPathSize) {
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
						val size = ImageLoader.scaleAndSaveImage(bigSize, bitmap, Bitmap.CompressFormat.JPEG, true, AndroidUtilities.getPhotoSize().toFloat(), AndroidUtilities.getPhotoSize().toFloat(), 80, false, 101, 101, false)

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
			else if (`object` is TL_document) {
				if ((MessageObject.isVideoDocument(`object`) || MessageObject.isNewGifDocument(`object`)) && MessageObject.isDocumentHasThumb(`object`)) {
					val photoSize = FileLoader.getClosestPhotoSizeWithSize(`object`.thumbs, 320)

					if (photoSize is TL_photoStrippedSize || photoSize is TL_photoPathSize) {
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

			return String.format(Locale.US, if (blur) "%d_%d@%d_%d_b" else "%d_%d@%d_%d", photoSize.location.volume_id, photoSize.location.local_id, (point.x / AndroidUtilities.density).toInt(), (point.y / AndroidUtilities.density).toInt())
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
		fun prepareSendingMedia(accountInstance: AccountInstance, media: List<SendingMediaInfo>, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, inputContent: InputContentInfoCompat?, forceDocument: Boolean, groupMedia: Boolean, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int, updateStickersOrder: Boolean, isMediaSale: Boolean, mediaSaleHash: String?) {
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

							var photo: TL_photo? = null
							var parentObject: String? = null

							if (!isEncrypted && info.ttl == 0) {
								var sentData = accountInstance.messagesStorage.getSentFile(originalPath, if (!isEncrypted) 0 else 3)

								if (sentData != null && sentData[0] is TL_photo) {
									photo = sentData[0] as TL_photo
									parentObject = sentData[1] as String
								}
								if (photo == null && info.uri != null) {
									sentData = accountInstance.messagesStorage.getSentFile(AndroidUtilities.getPath(info.uri), if (!isEncrypted) 0 else 3)

									if (sentData != null && sentData[0] is TL_photo) {
										photo = sentData[0] as TL_photo
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
							var document: TL_document? = null
							val parentObject: String? = null
							var cacheFile: File?

							if (info.searchImage!!.document is TL_document) {
								document = info.searchImage!!.document as TL_document
								cacheFile = FileLoader.getInstance(accountInstance.currentAccount).getPathToAttach(document, true)
							}
							else {/*if (!isEncrypted) {
                                Object[] sentData = getMessagesStorage().getSentFile(info.searchImage.imageUrl, !isEncrypted ? 1 : 4);
                                if (sentData != null && sentData[0] instanceof TLRPC.TL_document) {
                                    document = (TLRPC.TL_document) sentData[0];
                                    parentObject = (String) sentData[1];
                                }
                            }*/
								val md5 = Utilities.MD5(info.searchImage!!.imageUrl) + "." + ImageLoader.getHttpUrlExtension(info.searchImage!!.imageUrl, "jpg")
								cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), md5)
							}

							if (document == null) {
								var thumbFile: File? = null

								document = TL_document()
								document.id = 0
								document.file_reference = ByteArray(0)
								document.date = accountInstance.connectionsManager.currentTime

								val fileName = TL_documentAttributeFilename()
								fileName.file_name = "animation.gif"

								document.attributes.add(fileName)
								document.size = info.searchImage!!.size.toLong()
								document.dc_id = 0

								if (!forceDocument && cacheFile.toString().endsWith("mp4")) {
									document.mime_type = "video/mp4"
									document.attributes.add(TL_documentAttributeAnimated())
								}
								else {
									document.mime_type = "image/gif"
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
									val thumb = TL_photoSize()
									thumb.w = info.searchImage!!.width
									thumb.h = info.searchImage!!.height
									thumb.size = 0
									thumb.location = TL_fileLocationUnavailable()
									thumb.type = "x"

									document.thumbs.add(thumb)
									document.flags = document.flags or 1
								}
							}

							val documentFinal: TL_document = document
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
									accountInstance.sendMessagesHelper.sendMessage(documentFinal, null, pathFinal, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, 0, parentObject, null, false, isMediaSale, mediaSaleHash)
								}
							}
						}
						else {
							var needDownloadHttp = true
							var photo: TL_photo? = null
							val parentObject: String? = null

							if (info.searchImage!!.photo is TL_photo) {
								photo = info.searchImage!!.photo as TL_photo
							}
//							else {
//								if (!isEncrypted && info.ttl == 0) {/*Object[] sentData = getMessagesStorage().getSentFile(info.searchImage.imageUrl, !isEncrypted ? 0 : 3);
//                                if (sentData != null) {
//                                    photo = (TLRPC.TL_photo) sentData[0];
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
										photo = TL_photo()
										photo.date = accountInstance.connectionsManager.currentTime
										photo.file_reference = ByteArray(0)

										val photoSize = TL_photoSize()
										photoSize.w = info.searchImage!!.width
										photoSize.h = info.searchImage!!.height
										photoSize.size = 0
										photoSize.location = TL_fileLocationUnavailable()
										photoSize.type = "x"

										photo.sizes.add(photoSize)
									}
								}
							}

							val photoFinal: TL_photo = photo
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
									accountInstance.sendMessagesHelper.sendMessage(photoFinal, if (needDownloadHttpFinal) info.searchImage!!.imageUrl else null, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentObject, false, isMediaSale, mediaSaleHash)
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
									if (info.searchImage!!.photo is TL_photo) {
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

								var document: TL_document? = null
								var parentObject: String? = null

								if (!isEncrypted && info.ttl == 0 && (videoEditedInfo == null || videoEditedInfo.filterState == null && videoEditedInfo.paintPath == null && videoEditedInfo.mediaEntities == null && videoEditedInfo.cropState == null)) {
									val sentData = accountInstance.messagesStorage.getSentFile(originalPath, if (!isEncrypted) 2 else 5)

									if (sentData != null && sentData[0] is TL_document) {
										document = sentData[0] as TL_document
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

										val fileName = size?.location?.volume_id?.toString() + "_" + size?.location?.local_id + ".jpg"
										val fileDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)

										localPath = File(fileDir, fileName).absolutePath
									}

									document = TL_document()
									document.file_reference = ByteArray(0)
									document.localPath = localPath

									if (size != null) {
										document.thumbs.add(size)
										document.flags = document.flags or 1
									}

									document.mime_type = "video/mp4"

									accountInstance.userConfig.saveConfig(false)

									var attributeVideo: TL_documentAttributeVideo

									if (isEncrypted) {
										attributeVideo = TL_documentAttributeVideo()
									}
									else {
										attributeVideo = TL_documentAttributeVideo()
										attributeVideo.supports_streaming = true
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
									if (document.attributes.find { it is TL_documentAttributeAnimated } == null) {
										document.attributes.add(TL_documentAttributeAnimated())
									}
								}

								if (videoEditedInfo != null && (videoEditedInfo.needConvert() || !info.isVideo)) {
									val fileName = Int.MIN_VALUE.toString() + "_" + SharedConfig.getLastLocalId() + ".mp4"
									val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName)

									SharedConfig.saveConfig()

									path = cacheFile.absolutePath
								}

								val videoFinal: TL_document = document
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
									document.attributes.add(TL_documentAttributeHasStickers())

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
										ImageLoader.getInstance().putImageToCache(BitmapDrawable(ApplicationLoader.applicationContext.resources, thumbFinal), thumbKeyFinal, false)
									}

									if (editingMessageObject != null) {
										accountInstance.sendMessagesHelper.editMessage(editingMessageObject, null, videoEditedInfo, videoFinal, finalPath, params, false, parentFinal)
									}
									else {
										accountInstance.sendMessagesHelper.sendMessage(videoFinal, videoEditedInfo, finalPath, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentFinal, null, false, isMediaSale, mediaSaleHash)
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

								var photo: TL_photo? = null
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

										if (sentData != null && sentData[0] is TL_photo) {
											photo = sentData[0] as TL_photo
											parentObject = sentData[1] as String
										}

										if (photo == null && info.uri != null) {
											sentData = accountInstance.messagesStorage.getSentFile(AndroidUtilities.getPath(info.uri), if (!isEncrypted) 0 else 3)

											if (sentData != null && sentData[0] is TL_photo) {
												photo = sentData[0] as TL_photo
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
									val photoFinal: TL_photo = photo
									val parentFinal = parentObject
									val params = HashMap<String, String>()
									val bitmapFinal = arrayOfNulls<Bitmap>(1)
									val keyFinal = arrayOfNulls<String>(1)

									if (info.masks != null && !info.masks!!.isEmpty().also { photo.has_stickers = it }) {
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
											ImageLoader.getInstance().putImageToCache(BitmapDrawable(ApplicationLoader.applicationContext.resources, bitmapFinal[0]), keyFinal[0], false)
										}

										if (editingMessageObject != null) {
											accountInstance.sendMessagesHelper.editMessage(editingMessageObject, photoFinal, null, null, null, params, false, parentFinal)
										}
										else {
											accountInstance.sendMessagesHelper.sendMessage(photoFinal, null, dialogId, replyToMsg, replyToTopMsg, info.caption, info.entities, null, params, notify, scheduleDate, info.ttl, parentFinal, updateStickersOrder, isMediaSale, mediaSaleHash)
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

						val error = prepareSendingDocumentInternal(accountInstance, sendAsDocuments[a], sendAsDocumentsOriginal!![a], sendAsDocumentsUri!![a], extension, dialogId, replyToMsg, replyToTopMsg, sendAsDocumentsCaptions!![a], sendAsDocumentsEntities!![a], editingMessageObject, groupId2, mediaCount == 10 || a == documentsCount - 1, forceDocument, notify, scheduleDate, null, isMediaSale, mediaSaleHash)

						handleError(error, accountInstance)
					}
				}

				FileLog.d("total send time = " + (System.currentTimeMillis() - beginTime))
			}
		}

		private fun fillVideoAttribute(videoPath: String, attributeVideo: TL_documentAttributeVideo, videoEditedInfo: VideoEditedInfo?) {
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

					bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
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
		fun prepareSendingVideo(accountInstance: AccountInstance, videoPath: String?, info: VideoEditedInfo?, dialogId: Long, replyToMsg: MessageObject?, replyToTopMsg: MessageObject?, caption: CharSequence?, entities: List<MessageEntity>?, ttl: Int, editingMessageObject: MessageObject?, notify: Boolean, scheduleDate: Int, forceDocument: Boolean, isMediaSale: Boolean, mediaSaleHash: String?) {
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

					var document: TL_document? = null
					var parentObject: String? = null

					if (!isEncrypted && ttl == 0 && (videoEditedInfo == null || videoEditedInfo.filterState == null && videoEditedInfo.paintPath == null && videoEditedInfo.mediaEntities == null && videoEditedInfo.cropState == null)) {
						val sentData = accountInstance.messagesStorage.getSentFile(originalPath, 2)

						if (sentData != null && sentData[0] is TL_document) {
							document = sentData[0] as TL_document
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
									thumb = Bitmap.createScaledBitmap(thumb, 90, 90, true)

									Utilities.blurBitmap(thumb, 7, 1, thumb.width, thumb.height, thumb.rowBytes)
									Utilities.blurBitmap(thumb, 7, 1, thumb.width, thumb.height, thumb.rowBytes)
									Utilities.blurBitmap(thumb, 7, 1, thumb.width, thumb.height, thumb.rowBytes)

									thumbKey = String.format(size.location.volume_id.toString() + "_" + size.location.local_id + "@%d_%d_b2", (AndroidUtilities.roundMessageSize / AndroidUtilities.density).toInt(), (AndroidUtilities.roundMessageSize / AndroidUtilities.density).toInt())
								}
								else {
									Utilities.blurBitmap(thumb, 3, 1, thumb.width, thumb.height, thumb.rowBytes)
									thumbKey = String.format(size.location.volume_id.toString() + "_" + size.location.local_id + "@%d_%d_b", (AndroidUtilities.roundMessageSize / AndroidUtilities.density).toInt(), (AndroidUtilities.roundMessageSize / AndroidUtilities.density).toInt())
								}
							}
							else {
								thumb = null
							}
						}

						document = TL_document()

						if (size != null) {
							document.thumbs.add(size)
							document.flags = document.flags or 1
						}

						document.file_reference = ByteArray(0)
						document.mime_type = "video/mp4"

						accountInstance.userConfig.saveConfig(false)

						val attributeVideo: TL_documentAttributeVideo

						if (isEncrypted) {
							val encryptedChatId = DialogObject.getEncryptedChatId(dialogId)
							accountInstance.messagesController.getEncryptedChat(encryptedChatId) ?: return@Runnable
							attributeVideo = TL_documentAttributeVideo()
						}
						else {
							attributeVideo = TL_documentAttributeVideo()
							attributeVideo.supports_streaming = true
						}

						attributeVideo.round_message = isRound

						document.attributes.add(attributeVideo)

						if (videoEditedInfo != null && videoEditedInfo.needConvert()) {
							if (videoEditedInfo.muted) {
								document.attributes.add(TL_documentAttributeAnimated())

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

					val videoFinal: TL_document = document
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
							ImageLoader.getInstance().putImageToCache(BitmapDrawable(ApplicationLoader.applicationContext.resources, thumbFinal), thumbKeyFinal, false)
						}

						if (editingMessageObject != null) {
							accountInstance.sendMessagesHelper.editMessage(editingMessageObject, null, videoEditedInfo, videoFinal, finalPath, params, false, parentFinal)
						}
						else {
							accountInstance.sendMessagesHelper.sendMessage(videoFinal, videoEditedInfo, finalPath, dialogId, replyToMsg, replyToTopMsg, captionFinal, entities, null, params, notify, scheduleDate, ttl, parentFinal, null, false, isMediaSale, mediaSaleHash)
						}
					}
				}
				else {
					prepareSendingDocumentInternal(accountInstance, videoPath, videoPath, null, null, dialogId, replyToMsg, replyToTopMsg, caption, entities, editingMessageObject, null, false, forceDocument, notify, scheduleDate, null, isMediaSale, mediaSaleHash)
				}
			}).start()
		}
	}
}
