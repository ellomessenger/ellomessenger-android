/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.view_data

import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.ui.Charts.data.ChartData

class StackBarViewData(line: ChartData.Line) : LineViewData(line) {
	val unselectedPaint = Paint()
	var blendColor = 0

	override fun updateColors() {
		super.updateColors()
		blendColor = ColorUtils.blendARGB(ApplicationLoader.applicationContext.getColor(R.color.background), lineColor, 0.3f)
	}

	init {
		paint.strokeWidth = AndroidUtilities.dpf2(1f)
		paint.style = Paint.Style.STROKE
		unselectedPaint.style = Paint.Style.STROKE
		paint.isAntiAlias = false
	}
}
