/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.telegram.messenger.AndroidUtilities
import kotlin.math.ceil

class SeekBar(parent: View?) {
	interface SeekBarDelegate {
		fun onSeekBarDrag(progress: Float)
		fun onSeekBarContinuousDrag(progress: Float) {}
	}

	private var thumbX = 0
	private var draggingThumbX = 0
	private var thumbDX = 0
	private var width = 0
	private var height = 0
	private var delegate: SeekBarDelegate? = null
	private var backgroundColor = 0
	private var cacheColor = 0
	private var circleColor = 0
	private var progressColor = 0
	private var backgroundSelectedColor = 0
	private val rect = RectF()
	private var lineHeight = AndroidUtilities.dp(2f)
	private var selected = false
	private var bufferedProgress = 0f
	private var currentRadius: Float
	private val lastUpdateTime: Long = 0
	private val parentView: View?

	var isDragging = false
		private set

	init {
		parentView = parent
		currentRadius = AndroidUtilities.dp(6f).toFloat()
	}

	fun setDelegate(seekBarDelegate: SeekBarDelegate?) {
		delegate = seekBarDelegate
	}

	fun onTouch(action: Int, x: Float, y: Float): Boolean {
		if (action == MotionEvent.ACTION_DOWN) {
			val additionWidth = (height - thumbWidth) / 2
			if (x >= -additionWidth && x <= width + additionWidth && y >= 0 && y <= height) {
				if (!(thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth)) {
					thumbX = x.toInt() - thumbWidth / 2
					if (thumbX < 0) {
						thumbX = 0
					}
					else if (thumbX > width - thumbWidth) {
						thumbX = width - thumbWidth
					}
				}
				isDragging = true
				draggingThumbX = thumbX
				thumbDX = (x - thumbX).toInt()
				return true
			}
		}
		else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			if (isDragging) {
				thumbX = draggingThumbX
				if (action == MotionEvent.ACTION_UP && delegate != null) {
					delegate!!.onSeekBarDrag(thumbX.toFloat() / (width - thumbWidth).toFloat())
				}
				isDragging = false
				return true
			}
		}
		else if (action == MotionEvent.ACTION_MOVE) {
			if (isDragging) {
				draggingThumbX = (x - thumbDX).toInt()
				if (draggingThumbX < 0) {
					draggingThumbX = 0
				}
				else if (draggingThumbX > width - thumbWidth) {
					draggingThumbX = width - thumbWidth
				}
				if (delegate != null) {
					delegate!!.onSeekBarContinuousDrag(draggingThumbX.toFloat() / (width - thumbWidth).toFloat())
				}
				return true
			}
		}
		return false
	}

	fun setColors(background: Int, cache: Int, progress: Int, circle: Int, selected: Int) {
		backgroundColor = background
		cacheColor = cache
		circleColor = circle
		progressColor = progress
		backgroundSelectedColor = selected
	}

	fun setBufferedProgress(value: Float) {
		bufferedProgress = value
	}

	var progress: Float
		get() = thumbX.toFloat() / (width - thumbWidth).toFloat()
		set(progress) {
			thumbX = ceil(((width - thumbWidth) * progress).toDouble()).toInt()

			if (thumbX < 0) {
				thumbX = 0
			}
			else if (thumbX > width - thumbWidth) {
				thumbX = width - thumbWidth
			}
		}

	fun getThumbX(): Int {
		return (if (isDragging) draggingThumbX else thumbX) + thumbWidth / 2
	}

	fun setSelected(value: Boolean) {
		selected = value
	}

	fun setSize(w: Int, h: Int) {
		width = w
		height = h
	}

	fun getWidth(): Int {
		return width - thumbWidth
	}

	fun setLineHeight(value: Int) {
		lineHeight = value
	}

	fun draw(canvas: Canvas) {
		rect.set((thumbWidth / 2).toFloat(), (height / 2 - lineHeight / 2).toFloat(), (width - thumbWidth / 2).toFloat(), (height / 2 + lineHeight / 2).toFloat())
		paint.color = if (selected) backgroundSelectedColor else backgroundColor
		canvas.drawRoundRect(rect, (thumbWidth / 2).toFloat(), (thumbWidth / 2).toFloat(), paint)

		if (bufferedProgress > 0) {
			paint.color = if (selected) backgroundSelectedColor else cacheColor
			rect.set((thumbWidth / 2).toFloat(), (height / 2 - lineHeight / 2).toFloat(), thumbWidth / 2 + bufferedProgress * (width - thumbWidth), (height / 2 + lineHeight / 2).toFloat())
			canvas.drawRoundRect(rect, (thumbWidth / 2).toFloat(), (thumbWidth / 2).toFloat(), paint)
		}

		rect.set((thumbWidth / 2).toFloat(), (height / 2 - lineHeight / 2).toFloat(), (thumbWidth / 2 + if (isDragging) draggingThumbX else thumbX).toFloat(), (height / 2 + lineHeight / 2).toFloat())
		paint.color = progressColor

		canvas.drawRoundRect(rect, (thumbWidth / 2).toFloat(), (thumbWidth / 2).toFloat(), paint)
		paint.color = circleColor

		val newRad = AndroidUtilities.dp((if (isDragging) 8 else 6).toFloat())

		if (currentRadius != newRad.toFloat()) {
			val newUpdateTime = SystemClock.elapsedRealtime()
			var dt = newUpdateTime - lastUpdateTime

			if (dt > 18) {
				dt = 16
			}

			if (currentRadius < newRad) {
				currentRadius += AndroidUtilities.dp(1f) * (dt / 60.0f)

				if (currentRadius > newRad) {
					currentRadius = newRad.toFloat()
				}
			}
			else {
				currentRadius -= AndroidUtilities.dp(1f) * (dt / 60.0f)

				if (currentRadius < newRad) {
					currentRadius = newRad.toFloat()
				}
			}

			parentView?.invalidate()
		}

		canvas.drawCircle(((if (isDragging) draggingThumbX else thumbX) + thumbWidth / 2).toFloat(), (height / 2).toFloat(), currentRadius, paint)
	}

	companion object {
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val thumbWidth = AndroidUtilities.dp(24f)
	}
}
