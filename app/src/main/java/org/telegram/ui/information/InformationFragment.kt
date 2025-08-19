/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2025.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.information

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.databinding.FragmentInformationBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class InformationFragment : BaseFragment() {
	private var binding: FragmentInformationBinding? = null

	override fun createView(context: Context): View? {
		initActionBar(context)
		initViewBinding(context)
		getUserData()

		initListeners()

		binding?.tvElloAppInfo?.text = context.getString(R.string.ello_app_info, BuildConfig.VERSION_NAME)

		return binding?.root
	}

	private fun initActionBar(context: Context) {
		actionBar?.setAddToContainer(true)
		actionBar?.setTitle(context.getString(R.string.information))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})
	}

	private fun initViewBinding(context: Context) {
		binding = FragmentInformationBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun getUserData() {
		val userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()

		if (userId != 0L) {
			val user = messagesController.getUser(userId)
			val userFull = messagesController.getUserFull(userId)

			binding?.tvUserName?.text = user?.username
			binding?.tvUserId?.text = userId.toString()
			binding?.tvUserEmail?.text = userFull?.email
		}
	}

	private fun initListeners() {
		binding?.privacyPolicyContainer?.setOnClickListener {
			Browser.openUrl(context, PRIVACY_POLICY_URL)
		}

		binding?.termsConditionsContainer?.setOnClickListener {
			Browser.openUrl(context, TERMS_AND_CONDITIONS_URL)
		}

		binding?.aiPolicyContainer?.setOnClickListener {
			Browser.openUrl(context, AI_POLICY_URL)
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	companion object {
		const val PRIVACY_POLICY_URL = "https://ellomessenger.com/privacy-policy"
		const val TERMS_AND_CONDITIONS_URL = "https://ellomessenger.com/terms"
		const val AI_POLICY_URL = "https://ellomessenger.com/ai-terms"
	}
}
