/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.R
import org.telegram.messenger.databinding.ItemSubscriptionBinding
import org.telegram.messenger.databinding.LeftoversAiPackViewHolderBinding
import org.telegram.messenger.databinding.LeftoversButtonViewHolderBinding
import org.telegram.messenger.databinding.LeftoversDeleteFooterBinding
import org.telegram.messenger.databinding.LeftoversHeaderViewHolderBinding
import org.telegram.messenger.databinding.LeftoversMyChannelViewHolderBinding
import org.telegram.messenger.databinding.LeftoversSpacerViewHolderBinding
import org.telegram.messenger.databinding.LeftoversWalletViewHolderBinding
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_biz_dataRaw
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.profile.subscriptions.CurrentSubscriptionsFragment
import org.telegram.ui.profile.subscriptions.SubscriptionViewHolder
import org.telegram.ui.profile.wallet.WalletFragment

@SuppressLint("NotifyDataSetChanged")
class DeleteAccountLeftoversFragment : BaseFragment() {
	private var recyclerView: RecyclerView? = null
	private var progressBar: ContentLoadingProgressBar? = null
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val wallets = mutableListOf<ElloRpc.UserWallet>()
	private val activeSubscriptions = mutableListOf<ElloRpc.SubscriptionItem>()
	private val ownChats = mutableListOf<TLRPC.Chat>()
	private var aiPacks: ElloRpc.SubscriptionInfoAiBot? = null

	private val canDeleteAccount: Boolean
		get() {
			val emptyWallets = wallets.sumOf { it.amount.toDouble() } == 0.0
			val emptySubscriptions = activeSubscriptions.isEmpty()
			val emptyOwnChats = ownChats.isEmpty()

			return emptyWallets && emptySubscriptions && emptyOwnChats
		}

	override fun createView(context: Context): View {
		actionBar?.setAddToContainer(true)
		actionBar?.setTitle(context.getString(R.string.account_information))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		val frameLayout = FrameLayout(context)

		recyclerView = RecyclerView(context)
		recyclerView?.id = View.generateViewId()
		recyclerView?.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat())
		recyclerView?.layoutManager = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
		recyclerView?.adapter = LeftoversAdapter()
		recyclerView?.gone()

		frameLayout.addView(recyclerView)

