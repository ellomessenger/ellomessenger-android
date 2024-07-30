/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.IntDef
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.Bulletin.Layout.DefaultTransition
import org.telegram.ui.Components.LinkSpanDrawable.LinksTextView
import org.telegram.ui.DialogsActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

@Suppress("LeakingThis")
open class Bulletin {
	private val parentLayout: ParentLayout?
	private val containerLayout: FrameLayout?
	var tag = 0
	var currentBottomOffset = 0
	private var duration = 0
	private var canHide = false
	private var currentDelegate: Delegate? = null
	private var layoutTransition: Layout.Transition? = null

	@JvmField
	val layout: Layout?

	@JvmField
	var hash = 0

	var isShowing = false
		private set

	private constructor() {
		layout = null
		parentLayout = null
		containerLayout = null
	}

	private val hideRunnable = Runnable { this.hide() }

	private constructor(containerLayout: FrameLayout, layout: Layout, duration: Int) {
		this.layout = layout

		parentLayout = object : ParentLayout(layout) {
			override fun onPressedStateChanged(pressed: Boolean) {
				setCanHide(!pressed)
				containerLayout.parent?.requestDisallowInterceptTouchEvent(pressed)
			}

			override fun onHide() {
				hide()
			}
		}

		this.containerLayout = containerLayout
		this.duration = duration
	}

	fun setDuration(duration: Int) {
		this.duration = duration
	}

