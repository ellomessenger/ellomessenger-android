/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.Utilities
import java.util.Calendar

class FireworksOverlay(context: Context) : View(context) {
	private val rect = RectF()
	private var lastUpdateTime: Long = 0
	private var startedFall = false
	private var speedCoeff = 1.0f
	private var fallingDownCount = 0
	private var isFebruary14 = false
	private val particles = ArrayList<Particle>(particlesCount + fallParticlesCount)

	var isStarted = false
		private set

	private inner class Particle {
		var type: Byte = 0
		var colorType: Byte = 0
		var side: Byte = 0
		var typeSize: Byte = 0
		var xFinished: Byte = 0
		var finishedStart: Byte = 0
		var x = 0f
		var y = 0f
		var rotation: Short = 0
		var moveX = 0f
		var moveY = 0f

		fun draw(canvas: Canvas) {
			if (type.toInt() == 0) {
				canvas.drawCircle(x, y, AndroidUtilities.dp(typeSize.toFloat()).toFloat(), paint[colorType.toInt()])
			}
			else if (type.toInt() == 1) {
				rect.set(x - AndroidUtilities.dp(typeSize.toFloat()), y - AndroidUtilities.dp(2f), x + AndroidUtilities.dp(typeSize.toFloat()), y + AndroidUtilities.dp(2f))

				canvas.save()
				canvas.rotate(rotation.toFloat(), rect.centerX(), rect.centerY())
				canvas.drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), paint[colorType.toInt()])
				canvas.restore()
			}
			else if (type.toInt() == 2) {
				val drawable = heartDrawable[colorType.toInt()]
				val w = drawable.intrinsicWidth / 2
				val h = drawable.intrinsicHeight / 2

				drawable.setBounds(x.toInt() - w, y.toInt() - h, x.toInt() + w, y.toInt() + h)

				canvas.save()
				canvas.rotate(rotation.toFloat(), x, y)
				canvas.scale(typeSize / 6.0f, typeSize / 6.0f, x, y)

				drawable.draw(canvas)

				canvas.restore()
			}
		}

		fun update(dt: Int): Boolean {
			val moveCoeff = dt / 16.0f

			x += moveX * moveCoeff
			y += moveY * moveCoeff

			if (xFinished.toInt() != 0) {
				val dp = AndroidUtilities.dp(1f) * 0.5f

				if (xFinished.toInt() == 1) {
					moveX += dp * moveCoeff * 0.05f

					if (moveX >= dp) {
						xFinished = 2
					}
				}
				else {
					moveX -= dp * moveCoeff * 0.05f

					if (moveX <= -dp) {
						xFinished = 1
					}
				}
			}
			else {
				if (side.toInt() == 0) {
					if (moveX > 0) {
						moveX -= moveCoeff * 0.05f

						if (moveX <= 0) {
							moveX = 0f
							xFinished = finishedStart
						}
					}
				}
				else {
					if (moveX < 0) {
						moveX += moveCoeff * 0.05f

						if (moveX >= 0) {
							moveX = 0f
							xFinished = finishedStart
						}
					}
				}
			}

			val yEdge = -AndroidUtilities.dp(1.0f) / 2.0f
			val wasNegative = moveY < yEdge

			moveY += if (moveY > yEdge) {
				AndroidUtilities.dp(1.0f) / 3.0f * moveCoeff * speedCoeff
			}
			else {
				AndroidUtilities.dp(1.0f) / 3.0f * moveCoeff
			}

			if (wasNegative && moveY > yEdge) {
				fallingDownCount++
			}

			if (type.toInt() == 1 || type.toInt() == 2) {
				rotation = (rotation + (moveCoeff * 10).toInt().toShort()).toShort()

				if (rotation > 360) {
					rotation = (rotation - 360).toShort()
				}
			}

			return y >= measuredHeight
		}
	}

	private fun createParticle(fall: Boolean): Particle {
		val particle = Particle()
		particle.type = Utilities.random.nextInt(2).toByte()

		if (isFebruary14 && particle.type.toInt() == 0) {
			particle.type = 2
			particle.colorType = Utilities.random.nextInt(heartColors.size).toByte()
		}
		else {
			particle.colorType = Utilities.random.nextInt(colors.size).toByte()
		}

		particle.side = Utilities.random.nextInt(2).toByte()
		particle.finishedStart = (1 + Utilities.random.nextInt(2)).toByte()

		if (particle.type.toInt() == 0 || particle.type.toInt() == 2) {
			particle.typeSize = (4 + Utilities.random.nextFloat() * 2).toInt().toByte()
		}
		else {
			particle.typeSize = (4 + Utilities.random.nextFloat() * 4).toInt().toByte()
		}

		if (fall) {
			particle.y = -Utilities.random.nextFloat() * measuredHeight * 1.2f
			particle.x = (AndroidUtilities.dp(5f) + Utilities.random.nextInt(measuredWidth - AndroidUtilities.dp(10f))).toFloat()
			particle.xFinished = particle.finishedStart
		}
		else {
			val xOffset = AndroidUtilities.dp((4 + Utilities.random.nextInt(10)).toFloat())
			val yOffset = measuredHeight / 4

			if (particle.side.toInt() == 0) {
				particle.x = -xOffset.toFloat()
			}
			else {
				particle.x = (measuredWidth + xOffset).toFloat()
			}

			particle.moveX = (if (particle.side.toInt() == 0) 1 else -1) * (AndroidUtilities.dp(1.2f) + Utilities.random.nextFloat() * AndroidUtilities.dp(4f))
			particle.moveY = -(AndroidUtilities.dp(4f) + Utilities.random.nextFloat() * AndroidUtilities.dp(4f))
			particle.y = (yOffset / 2 + Utilities.random.nextInt(yOffset * 2)).toFloat()
		}

		return particle
	}

	fun start() {
		particles.clear()

		setLayerType(LAYER_TYPE_HARDWARE, null)

		isStarted = true
		startedFall = false
		fallingDownCount = 0
		speedCoeff = 1.0f

		val calendar = Calendar.getInstance()
		calendar.timeInMillis = System.currentTimeMillis()

		val day = calendar[Calendar.DAY_OF_MONTH]
		val month = calendar[Calendar.MONTH]

		isFebruary14 = month == 1 && (BuildConfig.DEBUG_PRIVATE_VERSION || day == 14)

		for (a in 0 until particlesCount) {
			particles.add(createParticle(false))
		}

		invalidate()
	}

	private fun startFall() {
		if (startedFall) {
			return
		}

		startedFall = true

		for (a in 0 until fallParticlesCount) {
			particles.add(createParticle(true))
		}
	}

	override fun onDraw(canvas: Canvas) {
		val newTime = SystemClock.elapsedRealtime()
		var dt = (newTime - lastUpdateTime).toInt()

		lastUpdateTime = newTime

		if (dt > 18) {
			dt = 16
		}

		var a = 0
		var n = particles.size

		while (a < n) {
			val p = particles[a]

			p.draw(canvas)

			if (p.update(dt)) {
				particles.removeAt(a)
				a--
				n--
			}

			a++
		}

		if (fallingDownCount >= particlesCount / 2 && speedCoeff > 0.2f) {
			startFall()
			speedCoeff -= dt / 16.0f * 0.15f

			if (speedCoeff < 0.2f) {
				speedCoeff = 0.2f
			}
		}

		if (particles.isNotEmpty()) {
			invalidate()
		}
		else {
			isStarted = false

			AndroidUtilities.runOnUIThread {
				if (!isStarted) {
					setLayerType(LAYER_TYPE_NONE, null)
				}
			}
		}
	}

	companion object {
		private val particlesCount = if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) 50 else 60
		private val fallParticlesCount = if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) 20 else 30
		private val colors = intArrayOf(-0xd34318, -0x61fb30, -0x134fe, -0x2dca9, -0xd87302, -0xa64794)
		private val heartColors = intArrayOf(-0x1daa85, -0xa0320e, -0x2597, -0x249c9d, -0x1c8950)

		private val paint by lazy {
			colors.map {
				Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it }
			}
		}

		private val heartDrawable by lazy {
			heartColors.map {
				ResourcesCompat.getDrawable(ApplicationLoader.applicationContext.resources, R.drawable.heart_confetti, null)!!.mutate().apply {
					colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.MULTIPLY)
				}
			}
		}
	}
}
