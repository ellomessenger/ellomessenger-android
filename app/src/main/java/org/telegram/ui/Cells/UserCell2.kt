/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isChannel
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.tgnet.botChatHistory
import org.telegram.tgnet.expires
import org.telegram.tgnet.photo
import org.telegram.tgnet.photoSmall
import org.telegram.tgnet.status
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CheckBox
import org.telegram.ui.Components.CheckBoxSquare
import org.telegram.ui.Components.LayoutHelper

class UserCell2(context: Context, padding: Int, checkbox: Int) : FrameLayout(context) {
	private val avatarImageView = BackupImageView(context)
	private val nameTextView: SimpleTextView
	private val statusTextView: SimpleTextView
	private val imageView: ImageView
	private var checkBox: CheckBox? = null
	private var checkBoxBig: CheckBoxSquare? = null
	private val avatarDrawable = AvatarDrawable()
	private var currentObject: TLObject? = null
	private var currentName: CharSequence? = null
	private var currentStatus: CharSequence? = null
	private var currentDrawable = 0
	private var lastName: String? = null
	private var lastStatus = 0
	private var lastAvatar: FileLocation? = null
	private val currentAccount = UserConfig.selectedAccount

	@ColorInt
	private var statusColor = context.getColor(R.color.dark_gray)

	@ColorInt
	private var statusOnlineColor = context.getColor(R.color.brand)

	init {
		avatarImageView.setRoundRadius(AndroidUtilities.dp(24f))

		addView(avatarImageView, LayoutHelper.createFrame(48, 48f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 7 + padding).toFloat(), 11f, (if (LocaleController.isRTL) 7 + padding else 0).toFloat(), 0f))

