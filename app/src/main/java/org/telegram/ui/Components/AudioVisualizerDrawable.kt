/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import java.util.*

class AudioVisualizerDrawable {
	var parentView: View? = null
	var rotation = 0f
	private val animateTo = FloatArray(8)
	private val current = FloatArray(8)
	private val drawables: Array<CircleBezierDrawable> = arrayOf(CircleBezierDrawable(6), CircleBezierDrawable(6))
	private val dt = FloatArray(8)
	private val lastAmplitude = FloatArray(MAX_SAMPLE_SUM)
	private val p1 = Paint(Paint.ANTI_ALIAS_FLAG)
	private val random = Random()
	private val tmpWaveform = IntArray(3)
	private var idleScale = 0f
	private var idleScaleInc = false
	private var lastAmplitudeCount = 0
	private var lastAmplitudePointer = 0

	init {
		drawables.forEach {
			it.idleStateDiff = 0f
			it.radius = AndroidUtilities.dp(24f).toFloat()
			it.radiusDiff = 0f
			it.randomK = 1f
		}
	}

	fun setWaveform(playing: Boolean, animate: Boolean, waveform: FloatArray?) {
		if (!playing && !animate) {
			for (i in 0..7) {
				current[i] = 0f
				animateTo[i] = current[i]
			}

			return
		}

		val idleState = waveform != null && waveform[6] == 0f
		val amplitude: Float = waveform?.get(6) ?: 0f

		if (waveform != null && amplitude > 0.4) {
			lastAmplitude[lastAmplitudePointer] = amplitude
			lastAmplitudePointer++

			if (lastAmplitudePointer > MAX_SAMPLE_SUM - 1) {
				lastAmplitudePointer = 0
			}

			lastAmplitudeCount++
		}
		else {
			lastAmplitudeCount = 0
		}

		if (idleState) {
			for (i in 0..5) {
				waveform?.set(i, random.nextInt() % 500 / 1000f)
			}
		}

		var duration = if (idleState) ANIMATION_DURATION * 2 else ANIMATION_DURATION

		if (lastAmplitudeCount > MAX_SAMPLE_SUM) {
			var a = 0f

			for (i in 0 until MAX_SAMPLE_SUM) {
				a += lastAmplitude[i]
			}

			a /= MAX_SAMPLE_SUM.toFloat()

			if (a > 0.52f) {
				duration -= ANIMATION_DURATION * (a - 0.40f)
			}
		}
		for (i in 0..6) {
			if (waveform == null) {
				animateTo[i] = 0f
			}
			else {
				animateTo[i] = waveform[i]
			}

			if (parentView == null) {
				current[i] = animateTo[i]
			}
			else if (i == 6) {
				dt[i] = (animateTo[i] - current[i]) / (ANIMATION_DURATION + 80)
			}
			else {
				dt[i] = (animateTo[i] - current[i]) / duration
			}
		}

		animateTo[7] = if (playing) 1f else 0f

		dt[7] = (animateTo[7] - current[7]) / 120
	}

	fun draw(canvas: Canvas, cx: Float, cy: Float, outOwner: Boolean) {
		if (outOwner) {
			p1.color = ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.white, null) // Theme.getColor(Theme.key_chat_outLoader)
			p1.alpha = ALPHA
		}
		else {
			p1.color = ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.brand, null) // Theme.getColor(Theme.key_chat_inLoader)
			p1.alpha = ALPHA
		}

		this.draw(canvas, cx, cy)
	}

	fun draw(canvas: Canvas, cx: Float, cy: Float) {
		for (i in 0..7) {
			if (animateTo[i] != current[i]) {
				current[i] += dt[i] * 16

				if (dt[i] > 0 && current[i] > animateTo[i] || dt[i] < 0 && current[i] < animateTo[i]) {
					current[i] = animateTo[i]
				}

				parentView?.invalidate()
			}
		}

		if (idleScaleInc) {
			idleScale += 0.02f

			if (idleScale > 1f) {
				idleScaleInc = false
				idleScale = 1f
			}
		}
		else {
			idleScale -= 0.02f

			if (idleScale < 0) {
				idleScaleInc = true
				idleScale = 0f
			}
		}

		val enterProgress = current[7]
		val radiusProgress = current[6] * current[0]

		if (enterProgress == 0f && radiusProgress == 0f) {
			return
		}

		for (i in 0..2) {
			tmpWaveform[i] = (current[i] * WAVE_RADIUS).toInt()
		}

		drawables[0].setAdditionals(tmpWaveform)

		for (i in 0..2) {
			tmpWaveform[i] = (current[i + 3] * WAVE_RADIUS).toInt()
		}

		drawables[1].setAdditionals(tmpWaveform)

		var radius = AndroidUtilities.dp(22f) + AndroidUtilities.dp(4f) * radiusProgress + IDLE_RADIUS * enterProgress

		if (radius > AndroidUtilities.dp(26f)) {
			radius = AndroidUtilities.dp(26f).toFloat()
		}

		drawables[1].radius = radius
		drawables[0].radius = drawables[1].radius

		canvas.save()

		rotation += 0.6.toFloat()

		canvas.rotate(rotation, cx, cy)
		canvas.save()

		var s = 1f + 0.04f * idleScale

		canvas.scale(s, s, cx, cy)

		drawables[0].draw(cx, cy, canvas, p1)

		canvas.restore()
		canvas.rotate(60f, cx, cy)

		s = 1f + 0.04f * (1f - idleScale)

		canvas.scale(s, s, cx, cy)

		drawables[1].draw(cx, cy, canvas, p1)

		canvas.restore()
	}

	fun setColor(color: Int) {
		p1.color = color
	}

	companion object {
		const val MAX_SAMPLE_SUM = 6
		const val ALPHA = 61
		const val ANIMATION_DURATION = 120f
		var IDLE_RADIUS = AndroidUtilities.dp(6f) * 0.33f
		var WAVE_RADIUS = AndroidUtilities.dp(12f) * 0.36f
	}
}
