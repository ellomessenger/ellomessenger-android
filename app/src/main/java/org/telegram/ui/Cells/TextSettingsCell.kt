/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedTextView
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.RLottieImageView

class TextSettingsCell @JvmOverloads constructor(context: Context, private val padding: Int = 16) : FrameLayout(context) {
	var paint: Paint? = null
	val textView: TextView
	val valueImageView: ImageView
	private val valueTextView: AnimatedTextView
	private val imageView: ImageView
	private var valueBackupImageView: BackupImageView? = null
	private var needDivider = false
	private var canDisable = false
	private var drawLoading = false
	private var incrementLoadingProgress = false
	private var loadingProgress = 0f
	private var drawLoadingProgress = 0f
	private var loadingSize = 0
	private var measureDelay = false
	private var changeProgressStartDelay = 0

	init {
		textView = TextView(context)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		textView.setLines(1)
		textView.maxLines = 1
		textView.isSingleLine = true
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
		textView.typeface = Theme.TYPEFACE_DEFAULT
		textView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, padding.toFloat(), 0f, padding.toFloat(), 0f))

		valueTextView = AnimatedTextView(context, true, true, !LocaleController.isRTL)
		valueTextView.setAnimationProperties(.55f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT)
		valueTextView.setTextSize(AndroidUtilities.dp(14f).toFloat())
		valueTextView.setGravity((if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL)
		valueTextView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))

		addView(valueTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP, (padding + 7).toFloat(), 0f, (padding + 7).toFloat(), 0f))

		imageView = RLottieImageView(context)
		imageView.setScaleType(ImageView.ScaleType.CENTER)
		imageView.setColorFilter(PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.brand, null), PorterDuff.Mode.SRC_IN))
		imageView.gone()

		addView(imageView, createFrame(imageViewWidth, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, padding.toFloat(), 0f, padding.toFloat(), 0f))

		valueImageView = ImageView(context)
		valueImageView.scaleType = ImageView.ScaleType.CENTER
		valueImageView.invisible()
		valueImageView.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY)

		addView(valueImageView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, (padding + 7).toFloat(), 0f, (padding + 7).toFloat(), 0f))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(64f) + if (needDivider) 1 else 0)

		val availableWidth = measuredWidth - paddingLeft - paddingRight - AndroidUtilities.dp(34f)
		var width = availableWidth / 2

		if (valueImageView.visibility == VISIBLE) {
			valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
		}

		if (imageView.visibility == VISIBLE) {
			imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.AT_MOST))
		}

		valueBackupImageView?.measure(MeasureSpec.makeMeasureSpec(valueBackupImageView!!.layoutParams.height, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(valueBackupImageView!!.layoutParams.width, MeasureSpec.EXACTLY))

		if (valueTextView.visibility == VISIBLE) {
			valueTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))

			width = availableWidth - valueTextView.measuredWidth - AndroidUtilities.dp(8f)

			if (valueImageView.isVisible) {
				valueTextView.updateLayoutParams<MarginLayoutParams> {
					if (LocaleController.isRTL) {
						leftMargin = valueImageView.measuredWidth + AndroidUtilities.dp((padding + 7).toFloat()) + AndroidUtilities.dp(8f)
					}
					else {
						rightMargin = valueImageView.measuredWidth + AndroidUtilities.dp((padding + 7).toFloat()) + AndroidUtilities.dp(8f)
					}
				}
			}

