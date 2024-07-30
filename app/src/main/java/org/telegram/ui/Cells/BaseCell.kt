/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup

abstract class BaseCell(context: Context) : ViewGroup(context) {
	private var checkingForLongPress = false
	private var pendingCheckForLongPress: CheckForLongPress? = null
	private var pressCount = 0
	private var pendingCheckForTap: CheckForTap? = null

	private inner class CheckForTap : Runnable {
		override fun run() {
			if (pendingCheckForLongPress == null) {
				pendingCheckForLongPress = CheckForLongPress()
			}

			pendingCheckForLongPress?.currentPressCount = ++pressCount

			postDelayed(pendingCheckForLongPress, (ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout()).toLong())
		}
	}

	internal inner class CheckForLongPress : Runnable {
		var currentPressCount = 0

		override fun run() {
			if (checkingForLongPress && parent != null && currentPressCount == pressCount) {
				checkingForLongPress = false

				if (onLongPress()) {
					performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
					val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
					onTouchEvent(event)
					event.recycle()
				}
			}
		}
	}

	init {
		setWillNotDraw(false)
		isFocusable = true
		isHapticFeedbackEnabled = true
	}

	protected fun startCheckLongPress() {
		if (checkingForLongPress) {
			return
		}

		checkingForLongPress = true

		if (pendingCheckForTap == null) {
			pendingCheckForTap = CheckForTap()
		}

		postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout().toLong())
	}

	protected fun cancelCheckLongPress() {
		checkingForLongPress = false

		pendingCheckForLongPress?.let {
			removeCallbacks(it)
		}

		pendingCheckForTap?.let {
			removeCallbacks(it)
		}
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	protected open fun onLongPress(): Boolean {
		return true
	}

	companion object {
		@JvmStatic
		fun setDrawableBounds(drawable: Drawable?, x: Int, y: Int) {
			if (drawable != null) {
				setDrawableBounds(drawable, x, y, drawable.intrinsicWidth, drawable.intrinsicHeight)
			}
		}

		fun setDrawableBounds(drawable: Drawable?, x: Float, y: Float) {
			if (drawable != null) {
				setDrawableBounds(drawable, x.toInt(), y.toInt(), drawable.intrinsicWidth, drawable.intrinsicHeight)
			}
		}

		@JvmStatic
		fun setDrawableBounds(drawable: Drawable?, x: Int, y: Int, w: Int, h: Int) {
			drawable?.setBounds(x, y, x + w, y + h)
		}

		fun setDrawableBounds(drawable: Drawable?, x: Float, y: Float, w: Int, h: Int) {
			drawable?.setBounds(x.toInt(), y.toInt(), x.toInt() + w, y.toInt() + h)
		}
	}
}
