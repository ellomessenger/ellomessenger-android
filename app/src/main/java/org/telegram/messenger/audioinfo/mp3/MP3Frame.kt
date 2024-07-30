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

class MP3Frame internal constructor(val header: Header, private val bytes: ByteArray) {
	internal class CRC16 {
		var value = 0xFFFF.toShort()
			private set

		fun update(value: Int, length: Int) {
			var mask = 1 shl length - 1

			do {
				if ((value and 0x8000 == 0) xor (value and mask == 0)) {
					this.value = (value shl 1).toShort()
					this.value = (value xor 0x8005).toShort()
				}
				else {
					this.value = (value shl 1).toShort()
				}
			} while (1.let { mask = mask ushr it; mask } != 0)
		}

		fun update(value: Byte) {
			update(value.toInt(), 8)
		}

		fun reset() {
			value = 0xFFFF.toShort()
		}
	}

	class Header(b1: Int, b2: Int, b3: Int) {
		val version: Int
		val layer: Int
		private val frequency: Int
		private val bitrate: Int
		private val channelMode: Int
		private val padding: Int
		val protection: Int

		init {
			version = b1 shr 3 and 0x3

			if (version == MPEG_VERSION_RESERVED) {
				throw MP3Exception("Reserved version")
			}

			layer = b1 shr 1 and 0x3

			if (layer == MPEG_LAYER_RESERVED) {
				throw MP3Exception("Reserved layer")
			}

			bitrate = b2 shr 4 and 0xF

			if (bitrate == MPEG_BITRATE_RESERVED) {
				throw MP3Exception("Reserved bitrate")
			}

			if (bitrate == MPEG_BITRATE_FREE) {
				throw MP3Exception("Free bitrate")
			}

			frequency = b2 shr 2 and 0x3

			if (frequency == MPEG_FRQUENCY_RESERVED) {
				throw MP3Exception("Reserved frequency")
			}

			channelMode = b3 shr 6 and 0x3
			padding = b2 shr 1 and 0x1
			protection = b1 and 0x1

			var minFrameSize = 4

			if (protection == MPEG_PROTECTION_CRC) {
				minFrameSize += 2
			}

			if (layer == MPEG_LAYER_3) {
				minFrameSize += sideInfoSize
			}

			if (frameSize < minFrameSize) {
				throw MP3Exception("Frame size must be at least $minFrameSize")
			}
		}

		private fun getFrequency(): Int {
			return FREQUENCIES[frequency][version]
		}

		// TODO correct?
		val sampleCount: Int
			get() = if (layer == MPEG_LAYER_1) {
				384
			}
			else { // TODO correct?
				1152
			}

		val frameSize: Int
			get() = (SIZE_COEFFICIENTS[version][layer] * getBitrate() / getFrequency() + padding) * SLOT_SIZES[layer]

		fun getBitrate(): Int {
			return BITRATES[bitrate][BITRATES_COLUMN[version][layer]]
		}

		val duration: Int
			get() = getTotalDuration(frameSize.toLong()).toInt()

		fun getTotalDuration(totalSize: Long): Long {
			var duration = 1000L * (sampleCount * totalSize) / (frameSize * getFrequency())

			if (version != MPEG_VERSION_1 && channelMode == MPEG_CHANNEL_MODE_MONO) {
				duration /= 2
			}

			return duration
		}

		fun isCompatible(header: Header): Boolean {
			return layer == header.layer && version == header.version && frequency == header.frequency && channelMode == header.channelMode
		}

		val sideInfoSize: Int
			get() = SIDE_INFO_SIZES[channelMode][version]

		val xingOffset: Int
			get() = 4 + sideInfoSize

		val vBRIOffset: Int
			get() = 4 + 32

		companion object {
			private const val MPEG_LAYER_RESERVED = 0
			private const val MPEG_VERSION_RESERVED = 1
			private const val MPEG_BITRATE_FREE = 0
			private const val MPEG_BITRATE_RESERVED = 15
			private const val MPEG_FRQUENCY_RESERVED = 3

			// [frequency][version]
			private val FREQUENCIES = arrayOf(intArrayOf(11025, -1, 22050, 44100), intArrayOf(12000, -1, 24000, 48000), intArrayOf(8000, -1, 16000, 32000), intArrayOf(-1, -1, -1, -1))

			// [bitrate][version,layer]
			private val BITRATES = arrayOf(intArrayOf(0, 0, 0, 0, 0), intArrayOf(32000, 32000, 32000, 32000, 8000), intArrayOf(64000, 48000, 40000, 48000, 16000), intArrayOf(96000, 56000, 48000, 56000, 24000), intArrayOf(128000, 64000, 56000, 64000, 32000), intArrayOf(160000, 80000, 64000, 80000, 40000), intArrayOf(192000, 96000, 80000, 96000, 48000), intArrayOf(224000, 112000, 96000, 112000, 56000), intArrayOf(256000, 128000, 112000, 128000, 64000), intArrayOf(288000, 160000, 128000, 144000, 80000), intArrayOf(320000, 192000, 160000, 160000, 96000), intArrayOf(352000, 224000, 192000, 176000, 112000), intArrayOf(384000, 256000, 224000, 192000, 128000), intArrayOf(416000, 320000, 256000, 224000, 144000), intArrayOf(448000, 384000, 320000, 256000, 160000), intArrayOf(-1, -1, -1, -1, -1))

			// [version][layer]
			private val BITRATES_COLUMN = arrayOf(intArrayOf(-1, 4, 4, 3), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, 4, 4, 3), intArrayOf(-1, 2, 1, 0))

