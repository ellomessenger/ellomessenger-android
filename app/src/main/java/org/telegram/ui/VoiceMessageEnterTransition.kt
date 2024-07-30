package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.ChatActivityEnterView.RecordCircle
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.RecyclerListView

class VoiceMessageEnterTransition(private val messageView: ChatMessageCell, chatActivityEnterView: ChatActivityEnterView, private val listView: RecyclerListView, var container: MessageEnterTransitionContainer) : MessageEnterTransitionContainer.Transition {
	private val animator: ValueAnimator
	private val recordCircle: RecordCircle
	private val gradientMatrix: Matrix
	private val gradientShader: LinearGradient
	private val messageId: Int
	private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var fromRadius: Float
	private var progress = 0f
	private var lastToCx = 0f
	private var lastToCy = 0f

	init {
		fromRadius = chatActivityEnterView.recordCircle.drawingCircleRadius

		messageView.enterTransitionInProgress = true

		recordCircle = chatActivityEnterView.recordCircle
		recordCircle.voiceEnterTransitionInProgress = true
		recordCircle.skipDraw = true

		gradientMatrix = Matrix()

		val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		gradientPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

		gradientShader = LinearGradient(0f, AndroidUtilities.dp(12f).toFloat(), 0f, 0f, 0, -0x1000000, Shader.TileMode.CLAMP)

		gradientPaint.shader = gradientShader

		messageId = messageView.getMessageObject()?.stableId ?: 0

		container.addTransition(this)

		animator = ValueAnimator.ofFloat(0f, 1f)

		animator.addUpdateListener {
			progress = it.animatedValue as Float
			container.invalidate()
		}

		animator.interpolator = LinearInterpolator()
		animator.duration = 220

		animator.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				messageView.enterTransitionInProgress = false
				container.removeTransition(this@VoiceMessageEnterTransition)
				recordCircle.skipDraw = false
			}
		})

		messageView.seekBarWaveform.setSent()
	}

	fun start() {
		animator.start()
	}

	override fun onDraw(canvas: Canvas) {
		val step1Time = 0.6f
		val moveProgress = progress
		val hideWavesProgress = if (progress > step1Time) 1f else progress / step1Time
		val fromCx = recordCircle.drawingCx + recordCircle.x - container.x
		val fromCy = recordCircle.drawingCy + recordCircle.y - container.y
		val toCy: Float
		val toCx: Float

		if (messageView.getMessageObject()?.stableId != messageId) {
			toCx = lastToCx
			toCy = lastToCy
		}
		else {
			toCy = messageView.radialProgress.progressRect.centerY() + messageView.y + listView.y - container.y
			toCx = messageView.radialProgress.progressRect.centerX() + messageView.x + listView.x - container.x
		}

		lastToCx = toCx
		lastToCy = toCy

		val progress = CubicBezierInterpolator.DEFAULT.getInterpolation(moveProgress)
		val xProgress = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(moveProgress)
		val cx = fromCx * (1f - xProgress) + toCx * xProgress
		val cy = fromCy * (1f - progress) + toCy * progress
		val toRadius = messageView.radialProgress.progressRect.height() / 2
		val radius = fromRadius * (1f - progress) + toRadius * progress
		val listViewBottom = listView.y - container.y + listView.measuredHeight
		var clipBottom = 0

		if (container.measuredHeight > 0) {
			clipBottom = (container.measuredHeight * (1f - progress) + listViewBottom * progress).toInt()
		}

		val baseColor = ResourcesCompat.getColor(container.resources, R.color.background, null)
		val secondaryColor = ResourcesCompat.getColor(container.resources, R.color.white, null) // getThemedColor(messageView.getRadialProgress().getCircleColorKey());

		circlePaint.color = ColorUtils.blendARGB(baseColor, secondaryColor, progress)

		recordCircle.drawWaves(canvas, cx, cy, 1f - hideWavesProgress)

		canvas.drawCircle(cx, cy, radius, circlePaint)
		canvas.save()

		val scale = radius / toRadius

		canvas.scale(scale, scale, cx, cy)
		canvas.translate(cx - messageView.radialProgress.progressRect.centerX(), cy - messageView.radialProgress.progressRect.centerY())

		messageView.radialProgress.apply {
			overrideAlpha = progress
			setDrawBackground(false)
			draw(canvas)
			setDrawBackground(true)
			overrideAlpha = 1f
		}

		canvas.restore()

		if (container.measuredHeight > 0) {
			gradientMatrix.setTranslate(0f, clipBottom.toFloat())
			gradientShader.setLocalMatrix(gradientMatrix)
		}

		recordCircle.drawIcon(canvas, fromCx.toInt(), fromCy.toInt(), 1f - moveProgress)
	}
}
