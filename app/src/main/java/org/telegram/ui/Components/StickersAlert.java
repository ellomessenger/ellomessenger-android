/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.messenger.messageobject.SendAnimationData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.TLDocument;
import org.telegram.tgnet.TLRPC.TLPhoto;
import org.telegram.tgnet.TLRPC.TLStickersSuggestedShortName;
import org.telegram.tgnet.TLRPCExtensions;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

@SuppressLint("NotifyDataSetChanged")
public class StickersAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {
	public boolean probablyEmojis;
	private Pattern urlPattern;
	private RecyclerListView gridView;
	private GridAdapter adapter;
	private TextView titleTextView;
	private TextView descriptionTextView;
	private ActionBarMenuItem optionsButton;
	private TextView pickerBottomLayout;
	private PremiumButtonView premiumButtonView;
	private FrameLayout pickerBottomFrameLayout;
	private FrameLayout stickerPreviewLayout;
	private TextView previewSendButton;
	private View previewSendButtonShadow;
	private BackupImageView stickerImageView;
	private TextView stickerEmojiTextView;
	private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
	private final AnimatorSet[] shadowAnimation = new AnimatorSet[2];
	private final View[] shadow = new View[2];
	private FrameLayout emptyView;
	private BaseFragment parentFragment;
	private GridLayoutManager layoutManager;
	private Activity parentActivity;
	private int itemSize, itemHeight;
	private TLRPC.TLMessagesStickerSet stickerSet;
	private TLRPC.Document selectedSticker;
	private SendMessagesHelper.ImportingSticker selectedStickerPath;
	private TLRPC.InputStickerSet inputStickerSet;
	private List<TLRPC.StickerSetCovered> stickerSetCovereds;
	private List<Parcelable> importingStickers;
	private List<SendMessagesHelper.ImportingSticker> importingStickersPaths;
	private Map<String, SendMessagesHelper.ImportingSticker> uploadImportStickers;
	private String importingSoftware;
	private StickersAlertDelegate delegate;
	private StickersAlertInstallDelegate installDelegate;
	private StickersAlertCustomButtonDelegate customButtonDelegate;
	private int scrollOffsetY;
	private int reqId;
	private boolean ignoreLayout;
	private boolean showEmoji;
	private boolean clearsInputField;
	private boolean showTooltipWhenToggle = true;
	private Runnable checkRunnable;
	private String lastCheckName;
	private int checkReqId;
	private boolean lastNameAvailable;
	private String setTitle;
	private Runnable onDismissListener;
	private final ContentPreviewViewer.ContentPreviewViewerDelegate previewDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
		@Override
		public boolean can() {
			return stickerSet == null || stickerSet.set == null || !stickerSet.set.emojis;
		}

		@Override
		public void sendSticker(TLRPC.Document sticker, String query, Object parent, boolean notify, int scheduleDate) {
			if (delegate == null) {
				return;
			}
			delegate.onStickerSelected(sticker, query, parent, null, clearsInputField, notify, scheduleDate);
			dismiss();
		}

		@Override
		public boolean canSchedule() {
			return delegate != null && delegate.canSchedule();
		}

		@Override
		public boolean isInScheduleMode() {
			return delegate != null && delegate.isInScheduleMode();
		}

		@Override
		public void openSet(TLRPC.InputStickerSet set, boolean clearsInputField) {

		}

		@Override
		public boolean needRemove() {
			return importingStickers != null;
		}

		@Override
		public void remove(SendMessagesHelper.ImportingSticker importingSticker) {
			removeSticker(importingSticker);
		}

		@Override
		public boolean needSend() {
			return delegate != null;
		}

		@Override
		public boolean needOpen() {
			return false;
		}

