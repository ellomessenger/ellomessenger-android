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
import org.telegram.ui.Components.Bulletin.SimpleLayout

@SuppressLint("ViewConstructor")
class ReorderingBulletinLayout(context: Context, text: String?) : SimpleLayout(context) {
	private val hintDrawable = ReorderingHintDrawable()

	init {
		textView.text = text
		textView.translationY = -1f
		imageView.setImageDrawable(hintDrawable)
	}

	override fun onEnterTransitionEnd() {
		super.onEnterTransitionEnd()
		hintDrawable.startAnimation()
	}

	override fun onExitTransitionEnd() {
		super.onExitTransitionEnd()
		hintDrawable.resetAnimation()
	}
}
