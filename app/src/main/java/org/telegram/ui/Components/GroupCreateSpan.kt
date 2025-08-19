/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController.Contact
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.User
import org.telegram.ui.ActionBar.Theme
import kotlin.math.ceil
import kotlin.math.min

class GroupCreateSpan @JvmOverloads constructor(context: Context, `object`: Any?, val contact: Contact? = null) : View(context) {
	@JvmField
	var uid: Long = 0

	@JvmField
	var key: String? = null

	private val deleteDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.delete, null)!!
	private val rect = RectF()
	private val imageReceiver: ImageReceiver
	private val nameLayout: StaticLayout
	private val avatarDrawable: AvatarDrawable
	private var textWidth = 0
	private var textX = 0f
	private var progress = 0f
	private var lastUpdateTime = 0L
	private val colors = IntArray(8)

	var isDeleting: Boolean = false
		private set

	constructor(context: Context, contact: Contact) : this(context, null, contact)

	init {
		textPaint.textSize = AndroidUtilities.dp(14f).toFloat()
		textPaint.setTypeface(Theme.TYPEFACE_DEFAULT)

		val firstName: String?
		val imageLocation: ImageLocation?
		val imageParent: Any?

		avatarDrawable = AvatarDrawable()
		avatarDrawable.setTextSize(AndroidUtilities.dp(12f))

		if (`object` is String) {
			imageLocation = null
			imageParent = null

			avatarDrawable.setSmallSize(true)

			when (`object`) {
				"contacts" -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_CONTACTS
					uid = Int.MIN_VALUE.toLong()
					firstName = context.getString(R.string.FilterContacts)
				}

				"non_contacts" -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_NON_CONTACTS
					uid = (Int.MIN_VALUE + 1).toLong()
					firstName = context.getString(R.string.FilterNonContacts)
				}

				"groups" -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS
					uid = (Int.MIN_VALUE + 2).toLong()
					firstName = context.getString(R.string.FilterGroups)
				}

				"channels" -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_CHANNELS
					uid = (Int.MIN_VALUE + 3).toLong()
					firstName = context.getString(R.string.FilterChannels)
				}

				"bots" -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_BOTS
					uid = (Int.MIN_VALUE + 4).toLong()
					firstName = context.getString(R.string.FilterBots)
				}

				"muted" -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_MUTED
					uid = (Int.MIN_VALUE + 5).toLong()
					firstName = context.getString(R.string.FilterMuted)
				}

				"read" -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_READ
					uid = (Int.MIN_VALUE + 6).toLong()
					firstName = context.getString(R.string.FilterRead)
				}

				"archived" -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_ARCHIVED
					uid = (Int.MIN_VALUE + 7).toLong()
					firstName = context.getString(R.string.FilterArchived)
				}

				else -> {
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_FILTER_ARCHIVED
					uid = (Int.MIN_VALUE + 7).toLong()
					firstName = context.getString(R.string.FilterArchived)
				}
			}
		}
		else if (`object` is User) {
			uid = `object`.id

			if (UserObject.isReplyUser(`object`)) {
				firstName = context.getString(R.string.RepliesTitle)

				avatarDrawable.setSmallSize(true)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES

				imageLocation = null
				imageParent = null
			}
			else if (UserObject.isUserSelf(`object`)) {
				firstName = context.getString(R.string.SavedMessages)

				avatarDrawable.setSmallSize(true)
				avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED

				imageLocation = null
				imageParent = null
			}
			else {
				avatarDrawable.setInfo(`object`)

				firstName = UserObject.getFirstName(`object`)
				imageLocation = ImageLocation.getForUserOrChat(`object`, ImageLocation.TYPE_SMALL)
				imageParent = `object`
			}
		}
		else if (`object` is Chat) {
			avatarDrawable.setInfo(`object`)
			uid = -`object`.id
			firstName = `object`.title
			imageLocation = ImageLocation.getForUserOrChat(`object`, ImageLocation.TYPE_SMALL)
			imageParent = `object`
		}
		else {
			avatarDrawable.setInfo(contact?.firstName, contact?.lastName)

			uid = contact?.contactId?.toLong() ?: 0L
			key = contact?.key

			firstName = if (!contact?.firstName.isNullOrEmpty()) {
				contact?.firstName
			}
			else {
				contact?.lastName
			}

			imageLocation = null
			imageParent = null
		}

		imageReceiver = ImageReceiver()
		imageReceiver.setRoundRadius(AndroidUtilities.dp(16f))
		imageReceiver.setParentView(this)
		imageReceiver.setImageCoordinates(0f, 0f, AndroidUtilities.dp(32f).toFloat(), AndroidUtilities.dp(32f).toFloat())

		val maxNameWidth = if (AndroidUtilities.isTablet()) {
			AndroidUtilities.dp((530 - 32 - 18 - 57 * 2).toFloat()) / 2
		}
		else {
			((min(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) - AndroidUtilities.dp((32 + 18 + 57 * 2).toFloat())) / 2).toInt()
		}

		val name = TextUtils.ellipsize(firstName?.replace('\n', ' ') ?: "", textPaint, maxNameWidth.toFloat(), TextUtils.TruncateAt.END)

		nameLayout = StaticLayout(name, textPaint, 1000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

		if (nameLayout.lineCount > 0) {
			textWidth = ceil(nameLayout.getLineWidth(0).toDouble()).toInt()
			textX = -nameLayout.getLineLeft(0)
		}

		imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, imageParent, 1)

		updateColors()
	}

	fun updateColors() {
		val color = avatarDrawable.color
		val back = context.getColor(R.color.light_background)
		val delete = context.getColor(R.color.white)

		colors[0] = Color.red(back)
		colors[1] = Color.red(color)
		colors[2] = Color.green(back)
		colors[3] = Color.green(color)
		colors[4] = Color.blue(back)
		colors[5] = Color.blue(color)
		colors[6] = Color.alpha(back)
		colors[7] = Color.alpha(color)

		deleteDrawable.colorFilter = PorterDuffColorFilter(delete, PorterDuff.Mode.MULTIPLY)

		backPaint.color = back
	}

	fun startDeleteAnimation() {
		if (isDeleting) {
			return
		}

		isDeleting = true
		lastUpdateTime = System.currentTimeMillis()

		invalidate()
	}

	fun cancelDeleteAnimation() {
		if (!isDeleting) {
			return
		}

		isDeleting = false
		lastUpdateTime = System.currentTimeMillis()

		invalidate()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(AndroidUtilities.dp(32f + 25f) + textWidth, AndroidUtilities.dp(32f))
	}

	override fun onDraw(canvas: Canvas) {
		if (isDeleting && progress != 1.0f || !isDeleting && progress != 0.0f) {
			val newTime = System.currentTimeMillis()
			var dt = newTime - lastUpdateTime

			if (dt < 0 || dt > 17) {
				dt = 17
			}

			if (isDeleting) {
				progress += dt / 120.0f

				if (progress >= 1.0f) {
					progress = 1.0f
				}
			}
			else {
				progress -= dt / 120.0f

				if (progress < 0.0f) {
					progress = 0.0f
				}
			}

			invalidate()
		}

		canvas.withSave {
			rect.set(0f, 0f, measuredWidth.toFloat(), AndroidUtilities.dp(32f).toFloat())

			backPaint.color = Color.argb(colors[6] + ((colors[7] - colors[6]) * progress).toInt(), colors[0] + ((colors[1] - colors[0]) * progress).toInt(), colors[2] + ((colors[3] - colors[2]) * progress).toInt(), colors[4] + ((colors[5] - colors[4]) * progress).toInt())

			drawRoundRect(rect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), backPaint)

			imageReceiver.draw(this)

			if (progress != 0f) {
				val color = avatarDrawable.color
				val alpha = Color.alpha(color) / 255.0f

				backPaint.color = color
				backPaint.alpha = (255 * progress * alpha).toInt()

				drawCircle(AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), backPaint)

				withRotation(45 * (1.0f - progress), AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat()) {
					deleteDrawable.setBounds(AndroidUtilities.dp(11f), AndroidUtilities.dp(11f), AndroidUtilities.dp(21f), AndroidUtilities.dp(21f))
					deleteDrawable.alpha = (255 * progress).toInt()
					deleteDrawable.draw(this)
				}
			}

			translate(textX + AndroidUtilities.dp((32 + 9).toFloat()), AndroidUtilities.dp(8f).toFloat())

			val text = context.getColor(R.color.dark_gray)
			val textSelected = context.getColor(R.color.white)

			textPaint.color = ColorUtils.blendARGB(text, textSelected, progress)

			nameLayout.draw(this)

		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.text = nameLayout.text

		if (isDeleting) {
			info.addAction(AccessibilityAction(AccessibilityAction.ACTION_CLICK.id, context.getString(R.string.Delete)))
		}
	}

	companion object {
		private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	}
}
