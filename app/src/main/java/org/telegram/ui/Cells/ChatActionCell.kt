/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Cells

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.DownloadController
import org.telegram.messenger.DownloadController.FileDownloadProgressListener
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.ImageReceiver.ImageReceiverDelegate
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedEmojiSpan.EmojiGroupedSpans
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.Premium.StarParticlesView
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.Components.URLSpanNoUnderline
import org.telegram.ui.Components.spoilers.SpoilerEffect
import org.telegram.ui.LaunchActivity
import org.telegram.ui.PhotoViewer
import java.util.Stack
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

open class ChatActionCell @JvmOverloads constructor(context: Context, private val canDrawInParent: Boolean = false) : BaseCell(context), FileDownloadProgressListener, NotificationCenterDelegate {
	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.startSpoilers -> {
				setSpoilersSuppressed(false)
			}

			NotificationCenter.stopSpoilers -> {
				setSpoilersSuppressed(true)
			}

			NotificationCenter.didUpdatePremiumGiftStickers -> {
				val messageObject = messageObject

				if (messageObject != null) {
					setMessageObject(messageObject, force = true, collapseForDate = collapseForDate)
				}
			}

			NotificationCenter.diceStickersDidLoad -> {
				if (args[0] == getInstance(currentAccount).premiumGiftsStickerPack) {
					val messageObject = messageObject

					if (messageObject != null) {
						setMessageObject(messageObject, force = true, collapseForDate = collapseForDate)
					}
				}
			}
		}
	}

	fun setSpoilersSuppressed(s: Boolean) {
		for (eff in spoilers) {
			eff.setSuppressUpdates(s)
		}
	}

	interface ChatActionCellDelegate {
		fun didClickImage(cell: ChatActionCell?) {}
		fun didOpenPremiumGift(cell: ChatActionCell?, giftOption: TLRPC.TL_premiumGiftOption?, animateConfetti: Boolean) {}

		fun didLongPress(cell: ChatActionCell?, x: Float, y: Float): Boolean {
			return false
		}

		fun needOpenUserProfile(uid: Long) {}
		fun didPressBotButton(messageObject: MessageObject?, button: TLRPC.KeyboardButton?) {}
		fun didPressReplyMessage(cell: ChatActionCell?, id: Int) {}
		fun needOpenInviteLink(invite: TLRPC.TL_chatInviteExported?) {}
		fun needShowEffectOverlay(cell: ChatActionCell?, document: TLRPC.Document?, videoSize: TLRPC.VideoSize?) {}
	}

	private val tag: Int
	private var pressedLink: URLSpan? = null
	private val currentAccount = UserConfig.selectedAccount
	private val avatarDrawable: AvatarDrawable
	private var textLayout: StaticLayout? = null
	private var textWidth = 0
	private var textHeight = 0
	private var textX = 0
	private var textY = 0
	private var textXLeft = 0
	private var previousWidth = 0
	private var imagePressed = false
	private var giftButtonPressed = false
	private val giftButtonRect = RectF()
	private val spoilers = mutableListOf<SpoilerEffect>()
	private val spoilersPool = Stack<SpoilerEffect>()
	private var animatedEmojiStack: EmojiGroupedSpans? = null
	private var textPaint: TextPaint? = null
	private var viewTop = 0f
	private var backgroundHeight = 0
	private var visiblePartSet = false
	private var currentVideoLocation: ImageLocation? = null
	private var lastTouchX = 0f
	private var lastTouchY = 0f
	private var wasLayout = false
	private var hasReplyMessage = false
	private var collapseForDate = false
	private var customText: CharSequence? = null
	private var overrideBackgroundPaint: Paint? = null
	private var overrideTextPaint: TextPaint? = null
	private val lineWidths = mutableListOf<Int>()
	private val lineHeights = mutableListOf<Int>()
	private val backgroundPath = Path()
	private val rect = RectF()
	private var invalidatePath = true
	private var invalidateColors = false
	private var delegate: ChatActionCellDelegate? = null
	private var stickerSize = 0
	private var giftRectSize = 0
	private var giftPremiumTitleLayout: StaticLayout? = null
	private var giftPremiumSubtitleLayout: StaticLayout? = null
	private var giftPremiumButtonLayout: StaticLayout? = null
	private var giftPremiumButtonWidth = 0f
	private val giftTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val giftSubtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private var giftSticker: TLRPC.Document? = null
	private var giftEffectAnimation: TLRPC.VideoSize? = null
	private var forceWasUnread = false
	val photoImage = ImageReceiver(this)

	var messageObject: MessageObject? = null
		private set

	var customDate = 0
		private set

	@ColorInt
	private var overrideBackground = 0

	@ColorInt
	private var overrideText = 0

	private val giftStickerDelegate = object : ImageReceiverDelegate {
		override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
			if (set) {
				val drawable = photoImage.lottieAnimation ?: return
				val messageObject = messageObject

				if (messageObject != null && !messageObject.playedGiftAnimation) {
					messageObject.playedGiftAnimation = true

					drawable.setCurrentFrame(0, false)

					AndroidUtilities.runOnUIThread { drawable.start() }

					if (messageObject.wasUnread || forceWasUnread) {
						messageObject.wasUnread = false

						forceWasUnread = false

						runCatching {
							performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
						}

						(getContext() as? LaunchActivity)?.fireworksOverlay?.start()

						if (giftEffectAnimation != null && delegate != null) {
							delegate?.needShowEffectOverlay(this@ChatActionCell, giftSticker, giftEffectAnimation)
						}
					}
				}
				else {
					drawable.stop()
					drawable.setCurrentFrame(drawable.framesCount - 1, false)
				}
			}
		}
	}

	private val rippleView: View
	private val starsPath = Path()
	private val starParticlesDrawable: StarParticlesView.Drawable
	private var starsSize = 0

	fun setDelegate(delegate: ChatActionCellDelegate?) {
		this.delegate = delegate
	}

	fun setCustomDate(date: Int, scheduled: Boolean, inLayout: Boolean) {
		if (customDate == date || customDate / 3600 == date / 3600) {
			return
		}

		val newText = if (scheduled) {
			if (date == 0x7ffffffe) {
				context.getString(R.string.MessageScheduledUntilOnline)
			}
			else {
				context.getString(R.string.MessageScheduledOn, LocaleController.formatDateChat(date.toLong()))
			}
		}
		else {
			LocaleController.formatDateChat(date.toLong())
		}

		customDate = date

		if (customText != null && TextUtils.equals(newText, customText)) {
			return
		}

		customText = newText
		accessibilityText = null

		updateTextInternal(inLayout)
	}

	private fun updateTextInternal(inLayout: Boolean) {
		if (measuredWidth != 0) {
			createLayout(customText, measuredWidth)
			invalidate()
		}

		if (!wasLayout) {
			if (inLayout) {
				AndroidUtilities.runOnUIThread {
					requestLayout()
				}
			}
			else {
				requestLayout()
			}
		}
		else {
			buildLayout()
		}
	}

	fun setCustomText(text: CharSequence?) {
		customText = text

		if (customText != null) {
			updateTextInternal(false)
		}
	}

	fun setOverrideColor(@ColorInt background: Int, @ColorInt text: Int) {
		overrideBackground = background
		overrideText = text
	}

	fun setMessageObject(messageObject: MessageObject, force: Boolean = false, collapseForDate: Boolean) {
		this.collapseForDate = collapseForDate

		if (this.messageObject === messageObject && (textLayout == null || TextUtils.equals(textLayout!!.text, messageObject.messageText)) && (hasReplyMessage || messageObject.replyMessageObject == null) && !force) {
			return
		}

		if (BuildConfig.DEBUG_PRIVATE_VERSION && Thread.currentThread() !== ApplicationLoader.applicationHandler?.looper?.thread) {
			FileLog.e(IllegalStateException("Wrong thread!!!"))
		}

		accessibilityText = null

		this.messageObject = messageObject

		hasReplyMessage = messageObject.replyMessageObject != null

		DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)

		previousWidth = 0

		if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
			photoImage.setRoundRadius(0)

			if (USE_PREMIUM_GIFT_LOCAL_STICKER) {
				forceWasUnread = messageObject.wasUnread

				photoImage.setAllowStartLottieAnimation(false)
				photoImage.setDelegate(giftStickerDelegate)
				photoImage.setImageBitmap(RLottieDrawable(R.raw.premium_gift, messageObject.id.toString() + "_" + R.raw.premium_gift, AndroidUtilities.dp(160f), AndroidUtilities.dp(160f)))
			}
			else {
				var set: TLRPC.TL_messages_stickerSet?
				var document: TLRPC.Document? = null
				val packName = getInstance(currentAccount).premiumGiftsStickerPack

				if (packName == null) {
					MediaDataController.getInstance(currentAccount).checkPremiumGiftStickers()
					return
				}

				set = MediaDataController.getInstance(currentAccount).getStickerSetByName(packName)

				if (set == null) {
					set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(packName)
				}

				if (set != null) {
					var months = messageObject.messageOwner?.action?.months ?: 0
					val monthsEmoticon: String?

					if (USE_PREMIUM_GIFT_MONTHS_AS_EMOJI_NUMBERS) {
						val monthsEmoticonBuilder = StringBuilder()

						while (months > 0) {
							monthsEmoticonBuilder.insert(0, (months % 10).toString() + "\u20E3")
							months /= 10
						}

						monthsEmoticon = monthsEmoticonBuilder.toString()
					}
					else {
						monthsEmoticon = monthsToEmoticon[months]
					}

					for (pack in set.packs) {
						if (pack.emoticon == monthsEmoticon) {
							for (id in pack.documents) {
								for (doc in set.documents) {
									if (doc.id == id) {
										document = doc
										break
									}
								}

								if (document != null) {
									break
								}
							}
						}

						if (document != null) {
							break
						}
					}

					if (document == null && set.documents.isNotEmpty()) {
						document = set.documents[0]
					}
				}

				forceWasUnread = messageObject.wasUnread
				giftSticker = document

				if (document != null) {
					photoImage.setAllowStartLottieAnimation(false)
					photoImage.setDelegate(giftStickerDelegate)

					giftEffectAnimation = null

					for (i in document.video_thumbs.indices) {
						if ("f" == document.video_thumbs[i].type) {
							giftEffectAnimation = document.video_thumbs[i]
							break
						}
					}

					val svgThumb = DocumentObject.getSvgThumb(document.thumbs, ResourcesCompat.getColor(context.resources, R.color.light_background, null), 0.2f)

					svgThumb?.overrideWidthAndHeight(512, 512)

					photoImage.setAutoRepeat(0)
					photoImage.setImage(ImageLocation.getForDocument(document), messageObject.id.toString() + "_130_130", svgThumb, "tgs", set, 1)
				}
				else {
					MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(packName, false, set == null)
				}
			}
		}
		else if (messageObject.type == 11) {
			photoImage.setAllowStartLottieAnimation(true)
			photoImage.setDelegate(null)
			photoImage.setRoundRadius((AndroidUtilities.roundMessageSize * 0.45f).toInt())

			avatarDrawable.setInfo(null, null)

			if (messageObject.messageOwner?.action is TLRPC.TL_messageActionUserUpdatedPhoto) {
				photoImage.setImage(null, null, avatarDrawable, null, messageObject, 0)
			}
			else {
				val strippedPhotoSize = messageObject.photoThumbs?.firstOrNull {
					it is TLRPC.TL_photoStrippedSize
				}

				val photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 640)

				if (photoSize != null) {
					val photo = messageObject.messageOwner?.action?.photo
					var videoSize: TLRPC.VideoSize? = null

					if (photo != null) {
						if (photo.video_sizes.isNotEmpty() && SharedConfig.autoplayGifs) {
							videoSize = photo.video_sizes[0]

							if (!messageObject.mediaExists && !DownloadController.getInstance(currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, videoSize.size.toLong())) {
								currentVideoLocation = ImageLocation.getForPhoto(videoSize, photo)

								val fileName = FileLoader.getAttachFileName(videoSize)

								DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, messageObject, this)

								videoSize = null
							}
						}
					}

					if (videoSize != null) {
						photoImage.setImage(ImageLocation.getForPhoto(videoSize, photo), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", avatarDrawable, 0, null, messageObject, 1)
					}
					else {
						photoImage.setImage(ImageLocation.getForObject(photoSize, messageObject.photoThumbsObject), "150_150", ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", avatarDrawable, 0, null, messageObject, 1)
					}
				}
				else {
					photoImage.setImageBitmap(avatarDrawable)
				}
			}

			photoImage.setVisible(!PhotoViewer.isShowingImage(messageObject), false)
		}
		else {
			photoImage.setAllowStartLottieAnimation(true)
			photoImage.setDelegate(null)
			photoImage.setImageBitmap(null as Bitmap?)
		}

		rippleView.visibility = if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) VISIBLE else GONE

		requestLayout()
	}

	fun setVisiblePart(visibleTop: Float, parentH: Int) {
		visiblePartSet = true
		backgroundHeight = parentH
		viewTop = visibleTop
	}

	override fun onLongPress(): Boolean {
		return delegate?.didLongPress(this, lastTouchX, lastTouchY) ?: false
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		rippleView.layout(giftButtonRect.left.toInt(), giftButtonRect.top.toInt(), giftButtonRect.right.toInt(), giftButtonRect.bottom.toInt())
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
		photoImage.onDetachedFromWindow()
		setStarsPaused(true)
		wasLayout = false
		AnimatedEmojiSpan.release(animatedEmojiStack)
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdatePremiumGiftStickers)
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		photoImage.onAttachedToWindow()
		setStarsPaused(false)
		animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack, textLayout)
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdatePremiumGiftStickers)
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad)
	}

	private fun setStarsPaused(paused: Boolean) {
		if (paused == starParticlesDrawable.paused) {
			return
		}

		starParticlesDrawable.paused = paused

		if (paused) {
			starParticlesDrawable.pausedTime = System.currentTimeMillis()
		}
		else {
			for (i in starParticlesDrawable.particles.indices) {
				starParticlesDrawable.particles[i].lifeTime += System.currentTimeMillis() - starParticlesDrawable.pausedTime
			}

			invalidate()
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		val messageObject = messageObject ?: return super.onTouchEvent(event)

		lastTouchX = event.x

		var x = lastTouchX

		lastTouchY = event.y

		var y = lastTouchY
		var result = false

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (delegate != null) {
				if ((messageObject.type == 11 || messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) && photoImage.isInsideImage(x, y)) {
					imagePressed = true
					result = true
				}

				if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM && giftButtonRect.contains(x, y)) {
					rippleView.isPressed = true.also { giftButtonPressed = true }
					result = true
				}

				if (result) {
					startCheckLongPress()
				}
			}
		}
		else {
			if (event.action != MotionEvent.ACTION_MOVE) {
				cancelCheckLongPress()
			}

			if (imagePressed) {
				when (event.action) {
					MotionEvent.ACTION_UP -> {
						imagePressed = false

						if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
							openPremiumGiftPreview()
						}
						else if (delegate != null) {
							delegate?.didClickImage(this)
							playSoundEffect(SoundEffectConstants.CLICK)
						}
					}

					MotionEvent.ACTION_CANCEL -> {
						imagePressed = false
					}

					MotionEvent.ACTION_MOVE -> if (!photoImage.isInsideImage(x, y)) {
						imagePressed = false
					}
				}
			}
			else if (giftButtonPressed) {
				when (event.action) {
					MotionEvent.ACTION_UP -> {
						rippleView.isPressed = false.also { giftButtonPressed = false }

						if (delegate != null) {
							playSoundEffect(SoundEffectConstants.CLICK)
							openPremiumGiftPreview()
						}
					}

					MotionEvent.ACTION_CANCEL -> {
						rippleView.isPressed = false.also { giftButtonPressed = false }
					}

					MotionEvent.ACTION_MOVE -> if (!giftButtonRect.contains(x, y)) {
						rippleView.isPressed = false.also { giftButtonPressed = false }
					}
				}
			}
		}

		if (!result) {
			if (event.action == MotionEvent.ACTION_DOWN || pressedLink != null && event.action == MotionEvent.ACTION_UP) {
				if (x >= textX && y >= textY && x <= textX + textWidth && y <= textY + textHeight) {
					y -= textY.toFloat()
					x -= textXLeft.toFloat()

					val line = textLayout!!.getLineForVertical(y.toInt())
					val off = textLayout!!.getOffsetForHorizontal(line, x)
					val left = textLayout!!.getLineLeft(line)

					if (left <= x && left + textLayout!!.getLineWidth(line) >= x && messageObject.messageText is Spannable) {
						val buffer = messageObject.messageText as Spannable
						val link = buffer.getSpans(off, off, URLSpan::class.java)

						if (link.isNotEmpty()) {
							if (event.action == MotionEvent.ACTION_DOWN) {
								pressedLink = link[0]
								result = true
							}
							else {
								if (link[0] === pressedLink) {
									openLink(pressedLink)
									result = true
								}
							}
						}
						else {
							pressedLink = null
						}
					}
					else {
						pressedLink = null
					}
				}
				else {
					pressedLink = null
				}
			}
		}

		if (!result) {
			result = super.onTouchEvent(event)
		}

		return result
	}

	private fun openPremiumGiftPreview() {
		val action = messageObject?.messageOwner?.action ?: return

		val giftOption = TLRPC.TL_premiumGiftOption()
		giftOption.amount = action.amount
		giftOption.months = action.months
		giftOption.currency = action.currency

		AndroidUtilities.runOnUIThread {
			delegate?.didOpenPremiumGift(this@ChatActionCell, giftOption, false)
		}
	}

	private fun openLink(link: CharacterStyle?) {
		if (delegate != null && link is URLSpan) {
			val url = link.url

			if (url.startsWith("invite") && pressedLink is URLSpanNoUnderline) {
				val spanNoUnderline = pressedLink as URLSpanNoUnderline
				val `object` = spanNoUnderline.getObject()

				if (`object` is TLRPC.TL_chatInviteExported) {
					delegate?.needOpenInviteLink(`object`)
				}
			}
			else if (url.startsWith("game")) {
				delegate?.didPressReplyMessage(this, messageObject!!.replyMsgId)                /*TLRPC.KeyboardButton gameButton = null;
				MessageObject messageObject = currentMessageObject.replyMessageObject;
				if (messageObject != null && messageObject.messageOwner.reply_markup != null) {
					for (int a = 0; a < messageObject.messageOwner.reply_markup.rows.size(); a++) {
						TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(a);
						for (int b = 0; b < row.buttons.size(); b++) {
							TLRPC.KeyboardButton button = row.buttons.get(b);
							if (button instanceof TLRPC.TL_keyboardButtonGame && button.game_id == currentMessageObject.messageOwner.action.game_id) {
								gameButton = button;
								break;
							}
						}
						if (gameButton != null) {
							break;
						}
					}
				}
				if (gameButton != null) {
					delegate.didPressBotButton(messageObject, gameButton);
				}*/
			}
			else if (url.startsWith("http")) {
				Browser.openUrl(context, url)
			}
			else {
				delegate?.needOpenUserProfile(url.toLong())
			}
		}
	}

	private fun createLayout(text: CharSequence?, width: Int) {
		val maxWidth = width - AndroidUtilities.dp(30f)

		invalidatePath = true

		textLayout = StaticLayout(text, getThemedPaint(Theme.key_paint_chatActionText) as TextPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

		spoilersPool.addAll(spoilers)

		spoilers.clear()

		if (text is Spannable) {
			SpoilerEffect.addSpoilers(this, textLayout, text as Spannable?, spoilersPool, spoilers)
		}

		animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack, textLayout)
		textHeight = 0
		textWidth = 0

		try {
			val linesCount = textLayout!!.lineCount
			for (a in 0 until linesCount) {
				var lineWidth: Float

				try {
					lineWidth = textLayout!!.getLineWidth(a)

					if (lineWidth > maxWidth) {
						lineWidth = maxWidth.toFloat()
					}

					textHeight = max(textHeight.toDouble(), ceil(textLayout!!.getLineBottom(a).toDouble())).toInt()
				}
				catch (e: Exception) {
					FileLog.e(e)
					return
				}

				textWidth = max(textWidth.toDouble(), ceil(lineWidth.toDouble())).toInt()
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		textX = (width - textWidth) / 2
		textY = AndroidUtilities.dp(7f)
		textXLeft = (width - textLayout!!.width) / 2
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val messageObject = messageObject

		if (messageObject == null && customText == null) {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), textHeight + AndroidUtilities.dp(14f))
			return
		}

		if (messageObject?.messageOwner?.action != null || collapseForDate) {
			// MARK: remove to show action messages in chat
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0)
			return
		}

		if (messageObject != null && messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
			giftRectSize = min((if (AndroidUtilities.isTablet()) AndroidUtilities.getMinTabletSide() * 0.6f else AndroidUtilities.displaySize.x * 0.6f).toInt(), AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(64f))
			stickerSize = giftRectSize - AndroidUtilities.dp(106f)
		}

		val width = max(AndroidUtilities.dp(30f), MeasureSpec.getSize(widthMeasureSpec))

		if (previousWidth != width) {
			wasLayout = true
			previousWidth = width
			buildLayout()
		}

		var additionalHeight = 0

		if (messageObject != null) {
			if (messageObject.type == 11) {
				additionalHeight = AndroidUtilities.roundMessageSize + AndroidUtilities.dp(10f)
			}
			else if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
				additionalHeight = giftRectSize + AndroidUtilities.dp(12f)
			}
		}

		setMeasuredDimension(width, textHeight + additionalHeight + AndroidUtilities.dp(14f))

		if (messageObject != null && messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
			var y = textY + textHeight + giftRectSize * 0.075f + stickerSize + AndroidUtilities.dp(4f) + giftPremiumTitleLayout!!.height + AndroidUtilities.dp(4f) + giftPremiumSubtitleLayout!!.height
			y += (measuredHeight - y - giftPremiumButtonLayout!!.height - AndroidUtilities.dp(8f)) / 2f

			val rectX = (previousWidth - giftPremiumButtonWidth) / 2f

			giftButtonRect[rectX - AndroidUtilities.dp(18f), y - AndroidUtilities.dp(8f), rectX + giftPremiumButtonWidth + AndroidUtilities.dp(18f)] = y + giftPremiumButtonLayout!!.height + AndroidUtilities.dp(8f)

			val sizeInternal = measuredWidth shl 16 + measuredHeight

			starParticlesDrawable.rect.set(giftButtonRect)
			starParticlesDrawable.rect2.set(giftButtonRect)

			if (starsSize != sizeInternal) {
				starsSize = sizeInternal
				starParticlesDrawable.resetPositions()
			}
		}
	}

	private fun buildLayout() {
		val messageObject = messageObject

		val text = if (messageObject != null) {
			if (messageObject.messageOwner?.media != null && messageObject.messageOwner?.media?.ttl_seconds != 0) {
				if (messageObject.messageOwner?.media?.photo is TLRPC.TL_photoEmpty) {
					context.getString(R.string.AttachPhotoExpired)
				}
				else if (messageObject.messageOwner?.media?.document is TLRPC.TL_documentEmpty) {
					context.getString(R.string.AttachVideoExpired)
				}
				else {
					AnimatedEmojiSpan.cloneSpans(messageObject.messageText)
				}
			}
			else {
				AnimatedEmojiSpan.cloneSpans(messageObject.messageText)
			}
		}
		else {
			customText
		}

		createLayout(text, previousWidth)

		if (messageObject != null) {
			if (messageObject.type == 11) {
				photoImage.setImageCoordinates((previousWidth - AndroidUtilities.roundMessageSize) / 2f, (textHeight + AndroidUtilities.dp(19f)).toFloat(), AndroidUtilities.roundMessageSize.toFloat(), AndroidUtilities.roundMessageSize.toFloat())
			}
			else if (messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
				createGiftPremiumLayouts(context.getString(R.string.ActionGiftPremiumTitle), context.getString(R.string.ActionGiftPremiumSubtitle, LocaleController.formatPluralString("Months", messageObject.messageOwner?.action?.months ?: 0)), context.getString(R.string.ActionGiftPremiumView), giftRectSize)
			}
		}
	}

	private fun createGiftPremiumLayouts(title: CharSequence, subtitle: CharSequence, button: CharSequence, width: Int) {
		val titleBuilder = SpannableStringBuilder.valueOf(title)
		titleBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), 0, titleBuilder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

		giftPremiumTitleLayout = StaticLayout(titleBuilder, giftTitlePaint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
		giftPremiumSubtitleLayout = StaticLayout(subtitle, giftSubtitlePaint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

		val buttonBuilder = SpannableStringBuilder.valueOf(button)
		buttonBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), 0, buttonBuilder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

		giftPremiumButtonLayout = StaticLayout(buttonBuilder, getThemedPaint(Theme.key_paint_chatActionText) as TextPaint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
		giftPremiumButtonWidth = measureLayoutWidth(giftPremiumButtonLayout!!)
	}

	private fun measureLayoutWidth(layout: Layout): Float {
		var maxWidth = 0f

		for (i in 0 until layout.lineCount) {
			val lineWidth = ceil(layout.getLineWidth(i).toDouble()).toInt()

			if (lineWidth > maxWidth) {
				maxWidth = lineWidth.toFloat()
			}
		}

		return maxWidth
	}

	override fun onDraw(canvas: Canvas) {
		val messageObject = messageObject

		if (messageObject != null && messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
			stickerSize = giftRectSize - AndroidUtilities.dp(106f)

			photoImage.setImageCoordinates((previousWidth - stickerSize) / 2f, textY + textHeight + giftRectSize * 0.075f, stickerSize.toFloat(), stickerSize.toFloat())

			textPaint?.let { textPaint ->
				if (giftTitlePaint.color != textPaint.color) {
					giftTitlePaint.color = textPaint.color
				}

				if (giftSubtitlePaint.color != textPaint.color) {
					giftSubtitlePaint.color = textPaint.color
				}
			}
		}

		if (messageObject != null && (messageObject.type == 11 || messageObject.type == MessageObject.TYPE_GIFT_PREMIUM)) {
			photoImage.draw(canvas)
		}

		if (textLayout == null) {
			return
		}

		drawBackground(canvas, false)

		if (textPaint != null) {
			canvas.save()
			canvas.translate(textXLeft.toFloat(), textY.toFloat())

			if (textLayout?.paint !== textPaint) {
				buildLayout()
			}

			canvas.save()

			SpoilerEffect.clipOutCanvas(canvas, spoilers)

			AnimatedEmojiSpan.drawAnimatedEmojis(canvas, textLayout, animatedEmojiStack, 0f, spoilers, 0f, 0f, 0f, 1f)

			textLayout?.draw(canvas)

			canvas.restore()

			for (eff in spoilers) {
				eff.setColor(textLayout!!.paint.color)
				eff.draw(canvas)
			}

			canvas.restore()
		}

		if (messageObject != null && messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
			canvas.save()

			val x = (previousWidth - giftRectSize) / 2f
			var y = textY + textHeight + giftRectSize * 0.075f + stickerSize + AndroidUtilities.dp(4f)

			canvas.translate(x, y)

			giftPremiumTitleLayout!!.draw(canvas)

			canvas.restore()

			y += giftPremiumTitleLayout!!.height.toFloat()
			y += AndroidUtilities.dp(4f).toFloat()

			canvas.save()
			canvas.translate(x, y)

			giftPremiumSubtitleLayout?.draw(canvas)

			canvas.restore()

			y += giftPremiumSubtitleLayout!!.height.toFloat()
			y += (height - y - giftPremiumButtonLayout!!.height - AndroidUtilities.dp(8f)) / 2f

			val backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground)

			canvas.drawRoundRect(giftButtonRect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), backgroundPaint)

			if (hasGradientService()) {
				canvas.drawRoundRect(giftButtonRect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
			}

			starsPath.rewind()
			starsPath.addRoundRect(giftButtonRect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), Path.Direction.CW)

			canvas.save()
			canvas.clipPath(starsPath)

			starParticlesDrawable.onDraw(canvas)

			if (!starParticlesDrawable.paused) {
				invalidate()
			}

			canvas.restore()
			canvas.save()
			canvas.translate(x, y)

			giftPremiumButtonLayout!!.draw(canvas)

			canvas.restore()
		}
	}

	fun drawBackground(canvas: Canvas, fromParent: Boolean) {
		if (canDrawInParent) {
			if (hasGradientService() && !fromParent) {
				return
			}

			if (!hasGradientService() && fromParent) {
				return
			}
		}

		// MARK: prevent background from drawing for hidden service messages
		if (measuredHeight == 0) {
			return
		}

		var backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground)

		textPaint = getThemedPaint(Theme.key_paint_chatActionText) as TextPaint

		if (overrideBackground != 0) {
			if (overrideBackgroundPaint == null) {
				overrideBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
				overrideBackgroundPaint?.color = overrideBackground

				overrideTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
				overrideTextPaint?.typeface = Theme.TYPEFACE_BOLD
				overrideTextPaint?.textSize = AndroidUtilities.dp((max(16, SharedConfig.fontSize) - 2).toFloat()).toFloat()
				overrideTextPaint?.color = overrideText
			}

			backgroundPaint = overrideBackgroundPaint!!

			textPaint = overrideTextPaint
		}

		if (invalidatePath) {
			invalidatePath = false
			lineWidths.clear()

			val count = textLayout?.lineCount ?: 0
			val corner = AndroidUtilities.dp(11f)
			val cornerIn = AndroidUtilities.dp(8f)
			var prevLineWidth = 0

			for (a in 0 until count) {
				var lineWidth = ceil(textLayout!!.getLineWidth(a).toDouble()).toInt()

				if (a != 0) {
					val diff = prevLineWidth - lineWidth

					if (diff > 0 && diff <= corner + cornerIn) {
						lineWidth = prevLineWidth
					}
				}

				lineWidths.add(lineWidth)

				prevLineWidth = lineWidth
			}

			for (a in count - 2 downTo 0) {
				var lineWidth = lineWidths[a]
				val diff = prevLineWidth - lineWidth

				if (diff > 0 && diff <= corner + cornerIn) {
					lineWidth = prevLineWidth
				}

				lineWidths[a] = lineWidth

				prevLineWidth = lineWidth
			}

			var y = AndroidUtilities.dp(4f)
			val x = measuredWidth / 2
			var previousLineBottom = 0
			val cornerOffset = AndroidUtilities.dp(3f)
			val cornerInSmall = AndroidUtilities.dp(6f)
			val cornerRest = corner - cornerOffset

			lineHeights.clear()
			backgroundPath.reset()
			backgroundPath.moveTo(x.toFloat(), y.toFloat())

			for (a in 0 until count) {
				val lineWidth = lineWidths[a]
				val lineBottom = textLayout!!.getLineBottom(a)
				val nextLineWidth = if (a < count - 1) lineWidths[a + 1] else 0
				var height = lineBottom - previousLineBottom

				if (a == 0 || lineWidth > prevLineWidth) {
					height += AndroidUtilities.dp(3f)
				}

				if (a == count - 1 || lineWidth > nextLineWidth) {
					height += AndroidUtilities.dp(3f)
				}

				previousLineBottom = lineBottom

				val startX = x + lineWidth / 2.0f

				val innerCornerRad = if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
					cornerInSmall
				}
				else {
					cornerIn
				}

				if (a == 0 || lineWidth > prevLineWidth) {
					rect[startX - cornerOffset - corner, y.toFloat(), startX + cornerRest] = (y + corner * 2).toFloat()
					backgroundPath.arcTo(rect, -90f, 90f)
				}
				else if (lineWidth < prevLineWidth) {
					rect[startX + cornerRest, y.toFloat(), startX + cornerRest + innerCornerRad * 2] = (y + innerCornerRad * 2).toFloat()
					backgroundPath.arcTo(rect, -90f, -90f)
				}

				y += height

				if (a != count - 1 && lineWidth < nextLineWidth) {
					y -= AndroidUtilities.dp(3f)
					height -= AndroidUtilities.dp(3f)
				}

				if (a != 0 && lineWidth < prevLineWidth) {
					y -= AndroidUtilities.dp(3f)
					height -= AndroidUtilities.dp(3f)
				}

				lineHeights.add(height)

				if (a == count - 1 || lineWidth > nextLineWidth) {
					rect[startX - cornerOffset - corner, (y - corner * 2).toFloat(), startX + cornerRest] = y.toFloat()
					backgroundPath.arcTo(rect, 0f, 90f)
				}
				else if (lineWidth < nextLineWidth) {
					rect[startX + cornerRest, (y - innerCornerRad * 2).toFloat(), startX + cornerRest + innerCornerRad * 2] = y.toFloat()
					backgroundPath.arcTo(rect, 180f, -90f)
				}

				prevLineWidth = lineWidth
			}

			for (a in count - 1 downTo 0) {
				prevLineWidth = if (a != 0) lineWidths[a - 1] else 0

				val lineWidth = lineWidths[a]
				val nextLineWidth = if (a != count - 1) lineWidths[a + 1] else 0
				val startX = (x - lineWidth / 2).toFloat()

				val innerCornerRad = if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
					cornerInSmall
				}
				else {
					cornerIn
				}

				if (a == count - 1 || lineWidth > nextLineWidth) {
					rect[startX - cornerRest, (y - corner * 2).toFloat(), startX + cornerOffset + corner] = y.toFloat()
					backgroundPath.arcTo(rect, 90f, 90f)
				}
				else if (lineWidth < nextLineWidth) {
					rect[startX - cornerRest - innerCornerRad * 2, (y - innerCornerRad * 2).toFloat(), startX - cornerRest] = y.toFloat()
					backgroundPath.arcTo(rect, 90f, -90f)
				}

				y -= lineHeights[a]

				if (a == 0 || lineWidth > prevLineWidth) {
					rect[startX - cornerRest, y.toFloat(), startX + cornerOffset + corner] = (y + corner * 2).toFloat()
					backgroundPath.arcTo(rect, 180f, 90f)
				}
				else if (lineWidth < prevLineWidth) {
					rect[startX - cornerRest - innerCornerRad * 2, y.toFloat(), startX - cornerRest] = (y + innerCornerRad * 2).toFloat()
					backgroundPath.arcTo(rect, 0f, -90f)
				}
			}

			backgroundPath.close()
		}

		if (!visiblePartSet) {
			val parent = parent as ViewGroup
			backgroundHeight = parent.measuredHeight
		}

		Theme.applyServiceShaderMatrix(measuredWidth, backgroundHeight, 0f, viewTop + AndroidUtilities.dp(4f))

		var oldAlpha = -1
		var oldAlpha2 = -1

		if (fromParent && alpha != 1f) {
			oldAlpha = backgroundPaint.alpha
			oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.alpha
			backgroundPaint.alpha = (oldAlpha * alpha).toInt()
			Theme.chat_actionBackgroundGradientDarkenPaint.alpha = (oldAlpha2 * alpha).toInt()
		}

		canvas.drawPath(backgroundPath, backgroundPaint)

		if (hasGradientService()) {
			canvas.drawPath(backgroundPath, Theme.chat_actionBackgroundGradientDarkenPaint)
		}

		val messageObject = messageObject

		if (messageObject != null && messageObject.type == MessageObject.TYPE_GIFT_PREMIUM) {
			val x = (width - giftRectSize) / 2f
			val y = (textY + textHeight + AndroidUtilities.dp(12f)).toFloat()

			AndroidUtilities.rectTmp[x, y, x + giftRectSize] = y + giftRectSize

			canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), backgroundPaint)

			if (hasGradientService()) {
				canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
			}
		}

		if (oldAlpha >= 0) {
			backgroundPaint.alpha = oldAlpha
			Theme.chat_actionBackgroundGradientDarkenPaint.alpha = oldAlpha2
		}
	}

	fun hasGradientService(): Boolean {
		return overrideBackgroundPaint == null && Theme.hasGradientService()
	}

	override fun onFailedDownload(fileName: String, canceled: Boolean) {
		// unused
	}

	override fun onSuccessDownload(fileName: String) {
		val messageObject = messageObject

		if (messageObject != null && messageObject.type == 11) {
			val strippedPhotoSize = messageObject.photoThumbs?.firstOrNull { it is TLRPC.TL_photoStrippedSize }
			photoImage.setImage(currentVideoLocation, ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(strippedPhotoSize, messageObject.photoThumbsObject), "50_50_b", avatarDrawable, 0, null, messageObject, 1)
			DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
		}
	}

	override fun onProgressDownload(fileName: String, downloadSize: Long, totalSize: Long) {
		// unused
	}

	override fun onProgressUpload(fileName: String, downloadSize: Long, totalSize: Long, isEncrypted: Boolean) {
		// unused
	}

	override fun getObserverTag(): Int {
		return tag
	}

	private var accessibilityText: SpannableStringBuilder? = null

	init {
		photoImage.setRoundRadius((AndroidUtilities.roundMessageSize * 0.45f).toInt())

		avatarDrawable = AvatarDrawable()

		tag = DownloadController.getInstance(currentAccount).generateObserverTag()

		giftTitlePaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
		giftSubtitlePaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15f, resources.displayMetrics)

		rippleView = View(context)
		rippleView.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background), Theme.RIPPLE_MASK_ROUNDRECT_6DP, AndroidUtilities.dp(16f))
		rippleView.visibility = GONE

		addView(rippleView)

		starParticlesDrawable = StarParticlesView.Drawable(10)
		starParticlesDrawable.type = 100
		starParticlesDrawable.isCircle = false
		starParticlesDrawable.roundEffect = true
		starParticlesDrawable.useRotate = false
		starParticlesDrawable.useBlur = true
		starParticlesDrawable.checkBounds = true
		starParticlesDrawable.size1 = 1
		starParticlesDrawable.k3 = 0.98f
		starParticlesDrawable.k2 = starParticlesDrawable.k3
		starParticlesDrawable.k1 = starParticlesDrawable.k2
		starParticlesDrawable.paused = false
		starParticlesDrawable.speedScale = 0f
		starParticlesDrawable.minLifeTime = 750
		starParticlesDrawable.randLifeTime = 750
		starParticlesDrawable.init()
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		val messageObject = messageObject

		if (TextUtils.isEmpty(customText) && messageObject == null) {
			return
		}

		if (accessibilityText == null) {
			val text = if (!TextUtils.isEmpty(customText)) customText else messageObject!!.messageText
			val sb = SpannableStringBuilder(text)
			val links = sb.getSpans(0, sb.length, ClickableSpan::class.java)

			for (link in links) {
				val start = sb.getSpanStart(link)
				val end = sb.getSpanEnd(link)

				sb.removeSpan(link)

				val underlineSpan: ClickableSpan = object : ClickableSpan() {
					override fun onClick(view: View) {
						if (delegate != null) {
							openLink(link)
						}
					}
				}

				sb.setSpan(underlineSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			accessibilityText = sb
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			info.contentDescription = accessibilityText.toString()
		}
		else {
			info.text = accessibilityText
		}

		info.isEnabled = true
	}

	fun setInvalidateColors(invalidate: Boolean) {
		if (invalidateColors == invalidate) {
			return
		}

		invalidateColors = invalidate

		invalidate()
	}

	private fun getThemedPaint(paintKey: String): Paint {
		return Theme.getThemePaint(paintKey)
	}

	companion object {
		private const val USE_PREMIUM_GIFT_LOCAL_STICKER = false
		private const val USE_PREMIUM_GIFT_MONTHS_AS_EMOJI_NUMBERS = false
		private val monthsToEmoticon: MutableMap<Int, String> = HashMap()

		init {
			monthsToEmoticon[1] = 1.toString() + "\u20E3"
			monthsToEmoticon[3] = 2.toString() + "\u20E3"
			monthsToEmoticon[6] = 3.toString() + "\u20E3"
			monthsToEmoticon[12] = 4.toString() + "\u20E3"
			monthsToEmoticon[24] = 5.toString() + "\u20E3"
		}
	}
}
