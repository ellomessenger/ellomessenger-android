/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2025.
 */
package org.telegram.ui.donate.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.messenger.databinding.MembershipItemBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible

@SuppressLint("NotifyDataSetChanged")
class MembershipAdapter(private var onClickListener: (MembershipModel) -> Unit = {}) : RecyclerView.Adapter<MembershipAdapter.ViewHolder>() {
	private var membershipList: List<MembershipModel> = emptyList()
	var selectedPosition: Int = 1

	fun setMembership(membership: List<MembershipModel>) {
		membershipList = membership
		notifyDataSetChanged()
	}

	fun setOnClickListener(item: (MembershipModel) -> Unit) {
		onClickListener = item
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		return ViewHolder(MembershipItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
	}

	override fun getItemCount(): Int = membershipList.size

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val membership = membershipList[position]

		val month = membership.month
		val price = membership.price

		val priceText = if (price == 120) {
			holder.binding.root.context.getString(R.string.subscription_plan_price, price)
		}
		else {
			holder.binding.root.context.resources.getQuantityString(R.plurals.subscription_plan_price, month, price, month)
		}

		holder.binding.subscriptionPlanMonths.text = holder.binding.root.context.resources.getQuantityString(R.plurals.subscription_plan_months, month, month)
		holder.binding.subscriptionPlanPrice.text = priceText

		if (position == selectedPosition) {
			holder.binding.root.backgroundTintList = ColorStateList.valueOf(holder.binding.root.context.getColor(R.color.orange_dark))
			holder.binding.root.strokeColor = Color.TRANSPARENT
			holder.binding.container.backgroundTintList = ColorStateList.valueOf(holder.binding.root.context.getColor(R.color.white))
			holder.binding.subscriptionPlanMonths.setTextColor(holder.binding.root.context.getColor(R.color.white))
			holder.binding.subscriptionPlanPrice.setTextColor(holder.binding.root.context.getColor(R.color.white))
			holder.binding.select.visible()
		}
		else {
			holder.binding.root.backgroundTintList = ColorStateList.valueOf(holder.binding.root.context.getColor(R.color.white))
			holder.binding.root.strokeColor = holder.binding.root.context.getColor(R.color.dark_fixed)
			holder.binding.container.backgroundTintList = ColorStateList.valueOf(holder.binding.root.context.getColor(R.color.dark_fixed))
			holder.binding.subscriptionPlanMonths.setTextColor(holder.binding.root.context.getColor(R.color.dark_fixed))
			holder.binding.subscriptionPlanPrice.setTextColor(holder.binding.root.context.getColor(R.color.dark_fixed))
			holder.binding.select.gone()
		}

		holder.binding.root.setOnClickListener {
			selectedPosition = position

			notifyDataSetChanged()

			onClickListener(membership)
		}
	}

	class ViewHolder(val binding: MembershipItemBinding) : RecyclerView.ViewHolder(binding.root)
}

data class MembershipModel(val month: Int, val price: Int, val planId: String)
