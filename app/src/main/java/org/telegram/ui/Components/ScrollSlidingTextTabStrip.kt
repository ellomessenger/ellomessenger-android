/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022.
 */
package org.telegram.ui.Components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.SparseArray
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Keep
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import kotlin.math.ceil

open class ScrollSlidingTextTabStrip(context: Context) : HorizontalScrollView(context) {
	private val tabsContainer: LinearLayout
	private val selectorDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null)
	private val interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
	private val positionToId = SparseIntArray(5)
	private val idToPosition = SparseIntArray(5)
	private val positionToWidth = SparseIntArray(5)
	private var delegate: ScrollSlidingTabStripDelegate? = null
	private var useSameWidth = false
	private var allTextWidth = 0
	private var indicatorX = 0
	private var indicatorWidth = 0
	private var prevLayoutWidth = 0
	private var animateIndicatorStartX = 0
	private var animateIndicatorStartWidth = 0
	private var animateIndicatorToX = 0
	private var animateIndicatorToWidth = 0
	var currentTabId = -1

	var tabsCount = 0
		private set

	var currentPosition = 0
		private set

	var selectedTab = -1
		private set
		get() = currentTabId

	var isAnimatingIndicator = false
		private set

	@get:Keep
	@set:Keep
	var animationIndicatorProgress = 0f
		set(value) {
			field = value
			val newTab = tabsContainer.getChildAt(currentPosition) as? TextView
			val prevTab = tabsContainer.getChildAt(previousPosition) as? TextView

			if (prevTab == null || newTab == null) {
				return
			}

			setAnimationProgressInternal(newTab, prevTab, value)

			delegate?.onPageScrolled(value)
		}

	private var scrollingToChild = -1
	private val tabLineColor = ResourcesCompat.getColor(resources, R.color.brand, null)
	private val activeTextColor = ResourcesCompat.getColor(resources, R.color.brand, null)
	private val inactiveTextColor = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
	private val selectorColor = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
	private val lastAnimationTime: Long = 0
	private var animationTime = 0f
	private var previousPosition = 0
	private var animateFromIndicatorX = 0
	private var animateFromIndicatorWidth = 0
	private var indicatorXAnimationDx = 0f
	private var indicatorWidthAnimationDx = 0f

	fun setDelegate(scrollSlidingTabStripDelegate: ScrollSlidingTabStripDelegate?) {
		delegate = scrollSlidingTabStripDelegate
	}

	private val animationRunnable: Runnable = object : Runnable {
		override fun run() {
			if (!isAnimatingIndicator) {
				return
			}

			val newTime = SystemClock.elapsedRealtime()

			var dt = newTime - lastAnimationTime

			if (dt > 17) {
				dt = 17
			}

			animationTime += dt / 200.0f

			animationIndicatorProgress = interpolator.getInterpolation(animationTime)

			if (animationTime > 1.0f) {
				animationTime = 1.0f
			}

			if (animationTime < 1.0f) {
				AndroidUtilities.runOnUIThread(this)
			}
			else {
				isAnimatingIndicator = false
				isEnabled = true

				delegate?.onPageScrolled(1.0f)
			}
		}
	}

	init {
		val rad = AndroidUtilities.dpf2(3f)
		selectorDrawable.cornerRadii = floatArrayOf(rad, rad, rad, rad, 0f, 0f, 0f, 0f)
		selectorDrawable.setColor(tabLineColor)

		isFillViewport = true

		setWillNotDraw(false)

		isHorizontalScrollBarEnabled = false

		tabsContainer = object : LinearLayout(context) {
			override fun setAlpha(alpha: Float) {
				super.setAlpha(alpha)
				this@ScrollSlidingTextTabStrip.invalidate()
			}
		}

		tabsContainer.setOrientation(LinearLayout.HORIZONTAL)
		tabsContainer.setPadding(AndroidUtilities.dp(7f), 0, AndroidUtilities.dp(7f), 0)
		tabsContainer.setLayoutParams(LayoutParams(LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

		addView(tabsContainer)
	}

	private fun setAnimationProgressInternal(newTab: TextView?, prevTab: TextView?, value: Float) {
		if (newTab == null || prevTab == null) {
			return
		}

		val newColor = activeTextColor
		val prevColor = inactiveTextColor
		val r1 = Color.red(newColor)
		val g1 = Color.green(newColor)
		val b1 = Color.blue(newColor)
		val a1 = Color.alpha(newColor)
		val r2 = Color.red(prevColor)
		val g2 = Color.green(prevColor)
		val b2 = Color.blue(prevColor)
		val a2 = Color.alpha(prevColor)

		prevTab.setTextColor(Color.argb((a1 + (a2 - a1) * value).toInt(), (r1 + (r2 - r1) * value).toInt(), (g1 + (g2 - g1) * value).toInt(), (b1 + (b2 - b1) * value).toInt()))
		newTab.setTextColor(Color.argb((a2 + (a1 - a2) * value).toInt(), (r2 + (r1 - r2) * value).toInt(), (g2 + (g1 - g2) * value).toInt(), (b2 + (b1 - b2) * value).toInt()))

		indicatorX = (animateIndicatorStartX + (animateIndicatorToX - animateIndicatorStartX) * value).toInt()

		indicatorWidth = (animateIndicatorStartWidth + (animateIndicatorToWidth - animateIndicatorStartWidth) * value).toInt()

		invalidate()
	}

	fun setUseSameWidth(value: Boolean) {
		useSameWidth = value
	}

	fun getSelectorDrawable(): Drawable {
		return selectorDrawable
	}

	fun getTabsContainer(): ViewGroup {
		return tabsContainer
	}

	fun getNextPageId(forward: Boolean): Int {
		return positionToId[currentPosition + (if (forward) 1 else -1), -1]
	}

	fun removeTabs(): SparseArray<View> {
		val views = SparseArray<View>()

		for (i in 0 until childCount) {
			val child = getChildAt(i)
			views[positionToId[i], child]
		}

		positionToId.clear()
		idToPosition.clear()
		positionToWidth.clear()
		tabsContainer.removeAllViews()
		allTextWidth = 0
		tabsCount = 0

		return views
	}

	fun hasTab(id: Int): Boolean {
		return idToPosition[id, -1] != -1
	}

	@JvmOverloads
	fun addTextTab(id: Int, text: CharSequence, viewsCache: SparseArray<View>? = null) {
		val position = tabsCount++

		if (position == 0 && currentTabId == -1) {
			currentTabId = id
		}

		positionToId.put(position, id)
		idToPosition.put(id, position)

		if (currentTabId != -1 && currentTabId == id) {
			currentPosition = position
			prevLayoutWidth = 0
		}

		var tab: TextView? = null

		if (viewsCache != null) {
			tab = viewsCache[id] as? TextView
			viewsCache.delete(id)
		}

		if (tab == null) {
			tab = object : TextView(context) {
				override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
					super.onInitializeAccessibilityNodeInfo(info)
					info.isSelected = currentTabId == id
				}
			}

			tab.setWillNotDraw(false)
			tab.setGravity(Gravity.CENTER)
			tab.setBackground(Theme.createSelectorDrawable(selectorColor, 3))
			tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			tab.setSingleLine(true)
			tab.setTypeface(Theme.TYPEFACE_BOLD)
			tab.setPadding(AndroidUtilities.dp(16f), 0, AndroidUtilities.dp(16f), 0)
			tab.setOnClickListener(OnClickListener { v ->
				val position1 = tabsContainer.indexOfChild(v)
				if (position1 < 0) {
					return@OnClickListener
				}

				if (position1 == currentPosition && delegate != null) {
					delegate!!.onSamePageSelected()
					return@OnClickListener
				}

				val scrollingForward = currentPosition < position1

				scrollingToChild = -1
				previousPosition = currentPosition
				currentPosition = position1
				currentTabId = id

				if (isAnimatingIndicator) {
					AndroidUtilities.cancelRunOnUIThread(animationRunnable)
				}

				animationTime = 0f
				isAnimatingIndicator = true
				animateIndicatorStartX = indicatorX
				animateIndicatorStartWidth = indicatorWidth

				val nextChild = v as TextView

				animateIndicatorToWidth = getChildWidth(nextChild)
				animateIndicatorToX = nextChild.left + (nextChild.measuredWidth - animateIndicatorToWidth) / 2
				isEnabled = false

				AndroidUtilities.runOnUIThread(animationRunnable, 16)

				delegate?.onPageSelected(id, scrollingForward)

				scrollToChild(position1)
			})
		}

		tab.text = text

		val tabWidth = ceil(tab.paint.measureText(text, 0, text.length).toDouble()).toInt() + tab.paddingLeft + tab.paddingRight

		tabsContainer.addView(tab, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT))

		allTextWidth += tabWidth

		positionToWidth.put(position, tabWidth)
	}

	fun finishAddingTabs() {
		val count = tabsContainer.childCount

		for (a in 0 until count) {
			val tab = tabsContainer.getChildAt(a) as TextView
			tab.setTextColor(if (currentPosition == a) activeTextColor else inactiveTextColor)

			if (a == 0) {
				tab.layoutParams.width = if (count == 1) LayoutHelper.WRAP_CONTENT else 0
			}
		}
	}

	fun setInitialTabId(id: Int) {
		currentTabId = id

		val pos = idToPosition[id]
		val child = tabsContainer.getChildAt(pos) as? TextView

		if (child != null) {
			currentPosition = pos
			prevLayoutWidth = 0
			finishAddingTabs()
			requestLayout()
		}
	}

	fun resetTab() {
		currentTabId = -1
	}

	val firstTabId: Int
		get() = positionToId[0, 0]

	override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
		val result = super.drawChild(canvas, child, drawingTime)

		if (child === tabsContainer) {
			val height = measuredHeight

			selectorDrawable.alpha = (255 * tabsContainer.alpha).toInt()

			val x = indicatorX + indicatorXAnimationDx
			val w = x + indicatorWidth + indicatorWidthAnimationDx

			selectorDrawable.setBounds(x.toInt(), height - AndroidUtilities.dpr(4f), w.toInt(), height)
			selectorDrawable.draw(canvas)
		}

		return result
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(22f)
		val count = tabsContainer.childCount

		for (a in 0 until count) {
			val child = tabsContainer.getChildAt(a)
			val layoutParams = child.layoutParams as LinearLayout.LayoutParams

			if (allTextWidth > width) {
				layoutParams.weight = 0f
				layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
			}
			else if (useSameWidth) {
				layoutParams.weight = 1.0f / count
				layoutParams.width = 0
			}
			else {
				if (a == 0 && count == 1) {
					layoutParams.weight = 0.0f
					layoutParams.width = LayoutHelper.WRAP_CONTENT
				}
				else {
					layoutParams.weight = 1.0f / allTextWidth * positionToWidth[a]
					layoutParams.width = 0
				}
			}
		}

		if (count == 1 || allTextWidth > width) {
			tabsContainer.weightSum = 0.0f
		}
		else {
			tabsContainer.weightSum = 1.0f
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
	}

	private fun scrollToChild(position: Int) {
		if (tabsCount == 0 || scrollingToChild == position) {
			return
		}

		scrollingToChild = position

		val child = tabsContainer.getChildAt(position) as? TextView ?: return
		val currentScrollX = scrollX
		val left = child.left
		val width = child.measuredWidth

		if (left - AndroidUtilities.dp(50f) < currentScrollX) {
			smoothScrollTo(left - AndroidUtilities.dp(50f), 0)
		}
		else if (left + width + AndroidUtilities.dp(21f) > currentScrollX + getWidth()) {
			smoothScrollTo(left + width, 0)
		}
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		super.onLayout(changed, l, t, r, b)

		if (prevLayoutWidth != r - l) {
			prevLayoutWidth = r - l
			scrollingToChild = -1

			if (isAnimatingIndicator) {
				AndroidUtilities.cancelRunOnUIThread(animationRunnable)

				isAnimatingIndicator = false

				isEnabled = true

				delegate?.onPageScrolled(1.0f)
			}

			val child = tabsContainer.getChildAt(currentPosition) as? TextView

			if (child != null) {
				indicatorWidth = getChildWidth(child)
				indicatorX = child.left + (child.measuredWidth - indicatorWidth) / 2

				if (animateFromIndicatorX > 0 && animateFromIndicatorWidth > 0) {
					if (animateFromIndicatorX != indicatorX || animateFromIndicatorWidth != indicatorWidth) {
						val dX = animateFromIndicatorX - indicatorX
						val dW = animateFromIndicatorWidth - indicatorWidth
						val valueAnimator = ValueAnimator.ofFloat(1f, 0f)

						valueAnimator.addUpdateListener { valueAnimator1: ValueAnimator ->
							val v = valueAnimator1.animatedValue as Float
							indicatorXAnimationDx = dX * v
							indicatorWidthAnimationDx = dW * v
							tabsContainer.invalidate()
							invalidate()
						}

						valueAnimator.duration = 200
						valueAnimator.interpolator = CubicBezierInterpolator.DEFAULT
						valueAnimator.start()
					}

					animateFromIndicatorX = 0
					animateFromIndicatorWidth = 0
				}
			}
		}
	}

	override fun setEnabled(enabled: Boolean) {
		super.setEnabled(enabled)

		val count = tabsContainer.childCount

		for (a in 0 until count) {
			val child = tabsContainer.getChildAt(a)
			child.isEnabled = enabled
		}
	}

	fun selectTabWithId(id: Int, progress: Float) {
		var progress = progress
		val position = idToPosition[id, -1]

		if (position < 0) {
			return
		}

		if (progress < 0) {
			progress = 0f
		}
		else if (progress > 1.0f) {
			progress = 1.0f
		}

		val child = tabsContainer.getChildAt(currentPosition) as? TextView
		val nextChild = tabsContainer.getChildAt(position) as? TextView

		if (child != null && nextChild != null) {
			animateIndicatorStartWidth = getChildWidth(child)
			animateIndicatorStartX = child.left + (child.measuredWidth - animateIndicatorStartWidth) / 2
			animateIndicatorToWidth = getChildWidth(nextChild)
			animateIndicatorToX = nextChild.left + (nextChild.measuredWidth - animateIndicatorToWidth) / 2

			setAnimationProgressInternal(nextChild, child, progress)

			scrollToChild(tabsContainer.indexOfChild(nextChild))
		}

		if (progress >= 1.0f) {
			currentPosition = position
			currentTabId = id
		}
	}

	private fun getChildWidth(child: TextView): Int {
		val layout = child.layout

		return if (layout != null) {
			ceil(layout.getLineWidth(0).toDouble()).toInt() + AndroidUtilities.dp(2f)
		}
		else {
			child.measuredWidth
		}
	}

	fun onPageScrolled(position: Int, first: Int) {
		if (currentPosition == position) {
			return
		}

		currentPosition = position

		if (position >= tabsContainer.childCount) {
			return
		}

		for (a in 0 until tabsContainer.childCount) {
			tabsContainer.getChildAt(a).isSelected = a == position
		}

		if (first == position && position > 1) {
			scrollToChild(position - 1)
		}
		else {
			scrollToChild(position)
		}

		invalidate()
	}

	fun recordIndicatorParams() {
		animateFromIndicatorX = indicatorX
		animateFromIndicatorWidth = indicatorWidth
	}

	interface ScrollSlidingTabStripDelegate {
		fun onPageSelected(page: Int, forward: Boolean)
		fun onPageScrolled(progress: Float)
		fun onSamePageSelected() {}
	}
}
