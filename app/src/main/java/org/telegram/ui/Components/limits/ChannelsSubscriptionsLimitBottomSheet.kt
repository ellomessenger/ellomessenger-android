/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Components.limits

import android.view.LayoutInflater
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.BottomSheetChannelsSubscriptionsLimitBinding
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet

class ChannelsSubscriptionsLimitBottomSheet(parentFragment: BaseFragment?, needFocus: Boolean, currentAccount: Int) : BottomSheet(parentFragment?.context, needFocus) {

	private var binding: BottomSheetChannelsSubscriptionsLimitBinding? = null

	init {
		val inflater = LayoutInflater.from(context)
		binding = BottomSheetChannelsSubscriptionsLimitBinding.inflate(inflater)

		setCustomView(binding?.root)

		val channelsLimit = MessagesController.getInstance(currentAccount).publicLinksLimitDefault

		binding?.limit?.text = channelsLimit.toString()
		binding?.description?.text = context.getString(R.string.channels_subscriptions_limit_reached, channelsLimit)

		binding?.closeButton?.setOnClickListener {
			dismiss()
		}

		binding?.okButton?.setOnClickListener {
			dismiss()
		}
	}

}