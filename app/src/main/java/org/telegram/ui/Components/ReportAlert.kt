/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createScroll

open class ReportAlert(context: Context, type: Int) : BottomSheet(context, true) {
	private val editText = EditTextBoldCursor(context)
	private val clearButton = BottomSheetCell(context)

	init {
		setApplyBottomPadding(false)
		setApplyTopPadding(false)

		val scrollView = ScrollView(context)
		scrollView.isFillViewport = true

		setCustomView(scrollView)

		val frameLayout = FrameLayout(context)

		scrollView.addView(frameLayout, createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP))

		val imageView = RLottieImageView(context)
		// imageView.setAnimation(R.raw.report_police, 120, 120);
		imageView.setAnimation(R.raw.cop, 120, 120)
		imageView.playAnimation()
		frameLayout.addView(imageView, createFrame(160, 160f, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 17f, 14f, 17f, 0f))

		val percentTextView = TextView(context)
		percentTextView.typeface = Theme.TYPEFACE_BOLD
		percentTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
		percentTextView.setTextColor(ResourcesCompat.getColor(getContext().resources, R.color.text, null))

		percentTextView.text = when (type) {
			AlertsCreator.REPORT_TYPE_SPAM -> context.getString(R.string.ReportTitleSpam)
			AlertsCreator.REPORT_TYPE_FAKE_ACCOUNT -> context.getString(R.string.ReportTitleFake)
			AlertsCreator.REPORT_TYPE_VIOLENCE -> context.getString(R.string.ReportTitleViolence)
			AlertsCreator.REPORT_TYPE_CHILD_ABUSE -> context.getString(R.string.ReportTitleChild)
			AlertsCreator.REPORT_TYPE_PORNOGRAPHY -> context.getString(R.string.ReportTitlePornography)
			AlertsCreator.REPORT_TYPE_OTHER -> context.getString(R.string.ReportChat)
			else -> null
		}

		frameLayout.addView(percentTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 17f, 197f, 17f, 0f))

		val infoTextView = TextView(context)
		infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		infoTextView.setTextColor(ResourcesCompat.getColor(getContext().resources, R.color.dark_gray, null))
		infoTextView.gravity = Gravity.CENTER_HORIZONTAL
		infoTextView.text = context.getString(R.string.ReportInfo)

		frameLayout.addView(infoTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 30f, 235f, 30f, 44f))

		editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		editText.setHintTextColor(ResourcesCompat.getColor(getContext().resources, R.color.hint, null))
		editText.setTextColor(ResourcesCompat.getColor(getContext().resources, R.color.text, null))
		editText.background = null
		editText.setLineColors(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), ResourcesCompat.getColor(context.resources, R.color.text, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
		editText.maxLines = 1
		editText.setLines(1)
		editText.setPadding(0, 0, 0, 0)
		editText.isSingleLine = true
		editText.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		editText.inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
		editText.imeOptions = EditorInfo.IME_ACTION_DONE
		editText.hint = context.getString(R.string.ReportHint)
		editText.setCursorColor(ResourcesCompat.getColor(getContext().resources, R.color.text, null))
		editText.setCursorSize(AndroidUtilities.dp(20f))
		editText.setCursorWidth(1.5f)

		editText.setOnEditorActionListener { _, action, _ ->
			if (action == EditorInfo.IME_ACTION_DONE) {
				clearButton.background.callOnClick()
				return@setOnEditorActionListener true
			}

			false
		}

		frameLayout.addView(editText, createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.LEFT or Gravity.TOP, 17f, 305f, 17f, 0f))

		clearButton.setBackground(null)
		clearButton.setText(context.getString(R.string.ReportSend))

		clearButton.background.setOnClickListener {
			AndroidUtilities.hideKeyboard(editText)
			onSend(type, editText.text.toString())
			dismiss()
		}

		frameLayout.addView(clearButton, createFrame(LayoutHelper.MATCH_PARENT, 50f, Gravity.LEFT or Gravity.TOP, 0f, 357f, 0f, 0f))

		smoothKeyboardAnimationEnabled = true
	}

	protected open fun onSend(type: Int, message: String?) {
		// stub
	}

	class BottomSheetCell(context: Context) : FrameLayout(context) {
		val background = View(context)
		private val textView: TextView

		init {
			background.background = Theme.AdaptiveRipple.filledRect(ResourcesCompat.getColor(resources, R.color.brand, null), 4f)

			addView(background, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, 16f, 16f, 16f, 16f))

			textView = TextView(context)
			textView.setLines(1)
			textView.isSingleLine = true
			textView.gravity = Gravity.CENTER_HORIZONTAL
			textView.ellipsize = TextUtils.TruncateAt.END
			textView.gravity = Gravity.CENTER
			textView.setTextColor(ResourcesCompat.getColor(resources, R.color.white, null))
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			textView.typeface = Theme.TYPEFACE_BOLD

			addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80f), MeasureSpec.EXACTLY))
		}

		fun setText(text: CharSequence?) {
			textView.text = text
		}
	}
}
