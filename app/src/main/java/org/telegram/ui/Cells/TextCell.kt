/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.text.SpannedString
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RLottieImageView

open class TextCell @JvmOverloads constructor(context: Context, private val leftPadding: Int = 23, boldText: Boolean = false, private val large: Boolean = false, private val fullDivider: Boolean = false) : FrameLayout(context) {
	val valueTextView: SimpleTextView
	private val valueImageView: ImageView
	private var needDivider = false
	private var offsetFromImage = 71
	private var inDialogs = false
	private var prioritizeTitleOverValue = false
	var imageLeft = 21
	val textView: SimpleTextView
	val imageView: RLottieImageView

	init {
		textView = SimpleTextView(context)
		textView.textColor = context.getColor(R.color.text)
		textView.setTextSize(15)
		textView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		textView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

		if (boldText) {
			textView.setTypeface(Theme.TYPEFACE_BOLD)
		}

		addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat()))

		valueTextView = SimpleTextView(context)

		if (boldText) {
			valueTextView.setTypeface(Theme.TYPEFACE_BOLD)
		}

		valueTextView.textColor = context.getColor(R.color.text)
		valueTextView.setTextSize(15)
		valueTextView.setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)
		valueTextView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

		addView(valueTextView)

		imageView = RLottieImageView(context)
		imageView.scaleType = ImageView.ScaleType.CENTER
		imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)

		addView(imageView)

		valueImageView = ImageView(context)
		valueImageView.scaleType = ImageView.ScaleType.CENTER

		addView(valueImageView)

		isFocusable = true
	}

