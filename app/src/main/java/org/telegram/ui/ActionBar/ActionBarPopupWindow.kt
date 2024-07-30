/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.ActionBar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.annotation.Keep
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.PopupSwipeBackLayout
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.max
import kotlin.math.min

open class ActionBarPopupWindow : PopupWindow {
	private val currentAccount = UserConfig.selectedAccount
	private var windowAnimatorSet: AnimatorSet? = null
	private var animationEnabled = allowAnimation
	private var dismissAnimationDuration = 150
	private var isClosingAnimated = false
	private var pauseNotifications = false
	private var outEmptyTime: Long = -1
	private var scaleOut = false
	private var mSuperScrollListener: OnScrollChangedListener? = null
	private var mViewTreeObserver: ViewTreeObserver? = null
	private var popupAnimationIndex = -1

	constructor() : super() {
		init()
	}

	constructor(context: Context) : super(context) {
		init()
	}

	constructor(width: Int, height: Int) : super(width, height) {
		init()
	}

	constructor(contentView: View?) : super(contentView) {
		init()
	}

	constructor(contentView: View?, width: Int, height: Int, focusable: Boolean) : super(contentView, width, height, focusable) {
		init()
	}

	constructor(contentView: View?, width: Int, height: Int) : super(contentView, width, height) {
		init()
	}

	fun setScaleOut(b: Boolean) {
		scaleOut = b
	}

	fun setAnimationEnabled(value: Boolean) {
		animationEnabled = value
	}

