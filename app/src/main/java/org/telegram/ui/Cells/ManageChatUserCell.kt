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
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.bot
import org.telegram.tgnet.expires
import org.telegram.tgnet.photo
import org.telegram.tgnet.photoSmall
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper

class ManageChatUserCell(context: Context, avatarPadding: Int, private val namePadding: Int, needOption: Boolean) : FrameLayout(context) {
	private val avatarImageView: BackupImageView
	private val nameTextView: SimpleTextView
	private val statusTextView: SimpleTextView
	private var optionsButton: ImageView? = null
	private var customImageView: ImageView? = null
	private val avatarDrawable: AvatarDrawable

	var currentObject: Any? = null
		private set

	private var currentName: CharSequence? = null
	private var currentStatus: CharSequence? = null
	private var lastName: String? = null
	private var lastStatus = 0
	private var lastAvatar: FileLocation? = null
	private var isAdmin = false
	private var needDivider = false
	private var dividerColor = 0
	private var statusColor: Int
	private var statusOnlineColor: Int
	private val currentAccount = UserConfig.selectedAccount
	private var delegate: ManageChatUserCellDelegate? = null

	fun interface ManageChatUserCellDelegate {
		fun onOptionsButtonCheck(cell: ManageChatUserCell, click: Boolean): Boolean
	}

	init {
		statusColor = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
		statusOnlineColor = ResourcesCompat.getColor(context.resources, R.color.brand, null)

		avatarDrawable = AvatarDrawable()

		avatarImageView = BackupImageView(context)
		avatarImageView.setRoundRadius(AndroidUtilities.dp(23f))

		addView(avatarImageView, LayoutHelper.createFrame(46, 46f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 7 + avatarPadding).toFloat(), 8f, (if (LocaleController.isRTL) 7 + avatarPadding else 0).toFloat(), 0f))

		nameTextView = SimpleTextView(context)
		nameTextView.textColor = ResourcesCompat.getColor(context.resources, R.color.text, null)
		nameTextView.setTextSize(17)
		nameTextView.setTypeface(Theme.TYPEFACE_BOLD)
		nameTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 28 + 18 else 68 + namePadding).toFloat(), 11.5f, (if (LocaleController.isRTL) 68 + namePadding else 28 + 18).toFloat(), 0f))

		statusTextView = SimpleTextView(context)
		statusTextView.setTextSize(14)
		statusTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 28 else 68 + namePadding).toFloat(), 34.5f, (if (LocaleController.isRTL) 68 + namePadding else 28).toFloat(), 0f))

		if (needOption) {
			optionsButton = ImageView(context)
			optionsButton?.isFocusable = false
			optionsButton?.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null))
			optionsButton?.setImageResource(R.drawable.overflow_menu)
			optionsButton?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
			optionsButton?.scaleType = ImageView.ScaleType.CENTER

			addView(optionsButton, LayoutHelper.createFrame(60, 64, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP))

			optionsButton?.setOnClickListener {
				delegate?.onOptionsButtonCheck(this@ManageChatUserCell, true)
			}

			optionsButton?.contentDescription = context.getString(R.string.AccDescrUserOptions)
		}
	}

	fun setCustomRightImage(resId: Int) {
		customImageView = ImageView(context)
		customImageView?.setImageResource(resId)
		customImageView?.scaleType = ImageView.ScaleType.CENTER
		customImageView?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)

		addView(customImageView, LayoutHelper.createFrame(52, 64, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP))
	}

	fun setCustomImageVisible(visible: Boolean) {
		customImageView?.isVisible = visible
	}

	fun setData(`object`: Any?, name: CharSequence?, status: CharSequence?, divider: Boolean) {
		if (`object` == null) {
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

		if (optionsButton != null) {
			val visible = delegate?.onOptionsButtonCheck(this@ManageChatUserCell, false) ?: false
			optionsButton?.visibility = if (visible) VISIBLE else INVISIBLE
			nameTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) (if (visible) 46 else 28) else 68 + namePadding).toFloat(), if (status == null || status.isNotEmpty()) 11.5f else 20.5f, (if (LocaleController.isRTL) 68 + namePadding else if (visible) 46 else 28).toFloat(), 0f)
			statusTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) (if (visible) 46 else 28) else 68 + namePadding).toFloat(), 34.5f, (if (LocaleController.isRTL) 68 + namePadding else if (visible) 46 else 28).toFloat(), 0f)
		}
		else if (customImageView != null) {
			val visible = customImageView?.visibility == VISIBLE
			nameTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) (if (visible) 54 else 28) else 68 + namePadding).toFloat(), if (status == null || status.isNotEmpty()) 11.5f else 20.5f, (if (LocaleController.isRTL) 68 + namePadding else if (visible) 54 else 28).toFloat(), 0f)
			statusTextView.layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) (if (visible) 54 else 28) else 68 + namePadding).toFloat(), 34.5f, (if (LocaleController.isRTL) 68 + namePadding else if (visible) 54 else 28).toFloat(), 0f)
		}

		needDivider = divider

		setWillNotDraw(!needDivider)

		update(0)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
	}

	val userId: Long
		get() = (currentObject as? User)?.id ?: 0

	fun setStatusColors(color: Int, onlineColor: Int) {
		statusColor = color
		statusOnlineColor = onlineColor
	}

