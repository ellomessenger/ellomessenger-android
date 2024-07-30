/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities

class CrossOutDrawable(context: Context, iconRes: Int, private var tintColor: Int = Color.WHITE) : Drawable() {
	val iconDrawable: Drawable
	var rectF = RectF()
	var paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val xRefPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	var color = 0
	var progress = 0f
	var cross = false
	private var xOffset = 0f
	private var lenOffsetTop = 0f
	private var lenOffsetBottom = 0f

	init {
		iconDrawable = ResourcesCompat.getDrawable(context.resources, iconRes, null)!!

		paint.style = Paint.Style.STROKE
		paint.strokeWidth = AndroidUtilities.dpf2(1.7f)
		paint.strokeCap = Paint.Cap.ROUND

		xRefPaint.color = -0x1000000
		xRefPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
		xRefPaint.style = Paint.Style.STROKE
		xRefPaint.strokeWidth = AndroidUtilities.dpf2(2.5f)
	}

	fun setCrossOut(cross: Boolean, animated: Boolean) {
		if (this.cross != cross) {
			this.cross = cross

			progress = if (!animated) {
				if (cross) 1f else 0f
			}
			else {
				if (cross) 0f else 1f
			}

			invalidateSelf()
		}
	}

	override fun draw(canvas: Canvas) {
		if (cross && progress != 1f) {
			progress += 16f / 150f

			invalidateSelf()

			if (progress > 1f) {
				progress = 1f
			}
		}
		else if (!cross && progress != 0f) {
			progress -= 16f / 150f

			invalidateSelf()

			if (progress < 0) {
				progress = 0f
			}
		}

		val newColor = tintColor

		if (color != newColor) {
			color = newColor
			paint.color = newColor
			iconDrawable.colorFilter = PorterDuffColorFilter(newColor, PorterDuff.Mode.SRC_IN)
		}

		if (progress == 0f) {
			iconDrawable.draw(canvas)
			return
		}

		rectF.set(iconDrawable.bounds)

		canvas.saveLayerAlpha(rectF, 255)

		iconDrawable.draw(canvas)

		var startX = rectF.left + AndroidUtilities.dpf2(4.5f) + xOffset + lenOffsetTop
		var startY = rectF.top + AndroidUtilities.dpf2(4.5f) - AndroidUtilities.dp(1f) + lenOffsetTop
		var stopX = rectF.right - AndroidUtilities.dp(3f) + xOffset - lenOffsetBottom
		var stopY = rectF.bottom - AndroidUtilities.dp(1f) - AndroidUtilities.dp(3f) - lenOffsetBottom

		if (cross) {
			stopX = startX + (stopX - startX) * progress
			stopY = startY + (stopY - startY) * progress
		}
		else {
			startX += (stopX - startX) * (1f - progress)
			startY += (stopY - startY) * (1f - progress)
		}

		canvas.drawLine(startX, startY - paint.strokeWidth, stopX, stopY - paint.strokeWidth, xRefPaint)

		val offsetY = (xRefPaint.strokeWidth - paint.strokeWidth) / 2f + 1

		canvas.drawLine(startX, startY - offsetY, stopX, stopY - offsetY, xRefPaint)
		canvas.drawLine(startX, startY, stopX, stopY, paint)
		canvas.restore()
	}

	override fun setAlpha(i: Int) {
		// stub
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		// stub
	}

	override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
		super.setBounds(left, top, right, bottom)
		iconDrawable.setBounds(left, top, right, bottom)
	}

	override fun getIntrinsicHeight(): Int {
		return iconDrawable.intrinsicHeight
	}

	override fun getIntrinsicWidth(): Int {
		return iconDrawable.intrinsicWidth
	}

	@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
	override fun getOpacity(): Int {
		return PixelFormat.TRANSPARENT
	}

	fun setOffsets(xOffset: Float, lenOffsetTop: Float, lenOffsetBottom: Float) {
		this.xOffset = xOffset
		this.lenOffsetTop = lenOffsetTop
		this.lenOffsetBottom = lenOffsetBottom
		invalidateSelf()
	}

	fun setStrokeWidth(w: Float) {
		paint.strokeWidth = w
		xRefPaint.strokeWidth = w * 1.47f
	}
}
