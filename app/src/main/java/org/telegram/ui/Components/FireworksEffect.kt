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
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class FireworksEffect {
	private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var lastAnimationTime: Long = 0

	private inner class Particle {
		var x = 0f
		var y = 0f
		var vx = 0f
		var vy = 0f
		var velocity = 0f
		var alpha = 0f
		var lifeTime = 0f
		var currentTime = 0f
		var scale = 0f
		var color = 0
		var type = 0

		fun draw(canvas: Canvas) {
			when (type) {
				0 -> {
					particlePaint.color = color
					particlePaint.strokeWidth = AndroidUtilities.dp(1.5f) * scale
					particlePaint.alpha = (255 * alpha).toInt()
					canvas.drawPoint(x, y, particlePaint)
				}

				1 -> {
					// unused
				}
			}
		}
	}

	private val particles = mutableListOf<Particle>()
	private val freeParticles = mutableListOf<Particle>()

	init {
		particlePaint.strokeWidth = AndroidUtilities.dp(1.5f).toFloat()
		particlePaint.color = ApplicationLoader.applicationContext.getColor(R.color.text) and -0x19191a
		particlePaint.strokeCap = Paint.Cap.ROUND
		particlePaint.style = Paint.Style.STROKE

		for (a in 0..19) {
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
			particle.vy += dt / 100.0f
			particle.currentTime += dt.toFloat()

			a++
		}
	}

	fun onDraw(parent: View?, canvas: Canvas?) {
		if (parent == null || canvas == null) {
			return
		}

		val count = particles.size

		for (a in 0 until count) {
			val particle = particles[a]
			particle.draw(canvas)
		}

		if (Utilities.random.nextBoolean() && particles.size + 8 < 150) {
			val statusBarHeight = AndroidUtilities.statusBarHeight
			val cx = Utilities.random.nextFloat() * parent.measuredWidth
			val cy = statusBarHeight + Utilities.random.nextFloat() * (parent.measuredHeight - AndroidUtilities.dp(20f) - statusBarHeight)

			val color = when (Utilities.random.nextInt(4)) {
				0 -> -0xcbd126
				1 -> -0xcdfeb
				2 -> -0x328ad
				3 -> -0xe63bc6
				4 -> -0x1678
				else -> -0x1678
			}

			for (a in 0..7) {
				val angle = Utilities.random.nextInt(270) - 225
				val vx = cos(Math.PI / 180.0 * angle).toFloat()
				val vy = sin(Math.PI / 180.0 * angle).toFloat()

				val newParticle = if (freeParticles.isNotEmpty()) {
					freeParticles.removeAt(0)
				}
				else {
					Particle()
				}

				newParticle.x = cx
				newParticle.y = cy
				newParticle.vx = vx * 1.5f
				newParticle.vy = vy
				newParticle.color = color
				newParticle.alpha = 1.0f
				newParticle.currentTime = 0f
				newParticle.scale = max(1.0f, Utilities.random.nextFloat() * 1.5f)
				newParticle.type = 0 //Utilities.random.nextInt(2);
				newParticle.lifeTime = (1000 + Utilities.random.nextInt(1000)).toFloat()
				newParticle.velocity = 20.0f + Utilities.random.nextFloat() * 4.0f

				particles.add(newParticle)
			}
		}

		val newTime = System.currentTimeMillis()
		val dt = min(17, newTime - lastAnimationTime)

		updateParticles(dt)

		lastAnimationTime = newTime

		parent.invalidate()
	}
}
