/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package androidx.recyclerview.widget

import android.content.Context
import android.graphics.PointF
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import org.telegram.messenger.AndroidUtilities
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

open class LinearSmoothScrollerCustom : RecyclerView.SmoothScroller {
	private val linearInterpolator = LinearInterpolator()
	private val decelerateInterpolator = DecelerateInterpolator(1.5f)
	private var targetVector: PointF? = null
	private var interimTargetDx = 0
	private var interimTargetDy = 0
	private var scrollPosition: Int
	private var durationMultiplier = 1f
	private var offset = 0
	private val millisPerPx: Float

	constructor(context: Context, position: Int) {
		millisPerPx = MILLISECONDS_PER_INCH / context.resources.displayMetrics.densityDpi
		scrollPosition = position
	}

	constructor(context: Context, position: Int, durationMultiplier: Float) {
		this.durationMultiplier = durationMultiplier
		millisPerPx = MILLISECONDS_PER_INCH / context.resources.displayMetrics.densityDpi * durationMultiplier
		scrollPosition = position
	}

	override fun onStart() {
		// stub
	}

	fun setOffset(offset: Int) {
		this.offset = offset
	}

	override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
		val dy = calculateDyToMakeVisible(targetView)
		val time = calculateTimeForDeceleration(dy)

		if (time > 0) {
			action.update(0, -dy, max((400 * durationMultiplier).toInt(), time), decelerateInterpolator)
		}
		else {
			onEnd()
		}
	}

	override fun onSeekTargetStep(dx: Int, dy: Int, state: RecyclerView.State, action: Action) {
		if (childCount == 0) {
			stop()
			return
		}

		interimTargetDx = clampApplyScroll(interimTargetDx, dx)
		interimTargetDy = clampApplyScroll(interimTargetDy, dy)

		if (interimTargetDx == 0 && interimTargetDy == 0) {
			updateActionForInterimTarget(action)
		}
	}

	override fun onStop() {
		interimTargetDy = 0
		interimTargetDx = 0
		targetVector = null
	}

	protected fun calculateTimeForDeceleration(dx: Int): Int {
		return ceil(calculateTimeForScrolling(dx) / .3356).toInt()
	}

	private fun calculateTimeForScrolling(dx: Int): Int {
		return ceil((abs(dx) * millisPerPx).toDouble()).toInt()
	}

	private fun updateActionForInterimTarget(action: Action) {
		val scrollVector = computeScrollVectorForPosition(targetPosition)

		if (scrollVector == null || scrollVector.x == 0f && scrollVector.y == 0f) {
			val target = targetPosition
			action.jumpTo(target)
			stop()
			return
		}

		normalize(scrollVector)

		targetVector = scrollVector
		interimTargetDx = (TARGET_SEEK_SCROLL_DISTANCE_PX * scrollVector.x).toInt()
		interimTargetDy = (TARGET_SEEK_SCROLL_DISTANCE_PX * scrollVector.y).toInt()

		val time = calculateTimeForScrolling(TARGET_SEEK_SCROLL_DISTANCE_PX)

		action.update((interimTargetDx * TARGET_SEEK_EXTRA_SCROLL_RATIO).toInt(), (interimTargetDy * TARGET_SEEK_EXTRA_SCROLL_RATIO).toInt(), (time * TARGET_SEEK_EXTRA_SCROLL_RATIO).toInt(), linearInterpolator)
	}

	private fun clampApplyScroll(tmpDt: Int, dt: Int): Int {
		@Suppress("NAME_SHADOWING") var tmpDt = tmpDt

		val before = tmpDt

		tmpDt -= dt

		return if (before * tmpDt <= 0) {
			0
		}
		else {
			tmpDt
		}
	}

	fun calculateDyToMakeVisible(view: View): Int {
		val layoutManager = layoutManager

		if (layoutManager == null || !layoutManager.canScrollVertically()) {
			return 0
		}

		val params = view.layoutParams as RecyclerView.LayoutParams
		val top = layoutManager.getDecoratedTop(view) - params.topMargin
		val bottom = layoutManager.getDecoratedBottom(view) + params.bottomMargin
		var start = layoutManager.paddingTop
		var end = layoutManager.height - layoutManager.paddingBottom
		val boxSize = end - start
		val viewSize = bottom - top

		start = if (scrollPosition == POSITION_TOP) {
			layoutManager.paddingTop + offset
		}
		else if (scrollPosition == POSITION_BOTTOM) {
			0
		}
		else if (viewSize > boxSize) {
			0
		}
		else if (scrollPosition == POSITION_MIDDLE) {
			(boxSize - viewSize) / 2
		}
		else {
			layoutManager.paddingTop + offset - AndroidUtilities.dp(88f)
		}

		end = start + viewSize

		val dtStart = start - top

		if (dtStart > 0) {
			return dtStart
		}

		val dtEnd = end - bottom

		return if (dtEnd < 0) {
			dtEnd
		}
		else {
			0
		}
	}

	override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
		return (layoutManager as? ScrollVectorProvider)?.computeScrollVectorForPosition(targetPosition)
	}

	open fun onEnd() {
		// stub
	}

	companion object {
		private const val MILLISECONDS_PER_INCH = 25f
		private const val TARGET_SEEK_SCROLL_DISTANCE_PX = 10000
		private const val TARGET_SEEK_EXTRA_SCROLL_RATIO = 1.2f
		const val POSITION_MIDDLE = 0
		const val POSITION_BOTTOM = 1
		const val POSITION_TOP = 2
	}
}
