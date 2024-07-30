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
import android.widget.PopupWindow
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.ActionBarMenuItem.Companion.addItem
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.BaseFragment

class ChatNotificationsPopupWrapper(context: Context, var currentAccount: Int, swipeBackLayout: PopupSwipeBackLayout?, createBackground: Boolean, var callback: Callback) {
	@JvmField
	var windowLayout: ActionBarPopupWindowLayout

	private val muteForLastSelected2: ActionBarMenuSubItem
	private val muteForLastSelected: ActionBarMenuSubItem
	private val muteUnmuteButton: ActionBarMenuSubItem
	private val soundToggle: ActionBarMenuSubItem
	private var backItem: View? = null
	private var lastDismissTime = 0L
	private var muteForLastSelected1Time = 0
	private var muteForLastSelected2Time = 0
	var popupWindow: ActionBarPopupWindow? = null

	init {
		windowLayout = ActionBarPopupWindowLayout(context, if (createBackground) R.drawable.popup_fixed_alert else 0)
		windowLayout.setFitItems(true)

		if (swipeBackLayout != null) {
			backItem = addItem(windowLayout, R.drawable.msg_arrow_back, context.getString(R.string.Back), false)

			backItem?.setOnClickListener {
				swipeBackLayout.closeForeground()
			}
		}

		soundToggle = addItem(windowLayout, R.drawable.msg_tone_on, context.getString(R.string.SoundOn), false)

		soundToggle.setOnClickListener {
			dismiss()
			callback.toggleSound()
		}

		muteForLastSelected = addItem(windowLayout, R.drawable.msg_mute_1h, context.getString(R.string.MuteFor1h), false)

		muteForLastSelected.setOnClickListener {
			dismiss()
			callback.muteFor(muteForLastSelected1Time)
		}

		muteForLastSelected2 = addItem(windowLayout, R.drawable.msg_mute_1h, context.getString(R.string.MuteFor1h), false)

		muteForLastSelected2.setOnClickListener {
			dismiss()
			callback.muteFor(muteForLastSelected2Time)
		}

		val item = addItem(windowLayout, R.drawable.msg_mute_period, context.getString(R.string.MuteForPopup), false)

		item.setOnClickListener {
			dismiss()

			AlertsCreator.createMuteForPickerDialog(context) { _, inSecond ->
				AndroidUtilities.runOnUIThread({
					if (inSecond != 0) {
						val sharedPreferences = MessagesController.getNotificationsSettings(currentAccount)
						var time1 = sharedPreferences.getInt(LAST_SELECTED_TIME_KEY_1, 0)
						val time2 = time1
						time1 = inSecond
						sharedPreferences.edit().putInt(LAST_SELECTED_TIME_KEY_1, time1).putInt(LAST_SELECTED_TIME_KEY_2, time2).commit()
					}

					callback.muteFor(inSecond)
				}, 16)
			}
		}

		muteUnmuteButton = addItem(windowLayout, 0, "", false)

		muteUnmuteButton.setOnClickListener {
			dismiss()
			AndroidUtilities.runOnUIThread {
				callback.toggleMute()
			}
		}
	}

	private fun dismiss() {
		popupWindow?.dismiss()
		callback.dismiss()
		lastDismissTime = System.currentTimeMillis()
	}

