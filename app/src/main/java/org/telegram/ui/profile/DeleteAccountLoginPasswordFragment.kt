/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024-2025.
 * Copyright Shamil Afandiyev, Ello 2025.
 */
package org.telegram.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.addTextChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.LeftoversLoginPasswordFragmentBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.setError
import org.telegram.messenger.utils.validateEmail
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC.TLBizDataRaw
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment

class DeleteAccountLoginPasswordFragment : BaseFragment() {

	private var binding: LeftoversLoginPasswordFragmentBinding? = null

	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.setTitle(context.getString(R.string.account_information))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = LeftoversLoginPasswordFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.emailField?.addTextChangedListener {
			validateFields()
		}

		binding?.deleteAccountButton?.setOnClickListener {
			ioScope.launch {
				requestVerificationCode()
			}
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun validateFields() {
		val email = binding?.emailField?.text?.toString().orEmpty()
		val isValid = email.validateEmail()

		when {
			email.isEmpty() -> {
				binding?.emailFieldLayout?.isErrorEnabled = false
				binding?.emailFieldLayout?.error = null
				binding?.deleteAccountButton?.isEnabled = false
			}

			!isValid -> {
				binding?.emailFieldLayout?.setError(context, R.string.wrong_email_format)
				binding?.deleteAccountButton?.isEnabled = false
			}

			else -> {
				binding?.emailFieldLayout?.isErrorEnabled = false
				binding?.emailFieldLayout?.error = null
				binding?.deleteAccountButton?.isEnabled = true
			}
		}
	}

	private suspend fun requestVerificationCode() {
		val email = binding?.emailField?.text?.toString() ?: return

		withContext(mainScope.coroutineContext) {
			binding?.progressBar?.visible()
			binding?.progressBar?.show()
		}

		val req = ElloRpc.sendMagicLinkRequest(usernameOrEmail = email, action = 0)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				binding?.progressBar?.gone()
				binding?.progressBar?.hide()

				if (error == null && response is TLBizDataRaw) {
					val data = response.readData<ElloRpc.ChangeEmailResponse>()
					@Suppress("NAME_SHADOWING") val email = data?.email

					if (email != null) {
						val args = Bundle()
						args.putBoolean("is_delete_account", true)
						presentFragment(EmailSentFragment(args))
					}
					else {
						binding?.emailFieldLayout?.setError(context, R.string.error_you_entered_an_invalid_email_please_try_again)
					}
				}
				else {
					when(error?.text) {
						"EMAIL_ALREADY_SEND" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.email_already_sent)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }.create()

								dialog.setCancelable(false)
								dialog.setCanceledOnTouchOutside(false)
								dialog.show()
							}
						}

						"NOT_ACCOUNT_EMAIL" -> {
							context?.let {
								val dialog = AlertDialog.Builder(it).setMessage(it.getString(R.string.error_not_account_email)).setPositiveButton(it.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }.create()

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
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		binding = null

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}
	}
}
