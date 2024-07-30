/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.PowerManager
import android.text.TextUtils
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.TransitionValues
import android.transition.Visibility
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ContactsController
import org.telegram.messenger.Emoji
import org.telegram.messenger.Emoji.EmojiDrawable
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.ImageReceiver.ImageReceiverDelegate
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.messenger.Utilities
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.messenger.voip.EncryptionKeyEmojifier
import org.telegram.messenger.voip.Instance
import org.telegram.messenger.voip.StateListener
import org.telegram.messenger.voip.VideoCapturerDevice
import org.telegram.messenger.voip.VoIPService
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.DarkAlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.BackgroundGradientDrawable
import org.telegram.ui.Components.BackgroundGradientDrawable.Sizes
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.HintView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.voip.AcceptDeclineView
import org.telegram.ui.Components.voip.PrivateVideoPreviewDialog
import org.telegram.ui.Components.voip.VoIPButtonsLayout
import org.telegram.ui.Components.voip.VoIPFloatingLayout
import org.telegram.ui.Components.voip.VoIPHelper.permissionDenied
import org.telegram.ui.Components.voip.VoIPNotificationsLayout
import org.telegram.ui.Components.voip.VoIPOverlayBackground
import org.telegram.ui.Components.voip.VoIPPiPView
import org.telegram.ui.Components.voip.VoIPStatusTextView
import org.telegram.ui.Components.voip.VoIPTextureView
import org.telegram.ui.Components.voip.VoIPToggleButton
import org.telegram.ui.Components.voip.VoIPWindowView
import org.telegram.ui.group.GroupCallActivity
import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon
import org.webrtc.RendererCommon.RendererEvents
import org.webrtc.TextureViewRenderer
import java.io.ByteArrayOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class VoIPFragment(private val currentAccount: Int) : StateListener, NotificationCenterDelegate {
	private var acceptDeclineView: AcceptDeclineView? = null
	private var accessibilityManager: AccessibilityManager? = null
	private var activity: Activity? = null
	private var backIcon: ImageView? = null
	private var bottomButtons = arrayOfNulls<VoIPToggleButton>(4)
	private var bottomShadow: View? = null
	private var buttonsLayout: VoIPButtonsLayout? = null
	private var callingUser = VoIPService.sharedInstance?.getUser()
	private var callingUserMiniFloatingLayout: VoIPFloatingLayout? = null
	private var callingUserMiniTextureRenderer: TextureViewRenderer? = null
	private var callingUserPhotoView: BackupImageView? = null
	private var callingUserPhotoViewMini: BackupImageView? = null
	private var callingUserTextureView: VoIPTextureView? = null
	private var callingUserTitle: TextView? = null
	private var cameraForceExpanded = false
	private var cameraShowingAnimator: Animator? = null
	private var canHideUI = false
	private var canSwitchToPip = false
	private var currentState = VoIPService.sharedInstance?.getCallState() ?: 0
	private var currentUserCameraFloatingLayout: VoIPFloatingLayout? = null
	private var currentUserCameraIsFullscreen = false
	private var currentUserTextureView: VoIPTextureView? = null
	private var deviceIsLocked = false
	private var emojiDrawables = arrayOfNulls<EmojiDrawable>(4)
	private var emojiExpanded = false
	private var emojiLayout: LinearLayout? = null
	private var emojiLoaded = false
	private var emojiViews = arrayOfNulls<ImageView>(4)
	private var enterFromPiP = false
	private var enterTransitionProgress = 0f
	private var fragmentView: ViewGroup? = null
	private var isFinished = false
	private var lastInsets: WindowInsets? = null
	private var notificationsLayout: VoIPNotificationsLayout? = null
	private var overlayBackground: VoIPOverlayBackground? = null
	private var previewDialog: PrivateVideoPreviewDialog? = null
	private var previousState = -1
	private var speakerPhoneIcon: ImageView? = null
	private var statusLayout: LinearLayout? = null
	private var statusLayoutAnimateToOffset = 0
	private var statusTextView: VoIPStatusTextView? = null
	private var switchingToPip = false
	private var tapToVideoTooltip: HintView? = null
	private var topShadow: View? = null
	private var uiVisibilityAlpha = 1f
	private var uiVisibilityAnimator: ValueAnimator? = null
	private var uiVisible = true
	private var windowView: VoIPWindowView? = null
	var animationIndex = -1
	var callingUserIsVideo = false
	var currentUserIsVideo = false
	var emojiRationalTextView: TextView? = null
	var lastContentTapTime: Long = 0
	var overlayBottomPaint = Paint()
	var overlayPaint = Paint()
	var touchSlop = 0f

	private var statusBarAnimatorListener = AnimatorUpdateListener {
		uiVisibilityAlpha = it.animatedValue as Float
		updateSystemBarColors()
	}

	private var fillNavigationBarValue = 0f
	private var fillNavigationBar = false
	private var navigationBarAnimator: ValueAnimator? = null

	private var navigationBarAnimationListener = AnimatorUpdateListener {
		fillNavigationBarValue = it.animatedValue as Float
		updateSystemBarColors()
	}

	var hideUiRunnableWaiting = false

	var hideUIRunnable = Runnable {
		hideUiRunnableWaiting = false

		if (canHideUI && uiVisible && !emojiExpanded) {
			lastContentTapTime = System.currentTimeMillis()
			showUi(false)
			previousState = currentState
			updateViewState()
		}
	}

	private var lockOnScreen = false
	private var screenWasWakeup = false
	private var isVideoCall = false
	private var pinchStartCenterX = 0f
	private var pinchStartCenterY = 0f
	private var pinchStartDistance = 0f
	private var pinchTranslationX = 0f
	private var pinchTranslationY = 0f
	private var isInPinchToZoomTouchMode = false
	private var pinchCenterX = 0f
	private var pinchCenterY = 0f
	private var pointerId1 = 0
	private var pointerId2 = 0
	var pinchScale = 1f
	private var zoomStarted = false
	private var canZoomGesture = false
	var zoomBackAnimator: ValueAnimator? = null

	private fun onBackPressed() {
		if (isFinished || switchingToPip) {
			return
		}

		if (previewDialog != null) {
			previewDialog?.dismiss(false, false)
			return
		}

		if (callingUserIsVideo && currentUserIsVideo && cameraForceExpanded) {
			cameraForceExpanded = false
			currentUserCameraFloatingLayout?.setRelativePosition(callingUserMiniFloatingLayout)
			currentUserCameraIsFullscreen = false
			previousState = currentState
			updateViewState()
			return
		}

		if (emojiExpanded) {
			expandEmoji(false)
		}
		else {
			if (emojiRationalTextView?.visibility != View.GONE) {
				return
			}

			if (canSwitchToPip && !lockOnScreen) {
				if (AndroidUtilities.checkInlinePermissions(activity)) {
					switchToPip()
				}
				else {
					requestInlinePermissions()
				}
			}
			else {
				windowView?.finish()
			}
		}
	}

	private fun setInsets(windowInsets: WindowInsets) {
		lastInsets = windowInsets
		(buttonsLayout?.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = windowInsets.systemWindowInsetBottom
		(acceptDeclineView?.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = windowInsets.systemWindowInsetBottom
		(backIcon?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = windowInsets.systemWindowInsetTop
		(speakerPhoneIcon?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = windowInsets.systemWindowInsetTop
		(topShadow?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = windowInsets.systemWindowInsetTop
		(statusLayout?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = AndroidUtilities.dp(68f) + windowInsets.systemWindowInsetTop
		(emojiLayout?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = AndroidUtilities.dp(17f) + windowInsets.systemWindowInsetTop
		(callingUserPhotoViewMini?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = AndroidUtilities.dp(68f) + windowInsets.systemWindowInsetTop
		(currentUserCameraFloatingLayout?.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = windowInsets.systemWindowInsetBottom
		(callingUserMiniFloatingLayout?.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = windowInsets.systemWindowInsetBottom
		(callingUserTextureView?.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = windowInsets.systemWindowInsetBottom
		(notificationsLayout?.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = windowInsets.systemWindowInsetBottom
		(bottomShadow?.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = windowInsets.systemWindowInsetBottom
		currentUserCameraFloatingLayout?.setInsets(windowInsets)
		callingUserMiniFloatingLayout?.setInsets(windowInsets)
		fragmentView?.requestLayout()
		previewDialog?.setBottomPadding(windowInsets.systemWindowInsetBottom)
	}

	init {
		VoIPService.sharedInstance?.registerStateListener(this)

		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.voipServiceCreated)

		NotificationCenter.globalInstance.let {
			it.addObserver(this, NotificationCenter.emojiLoaded)
			it.addObserver(this, NotificationCenter.closeInCallActivity)
		}
	}

	private fun destroy() {
		val service = VoIPService.sharedInstance
		service?.unregisterStateListener(this)

		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.voipServiceCreated)

		NotificationCenter.globalInstance.let {
			it.removeObserver(this, NotificationCenter.emojiLoaded)
			it.removeObserver(this, NotificationCenter.closeInCallActivity)
		}
	}

	override fun onStateChanged(state: Int) {
		if (currentState != state) {
			previousState = currentState
			currentState = state

			if (windowView != null) {
				updateViewState()
			}
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.voipServiceCreated -> {
				if (currentState == VoIPService.STATE_BUSY && VoIPService.sharedInstance != null) {
					currentUserTextureView?.renderer?.release()
					callingUserTextureView?.renderer?.release()
					callingUserMiniTextureRenderer?.release()
					initRenderers()
					VoIPService.sharedInstance?.registerStateListener(this)
				}
			}

			NotificationCenter.emojiLoaded -> {
				updateKeyView(true)
			}

			NotificationCenter.closeInCallActivity -> {
				windowView?.finish()
			}
		}
	}

	override fun onSignalBarsCountChanged(count: Int) {
		statusTextView?.setSignalBarCount(count)
	}

	override fun onAudioSettingsChanged() {
		updateButtons(true)
	}

	override fun onMediaStateUpdated(audioState: Int, videoState: Int) {
		previousState = currentState

		if (videoState == Instance.VIDEO_STATE_ACTIVE && !isVideoCall) {
			isVideoCall = true
		}

		updateViewState()
	}

	override fun onCameraSwitch(isFrontFace: Boolean) {
		previousState = currentState
		updateViewState()
	}

	override fun onVideoAvailableChange(isAvailable: Boolean) {
		previousState = currentState

		if (isAvailable && !isVideoCall) {
			isVideoCall = true
		}

		updateViewState()
	}

	override fun onScreenOnChange(screenOn: Boolean) {
		// unused
	}

	fun createView(context: Context): View {
		touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
		accessibilityManager = ContextCompat.getSystemService(context, AccessibilityManager::class.java)

		val frameLayout: FrameLayout = object : FrameLayout(context) {
			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)

				lastInsets?.let {
					canvas.drawRect(0f, 0f, measuredWidth.toFloat(), it.systemWindowInsetTop.toFloat(), overlayPaint)
				}

				lastInsets?.let {
					canvas.drawRect(0f, (measuredHeight - it.systemWindowInsetBottom).toFloat(), measuredWidth.toFloat(), measuredHeight.toFloat(), overlayBottomPaint)
				}
			}

			var pressedX = 0f
			var pressedY = 0f
			var check = false
			var pressedTime: Long = 0

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(ev: MotionEvent): Boolean {
				if (!canZoomGesture && !isInPinchToZoomTouchMode && !zoomStarted && ev.actionMasked != MotionEvent.ACTION_DOWN) {
					finishZoom()
					return false
				}

				if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
					canZoomGesture = false
					isInPinchToZoomTouchMode = false
					zoomStarted = false
				}



				if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
					if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
						fullscreenTextureView?.let {
							AndroidUtilities.rectTmp.set(it.x, it.y, it.x + it.measuredWidth, it.y + it.measuredHeight)
							AndroidUtilities.rectTmp.inset((it.measuredHeight * it.scaleTextureToFill - it.measuredHeight) / 2, (it.measuredWidth * it.scaleTextureToFill - it.measuredWidth) / 2)
						}

						if (!GroupCallActivity.isLandscapeMode) {
							AndroidUtilities.rectTmp.top = max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight().toFloat())
							AndroidUtilities.rectTmp.bottom = min(AndroidUtilities.rectTmp.bottom, ((fullscreenTextureView?.measuredHeight ?: 0) - AndroidUtilities.dp(90f)).toFloat())
						}
						else {
							AndroidUtilities.rectTmp.top = max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight().toFloat())
							AndroidUtilities.rectTmp.right = min(AndroidUtilities.rectTmp.right, ((fullscreenTextureView?.measuredWidth ?: 0) - AndroidUtilities.dp(90f)).toFloat())
						}

						canZoomGesture = AndroidUtilities.rectTmp.contains(ev.x, ev.y)

						if (!canZoomGesture) {
							finishZoom()
						}
					}

					if (canZoomGesture && !isInPinchToZoomTouchMode && ev.pointerCount == 2) {
						pinchStartDistance = hypot((ev.getX(1) - ev.getX(0)).toDouble(), (ev.getY(1) - ev.getY(0)).toDouble()).toFloat()
						pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f
						pinchStartCenterX = pinchCenterX
						pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f
						pinchStartCenterY = pinchCenterY
						pinchScale = 1f
						pointerId1 = ev.getPointerId(0)
						pointerId2 = ev.getPointerId(1)
						isInPinchToZoomTouchMode = true
					}
				}
				else if (ev.actionMasked == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
					var index1 = -1
					var index2 = -1

					for (i in 0 until ev.pointerCount) {
						if (pointerId1 == ev.getPointerId(i)) {
							index1 = i
						}
						if (pointerId2 == ev.getPointerId(i)) {
							index2 = i
						}
					}

					if (index1 == -1 || index2 == -1) {
						parent.requestDisallowInterceptTouchEvent(false)
						finishZoom()
					}
					else {
						pinchScale = hypot((ev.getX(index2) - ev.getX(index1)).toDouble(), (ev.getY(index2) - ev.getY(index1)).toDouble()).toFloat() / pinchStartDistance

						if (pinchScale > 1.005f && !zoomStarted) {
							pinchStartDistance = hypot((ev.getX(index2) - ev.getX(index1)).toDouble(), (ev.getY(index2) - ev.getY(index1)).toDouble()).toFloat()
							pinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f
							pinchStartCenterX = pinchCenterX
							pinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f
							pinchStartCenterY = pinchCenterY
							pinchScale = 1f
							pinchTranslationX = 0f
							pinchTranslationY = 0f
							parent.requestDisallowInterceptTouchEvent(true)
							zoomStarted = true
							isInPinchToZoomTouchMode = true
						}

						val newPinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f
						val newPinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f
						val moveDx = pinchStartCenterX - newPinchCenterX
						val moveDy = pinchStartCenterY - newPinchCenterY
						pinchTranslationX = -moveDx / pinchScale
						pinchTranslationY = -moveDy / pinchScale
						invalidate()
					}
				}
				else if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev) || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
					parent.requestDisallowInterceptTouchEvent(false)
					finishZoom()
				}

				fragmentView?.invalidate()

				when (ev.action) {
					MotionEvent.ACTION_DOWN -> {
						pressedX = ev.x
						pressedY = ev.y
						check = true
						pressedTime = System.currentTimeMillis()
					}

					MotionEvent.ACTION_CANCEL -> {
						check = false
					}

					MotionEvent.ACTION_UP -> {
						if (check) {
							val dx = ev.x - pressedX
							val dy = ev.y - pressedY
							val currentTime = System.currentTimeMillis()

							if (dx * dx + dy * dy < touchSlop * touchSlop && currentTime - pressedTime < 300 && currentTime - lastContentTapTime > 300) {
								lastContentTapTime = System.currentTimeMillis()

								if (emojiExpanded) {
									expandEmoji(false)
								}
								else if (canHideUI) {
									showUi(!uiVisible)
									previousState = currentState
									updateViewState()
								}
							}

							check = false
						}
					}
				}

				return canZoomGesture || check
			}

			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				if (child === callingUserPhotoView && (currentUserIsVideo || callingUserIsVideo)) {
					return false
				}

				if (child === callingUserPhotoView || child === callingUserTextureView || child === currentUserCameraFloatingLayout && currentUserCameraIsFullscreen) {
					if (zoomStarted || zoomBackAnimator != null) {
						canvas.save()
						canvas.scale(pinchScale, pinchScale, pinchCenterX, pinchCenterY)
						canvas.translate(pinchTranslationX, pinchTranslationY)
						val b = super.drawChild(canvas, child, drawingTime)
						canvas.restore()
						return b
					}
				}

				return super.drawChild(canvas, child, drawingTime)
			}
		}

		frameLayout.clipToPadding = false
		frameLayout.clipChildren = false
		frameLayout.setBackgroundColor(-0x1000000)

		updateSystemBarColors()

		fragmentView = frameLayout

		frameLayout.fitsSystemWindows = true

		callingUserPhotoView = object : BackupImageView(context) {
			var blackoutColor = ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.3f).toInt())

			override fun onDraw(canvas: Canvas) {
				super.onDraw(canvas)
				canvas.drawColor(blackoutColor)
			}
		}

		callingUserTextureView = VoIPTextureView(context, false, true, false, false)
		callingUserTextureView?.renderer?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
		callingUserTextureView?.renderer?.setEnableHardwareScaler(true)
		callingUserTextureView?.renderer?.setRotateTextureWithScreen(true)
		callingUserTextureView?.scaleType = VoIPTextureView.SCALE_TYPE_FIT

		frameLayout.addView(callingUserPhotoView)
		frameLayout.addView(callingUserTextureView)

		val gradientDrawable = BackgroundGradientDrawable(GradientDrawable.Orientation.TR_BL, intArrayOf(-0x94d270, -0x19481a, -0xe73d13, -0xe73d13, -0xe73d13))
		val sizes = Sizes.ofDeviceScreen(Sizes.Orientation.PORTRAIT)

		gradientDrawable.startDithering(sizes, object : BackgroundGradientDrawable.ListenerAdapter() {
			override fun onAllSizesReady() {
				callingUserPhotoView?.invalidate()
			}
		})

		overlayBackground = VoIPOverlayBackground(context)
		overlayBackground?.gone()

		callingUserPhotoView?.imageReceiver?.setDelegate(object : ImageReceiverDelegate {
			override fun onAnimationReady(imageReceiver: ImageReceiver) {
				// unused
			}

			override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
				imageReceiver.bitmapSafe?.let {
					overlayBackground?.setBackground(it)
				}
			}
		})

		val backgroundDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.call_background, null)

		callingUserPhotoView?.setImage(ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_BIG), null, backgroundDrawable, callingUser)

		currentUserCameraFloatingLayout = VoIPFloatingLayout(context)

		currentUserCameraFloatingLayout?.setDelegate { progress, value ->
			currentUserTextureView?.setScreenshareMiniProgress(progress, value)
		}

		currentUserCameraFloatingLayout?.setRelativePosition(1f, 1f)

		currentUserCameraIsFullscreen = true

		currentUserTextureView = VoIPTextureView(context, true, false)
		currentUserTextureView?.renderer?.setIsCamera(true)
		currentUserTextureView?.renderer?.setUseCameraRotation(true)

		currentUserCameraFloatingLayout?.setOnTapListener {
			if (currentUserIsVideo && callingUserIsVideo && System.currentTimeMillis() - lastContentTapTime > 500) {
				AndroidUtilities.cancelRunOnUIThread(hideUIRunnable)
				hideUiRunnableWaiting = false
				lastContentTapTime = System.currentTimeMillis()
				callingUserMiniFloatingLayout?.setRelativePosition(currentUserCameraFloatingLayout)
				currentUserCameraIsFullscreen = true
				cameraForceExpanded = true
				previousState = currentState
				updateViewState()
			}
		}

		currentUserTextureView?.renderer?.setMirror(true)

		currentUserCameraFloatingLayout?.addView(currentUserTextureView)

		callingUserMiniFloatingLayout = VoIPFloatingLayout(context)
		callingUserMiniFloatingLayout?.alwaysFloating = true
		callingUserMiniFloatingLayout?.setFloatingMode(true, false)

		callingUserMiniTextureRenderer = TextureViewRenderer(context)
		callingUserMiniTextureRenderer?.setEnableHardwareScaler(true)
		callingUserMiniTextureRenderer?.setIsCamera(false)
		callingUserMiniTextureRenderer?.setFpsReduction(30f)
		callingUserMiniTextureRenderer?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)

		val backgroundView = View(context)
		backgroundView.setBackgroundColor(-0xe4e0dd)

		callingUserMiniFloatingLayout?.addView(backgroundView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		callingUserMiniFloatingLayout?.addView(callingUserMiniTextureRenderer, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))

		callingUserMiniFloatingLayout?.setOnTapListener {
			if (cameraForceExpanded && System.currentTimeMillis() - lastContentTapTime > 500) {
				AndroidUtilities.cancelRunOnUIThread(hideUIRunnable)
				hideUiRunnableWaiting = false
				lastContentTapTime = System.currentTimeMillis()
				currentUserCameraFloatingLayout?.setRelativePosition(callingUserMiniFloatingLayout)
				currentUserCameraIsFullscreen = false
				cameraForceExpanded = false
				previousState = currentState
				updateViewState()
			}
		}

		callingUserMiniFloatingLayout?.gone()

		frameLayout.addView(currentUserCameraFloatingLayout, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat()))
		frameLayout.addView(callingUserMiniFloatingLayout)
		frameLayout.addView(overlayBackground)

		bottomShadow = View(context)
		bottomShadow?.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.5f).toInt())))

		frameLayout.addView(bottomShadow, createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.BOTTOM))

		topShadow = View(context)
		topShadow?.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.4f).toInt()), Color.TRANSPARENT))

		frameLayout.addView(topShadow, createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.TOP))

		emojiLayout = object : LinearLayout(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)
				info.isVisibleToUser = emojiLoaded
			}
		}

		emojiLayout?.orientation = LinearLayout.HORIZONTAL
		emojiLayout?.setPadding(0, 0, 0, AndroidUtilities.dp(30f))
		emojiLayout?.clipToPadding = false

		emojiLayout?.setOnClickListener {
			if (System.currentTimeMillis() - lastContentTapTime < 500) {
				return@setOnClickListener
			}

			lastContentTapTime = System.currentTimeMillis()

			if (emojiLoaded) {
				expandEmoji(!emojiExpanded)
			}
		}

		emojiRationalTextView = TextView(context)
		emojiRationalTextView?.text = LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, UserObject.getFirstName(callingUser))
		emojiRationalTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		emojiRationalTextView?.setTextColor(Color.WHITE)
		emojiRationalTextView?.gravity = Gravity.CENTER
		emojiRationalTextView?.gone()

		for (i in 0..3) {
			emojiViews[i] = ImageView(context)
			emojiViews[i]?.scaleType = ImageView.ScaleType.FIT_XY
			emojiLayout?.addView(emojiViews[i], createLinear(22, 22, (if (i == 0) 0 else 4).toFloat(), 0f, 0f, 0f))
		}

		statusLayout = object : LinearLayout(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)
				val service = VoIPService.sharedInstance
				val callingUserTitleText = callingUserTitle?.text

				if (service != null && !callingUserTitleText.isNullOrEmpty()) {
					val builder = StringBuilder(callingUserTitleText)
					builder.append(", ")

					if (service.privateCall?.video == true) {
						builder.append(service.getString(R.string.VoipInVideoCallBranding))
					}
					else {
						builder.append(service.getString(R.string.VoipInCallBranding))
					}

					val callDuration = service.getCallDuration()

					if (callDuration > 0) {
						builder.append(", ")
						builder.append(LocaleController.formatDuration((callDuration / 1000).toInt()))
					}

					info.text = builder
				}
			}
		}

		statusLayout?.orientation = LinearLayout.VERTICAL
		statusLayout?.isFocusable = true
		statusLayout?.isFocusableInTouchMode = true

		callingUserPhotoViewMini = BackupImageView(context)
		callingUserPhotoViewMini?.setImage(ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_SMALL), null, Theme.createCircleDrawable(AndroidUtilities.dp(135f), -0x1000000), callingUser)
		callingUserPhotoViewMini?.setRoundRadius(AndroidUtilities.dp(135f) / 2)
		callingUserPhotoViewMini?.gone()

		callingUserTitle = TextView(context)
		callingUserTitle?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
		callingUserTitle?.text = ContactsController.formatName(callingUser?.first_name, callingUser?.last_name)
		callingUserTitle?.setShadowLayer(AndroidUtilities.dp(3f).toFloat(), 0f, AndroidUtilities.dp(0.6666667f).toFloat(), 0x4C000000)
		callingUserTitle?.setTextColor(Color.WHITE)
		callingUserTitle?.gravity = Gravity.CENTER_HORIZONTAL
		callingUserTitle?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

		statusLayout?.addView(callingUserTitle, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6))

		statusTextView = VoIPStatusTextView(context).also {
			ViewCompat.setImportantForAccessibility(it, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
		}

		statusLayout?.addView(statusTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6))
		statusLayout?.clipChildren = false
		statusLayout?.clipToPadding = false
		statusLayout?.setPadding(0, 0, 0, AndroidUtilities.dp(15f))

		val encryptedTextView = TextView(context)
		encryptedTextView.text = context.getString(R.string.e2e_encrypted)
		encryptedTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		encryptedTextView.setTextColor(Color.WHITE)
		encryptedTextView.gravity = Gravity.CENTER

		statusLayout?.addView(encryptedTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6))

		frameLayout.addView(callingUserPhotoViewMini, createFrame(135, 135f, Gravity.CENTER_HORIZONTAL, 0f, 68f, 0f, 0f))
		frameLayout.addView(statusLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 0f, 68f, 0f, 0f))
		frameLayout.addView(emojiLayout, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_HORIZONTAL, 0f, 17f, 0f, 0f))
		frameLayout.addView(emojiRationalTextView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 24f, 32f, 24f, 0f))

		emojiLayout?.visibility = View.GONE

		emojiRationalTextView?.gone()

		buttonsLayout = VoIPButtonsLayout(context)

		for (i in 0..3) {
			bottomButtons[i] = VoIPToggleButton(context, 62f).also {
				buttonsLayout?.addView(it)
			}
		}

		acceptDeclineView = AcceptDeclineView(context)

		acceptDeclineView?.setListener(object : AcceptDeclineView.Listener {
			override fun onAccept() {
				if (currentState == VoIPService.STATE_BUSY) {
					val intent = Intent(activity, VoIPService::class.java)
					intent.putExtra("user_id", callingUser?.id ?: 0L)
					intent.putExtra("is_outgoing", true)
					intent.putExtra("start_incall_activity", false)
					intent.putExtra("video_call", isVideoCall)
					intent.putExtra("can_video_call", isVideoCall)
					intent.putExtra("account", currentAccount)

					try {
						activity?.startService(intent)
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}
				else {
					if (activity?.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
						activity?.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
					}
					else {
						VoIPService.sharedInstance?.acceptIncomingCall()

						if (currentUserIsVideo) {
							VoIPService.sharedInstance?.requestVideoCall(false)
						}
					}
				}
			}

			override fun onDecline() {
				if (currentState == VoIPService.STATE_BUSY) {
					windowView?.finish()
				}
				else {
					VoIPService.sharedInstance?.declineIncomingCall()
				}
			}
		})

		acceptDeclineView?.setScreenWasWakeup(screenWasWakeup)

		frameLayout.addView(buttonsLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM))
		frameLayout.addView(acceptDeclineView, createFrame(LayoutHelper.MATCH_PARENT, 186, Gravity.BOTTOM))

		backIcon = ImageView(context)
		backIcon?.background = Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.3f).toInt()))
		backIcon?.setImageResource(R.drawable.ic_back_arrow)
		backIcon?.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
		backIcon?.setPadding(AndroidUtilities.dp(16f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
		backIcon?.contentDescription = context.getString(R.string.Back)

		frameLayout.addView(backIcon, createFrame(56, 56, Gravity.TOP or Gravity.LEFT))

		speakerPhoneIcon = @SuppressLint("AppCompatCustomView") object : ImageView(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)

				info.className = ToggleButton::class.java.name
				info.isCheckable = true
				info.isChecked = VoIPService.sharedInstance?.isSpeakerphoneOn() == true
			}
		}

		speakerPhoneIcon?.contentDescription = context.getString(R.string.VoipSpeaker)
		speakerPhoneIcon?.background = Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.3f).toInt()))
		speakerPhoneIcon?.setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f), AndroidUtilities.dp(12f))

		frameLayout.addView(speakerPhoneIcon, createFrame(56, 56, Gravity.TOP or Gravity.RIGHT))

		//MARK: If you need to, uncomment the button listener
