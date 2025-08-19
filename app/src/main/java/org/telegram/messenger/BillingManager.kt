/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2025.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.messenger

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BillingManager(context: Context, private val listener: BillingUpdatesListener) : PurchasesUpdatedListener {
	val billingClient = BillingClient.newBuilder(context).enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).enableAutoServiceReconnection().setListener(this).build()

	interface BillingUpdatesListener {
		fun onBillingConnected() {}
		fun onPurchasesUpdated(purchases: List<Purchase>)
		fun onBillingError(@BillingResponseCode errorCode: Int)
	}

	fun startConnection() {
		billingClient.startConnection(object : BillingClientStateListener {
			override fun onBillingSetupFinished(billingResult: BillingResult) {
				if (billingResult.responseCode == BillingResponseCode.OK) {
					listener.onBillingConnected()
				}
				else {
					listener.onBillingError(billingResult.responseCode)
				}
			}

			override fun onBillingServiceDisconnected() {
				startConnection()
			}
		})
	}

	fun disconnect() {
		if (billingClient.isReady) {
			billingClient.endConnection()
		}
	}

	fun purchaseSubscription(activity: Activity, planId: String, userId: String) {
		val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId("com.beint.elloapp.membership").setProductType(BillingClient.ProductType.SUBS).build())).build()

		billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
			if (billingResult.responseCode == BillingResponseCode.OK && productDetailsList.productDetailsList.isNotEmpty()) {
				val productDetails = productDetailsList.productDetailsList.firstOrNull()
				val selectedOffer = productDetails?.subscriptionOfferDetails?.find { it.basePlanId == planId }

				if (selectedOffer != null) {
					val flowParams = BillingFlowParams.newBuilder().setObfuscatedAccountId(userId).setProductDetailsParamsList(listOf(ProductDetailsParams.newBuilder().setProductDetails(productDetails).setOfferToken(selectedOffer.offerToken).build())).build()
					billingClient.launchBillingFlow(activity, flowParams)
				}
				else {
					FileLog.e("Plan with ID $planId not found")
				}
			}
			else {
				listener.onBillingError(billingResult.responseCode)
			}
		}
	}

	fun purchaseProduct(activity: Activity, productId: String, userId: String) {
		val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(BillingClient.ProductType.INAPP).build())).build()

		billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
			if (billingResult.responseCode == BillingResponseCode.OK && productDetailsList.productDetailsList.isNotEmpty()) {
				val productDetails = productDetailsList.productDetailsList.firstOrNull()

				if (productDetails != null) {
					val flowParams = BillingFlowParams.newBuilder().setObfuscatedAccountId(userId).setProductDetailsParamsList(listOf(ProductDetailsParams.newBuilder().setProductDetails(productDetails).build())).build()
					billingClient.launchBillingFlow(activity, flowParams)
				}
			}
			else {
				FileLog.e("Product with ID $productId not found or billing error: ${billingResult.responseCode}")
			}
		}
	}

	fun checkActiveSubscriptions(callback: (Any) -> Unit) {
		val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()

		billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
			if (billingResult.responseCode == BillingResponseCode.OK) {
				if (purchasesList.isNotEmpty()) {
					val purchase = purchasesList.firstOrNull()
					callback(purchase ?: false)
				}
				else {
					callback(false)
				}
			}
		}
	}

	suspend fun loadPendingPurchases(): List<Purchase>? {
		return suspendCoroutine { continuation ->
			val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()

			billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
				if (billingResult.responseCode == BillingResponseCode.OK) {
					continuation.resume(purchasesList)
				}
				else {
					FileLog.e("Error loading pending purchases: ${billingResult.responseCode}")
					continuation.resume(null)
				}
			}
		}
	}

	override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
		if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
			listener.onPurchasesUpdated(purchases)
		}
		else {
			listener.onBillingError(billingResult.responseCode)
		}
	}

	suspend fun loadTopups(tierPrefix: String, tiers: List<Int>): List<ProductDetails>? {
		val productsList = tiers.map {
			QueryProductDetailsParams.Product.newBuilder().setProductId("$tierPrefix$it").setProductType(BillingClient.ProductType.INAPP).build()
		}

		val result = billingClient.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(productsList).build())

		if (result.billingResult.responseCode == BillingResponseCode.OK) {
			return result.productDetailsList?.sortedBy {
				it.productId.substringAfter(tierPrefix).toIntOrNull() ?: Int.MAX_VALUE
			}
		}

		return null
	}
}
