/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.viewpager.widget.ViewPager
import org.telegram.messenger.AndroidUtilities

class BottomPagesView(context: Context, private val viewPager: ViewPager, private val pagesCount: Int) : View(context) {
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val rect = RectF()
	private var color = 0
	private var currentPage = 0
	private var progress = 0f
	private var scrollPosition = 0
	private var selectedColor = 0

	fun setPageOffset(position: Int, offset: Float) {
		progress = offset
		scrollPosition = position
		invalidate()
	}

	fun setCurrentPage(page: Int) {
		currentPage = page
		invalidate()
	}

	fun setColor(color: Int, selectedColor: Int) {
		this.color = color
		this.selectedColor = selectedColor
	}

	override fun onDraw(canvas: Canvas) {
		if (color != 0) {
			paint.color = color and 0x00ffffff or -0x4c000000
		}
		else {
			paint.color = if (AndroidUtilities.isDarkTheme()) -0xaaaaab else -0x444445
		}

		var x: Int

		currentPage = viewPager.currentItem

		for (a in 0 until pagesCount) {
			if (a == currentPage) {
				continue
			}

			x = a * AndroidUtilities.dp(11f)

			rect.set(x.toFloat(), 0f, (x + AndroidUtilities.dp(5f)).toFloat(), AndroidUtilities.dp(5f).toFloat())

			canvas.drawRoundRect(rect, AndroidUtilities.dp(2.5f).toFloat(), AndroidUtilities.dp(2.5f).toFloat(), paint)
		}

		if (selectedColor != 0) {
			paint.color = selectedColor
		}
		else {
			paint.color = -0xd35a20
		}

		x = currentPage * AndroidUtilities.dp(11f)

		if (progress != 0f) {
			if (scrollPosition >= currentPage) {
				rect.set(x.toFloat(), 0f, x + AndroidUtilities.dp(5f) + AndroidUtilities.dp(11f) * progress, AndroidUtilities.dp(5f).toFloat())
			}
			else {
				rect.set(x - AndroidUtilities.dp(11f) * (1.0f - progress), 0f, (x + AndroidUtilities.dp(5f)).toFloat(), AndroidUtilities.dp(5f).toFloat())
			}
		}
		else {
			rect.set(x.toFloat(), 0f, (x + AndroidUtilities.dp(5f)).toFloat(), AndroidUtilities.dp(5f).toFloat())
		}

		canvas.drawRoundRect(rect, AndroidUtilities.dp(2.5f).toFloat(), AndroidUtilities.dp(2.5f).toFloat(), paint)
	}
}
