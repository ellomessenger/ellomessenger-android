/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.tgnet

import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.UserConfig
import org.telegram.ui.Country

class CountriesDataSource private constructor() {
	var countries: List<Country>? = null
		private set

	private val currentAccount: Int
		get() = UserConfig.selectedAccount

	private val connectionsManager: ConnectionsManager
		get() = AccountInstance.getInstance(currentAccount).connectionsManager

	fun loadCountries(callback: ((List<Country>?, TLRPC.TL_error?) -> Unit)? = null) {
		countries?.let {
			if (it.isNotEmpty()) {
				AndroidUtilities.runOnUIThread {
					callback?.invoke(it, null)
				}

				return
			}
		}

		val req = TLRPC.TL_help_getCountriesList()
		req.lang_code = ""

		connectionsManager.sendRequest(req, { response, error ->
			if (error == null) {
				countries = (response as TLRPC.TL_help_countriesList).countries.flatMap { c ->
					List(c.country_codes.size) { index ->
						val countryWithCode = Country()
						countryWithCode.name = c.default_name
						countryWithCode.code = c.country_codes[index].country_code
						countryWithCode.shortname = c.iso2
						countryWithCode
					}
				}
			}

			AndroidUtilities.runOnUIThread {
				callback?.invoke(countries, error)
			}
		}, ConnectionsManager.RequestFlagWithoutLogin or ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	companion object {
		val instance = CountriesDataSource()
	}
}
