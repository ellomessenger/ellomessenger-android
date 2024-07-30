/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components.spoilers

import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import androidx.core.view.GestureDetectorCompat

class SpoilersClickDetector(v: View, spoilers: List<SpoilerEffect>, offsetPadding: Boolean, clickedListener: OnSpoilerClickedListener?) {
	private val gestureDetector: GestureDetectorCompat
	private var trackingTap = false

	constructor(v: View, spoilers: List<SpoilerEffect>, clickedListener: OnSpoilerClickedListener?) : this(v, spoilers, true, clickedListener)

	init {
		gestureDetector = GestureDetectorCompat(v.context, object : SimpleOnGestureListener() {
			override fun onDown(e: MotionEvent): Boolean {
				var x = e.x.toInt()
				var y = e.y.toInt()

				y += v.scrollY

				if (offsetPadding) {
					x -= v.paddingLeft
					y -= v.paddingTop
				}

				for (eff in spoilers) {
					if (eff.bounds.contains(x, y)) {
						trackingTap = true
						return true
					}
				}

				return false
			}

			override fun onSingleTapUp(e: MotionEvent): Boolean {
				if (trackingTap) {
					v.playSoundEffect(SoundEffectConstants.CLICK)

					trackingTap = false

					var x = e.x.toInt()
					var y = e.y.toInt()

					y += v.scrollY

					if (offsetPadding) {
						x -= v.paddingLeft
						y -= v.paddingTop
					}

					for (eff in spoilers) {
						if (eff.bounds.contains(x, y)) {
							clickedListener?.onSpoilerClicked(eff, x.toFloat(), y.toFloat())
							return true
						}
					}
				}

				return false
			}
		})
	}

	fun onTouchEvent(ev: MotionEvent?): Boolean {
		return gestureDetector.onTouchEvent(ev!!)
	}

	fun interface OnSpoilerClickedListener {
		fun onSpoilerClicked(spoiler: SpoilerEffect, x: Float, y: Float)
	}
}
