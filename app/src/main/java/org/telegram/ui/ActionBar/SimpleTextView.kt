/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.ActionBar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Region
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.ui.Components.FixedWidthSpan
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedEmojiSpan.EmojiGroupedSpans
import org.telegram.ui.Components.EmptyStubSpan
import org.telegram.ui.Components.StaticLayoutEx
import org.telegram.ui.Components.spoilers.SpoilerEffect
import java.util.Stack
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

open class SimpleTextView(context: Context) : View(context), Drawable.Callback {
	@JvmField
	val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

	private val spoilers = mutableListOf<SpoilerEffect>()
	private val spoilersPool = Stack<SpoilerEffect>()
	private val path = Path()
	private var layout: Layout? = null
	private var firstLineLayout: Layout? = null
	private var fullLayout: Layout? = null
	private var partLayout: Layout? = null
	private var gravity = Gravity.LEFT or Gravity.TOP
	private var maxLines = 1
	private var text: CharSequence? = null
	private var replacedDrawable: Drawable? = null
	private var replacedText: String? = null
	private var replacingDrawableTextIndex = 0
	private var replacingDrawableTextOffset = 0f
	private var rightDrawableScale = 1.0f
	private var drawablePadding = AndroidUtilities.dp(2f)
	private var leftDrawableTopPadding = 0
	private var rightDrawableTopPadding = 0
	private var buildFullLayout = false
	private var fullAlpha = 0f
	private var wrapBackgroundDrawable: Drawable? = null
	private var scrollNonFitText = false
	private var textDoesNotFit = false
	private var scrollingOffset = 0f
	private var lastUpdateTime: Long = 0
	private var currentScrollDelay = 0
	private var fadePaint: Paint? = null
	private var fadePaintBack: Paint? = null
	private var fadeEllipsizePaint: Paint? = null
	private var lastWidth = 0
	private var offsetX = 0
	private var offsetY = 0
	private var totalWidth = 0
	private var wasLayout = false
	private var ellipsizeByGradient = false
	private var paddingRight = 0
	private var minWidth = 0
	private var fullLayoutAdditionalWidth = 0
	private var fullLayoutLeftOffset = 0
	private var fullLayoutLeftCharactersOffset = 0f
	private var minusWidth = 0
	private var fullTextMaxLines = 3
	private var canHideRightDrawable = false
	private var rightDrawableHidden = false
	private var rightDrawableOnClickListener: OnClickListener? = null
	private var maybeClick = false
	private var touchDownX = 0f
	private var touchDownY = 0f
	private var emojiStack: EmojiGroupedSpans? = null
	private var attachedToWindow = false
	var rightDrawableOutside = false
	var rightDrawableX = 0
	var rightDrawableY = 0

	var leftDrawable: Drawable? = null
		set(value) {
			if (field === value) {
				return
			}

			field?.callback = null

			field = value

			if (value != null) {
				value.callback = this
			}

			if (!recreateLayoutMaybe()) {
				invalidate()
			}
		}

	var rightDrawable: Drawable? = null
		set(value) {
			if (field === value) {
				return
			}

			field?.callback = null

			field = value

			if (value != null) {
				value.callback = this
			}

			if (!recreateLayoutMaybe()) {
				invalidate()
			}
		}

	var textWidth = 0
		private set

	var textHeight = 0
		private set

	init {
		textPaint.typeface = Theme.TYPEFACE_DEFAULT
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
	}

