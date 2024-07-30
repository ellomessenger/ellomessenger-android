/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.cardview.widget.CardView
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.ui.Country

class CountriesAdapter(context: Context, @LayoutRes private val layout: Int, val countries: List<Country>) : ArrayAdapter<Country>(context, layout, countries) {
	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		val view = convertView ?: View.inflate(context, layout, null)
		val country = getItem(position) as Country

		val label = view.findViewById<TextView>(R.id.country_name_label)
		label.text = country.name

		val flagPlaceholder = view.findViewById<ImageView>(R.id.world_placeholder)
		val icon = view.findViewById<ImageView>(R.id.flag_icon)
		val iconContainer = view.findViewById<CardView>(R.id.flag_icon_container)

		iconContainer.invisible()
		flagPlaceholder.visible()

		var imageReceiver = view.tag as? ImageReceiver

		if (view.tag == null) {
			imageReceiver = ImageReceiver(icon).apply {
				setParentView(icon)

				setDelegate(object: ImageReceiver.ImageReceiverDelegate {
					override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
						imageReceiver.drawable?.let {
							icon.setImageDrawable(it)
							iconContainer.visible()
							flagPlaceholder.gone()
						} ?: run {
							iconContainer.invisible()
							flagPlaceholder.visible()
						}
					}
				})
			}.also {
				view.tag = it
			}
		}

		imageReceiver?.cancelLoadImage()

		val countryFlagUrl = country.shortname?.takeIf { it.isNotEmpty() }

		if (!countryFlagUrl.isNullOrEmpty()) {
			val location = ImageLocation.getForPath(countryFlagUrl)
			imageReceiver?.setImage(location, null, null, null, 0L, null, null, 0)
		}

		return view
	}

	override fun getAutofillOptions(): Array<CharSequence> {
		return countries.mapNotNull { it.name }.toTypedArray()
	}
}
