/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui.Components.Reactions

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DocumentObject.getSvgThumb
import org.telegram.messenger.Emoji
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.tlrpc.Reaction
import org.telegram.tgnet.tlrpc.ReactionCount
import org.telegram.tgnet.tlrpc.TL_reactionCustomEmoji
import org.telegram.tgnet.tlrpc.TL_reactionEmoji
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AvatarsDrawable
import org.telegram.ui.Components.CounterView.CounterDrawable
import java.util.Collections
import kotlin.math.abs

class ReactionsLayoutInBubble(private val parentView: ChatMessageCell) {
	private val touchSlop: Float
	private var animateFromTotalHeight = 0
	private var animateHeight = false
	private var animateMove = false
	private var animateWidth = false
	private var currentAccount: Int = UserConfig.selectedAccount
	private var fromX = 0f
	private var fromY = 0f
	private var lastDrawTotalHeight = 0
	private val lastDrawingReactionButtons = mutableMapOf<String, ReactionButton>()
	private val lastDrawingReactionButtonsTmp = mutableMapOf<String, ReactionButton>()
	private var lastDrawnWidth = 0
	private var lastDrawnX = 0f
	private var lastDrawnY = 0f
	private var lastSelectedButton: ReactionButton? = null
	private val outButtons = mutableListOf<ReactionButton>()
	private val reactionButtons = mutableListOf<ReactionButton>()
	private var scrimViewReaction: String? = null
	private var wasDrawn = false
	var animatedReactions = mutableMapOf<VisibleReaction, ImageReceiver>()
	var attached = false
	var availableWidth = 0
	var drawServiceShaderBackground = false
	var fromWidth = 0
	var height = 0
	var isEmpty = false
	var isSmall = false
	var lastLineX = 0
	var lastX = 0f
	var lastY = 0f
	var longPressRunnable: Runnable? = null
	var messageObject: MessageObject? = null
	var positionOffsetY = 0
	var pressed = false
	var width = 0

	private val context: Context
		get() = parentView.context

	@JvmField
	var x = 0

	@JvmField
	var y = 0

	@JvmField
	var totalHeight = 0

	@JvmField
	var hasUnreadReactions = false

	init {
		paint.color = context.getColor(R.color.brand)
		textPaint.color = context.getColor(R.color.text)
		textPaint.textSize = AndroidUtilities.dp(12f).toFloat()
		textPaint.typeface = Theme.TYPEFACE_BOLD
		touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
	}

	fun setMessage(messageObject: MessageObject?, isSmall: Boolean) {
		this.isSmall = isSmall
		this.messageObject = messageObject

		reactionButtons.forEach {
			it.detach()
		}

		hasUnreadReactions = false
		reactionButtons.clear()

		if (messageObject != null) {
			comparator.dialogId = messageObject.dialogId

			val reactions = messageObject.messageOwner?.reactions
			val results = reactions?.results

			if (reactions != null && results != null) {
				var totalCount = 0

				for (i in results.indices) {
					totalCount += results[i].count
				}

				for (i in results.indices) {
					val reactionCount = results[i]
					val button = ReactionButton(reactionCount, isSmall)

					reactionButtons.add(button)

					if (!isSmall) {
						val users = mutableListOf<User>()

						if (messageObject.dialogId > 0) {
							if (reactionCount.count == 2) {
								UserConfig.getInstance(currentAccount).getCurrentUser()?.let {
									users.add(it)
								}

								MessagesController.getInstance(currentAccount).getUser(messageObject.dialogId)?.let {
									users.add(it)
								}
							}
							else {
								if (reactionCount.chosen) {
									UserConfig.getInstance(currentAccount).getCurrentUser()?.let {
										users.add(it)
									}
								}
								else {
									MessagesController.getInstance(currentAccount).getUser(messageObject.dialogId)?.let {
										users.add(it)
									}
								}
							}

							button.users = users

							if (users.isNotEmpty()) {
								button.count = 0
								button.counterDrawable.setCount(0, false)
							}
						}
						else if (reactionCount.count <= 3 && totalCount <= 3) {
							for (j in reactions.recentReactions.indices) {
								val recent = reactions.recentReactions[j]
								val visibleReactionPeer = VisibleReaction.fromTLReaction(recent.reaction)
								val visibleReactionCount = VisibleReaction.fromTLReaction(reactionCount.reaction)

								if (visibleReactionPeer == visibleReactionCount && MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(recent.peer_id)) != null) {
									MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(recent.peer_id))?.let {
										users.add(it)
									}
								}
							}

							button.users = users

							if (users.isNotEmpty()) {
								button.count = 0
								button.counterDrawable.setCount(0, false)
							}
						}
					}

					if (isSmall && reactionCount.count > 1 && reactionCount.chosen) {
						reactionButtons.add(ReactionButton(reactionCount, true))

						reactionButtons[0].isSelected = false
						reactionButtons[1].isSelected = true

						reactionButtons[0].realCount = 1
						reactionButtons[1].realCount = 1

						reactionButtons[1].key += "_"

						break
					}

					if (isSmall && i == 2) {
						break
					}

					if (attached) {
						button.attach()
					}
				}
			}

