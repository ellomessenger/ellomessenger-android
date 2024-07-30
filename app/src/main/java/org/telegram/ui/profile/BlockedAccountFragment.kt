/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.databinding.BlockedAccountFragmentBinding
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.LoginActivity

class BlockedAccountFragment(private val status: LoginActivity.UserStatus, private val username: String) : BaseFragment() {
	private var binding: BlockedAccountFragmentBinding? = null

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(false)

		binding = BlockedAccountFragmentBinding.inflate(LayoutInflater.from(context))

		val imageView = RLottieImageView(context)
		imageView.setAutoRepeat(true)
		imageView.setAnimation(R.raw.panda_blocked, 160, 160)
		imageView.contentDescription = context.getString(R.string.banned_panda)

		binding?.pandaImage?.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		imageView.playAnimation()

		val formattedUsername = if (username.contains("@")) username else "@${username}"

		when (status) {
			LoginActivity.UserStatus.BLOCKED -> {
				val title = SpannableStringBuilder(context.getString(R.string.account_x_suspended, formattedUsername))
				title.setSpan(ForegroundColorSpan(context.getColor(R.color.brand)), title.indexOf(formattedUsername), title.indexOf(formattedUsername) + formattedUsername.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

				binding?.title?.text = title
				binding?.description?.setText(R.string.account_blocked)
			}

			LoginActivity.UserStatus.DELETED -> {
				val title = SpannableStringBuilder(context.getString(R.string.account_x_deleted, formattedUsername))
				title.setSpan(ForegroundColorSpan(context.getColor(R.color.brand)), title.indexOf(formattedUsername), title.indexOf(formattedUsername) + formattedUsername.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

				binding?.title?.text = title
				binding?.description?.setText(R.string.account_deleted)
			}
		}

		binding?.supportButton?.setOnClickListener {
			Browser.openUrl(parentActivity, BuildConfig.SUPPORT_URL)
		}

		binding?.okButton?.setOnClickListener {
			finishFragment()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}
}
