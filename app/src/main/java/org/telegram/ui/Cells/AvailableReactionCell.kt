/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.tlrpc.TL_availableReaction
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Reactions.ReactionsUtils
import org.telegram.ui.Components.Switch

class AvailableReactionCell(context: Context, checkbox: Boolean, private val canLock: Boolean) : FrameLayout(context) {
	private val textView: SimpleTextView
	private val imageView: BackupImageView
	private var switchView: Switch? = null
	private var checkBox: CheckBox2? = null
	private val overlaySelectorView: View

	@JvmField
	var react: TL_availableReaction? = null

	@JvmField
	var locked = false

	init {
		textView = SimpleTextView(context)
		textView.textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
		textView.setTextSize(16)
		textView.setTypeface(Theme.TYPEFACE_BOLD)
		textView.setMaxLines(1)
		textView.setMaxLines(1)
		textView.setGravity(LayoutHelper.absoluteGravityStart or Gravity.CENTER_VERTICAL)

		addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 81f, 0f, 61f, 0f))

		imageView = BackupImageView(context)
		imageView.setAspectFit(true)
		imageView.setLayerNum(1)

		addView(imageView, LayoutHelper.createFrameRelatively(32f, 32f, Gravity.START or Gravity.CENTER_VERTICAL, 23f, 0f, 0f, 0f))

		if (checkbox) {
			checkBox = CheckBox2(context, 26)
			checkBox?.setDrawUnchecked(false)
			checkBox?.setColor(0, 0, context.getColor(R.color.brand))
			checkBox?.setDrawBackgroundAsArc(-1)

			addView(checkBox, LayoutHelper.createFrameRelatively(26f, 26f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 22f, 0f))
		}
		else {
			switchView = Switch(context)
			// switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
			addView(switchView, LayoutHelper.createFrameRelatively(37f, 20f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 22f, 0f))
		}

		overlaySelectorView = View(context)
		overlaySelectorView.background = Theme.getSelectorDrawable(false)

		addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		setWillNotDraw(false)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((AndroidUtilities.dp(58f) + Theme.dividerPaint.strokeWidth).toInt(), MeasureSpec.EXACTLY))
	}

	fun bind(react: TL_availableReaction?, checked: Boolean, currentAccount: Int) {
		var animated = false

		if (react != null && this.react != null && react.reaction == this.react?.reaction) {
			animated = true
		}

		this.react = react

		textView.setText(react?.title)

		val svgThumb = DocumentObject.getSvgThumb(react?.static_icon, context.getColor(R.color.light_background), 1.0f)

		imageView.setImage(ImageLocation.getForDocument(react?.activate_animation), ReactionsUtils.ACTIVATE_ANIMATION_FILTER, "tgs", svgThumb, react)

		locked = canLock && react?.premium == true && !UserConfig.getInstance(currentAccount).isPremium

		if (locked) {
			val drawable = ContextCompat.getDrawable(context, R.drawable.other_lockedfolders2)
			drawable?.colorFilter = PorterDuffColorFilter(context.getColor(R.color.dark), PorterDuff.Mode.MULTIPLY)
			textView.rightDrawable = drawable
		}
		else {
			textView.rightDrawable = null
		}

		setChecked(checked, animated)
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		switchView?.setChecked(checked, animated)
		checkBox?.setChecked(checked, animated)
	}

	var isChecked: Boolean
		get() {
			return switchView?.isChecked ?: checkBox?.isChecked ?: false
		}
		set(checked) {
			setChecked(checked, false)
		}

	override fun onDraw(canvas: Canvas) {
		canvas.drawColor(context.getColor(R.color.background))

		val w = Theme.dividerPaint.strokeWidth
		var l = 0
		var r = 0
		val pad = AndroidUtilities.dp(81f)

		if (LocaleController.isRTL) {
			r = pad
		}
		else {
			l = pad
		}

		canvas.drawLine((getPaddingLeft() + l).toFloat(), height - w, (width - getPaddingRight() - r).toFloat(), height - w, Theme.dividerPaint)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.isEnabled = true
		info.isClickable = true

		if (switchView != null) {
			info.isCheckable = true
			info.isChecked = isChecked
			info.setClassName("android.widget.Switch")
		}
		else if (isChecked) {
			info.isSelected = true
		}

		info.setContentDescription(textView.getText())
	}
}
