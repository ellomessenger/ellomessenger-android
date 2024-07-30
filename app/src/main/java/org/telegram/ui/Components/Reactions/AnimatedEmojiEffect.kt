/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components.Reactions

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC.TL_messages_stickerSet
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.RLottieDrawable
import kotlin.math.abs

class AnimatedEmojiEffect private constructor(@JvmField var animatedEmojiDrawable: AnimatedEmojiDrawable, var currentAccount: Int, var longAnimation: Boolean, private var showGeneric: Boolean) {
	private var bounds = Rect()
	private var particles = ArrayList<Particle>()
	private var parentView: View? = null
	private var startTime = System.currentTimeMillis()
	private var firsDraw = true
	private var effectImageReceiver: ImageReceiver? = null
	private var animationIndex = -1

	fun setBounds(l: Int, t: Int, r: Int, b: Int) {
		bounds.set(l, t, r, b)
		effectImageReceiver?.setImageCoordinates(bounds)
	}

	private var lastGenerateTime: Long = 0

	init {
		if (!longAnimation && showGeneric) {
			effectImageReceiver = ImageReceiver()
		}
	}

	fun draw(canvas: Canvas) {
		if (!longAnimation) {
			if (firsDraw) {
				for (i in 0..6) {
					val particle = Particle()
					particle.generate()
					particles.add(particle)
				}
			}
		}
		else {
			val currentTime = System.currentTimeMillis()

			if (particles.size < 12 && currentTime - startTime < 1500 && currentTime - startTime > 200) {
				if (currentTime - lastGenerateTime > 50 && Utilities.fastRandom.nextInt() % 6 == 0) {
					val particle = Particle()
					particle.generate()
					particles.add(particle)
					lastGenerateTime = currentTime
				}
			}
		}

		if (showGeneric) {
			effectImageReceiver?.draw(canvas)
		}

		var i = 0

		while (i < particles.size) {
			particles[i].draw(canvas)

			if (particles[i].progress >= 1f) {
				particles.removeAt(i)
				i--
			}

			i++
		}

		parentView?.invalidate()

		firsDraw = false
	}

	fun done(): Boolean {
		return System.currentTimeMillis() - startTime > 2500
	}

	fun setView(view: View?) {
		animatedEmojiDrawable.addView(view)
		parentView = view

		if (effectImageReceiver != null && showGeneric) {
			effectImageReceiver?.onAttachedToWindow()

			val document = animatedEmojiDrawable.document
			val emojicon = MessageObject.findAnimatedEmojiEmoticon(document, null)
			var imageSet = false

			if (emojicon != null) {
				val reaction = MediaDataController.getInstance(currentAccount).reactionsMap[emojicon]

				if (reaction?.around_animation != null) {
					effectImageReceiver?.setImage(ImageLocation.getForDocument(reaction.around_animation), ReactionsEffectOverlay.filterForAroundAnimation, null, null, reaction.around_animation, 0)
					imageSet = true
				}
			}

			if (!imageSet) {
				val packName = UserConfig.getInstance(currentAccount).genericAnimationsStickerPack
				var set: TL_messages_stickerSet? = null

				if (packName != null) {
					set = MediaDataController.getInstance(currentAccount).getStickerSetByName(packName)

					if (set == null) {
						set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(packName)
					}
				}

				if (set != null) {
					imageSet = true

					if (animationIndex < 0) {
						animationIndex = abs(Utilities.fastRandom.nextInt() % set.documents.size)
					}

					effectImageReceiver?.setImage(ImageLocation.getForDocument(set.documents[animationIndex]), "60_60", null, null, set.documents[animationIndex], 0)
				}
			}

			if (imageSet) {
				effectImageReceiver?.lottieAnimation?.setCurrentFrame(0, false, true)
				effectImageReceiver?.setAutoRepeat(0)
			}
			else {
				val rLottieDrawable = RLottieDrawable(R.raw.custom_emoji_reaction, "" + R.raw.custom_emoji_reaction, AndroidUtilities.dp(60f), AndroidUtilities.dp(60f), false, null)
				effectImageReceiver?.setImageBitmap(rLottieDrawable)
			}
		}
	}

	fun removeView(view: View?) {
		animatedEmojiDrawable.removeView(view)
		effectImageReceiver?.onDetachedFromWindow()
		effectImageReceiver?.clearImage()
	}

