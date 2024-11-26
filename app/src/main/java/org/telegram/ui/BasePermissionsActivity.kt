/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.RawRes
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.camera.CameraController
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.group.GroupCallActivity
import kotlin.math.min

open class BasePermissionsActivity : Activity() {
	@JvmField
	protected var currentAccount = -1

	protected fun checkPermissionsResult(requestCode: Int, permissions: Array<String>?, grantResults: IntArray?): Boolean {
		val finalPermissions = permissions ?: arrayOf()
		val finalGrantResults = grantResults ?: IntArray(0)
		val granted = finalGrantResults.isNotEmpty() && finalGrantResults[0] == PackageManager.PERMISSION_GRANTED

		when (requestCode) {
			104 -> {
				if (granted) {
					GroupCallActivity.groupCallInstance?.enableCamera()
				}
				else {
					showPermissionErrorAlert(R.raw.permission_request_camera, ApplicationLoader.applicationContext.getString(R.string.VoipNeedCameraPermission))
				}
			}

			REQUEST_CODE_EXTERNAL_STORAGE, REQUEST_CODE_EXTERNAL_STORAGE_FOR_AVATAR -> {
				if (!granted) {
					val message = if (requestCode == REQUEST_CODE_EXTERNAL_STORAGE_FOR_AVATAR) {
						ApplicationLoader.applicationContext.getString(R.string.PermissionNoStorageAvatar)
					}
					else {
						ApplicationLoader.applicationContext.getString(R.string.PermissionStorageWithHint)
					}

					showPermissionErrorAlert(R.raw.permission_request_folder, message)
				}
				else {
					ImageLoader.getInstance().checkMediaPaths()
				}
			}

			3, REQUEST_CODE_VIDEO_MESSAGE -> {
				var audioGranted = true
				var cameraGranted = true
				var i = 0
				val size = min(finalPermissions.size, finalGrantResults.size)

				while (i < size) {
					if (Manifest.permission.RECORD_AUDIO == finalPermissions[i]) {
						audioGranted = finalGrantResults[i] == PackageManager.PERMISSION_GRANTED
					}
					else if (Manifest.permission.CAMERA == finalPermissions[i]) {
						cameraGranted = finalGrantResults[i] == PackageManager.PERMISSION_GRANTED
					}

					i++
				}

				if (requestCode == REQUEST_CODE_VIDEO_MESSAGE && (!audioGranted || !cameraGranted)) {
					showPermissionErrorAlert(R.raw.permission_request_camera, ApplicationLoader.applicationContext.getString(R.string.PermissionNoCameraMicVideo))
				}
				else if (!audioGranted) {
					showPermissionErrorAlert(R.raw.permission_request_microphone, ApplicationLoader.applicationContext.getString(R.string.PermissionNoAudioWithHint))
				}
				else if (!cameraGranted) {
					showPermissionErrorAlert(R.raw.permission_request_camera, ApplicationLoader.applicationContext.getString(R.string.PermissionNoCameraWithHint))
				}
				else {
					if (SharedConfig.inappCamera) {
						CameraController.getInstance().initCamera(null)
					}
					return false
				}
			}

			18, 19, REQUEST_CODE_OPEN_CAMERA, 22 -> {
				if (!granted) {
					showPermissionErrorAlert(R.raw.permission_request_camera, ApplicationLoader.applicationContext.getString(R.string.PermissionNoCameraWithHint))
				}
			}

			REQUEST_CODE_GEOLOCATION -> {
				NotificationCenter.globalInstance.postNotificationName(if (granted) NotificationCenter.locationPermissionGranted else NotificationCenter.locationPermissionDenied)
			}
		}

		return true
	}

	fun createPermissionErrorAlert(@RawRes animationId: Int, message: String?): AlertDialog {
		val builder = AlertDialog.Builder(this)

		builder.setTopAnimation(animationId, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, ResourcesCompat.getColor(resources, R.color.brand, null))
		builder.setMessage(AndroidUtilities.replaceTags(message))

		builder.setPositiveButton(ApplicationLoader.applicationContext.getString(R.string.PermissionOpenSettings)) { _, _ ->
			try {
//				if (fullAccessPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//					if (!Environment.isExternalStorageManager()) {
//						val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
//						val uri = Uri.fromParts("package", packageName, null)
//						intent.data = uri
//						startActivity(intent)
//
//						return@setPositiveButton
//					}
//				}

				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
				intent.data = Uri.parse("package:" + ApplicationLoader.applicationContext.packageName)
				startActivity(intent)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		builder.setNegativeButton(ApplicationLoader.applicationContext.getString(R.string.ContactsPermissionAlertNotNow), null)

		return builder.create()
	}

	private fun showPermissionErrorAlert(@RawRes animationId: Int, message: String) {
		createPermissionErrorAlert(animationId, message).show()
	}

	companion object {
		const val REQUEST_CODE_GEOLOCATION = 2
		const val REQUEST_CODE_EXTERNAL_STORAGE = 4
		const val REQUEST_CODE_OPEN_CAMERA = 20
		const val REQUEST_CODE_VIDEO_MESSAGE = 150
		const val REQUEST_CODE_EXTERNAL_STORAGE_FOR_AVATAR = 151
		const val REQUEST_CODE_PAYMENT_FORM = 210
	}
}
