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
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R

class ShareLocationDrawable(context: Context, private val currentType: Int) : Drawable() {
	private var lastUpdateTime: Long = 0
	private val progress = floatArrayOf(0.0f, -0.5f)
	private val drawable: Drawable
	private val drawableLeft: Drawable
	private val drawableRight: Drawable

	init {
		when (currentType) {
			4 -> {
				drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.pin, null)!!
				drawableLeft = ResourcesCompat.getDrawable(context.resources, R.drawable.smallanimationpinleft, null)!!
				drawableRight = ResourcesCompat.getDrawable(context.resources, R.drawable.smallanimationpinright, null)!!
			}
			3 -> {
				drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.nearby_l, null)!!
				drawableLeft = ResourcesCompat.getDrawable(context.resources, R.drawable.animationpinleft, null)!!
				drawableRight = ResourcesCompat.getDrawable(context.resources, R.drawable.animationpinright, null)!!
			}
			2 -> {
				drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.nearby_m, null)!!
				drawableLeft = ResourcesCompat.getDrawable(context.resources, R.drawable.animationpinleft, null)!!
				drawableRight = ResourcesCompat.getDrawable(context.resources, R.drawable.animationpinright, null)!!
			}
			1 -> {
				drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.smallanimationpin, null)!!
				drawableLeft = ResourcesCompat.getDrawable(context.resources, R.drawable.smallanimationpinleft, null)!!
				drawableRight = ResourcesCompat.getDrawable(context.resources, R.drawable.smallanimationpinright, null)!!
			}
			else -> {
				drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.animationpin, null)!!
				drawableLeft = ResourcesCompat.getDrawable(context.resources, R.drawable.animationpinleft, null)!!
				drawableRight = ResourcesCompat.getDrawable(context.resources, R.drawable.animationpinright, null)!!
			}
		}
	}

	private fun update() {
		val newTime = System.currentTimeMillis()
		var dt = newTime - lastUpdateTime

		lastUpdateTime = newTime

		if (dt > 16) {
			dt = 16
		}

		for (a in 0..1) {
			if (progress[a] >= 1.0f) {
				progress[a] = 0.0f
			}

			progress[a] += dt / 1300.0f

			if (progress[a] > 1.0f) {
				progress[a] = 1.0f
			}
		}

		invalidateSelf()
	}

	override fun draw(canvas: Canvas) {
		val drawableW = drawable.intrinsicWidth
		val drawableH = drawable.intrinsicHeight

		val size = when (currentType) {
			4 -> AndroidUtilities.dp(24f)
			3 -> AndroidUtilities.dp(44f)
			2 -> AndroidUtilities.dp(32f)
			1 -> AndroidUtilities.dp(30f)
			else -> AndroidUtilities.dp(120f)
		}

		val y = bounds.top + (intrinsicHeight - size) / 2
		val x = bounds.left + (intrinsicWidth - size) / 2

		drawable.setBounds(x, y, x + drawableW, y + drawableH)
		drawable.draw(canvas)

		for (a in 0..1) {
			if (progress[a] < 0) {
				continue
			}

			val scale = 0.5f + 0.5f * progress[a]
			var w: Int
			var h: Int
			var tx: Int
			var cx: Int
			var cx2: Int
			var cy: Int

			when (currentType) {
				4 -> {
					w = AndroidUtilities.dp(2.5f * scale)
					h = AndroidUtilities.dp(6.5f * scale)
					tx = AndroidUtilities.dp(6.0f * progress[a])
					cx = x + AndroidUtilities.dp(3f) - tx
					cy = y + drawableH / 2 - AndroidUtilities.dp(2f)
					cx2 = x + drawableW - AndroidUtilities.dp(3f) + tx
				}
				3 -> {
					w = AndroidUtilities.dp(5 * scale)
					h = AndroidUtilities.dp(18 * scale)
					tx = AndroidUtilities.dp(15 * progress[a])
					cx = x + AndroidUtilities.dp(2f) - tx
					cy = y + drawableH / 2 - AndroidUtilities.dp(7f)
					cx2 = x + drawableW - AndroidUtilities.dp(2f) + tx
				}
				2 -> {
					w = AndroidUtilities.dp(5 * scale)
					h = AndroidUtilities.dp(18 * scale)
					tx = AndroidUtilities.dp(15 * progress[a])
					cx = x + AndroidUtilities.dp(2f) - tx
					cy = y + drawableH / 2
					cx2 = x + drawableW - AndroidUtilities.dp(2f) + tx
				}
				1 -> {
					w = AndroidUtilities.dp(2.5f * scale)
					h = AndroidUtilities.dp(6.5f * scale)
					tx = AndroidUtilities.dp(6.0f * progress[a])
					cx = x + AndroidUtilities.dp(7f) - tx
					cy = y + drawableH / 2
					cx2 = x + drawableW - AndroidUtilities.dp(7f) + tx
				}
				else -> {
					w = AndroidUtilities.dp(5 * scale)
					h = AndroidUtilities.dp(18 * scale)
					tx = AndroidUtilities.dp(15 * progress[a])
					cx = x + AndroidUtilities.dp(42f) - tx
					cy = y + drawableH / 2 - AndroidUtilities.dp(7f)
					cx2 = x + drawableW - AndroidUtilities.dp(42f) + tx
				}
			}

			val alpha = if (progress[a] < 0.5f) {
				progress[a] / 0.5f
			}
			else {
				1.0f - (progress[a] - 0.5f) / 0.5f
			}

			drawableLeft.alpha = (alpha * 255).toInt()
			drawableLeft.setBounds(cx - w, cy - h, cx + w, cy + h)
			drawableLeft.draw(canvas)

			drawableRight.alpha = (alpha * 255).toInt()
			drawableRight.setBounds(cx2 - w, cy - h, cx2 + w, cy + h)
			drawableRight.draw(canvas)
		}

		update()
	}

	override fun setAlpha(alpha: Int) {
		// unused
	}

	override fun setColorFilter(cf: ColorFilter?) {
		drawable.colorFilter = cf
		drawableLeft.colorFilter = cf
		drawableRight.colorFilter = cf
	}

	@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
	override fun getOpacity(): Int {
		return PixelFormat.TRANSPARENT
	}

	override fun getIntrinsicWidth(): Int {
		return when (currentType) {
			4 -> AndroidUtilities.dp(42f)
			3 -> AndroidUtilities.dp(100f)
			2 -> AndroidUtilities.dp(74f)
			1 -> AndroidUtilities.dp(40f)
			else -> AndroidUtilities.dp(120f)
		}
	}

	override fun getIntrinsicHeight(): Int {
		return when (currentType) {
			4 -> AndroidUtilities.dp(42f)
			3 -> AndroidUtilities.dp(100f)
			2 -> AndroidUtilities.dp(74f)
			1 -> AndroidUtilities.dp(40f)
			else -> AndroidUtilities.dp(180f)
		}
	}
}