		@Override
		public long getDialogId() {
			if (parentFragment instanceof ChatActivity) {
				return ((ChatActivity)parentFragment).getDialogId();
			}
			return 0;
		}
	};
	private List<ThemeDescription> animatingDescriptions;

	public StickersAlert(Context context, Object parentObject, TLObject object) {
		super(context, false);
		fixNavigationBar();
		parentActivity = (Activity)context;
		final TLRPC.TLMessagesGetAttachedStickers req = new TLRPC.TLMessagesGetAttachedStickers();
		if (object instanceof TLPhoto photo) {

			var inputPhoto = new TLRPC.TLInputPhoto();
			inputPhoto.id = photo.id;
			inputPhoto.accessHash = photo.accessHash;
			inputPhoto.fileReference = photo.fileReference;

			if (inputPhoto.fileReference == null) {
				inputPhoto.fileReference = new byte[0];
			}

			TLRPC.TLInputStickeredMediaPhoto inputStickeredMediaPhoto = new TLRPC.TLInputStickeredMediaPhoto();
			inputStickeredMediaPhoto.id = inputPhoto;

			req.media = inputStickeredMediaPhoto;
		}
		else if (object instanceof TLDocument document) {
			var inputDocument = new TLRPC.TLInputDocument();
			inputDocument.id = document.id;
			inputDocument.accessHash = document.accessHash;
			inputDocument.fileReference = document.fileReference;

			if (inputDocument.fileReference == null) {
				inputDocument.fileReference = new byte[0];
			}

			TLRPC.TLInputStickeredMediaDocument inputStickeredMediaDocument = new TLRPC.TLInputStickeredMediaDocument();
			inputStickeredMediaDocument.id = inputDocument;

			req.media = inputStickeredMediaDocument;
		}
		RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			reqId = 0;
			if (error == null) {
				Vector vector = (Vector)response;
				if (vector.objects.isEmpty()) {
					dismiss();
				}
				else if (vector.objects.size() == 1) {
					TLRPC.StickerSetCovered set = (TLRPC.StickerSetCovered)vector.objects.get(0);

					var s = new TLRPC.TLInputStickerSetID();
					s.id = set.set.id;
					s.accessHash = set.set.accessHash;

					inputStickerSet = s;

					loadStickerSet();
				}
				else {
					stickerSetCovereds = new ArrayList<>();
					for (int a = 0; a < vector.objects.size(); a++) {
						stickerSetCovereds.add((TLRPC.StickerSetCovered)vector.objects.get(a));
					}
					gridView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));
					titleTextView.setVisibility(View.GONE);
					shadow[0].setVisibility(View.GONE);
					adapter.notifyDataSetChanged();
				}
			}
			else {
				AlertsCreator.processError(currentAccount, error, parentFragment, req);
				dismiss();
			}
		});
		reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
			if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
				FileRefController.getInstance(currentAccount).requestReference(parentObject, req, requestDelegate);
				return;
			}
			requestDelegate.run(response, error);
		});
		init(context);
	}

	public StickersAlert(Context context, String software, List<Parcelable> uris, List<String> emoji) {
		super(context, false);
		fixNavigationBar();
		parentActivity = (Activity)context;
		importingStickers = uris;
		importingSoftware = software;
		Utilities.globalQueue.postRunnable(() -> {
			ArrayList<SendMessagesHelper.ImportingSticker> stickers = new ArrayList<>();
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			Boolean isAnimated = null;
			for (int a = 0, N = uris.size(); a < N; a++) {
				Object obj = uris.get(a);
				if (obj instanceof Uri uri) {
					String ext = MediaController.getStickerExt(uri);
					if (ext == null) {
						continue;
					}
					boolean animated = "tgs".equals(ext);
					if (isAnimated == null) {
						isAnimated = animated;
					}
					else if (isAnimated != animated) {
						continue;
					}
					if (isDismissed()) {
						return;
					}
					SendMessagesHelper.ImportingSticker importingSticker = new SendMessagesHelper.ImportingSticker();
					importingSticker.animated = animated;
					importingSticker.path = MediaController.copyFileToCache(uri, ext, (animated ? 64 : 512) * 1024);
					if (importingSticker.path == null) {
						continue;
					}
					if (!animated) {
						BitmapFactory.decodeFile(importingSticker.path, opts);
						if ((opts.outWidth != 512 || opts.outHeight <= 0 || opts.outHeight > 512) && (opts.outHeight != 512 || opts.outWidth <= 0 || opts.outWidth > 512)) {
							continue;
						}
						importingSticker.mimeType = "image/" + ext;
						importingSticker.validated = true;
					}
					else {
						importingSticker.mimeType = "application/x-tgsticker";
					}
					if (emoji != null && emoji.size() == N && emoji.get(a) instanceof String) {
						importingSticker.emoji = emoji.get(a);
					}
					else {
						importingSticker.emoji = "#️⃣";
					}
					stickers.add(importingSticker);
					if (stickers.size() >= 200) {
						break;
					}
				}
			}
			Boolean isAnimatedFinal = isAnimated;
			AndroidUtilities.runOnUIThread(() -> {
				importingStickersPaths = stickers;
				if (importingStickersPaths.isEmpty()) {
					dismiss();
				}
				else {
					adapter.notifyDataSetChanged();
					if (isAnimatedFinal) {
						uploadImportStickers = new HashMap<>();
						for (int a = 0, N = importingStickersPaths.size(); a < N; a++) {
							SendMessagesHelper.ImportingSticker sticker = importingStickersPaths.get(a);
							uploadImportStickers.put(sticker.path, sticker);
							FileLoader.getInstance(currentAccount).uploadFile(sticker.path, false, true, ConnectionsManager.FileTypeFile);
						}
					}
					updateFields();
				}
			});
		});
		init(context);
	}

	public StickersAlert(Context context, BaseFragment baseFragment, TLRPC.InputStickerSet set, TLRPC.TLMessagesStickerSet loadedSet, StickersAlertDelegate stickersAlertDelegate) {
		super(context, false);
		fixNavigationBar();
		delegate = stickersAlertDelegate;
		inputStickerSet = set;
		stickerSet = loadedSet;
		parentFragment = baseFragment;
		loadStickerSet();
		init(context);
	}

	public boolean isClearsInputField() {
		return clearsInputField;
	}

	public void setClearsInputField(boolean value) {
		clearsInputField = value;
	}

	private void loadStickerSet() {
		if (inputStickerSet != null) {
			final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
			final var shortName = TLRPCExtensions.getShortName(inputStickerSet);
			final var id = TLRPCExtensions.getId(inputStickerSet);

			if (stickerSet == null && shortName != null) {
				stickerSet = mediaDataController.getStickerSetByName(shortName);
			}
			if (stickerSet == null) {
				stickerSet = mediaDataController.getStickerSetById(id);
			}
			if (stickerSet == null) {
				TLRPC.TLMessagesGetStickerSet req = new TLRPC.TLMessagesGetStickerSet();
				req.stickerset = inputStickerSet;
				ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
					reqId = 0;
					if (error == null) {
						Transition addTarget = new Transition() {

							@Override
							public void captureStartValues(TransitionValues transitionValues) {
								transitionValues.values.put("start", true);
								transitionValues.values.put("offset", containerView.getTop() + scrollOffsetY);
							}

							@Override
							public void captureEndValues(TransitionValues transitionValues) {
								transitionValues.values.put("start", false);
								transitionValues.values.put("offset", containerView.getTop() + scrollOffsetY);
							}

							@Override
							public Animator createAnimator(@NonNull ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
								int scrollOffsetY = StickersAlert.this.scrollOffsetY;
								int startValue = (int)startValues.values.get("offset") - (int)endValues.values.get("offset");
								final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
								animator.setDuration(250);
								animator.addUpdateListener(a -> {
									float fraction = a.getAnimatedFraction();
									gridView.setAlpha(fraction);
									titleTextView.setAlpha(fraction);
									if (startValue != 0) {
										int value = (int)(startValue * (1f - fraction));
										setScrollOffsetY(scrollOffsetY + value);
										gridView.setTranslationY(value);
									}
								});
								return animator;
							}
						};
						addTarget.addTarget(containerView);
						TransitionManager.beginDelayedTransition(container, addTarget);
						optionsButton.setVisibility(View.VISIBLE);
						stickerSet = (TLRPC.TLMessagesStickerSet)response;
						showEmoji = !stickerSet.set.masks;
						checkPremiumStickers();
						mediaDataController.preloadStickerSetThumb(stickerSet);
						updateSendButton();
						updateFields();
						updateDescription();
						adapter.notifyDataSetChanged();
					}
					else {
						dismiss();
						BulletinFactory.of(parentFragment).createErrorBulletin(getContext().getString(R.string.AddStickersNotFound)).show();
					}
				}));
			}
			else {
				if (adapter != null) {
					updateSendButton();
					updateFields();
					adapter.notifyDataSetChanged();
				}
				updateDescription();
				mediaDataController.preloadStickerSetThumb(stickerSet);
				checkPremiumStickers();
			}
		}
		if (stickerSet != null) {
			showEmoji = !stickerSet.set.masks;
		}
		checkPremiumStickers();
	}

	private void checkPremiumStickers() {
		if (stickerSet != null) {
			stickerSet = MessagesController.getInstance(currentAccount).filterPremiumStickers(stickerSet);
			if (stickerSet == null) {
				dismiss();
			}
		}
	}

	private boolean isEmoji() {
		return stickerSet != null && stickerSet.set != null && stickerSet.set.emojis || stickerSet == null && probablyEmojis;
	}

	private void init(Context context) {
		containerView = new FrameLayout(context) {

			private int lastNotifyWidth;
			private final RectF rect = new RectF();
			private boolean fullHeight;
			private Boolean statusBarOpen;

			@Override
			public boolean onInterceptTouchEvent(MotionEvent ev) {
				if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
					dismiss();
					return true;
				}
				return super.onInterceptTouchEvent(ev);
			}

			@Override
			public boolean onTouchEvent(MotionEvent e) {
				return !isDismissed() && super.onTouchEvent(e);
			}

			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				int height = MeasureSpec.getSize(heightMeasureSpec);
				ignoreLayout = true;
				setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
				ignoreLayout = false;
				if (isEmoji()) {
					int width = gridView.getMeasuredWidth();
					if (width == 0) {
						width = AndroidUtilities.displaySize.x;
					}
					adapter.stickersPerRow = Math.max(1, width / AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 45));
					itemSize = (MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(36)) / adapter.stickersPerRow;
					itemHeight = itemSize;
				}
				else {
					adapter.stickersPerRow = 5;
					itemSize = (MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(36)) / adapter.stickersPerRow;
					itemHeight = AndroidUtilities.dp(82);
				}
				float spansCount = adapter.stickersPerRow;
				int contentSize;
				MarginLayoutParams params = (MarginLayoutParams)gridView.getLayoutParams();
				if (importingStickers != null) {
					contentSize = AndroidUtilities.dp(48) + params.bottomMargin + Math.max(3, (int)Math.ceil(importingStickers.size() / spansCount)) * itemHeight + backgroundPaddingTop + AndroidUtilities.statusBarHeight;
				}
				else if (stickerSetCovereds != null) {
					contentSize = AndroidUtilities.dp(8) + params.bottomMargin + AndroidUtilities.dp(60) * stickerSetCovereds.size() + adapter.stickersRowCount * itemHeight + backgroundPaddingTop + AndroidUtilities.dp(24);
				}
				else {
					contentSize = AndroidUtilities.dp(48) + params.bottomMargin + (Math.max(isEmoji() ? 2 : 3, (stickerSet != null ? (int)Math.ceil(stickerSet.documents.size() / spansCount) : 0))) * itemHeight + backgroundPaddingTop + AndroidUtilities.statusBarHeight;
				}
				if (isEmoji()) {
					contentSize += itemHeight * .15f;
				}
				if (descriptionTextView != null) {
					descriptionTextView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(9999, MeasureSpec.AT_MOST));
					contentSize += descriptionTextView.getMeasuredHeight();
				}
				int padding = contentSize < (height / 5f * 3.2) ? 0 : (int)(height / 5f * 2);
				if (padding != 0 && contentSize < height) {
					padding -= (height - contentSize);
				}
				if (padding == 0) {
					padding = backgroundPaddingTop;
				}
				if (descriptionTextView != null) {
					padding += AndroidUtilities.dp(32) + descriptionTextView.getMeasuredHeight();
				}
				if (stickerSetCovereds != null) {
					padding += AndroidUtilities.dp(8);
				}
				if (gridView.getPaddingTop() != padding) {
					ignoreLayout = true;
					gridView.setPadding(AndroidUtilities.dp(10), padding, AndroidUtilities.dp(10), AndroidUtilities.dp(8));
					emptyView.setPadding(0, padding, 0, 0);
					ignoreLayout = false;
				}
				fullHeight = contentSize >= height;
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, height), MeasureSpec.EXACTLY));
			}

			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				if (lastNotifyWidth != right - left) {
					lastNotifyWidth = right - left;
					if (adapter != null && stickerSetCovereds != null) {
						adapter.notifyDataSetChanged();
					}
				}
				super.onLayout(changed, left, top, right, bottom);
				updateLayout();
			}

			@Override
			public void requestLayout() {
				if (ignoreLayout) {
					return;
				}
				super.requestLayout();
			}

			private void updateLightStatusBar(boolean open) {
				if (statusBarOpen != null && statusBarOpen == open) {
					return;
				}
				boolean openBgLight = AndroidUtilities.computePerceivedBrightness(context.getColor(R.color.background)) > .721f;
				boolean closedBgLight = AndroidUtilities.computePerceivedBrightness(Theme.blendOver(context.getColor(R.color.background), 0x33000000)) > .721f;
				boolean isLight = open ? openBgLight : closedBgLight;
				AndroidUtilities.setLightStatusBar(getWindow(), isLight);
			}

			@Override
			protected void onDraw(@NonNull Canvas canvas) {
				int y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6);
				int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13);
				int height = getMeasuredHeight() + AndroidUtilities.dp(15) + backgroundPaddingTop;
				int statusBarHeight = 0;
				float radProgress = 1.0f;
				top += AndroidUtilities.statusBarHeight;
				y += AndroidUtilities.statusBarHeight;
				height -= AndroidUtilities.statusBarHeight;

				if (fullHeight) {
					if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
						int diff = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop);
						top -= diff;
						height += diff;
						radProgress = 1.0f - Math.min(1.0f, (diff * 2) / (float)AndroidUtilities.statusBarHeight);
					}
					if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
						statusBarHeight = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop);
					}
				}

				shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
				shadowDrawable.draw(canvas);

				if (radProgress != 1.0f) {
					Theme.dialogs_onlineCirclePaint.setColor(context.getColor(R.color.background));
					rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
					canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
				}

				int w = AndroidUtilities.dp(36);
				rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
				Theme.dialogs_onlineCirclePaint.setColor(context.getColor(R.color.light_background));
				Theme.dialogs_onlineCirclePaint.setAlpha((int)(255 * Math.max(0, Math.min(1f, (y - AndroidUtilities.statusBarHeight) / (float)AndroidUtilities.dp(16)))));
				canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

				updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2);
				if (statusBarHeight > 0) {
					Theme.dialogs_onlineCirclePaint.setColor(context.getColor(R.color.background));
					canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
				}
			}
		};
		containerView.setWillNotDraw(false);
		containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

		FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
		frameLayoutParams.topMargin = AndroidUtilities.dp(48);
		shadow[0] = new View(context);
		shadow[0].setBackgroundColor(context.getColor(R.color.shadow));
		shadow[0].setAlpha(0.0f);
		shadow[0].setVisibility(View.INVISIBLE);
		shadow[0].setTag(1);
		containerView.addView(shadow[0], frameLayoutParams);

		gridView = new RecyclerListView(context) {
			@Override
			public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
				boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, gridView, 0, previewDelegate);
				return super.onInterceptTouchEvent(event) || result;
			}

			@Override
			public void requestLayout() {
				if (ignoreLayout) {
					return;
				}
				super.requestLayout();
			}
		};
		gridView.setTag(14);
		gridView.setLayoutManager(layoutManager = new GridLayoutManager(getContext(), 5) {
			@Override
			protected boolean isLayoutRTL() {
				return stickerSetCovereds != null && LocaleController.isRTL;
			}
		});
		layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				if (stickerSetCovereds != null && adapter.cache.get(position) instanceof Integer || position == adapter.totalItems) {
					return adapter.stickersPerRow;
				}
				return 1;
			}
		});
		gridView.setAdapter(adapter = new GridAdapter(context));
		gridView.setVerticalScrollBarEnabled(false);
		gridView.addItemDecoration(new RecyclerView.ItemDecoration() {
			@Override
			public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
				outRect.left = 0;
				outRect.right = 0;
				outRect.bottom = 0;
				outRect.top = 0;
			}
		});
		gridView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
		gridView.setClipToPadding(false);
		gridView.setEnabled(true);
		gridView.setGlowColor(context.getColor(R.color.light_background));
		gridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, gridView, 0, stickersOnItemClickListener, previewDelegate));
		gridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				updateLayout();
			}
		});
		stickersOnItemClickListener = (view, position) -> {
			if (stickerSetCovereds != null) {
				TLRPC.StickerSetCovered pack = adapter.positionsToSets.get(position);
				if (pack != null) {
					dismiss();
					TLRPC.TLInputStickerSetID inputStickerSetID = new TLRPC.TLInputStickerSetID();
					inputStickerSetID.accessHash = pack.set.accessHash;
					inputStickerSetID.id = pack.set.id;
					StickersAlert alert = new StickersAlert(parentActivity, parentFragment, inputStickerSetID, null, null);
					alert.show();
				}
			}
			else if (importingStickersPaths != null) {
				if (position < 0 || position >= importingStickersPaths.size()) {
					return;
				}
				selectedStickerPath = importingStickersPaths.get(position);
				if (!selectedStickerPath.validated) {
					return;
				}
				stickerEmojiTextView.setText(Emoji.replaceEmoji(selectedStickerPath.emoji, stickerEmojiTextView.getPaint().getFontMetricsInt(), false));
				stickerImageView.setImage(ImageLocation.getForPath(selectedStickerPath.path), null, null, null, null, null, selectedStickerPath.animated ? "tgs" : null, 0, null);
				FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)stickerPreviewLayout.getLayoutParams();
				layoutParams.topMargin = scrollOffsetY;
				stickerPreviewLayout.setLayoutParams(layoutParams);
				stickerPreviewLayout.setVisibility(View.VISIBLE);
				AnimatorSet animatorSet = new AnimatorSet();
				animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, View.ALPHA, 0.0f, 1.0f));
				animatorSet.setDuration(200);
				animatorSet.start();
			}
			else {
				if (stickerSet == null || position < 0 || position >= stickerSet.documents.size()) {
					return;
				}
				selectedSticker = stickerSet.documents.get(position);

				boolean set = false;

				if (selectedSticker instanceof TLRPC.TLDocument tlDocument) {
					for (TLRPC.DocumentAttribute attribute : tlDocument.attributes) {
						if (attribute instanceof TLRPC.TLDocumentAttributeSticker) {
							if (attribute.alt != null && attribute.alt.length() > 0) {
								stickerEmojiTextView.setText(Emoji.replaceEmoji(attribute.alt, stickerEmojiTextView.getPaint().getFontMetricsInt(), false));
								set = true;
							}
							break;
						}
					}
				}

				if (!set) {
					stickerEmojiTextView.setText(Emoji.replaceEmoji(MediaDataController.getInstance(currentAccount).getEmojiForSticker(selectedSticker.id), stickerEmojiTextView.getPaint().getFontMetricsInt(), false));
				}

				if ((stickerSet == null || stickerSet.set == null || !stickerSet.set.emojis) && !ContentPreviewViewer.getInstance().showMenuFor(view)) {
					TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(TLRPCExtensions.getThumbs(selectedSticker), 90);
					stickerImageView.imageReceiver.setImage(ImageLocation.getForDocument(selectedSticker), null, ImageLocation.getForDocument(thumb, selectedSticker), null, "webp", stickerSet, 1);
					FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)stickerPreviewLayout.getLayoutParams();
					layoutParams.topMargin = scrollOffsetY;
					stickerPreviewLayout.setLayoutParams(layoutParams);
					stickerPreviewLayout.setVisibility(View.VISIBLE);
					AnimatorSet animatorSet = new AnimatorSet();
					animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, View.ALPHA, 0.0f, 1.0f));
					animatorSet.setDuration(200);
					animatorSet.start();
				}
			}
		};
		gridView.setOnItemClickListener(stickersOnItemClickListener);
		containerView.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 48));

		emptyView = new FrameLayout(context) {
			@Override
			public void requestLayout() {
				if (ignoreLayout) {
					return;
				}
				super.requestLayout();
			}
		};
		containerView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));
		gridView.setEmptyView(emptyView);
		emptyView.setOnTouchListener((v, event) -> true);

		titleTextView = new TextView(context);
		titleTextView.setLines(1);
		titleTextView.setSingleLine(true);
		titleTextView.setTextColor(context.getColor(R.color.text));
		titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
		titleTextView.setLinkTextColor(context.getColor(R.color.brand));
		titleTextView.setEllipsize(TextUtils.TruncateAt.END);
		titleTextView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
		titleTextView.setGravity(Gravity.CENTER_VERTICAL);
		titleTextView.setTypeface(Theme.TYPEFACE_BOLD);
		containerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 40, 0));

		optionsButton = new ActionBarMenuItem(context, null, 0, context.getColor(R.color.text));
		optionsButton.setLongClickEnabled(false);
		optionsButton.setSubMenuOpenSide(2);
		optionsButton.setIcon(R.drawable.overflow_menu);
		optionsButton.setBackground(Theme.createSelectorDrawable(context.getColor(R.color.light_background), 1));

		containerView.addView(optionsButton, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 5, 5, 0));

		optionsButton.addSubItem(1, R.drawable.msg_share, context.getString(R.string.StickersShare));
		optionsButton.addSubItem(2, R.drawable.msg_link, context.getString(R.string.CopyLink));
		optionsButton.setOnClickListener(v -> optionsButton.toggleSubMenu());
		optionsButton.setDelegate(this::onSubItemClick);
		optionsButton.setContentDescription(context.getString(R.string.AccDescrMoreOptions));
		optionsButton.setVisibility(inputStickerSet != null ? View.VISIBLE : View.GONE);

		RadialProgressView progressView = new RadialProgressView(context);
		emptyView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

		frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
		frameLayoutParams.bottomMargin = AndroidUtilities.dp(48);
		shadow[1] = new View(context);
		shadow[1].setBackgroundColor(context.getColor(R.color.shadow));
		containerView.addView(shadow[1], frameLayoutParams);

		pickerBottomLayout = new TextView(context);
		pickerBottomLayout.setBackground(Theme.createSelectorWithBackgroundDrawable(context.getColor(R.color.background), context.getColor(R.color.light_background)));
		pickerBottomLayout.setTextColor(context.getColor(R.color.brand));
		pickerBottomLayout.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		pickerBottomLayout.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
		pickerBottomLayout.setTypeface(Theme.TYPEFACE_BOLD);
		pickerBottomLayout.setGravity(Gravity.CENTER);

		pickerBottomFrameLayout = new FrameLayout(context);
		pickerBottomFrameLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
		containerView.addView(pickerBottomFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

		premiumButtonView = new PremiumButtonView(context, false);
		premiumButtonView.setIcon(R.raw.unlock_icon);
		premiumButtonView.setVisibility(View.INVISIBLE);
		containerView.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 8, 0, 8, 8));

		stickerPreviewLayout = new FrameLayout(context);
		stickerPreviewLayout.setVisibility(View.GONE);
		stickerPreviewLayout.setSoundEffectsEnabled(false);
		containerView.addView(stickerPreviewLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
		stickerPreviewLayout.setOnClickListener(v -> hidePreview());

		stickerImageView = new BackupImageView(context);
		stickerImageView.setAspectFit(true);
		stickerImageView.setLayerNum(7);
		stickerPreviewLayout.addView(stickerImageView);

		stickerEmojiTextView = new TextView(context);
		stickerEmojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
		stickerEmojiTextView.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
		stickerPreviewLayout.addView(stickerEmojiTextView);

		previewSendButton = new TextView(context);
		previewSendButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		previewSendButton.setTextColor(context.getColor(R.color.brand));
		previewSendButton.setBackground(Theme.createSelectorWithBackgroundDrawable(context.getColor(R.color.background), context.getColor(R.color.light_background)));
		previewSendButton.setGravity(Gravity.CENTER);
		previewSendButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
		previewSendButton.setTypeface(Theme.TYPEFACE_BOLD);
		stickerPreviewLayout.addView(previewSendButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
		previewSendButton.setOnClickListener(v -> {
			if (importingStickersPaths != null) {
				removeSticker(selectedStickerPath);
				hidePreview();
				selectedStickerPath = null;
			}
			else {
				delegate.onStickerSelected(selectedSticker, null, stickerSet, null, clearsInputField, true, 0);
				dismiss();
			}
		});

		frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
		frameLayoutParams.bottomMargin = AndroidUtilities.dp(48);
		previewSendButtonShadow = new View(context);
		previewSendButtonShadow.setBackgroundColor(context.getColor(R.color.shadow));
		stickerPreviewLayout.addView(previewSendButtonShadow, frameLayoutParams);

		NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
		if (importingStickers != null) {
			NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
			NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
		}

		updateFields();
		updateSendButton();
		updateDescription();
		updateColors();
		adapter.notifyDataSetChanged();
	}

	private void updateDescription() {
		if (containerView == null) {
			return;
		}
//		if (!UserConfig.getInstance(currentAccount).isPremium() && MessageObject.isPremiumEmojiPack(stickerSet)) {
//            descriptionTextView = new TextView(getContext());
//            descriptionTextView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription));
//            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
//            descriptionTextView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
//            descriptionTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("PremiumPreviewEmojiPack", R.string.PremiumPreviewEmojiPack)));
//            containerView.addView(descriptionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 50, 40, 0));
//		}
	}

	private void updateSendButton() {
		int size = (int)(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / 2 / AndroidUtilities.density);
		if (importingStickers != null) {
			previewSendButton.setText(getContext().getString(R.string.ImportStickersRemove));
			previewSendButton.setTextColor(getContext().getColor(R.color.purple));
			stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
			stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
			previewSendButton.setVisibility(View.VISIBLE);
			previewSendButtonShadow.setVisibility(View.VISIBLE);
		}
		else if (delegate != null && (stickerSet == null || !stickerSet.set.masks)) {
			previewSendButton.setText(getContext().getString(R.string.SendSticker));
			stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
			stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
			previewSendButton.setVisibility(View.VISIBLE);
			previewSendButtonShadow.setVisibility(View.VISIBLE);
		}
		else {
			previewSendButton.setText(getContext().getString(R.string.Close));
			stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER));
			stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER));
			previewSendButton.setVisibility(View.GONE);
			previewSendButtonShadow.setVisibility(View.GONE);
		}
	}

	private void removeSticker(SendMessagesHelper.ImportingSticker sticker) {
		int idx = importingStickersPaths.indexOf(sticker);
		if (idx >= 0) {
			importingStickersPaths.remove(idx);
			adapter.notifyItemRemoved(idx);
			if (importingStickersPaths.isEmpty()) {
				dismiss();
				return;
			}
			updateFields();
		}
	}

	public void setInstallDelegate(StickersAlertInstallDelegate stickersAlertInstallDelegate) {
		installDelegate = stickersAlertInstallDelegate;
	}

	public void setCustomButtonDelegate(StickersAlertCustomButtonDelegate customButtonDelegate) {
		this.customButtonDelegate = customButtonDelegate;
		updateFields();
	}

	private void onSubItemClick(int id) {
		if (stickerSet == null) {
			return;
		}
		String stickersUrl;
		if (stickerSet.set != null && stickerSet.set.emojis) {
			stickersUrl = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addemoji/" + stickerSet.set.shortName;
		}
		else {
			stickersUrl = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/" + stickerSet.set.shortName;
		}
		if (id == 1) {
			Context context = parentActivity;
			if (context == null && parentFragment != null) {
				context = parentFragment.getParentActivity();
			}
			if (context == null) {
				context = getContext();
			}
			ShareAlert alert = new ShareAlert(context, null, stickersUrl, false, stickersUrl, false) {
				@Override
				public void dismissInternal() {
					super.dismissInternal();
					if (parentFragment instanceof ChatActivity) {
						AndroidUtilities.requestAdjustResize(parentFragment.getParentActivity(), parentFragment.getClassGuid());
						if (((ChatActivity)parentFragment).chatActivityEnterView.getVisibility() == View.VISIBLE) {
							parentFragment.getFragmentView().requestLayout();
						}
					}
				}

				@Override
				protected void onSend(@NonNull LongSparseArray<TLRPC.Dialog> dids, int count) {
					AndroidUtilities.runOnUIThread(() -> {
						UndoView undoView;
						if (parentFragment instanceof ChatActivity) {
							undoView = ((ChatActivity)parentFragment).getUndoView();
						}
						else if (parentFragment instanceof ProfileActivity) {
							undoView = ((ProfileActivity)parentFragment).getUndoView();
						}
						else {
							undoView = null;
						}
						if (undoView != null) {
							if (dids.size() == 1) {
								undoView.showWithAction(dids.valueAt(0).id, UndoView.ACTION_FWD_MESSAGES, count);
							}
							else {
								undoView.showWithAction(0, UndoView.ACTION_FWD_MESSAGES, count, dids.size(), null, null);
							}
						}
					}, 100);
				}
			};
			if (parentFragment != null) {
				parentFragment.showDialog(alert);
				if (parentFragment instanceof ChatActivity) {
					alert.setCalcMandatoryInsets(((ChatActivity)parentFragment).isKeyboardVisible());
				}
			}
			else {
				alert.show();
			}
		}
		else if (id == 2) {
			try {
				AndroidUtilities.addToClipboard(stickersUrl);
				BulletinFactory.of((FrameLayout)containerView).createCopyLinkBulletin().show();
			}
			catch (Exception e) {
				FileLog.e(e);
			}
		}
	}

	private void updateFields() {
		if (titleTextView == null) {
			return;
		}
		if (stickerSet != null) {
			SpannableStringBuilder stringBuilder = null;
			try {
				if (urlPattern == null) {
					urlPattern = Pattern.compile("@[a-zA-Z\\d_]{1,32}");
				}
				Matcher matcher = urlPattern.matcher(stickerSet.set.title);
				while (matcher.find()) {
					if (stringBuilder == null) {
						stringBuilder = new SpannableStringBuilder(stickerSet.set.title);
						titleTextView.setMovementMethod(new LinkMovementMethodMy());
					}
					int start = matcher.start();
					int end = matcher.end();
					if (stickerSet.set.title.charAt(start) != '@') {
						start++;
					}
					URLSpanNoUnderline url = new URLSpanNoUnderline(stickerSet.set.title.subSequence(start + 1, end).toString()) {
						@Override
						public void onClick(View widget) {
							MessagesController.getInstance(currentAccount).openByUserName(getURL(), parentFragment, 1);
							dismiss();
						}
					};
					stringBuilder.setSpan(url, start, end, 0);
				}
			}
			catch (Exception e) {
				FileLog.e(e);
			}
			titleTextView.setText(stringBuilder != null ? stringBuilder : stickerSet.set.title);

			if (isEmoji()) {
				int width = gridView.getMeasuredWidth();
				if (width == 0) {
					width = AndroidUtilities.displaySize.x;
				}
				adapter.stickersPerRow = Math.max(1, width / AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 45));
			}
			else {
				adapter.stickersPerRow = 5;
			}
			layoutManager.setSpanCount(adapter.stickersPerRow);

			if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis && !UserConfig.getInstance(currentAccount).isPremium()) {
				boolean hasPremiumEmoji = false;
				if (stickerSet.documents != null) {
					for (int i = 0; i < stickerSet.documents.size(); ++i) {
						if (!MessageObject.isFreeEmoji(stickerSet.documents.get(i))) {
							hasPremiumEmoji = true;
							break;
						}
					}
				}

				if (hasPremiumEmoji) {
					premiumButtonView.setVisibility(View.VISIBLE);
					pickerBottomLayout.setBackground(null);

					setButton(null, null, 0);

					premiumButtonView.setButton(getContext().getString(R.string.UnlockPremiumEmoji), e -> {
						if (parentFragment != null) {
							new PremiumFeatureBottomSheet(parentFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show();
						}
						else if (getContext() instanceof LaunchActivity) {
							((LaunchActivity)getContext()).presentFragment(new PremiumPreviewFragment(null));
						}
					});

					return;
				}
			}
			else {
				premiumButtonView.setVisibility(View.INVISIBLE);
			}

			boolean notInstalled;
			if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis) {
				List<TLRPC.TLMessagesStickerSet> sets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJIPACKS);
				boolean has = false;
				for (int i = 0; i < sets.size(); ++i) {
					if (sets.get(i) != null && sets.get(i).set != null && sets.get(i).set.id == stickerSet.set.id) {
						has = true;
						break;
					}
				}
				notInstalled = !has;
			}
			else {
				notInstalled = stickerSet == null || stickerSet.set == null || !MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id);
			}

			if (customButtonDelegate != null) {
				setButton(v -> {
					if (customButtonDelegate.onCustomButtonPressed()) {
						dismiss();
					}
				}, customButtonDelegate.getCustomButtonText(), customButtonDelegate.getCustomButtonTextColor(), customButtonDelegate.getCustomButtonColor(), customButtonDelegate.getCustomButtonRippleColor());
			}
			else if (notInstalled) {
				String text;
				if (stickerSet != null && stickerSet.set != null && stickerSet.set.masks) {
					text = LocaleController.formatString("AddStickersCount", R.string.AddStickersCount, LocaleController.formatPluralString("MasksCount", stickerSet.documents.size()));
				}
				else if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis) {
					text = LocaleController.formatString("AddStickersCount", R.string.AddStickersCount, LocaleController.formatPluralString("EmojiCountButton", stickerSet.documents.size()));
				}
				else {
					text = LocaleController.formatString("AddStickersCount", R.string.AddStickersCount, LocaleController.formatPluralString("Stickers", stickerSet.documents == null ? 0 : stickerSet.documents.size()));
				}
				setButton(v -> {
					dismiss();
					if (installDelegate != null) {
						installDelegate.onStickerSetInstalled();
					}
					if (inputStickerSet == null || MediaDataController.getInstance(currentAccount).cancelRemovingStickerSet(TLRPCExtensions.getId(inputStickerSet))) {
						return;
					}
					TLRPC.TLMessagesInstallStickerSet req = new TLRPC.TLMessagesInstallStickerSet();
					req.stickerset = inputStickerSet;
					ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
						int type = MediaDataController.TYPE_IMAGE;
						if (stickerSet.set.masks) {
							type = MediaDataController.TYPE_MASK;
						}
						else if (stickerSet.set.emojis) {
							type = MediaDataController.TYPE_EMOJIPACKS;
						}

						try {
							if (error == null) {
								if (showTooltipWhenToggle) {
									Bulletin.make(parentFragment, new StickerSetBulletinLayout(pickerBottomFrameLayout.getContext(), stickerSet, StickerSetBulletinLayout.TYPE_ADDED, null), Bulletin.DURATION_SHORT).show();
								}
								if (response instanceof TLRPC.TLMessagesStickerSetInstallResultArchive) {
									MediaDataController.getInstance(currentAccount).processStickerSetInstallResultArchive(parentFragment, true, type, (TLRPC.TLMessagesStickerSetInstallResultArchive)response);
								}
							}
							else {
								Toast.makeText(getContext(), getContext().getString(R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
							}
						}
						catch (Exception e) {
							FileLog.e(e);
						}
						MediaDataController.getInstance(currentAccount).loadStickers(type, false, true, true, true);
					}));
				}, text, getContext().getColor(R.color.white), getContext().getColor(R.color.brand), getContext().getColor(R.color.darker_brand));
			}
			else {
				String text;
				if (stickerSet.set.masks) {
					text = LocaleController.formatString("RemoveStickersCount", R.string.RemoveStickersCount, LocaleController.formatPluralString("MasksCount", stickerSet.documents.size()));
				}
				else if (stickerSet.set.emojis) {
					text = LocaleController.formatString("RemoveStickersCount", R.string.RemoveStickersCount, LocaleController.formatPluralString("EmojiCountButton", stickerSet.documents.size()));
				}
				else {
					text = LocaleController.formatString("RemoveStickersCount", R.string.RemoveStickersCount, LocaleController.formatPluralString("Stickers", stickerSet.documents.size()));
				}
				if (stickerSet.set.official) {
					setButton(v -> {
						if (installDelegate != null) {
							installDelegate.onStickerSetUninstalled();
						}
						dismiss();
						MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 1, parentFragment, true, showTooltipWhenToggle);
					}, text, getContext().getColor(R.color.purple));
				}
				else {
					setButton(v -> {
						if (installDelegate != null) {
							installDelegate.onStickerSetUninstalled();
						}
						dismiss();
						MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 0, parentFragment, true, showTooltipWhenToggle);
					}, text, getContext().getColor(R.color.purple));
				}
			}
			adapter.notifyDataSetChanged();
		}
		else if (importingStickers != null) {
			titleTextView.setText(LocaleController.formatPluralString("Stickers", importingStickersPaths != null ? importingStickersPaths.size() : importingStickers.size()));
			if (uploadImportStickers == null || uploadImportStickers.isEmpty()) {
				setButton(v -> showNameEnterAlert(), LocaleController.formatString("ImportStickers", R.string.ImportStickers, LocaleController.formatPluralString("Stickers", importingStickersPaths != null ? importingStickersPaths.size() : importingStickers.size())), getContext().getColor(R.color.brand));
				pickerBottomLayout.setEnabled(true);
			}
			else {
				setButton(null, getContext().getString(R.string.ImportStickersProcessing), getContext().getColor(R.color.dark_gray));
				pickerBottomLayout.setEnabled(false);
			}
		}
		else {
			String text = getContext().getString(R.string.Close);
			setButton((v) -> dismiss(), text, getContext().getColor(R.color.brand));
		}
	}

	private void showNameEnterAlert() {
		Context context = getContext();

		int[] state = new int[]{0};
		FrameLayout fieldLayout = new FrameLayout(context);

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(context.getString(R.string.ImportStickersEnterName));
		builder.setPositiveButton(context.getString(R.string.Next), (dialog, which) -> {

		});

		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		builder.setView(linearLayout);

		linearLayout.addView(fieldLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 24, 6, 24, 0));

		TextView message = new TextView(context);

		TextView textView = new TextView(context);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		textView.setTextColor(context.getColor(R.color.hint));
		textView.setMaxLines(1);
		textView.setLines(1);
		textView.setText(String.format(Locale.getDefault(), "%s/addstickers/", ApplicationLoader.applicationContext.getString(R.string.domain)));
		textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		textView.setGravity(Gravity.LEFT | Gravity.TOP);
		textView.setSingleLine(true);
		textView.setVisibility(View.INVISIBLE);
		textView.setImeOptions(EditorInfo.IME_ACTION_DONE);
		textView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
		fieldLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.LEFT));

		EditTextBoldCursor editText = new EditTextBoldCursor(context);
		editText.setBackground(null);
		editText.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_dialogTextRed2));
		editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		editText.setTextColor(context.getColor(R.color.text));
		editText.setMaxLines(1);
		editText.setLines(1);
		editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		editText.setGravity(Gravity.LEFT | Gravity.TOP);
		editText.setSingleLine(true);
		editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
		editText.setCursorColor(context.getColor(R.color.text));
		editText.setCursorSize(AndroidUtilities.dp(20));
		editText.setCursorWidth(1.5f);
		editText.setPadding(0, AndroidUtilities.dp(4), 0, 0);
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (state[0] != 2) {
					return;
				}
				checkUrlAvailable(message, editText.getText().toString(), false);
			}

			@Override
			public void afterTextChanged(Editable s) {

			}
		});
		fieldLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT));
		editText.setOnEditorActionListener((view, i, keyEvent) -> {
			if (i == EditorInfo.IME_ACTION_NEXT) {
				builder.create().getButton(AlertDialog.BUTTON_POSITIVE).callOnClick();
				return true;
			}
			return false;
		});
		editText.setSelection(editText.length());

		builder.setNegativeButton(context.getString(R.string.Cancel), (dialog, which) -> AndroidUtilities.hideKeyboard(editText));

		message.setText(AndroidUtilities.replaceTags(context.getString(R.string.ImportStickersEnterNameInfo)));
		message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		message.setPadding(AndroidUtilities.dp(23), AndroidUtilities.dp(12), AndroidUtilities.dp(23), AndroidUtilities.dp(6));
		message.setTextColor(context.getColor(R.color.dark_gray));
		linearLayout.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

		final AlertDialog alertDialog = builder.create();
		alertDialog.setOnShowListener(dialog -> AndroidUtilities.runOnUIThread(() -> {
			editText.requestFocus();
			AndroidUtilities.showKeyboard(editText);
		}));
		alertDialog.show();
		editText.requestFocus();
		alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
			if (state[0] == 1) {
				return;
			}
			if (state[0] == 0) {
				state[0] = 1;
				TLRPC.TLStickersSuggestShortName req = new TLRPC.TLStickersSuggestShortName();
				req.title = setTitle = editText.getText().toString();
				ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
					boolean set = false;
					if (response instanceof TLStickersSuggestedShortName res) {
						if (res.shortName != null) {
							editText.setText(res.shortName);
							editText.setSelection(0, editText.length());
							checkUrlAvailable(message, editText.getText().toString(), true);
							set = true;
						}
					}
					textView.setVisibility(View.VISIBLE);
					editText.setPadding(textView.getMeasuredWidth(), AndroidUtilities.dp(4), 0, 0);
					if (!set) {
						editText.setText("");
					}
					state[0] = 2;
				}));
			}
			else if (state[0] == 2) {
				state[0] = 3;
				if (!lastNameAvailable) {
					AndroidUtilities.shakeView(editText, 2, 0);
					editText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
				}
				AndroidUtilities.hideKeyboard(editText);
				SendMessagesHelper.getInstance(currentAccount).prepareImportStickers(setTitle, lastCheckName, importingSoftware, importingStickersPaths, (param) -> {
					ImportingAlert importingAlert = new ImportingAlert(getContext(), lastCheckName, null);
					importingAlert.show();
				});
				builder.getDismissRunnable().run();
				dismiss();
			}
		});
	}

	private void checkUrlAvailable(TextView message, String text, boolean forceAvailable) {
		Context context = message.getContext();

		if (forceAvailable) {
			message.setText(context.getString(R.string.ImportStickersLinkAvailable));
			message.setTextColor(context.getColor(R.color.online));
			lastNameAvailable = true;
			lastCheckName = text;
			return;
		}
		if (checkRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(checkRunnable);
			checkRunnable = null;
			lastCheckName = null;
			if (checkReqId != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(checkReqId, true);
			}
		}
		if (TextUtils.isEmpty(text)) {
			message.setText(context.getString(R.string.ImportStickersEnterUrlInfo));
			message.setTextColor(context.getColor(R.color.dark_gray));
			return;
		}
		lastNameAvailable = false;
		if (text.startsWith("_") || text.endsWith("_")) {
			message.setText(context.getString(R.string.ImportStickersLinkInvalid));
			message.setTextColor(context.getColor(R.color.purple));
			return;
		}
		for (int a = 0, N = text.length(); a < N; a++) {
			char ch = text.charAt(a);
			if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
				message.setText(context.getString(R.string.ImportStickersEnterUrlInfo));
				message.setTextColor(context.getColor(R.color.purple));
				return;
			}
		}
		if (text.length() < 5) {
			message.setText(context.getString(R.string.ImportStickersLinkInvalidShort));
			message.setTextColor(context.getColor(R.color.purple));
			return;
		}
		if (text.length() > 32) {
			message.setText(context.getString(R.string.ImportStickersLinkInvalidLong));
			message.setTextColor(context.getColor(R.color.purple));
			return;
		}

		message.setText(context.getString(R.string.ImportStickersLinkChecking));
		message.setTextColor(context.getColor(R.color.dark_gray));
		lastCheckName = text;
		checkRunnable = () -> {
			TLRPC.TLStickersCheckShortName req = new TLRPC.TLStickersCheckShortName();
			req.shortName = text;
			checkReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
				checkReqId = 0;
				if (lastCheckName != null && lastCheckName.equals(text)) {
					if (error == null && response instanceof TLRPC.TLBoolTrue) {
						message.setText(context.getString(R.string.ImportStickersLinkAvailable));
						message.setTextColor(context.getColor(R.color.online));
						lastNameAvailable = true;
					}
					else {
						message.setText(context.getString(R.string.ImportStickersLinkTaken));
						message.setTextColor(context.getColor(R.color.purple));
						lastNameAvailable = false;
					}
				}
			}), ConnectionsManager.RequestFlagFailOnServerErrors);
		};

		AndroidUtilities.runOnUIThread(checkRunnable, 300);
	}

	@Override
	protected boolean canDismissWithSwipe() {
		return false;
	}

	@SuppressLint("NewApi")
	private void updateLayout() {
		if (gridView.getChildCount() <= 0) {
			setScrollOffsetY(gridView.getPaddingTop());
			return;
		}
		View child = gridView.getChildAt(0);
		RecyclerListView.Holder holder = (RecyclerListView.Holder)gridView.findContainingViewHolder(child);
		int top = child.getTop();
		int newOffset = 0;
		if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
			newOffset = top;
			runShadowAnimation(0, false);
		}
		else {
			runShadowAnimation(0, true);
		}

