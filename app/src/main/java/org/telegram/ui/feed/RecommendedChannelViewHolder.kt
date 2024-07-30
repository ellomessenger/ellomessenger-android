/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.feed

import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.databinding.RecommendedChannelViewHolderBinding
import org.telegram.messenger.utils.addRipple
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper

class RecommendedChannelViewHolder(private val binding: RecommendedChannelViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
	private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
	private val avatarImageView = AvatarImageView(binding.root.context)

	init {
		binding.root.addRipple()

		binding.avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val radius = AndroidUtilities.dp(24f)

		avatarImageView.imageReceiver.setAllowDecodeSingleFrame(true)
		avatarImageView.setRoundRadius(radius, radius, radius, radius)
		avatarImageView.setImage(null, null, avatarDrawable, null)
	}

	fun bind(chat: TLRPC.Chat) {
		avatarDrawable.setInfo(chat)

		val photo = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL)

		if (photo != null) {
			avatarImageView.setImage(photo, null, avatarDrawable, chat)
		}
		else {
			avatarImageView.setImage(null, null, avatarDrawable, chat)
		}

		binding.nameLabel.text = chat.title?.trim()
		binding.introLabel.text = LocaleController.formatPluralString("Subscribers", chat.participants_count)

		if (chat.adult) {
			binding.adultChannelIcon.visible()
		}
		else {
			binding.adultChannelIcon.gone()
		}

		if (chat.verified) {
			binding.verifiedIcon.visible()
		}
		else {
			binding.verifiedIcon.gone()
		}

		when {
			ChatObject.isOnlineCourse(chat) -> {
				when (chat.pay_type) {
					TLRPC.Chat.PAY_TYPE_SUBSCRIBE, TLRPC.Chat.PAY_TYPE_NONE, TLRPC.Chat.PAY_TYPE_BASE -> {
						binding.onlineCourseIcon.visible()
						binding.paidFeedIcon.gone()
					}
					else -> {
						binding.onlineCourseIcon.gone()
					}
				}
			}
			else -> {
				when (chat.pay_type) {
					TLRPC.Chat.PAY_TYPE_NONE, TLRPC.Chat.PAY_TYPE_BASE -> {
						binding.paidFeedIcon.gone()
					}
					TLRPC.Chat.PAY_TYPE_SUBSCRIBE -> {
						binding.paidFeedIcon.visible()
						binding.onlineCourseIcon.gone()
					}
					else -> {
						binding.paidFeedIcon.gone()
					}
				}
			}
		}
	}
}
