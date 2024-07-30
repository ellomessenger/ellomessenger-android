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
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.view_data.ChartBottomSignatureData
import org.telegram.ui.Charts.view_data.ChartHeaderView
import org.telegram.ui.Charts.view_data.ChartHorizontalLinesData
import org.telegram.ui.Charts.view_data.LegendSignatureView
import org.telegram.ui.Charts.view_data.LineViewData
import org.telegram.ui.Charts.view_data.TransitionParams
import org.telegram.ui.Components.CubicBezierInterpolator
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

abstract class BaseChartView<T : ChartData, L : LineViewData>(context: Context) : View(context), ChartPickerDelegate.Listener {
	private var maxValueAnimator: Animator? = null
	var pickerAnimator: Animator? = null
	var bottomSignatureDate = ArrayList<ChartBottomSignatureData>(25)
	var horizontalLines = ArrayList<ChartHorizontalLinesData>(10)
	private var currentBottomSignatures: ChartBottomSignatureData? = null
	private var bottomSignaturePaint: Paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
	private var emptyPaint = Paint()
	var linePaint = Paint()
	private var pickerSelectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	var selectedLinePaint = Paint()
	var selectionBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	var signaturePaint: Paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
	var signaturePaint2: Paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
	private var inactiveBottomChartPaint = Paint()
	private var whiteLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var pathTmp = Path()
	private var pickerRect = Rect()
	var chartData: T? = null
	private var alphaAnimator: ValueAnimator? = null
	private var alphaBottomAnimator: ValueAnimator? = null
	private var selectionAnimator: ValueAnimator? = null
	private var vibrationEffect: VibrationEffect? = null
	var invalidatePickerChart = true
	var isLandscape = false
	var postTransition = false
	var superDraw = false
	var useAlphaSignature = false
	private var animateToMaxHeight = 0f
	private var animateToMinHeight = 0f
	private var bottomSignaturePaintAlpha = 0f
	var signaturePaintAlpha = 0f
	private var thresholdMaxHeight = 0f
	var chartActiveLineAlpha = 0
	var chartBottom = 0
	var endXIndex = 0
	private var hintLinePaintAlpha = 0
	var startXIndex = 0
	private var bottomChartBitmap: Bitmap? = null
	private var bottomChartCanvas: Canvas? = null
	private val touchSlop: Int
	private var chartCaptured = false
	protected var drawPointOnSelection = true
	protected var animatedToPickerMaxHeight = 0f
	private var animatedToPickerMinHeight = 0f
	protected var pickerMaxHeight = 0f
	protected var pickerMinHeight = 0f
	protected var selectedCoordinate = -1f
	private var bottomSignatureOffset = 0
	protected var selectedIndex = -1
	protected var tmpI = 0
	protected var tmpN = 0
	var lines: ArrayList<L> = ArrayList()
	var pickerDelegate = ChartPickerDelegate(this)
	var legendSignatureView: LegendSignatureView? = null
	var chartArea = RectF()
	var sharedUiComponents: SharedUiComponents? = null
	var transitionParams: TransitionParams? = null
	var legendShowing = false
	var chartEnd = 0f
	var chartFullWidth = 0f
	var chartStart = 0f
	var chartWidth = 0f
	var currentMaxHeight = 250f
	var currentMinHeight = 0f
	var pickerWidth = 0f
	var selectionA = 0f
	var pikerHeight = AndroidUtilities.dp(46f)
	var transitionMode = TRANSITION_MODE_NONE

	@JvmField
	var enabled = true

	private val pickerHeightUpdateListener = AnimatorUpdateListener {
		pickerMaxHeight = it.animatedValue as Float
		invalidatePickerChart = true
		invalidate()
	}

	private val pickerMinHeightUpdateListener = AnimatorUpdateListener {
		pickerMinHeight = it.animatedValue as Float
		invalidatePickerChart = true
		invalidate()
	}

	private val heightUpdateListener = AnimatorUpdateListener {
		currentMaxHeight = it.animatedValue as Float
		invalidate()
	}

	private val minHeightUpdateListener = AnimatorUpdateListener {
		currentMinHeight = it.animatedValue as Float
		invalidate()
	}

	private val selectionAnimatorListener = AnimatorUpdateListener {
		selectionA = it.animatedValue as Float
		legendSignatureView?.alpha = selectionA
		invalidate()
	}

	private val selectorAnimatorEndListener: Animator.AnimatorListener = object : AnimatorListenerAdapter() {
		override fun onAnimationEnd(animation: Animator) {
			super.onAnimationEnd(animation)

			if (!animateLegendTo) {
				legendShowing = false
				legendSignatureView?.gone()
				invalidate()
			}

			postTransition = false
		}
	}

	protected var useMinHeight = false

	var dateSelectionListener: DateSelectionListener? = null
	private var startFromMax = 0f
	private var startFromMin = 0f
	private var startFromMaxH = 0f
	private var startFromMinH = 0f
	private var minMaxUpdateStep = 0f

	protected open fun init() {
		linePaint.strokeWidth = LINE_WIDTH

		selectedLinePaint.strokeWidth = SELECTED_LINE_WIDTH

		signaturePaint.textSize = SIGNATURE_TEXT_SIZE
		signaturePaint.typeface = Theme.TYPEFACE_DEFAULT

		signaturePaint2.textSize = SIGNATURE_TEXT_SIZE
		signaturePaint2.textAlign = Paint.Align.RIGHT
		signaturePaint2.typeface = Theme.TYPEFACE_DEFAULT

		bottomSignaturePaint.textSize = SIGNATURE_TEXT_SIZE
		bottomSignaturePaint.textAlign = Paint.Align.CENTER
		bottomSignaturePaint.typeface = Theme.TYPEFACE_DEFAULT

		selectionBackgroundPaint.strokeWidth = AndroidUtilities.dpf2(6f)
		selectionBackgroundPaint.strokeCap = Paint.Cap.ROUND

		setLayerType(LAYER_TYPE_HARDWARE, null)
		setWillNotDraw(false)

		legendSignatureView = createLegendView()
		legendSignatureView?.gone()

		whiteLinePaint.color = Color.WHITE
		whiteLinePaint.strokeWidth = AndroidUtilities.dpf2(3f)
		whiteLinePaint.strokeCap = Paint.Cap.ROUND

		updateColors()
	}

	protected open fun createLegendView(): LegendSignatureView {
		return LegendSignatureView(context)
	}

