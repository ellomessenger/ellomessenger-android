/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.GenericProvider
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import kotlin.math.sqrt

class CheckBoxBase(private val parentView: View, sz: Int) {
	private val bounds = Rect()
	private val rect = RectF()
	private val checkPaint: Paint
	private val backgroundPaint: Paint
	private val path = Path()
	private val drawBitmap: Bitmap
	private val bitmapCanvas: Canvas
	private val size: Float
	private var drawUnchecked = true
	private var backgroundType = 0
	private var checkedText: String? = null
	private var progressDelegate: ProgressDelegate? = null
	private var enabled = true
	private var attachedToWindow = false
	private var progress = 0f
	private var checkAnimator: ObjectAnimator? = null
	var animationDuration: Long = 200
	var useDefaultCheck = false
	var backgroundAlpha = 1.0f
	var circlePaintProvider = GenericProvider { _: Void? -> paint }

	@ColorInt
	private var checkmarkColor = parentView.context.getColor(R.color.white)

	@ColorInt
	private var uncheckedColor = parentView.context.getColor(R.color.brand)

	@ColorInt
	private var checkedColor = parentView.context.getColor(R.color.brand)

	private val textPaint by lazy {
		TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
			typeface = Theme.TYPEFACE_BOLD
		}
	}

	var isChecked = false
		private set

	init {
		size = sz.toFloat()

		if (paint == null) {
			paint = Paint(Paint.ANTI_ALIAS_FLAG)

			eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
				color = 0
				xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
			}
		}

		checkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		checkPaint.strokeCap = Paint.Cap.ROUND
		checkPaint.style = Paint.Style.STROKE
		checkPaint.strokeJoin = Paint.Join.ROUND
		checkPaint.strokeWidth = AndroidUtilities.dp(1.5f).toFloat()

		backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		backgroundPaint.style = Paint.Style.STROKE
		backgroundPaint.strokeWidth = AndroidUtilities.dp(1.5f).toFloat()

		drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(size), AndroidUtilities.dp(size), Bitmap.Config.ARGB_8888)

		bitmapCanvas = Canvas(drawBitmap)
	}

	fun onAttachedToWindow() {
		attachedToWindow = true
	}

	fun onDetachedFromWindow() {
		attachedToWindow = false
	}

	fun setBounds(x: Int, y: Int, width: Int, height: Int) {
		bounds.left = x
		bounds.top = y
		bounds.right = x + width
		bounds.bottom = y + height
	}

	fun setDrawUnchecked(value: Boolean) {
		drawUnchecked = value
	}

	private fun invalidate() {
		(parentView.parent as? View)?.invalidate()
		parentView.invalidate()
	}

	fun setProgressDelegate(delegate: ProgressDelegate?) {
		progressDelegate = delegate
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

		progressDelegate?.setProgress(value)
	}

	fun setEnabled(value: Boolean) {
		enabled = value
	}

	fun setBackgroundType(type: Int) {
		backgroundType = type

		if (type == 12 || type == 13) {
			backgroundPaint.strokeWidth = AndroidUtilities.dp(1f).toFloat()
		}
		else if (type == 4 || type == 5) {
			backgroundPaint.strokeWidth = AndroidUtilities.dp(1.9f).toFloat()
			if (type == 5) {
				checkPaint.strokeWidth = AndroidUtilities.dp(1.5f).toFloat()
			}
		}
		else if (type == 3) {
			backgroundPaint.strokeWidth = AndroidUtilities.dp(1.2f).toFloat()
		}
		else if (type != 0) {
			backgroundPaint.strokeWidth = AndroidUtilities.dp(1.5f).toFloat()
		}
	}

	private fun cancelCheckAnimator() {
		checkAnimator?.cancel()
		checkAnimator = null
	}

	private fun animateToCheckedState(newCheckedState: Boolean) {
		checkAnimator = ObjectAnimator.ofFloat(this, "progress", if (newCheckedState) 1f else 0f)

		checkAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				if (animation == checkAnimator) {
					checkAnimator = null
				}

				if (!isChecked) {
					checkedText = null
				}
			}
		})

		checkAnimator?.interpolator = CubicBezierInterpolator.EASE_OUT
		checkAnimator?.duration = animationDuration
		checkAnimator?.start()
	}

	fun setColor(@ColorInt unchecked: Int, @ColorInt checked: Int, @ColorInt checkmark: Int) {
		uncheckedColor = unchecked
		checkedColor = checked
		checkmarkColor = checkmark
		invalidate()
	}

	fun setNum(num: Int) {
		if (num >= 0) {
			checkedText = "" + (num + 1)
		}
		else if (checkAnimator == null) {
			checkedText = null
		}

		invalidate()
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		setChecked(-1, checked, animated)
	}

	fun setChecked(num: Int, checked: Boolean, animated: Boolean) {
		if (num >= 0) {
			checkedText = "" + (num + 1)
			invalidate()
		}

		if (checked == isChecked) {
			return
		}

		isChecked = checked

		if (attachedToWindow && animated) {
			animateToCheckedState(checked)
		}
		else {
			cancelCheckAnimator()
			setProgress(if (checked) 1.0f else 0.0f)
		}
	}

	fun draw(canvas: Canvas) {
		drawBitmap.eraseColor(0)

		var rad = AndroidUtilities.dp(size / 2).toFloat()
		var outerRad = rad

		if (backgroundType == 12 || backgroundType == 13) {
			outerRad = AndroidUtilities.dp(10f).toFloat()
			rad = outerRad
		}
		else {
			if (backgroundType != 0 && backgroundType != 11) {
				outerRad -= AndroidUtilities.dp(0.2f).toFloat()
			}
		}

		val roundProgress = if (progress >= 0.5f) 1.0f else progress / 0.5f
		val checkProgress = if (progress < 0.5f) 0.0f else (progress - 0.5f) / 0.5f
		val cx = bounds.centerX().toFloat()
		val cy = bounds.centerY().toFloat()

		if (uncheckedColor != 0) {
			if (drawUnchecked) {
				when (backgroundType) {
					12, 13 -> {
						paint?.color = uncheckedColor
						paint?.alpha = (255 * backgroundAlpha).toInt()
						backgroundPaint.color = checkmarkColor
					}

					6, 7 -> {
						paint?.color = checkedColor
						backgroundPaint.color = checkmarkColor
					}

					10 -> {
						backgroundPaint.color = checkedColor
					}

					else -> {
						paint?.color = ResourcesCompat.getColor(parentView.resources, R.color.white, null) // Theme.getServiceMessageColor() and 0x00ffffff or 0x28000000

						if (checkProgress != 0f) {
							backgroundPaint.color = ResourcesCompat.getColor(parentView.resources, R.color.white, null) // getThemedColor(checkColorKey)
						}
						else {
							backgroundPaint.color = ResourcesCompat.getColor(parentView.resources, R.color.inactive_brand, null) // getThemedColor(checkColorKey)
						}
					}
				}
			}
			else {
				backgroundPaint.color = AndroidUtilities.getOffsetColor(0x00ffffff, if (checkedColor != 0) checkedColor else checkmarkColor, progress, backgroundAlpha)
			}
		}
		else {
			if (drawUnchecked) {
				paint?.color = Color.argb((25 * backgroundAlpha).toInt(), 0, 0, 0)

				if (backgroundType == 8) {
					backgroundPaint.color = checkedColor
				}
				else {
					backgroundPaint.color = AndroidUtilities.getOffsetColor(-0x1, checkmarkColor, progress, backgroundAlpha)
				}
			}
			else {
				backgroundPaint.color = AndroidUtilities.getOffsetColor(0x00ffffff, if (checkedColor != 0) checkedColor else checkmarkColor, progress, backgroundAlpha)
			}
		}

		if (drawUnchecked && backgroundType >= 0) {
			when (backgroundType) {
				12, 13 -> {
					//draw nothing
				}

				8, 10 -> {
					val radius = rad - AndroidUtilities.dp(1.5f)
					canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cornerRadius, cornerRadius, backgroundPaint)
					// canvas.drawCircle(cx, cy, rad - AndroidUtilities.dp(1.5f), backgroundPaint)
				}

				6, 7 -> {
					var radius = rad - AndroidUtilities.dp(1f)

					canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cornerRadius, cornerRadius, paint!!)

					radius = rad - AndroidUtilities.dp(1.5f)
					canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cornerRadius, cornerRadius, backgroundPaint)

					// canvas.drawCircle(cx, cy, rad - AndroidUtilities.dp(1f), paint!!)
					// canvas.drawCircle(cx, cy, rad - AndroidUtilities.dp(1.5f), backgroundPaint)
				}

				else -> {
					// canvas.drawCircle(cx, cy, rad, paint!!)
					canvas.drawRoundRect(cx - rad, cy - rad, cx + rad, cy + rad, cornerRadius, cornerRadius, paint!!)
				}
			}
		}

		paint?.color = checkmarkColor

		if (backgroundType != -1 && backgroundType != 7 && backgroundType != 8 && backgroundType != 9 && backgroundType != 10) {
			when (backgroundType) {
				12, 13 -> {
					backgroundPaint.style = Paint.Style.FILL
					backgroundPaint.shader = null

					val radius = (rad - AndroidUtilities.dp(1f)) * backgroundAlpha

					// canvas.drawCircle(cx, cy, (rad - AndroidUtilities.dp(1f)) * backgroundAlpha, backgroundPaint)
					canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cornerRadius, cornerRadius, backgroundPaint)

					backgroundPaint.style = Paint.Style.STROKE
				}

				0, 11 -> {
					// canvas.drawCircle(cx, cy, rad, backgroundPaint)
					canvas.drawRoundRect(cx - rad, cy - rad, cx + rad, cy + rad, cornerRadius, cornerRadius, backgroundPaint)
				}

				else -> {
					rect.set(cx - outerRad, cy - outerRad, cx + outerRad, cy + outerRad)

					val startAngle: Float
					val sweepAngle: Float

					when (backgroundType) {
						6 -> {
							startAngle = 0f
							sweepAngle = (-360 * progress)
						}

						1 -> {
							startAngle = -90f
							sweepAngle = (-270 * progress)
						}

						else -> {
							startAngle = 90f
							sweepAngle = (270 * progress)
						}
					}

					if (backgroundType == 6) {
						var color = ApplicationLoader.applicationContext.getColor(R.color.background)
						var alpha = Color.alpha(color)

						backgroundPaint.color = color
						backgroundPaint.alpha = (alpha * progress).toInt()

						canvas.drawArc(rect, startAngle, sweepAngle, false, backgroundPaint)

						color = ApplicationLoader.applicationContext.getColor(R.color.light_background)
						alpha = Color.alpha(color)

						backgroundPaint.color = color
						backgroundPaint.alpha = (alpha * progress).toInt()
					}

					canvas.drawArc(rect, startAngle, sweepAngle, false, backgroundPaint)
				}
			}
		}

		if (roundProgress > 0) {
			if (backgroundType == 9) {
				paint?.color = checkedColor
			}
			else if (backgroundType == 11 || backgroundType == 6 || backgroundType == 7 || backgroundType == 10 || !drawUnchecked && uncheckedColor != 0) {
				paint?.color = uncheckedColor
			}
			else {
				paint?.color = ResourcesCompat.getColor(parentView.resources, if (enabled) R.color.brand else R.color.inactive_brand, null)
			}

			if (!useDefaultCheck && checkmarkColor != 0) {
				checkPaint.color = checkmarkColor
			}
			else {
				checkPaint.color = ApplicationLoader.applicationContext.getColor(R.color.brand)
			}

			if (backgroundType != -1) {
				val circlePaint = circlePaintProvider.provide(null)!!
				val cx1 = (drawBitmap.width / 2f)
				val cy1 = (drawBitmap.height / 2f)

				if (backgroundType == 12 || backgroundType == 13) {
					val a = circlePaint.alpha

					circlePaint.alpha = (255 * roundProgress).toInt()

					// bitmapCanvas.drawCircle((drawBitmap.width / 2f), (drawBitmap.height / 2f), rad * roundProgress, circlePaint)

					val radius = rad * roundProgress

					bitmapCanvas.drawRoundRect(cx1 - radius, cy1 - radius, cx1 + radius, cy1 + radius, cornerRadius, cornerRadius, circlePaint)

					if (circlePaint !== paint) {
						circlePaint.alpha = a
					}
				}
				else {
					rad -= AndroidUtilities.dp(0.5f).toFloat()

					// bitmapCanvas.drawCircle((drawBitmap.width / 2f), (drawBitmap.height / 2f), rad, circlePaint)
					// bitmapCanvas.drawCircle((drawBitmap.width / 2f), (drawBitmap.height / 2f), rad * (1.0f - roundProgress), eraser!!)

					var radius = rad
					val radiusDecrease = AndroidUtilities.dp(1f)

					bitmapCanvas.drawRoundRect(cx1 - radius, cy1 - radius, cx1 + radius, cy1 + radius, cornerRadius - radiusDecrease, cornerRadius - radiusDecrease, circlePaint)

					radius = rad * (1.0f - roundProgress)

					bitmapCanvas.drawRoundRect(cx1 - radius, cy1 - radius, cx1 + radius, cy1 + radius, cornerRadius, cornerRadius, eraser!!)
				}

				canvas.drawBitmap(drawBitmap, (cx - cx1), (cy - cy1), null)
			}

			if (checkProgress != 0f) {
				if (checkedText != null) {
					val textSize: Float
					val y: Float

					when (checkedText?.length) {
						0, 1, 2 -> {
							textSize = 14f
							y = 18f
						}

						3 -> {
							textSize = 10f
							y = 16.5f
						}

						else -> {
							textSize = 8f
							y = 15.75f
						}
					}

					textPaint.textSize = AndroidUtilities.dp(textSize).toFloat()
					textPaint.color = checkmarkColor

					canvas.save()
					canvas.scale(checkProgress, 1.0f, cx, cy)
					canvas.drawText(checkedText!!, cx - textPaint.measureText(checkedText) / 2f, AndroidUtilities.dp(y).toFloat(), textPaint)
					canvas.restore()
				}
				else {
					path.reset()

					var scale = 1f

					if (backgroundType == -1) {
						scale = 1.4f
					}
					else if (backgroundType == 5) {
						scale = 0.8f
					}

					val checkSide = AndroidUtilities.dp(9 * scale) * checkProgress
					val smallCheckSide = AndroidUtilities.dp(4 * scale) * checkProgress
					val x = cx - AndroidUtilities.dp(1.5f)
					val y = cy + AndroidUtilities.dp(4f)
					var side = sqrt(smallCheckSide * smallCheckSide / 2.0f)

					path.moveTo(x - side, y - side)
					path.lineTo(x, y)

					side = sqrt(checkSide * checkSide / 2.0f)

					path.lineTo(x + side, y - side)

					canvas.drawPath(path, checkPaint)
				}
			}
		}
	}

	fun interface ProgressDelegate {
		fun setProgress(progress: Float)
	}

	companion object {
		private var paint: Paint? = null
		private var eraser: Paint? = null
		private val cornerRadius = AndroidUtilities.dp(10f).toFloat()
	}
}
