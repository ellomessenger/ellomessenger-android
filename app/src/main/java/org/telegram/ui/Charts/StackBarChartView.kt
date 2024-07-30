/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts

import android.content.Context
import android.graphics.Canvas
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.SegmentTree
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.data.StackBarChartData
import org.telegram.ui.Charts.view_data.LineViewData
import org.telegram.ui.Charts.view_data.StackBarViewData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class StackBarChartView(context: Context) : BaseChartView<StackBarChartData, StackBarViewData>(context) {
	private var yMaxPoints: IntArray? = null

	init {
		superDraw = true
		useAlphaSignature = true
	}

	override fun createLineViewData(line: ChartData.Line): StackBarViewData {
		return StackBarViewData(line)
	}

	override fun drawChart(canvas: Canvas) {
		val chartData = chartData ?: return
		val fullWidth = chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart)
		val offset = fullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING
		val p: Float
		val lineWidth: Float

		if (chartData.xPercentage.size < 2) {
			p = 1f
			lineWidth = 1f
		}
		else {
			p = chartData.xPercentage[1] * fullWidth
			lineWidth = chartData.xPercentage[1] * (fullWidth - p)
		}

		val additionalPoints = (HORIZONTAL_PADDING / p).toInt() + 1
		val localStart = max(0, startXIndex - additionalPoints - 2)
		val localEnd = min(chartData.xPercentage.size - 1, endXIndex + additionalPoints + 2)

		for (k in lines.indices) {
			val line: LineViewData = lines[k]
			line.linesPathBottomSize = 0
		}

		var transitionAlpha = 1f

		canvas.save()

		when (transitionMode) {
			TRANSITION_MODE_PARENT -> {
				postTransition = true
				selectionA = 0f
				transitionAlpha = 1f - transitionParams!!.progress
				canvas.scale(1 + 2 * transitionParams!!.progress, 1f, transitionParams!!.pX, transitionParams!!.pY)
			}

			TRANSITION_MODE_CHILD -> {
				transitionAlpha = transitionParams!!.progress
				canvas.scale(transitionParams!!.progress, 1f, transitionParams!!.pX, transitionParams!!.pY)
			}

			TRANSITION_MODE_ALPHA_ENTER -> {
				transitionAlpha = transitionParams!!.progress
			}
		}

		val selected = selectedIndex >= 0 && legendShowing

		for (i in localStart..localEnd) {
			var stackOffset = 0f

			if (selectedIndex == i && selected) {
				continue
			}

			for (k in lines.indices) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				val y = line.line.y
				val xPoint = p / 2 + chartData.xPercentage[i] * (fullWidth - p) - offset
				val yPercentage = y[i].toFloat() / currentMaxHeight
				val height = yPercentage * (measuredHeight - chartBottom - SIGNATURE_TEXT_HEIGHT) * line.alpha
				val yPoint = measuredHeight - chartBottom - height

				line.linesPath[line.linesPathBottomSize++] = xPoint
				line.linesPath[line.linesPathBottomSize++] = yPoint - stackOffset
				line.linesPath[line.linesPathBottomSize++] = xPoint
				line.linesPath[line.linesPathBottomSize++] = measuredHeight - chartBottom - stackOffset

				stackOffset += height
			}
		}

		for (k in lines.indices) {
			val line = lines[k]
			val paint = if (selected || postTransition) line.unselectedPaint else line.paint

			if (selected) {
				line.unselectedPaint.color = ColorUtils.blendARGB(line.lineColor, line.blendColor, selectionA)
			}

			if (postTransition) {
				line.unselectedPaint.color = ColorUtils.blendARGB(line.lineColor, line.blendColor, 1f)
			}

			paint.alpha = (255 * transitionAlpha).toInt()
			paint.strokeWidth = lineWidth

			canvas.drawLines(line.linesPath, 0, line.linesPathBottomSize, paint)
		}

		if (selected) {
			var stackOffset = 0f

			for (k in lines.indices) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				val y = line.line.y
				val xPoint = p / 2 + chartData.xPercentage[selectedIndex] * (fullWidth - p) - offset
				val yPercentage = y[selectedIndex].toFloat() / currentMaxHeight
				val height = yPercentage * (measuredHeight - chartBottom - SIGNATURE_TEXT_HEIGHT) * line.alpha
				val yPoint = measuredHeight - chartBottom - height

				line.paint.strokeWidth = lineWidth
				line.paint.alpha = (255 * transitionAlpha).toInt()

				canvas.drawLine(xPoint, yPoint - stackOffset, xPoint, measuredHeight - chartBottom - stackOffset, line.paint)

				stackOffset += height
			}
		}

		canvas.restore()
	}

	override fun selectXOnChart(x: Int, y: Int) {
		val chartData = chartData ?: return
		val oldSelectedIndex = selectedIndex
		val offset = chartFullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING

		val p = if (chartData.xPercentage.size < 2) {
			1f
		}
		else {
			chartData.xPercentage[1] * chartFullWidth
		}

		val xP = (offset + x) / (chartFullWidth - p)

		selectedCoordinate = xP

		if (xP < 0) {
			selectedIndex = 0
			selectedCoordinate = 0f
		}
		else if (xP > 1) {
			selectedIndex = chartData.x.size - 1
			selectedCoordinate = 1f
		}
		else {
			selectedIndex = chartData.findIndex(startXIndex, endXIndex, xP)
			if (selectedIndex > endXIndex) selectedIndex = endXIndex
			if (selectedIndex < startXIndex) selectedIndex = startXIndex
		}

		if (oldSelectedIndex != selectedIndex) {
			legendShowing = true

			animateLegend(true)
			moveLegend(offset)

			dateSelectionListener?.onDateSelected(selectedDate)

			invalidate()
			runSmoothHaptic()
		}
	}

	override fun drawPickerChart(canvas: Canvas) {
		val chartData = chartData ?: return

		val n = chartData.xPercentage.size
		val nl = lines.size

		for (k in lines.indices) {
			val line: LineViewData = lines[k]
			line.linesPathBottomSize = 0
		}

		val step = max(1, (n / 200f).roundToInt())

		if (yMaxPoints == null || (yMaxPoints?.size ?: 0) < nl) {
			yMaxPoints = IntArray(nl)
		}

		for (i in 0 until n) {
			var stackOffset = 0f
			val xPoint = chartData.xPercentage[i] * pickerWidth

			for (k in 0 until nl) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				val y = line.line.y[i]

				if (y > yMaxPoints!![k]) {
					yMaxPoints!![k] = y
				}
			}

			if (i % step == 0) {
				for (k in 0 until nl) {
					val line: LineViewData = lines[k]

					if (!line.enabled && line.alpha == 0f) {
						continue
					}

					val h = if (ANIMATE_PICKER_SIZES) pickerMaxHeight else chartData.maxValue.toFloat()
					val yPercentage = yMaxPoints!![k].toFloat() / h * line.alpha
					val yPoint = yPercentage * pikerHeight

					line.linesPath[line.linesPathBottomSize++] = xPoint
					line.linesPath[line.linesPathBottomSize++] = pikerHeight - yPoint - stackOffset
					line.linesPath[line.linesPathBottomSize++] = xPoint
					line.linesPath[line.linesPathBottomSize++] = pikerHeight - stackOffset

					stackOffset += yPoint

					yMaxPoints!![k] = 0
				}
			}
		}

		val p = if (chartData.xPercentage.size < 2) {
			1f
		}
		else {
			chartData.xPercentage[1] * pickerWidth
		}

		for (k in 0 until nl) {
			val line: LineViewData = lines[k]
			line.paint.strokeWidth = p * step
			line.paint.alpha = 255

			canvas.drawLines(line.linesPath, 0, line.linesPathBottomSize, line.paint)
		}
	}

	override fun onCheckChanged() {
		val chartData = chartData

		if (chartData != null) {
			val n = chartData.lines[0].y.size
			val k = chartData.lines.size

			chartData.ySum = IntArray(n)

			for (i in 0 until n) {
				chartData.ySum[i] = 0

				for (j in 0 until k) {
					if (lines[j].enabled) chartData.ySum[i] += chartData.lines[j].y[i]
				}
			}

			chartData.ySumSegmentTree = SegmentTree(chartData.ySum)
		}

		super.onCheckChanged()
	}

	override fun drawSelection(canvas: Canvas) {
		// unused
	}

	override fun findMaxValue(startXIndex: Int, endXIndex: Int): Int {
		return chartData?.findMax(startXIndex, endXIndex) ?: 0
	}

	override fun updatePickerMinMaxHeight() {
		if (!ANIMATE_PICKER_SIZES) {
			return
		}

		var max = 0
		val n = chartData?.x?.size ?: 0
		val nl = lines.size

		for (i in 0 until n) {
			var h = 0

			for (k in 0 until nl) {
				val l = lines[k]

				if (l.enabled) {
					h += l.line.y[i]
				}
			}

			if (h > max) {
				max = h
			}
		}

		if (max > 0 && max.toFloat() != animatedToPickerMaxHeight) {
			animatedToPickerMaxHeight = max.toFloat()

			pickerAnimator?.cancel()

			pickerAnimator = createAnimator(pickerMaxHeight, animatedToPickerMaxHeight) {
				pickerMaxHeight = it.animatedValue as Float
				invalidatePickerChart = true
				invalidate()
			}

			pickerAnimator?.start()
		}
	}

	override fun initPickerMaxHeight() {
		super.initPickerMaxHeight()

		pickerMaxHeight = 0f

		val n = chartData?.x?.size ?: 0
		val nl = lines.size

		for (i in 0 until n) {
			var h = 0

			for (k in 0 until nl) {
				val l = lines[k]

				if (l.enabled) {
					h += l.line.y[i]
				}
			}

			if (h > pickerMaxHeight) {
				pickerMaxHeight = h.toFloat()
			}
		}
	}

	override fun onDraw(canvas: Canvas) {
		tick()
		drawChart(canvas)
		drawBottomLine(canvas)

		tmpN = horizontalLines.size
		tmpI = 0

		while (tmpI < tmpN) {
			drawHorizontalLines(canvas, horizontalLines[tmpI])
			drawSignaturesToHorizontalLines(canvas, horizontalLines[tmpI])
			tmpI++
		}

		drawBottomSignature(canvas)
		drawPicker(canvas)
		drawSelection(canvas)

		super.onDraw(canvas)
	}

	override val minDistance: Float
		get() = 0.1f
}
