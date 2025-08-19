/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2025.
 * Copyright Shamil Afadniyev, Ello 2025.
 */
package org.telegram.ui.profile.subscriptions

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentCurrentSubscriptionsBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.showMenu
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.profile.utils.CustomDividerItemDecoration

class CurrentSubscriptionsFragment : BaseFragment() {
	private var binding: FragmentCurrentSubscriptionsBinding? = null
	private var adapter: CurrentSubscriptionsAdapter? = null
	private var subscriptions: List<ElloRpc.SubscriptionItem>? = null
	private var emptyImage: RLottieImageView? = null

	private var subscriptionType = ElloRpc.SubscriptionType.ACTIVE_CHANNELS
		set(value) {
			field = value

			binding?.menuField?.setText(when (value) {
				ElloRpc.SubscriptionType.ACTIVE_CHANNELS -> context?.getString(R.string.my_current_subscriptions)
				ElloRpc.SubscriptionType.PAST_CHANNELS -> context?.getString(R.string.my_previous_subscriptions)
			})

			loadData()
		}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.current_subscriptions))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (parentActivity == null) {
					return
				}

				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = FragmentCurrentSubscriptionsBinding.inflate(LayoutInflater.from(context))

		val botInfo = messagesController.getUser(BuildConfig.AI_BOT_ID)

		if (botInfo == null) {
			messagesController.loadFullUser(TLRPC.TLUser().apply { id = BuildConfig.AI_BOT_ID }, classGuid, true)
		}

		binding?.menuField?.setOnClickListener {
			showSubscriptionTypeMenu(it)
		}

		binding?.menu?.setEndIconOnClickListener {
			showSubscriptionTypeMenu(binding!!.menuField)
		}

		adapter = CurrentSubscriptionsAdapter {
			when (it) {
				is CurrentSubscriptionsAdapter.SubscriptionItemAction.Cancel -> {
					when (it.subscriptionItem.type) {
						PEER_TYPE_APPLE, PEER_TYPE_GOOGLE -> showCancelMembershipSubscriptionMenu(it.view, it.subscriptionItem.type)
						else -> showCancelSubscriptionMenu(v = it.view, peer = it.peer, botType = it.botType, expireAt = it.expireAt)
					}
				}

				is CurrentSubscriptionsAdapter.SubscriptionItemAction.Subscribe -> {
					when (it.subscriptionItem.type) {
						PEER_TYPE_APPLE, PEER_TYPE_GOOGLE -> showMembershipSubscribeMenu(it.view, it.subscriptionItem.type)
						else -> showSubscribeMenu(it.view, peer = it.peer, botType = it.botType)
					}
				}
			}
		}

		val itemDecorator = CustomDividerItemDecoration(context, R.drawable.subscription_item_divider, 81)

		binding?.channelList?.addItemDecoration(itemDecorator)
		binding?.channelList?.adapter = adapter
		binding?.channelList?.layoutManager = LinearLayoutManager(context)

		emptyImage = RLottieImageView(context)
		emptyImage?.setAutoRepeat(true)
		emptyImage?.setAnimation(R.raw.panda_chat_list_no_results, 160, 160)

		binding?.emptyListImage?.addView(emptyImage, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		loadData()

		fragmentView = binding?.root

		return binding?.root
	}

	private fun loadData() {
		val request = ElloRpc.getSubscriptionsRequest(subscriptionType)

		connectionsManager.sendRequest(request) { response, _ ->
			if (response is TLRPC.TLBizDataRaw) {
				val subscriptions = response.readData<ElloRpc.Subscriptions>()

				this.subscriptions = subscriptions?.items

				AndroidUtilities.runOnUIThread {
					adapter?.submitList(subscriptions?.items)

					if (this.subscriptions.isNullOrEmpty()) {
						when (subscriptionType) {
							ElloRpc.SubscriptionType.ACTIVE_CHANNELS -> {
								binding?.emptyListLabel?.setText(R.string.no_current_subscriptions)
							}

							ElloRpc.SubscriptionType.PAST_CHANNELS -> {
								binding?.emptyListLabel?.setText(R.string.no_past_subscriptions)
							}
						}

						binding?.emptyListImage?.visible()

						emptyImage?.setAutoRepeat(true)
						emptyImage?.playAnimation()

						binding?.emptyListLabel?.visible()
					}
					else {
						binding?.emptyListImage?.gone()
						binding?.emptyListLabel?.gone()
					}
				}
			}
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	private fun showSubscriptionTypeMenu(v: View) {
		val popUp = context?.showMenu(v, R.menu.subscriptions_menu)

		popUp?.setOnMenuItemClickListener {
			subscriptionType = if (it.itemId == R.id.current_subscriptions) {
				ElloRpc.SubscriptionType.ACTIVE_CHANNELS
			}
			else {
				ElloRpc.SubscriptionType.PAST_CHANNELS
			}

			true
		}
	}

	private fun subscribe(peer: TLObject) {
		val (id, peerType) = getIdAndPeerType(peer) ?: return

		binding?.progressBar?.visible()

		val req = ElloRpc.subscribeRequest(peerId = id, type = peerType)

		connectionsManager.sendRequest(req) { response, error ->
			if (response is TLRPC.TLBizDataRaw) {
				val subscriptionItem = response.readData<ElloRpc.SubscriptionItem>()

				AndroidUtilities.runOnUIThread {
					val context = context ?: return@runOnUIThread

					if (subscriptionItem != null) {
						loadData()
						Toast.makeText(context, R.string.successfully_subscribed, Toast.LENGTH_SHORT).show()
					}
					else {
						val message = error?.text ?: context.getString(R.string.unknown_error)
						Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
					}

					binding?.progressBar?.gone()
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					binding?.progressBar?.gone()
				}
			}
		}
	}

	private fun subscribe(subType: Int) {
		binding?.progressBar?.visible()

		val req = ElloRpc.subscribeChatBotRequest(subType)

		connectionsManager.sendRequest(req) { response, error ->
			if (response is TLRPC.TLBizDataRaw) {
				val subscriptionItem = response.readData<ElloRpc.SubscriptionInfoAiBot>()

				AndroidUtilities.runOnUIThread {
					val context = context ?: return@runOnUIThread

					if (subscriptionItem != null) {
						loadData()
						Toast.makeText(context, R.string.successfully_subscribed, Toast.LENGTH_SHORT).show()
					}
					else {
						val message = error?.text ?: context.getString(R.string.unknown_error)
						Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
					}

					binding?.progressBar?.gone()
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					binding?.progressBar?.gone()
				}
			}
		}
	}

	private fun getIdAndPeerType(peer: TLObject): Pair<Long, Int>? {
		val channel = peer as? TLRPC.Chat
		val user = peer as? User

		if (channel == null && user == null) {
			return null
		}

		val id = channel?.id ?: user?.id ?: return null
		val peerType = if (channel != null) ElloRpc.PEER_TYPE_CHANNEL else ElloRpc.PEER_TYPE_USER

		return id to peerType
	}

	private fun unsubscribe(peer: TLObject) {
		val (id, peerType) = getIdAndPeerType(peer) ?: return

		binding?.progressBar?.visible()

		val req = ElloRpc.unsubscribeRequest(peerId = id, type = peerType)

		connectionsManager.sendRequest(req) { response, error ->
			if (response is TLRPC.TLBizDataRaw) {
				val subscriptionItem = response.readData<ElloRpc.SubscriptionItem>()

				AndroidUtilities.runOnUIThread {
					val context = context ?: return@runOnUIThread

					if (subscriptionItem != null) {
						loadData()
						Toast.makeText(context, R.string.subscription_cancelled, Toast.LENGTH_SHORT).show()
					}
					else {
						val message = error?.text ?: context.getString(R.string.unknown_error)
						Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
					}

					binding?.progressBar?.gone()
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					binding?.progressBar?.gone()
				}
			}
		}
	}

	private fun unsubscribe(subType: Int) {
		binding?.progressBar?.visible()

		val req = ElloRpc.unsubscribePurchaseRequest(subType = subType)

		connectionsManager.sendRequest(req) { response, error ->
			if (response is TLRPC.TLBizDataRaw) {
				val subscriptionItem = response.readData<ElloRpc.SubInfoResponse>()

				AndroidUtilities.runOnUIThread {
					val context = context ?: return@runOnUIThread

					if (subscriptionItem != null) {
						loadData()
						Toast.makeText(context, R.string.subscription_cancelled, Toast.LENGTH_SHORT).show()
					}
					else {
						val message = error?.text ?: context.getString(R.string.unknown_error)
						Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
					}

					binding?.progressBar?.gone()
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					binding?.progressBar?.gone()
				}
			}
		}
	}

	private fun showCancelSubscriptionMenu(v: View, peer: TLObject?, botType: Int, expireAt: Long) {
		val popUp = context?.showMenu(v, R.menu.cancel_subscription_menu)

		popUp?.setOnMenuItemClickListener {
			if (it.itemId == R.id.cancel_subscription) {
				val channel = peer as? TLRPC.Chat
				val user = peer as? User

				if (user?.bot == true) {
					AlertsCreator.createClearOrDeleteDialogAlert(this, user, expireAt) {
						unsubscribe(botType)
					}
				}
				else {
					AlertsCreator.createClearOrDeleteDialogAlert(fragment = this, clear = false, chat = channel, user = user, secret = false, canDeleteHistory = false) {
						if (peer != null) {
							unsubscribe(peer)
						}
					}
				}
			}

			true
		}
	}

	companion object {
		private const val PEER_TYPE_APPLE = 0
		private const val PEER_TYPE_GOOGLE = 1
	}

	private fun showCancelMembershipSubscriptionMenu(v: View, peerType: Int) {
		val popUp = context?.showMenu(v, R.menu.cancel_subscription_menu)

		popUp?.setOnMenuItemClickListener {
			if (peerType == PEER_TYPE_APPLE) {
				showConfirmationDialog(title = R.string.cancel_membership_subscription_title, message = R.string.cancel_subscription_ios_description, buttonText = R.string.OK)
			}
			else {
				showConfirmationDialog(title = R.string.cancel_membership_subscription_title, message = R.string.cancel_google_subscription_description, buttonText = R.string.OK)
			}

			true
		}
	}

	private fun showMembershipSubscribeMenu(v: View, peerType: Int) {
		val popUp = context?.showMenu(v, R.menu.subscribe_menu)

		popUp?.setOnMenuItemClickListener {
			if (peerType == PEER_TYPE_APPLE) {
				showConfirmationDialog(title = R.string.renew_subscription_apple_title, message = R.string.renew_subscription_apple_message, buttonText = R.string.OK)
			}
			else {
				showConfirmationDialog(title = R.string.renew_subscription_google_title, message = R.string.renew_subscription_google_message, buttonText = R.string.OK)
			}

			true
		}
	}

	private fun showConfirmationDialog(context: Context? = this.context, title: Int, message: Int, buttonText: Int) {
		if (context == null) {
			return
		}

		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(title))
		builder.setMessage(context.getString(message))

		builder.setPositiveButton(context.getString(buttonText)) { dialog, _ ->
			dialog.dismiss()
		}

		val dialog = builder.create()
		dialog.show()

		val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

		button?.setTextColor(context.getColor(R.color.brand))
	}

	private fun showSubscribeMenu(v: View, peer: TLObject?, botType: Int) {
		val popUp = context?.showMenu(v, R.menu.subscribe_menu)

		popUp?.setOnMenuItemClickListener {
			if (it.itemId == R.id.subscribe) {
				val context = context ?: return@setOnMenuItemClickListener true
				val user = peer as? User

				val builder = AlertDialog.Builder(context)
				if (user?.bot == true) {
					builder.setMessage(context.getString(R.string.continue_subs_confirm))
				}
				else {
					builder.setMessage(context.getString(R.string.subscribe_confirm))
				}

				builder.setPositiveButton(context.getString(R.string.confirm)) { _, _ ->
					if (user?.bot == true) {
						subscribe(botType)
					}
					else {
						if (peer != null) {
							subscribe(peer)
						}
					}
				}

				builder.setNegativeButton(context.getString(R.string.cancel), null)

				val dialog = builder.create()

				showDialog(dialog)

				val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
				button?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
			}

			true
		}
	}
}
