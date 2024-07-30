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
import org.telegram.tgnet.tlrpc.MessageEntity
import org.telegram.ui.ActionBar.Theme

class TextStyleSpan @JvmOverloads constructor(val style: TextStyleRun, private val textSize: Int = 0, private var color: Int = 0) : MetricAffectingSpan() {
	class TextStyleRun {
		@JvmField
		var styleFlags = 0

		@JvmField
		var start = 0

		@JvmField
		var end = 0

		@JvmField
		var urlEntity: MessageEntity? = null

		constructor()

		constructor(run: TextStyleRun) {
			styleFlags = run.styleFlags
			start = run.start
			end = run.end
			urlEntity = run.urlEntity
		}

		fun merge(run: TextStyleRun) {
			styleFlags = styleFlags or run.styleFlags

			if (urlEntity == null && run.urlEntity != null) {
				urlEntity = run.urlEntity
			}
		}

		fun replace(run: TextStyleRun) {
			styleFlags = run.styleFlags
			urlEntity = run.urlEntity
		}

		fun applyStyle(p: TextPaint) {
			val typeface = typeface

			if (typeface != null) {
				p.typeface = typeface
			}

			if (styleFlags and FLAG_STYLE_UNDERLINE != 0) {
				p.flags = p.flags or Paint.UNDERLINE_TEXT_FLAG
			}
			else {
				p.flags = p.flags and Paint.UNDERLINE_TEXT_FLAG.inv()
			}

			if (styleFlags and FLAG_STYLE_STRIKE != 0) {
				p.flags = p.flags or Paint.STRIKE_THRU_TEXT_FLAG
			}
			else {
				p.flags = p.flags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
			}

			if (styleFlags and FLAG_STYLE_SPOILER_REVEALED != 0) {
				p.bgColor = Theme.getColor(Theme.key_chats_archivePullDownBackground)
			}
		}

		val typeface: Typeface?
			get() = if (styleFlags and FLAG_STYLE_MONO != 0 || styleFlags and FLAG_STYLE_QUOTE != 0) {
				Theme.TYPEFACE_MONOSPACE
			}
			else if (styleFlags and FLAG_STYLE_BOLD != 0 && styleFlags and FLAG_STYLE_ITALIC != 0) {
				Theme.TYPEFACE_BOLD_ITALIC
			}
			else if (styleFlags and FLAG_STYLE_BOLD != 0) {
				Theme.TYPEFACE_BOLD
			}
			else if (styleFlags and FLAG_STYLE_ITALIC != 0) {
				Theme.TYPEFACE_ITALIC
			}
			else {
				null
			}
	}

	val typeface: Typeface?
		get() = style.typeface

	fun setColor(value: Int) {
		color = value
	}

	val isSpoiler: Boolean
		get() = style.styleFlags and FLAG_STYLE_SPOILER > 0

	var isSpoilerRevealed: Boolean
		get() = style.styleFlags and FLAG_STYLE_SPOILER_REVEALED > 0
		set(b) {
			if (b) {
				style.styleFlags = style.styleFlags or FLAG_STYLE_SPOILER_REVEALED
			}
			else {
				style.styleFlags = style.styleFlags and FLAG_STYLE_SPOILER_REVEALED.inv()
			}
		}
	val isMono: Boolean
		get() = style.typeface === Theme.TYPEFACE_MONOSPACE

	val isBold: Boolean
		get() = style.typeface === Theme.TYPEFACE_BOLD

	val isItalic: Boolean
		get() = style.typeface === Theme.TYPEFACE_ITALIC

	val isBoldItalic: Boolean
		get() = style.typeface === Theme.TYPEFACE_BOLD_ITALIC

	override fun updateMeasureState(p: TextPaint) {
		if (textSize != 0) {
			p.textSize = textSize.toFloat()
		}

		p.flags = p.flags or Paint.SUBPIXEL_TEXT_FLAG

		style.applyStyle(p)
	}

	override fun updateDrawState(p: TextPaint) {
		if (textSize != 0) {
			p.textSize = textSize.toFloat()
		}

		if (color != 0) {
			p.color = color
		}

		p.flags = p.flags or Paint.SUBPIXEL_TEXT_FLAG

		style.applyStyle(p)
	}

	companion object {
		const val FLAG_STYLE_BOLD = 1
		const val FLAG_STYLE_ITALIC = 2
		const val FLAG_STYLE_MONO = 4
		const val FLAG_STYLE_STRIKE = 8
		const val FLAG_STYLE_UNDERLINE = 16
		const val FLAG_STYLE_QUOTE = 32
		const val FLAG_STYLE_MENTION = 64
		const val FLAG_STYLE_URL = 128
		const val FLAG_STYLE_SPOILER = 256
		const val FLAG_STYLE_SPOILER_REVEALED = 512
	}
}
