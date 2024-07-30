package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Region
import android.text.TextPaint
import android.text.TextUtils
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import kotlin.math.max

class OutlineTextContainerView(context: Context) : FrameLayout(context) {
	private val rect = RectF()
	private var mText = ""
	private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val selectionSpring = SpringAnimation(this, SELECTION_PROGRESS_PROPERTY)
	private var selectionProgress = 0f
	private val errorSpring = SpringAnimation(this, ERROR_PROGRESS_PROPERTY)
	private var errorProgress = 0f
	private val strokeWidthRegular = max(2, AndroidUtilities.dp(0.5f)).toFloat()
	private val strokeWidthSelected = AndroidUtilities.dp(1.5f).toFloat()
	private var forceUseCenter = false

	var attachedEditText: EditText? = null
		private set

	init {
		setWillNotDraw(false)
		textPaint.textSize = AndroidUtilities.dp(16f).toFloat()
		outlinePaint.style = Paint.Style.STROKE
		outlinePaint.strokeCap = Paint.Cap.ROUND
		outlinePaint.strokeWidth = strokeWidthRegular
		updateColor()
		setPadding(0, AndroidUtilities.dp(6f), 0, 0)
	}

	fun setForceUseCenter(forceUseCenter: Boolean) {
		this.forceUseCenter = forceUseCenter
		invalidate()
	}

	fun attachEditText(attachedEditText: EditText?) {
		this.attachedEditText = attachedEditText
		invalidate()
	}

	fun setText(text: String) {
		mText = text
		invalidate()
	}

	private fun setColor(color: Int) {
		outlinePaint.color = color
		invalidate()
	}

	fun updateColor() {
		val textSelectionColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteHintText), Theme.getColor(Theme.key_windowBackgroundWhiteValueText), selectionProgress)
		textPaint.color = ColorUtils.blendARGB(textSelectionColor, Theme.getColor(Theme.key_dialogTextRed), errorProgress)
		val selectionColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), selectionProgress)
		setColor(ColorUtils.blendARGB(selectionColor, Theme.getColor(Theme.key_dialogTextRed), errorProgress))
	}

	@JvmOverloads
	fun animateSelection(newValue: Float, animate: Boolean = true) {
		if (!animate) {
			selectionProgress = newValue
			if (!forceUseCenter) {
				outlinePaint.strokeWidth = strokeWidthRegular + (strokeWidthSelected - strokeWidthRegular) * selectionProgress
			}
			updateColor()
			return
		}
		animateSpring(selectionSpring, newValue)
	}

	fun animateError(newValue: Float) {
		animateSpring(errorSpring, newValue)
	}

	private fun animateSpring(spring: SpringAnimation, newValue: Float) {
		var updatedValue = newValue
		updatedValue *= SPRING_MULTIPLIER

		if (spring.spring != null && updatedValue == spring.spring.finalPosition) {
			return
		}

		spring.cancel()
		spring.setSpring(SpringForce(updatedValue).setStiffness(500f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY).setFinalPosition(updatedValue)).start()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		val textOffset = textPaint.textSize / 2f - AndroidUtilities.dp(1.75f)
		val topY = paddingTop + textOffset
		val centerY = height / 2f + textPaint.textSize / 2f

		val useCenter = (attachedEditText != null && attachedEditText!!.length() == 0 && TextUtils.isEmpty(attachedEditText!!.hint)) || forceUseCenter

		val textY = if (useCenter) topY + (centerY - topY) * (1f - selectionProgress) else topY
		val stroke = outlinePaint.strokeWidth
		val scaleX = if (useCenter) 0.75f + 0.25f * (1f - selectionProgress) else 0.75f
		val textWidth = textPaint.measureText(mText) * scaleX

		canvas.save()

		rect[(paddingLeft + AndroidUtilities.dp((PADDING_LEFT - PADDING_TEXT).toFloat())).toFloat(), paddingTop.toFloat(), (width - AndroidUtilities.dp((PADDING_LEFT + PADDING_TEXT).toFloat()) - paddingRight).toFloat()] = paddingTop + stroke * 2

		canvas.clipRect(rect, Region.Op.DIFFERENCE)

		rect[paddingLeft + stroke, paddingTop + stroke, width - stroke - paddingRight] = height - stroke - paddingBottom

		canvas.drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), outlinePaint)
		canvas.restore()

		val left = (paddingLeft + AndroidUtilities.dp((PADDING_LEFT - PADDING_TEXT).toFloat())).toFloat()
		val lineY = paddingTop + stroke
		val right = width - stroke - paddingRight - AndroidUtilities.dp(6f)
		val activeLeft = left + textWidth + AndroidUtilities.dp((PADDING_LEFT - PADDING_TEXT).toFloat())
		val fromLeft = left + textWidth / 2f

		canvas.drawLine(fromLeft + (activeLeft - fromLeft) * if (useCenter) selectionProgress else 1f, lineY, right, lineY, outlinePaint)

		val fromRight = left + textWidth / 2f + AndroidUtilities.dp(PADDING_TEXT.toFloat())

		canvas.drawLine(left, lineY, fromRight + (left - fromRight) * if (useCenter) selectionProgress else 1f, lineY, outlinePaint)
		canvas.save()
		canvas.scale(scaleX, scaleX, (paddingLeft + AndroidUtilities.dp((PADDING_LEFT + PADDING_TEXT).toFloat())).toFloat(), textY)
		canvas.drawText(mText, (paddingLeft + AndroidUtilities.dp(PADDING_LEFT.toFloat())).toFloat(), textY, textPaint)
		canvas.restore()
	}

	companion object {
		private const val PADDING_LEFT = 14
		private const val PADDING_TEXT = 4
		private const val SPRING_MULTIPLIER = 100f

		private val SELECTION_PROGRESS_PROPERTY = SimpleFloatPropertyCompat("selectionProgress", SimpleFloatPropertyCompat.Getter<OutlineTextContainerView> { it.selectionProgress }) { obj, value ->
			obj.selectionProgress = value

			if (!obj.forceUseCenter) {
				obj.outlinePaint.strokeWidth = obj.strokeWidthRegular + (obj.strokeWidthSelected - obj.strokeWidthRegular) * obj.selectionProgress
				obj.updateColor()
			}

			obj.invalidate()
		}.setMultiplier(SPRING_MULTIPLIER)

		private val ERROR_PROGRESS_PROPERTY = SimpleFloatPropertyCompat("errorProgress", SimpleFloatPropertyCompat.Getter<OutlineTextContainerView> { it.errorProgress }) { obj, value ->
			obj.errorProgress = value
			obj.updateColor()
		}.setMultiplier(SPRING_MULTIPLIER)
	}
}