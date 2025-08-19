/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentTransferOutResultBinding
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.CountriesDataSource
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.WalletHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LoginActivity
import org.telegram.ui.profile.CodeVerificationFragment
import java.util.Locale

class TransferOutResultFragment(args: Bundle) : BaseFragment(args) {
	private var binding: FragmentTransferOutResultBinding? = null
	private var requisite: ElloRpc.BankRequisite? = null
	private var amount: Float = 0f
	private var selectedRequisitesId: Long? = 0L
	private var walletId: Long = 0L
	private var connecting = false
	private var reqId = 0

	override fun onFragmentCreate(): Boolean {
		requisite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			arguments?.getSerializable(EditBankRequisitesFragment.ARG_REQUISITE, ElloRpc.BankRequisite::class.java)
		}
		else {
			@Suppress("DEPRECATION") arguments?.getSerializable(EditBankRequisitesFragment.ARG_REQUISITE) as? ElloRpc.BankRequisite
		}

		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID, 0L) ?: return false
		amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT, 0f) ?: return false

		selectedRequisitesId = requisite?.requisitesId

		return true
	}

	override fun createView(context: Context): View? {
		initActionBar(context)
		initViewBinding(context)

		initPanels()
		initListeners()

		return binding?.root
	}

	private fun initActionBar(context: Context) {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.withdrawal))
		actionBar?.castShadows = true

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})
	}

	private fun initViewBinding(context: Context) {
		binding = FragmentTransferOutResultBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun initPanels() {
		binding?.firstName?.text = requisite?.personInfo?.firstName
		binding?.lastName?.text = requisite?.personInfo?.lastName
		binding?.phoneNumber?.text = requisite?.personInfo?.phoneNumber
		binding?.email?.text = requisite?.personInfo?.email

		val address = "${requisite?.addressInfo?.city} St. ${requisite?.addressInfo?.street}, ${requisite?.addressInfo?.state} ${requisite?.addressInfo?.postalCode}"

		binding?.yourAddress?.text = address

		loadCountries()

		binding?.amount?.text = String.format(Locale.getDefault(), "%.2f", amount)
	}

	private fun loadCountries() {
		CountriesDataSource.instance.loadCountries { countries, error ->
			if (error == null && countries != null) {

				countries.find { country ->
					country.code?.lowercase() == requisite?.bankInfo?.country?.lowercase()
				}?.let { country ->
					if (country.name == "USA" || country.name == "United States") {
						binding?.usaBankInfoPanel?.visible()
						binding?.routingRecipientNumbersPanel?.visible()

						binding?.usaBankCountry?.text = country.name

						binding?.usaBankName?.text = requisite?.bankInfo?.name

						val bankAddress = "${requisite?.bankInfo?.city} St. ${requisite?.bankInfo?.street}, ${requisite?.bankInfo?.state}"
						binding?.bankAddress?.text = bankAddress

						binding?.routingNumber?.text = requisite?.bankInfo?.routingNumber
						binding?.recipientAccountNumber?.text = requisite?.bankInfo?.recipientAccountNumber
					}
					else {
						binding?.bankInfoPanel?.visible()
						binding?.bankNamePanel?.visible()
						binding?.swiftIbanPanel?.visible()

						binding?.bankCountry?.text = country.name

						binding?.recipientType?.text = requisite?.recipientType

						if (binding?.recipientType?.text.toString().lowercase() == BUSINESS) {
							binding?.titleIndividualIdentificationNumber?.visible()
							binding?.individualIdentificationNumber?.visible()

							binding?.individualIdentificationNumber?.text = requisite?.businessIdNumber
						}

						binding?.currency?.text = (requisite?.currency ?: WalletHelper.DEFAULT_CURRENCY).uppercase()

						binding?.bankName?.text = requisite?.bankInfo?.name

						binding?.swift?.text = requisite?.bankInfo?.swift
						binding?.iban?.text = requisite?.bankInfo?.recipientAccountNumber
					}
				}

			}
			else {
				Toast.makeText(context, R.string.failed_to_load_countries, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun initListeners() {
		binding?.nextButton?.setOnClickListener {
			transferOutToBank()
		}

		binding?.backButton?.setOnClickListener {
			finishFragment()
		}
	}

	private fun transferOutToBank() {
		if (selectedRequisitesId == 0L) {
			return
		}

		connecting = true

		actionBar?.backButton?.isEnabled = false

		if (walletHelper.currentTransferOutPaymentId == null) {
			reqId = walletHelper.createWithdrawPaymentRequest(walletId = walletId, amount = amount, paymentId = null) { paymentId, _, _, _, _, _, _, error ->
				val context = context ?: return@createWithdrawPaymentRequest

				if (error != null) {
					Toast.makeText(context, error.text ?: context.getString(R.string.UnknownError), Toast.LENGTH_SHORT).show()

					actionBar?.backButton?.isEnabled = true

					connecting = false

					return@createWithdrawPaymentRequest
				}

				walletHelper.currentTransferOutPaymentId = paymentId

				if (!walletHelper.currentTransferOutPaymentId.isNullOrEmpty()) {
					requestVerificationCode()
				}
				else {
					actionBar?.backButton?.isEnabled = true
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
		val req = ElloRpc.withdrawSendApproveCode()

		reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
			val context = context ?: return@sendRequest

			if (response is TLRPC.TLBizDataRaw) {
				val data = response.readData<ElloRpc.WithdrawSendApproveCodeResponse>()

				AndroidUtilities.runOnUIThread {
					connecting = false

					if (data?.status == true) {
						goToVerification(data.confirmationExpire)
					}
					else {
						actionBar?.backButton?.isEnabled = true

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

	private fun goToVerification(codeExpiration: Long) {
		val args = Bundle()
		args.putFloat(WalletFragment.ARG_AMOUNT, amount)
		args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
		args.putString(CodeVerificationFragment.ARG_VERIFICATION_MODE, LoginActivity.VerificationMode.TRANSFER_OUT_BANK.name)
		args.putLong(CodeVerificationFragment.ARG_EXPIRATION, codeExpiration)
		selectedRequisitesId?.let { args.putLong(WalletFragment.ARG_BANK_WITHDRAW_REQUISITES_ID, it) }

		presentFragment(CodeVerificationFragment(args))

		actionBar?.backButton?.isEnabled = true
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

	companion object {
		private const val BUSINESS = "business"
	}
}
