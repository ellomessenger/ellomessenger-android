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

class TL_userFull_layer139 : UserFull() {
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
		blocked = flags and 1 != 0
		phone_calls_available = flags and 16 != 0
		phone_calls_private = flags and 32 != 0
		can_pin_message = flags and 128 != 0
		has_scheduled = flags and 4096 != 0
		video_calls_available = flags and 8192 != 0
		id = stream.readInt64(exception)

		if (flags and 2 != 0) {
			about = stream.readString(exception)
		}

		settings = TLRPC.TL_peerSettings.TLdeserialize(stream, stream.readInt32(exception), exception)

		if (flags and 4 != 0) {
			profile_photo = TLRPC.Photo.TLdeserialize(stream, stream.readInt32(exception), exception)
		}

		notify_settings = TLRPC.PeerNotifySettings.TLdeserialize(stream, stream.readInt32(exception), exception)

		if (flags and 8 != 0) {
			bot_info = TLRPC.BotInfo.TLdeserialize(stream, stream.readInt32(exception), exception)
		}

		if (flags and 64 != 0) {
			pinned_msg_id = stream.readInt32(exception)
		}

		common_chats_count = stream.readInt32(exception)

		if (flags and 2048 != 0) {
			folder_id = stream.readInt32(exception)
		}

		if (flags and 16384 != 0) {
			ttl_period = stream.readInt32(exception)
		}

		if (flags and 32768 != 0) {
			theme_emoticon = stream.readString(exception)
		}

		if (flags and 65536 != 0) {
			private_forward_name = stream.readString(exception)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)

		flags = if (blocked) flags or 1 else flags and 1.inv()
		flags = if (phone_calls_available) flags or 16 else flags and 16.inv()
		flags = if (phone_calls_private) flags or 32 else flags and 32.inv()
		flags = if (can_pin_message) flags or 128 else flags and 128.inv()
		flags = if (has_scheduled) flags or 4096 else flags and 4096.inv()
		flags = if (video_calls_available) flags or 8192 else flags and 8192.inv()

		stream.writeInt32(flags)
		stream.writeInt64(id)

		if (flags and 2 != 0) {
			stream.writeString(about)
		}

		settings?.serializeToStream(stream)

		if (flags and 4 != 0) {
			profile_photo?.serializeToStream(stream)
		}

		notify_settings?.serializeToStream(stream)

		if (flags and 8 != 0) {
			bot_info?.serializeToStream(stream)
		}

		if (flags and 64 != 0) {
			stream.writeInt32(pinned_msg_id)
		}

		stream.writeInt32(common_chats_count)

		if (flags and 2048 != 0) {
			stream.writeInt32(folder_id)
		}

		if (flags and 16384 != 0) {
			stream.writeInt32(ttl_period)
		}

		if (flags and 32768 != 0) {
			stream.writeString(theme_emoticon)
		}

		if (flags and 65536 != 0) {
			stream.writeString(private_forward_name)
		}
	}

	companion object {
		const val constructor = -0x30c99adf
	}
}
