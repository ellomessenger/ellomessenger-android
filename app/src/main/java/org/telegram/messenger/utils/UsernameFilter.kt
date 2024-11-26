/*
* Copyright (C) 2006 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.telegram.messenger.utils

import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned

class UsernameFilter : InputFilter {
	override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
		var modification: SpannableStringBuilder? = null
		var modoff = 0

		for (i in start until end) {
			val c = source[i]

			if (isAllowed(c)) {
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

	private fun isAllowed(c: Char): Boolean {
		return (c in '0'..'9') || (c in 'a'..'z') || (c in 'A'..'Z') //MARK: To get the underscore back, uncomment it || mAllowed.indexOf(c) != -1
	}
	companion object {
		private const val mAllowed = "_"
	}
}
