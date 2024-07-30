/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package androidx.recyclerview.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.ViewCompat
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.messageobject.GroupedMessages
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.ui.Cells.BotHelpCell
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatGreetingsView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.TextMessageEnterTransition
import org.telegram.ui.VoiceMessageEnterTransition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

open class ChatListItemAnimator(private val activity: ChatActivity?, private val recyclerListView: RecyclerListView) : DefaultItemAnimator() {
	private val willChangedGroups = mutableListOf<GroupedMessages>()
	private val willRemovedGroup = mutableMapOf<Int, GroupedMessages>()
	private var alphaEnterDelay = 0L
	private var animators = mutableMapOf<RecyclerView.ViewHolder, Animator>()
	private var chatGreetingsView: ChatGreetingsView? = null
	private var greetingsSticker: RecyclerView.ViewHolder? = null
	private var groupIdToEnterDelay = mutableMapOf<Long, Long>()
	private var reset = false
	private var reversePositions = false
	private var runOnAnimationsEnd = mutableListOf<Runnable>()
	private var shouldAnimateEnterFromBottom = false

	init {
		translationInterpolator = DEFAULT_INTERPOLATOR
		alwaysCreateMoveAnimationIfPossible = true
		supportsChangeAnimations = false
	}

	override fun runPendingAnimations() {
		val removalsPending = mPendingRemovals.isNotEmpty()
		val movesPending = mPendingMoves.isNotEmpty()
		val changesPending = mPendingChanges.isNotEmpty()
		val additionsPending = mPendingAdditions.isNotEmpty()

		if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
			return
		}

		var runTranslationFromBottom = false

		if (shouldAnimateEnterFromBottom) {
			for (i in mPendingAdditions.indices) {
				if (reversePositions) {
					val itemCount = recyclerListView.adapter?.itemCount ?: 0

					if (mPendingAdditions[i].layoutPosition == itemCount - 1) {
						runTranslationFromBottom = true
					}
				}
				else {
					if (mPendingAdditions[i].layoutPosition == 0) {
						runTranslationFromBottom = true
					}
				}
			}
		}

		onAnimationStart()

		if (runTranslationFromBottom) {
			runMessageEnterTransition()
		}
		else {
			runAlphaEnterTransition()
		}

		val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

		valueAnimator.addUpdateListener {
			activity?.onListItemAnimatorTick() ?: recyclerListView.invalidate()
		}

