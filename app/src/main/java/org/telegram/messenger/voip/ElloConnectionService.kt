/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright grishka, 2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger.voip

import android.annotation.TargetApi
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import org.telegram.messenger.FileLog

@TargetApi(Build.VERSION_CODES.O)
class ElloConnectionService : ConnectionService() {
	override fun onCreateIncomingConnection(connectionManagerPhoneAccount: PhoneAccountHandle, request: ConnectionRequest): Connection? {
		FileLog.d("onCreateIncomingConnection")

		val extras = request.extras

		if (extras.getInt("call_type") == 1) { // private
			val svc = VoIPService.sharedInstance ?: return null

			return if (svc.isOutgoing()) {
				null
			}
			else {
				svc.getConnectionAndStartCall()
			}
		}
		else if (extras.getInt("call_type") == 2) { // group
			val svc = VoIPService.sharedInstance ?: return null
			return svc.getConnectionAndStartCall()
		}

		return null
	}

	override fun onCreateIncomingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle, request: ConnectionRequest) {
		FileLog.e("onCreateIncomingConnectionFailed")
		VoIPService.sharedInstance?.callFailedFromConnectionService()
	}

	override fun onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle, request: ConnectionRequest) {
		FileLog.e("onCreateOutgoingConnectionFailed")
		VoIPService.sharedInstance?.callFailedFromConnectionService()
	}

	override fun onCreateOutgoingConnection(connectionManagerPhoneAccount: PhoneAccountHandle, request: ConnectionRequest): Connection? {
		FileLog.d("onCreateOutgoingConnection " /*+request*/)

		val extras = request.extras

		if (extras.getInt("call_type") == 1) { // private
			return VoIPService.sharedInstance?.getConnectionAndStartCall()
		}
		else if (extras.getInt("call_type") == 2) { // group
			val svc = VoIPService.sharedInstance ?: return null

			return if (!svc.isOutgoing()) {
				null
			}
			else {
				svc.getConnectionAndStartCall()
			}
		}

		return null
	}
}
