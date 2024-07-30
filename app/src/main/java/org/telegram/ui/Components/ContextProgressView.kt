/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

class ContextProgressView @JvmOverloads constructor(context: Context, colorType: Int = 0) : View(context) {
	private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val circleRect = RectF()
	private var radOffset = 0
	private var lastUpdateTime: Long = 0
	private var innerKey: String? = null
	private var outerKey: String? = null
	private var innerColor = 0
	private var outerColor = 0

	init {
		innerPaint.style = Paint.Style.STROKE
		innerPaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
		outerPaint.style = Paint.Style.STROKE
		outerPaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
		outerPaint.strokeCap = Paint.Cap.ROUND

		when (colorType) {
			0 -> {
				innerKey = Theme.key_contextProgressInner1
				outerKey = Theme.key_contextProgressOuter1
			}
			1 -> {
				innerKey = Theme.key_contextProgressInner2
				outerKey = Theme.key_contextProgressOuter2
			}
			2 -> {
				innerKey = Theme.key_contextProgressInner3
				outerKey = Theme.key_contextProgressOuter3
			}
			3 -> {
				innerKey = Theme.key_contextProgressInner4
				outerKey = Theme.key_contextProgressOuter4
			}
		}

		updateColors()
	}

	fun setColors(innerColor: Int, outerColor: Int) {
		innerKey = null
		outerKey = null
		this.innerColor = innerColor
		this.outerColor = outerColor
		updateColors()
	}

	fun updateColors() {
		if (innerKey != null) {
			innerPaint.color = Theme.getColor(innerKey)
		}
		else {
			innerPaint.color = innerColor
		}

		if (outerKey != null) {
			outerPaint.color = Theme.getColor(outerKey)
		}
		else {
			outerPaint.color = outerColor
		}

		invalidate()
	}

	override fun setVisibility(visibility: Int) {
		super.setVisibility(visibility)
		lastUpdateTime = System.currentTimeMillis()
		invalidate()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		lastUpdateTime = System.currentTimeMillis()
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		if (visibility != VISIBLE) {
			return
		}

		val newTime = System.currentTimeMillis()
		val dt = newTime - lastUpdateTime

		lastUpdateTime = newTime
		radOffset += (360 * dt / 1000.0f).toInt()

		val x = measuredWidth / 2 - AndroidUtilities.dp(9f)
		val y = measuredHeight / 2 - AndroidUtilities.dp(9f)

		circleRect[x.toFloat(), y.toFloat(), (x + AndroidUtilities.dp(18f)).toFloat()] = (y + AndroidUtilities.dp(18f)).toFloat()

		canvas.drawCircle((measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat(), AndroidUtilities.dp(9f).toFloat(), innerPaint)
		canvas.drawArc(circleRect, (-90 + radOffset).toFloat(), 90f, false, outerPaint)

		invalidate()
	}
}
