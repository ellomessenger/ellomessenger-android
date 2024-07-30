package org.telegram.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentChangePasswordBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.validatePassword
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.UserFull
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LoginActivity

class ChangePasswordFragment : BaseFragment(), NotificationCenter.NotificationCenterDelegate {
	private var email: String? = null
	private var binding: FragmentChangePasswordBinding? = null

	override fun onFragmentCreate(): Boolean {
		notificationCenter.addObserver(this, NotificationCenter.userInfoDidLoad)
		messagesController.loadUserInfo(userConfig.getCurrentUser(), true, classGuid)

		super.onFragmentCreate()
		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.ChangePassword))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}
				}
			}
		})

		binding = FragmentChangePasswordBinding.inflate(LayoutInflater.from(context))

		binding?.currentPasswordField?.doAfterTextChanged {
			binding?.currentPasswordFieldLayout?.isErrorEnabled = false
			binding?.currentPasswordField?.error = null
		}

		binding?.newPasswordField?.doAfterTextChanged {
			binding?.newPasswordFieldLayout?.isErrorEnabled = false
			binding?.newPasswordField?.error = null
		}

		binding?.confirmNewPasswordField?.doAfterTextChanged {
			binding?.confirmNewPasswordFieldLayout?.isErrorEnabled = false
			binding?.confirmNewPasswordField?.error = null
		}

		binding?.changePassword?.setOnClickListener {
			changePassword()
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun setControlsEnabled(enabled: Boolean) {
		binding?.currentPasswordField?.isEnabled = enabled
		binding?.newPasswordField?.isEnabled = enabled
		binding?.confirmNewPasswordField?.isEnabled = enabled
		binding?.changePassword?.isEnabled = enabled
	}

	private fun changePassword() {
		val context = context ?: return
		val oldPassword = binding?.currentPasswordField?.text?.toString()?.trim()

		if (oldPassword.isNullOrEmpty()) {
			binding?.currentPasswordFieldLayout?.error = context.getString(R.string.password_is_empty)
			return
		}

		val newPassword = binding?.newPasswordField?.text?.toString()?.trim()

		if (newPassword.isNullOrEmpty()) {
			binding?.newPasswordFieldLayout?.error = context.getString(R.string.password_is_empty)
			return
		}

		if (!newPassword.validatePassword()) {
			binding?.newPasswordFieldLayout?.error = context.getString(R.string.password_fail_rules)
			return
		}

		if (oldPassword == newPassword) {
			binding?.newPasswordFieldLayout?.error = context.getString(R.string.passwords_the_same)
			return
		}

		val confirmNewPassword = binding?.confirmNewPasswordField?.text?.toString()?.trim()

		if (confirmNewPassword.isNullOrEmpty()) {
			binding?.confirmNewPasswordFieldLayout?.error = context.getString(R.string.password_is_empty)
			return
		}

		if (newPassword != confirmNewPassword) {
			binding?.confirmNewPasswordFieldLayout?.error = context.getString(R.string.PasswordDoNotMatch)
			return
		}

		setControlsEnabled(false)

		binding?.progressBar?.visible()

		AndroidUtilities.hideKeyboard(binding?.root)

		val req = ElloRpc.verificationCodeRequest(email = email ?: "", password = oldPassword)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				binding?.progressBar?.gone()

				var ok = false

				if (error == null && response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.ForgotPasswordVerifyResponse>()

					if (data?.status == true) {
						ok = true
						verifyNewPassword(data.email ?: "", data.expirationDate, oldPassword, newPassword)
					}
				}
				else {
					Toast.makeText(context, "${error?.text}", Toast.LENGTH_LONG).show()
				}

				if (!ok) {
					needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), error?.text)
					setControlsEnabled(true)
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	private fun verifyNewPassword(email: String, expirationDate: Long, oldPass: String, newPass: String) {
		val args = Bundle()

		args.putString(CodeVerificationFragment.ARG_VERIFICATION_MODE, LoginActivity.VerificationMode.CHANGE_PASSWORD.name)
		args.putString(CodeVerificationFragment.ARG_EMAIL, email)
		args.putLong(CodeVerificationFragment.ARG_EXPIRATION, expirationDate)
		args.putString(CodeVerificationFragment.ARG_OLD_PASSWORD, oldPass)
		args.putString(CodeVerificationFragment.ARG_NEW_PASSWORD, newPass)

		presentFragment(CodeVerificationFragment(args), true)
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.userInfoDidLoad) {
			val userInfo = args[1] as UserFull
			email = userInfo.email
		}
	}
}
