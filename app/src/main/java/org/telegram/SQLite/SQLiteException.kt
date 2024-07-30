/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.SQLite

class SQLiteException : Exception {
	val errorCode: Int

	constructor(errcode: Int, msg: String?) : super(msg) {
		errorCode = errcode
	}

	constructor(msg: String?) : this(0, msg)

	constructor() {
		errorCode = 0
	}

	companion object {
		private const val serialVersionUID = -2398298479089615621L
	}
}