	fun setLinkTextColor(color: Int) {
		textPaint.linkColor = color
		invalidate()
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		attachedToWindow = true
		emojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, emojiStack, layout)
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attachedToWindow = false
		AnimatedEmojiSpan.release(emojiStack)
		wasLayout = false
	}

	fun setTextSize(size: Int) {
		val newSize = AndroidUtilities.dp(size.toFloat())

		if (newSize.toFloat() == textPaint.textSize) {
			return
		}

		textPaint.textSize = newSize.toFloat()

		if (!recreateLayoutMaybe()) {
			invalidate()
		}
	}

	fun setBuildFullLayout(value: Boolean) {
		buildFullLayout = value
	}

	fun getFullAlpha(): Float {
		return fullAlpha
	}

	open fun setFullAlpha(value: Float) {
		fullAlpha = value
		invalidate()
	}

	fun setScrollNonFitText(value: Boolean) {
		if (scrollNonFitText == value) {
			return
		}

		scrollNonFitText = value

		updateFadePaints()
		requestLayout()
	}

	fun setEllipsizeByGradient(value: Boolean) {
		if (scrollNonFitText == value) {
			return
		}

		ellipsizeByGradient = value

		updateFadePaints()
	}

	private fun updateFadePaints() {
		if ((fadePaint == null || fadePaintBack == null) && scrollNonFitText) {
			fadePaint = Paint()
			fadePaint?.shader = LinearGradient(0f, 0f, AndroidUtilities.dp(6f).toFloat(), 0f, intArrayOf(-0x1, 0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
			fadePaint?.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

			fadePaintBack = Paint()
			fadePaintBack?.shader = LinearGradient(0f, 0f, AndroidUtilities.dp(6f).toFloat(), 0f, intArrayOf(0, -0x1), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
			fadePaintBack?.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
		}

		if (fadeEllipsizePaint == null && ellipsizeByGradient) {
			fadeEllipsizePaint = Paint()
			fadeEllipsizePaint?.shader = LinearGradient(0f, 0f, AndroidUtilities.dp(16f).toFloat(), 0f, intArrayOf(0, -0x1), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
			fadeEllipsizePaint?.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
		}
	}

	fun setMaxLines(value: Int) {
		maxLines = value
	}

	fun setGravity(value: Int) {
		gravity = value
	}

	fun setTypeface(typeface: Typeface?) {
		textPaint.typeface = typeface
	}

	val sideDrawablesSize: Int
		get() {
			var size = 0

			if (leftDrawable != null) {
				size += leftDrawable!!.intrinsicWidth + drawablePadding
			}

			if (rightDrawable != null) {
				val dw = (rightDrawable!!.intrinsicWidth * rightDrawableScale).toInt()
				size += dw + drawablePadding
			}

			return size
		}

	val paint: Paint
		get() = textPaint

	private fun calcOffset(width: Int) {
		val layout = layout ?: return
		val fullLayout = fullLayout
		val firstLineLayout = firstLineLayout

		if (layout.lineCount > 0) {
			textWidth = ceil(layout.getLineWidth(0).toDouble()).toInt()

			textHeight = fullLayout?.getLineBottom(fullLayout.lineCount - 1) ?: if (maxLines > 1 && layout.lineCount > 0) {
				layout.getLineBottom(layout.lineCount - 1)
			}
			else {
				layout.getLineBottom(0)
			}

			offsetX = if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL) {
				(width - textWidth) / 2 - layout.getLineLeft(0).toInt()
			}
			else if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.LEFT) {
				if (firstLineLayout != null) {
					-firstLineLayout.getLineLeft(0).toInt()
				}
				else {
					-layout.getLineLeft(0).toInt()
				}
			}
			else if (layout.getLineLeft(0) == 0f) {
				if (firstLineLayout != null) {
					(width - firstLineLayout.getLineWidth(0)).toInt()
				}
				else {
					width - textWidth
				}
			}
			else {
				-AndroidUtilities.dp(8f)
			}

			offsetX += paddingLeft
			textDoesNotFit = textWidth > width

			if (fullLayout != null && fullLayoutAdditionalWidth > 0) {
				fullLayoutLeftCharactersOffset = fullLayout.getPrimaryHorizontal(0) - (firstLineLayout?.getPrimaryHorizontal(0) ?: 0f)
			}
		}

		replacingDrawableTextOffset = if (replacingDrawableTextIndex >= 0) {
			layout.getPrimaryHorizontal(replacingDrawableTextIndex)
		}
		else {
			0f
		}
	}

	protected open fun createLayout(width: Int): Boolean {
		@Suppress("NAME_SHADOWING") var width = width
		var text = text

		replacingDrawableTextIndex = -1
		rightDrawableHidden = false

		if (text != null) {
			runCatching {
				if (leftDrawable != null) {
					width -= leftDrawable!!.intrinsicWidth
					width -= drawablePadding
				}

				var rightDrawableWidth = 0

				rightDrawable?.let {
					if (!rightDrawableOutside) {
						rightDrawableWidth = (it.intrinsicWidth * rightDrawableScale).toInt()
						width -= rightDrawableWidth
						width -= drawablePadding
					}
				}

				val replacedText = replacedText
				val replacedDrawable = replacedDrawable

				if (replacedText != null && replacedDrawable != null) {
					replacingDrawableTextIndex = text.toString().indexOf(replacedText)

					if (replacingDrawableTextIndex >= 0) {
						val builder = SpannableStringBuilder.valueOf(text)
						builder.setSpan(FixedWidthSpan(replacedDrawable.intrinsicWidth), replacingDrawableTextIndex, replacingDrawableTextIndex + replacedText.length, 0)
						text = builder
					}
					else {
						width -= replacedDrawable.intrinsicWidth
						width -= drawablePadding
					}
				}

				if (canHideRightDrawable && rightDrawableWidth != 0 && !rightDrawableOutside) {
					val string = TextUtils.ellipsize(text, textPaint, width.toFloat(), TextUtils.TruncateAt.END)

					if (text != string) {
						rightDrawableHidden = true
						width += rightDrawableWidth
						width += drawablePadding
					}
				}

				if (buildFullLayout) {
					var string = text ?: ""

					if (!ellipsizeByGradient) {
						string = TextUtils.ellipsize(string, textPaint, width.toFloat(), TextUtils.TruncateAt.END)
					}

					if (!ellipsizeByGradient && string != text) {
						fullLayout = StaticLayoutEx.createStaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width, fullTextMaxLines, false)

						if (fullLayout != null) {
							val end = fullLayout!!.getLineEnd(0)
							val start = fullLayout!!.getLineStart(1)
							val substr = text?.subSequence(0, end) ?: ""

							val full = SpannableStringBuilder.valueOf(text)
							full.setSpan(EmptyStubSpan(), 0, start, 0)

							var part: CharSequence

							part = if (end < string.length) {
								string.subSequence(end, string.length)
							}
							else {
								"…"
							}

							// firstLineLayout = StaticLayout(string, 0, string.length, textPaint, if (scrollNonFitText) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							firstLineLayout = StaticLayout.Builder.obtain(string, 0, string.length, textPaint, if (scrollNonFitText) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.0f).setIncludePad(false).setMaxLines(maxLines).build()

							// layout = StaticLayout(substr, 0, substr.length, textPaint, if (scrollNonFitText) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							layout = StaticLayout.Builder.obtain(substr, 0, substr.length, textPaint, if (scrollNonFitText) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.0f).setIncludePad(false).setMaxLines(maxLines).build()

							if (layout?.getLineLeft(0) != 0f) {
								part = "\u200F" + part
							}

							// partLayout = StaticLayout(part, 0, part.length, textPaint, if (scrollNonFitText) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							partLayout = StaticLayout.Builder.obtain(part, 0, part.length, textPaint, if (scrollNonFitText) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.0f).setIncludePad(false).setMaxLines(maxLines).build()

							fullLayout = StaticLayoutEx.createStaticLayout(full, textPaint, width + AndroidUtilities.dp(8f) + fullLayoutAdditionalWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width + fullLayoutAdditionalWidth, fullTextMaxLines, false)
						}
					}
					else {
						// layout = StaticLayout(string, 0, string.length, textPaint, if (scrollNonFitText || ellipsizeByGradient) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
						layout = StaticLayout.Builder.obtain(string, 0, string.length, textPaint, if (scrollNonFitText || ellipsizeByGradient) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.0f).setIncludePad(false).setMaxLines(maxLines).build()

						fullLayout = null
						partLayout = null
						firstLineLayout = null
					}
				}
				else if (maxLines > 1) {
					layout = StaticLayoutEx.createStaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width, maxLines, false)
				}
				else {
					val string = if (scrollNonFitText || ellipsizeByGradient) {
						text
					}
					else {
						TextUtils.ellipsize(text, textPaint, width.toFloat(), TextUtils.TruncateAt.END)
					} ?: ""

//					if (layout != null && TextUtils.equals(layout.getText(), string)) {
//                        calcOffset(width)
//                        return false
//                  }

					// layout = StaticLayout(string, 0, string.length, textPaint, if (scrollNonFitText || ellipsizeByGradient) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
					layout = StaticLayout.Builder.obtain(string, 0, string.length, textPaint, if (scrollNonFitText || ellipsizeByGradient) AndroidUtilities.dp(2000f) else width + AndroidUtilities.dp(8f)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.0f).setIncludePad(false).setMaxLines(maxLines).build()
				}

				spoilersPool.addAll(spoilers)
				spoilers.clear()

				if (layout?.text is Spannable) {
					SpoilerEffect.addSpoilers(this, layout, spoilersPool, spoilers)
				}

				calcOffset(width)
			}
		}
		else {
			layout = null
			textWidth = 0
			textHeight = 0
		}

		AnimatedEmojiSpan.release(emojiStack)

		if (attachedToWindow) {
			emojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, emojiStack, layout)
		}

		invalidate()

		return true
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val width = MeasureSpec.getSize(widthMeasureSpec)
		val height = MeasureSpec.getSize(heightMeasureSpec)

		if (lastWidth != AndroidUtilities.displaySize.x) {
			lastWidth = AndroidUtilities.displaySize.x
			scrollingOffset = 0f
			currentScrollDelay = SCROLL_DELAY_MS
		}

		createLayout(width - paddingLeft - getPaddingRight() - minusWidth - if (rightDrawableOutside && rightDrawable != null) rightDrawable!!.intrinsicWidth + drawablePadding else 0)

		val finalHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
			height
		}
		else {
			paddingTop + textHeight + paddingBottom
		}

		setMeasuredDimension(width, finalHeight)

		offsetY = if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) {
			paddingTop + (measuredHeight - paddingTop - paddingBottom - textHeight) / 2
		}
		else {
			paddingTop
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		wasLayout = true
	}

	fun setLeftDrawableTopPadding(value: Int) {
		leftDrawableTopPadding = value
	}

	fun setRightDrawableTopPadding(value: Int) {
		rightDrawableTopPadding = value
	}

	fun setLeftDrawable(resId: Int) {
		leftDrawable = if (resId == 0) null else ResourcesCompat.getDrawable(context.resources, resId, null)
	}

	fun setMinWidth(width: Int) {
		minWidth = width
	}

	@Deprecated("Deprecated in Java")
	override fun setBackgroundDrawable(background: Drawable) {
		if (maxLines > 1) {
			super.setBackgroundDrawable(background)
			return
		}

		wrapBackgroundDrawable = background
	}

	override fun getBackground(): Drawable? {
		return wrapBackgroundDrawable ?: super.getBackground()
	}

	fun replaceTextWithDrawable(drawable: Drawable?, replacedText: String?) {
		if (replacedDrawable === drawable) {
			return
		}

		replacedDrawable?.callback = null

		replacedDrawable = drawable

		if (drawable != null) {
			drawable.callback = this
		}

		if (!recreateLayoutMaybe()) {
			invalidate()
		}

		this.replacedText = replacedText
	}

