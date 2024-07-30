/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts

import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.MotionEvent
import org.telegram.messenger.AndroidUtilities
import kotlin.math.sqrt

class ChartPickerDelegate(var view: Listener) {
	private var moveToAnimator: ValueAnimator? = null
	private var startTapTime: Long = 0
	private var tryMoveTo = false
	var disabled = false
	var leftPickerArea = Rect()
	var middlePickerArea = Rect()
	var minDistance = 0.1f
	var moveToX = 0f
	var moveToY = 0f
	var pickerEnd = 1f
	var pickerStart = 0.7f
	var pickerWidth = 0f
	var rightPickerArea = Rect()

	val middleCaptured: CapturesData?
		get() {
			val state0 = capturedStates[0]
			val state1 = capturedStates[1]

			return if (state0 != null && state0.state == CAPTURE_MIDDLE) {
				state0
			}
			else if (state1 != null && state1.state == CAPTURE_MIDDLE) {
				state1
			}
			else {
				null
			}
		}

	val leftCaptured: CapturesData?
		get() {
			val state0 = capturedStates[0]
			val state1 = capturedStates[1]

			return if (state0 != null && state0.state == CAPTURE_LEFT) {
				state0
			}
			else if (state1 != null && state1.state == CAPTURE_LEFT) {
				state1
			}
			else {
				null
			}
		}

	val rightCaptured: CapturesData?
		get() {
			val state0 = capturedStates[0]
			val state1 = capturedStates[1]

			return if (state0 != null && state0.state == CAPTURE_RIGHT) {
				state0
			}
			else if (state1 != null && state1.state == CAPTURE_RIGHT) {
				state1
			}
			else {
				null
			}
		}

	inner class CapturesData(val state: Int) {
		var capturedX = 0
		var lastMovingX = 0
		var start = 0f
		var end = 0f
		var a: ValueAnimator? = null
		private var jumpToAnimator: ValueAnimator? = null
		var aValue = 0f

		fun captured() {
			a = ValueAnimator.ofFloat(0f, 1f)
			a?.duration = 600
			a?.interpolator = BaseChartView.INTERPOLATOR

			a?.addUpdateListener {
				aValue = it.animatedValue as Float
				view.invalidate()
			}

			a?.start()
		}

		fun uncapture() {
			a?.cancel()
			jumpToAnimator?.cancel()
		}
	}

	private var capturedStates = arrayOf<CapturesData?>(null, null)

	fun capture(x: Int, y: Int, pointerIndex: Int): Boolean {
		if (disabled) {
			return false
		}

		if (pointerIndex == 0) {
			if (leftPickerArea.contains(x, y)) {
				if (capturedStates[0] != null) {
					capturedStates[1] = capturedStates[0]
				}

				capturedStates[0] = CapturesData(CAPTURE_LEFT)
				capturedStates[0]?.start = pickerStart
				capturedStates[0]?.capturedX = x
				capturedStates[0]?.lastMovingX = x
				capturedStates[0]?.captured()

				moveToAnimator?.cancel()

				return true
			}

			if (rightPickerArea.contains(x, y)) {
				if (capturedStates[0] != null) {
					capturedStates[1] = capturedStates[0]
				}

				capturedStates[0] = CapturesData(CAPTURE_RIGHT)
				capturedStates[0]?.end = pickerEnd
				capturedStates[0]?.capturedX = x
				capturedStates[0]?.lastMovingX = x
				capturedStates[0]?.captured()

				moveToAnimator?.cancel()

				return true
			}

			if (middlePickerArea.contains(x, y)) {
				capturedStates[0] = CapturesData(CAPTURE_MIDDLE)
				capturedStates[0]?.end = pickerEnd
				capturedStates[0]?.start = pickerStart
				capturedStates[0]?.capturedX = x
				capturedStates[0]?.lastMovingX = x
				capturedStates[0]?.captured()

				moveToAnimator?.cancel()

				return true
			}

			if (y < leftPickerArea.bottom && y > leftPickerArea.top) {
				tryMoveTo = true

				moveToX = x.toFloat()
				moveToY = y.toFloat()

				startTapTime = System.currentTimeMillis()

				if (moveToAnimator?.isRunning == true) {
					view.onPickerJumpTo(pickerStart, pickerEnd, true)
				}

				moveToAnimator?.cancel()

				return true
			}
		}
		else if (pointerIndex == 1) {
			if (capturedStates[0] == null) {
				return false
			}

			if (capturedStates[0]?.state == CAPTURE_MIDDLE) {
				return false
			}

			if (leftPickerArea.contains(x, y) && capturedStates[0]?.state != CAPTURE_LEFT) {
				capturedStates[1] = CapturesData(CAPTURE_LEFT)
				capturedStates[1]?.start = pickerStart
				capturedStates[1]?.capturedX = x
				capturedStates[1]?.lastMovingX = x
				capturedStates[1]?.captured()

				moveToAnimator?.cancel()

				return true
			}

			if (rightPickerArea.contains(x, y)) {
				if (capturedStates[0]?.state == CAPTURE_RIGHT) {
					return false
				}

				capturedStates[1] = CapturesData(CAPTURE_RIGHT)
				capturedStates[1]?.end = pickerEnd
				capturedStates[1]?.capturedX = x
				capturedStates[1]?.lastMovingX = x
				capturedStates[1]?.captured()

				moveToAnimator?.cancel()

				return true
			}
		}

		return false
	}

