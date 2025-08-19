/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.messenger

import org.telegram.tgnet.TLObject

class DownloadObject {
	@JvmField
	var `object`: TLObject? = null

	@JvmField
	var type = 0

	@JvmField
	var id = 0L

	@JvmField
	var secret = false

	@JvmField
	var forceCache = false

	@JvmField
	var parent: String? = null
}
