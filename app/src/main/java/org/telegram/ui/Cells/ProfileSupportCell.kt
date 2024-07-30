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
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.beint.elloapp.allCornersProvider
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class ProfileSupportCell(context: Context) : FrameLayout(context) {
	private val textView: TextView
	private val imageView: ImageView
	private val padding = 16

	init {
		textView = TextView(context)
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.typeface = Theme.TYPEFACE_DEFAULT
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
		textView.text = context.getString(R.string.Support)

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 0f else padding.toFloat() + 40, 0f, if (LocaleController.isRTL) padding.toFloat() + 40 else 0f, 0f))

		val drawable = ResourcesCompat.getDrawable(resources, R.drawable.support, null)
		drawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.brand, null), android.graphics.PorterDuff.Mode.SRC_IN)

		imageView = ImageView(context)
		imageView.setImageDrawable(drawable)

		addView(imageView, createFrame(24, 24f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) 0 else padding).toFloat(), 0f, (if (LocaleController.isRTL) padding else 0).toFloat(), 0f))

		val leftShapePathModel = ShapeAppearanceModel().toBuilder()

		val bg = MaterialShapeDrawable(leftShapePathModel.build())
		bg.fillColor = ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.background, null))
		bg.elevation = 0f

		background = bg

		outlineProvider = allCornersProvider(0f)
		clipToOutline = true
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(48f))
		val availableWidth = measuredWidth - paddingLeft - paddingRight - AndroidUtilities.dp(34f)
		imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY))
		textView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
	}
}
