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
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ContactsController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper

class AccountSelectCell @JvmOverloads constructor(context: Context, hasInfo: Boolean = false) : FrameLayout(context) {
	private val textView: TextView
	private var infoTextView: TextView? = null
	private val imageView = BackupImageView(context)
	private var checkImageView: ImageView? = null
	private val avatarDrawable: AvatarDrawable = AvatarDrawable()

	var accountNumber = 0
		private set

	init {
		avatarDrawable.setTextSize(AndroidUtilities.dp(12f))

		imageView.setRoundRadius(AndroidUtilities.dp(18f))

		addView(imageView, LayoutHelper.createFrame(36, 36f, Gravity.LEFT or Gravity.TOP, 10f, 10f, 0f, 0f))

		textView = TextView(context)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
		textView.ellipsize = TextUtils.TruncateAt.END

		textView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))

		if (hasInfo) {
			addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 61f, 7f, 8f, 0f))

			textView.text = context.getString(R.string.VoipGroupDisplayAs)

			infoTextView = TextView(context)
			infoTextView?.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
			infoTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			infoTextView?.setLines(1)
			infoTextView?.maxLines = 1
			infoTextView?.isSingleLine = true
			infoTextView?.maxWidth = AndroidUtilities.dp(320f)
			infoTextView?.gravity = Gravity.LEFT or Gravity.TOP
			infoTextView?.ellipsize = TextUtils.TruncateAt.END

			addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 61f, 27f, 8f, 0f))
		}
		else {
			addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.TOP, 61f, 0f, 56f, 0f))

			checkImageView = ImageView(context)
			checkImageView?.setImageResource(R.drawable.account_check)
			checkImageView?.scaleType = ImageView.ScaleType.CENTER
			checkImageView?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)

			addView(checkImageView, LayoutHelper.createFrame(40, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.RIGHT or Gravity.TOP, 0f, 0f, 6f, 0f))
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (checkImageView != null || infoTextView != null && layoutParams.width != LayoutHelper.WRAP_CONTENT) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56f), MeasureSpec.EXACTLY))
		}
		else {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56f), MeasureSpec.EXACTLY))
		}
	}

	fun setObject(`object`: TLObject) {
		when (`object`) {
			is User -> {
				avatarDrawable.setInfo(`object`)
				infoTextView?.text = ContactsController.formatName(`object`.firstName, `object`.lastName)
				imageView.setForUserOrChat(`object`, avatarDrawable)
			}

			is Chat -> {
				avatarDrawable.setInfo(`object`)
				infoTextView?.text = `object`.title
				imageView.setForUserOrChat(`object`, avatarDrawable)
			}
		}
	}

	fun setAccount(account: Int, check: Boolean) {
		accountNumber = account

		val user = UserConfig.getInstance(accountNumber).getCurrentUser()

		avatarDrawable.setInfo(user)

		textView.text = ContactsController.formatName(user?.firstName, user?.lastName)

		imageView.imageReceiver.currentAccount = account
		imageView.setForUserOrChat(user, avatarDrawable)

		checkImageView?.visibility = if (check && account == UserConfig.selectedAccount) VISIBLE else INVISIBLE
	}
}
