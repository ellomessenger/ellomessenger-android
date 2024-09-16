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

class TL_chatInvite : ChatInvite() {
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
		channel = (flags and 1) != 0
		broadcast = (flags and 2) != 0
		isPublic = (flags and 4) != 0
		megagroup = (flags and 8) != 0
		requestNeeded = (flags and 64) != 0
		title = stream.readString(exception)

		val hasAbout = (flags and 32) != 0

		if (hasAbout) {
			about = stream.readString(exception)
		}

		photo = TLRPC.Photo.TLdeserialize(stream, stream.readInt32(exception), exception)
		participantsCount = stream.readInt32(exception)

		if ((flags and 16) != 0) {
			val magic = stream.readInt32(exception)

			if (magic != 0x1cb5c415) {
				if (exception) {
					throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
				}

				return
			}

			val count = stream.readInt32(exception)

			for (a in 0 until count) {
				val `object` = User.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
				participants.add(`object`)
			}
		}

		if ((flags and 128) != 0) {
			chat = TLRPC.Chat.TLdeserialize(stream, stream.readInt32(exception), exception)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)

		flags = if (channel) (flags or 1) else (flags and 1.inv())
		flags = if (broadcast) (flags or 2) else (flags and 2.inv())
		flags = if (isPublic) (flags or 4) else (flags and 4.inv())
		flags = if (megagroup) (flags or 8) else (flags and 8.inv())
		flags = if (about != null) (flags or 32) else (flags and 32.inv())
		flags = if (requestNeeded) (flags or 64) else (flags and 64.inv())
		flags = if (chat != null) (flags or 128) else (flags and 128.inv())

		stream.writeInt32(flags)
		stream.writeString(title)

		if (about != null) {
			stream.writeString(about)
		}

		photo?.serializeToStream(stream)

		stream.writeInt32(participantsCount)

		if ((flags and 16) != 0) {
			stream.writeInt32(0x1cb5c415)

			val count = participants.size

			stream.writeInt32(count)

			for (a in 0 until count) {
				participants[a].serializeToStream(stream)
			}
		}

		if ((flags and 128) != 0) {
			chat?.serializeToStream(stream)
		}
	}

	companion object {
		const val CONSTRUCTOR: Int = 0x300c44c1
	}
}
