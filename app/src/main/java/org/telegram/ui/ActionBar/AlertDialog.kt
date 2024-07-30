/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.ActionBar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.LineProgressView
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.spoilers.SpoilersTextView
import java.util.*
import kotlin.math.min

fun buildDialog(context: Context, receiver: AlertDialog.Builder.() -> Unit): AlertDialog {
	val builder = AlertDialog.Builder(context)
	builder.apply(receiver)
	return builder.create()
}

open class AlertDialog(context: Context, progressStyle: Int) : Dialog(context, R.style.TransparentDialog), Drawable.Callback {
	private val shadow = arrayOfNulls<BitmapDrawable>(2)
	private val shadowVisibility = BooleanArray(2)
	private val shadowAnimation = arrayOfNulls<AnimatorSet>(2)
	private val backgroundPaddings = Rect()

	private val showRunnable = Runnable {
		if (isShowing) {
			return@Runnable
		}

		try {
			show()
		}
		catch (e: Exception) {
			// ignored
		}
	}

	private val itemViews = ArrayList<AlertDialogCell>()
	private val dimCustom = false

	var buttonsLayout: ViewGroup? = null
		protected set

	private var customView: View? = null
	private var customViewHeight = LayoutHelper.WRAP_CONTENT
	private var titleTextView: TextView? = null
	private var secondTitleTextView: TextView? = null
	private var subtitleTextView: TextView? = null
	private var messageTextView: TextView? = null
	private var progressViewContainer: FrameLayout? = null
	private var titleContainer: FrameLayout? = null
	private var contentScrollView: ScrollView? = null
	private var scrollContainer: LinearLayout? = null
	private var onScrollChangedListener: OnScrollChangedListener? = null
	private var customViewOffset = 20
	private var positiveButtonColor: Int
	private var negativeButtonColor: Int
	private var onCancelListener: DialogInterface.OnCancelListener? = null
	private var cancelDialog: AlertDialog? = null
	private var lastScreenWidth = 0
	private var onClickListener: DialogInterface.OnClickListener? = null
	private var onDismissListener: DialogInterface.OnDismissListener? = null
	private val dismissRunnable = Runnable { dismiss() }
	private var items: Array<CharSequence?>? = null
	private var itemIcons: IntArray? = null
	private var title: CharSequence? = null
	private var secondTitle: CharSequence? = null
	private var subtitle: CharSequence? = null
	private var message: CharSequence? = null
	private var topResId = 0
	private var topView: View? = null
	private var topAnimationId = 0
	private var topAnimationSize = 0
	private var topHeight = 132
	private var topDrawable: Drawable? = null
	private var topBackgroundColor = 0
	private var progressViewStyle: Int // TODO: Use constants here
	private var currentProgress = 0
	private var messageTextViewClickable = true
	private var canCacnel = true
	private var dismissDialogByButtons = true
	private var drawBackground = false
	private var notDrawBackgroundOnTopView = false
	private var topImageView: RLottieImageView? = null
	private var positiveButtonText: CharSequence? = null
	private var positiveButtonListener: DialogInterface.OnClickListener? = null
	private var negativeButtonText: CharSequence? = null
	private var negativeButtonListener: DialogInterface.OnClickListener? = null
	private var neutralButtonText: CharSequence? = null
	private var neutralButtonListener: DialogInterface.OnClickListener? = null
	private var lineProgressView: LineProgressView? = null
	private var lineProgressViewPercent: TextView? = null
	private var onBackButtonListener: DialogInterface.OnClickListener? = null
	private var checkFocusable = true
	private var shadowDrawable: Drawable? = null
	private var focusable = false
	private var verticalButtons = false
	private var aspectRatio = 0f
	private var dimEnabled = true
	private var dimAlpha = 0.6f
	private var topAnimationAutoRepeat = true

