/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2022-2023.
 * With long click processing by: https://stackoverflow.com/questions/26813104/long-click-on-clickable-span-not-firing-until-click-is-released
 */
package org.telegram.messenger.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.R

class LinkTouchMovementMethod : LinkMovementMethod() {
	private var pressedSpan: ElloClickableSpan? = null
	private val longClickHandler = Handler(Looper.getMainLooper())
	private var isLongPressed = false

	override fun onTouchEvent(textView: TextView, spannable: Spannable, event: MotionEvent): Boolean {
		if (event.action == MotionEvent.ACTION_DOWN) {
			pressedSpan = getPressedSpan(textView, spannable, event)

			pressedSpan?.let {
				it.setPressed(true)

				Selection.setSelection(spannable, spannable.getSpanStart(it), spannable.getSpanEnd(it))

				longClickHandler.postDelayed({
					it.onLongClick()
					isLongPressed = true
				}, LONG_CLICK_TIME)
			}
		}
		else if (event.action == MotionEvent.ACTION_MOVE) {
			val touchedSpan = getPressedSpan(textView, spannable, event)

			if (pressedSpan != null && touchedSpan != pressedSpan) {
				longClickHandler.removeCallbacksAndMessages(null)
				pressedSpan?.setPressed(false)
				pressedSpan = null

				Selection.removeSelection(spannable)
			}
		}
		else {
			if (event.action == MotionEvent.ACTION_UP) {
				longClickHandler.removeCallbacksAndMessages(null)

				if (isLongPressed) {
					isLongPressed = false
					pressedSpan?.setPressed(false)
					pressedSpan = null

					Selection.removeSelection(spannable)

					return false
				}
				else {
					pressedSpan?.onClick(textView)
				}

				isLongPressed = false
			}

			longClickHandler.removeCallbacksAndMessages(null)

			pressedSpan?.setPressed(false)
			pressedSpan = null

			Selection.removeSelection(spannable)
		}

		return super.onTouchEvent(textView, spannable, event)
	}

	private fun getPressedSpan(textView: TextView, spannable: Spannable, event: MotionEvent): ElloClickableSpan? {
		var x = event.x.toInt()
		var y = event.y.toInt()

		x -= textView.totalPaddingLeft
		y -= textView.totalPaddingTop
		x += textView.scrollX
		y += textView.scrollY

		val layout = textView.layout
		val line = layout.getLineForVertical(y)
		val off = layout.getOffsetForHorizontal(line, x.toFloat())
		val link = spannable.getSpans(off, off, ElloClickableSpan::class.java)
		var touchedSpan: ElloClickableSpan? = null

		if (link.isNotEmpty()) {
			touchedSpan = link[0]
		}

		return touchedSpan
	}

	companion object {
		private const val LONG_CLICK_TIME = 500L
		val instance = LinkTouchMovementMethod()
	}
}

private class ElloClickableSpan(private val route: String, private val normalTextColor: Int, private val pressedTextColor: Int, private val clickListener: LinkClickListener?) : ClickableSpan() {
	private var isPressed = false

	fun setPressed(isSelected: Boolean) {
		isPressed = isSelected
	}

	override fun onClick(tv: View) {
		clickListener?.onClick(route)
	}

	fun onLongClick() {
		clickListener?.onLongClick(route)
	}

	override fun updateDrawState(ds: TextPaint) {
		super.updateDrawState(ds)
		ds.color = if (isPressed) pressedTextColor else normalTextColor
		ds.isUnderlineText = false
	}
}

interface LinkClickListener {
	fun onClick(route: String)
	fun onLongClick(route: String)
}

fun makeLinkClickable(strBuilder: SpannableStringBuilder, span: URLSpan, context: Context, clickListener: LinkClickListener?) {
	val normalTextColor = ResourcesCompat.getColor(context.resources, R.color.brand, null)
	makeLinkClickable(strBuilder, span, normalTextColor, context, clickListener)
}

fun makeLinkClickable(strBuilder: SpannableStringBuilder, span: URLSpan, normalTextColor: Int, context: Context, clickListener: LinkClickListener?) {
	val start = strBuilder.getSpanStart(span)
	val end = strBuilder.getSpanEnd(span)
	val flags = strBuilder.getSpanFlags(span)
	val pressedTextColor = ResourcesCompat.getColor(context.resources, R.color.avatar_tint, null)
	val clickable: ClickableSpan = ElloClickableSpan(span.url, normalTextColor, pressedTextColor, clickListener)
	strBuilder.setSpan(clickable, start, end, flags)
	strBuilder.removeSpan(span)
}
