/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

import org.telegram.messenger.FileLog;

import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;

public class ForegroundDetector implements Application.ActivityLifecycleCallbacks {

	public interface Listener {
		void onBecameForeground();

		void onBecameBackground();
	}

	private int refs;
	private boolean wasInBackground = true;
	private long enterBackgroundTime = 0;
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
	private static ForegroundDetector Instance = null;

	public static ForegroundDetector getInstance() {
		return Instance;
	}

	public ForegroundDetector(Application application) {
		Instance = this;
		application.registerActivityLifecycleCallbacks(this);
	}

	public boolean isForeground() {
		return refs > 0;
	}

	public boolean isBackground() {
		return refs == 0;
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	@Override
	public void onActivityStarted(@NonNull Activity activity) {
		if (++refs == 1) {
			if (SystemClock.elapsedRealtime() - enterBackgroundTime < 200) {
				wasInBackground = false;
			}
			FileLog.d("switch to foreground");
			for (Listener listener : listeners) {
				try {
					listener.onBecameForeground();
				}
				catch (Exception e) {
					FileLog.e(e);
				}
			}
		}
	}

	public boolean isWasInBackground(boolean reset) {
		if (reset && (SystemClock.elapsedRealtime() - enterBackgroundTime < 200)) {
			wasInBackground = false;
		}
		return wasInBackground;
	}

	public void resetBackgroundVar() {
		wasInBackground = false;
	}

	@Override
	public void onActivityStopped(@NonNull Activity activity) {
		if (--refs == 0) {
			enterBackgroundTime = SystemClock.elapsedRealtime();
			wasInBackground = true;
			FileLog.d("switch to background");
			for (Listener listener : listeners) {
				try {
					listener.onBecameBackground();
				}
				catch (Exception e) {
					FileLog.e(e);
				}
			}
		}
	}

	@Override
	public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
	}

	@Override
	public void onActivityResumed(@NonNull Activity activity) {
	}

	@Override
	public void onActivityPaused(@NonNull Activity activity) {
	}

	@Override
	public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
	}

	@Override
	public void onActivityDestroyed(@NonNull Activity activity) {
	}
}
