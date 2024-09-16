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

class TL_chatInviteAlready : ChatInvite() {
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

		if ((flags and 2) != 0) {
			user = User.TLdeserialize(stream, stream.readInt32(exception), exception)
		}
		else {
			chat = TLRPC.Chat.TLdeserialize(stream, stream.readInt32(exception), exception)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)

		flags = if ((user == null)) (flags and 2.inv()) else (flags or 2)

		stream.writeInt32(flags)

		user?.serializeToStream(stream) ?: chat?.serializeToStream(stream)
	}

	companion object {
		const val CONSTRUCTOR: Int = 0x5a686d7c
	}
}
