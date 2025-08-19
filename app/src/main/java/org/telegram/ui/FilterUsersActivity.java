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
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.Chat;
import org.telegram.tgnet.TLRPC.User;
import org.telegram.tgnet.TLRPCExtensions;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

@SuppressLint("NotifyDataSetChanged")
public class FilterUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, View.OnClickListener {
	private ScrollView scrollView;
	private SpansContainer spansContainer;
	private EditTextBoldCursor editText;
	private RecyclerListView listView;
	private EmptyTextProgressView emptyView;
	private GroupCreateAdapter adapter;
	private FilterUsersActivityDelegate delegate;
	private ImageView floatingButton;
	private boolean ignoreScrollEvent;
	private int selectedCount;
	private int containerHeight;
	private final boolean isInclude;
	private int filterFlags;
	private final ArrayList<Long> initialIds;
	private boolean searchWas;
	private boolean searching;
	private final LongSparseArray<GroupCreateSpan> selectedContacts = new LongSparseArray<>();
	private final ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
	private GroupCreateSpan currentDeletingSpan;
	private int fieldY;
	private final static int done_button = 1;

	private static class ItemDecoration extends RecyclerView.ItemDecoration {
		private boolean single;
		private int skipRows;

		public void setSingle(boolean value) {
			single = value;
		}

		@Override
		public void onDraw(@NonNull Canvas canvas, RecyclerView parent, @NonNull RecyclerView.State state) {
			int width = parent.getWidth();
			int top;
			int childCount = parent.getChildCount() - (single ? 0 : 1);
			for (int i = 0; i < childCount; i++) {
				View child = parent.getChildAt(i);
				View nextChild = i < childCount - 1 ? parent.getChildAt(i + 1) : null;
				int position = parent.getChildAdapterPosition(child);
				if (position < skipRows || child instanceof GraySectionCell || nextChild instanceof GraySectionCell) {
					continue;
				}
				top = child.getBottom();
				canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(72), top, width - (LocaleController.isRTL ? AndroidUtilities.dp(72) : 0), top, Theme.dividerPaint);
			}
		}

