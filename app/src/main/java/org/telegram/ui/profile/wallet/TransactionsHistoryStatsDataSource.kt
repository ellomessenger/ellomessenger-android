/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.profile.wallet

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.UserConfig
import org.telegram.messenger.time.DateUtils
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC

class TransactionsHistoryStatsDataSource(private val walletId: Long) {
	enum class Period {
		WEEK, MONTH, YEAR
	}

	enum class Type {
		ALL, DEPOSIT, WITHDRAW
	}

	private val currentAccount: Int
		get() = UserConfig.selectedAccount

	private var page = 0
	private val limit = 25
	var listener: Listener? = null

	var stats = mutableListOf<ElloRpc.TransferStats>()
		private set

	var lastPage = false
		private set

	var isFirstPage = true
		private set

	fun load(period: Period, type: Type, forced: Boolean) {
		val p = if (forced) 0 else page
		val req = ElloRpc.getTransferStatisticGraphic(walletId = walletId, period = period.name.lowercase(), type = type.name.lowercase(), limit = limit, offset = p * limit)

		ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			if (response is TLRPC.TLBizDataRaw) {
				val data = response.readData<ElloRpc.TransferStatsResponse>()

				page += 1

				if (p == 0) {
					isFirstPage = true
					lastPage = false
					stats.clear()
				}
				else {
					isFirstPage = false
				}

				val dataStats = data?.stats

				if (dataStats.isNullOrEmpty()) {
					lastPage = true
					stats.add(ElloRpc.TransferStats(0f, period.currentFormatted(), emptyList()))
				}
				else {
					stats.addAll(dataStats)
				}

				stats.sortBy { it.data?.firstOrNull()?.date }

				AndroidUtilities.runOnUIThread {
					listener?.onStatsLoaded()
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					listener?.onStatsLoadFailed(error?.text)
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	interface Listener {
		fun onStatsLoaded()
		fun onStatsLoadFailed(message: String?)
	}
}

fun TransactionsHistoryStatsDataSource.Period.currentFormatted() = when (this) {
	TransactionsHistoryStatsDataSource.Period.WEEK -> DateUtils().currentWeek()
	TransactionsHistoryStatsDataSource.Period.MONTH -> DateUtils().currentMonth()
	TransactionsHistoryStatsDataSource.Period.YEAR -> DateUtils().currentYear()
}
