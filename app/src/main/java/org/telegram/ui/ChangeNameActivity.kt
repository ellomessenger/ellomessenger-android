/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022.
 */
package org.telegram.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView.OnEditorActionListener
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.*
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TL_account_updateProfile
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createLinear

class ChangeNameActivity : BaseFragment() {
	private var firstNameField: EditTextBoldCursor? = null
	private var lastNameField: EditTextBoldCursor? = null
	private var doneButton: View? = null

	@SuppressLint("ClickableViewAccessibility")
	override fun createView(context: Context): View? {
		// actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue), false);
		// actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(LocaleController.getString("EditName", R.string.EditName))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == -1) {
					finishFragment()
				}
				else if (id == done_button) {
					if (!firstNameField?.text.isNullOrEmpty()) {
						saveName()
						finishFragment()
					}
				}
			}
		})

		val menu = actionBar?.createMenu()

		doneButton = menu?.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56f), LocaleController.getString("Done", R.string.Done))

		var user = MessagesController.getInstance(currentAccount).getUser(getInstance(currentAccount).getClientUserId())

		if (user == null) {
			user = getInstance(currentAccount).getCurrentUser()
		}

		val linearLayout = LinearLayout(context)

		fragmentView = linearLayout
		fragmentView?.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
		(fragmentView as? LinearLayout)?.orientation = LinearLayout.VERTICAL

		fragmentView?.setOnTouchListener { _, _ ->
			true
		}

		firstNameField = EditTextBoldCursor(context)
		firstNameField?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		firstNameField?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		firstNameField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		firstNameField?.background = null
		firstNameField?.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_windowBackgroundWhiteRedText3))
		firstNameField?.maxLines = 1
		firstNameField?.setLines(1)
		firstNameField?.isSingleLine = true
		firstNameField?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		firstNameField?.inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
		firstNameField?.imeOptions = EditorInfo.IME_ACTION_NEXT
		firstNameField?.hint = LocaleController.getString("FirstName", R.string.FirstName)
		firstNameField?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		firstNameField?.setCursorSize(AndroidUtilities.dp(20f))
		firstNameField?.setCursorWidth(1.5f)

		linearLayout.addView(firstNameField, createLinear(LayoutHelper.MATCH_PARENT, 36, 24f, 24f, 24f, 0f))

		firstNameField?.setOnEditorActionListener(OnEditorActionListener { _, i, _ ->
			if (i == EditorInfo.IME_ACTION_NEXT) {
				lastNameField!!.requestFocus()
				lastNameField!!.setSelection(lastNameField!!.length())
				return@OnEditorActionListener true
			}

			false
		})

		lastNameField = EditTextBoldCursor(context)
		lastNameField?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		lastNameField?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		lastNameField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		lastNameField?.background = null
		lastNameField?.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_windowBackgroundWhiteRedText3))
		lastNameField?.maxLines = 1
		lastNameField?.setLines(1)
		lastNameField?.isSingleLine = true
		lastNameField?.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
		lastNameField?.inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
		lastNameField?.imeOptions = EditorInfo.IME_ACTION_DONE
		lastNameField?.hint = LocaleController.getString("LastName", R.string.LastName)
		lastNameField?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		lastNameField?.setCursorSize(AndroidUtilities.dp(20f))
		lastNameField?.setCursorWidth(1.5f)

		linearLayout.addView(lastNameField, createLinear(LayoutHelper.MATCH_PARENT, 36, 24f, 16f, 24f, 0f))

		lastNameField?.setOnEditorActionListener(OnEditorActionListener { _, i, _ ->
			if (i == EditorInfo.IME_ACTION_DONE) {
				doneButton?.performClick()
				return@OnEditorActionListener true
			}
			false
		})

		if (user != null) {
			firstNameField?.setText(user.first_name)
			firstNameField?.setSelection(firstNameField?.length() ?: 0)
			lastNameField?.setText(user.last_name)
		}
		return fragmentView
	}

	override fun onResume() {
		super.onResume()

		val preferences = MessagesController.getGlobalMainSettings()
		val animations = preferences.getBoolean("view_animations", true)

		if (!animations) {
			firstNameField?.requestFocus()
			AndroidUtilities.showKeyboard(firstNameField)
		}
	}

	private fun saveName() {
		val currentUser = getInstance(currentAccount).getCurrentUser()

		if (currentUser == null || lastNameField?.text == null || firstNameField?.text == null) {
			return
		}

		val newFirst = firstNameField?.text?.toString()
		val newLast = lastNameField?.text?.toString()

		if (currentUser.first_name != null && currentUser.first_name == newFirst && currentUser.last_name != null && currentUser.last_name == newLast) {
			return
		}

		val req = TL_account_updateProfile()
		req.flags = 3
		req.first_name = newFirst

		currentUser.first_name = req.first_name

		req.last_name = newLast

		currentUser.last_name = req.last_name

		val user = MessagesController.getInstance(currentAccount).getUser(getInstance(currentAccount).getClientUserId())
		user?.first_name = req.first_name
		user?.last_name = req.last_name

		getInstance(currentAccount).saveConfig(true)

		NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged)
		NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME)

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { _, _ ->
			// unused
		}
	}

	public override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		if (isOpen) {
			AndroidUtilities.runOnUIThread({
				if (firstNameField != null) {
					firstNameField?.requestFocus()
					AndroidUtilities.showKeyboard(firstNameField)
				}
			}, 100)
		}
	}

	companion object {
		private const val done_button = 1
	}
}
