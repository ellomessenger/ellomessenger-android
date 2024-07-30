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

class TL_chatBannedRights : TLObject() {
	var flags = 0

	@JvmField
	var view_messages = false

	@JvmField
	var send_messages = false

	@JvmField
	var send_media = false

	@JvmField
	var send_stickers = false

	@JvmField
	var send_gifs = false

	@JvmField
	var send_games = false

	@JvmField
	var send_inline = false

	@JvmField
	var embed_links = false

	@JvmField
	var send_polls = false

	@JvmField
	var change_info = false

	@JvmField
	var invite_users = false

	@JvmField
	var pin_messages = false

	@JvmField
	var until_date = 0

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
		view_messages = flags and 1 != 0
		send_messages = flags and 2 != 0
		send_media = flags and 4 != 0
		send_stickers = flags and 8 != 0
		send_gifs = flags and 16 != 0
		send_games = flags and 32 != 0
		send_inline = flags and 64 != 0
		embed_links = flags and 128 != 0
		send_polls = flags and 256 != 0
		change_info = flags and 1024 != 0
		invite_users = flags and 32768 != 0
		pin_messages = flags and 131072 != 0
		until_date = stream.readInt32(exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)

		flags = if (view_messages) flags or 1 else flags and 1.inv()
		flags = if (send_messages) flags or 2 else flags and 2.inv()
		flags = if (send_media) flags or 4 else flags and 4.inv()
		flags = if (send_stickers) flags or 8 else flags and 8.inv()
		flags = if (send_gifs) flags or 16 else flags and 16.inv()
		flags = if (send_games) flags or 32 else flags and 32.inv()
		flags = if (send_inline) flags or 64 else flags and 64.inv()
		flags = if (embed_links) flags or 128 else flags and 128.inv()
		flags = if (send_polls) flags or 256 else flags and 256.inv()
		flags = if (change_info) flags or 1024 else flags and 1024.inv()
		flags = if (invite_users) flags or 32768 else flags and 32768.inv()
		flags = if (pin_messages) flags or 131072 else flags and 131072.inv()

		stream.writeInt32(flags)
		stream.writeInt32(until_date)
	}

	companion object {
		const val constructor = -0x60edfbe8

		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_chatBannedRights? {
			if (Companion.constructor != constructor) {
				return if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_chatBannedRights", constructor))
				}
				else {
					null
				}
			}

			val result = TL_chatBannedRights()
			result.readParams(stream, exception)
			return result
		}
	}
}
