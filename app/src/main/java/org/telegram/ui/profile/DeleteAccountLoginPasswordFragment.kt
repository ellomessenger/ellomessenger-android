/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.R
import org.telegram.messenger.databinding.LeftoversLoginPasswordFragmentBinding
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC.TL_biz_dataRaw
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LoginActivity

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

		binding?.passwordField?.addTextChangedListener {
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
		val email = binding?.emailField?.text?.toString()

		if (email.isNullOrEmpty()) {
			binding?.deleteAccountButton?.isEnabled = false
			return
		}

		val password = binding?.passwordField?.text?.toString()

		if (password.isNullOrEmpty()) {
			binding?.deleteAccountButton?.isEnabled = false
			return
		}

		binding?.deleteAccountButton?.isEnabled = true
	}

	private suspend fun requestVerificationCode() {
		val email = binding?.emailField?.text?.toString() ?: return
		val password = binding?.passwordField?.text?.toString() ?: return

		withContext(mainScope.coroutineContext) {
			binding?.deleteAccountButton?.isEnabled = false

			binding?.progressBar?.visible()
			binding?.progressBar?.show()
		}

		when (val resp = connectionsManager.performRequest(ElloRpc.deleteAccountVerificationCodeRequest(email = email, password = password))) {
			is TL_biz_dataRaw -> {
				val data = resp.readData<ElloRpc.ForgotPasswordVerifyResponse>()

				withContext(mainScope.coroutineContext) {
					binding?.progressBar?.hide()

					validateFields()

					if (data != null) {
						val args = Bundle()

						args.putString(CodeVerificationFragment.ARG_VERIFICATION_MODE, LoginActivity.VerificationMode.DELETE_ACCOUNT.name)
						args.putString(CodeVerificationFragment.ARG_EMAIL, email)
						args.putString(CodeVerificationFragment.ARG_NEW_PASSWORD, password)
						args.putLong(CodeVerificationFragment.ARG_EXPIRATION, data.expirationDate)

						presentFragment(CodeVerificationFragment(args), true)
					}
					else {
						Toast.makeText(context, R.string.failed_to_send_verification_code, Toast.LENGTH_SHORT).show()
					}
				}
			}

			is TL_error -> {
				withContext(mainScope.coroutineContext) {
					binding?.progressBar?.hide()
					Toast.makeText(context, resp.text, Toast.LENGTH_SHORT).show()
					validateFields()
				}
			}

			else -> {
				withContext(mainScope.coroutineContext) {
					binding?.progressBar?.hide()
					Toast.makeText(context, R.string.failed_to_send_verification_code, Toast.LENGTH_SHORT).show()
					validateFields()
				}
			}
		}
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
