/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui.Components.sharedmedia

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper

class EmptyStubView(context: Context) : LinearLayout(context) {
	private var ignoreRequestLayout = false
	val emptyTextView = TextView(context)
	val emptyImageView = ImageView(context)

	init {
		orientation = VERTICAL
		gravity = Gravity.CENTER

		addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		emptyTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
		emptyTextView.gravity = Gravity.CENTER
		emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
		emptyTextView.setPadding(AndroidUtilities.dp(40f), 0, AndroidUtilities.dp(40f), AndroidUtilities.dp(128f))

		addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 24, 0, 0))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val manager = ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
		val rotation = manager.defaultDisplay.rotation

		ignoreRequestLayout = true

		if (AndroidUtilities.isTablet()) {
			emptyTextView.setPadding(AndroidUtilities.dp(40f), 0, AndroidUtilities.dp(40f), AndroidUtilities.dp(128f))
		}
		else {
			if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
				emptyTextView.setPadding(AndroidUtilities.dp(40f), 0, AndroidUtilities.dp(40f), 0)
			}
			else {
				emptyTextView.setPadding(AndroidUtilities.dp(40f), 0, AndroidUtilities.dp(40f), AndroidUtilities.dp(128f))
			}
		}

		ignoreRequestLayout = false

		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
	}

	override fun requestLayout() {
		if (ignoreRequestLayout) {
			return
		}

		super.requestLayout()
	}
}
