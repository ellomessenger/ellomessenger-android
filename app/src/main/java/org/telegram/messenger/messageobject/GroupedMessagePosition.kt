/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger.messageobject

class GroupedMessagePosition {
	@JvmField
	var minX: Byte = 0

	@JvmField
	var maxX: Byte = 0

	@JvmField
	var minY: Byte = 0

	@JvmField
	var maxY: Byte = 0

	@JvmField
	var pw = 0

	@JvmField
	var ph = 0f

	@JvmField
	var aspectRatio = 0f

	@JvmField
	var last = false

	@JvmField
	var spanSize = 0

	@JvmField
	var leftSpanOffset = 0

	@JvmField
	var edge = false

	@JvmField
	var flags = 0

	@JvmField
	var siblingHeights: FloatArray? = null

	var top = 0f // sum of ph of media above
	var left = 0f // sum of pw of media on the left side

	fun set(minX: Int, maxX: Int, minY: Int, maxY: Int, w: Int, h: Float, flags: Int) {
		this.minX = minX.toByte()
		this.maxX = maxX.toByte()
		this.minY = minY.toByte()
		this.maxY = maxY.toByte()
		pw = w
		spanSize = w
		ph = h
		this.flags = flags.toByte().toInt()
	}
}
