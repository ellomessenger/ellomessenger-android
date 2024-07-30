/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components.voip

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.voip.VoIPService
import org.telegram.ui.ActionBar.Theme

class VoIPTimerView(context: Context) : View(context) {
	private var timerLayout: StaticLayout? = null
	private val rectF = RectF()
	private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var currentTimeStr: String? = null
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private var signalBarCount = 4

	private val updater = Runnable {
		if (visibility == VISIBLE) {
			updateTimer()
		}
	}

	init {
		textPaint.textSize = AndroidUtilities.dp(15f).toFloat()
		textPaint.setTypeface(Theme.TYPEFACE_DEFAULT)
		textPaint.color = Color.WHITE
		textPaint.setShadowLayer(AndroidUtilities.dp(3f).toFloat(), 0f, AndroidUtilities.dp(0.6666667f).toFloat(), 0x4C000000)

		activePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.9f).toInt())

		inactivePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.4f).toInt())
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), timerLayout?.height ?: AndroidUtilities.dp(15f))
	}

	fun updateTimer() {
		removeCallbacks(updater)

		val service = VoIPService.sharedInstance ?: return
		val str = AndroidUtilities.formatLongDuration((service.getCallDuration() / 1000).toInt())

		if (currentTimeStr == null || currentTimeStr != str) {
			currentTimeStr = str

			if (timerLayout == null) {
				requestLayout()
			}

			timerLayout = StaticLayout(currentTimeStr, textPaint, textPaint.measureText(currentTimeStr).toInt(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
		}

		postDelayed(updater, 300)

		invalidate()
	}

	override fun setVisibility(visibility: Int) {
		if (getVisibility() != visibility) {
			if (visibility == VISIBLE) {
				currentTimeStr = "00:00"
				timerLayout = StaticLayout(currentTimeStr, textPaint, textPaint.measureText(currentTimeStr).toInt(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				updateTimer()
			}
			else {
				currentTimeStr = null
				timerLayout = null
			}
		}

		super.setVisibility(visibility)
	}

	override fun onDraw(canvas: Canvas) {
		val timerLayout = this.timerLayout
		val totalWidth = if (timerLayout == null) 0 else timerLayout.width + AndroidUtilities.dp(21f)

		canvas.save()
		canvas.translate((measuredWidth - totalWidth) / 2f, 0f)
		canvas.save()
		canvas.translate(0f, (measuredHeight - AndroidUtilities.dp(11f)) / 2f)

		for (i in 0..3) {
			val p = if (i + 1 > signalBarCount) inactivePaint else activePaint
			rectF[AndroidUtilities.dpf2(4.16f) * i, AndroidUtilities.dpf2(2.75f) * (3 - i), AndroidUtilities.dpf2(4.16f) * i + AndroidUtilities.dpf2(2.75f)] = AndroidUtilities.dp(11f).toFloat()
			canvas.drawRoundRect(rectF, AndroidUtilities.dpf2(0.7f), AndroidUtilities.dpf2(0.7f), p)
		}

		canvas.restore()

		if (timerLayout != null) {
			canvas.translate(AndroidUtilities.dp(21f).toFloat(), 0f)
			timerLayout.draw(canvas)
		}

		canvas.restore()
	}

	fun setSignalBarCount(count: Int) {
		signalBarCount = count
		invalidate()
	}
}
