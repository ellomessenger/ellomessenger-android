/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.telegram.messenger.support.JobIntentService;

import java.util.concurrent.CountDownLatch;

public class KeepAliveJob extends JobIntentService {
	private static volatile CountDownLatch countDownLatch;
	private static volatile boolean startingJob;
	private static final Object sync = new Object();
	private static final String intentAction = "ello.keepalive.job";

	public static void startJob() {
		Utilities.globalQueue.postRunnable(() -> {
			if (startingJob || countDownLatch != null) {
				return;
			}

			try {
				if (BuildConfig.DEBUG) {
					FileLog.d("starting keep-alive job");
				}

				synchronized (sync) {
					startingJob = true;
				}

				enqueueWork(ApplicationLoader.applicationContext, KeepAliveJob.class, 1000, new Intent(intentAction));
			}
			catch (Exception e) {
				// ignored
			}
		});
	}

	private static void finishJobInternal() {
		synchronized (sync) {
			if (countDownLatch != null) {
				if (BuildConfig.DEBUG) {
					FileLog.d("finish keep-alive job");
				}

				countDownLatch.countDown();
			}

			if (startingJob) {
				if (BuildConfig.DEBUG) {
					FileLog.d("finish queued keep-alive job");
				}

				startingJob = false;
			}
		}
	}

	public static void finishJob() {
		Utilities.globalQueue.postRunnable(KeepAliveJob::finishJobInternal);
	}

	private static final Runnable finishJobByTimeoutRunnable = KeepAliveJob::finishJobInternal;

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		synchronized (sync) {
			if (!startingJob) {
				return;
			}

			countDownLatch = new CountDownLatch(1);
		}

		if (BuildConfig.DEBUG) {
			FileLog.d("started keep-alive job");
		}

		Utilities.globalQueue.postRunnable(finishJobByTimeoutRunnable, 60 * 1000);

		try {
			countDownLatch.await();
		}
		catch (Throwable e) {
			// ignored
		}

		Utilities.globalQueue.cancelRunnable(finishJobByTimeoutRunnable);

		synchronized (sync) {
			countDownLatch = null;
		}

		if (BuildConfig.DEBUG) {
			FileLog.d("ended keep-alive job");
		}
	}
}
