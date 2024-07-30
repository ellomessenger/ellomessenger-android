/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

class FileLoaderPriorityQueue internal constructor(var name: String, private val maxActiveOperationsCount: Int) {
	private val allOperations = mutableListOf<FileLoadOperation>()

	fun add(operation: FileLoadOperation?) {
		if (operation == null) {
			return
		}

		var index = -1

		allOperations.remove(operation)

		for (i in allOperations.indices) {
			if (operation.priority > allOperations[i].priority) {
				index = i
				break
			}
		}

		if (index >= 0) {
			allOperations.add(index, operation)
		}
		else {
			allOperations.add(operation)
		}
	}

	fun cancel(operation: FileLoadOperation?) {
		if (operation == null) {
			return
		}

		allOperations.remove(operation)

		operation.cancel()
	}

	fun checkLoadingOperations() {
		var activeCount = 0
		var lastPriority = 0
		var pauseAllNextOperations = false

		for (i in allOperations.indices) {
			val operation = allOperations[i]

			if (i > 0 && !pauseAllNextOperations) {
				if (lastPriority > PRIORITY_VALUE_LOW && operation.priority == PRIORITY_VALUE_LOW) {
					pauseAllNextOperations = true
				}
			}

			if (!pauseAllNextOperations && i < maxActiveOperationsCount) {
				operation.start()
				activeCount++
			}
			else {
				if (operation.wasStarted()) {
					operation.pause()
				}
			}

			lastPriority = operation.priority
		}
	}

	fun remove(operation: FileLoadOperation?) {
		if (operation == null) {
			return
		}

		allOperations.remove(operation)
	}

	companion object {
		// private const val PRIORITY_VALUE_MAX = 1 shl 20
		// private const val PRIORITY_VALUE_NORMAL = 1 shl 16
		private const val PRIORITY_VALUE_LOW = 0
	}
}
