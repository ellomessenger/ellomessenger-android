/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.R
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_documentAttributeImageSize
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createLinear
import java.util.Locale
import kotlin.math.min

class ChatGreetingsView(context: Context, user: User, distance: Int, currentAccount: Int, sticker: TLRPC.Document?) : LinearLayout(context) {
	private val animationImageView = RLottieImageView(context).apply {
		setAutoRepeat(true)
	}

	private var preloadedGreetingsSticker: TLRPC.Document?
	private val titleView: TextView
	private val descriptionView: TextView
	private var listener: Listener? = null
	private val currentAccount: Int
	var wasDraw = false

	@JvmField
	var stickerToSendView: BackupImageView

	private fun setSticker(sticker: TLRPC.Document?) {
		if (sticker == null) {
			return
		}

		val svgThumb = null // DocumentObject.getSvgThumb(sticker, Theme.key_chat_serviceBackground, 1.0f)

		if (svgThumb != null) {
			stickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), svgThumb, 0, sticker)
		}
		else {
			val thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90)
			stickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), ImageLocation.getForDocument(thumb, sticker), null, 0, sticker)
		}

		stickerToSendView.setOnClickListener {
			listener?.onGreetings(sticker)
		}
	}

	private fun updateColors() {
		titleView.setTextColor(ResourcesCompat.getColor(resources, R.color.white, null))
		descriptionView.setTextColor(ResourcesCompat.getColor(resources, R.color.white, null))
	}

	fun setListener(listener: Listener?) {
		this.listener = listener
	}

	fun interface Listener {
		fun onGreetings(sticker: TLRPC.Document?)
	}

	var ignoreLayout = false

	init {
		orientation = VERTICAL

		this.currentAccount = currentAccount

		titleView = TextView(context)
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		titleView.typeface = Theme.TYPEFACE_BOLD
		titleView.gravity = Gravity.CENTER_HORIZONTAL
		titleView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))

		descriptionView = TextView(context)
		descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)

		if (user.id == BuildConfig.SUPPORT_BOT_ID) {
			descriptionView.gravity = Gravity.START
		}
		else {
			descriptionView.gravity = Gravity.CENTER_HORIZONTAL
		}

		descriptionView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))

		stickerToSendView = BackupImageView(context)

		if (user.bot) {
			when (user.id) {
				BuildConfig.AI_BOT_ID -> animationImageView.setAnimation(R.raw.ai_panda_hi, 112, 112)

				BuildConfig.SUPPORT_BOT_ID -> animationImageView.setAnimation(R.raw.panda_support_hi, 112, 112)
			}
		}
		else {
			animationImageView.setAnimation(R.raw.panda_hi, 112, 112)
		}

		addView(titleView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20f, 16f, 20f, 4f))

		if (user.id == BuildConfig.SUPPORT_BOT_ID) {
			addView(descriptionView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14f, 4f, 14f, 4f))
		}
		else {
			addView(descriptionView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20f, 4f, 20f, 4f))
		}

		if (user.id == BuildConfig.PHOENIX_BOT_ID) {
			addView(animationImageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 16))
		}
		else {
			addView(animationImageView, createLinear(112, 112, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 16))
		}

		updateColors()

		if (distance <= 0) {
			if (user.bot) {
				when (user.id) {
					BuildConfig.AI_BOT_ID -> {
						titleView.text = context.getString(R.string.ai_bot_welcome)
						descriptionView.text = context.getString(R.string.ai_bot_welcome_message)
					}

					BuildConfig.SUPPORT_BOT_ID -> {
						titleView.text = context.getString(R.string.support_bot_welcome)
						descriptionView.text = context.getString(R.string.support_bot_welcome_message)
					}

					BuildConfig.PHOENIX_BOT_ID -> {
						titleView.text = context.getString(R.string.ai_bot_welcome)
						descriptionView.text = context.getString(R.string.bot_phoenix_description)
					}
				}
			}
			else {
				titleView.text = context.getString(R.string.NoMessages)
				descriptionView.text = context.getString(R.string.NoMessagesGreetingsDescription)
			}
		}
		else {
			titleView.text = context.getString(R.string.NearbyPeopleGreetingsMessage, user.first_name, LocaleController.formatDistance(distance.toFloat(), 1))
			descriptionView.text = context.getString(R.string.NearbyPeopleGreetingsDescription)
		}

		stickerToSendView.contentDescription = descriptionView.text

		preloadedGreetingsSticker = sticker

		if (preloadedGreetingsSticker == null) {
			preloadedGreetingsSticker = MediaDataController.getInstance(currentAccount).getGreetingsSticker()
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		ignoreLayout = true

		descriptionView.visibility = VISIBLE
		stickerToSendView.visibility = VISIBLE

		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		if (measuredHeight > MeasureSpec.getSize(heightMeasureSpec)) {
			descriptionView.visibility = GONE
			stickerToSendView.visibility = GONE
		}
		else {
			descriptionView.visibility = VISIBLE
			stickerToSendView.visibility = VISIBLE
		}

		ignoreLayout = false

		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
	}

	override fun dispatchDraw(canvas: Canvas) {
		if (!wasDraw) {
			wasDraw = true
			setSticker(preloadedGreetingsSticker)
		}

		super.dispatchDraw(canvas)
	}

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		fetchSticker()
		animationImageView.playAnimation()
	}

	private fun fetchSticker() {
		if (preloadedGreetingsSticker == null) {
			preloadedGreetingsSticker = MediaDataController.getInstance(currentAccount).getGreetingsSticker()

			if (wasDraw) {
				setSticker(preloadedGreetingsSticker)
			}
		}
	}

	companion object {
		fun createFilter(document: TLRPC.Document): String {
			val maxHeight: Float
			val maxWidth: Float
			var photoWidth = 0
			var photoHeight = 0

			if (AndroidUtilities.isTablet()) {
				maxWidth = AndroidUtilities.getMinTabletSide() * 0.4f
				maxHeight = maxWidth
			}
			else {
				maxWidth = min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f
				maxHeight = maxWidth
			}

			document.attributes?.firstOrNull { it is TL_documentAttributeImageSize }?.let {
				photoWidth = it.w
				photoHeight = it.h
			}

			if (MessageObject.isAnimatedStickerDocument(document, true) && photoWidth == 0 && photoHeight == 0) {
				photoHeight = 512
				photoWidth = photoHeight
			}

			if (photoWidth == 0) {
				photoHeight = maxHeight.toInt()
				photoWidth = photoHeight + AndroidUtilities.dp(100f)
			}

			photoHeight *= (maxWidth / photoWidth).toInt()
			photoWidth = maxWidth.toInt()

			if (photoHeight > maxHeight) {
				photoWidth *= (maxHeight / photoHeight).toInt()
				photoHeight = maxHeight.toInt()
			}

			val w = (photoWidth / AndroidUtilities.density).toInt()
			val h = (photoHeight / AndroidUtilities.density).toInt()

			return String.format(Locale.US, "%d_%d", w, h)
		}
	}
}
