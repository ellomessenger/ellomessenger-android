/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components.Reactions

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.MessagePeerReaction
import org.telegram.tgnet.tlrpc.TL_availableReaction
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.Reactions.ReactionsContainerLayout.ReactionHolderView
import org.telegram.ui.SelectAnimatedEmojiDialog
import java.util.Random
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class ReactionsEffectOverlay private constructor(context: Context, fragment: BaseFragment, reactionsLayout: ReactionsContainerLayout?, private val cell: ChatMessageCell, fromAnimationView: View?, x: Float, y: Float, private val reaction: VisibleReaction, currentAccount: Int, private val animationType: Int) {
	private val effectImageView = AnimationView(context)
	private val emojiImageView = AnimationView(context)
	private val emojiStaticImageView = AnimationView(context)
	private val container: FrameLayout
	private var animateInProgress: Float
	private var animateOutProgress: Float
	private var windowView: FrameLayout
	private var loc = IntArray(2)
	private var windowManager: WindowManager? = null
	private var dismissed = false
	private var dismissProgress = 0f
	private val messageId = cell.getMessageObject()?.id ?: 0
	private val groupId = cell.getMessageObject()?.groupId ?: 0
	private var lastDrawnToX = 0f
	private var lastDrawnToY = 0f
	private var started = false
	private var holderView: ReactionHolderView? = null
	private var wasScrolled = false
	private val finished = false
	private var useWindow = false
	private var decorView: ViewGroup? = null
	private val avatars = mutableListOf<AvatarParticle>()
	private var startTime: Long = 0

	init {
		val reactionButton = cell.getReactionButton(reaction)
		val fromX: Float
		val fromY: Float
		val fromHeight: Float
		val chatActivity = fragment as? ChatActivity

		if (reactionsLayout != null) {
			for (i in 0 until reactionsLayout.recyclerListView.childCount) {
				if (reactionsLayout.recyclerListView.getChildAt(i) is ReactionHolderView) {
					if ((reactionsLayout.recyclerListView.getChildAt(i) as? ReactionHolderView)?.currentReaction == reaction) {
						holderView = reactionsLayout.recyclerListView.getChildAt(i) as? ReactionHolderView
						break
					}
				}
			}
		}

		if (animationType == SHORT_ANIMATION) {
			val random = Random()
			var recentReactions: List<MessagePeerReaction>? = null
			val messageOwnerReactions = cell.getMessageObject()?.messageOwner?.reactions

			if (messageOwnerReactions != null) {
				recentReactions = messageOwnerReactions.recentReactions
			}

			if (recentReactions != null && chatActivity != null && chatActivity.dialogId < 0) {
				for (i in recentReactions.indices) {
					if (reaction.equals(recentReactions[i].reaction) && recentReactions[i].unread) {
						var user: User?
						var chat: Chat?
						val avatarDrawable = AvatarDrawable()
						val imageReceiver = ImageReceiver()
						val peerId = MessageObject.getPeerId(recentReactions[i].peer_id)

						if (peerId < 0) {
							chat = MessagesController.getInstance(currentAccount).getChat(-peerId)

							if (chat == null) {
								continue
							}

							avatarDrawable.setInfo(chat)
							imageReceiver.setForUserOrChat(chat, avatarDrawable)
						}
						else {
							user = MessagesController.getInstance(currentAccount).getUser(peerId)

							if (user == null) {
								continue
							}

							avatarDrawable.setInfo(user)
							imageReceiver.setForUserOrChat(user, avatarDrawable)
						}

						val avatarParticle = AvatarParticle()
						avatarParticle.imageReceiver = imageReceiver
						avatarParticle.fromX = 0.5f // + Math.abs(random.nextInt() % 100) / 100f * 0.2f;
						avatarParticle.fromY = 0.5f // + Math.abs(random.nextInt() % 100) / 100f * 0.2f;
						avatarParticle.jumpY = 0.3f + abs(random.nextInt() % 100) / 100f * 0.1f
						avatarParticle.randomScale = 0.8f + abs(random.nextInt() % 100) / 100f * 0.4f
						avatarParticle.randomRotation = 60 * abs(random.nextInt() % 100) / 100f
						avatarParticle.leftTime = (400 + abs(random.nextInt() % 100) / 100f * 200).toInt()

						if (avatars.isEmpty()) {
							avatarParticle.toX = 0.2f + 0.6f * abs(random.nextInt() % 100) / 100f
							avatarParticle.toY = 0.4f * abs(random.nextInt() % 100) / 100f
						}
						else {
							var bestDistance = 0f
							var bestX = 0f
							var bestY = 0f

							for (k in 0..9) {
								val randX = 0.2f + 0.6f * abs(random.nextInt() % 100) / 100f
								val randY = 0.2f + 0.4f * abs(random.nextInt() % 100) / 100f
								var minDistance = Int.MAX_VALUE.toFloat()

								for (j in avatars.indices) {
									val rx = avatars[j].toX - randX
									val ry = avatars[j].toY - randY
									val distance = rx * rx + ry * ry

									if (distance < minDistance) {
										minDistance = distance
									}
								}

								if (minDistance > bestDistance) {
									bestDistance = minDistance
									bestX = randX
									bestY = randY
								}
							}

							avatarParticle.toX = bestX
							avatarParticle.toY = bestY
						}

						avatars.add(avatarParticle)
					}
				}
			}
		}

		val fromHolder = holderView != null || x != 0f && y != 0f

		if (fromAnimationView != null) {
			fromAnimationView.getLocationOnScreen(loc)

			var viewX = loc[0].toFloat()
			var viewY = loc[1].toFloat()
			var viewSize = fromAnimationView.width * fromAnimationView.scaleX

			if (fromAnimationView is SelectAnimatedEmojiDialog.ImageViewEmoji) {
				if (fromAnimationView.bigReactionSelectedProgress > 0) {
					val scale = 1 + 2 * fromAnimationView.bigReactionSelectedProgress
					viewSize = fromAnimationView.getWidth() * scale
					viewX -= (viewSize - fromAnimationView.getWidth()) / 2f
					viewY -= viewSize - fromAnimationView.getWidth()
				}
			}

			fromX = viewX
			fromY = viewY
			fromHeight = viewSize
		}
		else if (holderView != null) {
			holderView?.getLocationOnScreen(loc)
			fromX = loc[0] + holderView!!.loopImageView.x
			fromY = loc[1] + holderView!!.loopImageView.y
			fromHeight = holderView!!.loopImageView.width * holderView!!.scaleX
		}
		else if (reactionButton != null) {
			cell.getLocationInWindow(loc)
			fromX = loc[0] + cell.reactionsLayoutInBubble.x + reactionButton.x + reactionButton.imageReceiver.imageX
			fromY = loc[1] + cell.reactionsLayoutInBubble.y + reactionButton.y + reactionButton.imageReceiver.imageY
			fromHeight = reactionButton.imageReceiver.imageHeight
		}
		else {
			(cell.parent as? View)?.getLocationInWindow(loc)
			fromX = loc[0] + x
			fromY = loc[1] + y
			fromHeight = 0f
		}

		val size: Int
		val sizeForFilter: Int

		when (animationType) {
			ONLY_MOVE_ANIMATION -> {
				size = AndroidUtilities.dp(34f)
				sizeForFilter = (2f * size / AndroidUtilities.density).toInt()
			}

			SHORT_ANIMATION -> {
				size = AndroidUtilities.dp(80f)
				sizeForFilter = sizeForAroundReaction()
			}

			else -> {
				size = (min(AndroidUtilities.dp(350f), min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) * 0.8f).roundToInt()
				sizeForFilter = sizeForBigReaction()
			}
		}

		val emojiSize = size shr 1
		val emojiSizeForFilter = sizeForFilter shr 1
		val fromScale = fromHeight / emojiSize.toFloat()

		animateInProgress = 0f
		animateOutProgress = 0f

		container = FrameLayout(context)

		windowView = object : FrameLayout(context) {
			override fun dispatchDraw(canvas: Canvas) {
				if (dismissed) {
					if (dismissProgress != 1f) {
						dismissProgress += 16 / 150f

						if (dismissProgress > 1f) {
							dismissProgress = 1f

							AndroidUtilities.runOnUIThread {
								removeCurrentView()
							}
						}
					}

					if (dismissProgress != 1f) {
						alpha = 1f - dismissProgress
						super.dispatchDraw(canvas)
					}

					invalidate()

					return
				}

				if (!started) {
					invalidate()
					return
				}
				else {
					holderView?.enterImageView?.alpha = 0f
					holderView?.pressedBackupImageView?.alpha = 0f
				}

				val drawingCell = if (fragment is ChatActivity) {
					fragment.findMessageCell(messageId, false)
				}
				else {
					cell
				}

				var toX: Float
				var toY: Float

				val toH = if (cell.getMessageObject()?.shouldDrawReactionsInLayout() == true) {
					AndroidUtilities.dp(20f).toFloat()
				}
				else {
					AndroidUtilities.dp(14f).toFloat()
				}

				if (drawingCell != null) {
					cell.getLocationInWindow(loc)

					@Suppress("NAME_SHADOWING") val reactionButton = cell.getReactionButton(reaction)

					toX = (loc[0] + cell.reactionsLayoutInBubble.x).toFloat()
					toY = (loc[1] + cell.reactionsLayoutInBubble.y).toFloat()

					if (reactionButton != null) {
						toX += (reactionButton.x + reactionButton.drawingImageRect.left).toFloat()
						toY += (reactionButton.y + reactionButton.drawingImageRect.top).toFloat()
					}

					if (chatActivity != null) {
						toY += chatActivity.drawingChatLisViewYoffset
					}

					if (drawingCell.drawPinnedBottom && !drawingCell.shouldDrawTimeOnMedia()) {
						toY += AndroidUtilities.dp(2f).toFloat()
					}

					lastDrawnToX = toX
					lastDrawnToY = toY
				}
				else {
					toX = lastDrawnToX
					toY = lastDrawnToY
				}

				alpha = if (fragment.parentActivity != null && fragment.fragmentView?.parent != null && fragment.fragmentView?.visibility == VISIBLE) {
					fragment.fragmentView?.getLocationOnScreen(loc)
					(fragment.fragmentView?.parent as? View)?.alpha ?: 0f
				}
				else {
					return
				}

				var previewX = toX - (emojiSize - toH) / 2f
				val previewY = toY - (emojiSize - toH) / 2f

				if (animationType != SHORT_ANIMATION) {
					if (previewX < loc[0]) {
						previewX = loc[0].toFloat()
					}
					if (previewX + emojiSize > loc[0] + measuredWidth) {
						previewX = (loc[0] + measuredWidth - emojiSize).toFloat()
					}
				}

				val animateInProgressX: Float
				val animateInProgressY: Float
				val animateOutProgress = CubicBezierInterpolator.DEFAULT.getInterpolation(animateOutProgress)

				if (animationType == ONLY_MOVE_ANIMATION) {
					animateInProgressX = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(animateOutProgress)
					animateInProgressY = CubicBezierInterpolator.DEFAULT.getInterpolation(animateOutProgress)
				}
				else if (fromHolder) {
					animateInProgressX = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(animateInProgress)
					animateInProgressY = CubicBezierInterpolator.DEFAULT.getInterpolation(animateInProgress)
				}
				else {
					animateInProgressY = animateInProgress
					animateInProgressX = animateInProgressY
				}

				var scale = animateInProgressX + (1f - animateInProgressX) * fromScale
				val toScale = toH / emojiSize.toFloat()

				@Suppress("NAME_SHADOWING") var x: Float
				@Suppress("NAME_SHADOWING") var y: Float

				if (animationType == SHORT_ANIMATION) {
					x = previewX
					y = previewY
					scale = 1f
				}
				else {
					x = fromX * (1f - animateInProgressX) + previewX * animateInProgressX
					y = fromY * (1f - animateInProgressY) + previewY * animateInProgressY
				}

				effectImageView.translationX = x
				effectImageView.translationY = y
				effectImageView.alpha = 1f - animateOutProgress
				effectImageView.scaleX = scale
				effectImageView.scaleY = scale

				if (animationType == ONLY_MOVE_ANIMATION) {
					scale = fromScale * (1f - animateInProgressX) + toScale * animateInProgressX
					x = fromX * (1f - animateInProgressX) + toX * animateInProgressX
					y = fromY * (1f - animateInProgressY) + toY * animateInProgressY
				}
				else {
					if (animateOutProgress != 0f) {
						scale = scale * (1f - animateOutProgress) + toScale * animateOutProgress
						x = x * (1f - animateOutProgress) + toX * animateOutProgress
						y = y * (1f - animateOutProgress) + toY * animateOutProgress
					}
				}

				if (animationType != SHORT_ANIMATION) {
					emojiStaticImageView.alpha = if (animateOutProgress > 0.7f) (animateOutProgress - 0.7f) / 0.3f else 0f
				}

				//emojiImageView.setAlpha(animateOutProgress < 0.5f ? 1f - (animateOutProgress / 0.5f) : 0f);

				container.translationX = x
				container.translationY = y
				container.scaleX = scale
				container.scaleY = scale

				super.dispatchDraw(canvas)

				if ((animationType == SHORT_ANIMATION || emojiImageView.wasPlaying) && animateInProgress != 1f) {
					animateInProgress += if (fromHolder) {
						16f / 350f
					}
					else {
						16f / 220f
					}

					if (animateInProgress > 1f) {
						animateInProgress = 1f
					}
				}

				if (animationType == ONLY_MOVE_ANIMATION || wasScrolled && animationType == LONG_ANIMATION || animationType != SHORT_ANIMATION && emojiImageView.wasPlaying && emojiImageView.imageReceiver.lottieAnimation != null && !emojiImageView.imageReceiver.lottieAnimation!!.isRunning || reaction.documentId != 0L && System.currentTimeMillis() - startTime > 2000 || animationType == SHORT_ANIMATION && effectImageView.wasPlaying && effectImageView.imageReceiver.lottieAnimation != null && !effectImageView.imageReceiver.lottieAnimation!!.isRunning || reaction.documentId != 0L && System.currentTimeMillis() - startTime > 2000) {
					if (this@ReactionsEffectOverlay.animateOutProgress != 1f) {
						if (animationType == SHORT_ANIMATION) {
							this@ReactionsEffectOverlay.animateOutProgress = 1f
						}
						else {
							val duration = if (animationType == ONLY_MOVE_ANIMATION) 350f else 220f
							this@ReactionsEffectOverlay.animateOutProgress += 16f / duration
						}

						if (this@ReactionsEffectOverlay.animateOutProgress > 0.7f && !finished) {
							startShortAnimation()
						}

						if (this@ReactionsEffectOverlay.animateOutProgress >= 1f) {
							if (animationType == LONG_ANIMATION || animationType == ONLY_MOVE_ANIMATION) {
								cell.reactionsLayoutInBubble.animateReaction(reaction)
							}

							this@ReactionsEffectOverlay.animateOutProgress = 1f

							if (animationType == SHORT_ANIMATION) {
								currentShortOverlay = null
							}
							else {
								currentOverlay = null
							}

							cell.invalidate()

							if (cell.currentMessagesGroup != null) {
								(cell.parent as? View)?.invalidate()
							}

							AndroidUtilities.runOnUIThread {
								removeCurrentView()
							}
						}
					}
				}

				if (avatars.isNotEmpty() && effectImageView.wasPlaying) {
					val animation = effectImageView.imageReceiver.lottieAnimation
					var i = 0

					while (i < avatars.size) {
						val particle = avatars[i]
						val progress = particle.progress

						val isLeft = if (animation != null && animation.isRunning) {
							val duration = effectImageView.imageReceiver.lottieAnimation!!.duration
							val totalFramesCount = effectImageView.imageReceiver.lottieAnimation!!.framesCount
							val currentFrame = effectImageView.imageReceiver.lottieAnimation!!.currentFrame
							val timeLeft = (duration - duration * (currentFrame / totalFramesCount.toFloat())).toInt()
							timeLeft < particle.leftTime
						}
						else {
							true
						}

						if (isLeft && particle.outProgress != 1f) {
							particle.outProgress += 16f / 150f

							if (particle.outProgress > 1f) {
								particle.outProgress = 1f
								avatars.removeAt(i)
								i--
								i++
								continue
							}
						}

						val jumpProgress = if (progress < 0.5f) progress / 0.5f else 1f - (progress - 0.5f) / 0.5f
						val avatarX = particle.fromX * (1f - progress) + particle.toX * progress
						val avatarY = particle.fromY * (1f - progress) + particle.toY * progress - particle.jumpY * jumpProgress
						val s = progress * particle.randomScale * (1f - particle.outProgress)
						val cx = effectImageView.x + effectImageView.width * effectImageView.scaleX * avatarX
						val cy = effectImageView.y + effectImageView.height * effectImageView.scaleY * avatarY

						@Suppress("NAME_SHADOWING") val size = AndroidUtilities.dp(16f)

						avatars[i].imageReceiver?.setImageCoordinates(cx - size / 2f, cy - size / 2f, size.toFloat(), size.toFloat())
						avatars[i].imageReceiver?.setRoundRadius(size shr 1)

						canvas.save()
						canvas.translate(0f, particle.globalTranslationY)
						canvas.scale(s, s, cx, cy)
						canvas.rotate(particle.currentRotation, cx, cy)

						avatars[i].imageReceiver?.draw(canvas)

						canvas.restore()

						if (particle.progress < 1f) {
							particle.progress += 16f / 350f

							if (particle.progress > 1f) {
								particle.progress = 1f
							}
						}

						if (progress >= 1f) {
							particle.globalTranslationY += AndroidUtilities.dp(20f) * 16f / 500f
						}

						if (particle.incrementRotation) {
							particle.currentRotation += particle.randomRotation / 250f

							if (particle.currentRotation > particle.randomRotation) {
								particle.incrementRotation = false
							}
						}
						else {
							particle.currentRotation -= particle.randomRotation / 250f

							if (particle.currentRotation < -particle.randomRotation) {
								particle.incrementRotation = true
							}
						}

						i++
					}
				}

				invalidate()
			}

			override fun onAttachedToWindow() {
				super.onAttachedToWindow()

				avatars.forEach {
					it.imageReceiver?.onAttachedToWindow()
				}
			}

			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()

				avatars.forEach {
					it.imageReceiver?.onDetachedFromWindow()
				}
			}
		}

		var availableReaction: TL_availableReaction? = null

		if (reaction.emojicon != null) {
			availableReaction = MediaDataController.getInstance(currentAccount).reactionsMap[reaction.emojicon]
		}

		if (availableReaction != null || reaction.documentId != 0L) {
			if (availableReaction != null) {
				if (animationType != ONLY_MOVE_ANIMATION) {
					val document = if (animationType == SHORT_ANIMATION) availableReaction.around_animation else availableReaction.effect_animation
					val filter = if (animationType == SHORT_ANIMATION) filterForAroundAnimation else sizeForFilter.toString() + "_" + sizeForFilter

					effectImageView.imageReceiver.uniqueKeyPrefix = uniquePrefix++.toString() + "_" + cell.getMessageObject()!!.id + "_"
					effectImageView.setImage(ImageLocation.getForDocument(document), filter, null, null, 0, null)
					effectImageView.imageReceiver.setAutoRepeat(0)
					effectImageView.imageReceiver.allowStartAnimation = false

					effectImageView.imageReceiver.lottieAnimation?.setCurrentFrame(0, false)
					effectImageView.imageReceiver.lottieAnimation?.start()
				}

				if (animationType == ONLY_MOVE_ANIMATION) {
					val document = availableReaction.appear_animation
					emojiImageView.imageReceiver.uniqueKeyPrefix = uniquePrefix++.toString() + "_" + cell.getMessageObject()!!.id + "_"
					emojiImageView.setImage(ImageLocation.getForDocument(document), emojiSizeForFilter.toString() + "_" + emojiSizeForFilter, null, null, 0, null)
				}
				else if (animationType == LONG_ANIMATION) {
					val document = availableReaction.activate_animation
					emojiImageView.imageReceiver.uniqueKeyPrefix = uniquePrefix++.toString() + "_" + cell.getMessageObject()!!.id + "_"
					emojiImageView.setImage(ImageLocation.getForDocument(document), emojiSizeForFilter.toString() + "_" + emojiSizeForFilter, null, null, 0, null)
				}
			}
			else {
				if (animationType == LONG_ANIMATION) {
					emojiImageView.setAnimatedReactionDrawable(AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE, currentAccount, reaction.documentId))
				}
				else if (animationType == ONLY_MOVE_ANIMATION) {
					emojiImageView.setAnimatedReactionDrawable(AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, currentAccount, reaction.documentId))
				}

				if (animationType == LONG_ANIMATION || animationType == SHORT_ANIMATION) {
					effectImageView.setAnimatedEmojiEffect(AnimatedEmojiEffect.createFrom(AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, currentAccount, reaction.documentId), animationType == LONG_ANIMATION, true))
					windowView.clipChildren = false
				}
			}

			emojiImageView.imageReceiver.setAutoRepeat(0)
			emojiImageView.imageReceiver.allowStartAnimation = false

			emojiImageView.imageReceiver.lottieAnimation?.let {
				if (animationType == ONLY_MOVE_ANIMATION) {
					it.setCurrentFrame(it.framesCount - 1, false)
				}
				else {
					it.setCurrentFrame(0, false)
					it.start()
				}
			}

			val topOffset = size - emojiSize shr 1

			val leftOffset = if (animationType == SHORT_ANIMATION) {
				topOffset
			}
			else {
				size - emojiSize
			}

			container.addView(emojiImageView)

			emojiImageView.layoutParams.width = emojiSize
			emojiImageView.layoutParams.height = emojiSize
			(emojiImageView.layoutParams as? FrameLayout.LayoutParams)?.topMargin = topOffset
			(emojiImageView.layoutParams as? FrameLayout.LayoutParams)?.leftMargin = leftOffset

			if (animationType != SHORT_ANIMATION) {
				// MARK: this was causing second overlay upscaled and cropped emoji image to appear
//				if (availableReaction != null) {
//					emojiStaticImageView.imageReceiver.setImage(ImageLocation.getForDocument(availableReaction.center_icon), "40_40_lastframe", null, "webp", availableReaction, 1)
//				}

				container.addView(emojiStaticImageView)

				emojiStaticImageView.layoutParams.width = emojiSize
				emojiStaticImageView.layoutParams.height = emojiSize
				(emojiStaticImageView.layoutParams as? FrameLayout.LayoutParams)?.topMargin = topOffset
				(emojiStaticImageView.layoutParams as? FrameLayout.LayoutParams)?.leftMargin = leftOffset
			}

			windowView.addView(container)

			container.layoutParams.width = size
			container.layoutParams.height = size
			(container.layoutParams as? FrameLayout.LayoutParams)?.topMargin = -topOffset
			(container.layoutParams as? FrameLayout.LayoutParams)?.leftMargin = -leftOffset

			//if (availableReaction != null) {
			windowView.addView(effectImageView)

			effectImageView.layoutParams.width = size
			effectImageView.layoutParams.height = size
			effectImageView.layoutParams.width = size
			effectImageView.layoutParams.height = size
			(effectImageView.layoutParams as? FrameLayout.LayoutParams)?.topMargin = -topOffset
			(effectImageView.layoutParams as? FrameLayout.LayoutParams)?.leftMargin = -leftOffset
			// }

			container.pivotX = leftOffset.toFloat()
			container.pivotY = topOffset.toFloat()
		}
		else {
			dismissed = true
		}
	}

	private fun removeCurrentView() {
		runCatching {
			if (useWindow) {
				windowManager?.removeView(windowView)
			}
			else {
				decorView?.removeView(windowView)
			}
		}
	}

	private inner class AnimationView(context: Context) : BackupImageView(context) {
		var wasPlaying = false
		var emojiEffect: AnimatedEmojiEffect? = null
		override var animatedEmojiDrawable: AnimatedEmojiDrawable? = null
		override var attached = false

		override fun onDraw(canvas: Canvas) {
			if (animatedEmojiDrawable != null) {
				animatedEmojiDrawable?.setBounds(0, 0, measuredWidth, measuredHeight)
				animatedEmojiDrawable?.alpha = 255
				animatedEmojiDrawable?.draw(canvas)

				wasPlaying = true

				return
			}

			if (emojiEffect != null) {
				emojiEffect?.setBounds(0, 0, measuredWidth, measuredHeight)
				emojiEffect?.draw(canvas)

				wasPlaying = true

				return
			}

			wasPlaying = imageReceiver.lottieAnimation?.isRunning ?: false

			if (!wasPlaying && imageReceiver.lottieAnimation != null && imageReceiver.lottieAnimation?.isRunning != true) {
				if (animationType == ONLY_MOVE_ANIMATION) {
					imageReceiver.lottieAnimation?.setCurrentFrame((imageReceiver.lottieAnimation?.framesCount ?: 0) - 1, false)
				}
				else {
					imageReceiver.lottieAnimation?.setCurrentFrame(0, false)
					imageReceiver.lottieAnimation?.start()
				}
			}

			super.onDraw(canvas)
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()

			attached = true

			animatedEmojiDrawable?.addView(this)
			emojiEffect?.setView(this)
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()

			attached = false

			animatedEmojiDrawable?.removeView(this)
			emojiEffect?.removeView(this)
		}

		fun setAnimatedReactionDrawable(animatedEmojiDrawable: AnimatedEmojiDrawable?) {
			animatedEmojiDrawable?.removeView(this)

			this.animatedEmojiDrawable = animatedEmojiDrawable

			if (attached && animatedEmojiDrawable != null) {
				animatedEmojiDrawable.addView(this)
			}
		}

		fun setAnimatedEmojiEffect(effect: AnimatedEmojiEffect?) {
			emojiEffect = effect
		}
	}

	inner class AvatarParticle {
		var imageReceiver: ImageReceiver? = null
		var leftTime = 0
		var progress = 0f
		var outProgress = 0f
		var jumpY = 0f
		var fromX = 0f
		var fromY = 0f
		var toX = 0f
		var toY = 0f
		var randomScale = 0f
		var randomRotation = 0f
		var currentRotation = 0f
		var incrementRotation = false
		var globalTranslationY = 0f
	}

	companion object {
		const val LONG_ANIMATION = 0
		const val SHORT_ANIMATION = 1
		const val ONLY_MOVE_ANIMATION = 2
		private var uniquePrefix = 0
		private var lastHapticTime: Long = 0

		@SuppressLint("StaticFieldLeak")
		var currentOverlay: ReactionsEffectOverlay? = null

		@SuppressLint("StaticFieldLeak")
		var currentShortOverlay: ReactionsEffectOverlay? = null

		@JvmStatic
		val filterForAroundAnimation: String
			get() = sizeForAroundReaction().toString() + "_" + sizeForAroundReaction() + "_nolimit_pcache"

		@JvmStatic
		fun show(baseFragment: BaseFragment?, reactionsLayout: ReactionsContainerLayout?, cell: ChatMessageCell?, fromAnimationView: View?, x: Float, y: Float, visibleReaction: VisibleReaction?, currentAccount: Int, animationType: Int) {
			val parentActivity = baseFragment?.parentActivity

			if (cell == null || visibleReaction == null || baseFragment == null || parentActivity == null) {
				return
			}

			val animationEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true)

			if (!animationEnabled) {
				return
			}

			if (animationType == ONLY_MOVE_ANIMATION || animationType == LONG_ANIMATION) {
				show(baseFragment, null, cell, fromAnimationView, 0f, 0f, visibleReaction, currentAccount, SHORT_ANIMATION)
			}

			val reactionsEffectOverlay = ReactionsEffectOverlay(parentActivity, baseFragment, reactionsLayout, cell, fromAnimationView, x, y, visibleReaction, currentAccount, animationType)

			if (animationType == SHORT_ANIMATION) {
				currentShortOverlay = reactionsEffectOverlay
			}
			else {
				currentOverlay = reactionsEffectOverlay
			}

			var useWindow = false

			if (baseFragment is ChatActivity) {
				if ((animationType == LONG_ANIMATION || animationType == ONLY_MOVE_ANIMATION) && baseFragment.scrimPopupWindow?.isShowing == true) {
					useWindow = true
				}
			}

			reactionsEffectOverlay.useWindow = useWindow

			if (useWindow) {
				val lp = WindowManager.LayoutParams()
				lp.height = WindowManager.LayoutParams.MATCH_PARENT
				lp.width = lp.height
				lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
				lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
				lp.format = PixelFormat.TRANSLUCENT

				reactionsEffectOverlay.windowManager = parentActivity.windowManager
				reactionsEffectOverlay.windowManager?.addView(reactionsEffectOverlay.windowView, lp)
			}
			else {
				reactionsEffectOverlay.decorView = parentActivity.window.decorView as? FrameLayout
				reactionsEffectOverlay.decorView?.addView(reactionsEffectOverlay.windowView)
			}

			cell.invalidate()

			if (cell.currentMessagesGroup != null) {
				(cell.parent as? View)?.invalidate()
			}
		}

		@JvmStatic
		@Synchronized
		fun startAnimation() {
			if (currentOverlay != null) {
				currentOverlay?.started = true
				currentOverlay?.startTime = System.currentTimeMillis()

				if (currentOverlay?.animationType == LONG_ANIMATION && System.currentTimeMillis() - lastHapticTime > 200) {
					lastHapticTime = System.currentTimeMillis()
					currentOverlay?.cell?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
				}
			}
			else {
				startShortAnimation()

				currentShortOverlay?.let {
					it.cell.reactionsLayoutInBubble.animateReaction(it.reaction)
				}
			}
		}

		@Synchronized
		fun startShortAnimation() {
			val currentShortOverlay = currentShortOverlay ?: return

			if (!currentShortOverlay.started) {
				currentShortOverlay.started = true
				currentShortOverlay.startTime = System.currentTimeMillis()

				if (currentShortOverlay.animationType == SHORT_ANIMATION && System.currentTimeMillis() - lastHapticTime > 200) {
					lastHapticTime = System.currentTimeMillis()
					currentShortOverlay.cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
				}
			}
		}

		@JvmStatic
		@Synchronized
		fun removeCurrent(instant: Boolean) {
			for (i in 0..1) {
				val overlay = if (i == 0) currentOverlay else currentShortOverlay

				if (overlay != null) {
					if (instant) {
						overlay.removeCurrentView()
					}
					else {
						overlay.dismissed = true
					}
				}
			}

			currentShortOverlay = null
			currentOverlay = null
		}

		fun isPlaying(messageId: Int, groupId: Long, reaction: VisibleReaction?): Boolean {
			val currentOverlay = currentOverlay

			return if (currentOverlay != null && (currentOverlay.animationType == ONLY_MOVE_ANIMATION || currentOverlay.animationType == LONG_ANIMATION)) {
				(currentOverlay.groupId != 0L && groupId == currentOverlay.groupId || messageId == currentOverlay.messageId) && currentOverlay.reaction == reaction
			}
			else {
				false
			}
		}

		fun onScrolled(dy: Int) {
			val currentOverlay = currentOverlay ?: return

			currentOverlay.lastDrawnToY -= dy.toFloat()

			if (dy != 0) {
				currentOverlay.wasScrolled = true
			}
		}

		@JvmStatic
		fun sizeForBigReaction(): Int {
			return ((min(AndroidUtilities.dp(350f), min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) * 0.7f).roundToInt() / AndroidUtilities.density).toInt()
		}

		fun sizeForAroundReaction(): Int {
			val size = AndroidUtilities.dp(40f)
			return (2f * size / AndroidUtilities.density).toInt()
		}

		@Synchronized
		fun dismissAll() {
			currentOverlay?.dismissed = true
			currentShortOverlay?.dismissed = true
		}
	}
}
