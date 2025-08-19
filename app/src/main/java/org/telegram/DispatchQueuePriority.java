/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DispatchQueuePriority {
	ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new PriorityBlockingQueue<>(10, (o1, o2) -> {
		int priority1 = 1;
		int priority2 = 1;
		if (o1 instanceof PriorityRunnable) {
			priority1 = ((PriorityRunnable)o1).priority;
		}
		if (o2 instanceof PriorityRunnable) {
			priority2 = ((PriorityRunnable)o2).priority;
		}
		return priority2 - priority1;
	}));

	public DispatchQueuePriority(String threadName) {

	}

	public static Runnable wrap(Runnable runnable, int priority) {
		if (priority == 1) {
			return runnable;
		}
		else {
			return new PriorityRunnable(priority, runnable);
		}
	}

	public void postRunnable(Runnable runnable) {
		threadPoolExecutor.execute(runnable);
	}

	public Runnable postRunnable(Runnable runnable, int priority) {
		if (priority == 1) {
			postRunnable(runnable);
			return runnable;
		}
		else {
			PriorityRunnable priorityRunnable = new PriorityRunnable(priority, runnable);
			threadPoolExecutor.execute(priorityRunnable);
			return priorityRunnable;
		}
	}

	public void cancelRunnable(Runnable runnable) {
		if (runnable == null) {
			return;
		}
		threadPoolExecutor.remove(runnable);
	}

	private record PriorityRunnable(int priority, Runnable runnable) implements Runnable {
		@Override
		public void run() {
			runnable.run();
		}
	}
}
