/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.ChatListItemAnimator
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.messenger.Emoji.EmojiSpan
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.messageobject.TextLayoutBlock
import org.telegram.ui.ActionBar.MessageDrawable
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedEmojiSpan.EmojiGroupedSpans
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EmptyStubSpan
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.spoilers.SpoilerEffect
import kotlin.math.abs
import kotlin.math.max

class TextMessageEnterTransition(private val messageView: ChatMessageCell, private val chatActivity: ChatActivity, private val listView: RecyclerListView, private val container: MessageEnterTransitionContainer) : MessageEnterTransitionContainer.Transition {
	private val currentAccount = UserConfig.selectedAccount
	private val gradientMatrix = Matrix()
	private var animatedEmojiStack: EmojiGroupedSpans? = null
	private var animationIndex = -1
	private var animator: ValueAnimator? = null
	private var changeColor = false
	private var crossfadeTextBitmap: Bitmap? = null
	private var crossfadeTextOffset = 0f
	private var drawBitmaps = false
	private var drawableFromBottom: Float = 0f
	private var drawableFromTop: Float = 0f
	private var enterView: ChatActivityEnterView? = null
	private var fromMessageDrawable: Drawable? = null
	private var fromRadius: Float = 0f
	private var fromStartX: Float = 0f
	private var fromStartY: Float = 0f
	private var initBitmaps = false
	private var lastMessageX = 0f
	private var lastMessageY = 0f
	private var messageId: Int = 0
	private var replayFromColor = 0
	private var replayObjectFromColor = 0
	private var replyFromObjectStartY = 0f
	private var replyFromStartX = 0f
	private var replyFromStartY = 0f
	private var replyMessageDx = 0f
	private var replyNameDx = 0f
	private var rtlLayout: StaticLayout? = null
	private var scaleFrom: Float = 0f
	private var scaleY: Float = 0f
	private var textLayoutBitmap: Bitmap? = null
	private var textLayoutBitmapRtl: Bitmap? = null
	private var textLayoutBlock: TextLayoutBlock? = null
	private var toXOffset: Float = 0f
	private var toXOffsetRtl: Float = 0f
	var bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	var crossfade: Boolean = false
	var currentMessageObject: MessageObject? = null
	var fromColor: Int = 0
	var hasReply: Boolean = false
	var layout: StaticLayout? = null
	var progress = 0f
	var textX = 0f
	var textY = 0f
	var toColor: Int = 0

	private val gradientShader by lazy {
		LinearGradient(0f, AndroidUtilities.dp(12f).toFloat(), 0f, 0f, 0, -0x1000000, Shader.TileMode.CLAMP)
	}

	private val gradientPaint by lazy {
		Paint(Paint.ANTI_ALIAS_FLAG).apply {
			xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
			setShader(gradientShader)
		}
	}

