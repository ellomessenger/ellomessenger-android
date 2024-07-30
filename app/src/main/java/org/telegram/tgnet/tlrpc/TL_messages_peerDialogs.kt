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
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.TL_updates_state

class TL_messages_peerDialogs : TLObject() {
	val dialogs = ArrayList<TLRPC.Dialog>()
	val messages = ArrayList<Message>()
	val chats = ArrayList<Chat>()
	val users = ArrayList<User>()
	var state: TL_updates_state? = null

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
			val `object` = TLRPC.Dialog.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			dialogs.add(`object`)
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

		state = TL_updates_state.TLdeserialize(stream, stream.readInt32(exception), exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)
		stream.writeInt32(0x1cb5c415)

		var count = dialogs.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			dialogs[a].serializeToStream(stream)
		}

		stream.writeInt32(0x1cb5c415)

		count = messages.size

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

		stream.writeInt32(0x1cb5c415)

		count = users.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			users[a].serializeToStream(stream)
		}

		state?.serializeToStream(stream)
	}

	companion object {
		const val constructor = 0x3371c354

		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_messages_peerDialogs? {
			if (Companion.constructor != constructor) {
				return if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_messages_peerDialogs", constructor))
				}
				else {
					null
				}
			}

			val result = TL_messages_peerDialogs()
			result.readParams(stream, exception)
			return result
		}
	}
}
