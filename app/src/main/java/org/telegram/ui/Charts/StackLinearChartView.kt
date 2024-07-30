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
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.data.StackLinearChartData
import org.telegram.ui.Charts.view_data.LineViewData
import org.telegram.ui.Charts.view_data.StackLinearViewData
import org.telegram.ui.Charts.view_data.TransitionParams
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min

open class StackLinearChartView<T : StackLinearViewData>(context: Context) : BaseChartView<StackLinearChartData, T>(context) {
	private val matrix = Matrix()
	private val mapPoints = FloatArray(2)

	override fun createLineViewData(line: ChartData.Line): T {
		return StackLinearViewData(line) as T
	}

	private var ovalPath = Path()
	private var skipPoints: BooleanArray? = null
	private var startFromY: FloatArray? = null

	init {
		superDraw = true
		useAlphaSignature = true
		drawPointOnSelection = false
	}

	override fun drawChart(canvas: Canvas) {
		val chartData = chartData ?: return

		val fullWidth = chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart)
		val offset = fullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING
		val cX = chartArea.centerX()
		val cY = chartArea.centerY() + AndroidUtilities.dp(16f)

		for (k in lines.indices) {
			lines[k].chartPath.reset()
			lines[k].chartPathPicker.reset()
		}

		canvas.save()

		if (skipPoints == null || skipPoints!!.size < chartData.lines.size) {
			skipPoints = BooleanArray(chartData.lines.size)
			startFromY = FloatArray(chartData.lines.size)
		}

		var hasEmptyPoint = false
		var transitionAlpha = 255
		var transitionProgressHalf = 0f

