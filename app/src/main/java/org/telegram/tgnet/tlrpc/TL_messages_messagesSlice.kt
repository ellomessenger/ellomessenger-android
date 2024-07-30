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

class TL_messages_messagesSlice : messages_Messages() {
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
		inexact = flags and 2 != 0
		count = stream.readInt32(exception)

		if (flags and 1 != 0) {
			next_rate = stream.readInt32(exception)
		}

		if (flags and 4 != 0) {
			offset_id_offset = stream.readInt32(exception)
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

		magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = User.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			users.add(`object`)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)

		flags = if (inexact) flags or 2 else flags and 2.inv()
		stream.writeInt32(flags)
		stream.writeInt32(count)

		if (flags and 1 != 0) {
			stream.writeInt32(next_rate)
		}

		if (flags and 4 != 0) {
			stream.writeInt32(offset_id_offset)
		}

		stream.writeInt32(0x1cb5c415)
		stream.writeInt32(messages.size)

		for (message in messages) {
			message.serializeToStream(stream)
		}

		stream.writeInt32(0x1cb5c415)
		stream.writeInt32(chats.size)

		for (chat in chats) {
			chat.serializeToStream(stream)
		}

		stream.writeInt32(0x1cb5c415)
		stream.writeInt32(users.size)

		for (user in users) {
			user.serializeToStream(stream)
		}
	}

	companion object {
		const val constructor = 0x3a54685e
	}
}
