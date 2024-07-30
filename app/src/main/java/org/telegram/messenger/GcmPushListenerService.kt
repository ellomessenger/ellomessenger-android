/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GcmPushListenerService : FirebaseMessagingService() {
	override fun onMessageReceived(message: RemoteMessage) {
		val from = message.from
		val data = message.data
		val time = message.sentTime

		if (BuildConfig.DEBUG) {
			FileLog.d("FCM received data at $time: $data from: $from")
		}

		PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_FIREBASE, data["p"], time)
	}

	override fun onNewToken(token: String) {
		AndroidUtilities.runOnUIThread {
			if (BuildConfig.DEBUG) {
				FileLog.d("Refreshed FCM token: $token")
			}

			ApplicationLoader.postInitApplication()
			PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_FIREBASE, token)
		}
	}
}
