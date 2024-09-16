/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.animation.Interpolator
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DialogObject
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.voip.VoIPService
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.TL_groupCallParticipant
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.GroupCallUserCell.AvatarWavesDrawable
import java.util.Random

class AvatarsDrawable(var parent: View?, private val isInCall: Boolean) {
	private val currentStates: Array<DrawingState>
	private val animatingStates: Array<DrawingState>
	var wasDraw = false
	private var transitionProgress = 1f
	private var updateAfterTransition = false
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val xRefP = Paint(Paint.ANTI_ALIAS_FLAG)
	private var updateDelegate: Runnable? = null
	private var currentStyle = 0
	private var centered = false
	private var overrideSize = 0
	private var overrideAlpha = 1f
	private var transitionDuration = 220L
	private var transitionInProgress = false
	var height: Int = 0
	var width: Int = 0

	@JvmField
	var transitionProgressAnimator: ValueAnimator? = null

	@JvmField
	var count: Int = 0

	fun setTransitionProgress(transitionProgress: Float) {
		if (transitionInProgress) {
			if (this.transitionProgress != transitionProgress) {
				this.transitionProgress = transitionProgress

				if (transitionProgress == 1f) {
					swapStates()
					transitionInProgress = false
				}
			}
		}
	}

	@JvmOverloads
	fun commitTransition(animated: Boolean, createAnimator: Boolean = true) {
		if (!wasDraw || !animated) {
			transitionProgress = 1f
			swapStates()
			return
		}

		val removedStates = arrayOfNulls<DrawingState>(3)
		var changed = false

		for (i in 0..2) {
			removedStates[i] = currentStates[i]

			if (currentStates[i].id != animatingStates[i].id) {
				changed = true
			}
			else {
				currentStates[i].lastSpeakTime = animatingStates[i].lastSpeakTime
			}
		}

		if (!changed) {
			transitionProgress = 1f
			return
		}

		for (i in 0..2) {
			var found = false

			for (j in 0..2) {
				if (currentStates[j].id == animatingStates[i].id) {
					found = true
					removedStates[j] = null

					if (i == j) {
						animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_NONE

						val wavesDrawable = animatingStates[i].wavesDrawable

						animatingStates[i].wavesDrawable = currentStates[i].wavesDrawable
						currentStates[i].wavesDrawable = wavesDrawable
					}
					else {
						animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_MOVE
						animatingStates[i].moveFromIndex = j
					}

					break
				}
			}

			if (!found) {
				animatingStates[i].animationType = DrawingState.ANIMATION_TYPE_IN
			}
		}

		for (i in 0..2) {
			removedStates[i]?.animationType = DrawingState.ANIMATION_TYPE_OUT
		}

		if (transitionProgressAnimator != null) {
			transitionProgressAnimator?.cancel()

			if (transitionInProgress) {
				swapStates()
				transitionInProgress = false
			}
		}

		transitionProgress = 0f

		if (createAnimator) {
			transitionProgressAnimator = ValueAnimator.ofFloat(0f, 1f)

			transitionProgressAnimator?.addUpdateListener {
				transitionProgress = it.animatedValue as Float
				invalidate()
			}

			transitionProgressAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (transitionProgressAnimator != null) {
						transitionProgress = 1f

						swapStates()

						if (updateAfterTransition) {
							updateAfterTransition = false
							updateDelegate?.run()
						}

						invalidate()
					}

					transitionProgressAnimator = null
				}
			})

			transitionProgressAnimator?.setDuration(transitionDuration)
			transitionProgressAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
			transitionProgressAnimator?.start()
		}
		else {
			transitionInProgress = true
		}

		invalidate()
	}

	private fun swapStates() {
		for (i in 0..2) {
			val state = currentStates[i]
			currentStates[i] = animatingStates[i]
			animatingStates[i] = state
		}
	}

	fun updateAfterTransitionEnd() {
		updateAfterTransition = true
	}

	fun setDelegate(delegate: Runnable?) {
		updateDelegate = delegate
	}

	fun setStyle(currentStyle: Int) {
		this.currentStyle = currentStyle
		invalidate()
	}

	private fun invalidate() {
		parent?.invalidate()
	}

	fun animateFromState(avatarsDrawable: AvatarsDrawable, currentAccount: Int, createAnimator: Boolean) {
		if (avatarsDrawable.transitionProgressAnimator != null) {
			avatarsDrawable.transitionProgressAnimator?.cancel()

			if (transitionInProgress) {
				transitionInProgress = false
				swapStates()
			}
		}

		val objects = arrayOfNulls<TLObject>(3)

		for (i in 0..2) {
			objects[i] = currentStates[i].`object`
			setObject(i, currentAccount, avatarsDrawable.currentStates[i].`object`)
		}

		commitTransition(false)

		for (i in 0..2) {
			setObject(i, currentAccount, objects[i])
		}

		wasDraw = true

		commitTransition(true, createAnimator)
	}

	fun setAlpha(alpha: Float) {
		overrideAlpha = alpha
	}

	class DrawingState {
		var avatarDrawable: AvatarDrawable? = null
		var wavesDrawable: AvatarWavesDrawable? = null
		var lastUpdateTime = 0L
		var lastSpeakTime = 0L
		var imageReceiver: ImageReceiver? = null
		var participant: TL_groupCallParticipant? = null
		var id = 0L
		var `object`: TLObject? = null
		var animationType = 0
		var moveFromIndex = 0

		companion object {
			const val ANIMATION_TYPE_NONE: Int = -1
			const val ANIMATION_TYPE_IN: Int = 0
			const val ANIMATION_TYPE_OUT: Int = 1
			const val ANIMATION_TYPE_MOVE: Int = 2
		}
	}

	val random = Random()

	init {
		currentStates = (0..2).map {
			val state = DrawingState()

			state.imageReceiver = ImageReceiver(parent)
			state.imageReceiver?.setRoundRadius(AndroidUtilities.dp(12f))

			state.avatarDrawable = AvatarDrawable()
			state.avatarDrawable?.setTextSize(AndroidUtilities.dp(12f))

			state
		}.toTypedArray()

		animatingStates = (0..2).map {
			val state = DrawingState()

			state.imageReceiver = ImageReceiver(parent)
			state.imageReceiver?.setRoundRadius(AndroidUtilities.dp(12f))

			state.avatarDrawable = AvatarDrawable()
			state.avatarDrawable?.setTextSize(AndroidUtilities.dp(12f))

			state
		}.toTypedArray()

		xRefP.color = 0
		xRefP.setXfermode(PorterDuffXfermode(PorterDuff.Mode.CLEAR))
	}

	fun setObject(index: Int, account: Int, `object`: TLObject?) {
		animatingStates[index].id = 0
		animatingStates[index].participant = null

		if (`object` == null) {
			animatingStates[index].imageReceiver?.setImageBitmap(null as Drawable?)
			invalidate()
			return
		}

		var currentUser: User? = null
		var currentChat: Chat? = null

		animatingStates[index].lastSpeakTime = -1
		animatingStates[index].`object` = `object`

		when (`object`) {
			is TL_groupCallParticipant -> {
				animatingStates[index].participant = `object`

				val id = MessageObject.getPeerId(`object`.peer)

				if (DialogObject.isUserDialog(id)) {
					currentUser = MessagesController.getInstance(account).getUser(id)
					animatingStates[index].avatarDrawable?.setInfo(currentUser)
				}
				else {
					currentChat = MessagesController.getInstance(account).getChat(-id)
					animatingStates[index].avatarDrawable?.setInfo(currentChat)
				}

				if (currentStyle == 4) {
					if (id == AccountInstance.getInstance(account).userConfig.getClientUserId()) {
						animatingStates[index].lastSpeakTime = 0
					}
					else {
						if (isInCall) {
							animatingStates[index].lastSpeakTime = `object`.lastActiveDate
						}
						else {
							animatingStates[index].lastSpeakTime = `object`.active_date.toLong()
						}
					}
				}
				else {
					animatingStates[index].lastSpeakTime = `object`.active_date.toLong()
				}

				animatingStates[index].id = id
			}

			is User -> {
				currentUser = `object`
				animatingStates[index].avatarDrawable?.setInfo(currentUser)
				animatingStates[index].id = currentUser.id
			}

			else -> {
				currentChat = `object` as? Chat
				animatingStates[index].avatarDrawable?.setInfo(currentChat)
				animatingStates[index].id = -currentChat!!.id
			}
		}

		if (currentUser != null) {
			animatingStates[index].imageReceiver?.setForUserOrChat(currentUser, animatingStates[index].avatarDrawable)
		}
		else {
			animatingStates[index].imageReceiver?.setForUserOrChat(currentChat, animatingStates[index].avatarDrawable)
		}

		val bigAvatars = currentStyle == 4 || currentStyle == STYLE_GROUP_CALL_TOOLTIP

		animatingStates[index].imageReceiver?.setRoundRadius(AndroidUtilities.dp((if (bigAvatars) 16 else 12).toFloat()))

		val size = size

		animatingStates[index].imageReceiver?.setImageCoordinates(0f, 0f, size.toFloat(), size.toFloat())

		invalidate()
	}

	fun onDraw(canvas: Canvas) {
		wasDraw = true

		val bigAvatars = currentStyle == 4 || currentStyle == STYLE_GROUP_CALL_TOOLTIP
		val size = size

		val toAdd = if (currentStyle == STYLE_MESSAGE_SEEN) {
			AndroidUtilities.dp(12f)
		}
		else if (overrideSize != 0) {
			(overrideSize * 0.8f).toInt()
		}
		else {
			AndroidUtilities.dp((if (bigAvatars) 24 else 20).toFloat())
		}

		var drawCount = 0

		for (i in 0..2) {
			if (currentStates[i].id != 0L) {
				drawCount++
			}
		}

		val startPadding = if ((currentStyle == 0 || currentStyle == STYLE_GROUP_CALL_TOOLTIP || currentStyle == STYLE_MESSAGE_SEEN)) 0 else AndroidUtilities.dp(10f)
		val ax = if (centered) (width - drawCount * toAdd - AndroidUtilities.dp((if (bigAvatars) 8 else 4).toFloat())) / 2 else startPadding
		val isMuted = VoIPService.sharedInstance?.isMicMute() == true

		if (currentStyle == 4) {
			paint.color = ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.background, null)
		}
		else if (currentStyle != 3) {
			paint.color = ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, if (isMuted) R.color.dark_gray else R.color.avatar_blue, null)
		}

		var animateToDrawCount = 0

		for (i in 0..2) {
			if (animatingStates[i].id != 0L) {
				animateToDrawCount++
			}
		}

		val useAlphaLayer = currentStyle == 0 || currentStyle == 1 || currentStyle == 3 || currentStyle == 4 || currentStyle == 5 || currentStyle == STYLE_GROUP_CALL_TOOLTIP || currentStyle == STYLE_MESSAGE_SEEN

		if (useAlphaLayer) {
			val padding = (if (currentStyle == STYLE_GROUP_CALL_TOOLTIP) AndroidUtilities.dp(16f) else 0).toFloat()
			canvas.saveLayerAlpha(-padding, -padding, width + padding, height + padding, 255)
		}

		for (a in 2 downTo 0) {
			for (k in 0..1) {
				if (k == 0 && transitionProgress == 1f) {
					continue
				}

				val states = if (k == 0) animatingStates else currentStates

				if (k == 1 && transitionProgress != 1f && states[a].animationType != DrawingState.ANIMATION_TYPE_OUT) {
					continue
				}

				val imageReceiver = states[a].imageReceiver

				if (!imageReceiver!!.hasImageSet()) {
					continue
				}

				if (k == 0) {
					val toAx = if (centered) (width - animateToDrawCount * toAdd - AndroidUtilities.dp((if (bigAvatars) 8 else 4).toFloat())) / 2 else startPadding
					imageReceiver.setImageX(toAx + toAdd * a)
				}
				else {
					imageReceiver.setImageX(ax + toAdd * a)
				}

				if (currentStyle == 0 || currentStyle == STYLE_GROUP_CALL_TOOLTIP || currentStyle == STYLE_MESSAGE_SEEN) {
					imageReceiver.imageY = (height - size) / 2f
				}
				else {
					imageReceiver.imageY = AndroidUtilities.dp((if (currentStyle == 4) 8 else 6).toFloat()).toFloat()
				}

				var needRestore = false
				var alpha = 1f

				if (transitionProgress != 1f) {
					if (states[a].animationType == DrawingState.ANIMATION_TYPE_OUT) {
						canvas.save()
						canvas.scale(1f - transitionProgress, 1f - transitionProgress, imageReceiver.centerX, imageReceiver.centerY)
						needRestore = true
						alpha = 1f - transitionProgress
					}
					else if (states[a].animationType == DrawingState.ANIMATION_TYPE_IN) {
						canvas.save()
						canvas.scale(transitionProgress, transitionProgress, imageReceiver.centerX, imageReceiver.centerY)
						alpha = transitionProgress
						needRestore = true
					}
					else if (states[a].animationType == DrawingState.ANIMATION_TYPE_MOVE) {
						val toAx = if (centered) (width - animateToDrawCount * toAdd - AndroidUtilities.dp((if (bigAvatars) 8 else 4).toFloat())) / 2 else startPadding
						val toX = toAx + toAdd * a
						val fromX = ax + toAdd * states[a].moveFromIndex
						imageReceiver.setImageX((toX * transitionProgress + fromX * (1f - transitionProgress)).toInt())
					}
					else if (states[a].animationType == DrawingState.ANIMATION_TYPE_NONE && centered) {
						val toAx = (width - animateToDrawCount * toAdd - AndroidUtilities.dp((if (bigAvatars) 8 else 4).toFloat())) / 2
						val toX = toAx + toAdd * a
						val fromX = ax + toAdd * a
						imageReceiver.setImageX((toX * transitionProgress + fromX * (1f - transitionProgress)).toInt())
					}
				}

				alpha *= overrideAlpha

				var avatarScale = 1f

				if (a != states.size - 1) {
					if (currentStyle == 1 || currentStyle == 3 || currentStyle == 5) {
						canvas.drawCircle(imageReceiver.centerX, imageReceiver.centerY, AndroidUtilities.dp(13f).toFloat(), xRefP)

						if (states[a].wavesDrawable == null) {
							if (currentStyle == 5) {
								states[a].wavesDrawable = AvatarWavesDrawable(AndroidUtilities.dp(14f), AndroidUtilities.dp(16f))
							}
							else {
								states[a].wavesDrawable = AvatarWavesDrawable(AndroidUtilities.dp(17f), AndroidUtilities.dp(21f))
							}
						}

						if (currentStyle == 5) {
							states[a].wavesDrawable?.setColor(ColorUtils.setAlphaComponent(ApplicationLoader.applicationContext.getColor(R.color.online), (255 * 0.3f * alpha).toInt()))
						}

						if (states[a].participant != null && states[a].participant!!.amplitude > 0) {
							states[a].wavesDrawable?.setShowWaves(true, parent)
							val amplitude = states[a].participant!!.amplitude * 15f
							states[a].wavesDrawable?.setAmplitude(amplitude.toDouble())
						}
						else {
							states[a].wavesDrawable?.setShowWaves(false, parent)
						}

						if (currentStyle == 5 && (SystemClock.uptimeMillis() - states[a].participant!!.lastSpeakTime) > 500) {
							updateDelegate?.run()
						}

						states[a].wavesDrawable?.update()

						if (currentStyle == 5) {
							states[a].wavesDrawable?.draw(canvas, imageReceiver.centerX, imageReceiver.centerY, parent)
							invalidate()
						}

						avatarScale = states[a].wavesDrawable!!.avatarScale
					}
					else if (currentStyle == 4 || currentStyle == STYLE_GROUP_CALL_TOOLTIP) {
						canvas.drawCircle(imageReceiver.centerX, imageReceiver.centerY, AndroidUtilities.dp(17f).toFloat(), xRefP)

						if (states[a].wavesDrawable == null) {
							states[a].wavesDrawable = AvatarWavesDrawable(AndroidUtilities.dp(17f), AndroidUtilities.dp(21f))
						}
						if (currentStyle == STYLE_GROUP_CALL_TOOLTIP) {
							states[a].wavesDrawable!!.setColor(ColorUtils.setAlphaComponent(ApplicationLoader.applicationContext.getColor(R.color.online), (255 * 0.3f * alpha).toInt()))
						}
						else {
							states[a].wavesDrawable!!.setColor(ColorUtils.setAlphaComponent(ApplicationLoader.applicationContext.getColor(R.color.avatar_light_blue), (255 * 0.3f * alpha).toInt()))
						}

						val currentTime = System.currentTimeMillis()

						if (currentTime - states[a].lastUpdateTime > 100) {
							states[a].lastUpdateTime = currentTime

							if (currentStyle == STYLE_GROUP_CALL_TOOLTIP) {
								if (states[a].participant != null && states[a].participant!!.amplitude > 0) {
									states[a].wavesDrawable!!.setShowWaves(true, parent)
									val amplitude = states[a].participant!!.amplitude * 15f
									states[a].wavesDrawable!!.setAmplitude(amplitude.toDouble())
								}
								else {
									states[a].wavesDrawable!!.setShowWaves(false, parent)
								}
							}
							else {
								if (ConnectionsManager.getInstance(UserConfig.selectedAccount).currentTime - states[a].lastSpeakTime <= 5) {
									states[a].wavesDrawable!!.setShowWaves(true, parent)
									states[a].wavesDrawable!!.setAmplitude((random.nextInt() % 100).toDouble())
								}
								else {
									states[a].wavesDrawable!!.setShowWaves(false, parent)
									states[a].wavesDrawable!!.setAmplitude(0.0)
								}
							}
						}

						states[a].wavesDrawable!!.update()
						states[a].wavesDrawable!!.draw(canvas, imageReceiver.centerX, imageReceiver.centerY, parent)

						avatarScale = states[a].wavesDrawable!!.avatarScale
					}
					else {
						val rad = this.size / 2f + AndroidUtilities.dp(2f)

						if (useAlphaLayer) {
							canvas.drawCircle(imageReceiver.centerX, imageReceiver.centerY, rad, xRefP)
						}
						else {
							val paintAlpha = paint.alpha

							if (alpha != 1f) {
								paint.alpha = (paintAlpha * alpha).toInt()
							}

							canvas.drawCircle(imageReceiver.centerX, imageReceiver.centerY, rad, paint)

							if (alpha != 1f) {
								paint.alpha = paintAlpha
							}
						}
					}
				}

				imageReceiver.alpha = alpha

				if (avatarScale != 1f) {
					canvas.save()
					canvas.scale(avatarScale, avatarScale, imageReceiver.centerX, imageReceiver.centerY)

					imageReceiver.draw(canvas)

					canvas.restore()
				}
				else {
					imageReceiver.draw(canvas)
				}

				if (needRestore) {
					canvas.restore()
				}
			}
		}

		if (useAlphaLayer) {
			canvas.restore()
		}
	}

	private var size: Int
		get() {
			if (overrideSize != 0) {
				return overrideSize
			}

			val bigAvatars = currentStyle == 4 || currentStyle == STYLE_GROUP_CALL_TOOLTIP

			return AndroidUtilities.dp((if (bigAvatars) 32 else 24).toFloat())
		}
		set(size) {
			overrideSize = size
		}

	fun onDetachedFromWindow() {
		wasDraw = false

		for (a in 0..2) {
			currentStates[a].imageReceiver?.onDetachedFromWindow()
			animatingStates[a].imageReceiver?.onDetachedFromWindow()
		}

		if (currentStyle == 3) {
			Theme.getFragmentContextViewWavesDrawable().setAmplitude(0f)
		}
	}

	fun onAttachedToWindow() {
		for (a in 0..2) {
			currentStates[a].imageReceiver?.onAttachedToWindow()
			animatingStates[a].imageReceiver?.onAttachedToWindow()
		}
	}

	fun setCentered(centered: Boolean) {
		this.centered = centered
	}

	fun setCount(count: Int) {
		this.count = count
		parent?.requestLayout()
	}

	fun reset() {
		for (i in animatingStates.indices) {
			setObject(0, 0, null)
		}
	}

	companion object {
		const val STYLE_GROUP_CALL_TOOLTIP: Int = 10
		const val STYLE_MESSAGE_SEEN: Int = 11
	}
}
