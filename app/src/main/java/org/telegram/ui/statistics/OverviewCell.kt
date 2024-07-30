/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.statistics

import android.content.Context
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper

class OverviewCell(context: Context) : LinearLayout(context) {
	val primary = Array(4) { TextView(context) }
	val secondary = Array(4) { TextView(context) }
	val title = Array(4) { TextView(context) }

	init {
		orientation = VERTICAL

		setPadding(AndroidUtilities.dp(16f), 0, AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))

		for (i in 0..1) {
			val linearLayout = LinearLayout(context)
			linearLayout.orientation = HORIZONTAL

			for (j in 0..1) {
				val contentCell = LinearLayout(context)
				contentCell.orientation = VERTICAL

				val infoLayout = LinearLayout(context)
				infoLayout.orientation = HORIZONTAL

				primary[i * 2 + j].typeface = Theme.TYPEFACE_BOLD
				primary[i * 2 + j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)

				title[i * 2 + j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)

				secondary[i * 2 + j].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
				secondary[i * 2 + j].setPadding(AndroidUtilities.dp(4f), 0, 0, 0)

				infoLayout.addView(primary[i * 2 + j])
				infoLayout.addView(secondary[i * 2 + j])

				contentCell.addView(infoLayout)
				contentCell.addView(title[i * 2 + j])

				linearLayout.addView(contentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f))
			}

			addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 0f, 0f, 0f, (if (i == 0) 16 else 0).toFloat()))
		}
	}

	fun setData(data: OverviewChannelData?) {
		primary[0].text = data?.followersPrimary
		primary[1].text = data?.notificationsPrimary
		primary[2].text = data?.viewsPrimary
		primary[3].text = data?.sharesPrimary

		secondary[0].text = data?.followersSecondary
		secondary[1].text = ""
		secondary[2].text = data?.viewsSecondary
		secondary[3].text = data?.sharesSecondary

		title[0].text = data?.followersTitle
		title[1].text = data?.notificationsTitle
		title[2].text = data?.viewsTitle
		title[3].text = data?.sharesTitle

		updateColors()
	}

	fun setData(data: OverviewChatData?) {
		primary[0].text = data?.membersPrimary
		primary[1].text = data?.messagesPrimary
		primary[2].text = data?.viewingMembersPrimary
		primary[3].text = data?.postingMembersPrimary

		secondary[0].text = data?.membersSecondary
		secondary[1].text = data?.messagesSecondary
		secondary[2].text = data?.viewingMembersSecondary
		secondary[3].text = data?.postingMembersSecondary

		title[0].text = data?.membersTitle
		title[1].text = data?.messagesTitle
		title[2].text = data?.viewingMembersTitle
		title[3].text = data?.postingMembersTitle

		updateColors()
	}

	fun updateColors() {
		for (i in 0..3) {
			primary[i].setTextColor(context.getColor(R.color.text))
			title[i].setTextColor(context.getColor(R.color.dark_gray))
		}
	}
}
