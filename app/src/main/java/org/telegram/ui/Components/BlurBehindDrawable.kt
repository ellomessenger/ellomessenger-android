/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import kotlin.math.max

class BlurBehindDrawable(private val behindView: View, private val parentView: View, private val type: Int) {
	var queue: DispatchQueue? = null
	private var blurredBitmapTmp: Array<Bitmap?>? = null
	private var backgroundBitmap: Array<Bitmap?>? = null
	private var renderingBitmap: Array<Bitmap?>? = null
	private var renderingBitmapCanvas: Array<Canvas?>? = null
	private var backgroundBitmapCanvas: Array<Canvas?>? = null
	private var blurCanvas: Array<Canvas?>? = null
	private var processingNextFrame = false
	private var invalidate = true
	private var blurAlpha = 0f
	private var show = false
	private var error = false
	private var animateAlpha = true
	private val downscale = 6f
	private var lastH = 0
	private var lastW = 0
	private var toolbarH = 0
	private var wasDraw = false
	private var skipDraw = false
	private var panTranslationY = 0f
	private var blurBackgroundTask = BlurBackgroundTask()
	private val errorBlackoutPaint = Paint()
	private val emptyPaint = Paint(Paint.FILTER_BITMAP_FLAG)

	init {
		errorBlackoutPaint.color = Color.BLACK
	}

	fun draw(canvas: Canvas) {
		if (type == 1 && !wasDraw && !animateAlpha) {
			generateBlurredBitmaps()
			invalidate = false
		}

		val bitmap = renderingBitmap

		if ((bitmap != null || error) && animateAlpha) {
			if (show && blurAlpha != 1f) {
				blurAlpha += 0.09f

				if (blurAlpha > 1f) {
					blurAlpha = 1f
				}

				parentView.invalidate()
			}
			else if (!show && blurAlpha != 0f) {
				blurAlpha -= 0.09f

				if (blurAlpha < 0) {
					blurAlpha = 0f
				}

				parentView.invalidate()
			}
		}

		val alpha = if (animateAlpha) blurAlpha else 1f

		if (bitmap == null && error) {
			errorBlackoutPaint.alpha = (50 * alpha).toInt()
			canvas.drawPaint(errorBlackoutPaint)
			return
		}

		if (alpha == 1f) {
			canvas.save()
		}
		else {
			canvas.saveLayerAlpha(0f, 0f, parentView.measuredWidth.toFloat(), parentView.measuredHeight.toFloat(), (alpha * 255).toInt())
		}

		if (bitmap != null) {
			emptyPaint.alpha = (255 * alpha).toInt()

			if (type == 1) {
				canvas.translate(0f, panTranslationY)
			}

			canvas.save()
			canvas.scale(parentView.measuredWidth / bitmap[1]!!.width.toFloat(), parentView.measuredHeight / bitmap[1]!!.height.toFloat())
			canvas.drawBitmap(bitmap[1]!!, 0f, 0f, emptyPaint)
			canvas.restore()
			canvas.save()

			if (type == 0) {
				canvas.translate(0f, panTranslationY)
			}

			canvas.scale(parentView.measuredWidth / bitmap[0]!!.width.toFloat(), toolbarH / bitmap[0]!!.height.toFloat())
			canvas.drawBitmap(bitmap[0]!!, 0f, 0f, emptyPaint)
			canvas.restore()

			wasDraw = true

			canvas.drawColor(0x1a000000)
		}

		canvas.restore()

		if (show && !processingNextFrame && (renderingBitmap == null || invalidate)) {
			processingNextFrame = true
			invalidate = false

			if (blurredBitmapTmp == null) {
				blurredBitmapTmp = arrayOfNulls(2)
				blurCanvas = arrayOfNulls(2)
			}

			for (i in 0..1) {
				if (blurredBitmapTmp!![i] == null || parentView.measuredWidth != lastW || parentView.measuredHeight != lastH) {
					val lastH = parentView.measuredHeight
					val lastW = parentView.measuredWidth

					toolbarH = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(200f)

					try {
						val h = if (i == 0) toolbarH else lastH
						blurredBitmapTmp!![i] = Bitmap.createBitmap((lastW / downscale).toInt(), (h / downscale).toInt(), Bitmap.Config.ARGB_8888)
						blurCanvas?.set(i, Canvas(blurredBitmapTmp!![i]!!))
					}
					catch (e: Exception) {
						FileLog.e(e)

						AndroidUtilities.runOnUIThread {
							error = true
							parentView.invalidate()
						}

						return
					}
				}
				else {
					blurredBitmapTmp!![i]!!.eraseColor(Color.TRANSPARENT)
				}
				if (i == 1) {
					blurredBitmapTmp!![i]!!.eraseColor(parentView.context.getColor(R.color.background))
				}

				blurCanvas?.get(i)?.save()
				blurCanvas?.get(i)?.scale(1f / downscale, 1f / downscale, 0f, 0f)

				var backDrawable = behindView.background

				if (backDrawable == null) {
					backDrawable = backgroundDrawable
				}

				behindView.setTag(TAG_DRAWING_AS_BACKGROUND, i)

				if (i == STATIC_CONTENT) {
					blurCanvas!![i]!!.translate(0f, -panTranslationY)
					behindView.draw(blurCanvas!![i]!!)
				}

				if (backDrawable != null && i == ADJUST_PAN_TRANSLATION_CONTENT) {
					val oldBounds = backDrawable.bounds
					backDrawable.setBounds(0, 0, behindView.measuredWidth, behindView.measuredHeight)
					backDrawable.draw(blurCanvas!![i]!!)
					backDrawable.bounds = oldBounds
					behindView.draw(blurCanvas!![i]!!)
				}

				behindView.setTag(TAG_DRAWING_AS_BACKGROUND, null)
				blurCanvas!![i]!!.restore()
			}

			lastH = parentView.measuredHeight
			lastW = parentView.measuredWidth

			blurBackgroundTask.width = parentView.measuredWidth
			blurBackgroundTask.height = parentView.measuredHeight

			if (blurBackgroundTask.width == 0 || blurBackgroundTask.height == 0) {
				processingNextFrame = false
				return
			}

			if (queue == null) {
				queue = DispatchQueue("blur_thread_$this")
			}

			queue?.postRunnable(blurBackgroundTask)
		}
	}

