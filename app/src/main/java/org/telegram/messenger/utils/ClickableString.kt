/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.messenger.utils

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.TypefaceSpan

class ClickableString(source: String, url: String, underline: Boolean, listener: ClickableStringListener) : SpannableString(source) {
	init {
		setSpan(CustomClickableSpan(url, underline, listener), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		// setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
	}

	private class CustomClickableSpan(private val url: String, private val underline: Boolean, private val listener: ClickableStringListener) : ClickableSpan() {
		override fun onClick(widget: View) {
			listener.onLinkClicked(url)
		}

		override fun updateDrawState(ds: TextPaint) {
			super.updateDrawState(ds)
			ds.isUnderlineText = underline
		}
	}

	fun interface ClickableStringListener {
		fun onLinkClicked(url: String)
	}
}
