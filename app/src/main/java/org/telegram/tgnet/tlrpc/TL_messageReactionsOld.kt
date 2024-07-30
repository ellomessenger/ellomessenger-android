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
import org.telegram.tgnet.TLRPC.MessagePeerReaction

class TL_messageReactionsOld : TL_messageReactions() {
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
		canSeeList = (flags and 4) != 0

		var magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		var count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = ReactionCount.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			results.add(`object`)
		}

		if ((flags and 2) != 0) {
			magic = stream.readInt32(exception)

			if (magic != 0x1cb5c415) {
				if (exception) {
					throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
				}

				return
			}

			count = stream.readInt32(exception)

			for (a in 0 until count) {
				val `object` = MessagePeerReaction.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
				recentReactions.add(`object`)
			}
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)

		flags = if (min) (flags or 1) else (flags and 1.inv())
		flags = if (canSeeList) (flags or 4) else (flags and 4.inv())

		stream.writeInt32(flags)
		stream.writeInt32(0x1cb5c415)

		var count = results.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			results[a].serializeToStream(stream)
		}

		if ((flags and 2) != 0) {
			stream.writeInt32(0x1cb5c415)

			count = recentReactions.size

			stream.writeInt32(count)

			for (a in 0 until count) {
				recentReactions[a].serializeToStream(stream)
			}
		}
	}

	companion object {
		const val CONSTRUCTOR: Int = 0x87b6e36

		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_messageReactions? {
			if (TL_messageReactions.CONSTRUCTOR != constructor) {
				if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_messageReactions", constructor))
				}
				else {
					return null
				}
			}

			val result = TL_messageReactions()
			result.readParams(stream, exception)
			return result
		}
	}
}
