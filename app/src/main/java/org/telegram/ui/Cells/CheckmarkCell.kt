/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class CheckmarkCell @JvmOverloads constructor(context: Context, padding: Int = 16) : FrameLayout(context) {
	private val textView: TextView
	private val checkmark: ImageView
	private var needDivider = false

	init {
		textView = TextView(context)
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, padding.toFloat(), 0f, padding.toFloat(), 0f))

		checkmark = ImageView(context)
		checkmark.setImageResource(R.drawable.tick_circle)

		addView(checkmark, createFrame(20, 20f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) padding + 3f else 0).toFloat(), 0f, (if (LocaleController.isRTL) 0 else padding + 3f).toFloat(), 0f))
	}

	fun setRoundedType(roundTop: Boolean, roundBottom: Boolean) {
		if (roundTop || roundBottom) {
			val leftShapePathModel = ShapeAppearanceModel().toBuilder()
			val radius = context.resources.getDimensionPixelSize(R.dimen.common_size_15dp)

			if (roundTop) {
				leftShapePathModel.setTopLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
				leftShapePathModel.setTopRightCorner(CornerFamily.ROUNDED, radius.toFloat())
			}

			if (roundBottom) {
				leftShapePathModel.setBottomLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
				leftShapePathModel.setBottomRightCorner(CornerFamily.ROUNDED, radius.toFloat())
			}

			val bg = MaterialShapeDrawable(leftShapePathModel.build())
			bg.fillColor = ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.background, null))
			bg.elevation = 0f

			background = bg
		}
		else {
			setBackgroundResource(R.color.background)
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		updateLayoutParams<MarginLayoutParams> {
			leftMargin = AndroidUtilities.dp(16f)
			rightMargin = AndroidUtilities.dp(16f)
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(56f))
		val availableWidth = measuredWidth - paddingLeft - paddingRight - AndroidUtilities.dp(34f)
		checkmark.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
		textView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
	}

	fun setTextColor(color: Int) {
		textView.setTextColor(color)
	}

	fun setText(text: String?, checked: Boolean, divider: Boolean) {
		textView.text = text

		if (checked) {
			checkmark.visible()
		}
		else {
			checkmark.invisible()
		}

		needDivider = divider

		setWillNotDraw(!divider)
	}

	val isChecked: Boolean
		get() = checkmark.isVisible

	fun setChecked(checked: Boolean, animated: Boolean) {
		if (checked) {
			if (animated) {
				checkmark.alpha = 0f
				checkmark.visible()

				val animator = ObjectAnimator.ofFloat(checkmark, "alpha", 0f, 1f)
				animator.duration = 200
				animator.start()
			}
			else {
				checkmark.alpha = 1f
				checkmark.visible()
			}
		}
		else {
			if (animated) {
				val animator = ObjectAnimator.ofFloat(checkmark, "alpha", 1f, 0f)
				animator.duration = 200

				animator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						checkmark.invisible()
						checkmark.alpha = 1f
					}
				})

				animator.start()
			}
			else {
				checkmark.invisible()
				checkmark.alpha = 1f
			}
		}
	}

	fun setEnabled(value: Boolean, animators: ArrayList<Animator>?) {
		super.setEnabled(value)

		if (animators != null) {
			animators.add(ObjectAnimator.ofFloat(textView, ALPHA, if (value) 1.0f else 0.5f))
			animators.add(ObjectAnimator.ofFloat(checkmark, ALPHA, if (value) 1.0f else 0.5f))
		}
		else {
			textView.alpha = if (value) 1.0f else 0.5f
			checkmark.alpha = if (value) 1.0f else 0.5f
		}
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			canvas.drawLine(0f, (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.RadioButton"
		info.isCheckable = true
		info.isChecked = isChecked
	}
}
