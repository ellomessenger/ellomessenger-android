/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.SystemClock
import org.telegram.messenger.FileLog
import java.util.concurrent.CopyOnWriteArrayList

open class ForegroundDetector(application: Application) : ActivityLifecycleCallbacks {
	interface Listener {
		fun onBecameForeground()

		fun onBecameBackground()
	}

	private var refs = 0
	private var enterBackgroundTime = 0L
	private val listeners = CopyOnWriteArrayList<Listener>()

	var hasStarted = false
		private set

	private var wasInBackground = true
		set(value) {
			field = value
			hasStarted = true
		}

	init {
		instance = this
		application.registerActivityLifecycleCallbacks(this)
	}

	val isForeground: Boolean
		get() = refs > 0

	val isBackground: Boolean
		get() = refs == 0

	fun addListener(listener: Listener) {
		listeners.add(listener)
	}

	fun removeListener(listener: Listener) {
		listeners.remove(listener)
	}

	override fun onActivityStarted(activity: Activity) {
		if (++refs == 1) {
			if (SystemClock.elapsedRealtime() - enterBackgroundTime < 200) {
				wasInBackground = false
			}

			FileLog.d("switch to foreground")

			for (listener in listeners) {
				try {
					listener.onBecameForeground()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
	}

	fun isWasInBackground(reset: Boolean): Boolean {
		if (reset && (SystemClock.elapsedRealtime() - enterBackgroundTime < 200)) {
			wasInBackground = false
		}

		return wasInBackground
	}

	fun resetBackgroundVar() {
		wasInBackground = false
	}

	override fun onActivityStopped(activity: Activity) {
		if (--refs == 0) {
			enterBackgroundTime = SystemClock.elapsedRealtime()
			wasInBackground = true

			FileLog.d("switch to background")

			for (listener in listeners) {
				try {
					listener.onBecameBackground()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
	}

	override fun onActivityResumed(activity: Activity) {
	}

	override fun onActivityPaused(activity: Activity) {
	}

	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
	}

	override fun onActivityDestroyed(activity: Activity) {
	}

	companion object {
		@JvmStatic
		var instance: ForegroundDetector? = null
			private set
	}
}
