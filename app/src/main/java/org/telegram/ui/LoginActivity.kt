/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.annotation.Keep
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.telegram.PhoneFormat.PhoneFormat
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.databinding.FragmentCreatePasswordBinding
import org.telegram.messenger.databinding.FragmentLoginModeBinding
import org.telegram.messenger.databinding.FragmentPasswordResetBinding
import org.telegram.messenger.databinding.FragmentRegBirthdayBinding
import org.telegram.messenger.databinding.FragmentRegCredentialsBinding
import org.telegram.messenger.databinding.FragmentVerificationBinding
import org.telegram.messenger.utils.ClickableString
import org.telegram.messenger.utils.LatinLettersFilter
import org.telegram.messenger.utils.UsernameFilter
import org.telegram.messenger.utils.capitalizeFirstLetter
import org.telegram.messenger.utils.formatBirthday
import org.telegram.messenger.utils.formatDate
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.parseBirthday
import org.telegram.messenger.utils.validateEmail
import org.telegram.messenger.utils.validatePassword
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.CountriesDataSource
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_auth_authorization
import org.telegram.tgnet.TLRPC.TL_biz_dataRaw
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Adapters.CountriesAdapter
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.SlideView
import org.telegram.ui.profile.BlockedAccountFragment
import org.telegram.ui.profile.CodeVerification
import org.telegram.ui.profile.CodeVerificationFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

class LoginActivity : BaseFragment {
	enum class LoginMode {
		PERSONAL, BUSINESS
	}

	enum class AccountType {
		PUBLIC, PRIVATE
	}

	enum class VerificationMode {
		REGISTRATION, PASSWORD_RESET, CHANGE_PASSWORD, CHANGE_EMAIL, TRANSFER_OUT_PAYPAL, TRANSFER_OUT_BANK, LOGIN, DELETE_ACCOUNT
	}

	enum class UsernameValidationResult {
		AVAILABLE, RESERVED, UNAVAILABLE
	}

	enum class UserStatus {
		BLOCKED, DELETED
	}

	@Retention(AnnotationRetention.SOURCE)
	@IntDef(VIEW_LOGIN_MODE, VIEW_REGISTRATION_CREDENTIALS, VIEW_REGISTRATION_BIRTHDAY, VIEW_VERIFICATION, VIEW_PASSWORD_RESET, VIEW_NEW_PASSWORD)
	private annotation class ViewNumber

	@ViewNumber
	private var currentViewNum = 0

	@ViewNumber
	private var prevViewNum = 0

	private val views = arrayOfNulls<SlideView>(15)
	private var newAccount = false
	private var progressRequestId = 0
	private var sizeNotifierFrameLayout: SizeNotifierFrameLayout? = null
	private var radialProgressView: RadialProgressView? = null
	private var slideViewsContainer: FrameLayout? = null
	private var introView: View? = null
	private var animationFinishCallback: Runnable? = null
	private var progressBar: ProgressBar? = null
	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val mainScope = MainScope()
	private var usernameVerificationRequestId = -1

	private class ProgressView(context: Context) : View(context) {
		private val path = Path()
		private val rect = RectF()
		private val boundsRect = RectF()
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val paint2 = Paint(Paint.ANTI_ALIAS_FLAG)
		private var startTime: Long = 0
		private var duration: Long = 0
		private var radius = 0f

		var isProgressAnimationRunning = false
			private set

		init {
			paint.color = context.getColor(R.color.light_background)
			paint2.color = context.getColor(R.color.brand)
		}

		@Keep
		fun startProgressAnimation(duration: Long) {
			isProgressAnimationRunning = true
			this.duration = duration
			startTime = System.currentTimeMillis()
			invalidate()
		}

		@Keep
		fun resetProgressAnimation() {
			duration = 0
			startTime = 0
			isProgressAnimationRunning = false
			invalidate()
		}

		override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
			path.rewind()
			radius = h / 2f
			boundsRect[0f, 0f, w.toFloat()] = h.toFloat()
			rect.set(boundsRect)
			path.addRoundRect(boundsRect, radius, radius, Path.Direction.CW)
		}

