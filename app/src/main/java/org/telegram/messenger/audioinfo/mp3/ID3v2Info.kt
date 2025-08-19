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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.telegram.messenger.audioinfo.AudioInfo
import org.telegram.messenger.audioinfo.mp3.ID3v1Genre.Companion.getGenre
import java.io.IOException
import java.io.InputStream
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.max

class ID3v2Info @JvmOverloads constructor(input: InputStream, private val debugLevel: Level = Level.FINEST) : AudioInfo() {
	class AttachedPicture(val type: Byte, val description: String, val imageType: String, val imageData: ByteArray) {
		companion object {
			const val TYPE_OTHER: Byte = 0x00
			const val TYPE_COVER_FRONT: Byte = 0x03
		}
	}

	class CommentOrUnsynchronizedLyrics(val language: String, val description: String?, val text: String)

	private var coverPictureType: Byte = 0

	init {
		if (isID3v2StartPosition(input)) {
			val tagHeader = ID3v2TagHeader(input)

			brand = "ID3"
			version = String.format("2.%d.%d", tagHeader.version, tagHeader.revision)

			val tagBody = tagHeader.tagBody(input)

			try {
				while (tagBody.remainingLength > 10) { // TODO > tag.minimumFrameSize()
					val frameHeader = ID3v2FrameHeader(tagBody)

					if (frameHeader.isPadding) { // we ran into padding
						break
					}

					if (frameHeader.bodySize > tagBody.remainingLength) { // something wrong...
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "ID3 frame claims to extend frames area")
						}

						break
					}

					if (frameHeader.isValid && !frameHeader.isEncryption) {
						val frameBody = tagBody.frameBody(frameHeader)

						try {
							parseFrame(frameBody)
						}
						catch (e: ID3v2Exception) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, String.format("ID3 exception occured in frame %s: %s", frameHeader.frameId, e.message))
							}
						}
						finally {
							frameBody.data.skipFully(frameBody.remainingLength)
						}
					}
					else {
						tagBody.data.skipFully(frameHeader.bodySize.toLong())
					}
				}
			}
			catch (e: ID3v2Exception) {
				if (LOGGER.isLoggable(debugLevel)) {
					LOGGER.log(debugLevel, "ID3 exception occured: " + e.message)
				}
			}

			tagBody.data.skipFully(tagBody.remainingLength)

			if (tagHeader.footerSize > 0) {
				input.skip(tagHeader.footerSize.toLong())
			}
		}
	}

	@Throws(IOException::class, ID3v2Exception::class)
	fun parseFrame(frame: ID3v2FrameBody) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, "Parsing frame: " + frame.frameHeader.frameId)
		}
		when (frame.frameHeader.frameId) {
			"PIC", "APIC" -> if (cover == null || coverPictureType != AttachedPicture.TYPE_COVER_FRONT) {
				val picture = parseAttachedPictureFrame(frame)

				if (cover == null || picture.type == AttachedPicture.TYPE_COVER_FRONT || picture.type == AttachedPicture.TYPE_OTHER) {
					try {
						val bytes = picture.imageData

						val opts = BitmapFactory.Options()
						opts.inJustDecodeBounds = true
						opts.inSampleSize = 1

						BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

						if (opts.outWidth > 800 || opts.outHeight > 800) {
							var size = max(opts.outWidth, opts.outHeight)

							while (size > 800) {
								opts.inSampleSize *= 2
								size /= 2
							}
						}

						opts.inJustDecodeBounds = false

						cover = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

						if (cover != null) {
							val scale = max(cover!!.width, cover!!.height) / 120.0f

							smallCover = if (scale > 0) {
								Bitmap.createScaledBitmap(cover!!, (cover!!.width / scale).toInt(), (cover!!.height / scale).toInt(), true)
							}
							else {
								cover
							}

							if (smallCover == null) {
								smallCover = cover
							}
						}
					}
					catch (e: Throwable) {
						e.printStackTrace()
					}

					coverPictureType = picture.type
				}
			}

			"COM", "COMM" -> {
				val comm = parseCommentOrUnsynchronizedLyricsFrame(frame)

				if (comment == null || comm.description == null || "" == comm.description) { // prefer "default" comment (without description)
					comment = comm.text
				}
			}

			"TAL", "TALB" -> {
				album = parseTextFrame(frame)
			}

			"TCP", "TCMP" -> {
				isCompilation = "1" == parseTextFrame(frame)
			}

			"TCM", "TCOM" -> {
				composer = parseTextFrame(frame)
			}

			"TCO", "TCON" -> {
				val tcon = parseTextFrame(frame)

				if (tcon.isNotEmpty()) {
					genre = tcon

					try {
						var id3v1Genre: ID3v1Genre? = null

						if (tcon[0] == '(') {
							val pos = tcon.indexOf(')')

							if (pos > 1) { // (123)
								id3v1Genre = getGenre(tcon.substring(1, pos).toInt())

								if (id3v1Genre == null && tcon.length > pos + 1) { // (789)Special
									genre = tcon.substring(pos + 1)
								}
							}
						}
						else { // 123
							id3v1Genre = getGenre(tcon.toInt())
						}

						if (id3v1Genre != null) {
							genre = id3v1Genre.description
						}
					}
					catch (e: NumberFormatException) {
						// ignore
					}
				}
			}

			"TCR", "TCOP" -> {
				copyright = parseTextFrame(frame)
			}

			"TDRC" -> {
				val tdrc = parseTextFrame(frame)

				if (tdrc.length >= 4) {
					try {
						year = tdrc.substring(0, 4).toShort()
					}
					catch (e: NumberFormatException) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse year from: $tdrc")
						}
					}
				}
			}

			"TLE", "TLEN" -> {
				val tlen = parseTextFrame(frame)

				try {
					duration = java.lang.Long.valueOf(tlen)
				}
				catch (e: NumberFormatException) {
					if (LOGGER.isLoggable(debugLevel)) {
						LOGGER.log(debugLevel, "Could not parse track duration: $tlen")
					}
				}
			}

			"TP1", "TPE1" -> {
				artist = parseTextFrame(frame)
			}

			"TP2", "TPE2" -> {
				albumArtist = parseTextFrame(frame)
			}

			"TPA", "TPOS" -> {
				val tpos = parseTextFrame(frame)

				if (tpos.isNotEmpty()) {
					val index = tpos.indexOf('/')

					if (index < 0) {
						try {
							disc = tpos.toShort()
						}
						catch (e: NumberFormatException) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse disc number: $tpos")
							}
						}
					}
					else {
						try {
							disc = tpos.substring(0, index).toShort()
						}
						catch (e: NumberFormatException) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse disc number: $tpos")
							}
						}

						try {
							discs = tpos.substring(index + 1).toShort()
						}
						catch (e: NumberFormatException) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse number of discs: $tpos")
							}
						}
					}
				}
			}

			"TRK", "TRCK" -> {
				val trck = parseTextFrame(frame)

				if (trck.isNotEmpty()) {
					val index = trck.indexOf('/')

					if (index < 0) {
						try {
							track = trck.toShort()
						}
						catch (e: NumberFormatException) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse track number: $trck")
							}
						}
					}
					else {
						try {
							track = trck.substring(0, index).toShort()
						}
						catch (e: NumberFormatException) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse track number: $trck")
							}
						}

						try {
							tracks = trck.substring(index + 1).toShort()
						}
						catch (e: NumberFormatException) {
							if (LOGGER.isLoggable(debugLevel)) {
								LOGGER.log(debugLevel, "Could not parse number of tracks: $trck")
							}
						}
					}
				}
			}

			"TT1", "TIT1" -> {
				grouping = parseTextFrame(frame)
			}

			"TT2", "TIT2" -> {
				title = parseTextFrame(frame)
			}

			"TYE", "TYER" -> {
				val tyer = parseTextFrame(frame)

				if (tyer.isNotEmpty()) {
					try {
						year = tyer.toShort()
					}
					catch (e: NumberFormatException) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse year: $tyer")
						}
					}
				}
			}

			"ULT", "USLT" -> {
				if (lyrics == null) {
					lyrics = parseCommentOrUnsynchronizedLyricsFrame(frame).text
				}
			}

			else -> {
				// unused
			}
		}
	}

	@Throws(IOException::class, ID3v2Exception::class)
	fun parseTextFrame(frame: ID3v2FrameBody): String {
		val encoding = frame.readEncoding()
		return frame.readFixedLengthString(frame.remainingLength.toInt(), encoding)
	}

	@Throws(IOException::class, ID3v2Exception::class)
	fun parseCommentOrUnsynchronizedLyricsFrame(data: ID3v2FrameBody): CommentOrUnsynchronizedLyrics {
		val encoding = data.readEncoding()
		val language = data.readFixedLengthString(3, ID3v2Encoding.ISO_8859_1)
		val description = data.readZeroTerminatedString(200, encoding)
		val text = data.readFixedLengthString(data.remainingLength.toInt(), encoding)
		return CommentOrUnsynchronizedLyrics(language, description, text)
	}

	@Throws(IOException::class, ID3v2Exception::class)
	fun parseAttachedPictureFrame(data: ID3v2FrameBody): AttachedPicture {
		val encoding = data.readEncoding()

		val imageType = if (data.tagHeader.version == 2) { // file type, e.g. "JPG"
			val fileType = data.readFixedLengthString(3, ID3v2Encoding.ISO_8859_1)

			when (fileType.uppercase()) {
				"PNG" -> "image/png"
				"JPG" -> "image/jpeg"
				else -> "image/unknown"
			}
		}
		else { // mime type, e.g. "image/jpeg"
			data.readZeroTerminatedString(20, ID3v2Encoding.ISO_8859_1)
		}

		val pictureType = data.data.readByte()
		val description = data.readZeroTerminatedString(200, encoding)
		val imageData = data.data.readFully(data.remainingLength.toInt())

		return AttachedPicture(pictureType, description, imageType, imageData)
	}

	companion object {
		val LOGGER = Logger.getLogger(ID3v2Info::class.java.name)

		@JvmStatic
		@Throws(IOException::class)
		fun isID3v2StartPosition(input: InputStream): Boolean {
			input.mark(3)

			return try {
				input.read() == 'I'.code && input.read() == 'D'.code && input.read() == '3'.code
			}
			finally {
				input.reset()
			}
		}
	}
}
