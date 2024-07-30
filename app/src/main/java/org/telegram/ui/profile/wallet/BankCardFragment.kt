/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.addTextChangedListener
import org.telegram.messenger.R
import org.telegram.messenger.databinding.BankCardFragmentBinding
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment

class BankCardFragment(args: Bundle) : BaseFragment(args) {
	private var binding: BankCardFragmentBinding? = null
	private var walletId = 0L
	private var amount = 0f

	override fun onFragmentCreate(): Boolean {
		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID) ?: return false
		amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT) ?: return false
		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(context.getString(R.string.payment_card))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		binding = BankCardFragmentBinding.inflate(LayoutInflater.from(context))

		binding?.cardNumberField?.addTextChangedListener(CardNumberTextWatcher())

		binding?.cardNumberField?.addTextChangedListener {
			validateFields()
		}

		binding?.cardExpirationField?.addTextChangedListener(ExpiryDateTextWatcher())

		binding?.cardExpirationField?.addTextChangedListener {
			validateFields()
		}

		binding?.cardCvcField?.filters = arrayOf(InputFilter.LengthFilter(4))

		binding?.cardCvcField?.addTextChangedListener {
			validateFields()
		}

		val currency = arguments?.getString(WalletFragment.ARG_CURRENCY) ?: context.getString(R.string.wallet_fallback_currency)

		binding?.payButton?.text = context.getString(R.string.pay_amount, amount).fillElloCoinLogos()

		binding?.payButton?.setOnClickListener {
			val card = createCard() ?: return@setOnClickListener

			val args = Bundle()
			args.putInt(WalletFragment.ARG_MODE, WalletFragment.CARD)
			args.putFloat(WalletFragment.ARG_AMOUNT, amount)
			args.putString(WalletFragment.ARG_CURRENCY, currency)
			args.putString(WalletFragment.ARG_CARD_NUMBER, card.number)
			args.putString(WalletFragment.ARG_CARD_EXPIRY_MONTH, card.expirationMonth)
			args.putString(WalletFragment.ARG_CARD_EXPIRY_YEAR, card.expirationYear)
			args.putString(WalletFragment.ARG_CARD_CVC, card.cvc)
			args.putLong(WalletFragment.ARG_WALLET_ID, walletId)

			presentFragment(PaymentProcessingFragment(args))
		}

		fragmentView = binding?.root

		return binding?.root
	}

	private data class Card(
			val number: String,
			val expirationMonth: String,
			val expirationYear: String,
			val cvc: String,
	)

	private fun createCard(): Card? {
		val cardNumber = binding?.cardNumberField?.text?.toString()?.filterNot { it.isWhitespace() }?.takeIf { it.length in 13..19 }

		if (cardNumber.isNullOrEmpty()) {
			binding?.payButton?.isEnabled = false
			return null
		}

		val cardExpiration = binding?.cardExpirationField?.text?.toString()?.filterNot { it.isWhitespace() }?.split("/")

		if (cardExpiration?.size != 2) {
			binding?.payButton?.isEnabled = false
			return null
		}

		val month = cardExpiration[0].toIntOrNull()
		val year = cardExpiration[1].toIntOrNull()

		if (month == null || year == null || month !in 1..12 || year !in 1..99) {
			binding?.payButton?.isEnabled = false
			return null
		}

		val cvc = binding?.cardCvcField?.text?.toString()?.filterNot { it.isWhitespace() }

		if (cvc.isNullOrEmpty() || cvc.length !in 3..4) {
			binding?.payButton?.isEnabled = false
			return null
		}

		return Card(cardNumber, month.toString(), year.toString(), cvc)
	}

	private fun validateFields() {
		binding?.payButton?.isEnabled = (createCard() != null)
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
	}
}
