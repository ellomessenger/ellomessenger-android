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
import android.graphics.Canvas
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.CheckBoxSquare
import org.telegram.ui.Components.LayoutHelper

class CheckBoxCell @JvmOverloads constructor(context: Context, private val currentType: Int, padding: Int = 17) : FrameLayout(context) {
	private var checkBoxRound: CheckBox2? = null
	private var checkBoxSize = 18
	private var checkBoxSquare: CheckBoxSquare? = null
	private var isMultiline = false
	private var needDivider = false
	val textView = TextView(context)
	val valueTextView: TextView
	var checkBoxView: View? = null

	init {
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END

		if (currentType == 3) {
			textView.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
			textView.setPadding(0, 0, 0, AndroidUtilities.dp(3f))

			addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.TOP, 29f, 0f, 0f, 0f))
		}
		else {
			textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

			if (currentType == 2) {
				addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 29).toFloat(), 0f, (if (LocaleController.isRTL) 29 else 0).toFloat(), 0f))
			}
			else {
				val offset = if (currentType == 4) 56 else 46
				addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) padding else offset + (padding - 17)).toFloat(), 0f, (if (LocaleController.isRTL) offset + (padding - 17) else padding).toFloat(), 0f))
			}
		}

		valueTextView = TextView(context)
		valueTextView.setTextColor(context.getColor(if (currentType == 1 || currentType == 5) R.color.brand else R.color.text))
		valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		valueTextView.setLines(1)
		valueTextView.maxLines = 1
		valueTextView.isSingleLine = true
		valueTextView.ellipsize = TextUtils.TruncateAt.END
		valueTextView.gravity = (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL

		addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP, padding.toFloat(), 0f, padding.toFloat(), 0f))

		if (currentType == TYPE_CHECK_BOX_ROUND) {
			checkBoxRound = CheckBox2(context, 21)

			checkBoxView = checkBoxRound

			checkBoxRound?.setDrawUnchecked(true)
			checkBoxRound?.setChecked(checked = true, animated = false)
			checkBoxRound?.setDrawBackgroundAsArc(10)

			checkBoxSize = 21

			addView(checkBoxView, LayoutHelper.createFrame(checkBoxSize, checkBoxSize.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else padding).toFloat(), 16f, (if (LocaleController.isRTL) padding else 0).toFloat(), 0f))
		}
		else {
			checkBoxSquare = CheckBoxSquare(context)

			checkBoxView = checkBoxSquare

			checkBoxSize = 18

			when (currentType) {
				5 -> {
					addView(checkBoxView, LayoutHelper.createFrame(checkBoxSize, checkBoxSize.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) 0 else padding).toFloat(), 0f, (if (LocaleController.isRTL) padding else 0).toFloat(), 0f))
				}

				3 -> {
					addView(checkBoxView, LayoutHelper.createFrame(checkBoxSize, checkBoxSize.toFloat(), Gravity.LEFT or Gravity.TOP, 0f, 15f, 0f, 0f))
				}

				2 -> {
					addView(checkBoxView, LayoutHelper.createFrame(checkBoxSize, checkBoxSize.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 0f, 15f, 0f, 0f))
				}

				else -> {
					addView(checkBoxView, LayoutHelper.createFrame(checkBoxSize, checkBoxSize.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else padding).toFloat(), 16f, (if (LocaleController.isRTL) padding else 0).toFloat(), 0f))
				}
			}
		}

		updateTextColor()
	}

	private fun updateTextColor() {
		textView.setTextColor(context.getColor(if (currentType == 1 || currentType == 5) R.color.text else R.color.text))
		textView.setLinkTextColor(context.getColor(if (currentType == 1 || currentType == 5) R.color.brand else R.color.brand))
		valueTextView.setTextColor(context.getColor(if (currentType == 1 || currentType == 5) R.color.brand else R.color.text))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (currentType == 3) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			valueTextView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(10f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50f), MeasureSpec.EXACTLY))
			textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(34f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50f), MeasureSpec.EXACTLY))
			checkBoxView?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize.toFloat()), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize.toFloat()), MeasureSpec.EXACTLY))
			setMeasuredDimension(textView.measuredWidth + AndroidUtilities.dp(29f), AndroidUtilities.dp(50f))
		}
		else if (isMultiline) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
		}
		else {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50f) + if (needDivider) 1 else 0)
			val availableWidth = measuredWidth - paddingLeft - paddingRight - AndroidUtilities.dp(34f)
			valueTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth / 2, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
			textView.measure(MeasureSpec.makeMeasureSpec(availableWidth - valueTextView.measuredWidth - AndroidUtilities.dp(8f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
			checkBoxView?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize.toFloat()), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize.toFloat()), MeasureSpec.EXACTLY))
		}
	}

	fun setTextColor(color: Int) {
		textView.setTextColor(color)
	}

	fun setText(text: CharSequence?, value: String?, checked: Boolean, divider: Boolean) {
		textView.text = text
		checkBoxRound?.setChecked(checked, false) ?: checkBoxSquare?.setChecked(checked, false)
		valueTextView.text = value
		needDivider = divider
		setWillNotDraw(!divider)
	}

	fun setNeedDivider(needDivider: Boolean) {
		this.needDivider = needDivider
	}

	fun setMultiline(value: Boolean) {
		isMultiline = value

		val layoutParams = textView.layoutParams as LayoutParams
		val layoutParams1 = checkBoxView?.layoutParams as? LayoutParams

		if (isMultiline) {
			textView.setLines(0)
			textView.maxLines = 0
			textView.isSingleLine = false
			textView.ellipsize = null

			if (currentType != 5) {
				textView.setPadding(0, 0, 0, AndroidUtilities.dp(5f))

				layoutParams.height = LayoutParams.WRAP_CONTENT
				layoutParams.topMargin = AndroidUtilities.dp(10f)
				layoutParams1?.topMargin = AndroidUtilities.dp(12f)
			}
		}
		else {
			textView.setLines(1)
			textView.maxLines = 1
			textView.isSingleLine = true
			textView.ellipsize = TextUtils.TruncateAt.END
			textView.setPadding(0, 0, 0, 0)

			layoutParams.height = LayoutParams.MATCH_PARENT
			layoutParams.topMargin = 0
			layoutParams1?.topMargin = AndroidUtilities.dp(15f)
		}

		textView.layoutParams = layoutParams
		checkBoxView?.layoutParams = layoutParams1
	}

	override fun setEnabled(enabled: Boolean) {
		super.setEnabled(enabled)
		textView.alpha = if (enabled) 1.0f else 0.5f
		valueTextView.alpha = if (enabled) 1.0f else 0.5f
		checkBoxView?.alpha = if (enabled) 1.0f else 0.5f
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		checkBoxRound?.setChecked(checked, animated) ?: checkBoxSquare?.setChecked(checked, animated)
	}

	val isChecked: Boolean
		get() = checkBoxRound?.isChecked ?: checkBoxSquare?.isChecked ?: false

	fun setCheckBoxColor(@ColorInt background: Int, @ColorInt background1: Int, @ColorInt check: Int) {
		checkBoxRound?.setColor(background, background1, check)
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			val offset = if (currentType == TYPE_CHECK_BOX_ROUND) 50 else 20
			canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(offset.toFloat())).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(offset.toFloat()) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.CheckBox"
		info.isCheckable = true
		info.isChecked = isChecked
	}

	companion object {
		const val TYPE_CHECK_BOX_ROUND = 4
	}
}