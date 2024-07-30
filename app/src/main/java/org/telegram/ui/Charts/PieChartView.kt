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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.data.StackLinearChartData
import org.telegram.ui.Charts.view_data.ChartHorizontalLinesData
import org.telegram.ui.Charts.view_data.LegendSignatureView
import org.telegram.ui.Charts.view_data.LineViewData
import org.telegram.ui.Charts.view_data.PieLegendView
import org.telegram.ui.Charts.view_data.TransitionParams
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class PieChartView(context: Context) : StackLinearChartView<PieChartViewData>(context) {
	var values: FloatArray? = null
	private var drawingValuesPercentage: FloatArray? = null
	var sum = 0f
	var isEmpty = false
	private var currentSelection = -1
	var rectF = RectF()
	var textPaint: TextPaint
	private var minTextSize = AndroidUtilities.dp(9f).toFloat()
	private var maxTextSize = AndroidUtilities.dp(13f).toFloat()
	private var lookupTable = arrayOfNulls<String>(101)
	private var pieLegendView: PieLegendView? = null
	private var emptyDataAlpha = 1f

	override fun drawChart(canvas: Canvas) {
		if (chartData == null) {
			return
		}

		var transitionAlpha = 255

		canvas.save()

		if (transitionMode == TRANSITION_MODE_CHILD) {
			transitionAlpha = (transitionParams!!.progress * transitionParams!!.progress * 255).toInt()
		}

		if (isEmpty) {
			if (emptyDataAlpha != 0f) {
				emptyDataAlpha -= 0.12f

				if (emptyDataAlpha < 0) {
					emptyDataAlpha = 0f
				}

				invalidate()
			}
		}
		else {
			if (emptyDataAlpha != 1f) {
				emptyDataAlpha += 0.12f

				if (emptyDataAlpha > 1f) {
					emptyDataAlpha = 1f
				}

				invalidate()
			}
		}

		transitionAlpha = (transitionAlpha * emptyDataAlpha).toInt()

		val sc = 0.4f + emptyDataAlpha * 0.6f

		canvas.scale(sc, sc, chartArea.centerX(), chartArea.centerY())

		val radius = ((if (chartArea.width() > chartArea.height()) chartArea.height() else chartArea.width()) * 0.45f).toInt()

		rectF.set(chartArea.centerX() - radius, chartArea.centerY() + AndroidUtilities.dp(16f) - radius, chartArea.centerX() + radius, chartArea.centerY() + AndroidUtilities.dp(16f) + radius)

		var a = -90f
		var rText: Float
		val n = lines.size
		var localSum = 0f

		for (i in 0 until n) {
			val v = lines[i].drawingPart * lines[i].alpha
			localSum += v
		}

		if (localSum == 0f) {
			canvas.restore()
			return
		}

		for (i in 0 until n) {
			if (lines[i].alpha <= 0 && !lines[i].enabled) {
				continue
			}

			lines[i].paint.alpha = transitionAlpha
			val currentPercent = lines[i].drawingPart / localSum * lines[i].alpha

			drawingValuesPercentage?.set(i, currentPercent)

			if (currentPercent == 0f) {
				continue
			}
			canvas.save()

			val textAngle = (a + currentPercent / 2f * 360f).toDouble()

			if (lines[i].selectionA > 0f) {
				val ai = INTERPOLATOR.getInterpolation(lines[i].selectionA)
				canvas.translate((cos(Math.toRadians(textAngle)) * AndroidUtilities.dp(8f) * ai).toFloat(), (sin(Math.toRadians(textAngle)) * AndroidUtilities.dp(8f) * ai).toFloat())
			}

			lines[i].paint.style = Paint.Style.FILL_AND_STROKE
			lines[i].paint.strokeWidth = 1f
			lines[i].paint.isAntiAlias = !USE_LINES

			if (transitionMode != TRANSITION_MODE_CHILD) {
				canvas.drawArc(rectF, a, currentPercent * 360f, true, lines[i].paint)
				lines[i].paint.style = Paint.Style.STROKE
				canvas.restore()
			}

			lines[i].paint.alpha = 255

			a += currentPercent * 360f
		}

		a = -90f

		for (i in 0 until n) {
			if (lines[i].alpha <= 0 && !lines[i].enabled) {
				continue
			}

			val currentPercent = lines[i].drawingPart * lines[i].alpha / localSum

			canvas.save()

			val textAngle = (a + currentPercent / 2f * 360f).toDouble()

			if (lines[i].selectionA > 0f) {
				val ai = INTERPOLATOR.getInterpolation(lines[i].selectionA)
				canvas.translate((cos(Math.toRadians(textAngle)) * AndroidUtilities.dp(8f) * ai).toFloat(), (sin(Math.toRadians(textAngle)) * AndroidUtilities.dp(8f) * ai).toFloat())
			}

			val percent = (100f * currentPercent).toInt()

			if (currentPercent >= 0.02f && percent > 0 && percent <= 100) {
				rText = (rectF.width() * 0.42f * sqrt((1f - currentPercent).toDouble())).toFloat()
				textPaint.textSize = minTextSize + currentPercent * maxTextSize
				textPaint.alpha = (transitionAlpha * lines[i].alpha).toInt()
				canvas.drawText(lookupTable[percent]!!, (rectF.centerX() + rText * cos(Math.toRadians(textAngle))).toFloat(), (rectF.centerY() + rText * sin(Math.toRadians(textAngle))).toFloat() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
			}

			canvas.restore()
			lines[i].paint.alpha = 255
			a += currentPercent * 360f
		}

		canvas.restore()
	}

	override fun drawPickerChart(canvas: Canvas) {
		val chartData = chartData ?: return

		val n = chartData.xPercentage.size
		val nl = lines.size

		for (k in lines.indices) {
			val line: LineViewData = lines[k]
			line.linesPathBottomSize = 0
		}

		val p = 1f / chartData.xPercentage.size * pickerWidth

		for (i in 0 until n) {
			var stackOffset = 0f
			val xPoint = p / 2 + chartData.xPercentage[i] * (pickerWidth - p)
			var sum = 0f
			var drawingLinesCount = 0
			var allDisabled = true

			for (k in 0 until nl) {
				val line: LineViewData = lines[k]

				if (!line.enabled && line.alpha == 0f) {
					continue
				}

				val v = line.line.y[i] * line.alpha

				sum += v

				if (v > 0) {
					drawingLinesCount++

					if (line.enabled) {
						allDisabled = false
					}
				}
			}

			for (k in 0 until nl) {
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
					else if (allDisabled) {
						y[i] / sum * line.alpha * line.alpha
					}
					else {
						y[i] / sum * line.alpha
					}
				}

				val yPoint = yPercentage * pikerHeight

				line.linesPath[line.linesPathBottomSize++] = xPoint
				line.linesPath[line.linesPathBottomSize++] = pikerHeight - yPoint - stackOffset
				line.linesPath[line.linesPathBottomSize++] = xPoint
				line.linesPath[line.linesPathBottomSize++] = pikerHeight - stackOffset

				stackOffset += yPoint
			}
		}

		for (k in 0 until nl) {
			val line: LineViewData = lines[k]
			line.paint.strokeWidth = p
			line.paint.alpha = 255
			line.paint.isAntiAlias = false

			canvas.drawLines(line.linesPath, 0, line.linesPathBottomSize, line.paint)
		}
	}

	override fun drawBottomLine(canvas: Canvas) {
		// unused
	}

	override fun drawSelection(canvas: Canvas) {
		// unused
	}

	override fun drawHorizontalLines(canvas: Canvas, a: ChartHorizontalLinesData) {
		// unused
	}

	override fun drawSignaturesToHorizontalLines(canvas: Canvas, a: ChartHorizontalLinesData) {
		// unused
	}

	override fun drawBottomSignature(canvas: Canvas) {
		// unused
	}

	override fun setData(chartData: StackLinearChartData?) {
		super.setData(chartData)

		if (chartData != null) {
			values = FloatArray(chartData.lines.size)
			drawingValuesPercentage = FloatArray(chartData.lines.size)
			onPickerDataChanged(animated = false, force = true, useAnimator = false)
		}
	}

	override fun createLineViewData(line: ChartData.Line): PieChartViewData {
		return PieChartViewData(line)
	}

	override fun selectXOnChart(x: Int, y: Int) {
		if (chartData == null || isEmpty) {
			return
		}

		val theta = atan2((chartArea.centerY() + AndroidUtilities.dp(16f) - y).toDouble(), (chartArea.centerX() - x).toDouble())
		var a = (Math.toDegrees(theta) - 90).toFloat()

		if (a < 0) {
			a += 360.0.toFloat()
		}

		a /= 360f

		var p = 0f
		var newSelection = -1
		var selectionStartA = 0f
		var selectionEndA = 0f

		for (i in lines.indices) {
			if (!lines[i].enabled && lines[i].alpha == 0f) {
				continue
			}

			if (a > p && a < p + drawingValuesPercentage!![i]) {
				newSelection = i
				selectionStartA = p
				selectionEndA = p + drawingValuesPercentage!![i]
				break
			}

			p += drawingValuesPercentage!![i]
		}

		if (currentSelection != newSelection && newSelection >= 0) {
			currentSelection = newSelection

			invalidate()

			pieLegendView?.visible()

			val l: LineViewData = lines[newSelection]

			pieLegendView?.setData(l.line.name, values!![currentSelection].toInt(), l.lineColor)
			pieLegendView?.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.AT_MOST))

			val r = rectF.width() / 2
			var xl = min(rectF.centerX() + r * cos(Math.toRadians((selectionEndA * 360f - 90f).toDouble())), rectF.centerX() + r * cos(Math.toRadians((selectionStartA * 360f - 90f).toDouble()))).toInt()

			if (xl < 0) {
				xl = 0
			}

			if (xl + pieLegendView!!.measuredWidth > measuredWidth - AndroidUtilities.dp(16.toFloat())) {
				xl -= xl + pieLegendView!!.measuredWidth - (measuredWidth - AndroidUtilities.dp(16f))
			}

			var yl = min(rectF.centerY() + r * sin(Math.toRadians((selectionStartA * 360f - 90f).toDouble())), rectF.centerY() + r * sin(Math.toRadians((selectionEndA * 360f - 90f).toDouble()))).toInt()
			yl = min(rectF.centerY(), yl.toFloat()).toInt()
			yl -= AndroidUtilities.dp(50f)

			// if (yl < 0) yl = 0;

			pieLegendView!!.translationX = xl.toFloat()
			pieLegendView!!.translationY = yl.toFloat()

			var v = false

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
				v = performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}
			if (!v) {
				performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
			}
		}

		moveLegend()
	}

	override fun onDraw(canvas: Canvas) {
		if (chartData != null) {
			for (i in lines.indices) {
				if (i == currentSelection) {
					if (lines[i].selectionA < 1f) {
						lines[i].selectionA += 0.1f

						if (lines[i].selectionA > 1f) {
							lines[i].selectionA = 1f
						}

						invalidate()
					}
				}
				else {
					if (lines[i].selectionA > 0) {
						lines[i].selectionA -= 0.1f

						if (lines[i].selectionA < 0) {
							lines[i].selectionA = 0f
						}

						invalidate()
					}
				}
			}
		}

		super.onDraw(canvas)
	}

	override fun onActionUp() {
		currentSelection = -1
		pieLegendView?.gone()
		invalidate()
	}

	private var oldW = 0

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		if (measuredWidth != oldW) {
			oldW = measuredWidth
			val r = ((if (chartArea.width() > chartArea.height()) chartArea.height() else chartArea.width()) * 0.45f).toInt()
			minTextSize = (r / 13).toFloat()
			maxTextSize = (r / 7).toFloat()
		}
	}

	override fun updatePicker(chartData: ChartData, d: Long) {
		val n = chartData.x.size
		val startOfDay = d - d % 86400000L
		var startIndex = 0

		for (i in 0 until n) {
			if (startOfDay >= chartData.x[i]) startIndex = i
		}

		val p = if (chartData.xPercentage.size < 2) {
			0.5f
		}
		else {
			1f / chartData.x.size
		}

		if (startIndex == 0) {
			pickerDelegate.pickerStart = 0f
			pickerDelegate.pickerEnd = p
			return
		}

		if (startIndex >= chartData.x.size - 1) {
			pickerDelegate.pickerStart = 1f - p
			pickerDelegate.pickerEnd = 1f
			return
		}

		pickerDelegate.pickerStart = p * startIndex
		pickerDelegate.pickerEnd = pickerDelegate.pickerStart + p

		if (pickerDelegate.pickerEnd > 1f) {
			pickerDelegate.pickerEnd = 1f
		}

		onPickerDataChanged(animated = true, force = true, useAnimator = false)
	}

	override fun createLegendView(): LegendSignatureView {
		return PieLegendView(context).also { pieLegendView = it }
	}

	private var lastStartIndex = -1
	private var lastEndIndex = -1

	init {
		for (i in 1..100) {
			lookupTable[i] = "$i%"
		}

		textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		textPaint.textAlign = Paint.Align.CENTER
		textPaint.color = Color.WHITE
		textPaint.typeface = Theme.TYPEFACE_DEFAULT
		canCaptureChartSelection = true
	}

	override fun onPickerDataChanged(animated: Boolean, force: Boolean, useAnimator: Boolean) {
		super.onPickerDataChanged(animated, force, useAnimator)

		if (chartData?.xPercentage == null) {
			return
		}

		val startPercentage = pickerDelegate.pickerStart
		val endPercentage = pickerDelegate.pickerEnd

		updateCharValues(startPercentage, endPercentage, force)
	}

	private fun updateCharValues(startPercentage: Float, endPercentage: Float, force: Boolean) {
		val chartData = chartData ?: return
		val values = values ?: return
		val n = chartData.xPercentage.size
		val nl = lines.size
		var startIndex = -1
		var endIndex = -1

		for (j in 0 until n) {
			if (chartData.xPercentage[j] >= startPercentage && startIndex == -1) {
				startIndex = j
			}

			if (chartData.xPercentage[j] <= endPercentage) {
				endIndex = j
			}
		}

		if (endIndex < startIndex) {
			startIndex = endIndex
		}

		if (!force && lastEndIndex == endIndex && lastStartIndex == startIndex) {
			return
		}

		lastEndIndex = endIndex
		lastStartIndex = startIndex
		isEmpty = true
		sum = 0f

		for (i in 0 until nl) {
			values[i] = 0f
		}

		for (j in startIndex..endIndex) {
			for (i in 0 until nl) {
				values[i] += chartData.lines[i].y[j].toFloat()

				sum += chartData.lines[i].y[j].toFloat()

				if (isEmpty && lines[i].enabled && chartData.lines[i].y[j] > 0) {
					isEmpty = false
				}
			}
		}

		if (!force) {
			for (i in 0 until nl) {
				val line = lines[i]

				line.animator?.cancel()

				val animateTo = if (sum == 0f) {
					0f
				}
				else {
					values[i] / sum
				}

				val animator = createAnimator(line.drawingPart, animateTo) {
					line.drawingPart = it.animatedValue as Float
					invalidate()
				}

				line.animator = animator

				animator.start()
			}
		}
		else {
			for (i in 0 until nl) {
				if (sum == 0f) {
					lines[i].drawingPart = 0f
				}
				else {
					lines[i].drawingPart = values[i] / sum
				}
			}
		}
	}

	override fun onPickerJumpTo(start: Float, end: Float, force: Boolean) {
		if (chartData == null) {
			return
		}

		if (force) {
			updateCharValues(start, end, false)
		}
		else {
			updateIndexes()
			invalidate()
		}
	}

	override fun fillTransitionParams(params: TransitionParams?) {
		// drawChart(null) // MARK: check if this is required

		if (params == null) {
			return
		}

		drawingValuesPercentage?.let {
			var p = 0f

			for (i in it.indices) {
				p += it[i]
				params.angle?.set(i, p * 360 - 180)
			}
		}
	}
}
