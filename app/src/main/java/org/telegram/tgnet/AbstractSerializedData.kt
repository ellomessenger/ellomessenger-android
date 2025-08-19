/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.tgnet

abstract class AbstractSerializedData {
	abstract fun writeInt32(x: Int)

	abstract fun writeInt64(x: Long)

	abstract fun writeBool(value: Boolean)

	abstract fun writeBytes(b: ByteArray?)

	abstract fun writeBytes(b: ByteArray?, offset: Int, count: Int)

	abstract fun writeByte(i: Int)

	abstract fun writeByte(b: Byte)

	abstract fun writeString(s: String?)

	abstract fun writeByteArray(b: ByteArray?, offset: Int, count: Int)

	abstract fun writeByteArray(b: ByteArray?)

	abstract fun writeDouble(d: Double)

	abstract fun writeByteBuffer(buffer: NativeByteBuffer?)

	abstract fun readInt32(exception: Boolean): Int

	abstract fun readBool(exception: Boolean): Boolean

	abstract fun readInt64(exception: Boolean): Long

	abstract fun readBytes(b: ByteArray?, exception: Boolean)

	abstract fun readData(count: Int, exception: Boolean): ByteArray?

	abstract fun readString(exception: Boolean): String?

	abstract fun readByteArray(exception: Boolean): ByteArray?

	abstract fun readByteBuffer(exception: Boolean): NativeByteBuffer?

	abstract fun readDouble(exception: Boolean): Double

	abstract fun length(): Int

	abstract fun skip(count: Int)

	abstract val position: Int

	abstract fun remaining(): Int
}
