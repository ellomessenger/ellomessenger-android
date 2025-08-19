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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import androidx.core.graphics.withTranslation
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.RecentMeUrl
import org.telegram.tgnet.TLRPC.TLRecentMeUrlChat
import org.telegram.tgnet.TLRPC.TLRecentMeUrlChatInvite
import org.telegram.tgnet.TLRPC.TLRecentMeUrlStickerSet
import org.telegram.tgnet.TLRPC.TLRecentMeUrlUnknown
import org.telegram.tgnet.TLRPC.TLRecentMeUrlUser
import org.telegram.tgnet.TLRPC.TLUser
import org.telegram.tgnet.sizes
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import kotlin.math.ceil
import kotlin.math.max

class DialogMeUrlCell(context: Context) : BaseCell(context) {
	private var recentMeUrl: RecentMeUrl? = null
	private val avatarImage = ImageReceiver(this)
	private val avatarDrawable = AvatarDrawable()
	private var nameLeft = 0
	private var nameLayout: StaticLayout? = null
	private var drawNameLock = false
	private var nameMuteLeft = 0
	private var nameLockLeft = 0
	private var nameLockTop = 0
	private val messageTop = AndroidUtilities.dp(40f)
	private var messageLeft = 0
	private var messageLayout: StaticLayout? = null
	private var drawVerified = false
	private val avatarTop = AndroidUtilities.dp(10f)
	private var isSelected = false
	private val currentAccount = UserConfig.selectedAccount
	var useSeparator = false

	init {
		Theme.createDialogsResources(context)
		avatarImage.setRoundRadius(AndroidUtilities.dp(26f))
	}

