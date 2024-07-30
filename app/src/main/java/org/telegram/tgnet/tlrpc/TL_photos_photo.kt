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
import org.telegram.tgnet.TLRPC

class TL_photos_photo : TLObject() {
	@JvmField
	var photo: TLRPC.Photo? = null

	@JvmField
	val users = mutableListOf<User>()

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

		photo = TLRPC.Photo.TLdeserialize(stream, stream.readInt32(exception), exception)

		val magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		val count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = User.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
			users.add(`object`)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)

		photo?.serializeToStream(stream)

		stream.writeInt32(0x1cb5c415)

		val count = users.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			users[a].serializeToStream(stream)
		}
	}

	companion object {
		private const val CONSTRUCTOR: Int = 0x20212ca8

		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_photos_photo? {
			if (CONSTRUCTOR != constructor) {
				if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_photos_photo", constructor))
				}
				else {
					return null
				}
			}

			val result = TL_photos_photo()
			result.readParams(stream, exception)
			return result
		}
	}
}
