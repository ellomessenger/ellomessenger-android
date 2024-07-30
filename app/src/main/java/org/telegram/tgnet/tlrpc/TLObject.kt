/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.NativeByteBuffer

open class TLObject {
	@JvmField
	var networkType = 0

	@JvmField
	var disableFree = false

	open fun readParams(stream: AbstractSerializedData?, exception: Boolean) {}
	open fun serializeToStream(stream: AbstractSerializedData?) {}
	open fun freeResources() {}

	open fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		return null
	}

	val objectSize: Int
		get() {
			val byteBuffer = sizeCalculator.get() ?: return 0
			byteBuffer.rewind()
			serializeToStream(sizeCalculator.get())
			return byteBuffer.length()
		}

	companion object {
		private val sizeCalculator = object : ThreadLocal<NativeByteBuffer>() {
			override fun initialValue(): NativeByteBuffer {
				return NativeByteBuffer(true)
			}
		}
	}
}
