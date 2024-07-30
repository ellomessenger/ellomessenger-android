/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimationProperties
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Switch
import kotlin.math.max

open class TextCheckCell @JvmOverloads constructor(context: Context, padding: Int = 21) : FrameLayout(context) {
	private val textView: TextView
	private val valueTextView: TextView
	private val checkBox: Switch
	private var needDivider = false
	private var isMultiline = false
	private var height = 50
	private var animatedColorBackground = 0
	private var animationProgress = 0f
	private var lastTouchX = 0f
	private var animator: ObjectAnimator? = null
	private var drawCheckRipple = false

	private val animationPaint by lazy {
		Paint(Paint.ANTI_ALIAS_FLAG)
	}

	init {
		textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setLines(1)
		textView.setMaxLines(1)
		textView.setSingleLine(true)
		textView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL)
		textView.ellipsize = TextUtils.TruncateAt.END

		addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 70 else padding).toFloat(), 0f, (if (LocaleController.isRTL) padding else 70).toFloat(), 0f))

		valueTextView = TextView(context)
		valueTextView.setTextColor(context.getColor(R.color.dark_gray))
		valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		valueTextView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		valueTextView.setLines(1)
		valueTextView.setMaxLines(1)
		valueTextView.setSingleLine(true)
		valueTextView.setPadding(0, 0, 0, 0)
		valueTextView.ellipsize = TextUtils.TruncateAt.END

		addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 64 else padding).toFloat(), 36f, (if (LocaleController.isRTL) padding else 64).toFloat(), 0f))

		checkBox = Switch(context)

		addView(checkBox, LayoutHelper.createFrame(37, 20f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, 22f, 0f, 22f, 0f))

		setClipChildren(false)
	}

	override fun setEnabled(enabled: Boolean) {
		super.setEnabled(enabled)
		checkBox.setEnabled(enabled)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (isMultiline) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
		}
		else {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((if (valueTextView.visibility == VISIBLE) 64 else height).toFloat()) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		lastTouchX = event.x
		return super.onTouchEvent(event)
	}

	fun setDivider(divider: Boolean) {
		needDivider = divider
		setWillNotDraw(!divider)
	}

	fun setTextAndCheck(text: String?, checked: Boolean, divider: Boolean) {
		textView.text = text
		isMultiline = false

		checkBox.setChecked(checked, false)

		needDivider = divider

		valueTextView.gone()

		val layoutParams = textView.layoutParams as LayoutParams
		layoutParams.height = LayoutParams.MATCH_PARENT
		layoutParams.topMargin = 0

		textView.setLayoutParams(layoutParams)

		setWillNotDraw(!divider)
	}

	fun setTypeface(typeface: Typeface?) {
		textView.setTypeface(typeface)
	}

	fun setHeight(value: Int) {
		height = value
	}

	fun setDrawCheckRipple(value: Boolean) {
		drawCheckRipple = value
	}

	override fun setPressed(pressed: Boolean) {
		if (drawCheckRipple) {
			checkBox.setDrawRipple(pressed)
		}

		super.setPressed(pressed)
	}

	fun setTextAndValueAndCheck(text: String?, value: String?, checked: Boolean, multiline: Boolean, divider: Boolean) {
		textView.text = text
		valueTextView.text = value
		needDivider = divider
		isMultiline = multiline

		checkBox.setChecked(checked, false)

		valueTextView.visible()

		if (multiline) {
			valueTextView.setLines(0)
			valueTextView.setMaxLines(0)
			valueTextView.setSingleLine(false)
			valueTextView.ellipsize = null
			valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(11f))
		}
		else {
			valueTextView.setLines(1)
			valueTextView.setMaxLines(1)
			valueTextView.setSingleLine(true)
			valueTextView.ellipsize = TextUtils.TruncateAt.END
			valueTextView.setPadding(0, 0, 0, 0)
		}

		val layoutParams = textView.layoutParams as LayoutParams
		layoutParams.height = LayoutParams.WRAP_CONTENT
		layoutParams.topMargin = AndroidUtilities.dp(10f)

		textView.setLayoutParams(layoutParams)

		setWillNotDraw(!divider)
	}

	fun setEnabled(value: Boolean, animators: ArrayList<Animator>?) {
		super.setEnabled(value)

		if (animators != null) {
			animators.add(ObjectAnimator.ofFloat(textView, ALPHA, if (value) 1.0f else 0.5f))
			animators.add(ObjectAnimator.ofFloat(checkBox, ALPHA, if (value) 1.0f else 0.5f))

			if (valueTextView.visibility == VISIBLE) {
				animators.add(ObjectAnimator.ofFloat(valueTextView, ALPHA, if (value) 1.0f else 0.5f))
			}
		}
		else {
			textView.setAlpha(if (value) 1.0f else 0.5f)
			checkBox.setAlpha(if (value) 1.0f else 0.5f)

			if (valueTextView.visibility == VISIBLE) {
				valueTextView.setAlpha(if (value) 1.0f else 0.5f)
			}
		}
	}

	var isChecked: Boolean
		get() {
			return checkBox.isChecked
		}
		set(checked) {
			checkBox.setChecked(checked, true)
		}

	override fun setBackgroundColor(color: Int) {
		clearAnimation()
		animatedColorBackground = 0
		super.setBackgroundColor(color)
	}

	fun setBackgroundColorAnimated(checked: Boolean, color: Int) {
		animator?.cancel()
		animator = null

		if (animatedColorBackground != 0) {
			setBackgroundColor(animatedColorBackground)
		}

		checkBox.setOverrideColor(if (checked) 1 else 2)

		animatedColorBackground = color

		animationPaint.setColor(animatedColorBackground)

		animationProgress = 0.0f

		animator = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS, 0.0f, 1.0f)

		animator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				setBackgroundColor(animatedColorBackground)
				animatedColorBackground = 0
				invalidate()
			}
		})

		animator?.interpolator = CubicBezierInterpolator.EASE_OUT
		animator?.setDuration(240)?.start()
	}

	private fun setAnimationProgress(value: Float) {
		animationProgress = value

		val tx = lastTouchX
		val rad = (max(tx.toDouble(), (measuredWidth - tx).toDouble()) + AndroidUtilities.dp(40f)).toFloat()
		val cy = measuredHeight / 2
		val animatedRad = rad * animationProgress

		checkBox.setOverrideColorProgress(tx, cy.toFloat(), animatedRad)
	}

	fun setBackgroundColorAnimatedReverse(color: Int) {
		animator?.cancel()
		animator = null

		val from = if (animatedColorBackground != 0) animatedColorBackground else if (background is ColorDrawable) (background as ColorDrawable).color else 0

		animationPaint.setColor(from)

		setBackgroundColor(color)
		checkBox.setOverrideColor(1)

		animatedColorBackground = color

		animator = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS, 1f, 0f).setDuration(240)

		animator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				setBackgroundColor(color)
				animatedColorBackground = 0
				invalidate()
			}
		})

		animator?.interpolator = CubicBezierInterpolator.EASE_OUT
		animator?.start()
	}

	override fun onDraw(canvas: Canvas) {
		if (animatedColorBackground != 0) {
			val tx = lastTouchX
			val rad = (max(tx.toDouble(), (measuredWidth - tx).toDouble()) + AndroidUtilities.dp(40f)).toFloat()
			val cy = measuredHeight / 2
			val animatedRad = rad * animationProgress

			canvas.drawCircle(tx, cy.toFloat(), animatedRad, animationPaint)
		}

		if (needDivider) {
			canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(20f)).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(20f) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.setClassName("android.widget.Switch")
		info.isCheckable = true
		info.isChecked = checkBox.isChecked

		info.setContentDescription(buildString {
			append(textView.getText())

			val valueTextViewText = valueTextView.getText()

			if (!valueTextViewText.isNullOrEmpty()) {
				append('\n')
				append(valueTextViewText)
			}
		})
	}

	companion object {
		val ANIMATION_PROGRESS = object : AnimationProperties.FloatProperty<TextCheckCell>("animationProgress") {
			override fun setValue(`object`: TextCheckCell, value: Float) {
				`object`.setAnimationProgress(value)
				`object`.invalidate()
			}

			override operator fun get(`object`: TextCheckCell): Float {
				return `object`.animationProgress
			}
		}
	}
}
