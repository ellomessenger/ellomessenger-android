/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024-2025.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.quicklinks

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentQuickLinksBinding
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.ContactsActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.aispace.AiSpaceFragment
import org.telegram.ui.profile.referral.ReferralProgressFragment
import org.telegram.ui.profile.wallet.WalletFragment

class QuickLinksFragment : BaseFragment() {
	private var binding: FragmentQuickLinksBinding? = null
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
				when (id) {
					ActionBar.BACK_BUTTON -> finishFragment()
				}
			}
		})
	}

	private fun initViewBinding(context: Context) {
		binding = FragmentQuickLinksBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root
	}

	private fun initListeners() {
		binding?.btTips?.setOnClickListener {
			openTipsFragment()
		}

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

		binding?.btSupport?.setOnClickListener {
			loadBotUser(BuildConfig.SUPPORT_BOT_ID) {
				openBot(BuildConfig.SUPPORT_BOT_ID)
			}
		}
	}

	private fun openTipsFragment() {
		(parentActivity as? LaunchActivity)?.runLinkRequest(currentAccount, BuildConfig.USER_TIPS_USER)
	}

	private fun openBot(botId: Long) {
		val args = Bundle()
		args.putInt("dialog_folder_id", 0)
		args.putInt("dialog_filter_id", 0)
		args.putLong("user_id", botId)

		if (!messagesController.checkCanOpenChat(args, this@QuickLinksFragment)) {
			return
		}

		presentFragment(ChatActivity(args))
	}

	private fun loadBotUser(botId: Long, onBotReady: () -> Unit) {
		if (messagesController.getUser(botId) != null) {
			onBotReady()
		}
		else {
			ioScope.launch {
				try {
					val user = withTimeout(3_000) {
						messagesController.loadUser(botId, classGuid, true)
					}

					withContext(mainScope.coroutineContext) {
						if (user != null) {
							onBotReady()
						}
						else {
							Toast.makeText(context, R.string.error_bot_not_found, Toast.LENGTH_SHORT).show()
						}
					}
				}
				catch (e: TimeoutCancellationException) {
					withContext(mainScope.coroutineContext) {
						Toast.makeText(context, R.string.error_timeout, Toast.LENGTH_SHORT).show()
					}
				}
			}
		}
	}

	override fun onFragmentDestroy() {
		binding = null

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}

		super.onFragmentDestroy()
	}

}
