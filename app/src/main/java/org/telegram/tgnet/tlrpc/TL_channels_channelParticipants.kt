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
import org.telegram.tgnet.TLRPC.ChannelParticipant
import org.telegram.tgnet.TLRPC.Chat

class TL_channels_channelParticipants : channels_ChannelParticipants() {
	@JvmField
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

		count = stream.readInt32(exception)

		var magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		var count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = ChannelParticipant.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			participants.add(`object`)
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
		stream.writeInt32(count)
		stream.writeInt32(0x1cb5c415)
		stream.writeInt32(participants.size)

		for (participant in participants) {
			participant.serializeToStream(stream)
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
		const val constructor = -0x654f0151
	}
}
