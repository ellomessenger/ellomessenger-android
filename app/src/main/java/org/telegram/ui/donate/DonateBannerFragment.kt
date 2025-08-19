/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2025.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.donate

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.BillingManager
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.DonateBannerFragmentBinding
import org.telegram.messenger.databinding.DonateSuccessPopupBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.DialogsActivity.Companion.LAST_SHOWN_KEY
import org.telegram.ui.donate.adapters.DonateAdapter
import java.time.LocalDate

class DonateBannerFragment : BaseFragment(), BillingManager.BillingUpdatesListener {
	private var binding: DonateBannerFragmentBinding? = null
	private var donateAdapter: DonateAdapter? = null
	private var billingManager: BillingManager? = null
	private var isMembership = true
	private var productId = "${TIER_PREFIX}1"
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

	override fun createView(context: Context): View? {
		billingManager = BillingManager(context, this)
		billingManager?.startConnection()

		initActionBar(context)
		initBinding(context)
		initDonateAdapter()
		initListeners()

		if (messagesController.isPremiumUser(messagesController.getUser(userConfig.clientUserId))) {
			binding?.alreadySubscribed?.visible()
			binding?.membershipContainer?.gone()

			binding?.regularDonateContainer?.performClick()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun initActionBar(context: Context) {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.support_our_mission))
		actionBar?.castShadows = true

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})
	}

	private fun initBinding(context: Context) {
		binding = DonateBannerFragmentBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun initListeners() {
		val membership = binding?.membershipContainer
		val regularDonate = binding?.regularDonateContainer

		membership?.setBackgroundResource(R.drawable.donate_rounded_border_active)
		regularDonate?.setBackgroundResource(R.drawable.donate_rounded_border_inactive)

		binding?.membershipContainer?.setOnClickListener {
			membership?.setBackgroundResource(R.drawable.donate_rounded_border_active)
			regularDonate?.setBackgroundResource(R.drawable.donate_rounded_border_inactive)

			binding?.membershipCheckBox?.isChecked = true
			binding?.regularDonateCheckBox?.isChecked = false

			binding?.regularDonate?.gone()

			isMembership = true
		}

		binding?.regularDonateContainer?.setOnClickListener {
			regularDonate?.setBackgroundResource(R.drawable.donate_rounded_border_active)
			membership?.setBackgroundResource(R.drawable.donate_rounded_border_inactive)

			binding?.regularDonateCheckBox?.isChecked = true
			binding?.membershipCheckBox?.isChecked = false

			binding?.regularDonate?.visible()

			isMembership = false
		}

		binding?.supportOurMission?.setOnClickListener {
			if (isMembership) {
				presentFragment(MembershipFragment())
			}
			else {
				parentActivity?.let { billingManager?.purchaseProduct(it, productId, userConfig.getClientUserId().toString()) }
			}
		}

		donateAdapter?.setOnClickListener {
			productId = when (it) {
				0 -> "${TIER_PREFIX}1"
				1 -> "${TIER_PREFIX}5"
				2 -> "${TIER_PREFIX}10"
				3 -> "${TIER_PREFIX}25"
				4 -> "${TIER_PREFIX}50"
				else -> "${TIER_PREFIX}1"
			}
		}

		binding?.restorePurchases?.setOnClickListener {
			billingManager?.checkActiveSubscriptions { result ->
				when (result) {
					is Purchase -> {
						mainScope.launch {
							showConfirmationDialog(title = R.string.restore_purchases_title, message = R.string.restore_purchases_description, buttonText = R.string.Restore, showCancel = true) {
								ioScope.launch {
									val productId = result.products.firstOrNull().orEmpty()
									val req = ElloRpc.verifyGoogleRequest("com.beint.elloapp", productId, result.purchaseToken)
									val response = connectionsManager.performRequest(req)
									val error = response as? TLRPC.TLError

									withContext(mainScope.coroutineContext) {
										if (error == null && response is TLRPC.TLBizDataRaw) {
											val res = response.readData<ElloRpc.UserDonateResponse>()

											if (res?.isActive == true) {
												showConfirmationDialog(title = R.string.purchases_restored, message = R.string.purchases_restored_description, buttonText = R.string.ok)
											}
											else {
												showConfirmationDialog(title = R.string.restore_failed_title, message = R.string.restore_failed_description, buttonText = R.string.ok)
											}
										}
									}
								}
							}
						}
					}

					false -> {
						mainScope.launch {
							showConfirmationDialog(title = R.string.no_purchases_found, message = R.string.no_active_purchases_description, buttonText = R.string.got_it)
						}
					}
				}
			}
		}
	}

	private fun initDonateAdapter() {
		donateAdapter = DonateAdapter()

		binding?.regularDonate?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
		binding?.regularDonate?.adapter = donateAdapter

		donateAdapter?.setAmount(context?.resources?.getStringArray(R.array.donation_amounts)?.toList() ?: listOf())
	}

	private fun createSuccessDialogAlert(context: Context) {
		val builder = AlertDialog.Builder(context)

		val alertDialog = builder.create()

		val binding = DonateSuccessPopupBinding.inflate(LayoutInflater.from(context))
		binding.descriptionContainer.gone()

		builder.setView(binding.root).setPositiveButton(context.getString(R.string.ok)) { _, _ ->
			alertDialog.dismiss()
		}

		showDialog(alertDialog)

		val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

		button?.setTextColor(context.getColor(R.color.brand))
	}

	private fun dismissBanner() {
		val currentDate = LocalDate.now()
		val currentMillis = currentDate.toEpochDay() * 24 * 60 * 60 * 1000
		MessagesController.getGlobalMainSettings().edit { putLong(LAST_SHOWN_KEY, currentMillis) }
		FileLog.d("Banner dismissed, new time saved: $currentMillis")
	}

	override fun onPurchasesUpdated(purchases: List<Purchase>) {
		for (purchase in purchases) {
			consumePurchase(purchase)
		}
	}

	private fun consumePurchase(purchase: Purchase) {
		if (purchase.products.none { it.startsWith(TIER_PREFIX) }) {
			return
		}

		val purchaseToken = purchase.purchaseToken
		val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()

		billingManager?.billingClient?.consumeAsync(consumeParams) { billingResult, _ ->
			if (billingResult.responseCode == BillingResponseCode.OK) {
				mainScope.launch { context?.let { createSuccessDialogAlert(it) } }
				dismissBanner()
			}
		}
	}

	override fun onBillingError(errorCode: Int) {
		FileLog.e("Error Code: $errorCode")

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

						consumePurchase(purchase)
					}
				}
			}
		}
	}

	private fun showConfirmationDialog(context: Context? = this.context, title: Int, message: Int, buttonText: Int, showCancel: Boolean = false, onPositiveClick: (() -> Unit)? = null) {
		if (context == null) {
			return
		}

		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(title))
		builder.setMessage(context.getString(message))

		builder.setPositiveButton(context.getString(buttonText)) { dialog, _ ->
			dialog.dismiss()
			onPositiveClick?.invoke()
		}

		if (showCancel) {
			builder.setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
				dialog.dismiss()
			}
		}

		val dialog = builder.create()
		dialog.show()

		val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

		button?.setTextColor(context.getColor(R.color.brand))
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}

		billingManager?.disconnect()
	}

	companion object {
		private const val TIER_PREFIX = "com.beint.elloapp.donate"
	}
}
