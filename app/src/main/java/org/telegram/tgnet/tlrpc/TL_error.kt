/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.messenger.FileLog
import org.telegram.tgnet.AbstractSerializedData

class TL_error : TLObject() {
	@JvmField
	var code = UNKNOWN_ERROR

	@JvmField
	var text: String? = null

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

		code = stream.readInt32(exception)
		text = stream.readString(exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)
		stream.writeInt32(code)
		stream.writeString(text)
	}

	companion object {
		const val CONSTRUCTOR: Int = -0x3b460645
		const val UNKNOWN_ERROR = 0

		fun TLdeserialize(stream: AbstractSerializedData, constructor: Int, exception: Boolean): TL_error? {
			if (CONSTRUCTOR != constructor) {
				if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_error", constructor))
				}
				else {
					return null
				}
			}

			val result = TL_error()
			result.readParams(stream, exception)
			return result
		}
	}
}
