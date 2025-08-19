/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.aispace

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.databinding.ItemBotLayoutBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC.User
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView

class AiSpaceAdapter(private var onClickListener: (Long) -> Unit = {}) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var botList: List<ElloRpc.AiSpaceBotsInfo> = emptyList()
	var currentPage = 0
	var pageSize = 10
	var paginatedList: MutableList<ElloRpc.AiSpaceBotsInfo> = mutableListOf()

	private var isLoading = false

	fun setOnClickListener(item: (Long) -> Unit) {
		onClickListener = item
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			VIEW_TYPE_BOT -> {
				val binding = ItemBotLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
				ViewHolder(binding)
			}

			VIEW_TYPE_LOADING -> {
				val loaderContainer = LinearLayout(parent.context)
				LoadingViewHolder(loaderContainer)
			}

			else -> throw IllegalStateException("Unknown view type: $viewType")
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is ViewHolder) {
			val bot = paginatedList[position]
			holder.bind(bot)

			holder.binding.root.setOnClickListener {
				bot.botId?.let { id -> onClickListener(id) }
			}
		}
		else if (holder is LoadingViewHolder) {
			holder.apply { if (isLoading) playAnimation() else stopAnimation() }
		}
	}

	override fun getItemCount(): Int {
		return paginatedList.size + if (isLoading) 1 else 0
	}

	override fun getItemViewType(position: Int): Int {
		return if (position == paginatedList.size && isLoading) {
			VIEW_TYPE_LOADING
		}
		else {
			VIEW_TYPE_BOT
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	fun updateBotList(newList: List<ElloRpc.AiSpaceBotsInfo>) {
		botList = newList
		currentPage = 0
		paginatedList.clear()
		loadNextPage()
		notifyDataSetChanged()
	}

	fun loadNextPage() {
		isLoading = true

		val startIndex = currentPage * pageSize
		val endIndex = minOf(startIndex + pageSize, botList.size)

		if (startIndex < endIndex) {
			paginatedList.addAll(botList.subList(startIndex, endIndex))
			currentPage++

			if (paginatedList.size == botList.size) {
				isLoading = false
			}
		}
	}

	class ViewHolder(val binding: ItemBotLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
		private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
		private val avatarImageView = AvatarImageView(binding.root.context)
		private var user: User? = null

		init {
			binding.botIcon.addView(avatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			avatarImageView.imageReceiver.setAllowDecodeSingleFrame(true)
			avatarImageView.setRoundRadius(AndroidUtilities.dp(21f))
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

	class LoadingViewHolder(loaderContainer: LinearLayout) : RecyclerView.ViewHolder(loaderContainer) {
		private val loaderAnimationView: RLottieImageView = RLottieImageView(itemView.context)

		init {
			loaderContainer.gravity = Gravity.CENTER
			loaderContainer.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat())
			loaderContainer.orientation = LinearLayout.VERTICAL

			loaderAnimationView.setAutoRepeat(true)
			loaderAnimationView.setAnimation(R.raw.ello_loader, 50, 50)

			loaderContainer.addView(loaderAnimationView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
		}

		fun playAnimation() {
			loaderAnimationView.playAnimation()
			loaderAnimationView.visible()
		}

		fun stopAnimation() {
			loaderAnimationView.stopAnimation()
			loaderAnimationView.gone()
		}
	}

	companion object {
		const val VIEW_TYPE_LOADING = 1
		const val VIEW_TYPE_BOT = 0
	}

}