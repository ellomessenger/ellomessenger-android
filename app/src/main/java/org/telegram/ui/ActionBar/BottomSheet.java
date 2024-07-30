/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

public class BottomSheet extends Dialog {
	private final static boolean AVOID_SYSTEM_CUTOUT_FULLSCREEN = false;
	private final int touchSlop;
	private final ArrayList<BottomSheetCell> itemViews = new ArrayList<>();
	public boolean drawNavigationBar;
	public boolean drawDoubleNavigationBar;
	public boolean scrollNavBar;
	public boolean useBackgroundTopPadding = true;
	protected int currentAccount = UserConfig.selectedAccount;
	protected ViewGroup containerView;
	@NonNull
	protected ContainerView container;
	protected boolean keyboardVisible;
	protected boolean useSmoothKeyboard;
	protected Runnable startAnimationRunnable;
	protected boolean useHardwareLayer = true;
	protected boolean fullWidth;
	protected boolean isFullscreen;
	protected ColorDrawable backDrawable = new ColorDrawable(0xff000000) {
		@Override
		public void setAlpha(int alpha) {
			super.setAlpha(alpha);
			container.invalidate();
		}
	};
	protected boolean useLightStatusBar = true;
	protected boolean useLightNavBar;
	protected int behindKeyboardColor;
	protected boolean calcMandatoryInsets;
	protected Interpolator openInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT;
	protected boolean dimBehind = true;
	protected int dimBehindAlpha = 51;
	protected boolean allowNestedScroll = true;
	protected Drawable shadowDrawable;
	protected int backgroundPaddingTop;
	protected int backgroundPaddingLeft;
	protected BottomSheetDelegateInterface delegate;
	@Nullable
	protected AnimatorSet currentSheetAnimation;
	protected int currentSheetAnimationType;
	protected ValueAnimator navigationBarAnimation;
	protected float navigationBarAlpha = 0;
	protected View nestedScrollChild;
	protected int navBarColor;
	protected boolean isPortrait;
	protected boolean smoothKeyboardAnimationEnabled;
	protected int customViewGravity = Gravity.LEFT | Gravity.TOP;
	private WindowInsets lastInsets;
	private int layoutCount;
	private boolean dismissed;
	private int tag;
	private boolean allowDrawContent = true;
	private DialogInterface.OnClickListener onClickListener;
	private CharSequence[] items;
	private int[] itemIcons;
	private View customView;
	private CharSequence title;
	private boolean bigTitle;
	private boolean multipleLinesTitle;
	private int bottomInset;
	private int leftInset;
	private int rightInset;
	private boolean canDismissWithSwipe = true;
	private boolean allowCustomAnimation = true;
	private boolean showWithoutAnimation;
	private int statusBarHeight = AndroidUtilities.statusBarHeight;
	private TextView titleView;
	private boolean focusable;
	private boolean applyTopPadding = true;
	private boolean applyBottomPadding = true;
	private boolean disableScroll;
	private float currentPanTranslationY;
	private OnDismissListener onHideListener;
	private ValueAnimator keyboardContentAnimator;
	private boolean openNoDelay;
	private float hideSystemVerticalInsetsProgress;
	private final Runnable dismissRunnable = this::dismiss;
	private int overlayDrawNavBarColor;

	public BottomSheet(Context context, boolean needFocus) {
		super(context, R.style.TransparentDialog);

		if (Build.VERSION.SDK_INT >= 30) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		}
		else {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		}
		ViewConfiguration vc = ViewConfiguration.get(context);
		touchSlop = vc.getScaledTouchSlop();

		Rect padding = new Rect();

		shadowDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.sheet_shadow_round, null).mutate();
		shadowDrawable.setColorFilter(new PorterDuffColorFilter(context.getColor(R.color.background), PorterDuff.Mode.SRC_IN));
		shadowDrawable.getPadding(padding);

		backgroundPaddingLeft = padding.left;
		backgroundPaddingTop = padding.top;

		container = new ContainerView(getContext()) {
			@Override
			public boolean drawChild(Canvas canvas, View child, long drawingTime) {
				try {
					return allowDrawContent && super.drawChild(canvas, child, drawingTime);
				}
				catch (Exception e) {
					FileLog.e(e);
				}
				return true;
			}

			@Override
			protected void dispatchDraw(Canvas canvas) {
				super.dispatchDraw(canvas);
				mainContainerDispatchDraw(canvas);
			}

			@Override
			protected void onConfigurationChanged(Configuration newConfig) {
				lastInsets = null;
				container.requestApplyInsets();
			}
		};

		container.setBackground(backDrawable);

		focusable = needFocus;

		container.setFitsSystemWindows(true);

		container.setOnApplyWindowInsetsListener((v, insets) -> {
			int newTopInset = insets.getSystemWindowInsetTop();

			if ((newTopInset != 0 || AndroidUtilities.isInMultiwindow) && statusBarHeight != newTopInset) {
				statusBarHeight = newTopInset;
			}

			lastInsets = insets;

			v.requestLayout();

			if (Build.VERSION.SDK_INT >= 30) {
				return WindowInsets.CONSUMED;
			}
			else {
				return insets.consumeSystemWindowInsets();
			}
		});

		if (Build.VERSION.SDK_INT >= 30) {
			container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		}
		else {
			container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		}

		backDrawable.setAlpha(0);
	}

	public void setDisableScroll(boolean b) {
		disableScroll = b;
	}

	protected int getBottomSheetWidth(boolean isPortrait, int width) {
		return isPortrait ? width : (int)Math.max(width * 0.8f, Math.min(AndroidUtilities.dp(480), width));
	}

	protected boolean shouldOverlayCameraViewOverNavBar() {
		return false;
	}

	public void setHideSystemVerticalInsets(boolean hideSystemVerticalInsets) {
		ValueAnimator animator = ValueAnimator.ofFloat(hideSystemVerticalInsetsProgress, hideSystemVerticalInsets ? 1f : 0f).setDuration(180);
		animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
		animator.addUpdateListener(animation -> {
			hideSystemVerticalInsetsProgress = (float)animation.getAnimatedValue();
			container.requestLayout();
			containerView.requestLayout();
		});
		animator.start();
	}

	@RequiresApi(api = Build.VERSION_CODES.Q)
	private int getAdditionalMandatoryOffsets() {
		if (!calcMandatoryInsets) {
			return 0;
		}

		Insets insets = lastInsets.getSystemGestureInsets();

		return !keyboardVisible && drawNavigationBar && insets != null && (insets.left != 0 || insets.right != 0) ? insets.bottom : 0;
	}

	public void setCalcMandatoryInsets(boolean value) {
		calcMandatoryInsets = value;
		drawNavigationBar = value;
	}

	public void setAllowNestedScroll(boolean value) {
		allowNestedScroll = value;
		if (!allowNestedScroll) {
			containerView.setTranslationY(0);
		}
	}

	protected void mainContainerDispatchDraw(Canvas canvas) {

	}

	public void fixNavigationBar() {
		fixNavigationBar(getContext().getColor(R.color.background));
	}

	public void fixNavigationBar(int bgColor) {
		drawNavigationBar = true;
		drawDoubleNavigationBar = true;
		scrollNavBar = true;
		setOverlayNavBarColor(navBarColor = bgColor);
	}

	@SuppressLint("AppCompatCustomView")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Window window = getWindow();
		window.setWindowAnimations(R.style.DialogNoAnimation);
		setContentView(container, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		if (useLightStatusBar) {
			int color = getContext().getColor(R.color.background);
			if (color == 0xffffffff) {
				int flags = container.getSystemUiVisibility();
				flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
				container.setSystemUiVisibility(flags);
			}
		}
		if (useLightNavBar && Build.VERSION.SDK_INT >= 26) {
			AndroidUtilities.setLightNavigationBar(getWindow(), false);
		}

		if (containerView == null) {
			containerView = new FrameLayout(getContext()) {
				@Override
				public boolean hasOverlappingRendering() {
					return false;
				}

				@Override
				public void setTranslationY(float translationY) {
					super.setTranslationY(translationY);
					onContainerTranslationYChanged(translationY);
				}
			};
			containerView.setBackground(shadowDrawable);
			containerView.setPadding(backgroundPaddingLeft, (applyTopPadding ? AndroidUtilities.dp(8) : 0) + backgroundPaddingTop - 1, backgroundPaddingLeft, (applyBottomPadding ? AndroidUtilities.dp(8) : 0));
		}

		containerView.setVisibility(View.INVISIBLE);
		container.addView(containerView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

		int topOffset = 0;
		if (title != null) {
			titleView = new TextView(getContext()) {
				@Override
				protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
					super.onMeasure(widthMeasureSpec, heightMeasureSpec);
					if (multipleLinesTitle) {
						int topOffset = getMeasuredHeight();
						if (customView != null) {
							((ViewGroup.MarginLayoutParams)customView.getLayoutParams()).topMargin = topOffset;
						}
						else if (containerView != null) {
							for (int i = 1; i < containerView.getChildCount(); ++i) {
								View child = containerView.getChildAt(i);
								if (child instanceof BottomSheetCell) {
									((ViewGroup.MarginLayoutParams)child.getLayoutParams()).topMargin = topOffset;
									topOffset += AndroidUtilities.dp(48);
								}
							}
						}
					}
				}
			};
			int height = 48;
			titleView.setText(title);
			if (bigTitle) {
				titleView.setTextColor(getContext().getColor(R.color.text));
				titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
				titleView.setTypeface(Theme.TYPEFACE_BOLD);
				titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(multipleLinesTitle ? 14 : 6), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
			}
			else {
				titleView.setTextColor(getContext().getColor(R.color.dark_gray));
				titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				titleView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(multipleLinesTitle ? 8 : 0), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
			}
			if (multipleLinesTitle) {
				titleView.setSingleLine(false);
				titleView.setMaxLines(5);
				titleView.setEllipsize(TextUtils.TruncateAt.END);
			}
			else {
				titleView.setLines(1);
				titleView.setSingleLine(true);
				titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
			}
			titleView.setGravity(Gravity.CENTER_VERTICAL);
			containerView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, multipleLinesTitle ? ViewGroup.LayoutParams.WRAP_CONTENT : height));
			titleView.setOnTouchListener((v, event) -> true);
			topOffset += height;
		}
		if (customView != null) {
			if (customView.getParent() != null) {
				ViewGroup viewGroup = (ViewGroup)customView.getParent();
				viewGroup.removeView(customView);
			}
			if (!useBackgroundTopPadding) {
				containerView.setClipToPadding(false);
				containerView.setClipChildren(false);
				container.setClipToPadding(false);
				container.setClipChildren(false);
				containerView.addView(customView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, customViewGravity, 0, -backgroundPaddingTop + topOffset, 0, 0));
			}
			else {
				containerView.addView(customView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, customViewGravity, 0, topOffset, 0, 0));
			}
		}
		else {
			if (items != null) {
				for (int a = 0; a < items.length; a++) {
					if (items[a] == null) {
						continue;
					}
					BottomSheetCell cell = new BottomSheetCell(getContext(), 0);
					cell.setTextAndIcon(items[a], itemIcons != null ? itemIcons[a] : 0, null, bigTitle);
					containerView.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP, 0, topOffset, 0, 0));
					topOffset += 48;
					cell.setTag(a);
					cell.setOnClickListener(v -> dismissWithButtonClick((Integer)v.getTag()));
					itemViews.add(cell);
				}
			}
		}

		WindowManager.LayoutParams params = window.getAttributes();
		params.width = ViewGroup.LayoutParams.MATCH_PARENT;
		params.gravity = Gravity.TOP | Gravity.LEFT;
		params.dimAmount = 0;
		params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
		if (focusable) {
			params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
		}
		else {
			params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
		}
		if (isFullscreen) {
			params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
			params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
			container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
		}
		params.height = ViewGroup.LayoutParams.MATCH_PARENT;
		if (Build.VERSION.SDK_INT >= 28) {
			params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}
		window.setAttributes(params);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	public void setUseLightStatusBar(boolean value) {
		useLightStatusBar = value;
		int color = getContext().getColor(R.color.background);
		int flags = container.getSystemUiVisibility();
		if (useLightStatusBar && color == 0xffffffff) {
			flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
		}
		else {
			flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
		}
		container.setSystemUiVisibility(flags);
	}

	public boolean isFocusable() {
		return focusable;
	}

	public void setFocusable(boolean value) {
		if (focusable == value) {
			return;
		}
		focusable = value;
		Window window = getWindow();
		WindowManager.LayoutParams params = window.getAttributes();
		if (focusable) {
			params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
			params.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
		}
		else {
			params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
			params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
		}
		window.setAttributes(params);
	}

	public void setShowWithoutAnimation(boolean value) {
		showWithoutAnimation = value;
	}

	public void setBackgroundColor(int color) {
		shadowDrawable.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
	}

	@Override
	public void show() {
		super.show();
		if (focusable) {
			getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		}
		dismissed = false;
		cancelSheetAnimation();
		containerView.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x + backgroundPaddingLeft * 2, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST));
		if (showWithoutAnimation) {
			backDrawable.setAlpha(dimBehind ? dimBehindAlpha : 0);
			containerView.setTranslationY(0);
			return;
		}
		backDrawable.setAlpha(0);
		layoutCount = 2;
		containerView.setTranslationY(AndroidUtilities.statusBarHeight * (1f - hideSystemVerticalInsetsProgress) + containerView.getMeasuredHeight() + (scrollNavBar ? getBottomInset() : 0));
		AndroidUtilities.runOnUIThread(startAnimationRunnable = new Runnable() {
			@Override
			public void run() {
				if (startAnimationRunnable != this || dismissed) {
					return;
				}
				startAnimationRunnable = null;
				startOpenAnimation();
			}
		}, openNoDelay ? 0 : 150);
	}

	public ColorDrawable getBackDrawable() {
		return backDrawable;
	}

	public int getBackgroundPaddingTop() {
		return backgroundPaddingTop;
	}

	public void setAllowDrawContent(boolean value) {
		if (allowDrawContent != value) {
			allowDrawContent = value;
			container.setBackground(allowDrawContent ? backDrawable : null);
			container.invalidate();
		}
	}

	protected boolean canDismissWithSwipe() {
		return canDismissWithSwipe;
	}

	public void setCanDismissWithSwipe(boolean value) {
		canDismissWithSwipe = value;
	}

	protected boolean onContainerTouchEvent(MotionEvent event) {
		return false;
	}

	protected boolean onScrollUp(float translationY) {
		return false;
	}

	protected void onScrollUpEnd(float translationY) {
	}

	protected void onScrollUpBegin(float translationY) {
	}

	public void setCustomView(View view) {
		customView = view;
	}

	public void setTitle(CharSequence value) {
		setTitle(value, false);
	}

	public void setTitle(CharSequence value, boolean big) {
		title = value;
		bigTitle = big;
	}

	public void setApplyTopPadding(boolean value) {
		applyTopPadding = value;
	}

	public void setApplyBottomPadding(boolean value) {
		applyBottomPadding = value;
	}

	protected boolean onCustomMeasure(View view, int width, int height) {
		return false;
	}

	protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
		return false;
	}

	protected void onDismissWithTouchOutside() {
		dismiss();
	}

	protected boolean canDismissWithTouchOutside() {
		return true;
	}

	@Nullable
	public TextView getTitleView() {
		return titleView;
	}

	protected void onContainerTranslationYChanged(float translationY) {

	}

	protected void cancelSheetAnimation() {
		if (currentSheetAnimation != null) {
			currentSheetAnimation.cancel();
			currentSheetAnimation = null;
		}
		currentSheetAnimationType = 0;
	}

	public void setOnHideListener(OnDismissListener listener) {
		onHideListener = listener;
	}

	protected int getTargetOpenTranslationY() {
		return 0;
	}

	private void startOpenAnimation() {
		if (dismissed) {
			return;
		}
		containerView.setVisibility(View.VISIBLE);

		if (!onCustomOpenAnimation()) {
			if (useHardwareLayer) {
				container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
			}
			containerView.setTranslationY(getContainerViewHeight() + container.keyboardHeight + AndroidUtilities.dp(10) + (scrollNavBar ? getBottomInset() : 0));
			currentSheetAnimationType = 1;
			if (navigationBarAnimation != null) {
				navigationBarAnimation.cancel();
			}
			navigationBarAnimation = ValueAnimator.ofFloat(navigationBarAlpha, 1f);
			navigationBarAnimation.addUpdateListener(a -> {
				navigationBarAlpha = (float)a.getAnimatedValue();
				if (container != null) {
					container.invalidate();
				}
			});
			currentSheetAnimation = new AnimatorSet();
			currentSheetAnimation.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0), ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, dimBehind ? dimBehindAlpha : 0), navigationBarAnimation);
			currentSheetAnimation.setDuration(400);
			currentSheetAnimation.setStartDelay(20);
			currentSheetAnimation.setInterpolator(openInterpolator);
			currentSheetAnimation.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
						currentSheetAnimation = null;
						currentSheetAnimationType = 0;
						if (delegate != null) {
							delegate.onOpenAnimationEnd();
						}
						if (useHardwareLayer) {
							container.setLayerType(View.LAYER_TYPE_NONE, null);
						}

						if (isFullscreen) {
							WindowManager.LayoutParams params = getWindow().getAttributes();
							params.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
							getWindow().setAttributes(params);
						}
					}
					NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
				}

				@Override
				public void onAnimationCancel(Animator animation) {
					if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
						currentSheetAnimation = null;
						currentSheetAnimationType = 0;
					}
				}
			});
			NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
			currentSheetAnimation.start();
		}
	}

	public void setDelegate(BottomSheetDelegateInterface bottomSheetDelegate) {
		delegate = bottomSheetDelegate;
	}

	@NonNull
	public FrameLayout getContainer() {
		return container;
	}

	public ViewGroup getSheetContainer() {
		return containerView;
	}

	public int getTag() {
		return tag;
	}

	public void setDimBehind(boolean value) {
		dimBehind = value;
	}

	public void setDimBehindAlpha(int value) {
		dimBehindAlpha = value;
	}

	public void setItemText(int item, CharSequence text) {
		if (item < 0 || item >= itemViews.size()) {
			return;
		}
		BottomSheetCell cell = itemViews.get(item);
		cell.textView.setText(text);
	}

	public void setItemColor(int item, int color, int icon) {
		if (item < 0 || item >= itemViews.size()) {
			return;
		}
		BottomSheetCell cell = itemViews.get(item);
		cell.textView.setTextColor(color);
		cell.imageView.setColorFilter(new PorterDuffColorFilter(icon, PorterDuff.Mode.MULTIPLY));
	}

	public ArrayList<BottomSheetCell> getItemViews() {
		return itemViews;
	}

	public void setItems(CharSequence[] i, int[] icons, final OnClickListener listener) {
		items = i;
		itemIcons = icons;
		onClickListener = listener;
	}

	public void setTitleColor(int color) {
		if (titleView == null) {
			return;
		}
		titleView.setTextColor(color);
	}

	public boolean isDismissed() {
		return dismissed;
	}

	public void dismissWithButtonClick(final int item) {
		if (dismissed) {
			return;
		}
		dismissed = true;
		cancelSheetAnimation();
		currentSheetAnimationType = 2;
		currentSheetAnimation = new AnimatorSet();
		currentSheetAnimation.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, getContainerViewHeight() + container.keyboardHeight + AndroidUtilities.dp(10) + (scrollNavBar ? getBottomInset() : 0)), ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0));
		currentSheetAnimation.setDuration(180);
		currentSheetAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
		currentSheetAnimation.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
					currentSheetAnimation = null;
					currentSheetAnimationType = 0;
					if (onClickListener != null) {
						onClickListener.onClick(BottomSheet.this, item);
					}
					AndroidUtilities.runOnUIThread(() -> {
						if (onHideListener != null) {
							onHideListener.onDismiss(BottomSheet.this);
						}
						try {
							dismissInternal();
						}
						catch (Exception e) {
							FileLog.e(e);
						}
					});
				}
				NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
			}

			@Override
			public void onAnimationCancel(Animator animation) {
				if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
					currentSheetAnimation = null;
					currentSheetAnimationType = 0;
				}
			}
		});
		NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
		currentSheetAnimation.start();
	}

	@Override
	public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
		if (dismissed) {
			return false;
		}
		return super.dispatchTouchEvent(ev);
	}

	public void onDismissAnimationStart() {
	}

	public int getContainerViewHeight() {
		if (containerView == null) {
			return 0;
		}
		return containerView.getMeasuredHeight();
	}

	@Override
	public void dismiss() {
		if (delegate != null && !delegate.canDismiss()) {
			return;
		}
		if (dismissed) {
			return;
		}
		dismissed = true;
		if (onHideListener != null) {
			onHideListener.onDismiss(this);
		}
		cancelSheetAnimation();
		long duration = 0;
		onDismissAnimationStart();
		if (!allowCustomAnimation || !onCustomCloseAnimation()) {
			currentSheetAnimationType = 2;
			currentSheetAnimation = new AnimatorSet();
			if (navigationBarAnimation != null) {
				navigationBarAnimation.cancel();
			}
			navigationBarAnimation = ValueAnimator.ofFloat(navigationBarAlpha, 0f);
			navigationBarAnimation.addUpdateListener(a -> {
				navigationBarAlpha = (float)a.getAnimatedValue();
				if (container != null) {
					container.invalidate();
				}
			});
			currentSheetAnimation.playTogether(containerView == null ? null : ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, getContainerViewHeight() + container.keyboardHeight + AndroidUtilities.dp(10) + (scrollNavBar ? getBottomInset() : 0)), ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0), navigationBarAnimation);
