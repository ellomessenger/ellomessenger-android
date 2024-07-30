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
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.Components.Reactions.ReactionsContainerLayout
import kotlin.math.max

open class ChatScrimPopupContainerLayout(context: Context) : LinearLayout(context) {
	private var reactionsLayout: ReactionsContainerLayout? = null
	private var popupWindowLayout: ActionBarPopupWindowLayout? = null
	private var bottomView: View? = null
	private var maxHeight = 0
	private var popupLayoutLeftOffset = 0f
	private var progressToSwipeBack = 0f
	private var bottomViewYOffset = 0f
	private var expandSize = 0f

	init {
		orientation = VERTICAL
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		@Suppress("NAME_SHADOWING") var widthMeasureSpec = widthMeasureSpec
		@Suppress("NAME_SHADOWING") var heightMeasureSpec = heightMeasureSpec

		if (maxHeight != 0) {
			heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
		}

		val reactionsLayout = reactionsLayout
		val popupWindowLayout = popupWindowLayout

		if (reactionsLayout != null && popupWindowLayout != null) {
			reactionsLayout.layoutParams.width = LayoutHelper.WRAP_CONTENT
			(reactionsLayout.layoutParams as LayoutParams).rightMargin = 0

			popupLayoutLeftOffset = 0f

			super.onMeasure(widthMeasureSpec, heightMeasureSpec)

			var maxWidth = reactionsLayout.measuredWidth

			if (popupWindowLayout.swipeBack != null && popupWindowLayout.swipeBack!!.measuredWidth > maxWidth) {
				maxWidth = popupWindowLayout.swipeBack!!.measuredWidth
			}

			if (popupWindowLayout.measuredWidth > maxWidth) {
				maxWidth = popupWindowLayout.measuredWidth
			}

			if (reactionsLayout.showCustomEmojiReaction()) {
				widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY)
			}

			val reactionsLayoutTotalWidth = reactionsLayout.totalWidth
			val menuContainer = if (popupWindowLayout.swipeBack != null) popupWindowLayout.swipeBack!!.getChildAt(0) else popupWindowLayout.getChildAt(0)
			var maxReactionsLayoutWidth = menuContainer.measuredWidth + AndroidUtilities.dp(16f) + AndroidUtilities.dp(16f) + AndroidUtilities.dp(36f)

			if (maxReactionsLayoutWidth > maxWidth) {
				maxReactionsLayoutWidth = maxWidth
			}

			reactionsLayout.bigCircleOffset = AndroidUtilities.dp(36f)

			if (reactionsLayout.showCustomEmojiReaction()) {
				reactionsLayout.layoutParams.width = reactionsLayoutTotalWidth
				reactionsLayout.bigCircleOffset = max(reactionsLayoutTotalWidth - menuContainer.measuredWidth - AndroidUtilities.dp(36f), AndroidUtilities.dp(36f))
			}
			else if (reactionsLayoutTotalWidth > maxReactionsLayoutWidth) {
				val maxFullCount = (maxReactionsLayoutWidth - AndroidUtilities.dp(16f)) / AndroidUtilities.dp(36f) + 1
				var newWidth = maxFullCount * AndroidUtilities.dp(36f) + AndroidUtilities.dp(16f) - AndroidUtilities.dp(8f)

				if (newWidth > reactionsLayoutTotalWidth || maxFullCount == reactionsLayout.itemsCount) {
					newWidth = reactionsLayoutTotalWidth
				}

				reactionsLayout.layoutParams.width = newWidth
			}
			else {
				reactionsLayout.layoutParams.width = LayoutHelper.WRAP_CONTENT
			}

			var widthDiff = 0

			if (reactionsLayout.measuredWidth != maxWidth || !reactionsLayout.showCustomEmojiReaction()) {
				if (popupWindowLayout.swipeBack != null) {
					widthDiff = popupWindowLayout.swipeBack!!.measuredWidth - popupWindowLayout.swipeBack!!.getChildAt(0).measuredWidth
				}

				if (reactionsLayout.layoutParams.width != LayoutHelper.WRAP_CONTENT && reactionsLayout.layoutParams.width + widthDiff > maxWidth) {
					widthDiff = maxWidth - reactionsLayout.layoutParams.width + AndroidUtilities.dp(8f)
				}

				if (widthDiff < 0) {
					widthDiff = 0
				}

				(reactionsLayout.layoutParams as LayoutParams).rightMargin = widthDiff

				popupLayoutLeftOffset = 0f

				updatePopupTranslation()
			}
			else {
				popupLayoutLeftOffset = (maxWidth - menuContainer.measuredWidth) * 0.25f

				reactionsLayout.bigCircleOffset -= popupLayoutLeftOffset.toInt()

				if (reactionsLayout.bigCircleOffset < AndroidUtilities.dp(36f)) {
					popupLayoutLeftOffset = 0f
					reactionsLayout.bigCircleOffset = AndroidUtilities.dp(36f)
				}

				updatePopupTranslation()
			}

			val bottomView = bottomView

			if (bottomView != null) {
				if (reactionsLayout.showCustomEmojiReaction()) {
					bottomView.layoutParams.width = menuContainer.measuredWidth + AndroidUtilities.dp(16f)
					updatePopupTranslation()
				}
				else {
					bottomView.layoutParams.width = LayoutHelper.MATCH_PARENT
				}

				if (popupWindowLayout.swipeBack != null) {
					(bottomView.layoutParams as LayoutParams).rightMargin = widthDiff + AndroidUtilities.dp(36f)
				}
				else {
					(bottomView.layoutParams as LayoutParams).rightMargin = AndroidUtilities.dp(36f)
				}
			}

			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
		else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}

	private fun updatePopupTranslation() {
		val x = (1f - progressToSwipeBack) * popupLayoutLeftOffset
		popupWindowLayout?.translationX = x
		bottomView?.translationX = x
	}

	fun applyViewBottom(bottomView: FrameLayout?) {
		this.bottomView = bottomView
	}

	fun setReactionsLayout(reactionsLayout: ReactionsContainerLayout?) {
		this.reactionsLayout = reactionsLayout
		reactionsLayout?.setChatScrimView(this)
	}

	fun setPopupWindowLayout(popupWindowLayout: ActionBarPopupWindowLayout) {
		this.popupWindowLayout = popupWindowLayout

		popupWindowLayout.setOnSizeChangedListener {
			if (bottomView != null) {
				bottomViewYOffset = (popupWindowLayout.visibleHeight - popupWindowLayout.measuredHeight).toFloat()
				updateBottomViewPosition()
			}
		}

		if (popupWindowLayout.swipeBack != null) {
			popupWindowLayout.swipeBack?.addOnSwipeBackProgressListener { _, _, progress ->
				bottomView?.alpha = 1f - progress
				progressToSwipeBack = progress
				updatePopupTranslation()
			}
		}
	}

	private fun updateBottomViewPosition() {
		bottomView?.translationY = bottomViewYOffset + expandSize
	}

	fun setMaxHeight(maxHeight: Int) {
		this.maxHeight = maxHeight
	}

	fun setExpandSize(expandSize: Float) {
		popupWindowLayout?.translationY = expandSize
		this.expandSize = expandSize
		updateBottomViewPosition()
	}

	fun setPopupAlpha(alpha: Float) {
		popupWindowLayout?.alpha = alpha
		bottomView?.alpha = alpha
	}
}
