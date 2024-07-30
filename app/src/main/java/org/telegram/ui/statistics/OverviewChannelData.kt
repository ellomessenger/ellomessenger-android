/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.statistics

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import java.util.Locale
import kotlin.math.abs

class OverviewChannelData(stats: TLRPC.TL_stats_broadcastStats) {
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

		var dif = (stats.followers.current - stats.followers.previous).toInt()

		var difPercent: Float = if (stats.followers.previous == 0.0) {
			0f
		}
		else {
			abs(dif / stats.followers.previous.toFloat() * 100f)
		}

		followersTitle = context.getString(R.string.FollowersChartTitle)
		followersPrimary = AndroidUtilities.formatWholeNumber(stats.followers.current.toInt(), 0)

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

		dif = (stats.shares_per_post.current - stats.shares_per_post.previous).toInt()

		difPercent = if (stats.shares_per_post.previous == 0.0) {
			0f
		}
		else {
			abs(dif / stats.shares_per_post.previous.toFloat() * 100f)
		}

		sharesTitle = context.getString(R.string.SharesPerPost)
		sharesPrimary = AndroidUtilities.formatWholeNumber(stats.shares_per_post.current.toInt(), 0)

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

		dif = (stats.views_per_post.current - stats.views_per_post.previous).toInt()

		difPercent = if (stats.views_per_post.previous == 0.0) {
			0f
		}
		else {
			abs(dif / stats.views_per_post.previous.toFloat() * 100f)
		}

		viewsTitle = context.getString(R.string.ViewsPerPost)
		viewsPrimary = AndroidUtilities.formatWholeNumber(stats.views_per_post.current.toInt(), 0)

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

		difPercent = if (stats.enabled_notifications.total == 0.0) {
			0f
		}
		else {
			(stats.enabled_notifications.part / stats.enabled_notifications.total * 100f).toFloat()
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
