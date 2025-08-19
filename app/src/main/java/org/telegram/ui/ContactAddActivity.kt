/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLRPC.User
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper

class ContactAddActivity(args: Bundle?) : BaseFragment(args), NotificationCenterDelegate {
	private var doneButton: View? = null
	private var firstNameField: EditTextBoldCursor? = null
	private var lastNameField: EditTextBoldCursor? = null
	private var avatarImage: BackupImageView? = null
	private var nameTextView: TextView? = null
	private var onlineTextView: TextView? = null
	private var userId: Long = 0
	private var addContact = false
	private var delegate: ContactAddActivityDelegate? = null
	var paused = false
	// private TextView infoTextView;
	// private CheckBoxCell checkBoxCell;
	// private boolean needAddException;
	// private String phone;

	fun interface ContactAddActivityDelegate {
		fun didAddToContacts()
	}

	override fun onFragmentCreate(): Boolean {
		notificationCenter.addObserver(this, NotificationCenter.updateInterfaces)

		userId = getArguments()?.getLong("user_id", 0L) ?: 0L
		addContact = getArguments()?.getBoolean("addContact", false) ?: false
		// phone = getArguments().getString("phone");
		// needAddException = MessagesController.getNotificationsSettings(currentAccount).getBoolean("dialog_bar_exception" + user_id, false);

		var user: User? = null

		if (userId != 0L) {
			user = messagesController.getUser(userId)
		}

		return user != null && super.onFragmentCreate()
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		notificationCenter.removeObserver(this, NotificationCenter.updateInterfaces)
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		if (addContact) {
			actionBar?.setTitle(context.getString(R.string.NewContact))
		}
		else {
			actionBar?.setTitle(context.getString(R.string.EditName))
		}

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					DONE_BUTTON -> {
						val firstName = firstNameField?.getText()?.toString()?.trim()

						if (!firstName.isNullOrEmpty()) {
							val user = messagesController.getUser(userId) ?: return
							user.firstName = firstName
							user.lastName = lastNameField!!.getText().toString()

							// getContactsController().addContact(user, checkBoxCell != null && checkBoxCell.isChecked());
							contactsController.addContact(user, false)

							MessagesController.getNotificationsSettings(currentAccount).edit { putInt("dialog_bar_vis3$userId", 3) }

							notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME)
							notificationCenter.postNotificationName(NotificationCenter.peerSettingsDidLoad, userId)

							finishFragment()

							delegate?.didAddToContacts()
						}
					}
				}
			}
		})

		val menu = actionBar?.createMenu()

		doneButton = menu?.addItem(DONE_BUTTON, context.getString(R.string.Done).uppercase())

		fragmentView = ScrollView(context)

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL

		(fragmentView as ScrollView).addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT))

		linearLayout.setOnTouchListener { _, _ ->
			true
		}

		val frameLayout = FrameLayout(context)

		linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 24f, 24f, 0f))

		avatarImage = BackupImageView(context)
		avatarImage?.setRoundRadius(AndroidUtilities.dp(30f))

		frameLayout.addView(avatarImage, LayoutHelper.createFrame(60, 60, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP))

		nameTextView = TextView(context)
		nameTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		nameTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		nameTextView?.setLines(1)
		nameTextView?.setMaxLines(1)
		nameTextView?.setSingleLine(true)
		nameTextView?.ellipsize = TextUtils.TruncateAt.END
		nameTextView?.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		nameTextView?.setTypeface(Theme.TYPEFACE_BOLD)

		frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 80).toFloat(), 3f, (if (LocaleController.isRTL) 80 else 0).toFloat(), 0f))

		onlineTextView = TextView(context)
		onlineTextView?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		onlineTextView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		onlineTextView?.setLines(1)
		onlineTextView?.setMaxLines(1)
		onlineTextView?.setSingleLine(true)
		onlineTextView?.ellipsize = TextUtils.TruncateAt.END
		onlineTextView?.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)

		frameLayout.addView(onlineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 80).toFloat(), 32f, (if (LocaleController.isRTL) 80 else 0).toFloat(), 0f))

		firstNameField = EditTextBoldCursor(context)
		firstNameField?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		firstNameField?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		firstNameField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		firstNameField?.background = null
		firstNameField?.setLineColors(context.getColor(R.color.hint), context.getColor(R.color.brand), context.getColor(R.color.purple))
		firstNameField?.setMaxLines(1)
		firstNameField?.setLines(1)
		firstNameField?.setSingleLine(true)
		firstNameField?.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		firstNameField?.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
		firstNameField?.setImeOptions(EditorInfo.IME_ACTION_NEXT)
		firstNameField?.setHint(context.getString(R.string.FirstName))
		firstNameField?.setCursorColor(context.getColor(R.color.text))
		firstNameField?.setCursorSize(AndroidUtilities.dp(20f))
		firstNameField?.setCursorWidth(1.5f)

		linearLayout.addView(firstNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 24f, 24f, 24f, 0f))

		firstNameField?.setOnEditorActionListener { _, i, _ ->
			if (i == EditorInfo.IME_ACTION_NEXT) {
				lastNameField?.requestFocus()
				lastNameField?.setSelection(lastNameField?.length() ?: 0)
				return@setOnEditorActionListener true
			}

			false
		}

		firstNameField?.onFocusChangeListener = object : OnFocusChangeListener {
			var focused = false

			override fun onFocusChange(v: View, hasFocus: Boolean) {
				focused = hasFocus
			}
		}

		lastNameField = EditTextBoldCursor(context)
		lastNameField?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		lastNameField?.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
		lastNameField?.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		lastNameField?.background = null
		lastNameField?.setLineColors(context.getColor(R.color.hint), context.getColor(R.color.brand), context.getColor(R.color.purple))
		lastNameField?.setMaxLines(1)
		lastNameField?.setLines(1)
		lastNameField?.setSingleLine(true)
		lastNameField?.setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
		lastNameField?.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
		lastNameField?.setImeOptions(EditorInfo.IME_ACTION_DONE)
		lastNameField?.setHint(context.getString(R.string.LastName))
		lastNameField?.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		lastNameField?.setCursorSize(AndroidUtilities.dp(20f))
		lastNameField?.setCursorWidth(1.5f)

		linearLayout.addView(lastNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 24f, 16f, 24f, 0f))

		lastNameField?.setOnEditorActionListener { _, i, _ ->
			if (i == EditorInfo.IME_ACTION_DONE) {
				doneButton?.performClick()
				return@setOnEditorActionListener true
			}

			false
		}

		val user = messagesController.getUser(userId)

		if (user != null) {
//			if (user.phone == null) {
//				if (phone != null) {
//					user.phone = PhoneFormat.stripExceptNumbers(phone);
//				}
//			}

			firstNameField?.setText(user.firstName)
			firstNameField?.setSelection(firstNameField?.length() ?: 0)

			lastNameField?.setText(user.lastName)
		}

