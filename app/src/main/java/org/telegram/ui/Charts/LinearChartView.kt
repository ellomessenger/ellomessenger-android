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
import android.graphics.Paint
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.view_data.LineViewData
import kotlin.math.max
import kotlin.math.min

class LinearChartView(context: Context) : BaseChartView<ChartData, LineViewData>(context) {
	override fun init() {
		useMinHeight = true
		super.init()
	}

	override fun drawChart(canvas: Canvas) {
		val chartData = chartData ?: return

		val fullWidth = chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart)
		val offset = fullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING

		for (k in lines.indices) {
			val line = lines[k]

			if (!line.enabled && line.alpha == 0f) {
				continue
			}

			var j = 0

			val p = if (chartData.xPercentage.size < 2) {
				0f
			}
			else {
				chartData.xPercentage[1] * fullWidth
			}

			val y = line.line.y

			val additionalPoints = (HORIZONTAL_PADDING / p).toInt() + 1

			line.chartPath.reset()

			var first = true
			val localStart = max(0, startXIndex - additionalPoints)
			val localEnd = min(chartData.xPercentage.size - 1, endXIndex + additionalPoints)

			for (i in localStart..localEnd) {
				if (y[i] < 0) {
					continue
				}

				val xPoint = chartData.xPercentage[i] * fullWidth - offset
				val yPercentage = (y[i].toFloat() - currentMinHeight) / (currentMaxHeight - currentMinHeight)
				val padding = line.paint.strokeWidth / 2f
				val yPoint = measuredHeight - chartBottom - padding - yPercentage * (measuredHeight - chartBottom - SIGNATURE_TEXT_HEIGHT - padding)

				if (USE_LINES) {
					if (j == 0) {
						line.linesPath[j++] = xPoint
						line.linesPath[j++] = yPoint
					}
					else {
						line.linesPath[j++] = xPoint
						line.linesPath[j++] = yPoint
						line.linesPath[j++] = xPoint
						line.linesPath[j++] = yPoint
					}
				}
				else {
					if (first) {
						first = false
						line.chartPath.moveTo(xPoint, yPoint)
					}
					else {
						line.chartPath.lineTo(xPoint, yPoint)
					}
				}
			}

			canvas.save()

			var transitionAlpha = 1f

			when (transitionMode) {
				TRANSITION_MODE_PARENT -> {
					transitionAlpha = if (transitionParams!!.progress > 0.5f) 0f else 1f - transitionParams!!.progress * 2f
					canvas.scale(1 + 2 * transitionParams!!.progress, 1f, transitionParams!!.pX, transitionParams!!.pY)
				}

				TRANSITION_MODE_CHILD -> {
					transitionAlpha = if (transitionParams!!.progress < 0.3f) 0f else transitionParams!!.progress
					canvas.save()
					canvas.scale(transitionParams!!.progress, if (transitionParams!!.needScaleY) transitionParams!!.progress else 1f, transitionParams!!.pX, transitionParams!!.pY)
				}

				TRANSITION_MODE_ALPHA_ENTER -> {
					transitionAlpha = transitionParams!!.progress
				}
			}

			line.paint.alpha = (255 * line.alpha * transitionAlpha).toInt()

			if (endXIndex - startXIndex > 100) {
				line.paint.strokeCap = Paint.Cap.SQUARE
			}
			else {
				line.paint.strokeCap = Paint.Cap.ROUND
			}

			if (!USE_LINES) {
				canvas.drawPath(line.chartPath, line.paint)
			}
			else {
				canvas.drawLines(line.linesPath, 0, j, line.paint)
			}

			canvas.restore()
		}
	}

	override fun drawPickerChart(canvas: Canvas) {
		val chartData = chartData ?: return
		val nl = lines.size

		for (k in 0 until nl) {
			val line = lines[k]

			if (!line.enabled && line.alpha == 0f) {
				continue
			}

			line.bottomLinePath.reset()

			val n = chartData.xPercentage.size
			var j = 0
			val y = line.line.y

			line.chartPath.reset()

			for (i in 0 until n) {
				if (y[i] < 0) {
					continue
				}

				val xPoint = chartData.xPercentage[i] * pickerWidth
				val h = if (ANIMATE_PICKER_SIZES) pickerMaxHeight else chartData.maxValue.toFloat()
				val hMin = if (ANIMATE_PICKER_SIZES) pickerMinHeight else chartData.minValue.toFloat()
				val yPercentage = (y[i] - hMin) / (h - hMin)
				val yPoint = (1f - yPercentage) * pikerHeight

				if (USE_LINES) {
					if (j == 0) {
						line.linesPathBottom[j++] = xPoint
						line.linesPathBottom[j++] = yPoint
					}
					else {
						line.linesPathBottom[j++] = xPoint
						line.linesPathBottom[j++] = yPoint
						line.linesPathBottom[j++] = xPoint
						line.linesPathBottom[j++] = yPoint
					}
				}
				else {
					if (i == 0) {
						line.bottomLinePath.moveTo(xPoint, yPoint)
					}
					else {
						line.bottomLinePath.lineTo(xPoint, yPoint)
					}
				}
			}

			line.linesPathBottomSize = j

			if (!line.enabled && line.alpha == 0f) {
				continue
			}

			line.bottomLinePaint.alpha = (255 * line.alpha).toInt()

			if (USE_LINES) {
				canvas.drawLines(line.linesPathBottom, 0, line.linesPathBottomSize, line.bottomLinePaint)
			}
			else {
				canvas.drawPath(line.bottomLinePath, line.bottomLinePaint)
			}
		}
	}

	override fun createLineViewData(line: ChartData.Line): LineViewData {
		return LineViewData(line)
	}
}
