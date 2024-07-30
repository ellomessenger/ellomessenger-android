/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createFrameRelatively

class TextDetailCell(context: Context) : FrameLayout(context) {
	private val textView: TextView
	private val valueTextView: TextView
	private val imageView: ImageView
	private var needDivider = false
	private var contentDescriptionValueFirst = false

	init {
		textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		textView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

		addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16f, 32f, 16f, 0f))

		valueTextView = TextView(context)
		valueTextView.setTextColor(context.getColor(R.color.disabled_text))
		valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
		valueTextView.setLines(1)
		valueTextView.maxLines = 1
		valueTextView.isSingleLine = true
		valueTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		valueTextView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

		addView(valueTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 16f, 12f, 16f, 0f))

		imageView = ImageView(context)
		imageView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		imageView.scaleType = ImageView.ScaleType.CENTER

		addView(imageView, createFrameRelatively(48f, 48f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 12f, 0f))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62f), MeasureSpec.EXACTLY))
	}

	fun setTextAndValue(text: String?, value: String?, divider: Boolean) {
		textView.text = value
		valueTextView.text = text
		needDivider = divider
		setWillNotDraw(!needDivider)
	}

	fun setImage(drawable: Drawable?) {
		setImage(drawable, null)
	}

	fun setImage(drawable: Drawable?, imageContentDescription: CharSequence?) {
		imageView.setImageDrawable(drawable)
		imageView.isFocusable = drawable != null
		imageView.contentDescription = imageContentDescription

		if (drawable == null) {
			imageView.background = null
			imageView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		else {
			imageView.background = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(48f), Color.TRANSPARENT, ResourcesCompat.getColor(context.resources, R.color.light_background, null))
			imageView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
		}

		val margin = AndroidUtilities.dp(23f) + if (drawable == null) 0 else AndroidUtilities.dp(48f)

		if (LocaleController.isRTL) {
			(textView.layoutParams as MarginLayoutParams).leftMargin = margin
		}
		else {
			(textView.layoutParams as MarginLayoutParams).rightMargin = margin
		}

		textView.requestLayout()
	}

	fun setTextAppearance(textStyleId: Int, valueStyleId: Int) {
		textView.setTextAppearance(textStyleId)
		valueTextView.setTextAppearance(valueStyleId)
	}

	fun initTextParams() {
		val typeface = ResourcesCompat.getFont(context, R.font.inter_regular)

		textView.setTextColor(context.getColor(R.color.brand))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.typeface = typeface

		valueTextView.setTextColor(context.getColor(R.color.dark_gray))
		valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
		valueTextView.typeface = typeface
	}

	fun setImageClickListener(clickListener: OnClickListener?) {
		imageView.setOnClickListener(clickListener)
	}

	fun setTextWithEmojiAndValue(text: CharSequence?, value: CharSequence?, divider: Boolean) {
		textView.text = Emoji.replaceEmoji(text, textView.paint.fontMetricsInt, false)
		valueTextView.text = value
		needDivider = divider
		setWillNotDraw(!divider)
	}

	fun setContentDescriptionValueFirst(contentDescriptionValueFirst: Boolean) {
		this.contentDescriptionValueFirst = contentDescriptionValueFirst
	}

	override fun invalidate() {
		super.invalidate()
		textView.invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			canvas.drawLine(0f, (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		val text = textView.text
		val valueText = valueTextView.text

		if (!TextUtils.isEmpty(text) && !TextUtils.isEmpty(valueText)) {
			info.text = (if (contentDescriptionValueFirst) valueText else text).toString() + ": " + if (contentDescriptionValueFirst) text else valueText
		}
	}
}
