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
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.TL_peerBlocked

abstract class contacts_Blocked : TLObject() {
	val blocked = mutableListOf<TL_peerBlocked>()
	val chats = mutableListOf<Chat>()
	val users = mutableListOf<User>()
	var count = 0

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): contacts_Blocked? {
			var result: contacts_Blocked? = null

			when (constructor) {
				0xade1591 -> result = TL_contacts_blocked()
				-0x1e99be6c -> result = TL_contacts_blockedSlice()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in contacts_Blocked", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
