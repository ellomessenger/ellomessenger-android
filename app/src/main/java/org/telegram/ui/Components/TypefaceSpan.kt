/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import org.telegram.ui.ActionBar.Theme

class TypefaceSpan @JvmOverloads constructor(private val typeface: Typeface? = null, private val textSize: Int = 0, private var color: Int = 0) : MetricAffectingSpan() {
	fun setColor(value: Int) {
		color = value
	}

	val isMono: Boolean
		get() = typeface === Theme.TYPEFACE_MONOSPACE

	val isBold: Boolean
		get() = typeface === Theme.TYPEFACE_BOLD

	val isItalic: Boolean
		get() = typeface === Theme.TYPEFACE_ITALIC

	override fun updateMeasureState(p: TextPaint) {
		if (typeface != null) {
			p.typeface = typeface
		}

		if (textSize != 0) {
			p.textSize = textSize.toFloat()
		}

		p.flags = p.flags or Paint.SUBPIXEL_TEXT_FLAG
	}

	override fun updateDrawState(tp: TextPaint) {
		if (typeface != null) {
			tp.typeface = typeface
		}

		if (textSize != 0) {
			tp.textSize = textSize.toFloat()
		}

		if (color != 0) {
			tp.color = color
		}

		tp.flags = tp.flags or Paint.SUBPIXEL_TEXT_FLAG
	}
}
