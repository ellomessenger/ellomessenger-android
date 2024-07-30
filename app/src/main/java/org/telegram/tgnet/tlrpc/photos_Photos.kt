/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC

abstract class photos_Photos : TLObject() {
	@JvmField
	val photos = ArrayList<TLRPC.Photo>()

	val users = mutableListOf<User>()
	var count = 0

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): photos_Photos? {
			var result: photos_Photos? = null

			when (constructor) {
				-0x7235955b -> result = TL_photos_photos()
				0x15051f54 -> result = TL_photos_photosSlice()
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in photos_Photos", constructor))
			}

			result?.readParams(stream, exception)

			return result
		}
	}
}
