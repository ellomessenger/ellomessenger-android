/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView

@SuppressLint("AppCompatCustomView")
class BlurredOverlayTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : TextView(context, attrs, defStyleAttr) {
	private var blurRadius = 25f
	private var blurredBitmap: Bitmap? = null
	private val paint = Paint()
	private var sourceView: ImageView? = null
	private var renderScript: RenderScript? = null
	private var blurScript: ScriptIntrinsicBlur? = null
	private var overlayColor = Color.TRANSPARENT
	private var colorIntensity = 0.5f

	private val overlayPaint = Paint().apply {
		xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
	}

	init {
		setLayerType(View.LAYER_TYPE_HARDWARE, null)
	}

	fun setSourceImageView(imageView: ImageView) {
		sourceView = imageView
	}

	override fun onDraw(canvas: Canvas) {
		updateBlurredBackground()

		blurredBitmap?.let { bitmap ->
			canvas.drawBitmap(bitmap, 0f, 0f, paint)

			if (overlayColor != Color.TRANSPARENT) {
				overlayPaint.color = adjustAlpha(overlayColor, colorIntensity)
				canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
			}
		}

		super.onDraw(canvas)
	}

	private fun updateBlurredBackground() {
		val source = sourceView ?: return
		val renderScript = renderScript ?: return

		val locations = IntArray(2)

		getLocationInWindow(locations)

		val textViewX = locations[0]
		val textViewY = locations[1]

		source.getLocationInWindow(locations)

		val imageViewX = locations[0]
		val imageViewY = locations[1]

		val relativeX = textViewX - imageViewX
		val relativeY = textViewY - imageViewY

		if (blurredBitmap == null || blurredBitmap?.width != width || blurredBitmap?.height != height) {
			blurredBitmap?.recycle()
			blurredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		}

		val canvas = Canvas(blurredBitmap!!)
		canvas.save()
		canvas.translate(-relativeX.toFloat(), -relativeY.toFloat())

		source.draw(canvas)

		canvas.restore()

		val input = Allocation.createFromBitmap(renderScript, blurredBitmap)
		val output = Allocation.createTyped(renderScript, input.type)

		blurScript?.setInput(input)
		blurScript?.forEach(output)

		output.copyTo(blurredBitmap)

		input.destroy()
		output.destroy()
	}

	fun setOverlayColor(color: Int) {
		overlayColor = color
		invalidate()
	}

	/**
	 * @param intensity 0.0f - 1.0f
	 */
	fun setColorIntensity(intensity: Float) {
		colorIntensity = intensity.coerceIn(0f, 1f)
		invalidate()
	}

	fun setBlurRadius(radius: Float) {
		blurRadius = radius.coerceIn(0.1f, 25f)
		blurScript?.setRadius(blurRadius)
		invalidate()
	}

	private fun adjustAlpha(color: Int, factor: Float): Int {
		val alpha = (Color.alpha(color) * factor).toInt()
		return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		renderScript = RenderScript.create(context)

		blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
		blurScript?.setRadius(blurRadius)
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		blurredBitmap?.recycle()
		blurredBitmap = null

		renderScript?.destroy()
		renderScript = null

		blurScript?.destroy()
		blurScript = null
	}
}