		progressBar = ContentLoadingProgressBar(context)
		progressBar?.id = View.generateViewId()
		progressBar?.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat())
		progressBar?.isIndeterminate = true
		progressBar?.indeterminateTintList = context.resources.getColorStateList(R.color.brand, null)
		progressBar?.hide()

		frameLayout.addView(progressBar)

		fragmentView = frameLayout

		return frameLayout
	}

	override fun onResume() {
		super.onResume()

		ioScope.launch {
			loadData()
		}
	}

	override fun onPause() {
		super.onPause()
		ioScope.coroutineContext.cancelChildren()
		mainScope.coroutineContext.cancelChildren()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}
	}

	private suspend fun loadData() {
		withContext(mainScope.coroutineContext) {
			progressBar?.show()
		}

		val subscriptionsResponse = connectionsManager.performRequest(ElloRpc.getSubscriptionsRequest(type = ElloRpc.SUBSCRIPTION_TYPE_ACTIVE_CHANNELS))
		val subscriptions = (subscriptionsResponse as? TL_biz_dataRaw)?.readData<ElloRpc.Subscriptions>()?.items
		val leftoversResponse = connectionsManager.performRequest(ElloRpc.accountDeletionLeftoversRequest())

		if (leftoversResponse is TL_error) {
			withContext(mainScope.coroutineContext) {
				progressBar?.hide()
				Toast.makeText(context, leftoversResponse.text, Toast.LENGTH_SHORT).show()
				delay(300)
				finishFragment()
			}

			return
		}

		val leftovers = (leftoversResponse as? TL_biz_dataRaw)?.readData<ElloRpc.Leftovers>()

		if (leftovers == null) {
			withContext(mainScope.coroutineContext) {
				progressBar?.hide()
				Toast.makeText(context, R.string.failed_to_get_leftovers, Toast.LENGTH_SHORT).show()
				delay(300)
				finishFragment()
			}

			return
		}

		wallets.clear()

		leftovers.wallets?.wallets?.let { wallets.addAll(it) }

		activeSubscriptions.clear()

		leftovers.paidChannelsSubscribe?.chats?.mapNotNull {
			subscriptions?.find { subscription -> subscription.channelId == it.id }
		}?.let {
			activeSubscriptions.addAll(it)
		}

		ownChats.clear()

		leftovers.paidChannelsOwner?.chats?.let {
			for (chat in it) {
				val remoteChat = messagesController.loadChat(chat.id, classGuid, true)

				if (remoteChat != null) {
					ownChats.add(remoteChat)
				}
			}
		}

		aiPacks = leftovers.aiSubInfo

		withContext(mainScope.coroutineContext) {
			progressBar?.hide()
			recyclerView?.adapter?.notifyDataSetChanged()
			recyclerView?.visible()
		}
	}

	private enum class SectionMode {
		WALLET, SUBSCRIPTIONS, OWN, AI
	}

	private class HeaderViewHolder(private val binding: LeftoversHeaderViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
		fun setTitle(title: String) {
			binding.headerLabel.text = title
		}

		fun setSubtitle(subtitle: String?) {
			if (subtitle.isNullOrEmpty()) {
				binding.emptyLabel.gone()
			}
			else {
				binding.emptyLabel.text = subtitle
				binding.emptyLabel.visible()
			}
		}
	}

	private inner class WalletViewHolder(private val binding: LeftoversWalletViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
		fun bind(wallet: ElloRpc.UserWallet) {
			binding.walletNameLabel.text = if (wallet.id == walletHelper.earningsWallet?.id) {
				setVisibilityForLabels(true)

				binding.frozenBalanceLabel.text = binding.root.context.getString(R.string.simple_coin_format, walletHelper.earnings?.freezeBalance).fillElloCoinLogos()
				binding.transferBalanceLabel.text = binding.root.context.getString(R.string.simple_coin_format, walletHelper.earnings?.availableBalance).fillElloCoinLogos()

				binding.root.context.getString(R.string.business_wallet)
			}
			else {
				setVisibilityForLabels(false)
				binding.root.context.getString(R.string.main_wallet)
			}

			val amount = if (wallet.id == walletHelper.earningsWallet?.id) {
				walletHelper.earnings?.availableBalance?.let { walletHelper.earnings?.freezeBalance?.plus(it) }
			}
			else {
				wallet.amount
			}

			binding.walletAmountLabel.text = binding.root.context.getString(R.string.simple_coin_format, amount).fillElloCoinLogos()
		}

		private fun setVisibilityForLabels(visible: Boolean) {
			val visibility = if (visible) View.VISIBLE else View.GONE
			binding.frozenBalanceInfoLabel.visibility = visibility
			binding.transferBalanceInfoLabel.visibility = visibility
			binding.frozenBalanceLabel.visibility = visibility
			binding.transferBalanceLabel.visibility = visibility
		}
	}

	private inner class ButtonViewHolder(private val binding: LeftoversButtonViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
		fun setMode(mode: SectionMode) {
			when (mode) {
				SectionMode.WALLET -> {
					binding.button.text = binding.root.context.getString(R.string.go_to_ello_pay)

					binding.button.setOnClickListener {
						presentFragment(WalletFragment())
					}
				}

				SectionMode.SUBSCRIPTIONS -> {
					binding.button.text = binding.root.context.getString(R.string.go_to_my_subscriptions)

					binding.button.setOnClickListener {
						presentFragment(CurrentSubscriptionsFragment())
					}
				}

				SectionMode.OWN -> {
					binding.button.text = binding.root.context.getString(R.string.go_to_my_paid_channels)

					binding.button.setOnClickListener {
						val ids = ownChats.map { it.id }

						val args = Bundle()
						args.putLongArray(DeleteAccountMyChannels.EXTRA_CHATS, ids.toLongArray())

						presentFragment(DeleteAccountMyChannels(args))
					}
				}

				SectionMode.AI -> {
					binding.button.text = binding.root.context.getString(R.string.go_to_ai)

					binding.button.setOnClickListener {
						loadBotUser {
							openBot()
						}
					}
				}
			}
		}
	}

	private fun loadBotUser(botId: Long = BuildConfig.AI_BOT_ID, onBotReady: () -> Unit) {
		if (messagesController.getUser(botId) != null) {
			onBotReady()
		}
		else {
			ioScope.launch {
				try {
					val user = messagesController.loadUser(botId, classGuid, true)

					withContext(mainScope.coroutineContext) {
						if (user != null) {
							onBotReady()
						}
						else {
							Toast.makeText(context, R.string.error_bot_not_found, Toast.LENGTH_SHORT).show()
						}
					}
				}
				catch (e: TimeoutCancellationException) {
					withContext(mainScope.coroutineContext) {
						Toast.makeText(context, R.string.error_timeout, Toast.LENGTH_SHORT).show()
					}
				}
			}
		}
	}

	private fun openBot() {
		val args = Bundle()
		args.putInt("dialog_folder_id", 0)
		args.putInt("dialog_filter_id", 0)
		args.putLong("user_id", BuildConfig.AI_BOT_ID)

		if (!messagesController.checkCanOpenChat(args, this)) {
			return
		}

		presentFragment(ChatActivity(args))
	}

	enum class AiItemType {
		TEXT, IMAGE
	}

	private class AiViewHolder(private val binding: LeftoversAiPackViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
		fun bind(type: AiItemType, counter: Int) {
			when (type) {
				AiItemType.TEXT -> {
					binding.aiPackNameLabel.text = binding.root.context.getString(R.string.ai_text)
					binding.counterLabel.text = binding.root.context.getString(R.string.ai_bot_chat_free_requests, counter)
				}

				AiItemType.IMAGE -> {
					binding.aiPackNameLabel.text = binding.root.context.getString(R.string.ai_image)
					binding.counterLabel.text = binding.root.context.getString(R.string.ai_bot_chat_free_requests, counter)
				}
			}
		}
	}

	private inner class LeftoversDeleteFooterViewHolder(val binding: LeftoversDeleteFooterBinding) : RecyclerView.ViewHolder(binding.root) {
		init {
			binding.deleteButton.setOnClickListener {
				presentFragment(DeleteAccountLoginPasswordFragment())
			}
		}

		fun setCanDeleteAccount(canDeleteAccount: Boolean) {
			binding.deleteButton.isEnabled = canDeleteAccount

			if (canDeleteAccount) {
				binding.errorLabel.setTextColor(binding.root.context.resources.getColor(R.color.online, null))
				binding.errorLabel.setCompoundDrawablesWithIntrinsicBounds(R.drawable.tick_circle_hollow, 0, 0, 0)
				binding.errorLabel.setText(R.string.can_delete_account)
			}
			else {
				binding.errorLabel.setTextColor(binding.root.context.resources.getColor(R.color.purple, null))
				binding.errorLabel.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_error, 0, 0, 0)
				binding.errorLabel.setText(R.string.cannot_delete_account)
			}
		}
	}

	private class LeftoversSpacerViewHolder(binding: LeftoversSpacerViewHolderBinding) : RecyclerView.ViewHolder(binding.root)

	private inner class LeftoversAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			return when (viewType) {
				VIEW_TYPE_HEADER -> HeaderViewHolder(LeftoversHeaderViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				VIEW_TYPE_SPACER -> LeftoversSpacerViewHolder(LeftoversSpacerViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				VIEW_TYPE_WALLET -> WalletViewHolder(LeftoversWalletViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				VIEW_TYPE_BUTTON -> ButtonViewHolder(LeftoversButtonViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				VIEW_TYPE_SUBSCRIPTION -> SubscriptionViewHolder(ItemSubscriptionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				VIEW_TYPE_MY -> MyChannelViewHolder(LeftoversMyChannelViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				VIEW_TYPE_AI -> AiViewHolder(LeftoversAiPackViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				VIEW_TYPE_FOOTER -> LeftoversDeleteFooterViewHolder(LeftoversDeleteFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
				else -> throw IllegalArgumentException("Unknown view type: $viewType")
			}
		}

		override fun getItemCount(): Int {
			var count = 0

			// ello pay
			count += 1 // header
			count += wallets.size // wallets count
			count += 1 // footer
			count += 1 // spacer

			// subscriptions
			count += 1 // header
			count += activeSubscriptions.size // subscriptions count
			count += 1 // footer
			count += 1 // spacer

			// own chats
			count += 1 // header
			count += ownChats.size // own chats count
			count += 1 // footer
			count += 1 // spacer

			// ai packs
			count += 1 // header
			count += aiPacks?.let { 2 } ?: 0 // ai text and image packs
			count += 1 // footer

			// common footer with delete button
			count += 1 // button

			return count
		}

		override fun getItemViewType(position: Int): Int {
			var offset = 0

			// ello pay
			if (position == offset) {
				return VIEW_TYPE_HEADER
			}

			offset += 1

			if (position < offset + wallets.size) {
				return VIEW_TYPE_WALLET
			}

			offset += wallets.size

			if (position == offset) {
				return VIEW_TYPE_BUTTON
			}

			offset += 1

			if (position == offset) {
				return VIEW_TYPE_SPACER
			}

			// subscriptions

			offset += 1

			if (position == offset) {
				return VIEW_TYPE_HEADER
			}

			offset += 1

			if (position < offset + activeSubscriptions.size) {
				return VIEW_TYPE_SUBSCRIPTION
			}

			offset += activeSubscriptions.size

			if (position == offset) {
				return VIEW_TYPE_BUTTON
			}

			offset += 1

			if (position == offset) {
				return VIEW_TYPE_SPACER
			}

			// own chats

			offset += 1

			if (position == offset) {
				return VIEW_TYPE_HEADER
			}

			offset += 1

			if (position < offset + ownChats.size) {
				return VIEW_TYPE_MY
			}

			offset += ownChats.size

			if (position == offset) {
				return VIEW_TYPE_BUTTON
			}

			offset += 1

			if (position == offset) {
				return VIEW_TYPE_SPACER
			}

			// ai packs
			offset += 1

			if (position == offset) {
				return VIEW_TYPE_HEADER
			}

			offset += 1

			if (position < offset + (aiPacks?.let { 2 } ?: 0)) {
				return VIEW_TYPE_AI
			}

			offset += aiPacks?.let { 2 } ?: 0

			if (position == offset) {
				return VIEW_TYPE_BUTTON
			}

			// common footer with delete button
			offset += 1

			if (position == offset) {
				return VIEW_TYPE_FOOTER
			}

			throw IllegalArgumentException("Unknown view type for position: $position")
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				VIEW_TYPE_HEADER -> {
					val headerViewHolder = holder as HeaderViewHolder

					var offset = 0

					if (position == offset) {
						headerViewHolder.setTitle(headerViewHolder.itemView.context.getString(R.string.wallet))
						headerViewHolder.setSubtitle(null)
						return
					}

					offset += 1
					offset += wallets.size
					offset += 1
					offset += 1

					if (position == offset) {
						headerViewHolder.setTitle(headerViewHolder.itemView.context.getString(R.string.my_all_subscriptions))

						if (activeSubscriptions.isEmpty()) {
							headerViewHolder.setSubtitle(headerViewHolder.itemView.context.getString(R.string.my_subscriptions_empty))
						}
						else {
							headerViewHolder.setSubtitle(null)
						}

						return
					}

					offset += 1
					offset += activeSubscriptions.size
					offset += 1
					offset += 1

					if (position == offset) {
						headerViewHolder.setTitle(headerViewHolder.itemView.context.getString(R.string.my_paid_channels))

						if (ownChats.isEmpty()) {
							headerViewHolder.setSubtitle(headerViewHolder.itemView.context.getString(R.string.my_paid_channels_empty))
						}
						else {
							headerViewHolder.setSubtitle(null)
						}

						return
					}

					offset += 1
					offset += ownChats.size
					offset += 1
					offset += 1

					if (position == offset) {
						headerViewHolder.setTitle(headerViewHolder.itemView.context.getString(R.string.ai_packs))

						if (aiPacks == null || (aiPacks?.textTotal == 0 && aiPacks?.imgTotal == 0)) {
							headerViewHolder.setSubtitle(headerViewHolder.itemView.context.getString(R.string.ai_pack_empty))
						}
						else {
							headerViewHolder.setSubtitle(null)
						}

						return
					}
				}

				VIEW_TYPE_WALLET -> {
					val offset = 1

					if (position < offset + wallets.size) {
						(holder as WalletViewHolder).bind(wallets[position - offset])
					}
				}

				VIEW_TYPE_BUTTON -> {
					var offset = 1 + wallets.size

					if (position == offset) {
						(holder as ButtonViewHolder).setMode(SectionMode.WALLET)
						return
					}

					offset += 1
					offset += 1
					offset += 1 + activeSubscriptions.size

					if (position == offset) {
						(holder as ButtonViewHolder).setMode(SectionMode.SUBSCRIPTIONS)
						return
					}

					offset += 1
					offset += 1
					offset += 1 + ownChats.size

					if (position == offset) {
						(holder as ButtonViewHolder).setMode(SectionMode.OWN)
						return
					}

					offset += 1
					offset += 1
					offset += 1 + (aiPacks?.let { 2 } ?: 0)

					if (position == offset) {
						(holder as ButtonViewHolder).setMode(SectionMode.AI)
						return
					}
				}

				VIEW_TYPE_SUBSCRIPTION -> {
					val offset = wallets.size + 3 + 1

					if (position < offset + activeSubscriptions.size) {
						(holder as SubscriptionViewHolder).bind(activeSubscriptions[position - offset], isHideOptions = true)
					}
				}

				VIEW_TYPE_MY -> {
					val offset = wallets.size + 3 + activeSubscriptions.size + 3 + 1

					if (position < offset + ownChats.size) {
						(holder as MyChannelViewHolder).bind(ownChats[position - offset])
					}
				}

				VIEW_TYPE_AI -> {
					val offset = wallets.size + 3 + activeSubscriptions.size + 3 + ownChats.size + 3 + 1
					val pos = position - offset

					if (pos == 0) {
						(holder as AiViewHolder).bind(AiItemType.TEXT, aiPacks?.textTotal ?: 0)
					}
					else if (pos == 1) {
						(holder as AiViewHolder).bind(AiItemType.IMAGE, aiPacks?.imgTotal ?: 0)
					}
				}

				VIEW_TYPE_FOOTER -> {
					(holder as LeftoversDeleteFooterViewHolder).setCanDeleteAccount(canDeleteAccount)
				}
			}
		}
	}

	companion object {
		private const val VIEW_TYPE_HEADER = 0
		private const val VIEW_TYPE_SPACER = 1
		private const val VIEW_TYPE_WALLET = 2
		private const val VIEW_TYPE_BUTTON = 3
		private const val VIEW_TYPE_SUBSCRIPTION = 4
		private const val VIEW_TYPE_MY = 5
		private const val VIEW_TYPE_AI = 6
		private const val VIEW_TYPE_FOOTER = 7
	}
}
