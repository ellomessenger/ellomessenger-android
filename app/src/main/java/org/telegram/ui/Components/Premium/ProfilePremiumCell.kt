/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022.
 */
package org.telegram.ui.Components.Premium

import android.content.Context
import android.graphics.Canvas
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Cells.TextCell

class ProfilePremiumCell(context: Context) : TextCell(context) {
	var drawable = StarParticlesView.Drawable(6)

	init {
		drawable.size1 = 6
		drawable.size2 = 6
		drawable.size3 = 6
		drawable.useGradient = true
		drawable.speedScale = 3f
		drawable.minLifeTime = 600
		drawable.randLifeTime = 500
		drawable.startFromCenter = true
		drawable.type = StarParticlesView.Drawable.TYPE_SETTINGS
		drawable.init()
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		val cx = imageView.x + imageView.width / 2f
		val cy = imageView.paddingTop + imageView.y + imageView.height / 2f - AndroidUtilities.dp(3f)

		drawable.rect[cx - AndroidUtilities.dp(4f), cy - AndroidUtilities.dp(4f), cx + AndroidUtilities.dp(4f)] = cy + AndroidUtilities.dp(4f)

		if (changed) {
			drawable.resetPositions()
		}
	}

	override fun dispatchDraw(canvas: Canvas) {
		drawable.onDraw(canvas)
		invalidate()
		super.dispatchDraw(canvas)
	}
}
