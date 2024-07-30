/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Switch

class TextCheckCell2(context: Context) : FrameLayout(context) {
	private val textView: TextView
	private val valueTextView: TextView
	private val checkBox: Switch
	private var needDivider = false
	private var isMultiline = false

	init {
		textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
		textView.ellipsize = TextUtils.TruncateAt.END

		addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 64 else 21).toFloat(), 0f, (if (LocaleController.isRTL) 21 else 64).toFloat(), 0f))

		valueTextView = TextView(context)
		valueTextView.setTextColor(context.getColor(R.color.dark_gray))
		valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		valueTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		valueTextView.setLines(1)
		valueTextView.maxLines = 1
		valueTextView.isSingleLine = true
		valueTextView.setPadding(0, 0, 0, 0)
		valueTextView.ellipsize = TextUtils.TruncateAt.END

		addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 64 else 21).toFloat(), 35f, (if (LocaleController.isRTL) 21 else 64).toFloat(), 0f))

		checkBox = Switch(context)
		//MARK: checkBox.setDrawIconType(0) is responsible for drawing a check mark and a cross inside the switcher, if you want to display them, replace checkBox.setDrawIconType(0) with checkBox.setDrawIconType(1)
		checkBox.setDrawIconType(0)

		addView(checkBox, LayoutHelper.createFrame(37, 40f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, 22f, 0f, 22f, 0f))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (isMultiline) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
		}
		else {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((if (valueTextView.visibility == VISIBLE) 64 else 50).toFloat()) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
		}
	}

	fun setTextAndCheck(text: String?, checked: Boolean, divider: Boolean) {
		textView.text = text

		isMultiline = false

		checkBox.setChecked(checked, false)

		needDivider = divider

		valueTextView.gone()

		textView.updateLayoutParams<LayoutParams> {
			height = LayoutParams.MATCH_PARENT
			topMargin = 0
		}

		setWillNotDraw(!divider)
	}

	fun setTextAndValueAndCheck(text: String?, value: String?, checked: Boolean, multiline: Boolean, divider: Boolean) {
		textView.text = text
		valueTextView.text = value

		checkBox.setChecked(checked, false)

		needDivider = divider

		valueTextView.visible()

		isMultiline = multiline

		if (multiline) {
			valueTextView.setLines(0)
			valueTextView.maxLines = 0
			valueTextView.isSingleLine = false
			valueTextView.ellipsize = null
			valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(11f))
		}
		else {
			valueTextView.setLines(1)
			valueTextView.maxLines = 1
			valueTextView.isSingleLine = true
			valueTextView.ellipsize = TextUtils.TruncateAt.END
			valueTextView.setPadding(0, 0, 0, 0)
		}

		textView.updateLayoutParams<LayoutParams> {
			height = LayoutParams.WRAP_CONTENT
			topMargin = AndroidUtilities.dp(10f)
		}

		setWillNotDraw(!divider)
	}

	override fun setEnabled(value: Boolean) {
		super.setEnabled(value)
		textView.clearAnimation()
		valueTextView.clearAnimation()
		checkBox.clearAnimation()

		if (value) {
			textView.alpha = 1.0f
			valueTextView.alpha = 1.0f
			checkBox.alpha = 1.0f
		}
		else {
			checkBox.alpha = 0.5f
			textView.alpha = 0.5f
			valueTextView.alpha = 0.5f
		}
	}

	fun setEnabled(value: Boolean, animated: Boolean) {
		super.setEnabled(value)

		if (animated) {
			textView.clearAnimation()
			valueTextView.clearAnimation()
			checkBox.clearAnimation()
			textView.animate().alpha(if (value) 1f else .5f).start()
			valueTextView.animate().alpha(if (value) 1f else .5f).start()
			checkBox.animate().alpha(if (value) 1f else .5f).start()
		}
		else {
			if (value) {
				textView.alpha = 1.0f
				valueTextView.alpha = 1.0f
				checkBox.alpha = 1.0f
			}
			else {
				checkBox.alpha = 0.5f
				textView.alpha = 0.5f
				valueTextView.alpha = 0.5f
			}
		}
	}

	fun setIcon(icon: Int) {
		checkBox.setIcon(icon)
	}

	fun hasIcon(): Boolean {
		return checkBox.hasIcon()
	}

	var isChecked: Boolean
		get() = checkBox.isChecked
		set(checked) {
			checkBox.setChecked(checked, true)
		}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(20f)).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(20f) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.Switch"
		info.isCheckable = true
		info.isChecked = checkBox.isChecked
	}
}
