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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.switchmaterial.SwitchMaterial
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class NotificationsCheckCell @JvmOverloads constructor(context: Context, private val padding: Int = 16, height: Int = 52, private val reorder: Boolean = false) : FrameLayout(context) {
	private val textView: TextView
	private val valueTextView: TextView
	private val checkBox: SwitchMaterial
	private val currentHeight: Int
	private var needDivider = false
	private var drawLine = false
	private var isMultiline = false
	private var animationsEnabled = false

	var isChecked: Boolean
		get() = checkBox.isChecked
		set(checked) {
			checkBox.isChecked = checked
		}

	init {
		setWillNotDraw(false)

		currentHeight = height

		if (reorder) {
			val moveImageView = ImageView(context)
			moveImageView.isFocusable = false
			moveImageView.scaleType = ImageView.ScaleType.CENTER
			moveImageView.setImageResource(R.drawable.poll_reorder)
			moveImageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)
			addView(moveImageView, createFrame(48, 48f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, 6f, 0f, 6f, 0f))
		}

		textView = TextView(context)
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
		textView.ellipsize = TextUtils.TruncateAt.END

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 80f else (if (reorder) 64f else padding.toFloat()), (13 + (currentHeight - 70) / 2).toFloat(), if (LocaleController.isRTL) (if (reorder) 64f else padding.toFloat()) else 80f, 0f))

		valueTextView = TextView(context)
		valueTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		valueTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		valueTextView.setLines(1)
		valueTextView.maxLines = 1
		valueTextView.isSingleLine = true
		valueTextView.setPadding(0, 0, 0, 0)
		valueTextView.ellipsize = TextUtils.TruncateAt.END

		addView(valueTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 80f else (if (reorder) 64f else padding.toFloat()), (38 + (currentHeight - 70) / 2).toFloat(), if (LocaleController.isRTL) (if (reorder) 64f else padding.toFloat()) else 80f, 0f))

		checkBox = SwitchMaterial(context)
		checkBox.isFocusable = false
		checkBox.isClickable = false

		addView(checkBox, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, 21f, 0f, 21f, 0f))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (isMultiline) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
		}
		else {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(currentHeight.toFloat()), MeasureSpec.EXACTLY))
		}
	}

	fun setTextAndValueAndCheck(text: String?, value: CharSequence?, checked: Boolean, divider: Boolean) {
		setTextAndValueAndCheck(text, value, checked, false, divider)
	}

	fun setTextAndValueAndCheck(text: String?, value: CharSequence?, checked: Boolean, multiline: Boolean, divider: Boolean) {
		textView.text = text
		valueTextView.text = value

		checkBox.isChecked = checked

		needDivider = divider
		isMultiline = multiline

		if (value.isNullOrEmpty()) {
			valueTextView.gone()

			textView.updateLayoutParams<LayoutParams> {
				leftMargin = AndroidUtilities.dp((if (LocaleController.isRTL) 80 else (if (reorder) 64 else padding)).toFloat())
				topMargin = 0
				rightMargin = AndroidUtilities.dp((if (LocaleController.isRTL) (if (reorder) 64 else padding) else 80).toFloat())
				bottomMargin = 0

				gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
			}
		}
		else {
			textView.updateLayoutParams<LayoutParams> {
				leftMargin = AndroidUtilities.dp((if (LocaleController.isRTL) 80 else (if (reorder) 64 else padding)).toFloat())
				topMargin = AndroidUtilities.dp((13 + (currentHeight - 70) / 2).toFloat())
				rightMargin = AndroidUtilities.dp((if (LocaleController.isRTL) (if (reorder) 64 else padding) else 80).toFloat())
				bottomMargin = 0

				gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
			}

			valueTextView.visible()

			if (multiline) {
				valueTextView.setLines(0)
				valueTextView.maxLines = 0
				valueTextView.isSingleLine = false
				valueTextView.ellipsize = null
				valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(14f))
			}
			else {
				valueTextView.setLines(1)
				valueTextView.maxLines = 1
				valueTextView.isSingleLine = true
				valueTextView.ellipsize = TextUtils.TruncateAt.END
				valueTextView.setPadding(0, 0, 0, 0)
			}
		}

		checkBox.contentDescription = text
	}

	fun setDrawLine(value: Boolean) {
		drawLine = value
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			canvas.drawLine(if (LocaleController.isRTL) 0f else AndroidUtilities.dp(20f).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(20f) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}

		if (drawLine) {
			val x = if (LocaleController.isRTL) AndroidUtilities.dp(76f)
			else measuredWidth - AndroidUtilities.dp(76f) - 1
			val y = (measuredHeight - AndroidUtilities.dp(22f)) / 2
			canvas.drawRect(x.toFloat(), y.toFloat(), (x + 2).toFloat(), (y + AndroidUtilities.dp(22f)).toFloat(), Theme.dividerPaint)
		}
	}

	fun setAnimationsEnabled(animationsEnabled: Boolean) {
		this.animationsEnabled = animationsEnabled
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.className = "android.widget.Switch"

		info.contentDescription = buildString {
			append(textView.text)

			valueTextView.text?.takeIf { it.isNotEmpty() }?.run {
				append("\n")
				append(this)
			}
		}

		info.isCheckable = true
		info.isChecked = checkBox.isChecked
	}
}
