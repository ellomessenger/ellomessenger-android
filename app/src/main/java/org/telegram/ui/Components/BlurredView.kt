/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.view.View

open class BlurredView(context: Context, parentView: View) : View(context) {
	val drawable = BlurBehindDrawable(parentView, this, 1)

	init {
		drawable.setAnimateAlpha(false)
		drawable.show(true)
	}

	override fun onDraw(canvas: Canvas) {
		drawable.draw(canvas)
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		drawable.checkSizes()
	}

	fun update() {
		drawable.invalidate()
	}

	fun fullyDrawing(): Boolean {
		return drawable.isFullyDrawing && visibility == VISIBLE
	}
}
