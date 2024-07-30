/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.TopupSumFragmentBinding
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.WalletHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import kotlin.math.max
import kotlin.math.min

class TopupSumFragment(args: Bundle) : BaseFragment(args), WalletHelper.OnWalletChangedListener {
	private var binding: TopupSumFragmentBinding? = null
	private var walletId = 0L
	private var mode = WalletFragment.CARD
	private var isTopUp = true
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	private var feeJob: Job? = null
	private var commissionInfo: ElloRpc.CommissionInfo? = null

	private val minWithdrawalAmount: Float
		get() = commissionInfo?.minWithdrawals?.toFloat() ?: WalletHelper.minTopupAmount

	private val minDepositAmount: Float
		get() = commissionInfo?.minDeposit?.toFloat() ?: WalletHelper.minTopupAmount

	private val walletAmount: Float
		get() = walletHelper.findWallet(walletId)?.amount ?: 0f

	override fun onFragmentCreate(): Boolean {
		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID)?.takeIf { it != 0L } ?: return false
		mode = arguments?.getInt(WalletFragment.ARG_MODE) ?: WalletFragment.CARD
		isTopUp = arguments?.getBoolean(WalletFragment.ARG_IS_TOPUP, true) ?: true

		if (mode != WalletFragment.MY_BALANCE) {
			commissionInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				arguments?.getSerializable(ARG_COMMISSION_INFO, ElloRpc.CommissionInfo::class.java)
			}
			else {
				@Suppress("DEPRECATION") arguments?.getSerializable(ARG_COMMISSION_INFO) as? ElloRpc.CommissionInfo
			} ?: return false
		}

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		if (isTopUp) {
			actionBar?.setTitle(context.getString(R.string.deposit))
		}
		else {
			actionBar?.setTitle(context.getString(R.string.withdrawal))
		}

		actionBar?.castShadows = false

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = TopupSumFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.commissionInfoLabel?.gone()

		binding?.cardImage?.apply {
			when (mode) {
				WalletFragment.PAYPAL -> {
					colorFilter = null
					setImageResource(R.drawable.paypal)
				}

				WalletFragment.CARD, WalletFragment.BANK -> {
					if (isTopUp) {
						colorFilter = null
						setImageResource(R.drawable.stripe)
					}
					else {
						colorFilter = PorterDuffColorFilter(context.getColor(R.color.text), PorterDuff.Mode.SRC_IN)
						setImageResource(R.drawable.bank_transfer)
					}

//					binding?.apprSumLabel?.text = context.getString(R.string.you_will_pay, binding?.amountField?.toString()?.toFloatOrNull() ?: 0f)
//					binding?.apprSumLabel?.visible()
				}

				WalletFragment.MY_BALANCE -> {
					binding?.cardBackground?.setContentPadding(0, 0, 0, 0)
					setImageResource(R.drawable.ello_card_topup_small)
					colorFilter = null
				}
			}
		}

		binding?.amountField?.filters = arrayOf(LengthFilter(6), InputFilter { source, _, _, _, dstart, _ ->
			if (dstart == 0 && source.isNotEmpty() && source[0] == '0') {
				return@InputFilter ""
			}

			return@InputFilter null
		})

		binding?.amountField?.addTextChangedListener {
			binding?.amountField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))

			val amount = it?.toString()?.toFloatOrNull() ?: 0f

			if (isTopUp) {
				if (amount >= minDepositAmount) {
					binding?.topupButton?.isEnabled = true
					binding?.amountField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
				}
				else {
					binding?.topupButton?.isEnabled = false
					binding?.amountField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				}
			}
			else {
				reloadTemplate(amount)
			}
		}

		binding?.topupButton?.setOnClickListener {
			val amount = binding?.amountField?.text?.toString()?.toFloatOrNull() ?: 0f
			val minAmount = (if (isTopUp) commissionInfo?.minDeposit else commissionInfo?.minWithdrawals)?.toFloat() ?: if ((!isTopUp && mode == WalletFragment.MY_BALANCE)) 0.01f else WalletHelper.minTopupAmount

			if (amount < minAmount) {
				return@setOnClickListener
			}

			val wallet = walletHelper.findWallet(walletId) ?: return@setOnClickListener
			val currency = wallet.symbol

			val args = Bundle()
			args.putFloat(WalletFragment.ARG_AMOUNT, amount)
			args.putString(WalletFragment.ARG_CURRENCY, currency)
			args.putLong(WalletFragment.ARG_WALLET_ID, walletId)

			when (mode) {
				WalletFragment.PAYPAL -> {
					if (isTopUp) {
						presentFragment(PayPalTopupFragment(args))
					}
					else {
						if (binding?.amountField?.error != null) {
							return@setOnClickListener
						}

						presentFragment(PayPalTransferOutFragment(args))
					}
				}

				WalletFragment.CARD -> {
					if (isTopUp) {
						args.putInt(WalletFragment.ARG_MODE, WalletFragment.CARD)
						args.putBoolean(WalletFragment.ARG_IS_TOPUP, true)

						presentFragment(PaymentProcessingFragment(args))
					}
					else {
						if (binding?.amountField?.error != null) {
							return@setOnClickListener
						}

						presentFragment(BankRequisitesListFragment(args))
					}
				}

				WalletFragment.MY_BALANCE -> {
					if (isTopUp) {
						return@setOnClickListener
					}

					if (binding?.amountField?.error != null) {
						return@setOnClickListener
					}

					args.putInt(WalletFragment.ARG_MODE, WalletFragment.MY_BALANCE)
					args.putBoolean(WalletFragment.ARG_IS_TOPUP, false)

					presentFragment(PaymentProcessingFragment(args))
				}
			}
		}

		val withdrawMin = if (isTopUp) {
			commissionInfo?.minDeposit
		}
		else {
			commissionInfo?.minWithdrawals
		} ?: WalletHelper.minTopupAmount

		binding?.amountField?.setText(withdrawMin.toString())

		if (isTopUp) {
			binding?.minTopupInfoLabel?.text = context.getString(R.string.min_topup_hint, commissionInfo?.minDeposit?.toFloat() ?: WalletHelper.minTopupAmount).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
		}
		else {
			binding?.minTopupInfoLabel?.text = context.getString(R.string.min_transfer_out_hint, commissionInfo?.minWithdrawals?.toFloat() ?: WalletHelper.minTopupAmount).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
		}

		binding?.amountField?.requestFocus()

		fragmentView = binding?.root

		return binding?.root
	}

	private fun reloadTemplate(amount: Float) {
		val context = context ?: return

		if (!isTopUp && mode == WalletFragment.MY_BALANCE) {
			val maxAvailable = walletHelper.earnings?.availableBalance ?: 0f

			if (amount > maxAvailable) {
				binding?.amountField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				binding?.topupButton?.isEnabled = false
			}
			else {
				binding?.amountField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
				binding?.topupButton?.isEnabled = true
			}

			if (feeJob?.isActive == true) {
				feeJob?.cancel()
			}

			feeJob = ioScope.launch {
				val wallet = walletHelper.wallet ?: return@launch
				val earnings = walletHelper.earningsWallet ?: return@launch

				val fee = walletHelper.calculateTransferFee(fromWalletId = earnings.id, toWalletId = wallet.id, amount = amount)

				withContext(mainScope.coroutineContext) {
					if (fee > 0f) {
						binding?.commissionInfoLabel?.text = context.getString(R.string.transfer_out_commission_hint, context.getString(R.string.ello), fee).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
						binding?.commissionInfoLabel?.visible()
					}
					else {
						binding?.commissionInfoLabel?.gone()
					}

					binding?.apprSumLabel?.text = context.getString(R.string.coins_amount_commission_hint, amount - fee).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
					binding?.apprSumLabel?.visible()
				}
			}

			return
		}

		walletHelper.createWithdrawPaymentRequest(walletId = walletId, amount = amount, paymentId = walletHelper.currentTransferOutPaymentId) { paymentId, _, _, withdrawMax, _, fee, paymentSystemFee, error ->
			if (error != null) {
				binding?.amountField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				binding?.topupButton?.isEnabled = false
				return@createWithdrawPaymentRequest
			}

			val wallet = walletHelper.findWallet(walletId) ?: return@createWithdrawPaymentRequest

			walletHelper.currentTransferOutPaymentId = paymentId

			if (fee != null) {
				binding?.elloCommissionInfoLabel?.text = context.getString(R.string.transfer_out_commission_hint, context.getString(R.string.ello), fee).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
				binding?.elloCommissionInfoLabel?.visible()
			}

			if (paymentSystemFee != null) {
				val commissionSource = when (mode) {
					WalletFragment.PAYPAL -> context.getString(R.string.paypal)
					WalletFragment.MY_BALANCE -> context.getString(R.string.ello)
					else -> context.getString(R.string.bank)
				}

				binding?.commissionInfoLabel?.text = context.getString(R.string.transfer_out_commission_hint, commissionSource, paymentSystemFee).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
				binding?.commissionInfoLabel?.visible()
			}

			if (!isTopUp && mode != WalletFragment.MY_BALANCE) {
				val approximateValue = max(amount - (fee ?: 0f) - (paymentSystemFee ?: 0f), 0f)

				binding?.apprSumLabel?.text = context.getString(R.string.coins_amount_commission_hint, approximateValue).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
				binding?.apprSumLabel?.visible()
			}
			else {
				binding?.apprSumLabel?.gone()
			}

			val maxAvailable = min(withdrawMax ?: 0f, wallet.amount)

			if (amount > maxAvailable || amount < minWithdrawalAmount) {
				binding?.amountField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				binding?.topupButton?.isEnabled = false
			}
			else {
				binding?.amountField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
				binding?.topupButton?.isEnabled = true
			}
		}
	}

	override fun onPause() {
		super.onPause()
		walletHelper.removeListener(this)
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)
	}

	override fun onResume() {
		super.onResume()
		walletHelper.addListener(this)
		walletHelper.loadWallet()
		AndroidUtilities.requestAdjustNothing(parentActivity, classGuid)
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		if (feeJob?.isActive == true) {
			feeJob?.cancel()
			feeJob = null
		}

		walletHelper.removeListener(this)
		binding = null
	}

	override fun onWalletChanged(wallet: ElloRpc.UserWallet?, earnings: ElloRpc.Earnings?) {
		val context = context ?: return

		binding?.balanceLabel?.text = context.getString(R.string.balance_format, walletAmount).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))

		if (isTopUp) {
			binding?.minTopupInfoLabel?.text = context.getString(R.string.min_topup_hint, commissionInfo?.minDeposit?.toFloat() ?: WalletHelper.minTopupAmount).fillElloCoinLogos(tintColor = context.getColor(R.color.disabled_text))
		}
	}

	companion object {
		const val ARG_COMMISSION_INFO = "commission_info"
	}
}
