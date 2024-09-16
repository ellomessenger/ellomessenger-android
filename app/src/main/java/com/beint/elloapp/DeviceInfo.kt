/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */
package com.beint.elloapp

import android.os.Build
import org.telegram.messenger.utils.capitalizeFirstLetter

object DeviceInfo {
	@JvmStatic
	fun getDeviceName(): String {
		val manufacturer = Build.MANUFACTURER.lowercase().trim().capitalizeFirstLetter()
		val model = Build.MODEL.trim()

		return if (model.startsWith(manufacturer)) {
			model
		}
		else {
			"$manufacturer $model"
		}
	}
}
