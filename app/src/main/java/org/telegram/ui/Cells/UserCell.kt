/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import org.telegram.messenger.*
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.*
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.WrapSizeDrawable
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.NotificationsSettingsActivity.NotificationException
import kotlin.math.ceil

class UserCell @JvmOverloads constructor(context: Context, padding: Int, checkbox: Int, admin: Boolean, needAddButton: Boolean = false) : FrameLayout(context), NotificationCenterDelegate {
	private val avatarImageView: BackupImageView
	private val nameTextView: SimpleTextView
	private val statusTextView: SimpleTextView
	private val imageView: ImageView
	private var checkBox: CheckBox? = null
	private var checkBoxBig: CheckBoxSquare? = null
	private var adminTextView: TextView? = null
	private var addButton: TextView? = null
	private var premiumDrawable: Drawable? = null
	private val emojiStatus: SwapAnimatedEmojiDrawable
	private val avatarDrawable: AvatarDrawable

	var currentObject: Any? = null
		private set

	private var encryptedChat: TLRPC.EncryptedChat? = null
	private var currentName: CharSequence? = null
	private var currentStatus: CharSequence? = null
	private var currentDrawable = 0
	private var selfAsSavedMessages = false
	private var lastName: String? = null
	private var lastStatus = 0
	private var lastAvatar: TLRPC.FileLocation? = null
	private val currentAccount = UserConfig.selectedAccount
	private var statusColor: Int
	private var statusOnlineColor: Int
	private var needDivider = false

	init {
		val additionalPadding: Int

		if (needAddButton) {
			addButton = TextView(context).also {
				it.gravity = Gravity.CENTER
				it.setTextColor(ResourcesCompat.getColor(resources, R.color.white, null))
				it.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				it.typeface = Theme.TYPEFACE_BOLD
				it.background = Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 4f)
				it.text = context.getString(R.string.Add)
				it.setPadding(AndroidUtilities.dp(17f), 0, AndroidUtilities.dp(17f), 0)

				addView(it, createFrame(LayoutHelper.WRAP_CONTENT, 28f, Gravity.TOP or if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT, if (LocaleController.isRTL) 14f else 0f, 15f, if (LocaleController.isRTL) 0f else 14f, 0f))

				additionalPadding = ceil(((it.paint.measureText(it.text.toString()) + AndroidUtilities.dp((34 + 14).toFloat())) / AndroidUtilities.density).toDouble()).toInt()
			}
		}
		else {
			additionalPadding = 0
		}

		statusColor = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
		statusOnlineColor = ResourcesCompat.getColor(resources, R.color.brand, null)

		avatarDrawable = AvatarDrawable()

		avatarImageView = BackupImageView(context)
		avatarImageView.setRoundRadius(AndroidUtilities.dp(avatarSide.toFloat() * 0.45f))

		addView(avatarImageView, createFrame(avatarSide, avatarSide.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 0f else 7f + padding.toFloat(), (defaultHeight - avatarSide) / 2, if (LocaleController.isRTL) 7f + padding else 0f, 0f))

