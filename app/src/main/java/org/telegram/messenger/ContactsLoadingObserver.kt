/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger

import android.os.Handler
import android.os.Looper
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate

class ContactsLoadingObserver private constructor(private val callback: Callback) {
	fun interface Callback {
		fun onResult(contactsLoaded: Boolean)
	}

	private val observer = NotificationCenterDelegate { id, _, _ ->
		if (id == NotificationCenter.contactsDidLoad) {
			onContactsLoadingStateUpdated(false)
		}
	}

	private val currentAccount = UserConfig.selectedAccount
	private val handler = Handler(Looper.myLooper()!!)
	private val notificationCenter = NotificationCenter.getInstance(currentAccount)
	private val contactsController = ContactsController.getInstance(currentAccount)
	private val releaseRunnable = Runnable { onContactsLoadingStateUpdated(true) }
	private var released = false

	fun start(expirationTime: Long) {
		if (!onContactsLoadingStateUpdated(false)) {
			notificationCenter.addObserver(observer, NotificationCenter.contactsDidLoad)
			handler.postDelayed(releaseRunnable, expirationTime)
		}
	}

	fun release() {
		if (!released) {
			notificationCenter.removeObserver(observer, NotificationCenter.contactsDidLoad)
			handler.removeCallbacks(releaseRunnable)
			released = true
		}
	}

	private fun onContactsLoadingStateUpdated(force: Boolean): Boolean {
		if (!released) {
			val contactsLoaded = contactsController.contactsLoaded

			if (contactsLoaded || force) {
				release()
				callback.onResult(contactsLoaded)
				return true
			}
		}

		return false
	}

	companion object {
		@JvmStatic
		fun observe(callback: Callback, expirationTime: Long) {
			ContactsLoadingObserver(callback).start(expirationTime)
		}
	}
}
