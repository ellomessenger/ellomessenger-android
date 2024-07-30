/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RadialProgressView

class ChatLoadingCell(context: Context, parent: View) : FrameLayout(context) {
	private val frameLayout: FrameLayout
	private val progressBar: RadialProgressView

	init {
		val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		backgroundPaint.color = ColorUtils.setAlphaComponent(context.getColor(R.color.brand), 178)

		frameLayout = FrameLayout(context)
		frameLayout.background = Theme.createServiceDrawable(AndroidUtilities.dp(18f), frameLayout, parent, backgroundPaint)

		addView(frameLayout, createFrame(36, 36, Gravity.CENTER))

		progressBar = RadialProgressView(context)
		progressBar.setSize(AndroidUtilities.dp(28f))
		// progressBar.setProgressColor(context.getColor(R.color.text))

		frameLayout.addView(progressBar, createFrame(32, 32, Gravity.CENTER))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44f), MeasureSpec.EXACTLY))
	}

	fun setProgressVisible(value: Boolean) {
		frameLayout.visibility = if (value) VISIBLE else INVISIBLE
	}
}
