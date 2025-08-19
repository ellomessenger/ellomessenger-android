/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.Components.voip

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver.BitmapHolder
import org.telegram.messenger.Utilities

@SuppressLint("AppCompatCustomView")
class VoIPOverlayBackground(context: Context) : ImageView(context) {
	private var imageSet = false
	private var showBlackout = false
	private var blackoutProgress = 0f

	init {
		setScaleType(ScaleType.CENTER_CROP)
	}

	override fun onDraw(canvas: Canvas) {
		when (blackoutProgress) {
			1f -> {
				canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.4f).toInt()))
			}

			0f -> {
				imageAlpha = 255
				super.onDraw(canvas)
			}

			else -> {
				canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.4f * blackoutProgress).toInt()))
				imageAlpha = (255 * (1f - blackoutProgress)).toInt()
				super.onDraw(canvas)
			}
		}
	}

	fun setBackground(src: BitmapHolder) {
		val bitmap = src.bitmap ?: return

		Thread {
			runCatching {
				val blur1 = createBitmap(150, 150)

				val canvas = Canvas(blur1)
				canvas.drawBitmap(bitmap, null, Rect(0, 0, 150, 150), Paint(Paint.FILTER_BITMAP_FLAG))

				Utilities.blurBitmap(blur1, 3, 0, blur1.getWidth(), blur1.getHeight(), blur1.getRowBytes())

				val palette = Palette.from(bitmap).generate()

				val paint = Paint()
				paint.setColor((palette.getDarkMutedColor(-0xab8b67) and 0x00FFFFFF) or 0x44000000)

				canvas.drawColor(0x26000000)
				canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)

				AndroidUtilities.runOnUIThread {
					setImageBitmap(blur1)
					imageSet = true
					src.release()
				}
			}
		}.start()
	}

	fun setShowBlackout(showBlackout: Boolean, animated: Boolean) {
		if (this.showBlackout == showBlackout) {
			return
		}

		this.showBlackout = showBlackout

		if (!animated) {
			blackoutProgress = if (showBlackout) 1f else 0f
		}
		else {
			val animator = ValueAnimator.ofFloat(blackoutProgress, if (showBlackout) 1f else 0f)

			animator.addUpdateListener {
				blackoutProgress = it.getAnimatedValue() as Float
				invalidate()
			}

			animator.setDuration(150).start()
		}
	}
}
