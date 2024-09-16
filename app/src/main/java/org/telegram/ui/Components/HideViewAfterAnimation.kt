/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import org.telegram.messenger.utils.gone

class HideViewAfterAnimation(private val view: View) : AnimatorListenerAdapter() {
	override fun onAnimationEnd(animation: Animator) {
		super.onAnimationEnd(animation)
		view.gone()
	}
}