	private val blurRadius: Int
		get() = max(7.0, (max(lastH.toDouble(), lastW.toDouble()) / 180)).toInt()

	fun clear() {
		invalidate = true
		wasDraw = false
		error = false
		blurAlpha = 0f
		lastW = 0
		lastH = 0

		if (queue != null) {
			queue?.cleanupQueue()

			queue?.postRunnable {
				renderingBitmap?.getOrNull(0)?.recycle()
				renderingBitmap?.getOrNull(1)?.recycle()
				renderingBitmap = null

				backgroundBitmap?.getOrNull(0)?.recycle()
				backgroundBitmap?.getOrNull(1)?.recycle()
				backgroundBitmap = null

				renderingBitmapCanvas = null

				skipDraw = false

				AndroidUtilities.runOnUIThread {
					queue?.recycle()
					queue = null
				}
			}
		}
	}

	fun invalidate() {
		invalidate = true
		parentView.invalidate()
	}

	val isFullyDrawing: Boolean
		get() = !skipDraw && wasDraw && (blurAlpha == 1f || !animateAlpha) && show && parentView.alpha == 1f

	fun checkSizes() {
		val bitmap = renderingBitmap

		if (bitmap == null || parentView.measuredHeight == 0 || parentView.measuredWidth == 0) {
			return
		}

		generateBlurredBitmaps()

		lastH = parentView.measuredHeight
		lastW = parentView.measuredWidth
	}