//	fun setIsAdmin(value: Boolean) {
//		isAdmin = value
//	}

	fun hasAvatarSet(): Boolean {
		return avatarImageView.imageReceiver.hasNotThumb()
	}

	fun setNameColor(color: Int) {
		nameTextView.textColor = color
	}

	fun setDividerColor(@ColorInt color: Int) {
		dividerColor = color
	}

	fun update(mask: Int) {
		val currentObject = currentObject ?: return

		when (currentObject) {
			is User -> {
				val photo = currentObject.photo?.photoSmall
				var newName: String? = null

				if (mask != 0) {
					var continueUpdate = false

					if (mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
						if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar!!.volumeId != photo!!.volumeId || lastAvatar!!.localId != photo.localId)) {
							continueUpdate = true
						}
					}

					if (!continueUpdate && mask and MessagesController.UPDATE_MASK_STATUS != 0) {
						val newStatus = (currentObject as? TLRPC.TLUser)?.status?.expires ?: 0

						if (newStatus != lastStatus) {
							continueUpdate = true
						}
					}

					if (!continueUpdate && currentName == null && lastName != null && mask and MessagesController.UPDATE_MASK_NAME != 0) {
						newName = UserObject.getUserName(currentObject)

						if (newName != lastName) {
							continueUpdate = true
						}
					}

					if (!continueUpdate) {
						return
					}
				}

				avatarDrawable.setInfo(currentObject)

				lastStatus = (currentObject as? TLRPC.TLUser)?.status?.expires ?: 0

				if (currentName != null) {
					lastName = null
					nameTextView.setText(currentName)
				}
				else {
					lastName = newName ?: UserObject.getUserName(currentObject)
					nameTextView.setText(lastName)
				}

				if (currentStatus != null) {
					statusTextView.textColor = statusColor
					statusTextView.setText(currentStatus)
				}
				else {
					if (currentObject.bot) {
						statusTextView.textColor = statusColor

						if ((currentObject as? TLRPC.TLUser)?.botChatHistory == true || isAdmin) {
							statusTextView.setText(context.getString(R.string.BotStatusRead))
						}
						else {
							statusTextView.setText(context.getString(R.string.BotStatusCantRead))
						}
					}
					else {
						if (currentObject.id == UserConfig.getInstance(currentAccount).getClientUserId() || (currentObject as? TLRPC.TLUser)?.status != null && currentObject.status!!.expires > ConnectionsManager.getInstance(currentAccount).currentTime || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentObject.id)) {
							statusTextView.textColor = statusOnlineColor
							statusTextView.setText(context.getString(R.string.Online))
						}
						else {
							statusTextView.textColor = statusColor
							statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentObject))
						}
					}
				}

				lastAvatar = photo

				avatarImageView.setForUserOrChat(currentObject, avatarDrawable)
			}

			is Chat -> {
				val photo = currentObject.photo?.photoSmall
				var newName: String? = null

				if (mask != 0) {
					var continueUpdate = false

					if (mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
						if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && (lastAvatar?.volumeId != photo?.volumeId || lastAvatar?.localId != photo?.localId)) {
							continueUpdate = true
						}
					}

					if (!continueUpdate && currentName == null && lastName != null && mask and MessagesController.UPDATE_MASK_NAME != 0) {
						newName = currentObject.title

						if (newName != lastName) {
							continueUpdate = true
						}
					}

					if (!continueUpdate) {
						return
					}
				}

				avatarDrawable.setInfo(currentObject)

				if (currentName != null) {
					lastName = null
					nameTextView.setText(currentName)
				}
				else {
					lastName = newName ?: currentObject.title
					nameTextView.setText(lastName)
				}

				if (currentStatus != null) {
					statusTextView.textColor = statusColor
					statusTextView.setText(currentStatus)
				}
				else {
					statusTextView.textColor = statusColor

					if (currentObject.participantsCount != 0) {
						if (ChatObject.isChannel(currentObject) && !currentObject.megagroup) {
							statusTextView.setText(LocaleController.formatPluralString("Subscribers", currentObject.participantsCount))
						}
						else {
							statusTextView.setText(LocaleController.formatPluralString("Members", currentObject.participantsCount))
						}
					}
					else if (currentObject.hasGeo) {
						statusTextView.setText(context.getString(R.string.MegaLocation))
					}
					else if (currentObject.username.isNullOrEmpty()) {
						statusTextView.setText(context.getString(R.string.MegaPrivate))
					}
					else {
						statusTextView.setText(context.getString(R.string.MegaPublic))
					}
				}

				lastAvatar = photo

				avatarImageView.setForUserOrChat(currentObject, avatarDrawable)
			}

			is Int -> {
				nameTextView.setText(currentName)
				statusTextView.textColor = statusColor
				statusTextView.setText(currentStatus)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SHARES
				avatarImageView.setImage(null, "50_50", avatarDrawable)
			}
		}
	}

	fun recycle() {
		avatarImageView.imageReceiver.cancelLoadImage()
	}

	fun setDelegate(manageChatUserCellDelegate: ManageChatUserCellDelegate?) {
		delegate = manageChatUserCellDelegate
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	override fun onDraw(canvas: Canvas) {
		if (needDivider) {
			if (dividerColor != 0) {
				Theme.dividerExtraPaint.color = dividerColor
			}

			canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(68f)).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(68f) else 0).toFloat(), (measuredHeight - 1).toFloat(), if (dividerColor != 0) Theme.dividerExtraPaint else Theme.dividerPaint)
		}
	}
}
