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
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.ui.Charts.data.ChartData
import org.telegram.ui.Charts.view_data.ChartHeaderView
import org.telegram.ui.Charts.view_data.LineViewData
import org.telegram.ui.Charts.view_data.StackLinearViewData
import org.telegram.ui.Charts.view_data.TransitionParams
import org.telegram.ui.Components.FlatCheckBox
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.statistics.ChartViewData
import java.util.Arrays

abstract class BaseChartCell @SuppressLint("ClickableViewAccessibility") constructor(context: Context, type: Int, sharedUi: BaseChartView.SharedUiComponents?) : FrameLayout(context) {
	val chartView: BaseChartView<ChartData, LineViewData>
	val zoomedChartView: BaseChartView<ChartData, LineViewData>
	private var chartHeaderView: ChartHeaderView
	var progressView: RadialProgressView
	private var errorTextView: TextView
	var checkboxContainer: ViewGroup
	var checkBoxes = ArrayList<CheckBoxHolder>()
	var data: ChartViewData? = null
	var chartType: Int

	init {
		setWillNotDraw(false)

		chartType = type

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL

		checkboxContainer = object : FrameLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				var currentW = 0
				var currentH = 0
				val n = childCount
				val firstH = if (n > 0) getChildAt(0).measuredHeight else 0

				for (i in 0 until n) {
					if (currentW + getChildAt(i).measuredWidth > measuredWidth) {
						currentW = 0
						currentH += getChildAt(i).measuredHeight
					}

					currentW += getChildAt(i).measuredWidth
				}

				setMeasuredDimension(measuredWidth, firstH + currentH + AndroidUtilities.dp(16f))
			}

			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				var currentW = 0
				var currentH = 0
				val n = childCount

