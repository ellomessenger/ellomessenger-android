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
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.VibrationEffect
import android.util.StateSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.Keep
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R

class Switch(context: Context) : View(context) {
	private val rectF: RectF = RectF()
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val paint2 = Paint(Paint.ANTI_ALIAS_FLAG)
	private val pressedState = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
	private var progress = 0f
	private var checkAnimator: ObjectAnimator? = null
	private var iconAnimator: ObjectAnimator? = null
	private var attachedToWindow = false
	private var drawIconType = 0
	private var iconProgress = 1.0f
	private var onCheckedChangeListener: OnCheckedChangeListener? = null
	private val trackColor = ResourcesCompat.getColor(context.resources, R.color.purple, null) // Theme.key_switch2Track
	private val trackCheckedColor = ResourcesCompat.getColor(context.resources, R.color.brand, null) // Theme.key_switch2TrackChecked
	private val thumbColor = ResourcesCompat.getColor(context.resources, R.color.white, null) // Theme.key_windowBackgroundWhite
	private val thumbCheckedColor = ResourcesCompat.getColor(context.resources, R.color.white, null) // Theme.key_windowBackgroundWhite
	private var iconDrawable: Drawable? = null
	private var lastIconColor = 0
	private var drawRipple = false
	private var rippleDrawable: RippleDrawable? = null
	private var ripplePaint: Paint? = null
	private var colorSet = 0
	private var bitmapsCreated = false
	private var overlayBitmap: Array<Bitmap?>? = null
	private var overlayCanvas: Array<Canvas?>? = null
	private var overlayMaskBitmap: Bitmap? = null
	private var overlayMaskCanvas: Canvas? = null
	private var overlayCx = 0f
	private var overlayCy = 0f
	private var overlayRad = 0f
	private var overlayEraserPaint: Paint? = null
	private var overlayMaskPaint: Paint? = null
	private var overrideColorProgress = 0
	private var semHaptics = false

	var isChecked = false
		private set

	init {
		paint2.style = Paint.Style.STROKE
		paint2.strokeCap = Paint.Cap.ROUND
		paint2.strokeWidth = AndroidUtilities.dp(2f).toFloat()
		isHapticFeedbackEnabled = true
	}

	@Keep
	fun getProgress(): Float {
		return progress
	}

	@Keep
	fun setProgress(value: Float) {
		if (progress == value) {
			return
		}

		progress = value

		invalidate()
	}

	@Keep
	fun getIconProgress(): Float {
		return iconProgress
	}

	@Keep
	fun setIconProgress(value: Float) {
		if (iconProgress == value) {
			return
		}

		iconProgress = value

		invalidate()
	}

	private fun cancelCheckAnimator() {
		checkAnimator?.cancel()
		checkAnimator = null
	}

	private fun cancelIconAnimator() {
		iconAnimator?.cancel()
		iconAnimator = null
	}

	fun setDrawIconType(type: Int) {
		drawIconType = type
	}

	fun setDrawRipple(value: Boolean) {
		if (value == drawRipple) {
			return
		}

		drawRipple = value

		if (rippleDrawable == null) {
			ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
			ripplePaint?.color = -0x1

			val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(0))

			rippleDrawable = RippleDrawable(colorStateList, null, null)
			rippleDrawable?.radius = AndroidUtilities.dp(18f)
			rippleDrawable?.callback = this
		}

