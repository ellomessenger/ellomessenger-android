/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.ActionBar

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class ActionBarMenuSubItem(context: Context, needCheck: Boolean, top: Boolean, bottom: Boolean) : FrameLayout(context) {
	private val rightIconColor: Int
	private var iconColor = 0
	private var itemHeight = 44
	private var selectorColor = 0
	private var subtextView: TextView? = null
	private var textColor = 0
	var bottom = false
	var checkView: CheckBox2? = null
	var openSwipeBackLayout: Runnable? = null
	var top = false

	@JvmField
	val textView: TextView

	@JvmField
	val imageView: ImageView

	var rightIcon: ImageView? = null
		private set

	constructor(context: Context, top: Boolean, bottom: Boolean) : this(context, false, top, bottom)

	init {
		this.top = top
		this.bottom = bottom

		textColor = context.getColor(R.color.text)
		iconColor = context.getColor(R.color.brand)
		rightIconColor = ResourcesCompat.getColor(resources, R.color.text, null)
		selectorColor = context.getColor(R.color.light_background)

		updateBackground()

		setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)

		imageView = ImageView(context)
		imageView.scaleType = ImageView.ScaleType.CENTER
		imageView.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

		addView(imageView, createFrame(32, 40, Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT))

		textView = TextView(context)
		textView.setLines(1)
		textView.isSingleLine = true
		textView.gravity = Gravity.LEFT
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.setTextColor(textColor)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		textView.typeface = Theme.TYPEFACE_DEFAULT

		addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL))

		if (needCheck) {
			checkView = CheckBox2(context, 26)
			checkView?.setDrawUnchecked(false)
			checkView?.setColor(0, 0, context.getColor(R.color.brand))
			checkView?.setDrawBackgroundAsArc(-1)

			addView(checkView, createFrame(26, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT))
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(itemHeight.toFloat()), MeasureSpec.EXACTLY))
	}

	fun setItemHeight(itemHeight: Int) {
		this.itemHeight = itemHeight
	}

	fun setChecked(checked: Boolean) {
		checkView?.setChecked(checked, true)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.isEnabled = isEnabled

		if (checkView?.isChecked == true) {
			info.isCheckable = true
			info.isChecked = checkView?.isChecked ?: false
			info.className = "android.widget.CheckBox"
		}
	}

	fun setCheckColor(@ColorInt color: Int) {
		checkView?.setColor(0, 0, color)
	}

	fun setTextAndIcon(text: CharSequence?, icon: Int) {
		setTextAndIcon(text, icon, null)
	}

	fun setMultiline() {
		textView.setLines(2)
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		textView.isSingleLine = false
		textView.gravity = Gravity.CENTER_VERTICAL
	}

	fun setTextAndIcon(text: CharSequence?, icon: Int, iconDrawable: Drawable?) {
		textView.text = text

		if (icon != 0 || iconDrawable != null || checkView != null) {
			if (iconDrawable != null) {
				imageView.setImageDrawable(iconDrawable)
			}
			else {
				imageView.setImageResource(icon)
			}

			imageView.visibility = VISIBLE
			textView.setPadding(if (LocaleController.isRTL) 0 else AndroidUtilities.dp(48f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(48f) else 0, 0)
		}
		else {
			imageView.visibility = INVISIBLE
			textView.setPadding(0, 0, 0, 0)
		}
	}

	fun setColors(textColor: Int, iconColor: Int): ActionBarMenuSubItem {
		setTextColor(textColor)
		setIconColor(iconColor)
		return this
	}

	fun setTextColor(textColor: Int) {
		if (this.textColor != textColor) {
			textView.setTextColor(textColor.also { this.textColor = it })
		}
	}

	fun setIconColor(iconColor: Int) {
		if (this.iconColor != iconColor) {
			imageView.colorFilter = PorterDuffColorFilter(iconColor.also { this.iconColor = it }, PorterDuff.Mode.SRC_IN)
		}
	}

	fun setIcon(resId: Int) {
		imageView.setImageResource(resId)
	}

	fun setText(text: String?) {
		textView.text = text
	}

	fun setSubtextColor(color: Int) {
		subtextView?.setTextColor(color)
	}

	fun setSubtext(text: String?) {
		if (subtextView == null) {
			subtextView = TextView(context)
			subtextView?.setLines(1)
			subtextView?.isSingleLine = true
			subtextView?.gravity = Gravity.LEFT
			subtextView?.ellipsize = TextUtils.TruncateAt.END
			subtextView?.setTextColor(context.getColor(R.color.dark_gray))
			subtextView?.visibility = GONE
			subtextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
			subtextView?.setPadding(if (LocaleController.isRTL) 0 else AndroidUtilities.dp(48f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(48f) else 0, 0)

			addView(subtextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL, 0f, 10f, 0f, 0f))
		}

		val visible = !text.isNullOrEmpty()
		val oldVisible = subtextView?.visibility == VISIBLE

		if (visible != oldVisible) {
			subtextView?.visibility = if (visible) VISIBLE else GONE

			val layoutParams = textView.layoutParams as LayoutParams
			layoutParams.bottomMargin = if (visible) AndroidUtilities.dp(10f) else 0
			textView.layoutParams = layoutParams
		}

		subtextView?.text = text
	}

	fun setSelectorColor(selectorColor: Int) {
		if (this.selectorColor != selectorColor) {
			this.selectorColor = selectorColor
			updateBackground()
		}
	}

	fun updateSelectorBackground(top: Boolean, bottom: Boolean) {
		if (this.top == top && this.bottom == bottom) {
			return
		}

		this.top = top
		this.bottom = bottom

		updateBackground()
	}

	private fun updateBackground() {
		val topBackgroundRadius = if (top) 6 else 0
		val bottomBackgroundRadius = if (bottom) 6 else 0
		background = Theme.createRadSelectorDrawable(selectorColor, topBackgroundRadius, bottomBackgroundRadius)
	}

	fun openSwipeBack() {
		openSwipeBackLayout?.run()
	}

	fun setRightIcon(icon: Int) {
		if (rightIcon == null) {
			rightIcon = ImageView(context)
			rightIcon?.scaleType = ImageView.ScaleType.CENTER
			rightIcon?.setColorFilter(rightIconColor, PorterDuff.Mode.SRC_IN)

			if (LocaleController.isRTL) {
				rightIcon?.scaleX = -1f

			}
			addView(rightIcon, createFrame(24, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT))
		}

		setPadding(AndroidUtilities.dp((if (LocaleController.isRTL) 8 else 18).toFloat()), 0, AndroidUtilities.dp((if (LocaleController.isRTL) 18 else 8).toFloat()), 0)

		rightIcon?.setImageResource(icon)
	}
}
