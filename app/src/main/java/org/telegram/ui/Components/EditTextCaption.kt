/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.text.*
import android.text.style.CharacterStyle
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.MarginLayoutParams
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.widget.doAfterTextChanged
import org.telegram.messenger.*
import org.telegram.messenger.utils.CopyUtilities
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.TextStyleSpan.TextStyleRun
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

open class EditTextCaption(context: Context) : EditTextBoldCursor(context) {
	private var captionLayout: StaticLayout? = null
	private var userNameLength = 0
	private var xOffset = 0
	private var yOffset = 0
	private var hintColor = 0
	private var selectionStart = -1
	private var selectionEnd = -1
	private var lineCount = 0
	private var isInitLineCount = false
	var delegate: EditTextCaptionDelegate? = null
	var allowTextEntitiesIntersection = false

	var offsetY = 0f
		set(value) {
			field = value
			invalidate()
		}

	var caption: String? = null
		set(value) {
			if (field.isNullOrEmpty() && value.isNullOrEmpty() || (field == value)) {
				return
			}

			field = value?.replace('\n', ' ')

			requestLayout()
		}

	init {
		doAfterTextChanged {
			if (lineCount != getLineCount()) {
				if (!isInitLineCount && measuredWidth > 0) {
					onLineCountChanged(lineCount, getLineCount())
				}

				lineCount = getLineCount()
			}
		}

		setClipToPadding(true)
	}

	protected open fun onLineCountChanged(oldLineCount: Int, newLineCount: Int) {
		// stub
	}

	fun makeSelectedBold() {
		val run = TextStyleRun()
		run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_BOLD
		applyTextStyleToSelection(TextStyleSpan(run))
	}

	fun makeSelectedSpoiler() {
		val run = TextStyleRun()
		run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_SPOILER
		applyTextStyleToSelection(TextStyleSpan(run))
	}

	fun makeSelectedItalic() {
		val run = TextStyleRun()
		run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_ITALIC
		applyTextStyleToSelection(TextStyleSpan(run))
	}

	fun makeSelectedMono() {
		val run = TextStyleRun()
		run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_MONO
		applyTextStyleToSelection(TextStyleSpan(run))
	}

	fun makeSelectedStrike() {
		val run = TextStyleRun()
		run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_STRIKE
		applyTextStyleToSelection(TextStyleSpan(run))
	}

	fun makeSelectedUnderline() {
		val run = TextStyleRun()
		run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_UNDERLINE
		applyTextStyleToSelection(TextStyleSpan(run))
	}