		nameTextView = SimpleTextView(context)
		nameTextView.textColor = context.getColor(R.color.text)
		nameTextView.setTextSize(17)
		nameTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 28 + (if (checkbox == 2) 18 else 0) else 68 + padding).toFloat(), 14.5f, (if (LocaleController.isRTL) 68 + padding else 28 + if (checkbox == 2) 18 else 0).toFloat(), 0f))

		statusTextView = SimpleTextView(context)
		statusTextView.setTextSize(14)
		statusTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 28 else 68 + padding).toFloat(), 37.5f, (if (LocaleController.isRTL) 68 + padding else 28).toFloat(), 0f))

		imageView = ImageView(context)
		imageView.scaleType = ImageView.ScaleType.CENTER
		imageView.colorFilter = PorterDuffColorFilter(context.getColor(R.color.dark_gray), PorterDuff.Mode.SRC_IN)
		imageView.gone()

		addView(imageView, LayoutHelper.createFrame(LayoutParams.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) 0 else 16).toFloat(), 0f, (if (LocaleController.isRTL) 16 else 0).toFloat(), 0f))

		if (checkbox == 2) {
			checkBoxBig = CheckBoxSquare(context)
			addView(checkBoxBig, LayoutHelper.createFrame(18, 18f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) 19 else 0).toFloat(), 0f, (if (LocaleController.isRTL) 0 else 19).toFloat(), 0f))
		}
		else if (checkbox == 1) {
			checkBox = CheckBox(context, R.drawable.round_check2)
			checkBox?.invisible()
			checkBox?.setColor(context.getColor(R.color.brand), context.getColor(R.color.white))

			addView(checkBox, LayoutHelper.createFrame(22, 22f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 37 + padding).toFloat(), 41f, (if (LocaleController.isRTL) 37 + padding else 0).toFloat(), 0f))
		}
	}

	fun setData(`object`: TLObject?, name: CharSequence?, status: CharSequence?, resId: Int) {
		if (`object` == null && name.isNullOrEmpty() && status.isNullOrEmpty()) {
			currentStatus = null
			currentName = null
			currentObject = null
			nameTextView.setText("")
			statusTextView.setText("")
			avatarImageView.setImageDrawable(null)
			return
		}

		currentStatus = status
		currentName = name
		currentObject = `object`
		currentDrawable = resId

		update(0)
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		if (checkBox != null) {
			if (checkBox?.visibility != VISIBLE) {
				checkBox?.visible()
			}

			checkBox?.setChecked(checked, animated)
		}
		else if (checkBoxBig != null) {
			if (checkBoxBig?.visibility != VISIBLE) {
				checkBoxBig?.visible()
			}

			checkBoxBig?.setChecked(checked, animated)
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(70f), MeasureSpec.EXACTLY))
	}

	override fun invalidate() {
		super.invalidate()
		checkBoxBig?.invalidate()
	}

	fun update(mask: Int) {
		var photo: FileLocation? = null
		var newName: String? = null
		var currentUser: User? = null
		var currentChat: Chat? = null

		if (currentObject is User) {
			currentUser = currentObject as? User
			photo = currentUser?.photo?.photoSmall
		}
		else if (currentObject is Chat) {
			currentChat = currentObject as? Chat
			photo = currentChat?.photo?.photoSmall
		}

		if (mask != 0) {
			var continueUpdate = false

			if (mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
				if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar?.volumeId != photo.volumeId || lastAvatar?.localId != photo.localId)) {
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
					UserObject.getUserName(currentUser)
				}
				else {
					currentChat?.title
				}

				if (newName != lastName) {
					continueUpdate = true
				}
			}

			if (!continueUpdate) {
				return
			}
		}

		lastAvatar = photo

		if (currentUser != null) {
			avatarDrawable.setInfo(currentUser)

			lastStatus = currentUser.status?.expires ?: 0
		}
		else if (currentChat != null) {
			avatarDrawable.setInfo(currentChat)
		}
		else if (currentName != null) {
			avatarDrawable.setInfo(currentName?.toString(), null)
		}
		else {
			avatarDrawable.setInfo("#", null)
		}

		if (currentName != null) {
			lastName = null
			nameTextView.setText(currentName)
		}
		else {
			lastName = if (currentUser != null) {
				newName ?: UserObject.getUserName(currentUser)
			}
			else {
				newName ?: currentChat?.title
			}

			nameTextView.setText(lastName)
		}

		if (currentStatus != null) {
			statusTextView.textColor = statusColor
			statusTextView.setText(currentStatus)
			avatarImageView.setForUserOrChat(currentUser, avatarDrawable)
		}
		else if (currentUser != null) {
			if (currentUser.bot) {
				statusTextView.textColor = statusColor

				if (currentUser.botChatHistory) {
					statusTextView.setText(context.getString(R.string.BotStatusRead))
				}
				else {
					statusTextView.setText(context.getString(R.string.BotStatusCantRead))
				}
			}
			else {
				if (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId() || currentUser.status != null && currentUser.status!!.expires > ConnectionsManager.getInstance(currentAccount).currentTime || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser.id)) {
					statusTextView.textColor = statusOnlineColor
					statusTextView.setText(context.getString(R.string.Online))
				}
				else {
					statusTextView.textColor = statusColor
					statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentUser))
				}
			}

			avatarImageView.setForUserOrChat(currentUser, avatarDrawable)
		}
		else if (currentChat != null) {
			statusTextView.textColor = statusColor

			if (isChannel(currentChat) && !currentChat.megagroup) {
				if (currentChat.participantsCount != 0) {
					statusTextView.setText(LocaleController.formatPluralString("Subscribers", currentChat.participantsCount))
				}
				else if (TextUtils.isEmpty(currentChat.username)) {
					statusTextView.setText(context.getString(R.string.ChannelPrivate))
				}
				else {
					statusTextView.setText(context.getString(R.string.ChannelPublic))
				}
			}
			else {
				if (currentChat.participantsCount != 0) {
					statusTextView.setText(LocaleController.formatPluralString("Members", currentChat.participantsCount))
				}
				else if (currentChat.hasGeo) {
					statusTextView.setText(context.getString(R.string.MegaLocation))
				}
				else if (TextUtils.isEmpty(currentChat.username)) {
					statusTextView.setText(context.getString(R.string.MegaPrivate))
				}
				else {
					statusTextView.setText(context.getString(R.string.MegaPublic))
				}
			}

			avatarImageView.setForUserOrChat(currentChat, avatarDrawable)
		}
		else {
			avatarImageView.setImageDrawable(avatarDrawable)
		}

		if (imageView.isVisible && currentDrawable == 0 || imageView.isGone && currentDrawable != 0) {
			imageView.visibility = if (currentDrawable == 0) GONE else VISIBLE
			imageView.setImageResource(currentDrawable)
		}
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}
}
