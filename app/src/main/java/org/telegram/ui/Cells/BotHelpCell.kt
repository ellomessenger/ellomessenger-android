/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Cells

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji.replaceEmoji
import org.telegram.messenger.FileLoader.Companion.getClosestPhotoSizeWithSize
import org.telegram.messenger.FileRefController.Companion.getKeyForParentObject
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.messageobject.MessageObject.Companion.addLinks
import org.telegram.messenger.messageobject.MessageObject.Companion.getDocumentVideoThumb
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInfo
import org.telegram.tgnet.TLRPC.TL_photoStrippedSize
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.ui.ActionBar.MessageDrawable
import org.telegram.ui.Components.LinkPath
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.Components.URLSpanNoUnderline
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class BotHelpCell(context: Context) : View(context) {

	private var textLayout: StaticLayout? = null
	private var oldText: String? = null
	private var currentPhotoKey: String? = null
	private var width = 0
	private var height = 0
	private var textX = 0
	private var textY = 0
	var wasDraw = false
	private var pressedLink: ClickableSpan? = null
	private val urlPath = LinkPath()
	private var delegate: BotHelpCellDelegate? = null
	private var photoHeight = 0
	private val imageReceiver: ImageReceiver = ImageReceiver(this)
	private var isPhotoVisible = false
	private var isTextVisible = false
	private val imagePadding = AndroidUtilities.dp(4f)
	private var animating = false

	fun interface BotHelpCellDelegate {
		fun didPressUrl(url: String)
	}

	init {
		imageReceiver.setCrossfadeWithOldImage(true)
		imageReceiver.setCrossfadeDuration(300)
	}

	fun setDelegate(botHelpCellDelegate: BotHelpCellDelegate) {
		delegate = botHelpCellDelegate
	}

	private fun resetPressedLink() {
		pressedLink = null
		invalidate()
	}

	fun setText(bot: Boolean, text: String?) {
		setText(bot, text, null, null)
	}

	fun setVisible() {
		gone()
	}

	fun setText(bot: Boolean, text: String?, imageOrAnimation: TLObject?, botInfo: BotInfo?) {
		var text = text
		val photoVisible = imageOrAnimation != null
		val textVisible = !TextUtils.isEmpty(text)

		if (text.isNullOrEmpty() && !photoVisible) {
			visibility = GONE
			return
		}

		if (text == null) {
			text = ""
		}

		if (text == oldText && isPhotoVisible == photoVisible) {
			return
		}

		isPhotoVisible = photoVisible
		isTextVisible = textVisible

		if (isPhotoVisible) {
			val photoKey = getKeyForParentObject(botInfo)
			if (currentPhotoKey != photoKey) {
				currentPhotoKey = photoKey
				setupImageReceiver(imageOrAnimation, botInfo)
			}
		}

		oldText = AndroidUtilities.getSafeString(text)

		val maxWidth = if (AndroidUtilities.isTablet()) {
			(AndroidUtilities.getMinTabletSide() * 0.7f).toInt()
		} else {
			(min(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) * 0.7f).toInt()
		}

		if (isTextVisible) {
			setupTextLayout(bot, text, maxWidth)
		} else if (isPhotoVisible) {
			width = maxWidth
		}

		width += AndroidUtilities.dp((4 + 18).toFloat())

		if (isPhotoVisible) {
			height += (width * 0.5625).toInt().also { photoHeight = it } + AndroidUtilities.dp(4f) // 16:9
		}
	}

	private fun setupImageReceiver(imageOrAnimation: TLObject?, botInfo: BotInfo?) {
		when (imageOrAnimation) {

			is TLRPC.Photo -> {
				imageReceiver.setImage(ImageLocation.getForPhoto(getClosestPhotoSizeWithSize(imageOrAnimation.sizes, 400), imageOrAnimation), "400_400", null, "jpg", botInfo, 0)
			}

			is TLRPC.Document -> {
				val photoThumb = getClosestPhotoSizeWithSize(imageOrAnimation.thumbs, 400)
				var strippedThumb: BitmapDrawable? = null

				if (SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW) {
					for (photoSize in imageOrAnimation.thumbs) {
						if (photoSize is TL_photoStrippedSize) {
							strippedThumb = BitmapDrawable(resources, ImageLoader.getStrippedPhotoBitmap(photoSize.bytes, "b"))
						}
					}
				}

				imageReceiver.setImage(ImageLocation.getForDocument(imageOrAnimation), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(getDocumentVideoThumb(imageOrAnimation), imageOrAnimation), null, ImageLocation.getForDocument(photoThumb, imageOrAnimation), "86_86_b", strippedThumb, imageOrAnimation.size, "mp4", botInfo, 0)
			}
		}

		val topRadius = AndroidUtilities.dp(SharedConfig.bubbleRadius.toFloat()) - AndroidUtilities.dp(2f)
		val bottomRadius = if (!isTextVisible) topRadius else AndroidUtilities.dp(4f)
		imageReceiver.setRoundRadius(topRadius, topRadius, bottomRadius, bottomRadius)
	}

	private fun setupTextLayout(bot: Boolean, text: String, maxWidth: Int) {
		val lines = text.split("\n").dropLastWhile { it.isEmpty() }
		val stringBuilder = SpannableStringBuilder()
		val help = LocaleController.getString(R.string.BotInfoTitle)

		if (bot) {
			stringBuilder.append(help).append("\n\n")
		}

		for ((index, line) in lines.withIndex()) {
			stringBuilder.append(line.trim())
			if (index != lines.size - 1) {
				stringBuilder.append("\n")
			}
		}

		addLinks(false, stringBuilder)

		if (bot) {
			stringBuilder.setSpan(TypefaceSpan(Typeface.DEFAULT_BOLD), 0, help.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}

		val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
			typeface = Typeface.DEFAULT
			textSize = AndroidUtilities.dp((SharedConfig.fontSize - 1).toFloat()).toFloat()
			color = context?.getColor(R.color.dark)!!
			linkColor = context.getColor(R.color.brand)

			replaceEmoji(stringBuilder, fontMetricsInt, false)
		}

		try {
			textLayout = StaticLayout.Builder.obtain(stringBuilder, 0, stringBuilder.length, textPaint, maxWidth - if (isPhotoVisible) AndroidUtilities.dp(5f) else 0)
					.setAlignment(Layout.Alignment.ALIGN_NORMAL)
					.setLineSpacing(0.0f, 1.0f)
					.setIncludePad(false)
					.build()

			width = 0
			height = textLayout?.height?.plus(AndroidUtilities.dp((4 + 18).toFloat())) ?: 0
			val count = textLayout?.lineCount ?: 0
			for (a in 0 until count) {
				textLayout?.let {
					width = ceil(max(width.toDouble(), (it.getLineWidth(a) + it.getLineLeft(a)).toDouble())).toInt()
				}
			}
			if (width > maxWidth || isPhotoVisible) {
				width = maxWidth
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		val x = event.x
		val y = event.y
		var result = false
		if (textLayout != null) {
			if (event.action == MotionEvent.ACTION_DOWN || pressedLink != null && event.action == MotionEvent.ACTION_UP) {
				if (event.action == MotionEvent.ACTION_DOWN) {
					resetPressedLink()
					try {
						val x2 = (x - textX).toInt()
						val y2 = (y - textY).toInt()
						val line = textLayout!!.getLineForVertical(y2)
						val off = textLayout!!.getOffsetForHorizontal(line, x2.toFloat())
						val left = textLayout!!.getLineLeft(line)
						if (left <= x2 && left + textLayout!!.getLineWidth(line) >= x2) {
							val buffer = textLayout!!.text as Spannable
							val link = buffer.getSpans(off, off, ClickableSpan::class.java)
							if (link.isNotEmpty()) {
								resetPressedLink()
								pressedLink = link[0]
								result = true
								try {
									val start = buffer.getSpanStart(pressedLink)
									urlPath.setCurrentLayout(textLayout, start, 0f)
									textLayout!!.getSelectionPath(start, buffer.getSpanEnd(pressedLink), urlPath)
								} catch (e: Exception) {
									e.printStackTrace()
								}
							} else {
								resetPressedLink()
							}
						} else {
							resetPressedLink()
						}
					} catch (e: Exception) {
						resetPressedLink()
						e.printStackTrace()
					}
				} else if (pressedLink != null) {
					try {
						if (pressedLink is URLSpanNoUnderline) {
							val url = (pressedLink as URLSpanNoUnderline).url
							if (url.startsWith("@") || url.startsWith("#") || url.startsWith("/")) {
								delegate?.didPressUrl(url)
							}
						} else {
							if (pressedLink is URLSpan) {
								delegate?.didPressUrl((pressedLink as URLSpan).url)
							} else {
								pressedLink!!.onClick(this)
							}
						}
					} catch (e: Exception) {
						e.printStackTrace()
					}
					resetPressedLink()
					result = true
				}
			} else if (event.action == MotionEvent.ACTION_CANCEL) {
				resetPressedLink()
			}
		}
		return result || super.onTouchEvent(event)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(
				MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
				height + AndroidUtilities.dp(8f)
		)
	}

	@SuppressLint("DrawAllocation")
	override fun onDraw(canvas: Canvas) {
		val x = (getWidth() - width) / 2
		var y = photoHeight

		y += AndroidUtilities.dp(2f)

		val drawable = MessageDrawable(MessageDrawable.TYPE_MEDIA, isOut = false, isSelected = false);
		drawable.setTop(y, width, height, topNear = false, bottomNear = false)
		drawable.setBounds(x, 0, width + x, height)
		drawable.draw(canvas)

		imageReceiver.setImageCoordinates((x + imagePadding).toFloat(), imagePadding.toFloat(), (width - imagePadding * 2).toFloat(), (photoHeight - imagePadding).toFloat())
		imageReceiver.draw(canvas)

		canvas.save()
		canvas.translate((AndroidUtilities.dp((if (isPhotoVisible) 14 else 11).toFloat()) + x).also { textX = it }.toFloat(), (AndroidUtilities.dp(11f) + y).also { textY = it }.toFloat())

		pressedLink?.let {
			val chatUrlPaint = Paint().apply {
				pathEffect = LinkPath.getRoundedEffect()
				typeface = Typeface.DEFAULT
				color = context.getColor(R.color.brand)
			}

			canvas.drawPath(urlPath, chatUrlPaint)
		}
		textLayout?.draw(canvas)
		canvas.restore()
		wasDraw = true
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		imageReceiver.onAttachedToWindow()
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		imageReceiver.onDetachedFromWindow()
		wasDraw = false
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		textLayout?.let {
			info.text = it.text
		}
	}

	fun animating(): Boolean {
		return animating
	}

	fun setAnimating(animating: Boolean) {
		this.animating = animating
	}

}