	fun setRecentMeUrl(url: RecentMeUrl?) {
		recentMeUrl = url
		requestLayout()
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		avatarImage.onDetachedFromWindow()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		avatarImage.onAttachedToWindow()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(72f) + (if (useSeparator) 1 else 0))
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		if (changed) {
			buildLayout()
		}
	}

	fun buildLayout() {
		val recentMeUrl = recentMeUrl
		var nameString = ""
		val currentNamePaint = Theme.dialogs_namePaint[0]
		val currentMessagePaint = Theme.dialogs_messagePaint[0]

		drawNameLock = false
		drawVerified = false

		if (recentMeUrl is TLRecentMeUrlChat) {
			val chat = MessagesController.getInstance(currentAccount).getChat(recentMeUrl.chatId)
			drawVerified = chat!!.verified

			if (!LocaleController.isRTL) {
				nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
				nameLeft = AndroidUtilities.dp((AndroidUtilities.leftBaseline + 4).toFloat())
			}
			else {
				nameLockLeft = measuredWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
				nameLeft = AndroidUtilities.dp(14f)
			}

			nameString = chat.title ?: ""

			avatarDrawable.setInfo(chat)
			avatarImage.setForUserOrChat(chat, avatarDrawable, recentMeUrl)
		}
		else if (recentMeUrl is TLRecentMeUrlUser) {
			val user = MessagesController.getInstance(currentAccount).getUser(recentMeUrl.userId)

			nameLeft = if (!LocaleController.isRTL) {
				AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
			}
			else {
				AndroidUtilities.dp(14f)
			}

			if (user is TLUser) {
				if (user.bot) {
					nameLockTop = AndroidUtilities.dp(16.5f)

					if (!LocaleController.isRTL) {
						nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
						nameLeft = AndroidUtilities.dp((AndroidUtilities.leftBaseline + 4).toFloat())
					}
					else {
						nameLockLeft = measuredWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
						nameLeft = AndroidUtilities.dp(14f)
					}
				}

				drawVerified = user.verified
			}

			nameString = UserObject.getUserName(user)

			avatarDrawable.setInfo(user)
			avatarImage.setForUserOrChat(user, avatarDrawable, recentMeUrl)
		}
		else if (recentMeUrl is TLRecentMeUrlStickerSet) {
			nameLeft = if (!LocaleController.isRTL) {
				AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
			}
			else {
				AndroidUtilities.dp(14f)
			}

			nameString = recentMeUrl.set?.set?.title ?: ""
			avatarDrawable.setInfo(recentMeUrl.set?.set?.title, null)
			avatarImage.setImage(ImageLocation.getForDocument((recentMeUrl.set as? TLRPC.TLStickerSetCovered)?.cover), null, avatarDrawable, null, recentMeUrl, 0)
		}
		else if (recentMeUrl is TLRecentMeUrlChatInvite) {
			nameLeft = if (!LocaleController.isRTL) {
				AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
			}
			else {
				AndroidUtilities.dp(14f)
			}

			if (recentMeUrl.chatInvite?.chat != null) {
				avatarDrawable.setInfo(recentMeUrl.chatInvite?.chat)
				nameString = recentMeUrl.chatInvite?.chat?.title ?: ""
				drawVerified = recentMeUrl.chatInvite?.chat?.verified == true
				avatarImage.setForUserOrChat(recentMeUrl.chatInvite?.chat, avatarDrawable, recentMeUrl)
			}
			else {
				nameString = recentMeUrl.chatInvite?.title ?: ""
				avatarDrawable.setInfo(recentMeUrl.chatInvite?.title, null)
				val size = FileLoader.getClosestPhotoSizeWithSize(recentMeUrl.chatInvite?.photo?.sizes, 50)
				avatarImage.setImage(ImageLocation.getForPhoto(size, recentMeUrl.chatInvite?.photo), "50_50", avatarDrawable, null, recentMeUrl, 0)
			}
			if (!LocaleController.isRTL) {
				nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
				nameLeft = AndroidUtilities.dp((AndroidUtilities.leftBaseline + 4).toFloat())
			}
			else {
				nameLockLeft = measuredWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
				nameLeft = AndroidUtilities.dp(14f)
			}
		}
		else if (recentMeUrl is TLRecentMeUrlUnknown) {
			nameLeft = if (!LocaleController.isRTL) {
				AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
			}
			else {
				AndroidUtilities.dp(14f)
			}
			nameString = "Url"
			avatarImage.setImage(null, null, avatarDrawable, null, recentMeUrl, 0)
		}
		else {
			avatarImage.setImage(null, null, avatarDrawable, null, recentMeUrl, 0)
		}

		val messageString: CharSequence = MessagesController.getInstance(currentAccount).linkPrefix + "/" + recentMeUrl!!.url

		if (nameString.isEmpty()) {
			nameString = context.getString(R.string.HiddenName)
		}

		var nameWidth = if (!LocaleController.isRTL) {
			measuredWidth - nameLeft - AndroidUtilities.dp(14f)
		}
		else {
			measuredWidth - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
		}

		if (drawNameLock) {
			nameWidth -= AndroidUtilities.dp(4f) + Theme.dialogs_lockDrawable.intrinsicWidth
		}

		if (drawVerified) {
			val w = AndroidUtilities.dp(6f) + Theme.dialogs_verifiedDrawable.intrinsicWidth

			nameWidth -= w

			if (LocaleController.isRTL) {
				nameLeft += w
			}
		}

		nameWidth = max(AndroidUtilities.dp(12f).toDouble(), nameWidth.toDouble()).toInt()

		try {
			val nameStringFinal = TextUtils.ellipsize(nameString.replace('\n', ' '), currentNamePaint, (nameWidth - AndroidUtilities.dp(12f)).toFloat(), TextUtils.TruncateAt.END)
			nameLayout = StaticLayout(nameStringFinal, currentNamePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		var messageWidth = measuredWidth - AndroidUtilities.dp((AndroidUtilities.leftBaseline + 16).toFloat())
		val avatarLeft: Int

		if (!LocaleController.isRTL) {
			messageLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())
			avatarLeft = AndroidUtilities.dp((if (AndroidUtilities.isTablet()) 13 else 9).toFloat())
		}
		else {
			messageLeft = AndroidUtilities.dp(16f)
			avatarLeft = measuredWidth - AndroidUtilities.dp((if (AndroidUtilities.isTablet()) 65 else 61).toFloat())
		}

		avatarImage.setImageCoordinates(avatarLeft.toFloat(), avatarTop.toFloat(), AndroidUtilities.dp(52f).toFloat(), AndroidUtilities.dp(52f).toFloat())

		messageWidth = max(AndroidUtilities.dp(12f).toDouble(), messageWidth.toDouble()).toInt()

		val messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint, (messageWidth - AndroidUtilities.dp(12f)).toFloat(), TextUtils.TruncateAt.END)

		try {
			messageLayout = StaticLayout(messageStringFinal, currentMessagePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		var widthpx: Double
		var left: Float
		if (LocaleController.isRTL) {
			if (nameLayout != null && nameLayout!!.lineCount > 0) {
				left = nameLayout!!.getLineLeft(0)
				widthpx = ceil(nameLayout!!.getLineWidth(0).toDouble())
				if (drawVerified) {
					nameMuteLeft = (nameLeft + (nameWidth - widthpx) - AndroidUtilities.dp(6f) - Theme.dialogs_verifiedDrawable.intrinsicWidth).toInt()
				}
				if (left == 0f) {
					if (widthpx < nameWidth) {
						nameLeft = (nameLeft + (nameWidth - widthpx)).toInt()
					}
				}
			}
			if (messageLayout != null && messageLayout!!.lineCount > 0) {
				left = messageLayout!!.getLineLeft(0)
				if (left == 0f) {
					widthpx = ceil(messageLayout!!.getLineWidth(0).toDouble())
					if (widthpx < messageWidth) {
						messageLeft = (messageLeft + (messageWidth - widthpx)).toInt()
					}
				}
			}
		}
		else {
			if (nameLayout != null && nameLayout!!.lineCount > 0) {
				left = nameLayout!!.getLineRight(0)
				if (left == nameWidth.toFloat()) {
					widthpx = ceil(nameLayout!!.getLineWidth(0).toDouble())
					if (widthpx < nameWidth) {
						nameLeft = (nameLeft - (nameWidth - widthpx)).toInt()
					}
				}
				if (drawVerified) {
					nameMuteLeft = (nameLeft + left + AndroidUtilities.dp(6f)).toInt()
				}
			}
			if (messageLayout != null && messageLayout!!.lineCount > 0) {
				left = messageLayout!!.getLineRight(0)
				if (left == messageWidth.toFloat()) {
					widthpx = ceil(messageLayout!!.getLineWidth(0).toDouble())
					if (widthpx < messageWidth) {
						messageLeft = (messageLeft - (messageWidth - widthpx)).toInt()
					}
				}
			}
		}
	}

	fun setDialogSelected(value: Boolean) {
		if (isSelected != value) {
			invalidate()
		}
		isSelected = value
	}

	override fun onDraw(canvas: Canvas) {
		if (isSelected) {
			canvas.drawRect(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), Theme.dialogs_tabletSeletedPaint)
		}

		if (drawNameLock) {
			setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop)
			Theme.dialogs_lockDrawable.draw(canvas)
		}

		if (nameLayout != null) {
			canvas.withTranslation(nameLeft.toFloat(), AndroidUtilities.dp(13f).toFloat()) {
				nameLayout?.draw(this)
			}
		}

		if (messageLayout != null) {
			canvas.withTranslation(messageLeft.toFloat(), messageTop.toFloat()) {
				try {
					messageLayout?.draw(this)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		if (drawVerified) {
			setDrawableBounds(Theme.dialogs_verifiedDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f))
			setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, nameMuteLeft, AndroidUtilities.dp(16.5f))
			Theme.dialogs_verifiedDrawable.draw(canvas)
			Theme.dialogs_verifiedCheckDrawable.draw(canvas)
		}

		if (useSeparator) {
			if (LocaleController.isRTL) {
				canvas.drawLine(0f, (measuredHeight - 1).toFloat(), (measuredWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
			else {
				canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat()).toFloat(), (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
		}

		avatarImage.draw(canvas)
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}
}
