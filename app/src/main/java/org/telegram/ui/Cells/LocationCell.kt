/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ShapeDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC.TL_messageMediaVenue
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LocationCell(context: Context, private val wrapContent: Boolean) : FrameLayout(context) {
	private val nameTextView = TextView(context)
	private val addressTextView = TextView(context)
	private val circleDrawable: ShapeDrawable
	private var needDivider = false
	private var enterAlpha = 0f
	private var enterAnimator: ValueAnimator? = null
	val imageView = BackupImageView(context)

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (wrapContent) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
		}
		else {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
		}
	}

	fun setLocation(location: TL_messageMediaVenue?, icon: String?, pos: Int, divider: Boolean) {
		setLocation(location, icon, null, pos, divider)
	}

	private fun setLocation(location: TL_messageMediaVenue?, icon: String?, label: String?, pos: Int, divider: Boolean) {
		needDivider = divider
		circleDrawable.paint.color = getColorForIndex(pos)

		nameTextView.text = location?.title

		if (label != null) {
			addressTextView.text = label
		}
		else if (location != null) {
			addressTextView.text = location.address
		}
		else {
			addressTextView.text = null
		}

		imageView.setImage(icon, null, null)

		setWillNotDraw(false)

		isClickable = location == null

		enterAnimator?.cancel()

		val loading = location == null
		val fromEnterAlpha = enterAlpha
		val toEnterAlpha = if (loading) 0f else 1f
		val duration = (abs(fromEnterAlpha - toEnterAlpha) * 150).toLong()

		enterAnimator = ValueAnimator.ofFloat(fromEnterAlpha, toEnterAlpha)

		val start = SystemClock.elapsedRealtime()

		enterAnimator?.addUpdateListener {
			var t = min(max((SystemClock.elapsedRealtime() - start).toFloat() / duration, 0f), 1f)

			if (duration <= 0) {
				t = 1f
			}

			enterAlpha = AndroidUtilities.lerp(fromEnterAlpha, toEnterAlpha, t)
			imageView.alpha = enterAlpha
			nameTextView.alpha = enterAlpha
			addressTextView.alpha = enterAlpha

			invalidate()
		}

		enterAnimator?.duration = if (loading) Long.MAX_VALUE else duration
		enterAnimator?.start()

		imageView.alpha = fromEnterAlpha
		nameTextView.alpha = fromEnterAlpha
		addressTextView.alpha = fromEnterAlpha

		invalidate()
	}

	init {
		imageView.background = Theme.createCircleDrawable(AndroidUtilities.dp(42f), -0x1).also { circleDrawable = it }
		imageView.setSize(AndroidUtilities.dp(30f), AndroidUtilities.dp(30f))

		addView(imageView, LayoutHelper.createFrame(42, 42f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, (if (LocaleController.isRTL) 0 else 15).toFloat(), 11f, (if (LocaleController.isRTL) 15 else 0).toFloat(), 0f))

		nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		nameTextView.maxLines = 1
		nameTextView.ellipsize = TextUtils.TruncateAt.END
		nameTextView.isSingleLine = true
		nameTextView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
		nameTextView.typeface = Theme.TYPEFACE_BOLD
		nameTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

		addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, (if (LocaleController.isRTL) 16 else 73).toFloat(), 10f, (if (LocaleController.isRTL) 73 else 16).toFloat(), 0f))

		addressTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		addressTextView.maxLines = 1
		addressTextView.ellipsize = TextUtils.TruncateAt.END
		addressTextView.isSingleLine = true
		addressTextView.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
		addressTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

		addView(addressTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, (if (LocaleController.isRTL) 16 else 73).toFloat(), 35f, (if (LocaleController.isRTL) 73 else 16).toFloat(), 0f))

		imageView.alpha = enterAlpha
		nameTextView.alpha = enterAlpha
		addressTextView.alpha = enterAlpha
	}

	override fun onDraw(canvas: Canvas) {
		if (globalGradientView == null) {
			globalGradientView = FlickerLoadingView(context)
			globalGradientView?.setIsSingleCell(true)
		}

		val index = if (parent is ViewGroup) (parent as ViewGroup).indexOfChild(this) else 0

		globalGradientView?.setParentSize(measuredWidth, measuredHeight, (-index * AndroidUtilities.dp(56f)).toFloat())
		globalGradientView?.setViewType(FlickerLoadingView.AUDIO_TYPE)
		globalGradientView?.updateColors()
		globalGradientView?.updateGradient()

		canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), ((1f - enterAlpha) * 255).toInt())

		canvas.translate(AndroidUtilities.dp(2f).toFloat(), ((measuredHeight - AndroidUtilities.dp(56f)) / 2).toFloat())

		globalGradientView?.draw(canvas)

		canvas.restore()

		super.onDraw(canvas)

		if (needDivider) {
			canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(72f)).toFloat(), (height - 1).toFloat(), (if (LocaleController.isRTL) width - AndroidUtilities.dp(72f) else width).toFloat(), (height - 1).toFloat(), Theme.dividerPaint)
		}
	}

	companion object {
		@JvmStatic
		fun getColorForIndex(index: Int): Int {
			return when (index % 7) {
				0 -> -0x149fa0
				1 -> -0xd3fb5
				2 -> -0xba620b
				3 -> -0xc9389a
				4 -> -0x788e03
				5 -> -0xbc4629
				6 -> -0x139c75
				else -> -0x139c75
			}
		}

		private var globalGradientView: FlickerLoadingView? = null
	}
}
