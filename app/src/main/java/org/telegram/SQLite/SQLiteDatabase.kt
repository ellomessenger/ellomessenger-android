/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.SQLite

import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog

class SQLiteDatabase(fileName: String?) {
	private var isOpen: Boolean
	private var inTransaction = false
	val sQLiteHandle: Long

	init {
		sQLiteHandle = opendb(fileName, ApplicationLoader.filesDirFixed.path)
		isOpen = true
	}

	@Throws(SQLiteException::class)
	fun tableExists(tableName: String?): Boolean {
		checkOpened()
		val s = "SELECT rowid FROM sqlite_master WHERE type='table' AND name=?;"
		return executeInt(s, tableName) != null
	}

	@Throws(SQLiteException::class)
	fun executeFast(sql: String?): SQLitePreparedStatement {
		return SQLitePreparedStatement(this, sql)
	}

	@Throws(SQLiteException::class)
	fun executeInt(sql: String?, vararg args: Any?): Int? {
		checkOpened()

		val cursor = queryFinalized(sql, *args)

		return try {
			if (!cursor.next()) {
				null
			}
			else {
				cursor.intValue(0)
			}
		}
		finally {
			cursor.dispose()
		}
	}

	@Throws(SQLiteException::class)
	fun explainQuery(sql: String, vararg args: Any?) {
		checkOpened()

		val cursor = SQLitePreparedStatement(this, "EXPLAIN QUERY PLAN $sql").query(*args)

		while (cursor.next()) {
			val count = cursor.columnCount
			val builder = StringBuilder()

			for (a in 0 until count) {
				builder.append(cursor.stringValue(a)).append(", ")
			}

			FileLog.d("EXPLAIN QUERY PLAN $builder")
		}

		cursor.dispose()
	}

	@Throws(SQLiteException::class)
	fun queryFinalized(sql: String?, vararg args: Any?): SQLiteCursor {
		checkOpened()
		return SQLitePreparedStatement(this, sql).query(*args)
	}

	fun close() {
		if (isOpen) {
			try {
				commitTransaction()
				closedb(sQLiteHandle)
			}
			catch (e: SQLiteException) {
				FileLog.e(e.message ?: "???", e)
			}

			isOpen = false
		}
	}

	@Throws(SQLiteException::class)
	fun checkOpened() {
		if (!isOpen) {
			throw SQLiteException("Database closed")
		}
	}

	@Throws(Throwable::class)
	protected fun finalize() {
		close()
	}

	@Throws(SQLiteException::class)
	fun beginTransaction() {
		if (inTransaction) {
			throw SQLiteException("database already in transaction")
		}

		inTransaction = true

		beginTransaction(sQLiteHandle)
	}

	fun commitTransaction() {
		if (!inTransaction) {
			return
		}

		inTransaction = false

		commitTransaction(sQLiteHandle)
	}

	@Throws(SQLiteException::class)
	external fun opendb(fileName: String?, tempDir: String?): Long

	@Throws(SQLiteException::class)
	external fun closedb(sqliteHandle: Long)

	external fun beginTransaction(sqliteHandle: Long)
	external fun commitTransaction(sqliteHandle: Long)
}
