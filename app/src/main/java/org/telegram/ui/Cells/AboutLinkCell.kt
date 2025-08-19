/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.LinkClickListener
import org.telegram.messenger.utils.LinkTouchMovementMethod
import org.telegram.messenger.utils.collapsedBioLength
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.processForLinks
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import java.text.BreakIterator

class AboutLinkCell(context: Context) : LinearLayout(context) {
	private val textSwitcher: TextSwitcher
	private val valueTextView: TextView
	private val moreTextView: TextView
	private val bottomShadow: FrameLayout
	private var fullBio: SpannableStringBuilder? = null
	private var bioExpanded = false
	private var oldText: String? = null
	var listener: LinkClickListener? = null

	init {
		orientation = VERTICAL
		gravity = Gravity.START

		valueTextView = TextView(context)
		valueTextView.gone()
		valueTextView.setTextColor(context.getColor(R.color.dark_gray))
		valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		valueTextView.setLines(1)
		valueTextView.maxLines = 1
		valueTextView.isSingleLine = true
		valueTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		valueTextView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		valueTextView.isFocusable = false

		addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 16f, 12f, 16f, 4f))

		textSwitcher = TextSwitcher(context)

		textSwitcher.setFactory {
			val textView = LayoutInflater.from(context).inflate(R.layout.bio_label, this, false) as TextView
			textView.movementMethod = LinkTouchMovementMethod()
			textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
			textView.setTextColor(context.getColor(R.color.text))
			textView.setLinkTextColor(context.getColor(R.color.brand))
			textView.typeface = Theme.TYPEFACE_DEFAULT
			textView.layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
			textView
		}

		textSwitcher.inAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
		textSwitcher.outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)

		addView(textSwitcher, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16f, 4f, 16f, 0f))

		moreTextView = TextView(context)
		moreTextView.setTextColor(context.getColor(R.color.brand))
		moreTextView.typeface = Theme.TYPEFACE_DEFAULT
		moreTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
		moreTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		moreTextView.setLines(1)
		moreTextView.setText(R.string.more)

		moreTextView.setOnClickListener {
			bioExpanded = true
			textSwitcher.setText(fullBio)
			moreTextView.gone()
		}

		addView(moreTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 16f, 4f, 16f, 0f))

		bottomShadow = FrameLayout(context)

		bottomShadow.background = ResourcesCompat.getDrawable(context.resources, R.drawable.gradient_bottom, null)?.mutate()?.also {
			it.colorFilter = PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.SRC_ATOP)
		}

		addView(bottomShadow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 12, 0f, 6f, 0f, 0f))
	}

	fun setText(text: String?, parseLinks: Boolean) {
		setTextAndValue(text, null, parseLinks)
	}

	fun setTextAndValue(text: String?, value: String?, parseLinks: Boolean) {
		valueTextView.text = value

		if (text.isNullOrEmpty() || TextUtils.equals(text, oldText)) {
			return
		}

		oldText = try {
			AndroidUtilities.getSafeString(text)
		}
		catch (e: Throwable) {
			text
		}

		val bio = text.trim()

		if (bio.isEmpty()) {
			return
		}

		// source: https://stackoverflow.com/a/21430792/318460
		var useFullDescription = true

		if (!bioExpanded) {
			if (bio.length > collapsedBioLength) {
				val shortDescription = bio.substring(0, findWordBoundary(bio, collapsedBioLength)) + "…"
				textSwitcher.setText(shortDescription.processForLinks(context, parseLinks, listener))
				useFullDescription = false
			}
		}

		fullBio = bio.processForLinks(context, parseLinks, listener)

		if (useFullDescription) {
			textSwitcher.setText(fullBio)
		}

		if (value.isNullOrEmpty()) {
			valueTextView.gone()
		}
		else {
			valueTextView.text = value
			valueTextView.visible()
		}

		requestLayout()
	}

	private fun findWordBoundary(text: String, length: Int): Int {
		val breakIterator = BreakIterator.getWordInstance()
		breakIterator.setText(text)
		var end = breakIterator.following(length)
		if (end == BreakIterator.DONE) {
			end = length
		}
		else if (end < length) {
			end = breakIterator.next()
		}
		return end
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		oldText?.let {
			val text: CharSequence = it
			val valueText = valueTextView.text

			info.className = "android.widget.TextView"

			if (valueText.isNullOrEmpty()) {
				info.text = text
			}
			else {
				info.text = "$valueText: $text"
			}
		}
	}
}
