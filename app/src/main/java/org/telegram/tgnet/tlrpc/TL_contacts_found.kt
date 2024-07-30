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
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.Peer

class TL_contacts_found : TLObject() {
	val myResults = mutableListOf<Peer>()
	val results = mutableListOf<Peer>()
	val chats = mutableListOf<Chat>()
	val users = mutableListOf<User>()

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
			val `object` = Peer.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			myResults.add(`object`)
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
			val `object` = Peer.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			results.add(`object`)
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
		stream.writeInt32(0x1cb5c415)

		var count = myResults.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			myResults[a].serializeToStream(stream)
		}

		stream.writeInt32(0x1cb5c415)

		count = results.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			results[a].serializeToStream(stream)
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
	}

	companion object {
		private const val constructor = -0x4cecb263

		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_contacts_found? {
			if (Companion.constructor != constructor) {
				return if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_contacts_found", constructor))
				}
				else {
					null
				}
			}

			val result = TL_contacts_found()
			result.readParams(stream, exception)
			return result
		}
	}
}
