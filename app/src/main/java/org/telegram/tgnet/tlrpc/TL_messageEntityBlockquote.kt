/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Oleksandr Nahornyi, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.messenger.FileLog
import org.telegram.tgnet.AbstractSerializedData
import java.util.Objects

class TL_messageEntityBlockquote : MessageEntity() {
	override fun equals(other: Any?): Boolean {
		if (other !is TL_messageEntityBlockquote) {
			return false
		}

		if (this === other) {
			return true
		}

		return offset == other.offset && length == other.length
	}

	override fun readParams(stream: AbstractSerializedData?, exception: Boolean) {
		if (stream == null) {
			if (exception) {
				throw RuntimeException("Input stream is null")
			}
			else {
				FileLog.e("Input stream is null")
			}

			return
		}


		offset = stream.readInt32(exception)
		length = stream.readInt32(exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)
		stream.writeInt32(offset)
		stream.writeInt32(length)
	}

	override fun hashCode(): Int {
		return Objects.hash(CONSTRUCTOR, offset, length)
	}

	companion object {
		const val CONSTRUCTOR = 0x20df5d0
	}
}
