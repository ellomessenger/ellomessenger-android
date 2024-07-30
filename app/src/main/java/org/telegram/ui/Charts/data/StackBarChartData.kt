/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.data

import org.json.JSONObject
import org.telegram.messenger.SegmentTree

class StackBarChartData(jsonObject: JSONObject) : ChartData(jsonObject) {
	var ySum: IntArray
	var ySumSegmentTree: SegmentTree

	init {
		val n = lines[0].y.size
		val k = lines.size

		ySum = IntArray(n)

		for (i in 0 until n) {
			ySum[i] = 0

			for (j in 0 until k) {
				ySum[i] += lines[j].y[i]
			}
		}

		ySumSegmentTree = SegmentTree(ySum)
	}

	fun findMax(start: Int, end: Int): Int {
		return ySumSegmentTree.rMaxQ(start, end)
	}
}
