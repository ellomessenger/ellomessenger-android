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
package org.telegram.messenger.audioinfo.util

import java.io.IOException
import java.io.InputStream

/**
 * Input stream filter that keeps track of the current read position
 * and has a read length limit.
 */
class RangeInputStream(delegate: InputStream?, position: Long, length: Long) : PositionInputStream(delegate, position) {
	private val endPosition = position + length

	val remainingLength: Long
		get() = endPosition - position

	@Throws(IOException::class)
	override fun read(): Int {
		return if (position == endPosition) {
			-1
		}
		else super.read()
	}

	@Throws(IOException::class)
	override fun read(b: ByteArray, off: Int, len: Int): Int {
		@Suppress("NAME_SHADOWING") var len = len
		if (position + len > endPosition) {
			len = (endPosition - position).toInt()

			if (len == 0) {
				return -1
			}
		}

		return super.read(b, off, len)
	}

	@Throws(IOException::class)
	override fun skip(n: Long): Long {
		@Suppress("NAME_SHADOWING") var n = n

		if (position + n > endPosition) {
			n = (endPosition - position).toInt().toLong()
		}

		return super.skip(n)
	}
}
