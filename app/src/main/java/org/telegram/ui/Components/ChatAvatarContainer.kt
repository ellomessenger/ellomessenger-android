/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2025.
 * Copyright Shamil Afandiyev, Ello 2024-2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.ChatObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject
import org.telegram.messenger.utils.combineDrawables
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.tgnet.expires
import org.telegram.tgnet.participants
import org.telegram.tgnet.status
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.WrapSizeDrawable
import org.telegram.ui.Components.sharedmedia.SharedMediaPreloader
import org.telegram.ui.ProfileActivity
import java.util.Locale
import kotlin.math.min

open class ChatAvatarContainer(context: Context, private val parentFragment: ChatActivity?, needTime: Boolean) : FrameLayout(context), NotificationCenterDelegate {
	private val statusDrawables = arrayOfNulls<StatusDrawable>(6)
	private val avatarDrawable = AvatarDrawable()
	private val currentAccount = UserConfig.selectedAccount
	private val isOnline = BooleanArray(1)
	private val emojiStatusDrawable: SwapAnimatedEmojiDrawable
	private var titleTextLargerCopyView: SimpleTextView? = null
	private var subtitleTextLargerCopyView: SimpleTextView? = null
	private var timerDrawable: TimerDrawable? = null
	private var occupyStatusBar = true
	private var leftPadding = AndroidUtilities.dp(8f)
	private var lastWidth = -1
	private var largerWidth = -1
	private var titleAnimation: AnimatorSet? = null
	private var secretChatTimer = false
	private var onlineCount = -1
	private var currentConnectionState = 0
	private var lastSubtitle: CharSequence? = null
	private var overrideSubtitleColor: Int? = null
	private var rightDrawableIsScamOrVerified = false
	private var rightDrawableContentDescription: String? = null
	private var currentTypingDrawable: StatusDrawable? = null
	var sharedMediaPreloader: SharedMediaPreloader? = null
	val avatarImageView: BackupImageView
	val titleTextView: SimpleTextView
	val subtitleTextView: SimpleTextView
	var statusMadeShorter = BooleanArray(1)
	var timeItem: ImageView? = null

	@JvmField
	var allowShorterStatus = false

	@JvmField
	var premiumIconHidable = false

