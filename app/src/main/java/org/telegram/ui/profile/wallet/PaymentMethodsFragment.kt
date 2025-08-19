/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.profile.wallet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.PaymentMethodItemBinding
import org.telegram.messenger.databinding.PaymentMethodsFragmentBinding
import org.telegram.messenger.utils.fromJson
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readString
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.ExtendedGridLayoutManager

class PaymentMethodsFragment(args: Bundle) : BaseFragment(args) {
	private var binding: PaymentMethodsFragmentBinding? = null
	private var walletId = 0L
	private var channelId = 0L
	private var amount: Double = 0.0
	private var isTopUp = false
	private var showElloCard = false
	private var showPaypal = false
	private var showBankTransfer = false
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private var paypalInfo: ElloRpc.CommissionInfo? = null
	private var bankInfo: ElloRpc.CommissionInfo? = null

	override fun onFragmentCreate(): Boolean {
		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID, 0L) ?: return false
		isTopUp = arguments?.getBoolean(WalletFragment.ARG_IS_TOPUP, false) ?: false
		showElloCard = arguments?.getBoolean(ARG_SHOW_ELLO_CARD, false) ?: false
		channelId = arguments?.getLong(WalletFragment.ARG_CHANNEL_ID, 0L) ?: 0L
		amount = arguments?.getDouble(WalletFragment.ARG_AMOUNT, 0.0) ?: 0.0

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		if (isTopUp) {
			actionBar?.setTitle(context.getString(R.string.deposit))
		}
		else {
			actionBar?.setTitle(context.getString(R.string.transfer_out_methods))
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = PaymentMethodsFragmentBinding.inflate(LayoutInflater.from(context))

		val spacing = AndroidUtilities.dp(12f)

		binding?.recyclerView?.apply {
			setPadding(spacing / 2)
			clipToPadding = false

			layoutManager = ExtendedGridLayoutManager(context, 3)

			addItemDecoration(GridSpaceItemDecoration(spacing))
		}

		binding?.recyclerView?.adapter = object : RecyclerView.Adapter<PaymentMethodViewHolder>() {
			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentMethodViewHolder {
				val binding = PaymentMethodItemBinding.inflate(LayoutInflater.from(context), parent, false)
				return PaymentMethodViewHolder(binding)
			}

			override fun getItemCount(): Int {
				var total = 0

				if (showElloCard) {
					total += 1
				}

				if (showPaypal) {
					total += 1
				}

				if (showBankTransfer) {
					total += 1
				}

				return total
			}

			override fun getItemViewType(position: Int): Int {
				@Suppress("NAME_SHADOWING") var position = position

				if (showElloCard) {
					if (position == 0) {
						return VIEW_TYPE_ELLO_CARD
					}

					position -= 1
				}

				if (showPaypal) {
					if (position == 0) {
						return VIEW_TYPE_PAYPAL
					}

					position -= 1
				}

				if (showBankTransfer) {
					if (position == 0) {
						return VIEW_TYPE_BANK_TRANSFER
					}

					position -= 1
				}

				return VIEW_TYPE_BANK_TRANSFER
			}

			override fun onBindViewHolder(holder: PaymentMethodViewHolder, position: Int) {
				when (holder.itemViewType) {
					VIEW_TYPE_ELLO_CARD -> {
						holder.binding.imageView.setImageResource(R.drawable.my_balance)
						holder.binding.imageView.colorFilter = PorterDuffColorFilter(context.getColor(R.color.text), PorterDuff.Mode.SRC_IN)
						holder.binding.imageView.contentDescription = context.getString(R.string.my_balance)
						holder.binding.label.setText(R.string.my_balance)

						holder.binding.root.setOnClickListener {
							val args = Bundle()
							args.putInt(WalletFragment.ARG_MODE, WalletFragment.MY_BALANCE)
							args.putBoolean(WalletFragment.ARG_IS_TOPUP, isTopUp)
							args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
							args.putLong(WalletFragment.ARG_CHANNEL_ID, channelId)
							args.putDouble(WalletFragment.ARG_AMOUNT, amount)

							presentFragment(TopupSumFragment(args))
						}
					}

					VIEW_TYPE_PAYPAL -> {
						holder.binding.imageView.setImageResource(R.drawable.paypal)
						holder.binding.imageView.colorFilter = null
						holder.binding.imageView.contentDescription = context.getString(R.string.cont_desc_paypal_logo)
						holder.binding.label.setText(R.string.paypal)

						holder.binding.root.setOnClickListener {
							val args = Bundle()
							args.putInt(WalletFragment.ARG_MODE, WalletFragment.PAYPAL)
							args.putBoolean(WalletFragment.ARG_IS_TOPUP, isTopUp)
							args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
							args.putLong(WalletFragment.ARG_CHANNEL_ID, channelId)
							args.putDouble(WalletFragment.ARG_AMOUNT, amount)

							paypalInfo?.let {
								args.putSerializable(TopupSumFragment.ARG_COMMISSION_INFO, it)
							}

							presentFragment(TopupSumFragment(args))
						}
					}

					VIEW_TYPE_BANK_TRANSFER -> {
						if (!isTopUp) {
							holder.binding.imageView.setImageResource(R.drawable.bank_transfer)
							holder.binding.imageView.colorFilter = PorterDuffColorFilter(context.getColor(R.color.text), PorterDuff.Mode.SRC_IN)
							holder.binding.imageView.contentDescription = context.getString(R.string.bank_transfer)
							holder.binding.label.setText(R.string.bank_transfer)
						}
						else {
							holder.binding.imageView.setImageResource(R.drawable.ic_stripe)
							holder.binding.label.setText(R.string.stripe)
						}

						holder.binding.root.setOnClickListener {
							val args = Bundle()
							args.putInt(WalletFragment.ARG_MODE, WalletFragment.CARD)
							args.putBoolean(WalletFragment.ARG_IS_TOPUP, isTopUp)
							args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
							args.putLong(WalletFragment.ARG_CHANNEL_ID, channelId)
							args.putDouble(WalletFragment.ARG_AMOUNT, amount)

							bankInfo?.let {
								args.putSerializable(TopupSumFragment.ARG_COMMISSION_INFO, it)
							}

							presentFragment(TopupSumFragment(args))
						}
					}
				}
			}
		}

		fragmentView = binding?.root

		return binding?.root
	}