		if (isChecked && colorSet != 2 || !isChecked && colorSet != 1) {
			val color = if (isChecked) ResourcesCompat.getColor(context.resources, R.color.brand, null) else ResourcesCompat.getColor(context.resources, R.color.darker_brand, null)
			val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf(color))
			rippleDrawable?.setColor(colorStateList)
			colorSet = if (isChecked) 2 else 1
		}

		if (Build.VERSION.SDK_INT >= 28 && value) {
			rippleDrawable?.setHotspot(if (isChecked) 0f else AndroidUtilities.dp(100f).toFloat(), AndroidUtilities.dp(18f).toFloat())
		}

		rippleDrawable?.state = if (value) pressedState else StateSet.NOTHING

		invalidate()
	}

	override fun verifyDrawable(who: Drawable): Boolean {
		return super.verifyDrawable(who) || rippleDrawable != null && who === rippleDrawable
	}

	private fun animateToCheckedState(newCheckedState: Boolean) {
		checkAnimator = ObjectAnimator.ofFloat(this, "progress", if (newCheckedState) 1f else 0f)
		checkAnimator?.duration = if (semHaptics) 150 else 250.toLong()

		checkAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				checkAnimator = null
			}
		})

		checkAnimator?.start()
	}

	private fun animateIcon(newCheckedState: Boolean) {
		iconAnimator = ObjectAnimator.ofFloat(this, "iconProgress", if (newCheckedState) 1f else 0f)
		iconAnimator?.duration = if (semHaptics) 150 else 250

		iconAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				iconAnimator = null
			}
		})

		iconAnimator?.start()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		attachedToWindow = true
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attachedToWindow = false
	}

	fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
		onCheckedChangeListener = listener
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		setChecked(checked, drawIconType, animated)
	}

	fun setChecked(checked: Boolean, iconType: Int, animated: Boolean) {
		if (checked != isChecked) {
			isChecked = checked

			if (attachedToWindow && animated) {
				vibrateChecked()
				animateToCheckedState(checked)
			}
			else {
				cancelCheckAnimator()
				setProgress(if (checked) 1.0f else 0.0f)
			}

			onCheckedChangeListener?.onCheckedChanged(this, checked)
		}
		if (drawIconType != iconType) {
			drawIconType = iconType

			if (attachedToWindow && animated) {
				animateIcon(iconType == 0)
			}
			else {
				cancelIconAnimator()
				setIconProgress(if (iconType == 0) 1.0f else 0.0f)
			}
		}
	}

	fun setIcon(icon: Int) {
		if (icon != 0) {
			iconDrawable = ResourcesCompat.getDrawable(resources, icon, null)?.mutate()
			iconDrawable?.colorFilter = PorterDuffColorFilter(if (isChecked) trackCheckedColor else trackColor.also { lastIconColor = it }, PorterDuff.Mode.MULTIPLY)
		}
		else {
			iconDrawable = null
		}
	}

	fun hasIcon(): Boolean {
		return iconDrawable != null
	}

	fun setOverrideColor(override: Int) {
		if (overrideColorProgress == override) {
			return
		}

		if (overlayBitmap == null) {
			try {
				overlayBitmap = arrayOfNulls(2)
				overlayCanvas = arrayOfNulls(2)

				for (a in 0..1) {
					overlayBitmap!![a] = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
					overlayCanvas!![a] = Canvas(overlayBitmap!![a]!!)
				}

				overlayMaskBitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)

				overlayMaskCanvas = Canvas(overlayMaskBitmap!!)

				overlayEraserPaint = Paint(Paint.ANTI_ALIAS_FLAG)
				overlayEraserPaint?.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

				overlayMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
				overlayMaskPaint?.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

				bitmapsCreated = true
			}
			catch (e: Throwable) {
				return
			}
		}

		if (!bitmapsCreated) {
			return
		}

		overrideColorProgress = override
		overlayCx = 0f
		overlayCy = 0f
		overlayRad = 0f

		invalidate()
	}

	fun setOverrideColorProgress(cx: Float, cy: Float, rad: Float) {
		overlayCx = cx
		overlayCy = cy
		overlayRad = rad
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		if (visibility != VISIBLE) {
			return
		}

		val width = AndroidUtilities.dp(31f)
		val x = (measuredWidth - width) / 2
		val y = (measuredHeight - AndroidUtilities.dpf2(14f)) / 2
		var tx = x + AndroidUtilities.dp(7f) + (AndroidUtilities.dp(17f) * progress).toInt()
		var ty = measuredHeight / 2
		var color1: Int
		var color2: Int
		var colorProgress: Float
		var r1: Int
		var r2: Int
		var g1: Int
		var g2: Int
		var b1: Int
		var b2: Int
		var a1: Int
		var a2: Int
		var red: Int
		var green: Int
		var blue: Int
		var alpha: Int
		var color: Int

		for (a in 0..1) {
			if (a == 1 && overrideColorProgress == 0) {
				continue
			}

			val canvasToDraw = if (a == 0) canvas else overlayCanvas!![0]!!

			if (a == 1) {
				overlayBitmap!![0]!!.eraseColor(0)
				paint.color = -0x1000000
				overlayMaskCanvas?.drawRect(0f, 0f, overlayMaskBitmap!!.width.toFloat(), overlayMaskBitmap!!.height.toFloat(), paint)
				overlayMaskCanvas?.drawCircle(overlayCx - getX(), overlayCy - getY(), overlayRad, overlayEraserPaint!!)
			}

			colorProgress = if (overrideColorProgress == 1) {
				if (a == 0) 0f else 1f
			}
			else if (overrideColorProgress == 2) {
				if (a == 0) 1f else 0f
			}
			else {
				progress
			}

			color1 = trackColor
			color2 = trackCheckedColor

			if (a == 0 && iconDrawable != null && lastIconColor != if (isChecked) color2 else color1) {
				iconDrawable?.colorFilter = PorterDuffColorFilter((if (isChecked) color2 else color1).also { lastIconColor = it }, PorterDuff.Mode.MULTIPLY)
			}

			r1 = Color.red(color1)
			r2 = Color.red(color2)
			g1 = Color.green(color1)
			g2 = Color.green(color2)
			b1 = Color.blue(color1)
			b2 = Color.blue(color2)
			a1 = Color.alpha(color1)
			a2 = Color.alpha(color2)
			red = (r1 + (r2 - r1) * colorProgress).toInt()
			green = (g1 + (g2 - g1) * colorProgress).toInt()
			blue = (b1 + (b2 - b1) * colorProgress).toInt()
			alpha = (a1 + (a2 - a1) * colorProgress).toInt()
			color = alpha and 0xff shl 24 or (red and 0xff shl 16) or (green and 0xff shl 8) or (blue and 0xff)

			paint.color = color
			paint2.color = color
			rectF.set(x.toFloat(), y, (x + width).toFloat(), y + AndroidUtilities.dpf2(14f))

			canvasToDraw.drawRoundRect(rectF, AndroidUtilities.dpf2(7f), AndroidUtilities.dpf2(7f), paint)
			canvasToDraw.drawCircle(tx.toFloat(), ty.toFloat(), AndroidUtilities.dpf2(10f), paint)

			if (a == 0 && rippleDrawable != null) {
				rippleDrawable!!.setBounds(tx - AndroidUtilities.dp(18f), ty - AndroidUtilities.dp(18f), tx + AndroidUtilities.dp(18f), ty + AndroidUtilities.dp(18f))
				rippleDrawable!!.draw(canvasToDraw)
			}
			else if (a == 1) {
				canvasToDraw.drawBitmap(overlayMaskBitmap!!, 0f, 0f, overlayMaskPaint)
			}
		}

		if (overrideColorProgress != 0) {
			canvas.drawBitmap(overlayBitmap!![0]!!, 0f, 0f, null)
		}

		for (a in 0..1) {
			if (a == 1 && overrideColorProgress == 0) {
				continue
			}

			val canvasToDraw = if (a == 0) canvas else overlayCanvas!![1]!!

			if (a == 1) {
				overlayBitmap!![1]!!.eraseColor(0)
			}

			colorProgress = if (overrideColorProgress == 1) {
				if (a == 0) 0f else 1f
			}
			else if (overrideColorProgress == 2) {
				if (a == 0) 1f else 0f
			}
			else {
				progress
			}

			color1 = thumbColor
			color2 = thumbCheckedColor

			r1 = Color.red(color1)
			r2 = Color.red(color2)
			g1 = Color.green(color1)
			g2 = Color.green(color2)
			b1 = Color.blue(color1)
			b2 = Color.blue(color2)
			a1 = Color.alpha(color1)
			a2 = Color.alpha(color2)
			red = (r1 + (r2 - r1) * colorProgress).toInt()
			green = (g1 + (g2 - g1) * colorProgress).toInt()
			blue = (b1 + (b2 - b1) * colorProgress).toInt()
			alpha = (a1 + (a2 - a1) * colorProgress).toInt()

			paint.color = alpha and 0xff shl 24 or (red and 0xff shl 16) or (green and 0xff shl 8) or (blue and 0xff)

			canvasToDraw.drawCircle(tx.toFloat(), ty.toFloat(), AndroidUtilities.dp(8f).toFloat(), paint)

			if (a == 0) {
				if (iconDrawable != null) {
					iconDrawable?.setBounds(tx - iconDrawable!!.intrinsicWidth / 2, ty - iconDrawable!!.intrinsicHeight / 2, tx + iconDrawable!!.intrinsicWidth / 2, ty + iconDrawable!!.intrinsicHeight / 2)
					iconDrawable?.draw(canvasToDraw)
				}
				else if (drawIconType == 1) {
					tx -= (AndroidUtilities.dp(10.8f) - AndroidUtilities.dp(1.3f) * progress).toInt()
					ty -= (AndroidUtilities.dp(8.5f) - AndroidUtilities.dp(0.5f) * progress).toInt()

					val startX2 = AndroidUtilities.dpf2(4.6f).toInt() + tx
					val startY2 = (AndroidUtilities.dpf2(9.5f) + ty).toInt()
					val endX2 = startX2 + AndroidUtilities.dp(2f)
					val endY2 = startY2 + AndroidUtilities.dp(2f)
					var startX = AndroidUtilities.dpf2(7.5f).toInt() + tx
					var startY = AndroidUtilities.dpf2(5.4f).toInt() + ty
					var endX = startX + AndroidUtilities.dp(7f)
					var endY = startY + AndroidUtilities.dp(7f)

					startX = (startX + (startX2 - startX) * progress).toInt()
					startY = (startY + (startY2 - startY) * progress).toInt()
					endX = (endX + (endX2 - endX) * progress).toInt()
					endY = (endY + (endY2 - endY) * progress).toInt()

					canvasToDraw.drawLine(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), paint2)

					startX = AndroidUtilities.dpf2(7.5f).toInt() + tx
					startY = AndroidUtilities.dpf2(12.5f).toInt() + ty
					endX = startX + AndroidUtilities.dp(7f)
					endY = startY - AndroidUtilities.dp(7f)

					canvasToDraw.drawLine(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), paint2)
				}
				else if (drawIconType == 2 || iconAnimator != null) {
					paint2.alpha = (255 * (1.0f - iconProgress)).toInt()

					canvasToDraw.drawLine(tx.toFloat(), ty.toFloat(), tx.toFloat(), (ty - AndroidUtilities.dp(5f)).toFloat(), paint2)
					canvasToDraw.save()
					canvasToDraw.rotate(-90 * iconProgress, tx.toFloat(), ty.toFloat())
					canvasToDraw.drawLine(tx.toFloat(), ty.toFloat(), (tx + AndroidUtilities.dp(4f)).toFloat(), ty.toFloat(), paint2)
					canvasToDraw.restore()
				}
			}

			if (a == 1) {
				canvasToDraw.drawBitmap(overlayMaskBitmap!!, 0f, 0f, overlayMaskPaint)
			}
		}

		if (overrideColorProgress != 0) {
			canvas.drawBitmap(overlayBitmap!![1]!!, 0f, 0f, null)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.Switch"
		info.isCheckable = true
		info.isChecked = isChecked
	}

	private fun vibrateChecked() {
		try {
			if (isHapticFeedbackEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				val vibrator = AndroidUtilities.getVibrator()
				val vibrationEffect = VibrationEffect.createWaveform(longArrayOf(75, 10, 5, 10), intArrayOf(5, 20, 110, 20), -1)
				vibrator.cancel()
				vibrator.vibrate(vibrationEffect)
				semHaptics = true
			}
		}
		catch (ignore: Exception) {
		}
	}

	fun interface OnCheckedChangeListener {
		fun onCheckedChanged(view: Switch?, isChecked: Boolean)
	}
}
