/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.util.size
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DialogObject
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.unreadCount
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.CounterView.CounterDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import kotlin.math.max
import kotlin.math.min

class ChatPullingDownDrawable(private val currentAccount: Int, private val fragmentView: View?, private val currentDialog: Long, private val folderId: Int, private val filterId: Int) : NotificationCenterDelegate {
	private val context = ApplicationLoader.applicationContext
	private val textPaint2 = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val xRefPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var animateCheck = false
	private var arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var chatNameLayout: StaticLayout? = null
	private var chatNameWidth = 0
	private var circleRadius = 0f
	private var counterDrawable = CounterDrawable(null, drawBackground = true, drawWhenZero = false)
	private var drawFolderBackground = false
	private var lastHapticTime: Long = 0
	private var lastProgress = 0f
	private var layout1: StaticLayout? = null
	private var layout1Width = 0
	private var layout2: StaticLayout? = null
	private var layout2Width = 0
	private var progressToBottomPanel = 0f
	private var showReleaseAnimator: AnimatorSet? = null
	val imageReceiver = ImageReceiver()
	val path = Path()
	val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	var animateSwipeToRelease = false
	var bounceProgress = 0f
	var checkProgress = 0f
	var dialogFilterId = 0
	var dialogFolderId = 0
	var emptyStub = false
	var lastShowingReleaseTime: Long = 0
	var lastWidth = 0
	var nextChat: Chat? = null
	var nextDialogId: Long = 0
	var onAnimationFinishRunnable: Runnable? = null
	var params = IntArray(3)
	var parentView: View? = null
	var swipeToReleaseProgress = 0f

