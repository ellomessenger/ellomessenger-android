/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components.voip

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.projection.MediaProjectionManager
import android.os.Parcelable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.messenger.voip.StateListener
import org.telegram.messenger.voip.VideoCapturerDevice
import org.telegram.messenger.voip.VoIPService
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.MotionBackgroundDrawable
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.LaunchActivity
import org.webrtc.RendererCommon
import org.webrtc.RendererCommon.RendererEvents
import java.io.File
import java.io.FileOutputStream

@SuppressLint("AppCompatCustomView")
abstract class PrivateVideoPreviewDialog(context: Context, mic: Boolean, private val needScreencast: Boolean) : FrameLayout(context), StateListener {
	private var isDismissed = false
	private val outProgress = 0f
	private val viewPager = ViewPager(context)
	private val positiveButton: TextView
	private val titlesLayout = LinearLayout(context)
	private var micIconView: RLottieImageView? = null
	private val textureView: VoIPTextureView
	private var currentTexturePage = 1
	private var visibleCameraPage = 1
	private var cameraReady = false
	private var pageOffset = 0f
	private var currentPage = 0

	var micEnabled = false
		private set

	private val titles = (0..<(if (needScreencast) 3 else 2)).map { index ->
		val textView = TextView(context)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
		textView.setTextColor(-0x1)
		textView.setTypeface(Theme.TYPEFACE_BOLD)
		textView.setPadding(AndroidUtilities.dp(10f), 0, AndroidUtilities.dp(10f), 0)
		textView.gravity = Gravity.CENTER_VERTICAL
		textView.isSingleLine = true

		titlesLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT))

		if (index == 0 && needScreencast) {
			textView.text = context.getString(R.string.VoipPhoneScreen)
		}
		else if (index == 0 || index == 1 && needScreencast) {
			textView.text = context.getString(R.string.VoipFrontCamera)
		}
		else {
			textView.text = context.getString(R.string.VoipBackCamera)
		}

		textView.setOnClickListener {
			viewPager.setCurrentItem(index, true)
		}

		textView
	}.toTypedArray()

	init {
		AndroidUtilities.setViewPagerEdgeEffectColor(viewPager, 0x7f000000)

		viewPager.adapter = Adapter()
		viewPager.pageMargin = 0
		viewPager.offscreenPageLimit = 1

		addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		viewPager.addOnPageChangeListener(object : OnPageChangeListener {
			private var scrollState = ViewPager.SCROLL_STATE_IDLE
			private var willSetPage = 0

			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
				currentPage = position
				pageOffset = positionOffset
				updateTitlesLayout()
			}

			override fun onPageSelected(i: Int) {
				if (scrollState == ViewPager.SCROLL_STATE_IDLE) {
					currentTexturePage = if (i <= (if (needScreencast) 1 else 0)) {
						1
					}
					else {
						2
					}

					onFinishMoveCameraPage()
				}
				else {
					willSetPage = if (i <= (if (needScreencast) 1 else 0)) {
						1
					}
					else {
						2
					}
				}
			}

			override fun onPageScrollStateChanged(state: Int) {
				scrollState = state
				if (state == ViewPager.SCROLL_STATE_IDLE) {
					currentTexturePage = willSetPage
					onFinishMoveCameraPage()
				}
			}
		})

		textureView = VoIPTextureView(context, false, false)
		textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
		textureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT
		textureView.clipToTexture = true
		textureView.renderer.alpha = 0f
		textureView.renderer.setRotateTextureWithScreen(true)
		textureView.renderer.setUseCameraRotation(true)

		addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val actionBar = ActionBar(context)
		actionBar.setBackButtonDrawable(BackDrawable(false))
		actionBar.setBackgroundColor(Color.TRANSPARENT)
		actionBar.setItemsColor(Color.WHITE, false)
		actionBar.occupyStatusBar = true

		actionBar.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					dismiss(screencast = false, apply = false)
				}
			}
		})

		addView(actionBar)

		positiveButton = object : TextView(context) {
			private val gradientPaint = titles.indices.map { Paint(Paint.ANTI_ALIAS_FLAG) }

			override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
				super.onSizeChanged(w, h, oldw, oldh)

				for (a in gradientPaint.indices) {
					var color1: Int
					var color2: Int
					var color3: Int

					if (a == 0 && needScreencast) {
						color1 = -0x881aa4
						color2 = -0xa93802
						color3 = 0
					}
					else if (a == 0 || a == 1 && needScreencast) {
						color1 = -0xa85b02
						color2 = -0x899117
						color3 = 0
					}
					else {
						color1 = -0x899117
						color2 = -0xfaba7
						color3 = -0x1b58aa
					}

					val gradient: Shader = if (color3 != 0) {
						LinearGradient(0f, 0f, measuredWidth.toFloat(), 0f, intArrayOf(color1, color2, color3), null, Shader.TileMode.CLAMP)
					}
					else {
						LinearGradient(0f, 0f, measuredWidth.toFloat(), 0f, intArrayOf(color1, color2), null, Shader.TileMode.CLAMP)
					}

					gradientPaint[a].setShader(gradient)
				}
			}

			override fun onDraw(canvas: Canvas) {
				AndroidUtilities.rectTmp[0f, 0f, measuredWidth.toFloat()] = measuredHeight.toFloat()

				gradientPaint[currentPage].alpha = 255

				canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), gradientPaint[currentPage])

				if (pageOffset > 0 && currentPage + 1 < gradientPaint.size) {
					gradientPaint[currentPage + 1].alpha = (255 * pageOffset).toInt()
					canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), gradientPaint[currentPage + 1])
				}

				super.onDraw(canvas)
			}
		}

		positiveButton.setMinWidth(AndroidUtilities.dp(64f))
		positiveButton.setTag(Dialog.BUTTON_POSITIVE)
		positiveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		positiveButton.setTextColor(Color.WHITE)
		positiveButton.setGravity(Gravity.CENTER)
		positiveButton.setTypeface(Theme.TYPEFACE_BOLD)
		positiveButton.setText(context.getString(R.string.VoipShareVideo))
		positiveButton.setForeground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6f), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.3f).toInt())))
		positiveButton.setPadding(0, AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(12f))

		positiveButton.setOnClickListener {
			if (isDismissed) {
				return@setOnClickListener
			}

			if (currentPage == 0 && needScreencast) {
				val mediaProjectionManager = getContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
				(getContext() as? Activity)?.startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), LaunchActivity.SCREEN_CAPTURE_REQUEST_CODE)
			}
			else {
				dismiss(screencast = false, apply = true)
			}
		}

		addView(positiveButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 0f, 0f, 0f, 64f))

		addView(titlesLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 64, Gravity.BOTTOM))

		alpha = 0f
		translationX = AndroidUtilities.dp(32f).toFloat()

		animate().alpha(1f).translationX(0f).setDuration(150).start()

		setWillNotDraw(false)

		val service = VoIPService.sharedInstance

		if (service != null) {
			textureView.renderer.setMirror(service.isFrontFaceCamera())

			textureView.renderer.init(VideoCapturerDevice.getEglBase().eglBaseContext, object : RendererEvents {
				override fun onFirstFrameRendered() {
					// unused
				}

				override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
					// unused
				}
			})

			service.setLocalSink(textureView.renderer, false)
		}

		viewPager.currentItem = if (needScreencast) 1 else 0

		if (mic) {
			micIconView = RLottieImageView(context)
			micIconView?.setPadding(AndroidUtilities.dp(9f), AndroidUtilities.dp(9f), AndroidUtilities.dp(9f), AndroidUtilities.dp(9f))
			micIconView?.background = Theme.createCircleDrawable(AndroidUtilities.dp(48f), ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.3f).toInt()))

			val micIcon = RLottieDrawable(R.raw.voice_mini, "" + R.raw.voice_mini, AndroidUtilities.dp(24f), AndroidUtilities.dp(24f), true, null)

			micIconView?.setAnimation(micIcon)
			micIconView?.scaleType = ImageView.ScaleType.FIT_CENTER

			micEnabled = true

			micIcon.currentFrame = if (micEnabled) 69 else 36

			micIconView?.setOnClickListener {
				micEnabled = !micEnabled

				if (micEnabled) {
					micIcon.currentFrame = 36
					micIcon.setCustomEndFrame(69)
				}
				else {
					micIcon.currentFrame = 69
					micIcon.setCustomEndFrame(99)
				}

				micIcon.start()
			}

			addView(micIconView, LayoutHelper.createFrame(48, 48f, Gravity.LEFT or Gravity.BOTTOM, 24f, 0f, 0f, 136f))
		}
	}

	fun setBottomPadding(padding: Int) {
		var layoutParams = positiveButton.layoutParams as LayoutParams
		layoutParams.bottomMargin = AndroidUtilities.dp(64f) + padding

		layoutParams = titlesLayout.layoutParams as LayoutParams
		layoutParams.bottomMargin = padding
	}

	private fun updateTitlesLayout() {
		val current = titles[currentPage]
		val next: View? = if (currentPage < titles.size - 1) titles[currentPage + 1] else null
		// val cx = (measuredWidth / 2).toFloat()
		val currentCx = (current.left + current.measuredWidth / 2).toFloat()
		var tx = measuredWidth / 2 - currentCx

		if (next != null) {
			val nextCx = (next.left + next.measuredWidth / 2).toFloat()
			tx -= (nextCx - currentCx) * pageOffset
		}

		for (a in titles.indices) {
			var alpha: Float
			var scale: Float

			if (a < currentPage || a > currentPage + 1) {
				alpha = 0.7f
				scale = 0.9f
			}
			else if (a == currentPage) {
				alpha = 1.0f - 0.3f * pageOffset
				scale = 1.0f - 0.1f * pageOffset
			}
			else {
				alpha = 0.7f + 0.3f * pageOffset
				scale = 0.9f + 0.1f * pageOffset
			}

			titles[a].alpha = alpha
			titles[a].scaleX = scale
			titles[a].scaleY = scale
		}

		titlesLayout.translationX = tx
		positiveButton.invalidate()

		if (needScreencast && currentPage == 0 && pageOffset <= 0) {
			textureView.visibility = INVISIBLE
		}
		else {
			textureView.visibility = VISIBLE

			if (currentPage + (if (needScreencast) 0 else 1) == currentTexturePage) {
				textureView.translationX = -pageOffset * measuredWidth
			}
			else {
				textureView.translationX = (1.0f - pageOffset) * measuredWidth
			}
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		VoIPService.sharedInstance?.registerStateListener(this)
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		VoIPService.sharedInstance?.unregisterStateListener(this)
	}

	private fun onFinishMoveCameraPage() {
		val service = VoIPService.sharedInstance

		if (currentTexturePage == visibleCameraPage || service == null) {
			return
		}

		val currentFrontFace = service.isFrontFaceCamera()

		if (currentTexturePage == 1 && !currentFrontFace || currentTexturePage == 2 && currentFrontFace) {
			saveLastCameraBitmap()
			cameraReady = false
			service.switchCamera()
			textureView.alpha = 0.0f
		}

		visibleCameraPage = currentTexturePage
	}

	private fun saveLastCameraBitmap() {
		if (!cameraReady) {
			return
		}

		runCatching {
			var bitmap = textureView.renderer.bitmap

			if (bitmap != null) {
				val newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, textureView.renderer.matrix, true)
				bitmap.recycle()

				bitmap = newBitmap

				val lastBitmap = Bitmap.createScaledBitmap(bitmap, 80, (bitmap.height / (bitmap.width / 80.0f)).toInt(), true)

				if (lastBitmap != bitmap) {
					bitmap.recycle()
				}

				Utilities.blurBitmap(lastBitmap, 7, 1, lastBitmap.width, lastBitmap.height, lastBitmap.rowBytes)

				val file = File(ApplicationLoader.filesDirFixed, "cthumb$visibleCameraPage.jpg")

				FileOutputStream(file).use {
					lastBitmap.compress(Bitmap.CompressFormat.JPEG, 87, it)
				}

				val view = viewPager.findViewWithTag<View>(visibleCameraPage - (if (needScreencast) 0 else 1))

				if (view is ImageView) {
					view.setImageBitmap(lastBitmap)
				}
			}
		}
	}

	override fun onCameraFirstFrameAvailable() {
		if (!cameraReady) {
			cameraReady = true
			textureView.animate().alpha(1f).setDuration(250)
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		updateTitlesLayout()
	}

	fun dismiss(screencast: Boolean, apply: Boolean) {
		if (isDismissed) {
			return
		}

		isDismissed = true

		saveLastCameraBitmap()
		onDismiss(screencast, apply)

		animate().alpha(0f).translationX(AndroidUtilities.dp(32f).toFloat()).setDuration(150).setListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				super.onAnimationEnd(animation)
				(parent as? ViewGroup)?.removeView(this@PrivateVideoPreviewDialog)
			}
		})

		invalidate()
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		return true
	}

	protected open fun onDismiss(screencast: Boolean, apply: Boolean) {
		// stub
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val isLandscape = MeasureSpec.getSize(widthMeasureSpec) > MeasureSpec.getSize(heightMeasureSpec)
		var marginLayoutParams = positiveButton.layoutParams as MarginLayoutParams

		if (isLandscape) {
			marginLayoutParams.leftMargin = AndroidUtilities.dp(80f)
			marginLayoutParams.rightMargin = marginLayoutParams.leftMargin
		}
		else {
			marginLayoutParams.leftMargin = AndroidUtilities.dp(16f)
			marginLayoutParams.rightMargin = marginLayoutParams.leftMargin
		}

		micIconView?.let { micIconView ->
			marginLayoutParams = micIconView.layoutParams as MarginLayoutParams

			if (isLandscape) {
				marginLayoutParams.leftMargin = AndroidUtilities.dp(88f)
				marginLayoutParams.rightMargin = marginLayoutParams.leftMargin
			}
			else {
				marginLayoutParams.leftMargin = AndroidUtilities.dp(24f)
				marginLayoutParams.rightMargin = marginLayoutParams.leftMargin
			}
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		measureChildWithMargins(titlesLayout, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f), MeasureSpec.EXACTLY), 0)
	}

	val backgroundColor: Int
		get() {
			return ColorUtils.setAlphaComponent(context.getColor(R.color.dark_fixed), (255 * (alpha * (1f - outProgress))).toInt())
		}

	override fun invalidate() {
		super.invalidate()
		(parent as? View)?.invalidate()
	}

	override fun onCameraSwitch(isFrontFace: Boolean) {
		update()
	}

	fun update() {
		VoIPService.sharedInstance?.let {
			textureView.renderer.setMirror(it.isFrontFaceCamera())
		}
	}

	private inner class Adapter : PagerAdapter() {
		override fun getCount(): Int {
			return titles.size
		}

		override fun instantiateItem(container: ViewGroup, position: Int): Any {
			val view: View

			if (needScreencast && position == 0) {
				val frameLayout = FrameLayout(context)
				frameLayout.background = MotionBackgroundDrawable(-0xded1c6, -0xd4a4b3, -0xdba79d, -0xd8baa8, true)

				view = frameLayout

				val imageView = ImageView(context)
				imageView.scaleType = ImageView.ScaleType.CENTER
				imageView.setImageResource(R.drawable.screencast_big)

				frameLayout.addView(imageView, LayoutHelper.createFrame(82, 82f, Gravity.CENTER, 0f, 0f, 0f, 60f))

				val textView = TextView(context)
				textView.text = context.getString(R.string.VoipVideoPrivateScreenSharing)
				textView.gravity = Gravity.CENTER
				textView.setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1.0f)
				textView.setTextColor(-0x1)
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
				textView.typeface = Theme.TYPEFACE_BOLD
				frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 21f, 28f, 21f, 0f))
			}
			else {
				val imageView = ImageView(context)
				imageView.tag = position

				val bitmap = runCatching {
					val file = File(ApplicationLoader.filesDirFixed, "cthumb" + (if (position == 0 || position == 1 && needScreencast) 1 else 2) + ".jpg")
					BitmapFactory.decodeFile(file.absolutePath)
				}.getOrNull()

				if (bitmap != null) {
					imageView.setImageBitmap(bitmap)
				}
				else {
					imageView.setImageResource(R.drawable.icplaceholder)
				}

				imageView.scaleType = ImageView.ScaleType.FIT_XY

				view = imageView
			}

			(view.parent as? ViewGroup)?.removeView(view)

			container.addView(view, 0)

			return view
		}

		override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
			container.removeView(`object` as View)
		}

		override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
			super.setPrimaryItem(container, position, `object`)
		}

		override fun isViewFromObject(view: View, `object`: Any): Boolean {
			return view == `object`
		}

		override fun restoreState(arg0: Parcelable?, arg1: ClassLoader?) {
			// unused
		}

		override fun saveState(): Parcelable? {
			return null
		}
	}
}
