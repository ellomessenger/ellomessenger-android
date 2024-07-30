package org.telegram.ui.profile.subscriptions_channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.databinding.ItemSubscriptionChannelBinding
import org.telegram.tgnet.ElloRpc

class SubscriptionChannelsAdapter(private val action: (SubscriptionChannelAction) -> Unit) : ListAdapter<ElloRpc.SubscriptionItem, SubscriptionChannelsAdapter.ChannelViewHolder>(CardsDiffCallBack()) {
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
		return ChannelViewHolder(ItemSubscriptionChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))
	}

	override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
		val item = currentList[position]
		holder.onBind(item, action)
	}

	class ChannelViewHolder(private val binding: ItemSubscriptionChannelBinding) : RecyclerView.ViewHolder(binding.root) {
		fun onBind(subscriptionItem: ElloRpc.SubscriptionItem, action: (SubscriptionChannelAction) -> Unit) {
			binding.run {
//				channelName.text = subscriptionItem.name
//				priceValue.text = subscriptionItem.price.toString()
//				earnedAmountValue.text = subscriptionItem.earned.toString()
//				membersValue.text = subscriptionItem.members.toString()

				edit.setOnClickListener {
					action(SubscriptionChannelAction.Edit(subscriptionItem))
				}
			}
		}
	}

	private class CardsDiffCallBack : DiffUtil.ItemCallback<ElloRpc.SubscriptionItem>() {
		override fun areItemsTheSame(oldItem: ElloRpc.SubscriptionItem, newItem: ElloRpc.SubscriptionItem): Boolean {
			return oldItem == newItem
		}

		override fun areContentsTheSame(oldItem: ElloRpc.SubscriptionItem, newItem: ElloRpc.SubscriptionItem): Boolean {
			return oldItem.channelId == newItem.channelId || oldItem.userId == newItem.userId
		}
	}

	sealed class SubscriptionChannelAction {
		data class Edit(val subscriptionItem: ElloRpc.SubscriptionItem) : SubscriptionChannelAction()
		data class Open(val subscriptionItem: ElloRpc.SubscriptionItem) : SubscriptionChannelAction()
	}
}
