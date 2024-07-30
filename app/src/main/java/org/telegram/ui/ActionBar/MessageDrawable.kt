/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.ActionBar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import androidx.annotation.ColorRes
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.ui.Components.MotionBackgroundDrawable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max

open class MessageDrawable(private val currentType: Int, private val isOut: Boolean, val isSelected: Boolean) : Drawable() {
	private var backgroundColorId = 0
	private val backgroundDrawable = Array(2) { arrayOfNulls<Drawable>(4) }
	private val backgroundDrawableColor = arrayOf(intArrayOf(-0x1, -0x1, -0x1, -0x1), intArrayOf(-0x1, -0x1, -0x1, -0x1))
	private val backupRect = Rect()
	private val currentBackgroundDrawableRadius = arrayOf(intArrayOf(-1, -1, -1, -1), intArrayOf(-1, -1, -1, -1))
	// private val currentShadowDrawableRadius = intArrayOf(-1, -1, -1, -1)
	private val path = Path()
	private val rect = RectF()
	private val shadowDrawableColor = intArrayOf(-0x1, -0x1, -0x1, -0x1)
	val matrix = Matrix()
	val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	val shadowDrawables = arrayOfNulls<Drawable>(4)

	@JvmField
	var themePreview = false

	@JvmField
	var crossfadeFromDrawable: MessageDrawable? = null

	@JvmField
	var crossfadeProgress = 0f

	@JvmField
	var isCrossfadeBackground = false

	private var lastDrawWithShadow = false
	private var transitionDrawable: Drawable? = null
	private var transitionDrawableColor = 0
	private var pathDrawCacheParams: PathDrawParams? = null
	private var currentBackgroundHeight = 0
	private var topY = 0
	private var isTopNear = false
	private var isBottomNear = false
	private var innerAlpha: Int = 255
	var drawFullBubble = false
	// private var overrideRoundRadius = 0

	init {
		alpha = 255
		// setRoundRadius(AndroidUtilities.dp(12f))
	}

	fun setTop(top: Int, backgroundWidth: Int, backgroundHeight: Int, topNear: Boolean, bottomNear: Boolean) {
		setTop(top, backgroundWidth, backgroundHeight, backgroundHeight, 0, topNear, bottomNear)
	}

	fun setBackgroundColorId(@ColorRes color: Int) {
		backgroundColorId = color
	}

	fun setTop(top: Int, backgroundWidth: Int, backgroundHeight: Int, heightOffset: Int, blurredViewTopOffset: Int, topNear: Boolean, bottomNear: Boolean) {
		crossfadeFromDrawable?.setTop(top, backgroundWidth, backgroundHeight, heightOffset, blurredViewTopOffset, topNear, bottomNear)
		currentBackgroundHeight = backgroundHeight
		topY = top
		isTopNear = topNear
		isBottomNear = bottomNear
	}

	private fun dp(value: Float): Int {
		return if (currentType == TYPE_PREVIEW) {
			ceil((3 * value).toDouble()).toInt()
		}
		else {
			AndroidUtilities.dp(value)
		}
	}

	fun getBackgroundDrawable(): Drawable? {
		val newRad = AndroidUtilities.dp(SharedConfig.bubbleRadius.toFloat())

		val idx = if (isTopNear && isBottomNear) {
			3
		}
		else if (isTopNear) {
			2
		}
		else if (isBottomNear) {
			1
		}
		else {
			0
		}

		val idx2 = if (isSelected) 1 else 0
		var forceSetColor = false
		val drawWithShadow = !isSelected && !isCrossfadeBackground
		val shadowColor = ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.gray_border, null)// getColor(if (isOut) Theme.key_chat_outBubbleShadow else Theme.key_chat_inBubbleShadow)

