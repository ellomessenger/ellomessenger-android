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

abstract class ChatInvite : TLObject() {
	var flags = 0
	var channel = false
	var broadcast = false
	var isPublic = false
	var megagroup = false
	var requestNeeded = false
	var participantsCount = 0
	val participants = mutableListOf<User>()
	var expires = 0
	var user: User? = null
	var about: String? = null

	@JvmField
	var title: String? = null

	@JvmField
	var photo: TLRPC.Photo? = null

	@JvmField
	var chat: Chat? = null

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): ChatInvite? {
			var result: ChatInvite? = null

			when (constructor) {
				TL_chatInvite.CONSTRUCTOR -> result = TL_chatInvite()
				TL_chatInvitePeek.CONSTRUCTOR -> result = TL_chatInvitePeek()
				TL_chatInviteAlready.CONSTRUCTOR -> result = TL_chatInviteAlready()
				TL_chatInviteUser.CONSTRUCTOR -> result = TL_chatInviteUser()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in ChatInvite", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
