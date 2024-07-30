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
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.InflaterInputStream

class ID3v2TagBody internal constructor(delegate: InputStream?, position: Long, length: Int, tagHeader: ID3v2TagHeader) {
	private val input: RangeInputStream
	val tagHeader: ID3v2TagHeader
	val data: ID3v2DataInput

	init {
		input = RangeInputStream(delegate, position, length.toLong())
		data = ID3v2DataInput(input)
		this.tagHeader = tagHeader
	}

	val position: Long
		get() = input.position

	val remainingLength: Long
		get() = input.remainingLength

	@Throws(IOException::class, ID3v2Exception::class)
	fun frameBody(frameHeader: ID3v2FrameHeader): ID3v2FrameBody {
		var dataLength = frameHeader.bodySize
		var input: InputStream = input

		if (frameHeader.isUnsynchronization) {
			val bytes = data.readFully(frameHeader.bodySize)
			var ff = false
			val ffByte = 0xFF.toByte()
			var len = 0

			for (b in bytes) {
				if (!ff || b.toInt() != 0) {
					bytes[len++] = b
				}

				ff = b == ffByte
			}

			dataLength = len
			input = ByteArrayInputStream(bytes, 0, len)
		}

		if (frameHeader.isEncryption) {
			throw ID3v2Exception("Frame encryption is not supported")
		}

		if (frameHeader.isCompression) {
			dataLength = frameHeader.dataLengthIndicator
			input = InflaterInputStream(input)
		}

		return ID3v2FrameBody(input, frameHeader.headerSize.toLong(), dataLength, tagHeader, frameHeader)
	}

	override fun toString(): String {
		return "id3v2tag[pos=$position, $remainingLength left]"
	}
}
