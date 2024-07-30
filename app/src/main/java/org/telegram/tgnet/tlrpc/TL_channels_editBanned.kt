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
import org.telegram.tgnet.TLRPC.InputChannel
import org.telegram.tgnet.TLRPC.InputPeer

class TL_channels_editBanned : TLObject() {
	var channel: InputChannel? = null
	var participant: InputPeer? = null
	var banned_rights: TL_chatBannedRights? = null

	override fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		return TLRPC.Updates.TLdeserialize(stream, constructor, exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)

		channel?.serializeToStream(stream)
		participant?.serializeToStream(stream)
		banned_rights?.serializeToStream(stream)
	}

	companion object {
		const val constructor = -0x6919327f
	}
}
