/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.Emoji
import org.telegram.messenger.MediaDataController.KeywordResult
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper

class MentionCell(context: Context) : LinearLayout(context) {
	private val imageView: BackupImageView
	private val nameTextView: TextView
	private val usernameTextView: TextView
	private val avatarDrawable: AvatarDrawable
	private var emojiDrawable: Drawable? = null
	private var needsDivider = false
	private var attached = false

	init {
		orientation = HORIZONTAL

		avatarDrawable = AvatarDrawable()
		avatarDrawable.setTextSize(AndroidUtilities.dp(12f))

		imageView = BackupImageView(context)
		imageView.setRoundRadius(AndroidUtilities.dp(14f))

		addView(imageView, LayoutHelper.createLinear(28, 28, 12f, 4f, 0f, 0f))

		imageView.updateLayoutParams<LayoutParams> {
			weight = 0f
		}

		nameTextView = TextView(context)
		nameTextView.setTextColor(context.getColor(R.color.text))
		nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		nameTextView.isSingleLine = true
		nameTextView.gravity = Gravity.LEFT
		nameTextView.ellipsize = TextUtils.TruncateAt.END

		addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 0, 0))

		nameTextView.updateLayoutParams<LayoutParams> {
			weight = 0f
		}

		usernameTextView = TextView(context)
		usernameTextView.setTextColor(context.getColor(R.color.dark_gray))
		usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		usernameTextView.isSingleLine = true
		usernameTextView.gravity = Gravity.LEFT
		usernameTextView.ellipsize = TextUtils.TruncateAt.END

		addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 8, 0))

		usernameTextView.updateLayoutParams<LayoutParams> {
			weight = 1f
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36f), MeasureSpec.EXACTLY))
	}

	fun setUser(user: User?) {
		resetEmojiSuggestion()

		if (user == null) {
			nameTextView.text = ""
			usernameTextView.text = ""
			imageView.setImageDrawable(null)
			return
		}

		avatarDrawable.setInfo(user)

		if (user.photo?.photo_small != null) {
			imageView.setForUserOrChat(user, avatarDrawable)
		}
		else {
			imageView.setImageDrawable(avatarDrawable)
		}

		nameTextView.text = UserObject.getUserName(user)

		if (user.username != null) {
			usernameTextView.text = "@${user.username}"
		}
		else {
			usernameTextView.text = ""
		}

		imageView.visible()
		usernameTextView.visible()
	}

	fun setDivider(enabled: Boolean) {
		if (enabled != needsDivider) {
			needsDivider = enabled
			setWillNotDraw(!needsDivider)
			invalidate()
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		if (needsDivider) {
			canvas.drawLine(AndroidUtilities.dp(52f).toFloat(), (height - 1).toFloat(), (width - AndroidUtilities.dp(8f)).toFloat(), (height - 1).toFloat(), Theme.dividerPaint)
		}
	}

	fun setChat(chat: TLRPC.Chat?) {
		resetEmojiSuggestion()

		if (chat == null) {
			nameTextView.text = ""
			usernameTextView.text = ""
			imageView.setImageDrawable(null)
			return
		}

		avatarDrawable.setInfo(chat)

		if (chat.photo?.photo_small != null) {
			imageView.setForUserOrChat(chat, avatarDrawable)
		}
		else {
			imageView.setImageDrawable(avatarDrawable)
		}

		nameTextView.text = chat.title

		if (chat.username != null) {
			usernameTextView.text = "@${chat.username}"
		}
		else {
			usernameTextView.text = ""
		}

		imageView.visible()
		usernameTextView.visible()
	}

	fun setText(text: String?) {
		resetEmojiSuggestion()

		imageView.gone()
		usernameTextView.gone()
		nameTextView.text = text
	}

	override fun invalidate() {
		super.invalidate()
		nameTextView.invalidate()
	}

	private fun resetEmojiSuggestion() {
		usernameTextView.gravity = Gravity.LEFT

		nameTextView.setPadding(0, 0, 0, 0)

		if (emojiDrawable != null) {
			if (emojiDrawable is AnimatedEmojiDrawable) {
				(emojiDrawable as AnimatedEmojiDrawable).removeView(this)
			}

			emojiDrawable = null

			invalidate()
		}
	}

	fun setEmojiSuggestion(suggestion: KeywordResult) {
		usernameTextView.gravity = Gravity.LEFT
		imageView.gone()
		usernameTextView.gone()

		if (suggestion.emoji != null && suggestion.emoji?.startsWith("animated_") == true) {
			try {
				if (emojiDrawable is AnimatedEmojiDrawable) {
					(emojiDrawable as AnimatedEmojiDrawable).removeView(this)
					emojiDrawable = null
				}

				val documentId = suggestion.emoji?.substring(9)?.toLong() ?: 0L

				emojiDrawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, documentId)

				if (attached) {
					(emojiDrawable as AnimatedEmojiDrawable).addView(this)
				}
			}
			catch (ignore: Exception) {
				emojiDrawable = Emoji.getEmojiDrawable(suggestion.emoji)
			}
		}
		else {
			emojiDrawable = Emoji.getEmojiDrawable(suggestion.emoji)
		}

		if (emojiDrawable == null) {
			nameTextView.setPadding(0, 0, 0, 0)
			nameTextView.text = StringBuilder().append(suggestion.emoji).append(":  ").append(suggestion.keyword)
		}
		else {
			nameTextView.setPadding(AndroidUtilities.dp(22f), 0, 0, 0)
			nameTextView.text = StringBuilder().append(":  ").append(suggestion.keyword)
		}
	}

	override fun dispatchDraw(canvas: Canvas) {
		super.dispatchDraw(canvas)

		emojiDrawable?.let {
			val sz = AndroidUtilities.dp((if (it is AnimatedEmojiDrawable) 24 else 20).toFloat())
			val offsetX = AndroidUtilities.dp((if (it is AnimatedEmojiDrawable) -2 else 0).toFloat())

			it.setBounds(nameTextView.left + offsetX, (nameTextView.top + nameTextView.bottom - sz) / 2, nameTextView.left + offsetX + sz, (nameTextView.top + nameTextView.bottom + sz) / 2)

			if (it is AnimatedEmojiDrawable) {
				it.setTime(System.currentTimeMillis())
			}

			it.draw(canvas)
		}
	}

	fun setBotCommand(command: String?, help: String?, user: User?) {
		resetEmojiSuggestion()

		if (user != null) {
			avatarDrawable.setInfo(user)

			if (user.photo?.photo_small != null) {
				imageView.setForUserOrChat(user, avatarDrawable)
			}
			else {
				when (user.id) {
					BuildConfig.AI_BOT_ID -> {
						imageView.setImageResource(R.drawable.ai_bot_avatar)
					}

					BuildConfig.SUPPORT_BOT_ID -> {
						imageView.setImageResource(R.drawable.support_bot_avatar)
					}

					BuildConfig.PHOENIX_BOT_ID -> {
						imageView.setImageResource(R.drawable.ai_phoenix_bot)
					}

					BuildConfig.BUSINESS_BOT_ID -> {
						imageView.setImageResource(R.drawable.business_ai_bot_avatar)
					}

					BuildConfig.CANCER_BOT_ID -> {
						imageView.setImageResource(R.drawable.cancer_ai_bot_avatar)
					}

					else -> {
						imageView.setImageDrawable(avatarDrawable)
					}
				}
			}

			imageView.visible()
		}
		else {
			imageView.gone()
		}

		nameTextView.text = command
		usernameTextView.text = Emoji.replaceEmoji(help, usernameTextView.paint.fontMetricsInt, false)

		usernameTextView.visible()

		usernameTextView.gravity = Gravity.RIGHT
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attached = false
		(emojiDrawable as? AnimatedEmojiDrawable)?.removeView(this)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		(emojiDrawable as? AnimatedEmojiDrawable)?.addView(this)
	}
}
