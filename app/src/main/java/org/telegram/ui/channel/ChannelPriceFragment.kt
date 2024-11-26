/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.channel

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.databinding.FragmentChannelPriceBinding
import org.telegram.messenger.utils.formatBirthday
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.parseBirthday
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.information.InformationFragment.Companion.TERMS_AND_CONDITIONS_URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChannelPriceFragment(args: Bundle) : BaseFragment(args) {
	private var binding: FragmentChannelPriceBinding? = null
	private var isCourse = false

	override fun onFragmentCreate(): Boolean {
		isCourse = arguments?.getBoolean("isCourse", false)!!
		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(if (isCourse) R.string.Schedule else R.string.subscription_channel))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> finishFragment()
				}
			}
		})

		binding = FragmentChannelPriceBinding.inflate(LayoutInflater.from(context))

//		binding?.priceInputLayout?.hint = if (isCourse) context.getString(R.string.online_course_price) else context.getString(R.string.price)
		binding?.tvPrice?.text = if (isCourse) context.getString(R.string.online_course_price) else context.getString(R.string.price)
		binding?.priceField?.hint = ""

		binding?.priceField?.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
			binding?.priceInputLayout?.isHintEnabled = hasFocus

			if (hasFocus) {
				binding?.priceField?.hint = context.getString(R.string.zero_hint)
			}
			else {
				binding?.priceField?.hint = if (isCourse) context.getString(R.string.online_course_price) else context.getString(R.string.price)
			}
		}

		binding?.confirmButton?.setOnClickListener {
			nextStep()
		}

		if (isCourse) {
			binding?.durationLabel?.gone()

			binding?.startField?.setOnClickListener { view ->
				val datePickerDialog = createCalendar(binding?.startField!!, view.context)
				datePickerDialog.datePicker.minDate = System.currentTimeMillis()

				val endDate = binding?.endField?.text?.toString()?.takeIf { it.isNotEmpty() }?.parseBirthday()
				if (endDate != null) {
					datePickerDialog.datePicker.maxDate = endDate.time - TimeUnit.DAYS.toMillis(1)
				}

				datePickerDialog.show()
			}

			binding?.endField?.setOnClickListener { view ->
				val datePickerDialog = createCalendar(binding?.endField!!, view.context)
				val startDate = binding?.startField?.text?.toString()?.takeIf { it.isNotEmpty() }?.parseBirthday()

				datePickerDialog.datePicker.minDate = (startDate?.time ?: System.currentTimeMillis()) + TimeUnit.DAYS.toMillis(1)

				datePickerDialog.show()
			}
		}
		else {
			binding?.datesHeader?.gone()
			binding?.startDateLayout?.gone()
			binding?.endDateLayout?.gone()
			binding?.dateDescription?.gone()
		}

		setAgreementTextWithLink(context, binding?.agreementText, if (isCourse) context.getString(R.string.online_course).lowercase() else context.getString(R.string.subscription_channel).lowercase())

		fragmentView = binding?.root

		return binding?.root
	}

	private fun nextStep() {
		val price = binding?.priceField?.text?.toString()?.trim()?.run {
			if (isNotEmpty()) {
				val parts = split(".")
				if (parts.size > 2 || (parts.size == 2 && parts[1].length > 2)) {
					Toast.makeText(context, R.string.invalid_price_value, Toast.LENGTH_SHORT).show()
					return
				}
				try {
					toFloat()
				}
				catch (e: NumberFormatException) {
					Toast.makeText(context, R.string.invalid_price_value, Toast.LENGTH_SHORT).show()
					return
				}
			}
			else {
				Toast.makeText(context, R.string.invalid_price_value, Toast.LENGTH_SHORT).show()
				return
			}
		} ?: 0f

		if (price < MINIMUM_CHANNEL_PRICE) {
			Toast.makeText(context, R.string.channel_price_too_low, Toast.LENGTH_SHORT).show()
			return
		}

		if (price > MAXIMUM_CHANNEL_PRICE) {
			Toast.makeText(context, R.string.price_too_large, Toast.LENGTH_SHORT).show()
			return
		}

		val args = Bundle()
		args.putDouble("cost", price.toDouble())
		args.putBoolean("isPublic", arguments?.getBoolean("isPublic") ?: true)
		args.putBoolean("isPaid", true)
		args.putBoolean("isCourse", isCourse)

		if (isCourse) {
			val startDate = binding?.startField?.text?.toString()?.trim()
			val endDate = binding?.endField?.text?.toString()?.trim()

			if (startDate?.isEmpty() == true) {
				Toast.makeText(context, R.string.select_start_date, Toast.LENGTH_SHORT).show()
				return
			}

			if (endDate?.isEmpty() == true) {
				Toast.makeText(context, R.string.select_end_date, Toast.LENGTH_SHORT).show()
				return
			}

			args.putLong("startDate", startDate?.parseBirthday()?.time ?: 0L)
			args.putLong("endDate", endDate?.parseBirthday()?.time ?: 0L)
		}

		arguments?.getString("inviteLink")?.let {
			args.putString("inviteLink", it)
		}

		arguments?.getLongArray("users")?.let {
			args.putLongArray("users", it)
		}

		presentFragment(NewChannelSetupFragment(args))
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}

	private fun createCalendar(textView: TextView, context: Context): DatePickerDialog {
		val calendar = Calendar.getInstance()
		val year = calendar[Calendar.YEAR]
		val month = calendar[Calendar.MONTH]
		val day = calendar[Calendar.DAY_OF_MONTH]

		return DatePickerDialog(context, { _, y, m, d ->
			val formatter = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
			val date = formatter.parse(String.format("%02d%02d%d", d, m + 1, y))
			// DateTime(date).withZone(DateTimeZone.UTC).toString("MMMM dd YYYY")
			textView.text = date?.formatBirthday()
		}, year, month, day)
	}

	private fun setAgreementTextWithLink(context: Context, textView: TextView?, channelType: String) {
		val agreementText = textView?.context?.getString(R.string.agreement_text, channelType)

		val userAgreementStart = agreementText?.indexOf(context.getString(R.string.UserAgreement))
		val userAgreementEnd = userAgreementStart?.plus(context.getString(R.string.UserAgreement).length)

		val spannableString = SpannableString(agreementText)

		val clickableSpan = object : ClickableSpan() {
			override fun onClick(widget: View) {
				Browser.openUrl(context, TERMS_AND_CONDITIONS_URL)
			}
		}

		spannableString.setSpan(clickableSpan, userAgreementStart ?: 0, userAgreementEnd ?: 0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		spannableString.setSpan(ForegroundColorSpan(context.getColor(R.color.dark)), userAgreementStart ?: 0, userAgreementEnd ?: 0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

		textView?.text = spannableString
		textView?.movementMethod = LinkMovementMethod.getInstance()
	}

	companion object {
		private const val MINIMUM_CHANNEL_PRICE = 1f
		private const val MAXIMUM_CHANNEL_PRICE = 9999f
	}
}