		if (lastDrawWithShadow != drawWithShadow || currentBackgroundDrawableRadius[idx2][idx] != newRad || drawWithShadow && shadowDrawableColor[idx] != shadowColor) {
			currentBackgroundDrawableRadius[idx2][idx] = newRad

			try {
				val bitmap = Bitmap.createBitmap(dp(50f), dp(40f), Bitmap.Config.ARGB_8888)
				val canvas = Canvas(bitmap)

				backupRect.set(bounds)

				if (drawWithShadow) {
					shadowDrawableColor[idx] = shadowColor

					val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
					val gradientShader = LinearGradient(0f, 0f, 0f, dp(40f).toFloat(), intArrayOf(0x155F6569, 0x295F6569), null, Shader.TileMode.CLAMP)
					shadowPaint.shader = gradientShader
					shadowPaint.colorFilter = PorterDuffColorFilter(shadowColor, PorterDuff.Mode.MULTIPLY)
					shadowPaint.setShadowLayer(2f, 0f, 1f, -0x1)

					if (AndroidUtilities.density > 1) {
						setBounds(-1, -1, bitmap.width + 1, bitmap.height + 1)
					}
					else {
						setBounds(0, 0, bitmap.width, bitmap.height)
					}

					draw(canvas, shadowPaint)

					if (AndroidUtilities.density > 1) {
						shadowPaint.color = 0
						shadowPaint.setShadowLayer(0f, 0f, 0f, 0)
						shadowPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
						setBounds(0, 0, bitmap.width, bitmap.height)
						draw(canvas, shadowPaint)
					}
				}

				val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
				shadowPaint.color = -0x1

				setBounds(0, 0, bitmap.width, bitmap.height)
				draw(canvas, shadowPaint)

				backgroundDrawable[idx2][idx] = NinePatchDrawable(ApplicationLoader.applicationContext.resources, bitmap, getByteBuffer(bitmap.width / 2 - 1, bitmap.width / 2 + 1, bitmap.height / 2 - 1, bitmap.height / 2 + 1).array(), Rect(), null)

				forceSetColor = true

				bounds = backupRect
			}
			catch (ignore: Throwable) {
			}
		}

		lastDrawWithShadow = drawWithShadow

