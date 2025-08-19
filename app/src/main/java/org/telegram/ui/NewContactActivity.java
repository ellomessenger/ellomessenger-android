/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.User;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import static android.widget.LinearLayout.HORIZONTAL;

public class NewContactActivity extends BaseFragment implements AdapterView.OnItemSelectedListener {
	private LinearLayout contentLayout;
	private ActionBarMenuItem editDoneItem;
	private ContextProgressView editDoneItemProgress;
	private EditTextBoldCursor firstNameField;
	private EditTextBoldCursor lastNameField;
	private EditTextBoldCursor codeField;
	private HintEditText phoneField;
	private BackupImageView avatarImage;
	private TextView countryButton;
	private AvatarDrawable avatarDrawable;
	private AnimatorSet editDoneItemAnimation;
	private final ArrayList<String> countriesArray = new ArrayList<>();
	private final HashMap<String, String> countriesMap = new HashMap<>();
	private final HashMap<String, String> codesMap = new HashMap<>();
	private final HashMap<String, String> phoneFormatMap = new HashMap<>();
	private boolean ignoreOnTextChange;
	private boolean ignoreOnPhoneChange;
	private boolean ignoreSelection;
	private boolean donePressed;
	private String initialPhoneNumber;
	private boolean initialPhoneNumberWithCountryCode;
	private String initialFirstName;
	private String initialLastName;
	private final static int done_button = 1;

