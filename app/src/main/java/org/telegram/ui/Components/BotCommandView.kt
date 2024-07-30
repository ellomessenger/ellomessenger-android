/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R

class BotCommandView(context: Context) : LinearLayout(context) {
	var commandTextView: TextView
	var description: TextView
	var command: String? = null

	init {
		orientation = HORIZONTAL

		setPadding(AndroidUtilities.dp(16f), 0, AndroidUtilities.dp(16f), 0)

		description = TextView(context)
		description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		description.setTextColor(context.getColor(R.color.text))
		description.setLines(1)
		description.ellipsize = TextUtils.TruncateAt.END

		addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, AndroidUtilities.dp(8f), 0))

		commandTextView = TextView(context)
		commandTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		commandTextView.setTextColor(context.getColor(R.color.dark_gray))

		addView(commandTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36f), MeasureSpec.EXACTLY))
	}
}
