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

class OverviewChatData(stats: TLRPC.TLStatsMegagroupStats) {
	var membersTitle: String
	var membersPrimary: String
	var membersSecondary: String? = null
	private var membersUp: Boolean
	var messagesTitle: String
	var messagesPrimary: String
	var messagesSecondary: String? = null
	private var messagesUp: Boolean
	var viewingMembersTitle: String
	var viewingMembersPrimary: String
	var viewingMembersSecondary: String? = null
	private var viewingMembersUp: Boolean
	var postingMembersTitle: String
	var postingMembersPrimary: String
	var postingMembersSecondary: String? = null
	private var postingMembersUp: Boolean

	init {
		val context = ApplicationLoader.applicationContext
		val members = stats.members ?: TLRPC.TLStatsAbsValueAndPrev()
		var dif = (members.current - members.previous).toInt()
		var difPercent: Float = if (members.previous == 0.0) 0f else abs(dif / members.previous.toFloat() * 100f)

		membersTitle = context.getString(R.string.MembersOverviewTitle)
		membersPrimary = AndroidUtilities.formatWholeNumber(members.current.toInt(), 0)

		membersSecondary = if (dif == 0 || difPercent == 0f) {
			""
		}
		else if (difPercent == difPercent.toInt().toFloat()) {
			String.format(Locale.ENGLISH, "%s (%d%s)", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent.toInt(), "%")
		}
		else {
			String.format(Locale.ENGLISH, "%s (%.1f%s)", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0), difPercent, "%")
		}

		membersUp = dif >= 0

		val viewers = stats.viewers ?: TLRPC.TLStatsAbsValueAndPrev()

		dif = (viewers.current - viewers.previous).toInt()
		difPercent = if (viewers.previous == 0.0) 0f else abs(dif / viewers.previous.toFloat() * 100f)

		viewingMembersTitle = context.getString(R.string.ViewingMembers)
		viewingMembersPrimary = AndroidUtilities.formatWholeNumber(viewers.current.toInt(), 0)

		viewingMembersSecondary = if (dif == 0 || difPercent == 0f) {
			""
		}
		else {
			String.format(Locale.ENGLISH, "%s", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0))
		}

		viewingMembersUp = dif >= 0

		val posters = stats.posters ?: TLRPC.TLStatsAbsValueAndPrev()

		dif = (posters.current - posters.previous).toInt()
		difPercent = if (posters.previous == 0.0) 0f else abs(dif / posters.previous.toFloat() * 100f)

		postingMembersTitle = context.getString(R.string.PostingMembers)
		postingMembersPrimary = AndroidUtilities.formatWholeNumber(posters.current.toInt(), 0)

		postingMembersSecondary = if (dif == 0 || difPercent == 0f) {
			""
		}
		else {
			String.format(Locale.ENGLISH, "%s", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0))
		}

		postingMembersUp = dif >= 0

		val messages = stats.messages ?: TLRPC.TLStatsAbsValueAndPrev()

		dif = (messages.current - messages.previous).toInt()
		difPercent = if (messages.previous == 0.0) 0f else abs(dif / messages.previous.toFloat() * 100f)

		messagesTitle = context.getString(R.string.MessagesOverview)
		messagesPrimary = AndroidUtilities.formatWholeNumber(messages.current.toInt(), 0)

		messagesSecondary = if (dif == 0 || difPercent == 0f) {
			""
		}
		else {
			String.format(Locale.ENGLISH, "%s", (if (dif > 0) "+" else "") + AndroidUtilities.formatWholeNumber(dif, 0))
		}

		messagesUp = dif >= 0
	}
}
