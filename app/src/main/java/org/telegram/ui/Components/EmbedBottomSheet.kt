/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BringAppForegroundService
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AlertsCreator.createDrawOverlayPermissionDialog
import org.telegram.ui.Components.WebPlayerView.WebPlayerViewDelegate
import org.telegram.ui.LaunchActivity
import org.telegram.ui.PhotoViewer
import org.telegram.ui.PhotoViewer.PhotoViewerProvider
import java.util.Locale
import kotlin.math.min

@SuppressLint("WrongConstant")
class EmbedBottomSheet @SuppressLint("SetJavaScriptEnabled") private constructor(context: Context, title: String?, description: String?, originalUrl: String?, url: String, w: Int, h: Int, seekTime: Int) : BottomSheet(context, false) {
	private val webView = object : WebView(context) {
		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			val result = super.onTouchEvent(event)

			if (result) {
				if (event.action == MotionEvent.ACTION_UP) {
					setDisableScroll(false)
				}
				else {
					setDisableScroll(true)
				}
			}

			return result
		}
	}

	private lateinit var videoView: WebPlayerView
	private var customView: View? = null
	private val fullscreenVideoContainer: FrameLayout
	private var customViewCallback: CustomViewCallback? = null
	private val progressBarBlackBackground = View(context)
	private val progressBar = RadialProgressView(context)
	private var parentActivity: Activity? = null
	private val imageButtonsContainer = LinearLayout(context)
	private val copyTextButton = TextView(context)
	private val containerLayout: FrameLayout
	private val pipButton = ImageView(context)
	private var isYouTube = false
	private val position = IntArray(2)
	private var orientationEventListener: OrientationEventListener? = null
	private var width: Int
	private var height: Int
	private val openUrl: String?
	private val hasDescription: Boolean
	private val embedUrl: String
	private var prevOrientation = -2
	private var fullscreenedByButton = false
	private var wasInLandscape = false
	private var animationInProgress = false
	private var waitingForDraw = 0
	private val seekTimeOverride: Int

	private inner class YoutubeProxy {
		@JavascriptInterface
		fun postEvent(eventName: String, eventData: String?) {
			if ("loaded" == eventName) {
				AndroidUtilities.runOnUIThread {
					progressBar.visibility = View.INVISIBLE
					progressBarBlackBackground.visibility = View.INVISIBLE
					pipButton.isEnabled = true
					pipButton.alpha = 1.0f
				}
			}
		}
	}

	private val youtubeFrame = "<!DOCTYPE html><html><head><style>" + "body { margin: 0; width:100%%; height:100%%;  background-color:#000; }" + "html { width:100%%; height:100%%; background-color:#000; }" + ".embed-container iframe," + ".embed-container object," + "   .embed-container embed {" + "       position: absolute;" + "       top: 0;" + "       left: 0;" + "       width: 100%% !important;" + "       height: 100%% !important;" + "   }" + "   </style></head><body>" + "   <div class=\"embed-container\">" + "       <div id=\"player\"></div>" + "   </div>" + "   <script src=\"https://www.youtube.com/iframe_api\"></script>" + "   <script>" + "   var player;" + "   var observer;" + "   var videoEl;" + "   var playing;" + "   var posted = false;" + "   YT.ready(function() {" + "       player = new YT.Player(\"player\", {" + "                              \"width\" : \"100%%\"," + "                              \"events\" : {" + "                              \"onReady\" : \"onReady\"," + "                              \"onError\" : \"onError\"," + "                              \"onStateChange\" : \"onStateChange\"," + "                              }," + "                              \"videoId\" : \"%1\$s\"," + "                              \"height\" : \"100%%\"," + "                              \"playerVars\" : {" + "                              \"start\" : %2\$d," + "                              \"rel\" : 1," + "                              \"showinfo\" : 0," + "                              \"modestbranding\" : 0," + "                              \"iv_load_policy\" : 3," + "                              \"autohide\" : 1," + "                              \"autoplay\" : 1," + "                              \"cc_load_policy\" : 1," + "                              \"playsinline\" : 1," + "                              \"controls\" : 1" + "                              }" + "                            });" + "        player.setSize(window.innerWidth, window.innerHeight);" + "    });" + "    function hideControls() { " + "       playing = !videoEl.paused;" + "       videoEl.controls = 0;" + "       observer.observe(videoEl, {attributes: true});" + "    }" + "    function showControls() { " + "       playing = !videoEl.paused;" + "       observer.disconnect();" + "       videoEl.controls = 1;" + "    }" + "    function onError(event) {" + "       if (!posted) {" + "            if (window.YoutubeProxy !== undefined) {" + "                   YoutubeProxy.postEvent(\"loaded\", null); " + "            }" + "            posted = true;" + "       }" + "    }" + "    function onStateChange(event) {" + "       if (event.data == YT.PlayerState.PLAYING && !posted) {" + "            if (window.YoutubeProxy !== undefined) {" + "                   YoutubeProxy.postEvent(\"loaded\", null); " + "            }" + "            posted = true;" + "       }" + "    }" + "    function onReady(event) {" + "       player.playVideo();" + "    }" + "    window.onresize = function() {" + "       player.setSize(window.innerWidth, window.innerHeight);" + "       player.playVideo();" + "    }" + "    </script>" + "</body>" + "</html>"

