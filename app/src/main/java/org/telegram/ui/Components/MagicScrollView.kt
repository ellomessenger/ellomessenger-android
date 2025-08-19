/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ScrollView
import kotlin.math.abs

class MagicScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ScrollView(context, attrs, defStyleAttr) {
	private var startY = 0f
	private var startX = 0f
	private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
	private var isVerticalScroll: Boolean? = null
	private var activePointerId = MotionEvent.INVALID_POINTER_ID

	init {
		isNestedScrollingEnabled = true
		descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
	}

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
		when (ev.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				activePointerId = ev.getPointerId(0)
				startY = ev.getY(ev.findPointerIndex(activePointerId))
				startX = ev.getX(ev.findPointerIndex(activePointerId))
				isVerticalScroll = null

				parent.requestDisallowInterceptTouchEvent(true)
			}

			MotionEvent.ACTION_MOVE -> {
				val pointerIndex = ev.findPointerIndex(activePointerId)

				if (pointerIndex < 0) {
					return false
				}

				val y = ev.getY(pointerIndex)
				val x = ev.getX(pointerIndex)

				if (isVerticalScroll == null) {
					val diffY = abs(y - startY)
					val diffX = abs(x - startX)

					if (diffY > touchSlop || diffX > touchSlop) {
						isVerticalScroll = diffY > diffX

						if (isVerticalScroll == false) {
							parent.requestDisallowInterceptTouchEvent(false)
							return false
						}
					}
				}

				if (isVerticalScroll == true) {
					val currentDiffY = y - startY

					if ((scrollY == 0 && currentDiffY > 0) || (getChildAt(0).height - height == scrollY && currentDiffY < 0)) {
						parent.requestDisallowInterceptTouchEvent(false)
					}
					else {
						parent.requestDisallowInterceptTouchEvent(true)
					}
				}
			}

			MotionEvent.ACTION_POINTER_UP -> {
				val pointerIndex = ev.actionIndex
				val pointerId = ev.getPointerId(pointerIndex)

				if (pointerId == activePointerId) {
					val newPointerIndex = if (pointerIndex == 0) 1 else 0
					startY = ev.getY(newPointerIndex)
					startX = ev.getX(newPointerIndex)
					activePointerId = ev.getPointerId(newPointerIndex)
				}
			}

			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				activePointerId = MotionEvent.INVALID_POINTER_ID
				isVerticalScroll = null
				parent.requestDisallowInterceptTouchEvent(false)
			}
		}

		return try {
			super.onInterceptTouchEvent(ev)
		}
		catch (e: IllegalArgumentException) {
			false
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(ev: MotionEvent): Boolean {
		return try {
			when (ev.actionMasked) {
				MotionEvent.ACTION_MOVE -> {
					if (isVerticalScroll == false) return false

					val pointerIndex = ev.findPointerIndex(activePointerId)

					if (pointerIndex < 0) {
						return false
					}

					super.onTouchEvent(ev)
				}

				else -> super.onTouchEvent(ev)
			}
		}
		catch (e: IllegalArgumentException) {
			false
		}
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
			isVerticalScroll = null
			activePointerId = MotionEvent.INVALID_POINTER_ID
		}

		return super.dispatchTouchEvent(ev)
	}
}
