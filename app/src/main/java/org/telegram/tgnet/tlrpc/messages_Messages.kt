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

abstract class messages_Messages : TLObject() {
	@JvmField
	val messages = ArrayList<Message>() // TODO: replace with mutableListOf after converting ImageLoader to Kotlin

	@JvmField
	val chats = mutableListOf<Chat>()

	@JvmField
	val users = mutableListOf<User>()

	var flags = 0
	var inexact = false

	@JvmField
	var pts = 0

	@JvmField
	var count = 0

	@JvmField
	var next_rate = 0

	var offset_id_offset = 0

	@JvmField
	var animatedEmoji: ArrayList<TLRPC.Document>? = null

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): messages_Messages? {
			var result: messages_Messages? = null

			when (constructor) {
				0x3a54685e -> result = TL_messages_messagesSlice()
				-0x738e7179 -> result = TL_messages_messages()
				0x64479808 -> result = TL_messages_channelMessages()
				0x74535f21 -> result = TL_messages_messagesNotModified()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in messages_Messages", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
