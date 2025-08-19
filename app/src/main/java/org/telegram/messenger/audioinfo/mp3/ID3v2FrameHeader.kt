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

import java.nio.charset.StandardCharsets

class ID3v2FrameHeader(input: ID3v2TagBody) {
	val frameId: String
	val headerSize: Int
	var bodySize = 0
	var isUnsynchronization = false
	var isCompression = false
	var isEncryption = false
	var dataLengthIndicator = 0

	init {
		val startPosition = input.position
		val data = input.data

		/*
		 * Frame Id
		 */
		frameId = if (input.tagHeader.version == 2) { // $xx xx xx (three characters)
			String(data.readFully(3), StandardCharsets.ISO_8859_1)
		}
		else { // $xx xx xx xx (four characters)
			String(data.readFully(4), StandardCharsets.ISO_8859_1)
		}

		/*
		 * Size
		 */
		bodySize = when (input.tagHeader.version) {
			2 -> { // $xx xx xx
				data.readByte().toInt() and 0xFF shl 16 or (data.readByte().toInt() and 0xFF shl 8) or (data.readByte().toInt() and 0xFF)
			}

			3 -> { // $xx xx xx xx
				data.readInt()
			}

			else -> { // 4 * %0xxxxxxx (sync-save integer)
				data.readSyncsafeInt()
			}
		}

		/*
		 * Flags
		 */
		if (input.tagHeader.version > 2) { // $xx xx
			data.readByte() // status flags
			val formatFlags = data.readByte()
			val compressionMask: Int
			val encryptionMask: Int
			val groupingIdentityMask: Int
			var unsynchronizationMask = 0x00
			var dataLengthIndicatorMask = 0x00

			if (input.tagHeader.version == 3) { // %(compression)(encryption)(groupingIdentity)00000
				compressionMask = 0x80
				encryptionMask = 0x40
				groupingIdentityMask = 0x20
			}
			else { // %0(groupingIdentity)00(compression)(encryption)(unsynchronization)(dataLengthIndicator)
				groupingIdentityMask = 0x40
				compressionMask = 0x08
				encryptionMask = 0x04
				unsynchronizationMask = 0x02
				dataLengthIndicatorMask = 0x01
			}

			isCompression = formatFlags.toInt() and compressionMask != 0
			isUnsynchronization = formatFlags.toInt() and unsynchronizationMask != 0
			isEncryption = formatFlags.toInt() and encryptionMask != 0

			/*
			 * Read flag attachments in the order of the flags (version dependent).
			 */
			if (input.tagHeader.version == 3) {
				if (isCompression) {
					dataLengthIndicator = data.readInt()
					bodySize -= 4
				}

				if (isEncryption) {
					data.readByte() // just skip
					bodySize -= 1
				}

				if (formatFlags.toInt() and groupingIdentityMask != 0) {
					data.readByte() // just skip
					bodySize -= 1
				}
			}
			else {
				if (formatFlags.toInt() and groupingIdentityMask != 0) {
					data.readByte() // just skip
					bodySize -= 1
				}

				if (isEncryption) {
					data.readByte() // just skip
					bodySize -= 1
				}

				if (formatFlags.toInt() and dataLengthIndicatorMask != 0) {
					dataLengthIndicator = data.readSyncsafeInt()
					bodySize -= 4
				}
			}
		}

		headerSize = (input.position - startPosition).toInt()
	}

	val isValid: Boolean
		get() {
			for (i in frameId.indices) {
				if ((frameId[i] < 'A' || frameId[i] > 'Z') && (frameId[i] < '0' || frameId[i] > '9')) {
					return false
				}
			}

			return bodySize > 0
		}

	val isPadding: Boolean
		get() {
			for (i in frameId.indices) {
				if (frameId[0].code != 0) {
					return false
				}
			}

			return bodySize == 0
		}

	override fun toString(): String {
		return String.format("%s[id=%s, bodysize=%d]", javaClass.simpleName, frameId, bodySize)
	}
}
