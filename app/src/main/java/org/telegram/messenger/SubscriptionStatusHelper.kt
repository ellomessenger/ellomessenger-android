/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.messenger

import android.content.Context
import androidx.core.content.edit
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.profile.utils.toFormattedDate
import java.text.SimpleDateFormat
import java.util.Locale

class SubscriptionStatusHelper(account: Int) : BaseController(account) {
	private var daysLeft = 0
	private var amount = walletHelper.wallet?.amount ?: 0f

	fun fetchSubscriptionStatus(context: Context, showAlertCallback: (Boolean?, String?, Boolean?) -> Unit) {
		val request = ElloRpc.getSubscriptionsChatBotRequest(0L)

		connectionsManager.sendRequest(request) { response, error ->
			if (response is TLRPC.TLBizDataRaw) {
				val data = response.readData<ElloRpc.SubscriptionInfoAiBot>()

				data?.let {
					if ((!it.textSubExpired && it.textExpirationDate != 0L) || (!it.imgSubExpired && it.imgExpirationDate != 0L) || (!it.doubleSubExpired && it.doubleExpirationDate != 0L)) {
						updateSubscriptionStatus(it, context = context, showAlertCallback)
					}
				}
			}
			else {
				error?.let { FileLog.e("Error fetching subscription status: ${it.text}") }
			}
		}
	}

	private fun getSubscriptionPriceInfo(callback: (ElloRpc.PriceInfoResponse?) -> Unit) {
		val req = ElloRpc.getPriceInfo()

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TLRPC.TLBizDataRaw) {
				val data = response.readData<ElloRpc.PriceInfoResponse>()
				callback(data)
			}
		}
	}

	private fun updateSubscriptionStatus(data: ElloRpc.SubscriptionInfoAiBot, context: Context, showAlertCallback: (Boolean?, String?, Boolean?) -> Unit) {
		daysLeft = when {
			!data.doubleSubExpired -> data.doubleDayLeft
			!data.textSubExpired -> data.textDayLeft
			!data.imgSubExpired -> data.imgDayLeft
			else -> 0
		}

		getSubscriptionPriceInfo {
			if (it != null && shouldShowDialogToday(context) && daysLeft <= 1) {
				AndroidUtilities.runOnUIThread {
					when {
						data.doubleSubActive && amount < it.doubleSubscriptionPrice -> {
							showAlertCallback(false, null, true)
						}

						data.isTextSubscriptionActive && amount < it.textSubscriptionPrice -> {
							showAlertCallback(false, null, true)
						}

						data.isImgSubscriptionActive && amount < it.imageSubscriptionPrice -> {
							showAlertCallback(false, null, true)
						}
					}

					updateLastShownDate(context)
				}
			}
		}

		if (shouldShowDialogToday(context) && daysLeft <= 1 && (!data.isImgSubscriptionActive || !data.isTextSubscriptionActive || !data.doubleSubActive)) {
			val expirationDate = when {
				!data.doubleSubExpired -> (data.doubleExpirationDate * 1000L).toFormattedDate()
				!data.textSubExpired -> (data.textExpirationDate * 1000L).toFormattedDate()
				!data.imgSubExpired -> (data.imgExpirationDate * 1000L).toFormattedDate()
				else -> ""
			}

			AndroidUtilities.runOnUIThread {
				showAlertCallback(true, expirationDate, false)

				updateLastShownDate(context)
			}
		}
	}

	private fun shouldShowDialogToday(context: Context): Boolean {
		val prefs = context.getSharedPreferences("SubscriptionPrefs", Context.MODE_PRIVATE)
		val lastShownDate = prefs.getString("last_dialog_date", null)

		val currentDate = getCurrentDate()

		return lastShownDate != currentDate
	}

	private fun updateLastShownDate(context: Context) {
		context.getSharedPreferences("SubscriptionPrefs", Context.MODE_PRIVATE).edit {
			putString("last_dialog_date", getCurrentDate())
		}
	}

	private fun getCurrentDate(): String {
		val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
		return dateFormat.format(java.util.Date())
	}
}
