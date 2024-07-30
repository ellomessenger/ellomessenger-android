/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher

class ExpiryDateTextWatcher : TextWatcher {
	private var current = ""
	private val nonDigits = Regex("\\D")

	override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
		// unused
	}

	override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
		// unused
	}

	override fun afterTextChanged(s: Editable?) {
		if (s?.toString() != current) {
			val userInput = s?.toString()?.replace(nonDigits, "") ?: ""

			if (userInput.length <= 4) {
				current = userInput.chunked(2).joinToString("/")
				s?.filters = arrayOfNulls<InputFilter>(0)
			}

			s?.replace(0, s.length, current, 0, current.length)
		}
	}
}