	fun setLayoutInScreen() {
		try {
			if (layoutInScreenMethod == null) {
				layoutInScreenMethod = PopupWindow::class.java.getDeclaredMethod("setLayoutInScreenEnabled", Boolean::class.javaPrimitiveType)
				layoutInScreenMethod?.isAccessible = true
			}

			layoutInScreenMethod?.invoke(this, true)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private fun init() {
		superListenerField?.let {
			try {
				mSuperScrollListener = it[this] as OnScrollChangedListener
				it[this] = NOP
			}
			catch (e: Exception) {
				mSuperScrollListener = null
			}
		}
	}

	fun setDismissAnimationDuration(value: Int) {
		dismissAnimationDuration = value
	}

	private fun unregisterListener() {
		if (mSuperScrollListener != null && mViewTreeObserver != null) {
			if (mViewTreeObserver?.isAlive == true) {
				mViewTreeObserver?.removeOnScrollChangedListener(mSuperScrollListener)
			}

			mViewTreeObserver = null
		}
	}

	private fun registerListener(anchor: View) {
		if (mSuperScrollListener != null) {
			val vto = if (anchor.windowToken != null) anchor.viewTreeObserver else null

			if (vto != mViewTreeObserver) {
				if (mViewTreeObserver?.isAlive == true) {
					mViewTreeObserver?.removeOnScrollChangedListener(mSuperScrollListener)
				}

				if (vto.also { mViewTreeObserver = it } != null) {
					vto?.addOnScrollChangedListener(mSuperScrollListener)
				}
			}
		}
	}

	fun dimBehind() {
		val container = contentView.rootView
		val context = contentView.context
		val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
		val p = container.layoutParams as WindowManager.LayoutParams
		p.flags = p.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
		p.dimAmount = 0.2f
		wm.updateViewLayout(container, p)
	}

	fun setFocusableFlag(enable: Boolean) {
		val container = contentView.rootView
		val context = contentView.context
		val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
		val p = container?.layoutParams as? WindowManager.LayoutParams

		if (p != null) {
			if (enable) {
				p.flags = p.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
			}
			else {
				p.flags = p.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
			}

			wm.updateViewLayout(container, p)
		}
	}

	private fun dismissDim() {
		val container = contentView.rootView
		val context = contentView.context
		val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

		if (container.layoutParams == null || container.layoutParams !is WindowManager.LayoutParams) {
			return
		}

		val p = container.layoutParams as WindowManager.LayoutParams

		runCatching {
			if (p.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
				p.flags = p.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
				p.dimAmount = 0.0f
				wm.updateViewLayout(container, p)
			}
		}
	}

	override fun showAsDropDown(anchor: View, xoff: Int, yoff: Int) {
		try {
			super.showAsDropDown(anchor, xoff, yoff)
			registerListener(anchor)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	fun startAnimation() {
		if (animationEnabled) {
			if (windowAnimatorSet != null) {
				return
			}

			val viewGroup = contentView as ViewGroup
			var content: ActionBarPopupWindowLayout? = null

			if (viewGroup is ActionBarPopupWindowLayout) {
				content = viewGroup
				content.startAnimationPending = true
			}
			else {
				for (i in 0 until viewGroup.childCount) {
					if (viewGroup.getChildAt(i) is ActionBarPopupWindowLayout) {
						content = viewGroup.getChildAt(i) as ActionBarPopupWindowLayout
						content.startAnimationPending = true
					}
				}
			}

			content?.translationY = 0f
			content?.alpha = 1.0f
			content?.pivotX = content?.measuredWidth?.toFloat() ?: 0f
			content?.pivotY = 0f

			val count = content?.itemsCount ?: 0

			content?.positions?.clear()

			var visibleCount = 0

			for (a in 0 until count) {
				val child = content?.getItemAt(a)
				child?.alpha = 0.0f

				if (child?.visibility != View.VISIBLE) {
					continue
				}

				content?.positions?.put(child, visibleCount)

				visibleCount++
			}

			if (content?.getShownFromBottom() == true) {
				content.lastStartedChild = count - 1
			}
			else {
				content?.lastStartedChild = 0
			}

			var finalScaleY = 1f

			if (content?.swipeBack != null) {
				content.swipeBack?.invalidateTransforms()
				finalScaleY = content.getBackScaleY()
			}

			windowAnimatorSet = AnimatorSet()
			windowAnimatorSet?.playTogether(ObjectAnimator.ofFloat(content, "backScaleY", 0.0f, finalScaleY), ObjectAnimator.ofInt(content, "backAlpha", 0, 255))
			windowAnimatorSet?.duration = (150 + 16 * visibleCount).toLong()

			windowAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					windowAnimatorSet = null

					@Suppress("NAME_SHADOWING") val viewGroup = contentView as ViewGroup
					@Suppress("NAME_SHADOWING") var content: ActionBarPopupWindowLayout? = null

					if (viewGroup is ActionBarPopupWindowLayout) {
						content = viewGroup
						content?.startAnimationPending = false
					}
					else {
						for (i in 0 until viewGroup.childCount) {
							if (viewGroup.getChildAt(i) is ActionBarPopupWindowLayout) {
								content = viewGroup.getChildAt(i) as ActionBarPopupWindowLayout
								content?.startAnimationPending = false
							}
						}
					}

					@Suppress("NAME_SHADOWING") val count = content?.itemsCount ?: 0

					for (a in 0 until count) {
						val child = content?.getItemAt(a)

						if (child is GapView) {
							continue
						}

						child?.alpha = if (child?.isEnabled == true) 1f else 0.5f
					}
				}
			})

			windowAnimatorSet?.start()
		}
	}

	override fun update(anchor: View, xoff: Int, yoff: Int, width: Int, height: Int) {
		super.update(anchor, xoff, yoff, width, height)
		registerListener(anchor)
	}

	override fun update(anchor: View, width: Int, height: Int) {
		super.update(anchor, width, height)
		registerListener(anchor)
	}

	override fun showAtLocation(parent: View, gravity: Int, x: Int, y: Int) {
		super.showAtLocation(parent, gravity, x, y)
		unregisterListener()
	}

	override fun dismiss() {
		dismiss(true)
	}

	fun setPauseNotifications(value: Boolean) {
		pauseNotifications = value
	}

	open fun dismiss(animated: Boolean) {
		isFocusable = false

		dismissDim()

		if (windowAnimatorSet != null) {
			if (animated && isClosingAnimated) {
				return
			}

			windowAnimatorSet?.cancel()
			windowAnimatorSet = null
		}

		isClosingAnimated = false

		if (animationEnabled && animated) {
			isClosingAnimated = true

			val viewGroup = contentView as ViewGroup
			var content: ActionBarPopupWindowLayout? = null

			for (i in 0 until viewGroup.childCount) {
				if (viewGroup.getChildAt(i) is ActionBarPopupWindowLayout) {
					content = viewGroup.getChildAt(i) as ActionBarPopupWindowLayout
				}
			}

			content?.itemAnimators?.forEach {
				it.removeAllListeners()
				it.cancel()
			}

			content?.itemAnimators?.clear()

			windowAnimatorSet = AnimatorSet()

			if (outEmptyTime > 0) {
				windowAnimatorSet?.playTogether(ValueAnimator.ofFloat(0f, 1f))
				windowAnimatorSet?.duration = outEmptyTime
			}
			else if (scaleOut) {
				windowAnimatorSet?.playTogether(ObjectAnimator.ofFloat(viewGroup, View.SCALE_Y, 0.8f), ObjectAnimator.ofFloat(viewGroup, View.SCALE_X, 0.8f), ObjectAnimator.ofFloat(viewGroup, View.ALPHA, 0.0f))
				windowAnimatorSet?.duration = dismissAnimationDuration.toLong()
			}
			else {
				windowAnimatorSet?.playTogether(ObjectAnimator.ofFloat(viewGroup, View.TRANSLATION_Y, AndroidUtilities.dp((if (content != null && content.getShownFromBottom()) 5 else -5).toFloat()).toFloat()), ObjectAnimator.ofFloat(viewGroup, View.ALPHA, 0.0f))
				windowAnimatorSet?.duration = dismissAnimationDuration.toLong()
			}

			windowAnimatorSet?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					windowAnimatorSet = null
					isClosingAnimated = false
					isFocusable = false

					runCatching {
						super@ActionBarPopupWindow.dismiss()
					}

					unregisterListener()

					if (pauseNotifications) {
						NotificationCenter.getInstance(currentAccount).onAnimationFinish(popupAnimationIndex)
					}
				}
			})

