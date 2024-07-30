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

class TL_reactionEmpty : Reaction() {
	override fun serializeToStream(stream: AbstractSerializedData?) {
		stream?.writeInt32(CONSTRUCTOR)
	}

	companion object {
		private const val CONSTRUCTOR = 0x79f5d419
	}
}

