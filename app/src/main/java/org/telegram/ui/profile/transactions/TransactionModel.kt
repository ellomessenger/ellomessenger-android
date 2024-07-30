package org.telegram.ui.profile.transactions

import java.util.*
import kotlin.random.Random

data class TransactionModel(val id: String = UUID.randomUUID().toString(), val user: String = "Anika", val totalAmount: Int = Random.nextInt(1000), val commissions: Int = Random.nextInt(50), val receivedDateMillis: Long = Random.nextLong(from = Date().time, until = Date().time + 100_000_000_000), val withdrawalRequestDateMillis: Long = Random.nextLong(from = Date().time, until = Date().time + 100_000_000_000), val transferDateMillis: Long = Random.nextLong(from = Date().time, until = Date().time + 100_000_000_000), val paymentDateMillis: Long = Random.nextLong(from = Date().time, until = Date().time + 100_000_000_000), val status: TransactionStatus = TransactionStatus.values().random())

enum class TransactionStatus {
	Approved, Rejected, Pending,
}

fun provideFakeTransactions(): List<TransactionModel> {
	return List(20) { TransactionModel() }
}
