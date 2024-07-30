/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
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
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentCurrentSubscriptionsBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.showMenu
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.profile.utils.CustomDividerItemDecoration

class CurrentSubscriptionsFragment : BaseFragment() {
	private var binding: FragmentCurrentSubscriptionsBinding? = null
	private var adapter: CurrentSubscriptionsAdapter? = null
	private var subscriptions: List<ElloRpc.SubscriptionItem>? = null
	private var emptyImage: RLottieImageView? = null

	private var subscriptionType = SubscriptionType.ACTIVE_CHANNELS
		set(value) {
			field = value

			binding?.menuField?.setText(when (value) {
				SubscriptionType.ACTIVE_CHANNELS -> context?.getString(R.string.my_current_subscriptions)
				SubscriptionType.CANCELLED_CHANNELS -> context?.getString(R.string.my_previous_subscriptions)
				SubscriptionType.ALL_CHANNELS -> context?.getString(R.string.my_all_subscriptions)
				SubscriptionType.ALL -> context?.getString(R.string.my_all_subscriptions)
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

		binding?.menuField?.setOnClickListener {
			showSubscriptionTypeMenu(it)
		}

		binding?.menu?.setEndIconOnClickListener {
			showSubscriptionTypeMenu(binding!!.menuField)
		}

		adapter = CurrentSubscriptionsAdapter {
			when (it) {
				is CurrentSubscriptionsAdapter.SubscriptionItemAction.Cancel -> {
					showCancelSubscriptionMenu(v = it.view, peer = it.peer)
				}

				is CurrentSubscriptionsAdapter.SubscriptionItemAction.Subscribe -> {
					showSubscribeMenu(it.view, peer = it.peer)
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
		val request = ElloRpc.getSubscriptionsRequest(type = subscriptionType.value)

		connectionsManager.sendRequest(request) { response, _ ->
			if (response is TLRPC.TL_biz_dataRaw) {
				val subscriptions = response.readData<ElloRpc.Subscriptions>()

				this.subscriptions = subscriptions?.items

				AndroidUtilities.runOnUIThread {
					adapter?.submitList(subscriptions?.items)

					if (this.subscriptions.isNullOrEmpty()) {
						when (subscriptionType) {
							SubscriptionType.ACTIVE_CHANNELS -> {
								binding?.emptyListLabel?.setText(R.string.no_current_subscriptions)
							}

							SubscriptionType.CANCELLED_CHANNELS -> {
								binding?.emptyListLabel?.setText(R.string.no_past_subscriptions)
							}

							SubscriptionType.ALL_CHANNELS -> {
								binding?.emptyListLabel?.setText(R.string.no_current_subscriptions)
							}

							SubscriptionType.ALL -> {
								binding?.emptyListLabel?.setText(R.string.no_current_subscriptions)
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
				SubscriptionType.ACTIVE_CHANNELS
			}
			else {
				SubscriptionType.CANCELLED_CHANNELS
			}

			true
		}
	}

	private fun subscribe(peer: TLObject) {
		val (id, peerType) = getIdAndPeerType(peer) ?: return

		binding?.progressBar?.visible()

		val req = ElloRpc.subscribeRequest(peerId = id, type = peerType)

		connectionsManager.sendRequest(req) { response, error ->
			if (response is TLRPC.TL_biz_dataRaw) {
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
			if (response is TLRPC.TL_biz_dataRaw) {
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

	private fun showCancelSubscriptionMenu(v: View, peer: TLObject) {
		val popUp = context?.showMenu(v, R.menu.cancel_subscription_menu)

		popUp?.setOnMenuItemClickListener {
			val context = context ?: return@setOnMenuItemClickListener true

			if (it.itemId == R.id.cancel_subscription) {
				val builder = AlertDialog.Builder(context)
				builder.setMessage(context.getString(R.string.cancel_subs_confirm))

				builder.setPositiveButton(context.getString(R.string.confirm)) { _, _ ->
					unsubscribe(peer)
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

	private fun showSubscribeMenu(v: View, peer: TLObject) {
		val popUp = context?.showMenu(v, R.menu.subscribe_menu)

		popUp?.setOnMenuItemClickListener {
			if (it.itemId == R.id.subscribe) {
				val context = context ?: return@setOnMenuItemClickListener true

				val builder = AlertDialog.Builder(context)
				builder.setMessage(context.getString(R.string.subscribe_confirm))

				builder.setPositiveButton(context.getString(R.string.confirm)) { _, _ ->
					subscribe(peer)
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
