/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.Components.LayoutHelper

class LocationPoweredCell(context: Context) : FrameLayout(context) {
	init {
		val linearLayout = LinearLayout(context)

		addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

		val textView = TextView(context)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
		textView.text = context.getString(R.string.powered_by)

		linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		val imageView = ImageView(context)
		imageView.setImageResource(R.drawable.foursquare)
		imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)
		imageView.setPadding(0, AndroidUtilities.dp(2f), 0, 0)

		linearLayout.addView(imageView, LayoutHelper.createLinear(35, LayoutHelper.WRAP_CONTENT))

		val textView2 = TextView(context)
		textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView2.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
		textView2.text = context.getString(R.string.foursquare)

		linearLayout.addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56f), MeasureSpec.EXACTLY))
	}
}
