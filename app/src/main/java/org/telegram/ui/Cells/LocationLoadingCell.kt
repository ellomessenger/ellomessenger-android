/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView

class LocationLoadingCell(context: Context) : FrameLayout(context) {
	private val progressBar = RadialProgressView(context)
	private val textView = TextView(context)
	private val imageView = ImageView(context)

	init {
		addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

		imageView.setImageResource(R.drawable.location_empty)
		imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.light_background, null), PorterDuff.Mode.SRC_IN)

		addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 0f, 0f, 24f))

		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		textView.gravity = Gravity.CENTER
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
		textView.text = context.getString(R.string.NoPlacesFound)

		addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 34f, 0f, 0f))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((AndroidUtilities.dp(56f) * 2.5f).toInt(), MeasureSpec.EXACTLY))
	}

	fun setLoading(value: Boolean) {
		if (value) {
			progressBar.visible()
			textView.invisible()
			imageView.invisible()
		}
		else {
			progressBar.invisible()
			textView.visible()
			imageView.visible()
		}
	}
}
