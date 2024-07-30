/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.messenger

import android.os.Build

object BuildVars {
	var logsEnabled = BuildConfig.DEBUG

	@JvmField
	var USE_CLOUD_STRINGS = false

	@JvmField
	var NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29

	@JvmField
	var APP_ID = 13141676 // 4

	// @JvmField
	// var APP_HASH = "9a980bfd276d7cf1a73037c521460287" // "014b35b6184100b085b0d0572f9b5103"

	@JvmField
	var PLAYSTORE_APP_URL = "https://play.google.com/store/apps/details?id=com.beint.elloapp"

	@JvmField
	var GOOGLE_AUTH_CLIENT_ID = ""

	// You can use this flag to disable Google Play Billing (If you're making fork and want it to be in Google Play)
	@JvmField
	var IS_BILLING_UNAVAILABLE = false

	@JvmStatic
	fun useInvoiceBilling(): Boolean {
		return BuildConfig.DEBUG || hasDirectCurrency()
	}

	private fun hasDirectCurrency(): Boolean {
		val productDetails = BillingController.PREMIUM_PRODUCT_DETAILS

		if (!BillingController.getInstance().isReady || productDetails == null) {
			return false
		}

		val offers = productDetails.subscriptionOfferDetails

		if (offers != null) {
			for (offerDetails in offers) {
				for (phase in offerDetails.pricingPhases.pricingPhaseList) {
					for (cur in MessagesController.getInstance(UserConfig.selectedAccount).directPaymentsCurrency) {
						if (phase.priceCurrencyCode == cur) {
							return true
						}
					}
				}
			}
		}

		return false
	}
}
