/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import org.telegram.messenger.R
import org.telegram.messenger.databinding.AccountTypeInfoFragmentBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class AccountTypeFragment : BaseFragment() {
	private var binding: AccountTypeInfoFragmentBinding? = null

	override fun createView(context: Context): View? {
		actionBar?.setBackgroundColor(Color.TRANSPARENT)
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.personal_vs_business))
		actionBar?.setAddToContainer(false)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}
				}
			}
		})

		binding = AccountTypeInfoFragmentBinding.inflate(LayoutInflater.from(context))

		fragmentView = binding?.root

		binding?.actionBarContainer?.addView(actionBar)

		return binding?.root
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}
}
