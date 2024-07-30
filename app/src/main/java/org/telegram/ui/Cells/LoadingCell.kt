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
import android.view.Gravity
import android.widget.FrameLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RadialProgressView

class LoadingCell @JvmOverloads constructor(context: Context, size: Int = AndroidUtilities.dp(40f), private val height: Int = AndroidUtilities.dp(54f)) : FrameLayout(context) {
	private val progressBar = RadialProgressView(context)

	init {
		progressBar.setSize(size)
		addView(progressBar, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
	}
}
