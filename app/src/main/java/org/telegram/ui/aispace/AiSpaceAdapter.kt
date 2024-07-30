/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.aispace

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.BuildConfig.AI_BOT_ID
import org.telegram.messenger.BuildConfig.BUSINESS_BOT_ID
import org.telegram.messenger.BuildConfig.CANCER_BOT_ID
import org.telegram.messenger.BuildConfig.PHOENIX_BOT_ID
import org.telegram.messenger.R
import org.telegram.messenger.databinding.ItemBotLayoutBinding

class AiSpaceAdapter(private var onClickListener: (Long) -> Unit = {}) : RecyclerView.Adapter<AiSpaceAdapter.ViewHolder>() {

	private var botList: List<Long> = emptyList()

	fun setOnClickListener(item: (Long) -> Unit) {
		onClickListener = item
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = ItemBotLayoutBinding.inflate(inflater, parent, false)

		return ViewHolder(parent.context, binding)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val bot = botList[position]

		holder.bind(bot)

		holder.binding.root.setOnClickListener {
			onClickListener(bot)
		}
	}

	override fun getItemCount(): Int {
		return botList.size
	}

	@SuppressLint("NotifyDataSetChanged")
	fun setBotList(newList: List<Long>) {
		botList = newList
		notifyDataSetChanged()
	}

	class ViewHolder(private val context: Context, val binding: ItemBotLayoutBinding) : RecyclerView.ViewHolder(binding.root) {

		fun bind(botId: Long?) {
			when(botId) {
				AI_BOT_ID -> {
					binding.botIcon.setImageResource(R.drawable.ai_bot_avatar)
					binding.botTitle.text = context.getString(R.string.ai_chat_and_media)
					binding.botDescription.text = context.getString(R.string.general_topics_ai_chat_and_image_bot)
				}
				PHOENIX_BOT_ID -> {
					binding.botIcon.setImageResource(R.drawable.ai_phoenix_bot)
					binding.botTitle.text = context.getString(R.string.ai_phoenix_suns)
					binding.botDescription.text = context.getString(R.string.sports_ai_chat_and_image_bot)
				}
				BUSINESS_BOT_ID -> {
					binding.botIcon.setImageResource(R.drawable.business_ai_bot_avatar)
					binding.botTitle.text = context.getString(R.string.ai_business_bot_title)
					binding.botDescription.text = context.getString(R.string.ai_business_bot_description)
				}
				CANCER_BOT_ID -> {
					binding.botIcon.setImageResource(R.drawable.cancer_ai_bot_avatar)
					binding.botTitle.text = context.getString(R.string.ai_cancer_bot_title)
					binding.botDescription.text = context.getString(R.string.ai_cancer_bot_description)
				}

				else -> println(botId)
			}
		}

	}

}