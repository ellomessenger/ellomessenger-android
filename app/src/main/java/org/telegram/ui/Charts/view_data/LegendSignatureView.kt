/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.view_data

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.drawable.Drawable
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.utils.currentLocale
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CombinedDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

open class LegendSignatureView(context: Context) : FrameLayout(context) {
	private val content = LinearLayout(context)
	private val format = SimpleDateFormat("E, ", context.currentLocale)
	private val format2 = SimpleDateFormat("MMM dd", context.currentLocale)
	private val format3 = SimpleDateFormat("d MMM yyyy", context.currentLocale)
	private val format4 = SimpleDateFormat("d MMM", context.currentLocale)
	private val hourFormat = SimpleDateFormat(" HH:mm", context.currentLocale)
	private val progressView = RadialProgressView(context)
	private val time = TextView(context)
	private var backgroundDrawable: Drawable? = null
	private val holders = mutableListOf<Holder>()
	private var hourTime: TextView
	private var useWeek = false
	val chevron = ImageView(context)
	var canGoZoom = true
	var isTopHourChart = false
	var shadowDrawable: Drawable? = null
	var showPercentage = false
	var useHour = false
	var zoomEnabled = false

	private var showProgressRunnable = Runnable {
		chevron.animate().setDuration(120).alpha(0f)
		progressView.animate().setListener(null).start()

		if (progressView.visibility != VISIBLE) {
			progressView.visible()
			progressView.alpha = 0f
		}

		progressView.animate().setDuration(120).alpha(1f).start()
	}

