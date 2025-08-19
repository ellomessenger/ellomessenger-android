/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.math.MathUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.ChatListItemAnimator
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLog
import org.telegram.messenger.GenericProvider
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserObject.getUserName
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TLDataJSON
import org.telegram.tgnet.TLRPC.TLMessagesProlongWebView
import org.telegram.tgnet.TLRPC.TLMessagesRequestWebView
import org.telegram.tgnet.TLRPC.TLWebViewResult
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout
import org.telegram.ui.Components.LayoutHelper.createFrame
import kotlin.math.abs
import kotlin.math.max

class ChatAttachAlertBotWebViewLayout(alert: ChatAttachAlert, context: Context) : AttachAlertLayout(alert, context), NotificationCenterDelegate {
	val webViewContainer: BotWebViewContainer by lazy {
		object : BotWebViewContainer(context, ResourcesCompat.getColor(resources, R.color.background, null)) {
			override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
				if (ev.action == MotionEvent.ACTION_DOWN) {
					if (!isBotButtonAvailable) {
						isBotButtonAvailable = true
						webViewContainer.restoreButtonData()
					}
				}

				return super.dispatchTouchEvent(ev)
			}
		}
	}

	private var webViewScrollAnimator: ValueAnimator? = null
	private var ignoreLayout = false
	private var botId: Long = 0
	private var peerId: Long = 0
	private var queryId: Long = 0
	private var silent = false
	private var replyToMsgId = 0
	private var currentAccount = 0

	var startCommand: String? = null
		private set

	private var needReload = false
	private val progressView = WebProgressView(context)
	private val swipeContainer: WebViewSwipeContainer
	private val otherItem: ActionBarMenuItem
	private val settingsItem: ActionBarMenuSubItem
	private var measureOffsetY = 0
	private var lastSwipeTime: Long = 0
	private var ignoreMeasure = false

	var isBotButtonAvailable = false
		private set

	private var hasCustomBackground = false

	override var customBackground = 0
		set(customBackground) {
			field = customBackground
			hasCustomBackground = true
		}

	private var needCloseConfirmation = false
	private var destroyed = false

	private val pollRunnable: Runnable by lazy {
		Runnable {
			if (!destroyed) {
				val prolongWebView = TLMessagesProlongWebView()
				prolongWebView.bot = MessagesController.getInstance(currentAccount).getInputUser(botId)
				prolongWebView.peer = MessagesController.getInstance(currentAccount).getInputPeer(peerId)
				prolongWebView.queryId = queryId
				prolongWebView.silent = silent

				if (replyToMsgId != 0) {
					prolongWebView.replyToMsgId = replyToMsgId
					prolongWebView.flags = prolongWebView.flags or 1
				}

				if (peerId < 0) {
					val chatFull = MessagesController.getInstance(currentAccount).getChatFull(-peerId)

					if (chatFull != null) {
						val peer = chatFull.defaultSendAs

						if (peer != null) {
							prolongWebView.sendAs = MessagesController.getInstance(currentAccount).getInputPeer(peer)
							prolongWebView.flags = prolongWebView.flags or 8192
						}
					}
				}

				ConnectionsManager.getInstance(currentAccount).sendRequest(prolongWebView) { _, error ->
					AndroidUtilities.runOnUIThread {
						if (destroyed) {
							return@runOnUIThread
						}

						if (error != null) {
							parentAlert.dismiss()
						}
						else {
							AndroidUtilities.runOnUIThread(pollRunnable, POLL_PERIOD.toLong())
						}
					}
				}
			}
		}
	}

	init {
		val menu = parentAlert.actionBar.createMenu()

		otherItem = menu.addItem(0, R.drawable.overflow_menu)
		otherItem.addSubItem(R.id.menu_open_bot, R.drawable.msg_bot, context.getString(R.string.BotWebViewOpenBot))

		settingsItem = otherItem.addSubItem(R.id.menu_settings, R.drawable.msg_settings, context.getString(R.string.BotWebViewSettings))

		otherItem.addSubItem(R.id.menu_reload_page, R.drawable.msg_retry, context.getString(R.string.BotWebViewReloadPage))
		otherItem.addSubItem(R.id.menu_delete_bot, R.drawable.msg_delete, context.getString(R.string.BotWebViewDeleteBot))

		parentAlert.actionBar.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						if (!webViewContainer.onBackPressed()) {
							onCheckDismissByUser()
						}
					}

					R.id.menu_open_bot -> {
						val bundle = Bundle()
						bundle.putLong("user_id", botId)
						parentAlert.baseFragment.presentFragment(ChatActivity(bundle))
						parentAlert.dismiss()
					}

					R.id.menu_reload_page -> {
						if (webViewContainer.webView != null) {
							webViewContainer.webView.animate().cancel()
							webViewContainer.webView.animate().alpha(0f).start()
						}

						progressView.setLoadProgress(0f)
						progressView.alpha = 1f
						progressView.visibility = VISIBLE

						webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId))
						webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem)
						webViewContainer.reload()
					}

					R.id.menu_delete_bot -> {
						for (bot in MediaDataController.getInstance(currentAccount).attachMenuBots.bots) {
							if (bot.botId == botId) {
								parentAlert.onLongClickBotButton(bot, MessagesController.getInstance(currentAccount).getUser(botId))
								break
							}
						}
					}

					R.id.menu_settings -> {
						webViewContainer.onSettingsButtonPressed()
					}
				}
			}
		})

		swipeContainer = object : WebViewSwipeContainer(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(84f) + measureOffsetY, MeasureSpec.EXACTLY))
			}
		}

		swipeContainer.addView(webViewContainer, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		swipeContainer.setScrollListener {
			parentAlert.updateLayout(this, true, 0)
			webViewContainer.invalidateViewPortHeight()
			lastSwipeTime = System.currentTimeMillis()
		}

		swipeContainer.setScrollEndListener { webViewContainer.invalidateViewPortHeight(true) }

		swipeContainer.setDelegate {
			if (!onCheckDismissByUser()) {
				swipeContainer.stickTo(0f)
			}
		}

		swipeContainer.setIsKeyboardVisible {
			parentAlert.sizeNotifierFrameLayout.getKeyboardHeight() >= AndroidUtilities.dp(20f)
		}

		addView(swipeContainer, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		addView(progressView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM, 0f, 0f, 0f, 84f))

		webViewContainer.setWebViewProgressListener {
			progressView.setLoadProgressAnimated(it)

			if (it == 1f) {
				val animator = ValueAnimator.ofFloat(1f, 0f).setDuration(200)
				animator.interpolator = CubicBezierInterpolator.DEFAULT

				animator.addUpdateListener { animation ->
					progressView.alpha = (animation.animatedValue as Float)
				}

				animator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						progressView.visibility = GONE
					}
				})

				animator.start()

				requestEnableKeyboard()
			}
		}

		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.didSetNewTheme)
	}

	fun setNeedCloseConfirmation(needCloseConfirmation: Boolean) {
		this.needCloseConfirmation = needCloseConfirmation
	}

	override fun onDismissWithTouchOutside(): Boolean {
		onCheckDismissByUser()
		return false
	}

	fun onCheckDismissByUser(): Boolean {
		return if (needCloseConfirmation) {
			var botName: String? = null
			val user = MessagesController.getInstance(currentAccount).getUser(botId)

			if (user != null) {
				botName = ContactsController.formatName(user.firstName, user.lastName)
			}

			val dialog = AlertDialog.Builder(context).setTitle(botName).setMessage(context.getString(R.string.BotWebViewChangesMayNotBeSaved)).setPositiveButton(context.getString(R.string.BotWebViewCloseAnyway)) { _, _ -> parentAlert.dismiss() }.setNegativeButton(context.getString(R.string.Cancel), null).create()
			dialog.show()

			val textView = dialog.getButton(BUTTON_POSITIVE) as? TextView
			textView?.setTextColor(ResourcesCompat.getColor(resources, R.color.purple, null))

			false
		}
		else {
			parentAlert.dismiss()
			true
		}
	}

	override fun hasCustomBackground(): Boolean {
		return hasCustomBackground
	}

	fun canExpandByRequest(): Boolean {
		return !swipeContainer.isSwipeInProgress
	}

	fun setMeasureOffsetY(measureOffsetY: Int) {
		this.measureOffsetY = measureOffsetY
		swipeContainer.requestLayout()
	}

	fun disallowSwipeOffsetAnimation() {
		swipeContainer.setSwipeOffsetAnimationDisallowed(true)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (ignoreMeasure) {
			setMeasuredDimension(measuredWidth, measuredHeight)
		}
		else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}

	override fun onPanTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {
		if (!keyboardVisible) {
			return
		}

		webViewContainer.setViewPortByMeasureSuppressed(true)

		var doNotScroll = false
		val openOffset = -swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY()

		if (swipeContainer.getSwipeOffsetY() != openOffset) {
			swipeContainer.stickTo(openOffset)
			doNotScroll = true
		}

		val oldh = contentHeight + parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight()

		setMeasuredDimension(measuredWidth, contentHeight)

		ignoreMeasure = true

		swipeContainer.setSwipeOffsetAnimationDisallowed(true)

		if (!doNotScroll) {
			webViewScrollAnimator?.cancel()
			webViewScrollAnimator = null

			if (webViewContainer.webView != null) {
				val fromY = webViewContainer.webView.scrollY
				val toY = fromY + (oldh - contentHeight)

				webViewScrollAnimator = ValueAnimator.ofInt(fromY, toY).setDuration(250)
				webViewScrollAnimator?.interpolator = ChatListItemAnimator.DEFAULT_INTERPOLATOR

				webViewScrollAnimator?.addUpdateListener {
					val `val` = it.animatedValue as Int

					if (webViewContainer.webView != null) {
						webViewContainer.webView.scrollY = `val`
					}
				}

				webViewScrollAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (webViewContainer.webView != null) {
							webViewContainer.webView.scrollY = toY
						}

						if (animation === webViewScrollAnimator) {
							webViewScrollAnimator = null
						}
					}
				})

				webViewScrollAnimator?.start()
			}
		}
	}

	override fun onPanTransitionEnd() {
		ignoreMeasure = false
		swipeContainer.setSwipeOffsetAnimationDisallowed(false)
		webViewContainer.setViewPortByMeasureSuppressed(false)
		requestLayout()
	}

	override fun onShow(previousLayout: AttachAlertLayout?) {
		parentAlert.actionBar.setTitle(getUserName(MessagesController.getInstance(currentAccount).getUser(botId)))

		swipeContainer.setSwipeOffsetY(0f)

		if (webViewContainer.webView != null) {
			webViewContainer.webView.scrollTo(0, 0)
		}

		webViewContainer.setParentActivity(parentAlert.baseFragment.parentActivity)

		otherItem.visibility = VISIBLE

		if (!webViewContainer.isBackButtonVisible) {
			AndroidUtilities.updateImageViewImageAnimated(parentAlert.actionBar.backButton, R.drawable.ic_close_white)
		}
	}

	override fun onShown() {
		if (webViewContainer.isPageLoaded) {
			requestEnableKeyboard()
		}

		swipeContainer.setSwipeOffsetAnimationDisallowed(false)

		AndroidUtilities.runOnUIThread {
			webViewContainer.restoreButtonData()
		}
	}

	private fun requestEnableKeyboard() {
		val fragment = parentAlert.baseFragment

		if (fragment is ChatActivity && (fragment.contentView?.measureKeyboardHeight() ?: 0) > AndroidUtilities.dp(20f)) {
			AndroidUtilities.hideKeyboard(parentAlert.baseFragment.fragmentView)
			AndroidUtilities.runOnUIThread({ requestEnableKeyboard() }, 250)
			return
		}

		parentAlert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
		isFocusable = true
		parentAlert.isFocusable = true
	}

	override fun onHidden() {
		super.onHidden()
		parentAlert.isFocusable = false
		parentAlert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
	}

	override var currentItemTop: Int
		get() = (swipeContainer.getSwipeOffsetY() + swipeContainer.getOffsetY()).toInt()
		set(currentItemTop) {
			super.currentItemTop = currentItemTop
		}

	override fun setTranslationY(translationY: Float) {
		super.setTranslationY(translationY)
		parentAlert.sheetContainer.invalidate()
	}

	@JvmOverloads
	fun requestWebView(currentAccount: Int, peerId: Long, botId: Long, silent: Boolean, replyToMsgId: Int, startCommand: String? = null) {
		this.currentAccount = currentAccount
		this.peerId = peerId
		this.botId = botId
		this.silent = silent
		this.replyToMsgId = replyToMsgId
		this.startCommand = startCommand

		webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId))
		webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem)

		val req = TLMessagesRequestWebView()
		req.peer = MessagesController.getInstance(currentAccount).getInputPeer(peerId)
		req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId)
		req.silent = silent
		req.platform = "android"

		if (peerId < 0) {
			val chatFull = MessagesController.getInstance(currentAccount).getChatFull(-peerId)

			if (chatFull != null) {
				val peer = chatFull.defaultSendAs

				if (peer != null) {
					req.sendAs = MessagesController.getInstance(currentAccount).getInputPeer(peer)
					req.flags = req.flags or 8192
				}
			}
		}

		if (startCommand != null) {
			req.startParam = startCommand
			req.flags = req.flags or 8
		}

		if (replyToMsgId != 0) {
			req.replyToMsgId = replyToMsgId
			req.flags = req.flags or 1
		}

		try {
			val jsonObject = JSONObject()
			jsonObject.put("bg_color", ResourcesCompat.getColor(resources, R.color.background, null))
			jsonObject.put("secondary_bg_color", ResourcesCompat.getColor(resources, R.color.light_background, null))
			jsonObject.put("text_color", ResourcesCompat.getColor(resources, R.color.text, null))
			jsonObject.put("hint_color", ResourcesCompat.getColor(resources, R.color.hint, null))
			jsonObject.put("link_color", ResourcesCompat.getColor(resources, R.color.brand, null))
			jsonObject.put("button_color", ResourcesCompat.getColor(resources, R.color.brand, null))
			jsonObject.put("button_text_color", ResourcesCompat.getColor(resources, R.color.white, null))
			req.themeParams = TLDataJSON()
			req.themeParams?.data = jsonObject.toString()
			req.flags = req.flags or 4
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is TLWebViewResult) {
					queryId = response.queryId
					webViewContainer.loadUrl(currentAccount, response.url)
					swipeContainer.setWebView(webViewContainer.webView)
					AndroidUtilities.runOnUIThread(pollRunnable)
				}
			}
		}

		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.webViewResultSent)
	}

	override fun onDestroy() {
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.webViewResultSent)
		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.didSetNewTheme)

		val menu = parentAlert.actionBar.createMenu()

		otherItem.removeAllSubItems()

		menu.removeView(otherItem)

		webViewContainer.destroyWebView()

		destroyed = true

		AndroidUtilities.cancelRunOnUIThread(pollRunnable)
	}

	override fun onHide() {
		super.onHide()

		otherItem.visibility = GONE
		isBotButtonAvailable = false

		if (!webViewContainer.isBackButtonVisible) {
			AndroidUtilities.updateImageViewImageAnimated(parentAlert.actionBar.backButton, R.drawable.ic_back_arrow)
		}

		parentAlert.actionBar.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.background, null))

		if (webViewContainer.hasUserPermissions()) {
			webViewContainer.destroyWebView()
			needReload = true
		}
	}

	fun needReload(): Boolean {
		if (needReload) {
			needReload = false
			return true
		}

		return false
	}

	override val listTopPadding: Int
		get() = swipeContainer.getOffsetY().toInt()

	override val firstOffset: Int
		get() = listTopPadding + AndroidUtilities.dp(56f)

	override fun onPreMeasure(availableWidth: Int, availableHeight: Int) {
		var padding = if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
			(availableHeight / 3.5f).toInt()
		}
		else {
			availableHeight / 5 * 2
		}

		parentAlert.setAllowNestedScroll(true)

		if (padding < 0) {
			padding = 0
		}

		if (swipeContainer.getOffsetY() != padding.toFloat()) {
			ignoreLayout = true
			swipeContainer.setOffsetY(padding.toFloat())
			ignoreLayout = false
		}
	}

	override val buttonsHideOffset: Int
		get() = swipeContainer.getTopActionBarOffsetY().toInt() + AndroidUtilities.dp(12f)

	override fun onBackPressed(): Boolean {
		if (webViewContainer.onBackPressed()) {
			return true
		}

		onCheckDismissByUser()

		return true
	}

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	override fun scrollToTop() {
		swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY())
	}

	override fun shouldHideBottomButtons(): Boolean {
		return false
	}

	override fun needsActionBar(): Int {
		return 1
	}

	fun setDelegate(delegate: BotWebViewContainer.Delegate?) {
		webViewContainer.setDelegate(delegate)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.webViewResultSent -> {
				val queryId = args[0] as Long

				if (this.queryId == queryId) {
					webViewContainer.destroyWebView()
					needReload = true
					parentAlert.dismiss()
				}
			}

			NotificationCenter.didSetNewTheme -> {
				webViewContainer.updateFlickerBackgroundColor(ResourcesCompat.getColor(resources, R.color.background, null))
			}
		}
	}

	open class WebViewSwipeContainer(context: Context) : FrameLayout(context) {
		private val gestureDetector: GestureDetector

		var isSwipeInProgress = false
			private set

		private var isSwipeDisallowed = false
		private var topActionBarOffsetY = ActionBar.getCurrentActionBarHeight().toFloat()
		private var offsetY = 0f
		private var pendingOffsetY = -1f
		private var pendingSwipeOffsetY = Int.MIN_VALUE.toFloat()
		private var swipeOffsetY = 0f
		private var isSwipeOffsetAnimationDisallowed = false
		private var offsetYAnimator: SpringAnimation? = null
		private var flingInProgress = false
		private var webView: WebView? = null
		private var scrollListener: Runnable? = null
		private var scrollEndListener: Runnable? = null
		private var delegate: Delegate? = null
		private var scrollAnimator: SpringAnimation? = null
		private var swipeStickyRange = 0
		private var isKeyboardVisible = GenericProvider { _: Void? -> false }

		init {
			val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

			gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
				override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
					if (isSwipeDisallowed) {
						return false
					}

					if (velocityY >= 700 && (webView == null || webView!!.scrollY == 0)) {
						flingInProgress = true

						if (swipeOffsetY >= swipeStickyRange) {
							delegate?.onDismiss()
						}
						else {
							stickTo(0f)
						}

						return true
					}
					else if (velocityY <= -700 && swipeOffsetY > -offsetY + topActionBarOffsetY) {
						flingInProgress = true
						stickTo(-offsetY + topActionBarOffsetY)
						return true
					}

					return true
				}

				override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
					if (!isSwipeInProgress && !isSwipeDisallowed) {
						if (isKeyboardVisible.provide(null) && swipeOffsetY == -offsetY + topActionBarOffsetY) {
							isSwipeDisallowed = true
						}
						else if (abs(distanceY) >= touchSlop && abs(distanceY) * 1.5f >= abs(distanceX) && (swipeOffsetY != -offsetY + topActionBarOffsetY || webView == null || distanceY < 0) && webView?.scrollY == 0) {
							isSwipeInProgress = true

							val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)

							for (i in 0 until childCount) {
								getChildAt(i).dispatchTouchEvent(ev)
							}

							ev.recycle()

							return true
						}
						else if (webView != null && webView!!.canScrollHorizontally(if (distanceX >= 0) 1 else -1)) {
							isSwipeDisallowed = true
						}
					}

					if (isSwipeInProgress) {
						if (distanceY < 0) {
							if (swipeOffsetY > -offsetY + topActionBarOffsetY) {
								swipeOffsetY -= distanceY
							}
							else if (webView != null) {
								val newWebScrollY = (webView?.scrollY ?: 0) + distanceY

								webView?.scrollY = MathUtils.clamp(newWebScrollY, 0f, max(webView?.contentHeight ?: 0, webView?.height ?: 0) - topActionBarOffsetY).toInt()

								if (newWebScrollY < 0) {
									swipeOffsetY -= newWebScrollY
								}
							}
							else {
								swipeOffsetY -= distanceY
							}
						}
						else {
							swipeOffsetY -= distanceY

							if (webView != null && swipeOffsetY < -offsetY + topActionBarOffsetY) {
								val newWebScrollY = (webView?.scrollY ?: 0) - (swipeOffsetY + offsetY - topActionBarOffsetY)
								webView!!.scrollY = MathUtils.clamp(newWebScrollY, 0f, max(webView?.contentHeight ?: 0, webView?.height ?: 0) - topActionBarOffsetY).toInt()
							}
						}

						swipeOffsetY = MathUtils.clamp(swipeOffsetY, -offsetY + topActionBarOffsetY, height - offsetY + topActionBarOffsetY)

						invalidateTranslation()

						return true
					}

					return true
				}
			})

			updateStickyRange()
		}

		fun setIsKeyboardVisible(isKeyboardVisible: GenericProvider<Void?, Boolean>) {
			this.isKeyboardVisible = isKeyboardVisible
		}

		override fun onConfigurationChanged(newConfig: Configuration) {
			super.onConfigurationChanged(newConfig)
			updateStickyRange()
		}

		private fun updateStickyRange() {
			swipeStickyRange = AndroidUtilities.dp((if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) 8 else 64).toFloat())
		}

		override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
			super.requestDisallowInterceptTouchEvent(disallowIntercept)

			if (disallowIntercept) {
				isSwipeDisallowed = true
				isSwipeInProgress = false
			}
		}

		fun setSwipeOffsetAnimationDisallowed(swipeOffsetAnimationDisallowed: Boolean) {
			isSwipeOffsetAnimationDisallowed = swipeOffsetAnimationDisallowed
		}

		fun setScrollListener(scrollListener: Runnable?) {
			this.scrollListener = scrollListener
		}

		fun setScrollEndListener(scrollEndListener: Runnable?) {
			this.scrollEndListener = scrollEndListener
		}

		fun setWebView(webView: WebView?) {
			this.webView = webView
		}

		fun setTopActionBarOffsetY(topActionBarOffsetY: Float) {
			this.topActionBarOffsetY = topActionBarOffsetY
			invalidateTranslation()
		}

		fun setSwipeOffsetY(swipeOffsetY: Float) {
			this.swipeOffsetY = swipeOffsetY
			invalidateTranslation()
		}

		fun setOffsetY(offsetY: Float) {
			if (pendingSwipeOffsetY != Int.MIN_VALUE.toFloat()) {
				pendingOffsetY = offsetY
				return
			}

			offsetYAnimator?.cancel()

			val wasOffsetY = this.offsetY
			val deltaOffsetY = offsetY - wasOffsetY
			val wasOnTop = abs(swipeOffsetY + wasOffsetY - topActionBarOffsetY) <= AndroidUtilities.dp(1f)

			if (!isSwipeOffsetAnimationDisallowed) {
				offsetYAnimator?.cancel()

				offsetYAnimator = SpringAnimation(FloatValueHolder(wasOffsetY)).setSpring(SpringForce(offsetY).setStiffness(1400f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addUpdateListener { _, value, _ ->
					this.offsetY = value
					val progress = (value - wasOffsetY) / deltaOffsetY

					if (wasOnTop) {
						swipeOffsetY = MathUtils.clamp(swipeOffsetY - progress * max(0f, deltaOffsetY), -this.offsetY + topActionBarOffsetY, height - this.offsetY + topActionBarOffsetY)
					}

					if (scrollAnimator?.spring?.finalPosition == -wasOffsetY + topActionBarOffsetY) {
						scrollAnimator?.spring?.finalPosition = -offsetY + topActionBarOffsetY
					}

					invalidateTranslation()

				}.addEndListener { _, canceled, _, _ ->
					offsetYAnimator = null

					if (!canceled) {
						this@WebViewSwipeContainer.offsetY = offsetY
						invalidateTranslation()
					}
					else {
						pendingOffsetY = offsetY
					}
				}

				offsetYAnimator?.start()
			}
			else {
				this.offsetY = offsetY

				if (wasOnTop) {
					swipeOffsetY = MathUtils.clamp(swipeOffsetY - max(0f, deltaOffsetY), -this.offsetY + topActionBarOffsetY, height - this.offsetY + topActionBarOffsetY)
				}

				invalidateTranslation()
			}
		}

		private fun invalidateTranslation() {
			translationY = max(topActionBarOffsetY, offsetY + swipeOffsetY)
			scrollListener?.run()
		}

		fun getTopActionBarOffsetY(): Float {
			return topActionBarOffsetY
		}

		fun getOffsetY(): Float {
			return offsetY
		}

		fun getSwipeOffsetY(): Float {
			return swipeOffsetY
		}

		fun setDelegate(delegate: Delegate?) {
			this.delegate = delegate
		}

		override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
			if (isSwipeInProgress && ev.actionIndex != 0) {
				return false
			}

			val rawEvent = MotionEvent.obtain(ev)
			val index = ev.actionIndex

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				rawEvent.setLocation(ev.getRawX(index), ev.getRawY(index))
			}
			else {
				val offsetX = ev.rawX - ev.x
				val offsetY = ev.rawY - ev.y

				rawEvent.setLocation(ev.getX(index) + offsetX, ev.getY(index) + offsetY)
			}

			val detector = gestureDetector.onTouchEvent(rawEvent)

			rawEvent.recycle()

			if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
				isSwipeDisallowed = false
				isSwipeInProgress = false

				if (flingInProgress) {
					flingInProgress = false
				}
				else {
					if (swipeOffsetY <= -swipeStickyRange) {
						stickTo(-offsetY + topActionBarOffsetY)
					}
					else if (swipeOffsetY > -swipeStickyRange && swipeOffsetY <= swipeStickyRange) {
						stickTo(0f)
					}
					else {
						delegate?.onDismiss()
					}
				}
			}

			val superTouch = super.dispatchTouchEvent(ev)

			return if (!superTouch && !detector && ev.action == MotionEvent.ACTION_DOWN) {
				true
			}
			else {
				superTouch || detector
			}
		}

		@JvmOverloads
		fun stickTo(offset: Float, callback: Runnable? = null) {
			if (swipeOffsetY == offset || scrollAnimator?.spring?.finalPosition == offset) {
				callback?.run()
				scrollEndListener?.run()
				return
			}

			pendingSwipeOffsetY = offset

			offsetYAnimator?.cancel()
			scrollAnimator?.cancel()

			scrollAnimator = SpringAnimation(this, SWIPE_OFFSET_Y, offset).setSpring(SpringForce(offset).setStiffness(1400f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addEndListener { animation, _, _, _ ->
				if (animation === scrollAnimator) {
					scrollAnimator = null

					callback?.run()

					scrollEndListener?.run()

					if (pendingOffsetY != -1f) {
						val wasDisallowed = isSwipeOffsetAnimationDisallowed
						isSwipeOffsetAnimationDisallowed = true
						setOffsetY(pendingOffsetY)
						pendingOffsetY = -1f
						isSwipeOffsetAnimationDisallowed = wasDisallowed
					}

					pendingSwipeOffsetY = Int.MIN_VALUE.toFloat()
				}
			}

			scrollAnimator?.start()
		}

		fun interface Delegate {
			/**
			 * Called to dismiss parent layout
			 */
			fun onDismiss()
		}

		companion object {
			@JvmField
			val SWIPE_OFFSET_Y = SimpleFloatPropertyCompat("swipeOffsetY", { obj: WebViewSwipeContainer -> obj.getSwipeOffsetY() }) { obj: WebViewSwipeContainer, swipeOffsetY: Float ->
				obj.setSwipeOffsetY(swipeOffsetY)
			}
		}
	}

	open class WebProgressView(context: Context?) : View(context) {
		private val loadProgressProperty = SimpleFloatPropertyCompat("loadProgress", { obj: WebProgressView -> obj.loadProgress }) { obj: WebProgressView, loadProgress: Float ->
			obj.setLoadProgress(loadProgress)
		}.setMultiplier(100f)

		private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private var loadProgress = 0f
		private var springAnimation: SpringAnimation? = null

		init {
			bluePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null)
			bluePaint.style = Paint.Style.STROKE
			bluePaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
			bluePaint.strokeCap = Paint.Cap.ROUND
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			springAnimation = SpringAnimation(this, loadProgressProperty).setSpring(SpringForce().setStiffness(400f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			springAnimation?.cancel()
			springAnimation = null
		}

		fun setLoadProgressAnimated(loadProgress: Float) {
			if (springAnimation == null) {
				setLoadProgress(loadProgress)
				return
			}

			springAnimation?.spring?.finalPosition = loadProgress * 100f
			springAnimation?.start()
		}

		fun setLoadProgress(loadProgress: Float) {
			this.loadProgress = loadProgress
			invalidate()
		}

		override fun draw(canvas: Canvas) {
			super.draw(canvas)
			val y = height - bluePaint.strokeWidth / 2f
			canvas.drawLine(0f, y, width * loadProgress, y, bluePaint)
		}
	}

	companion object {
		private const val POLL_PERIOD = 60000
	}
}
