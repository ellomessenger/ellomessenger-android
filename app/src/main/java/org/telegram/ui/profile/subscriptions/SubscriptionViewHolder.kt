/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024
 */
package org.telegram.ui.profile.subscriptions

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.databinding.ItemSubscriptionBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.profile.utils.toFormattedDate

class SubscriptionViewHolder(private val binding: ItemSubscriptionBinding) : RecyclerView.ViewHolder(binding.root), NotificationCenter.NotificationCenterDelegate {
	private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = true }
	private val avatarImageView = AvatarImageView(binding.root.context)
	private val classGuid = ConnectionsManager.generateClassGuid()
	private val messagesController = MessagesController.getInstance(UserConfig.selectedAccount)
	private var subscription: ElloRpc.SubscriptionItem? = null
	private var channel: TLRPC.Chat? = null
	private var user: User? = null

	init {
		binding.avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val radius = binding.root.context.resources.getDimensionPixelSize(R.dimen.common_size_10dp)

		avatarImageView.imageReceiver.setAllowDecodeSingleFrame(true)
		avatarImageView.setRoundRadius(radius, radius, radius, radius)

		avatarImageView.setImage(null, null, avatarDrawable, null)
	}

	fun bind(subscription: ElloRpc.SubscriptionItem, action: ((CurrentSubscriptionsAdapter.SubscriptionItemAction) -> Unit)? = null, isHideOptions: Boolean? = false) {
		this.subscription = subscription

		binding.run {
			if (isHideOptions == true) {
				options.gone()
			}

			if (subscription.isCancelled) {
				statusLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, R.color.dark_gray))
				statusIcon.setImageResource(R.drawable.ic_cancel)
				statusText.text = binding.root.context.getString(R.string.cancelled)

				statusLayout.visible()

				options.setOnClickListener {
					val peer = channel ?: user ?: return@setOnClickListener
					action?.invoke(CurrentSubscriptionsAdapter.SubscriptionItemAction.Subscribe(subscriptionItem = subscription, peer = peer, view = it))
				}
			}
			else if (subscription.isActive) {
				statusLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, R.color.green))
				statusIcon.setImageResource(R.drawable.ic_success_mark_circle_filled_white)
				statusText.text = binding.root.context.getString(R.string.active)

				statusLayout.visible()

				options.setOnClickListener {
					val peer = channel ?: user ?: return@setOnClickListener
					action?.invoke(CurrentSubscriptionsAdapter.SubscriptionItemAction.Cancel(subscriptionItem = subscription, peer = peer, view = it))
				}
			}
			else {
				statusLayout.gone()
				statusIcon.setImageDrawable(null)
				statusText.text = null

				options.setOnClickListener {
					val peer = channel ?: user ?: return@setOnClickListener
					action?.invoke(CurrentSubscriptionsAdapter.SubscriptionItemAction.Subscribe(subscriptionItem = subscription, peer = peer, view = it))
				}
			}

			price.text = binding.root.context.getString(R.string.simple_money_format, subscription.amount).replace("+", "")

			if (subscription.expireAt > 0L) {
				validDate.text = (subscription.expireAt * 1000L).toFormattedDate()
				validDate.visible()
				validTillLabel.setText(R.string.valid_till)
			}
			else {
				validDate.invisible()
				validDate.text = null
				validTillLabel.setText(R.string.ongoing)
			}

			if (subscription.channelId > 0) {
				fillChannelPeerInfo(subscription.channelId)
			}
			else {
				fillUserPeerInfo(subscription.userId)
			}
		}
	}

	private fun fillChannelPeerInfo(peerId: Long) {
		val chat = messagesController.getChat(peerId)

		fillChannelPeerInfo(chat)

		if (chat == null) {
			NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.chatInfoDidLoad)
			loadChannelPeerInfo(peerId)
		}
	}

	private fun fillUserPeerInfo(user: User?) {
		this.user = user

		avatarDrawable.setInfo(user)

		val photo = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL)

		if (photo != null) {
			avatarImageView.setImage(photo, null, avatarDrawable, user)
		}
		else {
			avatarImageView.setImage(null, null, avatarDrawable, user)
		}

		binding.channelName.text = user?.username
	}

	private fun loadUserPeerInfo(peerId: Long) {
		val user = TLRPC.TL_userEmpty().apply { id = peerId }
		messagesController.loadFullUser(user, classGuid, true)
	}

	private fun fillUserPeerInfo(peerId: Long) {
		val user = messagesController.getUser(peerId)

		fillUserPeerInfo(user)

		if (user == null) {
			NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.userInfoDidLoad)
			loadUserPeerInfo(peerId)
		}
	}

	private fun fillChannelPeerInfo(chat: TLRPC.Chat?) {
		this.channel = chat

		avatarDrawable.setInfo(chat)

		val photo = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL)

		if (photo != null) {
			avatarImageView.setImage(photo, null, avatarDrawable, chat)
		}
		else {
			avatarImageView.setImage(null, null, avatarDrawable, chat)
		}

		binding.channelName.text = if (chat?.deactivated == true) binding.root.context.getString(R.string.txt_deleted).uppercase() else chat?.title
	}

	private fun loadChannelPeerInfo(peerId: Long) {
		messagesController.loadFullChat(peerId, classGuid, true)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.chatInfoDidLoad -> {
				val chatFull = args[0] as TLRPC.ChatFull
				val channelId = subscription?.channelId ?: 0

				if (channelId > 0 && chatFull.id == channelId) {
					NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad)

					val chat = messagesController.getChat(channelId)

					if (chat != null) {
						fillChannelPeerInfo(chat)
					}
				}
			}

			NotificationCenter.userInfoDidLoad -> {
				val userId = args[0] as Long

				if (userId > 0 && userId == subscription?.userId) {
					NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.userInfoDidLoad)

					val user = messagesController.getUser(userId)

					if (user != null) {
						fillUserPeerInfo(user)
					}
				}
			}
		}
	}
}
