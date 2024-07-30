/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper

class RadioButtonCell(context: Context) : FrameLayout(context) {
	private val textView: TextView
	private val radioButton: android.widget.RadioButton
	private var needDivider = false
	private val valueTextView: TextView

	init {
		radioButton = android.widget.RadioButton(context)
		radioButton.id = View.generateViewId()
		radioButton.isEnabled = true
		radioButton.isClickable = false
		radioButton.isFocusable = false

		val colorStateList = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)), intArrayOf(
				context.getColor(R.color.hint),  // Unchecked
				context.getColor(R.color.brand), // Checked
		))

		radioButton.buttonTintList = colorStateList

		addView(radioButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 20).toFloat(), 10f, (if (LocaleController.isRTL) 20 else 0).toFloat(), 0f))

		textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

		addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 23 else 61).toFloat(), 10f, (if (LocaleController.isRTL) 61 else 23).toFloat(), 0f))

		valueTextView = TextView(context)
		valueTextView.setTextColor(context.getColor(R.color.dark_gray))
		valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		valueTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		valueTextView.setLines(0)
		valueTextView.maxLines = 0
		valueTextView.isSingleLine = false
		valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(12f))

		addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 17 else 61).toFloat(), 35f, (if (LocaleController.isRTL) 61 else 17).toFloat(), 0f))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
	}

	fun setTextAndValue(text: String?, value: String?, divider: Boolean, checked: Boolean) {
		textView.text = text
		valueTextView.text = value
		radioButton.isChecked = checked
		needDivider = divider
	}

	fun setChecked(checked: Boolean) {
		radioButton.isChecked = checked
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			canvas.drawLine(AndroidUtilities.dp(if (LocaleController.isRTL) 0f else 60f).toFloat(), (height - 1).toFloat(), (measuredWidth - AndroidUtilities.dp(if (LocaleController.isRTL) 60f else 0f)).toFloat(), (height - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.RadioButton"
		info.isCheckable = true
		info.isChecked = radioButton.isChecked
	}
}
