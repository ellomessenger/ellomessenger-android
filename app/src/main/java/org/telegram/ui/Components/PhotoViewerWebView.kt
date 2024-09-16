/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.google.android.exoplayer2.ExoPlayer
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BringAppForegroundService
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.utils.isYouTubeShortsLink
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.ui.PhotoViewer
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

open class PhotoViewerWebView @SuppressLint("SetJavaScriptEnabled") constructor(private val photoViewer: PhotoViewer, context: Context, private val pipItem: View) : FrameLayout(context) {
	private val currentAccount = UserConfig.selectedAccount
	private val progressBar = RadialProgressView(context)
	private var currentWebpage: WebPage? = null
	private var playbackSpeed = 0f
	private var setPlaybackSpeed = false
	private var youtubeStoryboardsSpecUrl: String? = null
	private val youtubeStoryboards = mutableListOf<String>()
	private var currentYoutubeId: String? = null
	private var isTouchDisabled = false

	private val progressBarBlackBackground = object : View(context) {
		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)
			drawBlackBackground(canvas, measuredWidth, measuredHeight)
		}
	}

	@JvmField
	val webView: WebView

	private val isYoutubeShorts: Boolean
		get() = currentWebpage?.embed_url?.isYouTubeShortsLink() == true

	var isYouTube: Boolean = false
		private set

	var isPlaying: Boolean = false
		private set

	var videoDuration: Int = 0
		private set

	var currentPosition: Int = 0
		private set

	var bufferedPosition: Float = 0f
		private set

	private val progressRunnable: Runnable by lazy {
		Runnable {
			if (isYouTube) {
				runJsCode("pollPosition();")
			}

			if (isPlaying) {
				AndroidUtilities.runOnUIThread(progressRunnable, 500)
			}
		}
	}

	private inner class YoutubeProxy {
		@JavascriptInterface
		fun onPlayerLoaded() {
			AndroidUtilities.runOnUIThread {
				progressBar.visibility = INVISIBLE

				if (setPlaybackSpeed) {
					setPlaybackSpeed = false
					setPlaybackSpeed(playbackSpeed)
				}

				pipItem.isEnabled = true
				pipItem.alpha = 1.0f

				photoViewer.checkFullscreenButton()
			}
		}

		@JavascriptInterface
		fun onPlayerStateChange(state: String) {
			val stateInt = state.toInt()
			val wasPlaying = isPlaying

			isPlaying = stateInt == YT_PLAYING || stateInt == YT_BUFFERING

			checkPlayingPoll(wasPlaying)

			val exoState: Int
			val playWhenReady: Boolean

			when (stateInt) {
				YT_NOT_STARTED -> {
					exoState = ExoPlayer.STATE_IDLE
					playWhenReady = false
				}

				YT_PLAYING -> {
					exoState = ExoPlayer.STATE_READY
					playWhenReady = true
				}

				YT_PAUSED -> {
					exoState = ExoPlayer.STATE_READY
					playWhenReady = false
				}

				YT_BUFFERING -> {
					exoState = ExoPlayer.STATE_BUFFERING
					playWhenReady = true
				}

				YT_COMPLETED -> {
					exoState = ExoPlayer.STATE_ENDED
					playWhenReady = false
				}

				else -> {
					exoState = ExoPlayer.STATE_IDLE
					playWhenReady = false
				}
			}

			if (exoState == ExoPlayer.STATE_READY) {
				if (progressBarBlackBackground.visibility != INVISIBLE) {
					AndroidUtilities.runOnUIThread({
						progressBarBlackBackground.visibility = INVISIBLE
					}, 300)
				}
			}

			AndroidUtilities.runOnUIThread {
				photoViewer.updateWebPlayerState(playWhenReady, exoState)
			}
		}

		@JavascriptInterface
		fun onPlayerNotifyDuration(duration: Int) {
			videoDuration = duration * 1000
			processYoutubeStoryboards(youtubeStoryboardsSpecUrl)
			youtubeStoryboardsSpecUrl = null
		}

		@JavascriptInterface
		fun onPlayerNotifyCurrentPosition(position: Int) {
			currentPosition = position * 1000
		}

		@JavascriptInterface
		fun onPlayerNotifyBufferedPosition(position: Float) {
			bufferedPosition = position
		}
	}

	init {
		webView = object : WebView(context) {
			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				processTouch(event)
				return super.onTouchEvent(event)
			}

			override fun draw(canvas: Canvas) {
				super.draw(canvas)

				if (PipVideoOverlay.getInnerView() === this && progressBarBlackBackground.visibility == VISIBLE) {
					canvas.drawColor(Color.BLACK)
					drawBlackBackground(canvas, width, height)
				}
			}
		}

		webView.getSettings().apply {
			javaScriptEnabled = true
			domStorageEnabled = true
			mediaPlaybackRequiresUserGesture = false
			mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
		}

		val cookieManager = CookieManager.getInstance()
		cookieManager.setAcceptThirdPartyCookies(webView, true)

		webView.setWebViewClient(object : WebViewClient() {
			override fun onPageFinished(view: WebView, url: String) {
				super.onPageFinished(view, url)

				if (!isYouTube) {
					progressBar.visibility = INVISIBLE

					progressBarBlackBackground.visibility = INVISIBLE

					pipItem.isEnabled = true
					pipItem.alpha = 1.0f
				}
			}

			override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
				if (!VideoSeekPreviewImage.IS_YOUTUBE_PREVIEWS_SUPPORTED) {
					return null
				}

				val url = request.url.toString()

				if (isYouTube && url.startsWith("https://www.youtube.com/youtubei/v1/player?key=")) {
					Utilities.externalNetworkQueue.postRunnable {
						try {
							val con = URL(url).openConnection() as HttpURLConnection
							con.requestMethod = "POST"

							for ((key, value) in request.requestHeaders) {
								con.addRequestProperty(key, value)
							}

							con.doOutput = true

							val out = con.outputStream
							out.write(JSONObject().put("context", JSONObject().put("client", JSONObject().put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.0.0 Safari/537.36,gzip(gfe)").put("clientName", "WEB").put("clientVersion", request.requestHeaders["X-Youtube-Client-Version"]).put("osName", "Windows").put("osVersion", "10.0").put("originalUrl", "https://www.youtube.com/watch?v=$currentYoutubeId").put("platform", "DESKTOP"))).put("videoId", currentYoutubeId).toString().toByteArray(charset("UTF-8")))
							out.close()

							val `in` = if (con.responseCode == 200) con.inputStream else con.errorStream
							val buffer = ByteArray(10240)
							var c: Int
							val bos = ByteArrayOutputStream()

							while ((`in`.read(buffer).also { c = it }) != -1) {
								bos.write(buffer, 0, c)
							}

							bos.close()

							`in`.close()

							val str = bos.toString("UTF-8")
							val obj = JSONObject(str)
							val storyboards = obj.optJSONObject("storyboards")

							if (storyboards != null) {
								val renderer = storyboards.optJSONObject("playerStoryboardSpecRenderer")

								if (renderer != null) {
									val spec = renderer.optString("spec")

									if (!spec.isNullOrEmpty()) {
										if (videoDuration == 0) {
											youtubeStoryboardsSpecUrl = spec
										}
										else {
											processYoutubeStoryboards(spec)
										}
									}
								}
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}

				return null
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

		addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		progressBarBlackBackground.setBackgroundColor(-0x1000000)
		progressBarBlackBackground.visibility = INVISIBLE

		addView(progressBarBlackBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		progressBar.visibility = INVISIBLE

		addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
	}

	fun hasYoutubeStoryboards(): Boolean {
		return youtubeStoryboards.isNotEmpty()
	}

	private fun processYoutubeStoryboards(url: String?) {
		if (url == null) {
			return
		}

		val duration = videoDuration / 1000

		youtubeStoryboards.clear()

		if (duration <= 15) {
			return
		}

		val specParts = url.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		val baseUrl = specParts[0].split("\\$".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] + "2/"
		val sgpPart = specParts[0].split("\\\$N".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]

		val sighPart = when (specParts.size) {
			3 -> specParts[2].split("M#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
			2 -> specParts[1].split("t#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
			else -> specParts[3].split("M#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
		}

		val boardsCount = if (duration <= 100) {
			ceil((duration / 25f).toDouble()).toInt()
		}
		else if (duration <= 250) {
			ceil(((duration / 2f) / 25).toDouble()).toInt()
		}
		else if (duration <= 500) {
			ceil(((duration / 4f) / 25).toDouble()).toInt()
		}
		else if (duration <= 1000) {
			ceil(((duration / 5f) / 25).toDouble()).toInt()
		}
		else {
			ceil(((duration / 10f) / 25).toDouble()).toInt()
		}

		for (i in 0 until boardsCount) {
			youtubeStoryboards.add(String.format(Locale.ROOT, "%sM%d%s&sigh=%s", baseUrl, i, sgpPart, sighPart))
		}
	}

	fun getYoutubeStoryboardImageCount(position: Int): Int {
		val index = youtubeStoryboards.indexOf(getYoutubeStoryboard(position))

		if (index != -1) {
			if (index == youtubeStoryboards.size - 1) {
				val duration = videoDuration / 1000

				val totalImages = if (duration <= 100) {
					ceil(duration.toDouble()).toInt()
				}
				else if (duration <= 250) {
					ceil((duration / 2f).toDouble()).toInt()
				}
				else if (duration <= 500) {
					ceil((duration / 4f).toDouble()).toInt()
				}
				else if (duration <= 1000) {
					ceil((duration / 5f).toDouble()).toInt()
				}
				else {
					ceil((duration / 10f).toDouble()).toInt()
				}

				return min(25.0, (totalImages - (youtubeStoryboards.size - 1) * 25 + 1).toDouble()).toInt()
			}

			return 25
		}

		return 0
	}

	fun getYoutubeStoryboard(position: Int): String? {
		val duration = videoDuration / 1000

		val i = if (duration <= 100) {
			(position / 25f).toInt()
		}
		else if (duration <= 250) {
			(position / 2f).toInt() / 25
		}
		else if (duration <= 500) {
			(position / 4f).toInt() / 25
		}
		else if (duration <= 1000) {
			(position / 5f).toInt() / 25
		}
		else {
			((position / 10f) / 25).toInt()
		}

		return if (i < youtubeStoryboards.size) youtubeStoryboards[i] else null
	}

	fun getYoutubeStoryboardImageIndex(position: Int): Int {
		val duration = videoDuration / 1000

		val i = if (duration <= 100) {
			ceil(position.toDouble()).toInt() % 25
		}
		else if (duration <= 250) {
			ceil((position / 2f).toDouble()).toInt() % 25
		}
		else if (duration <= 500) {
			ceil((position / 4f).toDouble()).toInt() % 25
		}
		else if (duration <= 1000) {
			ceil((position / 5f).toDouble()).toInt() % 25
		}
		else {
			ceil((position / 10f).toDouble()).toInt() % 25
		}

		return i
	}

	fun setTouchDisabled(touchDisabled: Boolean) {
		isTouchDisabled = touchDisabled
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		if (isTouchDisabled) {
			return false
		}

		return super.dispatchTouchEvent(ev)
	}

	private fun checkPlayingPoll(wasPlaying: Boolean) {
		if (!wasPlaying && isPlaying) {
			AndroidUtilities.runOnUIThread(progressRunnable, 500)
		}
		else if (wasPlaying && !isPlaying) {
			AndroidUtilities.cancelRunOnUIThread(progressRunnable)
		}
	}

	@JvmOverloads
	fun seekTo(seekTo: Long, seekAhead: Boolean = true) {
		val playing = isPlaying

		currentPosition = seekTo.toInt()

		if (playing) {
			pauseVideo()
		}

		if (playing) {
			AndroidUtilities.runOnUIThread({
				runJsCode("seekTo(" + Math.round(seekTo / 1000f) + ", " + seekAhead + ");")
				AndroidUtilities.runOnUIThread({ this.playVideo() }, 100)
			}, 100)
		}
		else {
			runJsCode("seekTo(" + Math.round(seekTo / 1000f) + ", " + seekAhead + ");")
		}
	}

	private fun runJsCode(code: String) {
		webView.evaluateJavascript(code, null)
	}

	protected open fun processTouch(event: MotionEvent) {
		// stub
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (webView.parent === this) {
			val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
			val viewHeight = MeasureSpec.getSize(heightMeasureSpec)
			var w = 100
			var h = 100

			currentWebpage?.embed_url?.let { url ->
				if (url.contains("youtube.com") && url.contains("/shorts/")) {
					w = viewWidth
					h = viewHeight
				}
				else {
					w = currentWebpage?.embed_width?.takeIf { it != 0 } ?: 100
					h = currentWebpage?.embed_height?.takeIf { it != 0 } ?: 100
				}
			}

			val minScale = min((viewWidth / w.toFloat()).toDouble(), (viewHeight / h.toFloat()).toDouble()).toFloat()

			val layoutParams = webView.layoutParams as LayoutParams
			layoutParams.width = (w * minScale).toInt()
			layoutParams.height = (h * minScale).toInt()
			layoutParams.topMargin = (viewHeight - layoutParams.height) / 2
			layoutParams.leftMargin = (viewWidth - layoutParams.width) / 2
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
	}

	protected open fun drawBlackBackground(canvas: Canvas?, w: Int, h: Int) {
		// stub
	}

	val isLoaded: Boolean
		get() = progressBar.visibility != VISIBLE

	val isInAppOnly: Boolean
		get() = isYouTube && "inapp" == MessagesController.getInstance(currentAccount).youtubePipType

	fun openInPip(): Boolean {
		val inAppOnly = isInAppOnly

		if (!inAppOnly && !checkInlinePermissions()) {
			return false
		}

		if (progressBar.visibility == VISIBLE) {
			return false
		}

		if (PipVideoOverlay.isVisible()) {
			PipVideoOverlay.dismiss()
			AndroidUtilities.runOnUIThread({ this.openInPip() }, 300)
			return true
		}

		progressBarBlackBackground.visibility = VISIBLE

		if (PipVideoOverlay.show(inAppOnly, context as Activity, this, webView, currentWebpage?.embed_width ?: 0, currentWebpage?.embed_height ?: 0, false)) {
			PipVideoOverlay.setPhotoViewer(PhotoViewer.getInstance())
		}

		return true
	}

	val isControllable: Boolean
		get() = isYouTube

	fun showControls() {
		// TODO: Show controls after leaving PIP
	}

	fun hideControls() {
		// TODO: Hide controls in PIP
	}

	fun playVideo() {
		if (isPlaying || !isControllable) {
			return
		}

		runJsCode("playVideo();")

		isPlaying = true

		checkPlayingPoll(false)
	}

	fun pauseVideo() {
		if (!isPlaying || !isControllable) {
			return
		}

		runJsCode("pauseVideo();")

		isPlaying = false

		checkPlayingPoll(true)
	}

	fun setPlaybackSpeed(speed: Float) {
		playbackSpeed = speed

		if (progressBar.visibility != VISIBLE) {
			if (isYouTube) {
				runJsCode("setPlaybackSpeed($speed);")
			}
		}
		else {
			setPlaybackSpeed = true
		}
	}

	@SuppressLint("AddJavascriptInterface")
	fun init(seekTime: Int, webPage: WebPage) {
		currentWebpage = webPage

		currentYoutubeId = WebPlayerView.getYouTubeVideoId(webPage.embed_url)

		val originalUrl = webPage.url

		requestLayout()

		try {
			if (currentYoutubeId != null) {
				progressBarBlackBackground.visibility = VISIBLE
				isYouTube = true

				webView.addJavascriptInterface(YoutubeProxy(), "YoutubeProxy")

				var seekToTime = 0

				if (originalUrl != null) {
					try {
						val uri = Uri.parse(originalUrl)
						var t = if (seekTime > 0) "" + seekTime else null

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

				val `in` = context.assets.open("youtube_embed.html")
				val bos = ByteArrayOutputStream()
				val buffer = ByteArray(10240)
				var c: Int

				while ((`in`.read(buffer).also { c = it }) != -1) {
					bos.write(buffer, 0, c)
				}

				bos.close()
				`in`.close()

				webView.loadDataWithBaseURL("https://messenger.ello.team/", String.format(Locale.US, bos.toString("UTF-8"), currentYoutubeId, seekToTime), "text/html", "UTF-8", "https://youtube.com")
			}
			else {
				webView.loadUrl(webPage.embed_url, mapOf("Referer" to "messenger.ello.team"))
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		if (isYoutubeShorts) {
			progressBarBlackBackground.visibility = VISIBLE
		}

		pipItem.isEnabled = false
		pipItem.alpha = 0.5f

		progressBar.visibility = VISIBLE

		if (currentYoutubeId != null) {
			progressBarBlackBackground.visibility = VISIBLE
		}

		webView.visibility = VISIBLE
		webView.keepScreenOn = true

		if (currentYoutubeId != null && "disabled" == MessagesController.getInstance(currentAccount).youtubePipType) {
			pipItem.visibility = GONE
		}
	}

	fun checkInlinePermissions(): Boolean {
		if (Settings.canDrawOverlays(context)) {
			return true
		}
		else {
			AlertsCreator.createDrawOverlayPermissionDialog((context as Activity), null)
		}

		return false
	}

	fun exitFromPip() {
		if (ApplicationLoader.mainInterfacePaused) {
			try {
				context.startService(Intent(ApplicationLoader.applicationContext, BringAppForegroundService::class.java))
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		progressBarBlackBackground.visibility = VISIBLE

		val parent = webView.parent as? ViewGroup
		parent?.removeView(webView)

		addView(webView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		PipVideoOverlay.dismiss()
	}

	fun release() {
		webView.stopLoading()
		webView.loadUrl("about:blank")
		webView.destroy()

		videoDuration = 0
		currentPosition = 0

		AndroidUtilities.cancelRunOnUIThread(progressRunnable)
	}

	companion object {
		private const val YT_NOT_STARTED = -1
		private const val YT_COMPLETED = 0
		private const val YT_PLAYING = 1
		private const val YT_PAUSED = 2
		private const val YT_BUFFERING = 3
	}
}
