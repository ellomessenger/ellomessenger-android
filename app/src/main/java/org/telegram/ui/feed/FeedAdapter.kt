/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.databinding.FeedViewHolderBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.getChannel
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.Message
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView

class FeedAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	private val currentAccount = UserConfig.selectedAccount
	private var isLastPage = true
	private var isLoadingFeedData = false
	var delegate: FeedViewHolder.Delegate? = null
	var pinned: LongArray? = null

	var feed: List<List<MessageObject>>? = null
		private set

	/**
	 * @param feed list of messages
	 * @return number of newly added messages
	 */
	@SuppressLint("NotifyDataSetChanged")
	fun setFeed(feed: List<Message?>?, append: Boolean, isLastPage: Boolean): List<List<MessageObject>> {
		this.isLastPage = isLastPage

		val completeFeed = mutableSetOf<MessageObject>()

		if (append) {
			this.feed?.flatten()?.forEach {
				completeFeed.add(it)
			}
		}

		feed?.asSequence()?.filterNot {
			it is TLRPC.TL_messageService
		}?.mapNotNull {
			it?.let {
				MessageObject(currentAccount, it, generateLayout = false, checkMediaExists = true)
			}
		}?.forEach {
			completeFeed.add(it)
		}

		val groupedFeed = completeFeed.distinctBy {
			it.messageOwner?.id ?: 0
		}.groupBy {
			it.messageOwner?.groupId ?: 0L
		}

		val flatFeed = mutableListOf<List<MessageObject>>()

		groupedFeed.keys.forEach { key ->
			val messages = groupedFeed[key]

			if (messages.isNullOrEmpty()) {
				return@forEach
			}

			if (key == 0L) {
				messages.forEach {
					flatFeed.add(listOf(it))
				}
			}
			else {
				flatFeed.add(messages.sortedByDescending { it.messageOwner?.date ?: 0 })
			}
		}

		flatFeed.sortByDescending { it.firstOrNull()?.messageOwner?.date ?: 0 }

		val changedMessages = mutableSetOf<List<MessageObject>>()
		val newMessages = mutableSetOf<List<MessageObject>>()
		val removedMessages = mutableSetOf<List<MessageObject>>()

		for (newMessage in flatFeed) {
			var sameOldMessage = this.feed?.firstOrNull { old ->
				old.any {
					it.messageOwner?.groupId != 0L && it.messageOwner?.groupId == newMessage.firstOrNull()?.messageOwner?.groupId
				}
			}

			if (sameOldMessage == null) {
				sameOldMessage = this.feed?.firstOrNull {
					val oldId = it.firstOrNull()?.id
					val newId = newMessage.firstOrNull()?.id
					oldId != null && newId != null && oldId == newId
				}
			}

			if (sameOldMessage != null) {
				if (newMessage != sameOldMessage) {
					changedMessages.add(newMessage)
				}
			}
			else {
				newMessages.add(newMessage)
			}
		}

		for (oldMessage in this.feed.orEmpty()) {
			var sameNewMessage = flatFeed.firstOrNull { new ->
				new.any { it.messageOwner?.groupId == oldMessage.firstOrNull()?.messageOwner?.groupId }
			}

			if (sameNewMessage == null) {
				sameNewMessage = flatFeed.firstOrNull {
					val oldId = oldMessage.firstOrNull()?.id
					val newId = it.firstOrNull()?.id
					oldId != null && newId != null && oldId == newId
				}
			}

			if (sameNewMessage == null) {
				removedMessages.add(oldMessage)
			}
		}

		this.feed = flatFeed

		/* Yes, I know what I am doing.
		We need to update the whole list because of the way we are displaying messages:
		we do not want to use animation and we need to show "New messages" button
		when new messages are added to the top of the list */
		notifyDataSetChanged()

		return newMessages.toList()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			VIEW_TYPE_FEED -> {
				val binding = FeedViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
				FeedViewHolder(binding)
			}

			VIEW_TYPE_LOADING -> {
				val loaderContainer = LinearLayout(parent.context)
				LoadingViewHolder(loaderContainer)
			}

			else -> throw IllegalStateException("Unknown view type: $viewType")
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (holder.itemViewType) {
			VIEW_TYPE_FEED -> {
				val feedItem = feed?.get(position)

				(holder as FeedViewHolder).let {
					it.bind(feedItem, pinned?.contains(feedItem?.firstOrNull()?.messageOwner?.getChannel()?.id ?: Long.MAX_VALUE) ?: false)

					it.binding.root.setOnClickListener {
						feedItem?.firstOrNull()?.let { message ->
							message.messageOwner?.let { messageOwner ->
								delegate?.onFeedItemClick(messageOwner)
							}
						}
					}

					it.delegate = this.delegate
				}
			}

			VIEW_TYPE_LOADING -> {
				(holder as LoadingViewHolder).apply { if (isLoadingFeedData) playAnimation() else stopAnimation() }
			}
		}

		if (position == itemCount - 1) {
			delegate?.fetchNextFeedPage()
		}
	}

	override fun getItemCount(): Int {
		return (feed?.size ?: 0) + (if (isLastPage) 1 else 0)
	}

	override fun getItemViewType(position: Int): Int {
		return if (position == (feed?.size ?: -1)) VIEW_TYPE_LOADING else VIEW_TYPE_FEED
	}

	fun updateLoadingState(isLoading: Boolean) {
		isLoadingFeedData = isLoading
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
		private const val VIEW_TYPE_LOADING = 1
		private const val VIEW_TYPE_FEED = 0
	}
}
