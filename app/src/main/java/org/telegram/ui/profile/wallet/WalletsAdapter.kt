/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.UserConfig
import org.telegram.messenger.databinding.EarningsWalletViewHolderBinding
import org.telegram.messenger.databinding.UserWalletViewHolderBinding
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.WalletHelper
import java.io.Serializable

class WalletsAdapter(private val isDemo: Boolean = false) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	private val walletHelper = WalletHelper.getInstance(UserConfig.selectedAccount)

	var wallets: List<Serializable>? = null
		@SuppressLint("NotifyDataSetChanged") set(value) {
			field = value
			notifyDataSetChanged()
		}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			TYPE_MAIN_WALLET -> {
				val binding = UserWalletViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
				MainWalletViewHolder(binding, isDemo)
			}

			TYPE_BUSINESS_WALLET -> {
				val binding = EarningsWalletViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
				EarningsWalletViewHolder(binding, isDemo)
			}

			else -> {
				throw IllegalStateException("Unknown view type")
			}
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (isDemo) {
			return
		}

		val wallet = wallets?.getOrNull(position)

		when (holder.itemViewType) {
			TYPE_MAIN_WALLET -> {
				(holder as MainWalletViewHolder).bind(wallet as ElloRpc.UserWallet)
			}

			TYPE_BUSINESS_WALLET -> {
				(holder as EarningsWalletViewHolder).bind(walletHelper.earningsWallet, wallet as ElloRpc.Earnings)
			}

			else -> {
				throw IllegalStateException("Unknown view type")
			}
		}
	}

	override fun getItemCount(): Int {
		return wallets?.size ?: 0
	}

	override fun getItemViewType(position: Int): Int {
		return when (wallets?.getOrNull(position)) {
			is ElloRpc.UserWallet -> TYPE_MAIN_WALLET
			is ElloRpc.Earnings -> TYPE_BUSINESS_WALLET
			else -> throw IllegalStateException("Unknown wallet type")
		}
	}

	companion object {
		private const val TYPE_MAIN_WALLET = 0
		private const val TYPE_BUSINESS_WALLET = 1
	}
}