/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.ActionBar

import android.content.Context

class DarkAlertDialog(context: Context, progressStyle: Int) : AlertDialog(context, progressStyle) {
	class Builder : AlertDialog.Builder {
		constructor(context: Context) : super(DarkAlertDialog(context, 0))
		constructor(context: Context, progressViewStyle: Int) : super(DarkAlertDialog(context, progressViewStyle))
	}
}