	open fun show(): Bulletin {
		if (!isShowing && containerLayout != null) {
			isShowing = true

			val text = layout?.accessibilityText

			if (text != null) {
				AndroidUtilities.makeAccessibilityAnnouncement(text)
			}

			check(layout?.parent === parentLayout) {
				"Layout has incorrect parent"
			}

			visibleBulletin?.hide()

			visibleBulletin = this

			layout?.onAttach(this)

			layout?.addOnLayoutChangeListener(object : OnLayoutChangeListener {
				override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
					layout.removeOnLayoutChangeListener(this)

					if (isShowing) {
						layout.onShow()

						currentDelegate = delegates[containerLayout]
						currentBottomOffset = currentDelegate?.getBottomOffset(tag) ?: 0

						currentDelegate?.onShow(this@Bulletin)

						if (isTransitionsEnabled) {
							ensureLayoutTransitionCreated()

							layout.transitionRunningEnter = true
							layout.delegate = currentDelegate
							layout.invalidate()

							layoutTransition?.animateEnter(layout, { layout.onEnterTransitionStart() }, {
								layout.transitionRunningEnter = false
								layout.onEnterTransitionEnd()
								setCanHide(true)
							}, { offset ->
								currentDelegate?.onOffsetChange(layout.height - offset)
							}, currentBottomOffset)
						}
						else {
							currentDelegate?.onOffsetChange((layout.height - currentBottomOffset).toFloat())

							updatePosition()

							layout.onEnterTransitionStart()
							layout.onEnterTransitionEnd()

							setCanHide(true)
						}
					}
				}
			})

			layout?.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
				override fun onViewAttachedToWindow(v: View) {
					// unused
				}

				override fun onViewDetachedFromWindow(v: View) {
					layout.removeOnAttachStateChangeListener(this)
					hide(false, 0)
				}
			})

			containerLayout.addView(parentLayout)
		}

		return this
	}

	private fun setCanHide(canHide: Boolean) {
		if (this.canHide != canHide && layout != null) {
			this.canHide = canHide

			if (canHide) {
				layout.postDelayed(hideRunnable, duration.toLong())
			}
			else {
				layout.removeCallbacks(hideRunnable)
			}
		}
	}

	private fun ensureLayoutTransitionCreated() {
		if (layout != null && layoutTransition == null) {
			layoutTransition = layout.createTransition()
		}
	}

	fun hide(duration: Long) {
		hide(isTransitionsEnabled, duration)
	}

	@JvmOverloads
	fun hide(animated: Boolean = isTransitionsEnabled, duration: Long = 0) {
		if (layout == null) {
			return
		}

		if (isShowing) {
			isShowing = false

			if (visibleBulletin === this) {
				visibleBulletin = null
			}

			val bottomOffset = currentBottomOffset

			currentBottomOffset = 0

			if (ViewCompat.isLaidOut(layout)) {
				layout.removeCallbacks(hideRunnable)

				if (animated) {
					layout.transitionRunningExit = true
					layout.delegate = currentDelegate
					layout.invalidate()

					if (duration >= 0) {
						val transition = DefaultTransition()
						transition.duration = duration
						layoutTransition = transition
					}
					else {
						ensureLayoutTransitionCreated()
					}

					layoutTransition?.animateExit(layout, { layout.onExitTransitionStart() }, {
						currentDelegate?.onOffsetChange(0f)
						currentDelegate?.onHide(this)

						layout.transitionRunningExit = false
						layout.onExitTransitionEnd()
						layout.onHide()

						containerLayout?.removeView(parentLayout)

						layout.onDetach()
					}, { offset ->
						currentDelegate?.onOffsetChange(layout.height - offset)
					}, bottomOffset)

					return
				}
			}

			currentDelegate?.onOffsetChange(0f)
			currentDelegate?.onHide(this)

			layout.onExitTransitionStart()
			layout.onExitTransitionEnd()
			layout.onHide()

			if (containerLayout != null) {
				AndroidUtilities.runOnUIThread {
					containerLayout.removeView(parentLayout)
				}
			}

			layout.onDetach()
		}
	}

	fun updatePosition() {
		layout?.updatePosition()
	}

	@Retention(AnnotationRetention.SOURCE)
	@IntDef(value = [ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT])
	private annotation class WidthDef

	@Retention(AnnotationRetention.SOURCE)
	@SuppressLint("RtlHardcoded")
	@IntDef(value = [Gravity.LEFT, Gravity.RIGHT, Gravity.CENTER_HORIZONTAL, Gravity.NO_GRAVITY])
	private annotation class GravityDef

	interface Delegate {
		fun getBottomOffset(tag: Int): Int {
			return 0
		}

		fun onOffsetChange(offset: Float) {}
		fun onShow(bulletin: Bulletin) {}
		fun onHide(bulletin: Bulletin) {}
	}

	private abstract class ParentLayout(private val layout: Layout) : FrameLayout(layout.context) {
		private val rect = Rect()
		private val gestureDetector: GestureDetector
		private var pressed = false
		private var translationX = 0f
		private var hideAnimationRunning = false
		private var needLeftAlphaAnimation = false
		private var needRightAlphaAnimation = false

		init {
			gestureDetector = GestureDetector(layout.context, object : SimpleOnGestureListener() {
				override fun onDown(e: MotionEvent): Boolean {
					if (!hideAnimationRunning) {
						needLeftAlphaAnimation = layout.isNeedSwipeAlphaAnimation(true)
						needRightAlphaAnimation = layout.isNeedSwipeAlphaAnimation(false)
						return true
					}

					return false
				}

				override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
					layout.translationX = distanceX.let { translationX -= it; translationX }

					if (translationX == 0f || translationX < 0f && needLeftAlphaAnimation || translationX > 0f && needRightAlphaAnimation) {
						layout.setAlpha((1f - abs(translationX.toDouble()) / layout.width).toFloat())
					}

					return true
				}

				override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
					if (abs(velocityX.toDouble()) > 2000f) {
						val needAlphaAnimation = velocityX < 0f && needLeftAlphaAnimation || velocityX > 0f && needRightAlphaAnimation
						val springAnimation = SpringAnimation(layout, DynamicAnimation.TRANSLATION_X, (sign(velocityX.toDouble()) * layout.width * 2f).toFloat())

						if (!needAlphaAnimation) {
							springAnimation.addEndListener { _, _, _, _ ->
								onHide()
							}

							springAnimation.addUpdateListener { animation, value, _ ->
								if (abs(value.toDouble()) > layout.width) {
									animation.cancel()
								}
							}
						}

						springAnimation.spring.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
						springAnimation.spring.setStiffness(100f)
						springAnimation.setStartVelocity(velocityX)
						springAnimation.start()

						if (needAlphaAnimation) {
							val springAnimation2 = SpringAnimation(layout, DynamicAnimation.ALPHA, 0f)

							springAnimation2.addEndListener { _, _, _, _ ->
								onHide()
							}

							springAnimation2.addUpdateListener { animation, value, _ ->
								if (value <= 0f) {
									animation.cancel()
								}
							}

							springAnimation.spring.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
							springAnimation.spring.setStiffness(10f)
							springAnimation.setStartVelocity(velocityX)

							springAnimation2.start()
						}

						hideAnimationRunning = true

						return true
					}
					return false
				}
			})

			gestureDetector.setIsLongpressEnabled(false)

			addView(layout)
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (pressed || inLayoutHitRect(event.x, event.y)) {
				gestureDetector.onTouchEvent(event)

				val actionMasked = event.actionMasked

				if (actionMasked == MotionEvent.ACTION_DOWN) {
					if (!pressed && !hideAnimationRunning) {
						layout.animate().cancel()
						translationX = layout.translationX

						pressed = true

						onPressedStateChanged(true)
					}
				}
				else if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
					if (pressed) {
						if (!hideAnimationRunning) {
							if (abs(translationX.toDouble()) > layout.width / 3f) {
								val tx = (sign(translationX.toDouble()) * layout.width).toFloat()
								val needAlphaAnimation = translationX < 0f && needLeftAlphaAnimation || translationX > 0f && needRightAlphaAnimation

								layout.animate().translationX(tx).alpha(if (needAlphaAnimation) 0f else 1f).setDuration(200).setInterpolator(AndroidUtilities.accelerateInterpolator).withEndAction {
									if (layout.translationX == tx) {
										onHide()
									}
								}.start()
							}
							else {
								layout.animate().translationX(0f).alpha(1f).setDuration(200).start()
							}
						}

						pressed = false

						onPressedStateChanged(false)
					}
				}

				return true
			}

			return false
		}

		private fun inLayoutHitRect(x: Float, y: Float): Boolean {
			layout.getHitRect(rect)
			return rect.contains(x.toInt(), y.toInt())
		}

		protected abstract fun onPressedStateChanged(pressed: Boolean)

		protected abstract fun onHide()
	}

	abstract class Layout(context: Context) : FrameLayout(context) {
		private val callbacks: MutableList<Callback> = ArrayList()
		var transitionRunningEnter = false
		var transitionRunningExit = false
		private var inOutOffset = 0f
		var bulletin: Bulletin? = null
		var delegate: Delegate? = null
		private var background: Drawable? = null

		@WidthDef
		private var wideScreenWidth = ViewGroup.LayoutParams.WRAP_CONTENT

		@GravityDef
		private var wideScreenGravity = Gravity.CENTER_HORIZONTAL

		init {
			setMinimumHeight(AndroidUtilities.dp(48f))
			applyBackgroundColor(context.getColor(R.color.dark_bulletin_background))
			// setBackground(context.getColor(R.color.dark_bulletin_background))
			updateSize()
			setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
			setWillNotDraw(false)
		}

		private val isTransitionRunning: Boolean
			get() = transitionRunningEnter || transitionRunningExit

//		protected fun setBackground(color: Int) {
//			background = Theme.createRoundRectDrawable(AndroidUtilities.dp(6f), color)
//		}

		protected fun applyBackgroundColor(color: Int) {
			background = Theme.createRoundRectDrawable(AndroidUtilities.dp(6f), color)
		}

		override fun onConfigurationChanged(newConfig: Configuration) {
			super.onConfigurationChanged(newConfig)
			updateSize()
		}

		private fun updateSize() {
			val isWideScreen = isWideScreen
			setLayoutParams(LayoutHelper.createFrame(if (isWideScreen) wideScreenWidth else LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, if (isWideScreen) Gravity.BOTTOM or wideScreenGravity else Gravity.BOTTOM))
		}

		private val isWideScreen: Boolean
			get() = AndroidUtilities.isTablet() || AndroidUtilities.displaySize.x >= AndroidUtilities.displaySize.y

		fun setWideScreenParams(@WidthDef width: Int, @GravityDef gravity: Int) {
			var changed = false

			if (wideScreenWidth != width) {
				wideScreenWidth = width
				changed = true
			}

			if (wideScreenGravity != gravity) {
				wideScreenGravity = gravity
				changed = true
			}

			if (isWideScreen && changed) {
				updateSize()
			}
		}

		@SuppressLint("RtlHardcoded")
		fun isNeedSwipeAlphaAnimation(swipeLeft: Boolean): Boolean {
			if (!isWideScreen || wideScreenWidth == ViewGroup.LayoutParams.MATCH_PARENT) {
				return false
			}

			if (wideScreenGravity == Gravity.CENTER_HORIZONTAL) {
				return true
			}

			return if (swipeLeft) {
				wideScreenGravity == Gravity.RIGHT
			}
			else {
				wideScreenGravity != Gravity.RIGHT
			}
		}

		open val accessibilityText: CharSequence?
			get() = null

		val isAttachedToBulletin: Boolean
			get() = bulletin != null

		@CallSuper
		fun onAttach(bulletin: Bulletin) {
			this.bulletin = bulletin

			for (callback in callbacks) {
				callback.onAttach(this, bulletin)
			}
		}

		@CallSuper
		fun onDetach() {
			bulletin = null

			for (callback in callbacks) {
				callback.onDetach(this)
			}
		}

		@CallSuper
		open fun onShow() {
			for (callback in callbacks) {
				callback.onShow(this)
			}
		}

		@CallSuper
		fun onHide() {
			for (callback in callbacks) {
				callback.onHide(this)
			}
		}

		@CallSuper
		fun onEnterTransitionStart() {
			for (callback in callbacks) {
				callback.onEnterTransitionStart(this)
			}
		}

		@CallSuper
		open fun onEnterTransitionEnd() {
			for (callback in callbacks) {
				callback.onEnterTransitionEnd(this)
			}
		}

		@CallSuper
		fun onExitTransitionStart() {
			for (callback in callbacks) {
				callback.onExitTransitionStart(this)
			}
		}

		@CallSuper
		open fun onExitTransitionEnd() {
			for (callback in callbacks) {
				callback.onExitTransitionEnd(this)
			}
		}

		fun addCallback(callback: Callback) {
			callbacks.add(callback)
		}

		fun removeCallback(callback: Callback) {
			callbacks.remove(callback)
		}

		fun updatePosition() {
			var translation = 0f

			delegate?.let {
				translation += it.getBottomOffset(bulletin?.tag ?: 0).toFloat()
			}

			translationY = -translation + inOutOffset
		}

		fun createTransition(): Transition {
			return SpringTransition()
		}

		private fun setInOutOffset(offset: Float) {
			inOutOffset = offset
			updatePosition()
		}

		override fun dispatchDraw(canvas: Canvas) {
			val bulletin = bulletin ?: return
			val delegate = delegate

			background?.setBounds(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), measuredWidth - AndroidUtilities.dp(8f), measuredHeight - AndroidUtilities.dp(8f))

			if (isTransitionRunning && delegate != null) {
				val clipBottom = (parent as View).measuredHeight - delegate.getBottomOffset(bulletin.tag)
				val viewBottom = (y + measuredHeight).toInt()

				canvas.save()
				canvas.clipRect(0, 0, measuredWidth, measuredHeight - (viewBottom - clipBottom))

				background?.draw(canvas)

				super.dispatchDraw(canvas)

				canvas.restore()

				invalidate()
			}
			else {
				background?.draw(canvas)
				super.dispatchDraw(canvas)
			}
		}

		interface Callback {
			fun onAttach(layout: Layout, bulletin: Bulletin) {}
			fun onDetach(layout: Layout) {}
			fun onShow(layout: Layout) {}
			fun onHide(layout: Layout) {}
			fun onEnterTransitionStart(layout: Layout) {}
			fun onEnterTransitionEnd(layout: Layout) {}
			fun onExitTransitionStart(layout: Layout) {}
			fun onExitTransitionEnd(layout: Layout) {}
		}

		interface Transition {
			fun animateEnter(layout: Layout, startAction: Runnable?, endAction: Runnable?, onUpdate: Consumer<Float>?, bottomOffset: Int)
			fun animateExit(layout: Layout, startAction: Runnable?, endAction: Runnable?, onUpdate: Consumer<Float>?, bottomOffset: Int)
		}

		class DefaultTransition : Transition {
			var duration: Long = 255

			override fun animateEnter(layout: Layout, startAction: Runnable?, endAction: Runnable?, onUpdate: Consumer<Float>?, bottomOffset: Int) {
				layout.setInOutOffset(layout.measuredHeight.toFloat())

				onUpdate?.accept(layout.translationY)

				val animator = ObjectAnimator.ofFloat(layout, IN_OUT_OFFSET_Y2, 0f)
				animator.setDuration(duration)
				animator.interpolator = Easings.easeOutQuad

				if (startAction != null || endAction != null) {
					animator.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationStart(animation: Animator) {
							startAction?.run()
						}

						override fun onAnimationEnd(animation: Animator) {
							endAction?.run()
						}
					})
				}

				if (onUpdate != null) {
					animator.addUpdateListener {
						onUpdate.accept(layout.translationY)
					}
				}

				animator.start()
			}

			override fun animateExit(layout: Layout, startAction: Runnable?, endAction: Runnable?, onUpdate: Consumer<Float>?, bottomOffset: Int) {
				val animator = ObjectAnimator.ofFloat(layout, IN_OUT_OFFSET_Y2, layout.height.toFloat())
				animator.setDuration(175)
				animator.interpolator = Easings.easeInQuad

				if (startAction != null || endAction != null) {
					animator.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationStart(animation: Animator) {
							startAction?.run()
						}

						override fun onAnimationEnd(animation: Animator) {
							endAction?.run()
						}
					})
				}

				if (onUpdate != null) {
					animator.addUpdateListener {
						onUpdate.accept(layout.translationY)
					}
				}

				animator.start()
			}
		}

		class SpringTransition : Transition {
			override fun animateEnter(layout: Layout, startAction: Runnable?, endAction: Runnable?, onUpdate: Consumer<Float>?, bottomOffset: Int) {
				layout.setInOutOffset(layout.measuredHeight.toFloat())

				onUpdate?.accept(layout.translationY)

				val springAnimation = SpringAnimation(layout, IN_OUT_OFFSET_Y, 0f)
				springAnimation.spring.setDampingRatio(DAMPING_RATIO)
				springAnimation.spring.setStiffness(STIFFNESS)

				if (endAction != null) {
					springAnimation.addEndListener { _, canceled, _, _ ->
						layout.setInOutOffset(0f)

						if (!canceled) {
							endAction.run()
						}
					}
				}

				if (onUpdate != null) {
					springAnimation.addUpdateListener { _, _, _ ->
						onUpdate.accept(layout.translationY)
					}
				}

				springAnimation.start()

				startAction?.run()
			}

			override fun animateExit(layout: Layout, startAction: Runnable?, endAction: Runnable?, onUpdate: Consumer<Float>?, bottomOffset: Int) {
				val springAnimation = SpringAnimation(layout, IN_OUT_OFFSET_Y, layout.height.toFloat())
				springAnimation.spring.setDampingRatio(DAMPING_RATIO)
				springAnimation.spring.setStiffness(STIFFNESS)

				if (endAction != null) {
					springAnimation.addEndListener { _, canceled, _, _ ->
						if (!canceled) {
							endAction.run()
						}
					}
				}

				if (onUpdate != null) {
					springAnimation.addUpdateListener { _, _, _ ->
						onUpdate.accept(layout.translationY)
					}
				}

				springAnimation.start()

				startAction?.run()
			}

			companion object {
				private const val DAMPING_RATIO = 0.8f
				private const val STIFFNESS = 400f
			}
		}

		companion object {
			val IN_OUT_OFFSET_Y = object : FloatPropertyCompat<Layout>("offsetY") {
				override fun getValue(`object`: Layout): Float {
					return `object`.inOutOffset
				}

				override fun setValue(`object`: Layout, value: Float) {
					`object`.setInOutOffset(value)
				}
			}

			val IN_OUT_OFFSET_Y2 = object : AnimationProperties.FloatProperty<Layout>("offsetY") {
				override operator fun get(layout: Layout): Float {
					return layout.inOutOffset
				}

				override fun setValue(`object`: Layout, value: Float) {
					`object`.setInOutOffset(value)
				}
			}
		}
	}

	@SuppressLint("ViewConstructor")
	open class ButtonLayout(context: Context) : Layout(context) {
		private var button: Button? = null
		private var childrenMeasuredWidth = 0

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			childrenMeasuredWidth = 0

			super.onMeasure(widthMeasureSpec, heightMeasureSpec)

			val button = button

			if (button != null && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
				setMeasuredDimension(childrenMeasuredWidth + button.measuredWidth, measuredHeight)
			}
		}

		override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int, parentHeightMeasureSpec: Int, heightUsed: Int) {
			@Suppress("NAME_SHADOWING") var widthUsed = widthUsed
			val button = button

			if (button != null && child !== button) {
				widthUsed += button.measuredWidth - AndroidUtilities.dp(12f)
			}

			super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)

			if (child !== button) {
				val lp = child.layoutParams as MarginLayoutParams
				childrenMeasuredWidth = max(childrenMeasuredWidth.toDouble(), (lp.leftMargin + lp.rightMargin + child.measuredWidth).toDouble()).toInt()
			}
		}

		fun getButton(): Button? {
			return button
		}

		fun setButton(button: Button?) {
			this.button?.let {
				removeCallback(it)
				removeView(it)
			}

			this.button = button

			if (button != null) {
				addCallback(button)
				addView(button, 0, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL))
			}
		}
	}

	open class SimpleLayout(context: Context) : ButtonLayout(context) {
		@JvmField
		val imageView: ImageView

		@JvmField
		val textView: TextView

		init {
			val undoInfoColor = context.getColor(R.color.white)

			imageView = ImageView(context)
			imageView.colorFilter = PorterDuffColorFilter(undoInfoColor, PorterDuff.Mode.MULTIPLY)

			addView(imageView, LayoutHelper.createFrameRelatively(24f, 24f, Gravity.START or Gravity.CENTER_VERTICAL, 16f, 12f, 16f, 12f))

			textView = TextView(context)
			textView.setSingleLine()
			textView.setTextColor(undoInfoColor)
			textView.setTypeface(Theme.TYPEFACE_DEFAULT)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)

			addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 56f, 0f, 16f, 0f))
		}

		override val accessibilityText: CharSequence?
			get() = textView.getText()
	}

	@SuppressLint("ViewConstructor")
	open class MultiLineLayout(context: Context) : ButtonLayout(context) {
		@JvmField
		val imageView = BackupImageView(getContext())

		@JvmField
		val textView = TextView(getContext())

		init {
			addView(imageView, LayoutHelper.createFrameRelatively(30f, 30f, Gravity.START or Gravity.CENTER_VERTICAL, 12f, 8f, 12f, 8f))

			textView.setGravity(Gravity.START)
			textView.setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))
			textView.setTextColor(context.getColor(R.color.white))
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			textView.setTypeface(Theme.TYPEFACE_DEFAULT)

			addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 56f, 0f, 16f, 0f))
		}

		override val accessibilityText: CharSequence?
			get() = textView.getText()
	}

	@SuppressLint("ViewConstructor")
	open class TwoLineLayout(context: Context) : ButtonLayout(context) {
		@JvmField
		val imageView: BackupImageView

		@JvmField
		val titleTextView: TextView

		@JvmField
		val subtitleTextView: TextView

		init {
			val undoInfoColor = context.getColor(R.color.white)

			imageView = BackupImageView(context)

			addView(imageView, LayoutHelper.createFrameRelatively(29f, 29f, Gravity.START or Gravity.CENTER_VERTICAL, 12f, 12f, 12f, 12f))

			val linearLayout = LinearLayout(context)
			linearLayout.orientation = LinearLayout.VERTICAL

			addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 54f, 8f, 12f, 8f))

			titleTextView = TextView(context)
			titleTextView.setSingleLine()
			titleTextView.setTextColor(undoInfoColor)
			titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			titleTextView.setTypeface(Theme.TYPEFACE_BOLD)

			linearLayout.addView(titleTextView)

			subtitleTextView = TextView(context)
			subtitleTextView.setMaxLines(2)
			subtitleTextView.setTextColor(undoInfoColor)
			subtitleTextView.setLinkTextColor(context.getColor(R.color.avatar_tint))
			subtitleTextView.movementMethod = LinkMovementMethod()
			subtitleTextView.setTypeface(Theme.TYPEFACE_DEFAULT)
			subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)

			linearLayout.addView(subtitleTextView)
		}

		override val accessibilityText: CharSequence?
			get() = """
                ${titleTextView.getText()}.
                ${subtitleTextView.getText()}
                """.trimIndent()
	}

	class TwoLineLottieLayout(context: Context) : ButtonLayout(context) {
		val imageView: RLottieImageView
		val titleTextView: TextView
		val subtitleTextView: TextView
		private val textColor: Int

		init {
			textColor = context.getColor(R.color.white)

			// setBackground(context.getColor(R.color.dark_bulletin_background))
			applyBackgroundColor(context.getColor(R.color.dark_bulletin_background))

			imageView = RLottieImageView(context)
			imageView.setScaleType(ImageView.ScaleType.CENTER)

			addView(imageView, LayoutHelper.createFrameRelatively(56f, 48f, Gravity.START or Gravity.CENTER_VERTICAL))

			val undoInfoColor = context.getColor(R.color.white)
			val undoLinkColor = context.getColor(R.color.avatar_tint)

			val linearLayout = LinearLayout(context)
			linearLayout.orientation = LinearLayout.VERTICAL

			addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 56f, 8f, 12f, 8f))

			titleTextView = TextView(context)
			titleTextView.setSingleLine()
			titleTextView.setTextColor(undoInfoColor)
			titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			titleTextView.setTypeface(Theme.TYPEFACE_BOLD)

			linearLayout.addView(titleTextView)

			subtitleTextView = TextView(context)
			subtitleTextView.setTextColor(undoInfoColor)
			subtitleTextView.setLinkTextColor(undoLinkColor)
			subtitleTextView.setTypeface(Theme.TYPEFACE_DEFAULT)
			subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)

			linearLayout.addView(subtitleTextView)
		}

		override fun onShow() {
			super.onShow()
			imageView.playAnimation()
		}

		fun setAnimation(resId: Int, vararg layers: String?) {
			setAnimation(resId, 32, 32, *layers)
		}

		fun setAnimation(resId: Int, w: Int, h: Int, vararg layers: String?) {
			imageView.setAnimation(resId, w, h)

			for (layer in layers) {
				imageView.setLayerColor("$layer.**", textColor)
			}
		}

		override val accessibilityText: CharSequence
			get() = """
                ${titleTextView.getText()}.
                ${subtitleTextView.getText()}
                """.trimIndent()
	}

	class LottieLayout(context: Context) : ButtonLayout(context) {
		val imageView: RLottieImageView
		val textView: TextView
		private var textColor = 0

		init {
			imageView = RLottieImageView(context)
			imageView.setScaleType(ImageView.ScaleType.CENTER)

			addView(imageView, LayoutHelper.createFrameRelatively(56f, 48f, Gravity.START or Gravity.CENTER_VERTICAL))

			textView = LinksTextView(context)
			textView.setSingleLine()
			textView.setTypeface(Theme.TYPEFACE_DEFAULT)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			textView.ellipsize = TextUtils.TruncateAt.END
			textView.setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))

			addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 56f, 0f, 8f, 0f))

			textView.setLinkTextColor(context.getColor(R.color.avatar_tint))

			setTextColor(context.getColor(R.color.white))

			// setBackground(context.getColor(R.color.dark_bulletin_background))
			applyBackgroundColor(context.getColor(R.color.dark_bulletin_background))
		}

		constructor(context: Context, backgroundColor: Int, textColor: Int) : this(context) {
			// setBackground(backgroundColor)
			applyBackgroundColor(backgroundColor)
			setTextColor(textColor)
		}

		fun setTextColor(textColor: Int) {
			this.textColor = textColor
			textView.setTextColor(textColor)
		}

		override fun onShow() {
			super.onShow()
			imageView.playAnimation()
		}

		fun setAnimation(resId: Int, vararg layers: String?) {
			setAnimation(resId, 32, 32, *layers)
		}

		fun setAnimation(resId: Int, w: Int, h: Int, vararg layers: String?) {
			imageView.setAnimation(resId, w, h)

			for (layer in layers) {
				imageView.setLayerColor("$layer.**", textColor)
			}
		}

		fun setAnimation(document: TLRPC.Document?, w: Int, h: Int, vararg layers: String) {
			imageView.setAnimation(document, w, h)

			for (layer in layers) {
				imageView.setLayerColor("$layer.**", textColor)
			}
		}

		fun setIconPaddingBottom(paddingBottom: Int) {
			imageView.setLayoutParams(LayoutHelper.createFrameRelatively(56f, (48 - paddingBottom).toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 0f, 0f, 0f, paddingBottom.toFloat()))
		}

		override val accessibilityText: CharSequence?
			get() = textView.getText()
	}

	@SuppressLint("ViewConstructor")
	abstract class Button(context: Context) : FrameLayout(context), Layout.Callback {
		override fun onAttach(layout: Layout, bulletin: Bulletin) {}
		override fun onDetach(layout: Layout) {}
		override fun onShow(layout: Layout) {}
		override fun onHide(layout: Layout) {}
		override fun onEnterTransitionStart(layout: Layout) {}
		override fun onEnterTransitionEnd(layout: Layout) {}
		override fun onExitTransitionStart(layout: Layout) {}
		override fun onExitTransitionEnd(layout: Layout) {}
	}

	@SuppressLint("ViewConstructor")
	class UndoButton(context: Context, text: Boolean) : Button(context) {
		private var undoAction: Runnable? = null
		private var delayedAction: Runnable? = null
		private var bulletin: Bulletin? = null
		private var undoTextView: TextView? = null
		private var isUndone = false

		init {
			val undoCancelColor = context.getColor(R.color.avatar_tint)
			if (text) {
				undoTextView = TextView(context)

				undoTextView?.setOnClickListener {
					undo()
				}

				val leftInset = if (LocaleController.isRTL) AndroidUtilities.dp(16f) else 0
				val rightInset = if (LocaleController.isRTL) 0 else AndroidUtilities.dp(16f)

				undoTextView?.background = Theme.createCircleSelectorDrawable(undoCancelColor and 0x00ffffff or 0x19000000, leftInset, rightInset)
				undoTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				undoTextView?.setTypeface(Theme.TYPEFACE_BOLD)
				undoTextView?.setTextColor(undoCancelColor)
				undoTextView?.text = context.getString(R.string.Undo)
				undoTextView?.setGravity(Gravity.CENTER_VERTICAL)

				ViewHelper.setPaddingRelative(undoTextView, 16f, 0f, 16f, 0f)

				addView(undoTextView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), 48f, Gravity.CENTER_VERTICAL, 8f, 0f, 0f, 0f))
			}
			else {
				val undoImageView = ImageView(getContext())

				undoImageView.setOnClickListener {
					undo()
				}

				undoImageView.setImageResource(R.drawable.chats_undo)
				undoImageView.colorFilter = PorterDuffColorFilter(undoCancelColor, PorterDuff.Mode.MULTIPLY)
				undoImageView.background = Theme.createSelectorDrawable(undoCancelColor and 0x00ffffff or 0x19000000)

				ViewHelper.setPaddingRelative(undoImageView, 0f, 12f, 0f, 12f)

				addView(undoImageView, LayoutHelper.createFrameRelatively(56f, 48f, Gravity.CENTER_VERTICAL))
			}
		}

		fun setText(text: CharSequence?): UndoButton {
			undoTextView?.text = text
			return this
		}

		fun undo() {
			if (bulletin != null) {
				isUndone = true
				undoAction?.run()
				bulletin?.hide()
			}
		}

		override fun onAttach(layout: Layout, bulletin: Bulletin) {
			this.bulletin = bulletin
		}

		override fun onDetach(layout: Layout) {
			bulletin = null

			if (!isUndone) {
				delayedAction?.run()
			}
		}

		fun setUndoAction(undoAction: Runnable?): UndoButton {
			this.undoAction = undoAction
			return this
		}

		fun setDelayedAction(delayedAction: Runnable?): UndoButton {
			this.delayedAction = delayedAction
			return this
		}
	}

	class EmptyBulletin : Bulletin() {
		override fun show(): Bulletin {
			return this
		}
	}

	companion object {
		const val DURATION_SHORT = 1500
		const val DURATION_LONG = 2750
		const val TYPE_STICKER = 0
		const val TYPE_ERROR = 1
		const val TYPE_BIO_CHANGED = 2
		const val TYPE_NAME_CHANGED = 3
		const val TYPE_ERROR_SUBTITLE = 4
		const val TYPE_APP_ICON = 5
		private val delegates = mutableMapOf<FrameLayout, Delegate>()

		@JvmStatic
		@SuppressLint("StaticFieldLeak")
		var visibleBulletin: Bulletin? = null
			private set

		@JvmStatic
		fun make(containerLayout: FrameLayout, contentLayout: Layout, duration: Int): Bulletin {
			return Bulletin(containerLayout, contentLayout, duration)
		}

		@JvmStatic
		@SuppressLint("RtlHardcoded")
		fun make(fragment: BaseFragment, contentLayout: Layout, duration: Int): Bulletin {
			if (fragment is ChatActivity) {
				contentLayout.setWideScreenParams(ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT)
			}
			else if (fragment is DialogsActivity) {
				contentLayout.setWideScreenParams(ViewGroup.LayoutParams.MATCH_PARENT, Gravity.NO_GRAVITY)
			}

			return Bulletin(fragment.getLayoutContainer(), contentLayout, duration)
		}

		fun find(containerLayout: FrameLayout): Bulletin? {
			for (view in containerLayout.children) {
				if (view is Layout) {
					return view.bulletin
				}
			}

			return null
		}

		@JvmOverloads
		@JvmStatic
		fun hide(containerLayout: FrameLayout, animated: Boolean = true) {
			val bulletin = find(containerLayout)
			bulletin?.hide(animated && isTransitionsEnabled, 0)
		}

		fun hideVisible() {
			visibleBulletin?.hide()
		}

		private val isTransitionsEnabled: Boolean
			get() = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true)

		fun addDelegate(fragment: BaseFragment, delegate: Delegate) {
			val containerLayout = fragment.getLayoutContainer()

			if (containerLayout != null) {
				addDelegate(containerLayout, delegate)
			}
		}

		@JvmStatic
		fun addDelegate(containerLayout: FrameLayout, delegate: Delegate) {
			delegates[containerLayout] = delegate
		}

		fun removeDelegate(fragment: BaseFragment) {
			val containerLayout = fragment.getLayoutContainer()

			if (containerLayout != null) {
				removeDelegate(containerLayout)
			}
		}

		@JvmStatic
		fun removeDelegate(containerLayout: FrameLayout) {
			delegates.remove(containerLayout)
		}
	}
}
