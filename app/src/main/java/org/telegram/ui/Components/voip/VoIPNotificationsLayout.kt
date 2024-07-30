/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components.voip

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.TransitionValues
import android.transition.Visibility
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.utils.gone
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper

class VoIPNotificationsLayout(context: Context) : LinearLayout(context) {
	private val viewsByTag = mutableMapOf<String, NotificationView>()
	private val viewToAdd = mutableListOf<NotificationView>()
	private val viewToRemove = mutableListOf<NotificationView>()
	private var lockAnimation = false
	private var wasChanged = false
	private var onViewsUpdated: Runnable? = null
	var transitionSet: TransitionSet

	init {
		orientation = VERTICAL
		transitionSet = TransitionSet()

		transitionSet.addTransition(Fade(Fade.OUT).setDuration(150)).addTransition(ChangeBounds().setDuration(200)).addTransition(object : Visibility() {
			override fun onAppear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues?, endValues: TransitionValues?): Animator {
				val set = AnimatorSet()
				view.alpha = 0f
				set.playTogether(ObjectAnimator.ofFloat(view, ALPHA, 0f, 1f), ObjectAnimator.ofFloat(view, TRANSLATION_Y, view.measuredHeight.toFloat(), 0f))
				set.interpolator = CubicBezierInterpolator.DEFAULT
				return set
			}
		}.setDuration(200))

		transitionSet.ordering = TransitionSet.ORDERING_TOGETHER
	}

	fun addNotification(iconRes: Int, text: String?, tag: String, animated: Boolean) {
		if (viewsByTag[tag] != null) {
			return
		}

		val view = NotificationView(context)
		view.tag = tag
		view.iconView.setImageResource(iconRes)
		view.textView.text = text

		viewsByTag[tag] = view

		if (animated) {
			view.startAnimation()
		}

		if (lockAnimation) {
			viewToAdd.add(view)
		}
		else {
			wasChanged = true
			addView(view, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 0, 4))
		}
	}

	fun removeNotification(tag: String?) {
		val view = viewsByTag.remove(tag) ?: return

		if (lockAnimation) {
			if (viewToAdd.remove(view)) {
				return
			}

			viewToRemove.add(view)
		}
		else {
			wasChanged = true
			removeView(view)
		}
	}

	private fun lock() {
		lockAnimation = true

		AndroidUtilities.runOnUIThread({
			lockAnimation = false
			runDelayed()
		}, 700)
	}

	private fun runDelayed() {
		if (viewToAdd.isEmpty() && viewToRemove.isEmpty()) {
			return
		}

		val parent = parent

		if (parent != null) {
			TransitionManager.beginDelayedTransition(this, transitionSet)
		}

		var i = 0

		while (i < viewToAdd.size) {
			val view = viewToAdd[i]

			for (j in viewToRemove.indices) {
				if (view.tag == viewToRemove[j].tag) {
					viewToAdd.removeAt(i)
					viewToRemove.removeAt(j)
					i--
					break
				}
			}

			i++
		}

		viewToAdd.forEach {
			addView(it, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 0, 4))
		}

		viewToRemove.forEach {
			removeView(it)
		}

		viewsByTag.clear()

		for (v in children) {
			(v as? NotificationView)?.tag?.let {
				viewsByTag[it] = v
			}
		}

		viewToAdd.clear()
		viewToRemove.clear()

		lock()

		onViewsUpdated?.run()
	}

	fun beforeLayoutChanges() {
		wasChanged = false

		if (!lockAnimation) {
			val parent = parent

			if (parent != null) {
				TransitionManager.beginDelayedTransition(this, transitionSet)
			}
		}
	}

	fun animateLayoutChanges() {
		if (wasChanged) {
			lock()
		}

		wasChanged = false
	}

	val childrenHeight: Int
		get() {
			val n = childCount
			return (if (n > 0) AndroidUtilities.dp(16f) else 0) + n * AndroidUtilities.dp(32f)
		}

	class NotificationView(context: Context) : FrameLayout(context) {
		var tag: String? = null
		var iconView: ImageView
		var textView: TextView

		init {
			isFocusable = true
			isFocusableInTouchMode = true
			iconView = ImageView(context)
			background = Theme.createRoundRectDrawable(AndroidUtilities.dp(16f), ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.4f).toInt()))

			addView(iconView, LayoutHelper.createFrame(24, 24f, 0, 10f, 4f, 10f, 4f))

			textView = TextView(context)
			textView.setTextColor(Color.WHITE)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)

			addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, 44f, 4f, 16f, 4f))
		}

		fun startAnimation() {
			textView.gone()

			postDelayed({
				val transitionSet = TransitionSet()
				transitionSet.addTransition(Fade(Fade.IN).setDuration(150)).addTransition(ChangeBounds().setDuration(200))
				transitionSet.ordering = TransitionSet.ORDERING_TOGETHER

				val parent = parent

				if (parent != null) {
					TransitionManager.beginDelayedTransition(parent as ViewGroup, transitionSet)
				}

				textView.visibility = VISIBLE
			}, 400)
		}
	}

	fun setOnViewsUpdated(onViewsUpdated: Runnable?) {
		this.onViewsUpdated = onViewsUpdated
	}
}
