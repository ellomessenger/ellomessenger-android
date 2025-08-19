/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC.StickerSetCovered
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ArchivedStickerSetCell
import org.telegram.ui.Components.LayoutHelper.absoluteGravityStart
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.StickersActivity

class StickersArchiveAlert(context: Context, private val parentFragment: BaseFragment?, sets: List<StickerSetCovered>) : AlertDialog.Builder(context) {
	private val stickerSets = sets.toList()
	private var currentType = 0

	init {
		val set = sets[0]

		if (set.set?.masks == true) {
			currentType = MediaDataController.TYPE_MASK
			setTitle(context.getString(R.string.ArchivedMasksAlertTitle))
		}
		else {
			currentType = MediaDataController.TYPE_IMAGE
			setTitle(context.getString(R.string.ArchivedStickersAlertTitle))
		}

		val container = LinearLayout(context)
		container.orientation = LinearLayout.VERTICAL
		setView(container)

		val textView = TextView(context)
		textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		textView.gravity = absoluteGravityStart
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.setPadding(AndroidUtilities.dp(23f), AndroidUtilities.dp(10f), AndroidUtilities.dp(23f), 0)

		if (set.set?.masks == true) {
			textView.text = context.getString(R.string.ArchivedMasksAlertInfo)
		}
		else {
			textView.text = context.getString(R.string.ArchivedStickersAlertInfo)
		}

		container.addView(textView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		val listView = RecyclerListView(context)
		listView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		listView.setAdapter(ListAdapter(context))
		listView.isVerticalScrollBarEnabled = false
		listView.setPadding(AndroidUtilities.dp(10f), 0, AndroidUtilities.dp(10f), 0)
		listView.setGlowColor(-0xa0909)

		container.addView(listView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 10f, 0f, 0f))

		setNegativeButton(context.getString(R.string.Close)) { dialog, _ -> dialog.dismiss() }

		if (parentFragment != null) {
			setPositiveButton(context.getString(R.string.Settings)) { dialog, _ ->
				parentFragment.presentFragment(StickersActivity(currentType, null))
				dialog.dismiss()
			}
		}
	}

	private inner class ListAdapter(var context: Context) : SelectionAdapter() {
		override fun getItemCount(): Int {
			return stickerSets.size
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return false
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view = ArchivedStickerSetCell(context, false)
			view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(82f))
			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			(holder.itemView as? ArchivedStickerSetCell)?.setStickersSet(stickerSets[position], position != stickerSets.size - 1)
		}
	}
}
