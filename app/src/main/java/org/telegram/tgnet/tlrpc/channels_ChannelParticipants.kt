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
import org.telegram.tgnet.TLRPC.ChannelParticipant
import org.telegram.tgnet.TLRPC.TL_channels_channelParticipantsNotModified

abstract class channels_ChannelParticipants : TLObject() {
	var count = 0

	@JvmField
	val participants = mutableListOf<ChannelParticipant>()

	@JvmField
	val users = mutableListOf<User>()

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): channels_ChannelParticipants? {
			var result: channels_ChannelParticipants? = null

			when (constructor) {
				-0x654f0151 -> result = TL_channels_channelParticipants()
				-0xfe8c017 -> result = TL_channels_channelParticipantsNotModified()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in channels_ChannelParticipants", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