		val color = if (isOut) {
			if (backgroundColorId != 0) {
				ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, backgroundColorId, null)
			}
			else {
				ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.brand, null)
			}
		}
		else {
			ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.background, null)
		}

		if (backgroundDrawable[idx2][idx] != null && (backgroundDrawableColor[idx2][idx] != color || forceSetColor)) {
			backgroundDrawable[idx2][idx]?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
			backgroundDrawableColor[idx2][idx] = color
		}

		return backgroundDrawable[idx2][idx]
	}

	fun getTransitionDrawable(color: Int): Drawable {
		if (transitionDrawable == null) {
			val bitmap = Bitmap.createBitmap(dp(50f), dp(40f), Bitmap.Config.ARGB_8888)
			val canvas = Canvas(bitmap)

			backupRect.set(bounds)

			val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			shadowPaint.color = -0x1
			setBounds(0, 0, bitmap.width, bitmap.height)
			draw(canvas, shadowPaint)

			transitionDrawable = NinePatchDrawable(ApplicationLoader.applicationContext.resources, bitmap, getByteBuffer(bitmap.width / 2 - 1, bitmap.width / 2 + 1, bitmap.height / 2 - 1, bitmap.height / 2 + 1).array(), Rect(), null)

			bounds = backupRect
		}

		if (transitionDrawableColor != color) {
			transitionDrawableColor = color
			transitionDrawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
		}

		return transitionDrawable!!
	}

	val motionBackgroundDrawable: MotionBackgroundDrawable?
		get() = if (themePreview) {
			motionBackground[2]
		}
		else {
			motionBackground[if (currentType == TYPE_PREVIEW) 1 else 0]
		}

	fun getShadowDrawable(): Drawable? {
		return null
//		if (isCrossfadeBackground) {
//			return null
//		}
//
//		if (!isSelected && crossfadeFromDrawable == null) {
//			return null
//		}
//
//		val newRad = AndroidUtilities.dp(SharedConfig.bubbleRadius.toFloat())
//
//		val idx = if (isTopNear && isBottomNear) {
//			3
//		}
//		else if (isTopNear) {
//			2
//		}
//		else if (isBottomNear) {
//			1
//		}
//		else {
//			0
//		}
//
//		var forceSetColor = false
//
//		if (currentShadowDrawableRadius[idx] != newRad) {
//			currentShadowDrawableRadius[idx] = newRad
//
//			try {
//				val bitmap = Bitmap.createBitmap(dp(50f), dp(40f), Bitmap.Config.ARGB_8888)
//				val canvas = Canvas(bitmap)
//				val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
//				val gradientShader = LinearGradient(0f, 0f, 0f, dp(40f).toFloat(), intArrayOf(0x155F6569, 0x295F6569), null, Shader.TileMode.CLAMP)
//
//				shadowPaint.shader = gradientShader
//				shadowPaint.setShadowLayer(2f, 0f, 1f, -0x1)
//
//				if (AndroidUtilities.density > 1) {
//					setBounds(-1, -1, bitmap.width + 1, bitmap.height + 1)
//				}
//				else {
//					setBounds(0, 0, bitmap.width, bitmap.height)
//				}
//
//				draw(canvas, shadowPaint)
//
//				if (AndroidUtilities.density > 1) {
//					shadowPaint.color = 0
//					shadowPaint.setShadowLayer(0f, 0f, 0f, 0)
//					shadowPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
//					setBounds(0, 0, bitmap.width, bitmap.height)
//					draw(canvas, shadowPaint)
//				}
//
//				shadowDrawables[idx] = NinePatchDrawable(ApplicationLoader.applicationContext.resources, bitmap, getByteBuffer(bitmap.width / 2 - 1, bitmap.width / 2 + 1, bitmap.height / 2 - 1, bitmap.height / 2 + 1).array(), Rect(), null)
//
//				forceSetColor = true
//			}
//			catch (e: Throwable) {
//				// ignored
//			}
//		}
//
//		val color = ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.gray_border, null) //getColor(if (isOut) Theme.key_chat_outBubbleShadow else Theme.key_chat_inBubbleShadow)
//
//		if (shadowDrawables[idx] != null && (shadowDrawableColor[idx] != color || forceSetColor)) {
//			shadowDrawables[idx]?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
//			shadowDrawableColor[idx] = color
//		}
//
//		return shadowDrawables[idx]
	}

	@JvmOverloads
	fun drawCached(canvas: Canvas, patchDrawCacheParams: PathDrawParams?, paintToUse: Paint? = null) {
		pathDrawCacheParams = patchDrawCacheParams

		crossfadeFromDrawable?.pathDrawCacheParams = patchDrawCacheParams

		draw(canvas, paintToUse)

		pathDrawCacheParams = null

		crossfadeFromDrawable?.pathDrawCacheParams = null
	}

	override fun draw(canvas: Canvas) {
		if (crossfadeFromDrawable != null) {
			crossfadeFromDrawable?.draw(canvas)
			alpha = (255 * crossfadeProgress).toInt()
			draw(canvas, null)
			alpha = 255
		}
		else {
			draw(canvas, null)
		}
	}

	fun draw(canvas: Canvas, paintToUse: Paint?) {
		val bounds = bounds

		if (paintToUse == null) {
			val background = getBackgroundDrawable()

			if (background != null) {
				background.bounds = bounds
				background.draw(canvas)
				return
			}
		}

		val padding = dp(2f)
		val rad = AndroidUtilities.dp(12f)
		val nearRad = AndroidUtilities.dp(12f)
//
//		if (overrideRoundRadius != 0) {
//			rad = overrideRoundRadius
//			nearRad = overrideRoundRadius
//		}
//		else if (currentType == TYPE_PREVIEW) {
//			rad = dp(6f)
//			nearRad = dp(6f)
//		}
//		else {
//			rad = dp(SharedConfig.bubbleRadius.toFloat())
//			nearRad = dp(min(5, SharedConfig.bubbleRadius).toFloat())
//		}

		val smallRad = dp(6f)
		val p = paintToUse ?: paint

		val top = max(bounds.top, 0)
		val drawFullBottom: Boolean
		val drawFullTop: Boolean

		if (pathDrawCacheParams != null && bounds.height() < currentBackgroundHeight) {
			drawFullBottom = true
			drawFullTop = true
		}
		else {
			drawFullBottom = if (currentType == TYPE_MEDIA) topY + bounds.bottom - smallRad * 2 < currentBackgroundHeight else topY + bounds.bottom - rad < currentBackgroundHeight
			drawFullTop = topY + rad * 2 >= 0
		}

		val path: Path
		val invalidatePath: Boolean

		if (pathDrawCacheParams != null) {
			path = pathDrawCacheParams!!.path
			invalidatePath = pathDrawCacheParams!!.invalidatePath(bounds, drawFullBottom, drawFullTop)
		}
		else {
			path = this.path
			invalidatePath = true
		}

		if (invalidatePath) {
			path.reset()

			if (isOut) {
				if (drawFullBubble || currentType == TYPE_PREVIEW || paintToUse != null || drawFullBottom) {
					if (currentType == TYPE_MEDIA) {
						path.moveTo((bounds.right - dp(8f) - rad).toFloat(), (bounds.bottom - padding).toFloat())
					}
					else {
						path.moveTo((bounds.right - dp(2.6f)).toFloat(), (bounds.bottom - padding).toFloat())
					}

					path.lineTo((bounds.left + padding + rad).toFloat(), (bounds.bottom - padding).toFloat())

					rect[(bounds.left + padding).toFloat(), (bounds.bottom - padding - rad * 2).toFloat(), (bounds.left + padding + rad * 2).toFloat()] = (bounds.bottom - padding).toFloat()

					path.arcTo(rect, 90f, 90f, false)
				}
				else {
					path.moveTo((bounds.right - dp(8f)).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
					path.lineTo((bounds.left + padding).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
				}

				if (drawFullBubble || currentType == TYPE_PREVIEW || paintToUse != null || drawFullTop) {
					path.lineTo((bounds.left + padding).toFloat(), (bounds.top + padding + rad).toFloat())

					rect[(bounds.left + padding).toFloat(), (bounds.top + padding).toFloat(), (bounds.left + padding + rad * 2).toFloat()] = (bounds.top + padding + rad * 2).toFloat()

					path.arcTo(rect, 180f, 90f, false)

					val radToUse = if (isTopNear) nearRad else rad

					if (currentType == TYPE_MEDIA) {
						path.lineTo((bounds.right - padding - radToUse).toFloat(), (bounds.top + padding).toFloat())
						rect[(bounds.right - padding - radToUse * 2).toFloat(), (bounds.top + padding).toFloat(), (bounds.right - padding).toFloat()] = (bounds.top + padding + radToUse * 2).toFloat()
					}
					else {
						path.lineTo((bounds.right - dp(8f) - radToUse).toFloat(), (bounds.top + padding).toFloat())
						rect[(bounds.right - dp(8f) - radToUse * 2).toFloat(), (bounds.top + padding).toFloat(), (bounds.right - dp(8f)).toFloat()] = (bounds.top + padding + radToUse * 2).toFloat()
					}

					path.arcTo(rect, 270f, 90f, false)
				}
				else {
					path.lineTo((bounds.left + padding).toFloat(), (top - topY - dp(2f)).toFloat())

					if (currentType == TYPE_MEDIA) {
						path.lineTo((bounds.right - padding).toFloat(), (top - topY - dp(2f)).toFloat())
					}
					else {
						path.lineTo((bounds.right - dp(8f)).toFloat(), (top - topY - dp(2f)).toFloat())
					}
				}
				if (currentType == TYPE_MEDIA) {
					if (paintToUse != null || drawFullBottom) {
						val radToUse = if (isBottomNear) nearRad else rad
						path.lineTo((bounds.right - padding).toFloat(), (bounds.bottom - padding - radToUse).toFloat())
						rect[(bounds.right - padding - radToUse * 2).toFloat(), (bounds.bottom - padding - radToUse * 2).toFloat(), (bounds.right - padding).toFloat()] = (bounds.bottom - padding).toFloat()
						path.arcTo(rect, 0f, 90f, false)
					}
					else {
						path.lineTo((bounds.right - padding).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
					}
				}
				else {
					if (drawFullBubble || currentType == TYPE_PREVIEW || paintToUse != null || drawFullBottom) {
						path.lineTo((bounds.right - dp(8f)).toFloat(), (bounds.bottom - padding - smallRad - dp(3f)).toFloat())
						rect[(bounds.right - dp(8f)).toFloat(), (bounds.bottom - padding - smallRad * 2 - dp(9f)).toFloat(), (bounds.right - dp(7f) + smallRad * 2).toFloat()] = (bounds.bottom - padding - dp(1f)).toFloat()
						path.arcTo(rect, 180f, -83f, false)
					}
					else {
						path.lineTo((bounds.right - dp(8f)).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
					}
				}
			}
			else {
				if (drawFullBubble || currentType == TYPE_PREVIEW || paintToUse != null || drawFullBottom) {
					if (currentType == TYPE_MEDIA) {
						path.moveTo((bounds.left + dp(8f) + rad).toFloat(), (bounds.bottom - padding).toFloat())
					}
					else {
						path.moveTo((bounds.left + dp(2.6f)).toFloat(), (bounds.bottom - padding).toFloat())
					}

					path.lineTo((bounds.right - padding - rad).toFloat(), (bounds.bottom - padding).toFloat())

					rect[(bounds.right - padding - rad * 2).toFloat(), (bounds.bottom - padding - rad * 2).toFloat(), (bounds.right - padding).toFloat()] = (bounds.bottom - padding).toFloat()

					path.arcTo(rect, 90f, -90f, false)
				}
				else {
					path.moveTo((bounds.left + dp(8f)).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
					path.lineTo((bounds.right - padding).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
				}

				if (drawFullBubble || currentType == TYPE_PREVIEW || paintToUse != null || drawFullTop) {
					path.lineTo((bounds.right - padding).toFloat(), (bounds.top + padding + rad).toFloat())

					rect[(bounds.right - padding - rad * 2).toFloat(), (bounds.top + padding).toFloat(), (bounds.right - padding).toFloat()] = (bounds.top + padding + rad * 2).toFloat()

					path.arcTo(rect, 0f, -90f, false)

					val radToUse = if (isTopNear) nearRad else rad

					if (currentType == TYPE_MEDIA) {
						path.lineTo((bounds.left + padding + radToUse).toFloat(), (bounds.top + padding).toFloat())
						rect[(bounds.left + padding).toFloat(), (bounds.top + padding).toFloat(), (bounds.left + padding + radToUse * 2).toFloat()] = (bounds.top + padding + radToUse * 2).toFloat()
					}
					else {
						path.lineTo((bounds.left + dp(8f) + radToUse).toFloat(), (bounds.top + padding).toFloat())
						rect[(bounds.left + dp(8f)).toFloat(), (bounds.top + padding).toFloat(), (bounds.left + dp(8f) + radToUse * 2).toFloat()] = (bounds.top + padding + radToUse * 2).toFloat()
					}

					path.arcTo(rect, 270f, -90f, false)
				}
				else {
					path.lineTo((bounds.right - padding).toFloat(), (top - topY - dp(2f)).toFloat())

					if (currentType == TYPE_MEDIA) {
						path.lineTo((bounds.left + padding).toFloat(), (top - topY - dp(2f)).toFloat())
					}
					else {
						path.lineTo((bounds.left + dp(8f)).toFloat(), (top - topY - dp(2f)).toFloat())
					}
				}

				if (currentType == TYPE_MEDIA) {
					if (paintToUse != null || drawFullBottom) {
						val radToUse = if (isBottomNear) nearRad else rad

						path.lineTo((bounds.left + padding).toFloat(), (bounds.bottom - padding - radToUse).toFloat())

						rect[(bounds.left + padding).toFloat(), (bounds.bottom - padding - radToUse * 2).toFloat(), (bounds.left + padding + radToUse * 2).toFloat()] = (bounds.bottom - padding).toFloat()

						path.arcTo(rect, 180f, -90f, false)
					}
					else {
						path.lineTo((bounds.left + padding).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
					}
				}
				else {
					if (drawFullBubble || currentType == TYPE_PREVIEW || paintToUse != null || drawFullBottom) {
						path.lineTo((bounds.left + dp(8f)).toFloat(), (bounds.bottom - padding - smallRad - dp(3f)).toFloat())

						rect[(bounds.left + dp(7f) - smallRad * 2).toFloat(), (bounds.bottom - padding - smallRad * 2 - dp(9f)).toFloat(), (bounds.left + dp(8f)).toFloat()] = (bounds.bottom - padding - dp(1f)).toFloat()

						path.arcTo(rect, 0f, 83f, false)
					}
					else {
						path.lineTo((bounds.left + dp(8f)).toFloat(), (top - topY + currentBackgroundHeight).toFloat())
					}
				}
			}

			path.close()
		}

		canvas.drawPath(path, p)
	}

	override fun setAlpha(alpha: Int) {
		if (this.innerAlpha != alpha) {
			this.innerAlpha = alpha

			paint.alpha = alpha

			// if (isOut) {
			// selectedPaint.alpha = (Color.alpha(getColor(Theme.key_chat_outBubbleGradientSelectedOverlay)) * (alpha / 255.0f)).toInt()
			// }
		}

		getBackgroundDrawable()?.alpha = alpha
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		// unused
	}

	@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
	override fun getOpacity(): Int {
		return PixelFormat.TRANSPARENT
	}

	override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
		super.setBounds(left, top, right, bottom)
		crossfadeFromDrawable?.setBounds(left, top, right, bottom)
	}

//	fun setRoundRadius(radius: Int) {
//		overrideRoundRadius = radius
//	}

	class PathDrawParams {
		var path = Path()
		private var lastRect = Rect()
		private var lastDrawFullTop = false
		private var lastDrawFullBottom = false

		fun invalidatePath(bounds: Rect, drawFullBottom: Boolean, drawFullTop: Boolean): Boolean {
			val invalidate = lastRect.isEmpty || lastRect.top != bounds.top || lastRect.bottom != bounds.bottom || lastRect.right != bounds.right || lastRect.left != bounds.left || lastDrawFullTop != drawFullTop || lastDrawFullBottom != drawFullBottom || !drawFullTop || !drawFullBottom
			lastDrawFullTop = drawFullTop
			lastDrawFullBottom = drawFullBottom
			lastRect.set(bounds)
			return invalidate
		}
	}

	companion object {
		const val TYPE_TEXT = 0
		const val TYPE_MEDIA = 1
		const val TYPE_PREVIEW = 2
		val motionBackground = arrayOfNulls<MotionBackgroundDrawable>(3)

		private fun getByteBuffer(x1: Int, x2: Int, y1: Int, y2: Int): ByteBuffer {
			val buffer = ByteBuffer.allocate(4 + 4 * 7 + 4 * 2 + 4 * 2 + 4 * 9).order(ByteOrder.nativeOrder())
			buffer.put(0x01.toByte())
			buffer.put(2.toByte())
			buffer.put(2.toByte())
			buffer.put(0x09.toByte())
			buffer.putInt(0)
			buffer.putInt(0)
			buffer.putInt(0)
			buffer.putInt(0)
			buffer.putInt(0)
			buffer.putInt(0)
			buffer.putInt(0)
			buffer.putInt(x1)
			buffer.putInt(x2)
			buffer.putInt(y1)
			buffer.putInt(y2)
			buffer.putInt(0x00000001)
			buffer.putInt(0x00000001)
			buffer.putInt(0x00000001)
			buffer.putInt(0x00000001)
			buffer.putInt(0x00000001)
			buffer.putInt(0x00000001)
			buffer.putInt(0x00000001)
			buffer.putInt(0x00000001)
			buffer.putInt(0x00000001)
			return buffer
		}
	}
}
