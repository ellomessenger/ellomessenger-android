/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class GroupCreateUserCell @JvmOverloads constructor(context: Context, private val checkBoxType: Int, private val padding: Int, private val showSelfAsSaved: Boolean, private val forceDarkTheme: Boolean = false) : FrameLayout(context) {
	private val avatarImageView = BackupImageView(context)
	private val nameTextView = SimpleTextView(context)
	private val statusTextView = SimpleTextView(context)
	private var checkBox: CheckBox2? = null
	private val avatarDrawable = AvatarDrawable()
	private var currentName: CharSequence? = null
	private var currentStatus: CharSequence? = null
	private val currentAccount = UserConfig.selectedAccount
	private var lastName: String? = null
	private var lastStatus = 0
	private val lastAvatar: FileLocation? = null
	private var drawDivider = false
	private var animator: ValueAnimator? = null
	private var isChecked = false
	private var checkProgress = 0f
	private var paint: Paint? = null

	var `object`: Any? = null
		private set

	init {
		avatarImageView.setRoundRadius(AndroidUtilities.dp(24f))

		addView(avatarImageView, createFrame(46, 46f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 0f else (13f + padding), 6f, if (LocaleController.isRTL) 13f + padding else 0f, 0f))

		nameTextView.textColor = Theme.getColor(if (forceDarkTheme) Theme.key_voipgroup_nameText else Theme.key_windowBackgroundWhiteBlackText)
		nameTextView.setTypeface(Theme.TYPEFACE_BOLD)
		nameTextView.setTextSize(16)
		nameTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(nameTextView, createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, ((if (LocaleController.isRTL) 28 else 72) + padding).toFloat(), 10f, ((if (LocaleController.isRTL) 72 else 28) + padding).toFloat(), 0f))

		statusTextView.setTextSize(14)
		statusTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(statusTextView, createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, ((if (LocaleController.isRTL) 28 else 72) + padding).toFloat(), 32f, ((if (LocaleController.isRTL) 72 else 28) + padding).toFloat(), 0f))

		if (checkBoxType == 1) {
			checkBox = CheckBox2(context, 21)
			checkBox?.setColor(0, context.getColor(R.color.background), context.getColor(R.color.brand))
			checkBox?.setDrawUnchecked(false)
			checkBox?.setDrawBackgroundAsArc(3)

			addView(checkBox, createFrame(24, 24f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, if (LocaleController.isRTL) 0f else 40f + padding.toFloat(), 33f, if (LocaleController.isRTL) 39f + padding else 0f, 0f))
		}
		else if (checkBoxType == 2) {
			paint = Paint(Paint.ANTI_ALIAS_FLAG)
			paint?.style = Paint.Style.STROKE
			paint?.strokeWidth = AndroidUtilities.dp(2f).toFloat()
		}

		setWillNotDraw(false)
	}

	fun setObject(`object`: TLObject?, name: CharSequence?, status: CharSequence?, drawDivider: Boolean) {
		setObject(`object`, name, status)
		this.drawDivider = drawDivider
	}

	fun setObject(`object`: Any?, name: CharSequence?, status: CharSequence?) {
		this.`object` = `object`
		currentStatus = status
		currentName = name
		drawDivider = false
		update(0)
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		if (checkBox != null) {
			checkBox?.setChecked(checked, animated)
		}
		else if (checkBoxType == 2) {
			if (isChecked == checked) {
				return
			}

			isChecked = checked

			animator?.cancel()

			if (animated) {
				animator = ValueAnimator.ofFloat(0.0f, 1.0f)

				animator?.addUpdateListener {
					val v = it.animatedValue as Float
					val scale = if (isChecked) 1.0f - 0.18f * v else 0.82f + 0.18f * v
					avatarImageView.scaleX = scale
					avatarImageView.scaleY = scale
					checkProgress = if (isChecked) v else 1.0f - v
					invalidate()
				}

				animator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						animator = null
					}
				})

				animator?.duration = 180
				animator?.interpolator = CubicBezierInterpolator.EASE_OUT
				animator?.start()
			}
			else {
				avatarImageView.scaleX = if (isChecked) 0.82f else 1.0f
				avatarImageView.scaleY = if (isChecked) 0.82f else 1.0f

				checkProgress = if (isChecked) 1.0f else 0.0f
			}

			invalidate()
		}
	}

	fun setCheckBoxEnabled(enabled: Boolean) {
		checkBox?.isEnabled = enabled
	}

	fun isChecked(): Boolean {
		return checkBox?.isChecked ?: isChecked
	}

	fun setDrawDivider(value: Boolean) {
		drawDivider = value
		invalidate()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(if (`object` is String) 50f else 58f), MeasureSpec.EXACTLY))
	}

	fun recycle() {
		avatarImageView.imageReceiver.cancelLoadImage()
	}

	fun update(mask: Int) {
		if (`object` == null) {
			return
		}

		var photo: FileLocation? = null
		var newName: String? = null

		if (`object` is String) {
			(nameTextView.layoutParams as LayoutParams).topMargin = AndroidUtilities.dp(15f)
			avatarImageView.layoutParams.height = AndroidUtilities.dp(38f)
			avatarImageView.layoutParams.width = avatarImageView.layoutParams.height

			checkBox?.updateLayoutParams<LayoutParams> {
				topMargin = AndroidUtilities.dp(25f)

				if (LocaleController.isRTL) {
					rightMargin = AndroidUtilities.dp(31f)
				}
				else {
					leftMargin = AndroidUtilities.dp(32f)
				}
			}

			when (`object` as String) {
				"contacts" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS
				"non_contacts" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_NON_CONTACTS
				"groups" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS
				"channels" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS
				"bots" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_BOTS
				"muted" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_MUTED
				"read" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_READ
				"archived" -> avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_ARCHIVED
			}

			lastName = null

			nameTextView.setText(currentName, true)

			statusTextView.setText(null)

			avatarImageView.setImage(null, "50_50", avatarDrawable)
		}
		else {
			if (currentStatus != null && TextUtils.isEmpty(currentStatus)) {
				(nameTextView.layoutParams as LayoutParams).topMargin = AndroidUtilities.dp(19f)
			}
			else {
				(nameTextView.layoutParams as LayoutParams).topMargin = AndroidUtilities.dp(10f)
			}

			avatarImageView.layoutParams.height = AndroidUtilities.dp(46f)
			avatarImageView.layoutParams.width = avatarImageView.layoutParams.height

			checkBox?.updateLayoutParams<LayoutParams> {
				topMargin = AndroidUtilities.dp(33f) + padding

				if (LocaleController.isRTL) {
					rightMargin = AndroidUtilities.dp(39f) + padding
				}
				else {
					leftMargin = AndroidUtilities.dp(40f) + padding
				}
			}

			if (`object` is User) {
				val currentUser = `object` as User?

				if (showSelfAsSaved && isUserSelf(currentUser)) {
					nameTextView.setText(context.getString(R.string.SavedMessages), true)
					statusTextView.setText(null)
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
					avatarImageView.setImage(null, "50_50", avatarDrawable, currentUser)
					(nameTextView.layoutParams as LayoutParams).topMargin = AndroidUtilities.dp(19f)
					return
				}

				if (currentUser?.photo != null) {
					photo = currentUser.photo?.photo_small
				}

				if (mask != 0) {
					var continueUpdate = false

					if (mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
						if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
							continueUpdate = true
						}
					}

					if (currentUser != null && currentStatus == null && !continueUpdate && mask and MessagesController.UPDATE_MASK_STATUS != 0) {
						val newStatus = currentUser.status?.expires ?: 0

						if (newStatus != lastStatus) {
							continueUpdate = true
						}
					}

					if (!continueUpdate && currentName == null && lastName != null && mask and MessagesController.UPDATE_MASK_NAME != 0) {
						newName = getUserName(currentUser)

						if (newName != lastName) {
							continueUpdate = true
						}
					}

					if (!continueUpdate) {
						return
					}
				}

				avatarDrawable.setInfo(currentUser)

				lastStatus = currentUser?.status?.expires ?: 0

				if (currentName != null) {
					lastName = null
					nameTextView.setText(currentName, true)
				}
				else {
					lastName = newName ?: getUserName(currentUser)
					nameTextView.setText(lastName)
				}

				if (currentStatus == null) {
					if (currentUser?.bot == true) {
						statusTextView.textColor = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
						statusTextView.setText(context.getString(R.string.Bot))
					}
					else {
						if (currentUser?.id == getInstance(currentAccount).getClientUserId() || (currentUser?.status?.expires ?: 0) > ConnectionsManager.getInstance(currentAccount).currentTime || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser?.id)) {
							statusTextView.textColor = ResourcesCompat.getColor(context.resources, R.color.brand, null)
							statusTextView.setText(context.getString(R.string.Online))
						}
						else {
							statusTextView.textColor = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
							statusTextView.setText(LocaleController.formatUserStatus(currentAccount, currentUser))
						}
					}
				}

				avatarImageView.setForUserOrChat(currentUser, avatarDrawable)
			}
			else {
				val currentChat = `object` as Chat

				if (currentChat.photo != null) {
					photo = currentChat.photo.photo_small
				}

				if (mask != 0) {
					var continueUpdate = false

					if (mask and MessagesController.UPDATE_MASK_AVATAR != 0) {
						if (lastAvatar != null && photo == null || lastAvatar == null && photo != null || lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
							continueUpdate = true
						}
					}

					if (!continueUpdate && currentName == null && lastName != null && mask and MessagesController.UPDATE_MASK_NAME != 0) {
						newName = currentChat.title

						if (newName != lastName) {
							continueUpdate = true
						}
					}

					if (!continueUpdate) {
						return
					}
				}

				avatarDrawable.setInfo(currentChat)

				if (currentName != null) {
					lastName = null
					nameTextView.setText(currentName, true)
				}
				else {
					lastName = newName ?: currentChat.title
					nameTextView.setText(lastName)
				}

				if (currentStatus == null) {
					statusTextView.textColor = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)

					if (currentChat.participants_count != 0) {
						if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
							statusTextView.setText(LocaleController.formatPluralString("Subscribers", currentChat.participants_count))
						}
						else {
							statusTextView.setText(LocaleController.formatPluralString("Members", currentChat.participants_count))
						}
					}
					else if (currentChat.has_geo) {
						statusTextView.setText(context.getString(R.string.MegaLocation))
					}
					else if (currentChat.username.isNullOrEmpty()) {
						if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
							statusTextView.setText(context.getString(R.string.ChannelPrivate))
						}
						else {
							statusTextView.setText(context.getString(R.string.MegaPrivate))
						}
					}
					else {
						if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
							statusTextView.setText(context.getString(R.string.ChannelPublic))
						}
						else {
							statusTextView.setText(context.getString(R.string.MegaPublic))
						}
					}
				}

				avatarImageView.setForUserOrChat(currentChat, avatarDrawable)
			}
		}

		if (currentStatus != null) {
			statusTextView.setText(currentStatus, true)
			statusTextView.textColor = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		if (checkBoxType == 2 && (isChecked || checkProgress > 0.0f)) {
			paint?.color = Theme.getColor(Theme.key_checkboxSquareBackground) // TODO: replace color
			val cx = (avatarImageView.left + avatarImageView.measuredWidth / 2).toFloat()
			val cy = (avatarImageView.top + avatarImageView.measuredHeight / 2).toFloat()
			canvas.drawCircle(cx, cy, AndroidUtilities.dp(18f) + AndroidUtilities.dp(4f) * checkProgress, paint!!)
		}

		if (drawDivider) {
			val start = AndroidUtilities.dp(if (LocaleController.isRTL) 0f else 72f + padding.toFloat())
			val end = measuredWidth - AndroidUtilities.dp(if (!LocaleController.isRTL) 0f else 72f + padding.toFloat())

			// TODO: replace color
			if (forceDarkTheme) {
				Theme.dividerExtraPaint.color = Theme.getColor(Theme.key_voipgroup_actionBar)
				canvas.drawRect(start.toFloat(), (measuredHeight - 1).toFloat(), end.toFloat(), measuredHeight.toFloat(), Theme.dividerExtraPaint)
			}
			else {
				canvas.drawRect(start.toFloat(), (measuredHeight - 1).toFloat(), end.toFloat(), measuredHeight.toFloat(), Theme.dividerPaint)
			}
		}
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		if (isChecked()) {
			info.isCheckable = true
			info.isChecked = true
		}
	}
}
