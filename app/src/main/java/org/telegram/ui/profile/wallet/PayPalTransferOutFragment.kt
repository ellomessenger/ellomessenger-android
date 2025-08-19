/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.PaypalTransferOutFragmentBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.validateEmail
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LoginActivity
import org.telegram.ui.profile.CodeVerificationFragment

class PayPalTransferOutFragment(args: Bundle) : BaseFragment(args) {
	private var binding: PaypalTransferOutFragmentBinding? = null
	private var amount = 0f
	private var walletId = 0L
	private var connecting = false
	private var reqId = 0

	private val email: String?
		get() = binding?.emailField?.text?.toString()?.trim()

	override fun onFragmentCreate(): Boolean {
		val walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID)?.takeIf { it != 0L } ?: return false
		val amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT) ?: return false

		this.amount = amount
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

		binding = PaypalTransferOutFragmentBinding.inflate(LayoutInflater.from(context))



		binding?.connectButton?.setOnClickListener {
			connectToPayPal()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun connectToPayPal() {
		val email = email

		if (email?.validateEmail() != true) {
			Toast.makeText(context, R.string.wrong_email_format, Toast.LENGTH_SHORT).show()
			return
		}

		connecting = true

		actionBar?.backButton?.isEnabled = false

		binding?.paypalContainer?.gone()
		binding?.loadingContainer?.visible()

		if (walletHelper.currentTransferOutPaymentId == null) {
			reqId = walletHelper.createWithdrawPaymentRequest(walletId = walletId, amount = amount, paymentId = null) { paymentId, _, _, _, _, _, _, error ->
				val context = context ?: return@createWithdrawPaymentRequest

				if (error != null) {
					Toast.makeText(context, error.text ?: context.getString(R.string.UnknownError), Toast.LENGTH_SHORT).show()

					actionBar?.backButton?.isEnabled = true

					binding?.paypalContainer?.visible()
					binding?.loadingContainer?.gone()

					connecting = false

					return@createWithdrawPaymentRequest
				}

				walletHelper.currentTransferOutPaymentId = paymentId

				if (!walletHelper.currentTransferOutPaymentId.isNullOrEmpty()) {
					requestVerificationCode()
				}
				else {
					actionBar?.backButton?.isEnabled = true
					binding?.paypalContainer?.visible()
					binding?.loadingContainer?.gone()
					connecting = false
					Toast.makeText(context, context.getString(R.string.failed_to_create_transaction), Toast.LENGTH_SHORT).show()
				}
			}
		}
		else {
			requestVerificationCode()
		}
	}

	private fun requestVerificationCode() {
		val email = email

		if (email?.validateEmail() != true) {
			Toast.makeText(context, R.string.wrong_email_format, Toast.LENGTH_SHORT).show()
			return
		}

		val req = ElloRpc.withdrawSendApproveCode()

		reqId = connectionsManager.sendRequest(req) { response, error ->
			val context = context ?: return@sendRequest

			if (response is TLRPC.TLBizDataRaw) {
				val data = response.readData<ElloRpc.WithdrawSendApproveCodeResponse>()

				AndroidUtilities.runOnUIThread {
					if (data?.status == true) {
						goToVerification(email, data.confirmationExpire)
					}
					else {
						actionBar?.backButton?.isEnabled = true
						binding?.paypalContainer?.visible()
						binding?.loadingContainer?.gone()
						connecting = false

						val message = data?.message

						if (message.isNullOrEmpty()) {
							Toast.makeText(context, context.getString(R.string.failed_to_send_verification_code), Toast.LENGTH_SHORT).show()
						}
						else {
							Toast.makeText(context, String.format("%s: %s", context.getString(R.string.failed_to_send_verification_code), message), Toast.LENGTH_SHORT).show()
						}
					}
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					actionBar?.backButton?.isEnabled = true
					binding?.paypalContainer?.visible()
					binding?.loadingContainer?.gone()
					connecting = false

					val message = error?.text

					if (message.isNullOrEmpty()) {
						Toast.makeText(context, context.getString(R.string.failed_to_send_verification_code), Toast.LENGTH_SHORT).show()
					}
					else {
						Toast.makeText(context, String.format("%s: %s", context.getString(R.string.failed_to_send_verification_code), message), Toast.LENGTH_SHORT).show()
					}
				}
			}
		}
	}

	private fun goToVerification(email: String, codeExpiration: Long) {
		val args = Bundle()
		args.putFloat(WalletFragment.ARG_AMOUNT, amount)
		args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
		args.putString(CodeVerificationFragment.ARG_EMAIL, email)
		args.putString(CodeVerificationFragment.ARG_VERIFICATION_MODE, LoginActivity.VerificationMode.TRANSFER_OUT_PAYPAL.name)
		args.putLong(CodeVerificationFragment.ARG_EXPIRATION, codeExpiration)

		presentFragment(CodeVerificationFragment(args))

		actionBar?.backButton?.isEnabled = true
		binding?.paypalContainer?.visible()
		binding?.loadingContainer?.gone()
	}

	override fun canBeginSlide(): Boolean {
		return !connecting
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		binding = null

		if (reqId != 0) {
			connectionsManager.cancelRequest(reqId, false)
			reqId = 0
		}

		connecting = false
	}
}
