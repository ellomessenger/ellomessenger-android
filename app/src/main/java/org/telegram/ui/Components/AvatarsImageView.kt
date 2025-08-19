/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.view.View
import org.telegram.tgnet.TLObject

open class AvatarsImageView(context: Context, inCall: Boolean) : View(context) {
	@JvmField
	val avatarsDrawable: AvatarsDrawable = AvatarsDrawable(this, inCall)

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		avatarsDrawable.width = measuredWidth
		avatarsDrawable.height = measuredHeight
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		avatarsDrawable.onAttachedToWindow()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		avatarsDrawable.onDraw(canvas)
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		avatarsDrawable.onDetachedFromWindow()
	}

	fun setStyle(style: Int) {
		avatarsDrawable.setStyle(style)
	}

	fun setDelegate(delegate: Runnable?) {
		avatarsDrawable.setDelegate(delegate)
	}

	fun setObject(a: Int, currentAccount: Int, `object`: TLObject?) {
		avatarsDrawable.setObject(a, currentAccount, `object`)
	}

	fun reset() {
		avatarsDrawable.reset()
	}

	fun setCount(usersCount: Int) {
		avatarsDrawable.setCount(usersCount)
	}

	fun commitTransition(animated: Boolean) {
		avatarsDrawable.commitTransition(animated)
	}

	fun updateAfterTransitionEnd() {
		avatarsDrawable.updateAfterTransitionEnd()
	}

	fun setCentered(centered: Boolean) {
		avatarsDrawable.setCentered(centered)
	}
}
