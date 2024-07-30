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
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme

class CreationTextCell(context: Context) : FrameLayout(context) {
	private val textView: SimpleTextView
	private val imageView: ImageView
	var divider = false

	@JvmField
	var startPadding = 70

	init {
		textView = SimpleTextView(context)
		textView.setTextSize(16)
		textView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		textView.textColor = ResourcesCompat.getColor(context.resources, R.color.brand, null)
		addView(textView)
		imageView = ImageView(context)
		imageView.scaleType = ImageView.ScaleType.CENTER
		addView(imageView)
		setWillNotDraw(false)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val width = MeasureSpec.getSize(widthMeasureSpec)
		// val height = AndroidUtilities.dp(48f)
		textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp((71 + 23).toFloat()), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
		imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50f), MeasureSpec.EXACTLY))
		setMeasuredDimension(width, AndroidUtilities.dp(50f))
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val height = bottom - top
		val width = right - left
		val viewTop = (height - textView.textHeight) / 2

		var viewLeft = if (LocaleController.isRTL) {
			measuredWidth - textView.measuredWidth - AndroidUtilities.dp((if (imageView.visibility == VISIBLE) startPadding else 25).toFloat())
		}
		else {
			AndroidUtilities.dp((if (imageView.visibility == VISIBLE) startPadding else 25).toFloat())
		}

		textView.layout(viewLeft, viewTop, viewLeft + textView.measuredWidth, viewTop + textView.measuredHeight)

		viewLeft = if (!LocaleController.isRTL) (AndroidUtilities.dp(startPadding.toFloat()) - imageView.measuredWidth) / 2 else width - imageView.measuredWidth - AndroidUtilities.dp(25f)

		imageView.layout(viewLeft, 0, viewLeft + imageView.measuredWidth, imageView.measuredHeight)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		if (divider) {
			canvas.drawLine(AndroidUtilities.dp(startPadding.toFloat()).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth + AndroidUtilities.dp(23f)).toFloat(), measuredHeight.toFloat(), Theme.dividerPaint)
		}
	}

	fun setTextAndIcon(text: String?, icon: Drawable?, divider: Boolean) {
		textView.setText(text)
		imageView.setImageDrawable(icon)
		this.divider = divider
	}
}
