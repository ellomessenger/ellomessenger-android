/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.channel

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.R
import org.telegram.messenger.databinding.SubscriptionResultBinding
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.profile.wallet.WalletFragment

class SubscriptionResultFragment(args: Bundle) : BaseFragment(args) {
	override fun createView(context: Context): View {
		actionBar?.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), false)
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.castShadows = false

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		val binding = SubscriptionResultBinding.inflate(LayoutInflater.from(context))

		val success = arguments?.getBoolean(SUCCESS, false) ?: false
		val imageRes = arguments?.getInt(IMAGE_RES_ID, 0) ?: 0
		val showTopup = arguments?.getBoolean(SHOW_TOPUP, false) ?: false

		if (imageRes != 0) {
			binding.successImage.setImageResource(imageRes)
		}
		else {
			if (success) {
				binding.successImage.setImageResource(R.drawable.panda_payment_congrats)
			}
			else {
				binding.successImage.setImageResource(R.drawable.panda_payment_error)
			}
		}

		val title = arguments?.getString(TITLE, "")

		if (title.isNullOrEmpty()) {
			if (success) {
				binding.success.text = context.getString(R.string.success_excl)
			}
			else {
				binding.success.text = context.getString(R.string.sorry)
			}
		}
		else {
			binding.success.text = title
		}

		val description = arguments?.getString(DESCRIPTION, "")

		if (description.isNullOrEmpty()) {
			if (success) {
				binding.successDescription.text = context.getString(R.string.ai_buy_success_non_format)
			}
			else {
				binding.successDescription.text = context.getString(R.string.something_went_wrong)
			}
		}
		else {
			binding.successDescription.text = description
		}

		if (showTopup) {
			binding.topupButton.visible()

			binding.topupButton.setOnClickListener {
				presentFragment(WalletFragment(), true)
			}
		}

		binding.button.setOnClickListener {
			finishFragment()
		}

		fragmentView = binding.root

		return binding.root
	}

	override fun onBackPressed(): Boolean {
		finishFragment(false)
		return true
	}

	companion object {
		const val SUCCESS = "success"
		const val IMAGE_RES_ID = "imageResId"
		const val TITLE = "title"
		const val DESCRIPTION = "description"
		const val SHOW_TOPUP = "showTopup"
	}
}
