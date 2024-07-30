/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components.voip

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EllipsizeSpanAnimator
import org.telegram.ui.Components.LayoutHelper

@Suppress("JoinDeclarationAndAssignment")
class VoIPStatusTextView(context: Context) : FrameLayout(context) {
	private val textView: Array<TextView>
	private val reconnectTextView: TextView
	private val timerView: VoIPTimerView
	private var nextTextToSet: CharSequence? = null
	private var attachedToWindow = false
	private var animator: ValueAnimator? = null
	private var timerShowing = false
	private var animationInProgress = false
	private val ellipsizeAnimator: EllipsizeSpanAnimator

	init {
		textView = (0..1).map {
			val textView = TextView(context)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			textView.setShadowLayer(AndroidUtilities.dp(3f).toFloat(), 0f, AndroidUtilities.dp(0.6666667f).toFloat(), 0x4C000000)
			textView.setTextColor(Color.WHITE)
			textView.gravity = Gravity.CENTER_HORIZONTAL

			addView(textView)

			textView
		}.toTypedArray()

		reconnectTextView = TextView(context)
		reconnectTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		reconnectTextView.setShadowLayer(AndroidUtilities.dp(3f).toFloat(), 0f, AndroidUtilities.dp(0.6666667f).toFloat(), 0x4C000000)
		reconnectTextView.setTextColor(Color.WHITE)
		reconnectTextView.gravity = Gravity.CENTER_HORIZONTAL

		addView(reconnectTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 0f, 22f, 0f, 0f))

		ellipsizeAnimator = EllipsizeSpanAnimator(this)

		val ssb = SpannableStringBuilder(context.getString(R.string.VoipReconnecting))
		val ell = SpannableString("...")

		ellipsizeAnimator.wrap(ell, 0)

		ssb.append(ell)

		reconnectTextView.text = ssb
		reconnectTextView.visibility = GONE

		timerView = VoIPTimerView(context)

		addView(timerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
	}

	fun setText(text: String, ellipsis: Boolean, animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated
		var nextString: CharSequence = text

		if (ellipsis) {
			val ssb = SpannableStringBuilder(text)

			ellipsizeAnimator.reset()

			val ell = SpannableString("...")

			ellipsizeAnimator.wrap(ell, 0)

			ssb.append(ell)

			nextString = ssb

			ellipsizeAnimator.addView(textView[0])
			ellipsizeAnimator.addView(textView[1])
		}
		else {
			ellipsizeAnimator.removeView(textView[0])
			ellipsizeAnimator.removeView(textView[1])
		}

		if (textView[0].text.isNullOrEmpty()) {
			animated = false
		}

		if (!animated) {
			animator?.cancel()

			animationInProgress = false

			textView[0].text = nextString
			textView[0].visibility = VISIBLE
			textView[1].visibility = GONE

			timerView.visibility = GONE
		}
		else {
			if (animationInProgress) {
				nextTextToSet = nextString
				return
			}

			if (timerShowing) {
				textView[0].text = nextString
				replaceViews(timerView, textView[0], null)
			}
			else {
				if (textView[0].text != nextString) {
					textView[1].text = nextString

					replaceViews(textView[0], textView[1]) {
						val v = textView[0]

						textView[0] = textView[1]
						textView[1] = v
					}
				}
			}
		}
	}

	fun showTimer(animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (textView[0].text.isNullOrEmpty()) {
			animated = false
		}

		if (timerShowing) {
			return
		}

		timerView.updateTimer()

		if (!animated) {
			animator?.cancel()

			timerShowing = true
			animationInProgress = false

			textView[0].visibility = GONE
			textView[1].visibility = GONE

			timerView.visibility = VISIBLE
		}
		else {
			if (animationInProgress) {
				nextTextToSet = "timer"
				return
			}

			timerShowing = true

			replaceViews(textView[0], timerView, null)
		}

		ellipsizeAnimator.removeView(textView[0])
		ellipsizeAnimator.removeView(textView[1])
	}

	private fun replaceViews(out: View, `in`: View, onEnd: Runnable?) {
		out.visibility = VISIBLE
		`in`.visibility = VISIBLE

		`in`.translationY = AndroidUtilities.dp(15f).toFloat()
		`in`.alpha = 0f

		animationInProgress = true

		animator = ValueAnimator.ofFloat(0f, 1f)

		animator?.addUpdateListener {
			val v = it.animatedValue as Float
			val inScale = 0.4f + 0.6f * v
			val outScale = 0.4f + 0.6f * (1f - v)

			`in`.translationY = AndroidUtilities.dp(10f) * (1f - v)
			`in`.alpha = v
			`in`.scaleX = inScale
			`in`.scaleY = inScale

			out.translationY = -AndroidUtilities.dp(10f) * v
			out.alpha = 1f - v
			out.scaleX = outScale
			out.scaleY = outScale
		}

		animator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				out.visibility = GONE
				out.alpha = 1f
				out.translationY = 0f
				out.scaleY = 1f
				out.scaleX = 1f

				`in`.alpha = 1f
				`in`.translationY = 0f
				`in`.visibility = VISIBLE
				`in`.scaleY = 1f
				`in`.scaleX = 1f

				onEnd?.run()

				animationInProgress = false

				if (nextTextToSet != null) {
					if (nextTextToSet == "timer") {
						showTimer(true)
					}
					else {
						textView[1].text = nextTextToSet

						replaceViews(textView[0], textView[1]) {
							val v = textView[0]

							textView[0] = textView[1]
							textView[1] = v
						}
					}

					nextTextToSet = null
				}
			}
		})

		animator?.setDuration(250)?.interpolator = CubicBezierInterpolator.DEFAULT
		animator?.start()
	}

	fun setSignalBarCount(count: Int) {
		timerView.setSignalBarCount(count)
	}

	fun showReconnect(showReconnecting: Boolean, animated: Boolean) {
		if (!animated) {
			reconnectTextView.animate().setListener(null).cancel()
			reconnectTextView.visibility = if (showReconnecting) VISIBLE else GONE
		}
		else {
			if (showReconnecting) {
				if (reconnectTextView.visibility != VISIBLE) {
					reconnectTextView.visibility = VISIBLE
					reconnectTextView.alpha = 0f
				}

				reconnectTextView.animate().setListener(null).cancel()
				reconnectTextView.animate().alpha(1f).setDuration(150).start()
			}
			else {
				reconnectTextView.animate().alpha(0f).setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						reconnectTextView.visibility = GONE
					}
				}).setDuration(150).start()
			}
		}

		if (showReconnecting) {
			ellipsizeAnimator.addView(reconnectTextView)
		}
		else {
			ellipsizeAnimator.removeView(reconnectTextView)
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		attachedToWindow = true
		ellipsizeAnimator.onAttachedToWindow()
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attachedToWindow = false
		ellipsizeAnimator.onDetachedFromWindow()
	}
}
