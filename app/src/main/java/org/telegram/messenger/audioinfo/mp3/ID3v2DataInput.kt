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

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class ID3v2DataInput(private val input: InputStream) {
	@Throws(IOException::class)
	fun readFully(b: ByteArray?, off: Int, len: Int) {
		var total = 0

		while (total < len) {
			val current = input.read(b, off + total, len - total)

			total += if (current > 0) {
				current
			}
			else {
				throw EOFException()
			}
		}
	}

	@Throws(IOException::class)
	fun readFully(len: Int): ByteArray {
		val bytes = ByteArray(len)
		readFully(bytes, 0, len)
		return bytes
	}

	@Throws(IOException::class)
	fun skipFully(len: Long) {
		var total: Long = 0

		while (total < len) {
			val current = input.skip(len - total)

			total += if (current > 0) {
				current
			}
			else {
				throw EOFException()
			}
		}
	}

	@Throws(IOException::class)
	fun readByte(): Byte {
		val b = input.read()

		if (b < 0) {
			throw EOFException()
		}

		return b.toByte()
	}

	@Throws(IOException::class)
	fun readInt(): Int {
		return (readByte().toInt() and 0xFF shl 24) or (readByte().toInt() and 0xFF shl 16) or (readByte().toInt() and 0xFF shl 8) or (readByte().toInt() and 0xFF)
	}

	@Throws(IOException::class)
	fun readSyncsafeInt(): Int {
		return (readByte().toInt() and 0x7F shl 21) or (readByte().toInt() and 0x7F shl 14) or (readByte().toInt() and 0x7F shl 7) or (readByte().toInt() and 0x7F)
	}
}
