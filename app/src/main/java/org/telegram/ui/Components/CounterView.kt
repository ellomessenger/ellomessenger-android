package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

open class CounterView(context: Context) : View(context) {
	var counterDrawable: CounterDrawable

	init {
		visibility = GONE
		counterDrawable = CounterDrawable(this, true, false)
		counterDrawable.updateVisibility = true
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		counterDrawable.setSize(measuredHeight, measuredWidth)
	}

	override fun onDraw(canvas: Canvas) {
		counterDrawable.draw(canvas)
	}

	fun setGravity(gravity: Int) {
		counterDrawable.gravity = gravity
	}

	fun setReverse(b: Boolean) {
		counterDrawable.reverseAnimation = b
	}

	fun setCount(count: Int, animated: Boolean) {
		counterDrawable.setCount(count, animated)
	}

	class CounterDrawable(var parent: View?, private val drawBackground: Boolean, private val drawWhenZero: Boolean) {
		var shortFormat = false
		var animationType = -1
		var circlePaint: Paint? = null
		var textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		var rectF = RectF()
		var addServiceGradient = false
		var currentCount = 0
		private var countAnimationIncrement = false
		private var countAnimator: ValueAnimator? = null
		var countChangeProgress = 1f
		private var countLayout: StaticLayout? = null
		private var countOldLayout: StaticLayout? = null
		private var countAnimationStableLayout: StaticLayout? = null
		private var countAnimationInLayout: StaticLayout? = null
		private var countWidthOld = 0
		private var countWidth = 0
		private var circleColor = 0
		private var textColor = 0
		var lastH = 0
		var width = 0
		var gravity = Gravity.CENTER
		private var countLeft = 0f
		var x = 0f
		var reverseAnimation = false
		var horizontalPadding = 0f
		var updateVisibility = false
		var type = TYPE_DEFAULT

		init {
			if (drawBackground) {
				circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
				circlePaint?.color = Color.BLACK
			}

			textPaint.typeface = Theme.TYPEFACE_BOLD
			textPaint.textSize = AndroidUtilities.dp(13f).toFloat()
		}

		fun setSize(h: Int, w: Int) {
			if (h != lastH) {
				val count = currentCount
				currentCount = -1
				setCount(count, animationType == ANIMATION_TYPE_IN)
				lastH = h
			}

			width = w
		}

		private fun drawInternal(canvas: Canvas) {
			val countTop = (lastH - AndroidUtilities.dp(23f)) / 2f

			updateX(countWidth.toFloat())

			rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11f), countTop + AndroidUtilities.dp(23f))

			if (circlePaint != null && drawBackground) {
				canvas.drawRoundRect(rectF, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, circlePaint!!)

				if (addServiceGradient && Theme.hasGradientService()) {
					canvas.drawRoundRect(rectF, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.chat_actionBackgroundGradientDarkenPaint)
				}
			}

