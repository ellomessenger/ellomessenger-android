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
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.AppIconsSelectorCell
import org.telegram.ui.Components.Bulletin.ButtonLayout
import org.telegram.ui.LauncherIconController.LauncherIcon

@SuppressLint("ViewConstructor")
class AppIconBulletinLayout(context: Context, icon: LauncherIcon) : ButtonLayout(context) {
	private val imageView = AppIconsSelectorCell.AdaptiveIconImageView(context)
	private val textView = TextView(context)

	init {
		addView(imageView, LayoutHelper.createFrameRelatively(30f, 30f, Gravity.START or Gravity.CENTER_VERTICAL, 12f, 8f, 12f, 8f))

		textView.setGravity(Gravity.START)
		textView.setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))
		textView.setTextColor(context.getColor(R.color.white))
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		textView.setTypeface(Theme.TYPEFACE_DEFAULT)

		addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 56f, 0f, 16f, 0f))

		imageView.setImageDrawable(ContextCompat.getDrawable(context, icon.background))
		imageView.setOuterPadding(AndroidUtilities.dp(8f))
		imageView.setBackgroundOuterPadding(AndroidUtilities.dp(24f))
		imageView.setForeground(icon.foreground)

		textView.text = AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AppIconChangedTo, context.getString(icon.title)))
	}
}