	inner class Particle {
		private var fromSize = 0f
		private var randomRotation = 0f
		private var toSize = 0f
		private var toY1 = 0f
		var duration: Long = 0
		var fromX = 0f
		var fromY = 0f
		var mirror = false
		var progress = 0f
		var toX = 0f
		var toY2 = 0f

		fun generate() {
			progress = 0f

			var bestDistance = 0f
			var bestX = randX()
			var bestY = randY()

			for (k in 0..19) {
				val randX = randX()
				val randY = randY()
				var minDistance = Int.MAX_VALUE.toFloat()

				for (j in particles.indices) {
					val rx = particles[j].toX - randX
					val ry = particles[j].toY1 - randY
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

			val pivotX = if (longAnimation) 0.8f else 0.5f

			toX = bestX

			if (toX > bounds.width() * pivotX) {
				fromX = bounds.width() * pivotX // + bounds.width() * 0.1f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);
			}
			else {
				fromX = bounds.width() * pivotX // - bounds.width() * 0.3f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);

				if (toX > fromX) {
					toX = fromX - 0.1f
				}
			}

			fromY = bounds.height() * 0.45f + bounds.height() * 0.1f * (abs(Utilities.fastRandom.nextInt() % 100) / 100f)

			if (longAnimation) {
				fromSize = bounds.width() * 0.05f + bounds.width() * 0.1f * (abs(Utilities.fastRandom.nextInt() % 100) / 100f)
				toSize = fromSize * (1.5f + 1.5f * (abs(Utilities.fastRandom.nextInt() % 100) / 100f))
				toY1 = fromSize / 2f + bounds.height() * 0.1f * (abs(Utilities.fastRandom.nextInt() % 100) / 100f)
				toY2 = bounds.height() + fromSize
				duration = (1000 + abs(Utilities.fastRandom.nextInt() % 600)).toLong()
			}
			else {
				fromSize = bounds.width() * 0.05f + bounds.width() * 0.1f * (abs(Utilities.fastRandom.nextInt() % 100) / 100f)
				toSize = fromSize * (1.5f + 0.5f * (abs(Utilities.fastRandom.nextInt() % 100) / 100f))
				toY1 = bestY
				toY2 = toY1 + bounds.height()
				duration = 1800
			}

			mirror = Utilities.fastRandom.nextBoolean()

			randomRotation = 20 * (Utilities.fastRandom.nextInt() % 100 / 100f)
		}

		private fun randY(): Float {
			return bounds.height() * 0.5f * (abs(Utilities.fastRandom.nextInt() % 100) / 100f)
		}

//		private fun randDuration(): Long {
//			return (1000 + abs(Utilities.fastRandom.nextInt() % 900)).toLong()
//		}

		private fun randX(): Float {
			return if (longAnimation) {
				bounds.width() * -0.25f + bounds.width() * 1.5f * (abs(Utilities.fastRandom.nextInt() % 100) / 100f)
			}
			else {
				bounds.width() * (abs(Utilities.fastRandom.nextInt() % 100) / 100f)
			}
		}

		fun draw(canvas: Canvas) {
			progress += 16f / duration
			progress = Utilities.clamp(progress, 1f, 0f)

			val progressInternal = CubicBezierInterpolator.EASE_OUT.getInterpolation(progress)
			val cx = AndroidUtilities.lerp(fromX, toX, progressInternal)
			val cy: Float
			val k = if (longAnimation) 0.3f else 0.3f
			val k1 = 1f - k

			cy = if (progress < k) {
				AndroidUtilities.lerp(fromY, toY1, CubicBezierInterpolator.EASE_OUT.getInterpolation(progress / k))
			}
			else {
				AndroidUtilities.lerp(toY1, toY2, CubicBezierInterpolator.EASE_IN.getInterpolation((progress - k) / k1))
			}

			val size = AndroidUtilities.lerp(fromSize, toSize, progressInternal)
			var outAlpha = 1f

			if (!longAnimation) {
				val bottomBound = bounds.height() * 0.8f

				if (cy > bottomBound) {
					outAlpha = 1f - Utilities.clamp((cy - bottomBound) / AndroidUtilities.dp(16f), 1f, 0f)
				}
			}

			val sizeHalf = size / 2f * outAlpha

			canvas.save()

			if (mirror) {
				canvas.scale(-1f, 1f, cx, cy)
			}

			canvas.rotate(randomRotation, cx, cy)

			animatedEmojiDrawable.alpha = (255 * outAlpha * Utilities.clamp(progress / 0.2f, 1f, 0f)).toInt()
			animatedEmojiDrawable.setBounds((cx - sizeHalf).toInt(), (cy - sizeHalf).toInt(), (cx + sizeHalf).toInt(), (cy + sizeHalf).toInt())
			animatedEmojiDrawable.draw(canvas)
			animatedEmojiDrawable.alpha = 255

			canvas.restore()
		}
	}

	companion object {
		@JvmStatic
		fun createFrom(animatedEmojiDrawable: AnimatedEmojiDrawable, longAnimation: Boolean, showGeneric: Boolean): AnimatedEmojiEffect {
			return AnimatedEmojiEffect(animatedEmojiDrawable, UserConfig.selectedAccount, longAnimation, showGeneric)
		}
	}
}
