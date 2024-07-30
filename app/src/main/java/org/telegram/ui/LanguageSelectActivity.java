/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class LanguageSelectActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
	private ListAdapter listAdapter;
	private RecyclerListView listView;
	private ListAdapter searchListViewAdapter;
	private EmptyTextProgressView emptyView;
	private boolean searching;
	private ArrayList<LocaleController.LocaleInfo> searchResult;
	private ArrayList<LocaleController.LocaleInfo> sortedLanguages;
	private ArrayList<LocaleController.LocaleInfo> unofficialLanguages;

	@Override
	public boolean onFragmentCreate() {
		fillLanguages();
		LocaleController.getInstance().loadRemoteLanguages(currentAccount, false);
		NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
		return super.onFragmentCreate();
	}

	@Override
	public void onFragmentDestroy() {
		super.onFragmentDestroy();
		NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
	}

	@Override
	public View createView(@NonNull Context context) {
		searching = false;

		actionBar.setBackButtonImage(R.drawable.ic_back_arrow);
		actionBar.setAllowOverlayTitle(true);
		actionBar.setTitle(context.getString(R.string.Language));

		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					finishFragment();
				}
			}
		});

		ActionBarMenu menu = actionBar.createMenu();
		ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_search_menu).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
			@Override
			public void onSearchExpand() {
				searching = true;
			}

			@Override
			public void onSearchCollapse() {
				search(null);
				searching = false;
				if (listView != null) {
					emptyView.setVisibility(View.GONE);
					listView.setAdapter(listAdapter);
				}
			}

			@Override
			public void onTextChanged(EditText editText) {
				String text = editText.getText().toString();
				search(text);
				if (text.length() != 0) {
					if (listView != null) {
						listView.setAdapter(searchListViewAdapter);
					}
				}
				else {
					searching = false;
					if (listView != null) {
						emptyView.setVisibility(View.GONE);
						listView.setAdapter(listAdapter);
					}
				}
			}
		});
		item.setSearchFieldHint(context.getString(R.string.Search));

		listAdapter = new ListAdapter(context, false);
		searchListViewAdapter = new ListAdapter(context, true);

		fragmentView = new FrameLayout(context);
		fragmentView.setBackgroundResource(R.color.light_background);
		FrameLayout frameLayout = (FrameLayout)fragmentView;

		emptyView = new EmptyTextProgressView(context);
		emptyView.setText(context.getString(R.string.NoResult));
		emptyView.showTextView();
		emptyView.setShowAtCenter(true);
		frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

		listView = new RecyclerListView(context);
		listView.setEmptyView(emptyView);
		listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
		listView.setVerticalScrollBarEnabled(false);
		listView.setAdapter(listAdapter);
		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

		listView.setOnItemClickListener((view, position) -> {
			try {
				if (getParentActivity() == null || parentLayout == null || !(view instanceof TextRadioCell)) {
					return;
				}
				boolean search = listView.getAdapter() == searchListViewAdapter;
				if (!search) {
					position -= 2;
				}
				LocaleController.LocaleInfo localeInfo;
				if (search) {
					localeInfo = searchResult.get(position);
				}
				else if (!unofficialLanguages.isEmpty() && position >= 0 && position < unofficialLanguages.size()) {
					localeInfo = unofficialLanguages.get(position);
				}
				else {
					if (!unofficialLanguages.isEmpty()) {
						position -= unofficialLanguages.size() + 1;
					}
					localeInfo = sortedLanguages.get(position);
				}
				if (localeInfo != null) {
					LocaleController.LocaleInfo prevLocale = LocaleController.getInstance().getCurrentLocaleInfo();
					LocaleController.getInstance().applyLanguage(localeInfo, true, false, false, true, currentAccount);
					parentLayout.rebuildAllFragmentViews(false, false);

					String langCode = localeInfo.pluralLangCode, prevLangCode = prevLocale.pluralLangCode;
					SharedPreferences preferences = MessagesController.getGlobalMainSettings();
					HashSet<String> selectedLanguages = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
					HashSet<String> newSelectedLanguages = new HashSet<>(selectedLanguages);

					if (selectedLanguages.contains(langCode)) {
						newSelectedLanguages.removeIf(s -> s != null && s.equals(langCode));
						if (!selectedLanguages.contains(prevLangCode)) {
							newSelectedLanguages.add(prevLangCode);
						}
					}
					preferences.edit().putStringSet("translate_button_restricted_languages", newSelectedLanguages).commit();

					finishFragment();
				}
			}
			catch (Exception e) {
				FileLog.e(e);
			}
		});

		listView.setOnItemLongClickListener((view, position) -> {
			try {
				if (getParentActivity() == null || parentLayout == null || !(view instanceof TextRadioCell)) {
					return false;
				}
				boolean search = listView.getAdapter() == searchListViewAdapter;
				if (!search) {
					position -= 2;
				}
				LocaleController.LocaleInfo localeInfo;
				if (search) {
					localeInfo = searchResult.get(position);
				}
				else if (!unofficialLanguages.isEmpty() && position >= 0 && position < unofficialLanguages.size()) {
					localeInfo = unofficialLanguages.get(position);
				}
				else {
					if (!unofficialLanguages.isEmpty()) {
						position -= unofficialLanguages.size() + 1;
					}
					localeInfo = sortedLanguages.get(position);
				}
				if (localeInfo == null || localeInfo.pathToFile == null || localeInfo.isRemote() && localeInfo.serverIndex != Integer.MAX_VALUE) {
					return false;
				}
				final LocaleController.LocaleInfo finalLocaleInfo = localeInfo;
				AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
				builder.setTitle(context.getString(R.string.DeleteLocalizationTitle));
				builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DeleteLocalizationText", R.string.DeleteLocalizationText, localeInfo.name)));
				builder.setPositiveButton(context.getString(R.string.Delete), (dialogInterface, i) -> {
					if (LocaleController.getInstance().deleteLanguage(finalLocaleInfo, currentAccount)) {
						fillLanguages();
						if (searchResult != null) {
							searchResult.remove(finalLocaleInfo);
						}
						if (listAdapter != null) {
							listAdapter.notifyDataSetChanged();
						}
						if (searchListViewAdapter != null) {
							searchListViewAdapter.notifyDataSetChanged();
						}
					}
				});
				builder.setNegativeButton(context.getString(R.string.Cancel), null);
				AlertDialog alertDialog = builder.create();
				showDialog(alertDialog);
				TextView button = (TextView)alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
				if (button != null) {
					button.setTextColor(context.getColor(R.color.purple));
				}
			}
			catch (Exception e) {
				FileLog.e(e);
			}
			return true;
		});

		listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
				}
			}
		});

		return fragmentView;
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.suggestedLangpack) {
			if (listAdapter != null) {
				fillLanguages();
				AndroidUtilities.runOnUIThread(() -> listAdapter.notifyDataSetChanged());
			}
		}
	}

	private void fillLanguages() {
		final LocaleController.LocaleInfo currentLocale = LocaleController.getInstance().getCurrentLocaleInfo();
		Comparator<LocaleController.LocaleInfo> comparator = (o, o2) -> {
			if (o == currentLocale) {
				return -1;
			}
			else if (o2 == currentLocale) {
				return 1;
			}
			else if (o.serverIndex == o2.serverIndex) {
				return o.name.compareTo(o2.name);
			}
			if (o.serverIndex > o2.serverIndex) {
				return 1;
			}
			else if (o.serverIndex < o2.serverIndex) {
				return -1;
			}
			return 0;
		};

		sortedLanguages = new ArrayList<>();
		unofficialLanguages = new ArrayList<>(LocaleController.getInstance().unofficialLanguages);

		ArrayList<LocaleController.LocaleInfo> arrayList = LocaleController.getInstance().languages;
		for (int a = 0, size = arrayList.size(); a < size; a++) {
			LocaleController.LocaleInfo info = arrayList.get(a);
			if (info.serverIndex != Integer.MAX_VALUE) {
				sortedLanguages.add(info);
			}
			else {
				unofficialLanguages.add(info);
			}
		}
		Collections.sort(sortedLanguages, comparator);
		Collections.sort(unofficialLanguages, comparator);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
	}

	public void search(final String query) {
		if (query == null) {
			searching = false;
			searchResult = null;
			if (listView != null) {
				emptyView.setVisibility(View.GONE);
				listView.setAdapter(listAdapter);
			}
		}
		else {
//            try {
//                if (searchTimer != null) {
//                    searchTimer.cancel();
//                }
//            } catch (Exception e) {
//                FileLog.e(e);
//            }
//            searchTimer = new Timer();
//            searchTimer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                try {
//                    searchTimer.cancel();
//                    searchTimer = null;
//                } catch (Exception e) {
//                    FileLog.e(e);
//                }
			processSearch(query);
//                }
//            }, 100, 300);
		}
	}

	private void processSearch(final String query) {
		Utilities.searchQueue.postRunnable(() -> {

			String q = query.trim().toLowerCase();
			if (q.length() == 0) {
				updateSearchResults(new ArrayList<>());
				return;
			}
			ArrayList<LocaleController.LocaleInfo> resultArray = new ArrayList<>();

			for (int a = 0, N = unofficialLanguages.size(); a < N; a++) {
				LocaleController.LocaleInfo c = unofficialLanguages.get(a);
				if (c.name.toLowerCase().startsWith(query) || c.nameEnglish.toLowerCase().startsWith(query)) {
					resultArray.add(c);
				}
			}

			for (int a = 0, N = sortedLanguages.size(); a < N; a++) {
				LocaleController.LocaleInfo c = sortedLanguages.get(a);
				if (c.name.toLowerCase().startsWith(query) || c.nameEnglish.toLowerCase().startsWith(query)) {
					resultArray.add(c);
				}
			}

			updateSearchResults(resultArray);
		});
	}

	private void updateSearchResults(final ArrayList<LocaleController.LocaleInfo> arrCounties) {
		AndroidUtilities.runOnUIThread(() -> {
			searchResult = arrCounties;
			searchListViewAdapter.notifyDataSetChanged();
		});
	}

	private class TranslateSettings extends LinearLayout {
		private SharedPreferences preferences;
		private final HeaderCell header;
		private final TextCheckCell showButtonCheck;
		private final TextSettingsCell doNotTranslateCell;
		private final TextInfoPrivacyCell info;
		private final TextInfoPrivacyCell info2;
		private ValueAnimator doNotTranslateCellAnimation = null;
		private SharedPreferences.OnSharedPreferenceChangeListener listener;

		public TranslateSettings(Context context) {
			super(context);
			setFocusable(false);

			setOrientation(VERTICAL);

			preferences = MessagesController.getGlobalMainSettings();

			header = new HeaderCell(context);
			header.setFocusable(true);
			header.setBackgroundColor(context.getColor(R.color.background));
			header.setText(context.getString(R.string.TranslateMessages));
			header.setContentDescription(context.getString(R.string.TranslateMessages));
			addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

			boolean value = getValue();
			showButtonCheck = new TextCheckCell(context);
			showButtonCheck.setBackground(Theme.AdaptiveRipple.filledRect(context.getColor(R.color.background)));
			showButtonCheck.setTextAndCheck(context.getString(R.string.ShowTranslateButton), value, value);
			showButtonCheck.setOnClickListener(e -> preferences.edit().putBoolean("translate_button", !getValue()).commit());
			addView(showButtonCheck, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

			doNotTranslateCell = new TextSettingsCell(context);
			doNotTranslateCell.setBackground(Theme.AdaptiveRipple.filledRect(context.getColor(R.color.background)));
			doNotTranslateCell.setOnClickListener(e -> {
				presentFragment(new RestrictedLanguagesSelectActivity());
				update();
			});
			doNotTranslateCell.setClickable(value && LanguageDetector.hasSupport());
			doNotTranslateCell.setAlpha(value && LanguageDetector.hasSupport() ? 1f : 0f);
			addView(doNotTranslateCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

			info = new TextInfoPrivacyCell(context);
			info.setTopPadding(11);
			info.setBottomPadding(16);
			info.setFocusable(true);
			info.setText(context.getString(R.string.TranslateMessagesInfo1));
			info.setContentDescription(context.getString(R.string.TranslateMessagesInfo1));
			addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

			info2 = new TextInfoPrivacyCell(context);
			info2.setTopPadding(0);
			info2.setBottomPadding(16);
			info2.setFocusable(true);
			info2.setText(context.getString(R.string.TranslateMessagesInfo2));
			info2.setContentDescription(context.getString(R.string.TranslateMessagesInfo2));
			info2.setAlpha(value ? 0f : 1f);

			addView(info2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

			updateHeight();
			update();
		}

		private boolean getValue() {
			return preferences.getBoolean("translate_button", false);
		}

		private ArrayList<String> getRestrictedLanguages() {
			String currentLang = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode;
			ArrayList<String> langCodes = new ArrayList<>(RestrictedLanguagesSelectActivity.getRestrictedLanguages());
			if (!langCodes.contains(currentLang)) {
				langCodes.add(currentLang);
			}
			return langCodes;
		}

		public void update() {
			boolean value = getValue() && LanguageDetector.hasSupport();

			showButtonCheck.setChecked(getValue());

			if (doNotTranslateCellAnimation != null) {
				doNotTranslateCellAnimation.cancel();
			}

			showButtonCheck.setDivider(value);
			ArrayList<String> langCodes = getRestrictedLanguages();
			String doNotTranslateCellValue = null;
			if (langCodes.size() == 1) {
				try {
					doNotTranslateCellValue = LocaleController.getInstance().getLanguageFromDict(langCodes.get(0)).name;
				}
				catch (Exception e) {
					// unused
				}
			}
			if (doNotTranslateCellValue == null) {
				doNotTranslateCellValue = String.format(LocaleController.getPluralString("Languages", getRestrictedLanguages().size()), getRestrictedLanguages().size());
			}
			doNotTranslateCell.setTextAndValue(getContext().getString(R.string.DoNotTranslate), doNotTranslateCellValue, false);
			doNotTranslateCell.setClickable(value);

			info2.setVisibility(View.VISIBLE);
			doNotTranslateCellAnimation = ValueAnimator.ofFloat(doNotTranslateCell.getAlpha(), value ? 1f : 0f);
			doNotTranslateCellAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
			doNotTranslateCellAnimation.addUpdateListener(a -> {
				float t = (float)a.getAnimatedValue();
				doNotTranslateCell.setAlpha(t);
				doNotTranslateCell.setTranslationY(-AndroidUtilities.dp(8) * (1f - t));
				info.setTranslationY(-doNotTranslateCell.getHeight() * (1f - t));
				info2.setAlpha(1f - t);
				info2.setTranslationY(-doNotTranslateCell.getHeight() * (1f - t));
			});
			doNotTranslateCellAnimation.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					super.onAnimationEnd(animation);
					if (doNotTranslateCell.getAlpha() > .5) {
						info2.setVisibility(View.GONE);
					}
					else {
						info2.setVisibility(View.VISIBLE);
					}
				}
			});
			doNotTranslateCellAnimation.setDuration((long)(Math.abs(doNotTranslateCell.getAlpha() - (value ? 1f : 0f)) * 200));
			doNotTranslateCellAnimation.start();
		}

		@Override
		protected void onConfigurationChanged(Configuration newConfig) {
			super.onConfigurationChanged(newConfig);
			updateHeight();
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			updateHeight();
			super.onLayout(changed, l, t, r, b);
		}

		void updateHeight() {
			header.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
			showButtonCheck.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
			doNotTranslateCell.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
			info.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
			info2.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);

			int newHeight = searching ? 0 : height();
			if (getLayoutParams() == null) {
				setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, newHeight));
			}
			else if (getLayoutParams().height != newHeight) {
				RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams)getLayoutParams();
				lp.height = newHeight;
				setLayoutParams(lp);
			}
		}

		int height() {
			return Math.max(AndroidUtilities.dp(40), header.getMeasuredHeight()) + Math.max(AndroidUtilities.dp(50), showButtonCheck.getMeasuredHeight()) + Math.max(Math.max(AndroidUtilities.dp(50), doNotTranslateCell.getMeasuredHeight()), (info2.getMeasuredHeight() <= 0 ? AndroidUtilities.dp(51) : info2.getMeasuredHeight())) + (info.getMeasuredHeight() <= 0 ? AndroidUtilities.dp(62) : info.getMeasuredHeight());/* + header2.getHeight()*/
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			updateHeight();
		}

		@Override
		protected void onAttachedToWindow() {
			super.onAttachedToWindow();
			update();
			preferences.registerOnSharedPreferenceChangeListener(listener = (sharedPreferences, s) -> {
				preferences = sharedPreferences;
				update();
			});
			updateHeight();
		}

		@Override
		protected void onDetachedFromWindow() {
			super.onDetachedFromWindow();
			preferences.unregisterOnSharedPreferenceChangeListener(listener);
		}
	}

	private class ListAdapter extends RecyclerListView.SelectionAdapter {

		private final Context mContext;
		private final boolean search;

		public ListAdapter(Context context, boolean isSearch) {
			mContext = context;
			search = isSearch;
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder) {
			return holder.getItemViewType() == 0;
		}

		@Override
		public int getItemCount() {
			if (search) {
				if (searchResult == null) {
					return 0;
				}
				return searchResult.size();
			}
			else {
				int count = sortedLanguages.size();
				if (count != 0) {
					count++;
				}
				if (!unofficialLanguages.isEmpty()) {
					count += unofficialLanguages.size() + 1;
				}
				return 2 + count;
			}
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view;
			switch (viewType) {
				case 0: {
//                    view = new LanguageCell(mContext, false);
					view = new TextRadioCell(mContext);
					view.setBackgroundColor(mContext.getColor(R.color.background));
					break;
				}
				case 2:
					view = new TranslateSettings(mContext);
					break;
				case 3:
					HeaderCell header = new HeaderCell(mContext);
					header.setBackgroundColor(mContext.getColor(R.color.background));
					header.setText(mContext.getString(R.string.Language));
					view = header;
					break;
				case 1:
				default: {
					view = new ShadowSectionCell(mContext);
					break;
				}
			}
			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
			switch (holder.getItemViewType()) {
				case 0: {
					if (!search) {
						position -= 2;
					}
//                    LanguageCell textSettingsCell = (LanguageCell) holder.itemView;
					TextRadioCell textSettingsCell = (TextRadioCell)holder.itemView;
					LocaleController.LocaleInfo localeInfo;
					boolean last;
					if (search) {
						localeInfo = searchResult.get(position);
						last = position == searchResult.size() - 1;
					}
					else if (!unofficialLanguages.isEmpty() && position >= 0 && position < unofficialLanguages.size()) {
						localeInfo = unofficialLanguages.get(position);
						last = position == unofficialLanguages.size() - 1;
					}
					else {
						if (!unofficialLanguages.isEmpty()) {
							position -= unofficialLanguages.size() + 1;
						}
						localeInfo = sortedLanguages.get(position);
						last = position == sortedLanguages.size() - 1;
					}
					if (localeInfo.isLocal()) {
						textSettingsCell.setTextAndValueAndCheck(String.format("%1$s (%2$s)", localeInfo.name, mContext.getString(R.string.LanguageCustom)), localeInfo.nameEnglish, false, false, !last);
					}
					else {
						textSettingsCell.setTextAndValueAndCheck(localeInfo.name, localeInfo.nameEnglish, false, false, !last);
					}
					textSettingsCell.setChecked(localeInfo == LocaleController.getInstance().getCurrentLocaleInfo());
					break;
				}
				case 1: {
					if (!search) {
						position--;
					}
					ShadowSectionCell sectionCell = (ShadowSectionCell)holder.itemView;
					if (!unofficialLanguages.isEmpty() && position == unofficialLanguages.size()) {
						sectionCell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
					}
					else {
						sectionCell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
					}
					break;
				}
				case 2: {
					TranslateSettings translateSettings = (TranslateSettings)holder.itemView;
					translateSettings.setVisibility(searching ? View.GONE : View.VISIBLE);
					translateSettings.updateHeight();
				}
			}
		}

		@Override
		public int getItemViewType(int i) {
			if (!search) {
				i -= 2;
			}
			if (i == -2) {
				return 2;
			}
			if (i == -1) {
				return 3;
			}
			if (search) {
				return 0;
			}
			if (!unofficialLanguages.isEmpty() && (i == unofficialLanguages.size() || i == unofficialLanguages.size() + sortedLanguages.size() + 1) || unofficialLanguages.isEmpty() && i == sortedLanguages.size()) {
				return 1;
			}
			return 0;
		}
	}
}
