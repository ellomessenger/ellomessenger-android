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
package org.telegram.messenger.audioinfo

import android.graphics.Bitmap
import org.telegram.messenger.audioinfo.m4a.M4AInfo
import org.telegram.messenger.audioinfo.mp3.MP3Info
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

abstract class AudioInfo {
	var brand: String? = null // brand, e.g. "M4A", "ID3", ...
		protected set

	var version: String? = null // version, e.g. "0", "2.3.0", ...
		protected set

	var duration: Long = 0 // track duration (milliseconds)
		protected set

	var title: String? = null // track title
		protected set

	var artist: String? = null // track artist
		protected set

	var albumArtist: String? = null // album artist
		protected set

	var album: String? = null // album title
		protected set

	var year: Short = 0 // year...
		protected set

	var genre: String? = null // genre name
		protected set

	var comment: String? = null // comment...
		protected set

	var track: Short = 0 // track number
		protected set

	var tracks: Short = 0 // number of tracks
		protected set

	var disc: Short = 0 // disc number
		protected set

	var discs: Short = 0 // number of discs
		protected set

	var copyright: String? = null // copyright notice
		protected set

	var composer: String? = null // composer name
		protected set

	var grouping: String? = null // track grouping
		protected set

	var isCompilation = false // compilation flag
		protected set

	var lyrics: String? = null // song lyrics
		protected set

	var cover: Bitmap? = null // cover image data
		protected set

	var smallCover: Bitmap? = null // cover image data
		protected set

	companion object {
		@JvmStatic
		fun getAudioInfo(file: File): AudioInfo? {
			return runCatching {
				val header = ByteArray(12)
				RandomAccessFile(file, "r").use {
					it.readFully(header, 0, 8)
				}

				FileInputStream(file).use { fis ->
					BufferedInputStream(fis).use { bis ->
						if (header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() && header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()) {
							M4AInfo(bis)
						}
						else if (file.absolutePath.endsWith("mp3")) {
							MP3Info(bis, file.length())
						}
						else {
							null
						}
					}
				}
			}.getOrNull()
		}
	}
}
