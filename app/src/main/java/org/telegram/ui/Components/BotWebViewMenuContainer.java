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
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.User;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.ChatListItemAnimator;

public class BotWebViewMenuContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
	private final static int POLL_PERIOD = 60000;

	private final static SimpleFloatPropertyCompat<BotWebViewMenuContainer> ACTION_BAR_TRANSITION_PROGRESS_VALUE = new SimpleFloatPropertyCompat<BotWebViewMenuContainer>("actionBarTransitionProgress", obj -> obj.actionBarTransitionProgress, (obj, value) -> {
		obj.actionBarTransitionProgress = value;
		obj.invalidate();
		obj.invalidateActionBar();
	}).setMultiplier(100f);

	private float actionBarTransitionProgress;
	private SpringAnimation springAnimation;
	private final ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer;
	private final ChatAttachAlertBotWebViewLayout.WebProgressView progressView;
	private boolean ignoreLayout;
	private BotWebViewContainer webViewContainer;
	private final BotWebViewContainer.Delegate webViewDelegate;
	private ValueAnimator webViewScrollAnimator;
	private boolean ignoreMeasure;
	private final Paint dimPaint = new Paint();
	private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint actionBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint linePaint = new Paint();
	private final ChatActivityEnterView parentEnterView;
	private boolean botWebViewButtonWasVisible;
	private SpringAnimation botWebViewButtonAnimator;
	private int currentAccount;
	private long botId;
	private String botUrl;
	private boolean isLoaded;
	private boolean dismissed;
	private Boolean wasLightStatusBar;
	private long queryId;
	private final ActionBarMenuItem botMenuItem;
	private final ActionBar.ActionBarMenuOnItemClick actionBarOnItemClick;
	private ActionBarMenuSubItem settingsItem;
	private Editable savedEditText;
	private MessageObject savedReplyMessageObject;
	private MessageObject savedEditMessageObject;
	private Runnable globalOnDismissListener;
	private float overrideActionBarBackgroundProgress;
	private int overrideActionBarBackground;
	private boolean overrideBackgroundColor;
	private boolean needCloseConfirmation;

	private final Runnable pollRunnable = () -> {
		if (!dismissed) {
			var prolongWebView = new TLRPC.TLMessagesProlongWebView();
			prolongWebView.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
			prolongWebView.peer = MessagesController.getInstance(currentAccount).getInputPeer(botId);
			prolongWebView.queryId = queryId;

			ConnectionsManager.getInstance(currentAccount).sendRequest(prolongWebView, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
				if (dismissed) {
					return;
				}
				if (error != null) {
					dismiss();
				}
				else {
					AndroidUtilities.runOnUIThread(this.pollRunnable, POLL_PERIOD);
				}
			}));
		}
	};

	public BotWebViewMenuContainer(@NonNull Context context, ChatActivityEnterView parentEnterView) {
		super(context);

		this.parentEnterView = parentEnterView;
		ChatActivity chatActivity = parentEnterView.getParentFragment();
		ActionBar actionBar = chatActivity.getActionBar();
		ActionBarMenu menu = actionBar.createMenu();
		botMenuItem = menu.addItem(1000, R.drawable.overflow_menu);
		botMenuItem.setVisibility(GONE);

		botMenuItem.addSubItem(R.id.menu_reload_page, R.drawable.msg_retry, LocaleController.getString(R.string.BotWebViewReloadPage));
		actionBarOnItemClick = actionBar.getActionBarMenuOnItemClick();

		webViewContainer = new BotWebViewContainer(context, getColor(Theme.key_windowBackgroundWhite));
		webViewContainer.setDelegate(webViewDelegate = new BotWebViewContainer.Delegate() {

			@Override
			public void onCloseRequested(Runnable callback) {
				dismiss(callback);
			}

			@Override
			public void onWebAppSetupClosingBehavior(boolean needConfirmation) {
				BotWebViewMenuContainer.this.needCloseConfirmation = needConfirmation;
			}

			@Override
			public void onWebAppSetActionBarColor(int color) {
				int from = overrideActionBarBackground;
				int to = color;

				if (from == 0) {
					overrideActionBarBackground = to;
				}

				ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
				animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
				animator.addUpdateListener(animation -> {
					if (from != 0) {
						overrideActionBarBackground = ColorUtils.blendARGB(from, to, (float)animation.getAnimatedValue());
					}
					else {
						overrideActionBarBackgroundProgress = (float)animation.getAnimatedValue();
					}
					actionBarPaint.setColor(overrideActionBarBackground);
					invalidateActionBar();
				});
				animator.start();
			}

			@Override
			public void onWebAppSetBackgroundColor(int color) {
				overrideBackgroundColor = true;

				int from = backgroundPaint.getColor();
				ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
				animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
				animator.addUpdateListener(animation -> {
					backgroundPaint.setColor(ColorUtils.blendARGB(from, color, (Float)animation.getAnimatedValue()));
					BotWebViewMenuContainer.this.invalidate();
				});
				animator.start();
			}

			@Override
			public void onWebAppExpand() {
				if (/* System.currentTimeMillis() - lastSwipeTime <= 1000 || */ swipeContainer.isSwipeInProgress()) {
					return;
				}
				swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
			}

			@Override
			public void onSetupMainButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible) {
				ChatActivityBotWebViewButton botWebViewButton = parentEnterView.getBotWebViewButton();
				botWebViewButton.setupButtonParams(isActive, text, color, textColor, isProgressVisible);
				botWebViewButton.setOnClickListener(v -> webViewContainer.onMainButtonPressed());
				if (isVisible != botWebViewButtonWasVisible) {
					animateBotButton(isVisible);
				}
			}

			@Override
			public void onSetBackButtonVisible(boolean visible) {
				if (actionBarTransitionProgress == 1f) {
					if (visible) {
						AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), actionBar.getBackButtonDrawable());
					}
					else {
						AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), R.drawable.ic_close_white);
					}
				}
			}
		});

		linePaint.setStyle(Paint.Style.FILL_AND_STROKE);
		linePaint.setStrokeWidth(AndroidUtilities.dp(4));
		linePaint.setStrokeCap(Paint.Cap.ROUND);

		dimPaint.setColor(0x40000000);

		swipeContainer = new ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer(context) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				int availableHeight = MeasureSpec.getSize(heightMeasureSpec);

				int padding;
				if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
					padding = (int)(availableHeight / 3.5f);
				}
				else {
					padding = (availableHeight / 5 * 2);
				}
				if (padding < 0) {
					padding = 0;
				}

				if (getOffsetY() != padding) {
					ignoreLayout = true;
					setOffsetY(padding);
					ignoreLayout = false;
				}

				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight + AndroidUtilities.dp(24) - AndroidUtilities.dp(5), MeasureSpec.EXACTLY));
			}

			@Override
			public void requestLayout() {
				if (ignoreLayout) {
					return;
				}
				super.requestLayout();
			}
		};
		swipeContainer.setScrollListener(() -> {
			if (swipeContainer.getSwipeOffsetY() > 0) {
				dimPaint.setAlpha((int)(0x40 * (1f - Math.min(swipeContainer.getSwipeOffsetY(), swipeContainer.getHeight()) / (float)swipeContainer.getHeight())));
			}
			else {
				dimPaint.setAlpha(0x40);
			}
			invalidate();
			webViewContainer.invalidateViewPortHeight();

			if (springAnimation != null) {
				float progress = (1f - Math.min(swipeContainer.getTopActionBarOffsetY(), swipeContainer.getTranslationY() - swipeContainer.getTopActionBarOffsetY()) / swipeContainer.getTopActionBarOffsetY());
				if (BotWebViewMenuContainer.this.getVisibility() != VISIBLE) {
					progress = 0;
				}
				float newPos = (progress > 0.5f ? 1 : 0) * 100f;
				if (springAnimation.getSpring().getFinalPosition() != newPos) {
					springAnimation.getSpring().setFinalPosition(newPos);
					springAnimation.start();

					if (!webViewContainer.isBackButtonVisible()) {
						if (newPos == 100f) {
							AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), R.drawable.ic_close_white);
						}
						else {
							AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), actionBar.getBackButtonDrawable());
						}
					}
				}
			}
		});
		swipeContainer.setScrollEndListener(() -> webViewContainer.invalidateViewPortHeight(true));
		swipeContainer.addView(webViewContainer);
		swipeContainer.setDelegate(() -> {
			if (!onCheckDismissByUser()) {
				swipeContainer.stickTo(0);
			}
		});
		swipeContainer.setTopActionBarOffsetY(ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight - AndroidUtilities.dp(24));
		swipeContainer.setSwipeOffsetAnimationDisallowed(true);
		swipeContainer.setIsKeyboardVisible(obj -> parentEnterView.getSizeNotifierLayout().getKeyboardHeight() >= AndroidUtilities.dp(20));
		addView(swipeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 24, 0, 0));

		addView(progressView = new ChatAttachAlertBotWebViewLayout.WebProgressView(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 5));
		webViewContainer.setWebViewProgressListener(progress -> {
			progressView.setLoadProgressAnimated(progress);
			if (progress == 1f) {
				ValueAnimator animator = ValueAnimator.ofFloat(1, 0).setDuration(200);
				animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
				animator.addUpdateListener(animation -> progressView.setAlpha((Float)animation.getAnimatedValue()));
				animator.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						progressView.setVisibility(GONE);
					}
				});
				animator.start();
			}
		});

		setWillNotDraw(false);
	}

	private void invalidateActionBar() {
		ChatActivity chatActivity = parentEnterView.getParentFragment();
		if (chatActivity == null || getVisibility() != VISIBLE) {
			return;
		}

		ChatAvatarContainer avatarContainer = chatActivity.getAvatarContainer();
		String subtitleDefaultColorKey = Theme.key_actionBarDefaultSubtitle;
		int subtitleColor = ColorUtils.blendARGB(getColor(subtitleDefaultColorKey), getColor(Theme.key_windowBackgroundWhiteGrayText), actionBarTransitionProgress);
		ActionBar actionBar = chatActivity.getActionBar();
		int backgroundColor = ColorUtils.blendARGB(getColor(Theme.key_actionBarDefault), getColor(Theme.key_windowBackgroundWhite), actionBarTransitionProgress);
		actionBar.setBackgroundColor(backgroundColor);
		actionBar.setItemsColor(ColorUtils.blendARGB(getColor(Theme.key_actionBarDefaultIcon), getColor(Theme.key_windowBackgroundWhiteBlackText), actionBarTransitionProgress), false);
		actionBar.setItemsBackgroundColor(ColorUtils.blendARGB(getColor(Theme.key_actionBarDefaultSelector), getColor(Theme.key_actionBarWhiteSelector), actionBarTransitionProgress), false);
		actionBar.setSubtitleColor(subtitleColor);

		ChatAvatarContainer chatAvatarContainer = chatActivity.getAvatarContainer();
		chatAvatarContainer.getTitleTextView().setTextColor(ColorUtils.blendARGB(getColor(Theme.key_actionBarDefaultTitle), getColor(Theme.key_windowBackgroundWhiteBlackText), actionBarTransitionProgress));
		chatAvatarContainer.getSubtitleTextView().setTextColor(subtitleColor);
		chatAvatarContainer.setOverrideSubtitleColor(actionBarTransitionProgress == 0 ? null : subtitleColor);

		updateLightStatusBar();
	}

	public boolean onBackPressed() {
		if (webViewContainer.onBackPressed()) {
			return true;
		}

		if (getVisibility() == VISIBLE) {
			onCheckDismissByUser();
			return true;
		}

		return false;
	}

	public boolean onCheckDismissByUser() {
		if (needCloseConfirmation) {
			String botName = null;
			User user = MessagesController.getInstance(currentAccount).getUser(botId);
			if (user != null) {
				botName = ContactsController.formatName(user.firstName, user.lastName);
			}
			AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle(botName).setMessage(LocaleController.getString(R.string.BotWebViewChangesMayNotBeSaved)).setPositiveButton(LocaleController.getString(R.string.BotWebViewCloseAnyway), (dialog2, which) -> dismiss()).setNegativeButton(LocaleController.getString(R.string.Cancel), null).create();
			dialog.show();
			TextView textView = (TextView)dialog.getButton(AlertDialog.BUTTON_POSITIVE);
			textView.setTextColor(getColor(Theme.key_dialogTextRed));
			return false;
		}
		else {
			dismiss();
			return true;
		}
	}

	private void animateBotButton(boolean isVisible) {
		ChatActivityBotWebViewButton botWebViewButton = parentEnterView.getBotWebViewButton();
		if (botWebViewButtonAnimator != null) {
			botWebViewButtonAnimator.cancel();
			botWebViewButtonAnimator = null;
		}

		botWebViewButton.setProgress(isVisible ? 0f : 1f);
		if (isVisible) {
			botWebViewButton.setVisibility(VISIBLE);
		}

		botWebViewButtonAnimator = new SpringAnimation(botWebViewButton, ChatActivityBotWebViewButton.PROGRESS_PROPERTY).setSpring(new SpringForce((isVisible ? 1f : 0f) * ChatActivityBotWebViewButton.PROGRESS_PROPERTY.getMultiplier()).setStiffness(isVisible ? 600f : 750f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addUpdateListener((animation, value, velocity) -> {
			float v = value / ChatActivityBotWebViewButton.PROGRESS_PROPERTY.getMultiplier();
			parentEnterView.setBotWebViewButtonOffsetX(AndroidUtilities.dp(64) * v);
			parentEnterView.setComposeShadowAlpha(1f - v);
		}).addEndListener((animation, canceled, value, velocity) -> {
			if (!isVisible) {
				botWebViewButton.setVisibility(GONE);
			}
			if (botWebViewButtonAnimator == animation) {
				botWebViewButtonAnimator = null;
			}
		});
		botWebViewButtonAnimator.start();
		botWebViewButtonWasVisible = isVisible;
	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();

		if (springAnimation == null) {
			springAnimation = new SpringAnimation(this, ACTION_BAR_TRANSITION_PROGRESS_VALUE).setSpring(new SpringForce().setStiffness(1200f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)).addEndListener((animation, canceled, value, velocity) -> {
				ChatActivity chatActivity = parentEnterView.getParentFragment();
				ChatAvatarContainer chatAvatarContainer = chatActivity.getAvatarContainer();
				chatAvatarContainer.setClickable(value == 0);
				chatAvatarContainer.getAvatarImageView().setClickable(value == 0);

				ActionBar actionBar = chatActivity.getActionBar();
				if (value == 100 && parentEnterView.hasBotWebView()) {
					chatActivity.showHeaderItem(false);
					botMenuItem.setVisibility(VISIBLE);
					actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
						@Override
						public void onItemClick(int id) {
							if (id == -1) {
								if (!webViewContainer.onBackPressed()) {
									onCheckDismissByUser();
								}
							}
							else if (id == R.id.menu_reload_page) {
								if (webViewContainer.getWebView() != null) {
									webViewContainer.getWebView().animate().cancel();
									webViewContainer.getWebView().animate().alpha(0).start();
								}

								isLoaded = false;
								progressView.setLoadProgress(0);
								progressView.setAlpha(1f);
								progressView.setVisibility(VISIBLE);

								webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
								webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);
								webViewContainer.reload();
							}
							else if (id == R.id.menu_settings) {
								webViewContainer.onSettingsButtonPressed();
							}
						}
					});
				}
				else {
					chatActivity.showHeaderItem(true);
					botMenuItem.setVisibility(GONE);
					actionBar.setActionBarMenuOnItemClick(actionBarOnItemClick);
				}
			});
		}
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.webViewResultSent);
		NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		if (springAnimation != null) {
			springAnimation.cancel();
			springAnimation = null;
		}
		actionBarTransitionProgress = 0f;
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.webViewResultSent);
		NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (ignoreMeasure) {
			setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight());
		}
		else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}

	public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
		if (!keyboardVisible) {
			return;
		}

		boolean doNotScroll = false;
		float openOffset = -swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY();
		if (swipeContainer.getSwipeOffsetY() != openOffset) {
			swipeContainer.stickTo(openOffset);
			doNotScroll = true;
		}

		int oldh = contentHeight + parentEnterView.getSizeNotifierLayout().measureKeyboardHeight();
		setMeasuredDimension(getMeasuredWidth(), contentHeight);
		ignoreMeasure = true;

		if (!doNotScroll) {
			if (webViewScrollAnimator != null) {
				webViewScrollAnimator.cancel();
				webViewScrollAnimator = null;
			}

			if (webViewContainer.getWebView() != null) {
				int fromY = webViewContainer.getWebView().getScrollY();
				int toY = fromY + (oldh - contentHeight);
				webViewScrollAnimator = ValueAnimator.ofInt(fromY, toY).setDuration(250);
				webViewScrollAnimator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
				webViewScrollAnimator.addUpdateListener(animation -> {
					int val = (int)animation.getAnimatedValue();
					if (webViewContainer.getWebView() != null) {
						webViewContainer.getWebView().setScrollY(val);
					}
				});
				webViewScrollAnimator.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						if (webViewContainer.getWebView() != null) {
							webViewContainer.getWebView().setScrollY(toY);
						}
						if (animation == webViewScrollAnimator) {
							webViewScrollAnimator = null;
						}
					}
				});
				webViewScrollAnimator.start();
			}
		}
	}

	public void onPanTransitionEnd() {
		ignoreMeasure = false;
		requestLayout();
	}

	private void updateLightStatusBar() {
		int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
		boolean lightStatusBar = ColorUtils.calculateLuminance(color) >= 0.9 && actionBarTransitionProgress >= 0.85f;

		if (wasLightStatusBar != null && wasLightStatusBar == lightStatusBar) {
			return;
		}
		wasLightStatusBar = lightStatusBar;

		int flags = getSystemUiVisibility();
		if (lightStatusBar) {
			flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
		}
		else {
			flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
		}
		setSystemUiVisibility(flags);
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);

		if (!overrideBackgroundColor) {
			backgroundPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
		}
		if (overrideActionBarBackgroundProgress == 0) {
			actionBarPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
		}
		AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
		canvas.drawRect(AndroidUtilities.rectTmp, dimPaint);

		float radius = AndroidUtilities.dp(16) * (1f - actionBarTransitionProgress);
		AndroidUtilities.rectTmp.set(0, AndroidUtilities.lerp(swipeContainer.getTranslationY(), 0, actionBarTransitionProgress), getWidth(), swipeContainer.getTranslationY() + AndroidUtilities.dp(24) + radius);
		canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, actionBarPaint);

		AndroidUtilities.rectTmp.set(0, swipeContainer.getTranslationY() + AndroidUtilities.dp(24), getWidth(), getHeight() + radius);
		canvas.drawRect(AndroidUtilities.rectTmp, backgroundPaint);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() <= AndroidUtilities.lerp(swipeContainer.getTranslationY() + AndroidUtilities.dp(24), 0, actionBarTransitionProgress)) {
			onCheckDismissByUser();
			return true;
		}
		return super.onTouchEvent(event);
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		super.draw(canvas);

		linePaint.setColor(getColor(Theme.key_sheet_scrollUp));
		linePaint.setAlpha((int)(linePaint.getAlpha() * (1f - Math.min(0.5f, actionBarTransitionProgress) / 0.5f)));

		canvas.save();
		float scale = 1f - actionBarTransitionProgress;
		float y = AndroidUtilities.lerp(swipeContainer.getTranslationY(), AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() / 2f, actionBarTransitionProgress) + AndroidUtilities.dp(12);
		canvas.scale(scale, scale, getWidth() / 2f, y);
		canvas.drawLine(getWidth() / 2f - AndroidUtilities.dp(16), y, getWidth() / 2f + AndroidUtilities.dp(16), y, linePaint);
		canvas.restore();
	}

	/**
	 * Shows menu for the bot
	 */
	public void show(int currentAccount, long botId, String botUrl) {
		dismissed = false;
		if (this.currentAccount != currentAccount || this.botId != botId || !Objects.equals(this.botUrl, botUrl)) {
			isLoaded = false;
		}
		this.currentAccount = currentAccount;
		this.botId = botId;
		this.botUrl = botUrl;

		savedEditText = parentEnterView.getEditField().getText();
		parentEnterView.getEditField().setText(null);
		savedReplyMessageObject = parentEnterView.getReplyingMessageObject();
		savedEditMessageObject = parentEnterView.getEditingMessageObject();
		ChatActivity chatActivity = parentEnterView.getParentFragment();
		if (chatActivity != null) {
			chatActivity.hideFieldPanel(true);
		}

		if (!isLoaded) {
			loadWebView();
		}

		setVisibility(VISIBLE);
		setAlpha(0f);
		addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
				v.removeOnLayoutChangeListener(this);

				swipeContainer.setSwipeOffsetY(swipeContainer.getHeight());
				setAlpha(1f);

				new SpringAnimation(swipeContainer, ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer.SWIPE_OFFSET_Y, 0).setSpring(new SpringForce(0).setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY).setStiffness(500.0f)).addEndListener((animation, canceled, value, velocity) -> {
					webViewContainer.restoreButtonData();
					webViewContainer.invalidateViewPortHeight(true);
				}).start();
			}
		});
	}

	private void loadWebView() {
		progressView.setLoadProgress(0);
		progressView.setAlpha(1f);
		progressView.setVisibility(VISIBLE);

		webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
		webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);

		var req = new TLRPC.TLMessagesRequestWebView();
		req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
		req.peer = MessagesController.getInstance(currentAccount).getInputPeer(botId);
		req.platform = "android";

		req.url = botUrl;
		req.flags |= 2;

		try {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("bg_color", getColor(Theme.key_windowBackgroundWhite));
			jsonObject.put("secondary_bg_color", getColor(Theme.key_windowBackgroundGray));
			jsonObject.put("text_color", getColor(Theme.key_windowBackgroundWhiteBlackText));
			jsonObject.put("hint_color", getColor(Theme.key_windowBackgroundWhiteHintText));
			jsonObject.put("link_color", getColor(Theme.key_windowBackgroundWhiteLinkText));
			jsonObject.put("button_color", getColor(Theme.key_featuredStickers_addButton));
			jsonObject.put("button_text_color", getColor(Theme.key_featuredStickers_buttonText));

			req.themeParams = new TLRPC.TLDataJSON();
			req.themeParams.data = jsonObject.toString();
			req.flags |= 4;
		}
		catch (Exception e) {
			FileLog.e(e);
		}
		req.fromBotMenu = true;

		ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			if (response instanceof TLRPC.TLWebViewResult resultUrl) {
				isLoaded = true;
				queryId = resultUrl.queryId;
				webViewContainer.loadUrl(currentAccount, resultUrl.url);
				swipeContainer.setWebView(webViewContainer.getWebView());

				AndroidUtilities.runOnUIThread(pollRunnable, POLL_PERIOD);
			}
		}));
	}

	private int getColor(String key) {
		Integer color;
		Theme.ResourcesProvider resourcesProvider = parentEnterView.getParentFragment().getResourceProvider();
		if (resourcesProvider != null) {
			color = resourcesProvider.getColor(key);
		}
		else {
			color = Theme.getColor(key);
		}
		return color != null ? color : Theme.getColor(key);
	}

	/**
	 * Sets global dismiss callback to run every time menu being dismissed
	 */
	public void setOnDismissGlobalListener(Runnable callback) {
		globalOnDismissListener = callback;
	}

	/**
	 * Dismisses menu
	 */
	public void dismiss() {
		dismiss(null);
	}

	/**
	 * Dismisses menu
	 */
	public void dismiss(Runnable callback) {
		if (dismissed) {
			return;
		}
		dismissed = true;
		swipeContainer.stickTo(swipeContainer.getHeight() + parentEnterView.getSizeNotifierLayout().measureKeyboardHeight(), () -> {
			onDismiss();
			if (callback != null) {
				callback.run();
			}
			if (globalOnDismissListener != null) {
				globalOnDismissListener.run();
			}
		});
	}

	/**
	 * Called when menu is fully dismissed
	 */
	public void onDismiss() {
		setVisibility(GONE);

		needCloseConfirmation = false;
		overrideActionBarBackground = 0;
		overrideActionBarBackgroundProgress = 0;
		actionBarPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
		webViewContainer.destroyWebView();
		swipeContainer.removeView(webViewContainer);

		webViewContainer = new BotWebViewContainer(getContext(), getColor(Theme.key_windowBackgroundWhite));
		webViewContainer.setDelegate(webViewDelegate);
		webViewContainer.setWebViewProgressListener(progress -> {
			progressView.setLoadProgressAnimated(progress);
			if (progress == 1f) {
				ValueAnimator animator = ValueAnimator.ofFloat(1, 0).setDuration(200);
				animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
				animator.addUpdateListener(animation -> progressView.setAlpha((Float)animation.getAnimatedValue()));
				animator.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						progressView.setVisibility(GONE);
					}
				});
				animator.start();
			}
		});
		swipeContainer.addView(webViewContainer);
		isLoaded = false;

		AndroidUtilities.cancelRunOnUIThread(pollRunnable);
		boolean delayRestoreText = botWebViewButtonWasVisible;
		if (botWebViewButtonWasVisible) {
			botWebViewButtonWasVisible = false;
			animateBotButton(false);
		}

		AndroidUtilities.runOnUIThread(() -> {
			if (savedEditText != null) {
				parentEnterView.getEditField().setText(savedEditText);
				savedEditText = null;
			}
			if (savedReplyMessageObject != null) {
				ChatActivity chatActivity = parentEnterView.getParentFragment();
				if (chatActivity != null) {
					chatActivity.showFieldPanelForReply(savedReplyMessageObject);
				}
				savedReplyMessageObject = null;
			}
			if (savedEditMessageObject != null) {
				ChatActivity chatActivity = parentEnterView.getParentFragment();
				if (chatActivity != null) {
					chatActivity.showFieldPanelForEdit(true, savedEditMessageObject);
				}
				savedEditMessageObject = null;
			}
		}, delayRestoreText ? 200 : 0);
	}

	public boolean hasSavedText() {
		return savedEditText != null || savedReplyMessageObject != null || savedEditMessageObject != null;
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.webViewResultSent) {
			long queryId = (long)args[0];

			if (this.queryId == queryId) {
				dismiss();
			}
		}
		else if (id == NotificationCenter.didSetNewTheme) {
			webViewContainer.updateFlickerBackgroundColor(getColor(Theme.key_windowBackgroundWhite));
			invalidate();
			invalidateActionBar();
			AndroidUtilities.runOnUIThread(this::invalidateActionBar, 300);
		}
	}
}
