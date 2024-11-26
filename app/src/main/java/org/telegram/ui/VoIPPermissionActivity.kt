/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import org.telegram.messenger.FileLog
import org.telegram.messenger.voip.VoIPPreNotificationService
import org.telegram.messenger.voip.VoIPService
import org.telegram.ui.Components.voip.VoIPHelper

class VoIPPermissionActivity : Activity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val service = VoIPService.sharedInstance
		val isVideoCall = if (service != null) service.privateCall != null && service.privateCall!!.video else VoIPPreNotificationService.isVideo

		val permissions = ArrayList<String>()

		if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.RECORD_AUDIO)
		}

		if (isVideoCall && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.CAMERA)
		}

		if (permissions.isNotEmpty()) {
			try {
				requestPermissions(permissions.toTypedArray<String>(), if (isVideoCall) 102 else 101)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		if (requestCode == 101 || requestCode == 102) {
			var allGranted = true

			for (grantResult in grantResults) {
				if (grantResult != PackageManager.PERMISSION_GRANTED) {
					allGranted = false
					break
				}
			}
			if (grantResults.isNotEmpty() && allGranted) {
				if (VoIPService.sharedInstance != null) {
					VoIPService.sharedInstance!!.acceptIncomingCall()
				}
				else {
					VoIPPreNotificationService.answer(this)
				}

				finish()

				startActivity(Intent(this, LaunchActivity::class.java).setAction(VoIPPreNotificationService.VOIP_ACTION))
			}
			else {
				if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
					if (VoIPService.sharedInstance != null) {
						VoIPService.sharedInstance!!.declineIncomingCall()
					}
					else {
						VoIPPreNotificationService.decline(this, VoIPService.DISCARD_REASON_HANGUP)
					}

					VoIPHelper.permissionDenied(this, { this.finish() }, requestCode)
				}
				else {
					finish()
				}
			}
		}
	}
}
