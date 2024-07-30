/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import java.util.Locale

class LetterDrawable : Drawable() {
	private val rect = RectF()
	private var textLayout: StaticLayout? = null
	private var textWidth = 0f
	private var textHeight = 0f
	private var textLeft = 0f
	private val stringBuilder = StringBuilder(5)

	init {
		namePaint.setTypeface(Theme.TYPEFACE_DEFAULT)
		namePaint.textSize = AndroidUtilities.dp(28f).toFloat()

		paint.color = ApplicationLoader.applicationContext.getColor(R.color.light_gray)
		namePaint.color = ApplicationLoader.applicationContext.getColor(R.color.dark_gray)
	}

	fun setBackgroundColor(value: Int) {
		paint.color = value
	}

	fun setColor(value: Int) {
		namePaint.color = value
	}

	fun setTitle(title: String?) {
		stringBuilder.setLength(0)

		if (!title.isNullOrEmpty()) {
			stringBuilder.append(title.substring(0, 1))
		}

		if (stringBuilder.isNotEmpty()) {
			val text = stringBuilder.toString().uppercase(Locale.getDefault())

			try {
				textLayout = StaticLayout(text, namePaint, AndroidUtilities.dp(100f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				if (textLayout!!.lineCount > 0) {
					textLeft = textLayout!!.getLineLeft(0)
					textWidth = textLayout!!.getLineWidth(0)
					textHeight = textLayout!!.getLineBottom(0).toFloat()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
		else {
			textLayout = null
		}
	}

	override fun draw(canvas: Canvas) {
		val bounds = bounds

		rect.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())

		canvas.drawRoundRect(rect, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
		canvas.save()

		if (textLayout != null) {
			val size = bounds.width()
			canvas.translate(bounds.left + (size - textWidth) / 2 - textLeft, bounds.top + (size - textHeight) / 2)
			textLayout?.draw(canvas)
		}

		canvas.restore()
	}

	override fun setAlpha(alpha: Int) {
		namePaint.alpha = alpha
		paint.alpha = alpha
	}

	override fun setColorFilter(cf: ColorFilter?) {
		// unused
	}

	override fun getOpacity(): Int {
		return PixelFormat.TRANSPARENT
	}

	override fun getIntrinsicWidth(): Int {
		return 0
	}

	override fun getIntrinsicHeight(): Int {
		return 0
	}

	companion object {
		@JvmField
		val paint = Paint()

		private val namePaint by lazy {
			TextPaint(Paint.ANTI_ALIAS_FLAG)
		}
	}
}
