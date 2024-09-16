/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Adapters

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.tgnet.TLRPC.TL_messageMediaVenue
import org.telegram.ui.Cells.LocationCell
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.RecyclerListView

open class LocationActivitySearchAdapter(private val mContext: Context) : BaseLocationAdapter() {
	private val globalGradientView = FlickerLoadingView(mContext)

	init {
		globalGradientView.setIsSingleCell(true)
	}

	override fun getItemCount(): Int {
		return if (isSearching()) 3 else places.size
	}

	val isEmpty: Boolean
		get() = places.size == 0

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val locationCell = LocationCell(mContext, false)
		return RecyclerListView.Holder(locationCell)
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		val place = getItem(position)
		val iconUrl = if (!isSearching() && position >= 0 && position < iconUrls.size) iconUrls[position] else null
		val locationCell = holder.itemView as LocationCell
		locationCell.setLocation(place, iconUrl, position, position != itemCount - 1)
	}

	fun getItem(i: Int): TL_messageMediaVenue? {
		if (isSearching()) {
			return null
		}

		return places.getOrNull(i)
	}

	override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
		return true
	}

	override fun notifyStartSearch(wasSearching: Boolean, oldItemCount: Int, animated: Boolean) {
		if (wasSearching) {
			return
		}

		notifyDataSetChanged()
	}
}