		if (transitionMode == TRANSITION_MODE_PARENT) {
			transitionProgressHalf = transitionParams!!.progress / 0.6f

			if (transitionProgressHalf > 1f) {
				transitionProgressHalf = 1f
			}

			// transitionAlpha = (int) ((1f - transitionParams.progress) * 255);

			ovalPath.reset()

			val radiusStart = if (chartArea.width() > chartArea.height()) chartArea.width() else chartArea.height()
			val radiusEnd = (if (chartArea.width() > chartArea.height()) chartArea.height() else chartArea.width()) * 0.45f
			val radius = radiusEnd + (radiusStart - radiusEnd) / 2 * (1 - transitionParams!!.progress)

			val rectF = RectF()
			rectF.set(cX - radius, cY - radius, cX + radius, cY + radius)

			ovalPath.addRoundRect(rectF, radius, radius, Path.Direction.CW)

			canvas.clipPath(ovalPath)
		}
		else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
			transitionAlpha = (transitionParams!!.progress * 255).toInt()
		}

		var dX: Float
		var dY: Float
		var x1: Float
		var y1: Float

		val p = if (chartData.xPercentage.size < 2) {
			1f
		}
		else {
			chartData.xPercentage[1] * fullWidth
		}

		val additionalPoints = (HORIZONTAL_PADDING / p).toInt() + 1
		val localStart = max(0, startXIndex - additionalPoints - 1)
		val localEnd = min(chartData.xPercentage.size - 1, endXIndex + additionalPoints + 1)
		var startXPoint = 0f
		var endXPoint = 0f

		for (i in localStart..localEnd) {
			var stackOffset = 0f
			var sum = 0f
			var lastEnabled = 0
			var drawingLinesCount = 0

			for (k in lines.indices) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				if (line.line.y[i] > 0) {
					sum += line.line.y[i] * line.alpha
					drawingLinesCount++
				}

				lastEnabled = k
			}

			for (k in lines.indices) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				val y = line.line.y

				val yPercentage = if (drawingLinesCount == 1) {
					if (y[i] == 0) {
						0f
					}
					else {
						line.alpha
					}
				}
				else {
					if (sum == 0f) {
						0f
					}
					else {
						y[i] * line.alpha / sum
					}
				}

				var xPoint = chartData.xPercentage[i] * fullWidth - offset

				val nextXPoint = if (i == localEnd) {
					measuredWidth.toFloat()
				}
				else {
					chartData.xPercentage[i + 1] * fullWidth - offset
				}

				if (yPercentage == 0f && k == lastEnabled) {
					hasEmptyPoint = true
				}

				val height = yPercentage * (measuredHeight - chartBottom - SIGNATURE_TEXT_HEIGHT)
				var yPoint = measuredHeight - chartBottom - height - stackOffset

				startFromY?.set(k, yPoint)

				var angle = 0f
				var yPointZero = (measuredHeight - chartBottom).toFloat()
				var xPointZero = xPoint

				if (i == localEnd) {
					endXPoint = xPoint
				}
				else if (i == localStart) {
					startXPoint = xPoint
				}

				if (transitionMode == TRANSITION_MODE_PARENT && k != lastEnabled) {
					if (xPoint < cX) {
						x1 = transitionParams!!.startX!![k]
						y1 = transitionParams!!.startY!![k]
					}
					else {
						x1 = transitionParams!!.endX!![k]
						y1 = transitionParams!!.endY!![k]
					}

					dX = cX - x1
					dY = cY - y1

					val yTo = dY * (xPoint - x1) / dX + y1

					yPoint = yPoint * (1f - transitionProgressHalf) + yTo * transitionProgressHalf
					yPointZero = yPointZero * (1f - transitionProgressHalf) + yTo * transitionProgressHalf

					val angleK = dY / dX

					angle = if (angleK > 0) {
						Math.toDegrees(-atan(angleK.toDouble())).toFloat()
					}
					else {
						Math.toDegrees(atan(abs(angleK).toDouble())).toFloat()
					}

					angle -= 90f

					if (xPoint >= cX) {
						mapPoints[0] = xPoint
						mapPoints[1] = yPoint

						matrix.reset()
						matrix.postRotate(transitionParams!!.progress * angle, cX, cY)
						matrix.mapPoints(mapPoints)

						xPoint = mapPoints[0]
						yPoint = mapPoints[1]

						if (xPoint < cX) {
							xPoint = cX
						}

						mapPoints[0] = xPointZero
						mapPoints[1] = yPointZero

						matrix.reset()
						matrix.postRotate(transitionParams!!.progress * angle, cX, cY)
						matrix.mapPoints(mapPoints)

						yPointZero = mapPoints[1]

						if (xPointZero < cX) {
							xPointZero = cX
						}
					}
					else {
						if (nextXPoint >= cX) {
							xPoint = xPoint * (1f - transitionProgressHalf) + cX * transitionProgressHalf
							xPointZero = xPoint
							yPoint = yPoint * (1f - transitionProgressHalf) + cY * transitionProgressHalf
							yPointZero = yPoint
						}
						else {
							mapPoints[0] = xPoint
							mapPoints[1] = yPoint

							matrix.reset()
							matrix.postRotate(transitionParams!!.progress * angle + transitionParams!!.progress * transitionParams!!.angle!![k], cX, cY)
							matrix.mapPoints(mapPoints)

							xPoint = mapPoints[0]
							yPoint = mapPoints[1]

							mapPoints[0] = xPointZero
							mapPoints[1] = yPointZero

							matrix.reset()
							matrix.postRotate(transitionParams!!.progress * angle + transitionParams!!.progress * transitionParams!!.angle!![k], cX, cY)
							matrix.mapPoints(mapPoints)

							xPointZero = mapPoints[0]
							yPointZero = mapPoints[1]
						}
					}
				}

				if (i == localStart) {
					var localX = 0f
					var localY = measuredHeight.toFloat()

					if (transitionMode == TRANSITION_MODE_PARENT && k != lastEnabled) {
						mapPoints[0] = localX - cX
						mapPoints[1] = localY

						matrix.reset()
						matrix.postRotate(transitionParams!!.progress * angle + transitionParams!!.progress * transitionParams!!.angle!![k], cX, cY)
						matrix.mapPoints(mapPoints)

						localX = mapPoints[0]
						localY = mapPoints[1]
					}

					line.chartPath.moveTo(localX, localY)
					skipPoints!![k] = false
				}

				val transitionProgress = if (transitionParams == null) 0f else transitionParams!!.progress

				if (yPercentage == 0f && i > 0 && y[i - 1] == 0 && i < localEnd && y[i + 1] == 0 && transitionMode != TRANSITION_MODE_PARENT) {
					if (!skipPoints!![k]) {
						if (k == lastEnabled) {
							line.chartPath.lineTo(xPointZero, yPointZero * (1f - transitionProgress))
						}
						else {
							line.chartPath.lineTo(xPointZero, yPointZero)
						}
					}

					skipPoints!![k] = true
				}
				else {
					if (skipPoints!![k]) {
						if (k == lastEnabled) {
							line.chartPath.lineTo(xPointZero, yPointZero * (1f - transitionProgress))
						}
						else {
							line.chartPath.lineTo(xPointZero, yPointZero)
						}
					}

					if (k == lastEnabled) {
						line.chartPath.lineTo(xPoint, yPoint * (1f - transitionProgress))
					}
					else {
						line.chartPath.lineTo(xPoint, yPoint)
					}

					skipPoints!![k] = false
				}

				if (i == localEnd) {
					var localX = measuredWidth.toFloat()
					var localY = measuredHeight.toFloat()

					if (transitionMode == TRANSITION_MODE_PARENT && k != lastEnabled) {
						mapPoints[0] = localX + cX
						mapPoints[1] = localY

						matrix.reset()
						matrix.postRotate(transitionParams!!.progress * transitionParams!!.angle!![k], cX, cY)
						matrix.mapPoints(mapPoints)

						localX = mapPoints[0]
						localY = mapPoints[1]
					}
					else {
						line.chartPath.lineTo(localX, localY)
					}

					if (transitionMode == TRANSITION_MODE_PARENT && k != lastEnabled) {
						x1 = transitionParams!!.startX!![k]
						y1 = transitionParams!!.startY!![k]
						dX = cX - x1
						dY = cY - y1

						val angleK = dY / dX

						angle = if (angleK > 0) {
							Math.toDegrees(-atan(angleK.toDouble())).toFloat()
						}
						else {
							Math.toDegrees(atan(abs(angleK).toDouble())).toFloat()
						}

						angle -= 90f

						localX = transitionParams!!.startX!![k]
						localY = transitionParams!!.startY!![k]

						mapPoints[0] = localX
						mapPoints[1] = localY

						matrix.reset()
						matrix.postRotate(transitionParams!!.progress * angle + transitionParams!!.progress * transitionParams!!.angle!![k], cX, cY)
						matrix.mapPoints(mapPoints)

						localX = mapPoints[0]
						localY = mapPoints[1]

						// 0 right_top
						// 1 right_bottom
						// 2 left_bottom
						// 3 left_top
						var endQuarter: Int
						var startQuarter: Int

						if (abs(xPoint - localX) < 0.001 && (localY < cY && yPoint < cY || localY > cY && yPoint > cY)) {
							if (transitionParams!!.angle!![k] == -180f) {
								endQuarter = 0
								startQuarter = 0
							}
							else {
								endQuarter = 0
								startQuarter = 3
							}
						}
						else {
							endQuarter = quarterForPoint(xPoint, yPoint)
							startQuarter = quarterForPoint(localX, localY)
						}

						for (q in endQuarter..startQuarter) {
							when (q) {
								0 -> line.chartPath.lineTo(measuredWidth.toFloat(), 0f)
								1 -> line.chartPath.lineTo(measuredWidth.toFloat(), measuredHeight.toFloat())
								2 -> line.chartPath.lineTo(0f, measuredHeight.toFloat())
								else -> line.chartPath.lineTo(0f, 0f)
							}
						}
					}
				}

				stackOffset += height
			}
		}

		canvas.save()
		canvas.clipRect(startXPoint, SIGNATURE_TEXT_HEIGHT.toFloat(), endXPoint, (measuredHeight - chartBottom).toFloat())

		if (hasEmptyPoint) {
			canvas.drawColor(context.getColor(R.color.light_gray))
		}

		for (k in lines.indices.reversed()) {
			val line: LineViewData = lines[k]
			line.paint.alpha = transitionAlpha
			canvas.drawPath(line.chartPath, line.paint)
			line.paint.alpha = 255
		}

		canvas.restore()
		canvas.restore()
	}

	private fun quarterForPoint(x: Float, y: Float): Int {
		val cX = chartArea.centerX()
		val cY = chartArea.centerY() + AndroidUtilities.dp(16f)

		if (x >= cX && y <= cY) {
			return 0
		}

		if (x >= cX && y >= cY) {
			return 1
		}

		if (x < cX && y >= cY) {
			return 2
		}

		return 3
	}

	override fun drawPickerChart(canvas: Canvas) {
		val chartData = chartData ?: return

		val nl = lines.size

		for (k in 0 until nl) {
			lines[k].chartPathPicker.reset()
		}

		val n = chartData.simplifiedSize

		if (skipPoints == null || skipPoints!!.size < chartData.lines.size) {
			skipPoints = BooleanArray(chartData.lines.size)
		}

		var hasEmptyPoint = false

		for (i in 0 until n) {
			var stackOffset = 0f
			var sum = 0f
			var lastEnabled = 0
			var drawingLinesCount = 0

			for (k in lines.indices) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				if (chartData.simplifiedY!![k][i] > 0) {
					sum += chartData.simplifiedY!![k][i] * line.alpha
					drawingLinesCount++
				}

				lastEnabled = k
			}

			val xPoint = i / (n - 1).toFloat() * pickerWidth

			for (k in lines.indices) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				val yPercentage = if (drawingLinesCount == 1) {
					if (chartData.simplifiedY!![k][i] == 0) {
						0f
					}
					else {
						line.alpha
					}
				}
				else {
					if (sum == 0f) {
						0f
					}
					else {
						chartData.simplifiedY!![k][i] * line.alpha / sum
					}
				}

				if (yPercentage == 0f && k == lastEnabled) {
					hasEmptyPoint = true
				}

				val height = yPercentage * pikerHeight
				val yPoint = pikerHeight - height - stackOffset

				if (i == 0) {
					line.chartPathPicker.moveTo(0f, pikerHeight.toFloat())
					skipPoints!![k] = false
				}

				if (chartData.simplifiedY!![k][i] == 0 && i > 0 && chartData.simplifiedY!![k][i - 1] == 0 && i < n - 1 && chartData.simplifiedY!![k][i + 1] == 0) {
					if (!skipPoints!![k]) {
						line.chartPathPicker.lineTo(xPoint, pikerHeight.toFloat())
					}

					skipPoints!![k] = true
				}
				else {
					if (skipPoints!![k]) {
						line.chartPathPicker.lineTo(xPoint, pikerHeight.toFloat())
					}

					line.chartPathPicker.lineTo(xPoint, yPoint)
					skipPoints!![k] = false
				}

				if (i == n - 1) {
					line.chartPathPicker.lineTo(pickerWidth, pikerHeight.toFloat())
				}

				stackOffset += height
			}
		}

		if (hasEmptyPoint) {
			canvas.drawColor(context.getColor(R.color.light_gray))
		}

		for (k in lines.indices.reversed()) {
			val line: LineViewData = lines[k]
			canvas.drawPath(line.chartPathPicker, line.paint)
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

	override fun findMaxValue(startXIndex: Int, endXIndex: Int): Int {
		return 100
	}

	override val minDistance: Float
		get() = 0.1f

	override fun fillTransitionParams(params: TransitionParams?) {
		val chartData = chartData ?: return
		val fullWidth = chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart)
		val offset = fullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING

		val p = if (chartData.xPercentage.size < 2) {
			1f
		}
		else {
			chartData.xPercentage[1] * fullWidth
		}

		val additionalPoints = (HORIZONTAL_PADDING / p).toInt() + 1
		val localStart = max(0, startXIndex - additionalPoints - 1)
		val localEnd = min(chartData.xPercentage.size - 1, endXIndex + additionalPoints + 1)

		transitionParams!!.startX = FloatArray(chartData.lines.size)
		transitionParams!!.startY = FloatArray(chartData.lines.size)
		transitionParams!!.endX = FloatArray(chartData.lines.size)
		transitionParams!!.endY = FloatArray(chartData.lines.size)
		transitionParams!!.angle = FloatArray(chartData.lines.size)

		for (j in 0..1) {
			var i = localStart

			if (j == 1) {
				i = localEnd
			}

			var stackOffset = 0
			var sum = 0f
			var drawingLinesCount = 0

			for (k in lines.indices) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				if (line.line.y[i] > 0) {
					sum += line.line.y[i] * line.alpha
					drawingLinesCount++
				}
			}

			for (k in lines.indices) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				val y = line.line.y

				val yPercentage = if (drawingLinesCount == 1) {
					if (y[i] == 0) {
						0f
					}
					else {
						line.alpha
					}
				}
				else {
					if (sum == 0f) {
						0f
					}
					else {
						y[i] * line.alpha / sum
					}
				}

				val xPoint = chartData.xPercentage[i] * fullWidth - offset
				val height = yPercentage * (measuredHeight - chartBottom - SIGNATURE_TEXT_HEIGHT)
				val yPoint = measuredHeight - chartBottom - height - stackOffset

				stackOffset += height.toInt()

				if (j == 0) {
					transitionParams!!.startX!![k] = xPoint
					transitionParams!!.startY!![k] = yPoint
				}
				else {
					transitionParams!!.endX!![k] = xPoint
					transitionParams!!.endY!![k] = yPoint
				}
			}
		}
	}
}
