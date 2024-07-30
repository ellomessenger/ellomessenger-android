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
import org.telegram.tgnet.TLRPC.InputPhoto

class TL_photos_deletePhotos : TLObject() {
	val id = mutableListOf<InputPhoto>()

	override fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		if (stream == null) {
			if (exception) {
				throw RuntimeException("Input stream is null")
			}
			else {
				FileLog.e("Input stream is null")
			}

			return null
		}

		val vector = Vector()
		val size = stream.readInt32(exception)

		for (a in 0 until size) {
			vector.objects.add(stream.readInt64(exception))
		}

		return vector
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)
		stream.writeInt32(0x1cb5c415)
		stream.writeInt32(id.size)

		for (photo in id) {
			photo.serializeToStream(stream)
		}
	}

	companion object {
		private const val CONSTRUCTOR = -0x783080d1
	}
}
