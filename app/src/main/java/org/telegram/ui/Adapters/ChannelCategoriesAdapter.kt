/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.cardview.widget.CardView
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone

class ChannelCategoriesAdapter(context: Context, @LayoutRes private val layout: Int, val categories: List<String>) : ArrayAdapter<String>(context, layout, categories) {
	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		val view = convertView ?: View.inflate(context, layout, null)
		val category = getItem(position) as String

		val label = view.findViewById<TextView>(R.id.country_name_label)
		label.text = category

		val iconContainer = view.findViewById<CardView>(R.id.flag_icon_container)

		iconContainer.gone()

		return view
	}

	override fun getAutofillOptions(): Array<CharSequence> {
		return categories.toTypedArray()
	}
}
