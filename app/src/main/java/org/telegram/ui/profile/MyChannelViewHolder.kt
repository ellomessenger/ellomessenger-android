/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.profile

import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.R
import org.telegram.messenger.databinding.LeftoversMyChannelViewHolderBinding
import org.telegram.messenger.utils.addRipple
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper

class MyChannelViewHolder(private val binding: LeftoversMyChannelViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
	private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
	private val avatarImageView = AvatarImageView(binding.root.context)

	init {
		binding.avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val radius = binding.root.context.resources.getDimensionPixelSize(R.dimen.common_size_10dp)

		avatarImageView.imageReceiver.setAllowDecodeSingleFrame(true)
		avatarImageView.setRoundRadius(radius, radius, radius, radius)

		avatarImageView.setImage(null, null, avatarDrawable, null)
	}

	fun bind(chat: TLRPC.Chat) {
		binding.channelName.text = chat.title
		binding.subscribersCountLabel.text = chat.participantsCount.toString()

		if (chat.adult) {
			binding.adultChannelIcon.visible()
		}
		else {
			binding.adultChannelIcon.gone()
		}

		avatarDrawable.setInfo(chat)

		val photo = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL)

		if (photo != null) {
			avatarImageView.setImage(photo, null, avatarDrawable, chat)
		}
		else {
			avatarImageView.setImage(null, null, avatarDrawable, chat)
		}
	}

	fun setOnClickListener(listener: (() -> Unit)?) {
		if (listener == null) {
			binding.root.background = null
		}
		else {
			binding.root.addRipple()
		}

		binding.root.setOnClickListener { listener?.invoke() }
	}
}
