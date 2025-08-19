/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.thumbs

@SuppressLint("AppCompatCustomView")
open class RLottieImageView(context: Context) : ImageView(context) {
	private var layerColors: HashMap<String, Int>? = null
	private var imageReceiver: ImageReceiver? = null
	private var autoRepeat = false
	private var attachedToWindow = false
	private var playing = false
	private var startOnAttach = false

	var animatedDrawable: RLottieDrawable? = null
		private set

	fun clearLayerColors() {
		layerColors?.clear()
	}

	fun setLayerColor(layer: String, color: Int) {
		if (layerColors == null) {
			layerColors = HashMap()
		}

		layerColors?.put(layer, color)

		animatedDrawable?.setLayerColor(layer, color)
	}

	fun replaceColors(colors: IntArray?) {
		animatedDrawable?.replaceColors(colors)
	}

	fun setAnimation(resId: Int, w: Int, h: Int) {
		setAnimation(resId, w, h, null)
	}

	fun setAnimation(resId: Int, w: Int, h: Int, colorReplacement: IntArray?) {
		setAnimation(RLottieDrawable(resId, "" + resId, AndroidUtilities.dp(w.toFloat()), AndroidUtilities.dp(h.toFloat()), false, colorReplacement))
	}

	fun setOnAnimationEndListener(r: Runnable?) {
		animatedDrawable?.setOnAnimationEndListener(r)
	}

	fun setAnimation(lottieDrawable: RLottieDrawable?) {
		if (animatedDrawable === lottieDrawable) {
			return
		}

		imageReceiver?.onDetachedFromWindow()
		imageReceiver = null

		animatedDrawable = lottieDrawable
		animatedDrawable?.setMasterParent(this)

		if (autoRepeat) {
			animatedDrawable?.setAutoRepeat(1)
		}

		layerColors?.let {
			animatedDrawable?.beginApplyLayerColors()

			for ((key, value) in it) {
				animatedDrawable?.setLayerColor(key, value)
			}

			animatedDrawable?.commitApplyLayerColors()
		}

		animatedDrawable?.setAllowDecodeSingleFrame(true)

		setImageDrawable(animatedDrawable)
	}

	fun setAnimation(document: TLRPC.Document?, w: Int, h: Int) {
		imageReceiver?.onDetachedFromWindow()
		imageReceiver = null

		if (document == null) {
			return
		}

		imageReceiver = ImageReceiver()

		if ("video/webm" == document.mimeType) {
			val thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)
			imageReceiver?.setImage(ImageLocation.getForDocument(document), w.toString() + "_" + h + "_pcache_" + ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(thumb, document), null, null, document.size, null, document, 1)
		}
		else {
			var thumbDrawable: Drawable? = null
			val probableCacheKey = document.id.toString() + "@" + w + "_" + h

			if (!ImageLoader.getInstance().hasLottieMemCache(probableCacheKey)) {
				val svgThumb = DocumentObject.getSvgThumb(document.thumbs, ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), 0.2f)
				svgThumb?.overrideWidthAndHeight(512, 512)
				thumbDrawable = svgThumb
			}

			val thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)

			imageReceiver?.setImage(ImageLocation.getForDocument(document), w.toString() + "_" + h, ImageLocation.getForDocument(thumb, document), null, null, null, thumbDrawable, 0, null, document, 1)
		}

		imageReceiver?.isAspectFit = true
		imageReceiver?.setParentView(this)
		imageReceiver?.setAutoRepeat(1)
		imageReceiver?.setAllowStartLottieAnimation(true)
		imageReceiver?.allowStartAnimation = true
		imageReceiver?.clip = false

		setImageDrawable(object : Drawable() {
			override fun draw(canvas: Canvas) {
				AndroidUtilities.rectTmp2.set(bounds)
				AndroidUtilities.rectTmp2.inset(AndroidUtilities.dp(11f), AndroidUtilities.dp(11f))

				imageReceiver?.setImageCoordinates(AndroidUtilities.rectTmp2)
				imageReceiver?.draw(canvas)
			}

			override fun setAlpha(alpha: Int) {
				imageReceiver?.alpha = alpha / 255f
			}

			override fun setColorFilter(colorFilter: ColorFilter?) {
				imageReceiver?.setColorFilter(colorFilter)
			}

			@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
			override fun getOpacity(): Int {
				return PixelFormat.TRANSPARENT
			}
		})

		if (attachedToWindow) {
			imageReceiver?.onAttachedToWindow()
		}
	}

	fun clearAnimationDrawable() {
		animatedDrawable?.stop()
		imageReceiver?.onDetachedFromWindow()
		imageReceiver = null
		animatedDrawable = null
		setImageDrawable(null)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		attachedToWindow = true
		imageReceiver?.onAttachedToWindow()
		animatedDrawable?.callback = this

		if (playing) {
			animatedDrawable?.start()
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attachedToWindow = false
		animatedDrawable?.stop()
		imageReceiver?.onDetachedFromWindow()
		imageReceiver = null
	}

	fun isPlaying(): Boolean {
		return animatedDrawable?.isRunning() == true
	}

	fun setAutoRepeat(repeat: Boolean) {
		autoRepeat = repeat
	}

	fun setProgress(progress: Float) {
		animatedDrawable?.setProgress(progress)
	}

	override fun setImageResource(resId: Int) {
		super.setImageResource(resId)
		animatedDrawable = null
	}

	fun playAnimation() {
		if (animatedDrawable == null) {
			return
		}

		playing = true

		if (attachedToWindow) {
			animatedDrawable?.start()
			imageReceiver?.startAnimation()
		}
		else {
			startOnAttach = true
		}
	}

	fun stopAnimation() {
		if (animatedDrawable == null) {
			return
		}

		playing = false

		if (attachedToWindow) {
			animatedDrawable?.stop()
			imageReceiver?.stopAnimation()
		}
		else {
			startOnAttach = false
		}
	}
}