			if (countLayout != null) {
				canvas.save()
				canvas.translate(countLeft, countTop + AndroidUtilities.dp(4f))
				countLayout?.draw(canvas)
				canvas.restore()
			}
		}

		fun setCount(count: Int, animated: Boolean) {
			@Suppress("NAME_SHADOWING") var animated = animated

			if (count == currentCount) {
				return
			}

			countAnimator?.cancel()

			if ((drawWhenZero || count > 0) && updateVisibility) {
				parent?.visibility = VISIBLE
			}

			if (abs(count - currentCount) > 99) {
				animated = false
			}

			if (!animated) {
				currentCount = count

				if (count == 0 && !drawWhenZero) {
					if (updateVisibility) {
						parent?.visibility = GONE
					}

					return
				}

				val newStr = getStringOfCCount(count)

				countWidth = max(AndroidUtilities.dp(12f), ceil(textPaint.measureText(newStr).toDouble()).toInt())
				countLayout = StaticLayout(newStr, textPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

				parent?.invalidate()
			}

			val newStr = getStringOfCCount(count)

			if (animated) {
				countAnimator?.cancel()

				countChangeProgress = 0f

				countAnimator = ValueAnimator.ofFloat(0f, 1f)

				countAnimator?.addUpdateListener {
					countChangeProgress = it.animatedValue as Float
					parent?.invalidate()
				}

				countAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						countChangeProgress = 1f
						countOldLayout = null
						countAnimationStableLayout = null
						countAnimationInLayout = null

						if (currentCount == 0 && updateVisibility) {
							parent?.visibility = GONE
						}

						parent?.invalidate()

						animationType = -1
					}
				})

				if (currentCount <= 0) {
					animationType = ANIMATION_TYPE_IN
					countAnimator?.duration = 220
					countAnimator?.interpolator = OvershootInterpolator()
				}
				else if (count == 0) {
					animationType = ANIMATION_TYPE_OUT
					countAnimator?.duration = 150
					countAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
				}
				else {
					animationType = ANIMATION_TYPE_REPLACE
					countAnimator?.duration = 430
					countAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
				}

				if (countLayout != null) {
					val oldStr = getStringOfCCount(currentCount)

					if (oldStr.length == newStr.length) {
						val oldSpannableStr = SpannableStringBuilder(oldStr)
						val newSpannableStr = SpannableStringBuilder(newStr)
						val stableStr = SpannableStringBuilder(newStr)

						for (i in oldStr.indices) {
							if (oldStr[i] == newStr[i]) {
								oldSpannableStr.setSpan(EmptyStubSpan(), i, i + 1, 0)
								newSpannableStr.setSpan(EmptyStubSpan(), i, i + 1, 0)
							}
							else {
								stableStr.setSpan(EmptyStubSpan(), i, i + 1, 0)
							}
						}

						val countOldWidth = max(AndroidUtilities.dp(12f), ceil(textPaint.measureText(oldStr).toDouble()).toInt())

						countOldLayout = StaticLayout(oldSpannableStr, textPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
						countAnimationStableLayout = StaticLayout(stableStr, textPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
						countAnimationInLayout = StaticLayout(newSpannableStr, textPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
					}
					else {
						countOldLayout = countLayout
					}
				}

				countWidthOld = countWidth
				countAnimationIncrement = count > currentCount

				countAnimator?.start()
			}

			if (count > 0 || drawWhenZero) {
				countWidth = max(AndroidUtilities.dp(12f), ceil(textPaint.measureText(newStr).toDouble()).toInt())
				countLayout = StaticLayout(newStr, textPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
			}

			currentCount = count

			parent?.invalidate()
		}

		private fun getStringOfCCount(count: Int): String {
			return if (shortFormat) {
				AndroidUtilities.formatWholeNumber(count, 0)
			}
			else {
				count.toString()
			}
		}

		fun draw(canvas: Canvas) {
			if (type != TYPE_CHAT_PULLING_DOWN && type != TYPE_CHAT_REACTIONS) {
				val textColor = ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.white, null)
				val circleColor = ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.brand, null)

				if (this.textColor != textColor) {
					this.textColor = textColor
					textPaint.color = textColor
				}

				if (circlePaint != null && this.circleColor != circleColor) {
					this.circleColor = circleColor
					circlePaint?.color = circleColor
				}
			}

			if (countChangeProgress != 1f) {
				if (animationType == ANIMATION_TYPE_IN || animationType == ANIMATION_TYPE_OUT) {
					updateX(countWidth.toFloat())
					val cx = countLeft + countWidth / 2f
					val cy = lastH / 2f
					canvas.save()
					val progress = if (animationType == ANIMATION_TYPE_IN) countChangeProgress else 1f - countChangeProgress
					canvas.scale(progress, progress, cx, cy)
					drawInternal(canvas)
					canvas.restore()
				}
				else {
					var progressHalf = countChangeProgress * 2

					if (progressHalf > 1f) {
						progressHalf = 1f
					}

					val countTop = (lastH - AndroidUtilities.dp(23f)) / 2f
					val countWidth = if (this.countWidth == countWidthOld) {
						this.countWidth.toFloat()
					}
					else {
						this.countWidth * progressHalf + countWidthOld * (1f - progressHalf)
					}

					updateX(countWidth)

					var scale = 1f

					if (countAnimationIncrement) {
						scale += if (countChangeProgress <= 0.5f) {
							0.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(countChangeProgress * 2)
						}
						else {
							0.1f * CubicBezierInterpolator.EASE_IN.getInterpolation(1f - (countChangeProgress - 0.5f) * 2)
						}
					}

					rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11f), countTop + AndroidUtilities.dp(23f))

					canvas.save()
					canvas.scale(scale, scale, rectF.centerX(), rectF.centerY())

					if (drawBackground && circlePaint != null) {
						canvas.drawRoundRect(rectF, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, circlePaint!!)

						if (addServiceGradient && Theme.hasGradientService()) {
							canvas.drawRoundRect(rectF, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.chat_actionBackgroundGradientDarkenPaint)
						}
					}

					canvas.clipRect(rectF)

					val increment = reverseAnimation != countAnimationIncrement

					if (countAnimationInLayout != null) {
						canvas.save()
						canvas.translate(countLeft, countTop + AndroidUtilities.dp(4f) + (if (increment) AndroidUtilities.dp(13f) else -AndroidUtilities.dp(13f)) * (1f - progressHalf))
						textPaint.alpha = (255 * progressHalf).toInt()
						countAnimationInLayout?.draw(canvas)
						canvas.restore()
					}
					else if (countLayout != null) {
						canvas.save()
						canvas.translate(countLeft, countTop + AndroidUtilities.dp(4f) + (if (increment) AndroidUtilities.dp(13f) else -AndroidUtilities.dp(13f)) * (1f - progressHalf))
						textPaint.alpha = (255 * progressHalf).toInt()
						countLayout!!.draw(canvas)
						canvas.restore()
					}

					if (countOldLayout != null) {
						canvas.save()
						canvas.translate(countLeft, countTop + AndroidUtilities.dp(4f) + (if (increment) -AndroidUtilities.dp(13f) else AndroidUtilities.dp(13f)) * progressHalf)
						textPaint.alpha = (255 * (1f - progressHalf)).toInt()
						countOldLayout?.draw(canvas)
						canvas.restore()
					}

					if (countAnimationStableLayout != null) {
						canvas.save()
						canvas.translate(countLeft, countTop + AndroidUtilities.dp(4f))
						textPaint.alpha = 255
						countAnimationStableLayout!!.draw(canvas)
						canvas.restore()
					}

					textPaint.alpha = 255

					canvas.restore()
				}
			}
			else {
				drawInternal(canvas)
			}
		}

		fun updateBackgroundRect() {
			if (countChangeProgress != 1f) {
				if (animationType == ANIMATION_TYPE_IN || animationType == ANIMATION_TYPE_OUT) {
					updateX(countWidth.toFloat())

					val countTop = (lastH - AndroidUtilities.dp(23f)) / 2f

					rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11f), countTop + AndroidUtilities.dp(23f))
				}
				else {
					var progressHalf = countChangeProgress * 2

					if (progressHalf > 1f) {
						progressHalf = 1f
					}

					val countTop = (lastH - AndroidUtilities.dp(23f)) / 2f

					val countWidth = if (this.countWidth == countWidthOld) {
						this.countWidth.toFloat()
					}
					else {
						this.countWidth * progressHalf + countWidthOld * (1f - progressHalf)
					}

					updateX(countWidth)

					rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11f), countTop + AndroidUtilities.dp(23f))
				}
			}
			else {
				updateX(countWidth.toFloat())
				val countTop = (lastH - AndroidUtilities.dp(23f)) / 2f
				rectF.set(x, countTop, x + countWidth + AndroidUtilities.dp(11f), countTop + AndroidUtilities.dp(23f))
			}
		}

		private fun updateX(countWidth: Float) {
			val padding = if (drawBackground) AndroidUtilities.dp(5.5f).toFloat() else 0f

			when (gravity) {
				Gravity.RIGHT -> {
					countLeft = width - padding

					countLeft -= if (horizontalPadding != 0f) {
						max(horizontalPadding + countWidth / 2f, countWidth)
					}
					else {
						countWidth
					}
				}

				Gravity.LEFT -> {
					countLeft = padding
				}

				else -> {
					countLeft = ((width - countWidth) / 2f).toInt().toFloat()
				}
			}

			x = countLeft - padding
		}

		val centerX: Float
			get() {
				updateX(countWidth.toFloat())
				return countLeft + countWidth / 2f
			}

		companion object {
			const val ANIMATION_TYPE_IN = 0
			const val ANIMATION_TYPE_OUT = 1
			const val ANIMATION_TYPE_REPLACE = 2
			const val TYPE_DEFAULT = 0
			const val TYPE_CHAT_PULLING_DOWN = 1
			const val TYPE_CHAT_REACTIONS = 2
		}
	}

	val enterProgress: Float
		get() = if (counterDrawable.countChangeProgress != 1f && (counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_IN || counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_OUT)) {
			if (counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_IN) {
				counterDrawable.countChangeProgress
			}
			else {
				1f - counterDrawable.countChangeProgress
			}
		}
		else {
			if (counterDrawable.currentCount == 0) 0f else 1f
		}

	val isInOutAnimation: Boolean
		get() = counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_IN || counterDrawable.animationType == CounterDrawable.ANIMATION_TYPE_OUT
}
