/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.databinding.TransactionsViewLayoutBinding
import org.telegram.tgnet.ElloRpc

class TransactionsAdapter : RecyclerView.Adapter<TransactionsStatsViewHolder>() {
	private var period = TransactionsHistoryStatsDataSource.Period.WEEK

	var stats: List<ElloRpc.TransferStats>? = null
		private set

	fun setStats(stats: List<ElloRpc.TransferStats>?, period: TransactionsHistoryStatsDataSource.Period) {
		this.stats = stats
		this.period = period
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionsStatsViewHolder {
		val view = TransactionsViewLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return TransactionsStatsViewHolder(view)
	}

	override fun getItemCount(): Int {
		return stats?.size ?: 0
	}

	override fun onBindViewHolder(holder: TransactionsStatsViewHolder, position: Int) {
		holder.bind(stats?.getOrNull(position), period)
	}
}
