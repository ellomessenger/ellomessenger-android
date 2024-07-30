/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.SQLite

import android.os.SystemClock
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.FileLog
import org.telegram.tgnet.NativeByteBuffer
import java.nio.ByteBuffer

class SQLitePreparedStatement(db: SQLiteDatabase, sql: String?) {
	private var isFinalized = false
	private var startTime: Long = 0
	private var query: String? = null
	val statementHandle: Long

	init {
		statementHandle = prepare(db.sQLiteHandle, sql)

		if (BuildConfig.DEBUG) {
			query = sql
			startTime = SystemClock.elapsedRealtime()
		}
	}

	@Throws(SQLiteException::class)
	fun query(vararg args: Any?): SQLiteCursor {
		checkFinalized()
		reset(statementHandle)

		var i = 1

		for (obj in args) {
			when (obj) {
				null -> {
					bindNull(statementHandle, i)
				}

				is Int -> {
					bindInt(statementHandle, i, obj)
				}

				is Double -> {
					bindDouble(statementHandle, i, obj)
				}

				is String -> {
					bindString(statementHandle, i, obj)
				}

				is Long -> {
					bindLong(statementHandle, i, obj)
				}

				else -> {
					throw IllegalArgumentException()
				}
			}

			i++
		}

		return SQLiteCursor(this)
	}

	@Throws(SQLiteException::class)
	fun step(): Int {
		return step(statementHandle)
	}

	@Throws(SQLiteException::class)
	fun stepThis(): SQLitePreparedStatement {
		step(statementHandle)
		return this
	}

	@Throws(SQLiteException::class)
	fun requery() {
		checkFinalized()
		reset(statementHandle)
	}

	fun dispose() {
		finalizeQuery()
	}

	@Throws(SQLiteException::class)
	fun checkFinalized() {
		if (isFinalized) {
			throw SQLiteException("Prepared query finalized")
		}
	}

	private fun finalizeQuery() {
		if (isFinalized) {
			return
		}

		if (BuildConfig.DEBUG) {
			val diff = SystemClock.elapsedRealtime() - startTime

			if (diff > 500) {
				FileLog.d("sqlite query " + query + " took " + diff + "ms")
			}
		}

		try {
			isFinalized = true
			finalize(statementHandle)
		}
		catch (e: SQLiteException) {
			FileLog.e(e.message ?: "???", e)
		}
	}

	@Throws(SQLiteException::class)
	fun bindInteger(index: Int, value: Int) {
		bindInt(statementHandle, index, value)
	}

	@Throws(SQLiteException::class)
	fun bindDouble(index: Int, value: Double) {
		bindDouble(statementHandle, index, value)
	}

	@Throws(SQLiteException::class)
	fun bindByteBuffer(index: Int, value: ByteBuffer) {
		bindByteBuffer(statementHandle, index, value, value.limit())
	}

	@Throws(SQLiteException::class)
	fun bindByteBuffer(index: Int, value: NativeByteBuffer) {
		bindByteBuffer(statementHandle, index, value.buffer, value.limit())
	}

	@Throws(SQLiteException::class)
	fun bindString(index: Int, value: String?) {
		bindString(statementHandle, index, value)
	}

	@Throws(SQLiteException::class)
	fun bindLong(index: Int, value: Long) {
		bindLong(statementHandle, index, value)
	}

	@Throws(SQLiteException::class)
	fun bindNull(index: Int) {
		bindNull(statementHandle, index)
	}

	@Throws(SQLiteException::class)
	external fun bindByteBuffer(statementHandle: Long, index: Int, value: ByteBuffer?, length: Int)

	@Throws(SQLiteException::class)
	external fun bindString(statementHandle: Long, index: Int, value: String?)

	@Throws(SQLiteException::class)
	external fun bindInt(statementHandle: Long, index: Int, value: Int)

	@Throws(SQLiteException::class)
	external fun bindLong(statementHandle: Long, index: Int, value: Long)

	@Throws(SQLiteException::class)
	external fun bindDouble(statementHandle: Long, index: Int, value: Double)

	@Throws(SQLiteException::class)
	external fun bindNull(statementHandle: Long, index: Int)

	@Throws(SQLiteException::class)
	external fun reset(statementHandle: Long)

	@Throws(SQLiteException::class)
	external fun prepare(sqliteHandle: Long, sql: String?): Long

	@Throws(SQLiteException::class)
	external fun finalize(statementHandle: Long)

	@Throws(SQLiteException::class)
	external fun step(statementHandle: Long): Int
}