	init {
		if (!(messageView.getMessageObject()?.textLayoutBlocks == null || (messageView.getMessageObject()?.textLayoutBlocks?.size ?: 0) > 1 || messageView.getMessageObject()?.textLayoutBlocks.isNullOrEmpty() || (messageView.getMessageObject()?.textLayoutBlocks?.firstOrNull()?.textLayout?.lineCount ?: 0) > 10)) {
			enterView = chatActivity.chatActivityEnterView

			val chatActivityEnterView = chatActivity.chatActivityEnterView

			if (chatActivityEnterView?.editField?.layout != null) {
				fromRadius = chatActivityEnterView.recordCircle.drawingCircleRadius
				bitmapPaint.isFilterBitmap = true
				currentMessageObject = messageView.getMessageObject()

				if (!messageView.transitionParams.wasDraw) {
					messageView.draw(Canvas())
				}

				messageView.enterTransitionInProgress = true

				val editText = chatActivityEnterView.editField.layout.text
				var text = messageView.getMessageObject()?.messageText ?: ""

				crossfade = false

				var linesOffset = 0
				var layoutH = chatActivityEnterView.editField.layout.height
				var textPaint = Theme.chat_msgTextPaint
// 				var emojiSize = AndroidUtilities.dp(20f)
				val emojiOnlyCount = messageView.getMessageObject()?.emojiOnlyCount ?: 0

				if (emojiOnlyCount > 0) {
					textPaint = when (emojiOnlyCount) {
						1, 2 -> Theme.chat_msgTextPaintEmoji[0]
						3 -> Theme.chat_msgTextPaintEmoji[1]
						4 -> Theme.chat_msgTextPaintEmoji[2]
						5 -> Theme.chat_msgTextPaintEmoji[3]
						6 -> Theme.chat_msgTextPaintEmoji[4]
						7, 8, 9 -> Theme.chat_msgTextPaintEmoji[5]
						else -> Theme.chat_msgTextPaintEmoji[5]
					}

//					if (textPaint != null) {
//						emojiSize = (textPaint.textSize + AndroidUtilities.dp(4f)).toInt()
//					}
				}

				var containsSpans = false

				if (text is Spannable) {
					val objects = text.getSpans(0, text.length, Any::class.java)

					for (`object` in objects) {
						if (`object` !is EmojiSpan) {
							containsSpans = true
							break
						}
					}
				}

				if (editText.length != text.length || containsSpans) {
					crossfade = true

					val newStart = IntArray(1)
					val trimmedStr = AndroidUtilities.trim(editText, newStart)

					if (newStart[0] > 0) {
						linesOffset = chatActivityEnterView.editField.layout.getLineTop(chatActivityEnterView.editField.layout.getLineForOffset(newStart[0]))
						layoutH = chatActivityEnterView.editField.layout.getLineBottom(chatActivityEnterView.editField.layout.getLineForOffset(newStart[0] + trimmedStr.length)) - linesOffset
					}

					// text = AnimatedEmojiSpan.cloneSpans(text)
					text = Emoji.replaceEmoji(editText, textPaint?.getFontMetricsInt(), false) ?: editText
				}

				scaleFrom = chatActivityEnterView.editField.textSize / textPaint.textSize

				var n = chatActivityEnterView.editField.layout.lineCount
				val width = (chatActivityEnterView.editField.layout.width / scaleFrom).toInt()

				var layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width).setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
				}
				else {
					StaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				}.also {
					this.layout = it
				}

				animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, null, animatedEmojiStack, layout)

				val textViewY = chatActivityEnterView.y + chatActivityEnterView.editField.y + (chatActivityEnterView.editField.parent as View).y + (chatActivityEnterView.editField.parent.parent as View).y

				fromStartX = chatActivityEnterView.x + chatActivityEnterView.editField.x + (chatActivityEnterView.editField.parent as View).x + (chatActivityEnterView.editField.parent.parent as View).x
				fromStartY = textViewY + AndroidUtilities.dp(10f) - chatActivityEnterView.editField.scrollY + linesOffset
				toXOffset = 0f

				var minX = Float.MAX_VALUE

				for (i in 0 until layout.lineCount) {
					val begin = layout.getLineLeft(i)

					if (begin < minX) {
						minX = begin
					}
				}

				if (minX != Float.MAX_VALUE) {
					toXOffset = minX
				}

				scaleY = layoutH / (layout.height * scaleFrom)
				drawableFromTop = textViewY + AndroidUtilities.dp(4f)

				if (enterView?.isTopViewVisible == true) {
					drawableFromTop -= AndroidUtilities.dp(12f).toFloat()
				}

				drawableFromBottom = textViewY + chatActivityEnterView.editField.measuredHeight
				textLayoutBlock = messageView.getMessageObject()?.textLayoutBlocks?.firstOrNull()

				val messageTextLayout = textLayoutBlock?.textLayout
				var normalLinesCount = 0
				var rtlLinesCount = 0
				val context = messageView.context

				if (abs(ColorUtils.calculateLuminance(ResourcesCompat.getColor(context.resources, R.color.white, null)) - ColorUtils.calculateLuminance(ResourcesCompat.getColor(context.resources, R.color.text, null))) > 0.2f) {
					crossfade = true
					changeColor = true
				}

				fromColor = ResourcesCompat.getColor(context.resources, R.color.text, null)
				toColor = ResourcesCompat.getColor(context.resources, R.color.white, null)

				if (messageTextLayout?.lineCount == layout.lineCount) {
					n = messageTextLayout.lineCount

					for (i in 0 until n) {
						if (isRtlLine(layout, i)) {
							rtlLinesCount++
						}
						else {
							normalLinesCount++
						}

						if (messageTextLayout.getLineEnd(i) != layout.getLineEnd(i)) {
							crossfade = true
							break
						}
					}
				}
				else {
					crossfade = true
				}

				minX = Float.MAX_VALUE

