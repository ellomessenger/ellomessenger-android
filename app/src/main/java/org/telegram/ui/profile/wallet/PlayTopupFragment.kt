/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.beint.elloapp.allCornersProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BillingManager
import org.telegram.messenger.R
import org.telegram.messenger.databinding.PlayTopupCardBinding
import org.telegram.messenger.databinding.PlayTopupFragmentBinding
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.getDrawableResourceByName
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.WalletHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.LayoutHelper

class PlayTopupFragment(arguments: Bundle) : BaseFragment(arguments), BillingManager.BillingUpdatesListener, WalletHelper.OnWalletChangedListener {
	private var binding: PlayTopupFragmentBinding? = null
	private var billingManager: BillingManager? = null
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	private var walletId = 0L
	private var walletAmountLabel: TextView? = null
	private var channelId = 0L

	private val walletAmount: Float
		get() = walletHelper.findWallet(walletId)?.amount ?: 0f

	override fun onFragmentCreate(): Boolean {
		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID)?.takeIf { it != 0L } ?: return false
		channelId = arguments?.getLong(WalletFragment.ARG_CHANNEL_ID, 0L) ?: 0L

		return true
	}

	override fun createView(context: Context): View {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.castShadows = false

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		walletAmountLabel = TextView(context)
		walletAmountLabel?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		walletAmountLabel?.typeface = Theme.TYPEFACE_BOLD
		walletAmountLabel?.setSingleLine()
		walletAmountLabel?.setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(8f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))

		val topSpace = if (actionBar?.occupyStatusBar == true) AndroidUtilities.statusBarHeight / AndroidUtilities.dp(2f) else 0

		actionBar?.addView(walletAmountLabel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL, 0f, topSpace.toFloat(), 0f, 0f))

		val binding = PlayTopupFragmentBinding.inflate(LayoutInflater.from(context)).also {
			this.binding = it
		}

		billingManager = BillingManager(context, this).also { it.startConnection() }

		fragmentView = binding.root

		return binding.root
	}

	override fun onResume() {
		super.onResume()
		walletHelper.addListener(this)
		walletHelper.loadWallet()
	}

	override fun onBillingConnected() {
		ioScope.launch io@{
			val products = billingManager?.loadTopups(TIER_PREFIX, TIERS)

			if (products.isNullOrEmpty()) {
				return@io
			}

			mainScope.launch main@{
				val binding = binding ?: return@main
				val context = context ?: return@main

				binding.buttonsContainer.removeAllViews()

				for (product in products) {
					val offerDetails = product.oneTimePurchaseOfferDetails ?: continue

					val button = PlayTopupCardBinding.inflate(LayoutInflater.from(context), binding.buttonsContainer, false)

					button.productNameLabel.text = product.name
					button.priceLabel.text = offerDetails.formattedPrice

					@DrawableRes var resId = context.resources.getDrawableResourceByName("gold${product.productId.removePrefix(TIER_PREFIX)}")

					if (resId == 0 || resId == -1) {
						resId = R.drawable.gold5
					}

					button.goldImage.setImageResource(resId)

					button.root.setOnClickListener {
						val activity = parentActivity ?: return@setOnClickListener
						billingManager?.purchaseProduct(activity, product.productId, userConfig.getClientUserId().toString())
					}

					button.root.outlineProvider = allCornersProvider(32f)
					button.root.clipToOutline = true

					binding.buttonsContainer.addView(button.root)
				}
			}
		}
	}

	override fun onPurchasesUpdated(purchases: List<Purchase>) {
		ioScope.launch {
			for (purchase in purchases) {
				if (purchase.products.any { it.startsWith(TIER_PREFIX) }) {
					validatePurchase(purchase)
				}
			}
		}
	}

	private suspend fun validatePurchase(purchase: Purchase): Boolean {
		val purchaseToken = purchase.purchaseToken
		val productId = purchase.products.firstOrNull() ?: return false
		val amount = purchase.products.firstOrNull()?.removePrefix(TIER_PREFIX)?.toIntOrNull()?.toFloat() ?: return false

		val request = ElloRpc.processGoogleTopupPayment(amount = amount, packageName = ApplicationLoader.applicationContext.packageName, productId = productId, purchaseToken = purchaseToken)
		val response = connectionsManager.performRequest(request)
		val error = response as? TLRPC.TLError

		mainScope.launch {
			if (error == null && response is TLRPC.TLBizDataRaw) {
				val res = response.readData<ElloRpc.GoogleTopupPaymentResponse>()

				if (res == null) {
					context?.let {
						BulletinFactory.of(this@PlayTopupFragment).createErrorBulletin(it.getString(R.string.UnknownError)).show()
					}

					return@launch
				}

				val args = Bundle()
				args.putInt(WalletFragment.ARG_MODE, WalletFragment.GOOGLE)
				args.putFloat(WalletFragment.ARG_AMOUNT, amount)
				args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
				args.putLong(WalletFragment.ARG_WALLET_PAYMENT_ID, res.paymentId)
				args.putBoolean(WalletFragment.ARG_IS_TOPUP, true)
				args.putLong(WalletFragment.ARG_CHANNEL_ID, channelId)

				presentFragment(PaymentProcessingFragment(args))

				val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()

				billingManager?.billingClient?.consumeAsync(consumeParams) { _, _ ->
					// ignored
				}
			}
			else {
				val message = error?.text ?: context?.getString(R.string.UnknownError)

				context?.let {
					BulletinFactory.of(this@PlayTopupFragment).createErrorBulletin(message).show()
				}

				return@launch
			}
		}

		return error == null
	}

	override fun onBillingError(@BillingResponseCode errorCode: Int) {
		when (errorCode) {
			BillingResponseCode.ITEM_ALREADY_OWNED -> {
				ioScope.launch {
					val unconsumedPurchases = billingManager?.loadPendingPurchases()

					if (unconsumedPurchases.isNullOrEmpty()) {
						return@launch
					}

					for (purchase in unconsumedPurchases) {
						var hasTopupProducts = false

						for (product in purchase.products) {
							if (product.startsWith(TIER_PREFIX)) {
								hasTopupProducts = true
								break
							}
						}

						if (!hasTopupProducts) {
							continue
						}

						validatePurchase(purchase)
					}
				}
			}
		}
	}

	override fun onFragmentDestroy() {
		walletHelper.removeListener(this)

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}

		billingManager?.disconnect()

		binding = null

		super.onFragmentDestroy()
	}

	companion object {
		private const val TIER_PREFIX = "com.beint.elloapp.topup"
		private val TIERS = listOf(5, 10, 15, 20, 25, 50, 75, 100)
	}

	override fun onWalletChanged(wallet: ElloRpc.UserWallet?, earnings: ElloRpc.Earnings?) {
		val context = context ?: return
		walletAmountLabel?.text = context.getString(R.string.balance_format, walletAmount).fillElloCoinLogos()
	}
}
