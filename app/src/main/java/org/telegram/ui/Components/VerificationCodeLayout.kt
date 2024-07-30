/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Shamil Afandiyev, 2024
 */
package org.telegram.ui.Components

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import org.telegram.messenger.R
import org.telegram.messenger.databinding.VerificationCodeLayoutBinding

class VerificationCodeLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : LinearLayout(context, attrs, defStyle) {
	private val verificationText by lazy { VerificationText(this) }
	private val editFields = mutableListOf<TextInputEditText>()
	private var isInitialized = false
	val text: CharSequence by lazy { editable }
	var afterTextChanged: (Editable?) -> Unit = {}

	private val editable: Editable
		get() = verificationText.getText()

	fun addTextChangedListener(afterTextChanged: (Editable?) -> Unit = {}) {
		this.afterTextChanged = afterTextChanged
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (editFields.isEmpty()) {
			editFields.addAll(children.map { it.findViewById(R.id.code_digit) as TextInputEditText })

			editFields.forEachIndexed { i, editText ->
				editText.transformationMethod = object : PasswordTransformationMethod() {
					override fun getTransformation(source: CharSequence?, view: View?): CharSequence {
						return source ?: ""
					}
				}

				editText.setOnKeyListener { _, keyCode, _ ->
					if (keyCode == KeyEvent.KEYCODE_DEL) {
						val wasEmpty = editText.text.isNullOrEmpty()

						for (j in i until editFields.size) {
							editFields[j].let {
								it.text?.clear()
								it.isActivated = false
								it.clearFocus()
							}
						}

						if (wasEmpty && i > 0) {
							editFields[i - 1].requestFocus()
						}
						else {
							editFields[i].requestFocus()
						}

						return@setOnKeyListener true
					}

					false
				}

				editText.addTextChangedListener {
					editText.isActivated = (it?.length != 0)
					this@VerificationCodeLayout.afterTextChanged(editable)
				}

				editText.doAfterTextChanged {
					if (i < editFields.size - 1) {
						editFields[i + 1].requestFocus()
					}
				}

				editText.setOnCreateContextMenuListener { contextMenu, view, contextMenuInfo ->
					val pasteItem = contextMenu.add(Menu.NONE, view.id, Menu.NONE, context.getString(android.R.string.paste))
					pasteItem.setOnMenuItemClickListener {
						pasteFromClipboard()
						true
					}
				}

				if (i == 5) {
					editText.imeOptions = EditorInfo.IME_ACTION_DONE
				}
			}

			isInitialized = true
		}
	}

	private fun pasteFromClipboard() {
		val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		val clipData = clipboardManager.primaryClip
		if (clipData != null && clipData.itemCount > 0) {
			val text = clipData.getItemAt(0).text
			pasteDigits(text.toString())
		}
	}

	private fun pasteDigits(text: CharSequence) {
		val cleanedText = text.filter { it.isDigit() }
		val editTextIterator = editFields.iterator()
		cleanedText.forEach { char ->
			if (editTextIterator.hasNext()) {
				val editText = editTextIterator.next()
				editText.setText(char.toString())
			}
		}
	}

	fun setResult(ok: Boolean) {
		editFields.forEach {
			if (!ok) {
				it.setBackgroundResource(R.drawable.code_input_error)
			}
			else {
				it.setBackgroundResource(R.drawable.code_input_success)
			}
		}

	}
}

class VerificationText(private val codeLayout: VerificationCodeLayout) {
	fun getText(): Editable {
		val binding = VerificationCodeLayoutBinding.bind(codeLayout)
		return Editable.Factory.getInstance().newEditable(binding.input1.codeDigit.text).append(Editable.Factory.getInstance().newEditable(binding.input2.codeDigit.text)).append(Editable.Factory.getInstance().newEditable(binding.input3.codeDigit.text)).append(Editable.Factory.getInstance().newEditable(binding.input4.codeDigit.text)).append(Editable.Factory.getInstance().newEditable(binding.input5.codeDigit.text)).append(Editable.Factory.getInstance().newEditable(binding.input6.codeDigit.text)) ?: Editable.Factory.getInstance().newEditable("")
	}
}