	override fun onResume() {
		super.onResume()
		loadPaymentMethods()
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun loadPaymentMethods() {
		ioScope.launch {
			val req = ElloRpc.getCommissionInfo()
			val response = connectionsManager.performRequest(req)

			if (response is TLRPC.TLBizDataRaw) {
				val commissionInfo = response.readString()?.fromJson<List<Map<String, Any>>>()?.map {
					ElloRpc.CommissionInfo(type = it["type"] as String, value = it["value"] as Double, onOffDeposit = it["on_off_deposit"] as? Boolean, minDeposit = it["min_deposit"] as? Double, maxDeposit = it["max_deposit"] as? Double, onOffWithdrawals = it["on_off_withdrawals"] as? Boolean, minWithdrawals = it["min_withdrawals"] as? Double, maxWithdrawals = it["max_withdrawals"] as? Double)
				}

				commissionInfo?.forEach {
					when (it.type) {
						"paypal" -> {
							showPaypal = if (isTopUp) {
								it.onOffDeposit == false
							}
							else {
								it.onOffWithdrawals == false
							}

							paypalInfo = it
						}

						"stripe" -> {
							showBankTransfer = if (isTopUp) {
								it.onOffDeposit == false
							}
							else {
								it.onOffWithdrawals == false
							}

							bankInfo = it
						}

						"bank" -> {
							// unused
						}
					}
				}

				mainScope.launch {
					binding?.recyclerView?.adapter?.notifyDataSetChanged()
				}
			}
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}

		binding = null
	}

	private class PaymentMethodViewHolder(val binding: PaymentMethodItemBinding) : RecyclerView.ViewHolder(binding.root)

	companion object {
		const val ARG_SHOW_ELLO_CARD = "show_ello_card"
		private const val VIEW_TYPE_ELLO_CARD = 0
		private const val VIEW_TYPE_PAYPAL = 1
		private const val VIEW_TYPE_BANK_TRANSFER = 2
	}
}
