/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger.messageobject

import android.text.Layout
import android.text.StaticLayout
import org.telegram.ui.Components.spoilers.SpoilerEffect
import java.util.concurrent.atomic.AtomicReference

class TextLayoutBlock {
	val spoilersPatchedTextLayout = AtomicReference<Layout>()

	@JvmField
	var textLayout: StaticLayout? = null

	@JvmField
	var textYOffset = 0f

	@JvmField
	var charactersOffset = 0

	@JvmField
	var charactersEnd = 0

	@JvmField
	var height = 0

	@JvmField
	var directionFlags: Byte = 0

	@JvmField
	var spoilers = ArrayList<SpoilerEffect>()

	val isRtl: Boolean
		get() = directionFlags.toInt() and FLAG_RTL != 0 && directionFlags.toInt() and FLAG_NOT_RTL == 0

	companion object {
		const val FLAG_RTL = 1
		const val FLAG_NOT_RTL = 2
	}
}
