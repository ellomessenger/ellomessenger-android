/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.feed

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ExploreGridSpaceItemDecoration(spacing: Int) : RecyclerView.ItemDecoration() {
	private val halfSpacing = spacing / 2

	override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
		val position = parent.getChildAdapterPosition(view)
		val layoutManager = parent.layoutManager as? GridLayoutManager ?: return
		val spanCount = layoutManager.spanCount

		val column = position % spanCount
		val row = position / spanCount

		if (row == 0) {
			outRect.top = 0
		}
		else {
			outRect.top = halfSpacing
		}

		if (column == 0) {
			outRect.left = 0
		}
		else {
			outRect.left = halfSpacing
		}

		// Check if it's the last row
		if (row == (parent.adapter?.itemCount?.div(spanCount) ?: 0) - 1) {
			outRect.bottom = 0
		}
		else {
			outRect.bottom = halfSpacing
		}

		// Check if it's the last column
		if (column == spanCount - 1) {
			outRect.right = 0
		}
		else {
			outRect.right = halfSpacing
		}
	}
}