				for (i in 0 until n) {
					if (currentW + getChildAt(i).measuredWidth > measuredWidth) {
						currentW = 0
						currentH += getChildAt(i).measuredHeight
					}

					getChildAt(i).layout(currentW, currentH, currentW + getChildAt(i).measuredWidth, currentH + getChildAt(i).measuredHeight)
					currentW += getChildAt(i).measuredWidth
				}
			}
		}

		chartHeaderView = ChartHeaderView(getContext())
		chartHeaderView.back.setOnTouchListener(RecyclerListView.FocusableOnTouchListener())
		chartHeaderView.back.setOnClickListener { zoomOut(true) }

		when (type) {
			1 -> {
				chartView = DoubleLinearChartView(getContext()) as BaseChartView<ChartData, LineViewData>
				zoomedChartView = DoubleLinearChartView(getContext()) as BaseChartView<ChartData, LineViewData>
				zoomedChartView.legendSignatureView?.useHour = true
			}

			2 -> {
				chartView = StackBarChartView(getContext()) as BaseChartView<ChartData, LineViewData>
				zoomedChartView = StackBarChartView(getContext()) as BaseChartView<ChartData, LineViewData>
				zoomedChartView.legendSignatureView?.useHour = true
			}

			3 -> {
				chartView = BarChartView(getContext()) as BaseChartView<ChartData, LineViewData>
				zoomedChartView = LinearChartView(getContext())
				zoomedChartView.legendSignatureView?.useHour = true
			}

			4 -> {
				chartView = StackLinearChartView<StackLinearViewData>(getContext()) as BaseChartView<ChartData, LineViewData>
				chartView.legendSignatureView?.showPercentage = true
				zoomedChartView = PieChartView(getContext()) as BaseChartView<ChartData, LineViewData>
			}

			else -> {
				chartView = LinearChartView(getContext())
				zoomedChartView = LinearChartView(getContext())
				zoomedChartView.legendSignatureView?.useHour = true
			}
		}

		val frameLayout = FrameLayout(context)

		chartView.sharedUiComponents = sharedUi
		zoomedChartView.sharedUiComponents = sharedUi

		progressView = RadialProgressView(context)

		frameLayout.addView(chartView)
		frameLayout.addView(chartView.legendSignatureView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		frameLayout.addView(zoomedChartView)
		frameLayout.addView(zoomedChartView.legendSignatureView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		frameLayout.addView(progressView, LayoutHelper.createFrame(44, 44f, Gravity.CENTER, 0f, 0f, 0f, 60f))

		errorTextView = TextView(context)
		errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)

		frameLayout.addView(errorTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 0f, 0f, 30f))

		progressView.gone()

		errorTextView.setTextColor(context.getColor(R.color.dark_gray))

		chartView.dateSelectionListener = BaseChartView.DateSelectionListener {
			zoomCanceled()
			chartView.legendSignatureView?.showProgress(show = false, force = false)
		}

		chartView.legendSignatureView?.showProgress(show = false, force = false)
		chartView.legendSignatureView?.setOnTouchListener(RecyclerListView.FocusableOnTouchListener())
		chartView.legendSignatureView?.setOnClickListener { onZoomed() }

		zoomedChartView.legendSignatureView?.setOnClickListener {
			zoomedChartView.animateLegend(false)
		}

		chartView.visible()
		zoomedChartView.invisible()

		chartView.setHeader(chartHeaderView)

		linearLayout.addView(chartHeaderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52f))
		linearLayout.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
		linearLayout.addView(checkboxContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.NO_GRAVITY, 16f, 0f, 16f, 0f))

		if (chartType == 4) {
			frameLayout.clipChildren = false
			frameLayout.clipToPadding = false
			linearLayout.clipChildren = false
			linearLayout.clipToPadding = false
		}

		addView(linearLayout)
	}

	abstract fun onZoomed()
	abstract fun zoomCanceled()
	abstract fun loadData(viewData: ChartViewData)

	fun zoomChart(skipTransition: Boolean) {
		val d = chartView.selectedDate
		val childData = data?.childChartData ?: return

		if (!skipTransition || zoomedChartView.visibility != VISIBLE) {
			zoomedChartView.updatePicker(childData, d)
		}

		zoomedChartView.setData(childData)

		if (data!!.chartData!!.lines.size > 1) {
			var enabledCount = 0

			for (i in data!!.chartData!!.lines.indices) {
				var found = false

				for (j in childData.lines.indices) {
					val line = childData.lines[j]

					if (line.id == data!!.chartData!!.lines[i].id) {
						val check = checkBoxes[i].checkBox.isChecked()

						zoomedChartView.lines[j].enabled = check
						zoomedChartView.lines[j].alpha = if (check) 1f else 0f

						checkBoxes[i].checkBox.enabled = true
						checkBoxes[i].checkBox.animate().alpha(1f).start()

						if (check) {
							enabledCount++
						}

						found = true

						break
					}
				}

				if (!found) {
					checkBoxes[i].checkBox.enabled = false
					checkBoxes[i].checkBox.animate().alpha(0f).start()
				}
			}

			if (enabledCount == 0) {
				for (i in data!!.chartData!!.lines.indices) {
					checkBoxes[i].checkBox.enabled = true
					checkBoxes[i].checkBox.animate().alpha(1f).start()
				}

				return
			}
		}

		data!!.activeZoom = d

		chartView.legendSignatureView?.alpha = 0f
		chartView.selectionA = 0f
		chartView.legendShowing = false
		chartView.animateLegendTo = false

		zoomedChartView.updateColors()

		if (!skipTransition) {
			zoomedChartView.clearSelection()
			chartHeaderView.zoomTo(d, true)
		}

		zoomedChartView.setHeader(chartHeaderView)
		chartView.setHeader(null)

		if (skipTransition) {
			chartView.invisible()
			zoomedChartView.visible()
			chartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE
			zoomedChartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE
			chartView.enabled = false
			zoomedChartView.enabled = true
			chartHeaderView.zoomTo(d, false)
		}
		else {
			val animator = createTransitionAnimator(d, true)

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					chartView.invisible()
					chartView.enabled = false
					zoomedChartView.enabled = true
					chartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE
					zoomedChartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE
					(context as Activity).window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
				}
			})

			animator.start()
		}
	}

	private fun zoomOut(animated: Boolean) {
		if (data?.chartData?.x == null) {
			return
		}

		chartHeaderView.zoomOut(chartView, animated)
		chartView.legendSignatureView?.chevron?.alpha = 1f
		zoomedChartView.setHeader(null)

		val d = chartView.selectedDate

		data!!.activeZoom = 0
		chartView.visible()

		zoomedChartView.clearSelection()
		zoomedChartView.setHeader(null)

		chartView.setHeader(chartHeaderView)

		if (!animated) {
			zoomedChartView.invisible()
			chartView.enabled = true
			zoomedChartView.enabled = false
			chartView.invalidate()

			(context as Activity).window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

			for (checkbox in checkBoxes) {
				checkbox.checkBox.alpha = 1f
				checkbox.checkBox.enabled = true
			}
		}
		else {
			val animator = createTransitionAnimator(d, false)

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					zoomedChartView.invisible()
					chartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE
					zoomedChartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE
					chartView.enabled = true
					zoomedChartView.enabled = false

					if (chartView !is StackLinearChartView<*>) {
						chartView.legendShowing = true
						chartView.moveLegend()
						chartView.animateLegend(true)
						chartView.invalidate()
					}
					else {
						chartView.legendShowing = false
						chartView.clearSelection()
					}

					(context as Activity).window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
				}
			})

			for (checkbox in checkBoxes) {
				checkbox.checkBox.animate().alpha(1f).start()
				checkbox.checkBox.enabled = true
			}

			animator.start()
		}
	}

	private fun createTransitionAnimator(d: Long, `in`: Boolean): ValueAnimator {
		(context as Activity).window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
		chartView.enabled = false
		zoomedChartView.enabled = false
		chartView.transitionMode = BaseChartView.TRANSITION_MODE_PARENT
		zoomedChartView.transitionMode = BaseChartView.TRANSITION_MODE_CHILD

		val params = TransitionParams()
		params.pickerEndOut = chartView.pickerDelegate.pickerEnd
		params.pickerStartOut = chartView.pickerDelegate.pickerStart
		params.date = d

		var dateIndex = Arrays.binarySearch(data!!.chartData!!.x, d)

		if (dateIndex < 0) {
			dateIndex = data!!.chartData!!.x.size - 1
		}

		params.xPercentage = data!!.chartData!!.xPercentage[dateIndex]

		zoomedChartView.visibility = VISIBLE
		zoomedChartView.transitionParams = params

		chartView.transitionParams = params

		var max = 0
		var min = Int.MAX_VALUE

		for (i in data!!.chartData!!.lines.indices) {
			if (data!!.chartData!!.lines[i].y[dateIndex] > max) {
				max = data!!.chartData!!.lines[i].y[dateIndex]
			}

			if (data!!.chartData!!.lines[i].y[dateIndex] < min) {
				min = data!!.chartData!!.lines[i].y[dateIndex]
			}
		}

		val pYPercentage = (min.toFloat() + (max - min) - chartView.currentMinHeight) / (chartView.currentMaxHeight - chartView.currentMinHeight)

		chartView.fillTransitionParams(params)
		zoomedChartView.fillTransitionParams(params)

		val animator = ValueAnimator.ofFloat(if (`in`) 0f else 1f, if (`in`) 1f else 0f)

		animator.addUpdateListener {
			val fullWidth = chartView.chartWidth / (chartView.pickerDelegate.pickerEnd - chartView.pickerDelegate.pickerStart)
			val offset = fullWidth * chartView.pickerDelegate.pickerStart - BaseChartView.HORIZONTAL_PADDING

			params.pY = chartView.chartArea.top + (1f - pYPercentage) * chartView.chartArea.height()
			params.pX = chartView.chartFullWidth * params.xPercentage - offset
			params.progress = it.animatedValue as Float

			zoomedChartView.invalidate()
			zoomedChartView.fillTransitionParams(params)

			chartView.invalidate()
		}

		animator.duration = 400
		animator.interpolator = FastOutSlowInInterpolator()

		return animator
	}

	fun updateData(viewData: ChartViewData?, enterTransition: Boolean) {
		if (viewData == null) {
			return
		}

		chartHeaderView.setTitle(viewData.title)

		val configuration = context.resources.configuration
		val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

		chartView.isLandscape = isLandscape

		viewData.viewShowed = true
		zoomedChartView.isLandscape = isLandscape
		data = viewData

		if (viewData.isEmpty || viewData.isError) {
			progressView.gone()
			if (viewData.errorMessage != null) {
				errorTextView.text = viewData.errorMessage

				if (errorTextView.visibility == GONE) {
					errorTextView.alpha = 0f
					errorTextView.animate().alpha(1f)
				}

				errorTextView.visible()
			}

			chartView.setData(null)

			return
		}

		errorTextView.gone()

		chartView.legendSignatureView?.isTopHourChart = viewData.useHourFormat

		chartHeaderView.showDate(!viewData.useHourFormat)

		if (viewData.chartData == null && viewData.token != null) {
			progressView.alpha = 1f
			progressView.visible()
			loadData(viewData)
			chartView.setData(null)
			return
		}
		else if (!enterTransition) {
			progressView.gone()
		}

		chartView.setData(viewData.chartData)

		chartHeaderView.setUseWeekInterval(viewData.useWeekFormat)

		chartView.legendSignatureView?.setUseWeek(viewData.useWeekFormat)
		chartView.legendSignatureView?.zoomEnabled = !(data!!.zoomToken == null && chartType != 4)

		zoomedChartView.legendSignatureView?.zoomEnabled = false

		chartView.legendSignatureView?.isEnabled = chartView.legendSignatureView?.zoomEnabled ?: false

		zoomedChartView.legendSignatureView?.isEnabled = zoomedChartView.legendSignatureView?.zoomEnabled ?: false

		val n = chartView.lines.size

		checkboxContainer.removeAllViews()
		checkBoxes.clear()

		if (n > 1) {
			for (i in 0 until n) {
				val l = chartView.lines[i]
				CheckBoxHolder(i).setData(l)
			}
		}

		if (data!!.activeZoom > 0) {
			chartView.selectDate(data!!.activeZoom)
			zoomChart(true)
		}
		else {
			zoomOut(false)
			chartView.invalidate()
		}

		recolor()

		if (enterTransition) {
			chartView.transitionMode = BaseChartView.TRANSITION_MODE_ALPHA_ENTER
			val animator = ValueAnimator.ofFloat(0f, 1f)

			chartView.transitionParams = TransitionParams()
			chartView.transitionParams?.progress = 0f

			animator.addUpdateListener {
				val a = it.animatedValue as Float
				progressView.alpha = 1f - a
				chartView.transitionParams?.progress = a
				zoomedChartView.invalidate()
				chartView.invalidate()
			}

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					chartView.transitionMode = BaseChartView.TRANSITION_MODE_NONE
					progressView.gone()
				}
			})

			animator.start()
		}
	}

	fun recolor() {
		chartView.updateColors()
		chartView.invalidate()
		zoomedChartView.updateColors()
		zoomedChartView.invalidate()
		chartHeaderView.recolor()
		chartHeaderView.invalidate()

		if (data != null && data!!.chartData != null && data!!.chartData!!.lines.size > 1) {
			for (i in data!!.chartData!!.lines.indices) {
				val darkBackground = ColorUtils.calculateLuminance(context.getColor(R.color.background)) < 0.5f

				val color = if (darkBackground) data!!.chartData!!.lines[i].colorDark else data!!.chartData!!.lines[i].color

				if (i < checkBoxes.size) {
					checkBoxes[i].recolor(color)
				}
			}
		}

		progressView.setProgressColor(context.getColor(R.color.brand))
		errorTextView.setTextColor(context.getColor(R.color.dark_gray))
	}

	inner class CheckBoxHolder(val position: Int) {
		val checkBox = FlatCheckBox(context)
		var line: LineViewData? = null

		init {
			checkBox.setPadding(AndroidUtilities.dp(16f), 0, AndroidUtilities.dp(16f), 0)
			checkboxContainer.addView(checkBox)
			checkBoxes.add(this)
		}

		fun setData(l: LineViewData) {
			line = l

			checkBox.setText(l.line.name)
			checkBox.setChecked(l.enabled, false)
			checkBox.setOnTouchListener(RecyclerListView.FocusableOnTouchListener())

			checkBox.setOnClickListener {
				if (!checkBox.enabled) {
					return@setOnClickListener
				}

				var allDisabled = true
				val n = checkBoxes.size

				for (i in 0 until n) {
					if (i != position && checkBoxes[i].checkBox.enabled && checkBoxes[i].checkBox.isChecked()) {
						allDisabled = false
						break
					}
				}

				zoomCanceled()

				if (allDisabled) {
					checkBox.denied()
					return@setOnClickListener
				}

				checkBox.setChecked(!checkBox.isChecked())
				l.enabled = checkBox.isChecked()
				chartView.onCheckChanged()

				if (data!!.activeZoom > 0) {
					if (position < zoomedChartView.lines.size) {
						zoomedChartView.lines[position].enabled = checkBox.isChecked()
						zoomedChartView.onCheckChanged()
					}
				}
			}

			checkBox.setOnLongClickListener {
				if (!checkBox.enabled) {
					return@setOnLongClickListener false
				}

				zoomCanceled()

				val n = checkBoxes.size

				for (i in 0 until n) {
					checkBoxes[i].checkBox.setChecked(false)
					checkBoxes[i].line!!.enabled = false

					if (data!!.activeZoom > 0 && i < zoomedChartView.lines.size) {
						zoomedChartView.lines[i].enabled = false
					}
				}

				checkBox.setChecked(true)

				l.enabled = true

				chartView.onCheckChanged()

				if (data!!.activeZoom > 0) {
					zoomedChartView.lines[position].enabled = true
					zoomedChartView.onCheckChanged()
				}

				true
			}
		}

		fun recolor(c: Int) {
			checkBox.recolor(c)
		}
	}
}
