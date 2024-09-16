/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.aispace

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.databinding.ItemBotLayoutBinding
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper

class AiSpaceAdapter(private var onClickListener: (Long) -> Unit = {}) : RecyclerView.Adapter<AiSpaceAdapter.ViewHolder>() {

	private var botList: List<ElloRpc.AiSpaceBotsInfo> = emptyList()

	fun setOnClickListener(item: (Long) -> Unit) {
		onClickListener = item
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = ItemBotLayoutBinding.inflate(inflater, parent, false)

		return ViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val bot = botList[position]

		holder.bind(bot)

		holder.binding.root.setOnClickListener {
			bot.botId?.let { id -> onClickListener(id) }
		}
	}

	override fun getItemCount(): Int {
		return botList.size
	}

	@SuppressLint("NotifyDataSetChanged")
	fun setBotList(newList: List<ElloRpc.AiSpaceBotsInfo>) {
		botList = newList
		notifyDataSetChanged()
	}

	class ViewHolder(val binding: ItemBotLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
		private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
		private val avatarImageView = AvatarImageView(binding.root.context)
		private var user: User? = null

		init {
			binding.botIcon.addView(avatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			val radius = AndroidUtilities.dp(24f)

			avatarImageView.imageReceiver.setAllowDecodeSingleFrame(true)
			avatarImageView.setRoundRadius(radius, radius, radius, radius)
			avatarImageView.setImage(null, null, avatarDrawable, null)
		}

		fun bind(botsInfo: ElloRpc.AiSpaceBotsInfo) {
			user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(botsInfo.botId)

			avatarDrawable.setInfo(user)

			val photo = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL)

			if (photo != null) {
				avatarImageView.setImage(photo, null, avatarDrawable, user)
			}
			else {
				avatarImageView.setImage(null, null, binding.root.context.getDrawable(R.drawable.ai_bot_avatar), user)
			}

			binding.botTitle.text = botsInfo.firstName
			binding.botDescription.text = botsInfo.description
		}

	}

}