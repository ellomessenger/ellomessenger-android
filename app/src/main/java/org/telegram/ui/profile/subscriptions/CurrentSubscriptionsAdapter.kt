/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.profile.subscriptions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.telegram.messenger.databinding.ItemSubscriptionBinding
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.tlrpc.TLObject

class CurrentSubscriptionsAdapter(private val action: ((SubscriptionItemAction) -> Unit)? = null) : ListAdapter<ElloRpc.SubscriptionItem, SubscriptionViewHolder>(CardsDiffCallBack()) {
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
		return SubscriptionViewHolder(ItemSubscriptionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
	}

	override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
		val item = currentList[position]
		holder.bind(item, action)
	}

	private class CardsDiffCallBack : DiffUtil.ItemCallback<ElloRpc.SubscriptionItem>() {
		override fun areItemsTheSame(oldItem: ElloRpc.SubscriptionItem, newItem: ElloRpc.SubscriptionItem): Boolean {
			return oldItem == newItem
		}

		override fun areContentsTheSame(oldItem: ElloRpc.SubscriptionItem, newItem: ElloRpc.SubscriptionItem): Boolean {
			return oldItem.channelId == newItem.channelId || oldItem.userId == newItem.userId
		}
	}

	sealed class SubscriptionItemAction {
		data class Cancel(val subscriptionItem: ElloRpc.SubscriptionItem, val peer: TLObject, val view: View) : SubscriptionItemAction()
		data class Subscribe(val subscriptionItem: ElloRpc.SubscriptionItem, val peer: TLObject, val view: View) : SubscriptionItemAction()
	}
}
