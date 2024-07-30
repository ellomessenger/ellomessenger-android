/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import kotlin.math.min

class CheckBoxSquare(context: Context) : View(context) {
	private val rectF: RectF
	private val drawBitmap: Bitmap
	private val drawCanvas: Canvas
	private var progress = 0f
	private var checkAnimator: ObjectAnimator? = null
	private var attachedToWindow = false
	private var isDisabled = false

	@ColorInt
	private var uncheckedColor = 0

	@ColorInt
	private var checkedColor = 0

	@ColorInt
	private var checkmarkColor = 0

	var isChecked = false
		private set

	init {
		if (Theme.checkboxSquare_backgroundPaint == null) {
			Theme.createCommonResources(context)
		}

		uncheckedColor = context.getColor(R.color.brand)
		checkedColor = context.getColor(R.color.brand)
		checkmarkColor = context.getColor(R.color.white)

		rectF = RectF()

		drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), Bitmap.Config.ARGB_8888)

		drawCanvas = Canvas(drawBitmap)
	}

	fun setColors(unchecked: Int, checked: Int, check: Int) {
		uncheckedColor = unchecked
		checkedColor = checked
		checkmarkColor = check
		invalidate()
	}

	@Keep
	fun setProgress(value: Float) {
		if (progress == value) {
			return
		}

		progress = value

		invalidate()
	}

	@Keep
	fun getProgress(): Float {
		return progress
	}

	private fun cancelCheckAnimator() {
		checkAnimator?.cancel()
	}

	private fun animateToCheckedState(newCheckedState: Boolean) {
		checkAnimator = ObjectAnimator.ofFloat(this, "progress", (if (newCheckedState) 1 else 0).toFloat())
		checkAnimator?.duration = 300
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

	fun setDisabled(disabled: Boolean) {
		isDisabled = disabled
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		if (visibility != VISIBLE) {
			return
		}

		val checkProgress: Float
		val bounceProgress: Float
		val uncheckedColor = uncheckedColor
		val color = checkedColor

		if (progress <= 0.5f) {
			checkProgress = progress / 0.5f
			bounceProgress = checkProgress

			val rD = ((Color.red(color) - Color.red(uncheckedColor)) * checkProgress).toInt()
			val gD = ((Color.green(color) - Color.green(uncheckedColor)) * checkProgress).toInt()
			val bD = ((Color.blue(color) - Color.blue(uncheckedColor)) * checkProgress).toInt()
			val c = Color.rgb(Color.red(uncheckedColor) + rD, Color.green(uncheckedColor) + gD, Color.blue(uncheckedColor) + bD)

			Theme.checkboxSquare_backgroundPaint.color = c
		}
		else {
			bounceProgress = 2.0f - progress / 0.5f
			checkProgress = 1.0f

			Theme.checkboxSquare_backgroundPaint.color = color
		}

		if (isDisabled) {
			Theme.checkboxSquare_backgroundPaint.color = context.getColor(R.color.disabled_text)
		}

		val bounce = AndroidUtilities.dp(1f) * bounceProgress

		rectF.set(bounce, bounce, AndroidUtilities.dp(18f) - bounce, AndroidUtilities.dp(18f) - bounce)

		drawBitmap.eraseColor(0)
		drawCanvas.drawRoundRect(rectF, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.checkboxSquare_backgroundPaint)

		if (checkProgress != 1f) {
			val rad = min(AndroidUtilities.dp(7f).toFloat(), AndroidUtilities.dp(7f) * checkProgress + bounce)
			rectF.set(AndroidUtilities.dp(2f) + rad, AndroidUtilities.dp(2f) + rad, AndroidUtilities.dp(16f) - rad, AndroidUtilities.dp(16f) - rad)
			drawCanvas.drawRect(rectF, Theme.checkboxSquare_eraserPaint)
		}

		if (progress > 0.5f) {
			Theme.checkboxSquare_checkPaint.color = checkmarkColor

			var endX = (AndroidUtilities.dp(7f) - AndroidUtilities.dp(3f) * (1.0f - bounceProgress)).toInt()
			var endY = (AndroidUtilities.dpf2(13f) - AndroidUtilities.dp(3f) * (1.0f - bounceProgress)).toInt()

			drawCanvas.drawLine(AndroidUtilities.dp(7f).toFloat(), AndroidUtilities.dpf2(13f).toInt().toFloat(), endX.toFloat(), endY.toFloat(), Theme.checkboxSquare_checkPaint)

			endX = (AndroidUtilities.dpf2(7f) + AndroidUtilities.dp(7f) * (1.0f - bounceProgress)).toInt()
			endY = (AndroidUtilities.dpf2(13f) - AndroidUtilities.dp(7f) * (1.0f - bounceProgress)).toInt()

			drawCanvas.drawLine(AndroidUtilities.dpf2(7f).toInt().toFloat(), AndroidUtilities.dpf2(13f).toInt().toFloat(), endX.toFloat(), endY.toFloat(), Theme.checkboxSquare_checkPaint)
		}

		canvas.drawBitmap(drawBitmap, 0f, 0f, null)
	}
}
