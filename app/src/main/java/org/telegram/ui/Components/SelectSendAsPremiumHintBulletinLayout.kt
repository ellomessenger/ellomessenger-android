/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.Components.Bulletin.MultiLineLayout
import org.telegram.ui.Components.Bulletin.UndoButton

@SuppressLint("ViewConstructor")
class SelectSendAsPremiumHintBulletinLayout(context: Context, callback: Runnable?) : MultiLineLayout(context) {
	init {
		imageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.msg_premium_prolfilestar))
		imageView.setColorFilter(PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN))

		textView.text = AndroidUtilities.replaceTags(context.getString(R.string.SelectSendAsPeerPremiumHint))

		val button = UndoButton(context, true)
		button.setText(context.getString(R.string.SelectSendAsPeerPremiumOpen))
		button.setUndoAction(callback)

		setButton(button)
	}
}