//	fun setMinusWidth(value: Int) {
//		if (value == minusWidth) {
//			return
//		}
//
//		minusWidth = value
//
//		if (!recreateLayoutMaybe()) {
//			invalidate()
//		}
//	}

	fun setRightDrawable(resId: Int) {
		this.rightDrawable = if (resId == 0) null else ResourcesCompat.getDrawable(context.resources, resId, null)
	}

	fun setRightDrawableScale(scale: Float) {
		rightDrawableScale = scale
	}

	fun setSideDrawablesColor(color: Int) {
		Theme.setDrawableColor(rightDrawable, color)
		Theme.setDrawableColor(leftDrawable, color)
	}

	open fun setText(value: CharSequence?): Boolean {
		return setText(value, false)
	}

	fun setText(value: CharSequence?, force: Boolean): Boolean {
		if ((text == null && value == null) || (!force && text != null && text == value)) {
			return false
		}

		text = value
		scrollingOffset = 0f
		currentScrollDelay = SCROLL_DELAY_MS

		recreateLayoutMaybe()

		return true
	}

	fun setDrawablePadding(value: Int) {
		if (drawablePadding == value) {
			return
		}

		drawablePadding = value

		if (!recreateLayoutMaybe()) {
			invalidate()
		}
	}

	private fun recreateLayoutMaybe(): Boolean {
		if (wasLayout && measuredHeight != 0 && !buildFullLayout) {
			val result = createLayout(maxTextWidth - paddingLeft - getPaddingRight() - minusWidth)

			offsetY = if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) {
				(measuredHeight - textHeight) / 2
			}
			else {
				paddingTop
			}

			return result
		}
		else {
			requestLayout()
		}

		return true
	}

	fun getText(): CharSequence {
		return text ?: ""
	}

	val lineCount: Int
		get() {
			var count = 0
			count += (layout?.lineCount ?: 0)
			count += (fullLayout?.lineCount ?: 0)
			return count
		}

	val textStartX: Int
		get() {
			if (layout == null) {
				return 0
			}

			var textOffsetX = 0

			leftDrawable?.let {
				if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.LEFT) {
					textOffsetX += drawablePadding + it.intrinsicWidth
				}
			}

			replacedDrawable?.let {
				if (replacingDrawableTextIndex < 0) {
					if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.LEFT) {
						textOffsetX += drawablePadding + it.intrinsicWidth
					}
				}
			}

			return x.toInt() + offsetX + textOffsetX
		}

	val textStartY: Int
		get() = if (layout == null) 0 else y.toInt()

