/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.tgnet.AbstractSerializedData

class TL_contacts_search : TLObject() {
	var q: String? = null
	var limit = 0
	var flags = 0
	var isRecommended = false
	var isNew = false
	var isPaid = false
	var isCourse = false
	var isPublic = false
	var country: String? = null
	var category: String? = null
	var genre: String? = null
	var page = 0

	override fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		return TL_contacts_found.TLdeserialize(stream, constructor, exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)
		stream.writeString(q ?: "")
		stream.writeInt32(limit)

		flags = if (isRecommended) flags or 4 else flags and 4.inv()
		flags = if (isNew) flags or 8 else flags and 8.inv()
		flags = if (isPaid) flags or 16 else flags and 16.inv()
		flags = if (isCourse) flags or 32 else flags and 32.inv()
		flags = if (isPublic) flags or 64 else flags and 64.inv()
		flags = if (country != null) flags or 128 else flags and 128.inv()
		flags = if (category != null) flags or 256 else flags and 256.inv()
		flags = if (genre != null) flags or 512 else flags and 512.inv()
		flags = if (page != 0) flags or 1024 else flags and 1024.inv()

		stream.writeInt32(flags)

		if (flags and 128 != 0) {
			stream.writeString(country)
		}

		if (flags and 256 != 0) {
			stream.writeString(category)
		}

		if (flags and 512 != 0) {
			stream.writeString(genre)
		}

		if (flags and 1024 != 0) {
			stream.writeInt32(page)
		}
	}

	companion object {
		private const val constructor = 0x11f812d8
	}
}