	init {
		val avatarClickable = parentFragment != null && parentFragment.chatMode == 0 && !UserObject.isReplyUser(parentFragment.currentUser)

		avatarImageView = object : BackupImageView(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)

				if (avatarClickable && imageReceiver.hasNotThumb()) {
					info.text = context.getString(R.string.AccDescrProfilePicture)
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.Open)))
				}
				else {
					info.isVisibleToUser = false
				}
			}
		}

		if (parentFragment != null) {
			sharedMediaPreloader = SharedMediaPreloader(parentFragment)

			if (parentFragment.isThreadChat || parentFragment.chatMode == 2) {
				avatarImageView.setVisibility(GONE)
			}
		}

		avatarImageView.setContentDescription(context.getString(R.string.AccDescrProfilePicture))
		avatarImageView.setRoundRadius(AndroidUtilities.dp(19f))

		addView(avatarImageView)

		if (avatarClickable) {
			avatarImageView.setOnClickListener {
				openProfile(true)
			}
		}

		titleTextView = object : SimpleTextView(context) {
			override fun setText(value: CharSequence?): Boolean {
				titleTextLargerCopyView?.setText(value)
				return super.setText(value)
			}

			override fun setTranslationY(translationY: Float) {
				titleTextLargerCopyView?.translationY = translationY
				super.setTranslationY(translationY)
			}
		}

		titleTextView.setEllipsizeByGradient(true)
		titleTextView.textColor = ResourcesCompat.getColor(resources, R.color.text, null)
		titleTextView.setTextSize(17)
		titleTextView.setGravity(Gravity.LEFT)
		titleTextView.setTypeface(Theme.TYPEFACE_BOLD)
		titleTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f))
		titleTextView.setCanHideRightDrawable(false)
		titleTextView.rightDrawableOutside = true
		titleTextView.setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(12f))

		addView(titleTextView)

		subtitleTextView = object : SimpleTextView(context) {
			override fun setText(value: CharSequence?): Boolean {
				subtitleTextLargerCopyView?.setText(value)
				return super.setText(value)
			}

			override fun setTranslationY(translationY: Float) {
				subtitleTextLargerCopyView?.translationY = translationY
				super.setTranslationY(translationY)
			}
		}

		subtitleTextView.setEllipsizeByGradient(true)
		subtitleTextView.textColor = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
		subtitleTextView.setTextSize(14)
		subtitleTextView.setGravity(Gravity.LEFT)
		subtitleTextView.setPadding(0, 0, AndroidUtilities.dp(10f), 0)

		addView(subtitleTextView)

		if (parentFragment != null) {
			timeItem = ImageView(context)
			timeItem?.setPadding(AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(5f), AndroidUtilities.dp(5f))
			timeItem?.scaleType = ImageView.ScaleType.CENTER
			timeItem?.alpha = 0.0f
			timeItem?.scaleY = 0.0f
			timeItem?.scaleX = 0.0f
			timeItem?.visibility = GONE
			timeItem?.setImageDrawable(TimerDrawable(context, null).also { timerDrawable = it })

			addView(timeItem)

			secretChatTimer = needTime

			timeItem?.setOnClickListener {
				if (secretChatTimer) {
					parentFragment.currentEncryptedChat?.let { encryptedChat ->
						parentFragment.showDialog(AlertsCreator.createTTLAlert(it.context, encryptedChat).create())
					}
				}
				else {
					openSetTimer()
				}
			}

			if (secretChatTimer) {
				timeItem?.contentDescription = context.getString(R.string.SetTimer)
			}
			else {
				timeItem?.contentDescription = context.getString(R.string.AccAutoDeleteTimer)
			}
		}

		if (parentFragment != null && parentFragment.chatMode == 0) {
			if (!parentFragment.isThreadChat && !UserObject.isReplyUser(parentFragment.currentUser)) {
				setOnClickListener {
					openProfile(false)
				}
			}

			val chat = parentFragment.currentChat

			statusDrawables[0] = TypingDotsDrawable(true)
			statusDrawables[1] = RecordStatusDrawable(true)
			statusDrawables[2] = SendingFileDrawable(true)
			statusDrawables[3] = PlayingGameDrawable(false, null)
			statusDrawables[4] = RoundStatusDrawable(true)
			statusDrawables[5] = ChoosingStickerStatusDrawable(true)

			for (statusDrawable in statusDrawables) {
				statusDrawable?.setIsChat(chat != null)
			}
		}

		emojiStatusDrawable = SwapAnimatedEmojiDrawable(titleTextView, AndroidUtilities.dp(24f))
	}

	fun setTitleExpand(titleExpand: Boolean) {
		val newRightPadding = if (titleExpand) AndroidUtilities.dp(10f) else 0

		if (titleTextView.paddingRight != newRightPadding) {
			titleTextView.setPadding(0, AndroidUtilities.dp(6f), newRightPadding, AndroidUtilities.dp(12f))
			requestLayout()
			invalidate()
		}
	}

	fun setOverrideSubtitleColor(overrideSubtitleColor: Int?) {
		this.overrideSubtitleColor = overrideSubtitleColor
	}

	private fun openSetTimer(): Boolean {
		if (parentFragment?.parentActivity == null) {
			return false
		}

		val chat = parentFragment.currentChat

		if (chat != null && !ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES)) {
			if (timeItem?.tag != null) {
				parentFragment.showTimerHint()
			}

			return false
		}

		val chatInfo = parentFragment.currentChatInfo
		val userInfo = parentFragment.currentUserInfo
		var ttl = 0

		if (userInfo != null) {
			ttl = userInfo.ttlPeriod
		}
		else if (chatInfo != null) {
			ttl = chatInfo.ttlPeriod
		}

		val scrimPopupWindow = arrayOfNulls<ActionBarPopupWindow>(1)

		val autoDeletePopupWrapper = AutoDeletePopupWrapper(context, null, object : AutoDeletePopupWrapper.Callback {
			override fun dismiss() {
				scrimPopupWindow[0]?.dismiss()
			}

			override fun setAutoDeleteHistory(time: Int, action: Int) {
				parentFragment.messagesController.setDialogHistoryTTL(parentFragment.dialogId, time)

				@Suppress("NAME_SHADOWING") val chatInfo = parentFragment.currentChatInfo
				@Suppress("NAME_SHADOWING") val userInfo = parentFragment.currentUserInfo

				if (userInfo != null || chatInfo != null) {
					parentFragment.undoView?.showWithAction(parentFragment.dialogId, action, parentFragment.currentUser, userInfo?.ttlPeriod ?: chatInfo?.ttlPeriod, null, null)
				}
			}
		}, true)

		autoDeletePopupWrapper.updateItems(ttl)

		scrimPopupWindow[0] = object : ActionBarPopupWindow(autoDeletePopupWrapper.windowLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
			override fun dismiss() {
				super.dismiss()
				parentFragment.dimBehindView(false)
			}
		}

		scrimPopupWindow[0]?.setPauseNotifications(true)
		scrimPopupWindow[0]?.setDismissAnimationDuration(220)
		scrimPopupWindow[0]?.isOutsideTouchable = true
		scrimPopupWindow[0]?.isClippingEnabled = true
		scrimPopupWindow[0]?.animationStyle = R.style.PopupContextAnimation
		scrimPopupWindow[0]?.isFocusable = true

		autoDeletePopupWrapper.windowLayout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST))

		scrimPopupWindow[0]?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
		scrimPopupWindow[0]?.contentView?.isFocusableInTouchMode = true
		scrimPopupWindow[0]?.showAtLocation(avatarImageView, 0, (avatarImageView.x + x).toInt(), avatarImageView.y.toInt())

		parentFragment.dimBehindView(true)

		return true
	}

	private fun openProfile(byAvatar: Boolean) {
		@Suppress("NAME_SHADOWING") var byAvatar = byAvatar

		if (byAvatar && (AndroidUtilities.isTablet() || AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y || !avatarImageView.imageReceiver.hasNotThumb())) {
			byAvatar = false
		}

		val parentFragment = parentFragment ?: return

		val user = parentFragment.currentUser
		val chat = parentFragment.currentChat
		val imageReceiver = avatarImageView.imageReceiver
		val key = imageReceiver.imageKey
		val imageLoader = ImageLoader.getInstance()

		if (key != null && !imageLoader.isInMemCache(key, false)) {
			val drawable = imageReceiver.drawable

			if (drawable is BitmapDrawable && drawable !is AnimatedFileDrawable) {
				imageLoader.putImageToCache(drawable, key, false)
			}
		}

		if (user != null) {
			val args = Bundle()

			if (UserObject.isUserSelf(user)) {
				args.putLong("dialog_id", parentFragment.dialogId)

				val media = IntArray(MediaDataController.MEDIA_TYPES_COUNT)

				System.arraycopy(sharedMediaPreloader!!.lastMediaCount, 0, media, 0, media.size)

				val fragment = MediaActivity(args, sharedMediaPreloader)
				fragment.setChatInfo(parentFragment.currentChatInfo)

				parentFragment.presentFragment(fragment)
			}
			else {
				args.putLong("user_id", user.id)
				args.putBoolean("reportSpam", parentFragment.hasReportSpam())

				if (timeItem != null) {
					args.putLong("dialog_id", parentFragment.dialogId)
				}

				args.putInt("actionBarColor", ResourcesCompat.getColor(context.resources, R.color.background, null)) // TODO: check if this color is correct

				val fragment = ProfileActivity(args, sharedMediaPreloader)
				fragment.userInfo = parentFragment.currentUserInfo
				fragment.setPlayProfileAnimation(if (byAvatar) 2 else 1)

				parentFragment.presentFragment(fragment)
			}
		}
		else if (chat != null) {
			val args = Bundle()
			args.putLong("chat_id", chat.id)

			val fragment = ProfileActivity(args, sharedMediaPreloader)

			fragment.setChatInfo(parentFragment.currentChatInfo)
			fragment.setPlayProfileAnimation(if (byAvatar) 2 else 1)
			parentFragment.presentFragment(fragment)
		}
	}

	fun setOccupyStatusBar(value: Boolean) {
		occupyStatusBar = value
	}

	fun setTitleColors(title: Int, subtitle: Int) {
		titleTextView.textColor = title
		subtitleTextView.textColor = subtitle
		subtitleTextView.tag = subtitle
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val width = MeasureSpec.getSize(widthMeasureSpec) + titleTextView.paddingRight
		val availableWidth = width - AndroidUtilities.dp(((if (avatarImageView.isVisible) 54 else 0) + 16).toFloat())

		avatarImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42f), MeasureSpec.EXACTLY))
		titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((24 + 8).toFloat()) + titleTextView.paddingRight, MeasureSpec.AT_MOST))
		subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.AT_MOST))
		timeItem?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(34f), MeasureSpec.EXACTLY))

		setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec))

		if (lastWidth != -1 && lastWidth != width && lastWidth > width) {
			fadeOutToLessWidth(lastWidth)
		}

		if (titleTextLargerCopyView != null) {
			val largerAvailableWidth = largerWidth - AndroidUtilities.dp(((if (avatarImageView.isVisible) 54 else 0) + 16).toFloat())
			titleTextLargerCopyView?.measure(MeasureSpec.makeMeasureSpec(largerAvailableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.AT_MOST))
		}

		lastWidth = width
	}

	private fun fadeOutToLessWidth(largerWidth: Int) {
		this.largerWidth = largerWidth

		if (titleTextLargerCopyView != null) {
			removeView(titleTextLargerCopyView)
		}

		titleTextLargerCopyView = SimpleTextView(context)
		titleTextLargerCopyView?.textColor = ResourcesCompat.getColor(resources, R.color.text, null)
		titleTextLargerCopyView?.setTextSize(18)
		titleTextLargerCopyView?.setGravity(Gravity.LEFT)
		titleTextLargerCopyView?.setTypeface(Theme.TYPEFACE_BOLD)
		titleTextLargerCopyView?.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f))
		titleTextLargerCopyView?.rightDrawable = titleTextView.rightDrawable
		titleTextLargerCopyView?.rightDrawableOutside = titleTextView.rightDrawableOutside
		titleTextLargerCopyView?.leftDrawable = titleTextView.leftDrawable
		titleTextLargerCopyView?.setText(titleTextView.getText())

		titleTextLargerCopyView?.animate()?.alpha(0f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)?.withEndAction {
			if (titleTextLargerCopyView != null) {
				removeView(titleTextLargerCopyView)
				titleTextLargerCopyView = null
			}
		}?.start()

		addView(titleTextLargerCopyView)

		subtitleTextLargerCopyView = SimpleTextView(context)
		subtitleTextLargerCopyView?.textColor = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
		subtitleTextLargerCopyView?.setTextSize(14)
		subtitleTextLargerCopyView?.setGravity(Gravity.LEFT)
		subtitleTextLargerCopyView?.setText(subtitleTextView.getText())

		subtitleTextLargerCopyView?.animate()?.alpha(0f)?.setDuration(350)?.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)?.withEndAction {
			if (subtitleTextLargerCopyView != null) {
				removeView(subtitleTextLargerCopyView)
				subtitleTextLargerCopyView = null
				clipChildren = true
			}
		}?.start()

		addView(subtitleTextLargerCopyView)

		clipChildren = false
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val actionBarHeight = ActionBar.getCurrentActionBarHeight()
		val viewTop = (actionBarHeight - AndroidUtilities.dp(42f)) / 2 + if (occupyStatusBar) AndroidUtilities.statusBarHeight else 0

		avatarImageView.layout(leftPadding, viewTop + 1, leftPadding + AndroidUtilities.dp(42f), viewTop + 1 + AndroidUtilities.dp(42f))

		val l = leftPadding + if (avatarImageView.isVisible) AndroidUtilities.dp(54f) else 0

		if (subtitleTextView.visibility != GONE) {
			titleTextView.layout(l, viewTop + AndroidUtilities.dp(1.3f) - titleTextView.paddingTop, l + titleTextView.measuredWidth, viewTop + titleTextView.textHeight + AndroidUtilities.dp(1.3f) - titleTextView.paddingTop + titleTextView.paddingBottom)
			titleTextLargerCopyView?.layout(l, viewTop + AndroidUtilities.dp(1.3f), l + titleTextLargerCopyView!!.measuredWidth, viewTop + titleTextLargerCopyView!!.textHeight + AndroidUtilities.dp(1.3f))
		}
		else {
			titleTextView.layout(l, viewTop + AndroidUtilities.dp(11f) - titleTextView.paddingTop, l + titleTextView.measuredWidth, viewTop + titleTextView.textHeight + AndroidUtilities.dp(11f) - titleTextView.paddingTop + titleTextView.paddingBottom)
			titleTextLargerCopyView?.layout(l, viewTop + AndroidUtilities.dp(11f), l + titleTextLargerCopyView!!.measuredWidth, viewTop + titleTextLargerCopyView!!.textHeight + AndroidUtilities.dp(11f))
		}

		timeItem?.layout(leftPadding + AndroidUtilities.dp(16f), viewTop + AndroidUtilities.dp(15f), leftPadding + AndroidUtilities.dp((16 + 34).toFloat()), viewTop + AndroidUtilities.dp((15 + 34).toFloat()))
		subtitleTextView.layout(l, viewTop + AndroidUtilities.dp(24f), l + subtitleTextView.measuredWidth, viewTop + subtitleTextView.textHeight + AndroidUtilities.dp(24f))
		subtitleTextLargerCopyView?.layout(l, viewTop + AndroidUtilities.dp(24f), l + subtitleTextLargerCopyView!!.measuredWidth, viewTop + subtitleTextLargerCopyView!!.textHeight + AndroidUtilities.dp(24f))
	}

	fun setLeftPadding(value: Int) {
		leftPadding = value
	}

	fun showTimeItem(animated: Boolean) {
		if (timeItem == null || timeItem?.tag != null || avatarImageView.visibility != VISIBLE) {
			return
		}

		timeItem?.clearAnimation()
		timeItem?.visibility = VISIBLE
		timeItem?.tag = 1

		if (animated) {
			timeItem?.animate()?.setDuration(180)?.alpha(1.0f)?.scaleX(1.0f)?.scaleY(1.0f)?.setListener(null)?.start()
		}
		else {
			timeItem?.alpha = 1.0f
			timeItem?.scaleY = 1.0f
			timeItem?.scaleX = 1.0f
		}
	}

	fun hideTimeItem(animated: Boolean) {
		if (timeItem == null || timeItem?.tag == null) {
			return
		}

		timeItem?.clearAnimation()
		timeItem?.tag = null

		if (animated) {
			timeItem?.animate()?.setDuration(180)?.alpha(0.0f)?.scaleX(0.0f)?.scaleY(0.0f)?.setListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					timeItem?.visibility = GONE
					super.onAnimationEnd(animation)
				}
			})?.start()
		}
		else {
			timeItem?.visibility = GONE
			timeItem?.alpha = 0.0f
			timeItem?.scaleY = 0.0f
			timeItem?.scaleX = 0.0f
		}
	}

	fun setTime(value: Int, animated: Boolean) {
		if (timerDrawable == null) {
			return
		}

		val show = value != 0 || secretChatTimer

		if (show) {
			showTimeItem(animated)
			timerDrawable?.setTime(value)
		}
		else {
			hideTimeItem(animated)
		}
	}

	fun setTitleIcons(leftIcon: Drawable?) {
		titleTextView.leftDrawable = leftIcon
		titleTextLargerCopyView?.leftDrawable = leftIcon
	}

	fun setVerifiedMuteIcon(isMuted: Boolean, verified: Boolean?, isDonated: Boolean, isUserSelf: Boolean) {
		val drawables = mutableListOf<Drawable>()

		if (!isUserSelf) {
			when {
				verified == true && isDonated -> {
					ResourcesCompat.getDrawable(resources, R.drawable.verified_donated_icon, null)?.let { drawables.add(it) }
				}

				verified == true -> {
					ResourcesCompat.getDrawable(resources, R.drawable.verified_icon, null)?.let { drawables.add(it) }
					rightDrawableContentDescription = context.getString(R.string.AccDescrVerified)
				}

				isDonated -> {
					ResourcesCompat.getDrawable(resources, R.drawable.donated, null)?.let { drawables.add(it) }
				}
			}
		}
		if (isMuted) {
			ResourcesCompat.getDrawable(context!!.resources, R.drawable.volume_slash, null)?.let { drawables.add(it) }
			rightDrawableContentDescription = context.getString(R.string.NotificationsMuted)
		}

		titleTextView.setDrawablePadding(AndroidUtilities.dp(4f))
		titleTextView.rightDrawable = combineDrawables(36, drawables)
	}

	fun setTitle(value: CharSequence?) {
		setTitle(value, scam = false, fake = false, premium = false, emojiStatus = null, animated = false)
	}

	fun setTitle(value: CharSequence?, scam: Boolean, fake: Boolean, premium: Boolean, emojiStatus: TLRPC.EmojiStatus?, animated: Boolean) {
		@Suppress("NAME_SHADOWING") var value = value

		if (value != null) {
			value = Emoji.replaceEmoji(value, titleTextView.paint.fontMetricsInt, false)
		}

		titleTextView.setText(value)

		if (scam || fake) {
			if (titleTextView.rightDrawable !is ScamDrawable) {
				val drawable = ScamDrawable(11, if (scam) 0 else 1)
				drawable.setColor(subtitleTextView.textColor)

				titleTextView.rightDrawable = drawable
				rightDrawableContentDescription = context.getString(R.string.ScamMessage)
				rightDrawableIsScamOrVerified = true
			}
		}
		else if (premium) {
			if (titleTextView.rightDrawable is WrapSizeDrawable && (titleTextView.rightDrawable as WrapSizeDrawable).drawable is AnimatedEmojiDrawable) {
				((titleTextView.rightDrawable as WrapSizeDrawable).drawable as AnimatedEmojiDrawable).removeView(titleTextView)
			}

			if (emojiStatus is TLRPC.TLEmojiStatus) {
				emojiStatusDrawable[emojiStatus.documentId] = animated
			}
			else if (emojiStatus is TLRPC.TLEmojiStatusUntil && emojiStatus.until > (System.currentTimeMillis() / 1000).toInt()) {
				emojiStatusDrawable[emojiStatus.documentId] = animated
			}
			else {
				val drawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_premium_liststar)!!.mutate()
				drawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
				emojiStatusDrawable[drawable] = animated
			}

			emojiStatusDrawable.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
			titleTextView.rightDrawable = emojiStatusDrawable
			rightDrawableIsScamOrVerified = true
			rightDrawableContentDescription = context.getString(R.string.AccDescrPremium)
		}
		else if (titleTextView.rightDrawable is ScamDrawable) {
			titleTextView.rightDrawable = null
			rightDrawableIsScamOrVerified = false
			rightDrawableContentDescription = null
		}
	}

	fun setSubtitle(value: CharSequence) {
		if (lastSubtitle == null) {
			subtitleTextView.setText(value)
		}
		else {
			lastSubtitle = value
		}
	}

	fun onDestroy() {
		parentFragment?.let {
			sharedMediaPreloader?.onDestroy(it)
		}
	}

	@ColorInt
	private fun getTypingColor(): Int {
		val resId = if (parentFragment?.currentUser?.id == BuildConfig.AI_BOT_ID) {
			R.color.brand
		}
		else {
			R.color.dark_gray
		}

		return context.getColor(resId)
	}

	private fun setTypingAnimation(start: Boolean) {
		if (start) {
			try {
				val parentFragment = parentFragment ?: return
				val type = MessagesController.getInstance(currentAccount).getPrintingStringType(parentFragment.dialogId, parentFragment.threadId) ?: return
				val color: Int

				if (type == 5) {
					color = context.getColor(R.color.dark_gray)

					subtitleTextView.replaceTextWithDrawable(statusDrawables[type], "**oo**")
					subtitleTextView.leftDrawable = null
				}
				else {
					color = getTypingColor()

					subtitleTextView.replaceTextWithDrawable(null, null)
					subtitleTextView.leftDrawable = statusDrawables[type]
				}

				subtitleTextView.textColor = color

				statusDrawables[type]?.setColor(color)

				currentTypingDrawable = statusDrawables[type]

				statusDrawables.forEachIndexed { index, statusDrawable ->
					if (index == type) {
						statusDrawable?.start()
					}
					else {
						statusDrawable?.stop()
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
		else {
			currentTypingDrawable = null
			subtitleTextView.leftDrawable = null
			subtitleTextView.replaceTextWithDrawable(null, null)

			for (statusDrawable in statusDrawables) {
				statusDrawable?.stop()
			}
		}
	}

	@JvmOverloads
	fun updateSubtitle(animated: Boolean = false) {
		if (parentFragment == null) {
			return
		}

		var user = parentFragment.currentUser

		if (UserObject.isUserSelf(user) || UserObject.isReplyUser(user) || parentFragment.chatMode != 0) {
			if (subtitleTextView.visibility != GONE) {
				subtitleTextView.visibility = GONE
			}

			return
		}

		val chat = parentFragment.currentChat
		var printString = MessagesController.getInstance(currentAccount).getPrintingString(parentFragment.dialogId, parentFragment.threadId, false)

		if (printString != null) {
			printString = TextUtils.replace(printString, arrayOf("..."), arrayOf(""))
		}

		var newSubtitle: CharSequence

		if (printString.isNullOrEmpty() || ChatObject.isChannel(chat) && !chat.megagroup) {
			if (parentFragment.isThreadChat) {
				if (titleTextView.tag != null) {
					return
				}

				titleTextView.tag = 1

				titleAnimation?.cancel()
				titleAnimation = null

				if (animated) {
					titleAnimation = AnimatorSet()
					titleAnimation?.playTogether(ObjectAnimator.ofFloat(titleTextView, TRANSLATION_Y, AndroidUtilities.dp(9.7f).toFloat()), ObjectAnimator.ofFloat(subtitleTextView, ALPHA, 0.0f))

					titleAnimation?.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationCancel(animation: Animator) {
							titleAnimation = null
						}

						override fun onAnimationEnd(animation: Animator) {
							if (titleAnimation == animation) {
								subtitleTextView.visibility = INVISIBLE
								titleAnimation = null
							}
						}
					})

					titleAnimation?.duration = 180
					titleAnimation?.start()
				}
				else {
					titleTextView.translationY = AndroidUtilities.dp(9.7f).toFloat()
					subtitleTextView.alpha = 0.0f
					subtitleTextView.visibility = INVISIBLE
				}

				return
			}

			setTypingAnimation(false)

			if (chat != null) {
				val info = parentFragment.currentChatInfo

				if (ChatObject.isChannel(chat)) {
					newSubtitle = if (info != null && info.participantsCount != 0) {
						if (chat.megagroup) {
							if (onlineCount > 1) {
								String.format("%s, %s", LocaleController.formatPluralString("Members", info.participantsCount), LocaleController.formatPluralString("OnlineCount", min(onlineCount, info.participantsCount)))
							}
							else {
								LocaleController.formatPluralString("Members", info.participantsCount)
							}
						}
						else {
							val result = IntArray(1)
							val shortNumber = LocaleController.formatShortNumber(info.participantsCount, result)

							if (chat.megagroup) {
								LocaleController.formatPluralString("Members", result[0]).replace(String.format(Locale.getDefault(), "%d", result[0]), shortNumber)
							}
							else {
								LocaleController.formatPluralString("Subscribers", result[0]).replace(String.format(Locale.getDefault(), "%d", result[0]), shortNumber)
							}
						}
					}
					else {
						if (chat.megagroup) {
							if (info == null) {
								context.getString(R.string.Loading).lowercase()
							}
							else {
								if (chat.hasGeo) {
									context.getString(R.string.MegaLocation).lowercase()
								}
								else if (!chat.username.isNullOrEmpty()) {
									context.getString(R.string.MegaPublic).lowercase()
								}
								else {
									context.getString(R.string.MegaPrivate).lowercase()
								}
							}
						}
						else {
							if (ChatObject.isMasterclass(chat)) {
								context.getString(R.string.masterclass).lowercase()
							}
							else if (ChatObject.isSubscriptionChannel(chat) || ChatObject.isPaidChannel(chat)) {
								context.getString(R.string.paid_channel).lowercase()
							}
							else if (chat.flags and TLRPC.CHAT_FLAG_IS_PUBLIC != 0) {
								context.getString(R.string.ChannelPublic).lowercase()
							}
							else {
								context.getString(R.string.ChannelPrivate).lowercase()
							}
						}
					}
				}
				else {
					if (ChatObject.isKickedFromChat(chat)) {
						newSubtitle = context.getString(R.string.YouWereKicked)
					}
					else if (ChatObject.isLeftFromChat(chat)) {
						newSubtitle = context.getString(R.string.YouLeft)
					}
					else {
						var count = chat.participantsCount

						if (info?.participants != null) {
							count = info.participants?.participants?.size ?: 0
						}

						newSubtitle = if (onlineCount > 1 && count != 0) {
							String.format("%s, %s", LocaleController.formatPluralString("Members", count), LocaleController.formatPluralString("OnlineCount", onlineCount))
						}
						else {
							LocaleController.formatPluralString("Members", count)
						}
					}
				}
			}
			else if (user != null) {
				val newUser = MessagesController.getInstance(currentAccount).getUser(user.id)

				if (newUser != null) {
					user = newUser
				}

				val newStatus: String

				if (UserObject.isReplyUser(user)) {
					newStatus = ""
				}
				else if (user.id == getInstance(currentAccount).getClientUserId()) {
					newStatus = context.getString(R.string.ChatYourSelf)
				}
				else if (user.id == 333000L || user.id == BuildConfig.NOTIFICATIONS_BOT_ID || user.id == 42777L) {
					newStatus = context.getString(R.string.ServiceNotifications)
				}
				else if (MessagesController.isSupportUser(user)) {
					newStatus = context.getString(R.string.SupportStatus)
				}
				else if (user.bot) {
					newStatus = if (user.id == BuildConfig.SUPPORT_BOT_ID) context.getString(R.string.customer_service) else context.getString(R.string.Bot)
				}
				else {
					isOnline[0] = false
					newStatus = LocaleController.formatUserStatus(currentAccount, user, isOnline, if (allowShorterStatus) statusMadeShorter else null)
				}

				newSubtitle = newStatus
			}
			else {
				newSubtitle = ""
			}
		}
		else {
			if (parentFragment.isThreadChat) {
				if (titleTextView.tag != null) {
					titleTextView.tag = null
					subtitleTextView.visibility = VISIBLE

					titleAnimation?.cancel()
					titleAnimation = null

					if (animated) {
						titleAnimation = AnimatorSet()
						titleAnimation?.playTogether(ObjectAnimator.ofFloat(titleTextView, TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(subtitleTextView, ALPHA, 1.0f))

						titleAnimation?.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								titleAnimation = null
							}
						})

						titleAnimation?.duration = 180
						titleAnimation?.start()
					}
					else {
						titleTextView.translationY = 0.0f
						subtitleTextView.alpha = 1.0f
					}
				}
			}

			newSubtitle = printString

			if (MessagesController.getInstance(currentAccount).getPrintingStringType(parentFragment.dialogId, parentFragment.threadId) == 5) {
				newSubtitle = Emoji.replaceEmoji(newSubtitle, subtitleTextView.textPaint.fontMetricsInt, false) ?: newSubtitle
			}

			setTypingAnimation(true)
		}

		if (lastSubtitle == null) {
			subtitleTextView.setText(newSubtitle)

			val color = if (subtitleTextView.getText().contains(context.getString(R.string.Typing))) {
				getTypingColor()
			}
			else {
				ResourcesCompat.getColor(resources, R.color.dark_gray, null)
			}

			subtitleTextView.textColor = overrideSubtitleColor ?: color
		}
		else {
			lastSubtitle = newSubtitle
		}
	}

	fun setChatAvatar(chat: TLRPC.Chat?) {
		avatarDrawable.setInfo(chat)
		avatarImageView.setForUserOrChat(chat, avatarDrawable)
	}

	fun setUserAvatar(user: User?) {
		setUserAvatar(user, false)
	}

	fun setUserAvatar(user: User?, showSelf: Boolean) {
		avatarDrawable.setInfo(user)

		if (UserObject.isReplyUser(user)) {
			avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES
			avatarDrawable.setSmallSize(true)
			avatarImageView.setImage(null, null, avatarDrawable, user)
		}
		else if (UserObject.isUserSelf(user) && !showSelf) {
			avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
			avatarDrawable.setSmallSize(true)
			avatarImageView.setImage(null, null, avatarDrawable, user)
		}
		else {
			avatarDrawable.setSmallSize(false)
			avatarImageView.setForUserOrChat(user, avatarDrawable)
		}
	}

	fun checkAndUpdateAvatar() {
		if (parentFragment == null) {
			return
		}

		val user = parentFragment.currentUser
		val chat = parentFragment.currentChat

		if (user != null) {
			avatarDrawable.setInfo(user)

			if (UserObject.isReplyUser(user)) {
				avatarDrawable.setSmallSize(true)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES
				avatarImageView.setImage(null, null, avatarDrawable, user)
			}
			else if (UserObject.isUserSelf(user)) {
				avatarDrawable.setSmallSize(true)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
				avatarImageView.setImage(null, null, avatarDrawable, user)
			}
			else {
				avatarDrawable.setSmallSize(false)
				avatarImageView.setForUserOrChat(user, avatarDrawable)
			}
		}
		else if (chat != null) {
			avatarDrawable.setInfo(chat)
			avatarImageView.setForUserOrChat(chat, avatarDrawable)
		}
	}

	fun updateOnlineCount() {
		if (parentFragment == null) {
			return
		}

		onlineCount = 0

		val info = parentFragment.currentChatInfo ?: return
		val currentTime = ConnectionsManager.getInstance(currentAccount).currentTime

		if (info is TLRPC.TLChatFull || info is TLRPC.TLChannelFull && info.participantsCount <= 200 && info.participants != null) {
			for (a in info.participants!!.participants!!.indices) {
				val participant = info.participants!!.participants!![a]
				val user = MessagesController.getInstance(currentAccount).getUser(participant.userId)

				user?.status?.let {
					if ((it.expires > currentTime || user.id == getInstance(currentAccount).getClientUserId()) && it.expires > 10000) {
						onlineCount++
					}
				}
			}
		}
		else if (info is TLRPC.TLChannelFull && info.participantsCount > 200) {
			onlineCount = info.onlineCount
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (parentFragment != null) {
			NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState)
			NotificationCenter.globalInstance.addObserver(this, NotificationCenter.emojiLoaded)

			currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState()

			updateCurrentConnectionState()
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		if (parentFragment != null) {
			NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState)
			NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.emojiLoaded)
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.didUpdateConnectionState -> {
				val state = ConnectionsManager.getInstance(currentAccount).getConnectionState()

				if (currentConnectionState != state) {
					currentConnectionState = state
					updateCurrentConnectionState()
				}
			}

			NotificationCenter.emojiLoaded -> {
				titleTextView.invalidate()
				subtitleTextView.invalidate()
				invalidate()
			}
		}
	}

	private fun updateCurrentConnectionState() {
		val title = when (currentConnectionState) {
			ConnectionsManager.ConnectionStateWaitingForNetwork -> context.getString(R.string.WaitingForNetwork)
			ConnectionsManager.ConnectionStateConnecting -> context.getString(R.string.Connecting)
			ConnectionsManager.ConnectionStateUpdating -> context.getString(R.string.Updating)
			ConnectionsManager.ConnectionStateConnectingToProxy -> context.getString(R.string.ConnectingToProxy)
			else -> null
		}

		if (title == null) {
			if (lastSubtitle != null) {
				subtitleTextView.setText(lastSubtitle)
				lastSubtitle = null

				val color = if (subtitleTextView.getText().contains(context.getString(R.string.Typing))) {
					getTypingColor()
				}
				else {
					ResourcesCompat.getColor(resources, R.color.dark_gray, null)
				}

				subtitleTextView.textColor = overrideSubtitleColor ?: color
			}
		}
		else {
			if (lastSubtitle == null) {
				lastSubtitle = subtitleTextView.getText()
			}

			subtitleTextView.setText(title)

			val color = if (subtitleTextView.getText().contains(context.getString(R.string.Typing))) {
				getTypingColor()
			}
			else {
				ResourcesCompat.getColor(resources, R.color.dark_gray, null)
			}

			subtitleTextView.textColor = overrideSubtitleColor ?: color
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.contentDescription = buildString {
			append(titleTextView.getText())

			if (rightDrawableContentDescription != null) {
				append(", ")
				append(rightDrawableContentDescription)
			}

			append("\n")
			append(subtitleTextView.getText())
		}

		if (info.isClickable) {
			info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.OpenProfile)))
		}
	}

	fun updateColors() {
		currentTypingDrawable?.setColor(getTypingColor())
	}
}
