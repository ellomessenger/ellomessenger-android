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
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class ChatUnreadCell(context: Context) : FrameLayout(context) {
	val textView: TextView
	val imageView: ImageView
	private val backgroundLayout: FrameLayout

	init {
		backgroundLayout = FrameLayout(context)
		backgroundLayout.setBackgroundResource(R.drawable.newmsg_divider)
		backgroundLayout.background.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.background, null), PorterDuff.Mode.SRC_IN)

		addView(backgroundLayout, createFrame(LayoutHelper.MATCH_PARENT, 27f, Gravity.LEFT or Gravity.TOP, 0f, 7f, 0f, 0f))

		imageView = ImageView(context)
		imageView.setImageResource(R.drawable.ic_ab_new)
		imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
		imageView.setPadding(0, AndroidUtilities.dp(2f), 0, 0)

		backgroundLayout.addView(imageView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.RIGHT or Gravity.CENTER_VERTICAL, 0f, 0f, 10f, 0f))

		textView = TextView(context)
		textView.setPadding(0, 0, 0, AndroidUtilities.dp(1f))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
		textView.typeface = Theme.TYPEFACE_BOLD

		addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
	}

	fun setText(text: String?) {
		textView.text = text
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40f), MeasureSpec.EXACTLY))
	}
}
