/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.animation.OvershootInterpolator
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.DownloadController
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.combineDrawables
import org.telegram.messenger.utils.getUser
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.DraftMessage
import org.telegram.tgnet.TLRPC.EncryptedChat
import org.telegram.tgnet.TLRPC.TL_dialog
import org.telegram.tgnet.TLRPC.TL_dialogFolder
import org.telegram.tgnet.TLRPC.TL_documentEmpty
import org.telegram.tgnet.TLRPC.TL_emojiStatus
import org.telegram.tgnet.TLRPC.TL_emojiStatusUntil
import org.telegram.tgnet.TLRPC.TL_encryptedChat
import org.telegram.tgnet.TLRPC.TL_encryptedChatDiscarded
import org.telegram.tgnet.TLRPC.TL_encryptedChatRequested
import org.telegram.tgnet.TLRPC.TL_encryptedChatWaiting
import org.telegram.tgnet.TLRPC.TL_messageActionChannelMigrateFrom
import org.telegram.tgnet.TLRPC.TL_messageActionHistoryClear
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument
import org.telegram.tgnet.TLRPC.TL_messageMediaGame
import org.telegram.tgnet.TLRPC.TL_messageMediaInvoice
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto
import org.telegram.tgnet.TLRPC.TL_messageMediaPoll
import org.telegram.tgnet.TLRPC.TL_messageService
import org.telegram.tgnet.TLRPC.TL_photoEmpty
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Adapters.DialogsAdapter.DialogsPreloader
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedEmojiSpan.EmojiGroupedSpans
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BounceInterpolator
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.CustomDialog
import org.telegram.ui.Components.EmptyStubSpan
import org.telegram.ui.Components.FixedWidthSpan
import org.telegram.ui.Components.ForegroundColorSpanThemable
import org.telegram.ui.Components.MsgClockDrawable
import org.telegram.ui.Components.Premium.PremiumGradient
import org.telegram.ui.Components.PullForegroundDrawable
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.Reactions.VisibleReaction
import org.telegram.ui.Components.StaticLayoutEx
import org.telegram.ui.Components.SwipeGestureSettingsView
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.Components.URLSpanNoUnderline
import org.telegram.ui.Components.URLSpanNoUnderlineBold
import org.telegram.ui.Components.spoilers.SpoilerEffect
import org.telegram.ui.DialogsActivity
import java.util.Locale
import java.util.Stack
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class DialogCell @JvmOverloads constructor(private val parentFragment: DialogsActivity?, context: Context, needCheck: Boolean, forceThreeLines: Boolean, private val currentAccount: Int = UserConfig.selectedAccount) : BaseCell(context) {
	private val avatarDrawable = AvatarDrawable()
	private val avatarImage = ImageReceiver(this)
	private var drawAdult = false
	private var drawPaid = false
	private var drawPrivate = false
	private var adultLeft = 0
	private var paidLeft = 0
	private var privateLeft = 0
	private val emojiStatus: SwapAnimatedEmojiDrawable
	private val interpolator = BounceInterpolator()
	private val pinIconColorFilter = PorterDuffColorFilter(context.getColor(R.color.brand), PorterDuff.Mode.SRC_IN)
	private val rect = RectF()
	private val spoilers: MutableList<SpoilerEffect> = ArrayList()
	private val spoilersPool = Stack<SpoilerEffect>()
	private val thumbImage = ImageReceiver(this)
	private val verifiedDrawable: Drawable?
	private var animateFromStatusDrawableParams = 0
	private var animateToStatusDrawableParams = 0
	private var animatedEmojiStack: EmojiGroupedSpans? = null
	private var animatingArchiveAvatar = false
	private var animatingArchiveAvatarProgress = 0f
	private var archiveBackgroundProgress = 0f
	private var archiveHidden = false
	private var archivedChatsDrawable: PullForegroundDrawable? = null
	private var bottomClip = 0
	private var chat: Chat? = null
	private var chatCallProgress = 0f
	private var checkBox: CheckBox2? = null
	private var checkDrawLeft = 0
	private var checkDrawLeft1 = 0
	private var checkDrawTop = 0
	private var clearingDialog = false
	private var clipProgress = 0f
	private var clockDrawLeft = 0
	private var cornerProgress = 0f
	private var countAnimationInLayout: StaticLayout? = null
	private var countAnimationIncrement = false
	private var countAnimationStableLayout: StaticLayout? = null
	private var countAnimator: ValueAnimator? = null
	private var countChangeProgress = 1f
	private var countLayout: StaticLayout? = null
	private var countLeft = 0
	private var countLeftOld = 0
	private var countOldLayout: StaticLayout? = null
	private var countTop = 0
	private var countWidth = 0
	private var countWidthOld = 0
	private var currentDialogFolderDialogsCount = 0
	private var currentEditDate = 0
	private var currentRevealBounceProgress = 0f
	private var currentRevealProgress = 0f
	private var customDialog: CustomDialog? = null
	private var dialogMutedProgress = 0f
	private var dialogsType = 0
	private var draftMessage: DraftMessage? = null
	private var drawCheck1 = false
	private var drawCheck2 = false
	private var drawClock = false
	private var drawCount = false
	private var drawCount2 = true
	private var drawError = false
	private var drawMention = false
	private var drawNameLock = false
	private var drawPinBackground = false
	private var drawPlay = false
	private var drawPremium = false
	private var drawReactionMention = false
	private var drawReorder = false
	private var drawRevealBackground = false
	private var drawScam = 0
	private var drawVerified = false
	private var encryptedChat: EncryptedChat? = null
	private var errorLeft = 0
	private var errorTop = 0
	private var fadePaint: Paint? = null
	private var fadePaintBack: Paint? = null
	private var folderId = 0
	private var fullSeparator2 = false
	private var halfCheckDrawLeft = 0
	private var hasCall = false
	private var hasMessageThumb = false
	private var innerProgress = 0f
	private var isDialogCell = false
	private var isSelected = false
	private var isSliding = false
	private var lastDialogChangedTime: Long = 0
	private var lastDrawSwipeMessageStringId = 0
	private var lastDrawTranslationDrawable: RLottieDrawable? = null
	private var lastMessageDate = 0
	private var lastMessageString: CharSequence? = null
	private var lastPrintString: CharSequence? = null
	private var lastSendState = 0
	private var lastStatusDrawableParams = -1
	private var lastUnreadState = false
	private var lastUpdateTime: Long = 0
	private var markUnread = false
	private var mentionCount = 0
	private var mentionLayout: StaticLayout? = null
	private var mentionLeft = 0
	private var mentionWidth = 0
	private var messageLayout: StaticLayout? = null
	private var messageLeft = 0
	private var messageNameLayout: StaticLayout? = null
	private var messageNameLeft = 0
	private var messageNameTop = 0
	private var messageTop = 0
	private var nameLayout: StaticLayout? = null
	private var nameLayoutEllipsizeByGradient = false
	private var nameLayoutEllipsizeLeft = false
	private var nameLayoutFits = false
	private var nameLayoutTranslateX = 0f
	private var nameLeft = 0
	private var nameLockLeft = 0
	private var nameLockTop = 0
	private var nameMuteLeft = 0
	private var nameWidth = 0
	private var onlineProgress = 0f
	private var paintIndex = 0
	private var pinLeft = 0
	private var pinTop = 0
	private var preloader: DialogsPreloader? = null
	private var printingStringType = 0
	private var progressStage = 0
	private var promoDialog = false
	private var reactionMentionCount = 0
	private var reactionMentionLeft = 0
	private var reactionsMentionsAnimator: ValueAnimator? = null
	private var reactionsMentionsChangeProgress = 1f
	private var reorderIconProgress = 0f
	private var statusDrawableAnimationInProgress = false
	private var statusDrawableAnimator: ValueAnimator? = null
	private var statusDrawableLeft = 0
	private var statusDrawableProgress = 0f
	private var swipeMessageTextId = 0
	private var swipeMessageTextLayout: StaticLayout? = null
	private var swipeMessageWidth = 0
	private var timeLayout: StaticLayout? = null
	private var timeLeft = 0
	private var timeTop = 0
	private var topClip = 0
	private var translationAnimationStarted = false
	private var translationDrawable: RLottieDrawable? = null
	private var translationX = 0f
	private var unreadCount = 0
	private var useForceThreeLines: Boolean
	private var useMeForMyMessages = false
	private var user: User? = null
	var dialogIndex = 0
	var drawingForBlur = false
	var isMoving = false
	var lastSize = 0
	var swipeCanceled = false
	private var isSearch: Boolean? = false

	@JvmField
	var useSeparator = false

	@JvmField
	var fullSeparator = false

	var dialogId: Long = 0
		private set

	var currentDialogFolderId = 0
		private set

	var isMuted = false
		private set

	var message: MessageObject? = null
		private set(value) {
			field = value
		}

	var messageId = 0
		private set

	var isPinned = false
		private set

	init {
		Theme.createDialogsResources(context)

		avatarImage.setRoundRadius(AndroidUtilities.dp(26f))
		thumbImage.setRoundRadius(AndroidUtilities.dp(8f))

		useForceThreeLines = forceThreeLines
		verifiedDrawable = ResourcesCompat.getDrawable(resources, R.drawable.verified_icon, null)

		if (needCheck) {
			checkBox = CheckBox2(context, 21)
			checkBox?.setColor(0, context.getColor(R.color.background), context.getColor(R.color.white))
			checkBox?.setDrawUnchecked(false)
			checkBox?.setDrawBackgroundAsArc(3)

			addView(checkBox)
		}

		emojiStatus = SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(22f))
		emojiStatus.center = false
	}

	fun setDialog(dialog: TLRPC.Dialog, type: Int, folder: Int) {
		if (dialogId != dialog.id) {
			statusDrawableAnimator?.removeAllListeners()
			statusDrawableAnimator?.cancel()

			statusDrawableAnimationInProgress = false
			lastStatusDrawableParams = -1
		}

		dialogId = dialog.id
		lastDialogChangedTime = System.currentTimeMillis()
		isDialogCell = true

		if (dialog is TL_dialogFolder) {
			currentDialogFolderId = dialog.folder.id
			archivedChatsDrawable?.setCell(this)
		}
		else {
			currentDialogFolderId = 0
		}

		dialogsType = type
		folderId = folder
		messageId = 0

		update(0, false)
		checkOnline()
		checkGroupCall()
	}

	fun setDialog(dialog: CustomDialog?) {
		customDialog = dialog
		messageId = 0
		update(0)
		checkOnline()
		checkGroupCall()
	}

	private fun checkOnline() {
		if (user != null) {
			val newUser = MessagesController.getInstance(currentAccount).getUser(user?.id)

			if (newUser != null) {
				user = newUser
			}
		}

		val isOnline = isOnline

		onlineProgress = if (isOnline) 1.0f else 0.0f
	}

	private val isOnline: Boolean
		get() {
			val user = user ?: return false

			if (user.self) {
				return false
			}

			if ((user.status?.expires ?: Int.MAX_VALUE) <= 0) {
				if (MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(user.id)) {
					return true
				}
			}

			return (user.status?.expires ?: 0) > ConnectionsManager.getInstance(currentAccount).currentTime
		}

	private fun checkGroupCall() {
		hasCall = chat?.call_active == true && chat?.call_not_empty == true
		chatCallProgress = if (hasCall) 1.0f else 0.0f
	}

	fun setDialog(dialogId: Long, messageObject: MessageObject?, date: Int, useMe: Boolean) {
		if (this.dialogId != dialogId) {
			lastStatusDrawableParams = -1
		}

		this.dialogId = dialogId
		lastDialogChangedTime = System.currentTimeMillis()

		message = messageObject ?: MessagesStorage.getInstance(currentAccount).getLastMessage(dialogId)?.let {
			MessageObject(currentAccount, it, generateLayout = false, checkMediaExists = true)
		}

		useMeForMyMessages = useMe
		isDialogCell = false
		lastMessageDate = date
		currentEditDate = messageObject?.messageOwner?.edit_date ?: 0
		unreadCount = 0
		markUnread = false
		messageId = messageObject?.id ?: 0
		mentionCount = 0
		reactionMentionCount = 0
		lastUnreadState = messageObject != null && messageObject.isUnread

		if (message != null) {
			lastSendState = message?.messageOwner?.send_state ?: 0
		}

		update(0)
	}

	fun setDialog(dialogId: Long, messageObject: MessageObject?, date: Int, useMe: Boolean, isSearch: Boolean?) {
		if (this.dialogId != dialogId) {
			lastStatusDrawableParams = -1
		}

		this.dialogId = dialogId
		lastDialogChangedTime = System.currentTimeMillis()

		message = messageObject ?: MessagesStorage.getInstance(currentAccount).getLastMessage(dialogId)?.let {
			MessageObject(currentAccount, it, generateLayout = false, checkMediaExists = true)
		}

		useMeForMyMessages = useMe
		isDialogCell = false
		lastMessageDate = date
		currentEditDate = messageObject?.messageOwner?.edit_date ?: 0
		unreadCount = 0
		markUnread = false
		messageId = messageObject?.id ?: 0
		mentionCount = 0
		reactionMentionCount = 0
		lastUnreadState = messageObject != null && messageObject.isUnread

		if (message != null) {
			lastSendState = message?.messageOwner?.send_state ?: 0
		}

		this.isSearch = isSearch
		update(0)
	}

	fun setPreloader(preloader: DialogsPreloader?) {
		this.preloader = preloader
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		isSliding = false
		drawRevealBackground = false
		currentRevealProgress = 0.0f
		reorderIconProgress = if (isPinned && drawReorder) 1.0f else 0.0f

		avatarImage.onDetachedFromWindow()
		thumbImage.onDetachedFromWindow()

		if (translationDrawable != null) {
			translationDrawable?.stop()
			translationDrawable?.setProgress(0.0f)
			translationDrawable?.callback = null
			translationDrawable = null
			translationAnimationStarted = false
		}

		preloader?.remove(dialogId)

		emojiStatus.detach()

		AnimatedEmojiSpan.release(animatedEmojiStack)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		avatarImage.onAttachedToWindow()
		thumbImage.onAttachedToWindow()
		resetPinnedArchiveState()
		animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack, messageLayout)
	}

	fun resetPinnedArchiveState() {
		archiveHidden = SharedConfig.archiveHidden
		archiveBackgroundProgress = if (archiveHidden) 0.0f else 1.0f
		avatarDrawable.setArchivedAvatarHiddenProgress(archiveBackgroundProgress)
		clipProgress = 0.0f
		isSliding = false
		reorderIconProgress = if (isPinned && drawReorder) 1.0f else 0.0f
		cornerProgress = 0.0f
		setTranslationX(0f)
		translationY = 0f
		emojiStatus.attach()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		checkBox?.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY))
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), computeHeight())
		topClip = 0
		bottomClip = measuredHeight
	}

	private fun computeHeight(): Int {
		val heightThreeLines = 78f
		return AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) heightThreeLines else DEFAULT_HEIGHT) + if (useSeparator) 1 else 0
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		if (dialogId == 0L && customDialog == null) {
			return
		}

		checkBox?.let {
			val xDiff = AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 49 else 51).toFloat())
			val x = if (LocaleController.isRTL) right - left - xDiff else xDiff
			val y = AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 52 else 46).toFloat())
			it.layout(x, y, x + it.measuredWidth, y + it.measuredHeight)
		}

		val size = measuredHeight + measuredWidth shl 16

		if (size != lastSize) {
			lastSize = size

			try {
				buildLayout()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	val isUnread: Boolean
		get() = (unreadCount != 0 || markUnread) && !isMuted

	val hasUnread: Boolean
		get() = unreadCount != 0 || markUnread

	private fun formatArchivedDialogNames(): CharSequence {
		val dialogs = MessagesController.getInstance(currentAccount).getDialogs(currentDialogFolderId)

		currentDialogFolderDialogsCount = dialogs.size

		val builder = SpannableStringBuilder()
		var a = 0
		val n = dialogs.size

		while (a < n) {
			val dialog = dialogs[a]
			var currentUser: User? = null
			var currentChat: Chat? = null

			if (DialogObject.isEncryptedDialog(dialog.id)) {
				val encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialog.id))

				if (encryptedChat != null) {
					currentUser = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id)
				}
			}
			else {
				if (DialogObject.isUserDialog(dialog.id)) {
					currentUser = MessagesController.getInstance(currentAccount).getUser(dialog.id)
				}
				else {
					currentChat = MessagesController.getInstance(currentAccount).getChat(-dialog.id)
				}
			}

			val title = currentChat?.title?.replace('\n', ' ') ?: if (currentUser != null) {
				if (UserObject.isDeleted(currentUser)) {
					context.getString(R.string.HiddenName)
				}
				else {
					ContactsController.formatName(currentUser.first_name, currentUser.last_name).replace('\n', ' ')
				}
			}
			else {
				a++
				continue
			}

			if (builder.isNotEmpty()) {
				builder.append(", ")
			}

			val boldStart = builder.length
			val boldEnd = boldStart + title.length

			builder.append(title)

			if (dialog.unread_count > 0) {
				builder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD, 0, context.getColor(R.color.dark_gray)), boldStart, boldEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			if (builder.length > 150) {
				break
			}

			a++
		}

		return Emoji.replaceEmoji(builder, Theme.dialogs_messagePaint[paintIndex].fontMetricsInt, false) ?: builder
	}

	private fun buildLayout() {
		val thumbSize: Int

		if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
			Theme.dialogs_namePaint[1].textSize = AndroidUtilities.dp(15f).toFloat()
			Theme.dialogs_nameEncryptedPaint[1].textSize = AndroidUtilities.dp(15f).toFloat()
			Theme.dialogs_messagePaint[1].textSize = AndroidUtilities.dp(13f).toFloat()
			Theme.dialogs_messagePrintingPaint[1].textSize = AndroidUtilities.dp(13f).toFloat()
			Theme.dialogs_messagePaint[1].color = context.getColor(R.color.dark_gray).also { Theme.dialogs_messagePaint[1].linkColor = it }
			paintIndex = 1
			thumbSize = 18
		}
		else {
			Theme.dialogs_namePaint[0].textSize = AndroidUtilities.dp(15f).toFloat()
			Theme.dialogs_nameEncryptedPaint[0].textSize = AndroidUtilities.dp(15f).toFloat()
			Theme.dialogs_messagePaint[0].textSize = AndroidUtilities.dp(14f).toFloat()
			Theme.dialogs_messagePrintingPaint[0].textSize = AndroidUtilities.dp(14f).toFloat()
			Theme.dialogs_messagePaint[0].color = context.getColor(R.color.dark_gray).also { Theme.dialogs_messagePaint[0].linkColor = it }
			paintIndex = 0
			thumbSize = 19
		}

		currentDialogFolderDialogsCount = 0

		var nameString: String? = ""
		var timeString: String? = ""
		var countString: String? = null
		var mentionString: String? = null
		var messageString: CharSequence? = ""
		var messageNameString: CharSequence? = null
		var printingString: CharSequence? = null

		if (isDialogCell) {
			printingString = MessagesController.getInstance(currentAccount).getPrintingString(dialogId, 0, true)
		}

		var currentMessagePaint = Theme.dialogs_messagePaint[paintIndex]
		var checkMessage = true

		drawNameLock = false
		drawVerified = false
		drawPremium = false
		drawScam = 0
		drawPinBackground = false
		hasMessageThumb = false
		nameLayoutEllipsizeByGradient = false

		var offsetName = 0
		var showChecks = !UserObject.isUserSelf(user) && !useMeForMyMessages
		var drawTime = true

		printingStringType = -1

		var printingStringReplaceIndex = -1
		val messageFormat: String
		val hasNameInMessage: Boolean

		if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || currentDialogFolderId != 0) {
			messageFormat = "%2\$s: \u2068%1\$s\u2069"
			hasNameInMessage = true
		}
		else {
			messageFormat = "\u2068%s\u2069"
			hasNameInMessage = false
		}

		var msgText = message?.messageText

		if (msgText is Spannable) {
			val sp: Spannable = SpannableStringBuilder(msgText)

			for (span in sp.getSpans(0, sp.length, URLSpanNoUnderlineBold::class.java)) {
				sp.removeSpan(span)
			}
			for (span in sp.getSpans(0, sp.length, URLSpanNoUnderline::class.java)) {
				sp.removeSpan(span)
			}

			msgText = sp
		}

		lastMessageString = msgText

		if (customDialog != null) {
			if (customDialog?.type == 2) {
				drawNameLock = true

				if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
					nameLockTop = AndroidUtilities.dp(4.5f)

					nameLockLeft = if (!LocaleController.isRTL) {
						AndroidUtilities.dp((72 + 6).toFloat())
					}
					else {
						measuredWidth - AndroidUtilities.dp((72 + 6).toFloat()) - Theme.dialogs_lockDrawable.intrinsicWidth
					}
				}
				else {
					nameLockTop = AndroidUtilities.dp(8.5f)

					nameLockLeft = if (!LocaleController.isRTL) {
						AndroidUtilities.dp((72 + 4).toFloat())
					}
					else {
						measuredWidth - AndroidUtilities.dp((72 + 4).toFloat()) - Theme.dialogs_lockDrawable.intrinsicWidth
					}
				}
			}
			else {
				drawVerified = customDialog?.verified ?: false
			}

			if (customDialog?.type == 1) {
				messageNameString = context.getString(R.string.FromYou)
				checkMessage = false

				val stringBuilder: SpannableStringBuilder

				if (customDialog?.isMedia == true) {
					currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]
					stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, message!!.messageText))
					stringBuilder.setSpan(ForegroundColorSpanThemable(context.getColor(R.color.brand)), 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else {
					var mess = customDialog?.message ?: ""

					if (mess.length > 150) {
						mess = mess.substring(0, 150)
					}

					stringBuilder = if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
						SpannableStringBuilder.valueOf(String.format(messageFormat, mess, messageNameString))
					}
					else {
						SpannableStringBuilder.valueOf(String.format(messageFormat, mess.replace('\n', ' '), messageNameString))
					}
				}

				messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].fontMetricsInt, false)
			}
			else {
				messageString = customDialog?.message

				if (customDialog?.isMedia == true) {
					currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]
				}
			}

			timeString = LocaleController.stringForMessageListDate(customDialog!!.date.toLong())

			if (customDialog?.unread_count != 0) {
				drawCount = true
				countString = String.format(Locale.getDefault(), "%d", customDialog!!.unread_count)
			}
			else {
				drawCount = false
			}

			if (customDialog?.sent == true) {
				drawCheck1 = true
				drawCheck2 = true
			}
			else {
				drawCheck1 = false
				drawCheck2 = false
			}

			drawClock = false
			drawError = false
			nameString = customDialog?.name
		}
		else {
			if (encryptedChat != null) {
				if (currentDialogFolderId == 0) {
					drawNameLock = true

					if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
						nameLockTop = AndroidUtilities.dp(4.5f)

						nameLockLeft = if (!LocaleController.isRTL) {
							AndroidUtilities.dp((72 + 6).toFloat())
						}
						else {
							measuredWidth - AndroidUtilities.dp((72 + 6).toFloat()) - Theme.dialogs_lockDrawable.intrinsicWidth
						}
					}
					else {
						nameLockTop = AndroidUtilities.dp(8.5f)

						nameLockLeft = if (!LocaleController.isRTL) {
							AndroidUtilities.dp((72 + 4).toFloat())
						}
						else {
							measuredWidth - AndroidUtilities.dp((72 + 4).toFloat()) - Theme.dialogs_lockDrawable.intrinsicWidth
						}
					}
				}
			}
			else {
				if (currentDialogFolderId == 0) {
					if (chat != null) {
						if (chat?.scam == true) {
							drawScam = 1
							Theme.dialogs_scamDrawable.checkText()
						}
						else if (chat?.fake == true) {
							drawScam = 2
							Theme.dialogs_fakeDrawable.checkText()
						}
						else {
							drawVerified = chat!!.verified
						}
					}
					else if (user != null) {
						if (user?.scam == true) {
							drawScam = 1
							Theme.dialogs_scamDrawable.checkText()
						}
						else if (user?.fake == true) {
							drawScam = 2
							Theme.dialogs_fakeDrawable.checkText()
						}
						else {
							drawVerified = user?.verified ?: false
						}

						drawPremium = MessagesController.getInstance(currentAccount).isPremiumUser(user) && UserConfig.getInstance(currentAccount).clientUserId != user!!.id && user!!.id != 0L

						if (drawPremium) {
							if (user!!.emoji_status is TL_emojiStatusUntil && (user!!.emoji_status as TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
								nameLayoutEllipsizeByGradient = true
								emojiStatus[(user!!.emoji_status as TL_emojiStatusUntil).document_id] = false
							}
							else if (user!!.emoji_status is TL_emojiStatus) {
								nameLayoutEllipsizeByGradient = true
								emojiStatus[(user!!.emoji_status as TL_emojiStatus).document_id] = false
							}
							else {
								nameLayoutEllipsizeByGradient = true
								emojiStatus[PremiumGradient.getInstance().premiumStarDrawableMini] = false
							}
						}
					}
				}
			}

			var lastDate = lastMessageDate

			if (lastMessageDate == 0 && message != null) {
				lastDate = message?.messageOwner?.date ?: 0
			}

			if (isDialogCell) {
				draftMessage = MediaDataController.getInstance(currentAccount).getDraft(dialogId, 0)

				if (draftMessage != null && (TextUtils.isEmpty(draftMessage!!.message) && draftMessage!!.reply_to_msg_id == 0 || lastDate > draftMessage!!.date && unreadCount != 0) || ChatObject.isChannel(chat) && !chat!!.megagroup && !chat!!.creator && (chat!!.admin_rights == null || !chat!!.admin_rights.post_messages) || chat != null && (chat!!.left || chat!!.kicked)) {
					draftMessage = null
				}
			}
			else {
				draftMessage = null
			}

			if (printingString != null) {
				lastPrintString = printingString
				printingStringType = MessagesController.getInstance(currentAccount).getPrintingStringType(dialogId, 0) ?: 0

				val statusDrawable = Theme.getChatStatusDrawable(printingStringType)
				var startPadding = 0

				if (statusDrawable != null) {
					startPadding = statusDrawable.intrinsicWidth + AndroidUtilities.dp(3f)
				}

				val spannableStringBuilder = SpannableStringBuilder()

				printingString = TextUtils.replace(printingString, arrayOf("..."), arrayOf(""))

				if (printingStringType == 5) {
					printingStringReplaceIndex = printingString.toString().indexOf("**oo**")
				}

				if (printingStringReplaceIndex >= 0) {
					spannableStringBuilder.append(printingString).setSpan(FixedWidthSpan(Theme.getChatStatusDrawable(printingStringType).intrinsicWidth), printingStringReplaceIndex, printingStringReplaceIndex + 6, 0)
				}
				else {
					spannableStringBuilder.append(" ").append(printingString).setSpan(FixedWidthSpan(startPadding), 0, 1, 0)
				}

				messageString = spannableStringBuilder
				currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]
				checkMessage = false
			}
			else {
				lastPrintString = null

				if (draftMessage != null) {
					checkMessage = false
					messageNameString = context.getString(R.string.Draft)

					if (draftMessage?.message.isNullOrEmpty()) {
						messageString = if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
							""
						}
						else {
							val stringBuilder = SpannableStringBuilder.valueOf(messageNameString)
							stringBuilder.setSpan(ForegroundColorSpanThemable(context.getColor(R.color.purple)), 0, messageNameString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
							stringBuilder
						}
					}
					else {
						var mess = draftMessage!!.message

						if (mess.length > 150) {
							mess = mess.substring(0, 150)
						}

						val messSpan: Spannable = SpannableStringBuilder(mess)
						MediaDataController.addTextStyleRuns(draftMessage, messSpan, TextStyleSpan.FLAG_STYLE_SPOILER)

						if (draftMessage?.entities != null) {
							MediaDataController.addAnimatedEmojiSpans(draftMessage?.entities, messSpan, currentMessagePaint?.fontMetricsInt)
						}

						val stringBuilder = AndroidUtilities.formatSpannable(messageFormat, AndroidUtilities.replaceNewLines(messSpan), messageNameString)

						if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout) {
							stringBuilder.setSpan(ForegroundColorSpanThemable(context.getColor(R.color.purple)), 0, messageNameString.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						}

						messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].fontMetricsInt, false)
					}
				}
				else {
					if (clearingDialog) {
						currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]
						messageString = context.getString(R.string.HistoryCleared)
					}
					else if (message == null) {
						if (encryptedChat != null) {
							currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]

							when (encryptedChat) {
								is TL_encryptedChatRequested -> {
									messageString = context.getString(R.string.EncryptionProcessing)
								}

								is TL_encryptedChatWaiting -> {
									messageString = LocaleController.formatString("AwaitingEncryption", R.string.AwaitingEncryption, UserObject.getFirstName(user))
								}

								is TL_encryptedChatDiscarded -> {
									messageString = context.getString(R.string.EncryptionRejected)
								}

								is TL_encryptedChat -> {
									messageString = if (encryptedChat?.admin_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
										LocaleController.formatString("EncryptedChatStartedOutgoing", R.string.EncryptedChatStartedOutgoing, UserObject.getFirstName(user))
									}
									else {
										context.getString(R.string.EncryptedChatStartedIncoming)
									}
								}
							}
						}
						else {
							if (dialogsType == 3 && UserObject.isUserSelf(user)) {
								messageString = context.getString(R.string.SavedMessagesInfo)
								showChecks = false
								drawTime = false
							}
							else {
								messageString = ""
							}
						}
					}
					else {
						val restrictionReason = MessagesController.getRestrictionReason(message?.messageOwner?.restriction_reason)
						var fromUser: User? = null
						var fromChat: Chat? = null
						val fromId = message!!.fromChatId

						if (DialogObject.isUserDialog(fromId)) {
							fromUser = MessagesController.getInstance(currentAccount).getUser(fromId)
						}
						else {
							fromChat = MessagesController.getInstance(currentAccount).getChat(-fromId)
						}

						drawCount2 = true

						var lastMessageIsReaction = false

						if (dialogId > 0 && message!!.isOutOwner && !message?.messageOwner?.reactions?.recentReactions.isNullOrEmpty()) {
							val lastReaction = message?.messageOwner?.reactions?.recentReactions?.firstOrNull()

							if (lastReaction != null && lastReaction.peer_id.user_id != 0L && lastReaction.peer_id.user_id != UserConfig.getInstance(currentAccount).clientUserId) {
								lastMessageIsReaction = true

								val visibleReaction = VisibleReaction.fromTLReaction(lastReaction.reaction)

								currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]

								if (visibleReaction.emojicon != null) {
									messageString = LocaleController.formatString("ReactionInDialog", R.string.ReactionInDialog, visibleReaction.emojicon)
								}
								else {
									var string = LocaleController.formatString("ReactionInDialog", R.string.ReactionInDialog, "**reaction**")
									val i = string.indexOf("**reaction**")
									string = string.replace("**reaction**", "d")
									val spannableStringBuilder = SpannableStringBuilder(string)
									spannableStringBuilder.setSpan(AnimatedEmojiSpan(visibleReaction.documentId, currentMessagePaint?.fontMetricsInt), i, i + 1, 0)
									messageString = spannableStringBuilder
								}
							}
						}

						if (lastMessageIsReaction) {
							// unused
						}
						else if (dialogsType == 2) {
							messageString = if (chat != null) {
								if (ChatObject.isChannel(chat) && !chat!!.megagroup) {
									if (chat!!.participants_count != 0) {
										LocaleController.formatPluralStringComma("Subscribers", chat!!.participants_count)
									}
									else {
										if (chat?.username.isNullOrEmpty()) {
											context.getString(R.string.ChannelPrivate).lowercase()
										}
										else {
											context.getString(R.string.ChannelPublic).lowercase()
										}
									}
								}
								else {
									if (chat!!.participants_count != 0) {
										LocaleController.formatPluralStringComma("Members", chat!!.participants_count)
									}
									else {
										if (chat!!.has_geo) {
											context.getString(R.string.MegaLocation)
										}
										else if (chat?.username.isNullOrEmpty()) {
											context.getString(R.string.MegaPrivate).lowercase()
										}
										else {
											context.getString(R.string.MegaPublic).lowercase()
										}
									}
								}
							}
							else {
								""
							}

							drawCount2 = false
							showChecks = false
							drawTime = false
						}
						else if (dialogsType == 3 && UserObject.isUserSelf(user)) {
							messageString = context.getString(R.string.SavedMessagesInfo)
							showChecks = false
							drawTime = false
						}
						else if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout && currentDialogFolderId != 0) {
							checkMessage = false
							messageString = formatArchivedDialogNames()
						}
						else if (message!!.messageOwner is TL_messageService) {
							// MARK: uncomment to show service messages in dialogs list
//							if (ChatObject.isChannelAndNotMegaGroup(chat) && message?.messageOwner?.action is TL_messageActionChannelMigrateFrom) {
//								messageString = ""
//								showChecks = false
//							}
//							else {
//								messageString = msgText
//							}

							// MARK: remove following two lines to show service messages in dialogs list
							messageString = ""
							showChecks = false

							// MARK: change service messages color here
							currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]
						}
						else {
							var needEmoji = true

							if (restrictionReason.isNullOrEmpty() && currentDialogFolderId == 0 && encryptedChat == null && !message!!.needDrawBluredPreview() && (message!!.isPhoto || message!!.isNewGif || message!!.isVideo)) {
								val type = if (message?.isWebpage == true) message?.messageOwner?.media?.webpage?.type else null

								if (!("app" == type || "profile" == type || "article" == type || type != null && type.startsWith("telegram_"))) {
									val smallThumb = FileLoader.getClosestPhotoSizeWithSize(message!!.photoThumbs, 40)
									var bigThumb = FileLoader.getClosestPhotoSizeWithSize(message!!.photoThumbs, AndroidUtilities.getPhotoSize())

									if (smallThumb === bigThumb) {
										bigThumb = null
									}

									if (smallThumb != null) {
										hasMessageThumb = true
										drawPlay = message?.isVideo ?: false

										val fileName = FileLoader.getAttachFileName(bigThumb)

										if (message!!.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(message) || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
											val size = if (message!!.type == MessageObject.TYPE_PHOTO) {
												bigThumb?.size ?: 0
											}
											else {
												0
											}

											thumbImage.setImage(ImageLocation.getForObject(bigThumb, message!!.photoThumbsObject), "20_20", ImageLocation.getForObject(smallThumb, message!!.photoThumbsObject), "20_20", size.toLong(), null, message, 0)
										}
										else {
											thumbImage.setImage(null, null, ImageLocation.getForObject(smallThumb, message!!.photoThumbsObject), "20_20", null as Drawable?, message, 0)
										}

										needEmoji = false
									}
								}
							}
							if (chat != null && chat!!.id > 0 && fromChat == null && (!ChatObject.isChannel(chat) || ChatObject.isMegagroup(chat))) {
								messageNameString = if (message!!.isOutOwner) {
									context.getString(R.string.FromYou)
								}
								else if (message?.messageOwner?.fwd_from?.from_name != null) {
									message?.messageOwner?.fwd_from?.from_name
								}
								else if (fromUser != null) {
									if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
										if (UserObject.isDeleted(fromUser)) {
											context.getString(R.string.HiddenName)
										}
										else {
											ContactsController.formatName(fromUser.first_name, fromUser.last_name).replace("\n", "")
										}
									}
									else {
										UserObject.getFirstName(fromUser).replace("\n", "")
									}
								}
								else {
									"DELETED"
								}

								checkMessage = false

								val stringBuilder: SpannableStringBuilder

								if (!restrictionReason.isNullOrEmpty()) {
									stringBuilder = SpannableStringBuilder.valueOf(String.format(messageFormat, restrictionReason, messageNameString))
								}
								else if (message?.caption != null) {
									var mess: CharSequence = message!!.caption.toString()

									val emoji = if (!needEmoji) {
										""
									}
									else if (message!!.isVideo) {
										"\uD83D\uDCF9 "
									}
									else if (message!!.isVoice) {
										"\uD83C\uDFA4 "
									}
									else if (message!!.isMusic) {
										"\uD83C\uDFA7 "
									}
									else if (message!!.isPhoto) {
										"\uD83D\uDDBC "
									}
									else {
										"\uD83D\uDCCE "
									}

									if (message!!.hasHighlightedWords() && !message?.messageOwner?.message.isNullOrEmpty()) {
										var str = message!!.messageTrimmedToHighlight

										if (message!!.messageTrimmedToHighlight != null) {
											str = message!!.messageTrimmedToHighlight
										}

										var w = measuredWidth - AndroidUtilities.dp((72 + 23 + 24).toFloat())

										if (hasNameInMessage) {
											if (!TextUtils.isEmpty(messageNameString)) {
												w -= currentMessagePaint!!.measureText(messageNameString.toString()).toInt()
											}

											w -= currentMessagePaint!!.measureText(": ").toInt()
										}

										if (w > 0) {
											str = AndroidUtilities.ellipsizeCenterEnd(str, message!!.highlightedWords!![0], w, currentMessagePaint, 130).toString()
										}

										stringBuilder = SpannableStringBuilder(emoji).append(str)
									}
									else {
										if (mess.length > 150) {
											mess = mess.subSequence(0, 150)
										}

										val msgBuilder = SpannableStringBuilder(mess)

										MediaDataController.addTextStyleRuns(message!!.messageOwner?.entities, mess, msgBuilder, TextStyleSpan.FLAG_STYLE_SPOILER)

										if (message != null && message!!.messageOwner != null) {
											MediaDataController.addAnimatedEmojiSpans(message!!.messageOwner?.entities, msgBuilder, currentMessagePaint?.fontMetricsInt)
										}

										stringBuilder = AndroidUtilities.formatSpannable(messageFormat, SpannableStringBuilder(emoji).append(AndroidUtilities.replaceNewLines(msgBuilder)), messageNameString)
									}
								}
								else if (message!!.messageOwner?.media != null && !message!!.isMediaEmpty) {
									currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]

									var innerMessage = if (message!!.messageOwner?.media is TL_messageMediaPoll) {
										val mediaPoll = message!!.messageOwner?.media as TL_messageMediaPoll
										String.format("\uD83D\uDCCA \u2068%s\u2069", mediaPoll.poll.question)
									}
									else if (message!!.messageOwner?.media is TL_messageMediaGame) {
										String.format("\uD83C\uDFAE \u2068%s\u2069", message?.messageOwner?.media?.game?.title)
									}
									else if (message!!.messageOwner?.media is TL_messageMediaInvoice) {
										message?.messageOwner?.media?.title
									}
									else if (message!!.type == MessageObject.TYPE_MUSIC) {
										String.format("\uD83C\uDFA7 \u2068%s - %s\u2069", message!!.musicAuthor, message!!.musicTitle)
									}
									else {
										msgText.toString()
									}

									innerMessage = innerMessage?.replace('\n', ' ')
									stringBuilder = AndroidUtilities.formatSpannable(messageFormat, innerMessage, messageNameString)

									try {
										stringBuilder.setSpan(ForegroundColorSpanThemable(context.getColor(R.color.brand)), if (hasNameInMessage) messageNameString!!.length + 2 else 0, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
									}
									catch (e: Exception) {
										FileLog.e(e)
									}
								}
								else if (message?.messageOwner?.message != null) {
									var mess: CharSequence = message?.messageOwner?.message ?: ""

									if (message!!.hasHighlightedWords()) {
										if (message!!.messageTrimmedToHighlight != null) {
											mess = message!!.messageTrimmedToHighlight!!
										}

										var w = measuredWidth - AndroidUtilities.dp((72 + 23 + 10).toFloat())

										if (hasNameInMessage) {
											if (!TextUtils.isEmpty(messageNameString)) {
												w -= currentMessagePaint!!.measureText(messageNameString.toString()).toInt()
											}

											w -= currentMessagePaint!!.measureText(": ").toInt()
										}

										if (w > 0) {
											mess = AndroidUtilities.ellipsizeCenterEnd(mess, message!!.highlightedWords!![0], w, currentMessagePaint, 130).toString()
										}
									}
									else {
										if (mess.length > 150) {
											mess = mess.subSequence(0, 150)
										}

										mess = AndroidUtilities.replaceNewLines(mess)
									}

									mess = SpannableStringBuilder(mess)

									MediaDataController.addTextStyleRuns(message, mess as Spannable, TextStyleSpan.FLAG_STYLE_SPOILER)

									if (message != null && message!!.messageOwner != null) {
										MediaDataController.addAnimatedEmojiSpans(message?.messageOwner?.entities, mess, currentMessagePaint?.fontMetricsInt)
									}

									stringBuilder = AndroidUtilities.formatSpannable(messageFormat, mess, messageNameString)
								}
								else {
									stringBuilder = SpannableStringBuilder.valueOf("")
								}

								// MARK: here is the point where thumb is being offset with name
								var thumbInsertIndex = 0

								if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || currentDialogFolderId != 0 && stringBuilder.isNotEmpty()) {
									try {
										stringBuilder.setSpan(ForegroundColorSpan(context.getColor(R.color.brand)), 0, (messageNameString!!.length + 1).also {
											thumbInsertIndex = it
										}, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

										offsetName = thumbInsertIndex
									}
									catch (e: Exception) {
										FileLog.e(e)
									}
								}

								messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].fontMetricsInt, false)

								if (message!!.hasHighlightedWords()) {
									val messageH = AndroidUtilities.highlightText(messageString, message!!.highlightedWords)

									if (messageH != null) {
										messageString = messageH
									}
								}

								if (hasMessageThumb) {
									if (messageString !is SpannableStringBuilder) {
										messageString = SpannableStringBuilder(messageString)
									}

									checkMessage = false

									val builder = messageString

									if (thumbInsertIndex >= builder.length) {
										builder.append(" ")
										builder.setSpan(FixedWidthSpan(AndroidUtilities.dp((thumbSize + 6).toFloat())), builder.length - 1, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
									}
									else {
										builder.insert(thumbInsertIndex, " ")
										builder.setSpan(FixedWidthSpan(AndroidUtilities.dp((thumbSize + 6).toFloat())), thumbInsertIndex, thumbInsertIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
									}
								}
							}
							else {
								if (!restrictionReason.isNullOrEmpty()) {
									messageString = restrictionReason
								}
								else if (message!!.messageOwner?.media is TL_messageMediaPhoto && message!!.messageOwner?.media?.photo is TL_photoEmpty && message!!.messageOwner!!.media?.ttl_seconds != 0) {
									messageString = context.getString(R.string.AttachPhotoExpired)
								}
								else if (message!!.messageOwner?.media is TL_messageMediaDocument && message!!.messageOwner?.media?.document is TL_documentEmpty && message!!.messageOwner?.media?.ttl_seconds != 0) {
									messageString = context.getString(R.string.AttachVideoExpired)
								}
								else if (message!!.caption != null) {
									val emoji = if (!needEmoji) {
										""
									}
									else if (message!!.isVideo) {
										"\uD83D\uDCF9 "
									}
									else if (message!!.isVoice) {
										"\uD83C\uDFA4 "
									}
									else if (message!!.isMusic) {
										"\uD83C\uDFA7 "
									}
									else if (message!!.isPhoto) {
										"\uD83D\uDDBC "
									}
									else {
										"\uD83D\uDCCE "
									}

									if (message!!.hasHighlightedWords() && !message?.messageOwner?.message.isNullOrEmpty()) {
										var str = message?.messageTrimmedToHighlight
										var w = measuredWidth - AndroidUtilities.dp((72 + 23 + 24).toFloat())

										if (hasNameInMessage) {
											if (!TextUtils.isEmpty(messageNameString)) {
												w -= currentMessagePaint!!.measureText(messageNameString.toString()).toInt()
											}

											w -= currentMessagePaint!!.measureText(": ").toInt()
										}

										if (w > 0) {
											str = AndroidUtilities.ellipsizeCenterEnd(str, message!!.highlightedWords!![0], w, currentMessagePaint, 130).toString()
										}

										messageString = emoji + str
									}
									else {
										val msgBuilder = SpannableStringBuilder(message!!.caption)

										if (message != null && message!!.messageOwner != null) {
											MediaDataController.addTextStyleRuns(message!!.messageOwner?.entities, message!!.caption, msgBuilder, TextStyleSpan.FLAG_STYLE_SPOILER)
											MediaDataController.addAnimatedEmojiSpans(message!!.messageOwner?.entities, msgBuilder, currentMessagePaint?.fontMetricsInt)
										}

										messageString = SpannableStringBuilder(emoji).append(msgBuilder)
									}
								}
								else {
									if (message!!.messageOwner?.media is TL_messageMediaPoll) {
										val mediaPoll = message!!.messageOwner?.media as TL_messageMediaPoll
										messageString = "\uD83D\uDCCA " + mediaPoll.poll.question
									}
									else if (message!!.messageOwner?.media is TL_messageMediaGame) {
										messageString = "\uD83C\uDFAE " + message!!.messageOwner?.media?.game?.title
									}
									else if (message!!.messageOwner?.media is TL_messageMediaInvoice) {
										messageString = message!!.messageOwner?.media?.title
									}
									else if (message!!.type == MessageObject.TYPE_MUSIC) {
										messageString = String.format("\uD83C\uDFA7 %s - %s", message!!.musicAuthor, message!!.musicTitle)
									}
									else {
										if (message!!.hasHighlightedWords() && !message?.messageOwner?.message.isNullOrEmpty()) {
											messageString = message!!.messageTrimmedToHighlight

											val w = measuredWidth - AndroidUtilities.dp((72 + 23).toFloat())

											messageString = AndroidUtilities.ellipsizeCenterEnd(messageString, message!!.highlightedWords!![0], w, currentMessagePaint, 130).toString()
										}
										else {
											val stringBuilder = SpannableStringBuilder(msgText)

											MediaDataController.addTextStyleRuns(message, stringBuilder, TextStyleSpan.FLAG_STYLE_SPOILER)

											if (message != null && message!!.messageOwner != null) {
												MediaDataController.addAnimatedEmojiSpans(message!!.messageOwner?.entities, stringBuilder, currentMessagePaint?.fontMetricsInt)
											}

											messageString = stringBuilder
										}

										AndroidUtilities.highlightText(messageString, message!!.highlightedWords)
									}

									if (message!!.messageOwner?.media != null && !message!!.isMediaEmpty) {
										currentMessagePaint = Theme.dialogs_messagePrintingPaint[paintIndex]
									}
								}

								if (hasMessageThumb) {
									if (message!!.hasHighlightedWords() && !message?.messageOwner?.message.isNullOrEmpty()) {
										messageString = message!!.messageTrimmedToHighlight

										if (message!!.messageTrimmedToHighlight != null) {
											messageString = message!!.messageTrimmedToHighlight
										}

										val w = measuredWidth - AndroidUtilities.dp((72 + 23 + thumbSize + 6).toFloat())

										messageString = AndroidUtilities.ellipsizeCenterEnd(messageString, message!!.highlightedWords!![0], w, currentMessagePaint, 130).toString()
									}
									else {
										if (messageString!!.length > 150) {
											messageString = messageString.subSequence(0, 150)
										}

										messageString = AndroidUtilities.replaceNewLines(messageString)
									}

									if (messageString !is SpannableStringBuilder) {
										messageString = SpannableStringBuilder(messageString)
									}

									checkMessage = false

									val builder = messageString

									builder.insert(0, " ")
									builder.setSpan(FixedWidthSpan(AndroidUtilities.dp((thumbSize + 6).toFloat())), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

									Emoji.replaceEmoji(builder, Theme.dialogs_messagePaint[paintIndex].fontMetricsInt, false)

									if (message!!.hasHighlightedWords()) {
										val s = AndroidUtilities.highlightText(builder, message!!.highlightedWords)

										if (s != null) {
											messageString = s
										}
									}
								}
							}
						}

						if (currentDialogFolderId != 0) {
							messageNameString = formatArchivedDialogNames()
						}
					}
				}
			}

			if (draftMessage != null) {
				timeString = LocaleController.stringForMessageListDate(draftMessage!!.date.toLong())
			}
			else if (lastMessageDate != 0) {
				timeString = LocaleController.stringForMessageListDate(lastMessageDate.toLong())
			}
			else if (message != null) {
				timeString = LocaleController.stringForMessageListDate(message!!.messageOwner!!.date.toLong())
			}

			if (message == null) {
				drawCheck1 = false
				drawCheck2 = false
				drawClock = false
				drawCount = false
				drawMention = false
				drawReactionMention = false
				drawError = false
			}
			else {
				if (currentDialogFolderId != 0) {
					if (unreadCount + mentionCount > 0) {
						if (unreadCount > mentionCount) {
							drawCount = true
							drawMention = false
							countString = String.format(Locale.getDefault(), "%d", unreadCount + mentionCount)
						}
						else {
							drawCount = false
							drawMention = true
							mentionString = String.format(Locale.getDefault(), "%d", unreadCount + mentionCount)
						}
					}
					else {
						drawCount = false
						drawMention = false
					}

					drawReactionMention = false
				}
				else {
					if (clearingDialog) {
						drawCount = false
						showChecks = false
					}
					else if (unreadCount != 0 && (unreadCount != 1 || unreadCount != mentionCount || message == null || !message!!.messageOwner!!.mentioned)) {
						drawCount = true
						countString = String.format(Locale.getDefault(), "%d", unreadCount)
					}
					else if (markUnread) {
						drawCount = true
						countString = ""
					}
					else {
						drawCount = false
					}

					if (mentionCount != 0) {
						drawMention = true
						mentionString = "@"
					}
					else {
						drawMention = false
					}

					drawReactionMention = reactionMentionCount > 0
				}

				if (message!!.isOut && draftMessage == null && showChecks && message?.messageOwner?.action !is TL_messageActionHistoryClear) {
					if (message!!.isSending) {
						drawCheck1 = false
						drawCheck2 = false
						drawClock = true
						drawError = false
					}
					else if (message!!.isSendError) {
						drawCheck1 = false
						drawCheck2 = false
						drawClock = false
						drawError = true
						drawCount = false
						drawMention = false
					}
					else if (message!!.isSent) {
						drawCheck1 = !message!!.isUnread || ChatObject.isChannel(chat) && !chat!!.megagroup
						drawCheck2 = true
						drawClock = false
						drawError = false
					}
				}
				else {
					drawCheck1 = false
					drawCheck2 = false
					drawClock = false
					drawError = false
				}
			}

			promoDialog = false

			val messagesController = MessagesController.getInstance(currentAccount)

			if (dialogsType == 0 && messagesController.isPromoDialog(dialogId, true)) {
				drawPinBackground = true
				promoDialog = true

				if (messagesController.promoDialogType == MessagesController.PROMO_TYPE_PROXY) {
					timeString = context.getString(R.string.UseProxySponsor)
				}
				else if (messagesController.promoDialogType == MessagesController.PROMO_TYPE_PSA) {
					timeString = LocaleController.getString("PsaType_" + messagesController.promoPsaType)

					if (timeString.isNullOrEmpty()) {
						timeString = context.getString(R.string.PsaTypeDefault)
					}

					if (!messagesController.promoPsaMessage.isNullOrEmpty()) {
						messageString = messagesController.promoPsaMessage
						hasMessageThumb = false
					}
				}
			}

			if (currentDialogFolderId != 0) {
				nameString = context.getString(R.string.ArchivedChats)
			}
			else {
				if (chat != null) {
					nameString = chat?.title
				}
				else if (user != null) {
					if (UserObject.isReplyUser(user)) {
						nameString = context.getString(R.string.RepliesTitle)
					}
					else if (UserObject.isUserSelf(user)) {
						if (useMeForMyMessages) {
							nameString = context.getString(R.string.FromYou)
						}
						else {
							if (dialogsType == 3) {
								drawPinBackground = true
							}

							nameString = context.getString(R.string.SavedMessages)
						}
					}
					else {
						nameString = UserObject.getUserName(user)
					}
				}

				if (nameString.isNullOrEmpty()) {
					nameString = context.getString(R.string.HiddenName)
				}
			}
		}

		val timeWidth: Int

		if (drawTime) {
			timeWidth = ceil(Theme.dialogs_timePaint.measureText(timeString).toDouble()).toInt()
			timeLayout = StaticLayout(timeString, Theme.dialogs_timePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

			timeLeft = if (!LocaleController.isRTL) {
				measuredWidth - AndroidUtilities.dp(16f) - timeWidth
			}
			else {
				AndroidUtilities.dp(16f)
			}
		}
		else {
			timeWidth = 0
			timeLayout = null
			timeLeft = 0
		}

		nameLeft = AndroidUtilities.dp(85f)

		if (drawPrivate) {
			privateLeft = nameLeft
			nameLeft += AndroidUtilities.dp(20f)
		}
		else {
			privateLeft = Int.MIN_VALUE
		}

		if (drawAdult) {
			adultLeft = nameLeft
			nameLeft += AndroidUtilities.dp(20f)
		}
		else {
			adultLeft = Int.MIN_VALUE
		}

		if (drawPaid) {
			paidLeft = nameLeft
			nameLeft += AndroidUtilities.dp(19f)
		}
		else {
			paidLeft = Int.MIN_VALUE
		}

		if (!LocaleController.isRTL) {
			nameWidth = measuredWidth - nameLeft - AndroidUtilities.dp(14f) - timeWidth
		}
		else {
			nameWidth = measuredWidth - nameLeft - AndroidUtilities.dp(77f) - timeWidth
			nameLeft += timeWidth
		}

		if (drawNameLock) {
			nameWidth -= AndroidUtilities.dp(4f) + Theme.dialogs_lockDrawable.intrinsicWidth
		}

		if (drawClock) {
			val w = Theme.dialogs_clockDrawable.intrinsicWidth + AndroidUtilities.dp(5f)

			nameWidth -= w

			if (!LocaleController.isRTL) {
				clockDrawLeft = timeLeft - w
			}
			else {
				clockDrawLeft = timeLeft + timeWidth + AndroidUtilities.dp(5f)
				nameLeft += w
			}
		}
		else if (drawCheck2) {
			val w = Theme.dialogs_checkDrawable.intrinsicWidth + AndroidUtilities.dp(5f)

			nameWidth -= w

			if (drawCheck1) {
				nameWidth -= Theme.dialogs_halfCheckDrawable.intrinsicWidth - AndroidUtilities.dp(8f)

				if (!LocaleController.isRTL) {
					halfCheckDrawLeft = timeLeft - w
					checkDrawLeft = halfCheckDrawLeft - AndroidUtilities.dp(5.5f)
				}
				else {
					checkDrawLeft = timeLeft + timeWidth + AndroidUtilities.dp(5f)
					halfCheckDrawLeft = checkDrawLeft + AndroidUtilities.dp(5.5f)
					nameLeft += w + Theme.dialogs_halfCheckDrawable.intrinsicWidth - AndroidUtilities.dp(8f)
				}
			}
			else {
				if (!LocaleController.isRTL) {
					checkDrawLeft1 = timeLeft - w
				}
				else {
					checkDrawLeft1 = timeLeft + timeWidth + AndroidUtilities.dp(5f)
					nameLeft += w
				}
			}
		}

		if (drawPremium && emojiStatus.drawable != null) {
			val w = AndroidUtilities.dp((6 + 24 + 6).toFloat())

			nameWidth -= w

			if (LocaleController.isRTL) {
				nameLeft += w
			}
		}
		else if (isMuted && !drawVerified && drawScam == 0) {
			val w = AndroidUtilities.dp(6f) + Theme.dialogs_muteDrawable.intrinsicWidth

			nameWidth -= w

			if (LocaleController.isRTL) {
				nameLeft += w
			}
		}
		else if (drawVerified) {
			val w = AndroidUtilities.dp(6f) + verifiedDrawable!!.intrinsicWidth

			nameWidth -= w

			if (LocaleController.isRTL) {
				nameLeft += w
			}
		}
		else if (drawPremium) {
			val w = AndroidUtilities.dp((6 + 24 + 6).toFloat())

			nameWidth -= w

			if (LocaleController.isRTL) {
				nameLeft += w
			}
		}
		else if (drawScam != 0) {
			val w = AndroidUtilities.dp(6f) + (if (drawScam == 1) Theme.dialogs_scamDrawable else Theme.dialogs_fakeDrawable).intrinsicWidth

			nameWidth -= w

			if (LocaleController.isRTL) {
				nameLeft += w
			}
		}

		try {
			var ellipsizeWidth = nameWidth - AndroidUtilities.dp(12f)

			if (ellipsizeWidth < 0) {
				ellipsizeWidth = 0
			}

			var nameStringFinal: CharSequence? = nameString?.replace('\n', ' ')

			if (nameLayoutEllipsizeByGradient) {
				nameLayoutFits = nameStringFinal?.length == TextUtils.ellipsize(nameStringFinal, Theme.dialogs_namePaint[paintIndex], ellipsizeWidth.toFloat(), TextUtils.TruncateAt.END).length
				ellipsizeWidth += AndroidUtilities.dp(48f)
			}

			nameStringFinal = TextUtils.ellipsize(nameStringFinal, Theme.dialogs_namePaint[paintIndex], ellipsizeWidth.toFloat(), TextUtils.TruncateAt.END)
			nameStringFinal = Emoji.replaceEmoji(nameStringFinal, Theme.dialogs_namePaint[paintIndex].fontMetricsInt, false)

			if (message != null && message!!.hasHighlightedWords()) {
				val s = AndroidUtilities.highlightText(nameStringFinal, message!!.highlightedWords)

				if (s != null) {
					nameStringFinal = s
				}
			}

			nameLayout = StaticLayout(nameStringFinal, Theme.dialogs_namePaint[paintIndex], max(ellipsizeWidth, nameWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			nameLayoutTranslateX = (if (nameLayoutEllipsizeByGradient && nameLayout!!.isRtlCharAt(0)) -AndroidUtilities.dp(36f) else 0).toFloat()
			nameLayoutEllipsizeLeft = nameLayout!!.isRtlCharAt(0)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		val avatarLeft: Int
		val avatarTop = AndroidUtilities.dp(9.5f)
		val thumbLeft: Int

		if (isPinned && unreadCount == 0) {
			timeTop = AndroidUtilities.dp(32f)
			countTop = AndroidUtilities.dp(52f)
		}
		else {
			timeTop = AndroidUtilities.dp(15f)
			countTop = AndroidUtilities.dp(35f)
		}

		pinTop = AndroidUtilities.dp(8f)

		var messageWidth = measuredWidth - AndroidUtilities.dp(134f)

		if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
			messageNameTop = AndroidUtilities.dp(1f)
			errorTop = AndroidUtilities.dp(35f)
			checkDrawTop = AndroidUtilities.dp(5f)

			if (LocaleController.isRTL) {
				messageNameLeft = AndroidUtilities.dp(16f)
				messageLeft = messageNameLeft
				avatarLeft = measuredWidth - AndroidUtilities.dp(73f)
				thumbLeft = avatarLeft - AndroidUtilities.dp((13 + 18).toFloat())
			}
			else {
				messageNameLeft = AndroidUtilities.dp(85f)
				messageLeft = messageNameLeft
				avatarLeft = AndroidUtilities.dp(16f)
				thumbLeft = avatarLeft + AndroidUtilities.dp((56 + 13).toFloat())
			}

			avatarImage.setImageCoordinates(avatarLeft.toFloat(), avatarTop.toFloat(), AndroidUtilities.dp(57f).toFloat(), AndroidUtilities.dp(57f).toFloat())
			thumbImage.setImageCoordinates(thumbLeft.toFloat(), (avatarTop + AndroidUtilities.dp(31f)).toFloat(), AndroidUtilities.dp(18f).toFloat(), AndroidUtilities.dp(18f).toFloat())
		}
		else {
			messageNameTop = AndroidUtilities.dp(15f)
			errorTop = AndroidUtilities.dp(31f)
			checkDrawTop = AndroidUtilities.dp(9f)

			if (LocaleController.isRTL) {
				messageNameLeft = AndroidUtilities.dp(22f)
				messageLeft = messageNameLeft
				avatarLeft = measuredWidth - AndroidUtilities.dp(73f)
				thumbLeft = avatarLeft - AndroidUtilities.dp((11 + thumbSize).toFloat())
			}
			else {
				messageNameLeft = AndroidUtilities.dp(85f)
				messageLeft = messageNameLeft
				avatarLeft = AndroidUtilities.dp(16f)
				thumbLeft = avatarLeft + AndroidUtilities.dp((56 + 11).toFloat())
			}

			avatarImage.setImageCoordinates(avatarLeft.toFloat(), avatarTop.toFloat(), AndroidUtilities.dp(57f).toFloat(), AndroidUtilities.dp(57f).toFloat())
			thumbImage.setImageCoordinates(thumbLeft.toFloat(), (avatarTop + AndroidUtilities.dp(30f)).toFloat(), AndroidUtilities.dp(thumbSize.toFloat()).toFloat(), AndroidUtilities.dp(thumbSize.toFloat()).toFloat())
		}

		if (isPinned) {
			pinLeft = if (!LocaleController.isRTL) {
				measuredWidth - Theme.dialogs_pinnedDrawable.intrinsicWidth - AndroidUtilities.dp(14f)
			}
			else {
				AndroidUtilities.dp(14f)
			}
		}

		if (drawError) {
			val w = AndroidUtilities.dp((23 + 8).toFloat())

			if (!LocaleController.isRTL) {
				errorLeft = measuredWidth - AndroidUtilities.dp((23 + 11).toFloat())
			}
			else {
				errorLeft = AndroidUtilities.dp(11f)
				messageLeft += w
				messageNameLeft += w
			}
		}
		else if (countString != null || mentionString != null || drawReactionMention) {
			if (countString != null) {
				countWidth = max(AndroidUtilities.dp(12f), ceil(Theme.dialogs_countTextPaint.measureText(countString).toDouble()).toInt())
				countLayout = StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

				val w = countWidth + AndroidUtilities.dp(18f)

				if (!LocaleController.isRTL) {
					countLeft = measuredWidth - countWidth - AndroidUtilities.dp(20f)
				}
				else {
					countLeft = AndroidUtilities.dp(20f)
					messageLeft += w
					messageNameLeft += w
				}
				drawCount = true
			}
			else {
				countWidth = 0
			}

			if (mentionString != null) {
				if (currentDialogFolderId != 0) {
					mentionWidth = max(AndroidUtilities.dp(12f), ceil(Theme.dialogs_countTextPaint.measureText(mentionString).toDouble()).toInt())
					mentionLayout = StaticLayout(mentionString, Theme.dialogs_countTextPaint, mentionWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
				}
				else {
					mentionWidth = AndroidUtilities.dp(12f)
				}

				val w = mentionWidth + AndroidUtilities.dp(18f)

				messageWidth -= w

				if (!LocaleController.isRTL) {
					mentionLeft = measuredWidth - mentionWidth - AndroidUtilities.dp(20f) - if (countWidth != 0) countWidth + AndroidUtilities.dp(18f) else 0
				}
				else {
					mentionLeft = AndroidUtilities.dp(20f) + if (countWidth != 0) countWidth + AndroidUtilities.dp(18f) else 0
					messageLeft += w
					messageNameLeft += w
				}

				drawMention = true
			}
			else {
				mentionWidth = 0
			}

			if (drawReactionMention) {
				val w = AndroidUtilities.dp(24f)

				messageWidth -= w

				if (!LocaleController.isRTL) {
					reactionMentionLeft = measuredWidth - AndroidUtilities.dp(32f)

					if (drawMention) {
						reactionMentionLeft -= if (mentionWidth != 0) mentionWidth + AndroidUtilities.dp(18f) else 0
					}

					if (drawCount) {
						reactionMentionLeft -= if (countWidth != 0) countWidth + AndroidUtilities.dp(18f) else 0
					}
				}
				else {
					reactionMentionLeft = AndroidUtilities.dp(20f)

					if (drawMention) {
						reactionMentionLeft += if (mentionWidth != 0) mentionWidth + AndroidUtilities.dp(18f) else 0
					}

					if (drawCount) {
						reactionMentionLeft += if (countWidth != 0) countWidth + AndroidUtilities.dp(18f) else 0
					}

					messageLeft += w
					messageNameLeft += w
				}
			}
		}
		else {
			if (isPinned) {
				val w = Theme.dialogs_pinnedDrawable.intrinsicWidth + AndroidUtilities.dp(8f)

				messageWidth -= w

				if (LocaleController.isRTL) {
					messageLeft += w
					messageNameLeft += w
				}
			}

			drawCount = false
			drawMention = false
		}

		if (checkMessage) {
			if (messageString == null) {
				messageString = ""
			}

			var mess: CharSequence = messageString

			if (mess.length > 150) {
				mess = mess.subSequence(0, 150)
			}

			mess = if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || messageNameString != null) {
				AndroidUtilities.replaceNewLines(mess)
			}
			else {
				AndroidUtilities.replaceTwoNewLinesToOne(mess)
			}

			messageString = Emoji.replaceEmoji(mess, Theme.dialogs_messagePaint[paintIndex].fontMetricsInt, false)

			if (message != null) {
				val s = AndroidUtilities.highlightText(messageString, message!!.highlightedWords)

				if (s != null) {
					messageString = s
				}
			}
		}

		messageWidth = max(AndroidUtilities.dp(12f), messageWidth)

		if ((useForceThreeLines || SharedConfig.useThreeLinesLayout) && messageNameString != null && (currentDialogFolderId == 0 || currentDialogFolderDialogsCount == 1)) {
			try {
				if (message != null && message!!.hasHighlightedWords()) {
					val s = AndroidUtilities.highlightText(messageNameString, message!!.highlightedWords)

					if (s != null) {
						messageNameString = s
					}
				}

				messageNameLayout = StaticLayoutEx.createStaticLayout(messageNameString, Theme.dialogs_messageNamePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false, TextUtils.TruncateAt.END, messageWidth, 1)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			messageTop = AndroidUtilities.dp(1f)
			thumbImage.imageY = (avatarTop + AndroidUtilities.dp(38.5f)).toFloat()
		}
		else {
			messageNameLayout = null

			if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
				messageTop = AndroidUtilities.dp(32f)
				thumbImage.imageY = (avatarTop + AndroidUtilities.dp(38.5f)).toFloat()
			}
			else {
				messageTop = AndroidUtilities.dp(40f)
			}
		}
		try {
			val messageStringFinal: CharSequence?

			if ((useForceThreeLines || SharedConfig.useThreeLinesLayout) && currentDialogFolderId != 0 && currentDialogFolderDialogsCount > 1) {
				messageStringFinal = messageNameString
				messageNameString = null
				currentMessagePaint = Theme.dialogs_messagePaint[paintIndex]
			}
			else if (!useForceThreeLines && !SharedConfig.useThreeLinesLayout || messageNameString != null) {
				if (hasMessageThumb && isSearch == true) {
					if (message!!.hasHighlightedWords() && !message?.messageOwner?.message.isNullOrEmpty()) {
						messageString = message!!.messageTrimmedToHighlight

						if (message!!.messageTrimmedToHighlight != null) {
							messageString = message!!.messageTrimmedToHighlight
						}

						val w = measuredWidth - AndroidUtilities.dp((72 + 23 + thumbSize + 6).toFloat())

						messageString = AndroidUtilities.ellipsizeCenterEnd(messageString, message!!.highlightedWords!![0], w, currentMessagePaint, 130).toString()
					}
					else {
						if (messageString!!.length > 150) {
							messageString = messageString.subSequence(0, 150)
						}

						messageString = AndroidUtilities.replaceNewLines(messageString)
					}

					if (messageString !is SpannableStringBuilder) {
						messageString = SpannableStringBuilder(messageString)
					}

					val builder = messageString

					builder.insert(0, " ")
					builder.setSpan(FixedWidthSpan(AndroidUtilities.dp((thumbSize + 6).toFloat())), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

					Emoji.replaceEmoji(builder, Theme.dialogs_messagePaint[paintIndex].fontMetricsInt, false)

					if (message!!.hasHighlightedWords()) {
						val s = AndroidUtilities.highlightText(builder, message!!.highlightedWords)

						if (s != null) {
							messageString = s
						}
					}
				}

				messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, (messageWidth - AndroidUtilities.dp(12f)).toFloat(), TextUtils.TruncateAt.END)
			}
			else {
				messageStringFinal = messageString
			}

			// Removing links and bold spans to get rid of underlining and boldness

			if (messageStringFinal is Spannable) {
				for (span in messageStringFinal.getSpans(0, messageStringFinal.length, CharacterStyle::class.java)) {
					if (span is ClickableSpan || span is StyleSpan && span.style == Typeface.BOLD || span is TypefaceSpan && span.isBold) {
						messageStringFinal.removeSpan(span)
					}
				}
			}

			if (useForceThreeLines || SharedConfig.useThreeLinesLayout) {
				if (hasMessageThumb && messageNameString != null) {
					messageWidth += AndroidUtilities.dp(6f)
				}

				messageLayout = StaticLayoutEx.createStaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1f).toFloat(), false, TextUtils.TruncateAt.END, messageWidth, if (messageNameString != null) 1 else 2)
			}
			else {
				if (hasMessageThumb) {
					messageWidth += thumbSize + AndroidUtilities.dp(6f)

					if (LocaleController.isRTL) {
						messageLeft -= thumbSize + AndroidUtilities.dp(6f)
					}
				}

				messageLayout = StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}

			spoilersPool.addAll(spoilers)
			spoilers.clear()
			SpoilerEffect.addSpoilers(this, messageLayout, spoilersPool, spoilers)
			animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, animatedEmojiStack, messageLayout)
		}
		catch (e: Exception) {
			messageLayout = null
			FileLog.e(e)
		}

		var widthpx: Double
		var left: Float

		if (LocaleController.isRTL) {
			if (nameLayout != null && nameLayout!!.lineCount > 0) {
				left = nameLayout!!.getLineLeft(0)
				widthpx = ceil(nameLayout!!.getLineWidth(0).toDouble())

				if (nameLayoutEllipsizeByGradient) {
					widthpx = min(nameWidth.toDouble(), widthpx)
				}
				if (isMuted && !drawVerified && drawScam == 0) {
					nameMuteLeft = (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6f) - Theme.dialogs_muteDrawable.intrinsicWidth).toInt()
				}
				else if (drawVerified) {
					nameMuteLeft = (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6f) - verifiedDrawable!!.intrinsicWidth).toInt()
				}
				else if (drawPremium) {
					nameMuteLeft = (nameLeft + (nameWidth - widthpx - left) - AndroidUtilities.dp(24f)).toInt()
				}
				else if (drawScam != 0) {
					nameMuteLeft = (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6f) - (if (drawScam == 1) Theme.dialogs_scamDrawable else Theme.dialogs_fakeDrawable).intrinsicWidth).toInt()
				}

				if (left == 0f) {
					if (widthpx < nameWidth) {
						nameLeft += (nameWidth - widthpx).toInt()
					}
				}
			}

			messageLayout?.let { messageLayout ->
				val lineCount = messageLayout.lineCount

				if (lineCount > 0) {
					var w = Int.MAX_VALUE

					for (a in 0 until lineCount) {
						left = messageLayout.getLineLeft(a)

						if (left == 0f) {
							widthpx = ceil(messageLayout.getLineWidth(a).toDouble())
							w = min(w, (messageWidth - widthpx).toInt())
						}
						else {
							w = 0
							break
						}
					}

					if (w != Int.MAX_VALUE) {
						messageLeft += w
					}
				}
			}

			messageNameLayout?.let { messageNameLayout ->
				if (messageNameLayout.lineCount > 0) {
					left = messageNameLayout.getLineLeft(0)

					if (left == 0f) {
						widthpx = ceil(messageNameLayout.getLineWidth(0).toDouble())

						if (widthpx < messageWidth) {
							messageNameLeft += (messageWidth - widthpx).toInt()
						}
					}
				}
			}
		}
		else {
			nameLayout?.let { nameLayout ->
				if (nameLayout.lineCount > 0) {
					left = nameLayout.getLineRight(0)

					if (nameLayoutEllipsizeByGradient) {
						left = min(nameWidth.toFloat(), left)
					}

					if (left == nameWidth.toFloat()) {
						widthpx = ceil(nameLayout.getLineWidth(0).toDouble())

						if (nameLayoutEllipsizeByGradient) {
							widthpx = min(nameWidth.toDouble(), widthpx)
						}

						if (widthpx < nameWidth) {
							nameLeft -= (nameWidth - widthpx).toInt()
						}
					}

					if (isMuted || drawVerified || drawPremium || drawScam != 0) {
						nameMuteLeft = (nameLeft + left + AndroidUtilities.dp(6f)).toInt()
					}
				}
			}

			messageLayout?.let { messageLayout ->
				val lineCount = messageLayout.lineCount

				if (lineCount > 0) {
					left = Int.MAX_VALUE.toFloat()

					for (a in 0 until lineCount) {
						left = min(left, messageLayout.getLineLeft(a))
					}

					messageLeft -= left.toInt()
				}
			}

			messageNameLayout?.let { messageNameLayout ->
				if (messageNameLayout.lineCount > 0) {
					messageNameLeft -= messageNameLayout.getLineLeft(0).toInt()
				}
			}
		}

		if (hasMessageThumb) {
			messageLayout?.let { messageLayout ->
				try {
					val textLen = messageLayout.text.length

					if (offsetName >= textLen) {
						offsetName = textLen - 1
					}

					val x1 = messageLayout.getPrimaryHorizontal(offsetName)
					val x2 = messageLayout.getPrimaryHorizontal(offsetName + 1)
					var offset = ceil(min(x1, x2).toDouble()).toInt()

					if (offset != 0) {
						offset += AndroidUtilities.dp(3f)
					}

					if (isSearch == true) {
						thumbImage.setImageX(messageLeft)
					} else {
						thumbImage.setImageX(messageLeft + offset)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		if (printingStringType >= 0) {
			messageLayout?.let { messageLayout ->
				val x1: Float
				val x2: Float

				if (printingStringReplaceIndex >= 0 && printingStringReplaceIndex + 1 < messageLayout.text.length) {
					x1 = messageLayout.getPrimaryHorizontal(printingStringReplaceIndex)
					x2 = messageLayout.getPrimaryHorizontal(printingStringReplaceIndex + 1)
				}
				else {
					x1 = messageLayout.getPrimaryHorizontal(0)
					x2 = messageLayout.getPrimaryHorizontal(1)
				}

				statusDrawableLeft = if (x1 < x2) {
					(messageLeft + x1).toInt()
				}
				else {
					(messageLeft + x2 + AndroidUtilities.dp(3f)).toInt()
				}
			}
		}
	}

	private fun drawCheckStatus(canvas: Canvas, drawClock: Boolean, drawCheck1: Boolean, drawCheck2: Boolean, moveCheck: Boolean, alpha: Float) {
		if (alpha == 0f && !moveCheck) {
			return
		}

		val scale = 0.5f + 0.5f * alpha

		if (drawClock) {
			(Theme.dialogs_clockDrawable as? MsgClockDrawable)?.setColor(context.getColor(R.color.brand))

			setDrawableBounds(Theme.dialogs_clockDrawable, clockDrawLeft, timeTop + AndroidUtilities.dp(2f))

			if (alpha != 1f) {
				canvas.save()
				canvas.scale(scale, scale, Theme.dialogs_clockDrawable.bounds.centerX().toFloat(), Theme.dialogs_halfCheckDrawable.bounds.centerY().toFloat())
				Theme.dialogs_clockDrawable.alpha = (255 * alpha).toInt()
			}

			Theme.dialogs_clockDrawable.draw(canvas)

			if (alpha != 1f) {
				canvas.restore()
				Theme.dialogs_clockDrawable.alpha = 255
			}

			invalidate()
		}
		else if (drawCheck2) {
			if (drawCheck1) {
				val offsetY = if (isPinned) {
					 AndroidUtilities.dp(24F)
				} else {
					 AndroidUtilities.dp(8F)
				}

				setDrawableBounds(Theme.dialogs_halfCheckDrawable, halfCheckDrawLeft, checkDrawTop + offsetY)

				if (moveCheck) {
					canvas.save()
					canvas.scale(scale, scale, Theme.dialogs_halfCheckDrawable.getBounds().centerX().toFloat(), Theme.dialogs_halfCheckDrawable.getBounds().centerY().toFloat())
					Theme.dialogs_halfCheckDrawable.alpha = (255 * alpha).toInt()
				}

				if (!moveCheck) {
					canvas.save()
					canvas.scale(scale, scale, Theme.dialogs_halfCheckDrawable.getBounds().centerX().toFloat(), Theme.dialogs_halfCheckDrawable.getBounds().centerY().toFloat())
					Theme.dialogs_halfCheckDrawable.alpha = (255 * alpha).toInt()
					Theme.dialogs_checkReadDrawable.alpha = (255 * alpha).toInt()
				}

				Theme.dialogs_halfCheckDrawable.draw(canvas)

				if (moveCheck) {
					canvas.restore()
					canvas.save()
					canvas.translate(AndroidUtilities.dp(4F) * (1f - alpha), 0F)
				}
				setDrawableBounds(Theme.dialogs_checkReadDrawable, checkDrawLeft, checkDrawTop + offsetY)
				Theme.dialogs_checkReadDrawable.draw(canvas)
				if (moveCheck) {
					canvas.restore()
					Theme.dialogs_halfCheckDrawable.alpha = 255
				}

				if (!moveCheck) {
					canvas.restore()
					Theme.dialogs_halfCheckDrawable.alpha = 255
					Theme.dialogs_checkReadDrawable.alpha = 255
				}
			}
			else {
				val offsetY = if (isPinned) {
					AndroidUtilities.dp(24F)
				} else {
					AndroidUtilities.dp(8F)
				}

				setDrawableBounds(Theme.dialogs_checkDrawable, checkDrawLeft1, checkDrawTop + offsetY)
				if (alpha != 1f) {
					canvas.save()
					canvas.scale(scale, scale, Theme.dialogs_checkDrawable.getBounds().centerX().toFloat(), Theme.dialogs_halfCheckDrawable.getBounds().centerY().toFloat())
					Theme.dialogs_checkDrawable.alpha = (255 * alpha).toInt()
				}
				Theme.dialogs_checkDrawable.draw(canvas)
				if (alpha != 1f) {
					canvas.restore()
					Theme.dialogs_checkDrawable.alpha = 255
				}
			}
		}
	}

	fun isPointInsideAvatar(x: Float, y: Float): Boolean {
		return if (!LocaleController.isRTL) {
			x >= 0 && x < AndroidUtilities.dp(60f)
		}
		else {
			x >= measuredWidth - AndroidUtilities.dp(60f) && x < measuredWidth
		}
	}

	fun setDialogSelected(value: Boolean) {
		if (isSelected != value) {
			invalidate()
		}

		isSelected = value
	}

	fun checkCurrentDialogIndex(frozen: Boolean) {
		if (parentFragment == null) {
			return
		}

		val dialogsArray = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, frozen)

		if (dialogIndex < dialogsArray.size) {
			val dialog = dialogsArray[dialogIndex]
			val nextDialog = if (dialogIndex + 1 < dialogsArray.size) dialogsArray[dialogIndex + 1] else null
			val newDraftMessage = MediaDataController.getInstance(currentAccount).getDraft(dialogId, 0)

			val newMessageObject = if (currentDialogFolderId != 0) {
				findFolderTopMessage()
			}
			else {
				MessagesController.getInstance(currentAccount).dialogMessage[dialog.id]
			}

			if (dialogId != dialog.id || message != null && message!!.id != dialog.top_message || newMessageObject != null && newMessageObject.messageOwner?.edit_date != currentEditDate || unreadCount != dialog.unread_count || mentionCount != dialog.unread_mentions_count || markUnread != dialog.unread_mark || message !== newMessageObject || newDraftMessage !== draftMessage || isPinned != dialog.pinned) {
				val dialogChanged = dialogId != dialog.id

				dialogId = dialog.id

				if (dialogChanged) {
					lastDialogChangedTime = System.currentTimeMillis()

					statusDrawableAnimator?.removeAllListeners()
					statusDrawableAnimator?.cancel()

					statusDrawableAnimationInProgress = false
					lastStatusDrawableParams = -1
				}

				currentDialogFolderId = if (dialog is TL_dialogFolder) {
					dialog.folder.id
				}
				else {
					0
				}

				if (dialogsType == 7 || dialogsType == 8) {
					val filter = MessagesController.getInstance(currentAccount).selectedDialogFilter[if (dialogsType == 8) 1 else 0]

					fullSeparator = dialog is TL_dialog && nextDialog != null && filter != null && filter.pinnedDialogs.indexOfKey(dialog.id) >= 0 && filter.pinnedDialogs.indexOfKey(nextDialog.id) < 0
					fullSeparator2 = false
				}
				else {
					fullSeparator = dialog is TL_dialog && dialog.pinned && nextDialog != null && !nextDialog.pinned
					fullSeparator2 = dialog is TL_dialogFolder && nextDialog != null && !nextDialog.pinned
				}

				update(0, !dialogChanged)

				if (dialogChanged) {
					reorderIconProgress = if (isPinned && drawReorder) 1.0f else 0.0f
				}

				checkOnline()
				checkGroupCall()
			}
		}
	}

	fun animateArchiveAvatar() {
		if (avatarDrawable.avatarType != AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
			return
		}

		animatingArchiveAvatar = true
		animatingArchiveAvatarProgress = 0.0f

		Theme.dialogs_archiveAvatarDrawable.setProgress(0.0f)
		Theme.dialogs_archiveAvatarDrawable.start()

		invalidate()
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		checkBox?.setChecked(checked, animated)
	}

	private fun findFolderTopMessage(): MessageObject? {
		if (parentFragment == null) {
			return null
		}

		val dialogs = parentFragment.getDialogsArray(currentAccount, dialogsType, currentDialogFolderId, false)
		var maxMessage: MessageObject? = null

		for (dialog in dialogs) {
			val `object` = MessagesController.getInstance(currentAccount).dialogMessage[dialog.id]

			if (`object` != null && (maxMessage == null || `object`.messageOwner!!.date > maxMessage.messageOwner!!.date)) {
				maxMessage = `object`
			}

			if (dialog.pinnedNum == 0 && maxMessage != null) {
				break
			}
		}

		return maxMessage
	}

	val isFolderCell: Boolean
		get() = currentDialogFolderId != 0

	@JvmOverloads
	fun update(mask: Int, animated: Boolean = true) {
		val customDialog = customDialog

		if (customDialog != null) {
			lastMessageDate = customDialog.date
			lastUnreadState = customDialog.unread_count != 0
			unreadCount = customDialog.unread_count
			isPinned = customDialog.pinned
			isMuted = customDialog.muted
			avatarDrawable.setInfo(customDialog.name, null)
			avatarImage.setImage(null, "50_50", avatarDrawable, null, 0)
			thumbImage.setImageBitmap(null as BitmapDrawable?)
		}
		else {
			val oldUnreadCount = unreadCount
			val oldHasReactionsMentions = reactionMentionCount != 0
			val oldMarkUnread = markUnread

			if (isDialogCell) {
				val dialog = MessagesController.getInstance(currentAccount).dialogs_dict[dialogId]

				if (dialog != null) {
					if (mask == 0) {
						clearingDialog = MessagesController.getInstance(currentAccount).isClearingDialog(dialog.id)
						message = MessagesController.getInstance(currentAccount).dialogMessage[dialog.id]
						lastUnreadState = message != null && message!!.isUnread

						if (dialog is TL_dialogFolder) {
							unreadCount = MessagesStorage.getInstance(currentAccount).archiveUnreadCount
							mentionCount = 0
							reactionMentionCount = 0
						}
						else {
							unreadCount = dialog.unread_count
							mentionCount = dialog.unread_mentions_count
							reactionMentionCount = dialog.unread_reactions_count
						}

						markUnread = dialog.unread_mark
						currentEditDate = message?.messageOwner?.edit_date ?: 0
						lastMessageDate = dialog.last_message_date

						isPinned = if (dialogsType == 7 || dialogsType == 8) {
							val filter = MessagesController.getInstance(currentAccount).selectedDialogFilter[if (dialogsType == 8) 1 else 0]
							filter != null && filter.pinnedDialogs.indexOfKey(dialog.id) >= 0
						}
						else {
							currentDialogFolderId == 0 && dialog.pinned
						}

						message?.messageOwner?.send_state?.let {
							lastSendState = it
						}
					}
				}
				else {
					unreadCount = 0
					mentionCount = 0
					reactionMentionCount = 0
					currentEditDate = 0
					lastMessageDate = 0
					clearingDialog = false
				}
			}
			else {
				isPinned = false
			}

			if (dialogsType == 2) {
				isPinned = false
			}

			if (mask != 0) {
				var continueUpdate = false

				if (user != null && mask and MessagesController.UPDATE_MASK_STATUS != 0) {
					user = MessagesController.getInstance(currentAccount).getUser(user!!.id)
					invalidate()
				}

				if (user != null && mask and MessagesController.UPDATE_MASK_EMOJI_STATUS != 0) {
					user = MessagesController.getInstance(currentAccount).getUser(user!!.id)

					if (user!!.emoji_status is TL_emojiStatusUntil && (user!!.emoji_status as TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
						nameLayoutEllipsizeByGradient = true
						emojiStatus[(user!!.emoji_status as TL_emojiStatusUntil).document_id] = animated
					}
					else if (user!!.emoji_status is TL_emojiStatus) {
						nameLayoutEllipsizeByGradient = true
						emojiStatus[(user!!.emoji_status as TL_emojiStatus).document_id] = animated
					}
					else {
						nameLayoutEllipsizeByGradient = true
						emojiStatus[PremiumGradient.getInstance().premiumStarDrawableMini] = animated
					}

					invalidate()
				}

				if (isDialogCell) {
					if (mask and MessagesController.UPDATE_MASK_USER_PRINT != 0) {
						val printString = MessagesController.getInstance(currentAccount).getPrintingString(dialogId, 0, true)

						if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && lastPrintString != printString) {
							continueUpdate = true
						}
					}
				}

				if (!continueUpdate && mask and MessagesController.UPDATE_MASK_MESSAGE_TEXT != 0) {
					if (message != null && message!!.messageText !== lastMessageString) {
						continueUpdate = true
					}
				}

				if (!continueUpdate && mask and MessagesController.UPDATE_MASK_CHAT != 0 && chat != null) {
					val newChat = MessagesController.getInstance(currentAccount).getChat(chat!!.id)

					if ((newChat!!.call_active && newChat.call_not_empty) != hasCall) {
						continueUpdate = true
					}
				}

				if (!continueUpdate && mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
					if (chat == null) {
						continueUpdate = true
					}
				}

				if (!continueUpdate && mask and MessagesController.UPDATE_MASK_NAME != 0) {
					if (chat == null) {
						continueUpdate = true
					}
				}

				if (!continueUpdate && mask and MessagesController.UPDATE_MASK_CHAT_AVATAR != 0) {
					if (user == null) {
						continueUpdate = true
					}
				}

				if (!continueUpdate && mask and MessagesController.UPDATE_MASK_CHAT_NAME != 0) {
					if (user == null) {
						continueUpdate = true
					}
				}

				if (!continueUpdate) {
					if (message != null && lastUnreadState != message!!.isUnread) {
						lastUnreadState = message!!.isUnread
						continueUpdate = true
					}

					if (isDialogCell) {
						val dialog = MessagesController.getInstance(currentAccount).dialogs_dict[dialogId]
						val newCount: Int
						val newMentionCount: Int
						var newReactionCount = 0

						if (dialog is TL_dialogFolder) {
							newCount = MessagesStorage.getInstance(currentAccount).archiveUnreadCount
							newMentionCount = 0
						}
						else if (dialog != null) {
							newCount = dialog.unread_count
							newMentionCount = dialog.unread_mentions_count
							newReactionCount = dialog.unread_reactions_count
						}
						else {
							newCount = 0
							newMentionCount = 0
						}
						if (dialog != null && (unreadCount != newCount || markUnread != dialog.unread_mark || mentionCount != newMentionCount || reactionMentionCount != newReactionCount)) {
							unreadCount = newCount
							mentionCount = newMentionCount
							markUnread = dialog.unread_mark
							reactionMentionCount = newReactionCount
							continueUpdate = true
						}
					}
				}

				if (!continueUpdate && mask and MessagesController.UPDATE_MASK_SEND_STATE != 0) {
					if (message != null && lastSendState != message?.messageOwner?.send_state) {
						lastSendState = message!!.messageOwner!!.send_state
						continueUpdate = true
					}
				}

				if (!continueUpdate) {
					invalidate()
					return
				}
			}

			user = null
			chat = null
			encryptedChat = null

			val dialogId = if (currentDialogFolderId != 0) {
				isMuted = false
				message = findFolderTopMessage()
				message?.dialogId ?: 0
			}
			else {
				isMuted = isDialogCell && MessagesController.getInstance(currentAccount).isDialogMuted(this.dialogId)
				this.dialogId
			}

			if (dialogId != 0L) {
				if (DialogObject.isEncryptedDialog(dialogId)) {
					encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialogId))

					if (encryptedChat != null) {
						user = MessagesController.getInstance(currentAccount).getUser(encryptedChat!!.user_id)
					}
				}
				else if (DialogObject.isUserDialog(dialogId)) {
					user = MessagesController.getInstance(currentAccount).getUser(dialogId)
				}
				else {
					chat = MessagesController.getInstance(currentAccount).getChat(-dialogId)

					if (!isDialogCell && chat != null && chat!!.migrated_to != null) {
						val chat2 = MessagesController.getInstance(currentAccount).getChat(chat!!.migrated_to.channel_id)

						if (chat2 != null) {
							chat = chat2
						}
					}
				}

				if (useMeForMyMessages && user != null && message!!.isOutOwner) {
					user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).clientUserId)
				}

				drawAdult = chat?.adult ?: false
				drawPaid = ChatObject.isSubscriptionChannel(chat) || ChatObject.isOnlineCourse(chat) || ChatObject.isPaidChannel(chat)
				drawPrivate = user == null && chat != null && chat?.username.isNullOrEmpty()
			}
			else {
				drawAdult = false
				drawPaid = false
				drawPrivate = false
			}

			if (currentDialogFolderId != 0) {
				Theme.dialogs_archiveAvatarDrawable.callback = this
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_ARCHIVED
				avatarImage.setImage(null, null, avatarDrawable, null, user, 0)
			}
			else {
				if (user != null) {
					avatarDrawable.setInfo(user)

					if (UserObject.isReplyUser(user)) {
						avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES
						avatarImage.setImage(null, null, avatarDrawable, null, user, 0)
					}
					else if (UserObject.isUserSelf(user) && !useMeForMyMessages) {
						avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
						avatarImage.setImage(null, null, avatarDrawable, null, user, 0)
					}
					else {
						avatarImage.setForUserOrChat(user, avatarDrawable, null, true)
					}
				}
				else if (chat != null) {
					avatarDrawable.setInfo(chat)
					avatarImage.setForUserOrChat(chat, avatarDrawable)
				}
			}

			if (animated && (oldUnreadCount != unreadCount || oldMarkUnread != markUnread) && System.currentTimeMillis() - lastDialogChangedTime > 100) {
				countAnimator?.cancel()

				countAnimator = ValueAnimator.ofFloat(0f, 1f)

				countAnimator?.addUpdateListener {
					countChangeProgress = it.animatedValue as Float
					invalidate()
				}

				countAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						countChangeProgress = 1f
						countOldLayout = null
						countAnimationStableLayout = null
						countAnimationInLayout = null
						invalidate()
					}
				})

				if ((oldUnreadCount == 0 || markUnread) && !(!markUnread && oldMarkUnread)) {
					countAnimator?.duration = 220
					countAnimator?.interpolator = OvershootInterpolator()
				}
				else if (unreadCount == 0) {
					countAnimator?.duration = 150
					countAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
				}
				else {
					countAnimator?.duration = 430
					countAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
				}

				if (drawCount && drawCount2 && countLayout != null) {
					val oldStr = oldUnreadCount.toString()
					val newStr = unreadCount.toString()

					if (oldStr.length == newStr.length) {
						val oldSpannableStr = SpannableStringBuilder(oldStr)
						val newSpannableStr = SpannableStringBuilder(newStr)
						val stableStr = SpannableStringBuilder(newStr)

						for (i in oldStr.indices) {
							if (oldStr[i] == newStr[i]) {
								oldSpannableStr.setSpan(EmptyStubSpan(), i, i + 1, 0)
								newSpannableStr.setSpan(EmptyStubSpan(), i, i + 1, 0)
							}
							else {
								stableStr.setSpan(EmptyStubSpan(), i, i + 1, 0)
							}
						}

						val countOldWidth = max(AndroidUtilities.dp(12f), ceil(Theme.dialogs_countTextPaint.measureText(oldStr).toDouble()).toInt())

						countOldLayout = StaticLayout(oldSpannableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
						countAnimationStableLayout = StaticLayout(stableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
						countAnimationInLayout = StaticLayout(newSpannableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
					}
					else {
						countOldLayout = countLayout
					}
				}

				countWidthOld = countWidth
				countLeftOld = countLeft
				countAnimationIncrement = unreadCount > oldUnreadCount

				countAnimator?.start()
			}

			val newHasReactionsMentions = reactionMentionCount != 0

			if (animated && newHasReactionsMentions != oldHasReactionsMentions) {
				reactionsMentionsAnimator?.cancel()
				reactionsMentionsChangeProgress = 0f
				reactionsMentionsAnimator = ValueAnimator.ofFloat(0f, 1f)

				reactionsMentionsAnimator?.addUpdateListener {
					reactionsMentionsChangeProgress = it.animatedValue as Float
					invalidate()
				}

				reactionsMentionsAnimator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						reactionsMentionsChangeProgress = 1f
						invalidate()
					}
				})

				if (newHasReactionsMentions) {
					reactionsMentionsAnimator?.duration = 220
					reactionsMentionsAnimator?.interpolator = OvershootInterpolator()
				}
				else {
					reactionsMentionsAnimator?.duration = 150
					reactionsMentionsAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
				}

				reactionsMentionsAnimator?.start()
			}
		}

		if (measuredWidth != 0 || measuredHeight != 0) {
			buildLayout()
		}
		else {
			requestLayout()
		}

		if (!animated) {
			dialogMutedProgress = if (isMuted) 1f else 0f
			countAnimator?.cancel()
		}

		invalidate()
	}

	override fun getTranslationX(): Float {
		return translationX
	}

	override fun setTranslationX(value: Float) {
		translationX = value.toInt().toFloat()

		if (translationDrawable != null && translationX == 0f) {
			translationDrawable?.setProgress(0.0f)
			translationAnimationStarted = false
			archiveHidden = SharedConfig.archiveHidden
			currentRevealProgress = 0f
			isSliding = false
		}

		if (translationX != 0f) {
			isSliding = true
		}
		else {
			currentRevealBounceProgress = 0f
			currentRevealProgress = 0f
			drawRevealBackground = false
		}

		if (isSliding && !swipeCanceled) {
			val prevValue = drawRevealBackground

			drawRevealBackground = abs(translationX) >= measuredWidth * 0.45f

			if (prevValue != drawRevealBackground && archiveHidden == SharedConfig.archiveHidden) {
				runCatching {
					performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				}
			}
		}

		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		if (dialogId == 0L && customDialog == null) {
			return
		}

		var needInvalidate = false

		if (currentDialogFolderId != 0 && archivedChatsDrawable != null && archivedChatsDrawable!!.getOutProgress() == 0.0f && translationX == 0.0f) {
			if (!drawingForBlur) {
				canvas.save()
				canvas.clipRect(0, 0, measuredWidth, measuredHeight)
				archivedChatsDrawable?.draw(canvas)
				canvas.restore()
			}

			return
		}

		val newTime = SystemClock.elapsedRealtime()
		var dt = newTime - lastUpdateTime

		if (dt > 17) {
			dt = 17
		}

		lastUpdateTime = newTime

		if (clipProgress != 0.0f && Build.VERSION.SDK_INT != 24) {
			canvas.save()
			canvas.clipRect(0f, topClip * clipProgress, measuredWidth.toFloat(), (measuredHeight - (bottomClip * clipProgress).toInt()).toFloat())
		}

		var backgroundColor: Int

		if (translationX != 0f || cornerProgress != 0.0f) {
			canvas.save()

			val swipeMessage: String
			val revealBackgroundColor: Int
			var swipeMessageStringId: Int

			if (currentDialogFolderId != 0) {
				if (archiveHidden) {
					backgroundColor = context.getColor(R.color.medium_gray)
					revealBackgroundColor = context.getColor(R.color.brand)
					swipeMessage = context.getString(R.string.UnhideFromTop.also { swipeMessageStringId = it })
					translationDrawable = Theme.dialogs_unpinArchiveDrawable
				}
				else {
					backgroundColor = context.getColor(R.color.brand)
					revealBackgroundColor = context.getColor(R.color.medium_gray)
					swipeMessage = context.getString(R.string.HideOnTop.also { swipeMessageStringId = it })
					translationDrawable = Theme.dialogs_pinArchiveDrawable
				}
			}
			else {
				if (promoDialog) {
					backgroundColor = context.getColor(R.color.brand)
					revealBackgroundColor = context.getColor(R.color.medium_gray)
					swipeMessage = context.getString(R.string.PsaHide.also { swipeMessageStringId = it })
					translationDrawable = Theme.dialogs_hidePsaDrawable
				}
				else if (folderId == 0) {
					backgroundColor = context.getColor(R.color.brand)
					revealBackgroundColor = context.getColor(R.color.medium_gray)

					if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_MUTE) {
						if (isMuted) {
							swipeMessage = context.getString(R.string.SwipeUnmute.also { swipeMessageStringId = it })
							translationDrawable = Theme.dialogs_swipeUnmuteDrawable
						}
						else {
							swipeMessage = context.getString(R.string.SwipeMute.also { swipeMessageStringId = it })
							translationDrawable = Theme.dialogs_swipeMuteDrawable
						}
					}
					else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_DELETE) {
						swipeMessage = context.getString(R.string.SwipeDeleteChat.also { swipeMessageStringId = it })
						backgroundColor = Theme.getColor(Theme.key_dialogSwipeRemove)
						translationDrawable = Theme.dialogs_swipeDeleteDrawable
					}
					else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_READ) {
						if (unreadCount > 0 || markUnread) {
							swipeMessage = context.getString(R.string.SwipeMarkAsRead.also { swipeMessageStringId = it })
							translationDrawable = Theme.dialogs_swipeReadDrawable
						}
						else {
							swipeMessage = context.getString(R.string.SwipeMarkAsUnread.also { swipeMessageStringId = it })
							translationDrawable = Theme.dialogs_swipeUnreadDrawable
						}
					}
					else if (SharedConfig.getChatSwipeAction(currentAccount) == SwipeGestureSettingsView.SWIPE_GESTURE_PIN) {
						if (isPinned) {
							swipeMessage = context.getString(R.string.SwipeUnpin.also { swipeMessageStringId = it })
							translationDrawable = Theme.dialogs_swipeUnpinDrawable
						}
						else {
							swipeMessage = context.getString(R.string.SwipePin.also { swipeMessageStringId = it })
							translationDrawable = Theme.dialogs_swipePinDrawable
						}
					}
					else {
						swipeMessage = context.getString(R.string.Archive.also { swipeMessageStringId = it })
						translationDrawable = Theme.dialogs_archiveDrawable
					}
				}
				else {
					backgroundColor = context.getColor(R.color.medium_gray)
					revealBackgroundColor = context.getColor(R.color.brand)
					swipeMessage = context.getString(R.string.Unarchive.also { swipeMessageStringId = it })
					translationDrawable = Theme.dialogs_unarchiveDrawable
				}
			}

			if (swipeCanceled && lastDrawTranslationDrawable != null) {
				translationDrawable = lastDrawTranslationDrawable
				swipeMessageStringId = lastDrawSwipeMessageStringId
			}
			else {
				lastDrawTranslationDrawable = translationDrawable
				lastDrawSwipeMessageStringId = swipeMessageStringId
			}

			if (!translationAnimationStarted && abs(translationX) > AndroidUtilities.dp(43f)) {
				translationAnimationStarted = true
				translationDrawable?.setProgress(0.0f)
				translationDrawable?.callback = this
				translationDrawable?.start()
			}

			val tx = measuredWidth + translationX

			if (currentRevealProgress < 1.0f) {
				Theme.dialogs_pinnedPaint.color = backgroundColor

				canvas.drawRect(tx - AndroidUtilities.dp(8f), 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), Theme.dialogs_pinnedPaint)
				if (currentRevealProgress == 0f) {

					if (Theme.dialogs_archiveDrawableRecolored) {
						Theme.dialogs_archiveDrawable.setLayerColor("Arrow.**", context.getColor(R.color.brand))
						Theme.dialogs_archiveDrawableRecolored = false
					}
					if (Theme.dialogs_hidePsaDrawableRecolored) {
						Theme.dialogs_hidePsaDrawable.beginApplyLayerColors()
						Theme.dialogs_hidePsaDrawable.setLayerColor("Line 1.**", context.getColor(R.color.brand))
						Theme.dialogs_hidePsaDrawable.setLayerColor("Line 2.**", context.getColor(R.color.brand))
						Theme.dialogs_hidePsaDrawable.setLayerColor("Line 3.**", context.getColor(R.color.brand))
						Theme.dialogs_hidePsaDrawable.commitApplyLayerColors()
						Theme.dialogs_hidePsaDrawableRecolored = false
					}
				}
			}

			val drawableX = measuredWidth - AndroidUtilities.dp(43f) - translationDrawable!!.intrinsicWidth / 2
			val drawableY = AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 12 else 9).toFloat())
			val drawableCx = drawableX + translationDrawable!!.intrinsicWidth / 2
			val drawableCy = drawableY + translationDrawable!!.intrinsicHeight / 2

			if (currentRevealProgress > 0.0f) {
				canvas.save()
				canvas.clipRect(tx - AndroidUtilities.dp(8f), 0f, measuredWidth.toFloat(), measuredHeight.toFloat())

				Theme.dialogs_pinnedPaint.color = revealBackgroundColor

				val rad = sqrt((drawableCx * drawableCx + (drawableCy - measuredHeight) * (drawableCy - measuredHeight)).toDouble()).toFloat()

				canvas.drawCircle(drawableCx.toFloat(), drawableCy.toFloat(), rad * AndroidUtilities.accelerateInterpolator.getInterpolation(currentRevealProgress), Theme.dialogs_pinnedPaint)
				canvas.restore()

				if (!Theme.dialogs_archiveDrawableRecolored) {
					Theme.dialogs_archiveDrawable.setLayerColor("Arrow.**", context.getColor(R.color.medium_gray))
					Theme.dialogs_archiveDrawableRecolored = true
				}
				if (!Theme.dialogs_hidePsaDrawableRecolored) {
					Theme.dialogs_hidePsaDrawable.beginApplyLayerColors()
					Theme.dialogs_hidePsaDrawable.setLayerColor("Line 1.**", context.getColor(R.color.medium_gray))
					Theme.dialogs_hidePsaDrawable.setLayerColor("Line 2.**", context.getColor(R.color.medium_gray))
					Theme.dialogs_hidePsaDrawable.setLayerColor("Line 3.**", context.getColor(R.color.medium_gray))
					Theme.dialogs_hidePsaDrawable.commitApplyLayerColors()
					Theme.dialogs_hidePsaDrawableRecolored = true
				}
			}

			canvas.save()
			canvas.translate(drawableX.toFloat(), drawableY.toFloat())

			if (currentRevealBounceProgress != 0.0f && currentRevealBounceProgress != 1.0f) {
				val scale = 1.0f + interpolator.getInterpolation(currentRevealBounceProgress)
				canvas.scale(scale, scale, (translationDrawable!!.intrinsicWidth / 2).toFloat(), (translationDrawable!!.intrinsicHeight / 2).toFloat())
			}

			setDrawableBounds(translationDrawable, 0, 0)

			translationDrawable?.draw(canvas)

			canvas.restore()
			canvas.clipRect(tx, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())

			val width = ceil(Theme.dialogs_countTextPaint.measureText(swipeMessage).toDouble()).toInt()

			if (swipeMessageTextId != swipeMessageStringId || swipeMessageWidth != measuredWidth) {
				swipeMessageTextId = swipeMessageStringId
				swipeMessageWidth = measuredWidth

				swipeMessageTextLayout = StaticLayout(swipeMessage, Theme.dialogs_archiveTextPaint, min(AndroidUtilities.dp(80f), width), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

				if (swipeMessageTextLayout!!.lineCount > 1) {
					swipeMessageTextLayout = StaticLayout(swipeMessage, Theme.dialogs_archiveTextPaintSmall, min(AndroidUtilities.dp(82f), width), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
				}
			}

			if (swipeMessageTextLayout != null) {
				canvas.save()

				val yOffset = (if (swipeMessageTextLayout!!.lineCount > 1) -AndroidUtilities.dp(4f) else 0).toFloat()

				canvas.translate(measuredWidth - AndroidUtilities.dp(43f) - swipeMessageTextLayout!!.width / 2f, AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 50 else 47).toFloat()) + yOffset)

				swipeMessageTextLayout!!.draw(canvas)

				canvas.restore()
			}

			canvas.restore()
		}
		else if (translationDrawable != null) {
			translationDrawable?.stop()
			translationDrawable?.setProgress(0.0f)
			translationDrawable?.callback = null
			translationDrawable = null
			translationAnimationStarted = false
		}

		if (translationX != 0f) {
			canvas.save()
			canvas.translate(translationX, 0f)
		}

		val cornersRadius = AndroidUtilities.dp(8f) * cornerProgress

		if (isSelected) {
			rect.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
			canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_tabletSeletedPaint)
		}

		if (currentDialogFolderId != 0 && (!SharedConfig.archiveHidden || archiveBackgroundProgress != 0f)) {
			Theme.dialogs_pinnedPaint.color = AndroidUtilities.getOffsetColor(0, context.getColor(R.color.light_background), archiveBackgroundProgress, 1.0f)
			canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), Theme.dialogs_pinnedPaint)
		}
		else if (isPinned || drawPinBackground) {
			Theme.dialogs_pinnedPaint.color = context.getColor(R.color.light_background)
			canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), Theme.dialogs_pinnedPaint)
		}

		if (translationX != 0f || cornerProgress != 0.0f) {
			canvas.save()

			Theme.dialogs_pinnedPaint.color = context.getColor(R.color.background)

			rect.set((measuredWidth - AndroidUtilities.dp(64f)).toFloat(), 0f, measuredWidth.toFloat(), measuredHeight.toFloat())

			canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_pinnedPaint)

			if (isSelected) {
				canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_tabletSeletedPaint)
			}

			if (currentDialogFolderId != 0 && (!SharedConfig.archiveHidden || archiveBackgroundProgress != 0f)) {
				Theme.dialogs_pinnedPaint.color = AndroidUtilities.getOffsetColor(0, context.getColor(R.color.light_background), archiveBackgroundProgress, 1.0f)
				canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_pinnedPaint)
			}
			else if (isPinned || drawPinBackground) {
				Theme.dialogs_pinnedPaint.color = context.getColor(R.color.light_background)
				canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_pinnedPaint)
			}

			canvas.restore()
		}

		if (translationX != 0f) {
			if (cornerProgress < 1.0f) {
				cornerProgress += dt / 150.0f

				if (cornerProgress > 1.0f) {
					cornerProgress = 1.0f
				}

				needInvalidate = true
			}
		}
		else if (cornerProgress > 0.0f) {
			cornerProgress -= dt / 150.0f

			if (cornerProgress < 0.0f) {
				cornerProgress = 0.0f
			}

			needInvalidate = true
		}

		if (drawNameLock) {
			setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop)
			Theme.dialogs_lockDrawable.draw(canvas)
		}

		if (nameLayout != null) {
			if (nameLayoutEllipsizeByGradient && !nameLayoutFits) {
				if (nameLayoutEllipsizeLeft && fadePaint == null) {
					fadePaint = Paint()
					fadePaint!!.shader = LinearGradient(0f, 0f, AndroidUtilities.dp(24f).toFloat(), 0f, intArrayOf(-0x1, 0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
					fadePaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
				}
				else if (fadePaintBack == null) {
					fadePaintBack = Paint()
					fadePaintBack!!.shader = LinearGradient(0f, 0f, AndroidUtilities.dp(24f).toFloat(), 0f, intArrayOf(0, -0x1), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
					fadePaintBack!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
				}

				canvas.saveLayerAlpha(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), 255)

				canvas.clipRect(nameLeft, 0, nameLeft + nameWidth, measuredHeight)
			}

			if (currentDialogFolderId != 0) {
				Theme.dialogs_namePaint[paintIndex].color = context.getColor(R.color.text).also { Theme.dialogs_namePaint[paintIndex].linkColor = it }
			}
			else if (encryptedChat != null || customDialog != null && customDialog!!.type == 2) {
				Theme.dialogs_namePaint[paintIndex].color = context.getColor(R.color.online).also { Theme.dialogs_namePaint[paintIndex].linkColor = it }
			}
			else {
				Theme.dialogs_namePaint[paintIndex].color = context.getColor(R.color.text).also { Theme.dialogs_namePaint[paintIndex].linkColor = it }
			}

			canvas.save()
			canvas.translate(nameLeft + nameLayoutTranslateX, AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 1 else 15).toFloat()).toFloat())

			nameLayout?.draw(canvas)

			canvas.restore()

			if (nameLayoutEllipsizeByGradient && !nameLayoutFits) {
				canvas.save()

				if (nameLayoutEllipsizeLeft) {
					canvas.translate(nameLeft.toFloat(), 0f)
					canvas.drawRect(0f, 0f, AndroidUtilities.dp(24f).toFloat(), measuredHeight.toFloat(), fadePaint!!)
				}
				else {
					canvas.translate((nameLeft + nameWidth - AndroidUtilities.dp(24f)).toFloat(), 0f)
					canvas.drawRect(0f, 0f, AndroidUtilities.dp(24f).toFloat(), measuredHeight.toFloat(), fadePaintBack!!)
				}

				canvas.restore()
				canvas.restore()
			}
		}

		if (timeLayout != null && currentDialogFolderId == 0) {
			canvas.save()
			canvas.translate(timeLeft.toFloat(), timeTop.toFloat())
			timeLayout?.draw(canvas)
			canvas.restore()
		}

		if (messageNameLayout != null) {
			if (currentDialogFolderId != 0) {
				Theme.dialogs_messageNamePaint.color = context.getColor(R.color.text).also { Theme.dialogs_messageNamePaint.linkColor = it }
			}
			else if (draftMessage != null) {
				Theme.dialogs_messageNamePaint.color = context.getColor(R.color.purple).also { Theme.dialogs_messageNamePaint.linkColor = it }
			}
			else {
				Theme.dialogs_messageNamePaint.color = context.getColor(R.color.text).also { Theme.dialogs_messageNamePaint.linkColor = it }
			}

			canvas.save()
			canvas.translate(messageNameLeft.toFloat(), messageNameTop.toFloat())

			try {
				messageNameLayout?.draw(canvas)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			canvas.restore()
		}

		if (messageLayout != null) {
			if (currentDialogFolderId != 0) {
				if (chat != null) {
					Theme.dialogs_messagePaint[paintIndex].color = context.getColor(R.color.dark_gray).also { Theme.dialogs_messagePaint[paintIndex].linkColor = it }
				}
				else {
					Theme.dialogs_messagePaint[paintIndex].color = context.getColor(R.color.dark_gray).also { Theme.dialogs_messagePaint[paintIndex].linkColor = it }
				}
			}
			else {
				Theme.dialogs_messagePaint[paintIndex].color = context.getColor(R.color.dark_gray).also { Theme.dialogs_messagePaint[paintIndex].linkColor = it }
			}

			canvas.save()
			canvas.translate(messageLeft.toFloat(), messageTop.toFloat())

			if (spoilers.isNotEmpty()) {
				try {
					canvas.save()
					SpoilerEffect.clipOutCanvas(canvas, spoilers)
					messageLayout?.draw(canvas)
					AnimatedEmojiSpan.drawAnimatedEmojis(canvas, messageLayout, animatedEmojiStack, -.075f, spoilers, 0f, 0f, 0f, 1f)

					canvas.restore()

					for (i in spoilers.indices) {
						val eff = spoilers[i]
						eff.setColor(messageLayout!!.paint.color)
						eff.draw(canvas)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
			else {
				messageLayout?.draw(canvas)
				AnimatedEmojiSpan.drawAnimatedEmojis(canvas, messageLayout, animatedEmojiStack, -.075f, null, 0f, 0f, 0f, 1f)
			}

			canvas.restore()

			if (printingStringType >= 0) {
				val statusDrawable = Theme.getChatStatusDrawable(printingStringType)

				if (statusDrawable != null) {
					canvas.save()

					if (printingStringType == 1 || printingStringType == 4) {
						canvas.translate(statusDrawableLeft.toFloat(), (messageTop + if (printingStringType == 1) AndroidUtilities.dp(1f) else 0).toFloat())
					}
					else {
						canvas.translate(statusDrawableLeft.toFloat(), messageTop + (AndroidUtilities.dp(18f) - statusDrawable.intrinsicHeight) / 2f)
					}

					statusDrawable.draw(canvas)

					invalidate()

					canvas.restore()
				}
			}
		}

		if (currentDialogFolderId == 0) {
			var currentStatus = (if (drawClock) 1 else 0) + (if (drawCheck1) 2 else 0) + if (drawCheck2) 4 else 0

			if (lastStatusDrawableParams >= 0 && lastStatusDrawableParams != currentStatus && !statusDrawableAnimationInProgress) {
				createStatusDrawableAnimator(lastStatusDrawableParams, currentStatus)
			}

			if (statusDrawableAnimationInProgress) {
				currentStatus = animateToStatusDrawableParams
			}

			val drawClock = currentStatus and 1 != 0
			val drawCheck1 = currentStatus and 2 != 0
			val drawCheck2 = currentStatus and 4 != 0

			if (statusDrawableAnimationInProgress) {
				val outDrawClock = animateFromStatusDrawableParams and 1 != 0
				val outDrawCheck1 = animateFromStatusDrawableParams and 2 != 0
				val outDrawCheck2 = animateFromStatusDrawableParams and 4 != 0

				if (!drawClock && !outDrawClock && outDrawCheck2 && !outDrawCheck1 && drawCheck1 && drawCheck2) {
					drawCheckStatus(canvas, drawClock = false, drawCheck1 = true, drawCheck2 = true, moveCheck = true, alpha = statusDrawableProgress)
				}
				else {
					drawCheckStatus(canvas, outDrawClock, outDrawCheck1, outDrawCheck2, false, 1f - statusDrawableProgress)
					drawCheckStatus(canvas, drawClock, drawCheck1, drawCheck2, false, statusDrawableProgress)
				}
			}
			else {
				drawCheckStatus(canvas, drawClock, drawCheck1, drawCheck2, false, 1f)
			}

			lastStatusDrawableParams = (if (this.drawClock) 1 else 0) + (if (this.drawCheck1) 2 else 0) + if (this.drawCheck2) 4 else 0
		}

		if (dialogsType != 2 && (isMuted || dialogMutedProgress > 0) && !drawVerified && drawScam == 0 && !drawPremium) {
			val muteIcon = ResourcesCompat.getDrawable(context!!.resources, R.drawable.volume_slash, null)

			if (isMuted && dialogMutedProgress != 1f) {
				dialogMutedProgress += 16 / 150f

				if (dialogMutedProgress > 1f) {
					dialogMutedProgress = 1f
				}
				else {
					invalidate()
				}
			}
			else if (!isMuted && dialogMutedProgress != 0f) {
				dialogMutedProgress -= 16 / 150f

				if (dialogMutedProgress < 0f) {
					dialogMutedProgress = 0f
				}
				else {
					invalidate()
				}
			}

			setDrawableBounds(muteIcon, nameMuteLeft - AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 0 else 1).toFloat()), AndroidUtilities.dp(if (SharedConfig.useThreeLinesLayout) 14f else 19f))

			if (dialogMutedProgress != 1f) {
				canvas.save()
				canvas.scale(dialogMutedProgress, dialogMutedProgress, muteIcon!!.bounds.centerX().toFloat(), muteIcon.bounds.centerY().toFloat())

				muteIcon.alpha = (255 * dialogMutedProgress).toInt()
				muteIcon.draw(canvas)
				muteIcon.alpha = 255

				canvas.restore()
			}
			else {
				muteIcon?.draw(canvas)
			}
		}
		else if (drawVerified) {
			val drawables = mutableListOf<Drawable>()

			ResourcesCompat.getDrawable(resources, R.drawable.verified_icon, null)?.let { drawables.add(it) }

			if (isMuted) {
				ResourcesCompat.getDrawable(context!!.resources, R.drawable.volume_slash, null)?.let { drawables.add(it) }
			}

			val verifiedMuteIcons = combineDrawables(AndroidUtilities.dp(11f), drawables)

			if (isLastCharUpperCaseOrDigit(user, chat)) {
				setDrawableBounds(verifiedMuteIcons, nameMuteLeft - AndroidUtilities.dp(2f), AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 12.5f else Gravity.CENTER.toFloat() + 2.1f))
			} else {
				setDrawableBounds(verifiedMuteIcons, nameMuteLeft - AndroidUtilities.dp(2f), AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 12.5f else Gravity.CENTER.toFloat() + 2.3f))
			}

			verifiedMuteIcons.draw(canvas)
		}
		else if (drawPremium) {
			if (emojiStatus.drawable != null) {
				emojiStatus.setBounds(nameMuteLeft - AndroidUtilities.dp(2f), AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 12.5f else 15.5f) - AndroidUtilities.dp(4f), nameMuteLeft + AndroidUtilities.dp(20f), AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 12.5f else 15.5f) - AndroidUtilities.dp(4f) + AndroidUtilities.dp(22f))
				emojiStatus.color = context.getColor(R.color.brand)
				emojiStatus.draw(canvas)
			}
			else {
				val premiumDrawable = PremiumGradient.getInstance().premiumStarDrawableMini
				setDrawableBounds(premiumDrawable, nameMuteLeft - AndroidUtilities.dp(1f), AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 12.5f else 15.5f))
				premiumDrawable.draw(canvas)
			}
		}
		else if (drawScam != 0) {
			setDrawableBounds(if (drawScam == 1) Theme.dialogs_scamDrawable else Theme.dialogs_fakeDrawable, nameMuteLeft, AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 12 else 15).toFloat()))
			(if (drawScam == 1) Theme.dialogs_scamDrawable else Theme.dialogs_fakeDrawable).draw(canvas)
		}

		if (drawPrivate) {
			val privateDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.lock_ello, null)?.mutate()
			setDrawableBounds(privateDrawable, privateLeft, AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 5.5f else 16.5f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
			privateDrawable?.draw(canvas)
		}

		if (drawAdult) {
			val adultDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.adult_channel_icon, null)?.mutate()
			setDrawableBounds(adultDrawable, adultLeft, AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 5.5f else 16.5f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
			adultDrawable?.draw(canvas)
		}

		if (drawPaid) {
			val paidDrawable = if (ChatObject.isOnlineCourse(chat)) {
				ResourcesCompat.getDrawable(context.resources, R.drawable.online_course, null)?.mutate()
			}
			else {
				ResourcesCompat.getDrawable(context.resources, R.drawable.ic_paid_channel, null)?.mutate()
			}

			setDrawableBounds(paidDrawable, paidLeft, AndroidUtilities.dp(if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 5.5f else 16.5f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
			paidDrawable?.draw(canvas)
		}

		if (drawReorder || reorderIconProgress != 0f) {
			Theme.dialogs_reorderDrawable.alpha = (reorderIconProgress * 255).toInt()
			setDrawableBounds(Theme.dialogs_reorderDrawable, pinLeft, pinTop)
			Theme.dialogs_reorderDrawable.draw(canvas)
		}

		if (drawError) {
			Theme.dialogs_errorDrawable.alpha = ((1.0f - reorderIconProgress) * 255).toInt()
			rect.set(errorLeft.toFloat(), errorTop.toFloat(), (errorLeft + AndroidUtilities.dp(23f)).toFloat(), (errorTop + AndroidUtilities.dp(23f)).toFloat())
			canvas.drawRoundRect(rect, COUNTER_CORNER_RADIUS * AndroidUtilities.density, COUNTER_CORNER_RADIUS * AndroidUtilities.density, Theme.dialogs_errorPaint)
			setDrawableBounds(Theme.dialogs_errorDrawable, errorLeft + AndroidUtilities.dp(5.5f), errorTop + AndroidUtilities.dp(5f))
			Theme.dialogs_errorDrawable.draw(canvas)
		}
		else if ((drawCount || drawMention) && drawCount2 || countChangeProgress != 1f || drawReactionMention || reactionsMentionsChangeProgress != 1f) {
			if (drawCount && drawCount2 || countChangeProgress != 1f) {
				val progressFinal = if (unreadCount == 0 && !markUnread) 1f - countChangeProgress else countChangeProgress

				if (countOldLayout == null || unreadCount == 0) {
					val drawLayout = if (unreadCount == 0) countOldLayout else countLayout

					val paint = if (isMuted || currentDialogFolderId != 0) Theme.dialogs_countGrayPaint else Theme.dialogs_countPaint
					paint.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

					Theme.dialogs_countTextPaint.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

					val x = countLeft - AndroidUtilities.dp(5.5f)

					rect.set(x.toFloat(), countTop.toFloat(), (x + countWidth + AndroidUtilities.dp(11f)).toFloat(), (countTop + AndroidUtilities.dp(23f)).toFloat())

					if (progressFinal != 1f) {
						if (isPinned) {
							Theme.dialogs_pinnedDrawable.colorFilter = pinIconColorFilter
							Theme.dialogs_pinnedDrawable.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

							setDrawableBounds(Theme.dialogs_pinnedDrawable, pinLeft, pinTop)

							canvas.save()
							canvas.scale(1f - progressFinal, 1f - progressFinal, Theme.dialogs_pinnedDrawable.bounds.centerX().toFloat(), Theme.dialogs_pinnedDrawable.bounds.centerY().toFloat())

							Theme.dialogs_pinnedDrawable.draw(canvas)

							canvas.restore()
						}

						canvas.save()
						canvas.scale(progressFinal, progressFinal, rect.centerX(), rect.centerY())
					}

					canvas.drawRoundRect(rect, COUNTER_CORNER_RADIUS * AndroidUtilities.density, COUNTER_CORNER_RADIUS * AndroidUtilities.density, paint)

					if (drawLayout != null) {
						canvas.save()
						canvas.translate(countLeft.toFloat(), (countTop + AndroidUtilities.dp(4f)).toFloat())
						drawLayout.draw(canvas)
						canvas.restore()
					}

					if (progressFinal != 1f) {
						canvas.restore()
					}
				}
				else {
					val paint = if (isMuted || currentDialogFolderId != 0) Theme.dialogs_countGrayPaint else Theme.dialogs_countPaint
					paint.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

					Theme.dialogs_countTextPaint.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

					var progressHalf = progressFinal * 2

					if (progressHalf > 1f) {
						progressHalf = 1f
					}

					val countLeft = countLeft * progressHalf + countLeftOld * (1f - progressHalf)
					val x = countLeft - AndroidUtilities.dp(5.5f)

					rect.set(x, countTop.toFloat(), x + countWidth * progressHalf + countWidthOld * (1f - progressHalf) + AndroidUtilities.dp(11f), (countTop + AndroidUtilities.dp(23f)).toFloat())

					var scale = 1f

					scale += if (progressFinal <= 0.5f) {
						0.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(progressFinal * 2)
					}
					else {
						0.1f * CubicBezierInterpolator.EASE_IN.getInterpolation(1f - (progressFinal - 0.5f) * 2)
					}

					canvas.save()
					canvas.scale(scale, scale, rect.centerX(), rect.centerY())
					canvas.drawRoundRect(rect, COUNTER_CORNER_RADIUS * AndroidUtilities.density, COUNTER_CORNER_RADIUS * AndroidUtilities.density, paint)

					if (countAnimationStableLayout != null) {
						canvas.save()
						canvas.translate(countLeft, (countTop + AndroidUtilities.dp(4f)).toFloat())
						countAnimationStableLayout?.draw(canvas)
						canvas.restore()
					}

					val textAlpha = Theme.dialogs_countTextPaint.alpha

					Theme.dialogs_countTextPaint.alpha = (textAlpha * progressHalf).toInt()

					if (countAnimationInLayout != null) {
						canvas.save()
						canvas.translate(countLeft, (if (countAnimationIncrement) AndroidUtilities.dp(13f) else -AndroidUtilities.dp(13f)) * (1f - progressHalf) + countTop + AndroidUtilities.dp(4f))
						countAnimationInLayout?.draw(canvas)
						canvas.restore()
					}
					else if (countLayout != null) {
						canvas.save()
						canvas.translate(countLeft, (if (countAnimationIncrement) AndroidUtilities.dp(13f) else -AndroidUtilities.dp(13f)) * (1f - progressHalf) + countTop + AndroidUtilities.dp(4f))
						countLayout?.draw(canvas)
						canvas.restore()
					}

					if (countOldLayout != null) {
						Theme.dialogs_countTextPaint.alpha = (textAlpha * (1f - progressHalf)).toInt()
						canvas.save()
						canvas.translate(countLeft, (if (countAnimationIncrement) -AndroidUtilities.dp(13f) else AndroidUtilities.dp(13f)) * progressHalf + countTop + AndroidUtilities.dp(4f))
						countOldLayout?.draw(canvas)
						canvas.restore()
					}

					Theme.dialogs_countTextPaint.alpha = textAlpha

					canvas.restore()
				}
			}

			if (drawMention) {
				Theme.dialogs_countPaint.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

				val x = mentionLeft - AndroidUtilities.dp(5.5f)

				rect.set(x.toFloat(), countTop.toFloat(), (x + mentionWidth + AndroidUtilities.dp(11f)).toFloat(), (countTop + AndroidUtilities.dp(23f)).toFloat())

				val paint = if (isMuted && folderId != 0) Theme.dialogs_countGrayPaint else Theme.dialogs_countPaint

				canvas.drawRoundRect(rect, COUNTER_CORNER_RADIUS * AndroidUtilities.density, COUNTER_CORNER_RADIUS * AndroidUtilities.density, paint)

				if (mentionLayout != null) {
					Theme.dialogs_countTextPaint.alpha = ((1.0f - reorderIconProgress) * 255).toInt()
					canvas.save()
					canvas.translate(mentionLeft.toFloat(), (countTop + AndroidUtilities.dp(4f)).toFloat())
					mentionLayout?.draw(canvas)
					canvas.restore()
				}
				else {
					Theme.dialogs_mentionDrawable.alpha = ((1.0f - reorderIconProgress) * 255).toInt()
					setDrawableBounds(Theme.dialogs_mentionDrawable, mentionLeft - AndroidUtilities.dp(2f), countTop + AndroidUtilities.dp(3.2f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
					Theme.dialogs_mentionDrawable.draw(canvas)
				}
			}

			if (drawReactionMention || reactionsMentionsChangeProgress != 1f) {
				Theme.dialogs_reactionsCountPaint.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

				val x = reactionMentionLeft - AndroidUtilities.dp(5.5f)

				rect.set(x.toFloat(), countTop.toFloat(), (x + AndroidUtilities.dp(23f)).toFloat(), (countTop + AndroidUtilities.dp(23f)).toFloat())

				val paint = Theme.dialogs_reactionsCountPaint

				canvas.save()

				if (reactionsMentionsChangeProgress != 1f) {
					val s = if (drawReactionMention) reactionsMentionsChangeProgress else 1f - reactionsMentionsChangeProgress
					canvas.scale(s, s, rect.centerX(), rect.centerY())
				}

				canvas.drawRoundRect(rect, COUNTER_CORNER_RADIUS * AndroidUtilities.density, COUNTER_CORNER_RADIUS * AndroidUtilities.density, paint)

				Theme.dialogs_reactionsMentionDrawable.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

				setDrawableBounds(Theme.dialogs_reactionsMentionDrawable, reactionMentionLeft - AndroidUtilities.dp(2f), countTop + AndroidUtilities.dp(3.8f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))

				Theme.dialogs_reactionsMentionDrawable.draw(canvas)

				canvas.restore()
			}
		}
		else if (isPinned) {
			Theme.dialogs_pinnedDrawable.colorFilter = pinIconColorFilter
			Theme.dialogs_pinnedDrawable.alpha = ((1.0f - reorderIconProgress) * 255).toInt()

			setDrawableBounds(Theme.dialogs_pinnedDrawable, pinLeft, pinTop)

			Theme.dialogs_pinnedDrawable.draw(canvas)
		}

		if (animatingArchiveAvatar) {
			canvas.save()

			val scale = 1.0f + interpolator.getInterpolation(animatingArchiveAvatarProgress / 170.0f)

			canvas.scale(scale, scale, avatarImage.centerX, avatarImage.centerY)
		}

		if (currentDialogFolderId == 0 || archivedChatsDrawable == null || !archivedChatsDrawable!!.isDraw) {
			avatarImage.draw(canvas)
		}

		if (hasMessageThumb) {
			thumbImage.draw(canvas)

			if (drawPlay) {
				val x = (thumbImage.centerX - Theme.dialogs_playDrawable.intrinsicWidth / 2).toInt()
				val y = (thumbImage.centerY - Theme.dialogs_playDrawable.intrinsicHeight / 2).toInt()

				setDrawableBounds(Theme.dialogs_playDrawable, x, y)

				Theme.dialogs_playDrawable.draw(canvas)
			}
		}

		if (animatingArchiveAvatar) {
			canvas.restore()
		}

		if (isDialogCell && currentDialogFolderId == 0) {
			if (user != null && !MessagesController.isSupportUser(user) && !user!!.bot) {
				val isOnline = isOnline

				if (isOnline || onlineProgress != 0f) {
					var side = AndroidUtilities.dp(14f).toFloat()
					var top = avatarImage.imageY2 - side
					var left = avatarImage.imageX2 - side
					var width = side * onlineProgress
					var radius = AndroidUtilities.dp(6f).toFloat()

					Theme.dialogs_onlineCirclePaint.color = context.getColor(R.color.background)

					// canvas.drawCircle(left, top, AndroidUtilities.dp(7) * onlineProgress, Theme.dialogs_onlineCirclePaint);

					canvas.drawRoundRect(left, top, left + width, top + width, radius, radius, Theme.dialogs_onlineCirclePaint)

					left += AndroidUtilities.dp(1.5f).toFloat()
					top += AndroidUtilities.dp(1.5f).toFloat()
					side = AndroidUtilities.dp(11f).toFloat()
					width = side * onlineProgress
					radius = AndroidUtilities.dp(5f).toFloat()

					Theme.dialogs_onlineCirclePaint.color = context.getColor(R.color.online)

					//canvas.drawCircle(left, top, AndroidUtilities.dp(5) * onlineProgress, Theme.dialogs_onlineCirclePaint);

					canvas.drawRoundRect(left, top, left + width, top + width, radius, radius, Theme.dialogs_onlineCirclePaint)

					if (isOnline) {
						if (onlineProgress < 1.0f) {
							onlineProgress += dt / 150.0f

							if (onlineProgress > 1.0f) {
								onlineProgress = 1.0f
							}

							needInvalidate = true
						}
					}
					else {
						if (onlineProgress > 0.0f) {
							onlineProgress -= dt / 150.0f

							if (onlineProgress < 0.0f) {
								onlineProgress = 0.0f
							}

							needInvalidate = true
						}
					}
				}
			}
			else if (chat != null) {
				hasCall = chat!!.call_active && chat!!.call_not_empty

				if (hasCall || chatCallProgress != 0f) {
					val checkProgress = if (checkBox?.isChecked == true) 1.0f - (checkBox?.progress ?: 0f) else 1.0f
					val top = (avatarImage.imageY2 - AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 6 else 8).toFloat())).toInt()

					val left = if (LocaleController.isRTL) {
						(avatarImage.imageX + AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 10 else 6).toFloat())).toInt()
					}
					else {
						(avatarImage.imageX2 - AndroidUtilities.dp((if (useForceThreeLines || SharedConfig.useThreeLinesLayout) 10 else 6).toFloat())).toInt()
					}

					Theme.dialogs_onlineCirclePaint.color = context.getColor(R.color.background)

					canvas.drawCircle(left.toFloat(), top.toFloat(), AndroidUtilities.dp(11f) * chatCallProgress * checkProgress, Theme.dialogs_onlineCirclePaint)

					Theme.dialogs_onlineCirclePaint.color = context.getColor(R.color.online)

					canvas.drawCircle(left.toFloat(), top.toFloat(), AndroidUtilities.dp(9f) * chatCallProgress * checkProgress, Theme.dialogs_onlineCirclePaint)

					Theme.dialogs_onlineCirclePaint.color = context.getColor(R.color.background)

					val size1: Float
					val size2: Float

					when (progressStage) {
						0 -> {
							size1 = AndroidUtilities.dp(1f) + AndroidUtilities.dp(4f) * innerProgress
							size2 = AndroidUtilities.dp(3f) - AndroidUtilities.dp(2f) * innerProgress
						}

						1 -> {
							size1 = AndroidUtilities.dp(5f) - AndroidUtilities.dp(4f) * innerProgress
							size2 = AndroidUtilities.dp(1f) + AndroidUtilities.dp(4f) * innerProgress
						}

						2 -> {
							size1 = AndroidUtilities.dp(1f) + AndroidUtilities.dp(2f) * innerProgress
							size2 = AndroidUtilities.dp(5f) - AndroidUtilities.dp(4f) * innerProgress
						}

						3 -> {
							size1 = AndroidUtilities.dp(3f) - AndroidUtilities.dp(2f) * innerProgress
							size2 = AndroidUtilities.dp(1f) + AndroidUtilities.dp(2f) * innerProgress
						}

						4 -> {
							size1 = AndroidUtilities.dp(1f) + AndroidUtilities.dp(4f) * innerProgress
							size2 = AndroidUtilities.dp(3f) - AndroidUtilities.dp(2f) * innerProgress
						}

						5 -> {
							size1 = AndroidUtilities.dp(5f) - AndroidUtilities.dp(4f) * innerProgress
							size2 = AndroidUtilities.dp(1f) + AndroidUtilities.dp(4f) * innerProgress
						}

						6 -> {
							size1 = AndroidUtilities.dp(1f) + AndroidUtilities.dp(4f) * innerProgress
							size2 = AndroidUtilities.dp(5f) - AndroidUtilities.dp(4f) * innerProgress
						}

						else -> {
							size1 = AndroidUtilities.dp(5f) - AndroidUtilities.dp(4f) * innerProgress
							size2 = AndroidUtilities.dp(1f) + AndroidUtilities.dp(2f) * innerProgress
						}
					}

					if (chatCallProgress < 1.0f || checkProgress < 1.0f) {
						canvas.save()
						canvas.scale(chatCallProgress * checkProgress, chatCallProgress * checkProgress, left.toFloat(), top.toFloat())
					}

					rect.set((left - AndroidUtilities.dp(1f)).toFloat(), top - size1, (left + AndroidUtilities.dp(1f)).toFloat(), top + size1)

					canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), Theme.dialogs_onlineCirclePaint)

					rect.set((left - AndroidUtilities.dp(5f)).toFloat(), top - size2, (left - AndroidUtilities.dp(3f)).toFloat(), top + size2)

					canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), Theme.dialogs_onlineCirclePaint)

					rect.set((left + AndroidUtilities.dp(3f)).toFloat(), top - size2, (left + AndroidUtilities.dp(5f)).toFloat(), top + size2)

					canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), Theme.dialogs_onlineCirclePaint)

					if (chatCallProgress < 1.0f || checkProgress < 1.0f) {
						canvas.restore()
					}

					innerProgress += dt / 400.0f

					if (innerProgress >= 1.0f) {
						innerProgress = 0.0f
						progressStage++

						if (progressStage >= 8) {
							progressStage = 0
						}
					}

					needInvalidate = true

					if (hasCall) {
						if (chatCallProgress < 1.0f) {
							chatCallProgress += dt / 150.0f

							if (chatCallProgress > 1.0f) {
								chatCallProgress = 1.0f
							}
						}
					}
					else {
						if (chatCallProgress > 0.0f) {
							chatCallProgress -= dt / 150.0f

							if (chatCallProgress < 0.0f) {
								chatCallProgress = 0.0f
							}
						}
					}
				}
			}
		}

		if (translationX != 0f) {
			canvas.restore()
		}

		if (currentDialogFolderId != 0 && translationX == 0f && archivedChatsDrawable != null) {
			canvas.save()
			canvas.clipRect(0, 0, measuredWidth, measuredHeight)
			archivedChatsDrawable?.draw(canvas)
			canvas.restore()
		}

		if (useSeparator) {
			val left = if (fullSeparator || currentDialogFolderId != 0 && archiveHidden && !fullSeparator2 || fullSeparator2 && !archiveHidden) {
				0
			}
			else {
				AndroidUtilities.dp(81f)
			}

			if (LocaleController.isRTL) {
				canvas.drawLine(0f, (measuredHeight - 1).toFloat(), (measuredWidth - left).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
			else {
				canvas.drawLine(left.toFloat(), (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
		}

		if (clipProgress != 0.0f) {
			if (Build.VERSION.SDK_INT != 24) {
				canvas.restore()
			}
			else {
				Theme.dialogs_pinnedPaint.color = context.getColor(R.color.background)
				canvas.drawRect(0f, 0f, measuredWidth.toFloat(), topClip * clipProgress, Theme.dialogs_pinnedPaint)
				canvas.drawRect(0f, (measuredHeight - (bottomClip * clipProgress).toInt()).toFloat(), measuredWidth.toFloat(), measuredHeight.toFloat(), Theme.dialogs_pinnedPaint)
			}
		}

		if (drawReorder || reorderIconProgress != 0.0f) {
			if (drawReorder) {
				if (reorderIconProgress < 1.0f) {
					reorderIconProgress += dt / 170.0f

					if (reorderIconProgress > 1.0f) {
						reorderIconProgress = 1.0f
					}

					needInvalidate = true
				}
			}
			else {
				if (reorderIconProgress > 0.0f) {
					reorderIconProgress -= dt / 170.0f

					if (reorderIconProgress < 0.0f) {
						reorderIconProgress = 0.0f
					}

					needInvalidate = true
				}
			}
		}

		if (archiveHidden) {
			if (archiveBackgroundProgress > 0.0f) {
				archiveBackgroundProgress -= dt / 230.0f

				if (archiveBackgroundProgress < 0.0f) {
					archiveBackgroundProgress = 0.0f
				}

				if (avatarDrawable.avatarType == AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
					avatarDrawable.setArchivedAvatarHiddenProgress(CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(archiveBackgroundProgress))
				}

				needInvalidate = true
			}
		}
		else {
			if (archiveBackgroundProgress < 1.0f) {
				archiveBackgroundProgress += dt / 230.0f

				if (archiveBackgroundProgress > 1.0f) {
					archiveBackgroundProgress = 1.0f
				}

				if (avatarDrawable.avatarType == AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
					avatarDrawable.setArchivedAvatarHiddenProgress(CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(archiveBackgroundProgress))
				}

				needInvalidate = true
			}
		}

		if (animatingArchiveAvatar) {
			animatingArchiveAvatarProgress += dt.toFloat()

			if (animatingArchiveAvatarProgress >= 170.0f) {
				animatingArchiveAvatarProgress = 170.0f
				animatingArchiveAvatar = false
			}

			needInvalidate = true
		}

		if (drawRevealBackground) {
			if (currentRevealBounceProgress < 1.0f) {
				currentRevealBounceProgress += dt / 170.0f

				if (currentRevealBounceProgress > 1.0f) {
					currentRevealBounceProgress = 1.0f
					needInvalidate = true
				}
			}

			if (currentRevealProgress < 1.0f) {
				currentRevealProgress += dt / 300.0f

				if (currentRevealProgress > 1.0f) {
					currentRevealProgress = 1.0f
				}

				needInvalidate = true
			}
		}
		else {
			if (currentRevealBounceProgress == 1.0f) {
				currentRevealBounceProgress = 0.0f
				needInvalidate = true
			}

			if (currentRevealProgress > 0.0f) {
				currentRevealProgress -= dt / 300.0f

				if (currentRevealProgress < 0.0f) {
					currentRevealProgress = 0.0f
				}

				needInvalidate = true
			}
		}

		if (needInvalidate) {
			invalidate()
		}
	}

	private fun isUpperCaseOrDigit(c: Char): Boolean {
		return c.isUpperCase() || c.isDigit()
	}

	private fun isLastCharUpperCaseOrDigit(user: User?, chat: Chat?): Boolean {
		val name = user?.last_name ?: user?.first_name
		val channelName = chat?.title

		if (!name.isNullOrEmpty()) {
			val lastChar = name.last()

			if (lastChar.isLowerCase()) {
				return false
			}

			return isUpperCaseOrDigit(lastChar)
		}

		if (!channelName.isNullOrEmpty()) {
			val lastChar = channelName.last()

			if (lastChar.isLowerCase()) {
				return false
			}

			return isUpperCaseOrDigit(lastChar)
		}

		return false
	}

	private fun createStatusDrawableAnimator(lastStatusDrawableParams: Int, currentStatus: Int) {
		statusDrawableProgress = 0f

		statusDrawableAnimator = ValueAnimator.ofFloat(0f, 1f)
		statusDrawableAnimator?.duration = 220
		statusDrawableAnimator?.interpolator = CubicBezierInterpolator.DEFAULT

		animateFromStatusDrawableParams = lastStatusDrawableParams
		animateToStatusDrawableParams = currentStatus

		statusDrawableAnimator?.addUpdateListener {
			statusDrawableProgress = it.animatedValue as Float
			invalidate()
		}

		statusDrawableAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				@Suppress("NAME_SHADOWING") val currentStatus = (if (drawClock) 1 else 0) + (if (drawCheck1) 2 else 0) + if (drawCheck2) 4 else 0

				if (animateToStatusDrawableParams != currentStatus) {
					createStatusDrawableAnimator(animateToStatusDrawableParams, currentStatus)
				}
				else {
					statusDrawableAnimationInProgress = false
					this@DialogCell.lastStatusDrawableParams = animateToStatusDrawableParams
				}

				invalidate()
			}
		})

		statusDrawableAnimationInProgress = true

		statusDrawableAnimator?.start()
	}

	fun startOutAnimation() {
		archivedChatsDrawable?.let {
			it.outCy = avatarImage.centerY
			it.outCx = avatarImage.centerX
			it.outRadius = avatarImage.imageWidth / 2.0f
			it.outImageSize = avatarImage.bitmapWidth.toFloat()
			it.startOutAnimation()
		}
	}

	fun onReorderStateChanged(reordering: Boolean, animated: Boolean) {
		if (!isPinned && reordering || drawReorder == reordering) {
			if (!isPinned) {
				drawReorder = false
			}

			return
		}

		drawReorder = reordering

		reorderIconProgress = if (animated) {
			if (drawReorder) 0.0f else 1.0f
		}
		else {
			if (drawReorder) 1.0f else 0.0f
		}

		invalidate()
	}

	fun setSliding(value: Boolean) {
		isSliding = value
	}

	override fun invalidateDrawable(who: Drawable) {
		if (who === translationDrawable || who === Theme.dialogs_archiveAvatarDrawable) {
			invalidate()
		}
		else {
			super.invalidateDrawable(who)
		}
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
		if (action == R.id.acc_action_chat_preview && parentFragment != null) {
			parentFragment.showChatPreview(this)
			return true
		}

		return super.performAccessibilityAction(action, arguments)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		if (isFolderCell && archivedChatsDrawable != null && SharedConfig.archiveHidden && archivedChatsDrawable!!.pullProgress == 0.0f) {
			info.isVisibleToUser = false
		}
		else {
			info.addAction(AccessibilityAction.ACTION_CLICK)
			info.addAction(AccessibilityAction.ACTION_LONG_CLICK)

			if (!isFolderCell && parentFragment != null) {
				info.addAction(AccessibilityAction(R.id.acc_action_chat_preview, context.getString(R.string.AccActionChatPreview)))
			}
		}

		if (checkBox?.isChecked == true) {
			info.className = "android.widget.CheckBox"
			info.isCheckable = true
			info.isChecked = true
		}
	}

	override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
		super.onPopulateAccessibilityEvent(event)

		val encryptedChat = encryptedChat
		val chat = chat
		val user = user

		val sb = StringBuilder()

		if (currentDialogFolderId == 1) {
			sb.append(context.getString(R.string.ArchivedChats))
			sb.append(". ")
		}
		else {
			if (encryptedChat != null) {
				sb.append(context.getString(R.string.AccDescrSecretChat))
				sb.append(". ")
			}
			if (user != null) {
				if (UserObject.isReplyUser(user)) {
					sb.append(context.getString(R.string.RepliesTitle))
				}
				else {
					if (user.bot) {
						sb.append(context.getString(R.string.Bot))
						sb.append(". ")
					}
					if (user.self) {
						sb.append(context.getString(R.string.SavedMessages))
					}
					else {
						sb.append(ContactsController.formatName(user.first_name, user.last_name))
					}
				}

				sb.append(". ")
			}
			else if (chat != null) {
				if (chat.broadcast) {
					sb.append(context.getString(R.string.AccDescrChannel))
				}
				else {
					sb.append(context.getString(R.string.AccDescrGroup))
				}

				sb.append(". ")
				sb.append(chat.title)
				sb.append(". ")
			}
		}

		if (drawVerified) {
			sb.append(context.getString(R.string.AccDescrVerified))
			sb.append(". ")
		}

		if (unreadCount > 0) {
			sb.append(LocaleController.formatPluralString("NewMessages", unreadCount))
			sb.append(". ")
		}

		if (mentionCount > 0) {
			sb.append(LocaleController.formatPluralString("AccDescrMentionCount", mentionCount))
			sb.append(". ")
		}

		if (reactionMentionCount > 0) {
			sb.append(context.getString(R.string.AccDescrMentionReaction))
			sb.append(". ")
		}

		val message = message

		if (message == null || currentDialogFolderId != 0) {
			event.contentDescription = sb.toString()
			return
		}

		var lastDate = lastMessageDate

		if (lastMessageDate == 0) {
			lastDate = message.messageOwner!!.date
		}

		val date = LocaleController.formatDateAudio(lastDate.toLong(), true)

		if (message.isOut) {
			sb.append(LocaleController.formatString("AccDescrSentDate", R.string.AccDescrSentDate, date))
		}
		else {
			sb.append(LocaleController.formatString("AccDescrReceivedDate", R.string.AccDescrReceivedDate, date))
		}

		sb.append(". ")

		if (chat != null && !message.isOut && message.isFromUser && message.messageOwner?.action == null) {
			val fromUser = MessagesController.getInstance(currentAccount).getUser(message.messageOwner?.from_id?.user_id)

			if (fromUser != null) {
				sb.append(ContactsController.formatName(fromUser.first_name, fromUser.last_name))
				sb.append(". ")
			}
		}

		if (encryptedChat == null) {
			val messageString = StringBuilder()

			messageString.append(message.messageText)

			if (!message.isMediaEmpty) {
				if (!message.caption.isNullOrEmpty()) {
					messageString.append(". ")
					messageString.append(message.caption)
				}
			}

			val len = messageLayout?.text?.length ?: -1

			if (len > 0) {
				var index = messageString.length
				var b: Int

				if (messageString.indexOf("\n", len).also { b = it } < index && b >= 0) {
					index = b
				}

				if (messageString.indexOf("\t", len).also { b = it } < index && b >= 0) {
					index = b
				}

				if (messageString.indexOf(" ", len).also { b = it } < index && b >= 0) {
					index = b
				}

				sb.append(messageString.substring(0, index))
			}
			else {
				sb.append(messageString)
			}
		}

		event.contentDescription = sb.toString()
	}

	fun getClipProgress(): Float {
		return clipProgress
	}

	fun setClipProgress(value: Float) {
		clipProgress = value
		invalidate()
	}

	fun setTopClip(value: Int) {
		topClip = value
	}

	fun setBottomClip(value: Int) {
		bottomClip = value
	}

	fun setArchivedPullAnimation(drawable: PullForegroundDrawable?) {
		archivedChatsDrawable = drawable
	}

	val isDialogFolder: Boolean
		get() = currentDialogFolderId > 0

	companion object {
		private const val COUNTER_CORNER_RADIUS = 11f
		private const val DEFAULT_HEIGHT = 72f
	}
}
