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
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserObject.getUserName
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

open class GroupCallInvitedCell(context: Context) : FrameLayout(context) {
	private val avatarImageView: BackupImageView
	private val nameTextView: SimpleTextView
	private val statusTextView: SimpleTextView
	private val muteButton: ImageView
	private val avatarDrawable: AvatarDrawable
	var user: User? = null
		private set
	private val dividerPaint: Paint = Paint()
	private var grayIconColor = ResourcesCompat.getColor(getContext().resources, R.color.dark_gray, null)
	private var needDivider = false

	init {
		dividerPaint.color = Theme.getColor(Theme.key_voipgroup_actionBar)

		avatarDrawable = AvatarDrawable()

		avatarImageView = BackupImageView(context)
		avatarImageView.setRoundRadius(AndroidUtilities.dp(24f))

		addView(avatarImageView, createFrame(46, 46f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 11).toFloat(), 6f, (if (LocaleController.isRTL) 11 else 0).toFloat(), 0f))

		nameTextView = SimpleTextView(context)
		nameTextView.textColor = ResourcesCompat.getColor(getContext().resources, R.color.text, null)
		nameTextView.setTypeface(Theme.TYPEFACE_BOLD)
		nameTextView.setTextSize(16)
		nameTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)

		addView(nameTextView, createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 54 else 67).toFloat(), 10f, (if (LocaleController.isRTL) 67 else 54).toFloat(), 0f))

		statusTextView = SimpleTextView(context)
		statusTextView.setTextSize(15)
		statusTextView.setGravity((if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP)
		statusTextView.textColor = grayIconColor
		statusTextView.setText(context.getString(R.string.Invited))

		addView(statusTextView, createFrame(LayoutHelper.MATCH_PARENT, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 54 else 67).toFloat(), 32f, (if (LocaleController.isRTL) 67 else 54).toFloat(), 0f))

		muteButton = ImageView(context)
		muteButton.scaleType = ImageView.ScaleType.CENTER
		muteButton.setImageResource(R.drawable.msg_invited)
		muteButton.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		muteButton.setPadding(0, 0, AndroidUtilities.dp(4f), 0)
		muteButton.colorFilter = PorterDuffColorFilter(grayIconColor, PorterDuff.Mode.MULTIPLY)

		addView(muteButton, createFrame(48, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, 6f, 0f, 6f, 0f))

		setWillNotDraw(false)

		isFocusable = true
	}

	val name: CharSequence
		get() = nameTextView.getText()

	fun setData(account: Int, uid: Long?) {
		user = MessagesController.getInstance(account).getUser(uid)
		avatarDrawable.setInfo(user)
		val lastName = getUserName(user)
		nameTextView.setText(lastName)
		avatarImageView.imageReceiver.currentAccount = account
		avatarImageView.setForUserOrChat(user, avatarDrawable)
	}

	fun setDrawDivider(draw: Boolean) {
		needDivider = draw
		invalidate()
	}

	fun setGrayIconColor(color: Int, value: Int) {
		if (grayIconColor != color) {
			grayIconColor = color
		}

		muteButton.colorFilter = PorterDuffColorFilter(value, PorterDuff.Mode.MULTIPLY)
		statusTextView.textColor = value

		Theme.setSelectorDrawableColor(muteButton.drawable, value and 0x24ffffff, true)
	}

	fun hasAvatarSet(): Boolean {
		return avatarImageView.imageReceiver.hasNotThumb()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58f), MeasureSpec.EXACTLY))
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	override fun dispatchDraw(canvas: Canvas) {
		if (needDivider) {
			canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(68f)).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(68f) else 0).toFloat(), (measuredHeight - 1).toFloat(), dividerPaint)
		}

		super.dispatchDraw(canvas)
	}
}
