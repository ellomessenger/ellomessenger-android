/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components.Reactions

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet
import org.telegram.ui.Components.Reactions.ReactionsContainerLayout.ReactionHolderView
import org.telegram.ui.Components.Reactions.VisibleReaction.Companion.fromCustomEmoji
import org.telegram.ui.PremiumPreviewFragment
import org.telegram.ui.SelectAnimatedEmojiDialog
import kotlin.math.min

class CustomEmojiReactionsWindow(var baseFragment: BaseFragment, var reactions: List<VisibleReaction>, selectedReactions: HashSet<VisibleReaction>?, private val reactionsContainerLayout: ReactionsContainerLayout) {
	private var containerView: ContainerView
	private var dismissProgress = 0f
	private var dismissed = false
	private var drawingRect = RectF()
	private var enterTransitionFinished = false
	private var enterTransitionProgress = 0f
	private var fromRadius = 0f
	private var fromRect = RectF()
	private var invalidatePath = false
	private var keyboardHeight = 0f
	private var location = IntArray(2)
	private var onDismiss: Runnable? = null
	private var pathToClip = Path()
	private var wasFocused = false
	private val windowManager = baseFragment.parentActivity!!.windowManager
	private var windowView: FrameLayout
	private var yTranslation = 0f
	private var frameDrawCount = 0

	@JvmField
	var selectAnimatedEmojiDialog: SelectAnimatedEmojiDialog

	private fun updateWindowPosition() {
		if (dismissed) {
			return
		}

		var y = yTranslation

		if (y + containerView.measuredHeight > windowView.measuredHeight - keyboardHeight - AndroidUtilities.dp(32f)) {
			y = windowView.measuredHeight - keyboardHeight - containerView.measuredHeight - AndroidUtilities.dp(32f)
		}

		if (y < 0) {
			y = 0f
		}

		containerView.animate().translationY(y).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start()
	}

	private fun createLayoutParams(focusable: Boolean): WindowManager.LayoutParams {
		val lp = WindowManager.LayoutParams()
		lp.height = WindowManager.LayoutParams.MATCH_PARENT
		lp.width = lp.height
		lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
		lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

		if (focusable) {
			lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
		}
		else {
			lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
		}

		lp.format = PixelFormat.TRANSLUCENT

		return lp
	}

	private fun showUnlockPremiumAlert() {
		if (baseFragment is ChatActivity) {
			baseFragment.showDialog(PremiumFeatureBottomSheet(baseFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false))
		}
	}

