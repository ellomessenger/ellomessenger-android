/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.databinding.ExploreViewHolderBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.removeDuplicates
import org.telegram.tgnet.TLRPC.Message

class ExploreAdapter : RecyclerView.Adapter<ExploreViewHolder>() {
	private val items = mutableListOf<MessageObject>()
	var delegate: FeedExploreAdapterDelegate? = null

	@SuppressLint("NotifyDataSetChanged")
	fun setItems(items: List<Message>?, append: Boolean, currentAccount: Int) {
		val newMessageObjects = items?.map { MessageObject(currentAccount, it, generateLayout = false, checkMediaExists = true) }

		if (!append) {
			this.items.clear()
		}

		newMessageObjects?.let {
			this.items.addAll(it)
		}

		this.items.removeDuplicates(this.items)

		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExploreViewHolder {
		val binding = ExploreViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)

		return ExploreViewHolder(binding).also {
			it.delegate = delegate
		}
	}

	override fun getItemCount(): Int {
		return items.size
	}

	override fun onBindViewHolder(holder: ExploreViewHolder, position: Int) {
		val item = items[position]

		holder.itemView.setOnClickListener {
			delegate?.onExploreItemClick(item)
		}

		holder.messageObject = item

		if (position == itemCount - 1) {
			delegate?.fetchNextExplorePage()
		}
	}

	interface FeedExploreAdapterDelegate {
		fun fetchNextExplorePage()
		fun onExploreItemClick(messageObject: MessageObject)
	}
}
