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
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.Property
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.util.Consumer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup
import org.telegram.messenger.DocumentObject.getSvgThumb
import org.telegram.messenger.Emoji
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC.ChatFull
import org.telegram.tgnet.TLRPC.TL_chatReactionsAll
import org.telegram.tgnet.TLRPC.TL_chatReactionsNone
import org.telegram.tgnet.TLRPC.TL_chatReactionsSome
import org.telegram.tgnet.tlrpc.TL_availableReaction
import org.telegram.tgnet.tlrpc.TL_reactionCustomEmoji
import org.telegram.tgnet.tlrpc.TL_reactionEmoji
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.ChatScrimPopupContainerLayout
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet
import org.telegram.ui.Components.Premium.PremiumLockIconView
import org.telegram.ui.Components.Reactions.VisibleReaction.Companion.fromEmojicon
import org.telegram.ui.Components.Reactions.VisibleReaction.Companion.fromTLReaction
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.PremiumPreviewFragment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ReactionsContainerLayout(private val fragment: BaseFragment, context: Context, private val currentAccount: Int) : FrameLayout(context), NotificationCenterDelegate {
	private val allReactionsList: MutableList<VisibleReaction> = ArrayList(20)
	private val animationEnabled: Boolean
	private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val bigCircleRadius = AndroidUtilities.dp(8f).toFloat()
	private val leftShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val linearLayoutManager: LinearLayoutManager
	private val location = IntArray(2)
	private val mPath = Path()
	private val premiumLockedReactions: MutableList<TL_availableReaction> = ArrayList(10)
	private val rightShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val shadow: Drawable
	private val shadowPad = Rect()
	private val smallCircleRadius = bigCircleRadius / 2
	private val visibleReactionsList: MutableList<VisibleReaction> = ArrayList(20)
	private var allReactionsAvailable = false
	private var allReactionsIsDefault = false
	private var cancelPressedProgress = 0f
	private var chatScrimPopupContainerLayout: ChatScrimPopupContainerLayout? = null
	private var clicked = false
	private var customEmojiReactionsEnterProgress = 0f
	private var customEmojiReactionsIconView: InternalImageView? = null
	private var delegate: ReactionsContainerDelegate? = null
	private var lastVisibleViews = HashSet<View>()
	private var lastVisibleViewsTmp = HashSet<View>()
	private var leftAlpha = 0f
	private var listAdapter: RecyclerView.Adapter<*>? = null
	private var messageObject: MessageObject? = null
	private var otherViewsScale = 0f
	private var premiumLockIconView: PremiumLockIconView? = null
	private var prepareAnimation = false
	private var pressedProgress = 0f
	private var pressedReaction: VisibleReaction? = null
	private var pressedReactionPosition = 0
	private var pressedViewScale = 0f
	private var reactionsWindow: CustomEmojiReactionsWindow? = null
	private var rightAlpha = 0f
	private var skipDraw = false
	private var transitionProgress = 1f
	private var waitingLoadingChatId: Long = 0
	val nextRecentReaction = ReactionHolderView(context)
	val recyclerListView: RecyclerListView
	var cancelPressedAnimation: ValueAnimator? = null
	var customReactionsContainer: FrameLayout? = null
	var lastReactionSentTime: Long = 0
	var premiumLockContainer: FrameLayout? = null
	var pullingDownBackAnimator: ValueAnimator? = null
	var pullingLeftOffset = 0f
	var radius = AndroidUtilities.dp(72f).toFloat()
	var rect = RectF()
	var selectedReactions = HashSet<VisibleReaction>()

	@JvmField
	var bigCircleOffset = AndroidUtilities.dp(36f)

	private fun animatePullingBack() {
		if (pullingLeftOffset != 0f) {
			pullingDownBackAnimator = ValueAnimator.ofFloat(pullingLeftOffset, 0f)

			pullingDownBackAnimator?.addUpdateListener {
				pullingLeftOffset = it.animatedValue as Float
				customReactionsContainer?.invalidate()
				invalidate()
			}

			pullingDownBackAnimator?.duration = 150
			pullingDownBackAnimator?.start()
		}
	}

	private fun showCustomEmojiReactionDialog() {
		if (reactionsWindow != null) {
			return
		}

		reactionsWindow = CustomEmojiReactionsWindow(fragment, allReactionsList, selectedReactions, this)

		for (i in 0 until recyclerListView.childCount) {
			val child = recyclerListView.getChildAt(i)

			if (child is ReactionHolderView) {
				if (child.loopImageView.imageReceiver.lottieAnimation != null) {
					child.loopImageView.imageReceiver.moveLottieToFront()
				}

				child.loopImageView.animatedEmojiDrawable?.let {
					reactionsWindow?.selectAnimatedEmojiDialog?.putAnimatedEmojiToCache(it)
				}
			}
		}

		if (nextRecentReaction.visibility == VISIBLE) {
			nextRecentReaction.loopImageView.imageReceiver.moveLottieToFront()
		}

		reactionsWindow?.onDismissListener {
			reactionsWindow = null
		}

		// animatePullingBack();
	}

	fun showCustomEmojiReaction(): Boolean {
		return !MessagesController.getInstance(currentAccount).premiumLocked && allReactionsAvailable
	}

	private fun showUnlockPremiumButton(): Boolean {
		return premiumLockedReactions.isNotEmpty() && !MessagesController.getInstance(currentAccount).premiumLocked
	}

	private fun showUnlockPremium() {
		val bottomSheet = PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_REACTIONS, true)
		bottomSheet.show()
	}

	private fun setChildScale(child: View, scale: Float) {
		if (child is ReactionHolderView) {
			child.sideScale = scale
		}
		else {
			child.scaleX = scale
			child.scaleY = scale
		}
	}

	fun setDelegate(delegate: ReactionsContainerDelegate?) {
		this.delegate = delegate
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun setVisibleReactionsList(visibleReactionsList: List<VisibleReaction>) {
		this.visibleReactionsList.clear()

		if (showCustomEmojiReaction()) {
			var i = 0
			var n = (AndroidUtilities.displaySize.x - AndroidUtilities.dp(36f)) / AndroidUtilities.dp(34f)

			if (n > 7) {
				n = 7
			}

			if (n < 1) {
				n = 1
			}

			while (i < min(visibleReactionsList.size, n)) {
				this.visibleReactionsList.add(visibleReactionsList[i])
				i++
			}

			if (i < visibleReactionsList.size) {
				nextRecentReaction.setReaction(visibleReactionsList[i], -1)
			}
		}
		else {
			this.visibleReactionsList.addAll(visibleReactionsList)
		}

		allReactionsIsDefault = true

		this.visibleReactionsList.forEach {
			if (it.documentId != 0L) {
				allReactionsIsDefault = false
			}
		}

		allReactionsList.clear()
		allReactionsList.addAll(visibleReactionsList)

		// checkPremiumReactions(this.visibleReactionsList);

		val size = layoutParams.height - paddingTop - paddingBottom

		if (size * visibleReactionsList.size < AndroidUtilities.dp(200f)) {
			layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
		}

		listAdapter?.notifyDataSetChanged()
	}

	override fun dispatchDraw(canvas: Canvas) {
		val cPr = (max(CLIP_PROGRESS, min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS)
		val br = bigCircleRadius * cPr
		val sr = smallCircleRadius * cPr

		//        if (customEmojiReactionsEnterProgress != 0) {
//            return;
//        }

		lastVisibleViewsTmp.clear()
		lastVisibleViewsTmp.addAll(lastVisibleViews)
		lastVisibleViews.clear()

		if (prepareAnimation) {
			invalidate()
		}

		if (pressedReaction != null) {
			if (pressedProgress != 1f) {
				pressedProgress += 16f / 1500f

				if (pressedProgress >= 1f) {
					pressedProgress = 1f
				}

				invalidate()
			}
		}

		pressedViewScale = 1 + 2 * pressedProgress
		otherViewsScale = 1 - 0.15f * pressedProgress

		var s = canvas.save()
		val pivotX = if (LocaleController.isRTL) width * 0.125f else width * 0.875f

		if (transitionProgress <= SCALE_PROGRESS) {
			val sc = transitionProgress / SCALE_PROGRESS
			canvas.scale(sc, sc, pivotX, height / 2f)
		}

		var lt = 0f
		var rt = 1f

		if (LocaleController.isRTL) {
			rt = max(CLIP_PROGRESS, transitionProgress)
		}
		else {
			lt = 1f - max(CLIP_PROGRESS, transitionProgress)
		}

		val pullingLeftOffsetProgress = pullingLeftProgress
		val expandSize = expandSize()

		chatScrimPopupContainerLayout?.setExpandSize(expandSize)

		rect.set(paddingLeft + (width - paddingRight) * lt, paddingTop + recyclerListView.measuredHeight * (1f - otherViewsScale) - expandSize, (width - paddingRight) * rt, height - paddingBottom + expandSize)

		radius = (rect.height() - expandSize * 2f) / 2f

		shadow.alpha = (Utilities.clamp(1f - customEmojiReactionsEnterProgress / 0.05f, 1f, 0f) * 255).toInt()
		shadow.setBounds((paddingLeft + (width - paddingRight + shadowPad.right) * lt - shadowPad.left).toInt(), paddingTop - shadowPad.top - expandSize.toInt(), ((width - paddingRight + shadowPad.right) * rt).toInt(), height - paddingBottom + shadowPad.bottom + expandSize.toInt())
		shadow.draw(canvas)

		canvas.restoreToCount(s)

		if (!skipDraw) {
			s = canvas.save()

			if (transitionProgress <= SCALE_PROGRESS) {
				val sc = transitionProgress / SCALE_PROGRESS
				canvas.scale(sc, sc, pivotX, height / 2f)
			}

			canvas.drawRoundRect(rect, radius, radius, bgPaint)
			canvas.restoreToCount(s)
		}

		mPath.rewind()
		mPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

		s = canvas.save()

		if (transitionProgress <= SCALE_PROGRESS) {
			val sc = transitionProgress / SCALE_PROGRESS
			canvas.scale(sc, sc, pivotX, height / 2f)
		}

		if (transitionProgress != 0f && alpha == 1f) {
			var delay = 0
			var lastReactionX = 0

			for (i in 0 until recyclerListView.childCount) {
				val child = recyclerListView.getChildAt(i)

				if (child is ReactionHolderView) {
					val view = recyclerListView.getChildAt(i) as ReactionHolderView

					checkPressedProgress(canvas, view)

					if (view.hasEnterAnimation && view.enterImageView.imageReceiver.lottieAnimation == null) {
						continue
					}

					if (view.x + view.measuredWidth / 2f > 0 && view.x + view.measuredWidth / 2f < recyclerListView.width) {
						if (!lastVisibleViewsTmp.contains(view)) {
							view.play(delay)
							delay += 30
						}

						lastVisibleViews.add(view)
					}
					else if (!view.isEnter) {
						view.resetAnimation()
					}

					if (view.left > lastReactionX) {
						lastReactionX = view.left
					}
				}
				else {
					if (child === premiumLockContainer) {
						if (child.getX() + child.getMeasuredWidth() / 2f > 0 && child.getX() + child.getMeasuredWidth() / 2f < recyclerListView.width) {
							if (!lastVisibleViewsTmp.contains(child)) {
								premiumLockIconView!!.play(delay)
								delay += 30
							}

							lastVisibleViews.add(child)
						}
						else {
							premiumLockIconView!!.resetAnimation()
						}
					}

					if (child === customReactionsContainer) {
						if (child.getX() + child.getMeasuredWidth() / 2f > 0 && child.getX() + child.getMeasuredWidth() / 2f < recyclerListView.width) {
							if (!lastVisibleViewsTmp.contains(child)) {
								customEmojiReactionsIconView!!.play()
								delay += 30
							}

							lastVisibleViews.add(child)
						}
						else {
							customEmojiReactionsIconView!!.resetAnimation()
						}
					}

					checkPressedProgressForOtherViews(child)
				}
			}

			if (pullingLeftOffsetProgress > 0) {
				val progress = pullingLeftProgress
				val left = lastReactionX + AndroidUtilities.dp(32f)
				val leftProgress = Utilities.clamp(left / (measuredWidth - AndroidUtilities.dp(34f)).toFloat(), 1f, 0f)
				val pullingOffsetX = leftProgress * progress * AndroidUtilities.dp(32f)

				if (nextRecentReaction.tag == null) {
					nextRecentReaction.tag = 1f
					nextRecentReaction.resetAnimation()
					nextRecentReaction.play(0)
				}

				val scale = Utilities.clamp(progress, 1f, 0f)

				nextRecentReaction.scaleX = scale
				nextRecentReaction.scaleY = scale
				nextRecentReaction.translationX = recyclerListView.left + left - pullingOffsetX - AndroidUtilities.dp(20f)
				nextRecentReaction.visible()
			}
			else {
				nextRecentReaction.gone()
				nextRecentReaction.tag = null
			}

			//            if (pullingLeftOffsetProgress > 0.8f) {
//                if (nextRecentReaction.getTag() == null) {
//                    nextRecentReaction.setTag(1f);
//                    if (nextRecentReaction.getVisibility() == View.GONE) {
//                        nextRecentReaction.resetAnimation();
//                        nextRecentReaction.setVisibility(View.VISIBLE);
//                        nextRecentReaction.play(0);
//                        nextRecentReaction.setScaleX(1f);
//                        nextRecentReaction.setScaleY(1f);
//                    } else {
//                        nextRecentReaction.animate().setListener(null).cancel();
//                        nextRecentReaction.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
//                    }
//
//                }
//
//            } else {
//                if (nextRecentReaction.getTag() != null) {
//                    nextRecentReaction.setTag(null);
//                    nextRecentReaction.animate().scaleX(0f).scaleY(0f).setDuration(100).setListener(new AnimatorListenerAdapter() {
//                        @Override
//                        public void onAnimationEnd(Animator animation) {
//                            nextRecentReaction.setVisibility(View.GONE);
//                        }
//                    }).start();
//                }
//            }
		}

		if (skipDraw && reactionsWindow != null) {
			val alpha = (Utilities.clamp(1f - customEmojiReactionsEnterProgress / 0.2f, 1f, 0f) * (1f - customEmojiReactionsEnterProgress) * 255).toInt()
			canvas.save()

			//canvas.translate(rect.left - reactionsWindow.drawingRect.left + (rect.width() - reactionsWindow.drawingRect.width()), rect.top - reactionsWindow.drawingRect.top + (rect.height() - reactionsWindow.drawingRect.height()));

			// canvas.translate(rect.width() - reactionsWindow.drawingRect.width(), (reactionsWindow.drawingRect.bottom() - rect.height()));

			drawBubbles(canvas, br, cPr, sr, alpha)
			canvas.restore()
			return
		}

		canvas.clipPath(mPath)
		canvas.translate((if (LocaleController.isRTL) -1 else 1) * width * (1f - transitionProgress), 0f)

		super.dispatchDraw(canvas)

		var p = Utilities.clamp(leftAlpha * transitionProgress, 1f, 0f)

		leftShadowPaint.alpha = (p * 0xFF).toInt()

		canvas.drawRect(rect, leftShadowPaint)

		p = Utilities.clamp(rightAlpha * transitionProgress, 1f, 0f)

		rightShadowPaint.alpha = (p * 0xFF).toInt()

		canvas.drawRect(rect, rightShadowPaint)
		canvas.restoreToCount(s)

		drawBubbles(canvas, br, cPr, sr, 255)

		invalidate()
	}

	fun drawBubbles(canvas: Canvas) {
		val cPr = (max(CLIP_PROGRESS, min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS)
		val br = bigCircleRadius * cPr
		val sr = smallCircleRadius * cPr
		val alpha = (Utilities.clamp(customEmojiReactionsEnterProgress / 0.2f, 1f, 0f) * (1f - customEmojiReactionsEnterProgress) * 255).toInt()

		drawBubbles(canvas, br, cPr, sr, alpha)
	}

	private fun drawBubbles(canvas: Canvas, br: Float, cPr: Float, sr: Float, alpha: Int) {
		canvas.save()
		canvas.clipRect(0f, rect.bottom, measuredWidth.toFloat(), (measuredHeight + AndroidUtilities.dp(8f)).toFloat())

		var cx = (if (LocaleController.isRTL) bigCircleOffset else width - bigCircleOffset).toFloat()
		var cy = height - paddingBottom + expandSize()
		var sPad = AndroidUtilities.dp(3f)

		shadow.alpha = alpha
		bgPaint.alpha = alpha
		shadow.setBounds((cx - br - sPad * cPr).toInt(), (cy - br - sPad * cPr).toInt(), (cx + br + sPad * cPr).toInt(), (cy + br + sPad * cPr).toInt())
		shadow.draw(canvas)

		canvas.drawCircle(cx, cy, br, bgPaint)

		cx = if (LocaleController.isRTL) bigCircleOffset - bigCircleRadius else width - bigCircleOffset + bigCircleRadius
		cy = height - smallCircleRadius - sPad + expandSize()

		sPad = -AndroidUtilities.dp(1f)

		shadow.setBounds((cx - br - sPad * cPr).toInt(), (cy - br - sPad * cPr).toInt(), (cx + br + sPad * cPr).toInt(), (cy + br + sPad * cPr).toInt())
		shadow.draw(canvas)

		canvas.drawCircle(cx, cy, sr, bgPaint)
		canvas.restore()

		shadow.alpha = 255
		bgPaint.alpha = 255
	}

	private fun checkPressedProgressForOtherViews(view: View) {
		val position = recyclerListView.getChildAdapterPosition(view)
		val translationX: Float = view.measuredWidth * (pressedViewScale - 1f) / 3f - view.measuredWidth * (1f - otherViewsScale) * (abs(pressedReactionPosition - position) - 1)

		if (position < pressedReactionPosition) {
			view.pivotX = 0f
			view.translationX = -translationX
		}
		else {
			view.pivotX = view.measuredWidth.toFloat()
			view.translationX = translationX
		}

		view.scaleX = otherViewsScale
		view.scaleY = otherViewsScale
	}

	private fun checkPressedProgress(canvas: Canvas, view: ReactionHolderView) {
		var pullingOffsetX = 0f

		if (pullingLeftOffset != 0f) {
			val progress = pullingLeftProgress
			val leftProgress = Utilities.clamp(view.left / (measuredWidth - AndroidUtilities.dp(34f)).toFloat(), 1f, 0f)
			pullingOffsetX = leftProgress * progress * AndroidUtilities.dp(46f)
		}

		if (view.currentReaction == pressedReaction) {
			val imageView: View = if (showCustomEmojiReaction()) view.loopImageView else view.enterImageView

			view.pivotX = (view.measuredWidth shr 1).toFloat()
			view.pivotY = imageView.y + imageView.measuredHeight
			view.scaleX = pressedViewScale
			view.scaleY = pressedViewScale

			if (!clicked) {
				if (cancelPressedAnimation == null) {
					view.pressedBackupImageView.visible()
					view.pressedBackupImageView.alpha = 1f

					if (view.pressedBackupImageView.imageReceiver.hasBitmapImage() || view.pressedBackupImageView.animatedEmojiDrawable != null && view.pressedBackupImageView.animatedEmojiDrawable!!.imageReceiver != null && view.pressedBackupImageView.animatedEmojiDrawable!!.imageReceiver.hasBitmapImage()) {
						imageView.alpha = 0f
					}
				}
				else {
					view.pressedBackupImageView.alpha = 1f - cancelPressedProgress
					imageView.alpha = cancelPressedProgress
				}

				if (pressedProgress == 1f) {
					clicked = true

					if (System.currentTimeMillis() - lastReactionSentTime > 300) {
						lastReactionSentTime = System.currentTimeMillis()
						delegate?.onReactionClicked(view, view.currentReaction, longpress = true, addToRecent = false)
					}
				}
			}

			canvas.save()

			var x = recyclerListView.x + view.x
			val additionalWidth = (view.measuredWidth * view.scaleX - view.measuredWidth) / 2f

			if (x - additionalWidth < 0 && view.translationX >= 0) {
				view.translationX = -(x - additionalWidth) - pullingOffsetX
			}
			else if (x + view.measuredWidth + additionalWidth > measuredWidth && view.translationX <= 0) {
				view.translationX = measuredWidth - x - view.measuredWidth - additionalWidth - pullingOffsetX
			}
			else {
				view.translationX = 0 - pullingOffsetX
			}

			x = recyclerListView.x + view.x

			canvas.translate(x, recyclerListView.y + view.y)
			canvas.scale(view.scaleX, view.scaleY, view.pivotX, view.pivotY)

			view.draw(canvas)

			canvas.restore()
		}
		else {
			val position = recyclerListView.getChildAdapterPosition(view)
			val translationX: Float = view.measuredWidth * (pressedViewScale - 1f) / 3f - view.measuredWidth * (1f - otherViewsScale) * (abs(pressedReactionPosition - position) - 1)

			if (position < pressedReactionPosition) {
				view.pivotX = 0f
				view.translationX = -translationX
			}
			else {
				view.pivotX = view.measuredWidth - pullingOffsetX
				view.translationX = translationX - pullingOffsetX
			}

			view.pivotY = view.enterImageView.y + view.enterImageView.measuredHeight
			view.scaleX = otherViewsScale
			view.scaleY = otherViewsScale
			view.enterImageView.scaleX = view.sideScale
			view.enterImageView.scaleY = view.sideScale
			view.pressedBackupImageView.visibility = INVISIBLE
			view.enterImageView.alpha = 1f
		}
	}

	private val pullingLeftProgress: Float
		get() = Utilities.clamp(pullingLeftOffset / AndroidUtilities.dp(42f), 2f, 0f)

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		invalidateShaders()
	}

	private fun invalidateShaders() {
		val dp = AndroidUtilities.dp(24f)
		val cy = height / 2f
		val clr = context.getColor(R.color.background)

		leftShadowPaint.shader = LinearGradient(0f, cy, dp.toFloat(), cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP)
		rightShadowPaint.shader = LinearGradient(width.toFloat(), cy, (width - dp).toFloat(), cy, clr, Color.TRANSPARENT, Shader.TileMode.CLAMP)

		invalidate()
	}

	fun setTransitionProgress(transitionProgress: Float) {
		this.transitionProgress = transitionProgress
		invalidate()
	}

	fun setMessage(message: MessageObject?, chatFull: ChatFull?) {
		messageObject = message

		var reactionsChat = chatFull
		val visibleReactions: MutableList<VisibleReaction> = ArrayList()

		if (message?.isForwardedChannelPost == true) {
			reactionsChat = MessagesController.getInstance(currentAccount).getChatFull(-message.fromChatId)

			if (reactionsChat == null) {
				waitingLoadingChatId = -message.fromChatId
				MessagesController.getInstance(currentAccount).loadFullChat(-message.fromChatId, 0, true)
				visibility = INVISIBLE
				return
			}
		}

		if (reactionsChat != null) {
			if (reactionsChat.available_reactions is TL_chatReactionsAll) {
				val chat = MessagesController.getInstance(currentAccount).getChat(reactionsChat.id)
				allReactionsAvailable = chat != null && !isChannelAndNotMegaGroup(chat)
				fillRecentReactionsList(visibleReactions)
			}
			else if (reactionsChat.available_reactions is TL_chatReactionsSome) {
				val reactionsSome = reactionsChat.available_reactions as TL_chatReactionsSome

				for (s in reactionsSome.reactions) {
					for (a in MediaDataController.getInstance(currentAccount).enabledReactionsList) {
						if (s is TL_reactionEmoji && a.reaction == s.emoticon) {
							visibleReactions.add(fromTLReaction(s))
							break
						}
						else if (s is TL_reactionCustomEmoji) {
							visibleReactions.add(fromTLReaction(s))
							break
						}
					}
				}
			}
			else {
				throw RuntimeException("Unknown chat reactions type")
			}
		}
		else {
			allReactionsAvailable = true
			fillRecentReactionsList(visibleReactions)
		}

		setVisibleReactionsList(visibleReactions)

		message?.messageOwner?.reactions?.results?.forEach {
			if (it.chosen) {
				selectedReactions.add(fromTLReaction(it.reaction))
			}
		}
	}

	private fun fillRecentReactionsList(visibleReactions: MutableList<VisibleReaction>) {
		if (!allReactionsAvailable) {
			//fill default reactions
			val enabledReactions = MediaDataController.getInstance(currentAccount).enabledReactionsList

			for (i in enabledReactions.indices) {
				val visibleReaction = fromEmojicon(enabledReactions[i])
				visibleReactions.add(visibleReaction)
			}

			return
		}

		val topReactions = MediaDataController.getInstance(currentAccount).topReactions
		val hashSet = HashSet<VisibleReaction>()
		var added = 0

		for (i in topReactions.indices) {
			val visibleReaction = fromTLReaction(topReactions[i])

			if (!hashSet.contains(visibleReaction) && (UserConfig.getInstance(currentAccount).isPremium || visibleReaction.documentId == 0L)) {
				hashSet.add(visibleReaction)
				visibleReactions.add(visibleReaction)
				added++
			}

			if (added == 16) {
				break
			}
		}

		val recentReactions = MediaDataController.getInstance(currentAccount).recentReactions

		for (i in recentReactions.indices) {
			val visibleReaction = fromTLReaction(recentReactions[i])

			if (!hashSet.contains(visibleReaction)) {
				hashSet.add(visibleReaction)
				visibleReactions.add(visibleReaction)
			}
		}

		//fill default reactions
		val enabledReactions = MediaDataController.getInstance(currentAccount).enabledReactionsList

		for (i in enabledReactions.indices) {
			val visibleReaction = fromEmojicon(enabledReactions[i])

			if (!hashSet.contains(visibleReaction)) {
				hashSet.add(visibleReaction)
				visibleReactions.add(visibleReaction)
			}
		}
	}

	private fun checkPremiumReactions(reactions: MutableList<TL_availableReaction>) {
		premiumLockedReactions.clear()

		if (UserConfig.getInstance(currentAccount).isPremium) {
			return
		}

		try {
			var i = 0

			while (i < reactions.size) {
				if (reactions[i].premium) {
					premiumLockedReactions.add(reactions.removeAt(i))
					i--
				}

				i++
			}
		}
		catch (e: Exception) {
			// ignored
		}
	}

	fun startEnterAnimation() {
		setTransitionProgress(0f)

		alpha = 1f

		val animator = ObjectAnimator.ofFloat(this, TRANSITION_PROGRESS_VALUE, 0f, 1f).setDuration(400)
		animator.interpolator = OvershootInterpolator(1.004f)
		animator.start()
	}

	val totalWidth: Int
		get() {
			val itemsCount = itemsCount

			return if (!showCustomEmojiReaction()) {
				AndroidUtilities.dp(36f) * itemsCount + AndroidUtilities.dp(2f) * (itemsCount - 1) + AndroidUtilities.dp(16f)
			}
			else {
				AndroidUtilities.dp(36f) * itemsCount - AndroidUtilities.dp(4f)
			}
		}

	val itemsCount: Int
		get() = visibleReactionsList.size + (if (showCustomEmojiReaction()) 1 else 0) + 1

	fun setCustomEmojiEnterProgress(progress: Float) {
		customEmojiReactionsEnterProgress = progress
		chatScrimPopupContainerLayout?.setPopupAlpha(1f - progress)
		invalidate()
	}

	fun dismissParent(animated: Boolean) {
		reactionsWindow?.dismiss(animated)
		reactionsWindow = null
	}

	fun onReactionClicked(emojiView: View?, visibleReaction: VisibleReaction?, longpress: Boolean) {
		delegate?.onReactionClicked(emojiView, visibleReaction, longpress, true)
	}

	fun prepareAnimation(b: Boolean) {
		prepareAnimation = b
		invalidate()
	}

	fun setSkipDraw(b: Boolean) {
		if (skipDraw != b) {
			skipDraw = b

			if (!skipDraw) {
				for (i in 0 until recyclerListView.childCount) {
					if (recyclerListView.getChildAt(i) is ReactionHolderView) {
						val holderView = recyclerListView.getChildAt(i) as ReactionHolderView

						if (holderView.hasEnterAnimation && (holderView.loopImageView.imageReceiver.lottieAnimation != null || holderView.loopImageView.imageReceiver.animation != null)) {
							holderView.loopImageView.visible()
							holderView.enterImageView.invisible()

							if (holderView.shouldSwitchToLoopView) {
								holderView.switchedToLoopView = true
							}
						}
					}
				}
			}

			invalidate()
		}
	}

	fun onCustomEmojiWindowOpened() {
		animatePullingBack()
	}

	fun clearRecentReactions() {
		val alertDialog = AlertDialog.Builder(context).setTitle(context.getString(R.string.ClearRecentReactionsAlertTitle)).setMessage(context.getString(R.string.ClearRecentReactionsAlertMessage)).setPositiveButton(context.getString(R.string.ClearButton)) { _, _ ->
			MediaDataController.getInstance(currentAccount).clearRecentReactions()

			val visibleReactions: MutableList<VisibleReaction> = ArrayList()

			fillRecentReactionsList(visibleReactions)
			setVisibleReactionsList(visibleReactions)

			lastVisibleViews.clear()

			reactionsWindow?.setRecentReactions(visibleReactions)
		}.setNegativeButton(context.getString(R.string.Cancel), null).create()

		alertDialog.show()

		val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
		button?.setTextColor(context.getColor(R.color.purple))
	}

	init {
		selectedPaint.color = context.getColor(R.color.light_background)

		nextRecentReaction.gone()
		nextRecentReaction.touchable = false
		nextRecentReaction.pressedBackupImageView.gone()

		addView(nextRecentReaction)

		animationEnabled = SharedConfig.animationsEnabled() && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW

		shadow = ContextCompat.getDrawable(context, R.drawable.reactions_bubble_shadow)!!.mutate()

		shadowPad.bottom = AndroidUtilities.dp(7f)
		shadowPad.right = shadowPad.bottom
		shadowPad.top = shadowPad.right
		shadowPad.left = shadowPad.top

		shadow.colorFilter = PorterDuffColorFilter(context.getColor(R.color.shadow), PorterDuff.Mode.MULTIPLY)

		recyclerListView = object : RecyclerListView(context) {
			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				return if (child is ReactionHolderView && child.currentReaction == pressedReaction) {
					true
				}
				else {
					super.drawChild(canvas, child, drawingTime)
				}
			}

			override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
				if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
					if (ev.action == MotionEvent.ACTION_UP && pullingLeftProgress > 0.95f) {
						showCustomEmojiReactionDialog()
					}
					else {
						animatePullingBack()
					}
				}

				return super.dispatchTouchEvent(ev)
			}
		}

		recyclerListView.setClipChildren(false)
		recyclerListView.setClipToPadding(false)

		linearLayoutManager = object : LinearLayoutManager(context, HORIZONTAL, false) {
			override fun scrollHorizontallyBy(dx: Int, recycler: Recycler, state: RecyclerView.State): Int {
				@Suppress("NAME_SHADOWING") var dx = dx

				if (dx < 0 && pullingLeftOffset != 0f) {
					val oldProgress: Float = pullingLeftProgress

					pullingLeftOffset += dx.toFloat()

					val newProgress: Float = pullingLeftProgress
					val b1 = oldProgress > 1f
					val b2 = newProgress > 1f

					if (b1 != b2) {
						recyclerListView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
					}

					if (pullingLeftOffset < 0) {
						dx = pullingLeftOffset.toInt()
						pullingLeftOffset = 0f
					}
					else {
						dx = 0
					}

					customReactionsContainer?.invalidate()

					recyclerListView.invalidate()
				}

				val scrolled = super.scrollHorizontallyBy(dx, recycler, state)

				if (dx > 0 && scrolled == 0 && recyclerListView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING && showCustomEmojiReaction()) {
					pullingDownBackAnimator?.removeAllListeners()
					pullingDownBackAnimator?.cancel()

					val oldProgress: Float = pullingLeftProgress
					var k = 0.6f

					if (oldProgress > 1f) {
						k = 0.05f
					}

					pullingLeftOffset += dx * k

					val newProgress: Float = pullingLeftProgress
					val b1 = oldProgress > 1f
					val b2 = newProgress > 1f

					if (b1 != b2) {
						recyclerListView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
					}

					customReactionsContainer?.invalidate()

					recyclerListView.invalidate()
				}

				return scrolled
			}
		}

		recyclerListView.addItemDecoration(object : RecyclerView.ItemDecoration() {
			override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
				super.getItemOffsets(outRect, view, parent, state)

				if (!showCustomEmojiReaction()) {
					val position = parent.getChildAdapterPosition(view)

					if (position == 0) {
						outRect.left = AndroidUtilities.dp(6f)
					}

					outRect.right = AndroidUtilities.dp(4f)

					if (position == listAdapter!!.itemCount - 1) {
						if (showUnlockPremiumButton() || showCustomEmojiReaction()) {
							outRect.right = AndroidUtilities.dp(2f)
						}
						else {
							outRect.right = AndroidUtilities.dp(6f)
						}
					}
				}
				else {
					outRect.left = 0
					outRect.right = outRect.left
				}
			}
		})

		recyclerListView.setLayoutManager(linearLayoutManager)
		recyclerListView.setOverScrollMode(OVER_SCROLL_NEVER)

		recyclerListView.setAdapter(object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			var rowCount = 0
			var reactionsStartRow = 0
			var reactionsEndRow = 0
			var premiumUnlockButtonRow = 0
			var customReactionsEmojiRow = 0

			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				val view: View

				when (viewType) {
					0 -> view = ReactionHolderView(context)
					1 -> {
						premiumLockContainer = FrameLayout(context)

						premiumLockIconView = PremiumLockIconView(context, PremiumLockIconView.TYPE_REACTIONS)
						premiumLockIconView?.setColor(ColorUtils.blendARGB(context.getColor(R.color.brand), context.getColor(R.color.background), 0.7f))
						premiumLockIconView?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.MULTIPLY)
						premiumLockIconView?.scaleX = 0f
						premiumLockIconView?.scaleY = 0f
						premiumLockIconView?.setPadding(AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), AndroidUtilities.dp(1f))

						premiumLockContainer?.addView(premiumLockIconView, LayoutHelper.createFrame(26, 26, Gravity.CENTER))

						premiumLockIconView?.setOnClickListener {
							val position = IntArray(2)
							it.getLocationOnScreen(position)
							showUnlockPremium()
						}

						view = premiumLockContainer!!
					}

					2 -> {
						customReactionsContainer = CustomReactionsContainer(context)

						customEmojiReactionsIconView = InternalImageView(context)
						customEmojiReactionsIconView?.setImageResource(R.drawable.msg_reactions_expand)
						customEmojiReactionsIconView?.scaleType = ImageView.ScaleType.CENTER_INSIDE
						customEmojiReactionsIconView?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.MULTIPLY)
						customEmojiReactionsIconView?.background = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(28f), Color.TRANSPARENT, ColorUtils.setAlphaComponent(context.getColor(R.color.light_background), 40))
						customEmojiReactionsIconView?.setPadding(AndroidUtilities.dp(2f), AndroidUtilities.dp(2f), AndroidUtilities.dp(2f), AndroidUtilities.dp(2f))

						customReactionsContainer?.addView(customEmojiReactionsIconView, LayoutHelper.createFrame(30, 30, Gravity.CENTER))

						customEmojiReactionsIconView?.setOnClickListener {
							showCustomEmojiReactionDialog()
						}


						view = customReactionsContainer!!
					}

					else -> view = ReactionHolderView(context)
				}

				val size = layoutParams.height - paddingTop - paddingBottom
				view.layoutParams = RecyclerView.LayoutParams(size - AndroidUtilities.dp(12f), size)
				return RecyclerListView.Holder(view)
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				if (holder.itemViewType == 0) {
					val h = holder.itemView as ReactionHolderView
					h.scaleX = 1f
					h.scaleY = 1f
					h.setReaction(visibleReactionsList[position], position)
				}
			}

			override fun getItemCount(): Int {
				return rowCount
			}

			override fun getItemViewType(position: Int): Int {
				return if (position >= 0 && position < visibleReactionsList.size) {
					0
				}
				else if (position == premiumUnlockButtonRow) {
					1
				}
				else {
					2
				}
			}

			@SuppressLint("NotifyDataSetChanged")
			override fun notifyDataSetChanged() {
				rowCount = 0
				premiumUnlockButtonRow = -1
				customReactionsEmojiRow = -1
				reactionsStartRow = 0

				rowCount += visibleReactionsList.size

				reactionsEndRow = rowCount

				if (showUnlockPremiumButton()) {
					premiumUnlockButtonRow = rowCount++
				}

				if (showCustomEmojiReaction()) {
					customReactionsEmojiRow = rowCount++
				}

				super.notifyDataSetChanged()
			}
		}.also { listAdapter = it })

		recyclerListView.addOnScrollListener(LeftRightShadowsListener())

		recyclerListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				if (recyclerView.childCount > 2) {
					val sideDiff = 1f - SIDE_SCALE

					recyclerView.getLocationInWindow(location)

					val rX = location[0]

					val ch1 = recyclerView.getChildAt(0)
					ch1.getLocationInWindow(location)

					val ch1X = location[0]
					val dX1 = ch1X - rX
					var s1 = SIDE_SCALE + (1f - min(1f, -min(dX1.toFloat(), 0f) / ch1.width)) * sideDiff

					if (java.lang.Float.isNaN(s1)) {
						s1 = 1f
					}

					setChildScale(ch1, s1)

					val ch2 = recyclerView.getChildAt(recyclerView.childCount - 1)

					ch2.getLocationInWindow(location)

					val ch2X = location[0]
					val dX2 = rX + recyclerView.width - (ch2X + ch2.width)
					var s2 = SIDE_SCALE + (1f - min(1f, -min(dX2.toFloat(), 0f) / ch2.width)) * sideDiff

					if (java.lang.Float.isNaN(s2)) {
						s2 = 1f
					}

					setChildScale(ch2, s2)
				}

				for (i in 1 until recyclerListView.getChildCount() - 1) {
					val ch = recyclerListView.getChildAt(i)
					setChildScale(ch, 1f)
				}

				invalidate()
			}
		})

		recyclerListView.addItemDecoration(object : RecyclerView.ItemDecoration() {
			override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
				val i = parent.getChildAdapterPosition(view)

				if (i == 0) {
					outRect.left = AndroidUtilities.dp(8f)
				}

				if (i == listAdapter!!.itemCount - 1) {
					outRect.right = AndroidUtilities.dp(8f)
				}
			}
		})

		recyclerListView.setOnItemClickListener { view, _ ->
			if (view is ReactionHolderView) {
				delegate?.onReactionClicked(this, view.currentReaction, longpress = false, addToRecent = false)
			}
		}

		recyclerListView.setOnItemLongClickListener { view, _ ->
			if (delegate != null && view is ReactionHolderView) {
				delegate?.onReactionClicked(this, view.currentReaction, longpress = true, addToRecent = false)
				return@setOnItemLongClickListener true
			}

			false
		}

		addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		clipChildren = false
		clipToPadding = false

		invalidateShaders()

		val size = recyclerListView.getLayoutParams().height - recyclerListView.getPaddingTop() - recyclerListView.getPaddingBottom()

		nextRecentReaction.layoutParams.width = size - AndroidUtilities.dp(12f)
		nextRecentReaction.layoutParams.height = size

		bgPaint.color = context.getColor(R.color.background)

		MediaDataController.getInstance(currentAccount).preloadReactions()
	}

	fun setChatScrimView(chatScrimPopupContainerLayout: ChatScrimPopupContainerLayout?) {
		this.chatScrimPopupContainerLayout = chatScrimPopupContainerLayout
	}

	private inner class LeftRightShadowsListener : RecyclerView.OnScrollListener() {
		private var leftVisible = false
		private var rightVisible = false
		private var leftAnimator: ValueAnimator? = null
		private var rightAnimator: ValueAnimator? = null

		override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
			val l = linearLayoutManager.findFirstVisibleItemPosition() != 0

			if (l != leftVisible) {
				leftAnimator?.cancel()

				leftAnimator = startAnimator(leftAlpha, (if (l) 1 else 0).toFloat(), { aFloat ->
					leftShadowPaint.alpha = (aFloat.also { leftAlpha = it } * 0xFF).toInt()
					invalidate()
				}) {
					leftAnimator = null
				}

				leftVisible = l
			}

			val r = linearLayoutManager.findLastVisibleItemPosition() != listAdapter!!.itemCount - 1

			if (r != rightVisible) {
				rightAnimator?.cancel()

				rightAnimator = startAnimator(rightAlpha, (if (r) 1 else 0).toFloat(), { aFloat ->
					rightShadowPaint.alpha = (aFloat.also { rightAlpha = it } * 0xFF).toInt()
					invalidate()
				}) {
					rightAnimator = null
				}

				rightVisible = r
			}
		}

		private fun startAnimator(fromAlpha: Float, toAlpha: Float, callback: Consumer<Float>, onEnd: Runnable): ValueAnimator {
			val a = ValueAnimator.ofFloat(fromAlpha, toAlpha).setDuration((abs(toAlpha - fromAlpha) * ALPHA_DURATION).toLong())

			a.addUpdateListener {
				callback.accept(it.animatedValue as Float)
			}

			a.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					onEnd.run()
				}
			})

			a.start()

			return a
		}
	}

	inner class ReactionHolderView internal constructor(context: Context) : FrameLayout(context) {
		val enterImageView: BackupImageView = object : BackupImageView(context) {
			override fun dispatchDraw(canvas: Canvas) {
				super.dispatchDraw(canvas)

				if (shouldSwitchToLoopView && !switchedToLoopView && imageReceiver.lottieAnimation != null && imageReceiver.lottieAnimation!!.isLastFrame && loopImageView.imageReceiver.lottieAnimation != null && loopImageView.imageReceiver.lottieAnimation!!.hasBitmap()) {
					switchedToLoopView = true

					loopImageView.imageReceiver.lottieAnimation!!.setCurrentFrame(0, false, true)
					loopImageView.visibility = VISIBLE

					AndroidUtilities.runOnUIThread {
						this.visibility = INVISIBLE
					}
				}
			}

			override fun invalidate() {
				super.invalidate()
				this@ReactionsContainerLayout.invalidate()
			}

			@Deprecated("Deprecated in Java")
			override fun invalidate(dirty: Rect) {
				super.invalidate()
				this@ReactionsContainerLayout.invalidate()
			}
		}

		val loopImageView = BackupImageView(context)

		val pressedBackupImageView = object : BackupImageView(context) {
			override fun invalidate() {
				super.invalidate()
				this@ReactionsContainerLayout.invalidate()
			}
		}

		var currentReaction: VisibleReaction? = null
		internal var sideScale = 1f
		internal var isEnter = false
		var hasEnterAnimation = false
		internal var shouldSwitchToLoopView = false
		internal var switchedToLoopView = false
		var reactionSelected = false
		var position = 0

		private val playRunnable = Runnable {
			val animation = enterImageView.imageReceiver.lottieAnimation ?: return@Runnable

			if (!animation.isRunning && !animation.isGeneratingCache) {
				animation.start()
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)

			val currentReaction = currentReaction ?: return

			if (currentReaction.emojicon != null) {
				info.text = currentReaction.emojicon
				info.isEnabled = true
			}
			else {
				info.text = context.getString(R.string.AccDescrCustomEmoji)
				info.isEnabled = true
			}
		}

		fun setReaction(react: VisibleReaction?, position: Int) {
			if (currentReaction != null && currentReaction == react) {
				return
			}

			this.position = position

			resetAnimation()

			currentReaction = react

			reactionSelected = selectedReactions.contains(react)

			if (currentReaction?.emojicon != null) {
				var fallbackToEmoji = true
				val defaultReaction = MediaDataController.getInstance(currentAccount).reactionsMap[currentReaction!!.emojicon]

				if (defaultReaction != null) {
					val svgThumb = getSvgThumb(defaultReaction.activate_animation, ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), 1.0f)

					if (svgThumb != null) {
						val enterImage = ImageLocation.getForDocument(defaultReaction.appear_animation)
						val pressedImage = ImageLocation.getForDocument(defaultReaction.select_animation)
						val loopImage = ImageLocation.getForDocument(defaultReaction.select_animation)

						if (enterImage != null && pressedImage != null && loopImage != null) {
							fallbackToEmoji = false

							enterImageView.imageReceiver.setImage(enterImage, ReactionsUtils.APPEAR_ANIMATION_FILTER, null, null, svgThumb, 0, "tgs", react, 0)

							pressedBackupImageView.imageReceiver.setImage(pressedImage, ReactionsUtils.SELECT_ANIMATION_FILTER, null, null, svgThumb, 0, "tgs", react, 0)
							pressedBackupImageView.animatedEmojiDrawable = null

							loopImageView.imageReceiver.setImage(loopImage, ReactionsUtils.SELECT_ANIMATION_FILTER, null, null, null, 0, "tgs", currentReaction, 0)
							loopImageView.animatedEmojiDrawable = null
						}
					}
				}

				if (fallbackToEmoji) {
					val drawable = Emoji.getEmojiDrawableForEmojicon(currentReaction?.emojicon)

					enterImageView.imageReceiver.setImageBitmap(drawable)
					pressedBackupImageView.imageReceiver.setImageBitmap(drawable)
					loopImageView.imageReceiver.setImageBitmap(drawable)
					loopImageView.animatedEmojiDrawable = null
					pressedBackupImageView.animatedEmojiDrawable = null
				}
			}
			else {
				pressedBackupImageView.imageReceiver.clearImage()
				loopImageView.imageReceiver.clearImage()
				pressedBackupImageView.animatedEmojiDrawable = AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, currentReaction!!.documentId)
				loopImageView.animatedEmojiDrawable = AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, currentReaction!!.documentId)
			}

			isFocusable = true
			hasEnterAnimation = currentReaction!!.emojicon != null && (!showCustomEmojiReaction() || allReactionsIsDefault)
			shouldSwitchToLoopView = hasEnterAnimation && showCustomEmojiReaction()

			if (!hasEnterAnimation) {
				enterImageView.gone()
				loopImageView.visible()
			}
			else {
				switchedToLoopView = false
				enterImageView.visible()
				loopImageView.gone()
			}

			if (reactionSelected) {
				loopImageView.layoutParams.height = AndroidUtilities.dp(26f)
				loopImageView.layoutParams.width = loopImageView.layoutParams.height
				enterImageView.layoutParams.height = AndroidUtilities.dp(26f)
				enterImageView.layoutParams.width = enterImageView.layoutParams.height
			}
			else {
				loopImageView.layoutParams.height = AndroidUtilities.dp(34f)
				loopImageView.layoutParams.width = loopImageView.layoutParams.height
				enterImageView.layoutParams.height = AndroidUtilities.dp(34f)
				enterImageView.layoutParams.width = enterImageView.layoutParams.height
			}
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			resetAnimation()
		}

		fun play(delay: Int): Boolean {
			if (!animationEnabled) {
				resetAnimation()

				isEnter = true

				if (!hasEnterAnimation) {
					loopImageView.visible()
					loopImageView.scaleY = 1f
					loopImageView.scaleX = 1f
				}

				return false
			}

			AndroidUtilities.cancelRunOnUIThread(playRunnable)

			if (hasEnterAnimation) {
				if (enterImageView.imageReceiver.lottieAnimation != null && !enterImageView.imageReceiver.lottieAnimation!!.isGeneratingCache && !isEnter) {
					isEnter = true

					if (delay == 0) {
						enterImageView.imageReceiver.lottieAnimation?.stop()
						enterImageView.imageReceiver.lottieAnimation?.setCurrentFrame(0, false)

						playRunnable.run()
					}
					else {
						enterImageView.imageReceiver.lottieAnimation?.stop()
						enterImageView.imageReceiver.lottieAnimation?.setCurrentFrame(0, false)

						AndroidUtilities.runOnUIThread(playRunnable, delay.toLong())
					}

					return true
				}

				if (enterImageView.imageReceiver.lottieAnimation != null && isEnter && !enterImageView.imageReceiver.lottieAnimation!!.isRunning && !enterImageView.imageReceiver.lottieAnimation!!.isGeneratingCache) {
					enterImageView.imageReceiver.lottieAnimation!!.setCurrentFrame(enterImageView.imageReceiver.lottieAnimation!!.framesCount - 1, false)
				}

				loopImageView.scaleY = 1f
				loopImageView.scaleX = 1f
			}
			else {
				if (!isEnter) {
					loopImageView.scaleY = 0f
					loopImageView.scaleX = 0f
					loopImageView.animate().scaleX(1f).scaleY(1f).setDuration(150).setStartDelay(delay.toLong()).start()
					isEnter = true
				}
			}

			return false
		}

		fun resetAnimation() {
			if (hasEnterAnimation) {
				AndroidUtilities.cancelRunOnUIThread(playRunnable)

				if (enterImageView.imageReceiver.lottieAnimation != null && !enterImageView.imageReceiver.lottieAnimation!!.isGeneratingCache) {
					enterImageView.imageReceiver.lottieAnimation!!.stop()

					if (animationEnabled) {
						enterImageView.imageReceiver.lottieAnimation!!.setCurrentFrame(0, false, true)
					}
					else {
						enterImageView.imageReceiver.lottieAnimation!!.setCurrentFrame(enterImageView.imageReceiver.lottieAnimation!!.framesCount - 1, false, true)
					}
				}

				loopImageView.invisible()
				enterImageView.visible()

				switchedToLoopView = false

				loopImageView.scaleY = 1f
				loopImageView.scaleX = 1f
			}
			else {
				loopImageView.animate().cancel()

				loopImageView.scaleY = 0f
				loopImageView.scaleX = 0f
			}

			isEnter = false
		}

		private var longPressRunnable = Runnable {
			performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
			pressedReactionPosition = visibleReactionsList.indexOf(currentReaction)
			pressedReaction = currentReaction
			this@ReactionsContainerLayout.invalidate()
		}

		private var pressedX = 0f
		private var pressedY = 0f
		private var pressed = false
		internal var touchable = true

		init {
			enterImageView.imageReceiver.setAutoRepeat(0)
			enterImageView.imageReceiver.setAllowStartLottieAnimation(false)

			addView(enterImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER))
			addView(pressedBackupImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER))
			addView(loopImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER))
		}

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (!touchable) {
				return false
			}

			if (cancelPressedAnimation != null) {
				return false
			}

			if (event.action == MotionEvent.ACTION_DOWN) {
				pressed = true
				pressedX = event.x
				pressedY = event.y

				if (sideScale == 1f) {
					AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
				}
			}

			val touchSlop = ViewConfiguration.get(context).scaledTouchSlop * 2f
			val cancelByMove = event.action == MotionEvent.ACTION_MOVE && (abs(pressedX - event.x) > touchSlop || abs(pressedY - event.y) > touchSlop)

			if (cancelByMove || event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
				if (event.action == MotionEvent.ACTION_UP && pressed && (pressedReaction == null || pressedProgress > 0.8f) && delegate != null) {
					clicked = true

					if (System.currentTimeMillis() - lastReactionSentTime > 300) {
						lastReactionSentTime = System.currentTimeMillis()
						delegate?.onReactionClicked(this, currentReaction, pressedProgress > 0.8f, false)
					}
				}

				if (!clicked) {
					cancelPressed()
				}

				AndroidUtilities.cancelRunOnUIThread(longPressRunnable)

				pressed = false
			}

			return true
		}

		override fun dispatchDraw(canvas: Canvas) {
			if (reactionSelected) {
				canvas.drawCircle((measuredWidth shr 1).toFloat(), (measuredHeight shr 1).toFloat(), ((measuredWidth shr 1) - AndroidUtilities.dp(1f)).toFloat(), selectedPaint)
			}

			if (position == 0) {
				loopImageView.animatedEmojiDrawable?.imageReceiver?.setRoundRadius(AndroidUtilities.dp(6f), 0, 0, AndroidUtilities.dp(6f))
			}
			else {
				loopImageView.animatedEmojiDrawable?.imageReceiver?.setRoundRadius(if (reactionSelected) AndroidUtilities.dp(6f) else 0)
			}

			super.dispatchDraw(canvas)
		}
	}

	private fun cancelPressed() {
		if (pressedReaction != null) {
			cancelPressedProgress = 0f

			val fromProgress = pressedProgress

			cancelPressedAnimation = ValueAnimator.ofFloat(0f, 1f)

			cancelPressedAnimation?.addUpdateListener {
				cancelPressedProgress = it.animatedValue as Float
				pressedProgress = fromProgress * (1f - cancelPressedProgress)
				this@ReactionsContainerLayout.invalidate()
			}

			cancelPressedAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					super.onAnimationEnd(animation)

					cancelPressedAnimation = null
					pressedProgress = 0f
					pressedReaction = null

					this@ReactionsContainerLayout.invalidate()
				}
			})

			cancelPressedAnimation?.duration = 150
			cancelPressedAnimation?.interpolator = CubicBezierInterpolator.DEFAULT
			cancelPressedAnimation?.start()
		}
	}

	interface ReactionsContainerDelegate {
		fun onReactionClicked(view: View?, visibleReaction: VisibleReaction?, longpress: Boolean, addToRecent: Boolean)
		fun hideMenu()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad)
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.chatInfoDidLoad) {
			val chatFull = args[0] as ChatFull

			if (chatFull.id == waitingLoadingChatId && visibility != VISIBLE && chatFull.available_reactions !is TL_chatReactionsNone) {
				setMessage(messageObject, null)
				visibility = VISIBLE
				startEnterAnimation()
			}
		}
	}

	override fun setAlpha(alpha: Float) {
		if (getAlpha() != alpha && alpha == 0f) {
			lastVisibleViews.clear()

			for (i in 0 until recyclerListView.childCount) {
				if (recyclerListView.getChildAt(i) is ReactionHolderView) {
					val view = recyclerListView.getChildAt(i) as ReactionHolderView
					view.resetAnimation()
				}
			}
		}

		super.setAlpha(alpha)
	}

	override fun setTranslationX(translationX: Float) {
		if (translationX != getTranslationX()) {
			super.setTranslationX(translationX)
		}
	}

	@SuppressLint("AppCompatCustomView")
	private inner class InternalImageView(context: Context) : ImageView(context) {
		var isEnter = false
		var valueAnimator: ValueAnimator? = null

		fun play() {
			isEnter = true

			// cellFlickerDrawable.progress = 0
			// cellFlickerDrawable.repeatEnabled = false

			invalidate()

			valueAnimator?.removeAllListeners()
			valueAnimator?.cancel()

			valueAnimator = ValueAnimator.ofFloat(scaleX, 1f)
			valueAnimator?.interpolator = AndroidUtilities.overshootInterpolator

			valueAnimator?.addUpdateListener {
				val s = it.animatedValue as Float
				scaleX = s
				scaleY = s
				customReactionsContainer!!.invalidate()
			}

			valueAnimator?.duration = 300
			valueAnimator?.start()
		}

		fun resetAnimation() {
			isEnter = false
			scaleX = 0f
			scaleY = 0f

			customReactionsContainer?.invalidate()
			valueAnimator?.cancel()
		}
	}

	private inner class CustomReactionsContainer(context: Context) : FrameLayout(context) {
		var backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

		override fun dispatchDraw(canvas: Canvas) {
			super.dispatchDraw(canvas)

			val color = ColorUtils.blendARGB(context.getColor(R.color.brand), context.getColor(R.color.background), 0.7f)

			backgroundPaint.color = color

			val cy = measuredHeight shr 1
			val cx = measuredWidth shr 1
			val child = getChildAt(0)
			val sizeHalf = measuredWidth - AndroidUtilities.dp(6f) shr 1
			// val pullingLeftOffsetProgress: Float = pullingLeftProgress

			val expandSize = expandSize()

			AndroidUtilities.rectTmp[(cx - sizeHalf).toFloat(), cy - sizeHalf - expandSize, (cx + sizeHalf).toFloat()] = cy + sizeHalf + expandSize

			canvas.save()
			canvas.scale(child.scaleX, child.scaleY, cx.toFloat(), cy.toFloat())
			canvas.drawRoundRect(AndroidUtilities.rectTmp, sizeHalf.toFloat(), sizeHalf.toFloat(), backgroundPaint)
			canvas.restore()
			canvas.save()
			canvas.translate(0f, expandSize)

			super.dispatchDraw(canvas)

			canvas.restore()
		}
	}

	fun expandSize(): Float {
		return (pullingLeftProgress * AndroidUtilities.dp(6f)).toInt().toFloat()
	}

	companion object {
		val TRANSITION_PROGRESS_VALUE: Property<ReactionsContainerLayout, Float> = object : Property<ReactionsContainerLayout, Float>(Float::class.java, "transitionProgress") {
			override fun get(reactionsContainerLayout: ReactionsContainerLayout): Float {
				return reactionsContainerLayout.transitionProgress
			}

			override fun set(`object`: ReactionsContainerLayout, value: Float) {
				`object`.setTransitionProgress(value)
			}
		}

		private const val ALPHA_DURATION = 150
		private const val SIDE_SCALE = 0.6f
		private const val SCALE_PROGRESS = 0.75f
		private const val CLIP_PROGRESS = 0.25f
	}
}
