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
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.tlrpc.messages_Dialogs.Companion.TLdeserialize

class TL_messages_getDialogs : TLObject() {
	var flags = 0
	var excludePinned = false
	var folderId = 0
	var offsetDate = 0
	var offsetId = 0
	var offsetPeer: InputPeer? = null
	var limit = 0
	var hash = 0L

	override fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		return TLdeserialize(stream, constructor, exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)

		flags = if (excludePinned) (flags or 1) else (flags and 1.inv())

		stream.writeInt32(flags)

		if ((flags and 2) != 0) {
			stream.writeInt32(folderId)
		}

		stream.writeInt32(offsetDate)
		stream.writeInt32(offsetId)

		offsetPeer?.serializeToStream(stream)

		stream.writeInt32(limit)
		stream.writeInt64(hash)
	}

	companion object {
		private const val CONSTRUCTOR = -0x5f0b34b1
	}
}
