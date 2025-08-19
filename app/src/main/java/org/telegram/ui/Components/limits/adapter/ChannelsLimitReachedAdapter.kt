/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Components.limits.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.R
import org.telegram.messenger.databinding.ItemChannelsLimitReachedBinding
import org.telegram.tgnet.TLRPC
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper

class ChannelsLimitReachedAdapter(private var onClickListener: (TLRPC.Chat) -> Unit = {}) : ListAdapter<TLRPC.Chat, ChannelsLimitReachedAdapter.ChannelsLimitReachedViewHolder>(DiffCallBack()) {

	fun setOnClickListener(item: (TLRPC.Chat) -> Unit) {
		onClickListener = item
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelsLimitReachedViewHolder {
		return ChannelsLimitReachedViewHolder(
				parent.context,
				ItemChannelsLimitReachedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		)
	}

	override fun onBindViewHolder(holder: ChannelsLimitReachedViewHolder, position: Int) {
		val item = currentList[position]

		holder.binding.root.setOnClickListener {
			onClickListener(item)
		}

		holder.bind(item)
	}

	inner class ChannelsLimitReachedViewHolder(private val context: Context, val binding: ItemChannelsLimitReachedBinding) : RecyclerView.ViewHolder(binding.root) {

		private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
		private val avatarImageView = AvatarImageView(binding.root.context)

		init {
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

			binding.channelsGroupName.text = chat.title
			binding.username.text = context.getString(R.string.channels_limit_username_prefix, chat.username)
		}

	}

	private class DiffCallBack : DiffUtil.ItemCallback<TLRPC.Chat>() {
		override fun areItemsTheSame(oldItem: TLRPC.Chat, newItem: TLRPC.Chat): Boolean {
			return oldItem == newItem
		}

		override fun areContentsTheSame(oldItem: TLRPC.Chat, newItem: TLRPC.Chat): Boolean {
			return oldItem.id == newItem.id || newItem.id == oldItem.id
		}
	}

}