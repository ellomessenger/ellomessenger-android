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
import org.telegram.tgnet.TLRPC.MessagePeerReaction

abstract class MessageReactions : TLObject() {
	var flags = 0
	var min = false
	var canSeeList = false
	val results = mutableListOf<ReactionCount>()
	val recentReactions = mutableListOf<MessagePeerReaction>()

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_messageReactions? {
			var result: TL_messageReactions? = null

			when (constructor) {
				TL_messageReactionsOld.CONSTRUCTOR -> result = TL_messageReactionsOld()
				TL_messageReactions.CONSTRUCTOR -> result = TL_messageReactions()
				TL_messageReactions_layer137.CONSTRUCTOR -> result = TL_messageReactions_layer137()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in MessageReactions", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
