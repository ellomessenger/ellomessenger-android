/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.SQLite

import org.telegram.messenger.FileLog
import org.telegram.tgnet.NativeByteBuffer

class SQLiteCursor(private val preparedStatement: SQLitePreparedStatement) {
	private var inRow = false

	@Throws(SQLiteException::class)
	fun isNull(columnIndex: Int): Boolean {
		checkRow()
		return columnIsNull(preparedStatement.statementHandle, columnIndex) == 1
	}

	@Throws(SQLiteException::class)
	fun intValue(columnIndex: Int): Int {
		checkRow()
		return columnIntValue(preparedStatement.statementHandle, columnIndex)
	}

	@Throws(SQLiteException::class)
	fun doubleValue(columnIndex: Int): Double {
		checkRow()
		return columnDoubleValue(preparedStatement.statementHandle, columnIndex)
	}

	@Throws(SQLiteException::class)
	fun longValue(columnIndex: Int): Long {
		checkRow()
		return columnLongValue(preparedStatement.statementHandle, columnIndex)
	}

	@Throws(SQLiteException::class)
	fun stringValue(columnIndex: Int): String {
		checkRow()
		return columnStringValue(preparedStatement.statementHandle, columnIndex)
	}

	@Throws(SQLiteException::class)
	fun byteArrayValue(columnIndex: Int): ByteArray {
		checkRow()
		return columnByteArrayValue(preparedStatement.statementHandle, columnIndex)
	}

	@Throws(SQLiteException::class)
	fun byteBufferValue(columnIndex: Int): NativeByteBuffer? {
		checkRow()

		val ptr = columnByteBufferValue(preparedStatement.statementHandle, columnIndex)

		return if (ptr != 0L) {
			NativeByteBuffer.wrap(ptr)
		}
		else {
			null
		}
	}

	@Throws(SQLiteException::class)
	fun getTypeOf(columnIndex: Int): Int {
		checkRow()
		return columnType(preparedStatement.statementHandle, columnIndex)
	}

	@Throws(SQLiteException::class)
	operator fun next(): Boolean {
		var res = preparedStatement.step(preparedStatement.statementHandle)

		if (res == -1) {
			var repeatCount = 6

			while (repeatCount-- != 0) {
				try {
					FileLog.d("sqlite busy, waiting…")

					Thread.sleep(500)

					res = preparedStatement.step()

					if (res == 0) {
						break
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			if (res == -1) {
				throw SQLiteException("sqlite busy")
			}
		}

		inRow = res == 0

		return inRow
	}

	val statementHandle: Long
		get() = preparedStatement.statementHandle

	val columnCount: Int
		get() = columnCount(preparedStatement.statementHandle)

	fun dispose() {
		preparedStatement.dispose()
	}

	@Throws(SQLiteException::class)
	fun checkRow() {
		if (!inRow) {
			throw SQLiteException("You must call next before")
		}
	}

	private external fun columnType(statementHandle: Long, columnIndex: Int): Int
	private external fun columnCount(statementHandle: Long): Int
	private external fun columnIsNull(statementHandle: Long, columnIndex: Int): Int
	private external fun columnIntValue(statementHandle: Long, columnIndex: Int): Int
	private external fun columnLongValue(statementHandle: Long, columnIndex: Int): Long
	private external fun columnDoubleValue(statementHandle: Long, columnIndex: Int): Double
	private external fun columnStringValue(statementHandle: Long, columnIndex: Int): String
	private external fun columnByteArrayValue(statementHandle: Long, columnIndex: Int): ByteArray
	private external fun columnByteBufferValue(statementHandle: Long, columnIndex: Int): Long
}
