/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.databinding.RecommendedChannelViewHolderBinding
import org.telegram.messenger.utils.reload
import org.telegram.tgnet.TLRPC

class FeedRecommendationsAdapter : RecyclerView.Adapter<RecommendedChannelViewHolder>() {
	var delegate: FeedRecommendationsAdapterDelegate? = null
	private val recommendations = mutableListOf<TLRPC.Chat>()

	fun setRecommendations(recommendations: List<TLRPC.Chat>?, append: Boolean) {
		val prevItemCount = itemCount

		if (append) {
			this.recommendations.addAll(recommendations ?: emptyList())
		}
		else {
			this.recommendations.clear()
			this.recommendations.addAll(recommendations ?: emptyList())
		}

		reload(0, itemCount, prevItemCount)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendedChannelViewHolder {
		val binding = RecommendedChannelViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return RecommendedChannelViewHolder(binding)
	}

	override fun onBindViewHolder(holder: RecommendedChannelViewHolder, position: Int) {
		holder.bind(recommendations[position])

		holder.itemView.setOnClickListener {
			delegate?.onRecommendationItemClick(recommendations[position])
		}

		if (position == itemCount - 1) {
			delegate?.fetchNextRecommendationsPage()
		}
	}

	override fun getItemCount(): Int {
		return recommendations.size
	}

	interface FeedRecommendationsAdapterDelegate {
		fun fetchNextRecommendationsPage()
		fun onRecommendationItemClick(chat: TLRPC.Chat)
	}
}
