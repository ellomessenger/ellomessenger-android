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

import org.telegram.messenger.audioinfo.util.RangeInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class ID3v2FrameBody internal constructor(delegate: InputStream?, position: Long, dataLength: Int, tagHeader: ID3v2TagHeader, frameHeader: ID3v2FrameHeader) {
	class Buffer(initialLength: Int) {
		var bytes: ByteArray

		init {
			bytes = ByteArray(initialLength)
		}

		fun bytes(minLength: Int): ByteArray {
			if (minLength > bytes.size) {
				var length = bytes.size * 2

				while (minLength > length) {
					length *= 2
				}

				bytes = ByteArray(length)
			}

			return bytes
		}
	}

	private val input: RangeInputStream
	val tagHeader: ID3v2TagHeader
	val frameHeader: ID3v2FrameHeader
	val data: ID3v2DataInput

	init {
		input = RangeInputStream(delegate, position, dataLength.toLong())
		data = ID3v2DataInput(input)
		this.tagHeader = tagHeader
		this.frameHeader = frameHeader
	}

	val position: Long
		get() = input.position

	val remainingLength: Long
		get() = input.remainingLength

	private fun extractString(bytes: ByteArray, offset: Int, length: Int, encoding: ID3v2Encoding, searchZeros: Boolean): String {
		@Suppress("NAME_SHADOWING") var length = length

		if (searchZeros) {
			var zeros = 0

			for (i in 0 until length) {
				// UTF-16LE may have a zero byte as second byte of a 2-byte character -> skip first zero at odd index
				if (bytes[offset + i].toInt() == 0 && (encoding != ID3v2Encoding.UTF_16 || zeros != 0 || (offset + i) % 2 == 0)) {
					if (++zeros == encoding.zeroBytes) {
						length = i + 1 - encoding.zeroBytes
						break
					}
				}
				else {
					zeros = 0
				}
			}
		}

		return try {
			var string = String(bytes, offset, length, encoding.charset)

			if (string.isNotEmpty() && string[0] == '\uFEFF') { // remove BOM
				string = string.substring(1)
			}

			string
		}
		catch (e: Exception) {
			""
		}
	}

	@Throws(IOException::class, ID3v2Exception::class)
	fun readZeroTerminatedString(maxLength: Int, encoding: ID3v2Encoding): String {
		var zeros = 0
		val length = min(maxLength, remainingLength.toInt())
		val bytes = textBuffer.get().bytes(length)

		for (i in 0 until length) {
			// UTF-16LE may have a zero byte as second byte of a 2-byte character -> skip first zero at odd index
			if (data.readByte().also { bytes[i] = it }.toInt() == 0 && (encoding != ID3v2Encoding.UTF_16 || zeros != 0 || i % 2 == 0)) {
				if (++zeros == encoding.zeroBytes) {
					return extractString(bytes, 0, i + 1 - encoding.zeroBytes, encoding, false)
				}
			}
			else {
				zeros = 0
			}
		}
		throw ID3v2Exception("Could not read zero-termiated string")
	}

	@Throws(IOException::class, ID3v2Exception::class)
	fun readFixedLengthString(length: Int, encoding: ID3v2Encoding): String {
		if (length > remainingLength) {
			throw ID3v2Exception("Could not read fixed-length string of length: $length")
		}

		val bytes = textBuffer.get().bytes(length)

		data.readFully(bytes, 0, length)

		return extractString(bytes, 0, length, encoding, true)
	}

	@Throws(IOException::class, ID3v2Exception::class)
	fun readEncoding(): ID3v2Encoding {
		return when (val value = data.readByte().toInt()) {
			0 -> ID3v2Encoding.ISO_8859_1
			1 -> ID3v2Encoding.UTF_16
			2 -> ID3v2Encoding.UTF_16BE
			3 -> ID3v2Encoding.UTF_8
			else -> throw ID3v2Exception("Invalid encoding: $value")
		}
	}

	override fun toString(): String {
		return "id3v2frame[pos=$position, $remainingLength left]"
	}

	companion object {
		val textBuffer: ThreadLocal<Buffer> = object : ThreadLocal<Buffer>() {
			override fun initialValue(): Buffer {
				return Buffer(4096)
			}
		}
	}
}