//	fun setBold(bold: Boolean) {
//		textView.setTypeface(if (bold) Theme.TYPEFACE_BOLD else Theme.TYPEFACE_DEFAULT)
//	}

	fun setIsInDialogs() {
		inDialogs = true
	}

	fun setPrioritizeTitleOverValue(prioritizeTitleOverValue: Boolean) {
		this.prioritizeTitleOverValue = prioritizeTitleOverValue
		requestLayout()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val width = MeasureSpec.getSize(widthMeasureSpec)
		val height = AndroidUtilities.dp(if (large) largeHeight else 48f)

		if (prioritizeTitleOverValue) {
			textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp((71 + leftPadding).toFloat()), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
			valueTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp((103 + leftPadding).toFloat()) - textView.textWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
		}
		else {
			valueTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(leftPadding.toFloat()), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
			textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp((71 + leftPadding).toFloat()) - valueTextView.textWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
		}

		if (imageView.visibility == VISIBLE) {
			imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
		}

		if (valueImageView.visibility == VISIBLE) {
			valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
		}

		setMeasuredDimension(width, AndroidUtilities.dp(if (large) largeHeight else 50f) + if (needDivider) 1 else 0)
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val height = bottom - top
		val width = right - left
		var viewTop = (height - valueTextView.textHeight) / 2
		var viewLeft = if (LocaleController.isRTL) AndroidUtilities.dp(leftPadding.toFloat()) else 0

		if (prioritizeTitleOverValue && !LocaleController.isRTL) {
			viewLeft = width - valueTextView.measuredWidth - AndroidUtilities.dp(leftPadding.toFloat())
		}

		valueTextView.layout(viewLeft, viewTop, viewLeft + valueTextView.measuredWidth, viewTop + valueTextView.measuredHeight)

		viewTop = (height - textView.textHeight) / 2

		viewLeft = if (LocaleController.isRTL) {
			measuredWidth - textView.measuredWidth - AndroidUtilities.dp(if (imageView.visibility == VISIBLE) offsetFromImage.toFloat() else leftPadding.toFloat())
		}
		else {
			AndroidUtilities.dp(if (imageView.visibility == VISIBLE) offsetFromImage.toFloat() else leftPadding.toFloat())
		}

		textView.layout(viewLeft, viewTop, viewLeft + textView.measuredWidth, viewTop + textView.measuredHeight)

		if (imageView.visibility == VISIBLE) {
			viewTop = if (large) {
				(height - imageView.measuredHeight) / 2
			}
			else {
				AndroidUtilities.dp(5f)
			}

			viewLeft = if (!LocaleController.isRTL) {
				AndroidUtilities.dp(imageLeft.toFloat())
			}
			else {
				width - imageView.measuredWidth - AndroidUtilities.dp(imageLeft.toFloat())
			}

			imageView.layout(viewLeft, viewTop, viewLeft + imageView.measuredWidth, viewTop + imageView.measuredHeight)
		}

		if (valueImageView.visibility == VISIBLE) {
			viewTop = (height - valueImageView.measuredHeight) / 2

			viewLeft = if (LocaleController.isRTL) {
				AndroidUtilities.dp(23f)
			}
			else {
				width - valueImageView.measuredWidth - AndroidUtilities.dp(23f)
			}

			valueImageView.layout(viewLeft, viewTop, viewLeft + valueImageView.measuredWidth, viewTop + valueImageView.measuredHeight)
		}
	}

	fun setTextColor(color: Int) {
		textView.textColor = color
	}

	fun setColors(@ColorInt icon: Int?, @ColorInt text: Int?) {
		if (text != null) {
			textView.textColor = text
			// textView.tag = text
		}

		if (icon != null) {
			imageView.colorFilter = PorterDuffColorFilter(icon, PorterDuff.Mode.SRC_IN)
		}
	}

	fun setTextSize(size: Int) {
		textView.setTextSize(size)
	}

	fun setText(text: String?, divider: Boolean) {
		imageLeft = 21
		textView.setText(text)
		valueTextView.setText(null)
		imageView.visibility = GONE
		valueTextView.visibility = GONE
		valueImageView.visibility = GONE
		needDivider = divider
		setWillNotDraw(!needDivider)
	}

	fun setTextAndIcon(text: String?, resId: Int, divider: Boolean) {
		imageLeft = 21
		offsetFromImage = if (large) largeImageOffset else 71
		textView.setText(text)
		valueTextView.setText(null)
		imageView.setImageResource(resId)
		imageView.visibility = VISIBLE
		valueTextView.visibility = GONE
		valueImageView.visibility = GONE

		imageView.setPadding(0, if (large) 0 else AndroidUtilities.dp(7f), 0, 0)
		needDivider = divider
		setWillNotDraw(!needDivider)
	}

	fun setTextAndIcon(text: String?, drawable: Drawable?, divider: Boolean) {
		offsetFromImage = if (large) largeImageOffset else 68
		imageLeft = 18
		textView.setText(text)
		valueTextView.setText(null)
		imageView.colorFilter = null

		if (drawable is RLottieDrawable) {
			imageView.setAnimation(drawable)
		}
		else {
			imageView.setImageDrawable(drawable)
		}

		imageView.visible()
		valueTextView.gone()
		valueImageView.gone()
		imageView.setPadding(0, if (large) 0 else AndroidUtilities.dp(6f), 0, 0)
		needDivider = divider
		setWillNotDraw(!needDivider)
	}

	fun setOffsetFromImage(value: Int) {
		offsetFromImage = value
	}

	fun setTextAndValue(text: String?, value: String?, divider: Boolean) {
		imageLeft = 21
		offsetFromImage = 71
		textView.setText(text)
		valueTextView.setText(value)
		valueTextView.visible()
		imageView.gone()
		valueImageView.gone()
		needDivider = divider
		setWillNotDraw(!needDivider)
	}

	fun setTextAndValueAndIcon(text: String?, value: SpannedString?, resId: Int, divider: Boolean) {
		imageLeft = 21
		offsetFromImage = if (large) largeImageOffset else 71
		textView.setText(text)
		valueTextView.setText(value)
		valueTextView.visible()
		valueImageView.gone()
		imageView.visible()
		imageView.setPadding(0, if (large) 0 else AndroidUtilities.dp(7f), 0, 0)
		imageView.setImageResource(resId)
		needDivider = divider
		setWillNotDraw(!needDivider)
	}

	fun setTextAndValueAndIcon(text: String?, value: String?, resId: Int, divider: Boolean) {
		setTextAndValueAndIcon(text, value?.let { SpannedString(it) }, resId, divider)
	}

	fun setTextAndValueDrawable(text: String?, drawable: Drawable?, divider: Boolean) {
		imageLeft = 21
		offsetFromImage = if (large) largeImageOffset else 71
		textView.setText(text)
		valueTextView.setText(null)
		valueImageView.visible()
		valueImageView.setImageDrawable(drawable)
		valueTextView.gone()
		imageView.gone()
		imageView.setPadding(0, if (large) 0 else AndroidUtilities.dp(7f), 0, 0)
		needDivider = divider
		setWillNotDraw(!needDivider)
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			val left = if (fullDivider) {
				0f
			}
			else {
				if (LocaleController.isRTL) 0f else AndroidUtilities.dp(if (imageView.visibility == VISIBLE) (if (inDialogs) 72 else 21).toFloat() else 20.toFloat()).toFloat()
			}

			val right = if (fullDivider) {
				measuredWidth.toFloat()
			}
			else {
				(measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(if (imageView.visibility == VISIBLE) (if (inDialogs) 72 else 68).toFloat() else 20.toFloat()) else 0).toFloat()
			}

			val top = (measuredHeight - 1).toFloat()
			val bottom = (measuredHeight - 1).toFloat()

			canvas.drawLine(left, top, right, bottom, Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		val text = textView.getText()

		if (text.isNotEmpty()) {
			val valueText = valueTextView.getText()

			if (valueText.isNotEmpty()) {
				info.text = "$text: $valueText"
			}
			else {
				info.text = text
			}
		}

		info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
	}

	fun setNeedDivider(needDivider: Boolean) {
		if (this.needDivider != needDivider) {
			this.needDivider = needDivider
			setWillNotDraw(!needDivider)
			invalidate()
		}
	}

	companion object {
		private const val largeHeight = 64f
		private const val largeImageOffset = 58
	}
}
