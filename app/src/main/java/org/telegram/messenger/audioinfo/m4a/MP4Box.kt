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

import org.telegram.messenger.audioinfo.util.PositionInputStream
import org.telegram.messenger.audioinfo.util.RangeInputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

open class MP4Box<out I : PositionInputStream>(val input: I, val parent: MP4Box<I>?, val type: String) {
	protected val data: DataInput

	protected var child: MP4Atom? = null
		private set

	init {
		data = DataInputStream(input)
	}

	val position: Long
		get() = input.position

	@Throws(IOException::class)
	fun nextChild(): MP4Atom {
		child?.skip()

		val atomLength = data.readInt()
		val typeBytes = ByteArray(4)

		data.readFully(typeBytes)

		val atomType = String(typeBytes, ASCII)

		val atomInput = if (atomLength == 1) { // extended length
			RangeInputStream(input, 16, data.readLong() - 16)
		}
		else {
			RangeInputStream(input, 8, (atomLength - 8).toLong())
		}

		return MP4Atom(atomInput, this, atomType).also { child = it }
	}

	@Throws(IOException::class)
	fun nextChild(expectedTypeExpression: String): MP4Atom {
		val atom = nextChild()

		if (atom.type.matches(expectedTypeExpression.toRegex())) {
			return atom
		}

		throw IOException("atom type mismatch, expected " + expectedTypeExpression + ", got " + atom.type)
	}

	companion object {
		protected val ASCII: Charset = StandardCharsets.ISO_8859_1 // "ISO8859_1"
	}
}
