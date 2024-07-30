/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme

class FlatCheckBox(context: Context) : View(context) {
	private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val outLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val pInset = AndroidUtilities.dp(2f)
	private val rectF = RectF()
	private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
	private var attached = false
	private var checkAnimator: ValueAnimator? = null
	private var checked = false
	private var colorActive = 0
	private var colorInactive = 0
	private var colorTextActive = 0
	private var lastW = 0
	private var progress = 0f
	private var text: String? = null

	@JvmField
	var enabled = true

	fun recolor(c: Int) {
		colorActive = context.getColor(R.color.background)
		colorTextActive = context.getColor(R.color.white)
		colorInactive = c
		invalidate()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		attached = true
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attached = false
	}

	fun isChecked(): Boolean {
		return checked
	}

	fun setChecked(enabled: Boolean) {
		setChecked(enabled, true)
	}

	fun setChecked(enabled: Boolean, animate: Boolean) {
		checked = enabled

		if (!attached || !animate) {
			progress = if (enabled) 1f else 0f
		}
		else {
			checkAnimator?.removeAllListeners()
			checkAnimator?.cancel()

			checkAnimator = ValueAnimator.ofFloat(progress, if (enabled) 1f else 0f)

			checkAnimator?.addUpdateListener {
				progress = it.animatedValue as Float
				invalidate()
			}

			checkAnimator?.duration = 300
			checkAnimator?.start()
		}
	}

	fun setText(text: String?) {
		this.text = text
		requestLayout()
	}

	init {
		textPaint.textSize = AndroidUtilities.dp(14f).toFloat()
		textPaint.textAlign = Paint.Align.CENTER
		textPaint.typeface = Theme.TYPEFACE_DEFAULT

		outLinePaint.strokeWidth = AndroidUtilities.dpf2(1.5f)
		outLinePaint.style = Paint.Style.STROKE

		checkPaint.style = Paint.Style.STROKE
		checkPaint.strokeCap = Paint.Cap.ROUND
		checkPaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		var textW = if (text == null) 0 else textPaint.measureText(text).toInt()
		textW += INNER_PADDING shl 1

		setMeasuredDimension(textW + pInset * 2, HEIGHT + AndroidUtilities.dp(4f))

		if (measuredWidth != lastW) {
			rectF.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
			rectF.inset(pInset + outLinePaint.strokeWidth / 2, pInset + outLinePaint.strokeWidth / 2 + AndroidUtilities.dp(2f))
		}
	}

	override fun draw(canvas: Canvas) {
		super.draw(canvas)

		val textTranslation: Float

		if (progress <= 0.5f) {
			textTranslation = progress / 0.5f

			var rD = ((Color.red(colorInactive) - Color.red(colorActive)) * textTranslation).toInt()
			var gD = ((Color.green(colorInactive) - Color.green(colorActive)) * textTranslation).toInt()
			var bD = ((Color.blue(colorInactive) - Color.blue(colorActive)) * textTranslation).toInt()
			var c = Color.rgb(Color.red(colorActive) + rD, Color.green(colorActive) + gD, Color.blue(colorActive) + bD)

			fillPaint.color = c

			rD = ((Color.red(colorTextActive) - Color.red(colorInactive)) * textTranslation).toInt()
			gD = ((Color.green(colorTextActive) - Color.green(colorInactive)) * textTranslation).toInt()
			bD = ((Color.blue(colorTextActive) - Color.blue(colorInactive)) * textTranslation).toInt()

			c = Color.rgb(Color.red(colorInactive) + rD, Color.green(colorInactive) + gD, Color.blue(colorInactive) + bD)

			textPaint.color = c
		}
		else {
			textTranslation = 1f
			textPaint.color = colorTextActive
			fillPaint.color = colorInactive
		}

		val heightHalf = measuredHeight shr 1

		outLinePaint.color = colorInactive

		canvas.drawRoundRect(rectF, HEIGHT / 2f, HEIGHT / 2f, fillPaint)
		canvas.drawRoundRect(rectF, HEIGHT / 2f, HEIGHT / 2f, outLinePaint)

		text?.let {
			canvas.drawText(it, (measuredWidth shr 1) + textTranslation * TRANSLATE_TEXT, heightHalf + textPaint.textSize * 0.35f, textPaint)
		}

		val bounceProgress = 2.0f - progress / 0.5f

		canvas.save()
		canvas.scale(0.9f, 0.9f, AndroidUtilities.dpf2(7f), heightHalf.toFloat())
		canvas.translate(AndroidUtilities.dp(12f).toFloat(), (heightHalf - AndroidUtilities.dp(9f)).toFloat())

		if (progress > 0.5f) {
			checkPaint.color = colorTextActive

			var endX = (AndroidUtilities.dpf2(7f) - AndroidUtilities.dp(4f) * (1.0f - bounceProgress)).toInt()
			var endY = (AndroidUtilities.dpf2(13f) - AndroidUtilities.dp(4f) * (1.0f - bounceProgress)).toInt()

			canvas.drawLine(AndroidUtilities.dpf2(7f), AndroidUtilities.dpf2(13f).toInt().toFloat(), endX.toFloat(), endY.toFloat(), checkPaint)

			endX = (AndroidUtilities.dpf2(7f) + AndroidUtilities.dp(8f) * (1.0f - bounceProgress)).toInt()
			endY = (AndroidUtilities.dpf2(13f) - AndroidUtilities.dp(8f) * (1.0f - bounceProgress)).toInt()

			canvas.drawLine(AndroidUtilities.dpf2(7f).toInt().toFloat(), AndroidUtilities.dpf2(13f).toInt().toFloat(), endX.toFloat(), endY.toFloat(), checkPaint)
		}

		canvas.restore()
	}

	fun denied() {
		AndroidUtilities.shakeView(this, 2f, 0)
	}

	companion object {
		private val HEIGHT = AndroidUtilities.dp(36f)
		private val INNER_PADDING = AndroidUtilities.dp(22f)
		private val TRANSLATE_TEXT = AndroidUtilities.dp(8f)
	}
}
