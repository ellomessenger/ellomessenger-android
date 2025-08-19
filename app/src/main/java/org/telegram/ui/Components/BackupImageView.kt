/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.SecureDocument
import org.telegram.tgnet.TLObject

open class BackupImageView(context: Context) : View(context) {
	@JvmField
	var imageReceiver = ImageReceiver(this)

	private var aWidth = -1
	private var aHeight = -1

	open var animatedEmojiDrawable: AnimatedEmojiDrawable? = null
		set(value) {
			if (field === value) {
				return
			}

			if (attached && field != null) {
				field?.removeView(this)
			}

			field = value

			if (attached) {
				value?.addView(this)
			}
		}

	open var attached = false

	fun setOrientation(angle: Int, center: Boolean) {
		imageReceiver.setOrientation(angle, center)
	}

	fun setImage(secureDocument: SecureDocument?, filter: String?) {
		setImage(ImageLocation.getForSecureDocument(secureDocument), filter, null, null, null, null, null, 0, null)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, ext: String?, thumb: Drawable?, parentObject: Any?) {
		setImage(imageLocation, imageFilter, null, null, thumb, null, ext, 0, parentObject)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumb: Drawable?, parentObject: Any?) {
		setImage(imageLocation, imageFilter, null, null, thumb, null, null, 0, parentObject)
	}

	fun setImage(mediaLocation: ImageLocation?, mediaFilter: String?, imageLocation: ImageLocation?, imageFilter: String?, thumb: Drawable?, parentObject: Any?) {
		imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, null, null, thumb, 0, null, parentObject, 1)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumb: Bitmap?, parentObject: Any?) {
		setImage(imageLocation, imageFilter, null, null, null, thumb, null, 0, parentObject)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumb: Drawable?, size: Int, parentObject: Any?) {
		setImage(imageLocation, imageFilter, null, null, thumb, null, null, size, parentObject)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumbBitmap: Bitmap?, size: Int, cacheType: Int, parentObject: Any?) {
		var thumb: Drawable? = null

		if (thumbBitmap != null) {
			thumb = thumbBitmap.toDrawable(resources)
		}

		imageReceiver.setImage(imageLocation, imageFilter, null, null, thumb, size.toLong(), null, parentObject, cacheType)
	}

	fun setForUserOrChat(`object`: TLObject?, avatarDrawable: AvatarDrawable?) {
		imageReceiver.setForUserOrChat(`object`, avatarDrawable)
	}

	fun setForUserOrChat(`object`: TLObject?, avatarDrawable: AvatarDrawable?, parent: Any?) {
		imageReceiver.setForUserOrChat(`object`, avatarDrawable, parent)
	}

	fun setImageMedia(mediaLocation: ImageLocation?, mediaFilter: String?, imageLocation: ImageLocation?, imageFilter: String?, thumbBitmap: Bitmap?, size: Int, cacheType: Int, parentObject: Any?) {
		var thumb: Drawable? = null

		if (thumbBitmap != null) {
			thumb = thumbBitmap.toDrawable(resources)
		}

		imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, null, null, thumb, size.toLong(), null, parentObject, cacheType)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, size: Int, parentObject: Any?) {
		setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, null, null, size, parentObject)
	}

	fun setImage(path: String?, filter: String?, thumb: Drawable?) {
		setImage(ImageLocation.getForPath(path), filter, null, null, thumb, null, null, 0, null)
	}

	fun setImage(path: String?, filter: String?, thumbPath: String?, thumbFilter: String?) {
		setImage(ImageLocation.getForPath(path), filter, ImageLocation.getForPath(thumbPath), thumbFilter, null, null, null, 0, null)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, thumb: Drawable?, thumbBitmap: Bitmap?, ext: String?, size: Int, parentObject: Any?) {
		@Suppress("NAME_SHADOWING") var thumb = thumb

		if (thumbBitmap != null) {
			thumb = thumbBitmap.toDrawable(resources)
		}

		imageReceiver.setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, thumb, size.toLong(), ext, parentObject, 0)
	}

	fun setImage(imageLocation: ImageLocation?, imageFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, ext: String?, size: Long, cacheType: Int, parentObject: Any?) {
		imageReceiver.setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, null, size, ext, parentObject, cacheType)
	}

	fun setImageMedia(mediaLocation: ImageLocation?, mediaFilter: String?, imageLocation: ImageLocation?, imageFilter: String?, thumbLocation: ImageLocation?, thumbFilter: String?, ext: String?, size: Int, cacheType: Int, parentObject: Any?) {
		imageReceiver.setImage(mediaLocation, mediaFilter, imageLocation, imageFilter, thumbLocation, thumbFilter, null, size.toLong(), ext, parentObject, cacheType)
	}

	fun setImageBitmap(bitmap: Bitmap?) {
		imageReceiver.setImageBitmap(bitmap)
	}

	fun setImageResource(resId: Int) {
		val drawable = ResourcesCompat.getDrawable(resources, resId, null)
		imageReceiver.setImageBitmap(drawable)
		invalidate()
	}

	fun setImageResource(resId: Int, color: Int) {
		val drawable = ResourcesCompat.getDrawable(resources, resId, null)
		drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
		imageReceiver.setImageBitmap(drawable)
		invalidate()
	}

	fun setImageDrawable(drawable: Drawable?) {
		imageReceiver.setImageBitmap(drawable)
	}

	fun setLayerNum(value: Int) {
		imageReceiver.setLayerNum(value)
	}

	open fun setRoundRadius(value: Int) {
		imageReceiver.setRoundRadius(value)
		invalidate()
	}

	fun setRoundRadius(tl: Int, tr: Int, bl: Int, br: Int) {
		imageReceiver.setRoundRadius(tl, tr, bl, br)
		invalidate()
	}

	val roundRadius: IntArray
		get() = imageReceiver.getRoundRadius()

	fun setAspectFit(value: Boolean) {
		imageReceiver.isAspectFit = value
	}

	fun setSize(w: Int, h: Int) {
		aWidth = w
		aHeight = h
		invalidate()
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attached = false
		imageReceiver.onDetachedFromWindow()
		animatedEmojiDrawable?.removeView(this)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		attached = true
		imageReceiver.onAttachedToWindow()
		animatedEmojiDrawable?.addView(this)
	}

	override fun onDraw(canvas: Canvas) {
		val imageReceiver = animatedEmojiDrawable?.imageReceiver ?: imageReceiver

		if (aWidth != -1 && aHeight != -1) {
			imageReceiver.setImageCoordinates(((width - aWidth) / 2).toFloat(), ((height - aHeight) / 2).toFloat(), aWidth.toFloat(), aHeight.toFloat())
		}
		else {
			imageReceiver.setImageCoordinates(0f, 0f, width.toFloat(), height.toFloat())
		}

		imageReceiver.draw(canvas)
	}

	fun setColorFilter(colorFilter: ColorFilter?) {
		imageReceiver.setColorFilter(colorFilter)
	}
}
