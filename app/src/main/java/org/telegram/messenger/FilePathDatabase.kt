/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import android.os.Looper
import org.telegram.SQLite.SQLiteCursor
import org.telegram.SQLite.SQLiteDatabase
import org.telegram.SQLite.SQLiteException
import org.telegram.SQLite.SQLitePreparedStatement
import org.telegram.messenger.messageobject.MessageObject
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch

class FilePathDatabase(private val currentAccount: Int) {
	private val dispatchQueue = DispatchQueue("files_database_queue_$currentAccount")
	private var database: SQLiteDatabase? = null
	private var cacheFile: File? = null
	private var shmCacheFile: File? = null

	init {
		dispatchQueue.postRunnable {
			createDatabase(0, false)
		}
	}

	private fun createDatabase(tryCount: Int, fromBackup: Boolean) {
		var filesDir = ApplicationLoader.filesDirFixed

		if (currentAccount != 0) {
			filesDir = File(filesDir, "account$currentAccount/")
			filesDir.mkdirs()
		}

		cacheFile = File(filesDir, "$DATABASE_NAME.db")
		shmCacheFile = File(filesDir, "$DATABASE_NAME.db-shm")

		var createTable = false

		if (cacheFile?.exists() != true) {
			createTable = true
		}

		try {
			database = SQLiteDatabase(cacheFile!!.path)
			database?.executeFast("PRAGMA secure_delete = ON")?.stepThis()?.dispose()
			database?.executeFast("PRAGMA temp_store = MEMORY")?.stepThis()?.dispose()

			if (createTable) {
				database?.executeFast("CREATE TABLE paths(document_id INTEGER, dc_id INTEGER, type INTEGER, path TEXT, PRIMARY KEY(document_id, dc_id, type));")?.stepThis()?.dispose()
				database?.executeFast("CREATE INDEX IF NOT EXISTS path_in_paths ON paths(path);")?.stepThis()?.dispose()
				database?.executeFast("PRAGMA user_version = $LAST_DB_VERSION")?.stepThis()?.dispose()
			}
			else {
				val version = database!!.executeInt("PRAGMA user_version")!!

				if (BuildConfig.DEBUG) {
					FileLog.d("current files db version = $version")
				}

				if (version == 0) {
					throw Exception("malformed")
				}

				migrateDatabase(version)
			}

			if (!fromBackup) {
				createBackup()
			}

			FileLog.d("files db created from_backup= $fromBackup")
		}
		catch (e: Exception) {
			if (tryCount < 4) {
				if (!fromBackup && restoreBackup()) {
					createDatabase(tryCount + 1, true)
					return
				}
				else {
					cacheFile?.delete()
					shmCacheFile?.delete()
					createDatabase(tryCount + 1, false)
				}
			}

			if (BuildConfig.DEBUG) {
				FileLog.e(e)
			}
		}
	}

	@Throws(SQLiteException::class)
	private fun migrateDatabase(version: Int) {
		if (version == 1) {
			database?.executeFast("CREATE INDEX IF NOT EXISTS path_in_paths ON paths(path);")?.stepThis()?.dispose()
			database?.executeFast("PRAGMA user_version = " + 2)?.stepThis()?.dispose()
		}
	}

	private fun createBackup() {
		var filesDir = ApplicationLoader.filesDirFixed

		if (currentAccount != 0) {
			filesDir = File(filesDir, "account$currentAccount/")
			filesDir.mkdirs()
		}

		val backupCacheFile = File(filesDir, "$DATABASE_BACKUP_NAME.db")

		try {
			AndroidUtilities.copyFile(cacheFile, backupCacheFile)
			FileLog.d("file db backup created " + backupCacheFile.absolutePath)
		}
		catch (e: IOException) {
			FileLog.e(e)
		}
	}

	private fun restoreBackup(): Boolean {
		var filesDir = ApplicationLoader.filesDirFixed

		if (currentAccount != 0) {
			filesDir = File(filesDir, "account$currentAccount/")
			filesDir.mkdirs()
		}

		val backupCacheFile = File(filesDir, "$DATABASE_BACKUP_NAME.db")

		if (!backupCacheFile.exists()) {
			return false
		}

		try {
			return AndroidUtilities.copyFile(backupCacheFile, cacheFile)
		}
		catch (e: IOException) {
			FileLog.e(e)
		}

		return false
	}