//		infoTextView = new TextView(context);
//		infoTextView.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.dark_gray, null));
//		infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
//		infoTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

//		if (addContact) {
//			if (!needAddException || TextUtils.isEmpty(user.phone)) {
//				linearLayout.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 18, 24, 0));
//			}
//
//			if (needAddException) {
//				checkBoxCell = new CheckBoxCell(getParentActivity(), 0);
//				checkBoxCell.setBackground(Theme.getSelectorDrawable(false));
//				checkBoxCell.setText(LocaleController.formatString("SharePhoneNumberWith", R.string.SharePhoneNumberWith, UserObject.getFirstName(user)), "", true, false);
//				checkBoxCell.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
//				checkBoxCell.setOnClickListener(v -> checkBoxCell.setChecked(!checkBoxCell.isChecked(), true));
//				linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
//			}
//		}

		return fragmentView
	}

	fun setDelegate(contactAddActivityDelegate: ContactAddActivityDelegate?) {
		delegate = contactAddActivityDelegate
	}

	private fun updateAvatarLayout() {
		if (nameTextView == null) {
			return
		}

		val user = messagesController.getUser(userId) ?: return

		nameTextView?.text = UserObject.getUserName(user)

//		if (TextUtils.isEmpty(user.phone)) {
//			nameTextView.setText(LocaleController.getString("MobileHidden", R.string.MobileHidden));
		// infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("MobileHiddenExceptionInfo", R.string.MobileHiddenExceptionInfo, UserObject.getFirstName(user))));
//		}
//		else {
//			nameTextView.setText(PhoneFormat.getInstance().format("+" + user.phone));
//
//			if (needAddException) {
//				infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("MobileVisibleInfo", R.string.MobileVisibleInfo, UserObject.getFirstName(user))));
//			}
//		}

		onlineTextView?.text = LocaleController.formatUserStatus(currentAccount, user)
		avatarImage?.setForUserOrChat(user, AvatarDrawable(user))
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.updateInterfaces) {
			val mask = args[0] as? Int ?: return

			if (mask and MessagesController.UPDATE_MASK_AVATAR != 0 || mask and MessagesController.UPDATE_MASK_STATUS != 0) {
				updateAvatarLayout()
			}
		}
	}

	override fun onPause() {
		super.onPause()
		paused = true
	}

	override fun onResume() {
		super.onResume()

		updateAvatarLayout()

		if (firstNameField != null) {
			firstNameField?.requestFocus()

			val preferences = MessagesController.getGlobalMainSettings()
			val animations = preferences.getBoolean("view_animations", true)

			if (!animations) {
				AndroidUtilities.showKeyboard(firstNameField)
			}
		}
	}

	public override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
		if (isOpen) {
			firstNameField?.requestFocus()
			AndroidUtilities.showKeyboard(firstNameField)
		}
	}

	companion object {
		private const val DONE_BUTTON = 1
	}
}
