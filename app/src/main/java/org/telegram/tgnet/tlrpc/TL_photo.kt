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
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.VideoSize

open class TL_photo : TLRPC.Photo() {
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
		has_stickers = (flags and 1) != 0
		id = stream.readInt64(exception)
		access_hash = stream.readInt64(exception)
		file_reference = stream.readByteArray(exception)
		date = stream.readInt32(exception)
		var magic = stream.readInt32(exception)

		if (magic != 0x1cb5c415) {
			if (exception) {
				throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
			}

			return
		}

		var count = stream.readInt32(exception)

		for (a in 0 until count) {
			val `object` = PhotoSize.TLdeserialize(id, 0, 0, stream, stream.readInt32(exception), exception) ?: return
			sizes.add(`object`)
		}

		if ((flags and 2) != 0) {
			magic = stream.readInt32(exception)

			if (magic != 0x1cb5c415) {
				if (exception) {
					throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
				}

				return
			}

			count = stream.readInt32(exception)

			for (a in 0 until count) {
				val `object` = VideoSize.TLdeserialize(id, 0, stream, stream.readInt32(exception), exception) ?: return
				video_sizes.add(`object`)
			}
		}

		dc_id = stream.readInt32(exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(CONSTRUCTOR)

		flags = if (has_stickers) (flags or 1) else (flags and 1.inv())

		stream.writeInt32(flags)
		stream.writeInt64(id)
		stream.writeInt64(access_hash)
		stream.writeByteArray(file_reference)
		stream.writeInt32(date)
		stream.writeInt32(0x1cb5c415)

		var count = sizes.size

		stream.writeInt32(count)

		for (a in 0 until count) {
			sizes[a].serializeToStream(stream)
		}

		if ((flags and 2) != 0) {
			stream.writeInt32(0x1cb5c415)

			count = video_sizes.size

			stream.writeInt32(count)

			for (a in 0 until count) {
				video_sizes[a].serializeToStream(stream)
			}
		}

		stream.writeInt32(dc_id)
	}

	companion object {
		private const val CONSTRUCTOR: Int = -0x4e6859b
	}
}