	init {
		if (progressStyle != 3) {
			shadowDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.popup_fixed_alert, null)?.mutate()
			shadowDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.background, null), PorterDuff.Mode.MULTIPLY)
			shadowDrawable?.getPadding(backgroundPaddings)
		}

		progressViewStyle = progressStyle

		positiveButtonColor = ResourcesCompat.getColor(context.resources, R.color.text, null)
		negativeButtonColor = ResourcesCompat.getColor(context.resources, R.color.text, null)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val containerView: LinearLayout = object : LinearLayout(context) {
			private var inLayout = false

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				if (progressViewStyle == 3) {
					showCancelAlert()
					return false
				}

				return super.onTouchEvent(event)
			}

			override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
				if (progressViewStyle == 3) {
					showCancelAlert()
					return false
				}

				return super.onInterceptTouchEvent(ev)
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				if (progressViewStyle == 3) {
					progressViewContainer!!.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86f), MeasureSpec.EXACTLY))
					setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
				}
				else {
					inLayout = true

					val width = MeasureSpec.getSize(widthMeasureSpec)
					val height = MeasureSpec.getSize(heightMeasureSpec)
					val maxContentHeight = height - paddingTop - paddingBottom
					var availableHeight = maxContentHeight
					val availableWidth = width - paddingLeft - paddingRight
					val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(availableWidth - AndroidUtilities.dp(48f), MeasureSpec.EXACTLY)
					val childFullWidthMeasureSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY)
					var layoutParams: LayoutParams

					buttonsLayout?.let { buttonsLayout ->
						val count = buttonsLayout.childCount

						for (a in 0 until count) {
							val child = buttonsLayout.getChildAt(a)

							if (child is TextView) {
								child.maxWidth = AndroidUtilities.dp(((availableWidth - AndroidUtilities.dp(24f)) / 2).toFloat())
							}
						}

						buttonsLayout.measure(childFullWidthMeasureSpec, heightMeasureSpec)

						layoutParams = buttonsLayout.layoutParams as LayoutParams
						availableHeight -= buttonsLayout.measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
					}

					secondTitleTextView?.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(childWidthMeasureSpec), MeasureSpec.AT_MOST), heightMeasureSpec)

					titleTextView?.let { titleTextView ->
						secondTitleTextView?.let { secondTitleTextView ->
							titleTextView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(childWidthMeasureSpec) - secondTitleTextView.measuredWidth - AndroidUtilities.dp(8f), MeasureSpec.EXACTLY), heightMeasureSpec)
						} ?: run {
							titleTextView.measure(childWidthMeasureSpec, heightMeasureSpec)
						}
					}

					titleContainer?.let { titleContainer ->
						titleContainer.measure(childWidthMeasureSpec, heightMeasureSpec)
						layoutParams = titleContainer.layoutParams as LayoutParams
						availableHeight -= titleContainer.measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
					}

					subtitleTextView?.let { subtitleTextView ->
						subtitleTextView.measure(childWidthMeasureSpec, heightMeasureSpec)
						layoutParams = subtitleTextView.layoutParams as LayoutParams
						availableHeight -= subtitleTextView.measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
					}

					topImageView?.let { topImageView ->
						topImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(topHeight.toFloat()), MeasureSpec.EXACTLY))
						availableHeight -= topImageView.measuredHeight - AndroidUtilities.dp(8f)
					}

					topView?.let { topView ->
						val w = width - AndroidUtilities.dp(16f)

						val h = if (aspectRatio == 0f) {
							val scale = w / 936.0f
							(354 * scale).toInt()
						}
						else {
							(w * aspectRatio).toInt()
						}

						topView.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
						topView.layoutParams.height = h

						availableHeight -= topView.measuredHeight
					}

					if (progressViewStyle == 0) {
						layoutParams = contentScrollView!!.layoutParams as LayoutParams

						if (customView != null) {
							layoutParams.topMargin = if (titleTextView == null && messageTextView!!.visibility == GONE && items == null) AndroidUtilities.dp(16f) else 0
							layoutParams.bottomMargin = if (buttonsLayout == null) AndroidUtilities.dp(8f) else 0
						}
						else if (items != null) {
							layoutParams.topMargin = if (titleTextView == null && messageTextView!!.visibility == GONE) AndroidUtilities.dp(8f) else 0
							layoutParams.bottomMargin = AndroidUtilities.dp(8f)
						}
						else if (messageTextView!!.visibility == VISIBLE) {
							layoutParams.topMargin = if (titleTextView == null) AndroidUtilities.dp(19f) else 0
							layoutParams.bottomMargin = AndroidUtilities.dp(20f)
						}

						availableHeight -= layoutParams.bottomMargin + layoutParams.topMargin

						contentScrollView?.measure(childFullWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST))

						availableHeight -= contentScrollView!!.measuredHeight
					}
					else {
						if (progressViewContainer != null) {
							progressViewContainer?.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST))
							layoutParams = progressViewContainer!!.layoutParams as LayoutParams
							availableHeight -= progressViewContainer!!.measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
						}
						else if (messageTextView != null) {
							messageTextView?.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST))

							if (messageTextView?.visibility != GONE) {
								layoutParams = messageTextView!!.layoutParams as LayoutParams
								availableHeight -= messageTextView!!.measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
							}
						}

						if (lineProgressView != null) {
							lineProgressView?.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(4f), MeasureSpec.EXACTLY))
							layoutParams = lineProgressView!!.layoutParams as LayoutParams
							availableHeight -= lineProgressView!!.measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
							lineProgressViewPercent?.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST))
							layoutParams = lineProgressViewPercent!!.layoutParams as LayoutParams
							availableHeight -= lineProgressViewPercent!!.measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
						}
					}

					setMeasuredDimension(width, maxContentHeight - availableHeight + paddingTop + paddingBottom)

					inLayout = false

					if (lastScreenWidth != AndroidUtilities.displaySize.x) {
						AndroidUtilities.runOnUIThread {
							lastScreenWidth = AndroidUtilities.displaySize.x

							val calculatedWidth = AndroidUtilities.displaySize.x - AndroidUtilities.dp(56f)

							val maxWidth = if (AndroidUtilities.isTablet()) {
								if (AndroidUtilities.isSmallTablet()) {
									AndroidUtilities.dp(446f)
								}
								else {
									AndroidUtilities.dp(496f)
								}
							}
							else {
								AndroidUtilities.dp(356f)
							}

							window?.let {
								val params = WindowManager.LayoutParams()
								params.copyFrom(it.attributes)
								params.width = min(maxWidth, calculatedWidth) + backgroundPaddings.left + backgroundPaddings.right

								try {
									it.attributes = params
								}
								catch (e: Throwable) {
									FileLog.e(e)
								}
							}
						}
					}
				}
			}

			override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
				super.onLayout(changed, l, t, r, b)

				if (progressViewStyle == 3) {
					val x = (r - l - progressViewContainer!!.measuredWidth) / 2
					val y = (b - t - progressViewContainer!!.measuredHeight) / 2
					progressViewContainer?.layout(x, y, x + progressViewContainer!!.measuredWidth, y + progressViewContainer!!.measuredHeight)
				}
				else {
					contentScrollView?.let { contentScrollView ->
						if (onScrollChangedListener == null) {
							onScrollChangedListener = OnScrollChangedListener {
								runShadowAnimation(0, titleTextView != null && contentScrollView.scrollY > scrollContainer!!.top)
								runShadowAnimation(1, buttonsLayout != null && contentScrollView.scrollY + contentScrollView.height < scrollContainer!!.bottom)
								contentScrollView.invalidate()
							}

							contentScrollView.viewTreeObserver?.addOnScrollChangedListener(onScrollChangedListener)
						}

						onScrollChangedListener?.onScrollChanged()
					}
				}
			}

			override fun requestLayout() {
				if (inLayout) {
					return
				}

				super.requestLayout()
			}

			override fun hasOverlappingRendering(): Boolean {
				return false
			}

			override fun dispatchDraw(canvas: Canvas) {
				if (drawBackground) {
					shadowDrawable?.setBounds(0, 0, measuredWidth, measuredHeight)

					if (topView != null && notDrawBackgroundOnTopView) {
						val clipTop = topView!!.bottom
						canvas.save()
						canvas.clipRect(0, clipTop, measuredWidth, measuredHeight)
						shadowDrawable?.draw(canvas)
						canvas.restore()
					}
					else {
						shadowDrawable?.draw(canvas)
					}
				}

				super.dispatchDraw(canvas)
			}
		}

		containerView.orientation = LinearLayout.VERTICAL

		if (progressViewStyle == 3) {
			containerView.background = null
			containerView.setPadding(0, 0, 0, 0)
			drawBackground = false
		}
		else {
			if (notDrawBackgroundOnTopView) {
				val rect = Rect()
				shadowDrawable?.getPadding(rect)
				containerView.setPadding(rect.left, rect.top, rect.right, rect.bottom)
				drawBackground = true
			}
			else {
				containerView.background = null
				containerView.setPadding(0, 0, 0, 0)
				containerView.background = shadowDrawable
				drawBackground = false
			}
		}

		containerView.fitsSystemWindows = true
		setContentView(containerView)

		val hasButtons = positiveButtonText != null || negativeButtonText != null || neutralButtonText != null

		if (topResId != 0 || topAnimationId != 0 || topDrawable != null) {
			topImageView = RLottieImageView(context)

			if (topDrawable != null) {
				topImageView?.setImageDrawable(topDrawable)
			}
			else if (topResId != 0) {
				topImageView?.setImageResource(topResId)
			}
			else {
				topImageView?.setAutoRepeat(topAnimationAutoRepeat)
				topImageView?.setAnimation(topAnimationId, topAnimationSize, topAnimationSize)
				topImageView?.playAnimation()
			}

			topImageView?.scaleType = ImageView.ScaleType.CENTER
			topImageView?.background = ResourcesCompat.getDrawable(context.resources, R.drawable.popup_fixed_top, null)
			topImageView?.background?.colorFilter = PorterDuffColorFilter(topBackgroundColor, PorterDuff.Mode.MULTIPLY)
			topImageView?.setPadding(0, 0, 0, 0)

			containerView.addView(topImageView, createLinear(LayoutHelper.MATCH_PARENT, topHeight, Gravity.LEFT or Gravity.TOP, -8, -8, 0, 0))
		}
		else if (topView != null) {
			topView?.setPadding(0, 0, 0, 0)
			containerView.addView(topView, createLinear(LayoutHelper.MATCH_PARENT, topHeight, Gravity.LEFT or Gravity.TOP, 0, 0, 0, 0))
		}
		if (title != null) {
			titleContainer = FrameLayout(context)

			containerView.addView(titleContainer, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 24f, 0f, 24f, 0f))

			titleTextView = SpoilersTextView(context, false)
			titleTextView?.text = title
			titleTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			titleTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
			titleTextView?.typeface = Theme.TYPEFACE_BOLD
			titleTextView?.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP

			titleContainer?.addView(titleTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 0f, 19f, 0f, (if (subtitle != null) 2 else if (items != null) 14 else 10).toFloat()))
		}

		if (secondTitle != null && title != null) {
			secondTitleTextView = TextView(context)
			secondTitleTextView?.text = secondTitle
			secondTitleTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
			secondTitleTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
			secondTitleTextView?.gravity = (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP

			titleContainer?.addView(secondTitleTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP, 0f, 21f, 0f, 0f))
		}

		if (subtitle != null) {
			subtitleTextView = TextView(context)
			subtitleTextView?.text = subtitle
			subtitleTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))
			subtitleTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			subtitleTextView?.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP

			containerView.addView(subtitleTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24, 0, 24, if (items != null) 14 else 10))
		}

		if (progressViewStyle == 0) {
			shadow[0] = ResourcesCompat.getDrawable(context.resources, R.drawable.header_shadow, null)!!.mutate() as BitmapDrawable
			shadow[1] = ResourcesCompat.getDrawable(context.resources, R.drawable.header_shadow_reverse, null)!!.mutate() as BitmapDrawable
			shadow[0]?.alpha = 0
			shadow[1]?.alpha = 0
			shadow[0]?.callback = this
			shadow[1]?.callback = this

			contentScrollView = object : ScrollView(context) {
				override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
					val result = super.drawChild(canvas, child, drawingTime)

					if (shadow[0]?.paint?.alpha != 0) {
						shadow[0]?.setBounds(0, scrollY, measuredWidth, scrollY + AndroidUtilities.dp(3f))
						shadow[0]?.draw(canvas)
					}

					if (shadow[1]?.paint?.alpha != 0) {
						shadow[1]?.setBounds(0, scrollY + measuredHeight - AndroidUtilities.dp(3f), measuredWidth, scrollY + measuredHeight)
						shadow[1]?.draw(canvas)
					}

					return result
				}
			}

			contentScrollView?.isVerticalScrollBarEnabled = false

			AndroidUtilities.setScrollViewEdgeEffectColor(contentScrollView, ResourcesCompat.getColor(context.resources, R.color.color_toggle_active, null))

			containerView.addView(contentScrollView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0f, 0f, 0f))

			scrollContainer = LinearLayout(context)
			scrollContainer?.orientation = LinearLayout.VERTICAL

			contentScrollView?.addView(scrollContainer, FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

		messageTextView = SpoilersTextView(context, false)
		messageTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		messageTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		messageTextView?.movementMethod = AndroidUtilities.LinkMovementMethodMy()
		messageTextView?.setLinkTextColor(ResourcesCompat.getColor(context.resources, R.color.brand_day_night, null))

		if (!messageTextViewClickable) {
			messageTextView?.isClickable = false
			messageTextView?.isEnabled = false
		}

		messageTextView?.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP

		when (progressViewStyle) {
			1 -> {
				progressViewContainer = FrameLayout(context)

				containerView.addView(progressViewContainer, createLinear(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT or Gravity.TOP, 23, if (title == null) 24 else 0, 23, 24))

				val progressView = RadialProgressView(context)
				progressView.setProgressColor(ResourcesCompat.getColor(context.resources, R.color.brand_day_night, null))

				progressViewContainer?.addView(progressView, createFrame(44, 44, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP))

				messageTextView?.setLines(1)
				messageTextView?.ellipsize = TextUtils.TruncateAt.END

				progressViewContainer?.addView(messageTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, (if (LocaleController.isRTL) 0 else 62).toFloat(), 0f, (if (LocaleController.isRTL) 62 else 0).toFloat(), 0f))
			}

			2 -> {
				containerView.addView(messageTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24, if (title == null) 19 else 0, 24, 20))

				lineProgressView = LineProgressView(context)
				lineProgressView?.setProgress(currentProgress / 100.0f, false)
				lineProgressView?.setProgressColor(ResourcesCompat.getColor(context.resources, R.color.brand_day_night, null))
				lineProgressView?.setBackColor(ResourcesCompat.getColor(context.resources, R.color.light_background, null))

				containerView.addView(lineProgressView, createLinear(LayoutHelper.MATCH_PARENT, 4, Gravity.LEFT or Gravity.CENTER_VERTICAL, 24, 0, 24, 0))

				lineProgressViewPercent = TextView(context)
				lineProgressViewPercent?.typeface = Theme.TYPEFACE_BOLD
				lineProgressViewPercent?.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
				lineProgressViewPercent?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
				lineProgressViewPercent?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)

				containerView.addView(lineProgressViewPercent, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 23, 4, 23, 24))

				updateLineProgressTextView()
			}

			3 -> {
				setCanceledOnTouchOutside(false)
				setCancelable(false)

				progressViewContainer = FrameLayout(context)
				progressViewContainer?.background = Theme.createRoundRectDrawable(AndroidUtilities.dp(18f), ResourcesCompat.getColor(context.resources, R.color.color_toggle_active, null))

				containerView.addView(progressViewContainer, createLinear(86, 86, Gravity.CENTER))

				val progressView = RadialProgressView(context)
				progressView.setProgressColor(ResourcesCompat.getColor(context.resources, R.color.brand_day_night, null))

				progressViewContainer?.addView(progressView, createLinear(86, 86))
			}

			else -> {
				scrollContainer?.addView(messageTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, 24, 0, 24, if (customView != null || items != null) customViewOffset else 0))
			}
		}

		if (!message.isNullOrEmpty()) {
			messageTextView?.text = message
			messageTextView?.visible()
		}
		else {
			messageTextView?.gone()
		}

		items?.forEachIndexed { index, item ->
			if (item == null) {
				return@forEachIndexed
			}

			val cell = AlertDialogCell(context)
			cell.setTextAndIcon(item, itemIcons?.getOrNull(index) ?: 0)
			cell.tag = index

			itemViews.add(cell)

			scrollContainer?.addView(cell, createLinear(LayoutHelper.MATCH_PARENT, 50))

			cell.setOnClickListener {
				onClickListener?.onClick(this@AlertDialog, (it.tag as Int))
				dismiss()
			}
		}

		customView?.let { customView ->
			(customView.parent as? ViewGroup)?.removeView(customView)
			scrollContainer?.addView(customView, createLinear(LayoutHelper.MATCH_PARENT, customViewHeight))
		}

		if (hasButtons) {
			if (!verticalButtons) {
				var buttonsWidth = 0

				val paint = TextPaint()
				paint.typeface = Theme.TYPEFACE_DEFAULT
				paint.textSize = AndroidUtilities.dp(14f).toFloat()

				positiveButtonText?.let {
					buttonsWidth += (paint.measureText(it, 0, it.length) + AndroidUtilities.dp(10f)).toInt()
				}

				negativeButtonText?.let {
					buttonsWidth += (paint.measureText(it, 0, it.length) + AndroidUtilities.dp(10f)).toInt()
				}

				neutralButtonText?.let {
					buttonsWidth += (paint.measureText(it, 0, it.length) + AndroidUtilities.dp(10f)).toInt()
				}

				if (buttonsWidth > AndroidUtilities.displaySize.x - AndroidUtilities.dp(110f)) {
					verticalButtons = true
				}
			}

			if (verticalButtons) {
				val linearLayout = LinearLayout(context)
				linearLayout.orientation = LinearLayout.VERTICAL
				buttonsLayout = linearLayout
			}
			else {
				buttonsLayout = object : FrameLayout(context) {
					override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
						val count = childCount
						var positiveButton: View? = null
						val width = right - left

						for (a in 0 until count) {
							val child = getChildAt(a)
							val tag = child.tag as? Int

							if (tag != null) {
								if (tag == BUTTON_POSITIVE) {
									positiveButton = child

									if (LocaleController.isRTL) {
										child.layout(paddingLeft, paddingTop, paddingLeft + child.measuredWidth, paddingTop + child.measuredHeight)
									}
									else {
										child.layout(width - paddingRight - child.measuredWidth, paddingTop, width - paddingRight, paddingTop + child.measuredHeight)
									}
								}
								else if (tag == BUTTON_NEGATIVE) {
									var x: Int

									if (LocaleController.isRTL) {
										x = paddingLeft

										if (positiveButton != null) {
											x += positiveButton.measuredWidth + AndroidUtilities.dp(8f)
										}
									}
									else {
										x = width - paddingRight - child.measuredWidth

										if (positiveButton != null) {
											x -= positiveButton.measuredWidth + AndroidUtilities.dp(8f)
										}
									}

									child.layout(x, paddingTop, x + child.measuredWidth, paddingTop + child.measuredHeight)
								}
								else if (tag == BUTTON_NEUTRAL) {
									if (LocaleController.isRTL) {
										child.layout(width - paddingRight - child.measuredWidth, paddingTop, width - paddingRight, paddingTop + child.measuredHeight)
									}
									else {
										child.layout(paddingLeft, paddingTop, paddingLeft + child.measuredWidth, paddingTop + child.measuredHeight)
									}
								}
							}
							else {
								val w = child.measuredWidth
								val h = child.measuredHeight
								var l: Int
								var t: Int

								if (positiveButton != null) {
									l = positiveButton.left + (positiveButton.measuredWidth - w) / 2
									t = positiveButton.top + (positiveButton.measuredHeight - h) / 2
								}
								else {
									t = 0
									l = 0
								}

								child.layout(l, t, l + w, t + h)
							}
						}
					}

					override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
						super.onMeasure(widthMeasureSpec, heightMeasureSpec)

						var totalWidth = 0
						val availableWidth = measuredWidth - paddingLeft - paddingRight
						val count = childCount

						for (a in 0 until count) {
							val child = getChildAt(a)

							if (child is TextView && child.getTag() != null) {
								totalWidth += child.getMeasuredWidth()
							}
						}

						if (totalWidth > availableWidth) {
							val negative = findViewWithTag<View>(BUTTON_NEGATIVE)
							val neutral = findViewWithTag<View>(BUTTON_NEUTRAL)

							if (negative != null && neutral != null) {
								if (negative.measuredWidth < neutral.measuredWidth) {
									neutral.measure(MeasureSpec.makeMeasureSpec(neutral.measuredWidth - (totalWidth - availableWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(neutral.measuredHeight, MeasureSpec.EXACTLY))
								}
								else {
									negative.measure(MeasureSpec.makeMeasureSpec(negative.measuredWidth - (totalWidth - availableWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(negative.measuredHeight, MeasureSpec.EXACTLY))
								}
							}
						}
					}
				}
			}

			buttonsLayout?.setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))

			containerView.addView(buttonsLayout, createLinear(LayoutHelper.MATCH_PARENT, 52))

			positiveButtonText?.takeIf { it.isNotEmpty() }?.let {
				val textView = object : TextView(context) {
					override fun setEnabled(enabled: Boolean) {
						super.setEnabled(enabled)
						alpha = if (enabled) 1.0f else 0.5f
					}

					override fun setTextColor(color: Int) {
						super.setTextColor(color)
						background = Theme.getRoundRectSelectorDrawable(color)
					}
				}

				textView.minWidth = AndroidUtilities.dp(64f)
				textView.tag = BUTTON_POSITIVE
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				textView.setTextColor(positiveButtonColor)
				textView.gravity = Gravity.CENTER
				textView.typeface = Theme.TYPEFACE_BOLD
				textView.text = it.toString().uppercase()
				textView.background = Theme.getRoundRectSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.text, null))
				textView.setPadding(AndroidUtilities.dp(10f), 0, AndroidUtilities.dp(10f), 0)

				if (verticalButtons) {
					buttonsLayout?.addView(textView, createLinear(LayoutHelper.WRAP_CONTENT, 36, if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT))
				}
				else {
					buttonsLayout?.addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP or Gravity.RIGHT))
				}

				textView.setOnClickListener {
					positiveButtonListener?.onClick(this@AlertDialog, BUTTON_POSITIVE)

					if (dismissDialogByButtons) {
						dismiss()
					}
				}
			}

			negativeButtonText?.takeIf { it.isNotEmpty() }?.let {
				val textView = object : TextView(context) {
					override fun setEnabled(enabled: Boolean) {
						super.setEnabled(enabled)
						alpha = if (enabled) 1.0f else 0.5f
					}

					override fun setTextColor(color: Int) {
						super.setTextColor(color)
						background = Theme.getRoundRectSelectorDrawable(color)
					}
				}

				textView.minWidth = AndroidUtilities.dp(64f)
				textView.tag = BUTTON_NEGATIVE
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				textView.setTextColor(negativeButtonColor)
				textView.gravity = Gravity.CENTER
				textView.typeface = Theme.TYPEFACE_BOLD
				textView.ellipsize = TextUtils.TruncateAt.END
				textView.isSingleLine = true
				textView.text = it.toString().uppercase()
				textView.background = Theme.getRoundRectSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.text, null))
				textView.setPadding(AndroidUtilities.dp(10f), 0, AndroidUtilities.dp(10f), 0)

				if (verticalButtons) {
					buttonsLayout?.addView(textView, 0, createLinear(LayoutHelper.WRAP_CONTENT, 36, if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT))
				}
				else {
					buttonsLayout?.addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP or Gravity.RIGHT))
				}

				textView.setOnClickListener {
					negativeButtonListener?.onClick(this@AlertDialog, BUTTON_NEGATIVE)

					if (dismissDialogByButtons) {
						cancel()
					}
				}
			}

			neutralButtonText?.takeIf { it.isNotEmpty() }?.let {
				val textView = object : TextView(context) {
					override fun setEnabled(enabled: Boolean) {
						super.setEnabled(enabled)
						alpha = if (enabled) 1.0f else 0.5f
					}

					override fun setTextColor(color: Int) {
						super.setTextColor(color)
						background = Theme.getRoundRectSelectorDrawable(color)
					}
				}

				textView.minWidth = AndroidUtilities.dp(64f)
				textView.tag = BUTTON_NEUTRAL
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
				textView.gravity = Gravity.CENTER
				textView.typeface = Theme.TYPEFACE_BOLD
				textView.ellipsize = TextUtils.TruncateAt.END
				textView.isSingleLine = true
				textView.text = it.toString().uppercase()
				textView.background = Theme.getRoundRectSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.text, null))
				textView.setPadding(AndroidUtilities.dp(10f), 0, AndroidUtilities.dp(10f), 0)

				if (verticalButtons) {
					buttonsLayout?.addView(textView, 1, createLinear(LayoutHelper.WRAP_CONTENT, 36, if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT))
				}
				else {
					buttonsLayout?.addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP or Gravity.LEFT))
				}

				textView.setOnClickListener {
					neutralButtonListener?.onClick(this@AlertDialog, BUTTON_NEGATIVE)

					if (dismissDialogByButtons) {
						dismiss()
					}
				}
			}

			if (verticalButtons) {
				for (i in 1 until buttonsLayout!!.childCount) {
					(buttonsLayout?.getChildAt(i)?.layoutParams as? MarginLayoutParams)?.topMargin = AndroidUtilities.dp(6f)
				}
			}
		}

		val window = window

		val params = WindowManager.LayoutParams()
		params.copyFrom(window!!.attributes)

		if (progressViewStyle == 3) {
			params.width = WindowManager.LayoutParams.MATCH_PARENT
		}
		else {
			if (dimEnabled && !dimCustom) {
				params.dimAmount = dimAlpha
				params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
			}
			else {
				params.dimAmount = 0f
				params.flags = params.flags xor WindowManager.LayoutParams.FLAG_DIM_BEHIND
			}

			lastScreenWidth = AndroidUtilities.displaySize.x

			val calculatedWidth = AndroidUtilities.displaySize.x - AndroidUtilities.dp(48f)

			val maxWidth = if (AndroidUtilities.isTablet()) {
				if (AndroidUtilities.isSmallTablet()) {
					AndroidUtilities.dp(446f)
				}
				else {
					AndroidUtilities.dp(496f)
				}
			}
			else {
				AndroidUtilities.dp(356f)
			}

			params.width = min(maxWidth, calculatedWidth) + backgroundPaddings.left + backgroundPaddings.right
		}

		if (customView == null || !checkFocusable || !canTextInput(customView!!)) {
			params.flags = params.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
		}
		else {
			params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
		}

		if (Build.VERSION.SDK_INT >= 28) {
			params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
		}

		window.attributes = params
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		super.onBackPressed()
		onBackButtonListener?.onClick(this@AlertDialog, BUTTON_NEGATIVE)
	}

	fun setFocusable(value: Boolean) {
		if (focusable == value) {
			return
		}

		focusable = value

		val window = window
		val params = window!!.attributes

		if (focusable) {
			params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
			params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
		}
		else {
			params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
			params.flags = params.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
		}

		window.attributes = params
	}

	fun setBackgroundColor(color: Int) {
		shadowDrawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
	}

	fun setTextColor(color: Int) {
		titleTextView?.setTextColor(color)
		messageTextView?.setTextColor(color)
	}

	private fun showCancelAlert() {
		if (!canCacnel || cancelDialog != null) {
			return
		}

		val builder = Builder(context)
		builder.setTitle(context.getString(R.string.AppName))
		builder.setMessage(context.getString(R.string.StopLoading))
		builder.setPositiveButton(context.getString(R.string.WaitMore), null)

		builder.setNegativeButton(context.getString(R.string.Stop)) { _, _ ->
			onCancelListener?.onCancel(this@AlertDialog)
			dismiss()
		}

		builder.setOnDismissListener {
			cancelDialog = null
		}

		try {
			cancelDialog = builder.show()
		}
		catch (e: Exception) {
			// ignored
		}
	}

	private fun runShadowAnimation(num: Int, show: Boolean) {
		if (show && !shadowVisibility[num] || !show && shadowVisibility[num]) {
			shadowVisibility[num] = show

			shadowAnimation[num]?.cancel()

			shadowAnimation[num] = AnimatorSet()

			if (shadow[num] != null) {
				shadowAnimation[num]?.playTogether(ObjectAnimator.ofInt(shadow[num], "alpha", if (show) 255 else 0))
			}

			shadowAnimation[num]?.duration = 150

			shadowAnimation[num]?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (shadowAnimation[num] != null && shadowAnimation[num] == animation) {
						shadowAnimation[num] = null
					}
				}

				override fun onAnimationCancel(animation: Animator) {
					if (shadowAnimation[num] != null && shadowAnimation[num] == animation) {
						shadowAnimation[num] = null
					}
				}
			})

			try {
				shadowAnimation[num]?.start()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun setProgressStyle(style: Int) {
		progressViewStyle = style
	}

	fun setDismissDialogByButtons(value: Boolean) {
		dismissDialogByButtons = value
	}

	fun setProgress(progress: Int) {
		currentProgress = progress

		if (lineProgressView != null) {
			lineProgressView?.setProgress(progress / 100.0f, true)
			updateLineProgressTextView()
		}
	}

	private fun updateLineProgressTextView() {
		lineProgressViewPercent?.text = String.format(Locale.getDefault(), "%d%%", currentProgress)
	}

	fun setCanCancel(value: Boolean) {
		canCacnel = value
	}

	private fun canTextInput(v: View): Boolean {
		@Suppress("NAME_SHADOWING") var v = v

		if (v.onCheckIsTextEditor()) {
			return true
		}

		if (v !is ViewGroup) {
			return false
		}

		val vg = v
		var i = vg.childCount

		while (i > 0) {
			i--

			v = vg.getChildAt(i)

			if (canTextInput(v)) {
				return true
			}
		}

		return false
	}

	override fun dismiss() {
		onDismissListener?.onDismiss(this)
		cancelDialog?.dismiss()

		try {
			super.dismiss()
		}
		catch (e: Throwable) {
			// ignored
		}

		AndroidUtilities.cancelRunOnUIThread(showRunnable)
	}

	fun setTopImage(resId: Int, backgroundColor: Int) {
		topResId = resId
		topBackgroundColor = backgroundColor
	}

	fun setTopAnimation(resId: Int, backgroundColor: Int) {
		setTopAnimation(resId, 94, backgroundColor)
	}

	fun setTopAnimation(resId: Int, size: Int, backgroundColor: Int) {
		topAnimationId = resId
		topAnimationSize = size
		topBackgroundColor = backgroundColor
	}

	fun setTopHeight(value: Int) {
		topHeight = value
	}

	fun setTopImage(drawable: Drawable?, backgroundColor: Int) {
		topDrawable = drawable
		topBackgroundColor = backgroundColor
	}

	override fun setTitle(text: CharSequence?) {
		title = text
		titleTextView?.text = text
	}

	fun setSecondTitle(text: CharSequence?) {
		secondTitle = text
	}

	fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?) {
		positiveButtonText = text
		positiveButtonListener = listener
	}

	fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?) {
		negativeButtonText = text
		negativeButtonListener = listener
	}

	fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?) {
		neutralButtonText = text
		neutralButtonListener = listener
	}

	fun setItemColor(item: Int, color: Int, icon: Int) {
		if (item < 0 || item >= itemViews.size) {
			return
		}

		val cell = itemViews[item]
		cell.textView.setTextColor(color)
		cell.imageView.colorFilter = PorterDuffColorFilter(icon, PorterDuff.Mode.MULTIPLY)
	}

	val itemsCount: Int
		get() = itemViews.size

	fun setMessage(text: CharSequence?) {
		message = text

		messageTextView?.let { messageTextView ->
			if (!message.isNullOrEmpty()) {
				messageTextView.text = message
				messageTextView.visible()
			}
			else {
				messageTextView.gone()
			}
		}
	}

	fun setMessageTextViewClickable(value: Boolean) {
		messageTextViewClickable = value
	}

	fun setButton(type: Int, text: CharSequence?, listener: DialogInterface.OnClickListener?) {
		when (type) {
			BUTTON_NEUTRAL -> {
				neutralButtonText = text
				neutralButtonListener = listener
			}

			BUTTON_NEGATIVE -> {
				negativeButtonText = text
				negativeButtonListener = listener
			}

			BUTTON_POSITIVE -> {
				positiveButtonText = text
				positiveButtonListener = listener
			}
		}
	}

	fun getButton(type: Int): View? {
		return buttonsLayout?.findViewWithTag(type)
	}

	override fun invalidateDrawable(who: Drawable) {
		contentScrollView?.invalidate()
		scrollContainer?.invalidate()
	}

	override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
		contentScrollView?.postDelayed(what, `when`)
	}

	override fun unscheduleDrawable(who: Drawable, what: Runnable) {
		contentScrollView?.removeCallbacks(what)
	}

	override fun setOnCancelListener(listener: DialogInterface.OnCancelListener?) {
		onCancelListener = listener
		super.setOnCancelListener(listener)
	}

	fun setPositiveButtonListener(listener: DialogInterface.OnClickListener?) {
		positiveButtonListener = listener
	}

	fun showDelayed(delay: Long) {
		AndroidUtilities.cancelRunOnUIThread(showRunnable)
		AndroidUtilities.runOnUIThread(showRunnable, delay)
	}

	val themeDescriptions: ArrayList<ThemeDescription>?
		get() = null

	class AlertDialogCell(context: Context) : FrameLayout(context) {
		val textView: TextView
		val imageView: ImageView

		init {
			background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.text, null), 2)

			setPadding(AndroidUtilities.dp(23f), 0, AndroidUtilities.dp(23f), 0)

			imageView = ImageView(context)
			imageView.scaleType = ImageView.ScaleType.CENTER
			imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand_day_night, null), PorterDuff.Mode.MULTIPLY)

			addView(imageView, createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT))

			textView = TextView(context)
			textView.setLines(1)
			textView.isSingleLine = true
			textView.gravity = Gravity.CENTER_HORIZONTAL
			textView.ellipsize = TextUtils.TruncateAt.END
			textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)

			addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL))
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48f), MeasureSpec.EXACTLY))
		}

		fun setTextColor(color: Int) {
			textView.setTextColor(color)
		}

		fun setGravity(gravity: Int) {
			textView.gravity = gravity
		}

		fun setTextAndIcon(text: CharSequence, icon: Int) {
			textView.text = text

			if (icon != 0) {
				imageView.setImageResource(icon)
				imageView.visibility = VISIBLE
				textView.setPadding(if (LocaleController.isRTL) 0 else AndroidUtilities.dp(56f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(56f) else 0, 0)
			}
			else {
				imageView.visibility = INVISIBLE
				textView.setPadding(0, 0, 0, 0)
			}
		}
	}

	open class Builder {
		private val alertDialog: AlertDialog

		protected constructor(alert: AlertDialog) {
			alertDialog = alert
		}

		@JvmOverloads
		constructor(context: Context, progressViewStyle: Int = 0) {
			alertDialog = AlertDialog(context, progressViewStyle)
		}

		val context: Context
			get() = alertDialog.context

		fun setPositiveButtonColor(color: Int): Builder {
			alertDialog.positiveButtonColor = color
			return this
		}

		fun setNegativeButtonColor(color: Int): Builder {
			alertDialog.negativeButtonColor = color
			return this
		}

		fun forceVerticalButtons(): Builder {
			alertDialog.verticalButtons = true
			return this
		}

		fun setItems(items: Array<CharSequence?>?, onClickListener: DialogInterface.OnClickListener?): Builder {
			alertDialog.items = items
			alertDialog.onClickListener = onClickListener
			return this
		}

		fun setCheckFocusable(value: Boolean): Builder {
			alertDialog.checkFocusable = value
			return this
		}

		fun setItems(items: Array<CharSequence?>?, icons: IntArray?, onClickListener: DialogInterface.OnClickListener?): Builder {
			alertDialog.items = items
			alertDialog.itemIcons = icons
			alertDialog.onClickListener = onClickListener
			return this
		}

		fun setView(view: View?): Builder {
			return setView(view, LayoutHelper.WRAP_CONTENT)
		}

		fun setView(view: View?, height: Int): Builder {
			alertDialog.customView = view
			alertDialog.customViewHeight = height
			return this
		}

		fun setTitle(title: CharSequence?): Builder {
			alertDialog.title = title
			return this
		}

		fun setSubtitle(subtitle: CharSequence?): Builder {
			alertDialog.subtitle = subtitle
			return this
		}

		fun setTopImage(resId: Int, backgroundColor: Int): Builder {
			alertDialog.topResId = resId
			alertDialog.topBackgroundColor = backgroundColor
			return this
		}

		fun setTopView(view: View?): Builder {
			alertDialog.topView = view
			return this
		}

		fun setTopAnimation(resId: Int, size: Int, autoRepeat: Boolean, backgroundColor: Int): Builder {
			alertDialog.topAnimationId = resId
			alertDialog.topAnimationSize = size
			alertDialog.topAnimationAutoRepeat = autoRepeat
			alertDialog.topBackgroundColor = backgroundColor
			return this
		}

		fun setTopAnimation(resId: Int, backgroundColor: Int): Builder {
			return setTopAnimation(resId, 94, true, backgroundColor)
		}

		fun setTopImage(drawable: Drawable?, backgroundColor: Int): Builder {
			alertDialog.topDrawable = drawable
			alertDialog.topBackgroundColor = backgroundColor
			return this
		}

		fun setMessage(message: CharSequence?): Builder {
			alertDialog.message = message
			return this
		}

		fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder {
			alertDialog.positiveButtonText = text
			alertDialog.positiveButtonListener = listener
			return this
		}

		fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder {
			alertDialog.negativeButtonText = text
			alertDialog.negativeButtonListener = listener
			return this
		}

		fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder {
			alertDialog.neutralButtonText = text
			alertDialog.neutralButtonListener = listener
			return this
		}

		fun setOnBackButtonListener(listener: DialogInterface.OnClickListener?): Builder {
			alertDialog.onBackButtonListener = listener
			return this
		}

		fun setOnCancelListener(listener: DialogInterface.OnCancelListener?): Builder {
			alertDialog.setOnCancelListener(listener)
			return this
		}

		fun setCustomViewOffset(offset: Int): Builder {
			alertDialog.customViewOffset = offset
			return this
		}

		fun setMessageTextViewClickable(value: Boolean): Builder {
			alertDialog.messageTextViewClickable = value
			return this
		}

		fun create(): AlertDialog {
			return alertDialog
		}

		fun show(): AlertDialog {
			alertDialog.show()
			return alertDialog
		}

		fun getDismissRunnable(): Runnable {
			return alertDialog.dismissRunnable
		}

		fun setOnDismissListener(onDismissListener: DialogInterface.OnDismissListener?): Builder {
			alertDialog.setOnDismissListener(onDismissListener)
			return this
		}

		fun setTopViewAspectRatio(aspectRatio: Float) {
			alertDialog.aspectRatio = aspectRatio
		}

		fun setDimEnabled(dimEnabled: Boolean): Builder {
			alertDialog.dimEnabled = dimEnabled
			return this
		}

		fun setDimAlpha(dimAlpha: Float): Builder {
			alertDialog.dimAlpha = dimAlpha
			return this
		}

		fun notDrawBackgroundOnTopView(b: Boolean) {
			alertDialog.notDrawBackgroundOnTopView = b
		}

		fun setButtonsVertical(vertical: Boolean) {
			alertDialog.verticalButtons = vertical
		}

		fun setOnPreDismissListener(onDismissListener: DialogInterface.OnDismissListener?): Builder {
			alertDialog.onDismissListener = onDismissListener
			return this
		}
	}
}