//		speakerPhoneIcon?.setOnClickListener {
//			if (speakerPhoneIcon?.tag == null) {
//				return@setOnClickListener
//			}
//
//			VoIPService.sharedInstance?.toggleSpeakerphoneOrShowRouteSheet(activity, false)
//		}

		backIcon?.setOnClickListener {
			if (!lockOnScreen) {
				onBackPressed()
			}
		}

		if (windowView?.isLockOnScreen == true) {
			backIcon?.gone()
		}

		notificationsLayout = VoIPNotificationsLayout(context)
		notificationsLayout?.gravity = Gravity.BOTTOM

		notificationsLayout?.setOnViewsUpdated {
			previousState = currentState
			updateViewState()
		}

		frameLayout.addView(notificationsLayout, createFrame(LayoutHelper.MATCH_PARENT, 200f, Gravity.BOTTOM, 16f, 0f, 16f, 0f))

		tapToVideoTooltip = HintView(context, 4)
		tapToVideoTooltip?.setText(context.getString(R.string.TapToTurnCamera))

		frameLayout.addView(tapToVideoTooltip, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 19f, 0f, 19f, 8f))

		tapToVideoTooltip?.setBottomOffset(AndroidUtilities.dp(4f))
		tapToVideoTooltip?.gone()

		updateViewState()

		val service = VoIPService.sharedInstance

		if (service != null) {
			if (!isVideoCall) {
				isVideoCall = service.privateCall?.video == true
			}

			initRenderers()
		}

		return frameLayout
	}

	private fun checkPointerIds(ev: MotionEvent): Boolean {
		if (ev.pointerCount < 2) {
			return false
		}

		if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
			return true
		}

		return pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)
	}

	private val fullscreenTextureView: VoIPTextureView?
		get() = if (callingUserIsVideo) callingUserTextureView else currentUserTextureView

	private fun finishZoom() {
		if (zoomStarted) {
			zoomStarted = false
			zoomBackAnimator = ValueAnimator.ofFloat(1f, 0f)

			val fromScale = pinchScale
			val fromTranslateX = pinchTranslationX
			val fromTranslateY = pinchTranslationY

			zoomBackAnimator?.addUpdateListener {
				val v = it.animatedValue as Float
				pinchScale = fromScale * v + 1f * (1f - v)
				pinchTranslationX = fromTranslateX * v
				pinchTranslationY = fromTranslateY * v
				fragmentView?.invalidate()
			}

			zoomBackAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					zoomBackAnimator = null
					pinchScale = 1f
					pinchTranslationX = 0f
					pinchTranslationY = 0f
					fragmentView?.invalidate()
				}
			})

			zoomBackAnimator?.duration = GroupCallActivity.TRANSITION_DURATION
			zoomBackAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
			zoomBackAnimator?.start()
		}

		canZoomGesture = false
		isInPinchToZoomTouchMode = false
	}

	private fun initRenderers() {
		currentUserTextureView?.renderer?.init(VideoCapturerDevice.getEglBase().eglBaseContext, object : RendererEvents {
			override fun onFirstFrameRendered() {
				AndroidUtilities.runOnUIThread {
					updateViewState()
				}
			}

			override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
				// unused
			}
		})

		callingUserTextureView?.renderer?.init(VideoCapturerDevice.getEglBase().eglBaseContext, object : RendererEvents {
			override fun onFirstFrameRendered() {
				AndroidUtilities.runOnUIThread {
					updateViewState()
				}
			}

			override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
				// unused
			}
		}, EglBase.CONFIG_PLAIN, GlRectDrawer())

		callingUserMiniTextureRenderer?.init(VideoCapturerDevice.getEglBase().eglBaseContext, null)
	}

	fun switchToPip() {
		if (isFinished || !AndroidUtilities.checkInlinePermissions(activity) || instance == null) {
			return
		}

		isFinished = true

		if (VoIPService.sharedInstance != null) {
			var h = instance?.windowView?.measuredHeight ?: 0

			instance?.lastInsets?.let {
				h -= it.systemWindowInsetBottom
			}

			instance?.let {
				it.activity?.let { activity ->
					VoIPPiPView.show(activity, it.currentAccount, it.windowView?.measuredWidth ?: 0, h, VoIPPiPView.ANIMATION_ENTER_TYPE_TRANSITION)
				}
			}

			instance?.lastInsets?.let {
				VoIPPiPView.topInset = it.systemWindowInsetTop
				VoIPPiPView.bottomInset = it.systemWindowInsetBottom
			}
		}

		if (VoIPPiPView.getInstance() == null) {
			return
		}

		speakerPhoneIcon?.animate()?.alpha(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		backIcon?.animate()?.alpha(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		emojiLayout?.animate()?.alpha(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		statusLayout?.animate()?.alpha(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		buttonsLayout?.animate()?.alpha(0f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		bottomShadow?.animate()?.alpha(0f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		topShadow?.animate()?.alpha(0f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		callingUserMiniFloatingLayout?.animate()?.alpha(0f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		notificationsLayout?.animate()?.alpha(0f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()

		VoIPPiPView.switchingToPip = true

		switchingToPip = true

		val animator = createPiPTransition(false)

		animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null)

		animator.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				VoIPPiPView.getInstance()?.windowView?.alpha = 1f

				AndroidUtilities.runOnUIThread({
					NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex)
					VoIPPiPView.getInstance()?.onTransitionEnd()
					currentUserCameraFloatingLayout?.setCornerRadius(-1f)
					callingUserTextureView?.renderer?.release()
					currentUserTextureView?.renderer?.release()
					callingUserMiniTextureRenderer?.release()
					destroy()
					windowView?.finishImmediate()
					VoIPPiPView.switchingToPip = false
					switchingToPip = false
					instance = null
				}, 200)
			}
		})

		animator.duration = 350
		animator.interpolator = CubicBezierInterpolator.DEFAULT
		animator.start()
	}

	fun startTransitionFromPiP() {
		enterFromPiP = true

		val service = VoIPService.sharedInstance

		if (service != null && service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE) {
			callingUserTextureView?.setStub(VoIPPiPView.getInstance()?.callingUserTextureView)
			currentUserTextureView?.setStub(VoIPPiPView.getInstance()?.currentUserTextureView)
		}

		windowView?.alpha = 0f

		updateViewState()

		switchingToPip = true

		VoIPPiPView.switchingToPip = true
		VoIPPiPView.prepareForTransition()

		animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null)

		AndroidUtilities.runOnUIThread({
			windowView?.alpha = 1f

			val animator = createPiPTransition(true)

			backIcon?.alpha = 0f
			emojiLayout?.alpha = 0f
			statusLayout?.alpha = 0f
			buttonsLayout?.alpha = 0f
			bottomShadow?.alpha = 0f
			topShadow?.alpha = 0f
			speakerPhoneIcon?.alpha = 0f
			notificationsLayout?.alpha = 0f
			callingUserPhotoView?.alpha = 0f
			currentUserCameraFloatingLayout?.switchingToPip = true

			AndroidUtilities.runOnUIThread({
				VoIPPiPView.switchingToPip = false
				VoIPPiPView.finish()

				speakerPhoneIcon?.animate()?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				backIcon?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				emojiLayout?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				statusLayout?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				buttonsLayout?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				bottomShadow?.animate()?.alpha(1f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				topShadow?.animate()?.alpha(1f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				notificationsLayout?.animate()?.alpha(1f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
				callingUserPhotoView?.animate()?.alpha(1f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()

				animator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex)
						currentUserCameraFloatingLayout?.setCornerRadius(-1f)
						switchingToPip = false
						currentUserCameraFloatingLayout?.switchingToPip = false
						previousState = currentState
						updateViewState()
					}
				})

				animator.duration = 350
				animator.interpolator = CubicBezierInterpolator.DEFAULT
				animator.start()
			}, 32)
		}, 32)
	}

	private fun createPiPTransition(enter: Boolean): Animator {
		currentUserCameraFloatingLayout?.animate()?.cancel()

		val pipView = VoIPPiPView.getInstance()
		val toX = ((pipView?.windowLayoutParams?.x ?: 0) + (pipView?.xOffset ?: 0)).toFloat()
		val toY = ((pipView?.windowLayoutParams?.y ?: 0) + (pipView?.yOffset ?: 0)).toFloat()
		val cameraFromX = currentUserCameraFloatingLayout?.x ?: 0f
		val cameraFromY = currentUserCameraFloatingLayout?.y ?: 0f
		val cameraFromScale = currentUserCameraFloatingLayout?.scaleX ?: 1f
		var animateCamera = true
		val callingUserFromX = 0f
		val callingUserFromY = 0f
		val callingUserFromScale = 1f
		val callingUserToScale: Float
		val cameraToScale: Float
		val cameraToX: Float
		val cameraToY: Float
		val pipScale = if (VoIPPiPView.isExpanding()) 0.4f else 0.25f

		callingUserToScale = pipScale

		val callingUserToX = toX - ((callingUserTextureView?.measuredWidth ?: 0) - (callingUserTextureView?.measuredWidth ?: 0) * callingUserToScale) / 2f
		val callingUserToY = toY - ((callingUserTextureView?.measuredHeight ?: 0) - (callingUserTextureView?.measuredHeight ?: 0) * callingUserToScale) / 2f

		if (callingUserIsVideo) {
			val currentW = currentUserCameraFloatingLayout?.measuredWidth ?: 0

			if (currentUserIsVideo && currentW != 0) {
				cameraToScale = (windowView?.measuredWidth ?: 0) / currentW.toFloat() * pipScale * 0.4f
				cameraToX = toX - ((currentUserCameraFloatingLayout?.measuredWidth ?: 0) - (currentUserCameraFloatingLayout?.measuredWidth ?: 0) * cameraToScale) / 2f + (pipView?.parentWidth ?: 0) * pipScale - (pipView?.parentWidth ?: 0) * pipScale * 0.4f - AndroidUtilities.dp(4f)
				cameraToY = toY - ((currentUserCameraFloatingLayout?.measuredHeight ?: 0) - (currentUserCameraFloatingLayout?.measuredHeight ?: 0) * cameraToScale) / 2f + (pipView?.parentHeight ?: 0) * pipScale - (pipView?.parentHeight ?: 0) * pipScale * 0.4f - AndroidUtilities.dp(4f)
			}
			else {
				cameraToScale = 0f
				cameraToX = 1f
				cameraToY = 1f
				animateCamera = false
			}
		}
		else {
			cameraToScale = pipScale
			cameraToX = toX - ((currentUserCameraFloatingLayout?.measuredWidth ?: 0) - (currentUserCameraFloatingLayout?.measuredWidth ?: 0) * cameraToScale) / 2f
			cameraToY = toY - ((currentUserCameraFloatingLayout?.measuredHeight ?: 0) - (currentUserCameraFloatingLayout?.measuredHeight ?: 0) * cameraToScale) / 2f
		}

		val cameraCornerRadiusFrom = if (callingUserIsVideo) AndroidUtilities.dp(4f).toFloat() else 0f
		val cameraCornerRadiusTo = AndroidUtilities.dp(4f) * 1f / cameraToScale
		var fromCameraAlpha = 1f
		val toCameraAlpha = 1f

		if (callingUserIsVideo) {
			fromCameraAlpha = if (VoIPPiPView.isExpanding()) 1f else 0f
		}

		if (enter) {
			if (animateCamera) {
				currentUserCameraFloatingLayout?.scaleX = cameraToScale
				currentUserCameraFloatingLayout?.scaleY = cameraToScale
				currentUserCameraFloatingLayout?.translationX = cameraToX
				currentUserCameraFloatingLayout?.translationY = cameraToY
				currentUserCameraFloatingLayout?.setCornerRadius(cameraCornerRadiusTo)
				currentUserCameraFloatingLayout?.alpha = fromCameraAlpha
			}

			callingUserTextureView?.scaleX = callingUserToScale
			callingUserTextureView?.scaleY = callingUserToScale
			callingUserTextureView?.translationX = callingUserToX
			callingUserTextureView?.translationY = callingUserToY
			callingUserTextureView?.setRoundCorners(AndroidUtilities.dp(6f) * 1f / callingUserToScale)

			callingUserPhotoView?.alpha = 0f
			callingUserPhotoView?.scaleX = callingUserToScale
			callingUserPhotoView?.scaleY = callingUserToScale
			callingUserPhotoView?.translationX = callingUserToX
			callingUserPhotoView?.translationY = callingUserToY
		}

		val animator = ValueAnimator.ofFloat(if (enter) 1f else 0f, if (enter) 0f else 1f)

		enterTransitionProgress = if (enter) 0f else 1f

		updateSystemBarColors()

		val finalAnimateCamera = animateCamera
		val finalFromCameraAlpha = fromCameraAlpha

		animator.addUpdateListener {
			val v = it.animatedValue as Float
			enterTransitionProgress = 1f - v
			updateSystemBarColors()

			if (finalAnimateCamera) {
				val cameraScale = cameraFromScale * (1f - v) + cameraToScale * v
				currentUserCameraFloatingLayout?.scaleX = cameraScale
				currentUserCameraFloatingLayout?.scaleY = cameraScale
				currentUserCameraFloatingLayout?.translationX = cameraFromX * (1f - v) + cameraToX * v
				currentUserCameraFloatingLayout?.translationY = cameraFromY * (1f - v) + cameraToY * v
				currentUserCameraFloatingLayout?.setCornerRadius(cameraCornerRadiusFrom * (1f - v) + cameraCornerRadiusTo * v)
				currentUserCameraFloatingLayout?.alpha = toCameraAlpha * (1f - v) + finalFromCameraAlpha * v
			}

			val callingUserScale = callingUserFromScale * (1f - v) + callingUserToScale * v

			callingUserTextureView?.scaleX = callingUserScale
			callingUserTextureView?.scaleY = callingUserScale

			val tx = callingUserFromX * (1f - v) + callingUserToX * v
			val ty = callingUserFromY * (1f - v) + callingUserToY * v

			callingUserTextureView?.translationX = tx
			callingUserTextureView?.translationY = ty
			callingUserTextureView?.setRoundCorners(v * AndroidUtilities.dp(4f) * 1 / callingUserScale)

			if (currentUserCameraFloatingLayout?.measuredAsFloatingMode != true) {
				currentUserTextureView?.setScreenshareMiniProgress(v, false)
			}

			callingUserPhotoView?.scaleX = callingUserScale
			callingUserPhotoView?.scaleY = callingUserScale
			callingUserPhotoView?.translationX = tx
			callingUserPhotoView?.translationY = ty
			callingUserPhotoView?.alpha = 1f - v
		}

		return animator
	}

	private fun expandEmoji(expanded: Boolean) {
		if (!emojiLoaded || emojiExpanded == expanded || !uiVisible) {
			return
		}

		emojiExpanded = expanded

		if (expanded) {
			AndroidUtilities.runOnUIThread(hideUIRunnable)

			hideUiRunnableWaiting = false

			val s1 = emojiLayout?.measuredWidth?.toFloat() ?: 1f
			val s2 = ((windowView?.measuredWidth ?: 0) - AndroidUtilities.dp(128f)).toFloat()

			val scale = s2 / s1

			emojiLayout?.animate()?.scaleX(scale)?.scaleY(scale)?.translationY((windowView?.height ?: 0) / 2f - (emojiLayout?.bottom ?: 0))?.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)?.setDuration(250)?.start()

			emojiRationalTextView?.animate()?.setListener(null)?.cancel()

			if (emojiRationalTextView?.visibility != View.VISIBLE) {
				emojiRationalTextView?.visibility = View.VISIBLE
				emojiRationalTextView?.alpha = 0f
			}

			emojiRationalTextView?.animate()?.alpha(1f)?.setDuration(150)?.start()
			overlayBackground?.animate()?.setListener(null)?.cancel()

			if (overlayBackground?.visibility != View.VISIBLE) {
				overlayBackground?.visibility = View.VISIBLE
				overlayBackground?.alpha = 0f
				overlayBackground?.setShowBlackout(currentUserIsVideo || callingUserIsVideo, false)
			}

			overlayBackground?.animate()?.alpha(1f)?.setDuration(150)?.start()
		}
		else {
			emojiLayout?.animate()?.scaleX(1f)?.scaleY(1f)?.translationY(0f)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.setDuration(150)?.start()

			if (emojiRationalTextView?.visibility != View.GONE) {
				emojiRationalTextView?.animate()?.alpha(0f)?.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						val service = VoIPService.sharedInstance

						if (canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
							AndroidUtilities.runOnUIThread(hideUIRunnable, 3000)
							hideUiRunnableWaiting = true
						}

						emojiRationalTextView?.gone()
					}
				})?.setDuration(150)?.start()

				overlayBackground?.animate()?.alpha(0f)?.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						overlayBackground?.gone()
					}
				})?.setDuration(150)?.start()
			}
		}
	}

	private fun updateViewState() {
		if (isFinished || switchingToPip) {
			return
		}

		lockOnScreen = false

		val animated = previousState != -1
		var showAcceptDeclineView = false
		var showTimer = false
		var showReconnecting = false
		var showCallingAvatarMini = false
		var statusLayoutOffset = 0
		val service = VoIPService.sharedInstance

		when (currentState) {
			VoIPService.STATE_WAITING_INCOMING -> {
				showAcceptDeclineView = true
				lockOnScreen = true
				statusLayoutOffset = AndroidUtilities.dp(24f)
				acceptDeclineView?.setRetryMod(false)

				if (service != null && service.privateCall?.video == true) {
					showCallingAvatarMini = currentUserIsVideo && callingUser?.photo != null
					statusTextView?.setText(service.getString(R.string.VoipInVideoCallBranding), true, animated)
					acceptDeclineView?.translationY = -AndroidUtilities.dp(60f).toFloat()
				}
				else {
					statusTextView?.setText(ApplicationLoader.applicationContext.getString(R.string.VoipInCallBranding), true, animated)
					acceptDeclineView?.translationY = 0f
				}
			}

			VoIPService.STATE_WAIT_INIT, VoIPService.STATE_WAIT_INIT_ACK -> {
				statusTextView?.setText(ApplicationLoader.applicationContext.getString(R.string.VoipConnecting), true, animated)
			}

			VoIPService.STATE_EXCHANGING_KEYS -> {
				statusTextView?.setText(ApplicationLoader.applicationContext.getString(R.string.VoipExchangingKeys), true, animated)
			}

			VoIPService.STATE_WAITING -> {
				statusTextView?.setText(ApplicationLoader.applicationContext.getString(R.string.VoipWaiting), true, animated)
			}

			VoIPService.STATE_RINGING -> {
				statusTextView?.setText(ApplicationLoader.applicationContext.getString(R.string.VoipRinging), true, animated)
			}

			VoIPService.STATE_REQUESTING -> {
				statusTextView?.setText(ApplicationLoader.applicationContext.getString(R.string.VoipRequesting), true, animated)
			}

			VoIPService.STATE_HANGING_UP -> {
				// unused
			}

			VoIPService.STATE_BUSY -> {
				showAcceptDeclineView = true
				statusTextView?.setText(ApplicationLoader.applicationContext.getString(R.string.VoipBusy), false, animated)
				acceptDeclineView?.setRetryMod(true)
				currentUserIsVideo = false
				callingUserIsVideo = false
			}

			VoIPService.STATE_ESTABLISHED, VoIPService.STATE_RECONNECTING -> {
				updateKeyView(animated)

				showTimer = true

				if (currentState == VoIPService.STATE_RECONNECTING) {
					showReconnecting = true
				}
			}

			VoIPService.STATE_ENDED -> {
				currentUserTextureView?.saveCameraLastBitmap()

				AndroidUtilities.runOnUIThread({
					windowView?.finish()
				}, 200)
			}

			VoIPService.STATE_FAILED -> {
				statusTextView?.setText(ApplicationLoader.applicationContext.getString(R.string.VoipFailed), false, animated)

				val voipService = VoIPService.sharedInstance
				val lastError = voipService?.getLastError() ?: Instance.ERROR_UNKNOWN

				if (!TextUtils.equals(lastError, Instance.ERROR_UNKNOWN)) {
					if (TextUtils.equals(lastError, Instance.ERROR_INCOMPATIBLE)) {
						val name = ContactsController.formatName(callingUser?.first_name, callingUser?.last_name)
						val message = LocaleController.formatString("VoipPeerIncompatible", R.string.VoipPeerIncompatible, name)
						showErrorDialog(AndroidUtilities.replaceTags(message))
					}
					else if (TextUtils.equals(lastError, Instance.ERROR_PEER_OUTDATED)) {
						if (isVideoCall) {
							val name = UserObject.getFirstName(callingUser)
							val message = LocaleController.formatString("VoipPeerVideoOutdated", R.string.VoipPeerVideoOutdated, name)
							val callAgain = BooleanArray(1)

							activity?.let {
								val dlg = DarkAlertDialog.Builder(it).setTitle(it.getString(R.string.VoipFailed)).setMessage(AndroidUtilities.replaceTags(message)).setNegativeButton(it.getString(R.string.Cancel)) { _, _ ->
									windowView?.finish()
								}.setPositiveButton(it.getString(R.string.VoipPeerVideoOutdatedMakeVoice)) { _, _ ->
									callAgain[0] = true

									currentState = VoIPService.STATE_BUSY

									val intent = Intent(activity, VoIPService::class.java)
									intent.putExtra("user_id", callingUser?.id ?: 0L)
									intent.putExtra("is_outgoing", true)
									intent.putExtra("start_incall_activity", false)
									intent.putExtra("video_call", false)
									intent.putExtra("can_video_call", false)
									intent.putExtra("account", currentAccount)

									try {
										activity?.startService(intent)
									}
									catch (e: Throwable) {
										FileLog.e(e)
									}
								}.show()

								dlg.setCanceledOnTouchOutside(true)

								dlg.setOnDismissListener {
									if (!callAgain[0]) {
										windowView?.finish()
									}
								}
							}
						}
						else {
							val name = UserObject.getFirstName(callingUser)
							val message = LocaleController.formatString("VoipPeerOutdated", R.string.VoipPeerOutdated, name)
							showErrorDialog(AndroidUtilities.replaceTags(message))
						}
					}
					else if (TextUtils.equals(lastError, Instance.ERROR_PRIVACY)) {
						val name = ContactsController.formatName(callingUser?.first_name, callingUser?.last_name)
						val message = LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable, name)
						showErrorDialog(AndroidUtilities.replaceTags(message))
					}
					else if (TextUtils.equals(lastError, Instance.ERROR_AUDIO_IO)) {
						showErrorDialog("Error initializing audio hardware")
					}
					else if (TextUtils.equals(lastError, Instance.ERROR_LOCALIZED)) {
						windowView?.finish()
					}
					else if (TextUtils.equals(lastError, Instance.ERROR_CONNECTION_SERVICE)) {
						showErrorDialog(ApplicationLoader.applicationContext.getString(R.string.VoipErrorUnknown))
					}
					else {
						AndroidUtilities.runOnUIThread({
							windowView?.finish()
						}, 1000)
					}
				}
				else {
					AndroidUtilities.runOnUIThread({
						windowView?.finish()
					}, 1000)
				}
			}
		}

		if (previewDialog != null) {
			return
		}

		if (service != null) {
			callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE
			currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED

			if (currentUserIsVideo && !isVideoCall) {
				isVideoCall = true
			}
		}

		if (animated) {
			currentUserCameraFloatingLayout?.saveRelativePosition()
			callingUserMiniFloatingLayout?.saveRelativePosition()
		}

		if (callingUserIsVideo) {
			if (!switchingToPip) {
				callingUserPhotoView?.alpha = 1f
			}

			if (animated) {
				callingUserTextureView?.animate()?.alpha(1f)?.setDuration(250)?.start()
			}
			else {
				callingUserTextureView?.animate()?.cancel()
				callingUserTextureView?.alpha = 1f
			}

			if (callingUserTextureView?.renderer?.isFirstFrameRendered != true && !enterFromPiP) {
				callingUserIsVideo = false
			}
		}

		if (currentUserIsVideo || callingUserIsVideo) {
			fillNavigationBar(true, animated)
		}
		else {
			fillNavigationBar(false, animated)

			callingUserPhotoView?.visible()

			if (animated) {
				callingUserTextureView?.animate()?.alpha(0f)?.setDuration(250)?.start()
			}
			else {
				callingUserTextureView?.animate()?.cancel()
				callingUserTextureView?.alpha = 0f
			}
		}

		if (!currentUserIsVideo || !callingUserIsVideo) {
			cameraForceExpanded = false
		}

		val showCallingUserVideoMini = currentUserIsVideo && cameraForceExpanded

		showCallingUserAvatarMini(showCallingAvatarMini, animated)

		statusLayoutOffset += if (callingUserPhotoViewMini?.tag == null) 0 else AndroidUtilities.dp(135f) + AndroidUtilities.dp(12f)

		showAcceptDeclineView(showAcceptDeclineView, animated)

		windowView?.isLockOnScreen = lockOnScreen || deviceIsLocked
		canHideUI = currentState == VoIPService.STATE_ESTABLISHED && (currentUserIsVideo || callingUserIsVideo)

		if (!canHideUI && !uiVisible) {
			showUi(true)
		}

		if (uiVisible && canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
			AndroidUtilities.runOnUIThread(hideUIRunnable, 3000)
			hideUiRunnableWaiting = true
		}
		else if (service != null && service.isMicMute()) {
			AndroidUtilities.cancelRunOnUIThread(hideUIRunnable)
			hideUiRunnableWaiting = false
		}

		if (!uiVisible) {
			statusLayoutOffset -= AndroidUtilities.dp(50f)
		}

		if (animated) {
			if (lockOnScreen || !uiVisible) {
				if (backIcon?.visibility != View.VISIBLE) {
					backIcon?.visibility = View.VISIBLE
					backIcon?.alpha = 0f
				}

				backIcon?.animate()?.alpha(0f)?.start()
			}
			else {
				backIcon?.animate()?.alpha(1f)?.start()
			}

			notificationsLayout?.animate()?.translationY((-AndroidUtilities.dp(16f) - if (uiVisible) AndroidUtilities.dp(80f) else 0).toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
		}
		else {
			if (!lockOnScreen) {
				backIcon?.visibility = View.VISIBLE
			}

			backIcon?.alpha = if (lockOnScreen) 0f else 1f
			notificationsLayout?.translationY = (-AndroidUtilities.dp(16f) - if (uiVisible) AndroidUtilities.dp(80f) else 0).toFloat()
		}

		if (currentState != VoIPService.STATE_HANGING_UP && currentState != VoIPService.STATE_ENDED) {
			updateButtons(animated)
		}

		if (showTimer) {
			statusTextView?.showTimer(animated)
		}

		statusTextView?.showReconnect(showReconnecting, animated)

		if (animated) {
			if (statusLayoutOffset != statusLayoutAnimateToOffset) {
				statusLayout?.animate()?.translationY(statusLayoutOffset.toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			}
		}
		else {
			statusLayout?.translationY = statusLayoutOffset.toFloat()
		}

		statusLayoutAnimateToOffset = statusLayoutOffset

		overlayBackground?.setShowBlackout(currentUserIsVideo || callingUserIsVideo, animated)

		canSwitchToPip = currentState != VoIPService.STATE_ENDED && currentState != VoIPService.STATE_BUSY && (currentUserIsVideo || callingUserIsVideo)

		if (service != null) {
			if (currentUserIsVideo) {
				service.sharedUIParams.tapToVideoTooltipWasShowed = true
			}

			currentUserTextureView?.setIsScreencast(service.isScreencast())
			currentUserTextureView?.renderer?.setMirror(service.isFrontFaceCamera())

			service.setSinks(if (currentUserIsVideo && !service.isScreencast()) currentUserTextureView?.renderer else null, if (showCallingUserVideoMini) callingUserMiniTextureRenderer else callingUserTextureView?.renderer)

			if (animated) {
				notificationsLayout?.beforeLayoutChanges()
			}

			if ((currentUserIsVideo || callingUserIsVideo) && (currentState == VoIPService.STATE_ESTABLISHED || currentState == VoIPService.STATE_RECONNECTING) && service.getCallDuration() > 500) {
				if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
					notificationsLayout?.addNotification(R.drawable.calls_mute_mini, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated)
				}
				else {
					notificationsLayout?.removeNotification("muted")
				}

				if (service.getRemoteVideoState() == Instance.VIDEO_STATE_INACTIVE) {
					notificationsLayout?.addNotification(R.drawable.calls_camera_mini, LocaleController.formatString("VoipUserCameraIsOff", R.string.VoipUserCameraIsOff, UserObject.getFirstName(callingUser)), "video", animated)
				}
				else {
					notificationsLayout?.removeNotification("video")
				}
			}
			else {
				if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
					notificationsLayout?.addNotification(R.drawable.calls_mute_mini, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated)
				}
				else {
					notificationsLayout?.removeNotification("muted")
				}

				notificationsLayout?.removeNotification("video")
			}

			if (notificationsLayout?.childCount == 0 && callingUserIsVideo && service.privateCall != null && service.privateCall?.video != true && !service.sharedUIParams.tapToVideoTooltipWasShowed) {
				service.sharedUIParams.tapToVideoTooltipWasShowed = true
				tapToVideoTooltip?.showForView(bottomButtons[1], true)
			}
			else if (notificationsLayout?.childCount != 0) {
				tapToVideoTooltip?.hide()
			}

			if (animated) {
				notificationsLayout?.animateLayoutChanges()
			}
		}

		val floatingViewsOffset = notificationsLayout?.childrenHeight ?: 0

		callingUserMiniFloatingLayout?.setBottomOffset(floatingViewsOffset, animated)
		currentUserCameraFloatingLayout?.setBottomOffset(floatingViewsOffset, animated)
		currentUserCameraFloatingLayout?.setUiVisible(uiVisible)
		callingUserMiniFloatingLayout?.setUiVisible(uiVisible)

		if (currentUserIsVideo) {
			if (!callingUserIsVideo || cameraForceExpanded) {
				showFloatingLayout(STATE_FULLSCREEN, animated)
			}
			else {
				showFloatingLayout(STATE_FLOATING, animated)
			}
		}
		else {
			showFloatingLayout(STATE_GONE, animated)
		}

		if (showCallingUserVideoMini && callingUserMiniFloatingLayout?.tag == null) {
			callingUserMiniFloatingLayout?.setIsActive(true)

			if (callingUserMiniFloatingLayout?.visibility != View.VISIBLE) {
				callingUserMiniFloatingLayout?.visibility = View.VISIBLE
				callingUserMiniFloatingLayout?.alpha = 0f
				callingUserMiniFloatingLayout?.scaleX = 0.5f
				callingUserMiniFloatingLayout?.scaleY = 0.5f
			}

			callingUserMiniFloatingLayout?.animate()?.setListener(null)?.cancel()
			callingUserMiniFloatingLayout?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.setStartDelay(150)?.start()
			callingUserMiniFloatingLayout?.tag = 1
		}
		else if (!showCallingUserVideoMini && callingUserMiniFloatingLayout?.tag != null) {
			callingUserMiniFloatingLayout?.setIsActive(false)

			callingUserMiniFloatingLayout?.animate()?.alpha(0f)?.scaleX(0.5f)?.scaleY(0.5f)?.setListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (callingUserMiniFloatingLayout?.tag == null) {
						callingUserMiniFloatingLayout?.gone()
					}
				}
			})?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()

			callingUserMiniFloatingLayout?.tag = null
		}

		currentUserCameraFloatingLayout?.restoreRelativePosition()
		callingUserMiniFloatingLayout?.restoreRelativePosition()

		updateSpeakerPhoneIcon()
	}

	private fun fillNavigationBar(fill: Boolean, animated: Boolean) {
		if (switchingToPip) {
			return
		}

		if (!animated) {
			navigationBarAnimator?.cancel()

			fillNavigationBarValue = (if (fill) 1 else 0).toFloat()
			overlayBottomPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, (255 * if (fill) 1f else 0.5f).toInt())
		}
		else if (fill != fillNavigationBar) {
			navigationBarAnimator?.cancel()

			navigationBarAnimator = ValueAnimator.ofFloat(fillNavigationBarValue, if (fill) 1f else 0f)
			navigationBarAnimator?.addUpdateListener(navigationBarAnimationListener)
			navigationBarAnimator?.duration = 300
			navigationBarAnimator?.interpolator = LinearInterpolator()
			navigationBarAnimator?.start()
		}

		fillNavigationBar = fill
	}

	private fun showUi(show: Boolean) {
		uiVisibilityAnimator?.cancel()

		if (!show && uiVisible) {
			speakerPhoneIcon?.animate()?.alpha(0f)?.translationY(-AndroidUtilities.dp(50f).toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			backIcon?.animate()?.alpha(0f)?.translationY(-AndroidUtilities.dp(50f).toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			emojiLayout?.animate()?.alpha(0f)?.translationY(-AndroidUtilities.dp(50f).toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			statusLayout?.animate()?.alpha(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			buttonsLayout?.animate()?.alpha(0f)?.translationY(AndroidUtilities.dp(50f).toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			bottomShadow?.animate()?.alpha(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			topShadow?.animate()?.alpha(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()

			uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 0f)
			uiVisibilityAnimator?.addUpdateListener(statusBarAnimatorListener)
			uiVisibilityAnimator?.setDuration(150)?.interpolator = CubicBezierInterpolator.DEFAULT
			uiVisibilityAnimator?.start()

			AndroidUtilities.cancelRunOnUIThread(hideUIRunnable)

			hideUiRunnableWaiting = false

			buttonsLayout?.isEnabled = false
		}
		else if (show && !uiVisible) {
			tapToVideoTooltip?.hide()
			speakerPhoneIcon?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			backIcon?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			emojiLayout?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			statusLayout?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			buttonsLayout?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			bottomShadow?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			topShadow?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()

			uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 1f)
			uiVisibilityAnimator?.addUpdateListener(statusBarAnimatorListener)
			uiVisibilityAnimator?.setDuration(150)?.interpolator = CubicBezierInterpolator.DEFAULT
			uiVisibilityAnimator?.start()

			buttonsLayout?.isEnabled = true
		}

		uiVisible = show
		windowView?.requestFullscreen(!show)
		notificationsLayout?.animate()?.translationY((-AndroidUtilities.dp(16f) - if (uiVisible) AndroidUtilities.dp(80f) else 0).toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
	}

	private fun showFloatingLayout(state: Int, animated: Boolean) {
		if (currentUserCameraFloatingLayout?.tag == null || currentUserCameraFloatingLayout?.tag as Int != STATE_FLOATING) {
			currentUserCameraFloatingLayout?.setUiVisible(uiVisible)
		}

		if (!animated && cameraShowingAnimator != null) {
			cameraShowingAnimator?.removeAllListeners()
			cameraShowingAnimator?.cancel()
		}

		if (state == STATE_GONE) {
			if (animated) {
				if (currentUserCameraFloatingLayout?.tag != null && currentUserCameraFloatingLayout?.tag as Int != STATE_GONE) {

					cameraShowingAnimator?.removeAllListeners()
					cameraShowingAnimator?.cancel()

					val animatorSet = AnimatorSet()
					animatorSet.playTogether(ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, (currentUserCameraFloatingLayout?.alpha ?: 0f), 0f))

					if (currentUserCameraFloatingLayout?.tag != null && currentUserCameraFloatingLayout?.tag as Int == STATE_FLOATING) {
						animatorSet.playTogether(ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, (currentUserCameraFloatingLayout?.scaleX ?: 1f), 0.7f), ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, currentUserCameraFloatingLayout?.scaleX ?: 1f, 0.7f))
					}

					cameraShowingAnimator = animatorSet

					cameraShowingAnimator?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							currentUserCameraFloatingLayout?.translationX = 0f
							currentUserCameraFloatingLayout?.translationY = 0f
							currentUserCameraFloatingLayout?.scaleY = 1f
							currentUserCameraFloatingLayout?.scaleX = 1f
							currentUserCameraFloatingLayout?.visibility = View.GONE
						}
					})

					cameraShowingAnimator?.setDuration(250)?.interpolator = CubicBezierInterpolator.DEFAULT
					cameraShowingAnimator?.startDelay = 50
					cameraShowingAnimator?.start()
				}
			}
			else {
				currentUserCameraFloatingLayout?.gone()
			}
		}
		else {
			var switchToFloatAnimated = animated

			if (currentUserCameraFloatingLayout?.tag == null || currentUserCameraFloatingLayout?.tag as Int == STATE_GONE) {
				switchToFloatAnimated = false
			}

			if (animated) {
				if (currentUserCameraFloatingLayout?.tag != null && currentUserCameraFloatingLayout?.tag as Int == STATE_GONE) {
					if (currentUserCameraFloatingLayout?.visibility == View.GONE) {
						currentUserCameraFloatingLayout?.alpha = 0f
						currentUserCameraFloatingLayout?.scaleX = 0.7f
						currentUserCameraFloatingLayout?.scaleY = 0.7f
						currentUserCameraFloatingLayout?.visible()
					}

					cameraShowingAnimator?.removeAllListeners()
					cameraShowingAnimator?.cancel()

					val animatorSet = AnimatorSet()
					animatorSet.playTogether(ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, 0.0f, 1f), ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, 0.7f, 1f), ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, 0.7f, 1f))

					cameraShowingAnimator = animatorSet
					cameraShowingAnimator?.setDuration(150)?.start()
				}
			}
			else {
				currentUserCameraFloatingLayout?.visible()
			}

			if ((currentUserCameraFloatingLayout?.tag == null || currentUserCameraFloatingLayout?.tag as Int != STATE_FLOATING) && (currentUserCameraFloatingLayout?.relativePositionToSetX ?: 0f) < 0) {
				currentUserCameraFloatingLayout?.setRelativePosition(1f, 1f)
				currentUserCameraIsFullscreen = true
			}

			currentUserCameraFloatingLayout?.setFloatingMode(state == STATE_FLOATING, switchToFloatAnimated)
			currentUserCameraIsFullscreen = state != STATE_FLOATING
		}

		currentUserCameraFloatingLayout?.tag = state
	}

	private fun showCallingUserAvatarMini(show: Boolean, animated: Boolean) {
		if (animated) {
			if (show && callingUserPhotoViewMini?.tag == null) {
				callingUserPhotoViewMini?.animate()?.setListener(null)?.cancel()
				callingUserPhotoViewMini?.visibility = View.VISIBLE
				callingUserPhotoViewMini?.alpha = 0f
				callingUserPhotoViewMini?.translationY = -AndroidUtilities.dp(135f).toFloat()
				callingUserPhotoViewMini?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.start()
			}
			else if (!show && callingUserPhotoViewMini?.tag != null) {
				callingUserPhotoViewMini?.animate()?.setListener(null)?.cancel()

				callingUserPhotoViewMini?.animate()?.alpha(0f)?.translationY(-AndroidUtilities.dp(135f).toFloat())?.setDuration(150)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						callingUserPhotoViewMini?.gone()
					}
				})?.start()
			}
		}
		else {
			callingUserPhotoViewMini?.animate()?.setListener(null)?.cancel()
			callingUserPhotoViewMini?.translationY = 0f
			callingUserPhotoViewMini?.alpha = 1f
			callingUserPhotoViewMini?.visibility = if (show) View.VISIBLE else View.GONE
		}

		callingUserPhotoViewMini?.tag = if (show) 1 else null
	}

	private fun updateKeyView(animated: Boolean) {
		if (emojiLoaded) {
			return
		}

		val service = VoIPService.sharedInstance ?: return

		val authKey = runCatching {
			ByteArrayOutputStream().use {
				it.write(service.getEncryptionKey())
				it.write(service.getGA())
				it.toByteArray()
			}
		}.onFailure {
			FileLog.e(it)
		}.getOrNull() ?: return

		val sha256 = Utilities.computeSHA256(authKey, 0, authKey.size.toLong())
		val emoji = EncryptionKeyEmojifier.emojifyForCall(sha256)

		for (i in 0..3) {
			Emoji.preloadEmoji(emoji[i])

			val drawable = Emoji.getEmojiDrawable(emoji[i])

			if (drawable != null) {
				drawable.setBounds(0, 0, AndroidUtilities.dp(22f), AndroidUtilities.dp(22f))
				drawable.preload()

				emojiViews[i]?.setImageDrawable(drawable)
				emojiViews[i]?.contentDescription = emoji[i]
				emojiViews[i]?.gone()
			}

			emojiDrawables[i] = drawable
		}

		checkEmojiLoaded(animated)
	}

	private fun checkEmojiLoaded(animated: Boolean) {
		var count = 0

		for (i in 0..3) {
			if (emojiDrawables[i]?.isLoaded == true) {
				count++
			}
		}

		if (count == 4) {
			emojiLoaded = true

			for (i in 0..3) {
				if (emojiViews[i]?.visibility != View.VISIBLE) {
					emojiViews[i]?.visible()

					if (animated) {
						emojiViews[i]?.alpha = 0f
						emojiViews[i]?.translationY = AndroidUtilities.dp(30f).toFloat()
						emojiViews[i]?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(200)?.setStartDelay((20 * i).toLong())?.start()
					}
				}
			}
		}
	}

	private fun showAcceptDeclineView(show: Boolean, animated: Boolean) {
		if (!animated) {
			acceptDeclineView?.visibility = if (show) View.VISIBLE else View.GONE
		}
		else {
			if (show && acceptDeclineView?.tag == null) {
				acceptDeclineView?.animate()?.setListener(null)?.cancel()

				if (acceptDeclineView?.visibility == View.GONE) {
					acceptDeclineView?.visibility = View.VISIBLE
					acceptDeclineView?.alpha = 0f
				}

				acceptDeclineView?.animate()?.alpha(1f)
			}

			if (!show && acceptDeclineView?.tag != null) {
				acceptDeclineView?.animate()?.setListener(null)?.cancel()

				acceptDeclineView?.animate()?.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						acceptDeclineView?.visibility = View.GONE
					}
				})?.alpha(0f)
			}
		}

		acceptDeclineView?.isEnabled = show
		acceptDeclineView?.tag = if (show) 1 else null
	}

	private fun updateButtons(animated: Boolean) {
		val service = VoIPService.sharedInstance ?: return

		if (animated) {
			val transitionSet = TransitionSet()

			val visibility = object : Visibility() {
				override fun onAppear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues, endValues: TransitionValues): Animator {
					val animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, AndroidUtilities.dp(100f).toFloat(), 0f)

					if (view is VoIPToggleButton) {
						view.setTranslationY(AndroidUtilities.dp(100f).toFloat())
						animator.startDelay = view.animationDelay.toLong()
					}

					return animator
				}

				override fun onDisappear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues, endValues: TransitionValues): Animator {
					return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, AndroidUtilities.dp(100f).toFloat())
				}
			}

			transitionSet.addTransition(visibility.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)).addTransition(ChangeBounds().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT))
			transitionSet.excludeChildren(VoIPToggleButton::class.java, true)

			TransitionManager.beginDelayedTransition(buttonsLayout, transitionSet)
		}

		if (currentState == VoIPService.STATE_WAITING_INCOMING || currentState == VoIPService.STATE_BUSY) {
			if (service.privateCall?.video == true && currentState == VoIPService.STATE_WAITING_INCOMING) {
				if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
					setFrontalCameraAction(bottomButtons[0], service, animated)

					if (uiVisible) {
						speakerPhoneIcon?.animate()?.alpha(1f)?.start()
					}
				}
				else {
					setSpeakerPhoneAction(bottomButtons[0], service, animated)
					speakerPhoneIcon?.animate()?.alpha(0f)?.start()
				}

				// setVideoAction(bottomButtons[1], service, animated) // MARK: uncomment to enable video call
				setMicrophoneAction(bottomButtons[2], service, animated)
			}
			else {
				bottomButtons[0]?.gone()
				bottomButtons[1]?.gone()
				bottomButtons[2]?.gone()
			}

			bottomButtons[1]?.gone() // MARK: remove to enable video call
			bottomButtons[3]?.gone()
		}
		else {
			if (instance == null) {
				return
			}

			if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
				setFrontalCameraAction(bottomButtons[0], service, animated)

				if (uiVisible) {
					speakerPhoneIcon?.tag = 1
					speakerPhoneIcon?.animate()?.alpha(1f)?.start()
				}
			}
			else {
				setSpeakerPhoneAction(bottomButtons[0], service, animated)
				speakerPhoneIcon?.tag = null
				speakerPhoneIcon?.animate()?.alpha(0f)?.start()
			}

			// setVideoAction(bottomButtons[1], service, animated) // MARK: uncomment to enable video call
			setMicrophoneAction(bottomButtons[2], service, animated)
			bottomButtons[3]?.setData(R.drawable.calls_decline, Color.WHITE, Color.parseColor("#EF4062"), service.getString(R.string.VoipEndCall), false, animated)

			bottomButtons[3]?.setOnClickListener {
				VoIPService.sharedInstance?.hangUp()
			}

			bottomButtons[1]?.gone() // MARK: remove to enable video call
		}

		var animationDelay = 0

		for (i in 0..3) {
			if (bottomButtons[i]?.visibility == View.VISIBLE) {
				bottomButtons[i]?.animationDelay = animationDelay
				animationDelay += 16
			}
		}

		updateSpeakerPhoneIcon()
	}

	private fun setMicrophoneAction(bottomButton: VoIPToggleButton?, service: VoIPService, animated: Boolean) {
		if (service.isMicMute()) {
			bottomButton?.setData(R.drawable.ic_mic, Color.BLACK, Color.WHITE, service.getString(R.string.VoipUnmute), true, animated)
			bottomButton?.setCrossOffset(AndroidUtilities.dpf2(0.5f))
		}
		else {
			bottomButton?.setData(R.drawable.ic_mic, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()), service.getString(R.string.VoipMute), false, animated)
		}

		currentUserCameraFloatingLayout?.setMuted(service.isMicMute(), animated)

		bottomButton?.setOnClickListener {
			val serviceInstance = VoIPService.sharedInstance ?: return@setOnClickListener
			val micMute = !serviceInstance.isMicMute()

			if (accessibilityManager?.isTouchExplorationEnabled == true) {
				val text = if (micMute) {
					serviceInstance.getString(R.string.AccDescrVoipMicOff)
				}
				else {
					serviceInstance.getString(R.string.AccDescrVoipMicOn)
				}

				it.announceForAccessibility(text)
			}

			serviceInstance.setMicMute(mute = micMute, hold = false, send = true)

			previousState = currentState

			updateViewState()
		}
	}

	private fun setVideoAction(bottomButton: VoIPToggleButton?, service: VoIPService, animated: Boolean) {
		val isVideoAvailable = if (currentUserIsVideo || callingUserIsVideo) {
			true
		}
		else {
			service.isVideoAvailable()
		}

		if (isVideoAvailable) {
			if (currentUserIsVideo) {
				bottomButton?.setData(if (service.isScreencast()) R.drawable.calls_sharescreen else R.drawable.calls_video, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()), service.getString(R.string.VoipStopVideo), false, animated)
			}
			else {
				bottomButton?.setData(R.drawable.calls_video, Color.BLACK, Color.WHITE, service.getString(R.string.VoipStartVideo), true, animated)
			}

			bottomButton?.setCrossOffset(-AndroidUtilities.dpf2(3.5f))

			bottomButton?.setOnClickListener {
				if (activity?.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
					activity?.requestPermissions(arrayOf(Manifest.permission.CAMERA), 102)
				}
				else {
					toggleCameraInput()
				}
			}

			bottomButton?.isEnabled = true
		}
		else {
			bottomButton?.setData(R.drawable.calls_video, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.5f).toInt()), ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()), service.getString(R.string.ChatVideo), false, animated)
			bottomButton?.setOnClickListener(null)
			bottomButton?.isEnabled = false
		}
	}

	private fun updateSpeakerPhoneIcon() {
		val service = VoIPService.sharedInstance ?: return

		if (service.isBluetoothOn()) {
			speakerPhoneIcon?.setImageResource(R.drawable.calls_bluetooth)
		}
		else if (service.isSpeakerphoneOn()) {
			speakerPhoneIcon?.setImageResource(R.drawable.ic_cross_speaker)
		}
		else {
			if (service.isHeadsetPlugged()) {
				speakerPhoneIcon?.setImageResource(R.drawable.calls_menu_headset)
			}
			else {
				//MARK: If you need to, uncomment the icon
//				speakerPhoneIcon?.setImageResource(R.drawable.calls_menu_phone)
			}
		}
	}

	private fun setSpeakerPhoneAction(bottomButton: VoIPToggleButton?, service: VoIPService, animated: Boolean) {
		if (service.isBluetoothOn()) {
			bottomButton?.setData(R.drawable.calls_bluetooth, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()), service.getString(R.string.VoipAudioRoutingBluetooth), false, animated)
			bottomButton?.setChecked(false, animated)
		}
		else if (service.isSpeakerphoneOn()) {
			/** I don’t know why, but first you need to set the icon itself, and only then process its strikethrough
			 * cross: false
			 * cross: true
			 */
			bottomButton?.setData(R.drawable.ic_cross_speaker, Color.BLACK, Color.WHITE, service.getString(R.string.VoipSpeaker), false, animated)
			bottomButton?.setData(R.drawable.ic_cross_speaker, Color.BLACK, Color.WHITE, service.getString(R.string.VoipSpeaker), true, animated)

			bottomButton?.setChecked(true, animated)
			bottomButton?.setCrossOffset(AndroidUtilities.dpf2(0.5f))
		}
		else {
			bottomButton?.setData(R.drawable.ic_speaker, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()), service.getString(R.string.VoipSpeaker), false, animated)
			bottomButton?.setChecked(false, animated)
		}

		bottomButton?.setCheckableForAccessibility(true)
		bottomButton?.isEnabled = true

		bottomButton?.setOnClickListener {
			VoIPService.sharedInstance?.toggleSpeakerphoneOrShowRouteSheet(activity, false)
		}
	}

	private fun setFrontalCameraAction(bottomButton: VoIPToggleButton?, service: VoIPService, animated: Boolean) {
		if (!currentUserIsVideo) {
			bottomButton?.setData(R.drawable.calls_flip, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.5f).toInt()), ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()), service.getString(R.string.VoipFlip), false, animated)
			bottomButton?.setOnClickListener(null)
			bottomButton?.isEnabled = false
		}
		else {
			bottomButton?.isEnabled = true

			if (!service.isFrontFaceCamera()) {
				bottomButton?.setData(R.drawable.calls_flip, Color.BLACK, Color.WHITE, service.getString(R.string.VoipFlip), false, animated)
			}
			else {
				bottomButton?.setData(R.drawable.calls_flip, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()), service.getString(R.string.VoipFlip), false, animated)
			}

			bottomButton?.setOnClickListener {
				val serviceInstance = VoIPService.sharedInstance ?: return@setOnClickListener

				if (accessibilityManager?.isTouchExplorationEnabled == true) {
					val text = if (serviceInstance.isFrontFaceCamera()) {
						serviceInstance.getString(R.string.AccDescrVoipCamSwitchedToBack)
					}
					else {
						serviceInstance.getString(R.string.AccDescrVoipCamSwitchedToFront)
					}

					it.announceForAccessibility(text)
				}

				serviceInstance.switchCamera()
			}
		}
	}

	fun onScreenCastStart() {
		previewDialog?.dismiss(true, true)
	}

	private fun toggleCameraInput() {
		val service = VoIPService.sharedInstance ?: return

		if (accessibilityManager?.isTouchExplorationEnabled == true) {
			val text = if (!currentUserIsVideo) {
				service.getString(R.string.AccDescrVoipCamOn)
			}
			else {
				service.getString(R.string.AccDescrVoipCamOff)
			}

			fragmentView?.announceForAccessibility(text)
		}

		if (!currentUserIsVideo) {
			if (previewDialog == null) {
				service.createCaptureDevice(false)

				if (!service.isFrontFaceCamera()) {
					service.switchCamera()
				}

				windowView?.isLockOnScreen = true

				previewDialog = object : PrivateVideoPreviewDialog(fragmentView?.context, false, true) {
					public override fun onDismiss(screencast: Boolean, apply: Boolean) {
						previewDialog = null

						@Suppress("NAME_SHADOWING") val service = VoIPService.sharedInstance

						windowView?.isLockOnScreen = false

						if (apply) {
							currentUserIsVideo = true

							if (service != null && !screencast) {
								service.requestVideoCall(false)
								service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE)
							}
						}
						else {
							service?.setVideoState(false, Instance.VIDEO_STATE_INACTIVE)
						}

						previousState = currentState

						updateViewState()
					}
				}

				lastInsets?.let {
					previewDialog?.setBottomPadding(it.systemWindowInsetBottom)
				}

				fragmentView?.addView(previewDialog)
			}
			return
		}
		else {
			currentUserTextureView?.saveCameraLastBitmap()
			service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE)
			service.clearCamera()

		}
		previousState = currentState

		updateViewState()
	}

	private fun onRequestPermissionsResultInternal(requestCode: Int, grantResults: IntArray) {
		if (requestCode == 101) {
			if (VoIPService.sharedInstance == null) {
				windowView?.finish()
				return
			}

			if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				VoIPService.sharedInstance?.acceptIncomingCall()
			}
			else {
				if (activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) != true) {
					VoIPService.sharedInstance?.declineIncomingCall()

					permissionDenied(activity, {
						windowView?.finish()
					}, requestCode)

					return
				}
			}
		}

		if (requestCode == 102) {
			if (VoIPService.sharedInstance == null) {
				windowView?.finish()
				return
			}

			if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				toggleCameraInput()
			}
		}
	}

	private fun updateSystemBarColors() {
		overlayPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.4f * uiVisibilityAlpha * enterTransitionProgress).toInt())
		overlayBottomPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, (255 * (0.5f + 0.5f * fillNavigationBarValue) * enterTransitionProgress).toInt())
		fragmentView?.invalidate()
	}

	fun onPauseInternal() {
		val pm = activity?.getSystemService(Context.POWER_SERVICE) as? PowerManager
		val screenOn = pm?.isInteractive == true
		val hasPermissionsToPip = AndroidUtilities.checkInlinePermissions(activity)

		if (canSwitchToPip && hasPermissionsToPip) {
			var h = instance?.windowView?.measuredHeight ?: 0

			instance?.lastInsets?.let {
				h -= it.systemWindowInsetBottom
			}

			instance?.let {
				it.activity?.let { activity ->
					VoIPPiPView.show(activity, it.currentAccount, it.windowView?.measuredWidth ?: 0, h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE)
				}
			}

			instance?.lastInsets?.let {
				VoIPPiPView.topInset = it.systemWindowInsetTop
				VoIPPiPView.bottomInset = it.systemWindowInsetBottom
			}
		}

		if (currentUserIsVideo && (!hasPermissionsToPip || !screenOn)) {
			VoIPService.sharedInstance?.setVideoState(false, Instance.VIDEO_STATE_PAUSED)
		}
	}

	fun onResumeInternal() {
		if (VoIPPiPView.getInstance() != null) {
			VoIPPiPView.finish()
		}

		val service = VoIPService.sharedInstance

		if (service != null) {
			if (service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
				service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE)
			}

			updateViewState()
		}
		else {
			windowView?.finish()
		}

		deviceIsLocked = (activity?.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
	}

	private fun showErrorDialog(message: CharSequence) {
		val activity = activity ?: return

		if (activity.isFinishing) {
			return
		}

		val dlg = DarkAlertDialog.Builder(activity).setTitle(activity.getString(R.string.VoipFailed)).setMessage(message).setPositiveButton(activity.getString(R.string.OK), null).show()
		dlg.setCanceledOnTouchOutside(true)

		dlg.setOnDismissListener {
			windowView?.finish()
		}
	}

	private fun requestInlinePermissions() {
		val activity = activity ?: return

		AlertsCreator.createDrawOverlayPermissionDialog(activity) { _, _ ->
			windowView?.finish()
		}.show()
	}

	companion object {
		private const val STATE_GONE = 0
		private const val STATE_FULLSCREEN = 1
		private const val STATE_FLOATING = 2

		var instance: VoIPFragment? = null
			private set

		fun show(activity: Activity, account: Int) {
			show(activity, false, account)
		}

		fun show(activity: Activity, overlay: Boolean, account: Int) {
			if (instance != null && instance?.windowView?.parent == null) {
				if (instance != null) {
					instance?.callingUserTextureView?.renderer?.release()
					instance?.currentUserTextureView?.renderer?.release()
					instance?.callingUserMiniTextureRenderer?.release()
					instance?.destroy()
				}

				instance = null
			}

			if (instance != null || activity.isFinishing) {
				return
			}

			val transitionFromPip = VoIPPiPView.getInstance() != null

			if (VoIPService.sharedInstance?.getUser() == null) {
				return
			}

			val fragment = VoIPFragment(account)
			fragment.activity = activity

			instance = fragment

			val windowView = object : VoIPWindowView(activity, !transitionFromPip) {
				override fun dispatchKeyEvent(event: KeyEvent): Boolean {
					if (fragment.isFinished || fragment.switchingToPip) {
						return false
					}

					val keyCode = event.keyCode

					if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && !fragment.lockOnScreen) {
						fragment.onBackPressed()
						return true
					}

					if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
						if (fragment.currentState == VoIPService.STATE_WAITING_INCOMING) {
							val service = VoIPService.sharedInstance

							if (service != null) {
								service.stopRinging()
								return true
							}
						}
					}

					return super.dispatchKeyEvent(event)
				}
			}

			instance?.deviceIsLocked = (activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true

			val pm = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
			val screenOn = pm?.isInteractive == true
			instance?.screenWasWakeup = !screenOn
			windowView.isLockOnScreen = instance?.deviceIsLocked == true

			fragment.windowView = windowView

			windowView.setOnApplyWindowInsetsListener { _, windowInsets ->
				fragment.setInsets(windowInsets)

				if (Build.VERSION.SDK_INT >= 30) {
					return@setOnApplyWindowInsetsListener WindowInsets.CONSUMED
				}
				else {
					@Suppress("DEPRECATION") return@setOnApplyWindowInsetsListener windowInsets.consumeSystemWindowInsets()
				}
			}

			val wm = activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
			val layoutParams = windowView.createWindowLayoutParams()

			if (overlay) {
				layoutParams.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
				}
				else {
					@Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
				}
			}

			wm?.addView(windowView, layoutParams)

			val view = fragment.createView(activity)

			windowView.addView(view)

			if (transitionFromPip) {
				fragment.enterTransitionProgress = 0f
				fragment.startTransitionFromPiP()
			}
			else {
				fragment.enterTransitionProgress = 1f
				fragment.updateSystemBarColors()
			}
		}

		fun clearInstance() {
			if (instance != null) {
				if (VoIPService.sharedInstance != null) {
					var h = instance?.windowView?.measuredHeight ?: 0

					instance?.lastInsets?.let {
						h -= it.systemWindowInsetBottom
					}

					if (instance?.canSwitchToPip == true) {
						instance?.let {
							it.activity?.let { activity ->
								VoIPPiPView.show(activity, it.currentAccount, it.windowView?.measuredWidth ?: 0, h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE)
							}
						}

						instance?.lastInsets?.let {
							VoIPPiPView.topInset = it.systemWindowInsetTop
							VoIPPiPView.bottomInset = it.systemWindowInsetBottom
						}
					}
				}

				instance?.callingUserTextureView?.renderer?.release()
				instance?.currentUserTextureView?.renderer?.release()
				instance?.callingUserMiniTextureRenderer?.release()
				instance?.destroy()
			}

			instance = null
		}

		@JvmStatic
		fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
			instance?.onRequestPermissionsResultInternal(requestCode, grantResults)
		}

		fun onPause() {
			instance?.onPauseInternal()
			VoIPPiPView.getInstance()?.onPause()
		}

		fun onResume() {
			instance?.onResumeInternal()
			VoIPPiPView.getInstance()?.onResume()
		}
	}
}
