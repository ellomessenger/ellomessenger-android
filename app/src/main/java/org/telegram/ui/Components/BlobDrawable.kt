/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

class BlobDrawable(n: Int) {
	@JvmField
	var minRadius = 0f

	@JvmField
	var maxRadius = 0f

	@JvmField
	var paint = Paint(Paint.ANTI_ALIAS_FLAG)

	var amplitude = 0f
		private set

	private val radius: FloatArray
	private val angle: FloatArray
	private val radiusNext: FloatArray
	private val angleNext: FloatArray
	private val progress: FloatArray
	private val speed: FloatArray
	private val pointStart = FloatArray(4)
	private val pointEnd = FloatArray(4)
	private val n: Float
	private val l: Float
	private var cubicBezierK = 1f
	private val m = Matrix()
	private var animateToAmplitude = 0f
	private var animateAmplitudeDiff = 0f
	private val random = Random()
	private val path = Path()

	private fun generateBlob(radius: FloatArray, angle: FloatArray, i: Int) {
		val angleDif = 360f / n * 0.05f
		val radDif = maxRadius - minRadius
		radius[i] = minRadius + abs(random.nextInt() % 100f / 100f) * radDif
		angle[i] = 360f / n * i + random.nextInt() % 100f / 100f * angleDif
		speed[i] = (0.017 + 0.003 * (abs(random.nextInt() % 100f) / 100f)).toFloat()
	}

	fun update(amplitude: Float, speedScale: Float) {
		var i = 0

		while (i < n) {
			progress[i] += speed[i] * MIN_SPEED + amplitude * speed[i] * MAX_SPEED * speedScale

			if (progress[i] >= 1f) {
				progress[i] = 0f
				radius[i] = radiusNext[i]
				angle[i] = angleNext[i]
				generateBlob(radiusNext, angleNext, i)
			}

			i++
		}
	}

	fun draw(cX: Float, cY: Float, canvas: Canvas, paint: Paint?) {
		path.reset()

		var i = 0

		while (i < n) {
			val progress = progress[i]
			val nextIndex = if (i + 1 < n) i + 1 else 0
			val progressNext = this.progress[nextIndex]
			val r1 = radius[i] * (1f - progress) + radiusNext[i] * progress
			val r2 = radius[nextIndex] * (1f - progressNext) + radiusNext[nextIndex] * progressNext
			val angle1 = angle[i] * (1f - progress) + angleNext[i] * progress
			val angle2 = angle[nextIndex] * (1f - progressNext) + angleNext[nextIndex] * progressNext
			val l = l * (min(r1, r2) + (max(r1, r2) - min(r1, r2)) / 2f) * cubicBezierK

			m.reset()
			m.setRotate(angle1, cX, cY)

			pointStart[0] = cX
			pointStart[1] = cY - r1
			pointStart[2] = cX + l
			pointStart[3] = cY - r1

			m.mapPoints(pointStart)

			pointEnd[0] = cX
			pointEnd[1] = cY - r2
			pointEnd[2] = cX - l
			pointEnd[3] = cY - r2

			m.reset()
			m.setRotate(angle2, cX, cY)
			m.mapPoints(pointEnd)

			if (i == 0) {
				path.moveTo(pointStart[0], pointStart[1])
			}

			path.cubicTo(pointStart[2], pointStart[3], pointEnd[2], pointEnd[3], pointEnd[0], pointEnd[1])

			i++
		}

		canvas.save()
		canvas.drawPath(path, paint!!)
		canvas.restore()
	}

	fun generateBlob() {
		var i = 0

		while (i < n) {
			generateBlob(radius, angle, i)
			generateBlob(radiusNext, angleNext, i)
			progress[i] = 0f
			i++
		}
	}

	init {
		this.n = n.toFloat()

		l = (4.0 / 3.0 * tan(Math.PI / (2 * this.n))).toFloat()
		radius = FloatArray(n)
		angle = FloatArray(n)
		radiusNext = FloatArray(n)
		angleNext = FloatArray(n)
		progress = FloatArray(n)
		speed = FloatArray(n)

		var i = 0

		while (i < this.n) {
			generateBlob(radius, angle, i)
			generateBlob(radiusNext, angleNext, i)

			progress[i] = 0f

			i++
		}
	}

	fun setValue(value: Float, isBig: Boolean) {
		animateToAmplitude = value

		animateAmplitudeDiff = if (isBig) {
			if (animateToAmplitude > amplitude) {
				(animateToAmplitude - amplitude) / (100f + 300f * animationSpeed)
			}
			else {
				(animateToAmplitude - amplitude) / (100 + 500f * animationSpeed)
			}
		}
		else {
			if (animateToAmplitude > amplitude) {
				(animateToAmplitude - amplitude) / (100f + 400f * animationSpeedTiny)
			}
			else {
				(animateToAmplitude - amplitude) / (100f + 500f * animationSpeedTiny)
			}
		}
	}

	fun updateAmplitude(dt: Long) {
		if (animateToAmplitude != amplitude) {
			amplitude += animateAmplitudeDiff * dt

			if (animateAmplitudeDiff > 0) {
				if (amplitude > animateToAmplitude) {
					amplitude = animateToAmplitude
				}
			}
			else {
				if (amplitude < animateToAmplitude) {
					amplitude = animateToAmplitude
				}
			}
		}
	}

	companion object {
		@JvmField
		var MAX_SPEED = 8.2f

		@JvmField
		var MIN_SPEED = 0.8f

		@JvmField
		var AMPLITUDE_SPEED = 0.33f

		@JvmField
		var SCALE_BIG = 0.807f

		@JvmField
		var SCALE_SMALL = 0.704f

		@JvmField
		var SCALE_BIG_MIN = 0.878f

		@JvmField
		var SCALE_SMALL_MIN = 0.926f

		@JvmField
		var FORM_BIG_MAX = 0.6f

		@JvmField
		var FORM_SMALL_MAX = 0.6f

		@JvmField
		var GLOBAL_SCALE = 1f

		@JvmField
		var GRADIENT_SPEED_MIN = 0.5f

		@JvmField
		var GRADIENT_SPEED_MAX = 0.01f

		@JvmField
		var LIGHT_GRADIENT_SIZE = 0.5f

		private const val ANIMATION_SPEED_WAVE_HUGE = 0.65f
		private const val ANIMATION_SPEED_WAVE_SMALL = 0.45f
		private const val animationSpeed = 1f - ANIMATION_SPEED_WAVE_HUGE
		private const val animationSpeedTiny = 1f - ANIMATION_SPEED_WAVE_SMALL
	}
}
