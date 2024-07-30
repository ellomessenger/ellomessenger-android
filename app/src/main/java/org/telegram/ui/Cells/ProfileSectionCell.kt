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
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.view.setPadding
import com.beint.elloapp.allCornersProvider
import com.beint.elloapp.bottomCornersProvider
import com.beint.elloapp.topCornersProvider
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class ProfileSectionCell @JvmOverloads constructor(context: Context, iconSize: Int = DEFAULT_ICON_SIZE, private val cellHeight: Float = DEFAULT_HEIGHT) : FrameLayout(context) {
	private val textView: TextView
	private val valueView: TextView
	private val imageView: ImageView
	private val padding = 16
	private var shouldDrawDivider = true

	init {
		textView = TextView(context)
		textView.setTextColor(context.getColor(R.color.text))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.typeface = Theme.TYPEFACE_DEFAULT
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

		addView(textView, createFrame(175, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 0f else padding.toFloat() + 48, 0f, if (LocaleController.isRTL) padding.toFloat() + 48 else 0f, 0f))

		imageView = ImageView(context)
		imageView.setPadding(DEFAULT_ICON_SIZE - iconSize)

		addView(imageView, createFrame(iconSize, iconSize.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) 0 else padding).toFloat(), 0f, (if (LocaleController.isRTL) padding else 0).toFloat(), 0f))

		valueView = TextView(context)
		valueView.setTextColor(context.getColor(R.color.text))
		valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		valueView.setLines(1)
		valueView.maxLines = 1
		valueView.isSingleLine = true
		valueView.ellipsize = TextUtils.TruncateAt.END
		valueView.typeface = Theme.TYPEFACE_BOLD
		valueView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

		addView(valueView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) padding.toFloat() else 0f, 0f, if (LocaleController.isRTL) 0f else padding.toFloat(), 0f))
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
			bg.fillColor = ColorStateList.valueOf(context.getColor(R.color.background))
			bg.elevation = 0f

			background = bg

			outlineProvider = if (roundTop && roundBottom) {
				allCornersProvider(radius.toFloat())
			}
			else if (roundTop) {
				topCornersProvider(radius.toFloat())
			}
			else {
				bottomCornersProvider(radius.toFloat())
			}

			clipToOutline = true
		}
		else {
			setBackgroundResource(R.color.background)
			outlineProvider = ViewOutlineProvider.BACKGROUND
			clipToOutline = false
		}

		shouldDrawDivider = !roundBottom
	}

	fun setShouldDrawDivider(shouldDraw: Boolean) {
		shouldDrawDivider = shouldDraw
	}

	fun setIconColor(@ColorInt color: Int) {
		imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(cellHeight))

		imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(DEFAULT_ICON_SIZE.toFloat()), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(DEFAULT_ICON_SIZE.toFloat()), MeasureSpec.EXACTLY))

		val availableWidth = measuredWidth - paddingLeft - paddingRight - AndroidUtilities.dp(34f)

		textView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
		valueView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
	}

	fun setTextValueIcon(text: String, value: Int, @DrawableRes icon: Int) {
		textView.text = text

		if (value != NO_VALUE) {
			valueView.text = value.toString()
			valueView.visible()
		}
		else {
			valueView.gone()
			valueView.text = null
		}

		imageView.setImageResource(icon)
		setWillNotDraw(false)
	}

	override fun onDraw(canvas: Canvas) {
		if (shouldDrawDivider) {
			canvas.drawLine(AndroidUtilities.dp(padding.toFloat()).toFloat(), (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	companion object {
		const val NO_VALUE = Int.MIN_VALUE
		private const val DEFAULT_ICON_SIZE = 32
		private const val DEFAULT_HEIGHT = 60f
	}
}
