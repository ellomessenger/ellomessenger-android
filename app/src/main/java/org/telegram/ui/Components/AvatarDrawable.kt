/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.tlrpc.ChatInvite
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import kotlin.math.abs

class AvatarDrawable() : Drawable() {
	private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val stringBuilder = StringBuilder(5)
	private var textLayout: StaticLayout? = null
	private var textWidth = 0f
	private var textHeight = 0f
	private var textLeft = 0f
	private var drawDeleted = false
	private var drawLocal = false
	private var archivedAvatarProgress = 0f
	private var smallSize = false
	private var innerAlpha = 255
	private var localImage: Drawable? = null
	var avatarType = AVATAR_TYPE_NORMAL
	var color = 0
	var shouldDrawPlaceholder = true
	var shouldDrawStroke = false

	init {
		namePaint.isFakeBoldText = true
		namePaint.textSize = AndroidUtilities.dp(18f).toFloat()
	}

	constructor(user: User?) : this() {
		drawDeleted = UserObject.isDeleted(user)
		updateLocalImageState(user, null)
		setInfo(user?.first_name, user?.last_name, null)
	}

	constructor(chat: Chat?) : this() {
		updateLocalImageState(null, chat)
		setInfo(chat?.title, null, null)
	}

	fun setInfo(user: User?) {
		drawDeleted = UserObject.isDeleted(user)
		updateLocalImageState(user, null)
		setInfo(user?.first_name, user?.last_name, null)
	}

	private fun updateLocalImageState(user: User?, chat: Chat?) {
		val id = user?.id ?: chat?.id ?: return

		when (id) {

			BuildConfig.SUPPORT_BOT_ID, 333000L, 42777L -> {
				shouldDrawPlaceholder = false
				drawLocal = true
				localImage = ResourcesCompat.getDrawable(ApplicationLoader.applicationContext.resources, R.drawable.support_bot_avatar, null)
			}

			BuildConfig.NOTIFICATIONS_BOT_ID -> {
				shouldDrawPlaceholder = false
				drawLocal = true
				localImage = ResourcesCompat.getDrawable(ApplicationLoader.applicationContext.resources, R.drawable.ic_notifications_bot_avatar, null)
			}

			else -> {
				shouldDrawPlaceholder = true
				drawLocal = false
				localImage = null
			}
		}
	}

	fun setInfo(info: TLObject?) {
		when (info) {
			is User -> setInfo(info)
			is Chat -> setInfo(info)
			is ChatInvite -> setInfo(info)
		}
	}

	fun setSmallSize(value: Boolean) {
		smallSize = value
	}

	fun setArchivedAvatarHiddenProgress(progress: Float) {
		archivedAvatarProgress = progress
	}

	fun setInfo(chat: Chat?) {
		updateLocalImageState(null, chat)
		setInfo(chat?.title, null, null)
	}

	fun setInfo(chat: ChatInvite?) {
		setInfo(chat?.title, null, null)
	}

	fun setTextSize(size: Int) {
		namePaint.textSize = size.toFloat()
	}

	@JvmOverloads
	fun setInfo(firstName: String? = null, lastName: String? = null, custom: String? = null, fillColor: Int? = null) {
		var innerFirstName = firstName
		var innerLastName = lastName

		color = fillColor ?: ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.brand, null)

		avatarType = AVATAR_TYPE_NORMAL
		drawDeleted = false

		if (innerFirstName.isNullOrEmpty()) {
			innerFirstName = innerLastName
			innerLastName = null
		}

		stringBuilder.setLength(0)

		if (custom != null) {
			stringBuilder.append(custom)
		}
		else {
			if (!innerFirstName.isNullOrEmpty()) {
				stringBuilder.appendCodePoint(innerFirstName.codePointAt(0))
			}

			if (!innerLastName.isNullOrEmpty()) {
				var lastch: Int? = null

				for (a in innerLastName.length - 1 downTo 0) {
					if (lastch != null && innerLastName[a] == ' ') {
						break
					}

					lastch = innerLastName.codePointAt(a)
				}

				stringBuilder.append("\u200C")
				stringBuilder.appendCodePoint(lastch!!)
			}
			else if (!innerFirstName.isNullOrEmpty()) {
				for (a in innerFirstName.length - 1 downTo 0) {
					if (innerFirstName[a] == ' ') {
						if (a != innerFirstName.length - 1 && innerFirstName[a + 1] != ' ') {
							stringBuilder.append("\u200C")
							stringBuilder.appendCodePoint(innerFirstName.codePointAt(a + 1))
							break
						}
					}
				}
			}
		}

