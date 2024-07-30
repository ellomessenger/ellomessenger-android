/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import android.util.Log
import org.telegram.messenger.time.FastDateFormat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.system.exitProcess

object FileLog {
	private const val TAG = "ello"
	private var streamWriter: OutputStreamWriter? = null
	private val dateFormat = FastDateFormat.getInstance("dd_MM_yyyy_HH_mm_ss", Locale.US)
	private var logQueue: DispatchQueue? = null
	private var currentFile: File? = null
	private var networkFile: File? = null

	init {
		try {
			ApplicationLoader.applicationContext.getExternalFilesDir(null)?.let {
				val dir = File(it.absolutePath + "/logs")
				dir.mkdirs()
				currentFile = File(dir, dateFormat.format(System.currentTimeMillis()) + ".txt")
			}
		}
		catch (e: Exception) {
			e.printStackTrace()
		}

		try {
			logQueue = DispatchQueue("logQueue")

			currentFile?.createNewFile()

			val stream = FileOutputStream(currentFile)

			streamWriter = OutputStreamWriter(stream)
			streamWriter?.write("-----start log ${dateFormat.format(System.currentTimeMillis())}-----\n")
			streamWriter?.flush()
		}
		catch (e: Exception) {
			e.printStackTrace()
		}
	}

	@JvmStatic
	val networkLogPath: String
		get() {
			if (!BuildVars.logsEnabled) {
				return ""
			}

			try {
				val sdCard = ApplicationLoader.applicationContext.getExternalFilesDir(null) ?: return ""
				val dir = File(sdCard.absolutePath + "/logs")
				dir.mkdirs()
				networkFile = File(dir, dateFormat.format(System.currentTimeMillis()) + "_net.txt")
				return networkFile?.absolutePath ?: ""
			}
			catch (e: Throwable) {
				e.printStackTrace()
			}

			return ""
		}

	@JvmStatic
	fun e(message: String, exception: Throwable) {
		if (!BuildVars.logsEnabled) {
			return
		}

		Log.e(TAG, message, exception)

		val streamWriter = streamWriter ?: return

		logQueue?.postRunnable {
			try {
				streamWriter.write("${dateFormat.format(System.currentTimeMillis())} E/$TAG: $message")
				streamWriter.write(exception.toString())
				streamWriter.flush()
			}
			catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	@JvmStatic
	fun e(message: String) {
		if (!BuildVars.logsEnabled) {
			return
		}

		Log.e(TAG, message)

		val streamWriter = streamWriter ?: return

		logQueue?.postRunnable {
			try {
				streamWriter.write("${dateFormat.format(System.currentTimeMillis())} E/$TAG: $message")
				streamWriter.flush()
			}
			catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	@JvmStatic
	fun e(e: Throwable) {
		if (!BuildVars.logsEnabled) {
			return
		}

		e.printStackTrace()

		val streamWriter = streamWriter ?: return

		logQueue?.postRunnable {
			try {
				streamWriter.write("${dateFormat.format(System.currentTimeMillis())} E/$TAG: $e")

				val stack = e.stackTrace

				for (stackTraceElement in stack) {
					streamWriter.write("${dateFormat.format(System.currentTimeMillis())} E/$TAG: $stackTraceElement")
				}

				streamWriter.flush()
			}
			catch (e1: Exception) {
				e1.printStackTrace()
			}
		}
	}

	fun fatal(e: Throwable) {
		if (!BuildVars.logsEnabled) {
			return
		}

		e.printStackTrace()

		val streamWriter = streamWriter

		if (streamWriter != null) {
			logQueue?.postRunnable {
				try {
					streamWriter.write("${dateFormat.format(System.currentTimeMillis())} E/$TAG: $e")

					val stack = e.stackTrace

					for (stackTraceElement in stack) {
						streamWriter.write("${dateFormat.format(System.currentTimeMillis())} E/$TAG: $stackTraceElement")
					}

					streamWriter.flush()
				}
				catch (e1: Exception) {
					e1.printStackTrace()
				}

				if (BuildConfig.DEBUG_PRIVATE_VERSION) {
					exitProcess(2)
				}
			}
		}
		else {
			e.printStackTrace()

			if (BuildConfig.DEBUG_PRIVATE_VERSION) {
				exitProcess(2)
			}
		}
	}

	@JvmStatic
	fun d(message: String) {
		if (!BuildVars.logsEnabled) {
			return
		}

		Log.d(TAG, message)

		val streamWriter = streamWriter ?: return

		logQueue?.postRunnable {
			try {
				streamWriter.write("${dateFormat.format(System.currentTimeMillis())} D/$TAG: $message")
				streamWriter.flush()
			}
			catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	@JvmStatic
	fun w(message: String) {
		if (!BuildVars.logsEnabled) {
			return
		}

		Log.w(TAG, message)

		val streamWriter = streamWriter ?: return

		logQueue?.postRunnable {
			try {
				streamWriter.write("${dateFormat.format(System.currentTimeMillis())} W/$TAG: $message")
				streamWriter.flush()
			}
			catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	fun cleanupLogs() {
		val sdCard = ApplicationLoader.applicationContext.getExternalFilesDir(null) ?: return
		val dir = File(sdCard.absolutePath + "/logs")
		val files = dir.listFiles() ?: return

		for (file in files) {
			if (currentFile != null && file.absolutePath == currentFile?.absolutePath) {
				continue
			}

			if (networkFile != null && file.absolutePath == networkFile?.absolutePath) {
				continue
			}

			file.delete()
		}
	}
}
