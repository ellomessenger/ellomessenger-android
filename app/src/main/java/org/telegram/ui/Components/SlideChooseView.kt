/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class SlideChooseView(context: Context) : View(context) {
	private val accessibilityDelegate: SeekBarAccessibilityDelegate
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val linePaint: Paint
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private var lastDash = 0
	private var circleSize = 0
	private var gapSize = 0
	private var sideSide = 0
	private var lineSize = 0
	private var dashedFrom = -1
	private var moving = false
	private var startMoving = false
	private var xTouchDown = 0f
	private var yTouchDown = 0f
	private var startMovingPreset = 0
	private var optionsStr = arrayOf<String>()
	private var optionsSizes = intArrayOf()
	private val selectedIndexAnimatedHolder = AnimatedFloat(this, 120, CubicBezierInterpolator.DEFAULT)
	private val movingAnimatedHolder = AnimatedFloat(this, 150, CubicBezierInterpolator.DEFAULT)
	private var callback: Callback? = null

	var selectedIndex = 0
		private set

	init {
		textPaint.typeface = Theme.TYPEFACE_DEFAULT
		linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
		linePaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
		linePaint.strokeCap = Paint.Cap.ROUND
		textPaint.textSize = AndroidUtilities.dp(13f).toFloat()
		accessibilityDelegate = object : IntSeekBarAccessibilityDelegate() {
			override fun getProgress(): Int {
				return selectedIndex
			}

			override fun setProgress(progress: Int) {
				setOption(progress)
			}

			override fun getMaxValue(): Int {
				return optionsStr.size - 1
			}

			override fun getContentDescription(host: View?): CharSequence? {
				return if (selectedIndex < optionsStr.size) optionsStr[selectedIndex] else null
			}
		}
	}

	fun setCallback(callback: Callback?) {
		this.callback = callback
	}

	fun setOptions(selected: Int, vararg options: String) {
		optionsStr = arrayOf(*options)
		selectedIndex = selected
		optionsSizes = IntArray(optionsStr.size)

		for (i in optionsStr.indices) {
			optionsSizes[i] = ceil(textPaint.measureText(optionsStr[i]).toDouble()).toInt()
		}

		requestLayout()
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		val x = event.x
		val y = event.y
		var indexTouch = MathUtils.clamp((x - sideSide + circleSize / 2f) / (lineSize + gapSize * 2 + circleSize), 0f, (optionsStr.size - 1).toFloat())
		val isClose = abs(indexTouch - indexTouch.roundToLong()) < .35f

		if (isClose) {
			indexTouch = indexTouch.roundToLong().toFloat()
		}

		val selectedIndexTouch: Float

		when (event.action) {
			MotionEvent.ACTION_DOWN -> {
				xTouchDown = x
				yTouchDown = y
				startMovingPreset = selectedIndex
				startMoving = true
				invalidate()
			}

			MotionEvent.ACTION_MOVE -> {
				if (!moving) {
					if (abs(xTouchDown - x) > abs(yTouchDown - y)) {
						parent?.requestDisallowInterceptTouchEvent(true)
					}
				}

				if (startMoving) {
					if (abs(xTouchDown - x) >= AndroidUtilities.dp(2f)) {
						moving = true
						startMoving = false
					}
				}

				if (moving) {
					selectedIndexTouch = indexTouch
					invalidate()

					if (selectedIndexTouch.roundToInt() != selectedIndex && isClose) {
						setOption(selectedIndexTouch.roundToInt())
					}
				}

				invalidate()
			}

			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				if (!moving) {
					selectedIndexTouch = indexTouch

					if (selectedIndexTouch.roundToInt() != selectedIndex) {
						setOption(selectedIndexTouch.roundToInt())
					}
				}
				else {
					if (selectedIndex != startMovingPreset) {
						setOption(selectedIndex)
					}
				}

				callback?.onTouchEnd()

				startMoving = false
				moving = false

				invalidate()

				parent?.requestDisallowInterceptTouchEvent(false)
			}
		}

		return true
	}

	private fun setOption(index: Int) {
		if (selectedIndex != index) {
			runCatching {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
					performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
				}
			}
		}

		selectedIndex = index

		callback?.onOptionSelected(index)

		invalidate()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(74f), MeasureSpec.EXACTLY))
		circleSize = AndroidUtilities.dp(6f)
		gapSize = AndroidUtilities.dp(2f)
		sideSide = AndroidUtilities.dp(22f)
		lineSize = (measuredWidth - circleSize * optionsStr.size - gapSize * 2 * (optionsStr.size - 1) - sideSide * 2) / (optionsStr.size - 1)
	}

	override fun onDraw(canvas: Canvas) {
		val context = context ?: return
		val selectedIndexAnimated = selectedIndexAnimatedHolder.set(selectedIndex.toFloat())
		val movingAnimated = movingAnimatedHolder.set((if (moving) 1 else 0).toFloat())
		val cy = measuredHeight / 2 + AndroidUtilities.dp(11f)

		for (a in optionsStr.indices) {
			val cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2
			val t = max(0f, 1f - abs(a - selectedIndexAnimated))
			val ut = MathUtils.clamp(selectedIndexAnimated - a + 1f, 0f, 1f)
			val color = ColorUtils.blendARGB(ResourcesCompat.getColor(context.resources, R.color.brand_transparent, null), ResourcesCompat.getColor(context.resources, R.color.brand, null), ut)

			paint.color = color
			linePaint.color = color

			canvas.drawCircle(cx.toFloat(), cy.toFloat(), AndroidUtilities.lerp(circleSize / 2, AndroidUtilities.dp(6f), t).toFloat(), paint)

			if (a != 0) {
				var x = cx - circleSize / 2 - gapSize - lineSize
				var width = lineSize

				if (dashedFrom != -1 && a - 1 >= dashedFrom) {
					x += AndroidUtilities.dp(3f)
					width -= AndroidUtilities.dp(3f)

					val dash = width / AndroidUtilities.dp(13f)

					if (lastDash != dash) {
						val gap = (width - dash * AndroidUtilities.dp(8f)) / (dash - 1).toFloat()
						linePaint.pathEffect = DashPathEffect(floatArrayOf(AndroidUtilities.dp(6f).toFloat(), gap), 0f)
						lastDash = dash
					}

					canvas.drawLine((x + AndroidUtilities.dp(1f)).toFloat(), cy.toFloat(), (x + width - AndroidUtilities.dp(1f)).toFloat(), cy.toFloat(), linePaint)
				}
				else {
					val nt = MathUtils.clamp(1f - abs(a - selectedIndexAnimated - 1), 0f, 1f)
					val nct = MathUtils.clamp(1f - min(abs(a - selectedIndexAnimated), abs(a - selectedIndexAnimated - 1)), 0f, 1f)
					width -= (AndroidUtilities.dp(3f) * nct).toInt()
					x += (AndroidUtilities.dp(3f) * nt).toInt()
					canvas.drawRect(x.toFloat(), (cy - AndroidUtilities.dp(1f)).toFloat(), (x + width).toFloat(), (cy + AndroidUtilities.dp(1f)).toFloat(), paint)
				}
			}

			val size = optionsSizes[a]
			val text = optionsStr[a]

			textPaint.color = ColorUtils.blendARGB(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), ResourcesCompat.getColor(context.resources, R.color.brand, null), t)

			when (a) {
				0 -> {
					canvas.drawText(text, AndroidUtilities.dp(22f).toFloat(), AndroidUtilities.dp(28f).toFloat(), textPaint)
				}

				optionsStr.size - 1 -> {
					canvas.drawText(text, (measuredWidth - size - AndroidUtilities.dp(22f)).toFloat(), AndroidUtilities.dp(28f).toFloat(), textPaint)
				}

				else -> {
					canvas.drawText(text, (cx - size / 2).toFloat(), AndroidUtilities.dp(28f).toFloat(), textPaint)
				}
			}
		}

		val cx = sideSide + (lineSize + gapSize * 2 + circleSize) * selectedIndexAnimated + circleSize / 2

		paint.color = ColorUtils.setAlphaComponent(ResourcesCompat.getColor(context.resources, R.color.brand, null), 80)

		canvas.drawCircle(cx, cy.toFloat(), AndroidUtilities.dp(12 * movingAnimated).toFloat(), paint)

		paint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)

		canvas.drawCircle(cx, cy.toFloat(), AndroidUtilities.dp(6f).toFloat(), paint)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		accessibilityDelegate.onInitializeAccessibilityNodeInfoInternal(this, info)
	}

	override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
		return super.performAccessibilityAction(action, arguments) || accessibilityDelegate.performAccessibilityActionInternal(this, action, arguments)
	}

	interface Callback {
		fun onOptionSelected(index: Int)
		fun onTouchEnd() {}
	}
}
