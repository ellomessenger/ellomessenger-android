/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components.voip

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.WindowManager
import android.widget.FrameLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.VoIPFragment
import kotlin.math.abs
import kotlin.math.max

open class VoIPWindowView(var activity: Activity, enterAnimation: Boolean) : FrameLayout(activity) {
	private val orientationBefore: Int
	private var animationIndex = -1
	private var runEnterTransition = false
	private var startDragging = false
	var finished = false
	var isLockOnScreen = false
	var startX = 0f
	var startY = 0f
	var velocityTracker: VelocityTracker? = null

	init {
		systemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
		fitsSystemWindows = true
		orientationBefore = activity.requestedOrientation
		activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

		if (!enterAnimation) {
			runEnterTransition = true
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		if (!runEnterTransition) {
			runEnterTransition = true
			startEnterTransition()
		}
	}

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
		return onTouchEvent(ev)
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (isLockOnScreen) {
			return false
		}

		when (event.action) {
			MotionEvent.ACTION_DOWN -> {
				startX = event.x
				startY = event.y

				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain()
				}

				velocityTracker?.clear()
			}

			MotionEvent.ACTION_MOVE -> {
				var dx = event.x - startX
				val dy = event.y - startY

				if (!startDragging && abs(dx) > AndroidUtilities.getPixelsInCM(0.4f, true) && abs(dx) / 3 > dy) {
					startX = event.x
					dx = 0f
					startDragging = true
				}

				if (startDragging) {
					if (dx < 0) {
						dx = 0f
					}

					if (velocityTracker == null) {
						velocityTracker = VelocityTracker.obtain()
					}

					velocityTracker?.addMovement(event)

					translationX = dx
				}

				return startDragging
			}

			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				val x = translationX

				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain()
				}

				velocityTracker?.computeCurrentVelocity(1000)

				val velX = velocityTracker!!.xVelocity
				val velY = velocityTracker!!.yVelocity

				val backAnimation = x < measuredWidth / 3.0f && (velX < 3500 || velX < velY)

				if (!backAnimation) {
					val distToMove = measuredWidth - translationX
					finish(max((200.0f / measuredWidth * distToMove).toInt(), 50).toLong())
				}
				else {
					animate().translationX(0f).start()
				}

				startDragging = false
			}
		}

		return false
	}

	@JvmOverloads
	fun finish(animDuration: Long = 150) {
		if (!finished) {
			finished = true

			VoIPFragment.clearInstance()

			if (isLockOnScreen) {
				runCatching {
					val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
					wm.removeView(this@VoIPWindowView)
				}
			}
			else {
				val account = UserConfig.selectedAccount

				animationIndex = NotificationCenter.getInstance(account).setAnimationInProgress(animationIndex, null)

				animate().translationX(measuredWidth.toFloat()).setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						NotificationCenter.getInstance(account).onAnimationFinish(animationIndex)

						if (parent != null) {
							activity.requestedOrientation = orientationBefore

							val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

							visibility = GONE

							runCatching {
								wm.removeView(this@VoIPWindowView)
							}
						}
					}
				}).setDuration(animDuration).setInterpolator(CubicBezierInterpolator.DEFAULT).start()
			}
		}
	}

	private fun startEnterTransition() {
		if (!isLockOnScreen) {
			translationX = measuredWidth.toFloat()
			animate().translationX(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start()
		}
	}

	fun createWindowLayoutParams(): WindowManager.LayoutParams {
		val windowLayoutParams = WindowManager.LayoutParams()
		windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
		windowLayoutParams.format = PixelFormat.TRANSPARENT
		windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
		windowLayoutParams.gravity = Gravity.TOP or Gravity.LEFT
		windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW
		windowLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

		if (Build.VERSION.SDK_INT >= 28) {
			windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
		}

		windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
		windowLayoutParams.flags = windowLayoutParams.flags or (WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		return windowLayoutParams
	}

	fun requestFullscreen(request: Boolean) {
		if (request) {
			systemUiVisibility = systemUiVisibility or SYSTEM_UI_FLAG_FULLSCREEN
		}
		else {
			var flags = systemUiVisibility
			flags = flags and SYSTEM_UI_FLAG_FULLSCREEN.inv()
			systemUiVisibility = flags
		}
	}

	fun finishImmediate() {
		if (parent != null) {
			activity.requestedOrientation = orientationBefore
			val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
			visibility = GONE
			wm.removeView(this@VoIPWindowView)
		}
	}
}
