/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.aispace

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.messenger.databinding.AiSpaceCategoriesItemBinding

class AiSpaceCategoriesAdapter(private val context: Context, private var onClickListener: (String) -> Unit = {}) : ListAdapter<String, AiSpaceCategoriesAdapter.ViewHolder>(DiffCallBack()) {

	private var selectedCategories: MutableSet<String> = mutableSetOf()

	fun setOnClickListener(item: (String) -> Unit) {
		onClickListener = item
	}

	init {
		selectedCategories.add(context.getString(R.string.all))
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = AiSpaceCategoriesItemBinding.inflate(inflater, parent, false)

		return ViewHolder(binding)
	}

	@SuppressLint("NotifyDataSetChanged")
	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val categories = currentList[position]

		holder.bind(categories)

		holder.binding.categoriesBg.isSelected = selectedCategories.contains(categories)

		holder.binding.root.setOnClickListener {
			if (categories == context.getString(R.string.all)) {
				selectedCategories.clear()
				selectedCategories.add(categories)
			} else {
				selectedCategories.remove(context.getString(R.string.all))

				if (selectedCategories.contains(categories)) {
					selectedCategories.remove(categories)
				} else {
					selectedCategories.add(categories)
				}
			}

			if (selectedCategories.isEmpty()) {
				selectedCategories.add(context.getString(R.string.all))
			}

			println(categories)

			notifyDataSetChanged()

			onClickListener(categories)
		}
	}


	private class DiffCallBack : DiffUtil.ItemCallback<String>() {
		override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
			return oldItem == newItem
		}

		override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
			return oldItem == newItem
		}
	}

	class ViewHolder(val binding: AiSpaceCategoriesItemBinding) : RecyclerView.ViewHolder(binding.root) {

		fun bind(categories: String) {
			binding.filterText.text = categories
		}

	}

}