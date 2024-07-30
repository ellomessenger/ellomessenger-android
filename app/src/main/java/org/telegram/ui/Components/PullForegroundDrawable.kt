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
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import kotlin.math.abs

open class PullForegroundDrawable(pullText: String, releaseText: String) {
	var scrollDy = 0
	private var backgroundColor = ApplicationLoader.applicationContext.getColor(R.color.medium_gray)
	private var backgroundActiveColor = ApplicationLoader.applicationContext.getColor(R.color.brand)
	private val avatarBackgroundColor = ApplicationLoader.applicationContext.getColor(R.color.totals_blue)
	private var changeAvatarColor = true
	private val paintSecondary = Paint(Paint.ANTI_ALIAS_FLAG)
	private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG)
	private val paintBackgroundAccent = Paint(Paint.ANTI_ALIAS_FLAG)
	val backgroundPaint = Paint()
	private val rectF = RectF()
	private val tooltipTextPaint: Paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val arrowDrawable = ArrowDrawable()
	private val circleClipPath = Path()
	private var textSwappingProgress = 1f
	private var arrowRotateProgress = 1f
	private var animateToEndText = false
	private var arrowAnimateTo = false
	private var textSwipingAnimator: ValueAnimator? = null
	private var accentRevealAnimatorIn: ValueAnimator? = null
	private var accentRevealAnimatorOut: ValueAnimator? = null
	private var accentRevealProgress = 1f
	private var accentRevealProgressOut = 1f
	private var textInProgress = 0f
	private var animateToTextIn = false
	private var textIntAnimator: ValueAnimator? = null
	private var arrowRotateAnimator: ValueAnimator? = null
	private var outAnimator: AnimatorSet? = null
	private var outProgress = 0f
	private var bounceProgress = 0f
	private var animateOut = false
	private var bounceIn = false
	private var animateToColorize = false
	private var cell: View? = null
	private var listView: RecyclerListView? = null
	var pullProgress = 0f
	var outCy = 0f
	var outCx = 0f
	var outRadius = 0f
	var outImageSize = 0f
	private var outOverScroll = 0f
	private val pullTooltip: String
	private val releaseTooltip: String
	private var willDraw = false
	private var isOut = false
	private val touchSlop: Float

	private val textSwappingUpdateListener = AnimatorUpdateListener {
		textSwappingProgress = it.animatedValue as Float
		cell?.invalidate()
	}

	private val textInUpdateListener = AnimatorUpdateListener {
		textInProgress = it.animatedValue as Float
		cell?.invalidate()
	}

	fun setColors(background: Int, active: Int) {
		backgroundColor = background
		backgroundActiveColor = active
		changeAvatarColor = false
		updateColors()
	}

	fun setCell(view: View?) {
		cell = view
		updateColors()
	}

	fun updateColors() {
		val primaryColor = Color.WHITE
		tooltipTextPaint.color = primaryColor
		paintWhite.color = primaryColor
		paintSecondary.color = ColorUtils.setAlphaComponent(primaryColor, 100)
		backgroundPaint.color = backgroundColor
		arrowDrawable.setColor(backgroundColor)
		paintBackgroundAccent.color = avatarBackgroundColor
	}

	fun setListView(listView: RecyclerListView?) {
		this.listView = listView
	}

	fun drawOverScroll(canvas: Canvas) {
		draw(canvas, true)
	}

	protected open val viewOffset: Float
		get() = 0f

	@JvmOverloads
	fun draw(canvas: Canvas, header: Boolean = false) {
		val cell = cell
		val listView = listView

		if (!willDraw || isOut || cell == null || listView == null) {
			return
		}

		val startPadding = AndroidUtilities.dp(28f)
		val smallMargin = AndroidUtilities.dp(8f)
		val radius = AndroidUtilities.dp(9f)
		val diameter = AndroidUtilities.dp(18f)
		val overscroll = viewOffset.toInt()
		val visibleHeight = (cell.height * pullProgress).toInt()
		val bounceP = if (bounceIn) 0.07f * bounceProgress - 0.05f else 0.02f * bounceProgress

		updateTextProgress(pullProgress)

		var outProgressHalf = outProgress * 2f

		if (outProgressHalf > 1f) {
			outProgressHalf = 1f
		}

		val cX = outCx
		var cY = outCy

		if (header) {
			cY += overscroll.toFloat()
		}

		val smallCircleX = startPadding + radius
		var smallCircleY = cell.measuredHeight - smallMargin - radius

		if (header) {
			smallCircleY += overscroll
		}

		val startPullProgress = if (visibleHeight > diameter + smallMargin * 2) 1f else visibleHeight.toFloat() / (diameter + smallMargin * 2)

		canvas.save()

		if (header) {
			canvas.clipRect(0, 0, listView.measuredWidth, overscroll + 1)
		}

		if (outProgress == 0f) {
			if (!(accentRevealProgress == 1f || accentRevealProgressOut == 1f)) {
				canvas.drawPaint(backgroundPaint)
			}
		}
		else {
			val outBackgroundRadius = outRadius + (cell.width - outRadius) * (1f - outProgress) + outRadius * bounceP

			if (!(accentRevealProgress == 1f || accentRevealProgressOut == 1f)) {
				canvas.drawCircle(cX, cY, outBackgroundRadius, backgroundPaint)
			}

			circleClipPath.reset()
			rectF.set(cX - outBackgroundRadius, cY - outBackgroundRadius, cX + outBackgroundRadius, cY + outBackgroundRadius)

			circleClipPath.addOval(rectF, Path.Direction.CW)

			canvas.clipPath(circleClipPath)
		}

		if (animateToColorize) {
			if (accentRevealProgressOut > accentRevealProgress) {
				canvas.save()
				canvas.translate((cX - smallCircleX) * outProgress, (cY - smallCircleY) * outProgress)
				canvas.drawCircle(smallCircleX.toFloat(), smallCircleY.toFloat(), cell.width * accentRevealProgressOut, backgroundPaint)
				canvas.restore()
			}

			if (accentRevealProgress > 0f) {
				canvas.save()
				canvas.translate((cX - smallCircleX) * outProgress, (cY - smallCircleY) * outProgress)
				canvas.drawCircle(smallCircleX.toFloat(), smallCircleY.toFloat(), cell.width * accentRevealProgress, paintBackgroundAccent)
				canvas.restore()
			}
		}
		else {
			if (accentRevealProgress > accentRevealProgressOut) {
				canvas.save()
				canvas.translate((cX - smallCircleX) * outProgress, (cY - smallCircleY) * outProgress)
				canvas.drawCircle(smallCircleX.toFloat(), smallCircleY.toFloat(), cell.width * accentRevealProgress, paintBackgroundAccent)
				canvas.restore()
			}

			if (accentRevealProgressOut > 0f) {
				canvas.save()
				canvas.translate((cX - smallCircleX) * outProgress, (cY - smallCircleY) * outProgress)
				canvas.drawCircle(smallCircleX.toFloat(), smallCircleY.toFloat(), cell.width * accentRevealProgressOut, backgroundPaint)
				canvas.restore()
			}
		}

		if (visibleHeight > diameter + smallMargin * 2) {
			paintSecondary.alpha = ((1f - outProgressHalf) * 0.4f * startPullProgress * 255).toInt()

			if (header) {
				rectF.set(startPadding.toFloat(), smallMargin.toFloat(), (startPadding + diameter).toFloat(), (smallMargin + overscroll + radius).toFloat())
			}
			else {
				rectF.set(startPadding.toFloat(), (cell.height - visibleHeight + smallMargin - overscroll).toFloat(), (startPadding + diameter).toFloat(), (cell.height - smallMargin).toFloat())
			}

			canvas.drawRoundRect(rectF, radius.toFloat(), radius.toFloat(), paintSecondary)
		}

		if (header) {
			canvas.restore()
			return
		}

		if (outProgress == 0f) {
			paintWhite.alpha = (startPullProgress * 255).toInt()

			canvas.drawCircle(smallCircleX.toFloat(), smallCircleY.toFloat(), radius.toFloat(), paintWhite)

			val ih = arrowDrawable.intrinsicHeight
			val iw = arrowDrawable.intrinsicWidth

			arrowDrawable.setBounds(smallCircleX - (iw shr 1), smallCircleY - (ih shr 1), smallCircleX + (iw shr 1), smallCircleY + (ih shr 1))

			var rotateProgress = 1f - arrowRotateProgress

			if (rotateProgress < 0) {
				rotateProgress = 0f
			}

			rotateProgress = 1f - rotateProgress

			canvas.save()
			canvas.rotate(180 * rotateProgress, smallCircleX.toFloat(), smallCircleY.toFloat())
			canvas.translate(0f, AndroidUtilities.dpf2(1f) * 1f - rotateProgress)

			arrowDrawable.setColor(if (animateToColorize) paintBackgroundAccent.color else backgroundColor)
			arrowDrawable.draw(canvas)

			canvas.restore()
		}

		if (pullProgress > 0f) {
			textIn()
		}

		val textY = cell.height - (diameter + smallMargin * 2) / 2f + AndroidUtilities.dp(6f)

		tooltipTextPaint.alpha = (255 * textSwappingProgress * startPullProgress * textInProgress).toInt()

		val textCx = cell.width / 2f - AndroidUtilities.dp(2f)

		if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
			canvas.save()
			val scale = 0.8f + 0.2f * textSwappingProgress
			canvas.scale(scale, scale, textCx, textY + AndroidUtilities.dp(16f) * (1f - textSwappingProgress))
		}

		canvas.drawText(pullTooltip, textCx, textY + AndroidUtilities.dp(8f) * (1f - textSwappingProgress), tooltipTextPaint)

		if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
			canvas.restore()
		}

		if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
			canvas.save()
			val scale = 0.9f + 0.1f * (1f - textSwappingProgress)
			canvas.scale(scale, scale, textCx, textY - AndroidUtilities.dp(8f) * textSwappingProgress)
		}

		tooltipTextPaint.alpha = (255 * (1f - textSwappingProgress) * startPullProgress * textInProgress).toInt()

		canvas.drawText(releaseTooltip, textCx, textY - AndroidUtilities.dp(8f) * textSwappingProgress, tooltipTextPaint)

		if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
			canvas.restore()
		}

		canvas.restore()

		if (changeAvatarColor && outProgress > 0) {
			canvas.save()
			val iw = Theme.dialogs_archiveAvatarDrawable.intrinsicWidth
			val startCx = startPadding + radius
			val startCy = cell.height - smallMargin - radius
			val scaleStart = AndroidUtilities.dp(24f).toFloat() / iw
			val scale = scaleStart + (1f - scaleStart) * outProgress + bounceP

			canvas.translate((startCx - cX) * (1f - outProgress), (startCy - cY) * (1f - outProgress))
			canvas.scale(scale, scale, cX, cY)

			Theme.dialogs_archiveAvatarDrawable.setProgress(0f)

			if (!Theme.dialogs_archiveAvatarDrawableRecolored) {
				Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors()
				Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", avatarBackgroundColor)
				Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", avatarBackgroundColor)
				Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors()
				Theme.dialogs_archiveAvatarDrawableRecolored = true
			}

			Theme.dialogs_archiveAvatarDrawable.setBounds((cX - iw / 2f).toInt(), (cY - iw / 2f).toInt(), (cX + iw / 2f).toInt(), (cY + iw / 2f).toInt())
			Theme.dialogs_archiveAvatarDrawable.draw(canvas)

			canvas.restore()
		}
	}

	private fun updateTextProgress(pullProgress: Float) {
		val endText = pullProgress > SNAP_HEIGHT

		if (animateToEndText != endText) {
			animateToEndText = endText

			if (textInProgress == 0f) {
				textSwipingAnimator?.cancel()
				textSwappingProgress = if (endText) 0f else 1f
			}
			else {
				textSwipingAnimator?.cancel()

				textSwipingAnimator = ValueAnimator.ofFloat(textSwappingProgress, if (endText) 0f else 1f)
				textSwipingAnimator?.addUpdateListener(textSwappingUpdateListener)
				textSwipingAnimator?.interpolator = LinearInterpolator()
				textSwipingAnimator?.duration = 170
				textSwipingAnimator?.start()
			}
		}

		if (endText != arrowAnimateTo) {
			arrowAnimateTo = endText

			arrowRotateAnimator?.cancel()

			arrowRotateAnimator = ValueAnimator.ofFloat(arrowRotateProgress, if (arrowAnimateTo) 0f else 1f)

			arrowRotateAnimator?.addUpdateListener {
				arrowRotateProgress = it.animatedValue as Float
				cell?.invalidate()
			}

			arrowRotateAnimator?.interpolator = CubicBezierInterpolator.EASE_BOTH
			arrowRotateAnimator?.duration = 250
			arrowRotateAnimator?.start()
		}
	}

	fun colorize(colorize: Boolean) {
		if (animateToColorize != colorize) {
			animateToColorize = colorize

			if (colorize) {
				accentRevealAnimatorIn?.cancel()
				accentRevealAnimatorIn = null

				accentRevealProgress = 0f

				accentRevealAnimatorIn = ValueAnimator.ofFloat(accentRevealProgress, 1f)

				accentRevealAnimatorIn?.addUpdateListener {
					accentRevealProgress = it.animatedValue as Float
					cell?.invalidate()
					listView?.invalidate()
				}

				accentRevealAnimatorIn?.interpolator = AndroidUtilities.accelerateInterpolator
				accentRevealAnimatorIn?.duration = 230
				accentRevealAnimatorIn?.start()
			}
			else {
				accentRevealAnimatorOut?.cancel()
				accentRevealAnimatorOut = null

				accentRevealProgressOut = 0f
				accentRevealAnimatorOut = ValueAnimator.ofFloat(accentRevealProgressOut, 1f)

				accentRevealAnimatorOut?.addUpdateListener {
					accentRevealProgressOut = it.animatedValue as Float
					cell?.invalidate()
					listView?.invalidate()
				}

				accentRevealAnimatorOut?.interpolator = AndroidUtilities.accelerateInterpolator
				accentRevealAnimatorOut?.duration = 230
				accentRevealAnimatorOut?.start()
			}
		}
	}

	private var textInRunnable = Runnable {
		animateToTextIn = true
		textIntAnimator?.cancel()

		textInProgress = 0f

		textIntAnimator = ValueAnimator.ofFloat(0f, 1f)
		textIntAnimator?.addUpdateListener(textInUpdateListener)
		textIntAnimator?.interpolator = LinearInterpolator()
		textIntAnimator?.duration = 150
		textIntAnimator?.start()
	}

	private var wasSendCallback = false

	init {
		tooltipTextPaint.typeface = Theme.TYPEFACE_BOLD
		tooltipTextPaint.textAlign = Paint.Align.CENTER
		tooltipTextPaint.textSize = AndroidUtilities.dp(16f).toFloat()
		val vc = ViewConfiguration.get(ApplicationLoader.applicationContext)
		touchSlop = vc.scaledTouchSlop.toFloat()
		pullTooltip = pullText
		releaseTooltip = releaseText
	}

	private fun textIn() {
		if (!animateToTextIn) {
			if (abs(scrollDy) < touchSlop * 0.5f) {
				if (!wasSendCallback) {
					textInProgress = 1f
					animateToTextIn = true
				}
			}
			else {
				wasSendCallback = true
				cell?.removeCallbacks(textInRunnable)
				cell?.postDelayed(textInRunnable, 200)
			}
		}
	}

	fun startOutAnimation() {
		if (animateOut) {
			return
		}

		val listView = listView ?: return

		outAnimator?.removeAllListeners()
		outAnimator?.cancel()

		animateOut = true
		bounceIn = true
		bounceProgress = 0f
		outOverScroll = listView.translationY / AndroidUtilities.dp(100f)

		val out = ValueAnimator.ofFloat(0f, 1f)

		out.addUpdateListener {
			setOutProgress(it.animatedValue as Float)
			cell?.invalidate()
		}

		out.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
		out.duration = 250

		val bounceIn = ValueAnimator.ofFloat(0f, 1f)

		bounceIn.addUpdateListener {
			bounceProgress = it.animatedValue as Float
			this.bounceIn = true
			cell?.invalidate()
		}

		bounceIn.interpolator = CubicBezierInterpolator.EASE_BOTH
		bounceIn.duration = 150

		val bounceOut = ValueAnimator.ofFloat(1f, 0f)

		bounceOut.addUpdateListener {
			bounceProgress = it.animatedValue as Float
			this.bounceIn = false
			cell?.invalidate()
		}

		bounceOut.interpolator = CubicBezierInterpolator.EASE_BOTH
		bounceOut.duration = 135

		outAnimator = AnimatorSet()

		outAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				doNotShow()
			}
		})

		val bounce = AnimatorSet()
		bounce.playSequentially(bounceIn, bounceOut)
		bounce.startDelay = 180

		outAnimator?.playTogether(out, bounce)
		outAnimator?.start()
	}

	fun getOutProgress(): Float = outProgress

	private fun setOutProgress(value: Float) {
		outProgress = value

		val color = ColorUtils.blendARGB(avatarBackgroundColor, backgroundActiveColor, 1f - outProgress)

		paintBackgroundAccent.color = color

		if (changeAvatarColor && isDraw) {
			Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors()
			Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", color)
			Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", color)
			Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors()
		}
	}

	fun doNotShow() {
		textSwipingAnimator?.cancel()
		textIntAnimator?.cancel()
		cell?.removeCallbacks(textInRunnable)
		accentRevealAnimatorIn?.cancel()
		textSwappingProgress = 1f
		arrowRotateProgress = 1f
		animateToEndText = false
		arrowAnimateTo = false
		animateToTextIn = false
		wasSendCallback = false
		textInProgress = 0f
		isOut = true
		setOutProgress(1f)
		animateToColorize = false
		accentRevealProgress = 0f
	}

	fun showHidden() {
		outAnimator?.removeAllListeners()
		outAnimator?.cancel()

		setOutProgress(0f)
		isOut = false
		animateOut = false
	}

	fun destroyView() {
		cell = null
		textSwipingAnimator?.cancel()
		outAnimator?.removeAllListeners()
		outAnimator?.cancel()
	}

	val isDraw: Boolean
		get() = willDraw && !isOut

	fun setWillDraw(b: Boolean) {
		willDraw = b
	}

	fun resetText() {
		textIntAnimator?.cancel()
		cell?.removeCallbacks(textInRunnable)
		textInProgress = 0f
		animateToTextIn = false
		wasSendCallback = false
	}

	private inner class ArrowDrawable : Drawable() {
		private val path = Path()
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		private var lastDensity = 0f

		init {
			updatePath()
		}

		private fun updatePath() {
			val h = AndroidUtilities.dp(18f)
			path.reset()
			path.moveTo((h shr 1).toFloat(), AndroidUtilities.dpf2(4.98f))
			path.lineTo(AndroidUtilities.dpf2(4.95f), AndroidUtilities.dpf2(9f))
			path.lineTo(h - AndroidUtilities.dpf2(4.95f), AndroidUtilities.dpf2(9f))
			path.lineTo((h shr 1).toFloat(), AndroidUtilities.dpf2(4.98f))
			paint.style = Paint.Style.FILL_AND_STROKE
			paint.strokeJoin = Paint.Join.ROUND
			paint.strokeWidth = AndroidUtilities.dpf2(1f)
			lastDensity = AndroidUtilities.density
		}

		fun setColor(color: Int) {
			paint.color = color
		}

		override fun getIntrinsicHeight(): Int {
			return AndroidUtilities.dp(18f)
		}

		override fun getIntrinsicWidth(): Int {
			return intrinsicHeight
		}

		override fun draw(canvas: Canvas) {
			if (lastDensity != AndroidUtilities.density) {
				updatePath()
			}

			canvas.save()
			canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
			canvas.drawPath(path, paint)

			val h = AndroidUtilities.dp(18f)

			canvas.drawRect(AndroidUtilities.dpf2(7.56f), AndroidUtilities.dpf2(8f), h - AndroidUtilities.dpf2(7.56f), AndroidUtilities.dpf2(11.1f), paint)
			canvas.restore()
		}

		override fun setAlpha(alpha: Int) {
			// unused
		}

		override fun setColorFilter(colorFilter: ColorFilter?) {
			// unused
		}

		@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.UNKNOWN", "android.graphics.PixelFormat"))
		override fun getOpacity(): Int {
			return PixelFormat.UNKNOWN
		}
	}

	companion object {
		const val SNAP_HEIGHT = 0.85f
		const val startPullParallax = 0.45f
		const val endPullParallax = 0.25f
		const val startPullOverScroll = 0.2f
		const val minPullingTime = 200L

		val maxOverscroll: Int
			get() = AndroidUtilities.dp(72f)
	}
}