	fun captured(): Boolean {
		return capturedStates[0] != null || tryMoveTo
	}

	fun move(x: Int, y: Int, pointer: Int): Boolean {
		if (tryMoveTo) {
			return false
		}

		val d = capturedStates[pointer] ?: return false
		val capturedState = d.state
		val capturedStart = d.start
		val capturedEnd = d.end
		val capturedX = d.capturedX

		d.lastMovingX = x

		var notifyPicker = false

		if (capturedState == CAPTURE_LEFT) {
			pickerStart = capturedStart - (capturedX - x) / pickerWidth

			if (pickerStart < 0f) {
				pickerStart = 0f
			}

			if (pickerEnd - pickerStart < minDistance) {
				pickerStart = pickerEnd - minDistance
			}

			notifyPicker = true
		}

		if (capturedState == CAPTURE_RIGHT) {
			pickerEnd = capturedEnd - (capturedX - x) / pickerWidth

			if (pickerEnd > 1f) {
				pickerEnd = 1f
			}

			if (pickerEnd - pickerStart < minDistance) {
				pickerEnd = pickerStart + minDistance
			}

			notifyPicker = true
		}

		if (capturedState == CAPTURE_MIDDLE) {
			pickerStart = capturedStart - (capturedX - x) / pickerWidth
			pickerEnd = capturedEnd - (capturedX - x) / pickerWidth

			if (pickerStart < 0f) {
				pickerStart = 0f
				pickerEnd = capturedEnd - capturedStart
			}

			if (pickerEnd > 1f) {
				pickerEnd = 1f
				pickerStart = 1f - (capturedEnd - capturedStart)
			}

			notifyPicker = true
		}

		if (notifyPicker) {
			view.onPickerDataChanged()
		}

		return true
	}

	fun uncapture(event: MotionEvent, pointerIndex: Int): Boolean {
		if (pointerIndex == 0) {
			if (tryMoveTo) {
				tryMoveTo = false

				val dx = moveToX - event.x
				val dy = moveToY - event.y

				if (event.action == MotionEvent.ACTION_UP && System.currentTimeMillis() - startTapTime < 300 && sqrt((dx * dx + dy * dy).toDouble()) < AndroidUtilities.dp(10f)) {
					val moveToX = (moveToX - BaseChartView.HORIZONTAL_PADDING) / pickerWidth
					val w = pickerEnd - pickerStart
					var moveToLeft = moveToX - w / 2f
					var moveToRight = moveToX + w / 2f

					if (moveToLeft < 0f) {
						moveToLeft = 0f
						moveToRight = w
					}
					else if (moveToRight > 1f) {
						moveToLeft = 1f - w
						moveToRight = 1f
					}

					val moveFromLeft = pickerStart
					val moveFromRight = pickerEnd

					moveToAnimator = ValueAnimator.ofFloat(0f, 1f)

					val finalMoveToLeft = moveToLeft
					val finalMoveToRight = moveToRight

					view.onPickerJumpTo(finalMoveToLeft, finalMoveToRight, true)

					moveToAnimator?.addUpdateListener {
						val v = it.animatedValue as Float
						pickerStart = moveFromLeft + (finalMoveToLeft - moveFromLeft) * v
						pickerEnd = moveFromRight + (finalMoveToRight - moveFromRight) * v
						view.onPickerJumpTo(finalMoveToLeft, finalMoveToRight, false)
					}

					moveToAnimator?.interpolator = BaseChartView.INTERPOLATOR
					moveToAnimator?.start()
				}

				return true
			}

			capturedStates[0]?.uncapture()
			capturedStates[0] = null

			if (capturedStates[1] != null) {
				capturedStates[0] = capturedStates[1]
				capturedStates[1] = null
			}
		}
		else {
			capturedStates[1]?.uncapture()
			capturedStates[1] = null
		}

		return false
	}

	fun uncapture() {
		capturedStates[0]?.uncapture()
		capturedStates[0] = null

		capturedStates[1]?.uncapture()
		capturedStates[1] = null
	}

	interface Listener {
		fun onPickerDataChanged()
		fun onPickerJumpTo(start: Float, end: Float, force: Boolean)
		fun invalidate()
	}

	companion object {
		private const val CAPTURE_NONE = 0
		private const val CAPTURE_LEFT = 1
		private const val CAPTURE_RIGHT = 1 shl 1
		private const val CAPTURE_MIDDLE = 1 shl 2
	}
}
