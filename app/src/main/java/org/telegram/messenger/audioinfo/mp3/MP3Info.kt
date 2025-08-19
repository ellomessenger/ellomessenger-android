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
import org.telegram.messenger.audioinfo.mp3.ID3v1Info.Companion.isID3v1StartPosition
import org.telegram.messenger.audioinfo.mp3.ID3v2Info.Companion.isID3v2StartPosition
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.logging.Level
import java.util.logging.Logger

class MP3Info @JvmOverloads constructor(input: InputStream?, fileLength: Long, debugLevel: Level = Level.FINEST) : AudioInfo() {
	interface StopReadCondition {
		@Throws(IOException::class)
		fun stopRead(data: MP3Input): Boolean
	}

	init {
		brand = "MP3"
		version = "0"

		val data = MP3Input(input)

		if (isID3v2StartPosition(data)) {
			val info = ID3v2Info(data, debugLevel)

			album = info.album
			albumArtist = info.albumArtist
			artist = info.artist
			comment = info.comment
			cover = info.cover
			smallCover = info.smallCover
			isCompilation = info.isCompilation
			composer = info.composer
			copyright = info.copyright
			disc = info.disc
			discs = info.discs
			duration = info.duration
			genre = info.genre
			grouping = info.grouping
			lyrics = info.lyrics
			title = info.title
			track = info.track
			tracks = info.tracks
			year = info.year
		}

		if (duration <= 0 || duration >= 3600000L) { // don't trust strange durations (e.g. old lame versions always write TLEN 97391548)
			try {
				duration = calculateDuration(data, fileLength, object : StopReadCondition {
					val stopPosition = fileLength - 128

					@Throws(IOException::class)
					override fun stopRead(data: MP3Input): Boolean {
						return data.position == stopPosition && isID3v1StartPosition(data)
					}
				})
			}
			catch (e: MP3Exception) {
				if (LOGGER.isLoggable(debugLevel)) {
					LOGGER.log(debugLevel, "Could not determine MP3 duration", e)
				}
			}
		}

		if (title == null || album == null || artist == null) {
			if (data.position <= fileLength - 128) { // position to last 128 bytes
				data.skipFully(fileLength - 128 - data.position)

				if (isID3v1StartPosition(input!!)) {
					val info = ID3v1Info(input)

					if (album == null) {
						album = info.album
					}

					if (artist == null) {
						artist = info.artist
					}

					if (comment == null) {
						comment = info.comment
					}

					if (genre == null) {
						genre = info.genre
					}

					if (title == null) {
						title = info.title
					}

					if (track.toInt() == 0) {
						track = info.track
					}

					if (year.toInt() == 0) {
						year = info.year
					}
				}
			}
		}
	}

	@Throws(IOException::class)
	fun readFirstFrame(data: MP3Input, stopCondition: StopReadCondition): MP3Frame? {
		var b0 = 0
		var b1 = if (stopCondition.stopRead(data)) -1 else data.read()

		while (b1 != -1) {
			if (b0 == 0xFF && b1 and 0xE0 == 0xE0) { // first 11 bits should be 1
				data.mark(2) // set mark at b2

				val b2 = if (stopCondition.stopRead(data)) -1 else data.read()

				if (b2 == -1) {
					break
				}

				val b3 = if (stopCondition.stopRead(data)) -1 else data.read()

				if (b3 == -1) {
					break
				}

				var header: MP3Frame.Header? = null

				try {
					header = MP3Frame.Header(b1, b2, b3)
				}
				catch (e: MP3Exception) {
					// not a valid frame header
				}

				if (header != null) { // we have a candidate
					/*
					 * The code gets a bit complex here, because we need to be able to reset() to b2 if
					 * the check fails. Thus, we have to reset() to b2 before doing a call to mark().
					 */
					data.reset() // reset input to b2
					data.mark(header.frameSize + 2) // rest of frame (size - 2) + next header

					/*
					 * read frame data
					 */
					val frameBytes = ByteArray(header.frameSize)
					frameBytes[0] = 0xFF.toByte()
					frameBytes[1] = b1.toByte()

					try {
						data.readFully(frameBytes, 2, frameBytes.size - 2) // may throw EOFException
					}
					catch (e: EOFException) {
						break
					}

					val frame = MP3Frame(header, frameBytes)

					/*
					 * read next header  
					 */
					if (!frame.isChecksumError) {
						val nextB0 = if (stopCondition.stopRead(data)) -1 else data.read()
						val nextB1 = if (stopCondition.stopRead(data)) -1 else data.read()

						if (nextB0 == -1 || nextB1 == -1) {
							return frame
						}

						if (nextB0 == 0xFF && nextB1 and 0xFE == b1 and 0xFE) { // quick check: nextB1 must match b1's version & layer
							val nextB2 = if (stopCondition.stopRead(data)) -1 else data.read()
							val nextB3 = if (stopCondition.stopRead(data)) -1 else data.read()

							if (nextB2 == -1 || nextB3 == -1) {
								return frame
							}

							try {
								if (MP3Frame.Header(nextB1, nextB2, nextB3).isCompatible(header)) {
									data.reset() // reset input to b2
									data.skipFully((frameBytes.size - 2).toLong()) // skip to end of frame
									return frame
								}
							}
							catch (e: MP3Exception) {
								// not a valid frame header
							}
						}
					}
				}

				/*
				 * seems to be a false sync...
				 */
				data.reset() // reset input to b2
			}

			/*
			 * read next byte
			 */
			b0 = b1
			b1 = if (stopCondition.stopRead(data)) -1 else data.read()
		}

		return null
	}

