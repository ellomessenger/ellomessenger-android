/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.channel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentAgeRestrictionPolicyBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class AgeRestrictionPolicyFragment : BaseFragment() {
	private var binding: FragmentAgeRestrictionPolicyBinding? = null

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(R.string.age_restriction_policy))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = FragmentAgeRestrictionPolicyBinding.inflate(LayoutInflater.from(context))

		fragmentView = binding?.root

		return binding?.root
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}
}
