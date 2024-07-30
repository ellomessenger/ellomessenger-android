package org.telegram.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.ChangeEmailPasswordFragmentBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class ChangeEmailPasswordFragment : BaseFragment() {
	private var binding: ChangeEmailPasswordFragmentBinding? = null

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.setTitle(context.getString(R.string.Change))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = ChangeEmailPasswordFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.emailContainer?.setOnClickListener {
			presentFragment(ChangeEmailFragment())
		}

		binding?.passwordContainer?.setOnClickListener {
			presentFragment(ChangePasswordFragment())
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
