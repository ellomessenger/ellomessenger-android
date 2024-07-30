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
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

open class HeaderCell @JvmOverloads constructor(context: Context, padding: Int = 16, topMargin: Int = 0, bottomMargin: Int = 0, text2: Boolean = false) : FrameLayout(context) {
	val textView: TextView = TextView(context)
	var textView2: SimpleTextView? = null

	init {
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		textView.typeface = Theme.TYPEFACE_BOLD
		textView.ellipsize = TextUtils.TruncateAt.END
		textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT or Gravity.BOTTOM)
		textView.minHeight = AndroidUtilities.dp((height - topMargin).toFloat())
		textView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.brand, null))

		addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, padding.toFloat(), topMargin.toFloat(), padding.toFloat(), if (text2) 0f else bottomMargin.toFloat()))

		if (text2) {
			textView2 = SimpleTextView(getContext()).also {
				it.setTextSize(13)
				it.setGravity((if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP)

				addView(it, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP, padding.toFloat(), 16f, padding.toFloat(), bottomMargin.toFloat()))
			}
		}

		ViewCompat.setAccessibilityHeading(this, true)

		height = 46
	}

	fun setHeight(value: Int) {
		textView.minHeight = AndroidUtilities.dp(value.toFloat()) - (textView.layoutParams as LayoutParams).topMargin
	}

	fun setEnabled(value: Boolean, animators: ArrayList<Animator>?) {
		if (animators != null) {
			animators.add(ObjectAnimator.ofFloat(textView, ALPHA, if (value) 1.0f else 0.5f))
		}
		else {
			textView.alpha = if (value) 1.0f else 0.5f
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
	}

	fun setTextSize(dip: Float) {
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, dip)
	}

	fun setTextColor(color: Int) {
		textView.setTextColor(color)
	}

	fun setText(text: CharSequence?) {
		textView.text = text
	}

	fun setText2(text: CharSequence?) {
		textView2?.setText(text)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			info.isHeading = true
		}
		else {
			val collection = info.collectionItemInfo

			if (collection != null) {
				info.collectionItemInfo = CollectionItemInfo.obtain(collection.rowIndex, collection.rowSpan, collection.columnIndex, collection.columnSpan, true)
			}
		}

		info.isEnabled = true
	}
}
