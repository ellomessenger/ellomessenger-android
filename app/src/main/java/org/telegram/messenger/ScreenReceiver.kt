/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.telegram.tgnet.ConnectionsManager

class ScreenReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		when (intent.action) {
			Intent.ACTION_SCREEN_OFF -> {
				FileLog.d("screen off")
				ConnectionsManager.getInstance(UserConfig.selectedAccount).setAppPaused(value = true, byScreenState = true)
				ApplicationLoader.isScreenOn = false
			}

			Intent.ACTION_SCREEN_ON -> {
				FileLog.d("screen on")
				ConnectionsManager.getInstance(UserConfig.selectedAccount).setAppPaused(value = false, byScreenState = true)
				ApplicationLoader.isScreenOn = true
			}
		}

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.screenStateChanged)
	}
}
