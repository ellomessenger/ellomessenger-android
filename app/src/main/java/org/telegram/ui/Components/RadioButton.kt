/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.annotation.Keep
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R

open class RadioButton(context: Context) : View(context) {
	private var color = 0
	private var checkedColor = 0
	private var progress = 0f
	private var checkAnimator: ObjectAnimator? = null
	private var attachedToWindow = false
	private var size = AndroidUtilities.dp(16f)

	var isChecked = false
		private set

	@Keep
	fun getProgress(): Float {
		return progress
	}

	@Keep
	fun setProgress(value: Float) {
		if (progress == value) {
			return
		}

		progress = value

		invalidate()
	}

	fun setSize(value: Int) {
		if (size == value) {
			return
		}

		size = value
	}

	fun setColor(color1: Int, color2: Int) {
		color = color1
		checkedColor = color2
		invalidate()
	}

	override fun setBackgroundColor(color1: Int) {
		color = color1
		invalidate()
	}

	fun setCheckedColor(color2: Int) {
		checkedColor = color2
		invalidate()
	}

	private fun cancelCheckAnimator() {
		checkAnimator?.cancel()
	}

	private fun animateToCheckedState(newCheckedState: Boolean) {
		checkAnimator = ObjectAnimator.ofFloat(this, "progress", if (newCheckedState) 1f else 0f)
		checkAnimator?.duration = 200
		checkAnimator?.start()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		attachedToWindow = true
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attachedToWindow = false
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		if (checked == isChecked) {
			return
		}

		isChecked = checked

		if (attachedToWindow && animated) {
			animateToCheckedState(checked)
		}
		else {
			cancelCheckAnimator()
			setProgress(if (checked) 1.0f else 0.0f)
		}
	}

	override fun onDraw(canvas: Canvas) {
		val circleProgress: Float

		if (progress <= 0.5f) {
			paint.color = color
			checkedPaint.color = color
			circleProgress = progress / 0.5f
		}
		else {
			circleProgress = 2.0f - progress / 0.5f

			val r1 = Color.red(color)
			val rD = ((Color.red(checkedColor) - r1) * (1.0f - circleProgress)).toInt()
			val g1 = Color.green(color)
			val gD = ((Color.green(checkedColor) - g1) * (1.0f - circleProgress)).toInt()
			val b1 = Color.blue(color)
			val bD = ((Color.blue(checkedColor) - b1) * (1.0f - circleProgress)).toInt()
			val c = Color.rgb(r1 + rD, g1 + gD, b1 + bD)

			paint.color = c
			checkedPaint.color = c
		}

		eraser.color = ApplicationLoader.applicationContext.getColor(R.color.background)

		val rad = size / 2 - (1 + circleProgress) * AndroidUtilities.density

		canvas.drawCircle((measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat(), rad, paint)

		if (progress <= 0.5f) {
			canvas.drawCircle((measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat(), rad - AndroidUtilities.dp(1f), checkedPaint)
			canvas.drawCircle((measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat(), (rad - AndroidUtilities.dp(1f)) * (1.0f - circleProgress), eraser)
		}
		else {
			canvas.drawCircle((measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat(), size / 4 + (rad - AndroidUtilities.dp(1f) - size / 4) * circleProgress, checkedPaint)
		}
	}

	companion object {
		private val paint by lazy {
			Paint(Paint.ANTI_ALIAS_FLAG).apply {
				strokeWidth = AndroidUtilities.dp(2f).toFloat()
				style = Paint.Style.STROKE
			}
		}

		private val eraser by lazy {
			Paint(Paint.ANTI_ALIAS_FLAG)
		}

		private val checkedPaint by lazy {
			Paint(Paint.ANTI_ALIAS_FLAG)
		}
	}
}
