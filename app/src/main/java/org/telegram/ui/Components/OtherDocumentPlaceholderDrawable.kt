/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import com.beint.elloapp.FileHelper
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DownloadController
import org.telegram.messenger.DownloadController.FileDownloadProgressListener
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.media
import org.telegram.ui.ActionBar.Theme
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

class OtherDocumentPlaceholderDrawable @JvmOverloads constructor(context: Context, private var parentView: View?, messageObject: MessageObject? = null, path: String? = null) : RecyclableDrawable(), FileDownloadProgressListener {
	private var lastUpdateTime: Long = 0

	var currentProgress = 0f
		private set

	private var animationProgressStart = 0f
	private var currentProgressTime: Long = 0
	private var animatedProgressValue = 0f
	private var animatedAlphaValue = 1.0f
	private var progressVisible = false
	private var parentMessageObject: MessageObject?
	private val observerTag: Int
	private var loading = false
	private var loaded = false
	private var thumbDrawable: Drawable? = null
	private var ext: String? = null
	private var fileName: String? = null
	private var fileSize: String? = null
	private var progress: String? = null

	init {
		docPaint.textSize = AndroidUtilities.dp(14f).toFloat()
		namePaint.textSize = AndroidUtilities.dp(19f).toFloat()
		sizePaint.textSize = AndroidUtilities.dp(15f).toFloat()
		buttonPaint.textSize = AndroidUtilities.dp(15f).toFloat()
		percentPaint.textSize = AndroidUtilities.dp(15f).toFloat()
		openPaint.textSize = AndroidUtilities.dp(15f).toFloat()
		progressPaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()

		parentMessageObject = messageObject

		observerTag = DownloadController.getInstance(messageObject?.currentAccount ?: 0).generateObserverTag()

		val document = messageObject?.document

		if (document != null) {
			fileName = FileLoader.getDocumentFileName(messageObject.document)

			if (fileName.isNullOrEmpty()) {
				fileName = "name"
			}
		}
		else if (!path.isNullOrEmpty()) {
			fileName = path.substringAfterLast("/").substringBeforeLast(".")

			if (fileName.isNullOrEmpty()) {
				fileName = "name"
			}
		}

		if (!path.isNullOrEmpty()) {
			ext = path.substringAfterLast(".").uppercase()
		}
		else if (!fileName.isNullOrEmpty()) {
			ext = fileName?.substringAfterLast(".")?.uppercase()
		}

		if (ext.isNullOrEmpty()) {
			ext = ""
		}

		var w = ceil(docPaint.measureText(ext).toDouble()).toInt()

		if (w > AndroidUtilities.dp(40f)) {
			ext = TextUtils.ellipsize(ext, docPaint, AndroidUtilities.dp(40f).toFloat(), TextUtils.TruncateAt.END).toString()
		}

		val mime = messageObject?.document?.mimeType ?: path?.let { FileHelper.getMimeType(it) }

		thumbDrawable = ResourcesCompat.getDrawable(context.resources, AndroidUtilities.getThumbForNameOrMime(fileName, mime, true), null)?.mutate()

		if (document != null) {
			fileSize = AndroidUtilities.formatFileSize(document.size)
		}
		else if (path != null) {
			fileSize = AndroidUtilities.formatFileSize(File(path).length())
		}

		w = ceil(namePaint.measureText(fileName).toDouble()).toInt()

		if (w > AndroidUtilities.dp(320f)) {
			fileName = TextUtils.ellipsize(fileName, namePaint, AndroidUtilities.dp(320f).toFloat(), TextUtils.TruncateAt.END).toString()
		}

		checkFileExist()
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		// unused
	}

	override fun setAlpha(alpha: Int) {
		thumbDrawable?.alpha = alpha
		paint.alpha = alpha
		docPaint.alpha = alpha
		namePaint.alpha = alpha
		sizePaint.alpha = alpha
		buttonPaint.alpha = alpha
		percentPaint.alpha = alpha
		openPaint.alpha = alpha
	}

