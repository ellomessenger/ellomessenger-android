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
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.databinding.BankPaymentProcessingFragmentBinding
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.WalletHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import java.util.Timer
import kotlin.concurrent.timer

class PaymentProcessingFragment(args: Bundle) : BaseFragment(args) {
	private var binding: BankPaymentProcessingFragmentBinding? = null
	private var mode = WalletFragment.CARD
	private var amount = 0f
	private var walletId = 0L
	private var currency = ""

	//	private var cardNumber = ""
//	private var cardExpiryMonth = 0
//	private var cardExpiryYear = 0
//	private var cardCvc = 0
	private var paypalLink: String? = null
	private var paymentId: Long? = null
	private var paymentInProgress = false
	private var timer: Timer? = null
	private var isTopUp = true
	private var verificationCode: String? = null
	private var paypalEmail: String? = null
	private var bankWithdrawRequisitesId: Long? = null
	private var reqId = 0

	override fun onFragmentCreate(): Boolean {
		mode = arguments?.getInt(WalletFragment.ARG_MODE) ?: return false
		amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT) ?: return false
		currency = arguments?.getString(WalletFragment.ARG_CURRENCY)?.lowercase() ?: WalletHelper.DEFAULT_CURRENCY
		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID)?.takeIf { it != 0L } ?: return false
		isTopUp = arguments?.getBoolean(WalletFragment.ARG_IS_TOPUP, true) ?: true

		when (mode) {
			WalletFragment.MY_BALANCE -> {
				if (!isTopUp) {
					walletId = walletHelper.wallet?.id ?: return false
				}
			}

			WalletFragment.BANK -> {
				if (!isTopUp) {
					verificationCode = arguments?.getString(WalletFragment.ARG_VERIFICATION_CODE) ?: return false
					bankWithdrawRequisitesId = arguments?.getLong(WalletFragment.ARG_BANK_WITHDRAW_REQUISITES_ID)?.takeIf { it != 0L } ?: return false
				}
			}

			WalletFragment.CARD -> {
//				cardNumber = arguments?.getString(WalletFragment.ARG_CARD_NUMBER) ?: return false
//				cardExpiryMonth = arguments?.getString(WalletFragment.ARG_CARD_EXPIRY_MONTH)?.toIntOrNull() ?: return false
//				cardExpiryYear = arguments?.getString(WalletFragment.ARG_CARD_EXPIRY_YEAR)?.toIntOrNull() ?: return false
//				cardCvc = arguments?.getString(WalletFragment.ARG_CARD_CVC)?.toIntOrNull() ?: return false
			}

			WalletFragment.PAYPAL -> {
				if (isTopUp) {
					paypalLink = arguments?.getString(WalletFragment.ARG_PAYPAL_LINK) ?: return false
					paymentId = arguments?.getLong(WalletFragment.ARG_WALLET_PAYMENT_ID)?.takeIf { it != 0L } ?: return false
				}
				else {
					verificationCode = arguments?.getString(WalletFragment.ARG_VERIFICATION_CODE) ?: return false
					paypalEmail = arguments?.getString(WalletFragment.ARG_PAYPAL_EMAIL) ?: return false
				}
			}
		}

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.castShadows = false
		actionBar?.setTitle(null)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = BankPaymentProcessingFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.backButton?.setOnClickListener {
			finishFragment()
		}

		fragmentView = binding?.root

		when (mode) {
			WalletFragment.CARD -> {
				if (isTopUp) {
					launchCardPayment()
				}
			}

			WalletFragment.PAYPAL -> {
				if (isTopUp) {
					launchPaypalPayment()
				}
				else {
					launchTransferOutPayment()
				}
			}

			WalletFragment.BANK -> {
				if (!isTopUp) {
					launchTransferOutPayment()
				}
			}

			WalletFragment.MY_BALANCE -> {
				if (!isTopUp) {
					launchMainWalletTransferOutPayment()
				}
			}
		}

		return binding?.root
	}

	private fun launchMainWalletTransferOutPayment() {
		val context = context ?: return
		val earningsWalletId = walletHelper.earningsWallet?.id

		if (earningsWalletId == null || earningsWalletId == 0L) {
			val error = TLRPC.TL_error()
			error.text = context.getString(R.string.no_earnings_wallet_found)
			processError(error)
			return
		}

		paymentInProgress = true

		binding?.image?.setImageResource(R.drawable.tick_circle_green)
		binding?.mainLabel?.setText(R.string.request_has_been_sent)
		binding?.secondaryLabel?.setText(R.string.in_progress)
		binding?.secondaryLabel?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null))

		val req = ElloRpc.transferBetweenWalletsRequest(from = earningsWalletId, to = walletId, amount = amount, currency = currency)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.MyBalanceTransferResponse>()

					if (data != null) {
						processWithdrawSuccess(data.amount)
					}
					else {
						processError()
					}
				}
				else {
					processError(error)
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun launchTransferOutPayment() {
		val context = context ?: return

		paymentInProgress = true

		binding?.image?.setImageResource(R.drawable.tick_circle_green)
		binding?.mainLabel?.setText(R.string.request_has_been_sent)
		binding?.secondaryLabel?.setText(R.string.in_progress)
		binding?.secondaryLabel?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null))

		val transferOutPaymentId = walletHelper.currentTransferOutPaymentId
		val verificationCode = verificationCode ?: return

		if (transferOutPaymentId.isNullOrEmpty()) {
			reqId = walletHelper.createWithdrawPaymentRequest(walletId = walletId, amount = amount, paymentId = null, paypalEmail = paypalEmail, bankWithdrawRequisitesId = bankWithdrawRequisitesId) { paymentId, _, _, _, _, _, _, error ->
				if (error != null) {
					processError(error)
					return@createWithdrawPaymentRequest
				}

				walletHelper.currentTransferOutPaymentId = paymentId

				if (!paymentId.isNullOrEmpty()) {
					createWithdrawApproveRequest(paymentId, verificationCode)
				}
				else {
					processError()
				}
			}
		}
		else {
			createWithdrawApproveRequest(transferOutPaymentId, verificationCode)
		}
	}

	private fun createWithdrawApproveRequest(transferOutPaymentId: String, verificationCode: String) {
		val req = ElloRpc.getWithdrawApprovePayment(walletId = walletId, paymentId = transferOutPaymentId, approveCode = verificationCode, paypalEmail = paypalEmail, bankWithdrawRequisitesId = bankWithdrawRequisitesId)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.WithdrawApprovePayment>()

					if (data != null) {
						processWithdrawSuccess(data.amount)
					}
					else {
						processError()
					}
				}
				else {
					processError(error)
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun launchPaypalPayment() {
		val context = context ?: return

		paymentInProgress = true

		binding?.image?.setImageResource(R.drawable.tick_circle_green)
		binding?.mainLabel?.setText(R.string.request_has_been_sent)
		binding?.secondaryLabel?.setText(R.string.in_progress)
		binding?.secondaryLabel?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null))

		AndroidUtilities.runOnUIThread({
			Browser.openUrl(parentActivity, paypalLink)
		}, 1_000)
	}

	private fun launchCardPayment() {
		val context = context ?: return

		paymentInProgress = true

		binding?.image?.setImageResource(R.drawable.tick_circle_green)
		binding?.mainLabel?.setText(R.string.request_has_been_sent)
		binding?.secondaryLabel?.setText(R.string.in_progress)
		binding?.secondaryLabel?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.disabled_text, null))

		// val req = ElloRpc.stripePaymentRequest(assetId = WalletHelper.DEFAULT_ASSET_ID, walletId = walletId, currency = currency, message = null, cardNumber = cardNumber, expirationMonth = cardExpiryMonth, expirationYear = cardExpiryYear, cvc = cardCvc, amount = amount)

		val req = ElloRpc.stripePaymentRequest(assetId = WalletHelper.DEFAULT_ASSET_ID, walletId = walletId, currency = currency, message = null, amount = null, coin = amount)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.StripePaymentResponse>()

					if (data != null) {
						paymentId = data.paymentId
						paypalLink = data.link

						launchCheckTimer()
						launchPaypalPayment()
					}
					else {
						processError()
					}
				}
				else {
					processError(error)
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun processWithdrawSuccess(amount: Float) {
		val context = context ?: return

		binding?.purchaseWarning?.gone()
		binding?.image?.setImageResource(R.drawable.panda_payment_success)
		binding?.mainLabel?.setText(R.string.success_excl)
		binding?.secondaryLabel?.text = context.getString(R.string.you_withdrew_format, amount).fillElloCoinLogos(size = 16f)
		binding?.secondaryLabel?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		binding?.backButton?.visible()

		parentLayout?.let {
			it.postDelayed({
				it.showFragment(1)
				it.removeFragmentsUpTo(1)
			}, 2_500)
		}
	}

	private fun processTopupSuccess(amount: Float) {
		val context = context ?: return

		binding?.purchaseWarning?.gone()
		binding?.image?.setImageResource(R.drawable.panda_payment_success)
		binding?.mainLabel?.setText(R.string.success_excl)
		binding?.secondaryLabel?.text = context.getString(R.string.you_purchased_format, amount).fillElloCoinLogos(size = 16f)
		binding?.secondaryLabel?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		binding?.backButton?.visible()

		parentLayout?.let {
			it.postDelayed({
				it.showFragment(1)
				it.removeFragmentsUpTo(1)
			}, 1_500)
		}
	}

	private fun processError(error: TLRPC.TL_error? = null) {
		val context = context ?: return

		walletHelper.currentTransferOutPaymentId = null

		paymentInProgress = false

		binding?.image?.setImageResource(R.drawable.panda_payment_error)
		binding?.mainLabel?.setText(R.string.error_excl)
		binding?.secondaryLabel?.text = error?.text?.trim() ?: context.getString(R.string.something_went_wrong)
		binding?.secondaryLabel?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		binding?.backButton?.setText(R.string.ok)
		binding?.backButton?.visible()

		actionBar?.backButton?.isEnabled = true
	}

	override fun onPause() {
		super.onPause()

		timer?.cancel()
		timer = null
	}

	override fun onResume() {
		super.onResume()
		launchCheckTimer()
	}

	private fun launchCheckTimer() {
		timer?.cancel()

		val paymentId = paymentId ?: return

		// this will be executed only for topup flow
		timer = timer(period = 3_000L) {
			val req = ElloRpc.getWalletPaymentById(walletId, paymentId)

			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					if (response is TLRPC.TL_biz_dataRaw) {
						val data = response.readData<ElloRpc.WalletPaymentByIdResponse>()
						val payment = data?.payment

						if (payment != null) {
							if (payment.status == "completed") {
								processTopupSuccess(amount)
							}
						}
						else {
							processError()
						}
					}
					else {
						processError(error)
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
	}

	override fun finishFragment() {
		super.finishFragment()
		parentLayout?.let {
			it.showFragment(1)
			it.removeFragmentsUpTo(1)
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		timer?.cancel()
		timer = null

		binding = null
	}
}
