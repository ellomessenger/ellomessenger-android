/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts

import android.animation.Animator
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.view_data.StackLinearViewData

class PieChartViewData(line: ChartData.Line) : StackLinearViewData(line) {
	var selectionA = 0f
	var drawingPart = 0f
	var animator: Animator? = null
}