	fun updateColors() {
		if (useAlphaSignature) {
			signaturePaint.color = context.getColor(R.color.gray_border_transparent)
		}
		else {
			signaturePaint.color = context.getColor(R.color.gray_border_transparent)
		}

		bottomSignaturePaint.color = context.getColor(R.color.gray_border_transparent)
		linePaint.color = context.getColor(R.color.gray_border_transparent)
		selectedLinePaint.color = context.getColor(R.color.medium_gray)
		pickerSelectorPaint.color = context.getColor(R.color.chat_input_hint)
		inactiveBottomChartPaint.color = context.getColor(R.color.shadow)
		selectionBackgroundPaint.color = context.getColor(R.color.background)
		ripplePaint.color = context.getColor(R.color.light_background)
		legendSignatureView!!.recolor()
		hintLinePaintAlpha = linePaint.alpha
		chartActiveLineAlpha = selectedLinePaint.alpha
		signaturePaintAlpha = signaturePaint.alpha / 255f
		bottomSignaturePaintAlpha = bottomSignaturePaint.alpha / 255f

		for (line in lines) {
			line.updateColors()
		}

		if (legendShowing && selectedIndex < chartData!!.x.size) {
			legendSignatureView?.setData(selectedIndex, chartData!!.x[selectedIndex], lines as ArrayList<LineViewData>, false)
		}

		invalidatePickerChart = true
	}

