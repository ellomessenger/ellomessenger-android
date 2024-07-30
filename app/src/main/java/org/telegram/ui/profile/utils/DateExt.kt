package org.telegram.ui.profile.utils

import java.text.SimpleDateFormat
import java.util.*

fun Long.toFormattedDate(): String {
	val dt = Date(this)
	val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
	return dateFormat.format(dt)
}
