/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.view_data

import org.telegram.messenger.AndroidUtilities
import kotlin.math.ceil
import kotlin.math.max

class ChartHorizontalLinesData(newMaxHeight: Int, newMinHeight: Int, useMinHeight: Boolean, k: Float = 0f) {
	var values: IntArray
	var valuesStr: Array<String?>
	var valuesStr2: Array<String?>? = null
	var alpha = 0
	var fixedAlpha = 255

	init {
		@Suppress("NAME_SHADOWING") var newMinHeight = newMinHeight

		if (!useMinHeight) {
			var v = newMaxHeight

			if (newMaxHeight > 100) {
				v = round(newMaxHeight)
			}

			val step = max(1, ceil((v / 5f).toDouble()).toInt())
			var n: Int

			if (v < 6) {
				n = max(2, v + 1)
			}
			else if (v / 2 < 6) {
				n = v / 2 + 1

				if (v % 2 != 0) {
					n++
				}
			}
			else {
				n = 6
			}

			values = IntArray(n)
			valuesStr = arrayOfNulls(n)

			for (i in 1 until n) {
				values[i] = i * step
				valuesStr[i] = AndroidUtilities.formatWholeNumber(values[i], 0)
			}
		}
		else {
			val n: Int
			val dif = newMaxHeight - newMinHeight
			var step: Float

			if (dif == 0) {
				newMinHeight--
				n = 3
				step = 1f
			}
			else if (dif < 6) {
				n = max(2, dif + 1)
				step = 1f
			}
			else if (dif / 2 < 6) {
				n = dif / 2 + dif % 2 + 1
				step = 2f
			}
			else {
				step = (newMaxHeight - newMinHeight) / 5f

				if (step <= 0) {
					step = 1f
					n = max(2, newMaxHeight - newMinHeight + 1)
				}
				else {
					n = 6
				}
			}

			values = IntArray(n)
			valuesStr = arrayOfNulls(n)

			if (k > 0) {
				valuesStr2 = arrayOfNulls(n)
			}

			val skipFloatValues = step / k < 1

			for (i in 0 until n) {
				values[i] = newMinHeight + (i * step).toInt()
				valuesStr[i] = AndroidUtilities.formatWholeNumber(values[i], dif)

				if (k > 0) {
					val v = values[i] / k

					if (skipFloatValues) {
						if (v - v.toInt() < 0.01f) {
							valuesStr2?.set(i, AndroidUtilities.formatWholeNumber(v.toInt(), (dif / k).toInt()))
						}
						else {
							valuesStr2?.set(i, "")
						}
					}
					else {
						valuesStr2?.set(i, AndroidUtilities.formatWholeNumber(v.toInt(), (dif / k).toInt()))
					}
				}
			}
		}
	}

	companion object {
		fun lookupHeight(maxValue: Int): Int {
			var v = maxValue

			if (maxValue > 100) {
				v = round(maxValue)
			}

			val step = ceil((v / 5f).toDouble()).toInt()

			return step * 5
		}

		private fun round(maxValue: Int): Int {
			val k = (maxValue / 5).toFloat()

			return if (k % 10 == 0f) {
				maxValue
			}
			else {
				(maxValue / 10 + 1) * 10
			}
		}
	}
}
