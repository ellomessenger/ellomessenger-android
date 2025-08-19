/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Cells

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DownloadController
import org.telegram.messenger.DownloadController.FileDownloadProgressListener
import org.telegram.messenger.Emoji.replaceEmoji
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.message
import org.telegram.tgnet.thumbs
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.DotDividerSpan
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.MediaActionDrawable
import org.telegram.ui.Components.RadialProgress2
import org.telegram.ui.FilteredSearchView
import kotlin.math.ceil
import kotlin.math.min

open class SharedAudioCell @JvmOverloads constructor(context: Context, private val viewType: Int = VIEW_TYPE_DEFAULT) : FrameLayout(context), FileDownloadProgressListener, NotificationCenterDelegate {
	private val checkBox: CheckBox2
	private val titleY = AndroidUtilities.dp(9f)
	private val currentAccount = UserConfig.selectedAccount
	private val tag = DownloadController.getInstance(currentAccount).generateObserverTag()
	private val radialProgress: RadialProgress2
	private val captionTextPaint: TextPaint
	private var showReorderIcon = false
	private var showReorderIconProgress = 0f
	private var enterAlpha = 1f
	private var dotSpan: SpannableStringBuilder? = null
	private var needDivider = false
	private var buttonPressed = false
	private var miniButtonPressed = false
	private var hasMiniProgress = 0
	private var buttonX = 0
	private var buttonY = 0
	private var titleLayout: StaticLayout? = null
	private var descriptionY = AndroidUtilities.dp(29f)
	private var descriptionLayout: StaticLayout? = null
	private var captionY = AndroidUtilities.dp(29f)
	private var captionLayout: StaticLayout? = null
	private var checkForButtonPress = false
	private var buttonState = 0
	private var miniButtonState = 0
	private var dateLayout: StaticLayout? = null
	private var dateLayoutX = 0
	private var description2TextPaint: TextPaint? = null
	var globalGradientView: FlickerLoadingView? = null

	var message: MessageObject? = null
		private set

	init {
		isFocusable = true
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

		radialProgress = RadialProgress2(this)
		radialProgress.setColors(context.getColor(R.color.brand), context.getColor(R.color.darker_brand), context.getColor(R.color.white), context.getColor(R.color.light_gray_fixed))

		setWillNotDraw(false)

		checkBox = CheckBox2(context, 22)
		checkBox.visibility = INVISIBLE
		checkBox.setColor(0, context.getColor(R.color.background), context.getColor(R.color.brand))
		checkBox.setDrawUnchecked(false)
		checkBox.setDrawBackgroundAsArc(3)

		addView(checkBox, LayoutHelper.createFrame(24, 24f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 0f else 38.1f, 32.1f, (if (LocaleController.isRTL) 6 else 0).toFloat(), 0f))

		if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
			description2TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
			description2TextPaint?.textSize = AndroidUtilities.dp(13f).toFloat()
			description2TextPaint?.setTypeface(Theme.TYPEFACE_DEFAULT)

			dotSpan = SpannableStringBuilder(".")
			dotSpan?.setSpan(DotDividerSpan(), 0, 1, 0)
		}

		captionTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		captionTextPaint.textSize = AndroidUtilities.dp(13f).toFloat()
		captionTextPaint.setTypeface(Theme.TYPEFACE_DEFAULT)
	}

	@SuppressLint("DrawAllocation")
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		descriptionLayout = null
		titleLayout = null
		captionLayout = null

		val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
		val maxWidth = viewWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat()) - AndroidUtilities.dp((8 + 20).toFloat())
		var dateWidth = 0

		if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
			val str = LocaleController.stringForMessageListDate(message!!.messageOwner!!.date.toLong())
			val width = ceil(description2TextPaint!!.measureText(str).toDouble()).toInt()
			dateLayout = ChatMessageCell.generateStaticLayout(str, description2TextPaint, width, width, 0, 1)
			dateLayoutX = maxWidth - width - AndroidUtilities.dp(8f) + AndroidUtilities.dp(20f)
			dateWidth = width + AndroidUtilities.dp(12f)
		}

		try {
			var title = if (viewType == VIEW_TYPE_GLOBAL_SEARCH && (message?.isVoice == true || message?.isRoundVideo == true)) {
				FilteredSearchView.createFromInfoString(message)
			}
			else {
				message?.musicTitle?.replace('\n', ' ')
			}

			val titleH = AndroidUtilities.highlightText(title, message?.highlightedWords)

			if (titleH != null) {
				title = titleH
			}

			val titleFinal = TextUtils.ellipsize(title, Theme.chat_contextResult_titleTextPaint, (maxWidth - dateWidth).toFloat(), TextUtils.TruncateAt.END)

			titleLayout = StaticLayout(titleFinal, Theme.chat_contextResult_titleTextPaint, maxWidth + AndroidUtilities.dp(4f) - dateWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		if (message!!.hasHighlightedWords()) {
			val caption = replaceEmoji(message?.messageOwner?.message?.replace("\n", " ")?.replace(" +".toRegex(), " ")?.trim(), Theme.chat_msgTextPaint.fontMetricsInt, false)
			var sequence = AndroidUtilities.highlightText(caption, message!!.highlightedWords)

			if (sequence != null) {
				sequence = TextUtils.ellipsize(AndroidUtilities.ellipsizeCenterEnd(sequence, message!!.highlightedWords!![0], maxWidth, captionTextPaint, 130), captionTextPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)
				captionLayout = StaticLayout(sequence, captionTextPaint, maxWidth + AndroidUtilities.dp(4f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
		}

		try {
			if (viewType == VIEW_TYPE_GLOBAL_SEARCH && (message!!.isVoice || message!!.isRoundVideo)) {
				var duration: CharSequence? = AndroidUtilities.formatDuration(message!!.duration, false)
				val paint = description2TextPaint
				duration = TextUtils.ellipsize(duration, paint, maxWidth.toFloat(), TextUtils.TruncateAt.END)
				descriptionLayout = StaticLayout(duration, paint, maxWidth + AndroidUtilities.dp(4f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
			else {
				var author: CharSequence? = message!!.musicAuthor!!.replace('\n', ' ')
				val authorH = AndroidUtilities.highlightText(author, message!!.highlightedWords)

				if (authorH != null) {
					author = authorH
				}

				if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
					author = SpannableStringBuilder(author).append(' ').append(dotSpan).append(' ').append(FilteredSearchView.createFromInfoString(message))
				}

				val paint = if (viewType == VIEW_TYPE_GLOBAL_SEARCH) description2TextPaint else Theme.chat_contextResult_descriptionTextPaint

				author = TextUtils.ellipsize(author, paint, maxWidth.toFloat(), TextUtils.TruncateAt.END)

				descriptionLayout = StaticLayout(author, paint, maxWidth + AndroidUtilities.dp(4f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(56f) + (if (captionLayout == null) 0 else AndroidUtilities.dp(18f)) + (if (needDivider) 1 else 0))

		val maxPhotoWidth = AndroidUtilities.dp(52f)
		val x = if (LocaleController.isRTL) MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(8f) - maxPhotoWidth else AndroidUtilities.dp(8f)

		radialProgress.setProgressRect((x + AndroidUtilities.dp(4f)).also { buttonX = it }, AndroidUtilities.dp(6f).also { buttonY = it }, x + AndroidUtilities.dp(48f), AndroidUtilities.dp(50f))

		measureChildWithMargins(checkBox, widthMeasureSpec, 0, heightMeasureSpec, 0)

		if (captionLayout != null) {
			captionY = AndroidUtilities.dp(29f)
			descriptionY = AndroidUtilities.dp(29f) + AndroidUtilities.dp(18f)
		}
		else {
			descriptionY = AndroidUtilities.dp(29f)
		}
	}

	fun setMessageObject(messageObject: MessageObject, divider: Boolean) {
		needDivider = divider
		message = messageObject

		val document = messageObject.document
		val thumb = FileLoader.getClosestPhotoSizeWithSize(document?.thumbs, 360)

		if (thumb is TLRPC.TLPhotoSize || thumb is TLRPC.TLPhotoSizeProgressive) {
			radialProgress.setImageOverlay(thumb, document, messageObject)
		}
		else {
			val artworkUrl = messageObject.getArtworkUrl(true)

			if (!artworkUrl.isNullOrEmpty()) {
				radialProgress.setImageOverlay(artworkUrl)
			}
			else {
				radialProgress.setImageOverlay(null, null, null)
			}
		}

		updateButtonState(ifSame = false, animated = false)

		requestLayout()
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		if (checkBox.visibility != VISIBLE) {
			checkBox.visibility = VISIBLE
		}

		checkBox.setChecked(checked, animated)
	}

	fun setCheckForButtonPress(value: Boolean) {
		checkForButtonPress = value
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		radialProgress.onAttachedToWindow()

		updateButtonState(ifSame = false, animated = false)

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.messagePlayingDidReset)
			it.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.addObserver(this, NotificationCenter.messagePlayingDidStart)
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)

		radialProgress.onDetachedFromWindow()

		NotificationCenter.getInstance(currentAccount).let {
			it.removeObserver(this, NotificationCenter.messagePlayingDidReset)
			it.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.removeObserver(this, NotificationCenter.messagePlayingDidStart)
		}
	}

	fun initStreamingIcons() {
		radialProgress.initMiniIcons()
	}

	private fun checkAudioMotionEvent(event: MotionEvent): Boolean {
		val x = event.x.toInt()
		val y = event.y.toInt()
		var result = false
		val side = AndroidUtilities.dp(36f)
		var area = false

		if (miniButtonState >= 0) {
			val offset = AndroidUtilities.dp(27f)
			area = x >= buttonX + offset && x <= buttonX + offset + side && y >= buttonY + offset && y <= buttonY + offset + side
		}

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (area) {
				miniButtonPressed = true
				radialProgress.setPressed(true, true)
				invalidate()
				result = true
			}
			else if (checkForButtonPress && radialProgress.progressRect.contains(x.toFloat(), y.toFloat())) {
				buttonPressed = true
				radialProgress.setPressed(true, false)
				invalidate()
				result = true
			}
		}
		else if (event.action == MotionEvent.ACTION_UP) {
			if (miniButtonPressed) {
				miniButtonPressed = false
				playSoundEffect(SoundEffectConstants.CLICK)
				didPressedMiniButton()
				invalidate()
			}
			else if (buttonPressed) {
				buttonPressed = false
				playSoundEffect(SoundEffectConstants.CLICK)
				didPressedButton()
				invalidate()
			}
		}
		else if (event.action == MotionEvent.ACTION_CANCEL) {
			miniButtonPressed = false
			buttonPressed = false
			invalidate()
		}
		else if (event.action == MotionEvent.ACTION_MOVE) {
			if (!area && miniButtonPressed) {
				miniButtonPressed = false
				invalidate()
			}
		}

		radialProgress.setPressed(miniButtonPressed, true)

		return result
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (message == null) {
			return super.onTouchEvent(event)
		}

		var result = checkAudioMotionEvent(event)

		if (event.action == MotionEvent.ACTION_CANCEL) {
			miniButtonPressed = false
			buttonPressed = false
			result = false
			radialProgress.setPressed(false, false)
			radialProgress.setPressed(false, true)
		}

		return result
	}

	private fun didPressedMiniButton() {
		if (miniButtonState == 0) {
			miniButtonState = 1
			radialProgress.setProgress(0f, false)
			FileLoader.getInstance(currentAccount).loadFile(message!!.document, message, FileLoader.PRIORITY_NORMAL, 0)
			radialProgress.setMiniIcon(miniIconForCurrentState, false, true)
			invalidate()
		}
		else if (miniButtonState == 1) {
			if (MediaController.getInstance().isPlayingMessage(message)) {
				MediaController.getInstance().cleanupPlayer(true, true)
			}

			miniButtonState = 0
			FileLoader.getInstance(currentAccount).cancelLoadFile(message!!.document)
			radialProgress.setMiniIcon(miniIconForCurrentState, false, true)
			invalidate()
		}
	}

	fun didPressedButton() {
		if (buttonState == 0) {
			if (miniButtonState == 0) {
				message!!.putInDownloadsStore = true
				FileLoader.getInstance(currentAccount).loadFile(message!!.document, message, FileLoader.PRIORITY_NORMAL, 0)
			}

			if (needPlayMessage(message!!)) {
				if (hasMiniProgress == 2 && miniButtonState != 1) {
					miniButtonState = 1
					radialProgress.setProgress(0f, false)
					radialProgress.setMiniIcon(miniIconForCurrentState, false, true)
				}

				buttonState = 1
				radialProgress.setIcon(iconForCurrentState, false, true)
				invalidate()
			}
		}
		else if (buttonState == 1) {
			val result = MediaController.getInstance().pauseMessage(message)

			if (result) {
				buttonState = 0
				radialProgress.setIcon(iconForCurrentState, false, true)
				invalidate()
			}
		}
		else if (buttonState == 2) {
			radialProgress.setProgress(0f, false)
			message!!.putInDownloadsStore = true
			FileLoader.getInstance(currentAccount).loadFile(message!!.document, message, FileLoader.PRIORITY_NORMAL, 0)
			buttonState = 4
			radialProgress.setIcon(iconForCurrentState, false, true)
			invalidate()
		}
		else if (buttonState == 4) {
			FileLoader.getInstance(currentAccount).cancelLoadFile(message!!.document)
			buttonState = 2
			radialProgress.setIcon(iconForCurrentState, false, true)
			invalidate()
		}
	}

	private val miniIconForCurrentState: Int
		get() {
			if (miniButtonState < 0) {
				return MediaActionDrawable.ICON_NONE
			}
			return if (miniButtonState == 0) {
				MediaActionDrawable.ICON_DOWNLOAD
			}
			else {
				MediaActionDrawable.ICON_CANCEL
			}
		}

	private val iconForCurrentState: Int
		get() {
			return when (buttonState) {
				1 -> MediaActionDrawable.ICON_PAUSE
				2 -> MediaActionDrawable.ICON_DOWNLOAD
				4 -> MediaActionDrawable.ICON_CANCEL
				else -> MediaActionDrawable.ICON_PLAY
			}
		}

	fun updateButtonState(ifSame: Boolean, animated: Boolean) {
		val fileName = message?.fileName

		if (fileName.isNullOrEmpty()) {
			return
		}

		var fileExists = message!!.attachPathExists || message!!.mediaExists

		if (SharedConfig.streamMedia && message!!.isMusic && message!!.dialogId.toInt() != 0) {
			hasMiniProgress = if (fileExists) 1 else 2
			fileExists = true
		}
		else {
			hasMiniProgress = 0
			miniButtonState = -1
		}

		if (hasMiniProgress != 0) {
			radialProgress.setMiniProgressBackgroundColor(if (message!!.isOutOwner) context.getColor(R.color.brand) else context.getColor(R.color.brand))

			val playing = MediaController.getInstance().isPlayingMessage(message)

			buttonState = if (!playing || MediaController.getInstance().isMessagePaused) {
				0
			}
			else {
				1
			}

			radialProgress.setIcon(iconForCurrentState, ifSame, animated)

			if (hasMiniProgress == 1) {
				DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
				miniButtonState = -1
				radialProgress.setMiniIcon(miniIconForCurrentState, ifSame, animated)
			}
			else {
				DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, message, this)

				if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
					miniButtonState = 0
					radialProgress.setMiniIcon(miniIconForCurrentState, ifSame, animated)
				}
				else {
					miniButtonState = 1
					radialProgress.setMiniIcon(miniIconForCurrentState, ifSame, animated)

					val progress = ImageLoader.getInstance().getFileProgress(fileName)

					if (progress != null) {
						radialProgress.setProgress(progress, animated)
					}
					else {
						radialProgress.setProgress(0f, animated)
					}
				}
			}
		}
		else if (fileExists) {
			DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)

			val playing = MediaController.getInstance().isPlayingMessage(message)

			buttonState = if (!playing || MediaController.getInstance().isMessagePaused) {
				0
			}
			else {
				1
			}

			radialProgress.setProgress(1f, animated)
			radialProgress.setIcon(iconForCurrentState, ifSame, animated)

			invalidate()
		}
		else {
			DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, message, this)

			val isLoading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName)

			if (!isLoading) {
				buttonState = 2
				radialProgress.setProgress(0f, animated)
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

			radialProgress.setIcon(iconForCurrentState, ifSame, animated)

			invalidate()
		}
	}

	override fun onFailedDownload(fileName: String, canceled: Boolean) {
		updateButtonState(true, canceled)
	}

	override fun onSuccessDownload(fileName: String) {
		radialProgress.setProgress(1f, true)
		updateButtonState(ifSame = false, animated = true)
	}

	override fun onProgressDownload(fileName: String, downloadSize: Long, totalSize: Long) {
		val progress = min(1.0, (downloadSize / totalSize.toFloat()).toDouble()).toFloat()

		radialProgress.setProgress(progress, true)

		if (hasMiniProgress != 0) {
			if (miniButtonState != 1) {
				updateButtonState(ifSame = false, animated = true)
			}
		}
		else {
			if (buttonState != 4) {
				updateButtonState(ifSame = false, animated = true)
			}
		}
	}

	override fun onProgressUpload(fileName: String, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) {
		// unused
	}

	override fun getObserverTag(): Int {
		return tag
	}

	protected open fun needPlayMessage(messageObject: MessageObject): Boolean {
		return false
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.isEnabled = true

		if (message!!.isMusic) {
			info.text = LocaleController.formatString("AccDescrMusicInfo", R.string.AccDescrMusicInfo, message!!.musicAuthor, message!!.musicTitle)
		}
		else if (titleLayout != null && descriptionLayout != null) {
			info.text = titleLayout!!.text.toString() + ", " + descriptionLayout!!.text
		}

		if (checkBox.isChecked) {
			info.isCheckable = true
			info.isChecked = true
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		updateButtonState(ifSame = false, animated = true)
	}

	override fun dispatchDraw(canvas: Canvas) {
		if (enterAlpha != 1f && globalGradientView != null) {
			canvas.saveLayerAlpha(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), ((1f - enterAlpha) * 255).toInt())

			globalGradientView?.setViewType(FlickerLoadingView.AUDIO_TYPE)
			globalGradientView?.updateColors()
			globalGradientView?.updateGradient()
			globalGradientView?.draw(canvas)

			canvas.restore()
			canvas.saveLayerAlpha(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), (enterAlpha * 255).toInt())

			drawInternal(canvas)

			super.dispatchDraw(canvas)

			drawReorder(canvas)
			canvas.restore()
		}
		else {
			drawInternal(canvas)
			drawReorder(canvas)
			super.dispatchDraw(canvas)
		}
	}

	private fun drawReorder(canvas: Canvas) {
		if (showReorderIcon || showReorderIconProgress != 0f) {
			if (showReorderIcon && showReorderIconProgress != 1f) {
				showReorderIconProgress += 16 / 150f
				invalidate()
			}
			else if (!showReorderIcon) {
				showReorderIconProgress -= 16 / 150f
				invalidate()
			}

			showReorderIconProgress = Utilities.clamp(showReorderIconProgress, 1f, 0f)

			val x = measuredWidth - AndroidUtilities.dp(12f) - Theme.dialogs_reorderDrawable.intrinsicWidth
			val y = (measuredHeight - Theme.dialogs_reorderDrawable.intrinsicHeight) shr 1

			canvas.withScale(showReorderIconProgress, showReorderIconProgress, x + Theme.dialogs_reorderDrawable.intrinsicWidth / 2f, y + Theme.dialogs_reorderDrawable.intrinsicHeight / 2f) {
				Theme.dialogs_reorderDrawable.setBounds(x, y, x + Theme.dialogs_reorderDrawable.intrinsicWidth, y + Theme.dialogs_reorderDrawable.intrinsicHeight)
				Theme.dialogs_reorderDrawable.draw(this)
			}
		}
	}

	private fun drawInternal(canvas: Canvas) {
		if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
			description2TextPaint?.color = context.getColor(R.color.dark_gray)
		}

		if (dateLayout != null) {
			canvas.withTranslation((AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()) + (if (LocaleController.isRTL) 0 else dateLayoutX)).toFloat(), titleY.toFloat()) {
				dateLayout?.draw(this)
			}
		}

		if (titleLayout != null) {
			canvas.withTranslation((AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()) + (if (LocaleController.isRTL && dateLayout != null) dateLayout!!.width + AndroidUtilities.dp(4f) else 0)).toFloat(), titleY.toFloat()) {
				titleLayout?.draw(this)
			}
		}

		if (captionLayout != null) {
			captionTextPaint.color = context.getColor(R.color.dark_fixed)

			canvas.withTranslation(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), captionY.toFloat()) {
				captionLayout?.draw(this)
			}
		}

		if (descriptionLayout != null) {
			Theme.chat_contextResult_descriptionTextPaint.color = context.getColor(R.color.dark_gray)

			canvas.withTranslation(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), descriptionY.toFloat()) {
				descriptionLayout?.draw(this)
			}
		}

		radialProgress.setProgressColor(if (buttonPressed) context.getColor(R.color.medium_gray) else context.getColor(R.color.white))
		radialProgress.draw(canvas)

		if (needDivider) {
			canvas.drawLine(AndroidUtilities.dp(72f).toFloat(), (height - 1).toFloat(), (width - paddingRight).toFloat(), (height - 1).toFloat(), Theme.dividerPaint)
		}
	}

	fun setEnterAnimationAlpha(alpha: Float) {
		if (enterAlpha != alpha) {
			this.enterAlpha = alpha
			invalidate()
		}
	}

	fun showReorderIcon(show: Boolean, animated: Boolean) {
		if (showReorderIcon == show) {
			return
		}

		showReorderIcon = show

		if (!animated) {
			showReorderIconProgress = if (show) 1f else 0f
		}

		invalidate()
	}

	companion object {
		const val VIEW_TYPE_DEFAULT = 0
		const val VIEW_TYPE_GLOBAL_SEARCH = 1
	}
}
