/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RLottieImageView

class DatabaseMigrationHint(context: Context) : FrameLayout(context) {
	var container: LinearLayout
	var stickerView: RLottieImageView
	var title: TextView
	private var description1: TextView
	private var description2: TextView

	init {
		container = LinearLayout(context)
		container.orientation = LinearLayout.VERTICAL

		stickerView = RLottieImageView(context)
		stickerView.setAnimation(R.raw.db_migration_placeholder, 150, 150)
		stickerView.animatedDrawable!!.setAutoRepeat(1)
		stickerView.playAnimation()

		container.addView(stickerView, createLinear(150, 150, Gravity.CENTER_HORIZONTAL))

		title = TextView(context)
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
		title.text = context.getString(R.string.OptimizingTelegram)
		title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		title.gravity = Gravity.CENTER_HORIZONTAL

		container.addView(title, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 50, 32, 50, 0))

		description1 = TextView(context)
		description1.setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1.0f)
		description1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		description1.text = context.getString(R.string.OptimizingTelegramDescription1)
		description1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		description1.gravity = Gravity.CENTER_HORIZONTAL

		container.addView(description1, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 36, 20, 36, 0))

		description2 = TextView(context)
		description2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		description2.text = context.getString(R.string.OptimizingTelegramDescription2)
		description2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		description2.gravity = Gravity.CENTER_HORIZONTAL

		container.addView(description2, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 36, 24, 36, 0))

		addView(container, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL))

		setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

		setOnTouchListener { _, _ -> true }
	}
}
