/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridSpaceItemDecoration(spacing: Int) : RecyclerView.ItemDecoration() {
	private val realSpacing = spacing / 2

	override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
		outRect.left = realSpacing
		outRect.right = realSpacing
		outRect.top = realSpacing
		outRect.bottom = realSpacing
	}
}
