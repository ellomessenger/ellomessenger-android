/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.sizes
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedEmojiSpan.EmojiGroupedSpans
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.statistics.MemberData
import org.telegram.ui.statistics.RecentPostInfo

open class StatisticPostInfoCell(context: Context, private val chat: ChatFull) : FrameLayout(context) {
	private val message: TextView
	private val views: TextView
	private val shares: TextView
	private val date: TextView
	private val imageView = BackupImageView(context)
	private val avatarDrawable = AvatarDrawable()

	init {
		addView(imageView, createFrame(46, 46f, Gravity.START or Gravity.CENTER_VERTICAL, 12f, 0f, 16f, 0f))

		val contentLayout = LinearLayout(context)
		contentLayout.orientation = LinearLayout.VERTICAL

		var linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL

		message = @SuppressLint("AppCompatCustomView") object : TextView(context) {
			var stack: EmojiGroupedSpans? = null

			override fun setText(text: CharSequence, type: BufferType) {
				super.setText(text, type)
				stack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, stack, layout)
			}

			override fun onAttachedToWindow() {
				super.onAttachedToWindow()
				stack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, stack, layout)
			}

			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()
				AnimatedEmojiSpan.release(stack)
			}

			override fun onDraw(canvas: Canvas) {
				super.onDraw(canvas)
				AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, stack, 0f, null, 0f, 0f, 0f, 1f)
			}
		}

		message.setTypeface(Theme.TYPEFACE_BOLD)
		message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		message.setTextColor(Color.BLACK)
		message.setLines(1)
		message.setEllipsize(TextUtils.TruncateAt.END)

		views = TextView(context)
		views.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		views.setTextColor(Color.BLACK)

		linearLayout.addView(message, createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.NO_GRAVITY, 0, 0, 16, 0))
		linearLayout.addView(views, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		contentLayout.addView(linearLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 0f, 8f, 0f, 0f))

		date = TextView(context)
		date.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		date.setTextColor(Color.BLACK)
		date.setLines(1)
		date.ellipsize = TextUtils.TruncateAt.END

		shares = TextView(context)
		shares.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		shares.setTextColor(Color.BLACK)

		linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.addView(date, createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.NO_GRAVITY, 0, 0, 8, 0))
		linearLayout.addView(shares, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		contentLayout.addView(linearLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 0f, 2f, 0f, 8f))

		addView(contentLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.NO_GRAVITY, 72f, 0f, 12f, 0f))

		message.setTextColor(context.getColor(R.color.text))
		views.setTextColor(context.getColor(R.color.text))
		date.setTextColor(context.getColor(R.color.dark_gray))
		shares.setTextColor(context.getColor(R.color.dark_gray))
	}

	fun setData(postInfo: RecentPostInfo) {
		val messageObject = postInfo.message

		if (messageObject?.photoThumbs != null) {
			val size = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize())
			val thumbSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50)

			imageView.setImage(ImageLocation.getForObject(size, messageObject.photoThumbsObject), "50_50", ImageLocation.getForObject(thumbSize, messageObject.photoThumbsObject), "b1", 0, messageObject)
			imageView.setRoundRadius(AndroidUtilities.dp(4f))
		}
		else if ((chat.chatPhoto?.sizes?.size ?: 0) > 0) {
			imageView.setImage(ImageLocation.getForPhoto(chat.chatPhoto?.sizes?.firstOrNull(), chat.chatPhoto), "50_50", null, null, chat)
			imageView.setRoundRadius(AndroidUtilities.dp(46f) shr 1)
		}

		val text = if (messageObject?.isMusic == true) {
			String.format("%s, %s", messageObject.musicTitle?.trim(), messageObject.musicAuthor?.trim())
		}
		else {
			messageObject?.caption ?: messageObject?.messageText
		}

		message.text = AndroidUtilities.trim(AndroidUtilities.replaceNewLines(SpannableStringBuilder(text)), null)
		views.text = String.format(LocaleController.getPluralString("Views", postInfo.counters!!.views), AndroidUtilities.formatCount(postInfo.counters!!.views))
		date.text = LocaleController.formatDateAudio(postInfo.message!!.messageOwner!!.date.toLong(), false)
		shares.text = String.format(LocaleController.getPluralString("Shares", postInfo.counters!!.forwards), AndroidUtilities.formatCount(postInfo.counters!!.forwards))
	}

	fun setData(memberData: MemberData) {
		avatarDrawable.setInfo(memberData.user)
		imageView.setForUserOrChat(memberData.user, avatarDrawable)
		imageView.setRoundRadius(AndroidUtilities.dp(46f) shr 1)
		message.text = memberData.user?.firstName
		date.text = memberData.description
		views.gone()
		shares.gone()
	}
}
