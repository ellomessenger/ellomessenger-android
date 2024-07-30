/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.profile

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentVerificationBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.LoginActivity
import org.telegram.ui.LoginActivity.VerificationMode.CHANGE_EMAIL
import org.telegram.ui.LoginActivity.VerificationMode.CHANGE_PASSWORD
import org.telegram.ui.LoginActivity.VerificationMode.DELETE_ACCOUNT
import org.telegram.ui.LoginActivity.VerificationMode.LOGIN
import org.telegram.ui.LoginActivity.VerificationMode.PASSWORD_RESET
import org.telegram.ui.LoginActivity.VerificationMode.REGISTRATION
import org.telegram.ui.LoginActivity.VerificationMode.TRANSFER_OUT_BANK
import org.telegram.ui.LoginActivity.VerificationMode.TRANSFER_OUT_PAYPAL
import org.telegram.ui.profile.wallet.PaymentProcessingFragment
import org.telegram.ui.profile.wallet.WalletFragment
import java.util.concurrent.TimeUnit

class CodeVerificationFragment(args: Bundle?) : BaseFragment(args), CodeVerification {
	private var binding: FragmentVerificationBinding? = null
	private var verificationMode = CHANGE_PASSWORD
	private var email: String? = null
	private var password: String? = null
	private var expirationDate = 0L
	private var countDownTimer: CountDownTimer? = null
	private var bankWithdrawRequisitesId: Long? = null

