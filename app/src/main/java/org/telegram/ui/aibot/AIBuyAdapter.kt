/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhaylo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.aibot

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.databinding.AiBuyDescriptionItemBinding

class AIBuyAdapter : RecyclerView.Adapter<AiBuyViewHolder>() {

	private var descriptionList = mutableListOf<AiSubscriptionPlansFragment.Description>()

	@SuppressLint("NotifyDataSetChanged")
	fun setDescription(descriptionList: ArrayList<AiSubscriptionPlansFragment.Description>) {
		this.descriptionList = descriptionList
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AiBuyViewHolder(AiBuyDescriptionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

	@SuppressLint("SetTextI18n")
	override fun onBindViewHolder(holder: AiBuyViewHolder, position: Int) {
		val description = descriptionList[position]
		val binding = AiBuyDescriptionItemBinding.bind(holder.itemView)

		binding.title.text = description.title
		binding.description.text = description.description
	}

	override fun getItemCount() = descriptionList.size

}
