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

abstract class ReactionCount : TLObject() {
	var flags = 0
	var chosenOrder = 0
	var chosen = false
	var lastDrawnPosition = 0
	var reaction: Reaction? = null
	var count = 0

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): ReactionCount? {
			var result: ReactionCount? = null

			when (constructor) {
				TL_reactionCount.CONSTRUCTOR -> result = TL_reactionCount()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in ReactionCount", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
