/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components.Reactions

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DocumentObject.getSvgThumb
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Reactions.VisibleReaction.Companion.fromTLReaction

class ReactionTabHolderView(context: Context) : FrameLayout(context) {
	private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val rect = RectF()
	private val radius = AndroidUtilities.dp(32f).toFloat()
	private val reactView: BackupImageView
	private val iconView = ImageView(context)
	private val counterView = TextView(context)
	private val overlaySelectorView = View(context)
	private var outlineProgress = 0f
	private var count = 0
	private var reaction: VisibleReaction? = null
	private val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.msg_reactions_filled, null)!!.mutate()

	init {
		addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		iconView.setImageDrawable(drawable)

		addView(iconView, LayoutHelper.createFrameRelatively(24f, 24f, Gravity.START or Gravity.CENTER_VERTICAL, 8f, 0f, 8f, 0f))

		reactView = BackupImageView(context)

		addView(reactView, LayoutHelper.createFrameRelatively(24f, 24f, Gravity.START or Gravity.CENTER_VERTICAL, 8f, 0f, 8f, 0f))

		counterView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		counterView.setTextColor(context.getColor(R.color.brand))
		counterView.typeface = Theme.TYPEFACE_BOLD

		addView(counterView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 40f, 0f, 8f, 0f))

		outlinePaint.style = Paint.Style.STROKE
		outlinePaint.strokeWidth = AndroidUtilities.dp(1f).toFloat()

		setWillNotDraw(false)

		setOutlineProgress(outlineProgress)
	}

	fun setOutlineProgress(outlineProgress: Float) {
		this.outlineProgress = outlineProgress

		val backgroundSelectedColor = context.getColor(R.color.light_background)
		val backgroundColor = ColorUtils.setAlphaComponent(context.getColor(R.color.background), 0x10)
		val textSelectedColor = context.getColor(R.color.brand)
		val textColor = context.getColor(R.color.text)
		val textFinalColor = ColorUtils.blendARGB(textColor, textSelectedColor, outlineProgress)

		bgPaint.color = ColorUtils.blendARGB(backgroundColor, backgroundSelectedColor, outlineProgress)
		counterView.setTextColor(textFinalColor)
		drawable.colorFilter = PorterDuffColorFilter(textFinalColor, PorterDuff.Mode.MULTIPLY)

		if (outlineProgress == 1f) {
			overlaySelectorView.background = Theme.createSimpleSelectorRoundRectDrawable(radius.toInt(), Color.TRANSPARENT, ColorUtils.setAlphaComponent(context.getColor(R.color.brand), (0.3f * 255).toInt()))
		}
		else if (outlineProgress == 0f) {
			overlaySelectorView.background = Theme.createSimpleSelectorRoundRectDrawable(radius.toInt(), Color.TRANSPARENT, ColorUtils.setAlphaComponent(backgroundSelectedColor, (0.3f * 255).toInt()))
		}

		invalidate()
	}

	fun setCounter(count: Int) {
		this.count = count
		counterView.text = String.format("%s", LocaleController.formatShortNumber(count, null))
		iconView.visible()
		reactView.gone()
	}

	fun setCounter(currentAccount: Int, counter: TLRPC.TLReactionCount) {
		count = counter.count
		counterView.text = String.format("%s", LocaleController.formatShortNumber(counter.count, null))

		reaction = fromTLReaction(counter.reaction)

		if (reaction?.emojicon != null) {
			for (r in MediaDataController.getInstance(currentAccount).getReactionsList()) {
				if (r.reaction == reaction?.emojicon) {
					val svgThumb = getSvgThumb(r.staticIcon, ResourcesCompat.getColor(context.resources, R.color.light_background, null), 1.0f)
					reactView.setImage(ImageLocation.getForDocument(r.centerIcon), "40_40_lastframe", "webp", svgThumb, r)
					reactView.visible()
					iconView.gone()
					break
				}
			}
		}
		else {
			reactView.animatedEmojiDrawable = AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, currentAccount, reaction!!.documentId)
			reactView.visible()
			iconView.gone()
		}
	}

	override fun dispatchDraw(canvas: Canvas) {
		rect.set(0f, 0f, width.toFloat(), height.toFloat())
		canvas.drawRoundRect(rect, radius, radius, bgPaint)
		super.dispatchDraw(canvas)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)

		info.className = "android.widget.Button"
		info.isClickable = true

		if (outlineProgress > .5) {
			info.isSelected = true
		}
		if (reaction != null) {
			info.text = LocaleController.formatPluralString("AccDescrNumberOfPeopleReactions", count, reaction)
		}
		else {
			info.text = LocaleController.formatPluralString("AccDescrNumberOfReactions", count)
		}
	}
}
