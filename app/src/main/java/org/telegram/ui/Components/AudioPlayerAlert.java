/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.User;
import org.telegram.tgnet.TLRPCExtensions;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.AudioPlayerCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

@SuppressLint("NotifyDataSetChanged")
public class AudioPlayerAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, DownloadController.FileDownloadProgressListener {
	private final static int menu_speed_slow = 1;
	private final static int menu_speed_normal = 2;
	private final static int menu_speed_fast = 3;
	private final static int menu_speed_veryfast = 4;
	int rewindingState;
	float rewindingProgress = -1;
	int rewindingForwardPressedCount;
	long lastRewindingTime;
	long lastUpdateRewindingPlayerTime;
	private final ActionBar actionBar;
	private final View actionBarShadow;
	private final View playerShadow;
	private boolean searchWas;
	private boolean searching;
	private final RecyclerListView listView;
	private final LinearLayoutManager layoutManager;
	private final ListAdapter listAdapter;
	private final LinearLayout emptyView;
	private final TextView emptySubtitleTextView;
	private final FrameLayout playerLayout;
	private final CoverContainer coverContainer;
	private final ClippingTextViewSwitcher titleTextView;
	private final RLottieImageView prevButton;
	private final RLottieImageView nextButton;
	private final ClippingTextViewSwitcher authorTextView;
	private final ActionBarMenuItem optionsButton;
	private final LineProgressView progressView;
	private final SeekBarView seekBarView;
	private final SimpleTextView timeTextView;
	private final ActionBarMenuItem playbackSpeedButton;
	private final ActionBarMenuSubItem[] speedItems = new ActionBarMenuSubItem[4];
	private final TextView durationTextView;
	private final ActionBarMenuItem repeatButton;
	private final ActionBarMenuSubItem repeatSongItem;
	private final ActionBarMenuSubItem repeatListItem;
	private final ActionBarMenuSubItem shuffleListItem;
	private final ActionBarMenuSubItem reverseOrderItem;
	private final ImageView playButton;
	private final PlayPauseDrawable playPauseDrawable;
	private final FrameLayout blurredView;
	private final BackupImageView bigAlbumCover;
	private boolean blurredAnimationInProgress;
	private final View[] buttons = new View[5];
	private long lastBufferedPositionCheck;
	private boolean currentAudioFinishedLoading;
	private boolean scrollToSong = true;
	private int searchOpenPosition = -1;
	private int searchOpenOffset;
	private ArrayList<MessageObject> playlist;
	private MessageObject lastMessageObject;
	private int scrollOffsetY = Integer.MAX_VALUE;
	private String currentFile;
	private AnimatorSet actionBarAnimation;
	private int lastTime;
	private int lastDuration;
	private final int TAG;
	private final LaunchActivity parentActivity;

