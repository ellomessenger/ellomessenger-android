/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.DeleteAccountInfoFragmentBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class DeleteAccountInfoFragment : BaseFragment() {
	private var binding: DeleteAccountInfoFragmentBinding? = null

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.setTitle(context.getString(R.string.delete_account))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = DeleteAccountInfoFragmentBinding.inflate(LayoutInflater.from(context))

		val htmlContent = context.getString(R.string.account_deletion_info)

		binding?.contentLabel?.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			val flags = (Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM or Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST)
			Html.fromHtml(htmlContent, flags)
		}
		else {
			@Suppress("DEPRECATION") Html.fromHtml(htmlContent)
		}

		binding?.confirmButton?.isEnabled = false

		binding?.confirmButton?.setOnClickListener {
			presentFragment(DeleteAccountLeftoversFragment())
		}

		binding?.understoodCheckbox?.setOnCheckedChangeListener { _, isChecked ->
			binding?.confirmButton?.isEnabled = isChecked
		}

		fragmentView = binding?.root

		return binding?.root
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	@SuppressLint("SourceLockedOrientationActivity")
	override fun onResume() {
		super.onResume()

		if (!AndroidUtilities.isTablet()) {
			parentActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		}
	}

	override fun onPause() {
		super.onPause()

		if (!AndroidUtilities.isTablet()) {
			parentActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
		}
	}
}
