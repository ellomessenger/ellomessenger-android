/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2025.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.doOnTextChanged
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentChangeEmailBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.setError
import org.telegram.messenger.utils.validateEmail
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment

class ChangeEmailFragment : BaseFragment() {

	private var binding: FragmentChangeEmailBinding? = null

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.setTitle(context.getString(R.string.ChangeEmail))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = FragmentChangeEmailBinding.inflate(LayoutInflater.from(context))

		binding?.changeEmailButton?.setOnClickListener {
			val email = binding?.emailChangeField?.text?.toString() ?: return@setOnClickListener
			requestNewEmail(newEmail = email)
		}

		binding?.emailChangeField?.doOnTextChanged { _, _, _, _ ->
			checkButtonStatus()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun checkButtonStatus() {
		val email = binding?.emailChangeField?.text?.toString().orEmpty()
		val isValid = email.validateEmail()

		when {
			email.isEmpty() -> {
				binding?.emailChangeFieldLayout?.isErrorEnabled = false
				binding?.emailChangeFieldLayout?.error = null
				binding?.changeEmailButton?.isEnabled = false
			}
			!isValid -> {
				binding?.emailChangeFieldLayout?.setError(context, R.string.wrong_email_format)
				binding?.changeEmailButton?.isEnabled = false
			}
			else -> {
				binding?.emailChangeFieldLayout?.isErrorEnabled = false
				binding?.emailChangeFieldLayout?.error = null
				binding?.changeEmailButton?.isEnabled = true
			}
		}
	}

	private fun requestNewEmail(newEmail: String) {
		binding?.progressBar?.visible()

		if (newEmail.validateEmail()) {
			val req = ElloRpc.sendMagicLinkRequest(usernameOrEmail = newEmail, action = 2)

			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					binding?.progressBar?.gone()

					if (error == null && response is TLRPC.TLBizDataRaw) {
						val data = response.readData<ElloRpc.ChangeEmailResponse>()
						@Suppress("NAME_SHADOWING") val email = data?.email

						if (email != null) {
							val args = Bundle()
							args.putBoolean("is_change_email", true)
							presentFragment(EmailSentFragment(args))
						}
						else {
							binding?.errorLayout?.visible()
							binding?.errorText?.setText(R.string.error_you_entered_an_invalid_email_please_try_again)
							binding?.emailChangeFieldLayout?.invisible()
						}
					}
					else {
						when (error?.text) {
							"EMAIL_ALREADY_SEND" -> {
								context?.let {
									val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.email_already_sent)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }.create()

									dialog.setCancelable(false)
									dialog.setCanceledOnTouchOutside(false)
									dialog.show()
								}
							}
							"EMAIL_ALREADY_TAKEN" -> {
								context?.let {
									val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.email_already_use)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }.create()

									dialog.setCancelable(false)
									dialog.setCanceledOnTouchOutside(false)
									dialog.show()
								}
							}
							"EMAIL_IS_ALREADY_USE_BY_ACCOUNT" -> {
								context?.let {
									val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.email_in_use_by_another_account)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }.create()

									dialog.setCancelable(false)
									dialog.setCanceledOnTouchOutside(false)
									dialog.show()
								}
							}
							"INPUT_USER_DEACTIVATED" -> {
								context?.let {
									val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.input_user_deactivated)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }.create()

									dialog.setCancelable(false)
									dialog.setCanceledOnTouchOutside(false)
									dialog.show()
								}
							}
							else -> {
								binding?.emailChangeFieldLayout?.setError(context, R.string.error_you_entered_an_invalid_email_please_try_again)
							}
						}
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
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
