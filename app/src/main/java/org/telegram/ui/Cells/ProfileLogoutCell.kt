/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import com.beint.elloapp.allCornersProvider
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class ProfileLogoutCell(context: Context) : FrameLayout(context) {
	private val textView: TextView

	init {
		textView = TextView(context)
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.gravity = Gravity.CENTER
		textView.text = context.getString(R.string.logout)

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.CENTER, 0f, 0f, 0f, 0f))

		val leftShapePathModel = ShapeAppearanceModel().toBuilder()
		val radius = context.resources.getDimensionPixelSize(R.dimen.common_size_15dp)

		leftShapePathModel.setTopLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
		leftShapePathModel.setTopRightCorner(CornerFamily.ROUNDED, radius.toFloat())
		leftShapePathModel.setBottomLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
		leftShapePathModel.setBottomRightCorner(CornerFamily.ROUNDED, radius.toFloat())

		val bg = MaterialShapeDrawable(leftShapePathModel.build())
		bg.fillColor = ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.background, null))
		bg.elevation = 0f

		background = bg

		outlineProvider = allCornersProvider(radius.toFloat())
		clipToOutline = true
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		updateLayoutParams<MarginLayoutParams> {
			leftMargin = AndroidUtilities.dp(16f)
			rightMargin = AndroidUtilities.dp(16f)
			bottomMargin = AndroidUtilities.dp(16f)
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(56f))
		val availableWidth = measuredWidth
		textView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
	}
}
