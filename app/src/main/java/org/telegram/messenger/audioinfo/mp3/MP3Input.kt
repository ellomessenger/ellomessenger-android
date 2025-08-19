/*
 * Copyright 2013-2014 Odysseus Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.audioinfo.mp3

import org.telegram.messenger.audioinfo.util.PositionInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class MP3Input : PositionInputStream {
	constructor(delegate: InputStream?) : super(delegate)
	constructor(delegate: InputStream?, position: Long) : super(delegate, position)

	@Throws(IOException::class)
	fun readFully(b: ByteArray, off: Int, len: Int) {
		var total = 0

		while (total < len) {
			val current = read(b, off + total, len - total)

			total += if (current > 0) {
				current
			}
			else {
				throw EOFException()
			}
		}
	}

	@Throws(IOException::class)
	fun skipFully(len: Long) {
		var total: Long = 0

		while (total < len) {
			val current = skip(len - total)

			total += if (current > 0) {
				current
			}
			else {
				throw EOFException()
			}
		}
	}

	override fun toString(): String {
		return "mp3[pos=$position]"
	}
}
