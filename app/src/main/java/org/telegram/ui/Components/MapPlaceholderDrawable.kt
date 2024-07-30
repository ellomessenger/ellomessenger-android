/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

class MapPlaceholderDrawable : Drawable() {
	private val paint = Paint()
	private val linePaint = Paint()

	init {
		linePaint.strokeWidth = AndroidUtilities.dp(1f).toFloat()

		if (Theme.getCurrentTheme().isDark) {
			paint.color = -0xe2d3b3
			linePaint.color = -0xf1e9da
		}
		else {
			paint.color = -0x21282a
			linePaint.color = -0x394042
		}
	}

	override fun draw(canvas: Canvas) {
		canvas.drawRect(bounds, paint)
		val gap = AndroidUtilities.dp(9f)
		val xcount = bounds.width() / gap
		val ycount = bounds.height() / gap
		val x = bounds.left
		val y = bounds.top
		for (a in 0 until xcount) {
			canvas.drawLine((x + gap * (a + 1)).toFloat(), y.toFloat(), (x + gap * (a + 1)).toFloat(), (y + bounds.height()).toFloat(), linePaint)
		}
		for (a in 0 until ycount) {
			canvas.drawLine(x.toFloat(), (y + gap * (a + 1)).toFloat(), (x + bounds.width()).toFloat(), (y + gap * (a + 1)).toFloat(), linePaint)
		}
	}

	override fun setAlpha(alpha: Int) {}

	override fun setColorFilter(cf: ColorFilter?) {}

	@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.UNKNOWN", "android.graphics.PixelFormat"))
	override fun getOpacity(): Int {
		return PixelFormat.UNKNOWN
	}

	override fun getIntrinsicWidth(): Int {
		return 0
	}

	override fun getIntrinsicHeight(): Int {
		return 0
	}
}
