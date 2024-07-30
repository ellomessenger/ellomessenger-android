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
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.voip.Instance
import org.telegram.messenger.voip.StateListener
import org.telegram.messenger.voip.VideoCapturerDevice
import org.telegram.messenger.voip.VoIPService
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.LaunchActivity
import org.telegram.ui.VoIPFragment
import kotlin.math.max
import kotlin.math.min

class VoIPPiPView(context: Context, val parentWidth: Int, val parentHeight: Int, expanded: Boolean) : StateListener, NotificationCenterDelegate {
	private var animatorToCameraMini: ValueAnimator? = null
	private var callingUserIsVideo = false
	private var currentAccount = 0
	private var currentUserIsVideo = false
	private var expandedAnimationInProgress = false
	private var windowManager: WindowManager? = null
	val callingUserTextureView: VoIPTextureView
	val currentUserTextureView: VoIPTextureView
	val floatingView = FloatingView(context)
	var closeIcon: ImageView? = null
	var enlargeIcon: ImageView? = null
	var expandAnimator: ValueAnimator? = null
	var expanded = false
	var moving = false
	var point = FloatArray(2)
	var progressToCameraMini = 0f
	var startTime: Long = 0
	var startX = 0f
	var startY = 0f
	var topShadow: View? = null
	var windowLayoutParams: WindowManager.LayoutParams? = null
	var xOffset = (parentWidth * SCALE_EXPANDED * 1.05f - parentWidth * SCALE_EXPANDED).toInt() / 2
	var yOffset = (parentHeight * SCALE_EXPANDED * 1.05f - parentHeight * SCALE_EXPANDED).toInt() / 2
	var moveToBoundsAnimator: AnimatorSet? = null