	public AudioPlayerAlert(final Context context) {
		super(context, true);
		fixNavigationBar();

		MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();

		if (messageObject != null) {
			currentAccount = messageObject.currentAccount;
		}
		else {
			currentAccount = UserConfig.selectedAccount;
		}

		parentActivity = (LaunchActivity)context;

		TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadProgressChanged);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.musicDidLoad);
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.moreMusicDidLoad);

		listAdapter = new ListAdapter(context);

		containerView = new FrameLayout(context) {

			private final RectF rect = new RectF();
			private boolean ignoreLayout = false;
			private int lastMeasuredHeight;
			private int lastMeasuredWidth;

			@Override
			public boolean onTouchEvent(MotionEvent e) {
				return !isDismissed() && super.onTouchEvent(e);
			}

			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
				int w = MeasureSpec.getSize(widthMeasureSpec);
				if (totalHeight != lastMeasuredHeight || w != lastMeasuredWidth) {
					if (blurredView.getTag() != null) {
						showAlbumCover(false, false);
					}
					lastMeasuredWidth = w;
					lastMeasuredHeight = totalHeight;
				}
				ignoreLayout = true;
				if (!isFullscreen) {
					setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
				}
				playerLayout.setVisibility(searchWas || keyboardVisible ? INVISIBLE : VISIBLE);
				playerShadow.setVisibility(playerLayout.getVisibility());
				int availableHeight = totalHeight - getPaddingTop();

				LayoutParams layoutParams = (LayoutParams)listView.getLayoutParams();
				layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

				layoutParams = (LayoutParams)actionBarShadow.getLayoutParams();
				layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

				layoutParams = (LayoutParams)blurredView.getLayoutParams();
				layoutParams.topMargin = -getPaddingTop();

				int contentSize = AndroidUtilities.dp(179);
				if (playlist.size() > 1) {
					contentSize += backgroundPaddingTop + playlist.size() * AndroidUtilities.dp(56);
				}
				int padding;
				if (searching || keyboardVisible) {
					padding = AndroidUtilities.dp(8);
				}
				else {
					padding = (contentSize < availableHeight ? availableHeight - contentSize : availableHeight - (int)(availableHeight / 5 * 3.5f)) + AndroidUtilities.dp(8);
					if (padding > availableHeight - AndroidUtilities.dp(179 + 150)) {
						padding = availableHeight - AndroidUtilities.dp(179 + 150);
					}
					if (padding < 0) {
						padding = 0;
					}
				}
				if (listView.getPaddingTop() != padding) {
					listView.setPadding(0, padding, 0, searching && keyboardVisible ? 0 : listView.getPaddingBottom());
				}
				ignoreLayout = false;
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
			}

			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				super.onLayout(changed, left, top, right, bottom);
				updateLayout();
				updateEmptyViewPosition();
			}

			@Override
			public boolean onInterceptTouchEvent(MotionEvent ev) {
				if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && actionBar.getAlpha() == 0.0f) {
					boolean dismiss;
					if (listAdapter.getItemCount() > 0) {
						dismiss = ev.getY() < scrollOffsetY + AndroidUtilities.dp(12);
					}
					else {
						dismiss = ev.getY() < getMeasuredHeight() - AndroidUtilities.dp(179 + 12);
					}
					if (dismiss) {
						dismiss();
						return true;
					}
				}
				return super.onInterceptTouchEvent(ev);
			}

			@Override
			public void requestLayout() {
				if (ignoreLayout) {
					return;
				}
				super.requestLayout();
			}

			@Override
			protected void onDraw(@NonNull Canvas canvas) {
				if (playlist.size() <= 1) {
					shadowDrawable.setBounds(0, getMeasuredHeight() - playerLayout.getMeasuredHeight() - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
					shadowDrawable.draw(canvas);
				}
				else {
					int offset = AndroidUtilities.dp(13);
					int top = scrollOffsetY - backgroundPaddingTop - offset;
					if (currentSheetAnimationType == 1) {
						top += listView.getTranslationY();
					}
					int y = top + AndroidUtilities.dp(20);

					int height = getMeasuredHeight() + AndroidUtilities.dp(15) + backgroundPaddingTop;
					float rad = 1.0f;

					if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
						float toMove = offset + AndroidUtilities.dp(11 - 7);
						float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / toMove);
						float availableToMove = ActionBar.getCurrentActionBarHeight() - toMove;

						int diff = (int)(availableToMove * moveProgress);
						top -= diff;
						y -= diff;
						height += diff;
						rad = 1.0f - moveProgress;
					}

					top += AndroidUtilities.statusBarHeight;
					y += AndroidUtilities.statusBarHeight;

					shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
					shadowDrawable.draw(canvas);

					if (rad != 1.0f) {
						Theme.dialogs_onlineCirclePaint.setColor(context.getColor(R.color.background));
						rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
						canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
					}

					if (rad != 0) {
						float alphaProgress = 1.0f;
						int w = AndroidUtilities.dp(36);
						rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
						int color = context.getColor(R.color.light_background);
						int alpha = Color.alpha(color);
						Theme.dialogs_onlineCirclePaint.setColor(color);
						Theme.dialogs_onlineCirclePaint.setAlpha((int)(alpha * alphaProgress * rad));
						canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
					}

					int color1 = context.getColor(R.color.background);
					int finalColor = Color.argb((int)(255 * actionBar.getAlpha()), (int)(Color.red(color1) * 0.8f), (int)(Color.green(color1) * 0.8f), (int)(Color.blue(color1) * 0.8f));
					Theme.dialogs_onlineCirclePaint.setColor(finalColor);
					canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
				}
			}

			@Override
			protected void onAttachedToWindow() {
				super.onAttachedToWindow();
				Bulletin.addDelegate(this, new Bulletin.Delegate() {
					@Override
					public void onHide(@NonNull Bulletin bulletin) {
						// unused
					}

					@Override
					public void onShow(@NonNull Bulletin bulletin) {
						// unused
					}

					@Override
					public void onOffsetChange(float offset) {
						// unused
					}

					@Override
					public int getBottomOffset(int tag) {
						return playerLayout.getHeight();
					}
				});
			}

			@Override
			protected void onDetachedFromWindow() {
				super.onDetachedFromWindow();
				Bulletin.removeDelegate(this);
			}
		};
		containerView.setWillNotDraw(false);
		containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

		actionBar = new ActionBar(context) {
			@Override
			public void setAlpha(float alpha) {
				super.setAlpha(alpha);
				containerView.invalidate();
			}
		};
		actionBar.setBackgroundColor(context.getColor(R.color.background));
		actionBar.setBackButtonImage(R.drawable.ic_back_arrow);
		actionBar.setItemsColor(context.getColor(R.color.text), false);
		actionBar.setItemsBackgroundColor(context.getColor(R.color.brand), false);
		actionBar.setTitleColor(context.getColor(R.color.text));
		actionBar.setTitle(context.getString(R.string.AttachMusic));
		actionBar.setSubtitleColor(context.getColor(R.color.dark_gray));
		actionBar.setOccupyStatusBar(false);
		actionBar.setAlpha(0.0f);

		if (messageObject != null && !MediaController.getInstance().currentPlaylistIsGlobalSearch()) {
			long did = messageObject.getDialogId();
			if (DialogObject.isEncryptedDialog(did)) {
				TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(did));
				if (encryptedChat != null) {
					User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.userId);
					if (user != null) {
						actionBar.setTitle(ContactsController.formatName(user.firstName, user.lastName));
					}
				}
			}
			else if (DialogObject.isUserDialog(did)) {
				User user = MessagesController.getInstance(currentAccount).getUser(did);
				if (user != null) {
					actionBar.setTitle(ContactsController.formatName(user.firstName, user.lastName));
				}
			}
			else {
				TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
				if (chat != null) {
					actionBar.setTitle(chat.title);
				}
			}
		}

		ActionBarMenu menu = actionBar.createMenu();
		ActionBarMenuItem searchItem = menu.addItem(0, R.drawable.ic_search_menu).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
			@Override
			public void onSearchCollapse() {
				if (searching) {
					searchWas = false;
					searching = false;
					setAllowNestedScroll(true);
					listAdapter.search(null);
				}
			}

			@Override
			public void onSearchExpand() {
				searchOpenPosition = layoutManager.findLastVisibleItemPosition();
				View firstVisView = layoutManager.findViewByPosition(searchOpenPosition);
				searchOpenOffset = firstVisView == null ? 0 : firstVisView.getTop();
				searching = true;
				setAllowNestedScroll(false);
				listAdapter.notifyDataSetChanged();
			}

			@Override
			public void onTextChanged(@NonNull EditText editText) {
				if (editText.length() > 0) {
					listAdapter.search(editText.getText().toString());
				}
				else {
					searchWas = false;
					listAdapter.search(null);
				}
			}
		});
		searchItem.setContentDescription(context.getString(R.string.Search));
		EditTextBoldCursor editText = searchItem.getSearchField();
		editText.setHint(context.getString(R.string.Search));
		editText.setTextColor(context.getColor(R.color.text));
		editText.setHintTextColor(context.getColor(R.color.dark_gray));
		editText.setCursorColor(context.getColor(R.color.text));

		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					dismiss();
				}
				else {
					onSubItemClick(id);
				}
			}
		});

		actionBarShadow = new View(context);
		actionBarShadow.setAlpha(0.0f);
		actionBarShadow.setBackgroundResource(R.drawable.header_shadow);

		playerShadow = new View(context);
		playerShadow.setBackgroundColor(context.getColor(R.color.shadow));

		playerLayout = new FrameLayout(context) {
			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				super.onLayout(changed, left, top, right, bottom);
				if (playbackSpeedButton != null && durationTextView != null) {
					int x = durationTextView.getLeft() - AndroidUtilities.dp(4) - playbackSpeedButton.getMeasuredWidth();
					playbackSpeedButton.layout(x, playbackSpeedButton.getTop(), x + playbackSpeedButton.getMeasuredWidth(), playbackSpeedButton.getBottom());
				}
			}
		};

		coverContainer = new CoverContainer(context) {

			private long pressTime;

			@Override
			public boolean onTouchEvent(MotionEvent event) {
				int action = event.getAction();
				if (action == MotionEvent.ACTION_DOWN) {
					if (getImageReceiver().hasBitmapImage()) {
						showAlbumCover(true, true);
						pressTime = SystemClock.elapsedRealtime();
					}
				}
				else if (action != MotionEvent.ACTION_MOVE) {
					if (SystemClock.elapsedRealtime() - pressTime >= 400) {
						showAlbumCover(false, true);
					}
				}
				return true;
			}

			@Override
			protected void onImageUpdated(ImageReceiver imageReceiver) {
				if (blurredView.getTag() != null) {
					bigAlbumCover.setImageBitmap(imageReceiver.getBitmap());
				}
			}
		};
		playerLayout.addView(coverContainer, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.RIGHT, 0, 20, 20, 0));

		titleTextView = new ClippingTextViewSwitcher(context) {
			@Override
			protected TextView createTextView() {
				final TextView textView = new TextView(context);
				textView.setTextColor(context.getColor(R.color.text));
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
				textView.setTypeface(Theme.TYPEFACE_BOLD);
				textView.setEllipsize(TextUtils.TruncateAt.END);
				textView.setSingleLine(true);
				return textView;
			}
		};
		playerLayout.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 20, 72, 0));

		authorTextView = new ClippingTextViewSwitcher(context) {
			@Override
			protected TextView createTextView() {
				final TextView textView = new TextView(context);
				textView.setTextColor(context.getColor(R.color.dark_gray));
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
				textView.setEllipsize(TextUtils.TruncateAt.END);
				textView.setSingleLine(true);
				textView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), AndroidUtilities.dp(1));
				textView.setBackground(Theme.createRadSelectorDrawable(context.getColor(R.color.light_background), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));

				textView.setOnClickListener(view -> {
					int dialogsCount = MessagesController.getInstance(currentAccount).getTotalDialogsCount();
					if (dialogsCount <= 10 || TextUtils.isEmpty(textView.getText().toString())) {
						return;
					}
					String query = textView.getText().toString();
					if (parentActivity.getActionBarLayout().getLastFragment() instanceof DialogsActivity dialogsActivity) {
						if (!dialogsActivity.onlyDialogsAdapter()) {
							dialogsActivity.setShowSearch(query, FiltersView.FILTER_INDEX_MUSIC);
							dismiss();
							return;
						}
					}
					DialogsActivity fragment = new DialogsActivity(null);
					fragment.setSearchString(query);
					fragment.setInitialSearchType(FiltersView.FILTER_INDEX_MUSIC);
					parentActivity.presentFragment(fragment, false, false);
					dismiss();
				});
				return textView;
			}
		};
		playerLayout.addView(authorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 14, 47, 72, 0));

		seekBarView = new SeekBarView(context) {
			@Override
			boolean onTouch(MotionEvent ev) {
				if (rewindingState != 0) {
					return false;
				}
				return super.onTouch(ev);
			}
		};
		seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
			@Override
			public void onSeekBarDrag(boolean stop, float progress) {
				if (stop) {
					MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), progress);
				}
				MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
				if (messageObject != null && messageObject.isMusic()) {
					updateProgress(messageObject);
				}
			}

			@Override
			public void onSeekBarPressed(boolean pressed) {
			}

			@Override
			public CharSequence getContentDescription() {
				final String time = LocaleController.formatPluralString("Minutes", lastTime / 60) + ' ' + LocaleController.formatPluralString("Seconds", lastTime % 60);
				final String totalTime = LocaleController.formatPluralString("Minutes", lastDuration / 60) + ' ' + LocaleController.formatPluralString("Seconds", lastDuration % 60);
				return LocaleController.formatString("AccDescrPlayerDuration", R.string.AccDescrPlayerDuration, time, totalTime);
			}
		});
		seekBarView.setReportChanges(true);
		playerLayout.addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 5, 70, 5, 0));

		progressView = new LineProgressView(context);
		progressView.setVisibility(View.INVISIBLE);
		progressView.setBackgroundColor(context.getColor(R.color.avatar_tint));
		progressView.setProgressColor(context.getColor(R.color.brand));
		playerLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.TOP | Gravity.LEFT, 21, 90, 21, 0));

		timeTextView = new SimpleTextView(context);
		timeTextView.setTextSize(12);
		timeTextView.setText("0:00");
		timeTextView.setTextColor(context.getColor(R.color.dark_gray));
		timeTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		playerLayout.addView(timeTextView, LayoutHelper.createFrame(100, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 20, 98, 0, 0));

		durationTextView = new TextView(context);
		durationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		durationTextView.setTextColor(context.getColor(R.color.dark_gray));
		durationTextView.setGravity(Gravity.CENTER);
		durationTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		playerLayout.addView(durationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 96, 20, 0));

		playbackSpeedButton = new ActionBarMenuItem(context, null, 0, context.getColor(R.color.text), false);
		playbackSpeedButton.setLongClickEnabled(false);
		playbackSpeedButton.setShowSubmenuByMove(false);
		playbackSpeedButton.setAdditionalYOffset(-AndroidUtilities.dp(224));
		playbackSpeedButton.setContentDescription(context.getString(R.string.AccDescrPlayerSpeed));
		playbackSpeedButton.setDelegate(id -> {
			if (id == menu_speed_slow) {
				MediaController.getInstance().setPlaybackSpeed(true, 0.5f);
			}
			else if (id == menu_speed_normal) {
				MediaController.getInstance().setPlaybackSpeed(true, 1.0f);
			}
			else if (id == menu_speed_fast) {
				MediaController.getInstance().setPlaybackSpeed(true, 1.5f);
			}
			else {
				MediaController.getInstance().setPlaybackSpeed(true, 1.8f);
			}
			updatePlaybackButton();
		});
		speedItems[0] = playbackSpeedButton.addSubItem(menu_speed_slow, R.drawable.msg_speed_0_5, context.getString(R.string.SpeedSlow));
		speedItems[1] = playbackSpeedButton.addSubItem(menu_speed_normal, R.drawable.msg_speed_1, context.getString(R.string.SpeedNormal));
		speedItems[2] = playbackSpeedButton.addSubItem(menu_speed_fast, R.drawable.msg_speed_1_5, context.getString(R.string.SpeedFast));
		speedItems[3] = playbackSpeedButton.addSubItem(menu_speed_veryfast, R.drawable.msg_speed_2, context.getString(R.string.SpeedVeryFast));
		if (AndroidUtilities.density >= 3.0f) {
			playbackSpeedButton.setPadding(0, 1, 0, 0);
		}
		playbackSpeedButton.setAdditionalXOffset(AndroidUtilities.dp(8));
		playbackSpeedButton.setShowedFromBottom(true);
		playerLayout.addView(playbackSpeedButton, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.RIGHT, 0, 86, 20, 0));
		playbackSpeedButton.setOnClickListener(v -> {
			float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(true);
			if (Math.abs(currentPlaybackSpeed - 1.0f) > 0.001f) {
				MediaController.getInstance().setPlaybackSpeed(true, 1.0f);
			}
			else {
				MediaController.getInstance().setPlaybackSpeed(true, MediaController.getInstance().getFastPlaybackSpeed(true));
			}
			updatePlaybackButton();
		});
		playbackSpeedButton.setOnLongClickListener(view -> {
			playbackSpeedButton.toggleSubMenu();
			return true;
		});
		updatePlaybackButton();

		FrameLayout bottomView = new FrameLayout(context) {
			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				int dist = ((right - left) - AndroidUtilities.dp(8 + 48 * 5)) / 4;
				for (int a = 0; a < 5; a++) {
					int l = AndroidUtilities.dp(4 + 48 * a) + dist * a;
					int t = AndroidUtilities.dp(9);
					buttons[a].layout(l, t, l + buttons[a].getMeasuredWidth(), t + buttons[a].getMeasuredHeight());
				}
			}
		};
		playerLayout.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 66, Gravity.TOP | Gravity.LEFT, 0, 111, 0, 0));

		buttons[0] = repeatButton = new ActionBarMenuItem(context, null, 0, 0, false);
		repeatButton.setLongClickEnabled(false);
		repeatButton.setShowSubmenuByMove(false);
		repeatButton.setAdditionalYOffset(-AndroidUtilities.dp(166));
		repeatButton.setBackground(Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1, AndroidUtilities.dp(18)));

		bottomView.addView(repeatButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));

		repeatButton.setOnClickListener(v -> {
			updateSubMenu();
			repeatButton.toggleSubMenu();
		});
		repeatSongItem = repeatButton.addSubItem(3, R.drawable.player_new_repeatone, context.getString(R.string.RepeatSong));
		repeatListItem = repeatButton.addSubItem(4, R.drawable.player_new_repeatall, context.getString(R.string.RepeatList));
		shuffleListItem = repeatButton.addSubItem(2, R.drawable.player_new_shuffle, context.getString(R.string.ShuffleList));
		reverseOrderItem = repeatButton.addSubItem(1, R.drawable.player_new_order, context.getString(R.string.ReverseOrder));
		repeatButton.setShowedFromBottom(true);

		listView = new RecyclerListView(context) {
			boolean ignoreLayout;

			@Override
			protected void onLayout(boolean changed, int l, int t, int r, int b) {
				super.onLayout(changed, l, t, r, b);

				if (searchOpenPosition != -1 && !actionBar.isSearchFieldVisible()) {
					ignoreLayout = true;
					layoutManager.scrollToPositionWithOffset(searchOpenPosition, searchOpenOffset - listView.getPaddingTop());
					super.onLayout(false, l, t, r, b);
					ignoreLayout = false;
					searchOpenPosition = -1;
				}
				else if (scrollToSong) {
					scrollToSong = false;
					ignoreLayout = true;
					if (scrollToCurrentSong(true)) {
						super.onLayout(false, l, t, r, b);
					}
					ignoreLayout = false;
				}
			}

			@Override
			protected boolean allowSelectChildAtPosition(float x, float y) {
				return y < playerLayout.getY() - listView.getTop();
			}

			@Override
			public void requestLayout() {
				if (ignoreLayout) {
					return;
				}
				super.requestLayout();
			}
		};

		repeatButton.setDelegate(id -> {
			if (id == 1 || id == 2) {
				boolean oldReversed = SharedConfig.playOrderReversed;
				if (SharedConfig.playOrderReversed && id == 1 || SharedConfig.shuffleMusic && id == 2) {
					MediaController.getInstance().setPlaybackOrderType(0);
				}
				else {
					MediaController.getInstance().setPlaybackOrderType(id);
				}
				listAdapter.notifyDataSetChanged();
				if (oldReversed != SharedConfig.playOrderReversed) {
					listView.stopScroll();
					scrollToCurrentSong(false);
				}
			}
			else {
				if (id == 4) {
					if (SharedConfig.repeatMode == 1) {
						SharedConfig.setRepeatMode(0);
					}
					else {
						SharedConfig.setRepeatMode(1);
					}
				}
				else {
					if (SharedConfig.repeatMode == 2) {
						SharedConfig.setRepeatMode(0);
					}
					else {
						SharedConfig.setRepeatMode(2);
					}
				}
			}
			updateRepeatButton();
		});

		final int iconColor = context.getColor(R.color.dark_gray);
		float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

		buttons[1] = prevButton = new RLottieImageView(context) {
			float startX;
			float startY;

			int pressedCount = 0;

			long lastTime;
			long lastUpdateTime;
			long startTime;

			@Override
			public boolean onTouchEvent(MotionEvent event) {
				if (seekBarView.isDragging() || rewindingState == 1) {
					return false;
				}
				float x = event.getRawX();
				float y = event.getRawY();

				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						startX = x;
						startY = y;
						startTime = System.currentTimeMillis();
						rewindingState = 0;
						AndroidUtilities.runOnUIThread(pressedRunnable, 300);
						if (getBackground() != null) {
							getBackground().setHotspot(startX, startY);
						}
						setPressed(true);
						break;
					case MotionEvent.ACTION_MOVE:
						float dx = x - startX;
						float dy = y - startY;

						if ((dx * dx + dy * dy) > touchSlop * touchSlop && rewindingState == 0) {
							AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
							setPressed(false);
						}
						break;
					case MotionEvent.ACTION_CANCEL:
					case MotionEvent.ACTION_UP:
						AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
						AndroidUtilities.cancelRunOnUIThread(backSeek);
						if (rewindingState == 0 && event.getAction() == MotionEvent.ACTION_UP && (System.currentTimeMillis() - startTime < 300)) {
							MediaController.getInstance().playPreviousMessage();
							prevButton.setProgress(0f);
							prevButton.playAnimation();
						}
						if (pressedCount > 0) {
							lastUpdateTime = 0;
							backSeek.run();
							MediaController.getInstance().resumeByRewind();
						}
						rewindingProgress = -1;
						setPressed(false);
						rewindingState = 0;
						pressedCount = 0;
						break;
				}
				return true;
			}

			@Override
			public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
				super.onInitializeAccessibilityNodeInfo(info);
				info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
			}

			private final Runnable pressedRunnable = new Runnable() {
				@Override
				public void run() {
					pressedCount++;
					if (pressedCount == 1) {
						rewindingState = -1;
						rewindingProgress = MediaController.getInstance().getPlayingMessageObject().audioProgress;
						lastTime = System.currentTimeMillis();
						AndroidUtilities.runOnUIThread(this, 2000);
						AndroidUtilities.runOnUIThread(backSeek);
					}
					else if (pressedCount == 2) {
						AndroidUtilities.runOnUIThread(this, 2000);
					}
				}
			};

			private final Runnable backSeek = new Runnable() {
				@Override
				public void run() {
					long duration = MediaController.getInstance().getDuration();
					if (duration == 0 || duration == C.TIME_UNSET) {
						lastTime = System.currentTimeMillis();
						return;
					}
					float currentProgress = rewindingProgress;

					long t = System.currentTimeMillis();
					long dt = t - lastTime;
					lastTime = t;
					long updateDt = t - lastUpdateTime;
					if (pressedCount == 1) {
						dt *= 3;
					}
					else if (pressedCount == 2) {
						dt *= 6;
					}
					else {
						dt *= 12;
					}
					long currentTime = (long)(duration * currentProgress - dt);
					currentProgress = currentTime / (float)duration;
					if (currentProgress < 0) {
						currentProgress = 0;
					}
					rewindingProgress = currentProgress;
					MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
					if (messageObject != null && messageObject.isMusic()) {
						updateProgress(messageObject);
					}
					if (rewindingState == -1 && pressedCount > 0) {
						if (updateDt > 200 || rewindingProgress == 0) {
							lastUpdateTime = t;
							if (rewindingProgress == 0) {
								MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), 0);
								MediaController.getInstance().pauseByRewind();
							}
							else {
								MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), currentProgress);
							}
						}
						if (pressedCount > 0 && rewindingProgress > 0) {
							AndroidUtilities.runOnUIThread(backSeek, 16);
						}
					}
				}
			};
		};
		prevButton.setScaleType(ImageView.ScaleType.CENTER);
		prevButton.setAnimation(R.raw.player_prev, 20, 20);
		prevButton.setLayerColor("Triangle 3.**", iconColor);
		prevButton.setLayerColor("Triangle 4.**", iconColor);
		prevButton.setLayerColor("Rectangle 4.**", iconColor);
		prevButton.setBackground(Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1, AndroidUtilities.dp(22)));

		bottomView.addView(prevButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));

		prevButton.setContentDescription(context.getString(R.string.AccDescrPrevious));

		buttons[2] = playButton = new ImageView(context);
		playButton.setScaleType(ImageView.ScaleType.CENTER);
		playButton.setImageDrawable(playPauseDrawable = new PlayPauseDrawable(28));
		playPauseDrawable.setPause(!MediaController.getInstance().isMessagePaused(), false);
		playButton.setColorFilter(new PorterDuffColorFilter(context.getColor(R.color.dark_gray), PorterDuff.Mode.MULTIPLY));
		playButton.setBackground(Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1, AndroidUtilities.dp(24)));

		bottomView.addView(playButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));

		playButton.setOnClickListener(v -> {
			if (MediaController.getInstance().isDownloadingCurrentMessage()) {
				return;
			}
			if (MediaController.getInstance().isMessagePaused()) {
				MediaController.getInstance().playMessage(MediaController.getInstance().getPlayingMessageObject());
			}
			else {
				MediaController.getInstance().pauseMessage(MediaController.getInstance().getPlayingMessageObject());
			}
		});

		buttons[3] = nextButton = new RLottieImageView(context) {

			float startX;
			float startY;
			boolean pressed;

			private final Runnable pressedRunnable = new Runnable() {
				@Override
				public void run() {
					if (MediaController.getInstance().getPlayingMessageObject() == null) {
						return;
					}
					rewindingForwardPressedCount++;
					if (rewindingForwardPressedCount == 1) {
						pressed = true;
						rewindingState = 1;
						if (MediaController.getInstance().isMessagePaused()) {
							startForwardRewindingSeek();
						}
						else if (rewindingState == 1) {
							AndroidUtilities.cancelRunOnUIThread(forwardSeek);
							lastUpdateRewindingPlayerTime = 0;
						}
						MediaController.getInstance().setPlaybackSpeed(true, 4);
						AndroidUtilities.runOnUIThread(this, 2000);
					}
					else if (rewindingForwardPressedCount == 2) {
						MediaController.getInstance().setPlaybackSpeed(true, 7);
						AndroidUtilities.runOnUIThread(this, 2000);
					}
					else {
						MediaController.getInstance().setPlaybackSpeed(true, 13);
					}
				}
			};

			@Override
			public boolean onTouchEvent(MotionEvent event) {
				if (seekBarView.isDragging() || rewindingState == -1) {
					return false;
				}
				float x = event.getRawX();
				float y = event.getRawY();

				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						pressed = false;
						startX = x;
						startY = y;
						AndroidUtilities.runOnUIThread(pressedRunnable, 300);
						if (getBackground() != null) {
							getBackground().setHotspot(startX, startY);
						}
						setPressed(true);
						break;
					case MotionEvent.ACTION_MOVE:
						float dx = x - startX;
						float dy = y - startY;

						if ((dx * dx + dy * dy) > touchSlop * touchSlop && !pressed) {
							AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
							setPressed(false);
						}
						break;
					case MotionEvent.ACTION_CANCEL:
					case MotionEvent.ACTION_UP:
						if (!pressed && event.getAction() == MotionEvent.ACTION_UP && isPressed()) {
							MediaController.getInstance().playNextMessage();
							nextButton.setProgress(0f);
							nextButton.playAnimation();
						}
						AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
						if (rewindingForwardPressedCount > 0) {
							MediaController.getInstance().setPlaybackSpeed(true, 1f);
							if (MediaController.getInstance().isMessagePaused()) {
								lastUpdateRewindingPlayerTime = 0;
								forwardSeek.run();
							}
						}
						rewindingState = 0;
						setPressed(false);
						rewindingForwardPressedCount = 0;
						rewindingProgress = -1;
						break;
				}
				return true;
			}

			@Override
			public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
				super.onInitializeAccessibilityNodeInfo(info);
				info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
			}
		};
		nextButton.setScaleType(ImageView.ScaleType.CENTER);
		nextButton.setAnimation(R.raw.player_prev, 20, 20);
		nextButton.setLayerColor("Triangle 3.**", iconColor);
		nextButton.setLayerColor("Triangle 4.**", iconColor);
		nextButton.setLayerColor("Rectangle 4.**", iconColor);
		nextButton.setRotation(180f);
		nextButton.setBackground(Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1, AndroidUtilities.dp(22)));
		nextButton.setContentDescription(context.getString(R.string.Next));

		bottomView.addView(nextButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));

		buttons[4] = optionsButton = new ActionBarMenuItem(context, null, 0, iconColor, false);
		optionsButton.setLongClickEnabled(false);
		optionsButton.setShowSubmenuByMove(false);
		optionsButton.setIcon(R.drawable.overflow_menu);
		optionsButton.setSubMenuOpenSide(2);
		optionsButton.setAdditionalYOffset(-AndroidUtilities.dp(157));
		optionsButton.setBackground(Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1, AndroidUtilities.dp(18)));

		bottomView.addView(optionsButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));

		optionsButton.addSubItem(1, R.drawable.msg_forward, context.getString(R.string.Forward));
		optionsButton.addSubItem(2, R.drawable.msg_shareout, context.getString(R.string.ShareFile));
		optionsButton.addSubItem(5, R.drawable.msg_download, context.getString(R.string.SaveToMusic));
		optionsButton.addSubItem(4, R.drawable.msg_message, context.getString(R.string.ShowInChat));
		optionsButton.setShowedFromBottom(true);
		optionsButton.setOnClickListener(v -> optionsButton.toggleSubMenu());
		optionsButton.setDelegate(this::onSubItemClick);
		optionsButton.setContentDescription(context.getString(R.string.AccDescrMoreOptions));

		emptyView = new LinearLayout(context);
		emptyView.setOrientation(LinearLayout.VERTICAL);
		emptyView.setGravity(Gravity.CENTER);
		emptyView.setVisibility(View.GONE);
		containerView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
		emptyView.setOnTouchListener((v, event) -> true);

		ImageView emptyImageView = new ImageView(context);
		emptyImageView.setImageResource(R.drawable.music_empty);
		emptyImageView.setColorFilter(new PorterDuffColorFilter(context.getColor(R.color.dark_gray), PorterDuff.Mode.MULTIPLY));
		emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

		TextView emptyTitleTextView = new TextView(context);
		emptyTitleTextView.setTextColor(context.getColor(R.color.text));
		emptyTitleTextView.setGravity(Gravity.CENTER);
		emptyTitleTextView.setText(context.getString(R.string.NoAudioFound));
		emptyTitleTextView.setTypeface(Theme.TYPEFACE_BOLD);
		emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
		emptyTitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
		emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

		emptySubtitleTextView = new TextView(context);
		emptySubtitleTextView.setTextColor(context.getColor(R.color.dark_gray));
		emptySubtitleTextView.setGravity(Gravity.CENTER);
		emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		emptySubtitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
		emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

		listView.setClipToPadding(false);
		listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
		listView.setHorizontalScrollBarEnabled(false);
		listView.setVerticalScrollBarEnabled(false);

		containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

		listView.setAdapter(listAdapter);
		listView.setGlowColor(context.getColor(R.color.light_background));
		listView.setOnItemClickListener((view, position) -> {
			if (view instanceof AudioPlayerCell) {
				((AudioPlayerCell)view).didPressedButton();
			}
		});
		listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					int offset = AndroidUtilities.dp(13);
					int top = scrollOffsetY - backgroundPaddingTop - offset;
					if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() && listView.canScrollVertically(1)) {
						RecyclerListView.Holder holder = (RecyclerListView.Holder)listView.findViewHolderForAdapterPosition(0);
						if (holder != null && holder.itemView.getTop() > AndroidUtilities.dp(7)) {
							listView.smoothScrollBy(0, holder.itemView.getTop() - AndroidUtilities.dp(7));
						}
					}
				}
				else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
					AndroidUtilities.hideKeyboard(getCurrentFocus());
				}
			}

			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				updateLayout();
				updateEmptyViewPosition();

				if (!searchWas) {
					int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
					int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
					int totalItemCount = recyclerView.getAdapter().getItemCount();

					if (SharedConfig.playOrderReversed) {
						if (firstVisibleItem < 10) {
							MediaController.getInstance().loadMoreMusic();
						}
					}
					else {
						if (firstVisibleItem + visibleItemCount > totalItemCount - 10) {
							MediaController.getInstance().loadMoreMusic();
						}
					}
				}
			}
		});

		playlist = MediaController.getInstance().getPlaylist();
		listAdapter.notifyDataSetChanged();

		containerView.addView(playerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 179, Gravity.LEFT | Gravity.BOTTOM));
		containerView.addView(playerShadow, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.LEFT | Gravity.BOTTOM));
		FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)playerShadow.getLayoutParams();
		layoutParams.bottomMargin = AndroidUtilities.dp(179);
		containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3));
		containerView.addView(actionBar);

		blurredView = new FrameLayout(context) {
			@Override
			public boolean onTouchEvent(MotionEvent event) {
				if (blurredView.getTag() != null) {
					showAlbumCover(false, true);
				}
				return true;
			}
		};
		blurredView.setAlpha(0.0f);
		blurredView.setVisibility(View.INVISIBLE);
		getContainer().addView(blurredView);

		bigAlbumCover = new BackupImageView(context);
		bigAlbumCover.setAspectFit(true);
		bigAlbumCover.setRoundRadius(AndroidUtilities.dp(8));
		bigAlbumCover.setScaleX(0.9f);
		bigAlbumCover.setScaleY(0.9f);
		blurredView.addView(bigAlbumCover, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 30, 30, 30, 30));

		updateTitle(false);
		updateRepeatButton();
		updateEmptyView();
	}

	@Override
	public int getContainerViewHeight() {
		if (playerLayout == null) {
			return 0;
		}
		if (playlist.size() <= 1) {
			return playerLayout.getMeasuredHeight() + backgroundPaddingTop;
		}
		else {
			int offset = AndroidUtilities.dp(13);
			int top = scrollOffsetY - backgroundPaddingTop - offset;
			if (currentSheetAnimationType == 1) {
				top += listView.getTranslationY();
			}
			if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
				float toMove = offset + AndroidUtilities.dp(11 - 7);
				float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / toMove);
				float availableToMove = ActionBar.getCurrentActionBarHeight() - toMove;

				int diff = (int)(availableToMove * moveProgress);
				top -= diff;
			}

			top += AndroidUtilities.statusBarHeight;

			return container.getMeasuredHeight() - top;
		}
	}

	private void startForwardRewindingSeek() {
		if (rewindingState == 1) {
			lastRewindingTime = System.currentTimeMillis();
			rewindingProgress = MediaController.getInstance().getPlayingMessageObject().audioProgress;
			AndroidUtilities.cancelRunOnUIThread(forwardSeek);
			AndroidUtilities.runOnUIThread(forwardSeek);
		}
	}

	private void updateEmptyViewPosition() {
		if (emptyView.getVisibility() != View.VISIBLE) {
			return;
		}
		int h = playerLayout.getVisibility() == View.VISIBLE ? AndroidUtilities.dp(150) : -AndroidUtilities.dp(30);
		emptyView.setTranslationY((emptyView.getMeasuredHeight() - containerView.getMeasuredHeight() - h) / 2);
	}

	private final Runnable forwardSeek = new Runnable() {
		@Override
		public void run() {
			long duration = MediaController.getInstance().getDuration();
			if (duration == 0 || duration == C.TIME_UNSET) {
				lastRewindingTime = System.currentTimeMillis();
				return;
			}
			float currentProgress = rewindingProgress;

			long t = System.currentTimeMillis();
			long dt = t - lastRewindingTime;
			lastRewindingTime = t;
			long updateDt = t - lastUpdateRewindingPlayerTime;
			if (rewindingForwardPressedCount == 1) {
				dt = dt * 3 - dt;
			}
			else if (rewindingForwardPressedCount == 2) {
				dt = dt * 6 - dt;
			}
			else {
				dt = dt * 12 - dt;
			}
			long currentTime = (long)(duration * currentProgress + dt);
			currentProgress = currentTime / (float)duration;
			if (currentProgress < 0) {
				currentProgress = 0;
			}
			rewindingProgress = currentProgress;
			MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
			if (messageObject != null && messageObject.isMusic()) {
				if (!MediaController.getInstance().isMessagePaused()) {
					MediaController.getInstance().getPlayingMessageObject().audioProgress = rewindingProgress;
				}
				updateProgress(messageObject);
			}
			if (rewindingState == 1 && rewindingForwardPressedCount > 0 && MediaController.getInstance().isMessagePaused()) {
				if (updateDt > 200 || rewindingProgress == 0) {
					lastUpdateRewindingPlayerTime = t;
					MediaController.getInstance().seekToProgress(MediaController.getInstance().getPlayingMessageObject(), currentProgress);
				}
				if (rewindingForwardPressedCount > 0 && rewindingProgress > 0) {
					AndroidUtilities.runOnUIThread(forwardSeek, 16);
				}
			}
		}
	};

	private void updateEmptyView() {
		emptyView.setVisibility(searching && listAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
		updateEmptyViewPosition();
	}

	private boolean scrollToCurrentSong(boolean search) {
		MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
		if (playingMessageObject != null) {
			boolean found = false;
			if (search) {
				int count = listView.getChildCount();
				for (int a = 0; a < count; a++) {
					View child = listView.getChildAt(a);
					if (child instanceof AudioPlayerCell) {
						if (((AudioPlayerCell)child).getMessageObject() == playingMessageObject) {
							if (child.getBottom() <= listView.getMeasuredHeight()) {
								found = true;
							}
							break;
						}
					}
				}
			}
			if (!found) {
				int idx = playlist.indexOf(playingMessageObject);
				if (idx >= 0) {
					if (SharedConfig.playOrderReversed) {
						layoutManager.scrollToPosition(idx);
					}
					else {
						layoutManager.scrollToPosition(playlist.size() - idx);
					}
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean onCustomMeasure(View view, int width, int height) {
		if (view == blurredView) {
			blurredView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
			return true;
		}
		return false;
	}

	@Override
	protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
		int width = (right - left);
		int height = (bottom - top);
		if (view == blurredView) {
			blurredView.layout(left, 0, left + width, height);
			return true;
		}
		return false;
	}

	private void setMenuItemChecked(ActionBarMenuSubItem item, boolean checked) {
		if (checked) {
			item.setTextColor(getContext().getColor(R.color.brand));
			item.setIconColor(getContext().getColor(R.color.brand));
		}
		else {
			item.setTextColor(getContext().getColor(R.color.text));
			item.setIconColor(getContext().getColor(R.color.text));
		}
	}

	private void updateSubMenu() {
		setMenuItemChecked(shuffleListItem, SharedConfig.shuffleMusic);
		setMenuItemChecked(reverseOrderItem, SharedConfig.playOrderReversed);
		setMenuItemChecked(repeatListItem, SharedConfig.repeatMode == 1);
		setMenuItemChecked(repeatSongItem, SharedConfig.repeatMode == 2);
	}

	private void updatePlaybackButton() {
		float currentPlaybackSpeed = MediaController.getInstance().getPlaybackSpeed(true);

		int clr;

		if (Math.abs(currentPlaybackSpeed - 1.0f) > 0.001f) {
			clr = getContext().getColor(R.color.brand);
		}
		else {
			clr = getContext().getColor(R.color.dark_gray);
		}

		float speed = MediaController.getInstance().getFastPlaybackSpeed(true);

		if (Math.abs(speed - 1.8f) < 0.001f) {
			playbackSpeedButton.setIcon(R.drawable.voice_mini_2_0);
		}
		else if (Math.abs(speed - 1.5f) < 0.001f) {
			playbackSpeedButton.setIcon(R.drawable.voice_mini_1_5);
		}
		else {
			playbackSpeedButton.setIcon(R.drawable.voice_mini_0_5);
		}

		playbackSpeedButton.setIconColor(clr);
		playbackSpeedButton.setBackground(Theme.createSelectorDrawable(clr & 0x19ffffff, 1, AndroidUtilities.dp(14)));

		for (int a = 0; a < speedItems.length; a++) {
			if (a == 0 && Math.abs(currentPlaybackSpeed - 0.5f) < 0.001f || a == 1 && Math.abs(currentPlaybackSpeed - 1.0f) < 0.001f || a == 2 && Math.abs(currentPlaybackSpeed - 1.5f) < 0.001f || a == 3 && Math.abs(currentPlaybackSpeed - 1.8f) < 0.001f) {
				speedItems[a].setColors(getContext().getColor(R.color.brand), getContext().getColor(R.color.dark_gray));
			}
			else {
				speedItems[a].setColors(getContext().getColor(R.color.text), getContext().getColor(R.color.text));
			}
		}
	}

	private void onSubItemClick(int id) {
		final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
		if (messageObject == null || parentActivity == null) {
			return;
		}
		if (id == 1) {
			if (UserConfig.selectedAccount != currentAccount) {
				parentActivity.switchToAccount(currentAccount);
			}
			Bundle args = new Bundle();
			args.putBoolean("onlySelect", true);
			args.putInt("dialogsType", 3);
			DialogsActivity fragment = new DialogsActivity(args);
			final ArrayList<MessageObject> fmessages = new ArrayList<>();
			fmessages.add(messageObject);
			fragment.setDelegate((fragment1, dids, message, param) -> {
				if (dids.size() > 1 || dids.get(0) == UserConfig.getInstance(currentAccount).getClientUserId() || message != null) {
					for (int a = 0; a < dids.size(); a++) {
						long did = dids.get(a);
						if (message != null) {
							SendMessagesHelper.getInstance(currentAccount).sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0, null, false);
						}
						SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, did, false, false, true, 0);
					}
					fragment1.finishFragment();
				}
				else {
					long did = dids.get(0);
					Bundle args1 = new Bundle();
					args1.putBoolean("scrollToTopOnResume", true);
					if (DialogObject.isEncryptedDialog(did)) {
						args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
					}
					else if (DialogObject.isUserDialog(did)) {
						args1.putLong("user_id", did);
					}
					else {
						args1.putLong("chat_id", -did);
					}
					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
					ChatActivity chatActivity = new ChatActivity(args1);
					if (parentActivity.presentFragment(chatActivity, true, false)) {
						chatActivity.showFieldPanelForForward(true, fmessages);
					}
					else {
						fragment1.finishFragment();
					}
				}
			});
			parentActivity.presentFragment(fragment);
			dismiss();
		}
		else if (id == 2) {
			try {
				File f = null;

				if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
					f = new File(messageObject.messageOwner.attachPath);
					if (!f.exists()) {
						f = null;
					}
				}
				if (f == null) {
					f = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner);
				}

				if (f.exists()) {
					Intent intent = new Intent(Intent.ACTION_SEND);
					intent.setType(messageObject.getMimeType());
					if (Build.VERSION.SDK_INT >= 24) {
						try {
							intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", f));
							intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						}
						catch (Exception ignore) {
							intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
						}
					}
					else {
						intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
					}

					parentActivity.startActivityForResult(Intent.createChooser(intent, parentActivity.getString(R.string.ShareFile)), 500);
				}
				else {
					AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
					builder.setTitle(parentActivity.getString(R.string.AppName));
					builder.setPositiveButton(parentActivity.getString(R.string.OK), null);
					builder.setMessage(parentActivity.getString(R.string.PleaseDownload));
					builder.show();
				}
			}
			catch (Exception e) {
				FileLog.e(e);
			}
		}
		else if (id == 4) {
			if (UserConfig.selectedAccount != currentAccount) {
				parentActivity.switchToAccount(currentAccount);
			}

			Bundle args = new Bundle();
			long did = messageObject.getDialogId();
			if (DialogObject.isEncryptedDialog(did)) {
				args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
			}
			else if (DialogObject.isUserDialog(did)) {
				args.putLong("user_id", did);
			}
			else {
				TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
				if (chat != null && TLRPCExtensions.getMigratedTo(chat) != null) {
					args.putLong("migrated_to", did);
					did = -TLRPCExtensions.getMigratedTo(chat).channelId;
				}
				args.putLong("chat_id", -did);
			}
			args.putInt("message_id", messageObject.getId());
			NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
			parentActivity.presentFragment(new ChatActivity(args), false, false);
			dismiss();
		}
		else if (id == 5) {
			if ((Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && parentActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				parentActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
				return;
			}
			String fileName = FileLoader.getDocumentFileName(messageObject.getDocument());
			if (TextUtils.isEmpty(fileName)) {
				fileName = messageObject.getFileName();
			}
			String path = messageObject.messageOwner.attachPath;
			if (path != null && path.length() > 0) {
				File temp = new File(path);
				if (!temp.exists()) {
					path = null;
				}
			}
			if (path == null || path.length() == 0) {
				path = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner).toString();
			}
			MediaController.saveFile(path, parentActivity, 3, fileName, messageObject.getDocument() != null ? messageObject.getDocument().mimeType : "", () -> BulletinFactory.of((FrameLayout)containerView).createDownloadBulletin(BulletinFactory.FileType.AUDIO).show());
		}
	}

	private void showAlbumCover(boolean show, boolean animated) {
		if (show) {
			if (blurredView.getVisibility() == View.VISIBLE || blurredAnimationInProgress) {
				return;
			}
			blurredView.setTag(1);
			bigAlbumCover.setImageBitmap(coverContainer.getImageReceiver().getBitmap());
			blurredAnimationInProgress = true;
			BaseFragment fragment = parentActivity.getActionBarLayout().fragmentsStack.get(parentActivity.getActionBarLayout().fragmentsStack.size() - 1);
			View fragmentView = fragment.getFragmentView();
			int w = (int)(fragmentView.getMeasuredWidth() / 6.0f);
			int h = (int)(fragmentView.getMeasuredHeight() / 6.0f);
			Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
			fragmentView.draw(canvas);
			canvas.translate(containerView.getLeft() - getLeftInset(), 0);
			containerView.draw(canvas);
			Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
			blurredView.setBackground(new BitmapDrawable(parentActivity.getResources(), bitmap));
			blurredView.setVisibility(View.VISIBLE);
			blurredView.animate().alpha(1.0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					blurredAnimationInProgress = false;
				}
			}).start();
			bigAlbumCover.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
		}
		else {
			if (blurredView.getVisibility() != View.VISIBLE) {
				return;
			}
			blurredView.setTag(null);
			if (animated) {
				blurredAnimationInProgress = true;
				blurredView.animate().alpha(0.0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						blurredView.setVisibility(View.INVISIBLE);
						bigAlbumCover.setImageBitmap(null);
						blurredAnimationInProgress = false;
					}
				}).start();
				bigAlbumCover.animate().scaleX(0.9f).scaleY(0.9f).setDuration(180).start();
			}
			else {
				blurredView.setAlpha(0.0f);
				blurredView.setVisibility(View.INVISIBLE);
				bigAlbumCover.setImageBitmap(null);
				bigAlbumCover.setScaleX(0.9f);
				bigAlbumCover.setScaleY(0.9f);
			}
		}
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
			updateTitle(id == NotificationCenter.messagePlayingDidReset && (Boolean)args[1]);
			if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
				int count = listView.getChildCount();
				for (int a = 0; a < count; a++) {
					View view = listView.getChildAt(a);
					if (view instanceof AudioPlayerCell cell) {
						MessageObject messageObject = cell.getMessageObject();
						if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic())) {
							cell.updateButtonState(false, true);
						}
					}
				}
				if (id == NotificationCenter.messagePlayingPlayStateChanged) {
					if (MediaController.getInstance().getPlayingMessageObject() != null) {
						if (MediaController.getInstance().isMessagePaused()) {
							startForwardRewindingSeek();
						}
						else if (rewindingState == 1 && rewindingProgress != -1f) {
							AndroidUtilities.cancelRunOnUIThread(forwardSeek);
							lastUpdateRewindingPlayerTime = 0;
							forwardSeek.run();
							rewindingProgress = -1f;
						}
					}
				}
			}
			else {
				MessageObject messageObject = (MessageObject)args[0];
				if (messageObject.eventId != 0) {
					return;
				}
				int count = listView.getChildCount();
				for (int a = 0; a < count; a++) {
					View view = listView.getChildAt(a);
					if (view instanceof AudioPlayerCell cell) {
						MessageObject messageObject1 = cell.getMessageObject();
						if (messageObject1 != null && (messageObject1.isVoice() || messageObject1.isMusic())) {
							cell.updateButtonState(false, true);
						}
					}
				}
			}
		}
		else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
			MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
			if (messageObject != null && messageObject.isMusic()) {
				updateProgress(messageObject);
			}
		}
		else if (id == NotificationCenter.musicDidLoad) {
			playlist = MediaController.getInstance().getPlaylist();
			listAdapter.notifyDataSetChanged();
		}
		else if (id == NotificationCenter.moreMusicDidLoad) {
			playlist = MediaController.getInstance().getPlaylist();
			listAdapter.notifyDataSetChanged();
			if (SharedConfig.playOrderReversed) {
				listView.stopScroll();
				int addedCount = (Integer)args[0];
				int position = layoutManager.findLastVisibleItemPosition();
				if (position != RecyclerView.NO_POSITION) {
					View firstVisView = layoutManager.findViewByPosition(position);
					int offset = firstVisView == null ? 0 : firstVisView.getTop();
					layoutManager.scrollToPositionWithOffset(position + addedCount, offset);
				}
			}
		}
		else if (id == NotificationCenter.fileLoaded) {
			String name = (String)args[0];
			if (name.equals(currentFile)) {
				updateTitle(false);
				currentAudioFinishedLoading = true;
			}
		}
		else if (id == NotificationCenter.fileLoadProgressChanged) {
			String name = (String)args[0];
			if (name.equals(currentFile)) {
				MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
				if (messageObject == null) {
					return;
				}
				float bufferedProgress;
				if (currentAudioFinishedLoading) {
					bufferedProgress = 1.0f;
				}
				else {
					long newTime = SystemClock.elapsedRealtime();
					if (Math.abs(newTime - lastBufferedPositionCheck) >= 500) {
						bufferedProgress = MediaController.getInstance().isStreamingCurrentAudio() ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(messageObject.audioProgress, currentFile) : 1.0f;
						lastBufferedPositionCheck = newTime;
					}
					else {
						bufferedProgress = -1;
					}
				}
				if (bufferedProgress != -1) {
					seekBarView.setBufferedProgress(bufferedProgress);
				}
			}
		}
	}

	@Override
	protected boolean canDismissWithSwipe() {
		return false;
	}

	private void updateLayout() {
		if (listView.getChildCount() <= 0) {
			listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
			containerView.invalidate();
			return;
		}
		View child = listView.getChildAt(0);
		RecyclerListView.Holder holder = (RecyclerListView.Holder)listView.findContainingViewHolder(child);
		int top = child.getTop();
		int newOffset = AndroidUtilities.dp(7);
		if (top >= AndroidUtilities.dp(7) && holder != null && holder.getAdapterPosition() == 0) {
			newOffset = top;
		}
		boolean show = newOffset <= AndroidUtilities.dp(12);
		if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
			actionBar.setTag(show ? 1 : null);
			if (actionBarAnimation != null) {
				actionBarAnimation.cancel();
				actionBarAnimation = null;
			}
			actionBarAnimation = new AnimatorSet();
			actionBarAnimation.setDuration(180);
			actionBarAnimation.playTogether(ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f), ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f));
			actionBarAnimation.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {

				}

				@Override
				public void onAnimationCancel(Animator animation) {
					actionBarAnimation = null;
				}
			});
			actionBarAnimation.start();
		}
		FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)listView.getLayoutParams();
		newOffset += layoutParams.topMargin - AndroidUtilities.dp(11);
		if (scrollOffsetY != newOffset) {
			listView.setTopGlowOffset((scrollOffsetY = newOffset) - layoutParams.topMargin);
			containerView.invalidate();
		}
	}

	@Override
	public void dismiss() {
		super.dismiss();
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.musicDidLoad);
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.moreMusicDidLoad);
		DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
	}

	@Override
	public void onBackPressed() {
		if (actionBar != null && actionBar.isSearchFieldVisible()) {
			actionBar.closeSearchField();
			return;
		}
		if (blurredView.getTag() != null) {
			showAlbumCover(false, true);
			return;
		}
		super.onBackPressed();
	}

	@Override
	public void onFailedDownload(String fileName, boolean canceled) {

	}

	@Override
	public void onSuccessDownload(String fileName) {

	}

	@Override
	public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
		progressView.setProgress(Math.min(1f, downloadedSize / (float)totalSize), true);
	}

	@Override
	public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

	}

	@Override
	public int getObserverTag() {
		return TAG;
	}

	private void updateRepeatButton() {
		int mode = SharedConfig.repeatMode;
		if (mode == 0 || mode == 1) {
			if (SharedConfig.shuffleMusic) {
				if (mode == 0) {
					repeatButton.setIcon(R.drawable.player_new_shuffle);
				}
				else {
					repeatButton.setIcon(R.drawable.player_new_repeat_shuffle);
				}
			}
			else if (SharedConfig.playOrderReversed) {
				if (mode == 0) {
					repeatButton.setIcon(R.drawable.player_new_order);
				}
				else {
					repeatButton.setIcon(R.drawable.player_new_repeat_reverse);
				}
			}
			else {
				repeatButton.setIcon(R.drawable.player_new_repeatall);
			}
			if (mode == 0 && !SharedConfig.shuffleMusic && !SharedConfig.playOrderReversed) {
				repeatButton.setIconColor(getContext().getColor(R.color.dark_gray));
				Theme.setSelectorDrawableColor(repeatButton.getBackground(), getContext().getColor(R.color.light_background), true);
				repeatButton.setContentDescription(getContext().getString(R.string.AccDescrRepeatOff));
			}
			else {
				repeatButton.setIconColor(getContext().getColor(R.color.brand));
				Theme.setSelectorDrawableColor(repeatButton.getBackground(), getContext().getColor(R.color.brand) & 0x19ffffff, true);
				if (mode == 0) {
					if (SharedConfig.shuffleMusic) {
						repeatButton.setContentDescription(getContext().getString(R.string.ShuffleList));
					}
					else {
						repeatButton.setContentDescription(getContext().getString(R.string.ReverseOrder));
					}
				}
				else {
					repeatButton.setContentDescription(getContext().getString(R.string.AccDescrRepeatList));
				}
			}
		}
		else if (mode == 2) {
			repeatButton.setIcon(R.drawable.player_new_repeatone);
			repeatButton.setTag(Theme.key_player_buttonActive);
			repeatButton.setIconColor(getContext().getColor(R.color.brand));
			Theme.setSelectorDrawableColor(repeatButton.getBackground(), getContext().getColor(R.color.brand) & 0x19ffffff, true);
			repeatButton.setContentDescription(getContext().getString(R.string.AccDescrRepeatOne));
		}
	}

	private void updateProgress(MessageObject messageObject) {
		updateProgress(messageObject, false);
	}

	private void updateProgress(MessageObject messageObject, boolean animated) {
		if (seekBarView != null) {
			int newTime;
			if (seekBarView.isDragging()) {
				newTime = (int)(messageObject.getDuration() * seekBarView.getProgress());
			}
			else {
				boolean updateRewinding = rewindingProgress >= 0 && (rewindingState == -1 || (rewindingState == 1 && MediaController.getInstance().isMessagePaused()));
				if (updateRewinding) {
					seekBarView.setProgress(rewindingProgress, animated);
				}
				else {
					seekBarView.setProgress(messageObject.audioProgress, animated);
				}

				float bufferedProgress;
				if (currentAudioFinishedLoading) {
					bufferedProgress = 1.0f;
				}
				else {
					long time = SystemClock.elapsedRealtime();
					if (Math.abs(time - lastBufferedPositionCheck) >= 500) {
						bufferedProgress = MediaController.getInstance().isStreamingCurrentAudio() ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(messageObject.audioProgress, currentFile) : 1.0f;
						lastBufferedPositionCheck = time;
					}
					else {
						bufferedProgress = -1;
					}
				}
				if (bufferedProgress != -1) {
					seekBarView.setBufferedProgress(bufferedProgress);
				}
				if (updateRewinding) {
					newTime = (int)(messageObject.getDuration() * seekBarView.getProgress());
					messageObject.audioProgressSec = newTime;
				}
				else {
					newTime = messageObject.audioProgressSec;
				}
			}
			if (lastTime != newTime) {
				lastTime = newTime;
				timeTextView.setText(AndroidUtilities.formatShortDuration(newTime));
			}
		}
	}

	private void checkIfMusicDownloaded(MessageObject messageObject) {
		File cacheFile = null;
		if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() > 0) {
			cacheFile = new File(messageObject.messageOwner.attachPath);
			if (!cacheFile.exists()) {
				cacheFile = null;
			}
		}
		if (cacheFile == null) {
			cacheFile = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner);
		}
		boolean canStream = SharedConfig.streamMedia && (int)messageObject.getDialogId() != 0 && messageObject.isMusic();
		if (!cacheFile.exists() && !canStream) {
			String fileName = messageObject.getFileName();
			DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this);
			Float progress = ImageLoader.getInstance().getFileProgress(fileName);
			progressView.setProgress(progress != null ? progress : 0, false);
			progressView.setVisibility(View.VISIBLE);
			seekBarView.setVisibility(View.INVISIBLE);
			playButton.setEnabled(false);
		}
		else {
			DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
			progressView.setVisibility(View.INVISIBLE);
			seekBarView.setVisibility(View.VISIBLE);
			playButton.setEnabled(true);
		}
	}

	private void updateTitle(boolean shutdown) {
		MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
		if (messageObject == null && shutdown || messageObject != null && !messageObject.isMusic()) {
			dismiss();
		}
		else {
			if (messageObject == null) {
				lastMessageObject = null;
				return;
			}
			final boolean sameMessageObject = messageObject == lastMessageObject;
			lastMessageObject = messageObject;
			if (messageObject.eventId != 0 || messageObject.getId() <= -2000000000) {
				optionsButton.setVisibility(View.INVISIBLE);
			}
			else {
				optionsButton.setVisibility(View.VISIBLE);
			}
			if (MessagesController.getInstance(currentAccount).isChatNoForwards(messageObject.getChatId())) {
				optionsButton.hideSubItem(1);
				optionsButton.hideSubItem(2);
				optionsButton.hideSubItem(5);
				optionsButton.setAdditionalYOffset(-AndroidUtilities.dp(16));
			}
			else {
				optionsButton.showSubItem(1);
				optionsButton.showSubItem(2);
				optionsButton.showSubItem(5);
				optionsButton.setAdditionalYOffset(-AndroidUtilities.dp(157));
			}

			checkIfMusicDownloaded(messageObject);
			updateProgress(messageObject, !sameMessageObject);
			updateCover(messageObject, !sameMessageObject);

			if (MediaController.getInstance().isMessagePaused()) {
				playPauseDrawable.setPause(false);
				playButton.setContentDescription(getContext().getString(R.string.AccActionPlay));
			}
			else {
				playPauseDrawable.setPause(true);
				playButton.setContentDescription(getContext().getString(R.string.AccActionPause));
			}
			String title = messageObject.getMusicTitle();
			String author = messageObject.getMusicAuthor();
			titleTextView.setText(title);
			authorTextView.setText(author);

			int duration = lastDuration = messageObject.getDuration();

			if (durationTextView != null) {
				durationTextView.setText(duration != 0 ? AndroidUtilities.formatShortDuration(duration) : "-:--");
			}

			if (duration > 60 * 10) {
				playbackSpeedButton.setVisibility(View.VISIBLE);
			}
			else {
				playbackSpeedButton.setVisibility(View.GONE);
			}

			if (!sameMessageObject) {
				preloadNeighboringThumbs();
			}
		}
	}

	private void updateCover(MessageObject messageObject, boolean animated) {
		final BackupImageView imageView = animated ? coverContainer.getNextImageView() : coverContainer.getImageView();
		final AudioInfo audioInfo = MediaController.getInstance().getAudioInfo();
		if (audioInfo != null && audioInfo.getCover() != null) {
			imageView.setImageBitmap(audioInfo.getCover());
			currentFile = null;
			currentAudioFinishedLoading = true;
		}
		else {
			TLRPC.Document document = messageObject.getDocument();
			currentFile = FileLoader.getAttachFileName(document);
			currentAudioFinishedLoading = false;
			String artworkUrl = messageObject.getArtworkUrl(false);
			final ImageLocation thumbImageLocation = getArtworkThumbImageLocation(messageObject);
			if (!TextUtils.isEmpty(artworkUrl)) {
				imageView.setImage(ImageLocation.getForPath(artworkUrl), null, thumbImageLocation, null, null, 0, 1, messageObject);
			}
			else if (thumbImageLocation != null) {
				imageView.setImage(null, null, thumbImageLocation, null, null, 0, 1, messageObject);
			}
			else {
				imageView.setImageDrawable(null);
			}
			imageView.invalidate();
		}
		if (animated) {
			coverContainer.switchImageViews();
		}
	}

	private ImageLocation getArtworkThumbImageLocation(MessageObject messageObject) {
		final TLRPC.Document document = messageObject.getDocument();
		TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(TLRPCExtensions.getThumbs(document), 360) : null;
		if (!(thumb instanceof TLRPC.TLPhotoSize) && !(thumb instanceof TLRPC.TLPhotoSizeProgressive)) {
			thumb = null;
		}
		if (thumb != null) {
			return ImageLocation.getForDocument(thumb, document);
		}
		else {
			final String smallArtworkUrl = messageObject.getArtworkUrl(true);
			if (smallArtworkUrl != null) {
				return ImageLocation.getForPath(smallArtworkUrl);
			}
		}
		return null;
	}

	private void preloadNeighboringThumbs() {
		final MediaController mediaController = MediaController.getInstance();
		final List<MessageObject> playlist = mediaController.getPlaylist();
		if (playlist.size() <= 1) {
			return;
		}

		final List<MessageObject> neighboringItems = new ArrayList<>();
		final int playingIndex = mediaController.getPlayingMessageObjectNum();

		int nextIndex = playingIndex + 1;
		int prevIndex = playingIndex - 1;
		if (nextIndex >= playlist.size()) {
			nextIndex = 0;
		}
		if (prevIndex <= -1) {
			prevIndex = playlist.size() - 1;
		}

		neighboringItems.add(playlist.get(nextIndex));
		if (nextIndex != prevIndex) {
			neighboringItems.add(playlist.get(prevIndex));
		}

		for (int i = 0, N = neighboringItems.size(); i < N; i++) {
			final MessageObject messageObject = neighboringItems.get(i);
			final ImageLocation thumbImageLocation = getArtworkThumbImageLocation(messageObject);
			if (thumbImageLocation != null) {
				if (thumbImageLocation.path != null) {
					ImageLoader.getInstance().preloadArtwork(thumbImageLocation.path);
				}
				else {
					FileLoader.getInstance(currentAccount).loadFile(thumbImageLocation, messageObject, null, FileLoader.PRIORITY_LOW, 1);
				}
			}
		}
	}

	private static abstract class CoverContainer extends FrameLayout {
		private final BackupImageView[] imageViews = new BackupImageView[2];
		private int activeIndex;
		private AnimatorSet animatorSet;

		public CoverContainer(@NonNull Context context) {
			super(context);
			for (int i = 0; i < 2; i++) {
				imageViews[i] = new BackupImageView(context);
				final int index = i;

				imageViews[i].imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
					@Override
					public void onAnimationReady(@NonNull ImageReceiver imageReceiver) {
						// unused
					}

					@Override
					public void didSetImage(@NonNull ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
						if (index == activeIndex) {
							onImageUpdated(imageReceiver);
						}
					}
				});

				imageViews[i].setRoundRadius(AndroidUtilities.dp(4));
				if (i == 1) {
					imageViews[i].setVisibility(GONE);
				}
				addView(imageViews[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
			}
		}

		public final void switchImageViews() {
			if (animatorSet != null) {
				animatorSet.cancel();
			}
			animatorSet = new AnimatorSet();
			activeIndex = activeIndex == 0 ? 1 : 0;

			final BackupImageView prevImageView = imageViews[activeIndex == 0 ? 1 : 0];
			final BackupImageView currImageView = imageViews[activeIndex];

			final boolean hasBitmapImage = prevImageView.imageReceiver.hasBitmapImage();

			currImageView.setAlpha(hasBitmapImage ? 1f : 0f);
			currImageView.setScaleX(0.8f);
			currImageView.setScaleY(0.8f);
			currImageView.setVisibility(VISIBLE);

			if (hasBitmapImage) {
				prevImageView.bringToFront();
			}
			else {
				prevImageView.setVisibility(GONE);
				prevImageView.setImageDrawable(null);
			}

			final ValueAnimator expandAnimator = ValueAnimator.ofFloat(0.8f, 1f);
			expandAnimator.setDuration(125);
			expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
			expandAnimator.addUpdateListener(a -> {
				float animatedValue = (float)a.getAnimatedValue();
				currImageView.setScaleX(animatedValue);
				currImageView.setScaleY(animatedValue);
				if (!hasBitmapImage) {
					currImageView.setAlpha(a.getAnimatedFraction());
				}
			});

			if (hasBitmapImage) {
				final ValueAnimator collapseAnimator = ValueAnimator.ofFloat(prevImageView.getScaleX(), 0.8f);
				collapseAnimator.setDuration(125);
				collapseAnimator.setInterpolator(CubicBezierInterpolator.EASE_IN);
				collapseAnimator.addUpdateListener(a -> {
					float animatedValue = (float)a.getAnimatedValue();
					prevImageView.setScaleX(animatedValue);
					prevImageView.setScaleY(animatedValue);
					final float fraction = a.getAnimatedFraction();
					if (fraction > 0.25f && !currImageView.imageReceiver.hasBitmapImage()) {
						prevImageView.setAlpha(1f - (fraction - 0.25f) * (1f / 0.75f));
					}
				});
				collapseAnimator.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						prevImageView.setVisibility(GONE);
						prevImageView.setImageDrawable(null);
						prevImageView.setAlpha(1f);
					}
				});

				animatorSet.playSequentially(collapseAnimator, expandAnimator);
			}
			else {
				animatorSet.play(expandAnimator);
			}

			animatorSet.start();
		}

		public final BackupImageView getImageView() {
			return imageViews[activeIndex];
		}

		public final BackupImageView getNextImageView() {
			return imageViews[activeIndex == 0 ? 1 : 0];
		}

		public final ImageReceiver getImageReceiver() {
			return getImageView().imageReceiver;
		}

		protected abstract void onImageUpdated(ImageReceiver imageReceiver);
	}

	public abstract static class ClippingTextViewSwitcher extends FrameLayout {

		private final TextView[] textViews = new TextView[2];
		private final float[] clipProgress = new float[]{0f, 0.75f};
		private final int gradientSize = AndroidUtilities.dp(24);

		private final Matrix gradientMatrix;
		private final Paint gradientPaint;
		private final Paint erasePaint;
		private final RectF rectF = new RectF();
		private int activeIndex;
		private AnimatorSet animatorSet;
		private LinearGradient gradientShader;
		private int stableOffset = -1;

		public ClippingTextViewSwitcher(@NonNull Context context) {
			super(context);
			for (int i = 0; i < 2; i++) {
				textViews[i] = createTextView();
				if (i == 1) {
					textViews[i].setAlpha(0f);
					textViews[i].setVisibility(GONE);
				}
				addView(textViews[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
			}
			gradientMatrix = new Matrix();
			gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
			erasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			super.onSizeChanged(w, h, oldw, oldh);
			gradientShader = new LinearGradient(gradientSize, 0, 0, 0, 0, 0xFF000000, Shader.TileMode.CLAMP);
			gradientPaint.setShader(gradientShader);
		}

		@Override
		protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
			final int index = child == textViews[0] ? 0 : 1;
			final boolean result;
			boolean hasStableRect = false;
			if (stableOffset > 0 && textViews[activeIndex].getAlpha() != 1f && textViews[activeIndex].getLayout() != null) {
				float x1 = textViews[activeIndex].getLayout().getPrimaryHorizontal(0);
				float x2 = textViews[activeIndex].getLayout().getPrimaryHorizontal(stableOffset);
				hasStableRect = true;
				if (x1 == x2) {
					hasStableRect = false;
				}
				else if (x2 > x1) {
					rectF.set(x1, 0, x2, getMeasuredHeight());
				}
				else {
					rectF.set(x2, 0, x1, getMeasuredHeight());
				}

				if (hasStableRect && index == activeIndex) {
					canvas.save();
					canvas.clipRect(rectF);
					textViews[0].draw(canvas);
					canvas.restore();
				}
			}
			if (clipProgress[index] > 0f || hasStableRect) {
				final int width = child.getWidth();
				final int height = child.getHeight();
				final int saveCount = canvas.saveLayer(0, 0, width, height, null, Canvas.ALL_SAVE_FLAG);
				result = super.drawChild(canvas, child, drawingTime);
				final float gradientStart = width * (1f - clipProgress[index]);
				final float gradientEnd = gradientStart + gradientSize;
				gradientMatrix.setTranslate(gradientStart, 0);
				gradientShader.setLocalMatrix(gradientMatrix);
				canvas.drawRect(gradientStart, 0, gradientEnd, height, gradientPaint);
				if (width > gradientEnd) {
					canvas.drawRect(gradientEnd, 0, width, height, erasePaint);
				}
				if (hasStableRect) {
					canvas.drawRect(rectF, erasePaint);
				}
				canvas.restoreToCount(saveCount);
			}
			else {
				result = super.drawChild(canvas, child, drawingTime);
			}
			return result;
		}

		public void setText(CharSequence text) {
			setText(text, true);
		}

		public void setText(CharSequence text, boolean animated) {
			final CharSequence currentText = textViews[activeIndex].getText();

			if (TextUtils.isEmpty(currentText) || !animated) {
				textViews[activeIndex].setText(text);
				return;
			}
			else if (TextUtils.equals(text, currentText)) {
				return;
			}

			stableOffset = 0;
			int n = Math.min(text.length(), currentText.length());
			for (int i = 0; i < n; i++) {
				if (text.charAt(i) != currentText.charAt(i)) {
					break;
				}
				stableOffset++;
			}
			if (stableOffset <= 3) {
				stableOffset = -1;
			}

			final int index = activeIndex == 0 ? 1 : 0;
			final int prevIndex = activeIndex;
			activeIndex = index;

			if (animatorSet != null) {
				animatorSet.cancel();
			}
			animatorSet = new AnimatorSet();
			animatorSet.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					textViews[prevIndex].setVisibility(GONE);
				}
			});

			textViews[index].setText(text);
			textViews[index].bringToFront();
			textViews[index].setVisibility(VISIBLE);

			final int duration = 300;

			final ValueAnimator collapseAnimator = ValueAnimator.ofFloat(clipProgress[prevIndex], 0.75f);
			collapseAnimator.setDuration(duration / 3 * 2); // 0.66
			collapseAnimator.addUpdateListener(a -> {
				clipProgress[prevIndex] = (float)a.getAnimatedValue();
				invalidate();
			});

			final ValueAnimator expandAnimator = ValueAnimator.ofFloat(clipProgress[index], 0f);
			expandAnimator.setStartDelay(duration / 3); // 0.33
			expandAnimator.setDuration(duration / 3 * 2); // 0.66
			expandAnimator.addUpdateListener(a -> {
				clipProgress[index] = (float)a.getAnimatedValue();
				invalidate();
			});

			final ObjectAnimator fadeOutAnimator = ObjectAnimator.ofFloat(textViews[prevIndex], View.ALPHA, 0f);
			fadeOutAnimator.setStartDelay(duration / 4); // 0.25
			fadeOutAnimator.setDuration(duration / 2); // 0.5

			final ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(textViews[index], View.ALPHA, 1f);
			fadeInAnimator.setStartDelay(duration / 4); // 0.25
			fadeInAnimator.setDuration(duration / 2); // 0.5

			animatorSet.playTogether(collapseAnimator, expandAnimator, fadeOutAnimator, fadeInAnimator);
			animatorSet.start();
		}

		public TextView getTextView() {
			return textViews[activeIndex];
		}

		public TextView getNextTextView() {
			return textViews[activeIndex == 0 ? 1 : 0];
		}

		protected abstract TextView createTextView();
	}

	private class ListAdapter extends RecyclerListView.SelectionAdapter {

		private final Context context;
		private ArrayList<MessageObject> searchResult = new ArrayList<>();
		private Runnable searchRunnable;

		public ListAdapter(Context context) {
			this.context = context;
		}

		@Override
		public void notifyDataSetChanged() {
			super.notifyDataSetChanged();

			if (playlist.size() > 1) {
				playerLayout.setBackgroundColor(getContext().getColor(R.color.background));
				playerShadow.setVisibility(View.VISIBLE);
				listView.setPadding(0, listView.getPaddingTop(), 0, AndroidUtilities.dp(179));
			}
			else {
				playerLayout.setBackground(null);
				playerShadow.setVisibility(View.INVISIBLE);
				listView.setPadding(0, listView.getPaddingTop(), 0, 0);
			}
			updateEmptyView();
		}

		@Override
		public int getItemCount() {
			if (searchWas) {
				return searchResult.size();
			}
			return playlist.size() > 1 ? playlist.size() : 0;
		}

		@Override
		public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
			return true;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = new AudioPlayerCell(context, MediaController.getInstance().currentPlaylistIsGlobalSearch() ? AudioPlayerCell.VIEW_TYPE_GLOBAL_SEARCH : AudioPlayerCell.VIEW_TYPE_DEFAULT);
			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
			AudioPlayerCell cell = (AudioPlayerCell)holder.itemView;
			if (searchWas) {
				cell.setMessageObject(searchResult.get(position));
			}
			else {
				if (SharedConfig.playOrderReversed) {
					cell.setMessageObject(playlist.get(position));
				}
				else {
					cell.setMessageObject(playlist.get(playlist.size() - position - 1));
				}
			}
		}

		@Override
		public int getItemViewType(int i) {
			return 0;
		}

		public void search(final String query) {
			if (searchRunnable != null) {
				Utilities.searchQueue.cancelRunnable(searchRunnable);
				searchRunnable = null;
			}
			if (query == null) {
				searchResult.clear();
				notifyDataSetChanged();
			}
			else {
				Utilities.searchQueue.postRunnable(searchRunnable = () -> {
					searchRunnable = null;
					processSearch(query);
				}, 300);
			}
		}

		private void processSearch(final String query) {
			AndroidUtilities.runOnUIThread(() -> {
				final ArrayList<MessageObject> copy = new ArrayList<>(playlist);
				Utilities.searchQueue.postRunnable(() -> {
					String search1 = query.trim().toLowerCase();
					if (search1.length() == 0) {
						updateSearchResults(new ArrayList<>(), query);
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

					ArrayList<MessageObject> resultArray = new ArrayList<>();

					for (int a = 0; a < copy.size(); a++) {
						MessageObject messageObject = copy.get(a);
						for (String q : search) {
							String name = messageObject.getDocumentName();
							if (name == null || name.length() == 0) {
								continue;
							}
							name = name.toLowerCase();
							if (name.contains(q)) {
								resultArray.add(messageObject);
								break;
							}

							TLRPC.Document document = null;

							if (messageObject.type == 0) {
								var webpage = TLRPCExtensions.getWebpage(TLRPCExtensions.getMedia(messageObject.messageOwner));

								if (webpage != null) {
									document = TLRPCExtensions.getDocument(webpage);
								}
							}
							else {
								document = TLRPCExtensions.getDocument(TLRPCExtensions.getMedia(messageObject.messageOwner));
							}

							boolean ok = false;

							if (document instanceof TLRPC.TLDocument tlDocument) {
								for (int c = 0; c < tlDocument.attributes.size(); c++) {
									TLRPC.DocumentAttribute attribute = tlDocument.attributes.get(c);
									if (attribute instanceof TLRPC.TLDocumentAttributeAudio attr) {
										if (attr.performer != null) {
											ok = attr.performer.toLowerCase().contains(q);
										}
										if (!ok && attr.title != null) {
											ok = attr.title.toLowerCase().contains(q);
										}
										break;
									}
								}
							}

							if (ok) {
								resultArray.add(messageObject);
								break;
							}
						}
					}

					updateSearchResults(resultArray, query);
				});
			});
		}

		private void updateSearchResults(final ArrayList<MessageObject> documents, String query) {
			AndroidUtilities.runOnUIThread(() -> {
				if (!searching) {
					return;
				}
				searchWas = true;
				searchResult = documents;
				notifyDataSetChanged();
				layoutManager.scrollToPosition(0);
				emptySubtitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("NoAudioFoundPlayerInfo", R.string.NoAudioFoundPlayerInfo, query)));
			});
		}
	}
}