		if (stringBuilder.isNotEmpty()) {
			val text = stringBuilder.toString().uppercase()

			try {
				textLayout = StaticLayout(text, namePaint, AndroidUtilities.dp(100f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				if (textLayout!!.lineCount > 0) {
					textLeft = textLayout!!.getLineLeft(0)
					textWidth = textLayout!!.getLineWidth(0)
					textHeight = textLayout!!.getLineBottom(0).toFloat()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
		else {
			textLayout = null
		}
	}

	override fun draw(canvas: Canvas) {
		val bounds = bounds
		val size = bounds.width()
		val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)

		if (shouldDrawStroke) {
			strokePaint.apply {
				style = Paint.Style.STROKE
				color = ApplicationLoader.applicationContext.getColor(R.color.background)
				strokeWidth = AndroidUtilities.dp(0.5f).toFloat()
			}
		}

		namePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, innerAlpha)

		if (avatarType == AVATAR_TYPE_SAVED || avatarType == AVATAR_TYPE_REPLIES) {
			val colors = intArrayOf(
					ApplicationLoader.applicationContext.getColor(R.color.avatar_blue),
					ApplicationLoader.applicationContext.getColor(R.color.avatar_light_blue)
			)
			val positions = floatArrayOf(0.312f, 0.76f)

			Theme.avatar_backgroundPaint.shader = LinearGradient(0f, 0f, bounds.height().toFloat(), 0f, colors, positions, Shader.TileMode.CLAMP)
		} else {
			Theme.avatar_backgroundPaint.shader = null
			Theme.avatar_backgroundPaint.color = ColorUtils.setAlphaComponent(if (drawLocal) ApplicationLoader.applicationContext.getColor(R.color.background) else color, innerAlpha)
		}

		canvas.save()
		canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())

		if (shouldDrawPlaceholder) {
			val radius = size.toFloat() * 0.45f
			canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, Theme.avatar_backgroundPaint)
			if (shouldDrawStroke) {
				canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, strokePaint)
			}
		}

		val localImage = localImage

		if (avatarType == AVATAR_TYPE_ARCHIVED) {
			if (archivedAvatarProgress != 0f) {
				Theme.avatar_backgroundPaint.color = ColorUtils.setAlphaComponent(color, innerAlpha)

				canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f * archivedAvatarProgress, Theme.avatar_backgroundPaint)
				if (shouldDrawStroke) {
					canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f * archivedAvatarProgress, strokePaint)
				}

