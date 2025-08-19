/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocationController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.editDate
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CombinedDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ShareLocationDrawable
import kotlin.math.abs

class SendLocationCell(context: Context, private val live: Boolean) : FrameLayout(context) {
	private val currentAccount = UserConfig.selectedAccount
	private val accurateTextView: SimpleTextView
	private val titleTextView: SimpleTextView
	private val imageView = ImageView(context)
	private var dialogId: Long = 0
	private var rect: RectF? = null

	private val invalidateRunnable = object : Runnable {
		override fun run() {
			checkText()
			invalidate()
			AndroidUtilities.runOnUIThread(this, 1000)
		}
	}

	init {
		background = Theme.AdaptiveRipple.rect()

		val circle = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(42f), ResourcesCompat.getColor(context.resources, if (live) R.color.online else R.color.brand, null), ResourcesCompat.getColor(context.resources, if (live) R.color.online else R.color.brand, null))

		if (live) {
			rect = RectF()

			val drawable: Drawable = ShareLocationDrawable(context, 4)
			drawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.MULTIPLY)

			val combinedDrawable = CombinedDrawable(circle, drawable)
			combinedDrawable.setCustomSize(AndroidUtilities.dp(42f), AndroidUtilities.dp(42f))

			imageView.background = combinedDrawable

			AndroidUtilities.runOnUIThread(invalidateRunnable, 1000)

			setWillNotDraw(false)
		}
		else {
			val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.pin, null)
			drawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.MULTIPLY)

			val combinedDrawable = CombinedDrawable(circle, drawable)
			combinedDrawable.setCustomSize(AndroidUtilities.dp(42f), AndroidUtilities.dp(42f))
			combinedDrawable.setIconSize(AndroidUtilities.dp(24f), AndroidUtilities.dp(24f))

			imageView.background = combinedDrawable
		}

		addView(imageView, LayoutHelper.createFrame(42, 42f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, (if (LocaleController.isRTL) 0 else 15).toFloat(), 12f, (if (LocaleController.isRTL) 15 else 0).toFloat(), 0f))

		titleTextView = SimpleTextView(context)
		titleTextView.setTextSize(16)
		titleTextView.textColor = ResourcesCompat.getColor(context.resources, if (live) R.color.online else R.color.brand, null)
		titleTextView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		titleTextView.setTypeface(Theme.TYPEFACE_BOLD)

		addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, (if (LocaleController.isRTL) 16 else 73).toFloat(), 12f, (if (LocaleController.isRTL) 73 else 16).toFloat(), 0f))

		accurateTextView = SimpleTextView(context)
		accurateTextView.setTextSize(14)
		accurateTextView.textColor = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
		accurateTextView.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)

		addView(accurateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, Gravity.TOP or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, (if (LocaleController.isRTL) 16 else 73).toFloat(), 37f, (if (LocaleController.isRTL) 73 else 16).toFloat(), 0f))
	}

	fun setHasLocation(value: Boolean) {
		val info = LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId)

		if (info == null) {
			titleTextView.alpha = if (value) 1.0f else 0.5f
			accurateTextView.alpha = if (value) 1.0f else 0.5f
			imageView.alpha = if (value) 1.0f else 0.5f
		}

		if (live) {
			checkText()
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(66f), MeasureSpec.EXACTLY))
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		AndroidUtilities.cancelRunOnUIThread(invalidateRunnable)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (rect != null) {
			AndroidUtilities.runOnUIThread(invalidateRunnable, 1000)
		}
	}

	fun setText(title: String?, text: String?) {
		titleTextView.setText(title)
		accurateTextView.setText(text)
	}

	fun setDialogId(did: Long) {
		dialogId = did

		if (live) {
			checkText()
		}
	}

	private fun checkText() {
		val info = LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId)

		if (info != null) {
			val updateDate = info.messageObject?.messageOwner?.editDate?.takeIf { it != 0 } ?: info.messageObject?.messageOwner?.date ?: 0
			setText(context.getString(R.string.StopLiveLocation), LocaleController.formatLocationUpdateDate((updateDate).toLong()))
		}
		else {
			setText(context.getString(R.string.SendLiveLocation), context.getString(R.string.SendLiveLocationInfo))
		}
	}

	override fun onDraw(canvas: Canvas) {
		val currentInfo = LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId) ?: return
		val currentTime = ConnectionsManager.getInstance(currentAccount).currentTime

		if (currentInfo.stopTime < currentTime) {
			return
		}

		val progress = abs(currentInfo.stopTime - currentTime) / currentInfo.period.toFloat()

		if (LocaleController.isRTL) {
			rect?.set(AndroidUtilities.dp(13f).toFloat(), AndroidUtilities.dp(18f).toFloat(), AndroidUtilities.dp(43f).toFloat(), AndroidUtilities.dp(48f).toFloat())
		}
		else {
			rect?.set((measuredWidth - AndroidUtilities.dp(43f)).toFloat(), AndroidUtilities.dp(18f).toFloat(), (measuredWidth - AndroidUtilities.dp(13f)).toFloat(), AndroidUtilities.dp(48f).toFloat())
		}

		val color = ResourcesCompat.getColor(context.resources, R.color.online, null)

		Theme.chat_radialProgress2Paint.color = color
		Theme.chat_livePaint.color = color

		rect?.let {
			canvas.drawArc(it, -90f, -360 * progress, false, Theme.chat_radialProgress2Paint)

			val text = LocaleController.formatLocationLeftTime(abs(currentInfo.stopTime - currentTime))
			val size = Theme.chat_livePaint.measureText(text)

			canvas.drawText(text, it.centerX() - size / 2, AndroidUtilities.dp(37f).toFloat(), Theme.chat_livePaint)
		}
	}
}
