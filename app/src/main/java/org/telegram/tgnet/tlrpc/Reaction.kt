/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024
 */
package org.telegram.tgnet.tlrpc

import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.tlrpc.TL_reactionCustomEmoji

abstract class Reaction : TLObject() {
	override fun equals(other: Any?): Boolean {
		if (other == null) {
			return false
		}

		if (this is TL_reactionEmpty && other is TL_reactionEmpty) {
			return true
		}

		if (this is TL_reactionEmoji && other is TL_reactionEmoji) {
			return this.emoticon == other.emoticon
		}

		if (this is TL_reactionCustomEmoji && other is TL_reactionCustomEmoji) {
			return this.documentId == other.documentId
		}

		return false
	}

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): Reaction? {
			var result: Reaction? = null

			when (constructor) {
				-0x76ca038d -> result = TL_reactionCustomEmoji()
				0x79f5d419 -> result = TL_reactionEmpty()
				0x1b2286b8 -> result = TL_reactionEmoji()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in Reaction", constructor))
			}

			result?.readParams(stream, exception)
			return result
		}
	}
}
