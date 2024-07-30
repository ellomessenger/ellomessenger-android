/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import org.telegram.tgnet.TLRPC
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.Volatile

class AnimatedFileDrawableStream(@JvmField val document: TLRPC.Document?, @JvmField val location: ImageLocation?, private val parentObject: Any?, val currentAccount: Int, val isPreview: Boolean) : FileLoadOperationStream {
	private val loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, 0, isPreview)
	private var countDownLatch: CountDownLatch? = null

	@Volatile
	private var canceled = false

	private val sync = Any()
	private var lastOffset: Long = 0

	var isWaitingForLoad = false
		private set

	@Suppress("MemberVisibilityCanBePrivate") // this variable is used from JNI
	var isFinishedLoadingFile = false
		private set

	@Suppress("MemberVisibilityCanBePrivate") // this variable is used from JNI
	var finishedFilePath: String? = null
		private set

	fun read(offset: Int, readLength: Int): Int {
		synchronized(sync) {
			if (canceled) {
				return 0
			}
		}

		return if (readLength == 0) {
			0
		}
		else {
			var availableLength = 0L

			try {
				while (availableLength == 0L) {
					val result = loadOperation?.getDownloadedLengthFromOffset(offset.toLong(), readLength.toLong())

					availableLength = result?.firstOrNull() ?: 0L

					if (!isFinishedLoadingFile && result?.get(1) != 0L) {
						isFinishedLoadingFile = true
						finishedFilePath = loadOperation?.cacheFileFinal?.absolutePath
					}

					if (availableLength == 0L) {
						if (loadOperation?.isPaused == true || lastOffset != offset.toLong() || isPreview) {
							FileLoader.getInstance(currentAccount).loadStreamFile(this, document, location, parentObject, offset.toLong(), isPreview)
						}

						synchronized(sync) {
							if (canceled) {
								FileLoader.getInstance(currentAccount).cancelLoadFile(document)
								return 0
							}

							countDownLatch = CountDownLatch(1)
						}

						if (!isPreview) {
							FileLoader.getInstance(currentAccount).setLoadingVideo(document, player = false, schedule = true)
						}

						isWaitingForLoad = true

						countDownLatch?.await()

						isWaitingForLoad = false
					}
				}

				lastOffset = offset + availableLength
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			availableLength.toInt()
		}
	}

	@JvmOverloads
	fun cancel(removeLoading: Boolean = true) {
		synchronized(sync) {
			if (countDownLatch != null) {
				countDownLatch?.countDown()

				if (removeLoading && !canceled && !isPreview) {
					FileLoader.getInstance(currentAccount).removeLoadingVideo(document, player = false, schedule = true)
				}
			}

			canceled = true
		}
	}

	fun reset() {
		synchronized(sync) { canceled = false }
	}

	fun getParentObject(): Any? {
		return document
	}

	override fun newDataAvailable() {
		countDownLatch?.countDown()
	}
}