//	fun setRightPadding(padding: Int) {
//		paddingRight = padding
//	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		var textOffsetX = 0
		val fade = scrollNonFitText && (textDoesNotFit || scrollingOffset != 0f)
		var restore = Int.MIN_VALUE

		if (fade || ellipsizeByGradient) {
			restore = canvas.saveLayerAlpha(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), 255)
		}

		totalWidth = textWidth

		val leftDrawable = leftDrawable

		if (leftDrawable != null) {
			var x = -scrollingOffset.toInt()

			if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL) {
				x += offsetX
			}

			val y = if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) {
				(measuredHeight - leftDrawable.intrinsicHeight) / 2 + leftDrawableTopPadding
			}
			else {
				paddingTop + (textHeight - leftDrawable.intrinsicHeight) / 2 + leftDrawableTopPadding
			}

			leftDrawable.setBounds(x, y, x + leftDrawable.intrinsicWidth, y + leftDrawable.intrinsicHeight)
			leftDrawable.draw(canvas)

			if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.LEFT || gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL) {
				textOffsetX += drawablePadding + leftDrawable.intrinsicWidth
			}

			totalWidth += drawablePadding + leftDrawable.intrinsicWidth
		}

		val replacedDrawable = replacedDrawable

		if (replacedDrawable != null && replacedText != null) {
			var x = (-scrollingOffset + replacingDrawableTextOffset).toInt()

			if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL) {
				x += offsetX
			}

			val y = if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) {
				(measuredHeight - replacedDrawable.intrinsicHeight) / 2 + leftDrawableTopPadding
			}
			else {
				(textHeight - replacedDrawable.intrinsicHeight) / 2 + leftDrawableTopPadding
			}

			replacedDrawable.setBounds(x, y, x + replacedDrawable.intrinsicWidth, y + replacedDrawable.intrinsicHeight)
			replacedDrawable.draw(canvas)

			if (replacingDrawableTextIndex < 0) {
				if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.LEFT || gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL) {
					textOffsetX += drawablePadding + replacedDrawable.intrinsicWidth
				}

				totalWidth += drawablePadding + replacedDrawable.intrinsicWidth
			}
		}

		val rightDrawable = rightDrawable

		if (rightDrawable != null && !rightDrawableHidden && rightDrawableScale > 0 && !rightDrawableOutside) {
			var x = textOffsetX + textWidth + drawablePadding + -scrollingOffset.toInt()

			if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL || gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.RIGHT) {
				x += offsetX
			}

			val dw = (rightDrawable.intrinsicWidth * rightDrawableScale).toInt()
			val dh = (rightDrawable.intrinsicHeight * rightDrawableScale).toInt()

			val y = if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) {
				(measuredHeight - dh) / 2 + rightDrawableTopPadding
			}
			else {
				paddingTop + (textHeight - dh) / 2 + rightDrawableTopPadding
			}

			rightDrawable.setBounds(x, y, x + dw, y + dh)

			rightDrawableX = x + (dw shr 1)
			rightDrawableY = y + (dh shr 1)

			rightDrawable.draw(canvas)

			totalWidth += drawablePadding + dw
		}

		val nextScrollX = totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT.toFloat())

		if (scrollingOffset != 0f) {
			if (leftDrawable != null) {
				val x = -scrollingOffset.toInt() + nextScrollX

				val y = if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) {
					(measuredHeight - leftDrawable.intrinsicHeight) / 2 + leftDrawableTopPadding
				}
				else {
					paddingTop + (textHeight - leftDrawable.intrinsicHeight) / 2 + leftDrawableTopPadding
				}

				leftDrawable.setBounds(x, y, x + leftDrawable.intrinsicWidth, y + leftDrawable.intrinsicHeight)
				leftDrawable.draw(canvas)
			}

			if (rightDrawable != null && !rightDrawableOutside) {
				val dw = (rightDrawable.intrinsicWidth * rightDrawableScale).toInt()
				val dh = (rightDrawable.intrinsicHeight * rightDrawableScale).toInt()
				val x = textOffsetX + textWidth + drawablePadding + -scrollingOffset.toInt() + nextScrollX

				val y = if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) {
					(measuredHeight - dh) / 2 + rightDrawableTopPadding
				}
				else {
					paddingTop + (textHeight - dh) / 2 + rightDrawableTopPadding
				}

				rightDrawable.setBounds(x, y, x + dw, y + dh)
				rightDrawable.draw(canvas)
			}
		}

		if (layout != null) {
			if (rightDrawableOutside || ellipsizeByGradient) {
				canvas.save()
				canvas.clipRect(0, 0, maxTextWidth - paddingRight - AndroidUtilities.dp((if (rightDrawable != null && rightDrawable !is SwapAnimatedEmojiDrawable && rightDrawableOutside) 2 else 0).toFloat()), measuredHeight)
			}

			val usaAlphaForEmoji = false

			Emoji.emojiDrawingUseAlpha = usaAlphaForEmoji

			if (wrapBackgroundDrawable != null) {
				val cx = (offsetX + textOffsetX - scrollingOffset).toInt() + textWidth / 2
				val w = max(textWidth + paddingLeft + getPaddingRight(), minWidth)
				val x = cx - w / 2
				wrapBackgroundDrawable?.setBounds(x, 0, x + w, measuredHeight)
				wrapBackgroundDrawable?.draw(canvas)
			}

			if (offsetX + textOffsetX != 0 || offsetY != 0 || scrollingOffset != 0f) {
				canvas.save()
				canvas.translate(offsetX + textOffsetX - scrollingOffset, offsetY.toFloat())
			}

			drawLayout(canvas)

			if (partLayout != null && fullAlpha < 1.0f) {
				val prevAlpha = textPaint.alpha

				textPaint.alpha = (255 * (1.0f - fullAlpha)).toInt()

				canvas.save()

				var partOffset = 0f

				if (partLayout!!.text.length == 1) {
					partOffset = (if (fullTextMaxLines == 1) AndroidUtilities.dp(0.5f)
					else AndroidUtilities.dp(4f)).toFloat()
				}

				if (layout!!.getLineLeft(0) != 0f) {
					canvas.translate(-layout!!.getLineWidth(0) + partOffset, 0f)
				}
				else {
					canvas.translate(layout!!.getLineWidth(0) - partOffset, 0f)
				}

				canvas.translate(-fullLayoutLeftOffset * fullAlpha + fullLayoutLeftCharactersOffset * fullAlpha, 0f)

				partLayout!!.draw(canvas)

				canvas.restore()

				textPaint.alpha = prevAlpha
			}

			if (fullLayout != null && fullAlpha > 0) {
				val prevAlpha = textPaint.alpha
				textPaint.alpha = (255 * fullAlpha).toInt()
				canvas.translate(-fullLayoutLeftOffset * fullAlpha + fullLayoutLeftCharactersOffset * fullAlpha - fullLayoutLeftCharactersOffset, 0f)
				fullLayout!!.draw(canvas)
				textPaint.alpha = prevAlpha
			}

			if (scrollingOffset != 0f) {
				canvas.translate(nextScrollX.toFloat(), 0f)
				drawLayout(canvas)
			}

			if (offsetX + textOffsetX != 0 || offsetY != 0 || scrollingOffset != 0f) {
				canvas.restore()
			}

			if (fade) {
				if (scrollingOffset < AndroidUtilities.dp(10f)) {
					fadePaint?.alpha = (255 * (scrollingOffset / AndroidUtilities.dp(10f))).toInt()
				}
				else if (scrollingOffset > totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT.toFloat()) - AndroidUtilities.dp(10f)) {
					val dist = scrollingOffset - (totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT.toFloat()) - AndroidUtilities.dp(10f))
					fadePaint?.alpha = (255 * (1.0f - dist / AndroidUtilities.dp(10f))).toInt()
				}
				else {
					fadePaint?.alpha = 255
				}

				canvas.drawRect(0f, 0f, AndroidUtilities.dp(6f).toFloat(), measuredHeight.toFloat(), fadePaint!!)
				canvas.save()
				canvas.translate((maxTextWidth - paddingRight - AndroidUtilities.dp(6f)).toFloat(), 0f)
				canvas.drawRect(0f, 0f, AndroidUtilities.dp(6f).toFloat(), measuredHeight.toFloat(), fadePaintBack!!)
				canvas.restore()
			}
			else if (ellipsizeByGradient && fadeEllipsizePaint != null) {
				canvas.save()
				canvas.translate((maxTextWidth - paddingRight - AndroidUtilities.dp((if (rightDrawable != null && rightDrawable !is SwapAnimatedEmojiDrawable && rightDrawableOutside) 18 else 16).toFloat())).toFloat(), 0f)
				canvas.drawRect(0f, 0f, AndroidUtilities.dp(16f).toFloat(), measuredHeight.toFloat(), fadeEllipsizePaint!!)
				canvas.restore()
			}

			updateScrollAnimation()

			Emoji.emojiDrawingUseAlpha = true

			if (rightDrawableOutside) {
				canvas.restore()
			}
		}

		if (fade || ellipsizeByGradient) {
			canvas.restoreToCount(restore)
		}

		if (rightDrawable != null && rightDrawableOutside) {
			val x = min(textOffsetX + textWidth + drawablePadding + (if (scrollingOffset == 0f) -nextScrollX else -scrollingOffset.toInt()) + nextScrollX, maxTextWidth - paddingRight + drawablePadding - AndroidUtilities.dp(4f))
			val dw = (rightDrawable.intrinsicWidth * rightDrawableScale).toInt()
			val dh = (rightDrawable.intrinsicHeight * rightDrawableScale).toInt()

			val y = if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL) {
				(measuredHeight - dh) / 2 + rightDrawableTopPadding
			}
			else {
				paddingTop + (textHeight - dh) / 2 + rightDrawableTopPadding
			}

			rightDrawable.setBounds(x, y, x + dw, y + dh)

			rightDrawableX = x + (dw shr 1)
			rightDrawableY = y + (dh shr 1)

			rightDrawable.draw(canvas)
		}
	}

	private val maxTextWidth: Int
		get() = measuredWidth - if (rightDrawableOutside && rightDrawable != null) rightDrawable!!.intrinsicWidth + drawablePadding else 0

	private fun drawLayout(canvas: Canvas) {
		if (fullAlpha > 0 && fullLayoutLeftOffset != 0) {
			canvas.save()
			canvas.translate(-fullLayoutLeftOffset * fullAlpha + fullLayoutLeftCharactersOffset * fullAlpha, 0f)
			canvas.save()

			clipOutSpoilers(canvas)

			emojiStack?.clearPositions()

			layout?.draw(canvas)

			canvas.restore()

			AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, emojiStack, 0f, null, 0f, 0f, 0f, 1f)

			drawSpoilers(canvas)

			canvas.restore()
		}
		else {
			canvas.save()
			clipOutSpoilers(canvas)

			emojiStack?.clearPositions()

			layout?.draw(canvas)

			canvas.restore()

			AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, emojiStack, 0f, null, 0f, 0f, 0f, 1f)

			drawSpoilers(canvas)
		}
	}

	private fun clipOutSpoilers(canvas: Canvas) {
		path.rewind()

		for (eff in spoilers) {
			val b = eff.bounds
			path.addRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), Path.Direction.CW)
		}

		canvas.clipPath(path, Region.Op.DIFFERENCE)
	}

	private fun drawSpoilers(canvas: Canvas) {
		for (eff in spoilers) {
			eff.draw(canvas)
		}
	}

	private fun updateScrollAnimation() {
		if (!scrollNonFitText || !textDoesNotFit && scrollingOffset == 0f) {
			return
		}

		val newUpdateTime = SystemClock.elapsedRealtime()
		val dt = (newUpdateTime - lastUpdateTime).coerceAtMost(17)

		if (currentScrollDelay > 0) {
			currentScrollDelay -= dt.toInt()
		}
		else {
			val totalDistance = totalWidth + AndroidUtilities.dp(DIST_BETWEEN_SCROLLING_TEXT.toFloat())

			val pixelsPerSecond = if (scrollingOffset < AndroidUtilities.dp(SCROLL_SLOWDOWN_PX.toFloat())) {
				PIXELS_PER_SECOND_SLOW + (PIXELS_PER_SECOND - PIXELS_PER_SECOND_SLOW) * (scrollingOffset / AndroidUtilities.dp(SCROLL_SLOWDOWN_PX.toFloat()))
			}
			else if (scrollingOffset >= totalDistance - AndroidUtilities.dp(SCROLL_SLOWDOWN_PX.toFloat())) {
				val dist = scrollingOffset - (totalDistance - AndroidUtilities.dp(SCROLL_SLOWDOWN_PX.toFloat()))
				PIXELS_PER_SECOND - (PIXELS_PER_SECOND - PIXELS_PER_SECOND_SLOW) * (dist / AndroidUtilities.dp(SCROLL_SLOWDOWN_PX.toFloat()))
			}
			else {
				PIXELS_PER_SECOND.toFloat()
			}

			scrollingOffset += dt / 1000.0f * AndroidUtilities.dp(pixelsPerSecond)
			lastUpdateTime = newUpdateTime

			if (scrollingOffset > totalDistance) {
				scrollingOffset = 0f
				currentScrollDelay = SCROLL_DELAY_MS
			}
		}

		invalidate()
	}

	override fun invalidateDrawable(who: Drawable) {
		invalidate()
	}

	override fun hasOverlappingRendering(): Boolean {
		return false
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.isVisibleToUser = true
		info.className = "android.widget.TextView"
		info.text = text
	}

	fun setFullLayoutAdditionalWidth(fullLayoutAdditionalWidth: Int, fullLayoutLeftOffset: Int) {
		if (this.fullLayoutAdditionalWidth != fullLayoutAdditionalWidth || this.fullLayoutLeftOffset != fullLayoutLeftOffset) {
			this.fullLayoutAdditionalWidth = fullLayoutAdditionalWidth
			this.fullLayoutLeftOffset = fullLayoutLeftOffset
			createLayout(maxTextWidth - paddingLeft - getPaddingRight() - minusWidth)
		}
	}

	fun setFullTextMaxLines(fullTextMaxLines: Int) {
		this.fullTextMaxLines = fullTextMaxLines
	}

	var textColor: Int
		get() = textPaint.color
		set(color) {
			textPaint.color = color
			invalidate()
		}

	fun setCanHideRightDrawable(b: Boolean) {
		canHideRightDrawable = b
	}

	fun setRightDrawableOnClick(onClickListener: OnClickListener?) {
		rightDrawableOnClickListener = onClickListener
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (rightDrawableOnClickListener != null && rightDrawable != null) {
			AndroidUtilities.rectTmp[(rightDrawableX - AndroidUtilities.dp(16f)).toFloat(), (rightDrawableY - AndroidUtilities.dp(16f)).toFloat(), (rightDrawableX + AndroidUtilities.dp(16f)).toFloat()] = (rightDrawableY + AndroidUtilities.dp(16f)).toFloat()

			if (event.action == MotionEvent.ACTION_DOWN && AndroidUtilities.rectTmp.contains(event.x.toInt().toFloat(), event.y.toInt().toFloat())) {
				maybeClick = true
				touchDownX = event.x
				touchDownY = event.y
				parent.requestDisallowInterceptTouchEvent(true)
			}
			else if (event.action == MotionEvent.ACTION_MOVE && maybeClick) {
				if (abs(event.x - touchDownX) >= AndroidUtilities.touchSlop || abs(event.y - touchDownY) >= AndroidUtilities.touchSlop) {
					maybeClick = false
					parent.requestDisallowInterceptTouchEvent(false)
				}
			}
			else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
				if (maybeClick && event.action == MotionEvent.ACTION_UP) {
					rightDrawableOnClickListener!!.onClick(this)
				}

				maybeClick = false

				parent.requestDisallowInterceptTouchEvent(false)
			}
		}

		return super.onTouchEvent(event) || maybeClick
	}

	companion object {
		private const val PIXELS_PER_SECOND = 50
		private const val PIXELS_PER_SECOND_SLOW = 30
		private const val DIST_BETWEEN_SCROLLING_TEXT = 16
		private const val SCROLL_DELAY_MS = 500
		private const val SCROLL_SLOWDOWN_PX = 100
	}
}
