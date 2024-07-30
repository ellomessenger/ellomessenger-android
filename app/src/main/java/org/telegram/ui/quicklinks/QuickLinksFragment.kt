/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.quicklinks

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentQuickLinksBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ContactsActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.aispace.AiSpaceFragment
import org.telegram.ui.profile.referral.ReferralProgressFragment
import org.telegram.ui.profile.wallet.WalletFragment

class QuickLinksFragment : BaseFragment()  {

	private var binding: FragmentQuickLinksBinding? = null

	override fun createView(context: Context): View? {
		initActionBar(context)
		initViewBinding(context)
		initListeners()

		return binding?.root
	}

	private fun initActionBar(context: Context) {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.quick_links))
		actionBar?.castShadows = true

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when(id) { ActionBar.BACK_BUTTON -> finishFragment() }
			}
		})
	}

	private fun initViewBinding(context: Context) {
		binding = FragmentQuickLinksBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun initListeners() {
		binding?.btFindFriends?.setOnClickListener {
			(parentActivity as? LaunchActivity)?.switchToContactsFragment()
			finishFragment()
		}

		binding?.btCreateChannelGroupCourse?.setOnClickListener {
			val args = Bundle()
			args.putBoolean("destroyAfterSelect", true)
			args.putBoolean("disableSections", true)

			presentFragment(ContactsActivity(args))
		}

		binding?.btWallet?.setOnClickListener {
			presentFragment(WalletFragment())
		}

		binding?.btInviteFriends?.setOnClickListener {
			presentFragment(ReferralProgressFragment())
		}

		binding?.btExploreAi?.setOnClickListener {
			presentFragment(AiSpaceFragment())
		}
	}

	override fun onFragmentDestroy() {
		binding = null
		super.onFragmentDestroy()
	}

}