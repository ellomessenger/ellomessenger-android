/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.messenger.FileLog
import org.telegram.tgnet.AbstractSerializedData

class TL_reactionCount : ReactionCount() {
	override fun readParams(stream: AbstractSerializedData?, exception: Boolean) {
		if (stream == null) {
			if (exception) {
				throw RuntimeException("Input stream is null")
			}
			else {
				FileLog.e("Input stream is null")
			}

			return
		}

		flags = stream.readInt32(exception)

		if (((flags and 1) != 0).also { chosen = it }) {
			chosenOrder = stream.readInt32(exception)
		}

		reaction = Reaction.TLdeserialize(stream, stream.readInt32(exception), exception)
		count = stream.readInt32(exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)
		stream.writeInt32(flags)

		if ((flags and 1) != 0) {
			stream.writeInt32(chosenOrder)
		}

		reaction?.serializeToStream(stream)

		stream.writeInt32(count)
	}

	companion object {
		const val CONSTRUCTOR: Int = -0x5c2e3480
	}
}
