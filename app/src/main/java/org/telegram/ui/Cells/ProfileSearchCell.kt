/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 * Copyright Shamil Afandiyev, Ello 2025.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.ChatObject
import org.telegram.messenger.DialogObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.UserObject.isReplyUser
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.tgnet.emojiStatus
import org.telegram.tgnet.expires
import org.telegram.tgnet.isSelf
import org.telegram.tgnet.photo
import org.telegram.tgnet.photoSmall
import org.telegram.tgnet.status
import org.telegram.tgnet.strippedBitmap
import org.telegram.tgnet.unreadCount
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.NotificationsSettingsActivity.NotificationException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class ProfileSearchCell @JvmOverloads constructor(context: Context, private val leftPadding: Int = 0) : BaseCell(context), NotificationCenterDelegate {
	private var currentName: CharSequence? = null
	private val avatarImage = ImageReceiver(this)
	private val avatarDrawable: AvatarDrawable
	private var subLabel: CharSequence? = null
	private var lastName: String? = null
	private var lastStatus = 0
	private var lastAvatar: TLRPC.FileLocation? = null
	private var savedMessages = false
	private val currentAccount = UserConfig.selectedAccount
	private var nameLeft = 0
	private var nameTop = 0
	private var nameLayout: StaticLayout? = null
	private var drawNameLock = false
	private var nameLockLeft = 0
	private var nameLockTop = 0
	private var nameWidth = 0
	private var sublabelOffsetX = 0
	private var sublabelOffsetY = 0
	private var drawCount = false
	private var lastUnreadCount = 0
	private val countTop = AndroidUtilities.dp(19f)
	private var countLeft = 0
	private var countWidth = 0
	private var countLayout: StaticLayout? = null
	private var isOnline: BooleanArray? = null
	private var drawCheck = false
	private var drawPremium = false
	private var statusLeft = 0
	private var statusLayout: StaticLayout? = null
	private val statusDrawable: SwapAnimatedEmojiDrawable
	private val rect = RectF()
	val checkBox: CheckBox2
	var useSeparator = false

	var user: User? = null
		private set

	var chat: TLRPC.Chat? = null
		private set

	private var encryptedChat: TLRPC.EncryptedChat? = null

	var dialogId: Long = 0
		private set

	init {
		avatarImage.setRoundRadius(AndroidUtilities.dp(23f))
		avatarDrawable = AvatarDrawable()

		checkBox = CheckBox2(context, 21)
		checkBox.setColor(0, context.getColor(R.color.background), context.getColor(R.color.brand))
		checkBox.setDrawUnchecked(false)
		checkBox.setDrawBackgroundAsArc(3)

		addView(checkBox)

		statusDrawable = SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(20f))

		setPadding(AndroidUtilities.dp(leftPadding.toFloat()), 0, 0, 0)
	}

	fun setData(`object`: TLObject?, ec: TLRPC.EncryptedChat?, n: CharSequence?, s: CharSequence?, needCount: Boolean, saved: Boolean) {
		currentName = n

		when (`object`) {
			is User -> {
				user = `object`
				chat = null
			}

			is TLRPC.Chat -> {
				chat = `object`
				user = null
			}

			else -> {
				chat = null
				user = null
			}
		}

		encryptedChat = ec
		subLabel = s
		drawCount = needCount
		savedMessages = saved

		update(0)
	}

	fun setException(exception: NotificationException, name: CharSequence?) {
		var text: String?
		val enabled: Boolean
		val custom = exception.hasCustom
		val value = exception.notify
		var delta = exception.muteUntil

		if (value == 3 && delta != Int.MAX_VALUE) {
			delta -= ConnectionsManager.getInstance(currentAccount).currentTime

			text = if (delta <= 0) {
				if (custom) {
					context.getString(R.string.NotificationsCustom)
				}
				else {
					context.getString(R.string.NotificationsUnmuted)
				}
			}
			else if (delta < 60 * 60) {
				context.getString(R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60))
			}
			else if (delta < 60 * 60 * 24) {
				context.getString(R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", ceil((delta / 60.0f / 60).toDouble()).toInt()))
			}
			else if (delta < 60 * 60 * 24 * 365) {
				context.getString(R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", ceil((delta / 60.0f / 60 / 24).toDouble()).toInt()))
			}
			else {
				null
			}
		}
		else {
			enabled = when (value) {
				0 -> true
				1 -> true
				2 -> false
				else -> false
			}

			text = if (enabled && custom) {
				context.getString(R.string.NotificationsCustom)
			}
			else {
				if (enabled) context.getString(R.string.NotificationsUnmuted) else context.getString(R.string.NotificationsMuted)
			}
		}

		if (text == null) {
			text = context.getString(R.string.NotificationsOff)
		}

		if (DialogObject.isEncryptedDialog(exception.did)) {
			val encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(exception.did))

			if (encryptedChat != null) {
				val user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.userId)

				if (user != null) {
					setData(user, encryptedChat, name, text, needCount = false, saved = false)
				}
			}
		}
		else if (DialogObject.isUserDialog(exception.did)) {
			val user = MessagesController.getInstance(currentAccount).getUser(exception.did)

			if (user != null) {
				setData(user, null, name, text, needCount = false, saved = false)
			}
		}
		else {
			val chat = MessagesController.getInstance(currentAccount).getChat(-exception.did)

			if (chat != null) {
				setData(chat, null, name, text, needCount = false, saved = false)
			}
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		avatarImage.onDetachedFromWindow()
		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.emojiLoaded)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		avatarImage.onAttachedToWindow()
		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.emojiLoaded)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.emojiLoaded) {
			invalidate()
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY))
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(UserCell.defaultHeight))
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val x = if (LocaleController.isRTL) right - left - AndroidUtilities.dp(42f) else AndroidUtilities.dp(42f)
		val y = AndroidUtilities.dp(36f)

		checkBox.layout(x, y, x + checkBox.measuredWidth, y + checkBox.measuredHeight)

		if (changed) {
			buildLayout()
		}
	}

	fun setSublabelOffset(x: Int, y: Int) {
		sublabelOffsetX = x
		sublabelOffsetY = y
	}

	private fun buildLayout() {
		val chat = chat
		val user = user
		var nameString: CharSequence

		drawNameLock = false
		drawCheck = false
		drawPremium = false

		if (encryptedChat != null) {
			drawNameLock = true
			dialogId = DialogObject.makeEncryptedDialogId(encryptedChat!!.id.toLong())

			if (!LocaleController.isRTL) {
				nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
				nameLeft = AndroidUtilities.dp((AndroidUtilities.leftBaseline + 4).toFloat()) + Theme.dialogs_lockDrawable.intrinsicWidth
			}
			else {
				nameLockLeft = measuredWidth - AndroidUtilities.dp((AndroidUtilities.leftBaseline + 2).toFloat()) - Theme.dialogs_lockDrawable.intrinsicWidth
				nameLeft = AndroidUtilities.dp(11f)
			}

			nameLockTop = AndroidUtilities.dp(22.0f)

			updateStatus(false, null, false)
		}
		else {
			nameLeft = if (!LocaleController.isRTL) {
				AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
			}
			else {
				AndroidUtilities.dp(11f)
			}

			if (chat != null) {
				dialogId = -chat.id
				drawCheck = chat.verified

				updateStatus(drawCheck, null, false)
			}
			else if (user != null) {
				dialogId = user.id
				nameLockTop = AndroidUtilities.dp(21f)
				drawCheck = user.verified
				drawPremium = !user.isSelf && MessagesController.getInstance(currentAccount).isPremiumUser(user)

				updateStatus(drawCheck, user, false)
			}
			else {
				dialogId = 0
				drawCheck = false
				drawPremium = false

				updateStatus(drawCheck, null, false)
			}
		}

		if (currentName != null) {
			nameString = currentName!!
		}
		else {
			var nameString2 = ""

			if (chat != null) {
				nameString2 = chat.title ?: ""
			}
			else if (user != null) {
				nameString2 = getUserName(user)
			}

			nameString = nameString2.replace('\n', ' ')
		}

		if (nameString.isEmpty()) {
			nameString = user?.let { it.username ?: String.format(Locale.getDefault(), "%d", it.id) } ?: context.getString(R.string.HiddenName)
		}

		val currentNamePaint = if (encryptedChat != null) {
			Theme.dialogs_searchNameEncryptedPaint.also {
				it.color = context.getColor(R.color.online)
			}
		}
		else {
			Theme.dialogs_searchNamePaint.also {
				it.color = context.getColor(R.color.text)
			}
		}

		var statusWidth: Int

		if (!LocaleController.isRTL) {
			nameWidth = measuredWidth - nameLeft - AndroidUtilities.dp(14f)
			statusWidth = nameWidth
		}
		else {
			nameWidth = measuredWidth - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
			statusWidth = nameWidth
		}

		if (drawNameLock) {
			nameWidth -= AndroidUtilities.dp(6f) + Theme.dialogs_lockDrawable.intrinsicWidth
		}

		nameWidth -= paddingLeft + paddingRight

		statusWidth -= paddingLeft + paddingRight

		if (drawCount) {
			val dialog = MessagesController.getInstance(currentAccount).dialogs_dict[dialogId]

			if (dialog != null && dialog.unreadCount != 0) {
				lastUnreadCount = dialog.unreadCount

				val countString = String.format(Locale.getDefault(), "%d", dialog.unreadCount)

				countWidth = max(AndroidUtilities.dp(12f), ceil(Theme.dialogs_countTextPaint.measureText(countString).toDouble()).toInt())
				countLayout = StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

				val w = countWidth + AndroidUtilities.dp(18f)

				nameWidth -= w

				statusWidth -= w

				if (!LocaleController.isRTL) {
					countLeft = measuredWidth - countWidth - AndroidUtilities.dp(19f)
				}
				else {
					countLeft = AndroidUtilities.dp(19f)
					nameLeft += w
				}
			}
			else {
				lastUnreadCount = 0
				countLayout = null
			}
		}
		else {
			lastUnreadCount = 0
			countLayout = null
		}

		if (nameWidth < 0) {
			nameWidth = 0
		}

		var nameStringFinal = TextUtils.ellipsize(nameString, currentNamePaint, (nameWidth - AndroidUtilities.dp(12f)).toFloat(), TextUtils.TruncateAt.END)

		if (nameStringFinal != null) {
			nameStringFinal = Emoji.replaceEmoji(nameStringFinal, currentNamePaint.fontMetricsInt, false)
		}

		nameLayout = StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

		var statusString: CharSequence? = null
		var currentStatusPaint = Theme.dialogs_offlinePaint

		statusLeft = if (!LocaleController.isRTL) {
			AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
		}
		else {
			AndroidUtilities.dp(11f)
		}

		if (chat == null || subLabel != null) {
			if (subLabel != null) {
				statusString = subLabel
			}
			else if (user != null) {
				if (MessagesController.isSupportUser(user)) {
					statusString = context.getString(R.string.SupportStatus)
				}
				else if (user.bot) {
					statusString = context.getString(R.string.Bot)
				}
				else if (user.id == 333000L || user.id == BuildConfig.NOTIFICATIONS_BOT_ID) {
					statusString = context.getString(R.string.ServiceNotifications)
				}
				else {
					if (isOnline == null) {
						isOnline = BooleanArray(1)
					}

					isOnline!![0] = false

					statusString = LocaleController.formatUserStatus(currentAccount, user, isOnline)

					if (isOnline!![0]) {
						currentStatusPaint = Theme.dialogs_onlinePaint
					}

					if (user.id == getInstance(currentAccount).getClientUserId() || user.status != null && user.status!!.expires > ConnectionsManager.getInstance(currentAccount).currentTime) {
						currentStatusPaint = Theme.dialogs_onlinePaint
						statusString = context.getString(R.string.Online)
					}
				}
			}

			if (savedMessages || isReplyUser(user)) {
				statusString = null
				nameTop = AndroidUtilities.dp(20f)
			}
		}
		else {
			statusString = if (ChatObject.isChannel(chat) && !chat.megagroup) {
				if (chat.participantsCount != 0) {
					LocaleController.formatPluralString("Subscribers", chat.participantsCount)
				}
				else {
					if (TextUtils.isEmpty(chat.username)) {
						context.getString(R.string.ChannelPrivate).lowercase(Locale.getDefault())
					}
					else {
						context.getString(R.string.ChannelPublic).lowercase(Locale.getDefault())
					}
				}
			}
			else {
				if (chat.participantsCount != 0) {
					LocaleController.formatPluralString("Members", chat.participantsCount)
				}
				else {
					if (chat.hasGeo) {
						context.getString(R.string.MegaLocation)
					}
					else if (TextUtils.isEmpty(chat.username)) {
						context.getString(R.string.MegaPrivate).lowercase(Locale.getDefault())
					}
					else {
						context.getString(R.string.MegaPublic).lowercase(Locale.getDefault())
					}
				}
			}

			nameTop = AndroidUtilities.dp(19f)
		}

		if (!statusString.isNullOrEmpty()) {
			val statusStringFinal = TextUtils.ellipsize(statusString, currentStatusPaint, (statusWidth - AndroidUtilities.dp(12f)).toFloat(), TextUtils.TruncateAt.END)

			statusLayout = StaticLayout(statusStringFinal, currentStatusPaint, statusWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			nameTop = AndroidUtilities.dp(9f)
			nameLockTop -= AndroidUtilities.dp(10f)
		}
		else {
			nameTop = AndroidUtilities.dp(20f)
			statusLayout = null
		}

		val avatarLeft = if (LocaleController.isRTL) {
			measuredWidth - AndroidUtilities.dp(57f) - paddingRight
		}
		else {
			AndroidUtilities.dp(11f) + paddingLeft
		}

		avatarImage.setImageCoordinates(avatarLeft.toFloat(), AndroidUtilities.dp(7f).toFloat(), AndroidUtilities.dp(46f).toFloat(), AndroidUtilities.dp(46f).toFloat())

		var widthpx: Double
		var left: Float

		if (LocaleController.isRTL) {
			if (nameLayout!!.lineCount > 0) {
				left = nameLayout!!.getLineLeft(0)

				if (left == 0f) {
					widthpx = ceil(nameLayout!!.getLineWidth(0).toDouble())

					if (widthpx < nameWidth) {
						nameLeft += (nameWidth - widthpx).toInt()
					}
				}
			}

			if (statusLayout != null && statusLayout!!.lineCount > 0) {
				left = statusLayout!!.getLineLeft(0)

				if (left == 0f) {
					widthpx = ceil(statusLayout!!.getLineWidth(0).toDouble())

					if (widthpx < statusWidth) {
						statusLeft += (statusWidth - widthpx).toInt()
					}
				}
			}
		}
		else {
			if (nameLayout!!.lineCount > 0) {
				left = nameLayout!!.getLineRight(0)

				if (left == nameWidth.toFloat()) {
					widthpx = ceil(nameLayout!!.getLineWidth(0).toDouble())

					if (widthpx < nameWidth) {
						nameLeft -= (nameWidth - widthpx).toInt()
					}
				}
			}

			if (statusLayout != null && statusLayout!!.lineCount > 0) {
				left = statusLayout!!.getLineRight(0)

				if (left == statusWidth.toFloat()) {
					widthpx = ceil(statusLayout!!.getLineWidth(0).toDouble())

					if (widthpx < statusWidth) {
						statusLeft -= (statusWidth - widthpx).toInt()
					}
				}
			}
		}

		nameLeft += paddingLeft
		statusLeft += paddingLeft
		nameLockLeft += paddingLeft
	}

	fun updateStatus(verified: Boolean, user: User?, animated: Boolean) {
		if (verified) {
			if (user != null && !user.isSelf && MessagesController.getInstance(currentAccount).isPremiumUser(user)) {
				statusDrawable.set(ResourcesCompat.getDrawable(resources, R.drawable.verified_donated_icon, null), animated)
				statusDrawable.color = null
			}
			else if (user?.isSelf == false) {
				statusDrawable.set(ResourcesCompat.getDrawable(resources, R.drawable.verified_icon, null), animated)
				statusDrawable.color = null
			}
		}
		else if (user != null && !user.isSelf && user.emojiStatus is TLRPC.TLEmojiStatusUntil && (user.emojiStatus as TLRPC.TLEmojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
			statusDrawable.set((user.emojiStatus as TLRPC.TLEmojiStatusUntil).documentId, animated)
			statusDrawable.color = ResourcesCompat.getColor(resources, R.color.brand, null)
		}
		else if (user != null && !user.isSelf && user.emojiStatus is TLRPC.TLEmojiStatus) {
			statusDrawable.set((user.emojiStatus as TLRPC.TLEmojiStatus).documentId, animated)
			statusDrawable.color = ResourcesCompat.getColor(resources, R.color.brand, null)
		}
		else if (user != null && !user.isSelf && MessagesController.getInstance(currentAccount).isPremiumUser(user)) {
			statusDrawable.set(ResourcesCompat.getDrawable(resources, R.drawable.donated, null), animated)
			statusDrawable.color = null
		}
		else {
			statusDrawable.set(null as Drawable?, animated)
			statusDrawable.color = ResourcesCompat.getColor(resources, R.color.brand, null)
		}
	}

	fun update(mask: Int) {
		var photo: TLRPC.FileLocation? = null

		if (user != null) {
			avatarDrawable.setInfo(user)

			if (isReplyUser(user)) {
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES
				avatarImage.setImage(null, null, avatarDrawable, null, null, 0)
			}
			else if (savedMessages) {
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
				avatarImage.setImage(null, null, avatarDrawable, null, null, 0)
			}
			else {
				var thumb: Drawable? = avatarDrawable

				if (user?.photo != null) {
					photo = user?.photo?.photoSmall
					thumb = user?.photo?.strippedBitmap
				}

				avatarImage.setImage(ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_STRIPPED), "50_50", thumb, user, 0)
			}
		}
		else if (chat != null) {
			var thumb: Drawable? = avatarDrawable

			if (chat?.photo != null) {
				photo = chat?.photo?.photoSmall

				chat?.photo?.strippedBitmap?.let {
					thumb = it
				}
			}

			avatarDrawable.setInfo(chat)
			avatarImage.setImage(ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL), "50_50", ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_STRIPPED), "50_50", thumb, chat, 0)
		}
		else {
			avatarDrawable.setInfo(null, null)
			avatarImage.setImage(null, null, avatarDrawable, null, null, 0)
		}

		if (mask != 0) {
			var continueUpdate = false

			if (mask and MessagesController.UPDATE_MASK_AVATAR != 0 && user != null || mask and MessagesController.UPDATE_MASK_CHAT_AVATAR != 0 && chat != null) {
				if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar!!.volumeId != photo!!.volumeId || lastAvatar!!.localId != photo.localId)) {
					continueUpdate = true
				}
			}

			if (!continueUpdate && mask and MessagesController.UPDATE_MASK_STATUS != 0 && user != null) {
				val newStatus = user?.status?.expires ?: 0

				if (newStatus != lastStatus) {
					continueUpdate = true
				}
			}

			if (!continueUpdate && mask and MessagesController.UPDATE_MASK_EMOJI_STATUS != 0 && user != null) {
				updateStatus(user!!.verified, user, true)
			}

			if (!continueUpdate && mask and MessagesController.UPDATE_MASK_NAME != 0 && user != null || mask and MessagesController.UPDATE_MASK_CHAT_NAME != 0 && chat != null) {
				val newName = if (user != null) {
					user!!.firstName + user!!.lastName
				}
				else {
					chat!!.title
				}

				if (newName != lastName) {
					continueUpdate = true
				}
			}

			if (!continueUpdate && drawCount && mask and MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE != 0) {
				val dialog = MessagesController.getInstance(currentAccount).dialogs_dict[dialogId]

				if (dialog != null && dialog.unreadCount != lastUnreadCount) {
					continueUpdate = true
				}
			}

			if (!continueUpdate) {
				return
			}
		}

		if (user != null) {
			lastStatus = user?.status?.expires ?: 0
			lastName = user?.firstName + user?.lastName
		}
		else if (chat != null) {
			lastName = chat?.title
		}
		else {
			lastName = context.getString(R.string.HiddenName)
		}

		lastAvatar = photo

		if (measuredWidth != 0 || measuredHeight != 0) {
			buildLayout()
		}
		else {
			requestLayout()
		}

		postInvalidate()
	}

	override fun onDraw(canvas: Canvas) {
		if (useSeparator) {
			if (LocaleController.isRTL) {
				canvas.drawLine(0f, (measuredHeight - 1).toFloat(), (measuredWidth - AndroidUtilities.dp(UserCell.dividerPadding) - AndroidUtilities.dp(leftPadding.toFloat())).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
			else {
				canvas.drawLine(AndroidUtilities.dp(UserCell.dividerPadding).toFloat() + AndroidUtilities.dp(leftPadding.toFloat()), (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
		}

		if (drawNameLock) {
			setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop)
			Theme.dialogs_lockDrawable.draw(canvas)
		}

		if (nameLayout != null) {
			canvas.withTranslation(nameLeft.toFloat(), nameTop.toFloat()) {
				nameLayout?.draw(this)
			}

			val x = if (LocaleController.isRTL) {
				if (nameLayout!!.getLineLeft(0) == 0f) {
					nameLeft - AndroidUtilities.dp(6f) - statusDrawable.intrinsicWidth
				}
				else {
					val w = nameLayout!!.getLineWidth(0)
					(nameLeft + nameWidth - ceil(w.toDouble()) - AndroidUtilities.dp(6f) - statusDrawable.intrinsicWidth).toInt()
				}
			}
			else {
				(nameLeft + nameLayout!!.getLineRight(0) + AndroidUtilities.dp(6f)).toInt()
			}

			setDrawableBounds(statusDrawable, x.toFloat(), nameTop + (nameLayout!!.height - statusDrawable.intrinsicHeight) / 2f)

			statusDrawable.draw(canvas)
		}

		if (statusLayout != null) {
			canvas.withTranslation((statusLeft + sublabelOffsetX).toFloat(), (AndroidUtilities.dp(33f) + sublabelOffsetY).toFloat()) {
				statusLayout?.draw(this)
			}
		}

		if (countLayout != null) {
			val x = countLeft - AndroidUtilities.dp(5.5f)
			rect.set(x.toFloat(), countTop.toFloat(), (x + countWidth + AndroidUtilities.dp(11f)).toFloat(), (countTop + AndroidUtilities.dp(23f)).toFloat())
			canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, if (MessagesController.getInstance(currentAccount).isDialogMuted(dialogId)) Theme.dialogs_countGrayPaint else Theme.dialogs_countPaint)

			canvas.withTranslation(countLeft.toFloat(), (countTop + AndroidUtilities.dp(4f)).toFloat()) {
				countLayout?.draw(this)
			}
		}

		avatarImage.draw(canvas)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.text = buildString {
			nameLayout?.let {
				append(it.text)
			}

			if (drawCheck) {
				append(", ").append(context.getString(R.string.AccDescrVerified)).append("\n")
			}

			statusLayout?.let {
				if (isNotEmpty()) {
					append(", ")
				}

				append(it.text)
			}
		}

		if (checkBox.isChecked) {
			info.isCheckable = true
			info.isChecked = checkBox.isChecked
			info.className = "android.widget.CheckBox"
		}
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		checkBox.setChecked(checked, animated)
	}
}