		valueAnimator.duration = removeDuration + moveDuration
		valueAnimator.start()
	}

	private fun runAlphaEnterTransition() {
		val removalsPending = mPendingRemovals.isNotEmpty()
		val movesPending = mPendingMoves.isNotEmpty()
		val changesPending = mPendingChanges.isNotEmpty()
		val additionsPending = mPendingAdditions.isNotEmpty()

		if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
			// nothing to animate
			return
		}

		// First, remove stuff

		for (holder in mPendingRemovals) {
			animateRemoveImpl(holder)
		}

		mPendingRemovals.clear()

		// Next, move stuff

		if (movesPending) {
			val moves = ArrayList(mPendingMoves)

			mMovesList.add(moves)
			mPendingMoves.clear()

			val mover = Runnable {
				for (moveInfo in moves) {
					animateMoveImpl(moveInfo.holder, moveInfo)
				}

				moves.clear()

				mMovesList.remove(moves)
			}

			if (delayAnimations && removalsPending) {
				val view = moves[0].holder.itemView
				ViewCompat.postOnAnimationDelayed(view, mover, moveAnimationDelay)
			}
			else {
				mover.run()
			}
		}

		// Next, change stuff, to run in parallel with move animations

		if (changesPending) {
			val changes = ArrayList(mPendingChanges)

			mChangesList.add(changes)
			mPendingChanges.clear()

			val changer = Runnable {
				for (change in changes) {
					animateChangeImpl(change)
				}

				changes.clear()

				mChangesList.remove(changes)
			}

			if (delayAnimations && removalsPending) {
				val holder = changes[0].oldHolder
				ViewCompat.postOnAnimationDelayed(holder.itemView, changer, 0)
			}
			else {
				changer.run()
			}
		}

		// Next, add stuff

		if (additionsPending) {
			val additions = ArrayList(mPendingAdditions)

			mPendingAdditions.clear()

			alphaEnterDelay = 0

			additions.sortWith { i1, i2 -> i2.itemView.top - i1.itemView.top }

			for (holder in additions) {
				animateAddImpl(holder)
			}

			additions.clear()
		}
	}

	private fun runMessageEnterTransition() {
		val removalsPending = mPendingRemovals.isNotEmpty()
		val movesPending = mPendingMoves.isNotEmpty()
		val changesPending = mPendingChanges.isNotEmpty()
		val additionsPending = mPendingAdditions.isNotEmpty()

		if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
			return
		}

		var addedItemsHeight = 0

		for (i in mPendingAdditions.indices) {
			val view = mPendingAdditions[i].itemView

			if (view is ChatMessageCell) {
				val currentPosition = view.currentPosition

				if (currentPosition != null && currentPosition.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
					continue
				}
			}

			addedItemsHeight += mPendingAdditions[i].itemView.height
		}

		for (holder in mPendingRemovals) {
			animateRemoveImpl(holder)
		}

		mPendingRemovals.clear()

		if (movesPending) {
			val moves = ArrayList(mPendingMoves)

			mPendingMoves.clear()

			for (moveInfo in moves) {
				animateMoveImpl(moveInfo.holder, moveInfo)
			}

			moves.clear()
		}

		if (additionsPending) {
			val additions = ArrayList(mPendingAdditions)

			mPendingAdditions.clear()

			for (holder in additions) {
				animateAddImpl(holder, addedItemsHeight)
			}

			additions.clear()
		}
	}

	override fun animateAppearance(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo?, postLayoutInfo: ItemHolderInfo): Boolean {
		val res = super.animateAppearance(viewHolder, preLayoutInfo, postLayoutInfo)

		if (res && shouldAnimateEnterFromBottom) {
			var runTranslationFromBottom = false

			for (i in mPendingAdditions.indices) {
				if (mPendingAdditions[i].layoutPosition == 0) {
					runTranslationFromBottom = true
				}
			}

			var addedItemsHeight = 0

			if (runTranslationFromBottom) {
				for (i in mPendingAdditions.indices) {
					addedItemsHeight += mPendingAdditions[i].itemView.height
				}
			}

			for (i in mPendingAdditions.indices) {
				mPendingAdditions[i].itemView.translationY = addedItemsHeight.toFloat()
			}
		}
		return res
	}

	override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
		resetAnimation(holder)

		holder.itemView.alpha = 0f

		if (!shouldAnimateEnterFromBottom) {
			holder.itemView.scaleX = 0.9f
			holder.itemView.scaleY = 0.9f
		}
		else {
			if (holder.itemView is ChatMessageCell) {
				holder.itemView.transitionParams.messageEntering = true
			}
		}

		mPendingAdditions.add(holder)

		return true
	}

	private fun animateAddImpl(holder: RecyclerView.ViewHolder, addedItemsHeight: Int) {
		val view = holder.itemView
		val animation = view.animate()

		mAddAnimations.add(holder)

		view.translationY = addedItemsHeight.toFloat()

		holder.itemView.scaleX = 1f
		holder.itemView.scaleY = 1f

		val chatMessageCell = holder.itemView as? ChatMessageCell

		if (!(chatMessageCell != null && chatMessageCell.transitionParams.ignoreAlpha)) {
			holder.itemView.alpha = 1f
		}

		val messageObject = chatMessageCell?.getMessageObject()

		if (chatMessageCell != null && activity?.animatingMessageObjects?.contains(messageObject) == true) {
			activity.animatingMessageObjects.remove(messageObject)

			val chatActivityEnterView = activity.chatActivityEnterView

			if (chatActivityEnterView?.canShowMessageTransition() == true) {
				if (messageObject?.isVoice == true) {
					if (abs(view.translationY) < view.measuredHeight * 3f) {
						val transition = VoiceMessageEnterTransition(chatMessageCell, chatActivityEnterView, recyclerListView, activity.messageEnterTransitionContainer!!)
						transition.start()
					}
				}
				else {
					if (SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW && abs(view.translationY) < recyclerListView.measuredHeight) {
						val transition = TextMessageEnterTransition(chatMessageCell, activity, recyclerListView, activity.messageEnterTransitionContainer!!)
						transition.start()
					}
				}

				chatActivityEnterView.startMessageTransition()
			}
		}

		animation.translationY(0f).setDuration(moveDuration).setInterpolator(translationInterpolator).setListener(object : AnimatorListenerAdapter() {
			override fun onAnimationStart(animator: Animator) {
				dispatchAddStarting(holder)
			}

			override fun onAnimationCancel(animator: Animator) {
				view.translationY = 0f

				if (view is ChatMessageCell) {
					view.transitionParams.messageEntering = false
				}
			}

			override fun onAnimationEnd(animator: Animator) {
				if (view is ChatMessageCell) {
					view.transitionParams.messageEntering = false
				}

				animation.setListener(null)

				if (mAddAnimations.remove(holder)) {
					dispatchAddFinished(holder)
					dispatchFinishedWhenDone()
				}
			}
		}).start()
	}

	override fun animateRemove(holder: RecyclerView.ViewHolder, info: ItemHolderInfo?): Boolean {
		val rez = super.animateRemove(holder, info)

		if (rez) {
			if (info != null) {
				val fromY = info.top
				val toY = holder.itemView.top
				val fromX = info.left
				val toX = holder.itemView.left
				val deltaX = toX - fromX
				val deltaY = toY - fromY

				if (deltaY != 0) {
					holder.itemView.translationY = -deltaY.toFloat()
				}

				if (holder.itemView is ChatMessageCell) {
					val chatMessageCell = holder.itemView

					if (deltaX != 0) {
						chatMessageCell.setAnimationOffsetX(-deltaX.toFloat())
					}

					if (info is ItemHolderInfoExtended) {
						chatMessageCell.setImageCoordinates(info.imageX, info.imageY, info.imageWidth, info.imageHeight)
					}
				}
				else {
					if (deltaX != 0) {
						holder.itemView.translationX = -deltaX.toFloat()
					}
				}
			}
		}

		return rez
	}

	override fun animateMove(holder: RecyclerView.ViewHolder, info: ItemHolderInfo, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
		@Suppress("NAME_SHADOWING") var fromX = fromX
		@Suppress("NAME_SHADOWING") var fromY = fromY

		val view = holder.itemView
		var chatMessageCell: ChatMessageCell? = null

		if (holder.itemView is ChatMessageCell) {
			chatMessageCell = holder.itemView
			fromX += chatMessageCell.getAnimationOffsetX().toInt()

			if (chatMessageCell.transitionParams.lastTopOffset != chatMessageCell.getTopMediaOffset()) {
				fromY += chatMessageCell.transitionParams.lastTopOffset - chatMessageCell.getTopMediaOffset()
			}
		}
		else {
			fromX += holder.itemView.translationX.toInt()
		}

		fromY += holder.itemView.translationY.toInt()

		var imageX = 0f
		var imageY = 0f
		var imageW = 0f
		var imageH = 0f
		val roundRadius = IntArray(4)

		if (chatMessageCell != null) {
			imageX = chatMessageCell.photoImage.imageX
			imageY = chatMessageCell.photoImage.imageY
			imageW = chatMessageCell.photoImage.imageWidth
			imageH = chatMessageCell.photoImage.imageHeight

			for (i in 0..3) {
				roundRadius[i] = chatMessageCell.photoImage.getRoundRadius()[i]
			}
		}

		resetAnimation(holder)

		val deltaX = toX - fromX
		val deltaY = toY - fromY

		if (deltaY != 0) {
			view.translationY = -deltaY.toFloat()
		}

		val moveInfo = MoveInfoExtended(holder, fromX, fromY, toX, toY)

		if (chatMessageCell != null) {
			val params = chatMessageCell.transitionParams

			if (!params.supportChangeAnimation()) {
				if (deltaX == 0 && deltaY == 0) {
					dispatchMoveFinished(holder)
					return false
				}

				if (deltaX != 0) {
					view.translationX = -deltaX.toFloat()
				}

				mPendingMoves.add(moveInfo)

				checkIsRunning()

				return true
			}

			val group = chatMessageCell.currentMessagesGroup

			if (deltaX != 0) {
				chatMessageCell.setAnimationOffsetX(-deltaX.toFloat())
			}

			if (info is ItemHolderInfoExtended) {
				val newImage = chatMessageCell.photoImage

				moveInfo.animateImage = params.wasDraw && info.imageHeight != 0f && info.imageWidth != 0f

				if (moveInfo.animateImage) {
					recyclerListView.clipChildren = false
					recyclerListView.invalidate()

					params.imageChangeBoundsTransition = true

					if (chatMessageCell.getMessageObject()?.isRoundVideo == true) {
						params.animateToImageX = imageX
						params.animateToImageY = imageY
						params.animateToImageW = imageW
						params.animateToImageH = imageH
						params.animateToRadius = roundRadius
					}
					else {
						params.animateToImageX = newImage.imageX
						params.animateToImageY = newImage.imageY
						params.animateToImageW = newImage.imageWidth
						params.animateToImageH = newImage.imageHeight
						params.animateToRadius = newImage.getRoundRadius()
					}

					params.animateRadius = false

					for (i in 0..3) {
						if (params.imageRoundRadius[i] != params.animateToRadius?.get(i)) {
							params.animateRadius = true
							break
						}
					}

					if (params.animateToImageX == info.imageX && params.animateToImageY == info.imageY && params.animateToImageH == info.imageHeight && params.animateToImageW == info.imageWidth && !params.animateRadius) {
						params.imageChangeBoundsTransition = false
						moveInfo.animateImage = false
					}
					else {
						moveInfo.imageX = info.imageX
						moveInfo.imageY = info.imageY
						moveInfo.imageWidth = info.imageWidth
						moveInfo.imageHeight = info.imageHeight

						if (group != null && group.hasCaption != group.transitionParams.drawCaptionLayout) {
							group.transitionParams.captionEnterProgress = if (group.transitionParams.drawCaptionLayout) 1f else 0f
						}

						if (params.animateRadius) {
							if (params.animateToRadius.contentEquals(newImage.getRoundRadius())) {
								params.animateToRadius = IntArray(4)

								for (i in 0..3) {
									params.animateToRadius?.set(i, newImage.getRoundRadius()[i])
								}
							}

							newImage.setRoundRadius(params.imageRoundRadius)
						}

						chatMessageCell.setImageCoordinates(moveInfo.imageX, moveInfo.imageY, moveInfo.imageWidth, moveInfo.imageHeight)
					}
				}

				if (group == null && params.wasDraw) {
					val isOut = chatMessageCell.getMessageObject()?.isOutOwner == true
					val widthChanged = isOut && params.lastDrawingBackgroundRect.left != chatMessageCell.getBackgroundDrawableLeft() || !isOut && params.lastDrawingBackgroundRect.right != chatMessageCell.getBackgroundDrawableRight()

					if (widthChanged || params.lastDrawingBackgroundRect.top != chatMessageCell.getBackgroundDrawableTop() || params.lastDrawingBackgroundRect.bottom != chatMessageCell.getBackgroundDrawableBottom()) {
						moveInfo.deltaBottom = chatMessageCell.getBackgroundDrawableBottom() - params.lastDrawingBackgroundRect.bottom
						moveInfo.deltaTop = chatMessageCell.getBackgroundDrawableTop() - params.lastDrawingBackgroundRect.top

						if (isOut) {
							moveInfo.deltaLeft = chatMessageCell.getBackgroundDrawableLeft() - params.lastDrawingBackgroundRect.left
						}
						else {
							moveInfo.deltaRight = chatMessageCell.getBackgroundDrawableRight() - params.lastDrawingBackgroundRect.right
						}

						moveInfo.animateBackgroundOnly = true

						params.animateBackgroundBoundsInner = true
						params.animateBackgroundWidth = widthChanged
						params.deltaLeft = -moveInfo.deltaLeft.toFloat()
						params.deltaRight = -moveInfo.deltaRight.toFloat()
						params.deltaTop = -moveInfo.deltaTop.toFloat()
						params.deltaBottom = -moveInfo.deltaBottom.toFloat()

						recyclerListView.clipChildren = false
						recyclerListView.invalidate()
					}
				}
			}

			if (group != null) {
				if (willChangedGroups.contains(group)) {
					willChangedGroups.remove(group)

					val recyclerListView = holder.itemView.parent as RecyclerListView
					var animateToLeft = 0
					var animateToRight = 0
					var animateToTop = 0
					var animateToBottom = 0
					var allVisibleItemsDeleted = true
					val groupTransitionParams = group.transitionParams

					for (i in 0 until recyclerListView.childCount) {
						val child = recyclerListView.getChildAt(i)

						if (child is ChatMessageCell) {
							if (child.currentMessagesGroup == group && child.getMessageObject()?.deleted != true) {
								val left = child.left + child.getBackgroundDrawableLeft()
								val right = child.left + child.getBackgroundDrawableRight()
								val top = child.top + child.getBackgroundDrawableTop()
								val bottom = child.top + child.getBackgroundDrawableBottom()

								if (animateToLeft == 0 || left < animateToLeft) {
									animateToLeft = left
								}

								if (animateToRight == 0 || right > animateToRight) {
									animateToRight = right
								}

								if (child.transitionParams.wasDraw || groupTransitionParams.isNewGroup) {
									allVisibleItemsDeleted = false

									if (animateToTop == 0 || top < animateToTop) {
										animateToTop = top
									}

									if (animateToBottom == 0 || bottom > animateToBottom) {
										animateToBottom = bottom
									}
								}
							}
						}
					}

					groupTransitionParams.isNewGroup = false

					if (animateToTop == 0 && animateToBottom == 0 && animateToLeft == 0 && animateToRight == 0) {
						moveInfo.animateChangeGroupBackground = false
						groupTransitionParams.backgroundChangeBounds = false
					}
					else {
						moveInfo.groupOffsetTop = -animateToTop + groupTransitionParams.top
						moveInfo.groupOffsetBottom = -animateToBottom + groupTransitionParams.bottom
						moveInfo.groupOffsetLeft = -animateToLeft + groupTransitionParams.left
						moveInfo.groupOffsetRight = -animateToRight + groupTransitionParams.right
						moveInfo.animateChangeGroupBackground = true

						groupTransitionParams.backgroundChangeBounds = true
						groupTransitionParams.offsetTop = moveInfo.groupOffsetTop.toFloat()
						groupTransitionParams.offsetBottom = moveInfo.groupOffsetBottom.toFloat()
						groupTransitionParams.offsetLeft = moveInfo.groupOffsetLeft.toFloat()
						groupTransitionParams.offsetRight = moveInfo.groupOffsetRight.toFloat()
						groupTransitionParams.captionEnterProgress = if (groupTransitionParams.drawCaptionLayout) 1f else 0f

						recyclerListView.clipChildren = false
						recyclerListView.invalidate()
					}

					groupTransitionParams.drawBackgroundForDeletedItems = allVisibleItemsDeleted
				}
			}

			val removedGroup = willRemovedGroup[chatMessageCell.getMessageObject()!!.id]

			if (removedGroup != null) {
				val groupTransitionParams = removedGroup.transitionParams

				willRemovedGroup.remove(chatMessageCell.getMessageObject()!!.id)

				if (params.wasDraw) {
					// invoke when group transform to single message
					val animateToLeft = chatMessageCell.left + chatMessageCell.getBackgroundDrawableLeft()
					val animateToRight = chatMessageCell.left + chatMessageCell.getBackgroundDrawableRight()
					val animateToTop = chatMessageCell.top + chatMessageCell.getBackgroundDrawableTop()
					val animateToBottom = chatMessageCell.top + chatMessageCell.getBackgroundDrawableBottom()

					moveInfo.animateRemoveGroup = true

					params.animateBackgroundBoundsInner = true

					moveInfo.deltaLeft = animateToLeft - groupTransitionParams.left
					moveInfo.deltaRight = animateToRight - groupTransitionParams.right
					moveInfo.deltaTop = animateToTop - groupTransitionParams.top
					moveInfo.deltaBottom = animateToBottom - groupTransitionParams.bottom
					moveInfo.animateBackgroundOnly = false

					params.deltaLeft = (-moveInfo.deltaLeft - chatMessageCell.getAnimationOffsetX()).toInt().toFloat()
					params.deltaRight = (-moveInfo.deltaRight - chatMessageCell.getAnimationOffsetX()).toInt().toFloat()
					params.deltaTop = (-moveInfo.deltaTop - chatMessageCell.translationY).toInt().toFloat()
					params.deltaBottom = (-moveInfo.deltaBottom - chatMessageCell.translationY).toInt().toFloat()
					params.transformGroupToSingleMessage = true

					recyclerListView.clipChildren = false
					recyclerListView.invalidate()
				}
				else {
					groupTransitionParams.drawBackgroundForDeletedItems = true
				}
			}

			val drawPinnedBottom = chatMessageCell.isDrawPinnedBottom()

			if (params.drawPinnedBottomBackground != drawPinnedBottom) {
				moveInfo.animatePinnedBottom = true
				params.changePinnedBottomProgress = 0f
			}

			moveInfo.animateChangeInternal = chatMessageCell.transitionParams.animateChange()

			if (moveInfo.animateChangeInternal) {
				chatMessageCell.transitionParams.animateChange = true
				chatMessageCell.transitionParams.animateChangeProgress = 0f
			}

			if (deltaX == 0 && deltaY == 0 && !moveInfo.animateImage && !moveInfo.animateRemoveGroup && !moveInfo.animateChangeGroupBackground && !moveInfo.animatePinnedBottom && !moveInfo.animateBackgroundOnly && !moveInfo.animateChangeInternal) {
				dispatchMoveFinished(holder)
				return false
			}
		}
		else if (holder.itemView is BotHelpCell) {
			holder.itemView.setAnimating(true)
		}
		else {
			if (deltaX == 0 && deltaY == 0) {
				dispatchMoveFinished(holder)
				return false
			}

			if (deltaX != 0) {
				view.translationX = -deltaX.toFloat()
			}
		}

		mPendingMoves.add(moveInfo)

		checkIsRunning()

		return true
	}

	override fun animateMoveImpl(holder: RecyclerView.ViewHolder, moveInfo: MoveInfo) {
		val fromY = moveInfo.fromY
		val toY = moveInfo.toY
		val view = holder.itemView
		val deltaY = toY - fromY
		val animatorSet = AnimatorSet()

		if (deltaY != 0) {
			animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f))
		}

		mMoveAnimations.add(holder)

		val moveInfoExtended = moveInfo as MoveInfoExtended

		if (activity != null && holder.itemView is BotHelpCell) {
			val botCell = holder.itemView
			val animateFrom = botCell.translationY
			val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

			valueAnimator.addUpdateListener {
				val v = it.animatedValue as Float
				val top = (recyclerListView.measuredHeight - activity.chatListViewPadding - activity.blurredViewBottomOffset) / 2f - botCell.measuredHeight / 2f + activity.chatListViewPadding
				var animateTo = 0f

				if (botCell.top > top) {
					animateTo = top - botCell.top
				}

				botCell.translationY = animateFrom * (1f - v) + animateTo * v
			}

			animatorSet.playTogether(valueAnimator)
		}
		else if (holder.itemView is ChatMessageCell) {
			val chatMessageCell = holder.itemView
			val params = chatMessageCell.transitionParams
			val objectAnimator = ObjectAnimator.ofFloat(chatMessageCell, chatMessageCell.ANIMATION_OFFSET_X, 0f)

			animatorSet.playTogether(objectAnimator)

			if (moveInfoExtended.animateImage) {
				chatMessageCell.setImageCoordinates(moveInfoExtended.imageX, moveInfoExtended.imageY, moveInfoExtended.imageWidth, moveInfoExtended.imageHeight)

				val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
				val captionEnterFrom = chatMessageCell.currentMessagesGroup?.transitionParams?.captionEnterProgress ?: params.captionEnterProgress
				val captionEnterTo = (if (chatMessageCell.currentMessagesGroup == null) (if (chatMessageCell.hasCaptionLayout()) 1 else 0) else if (chatMessageCell.currentMessagesGroup?.hasCaption == true) 1 else 0).toFloat()
				val animateCaption = captionEnterFrom != captionEnterTo
				var fromRoundRadius: IntArray? = null

				if (params.animateRadius) {
					fromRoundRadius = IntArray(4)

					for (i in 0..3) {
						fromRoundRadius[i] = chatMessageCell.photoImage.getRoundRadius()[i]
					}
				}

				valueAnimator.addUpdateListener {
					val v = it.animatedValue as Float
					val x = moveInfoExtended.imageX * (1f - v) + params.animateToImageX * v
					val y = moveInfoExtended.imageY * (1f - v) + params.animateToImageY * v
					val width = moveInfoExtended.imageWidth * (1f - v) + params.animateToImageW * v
					val height = moveInfoExtended.imageHeight * (1f - v) + params.animateToImageH * v

					if (animateCaption) {
						val captionP = captionEnterFrom * (1f - v) + captionEnterTo * v

						params.captionEnterProgress = captionP

						chatMessageCell.currentMessagesGroup?.transitionParams?.captionEnterProgress = captionP
					}

					if (params.animateRadius) {
						chatMessageCell.photoImage.setRoundRadius((fromRoundRadius!![0] * (1f - v) + params.animateToRadius!![0] * v).toInt(), (fromRoundRadius[1] * (1f - v) + params.animateToRadius!![1] * v).toInt(), (fromRoundRadius[2] * (1f - v) + params.animateToRadius!![2] * v).toInt(), (fromRoundRadius[3] * (1f - v) + params.animateToRadius!![3] * v).toInt())
					}

					chatMessageCell.setImageCoordinates(x, y, width, height)

					holder.itemView.invalidate()
				}

				animatorSet.playTogether(valueAnimator)
			}

			if (moveInfoExtended.deltaBottom != 0 || moveInfoExtended.deltaRight != 0 || moveInfoExtended.deltaTop != 0 || moveInfoExtended.deltaLeft != 0) {
				recyclerListView.clipChildren = false
				recyclerListView.invalidate()

				val valueAnimator = ValueAnimator.ofFloat(1f, 0f)

				if (moveInfoExtended.animateBackgroundOnly) {
					params.toDeltaLeft = -moveInfoExtended.deltaLeft.toFloat()
					params.toDeltaRight = -moveInfoExtended.deltaRight.toFloat()
				}
				else {
					params.toDeltaLeft = -moveInfoExtended.deltaLeft - chatMessageCell.getAnimationOffsetX()
					params.toDeltaRight = -moveInfoExtended.deltaRight - chatMessageCell.getAnimationOffsetX()
				}

				valueAnimator.addUpdateListener {
					val v = it.animatedValue as Float

					if (moveInfoExtended.animateBackgroundOnly) {
						params.deltaLeft = -moveInfoExtended.deltaLeft * v
						params.deltaRight = -moveInfoExtended.deltaRight * v
						params.deltaTop = -moveInfoExtended.deltaTop * v
						params.deltaBottom = -moveInfoExtended.deltaBottom * v
					}
					else {
						params.deltaLeft = -moveInfoExtended.deltaLeft * v - chatMessageCell.getAnimationOffsetX()
						params.deltaRight = -moveInfoExtended.deltaRight * v - chatMessageCell.getAnimationOffsetX()
						params.deltaTop = -moveInfoExtended.deltaTop * v - chatMessageCell.translationY
						params.deltaBottom = -moveInfoExtended.deltaBottom * v - chatMessageCell.translationY
					}

					chatMessageCell.invalidate()
				}

				animatorSet.playTogether(valueAnimator)
			}
			else {
				params.toDeltaLeft = 0f
				params.toDeltaRight = 0f
			}

			val group = chatMessageCell.currentMessagesGroup

			if (group == null) {
				moveInfoExtended.animateChangeGroupBackground = false
			}

			if (moveInfoExtended.animateChangeGroupBackground) {
				val valueAnimator = ValueAnimator.ofFloat(1f, 0f)
				val groupTransitionParams = group?.transitionParams
				val recyclerListView = holder.itemView.getParent() as RecyclerListView
				val captionEnterFrom = group?.transitionParams?.captionEnterProgress ?: 0f
				val captionEnterTo = (if (group?.hasCaption == true) 1 else 0).toFloat()
				val animateCaption = captionEnterFrom != captionEnterTo

				valueAnimator.addUpdateListener {
					val v = it.animatedValue as Float
					groupTransitionParams?.offsetTop = moveInfoExtended.groupOffsetTop * v
					groupTransitionParams?.offsetBottom = moveInfoExtended.groupOffsetBottom * v
					groupTransitionParams?.offsetLeft = moveInfoExtended.groupOffsetLeft * v
					groupTransitionParams?.offsetRight = moveInfoExtended.groupOffsetRight * v

					if (animateCaption) {
						groupTransitionParams?.captionEnterProgress = captionEnterFrom * v + captionEnterTo * (1f - v)
					}

					recyclerListView.invalidate()
				}

				valueAnimator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						groupTransitionParams?.backgroundChangeBounds = false
						groupTransitionParams?.drawBackgroundForDeletedItems = false
					}
				})

				animatorSet.playTogether(valueAnimator)
			}

			if (moveInfoExtended.animatePinnedBottom) {
				val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

				valueAnimator.addUpdateListener {
					params.changePinnedBottomProgress = it.animatedValue as Float
					chatMessageCell.invalidate()
				}

				animatorSet.playTogether(valueAnimator)
			}

			if (moveInfoExtended.animateChangeInternal) {
				val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

				params.animateChange = true

				valueAnimator.addUpdateListener {
					params.animateChangeProgress = it.animatedValue as Float
					chatMessageCell.invalidate()
				}

				animatorSet.playTogether(valueAnimator)
			}
		}

		if (translationInterpolator != null) {
			animatorSet.interpolator = translationInterpolator
		}

		animatorSet.duration = moveDuration

		animatorSet.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationStart(animator: Animator) {
				dispatchMoveStarting(holder)
			}

			override fun onAnimationCancel(animator: Animator) {
				if (deltaY != 0) {
					view.translationY = 0f
				}
			}

			override fun onAnimationEnd(animator: Animator) {
				animator.removeAllListeners()
				restoreTransitionParams(holder.itemView)

				if (holder.itemView is ChatMessageCell) {
					val group = (view as? ChatMessageCell)?.currentMessagesGroup
					group?.transitionParams?.reset()
				}

				if (mMoveAnimations.remove(holder)) {
					dispatchMoveFinished(holder)
					dispatchFinishedWhenDone()
				}
			}
		})

		animatorSet.start()
		animators[holder] = animatorSet
	}

	override fun resetAnimation(holder: RecyclerView.ViewHolder) {
		reset = true
		super.resetAnimation(holder)
		reset = false
	}

	override fun animateChange(oldHolder: RecyclerView.ViewHolder, newHolder: RecyclerView.ViewHolder?, info: ItemHolderInfo, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
		if (oldHolder === newHolder) {
			// Don't know how to run change animations when the same view holder is re-used.
			// run a move animation to handle position changes.
			return animateMove(oldHolder, info, fromX, fromY, toX, toY)
		}

		val prevTranslationX = if (oldHolder.itemView is ChatMessageCell) {
			oldHolder.itemView.getAnimationOffsetX()
		}
		else {
			oldHolder.itemView.translationX
		}

		val prevTranslationY = oldHolder.itemView.translationY
		val prevAlpha = oldHolder.itemView.alpha

		resetAnimation(oldHolder)

		val deltaX = (toX - fromX - prevTranslationX).toInt()
		val deltaY = (toY - fromY - prevTranslationY).toInt()

		// recover prev translation state after ending animation

		if (oldHolder.itemView is ChatMessageCell) {
			oldHolder.itemView.setAnimationOffsetX(prevTranslationX)
		}
		else {
			oldHolder.itemView.translationX = prevTranslationX
		}

		oldHolder.itemView.translationY = prevTranslationY
		oldHolder.itemView.alpha = prevAlpha

		if (newHolder != null) {
			// carry over translation values

			resetAnimation(newHolder)

			if (newHolder.itemView is ChatMessageCell) {
				newHolder.itemView.setAnimationOffsetX(-deltaX.toFloat())
			}
			else {
				newHolder.itemView.translationX = -deltaX.toFloat()
			}

			newHolder.itemView.translationY = -deltaY.toFloat()
			newHolder.itemView.alpha = 0f
		}

		mPendingChanges.add(ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY))

		checkIsRunning()

		return true
	}

	override fun animateChangeImpl(changeInfo: ChangeInfo) {
		val holder = changeInfo.oldHolder
		val view = holder?.itemView
		val newHolder = changeInfo.newHolder
		val newView = newHolder?.itemView

		if (view != null) {
			val oldViewAnim = view.animate().setDuration(changeDuration)

			mChangeAnimations.add(changeInfo.oldHolder)

			oldViewAnim.translationX((changeInfo.toX - changeInfo.fromX).toFloat())
			oldViewAnim.translationY((changeInfo.toY - changeInfo.fromY).toFloat())

			oldViewAnim.alpha(0f).setListener(object : AnimatorListenerAdapter() {
				override fun onAnimationStart(animator: Animator) {
					dispatchChangeStarting(changeInfo.oldHolder, true)
				}

				override fun onAnimationEnd(animator: Animator) {
					oldViewAnim.setListener(null)

					view.alpha = 1f
					view.scaleX = 1f
					view.scaleX = 1f

					if (view is ChatMessageCell) {
						view.setAnimationOffsetX(0f)
					}
					else {
						view.translationX = 0f
					}

					view.translationY = 0f

					if (mChangeAnimations.remove(changeInfo.oldHolder)) {
						dispatchChangeFinished(changeInfo.oldHolder, true)
						dispatchFinishedWhenDone()
					}
				}
			}).start()
		}

		if (newView != null) {
			val newViewAnimation = newView.animate()

			mChangeAnimations.add(changeInfo.newHolder)

			newViewAnimation.translationX(0f).translationY(0f).setDuration(changeDuration).alpha(1f).setListener(object : AnimatorListenerAdapter() {
				override fun onAnimationStart(animator: Animator) {
					dispatchChangeStarting(changeInfo.newHolder, false)
				}

				override fun onAnimationEnd(animator: Animator) {
					newViewAnimation.setListener(null)

					newView.alpha = 1f
					newView.scaleX = 1f
					newView.scaleX = 1f

					if (newView is ChatMessageCell) {
						newView.setAnimationOffsetX(0f)
					}
					else {
						newView.translationX = 0f
					}

					newView.translationY = 0f

					if (mChangeAnimations.remove(changeInfo.newHolder)) {
						dispatchChangeFinished(changeInfo.newHolder, false)
						dispatchFinishedWhenDone()
					}
				}
			}).start()
		}
	}

	override fun recordPreLayoutInformation(state: RecyclerView.State, viewHolder: RecyclerView.ViewHolder, changeFlags: Int, payloads: List<Any>): ItemHolderInfo {
		val info = super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads)

		if (viewHolder.itemView is ChatMessageCell) {
			val extended = ItemHolderInfoExtended()
			extended.left = info.left
			extended.top = info.top
			extended.right = info.right
			extended.bottom = info.bottom

			val params = viewHolder.itemView.transitionParams

			extended.imageX = params.lastDrawingImageX
			extended.imageY = params.lastDrawingImageY
			extended.imageWidth = params.lastDrawingImageW
			extended.imageHeight = params.lastDrawingImageH

			return extended
		}

		return info
	}

	override fun onAllAnimationsDone() {
		super.onAllAnimationsDone()

		recyclerListView.clipChildren = true

		while (runOnAnimationsEnd.isNotEmpty()) {
			runOnAnimationsEnd.removeAt(0).run()
		}

		cancelAnimators()
	}

	private fun cancelAnimators() {
		val anim = ArrayList(animators.values)

		animators.clear()

		for (animator in anim) {
			animator?.cancel()
		}
	}

	override fun endAnimation(item: RecyclerView.ViewHolder) {
		val animator = animators.remove(item)
		animator?.cancel()

		super.endAnimation(item)

		restoreTransitionParams(item.itemView)
	}

	private fun restoreTransitionParams(view: View) {
		view.alpha = 1f
		view.scaleX = 1f
		view.scaleY = 1f
		view.translationY = 0f

		if (view is BotHelpCell) {
			val top = recyclerListView.measuredHeight / 2 - view.getMeasuredHeight() / 2

			view.setAnimating(false)

			if (view.getTop() > top) {
				view.setTranslationY((top - view.getTop()).toFloat())
			}
			else {
				view.setTranslationY(0f)
			}
		}
		else if (view is ChatMessageCell) {
			view.transitionParams.resetAnimation()
			view.setAnimationOffsetX(0f)
		}
		else {
			view.translationX = 0f
		}
	}

	override fun endAnimations() {
		for (groupedMessages in willChangedGroups) {
			groupedMessages.transitionParams.isNewGroup = false
		}

		willChangedGroups.clear()

		cancelAnimators()

		chatGreetingsView?.stickerToSendView?.alpha = 1f

		greetingsSticker = null
		chatGreetingsView = null

		var count = mPendingMoves.size

		for (i in count - 1 downTo 0) {
			val item = mPendingMoves[i]
			val view = item.holder.itemView
			restoreTransitionParams(view)
			dispatchMoveFinished(item.holder)
			mPendingMoves.removeAt(i)
		}

		count = mPendingRemovals.size

		for (i in count - 1 downTo 0) {
			val item = mPendingRemovals[i]
			restoreTransitionParams(item.itemView)
			dispatchRemoveFinished(item)
			mPendingRemovals.removeAt(i)
		}

		count = mPendingAdditions.size

		for (i in count - 1 downTo 0) {
			val item = mPendingAdditions[i]
			restoreTransitionParams(item.itemView)
			dispatchAddFinished(item)
			mPendingAdditions.removeAt(i)
		}

		count = mPendingChanges.size

		for (i in count - 1 downTo 0) {
			endChangeAnimationIfNecessary(mPendingChanges[i])
		}

		mPendingChanges.clear()

		if (!isRunning) {
			return
		}

		var listCount = mMovesList.size

		for (i in listCount - 1 downTo 0) {
			val moves = mMovesList[i]

			count = moves.size

			for (j in count - 1 downTo 0) {
				val moveInfo = moves[j]
				val item = moveInfo.holder

				restoreTransitionParams(item.itemView)
				dispatchMoveFinished(moveInfo.holder)
				moves.removeAt(j)

				if (moves.isEmpty()) {
					mMovesList.remove(moves)
				}
			}
		}

		listCount = mAdditionsList.size

		for (i in listCount - 1 downTo 0) {
			val additions = mAdditionsList[i]

			count = additions.size

			for (j in count - 1 downTo 0) {
				val item = additions[j]

				restoreTransitionParams(item.itemView)
				dispatchAddFinished(item)
				additions.removeAt(j)

				if (additions.isEmpty()) {
					mAdditionsList.remove(additions)
				}
			}
		}

		listCount = mChangesList.size

		for (i in listCount - 1 downTo 0) {
			val changes = mChangesList[i]

			count = changes.size

			for (j in count - 1 downTo 0) {
				endChangeAnimationIfNecessary(changes[j])

				if (changes.isEmpty()) {
					mChangesList.remove(changes)
				}
			}
		}

		cancelAll(mRemoveAnimations)
		cancelAll(mMoveAnimations)
		cancelAll(mAddAnimations)
		cancelAll(mChangeAnimations)

		dispatchAnimationsFinished()
	}

	override fun endChangeAnimationIfNecessary(changeInfo: ChangeInfo, item: RecyclerView.ViewHolder): Boolean {
		val a = animators.remove(item)
		a?.cancel()

		var oldItem = false

		if (changeInfo.newHolder === item) {
			changeInfo.newHolder = null
		}
		else if (changeInfo.oldHolder === item) {
			changeInfo.oldHolder = null
			oldItem = true
		}
		else {
			return false
		}

		restoreTransitionParams(item.itemView)
		dispatchChangeFinished(item, oldItem)

		return true
	}

	fun groupWillTransformToSingleMessage(groupedMessages: GroupedMessages) {
		willRemovedGroup[groupedMessages.messages[0].id] = groupedMessages
	}

	fun groupWillChanged(groupedMessages: GroupedMessages?) {
		if (groupedMessages == null) {
			return
		}

		if (groupedMessages.messages.size == 0) {
			groupedMessages.transitionParams.drawBackgroundForDeletedItems = true
		}
		else {
			if (groupedMessages.transitionParams.top == 0 && groupedMessages.transitionParams.bottom == 0 && groupedMessages.transitionParams.left == 0 && groupedMessages.transitionParams.right == 0) {
				val n = recyclerListView.childCount

				for (i in 0 until n) {
					val child = recyclerListView.getChildAt(i)

					if (child is ChatMessageCell) {
						val messageObject = child.getMessageObject()

						if (child.transitionParams.wasDraw && groupedMessages.messages.contains(messageObject)) {
							groupedMessages.transitionParams.top = child.top + child.getBackgroundDrawableTop()
							groupedMessages.transitionParams.bottom = child.top + child.getBackgroundDrawableBottom()
							groupedMessages.transitionParams.left = child.left + child.getBackgroundDrawableLeft()
							groupedMessages.transitionParams.right = child.left + child.getBackgroundDrawableRight()
							groupedMessages.transitionParams.drawCaptionLayout = child.hasCaptionLayout()
							groupedMessages.transitionParams.pinnedTop = child.isPinnedTop
							groupedMessages.transitionParams.pinnedBottom = child.isPinnedBottom
							groupedMessages.transitionParams.isNewGroup = true
							break
						}
					}
				}
			}

			willChangedGroups.add(groupedMessages)
		}
	}

	override fun animateAddImpl(holder: RecyclerView.ViewHolder) {
		val view = holder.itemView

		mAddAnimations.add(holder)

		if (holder === greetingsSticker) {
			view.alpha = 1f
		}

		val animatorSet = AnimatorSet()

		if (view is ChatMessageCell) {
			if (view.getAnimationOffsetX() != 0f) {
				animatorSet.playTogether(ObjectAnimator.ofFloat(view, view.ANIMATION_OFFSET_X, view.getAnimationOffsetX(), 0f))
			}

			val pivotX = view.getBackgroundDrawableLeft() + (view.getBackgroundDrawableRight() - view.getBackgroundDrawableLeft()) / 2f

			view.pivotX = pivotX
			view.animate().translationY(0f).setDuration(addDuration).start()
		}
		else {
			view.animate().translationX(0f).translationY(0f).setDuration(addDuration).start()
		}

		var useScale = true
		var currentDelay = ((1f - max(0f, min(1f, view.bottom / recyclerListView.measuredHeight.toFloat()))) * 100).toLong()

		if (view is ChatMessageCell) {
			if (holder === greetingsSticker) {
				useScale = false

				chatGreetingsView?.stickerToSendView?.alpha = 0f

				recyclerListView.clipChildren = false

				val parentForGreetingsView = chatGreetingsView!!.parent as View
				val fromX = chatGreetingsView!!.stickerToSendView.x + chatGreetingsView!!.x + parentForGreetingsView.x
				val fromY = chatGreetingsView!!.stickerToSendView.y + chatGreetingsView!!.y + parentForGreetingsView.y
				var toX = view.photoImage.imageX + recyclerListView.x + view.x
				var toY = view.photoImage.imageY + recyclerListView.y + view.y
				val fromW = chatGreetingsView!!.stickerToSendView.width.toFloat()
				val fromH = chatGreetingsView!!.stickerToSendView.height.toFloat()
				val toW = view.photoImage.imageWidth
				val toH = view.photoImage.imageHeight
				val deltaX = fromX - toX
				val deltaY = fromY - toY

				toX = view.photoImage.imageX
				toY = view.photoImage.imageY

				view.transitionParams.imageChangeBoundsTransition = true
				view.transitionParams.animateDrawingTimeAlpha = true
				view.photoImage.setImageCoordinates(toX + deltaX, toX + deltaY, fromW, fromH)

				val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

				valueAnimator.addUpdateListener {
					val v = it.animatedValue as Float

					view.transitionParams.animateChangeProgress = v

					if (view.transitionParams.animateChangeProgress > 1) {
						view.transitionParams.animateChangeProgress = 1f
					}

					view.photoImage.setImageCoordinates(toX + deltaX * (1f - v), toY + deltaY * (1f - v), fromW * (1f - v) + toW * v, fromH * (1f - v) + toH * v)
					view.invalidate()
				}

				valueAnimator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						view.transitionParams.resetAnimation()
						view.photoImage.setImageCoordinates(toX, toY, toW, toH)

						chatGreetingsView?.stickerToSendView?.alpha = 1f

						view.invalidate()
					}
				})

				animatorSet.play(valueAnimator)
			}
			else {
				val groupedMessages = view.currentMessagesGroup

				if (groupedMessages != null) {
					val groupDelay = groupIdToEnterDelay[groupedMessages.groupId]

					if (groupDelay == null) {
						groupIdToEnterDelay[groupedMessages.groupId] = currentDelay
					}
					else {
						currentDelay = groupDelay
					}
				}

				if (groupedMessages != null && groupedMessages.transitionParams.backgroundChangeBounds) {
					animatorSet.startDelay = 140
				}
			}
		}

		view.alpha = 0f

		animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 1f))

		if (useScale) {
			view.scaleX = 0.9f
			view.scaleY = 0.9f

			animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, 1f))
			animatorSet.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, 1f))
		}
		else {
			view.scaleX = 1f
			view.scaleY = 1f
		}

		if (holder === greetingsSticker) {
			animatorSet.duration = 350
			animatorSet.interpolator = OvershootInterpolator()
		}
		else {
			animatorSet.startDelay = currentDelay
			animatorSet.duration = DEFAULT_DURATION
		}

		animatorSet.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationStart(animator: Animator) {
				dispatchAddStarting(holder)
			}

			override fun onAnimationCancel(animator: Animator) {
				view.alpha = 1f
			}

			override fun onAnimationEnd(animator: Animator) {
				animator.removeAllListeners()

				view.alpha = 1f
				view.scaleX = 1f
				view.scaleY = 1f
				view.translationY = 0f
				view.translationY = 0f

				if (mAddAnimations.remove(holder)) {
					dispatchAddFinished(holder)
					dispatchFinishedWhenDone()
				}
			}
		})

		animators[holder] = animatorSet

		animatorSet.start()
	}

	override fun animateRemoveImpl(holder: RecyclerView.ViewHolder) {
		val view = holder.itemView

		mRemoveAnimations.add(holder)

		val animator = ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f)

		dispatchRemoveStarting(holder)

		animator.duration = removeDuration
		animator.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animator: Animator) {
				animator.removeAllListeners()

				view.alpha = 1f
				view.scaleX = 1f
				view.scaleY = 1f
				view.translationX = 0f
				view.translationY = 0f

				if (mRemoveAnimations.remove(holder)) {
					dispatchRemoveFinished(holder)
					dispatchFinishedWhenDone()
				}
			}
		})

		animators[holder] = animator

		animator.start()

		recyclerListView.stopScroll()
	}

	fun setShouldAnimateEnterFromBottom(shouldAnimateEnterFromBottom: Boolean) {
		this.shouldAnimateEnterFromBottom = shouldAnimateEnterFromBottom
	}

	open fun onAnimationStart() {
		// stub
	}

	override fun getMoveAnimationDelay(): Long {
		return 0
	}

	override fun getMoveDuration(): Long {
		return DEFAULT_DURATION
	}

	override fun getChangeDuration(): Long {
		return DEFAULT_DURATION
	}

	fun runOnAnimationEnd(runnable: Runnable) {
		runOnAnimationsEnd.add(runnable)
	}

	fun onDestroy() {
		onAllAnimationsDone()
	}

	fun willRemoved(view: View?): Boolean {
		val holder = recyclerListView.getChildViewHolder(view!!)

		return if (holder != null) {
			mPendingRemovals.contains(holder) || mRemoveAnimations.contains(holder)
		}
		else {
			false
		}
	}

	fun willAddedFromAlpha(view: View?): Boolean {
		if (shouldAnimateEnterFromBottom) {
			return false
		}

		val holder = recyclerListView.getChildViewHolder(view!!)

		return if (holder != null) {
			mPendingAdditions.contains(holder) || mAddAnimations.contains(holder)
		}
		else {
			false
		}
	}

	fun onGreetingStickerTransition(holder: RecyclerView.ViewHolder?, greetingsViewContainer: ChatGreetingsView?) {
		greetingsSticker = holder
		chatGreetingsView = greetingsViewContainer
		shouldAnimateEnterFromBottom = false
	}

	fun setReversePositions(reversePositions: Boolean) {
		this.reversePositions = reversePositions
	}

	protected class MoveInfoExtended(holder: RecyclerView.ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int) : MoveInfo(holder, fromX, fromY, toX, toY) {
		var groupOffsetTop = 0
		var groupOffsetBottom = 0
		var groupOffsetLeft = 0
		var groupOffsetRight = 0
		var animateChangeGroupBackground = false
		var animatePinnedBottom = false
		var animateBackgroundOnly = false
		var animateChangeInternal = false
		var animateImage = false
		var drawBackground = false
		var imageX = 0f
		var imageY = 0f
		var imageWidth = 0f
		var imageHeight = 0f
		var deltaLeft = 0
		var deltaRight = 0
		var deltaTop = 0
		var deltaBottom = 0
		var animateRemoveGroup = false
	}

	internal class ItemHolderInfoExtended : ItemHolderInfo() {
		var imageX = 0f
		var imageY = 0f
		var imageWidth = 0f
		var imageHeight = 0f
		var captionX = 0
		var captionY = 0
	}

	companion object {
		const val DEFAULT_DURATION: Long = 250

		@JvmField
		val DEFAULT_INTERPOLATOR: Interpolator = CubicBezierInterpolator(0.19919472913616398, 0.010644531250000006, 0.27920937042459737, 0.91025390625)
	}
}
