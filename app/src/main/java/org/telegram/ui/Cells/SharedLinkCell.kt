/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Cells

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.net.Uri
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.R
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.tlrpc.TL_messageEntityEmail
import org.telegram.tgnet.tlrpc.TL_messageEntitySpoiler
import org.telegram.tgnet.tlrpc.TL_messageEntityTextUrl
import org.telegram.tgnet.tlrpc.TL_messageEntityUrl
import org.telegram.tgnet.TLRPC.TL_messageMediaWebPage
import org.telegram.tgnet.TLRPC.TL_webPage
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell.Companion.generateStaticLayout
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LetterDrawable
import org.telegram.ui.Components.LinkPath
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.TextStyleSpan.TextStyleRun
import org.telegram.ui.Components.spoilers.SpoilerEffect
import org.telegram.ui.FilteredSearchView
import java.util.Locale
import java.util.Stack
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class SharedLinkCell @JvmOverloads constructor(context: Context, private val viewType: Int = VIEW_TYPE_DEFAULT) : FrameLayout(context) {
	interface SharedLinkCellDelegate {
		fun needOpenWebView(webPage: WebPage, messageObject: MessageObject)
		fun canPerformActions(): Boolean
		fun onLinkPress(urlFinal: String, longPress: Boolean)
	}

	private var checkingForLongPress = false
	private var pendingCheckForLongPress: CheckForLongPress? = null
	private var pressCount = 0
	private var pendingCheckForTap: CheckForTap? = null

	private inner class CheckForTap : Runnable {
		override fun run() {
			if (pendingCheckForLongPress == null) {
				pendingCheckForLongPress = CheckForLongPress()
			}

			pendingCheckForLongPress?.currentPressCount = ++pressCount

			postDelayed(pendingCheckForLongPress, (ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout()).toLong())
		}
	}

	internal inner class CheckForLongPress : Runnable {
		var currentPressCount = 0

		override fun run() {
			if (checkingForLongPress && parent != null && currentPressCount == pressCount) {
				checkingForLongPress = false

				performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

				if (pressedLink >= 0) {
					delegate?.onLinkPress(links[pressedLink].toString(), true)
				}

				val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
				onTouchEvent(event)
				event.recycle()
			}
		}
	}

	private fun startCheckLongPress() {
		if (checkingForLongPress) {
			return
		}

		checkingForLongPress = true

		if (pendingCheckForTap == null) {
			pendingCheckForTap = CheckForTap()
		}

		postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout().toLong())
	}

	private fun cancelCheckLongPress() {
		checkingForLongPress = false

		if (pendingCheckForLongPress != null) {
			removeCallbacks(pendingCheckForLongPress)
		}

		if (pendingCheckForTap != null) {
			removeCallbacks(pendingCheckForTap)
		}
	}

	private var linkPreviewPressed = false
	private val urlPath: LinkPath
	private var pressedLink = 0
	val linkImageView: ImageReceiver
	private var drawLinkImageView = false
	private val letterDrawable: LetterDrawable
	private val checkBox: CheckBox2
	private var delegate: SharedLinkCellDelegate? = null
	private var needDivider = false
	var links = ArrayList<CharSequence>()
	private var linkY = 0
	private val linkLayout = ArrayList<StaticLayout>()
	private val linkSpoilers = SparseArray<List<SpoilerEffect>>()
	private val descriptionLayoutSpoilers: MutableList<SpoilerEffect> = ArrayList()
	private val descriptionLayout2Spoilers: MutableList<SpoilerEffect> = ArrayList()
	private val spoilersPool = Stack<SpoilerEffect>()
	private val path = Path()
	private var spoilerPressed: SpoilerEffect? = null
	private var spoilerTypePressed = -1
	private val titleY = AndroidUtilities.dp(10f)
	private var titleLayout: StaticLayout? = null
	private var descriptionY = AndroidUtilities.dp(30f)
	private var descriptionLayout: StaticLayout? = null
	private val patchedDescriptionLayout = AtomicReference<Layout>()
	private var description2Y = AndroidUtilities.dp(30f)
	private var descriptionLayout2: StaticLayout? = null
	private val patchedDescriptionLayout2 = AtomicReference<Layout>()
	private var captionY = AndroidUtilities.dp(30f)
	private var captionLayout: StaticLayout? = null
	private val titleTextPaint: TextPaint
	private val descriptionTextPaint: TextPaint
	private var description2TextPaint: TextPaint? = null
	private val captionTextPaint: TextPaint
	private var dateLayoutX = 0
	private var dateLayout: StaticLayout? = null
	private var fromInfoLayoutY = AndroidUtilities.dp(30f)
	private var fromInfoLayout: StaticLayout? = null

	var message: MessageObject? = null
		private set

	@SuppressLint("DrawAllocation")
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		drawLinkImageView = false
		descriptionLayout = null
		titleLayout = null
		descriptionLayout2 = null
		captionLayout = null

		linkLayout.clear()
		links.clear()

		val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat()) - AndroidUtilities.dp(8f)
		var title: String? = null
		var description: CharSequence? = null
		var description2: CharSequence? = null
		var webPageLink: String? = null
		var hasPhoto = false
		val message = message


		if (message?.messageOwner?.media is TL_messageMediaWebPage && message.messageOwner?.media?.webpage is TL_webPage) {
			val webPage = message.messageOwner?.media?.webpage

			if (message.photoThumbs == null && webPage?.photo != null) {
				message.generateThumbs(true)
			}

			hasPhoto = webPage?.photo != null && message.photoThumbs != null
			title = webPage?.title

			if (title == null) {
				title = webPage?.site_name
			}

			description = webPage?.description
			webPageLink = webPage?.url
		}

		if (message != null && !message.messageOwner?.entities.isNullOrEmpty()) {
			for (a in message.messageOwner!!.entities.indices) {
				val entity = message.messageOwner!!.entities[a]

				if (entity.length <= 0 || entity.offset < 0 || entity.offset >= message.messageOwner!!.message!!.length) {
					continue
				}
				else if (entity.offset + entity.length > message.messageOwner!!.message!!.length) {
					entity.length = message.messageOwner!!.message!!.length - entity.offset
				}

				if (a == 0 && webPageLink != null && !(entity.offset == 0 && entity.length == message.messageOwner!!.message!!.length)) {
					if (message.messageOwner!!.entities.size == 1) {
						if (description == null) {
							val st = SpannableStringBuilder.valueOf(message.messageOwner!!.message)
							MediaDataController.addTextStyleRuns(message, st)
							description2 = st
						}
					}
					else {
						val st = SpannableStringBuilder.valueOf(message.messageOwner?.message)
						MediaDataController.addTextStyleRuns(message, st)
						description2 = st
					}
				}
				try {
					var link: CharSequence? = null

					if (entity is TL_messageEntityTextUrl || entity is TL_messageEntityUrl) {
						link = if (entity is TL_messageEntityUrl) {
							message.messageOwner?.message?.substring(entity.offset, entity.offset + entity.length)
						}
						else {
							entity.url
						}

						if (title.isNullOrEmpty()) {
							title = link.toString()

							val uri = Uri.parse(title)

							title = uri.host

							if (title == null) {
								title = link.toString()
							}

							var index: Int

							if (title.lastIndexOf('.').also { index = it } >= 0) {
								title = title.substring(0, index)

								if (title.lastIndexOf('.').also { index = it } >= 0) {
									title = title.substring(index + 1)
								}

								title = title.substring(0, 1).uppercase(Locale.getDefault()) + title.substring(1)
							}

							if (entity.offset != 0 || entity.length != message.messageOwner?.message?.length) {
								val st = SpannableStringBuilder.valueOf(message.messageOwner?.message)
								MediaDataController.addTextStyleRuns(message, st)
								description = st
							}
						}
					}
					else if (entity is TL_messageEntityEmail) {
						if (title.isNullOrEmpty()) {
							link = "mailto:" + message.messageOwner?.message?.substring(entity.offset, entity.offset + entity.length)
							title = message.messageOwner?.message?.substring(entity.offset, entity.offset + entity.length)

							if (entity.offset != 0 || entity.length != message.messageOwner?.message?.length) {
								val st = SpannableStringBuilder.valueOf(message.messageOwner?.message)
								MediaDataController.addTextStyleRuns(message, st)
								description = st
							}
						}
					}

					if (link != null) {
						var lobj: CharSequence?
						var offset = 0

						if (!AndroidUtilities.charSequenceContains(link, "://") && link.toString().lowercase(Locale.getDefault()).indexOf("http") != 0 && link.toString().lowercase(Locale.getDefault()).indexOf("mailto") != 0) {
							val prefix = "http://"
							lobj = prefix + link
							offset += prefix.length
						}
						else {
							lobj = link
						}

						val sb = SpannableString.valueOf(lobj)
						val start = entity.offset
						val end = entity.offset + entity.length

						for (e in message.messageOwner!!.entities) {
							val ss = e.offset
							val se = e.offset + e.length

							if (e is TL_messageEntitySpoiler && start <= se && end >= ss) {
								val run = TextStyleRun()
								run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_SPOILER
								sb.setSpan(TextStyleSpan(run), max(start, ss), min(end, se) + offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
							}
						}

						links.add(sb)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		if (webPageLink != null && links.isEmpty()) {
			links.add(webPageLink)
		}

		var dateWidth = 0

		if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
			val str = LocaleController.stringForMessageListDate(message!!.messageOwner!!.date.toLong())
			val width = ceil(description2TextPaint!!.measureText(str).toDouble()).toInt()
			dateLayout = generateStaticLayout(str, description2TextPaint, width, width, 0, 1)
			dateLayoutX = maxWidth - width - AndroidUtilities.dp(8f)
			dateWidth = width + AndroidUtilities.dp(12f)
		}

		if (title != null) {
			try {
				var titleFinal: CharSequence = title
				val titleH = AndroidUtilities.highlightText(titleFinal, message!!.highlightedWords)

				if (titleH != null) {
					titleFinal = titleH
				}

				titleLayout = generateStaticLayout(titleFinal, titleTextPaint, maxWidth - dateWidth - AndroidUtilities.dp(4f), maxWidth - dateWidth - AndroidUtilities.dp(4f), 0, 3)

				if (titleLayout!!.lineCount > 0) {
					descriptionY = titleY + titleLayout!!.getLineBottom(titleLayout!!.lineCount - 1) + AndroidUtilities.dp(4f)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			letterDrawable.setTitle(title)
		}

		description2Y = descriptionY

		val descriptionLines = max(1, 4 - (titleLayout?.lineCount ?: 0))

		if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
			description = null
			description2 = null
		}

		if (description != null) {
			try {
				descriptionLayout = generateStaticLayout(description, descriptionTextPaint, maxWidth, maxWidth, 0, descriptionLines)

				if (descriptionLayout!!.lineCount > 0) {
					description2Y = descriptionY + descriptionLayout!!.getLineBottom(descriptionLayout!!.lineCount - 1) + AndroidUtilities.dp(5f)
				}

				spoilersPool.addAll(descriptionLayoutSpoilers)
				descriptionLayoutSpoilers.clear()

				if (!message!!.isSpoilersRevealed) {
					SpoilerEffect.addSpoilers(this, descriptionLayout, spoilersPool, descriptionLayoutSpoilers)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
		if (description2 != null) {
			try {
				descriptionLayout2 = generateStaticLayout(description2, descriptionTextPaint, maxWidth, maxWidth, 0, descriptionLines)

				if (descriptionLayout != null) {
					description2Y += AndroidUtilities.dp(10f)
				}

				spoilersPool.addAll(descriptionLayout2Spoilers)
				descriptionLayout2Spoilers.clear()

				if (!message!!.isSpoilersRevealed) {
					SpoilerEffect.addSpoilers(this, descriptionLayout2, spoilersPool, descriptionLayout2Spoilers)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		if (message != null && !message.messageOwner?.message.isNullOrEmpty()) {
			val caption = Emoji.replaceEmoji(message.messageOwner?.message?.replace("\n", " ")?.replace(" +".toRegex(), " ")?.trim(), Theme.chat_msgTextPaint.fontMetricsInt, false)
			var sequence = AndroidUtilities.highlightText(caption, message.highlightedWords)

			if (sequence != null) {
				sequence = TextUtils.ellipsize(AndroidUtilities.ellipsizeCenterEnd(sequence, message.highlightedWords?.firstOrNull(), maxWidth, captionTextPaint, 130), captionTextPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)
				captionLayout = StaticLayout(sequence, captionTextPaint, maxWidth + AndroidUtilities.dp(4f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
		}

		if (captionLayout != null) {
			captionY = descriptionY
			descriptionY += captionLayout!!.getLineBottom(captionLayout!!.lineCount - 1) + AndroidUtilities.dp(5f)
			description2Y = descriptionY
		}

		if (links.isNotEmpty()) {
			for (i in 0 until linkSpoilers.size()) {
				spoilersPool.addAll(linkSpoilers[i]!!)
			}

			linkSpoilers.clear()

			for (a in links.indices) {
				try {
					val link = links[a]
					val width = ceil(descriptionTextPaint.measureText(link, 0, link.length).toDouble()).toInt()
					val linkFinal = TextUtils.ellipsize(AndroidUtilities.replaceNewLines(SpannableStringBuilder.valueOf(link)), descriptionTextPaint, min(width, maxWidth).toFloat(), TextUtils.TruncateAt.MIDDLE)
					val layout = StaticLayout(linkFinal, descriptionTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

					linkY = description2Y

					descriptionLayout2?.let {
						if (it.lineCount != 0) {
							linkY += it.getLineBottom(it.lineCount - 1) + AndroidUtilities.dp(5f)
						}
					}

					if (!message!!.isSpoilersRevealed) {
						val l: List<SpoilerEffect> = ArrayList()

						if (linkFinal is Spannable) {
							SpoilerEffect.addSpoilers(this, layout, linkFinal, spoilersPool, l)
						}

						linkSpoilers.put(a, l)
					}

					linkLayout.add(layout)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		val maxPhotoWidth = AndroidUtilities.dp(52f)
		val x = if (LocaleController.isRTL) MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(10f) - maxPhotoWidth else AndroidUtilities.dp(10f)

		letterDrawable.setBounds(x, AndroidUtilities.dp(11f), x + maxPhotoWidth, AndroidUtilities.dp(63f))

		if (hasPhoto) {
			val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(message!!.photoThumbs, maxPhotoWidth, true)
			var currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 80)

			if (currentPhotoObjectThumb === currentPhotoObject) {
				currentPhotoObjectThumb = null
			}

			currentPhotoObject!!.size = -1

			if (currentPhotoObjectThumb != null) {
				currentPhotoObjectThumb.size = -1
			}

			linkImageView.setImageCoordinates(x.toFloat(), AndroidUtilities.dp(11f).toFloat(), maxPhotoWidth.toFloat(), maxPhotoWidth.toFloat())

			val filter = String.format(Locale.US, "%d_%d", maxPhotoWidth, maxPhotoWidth)
			val thumbFilter = String.format(Locale.US, "%d_%d_b", maxPhotoWidth, maxPhotoWidth)

			linkImageView.setImage(ImageLocation.getForObject(currentPhotoObject, message.photoThumbsObject), filter, ImageLocation.getForObject(currentPhotoObjectThumb, message.photoThumbsObject), thumbFilter, 0, null, message, 0)

			drawLinkImageView = true
		}

		if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
			fromInfoLayout = generateStaticLayout(FilteredSearchView.createFromInfoString(message), description2TextPaint, maxWidth, maxWidth, 0, descriptionLines)
		}
		var height = 0

		titleLayout?.let {
			if (it.lineCount != 0) {
				height += it.getLineBottom(it.lineCount - 1) + AndroidUtilities.dp(4f)
			}
		}

		captionLayout?.let {
			if (it.lineCount != 0) {
				height += it.getLineBottom(it.lineCount - 1) + AndroidUtilities.dp(5f)
			}
		}

		descriptionLayout?.let {
			if (it.lineCount != 0) {
				height += it.getLineBottom(it.lineCount - 1) + AndroidUtilities.dp(5f)
			}
		}

		descriptionLayout2?.let {
			if (it.lineCount != 0) {
				height += it.getLineBottom(it.lineCount - 1) + AndroidUtilities.dp(5f)

				if (descriptionLayout != null) {
					height += AndroidUtilities.dp(10f)
				}
			}
		}

		var linksHeight = 0

		for (a in linkLayout.indices) {
			val layout = linkLayout[a]

			if (layout.lineCount > 0) {
				linksHeight += layout.getLineBottom(layout.lineCount - 1)
			}
		}

		height += linksHeight

		fromInfoLayout?.let {
			fromInfoLayoutY = linkY + linksHeight + AndroidUtilities.dp(5f)
			height += it.getLineBottom(it.lineCount - 1) + AndroidUtilities.dp(5f)
		}

		checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY))

		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), max(AndroidUtilities.dp(76f), height + AndroidUtilities.dp(17f)) + if (needDivider) 1 else 0)
	}

	fun setLink(messageObject: MessageObject?, divider: Boolean) {
		needDivider = divider
		resetPressedLink()
		message = messageObject
		requestLayout()
	}

	fun setDelegate(sharedLinkCellDelegate: SharedLinkCellDelegate?) {
		delegate = sharedLinkCellDelegate
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		if (drawLinkImageView) {
			linkImageView.onDetachedFromWindow()
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (drawLinkImageView) {
			linkImageView.onAttachedToWindow()
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		var result = false
		val message = message
		val delegate = delegate

		if (message != null && linkLayout.isNotEmpty() && delegate != null && delegate.canPerformActions()) {
			if (event.action == MotionEvent.ACTION_DOWN || (linkPreviewPressed || spoilerPressed != null) && event.action == MotionEvent.ACTION_UP) {
				val x = event.x.toInt()
				val y = event.y.toInt()
				var offset = 0
				var ok = false

				for (a in linkLayout.indices) {
					val layout = linkLayout[a]

					if (layout.lineCount > 0) {
						val height = layout.getLineBottom(layout.lineCount - 1)
						val linkPosX = AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat())

						if (x >= linkPosX + layout.getLineLeft(0) && x <= linkPosX + layout.getLineWidth(0) && y >= linkY + offset && y <= linkY + offset + height) {
							ok = true

							if (event.action == MotionEvent.ACTION_DOWN) {
								resetPressedLink()

								spoilerPressed = null

								if (linkSpoilers[a, null] != null) {
									for (eff in linkSpoilers[a]!!) {
										if (eff.bounds.contains(x - linkPosX, y - linkY - offset)) {
											spoilerPressed = eff
											spoilerTypePressed = SPOILER_TYPE_LINK
											break
										}
									}
								}

								if (spoilerPressed != null) {
									result = true
								}
								else {
									pressedLink = a
									linkPreviewPressed = true

									startCheckLongPress()

									try {
										urlPath.setCurrentLayout(layout, 0, 0f)
										layout.getSelectionPath(0, layout.text.length, urlPath)
									}
									catch (e: Exception) {
										FileLog.e(e)
									}

									result = true
								}
							}
							else if (linkPreviewPressed) {
								try {
									val webPage = if (pressedLink == 0 && message.messageOwner?.media != null) message.messageOwner?.media?.webpage else null

									if (webPage?.embed_url != null && webPage.embed_url.isNotEmpty()) {
										delegate.needOpenWebView(webPage, message)
									}
									else {
										delegate.onLinkPress(links[pressedLink].toString(), false)
									}
								}
								catch (e: Exception) {
									FileLog.e(e)
								}

								resetPressedLink()

								result = true
							}
							else if (spoilerPressed != null) {
								startSpoilerRipples(x, y, offset)
								result = true
							}

							break
						}

						offset += height
					}
				}

				if (event.action == MotionEvent.ACTION_DOWN) {
					val offX = AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat())

					if (descriptionLayout != null && x >= offX && x <= offX + descriptionLayout!!.width && y >= descriptionY && y <= descriptionY + descriptionLayout!!.height) {
						for (eff in descriptionLayoutSpoilers) {
							if (eff.bounds.contains(x - offX, y - descriptionY)) {
								spoilerPressed = eff
								spoilerTypePressed = SPOILER_TYPE_DESCRIPTION
								ok = true
								result = true
								break
							}
						}
					}

					if (descriptionLayout2 != null && x >= offX && x <= offX + descriptionLayout2!!.width && y >= description2Y && y <= description2Y + descriptionLayout2!!.height) {
						for (eff in descriptionLayout2Spoilers) {
							if (eff.bounds.contains(x - offX, y - description2Y)) {
								spoilerPressed = eff
								spoilerTypePressed = SPOILER_TYPE_DESCRIPTION2
								ok = true
								result = true
								break
							}
						}
					}
				}
				else if (event.action == MotionEvent.ACTION_UP && spoilerPressed != null) {
					startSpoilerRipples(x, y, 0)
					ok = true
					result = true
				}

				if (!ok) {
					resetPressedLink()
				}
			}
			else if (event.action == MotionEvent.ACTION_CANCEL) {
				resetPressedLink()
			}
		}
		else {
			resetPressedLink()
		}

		return result || super.onTouchEvent(event)
	}

	private fun startSpoilerRipples(x: Int, y: Int, offset: Int) {
		val linkPosX = AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat())

		resetPressedLink()

		spoilerPressed?.setOnRippleEndCallback {
			post {
				message?.isSpoilersRevealed = true
				linkSpoilers.clear()
				descriptionLayoutSpoilers.clear()
				descriptionLayout2Spoilers.clear()
				invalidate()
			}
		}

		val nx = x - linkPosX
		val rad = sqrt(width.toDouble().pow(2.0) + height.toDouble().pow(2.0)).toFloat()
		var offY = 0f

		when (spoilerTypePressed) {
			SPOILER_TYPE_LINK -> {
				linkLayout.forEachIndexed { index, lt ->
					offY += lt.getLineBottom(lt.lineCount - 1).toFloat()

					linkSpoilers[index]?.forEach {
						it.startRipple(nx.toFloat(), y - getYOffsetForType(SPOILER_TYPE_LINK) - offset + offY, rad)
					}
				}
			}

			SPOILER_TYPE_DESCRIPTION -> for (sp in descriptionLayoutSpoilers) {
				sp.startRipple(nx.toFloat(), (y - getYOffsetForType(SPOILER_TYPE_DESCRIPTION)).toFloat(), rad)
			}

			SPOILER_TYPE_DESCRIPTION2 -> for (sp in descriptionLayout2Spoilers) {
				sp.startRipple(nx.toFloat(), (y - getYOffsetForType(SPOILER_TYPE_DESCRIPTION2)).toFloat(), rad)
			}
		}

		for (i in SPOILER_TYPE_LINK..SPOILER_TYPE_DESCRIPTION2) {
			if (i != spoilerTypePressed) {
				when (i) {
					SPOILER_TYPE_LINK -> {
						linkLayout.forEachIndexed { index, lt ->
							offY += lt.getLineBottom(lt.lineCount - 1).toFloat()

							linkSpoilers[index]?.forEach {
								it.startRipple(it.bounds.centerX().toFloat(), it.bounds.centerY().toFloat(), rad)
							}
						}
					}

					SPOILER_TYPE_DESCRIPTION -> for (sp in descriptionLayoutSpoilers) {
						sp.startRipple(sp.bounds.centerX().toFloat(), sp.bounds.centerY().toFloat(), rad)
					}

					SPOILER_TYPE_DESCRIPTION2 -> for (sp in descriptionLayout2Spoilers) {
						sp.startRipple(sp.bounds.centerX().toFloat(), sp.bounds.centerY().toFloat(), rad)
					}
				}
			}
		}

		spoilerTypePressed = -1
		spoilerPressed = null
	}

	private fun getYOffsetForType(type: Int): Int {
		return when (type) {
			SPOILER_TYPE_LINK -> linkY
			SPOILER_TYPE_DESCRIPTION -> descriptionY
			SPOILER_TYPE_DESCRIPTION2 -> description2Y
			else -> linkY
		}
	}

	fun getLink(num: Int): String? {
		return if (num < 0 || num >= links.size) {
			null
		}
		else {
			links[num].toString()
		}
	}

	private fun resetPressedLink() {
		pressedLink = -1
		linkPreviewPressed = false
		cancelCheckLongPress()
		invalidate()
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		if (checkBox.visibility != VISIBLE) {
			checkBox.visibility = VISIBLE
		}

		checkBox.setChecked(checked, animated)
	}

	private var urlPaint: Paint? = null

	init {
		isFocusable = true

		urlPath = LinkPath()
		urlPath.setUseRoundRect(true)

		titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		titleTextPaint.setTypeface(Theme.TYPEFACE_BOLD)
		titleTextPaint.color = context.getColor(R.color.text)

		descriptionTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		descriptionTextPaint.setTypeface(Theme.TYPEFACE_DEFAULT)

		titleTextPaint.textSize = AndroidUtilities.dp(14f).toFloat()

		descriptionTextPaint.textSize = AndroidUtilities.dp(14f).toFloat()

		setWillNotDraw(false)

		linkImageView = ImageReceiver(this)
		linkImageView.setRoundRadius(AndroidUtilities.dp(4f))

		letterDrawable = LetterDrawable()

		checkBox = CheckBox2(context, 21)
		checkBox.visibility = INVISIBLE
		checkBox.setColor(0, context.getColor(R.color.background), context.getColor(R.color.brand))
		checkBox.setDrawUnchecked(false)
		checkBox.setDrawBackgroundAsArc(2)

		addView(checkBox, createFrame(24, 24f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 44).toFloat(), 44f, (if (LocaleController.isRTL) 44 else 0).toFloat(), 0f))

		if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
			description2TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
			description2TextPaint?.textSize = AndroidUtilities.dp(13f).toFloat()
			description2TextPaint?.setTypeface(Theme.TYPEFACE_DEFAULT)
		}

		captionTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		captionTextPaint.textSize = AndroidUtilities.dp(13f).toFloat()
		captionTextPaint.setTypeface(Theme.TYPEFACE_DEFAULT)
	}

	override fun onDraw(canvas: Canvas) {
		if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
			description2TextPaint?.color = context.getColor(R.color.dark_gray)
		}

		if (dateLayout != null) {
			canvas.save()
			canvas.translate((AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()) + if (LocaleController.isRTL) 0 else dateLayoutX).toFloat(), titleY.toFloat())
			dateLayout?.draw(canvas)
			canvas.restore()
		}

		if (titleLayout != null) {
			canvas.save()

			var x = AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat()

			if (LocaleController.isRTL) {
				x += (if (dateLayout == null) 0 else dateLayout!!.width + AndroidUtilities.dp(4f)).toFloat()
			}

			canvas.translate(x, titleY.toFloat())
			titleLayout?.draw(canvas)
			canvas.restore()
		}

		if (captionLayout != null) {
			captionTextPaint.color = context.getColor(R.color.text)
			canvas.save()
			canvas.translate(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), captionY.toFloat())
			captionLayout?.draw(canvas)
			canvas.restore()
		}

		if (descriptionLayout != null) {
			descriptionTextPaint.color = context.getColor(R.color.text)
			canvas.save()
			canvas.translate(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), descriptionY.toFloat())
			SpoilerEffect.renderWithRipple(this, false, descriptionTextPaint.color, -AndroidUtilities.dp(2f), patchedDescriptionLayout, descriptionLayout, descriptionLayoutSpoilers, canvas, false)
			canvas.restore()
		}

		if (descriptionLayout2 != null) {
			descriptionTextPaint.color = context.getColor(R.color.text)
			canvas.save()
			canvas.translate(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), description2Y.toFloat())
			SpoilerEffect.renderWithRipple(this, false, descriptionTextPaint.color, -AndroidUtilities.dp(2f), patchedDescriptionLayout2, descriptionLayout2, descriptionLayout2Spoilers, canvas, false)
			canvas.restore()
		}

		if (linkLayout.isNotEmpty()) {
			descriptionTextPaint.color = context.getColor(R.color.brand)

			var offset = 0

			for (a in linkLayout.indices) {
				val layout = linkLayout[a]
				val spoilers = linkSpoilers[a]

				if (layout.lineCount > 0) {
					canvas.save()
					canvas.translate(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), (linkY + offset).toFloat())

					path.rewind()

					if (spoilers != null) {
						for (eff in spoilers) {
							val b = eff.bounds
							path.addRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), Path.Direction.CW)
						}
					}

					if (urlPaint == null) {
						urlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
						urlPaint?.setPathEffect(CornerPathEffect(AndroidUtilities.dp(4f).toFloat()))
					}

					urlPaint?.color = context.getColor(R.color.brand)

					canvas.save()
					canvas.clipPath(path, Region.Op.DIFFERENCE)

					if (pressedLink == a) {
						canvas.drawPath(urlPath, urlPaint!!)
					}

					layout.draw(canvas)

					canvas.restore()
					canvas.save()
					canvas.clipPath(path)

					path.rewind()

					spoilers?.firstOrNull()?.getRipplePath(path)

					canvas.clipPath(path)

					if (pressedLink == a) {
						canvas.drawPath(urlPath, urlPaint!!)
					}

					layout.draw(canvas)
					canvas.restore()

					if (spoilers != null) {
						for (eff in spoilers) {
							eff.draw(canvas)
						}
					}

					canvas.restore()

					offset += layout.getLineBottom(layout.lineCount - 1)
				}
			}
		}

		if (fromInfoLayout != null) {
			canvas.save()
			canvas.translate(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else AndroidUtilities.leftBaseline).toFloat()).toFloat(), fromInfoLayoutY.toFloat())
			fromInfoLayout?.draw(canvas)
			canvas.restore()
		}

		letterDrawable.draw(canvas)

		if (drawLinkImageView) {
			linkImageView.draw(canvas)
		}

		if (needDivider) {
			if (LocaleController.isRTL) {
				canvas.drawLine(0f, (measuredHeight - 1).toFloat(), (measuredWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat())).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
			else {
				canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline.toFloat()).toFloat(), (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)


		info.text = buildString {
			titleLayout?.let {
				append(it.text)
			}

			descriptionLayout?.let {
				append(", ")
				append(it.text)
			}

			descriptionLayout2?.let {
				append(", ")
				append(it.text)
			}
		}

		if (checkBox.isChecked) {
			info.isChecked = true
			info.isCheckable = true
		}
	}

	companion object {
		private const val SPOILER_TYPE_LINK = 0
		private const val SPOILER_TYPE_DESCRIPTION = 1
		private const val SPOILER_TYPE_DESCRIPTION2 = 2
		const val VIEW_TYPE_DEFAULT = 0
		const val VIEW_TYPE_GLOBAL_SEARCH = 1
	}
}