	fun updateDialog() {
		val dialog = getNextUnreadDialog(currentDialog, folderId, filterId, true, params)

		if (dialog != null) {
			nextDialogId = dialog.id
			drawFolderBackground = params[0] == 1
			dialogFolderId = params[1]
			dialogFilterId = params[2]
			emptyStub = false
			nextChat = MessagesController.getInstance(currentAccount).getChat(-dialog.id)

			if (nextChat == null) {
				MessagesController.getInstance(currentAccount).getChat(dialog.id)
			}

			val avatarDrawable = AvatarDrawable()
			avatarDrawable.setInfo(nextChat)

			imageReceiver.setImage(ImageLocation.getForChat(nextChat, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, null, UserConfig.getInstance(0).getCurrentUser(), 0)

			MessagesController.getInstance(currentAccount).ensureMessagesLoaded(dialog.id, 0, null)

			counterDrawable.setCount(dialog.unreadCount, false)
		}
		else {
			nextChat = null
			drawFolderBackground = false
			emptyStub = true
		}
	}

	fun setWidth(width: Int) {
		if (width != lastWidth) {
			circleRadius = AndroidUtilities.dp(56f) / 2f
			lastWidth = width

			val nameStr = nextChat?.title ?: context.getString(R.string.SwipeToGoNextChannelEnd)

			chatNameWidth = textPaint.measureText(nameStr).toInt()
			chatNameWidth = min(chatNameWidth, lastWidth - AndroidUtilities.dp(60f))
			chatNameLayout = StaticLayout(nameStr, textPaint, chatNameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

			val str1: String
			val str2: String

			if (drawFolderBackground && dialogFolderId != folderId && dialogFolderId != 0) {
				str1 = context.getString(R.string.SwipeToGoNextArchive)
				str2 = context.getString(R.string.ReleaseToGoNextArchive)
			}
			else if (drawFolderBackground) {
				str1 = context.getString(R.string.SwipeToGoNextFolder)
				str2 = context.getString(R.string.ReleaseToGoNextFolder)
			}
			else {
				str1 = context.getString(R.string.SwipeToGoNextChannel)
				str2 = context.getString(R.string.ReleaseToGoNextChannel)
			}

			layout1Width = textPaint2.measureText(str1).toInt()
			layout1Width = min(layout1Width, lastWidth - AndroidUtilities.dp(60f))

			layout1 = StaticLayout(str1, textPaint2, layout1Width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

			layout2Width = textPaint2.measureText(str2).toInt()
			layout2Width = min(layout2Width, lastWidth - AndroidUtilities.dp(60f))

			layout2 = StaticLayout(str2, textPaint2, layout2Width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

			val cx = lastWidth / 2f
			val cy = AndroidUtilities.dp(12f) + circleRadius

			imageReceiver.setImageCoordinates(cx - AndroidUtilities.dp(40f) / 2f, cy - AndroidUtilities.dp(40f) / 2f, AndroidUtilities.dp(40f).toFloat(), AndroidUtilities.dp(40f).toFloat())
			imageReceiver.setRoundRadius((AndroidUtilities.dp(40f) / 2f).toInt())

			counterDrawable.setSize(AndroidUtilities.dp(28f), AndroidUtilities.dp(100f))
		}
	}

	fun draw(canvas: Canvas, parent: View, progress: Float, alpha: Float) {
		@Suppress("NAME_SHADOWING") var alpha = alpha
		parentView = parent
		counterDrawable.parent = parent

		var offset = AndroidUtilities.dp(110f) * progress

		if (offset < AndroidUtilities.dp(8f)) {
			return
		}

		if (progress < 0.2f) {
			alpha *= progress * 5f
		}

		Theme.applyServiceShaderMatrix(lastWidth, parent.measuredHeight, 0f, parent.measuredHeight - offset)

		textPaint.color = getThemedColor(Theme.key_chat_serviceText)
		arrowPaint.color = getThemedColor(Theme.key_chat_serviceText)
		textPaint2.color = getThemedColor(Theme.key_chat_messagePanelHint)

		val oldAlpha = getThemedPaint(Theme.key_paint_chatActionBackground).alpha
		val oldAlpha1 = Theme.chat_actionBackgroundGradientDarkenPaint.alpha
		val oldAlpha2 = textPaint.alpha
		val oldAlpha3 = arrowPaint.alpha

		Theme.chat_actionBackgroundGradientDarkenPaint.alpha = (oldAlpha1 * alpha).toInt()

		getThemedPaint(Theme.key_paint_chatActionBackground).alpha = (oldAlpha * alpha).toInt()

		textPaint.alpha = (oldAlpha2 * alpha).toInt()
		imageReceiver.alpha = alpha

		if (progress >= 1f && lastProgress < 1f || progress < 1f && lastProgress == 1f) {
			val time = System.currentTimeMillis()

			if (time - lastHapticTime > 100) {
				parent.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				lastHapticTime = time
			}

			lastProgress = progress
		}

		if (progress == 1f && !animateSwipeToRelease) {
			animateSwipeToRelease = true
			animateCheck = true

			showReleaseState(true, parent)

			lastShowingReleaseTime = System.currentTimeMillis()
		}
		else if (progress != 1f && animateSwipeToRelease) {
			animateSwipeToRelease = false
			showReleaseState(false, parent)
		}

		val cx = lastWidth / 2f
		val bounceOffset = bounceProgress * -AndroidUtilities.dp(4f)

		if (emptyStub) {
			offset -= bounceOffset
		}

		val widthRadius = max(0f, min(circleRadius, offset / 2f - AndroidUtilities.dp(16f) * progress - AndroidUtilities.dp(4f)))
		val widthRadius2 = max(0f, min(circleRadius * progress, offset / 2f - AndroidUtilities.dp(8f) * progress))
		val size = (widthRadius2 * 2 - AndroidUtilities.dp2(16f)) * (1f - swipeToReleaseProgress) + AndroidUtilities.dp(56f) * swipeToReleaseProgress

		if (swipeToReleaseProgress < 1f || emptyStub) {
			val bottom = -AndroidUtilities.dp(8f) * (1f - swipeToReleaseProgress) + (-offset + AndroidUtilities.dp(56f)) * swipeToReleaseProgress

			AndroidUtilities.rectTmp[cx - widthRadius, -offset, cx + widthRadius] = bottom

			if (swipeToReleaseProgress > 0 && !emptyStub) {
				val inset = AndroidUtilities.dp(16f) * swipeToReleaseProgress
				AndroidUtilities.rectTmp.inset(inset, inset)
			}

			drawBackground(canvas, AndroidUtilities.rectTmp)

			val arrowCy = -offset + AndroidUtilities.dp(24f) + AndroidUtilities.dp(8f) * (1f - progress) - AndroidUtilities.dp(36f) * swipeToReleaseProgress

			canvas.withSave {
				AndroidUtilities.rectTmp.inset(AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat())

				clipRect(AndroidUtilities.rectTmp)

				if (swipeToReleaseProgress > 0f) {
					arrowPaint.alpha = ((1f - swipeToReleaseProgress) * 255).toInt()
				}

				drawArrow(this, cx, arrowCy, AndroidUtilities.dp(24f) * progress)

				if (emptyStub) {
					val top = (-AndroidUtilities.dp(8f) - AndroidUtilities.dp2(8f) * progress - size) * (1f - swipeToReleaseProgress) + (-offset - AndroidUtilities.dp(2f)) * swipeToReleaseProgress + bounceOffset

					arrowPaint.alpha = oldAlpha3

					withScale(progress, progress, cx, top + AndroidUtilities.dp(28f)) {
						drawCheck(this, cx, top + AndroidUtilities.dp(28f))
					}
				}
			}
		}

		if (chatNameLayout != null && swipeToReleaseProgress > 0) {
			getThemedPaint(Theme.key_paint_chatActionBackground).alpha = (oldAlpha * alpha).toInt()

			textPaint.alpha = (oldAlpha2 * alpha).toInt()

			val y = AndroidUtilities.dp(20f) * (1f - swipeToReleaseProgress) - AndroidUtilities.dp(36f) * swipeToReleaseProgress + bounceOffset

			AndroidUtilities.rectTmp.set((lastWidth - chatNameWidth) / 2f, y, lastWidth - (lastWidth - chatNameWidth) / 2f, y + chatNameLayout!!.height)
			AndroidUtilities.rectTmp.inset(-AndroidUtilities.dp(8f).toFloat(), -AndroidUtilities.dp(4f).toFloat())

			canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(15f).toFloat(), AndroidUtilities.dp(15f).toFloat(), getThemedPaint(Theme.key_paint_chatActionBackground))

			if (hasGradientService()) {
				canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(15f).toFloat(), AndroidUtilities.dp(15f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
			}

			canvas.withTranslation((lastWidth - chatNameWidth) / 2f, y) {
				chatNameLayout?.draw(this)
			}
		}

		if (!emptyStub && size > 0) {
			val top = (-AndroidUtilities.dp(8f) - AndroidUtilities.dp2(8f) * progress - size) * (1f - swipeToReleaseProgress) + (-offset + AndroidUtilities.dp(4f)) * swipeToReleaseProgress + bounceOffset

			imageReceiver.setRoundRadius((size / 2f).toInt())
			imageReceiver.setImageCoordinates(cx - size / 2f, top, size, size)

			if (swipeToReleaseProgress > 0) {
				canvas.saveLayerAlpha(imageReceiver.imageX, imageReceiver.imageY, imageReceiver.imageX + imageReceiver.imageWidth, imageReceiver.imageY + imageReceiver.imageHeight, 255)

				imageReceiver.draw(canvas)

				canvas.scale(swipeToReleaseProgress, swipeToReleaseProgress, cx + AndroidUtilities.dp(12f) + counterDrawable.centerX, top - AndroidUtilities.dp(6f) + AndroidUtilities.dp(14f))
				canvas.translate(cx + AndroidUtilities.dp(12f), top - AndroidUtilities.dp(6f))

				counterDrawable.updateBackgroundRect()
				counterDrawable.rectF.inset(-AndroidUtilities.dp(2f).toFloat(), -AndroidUtilities.dp(2f).toFloat())

				canvas.drawRoundRect(counterDrawable.rectF, counterDrawable.rectF.height() / 2f, counterDrawable.rectF.height() / 2f, xRefPaint)
				canvas.restore()

				canvas.withScale(swipeToReleaseProgress, swipeToReleaseProgress, cx + AndroidUtilities.dp(12f) + counterDrawable.centerX, top - AndroidUtilities.dp(6f) + AndroidUtilities.dp(14f)) {
					translate(cx + AndroidUtilities.dp(12f), top - AndroidUtilities.dp(6f))
					counterDrawable.draw(this)
				}
			}
			else {
				imageReceiver.draw(canvas)
			}
		}

		getThemedPaint(Theme.key_paint_chatActionBackground).alpha = oldAlpha

		Theme.chat_actionBackgroundGradientDarkenPaint.alpha = oldAlpha1

		textPaint.alpha = oldAlpha2
		arrowPaint.alpha = oldAlpha3
		imageReceiver.alpha = 1f
	}

	private fun drawCheck(canvas: Canvas, cx: Float, cy: Float) {
		if (!animateCheck) {
			return
		}

		if (checkProgress < 1f) {
			checkProgress += 16 / 220f

			if (checkProgress > 1f) {
				checkProgress = 1f
			}
		}

		val p1 = if (checkProgress > 0.5f) 1f else checkProgress / 0.5f
		val p2: Float = if (checkProgress < 0.5f) 0f else (checkProgress - 0.5f) / 0.5f

		canvas.withClip(AndroidUtilities.rectTmp) {
			translate(cx - AndroidUtilities.dp(24f), cy - AndroidUtilities.dp(24f))

			val x1 = AndroidUtilities.dp(16f).toFloat()
			val y1 = AndroidUtilities.dp(26f).toFloat()
			val x2 = AndroidUtilities.dp(22f).toFloat()
			val y2 = AndroidUtilities.dp(32f).toFloat()
			val x3 = AndroidUtilities.dp(32f).toFloat()
			val y3 = AndroidUtilities.dp(20f).toFloat()

			drawLine(x1, y1, x1 * (1f - p1) + x2 * p1, y1 * (1f - p1) + y2 * p1, arrowPaint)

			if (p2 > 0) {
				drawLine(x2, y2, x2 * (1f - p2) + x3 * p2, y2 * (1f - p2) + y3 * p2, arrowPaint)
			}
		}
	}

	private fun drawBackground(canvas: Canvas, rectTmp: RectF) {
		if (drawFolderBackground) {
			path.reset()

			val roundRadius = rectTmp.width() * 0.2f
			val folderOffset = rectTmp.width() * 0.1f
			val folderOffset2 = rectTmp.width() * 0.03f
			val roundRadius2 = folderOffset / 2f
			val h = rectTmp.height() - folderOffset

			path.moveTo(rectTmp.right, rectTmp.top + roundRadius + folderOffset)
			path.rQuadTo(0f, -roundRadius, -roundRadius, -roundRadius)
			path.rLineTo(-(rectTmp.width() - 2 * roundRadius) / 2 + roundRadius2 * 2 - folderOffset2, 0f)
			path.rQuadTo(-roundRadius2 / 2, 0f, -roundRadius2 * 2, -folderOffset / 2)
			path.rQuadTo(-roundRadius2 / 2, -folderOffset / 2, -roundRadius2 * 2, -folderOffset / 2)
			path.rLineTo(-(rectTmp.width() - 2 * roundRadius) / 2 + roundRadius2 * 2 + folderOffset2, 0f)
			path.rQuadTo(-roundRadius, 0f, -roundRadius, roundRadius)
			path.rLineTo(0f, h + folderOffset - 2 * roundRadius)
			path.rQuadTo(0f, roundRadius, roundRadius, roundRadius)
			path.rLineTo(rectTmp.width() - 2 * roundRadius, 0f)
			path.rQuadTo(roundRadius, 0f, roundRadius, -roundRadius)
			path.rLineTo(0f, -(h - 2 * roundRadius))
			path.close()

			canvas.drawPath(path, getThemedPaint(Theme.key_paint_chatActionBackground))

			if (hasGradientService()) {
				canvas.drawPath(path, Theme.chat_actionBackgroundGradientDarkenPaint)
			}
		}
		else {
			canvas.drawRoundRect(AndroidUtilities.rectTmp, circleRadius, circleRadius, getThemedPaint(Theme.key_paint_chatActionBackground))

			if (hasGradientService()) {
				canvas.drawRoundRect(AndroidUtilities.rectTmp, circleRadius, circleRadius, Theme.chat_actionBackgroundGradientDarkenPaint)
			}
		}
	}

	private fun showReleaseState(show: Boolean, parent: View) {
		showReleaseAnimator?.removeAllListeners()
		showReleaseAnimator?.cancel()

		if (show) {
			val out = ValueAnimator.ofFloat(swipeToReleaseProgress, 1f)

			out.addUpdateListener {
				swipeToReleaseProgress = it.animatedValue as Float
				parent.invalidate()
				fragmentView?.invalidate()
			}

			out.interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
			out.setDuration(250)

			bounceProgress = 0f

			val bounceUp = ValueAnimator.ofFloat(0f, 1f)

			bounceUp.addUpdateListener {
				bounceProgress = it.animatedValue as Float
				parent.invalidate()
			}

			bounceUp.interpolator = CubicBezierInterpolator.EASE_BOTH
			bounceUp.setDuration(180)

			val bounceDown = ValueAnimator.ofFloat(1f, -0.5f)

			bounceDown.addUpdateListener {
				bounceProgress = it.animatedValue as Float
				parent.invalidate()
			}

			bounceDown.interpolator = CubicBezierInterpolator.EASE_BOTH
			bounceDown.setDuration(120)

			val bounceOut = ValueAnimator.ofFloat(-0.5f, 0f)

			bounceOut.addUpdateListener {
				bounceProgress = it.animatedValue as Float
				parent.invalidate()
			}

			bounceOut.interpolator = CubicBezierInterpolator.EASE_BOTH
			bounceOut.setDuration(100)

			showReleaseAnimator = AnimatorSet()

			showReleaseAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					bounceProgress = 0f
					swipeToReleaseProgress = 1f

					parent.invalidate()

					fragmentView?.invalidate()

					onAnimationFinishRunnable?.run()
					onAnimationFinishRunnable = null
				}
			})

			val bounce = AnimatorSet()

			bounce.playSequentially(bounceUp, bounceDown, bounceOut)

			showReleaseAnimator?.playTogether(out, bounce)
			showReleaseAnimator?.start()
		}
		else {
			val out = ValueAnimator.ofFloat(swipeToReleaseProgress, 0f)

			out.addUpdateListener {
				swipeToReleaseProgress = it.animatedValue as Float
				fragmentView?.invalidate()
				parent.invalidate()
			}

			out.interpolator = CubicBezierInterpolator.DEFAULT
			out.setDuration(220)

			showReleaseAnimator = AnimatorSet()
			showReleaseAnimator?.playTogether(out)
			showReleaseAnimator?.start()
		}
	}

	private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, size: Float) {
		canvas.withSave {
			val s = size / AndroidUtilities.dpf2(24f)

			scale(s, s, cx, cy - AndroidUtilities.dp(20f))
			translate(cx - AndroidUtilities.dp2(12f), cy - AndroidUtilities.dp(12f))
			drawLine(AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(4f), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(22f), arrowPaint)
			drawLine(AndroidUtilities.dpf2(3.5f), AndroidUtilities.dpf2(12f), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(3.5f), arrowPaint)
			drawLine(AndroidUtilities.dpf2(25 - 3.5f), AndroidUtilities.dpf2(12f), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(3.5f), arrowPaint)
		}
	}

