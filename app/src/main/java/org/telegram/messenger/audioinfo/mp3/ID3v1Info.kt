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

import org.telegram.messenger.audioinfo.AudioInfo
import org.telegram.messenger.audioinfo.mp3.ID3v1Genre.Companion.getGenre
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ID3v1Info(input: InputStream) : AudioInfo() {
	init {
		if (isID3v1StartPosition(input)) {
			brand = "ID3"
			version = "1.0"

			val bytes = readBytes(input, 128)

			title = extractString(bytes, 3, 30)
			artist = extractString(bytes, 33, 30)
			album = extractString(bytes, 63, 30)

			year = try {
				extractString(bytes, 93, 4).toShort()
			}
			catch (e: NumberFormatException) {
				0
			}

			comment = extractString(bytes, 97, 30)

			val id3v1Genre = getGenre(bytes[127].toInt())

			if (id3v1Genre != null) {
				genre = id3v1Genre.description
			}

			/*
			 * ID3v1.1
			 */
			if (bytes[125].toInt() == 0 && bytes[126].toInt() != 0) {
				version = "1.1"
				track = (bytes[126].toInt() and 0xFF).toShort()
			}
		}
	}

	@Throws(IOException::class)
	fun readBytes(input: InputStream, len: Int): ByteArray {
		var total = 0
		val bytes = ByteArray(len)

		while (total < len) {
			val current = input.read(bytes, total, len - total)

			total += if (current > 0) {
				current
			}
			else {
				throw EOFException()
			}
		}

		return bytes
	}

	fun extractString(bytes: ByteArray, offset: Int, length: Int): String {
		return try {
			val text = String(bytes, offset, length, StandardCharsets.ISO_8859_1)
			val zeroIndex = text.indexOf(0.toChar())
			if (zeroIndex < 0) text else text.substring(0, zeroIndex)
		}
		catch (e: Exception) {
			""
		}
	}

	companion object {
		@JvmStatic
		@Throws(IOException::class)
		fun isID3v1StartPosition(input: InputStream): Boolean {
			input.mark(3)

			return try {
				input.read() == 'T'.code && input.read() == 'A'.code && input.read() == 'G'.code
			}
			finally {
				input.reset()
			}
		}
	}
}