//        if (layoutManager.findLastCompletelyVisibleItemPosition() == adapter.getItemCount() - 1) {
//            runShadowAnimation(1, false);
//        } else {
		runShadowAnimation(1, true);
//        }

		if (scrollOffsetY != newOffset) {
			setScrollOffsetY(newOffset);
		}
	}

	private void setScrollOffsetY(int newOffset) {
		scrollOffsetY = newOffset;
		gridView.setTopGlowOffset(newOffset);
		if (stickerSetCovereds == null) {
			titleTextView.setTranslationY(newOffset);
			if (descriptionTextView != null) {
				descriptionTextView.setTranslationY(newOffset);
			}
			if (importingStickers == null) {
				optionsButton.setTranslationY(newOffset);
			}
			shadow[0].setTranslationY(newOffset);
		}
		containerView.invalidate();
	}

	private void hidePreview() {
		AnimatorSet animatorSet = new AnimatorSet();
		animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, View.ALPHA, 0.0f));
		animatorSet.setDuration(200);
		animatorSet.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				stickerPreviewLayout.setVisibility(View.GONE);
				stickerImageView.setImageDrawable(null);
			}
		});
		animatorSet.start();
	}

	private void runShadowAnimation(final int num, final boolean show) {
		if (stickerSetCovereds != null) {
			return;
		}
		if (show && shadow[num].getTag() != null || !show && shadow[num].getTag() == null) {
			shadow[num].setTag(show ? null : 1);
			if (show) {
				shadow[num].setVisibility(View.VISIBLE);
			}
			if (shadowAnimation[num] != null) {
				shadowAnimation[num].cancel();
			}
			shadowAnimation[num] = new AnimatorSet();
			shadowAnimation[num].playTogether(ObjectAnimator.ofFloat(shadow[num], View.ALPHA, show ? 1.0f : 0.0f));
			shadowAnimation[num].setDuration(150);
			shadowAnimation[num].addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
						if (!show) {
							shadow[num].setVisibility(View.INVISIBLE);
						}
						shadowAnimation[num] = null;
					}
				}

				@Override
				public void onAnimationCancel(Animator animation) {
					if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
						shadowAnimation[num] = null;
					}
				}
			});
			shadowAnimation[num].start();
		}
	}

	@Override
	public void show() {
		super.show();
		NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 4);
	}

	public void setOnDismissListener(Runnable onDismissListener) {
		this.onDismissListener = onDismissListener;
	}

	@Override
	public void dismiss() {
		super.dismiss();
		if (onDismissListener != null) {
			onDismissListener.run();
		}
		if (reqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
			reqId = 0;
		}
		NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
		if (importingStickers != null) {
			if (importingStickersPaths != null) {
				for (int a = 0, N = importingStickersPaths.size(); a < N; a++) {
					SendMessagesHelper.ImportingSticker sticker = importingStickersPaths.get(a);
					if (!sticker.validated) {
						FileLoader.getInstance(currentAccount).cancelFileUpload(sticker.path, false);
					}
					if (sticker.animated) {
						new File(sticker.path).delete();
					}
				}
			}
			NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
			NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
		}
		NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Bulletin.addDelegate((FrameLayout)containerView, new Bulletin.Delegate() {
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
				return pickerBottomFrameLayout != null ? pickerBottomFrameLayout.getHeight() : 0;
			}
		});
	}

	@Override
	protected void onStop() {
		super.onStop();
		Bulletin.removeDelegate((FrameLayout)containerView);
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.emojiLoaded) {
			if (gridView != null) {
				int count = gridView.getChildCount();
				for (int a = 0; a < count; a++) {
					gridView.getChildAt(a).invalidate();
				}
			}
		}
		else if (id == NotificationCenter.fileUploaded) {
			if (uploadImportStickers == null) {
				return;
			}
			String location = (String)args[0];
			SendMessagesHelper.ImportingSticker sticker = uploadImportStickers.get(location);
			if (sticker != null) {
				sticker.uploadMedia(currentAccount, (TLRPC.InputFile)args[1], () -> {
					if (isDismissed()) {
						return;
					}
					uploadImportStickers.remove(location);
					if (!"application/x-tgsticker".equals(sticker.mimeType)) {
						removeSticker(sticker);
					}
					else {
						sticker.validated = true;
						int idx = importingStickersPaths.indexOf(sticker);
						if (idx >= 0) {
							RecyclerView.ViewHolder holder = gridView.findViewHolderForAdapterPosition(idx);
							if (holder != null) {
								((StickerEmojiCell)holder.itemView).setSticker(sticker);
							}
						}
						else {
							adapter.notifyDataSetChanged();
						}
					}
					if (uploadImportStickers.isEmpty()) {
						updateFields();
					}
				});
			}
		}
		else if (id == NotificationCenter.fileUploadFailed) {
			if (uploadImportStickers == null) {
				return;
			}
			String location = (String)args[0];
			SendMessagesHelper.ImportingSticker sticker = uploadImportStickers.remove(location);
			if (sticker != null) {
				removeSticker(sticker);
			}
			if (uploadImportStickers.isEmpty()) {
				updateFields();
			}
		}
	}

	private void setButton(View.OnClickListener onClickListener, String title, int color) {
		setButton(onClickListener, title, color, 0, 0);
	}

	private void setButton(View.OnClickListener onClickListener, String title, int color, int backgroundColor, int backgroundSelectorColor) {
		if (color != 0) {
			pickerBottomLayout.setTextColor(color);
		}
		pickerBottomLayout.setText(title);
		pickerBottomLayout.setOnClickListener(onClickListener);

		ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)pickerBottomLayout.getLayoutParams();
		ViewGroup.MarginLayoutParams shadowParams = (ViewGroup.MarginLayoutParams)shadow[1].getLayoutParams();
		ViewGroup.MarginLayoutParams gridParams = (ViewGroup.MarginLayoutParams)gridView.getLayoutParams();
		ViewGroup.MarginLayoutParams emptyParams = (ViewGroup.MarginLayoutParams)emptyView.getLayoutParams();
		if (backgroundColor != 0 && backgroundSelectorColor != 0) {
			pickerBottomLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), backgroundColor, backgroundSelectorColor));
			pickerBottomFrameLayout.setBackgroundColor(getContext().getColor(R.color.background));
			params.leftMargin = params.topMargin = params.rightMargin = params.bottomMargin = AndroidUtilities.dp(8);
			emptyParams.bottomMargin = gridParams.bottomMargin = shadowParams.bottomMargin = AndroidUtilities.dp(64);
		}
		else {
			pickerBottomLayout.setBackground(Theme.createSelectorWithBackgroundDrawable(getContext().getColor(R.color.background), getContext().getColor(R.color.light_background)));
			pickerBottomFrameLayout.setBackgroundColor(Color.TRANSPARENT);
			params.leftMargin = params.topMargin = params.rightMargin = params.bottomMargin = 0;
			emptyParams.bottomMargin = gridParams.bottomMargin = shadowParams.bottomMargin = AndroidUtilities.dp(48);
		}
		containerView.requestLayout();
	}

	public boolean isShowTooltipWhenToggle() {
		return showTooltipWhenToggle;
	}

	public void setShowTooltipWhenToggle(boolean showTooltipWhenToggle) {
		this.showTooltipWhenToggle = showTooltipWhenToggle;
	}

	public void updateColors() {
		updateColors(false);
	}

	public void updateColors(boolean applyDescriptions) {
		adapter.updateColors();

		titleTextView.setHighlightColor(getContext().getColor(R.color.brand));
		stickerPreviewLayout.setBackgroundColor(getContext().getColor(R.color.background) & 0xdfffffff);

		optionsButton.setIconColor(getContext().getColor(R.color.text));
		optionsButton.setPopupItemsColor(getContext().getColor(R.color.text), false);
		optionsButton.setPopupItemsColor(getContext().getColor(R.color.text), true);
		optionsButton.setPopupItemsSelectorColor(getContext().getColor(R.color.light_background));
		optionsButton.redrawPopup(getContext().getColor(R.color.background));

		if (applyDescriptions) {
			if (Theme.isAnimatingColor() && animatingDescriptions == null) {
				animatingDescriptions = getThemeDescriptions();
				for (int i = 0, N = animatingDescriptions.size(); i < N; i++) {
					animatingDescriptions.get(i).setDelegateDisabled();
				}
			}
			// for (int i = 0, N = animatingDescriptions.size(); i < N; i++) {
			// final ThemeDescription description = animatingDescriptions.get(i);
			// description.setColor(getThemedColor(description.getCurrentKey()), false, false);
			// }
		}

		if (!Theme.isAnimatingColor() && animatingDescriptions != null) {
			animatingDescriptions = null;
		}
	}

	@Override
	public void onBackPressed() {
		if (ContentPreviewViewer.getInstance().isVisible()) {
			ContentPreviewViewer.getInstance().closeWithMenu();
			return;
		}
		super.onBackPressed();
	}

	public interface StickersAlertDelegate {
		void onStickerSelected(TLRPC.Document sticker, String query, Object parent, SendAnimationData sendAnimationData, boolean clearsInputField, boolean notify, int scheduleDate);

		boolean canSchedule();

		boolean isInScheduleMode();
	}

	public interface StickersAlertInstallDelegate {
		void onStickerSetInstalled();

		void onStickerSetUninstalled();
	}

	public interface StickersAlertCustomButtonDelegate {
		int getCustomButtonTextColor();

		int getCustomButtonRippleColor();

		int getCustomButtonColor();

		String getCustomButtonText();

		boolean onCustomButtonPressed();
	}

	private static class LinkMovementMethodMy extends LinkMovementMethod {
		@Override
		public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
			try {
				boolean result = super.onTouchEvent(widget, buffer, event);
				if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
					Selection.removeSelection(buffer);
				}
				return result;
			}
			catch (Exception e) {
				FileLog.e(e);
			}
			return false;
		}
	}

	private class GridAdapter extends RecyclerListView.SelectionAdapter {

		private final Context context;
		private int stickersPerRow;
		private final SparseArray<Object> cache = new SparseArray<>();
		private final SparseArray<TLRPC.StickerSetCovered> positionsToSets = new SparseArray<>();
		private int totalItems;
		private int stickersRowCount;

		public GridAdapter(Context context) {
			this.context = context;
		}

		@Override
		public int getItemCount() {
			return totalItems;
		}

		@Override
		public int getItemViewType(int position) {
			if (stickerSetCovereds != null) {
				Object object = cache.get(position);
				if (object != null) {
					if (object instanceof TLRPC.Document) {
						return 0;
					}
					else {
						return 2;
					}
				}
				return 1;
			}
			return 0;
		}

		@Override
		public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
			return false;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = null;
			switch (viewType) {
				case 0:
					StickerEmojiCell cell = new StickerEmojiCell(context, false) {
						public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
							super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize/*AndroidUtilities.dp(82)*/, MeasureSpec.EXACTLY));
						}
					};
					cell.getImageView().setLayerNum(7);
					view = cell;
					break;
				case 1:
					view = new EmptyCell(context);
					break;
				case 2:
					view = new FeaturedStickerSetInfoCell(context, 8, true, false);
					break;
			}

			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			if (stickerSetCovereds != null) {
				switch (holder.getItemViewType()) {
					case 0:
						TLRPC.Document sticker = (TLRPC.Document)cache.get(position);
						((StickerEmojiCell)holder.itemView).setSticker(sticker, positionsToSets.get(position), false);
						break;
					case 1:
						((EmptyCell)holder.itemView).setHeight(AndroidUtilities.dp(82));
						break;
					case 2:
						TLRPC.StickerSetCovered stickerSetCovered = stickerSetCovereds.get((Integer)cache.get(position));
						FeaturedStickerSetInfoCell cell = (FeaturedStickerSetInfoCell)holder.itemView;
						cell.setStickerSet(stickerSetCovered, false);
						break;
				}
			}
			else if (importingStickers != null) {
				((StickerEmojiCell)holder.itemView).setSticker(importingStickersPaths.get(position));
			}
			else {
				((StickerEmojiCell)holder.itemView).setSticker(stickerSet.documents.get(position), stickerSet, showEmoji);
			}
		}

		@Override
		public void notifyDataSetChanged() {
			if (stickerSetCovereds != null) {
				int width = gridView.getMeasuredWidth();
				if (width == 0) {
					width = AndroidUtilities.displaySize.x;
				}
				stickersPerRow = width / AndroidUtilities.dp(72);
				layoutManager.setSpanCount(stickersPerRow);
				cache.clear();
				positionsToSets.clear();
				totalItems = 0;
				stickersRowCount = 0;
				for (int a = 0; a < stickerSetCovereds.size(); a++) {
					final TLRPC.StickerSetCovered pack = stickerSetCovereds.get(a);
					final var covers = TLRPCExtensions.getCovers(pack);
					final var cover = TLRPCExtensions.getCover(pack);

					if ((covers == null || covers.isEmpty()) && cover == null) {
						continue;
					}
					stickersRowCount += Math.ceil(stickerSetCovereds.size() / (float)stickersPerRow);
					positionsToSets.put(totalItems, pack);
					cache.put(totalItems++, a);
					int count;
					if (covers != null && !covers.isEmpty()) {
						count = (int)Math.ceil(covers.size() / (float)stickersPerRow);
						for (int b = 0; b < covers.size(); b++) {
							cache.put(b + totalItems, covers.get(b));
						}
					}
					else {
						count = 1;
						cache.put(totalItems, cover);
					}
					for (int b = 0; b < count * stickersPerRow; b++) {
						positionsToSets.put(totalItems + b, pack);
					}
					totalItems += count * stickersPerRow;
				}
			}
			else if (importingStickersPaths != null) {
				totalItems = importingStickersPaths.size();
			}
			else {
				totalItems = stickerSet != null ? stickerSet.documents.size() : 0;
			}
			super.notifyDataSetChanged();
		}

		@Override
		public void notifyItemRemoved(int position) {
			if (importingStickersPaths != null) {
				totalItems = importingStickersPaths.size();
			}
			super.notifyItemRemoved(position);
		}

		public void updateColors() {
			if (stickerSetCovereds != null) {
				for (int i = 0, size = gridView.getChildCount(); i < size; i++) {
					final View child = gridView.getChildAt(i);
					if (child instanceof FeaturedStickerSetInfoCell) {
						((FeaturedStickerSetInfoCell)child).updateColors();
					}
				}
			}
		}

		public void getThemeDescriptions(List<ThemeDescription> descriptions, ThemeDescription.ThemeDescriptionDelegate delegate) {
			if (stickerSetCovereds != null) {
				FeaturedStickerSetInfoCell.createThemeDescriptions(descriptions, gridView, delegate);
			}
		}
	}
}