//            if (useFastDismiss) {
//                int height = containerView.getMeasuredHeight();
//                duration = Math.max(60, (int) (250 * (height - containerView.getTranslationY()) / (float) height));
//                currentSheetAnimation.setDuration(duration);
//                useFastDismiss = false;
//            } else {
			currentSheetAnimation.setDuration(duration = 250);
//            }
			currentSheetAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
			currentSheetAnimation.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
						currentSheetAnimation = null;
						currentSheetAnimationType = 0;
						AndroidUtilities.runOnUIThread(() -> {
							try {
								dismissInternal();
							}
							catch (Exception e) {
								FileLog.e(e);
							}
						});
					}
					NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
				}

				@Override
				public void onAnimationCancel(Animator animation) {
					if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
						currentSheetAnimation = null;
						currentSheetAnimationType = 0;
					}
				}
			});
			NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
			currentSheetAnimation.start();
		}

		Bulletin bulletin = Bulletin.getVisibleBulletin();
		if (bulletin != null && bulletin.isShowing()) {
			if (duration > 0) {
				bulletin.hide((long)(duration * 0.6f));
			}
			else {
				bulletin.hide();
			}
		}
	}

	public int getSheetAnimationType() {
		return currentSheetAnimationType;
	}

	public void dismissInternal() {
		try {
			super.dismiss();
		}
		catch (Exception e) {
			FileLog.e(e);
		}
	}

	protected boolean onCustomCloseAnimation() {
		return false;
	}

	protected boolean onCustomOpenAnimation() {
		return false;
	}

	public int getLeftInset() {
		if (lastInsets != null) {
			float inset;
			if (AVOID_SYSTEM_CUTOUT_FULLSCREEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lastInsets.getDisplayCutout() != null) {
				inset = lastInsets.getDisplayCutout().getSafeInsetLeft() + (lastInsets.getSystemWindowInsetLeft() - lastInsets.getDisplayCutout().getSafeInsetLeft()) * (1f - hideSystemVerticalInsetsProgress);
			}
			else {
				inset = lastInsets.getSystemWindowInsetLeft() * (1f - hideSystemVerticalInsetsProgress);
			}
			return (int)inset;
		}
		return 0;
	}

	public int getRightInset() {
		if (lastInsets != null) {
			float inset;
			if (AVOID_SYSTEM_CUTOUT_FULLSCREEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lastInsets.getDisplayCutout() != null) {
				inset = lastInsets.getDisplayCutout().getSafeInsetRight() + (lastInsets.getSystemWindowInsetRight() - lastInsets.getDisplayCutout().getSafeInsetRight()) * (1f - hideSystemVerticalInsetsProgress);
			}
			else {
				inset = lastInsets.getSystemWindowInsetRight() * (1f - hideSystemVerticalInsetsProgress);
			}
			return (int)inset;
		}
		return 0;
	}

	public int getStatusBarHeight() {
		return (int)(statusBarHeight * (1f - hideSystemVerticalInsetsProgress));
	}

	public int getBottomInset() {
		return (int)(bottomInset * (1f - hideSystemVerticalInsetsProgress));
	}

	public void onConfigurationChanged(android.content.res.Configuration newConfig) {

	}

	public void onContainerDraw(Canvas canvas) {

	}

	public ArrayList<ThemeDescription> getThemeDescriptions() {
		return null;
	}

	public void setCurrentPanTranslationY(float currentPanTranslationY) {
		this.currentPanTranslationY = currentPanTranslationY;
		container.invalidate();
	}

	public void setOverlayNavBarColor(int color) {
		overlayDrawNavBarColor = color;
		if (container != null) {
			container.invalidate();
		}

//        if (Color.alpha(color) > 120) {
//            AndroidUtilities.setLightStatusBar(getWindow(), false);
//            AndroidUtilities.setLightNavigationBar(getWindow(), false);
//        } else {
//            AndroidUtilities.setLightStatusBar(getWindow(), !useLightStatusBar);
//            AndroidUtilities.setLightNavigationBar(getWindow(), !useLightNavBar);
//        }
		AndroidUtilities.setNavigationBarColor(getWindow(), overlayDrawNavBarColor);
		AndroidUtilities.setLightNavigationBar(getWindow(), AndroidUtilities.computePerceivedBrightness(overlayDrawNavBarColor) > .721);
	}

	public ViewGroup getContainerView() {
		return containerView;
	}

	public int getCurrentAccount() {
		return currentAccount;
	}

	public void setOpenNoDelay(boolean openNoDelay) {
		this.openNoDelay = openNoDelay;
	}

	public int getBackgroundPaddingLeft() {
		return this.backgroundPaddingLeft;
	}

	public interface BottomSheetDelegateInterface {
		void onOpenAnimationStart();

		void onOpenAnimationEnd();

		boolean canDismiss();
	}

	public static class BottomSheetDelegate implements BottomSheetDelegateInterface {
		@Override
		public void onOpenAnimationStart() {

		}

		@Override
		public void onOpenAnimationEnd() {

		}

		@Override
		public boolean canDismiss() {
			return true;
		}
	}

	public static class BottomSheetCell extends FrameLayout {
		private final TextView textView;
		private final ImageView imageView;
		public boolean isSelected = false;
		int currentType;

		public BottomSheetCell(Context context, int type) {
			super(context);

			currentType = type;

			setBackground(Theme.getSelectorDrawable(false));

			//setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

			imageView = new ImageView(context);
			imageView.setScaleType(ImageView.ScaleType.CENTER);
			imageView.setColorFilter(new PorterDuffColorFilter(ResourcesCompat.getColor(context.getResources(), R.color.brand_day_night, null), PorterDuff.Mode.SRC_IN));
			addView(imageView, LayoutHelper.createFrame(56, 48, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

			textView = new TextView(context);
			textView.setLines(1);
			textView.setSingleLine(true);
			textView.setGravity(Gravity.CENTER_HORIZONTAL);
			textView.setEllipsize(TextUtils.TruncateAt.END);
			if (type == 0) {
				textView.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
			}
			else if (type == 1) {
				textView.setGravity(Gravity.CENTER);
				textView.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.text, null));
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
				textView.setTypeface(Theme.TYPEFACE_BOLD);
				addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
			}
			else if (type == 2) {
				textView.setGravity(Gravity.CENTER);
				textView.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.white, null));
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
				textView.setTypeface(Theme.TYPEFACE_BOLD);
				textView.setBackground(Theme.AdaptiveRipple.filledRect(ResourcesCompat.getColor(context.getResources(), R.color.white, null), 4));
				addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, 16, 16, 16));
			}
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int height = currentType == 2 ? 80 : 48;
			if (currentType == 0) {
				widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
			}
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(height), MeasureSpec.EXACTLY));
		}

		public void setTextColor(int color) {
			textView.setTextColor(color);
		}

		public void setIconColor(int color) {
			imageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
		}

		public void setGravity(int gravity) {
			textView.setGravity(gravity);
		}

		public void setTextAndIcon(CharSequence text, int icon) {
			setTextAndIcon(text, icon, null, false);
		}

		public void setTextAndIcon(CharSequence text, Drawable icon) {
			setTextAndIcon(text, 0, icon, false);
		}

		public void setTextAndIcon(CharSequence text, int icon, Drawable drawable, boolean bigTitle) {
			textView.setText(text);
			if (icon != 0 || drawable != null) {
				if (drawable != null) {
					imageView.setImageDrawable(drawable);
				}
				else {
					imageView.setImageResource(icon);
				}
				imageView.setVisibility(VISIBLE);
				if (bigTitle) {
					textView.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 21 : 72), 0, AndroidUtilities.dp(LocaleController.isRTL ? 72 : 21), 0);
					imageView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(5), 0, LocaleController.isRTL ? AndroidUtilities.dp(5) : 5, 0);
				}
				else {
					textView.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 16 : 72), 0, AndroidUtilities.dp(LocaleController.isRTL ? 72 : 16), 0);
					imageView.setPadding(0, 0, 0, 0);
				}
			}
			else {
				imageView.setVisibility(INVISIBLE);
				textView.setPadding(AndroidUtilities.dp(bigTitle ? 21 : 16), 0, AndroidUtilities.dp(bigTitle ? 21 : 16), 0);
			}
		}

		public TextView getTextView() {
			return textView;
		}

		public ImageView getImageView() {
			return imageView;
		}

		@Override
		public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
			super.onInitializeAccessibilityNodeInfo(info);
			if (isSelected) {
				info.setSelected(true);
			}
		}
	}

	public static class Builder {
		private final BottomSheet bottomSheet;

		public Builder(Context context) {
			this(context, false);
		}

		public Builder(Context context, boolean needFocus) {
			bottomSheet = new BottomSheet(context, needFocus);
			bottomSheet.fixNavigationBar();
		}

		public Builder(Context context, boolean needFocus, int bgColor) {
			bottomSheet = new BottomSheet(context, needFocus);
			bottomSheet.setBackgroundColor(bgColor);
			bottomSheet.fixNavigationBar(bgColor);
		}

		public Builder setItems(CharSequence[] items, final OnClickListener onClickListener) {
			bottomSheet.items = items;
			bottomSheet.onClickListener = onClickListener;
			return this;
		}

		public Builder setItems(CharSequence[] items, int[] icons, final OnClickListener onClickListener) {
			bottomSheet.items = items;
			bottomSheet.itemIcons = icons;
			bottomSheet.onClickListener = onClickListener;
			return this;
		}

		public View getCustomView() {
			return bottomSheet.customView;
		}

		public Builder setCustomView(View view) {
			bottomSheet.customView = view;
			return this;
		}

		public Builder setTitle(CharSequence title) {
			return setTitle(title, false);
		}

		public Builder setTitle(CharSequence title, boolean big) {
			bottomSheet.title = title;
			bottomSheet.bigTitle = big;
			return this;
		}

		public Builder setTitleMultipleLines(boolean allowMultipleLines) {
			bottomSheet.multipleLinesTitle = allowMultipleLines;
			return this;
		}

		public BottomSheet create() {
			return bottomSheet;
		}

		public BottomSheet setDimBehind(boolean value) {
			bottomSheet.dimBehind = value;
			return bottomSheet;
		}

		public BottomSheet show() {
			bottomSheet.show();
			return bottomSheet;
		}

		public Builder setTag(int tag) {
			bottomSheet.tag = tag;
			return this;
		}

		public Builder setUseHardwareLayer(boolean value) {
			bottomSheet.useHardwareLayer = value;
			return this;
		}

		public Builder setDelegate(BottomSheetDelegate delegate) {
			bottomSheet.setDelegate(delegate);
			return this;
		}

		public Builder setApplyTopPadding(boolean value) {
			bottomSheet.applyTopPadding = value;
			return this;
		}

		public Builder setApplyBottomPadding(boolean value) {
			bottomSheet.applyBottomPadding = value;
			return this;
		}

		public Runnable getDismissRunnable() {
			return bottomSheet.dismissRunnable;
		}

		public BottomSheet setUseFullWidth(boolean value) {
			bottomSheet.fullWidth = value;
			return bottomSheet;
		}

		public BottomSheet setUseFullscreen(boolean value) {
			bottomSheet.isFullscreen = value;
			return bottomSheet;
		}

		public Builder setOnPreDismissListener(OnDismissListener onDismissListener) {
			bottomSheet.setOnHideListener(onDismissListener);
			return this;
		}
	}

	protected class ContainerView extends FrameLayout implements NestedScrollingParent {
		private final NestedScrollingParentHelper nestedScrollingParentHelper;
		private final Rect rect = new Rect();
		private final Paint backgroundPaint = new Paint();
		private VelocityTracker velocityTracker = null;
		private int startedTrackingX;
		private int startedTrackingY;
		private int startedTrackingPointerId = -1;
		private boolean maybeStartTracking = false;
		private boolean startedTracking = false;
		private AnimatorSet currentAnimation = null;
		private int keyboardHeight;
		private boolean keyboardChanged;
		private float y = 0f;

		public ContainerView(Context context) {
			super(context);
			nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
			setWillNotDraw(false);
		}

		@Override
		public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int nestedScrollAxes) {
			return !(nestedScrollChild != null && child != nestedScrollChild) && !dismissed && allowNestedScroll && nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL && !canDismissWithSwipe();
		}

		@Override
		public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int nestedScrollAxes) {
			nestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
			if (dismissed || !allowNestedScroll) {
				return;
			}
			cancelCurrentAnimation();
		}

		@Override
		public void onStopNestedScroll(@NonNull View target) {
			nestedScrollingParentHelper.onStopNestedScroll(target);

			if (dismissed || !allowNestedScroll) {
				return;
			}

			checkDismiss(0, 0);
		}

		@Override
		public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
			if (dismissed || !allowNestedScroll) {
				return;
			}
			cancelCurrentAnimation();
			if (dyUnconsumed != 0) {
				float currentTranslation = containerView.getTranslationY();
				currentTranslation -= dyUnconsumed;
				if (currentTranslation < 0) {
					currentTranslation = 0;
				}
				containerView.setTranslationY(currentTranslation);
				container.invalidate();
			}
		}

		@Override
		public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) {
			if (dismissed || !allowNestedScroll) {
				return;
			}
			cancelCurrentAnimation();
			float currentTranslation = containerView.getTranslationY();
			if (currentTranslation > 0 && dy > 0) {
				currentTranslation -= dy;
				consumed[1] = dy;
				if (currentTranslation < 0) {
					currentTranslation = 0;
				}
				containerView.setTranslationY(currentTranslation);
				container.invalidate();
			}
		}

		@Override
		public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
			return false;
		}

		@Override
		public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
			return false;
		}

		@Override
		public int getNestedScrollAxes() {
			return nestedScrollingParentHelper.getNestedScrollAxes();
		}

		private void checkDismiss(float velX, float velY) {
			float translationY = containerView.getTranslationY();
			boolean backAnimation = translationY < AndroidUtilities.getPixelsInCM(0.8f, false) && (velY < 3500 || Math.abs(velY) < Math.abs(velX)) || velY < 0 && Math.abs(velY) >= 3500;
			if (!backAnimation) {
				boolean allowOld = allowCustomAnimation;
				allowCustomAnimation = false;
				dismiss();
				allowCustomAnimation = allowOld;
			}
			else {
				currentAnimation = new AnimatorSet();
				ValueAnimator invalidateContainer = ValueAnimator.ofFloat(0, 1);
				invalidateContainer.addUpdateListener(a -> {
					if (container != null) {
						container.invalidate();
					}
				});
				currentAnimation.playTogether(ObjectAnimator.ofFloat(containerView, "translationY", 0), invalidateContainer);
				currentAnimation.setDuration((int)(250 * (Math.max(0, translationY) / AndroidUtilities.getPixelsInCM(0.8f, false))));
				currentAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
				currentAnimation.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						if (currentAnimation != null && currentAnimation.equals(animation)) {
							currentAnimation = null;
						}
						NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
					}
				});
				NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
				currentAnimation.start();
			}
		}

		private void cancelCurrentAnimation() {
			if (currentAnimation != null) {
				currentAnimation.cancel();
				currentAnimation = null;
			}
		}

		public boolean processTouchEvent(MotionEvent ev, boolean intercept) {
			if (dismissed || !canDismissWithSwipe) {
				return false;
			}
			if (onContainerTouchEvent(ev)) {
				return true;
			}

			if (canDismissWithTouchOutside() && ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) && (!startedTracking && !maybeStartTracking && ev.getPointerCount() == 1)) {
				startedTrackingX = (int)ev.getX();
				startedTrackingY = (int)ev.getY();
				if (startedTrackingY < containerView.getTop() || startedTrackingX < containerView.getLeft() || startedTrackingX > containerView.getRight()) {
					onDismissWithTouchOutside();
					return true;
				}
				onScrollUpBegin(y);
				startedTrackingPointerId = ev.getPointerId(0);
				maybeStartTracking = true;
				cancelCurrentAnimation();
				if (velocityTracker != null) {
					velocityTracker.clear();
				}
			}
			else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain();
				}
				float dx = Math.abs((int)(ev.getX() - startedTrackingX));
				float dy = (int)ev.getY() - startedTrackingY;
				boolean canScrollUp = onScrollUp(y + dy);
				velocityTracker.addMovement(ev);
				if (!disableScroll && maybeStartTracking && !startedTracking && (dy > 0 && dy / 3.0f > Math.abs(dx) && Math.abs(dy) >= touchSlop)) {
					startedTrackingY = (int)ev.getY();
					maybeStartTracking = false;
					startedTracking = true;
					requestDisallowInterceptTouchEvent(true);
				}
				else if (startedTracking) {
					y += dy;
					if (!canScrollUp) {
						y = Math.max(y, 0);
					}
					containerView.setTranslationY(Math.max(y, 0));
					startedTrackingY = (int)ev.getY();
					container.invalidate();
				}
			}
			else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain();
				}
				velocityTracker.computeCurrentVelocity(1000);
				onScrollUpEnd(y);
				if (startedTracking || y > 0) {
					checkDismiss(velocityTracker.getXVelocity(), velocityTracker.getYVelocity());
				}
				else {
					maybeStartTracking = false;
				}
				startedTracking = false;
				if (velocityTracker != null) {
					velocityTracker.recycle();
					velocityTracker = null;
				}
				startedTrackingPointerId = -1;
			}
			return !intercept && maybeStartTracking || startedTracking || !canDismissWithSwipe();
		}

		@Override
		public boolean onTouchEvent(MotionEvent ev) {
			return processTouchEvent(ev, false);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int width = MeasureSpec.getSize(widthMeasureSpec);
			int height = MeasureSpec.getSize(heightMeasureSpec);
			int containerHeight = height;
			View rootView = getRootView();
			getWindowVisibleDisplayFrame(rect);
			int oldKeyboardHeight = keyboardHeight;
			if (rect.bottom != 0 && rect.top != 0) {
				int usableViewHeight = (int)(rootView.getHeight() - (rect.top != 0 ? AndroidUtilities.statusBarHeight * (1f - hideSystemVerticalInsetsProgress) : 0) - AndroidUtilities.getViewInset(rootView) * (1f - hideSystemVerticalInsetsProgress));
				keyboardHeight = Math.max(0, usableViewHeight - (rect.bottom - rect.top));
				if (keyboardHeight < AndroidUtilities.dp(20)) {
					keyboardHeight = 0;
				}
				bottomInset -= keyboardHeight;
			}
			else {
				keyboardHeight = 0;
			}
			if (oldKeyboardHeight != keyboardHeight) {
				keyboardChanged = true;
			}
			keyboardVisible = keyboardHeight > AndroidUtilities.dp(20);
			if (lastInsets != null) {
				bottomInset = lastInsets.getSystemWindowInsetBottom();
				leftInset = lastInsets.getSystemWindowInsetLeft();
				rightInset = lastInsets.getSystemWindowInsetRight();
				if (Build.VERSION.SDK_INT >= 29) {
					bottomInset += getAdditionalMandatoryOffsets();
				}
				if (keyboardVisible && rect.bottom != 0 && rect.top != 0) {
					bottomInset -= keyboardHeight;
				}
				if (!drawNavigationBar) {
					containerHeight -= getBottomInset();
				}
			}
			setMeasuredDimension(width, containerHeight);
			if (lastInsets != null) {
				int inset = (int)(lastInsets.getSystemWindowInsetBottom() * (1f - hideSystemVerticalInsetsProgress));
				if (Build.VERSION.SDK_INT >= 29) {
					inset += getAdditionalMandatoryOffsets();
				}
				height -= inset;
			}
			if (lastInsets != null) {
				width -= getRightInset() + getLeftInset();
			}
			isPortrait = width < height;

			if (containerView != null) {
				if (!fullWidth) {
					int widthSpec;
					if (AndroidUtilities.isTablet()) {
						widthSpec = MeasureSpec.makeMeasureSpec((int)(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f) + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY);
					}
					else {
						widthSpec = MeasureSpec.makeMeasureSpec((getBottomSheetWidth(isPortrait, width)) + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY);
					}
					containerView.measure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
				}
				else {
					containerView.measure(MeasureSpec.makeMeasureSpec(width + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
				}
			}
			int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				View child = getChildAt(i);
				if (child.getVisibility() == GONE || child == containerView) {
					continue;
				}
				if (!onCustomMeasure(child, width, height)) {
					measureChildWithMargins(child, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY), 0);
				}
			}
		}

		@Override
		public void requestLayout() {
			super.requestLayout();
		}

		@Override
		protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
			layoutCount--;
			if (containerView != null) {
				int t = (bottom - top) - containerView.getMeasuredHeight();
				if (lastInsets != null) {
					left += getLeftInset();
					right -= getRightInset();
					if (useSmoothKeyboard) {
						t = 0;
					}
					else {
						t -= lastInsets.getSystemWindowInsetBottom() * (1f - hideSystemVerticalInsetsProgress) - (drawNavigationBar ? 0 : getBottomInset());
						if (Build.VERSION.SDK_INT >= 29) {
							t -= getAdditionalMandatoryOffsets();
						}
					}
				}
				int l = ((right - left) - containerView.getMeasuredWidth()) / 2;
				if (lastInsets != null) {
					l += getLeftInset();
				}
				if (smoothKeyboardAnimationEnabled && startAnimationRunnable == null && keyboardChanged && !dismissed && containerView.getTop() != t) {
					containerView.setTranslationY(containerView.getTop() - t);
					if (keyboardContentAnimator != null) {
						keyboardContentAnimator.cancel();
					}
					keyboardContentAnimator = ValueAnimator.ofFloat(containerView.getTranslationY(), 0);
					keyboardContentAnimator.addUpdateListener(valueAnimator -> {
						containerView.setTranslationY((Float)valueAnimator.getAnimatedValue());
						invalidate();
					});
					keyboardContentAnimator.addListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							containerView.setTranslationY(0);
							invalidate();
						}
					});
					keyboardContentAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
					keyboardContentAnimator.start();
				}
				containerView.layout(l, t, l + containerView.getMeasuredWidth(), t + containerView.getMeasuredHeight());
			}

			final int count = getChildCount();
			for (int i = 0; i < count; i++) {
				final View child = getChildAt(i);
				if (child.getVisibility() == GONE || child == containerView) {
					continue;
				}
				if (!onCustomLayout(child, left, top, right, bottom - (drawNavigationBar ? getBottomInset() : 0))) {
					final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)child.getLayoutParams();

					final int width = child.getMeasuredWidth();
					final int height = child.getMeasuredHeight();

					int childLeft;
					int childTop;

					int gravity = lp.gravity;
					if (gravity == -1) {
						gravity = Gravity.TOP | Gravity.LEFT;
					}

					final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
					final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

					switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
						case Gravity.CENTER_HORIZONTAL:
							childLeft = (right - left - width) / 2 + lp.leftMargin - lp.rightMargin;
							break;
						case Gravity.RIGHT:
							childLeft = right - width - lp.rightMargin;
							break;
						case Gravity.LEFT:
						default:
							childLeft = lp.leftMargin;
					}

					switch (verticalGravity) {
						case Gravity.CENTER_VERTICAL:
							childTop = (bottom - top - height) / 2 + lp.topMargin - lp.bottomMargin;
							break;
						case Gravity.BOTTOM:
							childTop = (bottom - top) - height - lp.bottomMargin;
							break;
						default:
							childTop = lp.topMargin;
					}
					if (lastInsets != null) {
						childLeft += getLeftInset();
					}
					child.layout(childLeft, childTop, childLeft + width, childTop + height);
				}
			}
			if (layoutCount == 0 && startAnimationRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(startAnimationRunnable);
				startAnimationRunnable.run();
				startAnimationRunnable = null;
			}
			keyboardChanged = false;
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
			if (canDismissWithSwipe()) {
				return processTouchEvent(event, true);
			}
			return super.onInterceptTouchEvent(event);
		}

		@Override
		public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
			if (maybeStartTracking && !startedTracking) {
				onTouchEvent(null);
			}
			super.requestDisallowInterceptTouchEvent(disallowIntercept);
		}

		@Override
		public boolean hasOverlappingRendering() {
			return false;
		}

		@Override
		protected void dispatchDraw(Canvas canvas) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				backgroundPaint.setColor(navBarColor);
			}
			else {
				backgroundPaint.setColor(0xff000000);
			}
			if (drawDoubleNavigationBar && !shouldOverlayCameraViewOverNavBar()) {
				drawNavigationBar(canvas, 1f);
			}
			if (backgroundPaint.getAlpha() < 255 && drawNavigationBar) {
				float translation = 0;
				if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && getAdditionalMandatoryOffsets() > 0) {
					float dist = containerView.getMeasuredHeight() - containerView.getTranslationY();
					translation = Math.max(0, getBottomInset() - dist);
				}
				int navBarHeight = drawNavigationBar ? getBottomInset() : 0;
				canvas.save();
				canvas.clipRect(containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY, containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() + translation, Region.Op.DIFFERENCE);
				super.dispatchDraw(canvas);
				canvas.restore();
			}
			else {
				super.dispatchDraw(canvas);
			}
			if (!shouldOverlayCameraViewOverNavBar()) {
				drawNavigationBar(canvas, (drawDoubleNavigationBar ? 0.7f * navigationBarAlpha : 1f));
			}
			if (drawNavigationBar && rightInset != 0 && rightInset > leftInset && fullWidth && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				canvas.drawRect(containerView.getRight() - backgroundPaddingLeft, containerView.getTranslationY(), containerView.getRight() + rightInset, getMeasuredHeight(), backgroundPaint);
			}

			if (drawNavigationBar && leftInset != 0 && leftInset > rightInset && fullWidth && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				canvas.drawRect(0, containerView.getTranslationY(), containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight(), backgroundPaint);
			}

			if (containerView.getTranslationY() < 0) {
				backgroundPaint.setColor(behindKeyboardColor);
				canvas.drawRect(containerView.getLeft() + backgroundPaddingLeft, containerView.getY() + containerView.getMeasuredHeight(), containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight(), backgroundPaint);
			}
		}

		@Override
		protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
			if (child instanceof CameraView) {
				if (shouldOverlayCameraViewOverNavBar()) {
					drawNavigationBar(canvas, 1f);
				}
				return super.drawChild(canvas, child, drawingTime);
			}
			return super.drawChild(canvas, child, drawingTime);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			boolean restore = false;
			if (backgroundPaint.getAlpha() < 255 && drawNavigationBar) {
				float translation = 0;
				if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && getAdditionalMandatoryOffsets() > 0) {
					float dist = containerView.getMeasuredHeight() - containerView.getTranslationY();
					translation = Math.max(0, getBottomInset() - dist);
				}
				int navBarHeight = drawNavigationBar ? getBottomInset() : 0;
				canvas.save();
				canvas.clipRect(containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY, containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() + translation, Region.Op.DIFFERENCE);
				restore = true;
			}
			super.onDraw(canvas);
			if (lastInsets != null && keyboardHeight != 0) {
				backgroundPaint.setColor(behindKeyboardColor);
				canvas.drawRect(containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight() - keyboardHeight - (drawNavigationBar ? getBottomInset() : 0), containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() - (drawNavigationBar ? getBottomInset() : 0), backgroundPaint);
			}
			onContainerDraw(canvas);
			if (restore) {
				canvas.restore();
			}
		}

		public void drawNavigationBar(Canvas canvas, float alpha) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				backgroundPaint.setColor(navBarColor);
			}
			else {
				backgroundPaint.setColor(0xff000000);
			}
			if ((drawNavigationBar && bottomInset != 0) || currentPanTranslationY != 0) {
				float translation = 0;
				int navBarHeight = drawNavigationBar ? getBottomInset() : 0;
				if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && getAdditionalMandatoryOffsets() > 0) {
					if (drawDoubleNavigationBar) {
						translation = Math.max(0, Math.min(navBarHeight - currentPanTranslationY, containerView.getTranslationY()));
					}
					else {
						float dist = containerView.getMeasuredHeight() - containerView.getTranslationY();
						translation = Math.max(0, getBottomInset() - dist);
					}
				}
				int wasAlpha = backgroundPaint.getAlpha();
				if (alpha < 1f) {
					backgroundPaint.setAlpha((int)(wasAlpha * alpha));
				}
				canvas.drawRect(containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY, containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() + translation, backgroundPaint);
				backgroundPaint.setAlpha(wasAlpha);

				if (overlayDrawNavBarColor != 0) {
					backgroundPaint.setColor(overlayDrawNavBarColor);
					wasAlpha = backgroundPaint.getAlpha();
					if (alpha < 1f) {
						backgroundPaint.setAlpha((int)(wasAlpha * alpha));
						translation = 0;
					}
					canvas.drawRect(containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY, containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() + translation, backgroundPaint);
					backgroundPaint.setAlpha(wasAlpha);
				}
			}
		}
	}
}
