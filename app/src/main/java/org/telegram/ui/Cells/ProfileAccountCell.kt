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
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.setPadding
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper

class ProfileAccountCell(context: Context) : FrameLayout(context) {
	private val avatarDrawable: AvatarDrawable = AvatarDrawable()
	private val checkImageView = ImageView(context)
	private val imageView = BackupImageView(context)
	private val padding = 16
	private val textView = TextView(context)
	private val unreadCountLabel = TextView(context)
	private var shouldDrawDivider = true

	init {
		avatarDrawable.setTextSize(AndroidUtilities.dp(12f))

		imageView.setRoundRadius(AndroidUtilities.dp(18f))

		addView(imageView, LayoutHelper.createFrame(32, 32f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) 0 else padding).toFloat(), 0f, (if (LocaleController.isRTL) padding else 0).toFloat(), 0f))

		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
		textView.ellipsize = TextUtils.TruncateAt.END

		textView.setTextColor(context.getColor(R.color.text))

		addView(textView, LayoutHelper.createFrame(175, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 0f else padding.toFloat() + 48, 0f, if (LocaleController.isRTL) padding.toFloat() + 48 else 0f, 0f))

		checkImageView.setImageResource(R.drawable.selected_account_checkmark)
		checkImageView.scaleType = ImageView.ScaleType.CENTER

		addView(checkImageView, LayoutHelper.createFrame(15, 15f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.BOTTOM, if (LocaleController.isRTL) 0f else 38f, 0f, if (LocaleController.isRTL) 38f else 0f, 8f))

		unreadCountLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
		unreadCountLabel.typeface = Theme.TYPEFACE_BOLD
		unreadCountLabel.setBackgroundResource(R.drawable.unread_counter_background)
		unreadCountLabel.minimumWidth = AndroidUtilities.dp(24f)
		unreadCountLabel.gravity = Gravity.CENTER
		unreadCountLabel.setTextColor(Color.WHITE)
		unreadCountLabel.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
		unreadCountLabel.gone()

		addView(unreadCountLabel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) padding.toFloat() else 0f, 0f, if (LocaleController.isRTL) 0f else padding.toFloat(), 0f))

		setWillNotDraw(false)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(HEIGHT), MeasureSpec.EXACTLY))
	}

	fun setUser(user: User) {
		avatarDrawable.setInfo(user)
		textView.text = ContactsController.formatName(user.first_name, user.last_name)
		imageView.setForUserOrChat(user, avatarDrawable)
	}

	fun setAccount(account: Int, check: Boolean) {
		val user = UserConfig.getInstance(account).getCurrentUser()

		avatarDrawable.setInfo(user)

		textView.text = ContactsController.formatName(user?.first_name, user?.last_name)

		imageView.imageReceiver.currentAccount = account
		imageView.setForUserOrChat(user, avatarDrawable)

		checkImageView.visibility = if (check && account == UserConfig.selectedAccount) VISIBLE else INVISIBLE
	}

	fun setUnreadCount(count: Int) {
		if (count == 0) {
			unreadCountLabel.gone()
		}
		else {
			unreadCountLabel.text = count.toString()
			unreadCountLabel.visible()
		}
	}

	override fun onDraw(canvas: Canvas) {
		if (shouldDrawDivider) {
			val startX = if (LocaleController.isRTL) 0 else AndroidUtilities.dp(padding.toFloat())
			val endX = if (LocaleController.isRTL) measuredWidth - AndroidUtilities.dp(padding.toFloat()) else measuredWidth
			val y = AndroidUtilities.dp(HEIGHT - 1).toFloat()

			canvas.drawLine(startX.toFloat(), y, endX.toFloat(), y, Theme.dividerPaint)
		}
	}

	companion object {
		private const val HEIGHT = 50f
	}
}
