/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui.Components.sharedmedia

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.BlurredRecyclerView
import org.telegram.ui.Components.ClippingImageView
import org.telegram.ui.Components.ExtendedGridLayoutManager
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.RecyclerAnimationScrollHelper
import org.telegram.ui.Components.StickerEmptyView

open class MediaPage(context: Context) : FrameLayout(context) {
	var lastCheckScrollTime: Long = 0
	var fastScrollEnabled = false
	var fastScrollAnimator: ObjectAnimator? = null
	var fastScrollHintView: SharedMediaFastScrollTooltip? = null
	var fastScrollHideHintRunnable: Runnable? = null
	var fastScrollHinWasShown = false
	var highlightMessageId = 0
	var highlightAnimation = false
	var highlightProgress = 0f
	var listView: BlurredRecyclerView? = null
	var animationSupportingListView: BlurredRecyclerView? = null
	var animationSupportingLayoutManager: GridLayoutManager? = null
	var progressView: FlickerLoadingView? = null
	var emptyView: StickerEmptyView? = null
	var layoutManager: ExtendedGridLayoutManager? = null
	var animatingImageView: ClippingImageView? = null
	var scrollHelper: RecyclerAnimationScrollHelper? = null
	var selectedType = 0

	override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
		return if (child === animationSupportingListView) {
			true
		}
		else {
			super.drawChild(canvas, child, drawingTime)
		}
	}

	override fun dispatchDraw(canvas: Canvas) {
		super.dispatchDraw(canvas)

		if (fastScrollHintView?.visibility == VISIBLE) {
			val fastScroll = listView?.fastScroll

			if (fastScroll != null) {
				val y = (fastScroll.scrollBarY + AndroidUtilities.dp(36f)).toFloat()
				val x = (measuredWidth - fastScrollHintView!!.measuredWidth - AndroidUtilities.dp(16f)).toFloat()
				fastScrollHintView?.pivotX = fastScrollHintView!!.measuredWidth.toFloat()
				fastScrollHintView?.pivotY = 0f
				fastScrollHintView?.translationX = x
				fastScrollHintView?.translationY = y

				if (fastScroll.getProgress() > 0.85f) {
					SharedMediaLayout.showFastScrollHint(this, null, false)
				}
			}
		}
	}
}
