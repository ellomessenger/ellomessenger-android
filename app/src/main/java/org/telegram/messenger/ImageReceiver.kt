/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.messenger

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.annotation.Keep
import org.telegram.messenger.Emoji.EmojiDrawable
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.SvgHelper.SvgDrawable
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.Components.AnimatedFileDrawable
import org.telegram.ui.Components.LoadingStickerDrawable
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RecyclableDrawable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

open class ImageReceiver @JvmOverloads constructor(private var parentView: View? = null) : NotificationCenterDelegate {
	val drawRegion = RectF()
	private var roundRadius = IntArray(4)
	private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
	private val roundRect = RectF()
	private val shaderMatrix = Matrix()
	private val roundPath = Path()
	val loadingOperations = ArrayList<Runnable>()
	var clip = true

	@JvmField
	var animatedFileDrawableRepeatMaxCount = 0

	var orientation = 0
		protected set

	var currentAccount = UserConfig.selectedAccount
	var param = 0

	var parentObject: Any? = null
		private set

	private var canceledLoading = false
	var isForceLoading = false
	private var currentTime: Long = 0
	var fileLoadingPriority = FileLoader.PRIORITY_NORMAL
	private var currentLayerNum = 0
	private var currentOpenedLayerFlags = 0
	private var setImageBackup: SetImageBackup? = null
	private var blendMode: Any? = null
	private var gradientBitmap: Bitmap? = null
	private var gradientShader: BitmapShader? = null
	private var composeShader: ComposeShader? = null
	private var legacyBitmap: Bitmap? = null
	private var legacyShader: BitmapShader? = null
	private var legacyCanvas: Canvas? = null
	private var legacyPaint: Paint? = null
	var strippedLocation: ImageLocation? = null

	var imageLocation: ImageLocation? = null
		private set

	var imageFilter: String? = null
		private set

	var imageKey: String? = null
		private set

	private var imageTag = 0
	private var currentImageDrawable: Drawable? = null
	private var imageShader: BitmapShader? = null

	var thumbLocation: ImageLocation? = null
		private set

	var thumbFilter: String? = null
		private set

	var thumbKey: String? = null
		private set

	private var thumbTag = 0
	private var currentThumbDrawable: Drawable? = null
	private var thumbShader: BitmapShader? = null
	private var thumbOrientation = 0

	var mediaLocation: ImageLocation? = null
		private set

	var mediaFilter: String? = null
		private set

	var mediaKey: String? = null
		private set

	private var mediaTag = 0
	private var currentMediaDrawable: Drawable? = null
	private var mediaShader: BitmapShader? = null
	private var useRoundForThumb = true

	var staticThumb: Drawable? = null
		private set

	var ext: String? = null
		private set

	private var ignoreImageSet = false
	private var currentGuid = 0

	var size: Long = 0
		private set

	var cacheType = 0
		private set

	private var allowLottieVibration = true
	var allowStartAnimation = true
	private var allowStartLottieAnimation = true
	private var useSharedAnimationQueue = false
	private var allowDecodeSingleFrame = false
	private var autoRepeat = 1
	private var autoRepeatCount = -1
	private var autoRepeatTimeout: Long = 0
	private var animationReadySent = false
	private var crossfadeWithOldImage = false
	private var crossfadingWithThumb = false
	private var crossfadeImage: Drawable? = null
	private var crossfadeKey: String? = null
	private var crossfadeShader: BitmapShader? = null
	var isNeedsQualityThumb = false
	var isShouldGenerateQualityThumb = false
	var qualityThumbDocument: TLRPC.Document? = null

	var isCurrentKeyQuality = false
		private set

	var imageX = 0f
		private set

	var imageY = 0f

	var imageWidth = 0f
		private set

	var imageHeight = 0f
		private set

	private var sideClip = 0f

	var visible = true
		private set

	var isAspectFit = false
	var isForcePreview = false
	private var forceCrossfade = false
	private var isRoundRect = true

	@get:Keep
	@set:Keep
	var alpha = 1.0f

	private var isPressed = 0
	private var centerRotation = false
	private var delegate: ImageReceiverDelegate? = null

	@get:Keep
	@set:Keep
	var currentAlpha = 0f

	private var previousAlpha = 1f
	private var lastUpdateAlphaTime: Long = 0
	private var crossfadeAlpha: Byte = 1
	private var manualAlphaAnimator = false
	private var crossfadeWithThumb = false
	private var colorFilter: ColorFilter? = null
	private var isRoundVideo = false
	private var startTime: Long = 0
	private var endTime: Long = 0
	private var crossfadeDuration = DEFAULT_CROSSFADE_DURATION
	private var pressedProgress = 0f
	private var animateFromIsPressed = 0
	var uniqueKeyPrefix: String? = null

	var isAttachedToWindow = false
		private set

	private var videoThumbIsSame = false
	private var allowLoadingOnAttachedOnly = false
	private var skipUpdateFrame = false

