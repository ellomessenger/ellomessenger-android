/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.transactions

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.textfield.TextInputEditText
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentFilterTransactionsBinding
import org.telegram.messenger.utils.formatBirthday
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FilterTransactionsFragment : BaseFragment() {
	private var binding: FragmentFilterTransactionsBinding? = null

	override fun createActionBar(context: Context): ActionBar {
		val actionBar = ActionBar(context)
		actionBar.setAddToContainer(true)
		actionBar.backButtonDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_back_arrow, null)
		actionBar.setTitle(context.getString(R.string.filter))

		actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (parentActivity == null) {
					return
				}

				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					doneItem -> {
						// TODO: pass data to previous fragment
						finishFragment()
					}
				}
			}
		})

		val menu = actionBar.createMenu()

		menu.addItem(doneItem, R.drawable.done_checkmark)

		return actionBar
	}

	override fun createView(context: Context): View? {
		binding = FragmentFilterTransactionsBinding.inflate(LayoutInflater.from(context))

		setUpSpinner()

		binding?.dateToField?.setOnClickListener {
			pickDate(binding!!.dateToField)
		}

		binding?.dateFromField?.setOnClickListener {
			pickDate(binding!!.dateFromField)
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private fun setUpSpinner() {
		val context = context ?: return
		val items = arrayOf("Best recipes", "World", "Space", "Ice cream")
		val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)

		binding?.channelSpinner?.setOnItemClickListener { parent, view, position, id ->
			// TODO: process maybe?
		}

		binding?.channelSpinner?.setAdapter(adapter)
	}

	private fun pickDate(targetField: TextInputEditText) {
		val context = context ?: return
		val calendar = Calendar.getInstance()
		val year = calendar[Calendar.YEAR]
		val month = calendar[Calendar.MONTH]
		val day = calendar[Calendar.DAY_OF_MONTH]

		val datePickerDialog = DatePickerDialog(context, { _, y, m, d ->
			val formatter = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
			val date = formatter.parse(String.format("%02d%02d%d", d, m + 1, y))
			targetField.setText(date?.formatBirthday())
		}, year, month, day)

		datePickerDialog.show()
	}

	companion object {
		private const val doneItem = 0
	}
}
