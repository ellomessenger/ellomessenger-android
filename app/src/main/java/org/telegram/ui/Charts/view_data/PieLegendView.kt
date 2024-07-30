/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.view_data

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.ui.ActionBar.Theme

class PieLegendView(context: Context) : LegendSignatureView(context) {
	private val signature = TextView(context)
	private val value = TextView(context)

	init {
		val root = LinearLayout(getContext())
		root.setPadding(AndroidUtilities.dp(4f), AndroidUtilities.dp(2f), AndroidUtilities.dp(4f), AndroidUtilities.dp(2f))
		root.addView(signature)

		signature.layoutParams.width = AndroidUtilities.dp(96f)

		root.addView(value)

		addView(root)

		value.typeface = Theme.TYPEFACE_BOLD

		setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f))

		chevron.gone()

		zoomEnabled = false
	}

	override fun recolor() {
		super.recolor()
		signature.setTextColor(context.getColor(R.color.text))
	}

	fun setData(name: String?, value: Int, color: Int) {
		signature.text = name
		this.value.text = value.toString()
		this.value.setTextColor(color)
	}

	override fun setSize(n: Int) {
		// unused
	}
}
