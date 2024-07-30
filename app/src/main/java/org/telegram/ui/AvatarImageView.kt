/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.ImageReceiver.BitmapHolder
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.ProfileGalleryView

open class AvatarImageView(context: Context) : BackupImageView(context) {
	private val rect = RectF()
	private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
	private val foregroundImageReceiver = ImageReceiver(this)
	private var foregroundAlpha = 0f
	private var drawableHolder: BitmapHolder? = null
	var avatarsViewPager: ProfileGalleryView? = null
	var shouldDrawBorder = false
	var borderColor = Color.WHITE

	fun setForegroundImage(imageLocation: ImageLocation?, imageFilter: String?, thumb: Drawable?) {
		foregroundImageReceiver.setImage(imageLocation, imageFilter, thumb, 0, null, null, 0)
		drawableHolder?.release()
		drawableHolder = null
	}

	fun setForegroundImageDrawable(holder: BitmapHolder?) {
		if (holder != null) {
			foregroundImageReceiver.setImageBitmap(holder.drawable)
		}

		drawableHolder?.release()
		drawableHolder = holder
	}

	fun getForegroundAlpha(): Float {
		return foregroundAlpha
	}

	fun setForegroundAlpha(value: Float) {
		foregroundAlpha = value
		invalidate()
	}

	fun clearForeground() {
		foregroundImageReceiver.animation?.removeSecondParentView(this)
		foregroundImageReceiver.clearImage()

		drawableHolder?.release()
		drawableHolder = null

		foregroundAlpha = 0f

		invalidate()
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		foregroundImageReceiver.onDetachedFromWindow()
		drawableHolder?.release()
		drawableHolder = null
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		foregroundImageReceiver.onAttachedToWindow()
	}

	override fun setRoundRadius(value: Int) {
		super.setRoundRadius(value)
		foregroundImageReceiver.setRoundRadius(value)
	}

	override fun onDraw(canvas: Canvas) {
		val width = measuredWidth.toFloat()
		val height = measuredHeight.toFloat()

		if (foregroundAlpha < 1f) {
			imageReceiver.setImageCoordinates(0f, 0f, width, height)
			imageReceiver.draw(canvas)
		}

		if (foregroundAlpha > 0f) {
			if (foregroundImageReceiver.drawable != null) {
				foregroundImageReceiver.setImageCoordinates(0f, 0f, width, height)
				foregroundImageReceiver.alpha = foregroundAlpha
				foregroundImageReceiver.draw(canvas)
			}
			else {
				rect.set(0f, 0f, width, height)
				placeholderPaint.alpha = (foregroundAlpha * 255f).toInt()
				val radius = foregroundImageReceiver.getRoundRadius()[0]
				canvas.drawRoundRect(rect, radius.toFloat(), radius.toFloat(), placeholderPaint)
			}
		}

		if (shouldDrawBorder) {
			borderPaint.color = borderColor

			val radius = width * 0.45f
			rect.set(borderWidth, borderWidth, width - borderWidth / 2, height - borderWidth / 2)
			canvas.drawRoundRect(rect, radius, radius, borderPaint)
		}
	}

	override fun invalidate() {
		super.invalidate()
		avatarsViewPager?.invalidate()
	}

	companion object {
		private const val borderWidth = 1f

		private val borderPaint by lazy {
			Paint(Paint.ANTI_ALIAS_FLAG).apply {
				style = Paint.Style.STROKE
				strokeWidth = AndroidUtilities.dp(borderWidth).toFloat()
			}
		}
	}
}
