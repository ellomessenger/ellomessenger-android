/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Components

class CustomDialog {
	@JvmField
	var name: String? = null

	@JvmField
	var message: String? = null

	@JvmField
	var id = 0

	@JvmField
	var unreadCount = 0

	@JvmField
	var pinned = false

	@JvmField
	var muted = false

	@JvmField
	var type = 0

	@JvmField
	var date = 0

	@JvmField
	var verified = false

	@JvmField
	var isMedia = false

	@JvmField
	var sent = false

	@JvmField
	var adult = true

	@JvmField
	var paid = true

	@JvmField
	var isPublic = true
}
