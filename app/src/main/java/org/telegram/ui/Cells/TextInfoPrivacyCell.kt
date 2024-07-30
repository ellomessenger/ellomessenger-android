/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LinkSpanDrawable.LinkCollector
import org.telegram.ui.Components.LinkSpanDrawable.LinksTextView

open class TextInfoPrivacyCell @JvmOverloads constructor(context: Context, padding: Int = 21) : FrameLayout(context) {
	val textView: TextView
	private var links: LinkCollector? = null
	private var topPadding = 10
	private var bottomPadding = 17
	private var fixedSize = 0
	private var text: CharSequence? = null

	init {
		textView = object : LinksTextView(context, LinkCollector(this).also { links = it }) {
			override fun onDraw(canvas: Canvas) {
				onTextDraw()
				super.onDraw(canvas)
				afterTextDraw()
			}
		}

		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		textView.setPadding(0, AndroidUtilities.dp(10f), 0, AndroidUtilities.dp(17f))
		textView.setMovementMethod(LinkMovementMethod.getInstance())
		textView.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
		textView.setLinkTextColor(ResourcesCompat.getColor(resources, R.color.brand, null))
		textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, padding.toFloat(), 0f, padding.toFloat(), 0f))

		setWillNotDraw(false)
	}

	override fun onDraw(canvas: Canvas) {
		if (links != null) {
			canvas.save()
			canvas.translate(textView.left.toFloat(), textView.top.toFloat())

			if (links?.draw(canvas) == true) {
				invalidate()
			}

			canvas.restore()
		}

		super.onDraw(canvas)
	}

	protected open fun onTextDraw() {

	}

	protected open fun afterTextDraw() {

	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (fixedSize != 0) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(fixedSize.toFloat()), MeasureSpec.EXACTLY))
		}
		else {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
		}
	}

	fun setTopPadding(topPadding: Int) {
		this.topPadding = topPadding
	}

	fun setBottomPadding(value: Int) {
		bottomPadding = value
	}

	fun setFixedSize(size: Int) {
		fixedSize = size
	}

	fun setText(text: CharSequence?) {
		if (!TextUtils.equals(text, this.text)) {
			this.text = text

			if (text == null) {
				textView.setPadding(0, AndroidUtilities.dp(2f), 0, 0)
			}
			else {
				textView.setPadding(0, AndroidUtilities.dp(topPadding.toFloat()), 0, AndroidUtilities.dp(bottomPadding.toFloat()))
			}

			var spannableString: SpannableString? = null

			if (text != null) {
				var i = 0
				val len = text.length

				while (i < len - 1) {
					if (text[i] == '\n' && text[i + 1] == '\n') {
						if (spannableString == null) {
							spannableString = SpannableString(text)
						}

						spannableString.setSpan(AbsoluteSizeSpan(10, true), i + 1, i + 2, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
					}

					i++
				}
			}

			textView.text = spannableString ?: text
		}
	}

	fun setTextColor(color: Int) {
		textView.setTextColor(color)
	}

	fun length(): Int {
		return textView.length()
	}

	fun setEnabled(value: Boolean, animators: ArrayList<Animator>?) {
		if (animators != null) {
			animators.add(ObjectAnimator.ofFloat(textView, ALPHA, if (value) 1.0f else 0.5f))
		}
		else {
			textView.alpha = if (value) 1.0f else 0.5f
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = TextView::class.java.name
		info.text = text
	}
}
