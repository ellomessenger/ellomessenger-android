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
import java.io.EOFException
import java.io.IOException
import java.math.BigDecimal
import java.nio.charset.Charset

class MP4Atom(input: RangeInputStream, parent: MP4Box<PositionInputStream>?, type: String) : MP4Box<RangeInputStream>(input, parent as MP4Box<out RangeInputStream>?, type) {
	val length: Long
		get() = input.position + ((input as? RangeInputStream)?.remainingLength ?: 0)

	val remaining: Long
		get() = (input as? RangeInputStream)?.remainingLength ?: 0

	val offset: Long
		get() = (parent?.position ?: 0) - position

	fun hasMoreChildren(): Boolean {
		return (child?.remaining ?: 0) < this.remaining
	}

	@Throws(IOException::class)
	fun nextChildUpTo(expectedTypeExpression: String): MP4Atom {
		while (this.remaining > 0) {
			val atom = nextChild()
			if (atom.type.matches(expectedTypeExpression.toRegex())) {
				return atom
			}
		}
		throw IOException("atom type mismatch, not found: $expectedTypeExpression")
	}

	@Throws(IOException::class)
	fun readBoolean(): Boolean {
		return data.readBoolean()
	}

	@Throws(IOException::class)
	fun readByte(): Byte {
		return data.readByte()
	}

	@Throws(IOException::class)
	fun readShort(): Short {
		return data.readShort()
	}

	@Throws(IOException::class)
	fun readInt(): Int {
		return data.readInt()
	}

	@Throws(IOException::class)
	fun readLong(): Long {
		return data.readLong()
	}

	@JvmOverloads
	@Throws(IOException::class)
	fun readBytes(len: Int = this.remaining.toInt()): ByteArray {
		val bytes = ByteArray(len)
		data.readFully(bytes)
		return bytes
	}

	@Throws(IOException::class)
	fun readShortFixedPoint(): BigDecimal {
		val integer = data.readByte().toInt()
		val decimal = data.readUnsignedByte()
		return BigDecimal(integer.toString() + "" + decimal.toString())
	}

	@Throws(IOException::class)
	fun readIntegerFixedPoint(): BigDecimal {
		val integer = data.readShort().toInt()
		val decimal = data.readUnsignedShort()
		return BigDecimal(integer.toString() + "" + decimal.toString())
	}

	@Throws(IOException::class)
	fun readString(len: Int, enc: String): String {
		val s = String(readBytes(len), Charset.forName(enc))
		val end = s.indexOf(0.toChar())
		return if (end < 0) s else s.substring(0, end)
	}

	@Throws(IOException::class)
	fun readString(enc: String): String {
		return readString(this.remaining.toInt(), enc)
	}

	@Throws(IOException::class)
	fun skip(len: Int) {
		var total = 0

		while (total < len) {
			val current = data.skipBytes(len - total)

			total += if (current > 0) {
				current
			}
			else {
				throw EOFException()
			}
		}
	}

	@Throws(IOException::class)
	fun skip() {
		while (this.remaining > 0) {
			if (input.skip(this.remaining) == 0L) {
				throw EOFException("Cannot skip atom")
			}
		}
	}

	private fun appendPath(s: StringBuffer, box: MP4Box<*>): StringBuffer {
		if (box.parent != null) {
			appendPath(s, box.parent)
			s.append("/")
		}

		return s.append(box.type)
	}

	val path: String
		get() = appendPath(StringBuffer(), this).toString()

	override fun toString(): String {
		val s = StringBuffer()

		appendPath(s, this)

		s.append("[off=")
		s.append(offset)
		s.append(",pos=")
		s.append(position)
		s.append(",len=")
		s.append(length)
		s.append("]")

		return s.toString()
	}
}