		override fun onDraw(canvas: Canvas) {
			val progress: Float = if (duration > 0) {
				min(1f, (System.currentTimeMillis() - startTime) / duration.toFloat())
			}
			else {
				0f
			}

			canvas.clipPath(path)
			canvas.drawRoundRect(boundsRect, radius, radius, paint)

			rect.right = boundsRect.right * progress

			canvas.drawRoundRect(rect, radius, radius, paint2)

			if (isProgressAnimationRunning && duration > 0 && progress < 1f) {
				postInvalidateOnAnimation()
			}
		}
	}

	constructor() : super()

	constructor(account: Int) : super() {
		currentAccount = account
		newAccount = true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		if (ioScope.isActive) {
			ioScope.cancel()
		}

		if (mainScope.isActive) {
			mainScope.cancel()
		}

		views.forEach {
			it?.onDestroyActivity()
		}
	}

	fun setReferralCode(code: String?) {
		(views[VIEW_REGISTRATION_CREDENTIALS] as? RegistrationCredentialsView)?.setReferralCode(code)
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))
		actionBar?.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), false)
		actionBar?.setItemsBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), true)
		actionBar?.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), false)
		actionBar?.setItemsColor(ResourcesCompat.getColor(context.resources, R.color.brand, null), true)
		actionBar?.setTitleColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		actionBar?.setSubtitleColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

		actionBar?.castShadows = false

		actionBar?.setAddToContainer(true)

		val menu = actionBar?.createMenu()
		menu?.addItem(id = help, icon = R.drawable.support)

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						if (onBackPressed()) {
							finishFragment()
						}
					}

					help -> {
						Browser.openUrl(parentActivity, BuildConfig.SUPPORT_URL)
					}
				}
			}
		})

		sizeNotifierFrameLayout = object : SizeNotifierFrameLayout(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				if (Bulletin.visibleBulletin?.isShowing == true) {
					super.onMeasure(widthMeasureSpec, heightMeasureSpec)
				}

				val statusBarHeight = if (AndroidUtilities.isTablet()) 0 else AndroidUtilities.statusBarHeight

				radialProgressView?.updateLayoutParams<MarginLayoutParams> {
					topMargin = AndroidUtilities.dp(16f) + statusBarHeight
				}

				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}
		}

		fragmentView = sizeNotifierFrameLayout

		val scrollView = ScrollView(context)
		scrollView.isFillViewport = true

		sizeNotifierFrameLayout?.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		slideViewsContainer = object : FrameLayout(context) {
			override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
				super.onLayout(changed, left, top, right, bottom)

				views.filterNotNull().forEach { slideView ->
					val params = slideView.layoutParams as MarginLayoutParams
					val childBottom = height + AndroidUtilities.dp(16f)
					slideView.layout(params.leftMargin, params.topMargin, width - params.rightMargin, childBottom)
				}
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)

				val width = measuredWidth
				val height = measuredHeight

				views.filterNotNull().forEach { slideView ->
					val params = slideView.layoutParams as MarginLayoutParams
					val childHeight = height - params.topMargin + AndroidUtilities.dp(16f)
					slideView.measure(MeasureSpec.makeMeasureSpec(width - params.rightMargin - params.leftMargin, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY))
				}
			}
		}

		scrollView.addView(slideViewsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))

		views[VIEW_LOGIN_MODE] = LoginModeView(context)
		views[VIEW_REGISTRATION_CREDENTIALS] = RegistrationCredentialsView(context)
		views[VIEW_REGISTRATION_BIRTHDAY] = RegistrationBirthdayView(context)
		views[VIEW_VERIFICATION] = CodeVerificationView(context)
		views[VIEW_PASSWORD_RESET] = ResetPasswordView(context)
		views[VIEW_NEW_PASSWORD] = CreatePasswordView(context)

		views.forEachIndexed { index, slideView ->
			if (slideView == null) {
				return@forEachIndexed
			}

			slideView.visibility = if (index == 0) View.VISIBLE else View.GONE
			slideViewsContainer?.addView(slideView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.CENTER, if (AndroidUtilities.isTablet()) 26.toFloat() else 18.toFloat(), 0f, if (AndroidUtilities.isTablet()) 26.toFloat() else 18.toFloat(), 0f))
		}

		radialProgressView = RadialProgressView(context)
		radialProgressView?.setSize(AndroidUtilities.dp(20f))
		radialProgressView?.alpha = 0f
		radialProgressView?.scaleX = 0.1f
		radialProgressView?.scaleY = 0.1f

		sizeNotifierFrameLayout?.addView(radialProgressView, LayoutHelper.createFrame(32, 32f, Gravity.RIGHT or Gravity.TOP, 0f, 16f, 16f, 0f))

		for (a in views.indices) {
			val v = views[a] ?: continue

			if (currentViewNum == a) {
				actionBar?.backButtonDrawable = if (v.needBackButton() || newAccount /* || activityMode == MODE_CHANGE_PHONE_NUMBER */) ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null) else null
				// backButtonView?.visibility = if (v.needBackButton() || newAccount || activityMode == MODE_CHANGE_PHONE_NUMBER) View.VISIBLE else View.GONE

				v.visibility = View.VISIBLE
				v.onShow()
			}
			else {
				if (v.visibility != View.GONE) {
					v.visibility = View.GONE
					v.onHide()
				}
			}
		}

		progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
		progressBar?.indeterminateTintList = ColorStateList.valueOf(ResourcesCompat.getColor(context.resources, R.color.brand, null))
		progressBar?.visibility = View.GONE
		progressBar?.isIndeterminate = true

		sizeNotifierFrameLayout?.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0f, 0f, 0f, 0f))

		return fragmentView
	}

	override fun onPause() {
		super.onPause()

		if (newAccount) {
			connectionsManager.setAppPaused(value = true, byScreenState = false)
		}
	}

	override fun onResume() {
		super.onResume()

		if (newAccount) {
			connectionsManager.setAppPaused(value = false, byScreenState = false)
		}

		AndroidUtilities.requestAdjustResize(parentActivity, classGuid)

		fragmentView?.requestLayout()

		views[currentViewNum]?.onShow()
	}

	override fun hasForceLightStatusBar(): Boolean {
		return true
	}

	//MARK: Removed returning to the login screen after resending a request for a verification code
	private fun popBackWithNextRepeat() {
		onBackPressed()

		mainScope.launch {
			delay(300)

			val view = views[currentViewNum]

			view?.onNextPressed(view.code)
		}
	}

	override fun onBackPressed(): Boolean {
		AndroidUtilities.hideKeyboard(fragmentView?.findFocus())

		if (currentViewNum == VIEW_LOGIN_MODE) {
			for (view in views) {
				view?.onDestroyActivity()
			}

//			if (parentActivity == null) {
//				presentFragment(IntroActivity(), true)
//			}
//			else if ((parentActivity as? LaunchActivity)?.actionBarLayout?.fragmentsStack?.size == 1) {
//				presentFragment(IntroActivity(), true)
//			}

			return true
		}

		if (views[currentViewNum]?.onBackPressed(true) == true) {
			when (currentViewNum) {
				VIEW_PASSWORD_RESET -> {
					setPage(VIEW_LOGIN_MODE, true, null, true)
				}

				VIEW_VERIFICATION -> {
					setPage(prevViewNum, true, null, true)
				}

				else -> {
					setPage(currentViewNum - 1, true, null, true)
				}
			}
		}

		return false
	}

	private fun needShowProgress(requestId: Int) {
		progressRequestId = requestId
		progressBar?.visible()
	}

	private fun needShowProgress() {
		progressBar?.visible()
	}

	private fun needHideProgress(cancel: Boolean) {
		if (progressRequestId != 0) {
			if (cancel) {
				connectionsManager.cancelRequest(progressRequestId, true)
			}

			progressRequestId = 0
		}

		progressBar?.gone()
	}

	private fun setPage(@ViewNumber page: Int, animated: Boolean, params: Bundle?, back: Boolean) {
		val context = context ?: return

		if (animated) {
			val outView = views[currentViewNum] ?: views.firstOrNull() ?: throw IllegalArgumentException("View not found")
			val newView = views[page] ?: throw IllegalArgumentException("View not found")

			prevViewNum = currentViewNum
			currentViewNum = page

			actionBar?.backButtonDrawable = if (newView.needBackButton() || newAccount) ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null) else null

			newView.setParams(params, false)

			setParentActivityTitle(newView.headerName)

			newView.onShow()
			newView.x = (if (back) -AndroidUtilities.displaySize.x else AndroidUtilities.displaySize.x).toFloat()
			newView.visibility = View.VISIBLE

			val pagesAnimation = AnimatorSet()

			pagesAnimation.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					outView.visibility = View.GONE
					outView.x = 0f
				}
			})

			pagesAnimation.playTogether(ObjectAnimator.ofFloat(outView, View.TRANSLATION_X, if (back) AndroidUtilities.displaySize.x.toFloat() else -AndroidUtilities.displaySize.x.toFloat()), ObjectAnimator.ofFloat(newView, View.TRANSLATION_X, 0f))
			pagesAnimation.duration = 300
			pagesAnimation.interpolator = AccelerateDecelerateInterpolator()
			pagesAnimation.start()
		}
		else {
			actionBar?.backButtonDrawable = if (views[page]!!.needBackButton() || newAccount) ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null) else null

			views[currentViewNum]?.visibility = View.GONE

			prevViewNum = currentViewNum
			currentViewNum = page

			views[page]?.setParams(params, false)
			views[page]?.visibility = View.VISIBLE

			setParentActivityTitle(views[page]!!.headerName)

			views[page]?.onShow()
		}
	}

	private fun needFinishActivity(afterSignup: Boolean, showSetPasswordConfirm: Boolean, otherwiseRelogin: Int) {
		parentActivity?.window?.let {
			AndroidUtilities.setLightStatusBar(it, false)
		}

		if (parentActivity is LaunchActivity) {
			if (newAccount) {
				newAccount = false

				(parentActivity as LaunchActivity).switchToAccount(currentAccount)

				finishFragment()
			}
			else {
				if (afterSignup && showSetPasswordConfirm) {
					val twoStepVerification = TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)
					twoStepVerification.setBlockingAlert(otherwiseRelogin)
					twoStepVerification.setFromRegistration(true)
					presentFragment(twoStepVerification, true)
				}
				else {
					val args = Bundle()
					args.putBoolean("afterSignup", afterSignup)

					val dialogsActivity = DialogsActivity(args)

					presentFragment(dialogsActivity, true)
				}

				notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)

				LocaleController.getInstance().loadRemoteLanguages(currentAccount)
			}
		}
		else if (parentActivity is ExternalActionActivity) {
			(parentActivity as ExternalActionActivity).onFinishLogin()
		}
	}

	private fun onLoginCodeSent(verificationMode: VerificationMode, email: String, password: String?, expiration: Long) {
		val params = Bundle()
		params.putString("username", email)
		params.putString("password", password)
		params.putString("email", email)
		params.putLong("expiration", expiration)
		params.putString("loginMode", LoginMode.PERSONAL.name) // stub
		params.putString("verificationMode", verificationMode.name)
		params.putString("accountType", AccountType.PUBLIC.name) // stub
		setPage(VIEW_VERIFICATION, true, params, false)
	}

	private fun onSignupCodeReceived(username: String, password: String, email: String, expiration: Long, loginMode: LoginMode, verificationMode: VerificationMode, accountType: AccountType) {
		val params = Bundle()
		params.putString("username", username)
		params.putString("password", password)
		params.putString("email", email)
		params.putLong("expiration", expiration)
		params.putString("loginMode", loginMode.name)
		params.putString("verificationMode", verificationMode.name)
		params.putString("accountType", accountType.name)

		setPage(VIEW_VERIFICATION, true, params, false)
	}

	private fun onPasswordResetCodeReceived(email: String, expiration: Long) {
		val params = Bundle()
		params.putString("username", email)
		params.putString("email", email)
		params.putLong("expiration", expiration)
		params.putString("loginMode", LoginMode.PERSONAL.name) // stub
		params.putString("verificationMode", VerificationMode.PASSWORD_RESET.name) // stub
		params.putString("accountType", AccountType.PUBLIC.name) // stub

		setPage(VIEW_VERIFICATION, true, params, false)
	}

	private fun openNewPasswordScreen(email: String, code: String) {
		val params = Bundle()
		params.putString("email", email)
		params.putString("code", code)
		setPage(VIEW_NEW_PASSWORD, true, params, false)
	}

	private fun onPasswordResetFinished() {
		setPage(VIEW_LOGIN_MODE, true, null, false)
	}

	private fun forgotPassword(email: String?) {
		val params = Bundle()

		if (!email.isNullOrEmpty()) {
			params.putString("email", email)
		}

		setPage(VIEW_PASSWORD_RESET, true, params, false)
	}

	private fun onAuthSuccess(res: TL_auth_authorization, afterSignup: Boolean) {
		messagesController.cleanup()
		connectionsManager.setUserId(res.user.id)
		userConfig.clearConfig()
		userConfig.setCurrentUser(res.user)
		userConfig.saveConfig(true)
		messagesStorage.cleanup(true)

		val users = listOf(res.user)

		messagesStorage.putUsersAndChats(users, null, true, true)
		messagesController.putUser(res.user, false)
		contactsController.checkAppAccount()
		messagesController.checkPromoInfo(true)
		connectionsManager.updateDcSettings()

		if (afterSignup) {
			messagesController.putDialogsEndReachedAfterRegistration()
		}

		mediaDataController.loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, true)

		needFinishActivity(afterSignup, res.setup_password_required, res.otherwise_relogin_days)

		messagesController.loadFullUser(messagesController.getUser(res.user.id), classGuid, true)

		walletHelper.loadWallet()
	}

	private fun goToRegistrationCredentials(mode: LoginMode, animate: Boolean = true) {
		val params = Bundle()
		params.putString("mode", mode.name)
		setPage(VIEW_REGISTRATION_CREDENTIALS, animate, params, false)
	}

	private fun goToBirthdayGenderCountry(type: AccountType, username: String, password: String, email: String, referralCode: String?, animate: Boolean = true) {
		val params = Bundle()
		params.putString("username", username)
		params.putString("password", password)
		params.putString("email", email)
		params.putString("type", type.name)

		referralCode?.let {
			params.putString("referral_code", it)
		}

		setPage(VIEW_REGISTRATION_BIRTHDAY, animate, params, false)
	}

	private inner class CodeVerificationView(context: Context) : SlideView(context), CodeVerification {
		private lateinit var binding: FragmentVerificationBinding
		private lateinit var loginMode: LoginMode
		private lateinit var verificationMode: VerificationMode
		private lateinit var accountType: AccountType
		private lateinit var username: String
		private lateinit var password: String
		private lateinit var email: String
		private var expirationDate = 0L
		private var countDownTimer: CountDownTimer? = null
		private val reportTime = 120000L

		override fun needBackButton(): Boolean {
			return true
		}

		override fun setParams(params: Bundle?, restore: Boolean) {
			if (params != null) {
				val loginModeStr = params.getString("loginMode") ?: throw IllegalArgumentException("loginMode param is missing")

				loginMode = LoginMode.valueOf(loginModeStr)

				val verificationModeStr = params.getString("verificationMode") ?: throw IllegalArgumentException("verificationMode param is missing")
				verificationMode = VerificationMode.valueOf(verificationModeStr)

				val accountTypeStr = params.getString("accountType") ?: throw IllegalArgumentException("type param is missing")
				accountType = AccountType.valueOf(accountTypeStr)

				username = params.getString("username") ?: throw IllegalArgumentException("username param is missing")

				if (verificationMode != VerificationMode.PASSWORD_RESET) {
					password = params.getString("password") ?: throw IllegalArgumentException("password param is missing")
				}

				email = params.getString("email") ?: throw IllegalArgumentException("email param is missing")
				expirationDate = params.getLong("expiration") * 1000L
			}

			removeAllViews()

			binding = FragmentVerificationBinding.inflate(LayoutInflater.from(context), this, false)
			binding.errorLayout.invisible()

			val countdownDurationMs = CodeVerificationFragment.updateCountDown(expirationDate, binding.countdownLabel, context)

			CodeVerificationFragment.updateResendButton(context, binding.resendButton, this@CodeVerificationView)
			CodeVerificationFragment.updateCodeInputLayout(binding, this@CodeVerificationView)

			startTimer(countdownDurationMs)

			addView(binding.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))

			binding.openMail.setOnClickListener {
				val intent = Intent(Intent.ACTION_MAIN)
				intent.addCategory(Intent.CATEGORY_APP_EMAIL)
				intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
				context.startActivity(intent)
			}
		}

		override fun onShow() {
			super.onShow()

			postDelayed({
				if (this::binding.isInitialized) {
					showKeyboard(binding.codeFieldLayout.input1.codeDigit)
				}
			}, 300)
		}

		override fun processCode(code: String) {
			if (code.length != 6) {
				return
			}

			countDownTimer?.cancel()

			binding.countdownHeader.gone()
			binding.countdownLabel.gone()

			binding.resendButton.gone()

			when (verificationMode) {
				VerificationMode.REGISTRATION, VerificationMode.LOGIN -> {
					onNextPressed(code)
				}

				VerificationMode.PASSWORD_RESET -> {
					openNewPasswordScreen(email, code)
				}

				else -> {
					// unused in this flow
				}
			}
		}

		private fun setControlsEnabled(enabled: Boolean) {
			binding.codeFieldLayout.codeLayout.isEnabled = enabled
			binding.resendButton.isEnabled = enabled
			actionBar?.backButton?.isEnabled = enabled
		}

		override fun onNextPressed(code: String?) {
			if (code.isNullOrEmpty()) {
				return
			}

			setControlsEnabled(false)

			binding.errorLayout.invisible()

			val req = when (verificationMode) {
				VerificationMode.REGISTRATION -> ElloRpc.verifySignupRequest(username, code)
				VerificationMode.LOGIN -> ElloRpc.verifyLoginRequest(username, code)
				else -> ElloRpc.verifyRequest(username, code)
			}

			val reqId = connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					needHideProgress(cancel = false)

					var ok = false
					var errorText = error?.text
					val auth: TL_auth_authorization?

					if (error == null && response is TL_biz_dataRaw) {
						if (verificationMode == VerificationMode.REGISTRATION || verificationMode == VerificationMode.LOGIN) {
							val data = response.readData<ElloRpc.AuthResponse>()
							val user = data?.authUser

							if (user != null) {
								ok = true

								auth = TL_auth_authorization()
								auth.user = user.toTLRPCUser()

								postDelayed({
									setControlsEnabled(true)
									onAuthSuccess(auth, !newAccount)
								}, 3_000)
							}
							else {
								errorText = context.getString(R.string.unknown_error)
							}
						}
						else {
							val data = response.readData<ElloRpc.VerifyResponse>()

							if (data?.message == "success") {
								ok = true

								postDelayed({
									setControlsEnabled(true)
									loginOnSuccess()
								}, 3_000)
							}
							else {
								errorText = data?.message
							}
						}
					}

					if (!ok) {
						setControlsEnabled(true)
					}

					CodeVerificationFragment.showResponseResult(binding, context, ok)
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)

			needShowProgress(requestId = reqId)
		}

		private fun loginOnSuccess() {
			val req = ElloRpc.authRequest(username, password)

			val reqId = connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					needHideProgress(cancel = false)

					var ok = false

					if (error == null && response is TL_biz_dataRaw) {
						val res = response.readData<ElloRpc.AuthResponse>()
						val user = res?.authUser

						if (user != null) {
							val auth = TL_auth_authorization()
							auth.user = user.toTLRPCUser()
							setControlsEnabled(true)
							onAuthSuccess(auth, false)

							ok = true
						}
					}

					if (!ok) {
						needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.wrong_login_or_password))
						setControlsEnabled(true)
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)

			needShowProgress(requestId = reqId)
		}

		override fun resendCode() {
			val req = ElloRpc.authRequest(email, password)

			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					needHideProgress(cancel = false)

					@Suppress("NAME_SHADOWING") var ok = false

					if (error == null) {

						if (response is TL_biz_dataRaw) {
							val res = response.readData<ElloRpc.SignUpResponse>()
							@Suppress("NAME_SHADOWING") val email = res?.email

							if (email != null) {
								startTimer(reportTime)

								ok = true
							}
						}
					}

					if (!ok) {
						needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), error?.text ?: context.getString(R.string.failed_to_request_code))
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
		}

		private fun startTimer(countdownDurationMs: Long) {
			countDownTimer = CodeVerificationFragment.startTimer(binding, context, countDownTimer, countdownDurationMs)
		}

		override fun onDestroyActivity() {
			super.onDestroyActivity()
			countDownTimer?.cancel()
			countDownTimer = null
		}
	}

	private inner class RegistrationCredentialsView(context: Context) : SlideView(context) {
		private var binding: FragmentRegCredentialsBinding? = null
		private val minUsernameLength = 5
		private var maxUsernameLength = 32
		private var usernameAvailability = UsernameValidationResult.UNAVAILABLE
		private lateinit var mode: LoginMode
		private var usernameValidationJob: Job? = null
		private var countries: List<Country>? = null

		private var referralCode: String? = null
			get() {
				if (field.isNullOrEmpty()) {
					return LaunchActivity.REFERRAL_CODE
				}

				return field
			}

		fun setReferralCode(code: String?) {
			this.referralCode = code
			binding?.regReferralField?.setText(code)
		}

		override fun needBackButton(): Boolean {
			return true
		}

		override fun setParams(params: Bundle?, restore: Boolean) {
			if (params == null) {
				return
			}

			val modeStr = params.getString("mode") ?: throw IllegalArgumentException("mode param is missing")

			if (this::mode.isInitialized && modeStr == mode.name) {
				// do not rebuild view if mode has not changed
				return
			}

			this.mode = LoginMode.valueOf(modeStr)

			removeAllViews()

			binding = FragmentRegCredentialsBinding.inflate(LayoutInflater.from(context), this, false)

			binding?.regReferralField?.setText(referralCode)

			binding?.regUsernameField?.addTextChangedListener {
				val username = it?.toString() ?: run {
					binding?.regUsernameFieldLayout?.isErrorEnabled = false
					binding?.regUsernameFieldLayout?.error = null
					return@addTextChangedListener
				}

				when {
					username.firstOrNull()?.isDigit() == true -> {
						binding?.regUsernameFieldLayout?.error = context.getString(R.string.UsernameInvalidStartNumber)
						return@addTextChangedListener
					}

					username.length >= maxUsernameLength -> {
						binding?.regUsernameFieldLayout?.error = context.getString(R.string.username_length_maximum)
						return@addTextChangedListener
					}

					username.length < minUsernameLength -> {
						binding?.regUsernameFieldLayout?.error = context.getString(R.string.username_too_short)
						return@addTextChangedListener
					}
				}

				binding?.regUsernameFieldLayout?.isErrorEnabled = false
				binding?.regUsernameFieldLayout?.error = null

				cancelUsernameValidation()

				usernameValidationJob = ioScope.launch {
					usernameAvailability = validateUsername(username)

					mainScope.launch {
						validateFields()
					}
				}
			}

			binding?.regUsernameField?.filters = arrayOf(UsernameFilter())

			binding?.regPasswordField?.addTextChangedListener {
				validateFields()
			}

			binding?.regPasswordReField?.addTextChangedListener {
				validateFields()
			}

			binding?.regEmailField?.filters = arrayOf(LatinLettersFilter())

			binding?.regEmailField?.addTextChangedListener {
				validateFields()
			}

			binding?.countrySpinner?.addTextChangedListener {
				binding?.countrySpinnerLayout?.isErrorEnabled = false
				binding?.countrySpinnerLayout?.error = null
			}

			binding?.countrySpinner?.setOnItemClickListener { _, _, _, _ ->
				setCountriesAdapter()
			}

			binding?.publicAccountCheckbox?.isChecked = true

			binding?.privateAccountContainer?.setOnTouchListener { _, event ->
				binding?.privateAccountCheckbox?.onTouchEvent(event) ?: false
			}

			binding?.publicAccountContainer?.setOnTouchListener { _, event ->
				binding?.publicAccountCheckbox?.onTouchEvent(event) ?: false
			}

			binding?.privateAccountCheckbox?.setOnCheckedChangeListener { _, isChecked ->
				binding?.publicAccountCheckbox?.isChecked = !isChecked

				if (isChecked) {
					binding?.privateAccountCheckbox?.isClickable = false
					binding?.publicAccountCheckbox?.isClickable = true

					when (mode) {
						LoginMode.PERSONAL -> binding?.typeLabel?.text = context.getString(R.string.personal_private_profile)
						LoginMode.BUSINESS -> binding?.typeLabel?.text = context.getString(R.string.business_private_profile)
					}
				}
				else {
					binding?.publicAccountCheckbox?.isClickable = false
					binding?.privateAccountCheckbox?.isClickable = true

					when (mode) {
						LoginMode.PERSONAL -> binding?.typeLabel?.text = context.getString(R.string.personal_public_profile)
						LoginMode.BUSINESS -> binding?.typeLabel?.text = context.getString(R.string.business_public_profile)
					}
				}
			}

			binding?.publicAccountCheckbox?.setOnCheckedChangeListener { _, isChecked ->
				binding?.privateAccountCheckbox?.isChecked = !isChecked
			}

			when (mode) {
				LoginMode.PERSONAL -> {
					binding?.countrySpinnerLayout?.gone()
					binding?.termsCheckbox?.gone()
					binding?.termsLabel?.gone()

					binding?.typeLabel?.text = context.getString(R.string.personal_public_profile)

					binding?.regNextButton?.text = context.getString(R.string.next)
					binding?.regNextButton?.isEnabled = false
				}

				LoginMode.BUSINESS -> {
					binding?.countrySpinnerLayout?.visible()
					binding?.termsCheckbox?.visible()
					binding?.termsLabel?.visible()

					binding?.typeLabel?.text = context.getString(R.string.business_public_profile)

					binding?.regNextButton?.text = context.getString(R.string.registration)
					binding?.regNextButton?.isEnabled = false

					loadCountries()

					val termsLink = ClickableString(source = context.getString(R.string.terms), url = BuildConfig.TERMS_URL, underline = true) {
						Browser.openUrl(context, it)
					}

					val privacyLink = ClickableString(source = context.getString(R.string.privacy), url = BuildConfig.PRIVACY_POLICY_URL, underline = true) {
						Browser.openUrl(context, it)
					}

					val checkboxText = TextUtils.expandTemplate(context.getString(R.string.welcome_privacy_description), termsLink, privacyLink)

					binding?.termsLabel?.apply {
						text = checkboxText
						movementMethod = LinkMovementMethod.getInstance()
					}

					binding?.termsCheckbox?.setOnCheckedChangeListener { _, _ ->
						validateFields()
					}
				}
			}

			binding?.regNextButton?.setOnClickListener {
				onNextPressed(null)
			}

			addView(binding?.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))
		}

		private fun validateFields() {
			var hasErrors = false

			when (usernameAvailability) {
				UsernameValidationResult.AVAILABLE -> {
					binding?.regUsernameFieldLayout?.isErrorEnabled = false
					binding?.regUsernameFieldLayout?.error = null
				}

				UsernameValidationResult.UNAVAILABLE -> {
					binding?.regUsernameFieldLayout?.error = context.getString(R.string.username_unavailable)
					hasErrors = true
				}

				UsernameValidationResult.RESERVED -> {
					binding?.regUsernameFieldLayout?.error = context.getString(R.string.username_reserved)
					hasErrors = true
				}
			}

			val password = binding?.regPasswordField?.text?.toString()?.trim()

			hasErrors = validatePassword(password, binding?.regPasswordFieldLayout, context) || hasErrors

			val passwordConfirmation = binding?.regPasswordReField?.text?.toString()?.trim()

			if (passwordConfirmation.isNullOrEmpty()) {
				binding?.regPasswordReFieldLayout?.error = context.getString(R.string.password_confirm_not_empty)
				hasErrors = true
			}
			else if (password != passwordConfirmation) {
				binding?.regPasswordReFieldLayout?.error = context.getString(R.string.PasswordDoNotMatch)
				hasErrors = true
			}
			else {
				binding?.regPasswordReFieldLayout?.isErrorEnabled = false
				binding?.regPasswordReFieldLayout?.error = null
			}

			val email = binding?.regEmailField?.text?.toString()?.trim()

			if (email.isNullOrEmpty()) {
				binding?.regEmailFieldLayout?.error = context.getString(R.string.email_not_empty)
				hasErrors = true
			}
			else if (!email.validateEmail()) {
				binding?.regEmailFieldLayout?.error = context.getString(R.string.wrong_email_format)
				hasErrors = true
			}
			else {
				binding?.regEmailFieldLayout?.isErrorEnabled = false
				binding?.regEmailFieldLayout?.error = null
			}

			if (mode == LoginMode.BUSINESS) {
				if (binding?.termsCheckbox?.isChecked != true) {
					hasErrors = true
				}
			}

			binding?.regNextButton?.isEnabled = !hasErrors
		}

		private fun setCountriesAdapter() {
			val countries = countries ?: return
			val context = context ?: return
			val countriesAdapter = CountriesAdapter(context, R.layout.country_view_holder, countries)

			binding?.countrySpinner?.setAdapter(countriesAdapter)
		}

		private fun loadCountries() {
			CountriesDataSource.instance.loadCountries { countries, error ->
				if (error == null && countries != null) {
					this.countries = countries
					setCountriesAdapter()
				}
				else {
					Toast.makeText(context, R.string.failed_to_load_countries, Toast.LENGTH_SHORT).show()
				}
			}
		}

		private fun cancelUsernameValidation() {
			if (usernameValidationJob?.isActive == true) {
				usernameValidationJob?.cancel()
			}

			if (usernameVerificationRequestId != -1) {
				connectionsManager.cancelRequest(usernameVerificationRequestId, true)
			}
		}

		private suspend fun validateUsername(username: String): UsernameValidationResult = suspendCoroutine {
			val req = TLRPC.TL_channels_checkUsername()
			req.username = username
			req.channel = TLRPC.TL_inputChannelEmpty()

			usernameVerificationRequestId = connectionsManager.sendRequest(req, { response, error ->
				usernameVerificationRequestId = -1

				if (error == null) {
					if (response is TLRPC.TL_boolTrue) {
						it.resume(UsernameValidationResult.AVAILABLE)
					}
					else {
						it.resume(UsernameValidationResult.UNAVAILABLE)
					}
				}
				else {
					if (error.text.contains("not available") || error.text.contains("support")) {
						it.resume(UsernameValidationResult.RESERVED)
					}
					else {
						it.resume(UsernameValidationResult.UNAVAILABLE)
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
		}

		override fun onNextPressed(code: String?) {
			if (usernameAvailability != UsernameValidationResult.AVAILABLE) {
				return
			}

			val username = binding?.regUsernameField?.text?.toString()?.trim()

			if (username.isNullOrEmpty()) {
				binding?.regUsernameFieldLayout?.error = context.getString(R.string.username_not_empty)
				return
			}

			if (username.length < minUsernameLength) {
				binding?.regUsernameFieldLayout?.error = context.getString(R.string.username_too_short)
				return
			}

			val password = binding?.regPasswordField?.text?.toString()?.trim()

			if (password.isNullOrEmpty()) {
				binding?.regPasswordFieldLayout?.error = context.getString(R.string.password_not_empty)
				return
			}

			val passwordConfirmation = binding?.regPasswordReField?.text?.toString()?.trim()

			if (passwordConfirmation.isNullOrEmpty()) {
				binding?.regPasswordReFieldLayout?.error = context.getString(R.string.password_confirm_not_empty)
				return
			}

			if (password != passwordConfirmation) {
				binding?.regPasswordFieldLayout?.error = context.getString(R.string.PasswordDoNotMatch)
				binding?.regPasswordReFieldLayout?.error = context.getString(R.string.PasswordDoNotMatch)
				return
			}

			val email = binding?.regEmailField?.text?.toString()?.trim()

			if (email.isNullOrEmpty()) {
				binding?.regEmailFieldLayout?.error = context.getString(R.string.email_not_empty)
				return
			}

			if (!email.validateEmail()) {
				binding?.regEmailFieldLayout?.error = context.getString(R.string.wrong_email_format)
				return
			}

			if (!password.validatePassword()) {
				binding?.regPasswordFieldLayout?.error = context.getString(R.string.password_fail_rules)
				return
			}

			val referralCode = binding?.regReferralField?.text?.toString()?.trim()

			setControlsEnabled(false)

			// TODO: perform email unique validation
//			val emailValidationResult = runBlocking(context = ioDispatcher) {
//				user.validateEmail(email)
//			}
//
//			if (emailValidationResult.data != true) {
//				binding.regEmailFieldLayout?.error = getString(R.string.email_already_exists)
//				binding.progressBar?.gone()
//				setControlsEnabled(true)
//				return
//			}

			setControlsEnabled(true)

			val accountType = if (binding?.privateAccountCheckbox?.isChecked == true) AccountType.PRIVATE else AccountType.PUBLIC

			when (mode) {
				LoginMode.PERSONAL -> {
					AndroidUtilities.hideKeyboard(binding?.root)
					goToBirthdayGenderCountry(type = accountType, username = username, password = password, email = email, referralCode = referralCode)
				}

				LoginMode.BUSINESS -> {
					val country = (binding?.countrySpinner?.adapter as? CountriesAdapter)?.countries?.find {
						it.name == binding?.countrySpinner?.text?.toString()?.trim()
					}

					val countryCode = country?.code

					if (countryCode.isNullOrEmpty()) {
						binding?.countrySpinnerLayout?.error = context.getString(R.string.country_is_empty)
						return
					}

					signupBusinessAccount(username = username, password = password, email = email, countryCode = countryCode, accountType = accountType, referralCode = referralCode)
				}
			}
		}

		private fun setControlsEnabled(enabled: Boolean) {
			binding?.publicAccountContainer?.isEnabled = enabled
			binding?.privateAccountContainer?.isEnabled = enabled
			binding?.regNextButton?.isEnabled = enabled
			actionBar?.backButton?.isEnabled = enabled
		}

		private fun signupBusinessAccount(username: String, password: String, email: String, countryCode: String, accountType: AccountType, referralCode: String?) {
			cancelUsernameValidation()

			setControlsEnabled(false)

			AndroidUtilities.hideKeyboard(binding?.root)

			val req = ElloRpc.signupRequest(username = username, password = password, gender = null, birthday = null, email = email, phone = null, country = countryCode, kind = accountType, type = LoginMode.BUSINESS, referralCode = referralCode)

			val request = connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					var ok = false

					if (response is TL_biz_dataRaw) {
						val data = response.readData<ElloRpc.SignUpResponse>()

						if (data != null) {
							if (!data.email.isNullOrEmpty() && data.expirationDate > 0) {
								postDelayed({
									setControlsEnabled(true)
									needHideProgress(false)
									AndroidUtilities.hideKeyboard(fragmentView?.findFocus())
									onSignupCodeReceived(username = username, password = password, email = email, expiration = data.expirationDate, loginMode = LoginMode.BUSINESS, verificationMode = VerificationMode.REGISTRATION, accountType = accountType)
								}, 150)

								MessagesController.getGlobalMainSettings().edit().putBoolean("newlyRegistered", true).commit()

								ok = true
							}
						}
					}

					if (!ok) {
						needHideProgress(false)

						val errorText = error?.text ?: ""

						when {
							errorText.contains("PHONE_NUMBER_INVALID") -> {
								needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.InvalidPhoneNumber))
							}

							errorText.contains("PHONE_CODE_EMPTY") || errorText.contains("PHONE_CODE_INVALID") -> {
								needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.InvalidCode))
							}

							errorText.contains("PHONE_CODE_EXPIRED") -> {
								needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.CodeExpired))
							}

							errorText.contains("FIRSTNAME_INVALID") -> {
								needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.InvalidFirstName))
							}

							errorText.contains("LASTNAME_INVALID") -> {
								needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.InvalidLastName))
							}

							errorText.isEmpty() -> {
								needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.UnknownError))
							}

							else -> {
								needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), error?.text)
							}
						}

						setControlsEnabled(true)
					}
				}
			}, ConnectionsManager.RequestFlagWithoutLogin or ConnectionsManager.RequestFlagFailOnServerErrors)

			needShowProgress(request)
		}
	}

	private inner class LoginModeView(context: Context) : SlideView(context), AdapterView.OnItemSelectedListener {
		private val binding: FragmentLoginModeBinding
		private var nextPressed = false

		override fun needBackButton(): Boolean {
			// MARK: return true to show back button on first screen
			return false
		}

		override fun onCancelPressed() {
			nextPressed = false
		}

		override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
			// unused
		}

		override fun onNothingSelected(adapterView: AdapterView<*>?) {
			// unused
		}

		override fun onNextPressed(code: String?) {
			val email = binding.emailField.text?.toString()?.trim()
			binding.loginError.gone()

			if (email.isNullOrEmpty()) {
				binding.emailFieldLayout.isErrorEnabled = true
				binding.emailFieldLayout.error = context.getString(R.string.username_not_empty)
				return
			}

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				val u = AccountInstance.getInstance(a).userConfig.getCurrentUser()

				if (email == u?.username) {
					needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.AccountAlreadyLoggedIn))
					return
				}
			}

			val password = binding.passwordField.text?.toString()?.trim()

			if (password.isNullOrEmpty()) {
				binding.passwordFieldLayout.isErrorEnabled = true
				binding.passwordFieldLayout.error = context.getString(R.string.password_not_empty)
				return
			}

			AndroidUtilities.hideKeyboard(binding.root)

			binding.loginButton.isEnabled = false
			binding.forgotPasswordButton.isEnabled = false
			binding.modeSegmentControl.isEnabled = false

			val req = ElloRpc.authRequest(email, password)

			ioScope.launch {
				try {
					withContext(mainScope.coroutineContext) {
						needShowProgress()
					}

					val response = withTimeout(60_000) {
						connectionsManager.performRequest(req, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
					}

					val error = response as? TLRPC.TL_error

					withContext(mainScope.coroutineContext) {
						needHideProgress(cancel = false)

						var ok = false

						if (error == null) {
							nextPressed = false

							if (response is TL_biz_dataRaw) {
								val res = response.readData<ElloRpc.SignUpResponse>()
								@Suppress("NAME_SHADOWING") val email = res?.email
								val expiration = res?.expirationDate

								if (email != null && expiration != null) {
									onLoginCodeSent(VerificationMode.LOGIN, email, password, expiration)

									ok = true

									postDelayed({
										binding.loginButton.isEnabled = true
										binding.forgotPasswordButton.isEnabled = true
										binding.modeSegmentControl.isEnabled = true
									}, 500)
								}
							}
						}

						if (!ok) {
							nextPressed = false

							val errorText = error?.text
							var shouldConfirm = false
							var userStatus: UserStatus? = null

							val label = when {
								errorText?.contains("blocked", ignoreCase = true) == true -> {
									userStatus = UserStatus.BLOCKED
									""
								}

								errorText?.contains("deleted", ignoreCase = true) == true -> {
									userStatus = UserStatus.DELETED
									""
								}

								errorText?.contains("confirmed", ignoreCase = true) == true -> {
									shouldConfirm = true
									context.getString(R.string.email_not_confirmed)
								}

								else -> {
									context.getString(R.string.wrong_login_or_password)
								}
							}

							if (shouldConfirm) {
								needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), label, context.getString(R.string.ResendCode)) { _, _ ->
									@Suppress("NAME_SHADOWING") val req = ElloRpc.resendConfirmationCodeRequest(email)

									val reqId = connectionsManager.sendRequest(req, { response, error ->
										AndroidUtilities.runOnUIThread {
											needHideProgress(cancel = false)

											@Suppress("NAME_SHADOWING") var ok = false

											if (error == null) {
												if (response is TL_biz_dataRaw) {
													val res = response.readData<ElloRpc.SignUpResponse>()
													@Suppress("NAME_SHADOWING") val email = res?.email
													val expiration = res?.expirationDate

													if (email != null && expiration != null) {
														onLoginCodeSent(VerificationMode.REGISTRATION, email, password, expiration)

														ok = true
													}
												}
											}

											if (!ok) {
												nextPressed = false

												needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), error?.text ?: context.getString(R.string.failed_to_request_code))
											}
										}
									}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)

									needShowProgress(requestId = reqId)
								}
							}
							else {
								when (userStatus) {
									UserStatus.BLOCKED, UserStatus.DELETED -> presentFragment(BlockedAccountFragment(userStatus, email))
									else -> showErrorView(label)
								}
							}

							postDelayed({
								binding.loginButton.isEnabled = true
								binding.forgotPasswordButton.isEnabled = true
								binding.modeSegmentControl.isEnabled = true
							}, 300)
						}
					}
				}
				catch (e: TimeoutCancellationException) {
					withContext(mainScope.coroutineContext) {
						needHideProgress(cancel = true)

						needShowAlert(null, context.getString(R.string.long_time_service_error))

						postDelayed({
							binding.loginButton.isEnabled = true
							binding.forgotPasswordButton.isEnabled = true
							binding.modeSegmentControl.isEnabled = true
						}, 300)
					}
				}
			}
		}

		private fun showErrorView(label: String) {
			binding.loginError.apply {
				text = label
				visible()
			}
		}

		init {
			orientation = VERTICAL
			gravity = Gravity.CENTER

			binding = FragmentLoginModeBinding.inflate(LayoutInflater.from(context), this, false)

			addView(binding.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))

			binding.modeSegmentControl.addOnButtonCheckedListener { _, checkedId, isChecked ->
				if (!isChecked) {
					return@addOnButtonCheckedListener
				}

				when (checkedId) {
					R.id.segment_button_registration -> {
						binding.registrationContainer.visible()
						binding.loginContainer.gone()
						binding.loginButton.gone()
					}

					R.id.segment_button_login -> {
						binding.registrationContainer.gone()
						binding.loginContainer.visible()
						binding.loginButton.visible()
					}
				}

				AndroidUtilities.hideKeyboard(binding.root)
			}

			binding.personalContainer.setOnClickListener {
				goToRegistrationCredentials(mode = LoginMode.PERSONAL, animate = true)
			}

			binding.businessContainer.setOnClickListener {
				goToRegistrationCredentials(mode = LoginMode.BUSINESS, animate = true)
			}

			binding.loginButton.setOnClickListener {
				onNextPressed(null)
			}

			binding.forgotPasswordButton.setOnClickListener {
				forgotPassword(email = binding.emailField.text?.toString()?.trim())
			}

			binding.emailField.addTextChangedListener {
				binding.emailFieldLayout.error = null
				binding.emailFieldLayout.isErrorEnabled = false
			}

			binding.passwordField.addTextChangedListener {
				binding.passwordFieldLayout.error = null
				binding.passwordFieldLayout.isErrorEnabled = false
			}

			binding.accountTypeButton.setOnClickListener {
				presentFragment(AccountTypeFragment())
			}
		}
	}

	private inner class RegistrationBirthdayView(context: Context) : SlideView(context) {
		private val binding: FragmentRegBirthdayBinding
		private var username: String? = null
		private var password: String? = null
		private var email: String? = null
		private var referralCode: String? = null
		private var accountType = AccountType.PUBLIC
		private var countries: List<Country>? = null

		init {
			binding = FragmentRegBirthdayBinding.inflate(LayoutInflater.from(context), this, false)

			val genderAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, resources.getStringArray(R.array.gender))

			binding.genderSpinner.setAdapter(genderAdapter)

			loadCountries()

			binding.birthdayField.setOnClickListener {
				val calendar = Calendar.getInstance()
				val year = calendar[Calendar.YEAR]
				val month = calendar[Calendar.MONTH]
				val day = calendar[Calendar.DAY_OF_MONTH]

				val datePickerDialog = DatePickerDialog(it.context, { _, y, m, d ->
					val formatter = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
					val date = formatter.parse(String.format("%02d%02d%d", d, m + 1, y))
					binding.birthdayField.setText(date?.formatBirthday())
				}, year, month, day)

				datePickerDialog.datePicker.maxDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365 * 13) // -13 years from now

				datePickerDialog.show()
			}

			binding.birthdayField.addTextChangedListener {
				binding.birthdayFieldLayout.isErrorEnabled = false
				binding.birthdayFieldLayout.error = null
			}

			binding.countrySpinner.addTextChangedListener {
				binding.countrySpinnerLayout.isErrorEnabled = false
				binding.countrySpinnerLayout.error = null
			}

			binding.countrySpinner.setOnItemClickListener { _, _, _, _ ->
				setCountriesAdapter()
			}

			val termsLink = ClickableString(source = context.getString(R.string.terms), url = BuildConfig.TERMS_URL, underline = true) {
				Browser.openUrl(context, it)
			}

			val privacyLink = ClickableString(source = context.getString(R.string.privacy), url = BuildConfig.PRIVACY_POLICY_URL, underline = true) {
				Browser.openUrl(context, it)
			}

			val checkboxText = TextUtils.expandTemplate(context.getString(R.string.welcome_privacy_description), termsLink, privacyLink)

			binding.termsLabel.apply {
				text = checkboxText
				movementMethod = LinkMovementMethod.getInstance()
			}

			binding.regNextButton.isEnabled = false

			binding.regNextButton.setOnClickListener {
				onNextPressed(null)
			}

			binding.termsCheckbox.setOnCheckedChangeListener { _, isChecked ->
				binding.regNextButton.isEnabled = isChecked
			}

			addView(binding.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))
		}

		private fun setControlsEnabled(enabled: Boolean) {
			binding.birthdayField.isEnabled = enabled
			binding.countrySpinner.isEnabled = enabled
			binding.genderSpinner.isEnabled = enabled
			binding.termsCheckbox.isEnabled = enabled
			binding.regNextButton.isEnabled = enabled
			actionBar?.backButton?.isEnabled = enabled
		}

		override fun onNextPressed(code: String?) {
			val username = username

			if (username.isNullOrEmpty()) {
				throw IllegalArgumentException("Username is null")
			}

			val password = password

			if (password.isNullOrEmpty()) {
				throw IllegalArgumentException("Password is null")
			}

			val email = email

			if (email.isNullOrEmpty()) {
				throw IllegalArgumentException("Email is null")
			}

			val country = (binding.countrySpinner.adapter as? CountriesAdapter)?.countries?.find {
				it.name == binding.countrySpinner.text?.toString()?.trim()
			}

			if (country?.code == null) {
				binding.countrySpinnerLayout.error = context.getString(R.string.country_is_empty)
				return
			}

			val birthday = binding.birthdayField.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.parseBirthday()?.formatDate()

			if (birthday.isNullOrEmpty()) {
				binding.birthdayFieldLayout.error = context.getString(R.string.birthday_is_empty)
				return
			}

			val gender = when (binding.genderSpinner.text?.toString()?.trim()) {
				context.getString(R.string.female) -> ElloRpc.Gender.WOMAN
				context.getString(R.string.male) -> ElloRpc.Gender.MAN
				context.getString(R.string.non_binary) -> ElloRpc.Gender.NON_BINARY
				else -> ElloRpc.Gender.OTHER
			}

			val req = ElloRpc.signupRequest(username = username, password = password, gender = gender, birthday = birthday, email = email, phone = null, country = country.code, type = LoginMode.PERSONAL, kind = accountType, referralCode = referralCode)

			setControlsEnabled(false)

			ioScope.launch {
				try {
					withContext(mainScope.coroutineContext) {
						needShowProgress()
					}

					val response = withTimeout(60_000) {
						connectionsManager.performRequest(req, ConnectionsManager.RequestFlagWithoutLogin or ConnectionsManager.RequestFlagFailOnServerErrors)
					}
					val error = response as? TLRPC.TL_error

					withContext(mainScope.coroutineContext) {
						var ok = false

						if (response is TL_biz_dataRaw) {
							val data = response.readData<ElloRpc.SignUpResponse>()

							if (data != null) {
								if (!data.email.isNullOrEmpty() && data.expirationDate > 0) {
									postDelayed({
										setControlsEnabled(true)
										needHideProgress(false)
										AndroidUtilities.hideKeyboard(fragmentView?.findFocus())
										onSignupCodeReceived(username = username, password = password, email = email, expiration = data.expirationDate, loginMode = LoginMode.PERSONAL, verificationMode = VerificationMode.REGISTRATION, accountType = accountType)
									}, 150)

									ok = true

									MessagesController.getGlobalMainSettings().edit().putBoolean("newlyRegistered", true).commit()
								}
							}
						}

						if (!ok) {
							needHideProgress(false)

							val errorText = error?.text ?: ""

							when {
								errorText.contains("PHONE_NUMBER_INVALID") -> {
									needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.InvalidPhoneNumber))
								}

								errorText.contains("PHONE_CODE_EMPTY") || errorText.contains("PHONE_CODE_INVALID") -> {
									needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.InvalidCode))
								}

								errorText.contains("PHONE_CODE_EXPIRED") -> {
									needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.CodeExpired))
								}

								errorText.contains("FIRSTNAME_INVALID") -> {
									needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.InvalidFirstName))
								}

								errorText.contains("LASTNAME_INVALID") -> {
									needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.InvalidLastName))
								}

								errorText.isEmpty() -> {
									needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), context.getString(R.string.UnknownError))
								}

								else -> {
									needShowAlert(context.getString(R.string.RestorePasswordNoEmailTitle), error?.text)
								}
							}

							setControlsEnabled(true)
						}
					}

				}
				catch (e: TimeoutCancellationException) {
					withContext(mainScope.coroutineContext) {
						setControlsEnabled(true)
						needHideProgress(false)
						AndroidUtilities.hideKeyboard(fragmentView?.findFocus())

						needShowAlert(null, context.getString(R.string.long_time_service_error))
					}
				}
			}
		}

		private fun setCountriesAdapter() {
			val countries = countries ?: return
			val context = context ?: return
			val countriesAdapter = CountriesAdapter(context, R.layout.country_view_holder, countries)
			binding.countrySpinner.setAdapter(countriesAdapter)
		}

		private fun loadCountries() {
			CountriesDataSource.instance.loadCountries { countries, error ->
				if (error == null && countries != null) {
					this.countries = countries
					setCountriesAdapter()
				}
				else {
					Toast.makeText(context, R.string.failed_to_load_countries, Toast.LENGTH_SHORT).show()
				}
			}
		}

		override fun needBackButton(): Boolean {
			return true
		}

		override fun setParams(params: Bundle?, restore: Boolean) {
			if (params == null) {
				return
			}

			username = params.getString("username")
			password = params.getString("password")
			email = params.getString("email")
			referralCode = params.getString("referral_code")
			accountType = AccountType.valueOf(params.getString("type") ?: throw IllegalArgumentException("type param is missing"))
		}

		override fun onShow() {
			super.onShow()
			loadCountries()
		}
	}

	private inner class ResetPasswordView(context: Context) : SlideView(context) {
		private val binding: FragmentPasswordResetBinding

		init {
			orientation = VERTICAL
			gravity = Gravity.CENTER

			binding = FragmentPasswordResetBinding.inflate(LayoutInflater.from(context), this, false)

			binding.errorLayout.invisible()

			binding.emailField.addTextChangedListener {
				binding.emailFieldLayout.isErrorEnabled = false
				binding.emailFieldLayout.error = null
			}

			binding.sendButton.setOnClickListener {
				onNextPressed(null)
			}

			addView(binding.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))
		}

		override fun needBackButton(): Boolean {
			return true
		}

		override fun setParams(params: Bundle?, restore: Boolean) {
			if (params == null) {
				return
			}

			binding.emailField.setText(params.getString("email"))
		}

		private fun setControlsEnabled(enabled: Boolean) {
			binding.emailField.isEnabled = enabled
			binding.sendButton.isEnabled = enabled
		}

		override fun onNextPressed(code: String?) {
			binding.errorLayout.invisible()

			val email = binding.emailField.text?.toString()?.trim()

			if (email.isNullOrEmpty()) {
				binding.emailFieldLayout.error = context.getString(R.string.email_not_empty)
				return
			}

			if (!email.validateEmail()) {
				binding.emailFieldLayout.error = context.getString(R.string.wrong_email_format)
				return
			}

			binding.emailFieldLayout.error = null
			binding.emailFieldLayout.isErrorEnabled = false

			setControlsEnabled(false)

			AndroidUtilities.hideKeyboard(binding.root)

			val req = ElloRpc.forgotPasswordRequest(email)

			val reqId = connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					needHideProgress(cancel = false)

					setControlsEnabled(true)

					var ok = false
					var errorText = error?.text

					if (error == null && response is TL_biz_dataRaw) {
						val data = response.readData<ElloRpc.ForgotPasswordVerifyResponse>()

						if (data?.status == true) {
							ok = true
							onPasswordResetCodeReceived(email = email, expiration = data.expirationDate)
						}
						else {
							errorText = data?.message
						}
					}

					if (!ok) {
						if (errorText?.contains("not exist") == true) {
							binding.emailFieldLayout.error = context.getString(R.string.cant_find_email)
						}
						else {
							binding.emailFieldLayout.error = errorText?.capitalizeFirstLetter()
						}

//						binding.errorLayout.background = ResourcesCompat.getDrawable(resources, R.drawable.error_background, null)
//						binding.errorSign.setImageResource(R.drawable.ic_error)
//						binding.errorLabel.text = errorText?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
//						binding.errorLayout.visible()
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)

			needShowProgress(requestId = reqId)
		}
	}

	private inner class CreatePasswordView(context: Context) : SlideView(context) {
		private val binding: FragmentCreatePasswordBinding
		private var email: String? = null

		init {
			orientation = VERTICAL
			gravity = Gravity.CENTER

			binding = FragmentCreatePasswordBinding.inflate(LayoutInflater.from(context), this, false)

			var passwordHasErrors = false
			var rePasswordHasErrors = false

			binding.errorLayout.gone()

			binding.passwordField.addTextChangedListener {
				val password = it.toString().trim()
				passwordHasErrors = validatePassword(password, binding.passwordFieldLayout, context)
				binding.saveButton.isEnabled = !passwordHasErrors && !rePasswordHasErrors
			}

			binding.passwordReField.addTextChangedListener {
				val password = it.toString().trim()

				rePasswordHasErrors = validatePassword(password, binding.passwordReFieldLayout, context)

				if (password != binding.passwordField.text.toString().trim()) {
					binding.passwordReFieldLayout.error = context.getString(R.string.PasswordDoNotMatch)
					rePasswordHasErrors = true
				}

				binding.saveButton.isEnabled = !passwordHasErrors && !rePasswordHasErrors
			}

			binding.saveButton.setOnClickListener {
				onNextPressed(code)
			}

			addView(binding.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))
		}

		override fun needBackButton(): Boolean {
			resetPasswordFields()
			return true
		}

		private fun setControlsEnabled(enabled: Boolean) {
			binding.passwordField.isEnabled = enabled
			binding.passwordReField.isEnabled = enabled
			binding.saveButton.isEnabled = enabled
		}

		private fun resetPasswordFields() {
			binding.passwordField.setText("")
			binding.passwordReField.setText("")
		}

		override fun onNextPressed(code: String?) {
			val password = binding.passwordField.text?.toString()?.trim()

			if (password.isNullOrEmpty()) {
				binding.passwordFieldLayout.error = context.getString(R.string.password_not_empty)
				return
			}

			val passwordConfirmation = binding.passwordReField.text?.toString()?.trim()

			if (passwordConfirmation.isNullOrEmpty()) {
				binding.passwordReFieldLayout.error = context.getString(R.string.password_confirm_not_empty)
				return
			}

			if (password != passwordConfirmation) {
				binding.passwordFieldLayout.error = context.getString(R.string.PasswordDoNotMatch)
				binding.passwordReFieldLayout.error = context.getString(R.string.PasswordDoNotMatch)
				return
			}

			val email = email

			if (email.isNullOrEmpty()) {
				return
			}

			val finalCode = code

			if (finalCode.isNullOrEmpty()) {
				return
			}

			setControlsEnabled(false)

			binding.errorLayout.gone()

			val req = ElloRpc.newPasswordRequest(email = email, code = finalCode, password = password)

			connectionsManager.sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					needHideProgress(cancel = false)

					var ok = false
					var errorText = error?.text

					if (error == null && response is TL_biz_dataRaw) {
						val data = response.readData<ElloRpc.RichVerifyResponse>()

						if (data?.status == true) {
							ok = true

							binding.errorLayout.background = ResourcesCompat.getDrawable(resources, R.drawable.success_background, null)
							binding.errorSign.setImageResource(R.drawable.ic_success_mark)
							binding.errorLabel.text = context.getString(R.string.password_update_successfully)
							binding.errorLayout.visible()

							postDelayed({
								onPasswordResetFinished()
								resetPasswordFields()
							}, TimeUnit.SECONDS.toMillis(3))
						}
						else {
							errorText = data?.message
						}
					}

					if (!ok) {
						setControlsEnabled(true)

						binding.errorLayout.background = ResourcesCompat.getDrawable(resources, R.drawable.error_background, null)
						binding.errorSign.setImageResource(R.drawable.ic_error)
						binding.errorLabel.text = errorText?.capitalizeFirstLetter()
						binding.errorLayout.visible()
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
		}

		override fun setParams(params: Bundle?, restore: Boolean) {
			binding.errorLayout.gone()

			if (params == null) {
				return
			}

			email = params.getString("email")
			code = params.getString("code")
		}
	}

	private fun showKeyboard(editText: View?): Boolean {
		return AndroidUtilities.showKeyboard(editText)
	}

	fun setIntroView(intro: View): LoginActivity {
		introView = intro
		return this
	}

	override fun onCustomTransitionAnimation(isOpen: Boolean, callback: Runnable): AnimatorSet? {
		if (isOpen && introView != null) {
			val animator = ValueAnimator.ofFloat(0f, 1f)

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationStart(animation: Animator) {
					fragmentView?.setBackgroundColor(Color.TRANSPARENT)
				}

				override fun onAnimationEnd(animation: Animator) {
					fragmentView?.setBackgroundColor(context!!.getColor(R.color.background))

					if (animationFinishCallback != null) {
						AndroidUtilities.runOnUIThread(animationFinishCallback)
						animationFinishCallback = null
					}

					callback.run()
				}
			})

			val bgColor = context!!.getColor(R.color.background)
			val initialAlpha = Color.alpha(bgColor)

			animator.addUpdateListener {
				val `val` = it.animatedValue as Float

				fragmentView?.setBackgroundColor(ColorUtils.setAlphaComponent(bgColor, (initialAlpha * `val`).toInt()))

				val inverted = 1f - `val`

				slideViewsContainer?.translationY = AndroidUtilities.dp(20f) * inverted

				introView?.translationY = -AndroidUtilities.dp(20f) * `val`

				val sc = 0.95f + 0.05f * inverted

				introView?.scaleX = sc
				introView?.scaleY = sc
			}

			animator.interpolator = CubicBezierInterpolator.DEFAULT
			val set = AnimatorSet()
			set.duration = 300
			set.playTogether(animator)
			set.start()
			return set
		}

		return null
	}

	class PhoneInputData {
		var country: Country? = null
		var patterns: List<String>? = null
		var phoneNumber: String? = null
	}

	override fun isLightStatusBar(): Boolean {
		return !AndroidUtilities.isDarkTheme()
	}

	companion object {
		const val AUTH_TYPE_MESSAGE = 1
		const val AUTH_TYPE_MISSED_CALL = 11
		private const val VIEW_LOGIN_MODE = 0
		private const val VIEW_REGISTRATION_CREDENTIALS = 1
		private const val VIEW_REGISTRATION_BIRTHDAY = 2
		private const val VIEW_VERIFICATION = 3
		private const val VIEW_PASSWORD_RESET = 4
		private const val VIEW_NEW_PASSWORD = 5
		private const val help = 1

		@JvmStatic
		fun needShowInvalidAlert(fragment: BaseFragment?, phoneNumber: String, banned: Boolean) {
			needShowInvalidAlert(fragment, phoneNumber, null, banned)
		}

		private fun needShowInvalidAlert(fragment: BaseFragment?, phoneNumber: String, inputData: PhoneInputData?, banned: Boolean) {
			if (fragment?.parentActivity == null) {
				return
			}

			val context = fragment.parentActivity ?: return
			val builder = AlertDialog.Builder(context)

			if (banned) {
				builder.setTitle(context.getString(R.string.RestorePasswordNoEmailTitle))
				builder.setMessage(context.getString(R.string.BannedPhoneNumber))
			}
			else {
				if (inputData?.patterns != null && !inputData.patterns.isNullOrEmpty() && inputData.country != null) {
					var patternLength = Int.MAX_VALUE

					inputData.patterns?.forEach { pattern ->
						val length = pattern.replace(" ", "").length

						if (length < patternLength) {
							patternLength = length
						}
					}

					if (PhoneFormat.stripExceptNumbers(phoneNumber).length - (inputData.country?.code?.length ?: 0) < patternLength) {
						builder.setTitle(context.getString(R.string.WrongNumberFormat))
						builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ShortNumberInfo", R.string.ShortNumberInfo, inputData.country?.name ?: "", inputData.phoneNumber)))
					}
					else {
						builder.setTitle(context.getString(R.string.RestorePasswordNoEmailTitle))
						builder.setMessage(context.getString(R.string.InvalidPhoneNumber))
					}
				}
				else {
					builder.setTitle(context.getString(R.string.RestorePasswordNoEmailTitle))
					builder.setMessage(context.getString(R.string.InvalidPhoneNumber))
				}
			}
			builder.setNeutralButton(context.getString(R.string.BotHelp)) { _, _ ->
				try {
					val pInfo = ApplicationLoader.applicationContext.packageManager.getPackageInfo(ApplicationLoader.applicationContext.packageName, 0)
					val version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode)
					val mailer = Intent(Intent.ACTION_SENDTO)
					mailer.data = Uri.parse("mailto:")
					mailer.putExtra(Intent.EXTRA_EMAIL, arrayOf(if (banned) "recover@ello.team" else "login@ello.team"))
					if (banned) {
						mailer.putExtra(Intent.EXTRA_SUBJECT, "Banned phone number: $phoneNumber")
						mailer.putExtra(Intent.EXTRA_TEXT, """
     I'm trying to use my mobile phone number: $phoneNumber
     But Ello says it's banned. Please help.
     
     App version: $version
     OS version: SDK ${Build.VERSION.SDK_INT}
     Device Name: ${Build.MANUFACTURER}${Build.MODEL}
     Locale: ${Locale.getDefault()}
     """.trimIndent())
					}
					else {
						mailer.putExtra(Intent.EXTRA_SUBJECT, "Invalid phone number: $phoneNumber")
						mailer.putExtra(Intent.EXTRA_TEXT, """
     I'm trying to use my mobile phone number: $phoneNumber
     But Ello says it's invalid. Please help.
     
     App version: $version
     OS version: SDK ${Build.VERSION.SDK_INT}
     Device Name: ${Build.MANUFACTURER}${Build.MODEL}
     Locale: ${Locale.getDefault()}
     """.trimIndent())
					}
					fragment.parentActivity?.startActivity(Intent.createChooser(mailer, "Send email..."))
				}
				catch (e: Exception) {
					fragment.parentActivity?.let {
						val builder2 = AlertDialog.Builder(it)
						builder2.setTitle(context.getString(R.string.RestorePasswordNoEmailTitle))
						builder2.setMessage(context.getString(R.string.NoMailInstalled))
						builder2.setPositiveButton(context.getString(R.string.OK), null)
						fragment.showDialog(builder2.create())
					}
				}
			}
			builder.setPositiveButton(context.getString(R.string.OK), null)
			fragment.showDialog(builder.create())
		}

		fun validatePassword(password: String?, inputLayout: TextInputLayout?, context: Context): Boolean {
			var hasErrors = false

			if (password.isNullOrEmpty()) {
				inputLayout?.error = context.getString(R.string.password_not_empty)
				hasErrors = true
			}
			else if (!password.validatePassword()) {
				inputLayout?.error = context.getString(R.string.password_fail_rules)
				hasErrors = true
			}
			else {
				inputLayout?.isErrorEnabled = false
				inputLayout?.error = null
			}

			return hasErrors
		}
	}
}
