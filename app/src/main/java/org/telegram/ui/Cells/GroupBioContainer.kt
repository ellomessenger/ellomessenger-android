/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Mykhaylo Mykytyn, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.utils.dp
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame

class GroupBioContainer(context: Context) : FrameLayout(context) {
	private val limit = MessagesController.getInstance(UserConfig.selectedAccount).aboutLimit
	private val editText = TextInputEditText(context)
	private val hintColor = context.getColor(R.color.gray_border)
	private val counterColor = context.getColor(R.color.dark)

	var text: String?
		get() = editText.text?.toString()?.trim()
		set(value) = editText.setText(value?.trim())

	init {
		setBackgroundResource(R.color.background)

		editText.minimumHeight = AndroidUtilities.dp(33f)
		editText.gravity = Gravity.TOP or Gravity.START
		editText.hint = context.getString(R.string.group_description)
		editText.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
		editText.maxLines = 4
		editText.filters = arrayOf(InputFilter.LengthFilter(limit))
		editText.setHorizontallyScrolling(false)
		editText.isSingleLine = false
		editText.typeface = Theme.TYPEFACE_DEFAULT
		editText.setHintTextColor(hintColor)
		editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
		editText.backgroundTintList = ColorStateList.valueOf(hintColor)

		val inputLayout = TextInputLayout(context).apply {
			isHintEnabled = false
			isCounterEnabled = true
			counterMaxLength = limit
			counterTextColor = ColorStateList.valueOf(counterColor)
			endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT

			setEndIconTintList(ColorStateList.valueOf(hintColor))

			findViewById<View>(com.google.android.material.R.id.text_input_end_icon)?.let {
				it.updateLayoutParams<LayoutParams> {
					gravity = Gravity.TOP
					bottomMargin = 2.dp
				}

				it.translationX = 14.dp.toFloat()
			}

			addView(this@GroupBioContainer.editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}

//		TODO: render the counter inside the editText
//		make use of org.telegram.ui.Components.CounterView for that:
//
//		val counterView = CounterView(context)
//		addView(counterView, createFrame(LayoutHelper.MATCH_PARENT, 28f, Gravity.BOTTOM, 0f, 4f, 0f, 0f))
//		counterView.setGravity(Gravity.RIGHT)
//		counterView.setCount(limit,animated = true)

		addView(inputLayout, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.START, 13f, 0f, 13f, 16f))
	}

	override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
		return editText.requestFocus(direction, previouslyFocusedRect)
	}
}
