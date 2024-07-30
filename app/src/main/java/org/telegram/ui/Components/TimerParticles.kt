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
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Utilities
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class TimerParticles {
	private var lastAnimationTime: Long = 0
	private val particles = mutableListOf<Particle>()
	private val freeParticles = mutableListOf<Particle>()

	init {
		for (a in 0..39) {
			freeParticles.add(Particle())
		}
	}

	private fun updateParticles(dt: Long) {
		var count = particles.size
		var a = 0

		while (a < count) {
			val particle = particles[a]

			if (particle.currentTime >= particle.lifeTime) {
				if (freeParticles.size < 40) {
					freeParticles.add(particle)
				}

				particles.removeAt(a)

				a--
				count--
				a++

				continue
			}

			particle.alpha = 1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(particle.currentTime / particle.lifeTime)
			particle.x += particle.vx * particle.velocity * dt / 500.0f
			particle.y += particle.vy * particle.velocity * dt / 500.0f
			particle.currentTime += dt.toFloat()

			a++
		}
	}

	fun draw(canvas: Canvas, particlePaint: Paint, rect: RectF, radProgress: Float, alpha: Float) {
		val count = particles.size

		for (a in 0 until count) {
			val particle = particles[a]
			particlePaint.alpha = (255 * particle.alpha * alpha).toInt()
			canvas.drawPoint(particle.x, particle.y, particlePaint)
		}

		val vx = sin(Math.PI / 180.0 * (radProgress - 90))
		val vy = -cos(Math.PI / 180.0 * (radProgress - 90))
		val rad = rect.width() / 2
		val cx = (-vy * rad + rect.centerX()).toFloat()
		val cy = (vx * rad + rect.centerY()).toFloat()

		for (a in 0..0) {
			val newParticle = if (freeParticles.isNotEmpty()) {
				freeParticles.removeAt(0)

			}
			else {
				Particle()
			}

			newParticle.x = cx
			newParticle.y = cy

			var angle = Math.PI / 180.0 * (Utilities.random.nextInt(140) - 70)

			if (angle < 0) {
				angle += Math.PI * 2
			}

			newParticle.vx = (vx * cos(angle) - vy * sin(angle)).toFloat()
			newParticle.vy = (vx * sin(angle) + vy * cos(angle)).toFloat()
			newParticle.alpha = 1.0f
			newParticle.currentTime = 0f
			newParticle.lifeTime = (400 + Utilities.random.nextInt(100)).toFloat()
			newParticle.velocity = 20.0f + Utilities.random.nextFloat() * 4.0f

			particles.add(newParticle)
		}

		val newTime = SystemClock.elapsedRealtime()
		val dt = min(20, newTime - lastAnimationTime)

		updateParticles(dt)

		lastAnimationTime = newTime
	}

	private class Particle {
		var x = 0f
		var y = 0f
		var vx = 0f
		var vy = 0f
		var velocity = 0f
		var alpha = 0f
		var lifeTime = 0f
		var currentTime = 0f
	}
}