	fun update(dialogId: Long) {
		if (System.currentTimeMillis() - lastDismissTime < 200) {
			AndroidUtilities.runOnUIThread {
				update(dialogId)
			}

			return
		}

		val muted = MessagesController.getInstance(currentAccount).isDialogMuted(dialogId)
		val color: Int
		val context = muteUnmuteButton.context

		if (muted) {
			muteUnmuteButton.setTextAndIcon(context.getString(R.string.UnmuteNotifications), R.drawable.msg_unmute)
			color = context.getColor(R.color.online)
			soundToggle.gone()
		}
		else {
			muteUnmuteButton.setTextAndIcon(context.getString(R.string.MuteNotifications), R.drawable.msg_mute)
			color = context.getColor(R.color.purple)
			soundToggle.visible()

			val soundOn = MessagesController.getInstance(currentAccount).isDialogNotificationsSoundEnabled(dialogId)

			if (soundOn) {
				soundToggle.setTextAndIcon(context.getString(R.string.SoundOff), R.drawable.msg_tone_off)
			}
			else {
				soundToggle.setTextAndIcon(context.getString(R.string.SoundOn), R.drawable.msg_tone_on)
			}
		}

		val time1: Int
		val time2: Int

		if (muted) {
			time1 = 0
			time2 = 0
		}
		else {
			val sharedPreferences = MessagesController.getNotificationsSettings(currentAccount)
			time1 = sharedPreferences.getInt(LAST_SELECTED_TIME_KEY_1, 0)
			time2 = sharedPreferences.getInt(LAST_SELECTED_TIME_KEY_2, 0)
		}

		if (time1 != 0) {
			muteForLastSelected1Time = time1
			muteForLastSelected.visibility = View.VISIBLE
			muteForLastSelected.imageView.setImageDrawable(TimerDrawable.getTtlIcon(time1))
			muteForLastSelected.setText(formatMuteForTime(time1))
		}
		else {
			muteForLastSelected.gone()
		}

		if (time2 != 0) {
			muteForLastSelected2Time = time2
			muteForLastSelected2.visible()
			muteForLastSelected2.imageView.setImageDrawable(TimerDrawable.getTtlIcon(time2))
			muteForLastSelected2.setText(formatMuteForTime(time2))
		}
		else {
			muteForLastSelected2.gone()
		}

		muteUnmuteButton.setColors(color, color)
	}

	private fun formatMuteForTime(time: Int): String {
		@Suppress("NAME_SHADOWING") var time = time
		val stringBuilder = StringBuilder()
		val days = time / (60 * 60 * 24)

		time -= days * (60 * 60 * 24)

		val hours = time / (60 * 60)

		time -= hours * (60 * 60)

		val minutes = time / 60

		val context = ApplicationLoader.applicationContext

		if (days != 0) {
			stringBuilder.append(days).append(context.getString(R.string.SecretChatTimerDays))
		}

		if (hours != 0) {
			if (stringBuilder.isNotEmpty()) {
				stringBuilder.append(" ")
			}

			stringBuilder.append(hours).append(context.getString(R.string.SecretChatTimerHours))
		}

		if (minutes != 0) {
			if (stringBuilder.isNotEmpty()) {
				stringBuilder.append(" ")
			}

			stringBuilder.append(minutes).append(context.getString(R.string.SecretChatTimerMinutes))
		}

		return LocaleController.formatString("MuteForButton", R.string.MuteForButton, stringBuilder.toString())
	}

	fun showAsOptions(parentFragment: BaseFragment?, anchorView: View, touchedX: Float, touchedY: Float) {
		if (parentFragment == null || parentFragment.fragmentView == null) {
			return
		}

		popupWindow = ActionBarPopupWindow(windowLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
		popupWindow?.setPauseNotifications(true)
		popupWindow?.setDismissAnimationDuration(220)
		popupWindow?.isOutsideTouchable = true
		popupWindow?.isClippingEnabled = true
		popupWindow?.animationStyle = R.style.PopupContextAnimation
		popupWindow?.isFocusable = true

		windowLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST))

		popupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
		popupWindow?.contentView?.isFocusableInTouchMode = true

		var x = touchedX
		var y = touchedY
		var view = anchorView

		while (view !== parentFragment.fragmentView) {
			x += view.x
			y += view.y
			view = view.parent as View
		}

		x -= windowLayout.measuredWidth / 2f
		y -= windowLayout.measuredHeight / 2f

		popupWindow?.showAtLocation(parentFragment.fragmentView!!, 0, x.toInt(), y.toInt())
		popupWindow?.dimBehind()
	}

	interface Callback {
		fun dismiss() {}
		fun toggleSound()
		fun muteFor(timeInSeconds: Int)
		fun showCustomize()
		fun toggleMute()
	}

	companion object {
		private const val LAST_SELECTED_TIME_KEY_1 = "last_selected_mute_until_time"
		private const val LAST_SELECTED_TIME_KEY_2 = "last_selected_mute_until_time2"
	}
}
