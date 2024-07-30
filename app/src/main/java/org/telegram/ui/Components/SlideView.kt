/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout

abstract class SlideView(context: Context) : LinearLayout(context) {
	open var code: String? = null
	open val headerName: String? = ""
	open fun setParams(params: Bundle?, restore: Boolean) {}
	open fun onShow() {}
	open fun onHide() {}
	open fun onDestroyActivity() {}
	open fun onNextPressed(code: String?) {}
	open fun onCancelPressed() {}
	open fun onBackPressed(force: Boolean): Boolean = true
	open fun needBackButton(): Boolean = false
}
