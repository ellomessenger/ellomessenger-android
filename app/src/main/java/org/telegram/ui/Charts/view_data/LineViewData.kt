/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.view_data

import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.ui.Charts.BaseChartView
import org.telegram.ui.Charts.data.ChartData

open class LineViewData(val line: ChartData.Line) {
	val bottomLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	val bottomLinePath = Path()
	val chartPath = Path()
	val chartPathPicker = Path()
	var animatorIn: ValueAnimator? = null
	var animatorOut: ValueAnimator? = null
	var linesPathBottomSize = 0
	var linesPath: FloatArray
	var linesPathBottom: FloatArray
	var lineColor = 0
	var enabled = true
	var alpha = 1f

	init {
		paint.strokeWidth = AndroidUtilities.dpf2(2f)
		paint.style = Paint.Style.STROKE

		if (!BaseChartView.USE_LINES) {
			paint.strokeJoin = Paint.Join.ROUND
		}

		paint.color = line.color

		bottomLinePaint.strokeWidth = AndroidUtilities.dpf2(1f)
		bottomLinePaint.style = Paint.Style.STROKE
		bottomLinePaint.color = line.color

		selectionPaint.strokeWidth = AndroidUtilities.dpf2(10f)
		selectionPaint.style = Paint.Style.STROKE
		selectionPaint.strokeCap = Paint.Cap.ROUND
		selectionPaint.color = line.color

		linesPath = FloatArray(line.y.size shl 2)
		linesPathBottom = FloatArray(line.y.size shl 2)
	}

	open fun updateColors() {
		val color = ApplicationLoader.applicationContext.getColor(R.color.background)
		val darkBackground = ColorUtils.calculateLuminance(color) < 0.5f

		lineColor = if (darkBackground) line.colorDark else line.color
		paint.color = lineColor
		bottomLinePaint.color = lineColor
		selectionPaint.color = lineColor
	}
}

