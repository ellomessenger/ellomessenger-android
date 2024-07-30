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
import org.telegram.tgnet.TLRPC

class TL_contacts_getContacts : TLObject() {
	var hash: Long = 0

	override fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		return TLRPC.contacts_Contacts.TLdeserialize(stream, constructor, exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)
		stream.writeInt64(hash)
	}

	companion object {
		private const val constructor = 0x5dd69e12
	}
}
