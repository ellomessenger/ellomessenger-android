/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components.voip

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class VoIPToggleButton @JvmOverloads constructor(context: Context, private val radius: Float = 52f) : FrameLayout(context) {
	private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var drawBackground = true
	private var animateBackground = false
	private val icon = arrayOfNulls<Drawable>(2)
	private val textLayoutContainer = FrameLayout(context)
	private val textView: Array<TextView>
	private var backgroundColor = 0
	private var animateToBackgroundColor = 0
	private var replaceProgress = 0f
	private var replaceAnimator: ValueAnimator? = null
	private var currentIconRes = 0
	private var currentIconColor = 0
	private var currentBackgroundColor = 0
	private var currentText: String? = null
	private var iconChangeColor = false
	private var replaceColorFrom = 0
	private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val xRefPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var crossProgress = 0f
	private var drawCross = false
	private var crossOffset = 0f
	private var rippleDrawable: Drawable? = null
	private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var checkableForAccessibility = false
	private var checkable = false
	private var checked = false
	private var checkedProgress = 0f
	private var backgroundCheck1 = 0
	private var backgroundCheck2 = 0
	private var checkAnimator: ValueAnimator? = null

	init {
		setWillNotDraw(false)

		addView(textLayoutContainer)

		textView = (0..1).map {
			val textView = TextView(context)
			textView.setGravity(Gravity.CENTER_HORIZONTAL)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
			textView.setTextColor(Color.WHITE)
			textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)

			textLayoutContainer.addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 0f, radius + 4, 0f, 0f))

			textView
		}.toTypedArray()

		textView[1].visibility = GONE

		xRefPaint.setColor(-0x1000000)
		xRefPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
		xRefPaint.strokeWidth = AndroidUtilities.dp(3f).toFloat()

		crossPaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
		crossPaint.strokeCap = Paint.Cap.ROUND

		bitmapPaint.isFilterBitmap = true
	}

	fun setTextSize(size: Int) {
		textView.forEach {
			it.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
		}
	}

	fun setDrawBackground(value: Boolean) {
		drawBackground = value
	}

	@SuppressLint("DrawAllocation")
	override fun onDraw(canvas: Canvas) {
		if (animateBackground && replaceProgress != 0f) {
			circlePaint.setColor(ColorUtils.blendARGB(backgroundColor, animateToBackgroundColor, replaceProgress))
		}
		else {
			circlePaint.setColor(backgroundColor)
		}

		val cx = width / 2f
		val cy = AndroidUtilities.dp(radius) / 2f
		val radius = AndroidUtilities.dp(this.radius) / 2f

		if (drawBackground) {
			canvas.drawCircle(cx, cy, AndroidUtilities.dp(this.radius) / 2f, circlePaint)
		}

		if (rippleDrawable == null) {
			rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(this.radius), 0, Color.BLACK)
			rippleDrawable?.callback = this
		}

		rippleDrawable?.setBounds((cx - radius).toInt(), (cy - radius).toInt(), (cx + radius).toInt(), (cy + radius).toInt())
		rippleDrawable?.draw(canvas)

		if (currentIconRes != 0) {
			if (drawCross || crossProgress != 0f) {
				if (iconChangeColor) {
					val color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress)
					icon[0]?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
					crossPaint.setColor(color)
				}

				icon[0]?.alpha = 255

				if (replaceProgress != 0f && iconChangeColor) {
					val color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress)
					icon[0]?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
					crossPaint.setColor(color)
				}

				icon[0]?.alpha = 255

				if (drawCross && crossProgress < 1f) {
					crossProgress += 0.08f

					if (crossProgress > 1f) {
						crossProgress = 1f
					}
					else {
						invalidate()
					}
				}
				else if (!drawCross) {
					crossProgress -= 0.08f

					if (crossProgress < 0) {
						crossProgress = 0f
					}
					else {
						invalidate()
					}
				}

				if (crossProgress > 0) {
					val left = (cx - icon[0]!!.intrinsicWidth / 2f).toInt()
					val top = (cy - icon[0]!!.intrinsicHeight / 2).toInt()

					val startX = left + AndroidUtilities.dpf2(8f) + crossOffset
					val startY = top + AndroidUtilities.dpf2(8f) + crossOffset

					val endX = startX - AndroidUtilities.dp(1f) + AndroidUtilities.dp(23f) * CubicBezierInterpolator.DEFAULT.getInterpolation(crossProgress)
					val endY = startY + AndroidUtilities.dp(23f) * CubicBezierInterpolator.DEFAULT.getInterpolation(crossProgress)

					canvas.saveLayerAlpha(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), 255)

					icon[0]?.setBounds((cx - icon[0]!!.intrinsicWidth / 2f).toInt(), (cy - icon[0]!!.intrinsicHeight / 2).toInt(), (cx + icon[0]!!.intrinsicWidth / 2).toInt(), (cy + icon[0]!!.intrinsicHeight / 2).toInt())
					icon[0]?.draw(canvas)

					canvas.drawLine(startX, startY - AndroidUtilities.dp(2f), endX, endY - AndroidUtilities.dp(2f), xRefPaint)
					canvas.drawLine(startX, startY, endX, endY, crossPaint)
					canvas.restore()
				}
				else {
					icon[0]?.setBounds((cx - icon[0]!!.intrinsicWidth / 2f).toInt(), (cy - icon[0]!!.intrinsicHeight / 2).toInt(), (cx + icon[0]!!.intrinsicWidth / 2).toInt(), (cy + icon[0]!!.intrinsicHeight / 2).toInt())
					icon[0]?.draw(canvas)
				}
			}
			else {
				for (i in 0 until (if ((replaceProgress == 0f || iconChangeColor)) 1 else 2)) {
					if (icon[i] != null) {
						canvas.save()

						if (replaceProgress != 0f && !iconChangeColor && icon[0] != null && icon[1] != null) {
							val p = if (i == 0) 1f - replaceProgress else replaceProgress
							canvas.scale(p, p, cx, cy)
							icon[i]?.alpha = (255 * p).toInt()
						}
						else {
							if (iconChangeColor) {
								val color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress)
								icon[i]?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
								crossPaint.setColor(color)
							}

							icon[i]?.alpha = 255
						}

						icon[i]?.setBounds((cx - icon[i]!!.intrinsicWidth / 2f).toInt(), (cy - icon[i]!!.intrinsicHeight / 2).toInt(), (cx + icon[i]!!.intrinsicWidth / 2).toInt(), (cy + icon[i]!!.intrinsicHeight / 2).toInt())
						icon[i]?.draw(canvas)

						canvas.restore()
					}
				}
			}
		}
	}

	fun setBackgroundColor(backgroundColor: Int, backgroundColorChecked: Int) {
		backgroundCheck1 = backgroundColor
		backgroundCheck2 = backgroundColorChecked

		this.backgroundColor = ColorUtils.blendARGB(backgroundColor, backgroundColorChecked, checkedProgress)

		invalidate()
	}

	fun setData(iconRes: Int, iconColor: Int, backgroundColor: Int, text: String?, cross: Boolean, animated: Boolean) {
		setData(iconRes, iconColor, backgroundColor, 1.0f, true, text, cross, animated)
	}

	fun setEnabled(enabled: Boolean, animated: Boolean) {
		super.setEnabled(enabled)

		if (animated) {
			animate().alpha(if (enabled) 1.0f else 0.5f).setDuration(180).start()
		}
		else {
			clearAnimation()
			setAlpha(if (enabled) 1.0f else 0.5f)
		}
	}

	fun setData(iconRes: Int, iconColor: Int, backgroundColor: Int, selectorAlpha: Float, recreateRipple: Boolean, text: String?, cross: Boolean, animated: Boolean) {
		var animated = animated

		if (visibility != VISIBLE) {
			animated = false
			visibility = VISIBLE
		}

		if (currentIconRes == iconRes && currentIconColor == iconColor && (checkable || currentBackgroundColor == backgroundColor) && (currentText != null && currentText == text) && cross == this.drawCross) {
			return
		}

		if (rippleDrawable == null || recreateRipple) {
			if (Color.alpha(backgroundColor) == 255 && AndroidUtilities.computePerceivedBrightness(backgroundColor) > 0.5) {
				rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(radius), 0, ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.1f * selectorAlpha).toInt()))
				rippleDrawable?.callback = this
			}
			else {
				rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(radius), 0, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.3f * selectorAlpha).toInt()))
				rippleDrawable?.callback = this
			}
		}

		replaceAnimator?.cancel()

		animateBackground = currentBackgroundColor != backgroundColor

		iconChangeColor = currentIconRes == iconRes

		if (iconChangeColor) {
			replaceColorFrom = currentIconColor
		}

		currentIconRes = iconRes
		currentIconColor = iconColor
		currentBackgroundColor = backgroundColor
		currentText = text
		drawCross = cross

		if (!animated) {
			if (iconRes != 0) {
				icon[0] = ContextCompat.getDrawable(context, iconRes)?.mutate()
				icon[0]?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY)
			}

			crossPaint.setColor(iconColor)

			if (!checkable) {
				this.backgroundColor = backgroundColor
			}

			textView[0].text = text
			crossProgress = if (drawCross) 1f else 0f
			iconChangeColor = false
			replaceProgress = 0f

			invalidate()
		}
		else {
			if (!iconChangeColor && iconRes != 0) {
				icon[1] = ContextCompat.getDrawable(context, iconRes)?.mutate()
				icon[1]?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY)
			}

			if (!checkable) {
				this.animateToBackgroundColor = backgroundColor
			}

			val animateText = textView[0].getText().toString() != text

			if (!animateText) {
				textView[0].text = text
			}
			else {
				textView[1].text = text
				textView[1].visibility = VISIBLE
				textView[1].setAlpha(0f)
				textView[1].scaleX = 0f
				textView[1].scaleY = 0f
			}

			replaceAnimator = ValueAnimator.ofFloat(0f, 1f)

			replaceAnimator?.addUpdateListener {
				replaceProgress = it.getAnimatedValue() as Float

				invalidate()

				if (animateText) {
					textView[0].setAlpha(1f - replaceProgress)
					textView[0].scaleX = 1f - replaceProgress
					textView[0].scaleY = 1f - replaceProgress

					textView[1].setAlpha(replaceProgress)
					textView[1].scaleX = replaceProgress
					textView[1].scaleY = replaceProgress
				}
			}

			replaceAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					replaceAnimator = null

					if (animateText) {
						val tv = textView[0]

						textView[0] = textView[1]
						textView[1] = tv
						textView[1].visibility = GONE
					}

					if (!iconChangeColor && icon[1] != null) {
						icon[0] = icon[1]
						icon[1] = null
					}

					iconChangeColor = false

					if (!checkable) {
						this@VoIPToggleButton.backgroundColor = animateToBackgroundColor
					}

					replaceProgress = 0f

					invalidate()
				}
			})

			replaceAnimator?.setDuration(150)?.start()

			invalidate()
		}
	}

	fun setCrossOffset(crossOffset: Float) {
		this.crossOffset = crossOffset
	}

	override fun drawableStateChanged() {
		super.drawableStateChanged()
		rippleDrawable?.setState(drawableState)
	}

	public override fun verifyDrawable(drawable: Drawable): Boolean {
		return rippleDrawable === drawable || super.verifyDrawable(drawable)
	}

	override fun jumpDrawablesToCurrentState() {
		super.jumpDrawablesToCurrentState()
		rippleDrawable?.jumpToCurrentState()
	}

	fun setCheckableForAccessibility(checkableForAccessibility: Boolean) {
		this.checkableForAccessibility = checkableForAccessibility
	}

	// animate background if true
	fun setCheckable(checkable: Boolean) {
		this.checkable = checkable
	}

	fun setChecked(value: Boolean, animated: Boolean) {
		if (checked == value) {
			return
		}

		checked = value

		if (checkable) {
			if (animated) {
				checkAnimator?.removeAllListeners()
				checkAnimator?.cancel()

				checkAnimator = ValueAnimator.ofFloat(checkedProgress, if (checked) 1f else 0f)

				checkAnimator?.addUpdateListener {
					checkedProgress = it.getAnimatedValue() as Float
					setBackgroundColor(backgroundCheck1, backgroundCheck2)
				}

				checkAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						checkedProgress = if (checked) 1f else 0f
						setBackgroundColor(backgroundCheck1, backgroundCheck2)
					}
				})

				checkAnimator?.setDuration(150)
				checkAnimator?.start()
			}
			else {
				checkedProgress = if (checked) 1f else 0f

				setBackgroundColor(backgroundCheck1, backgroundCheck2)
			}
		}
	}

	fun isChecked(): Boolean {
		return checked
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.setText(currentText)

		if (checkable || checkableForAccessibility) {
			info.setClassName(ToggleButton::class.java.getName())
			info.isCheckable = true
			info.isChecked = checked
		}
		else {
			info.setClassName(Button::class.java.getName())
		}
	}

	fun shakeView() {
		AndroidUtilities.shakeView(textView[0], 2f, 0)
		AndroidUtilities.shakeView(textView[1], 2f, 0)
	}

	fun showText(show: Boolean, animated: Boolean) {
		if (animated) {
			val a = if (show) 1f else 0f

			if (textLayoutContainer.alpha != a) {
				textLayoutContainer.animate().alpha(a).start()
			}
		}
		else {
			textLayoutContainer.animate().cancel()
			textLayoutContainer.setAlpha(if (show) 1f else 0f)
		}
	}
}
