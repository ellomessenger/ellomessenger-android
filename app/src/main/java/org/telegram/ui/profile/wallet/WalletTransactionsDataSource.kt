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
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import java.io.Serializable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

class WalletTransactionsDataSource {
	enum class PaymentType {
		ALL, DEPOSIT, TRANSFER, WITHDRAW
	}

	private val currentAccount: Int
		get() = UserConfig.selectedAccount

	private val transactions = mutableSetOf<ElloRpc.TransactionHistoryEntry>()
	private val outputTransactions = mutableListOf<Serializable>()
	private var reqId = 0
	private var isLastPage = false
	private var offset = 0
	var listener: WalletTransactionsListener? = null

	fun getTransactionsList(): List<Serializable> {
		return outputTransactions
	}

	fun getPayments(assetId: Int, walletId: Long, paymentType: PaymentType, query: String? = null, dateFrom: String? = null, dateTo: String? = null, force: Boolean = false, pageLimit: Int = PAGE_LIMIT) {
		if (reqId != 0 && force) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, false)
		}
		else if (reqId != 0) {
			return
		}

		if (isLastPage && !force) {
			return
		}

		offset = if (force) {
			0
		}
		else {
			transactions.size
		}

		val req = ElloRpc.getTransactionsHistoryRequest(assetId, walletId, paymentType.name.lowercase(), query, dateFrom, dateTo, pageLimit, offset)

		reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				if (response is TLRPC.TLBizDataRaw) {
					val data = response.readData<ElloRpc.TransactionsHistoryResponse>()
					processResponse(transactions = data?.transactions, offset = offset, error = null, isFailure = false)
				}
				else {
					processResponse(transactions = null, offset = offset, error = error, isFailure = true)
				}
			}

			reqId = 0
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	@Synchronized
	private fun processResponse(transactions: List<ElloRpc.TransactionHistoryEntry>?, offset: Int, error: TLRPC.TLError?, isFailure: Boolean) {
		if (offset == 0) {
			this.transactions.clear()
		}

		outputTransactions.clear()

		if (isFailure) {
			listener?.onTransactionsLoadError(error)
		}
		else {
			isLastPage = transactions.isNullOrEmpty()

			val dayDateFormatter = SimpleDateFormat.getDateInstance(DateFormat.MEDIUM)

			this.transactions.union(transactions ?: emptyList()).groupBy {
				// group by day
				val timestamp = it.payment?.createdAt ?: it.transaction?.createdAt ?: return@groupBy null
				val dateString = dayDateFormatter.format(Date(timestamp * 1000L))
				val simplifiedTimestamp = dayDateFormatter.parse(dateString)

				dateString to simplifiedTimestamp
			}.toList().sortedByDescending { (date, _) ->
				date?.second?.time ?: 0L
			}.forEach { (date, transactions) ->
				if (date != null) {
					this.transactions.addAll(transactions)

					outputTransactions.add(date.first)

					outputTransactions.addAll(transactions.sortedByDescending {
						it.payment?.createdAt ?: it.transaction?.createdAt
					})
				}
			}
		}

		listener?.onTransactionsLoaded()
	}

	interface WalletTransactionsListener {
		fun onTransactionsLoaded()
		fun onTransactionsLoadError(error: TLRPC.TLError?)
	}

	companion object {
		const val PAGE_LIMIT = 5
	}
}
