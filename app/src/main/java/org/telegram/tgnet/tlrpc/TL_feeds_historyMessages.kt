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
import org.telegram.tgnet.TLRPC.Chat

class TL_feeds_historyMessages : TLObject() {
	val messages = mutableListOf<Message>()
	val chats = mutableListOf<Chat>()

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

		var magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		var count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = Message.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			messages.add(`object`)
		}

		magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = Chat.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			chats.add(`object`)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)
		stream.writeInt32(0x1cb5c415)

		var count = messages.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			messages[a].serializeToStream(stream)
		}

		stream.writeInt32(0x1cb5c415)

		count = chats.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			chats[a].serializeToStream(stream)
		}
	}

	companion object {
		private const val CONSTRUCTOR = 1001001002

		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_feeds_historyMessages? {
			if (CONSTRUCTOR != constructor) {
				return if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in feeds_historyMessages", constructor))
				}
				else {
					null
				}
			}

			val result = TL_feeds_historyMessages()
			result.readParams(stream, exception)
			return result
		}
	}
}