				if (Theme.dialogs_archiveAvatarDrawableRecolored) {
					Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors()
					Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", Theme.getNonAnimatedColor(Theme.key_avatar_backgroundArchived))
					Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", Theme.getNonAnimatedColor(Theme.key_avatar_backgroundArchived))
					Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors()
					Theme.dialogs_archiveAvatarDrawableRecolored = false
				}
			}
			else {
				if (!Theme.dialogs_archiveAvatarDrawableRecolored) {
					Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors()
					Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", color)
					Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", color)
					Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors()
					Theme.dialogs_archiveAvatarDrawableRecolored = true
				}
			}

			val w = Theme.dialogs_archiveAvatarDrawable.intrinsicWidth
			val h = Theme.dialogs_archiveAvatarDrawable.intrinsicHeight
			val x = (size - w) / 2
			val y = (size - h) / 2

			canvas.save()

			Theme.dialogs_archiveAvatarDrawable.setBounds(x, y, x + w, y + h)
			Theme.dialogs_archiveAvatarDrawable.draw(canvas)

			canvas.restore()
		}
		else if (avatarType != AVATAR_TYPE_NORMAL) {
			val drawable = when (avatarType) {
				AVATAR_TYPE_REPLIES -> Theme.avatarDrawables[11]
				AVATAR_TYPE_SAVED -> Theme.avatarDrawables[0]
				AVATAR_TYPE_SHARES -> Theme.avatarDrawables[10]
				AVATAR_TYPE_FILTER_CONTACTS -> Theme.avatarDrawables[2]
				AVATAR_TYPE_FILTER_NON_CONTACTS -> Theme.avatarDrawables[3]
				AVATAR_TYPE_FILTER_GROUPS -> Theme.avatarDrawables[4]
				AVATAR_TYPE_FILTER_CHANNELS -> Theme.avatarDrawables[5]
				AVATAR_TYPE_FILTER_BOTS -> Theme.avatarDrawables[6]
				AVATAR_TYPE_FILTER_MUTED -> Theme.avatarDrawables[7]
				AVATAR_TYPE_FILTER_READ -> Theme.avatarDrawables[8]
				else -> Theme.avatarDrawables[9]
			}

			if (drawable != null) {
				var w = drawable.intrinsicWidth
				var h = drawable.intrinsicHeight

				if (smallSize) {
					w = (w.toFloat() * 0.8f).toInt()
					h = (h.toFloat() * 0.8f).toInt()
				}

				val x = (size - w) / 2
				val y = (size - h) / 2

				drawable.setBounds(x, y, x + w, y + h)

				if (innerAlpha != 255) {
					drawable.alpha = innerAlpha
					drawable.draw(canvas)
					drawable.alpha = 255
				}
				else {
					drawable.draw(canvas)
				}
			}
		}
		else if (drawDeleted && Theme.avatarDrawables[1] != null) {
			var w = Theme.avatarDrawables[1].intrinsicWidth
			var h = Theme.avatarDrawables[1].intrinsicHeight

			if (w > size - AndroidUtilities.dp(6f) || h > size - AndroidUtilities.dp(6f)) {
				val scale = size / AndroidUtilities.dp(50f).toFloat()
				w = (w.toFloat() * scale).toInt()
				h = (h.toFloat() * scale).toInt()
			}

			val x = (size - w) / 2
			val y = (size - h) / 2

			Theme.avatarDrawables[1].setBounds(x, y, x + w, y + h)
			Theme.avatarDrawables[1].draw(canvas)
		}
		else if (drawLocal && localImage != null) {
			var w = localImage.intrinsicWidth
			var h = localImage.intrinsicHeight

			if (w > size - AndroidUtilities.dp(6f) || h > size - AndroidUtilities.dp(6f)) {
				val scale = size / AndroidUtilities.dp(50f).toFloat()
				w = (w.toFloat() * scale).toInt()
				h = (h.toFloat() * scale).toInt()
			}

			val x = (size - w) / 2
			val y = (size - h) / 2

			localImage.setBounds(x, y, x + w, y + h)
			localImage.draw(canvas)
		}
		else {
			if (shouldDrawPlaceholder && textLayout != null) {
				val scale = size / AndroidUtilities.dp(50f).toFloat()
				canvas.scale(scale, scale, size / 2f, size / 2f)
				canvas.translate((size - textWidth) / 2 - textLeft, (size - textHeight) / 2)
				textLayout?.draw(canvas)
			}
		}

		canvas.restore()
	}

	override fun setAlpha(alpha: Int) {
		this.innerAlpha = alpha
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		// unused
	}

	@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
	override fun getOpacity(): Int {
		return PixelFormat.TRANSPARENT
	}

	override fun getIntrinsicWidth(): Int {
		return 0
	}

	override fun getIntrinsicHeight(): Int {
		return 0
	}

	companion object {
		const val AVATAR_TYPE_NORMAL = 0
		const val AVATAR_TYPE_SAVED = 1
		const val AVATAR_TYPE_ARCHIVED = 2
		const val AVATAR_TYPE_SHARES = 3
		const val AVATAR_TYPE_REPLIES = 12
		const val AVATAR_TYPE_FILTER_CONTACTS = 4
		const val AVATAR_TYPE_FILTER_NON_CONTACTS = 5
		const val AVATAR_TYPE_FILTER_GROUPS = 6
		const val AVATAR_TYPE_FILTER_CHANNELS = 7
		const val AVATAR_TYPE_FILTER_BOTS = 8
		const val AVATAR_TYPE_FILTER_MUTED = 9
		const val AVATAR_TYPE_FILTER_READ = 10
		const val AVATAR_TYPE_FILTER_ARCHIVED = 11
		// const val AVATAR_TYPE_REGISTER = 13

		private fun getColorIndex(id: Long): Int {
			return if (id in 0..6) {
				id.toInt()
			}
			else {
				abs(id % Theme.keys_avatar_background.size).toInt()
			}
		}

		@JvmStatic
		fun getColorForId(id: Long): Int {
			return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)])
		}

//		@JvmStatic
//		fun getIconColorForId(): Int {
//			return Theme.getColor(Theme.key_avatar_actionBarIconBlue)
//		}

		fun getProfileColorForId(id: Long): Int {
			return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)])
		}

//		@JvmStatic
//		fun getProfileTextColorForId(): Int {
//			return Theme.getColor(Theme.key_avatar_subtitleInProfileBlue)
//		}

//		@JvmStatic
//		fun getProfileBackColorForId(): Int {
//			return Theme.getColor(Theme.key_avatar_backgroundActionBarBlue)
//		}

		fun getNameColorNameForId(id: Long): String {
			return Theme.keys_avatar_nameInMessage[getColorIndex(id)]
		}
	}
}
