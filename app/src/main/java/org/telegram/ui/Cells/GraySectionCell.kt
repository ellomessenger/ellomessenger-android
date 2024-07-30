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
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedTextView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class GraySectionCell(context: Context) : FrameLayout(context) {
	val textView: TextView
	private val rightTextView: AnimatedTextView

	init {
		setBackgroundColor(ResourcesCompat.getColor(resources, R.color.light_background, null))

		textView = TextView(context)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 16f, 0f, 16f, 0f))

		rightTextView = object : AnimatedTextView(context, true, true, true) {
			override fun getAccessibilityClassName(): CharSequence {
				return Button::class.java.name
			}
		}

		rightTextView.setTypeface(Theme.TYPEFACE_BOLD)
		rightTextView.setPadding(AndroidUtilities.dp(2f), 0, AndroidUtilities.dp(2f), 0)
		rightTextView.setAnimationProperties(1f, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT)
		rightTextView.setTextSize(AndroidUtilities.dp(14f).toFloat())
		rightTextView.setTextColor(ResourcesCompat.getColor(resources, R.color.brand, null))
		rightTextView.setGravity((if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL)

		addView(rightTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP, 16f, 0f, 16f, 0f))

		ViewCompat.setAccessibilityHeading(this, true)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40f), MeasureSpec.EXACTLY))
	}

	val text: CharSequence?
		get() = textView.text

	@JvmOverloads
	fun setText(left: String? = null, right: String? = null, onClickListener: OnClickListener? = null) {
		textView.text = left

		if (right.isNullOrEmpty()) {
			rightTextView.gone()
			rightTextView.setOnClickListener(null)
			return
		}
		else {
			rightTextView.visible()
			rightTextView.setText(right, false)
			rightTextView.setOnClickListener(onClickListener)
		}
	}

	@JvmOverloads
	fun setRightText(right: String?, moveDown: Boolean = true) {
		rightTextView.setText(right, true, moveDown)
		rightTextView.visibility = VISIBLE
	}
}
