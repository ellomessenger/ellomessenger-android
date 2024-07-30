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
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ID3v2TagHeader internal constructor(input: PositionInputStream) {
	var version = 0
	var revision = 0
	var headerSize = 0 // size of header, including extended header (with attachments)
	private var totalTagSize = 0 // everything, i.e. including tag header, extended header, footer & padding
	private var paddingSize = 0 // size of zero padding after frames
	var footerSize = 0 // size of footer (version 4 only)
	private var isUnsynchronization = false
	private var isCompression = false

	constructor(input: InputStream?) : this(PositionInputStream(input))

	init {
		val startPosition = input.position
		val data = ID3v2DataInput(input)

		/*
		 * Identifier: "ID3"
		 */
		val id = String(data.readFully(3), StandardCharsets.ISO_8859_1)
		if ("ID3" != id) {
			throw ID3v2Exception("Invalid ID3 identifier: $id")
		}

		/*
		 * Version: $02, $03 or $04
		 */
		version = data.readByte().toInt()

		if (version != 2 && version != 3 && version != 4) {
			throw ID3v2Exception("Unsupported ID3v2 version: $version")
		}

		/*
		 * Revision: $xx
		 */
		revision = data.readByte().toInt()

		/*
		 * Flags (evaluated below)
		 */
		val flags = data.readByte()

		/*
		 * Size: 4 * %0xxxxxxx (sync-save integer)
		 */
		totalTagSize = 10 + data.readSyncsafeInt()

		/*
		 * Evaluate flags
		 */
		if (version == 2) { // %(unsynchronisation)(compression)000000
			isUnsynchronization = flags.toInt() and 0x80 != 0
			isCompression = flags.toInt() and 0x40 != 0
		}
		else { // %(unsynchronisation)(extendedHeader)(experimentalIndicator)(version == 3 ? 0 : footerPresent)0000
			isUnsynchronization = flags.toInt() and 0x80 != 0

			/*
			 * Extended Header
			 */
			if (flags.toInt() and 0x40 != 0) {
				if (version == 3) {/*
					 * Extended header size: $xx xx xx xx (6 or 10 if CRC data present)
					 * In version 3, the size excludes itself.
					 */
					val extendedHeaderSize = data.readInt()

					/*
					 * Extended Flags: $xx xx (skip)
					 */
					data.readByte() // flags...
					data.readByte() // more flags...

					/*
					 * Size of padding: $xx xx xx xx
					 */
					paddingSize = data.readInt()

					/*
					 * consume the rest
					 */
					data.skipFully((extendedHeaderSize - 6).toLong())
				}
				else {/*
					 * Extended header size: 4 * %0xxxxxxx (sync-save integer)
					 * In version 4, the size includes itself.
					 */
					val extendedHeaderSize = data.readSyncsafeInt()

					/*
					 * consume the rest
					 */
					data.skipFully((extendedHeaderSize - 4).toLong())
				}
			}

			/*
			 * Footer Present
			 */
			if (version >= 4 && flags.toInt() and 0x10 != 0) { // footer present
				footerSize = 10
				totalTagSize += 10
			}
		}

		headerSize = (input.position - startPosition).toInt()
	}

	@Throws(IOException::class, ID3v2Exception::class)
	fun tagBody(input: InputStream?): ID3v2TagBody {
		if (isCompression) {
			throw ID3v2Exception("Tag compression is not supported")
		}

		return if (version < 4 && isUnsynchronization) {
			val bytes = ID3v2DataInput(input!!).readFully(totalTagSize - headerSize)
			var ff = false
			val ffByte = 0xFF.toByte()
			var len = 0

			for (b in bytes) {
				if (!ff || b.toInt() != 0) {
					bytes[len++] = b
				}

				ff = b == ffByte
			}

			ID3v2TagBody(ByteArrayInputStream(bytes, 0, len), headerSize.toLong(), len, this)
		}
		else {
			ID3v2TagBody(input, headerSize.toLong(), totalTagSize - headerSize - footerSize, this)
		}
	}

	override fun toString(): String {
		return String.format("%s[version=%s, totalTagSize=%d]", javaClass.simpleName, version, totalTagSize)
	}
}
