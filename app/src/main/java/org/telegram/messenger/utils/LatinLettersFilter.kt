package org.telegram.messenger.utils

import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned

class LatinLettersFilter : InputFilter {
	override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
		var modification: SpannableStringBuilder? = null
		var modoff = 0

		for (i in start until end) {
			val c = source[i]

			if (isLatinLetter(c)) {
				modoff++
			}
			else {
				if (modification == null) {
					modification = SpannableStringBuilder(source, start, end)
					modoff = i - start
				}

				modification.delete(modoff, modoff + 1)
			}
		}

		return modification

	}

	private fun isLatinLetter(c: Char): Boolean {
		return (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '@' || c == '.' || c == '_' || c == '-' || c == '+')
	}
}
