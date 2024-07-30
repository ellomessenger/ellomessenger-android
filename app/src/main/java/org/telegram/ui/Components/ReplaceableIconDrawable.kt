package org.telegram.ui.Components

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat

class ReplaceableIconDrawable(private val context: Context) : Drawable(), Animator.AnimatorListener {
	private var colorFilter: ColorFilter? = null
	private var currentResId = 0
	private var currentDrawable: Drawable? = null
	private var outDrawable: Drawable? = null
	private var animation: ValueAnimator? = null
	private var progress = 1f

	fun setIcon(@DrawableRes resId: Int, animated: Boolean) {
		if (currentResId == resId) {
			return
		}

		setIcon(ResourcesCompat.getDrawable(context.resources, resId, null)?.mutate(), animated)

		currentResId = resId
	}

	fun setIcon(drawable: Drawable?, animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (drawable == null) {
			currentDrawable = null
			outDrawable = null

			invalidateSelf()

			return
		}

		if (bounds.isEmpty) {
			animated = false
		}

		if (drawable === currentDrawable) {
			currentDrawable?.colorFilter = colorFilter
			return
		}

		currentResId = 0

		outDrawable = currentDrawable

		currentDrawable = drawable
		currentDrawable?.colorFilter = colorFilter

		updateBounds(currentDrawable, bounds)
		updateBounds(outDrawable, bounds)

		animation?.removeAllListeners()
		animation?.cancel()

		if (!animated) {
			progress = 1f
			outDrawable = null
			return
		}

		animation = ValueAnimator.ofFloat(0f, 1f)

		animation?.addUpdateListener {
			progress = it.animatedValue as Float
			invalidateSelf()
		}

		animation?.addListener(this)
		animation?.duration = 150
		animation?.start()
	}

	override fun onBoundsChange(bounds: Rect) {
		super.onBoundsChange(bounds)
		updateBounds(currentDrawable, bounds)
		updateBounds(outDrawable, bounds)
	}

	private fun updateBounds(d: Drawable?, bounds: Rect) {
		if (d == null) {
			return
		}

		val left: Int
		val right: Int
		val bottom: Int
		val top: Int

		if (d.intrinsicHeight < 0) {
			top = bounds.top
			bottom = bounds.bottom
		}
		else {
			val offset = (bounds.height() - d.intrinsicHeight) / 2
			top = bounds.top + offset
			bottom = bounds.top + offset + d.intrinsicHeight
		}

		if (d.intrinsicWidth < 0) {
			left = bounds.left
			right = bounds.right
		}
		else {
			val offset = (bounds.width() - d.intrinsicWidth) / 2
			left = bounds.left + offset
			right = bounds.left + offset + d.intrinsicWidth
		}

		d.setBounds(left, top, right, bottom)
	}

	override fun draw(canvas: Canvas) {
		val cX = bounds.centerX()
		val cY = bounds.centerY()

		currentDrawable?.let {
			if (progress != 1f) {
				canvas.save()
				canvas.scale(progress, progress, cX.toFloat(), cY.toFloat())

				it.alpha = (255 * progress).toInt()
				it.draw(canvas)

				canvas.restore()
			}
			else {
				it.alpha = 255
				it.draw(canvas)
			}
		}

		outDrawable?.let {
			if (progress != 1f) {
				val progressRev = 1f - progress

				canvas.save()
				canvas.scale(progressRev, progressRev, cX.toFloat(), cY.toFloat())

				it.alpha = (255 * progressRev).toInt()
				it.draw(canvas)

				canvas.restore()
			}
			else {
				it.alpha = 255
				it.draw(canvas)
			}
		}
	}

	override fun setAlpha(alpha: Int) {
		// unused
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		this.colorFilter = colorFilter
		currentDrawable?.colorFilter = colorFilter
		outDrawable?.colorFilter = colorFilter
		invalidateSelf()
	}

	@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
	override fun getOpacity(): Int {
		return PixelFormat.TRANSPARENT
	}

	override fun onAnimationEnd(animation: Animator) {
		outDrawable = null
		invalidateSelf()
	}

	override fun onAnimationStart(animation: Animator) {
		// unused
	}

	override fun onAnimationCancel(animation: Animator) {
		// unused
	}

	override fun onAnimationRepeat(animation: Animator) {
		// unused
	}
}