//	private val onShowListener = OnShowListener {
//		if (PipVideoOverlay.isVisible() && videoView.isInline) {
//			videoView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
//				override fun onPreDraw(): Boolean {
//					videoView.viewTreeObserver.removeOnPreDrawListener(this)
//					return true
//				}
//			})
//		}
//	}

	init {
		fullWidth = true

		setApplyTopPadding(false)
		setApplyBottomPadding(false)

		seekTimeOverride = seekTime

		if (context is Activity) {
			parentActivity = context
		}

		embedUrl = url
		hasDescription = !description.isNullOrEmpty()
		openUrl = originalUrl
		width = w
		height = h

		if (width == 0 || height == 0) {
			width = AndroidUtilities.displaySize.x
			height = AndroidUtilities.displaySize.y / 2
		}

		fullscreenVideoContainer = FrameLayout(context)
		fullscreenVideoContainer.keepScreenOn = true
		fullscreenVideoContainer.setBackgroundColor(-0x1000000)
		fullscreenVideoContainer.fitsSystemWindows = true
		fullscreenVideoContainer.setOnTouchListener { _, _ -> true }

		container.addView(fullscreenVideoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		fullscreenVideoContainer.visibility = View.INVISIBLE

		containerLayout = object : FrameLayout(context) {
			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()

				try {
					if ((!PipVideoOverlay.isVisible() || webView.visibility != VISIBLE) && webView.parent != null) {
						removeView(webView)

						webView.stopLoading()
						webView.loadUrl("about:blank")
						webView.destroy()
					}

					if (!videoView.isInline && !PipVideoOverlay.isVisible()) {
						if (instance === this@EmbedBottomSheet) {
							instance = null
						}

						videoView.destroy()
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
				val scale = this@EmbedBottomSheet.width / parentWidth.toFloat()
				@Suppress("NAME_SHADOWING") val h = min((this@EmbedBottomSheet.height / scale).toDouble(), (AndroidUtilities.displaySize.y / 2).toDouble()).toInt()
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h + AndroidUtilities.dp((48 + 36 + (if (hasDescription) 22 else 0)).toFloat()) + 1, MeasureSpec.EXACTLY))
			}
		}

		containerLayout.setOnTouchListener { _, _ -> true }

		setCustomView(containerLayout)

		webView.getSettings().javaScriptEnabled = true
		webView.getSettings().domStorageEnabled = true
		webView.getSettings().mediaPlaybackRequiresUserGesture = false
		webView.getSettings().mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

		val cookieManager = CookieManager.getInstance()
		cookieManager.setAcceptThirdPartyCookies(webView, true)

		webView.setWebChromeClient(object : WebChromeClient() {
			@Deprecated("Deprecated in Java", ReplaceWith("onShowCustomView(view, callback)"))
			override fun onShowCustomView(view: View, requestedOrientation: Int, callback: CustomViewCallback) {
				onShowCustomView(view, callback)
			}

			override fun onShowCustomView(view: View, callback: CustomViewCallback) {
				if (customView != null || PipVideoOverlay.isVisible()) {
					callback.onCustomViewHidden()
					return
				}

				exitFromPip()

				customView = view

				sheetContainer.visibility = View.INVISIBLE

				fullscreenVideoContainer.visibility = View.VISIBLE
				fullscreenVideoContainer.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

				customViewCallback = callback
			}

			override fun onHideCustomView() {
				super.onHideCustomView()

				if (customView == null) {
					return
				}

				sheetContainer.visibility = View.VISIBLE

				fullscreenVideoContainer.visibility = View.INVISIBLE
				fullscreenVideoContainer.removeView(customView)

				if (customViewCallback?.javaClass?.name?.contains(".chromium.") != true) {
					customViewCallback?.onCustomViewHidden()
				}

				customView = null
			}
		})

		webView.setWebViewClient(object : WebViewClient() {
			override fun onLoadResource(view: WebView, url: String) {
				super.onLoadResource(view, url)
			}

			override fun onPageFinished(view: WebView, url: String) {
				super.onPageFinished(view, url)

				if (!isYouTube) {
					progressBar.visibility = View.INVISIBLE

					progressBarBlackBackground.visibility = View.INVISIBLE

					pipButton.isEnabled = true
					pipButton.alpha = 1.0f
				}
			}

			@Deprecated("Deprecated in Java")
			override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
				if (isYouTube) {
					Browser.openUrl(view.context, url)
					return true
				}

				return super.shouldOverrideUrlLoading(view, url)
			}
		})

		containerLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, (48f + 36f + (if (hasDescription) 22f else 0f))))

		videoView = WebPlayerView(context, true, false, object : WebPlayerViewDelegate {
			override fun onInitFailed() {
				webView.visibility = View.VISIBLE
				imageButtonsContainer.visibility = View.VISIBLE
				copyTextButton.visibility = View.INVISIBLE
				webView.keepScreenOn = true
				videoView.visibility = View.INVISIBLE
				videoView.controlsView.visibility = View.INVISIBLE
				videoView.textureView.visibility = View.INVISIBLE
				videoView.textureImageView?.visibility = View.INVISIBLE

				videoView.loadVideo(null, null, null, null, false)

				try {
					webView.loadUrl(embedUrl, mapOf("Referer" to "messenger.ello.team"))
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			override fun onSwitchToFullscreen(controlsView: View, fullscreen: Boolean, aspectRatio: Float, rotation: Int, byButton: Boolean): TextureView? {
				if (fullscreen) {
					fullscreenVideoContainer.visibility = View.VISIBLE
					fullscreenVideoContainer.alpha = 1.0f
					fullscreenVideoContainer.addView(videoView.aspectRatioView)

					wasInLandscape = false

					fullscreenedByButton = byButton

					val parentActivity = parentActivity

					if (parentActivity != null) {
						try {
							prevOrientation = parentActivity.requestedOrientation

							if (byButton) {
								val manager = parentActivity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
								val displayRotation = manager.defaultDisplay.rotation

								if (displayRotation == Surface.ROTATION_270) {
									parentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
								}
								else {
									parentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
								}
							}

							containerView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
				else {
					fullscreenVideoContainer.visibility = View.INVISIBLE
					fullscreenedByButton = false

					val parentActivity = parentActivity

					if (parentActivity != null) {
						try {
							containerView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
							parentActivity.requestedOrientation = prevOrientation
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}

				return null
			}

			override fun onVideoSizeChanged(aspectRatio: Float, rotation: Int) {
				// unused
			}

			override fun onInlineSurfaceTextureReady() {
				if (videoView.isInline) {
					dismissInternal()
				}
			}

			override fun prepareToSwitchInlineMode(inline: Boolean, switchInlineModeRunnable: Runnable, aspectRatio: Float, animated: Boolean) {
				if (inline) {
					val parentActivity = parentActivity

					if (parentActivity != null) {
						try {
							containerView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
							if (prevOrientation != -2) {
								parentActivity.requestedOrientation = prevOrientation
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					if (fullscreenVideoContainer.visibility == View.VISIBLE) {
						containerView.translationY = (containerView.measuredHeight + AndroidUtilities.dp(10f)).toFloat()
						backDrawable.alpha = 0
					}

					setOnShowListener(null)

					if (animated && PipVideoOverlay.IS_TRANSITION_ANIMATION_SUPPORTED) {
						val textureView = videoView.textureView
						val controlsView = videoView.controlsView
						val textureImageView = videoView.textureImageView
						val rect = PipVideoOverlay.getPipRect(true, aspectRatio)
						val scale = rect.width / textureView.width

						val animatorSet = AnimatorSet()
						animatorSet.playTogether(ObjectAnimator.ofFloat(textureImageView, View.SCALE_X, scale), ObjectAnimator.ofFloat(textureImageView, View.SCALE_Y, scale), ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_X, rect.x), ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_Y, rect.y), ObjectAnimator.ofFloat(textureView, View.SCALE_X, scale), ObjectAnimator.ofFloat(textureView, View.SCALE_Y, scale), ObjectAnimator.ofFloat(textureView, View.TRANSLATION_X, rect.x), ObjectAnimator.ofFloat(textureView, View.TRANSLATION_Y, rect.y), ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, (containerView.measuredHeight + AndroidUtilities.dp(10f)).toFloat()), ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0), ObjectAnimator.ofFloat(fullscreenVideoContainer, View.ALPHA, 0f), ObjectAnimator.ofFloat(controlsView, View.ALPHA, 0f))
						animatorSet.interpolator = DecelerateInterpolator()
						animatorSet.setDuration(250)

						animatorSet.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								if (fullscreenVideoContainer.visibility == View.VISIBLE) {
									fullscreenVideoContainer.alpha = 1.0f
									fullscreenVideoContainer.visibility = View.INVISIBLE
								}

								switchInlineModeRunnable.run()
							}
						})

						animatorSet.start()
					}
					else {
						if (fullscreenVideoContainer.visibility == View.VISIBLE) {
							fullscreenVideoContainer.alpha = 1.0f
							fullscreenVideoContainer.visibility = View.INVISIBLE
						}

						switchInlineModeRunnable.run()

						dismissInternal()
					}
				}
				else {
					if (ApplicationLoader.mainInterfacePaused) {
						try {
							parentActivity?.startService(Intent(ApplicationLoader.applicationContext, BringAppForegroundService::class.java))
						}
						catch (e: Throwable) {
							FileLog.e(e)
						}
					}

					if (animated && PipVideoOverlay.IS_TRANSITION_ANIMATION_SUPPORTED) {
						// setOnShowListener(onShowListener)
						val rect = PipVideoOverlay.getPipRect(false, aspectRatio)

						val textureView = videoView.textureView
						val textureImageView = videoView.textureImageView
						val scale = rect.width / textureView.layoutParams.width

						textureImageView.scaleX = scale
						textureImageView.scaleY = scale
						textureImageView.translationX = rect.x
						textureImageView.translationY = rect.y

						textureView.scaleX = scale
						textureView.scaleY = scale
						textureView.translationX = rect.x
						textureView.translationY = rect.y
					}
					else {
						PipVideoOverlay.dismiss()
					}

					setShowWithoutAnimation(true)

					show()

					if (animated) {
						waitingForDraw = 4
						backDrawable.alpha = 1
						containerView.translationY = (containerView.measuredHeight + AndroidUtilities.dp(10f)).toFloat()
					}
				}
			}

			override fun onSwitchInlineMode(controlsView: View, inline: Boolean, videoWidth: Int, videoHeight: Int, rotation: Int, animated: Boolean): TextureView? {
				if (inline) {
					controlsView.translationY = 0f

					val textureView = TextureView(context)

					if (PipVideoOverlay.show(false, parentActivity, textureView, videoWidth, videoHeight)) {
						PipVideoOverlay.setParentSheet(this@EmbedBottomSheet)
						return textureView
					}

					return null
				}

				if (animated) {
					animationInProgress = true

					val view = videoView.aspectRatioView
					view.getLocationInWindow(position)

					position[0] -= leftInset
					position[1] = (position[1] - containerView.translationY).toInt()

					val textureView = videoView.textureView
					val textureImageView = videoView.textureImageView

					val animatorSet = AnimatorSet()
					animatorSet.playTogether(ObjectAnimator.ofFloat(textureImageView, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(textureImageView, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_X, position[0].toFloat()), ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_Y, position[1].toFloat()), ObjectAnimator.ofFloat(textureView, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(textureView, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(textureView, View.TRANSLATION_X, position[0].toFloat()), ObjectAnimator.ofFloat(textureView, View.TRANSLATION_Y, position[1].toFloat()), ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0f), ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 51))
					animatorSet.interpolator = DecelerateInterpolator()
					animatorSet.setDuration(250)

					animatorSet.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							animationInProgress = false
						}
					})

					animatorSet.start()
				}
				else {
					containerView.translationY = 0f
				}

				return null
			}

			override fun onSharePressed() {
				// unused
			}

			override fun onPlayStateChanged(playerView: WebPlayerView, playing: Boolean) {
				if (playing) {
					try {
						parentActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				else {
					try {
						parentActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			override fun checkInlinePermissions(): Boolean {
				return this@EmbedBottomSheet.checkInlinePermissions()
			}

			override fun getTextureViewContainer(): ViewGroup {
				return container
			}
		})

		videoView.visibility = View.INVISIBLE

		containerLayout.addView(videoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, (48 + 36 + (if (hasDescription) 22 else 0) - 10).toFloat()))

		progressBarBlackBackground.setBackgroundColor(-0x1000000)
		progressBarBlackBackground.visibility = View.INVISIBLE

		containerLayout.addView(progressBarBlackBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, (48 + 36 + (if (hasDescription) 22 else 0)).toFloat()))

		progressBar.visibility = View.INVISIBLE

		containerLayout.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 0f, 0f, ((48 + 36 + (if (hasDescription) 22 else 0)) / 2).toFloat()))

		var textView: TextView

		if (hasDescription) {
			textView = TextView(context)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			textView.setTextColor(context.getColor(R.color.text))
			textView.text = description
			textView.isSingleLine = true
			textView.setTypeface(Theme.TYPEFACE_BOLD)
			textView.ellipsize = TextUtils.TruncateAt.END
			textView.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
			containerLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.BOTTOM, 0f, 0f, 0f, (48 + 9 + 20).toFloat()))
		}

		textView = TextView(context)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.setTextColor(context.getColor(R.color.dark_gray))
		textView.text = title
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
		containerLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.BOTTOM, 0f, 0f, 0f, (48 + 9).toFloat()))

		val lineView = View(context)
		lineView.setBackgroundColor(context.getColor(R.color.divider))
		containerLayout.addView(lineView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT or Gravity.BOTTOM))
		(lineView.layoutParams as FrameLayout.LayoutParams).bottomMargin = AndroidUtilities.dp(48f)

		val frameLayout = FrameLayout(context)
		frameLayout.setBackgroundColor(context.getColor(R.color.background))
		containerLayout.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT or Gravity.BOTTOM))

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1f
		frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.RIGHT))

		textView = TextView(context)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.setTextColor(context.getColor(R.color.brand))
		textView.gravity = Gravity.CENTER
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.background = Theme.createSelectorDrawable(context.getColor(R.color.brand), 0)
		textView.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
		textView.text = context.getString(R.string.Close).uppercase(Locale.getDefault())
		textView.setTypeface(Theme.TYPEFACE_BOLD)
		textView.setOnClickListener { dismiss() }

		frameLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		imageButtonsContainer.visibility = View.INVISIBLE

		frameLayout.addView(imageButtonsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))

		pipButton.scaleType = ImageView.ScaleType.CENTER
		pipButton.setImageResource(R.drawable.ic_goinline)
		pipButton.contentDescription = context.getString(R.string.AccDescrPipMode)
		pipButton.isEnabled = false
		pipButton.alpha = 0.5f
		pipButton.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)
		pipButton.background = Theme.createSelectorDrawable(context.getColor(R.color.brand), 0)

		imageButtonsContainer.addView(pipButton, LayoutHelper.createFrame(48, 48f, Gravity.TOP or Gravity.LEFT, 0f, 0f, 4f, 0f))

		pipButton.setOnClickListener {
			if (PipVideoOverlay.isVisible()) {
				PipVideoOverlay.dismiss()
				AndroidUtilities.runOnUIThread({ it.callOnClick() }, 300)
				return@setOnClickListener
			}

			val inAppOnly = isYouTube && "inapp" == MessagesController.getInstance(currentAccount).youtubePipType

			if (!inAppOnly && !checkInlinePermissions()) {
				return@setOnClickListener
			}

			if (progressBar.visibility == View.VISIBLE) {
				return@setOnClickListener
			}

			val animated = false

			if (PipVideoOverlay.show(inAppOnly, parentActivity, webView, width, height)) {
				PipVideoOverlay.setParentSheet(this@EmbedBottomSheet)
			}

			if (isYouTube) {
				runJsCode("hideControls();")
			}

			if (animated && PipVideoOverlay.IS_TRANSITION_ANIMATION_SUPPORTED) {
				animationInProgress = true

				val view = videoView.aspectRatioView
				view.getLocationInWindow(position)
				position[0] -= leftInset
				position[1] = (position[1] - containerView.translationY).toInt()

				val textureView = videoView.textureView
				val textureImageView = videoView.textureImageView

				val animatorSet = AnimatorSet()
				animatorSet.playTogether(ObjectAnimator.ofFloat(textureImageView, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(textureImageView, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_X, position[0].toFloat()), ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_Y, position[1].toFloat()), ObjectAnimator.ofFloat(textureView, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(textureView, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(textureView, View.TRANSLATION_X, position[0].toFloat()), ObjectAnimator.ofFloat(textureView, View.TRANSLATION_Y, position[1].toFloat()), ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0f), ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 51))
				animatorSet.interpolator = DecelerateInterpolator()
				animatorSet.setDuration(250)

				animatorSet.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						animationInProgress = false
					}
				})

				animatorSet.start()
			}
			else {
				containerView.translationY = 0f
			}

			dismissInternal()
		}

		val copyClickListener = View.OnClickListener {
			try {
				val clipboard = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
				val clip = ClipData.newPlainText("label", openUrl)
				clipboard.setPrimaryClip(clip)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			(parentActivity as? LaunchActivity)?.showBulletin { obj -> obj.createCopyLinkBulletin() }

			dismiss()
		}

		val copyButton = ImageView(context)
		copyButton.scaleType = ImageView.ScaleType.CENTER
		copyButton.setImageResource(R.drawable.msg_copy)
		copyButton.contentDescription = context.getString(R.string.CopyLink)
		copyButton.colorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.MULTIPLY)
		copyButton.background = Theme.createSelectorDrawable(context.getColor(R.color.brand), 0)
		copyButton.setOnClickListener(copyClickListener)

		imageButtonsContainer.addView(copyButton, LayoutHelper.createFrame(48, 48, Gravity.TOP or Gravity.LEFT))

		copyTextButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		copyTextButton.setTextColor(context.getColor(R.color.brand))
		copyTextButton.gravity = Gravity.CENTER
		copyTextButton.isSingleLine = true
		copyTextButton.ellipsize = TextUtils.TruncateAt.END
		copyTextButton.background = Theme.createSelectorDrawable(context.getColor(R.color.brand), 0)
		copyTextButton.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
		copyTextButton.text = context.getString(R.string.Copy).uppercase(Locale.getDefault())
		copyTextButton.setTypeface(Theme.TYPEFACE_BOLD)
		copyTextButton.setOnClickListener(copyClickListener)

		linearLayout.addView(copyTextButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		val openInButton = TextView(context)
		openInButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		openInButton.setTextColor(context.getColor(R.color.brand))
		openInButton.gravity = Gravity.CENTER
		openInButton.isSingleLine = true
		openInButton.ellipsize = TextUtils.TruncateAt.END
		openInButton.background = Theme.createSelectorDrawable(context.getColor(R.color.brand), 0)
		openInButton.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
		openInButton.text = context.getString(R.string.OpenInBrowser).uppercase(Locale.getDefault())
		openInButton.setTypeface(Theme.TYPEFACE_BOLD)

		linearLayout.addView(openInButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		openInButton.setOnClickListener {
			Browser.openUrl(parentActivity, openUrl)
			dismiss()
		}

		val canHandleUrl = videoView.canHandleUrl(embedUrl) || videoView.canHandleUrl(originalUrl)

		videoView.visibility = if (canHandleUrl) View.VISIBLE else View.INVISIBLE

		if (canHandleUrl) {
			videoView.willHandle()
		}

		setDelegate(object : BottomSheetDelegate() {
			override fun onOpenAnimationEnd() {
				val handled = canHandleUrl && videoView.loadVideo(embedUrl, null, null, openUrl, true)

				if (handled) {
					progressBar.visibility = View.INVISIBLE
					webView.visibility = View.INVISIBLE
					videoView.visibility = View.VISIBLE
				}
				else {
					progressBar.visibility = View.VISIBLE
					webView.visibility = View.VISIBLE
					imageButtonsContainer.visibility = View.VISIBLE
					copyTextButton.visibility = View.INVISIBLE
					webView.keepScreenOn = true
					videoView.visibility = View.INVISIBLE
					videoView.controlsView.visibility = View.INVISIBLE
					videoView.textureView.visibility = View.INVISIBLE
					videoView.textureImageView?.visibility = View.INVISIBLE

					videoView.loadVideo(null, null, null, null, false)

					val args = mapOf("Referer" to "messenger.ello.team")

					try {
						val currentYoutubeId = videoView.youtubeId

						if (currentYoutubeId != null) {
							progressBarBlackBackground.visibility = View.VISIBLE
							isYouTube = true

							webView.addJavascriptInterface(YoutubeProxy(), "YoutubeProxy")

							var seekToTime = 0

							if (openUrl != null) {
								try {
									val uri = Uri.parse(openUrl)
									var t = if (seekTimeOverride > 0) "" + seekTimeOverride else null

									if (t == null) {
										t = uri.getQueryParameter("t")

										if (t == null) {
											t = uri.getQueryParameter("time_continue")
										}
									}

									if (t != null) {
										if (t.contains("m")) {
											val arg = t.split("m".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

											seekToTime = Utilities.parseInt(arg[0]) * 60 + Utilities.parseInt(arg[1])
										}
										else {
											seekToTime = Utilities.parseInt(t)
										}
									}
								}
								catch (e: Exception) {
									FileLog.e(e)
								}
							}

							webView.loadDataWithBaseURL("https://messenger.ello.team/", String.format(Locale.US, youtubeFrame, currentYoutubeId, seekToTime), "text/html", "UTF-8", "https://youtube.com")
						}
						else {
							webView.loadUrl(embedUrl, args)
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			override fun canDismiss(): Boolean {
				if (videoView.isInFullscreen) {
					videoView.exitFullscreen()
					return false
				}

				try {
					parentActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				return true
			}
		})

		orientationEventListener = object : OrientationEventListener(ApplicationLoader.applicationContext) {
			override fun onOrientationChanged(orientation: Int) {
				if (orientationEventListener == null || videoView.visibility != View.VISIBLE) {
					return
				}
				if (parentActivity != null && videoView.isInFullscreen && fullscreenedByButton) {
					if (orientation >= 270 - 30 && orientation <= 270 + 30) {
						wasInLandscape = true
					}
					else if (wasInLandscape && orientation > 0 && (orientation >= 330 || orientation <= 30)) {
						parentActivity?.requestedOrientation = prevOrientation
						fullscreenedByButton = false
						wasInLandscape = false
					}
				}
			}
		}

		val currentYoutubeId = WebPlayerView.getYouTubeVideoId(embedUrl)

		if (currentYoutubeId != null || !canHandleUrl) {
			progressBar.visibility = View.VISIBLE
			webView.visibility = View.VISIBLE
			imageButtonsContainer.visibility = View.VISIBLE

			if (currentYoutubeId != null) {
				progressBarBlackBackground.visibility = View.VISIBLE
			}

			copyTextButton.visibility = View.INVISIBLE
			webView.keepScreenOn = true

			videoView.visibility = View.INVISIBLE
			videoView.controlsView.visibility = View.INVISIBLE
			videoView.textureView.visibility = View.INVISIBLE
			videoView.textureImageView?.visibility = View.INVISIBLE

			if (currentYoutubeId != null && "disabled" == MessagesController.getInstance(currentAccount).youtubePipType) {
				pipButton.visibility = View.GONE
			}
		}

		if (orientationEventListener?.canDetectOrientation() == true) {
			orientationEventListener?.enable()
		}
		else {
			orientationEventListener?.disable()
			orientationEventListener = null
		}

		instance = this
	}

	private fun runJsCode(code: String) {
		webView.evaluateJavascript(code, null)
	}

	fun checkInlinePermissions(): Boolean {
		val parentActivity = parentActivity ?: return false

		if (Settings.canDrawOverlays(parentActivity)) {
			return true
		}
		else {
			createDrawOverlayPermissionDialog(parentActivity, null)
		}

		return false
	}

	override fun canDismissWithSwipe(): Boolean {
		return videoView.visibility != View.VISIBLE || !videoView.isInFullscreen
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		if (videoView.visibility == View.VISIBLE && videoView.isInitied && !videoView.isInline) {
			if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
				if (!videoView.isInFullscreen) {
					videoView.enterFullscreen()
				}
			}
			else {
				if (videoView.isInFullscreen) {
					videoView.exitFullscreen()
				}
			}
		}
	}

	fun destroy() {
		if (webView.visibility == View.VISIBLE) {
			containerLayout.removeView(webView)

			webView.stopLoading()
			webView.loadUrl("about:blank")
			webView.destroy()
		}

		PipVideoOverlay.dismiss()
		videoView.destroy()
		instance = null
		dismissInternal()
	}

	override fun dismissInternal() {
		super.dismissInternal()

		orientationEventListener?.disable()
		orientationEventListener = null
	}

	fun exitFromPip() {
		if (!PipVideoOverlay.isVisible()) {
			return
		}

		if (ApplicationLoader.mainInterfacePaused) {
			try {
				parentActivity?.startService(Intent(ApplicationLoader.applicationContext, BringAppForegroundService::class.java))
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		if (isYouTube) {
			runJsCode("showControls();")
		}

		val parent = webView.parent as? ViewGroup
		parent?.removeView(webView)

		containerLayout.addView(webView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, (48 + 36 + (if (hasDescription) 22 else 0)).toFloat()))

		setShowWithoutAnimation(true)

		show()

		PipVideoOverlay.dismiss(true)
	}

	fun updateTextureViewPosition() {
		val view = videoView.aspectRatioView
		view.getLocationInWindow(position)
		position[0] -= leftInset

		if (!videoView.isInline && !animationInProgress) {
			val textureView = videoView.textureView
			textureView.translationX = position[0].toFloat()
			textureView.translationY = position[1].toFloat()

			val textureImageView = videoView.textureImageView

			if (textureImageView != null) {
				textureImageView.translationX = position[0].toFloat()
				textureImageView.translationY = position[1].toFloat()
			}
		}

		val controlsView = videoView.controlsView

		if (controlsView.parent === container) {
			controlsView.translationY = position[1].toFloat()
		}
		else {
			controlsView.translationY = 0f
		}
	}

	override fun canDismissWithTouchOutside(): Boolean {
		return fullscreenVideoContainer.visibility != View.VISIBLE
	}

	override fun onContainerTranslationYChanged(translationY: Float) {
		updateTextureViewPosition()
	}

	override fun onCustomMeasure(view: View, width: Int, height: Int): Boolean {
		if (view === videoView.controlsView) {
			val layoutParams = view.layoutParams
			layoutParams.width = videoView.measuredWidth
			layoutParams.height = videoView.aspectRatioView.measuredHeight + (if (videoView.isInFullscreen) 0 else AndroidUtilities.dp(10f))
		}

		return false
	}

	override fun onCustomLayout(view: View, left: Int, top: Int, right: Int, bottom: Int): Boolean {
		if (view === videoView.controlsView) {
			updateTextureViewPosition()
		}

		return false
	}

	fun pause() {
		if (videoView.isInitied) {
			videoView.pause()
		}
	}

	override fun onContainerDraw(canvas: Canvas) {
		if (waitingForDraw != 0) {
			waitingForDraw--

			if (waitingForDraw == 0) {
				videoView.updateTextureImageView()
				PipVideoOverlay.dismiss()
			}
			else {
				container.invalidate()
			}
		}
	}

	companion object {
		@JvmStatic
		@SuppressLint("StaticFieldLeak")
		var instance: EmbedBottomSheet? = null
			private set

		@JvmStatic
		fun show(fragment: BaseFragment, message: MessageObject?, photoViewerProvider: PhotoViewerProvider?, title: String?, description: String?, originalUrl: String?, url: String, w: Int, h: Int, keyboardVisible: Boolean) {
			show(fragment, message, photoViewerProvider, title, description, originalUrl, url, w, h, -1, keyboardVisible)
		}

		fun show(fragment: BaseFragment, message: MessageObject?, photoViewerProvider: PhotoViewerProvider?, title: String?, description: String?, originalUrl: String?, url: String, w: Int, h: Int, seekTime: Int, keyboardVisible: Boolean) {
			instance?.destroy()

			val youtubeId = if (message?.messageOwner?.media?.webpage != null) WebPlayerView.getYouTubeVideoId(url) else null

			if (youtubeId != null || (url.contains("youtube.com") && url.contains("/shorts/"))) {
				PhotoViewer.getInstance().setParentActivity(fragment)
				PhotoViewer.getInstance().openPhoto(message, seekTime, null, 0, 0, photoViewerProvider)
			}
			else {
				val sheet = EmbedBottomSheet(fragment.parentActivity!!, title, description, originalUrl, url, w, h, seekTime)
				sheet.setCalcMandatoryInsets(keyboardVisible)
				sheet.show()
			}
		}
	}
}
