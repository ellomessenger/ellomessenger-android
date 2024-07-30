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
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig

class PacmanAnimation(private val parentView: View) {
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var finishRunnable: Runnable? = null
	private var lastUpdateTime: Long = 0
	private val rect = RectF()
	private var progress = 0f
	private var translationProgress = 0f
	private var ghostProgress = 0f
	private val ghostPath by lazy { Path() }
	private var ghostWalk = false
	private var currentGhostWalk = false

	init {
		edgePaint.style = Paint.Style.STROKE
		edgePaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
	}

	fun setFinishRunnable(onAnimationFinished: Runnable?) {
		finishRunnable = onAnimationFinished
	}

	private fun update() {
		val newTime = System.currentTimeMillis()
		var dt = newTime - lastUpdateTime

		lastUpdateTime = newTime

		if (dt > 17) {
			dt = 17
		}

		if (progress >= 1.0f) {
			progress = 0.0f
		}

		progress += dt / 400.0f

		if (progress > 1.0f) {
			progress = 1.0f
		}

		translationProgress += dt / 2000.0f

		if (translationProgress > 1.0f) {
			translationProgress = 1.0f
		}

		ghostProgress += dt / 200.0f

		if (ghostProgress >= 1.0f) {
			ghostWalk = !ghostWalk
			ghostProgress = 0.0f
		}

		parentView.invalidate()
	}

	fun start() {
		translationProgress = 0.0f
		progress = 0.0f
		lastUpdateTime = System.currentTimeMillis()
		parentView.invalidate()
	}

	private fun drawGhost(canvas: Canvas, num: Int) {
		if (ghostWalk != currentGhostWalk) {
			ghostPath.reset()

			currentGhostWalk = ghostWalk

			if (currentGhostWalk) {
				ghostPath.moveTo(0f, AndroidUtilities.dp(50f).toFloat())
				ghostPath.lineTo(0f, AndroidUtilities.dp(24f).toFloat())

				rect.set(0f, 0f, AndroidUtilities.dp(42f).toFloat(), AndroidUtilities.dp(24f).toFloat())

				ghostPath.arcTo(rect, 180f, 180f, false)
				ghostPath.lineTo(AndroidUtilities.dp(42f).toFloat(), AndroidUtilities.dp(50f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(35f).toFloat(), AndroidUtilities.dp(43f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(28f).toFloat(), AndroidUtilities.dp(50f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(21f).toFloat(), AndroidUtilities.dp(43f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(14f).toFloat(), AndroidUtilities.dp(50f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(7f).toFloat(), AndroidUtilities.dp(43f).toFloat())
			}
			else {
				ghostPath.moveTo(0f, AndroidUtilities.dp(43f).toFloat())
				ghostPath.lineTo(0f, AndroidUtilities.dp(24f).toFloat())

				rect.set(0f, 0f, AndroidUtilities.dp(42f).toFloat(), AndroidUtilities.dp(24f).toFloat())

				ghostPath.arcTo(rect, 180f, 180f, false)
				ghostPath.lineTo(AndroidUtilities.dp(42f).toFloat(), AndroidUtilities.dp(43f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(35f).toFloat(), AndroidUtilities.dp(50f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(28f).toFloat(), AndroidUtilities.dp(43f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(21f).toFloat(), AndroidUtilities.dp(50f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(14f).toFloat(), AndroidUtilities.dp(43f).toFloat())
				ghostPath.lineTo(AndroidUtilities.dp(7f).toFloat(), AndroidUtilities.dp(50f).toFloat())
			}

			ghostPath.close()
		}

		canvas.drawPath(ghostPath, edgePaint)

		paint.color = when (num) {
			0 -> -0x16000
			1 -> -0x14d4e
			else -> -0xff2121
		}

		canvas.drawPath(ghostPath, paint)

		paint.color = -0x1

		rect.set(AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(14f).toFloat(), AndroidUtilities.dp(20f).toFloat(), AndroidUtilities.dp(28f).toFloat())

		canvas.drawOval(rect, paint)

		rect.set(AndroidUtilities.dp((8 + 16).toFloat()).toFloat(), AndroidUtilities.dp(14f).toFloat(), AndroidUtilities.dp((20 + 16).toFloat()).toFloat(), AndroidUtilities.dp(28f).toFloat())

		canvas.drawOval(rect, paint)

		paint.color = -0x1000000

		rect.set(AndroidUtilities.dp(14f).toFloat(), AndroidUtilities.dp(18f).toFloat(), AndroidUtilities.dp(19f).toFloat(), AndroidUtilities.dp(24f).toFloat())

		canvas.drawOval(rect, paint)

		rect.set(AndroidUtilities.dp((14 + 16).toFloat()).toFloat(), AndroidUtilities.dp(18f).toFloat(), AndroidUtilities.dp((19 + 16).toFloat()).toFloat(), AndroidUtilities.dp(24f).toFloat())

		canvas.drawOval(rect, paint)
	}

	fun draw(canvas: Canvas, cy: Int) {
		val size = AndroidUtilities.dp(110f)
		val height = AndroidUtilities.dp(if (SharedConfig.useThreeLinesLayout) 78f else 72f)
		val additionalSize = size + AndroidUtilities.dp((42 + 20).toFloat()) * 3
		val width = parentView.measuredWidth + additionalSize
		val translation = width * translationProgress - additionalSize
		val y = cy - size / 2

		paint.color = parentView.context.getColor(R.color.background)

		canvas.drawRect(0f, (cy - height / 2).toFloat(), translation + size / 2, (cy + height / 2 + 1).toFloat(), paint)

		paint.color = -0x10e00

		rect.set(translation, y.toFloat(), translation + size, (y + size).toFloat())

		val rad = if (progress < 0.5f) {
			(35 * (1.0f - progress / 0.5f)).toInt()
		}
		else {
			(35 * (progress - 0.5f) / 0.5f).toInt()
		}

		canvas.drawArc(rect, rad.toFloat(), (360 - rad * 2).toFloat(), true, edgePaint)
		canvas.drawArc(rect, rad.toFloat(), (360 - rad * 2).toFloat(), true, paint)

		paint.color = -0x1000000

		canvas.drawCircle(translation + size / 2 - AndroidUtilities.dp(8f), (y + size / 4).toFloat(), AndroidUtilities.dp(8f).toFloat(), paint)
		canvas.save()
		canvas.translate(translation + size + AndroidUtilities.dp(20f), (cy - AndroidUtilities.dp(25f)).toFloat())

		for (a in 0..2) {
			drawGhost(canvas, a)
			canvas.translate(AndroidUtilities.dp((42 + 20).toFloat()).toFloat(), 0f)
		}

		canvas.restore()

		if (translationProgress >= 1.0f) {
			finishRunnable?.run()
		}

		update()
	}
}
