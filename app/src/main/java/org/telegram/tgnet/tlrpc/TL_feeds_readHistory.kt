/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.messenger.FileLog
import org.telegram.tgnet.AbstractSerializedData

class TL_feeds_readHistory : TLObject() {
	var page = 0
	var limit = 0
	var flags = 0
	var isExplore = false

	override fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		return TL_feeds_historyMessages.TLdeserialize(stream, constructor, exception)
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

		page = stream.readInt32(exception)
		limit = stream.readInt32(exception)
		flags = stream.readInt32(exception)
		isExplore = flags and 4 != 0
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)
		stream.writeInt32(page)
		stream.writeInt32(limit)

		flags = if (isExplore) flags or 4 else flags and 4.inv()

		stream.writeInt32(flags)
	}

	companion object {
		const val constructor = 1001001001

		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_feeds_readHistory? {
			if (Companion.constructor != constructor) {
				return if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_feeds_readHistory", constructor))
				}
				else {
					null
				}
			}

			val result = TL_feeds_readHistory()
			result.readParams(stream, exception)
			return result
		}
	}
}
