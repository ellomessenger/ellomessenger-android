/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components.spoilers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Region
import android.text.Spanned
import android.view.MotionEvent
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.spoilers.SpoilersClickDetector.OnSpoilerClickedListener
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

open class SpoilersTextView @JvmOverloads constructor(context: Context, revealOnClick: Boolean = true) : TextView(context) {
	private val clickDetector: SpoilersClickDetector
	private var spoilers: MutableList<SpoilerEffect>? = null
	private val spoilersPool = Stack<SpoilerEffect>()
	private var isSpoilersRevealed = false
	private val path = Path()
	private var xRefPaint: Paint? = null

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		return if (clickDetector.onTouchEvent(event)) {
			true
		}
		else {
			super.dispatchTouchEvent(event)
		}
	}

	override fun setText(text: CharSequence?, type: BufferType) {
		isSpoilersRevealed = false
		super.setText(text, type)
	}

	override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
		super.onTextChanged(text, start, lengthBefore, lengthAfter)
		invalidateSpoilers()
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		invalidateSpoilers()
	}

	override fun onDraw(canvas: Canvas) {
		val pl = paddingLeft
		val pt = paddingTop

		canvas.save()

		path.rewind()

		spoilers?.forEach {
			val bounds = it.bounds
			path.addRect((bounds.left + pl).toFloat(), (bounds.top + pt).toFloat(), (bounds.right + pl).toFloat(), (bounds.bottom + pt).toFloat(), Path.Direction.CW)
		}

		canvas.clipPath(path, Region.Op.DIFFERENCE)

		super.onDraw(canvas)

		canvas.restore()
		canvas.save()
		canvas.clipPath(path)

		path.rewind()

		spoilers?.firstOrNull()?.getRipplePath(path)

		canvas.clipPath(path)

		super.onDraw(canvas)

		canvas.restore()

		if (!spoilers.isNullOrEmpty()) {
			val useAlphaLayer = spoilers?.firstOrNull()?.rippleProgress != -1f

			if (useAlphaLayer) {
				canvas.saveLayer(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), null)
			}
			else {
				canvas.save()
			}

			canvas.translate(paddingLeft.toFloat(), (paddingTop + AndroidUtilities.dp(2f)).toFloat())

			spoilers?.forEach {
				it.setColor(paint.color)
				it.draw(canvas)
			}

			if (useAlphaLayer) {
				path.rewind()

				spoilers?.firstOrNull()?.getRipplePath(path)

				if (xRefPaint == null) {
					xRefPaint = Paint(Paint.ANTI_ALIAS_FLAG)
					xRefPaint?.color = -0x1000000
					xRefPaint?.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
				}

				canvas.drawPath(path, xRefPaint!!)
			}

			canvas.restore()
		}
	}

	private fun invalidateSpoilers() {
		val spoilers = spoilers

		if (spoilers.isNullOrEmpty()) {
			return
		}

		spoilersPool.addAll(spoilers)
		spoilers.clear()

		if (isSpoilersRevealed) {
			invalidate()
			return
		}

		val layout = layout

		if (layout != null && text is Spanned) {
			SpoilerEffect.addSpoilers(this, spoilersPool, spoilers)
		}

		invalidate()
	}

	init {
		spoilers = mutableListOf()

		clickDetector = SpoilersClickDetector(this, spoilers!!, OnSpoilerClickedListener { eff, x, y ->
			if (isSpoilersRevealed || !revealOnClick) {
				return@OnSpoilerClickedListener
			}

			eff.setOnRippleEndCallback {
				post {
					isSpoilersRevealed = true
					invalidateSpoilers()
				}
			}

			val rad = sqrt(width.toDouble().pow(2.0) + height.toDouble().pow(2.0)).toFloat()

			spoilers?.forEach {
				it.startRipple(x, y, rad)
			}
		})
	}
}