	val windowView: FrameLayout = object : FrameLayout(context) {
		override fun onDraw(canvas: Canvas) {
			val outerDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.calls_pip_outershadow, null)

			canvas.save()
			canvas.scale(floatingView.scaleX, floatingView.scaleY, floatingView.left + floatingView.pivotX, floatingView.top + floatingView.pivotY)

			outerDrawable?.setBounds(floatingView.left - AndroidUtilities.dp(2f), floatingView.top - AndroidUtilities.dp(2f), floatingView.right + AndroidUtilities.dp(2f), floatingView.bottom + AndroidUtilities.dp(2f))
			outerDrawable?.draw(canvas)

			canvas.restore()

			super.onDraw(canvas)
		}
	}

	private val animatorToCameraMiniUpdater = AnimatorUpdateListener { valueAnimator: ValueAnimator ->
		progressToCameraMini = valueAnimator.animatedValue as Float
		floatingView.invalidate()
	}

	val collapseRunnable = Runnable {
		instance?.floatingView?.expand(false)
	}

	private val updateXlistener = AnimatorUpdateListener {
		val x = it.animatedValue as Float
		windowLayoutParams?.x = x.toInt()

		if (windowView.parent != null) {
			windowManager?.updateViewLayout(windowView, windowLayoutParams)
		}
	}
	private val updateYlistener = AnimatorUpdateListener {
		val y = it.animatedValue as Float

		windowLayoutParams?.y = y.toInt()

		if (windowView.parent != null) {
			windowManager?.updateViewLayout(windowView, windowLayoutParams)
		}
	}

	init {
		windowView.setWillNotDraw(false)
		windowView.setPadding(xOffset, yOffset, xOffset, yOffset)

		callingUserTextureView = VoIPTextureView(context, false, true)
		callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_NONE
		currentUserTextureView = VoIPTextureView(context, false, true)

		currentUserTextureView.renderer.setMirror(true)

		floatingView.addView(callingUserTextureView)
		floatingView.addView(currentUserTextureView)
		floatingView.setBackgroundColor(Color.GRAY)

		windowView.addView(floatingView)

		windowView.clipChildren = false
		windowView.clipToPadding = false

		if (expanded) {
			topShadow = View(context)
			topShadow?.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.3f).toInt()), Color.TRANSPARENT))

			floatingView.addView(topShadow, FrameLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(60f))

			closeIcon = ImageView(context)
			closeIcon?.setImageResource(R.drawable.pip_close)
			closeIcon?.setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
			closeIcon?.contentDescription = context.getString(R.string.Close)

			floatingView.addView(closeIcon, LayoutHelper.createFrame(40, 40f, Gravity.TOP or Gravity.RIGHT, 4f, 4f, 4f, 0f))

			enlargeIcon = ImageView(context)
			enlargeIcon?.setImageResource(R.drawable.pip_enlarge)
			enlargeIcon?.setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
			enlargeIcon?.contentDescription = context.getString(R.string.Open)

			floatingView.addView(enlargeIcon, LayoutHelper.createFrame(40, 40f, Gravity.TOP or Gravity.LEFT, 4f, 4f, 4f, 0f))

			closeIcon!!.setOnClickListener {
				val service = VoIPService.sharedInstance

				if (service != null) {
					service.hangUp()
				}
				else {
					finish()
				}
			}

			enlargeIcon?.setOnClickListener {
				if (context is LaunchActivity && !ApplicationLoader.mainInterfacePaused) {
					VoIPFragment.show((context as Activity), currentAccount)
				}
				else if (context is LaunchActivity) {
					val intent = Intent(context, LaunchActivity::class.java)
					intent.action = "voip"
					context.startActivity(intent)
				}
			}
		}

		VoIPService.sharedInstance?.registerStateListener(this)

		updateViewState()
	}

	private fun setRelativePosition(x: Float, y: Float) {
		val width = AndroidUtilities.displaySize.x.toFloat()
		val height = AndroidUtilities.displaySize.y.toFloat()
		val leftPadding = AndroidUtilities.dp(16f).toFloat()
		val rightPadding = AndroidUtilities.dp(16f).toFloat()
		val topPadding = AndroidUtilities.dp(60f).toFloat()
		val bottomPadding = AndroidUtilities.dp(16f).toFloat()
		val widthNormal = parentWidth * SCALE_NORMAL
		val heightNormal = parentHeight * SCALE_NORMAL
		val floatingWidth = if (floatingView.measuredWidth == 0) widthNormal else floatingView.measuredWidth.toFloat()
		val floatingHeight = if (floatingView.measuredWidth == 0) heightNormal else floatingView.measuredHeight.toFloat()

		windowLayoutParams?.x = (x * (width - leftPadding - rightPadding - floatingWidth) - (xOffset - leftPadding)).toInt()
		windowLayoutParams?.y = (y * (height - topPadding - bottomPadding - floatingHeight) - (yOffset - topPadding)).toInt()

		if (windowView.parent != null) {
			windowManager?.updateViewLayout(windowView, windowLayoutParams)
		}
	}

	private fun finishInternal() {
		currentUserTextureView.renderer.release()
		callingUserTextureView.renderer.release()

		val service = VoIPService.sharedInstance
		service?.unregisterStateListener(this)

		windowView.visibility = View.GONE

		if (windowView.parent != null) {
			floatingView.getRelativePosition(point)

			val x = min(1f, max(0f, point[0]))
			val y = min(1f, max(0f, point[1]))

			val preferences = ApplicationLoader.applicationContext.getSharedPreferences("voippipconfig", Context.MODE_PRIVATE)
			preferences.edit().putFloat("relativeX", x).putFloat("relativeY", y).commit()

			try {
				windowManager?.removeView(windowView)
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.didEndCall)
	}

	override fun onStateChanged(state: Int) {
		if (state == VoIPService.STATE_ENDED || state == VoIPService.STATE_BUSY || state == VoIPService.STATE_FAILED || state == VoIPService.STATE_HANGING_UP) {
			AndroidUtilities.runOnUIThread({ finish() }, 200)
		}

		val service = VoIPService.sharedInstance

		if (service == null) {
			finish()
			return
		}

		if (state == VoIPService.STATE_ESTABLISHED && !service.isVideoAvailable()) {
			finish()
			return
		}

		updateViewState()
	}

	override fun onSignalBarsCountChanged(count: Int) {
		// unused
	}

	override fun onAudioSettingsChanged() {
		// unused
	}

	override fun onMediaStateUpdated(audioState: Int, videoState: Int) {
		updateViewState()
	}

	override fun onCameraSwitch(isFrontFace: Boolean) {
		updateViewState()
	}

	override fun onVideoAvailableChange(isAvailable: Boolean) {
		// unused
	}

	override fun onScreenOnChange(screenOn: Boolean) {
		val service = VoIPService.sharedInstance ?: return

		if (!screenOn && currentUserIsVideo) {
			service.setVideoState(false, Instance.VIDEO_STATE_PAUSED)
		}
		else if (screenOn && service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
			service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE)
		}
	}

	private fun updateViewState() {
		val animated = floatingView.measuredWidth != 0
		val callingUserWasVideo = callingUserIsVideo
		val service = VoIPService.sharedInstance

		if (service != null) {
			callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE
			currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED

			currentUserTextureView.renderer.setMirror(service.isFrontFaceCamera())
			currentUserTextureView.setIsScreencast(service.isScreencast())
			currentUserTextureView.setScreenshareMiniProgress(1.0f, false)
		}

		if (!animated) {
			progressToCameraMini = if (callingUserIsVideo) 1f else 0f
		}
		else {
			if (callingUserWasVideo != callingUserIsVideo) {
				animatorToCameraMini?.cancel()

				animatorToCameraMini = ValueAnimator.ofFloat(progressToCameraMini, if (callingUserIsVideo) 1f else 0f)
				animatorToCameraMini?.addUpdateListener(animatorToCameraMiniUpdater)
				animatorToCameraMini?.setDuration(300)?.interpolator = CubicBezierInterpolator.DEFAULT
				animatorToCameraMini?.start()
			}
		}
	}

	fun onTransitionEnd() {
		VoIPService.sharedInstance?.swapSinks()
	}

	fun onPause() {
		if (windowLayoutParams?.type == WindowManager.LayoutParams.LAST_APPLICATION_WINDOW) {
			val service = VoIPService.sharedInstance

			if (currentUserIsVideo) {
				service?.setVideoState(false, Instance.VIDEO_STATE_PAUSED)
			}
		}
	}

	fun onResume() {
		val service = VoIPService.sharedInstance

		if (service != null && service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
			service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE)
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.didEndCall) {
			finish()
		}
	}

	inner class FloatingView(context: Context) : FrameLayout(context) {
		private var bottomPadding = 0f
		private var leftPadding = 0f
		private var rightPadding = 0f
		var topPadding = 0f
		var touchSlop: Float

		init {
			touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

			outlineProvider = object : ViewOutlineProvider() {
				override fun getOutline(view: View, outline: Outline) {
					outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight, 1f / view.scaleX * AndroidUtilities.dp(4f))
				}
			}

			clipToOutline = true
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)

			leftPadding = AndroidUtilities.dp(16f).toFloat()
			rightPadding = AndroidUtilities.dp(16f).toFloat()
			topPadding = AndroidUtilities.dp(60f).toFloat()
			bottomPadding = AndroidUtilities.dp(16f).toFloat()
		}

		override fun dispatchDraw(canvas: Canvas) {
			currentUserTextureView.pivotX = callingUserTextureView.measuredWidth.toFloat()
			currentUserTextureView.pivotY = callingUserTextureView.measuredHeight.toFloat()
			currentUserTextureView.translationX = -AndroidUtilities.dp(4f) * (1f / scaleX) * progressToCameraMini
			currentUserTextureView.translationY = -AndroidUtilities.dp(4f) * (1f / scaleY) * progressToCameraMini
			currentUserTextureView.setRoundCorners(AndroidUtilities.dp(8f) * (1f / scaleY) * progressToCameraMini)
			currentUserTextureView.scaleX = 0.4f + 0.6f * (1f - progressToCameraMini)
			currentUserTextureView.scaleY = 0.4f + 0.6f * (1f - progressToCameraMini)
			currentUserTextureView.alpha = min(1f, 1f - progressToCameraMini)

			super.dispatchDraw(canvas)
		}

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (expandedAnimationInProgress || switchingToPip || instance == null) {
				return false
			}

			AndroidUtilities.cancelRunOnUIThread(collapseRunnable)

			val x = event.rawX
			val y = event.rawY
			val parent = parent

			when (event.action) {
				MotionEvent.ACTION_DOWN -> {
					startX = x
					startY = y
					startTime = System.currentTimeMillis()

					//  animate().scaleY(1.05f).scaleX(1.05f).setDuration(150).start();

					moveToBoundsAnimator?.cancel()
				}

				MotionEvent.ACTION_MOVE -> {
					var dx = x - startX
					var dy = y - startY

					if (!moving && dx * dx + dy * dy > touchSlop * touchSlop) {
						parent?.requestDisallowInterceptTouchEvent(true)

						moving = true
						startX = x
						startY = y

						dx = 0f
						dy = 0f
					}

					if (moving) {
						windowLayoutParams?.let {
							it.x += dx.toInt()
							it.y += dy.toInt()
						}

						startX = x
						startY = y

						if (windowView.parent != null) {
							windowManager?.updateViewLayout(windowView, windowLayoutParams)
						}
					}
				}

				MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
					//     animate().scaleX(1f).scaleY(1f).start();

					moveToBoundsAnimator?.cancel()

					if (event.action == MotionEvent.ACTION_UP && !moving && System.currentTimeMillis() - startTime < 150) {
						val context = context

						if (context is LaunchActivity && !ApplicationLoader.mainInterfacePaused) {
							VoIPFragment.show((context as Activity), currentAccount)
						}
						else if (context is LaunchActivity) {
							val intent = Intent(context, LaunchActivity::class.java)
							intent.action = "voip"
							context.startActivity(intent)
						}

						moving = false

						return false
					}

					if (parent != null) {
						parent.requestDisallowInterceptTouchEvent(false)

						val parentWidth = AndroidUtilities.displaySize.x
						val parentHeight = AndroidUtilities.displaySize.y + topInset
						val maxTop = topPadding
						val maxBottom = bottomPadding
						val left = (windowLayoutParams!!.x + floatingView.left).toFloat()
						val right = left + floatingView.measuredWidth
						val top = (windowLayoutParams!!.y + floatingView.top).toFloat()
						val bottom = top + floatingView.measuredHeight

						moveToBoundsAnimator = AnimatorSet()

						if (left < leftPadding) {
							val animator = ValueAnimator.ofFloat(windowLayoutParams!!.x.toFloat(), leftPadding - floatingView.left)
							animator.addUpdateListener(updateXlistener)
							moveToBoundsAnimator?.playTogether(animator)
						}
						else if (right > parentWidth - rightPadding) {
							val animator = ValueAnimator.ofFloat(windowLayoutParams!!.x.toFloat(), parentWidth - floatingView.right - rightPadding)
							animator.addUpdateListener(updateXlistener)
							moveToBoundsAnimator?.playTogether(animator)
						}

						if (top < maxTop) {
							val animator = ValueAnimator.ofFloat(windowLayoutParams!!.y.toFloat(), maxTop - floatingView.top)
							animator.addUpdateListener(updateYlistener)
							moveToBoundsAnimator?.playTogether(animator)
						}
						else if (bottom > parentHeight - maxBottom) {
							val animator = ValueAnimator.ofFloat(windowLayoutParams!!.y.toFloat(), parentHeight - floatingView.measuredHeight - maxBottom)
							animator.addUpdateListener(updateYlistener)
							moveToBoundsAnimator?.playTogether(animator)
						}

						moveToBoundsAnimator?.setDuration(150)?.interpolator = CubicBezierInterpolator.DEFAULT
						moveToBoundsAnimator?.start()
					}

					moving = false

					if (instance?.expanded == true) {
						AndroidUtilities.runOnUIThread(collapseRunnable, 3000)
					}
				}
			}
			return true
		}

		fun getRelativePosition(point: FloatArray) {
			val width = AndroidUtilities.displaySize.x.toFloat()
			val height = AndroidUtilities.displaySize.y.toFloat()
			point[0] = (windowLayoutParams!!.x + floatingView.left - leftPadding) / (width - leftPadding - rightPadding - floatingView.measuredWidth)
			point[1] = (windowLayoutParams!!.y + floatingView.top - topPadding) / (height - topPadding - bottomPadding - floatingView.measuredHeight)
			point[0] = min(1f, max(0f, point[0]))
			point[1] = min(1f, max(0f, point[1]))
		}

		fun expand(expanded: Boolean) {
			AndroidUtilities.cancelRunOnUIThread(collapseRunnable)

			val instance = instance

			if (instance == null || expandedAnimationInProgress || instance.expanded == expanded) {
				return
			}

			instance.expanded = expanded

			val widthNormal = parentWidth * SCALE_NORMAL + 2 * xOffset
			val heightNormal = parentHeight * SCALE_NORMAL + 2 * yOffset
			val widthExpanded = parentWidth * SCALE_EXPANDED + 2 * xOffset
			val heightExpanded = parentHeight * SCALE_EXPANDED + 2 * yOffset

			expandedAnimationInProgress = true

			if (expanded) {
				val layoutParams = createWindowLayoutParams(instance.windowView.context, parentWidth, parentHeight, SCALE_EXPANDED)
				val pipViewExpanded = VoIPPiPView(context, parentWidth, parentHeight, true)

				getRelativePosition(point)

				val cX = point[0]
				val cY = point[1]

				layoutParams.x = (windowLayoutParams!!.x - (widthExpanded - widthNormal) * cX).toInt()
				layoutParams.y = (windowLayoutParams!!.y - (heightExpanded - heightNormal) * cY).toInt()

				windowManager?.addView(pipViewExpanded.windowView, layoutParams)

				pipViewExpanded.windowView.alpha = 1f
				pipViewExpanded.windowLayoutParams = layoutParams
				pipViewExpanded.windowManager = windowManager

				expandedInstance = pipViewExpanded

				swapRender(instance, expandedInstance!!)

				val scale = SCALE_NORMAL / SCALE_EXPANDED * floatingView.scaleX

				pipViewExpanded.floatingView.pivotX = cX * parentWidth * SCALE_EXPANDED
				pipViewExpanded.floatingView.pivotY = cY * parentHeight * SCALE_EXPANDED
				pipViewExpanded.floatingView.scaleX = scale
				pipViewExpanded.floatingView.scaleY = scale

				expandedInstance?.topShadow?.alpha = 0f
				expandedInstance?.closeIcon?.alpha = 0f
				expandedInstance?.enlargeIcon?.alpha = 0f

				AndroidUtilities.runOnUIThread({
					if (expandedInstance == null) {
						return@runOnUIThread
					}

					windowView.alpha = 0f

					try {
						windowManager?.removeView(windowView)
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}

					animate().cancel()

					val animateToScale = 1f

					showUi(true)

					val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

					valueAnimator.addUpdateListener {
						val v = it.animatedValue as Float
						val sc = scale * (1f - v) + animateToScale * v

						pipViewExpanded.floatingView.scaleX = sc
						pipViewExpanded.floatingView.scaleY = sc
						pipViewExpanded.floatingView.invalidate()
						pipViewExpanded.windowView.invalidate()
						pipViewExpanded.floatingView.invalidateOutline()
					}

					valueAnimator.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							super.onAnimationEnd(animation)
							expandedAnimationInProgress = false
						}
					})

					valueAnimator.setDuration(300).interpolator = CubicBezierInterpolator.DEFAULT
					valueAnimator.start()

					expandAnimator = valueAnimator
				}, 64)
			}
			else {
				if (expandedInstance == null) {
					return
				}

				expandedInstance?.floatingView?.getRelativePosition(point)

				val cX = point[0]
				val cY = point[1]

				instance.windowLayoutParams!!.x = (expandedInstance!!.windowLayoutParams!!.x + (widthExpanded - widthNormal) * cX).toInt()
				instance.windowLayoutParams!!.y = (expandedInstance!!.windowLayoutParams!!.y + (heightExpanded - heightNormal) * cY).toInt()

				val scale = SCALE_NORMAL / SCALE_EXPANDED * floatingView.scaleX

				expandedInstance?.floatingView?.pivotX = cX * parentWidth * SCALE_EXPANDED
				expandedInstance?.floatingView?.pivotY = cY * parentHeight * SCALE_EXPANDED

				showUi(false)

				val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

				valueAnimator.addUpdateListener { a: ValueAnimator ->
					val v = a.animatedValue as Float
					val sc = 1f - v + scale * v

					if (expandedInstance != null) {
						expandedInstance?.floatingView?.scaleX = sc
						expandedInstance?.floatingView?.scaleY = sc
						expandedInstance?.floatingView?.invalidate()
						expandedInstance?.floatingView?.invalidateOutline()
						expandedInstance?.windowView?.invalidate()
					}
				}

				valueAnimator.setDuration(300).interpolator = CubicBezierInterpolator.DEFAULT

				valueAnimator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (expandedInstance == null) {
							return
						}

						swapRender(expandedInstance!!, instance)

						instance.windowView.alpha = 1f

						windowManager?.addView(instance.windowView, instance.windowLayoutParams)

						AndroidUtilities.runOnUIThread({
							if (expandedInstance == null) {
								return@runOnUIThread
							}

							expandedInstance?.windowView?.alpha = 0f
							expandedInstance?.finishInternal()

							expandedAnimationInProgress = false

							if (expanded) {
								AndroidUtilities.runOnUIThread(collapseRunnable, 3000)
							}
						}, 64)
					}
				})

				valueAnimator.start()

				expandAnimator = valueAnimator
			}
		}

		private fun showUi(show: Boolean) {
			val expandedInstance = expandedInstance ?: return

			if (show) {
				expandedInstance.topShadow?.alpha = 0f
				expandedInstance.closeIcon?.alpha = 0f
				expandedInstance.enlargeIcon?.alpha = 0f
			}

			expandedInstance.topShadow?.animate()?.alpha(if (show) 1f else 0f)?.setDuration(300)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			expandedInstance.closeIcon?.animate()?.alpha(if (show) 1f else 0f)?.setDuration(300)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			expandedInstance.enlargeIcon?.animate()?.alpha(if (show) 1f else 0f)?.setDuration(300)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		}

		private fun swapRender(from: VoIPPiPView, to: VoIPPiPView) {
			to.currentUserTextureView.setStub(from.currentUserTextureView)
			to.callingUserTextureView.setStub(from.callingUserTextureView)

			from.currentUserTextureView.renderer.release()
			from.callingUserTextureView.renderer.release()

			if (VideoCapturerDevice.eglBase == null) {
				return
			}

			to.currentUserTextureView.renderer.init(VideoCapturerDevice.eglBase.eglBaseContext, null)
			to.callingUserTextureView.renderer.init(VideoCapturerDevice.eglBase.eglBaseContext, null)

			VoIPService.sharedInstance?.setSinks(to.currentUserTextureView.renderer, to.callingUserTextureView.renderer)
		}
	}

	companion object {
		const val ANIMATION_ENTER_TYPE_NONE = 3
		const val ANIMATION_ENTER_TYPE_SCALE = 0
		const val ANIMATION_ENTER_TYPE_TRANSITION = 1
		private const val SCALE_EXPANDED = 0.4f
		private const val SCALE_NORMAL = 0.25f
		private var expandedInstance: VoIPPiPView? = null
		private var instance: VoIPPiPView? = null
		var bottomInset = 0
		var switchingToPip = false
		var topInset = 0

		fun show(activity: Activity, account: Int, parentWidth: Int, parentHeight: Int, animationType: Int) {
			if (instance != null || VideoCapturerDevice.eglBase == null) {
				return
			}

			val windowLayoutParams = createWindowLayoutParams(activity, parentWidth, parentHeight, SCALE_NORMAL)

			instance = VoIPPiPView(activity, parentWidth, parentHeight, false)

			val wm: WindowManager = if (AndroidUtilities.checkInlinePermissions(activity)) {
				ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
			}
			else {
				activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
			}

			instance?.currentAccount = account
			instance?.windowManager = wm
			instance?.windowLayoutParams = windowLayoutParams

			val preferences = ApplicationLoader.applicationContext.getSharedPreferences("voippipconfig", Context.MODE_PRIVATE)

			val x = preferences.getFloat("relativeX", 1f)
			val y = preferences.getFloat("relativeY", 0f)

			instance?.setRelativePosition(x, y)

			NotificationCenter.globalInstance.addObserver(instance!!, NotificationCenter.didEndCall)

			wm.addView(instance!!.windowView, windowLayoutParams)

			instance!!.currentUserTextureView.renderer.init(VideoCapturerDevice.eglBase.eglBaseContext, null)
			instance!!.callingUserTextureView.renderer.init(VideoCapturerDevice.eglBase.eglBaseContext, null)

			if (animationType == ANIMATION_ENTER_TYPE_SCALE) {
				instance!!.windowView.scaleX = 0.5f
				instance!!.windowView.scaleY = 0.5f
				instance!!.windowView.alpha = 0f
				instance!!.windowView.animate().alpha(1f).scaleY(1f).scaleX(1f).start()

				VoIPService.sharedInstance?.setSinks(instance!!.currentUserTextureView.renderer, instance!!.callingUserTextureView.renderer)
			}
			else if (animationType == ANIMATION_ENTER_TYPE_TRANSITION) {
				instance!!.windowView.alpha = 0f

				VoIPService.sharedInstance?.setBackgroundSinks(instance!!.currentUserTextureView.renderer, instance!!.callingUserTextureView.renderer)
			}
		}

		private fun createWindowLayoutParams(context: Context, parentWidth: Int, parentHeight: Int, scale: Float): WindowManager.LayoutParams {
			val windowLayoutParams = WindowManager.LayoutParams()
			val topPadding = (parentHeight * SCALE_EXPANDED * 1.05f - parentHeight * SCALE_EXPANDED).toInt() / 2
			val leftPadding = (parentWidth * SCALE_EXPANDED * 1.05f - parentWidth * SCALE_EXPANDED).toInt() / 2

			windowLayoutParams.height = (parentHeight * scale + 2 * topPadding).toInt()
			windowLayoutParams.width = (parentWidth * scale + 2 * leftPadding).toInt()
			windowLayoutParams.gravity = Gravity.TOP or Gravity.LEFT
			windowLayoutParams.format = PixelFormat.TRANSLUCENT

			if (AndroidUtilities.checkInlinePermissions(context)) {
				if (Build.VERSION.SDK_INT >= 26) {
					windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
				}
				else {
					windowLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
				}
			}
			else {
				windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW
			}

			windowLayoutParams.flags = windowLayoutParams.flags or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
			windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

			return windowLayoutParams
		}

		fun prepareForTransition() {
			if (expandedInstance != null) {
				instance?.expandAnimator?.cancel()
			}
		}

		fun finish() {
			if (switchingToPip) {
				return
			}

			expandedInstance?.finishInternal()
			expandedInstance = null

			instance?.finishInternal()
			instance = null
		}

		fun getInstance(): VoIPPiPView? {
			return expandedInstance ?: instance
		}

		fun isExpanding(): Boolean {
			return instance?.expanded == true
		}
	}
}