			// [version][layer]
			private val SIZE_COEFFICIENTS = arrayOf(intArrayOf(-1, 72, 144, 12), intArrayOf(-1, -1, -1, -1), intArrayOf(-1, 72, 144, 12), intArrayOf(-1, 144, 144, 12))

			// [layer]
			private val SLOT_SIZES = intArrayOf( // reserved III        II         I
					-1, 1, 1, 4)

			// [channelMode][version]
			private val SIDE_INFO_SIZES = arrayOf(intArrayOf(17, -1, 17, 32), intArrayOf(17, -1, 17, 32), intArrayOf(17, -1, 17, 32), intArrayOf(9, -1, 9, 17))
			const val MPEG_LAYER_1 = 3
			const val MPEG_LAYER_2 = 2
			const val MPEG_LAYER_3 = 1
			const val MPEG_VERSION_1 = 3
			const val MPEG_VERSION_2 = 2
			const val MPEG_VERSION_2_5 = 0
			const val MPEG_CHANNEL_MODE_MONO = 3
			const val MPEG_PROTECTION_CRC = 0
		}
	}

	// skip crc bytes 4+5
	val isChecksumError: Boolean
		get() {
			if (header.protection == Header.MPEG_PROTECTION_CRC) {
				if (header.layer == Header.MPEG_LAYER_3) {
					val crc16 = CRC16()
					crc16.update(bytes[2])
					crc16.update(bytes[3])

					// skip crc bytes 4+5

					val sideInfoSize = header.sideInfoSize

					for (i in 0 until sideInfoSize) {
						crc16.update(bytes[6 + i])
					}

					val crc = bytes[4].toInt() and 0xFF shl 8 or (bytes[5].toInt() and 0xFF)

					return crc != crc16.value.toInt()
				}
			}

			return false
		}

	val size: Int
		get() = bytes.size

	// minimum Xing header size == 12
	private val isXingFrame: Boolean
		get() {
			val xingOffset = header.xingOffset

			if (bytes.size < xingOffset + 12) { // minimum Xing header size == 12
				return false
			}

			if (xingOffset < 0 || bytes.size < xingOffset + 8) {
				return false
			}

			if (bytes[xingOffset] == 'X'.code.toByte() && bytes[xingOffset + 1] == 'i'.code.toByte() && bytes[xingOffset + 2] == 'n'.code.toByte() && bytes[xingOffset + 3] == 'g'.code.toByte()) {
				return true
			}

			return bytes[xingOffset] == 'I'.code.toByte() && bytes[xingOffset + 1] == 'n'.code.toByte() && bytes[xingOffset + 2] == 'f'.code.toByte() && bytes[xingOffset + 3] == 'o'.code.toByte()
		}

	// minimum VBRI header size == 26
	private val isVBRIFrame: Boolean
		get() {
			val vbriOffset = header.vBRIOffset

			return if (bytes.size < vbriOffset + 26) { // minimum VBRI header size == 26
				false
			}
			else {
				bytes[vbriOffset] == 'V'.code.toByte() && bytes[vbriOffset + 1] == 'B'.code.toByte() && bytes[vbriOffset + 2] == 'R'.code.toByte() && bytes[vbriOffset + 3] == 'I'.code.toByte()
			}
		}

	val numberOfFrames: Int
		get() {
			if (isXingFrame) {
				val xingOffset = header.xingOffset
				val flags = bytes[xingOffset + 7]

				if (flags.toInt() and 0x01 != 0) {
					return bytes[xingOffset + 8].toInt() and 0xFF shl 24 or (bytes[xingOffset + 9].toInt() and 0xFF shl 16) or (bytes[xingOffset + 10].toInt() and 0xFF shl 8) or (bytes[xingOffset + 11].toInt() and 0xFF)
				}
			}
			else if (isVBRIFrame) {
				val vbriOffset = header.vBRIOffset
				return bytes[vbriOffset + 14].toInt() and 0xFF shl 24 or (bytes[vbriOffset + 15].toInt() and 0xFF shl 16) or (bytes[vbriOffset + 16].toInt() and 0xFF shl 8) or (bytes[vbriOffset + 17].toInt() and 0xFF)
			}

			return -1
		}
}
