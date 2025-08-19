/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadioButton

class RadioCell @JvmOverloads constructor(context: Context, padding: Int = 21) : FrameLayout(context) {
	private val textView = TextView(context)
	private val radioButton: RadioButton
	private var needDivider = false

	init {
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

		addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, padding.toFloat(), 0f, padding.toFloat(), 0f))

		radioButton = RadioButton(context)
		radioButton.setSize(AndroidUtilities.dp(20f))
		radioButton.setColor(context.getColor(R.color.brand), context.getColor(R.color.brand))

		addView(radioButton, LayoutHelper.createFrame(22, 22f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) padding + 1 else 0).toFloat(), 0f, (if (LocaleController.isRTL) 0 else padding + 1).toFloat(), 0f))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50f) + if (needDivider) 1 else 0)
		val availableWidth = measuredWidth - paddingLeft - paddingRight - AndroidUtilities.dp(34f)
		radioButton.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22f), MeasureSpec.EXACTLY))
		textView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
	}

	fun setTextColor(color: Int) {
		textView.setTextColor(color)
	}

	fun setText(text: String?, checked: Boolean, divider: Boolean) {
		textView.text = text
		radioButton.setChecked(checked, false)
		needDivider = divider
		setWillNotDraw(!divider)
	}

	val isChecked: Boolean
		get() = radioButton.isChecked

	fun setChecked(checked: Boolean, animated: Boolean) {
		radioButton.setChecked(checked, animated)
	}

	fun setEnabled(value: Boolean, animators: ArrayList<Animator>?) {
		super.setEnabled(value)

		if (animators != null) {
			animators.add(ObjectAnimator.ofFloat(textView, ALPHA, if (value) 1.0f else 0.5f))
			animators.add(ObjectAnimator.ofFloat(radioButton, ALPHA, if (value) 1.0f else 0.5f))
		}
		else {
			textView.alpha = if (value) 1.0f else 0.5f
			radioButton.alpha = if (value) 1.0f else 0.5f
		}
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			canvas.drawLine(if (LocaleController.isRTL) 0f else AndroidUtilities.dp(20f).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(20f) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.RadioButton"
		info.isCheckable = true
		info.isChecked = isChecked
	}
}
