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
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.TLRPC.Update

class TL_updateMessageReactions : Update() {
	var peer: Peer? = null
	var msgId = 0
	var reactions: TL_messageReactions? = null
	var updateUnreadState = true // custom

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

		peer = Peer.TLdeserialize(stream, stream.readInt32(exception), exception)
		msgId = stream.readInt32(exception)
		reactions = MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)
		peer?.serializeToStream(stream)
		stream.writeInt32(msgId)
		reactions?.serializeToStream(stream)
	}

	companion object {
		const val CONSTRUCTOR: Int = 0x154798c3
	}
}