	fun onAttach() {
		imageReceiver.onAttachedToWindow()
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces)
	}

	fun onDetach() {
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces)
		imageReceiver.onDetachedFromWindow()
		lastProgress = 0f
		lastHapticTime = 0
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (nextDialogId != 0L) {
			val dialog = MessagesController.getInstance(currentAccount).dialogs_dict[nextDialogId]

			if (dialog != null) {
				counterDrawable.setCount(dialog.unreadCount, true)
				parentView?.invalidate()
			}
		}
	}

	val chatId: Long
		get() = nextChat?.id ?: 0L

	fun drawBottomPanel(canvas: Canvas, top: Int, bottom: Int, width: Int) {
		if (showBottomPanel && progressToBottomPanel != 1f) {
			progressToBottomPanel += 16f / 150f

			if (progressToBottomPanel > 1f) {
				progressToBottomPanel = 1f
			}
			else {
				fragmentView?.invalidate()
			}
		}
		else if (!showBottomPanel && progressToBottomPanel != 0f) {
			progressToBottomPanel -= 16f / 150f

			if (progressToBottomPanel < 0) {
				progressToBottomPanel = 0f
			}
			else {
				fragmentView?.invalidate()
			}
		}

		textPaint2.color = getThemedColor(Theme.key_chat_messagePanelHint)

		val composeBackgroundPaint = getThemedPaint(Theme.key_paint_chatComposeBackground)
		val oldAlpha = composeBackgroundPaint.alpha
		val oldAlphaText = textPaint2.alpha

		composeBackgroundPaint.alpha = (oldAlpha * progressToBottomPanel).toInt()
		canvas.drawRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat(), composeBackgroundPaint)

		if (layout1 != null && swipeToReleaseProgress < 1f) {
			textPaint2.alpha = (oldAlphaText * (1f - swipeToReleaseProgress) * progressToBottomPanel).toInt()

			val y = top + (bottom - top - layout1!!.height) / 2f - AndroidUtilities.dp(10f) * swipeToReleaseProgress

			canvas.withTranslation((lastWidth - layout1Width) / 2f, y) {
				layout1?.draw(this)
			}
		}

		if (layout2 != null && swipeToReleaseProgress > 0) {
			textPaint2.alpha = (oldAlphaText * swipeToReleaseProgress * progressToBottomPanel).toInt()

			val y = top + (bottom - top - layout2!!.height) / 2f + AndroidUtilities.dp(10f) * (1f - swipeToReleaseProgress)

			canvas.withTranslation((lastWidth - layout2Width) / 2f, y) {
				layout2?.draw(this)
			}
		}

		textPaint2.alpha = oldAlphaText

		composeBackgroundPaint.alpha = oldAlpha
	}

	var showBottomPanel = false

	init {
		arrowPaint.strokeWidth = AndroidUtilities.dpf2(2.8f)
		arrowPaint.strokeCap = Paint.Cap.ROUND

		counterDrawable.gravity = Gravity.LEFT
		counterDrawable.type = CounterDrawable.TYPE_CHAT_PULLING_DOWN
		counterDrawable.addServiceGradient = true
		counterDrawable.circlePaint = getThemedPaint(Theme.key_paint_chatActionBackground)
		counterDrawable.textPaint = textPaint

		textPaint.textSize = AndroidUtilities.dp(13f).toFloat()
		textPaint.setTypeface(Theme.TYPEFACE_BOLD)
		textPaint2.textSize = AndroidUtilities.dp(14f).toFloat()
		textPaint2.setTypeface(Theme.TYPEFACE_DEFAULT)

		xRefPaint.color = -0x1000000
		xRefPaint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.CLEAR))

		updateDialog()
	}

	fun showBottomPanel(b: Boolean) {
		showBottomPanel = b
		fragmentView?.invalidate()
	}

	fun needDrawBottomPanel(): Boolean {
		return (showBottomPanel || progressToBottomPanel > 0) && !emptyStub
	}

	fun animationIsRunning(): Boolean {
		return swipeToReleaseProgress != 1f
	}

	fun runOnAnimationFinish(runnable: Runnable?) {
		showReleaseAnimator?.removeAllListeners()
		showReleaseAnimator?.cancel()

		onAnimationFinishRunnable = runnable

		showReleaseAnimator = AnimatorSet()

		val out = ValueAnimator.ofFloat(swipeToReleaseProgress, 1f)

		out.addUpdateListener {
			swipeToReleaseProgress = it.animatedValue as Float
			fragmentView?.invalidate()
			parentView?.invalidate()
		}

		val bounceOut = ValueAnimator.ofFloat(bounceProgress, 0f)

		bounceOut.addUpdateListener {
			bounceProgress = it.animatedValue as Float
			parentView?.invalidate()
		}

		showReleaseAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				bounceProgress = 0f
				swipeToReleaseProgress = 1f

				parentView?.invalidate()
				fragmentView?.invalidate()

				onAnimationFinishRunnable?.run()
				onAnimationFinishRunnable = null
			}
		})

		showReleaseAnimator?.playTogether(out, bounceOut)
		showReleaseAnimator?.setDuration(120)
		showReleaseAnimator?.interpolator = CubicBezierInterpolator.DEFAULT
		showReleaseAnimator?.start()
	}

	fun reset() {
		checkProgress = 0f
		animateCheck = false
	}

	private fun getThemedColor(key: String): Int {
		return Theme.getColor(key)
	}

	private fun getThemedPaint(paintKey: String): Paint {
		return Theme.getThemePaint(paintKey)
	}

	private fun hasGradientService(): Boolean {
		return Theme.hasGradientService()
	}

	companion object {
		fun getNextUnreadDialog(currentDialogId: Long, folderId: Int, filterId: Int): TLRPC.Dialog? {
			return getNextUnreadDialog(currentDialogId, folderId, filterId, true, null)
		}

		fun getNextUnreadDialog(currentDialogId: Long, folderId: Int, filterId: Int, searchNext: Boolean, params: IntArray?): TLRPC.Dialog? {
			val messagesController = AccountInstance.getInstance(UserConfig.selectedAccount).messagesController

			if (params != null) {
				params[0] = 0
				params[1] = folderId
				params[2] = filterId
			}

			val dialogs: ArrayList<TLRPC.Dialog> = if (filterId != 0) {
				val filter = messagesController.dialogFiltersById[filterId] ?: return null
				filter.dialogs
			}
			else {
				messagesController.getDialogs(folderId)
			}

			for (i in dialogs.indices) {
				val dialog = dialogs[i]
				val chat = messagesController.getChat(-dialog.id)

				if (chat != null && dialog.id != currentDialogId && dialog.unreadCount > 0 && DialogObject.isChannel(dialog) && !chat.megagroup && !messagesController.isPromoDialog(dialog.id, false)) {
					MessagesController.getRestrictionReason(chat.restrictionReason) ?: return dialog
				}
			}

			if (searchNext) {
				if (filterId != 0) {
					for (i in messagesController.dialogFilters.indices) {
						val newFilterId = messagesController.dialogFilters[i].id

						if (filterId != newFilterId) {
							val dialog = getNextUnreadDialog(currentDialogId, folderId, newFilterId, false, params)

							if (dialog != null) {
								if (params != null) {
									params[0] = 1
								}

								return dialog
							}
						}
					}
				}

				for (i in 0 until messagesController.dialogsByFolder.size) {
					val newFolderId = messagesController.dialogsByFolder.keyAt(i)

					if (folderId != newFolderId) {
						val dialog = getNextUnreadDialog(currentDialogId, newFolderId, 0, false, params)

						if (dialog != null) {
							if (params != null) {
								params[0] = 1
							}

							return dialog
						}
					}
				}
			}

			return null
		}
	}
}