		nameTextView = SimpleTextView(context)
		nameTextView.textColor = ResourcesCompat.getColor(resources, R.color.text, null)
		nameTextView.setTypeface(Theme.TYPEFACE_BOLD)
		nameTextView.setTextSize(16)
		nameTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(nameTextView, createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 28f + (if (checkbox == 2) 18f else 0f) + additionalPadding else (64 + padding).toFloat(), 11f, if (LocaleController.isRTL) 64f + padding else 28f + (if (checkbox == 2) 18f else 0f) + additionalPadding.toFloat(), 0f))

		emojiStatus = SwapAnimatedEmojiDrawable(nameTextView, AndroidUtilities.dp(20f))

		statusTextView = SimpleTextView(context)
		statusTextView.setTextSize(12)
		statusTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(statusTextView, createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 28f + additionalPadding else (64f + padding), 33f, if (LocaleController.isRTL) 64f + padding else 28f + additionalPadding.toFloat(), 0f))

		imageView = ImageView(context)
		imageView.scaleType = ImageView.ScaleType.CENTER
		imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)
		imageView.visibility = GONE

		addView(imageView, createFrame(LayoutParams.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 0f else 16f, 0f, if (LocaleController.isRTL) 16f else 0f, 0f))

		if (checkbox == 2) {
			checkBoxBig = CheckBoxSquare(context).also {
				addView(it, createFrame(18, 18f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 19f else 0f, 0f, if (LocaleController.isRTL) 0f else 19f, 0f))
			}
		}
		else if (checkbox == 1) {
			checkBox = CheckBox(context, R.drawable.round_check2).also {
				it.visibility = INVISIBLE
				it.setColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null), ResourcesCompat.getColor(resources, R.color.brand, null))

				addView(it, createFrame(22, 22f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 0f else 37f + padding.toFloat(), 40f, if (LocaleController.isRTL) 37f + padding else 0f, 0f))
			}
		}

		if (admin) {
			adminTextView = TextView(context).also {
				it.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				it.setTextColor(ResourcesCompat.getColor(resources, R.color.brand, null))

				addView(it, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP, if (LocaleController.isRTL) 23f else 0f, 10f, if (LocaleController.isRTL) 0f else 23f, 0f))
			}
		}

		isFocusable = true
	}

	fun setAvatarPadding(padding: Int) {
		avatarImageView.updateLayoutParams<LayoutParams> {
			leftMargin = AndroidUtilities.dp(if (LocaleController.isRTL) 0f else 7f + padding.toFloat())
			rightMargin = AndroidUtilities.dp(if (LocaleController.isRTL) 7f + padding else 0f)
		}

		nameTextView.updateLayoutParams<LayoutParams> {
			leftMargin = AndroidUtilities.dp(if (LocaleController.isRTL) 28f + (if (checkBoxBig != null) 18f else 0f) else (64f + padding))
			rightMargin = AndroidUtilities.dp(if (LocaleController.isRTL) 64f + padding else 28f + (if (checkBoxBig != null) 18f else 0f).toFloat())
		}

		statusTextView.updateLayoutParams<LayoutParams> {
			leftMargin = AndroidUtilities.dp(if (LocaleController.isRTL) 28f else (64f + padding))
			rightMargin = AndroidUtilities.dp(if (LocaleController.isRTL) 64f + padding else 28f)
		}

		checkBox?.updateLayoutParams<LayoutParams> {
			leftMargin = AndroidUtilities.dp(if (LocaleController.isRTL) 0f else 37f + padding.toFloat())
			rightMargin = AndroidUtilities.dp(if (LocaleController.isRTL) 37f + padding else 0f)
		}
	}

	fun setAddButtonVisible(value: Boolean) {
		addButton?.visibility = if (value) VISIBLE else GONE
	}

	fun setAdminRole(role: String?) {
		val adminTextView = adminTextView ?: return

		adminTextView.visibility = if (role != null) VISIBLE else GONE
		adminTextView.text = role

		if (role != null) {
			val text = adminTextView.text
			val size = ceil(adminTextView.paint.measureText(text, 0, text.length).toDouble()).toInt()
			nameTextView.setPadding(if (LocaleController.isRTL) size + AndroidUtilities.dp(6f) else 0, 0, if (!LocaleController.isRTL) size + AndroidUtilities.dp(6f) else 0, 0)
		}
		else {
			nameTextView.setPadding(0, 0, 0, 0)
		}
	}

	val name: CharSequence
		get() = nameTextView.getText()

	fun setData(`object`: Any?, name: CharSequence?, status: CharSequence?, resId: Int) {
		setData(`object`, null, name, status, resId, false)
	}

	fun setData(`object`: Any?, name: CharSequence?, status: CharSequence?, resId: Int, divider: Boolean) {
		setData(`object`, null, name, status, resId, divider)
	}

	fun setData(`object`: Any?, ec: TLRPC.EncryptedChat?, name: CharSequence?, status: CharSequence?, resId: Int, divider: Boolean) {
		@Suppress("NAME_SHADOWING") var name = name

		if (`object` == null && name == null && status == null) {
			currentStatus = null
			currentName = null
			currentObject = null
			nameTextView.setText("")
			statusTextView.setText("")
			avatarImageView.setImageDrawable(null)
			return
		}

		encryptedChat = ec
		currentStatus = status

		try {
			if (name != null) {
				name = Emoji.replaceEmoji(name, nameTextView.paint.fontMetricsInt, false)
			}
		}
		catch (e: Exception) {
			// ignored
		}

		currentName = name
		currentObject = `object`
		currentDrawable = resId
		needDivider = divider

		setWillNotDraw(!needDivider)

		update(0)
	}

	fun setException(exception: NotificationException, name: CharSequence?, divider: Boolean) {
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
				LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60))
			}
			else if (delta < 60 * 60 * 24) {
				LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", ceil((delta / 60.0f / 60).toDouble()).toInt()))
			}
			else if (delta < 60 * 60 * 24 * 365) {
				LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", ceil((delta / 60.0f / 60 / 24).toDouble()).toInt()))
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
				val user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id)

				if (user != null) {
					setData(user, encryptedChat, name, text, 0, false)
				}
			}
		}
		else if (DialogObject.isUserDialog(exception.did)) {
			val user = MessagesController.getInstance(currentAccount).getUser(exception.did)

			if (user != null) {
				setData(user, null, name, text, 0, divider)
			}
		}
		else {
			val chat = MessagesController.getInstance(currentAccount).getChat(-exception.did)

			if (chat != null) {
				setData(chat, null, name, text, 0, divider)
			}
		}
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		checkBox?.run {
			visible()
			setChecked(checked, animated)
		}

		checkBoxBig?.run {
			visible()
			setChecked(checked, animated)
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(defaultHeight), MeasureSpec.EXACTLY))
	}

	override fun invalidate() {
		super.invalidate()
		checkBoxBig?.invalidate()
	}

	fun update(mask: Int) {
		var photo: TLRPC.FileLocation? = null
		var newName: String? = null
		var currentUser: User? = null
		var currentChat: TLRPC.Chat? = null

		if (currentObject is User) {
			currentUser = currentObject as User?
			photo = currentUser?.photo?.photo_small
		}
		else if (currentObject is TLRPC.Chat) {
			currentChat = currentObject as TLRPC.Chat?
			photo = currentChat?.photo?.photo_small
		}

		if (mask != 0) {
			var continueUpdate = false

			if (mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
				if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar!!.volume_id != photo!!.volume_id || lastAvatar!!.local_id != photo.local_id)) {
					continueUpdate = true
				}
			}

			if (currentUser != null && !continueUpdate && mask and MessagesController.UPDATE_MASK_STATUS != 0) {
				val newStatus = currentUser.status?.expires ?: 0

				if (newStatus != lastStatus) {
					continueUpdate = true
				}
			}

			if (!continueUpdate && currentName == null && lastName != null && mask and MessagesController.UPDATE_MASK_NAME != 0) {
				newName = if (currentUser != null) {
					getUserName(currentUser)
				}
				else {
					currentChat!!.title
				}
				if (newName != lastName) {
					continueUpdate = true
				}
			}

			if (!continueUpdate) {
				return
			}
		}

		if (currentObject is String) {
			nameTextView.updateLayoutParams<LayoutParams> {
				topMargin = AndroidUtilities.dp(19f)
			}

			when (currentObject as String) {
				"contacts" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS
				"non_contacts" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_NON_CONTACTS
				"groups" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS
				"channels" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS
				"bots" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_BOTS
				"muted" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_MUTED
				"read" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_READ
				"archived" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_ARCHIVED
			}

			avatarImageView.setImage(null, "50_50", avatarDrawable)

			currentStatus = ""
		}
		else {
			nameTextView.updateLayoutParams<LayoutParams> {
				topMargin = AndroidUtilities.dp(10f)
			}

			if (currentUser != null) {
				if (selfAsSavedMessages && isUserSelf(currentUser)) {
					nameTextView.setText(context.getString(R.string.SavedMessages), true)
					statusTextView.setText(null)
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
					avatarImageView.setImage(null, "50_50", avatarDrawable, currentUser)
					(nameTextView.layoutParams as LayoutParams).topMargin = AndroidUtilities.dp(19f)
					return
				}

				avatarDrawable.setInfo(currentUser)

				lastStatus = currentUser.status?.expires ?: 0
			}
			else if (currentChat != null) {
				avatarDrawable.setInfo(currentChat)
			}
			else if (currentName != null) {
				avatarDrawable.setInfo(currentName.toString(), null)
			}
			else {
				avatarDrawable.setInfo("#", null)
			}
		}

		if (currentName != null) {
			lastName = null
			nameTextView.setText(currentName)
		}
		else {
			lastName = if (currentUser != null) {
				newName ?: getUserName(currentUser)
			}
			else if (currentChat != null) {
				newName ?: currentChat.title
			}
			else {
				""
			}

			var name: CharSequence? = lastName

			if (name != null) {
				try {
					name = Emoji.replaceEmoji(lastName, nameTextView.paint.fontMetricsInt, false)
				}
				catch (e: Exception) {
					// ignored
				}
			}

			nameTextView.setText(name)
		}
		if (currentUser != null && MessagesController.getInstance(currentAccount).isPremiumUser(currentUser)) {
			if (currentUser.emoji_status is TLRPC.TL_emojiStatusUntil && (currentUser.emoji_status as TLRPC.TL_emojiStatusUntil).until > (System.currentTimeMillis() / 1000).toInt()) {
				emojiStatus[(currentUser.emoji_status as TLRPC.TL_emojiStatusUntil).document_id] = false
				emojiStatus.color = ResourcesCompat.getColor(resources, R.color.brand, null)
				nameTextView.rightDrawable = emojiStatus
			}
			else if (currentUser.emoji_status is TLRPC.TL_emojiStatus) {
				emojiStatus[(currentUser.emoji_status as TLRPC.TL_emojiStatus).document_id] = false
				emojiStatus.color = ResourcesCompat.getColor(resources, R.color.brand, null)
				nameTextView.rightDrawable = emojiStatus
			}
			else {
				if (premiumDrawable == null) {
					premiumDrawable = ResourcesCompat.getDrawable(resources, R.drawable.msg_premium_liststar, null)?.mutate().let {
						object : WrapSizeDrawable(it, AndroidUtilities.dp(14f), AndroidUtilities.dp(14f)) {
							override fun draw(canvas: Canvas) {
								canvas.save()
								canvas.translate(0f, AndroidUtilities.dp(1f).toFloat())
								super.draw(canvas)
								canvas.restore()
							}
						}
					}

					premiumDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
				}

				nameTextView.rightDrawable = premiumDrawable
			}

			nameTextView.setRightDrawableTopPadding(-AndroidUtilities.dp(0.5f))
		}
		else {
			nameTextView.rightDrawable = null
			nameTextView.setRightDrawableTopPadding(0)
		}

		if (currentStatus != null) {
			statusTextView.textColor = statusColor
			statusTextView.setText(currentStatus)
		}
		else if (currentUser != null) {
			if (currentUser.bot) {
				statusTextView.textColor = statusColor

				if (currentUser.bot_chat_history || adminTextView?.visibility == VISIBLE) {
					statusTextView.setText(context.getString(R.string.BotStatusRead))
				}
				else {
					statusTextView.setText(context.getString(R.string.BotStatusCantRead))
				}
			}
			else {
				if ((currentUser.id == getInstance(currentAccount).getClientUserId() || currentUser.status != null) && (currentUser.status?.expires ?: 0) > ConnectionsManager.getInstance(currentAccount).currentTime || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser.id)) {
					statusTextView.textColor = statusOnlineColor
					statusTextView.setText(context.getString(R.string.Online))
				}
				else {
					statusTextView.textColor = statusColor
					statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentUser))
				}
			}
		}

		if (imageView.visibility == VISIBLE && currentDrawable == 0 || imageView.visibility == GONE && currentDrawable != 0) {
			imageView.visibility = if (currentDrawable == 0) GONE else VISIBLE
			imageView.setImageResource(currentDrawable)
		}

		lastAvatar = photo

		if (currentUser != null) {
			avatarImageView.setForUserOrChat(currentUser, avatarDrawable)
		}
		else if (currentChat != null) {
			avatarImageView.setForUserOrChat(currentChat, avatarDrawable)
		}
		else {
			avatarImageView.setImageDrawable(avatarDrawable)
		}

		nameTextView.textColor = ResourcesCompat.getColor(resources, R.color.text, null)

		adminTextView?.setTextColor(ResourcesCompat.getColor(resources, R.color.brand, null))
	}

	fun setSelfAsSavedMessages(value: Boolean) {
		selfAsSavedMessages = value
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			canvas.drawLine(if (LocaleController.isRTL) 0f else AndroidUtilities.dp(dividerPadding).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(dividerPadding) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		if (checkBoxBig != null && checkBoxBig?.visibility == VISIBLE) {
			info.isCheckable = true
			info.isChecked = checkBoxBig?.isChecked ?: false
			info.className = "android.widget.CheckBox"
		}
		else if (checkBox != null && checkBox?.visibility == VISIBLE) {
			info.isCheckable = true
			info.isChecked = checkBox?.isChecked ?: false
			info.className = "android.widget.CheckBox"
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.emojiLoaded) {
			nameTextView.invalidate()
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.emojiLoaded)
		emojiStatus.attach()
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.emojiLoaded)
		emojiStatus.detach()
	}

	companion object {
		const val dividerPadding = 81f
		const val defaultHeight = 60f
		const val avatarSide = 42
	}
}