	fun cancelLoadImage() {
		isForceLoading = false
		ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true)
		canceledLoading = true
	}

	fun setIgnoreImageSet(value: Boolean) {
		ignoreImageSet = value
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumb: Drawable?, ext: String?, parentObject: Any?, cacheType: Int) {
		setImage(imageLocation, imageFilter, null, null, thumb, 0, ext, parentObject, cacheType)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumb: Drawable?, size: Long, ext: String?, parentObject: Any?, cacheType: Int) {
		setImage(imageLocation, imageFilter, null, null, thumb, size, ext, parentObject, cacheType)
	}

	fun setImage(imagePath: String?, imageFilter: String?, thumb: Drawable?, ext: String?, size: Long) {
		setImage(ImageLocation.getForPath(imagePath), imageFilter, null, null, thumb, size, ext, null, 1)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, ext: String?, parentObject: Any?, cacheType: Int) {
		setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, 0, ext, parentObject, cacheType)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, size: Long, ext: String?, parentObject: Any?, cacheType: Int) {
		setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, size, ext, parentObject, cacheType)
	}

	fun setForUserOrChat(`object`: TLObject?, avatarDrawable: Drawable?) {
		setForUserOrChat(`object`, avatarDrawable, null)
	}

	fun setForUserOrChat(`object`: TLObject?, avatarDrawable: Drawable?, parentObject: Any?) {
		setForUserOrChat(`object`, avatarDrawable, parentObject, false)
	}

	fun setForUserOrChat(`object`: TLObject?, avatarDrawable: Drawable?, parentObject: Any?, animationEnabled: Boolean) {
		@Suppress("NAME_SHADOWING") val parentObject = parentObject ?: `object`

		setUseRoundForThumbDrawable(true)

		var strippedBitmap: BitmapDrawable? = null
		var hasStripped = false
		var videoLocation: ImageLocation? = null

		if (`object` is User) {
			if (`object`.photo != null) {
				strippedBitmap = `object`.photo?.strippedBitmap
				hasStripped = `object`.photo?.stripped_thumb != null

				if (animationEnabled && MessagesController.getInstance(currentAccount).isPremiumUser(`object`) && `object`.photo?.has_video == true) {
					val userFull = MessagesController.getInstance(currentAccount).getUserFull(`object`.id)

					if (userFull == null) {
						MessagesController.getInstance(currentAccount).loadFullUser(`object`, currentGuid, false)
					}
					else {
						val videoSizes = userFull.profile_photo?.video_sizes

						if (!videoSizes.isNullOrEmpty()) {
							var videoSize = videoSizes[0]

							for (size in videoSizes) {
								if ("p" == size.type) {
									videoSize = size
									break
								}
							}

							videoLocation = ImageLocation.getForPhoto(videoSize, userFull.profile_photo)
						}
					}
				}
			}
		}
		else if (`object` is Chat) {
			if (`object`.photo != null) {
				strippedBitmap = `object`.photo.strippedBitmap
				hasStripped = `object`.photo.stripped_thumb != null
			}
		}

		val location = ImageLocation.getForUserOrChat(`object`, ImageLocation.TYPE_SMALL)
		val filter = "50_50"

		if (videoLocation != null) {
			setImage(videoLocation, "avatar", location, filter, null, null, strippedBitmap, 0, null, parentObject, 0)
			animatedFileDrawableRepeatMaxCount = 3
		}
		else {
			if (strippedBitmap != null) {
				setImage(location, filter, strippedBitmap, null, parentObject, 0)
			}
			else if (hasStripped) {
				setImage(location, filter, ImageLocation.getForUserOrChat(`object`, ImageLocation.TYPE_STRIPPED), "50_50_b", avatarDrawable, parentObject, 0)
			}
			else {
				setImage(location, filter, avatarDrawable, null, parentObject, 0)
			}
		}
	}

	fun setImage(fileLocation: ImageLocation?, fileFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, thumb: Drawable?, parentObject: Any?, cacheType: Int) {
		setImage(null, null, fileLocation, fileFilter, thumbLocation, thumbFilter, thumb, 0, null, parentObject, cacheType)
	}

	fun setImage(fileLocation: ImageLocation?, fileFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, thumb: Drawable?, size: Long, ext: String?, parentObject: Any?, cacheType: Int) {
		setImage(null, null, fileLocation, fileFilter, thumbLocation, thumbFilter, thumb, size, ext, parentObject, cacheType)
	}

	fun setImage(mediaLocation: ImageLocation?, mediaFilter: String?, imageLocation: ImageLocation?, imageFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, thumb: Drawable?, size: Long, ext: String?, parentObject: Any?, cacheType: Int) {
		@Suppress("NAME_SHADOWING") var mediaLocation = mediaLocation
		@Suppress("NAME_SHADOWING") var imageLocation = imageLocation

		if (allowLoadingOnAttachedOnly && !isAttachedToWindow) {
			if (setImageBackup == null) {
				setImageBackup = SetImageBackup()
			}

			setImageBackup?.mediaLocation = mediaLocation
			setImageBackup?.mediaFilter = mediaFilter
			setImageBackup?.imageLocation = imageLocation
			setImageBackup?.imageFilter = imageFilter
			setImageBackup?.thumbLocation = thumbLocation
			setImageBackup?.thumbFilter = thumbFilter
			setImageBackup?.thumb = thumb
			setImageBackup?.size = size
			setImageBackup?.ext = ext
			setImageBackup?.cacheType = cacheType
			setImageBackup?.parentObject = parentObject

			return
		}

		if (ignoreImageSet) {
			return
		}

		if (crossfadeWithOldImage && setImageBackup?.isWebfileSet == true) {
			setBackupImage()
		}

		setImageBackup?.clear()

		if (imageLocation == null && thumbLocation == null && mediaLocation == null) {
			for (a in 0..3) {
				recycleBitmap(null, a)
			}

			this.imageLocation = null
			this.imageFilter = null
			imageKey = null
			this.mediaLocation = null
			this.mediaFilter = null
			mediaKey = null
			this.thumbLocation = null
			this.thumbFilter = null
			thumbKey = null
			currentMediaDrawable = null
			mediaShader = null
			currentImageDrawable = null
			imageShader = null
			composeShader = null
			thumbShader = null
			crossfadeShader = null
			legacyShader = null
			legacyCanvas = null

			legacyBitmap?.recycle()
			legacyBitmap = null

			this.ext = ext
			this.parentObject = null
			this.cacheType = 0
			roundPaint.shader = null
			staticThumb = thumb
			currentAlpha = 1.0f
			previousAlpha = 1f
			this.size = 0

			(staticThumb as? SvgDrawable)?.setParent(this)

			ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true)

			invalidate()

			delegate?.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumb != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, false)

			return
		}

		var imageKey = imageLocation?.getKey(parentObject, null, false)

		if (imageKey == null && imageLocation != null) {
			imageLocation = null
		}

		animatedFileDrawableRepeatMaxCount = max(autoRepeatCount, 0)
		isCurrentKeyQuality = false

		if (imageKey == null && isNeedsQualityThumb && (parentObject is MessageObject || qualityThumbDocument != null)) {
			val document = qualityThumbDocument ?: (parentObject as? MessageObject)?.document

			if (document != null && document.dc_id != 0 && document.id != 0L) {
				imageKey = "q_" + document.dc_id + "_" + document.id
				isCurrentKeyQuality = true
			}
		}

		if (imageKey != null && imageFilter != null) {
			imageKey += "@$imageFilter"
		}

		if (uniqueKeyPrefix != null) {
			imageKey = uniqueKeyPrefix + imageKey
		}

		var mediaKey = mediaLocation?.getKey(parentObject, null, false)

		if (mediaKey == null && mediaLocation != null) {
			mediaLocation = null
		}

		if (mediaKey != null && mediaFilter != null) {
			mediaKey += "@$mediaFilter"
		}

		if (uniqueKeyPrefix != null) {
			mediaKey = uniqueKeyPrefix + mediaKey
		}

		if (mediaKey == null && this.imageKey != null && this.imageKey == imageKey || this.mediaKey != null && this.mediaKey == mediaKey) {
			delegate?.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumb != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, false)

			if (!canceledLoading) {
				return
			}
		}

		var strippedLoc = if (strippedLocation != null) {
			strippedLocation
		}
		else {
			mediaLocation ?: imageLocation
		}

		if (strippedLoc == null) {
			strippedLoc = thumbLocation
		}

		var thumbKey = thumbLocation?.getKey(parentObject, strippedLoc, false)

		if (thumbKey != null && thumbFilter != null) {
			thumbKey += "@$thumbFilter"
		}

		if (crossfadeWithOldImage) {
			if (currentMediaDrawable != null) {
				(currentMediaDrawable as? AnimatedFileDrawable)?.let {
					it.stop()
					it.removeParent(this)
				}

				recycleBitmap(thumbKey, TYPE_THUMB)
				recycleBitmap(null, TYPE_CROSSFADE)
				recycleBitmap(mediaKey, TYPE_IMAGE)

				crossfadeImage = currentMediaDrawable
				crossfadeShader = mediaShader
				crossfadeKey = this.imageKey
				crossfadingWithThumb = false
				currentMediaDrawable = null
			}
			else if (currentImageDrawable != null) {
				recycleBitmap(thumbKey, TYPE_THUMB)
				recycleBitmap(null, TYPE_CROSSFADE)
				recycleBitmap(mediaKey, TYPE_MEDIA)

				crossfadeShader = imageShader
				crossfadeImage = currentImageDrawable
				crossfadeKey = this.imageKey
				crossfadingWithThumb = false
				currentImageDrawable = null
			}
			else if (currentThumbDrawable != null) {
				recycleBitmap(imageKey, TYPE_IMAGE)
				recycleBitmap(null, TYPE_CROSSFADE)
				recycleBitmap(mediaKey, TYPE_MEDIA)

				crossfadeShader = thumbShader
				crossfadeImage = currentThumbDrawable
				crossfadeKey = this.thumbKey
				crossfadingWithThumb = false
				currentThumbDrawable = null
			}
			else if (staticThumb != null) {
				recycleBitmap(imageKey, TYPE_IMAGE)
				recycleBitmap(thumbKey, TYPE_THUMB)
				recycleBitmap(null, TYPE_CROSSFADE)
				recycleBitmap(mediaKey, TYPE_MEDIA)

				crossfadeShader = thumbShader
				crossfadeImage = staticThumb
				crossfadingWithThumb = false
				crossfadeKey = null
				currentThumbDrawable = null
			}
			else {
				recycleBitmap(imageKey, TYPE_IMAGE)
				recycleBitmap(thumbKey, TYPE_THUMB)
				recycleBitmap(null, TYPE_CROSSFADE)
				recycleBitmap(mediaKey, TYPE_MEDIA)

				crossfadeShader = null
			}
		}
		else {
			recycleBitmap(imageKey, TYPE_IMAGE)
			recycleBitmap(thumbKey, TYPE_THUMB)
			recycleBitmap(null, TYPE_CROSSFADE)
			recycleBitmap(mediaKey, TYPE_MEDIA)

			crossfadeShader = null
		}

		this.imageLocation = imageLocation
		this.imageFilter = imageFilter
		this.imageKey = imageKey
		this.mediaLocation = mediaLocation
		this.mediaFilter = mediaFilter
		this.mediaKey = mediaKey
		this.thumbLocation = thumbLocation
		this.thumbFilter = thumbFilter
		this.thumbKey = thumbKey
		this.parentObject = parentObject
		this.ext = ext
		this.size = size
		this.cacheType = cacheType
		staticThumb = thumb
		imageShader = null
		composeShader = null
		thumbShader = null
		mediaShader = null
		legacyShader = null
		legacyCanvas = null
		roundPaint.shader = null

		legacyBitmap?.recycle()
		legacyBitmap = null

		currentAlpha = 1.0f
		previousAlpha = 1f

		(staticThumb as? SvgDrawable)?.setParent(this)

		updateDrawableRadius(staticThumb)

		delegate?.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumb != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, false)

		loadImage()

		isRoundVideo = parentObject is MessageObject && parentObject.isRoundVideo
	}

	private fun loadImage() {
		ImageLoader.getInstance().loadImageForImageReceiver(this)
		invalidate()
	}

	fun canInvertBitmap(): Boolean {
		return currentMediaDrawable is ExtendedBitmapDrawable || currentImageDrawable is ExtendedBitmapDrawable || currentThumbDrawable is ExtendedBitmapDrawable || staticThumb is ExtendedBitmapDrawable
	}

	fun setColorFilter(filter: ColorFilter?) {
		colorFilter = filter
	}

	fun setDelegate(delegate: ImageReceiverDelegate?) {
		this.delegate = delegate
	}

	fun getPressed(): Boolean {
		return isPressed != 0
	}

	fun setPressed(value: Int) {
		isPressed = value
	}

	fun setOrientation(angle: Int, center: Boolean) {
		@Suppress("NAME_SHADOWING") var angle = angle

		while (angle < 0) {
			angle += 360
		}

		while (angle > 360) {
			angle -= 360
		}

		thumbOrientation = angle
		orientation = thumbOrientation
		centerRotation = center
	}

	val animatedOrientation: Int
		get() = animation?.orientation ?: 0

	fun setLayerNum(value: Int) {
		currentLayerNum = value

		if (isAttachedToWindow) {
			currentOpenedLayerFlags = NotificationCenter.globalInstance.currentHeavyOperationFlags
			currentOpenedLayerFlags = currentOpenedLayerFlags and currentLayerNum.inv()
		}
	}

	fun setImageBitmap(bitmap: Bitmap?) {
		setImageBitmap(if (bitmap != null) BitmapDrawable(null, bitmap) else null)
	}

	fun setImageBitmap(bitmap: Drawable?) {
		ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true)

		if (crossfadeWithOldImage) {
			if (currentImageDrawable != null) {
				recycleBitmap(null, TYPE_THUMB)
				recycleBitmap(null, TYPE_CROSSFADE)
				recycleBitmap(null, TYPE_MEDIA)

				crossfadeShader = imageShader
				crossfadeImage = currentImageDrawable
				crossfadeKey = imageKey
				crossfadingWithThumb = true
			}
			else if (currentThumbDrawable != null) {
				recycleBitmap(null, TYPE_IMAGE)
				recycleBitmap(null, TYPE_CROSSFADE)
				recycleBitmap(null, TYPE_MEDIA)

				crossfadeShader = thumbShader
				crossfadeImage = currentThumbDrawable
				crossfadeKey = thumbKey
				crossfadingWithThumb = true
			}
			else if (staticThumb != null) {
				recycleBitmap(null, TYPE_IMAGE)
				recycleBitmap(null, TYPE_THUMB)
				recycleBitmap(null, TYPE_CROSSFADE)
				recycleBitmap(null, TYPE_MEDIA)

				crossfadeShader = thumbShader
				crossfadeImage = staticThumb
				crossfadingWithThumb = true
				crossfadeKey = null
			}
			else {
				for (a in 0..3) {
					recycleBitmap(null, a)
				}

				crossfadeShader = null
			}
		}
		else {
			for (a in 0..3) {
				recycleBitmap(null, a)
			}
		}

		if (staticThumb is RecyclableDrawable) {
			val drawable = staticThumb as RecyclableDrawable
			drawable.recycle()
		}

		if (bitmap is AnimatedFileDrawable) {
			val fileDrawable = bitmap
			fileDrawable.setParentView(parentView)

			if (isAttachedToWindow) {
				fileDrawable.addParent(this)
			}

			fileDrawable.setUseSharedQueue(useSharedAnimationQueue || fileDrawable.isWebmSticker)

			if (allowStartAnimation && currentOpenedLayerFlags == 0) {
				fileDrawable.checkRepeat()
			}

			fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame)
		}
		else if (bitmap is RLottieDrawable) {
			val fileDrawable = bitmap

			if (isAttachedToWindow) {
				fileDrawable.addParentView(this)
			}

			fileDrawable.setAllowVibration(allowLottieVibration)

			if (allowStartLottieAnimation && (!fileDrawable.isHeavyDrawable || currentOpenedLayerFlags == 0)) {
				fileDrawable.start()
			}

			fileDrawable.setAllowDecodeSingleFrame(true)
		}

		thumbShader = null
		roundPaint.shader = null
		staticThumb = bitmap
		updateDrawableRadius(bitmap)
		mediaLocation = null
		mediaFilter = null

		(currentMediaDrawable as? AnimatedFileDrawable)?.removeParent(this)

		currentMediaDrawable = null
		mediaKey = null
		mediaShader = null
		imageLocation = null
		imageFilter = null
		currentImageDrawable = null
		imageKey = null
		imageShader = null
		composeShader = null
		legacyShader = null
		legacyCanvas = null

		legacyBitmap?.recycle()
		legacyBitmap = null

		thumbLocation = null
		thumbFilter = null
		thumbKey = null
		isCurrentKeyQuality = false
		ext = null
		size = 0
		cacheType = 0
		currentAlpha = 1f
		previousAlpha = 1f

		setImageBackup?.clear()

		delegate?.didSetImage(this, currentThumbDrawable != null || staticThumb != null, thumb = true, memCache = false)

		invalidate()

		if (forceCrossfade && crossfadeWithOldImage && crossfadeImage != null) {
			currentAlpha = 0.0f
			lastUpdateAlphaTime = System.currentTimeMillis()
			crossfadeWithThumb = currentThumbDrawable != null || staticThumb != null
		}
	}

	private fun setDrawableShader(drawable: Drawable, shader: BitmapShader?) {
		if (drawable === currentThumbDrawable || drawable === staticThumb) {
			thumbShader = shader
		}
		else if (drawable === currentMediaDrawable) {
			mediaShader = shader
		}
		else if (drawable === currentImageDrawable) {
			imageShader = shader

			if (gradientShader != null && drawable is BitmapDrawable) {
				if (Build.VERSION.SDK_INT >= 28) {
					composeShader = ComposeShader(gradientShader!!, imageShader!!, PorterDuff.Mode.DST_IN)
				}
				else {
					val bitmapDrawable = drawable
					val w = bitmapDrawable.bitmap.width
					val h = bitmapDrawable.bitmap.height

					if (legacyBitmap == null || legacyBitmap!!.width != w || legacyBitmap!!.height != h) {
						legacyBitmap?.recycle()

						legacyBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
							legacyCanvas = Canvas(it)
							legacyShader = BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
						}

						if (legacyPaint == null) {
							legacyPaint = Paint()
							legacyPaint?.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
						}
					}
				}
			}
		}
	}

	private fun hasRoundRadius(): Boolean {        /*for (int a = 0; a < roundRadius.length; a++) {
            if (roundRadius[a] != 0) {
                return true;
            }
        }*/
		return true
	}

	private fun updateDrawableRadius(drawable: Drawable?) {
		if (drawable == null) {
			return
		}

		if ((hasRoundRadius() || gradientShader != null) && drawable is BitmapDrawable) {
			val bitmapDrawable = drawable

			if (bitmapDrawable is RLottieDrawable) {
				// unused
			}
			else if (bitmapDrawable is AnimatedFileDrawable) {
				val animatedFileDrawable = drawable as AnimatedFileDrawable
				animatedFileDrawable.setRoundRadius(roundRadius)
			}
			else if (bitmapDrawable.bitmap != null) {
				setDrawableShader(drawable, BitmapShader(bitmapDrawable.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
			}
		}
		else {
			setDrawableShader(drawable, null)
		}
	}

	fun clearImage() {
		for (a in 0..3) {
			recycleBitmap(null, a)
		}

		ImageLoader.getInstance().cancelLoadingForImageReceiver(this, true)
	}

	fun onDetachedFromWindow() {
		isAttachedToWindow = false

		if (imageLocation != null || mediaLocation != null || thumbLocation != null || staticThumb != null) {
			if (setImageBackup == null) {
				setImageBackup = SetImageBackup()
			}

			setImageBackup?.mediaLocation = mediaLocation
			setImageBackup?.mediaFilter = mediaFilter
			setImageBackup?.imageLocation = imageLocation
			setImageBackup?.imageFilter = imageFilter
			setImageBackup?.thumbLocation = thumbLocation
			setImageBackup?.thumbFilter = thumbFilter
			setImageBackup?.thumb = staticThumb
			setImageBackup?.size = size
			setImageBackup?.ext = ext
			setImageBackup?.cacheType = cacheType
			setImageBackup?.parentObject = parentObject
		}

		NotificationCenter.globalInstance.let {
			it.removeObserver(this, NotificationCenter.didReplacedPhotoInMemCache)
			it.removeObserver(this, NotificationCenter.stopAllHeavyOperations)
			it.removeObserver(this, NotificationCenter.startAllHeavyOperations)
		}

		if (staticThumb != null) {
			staticThumb = null
			thumbShader = null
			roundPaint.shader = null
		}

		clearImage()

		if (isPressed == 0) {
			pressedProgress = 0f
		}

		animation?.removeParent(this)

		lottieAnimation?.removeParentView(this)
	}

	private fun setBackupImage(): Boolean {
		setImageBackup?.takeIf { it.isSet }?.let { temp ->
			setImageBackup = null

			setImage(temp.mediaLocation, temp.mediaFilter, temp.imageLocation, temp.imageFilter, temp.thumbLocation, temp.thumbFilter, temp.thumb, temp.size, temp.ext, temp.parentObject, temp.cacheType)

			temp.clear()

			setImageBackup = temp

			val lottieDrawable = lottieAnimation
			lottieDrawable?.setAllowVibration(allowLottieVibration)

			if (lottieDrawable != null && allowStartLottieAnimation && (!lottieDrawable.isHeavyDrawable || currentOpenedLayerFlags == 0)) {
				lottieDrawable.start()
			}

			return true
		}

		return false
	}

	fun onAttachedToWindow(): Boolean {
		isAttachedToWindow = true
		currentOpenedLayerFlags = NotificationCenter.globalInstance.currentHeavyOperationFlags
		currentOpenedLayerFlags = currentOpenedLayerFlags and currentLayerNum.inv()

		NotificationCenter.globalInstance.let {
			it.addObserver(this, NotificationCenter.didReplacedPhotoInMemCache)
			it.addObserver(this, NotificationCenter.stopAllHeavyOperations)
			it.addObserver(this, NotificationCenter.startAllHeavyOperations)
		}

		if (setBackupImage()) {
			return true
		}

		val lottieDrawable = lottieAnimation

		if (lottieDrawable != null) {
			lottieDrawable.addParentView(this)
			lottieDrawable.setAllowVibration(allowLottieVibration)
		}

		if (lottieDrawable != null && allowStartLottieAnimation && (!lottieDrawable.isHeavyDrawable || currentOpenedLayerFlags == 0)) {
			lottieDrawable.start()
		}

		val animatedFileDrawable = animation
		animatedFileDrawable?.addParent(this)

		if (animatedFileDrawable != null && allowStartAnimation && currentOpenedLayerFlags == 0) {
			animatedFileDrawable.checkRepeat()
			invalidate()
		}

		if (NotificationCenter.globalInstance.isAnimationInProgress) {
			didReceivedNotification(NotificationCenter.stopAllHeavyOperations, currentAccount, 512)
		}

		return false
	}

	private fun drawDrawable(canvas: Canvas, drawable: Drawable, alpha: Int, shader: BitmapShader?, orientation: Int, backgroundThreadDrawHolder: BackgroundThreadDrawHolder?) {
		if (isPressed == 0 && pressedProgress != 0f) {
			pressedProgress -= 16 / 150f

			if (pressedProgress < 0) {
				pressedProgress = 0f
			}

			invalidate()
		}

		if (isPressed != 0) {
			pressedProgress = 1f
			animateFromIsPressed = isPressed
		}

		if (pressedProgress == 0f || pressedProgress == 1f) {
			drawDrawable(canvas, drawable, alpha, shader, orientation, isPressed, backgroundThreadDrawHolder)
		}
		else {
			drawDrawable(canvas, drawable, alpha, shader, orientation, isPressed, backgroundThreadDrawHolder)
			drawDrawable(canvas, drawable, (alpha * pressedProgress).toInt(), shader, orientation, animateFromIsPressed, backgroundThreadDrawHolder)
		}
	}

	fun setUseRoundForThumbDrawable(value: Boolean) {
		useRoundForThumb = value
	}

	private fun drawDrawable(canvas: Canvas, drawable: Drawable, alpha: Int, shader: BitmapShader?, orientation: Int, isPressed: Int, backgroundThreadDrawHolder: BackgroundThreadDrawHolder?) {
		val imageX: Float
		val imageY: Float
		val imageH: Float
		val imageW: Float
		val drawRegion: RectF
		val colorFilter: ColorFilter?
		val roundRadius: IntArray

		if (backgroundThreadDrawHolder != null) {
			imageX = backgroundThreadDrawHolder.imageX
			imageY = backgroundThreadDrawHolder.imageY
			imageH = backgroundThreadDrawHolder.imageH
			imageW = backgroundThreadDrawHolder.imageW
			drawRegion = backgroundThreadDrawHolder.drawRegion
			colorFilter = backgroundThreadDrawHolder.colorFilter
			roundRadius = backgroundThreadDrawHolder.roundRadius
		}
		else {
			imageX = this.imageX
			imageY = this.imageY
			imageH = imageHeight
			imageW = imageWidth
			drawRegion = this.drawRegion
			colorFilter = this.colorFilter
			roundRadius = this.roundRadius
		}

		if (drawable is BitmapDrawable) {
			val bitmapDrawable = drawable

			if (drawable is RLottieDrawable) {
				drawable.skipFrameUpdate = skipUpdateFrame
			}
			else if (drawable is AnimatedFileDrawable) {
				drawable.skipFrameUpdate = skipUpdateFrame
			}

			val paint = if (shader != null) {
				roundPaint
			}
			else {
				bitmapDrawable.paint
			}

			if (Build.VERSION.SDK_INT >= 29) {
				if (blendMode != null && gradientShader == null) {
					paint?.blendMode = blendMode as? BlendMode
				}
				else {
					paint?.blendMode = null
				}
			}

			val hasFilter = paint != null && paint.colorFilter != null

			if (hasFilter && isPressed == 0) {
				if (shader != null) {
					roundPaint.colorFilter = null
				}
				else if (staticThumb !== drawable) {
					bitmapDrawable.colorFilter = null
				}
			}
			else if (!hasFilter && isPressed != 0) {
				if (isPressed == 1) {
					if (shader != null) {
						roundPaint.colorFilter = selectedColorFilter
					}
					else {
						bitmapDrawable.colorFilter = selectedColorFilter
					}
				}
				else {
					if (shader != null) {
						roundPaint.colorFilter = selectedGroupColorFilter
					}
					else {
						bitmapDrawable.colorFilter = selectedGroupColorFilter
					}
				}
			}

			if (colorFilter != null && gradientShader == null) {
				if (shader != null) {
					roundPaint.colorFilter = colorFilter
				}
				else {
					bitmapDrawable.colorFilter = colorFilter
				}
			}

			var bitmapW: Int
			var bitmapH: Int

			if (bitmapDrawable is AnimatedFileDrawable || bitmapDrawable is RLottieDrawable) {
				if (orientation % 360 == 90 || orientation % 360 == 270) {
					bitmapW = bitmapDrawable.intrinsicHeight
					bitmapH = bitmapDrawable.intrinsicWidth
				}
				else {
					bitmapW = bitmapDrawable.intrinsicWidth
					bitmapH = bitmapDrawable.intrinsicHeight
				}
			}
			else {
				val bitmap = bitmapDrawable.bitmap

				if (bitmap != null && bitmap.isRecycled) {
					return
				}

				if (orientation % 360 == 90 || orientation % 360 == 270) {
					bitmapW = bitmap!!.height
					bitmapH = bitmap.width
				}
				else {
					bitmapW = bitmap!!.width
					bitmapH = bitmap.height
				}
			}

			val realImageW = imageW - sideClip * 2
			val realImageH = imageH - sideClip * 2
			val scaleW = if (imageW == 0f) 1.0f else bitmapW.toFloat() / realImageW
			val scaleH = if (imageH == 0f) 1.0f else bitmapH.toFloat() / realImageH

			if (shader != null && backgroundThreadDrawHolder == null) {
				if (isAspectFit) {
					val scale = max(scaleW, scaleH)

					bitmapW = (bitmapW / scale).toInt()
					bitmapH = (bitmapH / scale).toInt()

					drawRegion.set(imageX + (imageW - bitmapW) / 2, imageY + (imageH - bitmapH) / 2, imageX + (imageW + bitmapW) / 2, imageY + (imageH + bitmapH) / 2)

					if (visible) {
						shaderMatrix.reset()
						shaderMatrix.setTranslate(drawRegion.left.toInt().toFloat(), drawRegion.top.toInt().toFloat())
						shaderMatrix.preScale(1.0f / scale, 1.0f / scale)

						shader.setLocalMatrix(shaderMatrix)

						roundPaint.shader = shader
						roundPaint.alpha = alpha
						roundRect.set(drawRegion)

						if (isRoundRect) {
							try {
								if (roundRadius[0] == 0) {
									canvas.drawRect(roundRect, roundPaint)
								}
								else {
									canvas.drawRoundRect(roundRect, roundRadius[0].toFloat(), roundRadius[0].toFloat(), roundPaint)
								}
							}
							catch (e: Exception) {
								onBitmapException(bitmapDrawable)
								FileLog.e(e)
							}
						}
						else {
							for (a in roundRadius.indices) {
								radii[a * 2] = roundRadius[a].toFloat()
								radii[a * 2 + 1] = roundRadius[a].toFloat()
							}

							roundPath.reset()
							roundPath.addRoundRect(roundRect, radii, Path.Direction.CW)
							roundPath.close()

							canvas.drawPath(roundPath, roundPaint)
						}
					}
				}
				else {
					if (legacyCanvas != null) {
						roundRect.set(0f, 0f, legacyBitmap!!.width.toFloat(), legacyBitmap!!.height.toFloat())
						legacyCanvas?.drawBitmap(gradientBitmap!!, null, roundRect, null)
						legacyCanvas?.drawBitmap(bitmapDrawable.bitmap, null, roundRect, legacyPaint)
					}

					if (shader === imageShader && gradientShader != null) {
						if (composeShader != null) {
							roundPaint.shader = composeShader
						}
						else {
							roundPaint.shader = legacyShader
						}
					}
					else {
						roundPaint.shader = shader
					}

					var scale = 1.0f / min(scaleW, scaleH)

					roundRect.set(imageX + sideClip, imageY + sideClip, imageX + imageW - sideClip, imageY + imageH - sideClip)

					if (abs(scaleW - scaleH) > 0.0005f) {
						if (bitmapW / scaleH > realImageW) {
							bitmapW = (bitmapW / scaleH).toInt()
							drawRegion.set(imageX - (bitmapW - realImageW) / 2, imageY, imageX + (bitmapW + realImageW) / 2, imageY + realImageH)
						}
						else {
							bitmapH = (bitmapH / scaleW).toInt()
							drawRegion.set(imageX, imageY - (bitmapH - realImageH) / 2, imageX + realImageW, imageY + (bitmapH + realImageH) / 2)
						}
					}
					else {
						drawRegion.set(imageX, imageY, imageX + realImageW, imageY + realImageH)
					}

					if (visible) {
						shaderMatrix.reset()
						shaderMatrix.setTranslate((drawRegion.left + sideClip).toInt().toFloat(), (drawRegion.top + sideClip).toInt().toFloat())

						when (orientation) {
							90 -> {
								shaderMatrix.preRotate(90f)
								shaderMatrix.preTranslate(0f, -drawRegion.width())
							}

							180 -> {
								shaderMatrix.preRotate(180f)
								shaderMatrix.preTranslate(-drawRegion.width(), -drawRegion.height())
							}

							270 -> {
								shaderMatrix.preRotate(270f)
								shaderMatrix.preTranslate(-drawRegion.height(), 0f)
							}
						}

						shaderMatrix.preScale(scale, scale)

						if (isRoundVideo) {
							val postScale = (realImageW + AndroidUtilities.roundMessageInset * 2) / realImageW
							shaderMatrix.postScale(postScale, postScale, drawRegion.centerX(), drawRegion.centerY())
						}

						legacyShader?.setLocalMatrix(shaderMatrix)

						shader.setLocalMatrix(shaderMatrix)

						if (composeShader != null) {
							var bitmapW2 = gradientBitmap!!.width
							var bitmapH2 = gradientBitmap!!.height

							val scaleW2 = if (imageW == 0f) 1.0f else bitmapW2 / realImageW
							val scaleH2 = if (imageH == 0f) 1.0f else bitmapH2 / realImageH

							if (abs(scaleW2 - scaleH2) > 0.0005f) {
								if (bitmapW2 / scaleH2 > realImageW) {
									bitmapW2 = (bitmapW2 / scaleH2).toInt()
									drawRegion.set(imageX - (bitmapW2 - realImageW) / 2, imageY, imageX + (bitmapW2 + realImageW) / 2, imageY + realImageH)
								}
								else {
									bitmapH2 = (bitmapH2 / scaleW2).toInt()
									drawRegion.set(imageX, imageY - (bitmapH2 - realImageH) / 2, imageX + realImageW, imageY + (bitmapH2 + realImageH) / 2)
								}
							}
							else {
								drawRegion[imageX, imageY, imageX + realImageW] = imageY + realImageH
							}

							scale = 1.0f / min(if (imageW == 0f) 1.0f else bitmapW2 / realImageW, if (imageH == 0f) 1.0f else bitmapH2 / realImageH)

							shaderMatrix.reset()
							shaderMatrix.setTranslate(drawRegion.left + sideClip, drawRegion.top + sideClip)
							shaderMatrix.preScale(scale, scale)

							gradientShader?.setLocalMatrix(shaderMatrix)
						}

						roundPaint.alpha = alpha

						if (isRoundRect) {
							try {
								if (roundRadius[0] == 0) {
									canvas.drawRect(roundRect, roundPaint)
								}
								else {
									canvas.drawRoundRect(roundRect, roundRadius[0].toFloat(), roundRadius[0].toFloat(), roundPaint)
								}
							}
							catch (e: Exception) {
								onBitmapException(bitmapDrawable)

								FileLog.e(e)
							}
						}
						else {
							for (a in roundRadius.indices) {
								radii[a * 2] = roundRadius[a].toFloat()
								radii[a * 2 + 1] = roundRadius[a].toFloat()
							}

							roundPath.reset()
							roundPath.addRoundRect(roundRect, radii, Path.Direction.CW)
							roundPath.close()

							canvas.drawPath(roundPath, roundPaint)
						}
					}
				}
			}
			else {
				if (isAspectFit) {
					val scale = max(scaleW, scaleH)

					canvas.save()

					bitmapW = (bitmapW / scale).toInt()
					bitmapH = (bitmapH / scale).toInt()

					if (backgroundThreadDrawHolder == null) {
						drawRegion.set(imageX + (imageW - bitmapW) / 2.0f, imageY + (imageH - bitmapH) / 2.0f, imageX + (imageW + bitmapW) / 2.0f, imageY + (imageH + bitmapH) / 2.0f)

						bitmapDrawable.setBounds(drawRegion.left.toInt(), drawRegion.top.toInt(), drawRegion.right.toInt(), drawRegion.bottom.toInt())

						if (bitmapDrawable is AnimatedFileDrawable) {
							bitmapDrawable.setActualDrawRect(drawRegion.left, drawRegion.top, drawRegion.width(), drawRegion.height())
						}
					}
					if (backgroundThreadDrawHolder != null && roundRadius[0] > 0) {
						canvas.save()

						val path = backgroundThreadDrawHolder.roundPath ?: Path().also { backgroundThreadDrawHolder.roundPath = it }

						path.rewind()

						AndroidUtilities.rectTmp.set(imageX, imageY, imageX + imageW, imageY + imageH)

						path.addRoundRect(AndroidUtilities.rectTmp, roundRadius[0].toFloat(), roundRadius[2].toFloat(), Path.Direction.CW)

						canvas.clipPath(path)
					}

					if (visible) {
						try {
							bitmapDrawable.alpha = alpha
							drawBitmapDrawable(canvas, bitmapDrawable, backgroundThreadDrawHolder, alpha)
						}
						catch (e: Exception) {
							if (backgroundThreadDrawHolder == null) {
								onBitmapException(bitmapDrawable)
							}

							FileLog.e(e)
						}
					}

					canvas.restore()

					if (backgroundThreadDrawHolder != null && roundRadius[0] > 0) {
						canvas.restore()
					}
				}
				else {
					if (abs(scaleW - scaleH) > 0.00001f) {
						canvas.save()

						if (clip) {
							canvas.clipRect(imageX, imageY, imageX + imageW, imageY + imageH)
						}

						if (orientation % 360 != 0) {
							if (centerRotation) {
								canvas.rotate(orientation.toFloat(), imageW / 2, imageH / 2)
							}
							else {
								canvas.rotate(orientation.toFloat(), 0f, 0f)
							}
						}

						if (bitmapW / scaleH > imageW) {
							bitmapW = (bitmapW / scaleH).toInt()
							drawRegion.set(imageX - (bitmapW - imageW) / 2.0f, imageY, imageX + (bitmapW + imageW) / 2.0f, imageY + imageH)
						}
						else {
							bitmapH = (bitmapH / scaleW).toInt()
							drawRegion.set(imageX, imageY - (bitmapH - imageH) / 2.0f, imageX + imageW, imageY + (bitmapH + imageH) / 2.0f)
						}

						if (bitmapDrawable is AnimatedFileDrawable) {
							bitmapDrawable.setActualDrawRect(imageX, imageY, imageW, imageH)
						}

						if (backgroundThreadDrawHolder == null) {
							if (orientation % 360 == 90 || orientation % 360 == 270) {
								val width = drawRegion.width() / 2
								val height = drawRegion.height() / 2
								val centerX = drawRegion.centerX()
								val centerY = drawRegion.centerY()
								bitmapDrawable.setBounds((centerX - height).toInt(), (centerY - width).toInt(), (centerX + height).toInt(), (centerY + width).toInt())
							}
							else {
								bitmapDrawable.setBounds(drawRegion.left.toInt(), drawRegion.top.toInt(), drawRegion.right.toInt(), drawRegion.bottom.toInt())
							}
						}

						if (visible) {
							try {
								if (Build.VERSION.SDK_INT >= 29) {
									if (blendMode != null) {
										bitmapDrawable.paint.blendMode = blendMode as BlendMode?
									}
									else {
										bitmapDrawable.paint.blendMode = null
									}
								}

								drawBitmapDrawable(canvas, bitmapDrawable, backgroundThreadDrawHolder, alpha)
							}
							catch (e: Exception) {
								if (backgroundThreadDrawHolder == null) {
									onBitmapException(bitmapDrawable)
								}

								FileLog.e(e)
							}
						}

						canvas.restore()
					}
					else {
						canvas.save()

						if (orientation % 360 != 0) {
							if (centerRotation) {
								canvas.rotate(orientation.toFloat(), imageW / 2, imageH / 2)
							}
							else {
								canvas.rotate(orientation.toFloat(), 0f, 0f)
							}
						}

						drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH)

						if (isRoundVideo) {
							drawRegion.inset(-AndroidUtilities.roundMessageInset.toFloat(), -AndroidUtilities.roundMessageInset.toFloat())
						}

						if (bitmapDrawable is AnimatedFileDrawable) {
							bitmapDrawable.setActualDrawRect(imageX, imageY, imageW, imageH)
						}

						if (backgroundThreadDrawHolder == null) {
							if (orientation % 360 == 90 || orientation % 360 == 270) {
								val width = drawRegion.width() / 2
								val height = drawRegion.height() / 2
								val centerX = drawRegion.centerX()
								val centerY = drawRegion.centerY()

								bitmapDrawable.setBounds((centerX - height).toInt(), (centerY - width).toInt(), (centerX + height).toInt(), (centerY + width).toInt())
							}
							else {
								bitmapDrawable.setBounds(drawRegion.left.toInt(), drawRegion.top.toInt(), drawRegion.right.toInt(), drawRegion.bottom.toInt())
							}
						}

						if (visible) {
							try {
								if (Build.VERSION.SDK_INT >= 29) {
									if (blendMode != null) {
										bitmapDrawable.paint.blendMode = blendMode as BlendMode?
									}
									else {
										bitmapDrawable.paint.blendMode = null
									}
								}

								drawBitmapDrawable(canvas, bitmapDrawable, backgroundThreadDrawHolder, alpha)
							}
							catch (e: Exception) {
								onBitmapException(bitmapDrawable)
								FileLog.e(e)
							}
						}

						canvas.restore()
					}
				}
			}

			if (drawable is RLottieDrawable) {
				drawable.skipFrameUpdate = false
			}
			else if (drawable is AnimatedFileDrawable) {
				drawable.skipFrameUpdate = false
			}
		}
		else {
			if (backgroundThreadDrawHolder == null) {
				if (isAspectFit) {
					var bitmapW = drawable.intrinsicWidth
					var bitmapH = drawable.intrinsicHeight
					val realImageW = imageW - sideClip * 2
					val realImageH = imageH - sideClip * 2
					val scaleW = if (imageW == 0f) 1.0f else bitmapW / realImageW
					val scaleH = if (imageH == 0f) 1.0f else bitmapH / realImageH

					val scale = max(scaleW, scaleH)

					bitmapW = (bitmapW / scale).toInt()
					bitmapH = (bitmapH / scale).toInt()

					drawRegion.set(imageX + (imageW - bitmapW) / 2.0f, imageY + (imageH - bitmapH) / 2.0f, imageX + (imageW + bitmapW) / 2.0f, imageY + (imageH + bitmapH) / 2.0f)
				}
				else {
					drawRegion.set(imageX, imageY, imageX + imageW, imageY + imageH)
				}

				drawable.setBounds(drawRegion.left.toInt(), drawRegion.top.toInt(), drawRegion.right.toInt(), drawRegion.bottom.toInt())
			}
			if (visible) {
				try {
					drawable.alpha = alpha

					if (backgroundThreadDrawHolder != null) {
						if (drawable is SvgDrawable) {
							var time = backgroundThreadDrawHolder.time

							if (time == 0L) {
								time = System.currentTimeMillis()
							}

							drawable.drawInternal(canvas, true, time, backgroundThreadDrawHolder.imageX, backgroundThreadDrawHolder.imageY, backgroundThreadDrawHolder.imageW, backgroundThreadDrawHolder.imageH)
						}
						else {
							drawable.draw(canvas)
						}
					}
					else {
						drawable.draw(canvas)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
	}

	private fun drawBitmapDrawable(canvas: Canvas, bitmapDrawable: BitmapDrawable, backgroundThreadDrawHolder: BackgroundThreadDrawHolder?, alpha: Int) {
		if (backgroundThreadDrawHolder != null) {
			if (bitmapDrawable is RLottieDrawable) {
				bitmapDrawable.drawInBackground(canvas, backgroundThreadDrawHolder.imageX, backgroundThreadDrawHolder.imageY, backgroundThreadDrawHolder.imageW, backgroundThreadDrawHolder.imageH, alpha, backgroundThreadDrawHolder.colorFilter)
			}
			else if (bitmapDrawable is AnimatedFileDrawable) {
				bitmapDrawable.drawInBackground(canvas, backgroundThreadDrawHolder.imageX, backgroundThreadDrawHolder.imageY, backgroundThreadDrawHolder.imageW, backgroundThreadDrawHolder.imageH, alpha, backgroundThreadDrawHolder.colorFilter)
			}
			else {
				val bitmap = bitmapDrawable.bitmap

				if (bitmap != null) {
					if (backgroundThreadDrawHolder.paint == null) {
						backgroundThreadDrawHolder.paint = Paint(Paint.ANTI_ALIAS_FLAG)
					}

					backgroundThreadDrawHolder.paint?.alpha = alpha
					backgroundThreadDrawHolder.paint?.colorFilter = backgroundThreadDrawHolder.colorFilter

					canvas.save()
					canvas.translate(backgroundThreadDrawHolder.imageX, backgroundThreadDrawHolder.imageY)
					canvas.scale(backgroundThreadDrawHolder.imageW / bitmap.width, backgroundThreadDrawHolder.imageH / bitmap.height)
					canvas.drawBitmap(bitmap, 0f, 0f, backgroundThreadDrawHolder.paint)
					canvas.restore()
				}
			}
		}
		else {
			bitmapDrawable.alpha = alpha

			when (bitmapDrawable) {
				is RLottieDrawable -> bitmapDrawable.drawInternal(canvas, false, currentTime)
				is AnimatedFileDrawable -> bitmapDrawable.drawInternal(canvas, false, currentTime)
				else -> bitmapDrawable.draw(canvas)
			}
		}
	}

	fun setBlendMode(mode: Any?) {
		blendMode = mode
		invalidate()
	}

	fun setGradientBitmap(bitmap: Bitmap?) {
		if (bitmap != null) {
			if (gradientShader == null || gradientBitmap != bitmap) {
				gradientShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
				updateDrawableRadius(currentImageDrawable)
			}

			isRoundRect = true
		}
		else {
			gradientShader = null
			composeShader = null
			legacyShader = null
			legacyCanvas = null

			legacyBitmap?.recycle()
			legacyBitmap = null
		}

		gradientBitmap = bitmap
	}

	private fun onBitmapException(bitmapDrawable: Drawable) {
		if (bitmapDrawable === currentMediaDrawable && mediaKey != null) {
			ImageLoader.getInstance().removeImage(mediaKey)
			mediaKey = null
		}
		else if (bitmapDrawable === currentImageDrawable && imageKey != null) {
			ImageLoader.getInstance().removeImage(imageKey)
			imageKey = null
		}
		else if (bitmapDrawable === currentThumbDrawable && thumbKey != null) {
			ImageLoader.getInstance().removeImage(thumbKey)
			thumbKey = null
		}

		setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, thumbLocation, thumbFilter, currentThumbDrawable, size, ext, parentObject, cacheType)
	}

	private fun checkAlphaAnimation(skip: Boolean, backgroundThreadDrawHolder: BackgroundThreadDrawHolder?) {
		if (manualAlphaAnimator) {
			return
		}

		if (currentAlpha != 1f) {
			if (!skip) {
				if (backgroundThreadDrawHolder != null) {
					val currentTime = System.currentTimeMillis()
					var dt = currentTime - lastUpdateAlphaTime

					if (lastUpdateAlphaTime == 0L) {
						dt = 16
					}

					if (dt > 30 && AndroidUtilities.screenRefreshRate > 60) {
						dt = 30
					}

					currentAlpha += dt / crossfadeDuration.toFloat()
				}
				else {
					currentAlpha += 16f / crossfadeDuration.toFloat()
				}

				if (currentAlpha > 1) {
					currentAlpha = 1f
					previousAlpha = 1f

					if (crossfadeImage != null) {
						recycleBitmap(null, 2)
						crossfadeShader = null
					}
				}
			}

			if (backgroundThreadDrawHolder != null) {
				AndroidUtilities.runOnUIThread {
					invalidate()
				}
			}
			else {
				invalidate()
			}
		}
	}

	fun skipDraw() {
//        RLottieDrawable lottieDrawable = getLottieAnimation();
//        if (lottieDrawable != null) {
//            lottieDrawable.setCurrentParentView(parentView);
//            lottieDrawable.updateCurrentFrame();
//        }
	}

	@JvmOverloads
	fun draw(canvas: Canvas, backgroundThreadDrawHolder: BackgroundThreadDrawHolder? = null): Boolean {
		var result = false

		if (gradientBitmap != null && imageKey != null) {
			canvas.save()
			canvas.clipRect(imageX, imageY, imageX + imageWidth, imageY + imageHeight)
			canvas.drawColor(-0x1000000)
		}

		try {
			var drawable: Drawable? = null
			val animation: AnimatedFileDrawable?
			val lottieDrawable: RLottieDrawable?
			val currentMediaDrawable: Drawable?
			val mediaShader: BitmapShader?
			val currentImageDrawable: Drawable?
			val imageShader: BitmapShader?
			val currentThumbDrawable: Drawable?
			var thumbShader: BitmapShader?
			val crossfadingWithThumb: Boolean
			val crossfadeImage: Drawable?
			val crossfadeShader: BitmapShader?
			val staticThumbDrawable: Drawable?
			val currentAlpha: Float
			val previousAlpha: Float
			val overrideAlpha: Float
			val roundRadius: IntArray
			var animationNotReady: Boolean
			val drawInBackground = backgroundThreadDrawHolder != null

			if (drawInBackground) {
				animation = backgroundThreadDrawHolder!!.animation
				lottieDrawable = backgroundThreadDrawHolder.lottieDrawable
				roundRadius = backgroundThreadDrawHolder.roundRadius
				currentMediaDrawable = backgroundThreadDrawHolder.mediaDrawable
				mediaShader = backgroundThreadDrawHolder.mediaShader
				currentImageDrawable = backgroundThreadDrawHolder.imageDrawable
				imageShader = backgroundThreadDrawHolder.imageShader
				thumbShader = backgroundThreadDrawHolder.thumbShader
				crossfadeImage = backgroundThreadDrawHolder.crossfadeImage
				crossfadingWithThumb = backgroundThreadDrawHolder.crossfadingWithThumb
				currentThumbDrawable = backgroundThreadDrawHolder.thumbDrawable
				staticThumbDrawable = backgroundThreadDrawHolder.staticThumbDrawable
				currentAlpha = backgroundThreadDrawHolder.currentAlpha
				previousAlpha = backgroundThreadDrawHolder.previousAlpha
				crossfadeShader = backgroundThreadDrawHolder.crossfadeShader
				animationNotReady = backgroundThreadDrawHolder.animationNotReady
				overrideAlpha = backgroundThreadDrawHolder.overrideAlpha
			}
			else {
				animation = this.animation
				lottieDrawable = lottieAnimation
				roundRadius = this.roundRadius
				currentMediaDrawable = this.currentMediaDrawable
				mediaShader = this.mediaShader
				currentImageDrawable = this.currentImageDrawable
				imageShader = this.imageShader
				currentThumbDrawable = this.currentThumbDrawable
				thumbShader = this.thumbShader
				crossfadingWithThumb = this.crossfadingWithThumb
				crossfadeImage = this.crossfadeImage
				staticThumbDrawable = staticThumb
				currentAlpha = this.currentAlpha
				previousAlpha = this.previousAlpha
				crossfadeShader = this.crossfadeShader
				overrideAlpha = alpha
				animationNotReady = animation != null && !animation.hasBitmap() || lottieDrawable != null && !lottieDrawable.hasBitmap()
			}

			animation?.setRoundRadius(roundRadius)

			if (lottieDrawable != null && !drawInBackground) {
				lottieDrawable.setCurrentParentView(parentView)
			}

			if ((animation != null || lottieDrawable != null) && !animationNotReady && !animationReadySent && !drawInBackground) {
				animationReadySent = true
				delegate?.onAnimationReady(this)
			}

			var orientation = 0
			var shaderToUse: BitmapShader? = null

			if (!isForcePreview && currentMediaDrawable != null && !animationNotReady) {
				drawable = currentMediaDrawable
				shaderToUse = mediaShader
				orientation = this.orientation
			}
			else if (!isForcePreview && currentImageDrawable != null && (!animationNotReady || currentMediaDrawable != null)) {
				drawable = currentImageDrawable
				shaderToUse = imageShader
				orientation = this.orientation
				animationNotReady = false
			}
			else if (crossfadeImage != null && !crossfadingWithThumb) {
				drawable = crossfadeImage
				shaderToUse = crossfadeShader
				orientation = this.orientation
			}
			else if (staticThumbDrawable is BitmapDrawable) {
				drawable = staticThumbDrawable
				if (useRoundForThumb && thumbShader == null) {
					updateDrawableRadius(staticThumbDrawable)
					thumbShader = this.thumbShader
				}
				shaderToUse = thumbShader
				orientation = thumbOrientation
			}
			else if (currentThumbDrawable != null) {
				drawable = currentThumbDrawable
				shaderToUse = thumbShader
				orientation = thumbOrientation
			}

			if (drawable != null) {
				if (crossfadeAlpha.toInt() != 0) {
					if (previousAlpha != 1f && (drawable === currentImageDrawable || drawable === currentMediaDrawable) && staticThumbDrawable != null) {
						if (useRoundForThumb && thumbShader == null) {
							updateDrawableRadius(staticThumbDrawable)
							thumbShader = this.thumbShader
						}

						drawDrawable(canvas, staticThumbDrawable, (overrideAlpha * 255).toInt(), thumbShader, orientation, backgroundThreadDrawHolder)
					}
					if (crossfadeWithThumb && animationNotReady) {
						drawDrawable(canvas, drawable, (overrideAlpha * 255).toInt(), shaderToUse, orientation, backgroundThreadDrawHolder)
					}
					else {
						if (crossfadeWithThumb && currentAlpha != 1.0f) {
							var thumbDrawable: Drawable? = null
							var thumbShaderToUse: BitmapShader? = null

							if (drawable === currentImageDrawable || drawable === currentMediaDrawable) {
								if (crossfadeImage != null) {
									thumbDrawable = crossfadeImage
									thumbShaderToUse = crossfadeShader
								}
								else if (currentThumbDrawable != null) {
									thumbDrawable = currentThumbDrawable
									thumbShaderToUse = thumbShader
								}
								else if (staticThumbDrawable != null) {
									thumbDrawable = staticThumbDrawable

									if (useRoundForThumb && thumbShader == null) {
										updateDrawableRadius(staticThumbDrawable)
										thumbShader = this.thumbShader
									}

									thumbShaderToUse = thumbShader
								}
							}
							else if (drawable === currentThumbDrawable || drawable === crossfadeImage) {
								if (staticThumbDrawable != null) {
									thumbDrawable = staticThumbDrawable

									if (useRoundForThumb && thumbShader == null) {
										updateDrawableRadius(staticThumbDrawable)
										thumbShader = this.thumbShader
									}

									thumbShaderToUse = thumbShader
								}
							}
							else if (drawable === staticThumbDrawable) {
								if (crossfadeImage != null) {
									thumbDrawable = crossfadeImage
									thumbShaderToUse = crossfadeShader
								}
							}

							if (thumbDrawable != null) {
								val alpha = if (thumbDrawable is SvgDrawable || thumbDrawable is EmojiDrawable) {
									(overrideAlpha * 255 * (1.0f - currentAlpha)).toInt()
								}
								else {
									(overrideAlpha * previousAlpha * 255).toInt()
								}

								drawDrawable(canvas, thumbDrawable, alpha, thumbShaderToUse, thumbOrientation, backgroundThreadDrawHolder)

								if (alpha != 255 && thumbDrawable is EmojiDrawable) {
									thumbDrawable.setAlpha(255)
								}
							}
						}

						drawDrawable(canvas, drawable, (overrideAlpha * currentAlpha * 255).toInt(), shaderToUse, orientation, backgroundThreadDrawHolder)
					}
				}
				else {
					drawDrawable(canvas, drawable, (overrideAlpha * 255).toInt(), shaderToUse, orientation, backgroundThreadDrawHolder)
				}

				checkAlphaAnimation(animationNotReady && crossfadeWithThumb, backgroundThreadDrawHolder)
				result = true
			}
			else if (staticThumbDrawable != null) {
				drawDrawable(canvas, staticThumbDrawable, (overrideAlpha * 255).toInt(), null, thumbOrientation, backgroundThreadDrawHolder)
				checkAlphaAnimation(animationNotReady, backgroundThreadDrawHolder)
				result = true
			}
			else {
				checkAlphaAnimation(animationNotReady, backgroundThreadDrawHolder)
			}

			if (drawable == null && animationNotReady && !drawInBackground) {
				invalidate()
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		if (gradientBitmap != null && imageKey != null) {
			canvas.restore()
		}

		return result
	}

	fun setManualAlphaAnimator(value: Boolean) {
		manualAlphaAnimator = value
	}

	val drawable: Drawable?
		get() {
			if (currentMediaDrawable != null) {
				return currentMediaDrawable
			}
			else if (currentImageDrawable != null) {
				return currentImageDrawable
			}
			else if (currentThumbDrawable != null) {
				return currentThumbDrawable
			}
			else if (staticThumb != null) {
				return staticThumb
			}

			return null
		}

	val bitmap: Bitmap?
		get() {
			val lottieDrawable = lottieAnimation

			if (lottieDrawable != null && lottieDrawable.hasBitmap()) {
				return lottieDrawable.animatedBitmap
			}

			val animation = animation

			if (animation?.hasBitmap() == true) {
				return animation.animatedBitmap
			}
			else if (currentMediaDrawable is BitmapDrawable && currentMediaDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				return (currentMediaDrawable as BitmapDrawable).bitmap
			}
			else if (currentImageDrawable is BitmapDrawable && currentImageDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				return (currentImageDrawable as BitmapDrawable).bitmap
			}
			else if (currentThumbDrawable is BitmapDrawable && currentThumbDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				return (currentThumbDrawable as BitmapDrawable).bitmap
			}
			else if (staticThumb is BitmapDrawable) {
				return (staticThumb as BitmapDrawable).bitmap
			}

			return null
		}

	val bitmapSafe: BitmapHolder?
		get() {
			var bitmap: Bitmap? = null
			var key: String? = null
			val animation = animation
			val lottieDrawable = lottieAnimation
			var orientation = 0

			if (lottieDrawable != null && lottieDrawable.hasBitmap()) {
				bitmap = lottieDrawable.animatedBitmap
			}
			else if (animation != null && animation.hasBitmap()) {
				bitmap = animation.animatedBitmap
				orientation = animation.orientation

				if (orientation != 0) {
					return BitmapHolder(Bitmap.createBitmap(bitmap), null, orientation)
				}
			}
			else if (currentMediaDrawable is BitmapDrawable && currentMediaDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				bitmap = (currentMediaDrawable as BitmapDrawable).bitmap
				key = mediaKey
			}
			else if (currentImageDrawable is BitmapDrawable && currentImageDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				bitmap = (currentImageDrawable as BitmapDrawable).bitmap
				key = imageKey
			}
			else if (currentThumbDrawable is BitmapDrawable && currentThumbDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				bitmap = (currentThumbDrawable as BitmapDrawable).bitmap
				key = thumbKey
			}
			else if (staticThumb is BitmapDrawable) {
				bitmap = (staticThumb as BitmapDrawable).bitmap
			}

			return bitmap?.let { BitmapHolder(it, key, orientation) }
		}

	val drawableSafe: BitmapHolder?
		get() {
			var drawable: Drawable? = null
			var key: String? = null

			if (currentMediaDrawable is BitmapDrawable && currentMediaDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				drawable = currentMediaDrawable
				key = mediaKey
			}
			else if (currentImageDrawable is BitmapDrawable && currentImageDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				drawable = currentImageDrawable
				key = imageKey
			}
			else if (currentThumbDrawable is BitmapDrawable && currentThumbDrawable !is AnimatedFileDrawable && currentMediaDrawable !is RLottieDrawable) {
				drawable = currentThumbDrawable
				key = thumbKey
			}
			else if (staticThumb is BitmapDrawable) {
				drawable = staticThumb
			}

			return if (drawable != null) {
				BitmapHolder(drawable, key, 0)
			}
			else {
				null
			}
		}

	val thumbBitmap: Bitmap?
		get() {
			if (currentThumbDrawable is BitmapDrawable) {
				return (currentThumbDrawable as BitmapDrawable).bitmap
			}
			else if (staticThumb is BitmapDrawable) {
				return (staticThumb as BitmapDrawable).bitmap
			}

			return null
		}

	val thumbBitmapSafe: BitmapHolder?
		get() {
			var bitmap: Bitmap? = null
			var key: String? = null

			if (currentThumbDrawable is BitmapDrawable) {
				bitmap = (currentThumbDrawable as BitmapDrawable).bitmap
				key = thumbKey
			}
			else if (staticThumb is BitmapDrawable) {
				bitmap = (staticThumb as BitmapDrawable).bitmap
			}

			return if (bitmap != null) {
				BitmapHolder(bitmap, key, 0)
			}
			else {
				null
			}
		}

	val bitmapWidth: Int
		get() {
			val animation = animation

			if (animation != null) {
				return if (orientation % 360 == 0 || orientation % 360 == 180) animation.intrinsicWidth else animation.intrinsicHeight
			}

			val lottieDrawable = lottieAnimation

			if (lottieDrawable != null) {
				return lottieDrawable.intrinsicWidth
			}

			val bitmap = bitmap ?: return (staticThumb?.intrinsicWidth ?: 1)

			return if (orientation % 360 == 0 || orientation % 360 == 180) bitmap.width else bitmap.height
		}

	val bitmapHeight: Int
		get() {
			val animation = animation

			if (animation != null) {
				return if (orientation % 360 == 0 || orientation % 360 == 180) animation.intrinsicHeight else animation.intrinsicWidth
			}

			val lottieDrawable = lottieAnimation

			if (lottieDrawable != null) {
				return lottieDrawable.intrinsicHeight
			}

			val bitmap = bitmap ?: return (staticThumb?.intrinsicHeight ?: 1)

			return if (orientation % 360 == 0 || orientation % 360 == 180) bitmap.height else bitmap.width
		}

	fun setVisible(value: Boolean, invalidate: Boolean) {
		if (visible == value) {
			return
		}

		visible = value

		if (invalidate) {
			invalidate()
		}
	}

	open fun invalidate() {
		parentView?.invalidate()
	}

	fun getParentPosition(position: IntArray?) {
		parentView?.getLocationInWindow(position)
	}

	fun setCrossfadeAlpha(value: Byte) {
		crossfadeAlpha = value
	}

	fun hasImageSet(): Boolean {
		return currentImageDrawable != null || currentMediaDrawable != null || currentThumbDrawable != null || staticThumb != null || imageKey != null || mediaKey != null
	}

	fun hasBitmapImage(): Boolean {
		return currentImageDrawable != null || currentThumbDrawable != null || staticThumb != null || currentMediaDrawable != null
	}

	fun hasNotThumb(): Boolean {
		return currentImageDrawable != null || currentMediaDrawable != null
	}

	fun hasStaticThumb(): Boolean {
		return staticThumb != null
	}

	fun setImageCoordinates(x: Float, y: Float, width: Float, height: Float) {
		imageX = x
		imageY = y
		imageWidth = width
		imageHeight = height
	}

	fun setImageCoordinates(bounds: Rect?) {
		if (bounds != null) {
			imageX = bounds.left.toFloat()
			imageY = bounds.top.toFloat()
			imageWidth = bounds.width().toFloat()
			imageHeight = bounds.height().toFloat()
		}
	}

	fun setSideClip(value: Float) {
		sideClip = value
	}

	val centerX: Float
		get() = imageX + imageWidth / 2.0f

	val centerY: Float
		get() = imageY + imageHeight / 2.0f

	fun setImageX(x: Int) {
		imageX = x.toFloat()
	}

	val imageX2: Float
		get() = imageX + imageWidth

	val imageY2: Float
		get() = imageY + imageHeight

	fun setImageWidth(width: Int) {
		imageWidth = width.toFloat()
	}

	// val imageAspectRatio: Float
	//	get() = if (orientation % 180 != 0) drawRegion.height() / drawRegion.width() else drawRegion.width() / drawRegion.height()

	fun isInsideImage(x: Float, y: Float): Boolean {
		return x >= imageX && x <= imageX + imageWidth && y >= imageY && y <= imageY + imageHeight
	}

	val newGuid: Int
		get() = ++currentGuid

	fun setForceCrossfade(value: Boolean) {
		forceCrossfade = value
	}

	fun getRoundRadius(): IntArray {
		return roundRadius
	}

	fun setRoundRadius(tl: Int, tr: Int, br: Int, bl: Int) {
		setRoundRadius(intArrayOf(tl, tr, br, bl))
	}

	fun setRoundRadius(value: Int) {
		setRoundRadius(intArrayOf(value, value, value, value))
	}

	fun setRoundRadius(value: IntArray) {
		var changed = false
		val firstValue = value[0]

		isRoundRect = true

		for (a in roundRadius.indices) {
			if (roundRadius[a] != value[a]) {
				changed = true
			}

			if (firstValue != value[a]) {
				isRoundRect = false
			}

			roundRadius[a] = value[a]
		}

		if (changed) {
			if (currentImageDrawable != null && imageShader == null) {
				updateDrawableRadius(currentImageDrawable)
			}
			if (currentMediaDrawable != null && mediaShader == null) {
				updateDrawableRadius(currentMediaDrawable)
			}
			if (currentThumbDrawable != null) {
				updateDrawableRadius(currentThumbDrawable)
			}
			else if (staticThumb != null) {
				updateDrawableRadius(staticThumb)
			}
		}
	}

	fun setCrossfadeWithOldImage(value: Boolean) {
		crossfadeWithOldImage = value
	}

	fun setAllowLottieVibration(allow: Boolean) {
		allowLottieVibration = allow
	}

	fun setAllowStartLottieAnimation(value: Boolean) {
		allowStartLottieAnimation = value
	}

	fun setAllowDecodeSingleFrame(value: Boolean) {
		allowDecodeSingleFrame = value
	}

	fun setAutoRepeat(value: Int) {
		autoRepeat = value
		val drawable = lottieAnimation
		drawable?.setAutoRepeat(value)
	}

	fun setAutoRepeatCount(count: Int) {
		autoRepeatCount = count

		if (lottieAnimation != null) {
			lottieAnimation?.setAutoRepeatCount(count)
		}
		else {
			animatedFileDrawableRepeatMaxCount = count
			animation?.repeatCount = 0
		}
	}

//	fun setAutoRepeatTimeout(timeout: Long) {
//		autoRepeatTimeout = timeout
//		lottieAnimation?.setAutoRepeatTimeout(autoRepeatTimeout)
//	}

	fun setUseSharedAnimationQueue(value: Boolean) {
		useSharedAnimationQueue = value
	}

	fun startAnimation() {
		val animation = animation

		if (animation != null) {
			animation.setUseSharedQueue(useSharedAnimationQueue)
			animation.start()
		}
		else {
			val rLottieDrawable = lottieAnimation

			if (rLottieDrawable != null && !rLottieDrawable.isRunning) {
				rLottieDrawable.restart()
			}
		}
	}

	fun stopAnimation() {
		val animation = animation

		if (animation != null) {
			animation.stop()
		}
		else {
			val rLottieDrawable = lottieAnimation

			if (rLottieDrawable != null && !rLottieDrawable.isRunning) {
				rLottieDrawable.stop()
			}
		}
	}

	val isAnimationRunning: Boolean
		get() = animation?.isRunning == true

	val animation: AnimatedFileDrawable?
		get() {
			if (currentMediaDrawable is AnimatedFileDrawable) {
				return currentMediaDrawable as AnimatedFileDrawable?
			}
			else if (currentImageDrawable is AnimatedFileDrawable) {
				return currentImageDrawable as AnimatedFileDrawable?
			}
			else if (currentThumbDrawable is AnimatedFileDrawable) {
				return currentThumbDrawable as AnimatedFileDrawable?
			}
			else if (staticThumb is AnimatedFileDrawable) {
				return staticThumb as AnimatedFileDrawable?
			}

			return null
		}

	val lottieAnimation: RLottieDrawable?
		get() {
			if (currentMediaDrawable is RLottieDrawable) {
				return currentMediaDrawable as RLottieDrawable?
			}
			else if (currentImageDrawable is RLottieDrawable) {
				return currentImageDrawable as RLottieDrawable?
			}
			else if (currentThumbDrawable is RLottieDrawable) {
				return currentThumbDrawable as RLottieDrawable?
			}
			else if (staticThumb is RLottieDrawable) {
				return staticThumb as RLottieDrawable?
			}

			return null
		}

	fun getTag(type: Int): Int {
		return when (type) {
			TYPE_THUMB -> thumbTag
			TYPE_MEDIA -> mediaTag
			else -> imageTag
		}
	}

	fun setTag(value: Int, type: Int) {
		when (type) {
			TYPE_THUMB -> thumbTag = value
			TYPE_MEDIA -> mediaTag = value
			else -> imageTag = value
		}
	}

	open fun setImageBitmapByKey(drawable: Drawable?, key: String?, type: Int, memCache: Boolean, guid: Int): Boolean {
		if (drawable == null || key == null || currentGuid != guid) {
			return false
		}

		if (type == TYPE_IMAGE) {
			if (key != imageKey) {
				return false
			}

			var allowCrossFade = true

			if (drawable !is AnimatedFileDrawable) {
				imageKey?.let {
					ImageLoader.getInstance().incrementUseCount(it)
				}

				if (videoThumbIsSame) {
					allowCrossFade = drawable !== currentImageDrawable && currentAlpha >= 1
				}
			}
			else {
				val animatedFileDrawable = drawable
				animatedFileDrawable.setStartEndTime(startTime, endTime)

				if (animatedFileDrawable.isWebmSticker) {
					imageKey?.let {
						ImageLoader.getInstance().incrementUseCount(it)
					}
				}

				if (videoThumbIsSame) {
					allowCrossFade = !animatedFileDrawable.hasBitmap()
				}
			}

			currentImageDrawable = drawable

			if (drawable is ExtendedBitmapDrawable) {
				orientation = drawable.orientation
			}

			updateDrawableRadius(drawable)

			if (allowCrossFade && visible && (!memCache && !isForcePreview || forceCrossfade) && crossfadeDuration != 0) {
				var allowCrossfade = true

				if (currentMediaDrawable is RLottieDrawable && (currentMediaDrawable as RLottieDrawable).hasBitmap()) {
					allowCrossfade = false
				}
				else if (currentMediaDrawable is AnimatedFileDrawable && (currentMediaDrawable as AnimatedFileDrawable).hasBitmap()) {
					allowCrossfade = false
				}
				else if (currentImageDrawable is RLottieDrawable) {
					allowCrossfade = staticThumb is LoadingStickerDrawable || staticThumb is SvgDrawable || staticThumb is EmojiDrawable
				}

				if (allowCrossfade && (currentThumbDrawable != null || staticThumb != null || forceCrossfade)) {
					previousAlpha = if (currentThumbDrawable != null && staticThumb != null) {
						currentAlpha
					}
					else {
						1f
					}

					currentAlpha = 0.0f
					lastUpdateAlphaTime = System.currentTimeMillis()
					crossfadeWithThumb = crossfadeImage != null || currentThumbDrawable != null || staticThumb != null
				}
			}
			else {
				currentAlpha = 1.0f
				previousAlpha = 1f
			}
		}
		else if (type == TYPE_MEDIA) {
			if (key != mediaKey) {
				return false
			}

			if (drawable !is AnimatedFileDrawable) {
				mediaKey?.let {
					ImageLoader.getInstance().incrementUseCount(it)
				}
			}
			else {
				val animatedFileDrawable = drawable
				animatedFileDrawable.setStartEndTime(startTime, endTime)

				if (animatedFileDrawable.isWebmSticker) {
					mediaKey?.let {
						ImageLoader.getInstance().incrementUseCount(it)
					}
				}

				if (videoThumbIsSame && (currentThumbDrawable is AnimatedFileDrawable || currentImageDrawable is AnimatedFileDrawable)) {
					var currentTimestamp: Long = 0

					if (currentThumbDrawable is AnimatedFileDrawable) {
						currentTimestamp = (currentThumbDrawable as AnimatedFileDrawable).lastFrameTimestamp
					}

					animatedFileDrawable.seekTo(currentTimestamp, true, true)
				}
			}

			currentMediaDrawable = drawable

			updateDrawableRadius(drawable)

			if (currentImageDrawable == null) {
				if (!memCache && !isForcePreview || forceCrossfade) {
					if (currentThumbDrawable == null && staticThumb == null || currentAlpha == 1.0f || forceCrossfade) {
						previousAlpha = if (currentThumbDrawable != null && staticThumb != null) {
							currentAlpha
						}
						else {
							1f
						}

						currentAlpha = 0.0f
						lastUpdateAlphaTime = System.currentTimeMillis()
						crossfadeWithThumb = crossfadeImage != null || currentThumbDrawable != null || staticThumb != null
					}
				}
				else {
					currentAlpha = 1.0f
					previousAlpha = 1f
				}
			}
		}
		else if (type == TYPE_THUMB) {
			if (currentThumbDrawable != null) {
				return false
			}

			if (!isForcePreview) {
				val animation = animation

				if (animation != null && animation.hasBitmap()) {
					return false
				}

				if (currentImageDrawable != null && currentImageDrawable !is AnimatedFileDrawable || currentMediaDrawable != null && currentMediaDrawable !is AnimatedFileDrawable) {
					return false
				}
			}

			if (key != thumbKey) {
				return false
			}

			thumbKey?.let {
				ImageLoader.getInstance().incrementUseCount(it)
			}

			currentThumbDrawable = drawable

			if (drawable is ExtendedBitmapDrawable) {
				thumbOrientation = drawable.orientation
			}

			updateDrawableRadius(drawable)

			if (!memCache && crossfadeAlpha.toInt() != 2) {
				if (parentObject is MessageObject && (parentObject as MessageObject).isRoundVideo && (parentObject as MessageObject).isSending) {
					currentAlpha = 1.0f
					previousAlpha = 1f
				}
				else {
					currentAlpha = 0.0f
					previousAlpha = 1f
					lastUpdateAlphaTime = System.currentTimeMillis()
					crossfadeWithThumb = staticThumb != null
				}
			}
			else {
				currentAlpha = 1.0f
				previousAlpha = 1f
			}
		}

		delegate?.didSetImage(this, currentImageDrawable != null || currentThumbDrawable != null || staticThumb != null || currentMediaDrawable != null, currentImageDrawable == null && currentMediaDrawable == null, memCache)

		if (drawable is AnimatedFileDrawable) {
			val fileDrawable = drawable
			fileDrawable.setUseSharedQueue(useSharedAnimationQueue)

			if (isAttachedToWindow) {
				fileDrawable.addParent(this)
			}

			if (allowStartAnimation && currentOpenedLayerFlags == 0) {
				fileDrawable.checkRepeat()
			}

			fileDrawable.setAllowDecodeSingleFrame(allowDecodeSingleFrame)

			animationReadySent = false

			parentView?.invalidate()
		}
		else if (drawable is RLottieDrawable) {
			val fileDrawable = drawable

			if (isAttachedToWindow) {
				fileDrawable.addParentView(this)
			}

			if (allowStartLottieAnimation && (!fileDrawable.isHeavyDrawable || currentOpenedLayerFlags == 0)) {
				fileDrawable.start()
			}

			fileDrawable.setAllowDecodeSingleFrame(true)
			fileDrawable.setAutoRepeat(autoRepeat)
			fileDrawable.setAutoRepeatCount(autoRepeatCount)
			fileDrawable.setAutoRepeatTimeout(autoRepeatTimeout)

			animationReadySent = false
		}

		invalidate()

		return true
	}

	fun setMediaStartEndTime(startTime: Long, endTime: Long) {
		this.startTime = startTime
		this.endTime = endTime

		if (currentMediaDrawable is AnimatedFileDrawable) {
			(currentMediaDrawable as AnimatedFileDrawable).setStartEndTime(startTime, endTime)
		}
	}

	private fun recycleBitmap(newKey: String?, type: Int) {
		var key: String?
		val image: Drawable?

		when (type) {
			TYPE_MEDIA -> {
				key = mediaKey
				image = currentMediaDrawable
			}

			TYPE_CROSSFADE -> {
				key = crossfadeKey
				image = crossfadeImage
			}

			TYPE_THUMB -> {
				key = thumbKey
				image = currentThumbDrawable
			}

			else -> {
				key = imageKey
				image = currentImageDrawable
			}
		}

		if (key != null && (key.startsWith("-") || key.startsWith("strippedmessage-"))) {
			val replacedKey = ImageLoader.getInstance().getReplacedKey(key)

			if (replacedKey != null) {
				key = replacedKey
			}
		}

		if (image is RLottieDrawable) {
			image.removeParentView(this)
		}

		if (image is AnimatedFileDrawable) {
			image.removeParent(this)
		}

		if (key != null && (newKey == null || newKey != key) && image != null) {
			if (image is RLottieDrawable) {
				val canDelete = ImageLoader.getInstance().decrementUseCount(key)

				if (!ImageLoader.getInstance().isInMemCache(key, true)) {
					if (canDelete) {
						image.recycle()
					}
				}
			}
			else if (image is AnimatedFileDrawable) {
				if (image.isWebmSticker) {
					val canDelete = ImageLoader.getInstance().decrementUseCount(key)

					if (!ImageLoader.getInstance().isInMemCache(key, true)) {
						if (canDelete) {
							image.recycle()
						}
					}
					else if (canDelete) {
						image.stop()
					}
				}
				else {
					if (image.parents.isEmpty()) {
						image.recycle()
					}
				}
			}
			else if (image is BitmapDrawable) {
				val bitmap = image.bitmap
				val canDelete = ImageLoader.getInstance().decrementUseCount(key)

				if (!ImageLoader.getInstance().isInMemCache(key, false)) {
					if (canDelete) {
						val bitmapToRecycle = ArrayList<Bitmap>()
						bitmapToRecycle.add(bitmap)
						AndroidUtilities.recycleBitmaps(bitmapToRecycle)
					}
				}
			}
		}

		when (type) {
			TYPE_MEDIA -> {
				mediaKey = null
				currentMediaDrawable = null
			}

			TYPE_CROSSFADE -> {
				crossfadeKey = null
				crossfadeImage = null
			}

			TYPE_THUMB -> {
				currentThumbDrawable = null
				thumbKey = null
			}

			else -> {
				currentImageDrawable = null
				imageKey = null
			}
		}
	}

	fun setCrossfadeDuration(duration: Int) {
		crossfadeDuration = duration
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.didReplacedPhotoInMemCache -> {
				val oldKey = args[0] as String

				if (mediaKey != null && mediaKey == oldKey) {
					mediaKey = args[1] as String
					mediaLocation = args[2] as ImageLocation
					setImageBackup?.mediaLocation = args[2] as ImageLocation
				}

				if (imageKey != null && imageKey == oldKey) {
					imageKey = args[1] as String
					imageLocation = args[2] as ImageLocation
					setImageBackup?.imageLocation = args[2] as ImageLocation
				}

				if (thumbKey != null && thumbKey == oldKey) {
					thumbKey = args[1] as String
					thumbLocation = args[2] as ImageLocation
					setImageBackup?.thumbLocation = args[2] as ImageLocation
				}
			}

			NotificationCenter.stopAllHeavyOperations -> {
				val layer = args[0] as Int

				if (currentLayerNum >= layer) {
					return
				}

				currentOpenedLayerFlags = currentOpenedLayerFlags or layer

				if (currentOpenedLayerFlags != 0) {
					val lottieDrawable = lottieAnimation

					if (lottieDrawable != null && lottieDrawable.isHeavyDrawable) {
						lottieDrawable.stop()
					}

					val animatedFileDrawable = animation
					animatedFileDrawable?.stop()
				}
			}

			NotificationCenter.startAllHeavyOperations -> {
				val layer = args[0] as Int

				if (currentLayerNum >= layer || currentOpenedLayerFlags == 0) {
					return
				}

				currentOpenedLayerFlags = currentOpenedLayerFlags and layer.inv()

				if (currentOpenedLayerFlags == 0) {
					val lottieDrawable = lottieAnimation
					lottieDrawable?.setAllowVibration(allowLottieVibration)

					if (allowStartLottieAnimation && lottieDrawable != null && lottieDrawable.isHeavyDrawable) {
						lottieDrawable.start()
					}

					val animatedFileDrawable = animation

					if (allowStartAnimation && animatedFileDrawable != null) {
						animatedFileDrawable.checkRepeat()
						invalidate()
					}
				}
			}
		}
	}

	fun startCrossfadeFromStaticThumb(thumb: Bitmap?) {
		startCrossfadeFromStaticThumb(BitmapDrawable(null, thumb))
	}

	fun startCrossfadeFromStaticThumb(thumb: Drawable?) {
		thumbKey = null
		currentThumbDrawable = null
		thumbShader = null
		roundPaint.shader = null
		staticThumb = thumb
		crossfadeWithThumb = true
		currentAlpha = 0f
		updateDrawableRadius(staticThumb)
	}

	fun addLoadingImageRunnable(loadOperationRunnable: Runnable?) {
		if (loadOperationRunnable != null) {
			loadingOperations.add(loadOperationRunnable)
		}
	}

	fun moveImageToFront() {
		ImageLoader.getInstance().let {
			it.moveToFront(imageKey)
			it.moveToFront(thumbKey)
		}
	}

	fun moveLottieToFront() {
		var drawable: BitmapDrawable? = null
		var key: String? = null

		if (currentMediaDrawable is RLottieDrawable) {
			drawable = currentMediaDrawable as? BitmapDrawable
			key = mediaKey
		}
		else if (currentImageDrawable is RLottieDrawable) {
			drawable = currentImageDrawable as? BitmapDrawable
			key = imageKey
		}

		if (key != null) { // && drawable != null) {
			ImageLoader.getInstance().moveToFront(key)

			if (!ImageLoader.getInstance().isInMemCache(key, true)) {
				ImageLoader.getInstance().lottieMemCache.put(key, drawable)
			}
		}
	}

	fun getParentView(): View? {
		return parentView
	}

	fun setParentView(view: View?) {
		parentView = view

		val animation = animation

		if (animation != null && isAttachedToWindow) {
			animation.setParentView(parentView)
		}
	}

	fun setVideoThumbIsSame(b: Boolean) {
		videoThumbIsSame = b
	}

	fun setAllowLoadingOnAttachedOnly(b: Boolean) {
		allowLoadingOnAttachedOnly = b
	}

	fun setSkipUpdateFrame(skipUpdateFrame: Boolean) {
		this.skipUpdateFrame = skipUpdateFrame
	}

	fun setCurrentTime(time: Long) {
		currentTime = time
	}

	fun setDrawInBackgroundThread(holder: BackgroundThreadDrawHolder?): BackgroundThreadDrawHolder {
		@Suppress("NAME_SHADOWING") val holder = holder ?: BackgroundThreadDrawHolder()

		holder.animation = animation
		holder.lottieDrawable = lottieAnimation

		System.arraycopy(roundRadius, 0, holder.roundRadius, 0, 4)

		holder.mediaDrawable = currentMediaDrawable
		holder.mediaShader = mediaShader
		holder.imageDrawable = currentImageDrawable
		holder.imageShader = imageShader
		holder.thumbDrawable = currentThumbDrawable
		holder.thumbShader = thumbShader
		holder.staticThumbDrawable = staticThumb
		holder.crossfadeImage = crossfadeImage
		holder.colorFilter = colorFilter
		holder.crossfadingWithThumb = crossfadingWithThumb
		holder.crossfadeWithOldImage = crossfadeWithOldImage
		holder.currentAlpha = currentAlpha
		holder.previousAlpha = previousAlpha
		holder.crossfadeShader = crossfadeShader
		holder.animationNotReady = holder.animation != null && !holder.animation!!.hasBitmap() || holder.lottieDrawable != null && !holder.lottieDrawable!!.hasBitmap()
		holder.imageX = imageX
		holder.imageY = imageY
		holder.imageW = imageWidth
		holder.imageH = imageHeight
		holder.overrideAlpha = alpha
		return holder
	}

	interface ImageReceiverDelegate {
		fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean)

		fun onAnimationReady(imageReceiver: ImageReceiver) {}
	}

	class BitmapHolder {
		@JvmField
		var bitmap: Bitmap? = null

		@JvmField
		var drawable: Drawable? = null

		@JvmField
		var orientation = 0

		private var key: String? = null
		private var recycleOnRelease = false

		constructor(b: Bitmap?, k: String?, o: Int) {
			bitmap = b
			key = k
			orientation = o

			key?.let {
				ImageLoader.getInstance().incrementUseCount(it)
			}
		}

		constructor(d: Drawable?, k: String?, o: Int) {
			drawable = d
			key = k
			orientation = o

			key?.let {
				ImageLoader.getInstance().incrementUseCount(it)
			}
		}

		constructor(b: Bitmap?) {
			bitmap = b
			recycleOnRelease = true
		}

		val width: Int
			get() = if (bitmap != null) bitmap!!.width else 0

		val height: Int
			get() = if (bitmap != null) bitmap!!.height else 0

		val isRecycled: Boolean
			get() = bitmap == null || bitmap!!.isRecycled

		fun release() {
			if (key == null) {
				if (recycleOnRelease && bitmap != null) {
					bitmap?.recycle()
				}

				bitmap = null
				drawable = null

				return
			}

			val canDelete = ImageLoader.getInstance().decrementUseCount(key!!)

			if (!ImageLoader.getInstance().isInMemCache(key, false)) {
				if (canDelete) {
					if (bitmap != null) {
						bitmap?.recycle()
					}
					else if (drawable != null) {
						when (drawable) {
							is RLottieDrawable -> {
								val fileDrawable = drawable as RLottieDrawable
								fileDrawable.recycle()
							}

							is AnimatedFileDrawable -> {
								val fileDrawable = drawable as AnimatedFileDrawable
								fileDrawable.recycle()
							}

							is BitmapDrawable -> {
								val bitmap = (drawable as BitmapDrawable).bitmap
								bitmap.recycle()
							}
						}
					}
				}
			}

			key = null
			bitmap = null
			drawable = null
		}
	}

	private class SetImageBackup {
		var imageLocation: ImageLocation? = null
		var imageFilter: String? = null
		var thumbLocation: ImageLocation? = null
		var thumbFilter: String? = null
		var mediaLocation: ImageLocation? = null
		var mediaFilter: String? = null
		var thumb: Drawable? = null
		var size: Long = 0
		var cacheType = 0
		var parentObject: Any? = null
		var ext: String? = null

		val isSet: Boolean
			get() = imageLocation != null || thumbLocation != null || mediaLocation != null || thumb != null

		val isWebfileSet: Boolean
			get() = imageLocation != null && (imageLocation?.webFile != null || imageLocation?.path != null) || thumbLocation != null && (thumbLocation?.webFile != null || thumbLocation?.path != null) || mediaLocation != null && (mediaLocation?.webFile != null || mediaLocation?.path != null)

		fun clear() {
			imageLocation = null
			thumbLocation = null
			mediaLocation = null
			thumb = null
		}
	}

	class BackgroundThreadDrawHolder {
		val roundRadius = IntArray(4)
		var animationNotReady = false

		@JvmField
		var overrideAlpha = 0f

		@JvmField
		var time: Long = 0

		@JvmField
		var imageH = 0f

		@JvmField
		var imageW = 0f

		@JvmField
		var imageX = 0f

		@JvmField
		var imageY = 0f

		var drawRegion = RectF()
		var colorFilter: ColorFilter? = null
		var paint: Paint? = null
		var animation: AnimatedFileDrawable? = null
		var lottieDrawable: RLottieDrawable? = null
		var mediaShader: BitmapShader? = null
		var mediaDrawable: Drawable? = null
		var imageShader: BitmapShader? = null
		var imageDrawable: Drawable? = null
		var thumbDrawable: Drawable? = null
		var thumbShader: BitmapShader? = null
		var staticThumbDrawable: Drawable? = null
		var currentAlpha = 0f
		var previousAlpha = 0f
		var crossfadeShader: BitmapShader? = null
		var crossfadeWithOldImage = false
		var crossfadingWithThumb = false
		var crossfadeImage: Drawable? = null
		var roundPath: Path? = null

		fun release() {
			animation = null
			lottieDrawable = null
			System.arraycopy(roundRadius, 0, roundRadius, 0, 4)
			mediaDrawable = null
			mediaShader = null
			imageDrawable = null
			imageShader = null
			thumbDrawable = null
			thumbShader = null
			staticThumbDrawable = null
			crossfadeImage = null
			colorFilter = null
		}

		fun setBounds(bounds: Rect?) {
			if (bounds != null) {
				imageX = bounds.left.toFloat()
				imageY = bounds.top.toFloat()
				imageW = bounds.width().toFloat()
				imageH = bounds.height().toFloat()
			}
		}

		fun getBounds(out: RectF?) {
			if (out != null) {
				out.left = imageX
				out.top = imageY
				out.right = out.left + imageW
				out.bottom = out.top + imageH
			}
		}

		fun getBounds(out: Rect?) {
			if (out != null) {
				out.left = imageX.toInt()
				out.top = imageY.toInt()
				out.right = (out.left + imageW).toInt()
				out.bottom = (out.top + imageH).toInt()
			}
		}
	}

	companion object {
		const val TYPE_IMAGE = 0
		const val TYPE_THUMB = 1
		const val TYPE_MEDIA = 3
		const val DEFAULT_CROSSFADE_DURATION = 150
		private const val TYPE_CROSSFADE = 2
		private val selectedColorFilter = PorterDuffColorFilter(-0x222223, PorterDuff.Mode.MULTIPLY)
		private val selectedGroupColorFilter = PorterDuffColorFilter(-0x444445, PorterDuff.Mode.MULTIPLY)
		private val radii = FloatArray(8)
	}
}
