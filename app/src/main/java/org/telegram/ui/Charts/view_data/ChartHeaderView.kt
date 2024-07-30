/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.view_data

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Charts.BaseChartView
import org.telegram.ui.Components.LayoutHelper
import java.text.SimpleDateFormat
import java.util.Date

open class ChartHeaderView(context: Context) : FrameLayout(context) {
	private val title: TextView
	private val dates: TextView
	private val datesTmp: TextView
	val back: TextView
	private var showDate = true
	private var useWeekInterval = false
	private val zoomIcon: Drawable?
	private var textMargin: Int
	var formatter = SimpleDateFormat("d MMM yyyy")

	init {
		val textPaint = TextPaint()
		textPaint.textSize = 14f
		textPaint.typeface = Theme.TYPEFACE_BOLD

		textMargin = textPaint.measureText("00 MMM 0000 - 00 MMM 000").toInt()

		title = TextView(context)
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		title.typeface = Theme.TYPEFACE_BOLD

		addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 16f, 0f, textMargin.toFloat(), 0f))

		back = TextView(context)
		back.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		back.typeface = Theme.TYPEFACE_BOLD
		back.gravity = Gravity.START or Gravity.CENTER_VERTICAL

		addView(back, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 8f, 0f, 8f, 0f))

		dates = TextView(context)
		dates.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		dates.typeface = Theme.TYPEFACE_BOLD
		dates.gravity = Gravity.END or Gravity.CENTER_VERTICAL

		addView(dates, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL, 16f, 0f, 16f, 0f))

		datesTmp = TextView(context)
		datesTmp.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		datesTmp.typeface = Theme.TYPEFACE_BOLD
		datesTmp.gravity = Gravity.END or Gravity.CENTER_VERTICAL

		addView(datesTmp, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL, 16f, 0f, 16f, 0f))

		datesTmp.gone()

		back.gone()
		back.text = context.getString(R.string.ZoomOut)

		zoomIcon = ContextCompat.getDrawable(getContext(), R.drawable.msg_zoomout_stats)

		back.setCompoundDrawablesWithIntrinsicBounds(zoomIcon, null, null, null)
		back.compoundDrawablePadding = AndroidUtilities.dp(4f)
		back.setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(4f), AndroidUtilities.dp(8f), AndroidUtilities.dp(4f))
		back.background = Theme.getRoundRectSelectorDrawable(context.getColor(R.color.brand))

		datesTmp.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
			datesTmp.pivotX = datesTmp.measuredWidth * 0.7f
			dates.pivotX = dates.measuredWidth * 0.7f
		}

		recolor()
	}

	fun recolor() {
		title.setTextColor(context.getColor(R.color.text))
		dates.setTextColor(context.getColor(R.color.text))
		datesTmp.setTextColor(context.getColor(R.color.text))
		back.setTextColor(context.getColor(R.color.brand))
		zoomIcon?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.SRC_IN)
	}

	fun setDates(start: Long, end: Long) {
		@Suppress("NAME_SHADOWING") var end = end

		if (!showDate) {
			dates.gone()
			datesTmp.gone()
			return
		}

		if (useWeekInterval) {
			end += 86400000L * 7
		}

		val newText = if (end - start >= 86400000L) {
			formatter.format(Date(start)) + " — " + formatter.format(Date(end))
		}
		else {
			formatter.format(Date(start))
		}

		dates.text = newText
		dates.visible()
	}

	fun setTitle(s: String?) {
		title.text = s
	}

	fun zoomTo(d: Long, animate: Boolean) {
		setDates(d, d)

		back.visible()

		if (animate) {
			back.alpha = 0f
			back.scaleX = 0.3f
			back.scaleY = 0.3f
			back.pivotX = 0f
			back.pivotY = AndroidUtilities.dp(40f).toFloat()

			back.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(200).start()

			title.alpha = 1f
			title.translationX = 0f
			title.translationY = 0f
			title.scaleX = 1f
			title.scaleY = 1f
			title.pivotX = 0f
			title.pivotY = 0f

			title.animate().alpha(0f).scaleY(0.3f).scaleX(0.3f).setDuration(200).start()
		}
		else {
			back.alpha = 1f
			back.translationX = 0f
			back.translationY = 0f
			back.scaleX = 1f
			back.scaleY = 1f
			title.alpha = 0f
		}
	}

	fun zoomOut(chartView: BaseChartView<*, *>, animated: Boolean) {
		setDates(chartView.startDate, chartView.endDate)

		if (animated) {
			title.alpha = 0f
			title.scaleX = 0.3f
			title.scaleY = 0.3f
			title.pivotX = 0f
			title.pivotY = 0f

			title.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(200).start()

			back.alpha = 1f
			back.translationX = 0f
			back.translationY = 0f
			back.scaleX = 1f
			back.scaleY = 1f
			back.pivotY = AndroidUtilities.dp(40f).toFloat()

			back.animate().alpha(0f).scaleY(0.3f).scaleX(0.3f).setDuration(200).start()
		}
		else {
			title.alpha = 1f
			title.scaleX = 1f
			title.scaleY = 1f
			back.alpha = 0f
		}
	}

	fun setUseWeekInterval(useWeekInterval: Boolean) {
		this.useWeekInterval = useWeekInterval
	}

	fun showDate(b: Boolean) {
		showDate = b

		if (!showDate) {
			datesTmp.gone()
			dates.gone()
			title.layoutParams = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 16f, 0f, 16f, 0f)
			title.requestLayout()
		}
		else {
			title.layoutParams = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 16f, 0f, textMargin.toFloat(), 0f)
		}
	}
}
