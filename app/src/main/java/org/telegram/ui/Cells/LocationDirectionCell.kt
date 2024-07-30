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
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class LocationDirectionCell(context: Context) : FrameLayout(context) {
	private val frameLayout = FrameLayout(context)

	init {
		frameLayout.background = Theme.AdaptiveRipple.filledRect(ResourcesCompat.getColor(resources, R.color.brand, null), 4f)

		addView(frameLayout, createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.LEFT or Gravity.TOP, 16f, 10f, 16f, 0f))

		val buttonTextView = SimpleTextView(context)
		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.setGravity(Gravity.CENTER)
		buttonTextView.setDrawablePadding(AndroidUtilities.dp(8f))
		buttonTextView.textColor = ResourcesCompat.getColor(resources, R.color.white, null)
		buttonTextView.setTextSize(14)
		buttonTextView.setText(context.getString(R.string.Directions))
		buttonTextView.setLeftDrawable(R.drawable.navigate)
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD)

		frameLayout.addView(buttonTextView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(73f), MeasureSpec.EXACTLY))
	}

	fun setOnButtonClick(onButtonClick: OnClickListener?) {
		frameLayout.setOnClickListener(onButtonClick)
	}
}
