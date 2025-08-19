/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.tgnet

import org.telegram.tgnet.TLRPC.TLError

fun interface RequestDelegateTimestamp {
	fun run(response: TLObject?, error: TLError?, responseTime: Long)
}
