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
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R

class DividerCell @JvmOverloads constructor(context: Context, padding: Float = 8f, private val shouldDraw: Boolean = true) : View(context) {
	private val paint = Paint()

	init {
		setPadding(0, AndroidUtilities.dp(padding), 0, AndroidUtilities.dp(padding))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), paddingTop + paddingBottom + (if (shouldDraw) 1 else 0))
	}

	override fun onDraw(canvas: Canvas) {
		if (shouldDraw) {
			paint.color = ResourcesCompat.getColor(context.resources, R.color.divider, null)
			canvas.drawLine(paddingLeft.toFloat(), paddingTop.toFloat(), (width - paddingRight).toFloat(), paddingTop.toFloat(), paint)
		}
	}
}
