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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CombinedDrawable

class ShadowSectionCell @JvmOverloads constructor(context: Context, private var size: Int = 12, backgroundColor: Int? = null) : View(context) {
	init {
		if (backgroundColor == null) {
			background = Theme.getThemedDrawable(context, R.drawable.greydivider, context.getColor(R.color.shadow))
		}
		else {
			val shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider, context.getColor(R.color.shadow))
			val background: Drawable = ColorDrawable(backgroundColor)
			val combinedDrawable = CombinedDrawable(background, shadowDrawable, 0, 0)
			combinedDrawable.setFullSize(true)
			setBackground(combinedDrawable)
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(size.toFloat()), MeasureSpec.EXACTLY))
	}
}
