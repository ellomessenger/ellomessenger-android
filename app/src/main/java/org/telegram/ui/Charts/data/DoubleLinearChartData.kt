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

class DoubleLinearChartData(jsonObject: JSONObject) : ChartData(jsonObject) {
	var linesK: FloatArray? = null
		private set

	override fun measure() {
		super.measure()

		val n = lines.size
		var max = 0

		for (i in 0 until n) {
			val m = lines[i].maxValue
			if (m > max) max = m
		}

		val linesK = FloatArray(n).also {
			this.linesK = it
		}

		for (i in 0 until n) {
			val m = lines[i].maxValue

			if (max == m) {
				linesK[i] = 1f
				continue
			}

			linesK[i] = (max / m).toFloat()
		}
	}
}
