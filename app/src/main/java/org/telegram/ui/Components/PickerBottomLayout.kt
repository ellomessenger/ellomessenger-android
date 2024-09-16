/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme

class PickerBottomLayout(context: Context) : FrameLayout(context) {
	@JvmField
	var doneButton: LinearLayout

	@JvmField
	var cancelButton: TextView

	@JvmField
	var doneButtonTextView: TextView

	@JvmField
	var doneButtonBadgeTextView: TextView

	init {
		setBackgroundColor(context.getColor(R.color.background))

		cancelButton = TextView(context)
		cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		cancelButton.setTextColor(context.getColor(R.color.brand))
		cancelButton.gravity = Gravity.CENTER
		cancelButton.background = Theme.createSelectorDrawable(context.getColor(R.color.brand) and 0x0fffffff, 0)
		cancelButton.setPadding(AndroidUtilities.dp(33f), 0, AndroidUtilities.dp(33f), 0)
		cancelButton.text = context.getString(R.string.Cancel).uppercase()
		cancelButton.typeface = Theme.TYPEFACE_BOLD

		addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		doneButton = LinearLayout(context)
		doneButton.orientation = LinearLayout.HORIZONTAL
		doneButton.background = Theme.createSelectorDrawable(context.getColor(R.color.brand) and 0x0fffffff, 0)
		doneButton.setPadding(AndroidUtilities.dp(33f), 0, AndroidUtilities.dp(33f), 0)

		addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.RIGHT))

		doneButtonBadgeTextView = TextView(context)
		doneButtonBadgeTextView.typeface = Theme.TYPEFACE_BOLD
		doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		doneButtonBadgeTextView.setTextColor(context.getColor(R.color.white))
		doneButtonBadgeTextView.gravity = Gravity.CENTER

		val drawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(11f), context.getColor(R.color.brand))

		doneButtonBadgeTextView.background = drawable
		doneButtonBadgeTextView.minWidth = AndroidUtilities.dp(23f)
		doneButtonBadgeTextView.setPadding(AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f), AndroidUtilities.dp(1f))

		doneButton.addView(doneButtonBadgeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 23, Gravity.CENTER_VERTICAL, 0, 0, 10, 0))

		doneButtonTextView = TextView(context)
		doneButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		doneButtonTextView.setTextColor(context.getColor(R.color.brand))
		doneButtonTextView.gravity = Gravity.CENTER
		doneButtonTextView.compoundDrawablePadding = AndroidUtilities.dp(8f)
		doneButtonTextView.text = context.getString(R.string.Send).uppercase()
		doneButtonTextView.typeface = Theme.TYPEFACE_BOLD

		doneButton.addView(doneButtonTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
	}

	fun updateSelectedCount(count: Int, disable: Boolean) {
		if (count == 0) {
			doneButtonBadgeTextView.visibility = GONE

			if (disable) {
				doneButtonTextView.setTextColor(context.getColor(R.color.dark_gray))
				doneButton.isEnabled = false
			}
			else {
				doneButtonTextView.setTextColor(context.getColor(R.color.brand))
			}
		}
		else {
			doneButtonBadgeTextView.visibility = VISIBLE
			doneButtonBadgeTextView.text = "$count"

			doneButtonTextView.setTextColor(context.getColor(R.color.brand))

			if (disable) {
				doneButton.isEnabled = true
			}
		}
	}
}
