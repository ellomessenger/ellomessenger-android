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
import java.util.Arrays
import kotlin.math.max
import kotlin.math.roundToInt

class StackLinearChartData : ChartData {
	private var ySum: IntArray? = null
	private var ySumSegmentTree: SegmentTree? = null
	var simplifiedY: Array<IntArray>? = null
	var simplifiedSize = 0

	constructor(jsonObject: JSONObject, isLanguages: Boolean) : super(jsonObject) {
		if (isLanguages) {
			val totalCount = LongArray(lines.size)
			val emptyCount = IntArray(lines.size)
			var total: Long = 0

			for (k in lines.indices) {
				val n = x.size

				for (i in 0 until n) {
					val v = lines[k].y[i]

					totalCount[k] += v.toLong()

					if (v == 0) {
						emptyCount[k]++
					}
				}

				total += totalCount[k]
			}

			val removed = mutableListOf<Line>()

			for (k in lines.indices) {
				if (totalCount[k] / total.toDouble() < 0.01 && emptyCount[k] > x.size / 2f) {
					removed.add(lines[k])
				}
			}

			for (r in removed) {
				lines.remove(r)
			}
		}

		val n = lines[0].y.size
		val k = lines.size

		val ySum = IntArray(n).also {
			this.ySum = it
		}

		for (i in 0 until n) {
			ySum[i] = 0

			for (j in 0 until k) {
				ySum[i] += lines[j].y[i]
			}
		}

		ySumSegmentTree = SegmentTree(ySum)
	}

	constructor(data: ChartData, d: Long) {
		val index = Arrays.binarySearch(data.x, d)
		var startIndex = index - 4
		var endIndex = index + 4

		if (startIndex < 0) {
			endIndex += -startIndex
			startIndex = 0
		}

		if (endIndex > data.x.size - 1) {
			startIndex -= endIndex - data.x.size
			endIndex = data.x.size - 1
		}

		if (startIndex < 0) {
			startIndex = 0
		}

		val n = endIndex - startIndex + 1
		x = LongArray(n)
		xPercentage = FloatArray(n)
		lines = ArrayList()

		for (i in data.lines.indices) {
			val line = Line()
			line.y = IntArray(n)
			line.id = data.lines[i].id
			line.name = data.lines[i].name
			line.color = data.lines[i].color
			line.colorDark = data.lines[i].colorDark
			lines.add(line)
		}

		for ((i, j) in (startIndex..endIndex).withIndex()) {
			x[i] = data.x[j]

			for (k in lines.indices) {
				val line = lines[k]
				line.y[i] = data.lines[k].y[j]
			}
		}

		timeStep = 86400000L

		measure()
	}

	override fun measure() {
		super.measure()

		simplifiedSize = 0

		val n = xPercentage.size
		val nl = lines.size
		val step = max(1, (n / 140f).roundToInt())
		val maxSize = n / step

		val simplifiedY = Array(nl) { IntArray(maxSize) }.also {
			this.simplifiedY = it
		}

		val max = IntArray(nl)

		for (i in 0 until n) {
			for (k in 0 until nl) {
				val line = lines[k]

				if (line.y[i] > max[k]) {
					max[k] = line.y[i]
				}
			}

			if (i % step == 0) {
				for (k in 0 until nl) {
					simplifiedY[k][simplifiedSize] = max[k]
					max[k] = 0
				}

				simplifiedSize++

				if (simplifiedSize >= maxSize) {
					break
				}
			}
		}
	}
}
