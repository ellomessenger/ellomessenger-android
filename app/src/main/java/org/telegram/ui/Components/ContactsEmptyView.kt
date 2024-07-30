/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createLinear

class ContactsEmptyView(context: Context) : FrameLayout(context) {
	private val imageView: RLottieImageView
	private val titleTextView: TextView
	private val subTitleTextView: TextView

	init {
		val layout = LinearLayout(context)
		layout.orientation = LinearLayout.VERTICAL

		imageView = RLottieImageView(context)
		imageView.setAutoRepeat(true)
		imageView.setAnimation(R.raw.panda_no_contacts, 160, 160)

		layout.addView(imageView, createLinear(160, 160, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 12, 0, 12, 0))

		imageView.playAnimation()

		titleTextView = TextView(context)
		titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
		titleTextView.typeface = Theme.TYPEFACE_BOLD
		titleTextView.setTextColor(context.getColor(R.color.text))
		titleTextView.gravity = Gravity.CENTER_HORIZONTAL
		titleTextView.text = context.getString(R.string.NoContactsYet)

		layout.addView(titleTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 12, 16, 12, 0))

		subTitleTextView = TextView(context)
		subTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		subTitleTextView.typeface = Theme.TYPEFACE_DEFAULT
		subTitleTextView.setTextColor(context.getColor(R.color.dark_gray))
		subTitleTextView.setText(R.string.NoContactsYetDescription)
		layout.addView(subTitleTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 12, 8, 12, 0))

		addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
	}
}
