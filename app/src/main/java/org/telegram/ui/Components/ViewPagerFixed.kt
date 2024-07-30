/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TL_messages_updateDialogFiltersOrder
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RecyclerListView.OnItemClickListenerExtended
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.ViewPagerFixed.TabsView.TabsViewDelegate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

open class ViewPagerFixed(context: Context) : FrameLayout(context) {
	var nextPosition = 0
	protected val viewPages = arrayOfNulls<View>(2)
	private val viewTypes = IntArray(2)
	private var startedTrackingPointerId = 0
	private var startedTrackingX = 0
	private var startedTrackingY = 0
	private var velocityTracker: VelocityTracker? = null
	private var tabsAnimation: AnimatorSet? = null
	private var tabsAnimationInProgress = false
	private var animatingForward = false
	private var additionalOffset = 0f
	private var backAnimation = false
	private val maximumVelocity: Int
	private var startedTracking = false
	private var maybeStartTracking = false
	private val touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true)
	private var adapter: Adapter? = null
	private val rect = Rect()

	@JvmField
	var tabsView: TabsView? = null

	@JvmField
	protected var viewsByType = SparseArray<View?>()

	@JvmField
	var currentPosition = 0

	private var updateTabProgress = AnimatorUpdateListener {
		if (tabsAnimationInProgress) {
			viewPages[0]?.let {
				val scrollProgress = abs(it.translationX) / it.measuredWidth.toFloat()
				tabsView?.selectTab(nextPosition, currentPosition, 1f - scrollProgress)
			}
		}
	}

	init {
		maximumVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
		clipChildren = true
	}

	fun setAdapter(adapter: Adapter) {
		this.adapter = adapter
		viewTypes[0] = adapter.getItemViewType(currentPosition)
		viewPages[0] = adapter.createView(viewTypes[0])
		adapter.bindView(viewPages[0], currentPosition, viewTypes[0])
		addView(viewPages[0])
		viewPages[0]?.visible()
		fillTabs()
	}

	fun createTabsView(): TabsView {
		tabsView = TabsView(context)

		tabsView?.setDelegate(object : TabsViewDelegate {
			override fun onPageSelected(page: Int, forward: Boolean) {
				animatingForward = forward
				nextPosition = page

				updateViewForIndex(1)

				if (forward) {
					viewPages[1]!!.translationX = viewPages[0]!!.measuredWidth.toFloat()
				}
				else {
					viewPages[1]!!.translationX = -viewPages[0]!!.measuredWidth.toFloat()
				}
			}

			override fun onPageScrolled(progress: Float) {
				if (progress == 1f) {
					if (viewPages[1] != null) {
						swapViews()
						viewsByType.put(viewTypes[1], viewPages[1])
						removeView(viewPages[1])
						viewPages[0]?.translationX = 0f
						viewPages[1] = null
					}

					return
				}

				if (viewPages[1] == null) {
					return
				}

				if (animatingForward) {
					viewPages[1]?.translationX = viewPages[0]!!.measuredWidth * (1f - progress)
					viewPages[0]?.translationX = -viewPages[0]!!.measuredWidth * progress
				}
				else {
					viewPages[1]?.translationX = -viewPages[0]!!.measuredWidth * (1f - progress)
					viewPages[0]?.translationX = viewPages[0]!!.measuredWidth * progress
				}
			}

			override fun onSamePageSelected() {
				// unused
			}

			override fun canPerformActions(): Boolean {
				return !tabsAnimationInProgress && !startedTracking
			}

			override fun invalidateBlur() {
				this@ViewPagerFixed.invalidateBlur()
			}
		})

		fillTabs()

		return tabsView!!
	}

	protected open fun invalidateBlur() {
		// stub
	}

	private fun updateViewForIndex(index: Int) {
		val adapter = adapter ?: return
		val adapterPosition = if (index == 0) currentPosition else nextPosition

		if (viewPages[index] == null) {
			viewTypes[index] = adapter.getItemViewType(adapterPosition)

			var v = viewsByType[viewTypes[index]]

			if (v == null) {
				v = adapter.createView(viewTypes[index])
			}
			else {
				viewsByType.remove(viewTypes[index])
			}

			if (v?.parent != null) {
				val parent = v.parent as ViewGroup
				parent.removeView(v)
			}

			addView(v)

			viewPages[index] = v

			adapter.bindView(viewPages[index], adapterPosition, viewTypes[index])

			viewPages[index]?.visible()
		}
		else {
			if (viewTypes[index] == adapter.getItemViewType(adapterPosition)) {
				adapter.bindView(viewPages[index], adapterPosition, viewTypes[index])
				viewPages[index]?.visible()
			}
			else {
				viewsByType.put(viewTypes[index], viewPages[index])

				viewPages[index]?.gone()

				removeView(viewPages[index])

				viewTypes[index] = adapter.getItemViewType(adapterPosition)

				var v = viewsByType[viewTypes[index]]

				if (v == null) {
					v = adapter.createView(viewTypes[index])
				}
				else {
					viewsByType.remove(viewTypes[index])
				}

				addView(v)
				viewPages[index] = v
				viewPages[index]?.visible()

				adapter.bindView(viewPages[index], adapterPosition, adapter.getItemViewType(adapterPosition))
			}
		}
	}

	private fun fillTabs() {
		val adapter = adapter ?: return
		val tabsView = tabsView ?: return

		tabsView.removeTabs()

		for (i in 0 until adapter.itemCount) {
			tabsView.addTab(adapter.getItemId(i), adapter.getItemTitle(i))
		}
	}

	private fun prepareForMoving(ev: MotionEvent, forward: Boolean): Boolean {
		if (!forward && currentPosition == 0 || forward && currentPosition == adapter!!.itemCount - 1) {
			return false
		}

		parent.requestDisallowInterceptTouchEvent(true)
		maybeStartTracking = false

		startedTracking = true
		startedTrackingX = (ev.x + additionalOffset).toInt()

		tabsView?.isEnabled = false

		animatingForward = forward
		nextPosition = currentPosition + if (forward) 1 else -1

		updateViewForIndex(1)

		if (forward) {
			viewPages[1]?.translationX = viewPages[0]!!.measuredWidth.toFloat()
		}
		else {
			viewPages[1]?.translationX = -viewPages[0]!!.measuredWidth.toFloat()
		}

		return true
	}

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
		if (tabsView != null && tabsView?.isAnimatingIndicator == true) {
			return false
		}

		if (checkTabsAnimationInProgress()) {
			return true
		}

		onTouchEvent(ev)

		return startedTracking
	}

	override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
		if (maybeStartTracking && !startedTracking) {
			onTouchEvent(null)
		}

		super.requestDisallowInterceptTouchEvent(disallowIntercept)
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(ev: MotionEvent?): Boolean {
		if (tabsView != null && tabsView?.isAnimatingIndicator == true) {
			return false
		}

		if (ev != null) {
			if (velocityTracker == null) {
				velocityTracker = VelocityTracker.obtain()
			}

			velocityTracker?.addMovement(ev)
		}

		if (ev != null && ev.action == MotionEvent.ACTION_DOWN && checkTabsAnimationInProgress()) {
			startedTracking = true
			startedTrackingPointerId = ev.getPointerId(0)
			startedTrackingX = ev.x.toInt()

			if (animatingForward) {
				if (startedTrackingX < viewPages[0]!!.measuredWidth + viewPages[0]!!.translationX) {
					additionalOffset = viewPages[0]!!.translationX
				}
				else {
					swapViews()
					animatingForward = false
					additionalOffset = viewPages[0]!!.translationX
				}
			}
			else {
				if (startedTrackingX < viewPages[1]!!.measuredWidth + viewPages[1]!!.translationX) {
					swapViews()
					animatingForward = true
					additionalOffset = viewPages[0]!!.translationX
				}
				else {
					additionalOffset = viewPages[0]!!.translationX
				}
			}

			tabsAnimation?.removeAllListeners()
			tabsAnimation?.cancel()

			tabsAnimationInProgress = false
		}
		else if (ev != null && ev.action == MotionEvent.ACTION_DOWN) {
			additionalOffset = 0f
		}

		if (!startedTracking && ev != null) {
			val child = findScrollingChild(this, ev.x, ev.y)

			if (child != null && (child.canScrollHorizontally(1) || child.canScrollHorizontally(-1))) {
				return false
			}
		}

		if (ev != null && ev.action == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
			startedTrackingPointerId = ev.getPointerId(0)
			maybeStartTracking = true
			startedTrackingX = ev.x.toInt()
			startedTrackingY = ev.y.toInt()
		}
		else if (ev != null && ev.action == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
			val dx = (ev.x - startedTrackingX + additionalOffset).toInt()
			val dy = abs(ev.y.toInt() - startedTrackingY)

			if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
				if (!prepareForMoving(ev, dx < 0)) {
					maybeStartTracking = true
					startedTracking = false

					viewPages[0]?.translationX = 0f
					viewPages[1]?.translationX = (if (animatingForward) viewPages[0]!!.measuredWidth else -viewPages[0]!!.measuredWidth).toFloat()

					tabsView?.selectTab(currentPosition, 0, 0f)
				}
			}

			if (maybeStartTracking && !startedTracking) {
				val dxLocal = (ev.x - startedTrackingX).toInt()

				if (abs(dxLocal) >= touchSlop && abs(dxLocal) > dy) {
					prepareForMoving(ev, dx < 0)
				}
			}
			else if (startedTracking) {
				viewPages[0]?.translationX = dx.toFloat()

				if (animatingForward) {
					viewPages[1]?.translationX = (viewPages[0]!!.measuredWidth + dx).toFloat()
				}
				else {
					viewPages[1]?.translationX = (dx - viewPages[0]!!.measuredWidth).toFloat()
				}

				val scrollProgress = abs(dx) / viewPages[0]!!.measuredWidth.toFloat()

				tabsView?.selectTab(nextPosition, currentPosition, 1f - scrollProgress)
			}
		}
		else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.action == MotionEvent.ACTION_CANCEL || ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_POINTER_UP)) {
			velocityTracker?.computeCurrentVelocity(1000, maximumVelocity.toFloat())

			var velX: Float
			val velY: Float

			if (ev != null && ev.action != MotionEvent.ACTION_CANCEL) {
				velX = velocityTracker?.xVelocity ?: 0f
				velY = velocityTracker?.yVelocity ?: 0f

				if (!startedTracking) {
					if (abs(velX) >= 3000 && abs(velX) > abs(velY)) {
						prepareForMoving(ev, velX < 0)
					}
				}
			}
			else {
				velX = 0f
				velY = 0f
			}

			if (startedTracking) {
				val x = viewPages[0]?.x ?: 0f

				tabsAnimation = AnimatorSet()

				backAnimation = if (additionalOffset != 0f) {
					if (abs(velX) > 1500) {
						if (animatingForward) velX > 0 else velX < 0
					}
					else {
						if (animatingForward) {
							viewPages[1]!!.x > viewPages[0]!!.measuredWidth shr 1
						}
						else {
							viewPages[0]!!.x < viewPages[0]!!.measuredWidth shr 1
						}
					}
				}
				else {
					abs(x) < viewPages[0]!!.measuredWidth / 3.0f && (abs(velX) < 3500 || abs(velX) < abs(velY))
				}

				val dx: Float

				if (backAnimation) {
					dx = abs(x)

					if (animatingForward) {
						tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages[0], TRANSLATION_X, 0f), ObjectAnimator.ofFloat(viewPages[1], TRANSLATION_X, viewPages[1]!!.measuredWidth.toFloat()))
					}
					else {
						tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages[0], TRANSLATION_X, 0f), ObjectAnimator.ofFloat(viewPages[1], TRANSLATION_X, -viewPages[1]!!.measuredWidth.toFloat()))
					}
				}
				else {
					dx = viewPages[0]!!.measuredWidth - abs(x)

					if (animatingForward) {
						tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages[0], TRANSLATION_X, -viewPages[0]!!.measuredWidth.toFloat()), ObjectAnimator.ofFloat(viewPages[1], TRANSLATION_X, 0f))
					}
					else {
						tabsAnimation?.playTogether(ObjectAnimator.ofFloat(viewPages[0], TRANSLATION_X, viewPages[0]!!.measuredWidth.toFloat()), ObjectAnimator.ofFloat(viewPages[1], TRANSLATION_X, 0f))
					}
				}

				val animator = ValueAnimator.ofFloat(0f, 1f)
				animator.addUpdateListener(updateTabProgress)

				tabsAnimation?.playTogether(animator)
				tabsAnimation?.interpolator = interpolator

				val width = measuredWidth
				val halfWidth = width / 2
				val distanceRatio = min(1.0f, 1.0f * dx / width.toFloat())
				val distance = halfWidth.toFloat() + halfWidth.toFloat() * distanceInfluenceForSnapDuration(distanceRatio)
				velX = abs(velX)

				var duration = if (velX > 0) {
					4 * (1000.0f * abs(distance / velX)).roundToInt()
				}
				else {
					val pageDelta = dx / measuredWidth
					((pageDelta + 1.0f) * 100.0f).toInt()
				}

				duration = max(150, min(duration, 600))

				tabsAnimation?.duration = duration.toLong()

				tabsAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animator: Animator) {
						tabsAnimation = null

						if (viewPages[1] != null) {
							if (!backAnimation) {
								swapViews()
							}

							viewsByType.put(viewTypes[1], viewPages[1])

							removeView(viewPages[1])

							viewPages[1]?.gone()
							viewPages[1] = null
						}

						tabsAnimationInProgress = false
						maybeStartTracking = false
						tabsView?.isEnabled = true
					}
				})

				tabsAnimation?.start()

				tabsAnimationInProgress = true
				startedTracking = false
			}
			else {
				maybeStartTracking = false
				tabsView?.isEnabled = true
			}

			velocityTracker?.recycle()
			velocityTracker = null
		}

		return startedTracking || maybeStartTracking
	}

	private fun swapViews() {
		val page = viewPages[0]
		viewPages[0] = viewPages[1]
		viewPages[1] = page

		var p = currentPosition
		currentPosition = nextPosition
		nextPosition = p

		p = viewTypes[0]
		viewTypes[0] = viewTypes[1]
		viewTypes[1] = p

		onItemSelected(viewPages[0], viewPages[1], currentPosition, nextPosition)
	}

	fun checkTabsAnimationInProgress(): Boolean {
		if (tabsAnimationInProgress) {
			var cancel = false

			if (backAnimation) {
				if (abs(viewPages[0]!!.translationX) < 1) {
					viewPages[0]?.translationX = 0f
					viewPages[1]?.translationX = (viewPages[0]!!.measuredWidth * if (animatingForward) 1 else -1).toFloat()
					cancel = true
				}
			}
			else if (abs(viewPages[1]!!.translationX) < 1) {
				viewPages[0]?.translationX = (viewPages[0]!!.measuredWidth * if (animatingForward) -1 else 1).toFloat()
				viewPages[1]?.translationX = 0f
				cancel = true
			}

			if (cancel) {
				//showScrollbars(true);
				tabsAnimation?.cancel()
				tabsAnimation = null

				tabsAnimationInProgress = false
			}

			return tabsAnimationInProgress
		}

		return false
	}

	open fun setPosition(position: Int) {
		tabsAnimation?.cancel()

		if (viewPages[1] != null) {
			viewsByType.put(viewTypes[1], viewPages[1])
			removeView(viewPages[1])
			viewPages[1] = null
		}

		if (currentPosition != position) {
			val oldPosition = currentPosition

			currentPosition = position

			val oldView = viewPages[0]
			updateViewForIndex(0)
			onItemSelected(viewPages[0], oldView, currentPosition, oldPosition)

			viewPages[0]?.translationX = 0f

			tabsView?.selectTab(position, 0, 1f)
		}
	}

	protected open fun onItemSelected(currentPage: View?, oldPage: View?, position: Int, oldPosition: Int) {
		// stub
	}

	abstract class Adapter {
		abstract val itemCount: Int
		abstract fun createView(viewType: Int): View?
		abstract fun bindView(view: View?, position: Int, viewType: Int)

		fun getItemId(position: Int): Int {
			return position
		}

		open fun getItemTitle(position: Int): String? {
			return ""
		}

		open fun getItemViewType(position: Int): Int {
			return 0
		}
	}

	override fun canScrollHorizontally(direction: Int): Boolean {
		if (direction == 0) {
			return false
		}

		if (tabsAnimationInProgress || startedTracking) {
			return true
		}

		val forward = direction > 0

		return !(!forward && currentPosition == 0 || forward && currentPosition == adapter!!.itemCount - 1)
	}

	val currentView: View?
		get() = viewPages[0]

	class TabsView(context: Context) : FrameLayout(context) {
		interface TabsViewDelegate {
			fun onPageSelected(page: Int, forward: Boolean)
			fun onPageScrolled(progress: Float)
			fun onSamePageSelected()
			fun invalidateBlur()
			fun canPerformActions(): Boolean
		}

		class Tab(var id: Int, var title: String?) {
			var titleWidth = 0
			var counter = 0

			fun getWidth(textPaint: TextPaint): Int {
				titleWidth = ceil(textPaint.measureText(title).toDouble()).toInt()
				val width = titleWidth
				return max(AndroidUtilities.dp(40f), width)
			}

			fun setTitle(newTitle: String?): Boolean {
				if (TextUtils.equals(title, newTitle)) {
					return false
				}

				title = newTitle

				return true
			}
		}

		inner class TabView(context: Context) : View(context) {
			private var currentPosition = 0
			var currentTab: Tab? = null
			private var currentText: String? = null
			private var textHeight = 0
			private var textLayout: StaticLayout? = null
			private var textOffsetX = 0
			val rect = RectF()
			var tabWidth = 0

			fun setTab(tab: Tab, position: Int) {
				currentTab = tab
				currentPosition = position
				contentDescription = tab.title
				requestLayout()
			}

			override fun getId(): Int {
				return currentTab?.id ?: Int.MAX_VALUE
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val w = currentTab!!.getWidth(textPaint) + AndroidUtilities.dp(32f) + additionalTabWidth
				setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec))
			}

			@SuppressLint("DrawAllocation")
			override fun onDraw(canvas: Canvas) {
				if (currentTab?.id != Int.MAX_VALUE && editingAnimationProgress != 0f) {
					canvas.save()

					val p = editingAnimationProgress * if (currentPosition % 2 == 0) 1.0f else -1.0f

					canvas.translate(AndroidUtilities.dp(0.66f) * p, 0f)
					canvas.rotate(p, (measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat())
				}

				val key: Int
				val otherKey: Int
				val id1: Int
				val id2: Int

				if (manualScrollingToId != -1) {
					id1 = manualScrollingToId
					id2 = currentTabId
				}
				else {
					id1 = currentTabId
					id2 = previousId
				}

				if (currentTab?.id == id1) {
					key = context.getColor(R.color.brand)
					otherKey = context.getColor(R.color.dark_gray)
				}
				else {
					key = context.getColor(R.color.dark_gray)
					otherKey = context.getColor(R.color.brand)
				}

				if ((isAnimatingIndicator || manualScrollingToId != -1) && (currentTab!!.id == id1 || currentTab!!.id == id2)) {
					textPaint.color = ColorUtils.blendARGB(otherKey, key, animatingIndicatorProgress)
				}
				else {
					textPaint.color = key
				}

				val counterWidth: Int
				var countWidth: Int
				val counterText: String?

				if (currentTab!!.counter > 0) {
					counterText = String.format("%d", currentTab!!.counter)
					counterWidth = ceil(textCounterPaint.measureText(counterText).toDouble()).toInt()
					countWidth = max(AndroidUtilities.dp(10f), counterWidth) + AndroidUtilities.dp(10f)
				}
				else {
					counterText = null
					counterWidth = 0
					countWidth = 0
				}

				if (currentTab!!.id != Int.MAX_VALUE && (isEditing || editingStartAnimationProgress != 0f)) {
					countWidth = (countWidth + (AndroidUtilities.dp(20f) - countWidth) * editingStartAnimationProgress).toInt()
				}

				tabWidth = currentTab!!.titleWidth + if (countWidth != 0) countWidth + AndroidUtilities.dp(6 * if (counterText != null) 1.0f else editingStartAnimationProgress) else 0

				val textX = (measuredWidth - tabWidth) / 2

				if (!TextUtils.equals(currentTab!!.title, currentText)) {
					currentText = currentTab!!.title

					val text = Emoji.replaceEmoji(currentText, textPaint.fontMetricsInt, false)

					textLayout = StaticLayout(text, textPaint, AndroidUtilities.dp(400f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
					textHeight = textLayout!!.height
					textOffsetX = -textLayout!!.getLineLeft(0).toInt()
				}

				if (textLayout != null) {
					canvas.save()
					canvas.translate((textX + textOffsetX).toFloat(), ((measuredHeight - textHeight) / 2 + 1).toFloat())
					textLayout?.draw(canvas)
					canvas.restore()
				}

				if (counterText != null || currentTab!!.id != Int.MAX_VALUE && (isEditing || editingStartAnimationProgress != 0f)) {
					textCounterPaint.color = context.getColor(R.color.brand)
					counterPaint.color = textPaint.color

					val x = textX + currentTab!!.titleWidth + AndroidUtilities.dp(6f)
					val countTop = (measuredHeight - AndroidUtilities.dp(20f)) / 2

					if (currentTab!!.id != Int.MAX_VALUE && (isEditing || editingStartAnimationProgress != 0f) && counterText == null) {
						counterPaint.alpha = (editingStartAnimationProgress * 255).toInt()
					}
					else {
						counterPaint.alpha = 255
					}

					rect.set(x.toFloat(), countTop.toFloat(), (x + countWidth).toFloat(), (countTop + AndroidUtilities.dp(20f)).toFloat())

					canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, counterPaint)

					if (counterText != null) {
						if (currentTab!!.id != Int.MAX_VALUE) {
							textCounterPaint.alpha = (255 * (1.0f - editingStartAnimationProgress)).toInt()
						}

						canvas.drawText(counterText, rect.left + (rect.width() - counterWidth) / 2, (countTop + AndroidUtilities.dp(14.5f)).toFloat(), textCounterPaint)
					}

					if (currentTab!!.id != Int.MAX_VALUE && (isEditing || editingStartAnimationProgress != 0f)) {
						deletePaint.color = textCounterPaint.color
						deletePaint.alpha = (255 * editingStartAnimationProgress).toInt()

						val side = AndroidUtilities.dp(3f)

						canvas.drawLine(rect.centerX() - side, rect.centerY() - side, rect.centerX() + side, rect.centerY() + side, deletePaint)
						canvas.drawLine(rect.centerX() - side, rect.centerY() + side, rect.centerX() + side, rect.centerY() - side, deletePaint)
					}
				}

				if (currentTab!!.id != Int.MAX_VALUE && editingAnimationProgress != 0f) {
					canvas.restore()
				}
			}

			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)
				info.isSelected = currentTab != null && currentTabId != -1 && currentTab!!.id == currentTabId
			}
		}

		private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private val textCounterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private val deletePaint: Paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private val counterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val tabs = ArrayList<Tab>()
		private val crossfadeBitmap: Bitmap? = null
		private val crossfadePaint = Paint()
		private val crossfadeAlpha = 0f
		private var isEditing = false
		private var editingForwardAnimation = false
		private var editingAnimationProgress = 0f
		private val editingStartAnimationProgress = 0f
		private var orderChanged = false
		private var ignoreLayout = false
		private val tabsContainer: RecyclerListView
		private var layoutManager: LinearLayoutManager? = null
		private var adapter: ListAdapter? = null
		private var delegate: TabsViewDelegate? = null
		private var currentPosition = 0

		var currentTabId = -1
			private set

		private var allTabsWidth = 0
		private var additionalTabWidth = 0

		var isAnimatingIndicator = false
			private set

		private var animatingIndicatorProgress = 0f
		private var manualScrollingToPosition = -1
		private var manualScrollingToId = -1
		private var scrollingToChild = -1
		private val selectorDrawable: GradientDrawable
		private val tabLineColorKey = context.getColor(R.color.brand)
		private val selectorColorKey = context.getColor(R.color.light_background)
		private var prevLayoutWidth = 0
		private var invalidated = false
		private var isInHiddenMode = false
		private var hideProgress = 0f
		private val interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		private val positionToId = SparseIntArray(5)
		private val idToPosition = SparseIntArray(5)
		private val positionToWidth = SparseIntArray(5)
		private val positionToX = SparseIntArray(5)
		private val lastAnimationTime: Long = 0
		private var animationTime = 0f
		private var previousPosition = 0
		private var previousId = 0

		private val animationRunnable = object : Runnable {
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

				setAnimationIndicatorProgress(interpolator.getInterpolation(animationTime))

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

		private var tabsAnimator: ValueAnimator? = null

		init {
			textCounterPaint.textSize = AndroidUtilities.dp(13f).toFloat()
			textCounterPaint.typeface = Theme.TYPEFACE_BOLD

			textPaint.textSize = AndroidUtilities.dp(15f).toFloat()
			textPaint.typeface = Theme.TYPEFACE_BOLD

			deletePaint.style = Paint.Style.STROKE
			deletePaint.strokeCap = Paint.Cap.ROUND
			deletePaint.strokeWidth = AndroidUtilities.dp(1.5f).toFloat()
			deletePaint.typeface = Theme.TYPEFACE_DEFAULT

			selectorDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null)

			val rad = AndroidUtilities.dpf2(3f)

			selectorDrawable.cornerRadii = floatArrayOf(rad, rad, rad, rad, 0f, 0f, 0f, 0f)
			selectorDrawable.setColor(context.getColor(R.color.brand))

			isHorizontalScrollBarEnabled = false

			tabsContainer = object : RecyclerListView(context) {
				override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
					super.addView(child, index, params)

					if (isInHiddenMode) {
						child.scaleX = 0.3f
						child.scaleY = 0.3f
						child.alpha = 0f
					}
					else {
						child.scaleX = 1f
						child.scaleY = 1f
						child.alpha = 1f
					}
				}

				override fun setAlpha(alpha: Float) {
					super.setAlpha(alpha)
					this@TabsView.invalidate()
				}

				override fun canHighlightChildAt(child: View?, x: Float, y: Float): Boolean {
					if (isEditing) {
						val tabView = child as TabView?
						val side = AndroidUtilities.dp(6f)

						if (tabView!!.rect.left - side < x && tabView.rect.right + side > x) {
							return false
						}
					}

					return super.canHighlightChildAt(child, x, y)
				}
			}

			(tabsContainer.getItemAnimator() as? DefaultItemAnimator)?.setDelayAnimations(false)

			tabsContainer.setSelectorType(8)
			tabsContainer.setSelectorRadius(6)
			tabsContainer.setSelectorDrawableColor(selectorColorKey)

			tabsContainer.setLayoutManager(object : LinearLayoutManager(context, HORIZONTAL, false) {
				override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
					val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
						override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
							var dx = calculateDxToMakeVisible(targetView, horizontalSnapPreference)

							if (dx > 0 || dx == 0 && targetView.left - AndroidUtilities.dp(21f) < 0) {
								dx += AndroidUtilities.dp(60f)
							}
							else if (dx < 0 || dx == 0 && targetView.right + AndroidUtilities.dp(21f) > measuredWidth) {
								dx -= AndroidUtilities.dp(60f)
							}

							val dy = calculateDyToMakeVisible(targetView, verticalSnapPreference)
							val distance = sqrt((dx * dx + dy * dy).toDouble()).toInt()
							val time = max(180, calculateTimeForDeceleration(distance))

							if (time > 0) {
								action.update(-dx, -dy, time, mDecelerateInterpolator)
							}
						}
					}

					linearSmoothScroller.targetPosition = position
					startSmoothScroll(linearSmoothScroller)
				}

				override fun onInitializeAccessibilityNodeInfo(recycler: Recycler, state: RecyclerView.State, info: AccessibilityNodeInfoCompat) {
					super.onInitializeAccessibilityNodeInfo(recycler, state, info)

					if (isInHiddenMode) {
						info.isVisibleToUser = false
					}
				}
			}.also {
				layoutManager = it
			})

			tabsContainer.setPadding(AndroidUtilities.dp(7f), 0, AndroidUtilities.dp(7f), 0)
			tabsContainer.setClipToPadding(false)
			tabsContainer.setDrawSelectorBehind(true)
			tabsContainer.setAdapter(ListAdapter(context).also { adapter = it })

			tabsContainer.setOnItemClickListener(object : OnItemClickListenerExtended {
				override fun onDoubleTap(view: View, position: Int, x: Float, y: Float) {
					// unused
				}

				override fun hasDoubleTap(view: View, position: Int): Boolean {
					return false
				}

				override fun onItemClick(view: View, position: Int, x: Float, y: Float) {
					if (delegate?.canPerformActions() != true) {
						return
					}

					val tabView = view as TabView

					if (position == currentPosition && delegate != null) {
						delegate?.onSamePageSelected()
						return
					}

					scrollToTab(tabView.currentTab!!.id, position)
				}
			})

			tabsContainer.addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					invalidate()
				}
			})

			addView(tabsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		fun setDelegate(filterTabsViewDelegate: TabsViewDelegate?) {
			delegate = filterTabsViewDelegate
		}

		fun scrollToTab(id: Int, position: Int) {
			val scrollingForward = currentPosition < position

			scrollingToChild = -1
			previousPosition = currentPosition
			previousId = currentTabId
			currentPosition = position
			currentTabId = id

			tabsAnimator?.cancel()

			if (isAnimatingIndicator) {
				isAnimatingIndicator = false
			}

			animationTime = 0f
			animatingIndicatorProgress = 0f
			isAnimatingIndicator = true
			isEnabled = false

			delegate?.onPageSelected(id, scrollingForward)

			scrollToChild(position)

			tabsAnimator = ValueAnimator.ofFloat(0f, 1f)

			tabsAnimator?.addUpdateListener {
				val progress = it.animatedValue as Float
				setAnimationIndicatorProgress(progress)
				delegate?.onPageScrolled(progress)
			}

			tabsAnimator?.duration = 250
			tabsAnimator?.interpolator = CubicBezierInterpolator.DEFAULT

			tabsAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					isAnimatingIndicator = false
					isEnabled = true
					delegate?.onPageScrolled(1.0f)
					invalidate()
				}
			})

			tabsAnimator?.start()
		}

		fun setAnimationIndicatorProgress(value: Float) {
			animatingIndicatorProgress = value
			tabsContainer.invalidateViews()
			invalidate()
			delegate?.onPageScrolled(value)
		}

		fun getSelectorDrawable(): Drawable {
			return selectorDrawable
		}

		fun getNextPageId(forward: Boolean): Int {
			return positionToId[currentPosition + (if (forward) 1 else -1), -1]
		}

		fun addTab(id: Int, text: String?) {
			val position = tabs.size

			if (position == 0 && currentTabId == -1) {
				currentTabId = id
			}

			positionToId.put(position, id)
			idToPosition.put(id, position)

			if (currentTabId != -1 && currentTabId == id) {
				currentPosition = position
			}

			val tab = Tab(id, text)

			allTabsWidth += tab.getWidth(textPaint) + AndroidUtilities.dp(32f)

			tabs.add(tab)
		}

		fun removeTabs() {
			tabs.clear()
			positionToId.clear()
			idToPosition.clear()
			positionToWidth.clear()
			positionToX.clear()
			allTabsWidth = 0
		}

		@SuppressLint("NotifyDataSetChanged")
		fun finishAddingTabs() {
			adapter?.notifyDataSetChanged()
		}

		val firstTabId: Int
			get() = positionToId[0, 0]

		private fun updateTabsWidths() {
			positionToX.clear()
			positionToWidth.clear()

			var xOffset = AndroidUtilities.dp(7f)

			tabs.forEachIndexed { index, tab ->
				val tabWidth = tab.getWidth(textPaint)
				positionToWidth.put(index, tabWidth)
				positionToX.put(index, xOffset + additionalTabWidth / 2)
				xOffset += tabWidth + AndroidUtilities.dp(32f) + additionalTabWidth
			}
		}

		override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
			val result = super.drawChild(canvas, child, drawingTime)

			if (child === tabsContainer) {
				val height = measuredHeight

				if (isInHiddenMode && hideProgress != 1f) {
					hideProgress += 0.1f

					if (hideProgress > 1f) {
						hideProgress = 1f
					}

					invalidate()
				}
				else if (!isInHiddenMode && hideProgress != 0f) {
					hideProgress -= 0.12f

					if (hideProgress < 0) {
						hideProgress = 0f
					}

					invalidate()
				}

				selectorDrawable.alpha = (255 * tabsContainer.alpha).toInt()

				var indicatorX = 0
				var indicatorWidth = 0

				if (isAnimatingIndicator || manualScrollingToPosition != -1) {
					val position = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION

					if (position != RecyclerView.NO_POSITION) {
						val holder = tabsContainer.findViewHolderForAdapterPosition(position)

						if (holder != null) {
							val idx1: Int
							val idx2: Int

							if (isAnimatingIndicator) {
								idx1 = previousPosition
								idx2 = currentPosition
							}
							else {
								idx1 = currentPosition
								idx2 = manualScrollingToPosition
							}

							val prevX = positionToX[idx1]
							val newX = positionToX[idx2]
							val prevW = positionToWidth[idx1]
							val newW = positionToWidth[idx2]

							indicatorX = if (additionalTabWidth != 0) {
								(prevX + (newX - prevX) * animatingIndicatorProgress).toInt() + AndroidUtilities.dp(16f)
							}
							else {
								val x = positionToX[position]
								(prevX + (newX - prevX) * animatingIndicatorProgress).toInt() - (x - holder.itemView.left) + AndroidUtilities.dp(16f)
							}

							indicatorWidth = (prevW + (newW - prevW) * animatingIndicatorProgress).toInt()
						}
					}
				}
				else {
					val holder = tabsContainer.findViewHolderForAdapterPosition(currentPosition)

					if (holder != null) {
						val tabView = holder.itemView as TabView
						indicatorWidth = max(AndroidUtilities.dp(40f), tabView.tabWidth)
						indicatorX = (tabView.x + (tabView.measuredWidth - indicatorWidth) / 2).toInt()
					}
				}

				if (indicatorWidth != 0) {
					selectorDrawable.setBounds(indicatorX, (height - AndroidUtilities.dpr(4f) + hideProgress * AndroidUtilities.dpr(4f)).toInt(), indicatorX + indicatorWidth, (height + hideProgress * AndroidUtilities.dpr(4f)).toInt())
					selectorDrawable.draw(canvas)
				}

				if (crossfadeBitmap != null) {
					crossfadePaint.alpha = (crossfadeAlpha * 255).toInt()
					canvas.drawBitmap(crossfadeBitmap, 0f, 0f, crossfadePaint)
				}
			}
			return result
		}

		@SuppressLint("NotifyDataSetChanged")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			if (tabs.isNotEmpty()) {
				val width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(7f) - AndroidUtilities.dp(7f)
				val prevWidth = additionalTabWidth

				additionalTabWidth = if (allTabsWidth < width) (width - allTabsWidth) / tabs.size else 0

				if (prevWidth != additionalTabWidth) {
					ignoreLayout = true
					adapter?.notifyDataSetChanged()
					ignoreLayout = false
				}

				updateTabsWidths()

				invalidated = false
			}

			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}

		fun updateColors() {
			selectorDrawable.setColor(tabLineColorKey)
			tabsContainer.invalidateViews()
			tabsContainer.invalidate()
			invalidate()
		}

		override fun requestLayout() {
			if (ignoreLayout) {
				return
			}

			super.requestLayout()
		}

		private fun scrollToChild(position: Int) {
			if (tabs.isEmpty() || scrollingToChild == position || position < 0 || position >= tabs.size) {
				return
			}

			scrollingToChild = position

			tabsContainer.smoothScrollToPosition(position)
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
			}
		}

		fun selectTab(currentPosition: Int, nextPosition: Int, progress: Float) {
			@Suppress("NAME_SHADOWING") var progress = progress

			if (progress < 0) {
				progress = 0f
			}
			else if (progress > 1.0f) {
				progress = 1.0f
			}

			this.currentPosition = currentPosition

			currentTabId = positionToId[currentPosition]

			if (progress > 0) {
				manualScrollingToPosition = nextPosition
				manualScrollingToId = positionToId[nextPosition]
			}
			else {
				manualScrollingToPosition = -1
				manualScrollingToId = -1
			}

			animatingIndicatorProgress = progress

			tabsContainer.invalidateViews()
			invalidate()
			scrollToChild(currentPosition)

			if (progress >= 1.0f) {
				manualScrollingToPosition = -1
				manualScrollingToId = -1
				this.currentPosition = nextPosition
				currentTabId = positionToId[nextPosition]
			}

			delegate?.invalidateBlur()

		}

		fun selectTabWithId(id: Int, progress: Float) {
			@Suppress("NAME_SHADOWING") var progress = progress
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

			if (progress > 0) {
				manualScrollingToPosition = position
				manualScrollingToId = id
			}
			else {
				manualScrollingToPosition = -1
				manualScrollingToId = -1
			}

			animatingIndicatorProgress = progress
			tabsContainer.invalidateViews()
			invalidate()
			scrollToChild(position)

			if (progress >= 1.0f) {
				manualScrollingToPosition = -1
				manualScrollingToId = -1
				currentPosition = position
				currentTabId = id
			}
		}

		private fun getChildWidth(child: TextView): Int {
			val layout = child.layout

			return if (layout != null) {
				var w = ceil(layout.getLineWidth(0).toDouble()).toInt() + AndroidUtilities.dp(2f)

				if (child.compoundDrawables[2] != null) {
					w += child.compoundDrawables[2].intrinsicWidth + AndroidUtilities.dp(6f)
				}

				w
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

			if (position >= tabs.size) {
				return
			}

			if (first == position && position > 1) {
				scrollToChild(position - 1)
			}
			else {
				scrollToChild(position)
			}

			invalidate()
		}

		fun isEditing(): Boolean {
			return isEditing
		}

		fun setIsEditing(value: Boolean) {
			isEditing = value
			editingForwardAnimation = true
			tabsContainer.invalidateViews()
			invalidate()

			if (!isEditing && orderChanged) {
				MessagesStorage.getInstance(UserConfig.selectedAccount).saveDialogFiltersOrder()
				val req = TL_messages_updateDialogFiltersOrder()
				val filters = MessagesController.getInstance(UserConfig.selectedAccount).dialogFilters
				req.order.addAll(filters.map { it.id })

				ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req)

				orderChanged = false
			}
		}

		private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
			override fun getItemCount(): Int {
				return tabs.size
			}

			override fun getItemId(i: Int): Long {
				return i.toLong()
			}

			override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
				return true
			}

			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				return RecyclerListView.Holder(TabView(mContext))
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				val tabView = holder.itemView as TabView
				tabView.setTab(tabs[position], position)
			}

			override fun getItemViewType(i: Int): Int {
				return 0
			}
		}

		fun hide(hide: Boolean, animated: Boolean) {
			isInHiddenMode = hide

			if (animated) {
				for (i in 0 until tabsContainer.childCount) {
					tabsContainer.getChildAt(i).animate().alpha(if (hide) 0f else 1f).scaleX(if (hide) 0f else 1f).scaleY(if (hide) 0f else 1f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(220).start()
				}
			}
			else {
				for (i in 0 until tabsContainer.childCount) {
					val v = tabsContainer.getChildAt(i)
					v.scaleX = if (hide) 0f else 1f
					v.scaleY = if (hide) 0f else 1f
					v.alpha = if (hide) 0f else 1f
				}

				hideProgress = (if (hide) 1 else 0).toFloat()
			}

			invalidate()
		}
	}

	private fun findScrollingChild(parent: ViewGroup, x: Float, y: Float): View? {
		val n = parent.childCount

		for (i in 0 until n) {
			val child = parent.getChildAt(i)

			if (child.visibility != VISIBLE) {
				continue
			}

			child.getHitRect(rect)

			if (rect.contains(x.toInt(), y.toInt())) {
				if (child.canScrollHorizontally(-1)) {
					return child
				}
				else if (child is ViewGroup) {
					val v = findScrollingChild(child, x - rect.left, y - rect.top)

					if (v != null) {
						return v
					}
				}
			}
		}

		return null
	}

	fun drawForBlur(blurCanvas: Canvas) {
		for (i in viewPages.indices) {
			val viewPage = viewPages[i] ?: continue

			if (viewPage.visibility == VISIBLE) {
				val recyclerListView = findRecyclerView(viewPage)

				if (recyclerListView != null) {
					for (j in 0 until recyclerListView.childCount) {
						val child = recyclerListView.getChildAt(j)

						if (child.y < AndroidUtilities.dp(203f) + AndroidUtilities.dp(100f)) {
							val restore = blurCanvas.save()
							blurCanvas.translate(viewPage.x, y + viewPage.y + recyclerListView.y + child.y)
							child.draw(blurCanvas)
							blurCanvas.restoreToCount(restore)
						}
					}
				}
			}
		}
	}

	private fun findRecyclerView(view: View): RecyclerListView? {
		if (view is ViewGroup) {
			for (i in 0 until view.childCount) {
				val child = view.getChildAt(i)

				if (child is RecyclerListView) {
					return child
				}
				else {
					(child as? ViewGroup)?.let { findRecyclerView(it) }
				}
			}
		}

		return null
	}

	companion object {
		private val interpolator = Interpolator {
			var t = it
			--t
			t * t * t * t * t + 1.0f
		}

		fun distanceInfluenceForSnapDuration(f: Float): Float {
			@Suppress("NAME_SHADOWING") var f = f
			f -= 0.5f
			f *= 0.47123894f
			return sin(f.toDouble()).toFloat()
		}
	}
}
