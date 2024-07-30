/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.ProfileSettingsFragmentBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.profile.ChangeEmailPasswordFragment
import org.telegram.ui.profile.DeleteAccountInfoFragment

class ProfileSettingsFragment : BaseFragment() {
	private var binding: ProfileSettingsFragmentBinding? = null

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.setTitle(context.getString(R.string.administration))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = ProfileSettingsFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.privacyContainer?.setOnClickListener {
			presentFragment(MyPrivacySettingsFragment())
		}

		binding?.emailContainer?.setOnClickListener {
			presentFragment(ChangeEmailPasswordFragment())
		}

		binding?.languageContainer?.setOnClickListener {
			// TODO: open language settings
			Toast.makeText(context, "TODO: open language settings", Toast.LENGTH_SHORT).show()

			// presentFragment(LanguageSelectActivity())
		}

		binding?.devicesContainer?.setOnClickListener {
			presentFragment(SessionsActivity(SessionsActivity.ALL_SESSIONS))
		}

		binding?.deleteAccountButton?.setOnClickListener {
			requestAccountDeleteConfirmation()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun requestAccountDeleteConfirmation() {
		val context = context ?: return

		AlertsCreator.createSimpleAlert(context, null, context.getString(R.string.delete_account_dialog_text), context.getString(R.string.confirm), context.getString(R.string.cancel), { _, _ ->
			presentFragment(DeleteAccountInfoFragment())
		}) { _, _ ->
			// just close dialog
		}.also {
			showDialog(it.create())
		}
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

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}
}
