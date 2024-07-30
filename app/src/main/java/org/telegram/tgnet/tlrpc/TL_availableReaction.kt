/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.messenger.FileLog
import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC

open class TL_availableReaction : TLObject() {
	var flags = 0

	@JvmField
	var inactive = false

	@JvmField
	var premium = false

	@JvmField
	var reaction: String? = null

	@JvmField
	var title: String? = null

	@JvmField
	var static_icon: TLRPC.Document? = null

	@JvmField
	var appear_animation: TLRPC.Document? = null

	@JvmField
	var select_animation: TLRPC.Document? = null

	@JvmField
	var activate_animation: TLRPC.Document? = null

	@JvmField
	var effect_animation: TLRPC.Document? = null

	@JvmField
	var around_animation: TLRPC.Document? = null

	@JvmField
	var center_icon: TLRPC.Document? = null

	@JvmField
	var positionInList = 0 //custom

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
		inactive = flags and 1 != 0
		premium = flags and 4 != 0
		reaction = stream.readString(exception)
		title = stream.readString(exception)
		static_icon = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception)
		appear_animation = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception)
		select_animation = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception)
		activate_animation = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception)
		effect_animation = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception)

		if (flags and 2 != 0) {
			around_animation = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception)
		}
		if (flags and 2 != 0) {
			center_icon = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception)
		}
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)

		flags = if (inactive) flags or 1 else flags and 1.inv()
		flags = if (premium) flags or 4 else flags and 4.inv()

		stream.writeInt32(flags)
		stream.writeString(reaction)
		stream.writeString(title)

		static_icon?.serializeToStream(stream)
		appear_animation?.serializeToStream(stream)
		select_animation?.serializeToStream(stream)
		activate_animation?.serializeToStream(stream)
		effect_animation?.serializeToStream(stream)

		if (flags and 2 != 0) {
			around_animation?.serializeToStream(stream)
		}

		if (flags and 2 != 0) {
			center_icon?.serializeToStream(stream)
		}
	}

	companion object {
		const val constructor = -0x3f8813ff

		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TL_availableReaction? {
			if (Companion.constructor != constructor) {
				return if (exception) {
					throw RuntimeException(String.format("can't parse magic %x in TL_availableReaction", constructor))
				}
				else {
					null
				}
			}

			val result = TL_availableReaction()
			result.readParams(stream, exception)
			return result
		}
	}
}
