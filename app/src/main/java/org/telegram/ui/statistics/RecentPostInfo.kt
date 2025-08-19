/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.statistics

import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC

class RecentPostInfo {
	var counters: TLRPC.TLMessageInteractionCounters? = null
	var message: MessageObject? = null
}
