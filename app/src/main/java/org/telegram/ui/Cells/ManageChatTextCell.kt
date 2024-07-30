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
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme

class ManageChatTextCell(context: Context) : FrameLayout(context) {
	val textView: SimpleTextView
	val valueTextView: SimpleTextView
	private val imageView: ImageView
	private var divider = false
	private var dividerColor: Int? = null

	init {
		textView = SimpleTextView(context)
		textView.textColor = ResourcesCompat.getColor(resources, R.color.text, null)
		textView.setTextSize(16)
		textView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)

		addView(textView)

		valueTextView = SimpleTextView(context)
		valueTextView.textColor = ResourcesCompat.getColor(resources, R.color.text, null)
		valueTextView.setTextSize(16)
		valueTextView.setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)

		addView(valueTextView)

		imageView = ImageView(context)
		imageView.scaleType = ImageView.ScaleType.CENTER
		imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.text, null), PorterDuff.Mode.SRC_IN)

		addView(imageView)
	}

	fun setDividerColor(color: Int?) {
		dividerColor = color
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val width = MeasureSpec.getSize(widthMeasureSpec)
		val height = AndroidUtilities.dp(48f)
		valueTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(24f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
		textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp((71 + 24).toFloat()), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
		imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
		setMeasuredDimension(width, AndroidUtilities.dp(56f) + if (divider) 1 else 0)
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val height = bottom - top
		val width = right - left
		var viewTop = (height - valueTextView.textHeight) / 2
		var viewLeft = if (LocaleController.isRTL) AndroidUtilities.dp(24f) else 0
		valueTextView.layout(viewLeft, viewTop, viewLeft + valueTextView.measuredWidth, viewTop + valueTextView.measuredHeight)
		viewTop = (height - textView.textHeight) / 2
		viewLeft = if (!LocaleController.isRTL) AndroidUtilities.dp(71f) else AndroidUtilities.dp(24f)
		textView.layout(viewLeft, viewTop, viewLeft + textView.measuredWidth, viewTop + textView.measuredHeight)
		viewTop = AndroidUtilities.dp(9f)
		viewLeft = if (!LocaleController.isRTL) AndroidUtilities.dp(21f)
		else width - imageView.measuredWidth - AndroidUtilities.dp(21f)
		imageView.layout(viewLeft, viewTop, viewLeft + imageView.measuredWidth, viewTop + imageView.measuredHeight)
	}

	fun setTextColor(color: Int) {
		textView.textColor = color
	}

	fun setColors(icon: Int, text: Int) {
		textView.textColor = text
		imageView.colorFilter = PorterDuffColorFilter(icon, PorterDuff.Mode.SRC_IN)
	}

	fun setText(text: String?, value: String?, resId: Int, needDivider: Boolean) {
		setText(text, value, resId, 5, needDivider)
	}

	fun setText(text: String?, value: String?, resId: Int, paddingTop: Int, needDivider: Boolean) {
		textView.setText(text)

		if (value != null) {
			valueTextView.setText(value)
			valueTextView.visibility = VISIBLE
		}
		else {
			valueTextView.visibility = INVISIBLE
		}

		imageView.setPadding(0, AndroidUtilities.dp(paddingTop.toFloat()), 0, 0)
		imageView.setImageResource(resId)

		divider = needDivider

		setWillNotDraw(!divider)
	}

	override fun onDraw(canvas: Canvas) {
		if (divider) {
			dividerColor?.let {
				Theme.dividerExtraPaint.color = it
			}

			canvas.drawLine(AndroidUtilities.dp(71f).toFloat(), (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), if (dividerColor != null) Theme.dividerExtraPaint else Theme.dividerPaint)
		}
	}
}
