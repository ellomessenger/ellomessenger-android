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
import org.telegram.messenger.utils.*
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LoginActivity

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
			val password = binding?.passwordField?.text?.toString() ?: return@setOnClickListener
			requestNewEmail(newEmail = email, password = password)
		}

		binding?.emailChangeField?.doOnTextChanged { _, _, _, _ ->
			checkButtonStatus()
			binding?.emailChangeFieldLayout?.resetError()
		}

		binding?.passwordField?.doOnTextChanged { _, _, _, _ ->
			checkButtonStatus()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun checkButtonStatus() {
		var valid = binding?.emailChangeField?.text?.toString()?.validateEmail() ?: false

		if (valid) {
			valid = !binding?.passwordField?.text?.toString().isNullOrEmpty()
		}

		binding?.changeEmailButton?.isEnabled = valid
	}

	private fun requestNewEmail(newEmail: String, password: String) {
		binding?.progressBar?.visible()

		if (newEmail.validateEmail()) {
			val req = ElloRpc.verificationCodeRequest(email = newEmail, password = password)

			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					binding?.progressBar?.gone()

					if (error == null && response is TLRPC.TL_biz_dataRaw) {
						val data = response.readData<ElloRpc.ForgotPasswordVerifyResponse>()

						if (data?.status == true) {
							val args = Bundle()

							args.putString(CodeVerificationFragment.ARG_VERIFICATION_MODE, LoginActivity.VerificationMode.CHANGE_EMAIL.name)
							args.putString(CodeVerificationFragment.ARG_EMAIL, data.email)
							args.putString(CodeVerificationFragment.ARG_NEW_PASSWORD, password)
							args.putLong(CodeVerificationFragment.ARG_EXPIRATION, data.expirationDate)

							presentFragment(CodeVerificationFragment(args), true)

						}
						else {
							binding?.errorLayout?.visible()
							binding?.errorText?.setText(R.string.error_you_entered_an_invalid_email_please_try_again)
							binding?.emailChangeFieldLayout?.invisible()
						}
					}
					else {
						binding?.emailChangeFieldLayout?.setError(context, R.string.msg_invalid_email)
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
