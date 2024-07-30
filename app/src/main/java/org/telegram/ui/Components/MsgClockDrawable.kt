/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import kotlin.math.min

class MsgClockDrawable : Drawable() {
	private var constantState: ConstantState? = null
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var alpha = 255
	private var colorAlpha = 255
	private val startTime: Long
	private var color = 0

	init {
		paint.style = Paint.Style.STROKE
		paint.strokeCap = Paint.Cap.ROUND
		paint.strokeWidth = AndroidUtilities.dp(1f).toFloat()
		startTime = System.currentTimeMillis()
	}

	override fun draw(canvas: Canvas) {
		val bounds = getBounds()
		val r = min(bounds.width().toDouble(), bounds.height().toDouble()).toInt()

		canvas.drawCircle(bounds.centerX().toFloat(), bounds.centerY().toFloat(), ((r shr 1) - AndroidUtilities.dp(0.5f)).toFloat(), paint)

		val currentTime = System.currentTimeMillis()
		val rotateTime = 1500f
		val rotateHourTime = rotateTime * 3

		canvas.save()
		canvas.rotate(360 * ((currentTime - startTime) % rotateTime) / rotateTime, bounds.centerX().toFloat(), bounds.centerY().toFloat())
		canvas.drawLine(bounds.centerX().toFloat(), bounds.centerY().toFloat(), bounds.centerX().toFloat(), (bounds.centerY() - AndroidUtilities.dp(3f)).toFloat(), paint)
		canvas.restore()
		canvas.save()
		canvas.rotate(360 * ((currentTime - startTime) % rotateHourTime) / rotateHourTime, bounds.centerX().toFloat(), bounds.centerY().toFloat())
		canvas.drawLine(bounds.centerX().toFloat(), bounds.centerY().toFloat(), (bounds.centerX() + AndroidUtilities.dp(2.3f)).toFloat(), bounds.centerY().toFloat(), paint)
		canvas.restore()
	}

	fun setColor(color: Int) {
		if (color != this.color) {
			colorAlpha = Color.alpha(color)
			paint.setColor(ColorUtils.setAlphaComponent(color, (alpha * (colorAlpha / 255f)).toInt()))
		}

		this.color = color
	}

	override fun getIntrinsicHeight(): Int {
		return AndroidUtilities.dp(12f)
	}

	override fun getIntrinsicWidth(): Int {
		return AndroidUtilities.dp(12f)
	}

	override fun setAlpha(i: Int) {
		if (alpha != i) {
			alpha = i
			paint.setAlpha((alpha * (colorAlpha / 255f)).toInt())
		}
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		// unused
	}

	@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
	override fun getOpacity(): Int {
		return PixelFormat.TRANSPARENT
	}

	override fun getConstantState(): ConstantState? {
		if (constantState == null) {
			constantState = object : ConstantState() {
				override fun newDrawable(): Drawable {
					return MsgClockDrawable()
				}

				override fun getChangingConfigurations(): Int {
					return 0
				}
			}
		}

		return constantState
	}
}
