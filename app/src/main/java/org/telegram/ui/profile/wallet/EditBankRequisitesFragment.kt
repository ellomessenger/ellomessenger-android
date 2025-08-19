/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.profile.wallet

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.databinding.EditBankRequisitesFragmentBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.validateEmail
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.CountriesDataSource
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.WalletHelper
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Adapters.CountriesAdapter
import org.telegram.ui.Country
import org.telegram.ui.LoginActivity
import org.telegram.ui.profile.CodeVerificationFragment
import java.util.Locale

class EditBankRequisitesFragment(args: Bundle? = null) : BaseFragment(args) {
	private var requisite: ElloRpc.BankRequisite? = null
	private var amount = 0f
	private var binding: EditBankRequisitesFragmentBinding? = null
	private var saving = false
	private var currentPage = PRIVATE_INFO_ADDRESS_PAGE
	private var countries: List<Country>? = null
	private var isSave = false
	private var selectedRequisitesId: Long? = 0L
	private var walletId: Long = 0L
	private var connecting = false
	private var reqId = 0
	private var isEdit = false

	override fun onFragmentCreate(): Boolean {
		requisite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			arguments?.getSerializable(ARG_REQUISITE, ElloRpc.BankRequisite::class.java)
		}
		else {
			@Suppress("DEPRECATION") arguments?.getSerializable(ARG_REQUISITE) as? ElloRpc.BankRequisite
		}

		amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT, 0f) ?: 0f
		walletId = arguments?.getLong(WalletFragment.ARG_WALLET_ID, 0L) ?: return false
		amount = arguments?.getFloat(WalletFragment.ARG_AMOUNT, 0f) ?: return false
		isEdit = arguments?.getBoolean(EDIT_REQUISITES, false) ?: return false

		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)

		if (requisite != null) {
			actionBar?.setTitle(context.getString(R.string.edit_bank_requisites))
		}
		else {
			actionBar?.setTitle(context.getString(R.string.withdrawal))
		}

		actionBar?.castShadows = true

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					if (!saving) {
						if (currentPage == PRIVATE_INFO_ADDRESS_PAGE) {
							finishFragment()
							return
						}

						currentPage--

						updatePage()
					}
				}
			}
		})

		binding = EditBankRequisitesFragmentBinding.inflate(LayoutInflater.from(context)).also { binding ->
			val editFields = listOf(binding.bankNameField, binding.swiftField, binding.bankCityField, binding.bankStateField, binding.bankAccountNumberField, binding.bankAccountNumberVerifyField, binding.streetField, binding.cityField, binding.postalCodeField, binding.identificationNumberField, binding.bankRoutingNumberField, binding.firstNameField, binding.lastNameField, binding.phoneField, binding.emailField)

			editFields.forEach {
				it.doAfterTextChanged {

					checkNextButtonState()
				}
			}
		}

		binding?.nextButton?.setOnClickListener {
			nextPage()
		}

		binding?.backButton?.setOnClickListener {
			previousPage()
		}

		binding?.saveDetailsCheckbox?.setOnCheckedChangeListener { _, isChecked ->
			isSave = isChecked
		}

		if (amount > 0) {
			binding?.amountLabel?.text = String.format(Locale.getDefault(), "%.2f", amount)
			binding?.amountLabel?.visible()
			binding?.amountHeaderLabel?.visible()
		}
		else {
			binding?.amountLabel?.gone()
			binding?.amountHeaderLabel?.gone()
		}

		val recipientTypeAdapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, context.resources.getStringArray(R.array.recipient_type)) {
			override fun getFilter(): Filter {
				return object : Filter() {
					override fun performFiltering(constraint: CharSequence?): FilterResults {
						return FilterResults()
					}

					override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
						// unused
					}
				}
			}
		}

		binding?.recipientTypeSpinner?.setAdapter(recipientTypeAdapter)

		binding?.recipientTypeSpinner?.doOnTextChanged { _, _, _, _ ->
			if (recipientType == INDIVIDUAL) {
				binding?.identificationNumberLayout?.gone()
				binding?.addIdentificationNumber?.gone()
			}
			else {
				binding?.identificationNumberLayout?.visible()
				binding?.addIdentificationNumber?.visible()

				binding?.identificationNumberLayout?.hint = context.getString(R.string.business_identification_number)
			}

			checkNextButtonState()
		}

		binding?.currencyField?.filters = arrayOf(InputFilter.AllCaps())

		binding?.currencyField?.addTextChangedListener {
			checkNextButtonState()
		}

		binding?.countrySpinner?.setOnItemClickListener { _, _, _, _ ->
			setCountriesAdapter()
		}

		binding?.countrySpinner?.doOnTextChanged { text, _, _, _ ->
			val countryName = text?.toString()?.lowercase()

			if (countryName == "USA".lowercase() || countryName == "United States".lowercase()) {
				binding?.currencyHeader?.gone()
				binding?.currencyLayout?.gone()

				binding?.bankAccountNumberLayout?.hint = context.getString(R.string.recipient_account_number)
				binding?.bankAccountNumberVerifyLayout?.hint = context.getString(R.string.verify_recipient_account_number)

				binding?.bankCityLayout?.visible()
				binding?.bankStateLayout?.visible()
				binding?.bankStreetLayout?.visible()
				binding?.bankRoutingNumberLayout?.visible()
				binding?.addBankAccountInfoHint?.gone()
				binding?.addIdentificationNumber?.gone()

				binding?.recipientTypeHeader?.gone()
				binding?.recipientTypeSpinnerLayout?.gone()
				binding?.identificationNumberLayout?.gone()

				binding?.swiftLayout?.gone()
				binding?.bankStateLayout?.visible()
			}
			else {
				binding?.bankAccountNumberLayout?.hint = context.getString(R.string.iban_number)
				binding?.bankAccountNumberVerifyLayout?.hint = context.getString(R.string.re_enter_iban_number)

				binding?.currencyHeader?.visible()
				binding?.currencyLayout?.visible()

				binding?.bankCityLayout?.gone()
				binding?.bankStreetLayout?.gone()
				binding?.bankRoutingNumberLayout?.gone()
				binding?.addBankAccountInfoHint?.visible()

				binding?.recipientTypeHeader?.visible()
				binding?.recipientTypeSpinnerLayout?.visible()

				binding?.swiftLayout?.visible()
				binding?.bankStateLayout?.gone()
			}
		}

		loadCountries()

		requisite?.let {
			fillRequisite(it)
		}

		fragmentView = binding?.root

		updatePage()

		return binding?.root
	}

	private fun transferOutToBank() {
		if (selectedRequisitesId == 0L) {
			return
		}

		connecting = true

		actionBar?.backButton?.isEnabled = false

		if (walletHelper.currentTransferOutPaymentId == null) {
			reqId = walletHelper.createWithdrawPaymentRequest(walletId = walletId, amount = amount, paymentId = null) { paymentId, _, _, _, _, _, _, error ->
				val context = context ?: return@createWithdrawPaymentRequest

				if (error != null) {
					Toast.makeText(context, error.text ?: context.getString(R.string.UnknownError), Toast.LENGTH_SHORT).show()

					actionBar?.backButton?.isEnabled = true

					connecting = false

					return@createWithdrawPaymentRequest
				}

				walletHelper.currentTransferOutPaymentId = paymentId

				if (!walletHelper.currentTransferOutPaymentId.isNullOrEmpty()) {
					requestVerificationCode()
				}
				else {
					actionBar?.backButton?.isEnabled = true
					connecting = false
					Toast.makeText(context, context.getString(R.string.failed_to_create_transaction), Toast.LENGTH_SHORT).show()
				}
			}
		}
		else {
			requestVerificationCode()
		}
	}

	private fun requestVerificationCode() {
		val req = ElloRpc.withdrawSendApproveCode()

		reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
			val context = context ?: return@sendRequest

			if (response is TLRPC.TLBizDataRaw) {
				val data = response.readData<ElloRpc.WithdrawSendApproveCodeResponse>()

				AndroidUtilities.runOnUIThread {
					connecting = false

					if (data?.status == true) {
						goToVerification(data.confirmationExpire)
					}
					else {
						actionBar?.backButton?.isEnabled = true

						val message = data?.message

						if (message.isNullOrEmpty()) {
							Toast.makeText(context, context.getString(R.string.failed_to_send_verification_code), Toast.LENGTH_SHORT).show()
						}
						else {
							Toast.makeText(context, String.format("%s: %s", context.getString(R.string.failed_to_send_verification_code), message), Toast.LENGTH_SHORT).show()
						}
					}
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					actionBar?.backButton?.isEnabled = true
					connecting = false

					val message = error?.text

					if (message.isNullOrEmpty()) {
						Toast.makeText(context, context.getString(R.string.failed_to_send_verification_code), Toast.LENGTH_SHORT).show()
					}
					else {
						Toast.makeText(context, String.format("%s: %s", context.getString(R.string.failed_to_send_verification_code), message), Toast.LENGTH_SHORT).show()
					}
				}
			}
		}
	}

	private fun goToVerification(codeExpiration: Long) {
		val args = Bundle()
		args.putFloat(WalletFragment.ARG_AMOUNT, amount)
		args.putLong(WalletFragment.ARG_WALLET_ID, walletId)
		args.putString(CodeVerificationFragment.ARG_VERIFICATION_MODE, LoginActivity.VerificationMode.TRANSFER_OUT_BANK.name)
		args.putLong(CodeVerificationFragment.ARG_EXPIRATION, codeExpiration)
		selectedRequisitesId?.let { args.putLong(WalletFragment.ARG_BANK_WITHDRAW_REQUISITES_ID, it) }

		presentFragment(CodeVerificationFragment(args))

		actionBar?.backButton?.isEnabled = true
	}

	private fun initRequestForPage() {
		binding?.firstName?.text = binding?.firstNameField?.text.toString()
		binding?.lastName?.text = binding?.lastNameField?.text.toString()
		binding?.phoneNumber?.text = binding?.phoneField?.text.toString()
		binding?.email?.text = binding?.emailField?.text.toString()

		val address = "${binding?.cityField?.text.toString()} St. ${binding?.streetField?.text.toString()}, ${binding?.stateField?.text.toString()} ${binding?.postalCodeField?.text.toString()}"
		binding?.yourAddress?.text = address

		binding?.amount?.text = binding?.amountLabel?.text

		val countryName = binding?.countrySpinner?.text.toString().lowercase()

		if (countryName == "USA".lowercase() || countryName == "United States".lowercase()) {
			binding?.usaBankInfoPanel?.visible()
			binding?.routingRecipientNumbersPanel?.visible()

			binding?.usaBankCountry?.text = binding?.countrySpinner?.text.toString()

			binding?.usaBankName?.text = binding?.bankNameField?.text.toString()

			val bankAddress = "${binding?.bankCityField?.text.toString()} St. ${binding?.bankStreetField?.text.toString()}, ${binding?.bankStateField?.text.toString()} ${binding?.postalCodeField?.text.toString()}"

			binding?.bankAddress?.text = bankAddress

			binding?.routingNumber?.text = binding?.bankRoutingNumberField?.text.toString()
			binding?.recipientAccountNumber?.text = binding?.bankAccountNumberField?.text.toString()
		}
		else {
			binding?.bankInfoPanel?.visible()
			binding?.bankNamePanel?.visible()
			binding?.swiftIbanPanel?.visible()

			binding?.bankCountry?.text = binding?.countrySpinner?.text.toString()

			binding?.recipientType?.text = binding?.recipientTypeSpinner?.text.toString()
			binding?.currency?.text = binding?.currencyField?.text.toString()

			if (binding?.recipientType?.text.toString().lowercase() == BUSINESS) {
				binding?.titleIndividualIdentificationNumber?.visible()
				binding?.individualIdentificationNumber?.visible()

				binding?.individualIdentificationNumber?.text = binding?.identificationNumberField?.text.toString()
			}
			else {
				binding?.titleIndividualIdentificationNumber?.gone()
				binding?.individualIdentificationNumber?.gone()
			}

			binding?.bankName?.text = binding?.bankNameField?.text.toString()

			binding?.swift?.text = binding?.swiftField?.text.toString()
			binding?.iban?.text = binding?.bankAccountNumberField?.text.toString()
		}
	}

	private fun setCountriesAdapter() {
		val countries = countries ?: return
		val context = context ?: return
		val countriesAdapter = CountriesAdapter(context, R.layout.country_view_holder, countries)
		binding?.countrySpinner?.setAdapter(countriesAdapter)
	}

	private fun loadCountries() {
		CountriesDataSource.instance.loadCountries { countries, error ->
			if (error == null && countries != null) {
				this.countries = countries

				setCountriesAdapter()

				countries.find { country ->
					country.code?.lowercase() == requisite?.bankInfo?.country?.lowercase()
				}?.let { country ->
					binding?.countrySpinner?.setText(country.name, false)
				}

				requisite?.let { requisite ->
					fillRequisite(requisite)
				}
			}
			else {
				Toast.makeText(context, R.string.failed_to_load_countries, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun fillRequisite(requisite: ElloRpc.BankRequisite) {
		// personal info
		binding?.bankRoutingNumberField?.setText(requisite.bankInfo?.routingNumber)
		binding?.firstNameField?.setText(requisite.personInfo?.firstName)
		binding?.lastNameField?.setText(requisite.personInfo?.lastName)
		binding?.phoneField?.setText(requisite.personInfo?.phoneNumber)
		binding?.emailField?.setText(requisite.personInfo?.email)

		// address
		binding?.addressNameField?.setText(requisite.addressInfo?.address)
		binding?.streetField?.setText(requisite.addressInfo?.street)
		binding?.cityField?.setText(requisite.addressInfo?.city)
		binding?.stateField?.setText(requisite.addressInfo?.state)
		binding?.postalCodeField?.setText(requisite.addressInfo?.postalCode)

		when (requisite.recipientType) {
			INDIVIDUAL -> {
				binding?.recipientTypeSpinner?.setText(R.string.individual)
			}

			BUSINESS -> {
				binding?.recipientTypeSpinner?.setText(R.string.business)
			}
		}

		binding?.identificationNumberField?.setText(requisite.businessIdNumber)
		binding?.currencyField?.setText((requisite.currency ?: WalletHelper.DEFAULT_CURRENCY).uppercase())

		// bank address
		binding?.bankStateField?.setText(requisite.bankInfo?.state)
		binding?.bankNameField?.setText(requisite.bankInfo?.name)
		binding?.swiftField?.setText(requisite.bankInfo?.swift)
		binding?.bankStreetField?.setText(requisite.bankInfo?.street)
		binding?.bankCityField?.setText(requisite.bankInfo?.city)
		binding?.bankAccountNumberField?.setText(requisite.bankInfo?.recipientAccountNumber)
		binding?.bankAccountNumberVerifyField?.setText(requisite.bankInfo?.recipientAccountNumber)

		CountriesDataSource.instance.countries?.find { country ->
			country.code?.lowercase() == requisite.bankInfo?.country?.lowercase()
		}?.let { country ->
			binding?.countrySpinner?.setText(country.name, false)
		} ?: run {
			binding?.countrySpinner?.setText(requisite.bankInfo?.country, false)
		}

		checkNextButtonState()
	}

	private fun saveRequisite() {
		val isTemplate = isSave
		val recipientType = recipientType
		val businessIdNumber = identificationNumber
		val personalFirstName = firstName ?: return
		val personalLastName = lastName ?: return
		val personalPhoneNumber = phone ?: return
		val personalEmail = email ?: return
		val bankCountry = bankCountry?.code ?: return
		val bankRoutingNumber = routingNumber
		val bankName = bankName ?: return
		val bankStreet = bankState ?: return
		val bankCity = bankCity ?: return
		val bankState = bankState
		val bankSwift = swift
		val bankRecipientAccountNumber = bankAccountNumber ?: return
		val userAddressAddress = addressName ?: return
		val userAddressStreet = street ?: return
		val userAddressCity = city ?: return
		val userAddressState = state ?: return
		val userAddressZipCode = postalCode ?: return
		val userAddressPostalCode = postalCode ?: return
		val bankCurrency = bankCurrency

		val request = ElloRpc.createBankWithdrawRequisites(
				isTemplate = isTemplate,
				recipientType = recipientType,
				businessIdNumber = businessIdNumber,
				personalFirstName = personalFirstName,
				personalLastName = personalLastName,
				personalPhoneNumber = personalPhoneNumber,
				personalEmail = personalEmail,
				bankCountry = bankCountry,
				bankRoutingNumber = bankRoutingNumber,
				bankName = bankName,
				bankStreet = bankStreet,
				bankCity = bankCity,
				bankState = bankState,
				bankSwift = bankSwift,
				bankRecipientAccountNumber = bankRecipientAccountNumber,
				userAddressAddress = userAddressAddress,
				userAddressStreet = userAddressStreet,
				userAddressCity = userAddressCity,
				userAddressState = userAddressState,
				userAddressZipCode = userAddressZipCode,
				userAddressPostalCode = userAddressPostalCode,
				currency = bankCurrency?.uppercase(),
		)

		setControlsEnabled(false)

		connectionsManager.sendRequest(request) { response, error ->
			if (response is TLRPC.TLBizDataRaw) {
				val requisite = response.readData<ElloRpc.BankRequisite>()

				when {
					isSave -> {
						AndroidUtilities.runOnUIThread {
							Toast.makeText(context, R.string.requisite_saved, Toast.LENGTH_SHORT).show()
							finishFragment()
						}
					}

					requisite != null && requisite.requisitesId > 0L -> {
						AndroidUtilities.runOnUIThread {
							selectedRequisitesId = requisite.requisitesId
							transferOutToBank()
						}
					}

					else -> {
						AndroidUtilities.runOnUIThread {
							Toast.makeText(context, R.string.failed_to_save_requisite, Toast.LENGTH_SHORT).show()
							setControlsEnabled(true)
						}
					}
				}

			}
			else {
				AndroidUtilities.runOnUIThread {
					val context = context ?: return@runOnUIThread
					val errorMessage = error?.text

					val toastText = if (errorMessage.isNullOrEmpty()) {
						context.getString(R.string.failed_to_save_requisite)
					}
					else {
						String.format("%s: %s", context.getString(R.string.failed_to_save_requisite), errorMessage)
					}

					Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()

					setControlsEnabled(true)
				}
			}
		}
	}

	private fun editRequisites() {
		val templateId = requisite?.requisitesId ?: return
		val recipientType = recipientType
		val businessIdNumber = identificationNumber
		val personalFirstName = firstName ?: return
		val personalLastName = lastName ?: return
		val personalPhoneNumber = phone ?: return
		val personalEmail = email ?: return
		val bankCountry = bankCountry?.code ?: return
		val bankRoutingNumber = routingNumber
		val bankName = bankName ?: return
		val bankStreet = bankState ?: return
		val bankCity = bankCity ?: return
		val bankState = bankState
		val bankSwift = swift
		val bankRecipientAccountNumber = bankAccountNumber ?: return
		val userAddressAddress = addressName ?: return
		val userAddressStreet = street ?: return
		val userAddressCity = city ?: return
		val userAddressState = state ?: return
		val userAddressZipCode = postalCode ?: return
		val userAddressPostalCode = postalCode ?: return
		val bankCurrency = bankCurrency

		val request = ElloRpc.editBankWithdrawRequisites(
				templateId = templateId,
				recipientType = recipientType,
				businessIdNumber = businessIdNumber,
				personalFirstName = personalFirstName,
				personalLastName = personalLastName,
				personalPhoneNumber = personalPhoneNumber,
				personalEmail = personalEmail,
				bankCountry = bankCountry,
				bankRoutingNumber = bankRoutingNumber,
				bankName = bankName,
				bankStreet = bankStreet,
				bankCity = bankCity,
				bankState = bankState,
				bankSwift = bankSwift,
				bankRecipientAccountNumber = bankRecipientAccountNumber,
				userAddressAddress = userAddressAddress,
				userAddressStreet = userAddressStreet,
				userAddressCity = userAddressCity,
				userAddressState = userAddressState,
				userAddressZipCode = userAddressZipCode,
				userAddressPostalCode = userAddressPostalCode,
				currency = bankCurrency?.uppercase(),
		)

		setControlsEnabled(false)

		connectionsManager.sendRequest(request) { response, error ->
			if (response is TLRPC.TLBizDataRaw) {
				val requisite = response.readData<ElloRpc.BankRequisite>()

				if (requisite != null) {
					AndroidUtilities.runOnUIThread {
						Toast.makeText(context, R.string.requisite_edited, Toast.LENGTH_SHORT).show()
						finishFragment()
					}
				}
				else {
					AndroidUtilities.runOnUIThread {
						Toast.makeText(context, R.string.failed_to_edit_requisite, Toast.LENGTH_SHORT).show()
						setControlsEnabled(true)
					}
				}

			}
			else {
				AndroidUtilities.runOnUIThread {
					val context = context ?: return@runOnUIThread
					val errorMessage = error?.text

					val toastText = if (errorMessage.isNullOrEmpty()) {
						context.getString(R.string.failed_to_edit_requisite)
					}
					else {
						String.format("%s: %s", context.getString(R.string.failed_to_edit_requisite), errorMessage)
					}

					Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()

					setControlsEnabled(true)
				}
			}
		}
	}

	private fun setControlsEnabled(enabled: Boolean) {
		binding?.nextButton?.isEnabled = enabled
		binding?.backButton?.isEnabled = enabled
		actionBar?.backButton?.isEnabled = enabled
	}

	private fun nextPage() {
		if (isEdit && isSave) {
			editRequisites()

			return
		}

		if (isSave) {
			saveRequisite()

			return
		}

		if (currentPage == WITHDRAW_PAGE) {
			if (validateCurrentPage()) {
				AndroidUtilities.hideKeyboard(binding?.root)
				saveRequisite()
			}

			return
		}

		if (!validateCurrentPage()) {
			return
		}

		currentPage++

		updatePage()
	}

	private fun previousPage() {
		if (currentPage == PRIVATE_INFO_ADDRESS_PAGE) {
			return
		}

		currentPage--

		updatePage()
	}

	private val bankState: String?
		get() = binding?.bankStateField?.text?.toString()?.trim()
	private val routingNumber: String?
		get() = binding?.bankRoutingNumberField?.text?.toString()?.trim()
	private val bankCountry: Country?
		get() = (binding?.countrySpinner?.adapter as? CountriesAdapter)?.countries?.find { it.name == binding?.countrySpinner?.text?.toString()?.trim() }
	private val firstName: String?
		get() = binding?.firstNameField?.text?.toString()?.trim()
	private val lastName: String?
		get() = binding?.lastNameField?.text?.toString()?.trim()
	private val phone: String?
		get() = binding?.phoneField?.text?.toString()?.trim()
	private val email: String?
		get() = binding?.emailField?.text?.toString()?.trim()
	private val addressName: String?
		get() = binding?.addressNameField?.text?.toString()?.trim()
	private val street: String?
		get() = binding?.streetField?.text?.toString()?.trim()
	private val city: String?
		get() = binding?.cityField?.text?.toString()?.trim()
	private val state: String?
		get() = binding?.stateField?.text?.toString()?.trim()
	private val postalCode: String?
		get() = binding?.postalCodeField?.text?.toString()?.trim()
	private val recipientType: String
		get() = when (binding?.recipientTypeSpinner?.text?.toString()?.trim()) {
			context?.getString(R.string.individual) -> INDIVIDUAL
			context?.getString(R.string.business) -> BUSINESS
			else -> INDIVIDUAL
		}
	private val identificationNumber: String?
		get() = binding?.identificationNumberField?.text?.toString()?.trim()
	private val bankName: String?
		get() = binding?.bankNameField?.text?.toString()?.trim()
	private val swift: String?
		get() = binding?.swiftField?.text?.toString()?.trim()
	private val bankCity: String?
		get() = binding?.bankCityField?.text?.toString()?.trim()
	private val bankAccountNumber: String?
		get() = binding?.bankAccountNumberField?.text?.toString()?.trim()?.replace(" ", "")?.replace("-", "")
	private val bankAccountNumberVerify: String?
		get() = binding?.bankAccountNumberVerifyField?.text?.toString()?.trim()?.replace(" ", "")?.replace("-", "")
	private val bankCurrency: String?
		get() = binding?.currencyField?.text?.toString()?.trim()

	private val isUnitedStates: Boolean
		get() = bankCountry?.code?.lowercase() == "us"

	private fun checkNextButtonState() {
		binding?.nextButton?.isEnabled = validateCurrentPage(showToast = false)
	}

	private fun validateCurrentPage(showToast: Boolean = true): Boolean {
		val context = context ?: return false

		when (currentPage) {
			PRIVATE_INFO_ADDRESS_PAGE -> {
				if (firstName.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.first_name_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (lastName.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.last_name_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (phone.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.phone_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (email.isNullOrEmpty() || email?.validateEmail() == false) {
					if (showToast) {
						Toast.makeText(context, R.string.email_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (street.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.street_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (state.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.state_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (city.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.city_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (postalCode.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.postal_code_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				return true
			}

			BANK_PAGE -> {
				if (bankCountry?.code == null) {
					if (showToast) {
						Toast.makeText(context, R.string.country_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (bankAccountNumber.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.account_number_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (bankAccountNumber != bankAccountNumberVerify) {
					if (showToast) {
						Toast.makeText(context, R.string.account_number_confirmation_is_wrong, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (bankName.isNullOrEmpty()) {
					if (showToast) {
						Toast.makeText(context, R.string.bank_name_is_empty, Toast.LENGTH_SHORT).show()
					}

					return false
				}

				if (isUnitedStates) {
					if (binding?.bankCityLayout?.isVisible == true) {
						if (bankCity.isNullOrEmpty()) {
							if (showToast) {
								Toast.makeText(context, R.string.city_is_empty, Toast.LENGTH_SHORT).show()
							}

							return false
						}
					}

					if (binding?.bankRoutingNumberLayout?.isVisible == true) {
						if (routingNumber.isNullOrEmpty()) {
							if (showToast) {
								Toast.makeText(context, R.string.routing_number_is_empty, Toast.LENGTH_SHORT).show()
							}

							return false
						}
					}

					if (binding?.bankStateLayout?.isVisible == true) {
						if (bankState.isNullOrEmpty()) {
							if (showToast) {
								Toast.makeText(context, R.string.state_is_empty, Toast.LENGTH_SHORT).show()
							}

							return false
						}
					}
				}
				else {
					if (binding?.swiftLayout?.isVisible == true) {
						if (swift.isNullOrEmpty()) {
							if (showToast) {
								Toast.makeText(context, R.string.swift_is_empty, Toast.LENGTH_SHORT).show()
							}

							return false
						}
					}

					if (binding?.currencyLayout?.isVisible == true) {
						if (bankCurrency.isNullOrEmpty()) {
							if (showToast) {
								Toast.makeText(context, R.string.currency_is_empty, Toast.LENGTH_SHORT).show()
							}

							return false
						}
					}

					if (binding?.identificationNumberLayout?.isVisible == true && binding?.recipientTypeSpinner?.text?.toString()?.trim() == BUSINESS) {
						if (identificationNumber.isNullOrEmpty()) {
							if (showToast) {
								Toast.makeText(context, R.string.identification_number_is_empty, Toast.LENGTH_SHORT).show()
							}

							return false
						}
					}
				}

				return true
			}

			WITHDRAW_PAGE -> {
				return true
			}
		}

		return false
	}

	private fun updatePage() {
		AndroidUtilities.hideKeyboard(binding?.root)

		when (currentPage) {
			PRIVATE_INFO_ADDRESS_PAGE -> {
				binding?.backButton?.gone()
				binding?.nextButton?.setText(R.string.next)

				binding?.personalInfoContainer?.visible()
				binding?.addressContainer?.gone()
				binding?.bankAddressContainer?.gone()
			}

			BANK_PAGE -> {
				binding?.backButton?.visible()
				binding?.nextButton?.setText(R.string.withdraw)

				binding?.personalInfoContainer?.gone()
				binding?.addressContainer?.visible()
				binding?.bankAddressContainer?.gone()
			}

			WITHDRAW_PAGE -> {
				initRequestForPage()
				binding?.backButton?.visible()
				binding?.nextButton?.setText(R.string.withdraw)
				binding?.personalInfoContainer?.gone()
				binding?.addressContainer?.gone()
				binding?.bankAddressContainer?.visible()
			}

			else -> {
				// unused
			}
		}

		checkNextButtonState()
	}

	override fun canBeginSlide(): Boolean {
		return !saving
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding = null
		saving = false

		if (reqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, false)
			reqId = 0
		}

		connecting = false
	}

	companion object {
		const val ARG_REQUISITE = "requisite"
		private const val INDIVIDUAL = "individual"
		private const val BUSINESS = "business"
		const val EDIT_REQUISITES = "is_edit"
		private const val PRIVATE_INFO_ADDRESS_PAGE = 0
		private const val BANK_PAGE = 1
		private const val WITHDRAW_PAGE = 2
	}
}