				if (!crossfade && rtlLinesCount > 0 && normalLinesCount > 0) {
					val normalText = SpannableString(text)
					val rtlText = SpannableString(text)

					for (i in 0 until n) {
						if (isRtlLine(layout, i)) {
							normalText.setSpan(EmptyStubSpan(), layout.getLineStart(i), layout.getLineEnd(i), 0)

							val begin = layout.getLineLeft(i)

							if (begin < minX) {
								minX = begin
							}
						}
						else {
							rtlText.setSpan(EmptyStubSpan(), layout.getLineStart(i), layout.getLineEnd(i), 0)
						}
					}

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
						layout = StaticLayout.Builder.obtain(normalText, 0, normalText.length, textPaint, width).setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
						rtlLayout = StaticLayout.Builder.obtain(rtlText, 0, rtlText.length, textPaint, width).setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
					}
					else {
						layout = StaticLayout(normalText, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
						rtlLayout = StaticLayout(rtlText, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
					}
				}

				toXOffsetRtl = (layout.width - (messageView.getMessageObject()?.textLayoutBlocks?.firstOrNull()?.textLayout?.width ?: 0)).toFloat()

				try {
					if (drawBitmaps) {
						val textLayoutBitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888).also {
							this.textLayoutBitmap = it
						}

						var bitmapCanvas = Canvas(textLayoutBitmap)

						layout.draw(bitmapCanvas)

						rtlLayout?.let { rtlLayout ->
							val textLayoutBitmapRtl = Bitmap.createBitmap(rtlLayout.width, rtlLayout.height, Bitmap.Config.ARGB_8888).also {
								this.textLayoutBitmapRtl = it
							}

							bitmapCanvas = Canvas(textLayoutBitmapRtl)

							rtlLayout.draw(bitmapCanvas)
						}

						if (crossfade) {
							if (messageView.measuredHeight < listView.measuredHeight) {
								crossfadeTextOffset = 0f
								crossfadeTextBitmap = Bitmap.createBitmap(messageView.getMeasuredWidth(), messageView.measuredHeight, Bitmap.Config.ARGB_8888)
							}
							else {
								crossfadeTextOffset = messageView.getTop().toFloat()
								crossfadeTextBitmap = Bitmap.createBitmap(messageView.getMeasuredWidth(), listView.measuredHeight, Bitmap.Config.ARGB_8888)
							}
						}
					}
				}
				catch (e: Exception) {
					drawBitmaps = false
				}

				hasReply = messageView.getMessageObject()?.replyMsgId != 0 && messageView.replyNameLayout != null

				if (hasReply) {
					chatActivity.replyNameTextView?.let { replyNameTextView ->
						replyFromStartX = replyNameTextView.x + (replyNameTextView.parent as View).x
						replyFromStartY = replyNameTextView.y + (replyNameTextView.parent.parent as View).y + (replyNameTextView.parent.parent.parent as View).y
					}

					chatActivity.replyObjectTextView?.let { replyNameTextView ->
						replyFromObjectStartY = replyNameTextView.y + (replyNameTextView.parent.parent as View).y + (replyNameTextView.parent.parent.parent as View).y
						replayFromColor = chatActivity.replyNameTextView?.textColor ?: 0
						replayObjectFromColor = chatActivity.replyObjectTextView?.textColor ?: 0
						drawableFromTop -= AndroidUtilities.dp(46f).toFloat()
					}
				}

				messageId = messageView.getMessageObject()?.stableId ?: 0

				chatActivityEnterView.editField.setAlpha(0f)
				chatActivityEnterView.textTransitionIsRunning = true

				val replyNameLayout = messageView.replyNameLayout

				if (replyNameLayout != null && replyNameLayout.text.length > 1) {
					if (replyNameLayout.getPrimaryHorizontal(0) != 0f) {
						replyNameDx = replyNameLayout.width - replyNameLayout.getLineWidth(0)
					}
				}

				val replyTextLayout = messageView.replyTextLayout

				if (replyTextLayout != null && replyTextLayout.text.isNotEmpty()) {
					if (replyTextLayout.getPrimaryHorizontal(0) != 0f) {
						replyMessageDx = replyTextLayout.width - replyTextLayout.getLineWidth(0)
					}
				}

				animator = ValueAnimator.ofFloat(0f, 1f)

				animator?.addUpdateListener {
					progress = it.getAnimatedValue() as Float
					chatActivityEnterView.editField.setAlpha(progress)
					container.invalidate()
				}

