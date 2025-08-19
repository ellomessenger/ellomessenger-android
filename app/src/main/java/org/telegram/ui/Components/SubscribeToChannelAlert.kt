/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isMasterclass
import org.telegram.messenger.ChatObject.isPaidChannel
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.SubscribeToChannelDialogBinding
import org.telegram.messenger.utils.LinkClickListener
import org.telegram.messenger.utils.LinkTouchMovementMethod
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.processForLinks
import org.telegram.messenger.utils.toDateOnlyString
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.LayoutHelper.createFrame
import java.util.Date

class SubscribeToChannelAlert(context: Context, currentChat: TLRPC.Chat, currentChatInfo: TLRPC.ChatFull?, subscriptionView: View?, linkClickListener: LinkClickListener, subscribeClickListener: OnClickListener) {
	private val binding: SubscribeToChannelDialogBinding

	val view: View
		get() = binding.root

	init {
		val showingDialog = subscriptionView != null

		binding = if (showingDialog && subscriptionView != null) {
			SubscribeToChannelDialogBinding.bind(subscriptionView)
		}
		else {
			SubscribeToChannelDialogBinding.inflate(LayoutInflater.from(context))
		}

		binding.channelProfile.about.setLinkTextColor(context.getColor(R.color.brand))
		binding.channelProfile.about.movementMethod = LinkTouchMovementMethod()

		val avatarDrawable = AvatarDrawable(currentChat)

		val avatarImageView = BackupImageView(context)
		avatarImageView.setForUserOrChat(currentChat, avatarDrawable)
		avatarImageView.setRoundRadius(AndroidUtilities.dp(27f))

		binding.channelProfile.avatarContainer.addView(avatarImageView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		binding.channelProfile.title.text = currentChat.title // or currentChat.channel_name?
		binding.channelProfile.title.isSelected = true
		binding.channelProfile.subTitle.text = LocaleController.formatPluralString("Subscribers", currentChat.participantsCount)

		if (!currentChatInfo?.about.isNullOrEmpty()) {
			binding.channelProfile.about.text = currentChatInfo?.about?.processForLinks(context, true, linkClickListener)
			binding.channelProfile.about.visible()
		}
		else {
			binding.channelProfile.about.gone()
		}

		if (!currentChatInfo?.about.isNullOrEmpty() && binding.channelProfile.about.text.length > ChatActivity.MAX_VISIBLE_LENGTH) {
			binding.channelProfile.more.visible()
		}

		binding.channelProfile.more.setOnClickListener {
			binding.channelProfile.about.maxLines = Int.MAX_VALUE
			binding.channelProfile.more.gone()
		}

		val isPaid = isPaidChannel(currentChat)
		val isAdult = currentChat.adult

		if (isPaid) {
			binding.priceContainer.visible()
			binding.description.visible()

			if (isMasterclass(currentChat)) {
				binding.subsChannel.text = context.getString(R.string.masterclass)
				binding.subscribeButton.text = context.getString(R.string.VoipChatJoin)
				binding.channelProfile.paid.setImageResource(R.drawable.online_course)
				binding.description.setText(R.string.course_description)
			}
			else {
				binding.description.setText(R.string.subscription_description)
			}

			if (currentChat.verified) {
				binding.channelProfile.verified.visible()
			}
			else {
				binding.channelProfile.verified.gone()
			}

			binding.adultLayout.visibility = if (isAdult) View.VISIBLE else View.GONE
			binding.channelProfile.adult.visibility = if (isAdult) View.VISIBLE else View.GONE
			binding.price.text = LocaleController.formatString(if (isMasterclass(currentChat)) R.string.PricePerCourse else R.string.PricePerMonthMe, currentChat.cost)

			if (currentChat.startDate != 0L) {
				val startDate = Date(currentChat.startDate)
				binding.startDateLabel.text = startDate.toDateOnlyString()
			}
			else {
				binding.startDateContainer.gone()
			}

			if (currentChat.endDate != 0L) {
				val endDate = Date(currentChat.endDate)
				binding.endDateLabel.text = endDate.toDateOnlyString()
			}
			else {
				binding.endDateContainer.gone()
			}
		}
		else if (isAdult) {
			binding.priceContainer.gone()
			binding.description.gone()
			binding.adultLayout.visible()
			binding.channelProfile.adult.visible()

			if (currentChat.verified) {
				binding.channelProfile.verified.visible()
			}
			else {
				binding.channelProfile.verified.gone()
			}
		}
//		else {
//			// impossible situation, but should be covered anyway
//			return
//		}

		binding.subscribeButton.setOnClickListener(subscribeClickListener)
	}

	fun setCloseButtonClickListener(listener: OnClickListener) {
		binding.closeButton.setOnClickListener(listener)
	}
}