//			if (valueImageView.visibility == VISIBLE) {
//				val params = valueImageView.layoutParams as MarginLayoutParams
//
//				if (LocaleController.isRTL) {
//					params.leftMargin = AndroidUtilities.dp((padding + 4).toFloat()) + valueTextView.measuredWidth
//				}
//				else {
//					params.rightMargin = AndroidUtilities.dp((padding + 4).toFloat()) + valueTextView.measuredWidth
//				}
//			}
		}
		else {
			width = availableWidth
		}

		textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		if (measureDelay && parent != null) {
			changeProgressStartDelay = (getTop() / (parent as View).measuredHeight.toFloat() * 150f).toInt()
		}
	}

	fun setCanDisable(value: Boolean) {
		canDisable = value
	}

	fun setTextColor(color: Int) {
		textView.setTextColor(color)
	}

	fun setTextValueColor(color: Int) {
		valueTextView.setTextColor(color)
	}

	fun setText(text: CharSequence?, divider: Boolean) {
		textView.text = text
		valueTextView.invisible()
		valueImageView.invisible()
		needDivider = divider
		setWillNotDraw(!divider)
	}

	fun setTextAndValue(text: CharSequence?, value: CharSequence?, divider: Boolean) {
		setTextAndValue(text, value, false, divider)
	}

	fun setTextAndValue(text: CharSequence?, value: CharSequence?, animated: Boolean, divider: Boolean) {
		textView.text = text
		valueImageView.invisible()

		if (value != null) {
			valueTextView.setText(value, animated)
			valueTextView.visible()
		}
		else {
			valueTextView.invisible()
		}

		needDivider = divider

		setWillNotDraw(!divider)

		requestLayout()
	}

	fun setTextAndValueAndIcon(text: CharSequence?, value: CharSequence?, icon: Int, animated: Boolean, divider: Boolean) {
		textView.text = text

		if (value != null) {
			valueTextView.setText(value, animated)
			valueTextView.visible()
		}
		else {
			valueTextView.invisible()
		}

		if (icon != 0) {
			valueImageView.visible()
			valueImageView.setImageResource(icon)
		}
		else {
			valueImageView.invisible()
		}

		needDivider = divider

		setWillNotDraw(!divider)

		requestLayout()
	}

	fun setTextAndIcon(text: CharSequence?, resId: Int, divider: Boolean) {
		textView.text = text

		valueTextView.invisible()

		if (resId != 0) {
			valueImageView.visible()
			valueImageView.setImageResource(resId)
		}
		else {
			valueImageView.invisible()
		}

		needDivider = divider

		setWillNotDraw(!divider)
	}

	@JvmOverloads
	fun setIcon(resId: Int, colorResId: Int = 0) {
		if (resId == 0) {
			imageView.gone()

			textView.updateLayoutParams<MarginLayoutParams> {
				leftMargin = AndroidUtilities.dp(padding.toFloat())
			}
		}
		else {
			imageView.setImageResource(resId)
			imageView.visible()

			textView.updateLayoutParams<MarginLayoutParams> {
				leftMargin = AndroidUtilities.dp(padding.toFloat()) + imageViewWidth + AndroidUtilities.dp(21f)
			}

			if(colorResId != 0) {
				imageView.colorFilter =
					PorterDuffColorFilter(ResourcesCompat.getColor(resources, colorResId, null), PorterDuff.Mode.SRC_IN)
			}
		}
	}

	fun setEnabled(value: Boolean, animators: ArrayList<Animator?>?) {
		isEnabled = value

		if (animators != null) {
			animators.add(ObjectAnimator.ofFloat(textView, "alpha", if (value) 1.0f else 0.5f))

			if (valueTextView.visibility == VISIBLE) {
				animators.add(ObjectAnimator.ofFloat(valueTextView, "alpha", if (value) 1.0f else 0.5f))
			}

			if (valueImageView.visibility == VISIBLE) {
				animators.add(ObjectAnimator.ofFloat(valueImageView, "alpha", if (value) 1.0f else 0.5f))
			}
		}
		else {
			textView.alpha = if (value) 1.0f else 0.5f

			if (valueTextView.visibility == VISIBLE) {
				valueTextView.alpha = if (value) 1.0f else 0.5f
			}

			if (valueImageView.visibility == VISIBLE) {
				valueImageView.alpha = if (value) 1.0f else 0.5f
			}
		}
	}

	override fun setEnabled(value: Boolean) {
		super.setEnabled(value)

		textView.alpha = if (value || !canDisable) 1.0f else 0.5f

		if (valueTextView.visibility == VISIBLE) {
			valueTextView.alpha = if (value || !canDisable) 1.0f else 0.5f
		}

		if (valueImageView.visibility == VISIBLE) {
			valueImageView.alpha = if (value || !canDisable) 1.0f else 0.5f
		}
	}

	override fun dispatchDraw(canvas: Canvas) {
		if (drawLoading || drawLoadingProgress != 0f) {
			if (paint == null) {
				paint = Paint(Paint.ANTI_ALIAS_FLAG)
				paint?.color = context.getColor(R.color.background)
			}

			if (incrementLoadingProgress) {
				loadingProgress += 16 / 1000f

				if (loadingProgress > 1f) {
					loadingProgress = 1f
					incrementLoadingProgress = false
				}
			}
			else {
				loadingProgress -= 16 / 1000f

				if (loadingProgress < 0) {
					loadingProgress = 0f
					incrementLoadingProgress = true
				}
			}

			if (changeProgressStartDelay > 0) {
				changeProgressStartDelay -= 15
			}
			else if (drawLoading && drawLoadingProgress != 1f) {
				drawLoadingProgress += 16 / 150f

				if (drawLoadingProgress > 1f) {
					drawLoadingProgress = 1f
				}
			}
			else if (!drawLoading && drawLoadingProgress != 0f) {
				drawLoadingProgress -= 16 / 150f

				if (drawLoadingProgress < 0) {
					drawLoadingProgress = 0f
				}
			}

			val alpha = (0.6f + 0.4f * loadingProgress) * drawLoadingProgress

			paint?.alpha = (255 * alpha).toInt()

			val cy = measuredHeight shr 1

			AndroidUtilities.rectTmp[(measuredWidth - AndroidUtilities.dp(padding.toFloat()) - AndroidUtilities.dp(loadingSize.toFloat())).toFloat(), (cy - AndroidUtilities.dp(3f)).toFloat(), (measuredWidth - AndroidUtilities.dp(padding.toFloat())).toFloat()] = (cy + AndroidUtilities.dp(3f)).toFloat()

			if (LocaleController.isRTL) {
				AndroidUtilities.rectTmp.left = measuredWidth - AndroidUtilities.rectTmp.left
				AndroidUtilities.rectTmp.right = measuredWidth - AndroidUtilities.rectTmp.right
			}

			canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3f).toFloat(), AndroidUtilities.dp(3f).toFloat(), paint!!)

			invalidate()
		}

		valueTextView.alpha = 1f - drawLoadingProgress

		super.dispatchDraw(canvas)

		if (needDivider) {
			canvas.drawLine(0f, (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.text = textView.text.toString() + if (valueTextView.visibility == VISIBLE) """
     
     ${valueTextView.text}
     """.trimIndent()
		else ""
		info.isEnabled = isEnabled
	}

	fun setDrawLoading(drawLoading: Boolean, size: Int, animated: Boolean) {
		this.drawLoading = drawLoading

		loadingSize = size

		if (!animated) {
			drawLoadingProgress = if (drawLoading) 1f else 0f
		}
		else {
			measureDelay = true
		}

		invalidate()
	}

	fun getValueBackupImageView(): BackupImageView {
		return valueBackupImageView ?: BackupImageView(context).also {
			addView(it, createFrame(24, 24f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, padding.toFloat(), 0f, padding.toFloat(), 0f))
			valueBackupImageView = it
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		(valueBackupImageView?.imageReceiver?.drawable as? AnimatedEmojiDrawable)?.removeView(this)
	}

	companion object {
		private val imageViewWidth = AndroidUtilities.dp(24f)
	}
}