				animator?.interpolator = LinearInterpolator()
				animator?.setDuration(ChatListItemAnimator.DEFAULT_DURATION)

				container.addTransition(this)

				animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null)

				animator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex)

						container.removeTransition(this@TextMessageEnterTransition)

						messageView.enterTransitionInProgress = false

						chatActivityEnterView.textTransitionIsRunning = false
						chatActivityEnterView.editField.setAlpha(1f)

						chatActivity.replyNameTextView?.setAlpha(1f)
						chatActivity.replyObjectTextView?.setAlpha(1f)

						AnimatedEmojiSpan.release(animatedEmojiStack)
					}
				})

				if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH) {
					val drawable = messageView.getCurrentBackgroundDrawable(true)

					if (drawable != null) {
						fromMessageDrawable = drawable.getTransitionDrawable(context.getColor(R.color.background))
					}
				}
			}
		}
	}

	fun start() {
		animator?.start()
	}

	private fun isRtlLine(layout: Layout?, line: Int): Boolean {
		if (layout == null) {
			return false
		}

		return layout.getLineRight(line) == layout.width.toFloat() && layout.getLineLeft(line) != 0f
	}

	override fun onDraw(canvas: Canvas) {
		val crossfadeTextBitmap = crossfadeTextBitmap

		if (drawBitmaps && !initBitmaps && crossfadeTextBitmap != null && messageView.transitionParams.wasDraw) {
			initBitmaps = true

			val bitmapCanvas = Canvas(crossfadeTextBitmap)
			bitmapCanvas.translate(0f, crossfadeTextOffset)

			messageView.animatedEmojiStack?.clearPositions()

			messageView.drawMessageText(bitmapCanvas, messageView.getMessageObject()?.textLayoutBlocks, true, 1f, true)
			messageView.drawAnimatedEmojis(bitmapCanvas, 1f)
		}

		val listViewBottom = listView.y - container.y + listView.measuredHeight
		val fromX = fromStartX - container.x
		val fromY = fromStartY - container.y

		textX = messageView.textX.toFloat()
		textY = messageView.textY.toFloat()

		val messageViewX: Float
		var messageViewY: Float

		if (messageView.getMessageObject()?.stableId != messageId) {
			return
		}
		else {
			messageViewX = messageView.getX() + listView.x - container.x
			messageViewY = messageView.getTop() + listView.top - container.y
			messageViewY += (enterView?.topViewHeight ?: 0f)

			lastMessageX = messageViewX
			lastMessageY = messageViewY
		}

		val progress = ChatListItemAnimator.DEFAULT_INTERPOLATOR.getInterpolation(progress)
		val alphaProgress = if (this.progress > 0.4f) 1f else this.progress / 0.4f
		val p2 = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(this.progress)
		val progressX = CubicBezierInterpolator.EASE_OUT.getInterpolation(p2)
		val toX = messageViewX + textX
		val toY = messageViewY + textY
		val clipBottom = (container.measuredHeight * (1f - progressX) + listViewBottom * progressX).toInt()
		val messageViewOverscrolled = messageView.getBottom() - AndroidUtilities.dp(4f) > listView.measuredHeight
		val clipBottomWithAlpha = messageViewOverscrolled && messageViewY + messageView.measuredHeight - AndroidUtilities.dp(8f) > clipBottom && container.measuredHeight > 0

		if (clipBottomWithAlpha) {
			canvas.saveLayerAlpha(0f, max(0.0, messageViewY.toDouble()).toFloat(), container.measuredWidth.toFloat(), container.measuredHeight.toFloat(), 255)
		}

		canvas.save()
		canvas.clipRect(0f, listView.y + chatActivity.chatListViewPadding - container.y - AndroidUtilities.dp(3f), container.measuredWidth.toFloat(), container.measuredHeight.toFloat())
		canvas.save()

		val drawableX = messageViewX + messageView.getBackgroundDrawableLeft() + (fromX - (toX - toXOffset)) * (1f - progressX)
		val drawableToTop = messageViewY + messageView.getBackgroundDrawableTop()
		val drawableTop = (drawableFromTop - container.y) * (1f - progress) + drawableToTop * progress
		val drawableH = (messageView.getBackgroundDrawableBottom() - messageView.getBackgroundDrawableTop()).toFloat()
		val drawableBottom = (drawableFromBottom - container.y) * (1f - progress) + (drawableToTop + drawableH) * progress
		val drawableRight = (messageViewX + messageView.getBackgroundDrawableRight() + AndroidUtilities.dp(4f) * (1f - progressX)).toInt()
		var drawable: MessageDrawable? = null

		if (currentMessageObject?.isAnimatedEmojiStickers != true) {
			drawable = messageView.getCurrentBackgroundDrawable(true)
		}

		if (drawable != null) {
			messageView.setBackgroundTopY(container.top - listView.top)

			val shadowDrawable = drawable.getShadowDrawable()

			if (alphaProgress != 1f) {
				fromMessageDrawable?.setBounds(drawableX.toInt(), drawableTop.toInt(), drawableRight, drawableBottom.toInt())
				fromMessageDrawable?.draw(canvas)
			}

			if (shadowDrawable != null) {
				shadowDrawable.alpha = (255 * progressX).toInt()
				shadowDrawable.setBounds(drawableX.toInt(), drawableTop.toInt(), drawableRight, drawableBottom.toInt())
				shadowDrawable.draw(canvas)
				shadowDrawable.alpha = 255
			}

			drawable.setAlpha((255 * alphaProgress).toInt())
			drawable.setBounds(drawableX.toInt(), drawableTop.toInt(), drawableRight, drawableBottom.toInt())
			drawable.drawFullBubble = true
			drawable.draw(canvas)
			drawable.drawFullBubble = false
			drawable.setAlpha(255)
		}

		canvas.restore()
		canvas.save()

		if (drawable != null) {
			if (currentMessageObject?.isOutOwner == true) {
				canvas.clipRect(drawableX + AndroidUtilities.dp(4f), drawableTop + AndroidUtilities.dp(4f), (drawableRight - AndroidUtilities.dp(10f)).toFloat(), drawableBottom - AndroidUtilities.dp(4f))
			}
			else {
				canvas.clipRect(drawableX + AndroidUtilities.dp(4f), drawableTop + AndroidUtilities.dp(4f), (drawableRight - AndroidUtilities.dp(4f)).toFloat(), drawableBottom - AndroidUtilities.dp(4f))
			}
		}

		val dy = messageViewY + (fromY - toY) * (1f - progress)

		canvas.translate(messageView.left + listView.x - container.x, dy)

		messageView.drawTime(canvas, alphaProgress, false)
		messageView.drawNamesLayout(canvas, alphaProgress)
		messageView.drawCommentButton(canvas, alphaProgress)
		messageView.drawCaptionLayout(canvas, false, alphaProgress)
		messageView.drawLinkPreview(canvas, alphaProgress)

		canvas.restore()

		if (hasReply) {
			val context = messageView.context

			chatActivity.replyNameTextView?.setAlpha(0f)
			chatActivity.replyObjectTextView?.setAlpha(0f)

			var fromReplayX = replyFromStartX - container.x
			val fromReplayY = replyFromStartY - container.y
			val toReplayX = messageViewX + messageView.replyStartX
			val toReplayY = messageViewY + messageView.replyStartY
			val replyOwnerMessageColor: Int
			val replyLineColor: Int

//			val replyMessageColor: Int = if (currentMessageObject?.hasValidReplyMessageObject() == true && (currentMessageObject?.replyMessageObject?.type == 0 || !currentMessageObject?.replyMessageObject?.caption.isNullOrEmpty()) && !(currentMessageObject?.replyMessageObject?.messageOwner?.media is TL_messageMediaGame || currentMessageObject?.replyMessageObject?.messageOwner?.media is TL_messageMediaInvoice)) {
//				getThemedColor(Theme.key_chat_outReplyMessageText)
//			}
//			else {
//				getThemedColor(Theme.key_chat_outReplyMediaMessageText)
//			}

			val replyMessageColor = context.getColor(R.color.white)

			if (currentMessageObject?.isOutOwner == true) {
				replyOwnerMessageColor = context.getColor(R.color.white)
				replyLineColor = context.getColor(R.color.white)
			}
			else {
				replyOwnerMessageColor = context.getColor(R.color.brand)
				replyLineColor = context.getColor(R.color.brand)
			}

			Theme.chat_replyTextPaint.setColor(ColorUtils.blendARGB(replayObjectFromColor, replyMessageColor, progress))
			Theme.chat_replyNamePaint.setColor(ColorUtils.blendARGB(replayFromColor, replyOwnerMessageColor, progress))

			if (messageView.needReplyImage) {
				fromReplayX -= AndroidUtilities.dp(44f).toFloat()
			}

			val replyX = fromReplayX * (1f - progressX) + toReplayX * progressX
			val replyY = (fromReplayY + AndroidUtilities.dp(12f) * progress) * (1f - progress) + toReplayY * progress

			Theme.chat_replyLinePaint.setColor(ColorUtils.setAlphaComponent(replyLineColor, (Color.alpha(replyLineColor) * progressX).toInt()))

			canvas.drawRect(replyX, replyY, replyX + AndroidUtilities.dp(2f), replyY + AndroidUtilities.dp(35f), Theme.chat_replyLinePaint)
			canvas.save()
			canvas.translate(AndroidUtilities.dp(10f) * progressX, 0f)

			if (messageView.needReplyImage) {
				canvas.save()

				messageView.replyImageReceiver.setImageCoordinates(replyX, replyY, AndroidUtilities.dp(35f).toFloat(), AndroidUtilities.dp(35f).toFloat())
				messageView.replyImageReceiver.draw(canvas)

				canvas.translate(replyX, replyY)
				canvas.restore()
				canvas.translate(AndroidUtilities.dp(44f).toFloat(), 0f)
			}

			val replyToMessageX = toReplayX - replyMessageDx
			val replyToNameX = toReplayX - replyNameDx
			val replyMessageX = (fromReplayX - replyMessageDx) * (1f - progressX) + replyToMessageX * progressX
			val replyNameX = fromReplayX * (1f - progressX) + replyToNameX * progressX

			messageView.replyNameLayout?.let {
				canvas.save()
				canvas.translate(replyNameX, replyY)

				it.draw(canvas)

				canvas.restore()
			}

			messageView.replyTextLayout?.let {
				canvas.save()
				canvas.translate(replyMessageX, replyY + AndroidUtilities.dp(19f))
				canvas.save()

				SpoilerEffect.clipOutCanvas(canvas, messageView.replySpoilers)

				AnimatedEmojiSpan.drawAnimatedEmojis(canvas, it, messageView.animatedEmojiReplyStack, 0f, messageView.replySpoilers, 0f, 0f, 0f, 1f)

				it.draw(canvas)

				canvas.restore()

				for (eff in messageView.replySpoilers) {
					if (eff.shouldInvalidateColor()) {
						eff.setColor(it.paint.color)
					}

					eff.draw(canvas)
				}

				canvas.restore()
			}

			canvas.restore()
		}

		canvas.save()

		if (messageView.getMessageObject()?.type != MessageObject.TYPE_EMOJIS) {
			canvas.clipRect(drawableX + AndroidUtilities.dp(4f), drawableTop + AndroidUtilities.dp(4f), (drawableRight - AndroidUtilities.dp(4f)).toFloat(), drawableBottom - AndroidUtilities.dp(4f))
		}

		val scale = progressX + scaleFrom * (1f - progressX)

		val scale2: Float = if (drawBitmaps) {
			progressX + scaleY * (1f - progressX)
		}
		else {
			1f
		}

		canvas.save()
		canvas.translate(fromX * (1f - progressX) + (toX - toXOffset) * progressX, fromY * (1f - progress) + (toY + (textLayoutBlock?.textYOffset ?: 0f)) * progress)
		canvas.scale(scale, scale * scale2, 0f, 0f)

		if (drawBitmaps) {
			if (crossfade) {
				bitmapPaint.setAlpha((255 * (1f - alphaProgress)).toInt())
			}

			textLayoutBitmap?.let {
				canvas.drawBitmap(it, 0f, 0f, bitmapPaint)
			}
		}
		else {
			val layout = layout

			if (layout != null) {
				if (crossfade && changeColor) {
					val oldColor = layout.paint.color
					layout.paint.setColor(ColorUtils.blendARGB(fromColor, toColor, alphaProgress))
					canvas.saveLayerAlpha(0f, 0f, layout.width.toFloat(), layout.height.toFloat(), (255 * (1f - alphaProgress)).toInt())
					layout.draw(canvas)
					AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, animatedEmojiStack, 0f, null, 0f, 0f, 0f, 1f - alphaProgress)
					layout.paint.setColor(oldColor)
					canvas.restore()
				}
				else if (crossfade) {
					canvas.saveLayerAlpha(0f, 0f, layout.width.toFloat(), layout.height.toFloat(), (255 * (1f - alphaProgress)).toInt())
					layout.draw(canvas)
					AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, animatedEmojiStack, 0f, null, 0f, 0f, 0f, 1f - alphaProgress)
					canvas.restore()
				}
				else {
					layout.draw(canvas)
					AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, animatedEmojiStack, 0f, null, 0f, 0f, 0f, 1f)
				}
			}
		}

		canvas.restore()

		if (rtlLayout != null) {
			canvas.save()
			canvas.translate(fromX * (1f - progressX) + (toX - toXOffsetRtl) * progressX, fromY * (1f - progress) + (toY + (textLayoutBlock?.textYOffset ?: 0f)) * progress)
			canvas.scale(scale, scale * scale2, 0f, 0f)

			if (drawBitmaps) {
				if (crossfade) {
					bitmapPaint.setAlpha((255 * (1f - alphaProgress)).toInt())
				}

				textLayoutBitmapRtl?.let {
					canvas.drawBitmap(it, 0f, 0f, bitmapPaint)
				}
			}
			else {
				if (crossfade && changeColor) {
					val oldColor = rtlLayout?.paint?.color ?: 0
					val oldAlpha = Color.alpha(oldColor)

					rtlLayout?.paint?.setColor(ColorUtils.setAlphaComponent(ColorUtils.blendARGB(fromColor, toColor, alphaProgress), (oldAlpha * (1f - alphaProgress)).toInt()))
					rtlLayout?.draw(canvas)
					rtlLayout?.paint?.setColor(oldColor)
				}
				else if (crossfade) {
					val oldAlpha = rtlLayout?.paint?.alpha ?: 0

					rtlLayout?.paint?.setAlpha((oldAlpha * (1f - alphaProgress)).toInt())
					rtlLayout?.draw(canvas)
					rtlLayout?.paint?.setAlpha(oldAlpha)
				}
				else {
					rtlLayout?.draw(canvas)
				}
			}

			canvas.restore()
		}

		if (crossfade) {
			canvas.save()
			canvas.translate(messageView.left + listView.x - container.x + (fromX - toX) * (1f - progressX), dy)
			canvas.scale(scale, scale * scale2, messageView.textX.toFloat(), messageView.textY.toFloat())
			canvas.translate(0f, -crossfadeTextOffset)

			if (crossfadeTextBitmap != null) {
				bitmapPaint.setAlpha((255 * alphaProgress).toInt())
				canvas.drawBitmap(crossfadeTextBitmap, 0f, 0f, bitmapPaint)
			}
			else {
				val oldColor = Theme.chat_msgTextPaint.color

				Theme.chat_msgTextPaint.setColor(toColor)

				messageView.drawMessageText(canvas, messageView.getMessageObject()?.textLayoutBlocks, false, alphaProgress, true)

				messageView.drawAnimatedEmojis(canvas, alphaProgress)

				if (Theme.chat_msgTextPaint.color != oldColor) {
					Theme.chat_msgTextPaint.setColor(oldColor)
				}
			}
			canvas.restore()
		}

		canvas.restore()

		if (clipBottomWithAlpha) {
			gradientMatrix.setTranslate(0f, clipBottom.toFloat())
			gradientShader.setLocalMatrix(gradientMatrix)
			canvas.drawRect(0f, clipBottom.toFloat(), container.measuredWidth.toFloat(), container.measuredHeight.toFloat(), gradientPaint)
			canvas.restore()
		}

		enterView?.let { enterView ->
			val sendProgress = if (this.progress > 0.4f) 1f else this.progress / 0.4f

			if (sendProgress == 1f) {
				enterView.textTransitionIsRunning = false
			}

			if (enterView.getSendButton().visibility == View.VISIBLE && sendProgress < 1f) {
				canvas.save()
				canvas.translate(enterView.x + enterView.getSendButton().x + (enterView.getSendButton().parent as View).x + (enterView.getSendButton().parent.parent as View).x - container.x + AndroidUtilities.dp(52f) * sendProgress, enterView.y + enterView.getSendButton().y + (enterView.getSendButton().parent as View).y + (enterView.getSendButton().parent.parent as View).y - container.y)
				enterView.getSendButton().draw(canvas)
				canvas.restore()
				canvas.restore()
			}
		}
	}
}
