/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.data

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.json.JSONObject
import org.telegram.messenger.SegmentTree
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

open class ChartData {
	var x = LongArray(0)
	var xPercentage = FloatArray(0)
	private var daysLookup: Array<String?>? = null
	var lines = ArrayList<Line>()
	var maxValue = 0
	var minValue = Int.MAX_VALUE
	var oneDayPercentage = 0f
	protected var timeStep: Long = 0

	protected constructor()

	constructor(jsonObject: JSONObject) {
		val columns = jsonObject.getJSONArray("columns")

		for (i in 0 until columns.length()) {
			val a = columns.getJSONArray(i)

			if (a.getString(0) == "x") {
				val len = a.length() - 1

				x = LongArray(len)

				for (j in 0 until len) {
					x[j] = a.getLong(j + 1)
				}
			}
			else {
				val l = Line()

				lines.add(l)

				val len = a.length() - 1

				l.id = a.getString(0)
				l.y = IntArray(len)

				for (j in 0 until len) {
					l.y[j] = a.getInt(j + 1)

					if (l.y[j] > l.maxValue) {
						l.maxValue = l.y[j]
					}

					if (l.y[j] < l.minValue) {
						l.minValue = l.y[j]
					}
				}
			}

			timeStep = if (x.size > 1) {
				x[1] - x[0]
			}
			else {
				86400000L
			}
		}

		measure()

		val colors = jsonObject.optJSONObject("colors")
		val names = jsonObject.optJSONObject("names")
		val colorPattern = Pattern.compile("(.*)(#.*)")

		for (line in lines) {
			val id = line.id ?: continue

			if (colors != null) {
				val matcher = colorPattern.matcher(colors.getString(id))

				if (matcher.matches()) {
					line.color = Color.parseColor(matcher.group(2))
					line.colorDark = ColorUtils.blendARGB(Color.WHITE, line.color, 0.85f)
				}
			}

			if (names != null) {
				line.name = names.getString(id)
			}
		}
	}

	protected open fun measure() {
		val n = x.size

		if (n == 0) {
			return
		}

		val start = x[0]
		val end = x[n - 1]

		xPercentage = FloatArray(n)

		if (n == 1) {
			xPercentage[0] = 1f
		}
		else {
			for (i in 0 until n) {
				xPercentage[i] = (x[i] - start).toFloat() / (end - start).toFloat()
			}
		}

		for (i in lines.indices) {
			if (lines[i].maxValue > maxValue) {
				maxValue = lines[i].maxValue
			}

			if (lines[i].minValue < minValue) {
				minValue = lines[i].minValue
			}

			lines[i].segmentTree = SegmentTree(lines[i].y)
		}

		daysLookup = arrayOfNulls(((end - start) / timeStep).toInt() + 10)

		val formatter = if (timeStep == 1L) {
			null
		}
		else if (timeStep < 86400000L) {
			SimpleDateFormat("HH:mm", Locale.getDefault())
		}
		else {
			SimpleDateFormat("MMM d", Locale.getDefault())
		}

		for (i in daysLookup!!.indices) {
			if (timeStep == 1L) {
				daysLookup!![i] = String.format(Locale.ENGLISH, "%02d:00", i)
			}
			else {
				daysLookup!![i] = formatter!!.format(Date(start + i * timeStep))
			}
		}

		oneDayPercentage = timeStep / (x[x.size - 1] - x[0]).toFloat()
	}

	fun getDayString(i: Int): String? {
		return daysLookup?.getOrNull(((x[i] - x[0]) / timeStep).toInt())
	}

	fun findStartIndex(v: Float): Int {
		if (v == 0f) {
			return 0
		}

		val n = xPercentage.size

		if (n < 2) {
			return 0
		}

		var left = 0
		var right = n - 1

		while (left <= right) {
			val middle = right + left shr 1

			if (v < xPercentage[middle] && (middle == 0 || v > xPercentage[middle - 1])) {
				return middle
			}

			if (v == xPercentage[middle]) {
				return middle
			}

			if (v < xPercentage[middle]) {
				right = middle - 1
			}
			else if (v > xPercentage[middle]) {
				left = middle + 1
			}
		}

		return left
	}

	fun findEndIndex(left: Int, v: Float): Int {
		@Suppress("NAME_SHADOWING") var left = left

		val n = xPercentage.size

		if (v == 1f) {
			return n - 1
		}

		var right = n - 1

		while (left <= right) {
			val middle = right + left shr 1

			if (v > xPercentage[middle] && (middle == n - 1 || v < xPercentage[middle + 1])) {
				return middle
			}

			if (v == xPercentage[middle]) {
				return middle
			}

			if (v < xPercentage[middle]) {
				right = middle - 1
			}
			else if (v > xPercentage[middle]) {
				left = middle + 1
			}
		}

		return right
	}

	fun findIndex(left: Int, right: Int, v: Float): Int {
		@Suppress("NAME_SHADOWING") var left = left
		@Suppress("NAME_SHADOWING") var right = right
		val n = xPercentage.size

		if (v <= xPercentage[left]) {
			return left
		}

		if (v >= xPercentage[right]) {
			return right
		}

		while (left <= right) {
			val middle = right + left shr 1

			if (v > xPercentage[middle] && (middle == n - 1 || v < xPercentage[middle + 1])) {
				return middle
			}

			if (v == xPercentage[middle]) {
				return middle
			}

			if (v < xPercentage[middle]) {
				right = middle - 1
			}
			else if (v > xPercentage[middle]) {
				left = middle + 1
			}
		}

		return right
	}

	class Line {
		var y = IntArray(0)
		var segmentTree: SegmentTree? = null
		var id: String? = null
		var name: String? = null
		var maxValue = 0
		var minValue = Int.MAX_VALUE
		var color = Color.BLACK
		var colorDark = Color.WHITE
	}
}
