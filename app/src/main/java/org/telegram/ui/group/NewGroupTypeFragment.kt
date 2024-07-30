/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.group

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.messenger.databinding.NewMessageGroupTypeViewBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.LayoutHelper

class NewGroupTypeFragment(args: Bundle) : BaseFragment(args) {
	private var recyclerListView: RecyclerView? = null

	private val adapter = object : RecyclerView.Adapter<GroupTypeViewHolder>() {
		var selectedPosition = 0

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupTypeViewHolder {
			val binding = NewMessageGroupTypeViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
			return GroupTypeViewHolder(binding)
		}

		override fun onBindViewHolder(holder: GroupTypeViewHolder, position: Int) {
			when (position) {
				0 -> holder.isPublic = true
				1 -> holder.isPublic = false
			}

			holder.isSelected = (selectedPosition == position)

			holder.itemView.setOnClickListener {
				selectedPosition = position

				for (i in 0 until itemCount) {
					recyclerListView?.findViewHolderForAdapterPosition(i)?.let { viewHolder ->
						(viewHolder as GroupTypeViewHolder).isSelected = (selectedPosition == i)
					}
				}
			}
		}

		override fun getItemCount(): Int {
			return 2
		}
	}

	override fun createView(context: Context): View {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(R.string.select))

		val menu = actionBar?.createMenu()
		menu?.addItem(BUTTON_DONE, R.drawable.ic_checkmark)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}
					BUTTON_DONE -> {
						val args = Bundle()
						args.putBoolean("isPublic", adapter.selectedPosition == 0)
						presentFragment(GroupCreateActivity(args), true)
					}
				}
			}
		})

		val frameLayout = FrameLayout(context)

		recyclerListView = RecyclerView(context)
		recyclerListView?.layoutManager = LinearLayoutManager(context)
		recyclerListView?.adapter = adapter

		frameLayout.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		fragmentView = frameLayout

		return frameLayout
	}

	private class GroupTypeViewHolder(private val binding: NewMessageGroupTypeViewBinding) : RecyclerView.ViewHolder(binding.root) {
		var isPublic: Boolean = false
			set(value) {
				field = value

				if (value) {
					binding.checkbox.text = binding.root.context.getString(R.string.public_group)
				}
				else {
					binding.checkbox.text = binding.root.context.getString(R.string.private_group)
				}
			}

		var isSelected: Boolean = false
			set(value) {
				field = value
				binding.checkbox.isChecked = value
			}
	}

	companion object {
		private const val BUTTON_DONE = 1
	}
}
