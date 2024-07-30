/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createFrame
import kotlin.math.max
import kotlin.math.min

class ChatActivityBotWebViewButton(context: Context) : FrameLayout(context) {
	private val path = Path()
	private var progress = 0f
	private var buttonColor = ResourcesCompat.getColor(resources, R.color.brand, null)
	private var backgroundColor = 0
	private var menuButtonWidth = 0
	private val textView: TextView
	private val progressView: RadialProgressView
	private val rippleView: View
	private var progressWasVisible = false
	private var menuButton: BotCommandsMenuView? = null

	init {
		textView = TextView(context)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.setSingleLine()
		textView.alpha = 0f
		textView.gravity = Gravity.CENTER
		textView.typeface = Theme.TYPEFACE_BOLD

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT, 0f, 0f, 0f, 0f))

		progressView = RadialProgressView(context)
		progressView.setSize(AndroidUtilities.dp(18f))
		progressView.alpha = 0f
		progressView.scaleX = 0f
		progressView.scaleY = 0f

		addView(progressView, createFrame(28, 28f, Gravity.RIGHT or Gravity.CENTER_VERTICAL, 0f, 0f, 12f, 0f))

		rippleView = View(context)
		rippleView.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(resources, R.color.brand, null), 2)

		addView(rippleView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT, 0f, 0f, 0f, 0f))

		setWillNotDraw(false)
	}

	fun setBotMenuButton(menuButton: BotCommandsMenuView?) {
		this.menuButton = menuButton
		invalidate()
	}

	fun setupButtonParams(isActive: Boolean, text: String?, color: Int, textColor: Int, isProgressVisible: Boolean) {
		isClickable = isActive

		rippleView.visibility = if (isActive) VISIBLE else GONE

		textView.text = text
		textView.setTextColor(textColor)

		buttonColor = color

		rippleView.background = Theme.createSelectorDrawable(BotWebViewContainer.getMainButtonRippleColor(buttonColor), 2)

		progressView.setProgressColor(textColor)

		if (progressWasVisible != isProgressVisible) {
			progressWasVisible = isProgressVisible

			progressView.animate().cancel()

			if (isProgressVisible) {
				progressView.alpha = 0f
				progressView.visibility = VISIBLE
			}

			progressView.animate().alpha(if (isProgressVisible) 1f else 0f).scaleX(if (isProgressVisible) 1f else 0.1f).scaleY(if (isProgressVisible) 1f else 0.1f).setDuration(250).setListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (!isProgressVisible) {
						progressView.visibility = GONE
					}
				}
			}).start()
		}

		invalidate()
	}

	fun setProgress(progress: Float) {
		this.progress = progress

		backgroundColor = ColorUtils.blendARGB(ResourcesCompat.getColor(resources, R.color.feed_audio_background, null), buttonColor, progress)

		children.forEach {
			it.alpha = progress
		}

		invalidate()
	}

	fun setMeasuredButtonWidth(width: Int) {
		menuButtonWidth = width
		invalidate()
	}

	override fun draw(canvas: Canvas) {
		canvas.save()

		val menuY = (height - AndroidUtilities.dp(32f)) / 2f
		val offset = max(width - menuButtonWidth - AndroidUtilities.dp(4f), height) * progress
		val rad = AndroidUtilities.dp(16f) + offset

		AndroidUtilities.rectTmp[AndroidUtilities.dp(14f) - offset, menuY + AndroidUtilities.dp(4f) - offset, AndroidUtilities.dp(6f) + menuButtonWidth + offset] = height - AndroidUtilities.dp(12f) + offset

		path.rewind()
		path.addRoundRect(AndroidUtilities.rectTmp, rad, rad, Path.Direction.CW)

		canvas.clipPath(path)
		canvas.drawColor(backgroundColor)
		canvas.saveLayerAlpha(AndroidUtilities.rectTmp, ((1f - min(0.5f, progress) / 0.5f) * 0xFF).toInt())
		canvas.translate(AndroidUtilities.dp(10f).toFloat(), menuY)

		menuButton?.let {
			it.setDrawBackgroundDrawable(false)
			it.draw(canvas)
			it.setDrawBackgroundDrawable(true)
		}

		canvas.restore()
		canvas.translate(-AndroidUtilities.dp(8f) * (1f - progress), 0f)

		super.draw(canvas)

		canvas.restore()
	}

	companion object {
		@JvmField
		val PROGRESS_PROPERTY = SimpleFloatPropertyCompat("progress", { obj: ChatActivityBotWebViewButton -> obj.progress }) { obj: ChatActivityBotWebViewButton, progress: Float ->
			obj.setProgress(progress)
		}.setMultiplier(100f)
	}
}
