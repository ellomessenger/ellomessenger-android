/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.PaypalTopupFragmentBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.WalletHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class PayPalTopupFragment(args: Bundle) : BaseFragment(args) {
	private var binding: PaypalTopupFragmentBinding? = null
	private var amount = 0f
	private var walletId = 0L
	private var currency = ""
	private var connecting = false
	private var reqId = 0

	override fun onFragmentCreate(): Boolean {
		val walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID)?.takeIf { it != 0L } ?: return false
		val amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT) ?: return false
		val currency = arguments?.getString(WalletFragment.ARG_CURRENCY)?.lowercase() ?: WalletHelper.DEFAULT_CURRENCY

		this.amount = amount
		this.currency = currency
		this.walletId = walletId

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.paypal))
		actionBar?.castShadows = false

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = PaypalTopupFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.connectButton?.setOnClickListener {
			connectToPayPal()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun connectToPayPal() {
		connecting = true

		actionBar?.backButton?.isEnabled = false

		binding?.paypalContainer?.gone()
		binding?.loadingContainer?.visible()

		reqId = walletHelper.getPayPalPaymentLink(assetId = WalletHelper.DEFAULT_ASSET_ID, walletId = walletId, currency = currency, message = "Topup", amount = amount) { link, paymentId, error ->
			AndroidUtilities.runOnUIThread {
				connecting = false

				actionBar?.backButton?.isEnabled = true

				if (!link.isNullOrEmpty() && paymentId != null) {
					val args = Bundle()
					args.putInt(WalletFragment.ARG_MODE, WalletFragment.PAYPAL)
					args.putFloat(WalletFragment.ARG_AMOUNT, amount)
					args.putString(WalletFragment.ARG_CURRENCY, currency)
					args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
					args.putString(WalletFragment.ARG_PAYPAL_LINK, link)
					args.putLong(WalletFragment.ARG_WALLET_PAYMENT_ID, paymentId)

					presentFragment(PaymentProcessingFragment(args))
				}
				else if (error != null) {
					val message = error.text ?: context?.getString(R.string.UnknownError)
					Toast.makeText(parentActivity, message, Toast.LENGTH_SHORT).show()
				}

				reqId = 0

				binding?.paypalContainer?.visible()
				binding?.loadingContainer?.gone()
			}
		}
	}

	override fun canBeginSlide(): Boolean {
		return !connecting
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		binding = null

		if (reqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, false)
			reqId = 0
		}

		connecting = false
	}
}