	private fun createTransition(enter: Boolean) {
		fromRect.set(reactionsContainerLayout.rect)
		fromRadius = reactionsContainerLayout.radius

		val windowLocation = IntArray(2)

		if (enter) {
			reactionsContainerLayout.getLocationOnScreen(location)
		}

		windowView.getLocationOnScreen(windowLocation)

		var y = (location[1] - windowLocation[1] - AndroidUtilities.dp(44f) - AndroidUtilities.dp(34f)).toFloat()

		if (y + containerView.measuredHeight > windowView.measuredHeight - AndroidUtilities.dp(32f)) {
			y = (windowView.measuredHeight - AndroidUtilities.dp(32f) - containerView.measuredHeight).toFloat()
		}

		if (y < AndroidUtilities.dp(16f)) {
			y = AndroidUtilities.dp(16f).toFloat()
		}

		containerView.translationX = (location[0] - windowLocation[0] - AndroidUtilities.dp(2f)).toFloat()

		if (!enter) {
			yTranslation = containerView.translationY
		}
		else {
			yTranslation = y
			containerView.translationY = yTranslation
		}

		fromRect.offset(location[0] - windowLocation[0] - containerView.x, location[1] - windowLocation[1] - containerView.y)

		reactionsContainerLayout.setCustomEmojiEnterProgress(enterTransitionProgress)

		if (enter) {
			enterTransitionFinished = false
		}

		val valueAnimator = ValueAnimator.ofFloat(enterTransitionProgress, if (enter) 1f else 0f)

		valueAnimator.addUpdateListener {
			enterTransitionProgress = it.animatedValue as Float
			reactionsContainerLayout.setCustomEmojiEnterProgress(enterTransitionProgress)
			invalidatePath = true
			containerView.invalidate()
		}

		valueAnimator.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				enterTransitionProgress = if (enter) 1f else 0f

				if (enter) {
					enterTransitionFinished = true
					selectAnimatedEmojiDialog.resetBackgroundBitmaps()
					reactionsContainerLayout.onCustomEmojiWindowOpened()
					containerView.invalidate()
				}

				reactionsContainerLayout.setCustomEmojiEnterProgress(enterTransitionProgress)

				if (!enter) {
					reactionsContainerLayout.setSkipDraw(false)
				}

				if (!enter) {
					removeView()
				}
			}
		})

		valueAnimator.startDelay = 30
		valueAnimator.duration = 350
		valueAnimator.interpolator = CubicBezierInterpolator.DEFAULT
		valueAnimator.start()
	}

	fun removeView() {
		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.startAllHeavyOperations, 7)

		AndroidUtilities.runOnUIThread {
			if (windowView.parent == null) {
				return@runOnUIThread
			}

			runCatching {
				windowManager.removeView(windowView)
			}

			onDismiss?.run()
		}
	}

	private fun dismiss() {
		if (dismissed) {
			return
		}

		Bulletin.hideVisible()

		dismissed = true

		AndroidUtilities.hideKeyboard(windowView)

		createTransition(false)

		if (wasFocused) {
			(baseFragment as? ChatActivity)?.onEditTextDialogClose(resetAdjust = true, reset = true)
		}
	}

	fun onDismissListener(onDismiss: Runnable?) {
		this.onDismiss = onDismiss
	}

	fun dismiss(animated: Boolean) {
		if (dismissed && animated) {
			return
		}

		dismissed = true

		if (!animated) {
			removeView()
		}
		else {
			val valueAnimator = ValueAnimator.ofFloat(0f, 1f)

			valueAnimator.addUpdateListener {
				dismissProgress = it.animatedValue as Float
				containerView.alpha = 1f - dismissProgress
			}

			valueAnimator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					removeView()
				}
			})

			valueAnimator.duration = 150
			valueAnimator.start()
		}
	}

	init {
		val context = baseFragment.context!!

		windowView = object : FrameLayout(context) {
			override fun dispatchKeyEvent(event: KeyEvent): Boolean {
				if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK) {
					dismiss()
					return true
				}

				return super.dispatchKeyEvent(event)
			}

			override fun dispatchSetPressed(pressed: Boolean) {
				// unused
			}

			@Deprecated("Deprecated in Java")
			override fun fitSystemWindows(insets: Rect): Boolean {
				if (keyboardHeight != insets.bottom.toFloat() && wasFocused) {
					keyboardHeight = insets.bottom.toFloat()
					updateWindowPosition()
				}

				return super.fitSystemWindows(insets)
			}
		}

		windowView.setOnClickListener {
			dismiss()
		}

		// sizeNotifierFrameLayout.setFitsSystemWindows(true);

		containerView = ContainerView(context)

		selectAnimatedEmojiDialog = object : SelectAnimatedEmojiDialog(baseFragment, context, false, null, TYPE_REACTIONS) {
			override fun onInputFocus() {
				if (!wasFocused) {
					wasFocused = true
					windowManager.updateViewLayout(windowView, createLayoutParams(true))
					if (baseFragment is ChatActivity) {
						(baseFragment as ChatActivity).needEnterText()
					}
				}
			}

			override fun onReactionClick(emoji: ImageViewEmoji?, reaction: VisibleReaction?) {
				reactionsContainerLayout.onReactionClicked(emoji, reaction, false)
				AndroidUtilities.hideKeyboard(windowView)
			}

			override fun onEmojiSelected(view: View, documentId: Long?, document: TLRPC.Document?, until: Int?) {
				if (!UserConfig.getInstance(baseFragment.currentAccount).isPremium) {
					windowView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
					BulletinFactory.of(windowView).createEmojiBulletin(document, AndroidUtilities.replaceTags(context.getString(R.string.UnlockPremiumEmojiReaction)), context.getString(R.string.PremiumMore)) { showUnlockPremiumAlert() }.show()
					return
				}

				reactionsContainerLayout.onReactionClicked(view, fromCustomEmoji(documentId ?: -1), false)

				AndroidUtilities.hideKeyboard(windowView)
			}
		}

		selectAnimatedEmojiDialog.setOnLongPressedListener { view ->
			if (view.isDefaultReaction) {
				reactionsContainerLayout.onReactionClicked(view, view.reaction, true)
			}
			else {
				reactionsContainerLayout.onReactionClicked(view, fromCustomEmoji(view.span?.documentId ?: -1), true)
			}
		}

		selectAnimatedEmojiDialog.setOnRecentClearedListener { reactionsContainerLayout.clearRecentReactions() }
		selectAnimatedEmojiDialog.setRecentReactions(reactions)
		selectAnimatedEmojiDialog.setSelectedReactions(selectedReactions)
		selectAnimatedEmojiDialog.setDrawBackground(false)
		selectAnimatedEmojiDialog.onShow(null)

		containerView.addView(selectAnimatedEmojiDialog, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 0f, 0f, 0f, 0f))
		windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP, 16f, 16f, 16f, 16f))
		windowView.clipChildren = false

		val lp = createLayoutParams(false)

		windowManager.addView(windowView, lp)

		reactionsContainerLayout.prepareAnimation(true)

		containerView.addOnLayoutChangeListener(object : OnLayoutChangeListener {
			override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
				containerView.removeOnLayoutChangeListener(this)
				reactionsContainerLayout.prepareAnimation(false)
				createTransition(true)
			}
		})

		NotificationCenter.globalInstance.postNotificationName(NotificationCenter.stopAllHeavyOperations, 7)
	}

	inner class ContainerView(context: Context) : FrameLayout(context) {
		private val shadow: Drawable
		private var shadowPad = Rect()
		private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val radiusTmp = IntArray(4)

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			var size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
			val measuredSize = AndroidUtilities.dp(36f) * 8 + AndroidUtilities.dp(12f)

			if (measuredSize < size) {
				size = measuredSize
			}

			val height = size
			//            if (height * 1.2 < MeasureSpec.getSize(heightMeasureSpec)) {
//                height *= 1.2;
//            }

			super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
		}

		private val transitionReactions = mutableMapOf<VisibleReaction, SelectAnimatedEmojiDialog.ImageViewEmoji>()

		init {
			shadow = ContextCompat.getDrawable(context, R.drawable.reactions_bubble_shadow)!!.mutate()
			shadowPad.bottom = AndroidUtilities.dp(7f)
			shadowPad.right = shadowPad.bottom
			shadowPad.top = shadowPad.right
			shadowPad.left = shadowPad.top
			shadow.colorFilter = PorterDuffColorFilter(context.getColor(R.color.shadow), PorterDuff.Mode.MULTIPLY)
			backgroundPaint.color = context.getColor(R.color.background)
		}

		override fun dispatchDraw(canvas: Canvas) {
			dimPaint.alpha = (0.2f * enterTransitionProgress * 255).toInt()

			canvas.drawPaint(dimPaint)

			AndroidUtilities.rectTmp.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
			AndroidUtilities.lerp(fromRect, AndroidUtilities.rectTmp, enterTransitionProgress, drawingRect)

			val radius = AndroidUtilities.lerp(fromRadius, AndroidUtilities.dp(8f).toFloat(), enterTransitionProgress)

			shadow.alpha = (Utilities.clamp(enterTransitionProgress / 0.05f, 1f, 0f) * 255).toInt()
			shadow.setBounds(drawingRect.left.toInt() - shadowPad.left, drawingRect.top.toInt() - shadowPad.top, drawingRect.right.toInt() + shadowPad.right, drawingRect.bottom.toInt() + shadowPad.bottom)
			shadow.draw(canvas)

			transitionReactions.clear()

			canvas.drawRoundRect(drawingRect, radius, radius, backgroundPaint)

			val rightDelta = drawingRect.left - reactionsContainerLayout.rect.left + (drawingRect.width() - reactionsContainerLayout.rect.width())

			if (enterTransitionProgress > 0.05f) {
				canvas.save()
				canvas.translate(rightDelta, drawingRect.top - reactionsContainerLayout.rect.top + (drawingRect.height() - reactionsContainerLayout.rect.height()))
				reactionsContainerLayout.drawBubbles(canvas)
				canvas.restore()
			}

			var enterTransitionOffsetX = 0f
			var enterTransitionOffsetY = 0f
			var enterTransitionScale = 1f
			var enterTransitionScalePx = 0f
			var enterTransitionScalePy = 0f

			if (enterTransitionProgress != 1f) {
				for (i in 0 until (selectAnimatedEmojiDialog.emojiGridView?.childCount ?: 0)) {
					if (selectAnimatedEmojiDialog.emojiGridView?.getChildAt(i) is SelectAnimatedEmojiDialog.ImageViewEmoji) {
						val imageViewEmoji = selectAnimatedEmojiDialog.emojiGridView?.getChildAt(i) as? SelectAnimatedEmojiDialog.ImageViewEmoji

						if (imageViewEmoji?.reaction != null) {
							transitionReactions[imageViewEmoji.reaction!!] = imageViewEmoji

							imageViewEmoji.notDraw = false
							imageViewEmoji.invalidate()
						}
					}
				}

				canvas.save()
				canvas.translate(drawingRect.left, drawingRect.top + reactionsContainerLayout.expandSize() * (1f - enterTransitionProgress))

				for (i in -1 until reactionsContainerLayout.recyclerListView.childCount) {
					val child = if (i == -1) {
						reactionsContainerLayout.nextRecentReaction
					}
					else {
						reactionsContainerLayout.recyclerListView.getChildAt(i)
					}

					if (child.left < 0 || child.visibility == GONE) {
						continue
					}

					canvas.save()

					if (child is ReactionHolderView) {
						val toImageView = transitionReactions[child.currentReaction]
						var fromRoundRadiusLt = 0f
						var fromRoundRadiusRt = 0f
						var fromRoundRadiusLb = 0f
						var fromRoundRadiusRb = 0f
						val toRoundRadiusLt = 0f
						val toRoundRadiusRt = 0f
						val toRoundRadiusLb = 0f
						val toRoundRadiusRb = 0f
						var scale = 1f

						if (toImageView != null) {
							var fromX = child.getX() + child.loopImageView.x
							var fromY = child.getY() + child.loopImageView.y

							if (i == -1) {
								fromX -= reactionsContainerLayout.recyclerListView.x
								fromY -= reactionsContainerLayout.recyclerListView.y
							}

							var toX = toImageView.x + selectAnimatedEmojiDialog.x + (selectAnimatedEmojiDialog.emojiGridView?.x ?: 0f)
							var toY = toImageView.y + selectAnimatedEmojiDialog.y + selectAnimatedEmojiDialog.gridViewContainer.y + (selectAnimatedEmojiDialog.emojiGridView?.y ?: 0f)
							var toImageViewSize = toImageView.measuredWidth.toFloat()

							if (toImageView.getViewSelected()) {
								val sizeAfterScale = toImageViewSize * (0.8f + 0.2f * 0.3f)
								toX += (toImageViewSize - sizeAfterScale) / 2f
								toY += (toImageViewSize - sizeAfterScale) / 2f
								toImageViewSize = sizeAfterScale
							}

							val dX = AndroidUtilities.lerp(fromX, toX, enterTransitionProgress)
							val dY = AndroidUtilities.lerp(fromY, toY, enterTransitionProgress)
							val toScale = toImageViewSize / child.loopImageView.measuredWidth.toFloat()

							scale = AndroidUtilities.lerp(1f, toScale, enterTransitionProgress)

							if (child.position == 0) {
								fromRoundRadiusLt = AndroidUtilities.dp(6f).toFloat()
								fromRoundRadiusLb = fromRoundRadiusLt
								fromRoundRadiusRt = 0f
								fromRoundRadiusRb = fromRoundRadiusRt
							}
							else if (child.reactionSelected) {
								fromRoundRadiusLt = AndroidUtilities.dp(6f).toFloat()
								fromRoundRadiusRt = fromRoundRadiusLt
								fromRoundRadiusLb = fromRoundRadiusRt
								fromRoundRadiusRb = fromRoundRadiusLb
							}

							canvas.translate(dX, dY)
							canvas.scale(scale, scale)

							if (enterTransitionOffsetX == 0f && enterTransitionOffsetY == 0f) {
								enterTransitionOffsetX = AndroidUtilities.lerp(fromRect.left + fromX - toX, 0f, enterTransitionProgress)
								enterTransitionOffsetY = AndroidUtilities.lerp(fromRect.top + fromY - toY, 0f, enterTransitionProgress)
								enterTransitionScale = AndroidUtilities.lerp(1f / toScale, 1f, enterTransitionProgress)
								enterTransitionScalePx = toX
								enterTransitionScalePy = toY
							}
						}
						else {
							canvas.translate(child.getX() + child.loopImageView.x, child.getY() + child.loopImageView.y)
						}

						if (child.loopImageView.visibility == VISIBLE && toImageView != null && imageIsEquals(child.loopImageView, toImageView)) {
							if (toImageView.getViewSelected()) {
								val cx = child.loopImageView.measuredWidth / 2f
								val cy = child.loopImageView.measuredHeight / 2f
								val fromSize = (child.measuredWidth - AndroidUtilities.dp(2f)).toFloat()
								val toSize = (toImageView.measuredWidth - AndroidUtilities.dp(2f)).toFloat()
								val finalSize = AndroidUtilities.lerp(fromSize, toSize / scale, enterTransitionProgress)

								AndroidUtilities.rectTmp.set(cx - finalSize / 2f, cy - finalSize / 2f, cx + finalSize / 2f, cy + finalSize / 2f)

								val rectRadius = AndroidUtilities.lerp(fromSize / 2f, AndroidUtilities.dp(4f).toFloat(), enterTransitionProgress)

								canvas.drawRoundRect(AndroidUtilities.rectTmp, rectRadius, rectRadius, selectAnimatedEmojiDialog.selectorPaint)
							}

							if (fromRoundRadiusLb != 0f || toRoundRadiusLb != 0f) {
								var imageReceiver = child.loopImageView.imageReceiver

								if (child.loopImageView.animatedEmojiDrawable != null && child.loopImageView.animatedEmojiDrawable!!.imageReceiver != null) {
									imageReceiver = child.loopImageView.animatedEmojiDrawable!!.imageReceiver
								}

								val oldRadius = imageReceiver.getRoundRadius()

								for (k in 0..3) {
									radiusTmp[k] = oldRadius[k]
								}

								imageReceiver.setRoundRadius(AndroidUtilities.lerp(fromRoundRadiusLt, toRoundRadiusLt, enterTransitionProgress).toInt(), AndroidUtilities.lerp(fromRoundRadiusRt, toRoundRadiusRt, enterTransitionProgress).toInt(), AndroidUtilities.lerp(fromRoundRadiusRb, toRoundRadiusRb, enterTransitionProgress).toInt(), AndroidUtilities.lerp(fromRoundRadiusLb, toRoundRadiusLb, enterTransitionProgress).toInt())

								child.loopImageView.draw(canvas)
								child.loopImageView.draw(canvas)

								imageReceiver.setRoundRadius(radiusTmp)
							}
							else {
								child.loopImageView.draw(canvas)
							}

							if (!toImageView.notDraw) {
								toImageView.notDraw = true
								toImageView.invalidate()
							}
						}
						else {
							if (child.hasEnterAnimation) {
								val oldAlpha = child.enterImageView.imageReceiver.alpha
								child.enterImageView.imageReceiver.alpha = oldAlpha * (1f - enterTransitionProgress)
								child.enterImageView.draw(canvas)
								child.enterImageView.imageReceiver.alpha = oldAlpha
							}
							else {
								var imageReceiver = child.loopImageView.imageReceiver

								if (child.loopImageView.animatedEmojiDrawable != null && child.loopImageView.animatedEmojiDrawable!!.imageReceiver != null) {
									imageReceiver = child.loopImageView.animatedEmojiDrawable!!.imageReceiver
								}

								val oldAlpha = imageReceiver.alpha
								imageReceiver.alpha = oldAlpha * (1f - enterTransitionProgress)
								child.loopImageView.draw(canvas)
								imageReceiver.alpha = oldAlpha
							}
						}
					}
					else {
						canvas.translate(child.x + drawingRect.width() - reactionsContainerLayout.rect.width(), child.y + fromRect.top - drawingRect.top)
						canvas.saveLayerAlpha(0f, 0f, child.measuredWidth.toFloat(), child.measuredHeight.toFloat(), (255 * (1f - enterTransitionProgress)).toInt())
						canvas.scale(1f - enterTransitionProgress, 1f - enterTransitionProgress, (child.measuredWidth shr 1).toFloat(), (child.measuredHeight shr 1).toFloat())
						child.draw(canvas)
						canvas.restore()
					}

					canvas.restore()
				}

				canvas.restore()
			}

			if (invalidatePath) {
				invalidatePath = false
				pathToClip.rewind()
				pathToClip.addRoundRect(drawingRect, radius, radius, Path.Direction.CW)
			}

			canvas.save()
			canvas.clipPath(pathToClip)
			canvas.translate(enterTransitionOffsetX, enterTransitionOffsetY)
			canvas.scale(enterTransitionScale, enterTransitionScale, enterTransitionScalePx, enterTransitionScalePy)

			selectAnimatedEmojiDialog.alpha = enterTransitionProgress

			super.dispatchDraw(canvas)

			canvas.restore()

			if (frameDrawCount < 5) {
				if (frameDrawCount == 3) {
					reactionsContainerLayout.setSkipDraw(true)
				}

				frameDrawCount++
			}

			selectAnimatedEmojiDialog.drawBigReaction(canvas, this)

			invalidate()
		}
	}

	private fun imageIsEquals(loopImageView: BackupImageView, toImageView: SelectAnimatedEmojiDialog.ImageViewEmoji): Boolean {
		if (toImageView.span == null) {
			return toImageView.imageReceiver?.lottieAnimation === loopImageView.imageReceiver.lottieAnimation
		}

		return if (loopImageView.animatedEmojiDrawable != null) {
			toImageView.span?.getDocumentId() == loopImageView.animatedEmojiDrawable?.documentId
		}
		else {
			false
		}
	}

	fun setRecentReactions(reactions: List<VisibleReaction>?) {
		selectAnimatedEmojiDialog.setRecentReactions(reactions)
	}
}
