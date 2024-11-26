/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger.voip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Created by grishka on 28.07.17.
 */
class VoIPActionsReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (VoIPService.sharedInstance != null) {
			VoIPService.sharedInstance?.handleNotificationAction(intent)
		}
		else {
			val packageName = context.packageName

			when (intent.action) {
				"$packageName.END_CALL" -> {
					VoIPPreNotificationService.decline(context, VoIPService.DISCARD_REASON_HANGUP)
				}

				"$packageName.DECLINE_CALL" -> {
					VoIPPreNotificationService.decline(context, VoIPService.DISCARD_REASON_LINE_BUSY)
				}

				"$packageName.ANSWER_CALL" -> {
					VoIPPreNotificationService.answer(context)
				}

				"$packageName.HIDE_CALL" -> {
					VoIPPreNotificationService.dismiss(context)
				}
			}
		}
	}
}
