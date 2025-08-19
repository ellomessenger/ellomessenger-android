/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.util.Property
import android.view.Gravity
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withSave
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DocumentObject.getSvgThumb
import org.telegram.messenger.DownloadController
import org.telegram.messenger.DownloadController.FileDownloadProgressListener
import org.telegram.messenger.Emoji.replaceEmoji
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLoader.Companion.getAttachFileName
import org.telegram.messenger.FileLoader.Companion.getClosestPhotoSizeWithSize
import org.telegram.messenger.FileLoader.Companion.getDirectory
import org.telegram.messenger.FileLoader.Companion.getMimeTypePart
import org.telegram.messenger.FileLog.e
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation.Companion.getForDocument
import org.telegram.messenger.ImageLocation.Companion.getForPath
import org.telegram.messenger.ImageLocation.Companion.getForPhoto
import org.telegram.messenger.ImageLocation.Companion.getForWebFile
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.WebFile
import org.telegram.messenger.WebFile.Companion.createWithGeoPoint
import org.telegram.messenger.WebFile.Companion.createWithWebDocument
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.messageobject.MessageObject.Companion.canAutoplayAnimatedSticker
import org.telegram.messenger.messageobject.MessageObject.Companion.getDocumentVideoThumb
import org.telegram.messenger.messageobject.MessageObject.Companion.getInlineResultDuration
import org.telegram.messenger.messageobject.MessageObject.Companion.getInlineResultWidthAndHeight
import org.telegram.messenger.messageobject.MessageObject.Companion.isAnimatedStickerDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isGifDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isMusicDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isStickerDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isVoiceDocument
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInlineResult
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.TLWebDocument
import org.telegram.tgnet.content
import org.telegram.tgnet.document
import org.telegram.tgnet.lat
import org.telegram.tgnet.lon
import org.telegram.tgnet.photo
import org.telegram.tgnet.sizes
import org.telegram.tgnet.thumb
import org.telegram.tgnet.thumbs
import org.telegram.tgnet.url
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell.Companion.generateStaticLayout
import org.telegram.ui.Components.AnimationProperties
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LetterDrawable
import org.telegram.ui.Components.MediaActionDrawable
import org.telegram.ui.Components.RadialProgress2
import org.telegram.ui.PhotoViewer
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.withTranslation

class ContextLinkCell @JvmOverloads constructor(context: Context, needsCheckBox: Boolean = false) : FrameLayout(context), FileDownloadProgressListener {
	fun interface ContextLinkCellDelegate {
		fun didPressedImage(cell: ContextLinkCell?)
	}

	val photoImage: ImageReceiver = ImageReceiver(this)
	private var drawLinkImageView = false
	private val letterDrawable: LetterDrawable
	private val currentAccount = UserConfig.selectedAccount
	var parentObject: Any? = null
		private set

	private var needDivider = false
	private var buttonPressed = false
	private var needShadow = false

	var isCanPreviewGif: Boolean = false

	private var isForceGif = false

	private var linkY = 0
	private var linkLayout: StaticLayout? = null

	private val titleY = AndroidUtilities.dp(7f)
	private var titleLayout: StaticLayout? = null

	private val descriptionY = AndroidUtilities.dp(27f)
	private var descriptionLayout: StaticLayout? = null

	var result: BotInlineResult? = null
		private set

	var inlineBot: TLRPC.User? = null
		private set
	var document: TLRPC.Document? = null
		private set
	var date: Int = 0
		private set

	private var photoAttach: TLRPC.Photo? = null
	private var currentPhotoObject: PhotoSize? = null
	private var documentAttachType = 0
	private var mediaWebpage = false

	var messageObject: MessageObject? = null
		private set

	private var animator: AnimatorSet? = null

	private var backgroundPaint: Paint? = null

	private val TAG: Int
	private var buttonState = 0
	private val radialProgress: RadialProgress2

	private var lastUpdateTime: Long = 0
	private var scaled = false
	private var scale = 0f

	private var checkBox: CheckBox2? = null

	private var delegate: ContextLinkCellDelegate? = null

	@SuppressLint("DrawAllocation")
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val result = result
		val document = document

		drawLinkImageView = false
		descriptionLayout = null
		titleLayout = null
		linkLayout = null
		currentPhotoObject = null
		linkY = AndroidUtilities.dp(27f)

		if (result == null && document == null) {
			setMeasuredDimension(AndroidUtilities.dp(100f), AndroidUtilities.dp(100f))
			return
		}