	@Override
	public View createView(@NonNull Context context) {
		actionBar.setBackButtonImage(R.drawable.ic_back_arrow);
		actionBar.setAllowOverlayTitle(true);
		actionBar.setTitle(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					finishFragment();
				}
				else if (id == done_button) {
					if (donePressed) {
						return;
					}
					if (firstNameField.length() == 0) {
						Vibrator v = (Vibrator)getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
						if (v != null) {
							v.vibrate(200);
						}
						AndroidUtilities.shakeView(firstNameField, 2, 0);
						return;
					}
					if (codeField.length() == 0) {
						Vibrator v = (Vibrator)getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
						if (v != null) {
							v.vibrate(200);
						}
						AndroidUtilities.shakeView(codeField, 2, 0);
						return;
					}
					if (phoneField.length() == 0) {
						Vibrator v = (Vibrator)getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
						if (v != null) {
							v.vibrate(200);
						}
						AndroidUtilities.shakeView(phoneField, 2, 0);
						return;
					}
					donePressed = true;
					showEditDoneProgress(true, true);
					final TLRPC.TLContactsImportContacts req = new TLRPC.TLContactsImportContacts();
					final var inputPhoneContact = new TLRPC.TLInputContact();
					inputPhoneContact.firstName = firstNameField.getText().toString();
					inputPhoneContact.lastName = lastNameField.getText().toString();
					inputPhoneContact.phone = "+" + codeField.getText().toString() + phoneField.getText().toString();
					req.contacts.add(inputPhoneContact);
					int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
						final TLRPC.TLContactsImportedContacts res = (TLRPC.TLContactsImportedContacts)response;
						AndroidUtilities.runOnUIThread(() -> {
							donePressed = false;
							if (res != null) {
								if (!res.users.isEmpty()) {
									MessagesController.getInstance(currentAccount).putUsers(res.users, false);
									MessagesController.openChatOrProfileWith(res.users.get(0), null, NewContactActivity.this, 1, true);
								}
								else {
									if (getParentActivity() == null) {
										return;
									}
									showEditDoneProgress(false, true);
									AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
									builder.setTitle(LocaleController.getString("ContactNotRegisteredTitle", R.string.ContactNotRegisteredTitle));
									builder.setMessage(LocaleController.formatString("ContactNotRegistered", R.string.ContactNotRegistered, ContactsController.formatName(inputPhoneContact.firstName, inputPhoneContact.lastName)));
									builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
									builder.setPositiveButton(LocaleController.getString("Invite", R.string.Invite), (dialog, which) -> {
										try {
											Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", inputPhoneContact.phone, null));
											intent.putExtra("sms_body", ContactsController.getInstance(currentAccount).getInviteText(1));
											getParentActivity().startActivityForResult(intent, 500);
										}
										catch (Exception e) {
											FileLog.e(e);
										}
									});
									showDialog(builder.create());
								}
							}
							else {
								showEditDoneProgress(false, true);
								AlertsCreator.processError(currentAccount, error, NewContactActivity.this, req);
							}
						});
					}, ConnectionsManager.RequestFlagFailOnServerErrors);
					ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
				}
			}
		});

		avatarDrawable = new AvatarDrawable();
		avatarDrawable.setInfo("", "");

		ActionBarMenu menu = actionBar.createMenu();
		editDoneItem = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56));
		editDoneItem.setContentDescription(LocaleController.getString("Done", R.string.Done));

		editDoneItemProgress = new ContextProgressView(context, 1);
		editDoneItem.addView(editDoneItemProgress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
		editDoneItemProgress.setVisibility(View.INVISIBLE);

		fragmentView = new ScrollView(context);

		contentLayout = new LinearLayout(context);
		contentLayout.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
		contentLayout.setOrientation(LinearLayout.VERTICAL);
		((ScrollView)fragmentView).addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
		contentLayout.setOnTouchListener((v, event) -> true);

		FrameLayout frameLayout = new FrameLayout(context);
		contentLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 0));

		avatarImage = new BackupImageView(context);
		avatarImage.setImageDrawable(avatarDrawable);
		frameLayout.addView(avatarImage, LayoutHelper.createFrame(60, 60, Gravity.LEFT | Gravity.TOP, 0, 9, 0, 0));
		boolean needInvalidateAvatar = false;

		firstNameField = new EditTextBoldCursor(context);
		firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
		firstNameField.setHintTextColor(ResourcesCompat.getColor(context.getResources(), R.color.hint, null));
		firstNameField.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		firstNameField.setMaxLines(1);
		firstNameField.setLines(1);
		firstNameField.setSingleLine(true);
		firstNameField.setBackground(null);
		firstNameField.setLineColors(ResourcesCompat.getColor(context.getResources(), R.color.hint, null), ResourcesCompat.getColor(context.getResources(), R.color.brand, null), ResourcesCompat.getColor(context.getResources(), R.color.purple, null));
		firstNameField.setGravity(Gravity.LEFT);
		firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
		firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
		firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
		firstNameField.setCursorColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		firstNameField.setCursorSize(AndroidUtilities.dp(20));
		firstNameField.setCursorWidth(1.5f);
		if (initialFirstName != null) {
			firstNameField.setText(initialFirstName);
			initialFirstName = null;
			needInvalidateAvatar = true;
		}
		frameLayout.addView(firstNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 84, 0, 0, 0));
		firstNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
			if (i == EditorInfo.IME_ACTION_NEXT) {
				lastNameField.requestFocus();
				lastNameField.setSelection(lastNameField.length());
				return true;
			}
			return false;
		});
		firstNameField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				invalidateAvatar();
			}
		});

		lastNameField = new EditTextBoldCursor(context);
		lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
		lastNameField.setHintTextColor(ResourcesCompat.getColor(context.getResources(), R.color.hint, null));
		lastNameField.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		lastNameField.setBackground(null);
		lastNameField.setLineColors(ResourcesCompat.getColor(context.getResources(), R.color.hint, null), ResourcesCompat.getColor(context.getResources(), R.color.brand, null), ResourcesCompat.getColor(context.getResources(), R.color.purple, null));
		lastNameField.setMaxLines(1);
		lastNameField.setLines(1);
		lastNameField.setSingleLine(true);
		lastNameField.setGravity(Gravity.LEFT);
		lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
		lastNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
		lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
		lastNameField.setCursorColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		lastNameField.setCursorSize(AndroidUtilities.dp(20));
		lastNameField.setCursorWidth(1.5f);
		if (initialLastName != null) {
			lastNameField.setText(initialLastName);
			initialLastName = null;
			needInvalidateAvatar = true;
		}
		frameLayout.addView(lastNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 84, 44, 0, 0));
		lastNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
			if (i == EditorInfo.IME_ACTION_NEXT) {
				phoneField.requestFocus();
				phoneField.setSelection(phoneField.length());
				return true;
			}
			return false;
		});
		lastNameField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				invalidateAvatar();
			}
		});

		if (needInvalidateAvatar) {
			invalidateAvatar();
		}

		countryButton = new TextView(context);
		countryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
		countryButton.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
		countryButton.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		countryButton.setMaxLines(1);
		countryButton.setSingleLine(true);
		countryButton.setEllipsize(TextUtils.TruncateAt.END);
		countryButton.setGravity(Gravity.LEFT | Gravity.CENTER_HORIZONTAL);
		countryButton.setBackground(Theme.getSelectorDrawable(true));
		contentLayout.addView(countryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 24, 0, 14));
		countryButton.setOnClickListener(view -> {
			CountrySelectActivity fragment = new CountrySelectActivity(true);
			fragment.setCountrySelectActivityDelegate((country) -> {
				selectCountry(country.name);
				phoneField.requestFocus();
				phoneField.setSelection(phoneField.length());
			});
			presentFragment(fragment);
		});

		View lineView = new View(context);
		lineView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
		lineView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.divider, null));
		contentLayout.addView(lineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, -17.5f, 0, 0));

		LinearLayout linearLayout2 = new LinearLayout(context);
		linearLayout2.setOrientation(HORIZONTAL);
		contentLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

		TextView textView1 = new TextView(context);
		textView1.setText("+");
		textView1.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
		textView1.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		linearLayout2.addView(textView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

		codeField = new EditTextBoldCursor(context);
		codeField.setInputType(InputType.TYPE_CLASS_PHONE);
		codeField.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		codeField.setBackground(null);
		codeField.setLineColors(ResourcesCompat.getColor(context.getResources(), R.color.hint, null), ResourcesCompat.getColor(context.getResources(), R.color.brand, null), ResourcesCompat.getColor(context.getResources(), R.color.purple, null));
		codeField.setCursorColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		codeField.setCursorSize(AndroidUtilities.dp(20));
		codeField.setCursorWidth(1.5f);
		codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
		codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
		codeField.setMaxLines(1);
		codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
		codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		linearLayout2.addView(codeField, LayoutHelper.createLinear(55, 36, -9, 0, 16, 0));
		codeField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				if (ignoreOnTextChange) {
					return;
				}
				ignoreOnTextChange = true;
				String text = PhoneFormat.stripExceptNumbers(codeField.getText().toString());
				codeField.setText(text);
				if (text.length() == 0) {
					countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
					phoneField.setHintText(null);
				}
				else {
					String country;
					boolean ok = false;
					String textToSet = null;
					if (text.length() > 4) {
						ignoreOnTextChange = true;
						for (int a = 4; a >= 1; a--) {
							String sub = text.substring(0, a);
							country = codesMap.get(sub);
							if (country != null) {
								ok = true;
								textToSet = text.substring(a) + phoneField.getText().toString();
								codeField.setText(text = sub);
								break;
							}
						}
						if (!ok) {
							ignoreOnTextChange = true;
							textToSet = text.substring(1) + phoneField.getText().toString();
							codeField.setText(text = text.substring(0, 1));
						}
					}
					country = codesMap.get(text);
					if (country != null) {
						int index = countriesArray.indexOf(country);
						if (index != -1) {
							ignoreSelection = true;
							countryButton.setText(countriesArray.get(index));
							String hint = phoneFormatMap.get(text);
							phoneField.setHintText(hint != null ? hint.replace('X', '–') : null);
						}
						else {
							countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
							phoneField.setHintText(null);
						}
					}
					else {
						countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
						phoneField.setHintText(null);
					}
					if (!ok) {
						codeField.setSelection(codeField.getText().length());
					}
					if (textToSet != null) {
						if (initialPhoneNumber == null) {
							phoneField.requestFocus();
						}
						phoneField.setText(textToSet);
						phoneField.setSelection(phoneField.length());
					}
				}
				ignoreOnTextChange = false;
			}
		});
		codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
			if (i == EditorInfo.IME_ACTION_NEXT) {
				phoneField.requestFocus();
				phoneField.setSelection(phoneField.length());
				return true;
			}
			return false;
		});

		phoneField = new HintEditText(context);
		phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
		phoneField.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		phoneField.setHintTextColor(ResourcesCompat.getColor(context.getResources(), R.color.hint, null));
		phoneField.setBackground(null);
		phoneField.setLineColors(ResourcesCompat.getColor(context.getResources(), R.color.hint, null), ResourcesCompat.getColor(context.getResources(), R.color.brand, null), ResourcesCompat.getColor(context.getResources(), R.color.purple, null));
		phoneField.setPadding(0, 0, 0, 0);
		phoneField.setCursorColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
		phoneField.setCursorSize(AndroidUtilities.dp(20));
		phoneField.setCursorWidth(1.5f);
		phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
		phoneField.setMaxLines(1);
		phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
		phoneField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);

		linearLayout2.addView(phoneField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));

		phoneField.addTextChangedListener(new TextWatcher() {
			private int characterAction = -1;
			private int actionPosition;

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				if (count == 0 && after == 1) {
					characterAction = 1;
				}
				else if (count == 1 && after == 0) {
					if (s.charAt(start) == ' ' && start > 0) {
						characterAction = 3;
						actionPosition = start - 1;
					}
					else {
						characterAction = 2;
					}
				}
				else {
					characterAction = -1;
				}
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				if (ignoreOnPhoneChange) {
					return;
				}
				int start = phoneField.getSelectionStart();
				String phoneChars = "0123456789";
				String str = phoneField.getText().toString();
				if (characterAction == 3) {
					str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
					start--;
				}
				StringBuilder builder = new StringBuilder(str.length());
				for (int a = 0; a < str.length(); a++) {
					String ch = str.substring(a, a + 1);
					if (phoneChars.contains(ch)) {
						builder.append(ch);
					}
				}
				ignoreOnPhoneChange = true;
				String hint = phoneField.getHintText();
				if (hint != null) {
					for (int a = 0; a < builder.length(); a++) {
						if (a < hint.length()) {
							if (hint.charAt(a) == ' ') {
								builder.insert(a, ' ');
								a++;
								if (start == a && characterAction != 2 && characterAction != 3) {
									start++;
								}
							}
						}
						else {
							builder.insert(a, ' ');
							if (start == a + 1 && characterAction != 2 && characterAction != 3) {
								start++;
							}
							break;
						}
					}
				}
				phoneField.setText(builder);
				if (start >= 0) {
					phoneField.setSelection(Math.min(start, phoneField.length()));
				}
				phoneField.onTextChange();
				ignoreOnPhoneChange = false;
			}
		});
		phoneField.setOnEditorActionListener((textView, i, keyEvent) -> {
			if (i == EditorInfo.IME_ACTION_DONE) {
				editDoneItem.performClick();
				return true;
			}
			return false;
		});
		phoneField.setOnKeyListener((v, keyCode, event) -> {
			if (keyCode == KeyEvent.KEYCODE_DEL && phoneField.length() == 0) {
				codeField.requestFocus();
				codeField.setSelection(codeField.length());
				codeField.dispatchKeyEvent(event);
				return true;
			}
			return false;
		});

		HashMap<String, String> languageMap = new HashMap<>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] args = line.split(";");
				countriesArray.add(0, args[2]);
				countriesMap.put(args[2], args[0]);
				codesMap.put(args[0], args[2]);
				if (args.length > 3) {
					phoneFormatMap.put(args[0], args[3]);
				}
				languageMap.put(args[1], args[2]);
			}
			reader.close();
		}
		catch (Exception e) {
			FileLog.e(e);
		}

		Collections.sort(countriesArray, String::compareTo);

		if (!TextUtils.isEmpty(initialPhoneNumber)) {
			User user = getUserConfig().getCurrentUser();
			if (initialPhoneNumber.startsWith("+")) {
				codeField.setText(initialPhoneNumber.substring(1));
			}
			else if (initialPhoneNumberWithCountryCode || user == null) { // || TextUtils.isEmpty(user.phone)) {
				codeField.setText(initialPhoneNumber);
			}
			else {
				// String phone = user.phone;
//				for (int a = 4; a >= 1; a--) {
//					String sub = phone.substring(0, a);
//					String country = codesMap.get(sub);
//					if (country != null) {
//						codeField.setText(sub);
//						break;
//					}
//				}
				phoneField.setText(initialPhoneNumber);
			}
			initialPhoneNumber = null;
		}
		else {
			String country = null;
			try {
				TelephonyManager telephonyManager = (TelephonyManager)ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
				if (telephonyManager != null) {
					country = telephonyManager.getSimCountryIso().toUpperCase();
				}
			}
			catch (Exception e) {
				FileLog.e(e);
			}

			if (country != null) {
				String countryName = languageMap.get(country);
				if (countryName != null) {
					int index = countriesArray.indexOf(countryName);
					if (index != -1) {
						codeField.setText(countriesMap.get(countryName));
					}
				}
			}
			if (codeField.length() == 0) {
				countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
				phoneField.setHintText(null);
			}
		}

		return fragmentView;
	}

	private void invalidateAvatar() {
		avatarDrawable.setInfo(firstNameField.getText().toString(), lastNameField.getText().toString());
		avatarImage.invalidate();
	}

	@Override
	public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
		if (isOpen) {
			View focusedView = contentLayout.findFocus();
			if (focusedView == null) {
				firstNameField.requestFocus();
				focusedView = firstNameField;
			}
			AndroidUtilities.showKeyboard(focusedView);
		}
	}

	public void setInitialPhoneNumber(String value, boolean withCountryCode) {
		initialPhoneNumber = value;
		initialPhoneNumberWithCountryCode = withCountryCode;
	}

	public void setInitialName(String firstName, String lastName) {
		initialFirstName = firstName;
		initialLastName = lastName;
	}

	public void selectCountry(String name) {
		int index = countriesArray.indexOf(name);
		if (index != -1) {
			ignoreOnTextChange = true;
			String code = countriesMap.get(name);
			codeField.setText(code);
			countryButton.setText(name);
			String hint = phoneFormatMap.get(code);
			phoneField.setHintText(hint != null ? hint.replace('X', '–') : null);
			ignoreOnTextChange = false;
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
		if (ignoreSelection) {
			ignoreSelection = false;
			return;
		}
		ignoreOnTextChange = true;
		String str = countriesArray.get(i);
		codeField.setText(countriesMap.get(str));
		ignoreOnTextChange = false;
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {
		// unused
	}

	private void showEditDoneProgress(final boolean show, boolean animated) {
		if (editDoneItemAnimation != null) {
			editDoneItemAnimation.cancel();
		}
		if (!animated) {
			if (show) {
				editDoneItem.getContentView().setScaleX(0.1f);
				editDoneItem.getContentView().setScaleY(0.1f);
				editDoneItem.getContentView().setAlpha(0.0f);
				editDoneItemProgress.setScaleX(1.0f);
				editDoneItemProgress.setScaleY(1.0f);
				editDoneItemProgress.setAlpha(1.0f);
				editDoneItem.getContentView().setVisibility(View.INVISIBLE);
				editDoneItemProgress.setVisibility(View.VISIBLE);
				editDoneItem.setEnabled(false);
			}
			else {
				editDoneItemProgress.setScaleX(0.1f);
				editDoneItemProgress.setScaleY(0.1f);
				editDoneItemProgress.setAlpha(0.0f);
				editDoneItem.getContentView().setScaleX(1.0f);
				editDoneItem.getContentView().setScaleY(1.0f);
				editDoneItem.getContentView().setAlpha(1.0f);
				editDoneItem.getContentView().setVisibility(View.VISIBLE);
				editDoneItemProgress.setVisibility(View.INVISIBLE);
				editDoneItem.setEnabled(true);
			}
		}
		else {
			editDoneItemAnimation = new AnimatorSet();
			if (show) {
				editDoneItemProgress.setVisibility(View.VISIBLE);
				editDoneItem.setEnabled(false);
				editDoneItemAnimation.playTogether(ObjectAnimator.ofFloat(editDoneItem.getContentView(), "scaleX", 0.1f), ObjectAnimator.ofFloat(editDoneItem.getContentView(), "scaleY", 0.1f), ObjectAnimator.ofFloat(editDoneItem.getContentView(), "alpha", 0.0f), ObjectAnimator.ofFloat(editDoneItemProgress, "scaleX", 1.0f), ObjectAnimator.ofFloat(editDoneItemProgress, "scaleY", 1.0f), ObjectAnimator.ofFloat(editDoneItemProgress, "alpha", 1.0f));
			}
			else {
				editDoneItem.getContentView().setVisibility(View.VISIBLE);
				editDoneItem.setEnabled(true);
				editDoneItemAnimation.playTogether(ObjectAnimator.ofFloat(editDoneItemProgress, "scaleX", 0.1f), ObjectAnimator.ofFloat(editDoneItemProgress, "scaleY", 0.1f), ObjectAnimator.ofFloat(editDoneItemProgress, "alpha", 0.0f), ObjectAnimator.ofFloat(editDoneItem.getContentView(), "scaleX", 1.0f), ObjectAnimator.ofFloat(editDoneItem.getContentView(), "scaleY", 1.0f), ObjectAnimator.ofFloat(editDoneItem.getContentView(), "alpha", 1.0f));
			}
			editDoneItemAnimation.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					if (editDoneItemAnimation != null && editDoneItemAnimation.equals(animation)) {
						if (!show) {
							editDoneItemProgress.setVisibility(View.INVISIBLE);
						}
						else {
							editDoneItem.getContentView().setVisibility(View.INVISIBLE);
						}
					}
				}

				@Override
				public void onAnimationCancel(Animator animation) {
					if (editDoneItemAnimation != null && editDoneItemAnimation.equals(animation)) {
						editDoneItemAnimation = null;
					}
				}
			});
			editDoneItemAnimation.setDuration(150);
			editDoneItemAnimation.start();
		}
	}
}