	fun makeSelectedUrl() {
		val builder = AlertDialog.Builder(context)
		builder.setTitle(context.getString(R.string.CreateLink))

		val editText = object : EditTextBoldCursor(context) {
			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64f), MeasureSpec.EXACTLY))
			}
		}

		editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		editText.setText(context.getString(R.string.http_scheme))
		editText.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
		editText.setHintText(context.getString(R.string.URL))
		editText.setHeaderHintColor(ResourcesCompat.getColor(resources, R.color.brand, null))
		editText.isSingleLine = true
		editText.isFocusable = true
		editText.setTransformHintToHeader(true)
		editText.setLineColors(ResourcesCompat.getColor(context.resources, R.color.hint, null), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
		editText.imeOptions = EditorInfo.IME_ACTION_DONE
		editText.background = null
		editText.requestFocus()
		editText.setPadding(0, 0, 0, 0)

		builder.setView(editText)

		val start: Int
		val end: Int

		if (selectionStart >= 0 && selectionEnd >= 0) {
			start = selectionStart
			end = selectionEnd
			selectionEnd = -1
			selectionStart = selectionEnd
		}
		else {
			start = getSelectionStart()
			end = getSelectionEnd()
		}

		builder.setPositiveButton(context.getString(R.string.OK)) { _, _ ->
			val editable = text
			val spans = editable.getSpans(start, end, CharacterStyle::class.java)

			if (!spans.isNullOrEmpty()) {
				for (oldSpan in spans) {
					if (oldSpan !is AnimatedEmojiSpan) {
						val spanStart = editable.getSpanStart(oldSpan)
						val spanEnd = editable.getSpanEnd(oldSpan)

						editable.removeSpan(oldSpan)

						if (spanStart < start) {
							editable.setSpan(oldSpan, spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						}

						if (spanEnd > end) {
							editable.setSpan(oldSpan, end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						}
					}
				}
			}

			try {
				editable.setSpan(URLSpanReplacement(editText.text.toString()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
			catch (e: Exception) {
				// ignored
			}

			delegate?.onSpansChanged()
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)

		builder.show().setOnShowListener {
			editText.requestFocus()
			AndroidUtilities.showKeyboard(editText)
		}

		val layoutParams = editText.layoutParams as? MarginLayoutParams

		if (layoutParams != null) {
			if (layoutParams is FrameLayout.LayoutParams) {
				layoutParams.gravity = Gravity.CENTER_HORIZONTAL
			}

			layoutParams.leftMargin = AndroidUtilities.dp(24f)
			layoutParams.rightMargin = layoutParams.leftMargin
			layoutParams.height = AndroidUtilities.dp(36f)

			editText.layoutParams = layoutParams
		}

		editText.setSelection(0, editText.text.length)
	}

	fun makeSelectedRegular() {
		applyTextStyleToSelection(null)
	}

	fun setSelectionOverride(start: Int, end: Int) {
		selectionStart = start
		selectionEnd = end
	}

	private fun applyTextStyleToSelection(span: TextStyleSpan?) {
		val start: Int
		val end: Int

		if (selectionStart >= 0 && selectionEnd >= 0) {
			start = selectionStart
			end = selectionEnd
			selectionEnd = -1
			selectionStart = selectionEnd
		}
		else {
			start = getSelectionStart()
			end = getSelectionEnd()
		}

		MediaDataController.addStyleToText(span, start, end, text, allowTextEntitiesIntersection)

		delegate?.onSpansChanged()
	}

	override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
		try {
			super.onWindowFocusChanged(hasWindowFocus)
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}
	}

	protected open fun onContextMenuOpen() {
		// stub
	}

	protected open fun onContextMenuClose() {
		// stub
	}

	private fun overrideCallback(callback: ActionMode.Callback): ActionMode.Callback {
		val wrap = object : ActionMode.Callback {
			override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
				onContextMenuOpen()
				return callback.onCreateActionMode(mode, menu)
			}

			override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
				return callback.onPrepareActionMode(mode, menu)
			}

			override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
				if (performMenuAction(item.itemId)) {
					mode.finish()
					return true
				}

				try {
					return callback.onActionItemClicked(mode, item)
				}
				catch (e: Exception) {
					// ignored
				}

				return true
			}

			override fun onDestroyActionMode(mode: ActionMode) {
				onContextMenuClose()
				callback.onDestroyActionMode(mode)
			}
		}

		return object : ActionMode.Callback2() {
			override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
				return wrap.onCreateActionMode(mode, menu)
			}

			override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
				return wrap.onPrepareActionMode(mode, menu)
			}

			override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
				return wrap.onActionItemClicked(mode, item)
			}

			override fun onDestroyActionMode(mode: ActionMode) {
				wrap.onDestroyActionMode(mode)
			}

			override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
				if (callback is ActionMode.Callback2) {
					callback.onGetContentRect(mode, view, outRect)
				}
				else {
					super.onGetContentRect(mode, view, outRect)
				}
			}
		}
	}

	private fun performMenuAction(itemId: Int): Boolean {
		return when (itemId) {
			R.id.menu_regular -> {
				makeSelectedRegular()
				true
			}
			R.id.menu_bold -> {
				makeSelectedBold()
				true
			}
			R.id.menu_italic -> {
				makeSelectedItalic()
				true
			}
			R.id.menu_mono -> {
				makeSelectedMono()
				true
			}
			R.id.menu_link -> {
				makeSelectedUrl()
				true
			}
			R.id.menu_strike -> {
				makeSelectedStrike()
				true
			}
			R.id.menu_underline -> {
				makeSelectedUnderline()
				true
			}
			R.id.menu_spoiler -> {
				makeSelectedSpoiler()
				true
			}
			else -> {
				false
			}
		}
	}

	override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode {
		return super.startActionMode(overrideCallback(callback), type)
	}

	override fun startActionMode(callback: ActionMode.Callback): ActionMode {
		return super.startActionMode(overrideCallback(callback))
	}

	@SuppressLint("DrawAllocation")
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		try {
			isInitLineCount = (measuredWidth == 0) && (measuredHeight == 0)

			super.onMeasure(widthMeasureSpec, heightMeasureSpec)

			if (isInitLineCount) {
				lineCount = getLineCount()
			}

			isInitLineCount = false
		}
		catch (e: Exception) {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(51f))
			FileLog.e(e)
		}

		captionLayout = null

		if (!caption.isNullOrEmpty()) {
			val text: CharSequence = text

			if (text.length > 1 && text[0] == '@') {
				val index = text.indexOf(' ')

				if (index != -1) {
					val paint = paint
					val str = text.subSequence(0, index + 1)
					val size = ceil(paint.measureText(text, 0, index + 1).toDouble()).toInt()
					val width = measuredWidth - paddingLeft - paddingRight

					userNameLength = str.length

					val captionFinal = TextUtils.ellipsize(caption, paint, (width - size).toFloat(), TextUtils.TruncateAt.END)

					xOffset = size

					try {
						captionLayout = StaticLayout(captionFinal, getPaint(), width - size, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false).also {
							if (it.lineCount > 0) {
								xOffset -= it.getLineLeft(0).toInt()
							}

							yOffset = (measuredHeight - it.getLineBottom(0)) / 2 + AndroidUtilities.dp(0.5f)
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
		}
	}

	override fun setHintColor(value: Int) {
		super.setHintColor(value)
		hintColor = value
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		canvas.save()
		canvas.translate(0f, offsetY)

		super.onDraw(canvas)

		try {
			if (captionLayout != null && userNameLength == length()) {
				val paint = paint
				val oldColor = getPaint().color
				paint.color = hintColor
				canvas.save()
				canvas.translate(xOffset.toFloat(), yOffset.toFloat())
				captionLayout?.draw(canvas)
				canvas.restore()
				paint.color = oldColor
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		canvas.restore()
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		val infoCompat = AccessibilityNodeInfoCompat.wrap(info)

		if (!caption.isNullOrEmpty()) {
			infoCompat.hintText = caption
		}

		for (action in infoCompat.actionList) {
			if (action.id == ACCESSIBILITY_ACTION_SHARE) {
				infoCompat.removeAction(action)
				break
			}
		}

		if (hasSelection()) {
			infoCompat.addAction(AccessibilityActionCompat(R.id.menu_spoiler, context.getString(R.string.Spoiler)))
			infoCompat.addAction(AccessibilityActionCompat(R.id.menu_bold, context.getString(R.string.Bold)))
			infoCompat.addAction(AccessibilityActionCompat(R.id.menu_italic, context.getString(R.string.Italic)))
			infoCompat.addAction(AccessibilityActionCompat(R.id.menu_mono, context.getString(R.string.Mono)))
			infoCompat.addAction(AccessibilityActionCompat(R.id.menu_strike, context.getString(R.string.Strike)))
			infoCompat.addAction(AccessibilityActionCompat(R.id.menu_underline, context.getString(R.string.Underline)))
			infoCompat.addAction(AccessibilityActionCompat(R.id.menu_link, context.getString(R.string.CreateLink)))
			infoCompat.addAction(AccessibilityActionCompat(R.id.menu_regular, context.getString(R.string.Regular)))
		}
	}

	override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
		return performMenuAction(action) || super.performAccessibilityAction(action, arguments)
	}

	override fun onTextContextMenuItem(id: Int): Boolean {
		when (id) {
			android.R.id.paste -> {
				val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
				val clipData = clipboard?.primaryClip

				if (clipData != null && clipData.itemCount == 1 && clipData.description.hasMimeType("text/html")) {
					try {
						val html = clipData.getItemAt(0).htmlText
						val pasted = CopyUtilities.fromHTML(html)

						Emoji.replaceEmoji(pasted, paint.fontMetricsInt, false, null)

						val spans = pasted.getSpans(0, pasted.length, AnimatedEmojiSpan::class.java)

						if (spans != null) {
							for (span in spans) {
								span.applyFontMetrics(paint.fontMetricsInt, AnimatedEmojiDrawable.getCacheTypeForEnterView())
							}
						}

						val start = max(0, getSelectionStart())
						val end = min(text.length, getSelectionEnd())

						text = text.replace(start, end, pasted)

						setSelection(start + pasted.length, start + pasted.length)

						return true
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
			android.R.id.copy -> {
				val start = max(0, getSelectionStart())
				val end = min(text.length, getSelectionEnd())

				try {
					AndroidUtilities.addToClipboard(text.subSequence(start, end))
					return true
				}
				catch (e: Exception) {
					// ignored
				}
			}
			android.R.id.cut -> {
				val start = max(0, getSelectionStart())
				val end = min(text.length, getSelectionEnd())

				try {
					AndroidUtilities.addToClipboard(text.subSequence(start, end))

					val stringBuilder = SpannableStringBuilder()

					if (start != 0) {
						stringBuilder.append(text.subSequence(0, start))
					}
					if (end != text.length) {
						stringBuilder.append(text.subSequence(end, text.length))
					}

					text = stringBuilder

					return true
				}
				catch (e: Exception) {
					// unused
				}
			}
		}

		return super.onTextContextMenuItem(id)
	}

	fun interface EditTextCaptionDelegate {
		fun onSpansChanged()
	}

	companion object {
		private const val ACCESSIBILITY_ACTION_SHARE = 0x10000000
	}
}
