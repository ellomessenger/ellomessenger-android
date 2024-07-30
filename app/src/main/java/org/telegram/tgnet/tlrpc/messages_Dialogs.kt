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
import org.telegram.tgnet.TLRPC.Chat

abstract class messages_Dialogs : TLObject() {
	var count = 0

	@JvmField
	var dialogs = ArrayList<TLRPC.Dialog>()

	@JvmField
	var messages = ArrayList<Message>()

	@JvmField
	var chats = ArrayList<Chat>()

	@JvmField
	var users = ArrayList<User>()

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): messages_Dialogs? {
			var result: messages_Dialogs? = null

			when (constructor) {
				0x15ba6c40 -> result = TL_messages_dialogs()
				0x71e094f3 -> result = TL_messages_dialogsSlice()
				-0xf1c1a6a -> result = TL_messages_dialogsNotModified()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in messages_Dialogs", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
