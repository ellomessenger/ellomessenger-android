/*
 * Copyright Hannu Leinonen, 2016-2020 (https://gist.github.com/hleinone/5b445e5475ca9f8a3bdc6a44998f4edd).
 */
package org.telegram.ui.profile.wallet

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher

class CardNumberTextWatcher : TextWatcher {
	private var current = ""
	private val nonDigits = Regex("\\D")
	private val maxCardLength = 19

	override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
		// unused
	}

	override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
		// unused
	}

	override fun afterTextChanged(s: Editable?) {
		if (s?.toString() != current) {
			val userInput = s?.toString()?.replace(nonDigits, "") ?: ""

			if (userInput.length <= maxCardLength) {
				current = userInput.chunked(4).joinToString(" ")
				s?.filters = arrayOfNulls<InputFilter>(0)
			}

			s?.replace(0, s.length, current, 0, current.length)
		}
	}
}
