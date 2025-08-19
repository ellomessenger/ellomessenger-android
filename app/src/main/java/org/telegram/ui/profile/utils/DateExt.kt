/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.utils

import java.text.SimpleDateFormat
import java.util.*

fun Long.toFormattedDate(): String {
	val dt = Date(this)
	val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
	return dateFormat.format(dt)
}