	fun getPath(documentId: Long, dc: Int, type: Int, useQueue: Boolean): String? {
		return if (useQueue) {
			if (BuildConfig.DEBUG) {
				if (dispatchQueue.handler != null && Thread.currentThread() === dispatchQueue.handler.looper.thread) {
					throw RuntimeException("Error, lead to infinity loop")
				}
			}

			val syncLatch = CountDownLatch(1)
			var res: String? = null

			dispatchQueue.postRunnable {
				var cursor: SQLiteCursor? = null

				try {
					cursor = database?.queryFinalized("SELECT path FROM paths WHERE document_id = $documentId AND dc_id = $dc AND type = $type")

					if (cursor?.next() == true) {
						res = cursor?.stringValue(0)

						if (BuildConfig.DEBUG) {
							FileLog.d("get file path id=$documentId dc=$dc type=$type path=$res")
						}
					}
				}
				catch (e: SQLiteException) {
					FileLog.e(e)
				}
				finally {
					cursor?.dispose()
				}

				syncLatch.countDown()
			}

			runCatching {
				syncLatch.await()
			}

			res
		}
		else {
			var cursor: SQLiteCursor? = null
			var res: String? = null

			try {
				cursor = database?.queryFinalized("SELECT path FROM paths WHERE document_id = $documentId AND dc_id = $dc AND type = $type")

				if (cursor?.next() == true) {
					res = cursor?.stringValue(0)

					if (BuildConfig.DEBUG) {
						FileLog.d("get file path id=$documentId dc=$dc type=$type path=$res")
					}
				}
			}
			catch (e: SQLiteException) {
				FileLog.e(e)
			}
			finally {
				cursor?.dispose()
			}

			res
		}
	}

	fun putPath(id: Long, dc: Int, type: Int, path: String?) {
		dispatchQueue.postRunnable {
			if (BuildConfig.DEBUG) {
				FileLog.d("put file path id=$id dc=$dc type=$type path=$path")
			}

			var state: SQLitePreparedStatement? = null
			var deleteState: SQLitePreparedStatement? = null

			try {
				if (path != null) {
					deleteState = database!!.executeFast("DELETE FROM paths WHERE path = ?")
					deleteState.bindString(1, path)
					deleteState.step()

					state = database?.executeFast("REPLACE INTO paths VALUES(?, ?, ?, ?)")

					state?.requery()
					state?.bindLong(1, id)
					state?.bindInteger(2, dc)
					state?.bindInteger(3, type)
					state?.bindString(4, path)
					state?.step()
					state?.dispose()
				}
				else {
					database?.executeFast("DELETE FROM paths WHERE document_id = $id AND dc_id = $dc AND type = $type")?.stepThis()?.dispose()
				}
			}
			catch (e: SQLiteException) {
				FileLog.e(e)
			}
			finally {
				deleteState?.dispose()
				state?.dispose()
			}
		}
	}

	fun checkMediaExistence(messageObjects: List<MessageObject>?) {
		if (messageObjects.isNullOrEmpty()) {
			return
		}

		val arrayListFinal = ArrayList(messageObjects)
		val syncLatch = CountDownLatch(1)
		val time = System.currentTimeMillis()

		dispatchQueue.postRunnable {
			try {
				for (i in arrayListFinal.indices) {
					val messageObject = arrayListFinal[i]
					messageObject.checkMediaExistence(false)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			syncLatch.countDown()
		}

		try {
			syncLatch.await()
		}
		catch (e: InterruptedException) {
			FileLog.e(e)
		}

		FileLog.d("checkMediaExistence size=" + messageObjects.size + " time=" + (System.currentTimeMillis() - time))

		if (BuildConfig.DEBUG) {
			if (Thread.currentThread() === Looper.getMainLooper().thread) {
				FileLog.e(Exception("warning, not allowed in main thread"))
			}
		}
	}

	fun clear() {
		dispatchQueue.postRunnable {
			try {
				database?.executeFast("DELETE FROM paths WHERE 1")?.stepThis()?.dispose()
			}
			catch (e: SQLiteException) {
				FileLog.e(e)
			}
		}
	}

	fun hasAnotherRefOnFile(path: String): Boolean {
		val syncLatch = CountDownLatch(1)
		var res = false

		dispatchQueue.postRunnable {
			try {
				val cursor = database?.queryFinalized("SELECT document_id FROM paths WHERE path = '$path'")

				if (cursor?.next() == true) {
					res = true
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			syncLatch.countDown()
		}

		try {
			syncLatch.await()
		}
		catch (e: InterruptedException) {
			FileLog.e(e)
		}

		return res
	}

	data class PathData(val id: Long, val dc: Int, val type: Int)

	companion object {
		private const val LAST_DB_VERSION = 2
		private const val DATABASE_NAME = "file_to_path"
		private const val DATABASE_BACKUP_NAME = "file_to_path_backup"
	}
}