		@Override
		public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
			super.getItemOffsets(outRect, view, parent, state);
            /*int position = parent.getChildAdapterPosition(view);
            if (position == 0 || !searching && position == 1) {
                return;
            }*/
			outRect.top = 1;
		}
	}

	public interface FilterUsersActivityDelegate {
		void didSelectChats(ArrayList<Long> ids, int flags);
	}

	private class SpansContainer extends ViewGroup {

		private AnimatorSet currentAnimation;
		private boolean animationStarted;
		private final ArrayList<Animator> animators = new ArrayList<>();
		private View addingSpan;
		private View removingSpan;

		public SpansContainer(Context context) {
			super(context);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int count = getChildCount();
			int width = MeasureSpec.getSize(widthMeasureSpec);
			int maxWidth = width - AndroidUtilities.dp(26);
			int currentLineWidth = 0;
			int y = AndroidUtilities.dp(10);
			int allCurrentLineWidth = 0;
			int allY = AndroidUtilities.dp(10);
			int x;
			for (int a = 0; a < count; a++) {
				View child = getChildAt(a);
				if (!(child instanceof GroupCreateSpan)) {
					continue;
				}
				child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
				if (child != removingSpan && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
					y += child.getMeasuredHeight() + AndroidUtilities.dp(8);
					currentLineWidth = 0;
				}
				if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
					allY += child.getMeasuredHeight() + AndroidUtilities.dp(8);
					allCurrentLineWidth = 0;
				}
				x = AndroidUtilities.dp(13) + currentLineWidth;
				if (!animationStarted) {
					if (child == removingSpan) {
						child.setTranslationX(AndroidUtilities.dp(13) + allCurrentLineWidth);
						child.setTranslationY(allY);
					}
					else if (removingSpan != null) {
						if (child.getTranslationX() != x) {
							animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_X, x));
						}
						if (child.getTranslationY() != y) {
							animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, y));
						}
					}
					else {
						child.setTranslationX(x);
						child.setTranslationY(y);
					}
				}
				if (child != removingSpan) {
					currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
				}
				allCurrentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
			}
			int minWidth;
			if (AndroidUtilities.isTablet()) {
				minWidth = AndroidUtilities.dp(530 - 26 - 18 - 57 * 2) / 3;
			}
			else {
				minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(26 + 18 + 57 * 2)) / 3;
			}
			if (maxWidth - currentLineWidth < minWidth) {
				currentLineWidth = 0;
				y += AndroidUtilities.dp(32 + 8);
			}
			if (maxWidth - allCurrentLineWidth < minWidth) {
				allY += AndroidUtilities.dp(32 + 8);
			}
			editText.measure(MeasureSpec.makeMeasureSpec(maxWidth - currentLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
			if (!animationStarted) {
				int currentHeight = allY + AndroidUtilities.dp(32 + 10);
				int fieldX = currentLineWidth + AndroidUtilities.dp(16);
				fieldY = y;
				if (currentAnimation != null) {
					int resultHeight = y + AndroidUtilities.dp(32 + 10);
					if (containerHeight != resultHeight) {
						animators.add(ObjectAnimator.ofInt(FilterUsersActivity.this, "containerHeight", resultHeight));
					}
					if (editText.getTranslationX() != fieldX) {
						animators.add(ObjectAnimator.ofFloat(editText, View.TRANSLATION_X, fieldX));
					}
					if (editText.getTranslationY() != fieldY) {
						animators.add(ObjectAnimator.ofFloat(editText, View.TRANSLATION_Y, fieldY));
					}
					editText.setAllowDrawCursor(false);
					currentAnimation.playTogether(animators);
					currentAnimation.start();
					animationStarted = true;
				}
				else {
					containerHeight = currentHeight;
					editText.setTranslationX(fieldX);
					editText.setTranslationY(fieldY);
				}
			}
			else if (currentAnimation != null) {
				if (!ignoreScrollEvent && removingSpan == null) {
					editText.bringPointIntoView(editText.getSelectionStart());
				}
			}
			setMeasuredDimension(width, containerHeight);
		}

		@Override
		protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
			int count = getChildCount();
			for (int a = 0; a < count; a++) {
				View child = getChildAt(a);
				child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
			}
		}

		public void addSpan(final GroupCreateSpan span, boolean animated) {
			allSpans.add(span);
			long uid = span.uid;
			if (uid > Integer.MIN_VALUE + 7) {
				selectedCount++;
			}
			selectedContacts.put(uid, span);

			editText.setHintVisible(false);
			if (currentAnimation != null) {
				currentAnimation.setupEndValues();
				currentAnimation.cancel();
			}
			animationStarted = false;
			if (animated) {
				currentAnimation = new AnimatorSet();
				currentAnimation.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animator) {
						addingSpan = null;
						currentAnimation = null;
						animationStarted = false;
						editText.setAllowDrawCursor(true);
					}
				});
				currentAnimation.setDuration(150);
				addingSpan = span;
				animators.clear();
				animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_X, 0.01f, 1.0f));
				animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_Y, 0.01f, 1.0f));
				animators.add(ObjectAnimator.ofFloat(addingSpan, View.ALPHA, 0.0f, 1.0f));
			}
			addView(span);
		}

		public void removeSpan(final GroupCreateSpan span) {
			ignoreScrollEvent = true;
			long uid = span.uid;
			if (uid > Integer.MIN_VALUE + 7) {
				selectedCount--;
			}
			selectedContacts.remove(uid);
			allSpans.remove(span);
			span.setOnClickListener(null);

			if (currentAnimation != null) {
				currentAnimation.setupEndValues();
				currentAnimation.cancel();
			}
			animationStarted = false;
			currentAnimation = new AnimatorSet();
			currentAnimation.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animator) {
					removeView(span);
					removingSpan = null;
					currentAnimation = null;
					animationStarted = false;
					editText.setAllowDrawCursor(true);
					if (allSpans.isEmpty()) {
						editText.setHintVisible(true);
					}
				}
			});
			currentAnimation.setDuration(150);
			removingSpan = span;
			animators.clear();
			animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_X, 1.0f, 0.01f));
			animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_Y, 1.0f, 0.01f));
			animators.add(ObjectAnimator.ofFloat(removingSpan, View.ALPHA, 1.0f, 0.0f));
			requestLayout();
		}
	}

	public FilterUsersActivity(boolean include, ArrayList<Long> arrayList, int flags) {
		super();
		isInclude = include;
		filterFlags = flags;
		initialIds = arrayList;
	}

	@Override
	public boolean onFragmentCreate() {
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsDidLoad);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidCreated);
		return super.onFragmentCreate();
	}

	@Override
	public void onFragmentDestroy() {
		super.onFragmentDestroy();
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsDidLoad);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidCreated);
	}

	@Override
	public void onClick(View v) {
		GroupCreateSpan span = (GroupCreateSpan)v;
		if (span.isDeleting()) {
			currentDeletingSpan = null;
			spansContainer.removeSpan(span);
			if (span.uid == Integer.MIN_VALUE) {
				filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
			}
			else if (span.uid == Integer.MIN_VALUE + 1) {
				filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
			}
			else if (span.uid == Integer.MIN_VALUE + 2) {
				filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_GROUPS;
			}
			else if (span.uid == Integer.MIN_VALUE + 3) {
				filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
			}
			else if (span.uid == Integer.MIN_VALUE + 4) {
				filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_BOTS;
			}
			else if (span.uid == Integer.MIN_VALUE + 5) {
				filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
			}
			else if (span.uid == Integer.MIN_VALUE + 6) {
				filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
			}
			else if (span.uid == Integer.MIN_VALUE + 7) {
				filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
			}
			updateHint();
			checkVisibleRows();
		}
		else {
			if (currentDeletingSpan != null) {
				currentDeletingSpan.cancelDeleteAnimation();
			}
			currentDeletingSpan = span;
			span.startDeleteAnimation();
		}
	}

	@Override
	public View createView(@NonNull Context context) {
		searching = false;
		searchWas = false;
		allSpans.clear();
		selectedContacts.clear();
		currentDeletingSpan = null;

		actionBar.setBackButtonImage(R.drawable.ic_back_arrow);
		actionBar.setAllowOverlayTitle(true);
		if (isInclude) {
			actionBar.setTitle(context.getString(R.string.FilterAlwaysShow));
		}
		else {
			actionBar.setTitle(context.getString(R.string.FilterNeverShow));
		}
		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					finishFragment();
				}
				else if (id == done_button) {
					onDonePressed(true);
				}
			}
		});

		fragmentView = new ViewGroup(context) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				int width = MeasureSpec.getSize(widthMeasureSpec);
				int height = MeasureSpec.getSize(heightMeasureSpec);
				setMeasuredDimension(width, height);
				int maxSize;
				if (AndroidUtilities.isTablet() || height > width) {
					maxSize = AndroidUtilities.dp(144);
				}
				else {
					maxSize = AndroidUtilities.dp(56);
				}

				scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST));
				listView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
				emptyView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
				if (floatingButton != null) {
					int w = AndroidUtilities.dp(56);
					floatingButton.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY));
				}
			}

			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
				listView.layout(0, scrollView.getMeasuredHeight(), listView.getMeasuredWidth(), scrollView.getMeasuredHeight() + listView.getMeasuredHeight());
				emptyView.layout(0, scrollView.getMeasuredHeight(), emptyView.getMeasuredWidth(), scrollView.getMeasuredHeight() + emptyView.getMeasuredHeight());

				if (floatingButton != null) {
					int l = LocaleController.isRTL ? AndroidUtilities.dp(14) : (right - left) - AndroidUtilities.dp(14) - floatingButton.getMeasuredWidth();
					int t = bottom - top - AndroidUtilities.dp(14) - floatingButton.getMeasuredHeight();
					floatingButton.layout(l, t, l + floatingButton.getMeasuredWidth(), t + floatingButton.getMeasuredHeight());
				}
			}

			@Override
			protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
				boolean result = super.drawChild(canvas, child, drawingTime);
				if (child == listView || child == emptyView) {
					parentLayout.drawHeaderShadow(canvas, scrollView.getMeasuredHeight());
				}
				return result;
			}
		};
		ViewGroup frameLayout = (ViewGroup)fragmentView;

		scrollView = new ScrollView(context) {
			@Override
			public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
				if (ignoreScrollEvent) {
					ignoreScrollEvent = false;
					return false;
				}
				rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
				rectangle.top += fieldY + AndroidUtilities.dp(20);
				rectangle.bottom += fieldY + AndroidUtilities.dp(50);
				return super.requestChildRectangleOnScreen(child, rectangle, immediate);
			}
		};
		scrollView.setVerticalScrollBarEnabled(false);
		AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, context.getColor(R.color.background));
		frameLayout.addView(scrollView);

		spansContainer = new SpansContainer(context);
		scrollView.addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
		spansContainer.setOnClickListener(v -> {
			editText.clearFocus();
			editText.requestFocus();
			AndroidUtilities.showKeyboard(editText);
		});

		editText = new EditTextBoldCursor(context) {
			@Override
			public boolean onTouchEvent(MotionEvent event) {
				if (currentDeletingSpan != null) {
					currentDeletingSpan.cancelDeleteAnimation();
					currentDeletingSpan = null;
				}
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (!AndroidUtilities.showKeyboard(this)) {
						clearFocus();
						requestFocus();
					}
				}
				return super.onTouchEvent(event);
			}
		};
		editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		editText.setHintColor(context.getColor(R.color.hint));
		editText.setTextColor(context.getColor(R.color.text));
		editText.setCursorColor(context.getColor(R.color.text));
		editText.setCursorWidth(1.5f);
		editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		editText.setSingleLine(true);
		editText.setBackground(null);
		editText.setVerticalScrollBarEnabled(false);
		editText.setHorizontalScrollBarEnabled(false);
		editText.setTextIsSelectable(false);
		editText.setPadding(0, 0, 0, 0);
		editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
		spansContainer.addView(editText);
		editText.setHintText(context.getString(R.string.SearchForPeopleAndGroups));

		editText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return false;
			}

			public void onDestroyActionMode(ActionMode mode) {

			}

			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				return false;
			}

			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				return false;
			}
		});
		//editText.setOnEditorActionListener((v, actionId, event) -> actionId == EditorInfo.IME_ACTION_DONE && onDonePressed(true));
		editText.setOnKeyListener(new View.OnKeyListener() {

			private boolean wasEmpty;

			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_DEL) {
					if (event.getAction() == KeyEvent.ACTION_DOWN) {
						wasEmpty = editText.length() == 0;
					}
					else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty && !allSpans.isEmpty()) {
						GroupCreateSpan span = allSpans.get(allSpans.size() - 1);
						spansContainer.removeSpan(span);
						if (span.uid == Integer.MIN_VALUE) {
							filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
						}
						else if (span.uid == Integer.MIN_VALUE + 1) {
							filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
						}
						else if (span.uid == Integer.MIN_VALUE + 2) {
							filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_GROUPS;
						}
						else if (span.uid == Integer.MIN_VALUE + 3) {
							filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
						}
						else if (span.uid == Integer.MIN_VALUE + 4) {
							filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_BOTS;
						}
						else if (span.uid == Integer.MIN_VALUE + 5) {
							filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
						}
						else if (span.uid == Integer.MIN_VALUE + 6) {
							filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
						}
						else if (span.uid == Integer.MIN_VALUE + 7) {
							filterFlags &= ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
						}
						updateHint();
						checkVisibleRows();
						return true;
					}
				}
				return false;
			}
		});
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

			}

			@Override
			public void afterTextChanged(Editable editable) {
				if (editText.length() != 0) {
					if (!adapter.searching) {
						searching = true;
						searchWas = true;
						adapter.setSearching(true);
						listView.setFastScrollVisible(false);
						listView.setVerticalScrollBarEnabled(true);
						emptyView.setText(context.getString(R.string.NoResult));
						emptyView.showProgress();
					}
					adapter.searchDialogs(editText.getText().toString());
				}
				else {
					closeSearch();
				}
			}
		});

		emptyView = new EmptyTextProgressView(context);
		if (ContactsController.getInstance(currentAccount).isLoadingContacts()) {
			emptyView.showProgress();
		}
		else {
			emptyView.showTextView();
		}
		emptyView.setShowAtCenter(true);
		emptyView.setText(context.getString(R.string.NoContacts));
		frameLayout.addView(emptyView);

		LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

		listView = new RecyclerListView(context);
		listView.setFastScrollEnabled(RecyclerListView.LETTER_TYPE);
		listView.setEmptyView(emptyView);
		listView.setAdapter(adapter = new GroupCreateAdapter(context));
		listView.setLayoutManager(linearLayoutManager);
		listView.setVerticalScrollBarEnabled(false);
		listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
		listView.addItemDecoration(new ItemDecoration());
		frameLayout.addView(listView);
		listView.setOnItemClickListener((view, position) -> {
			if (view instanceof GroupCreateUserCell cell) {
				Object object = cell.getObject();
				long id;
				if (object instanceof String) {
					int flag;
					if (isInclude) {
						if (position == 1) {
							flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
							id = Integer.MIN_VALUE;
						}
						else if (position == 2) {
							flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
							id = Integer.MIN_VALUE + 1;
						}
						else if (position == 3) {
							flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
							id = Integer.MIN_VALUE + 2;
						}
						else if (position == 4) {
							flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
							id = Integer.MIN_VALUE + 3;
						}
						else {
							flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
							id = Integer.MIN_VALUE + 4;
						}
					}
					else {
						if (position == 1) {
							flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
							id = Integer.MIN_VALUE + 5;
						}
						else if (position == 2) {
							flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
							id = Integer.MIN_VALUE + 6;
						}
						else {
							flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
							id = Integer.MIN_VALUE + 7;
						}
					}
					if (cell.isChecked()) {
						filterFlags &= ~flag;
					}
					else {
						filterFlags |= flag;
					}
				}
				else {
					if (object instanceof User) {
						id = ((User)object).id;
					}
					else if (object instanceof TLRPC.Chat) {
						id = -((TLRPC.Chat)object).id;
					}
					else {
						return;
					}
				}
				boolean exists;
				if (exists = selectedContacts.indexOfKey(id) >= 0) {
					GroupCreateSpan span = selectedContacts.get(id);
					spansContainer.removeSpan(span);
				}
				else {
					if (!(object instanceof String) && (!getUserConfig().isPremium() && selectedCount >= MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitDefault) || selectedCount >= MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitPremium) {
						LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(this, LimitReachedBottomSheet.TYPE_CHATS_IN_FOLDER, currentAccount);
						limitReachedBottomSheet.setCurrentValue(selectedCount);
						showDialog(limitReachedBottomSheet);
						return;
					}
					if (object instanceof User user) {
						MessagesController.getInstance(currentAccount).putUser(user, !searching);
					}
					else if (object instanceof Chat chat) {
						MessagesController.getInstance(currentAccount).putChat(chat, !searching);
					}
					GroupCreateSpan span = new GroupCreateSpan(editText.getContext(), object);
					spansContainer.addSpan(span, true);
					span.setOnClickListener(FilterUsersActivity.this);
				}
				updateHint();
				if (searching || searchWas) {
					AndroidUtilities.showKeyboard(editText);
				}
				else {
					cell.setChecked(!exists, true);
				}
				if (editText.length() > 0) {
					editText.setText(null);
				}
			}
		});
		listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					AndroidUtilities.hideKeyboard(editText);
				}
			}
		});

		floatingButton = new ImageView(context);
		floatingButton.setScaleType(ImageView.ScaleType.CENTER);

		Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), context.getColor(R.color.brand), context.getColor(R.color.darker_brand));
		floatingButton.setBackground(drawable);
		floatingButton.setColorFilter(new PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN));
		floatingButton.setImageResource(R.drawable.floating_check);
		StateListAnimator animator = new StateListAnimator();
		animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
		animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
		floatingButton.setStateListAnimator(animator);
		floatingButton.setOutlineProvider(new ViewOutlineProvider() {
			@SuppressLint("NewApi")
			@Override
			public void getOutline(View view, Outline outline) {
				outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
			}
		});
		frameLayout.addView(floatingButton);
		floatingButton.setOnClickListener(v -> onDonePressed(true));
        /*floatingButton.setVisibility(View.INVISIBLE);
        floatingButton.setScaleX(0.0f);
        floatingButton.setScaleY(0.0f);
        floatingButton.setAlpha(0.0f);*/
		floatingButton.setContentDescription(context.getString(R.string.Next));

		for (int position = 1, N = (isInclude ? 5 : 3); position <= N; position++) {
			int flag;
			Object object;
			if (isInclude) {
				if (position == 1) {
					object = "contacts";
					flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
				}
				else if (position == 2) {
					object = "non_contacts";
					flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
				}
				else if (position == 3) {
					object = "groups";
					flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
				}
				else if (position == 4) {
					object = "channels";
					flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
				}
				else {
					object = "bots";
					flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
				}
			}
			else {
				if (position == 1) {
					object = "muted";
					flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
				}
				else if (position == 2) {
					object = "read";
					flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
				}
				else {
					object = "archived";
					flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
				}
			}
			if ((filterFlags & flag) != 0) {
				GroupCreateSpan span = new GroupCreateSpan(editText.getContext(), object);
				spansContainer.addSpan(span, false);
				span.setOnClickListener(FilterUsersActivity.this);
			}
		}
		if (initialIds != null && !initialIds.isEmpty()) {
			TLObject object;
			for (int a = 0, N = initialIds.size(); a < N; a++) {
				Long id = initialIds.get(a);
				if (id > 0) {
					object = getMessagesController().getUser(id);
				}
				else {
					object = getMessagesController().getChat(-id);
				}
				if (object == null) {
					continue;
				}
				GroupCreateSpan span = new GroupCreateSpan(editText.getContext(), object);
				spansContainer.addSpan(span, false);
				span.setOnClickListener(FilterUsersActivity.this);
			}
		}

		updateHint();
		return fragmentView;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (editText != null) {
			editText.requestFocus();
		}
		AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.contactsDidLoad) {
			if (emptyView != null) {
				emptyView.showTextView();
			}
			if (adapter != null) {
				adapter.notifyDataSetChanged();
			}
		}
		else if (id == NotificationCenter.updateInterfaces) {
			if (listView != null) {
				int mask = (Integer)args[0];
				int count = listView.getChildCount();
				if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
					for (int a = 0; a < count; a++) {
						View child = listView.getChildAt(a);
						if (child instanceof GroupCreateUserCell) {
							((GroupCreateUserCell)child).update(mask);
						}
					}
				}
			}
		}
		else if (id == NotificationCenter.chatDidCreated) {
			removeSelfFromStack();
		}
	}

	@Keep
	public void setContainerHeight(int value) {
		containerHeight = value;
		if (spansContainer != null) {
			spansContainer.requestLayout();
		}
	}

	@Keep
	public int getContainerHeight() {
		return containerHeight;
	}

	private void checkVisibleRows() {
		int count = listView.getChildCount();
		for (int a = 0; a < count; a++) {
			View child = listView.getChildAt(a);
			if (child instanceof GroupCreateUserCell cell) {
				Object object = cell.getObject();
				long id;
				if (object instanceof String str) {
					id = switch (str) {
						case "contacts" -> Integer.MIN_VALUE;
						case "non_contacts" -> Integer.MIN_VALUE + 1;
						case "groups" -> Integer.MIN_VALUE + 2;
						case "channels" -> Integer.MIN_VALUE + 3;
						case "bots" -> Integer.MIN_VALUE + 4;
						case "muted" -> Integer.MIN_VALUE + 5;
						case "read" -> Integer.MIN_VALUE + 6;
						default -> Integer.MIN_VALUE + 7;
					};
				}
				else if (object instanceof User) {
					id = ((User)object).id;
				}
				else if (object instanceof TLRPC.Chat) {
					id = -((TLRPC.Chat)object).id;
				}
				else {
					id = 0;
				}
				if (id != 0) {
					cell.setChecked(selectedContacts.indexOfKey(id) >= 0, true);
					cell.setCheckBoxEnabled(true);
				}
			}
		}
	}

	private boolean onDonePressed(boolean alert) {
        /*if (!doneButtonVisible || selectedContacts.size() == 0) {
            return false;
        }*/
		ArrayList<Long> result = new ArrayList<>();
		for (int a = 0; a < selectedContacts.size(); a++) {
			long uid = selectedContacts.keyAt(a);
			if (uid <= Integer.MIN_VALUE + 7) {
				continue;
			}
			result.add(selectedContacts.keyAt(a));
		}
		if (delegate != null) {
			delegate.didSelectChats(result, filterFlags);
		}
		finishFragment();
		return true;
	}

	private void closeSearch() {
		searching = false;
		searchWas = false;
		adapter.setSearching(false);
		adapter.searchDialogs(null);
		listView.setFastScrollVisible(true);
		listView.setVerticalScrollBarEnabled(false);
		emptyView.setText(getContext().getString(R.string.NoContacts));
	}

	private void updateHint() {
		int limit = getUserConfig().isPremium() ? getMessagesController().dialogFiltersChatsLimitPremium : getMessagesController().dialogFiltersChatsLimitDefault;
		if (selectedCount == 0) {
			actionBar.setSubtitle(LocaleController.formatString("MembersCountZero", R.string.MembersCountZero, LocaleController.formatPluralString("Chats", limit)));
		}
		else {
			actionBar.setSubtitle(String.format(LocaleController.getPluralString("MembersCountSelected", selectedCount), selectedCount, limit));
		}
	}

	public void setDelegate(FilterUsersActivityDelegate filterUsersActivityDelegate) {
		delegate = filterUsersActivityDelegate;
	}

	public class GroupCreateAdapter extends RecyclerListView.FastScrollAdapter {

		private final Context context;
		private ArrayList<Object> searchResult = new ArrayList<>();
		private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
		private final SearchAdapterHelper searchAdapterHelper;
		private Runnable searchRunnable;
		private boolean searching;
		private final ArrayList<TLObject> contacts = new ArrayList<>();
		private final int usersStartRow = isInclude ? 7 : 5;

		public GroupCreateAdapter(Context ctx) {
			context = ctx;

			boolean hasSelf = false;
			ArrayList<TLRPC.Dialog> dialogs = getMessagesController().allDialogs;
			for (int a = 0, N = dialogs.size(); a < N; a++) {
				TLRPC.Dialog dialog = dialogs.get(a);
				if (DialogObject.isEncryptedDialog(dialog.id)) {
					continue;
				}
				if (DialogObject.isUserDialog(dialog.id)) {
					User user = getMessagesController().getUser(dialog.id);
					if (user != null) {
						contacts.add(user);
						if (UserObject.isUserSelf(user)) {
							hasSelf = true;
						}
					}
				}
				else {
					TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
					if (chat != null) {
						contacts.add(chat);
					}
				}
			}
			if (!hasSelf) {
				User user = getMessagesController().getUser(getUserConfig().clientUserId);
				contacts.add(0, user);
			}

			searchAdapterHelper = new SearchAdapterHelper(false);
			searchAdapterHelper.setAllowGlobalResults(false);

			searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
				@Override
				public boolean canApplySearchResults(int searchId) {
					return true;
				}

				@Nullable
				@Override
				public long[] getExcludeUsersIds() {
					return null;
				}

				@Nullable
				@Override
				public LongSparseArray<TLRPC.TLGroupCallParticipant> getExcludeCallParticipants() {
					return null;
				}

				@Nullable
				@Override
				public LongSparseArray<User> getExcludeUsers() {
					return null;
				}

				@Override
				public void onSetHashtags(@Nullable ArrayList<SearchAdapterHelper.HashtagObject> arrayList, @Nullable HashMap<String, SearchAdapterHelper.HashtagObject> hashMap) {
					// unused
				}

				@Override
				public void onDataSetChanged(int searchId) {
					if (searchRunnable == null && !searchAdapterHelper.isSearchInProgress()) {
						emptyView.showTextView();
					}
					notifyDataSetChanged();
				}
			});
		}

		public void setSearching(boolean value) {
			if (searching == value) {
				return;
			}
			searching = value;
			notifyDataSetChanged();
		}

		@Override
		public String getLetter(int position) {
			return null;
		}

		@Override
		public int getItemCount() {
			int count;
			if (searching) {
				count = searchResult.size();
				int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
				int globalCount = searchAdapterHelper.getGlobalSearch().size();
				count += localServerCount + globalCount;
				return count;
			}
			else {
				if (isInclude) {
					count = 7;
				}
				else {
					count = 5;
				}
				count += contacts.size();
			}
			return count;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = switch (viewType) {
				case 1 -> new GroupCreateUserCell(context, 1, 0, true);
				default -> new GraySectionCell(context);
			};
			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
			switch (holder.getItemViewType()) {
				case 1: {
					GroupCreateUserCell cell = (GroupCreateUserCell)holder.itemView;
					Object object;
					CharSequence username = null;
					CharSequence name = null;
					if (searching) {
						int localCount = searchResult.size();
						int globalCount = searchAdapterHelper.getGlobalSearch().size();
						int localServerCount = searchAdapterHelper.getLocalServerSearch().size();

						if (position >= 0 && position < localCount) {
							object = searchResult.get(position);
						}
						else if (position >= localCount && position < localServerCount + localCount) {
							object = searchAdapterHelper.getLocalServerSearch().get(position - localCount);
						}
						else if (position > localCount + localServerCount && position < globalCount + localCount + localServerCount) {
							object = searchAdapterHelper.getGlobalSearch().get(position - localCount - localServerCount);
						}
						else {
							object = null;
						}
						if (object != null) {
							String objectUserName;
							if (object instanceof User) {
								objectUserName = ((User)object).username;
							}
							else {
								objectUserName = ((TLRPC.Chat)object).username;
							}
							if (position < localCount) {
								name = searchResultNames.get(position);
								if (name != null && !TextUtils.isEmpty(objectUserName)) {
									if (name.toString().startsWith("@" + objectUserName)) {
										username = name;
										name = null;
									}
								}
							}
							else if (position > localCount && !TextUtils.isEmpty(objectUserName)) {
								String foundUserName = searchAdapterHelper.getLastFoundUsername();
								if (foundUserName.startsWith("@")) {
									foundUserName = foundUserName.substring(1);
								}
								try {
									int index;
									SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
									spannableStringBuilder.append("@");
									spannableStringBuilder.append(objectUserName);
									if ((index = AndroidUtilities.indexOfIgnoreCase(objectUserName, foundUserName)) != -1) {
										int len = foundUserName.length();
										if (index == 0) {
											len++;
										}
										else {
											index++;
										}
										spannableStringBuilder.setSpan(new ForegroundColorSpan(context.getColor(R.color.brand)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
									}
									username = spannableStringBuilder;
								}
								catch (Exception e) {
									username = objectUserName;
								}
							}
						}
					}
					else {
						if (position < usersStartRow) {
							int flag;
							if (isInclude) {
								if (position == 1) {
									name = context.getString(R.string.FilterContacts);
									object = "contacts";
									flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
								}
								else if (position == 2) {
									name = context.getString(R.string.FilterNonContacts);
									object = "non_contacts";
									flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
								}
								else if (position == 3) {
									name = context.getString(R.string.FilterGroups);
									object = "groups";
									flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
								}
								else if (position == 4) {
									name = context.getString(R.string.FilterChannels);
									object = "channels";
									flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
								}
								else {
									name = context.getString(R.string.FilterBots);
									object = "bots";
									flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
								}
							}
							else {
								if (position == 1) {
									name = context.getString(R.string.FilterMuted);
									object = "muted";
									flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
								}
								else if (position == 2) {
									name = context.getString(R.string.FilterRead);
									object = "read";
									flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
								}
								else {
									name = context.getString(R.string.FilterArchived);
									object = "archived";
									flag = MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
								}
							}
							cell.setObject(object, name, null);
							cell.setChecked((filterFlags & flag) == flag, false);
							cell.setCheckBoxEnabled(true);
							return;
						}
						object = contacts.get(position - usersStartRow);
					}
					long id;
					if (object instanceof User) {
						id = ((User)object).id;
					}
					else if (object instanceof TLRPC.Chat) {
						id = -((TLRPC.Chat)object).id;
					}
					else {
						id = 0;
					}
					if (!searching) {
						StringBuilder builder = new StringBuilder();
						ArrayList<MessagesController.DialogFilter> filters = getMessagesController().dialogFilters;
						for (int a = 0, N = filters.size(); a < N; a++) {
							MessagesController.DialogFilter filter = filters.get(a);
							if (filter.includesDialog(getAccountInstance(), id)) {
								if (builder.length() > 0) {
									builder.append(", ");
								}
								builder.append(filter.name);
							}
						}
						username = builder;
					}
					cell.setObject(object, name, username);
					if (id != 0) {
						cell.setChecked(selectedContacts.indexOfKey(id) >= 0, false);
						cell.setCheckBoxEnabled(true);
					}
					break;
				}
				case 2: {
					GraySectionCell cell = (GraySectionCell)holder.itemView;
					if (position == 0) {
						cell.setText(context.getString(R.string.FilterChatTypes));
					}
					else {
						cell.setText(context.getString(R.string.FilterChats));
					}
					break;
				}
			}
		}

		@Override
		public int getItemViewType(int position) {
			if (searching) {
				return 1;
			}
			else {
				if (isInclude) {
					if (position == 0 || position == 6) {
						return 2;
					}
				}
				else {
					if (position == 0 || position == 4) {
						return 2;
					}
				}
				return 1;
			}
		}

		@Override
		public void getPositionForScrollProgress(@NonNull RecyclerListView listView, float progress, int[] position) {
			position[0] = (int)(getItemCount() * progress);
			position[1] = 0;
		}

		@Override
		public void onViewRecycled(RecyclerView.ViewHolder holder) {
			if (holder.itemView instanceof GroupCreateUserCell) {
				((GroupCreateUserCell)holder.itemView).recycle();
			}
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder) {
			return holder.getItemViewType() == 1;
		}

		public void searchDialogs(final String query) {
			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable);
				searchRunnable = null;
			}
			if (query == null) {
				searchResult.clear();
				searchResultNames.clear();
				searchAdapterHelper.mergeResults(null);
				searchAdapterHelper.queryServerSearch(null, true, true, false, false, false, 0, 0, 0, null);
				notifyDataSetChanged();
			}
			else {
				Utilities.searchQueue.postRunnable(searchRunnable = () -> AndroidUtilities.runOnUIThread(() -> {
					searchAdapterHelper.queryServerSearch(query, true, true, true, true, false, 0, 0, 0, null);
					Utilities.searchQueue.postRunnable(searchRunnable = () -> {
						String search1 = query.trim().toLowerCase();
						if (search1.length() == 0) {
							updateSearchResults(new ArrayList<>(), new ArrayList<>());
							return;
						}
						String search2 = LocaleController.getInstance().getTranslitString(search1);
						if (search1.equals(search2) || search2.length() == 0) {
							search2 = null;
						}
						String[] search = new String[1 + (search2 != null ? 1 : 0)];
						search[0] = search1;
						if (search2 != null) {
							search[1] = search2;
						}

						ArrayList<Object> resultArray = new ArrayList<>();
						ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

						for (int a = 0; a < contacts.size(); a++) {
							TLObject object = contacts.get(a);

							String username;
							final String[] names = new String[3];

							if (object instanceof User user) {
								names[0] = ContactsController.formatName(user.firstName, user.lastName).toLowerCase();
								username = user.username;
								if (UserObject.isReplyUser(user)) {
									names[2] = context.getString(R.string.RepliesTitle).toLowerCase();
								}
								else if (TLRPCExtensions.isSelf(user)) {
									names[2] = context.getString(R.string.SavedMessages).toLowerCase();
								}
							}
							else {
								TLRPC.Chat chat = (TLRPC.Chat)object;
								names[0] = chat.title.toLowerCase();
								username = chat.username;
							}
							names[1] = LocaleController.getInstance().getTranslitString(names[0]);
							if (names[0].equals(names[1])) {
								names[1] = null;
							}

							int found = 0;
							for (String q : search) {
								for (final String name : names) {
									if (name != null && (name.startsWith(q) || name.contains(" " + q))) {
										found = 1;
										break;
									}
								}
								if (found == 0 && username != null && username.toLowerCase().startsWith(q)) {
									found = 2;
								}

								if (found != 0) {
									if (found == 1) {
										if (object instanceof User user) {
											resultArrayNames.add(AndroidUtilities.generateSearchName(user.firstName, user.lastName, q));
										}
										else {
											TLRPC.Chat chat = (TLRPC.Chat)object;
											resultArrayNames.add(AndroidUtilities.generateSearchName(chat.title, null, q));
										}
									}
									else {
										resultArrayNames.add(AndroidUtilities.generateSearchName("@" + username, null, "@" + q));
									}
									resultArray.add(object);
									break;
								}
							}
						}
						updateSearchResults(resultArray, resultArrayNames);
					});
				}), 300);
			}
		}

		private void updateSearchResults(final ArrayList<Object> users, final ArrayList<CharSequence> names) {
			AndroidUtilities.runOnUIThread(() -> {
				if (!searching) {
					return;
				}
				searchRunnable = null;
				searchResult = users;
				searchResultNames = names;
				searchAdapterHelper.mergeResults(searchResult);
				if (searching && !searchAdapterHelper.isSearchInProgress()) {
					emptyView.showTextView();
				}
				notifyDataSetChanged();
			});
		}
	}
}
