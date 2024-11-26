package org.telegram.ui.profile

import android.content.Context
import android.os.CountDownTimer
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import java.util.concurrent.TimeUnit

class ChangePasswordFragment : BaseFragment(), NotificationCenter.NotificationCenterDelegate, CodeVerification  {
	private var email: String? = null
	private var binding: FragmentChangePasswordBinding? = null
	private var countDownTimer: CountDownTimer? = null
	private var debounceJob: Job? = null

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

		binding?.currentPasswordField?.addTextChangedListener {
			debounceJob?.cancel()
			debounceJob = CoroutineScope(Dispatchers.Main).launch {
				delay(3000L)

				val password = it.toString()
				if (password.length >= 6) {
					changePassword()
				}
			}
		}

		binding?.changePassword?.setOnClickListener {
			val code = binding?.codeFieldLayout?.codeLayout?.text?.toString()?.trim()
			processCode(code!!)
		}

		updateResendButton(context, binding?.resendButton!!)

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

		val req = ElloRpc.verificationCodeRequest(email = email ?: "")

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				binding?.progressBar?.gone()

				var ok = false

				if (error == null && response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.ForgotPasswordVerifyResponse>()

					if (data?.status == true) {
						ok = true
						val countdownDurationMs = updateCountDown(data.expirationDate * 1000L)
						startTimer(countdownDurationMs)

						val code = binding?.codeFieldLayout?.codeLayout?.text?.toString()?.trim()
						processCode(code!!)

						if (debounceJob?.isActive == true) {
							debounceJob?.cancel()
						}
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

	private fun updateCountDown(date: Long): Long {
		return updateCountDown(date, binding?.countdownLabel)
	}

	private fun startTimer(countdownDurationMs: Long) {
		countDownTimer = startTimer(countDownTimer, countdownDurationMs)
	}

	private fun updateCountDown(expirationDate: Long, countdownLabel: TextView?): Long {
		val countdownDurationMs = expirationDate - System.currentTimeMillis()
		val countdownDurationSeconds = TimeUnit.MILLISECONDS.toSeconds(countdownDurationMs).toInt()

		countdownLabel?.text = context?.resources?.getQuantityString(R.plurals.seconds, countdownDurationSeconds, countdownDurationSeconds)

		return countdownDurationMs
	}

	private fun startTimer(countDownTimer: CountDownTimer?, countdownDurationMs: Long): CountDownTimer {
		binding?.countdownLabel?.visible()
		binding?.countdownHeader?.visible()

		binding?.resendButton?.gone()

		countDownTimer?.cancel()

		return object : CountDownTimer(countdownDurationMs, TimeUnit.SECONDS.toMillis(1)) {
			override fun onTick(millisUntilFinished: Long) {
				val secondsLeft = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished).toInt()
				binding?.countdownLabel?.text = context?.resources?.getQuantityString(R.plurals.seconds, secondsLeft, secondsLeft)
			}

			override fun onFinish() {
				binding?.countdownLabel?.gone()
				binding?.countdownHeader?.gone()

				binding?.resendButton?.visible()
			}
		}.apply {
			start()
		}
	}

	private fun updateResendButton(context: Context, button: TextView) {
		val resendColoredText = SpannableString(context.getString(R.string.resend_email)).apply {
			setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}

		val resendText = TextUtils.expandTemplate(context.getString(R.string.resend_code_template), resendColoredText)

		button.text = resendText

		button.setOnClickListener {
			resendCode()
		}
	}

	private fun showResponseResult(ok: Boolean, errorText: String?) {
		val context = context ?: return

		showResponseResult(binding, context, ok, errorText)

		if (ok) {
			binding?.root?.postDelayed({
				setControlsEnabled(true)
				finishFragment()
			}, 3_000)
		}
		else {
			setControlsEnabled(true)
		}
	}

	private fun showResponseResult(binding: FragmentChangePasswordBinding?, context: Context?, ok: Boolean, errorText: String?) {
		if (binding == null) {
			return
		}

		if (context == null) {
			return
		}

		if (ok) {
			binding.errorLabel.setTextColor(ResourcesCompat.getColor(context.resources, R.color.green, null))
			binding.errorLabel.text = context.getString(R.string.code_verified)
			binding.errorLayout.visible()
			binding.countdownHeader.gone()
			binding.countdownLabel.gone()
		}
		else {
			binding.errorLabel.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
			when(errorText) {
				INVALID_CODE -> binding.errorLabel.text = context.getString(R.string.invalid_code)
				INVALID_PASSWORD -> binding.errorLabel.text = context.getString(R.string.invalid_password)
			}
			binding.errorLayout.visible()
		}

		binding.codeFieldLayout.codeLayout.setResult(ok)
	}

	override fun resendCode() {
		setControlsEnabled(false)

		val req = email?.let {
			ElloRpc.verificationCodeRequest(email = it, password = binding?.currentPasswordField?.text.toString().trim())
		}

		if (req != null) {
			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					binding?.progressBar?.gone()

					setControlsEnabled(true)

					if (error == null && response is TLRPC.TL_biz_dataRaw) {
						val data = response.readData<ElloRpc.ForgotPasswordVerifyResponse>()

						if (data?.status == true) {
							val countdownDurationMs = updateCountDown(data.expirationDate * 1000L)
							startTimer(countdownDurationMs)
						}
						else {
							Toast.makeText(context, context?.getString(R.string.error_format, data?.message), Toast.LENGTH_LONG).show()
						}
					}
					else {
						Toast.makeText(context, context?.getString(R.string.error_format, error?.text), Toast.LENGTH_LONG).show()
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}
	}

	override fun processCode(code: String) {
		val context = context ?: return

		if (code.length != 6) {
			return
		}

		val oldPass = binding?.currentPasswordField?.text?.toString()?.trim()
		val newPass = binding?.newPasswordField?.text?.toString()?.trim()

		if (oldPass.isNullOrEmpty()) {
			binding?.currentPasswordFieldLayout?.error = context.getString(R.string.password_is_empty)
			return
		}

		if (newPass.isNullOrEmpty()) {
			binding?.newPasswordFieldLayout?.error = context.getString(R.string.password_is_empty)
			return
		}

		if (!newPass.validatePassword()) {
			binding?.newPasswordFieldLayout?.error = context.getString(R.string.password_fail_rules)
			return
		}

		if (oldPass == newPass) {
			binding?.newPasswordFieldLayout?.error = context.getString(R.string.passwords_the_same)
			return
		}

		val confirmNewPassword = binding?.confirmNewPasswordField?.text?.toString()?.trim()

		if (confirmNewPassword.isNullOrEmpty()) {
			binding?.confirmNewPasswordFieldLayout?.error = context.getString(R.string.password_is_empty)
			return
		}

		if (newPass != confirmNewPassword) {
			binding?.confirmNewPasswordFieldLayout?.error = context.getString(R.string.PasswordDoNotMatch)
			return
		}

		setControlsEnabled(false)

		binding?.progressBar?.visible()

		AndroidUtilities.hideKeyboard(binding?.root)

		setControlsEnabled(false)

		val req = ElloRpc.changePasswordRequest(oldPassword = oldPass, newPassword = newPass, code = code)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				binding?.progressBar?.gone()

				var ok = false

				if (error == null && response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.RichVerifyResponse>()
					ok = data?.status == true
				}

				showResponseResult(ok, error?.text)
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
	}

	override fun onResume() {
		super.onResume()
		binding?.codeFieldLayout?.input1?.codeDigit?.let {
			binding?.errorLayout?.gone()
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
		countDownTimer?.cancel()
		countDownTimer = null
		if (debounceJob?.isActive == true) {
			debounceJob?.cancel()
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.userInfoDidLoad) {
			val userInfo = args[1] as UserFull
			email = userInfo.email
		}
	}

	companion object {
		private const val INVALID_PASSWORD = "password is not matched"
		private const val INVALID_CODE = "code mismatch"
	}

}
