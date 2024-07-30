/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.databinding.WalletTransactionDateHeaderBinding

class TransactionsDateHeaderViewHolder(private val binding: WalletTransactionDateHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
	var date: String? = null
		set(value) {
			field = value
			binding.dateLabel.text = value
		}
}