			if (pauseNotifications) {
				popupAnimationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(popupAnimationIndex, null)
			}

			windowAnimatorSet?.start()
		}
		else {
			runCatching {
				super.dismiss()
			}

			unregisterListener()
		}
	}

	fun setEmptyOutAnimation(time: Long) {
		outEmptyTime = time
	}

	fun interface OnDispatchKeyEventListener {
		fun onDispatchKeyEvent(keyEvent: KeyEvent)
	}

	fun interface OnSizeChangedListener {
		fun onSizeChanged()
	}

	open class ActionBarPopupWindowLayout @JvmOverloads constructor(context: Context, resId: Int = R.drawable.popup_fixed_alert2, flags: Int = 0) : FrameLayout(context) {
		val positions = HashMap<View, Int>()
		private val bgPaddings = Rect()

		@JvmField
		var updateAnimation = false

		@JvmField
		var swipeBackGravityRight = false

		private var subtractBackgroundHeight = 0
		val linearLayout: LinearLayout

		var backgroundDrawable: Drawable? = null
			private set

		private var mOnDispatchKeyEventListener: OnDispatchKeyEventListener? = null
		private var backScaleX = 1f
		private var backScaleY = 1f

		var startAnimationPending = false

		@get:Keep
		@set:Keep
		var backAlpha = 255

		var lastStartedChild = 0
		private var shownFromBottom = false
		private var animationEnabled = allowAnimation
		var itemAnimators: ArrayList<AnimatorSet>? = null
		private var gapStartY = -1000000
		private var gapEndY = -1000000
		private var onSizeChangedListener: OnSizeChangedListener? = null
		var swipeBack: PopupSwipeBackLayout? = null
		private var scrollView: ScrollView? = null
		private var backgroundColor = context.getColor(R.color.background)
		private var fitItems = false
		private var topView: View? = null

		init {
			super.setBackgroundResource(R.color.background)

			setBackgroundColor(backgroundColor)

			if (resId != 0) {
				backgroundDrawable = ResourcesCompat.getDrawable(resources, resId, null)?.mutate()
				setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
			}

			if (backgroundDrawable != null) {
				backgroundDrawable?.getPadding(bgPaddings)
				setBackgroundColor(context.getColor(R.color.background))
			}

			setWillNotDraw(false)

			if (flags and FLAG_SHOWN_FROM_BOTTOM > 0) {
				shownFromBottom = true
			}

			if (flags and FLAG_USE_SWIPE_BACK > 0) {
				swipeBack = PopupSwipeBackLayout(context)
				addView(swipeBack, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat()))
			}

			try {
				scrollView = ScrollView(context)
				scrollView?.isVerticalScrollBarEnabled = false

				if (swipeBack != null) {
					swipeBack?.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (shownFromBottom) Gravity.BOTTOM else Gravity.TOP))
				}
				else {
					addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat()))
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			linearLayout = object : LinearLayout(context) {
				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					if (fitItems) {
						var maxWidth = 0
						var fixWidth = 0

						gapStartY = -1000000
						gapEndY = -1000000

						var viewsToFix: ArrayList<View>? = null
						var a = 0
						val n = childCount

						while (a < n) {
							val view = getChildAt(a)

							if (view.visibility == GONE) {
								a++
								continue
							}

							val tag = view.getTag(R.id.width_tag)
							val tag2 = view.getTag(R.id.object_tag)
							val fitToWidth = view.getTag(R.id.fit_width_tag)

							if (tag != null) {
								view.layoutParams.width = LayoutHelper.WRAP_CONTENT
							}

							measureChildWithMargins(view, widthMeasureSpec, 0, heightMeasureSpec, 0)

							if (fitToWidth != null) {
								// unused
							}
							else if (tag !is Int && tag2 == null) {
								maxWidth = max(maxWidth, view.measuredWidth)
								a++
								continue
							}
							else if (tag is Int) {
								fixWidth = max(tag, view.measuredWidth)
								gapStartY = view.measuredHeight
								gapEndY = gapStartY + AndroidUtilities.dp(6f)
							}

							if (viewsToFix == null) {
								viewsToFix = ArrayList()
							}

							viewsToFix.add(view)

							a++
						}

						viewsToFix?.forEach {
							it.layoutParams.width = max(maxWidth, fixWidth)
						}
					}

					super.onMeasure(widthMeasureSpec, heightMeasureSpec)
				}

				override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
					return if (child is GapView) {
						false
					}
					else {
						super.drawChild(canvas, child, drawingTime)
					}
				}
			}

			linearLayout.orientation = LinearLayout.VERTICAL

			if (scrollView != null) {
				scrollView?.addView(linearLayout, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
			}
			else if (swipeBack != null) {
				swipeBack?.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (shownFromBottom) Gravity.BOTTOM else Gravity.TOP))
			}
			else {
				addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat()))
			}
		}

		fun addViewToSwipeBack(v: View?): Int {
			swipeBack?.addView(v, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (shownFromBottom) Gravity.BOTTOM else Gravity.TOP))
			return (swipeBack?.childCount ?: 0) - 1
		}

		fun setFitItems(value: Boolean) {
			fitItems = value
		}

		fun getShownFromBottom(): Boolean {
			return shownFromBottom
		}

		fun setShownFromBottom(value: Boolean) {
			shownFromBottom = value
		}

		fun setDispatchKeyEventListener(listener: OnDispatchKeyEventListener?) {
			mOnDispatchKeyEventListener = listener
		}

		fun getBackgroundColor(): Int {
			return backgroundColor
		}

		override fun setBackgroundColor(color: Int) {
			backgroundColor = color
			backgroundDrawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
		}

		private fun startChildAnimation(child: View) {
			if (animationEnabled) {
				val animatorSet = AnimatorSet()
				animatorSet.playTogether(ObjectAnimator.ofFloat(child, ALPHA, 0f, if (child.isEnabled) 1f else 0.5f), ObjectAnimator.ofFloat(child, TRANSLATION_Y, AndroidUtilities.dp((if (shownFromBottom) 6 else -6).toFloat()).toFloat(), 0f))
				animatorSet.duration = 180

				animatorSet.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						itemAnimators?.remove(animatorSet)
					}
				})

				animatorSet.interpolator = decelerateInterpolator
				animatorSet.start()

				if (itemAnimators == null) {
					itemAnimators = ArrayList()
				}

				itemAnimators?.add(animatorSet)
			}
		}

		fun setAnimationEnabled(value: Boolean) {
			animationEnabled = value
		}

		override fun addView(child: View) {
			linearLayout.addView(child)
		}

		fun addView(child: View?, layoutParams: LinearLayout.LayoutParams?) {
			linearLayout.addView(child, layoutParams)
		}

		fun removeInnerViews() {
			linearLayout.removeAllViews()
		}

		@Keep
		fun getBackScaleX(): Float {
			return backScaleX
		}

		@Keep
		fun setBackScaleX(value: Float) {
			if (backScaleX != value) {
				backScaleX = value
				invalidate()
				onSizeChangedListener?.onSizeChanged()
			}
		}

		fun getBackScaleY(): Float {
			return backScaleY
		}

		@Keep
		fun setBackScaleY(value: Float) {
			if (backScaleY != value) {
				backScaleY = value

				if (animationEnabled && updateAnimation) {
					val height = measuredHeight - AndroidUtilities.dp(16f)

					if (shownFromBottom) {
						for (a in lastStartedChild downTo 0) {
							val child = getItemAt(a)

							if (child.visibility != VISIBLE || child is GapView) {
								continue
							}

							val position = positions[child]

							if (position != null && height - (position * AndroidUtilities.dp(48f) + AndroidUtilities.dp(32f)) > value * height) {
								break
							}

							lastStartedChild = a - 1

							startChildAnimation(child)
						}
					}
					else {
						val count = itemsCount
						var h = 0

						for (a in 0 until count) {
							val child = getItemAt(a)

							if (child.visibility != VISIBLE) {
								continue
							}

							h += child.measuredHeight

							if (a < lastStartedChild) {
								continue
							}

							val position = positions[child]

							if (position != null && h - AndroidUtilities.dp(24f) > value * height) {
								break
							}

							lastStartedChild = a + 1

							startChildAnimation(child)
						}
					}
				}

				invalidate()

				onSizeChangedListener?.onSizeChanged()
			}
		}

		override fun dispatchKeyEvent(event: KeyEvent): Boolean {
			mOnDispatchKeyEventListener?.onDispatchKeyEvent(event)
			return super.dispatchKeyEvent(event)
		}

		override fun dispatchDraw(canvas: Canvas) {
			if (swipeBackGravityRight) {
				translationX = measuredWidth * (1f - backScaleX)

				if (topView != null) {
					topView?.translationX = measuredWidth * (1f - backScaleX)
					topView?.alpha = 1f - (swipeBack?.transitionProgress ?: 0f)
					val h = (topView!!.measuredHeight - AndroidUtilities.dp(16f)).toFloat()
					val yOffset = -h * (swipeBack?.transitionProgress ?: 0f)
					topView?.translationY = yOffset
					translationY = yOffset
				}
			}

			super.dispatchDraw(canvas)
		}

		override fun onDraw(canvas: Canvas) {
			val backgroundDrawable = backgroundDrawable ?: return
			val start = gapStartY - scrollView!!.scrollY
			val end = gapEndY - scrollView!!.scrollY
			var hasGap = false

			for (i in 0 until linearLayout.childCount) {
				if (linearLayout.getChildAt(i) is GapView && linearLayout.getChildAt(i).visibility == VISIBLE) {
					hasGap = true
					break
				}
			}

			for (a in 0..1) {
				if (a == 1 && start < -AndroidUtilities.dp(16f)) {
					break
				}

				var needRestore = false
				var applyAlpha = true

				if (hasGap && backAlpha != 255) {
					canvas.saveLayerAlpha(0f, bgPaddings.top.toFloat(), measuredWidth.toFloat(), measuredHeight.toFloat(), backAlpha)
					needRestore = true
					applyAlpha = false
				}
				else if (gapStartY != -1000000) {
					needRestore = true
					canvas.save()
					canvas.clipRect(0, bgPaddings.top, measuredWidth, measuredHeight)
				}

				backgroundDrawable.alpha = if (applyAlpha) backAlpha else 255

				if (shownFromBottom) {
					val height = measuredHeight
					backgroundDrawable.setBounds(0, (height * (1.0f - backScaleY)).toInt(), (measuredWidth * backScaleX).toInt(), height)
				}
				else {
					if (start > -AndroidUtilities.dp(16f)) {
						val h = (measuredHeight * backScaleY).toInt()

						if (a == 0) {
							backgroundDrawable.setBounds(0, -scrollView!!.scrollY + if (gapStartY != -1000000) AndroidUtilities.dp(1f) else 0, (measuredWidth * backScaleX).toInt(), (if (gapStartY != -1000000) min(h, start + AndroidUtilities.dp(16f)) else h) - subtractBackgroundHeight)
						}
						else {
							if (h < end) {
								if (gapStartY != -1000000) {
									canvas.restore()
								}

								continue
							}

							backgroundDrawable.setBounds(0, end, (measuredWidth * backScaleX).toInt(), h - subtractBackgroundHeight)
						}
					}
					else {
						backgroundDrawable.setBounds(0, if (gapStartY < 0) 0 else -AndroidUtilities.dp(16f), (measuredWidth * backScaleX).toInt(), (measuredHeight * backScaleY).toInt() - subtractBackgroundHeight)
					}
				}

				backgroundDrawable.draw(canvas)

				if (hasGap) {
					canvas.save()

					AndroidUtilities.rectTmp2.set(backgroundDrawable.bounds)
					AndroidUtilities.rectTmp2.inset(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))

					canvas.clipRect(AndroidUtilities.rectTmp2)

					for (i in 0 until linearLayout.childCount) {
						if (linearLayout.getChildAt(i) is GapView && linearLayout.getChildAt(i).visibility == VISIBLE) {
							canvas.save()
							var x = 0f
							var y = 0f
							val child = linearLayout.getChildAt(i) as GapView
							var view: View? = child

							while (view !== this) {
								x += (view?.x ?: 0f)
								y += (view?.y ?: 0f)

								view = view?.parent as? View

								if (view == null) {
									break
								}
							}

							canvas.translate(x, y * scrollView!!.scaleY)
							child.draw(canvas)
							canvas.restore()
						}
					}
					canvas.restore()
				}

				if (needRestore) {
					canvas.restore()
				}
			}
		}

		override fun setBackgroundDrawable(drawable: Drawable) {
			backgroundColor = context.getColor(R.color.background)
			backgroundDrawable = drawable
			backgroundDrawable?.getPadding(bgPaddings)
		}

		val itemsCount: Int
			get() = linearLayout.childCount

		fun getItemAt(index: Int): View {
			return linearLayout.getChildAt(index)
		}

		fun scrollToTop() {
			scrollView?.scrollTo(0, 0)
		}

		fun setupRadialSelectors(color: Int) {
			val count = linearLayout.childCount

			for (a in 0 until count) {
				val child = linearLayout.getChildAt(a)
				child.background = Theme.createRadSelectorDrawable(color, if (a == 0) 6 else 0, if (a == count - 1) 6 else 0)
			}
		}

		fun updateRadialSelectors() {
			val count = linearLayout.childCount
			var firstVisible: View? = null
			var lastVisible: View? = null

			for (a in 0 until count) {
				val child = linearLayout.getChildAt(a)

				if (child.visibility != VISIBLE) {
					continue
				}

				if (firstVisible == null) {
					firstVisible = child
				}

				lastVisible = child
			}

			var prevGap = false

			for (a in 0 until count) {
				val child = linearLayout.getChildAt(a)

				if (child.visibility != VISIBLE) {
					continue
				}

				val tag = child.getTag(R.id.object_tag)

				if (child is ActionBarMenuSubItem) {
					child.updateSelectorBackground(child === firstVisible || prevGap, child === lastVisible)
				}

				prevGap = tag != null
			}
		}

		fun setOnSizeChangedListener(onSizeChangedListener: OnSizeChangedListener?) {
			this.onSizeChangedListener = onSizeChangedListener
		}

		val visibleHeight: Int
			get() = (measuredHeight * backScaleY).toInt()

		fun setTopView(topView: View?) {
			this.topView = topView
		}

		fun setSwipeBackForegroundColor(color: Int) {
			swipeBack!!.setForegroundColor(color)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			swipeBack?.invalidateTransforms(!startAnimationPending)
		}

		companion object {
			const val FLAG_USE_SWIPE_BACK = 1
			const val FLAG_SHOWN_FROM_BOTTOM = 2
		}
	}

	class GapView(context: Context, color: Int) : FrameLayout(context) {
		init {
			setBackgroundColor(color)
		}

		fun setColor(color: Int) {
			setBackgroundColor(color)
		}
	}

	@SuppressLint("SoonBlockedPrivateApi")
	companion object {
		private var superListenerField: Field? = null
		private const val allowAnimation = true
		private val decelerateInterpolator = DecelerateInterpolator()
		private val NOP = OnScrollChangedListener {}
		private var layoutInScreenMethod: Method? = null

		init {
			var f: Field? = null

			runCatching {
				f = PopupWindow::class.java.getDeclaredField("mOnScrollChangedListener")
				f?.isAccessible = true
			}

			superListenerField = f
		}

		fun startAnimation(content: ActionBarPopupWindowLayout): AnimatorSet {
			content.startAnimationPending = true
			content.translationY = 0f
			content.alpha = 1.0f
			content.pivotX = content.measuredWidth.toFloat()
			content.pivotY = 0f

			val count = content.itemsCount
			content.positions.clear()

			var visibleCount = 0

			for (a in 0 until count) {
				val child = content.getItemAt(a)
				child.alpha = 0.0f

				if (child.visibility != View.VISIBLE) {
					continue
				}

				content.positions[child] = visibleCount
				visibleCount++
			}

			if (content.getShownFromBottom()) {
				content.lastStartedChild = count - 1
			}
			else {
				content.lastStartedChild = 0
			}

			var finalScaleY = 1f

			if (content.swipeBack != null) {
				content.swipeBack?.invalidateTransforms()
				finalScaleY = content.getBackScaleY()
			}

			val windowAnimatorSet = AnimatorSet()

			val childTranslations = ValueAnimator.ofFloat(0f, 1f)

			childTranslations.addUpdateListener {
				val count2 = content.itemsCount
				val t = it.animatedValue as Float

				for (a in 0 until count2) {
					val child = content.getItemAt(a)

					if (child is GapView) {
						continue
					}

					val at = AndroidUtilities.cascade(t, a.toFloat(), count2.toFloat(), 2f)

					child.translationY = (1f - at) * AndroidUtilities.dp(-12f)
					child.alpha = at
				}
			}

			content.updateAnimation = true

			windowAnimatorSet.playTogether(ObjectAnimator.ofFloat(content, "backScaleY", 0.0f, finalScaleY), ObjectAnimator.ofInt(content, "backAlpha", 0, 255), childTranslations)
			windowAnimatorSet.duration = (150 + 16 * visibleCount + 1000).toLong()

			windowAnimatorSet.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					content.startAnimationPending = false

					@Suppress("NAME_SHADOWING") val count = content.itemsCount

					for (a in 0 until count) {
						val child = content.getItemAt(a)

						if (child is GapView) {
							continue
						}

						child.alpha = if (child.isEnabled) 1f else 0.5f
					}
				}
			})

			windowAnimatorSet.start()

			return windowAnimatorSet
		}
	}
}
