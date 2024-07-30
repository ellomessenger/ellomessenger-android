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

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

open class PositionInputStream @JvmOverloads constructor(delegate: InputStream?, var position: Long = 0L) : FilterInputStream(delegate) {
	private var positionMark: Long = 0

	@Synchronized
	override fun mark(readlimit: Int) {
		positionMark = position
		super.mark(readlimit)
	}

	@Synchronized
	@Throws(IOException::class)
	override fun reset() {
		super.reset()
		position = positionMark
	}

	@Throws(IOException::class)
	override fun read(): Int {
		val data = super.read()

		if (data >= 0) {
			position++
		}

		return data
	}

	@Throws(IOException::class)
	override fun read(b: ByteArray, off: Int, len: Int): Int {
		val p = position
		val read = super.read(b, off, len)

		if (read > 0) {
			position = p + read
		}

		return read
	}

	@Throws(IOException::class)
	override fun read(b: ByteArray): Int {
		return read(b, 0, b.size)
	}

	@Throws(IOException::class)
	override fun skip(n: Long): Long {
		val p = position
		val skipped = super.skip(n)
		position = p + skipped
		return skipped
	}
}
