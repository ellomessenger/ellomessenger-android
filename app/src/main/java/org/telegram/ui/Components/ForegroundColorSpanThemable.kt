/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance

class ForegroundColorSpanThemable(private val color: Int) : CharacterStyle(), UpdateAppearance {
	override fun updateDrawState(textPaint: TextPaint) {
		if (textPaint.color != color) {
			textPaint.color = color
		}
	}
}
