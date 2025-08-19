/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.addRipple
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RadioButton

class RadioColorCell(context: Context) : FrameLayout(context) {
	private val textView: TextView
	private val radioButton: RadioButton

	init {
		addRipple()

		radioButton = RadioButton(context)
		radioButton.setSize(AndroidUtilities.dp(40f))
		radioButton.setColor(ResourcesCompat.getColor(resources, R.color.purple, null), ResourcesCompat.getColor(resources, R.color.purple, null))
		radioButton.setProgress(1f)

		addView(radioButton, createFrame(22, 22f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 18).toFloat(), 14f, (if (LocaleController.isRTL) 18 else 0).toFloat(), 0f))

		textView = TextView(context)
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

		addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 21 else 51).toFloat(), 13f, (if (LocaleController.isRTL) 51 else 21).toFloat(), 0f))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50f), MeasureSpec.EXACTLY))
	}

	fun setCheckColor(color1: Int, color2: Int) {
		radioButton.setColor(color1, color2)
	}

	fun setTextAndValue(text: String?, checked: Boolean) {
		textView.text = text
		radioButton.setChecked(checked, false)
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		radioButton.setChecked(checked, animated)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.RadioButton"
		info.isCheckable = true
		info.isChecked = radioButton.isChecked
	}
}
