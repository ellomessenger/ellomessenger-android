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
package org.telegram.messenger.audioinfo.m4a

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.telegram.messenger.audioinfo.AudioInfo
import org.telegram.messenger.audioinfo.mp3.ID3v1Genre.Companion.getGenre
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.abs
import kotlin.math.max

class M4AInfo @JvmOverloads constructor(input: InputStream?, private val debugLevel: Level = Level.FINEST) : AudioInfo() {
	var volume: BigDecimal? = null // normal = 1.0
		private set

	var speed: BigDecimal? = null // normal = 1.0
		private set

	var tempo: Short = 0
		private set

	var rating: Byte = 0 // none = 0, clean = 2, explicit = 4
		private set

	init {
		val mp4 = MP4Input(input)

		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, mp4.toString())
		}

		ftyp(mp4.nextChild("ftyp"))
		moov(mp4.nextChildUpTo("moov"))
	}

	@Throws(IOException::class)
	fun ftyp(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		brand = atom.readString(4, ASCII).trim()

		if (brand?.matches("M4V|MP4|mp42|isom".toRegex()) == true) { // experimental file types
			LOGGER.warning(atom.path + ": brand=" + brand + " (experimental)")
		}
		else if (brand?.matches("M4A|M4P".toRegex()) != true) {
			LOGGER.warning(atom.path + ": brand=" + brand + " (expected M4A or M4P)")
		}

		version = atom.readInt().toString()
	}

	@Throws(IOException::class)
	fun moov(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		while (atom.hasMoreChildren()) {
			val child = atom.nextChild()

			when (child.type) {
				"mvhd" -> mvhd(child)
				"trak" -> trak(child)
				"udta" -> udta(child)
				else -> {}
			}
		}
	}

	@Throws(IOException::class)
	fun mvhd(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		val version = atom.readByte()

		atom.skip(3) // flags
		atom.skip(if (version.toInt() == 1) 16 else 8) // created/modified date

		val scale = atom.readInt()
		val units = if (version.toInt() == 1) atom.readLong() else atom.readInt().toLong()

		if (duration == 0L) {
			duration = 1000 * units / scale
		}
		else if (LOGGER.isLoggable(debugLevel) && abs(duration - 1000 * units / scale) > 2) {
			LOGGER.log(debugLevel, "mvhd: duration " + duration + " -> " + 1000 * units / scale)
		}

		speed = atom.readIntegerFixedPoint()
		volume = atom.readShortFixedPoint()
	}

	@Throws(IOException::class)
	fun trak(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		mdia(atom.nextChildUpTo("mdia"))
	}

	@Throws(IOException::class)
	fun mdia(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		mdhd(atom.nextChild("mdhd"))
	}

	@Throws(IOException::class)
	fun mdhd(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		val version = atom.readByte()

		atom.skip(3)
		atom.skip(if (version.toInt() == 1) 16 else 8) // created/modified date

		val sampleRate = atom.readInt()
		val samples = if (version.toInt() == 1) atom.readLong() else atom.readInt().toLong()

		if (duration == 0L) {
			duration = 1000 * samples / sampleRate
		}
		else if (LOGGER.isLoggable(debugLevel) && abs(duration - 1000 * samples / sampleRate) > 2) {
			LOGGER.log(debugLevel, "mdhd: duration " + duration + " -> " + 1000 * samples / sampleRate)
		}
	}

	@Throws(IOException::class)
	fun udta(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		while (atom.hasMoreChildren()) {
			val child = atom.nextChild()

			if ("meta" == child.type) {
				meta(child)
				break
			}
		}
	}

	@Throws(IOException::class)
	fun meta(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		atom.skip(4) // version/flags

		while (atom.hasMoreChildren()) {
			val child = atom.nextChild()

			if ("ilst" == child.type) {
				ilst(child)
				break
			}
		}
	}

	@Throws(IOException::class)
	fun ilst(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		while (atom.hasMoreChildren()) {
			val child = atom.nextChild()

			if (LOGGER.isLoggable(debugLevel)) {
				LOGGER.log(debugLevel, child.toString())
			}

			if (child.remaining == 0L) {
				if (LOGGER.isLoggable(debugLevel)) {
					LOGGER.log(debugLevel, child.path + ": contains no value")
				}

				continue
			}

			data(child.nextChildUpTo("data"))
		}
	}

	@Throws(IOException::class)
	fun data(atom: MP4Atom) {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString())
		}

		atom.skip(4) // version & flags
		atom.skip(4) // reserved

		when (atom.parent?.type) {
			"©alb" -> {
				album = atom.readString(UTF_8)
			}

			"aART" -> {
				albumArtist = atom.readString(UTF_8)
			}

			"©ART" -> {
				artist = atom.readString(UTF_8)
			}

			"©cmt" -> {
				comment = atom.readString(UTF_8)
			}

			"©com", "©wrt" -> {
				if (composer?.trim().isNullOrEmpty()) {
					composer = atom.readString(UTF_8)
				}
			}

			"covr" -> {
				try {
					val bytes = atom.readBytes()

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
				catch (e: Exception) {
					e.printStackTrace()
				}
			}

			"cpil" -> {
				isCompilation = atom.readBoolean()
			}

			"cprt", "©cpy" -> {
				if (copyright?.trim().isNullOrEmpty()) {
					copyright = atom.readString(UTF_8)
				}
			}

			"©day" -> {
				val day = atom.readString(UTF_8).trim()

				if (day.length >= 4) {
					try {
						year = day.substring(0, 4).toShort()
					}
					catch (e: NumberFormatException) {
						// ignore
					}
				}
			}

			"disk" -> {
				atom.skip(2) // padding?
				disc = atom.readShort()
				discs = atom.readShort()
			}

			"gnre" -> {
				if (genre?.trim().isNullOrEmpty()) {
					if (atom.remaining == 2L) { // id3v1 genre?
						val index = atom.readShort() - 1
						val id3v1Genre = getGenre(index)

						if (id3v1Genre != null) {
							genre = id3v1Genre.description
						}
					}
					else {
						genre = atom.readString(UTF_8)
					}
				}
			}

			"©gen" -> {
				if (genre?.trim().isNullOrEmpty()) {
					genre = atom.readString(UTF_8)
				}
			}

			"©grp" -> {
				grouping = atom.readString(UTF_8)
			}

			"©lyr" -> {
				lyrics = atom.readString(UTF_8)
			}

			"©nam" -> {
				title = atom.readString(UTF_8)
			}

			"rtng" -> {
				rating = atom.readByte()
			}

			"tmpo" -> {
				tempo = atom.readShort()
			}

			"trkn" -> {
				atom.skip(2) // padding?
				track = atom.readShort()
				tracks = atom.readShort()
			}

			else -> {
				// unused
			}
		}
	}

	companion object {
		val LOGGER: Logger = Logger.getLogger(M4AInfo::class.java.name)
		private const val ASCII = "ISO8859_1"
		private const val UTF_8 = "UTF-8"
	}
}
