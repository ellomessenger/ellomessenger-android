/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.InputMedia
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.tlrpc.MessageEntity
import org.telegram.tgnet.TLRPC.ReplyMarkup

class TL_messages_editMessage : TLObject() {
	var flags = 0
	var noWebpage = false
	var peer: InputPeer? = null
	var id = 0
	var message: String? = null
	var media: InputMedia? = null
	var replyMarkup: ReplyMarkup? = null
	var entities = listOf<MessageEntity>()
	var scheduleDate = 0

	override fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		return TLRPC.Updates.TLdeserialize(stream, constructor, exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)

		flags = if (noWebpage) (flags or 2) else (flags and 2.inv())

		stream.writeInt32(flags)

		peer?.serializeToStream(stream)

		stream.writeInt32(id)

		if ((flags and 2048) != 0) {
			stream.writeString(message)
		}

		if ((flags and 16384) != 0) {
			media?.serializeToStream(stream)
		}

		if ((flags and 4) != 0) {
			replyMarkup?.serializeToStream(stream)
		}

		if ((flags and 8) != 0) {
			stream.writeInt32(0x1cb5c415)

			val count = entities.size

			stream.writeInt32(count)

			for (a in 0 until count) {
				entities[a].serializeToStream(stream)
			}
		}

		if ((flags and 32768) != 0) {
			stream.writeInt32(scheduleDate)
		}
	}

	companion object {
		private const val CONSTRUCTOR = 0x48f71778
	}
}
