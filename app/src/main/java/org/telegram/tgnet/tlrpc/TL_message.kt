/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Oleksandr Nahornyi, Ello 2023-2024
 */
package org.telegram.tgnet.tlrpc

import org.telegram.messenger.FileLog
import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.MessageEntity
import org.telegram.tgnet.TLRPC.TL_restrictionReason

open class TL_message : Message() {
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

		out = flags and 2 != 0
		mentioned = flags and 16 != 0
		media_unread = flags and 32 != 0
		silent = flags and 8192 != 0
		post = flags and 16384 != 0
		from_scheduled = flags and 262144 != 0
		legacy = flags and 524288 != 0
		edit_hide = flags and 2097152 != 0
		pinned = flags and 16777216 != 0
		noforwards = flags and 67108864 != 0
		is_media_sale = flags and 4096 != 0
		is_media_sale_info = flags and 134217728 != 0
		is_liked = flags and 1073741824 != 0
		id = stream.readInt32(exception)

		if (flags and 256 != 0) {
			from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception)
		}

		peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception)

		if (flags and 4 != 0) {
			fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception)
		}

		if (flags and 2048 != 0) {
			via_bot_id = stream.readInt64(exception)
		}

		if (flags and 8 != 0) {
			reply_to = TLRPC.TL_messageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception)
		}

		date = stream.readInt32(exception)
		message = stream.readString(exception)

		if (flags and 512 != 0) {
			media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception)

			media?.let {
				ttl = it.ttl_seconds
			}

			media?.captionLegacy?.takeIf { it.isNotEmpty() }?.let {
				message = it
			}
		}

		if (flags and 64 != 0) {
			reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception)
		}

		if (flags and 128 != 0) {
			val magic = stream.readInt32(exception)

			if (magic != 0x1cb5c415) {
				if (exception) {
					throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
				}

				return
			}

			val count = stream.readInt32(exception)

			for (a in 0 until count) {
				val `object` = MessageEntity.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return

				entities.add(`object`)
			}
		}

		if (flags and 1024 != 0) {
			views = stream.readInt32(exception)
		}

		if (flags and 268435456 != 0) {
			forwards = stream.readInt32(exception)
		}

		if (flags and 8388608 != 0) {
			replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception)
		}

		if (flags and 32768 != 0) {
			edit_date = stream.readInt32(exception)
		}

		if (flags and 65536 != 0) {
			post_author = stream.readString(exception)
		}

		if (flags and 131072 != 0) {
			realGroupId = stream.readInt64(exception)
		}

		if (flags and 1048576 != 0) {
			originalReactions = MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception)
		}

		if (flags and 4194304 != 0) {
			val magic = stream.readInt32(exception)

			if (magic != 0x1cb5c415) {
				if (exception) {
					throw RuntimeException(String.format("wrong Vector magic, got %x", magic))
				}

				return
			}

			val count = stream.readInt32(exception)

			for (a in 0 until count) {
				val `object` = TL_restrictionReason.TLdeserialize(stream, stream.readInt32(exception), exception) ?: return
				restriction_reason.add(`object`)
			}
		}

		if (flags and 33554432 != 0) {
			ttl_period = stream.readInt32(exception)
		}

		if (flags and 4096 != 0) {
			is_media_sale = true
			mediaHash = stream.readString(exception)
			is_paid = stream.readBool(exception)

			if (flags and 134217728 != 0) {
				title = stream.readString(exception)
				price = stream.readDouble(exception)
				quantity = stream.readInt32(exception)
			}
		}

		if (flags and 536870912 != 0) {
			likes = stream.readInt32(exception)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)

		flags = if (out) flags or 2 else flags and 2.inv()
		flags = if (mentioned) flags or 16 else flags and 16.inv()
		flags = if (media_unread) flags or 32 else flags and 32.inv()
		flags = if (silent) flags or 8192 else flags and 8192.inv()
		flags = if (post) flags or 16384 else flags and 16384.inv()
		flags = if (from_scheduled) flags or 262144 else flags and 262144.inv()
		flags = if (legacy) flags or 524288 else flags and 524288.inv()
		flags = if (edit_hide) flags or 2097152 else flags and 2097152.inv()
		flags = if (pinned) flags or 16777216 else flags and 16777216.inv()
		flags = if (noforwards) flags or 67108864 else flags and 67108864.inv()
		flags = if (is_media_sale) flags or 4096 else flags and 4096.inv()
		flags = if (is_media_sale_info) flags or 134217728 else flags and 134217728.inv()

		stream.writeInt32(flags)
		stream.writeInt32(id)

		if (flags and 256 != 0) {
			from_id?.serializeToStream(stream)
		}

		peer_id?.serializeToStream(stream)

		if (flags and 4 != 0) {
			fwd_from?.serializeToStream(stream)
		}

		if (flags and 2048 != 0) {
			stream.writeInt64(via_bot_id)
		}

		if (flags and 8 != 0) {
			reply_to?.serializeToStream(stream)
		}

		stream.writeInt32(date)
		stream.writeString(message)

		if (flags and 512 != 0) {
			media?.serializeToStream(stream)
		}

		if (flags and 64 != 0) {
			reply_markup?.serializeToStream(stream)
		}

		if (flags and 128 != 0) {
			stream.writeInt32(0x1cb5c415)

			val count = entities.size

			stream.writeInt32(count)

			for (a in 0 until count) {
				entities[a].serializeToStream(stream)
			}
		}

		if (flags and 1024 != 0) {
			stream.writeInt32(views)
		}

		if (flags and 268435456 != 0) {
			stream.writeInt32(forwards)
		}

		if (flags and 8388608 != 0) {
			replies?.serializeToStream(stream)
		}

		if (flags and 32768 != 0) {
			stream.writeInt32(edit_date)
		}

		if (flags and 65536 != 0) {
			stream.writeString(post_author)
		}

		if (flags and 131072 != 0) {
			stream.writeInt64(realGroupId)
		}

		if (flags and 1048576 != 0) {
			originalReactions?.serializeToStream(stream)
		}

		if (flags and 4194304 != 0) {
			stream.writeInt32(0x1cb5c415)

			val count = restriction_reason.size

			stream.writeInt32(count)

			for (a in 0 until count) {
				restriction_reason[a].serializeToStream(stream)
			}
		}

		if (flags and 33554432 != 0) {
			stream.writeInt32(ttl_period)
		}

		if (flags and 4096 != 0) {
			stream.writeString(mediaHash)
			stream.writeBool(is_paid)

			if (flags and 134217728 != 0) {
				stream.writeString(title)
				stream.writeDouble(price)
				stream.writeInt32(quantity)
			}
		}

		if (flags and 536870912 != 0) {
			stream.writeInt32(likes)
		}

		writeAttachPath(stream)
	}

	companion object {
		private const val constructor = 0x38116ee0
	}
}
