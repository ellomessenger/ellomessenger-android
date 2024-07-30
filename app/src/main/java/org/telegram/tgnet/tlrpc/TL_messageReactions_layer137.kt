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

class TL_messageReactions_layer137 : TL_messageReactions() {
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

		flags = stream.readInt32(exception)
		min = (flags and 1) != 0

		val magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		val count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = ReactionCount.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			results.add(`object`)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)

		flags = if (min) (flags or 1) else (flags and 1.inv())

		stream.writeInt32(flags)
		stream.writeInt32(0x1cb5c415)

		val count = results.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			results[a].serializeToStream(stream)
		}
	}

	companion object {
		const val CONSTRUCTOR = -0x4785db2f
	}
}
