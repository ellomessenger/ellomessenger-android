/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.messenger.databinding.EarningsWalletViewHolderBinding
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.ElloRpc

class EarningsWalletViewHolder(private val binding: EarningsWalletViewHolderBinding, private val isDemo: Boolean = false) : RecyclerView.ViewHolder(binding.root) {
	init {
		if (isDemo) {
			binding.balanceLabel.gone()
			binding.transferBalanceLabel.gone()
			binding.frozenBalanceLabel.gone()
			binding.frozenBalanceInfoLabel.gone()
			binding.transferBalanceInfoLabel.gone()
		}
	}

	fun bind(wallet: ElloRpc.UserWallet?, earnings: ElloRpc.Earnings) {
		if (wallet == null || isDemo) {
			return
		}

		binding.balanceLabel.text = binding.root.context.getString(R.string.balance_short_format, wallet.amount)
		binding.frozenBalanceLabel.text = binding.root.context.getString(R.string.balance_short_format, earnings.freezeBalance)
		binding.transferBalanceLabel.text = binding.root.context.getString(R.string.balance_short_format, earnings.availableBalance)
	}
}
