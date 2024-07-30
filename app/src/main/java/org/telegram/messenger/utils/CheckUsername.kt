/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhailo Mykytyn, Nikita Denin, Ello 2023.
 */
package org.telegram.messenger.utils

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC

class CheckUsername(private val resultObserver: Result) {
	private var checkReqId = 0
	private var lastCheckName: String? = null
	private var checkRunnable: Runnable? = null
	private var lastNameAvailable = false

	fun interface Result {
		fun done(errorText: String?)
	}

	fun check(name: String) {
		if (checkRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(checkRunnable)

			checkRunnable = null
			lastCheckName = null

			if (checkReqId != 0) {
				ConnectionsManager.getInstance(UserConfig.selectedAccount).cancelRequest(checkReqId, true)
			}
		}

		lastNameAvailable = false

		if (name.isBlank()) {
			resultObserver.done(ApplicationLoader.applicationContext.getString(R.string.username_empty))
			return
		}

		if (name.startsWith("_") || name.endsWith("_") || name.contains("__")) {
			resultObserver.done(ApplicationLoader.applicationContext.getString(R.string.LinkInvalid))
			return
		}

		for (a in name.indices) {
			val ch = name[a]

			if (a == 0 && ch >= '0' && ch <= '9') {
				resultObserver.done(ApplicationLoader.applicationContext.getString(R.string.UsernameInvalidStartNumber))
				return
			}

			if (!(ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' || ch == '_')) {
				resultObserver.done(ApplicationLoader.applicationContext.getString(R.string.LinkInvalid))
				return
			}
		}

		if (name.length < MIN_LENGTH) {
			resultObserver.done(ApplicationLoader.applicationContext.getString(R.string.UsernameInvalidShort))
			return
		}

		lastCheckName = name

		checkRunnable = Runnable {
			val req = TLRPC.TL_account_checkUsername()
			req.username = name

			checkReqId = ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, { response, error ->
				AndroidUtilities.runOnUIThread {
					checkReqId = 0

					if (lastCheckName != null && lastCheckName == name) {
						lastNameAvailable = if (error == null && response is TLRPC.TL_boolTrue) {
							resultObserver.done(null)
							true
						}
						else {
							resultObserver.done(ApplicationLoader.applicationContext.getString(R.string.UsernameAlreadyTaken))
							false
						}
					}
				}
			}, ConnectionsManager.RequestFlagFailOnServerErrors)
		}

		AndroidUtilities.runOnUIThread(checkRunnable, 300)
	}

	companion object {
		private const val MIN_LENGTH = 5
	}
}
