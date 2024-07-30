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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.URLSpanNoUnderline

class AdministeredChannelCell(context: Context, needCheck: Boolean, padding: Int, onClickListener: OnClickListener?) : FrameLayout(context) {
	private val avatarDrawable: AvatarDrawable = AvatarDrawable()
	private val avatarImageView: BackupImageView
	private val currentAccount = UserConfig.selectedAccount
	private var deleteButton: ImageView? = null
	private var isLast = false
	val nameTextView: SimpleTextView
	val statusTextView: SimpleTextView
	var checkBox: CheckBox2? = null

	var currentChannel: Chat? = null
		private set

	init {
		avatarImageView = BackupImageView(context)
		avatarImageView.setRoundRadius(AndroidUtilities.dp(24f))

		addView(avatarImageView, LayoutHelper.createFrame(48, 48f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 12 + padding).toFloat(), 6f, (if (LocaleController.isRTL) 12 + padding else 0).toFloat(), 6f))

		if (needCheck) {
			checkBox = CheckBox2(context, 21)
			checkBox?.setColor(0, context.getColor(R.color.background), context.getColor(R.color.brand))
			checkBox?.setDrawUnchecked(false)
			checkBox?.setDrawBackgroundAsArc(3)

			addView(checkBox, LayoutHelper.createFrame(24, 24f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 42 + padding).toFloat(), 32f, (if (LocaleController.isRTL) 42 + padding else 0).toFloat(), 0f))
		}

		val leftPadding = if (onClickListener == null) 24 else 62

		nameTextView = SimpleTextView(context)
		nameTextView.textColor = context.getColor(R.color.text)
		nameTextView.setTextSize(17)
		nameTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) leftPadding else 73 + padding).toFloat(), 9.5f, (if (LocaleController.isRTL) 73 + padding else leftPadding).toFloat(), 0f))

		statusTextView = SimpleTextView(context)
		statusTextView.setTextSize(14)
		statusTextView.textColor = context.getColor(R.color.dark_gray)
		statusTextView.setLinkTextColor(context.getColor(R.color.brand))
		statusTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) leftPadding else 73 + padding).toFloat(), 32.5f, (if (LocaleController.isRTL) 73 + padding else leftPadding).toFloat(), 6f))

		if (onClickListener != null) {
			deleteButton = ImageView(context)
			deleteButton?.scaleType = ImageView.ScaleType.CENTER
			deleteButton?.setImageResource(R.drawable.msg_panel_clear)
			deleteButton?.setOnClickListener(onClickListener)
			deleteButton?.background = Theme.createSelectorDrawable(context.getColor(R.color.light_background))
			deleteButton?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.dark_gray), PorterDuff.Mode.SRC_IN)

			addView(deleteButton, LayoutHelper.createFrame(48, 48f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP, (if (LocaleController.isRTL) 7 else 0).toFloat(), 6f, (if (LocaleController.isRTL) 0 else 7).toFloat(), 0f))
		}
	}

	fun setChannel(channel: Chat, last: Boolean) {
		val url = MessagesController.getInstance(currentAccount).linkPrefix + "/"

		currentChannel = channel

		avatarDrawable.setInfo(channel)

		nameTextView.setText(channel.title)

		val stringBuilder = SpannableStringBuilder(url + channel.username)
		stringBuilder.setSpan(URLSpanNoUnderline(""), url.length, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

		statusTextView.setText(stringBuilder)

		avatarImageView.setForUserOrChat(channel, avatarDrawable)

		isLast = last
	}

	fun update() {
		avatarDrawable.setInfo(currentChannel)
		avatarImageView.invalidate()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((60 + if (isLast) 12 else 0).toFloat()), MeasureSpec.EXACTLY))
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		checkBox?.setChecked(checked, animated)
	}
}