	override fun draw(canvas: Canvas) {
		val bounds = bounds
		val width = bounds.width()
		val height = bounds.height()

		canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
			drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

			var y = (height - AndroidUtilities.dp(240f)) / 2
			var x = (width - AndroidUtilities.dp(48f)) / 2
			var w: Int

			thumbDrawable?.let {
				it.setBounds(x, y, x + AndroidUtilities.dp(48f), y + AndroidUtilities.dp(48f))
				it.draw(this)
			}

			ext?.let {
				w = ceil(docPaint.measureText(it).toDouble()).toInt()
				drawText(it, ((width - w) / 2).toFloat(), (y + AndroidUtilities.dp(31f)).toFloat(), docPaint)
			}

			fileName?.let {
				w = ceil(namePaint.measureText(it).toDouble()).toInt()
				drawText(it, ((width - w) / 2).toFloat(), (y + AndroidUtilities.dp(96f)).toFloat(), namePaint)
			}

			fileSize?.let {
				w = ceil(sizePaint.measureText(it).toDouble()).toInt()
				drawText(it, ((width - w) / 2).toFloat(), (y + AndroidUtilities.dp(125f)).toFloat(), sizePaint)
			}

			val button: String
			val paint: TextPaint
			val offsetY: Int

			if (loaded) {
				button = ApplicationLoader.applicationContext.getString(R.string.OpenFile)
				paint = openPaint
				offsetY = 0
			}
			else {
				button = if (loading) {
					ApplicationLoader.applicationContext.getString(R.string.Cancel).uppercase()
				}
				else {
					ApplicationLoader.applicationContext.getString(R.string.TapToDownload)
				}

				offsetY = AndroidUtilities.dp(28f)
				paint = buttonPaint
			}

			w = ceil(paint.measureText(button).toDouble()).toInt()
			drawText(button, ((width - w) / 2).toFloat(), (y + AndroidUtilities.dp(235f) + offsetY).toFloat(), paint)

			if (progressVisible) {
				progress?.let {
					w = ceil(percentPaint.measureText(it).toDouble()).toInt()
					drawText(it, ((width - w) / 2).toFloat(), (y + AndroidUtilities.dp(210f)).toFloat(), percentPaint)
				}

				x = (width - AndroidUtilities.dp(240f)) / 2
				y += AndroidUtilities.dp(232f)

				progressPaint.color = -0x9d948b
				progressPaint.alpha = (255 * animatedAlphaValue).toInt()

				val start = (AndroidUtilities.dp(240f) * animatedProgressValue).toInt()

				drawRect((x + start).toFloat(), y.toFloat(), (x + AndroidUtilities.dp(240f)).toFloat(), (y + AndroidUtilities.dp(2f)).toFloat(), progressPaint)

				progressPaint.color = -0x1
				progressPaint.alpha = (255 * animatedAlphaValue).toInt()

				drawRect(x.toFloat(), y.toFloat(), x + AndroidUtilities.dp(240f) * animatedProgressValue, (y + AndroidUtilities.dp(2f)).toFloat(), progressPaint)

				updateAnimation()
			}
		}
	}

	override fun getIntrinsicWidth(): Int {
		return parentView?.measuredWidth ?: 0
	}

	override fun getIntrinsicHeight(): Int {
		return parentView?.measuredHeight ?: 0
	}

	override fun getMinimumWidth(): Int {
		return parentView?.measuredWidth ?: 0
	}

	override fun getMinimumHeight(): Int {
		return parentView?.measuredHeight ?: 0
	}

	@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat"))
	override fun getOpacity(): Int {
		return PixelFormat.OPAQUE
	}

	override fun onFailedDownload(name: String, canceled: Boolean) {
		checkFileExist()
	}

	override fun onSuccessDownload(name: String) {
		setProgress(1f, true)
		checkFileExist()
	}

	override fun onProgressDownload(fileName: String, downloadedSize: Long, totalSize: Long) {
		if (!progressVisible) {
			checkFileExist()
		}

		setProgress(min(1f, downloadedSize / totalSize.toFloat()), true)
	}

	override fun onProgressUpload(fileName: String, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) {
		// unused
	}

	override fun getObserverTag(): Int {
		return observerTag
	}

	override fun recycle() {
		DownloadController.getInstance(parentMessageObject?.currentAccount ?: 0).removeLoadingFileObserver(this)
		parentView = null
		parentMessageObject = null
	}

	fun checkFileExist() {
		val parentMessageObject = parentMessageObject ?: return

		if (parentMessageObject.messageOwner?.media != null) {
			var fileName: String? = null
			val cacheFile: File

			val attachPath = parentMessageObject.messageOwner?.attachPath

			if (attachPath.isNullOrEmpty() || !File(attachPath).exists()) {
				cacheFile = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(parentMessageObject.messageOwner)

				if (!cacheFile.exists()) {
					fileName = FileLoader.getAttachFileName(parentMessageObject.document)
				}
			}

			loaded = false

			if (fileName == null) {
				progressVisible = false
				loading = false
				loaded = true

				DownloadController.getInstance(parentMessageObject.currentAccount).removeLoadingFileObserver(this)
			}
			else {
				DownloadController.getInstance(parentMessageObject.currentAccount).addLoadingFileObserver(fileName, this)

				loading = FileLoader.getInstance(parentMessageObject.currentAccount).isLoadingFile(fileName)

				if (loading) {
					progressVisible = true

					var progress = ImageLoader.getInstance().getFileProgress(fileName)

					if (progress == null) {
						progress = 0.0f
					}

					setProgress(progress, false)
				}
				else {
					progressVisible = false
				}
			}
		}
		else {
			loading = false
			loaded = true
			progressVisible = false
			setProgress(0f, false)
			DownloadController.getInstance(parentMessageObject.currentAccount).removeLoadingFileObserver(this)
		}

		parentView?.invalidate()
	}

	private fun updateAnimation() {
		val newTime = System.currentTimeMillis()
		val dt = newTime - lastUpdateTime

		lastUpdateTime = newTime

		if (animatedProgressValue != 1f && animatedProgressValue != currentProgress) {
			val progressDiff = currentProgress - animationProgressStart

			if (progressDiff > 0) {
				currentProgressTime += dt

				if (currentProgressTime >= 300) {
					animatedProgressValue = currentProgress
					animationProgressStart = currentProgress
					currentProgressTime = 0
				}
				else {
					animatedProgressValue = animationProgressStart + progressDiff * decelerateInterpolator.getInterpolation(currentProgressTime / 300.0f)
				}
			}

			parentView?.invalidate()
		}

		if (animatedProgressValue >= 1 && animatedProgressValue == 1f && animatedAlphaValue != 0f) {
			animatedAlphaValue -= dt / 200.0f

			if (animatedAlphaValue <= 0) {
				animatedAlphaValue = 0.0f
			}

			parentView?.invalidate()
		}
	}

	fun setProgress(value: Float, animated: Boolean) {
		if (!animated) {
			animatedProgressValue = value
			animationProgressStart = value
		}
		else {
			animationProgressStart = animatedProgressValue
		}

		progress = String.format(Locale.getDefault(), "%d%%", (100 * value).toInt())

		if (value != 1f) {
			animatedAlphaValue = 1f
		}

		currentProgress = value
		currentProgressTime = 0
		lastUpdateTime = System.currentTimeMillis()

		parentView?.invalidate()
	}

	companion object {
		private val paint = Paint()
		private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val docPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = Theme.TYPEFACE_BOLD }
		private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = Theme.TYPEFACE_BOLD }
		private val sizePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = Theme.TYPEFACE_DEFAULT }
		private val buttonPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = Theme.TYPEFACE_BOLD }
		private val percentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = Theme.TYPEFACE_BOLD }
		private val openPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = Theme.TYPEFACE_BOLD }
		private val decelerateInterpolator = DecelerateInterpolator()

		init {
			progressPaint.strokeCap = Paint.Cap.ROUND
			paint.color = -0xd8d3ce
			docPaint.color = -0x1
			namePaint.color = -0x1
			sizePaint.color = -0x9d948b
			buttonPaint.color = -0x9d948b
			percentPaint.color = -0x1
			openPaint.color = -0x1
		}
	}
}