	override fun onFragmentCreate(): Boolean {
		val verificationModeStr = arguments?.getString(ARG_VERIFICATION_MODE) ?: throw IllegalArgumentException("$ARG_VERIFICATION_MODE param is missing")
		verificationMode = LoginActivity.VerificationMode.valueOf(verificationModeStr)

		email = arguments?.getString(ARG_EMAIL)
		password = arguments?.getString(ARG_NEW_PASSWORD)
		expirationDate = (arguments?.getLong(ARG_EXPIRATION) ?: 0L) * 1000L
		bankWithdrawRequisitesId = arguments?.getLong(WalletFragment.ARG_BANK_WITHDRAW_REQUISITES_ID)?.takeIf { it > 0L }

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setBackgroundColor(Color.TRANSPARENT)
		actionBar?.castShadows = false

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}
				}
			}
		})

		binding = FragmentVerificationBinding.inflate(LayoutInflater.from(context))

		binding?.errorLayout?.invisible()

		val countdownDurationMs = updateCountDown()

		updateResendButton(context, binding?.resendButton!!, this)
		updateCodeInputLayout(binding!!, this)

		startTimer(countdownDurationMs)

		fragmentView = binding?.root

		binding?.openMail?.setOnClickListener {
			val intent = Intent(Intent.ACTION_MAIN)
			intent.addCategory(Intent.CATEGORY_APP_EMAIL)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			context.startActivity(intent)
		}

		return binding?.root
	}

	private fun updateCountDown(): Long {
		return Companion.updateCountDown(expirationDate, binding?.countdownLabel, context)
	}

	override fun onResume() {
		super.onResume()
		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)

		binding?.codeFieldLayout?.input1?.codeDigit?.let {
			it.postDelayed({
				binding?.errorLayout?.gone()
				AndroidUtilities.showKeyboard(it)
			}, 300)
		}
	}

	override fun processCode(code: String) {
		if (code.length != 6) {
			return
		}

		countDownTimer?.cancel()

		binding?.countdownHeader?.gone()
		binding?.countdownLabel?.gone()

		binding?.codeFieldLayout?.input1?.codeDigit?.clearFocus()

		binding?.resendButton?.gone()

		when (verificationMode) {
			REGISTRATION -> {
				// this branch cannot be executed in this fragment
			}

			PASSWORD_RESET -> {
				// this branch cannot be executed in this fragment
			}

			LOGIN -> {
				// this branch cannot be executed in this fragment
			}

			CHANGE_PASSWORD -> {
				verifyChangePassword(code)
			}

			CHANGE_EMAIL -> {
				verifyNewEmailCode(code)
			}

			TRANSFER_OUT_PAYPAL -> {
				verifyTransferOutCode(code = code, mode = WalletFragment.PAYPAL)
			}

			TRANSFER_OUT_BANK -> {
				verifyTransferOutCode(code = code, mode = WalletFragment.BANK)
			}

			DELETE_ACCOUNT -> {
				verifyDeleteAccountCode(code)
			}
		}
	}

	private fun verifyDeleteAccountCode(code: String) {
		val context = context ?: return

		AlertsCreator.createSimpleAlert(context, null, context.getString(R.string.delete_account_dialog_text), context.getString(R.string.confirm), context.getString(R.string.cancel), { _, _ ->
			setControlsEnabled(false)

			binding?.progressBar?.visible()
			binding?.errorLayout?.invisible()

			val req = ElloRpc.deleteAccount(code)

			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					binding?.progressBar?.gone()

					var ok = false

					if (error == null && response is TLRPC.TL_biz_dataRaw) {
						val data = response.readData<ElloRpc.RichVerifyResponse>()
						ok = data?.status == true
					}

					showResponseResult(ok)
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
		}) { _, _ ->
			setControlsEnabled(true)

			binding?.progressBar?.gone()
			binding?.errorLayout?.invisible()
		}.also {
			showDialog(it.create())
		}
	}

	private fun verifyTransferOutCode(code: String, mode: Int) {
		val amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT) ?: throw IllegalArgumentException("${WalletFragment.ARG_AMOUNT} param is missing")
		val walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID) ?: throw IllegalArgumentException("${WalletFragment.ARG_WALLET_ID} param is missing")

		val args = Bundle()
		args.putFloat(WalletFragment.ARG_AMOUNT, amount)
		args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
		args.putString(WalletFragment.ARG_VERIFICATION_CODE, code)
		args.putBoolean(WalletFragment.ARG_IS_TOPUP, false)
		args.putInt(WalletFragment.ARG_MODE, mode)

		email?.let {
			args.putString(WalletFragment.ARG_PAYPAL_EMAIL, it)
		}

		bankWithdrawRequisitesId?.let {
			args.putLong(WalletFragment.ARG_BANK_WITHDRAW_REQUISITES_ID, it)
		}

		presentFragment(PaymentProcessingFragment(args))
	}

	private fun setControlsEnabled(enabled: Boolean) {
		binding?.codeFieldLayout?.codeLayout?.isEnabled = enabled
		binding?.resendButton?.isEnabled = enabled
		actionBar?.backButton?.isEnabled = enabled
	}

	private fun showResponseResult(ok: Boolean) {
		val context = context ?: return

		showResponseResult(binding, context, ok)

		if (ok) {
			binding?.root?.postDelayed({
				setControlsEnabled(true)
				proceedOnSuccess()
			}, 3_000)
		}
		else {
			setControlsEnabled(true)
		}
	}

	private fun verifyChangePassword(code: String) {
		setControlsEnabled(false)

		binding?.progressBar?.visible()
		binding?.errorLayout?.invisible()

		val req = ElloRpc.changePasswordRequest(oldPassword = arguments?.getString(ARG_OLD_PASSWORD) ?: "", newPassword = arguments?.getString(ARG_NEW_PASSWORD) ?: "", code = code)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				binding?.progressBar?.gone()

				var ok = false

				if (error == null && response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.RichVerifyResponse>()
					ok = data?.status == true
				}

				showResponseResult(ok)
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)

	}

	private fun verifyNewEmailCode(code: String) {
		val email = email ?: return

		setControlsEnabled(false)

		binding?.progressBar?.visible()

		binding?.errorLayout?.invisible()

		val req = ElloRpc.changeEmailRequest(newEmail = email, code = code)

		connectionsManager.sendRequest(req, { response, error ->
			AndroidUtilities.runOnUIThread {
				binding?.progressBar?.gone()

				var ok = false

				if (error == null && response is TLRPC.TL_biz_dataRaw) {
					val data = response.readData<ElloRpc.RichVerifyResponse>()
					ok = data?.status == true
				}

				showResponseResult(ok)
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
	}

	private fun proceedOnSuccess() {
		if (verificationMode == DELETE_ACCOUNT) {
			messagesController.performLogout(1)
			return
		}

		finishFragment()
	}

	override fun resendCode() {
		val email = email ?: return
		val password = password ?: return

		when (verificationMode) {
			REGISTRATION, PASSWORD_RESET, LOGIN -> {
				throw IllegalArgumentException("${verificationMode.name} branch cannot be executed in this fragment")
			}

			CHANGE_PASSWORD -> {
				Toast.makeText(context, "TODO: Send request to server to resend code", Toast.LENGTH_SHORT).show()
			}

			TRANSFER_OUT_PAYPAL, TRANSFER_OUT_BANK -> {
				Toast.makeText(context, "TODO: Send request to server to resend code", Toast.LENGTH_SHORT).show()
			}

			CHANGE_EMAIL -> {
				setControlsEnabled(false)

				val req = ElloRpc.verificationCodeRequest(email = email, password = password)

				connectionsManager.sendRequest(req, { response, error ->
					AndroidUtilities.runOnUIThread {
						binding?.progressBar?.gone()

						setControlsEnabled(true)

						if (error == null && response is TLRPC.TL_biz_dataRaw) {
							val data = response.readData<ElloRpc.ForgotPasswordVerifyResponse>()

							if (data?.status == true) {
								expirationDate = data.expirationDate
								val countdownDurationMs = updateCountDown()
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

			DELETE_ACCOUNT -> {
				setControlsEnabled(false)

				val req = ElloRpc.deleteAccountVerificationCodeRequest(email = email, password = password)

				connectionsManager.sendRequest(req, { response, error ->
					AndroidUtilities.runOnUIThread {
						binding?.progressBar?.gone()

						setControlsEnabled(true)

						if (error == null && response is TLRPC.TL_biz_dataRaw) {
							val data = response.readData<ElloRpc.ForgotPasswordVerifyResponse>()

							if (data?.status == true) {
								expirationDate = data.expirationDate
								val countdownDurationMs = updateCountDown()
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
	}

	private fun startTimer(countdownDurationMs: Long) {
		countDownTimer = Companion.startTimer(binding, context, countDownTimer, countdownDurationMs)
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		countDownTimer?.cancel()
		countDownTimer = null
	}

	companion object {
		const val ARG_EMAIL = "email"
		const val ARG_VERIFICATION_MODE = "verificationMode"
		const val ARG_EXPIRATION = "expiration"
		const val ARG_OLD_PASSWORD = "old_pass"
		const val ARG_NEW_PASSWORD = "new_pass"

		fun updateCountDown(expirationDate: Long, countdownLabel: TextView?, context: Context?): Long {
			val countdownDurationMs = expirationDate - System.currentTimeMillis()
			val countdownDurationSeconds = TimeUnit.MILLISECONDS.toSeconds(countdownDurationMs).toInt()

			countdownLabel?.text = context?.resources?.getQuantityString(R.plurals.seconds, countdownDurationSeconds, countdownDurationSeconds)

			return countdownDurationMs
		}

		fun startTimer(binding: FragmentVerificationBinding?, context: Context?, countDownTimer: CountDownTimer?, countdownDurationMs: Long): CountDownTimer {
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

		fun updateResendButton(context: Context, button: TextView, codeVerification: CodeVerification) {
			val resendColoredText = SpannableString(context.getString(R.string.resend_email)).apply {
				setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.brand, null)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			val resendText = TextUtils.expandTemplate(context.getString(R.string.resend_code_template), resendColoredText)

			button.text = resendText

			button.setOnClickListener {
				codeVerification.resendCode()
			}
		}

		fun updateCodeInputLayout(binding: FragmentVerificationBinding, codeVerification: CodeVerification) {
			binding.codeFieldLayout.input1.codeDigit.isFocusableInTouchMode = true
			binding.codeFieldLayout.input1.codeDigit.requestFocus()

			binding.codeFieldLayout.input6.codeDigit.setOnEditorActionListener { _, actionId, _ ->
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					val code = binding.codeFieldLayout.codeLayout.text.toString().trim()

					if (code.isNotEmpty()) {
						codeVerification.processCode(code)
						return@setOnEditorActionListener true
					}
				}

				true
			}

			binding.codeFieldLayout.codeLayout.addTextChangedListener {
				val code = it?.toString()?.trim()?.takeIf { c -> c.isNotEmpty() } ?: return@addTextChangedListener
				codeVerification.processCode(code)
			}
		}

		fun showResponseResult(binding: FragmentVerificationBinding?, context: Context?, ok: Boolean) {
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
			}
			else {
				binding.errorLabel.setTextColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
				binding.errorLabel.text = context.getString(R.string.invalid_code)
				binding.errorLayout.visible()
			}

			binding.codeFieldLayout.codeLayout.setResult(ok)
		}
	}
}