	private fun generateBlurredBitmaps() {
		var bitmap = renderingBitmap

		if (bitmap == null) {
			renderingBitmap = arrayOfNulls(2)
			bitmap = renderingBitmap
			renderingBitmapCanvas = arrayOfNulls(2)
		}

		if (blurredBitmapTmp == null) {
			blurredBitmapTmp = arrayOfNulls(2)
			blurCanvas = arrayOfNulls(2)
		}

		blurBackgroundTask.canceled = true

		blurBackgroundTask = BlurBackgroundTask()

		for (i in 0..1) {
			val lastH = parentView.measuredHeight
			val lastW = parentView.measuredWidth

			toolbarH = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(200f)

			val h = if (i == 0) toolbarH else lastH

			if (bitmap!![i] == null || bitmap[i]!!.height != h || bitmap[i]!!.width != parentView.measuredWidth) {
				queue?.cleanupQueue()

				blurredBitmapTmp!![i] = Bitmap.createBitmap((lastW / downscale).toInt(), (h / downscale).toInt(), Bitmap.Config.ARGB_8888)

				if (i == 1) {
					blurredBitmapTmp!![i]!!.eraseColor(parentView.context.getColor(R.color.background))
				}

				blurCanvas!![i] = Canvas(blurredBitmapTmp!![i]!!)

				val bitmapH = ((if (i == 0) toolbarH else lastH) / downscale).toInt()
				val bitmapW = (lastW / downscale).toInt()

				renderingBitmap!![i] = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)

				renderingBitmapCanvas!![i] = Canvas(renderingBitmap!![i]!!)
				renderingBitmapCanvas!![i]!!.scale(renderingBitmap!![i]!!.width.toFloat() / blurredBitmapTmp!![i]!!.width.toFloat(), renderingBitmap!![i]!!.height.toFloat() / blurredBitmapTmp!![i]!!.height.toFloat())

				blurCanvas!![i]!!.save()
				blurCanvas!![i]!!.scale(1f / downscale, 1f / downscale, 0f, 0f)

				var backDrawable = behindView.background

				if (backDrawable == null) {
					backDrawable = backgroundDrawable
				}

				behindView.setTag(TAG_DRAWING_AS_BACKGROUND, i)

				if (i == STATIC_CONTENT) {
					blurCanvas!![i]!!.translate(0f, -panTranslationY)
					behindView.draw(blurCanvas!![i]!!)
				}

				if (i == ADJUST_PAN_TRANSLATION_CONTENT) {
					val oldBounds = backDrawable!!.bounds
					backDrawable.setBounds(0, 0, behindView.measuredWidth, behindView.measuredHeight)
					backDrawable.draw(blurCanvas!![i]!!)
					backDrawable.bounds = oldBounds
					behindView.draw(blurCanvas!![i]!!)
				}

				behindView.setTag(TAG_DRAWING_AS_BACKGROUND, null)

				blurCanvas!![i]!!.restore()

				Utilities.stackBlurBitmap(blurredBitmapTmp!![i], blurRadius)

				emptyPaint.alpha = 255

				if (i == 1) {
					renderingBitmap!![i]!!.eraseColor(parentView.context.getColor(R.color.background))
				}

				renderingBitmapCanvas!![i]!!.drawBitmap(blurredBitmapTmp!![i]!!, 0f, 0f, emptyPaint)
			}
		}
	}

	fun show(show: Boolean) {
		this.show = show
	}

	fun setAnimateAlpha(animateAlpha: Boolean) {
		this.animateAlpha = animateAlpha
	}

	fun onPanTranslationUpdate(y: Float) {
		panTranslationY = y
		parentView.invalidate()
	}

	inner class BlurBackgroundTask : Runnable {
		var canceled: Boolean = false
		var width: Int = 0
		var height: Int = 0

		override fun run() {
			if (backgroundBitmap == null) {
				backgroundBitmap = arrayOfNulls(2)
				backgroundBitmapCanvas = arrayOfNulls(2)
			}

			val bitmapWidth = (width / downscale).toInt()

			for (i in 0..1) {
				val h = ((if (i == 0) toolbarH else height) / downscale).toInt()

				if (backgroundBitmap!![i] != null && (backgroundBitmap!![i]!!.height != h || backgroundBitmap!![i]!!.width != bitmapWidth)) {
					if (backgroundBitmap!![i] != null) {
						backgroundBitmap!![i]!!.recycle()
						backgroundBitmap!![i] = null
					}
				}

				if (backgroundBitmap!![i] == null) {
					try {
						backgroundBitmap!![i] = Bitmap.createBitmap(bitmapWidth, h, Bitmap.Config.ARGB_8888)
						backgroundBitmapCanvas!![i] = Canvas(backgroundBitmap!![i]!!)
						backgroundBitmapCanvas!![i]!!.scale(bitmapWidth / blurredBitmapTmp!![i]!!.width.toFloat(), h / blurredBitmapTmp!![i]!!.height.toFloat())
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}

				if (i == 1) {
					backgroundBitmap!![i]!!.eraseColor(parentView.context.getColor(R.color.background))
				}
				else {
					backgroundBitmap!![i]!!.eraseColor(Color.TRANSPARENT)
				}

				emptyPaint.alpha = 255

				Utilities.stackBlurBitmap(blurredBitmapTmp!![i], this@BlurBehindDrawable.blurRadius)

				if (backgroundBitmapCanvas!![i] != null) {
					backgroundBitmapCanvas!![i]!!.drawBitmap(blurredBitmapTmp!![i]!!, 0f, 0f, emptyPaint)
				}

				if (canceled) {
					return
				}
			}

			AndroidUtilities.runOnUIThread {
				if (canceled) {
					return@runOnUIThread
				}

				val bitmap = renderingBitmap
				val canvas = renderingBitmapCanvas

				renderingBitmap = backgroundBitmap
				renderingBitmapCanvas = backgroundBitmapCanvas

				backgroundBitmap = bitmap
				backgroundBitmapCanvas = canvas

				processingNextFrame = false

				parentView.invalidate()
			}
		}
	}

	private val backgroundDrawable: Drawable?
		get() = ResourcesCompat.getDrawable(parentView.context.resources, R.drawable.chat_background, null)

	companion object {
		const val TAG_DRAWING_AS_BACKGROUND = (1 shl 26) + 3
		const val STATIC_CONTENT = 0
		private const val ADJUST_PAN_TRANSLATION_CONTENT = 1
	}
}
