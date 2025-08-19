/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.statistics

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import java.util.Locale
import kotlin.math.abs

class OverviewChannelData(stats: TLRPC.TLStatsBroadcastStats) {
	private var followersUp: Boolean
	private var sharesUp: Boolean
	private var viewsUp: Boolean
	var followersPrimary: String
	var followersSecondary: String? = null
	var followersTitle: String
	var notificationsPrimary: String? = null
	var notificationsTitle: String
	var sharesPrimary: String
	var sharesSecondary: String? = null
	var sharesTitle: String
	var viewsPrimary: String
	var viewsSecondary: String? = null
	var viewsTitle: String

	init {
		val context = ApplicationLoader.applicationContext
		val followers = stats.followers ?: TLRPC.TLStatsAbsValueAndPrev()

		var dif = (followers.current - followers.previous).toInt()

		var difPercent: Float = if (followers.previous == 0.0) {
			0f
		}
		else {
			abs(dif / followers.previous.toFloat() * 100f)
		}

		followersTitle = context.getString(R.string.FollowersChartTitle)
		followersPrimary = AndroidUtilities.formatWholeNumber(followers.current.toInt(), 0)

		followersSecondary = if (dif == 0 || difPercent == 0f) {
			""
		}
		else if (difPercent == difPercent.toInt().toFloat()) {
			String.format(Locale.ENGLISH, "%s (%d%s)", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent.toInt(), "%")
		}
		else {
			String.format(Locale.ENGLISH, "%s (%.1f%s)", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%")
		}

		followersUp = dif >= 0

		val sharesPerPost = stats.sharesPerPost ?: TLRPC.TLStatsAbsValueAndPrev()

		dif = (sharesPerPost.current - sharesPerPost.previous).toInt()

		difPercent = if (sharesPerPost.previous == 0.0) {
			0f
		}
		else {
			abs(dif / sharesPerPost.previous.toFloat() * 100f)
		}

		sharesTitle = context.getString(R.string.SharesPerPost)
		sharesPrimary = AndroidUtilities.formatWholeNumber(sharesPerPost.current.toInt(), 0)

		sharesSecondary = if (dif == 0 || difPercent == 0f) {
			""
		}
		else if (difPercent == difPercent.toInt().toFloat()) {
			String.format(Locale.ENGLISH, "%s (%d%s)", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent.toInt(), "%")
		}
		else {
			String.format(Locale.ENGLISH, "%s (%.1f%s)", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%")
		}

		sharesUp = dif >= 0

		val viewsPerPost = stats.viewsPerPost ?: TLRPC.TLStatsAbsValueAndPrev()

		dif = (viewsPerPost.current - viewsPerPost.previous).toInt()

		difPercent = if (viewsPerPost.previous == 0.0) {
			0f
		}
		else {
			abs(dif / viewsPerPost.previous.toFloat() * 100f)
		}

		viewsTitle = context.getString(R.string.ViewsPerPost)
		viewsPrimary = AndroidUtilities.formatWholeNumber(viewsPerPost.current.toInt(), 0)

		viewsSecondary = if (dif == 0 || difPercent == 0f) {
			""
		}
		else if (difPercent == difPercent.toInt().toFloat()) {
			String.format(Locale.ENGLISH, "%s (%d%s)", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent.toInt(), "%")
		}
		else {
			String.format(Locale.ENGLISH, "%s (%.1f%s)", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%")
		}

		viewsUp = dif >= 0

		val enabledNotifications = stats.enabledNotifications ?: TLRPC.TLStatsPercentValue()

		difPercent = if (enabledNotifications.total == 0.0) {
			0f
		}
		else {
			(enabledNotifications.part / enabledNotifications.total * 100f).toFloat()
		}

		notificationsTitle = context.getString(R.string.EnabledNotifications)

		notificationsPrimary = if (difPercent == difPercent.toInt().toFloat()) {
			String.format(Locale.ENGLISH, "%d%s", difPercent.toInt(), "%")
		}
		else {
			String.format(Locale.ENGLISH, "%.2f%s", difPercent, "%")
		}
	}
}
