/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2025.
 * Copyright Shamil Afandiyev, Ello 2025.
 */
package org.telegram.ui.donate

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BillingManager
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.DonateSuccessPopupBinding
import org.telegram.messenger.databinding.FragmentMembershipBinding
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.DialogsActivity.Companion.LAST_SHOWN_KEY
import org.telegram.ui.donate.adapters.MembershipAdapter
import org.telegram.ui.donate.adapters.MembershipModel
import java.time.LocalDate

class MembershipFragment : BaseFragment(), BillingManager.BillingUpdatesListener {
	private var binding: FragmentMembershipBinding? = null
	private var adapter: MembershipAdapter? = null
	private var billingManager: BillingManager? = null
	private var planId = "primary-membership-3-month"
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

	override fun createView(context: Context): View? {
		billingManager = BillingManager(context, this)
		billingManager?.startConnection()

		initActionBar(context)
		initBinding(context)
		initAdapter()
		initListeners()

		binding?.subscriptionCostNotice?.text = context.getString(R.string.subscription_cost_notice, "30")

		fragmentView = binding?.root

		return binding?.root
	}

	private fun initListeners() {
		adapter?.setOnClickListener {
			planId = it.planId
			binding?.subscriptionCostNotice?.text = context?.getString(R.string.subscription_cost_notice, it.price.toString())
		}

		binding?.subscribeButton?.setOnClickListener {
			parentActivity?.let { billingManager?.purchaseSubscription(it, planId, userConfig.getClientUserId().toString()) }
		}
	}

	private fun initBinding(context: Context) {
		binding = FragmentMembershipBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun initActionBar(context: Context) {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.join_ello_plus))
		actionBar?.castShadows = true

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> finishFragment()
				}
			}
		})
	}

	private fun initAdapter() {
		adapter = MembershipAdapter()
		binding?.membershipList?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
		binding?.membershipList?.adapter = adapter

		adapter?.setMembership(listOf(MembershipModel(1, 10, "primary-membership-1"), MembershipModel(3, 30, "primary-membership-3-month"), MembershipModel(6, 60, "primary-membership-6"), MembershipModel(12, 120, "primary-membership-12")))
	}

	private fun createSuccessDialogAlert(context: Context) {
		val builder = AlertDialog.Builder(context)

		val alertDialog = builder.create()

		val binding = DonateSuccessPopupBinding.inflate(LayoutInflater.from(context))

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
		val purchaseToken = purchases.firstOrNull()?.purchaseToken

		for (purchase in purchases) {
			handlePurchase(purchase)
		}

		ioScope.launch {
			val req = ElloRpc.verifyGoogleRequest(ApplicationLoader.applicationContext.packageName, MEMBERSHIP_PRODUCT_ID, purchaseToken ?: "")
			val response = connectionsManager.performRequest(req)
			val error = response as? TLRPC.TLError

			withContext(mainScope.coroutineContext) {
				if (error == null) {
					FileLog.d("verify Google request success")
				}
				else {
					error.text?.let { FileLog.e(it) }
				}
			}
		}

		dismissBanner()
	}

	private fun handlePurchase(purchase: Purchase) {
		if (purchase.products.none { it == MEMBERSHIP_PRODUCT_ID }) {
			return
		}

		if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
			val acknowledgeParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()

			billingManager?.billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
				if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
					mainScope.launch { context?.let { createSuccessDialogAlert(it) } }
					FileLog.d("Purchase acknowledged successfully")
				}
				else {
					FileLog.e("Failed to acknowledge purchase: ${billingResult.responseCode}")
				}
			}
		}
	}

	override fun onBillingError(errorCode: Int) {
		FileLog.e("Error Code: $errorCode")
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
		private const val MEMBERSHIP_PRODUCT_ID = "com.beint.elloapp.membership"
	}
}