		val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
		val maxWidth = viewWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat()) - AndroidUtilities.dp(8f)

		var currentPhotoObjectThumb: PhotoSize? = null
		var photoThumbs: List<PhotoSize?>? = null
		var webFile: WebFile? = null
		var webDocument: TLWebDocument? = null
		var urlLocation: String? = null

		if (document != null) {
			photoThumbs = document.thumbs?.toList()
		}
		else if (result?.photo != null) {
			photoThumbs = result.photo?.sizes?.toList()
		}

		if (!mediaWebpage && result != null) {
			if (result.title != null) {
				try {
					val width = ceil(Theme.chat_contextResult_titleTextPaint.measureText(result.title).toDouble()).toInt()
					val titleFinal = TextUtils.ellipsize(replaceEmoji(result.title!!.replace('\n', ' '), Theme.chat_contextResult_titleTextPaint.fontMetricsInt, false), Theme.chat_contextResult_titleTextPaint, min(width.toDouble(), maxWidth.toDouble()).toFloat(), TextUtils.TruncateAt.END)
					titleLayout = StaticLayout(titleFinal, Theme.chat_contextResult_titleTextPaint, maxWidth + AndroidUtilities.dp(4f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				}
				catch (e: Exception) {
					e(e)
				}
				letterDrawable.setTitle(result.title)
			}

			if (result.description != null) {
				try {
					descriptionLayout = generateStaticLayout(replaceEmoji(result.description, Theme.chat_contextResult_descriptionTextPaint.fontMetricsInt, false)!!, Theme.chat_contextResult_descriptionTextPaint, maxWidth, maxWidth, 0, 3)
					if (descriptionLayout!!.lineCount > 0) {
						linkY = descriptionY + descriptionLayout!!.getLineBottom(descriptionLayout!!.lineCount - 1) + AndroidUtilities.dp(1f)
					}
				}
				catch (e: Exception) {
					e(e)
				}
			}

			val resultUrl = result.url

			if (resultUrl != null) {
				try {
					val width = ceil(Theme.chat_contextResult_descriptionTextPaint.measureText(resultUrl).toDouble()).toInt()
					val linkFinal = TextUtils.ellipsize(resultUrl.replace('\n', ' '), Theme.chat_contextResult_descriptionTextPaint, min(width.toDouble(), maxWidth.toDouble()).toFloat(), TextUtils.TruncateAt.MIDDLE)
					linkLayout = StaticLayout(linkFinal, Theme.chat_contextResult_descriptionTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				}
				catch (e: Exception) {
					e(e)
				}
			}
		}

		var ext: String? = null

		if (document != null) {
			if (isForceGif || isGifDocument(document)) {
				currentPhotoObject = getClosestPhotoSizeWithSize(document.thumbs, 90)
			}
			else if (isStickerDocument(document) || isAnimatedStickerDocument(document, true)) {
				currentPhotoObject = getClosestPhotoSizeWithSize(document.thumbs, 90)
				ext = "webp"
			}
			else {
				if (documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO) {
					currentPhotoObject = getClosestPhotoSizeWithSize(document.thumbs, 90)
				}
			}
		}
		else if (result?.photo != null) {
			currentPhotoObject = getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize(), true)
			currentPhotoObjectThumb = getClosestPhotoSizeWithSize(photoThumbs, 80)

			if (currentPhotoObjectThumb === currentPhotoObject) {
				currentPhotoObjectThumb = null
			}
		}

		if (result != null) {
			if (result.content is TLWebDocument) {
				if (result.type != null) {
					if (result.type!!.startsWith("gif")) {
						webDocument = if (result.thumb is TLWebDocument && "video/mp4" == result.thumb?.mimeType) {
							result.thumb as TLWebDocument?
						}
						else {
							result.content as TLWebDocument?
						}
						documentAttachType = DOCUMENT_ATTACH_TYPE_GIF
					}
					else if (result.type == "photo") {
						webDocument = if (result.thumb is TLWebDocument) {
							result.thumb as TLWebDocument?
						}
						else {
							result.content as TLWebDocument?
						}
					}
				}
			}
			if (webDocument == null && (result.thumb is TLWebDocument)) {
				webDocument = result.thumb as TLWebDocument?
			}
			if (webDocument == null && currentPhotoObject == null && currentPhotoObjectThumb == null) {
				if (result.sendMessage is TLRPC.TLBotInlineMessageMediaVenue || result.sendMessage is TLRPC.TLBotInlineMessageMediaGeo) {
					val lat = result.sendMessage?.geo?.lat ?: 0.0
					val lon = result.sendMessage?.geo?.lon ?: 0.0

					if (MessagesController.getInstance(currentAccount).mapProvider == MessagesController.MAP_PROVIDER_ELLO) {
						webFile = createWithGeoPoint(result.sendMessage?.geo, 72, 72, 15, min(2.0, ceil(AndroidUtilities.density.toDouble()).toInt().toDouble()).toInt())
					}
					else {
						urlLocation = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, 72, 72, true, 15, -1)
					}
				}
			}
			if (webDocument != null) {
				webFile = createWithWebDocument(webDocument)
			}
		}

		var width: Int
		var w = 0
		var h = 0

		if (document is TLRPC.TLDocument) {
			for (attribute in document.attributes) {
				if (attribute is TLRPC.TLDocumentAttributeImageSize || attribute is TLRPC.TLDocumentAttributeVideo) {
					w = attribute.w
					h = attribute.h
					break
				}
			}
		}
		if (w == 0 || h == 0) {
			if (currentPhotoObject != null) {
				currentPhotoObjectThumb?.size = -1

				w = currentPhotoObject!!.w
				h = currentPhotoObject!!.h
			}
			else if (result != null) {
				val result = getInlineResultWidthAndHeight(result)
				w = result[0]
				h = result[1]
			}
		}
		if (w == 0 || h == 0) {
			h = AndroidUtilities.dp(80f)
			w = h
		}
		if (document != null || currentPhotoObject != null || webFile != null || urlLocation != null) {
			val currentPhotoFilter: String
			var currentPhotoFilterThumb = "52_52_b"

			if (mediaWebpage) {
				width = (w / (h / AndroidUtilities.dp(80f).toFloat())).toInt()
				if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
					currentPhotoFilter = String.format(Locale.US, "%d_%d_b", (width / AndroidUtilities.density).toInt(), 80)
					currentPhotoFilterThumb = currentPhotoFilter
				}
				else {
					currentPhotoFilter = String.format(Locale.US, "%d_%d", (width / AndroidUtilities.density).toInt(), 80)
					currentPhotoFilterThumb = currentPhotoFilter + "_b"
				}
			}
			else {
				currentPhotoFilter = "52_52"
			}
			photoImage.isAspectFit = documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
				if (document != null) {
					val thumb = getDocumentVideoThumb(document)
					if (thumb != null) {
						photoImage.setImage(getForDocument(thumb, document), "100_100", getForDocument(currentPhotoObject, document), currentPhotoFilter, -1, ext, parentObject, 1)
					}
					else {
						val location = getForDocument(document)
						if (isForceGif) {
							location!!.imageType = FileLoader.IMAGE_TYPE_ANIMATION
						}
						photoImage.setImage(location, "100_100", getForDocument(currentPhotoObject, document), currentPhotoFilter, document.size, ext, parentObject, 0)
					}
				}
				else if (webFile != null) {
					photoImage.setImage(getForWebFile(webFile), "100_100", getForPhoto(currentPhotoObject, photoAttach), currentPhotoFilter, -1, ext, parentObject, 1)
				}
				else {
					photoImage.setImage(getForPath(urlLocation), "100_100", getForPhoto(currentPhotoObject, photoAttach), currentPhotoFilter, -1, ext, parentObject, 1)
				}
			}
			else {
				if (currentPhotoObject != null) {
					val svgThumb = getSvgThumb(document, ResourcesCompat.getColor(context.resources, R.color.light_background, null), 1.0f)
					if (canAutoplayAnimatedSticker(document)) {
						if (svgThumb != null) {
							photoImage.setImage(getForDocument(document), "80_80", svgThumb, currentPhotoObject?.size?.toLong() ?: 0L, ext, parentObject, 0)
						}
						else {
							photoImage.setImage(getForDocument(document), "80_80", getForDocument(currentPhotoObject, document), currentPhotoFilterThumb, currentPhotoObject?.size?.toLong() ?: 0L, ext, parentObject, 0)
						}
					}
					else {
						if (document != null) {
							if (svgThumb != null) {
								photoImage.setImage(getForDocument(currentPhotoObject, document), currentPhotoFilter, svgThumb, currentPhotoObject?.size?.toLong() ?: 0L, ext, parentObject, 0)
							}
							else {
								photoImage.setImage(getForDocument(currentPhotoObject, document), currentPhotoFilter, getForPhoto(currentPhotoObjectThumb, photoAttach), currentPhotoFilterThumb, currentPhotoObject?.size?.toLong() ?: 0L, ext, parentObject, 0)
							}
						}
						else {
							photoImage.setImage(getForPhoto(currentPhotoObject, photoAttach), currentPhotoFilter, getForPhoto(currentPhotoObjectThumb, photoAttach), currentPhotoFilterThumb, currentPhotoObject?.size?.toLong() ?: 0L, ext, parentObject, 0)
						}
					}
				}
				else if (webFile != null) {
					photoImage.setImage(getForWebFile(webFile), currentPhotoFilter, getForPhoto(currentPhotoObjectThumb, photoAttach), currentPhotoFilterThumb, -1, ext, parentObject, 1)
				}
				else {
					photoImage.setImage(getForPath(urlLocation), currentPhotoFilter, getForPhoto(currentPhotoObjectThumb, photoAttach), currentPhotoFilterThumb, -1, ext, parentObject, 1)
				}
			}
			drawLinkImageView = true
		}

		if (mediaWebpage) {
			width = viewWidth
			var height = MeasureSpec.getSize(heightMeasureSpec)
			if (height == 0) {
				height = AndroidUtilities.dp(100f)
			}
			setMeasuredDimension(width, height)
			val x = (width - AndroidUtilities.dp(24f)) / 2
			val y = (height - AndroidUtilities.dp(24f)) / 2
			radialProgress.setProgressRect(x, y, x + AndroidUtilities.dp(24f), y + AndroidUtilities.dp(24f))
			radialProgress.setCircleRadius(AndroidUtilities.dp(12f))
			photoImage.setImageCoordinates(0f, 0f, width.toFloat(), height.toFloat())
		}
		else {
			var height = 0
			val titleLayout = titleLayout
			val descriptionLayout = descriptionLayout
			val linkLayout = linkLayout

			if (titleLayout != null && titleLayout.lineCount != 0) {
				height += titleLayout.getLineBottom(titleLayout.lineCount - 1)
			}
			if (descriptionLayout != null && descriptionLayout.lineCount != 0) {
				height += descriptionLayout.getLineBottom(descriptionLayout.lineCount - 1)
			}
			if (linkLayout != null && linkLayout.lineCount > 0) {
				height += linkLayout.getLineBottom(linkLayout.lineCount - 1)
			}
			height = max(AndroidUtilities.dp(52f).toDouble(), height.toDouble()).toInt()
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (max(AndroidUtilities.dp(68f).toDouble(), (height + AndroidUtilities.dp(16f)).toDouble()) + (if (needDivider) 1 else 0)).toInt())

			val maxPhotoWidth = AndroidUtilities.dp(52f)
			val x = if (LocaleController.isRTL) MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(8f) - maxPhotoWidth else AndroidUtilities.dp(8f)
			letterDrawable.setBounds(x, AndroidUtilities.dp(8f), x + maxPhotoWidth, AndroidUtilities.dp(60f))
			photoImage.setImageCoordinates(x.toFloat(), AndroidUtilities.dp(8f).toFloat(), maxPhotoWidth.toFloat(), maxPhotoWidth.toFloat())
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				radialProgress.setCircleRadius(AndroidUtilities.dp(24f))
				radialProgress.setProgressRect(x + AndroidUtilities.dp(4f), AndroidUtilities.dp(12f), x + AndroidUtilities.dp(48f), AndroidUtilities.dp(56f))
			}
		}
		if (checkBox != null) {
			measureChildWithMargins(checkBox, widthMeasureSpec, 0, heightMeasureSpec, 0)
		}
	}

	private fun setAttachType() {
		messageObject = null
		documentAttachType = DOCUMENT_ATTACH_TYPE_NONE
		if (document != null) {
			if (isGifDocument(document)) {
				documentAttachType = DOCUMENT_ATTACH_TYPE_GIF
			}
			else if (isStickerDocument(document) || isAnimatedStickerDocument(document, true)) {
				documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER
			}
			else if (isMusicDocument(document)) {
				documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC
			}
			else if (isVoiceDocument(document)) {
				documentAttachType = DOCUMENT_ATTACH_TYPE_AUDIO
			}
		}
		else if (result != null) {
			if (result?.photo != null) {
				documentAttachType = DOCUMENT_ATTACH_TYPE_PHOTO
			}
			else if (result?.type == "audio") {
				documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC
			}
			else if (result?.type == "voice") {
				documentAttachType = DOCUMENT_ATTACH_TYPE_AUDIO
			}
		}
		if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			val message = TLRPC.TLMessage()
			message.out = true
			message.id = -Utilities.random.nextInt()

			message.fromId = TLRPC.TLPeerUser().also { fromId ->
				fromId.userId = UserConfig.getInstance(currentAccount).getClientUserId()

				message.peerId = TLRPC.TLPeerUser().also { peerId ->
					peerId.userId = fromId.userId
				}
			}

			message.date = (System.currentTimeMillis() / 1000).toInt()
			message.message = ""

			message.media = TLRPC.TLMessageMediaDocument().also { media ->
				media.flags = media.flags or 3

				message.flags = message.flags or (TLRPC.MESSAGE_FLAG_HAS_MEDIA or TLRPC.MESSAGE_FLAG_HAS_FROM_ID)

				if (document != null) {
					media.document = document
					message.attachPath = ""
				}
				else {
					media.document = TLRPC.TLDocument().also { mediaDocument ->
						mediaDocument.fileReference = ByteArray(0)

						val ext = ImageLoader.getHttpUrlExtension(result?.content?.url, if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) "mp3" else "ogg")
						mediaDocument.id = 0
						mediaDocument.accessHash = 0
						mediaDocument.date = message.date
						mediaDocument.mimeType = "audio/$ext"
						mediaDocument.size = 0
						mediaDocument.dcId = 0

						val attributeAudio = TLRPC.TLDocumentAttributeAudio()
						attributeAudio.duration = getInlineResultDuration(result!!)
						attributeAudio.title = if (result!!.title != null) result!!.title else ""
						attributeAudio.performer = if (result!!.description != null) result!!.description else ""
						attributeAudio.flags = attributeAudio.flags or 3

						if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
							attributeAudio.voice = true
						}

						mediaDocument.attributes.add(attributeAudio)

						val fileName = TLRPC.TLDocumentAttributeFilename()
						fileName.fileName = Utilities.MD5(result?.content?.url) + "." + ImageLoader.getHttpUrlExtension(result?.content?.url, if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) "mp3" else "ogg")

						mediaDocument.attributes.add(fileName)

						message.attachPath = File(getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(result?.content?.url) + "." + ImageLoader.getHttpUrlExtension(result?.content?.url, if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) "mp3" else "ogg")).absolutePath
					}
				}
			}

			messageObject = MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = true)
		}
	}

	fun setLink(contextResult: BotInlineResult?, bot: TLRPC.User?, media: Boolean, divider: Boolean, shadow: Boolean) {
		setLink(contextResult, bot, media, divider, shadow, false)
	}

	fun setLink(contextResult: BotInlineResult?, bot: TLRPC.User?, media: Boolean, divider: Boolean, shadow: Boolean, forceGif: Boolean) {
		needDivider = divider
		needShadow = shadow
		inlineBot = bot
		result = contextResult
		parentObject = result
		document = result?.document
		photoAttach = result?.photo

		mediaWebpage = media
		isForceGif = forceGif
		setAttachType()

		if (forceGif) {
			documentAttachType = DOCUMENT_ATTACH_TYPE_GIF
		}

		requestLayout()
		fileName = null
		cacheFile = null
		fileExist = false
		resolvingFileName = false
		updateButtonState(ifSame = false, animated = false)
	}

	fun setGif(document: TLRPC.Document, divider: Boolean) {
		setGif(document, "gif$document", 0, divider)
	}

	fun setGif(document: TLRPC.Document?, parent: Any?, date: Int, divider: Boolean) {
		needDivider = divider
		needShadow = false
		this.date = date
		result = null
		parentObject = parent
		this.document = document
		photoAttach = null
		mediaWebpage = true
		isForceGif = true
		setAttachType()
		documentAttachType = DOCUMENT_ATTACH_TYPE_GIF
		requestLayout()
		fileName = null
		cacheFile = null
		fileExist = false
		resolvingFileName = false
		updateButtonState(ifSame = false, animated = false)
	}

	val isSticker: Boolean
		get() = documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER

	val isGif: Boolean
		get() = documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && isCanPreviewGif

	fun showingBitmap(): Boolean {
		return photoImage.bitmap != null
	}

	fun setScaled(value: Boolean) {
		scaled = value
		lastUpdateTime = System.currentTimeMillis()
		invalidate()
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		photoImage.onDetachedFromWindow()

		radialProgress.onDetachedFromWindow()
		DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		if (photoImage.onAttachedToWindow()) {
			updateButtonState(ifSame = false, animated = false)
		}
		radialProgress.onAttachedToWindow()
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (mediaWebpage || delegate == null || result == null) {
			return super.onTouchEvent(event)
		}
		val x = event.x.toInt()
		val y = event.y.toInt()

		var result = false

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			val area = letterDrawable.bounds.contains(x, y)
			if (event.action == MotionEvent.ACTION_DOWN) {
				if (area) {
					buttonPressed = true
					radialProgress.setPressed(buttonPressed, false)
					invalidate()
					result = true
				}
			}
			else if (buttonPressed) {
				if (event.action == MotionEvent.ACTION_UP) {
					buttonPressed = false
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressedButton()
					invalidate()
				}
				else if (event.action == MotionEvent.ACTION_CANCEL) {
					buttonPressed = false
					invalidate()
				}
				else if (event.action == MotionEvent.ACTION_MOVE) {
					if (!area) {
						buttonPressed = false
						invalidate()
					}
				}
				radialProgress.setPressed(buttonPressed, false)
			}
		}
		else {
			if (!this.result?.content?.url.isNullOrEmpty()) {
				if (event.action == MotionEvent.ACTION_DOWN) {
					if (letterDrawable.bounds.contains(x, y)) {
						buttonPressed = true
						result = true
					}
				}
				else {
					if (buttonPressed) {
						if (event.action == MotionEvent.ACTION_UP) {
							buttonPressed = false
							playSoundEffect(SoundEffectConstants.CLICK)
							delegate?.didPressedImage(this)
						}
						else if (event.action == MotionEvent.ACTION_CANCEL) {
							buttonPressed = false
						}
						else if (event.action == MotionEvent.ACTION_MOVE) {
							if (!letterDrawable.bounds.contains(x, y)) {
								buttonPressed = false
							}
						}
					}
				}
			}
		}

		if (!result) {
			result = super.onTouchEvent(event)
		}

		return result
	}

	private fun didPressedButton() {
		if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			if (buttonState == 0) {
				if (MediaController.getInstance().playMessage(messageObject)) {
					buttonState = 1
					radialProgress.setIcon(iconForCurrentState, false, true)
					invalidate()
				}
			}
			else if (buttonState == 1) {
				val result = MediaController.getInstance().pauseMessage(messageObject)
				if (result) {
					buttonState = 0
					radialProgress.setIcon(iconForCurrentState, false, true)
					invalidate()
				}
			}
			else if (buttonState == 2) {
				radialProgress.setProgress(0f, false)
				if (document != null) {
					FileLoader.getInstance(currentAccount).loadFile(document, result, FileLoader.PRIORITY_NORMAL, 0)
				}
				else if (result?.content is TLWebDocument) {
					FileLoader.getInstance(currentAccount).loadFile(createWithWebDocument(result?.content), FileLoader.PRIORITY_HIGH, 1)
				}
				buttonState = 4
				radialProgress.setIcon(iconForCurrentState, false, true)
				invalidate()
			}
			else if (buttonState == 4) {
				if (document != null) {
					FileLoader.getInstance(currentAccount).cancelLoadFile(document)
				}
				else if (result?.content is TLWebDocument) {
					FileLoader.getInstance(currentAccount).cancelLoadFile(createWithWebDocument(result?.content))
				}
				buttonState = 2
				radialProgress.setIcon(iconForCurrentState, false, true)
				invalidate()
			}
		}
	}

	override fun onDraw(canvas: Canvas) {
		if (checkBox != null) {
			if (checkBox!!.isChecked || !photoImage.hasBitmapImage() || photoImage.currentAlpha != 1.0f || PhotoViewer.isShowingImage(parentObject as MessageObject?)) {
				canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), backgroundPaint!!)
			}
		}

		if (titleLayout != null) {
			canvas.withTranslation(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), titleY.toFloat()) {
				titleLayout?.draw(this)
			}
		}

		if (descriptionLayout != null) {
			Theme.chat_contextResult_descriptionTextPaint.color = context.getColor(R.color.dark_gray)

			canvas.withTranslation(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), descriptionY.toFloat()) {
				descriptionLayout?.draw(this)
			}
		}

		if (linkLayout != null) {
			Theme.chat_contextResult_descriptionTextPaint.color = context.getColor(R.color.brand)

			canvas.withTranslation(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), linkY.toFloat()) {
				linkLayout?.draw(this)
			}
		}

		if (!mediaWebpage) {
			if (drawLinkImageView && !PhotoViewer.isShowingImage(result)) {
				letterDrawable.alpha = (255 * (1.0f - photoImage.currentAlpha)).toInt()
			}
			else {
				letterDrawable.alpha = 255
			}
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				radialProgress.setProgressColor(context.getColor(R.color.brand))
				radialProgress.draw(canvas)
			}
			else if (result != null && result!!.type == "file") {
				val w = Theme.chat_inlineResultFile.intrinsicWidth
				val h = Theme.chat_inlineResultFile.intrinsicHeight
				val x = (photoImage.imageX + (AndroidUtilities.dp(52f) - w) / 2).toInt()
				val y = (photoImage.imageY + (AndroidUtilities.dp(52f) - h) / 2).toInt()
				canvas.drawRect(photoImage.imageX, photoImage.imageY, photoImage.imageX + AndroidUtilities.dp(52f), photoImage.imageY + AndroidUtilities.dp(52f), LetterDrawable.paint)
				Theme.chat_inlineResultFile.setBounds(x, y, x + w, y + h)
				Theme.chat_inlineResultFile.draw(canvas)
			}
			else if (result != null && (result!!.type == "audio" || result!!.type == "voice")) {
				val w = Theme.chat_inlineResultAudio.intrinsicWidth
				val h = Theme.chat_inlineResultAudio.intrinsicHeight
				val x = (photoImage.imageX + (AndroidUtilities.dp(52f) - w) / 2).toInt()
				val y = (photoImage.imageY + (AndroidUtilities.dp(52f) - h) / 2).toInt()
				canvas.drawRect(photoImage.imageX, photoImage.imageY, photoImage.imageX + AndroidUtilities.dp(52f), photoImage.imageY + AndroidUtilities.dp(52f), LetterDrawable.paint)
				Theme.chat_inlineResultAudio.setBounds(x, y, x + w, y + h)
				Theme.chat_inlineResultAudio.draw(canvas)
			}
			else if (result != null && (result!!.type == "venue" || result!!.type == "geo")) {
				val w = Theme.chat_inlineResultLocation.intrinsicWidth
				val h = Theme.chat_inlineResultLocation.intrinsicHeight
				val x = (photoImage.imageX + (AndroidUtilities.dp(52f) - w) / 2).toInt()
				val y = (photoImage.imageY + (AndroidUtilities.dp(52f) - h) / 2).toInt()
				canvas.drawRect(photoImage.imageX, photoImage.imageY, photoImage.imageX + AndroidUtilities.dp(52f), photoImage.imageY + AndroidUtilities.dp(52f), LetterDrawable.paint)
				Theme.chat_inlineResultLocation.setBounds(x, y, x + w, y + h)
				Theme.chat_inlineResultLocation.draw(canvas)
			}
			else {
				letterDrawable.draw(canvas)
			}
		}
		else {
			if (result?.sendMessage is TLRPC.TLBotInlineMessageMediaGeo || result?.sendMessage is TLRPC.TLBotInlineMessageMediaVenue) {
				val w = Theme.chat_inlineResultLocation.intrinsicWidth
				val h = Theme.chat_inlineResultLocation.intrinsicHeight
				val x = (photoImage.imageX + (photoImage.imageWidth - w) / 2).toInt()
				val y = (photoImage.imageY + (photoImage.imageHeight - h) / 2).toInt()
				canvas.drawRect(photoImage.imageX, photoImage.imageY, photoImage.imageX + photoImage.imageWidth, photoImage.imageY + photoImage.imageHeight, LetterDrawable.paint)
				Theme.chat_inlineResultLocation.setBounds(x, y, x + w, y + h)
				Theme.chat_inlineResultLocation.draw(canvas)
			}
		}
		if (drawLinkImageView) {
			if (result != null) {
				photoImage.setVisible(!PhotoViewer.isShowingImage(result), false)
			}

			canvas.withSave {
				if (scaled && scale != 0.8f || !scaled && scale != 1.0f) {
					val newTime = System.currentTimeMillis()
					val dt = (newTime - lastUpdateTime)
					lastUpdateTime = newTime
					if (scaled && scale != 0.8f) {
						scale -= dt / 400.0f
						if (scale < 0.8f) {
							scale = 0.8f
						}
					}
					else {
						scale += dt / 400.0f
						if (scale > 1.0f) {
							scale = 1.0f
						}
					}
					invalidate()
				}
				scale(scale * imageScale, scale * imageScale, (measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat())
				photoImage.draw(this)
			}
		}
		if (mediaWebpage && (documentAttachType == DOCUMENT_ATTACH_TYPE_PHOTO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF)) {
			radialProgress.draw(canvas)
		}

		if (needDivider && !mediaWebpage) {
			if (LocaleController.isRTL) {
				canvas.drawLine(0f, (measuredHeight - 1).toFloat(), (measuredWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
			else {
				canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat()).toFloat(), (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
		}
		if (needShadow) {
			Theme.chat_contextResult_shadowUnderSwitchDrawable.setBounds(0, 0, measuredWidth, AndroidUtilities.dp(3f))
			Theme.chat_contextResult_shadowUnderSwitchDrawable.draw(canvas)
		}
	}

	private val iconForCurrentState: Int
		get() {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				radialProgress.setColors(context.getColor(R.color.brand), context.getColor(R.color.white), context.getColor(R.color.brand), context.getColor(R.color.white))

				when (buttonState) {
					1 -> {
						return MediaActionDrawable.ICON_PAUSE
					}

					2 -> {
						return MediaActionDrawable.ICON_DOWNLOAD
					}

					4 -> {
						return MediaActionDrawable.ICON_CANCEL
					}

					else -> {
						return MediaActionDrawable.ICON_PLAY
					}
				}
			}

			radialProgress.setColors(context.getColor(R.color.brand), context.getColor(R.color.white), context.getColor(R.color.brand), context.getColor(R.color.white))

			return if (buttonState == 1) MediaActionDrawable.ICON_EMPTY else MediaActionDrawable.ICON_NONE
		}

	var resolvingFileName: Boolean = false
	var fileName: String? = null
	var cacheFile: File? = null
	var resolveFileNameId: Int = 0
	var fileExist: Boolean = false

	fun updateButtonState(ifSame: Boolean, animated: Boolean) {
		if (fileName == null && !resolvingFileName) {
			resolvingFileName = true
			resolveFileNameId = resolveFileNameId++
			val localId = resolveFileNameId
			Utilities.searchQueue.postRunnable {
				var fileName: String? = null
				var cacheFile: File? = null
				if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
					if (document != null) {
						fileName = getAttachFileName(document)
						cacheFile = FileLoader.getInstance(currentAccount).getPathToAttach(document)
					}
					else if (result?.content is TLWebDocument) {
						fileName = Utilities.MD5(result?.content?.url) + "." + ImageLoader.getHttpUrlExtension(result?.content?.url, if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) "mp3" else "ogg")
						cacheFile = File(getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName)
					}
				}
				else if (mediaWebpage) {
					if (result != null) {
						if (result?.document is TLRPC.TLDocument) {
							fileName = getAttachFileName(result?.document)
							cacheFile = FileLoader.getInstance(currentAccount).getPathToAttach(result?.document)
						}
						else if (result?.photo is TLRPC.TLPhoto) {
							currentPhotoObject = getClosestPhotoSizeWithSize(result?.photo?.sizes, AndroidUtilities.getPhotoSize(), true)
							fileName = getAttachFileName(currentPhotoObject)
							cacheFile = FileLoader.getInstance(currentAccount).getPathToAttach(currentPhotoObject)
						}
						else if (result?.content is TLWebDocument) {
							fileName = Utilities.MD5(result?.content?.url) + "." + ImageLoader.getHttpUrlExtension(result?.content?.url, getMimeTypePart(result?.content?.mimeType))
							cacheFile = File(getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName)
							if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && result?.thumb is TLWebDocument && "video/mp4" == result?.thumb?.mimeType) {
								fileName = null
							}
						}
						else if (result?.thumb is TLWebDocument) {
							fileName = Utilities.MD5(result?.thumb?.url) + "." + ImageLoader.getHttpUrlExtension(result?.thumb?.url, getMimeTypePart(result?.thumb?.mimeType))
							cacheFile = File(getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName)
						}
					}
					else if (document != null) {
						fileName = getAttachFileName(document)
						cacheFile = FileLoader.getInstance(currentAccount).getPathToAttach(document)
					}

					if (document != null && documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && getDocumentVideoThumb(document) != null) {
						fileName = null
					}
				}
				val fileNameFinal = fileName
				val cacheFileFinal = cacheFile
				val fileExist = !TextUtils.isEmpty(fileName) && cacheFile!!.exists()
				AndroidUtilities.runOnUIThread {
					resolvingFileName = false
					if (resolveFileNameId == localId) {
						this@ContextLinkCell.fileName = fileNameFinal
						if (this@ContextLinkCell.fileName == null) {
							this@ContextLinkCell.fileName = ""
						}
						this@ContextLinkCell.cacheFile = cacheFileFinal
						this@ContextLinkCell.fileExist = fileExist
					}
					updateButtonState(ifSame, true)
				}
			}
			radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
		}
		else {
			if (TextUtils.isEmpty(fileName)) {
				buttonState = -1
				radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
				return
			}
			val isLoading = if (document != null) {
				FileLoader.getInstance(currentAccount).isLoadingFile(fileName)
			}
			else {
				ImageLoader.getInstance().isLoadingHttpFile(fileName)
			}
			if (isLoading || !fileExist) {
				DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this)
				if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
					if (!isLoading) {
						buttonState = 2
					}
					else {
						buttonState = 4
						val progress = ImageLoader.getInstance().getFileProgress(fileName)
						if (progress != null) {
							radialProgress.setProgress(progress, animated)
						}
						else {
							radialProgress.setProgress(0f, animated)
						}
					}
				}
				else {
					buttonState = 1
					val progress = ImageLoader.getInstance().getFileProgress(fileName)
					val setProgress = progress ?: 0f
					radialProgress.setProgress(setProgress, false)
				}
			}
			else {
				DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
				if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
					val playing = MediaController.getInstance().isPlayingMessage(messageObject)
					buttonState = if (!playing || playing && MediaController.getInstance().isMessagePaused) {
						0
					}
					else {
						1
					}
					radialProgress.setProgress(1f, animated)
				}
				else {
					buttonState = -1
				}
			}
			radialProgress.setIcon(iconForCurrentState, ifSame, animated)
			invalidate()
		}
	}

	fun setDelegate(contextLinkCellDelegate: ContextLinkCellDelegate?) {
		delegate = contextLinkCellDelegate
	}

	override fun onFailedDownload(fileName: String, canceled: Boolean) {
		updateButtonState(true, canceled)
	}

	override fun onSuccessDownload(fileName: String) {
		fileExist = true
		radialProgress.setProgress(1f, true)
		updateButtonState(ifSame = false, animated = true)
	}

	override fun onProgressDownload(fileName: String, downloadedSize: Long, totalSize: Long) {
		radialProgress.setProgress(min(1.0, (downloadedSize / totalSize.toFloat()).toDouble()).toFloat(), true)
		if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			if (buttonState != 4) {
				updateButtonState(ifSame = false, animated = true)
			}
		}
		else {
			if (buttonState != 1) {
				updateButtonState(ifSame = false, animated = true)
			}
		}
	}

	override fun onProgressUpload(fileName: String, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) {
		// unused
	}

	override fun getObserverTag(): Int {
		return TAG
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		val sbuf = StringBuilder()
		when (documentAttachType) {
			DOCUMENT_ATTACH_TYPE_DOCUMENT -> sbuf.append(context.getString(R.string.AttachDocument))

			DOCUMENT_ATTACH_TYPE_GIF -> sbuf.append(context.getString(R.string.AttachGif))
			DOCUMENT_ATTACH_TYPE_AUDIO -> sbuf.append(context.getString(R.string.AttachAudio))
			DOCUMENT_ATTACH_TYPE_VIDEO -> sbuf.append(context.getString(R.string.AttachVideo))
			DOCUMENT_ATTACH_TYPE_MUSIC -> sbuf.append(context.getString(R.string.AttachMusic))
			DOCUMENT_ATTACH_TYPE_STICKER -> sbuf.append(context.getString(R.string.AttachSticker))
			DOCUMENT_ATTACH_TYPE_PHOTO -> sbuf.append(context.getString(R.string.AttachPhoto))
			DOCUMENT_ATTACH_TYPE_GEO -> sbuf.append(context.getString(R.string.AttachLocation))
		}
		val hasTitle = titleLayout != null && !TextUtils.isEmpty(titleLayout!!.text)
		val hasDescription = descriptionLayout != null && !TextUtils.isEmpty(descriptionLayout!!.text)
		if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC && hasTitle && hasDescription) {
			sbuf.append(", ")
			sbuf.append(LocaleController.formatString("AccDescrMusicInfo", R.string.AccDescrMusicInfo, descriptionLayout!!.text, titleLayout!!.text))
		}
		else {
			if (hasTitle) {
				if (sbuf.isNotEmpty()) {
					sbuf.append(", ")
				}
				sbuf.append(titleLayout!!.text)
			}
			if (hasDescription) {
				if (sbuf.isNotEmpty()) {
					sbuf.append(", ")
				}
				sbuf.append(descriptionLayout!!.text)
			}
		}
		info.text = sbuf
		if (checkBox != null && checkBox!!.isChecked) {
			info.isCheckable = true
			info.isChecked = true
		}
	}

	private var imageScale = 1.0f

	val IMAGE_SCALE: Property<ContextLinkCell, Float> = object : AnimationProperties.FloatProperty<ContextLinkCell>("animationValue") {
		override fun setValue(`object`: ContextLinkCell, value: Float) {
			imageScale = value
			invalidate()
		}

		override fun get(`object`: ContextLinkCell): Float {
			return imageScale
		}
	}

	init {
		photoImage.setLayerNum(1)
		photoImage.setUseSharedAnimationQueue(true)
		letterDrawable = LetterDrawable()
		radialProgress = RadialProgress2(this)
		TAG = DownloadController.getInstance(currentAccount).generateObserverTag()
		isFocusable = true

		if (needsCheckBox) {
			backgroundPaint = Paint()
			backgroundPaint!!.color = context.getColor(R.color.light_background)

			checkBox = CheckBox2(context, 21)
			checkBox!!.visibility = INVISIBLE
			checkBox!!.setColor(0, context.getColor(R.color.light_background), context.getColor(R.color.brand))
			checkBox!!.setDrawUnchecked(false)
			checkBox!!.setDrawBackgroundAsArc(1)
			addView(checkBox, createFrame(24, 24f, Gravity.RIGHT or Gravity.TOP, 0f, 1f, 1f, 0f))
		}
		setWillNotDraw(false)
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		if (checkBox == null) {
			return
		}
		if (checkBox!!.visibility != VISIBLE) {
			checkBox!!.visibility = VISIBLE
		}
		checkBox!!.setChecked(checked, animated)
		if (animator != null) {
			animator!!.cancel()
			animator = null
		}
		if (animated) {
			animator = AnimatorSet()
			animator!!.playTogether(ObjectAnimator.ofFloat(this, IMAGE_SCALE, if (checked) 0.81f else 1.0f))
			animator!!.setDuration(200)
			animator!!.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (animator != null && animator == animation) {
						animator = null
						if (!checked) {
							setBackgroundColor(0)
						}
					}
				}

				override fun onAnimationCancel(animation: Animator) {
					if (animator != null && animator == animation) {
						animator = null
					}
				}
			})
			animator!!.start()
		}
		else {
			imageScale = if (checked) 0.85f else 1.0f
			invalidate()
		}
	}

	companion object {
		private const val DOCUMENT_ATTACH_TYPE_NONE = 0
		private const val DOCUMENT_ATTACH_TYPE_DOCUMENT = 1
		private const val DOCUMENT_ATTACH_TYPE_GIF = 2
		private const val DOCUMENT_ATTACH_TYPE_AUDIO = 3
		private const val DOCUMENT_ATTACH_TYPE_VIDEO = 4
		private const val DOCUMENT_ATTACH_TYPE_MUSIC = 5
		private const val DOCUMENT_ATTACH_TYPE_STICKER = 6
		private const val DOCUMENT_ATTACH_TYPE_PHOTO = 7
		private const val DOCUMENT_ATTACH_TYPE_GEO = 8
	}
}