	private var lastW = 0
	var lastH = 0

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		if (!isLandscape) {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(widthMeasureSpec))
		}
		else {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.displaySize.y - AndroidUtilities.dp(56f))
		}

		if (measuredWidth != lastW || measuredHeight != lastH) {
			lastW = measuredWidth
			lastH = measuredHeight

			bottomChartBitmap = Bitmap.createBitmap((measuredWidth - HORIZONTAL_PADDING * 2f).toInt(), pikerHeight, Bitmap.Config.ARGB_8888)

			bottomChartCanvas = Canvas(bottomChartBitmap!!)

			sharedUiComponents?.getPickerMaskBitmap(pikerHeight, (measuredWidth - HORIZONTAL_PADDING * 2).toInt())

			measureSizes()

			if (legendShowing) {
				moveLegend(chartFullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING)
			}

			onPickerDataChanged(animated = false, force = true, useAnimator = false)
		}
	}

	private fun measureSizes() {
		if (measuredHeight <= 0 || measuredWidth <= 0) {
			return
		}

		pickerWidth = measuredWidth - HORIZONTAL_PADDING * 2
		chartStart = HORIZONTAL_PADDING
		chartEnd = (measuredWidth - if (isLandscape) LANDSCAPE_END_PADDING else HORIZONTAL_PADDING.toInt()).toFloat()
		chartWidth = chartEnd - chartStart
		chartFullWidth = chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart)

		updateLineSignature()

		chartBottom = AndroidUtilities.dp(100f)
		chartArea[chartStart - HORIZONTAL_PADDING, 0f, chartEnd + HORIZONTAL_PADDING] = (measuredHeight - chartBottom).toFloat()

		chartData?.let {
			bottomSignatureOffset = (AndroidUtilities.dp(20f) / (pickerWidth / it.x.size)).toInt()
		}

		measureHeightThreshold()
	}

	private fun measureHeightThreshold() {
		val chartHeight = measuredHeight - chartBottom

		if (animateToMaxHeight == 0f || chartHeight == 0) {
			return
		}

		thresholdMaxHeight = animateToMaxHeight / chartHeight * SIGNATURE_TEXT_SIZE
	}

	protected open fun drawPickerChart(canvas: Canvas) {
		// stub
	}

	override fun onDraw(canvas: Canvas) {
		if (superDraw) {
			super.onDraw(canvas)
			return
		}

		tick()

		val count = canvas.save()
		canvas.clipRect(0f, chartArea.top, measuredWidth.toFloat(), chartArea.bottom)
		drawBottomLine(canvas)

		tmpN = horizontalLines.size
		tmpI = 0

		while (tmpI < tmpN) {
			drawHorizontalLines(canvas, horizontalLines[tmpI])
			tmpI++
		}

		drawChart(canvas)

		tmpI = 0

		while (tmpI < tmpN) {
			drawSignaturesToHorizontalLines(canvas, horizontalLines[tmpI])
			tmpI++
		}

		canvas.restoreToCount(count)
		drawBottomSignature(canvas)
		drawPicker(canvas)
		drawSelection(canvas)

		super.onDraw(canvas)
	}

	protected fun tick() {
		if (minMaxUpdateStep == 0f) {
			return
		}

		if (currentMaxHeight != animateToMaxHeight) {
			startFromMax += minMaxUpdateStep

			if (startFromMax > 1) {
				startFromMax = 1f
				currentMaxHeight = animateToMaxHeight
			}
			else {
				currentMaxHeight = startFromMaxH + (animateToMaxHeight - startFromMaxH) * CubicBezierInterpolator.EASE_OUT.getInterpolation(startFromMax)
			}

			invalidate()
		}

		if (useMinHeight) {
			if (currentMinHeight != animateToMinHeight) {
				startFromMin += minMaxUpdateStep

				if (startFromMin > 1) {
					startFromMin = 1f
					currentMinHeight = animateToMinHeight
				}
				else {
					currentMinHeight = startFromMinH + (animateToMinHeight - startFromMinH) * CubicBezierInterpolator.EASE_OUT.getInterpolation(startFromMin)
				}

				invalidate()
			}
		}
	}

	open fun drawBottomSignature(canvas: Canvas) {
		val chartData = chartData ?: return

		tmpN = bottomSignatureDate.size

		var transitionAlpha = 1f

		when (transitionMode) {
			TRANSITION_MODE_PARENT -> {
				transitionAlpha = 1f - transitionParams!!.progress
			}

			TRANSITION_MODE_CHILD -> {
				transitionAlpha = transitionParams!!.progress
			}

			TRANSITION_MODE_ALPHA_ENTER -> {
				transitionAlpha = transitionParams!!.progress
			}
		}

		tmpI = 0

		while (tmpI < tmpN) {
			val resultAlpha = bottomSignatureDate[tmpI].alpha
			var step = bottomSignatureDate[tmpI].step

			if (step == 0) {
				step = 1
			}

			var start = startXIndex - bottomSignatureOffset

			while (start % step != 0) {
				start--
			}

			var end = endXIndex - bottomSignatureOffset

			while (end % step != 0 || end < chartData.x.size - 1) {
				end++
			}

			start += bottomSignatureOffset
			end += bottomSignatureOffset

			val offset = chartFullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING
			var i = start

			while (i < end) {
				if (i < 0 || i >= chartData.x.size - 1) {
					i += step
					continue
				}

				val xPercentage = (chartData.x[i] - chartData.x[0]).toFloat() / (chartData.x[chartData.x.size - 1] - chartData.x[0]).toFloat()
				val xPoint = xPercentage * chartFullWidth - offset
				val xPointOffset = xPoint - BOTTOM_SIGNATURE_OFFSET

				if (xPointOffset > 0 && xPointOffset <= chartWidth + HORIZONTAL_PADDING) {
					if (xPointOffset < BOTTOM_SIGNATURE_START_ALPHA) {
						val a = 1f - (BOTTOM_SIGNATURE_START_ALPHA - xPointOffset) / BOTTOM_SIGNATURE_START_ALPHA
						bottomSignaturePaint.alpha = (resultAlpha * a * bottomSignaturePaintAlpha * transitionAlpha).toInt()
					}
					else if (xPointOffset > chartWidth) {
						val a = 1f - (xPointOffset - chartWidth) / HORIZONTAL_PADDING
						bottomSignaturePaint.alpha = (resultAlpha * a * bottomSignaturePaintAlpha * transitionAlpha).toInt()
					}
					else {
						bottomSignaturePaint.alpha = (resultAlpha * bottomSignaturePaintAlpha * transitionAlpha).toInt()
					}

					canvas.drawText(chartData.getDayString(i)!!, xPoint, (measuredHeight - chartBottom + BOTTOM_SIGNATURE_TEXT_HEIGHT + AndroidUtilities.dp(3f)).toFloat(), bottomSignaturePaint)
				}

				i += step
			}

			tmpI++
		}
	}

	protected open fun drawBottomLine(canvas: Canvas) {
		if (chartData == null) {
			return
		}

		var transitionAlpha = 1f

		when (transitionMode) {
			TRANSITION_MODE_PARENT -> {
				transitionAlpha = 1f - (transitionParams?.progress ?: 0f)
			}

			TRANSITION_MODE_CHILD -> {
				transitionAlpha = transitionParams?.progress ?: 0f
			}

			TRANSITION_MODE_ALPHA_ENTER -> {
				transitionAlpha = transitionParams?.progress ?: 0f
			}
		}

		linePaint.alpha = (hintLinePaintAlpha * transitionAlpha).toInt()
		signaturePaint.alpha = (255 * signaturePaintAlpha * transitionAlpha).toInt()

		val textOffset = (SIGNATURE_TEXT_HEIGHT - signaturePaint.textSize).toInt()
		val y = measuredHeight - chartBottom - 1

		canvas.drawLine(chartStart, y.toFloat(), chartEnd, y.toFloat(), linePaint)

		if (useMinHeight) {
			return
		}

		canvas.drawText("0", HORIZONTAL_PADDING, (y - textOffset).toFloat(), signaturePaint)
	}

	protected open fun drawSelection(canvas: Canvas) {
		val chartData = chartData ?: return

		if (selectedIndex < 0 || !legendShowing) {
			return
		}

		val alpha = (chartActiveLineAlpha * selectionA).toInt()
		val fullWidth = chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart)
		val offset = fullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING

		val xPoint = if (selectedIndex < chartData.xPercentage.size) {
			chartData.xPercentage[selectedIndex] * fullWidth - offset
		}
		else {
			return
		}

		selectedLinePaint.alpha = alpha

		canvas.drawLine(xPoint, 0f, xPoint, chartArea.bottom, selectedLinePaint)

		if (drawPointOnSelection) {
			tmpN = lines.size
			tmpI = 0

			while (tmpI < tmpN) {
				val line: LineViewData = lines[tmpI]

				if (!line.enabled && line.alpha == 0f) {
					tmpI++
					continue
				}

				val yPercentage = (line.line.y[selectedIndex] - currentMinHeight) / (currentMaxHeight - currentMinHeight)
				val yPoint = measuredHeight - chartBottom - yPercentage * (measuredHeight - chartBottom - SIGNATURE_TEXT_HEIGHT)
				line.selectionPaint.alpha = (255 * line.alpha * selectionA).toInt()
				selectionBackgroundPaint.alpha = (255 * line.alpha * selectionA).toInt()
				canvas.drawPoint(xPoint, yPoint, line.selectionPaint)
				canvas.drawPoint(xPoint, yPoint, selectionBackgroundPaint)
				tmpI++
			}
		}
	}

	protected open fun drawChart(canvas: Canvas) {
		// stub
	}

	protected open fun drawHorizontalLines(canvas: Canvas, a: ChartHorizontalLinesData) {
		val n = a.values.size
		var additionalOutAlpha = 1f

		if (n > 2) {
			val v = (a.values[1] - a.values[0]) / (currentMaxHeight - currentMinHeight)

			if (v < 0.1) {
				additionalOutAlpha = v / 0.1f
			}
		}

		var transitionAlpha = 1f

		when (transitionMode) {
			TRANSITION_MODE_PARENT -> {
				transitionAlpha = 1f - (transitionParams?.progress ?: 0f)
			}

			TRANSITION_MODE_CHILD -> {
				transitionAlpha = transitionParams?.progress ?: 0f
			}

			TRANSITION_MODE_ALPHA_ENTER -> {
				transitionAlpha = transitionParams?.progress ?: 0f
			}
		}

		linePaint.alpha = (a.alpha * (hintLinePaintAlpha / 255f) * transitionAlpha * additionalOutAlpha).toInt()
		signaturePaint.alpha = (a.alpha * signaturePaintAlpha * transitionAlpha * additionalOutAlpha).toInt()

		val chartHeight = measuredHeight - chartBottom - SIGNATURE_TEXT_HEIGHT

		for (i in (if (useMinHeight) 0 else 1) until n) {
			val y = (measuredHeight - chartBottom - chartHeight * ((a.values[i] - currentMinHeight) / (currentMaxHeight - currentMinHeight))).toInt()
			canvas.drawRect(chartStart, y.toFloat(), chartEnd, (y + 1).toFloat(), linePaint)
		}
	}

	protected open fun drawSignaturesToHorizontalLines(canvas: Canvas, a: ChartHorizontalLinesData) {
		val n = a.values.size
		var additionalOutAlpha = 1f

		if (n > 2) {
			val v = (a.values[1] - a.values[0]) / (currentMaxHeight - currentMinHeight)

			if (v < 0.1) {
				additionalOutAlpha = v / 0.1f
			}
		}

		var transitionAlpha = 1f

		when (transitionMode) {
			TRANSITION_MODE_PARENT -> {
				transitionAlpha = 1f - (transitionParams?.progress ?: 0f)
			}

			TRANSITION_MODE_CHILD -> {
				transitionAlpha = transitionParams?.progress ?: 0f
			}

			TRANSITION_MODE_ALPHA_ENTER -> {
				transitionAlpha = transitionParams?.progress ?: 0f
			}
		}

		linePaint.alpha = (a.alpha * (hintLinePaintAlpha / 255f) * transitionAlpha * additionalOutAlpha).toInt()
		signaturePaint.alpha = (a.alpha * signaturePaintAlpha * transitionAlpha * additionalOutAlpha).toInt()

		val chartHeight = measuredHeight - chartBottom - SIGNATURE_TEXT_HEIGHT
		val textOffset = (SIGNATURE_TEXT_HEIGHT - signaturePaint.textSize).toInt()

		for (i in (if (useMinHeight) 0 else 1) until n) {
			val y = (measuredHeight - chartBottom - chartHeight * ((a.values[i] - currentMinHeight) / (currentMaxHeight - currentMinHeight))).toInt()
			canvas.drawText(a.valuesStr[i] ?: "", HORIZONTAL_PADDING, (y - textOffset).toFloat(), signaturePaint)
		}
	}

	fun drawPicker(canvas: Canvas) {
		if (chartData == null) {
			return
		}

		pickerDelegate.pickerWidth = pickerWidth

		val bottom = measuredHeight - PICKER_PADDING
		val top = measuredHeight - pikerHeight - PICKER_PADDING
		var start = (HORIZONTAL_PADDING + pickerWidth * pickerDelegate.pickerStart).toInt()
		var end = (HORIZONTAL_PADDING + pickerWidth * pickerDelegate.pickerEnd).toInt()
		var transitionAlpha = 1f

		if (transitionMode == TRANSITION_MODE_CHILD) {
			val startParent = (HORIZONTAL_PADDING + pickerWidth * transitionParams!!.pickerStartOut).toInt()
			val endParent = (HORIZONTAL_PADDING + pickerWidth * transitionParams!!.pickerEndOut).toInt()
			start += ((startParent - start) * (1f - transitionParams!!.progress)).toInt()
			end += ((endParent - end) * (1f - transitionParams!!.progress)).toInt()
		}
		else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
			transitionAlpha = transitionParams!!.progress
		}

		var instantDraw = false

		if (transitionMode == TRANSITION_MODE_NONE) {
			for (i in lines.indices) {
				val l = lines[i]

				if (l.animatorIn != null && l.animatorIn!!.isRunning || l.animatorOut != null && l.animatorOut!!.isRunning) {
					instantDraw = true
					break
				}
			}
		}

		if (instantDraw) {
			canvas.save()
			canvas.clipRect(HORIZONTAL_PADDING, (measuredHeight - PICKER_PADDING - pikerHeight).toFloat(), measuredWidth - HORIZONTAL_PADDING, (measuredHeight - PICKER_PADDING).toFloat())
			canvas.translate(HORIZONTAL_PADDING, (measuredHeight - PICKER_PADDING - pikerHeight).toFloat())
			drawPickerChart(canvas)
			canvas.restore()
		}
		else if (invalidatePickerChart) {
			bottomChartBitmap?.eraseColor(0)

			bottomChartCanvas?.let {
				drawPickerChart(it)
			}

			invalidatePickerChart = false
		}

		if (!instantDraw) {
			when (transitionMode) {
				TRANSITION_MODE_PARENT -> {
					val pY = (top + (bottom - top) shr 1).toFloat()
					val pX = HORIZONTAL_PADDING + pickerWidth * transitionParams!!.xPercentage
					emptyPaint.alpha = ((1f - transitionParams!!.progress) * 255).toInt()
					canvas.save()
					canvas.clipRect(HORIZONTAL_PADDING, top.toFloat(), measuredWidth - HORIZONTAL_PADDING, bottom.toFloat())
					canvas.scale(1 + 2 * transitionParams!!.progress, 1f, pX, pY)
					canvas.drawBitmap(bottomChartBitmap!!, HORIZONTAL_PADDING, (measuredHeight - PICKER_PADDING - pikerHeight).toFloat(), emptyPaint)
					canvas.restore()
				}

				TRANSITION_MODE_CHILD -> {
					val pY = (top + (bottom - top) shr 1).toFloat()
					val pX = HORIZONTAL_PADDING + pickerWidth * transitionParams!!.xPercentage
					val dX = (if (transitionParams!!.xPercentage > 0.5f) pickerWidth * transitionParams!!.xPercentage else pickerWidth * (1f - transitionParams!!.xPercentage)) * transitionParams!!.progress
					canvas.save()
					canvas.clipRect(pX - dX, top.toFloat(), pX + dX, bottom.toFloat())
					emptyPaint.alpha = (transitionParams!!.progress * 255).toInt()
					canvas.scale(transitionParams!!.progress, 1f, pX, pY)
					canvas.drawBitmap(bottomChartBitmap!!, HORIZONTAL_PADDING, (measuredHeight - PICKER_PADDING - pikerHeight).toFloat(), emptyPaint)
					canvas.restore()
				}

				else -> {
					emptyPaint.alpha = (transitionAlpha * 255).toInt()
					canvas.drawBitmap(bottomChartBitmap!!, HORIZONTAL_PADDING, (measuredHeight - PICKER_PADDING - pikerHeight).toFloat(), emptyPaint)
				}
			}
		}

		if (transitionMode == TRANSITION_MODE_PARENT) {
			return
		}

		canvas.drawRect(HORIZONTAL_PADDING, top.toFloat(), (start + DP_12).toFloat(), bottom.toFloat(), inactiveBottomChartPaint)
		canvas.drawRect((end - DP_12).toFloat(), top.toFloat(), measuredWidth - HORIZONTAL_PADDING, bottom.toFloat(), inactiveBottomChartPaint)
		canvas.drawBitmap(sharedUiComponents!!.getPickerMaskBitmap(pikerHeight, (measuredWidth - HORIZONTAL_PADDING * 2).toInt())!!, HORIZONTAL_PADDING, (measuredHeight - PICKER_PADDING - pikerHeight).toFloat(), emptyPaint)

		pickerRect.set(start, top, end, bottom)

		pickerDelegate.middlePickerArea.set(pickerRect)

		canvas.drawPath(RoundedRect(pathTmp, pickerRect.left.toFloat(), (pickerRect.top - DP_1).toFloat(), (pickerRect.left + DP_12).toFloat(), (pickerRect.bottom + DP_1).toFloat(), DP_6.toFloat(), DP_6.toFloat(), tl = true, tr = false, br = false, bl = true), pickerSelectorPaint)
		canvas.drawPath(RoundedRect(pathTmp, (pickerRect.right - DP_12).toFloat(), (pickerRect.top - DP_1).toFloat(), pickerRect.right.toFloat(), (pickerRect.bottom + DP_1).toFloat(), DP_6.toFloat(), DP_6.toFloat(), tl = false, tr = true, br = true, bl = false), pickerSelectorPaint)
		canvas.drawRect((pickerRect.left + DP_12).toFloat(), pickerRect.bottom.toFloat(), (pickerRect.right - DP_12).toFloat(), (pickerRect.bottom + DP_1).toFloat(), pickerSelectorPaint)
		canvas.drawRect((pickerRect.left + DP_12).toFloat(), (pickerRect.top - DP_1).toFloat(), (pickerRect.right - DP_12).toFloat(), pickerRect.top.toFloat(), pickerSelectorPaint)
		canvas.drawLine((pickerRect.left + DP_6).toFloat(), (pickerRect.centerY() - DP_6).toFloat(), (pickerRect.left + DP_6).toFloat(), (pickerRect.centerY() + DP_6).toFloat(), whiteLinePaint)
		canvas.drawLine((pickerRect.right - DP_6).toFloat(), (pickerRect.centerY() - DP_6).toFloat(), (pickerRect.right - DP_6).toFloat(), (pickerRect.centerY() + DP_6).toFloat(), whiteLinePaint)

		val middleCap = pickerDelegate.middleCaptured
		val r = pickerRect.bottom - pickerRect.top shr 1
		val cY = pickerRect.top + r

		if (middleCap != null) {
			// canvas.drawCircle(pickerRect.left + ((pickerRect.right - pickerRect.left) >> 1), cY, r * middleCap.aValue + HORIZONTAL_PADDING, ripplePaint);
		}
		else {
			val lCap = pickerDelegate.leftCaptured
			val rCap = pickerDelegate.rightCaptured

			if (lCap != null) {
				canvas.drawCircle((pickerRect.left + DP_5).toFloat(), cY.toFloat(), r * lCap.aValue - DP_2, ripplePaint)
			}
			if (rCap != null) {
				canvas.drawCircle((pickerRect.right - DP_5).toFloat(), cY.toFloat(), r * rCap.aValue - DP_2, ripplePaint)
			}
		}

		var cX = start

		pickerDelegate.leftPickerArea.set(cX - PICKER_CAPTURE_WIDTH, top, cX + (PICKER_CAPTURE_WIDTH shr 1), bottom)

		cX = end

		pickerDelegate.rightPickerArea.set(cX - (PICKER_CAPTURE_WIDTH shr 1), top, cX + PICKER_CAPTURE_WIDTH, bottom)
	}

	var lastTime: Long = 0

	private fun setMaxMinValue(newMaxHeight: Int, newMinHeight: Int, animated: Boolean) {
		setMaxMinValue(newMaxHeight, newMinHeight, animated, force = false, useAnimator = false)
	}

	private fun setMaxMinValue(newMaxHeight: Int, newMinHeight: Int, animated: Boolean, force: Boolean, useAnimator: Boolean) {
		@Suppress("NAME_SHADOWING") var newMaxHeight = newMaxHeight
		@Suppress("NAME_SHADOWING") var newMinHeight = newMinHeight

		val heightChanged = abs(ChartHorizontalLinesData.lookupHeight(newMaxHeight) - animateToMaxHeight) >= thresholdMaxHeight && newMaxHeight != 0

		if (!heightChanged && newMaxHeight.toFloat() == animateToMinHeight) {
			return
		}

		val newData = createHorizontalLinesData(newMaxHeight, newMinHeight)

		newMaxHeight = newData.values[newData.values.size - 1]
		newMinHeight = newData.values[0]

		if (!useAnimator) {
			var k = (currentMaxHeight - currentMinHeight) / (newMaxHeight - newMinHeight)

			if (k > 1f) {
				k = (newMaxHeight - newMinHeight) / (currentMaxHeight - currentMinHeight)
			}

			var s = 0.045f

			if (k > 0.7) {
				s = 0.1f
			}
			else if (k < 0.1) {
				s = 0.03f
			}

			var update = newMaxHeight.toFloat() != animateToMaxHeight

			if (useMinHeight && newMinHeight.toFloat() != animateToMinHeight) {
				update = true
			}

			if (update) {
				maxValueAnimator?.removeAllListeners()
				maxValueAnimator?.cancel()

				startFromMaxH = currentMaxHeight
				startFromMinH = currentMinHeight
				startFromMax = 0f
				startFromMin = 0f
				minMaxUpdateStep = s
			}
		}

		animateToMaxHeight = newMaxHeight.toFloat()
		animateToMinHeight = newMinHeight.toFloat()

		measureHeightThreshold()

		val t = System.currentTimeMillis()

		//  debounce
		if (t - lastTime < 320 && !force) {
			return
		}

		lastTime = t

		alphaAnimator?.removeAllListeners()
		alphaAnimator?.cancel()

		if (!animated) {
			currentMaxHeight = newMaxHeight.toFloat()
			currentMinHeight = newMinHeight.toFloat()
			horizontalLines.clear()
			horizontalLines.add(newData)
			newData.alpha = 255
			return
		}

		horizontalLines.add(newData)

		if (useAnimator) {
			maxValueAnimator?.removeAllListeners()
			maxValueAnimator?.cancel()

			minMaxUpdateStep = 0f

			val animatorSet = AnimatorSet()
			animatorSet.playTogether(createAnimator(currentMaxHeight, newMaxHeight.toFloat(), heightUpdateListener))

			if (useMinHeight) {
				animatorSet.playTogether(createAnimator(currentMinHeight, newMinHeight.toFloat(), minHeightUpdateListener))
			}

			maxValueAnimator = animatorSet

			maxValueAnimator?.start()
		}

		val n = horizontalLines.size

		for (i in 0 until n) {
			val a = horizontalLines[i]

			if (a !== newData) {
				a.fixedAlpha = a.alpha
			}
		}

		alphaAnimator = createAnimator(0f, 255f) {
			newData.alpha = (it.animatedValue as Float).toInt()

			for (a in horizontalLines) {
				if (a !== newData) {
					a.alpha = (a.fixedAlpha / 255f * (255 - newData.alpha)).toInt()
				}
			}

			invalidate()
		}

		alphaAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				horizontalLines.clear()
				horizontalLines.add(newData)
			}
		})

		alphaAnimator?.start()
	}

	protected open fun createHorizontalLinesData(newMaxHeight: Int, newMinHeight: Int): ChartHorizontalLinesData {
		return ChartHorizontalLinesData(newMaxHeight, newMinHeight, useMinHeight)
	}

	fun createAnimator(f1: Float, f2: Float, l: AnimatorUpdateListener?): ValueAnimator {
		val a = ValueAnimator.ofFloat(f1, f2)
		a.duration = 400L
		a.interpolator = INTERPOLATOR
		a.addUpdateListener(l)
		return a
	}

	var lastX = 0
	var lastY = 0
	private var capturedX = 0
	private var capturedY = 0
	private var capturedTime: Long = 0
	protected var canCaptureChartSelection = false

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (chartData == null) {
			return false
		}

		if (!enabled) {
			pickerDelegate.uncapture(event, event.actionIndex)
			parent.requestDisallowInterceptTouchEvent(false)
			chartCaptured = false
			return false
		}

		var x = event.getX(event.actionIndex).toInt()
		var y = event.getY(event.actionIndex).toInt()

		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				capturedTime = System.currentTimeMillis()

				parent.requestDisallowInterceptTouchEvent(true)

				val captured = pickerDelegate.capture(x, y, event.actionIndex)

				if (captured) {
					return true
				}

				lastX = x
				capturedX = lastX

				lastY = y
				capturedY = lastY

				if (chartArea.contains(x.toFloat(), y.toFloat())) {
					if (selectedIndex < 0 || !animateLegendTo) {
						chartCaptured = true
						selectXOnChart(x, y)
					}

					return true
				}

				return false
			}

			MotionEvent.ACTION_POINTER_DOWN -> {
				return pickerDelegate.capture(x, y, event.actionIndex)
			}

			MotionEvent.ACTION_MOVE -> {
				val dx = x - lastX
				val dy = y - lastY

				if (pickerDelegate.captured()) {
					val rez = pickerDelegate.move(x, y, event.actionIndex)

					if (event.pointerCount > 1) {
						x = event.getX(1).toInt()
						y = event.getY(1).toInt()
						pickerDelegate.move(x, y, 1)
					}

					parent.requestDisallowInterceptTouchEvent(rez)

					return true
				}

				if (chartCaptured) {
					val disable = if (canCaptureChartSelection && System.currentTimeMillis() - capturedTime > 200) {
						true
					}
					else {
						abs(dx) > abs(dy) || abs(dy) < touchSlop
					}

					lastX = x
					lastY = y

					parent.requestDisallowInterceptTouchEvent(disable)

					selectXOnChart(x, y)
				}
				else if (chartArea.contains(capturedX.toFloat(), capturedY.toFloat())) {
					val dxCaptured = capturedX - x
					val dyCaptured = capturedY - y

					if (sqrt((dxCaptured * dxCaptured + dyCaptured * dyCaptured).toDouble()) > touchSlop || System.currentTimeMillis() - capturedTime > 200) {
						chartCaptured = true
						selectXOnChart(x, y)
					}
				}

				return true
			}

			MotionEvent.ACTION_POINTER_UP -> {
				pickerDelegate.uncapture(event, event.actionIndex)
				return true
			}

			MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
				if (pickerDelegate.uncapture(event, event.actionIndex)) {
					return true
				}

				if (chartArea.contains(capturedX.toFloat(), capturedY.toFloat()) && !chartCaptured) {
					animateLegend(false)
				}

				pickerDelegate.uncapture()
				updateLineSignature()
				parent.requestDisallowInterceptTouchEvent(false)

				chartCaptured = false

				onActionUp()
				invalidate()

				var min = 0

				if (useMinHeight) {
					min = findMinValue(startXIndex, endXIndex)
				}

				setMaxMinValue(findMaxValue(startXIndex, endXIndex), min, animated = true, force = true, useAnimator = false)

				return true
			}
		}
		return false
	}

	protected open fun onActionUp() {
		// stub
	}

	protected open fun selectXOnChart(x: Int, y: Int) {
		val oldSelectedX = selectedIndex

		if (chartData == null) {
			return
		}

		val offset = chartFullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING
		val xP = (offset + x) / chartFullWidth

		selectedCoordinate = xP

		if (xP < 0) {
			selectedIndex = 0
			selectedCoordinate = 0f
		}
		else if (xP > 1) {
			selectedIndex = chartData!!.x.size - 1
			selectedCoordinate = 1f
		}
		else {
			selectedIndex = chartData!!.findIndex(startXIndex, endXIndex, xP)

			if (selectedIndex + 1 < chartData!!.xPercentage.size) {
				val dx = abs(chartData!!.xPercentage[selectedIndex] - xP)
				val dx2 = abs(chartData!!.xPercentage[selectedIndex + 1] - xP)

				if (dx2 < dx) {
					selectedIndex++
				}
			}
		}

		if (selectedIndex > endXIndex) {
			selectedIndex = endXIndex
		}

		if (selectedIndex < startXIndex) {
			selectedIndex = startXIndex
		}

		if (oldSelectedX != selectedIndex) {
			legendShowing = true
			animateLegend(true)
			moveLegend(offset)

			dateSelectionListener?.onDateSelected(selectedDate)

			runSmoothHaptic()
			invalidate()
		}
	}

	protected fun runSmoothHaptic() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

			if (vibrationEffect == null) {
				val vibrationWaveFormDurationPattern = longArrayOf(0, 2)
				vibrationEffect = VibrationEffect.createWaveform(vibrationWaveFormDurationPattern, -1)
			}

			vibrator.cancel()
			vibrator.vibrate(vibrationEffect)
		}
	}

	var animateLegendTo = false

	fun animateLegend(show: Boolean) {
		moveLegend()

		if (animateLegendTo == show) {
			return
		}

		animateLegendTo = show

		selectionAnimator?.removeAllListeners()
		selectionAnimator?.cancel()

		selectionAnimator = createAnimator(selectionA, if (show) 1f else 0f, selectionAnimatorListener).setDuration(200)
		selectionAnimator?.addListener(selectorAnimatorEndListener)
		selectionAnimator?.start()
	}

	fun moveLegend(offset: Float = chartFullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING) {
		val chartData = chartData ?: return

		if (selectedIndex == -1 || !legendShowing) {
			return
		}

		val legendSignatureView = legendSignatureView ?: return

		legendSignatureView.setData(selectedIndex, chartData.x[selectedIndex], lines as ArrayList<LineViewData>, false)
		legendSignatureView.visible()
		legendSignatureView.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.AT_MOST))

		var lXPoint = chartData.xPercentage[selectedIndex] * chartFullWidth - offset

		if (lXPoint > (chartStart + chartWidth) / 2f) {
			lXPoint -= (legendSignatureView.width + DP_5).toFloat()
		}
		else {
			lXPoint += DP_5.toFloat()
		}

		if (lXPoint < 0) {
			lXPoint = 0f
		}
		else if (lXPoint + legendSignatureView.measuredWidth > measuredWidth) {
			lXPoint = (measuredWidth - legendSignatureView.measuredWidth).toFloat()
		}

		legendSignatureView.translationX = lXPoint
	}

	open fun findMaxValue(startXIndex: Int, endXIndex: Int): Int {
		val linesSize = lines.size
		var maxValue = 0

		for (j in 0 until linesSize) {
			if (!lines[j].enabled) {
				continue
			}

			val lineMax = lines[j].line.segmentTree!!.rMaxQ(startXIndex, endXIndex)

			if (lineMax > maxValue) {
				maxValue = lineMax
			}
		}

		return maxValue
	}

	open fun findMinValue(startXIndex: Int, endXIndex: Int): Int {
		val linesSize = lines.size
		var minValue = Int.MAX_VALUE

		for (j in 0 until linesSize) {
			if (!lines[j].enabled) {
				continue
			}

			val lineMin = lines[j].line.segmentTree!!.rMinQ(startXIndex, endXIndex)

			if (lineMin < minValue) {
				minValue = lineMin
			}
		}

		return minValue
	}

	open fun setData(chartData: T?) {
		if (this.chartData !== chartData) {
			invalidate()
			lines.clear()

			if (chartData?.lines != null) {
				for (i in chartData.lines.indices) {
					lines.add(createLineViewData(chartData.lines[i]))
				}
			}

			clearSelection()

			this.chartData = chartData

			if (chartData != null) {
				if (chartData.x[0] == 0L) {
					pickerDelegate.pickerStart = 0f
					pickerDelegate.pickerEnd = 1f
				}
				else {
					pickerDelegate.minDistance = minDistance

					if (pickerDelegate.pickerEnd - pickerDelegate.pickerStart < pickerDelegate.minDistance) {
						pickerDelegate.pickerStart = pickerDelegate.pickerEnd - pickerDelegate.minDistance

						if (pickerDelegate.pickerStart < 0) {
							pickerDelegate.pickerStart = 0f
							pickerDelegate.pickerEnd = 1f
						}
					}
				}
			}
		}

		measureSizes()

		if (chartData != null) {
			updateIndexes()
			val min = if (useMinHeight) findMinValue(startXIndex, endXIndex) else 0

			setMaxMinValue(findMaxValue(startXIndex, endXIndex), min, false)

			pickerMaxHeight = 0f
			pickerMinHeight = Int.MAX_VALUE.toFloat()

			initPickerMaxHeight()

			legendSignatureView?.setSize(lines.size)

			invalidatePickerChart = true

			updateLineSignature()
		}
		else {
			pickerDelegate.pickerStart = 0.7f
			pickerDelegate.pickerEnd = 1f

			pickerMinHeight = 0f
			pickerMaxHeight = pickerMinHeight

			horizontalLines.clear()

			maxValueAnimator?.cancel()

			alphaAnimator?.removeAllListeners()
			alphaAnimator?.cancel()
		}
	}

	protected open val minDistance: Float
		get() {
			if (chartData == null) {
				return 0.1f
			}

			val n = chartData!!.x.size

			if (n < 5) {
				return 1f
			}

			val r = 5f / n

			return max(r, 0.1f)
		}

	protected open fun initPickerMaxHeight() {
		for (l in lines) {
			if (l.enabled && l.line.maxValue > pickerMaxHeight) {
				pickerMaxHeight = l.line.maxValue.toFloat()
			}

			if (l.enabled && l.line.minValue < pickerMinHeight) {
				pickerMinHeight = l.line.minValue.toFloat()
			}

			if (pickerMaxHeight == pickerMinHeight) {
				pickerMaxHeight++
				pickerMinHeight--
			}
		}
	}

	abstract fun createLineViewData(line: ChartData.Line): L

	override fun onPickerDataChanged() {
		onPickerDataChanged(animated = true, force = false, useAnimator = false)
	}

	open fun onPickerDataChanged(animated: Boolean, force: Boolean, useAnimator: Boolean) {
		if (chartData == null) {
			return
		}

		chartFullWidth = chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart)

		updateIndexes()

		val min = if (useMinHeight) findMinValue(startXIndex, endXIndex) else 0

		setMaxMinValue(findMaxValue(startXIndex, endXIndex), min, animated, force, useAnimator)

		if (legendShowing && !force) {
			animateLegend(false)
			moveLegend(chartFullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING)
		}

		invalidate()
	}

	override fun onPickerJumpTo(start: Float, end: Float, force: Boolean) {
		val chartData = chartData ?: return

		if (force) {
			val startXIndex = chartData.findStartIndex(max(start, 0f))
			val endXIndex = chartData.findEndIndex(startXIndex, min(end, 1f))
			setMaxMinValue(findMaxValue(startXIndex, endXIndex), findMinValue(startXIndex, endXIndex), animated = true, force = true, useAnimator = false)
			animateLegend(false)
		}
		else {
			updateIndexes()
			invalidate()
		}
	}

	protected fun updateIndexes() {
		val chartData = chartData ?: return

		startXIndex = chartData.findStartIndex(max(pickerDelegate.pickerStart, 0f))
		endXIndex = chartData.findEndIndex(startXIndex, min(pickerDelegate.pickerEnd, 1f))

		if (endXIndex < startXIndex) {
			endXIndex = startXIndex
		}

		if (chartHeaderView != null) {
			chartHeaderView?.setDates(chartData.x[startXIndex], chartData.x[endXIndex])
		}

		updateLineSignature()
	}

	private fun updateLineSignature() {
		if (chartWidth == 0f) {
			return
		}

		val chartData = chartData ?: return
		val d = chartFullWidth * chartData.oneDayPercentage
		val k = chartWidth / d
		val step = (k / BOTTOM_SIGNATURE_COUNT).toInt()

		updateDates(step)
	}

	private fun updateDates(step: Int) {
		@Suppress("NAME_SHADOWING") var step = step

		if (currentBottomSignatures == null || step >= currentBottomSignatures!!.stepMax || step <= currentBottomSignatures!!.stepMin) {
			step = Integer.highestOneBit(step) shl 1

			if (currentBottomSignatures != null && currentBottomSignatures!!.step == step) {
				return
			}

			alphaBottomAnimator?.removeAllListeners()
			alphaBottomAnimator?.cancel()

			val stepMax = (step + step * 0.2).toInt()
			val stepMin = (step - step * 0.2).toInt()

			val data = ChartBottomSignatureData(step, stepMax, stepMin)
			data.alpha = 255

			if (currentBottomSignatures == null) {
				currentBottomSignatures = data
				bottomSignatureDate.add(data)
				return
			}

			currentBottomSignatures = data
			tmpN = bottomSignatureDate.size

			for (i in 0 until tmpN) {
				val a = bottomSignatureDate[i]
				a.fixedAlpha = a.alpha
			}

			bottomSignatureDate.add(data)

			if (bottomSignatureDate.size > 2) {
				bottomSignatureDate.removeAt(0)
			}

			alphaBottomAnimator = createAnimator(0f, 1f) {
				val alpha = it.animatedValue as Float

				for (a in bottomSignatureDate) {
					if (a === data) {
						data.alpha = (255 * alpha).toInt()
					}
					else {
						a.alpha = ((1f - alpha) * a.fixedAlpha).toInt()
					}
				}

				invalidate()
			}.setDuration(200)

			alphaBottomAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					super.onAnimationEnd(animation)
					bottomSignatureDate.clear()
					bottomSignatureDate.add(data)
				}
			})

			alphaBottomAnimator?.start()
		}
	}

	open fun onCheckChanged() {
		onPickerDataChanged(animated = true, force = true, useAnimator = true)

		tmpN = lines.size
		tmpI = 0

		while (tmpI < tmpN) {
			val lineViewData: LineViewData = lines[tmpI]

			if (lineViewData.enabled) {
				lineViewData.animatorOut?.cancel()
			}

			if (!lineViewData.enabled) {
				lineViewData.animatorIn?.cancel()
			}

			if (lineViewData.enabled && lineViewData.alpha != 1f) {
				if (lineViewData.animatorIn?.isRunning == true) {
					tmpI++
					continue
				}

				lineViewData.animatorIn = createAnimator(lineViewData.alpha, 1f) {
					lineViewData.alpha = it.animatedValue as Float
					invalidatePickerChart = true
					invalidate()
				}

				lineViewData.animatorIn?.start()
			}

			if (!lineViewData.enabled && lineViewData.alpha != 0f) {
				if (lineViewData.animatorOut?.isRunning == true) {
					tmpI++
					continue
				}

				lineViewData.animatorOut = createAnimator(lineViewData.alpha, 0f) {
					lineViewData.alpha = it.animatedValue as Float
					invalidatePickerChart = true
					invalidate()
				}

				lineViewData.animatorOut?.start()
			}

			tmpI++
		}

		updatePickerMinMaxHeight()

		if (legendShowing) {
			legendSignatureView?.setData(selectedIndex, chartData!!.x[selectedIndex], lines as ArrayList<LineViewData>, true)
		}
	}

	protected open fun updatePickerMinMaxHeight() {
		if (!ANIMATE_PICKER_SIZES) {
			return
		}

		var max = 0
		var min = Int.MAX_VALUE

		for (l in lines) {
			if (l.enabled && l.line.maxValue > max) {
				max = l.line.maxValue
			}

			if (l.enabled && l.line.minValue < min) {
				min = l.line.minValue
			}
		}

		if (min != Int.MAX_VALUE && min.toFloat() != animatedToPickerMinHeight || max > 0 && max.toFloat() != animatedToPickerMaxHeight) {
			animatedToPickerMaxHeight = max.toFloat()

			pickerAnimator?.cancel()

			val animatorSet = AnimatorSet()
			animatorSet.playTogether(createAnimator(pickerMaxHeight, animatedToPickerMaxHeight, pickerHeightUpdateListener), createAnimator(pickerMinHeight, animatedToPickerMinHeight, pickerMinHeightUpdateListener))

			pickerAnimator = animatorSet
			pickerAnimator?.start()
		}
	}

	fun saveState(outState: Bundle?) {
		if (outState == null) {
			return
		}

		outState.putFloat("chart_start", pickerDelegate.pickerStart)
		outState.putFloat("chart_end", pickerDelegate.pickerEnd)

		val n = lines.size
		val bArray = BooleanArray(n)

		for (i in 0 until n) {
			bArray[i] = lines[i].enabled
		}

		outState.putBooleanArray("chart_line_enabled", bArray)
	}

	private var chartHeaderView: ChartHeaderView? = null

	init {
		init()
		touchSlop = ViewConfiguration.get(context).scaledTouchSlop
	}

	fun setHeader(chartHeaderView: ChartHeaderView?) {
		this.chartHeaderView = chartHeaderView
	}

	val selectedDate: Long
		get() = if (selectedIndex < 0) {
			-1
		}
		else {
			chartData?.x?.get(selectedIndex) ?: -1
		}

	fun clearSelection() {
		selectedIndex = -1
		legendShowing = false
		animateLegendTo = false
		legendSignatureView?.gone()
		selectionA = 0f
	}

	fun selectDate(activeZoom: Long) {
		selectedIndex = Arrays.binarySearch(chartData!!.x, activeZoom)
		legendShowing = true
		legendSignatureView?.visible()
		selectionA = 1f
		moveLegend(chartFullWidth * pickerDelegate.pickerStart - HORIZONTAL_PADDING)
		performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
	}

	val startDate: Long
		get() = chartData?.x?.get(startXIndex) ?: 0

	val endDate: Long
		get() = chartData?.x?.get(endXIndex) ?: 0

	open fun updatePicker(chartData: ChartData, d: Long) {
		val n = chartData.x.size
		val startOfDay = d - d % 86400000L
		val endOfDay = startOfDay + 86400000L - 1
		var startIndex = 0
		var endIndex = 0

		for (i in 0 until n) {
			if (startOfDay > chartData.x[i]) {
				startIndex = i
			}

			if (endOfDay > chartData.x[i]) {
				endIndex = i
			}
		}

		pickerDelegate.pickerStart = chartData.xPercentage[startIndex]
		pickerDelegate.pickerEnd = chartData.xPercentage[endIndex]
	}

	fun interface DateSelectionListener {
		fun onDateSelected(date: Long)
	}

	class SharedUiComponents {
		private var pickerRoundBitmap: Bitmap? = null
		private val rectF = RectF()
		private val xRefP = Paint(Paint.ANTI_ALIAS_FLAG)
		var k = 0
		private var invalidate = true

		init {
			xRefP.color = 0
			xRefP.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
		}

		fun getPickerMaskBitmap(h: Int, w: Int): Bitmap? {
			if (h + w shl 10 != k || invalidate) {
				invalidate = false
				k = h + w shl 10
				pickerRoundBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
				val canvas = Canvas(pickerRoundBitmap!!)
				rectF.set(0f, 0f, w.toFloat(), h.toFloat())
				canvas.drawColor(ApplicationLoader.applicationContext.getColor(R.color.background))
				canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), xRefP)
			}

			return pickerRoundBitmap
		}

		fun invalidate() {
			invalidate = true
		}
	}

	open fun fillTransitionParams(params: TransitionParams?) {
		// stub
	}

	companion object {
		private const val LINE_WIDTH = 1f
		private val SELECTED_LINE_WIDTH = AndroidUtilities.dpf2(1.5f)
		private val SIGNATURE_TEXT_SIZE = AndroidUtilities.dpf2(12f)
		private val BOTTOM_SIGNATURE_OFFSET = AndroidUtilities.dp(10f)
		private val BOTTOM_SIGNATURE_TEXT_HEIGHT = AndroidUtilities.dp(14f)
		private val DP_1 = AndroidUtilities.dp(1f)
		private val DP_12 = AndroidUtilities.dp(12f)
		private val DP_2 = AndroidUtilities.dp(2f)
		private val DP_5 = AndroidUtilities.dp(5f)
		private val DP_6 = AndroidUtilities.dp(6f)
		private val LANDSCAPE_END_PADDING = AndroidUtilities.dp(16f)
		private val PICKER_CAPTURE_WIDTH = AndroidUtilities.dp(24f)
		const val ANIMATE_PICKER_SIZES = true
		val PICKER_PADDING = AndroidUtilities.dp(16f)
		val USE_LINES = Build.VERSION.SDK_INT < Build.VERSION_CODES.P
		val HORIZONTAL_PADDING = AndroidUtilities.dpf2(16f)
		val BOTTOM_SIGNATURE_START_ALPHA = AndroidUtilities.dp(10f)
		val SIGNATURE_TEXT_HEIGHT = AndroidUtilities.dp(18f)
		const val TRANSITION_MODE_ALPHA_ENTER = 3
		const val TRANSITION_MODE_CHILD = 1
		const val TRANSITION_MODE_NONE = 0
		const val TRANSITION_MODE_PARENT = 2
		var INTERPOLATOR = FastOutSlowInInterpolator()
		private const val BOTTOM_SIGNATURE_COUNT = 6

		fun RoundedRect(path: Path, left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, tl: Boolean, tr: Boolean, br: Boolean, bl: Boolean): Path {
			@Suppress("NAME_SHADOWING") var rx = rx
			@Suppress("NAME_SHADOWING") var ry = ry

			path.reset()

			if (rx < 0) {
				rx = 0f
			}

			if (ry < 0) {
				ry = 0f
			}

			val width = right - left
			val height = bottom - top

			if (rx > width / 2) {
				rx = width / 2
			}

			if (ry > height / 2) {
				ry = height / 2
			}

			val widthMinusCorners = width - 2 * rx
			val heightMinusCorners = height - 2 * ry

			path.moveTo(right, top + ry)

			if (tr) {
				path.rQuadTo(0f, -ry, -rx, -ry)
			}
			else {
				path.rLineTo(0f, -ry)
				path.rLineTo(-rx, 0f)
			}

			path.rLineTo(-widthMinusCorners, 0f)

			if (tl) {
				path.rQuadTo(-rx, 0f, -rx, ry)
			}
			else {
				path.rLineTo(-rx, 0f)
				path.rLineTo(0f, ry)
			}

			path.rLineTo(0f, heightMinusCorners)

			if (bl) {
				path.rQuadTo(0f, ry, rx, ry)
			}
			else {
				path.rLineTo(0f, ry)
				path.rLineTo(rx, 0f)
			}

			path.rLineTo(widthMinusCorners, 0f)

			if (br) {
				path.rQuadTo(rx, 0f, rx, -ry)
			}
			else {
				path.rLineTo(rx, 0f)
				path.rLineTo(0f, -ry)
			}

			path.rLineTo(0f, -heightMinusCorners)
			path.close()

			return path
		}
	}
}
