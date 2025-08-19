/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2025.
 */
package org.telegram.ui.donate.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.messenger.databinding.DonateItemBinding

@SuppressLint("NotifyDataSetChanged")
class DonateAdapter(private var onClickListener: (Int) -> Unit = {}) : RecyclerView.Adapter<DonateAdapter.DonateViewHolder>() {
	private var donateAmount = emptyList<String>()
	private var selectedPosition: Int = 0

	fun setOnClickListener(item: (Int) -> Unit) {
		onClickListener = item
	}

	fun setAmount(amount: List<String>) {
		donateAmount = amount
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DonateViewHolder {
		return DonateViewHolder(DonateItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
	}

	override fun getItemCount() = donateAmount.size

	override fun onBindViewHolder(holder: DonateViewHolder, @SuppressLint("RecyclerView") position: Int) {
		val amount = donateAmount[position]
		holder.binding.root.text = amount

		if (position == selectedPosition) {
			holder.binding.root.setBackgroundResource(R.drawable.donate_rounded_border_active)
		}
		else {
			holder.binding.root.setBackgroundResource(R.drawable.donate_rounded_border_inactive)
		}

		holder.binding.root.setOnClickListener {
			selectedPosition = position

			notifyDataSetChanged()

			onClickListener(selectedPosition)
		}
	}

	class DonateViewHolder(val binding: DonateItemBinding) : RecyclerView.ViewHolder(binding.root)
}