			if (!isSmall && reactionButtons.isNotEmpty()) {
				comparator.currentAccount = currentAccount

				Collections.sort(reactionButtons, comparator)

				reactionButtons.forEach {
					it.reactionCount.lastDrawnPosition = pointer++
				}
			}

			hasUnreadReactions = MessageObject.hasUnreadReactions(messageObject.messageOwner)
		}

		isEmpty = reactionButtons.isEmpty()
	}

	fun measure(availableWidth: Int, gravity: Int) {
		height = 0
		width = 0
		positionOffsetY = 0
		totalHeight = 0

		if (isEmpty) {
			return
		}

		this.availableWidth = availableWidth

		var maxWidth = 0
		var currentX = 0
		var currentY = 0

		reactionButtons.forEach { button ->
			if (button.isSmall) {
				button.width = AndroidUtilities.dp(14f)
				button.height = AndroidUtilities.dp(14f)
			}
			else {
				button.width = (AndroidUtilities.dp(8f) + AndroidUtilities.dp(20f) + AndroidUtilities.dp(4f))

				if (button.avatarsDrawable != null && !button.users.isNullOrEmpty()) {
					val c1 = 1
					val c2 = if (button.users!!.size > 1) button.users!!.size - 1 else 0

					button.width += (AndroidUtilities.dp(2f) + c1 * AndroidUtilities.dp(20f) + c2 * AndroidUtilities.dp(20f) * 0.8f + AndroidUtilities.dp(1f)).toInt()
					button.avatarsDrawable!!.height = AndroidUtilities.dp(26f)
				}
				else {
					button.width += (button.counterDrawable.textPaint.measureText(button.countText) + AndroidUtilities.dp(8f)).toInt()
				}

				button.height = AndroidUtilities.dp(26f)
			}

			if (currentX + button.width > availableWidth) {
				currentX = 0
				currentY += button.height + AndroidUtilities.dp(4f)
			}

			button.x = currentX
			button.y = currentY

			currentX += button.width + AndroidUtilities.dp(4f)

			if (currentX > maxWidth) {
				maxWidth = currentX
			}
		}

		if (gravity == Gravity.RIGHT && reactionButtons.isNotEmpty()) {
			var fromP = 0
			val startY = reactionButtons[0].y

			for (i in reactionButtons.indices) {
				if (reactionButtons[i].y != startY) {
					val lineOffset = availableWidth - (reactionButtons[i - 1].x + reactionButtons[i - 1].width)

					for (k in fromP until i) {
						reactionButtons[k].x += lineOffset
					}

					fromP = i
				}
			}

			val last = reactionButtons.size - 1
			val lineOffset = availableWidth - (reactionButtons[last].x + reactionButtons[last].width)

			for (k in fromP..last) {
				reactionButtons[k].x += lineOffset
			}
		}

		lastLineX = currentX

		width = if (gravity == Gravity.RIGHT) {
			availableWidth
		}
		else {
			maxWidth
		}

		height = currentY + if (reactionButtons.size == 0) 0 else AndroidUtilities.dp(26f)
		drawServiceShaderBackground = false
	}

	fun draw(canvas: Canvas, animationProgress: Float, drawOnlyReaction: String?) {
		if (isEmpty && outButtons.isEmpty()) {
			return
		}

		var totalX = x.toFloat()
		var totalY = y.toFloat()

		if (isEmpty) {
			totalX = lastDrawnX
			totalY = lastDrawnY
		}
		else if (animateMove) {
			totalX = totalX * animationProgress + fromX * (1f - animationProgress)
			totalY = totalY * animationProgress + fromY * (1f - animationProgress)
		}

		canvas.save()
		canvas.translate(totalX, totalY)

		for (reactionButton in reactionButtons) {
			canvas.save()

			var x = reactionButton.x.toFloat()
			var y = reactionButton.y.toFloat()

			if (animationProgress != 1f && reactionButton.animationType == ANIMATION_TYPE_MOVE) {
				x = reactionButton.x * animationProgress + reactionButton.animateFromX * (1f - animationProgress)
				y = reactionButton.y * animationProgress + reactionButton.animateFromY * (1f - animationProgress)
			}

			canvas.translate(x, y)

			var alpha = 1f

			if (animationProgress != 1f && reactionButton.animationType == ANIMATION_TYPE_IN) {
				val s = 0.5f + 0.5f * animationProgress
				alpha = animationProgress
				canvas.scale(s, s, reactionButton.width / 2f, reactionButton.height / 2f)
			}

			reactionButton.draw(canvas, if (reactionButton.animationType == ANIMATION_TYPE_MOVE) animationProgress else 1f, alpha, drawOnlyReaction != null)

			canvas.restore()
		}

		for (reactionButton in outButtons) {
			canvas.save()
			canvas.translate(reactionButton.x.toFloat(), reactionButton.y.toFloat())

			val s = 0.5f + 0.5f * (1f - animationProgress)

			canvas.scale(s, s, reactionButton.width / 2f, reactionButton.height / 2f)

			reactionButton.draw(canvas, 1f, 1f - animationProgress, false)

			canvas.restore()
		}

		canvas.restore()
	}

	fun recordDrawingState() {
		lastDrawingReactionButtons.clear()

		reactionButtons.forEach {
			it.key?.let { key ->
				lastDrawingReactionButtons[key] = it
			}
		}

		wasDrawn = !isEmpty

		lastDrawnX = x.toFloat()
		lastDrawnY = y.toFloat()

		lastDrawnWidth = width
		lastDrawTotalHeight = totalHeight
	}

	fun animateChange(): Boolean {
		if (messageObject == null) {
			return false
		}

		var changed = false

		lastDrawingReactionButtonsTmp.clear()

		outButtons.forEach {
			it.detach()
		}

		outButtons.clear()

		lastDrawingReactionButtonsTmp.putAll(lastDrawingReactionButtons)

		for (i in reactionButtons.indices) {
			val button = reactionButtons[i]
			var lastButton = lastDrawingReactionButtonsTmp[button.key]

			if (lastButton != null && button.isSmall != lastButton.isSmall) {
				lastButton = null
			}

			if (lastButton != null) {
				lastDrawingReactionButtonsTmp.remove(button.key)

				if (button.x != lastButton.x || button.y != lastButton.y || button.width != lastButton.width || button.count != lastButton.count || button.chosen != lastButton.chosen || button.avatarsDrawable != null || lastButton.avatarsDrawable != null) {
					button.animateFromX = lastButton.x
					button.animateFromY = lastButton.y
					button.animateFromWidth = lastButton.width
					button.fromTextColor = lastButton.lastDrawnTextColor
					button.fromBackgroundColor = lastButton.lastDrawnBackgroundColor
					button.animationType = ANIMATION_TYPE_MOVE

					if (button.count != lastButton.count) {
						button.counterDrawable.setCount(lastButton.count, false)
						button.counterDrawable.setCount(button.count, true)
					}

					if (button.avatarsDrawable != null || lastButton.avatarsDrawable != null) {
						if (button.avatarsDrawable == null) {
							button.users = ArrayList()
						}

						if (lastButton.avatarsDrawable == null) {
							lastButton.users = ArrayList()
						}

						if (!equalsUsersList(lastButton.users, button.users)) {
							lastButton.avatarsDrawable?.let {
								button.avatarsDrawable?.animateFromState(it, currentAccount, false)
							}
						}
					}

					changed = true
				}
				else {
					button.animationType = 0
				}
			}
			else {
				changed = true
				button.animationType = ANIMATION_TYPE_IN
			}
		}

		if (lastDrawingReactionButtonsTmp.isNotEmpty()) {
			changed = true

			outButtons.addAll(lastDrawingReactionButtonsTmp.values)

			outButtons.forEach {
				it.drawImage = it.lastImageDrawn
				it.attach()
			}
		}

		if (wasDrawn && (lastDrawnX != x.toFloat() || lastDrawnY != y.toFloat())) {
			animateMove = true
			fromX = lastDrawnX
			fromY = lastDrawnY
			changed = true
		}

		if (lastDrawnWidth != width) {
			animateWidth = true
			fromWidth = lastDrawnWidth
			changed = true
		}

		if (lastDrawTotalHeight != totalHeight) {
			animateHeight = true
			animateFromTotalHeight = lastDrawTotalHeight
			changed = true
		}

		return changed
	}

	private fun equalsUsersList(users: List<User>?, users1: List<User>?): Boolean {
		@Suppress("NAME_SHADOWING") val users = users ?: return false
		@Suppress("NAME_SHADOWING") val users1 = users1 ?: return false

		if (users.size != users1.size) {
			return false
		}

		users.forEachIndexed { index, user ->
			if (user.id != users1[index].id) {
				return false
			}
		}

		return true
	}

	fun resetAnimation() {
		outButtons.forEach {
			it.detach()
		}

		outButtons.clear()
		animateMove = false
		animateWidth = false
		animateHeight = false

		reactionButtons.forEach {
			it.animationType = 0
		}
	}

	fun getReactionButton(visibleReaction: VisibleReaction): ReactionButton? {
		val hash = visibleReaction.emojicon ?: visibleReaction.documentId.toString()

		if (isSmall) {
			val button = lastDrawingReactionButtons[hash + "_"]

			if (button != null) {
				return button
			}
		}

		return lastDrawingReactionButtons[hash]
	}

	fun setScrimReaction(scrimViewReaction: String?) {
		this.scrimViewReaction = scrimViewReaction
	}

	fun checkTouchEvent(event: MotionEvent): Boolean {
		val messageObject = messageObject

		if (isEmpty || isSmall || messageObject == null || messageObject.messageOwner?.reactions == null) {
			return false
		}

		val x = event.x - x
		val y = event.y - y

		if (event.action == MotionEvent.ACTION_DOWN) {
			var i = 0
			val n = reactionButtons.size

			while (i < n) {
				if (x > reactionButtons[i].x && x < reactionButtons[i].x + reactionButtons[i].width && y > reactionButtons[i].y && y < reactionButtons[i].y + reactionButtons[i].height) {
					lastX = event.x
					lastY = event.y

					lastSelectedButton = reactionButtons[i]

					if (longPressRunnable != null) {
						AndroidUtilities.cancelRunOnUIThread(longPressRunnable)
						longPressRunnable = null
					}

					val selectedButtonFinal = lastSelectedButton

					if (messageObject.messageOwner?.reactions?.canSeeList == true || messageObject.dialogId >= 0) {
						AndroidUtilities.runOnUIThread(Runnable {
							parentView.delegate?.didPressReaction(parentView, selectedButtonFinal!!.reactionCount, true)
							longPressRunnable = null
						}.also {
							longPressRunnable = it
						}, ViewConfiguration.getLongPressTimeout().toLong())
					}

					pressed = true

					break
				}

				i++
			}
		}
		else if (event.action == MotionEvent.ACTION_MOVE) {
			if (pressed && abs(event.x - lastX) > touchSlop || abs(event.y - lastY) > touchSlop) {
				pressed = false
				lastSelectedButton = null

				if (longPressRunnable != null) {
					AndroidUtilities.cancelRunOnUIThread(longPressRunnable)
					longPressRunnable = null
				}
			}
		}
		else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
			if (longPressRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(longPressRunnable)
				longPressRunnable = null
			}

			if (pressed && lastSelectedButton != null && event.action == MotionEvent.ACTION_UP) {
				parentView.delegate?.didPressReaction(parentView, lastSelectedButton!!.reactionCount, false)
			}

			pressed = false
			lastSelectedButton = null
		}

		return pressed
	}

	private fun hasGradientService(): Boolean {
		return Theme.hasGradientService()
	}

	fun getCurrentWidth(transitionProgress: Float): Float {
		return if (animateWidth) {
			fromWidth * (1f - transitionProgress) + width * transitionProgress
		}
		else {
			width.toFloat()
		}
	}

	fun getCurrentTotalHeight(transitionProgress: Float): Float {
		return if (animateHeight) {
			animateFromTotalHeight * (1f - transitionProgress) + totalHeight * transitionProgress
		}
		else {
			totalHeight.toFloat()
		}
	}

	fun onAttachToWindow() {
		attached = true

		reactionButtons.forEach {
			it.attach()
		}
	}

	fun onDetachFromWindow() {
		attached = false

		reactionButtons.forEach {
			it.detach()
		}

		if (animatedReactions.isNotEmpty()) {
			for (imageReceiver in animatedReactions.values) {
				imageReceiver.onDetachedFromWindow()
			}
		}

		animatedReactions.clear()
	}

	fun animateReaction(reaction: VisibleReaction) {
		if (reaction.documentId == 0L && animatedReactions[reaction] == null) {
			val imageReceiver = ImageReceiver()
			imageReceiver.setParentView(parentView)
			imageReceiver.uniqueKeyPrefix = (animationUnique++).toString()

			val r = MediaDataController.getInstance(currentAccount).reactionsMap[reaction.emojicon]

			if (r != null) {
				imageReceiver.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_nolimit", null, "tgs", r, 1)
			}

			imageReceiver.setAutoRepeat(0)
			imageReceiver.onAttachedToWindow()

			animatedReactions[reaction] = imageReceiver
		}
	}

	private class ButtonsComparator : Comparator<ReactionButton> {
		var currentAccount = 0
		var dialogId: Long = 0

		override fun compare(o1: ReactionButton, o2: ReactionButton): Int {
			if (dialogId >= 0) {
				if (o1.isSelected != o2.isSelected) {
					return if (o1.isSelected) -1 else 1
				}
				else if (o1.isSelected) {
					if (o1.chosenOrder != o2.chosenOrder) {
						return o1.chosenOrder - o2.chosenOrder
					}
				}

				return o1.reactionCount.lastDrawnPosition - o2.reactionCount.lastDrawnPosition
			}
			else {
				if (o1.realCount != o2.realCount) {
					return o2.realCount - o1.realCount
				}
			}

			//            TLRPC.TL_availableReaction availableReaction1 = MediaDataController.getInstance(currentAccount).getReactionsMap().get(o1.reaction);
//            TLRPC.TL_availableReaction availableReaction2 = MediaDataController.getInstance(currentAccount).getReactionsMap().get(o2.reaction);
//            if (availableReaction1 != null && availableReaction2 != null) {
//                return availableReaction1.positionInList - availableReaction2.positionInList;
//            }

			return o1.reactionCount.lastDrawnPosition - o2.reactionCount.lastDrawnPosition
		}
	}

	inner class ReactionButton(val reactionCount: ReactionCount, val isSmall: Boolean) {
		private var animatedEmojiDrawable: AnimatedEmojiDrawable? = null
		private var serviceBackgroundColor = 0
		private var serviceTextColor = 0
		var animateFromWidth = 0
		var animateFromX = 0
		var animateFromY = 0
		var animationType = 0
		var avatarsDrawable: AvatarsDrawable? = null
		var backgroundColor = 0
		var chosen = reactionCount.chosen
		var chosenOrder = reactionCount.chosenOrder
		var count = reactionCount.count
		var countText: String
		var counterDrawable = CounterDrawable(parentView, drawBackground = false, drawWhenZero = true)
		var drawImage = true
		var fromBackgroundColor = 0
		var fromTextColor = 0
		var isSelected: Boolean
		var key: String? = null
		var lastDrawnBackgroundColor = 0
		var lastDrawnTextColor = 0
		var lastImageDrawn = false
		val reaction: Reaction? = reactionCount.reaction
		var realCount = reactionCount.count
		var textColor = 0
		val visibleReaction = VisibleReaction.fromTLReaction(reactionCount.reaction)
		var width = 0
		var x = 0
		var y = 0
		var height = 0
		val drawingImageRect = Rect()
		val imageReceiver = ImageReceiver()

		var users: List<User>? = null
			set(value) {
				field = value?.sortedWith(usersComparator)

				// MARK: uncomment to enable users avatars on reactions
//				if (value != null) {
//					if (avatarsDrawable == null) {
//						avatarsDrawable = AvatarsDarawable(parentView, false)
//						avatarsDrawable?.transitionDuration = ChatListItemAnimator.DEFAULT_DURATION
//						avatarsDrawable?.transitionInterpolator = ChatListItemAnimator.DEFAULT_INTERPOLATOR
//						avatarsDrawable?.setSize(AndroidUtilities.dp(20f))
//						avatarsDrawable?.width = AndroidUtilities.dp(100f)
//						avatarsDrawable?.height = height
//
//						if (attached) {
//							avatarsDrawable?.onAttachedToWindow()
//						}
//					}
//
//					for (i in value.indices) {
//						if (i == 3) {
//							break
//						}
//
//						avatarsDrawable?.setObject(i, currentAccount, value[i])
//					}
//
//					avatarsDrawable?.commitTransition(false)
//				}
			}

		init {
			key = when (reaction) {
				is TL_reactionEmoji -> reaction.emoticon
				is TL_reactionCustomEmoji -> reaction.documentId.toString()
				else -> throw RuntimeException("unsupported")
			}

			countText = reactionCount.count.toString()
			imageReceiver.setParentView(parentView)
			isSelected = reactionCount.chosen

			counterDrawable.updateVisibility = false
			counterDrawable.shortFormat = true

			if (visibleReaction.emojicon != null) {
				var fallbackToEmoji = true
				val r = MediaDataController.getInstance(currentAccount).reactionsMap[visibleReaction.emojicon]

				if (r != null) {
					val svgThumb = getSvgThumb(r.static_icon, ResourcesCompat.getColor(parentView.resources, R.color.dark_gray, null), 1.0f)

					if (svgThumb != null) {
						val image = ImageLocation.getForDocument(r.center_icon)

						if (image != null) {
							fallbackToEmoji = false
							imageReceiver.setImage(image, "40_40_lastframe", svgThumb, "webp", r, 1)
						}
					}
				}

				if (fallbackToEmoji) {
					imageReceiver.setImageBitmap(Emoji.getEmojiDrawableForEmojicon(visibleReaction.emojicon))
				}
			}
			else if (visibleReaction.documentId != 0L) {
				animatedEmojiDrawable = AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, visibleReaction.documentId)
			}

			counterDrawable.setSize(AndroidUtilities.dp(26f), AndroidUtilities.dp(100f))
			counterDrawable.textPaint = textPaint
			counterDrawable.setCount(count, false)
			counterDrawable.type = CounterDrawable.TYPE_CHAT_REACTIONS
			counterDrawable.gravity = Gravity.LEFT
		}

		fun draw(canvas: Canvas, progress: Float, alpha: Float, drawOverlayScrim: Boolean) {
			val imageReceiver = animatedEmojiDrawable?.imageReceiver ?: imageReceiver

			if (isSmall) {
				imageReceiver.alpha = alpha
				drawingImageRect.set(0, 0, AndroidUtilities.dp(14f), AndroidUtilities.dp(14f))
				imageReceiver.setImageCoordinates(drawingImageRect)
				imageReceiver.setRoundRadius(0)
				drawImage(canvas, alpha)
				return
			}

			if (chosen) {
				if (messageObject?.isOutOwner == true) {
					backgroundColor = context.getColor(R.color.out_reaction_selected_background)
					textColor = context.getColor(R.color.text_fixed)
					serviceTextColor = context.getColor(R.color.text_fixed)
					serviceBackgroundColor = context.getColor(R.color.out_reaction_selected_background)
				}
				else {
					backgroundColor = context.getColor(R.color.brand)
					textColor = context.getColor(R.color.white)
					serviceTextColor = context.getColor(R.color.white)
					serviceBackgroundColor = context.getColor(R.color.brand)
				}
			}
			else {
				if (messageObject?.isOutOwner == true) {
					textColor = context.getColor(R.color.white)
					backgroundColor = context.getColor(R.color.out_reaction_background)
					serviceTextColor = context.getColor(R.color.white)
					serviceBackgroundColor = context.getColor(R.color.out_reaction_background)
				}
				else {
					textColor = context.getColor(R.color.text)
					backgroundColor = context.getColor(R.color.feed_audio_background)
					serviceTextColor = context.getColor(R.color.text)
					serviceBackgroundColor = Color.TRANSPARENT
				}
			}

			updateColors(progress)

			textPaint.color = lastDrawnTextColor
			paint.color = lastDrawnBackgroundColor

			if (alpha != 1f) {
				textPaint.alpha = (textPaint.alpha * alpha).toInt()
				paint.alpha = (paint.alpha * alpha).toInt()
			}

			imageReceiver.alpha = alpha

			var w = width

			if (progress != 1f && animationType == ANIMATION_TYPE_MOVE) {
				w = (width * progress + animateFromWidth * (1f - progress)).toInt()
			}

			AndroidUtilities.rectTmp.set(0f, 0f, w.toFloat(), height.toFloat())

			val rad = height / 2f

			if (drawServiceShaderBackground) {
				val paint1 = Theme.chat_actionBackgroundPaint
				val paint2 = Theme.chat_actionBackgroundGradientDarkenPaint

				val oldAlpha = paint1.alpha
				val oldAlpha2 = paint2.alpha

				paint1.alpha = (oldAlpha * alpha).toInt()
				paint2.alpha = (oldAlpha2 * alpha).toInt()

				canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint1)

				if (hasGradientService()) {
					canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint2)
				}

				paint1.alpha = oldAlpha
				paint2.alpha = oldAlpha2
			}

			if (!drawServiceShaderBackground && drawOverlayScrim) {
				val messageBackground = parentView.getCurrentBackgroundDrawable(false)

				if (messageBackground != null) {
					canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, messageBackground.paint)
				}
			}

			canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint)

			val size: Int
			val x: Int

			if (animatedEmojiDrawable != null) {
				size = AndroidUtilities.dp(24f)
				x = AndroidUtilities.dp(6f)
				imageReceiver.setRoundRadius(AndroidUtilities.dp(6f))
			}
			else {
				size = AndroidUtilities.dp(20f)
				x = AndroidUtilities.dp(8f)
				imageReceiver.setRoundRadius(0)
			}

			val y = ((height - size) / 2f).toInt()

			drawingImageRect.set(x, y, x + size, y + size)

			imageReceiver.setImageCoordinates(drawingImageRect)

			drawImage(canvas, alpha)

			// if (count != 0 || counterDrawable.countChangeProgress != 1f) {
			canvas.save()
			canvas.translate((AndroidUtilities.dp(8f) + AndroidUtilities.dp(20f) + AndroidUtilities.dp(2f)).toFloat(), 0f)

			counterDrawable.draw(canvas)

			canvas.restore()
			// }

			avatarsDrawable?.let {
				canvas.save()
				canvas.translate((AndroidUtilities.dp(10f) + AndroidUtilities.dp(20f) + AndroidUtilities.dp(2f)).toFloat(), 0f)

				it.setAlpha(alpha)
				it.setTransitionProgress(progress)
				it.onDraw(canvas)

				canvas.restore()
			}
		}

		private fun updateColors(progress: Float) {
			if (drawServiceShaderBackground) {
				lastDrawnTextColor = ColorUtils.blendARGB(fromTextColor, serviceTextColor, progress)
				lastDrawnBackgroundColor = ColorUtils.blendARGB(fromBackgroundColor, serviceBackgroundColor, progress)
			}
			else {
				lastDrawnTextColor = ColorUtils.blendARGB(fromTextColor, textColor, progress)
				lastDrawnBackgroundColor = ColorUtils.blendARGB(fromBackgroundColor, backgroundColor, progress)
			}
		}

		private fun drawImage(canvas: Canvas, alpha: Float) {
			val imageReceiver = animatedEmojiDrawable?.imageReceiver ?: imageReceiver

			if (drawImage && (realCount > 1 || !ReactionsEffectOverlay.isPlaying(messageObject!!.id, messageObject!!.groupId, visibleReaction) || !isSelected)) {
				val imageReceiver2 = animatedReactions[visibleReaction]
				var drawStaticImage = true

				if (imageReceiver2 != null) {
					if (imageReceiver2.lottieAnimation != null && imageReceiver2.lottieAnimation!!.hasBitmap()) {
						drawStaticImage = false
					}

					if (alpha != 1f) {
						imageReceiver2.alpha = alpha

						if (alpha <= 0) {
							imageReceiver2.onDetachedFromWindow()
							animatedReactions.remove(visibleReaction)
						}
					}
					else {
						if (imageReceiver2.lottieAnimation != null && imageReceiver2.lottieAnimation?.isRunning != true) {
							drawStaticImage = true

							val alpha1 = imageReceiver2.alpha - 16f / 200

							if (alpha1 <= 0) {
								imageReceiver2.onDetachedFromWindow()
								animatedReactions.remove(visibleReaction)
							}
							else {
								imageReceiver2.alpha = alpha1
							}

							parentView.invalidate()
						}
					}

					imageReceiver2.setImageCoordinates(imageReceiver.imageX - imageReceiver.imageWidth / 2, imageReceiver.imageY - imageReceiver.imageWidth / 2, imageReceiver.imageWidth * 2, imageReceiver.imageHeight * 2)
					imageReceiver2.draw(canvas)
				}

				if (drawStaticImage) {
					imageReceiver.draw(canvas)
				}

				lastImageDrawn = true
			}
			else {
				imageReceiver.alpha = 0f
				imageReceiver.draw(canvas)
				lastImageDrawn = false
			}
		}

		fun attach() {
			imageReceiver.onAttachedToWindow()
			avatarsDrawable?.onAttachedToWindow()
			animatedEmojiDrawable?.addView(parentView)
		}

		fun detach() {
			imageReceiver.onDetachedFromWindow()
			avatarsDrawable?.onDetachedFromWindow()
			animatedEmojiDrawable?.removeView(parentView)
		}
	}

	companion object {
		private const val ANIMATION_TYPE_IN = 1

		// private const val ANIMATION_TYPE_OUT = 2
		private const val ANIMATION_TYPE_MOVE = 3
		private val comparator = ButtonsComparator()
		private val usersComparator = Comparator { user1: User, user2: User -> (user1.id - user2.id).toInt() }
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private var animationUnique = 0
		private var pointer = 1

		@JvmStatic
		fun equalsTLReaction(reaction: Reaction?, reaction1: Reaction?): Boolean {
			if (reaction is TL_reactionEmoji && reaction1 is TL_reactionEmoji) {
				return TextUtils.equals(reaction.emoticon, reaction1.emoticon)
			}

			if (reaction is TL_reactionCustomEmoji && reaction1 is TL_reactionCustomEmoji) {
				return reaction.documentId == reaction1.documentId
			}

			return false
		}
	}
}