	@Throws(IOException::class)
	fun readNextFrame(data: MP3Input, stopCondition: StopReadCondition, previousFrame: MP3Frame?): MP3Frame? {
		val previousHeader = previousFrame!!.header

		data.mark(4)

		val b0 = if (stopCondition.stopRead(data)) -1 else data.read()
		val b1 = if (stopCondition.stopRead(data)) -1 else data.read()

		if (b0 == -1 || b1 == -1) {
			return null
		}

		if (b0 == 0xFF && b1 and 0xE0 == 0xE0) { // first 11 bits should be 1
			val b2 = if (stopCondition.stopRead(data)) -1 else data.read()
			val b3 = if (stopCondition.stopRead(data)) -1 else data.read()

			if (b2 == -1 || b3 == -1) {
				return null
			}

			var nextHeader: MP3Frame.Header? = null

			try {
				nextHeader = MP3Frame.Header(b1, b2, b3)
			}
			catch (e: MP3Exception) {
				// not a valid frame header
			}

			if (nextHeader != null && nextHeader.isCompatible(previousHeader)) {
				val frameBytes = ByteArray(nextHeader.frameSize)
				frameBytes[0] = b0.toByte()
				frameBytes[1] = b1.toByte()
				frameBytes[2] = b2.toByte()
				frameBytes[3] = b3.toByte()

				try {
					data.readFully(frameBytes, 4, frameBytes.size - 4)
				}
				catch (e: EOFException) {
					return null
				}

				return MP3Frame(nextHeader, frameBytes)
			}
		}

		data.reset()

		return null
	}

	@Throws(IOException::class, MP3Exception::class)
	fun calculateDuration(data: MP3Input, totalLength: Long, stopCondition: StopReadCondition): Long {
		var frame = readFirstFrame(data, stopCondition)

		return if (frame != null) {
			// check for Xing header
			var numberOfFrames = frame.numberOfFrames

			if (numberOfFrames > 0) { // from Xing/VBRI header
				frame.header.getTotalDuration((numberOfFrames * frame.size).toLong())
			}
			else { // scan file
				numberOfFrames = 1

				val firstFramePosition = data.position - frame.size
				var frameSizeSum = frame.size.toLong()
				val firstFrameBitrate = frame.header.getBitrate()
				var bitrateSum = firstFrameBitrate.toLong()
				var vbr = false
				val cbrThreshold = 10000 / frame.header.duration // assume CBR after 10 seconds

				while (true) {
					if (numberOfFrames == cbrThreshold && !vbr && totalLength > 0) {
						return frame!!.header.getTotalDuration(totalLength - firstFramePosition)
					}

					if (readNextFrame(data, stopCondition, frame).also { frame = it } == null) {
						break
					}

					val bitrate = frame!!.header.getBitrate()

					if (bitrate != firstFrameBitrate) {
						vbr = true
					}

					bitrateSum += bitrate.toLong()
					frameSizeSum += frame!!.size.toLong()
					numberOfFrames++
				}

				1000L * frameSizeSum * numberOfFrames * 8 / bitrateSum
			}
		}
		else {
			throw MP3Exception("No audio frame")
		}
	}

	companion object {
		val LOGGER: Logger = Logger.getLogger(MP3Info::class.java.name)
	}
}