	init {
		setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))

		content.orientation = LinearLayout.VERTICAL

		time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		time.typeface = Theme.TYPEFACE_BOLD
		hourTime = TextView(context)
		hourTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		hourTime.typeface = Theme.TYPEFACE_BOLD

		chevron.setImageResource(R.drawable.ic_chevron_right_black_18dp)

		progressView.setSize(AndroidUtilities.dp(12f))
		progressView.setStrokeWidth(AndroidUtilities.dp(0.5f).toFloat())
		progressView.gone()

		addView(content, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.NO_GRAVITY, 0f, 22f, 0f, 0f))
		addView(time, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START, 4f, 0f, 4f, 0f))
		addView(hourTime, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END, 4f, 0f, 4f, 0f))
		addView(chevron, LayoutHelper.createFrame(18, 18f, Gravity.END or Gravity.TOP, 0f, 2f, 0f, 0f))
		addView(progressView, LayoutHelper.createFrame(18, 18f, Gravity.END or Gravity.TOP, 0f, 2f, 0f, 0f))

		recolor()
	}

	open fun recolor() {
		time.setTextColor(context.getColor(R.color.text))
		hourTime.setTextColor(context.getColor(R.color.text))
		chevron.setColorFilter(context.getColor(R.color.medium_gray))
		progressView.setProgressColor(context.getColor(R.color.medium_gray))
		shadowDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.stats_tooltip, null)!!.mutate()
		backgroundDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4f), context.getColor(R.color.background), context.getColor(R.color.light_background), -0x1000000)
		val drawable = CombinedDrawable(shadowDrawable, backgroundDrawable, AndroidUtilities.dp(3f), AndroidUtilities.dp(3f))
		drawable.setFullSize(true)
		background = drawable
	}

	open fun setSize(n: Int) {
		content.removeAllViews()

		holders.clear()

		for (i in 0 until n) {
			holders.add(Holder().also {
				content.addView(it.root)
			})
		}
	}

	fun setData(index: Int, date: Long, lines: ArrayList<LineViewData>, animateChanges: Boolean) {
		val n = holders.size

		if (animateChanges) {
			val transition = TransitionSet()
			transition.addTransition(Fade(Fade.OUT).setDuration(150)).addTransition(ChangeBounds().setDuration(150)).addTransition(Fade(Fade.IN).setDuration(150))
			transition.ordering = TransitionSet.ORDERING_TOGETHER
			TransitionManager.beginDelayedTransition(this, transition)
		}

		if (isTopHourChart) {
			time.text = String.format(Locale.ENGLISH, "%02d:00", date)
		}
		else {
			if (useWeek) {
				time.text = String.format("%s — %s", format4.format(Date(date)), format3.format(Date(date + 86400000L * 7)))
			}
			else {
				time.text = formatData(Date(date))
			}

			if (useHour) {
				hourTime.text = hourFormat.format(date)
			}
		}

		var sum = 0

		for (i in 0 until n) {
			if (lines[i].enabled) {
				sum += lines[i].line.y[index]
			}
		}

		for (i in 0 until n) {
			val h = holders[i]

			if (!lines[i].enabled) {
				h.root.gone()
			}
			else {
				val l = lines[i].line

				if (h.root.measuredHeight == 0) {
					h.root.requestLayout()
				}

				h.root.visibility = VISIBLE
				h.value.text = formatWholeNumber(l.y[index])
				h.signature.text = l.name
				h.value.setTextColor(if (AndroidUtilities.isSimAvailable()) l.colorDark else l.color)
				h.signature.setTextColor(context.getColor(R.color.text))

				if (showPercentage && h.percentage != null) {
					h.percentage?.visible()
					h.percentage?.setTextColor(context.getColor(R.color.text))

					val v = lines[i].line.y[index] / sum.toFloat()

					if (v < 0.1f && v != 0f) {
						h.percentage?.text = String.format(Locale.ENGLISH, "%.1f%s", 100f * v, "%")
					}
					else {
						h.percentage?.text = String.format(Locale.ENGLISH, "%d%s", (100 * v).roundToLong(), "%")
					}
				}
			}
		}

		if (zoomEnabled) {
			canGoZoom = sum > 0
			chevron.visibility = if (sum > 0) VISIBLE else GONE
		}
		else {
			canGoZoom = false
			chevron.gone()
		}
	}

	private fun formatData(date: Date): String {
		return if (useHour) {
			capitalize(format2.format(date))
		}
		else {
			capitalize(format.format(date)) + capitalize(format2.format(date))
		}
	}

	private fun capitalize(s: String): String {
		return if (s.isNotEmpty()) {
			s[0].uppercaseChar().toString() + s.substring(1)
		}
		else {
			s
		}
	}

	private fun formatWholeNumber(v: Int): String {
		var num = v.toFloat()
		var count = 0

		if (v < 10000) {
			return String.format(Locale.getDefault(), "%d", v)
		}

		while (num >= 10000 && count < AndroidUtilities.numbersSignatureArray.size - 1) {
			num /= 1000f
			count++
		}

		return String.format(Locale.getDefault(), "%.2f", num) + AndroidUtilities.numbersSignatureArray[count]
	}

	fun showProgress(show: Boolean, force: Boolean) {
		if (show) {
			AndroidUtilities.runOnUIThread(showProgressRunnable, 300)
		}
		else {
			AndroidUtilities.cancelRunOnUIThread(showProgressRunnable)

			if (force) {
				progressView.visibility = GONE
			}
			else {
				chevron.animate().setDuration(80).alpha(1f).start()

				if (progressView.visibility == VISIBLE) {
					progressView.animate().setDuration(80).alpha(0f).setListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							progressView.gone()
						}
					}).start()
				}
			}
		}
	}

	fun setUseWeek(useWeek: Boolean) {
		this.useWeek = useWeek
	}

	inner class Holder {
		val value = TextView(context)
		val signature = TextView(context)
		var percentage: TextView? = null
		val root = LinearLayout(context)

		init {
			root.setPadding(AndroidUtilities.dp(4f), AndroidUtilities.dp(2f), AndroidUtilities.dp(4f), AndroidUtilities.dp(2f))

			if (showPercentage) {
				percentage = TextView(context)

				root.addView(percentage)

				percentage?.layoutParams?.width = AndroidUtilities.dp(36f)
				percentage?.gone()
				percentage?.typeface = Theme.TYPEFACE_BOLD
				percentage?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
			}

			root.addView(signature)

			signature.layoutParams?.width = if (showPercentage) AndroidUtilities.dp(80f) else AndroidUtilities.dp(96f)
			signature.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
			signature.gravity = Gravity.START

			root.addView(value, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			value.gravity = Gravity.END
			value.typeface = Theme.TYPEFACE_BOLD
			value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
			value.minEms = 4
			value.maxEms = 4
		}
	}
}
