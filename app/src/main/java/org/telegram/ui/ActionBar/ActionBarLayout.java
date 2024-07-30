/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.BackButtonMenu;
import org.telegram.ui.Components.BottomNavigationPanel;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

public class ActionBarLayout extends FrameLayout {
	private static Drawable headerShadowDrawable;
	private static Drawable layerShadowDrawable;
	private static Paint scrimPaint;
	private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
	private final OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.02f);
	private final AccelerateDecelerateInterpolator accelerateDecelerateInterpolator = new AccelerateDecelerateInterpolator();
	private final Rect rect = new Rect();
	public boolean highlightActionButtons = false;
	public LayoutContainer containerView; /* Contest: private -> public temp hack bc I don't know how to blur action bar otherwise */
	public float innerTranslationX;
	public ArrayList<BaseFragment> fragmentsStack;
	public ArrayList<BackButtonMenu.PulledDialog> pulledDialogs;
	protected boolean startedTracking;
	protected boolean animationInProgress;
	protected Activity parentActivity;
	private Runnable waitingForKeyboardCloseRunnable;
	private Runnable delayedOpenAnimationRunnable;
	private boolean inBubbleMode;
	private boolean inPreviewMode;
	private boolean previewOpenAnimationInProgress;
	private ColorDrawable previewBackgroundDrawable;
	private LayoutContainer containerViewBack;
	private DrawerLayoutContainer drawerLayoutContainer;
	private ActionBar currentActionBar;
	private BaseFragment newFragment;
	private BaseFragment oldFragment;
	/* Contest */
	private ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu;
	private AnimatorSet currentAnimation;
	private boolean maybeStartTracking;
	private int startedTrackingX;
	private int startedTrackingY;
	private VelocityTracker velocityTracker;
	private boolean beginTrackingSent;
	private boolean transitionAnimationInProgress;
	private boolean transitionAnimationPreviewMode;
	private boolean rebuildAfterAnimation;
	private boolean rebuildLastAfterAnimation;
	private boolean showLastAfterAnimation;
	private long transitionAnimationStartTime;
	private boolean inActionMode;
	private int startedTrackingPointerId;
	private Runnable onCloseAnimationEndRunnable;
	private Runnable onOpenAnimationEndRunnable;
	private boolean useAlphaAnimations;
	private View backgroundView;
	private boolean removeActionBarExtraHeight;
	private Runnable animationRunnable;
	private float animationProgress;
	private long lastFrameTime;
	private String titleOverlayText;
	private int titleOverlayTextId;
	private Runnable overlayAction;
	private ActionBarLayoutDelegate delegate;
	private boolean delayedAnimationResumed;
	private Runnable onFragmentStackChangedListener;

	@Nullable
	private BottomNavigationPanel bottomNavigationPanel;

	public ActionBarLayout(Context context) {
		super(context);

		parentActivity = (Activity)context;

		if (layerShadowDrawable == null) {
			layerShadowDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.layer_shadow, null);
			headerShadowDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.header_shadow, null).mutate();
			scrimPaint = new Paint();
		}
	}

	public void init(ArrayList<BaseFragment> stack) {
		this.fragmentsStack = stack;
		this.containerViewBack = new LayoutContainer(parentActivity);
		addView(containerViewBack);
		FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)containerViewBack.getLayoutParams();
		layoutParams.width = LayoutHelper.MATCH_PARENT;
		layoutParams.height = LayoutHelper.MATCH_PARENT;
		layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
		containerViewBack.setLayoutParams(layoutParams);

		containerView = new LayoutContainer(parentActivity);
		addView(containerView);
		layoutParams = (FrameLayout.LayoutParams)containerView.getLayoutParams();
		layoutParams.width = LayoutHelper.MATCH_PARENT;
		layoutParams.height = LayoutHelper.MATCH_PARENT;
		layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
		containerView.setLayoutParams(layoutParams);

		for (BaseFragment fragment : fragmentsStack) {
			fragment.setParentLayout(this);
		}
	}

	@Override
	public void onConfigurationChanged(android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (!fragmentsStack.isEmpty()) {
			for (int a = 0, N = fragmentsStack.size(); a < N; a++) {
				BaseFragment fragment = fragmentsStack.get(a);
				fragment.onConfigurationChanged(newConfig);
				if (fragment.visibleDialog instanceof BottomSheet) {
					((BottomSheet)fragment.visibleDialog).onConfigurationChanged(newConfig);
				}
			}
		}
	}

	public int getBottomNavigationPanelHeight() {
		return bottomNavigationPanel != null ? bottomNavigationPanel.getMeasuredHeight() : 0;
	}

	public void setBottomNavigationPanel(@Nullable BottomNavigationPanel panel) {
		bottomNavigationPanel = panel;
	}

	public void drawHeaderShadow(Canvas canvas, int y) {
		drawHeaderShadow(canvas, 255, y);
	}

	public boolean isInBubbleMode() {
		return inBubbleMode;
	}

	public void setInBubbleMode(boolean value) {
		inBubbleMode = value;
	}

	public void drawHeaderShadow(Canvas canvas, int alpha, int y) {
		if (headerShadowDrawable != null) {
			if (headerShadowDrawable.getAlpha() != alpha) {
				headerShadowDrawable.setAlpha(alpha);
			}

			headerShadowDrawable.setBounds(0, y, getMeasuredWidth(), y + headerShadowDrawable.getIntrinsicHeight());
			headerShadowDrawable.draw(canvas);
		}
	}

	@Keep
	public float getInnerTranslationX() {
		return innerTranslationX;
	}

	@Keep
	public void setInnerTranslationX(float value) {
		innerTranslationX = value;
		invalidate();

		if (fragmentsStack.size() >= 2 && containerView.getMeasuredWidth() > 0) {
			float progress = value / containerView.getMeasuredWidth();
			BaseFragment prevFragment = fragmentsStack.get(fragmentsStack.size() - 2);
			prevFragment.onSlideProgress(false, progress);
			BaseFragment currFragment = fragmentsStack.get(fragmentsStack.size() - 1);
			float ratio = MathUtils.clamp(2f * progress, 0f, 1f);

			if (currFragment.isBeginToShow()) {
				int currNavigationBarColor = currFragment.getNavigationBarColor();
				int prevNavigationBarColor = prevFragment.getNavigationBarColor();

				if (currNavigationBarColor != prevNavigationBarColor) {
					currFragment.setNavigationBarColor(ColorUtils.blendARGB(currNavigationBarColor, prevNavigationBarColor, ratio));
				}
			}

			if (!currFragment.inPreviewMode && !SharedConfig.noStatusBar) {
				int overlayColor = AndroidUtilities.isDarkTheme() ? AndroidUtilities.DARK_STATUS_BAR_OVERLAY : AndroidUtilities.LIGHT_STATUS_BAR_OVERLAY;
				int oldStatusBarColor = prevFragment.hasForceLightStatusBar() ? Color.TRANSPARENT : overlayColor;
				int newStatusBarColor = currFragment.hasForceLightStatusBar() ? Color.TRANSPARENT : overlayColor;
				parentActivity.getWindow().setStatusBarColor(ColorUtils.blendARGB(newStatusBarColor, oldStatusBarColor, ratio));
			}
		}
	}

	public void dismissDialogs() {
		if (!fragmentsStack.isEmpty()) {
			BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
			lastFragment.dismissCurrentDialog();
		}
	}

	public void onResume() {
		if (transitionAnimationInProgress) {
			if (currentAnimation != null) {
				currentAnimation.cancel();
				currentAnimation = null;
			}
			if (animationRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(animationRunnable);
				animationRunnable = null;
			}
			if (waitingForKeyboardCloseRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(waitingForKeyboardCloseRunnable);
				waitingForKeyboardCloseRunnable = null;
			}
			if (onCloseAnimationEndRunnable != null) {
				onCloseAnimationEnd();
			}
			else if (onOpenAnimationEndRunnable != null) {
				onOpenAnimationEnd();
			}
		}
		if (!fragmentsStack.isEmpty()) {
			BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
			lastFragment.onResume();
		}
	}

	public void onUserLeaveHint() {
		if (!fragmentsStack.isEmpty()) {
			BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
			lastFragment.onUserLeaveHint();
		}
	}

	public void onPause() {
		if (!fragmentsStack.isEmpty()) {
			BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
			lastFragment.onPause();
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return animationInProgress || checkTransitionAnimation() || onTouchEvent(ev);
	}

	@Override
	public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
		onTouchEvent(null);
		super.requestDisallowInterceptTouchEvent(disallowIntercept);
	}

	@Override
	public boolean dispatchKeyEventPreIme(KeyEvent event) {
		if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
			return delegate != null && delegate.onPreIme() || super.dispatchKeyEventPreIme(event);
		}
		return super.dispatchKeyEventPreIme(event);
	}

	@Override
	protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
		if (drawerLayoutContainer != null && drawerLayoutContainer.isDrawCurrentPreviewFragmentAbove()) {
			if (inPreviewMode || transitionAnimationPreviewMode || previewOpenAnimationInProgress) {
				if (child == (oldFragment != null && oldFragment.inPreviewMode ? containerViewBack : containerView)) {
					drawerLayoutContainer.invalidate();
					return false;
				}
			}
		}

		int width = getWidth() - getPaddingLeft() - getPaddingRight();
		int translationX = (int)innerTranslationX + getPaddingRight();
		int clipLeft = getPaddingLeft();
		int clipRight = width + getPaddingLeft();

		if (child == containerViewBack) {
			clipRight = translationX + AndroidUtilities.dp(1);
		}
		else if (child == containerView) {
			clipLeft = translationX;
		}

		final int restoreCount = canvas.save();
		if (!isTransitionAnimationInProgress() && !inPreviewMode) {
			canvas.clipRect(clipLeft, 0, clipRight, getHeight());
		}
		if ((inPreviewMode || transitionAnimationPreviewMode) && child == containerView) {
			drawPreviewDrawables(canvas, containerView);
		}
		final boolean result = super.drawChild(canvas, child, drawingTime);
		canvas.restoreToCount(restoreCount);

		if (translationX != 0) {
			int widthOffset = width - translationX;
			if (child == containerView) {
				float alpha = MathUtils.clamp(widthOffset / (float)AndroidUtilities.dp(20), 0, 1f);
				layerShadowDrawable.setBounds(translationX - layerShadowDrawable.getIntrinsicWidth(), child.getTop(), translationX, child.getBottom());
				layerShadowDrawable.setAlpha((int)(0xff * alpha));
				layerShadowDrawable.draw(canvas);
			}
			else if (child == containerViewBack) {
				float opacity = MathUtils.clamp(widthOffset / (float)width, 0, 0.8f);
				scrimPaint.setColor(Color.argb((int)(0x99 * opacity), 0x00, 0x00, 0x00));
				canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
			}
		}

		return result;
	}

	public float getCurrentPreviewFragmentAlpha() {
		if (inPreviewMode || transitionAnimationPreviewMode || previewOpenAnimationInProgress) {
			return (oldFragment != null && oldFragment.inPreviewMode ? containerViewBack : containerView).getAlpha();
		}
		else {
			return 0f;
		}
	}

	public void drawCurrentPreviewFragment(Canvas canvas, Drawable foregroundDrawable) {
		if (inPreviewMode || transitionAnimationPreviewMode || previewOpenAnimationInProgress) {
			final ViewGroup v = oldFragment != null && oldFragment.inPreviewMode ? containerViewBack : containerView;
			drawPreviewDrawables(canvas, v);
			if (v.getAlpha() < 1f) {
				canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int)(v.getAlpha() * 255), Canvas.ALL_SAVE_FLAG);
			}
			else {
				canvas.save();
			}
			canvas.concat(v.getMatrix());
			v.draw(canvas);
			if (foregroundDrawable != null) {
				final View child = v.getChildAt(0);
				if (child != null) {
					final MarginLayoutParams lp = (MarginLayoutParams)child.getLayoutParams();
					final Rect rect = new Rect();
					child.getLocalVisibleRect(rect);
					rect.offset(lp.leftMargin, lp.topMargin);
					rect.top += AndroidUtilities.statusBarHeight - 1;
					foregroundDrawable.setAlpha((int)(v.getAlpha() * 255));
					foregroundDrawable.setBounds(rect);
					foregroundDrawable.draw(canvas);
				}
			}
			canvas.restore();
		}
	}

	private void drawPreviewDrawables(Canvas canvas, ViewGroup containerView) {
		View view = containerView.getChildAt(0);
		if (view != null) {
			previewBackgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
			previewBackgroundDrawable.draw(canvas);
			if (previewMenu == null) {
				int width = AndroidUtilities.dp(32), height = width / 2;
				int x = (getMeasuredWidth() - width) / 2;
				int y = (int)(view.getTop() + containerView.getTranslationY() - AndroidUtilities.dp(12));
				Theme.moveUpDrawable.setBounds(x, y, x + width, y + height);
				Theme.moveUpDrawable.draw(canvas);
			}
		}
	}

	public void setDelegate(ActionBarLayoutDelegate actionBarLayoutDelegate) {
		delegate = actionBarLayoutDelegate;
	}

	private void onSlideAnimationEnd(final boolean backAnimation) {
		if (!backAnimation) {
			if (fragmentsStack.size() < 2) {
				return;
			}
			BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
			lastFragment.prepareFragmentToSlide(true, false);
			lastFragment.onPause();
			lastFragment.onFragmentDestroy();
			lastFragment.setParentLayout(null);

			fragmentsStack.remove(fragmentsStack.size() - 1);
			onFragmentStackChanged();

			LayoutContainer temp = containerView;
			containerView = containerViewBack;
			containerViewBack = temp;
			bringChildToFront(containerView);

			lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
			currentActionBar = lastFragment.actionBar;
			lastFragment.onResume();
			lastFragment.onBecomeFullyVisible();
			lastFragment.prepareFragmentToSlide(false, false);
		}
		else {
			if (fragmentsStack.size() >= 2) {
				BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
				lastFragment.prepareFragmentToSlide(true, false);

				lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
				lastFragment.prepareFragmentToSlide(false, false);
				lastFragment.onPause();
				if (lastFragment.fragmentView != null) {
					ViewGroup parent = (ViewGroup)lastFragment.fragmentView.getParent();
					if (parent != null) {
						lastFragment.onRemoveFromParent();
						parent.removeViewInLayout(lastFragment.fragmentView);
					}
				}
				if (lastFragment.actionBar != null && lastFragment.actionBar.shouldAddToContainer()) {
					ViewGroup parent = (ViewGroup)lastFragment.actionBar.getParent();
					if (parent != null) {
						parent.removeViewInLayout(lastFragment.actionBar);
					}
				}
			}
		}
		containerViewBack.setVisibility(View.INVISIBLE);
		startedTracking = false;
		animationInProgress = false;
		containerView.setTranslationX(0);
		containerViewBack.setTranslationX(0);
		setInnerTranslationX(0);
	}

	private void prepareForMoving(MotionEvent ev) {
		maybeStartTracking = false;
		startedTracking = true;
		startedTrackingX = (int)ev.getX();
		containerViewBack.setVisibility(View.VISIBLE);
		beginTrackingSent = false;

		BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
		View fragmentView = lastFragment.fragmentView;

		if (fragmentView == null) {
			fragmentView = lastFragment.createView(parentActivity);
		}

		ViewGroup parent = (ViewGroup)fragmentView.getParent();

		if (parent != null) {
			lastFragment.onRemoveFromParent();
			parent.removeView(fragmentView);
		}

		containerViewBack.addView(fragmentView);

		FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)fragmentView.getLayoutParams();
		layoutParams.width = LayoutHelper.MATCH_PARENT;
		layoutParams.height = LayoutHelper.MATCH_PARENT;
		layoutParams.topMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;

		if (bottomNavigationPanel != null && lastFragment.shouldShowBottomNavigationPanel()) {
			layoutParams.bottomMargin = AndroidUtilities.dp(BottomNavigationPanel.height);

			ViewGroup panelParent = (ViewGroup)bottomNavigationPanel.getParent();

			if (panelParent != null) {
				panelParent.removeView(bottomNavigationPanel);
			}

			containerViewBack.addView(bottomNavigationPanel, BottomNavigationPanel.createLayoutParams());
		}
		else {
			if (bottomNavigationPanel != null && bottomNavigationPanel.getParent() == containerViewBack) {
				containerViewBack.removeView(bottomNavigationPanel);
			}

			layoutParams.bottomMargin = 0;
		}

		fragmentView.setLayoutParams(layoutParams);

		if (lastFragment.actionBar != null && lastFragment.actionBar.shouldAddToContainer()) {
			parent = (ViewGroup)lastFragment.actionBar.getParent();

			if (parent != null) {
				parent.removeView(lastFragment.actionBar);
			}

			if (removeActionBarExtraHeight) {
				lastFragment.actionBar.setOccupyStatusBar(false);
			}

			containerViewBack.addView(lastFragment.actionBar);

			lastFragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, overlayAction);
		}

		if (!lastFragment.hasOwnBackground && fragmentView.getBackground() == null) {
			fragmentView.setBackgroundResource(R.color.background);
		}

		lastFragment.onResume();

		BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
		currentFragment.prepareFragmentToSlide(true, true);
		lastFragment.prepareFragmentToSlide(false, true);
	}

	@SuppressLint("ClickableViewAccessibility")
	public boolean onTouchEvent(MotionEvent ev) {
		if (!checkTransitionAnimation() && !inActionMode && !animationInProgress) {
			if (fragmentsStack.size() > 1) {
				if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
					BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
					if (!currentFragment.isSwipeBackEnabled(ev)) {
						maybeStartTracking = false;
						startedTracking = false;
						return false;
					}
					startedTrackingPointerId = ev.getPointerId(0);
					maybeStartTracking = true;
					startedTrackingX = (int)ev.getX();
					startedTrackingY = (int)ev.getY();
					if (velocityTracker != null) {
						velocityTracker.clear();
					}
				}
				else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
					if (velocityTracker == null) {
						velocityTracker = VelocityTracker.obtain();
					}
					int dx = Math.max(0, (int)(ev.getX() - startedTrackingX));
					int dy = Math.abs((int)ev.getY() - startedTrackingY);
					velocityTracker.addMovement(ev);
					if (!transitionAnimationInProgress && !inPreviewMode && maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
						BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
						if (currentFragment.canBeginSlide() && findScrollingChild(this, ev.getX(), ev.getY()) == null) {
							prepareForMoving(ev);
						}
						else {
							maybeStartTracking = false;
						}
					}
					else if (startedTracking) {
						if (!beginTrackingSent) {
							if (parentActivity.getCurrentFocus() != null) {
								AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
							}
							BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
							currentFragment.onBeginSlide();
							beginTrackingSent = true;
						}
						containerView.setTranslationX(dx);
						setInnerTranslationX(dx);
					}
				}
				else if (ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
					if (velocityTracker == null) {
						velocityTracker = VelocityTracker.obtain();
					}
					velocityTracker.computeCurrentVelocity(1000);
					BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
					if (!inPreviewMode && !transitionAnimationPreviewMode && !startedTracking && currentFragment.isSwipeBackEnabled(ev)) {
						float velX = velocityTracker.getXVelocity();
						float velY = velocityTracker.getYVelocity();
						if (velX >= 3500 && velX > Math.abs(velY) && currentFragment.canBeginSlide()) {
							prepareForMoving(ev);
							if (!beginTrackingSent) {
								if (((Activity)getContext()).getCurrentFocus() != null) {
									AndroidUtilities.hideKeyboard(((Activity)getContext()).getCurrentFocus());
								}
								beginTrackingSent = true;
							}
						}
					}
					if (startedTracking) {
						float x = containerView.getX();
						AnimatorSet animatorSet = new AnimatorSet();
						float velX = velocityTracker.getXVelocity();
						float velY = velocityTracker.getYVelocity();
						final boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
						float distToMove;
						boolean overrideTransition = currentFragment.shouldOverrideSlideTransition(false, backAnimation);

						if (!backAnimation) {
							distToMove = containerView.getMeasuredWidth() - x;
							int duration = Math.max((int)(200.0f / containerView.getMeasuredWidth() * distToMove), 50);
							if (!overrideTransition) {
								animatorSet.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, containerView.getMeasuredWidth()).setDuration(duration), ObjectAnimator.ofFloat(this, "innerTranslationX", (float)containerView.getMeasuredWidth()).setDuration(duration));
							}
						}
						else {
							distToMove = x;
							int duration = Math.max((int)(200.0f / containerView.getMeasuredWidth() * distToMove), 50);
							if (!overrideTransition) {
								animatorSet.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0).setDuration(duration), ObjectAnimator.ofFloat(this, "innerTranslationX", 0.0f).setDuration(duration));
							}
						}

						Animator customTransition = currentFragment.getCustomSlideTransition(false, backAnimation, distToMove);
						if (customTransition != null) {
							animatorSet.playTogether(customTransition);
						}

						BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
						if (lastFragment != null) {
							customTransition = lastFragment.getCustomSlideTransition(false, backAnimation, distToMove);
							if (customTransition != null) {
								animatorSet.playTogether(customTransition);
							}
						}

						animatorSet.addListener(new AnimatorListenerAdapter() {
							@Override
							public void onAnimationEnd(Animator animator) {
								onSlideAnimationEnd(backAnimation);
							}
						});
						animatorSet.start();
						animationInProgress = true;
					}
					else {
						maybeStartTracking = false;
					}

					if (velocityTracker != null) {
						velocityTracker.recycle();
						velocityTracker = null;
					}
				}
				else if (ev == null) {
					maybeStartTracking = false;
					startedTracking = false;
					if (velocityTracker != null) {
						velocityTracker.recycle();
						velocityTracker = null;
					}
				}
			}
			return startedTracking;
		}
		return false;
	}

	public void onBackPressed() {
		if (transitionAnimationPreviewMode || startedTracking || checkTransitionAnimation() || fragmentsStack.isEmpty()) {
			return;
		}
		if (GroupCallPip.onBackPressed()) {
			return;
		}
		if (currentActionBar != null && !currentActionBar.isActionModeShowed() && currentActionBar.isSearchFieldVisible) {
			currentActionBar.closeSearchField();
			return;
		}
		BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
		if (lastFragment.onBackPressed()) {
			if (!fragmentsStack.isEmpty()) {
				closeLastFragment(true);
			}
		}
	}

	public void onLowMemory() {
		for (BaseFragment fragment : fragmentsStack) {
			fragment.onLowMemory();
		}
	}

	private void onAnimationEndCheck(boolean byCheck) {
		onCloseAnimationEnd();
		onOpenAnimationEnd();
		if (waitingForKeyboardCloseRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(waitingForKeyboardCloseRunnable);
			waitingForKeyboardCloseRunnable = null;
		}
		if (currentAnimation != null) {
			if (byCheck) {
				currentAnimation.cancel();
			}
			currentAnimation = null;
		}
		if (animationRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(animationRunnable);
			animationRunnable = null;
		}
		setAlpha(1.0f);
		containerView.setAlpha(1.0f);
		containerView.setScaleX(1.0f);
		containerView.setScaleY(1.0f);
		containerViewBack.setAlpha(1.0f);
		containerViewBack.setScaleX(1.0f);
		containerViewBack.setScaleY(1.0f);
	}

	public BaseFragment getLastFragment() {
		if (fragmentsStack.isEmpty()) {
			return null;
		}
		return fragmentsStack.get(fragmentsStack.size() - 1);
	}

	public boolean checkTransitionAnimation() {
		if (transitionAnimationPreviewMode) {
			return false;
		}
		if (transitionAnimationInProgress && transitionAnimationStartTime < System.currentTimeMillis() - 1500) {
			onAnimationEndCheck(true);
		}
		return transitionAnimationInProgress;
	}

	public boolean isPreviewOpenAnimationInProgress() {
		return previewOpenAnimationInProgress;
	}

	public boolean isTransitionAnimationInProgress() {
		return transitionAnimationInProgress || animationInProgress;
	}

	private void presentFragmentInternalRemoveOld(boolean removeLast, final BaseFragment fragment) {
		if (fragment == null) {
			return;
		}
		fragment.onBecomeFullyHidden();
		fragment.onPause();
		if (removeLast) {
			fragment.onFragmentDestroy();
			fragment.setParentLayout(null);
			fragmentsStack.remove(fragment);
			onFragmentStackChanged();
		}
		else {
			if (fragment.fragmentView != null) {
				ViewGroup parent = (ViewGroup)fragment.fragmentView.getParent();
				if (parent != null) {
					fragment.onRemoveFromParent();
					try {
						parent.removeViewInLayout(fragment.fragmentView);
					}
					catch (Exception e) {
						FileLog.e(e);
						try {
							parent.removeView(fragment.fragmentView);
						}
						catch (Exception e2) {
							FileLog.e(e2);
						}
					}
				}
			}
			if (fragment.actionBar != null && fragment.actionBar.shouldAddToContainer()) {
				ViewGroup parent = (ViewGroup)fragment.actionBar.getParent();
				if (parent != null) {
					parent.removeViewInLayout(fragment.actionBar);
				}
			}
		}
		containerViewBack.setVisibility(View.INVISIBLE);
	}

	public boolean presentFragmentAsPreview(BaseFragment fragment) {
		return presentFragment(fragment, false, false, true, true, null);
	}

	public boolean presentFragmentAsPreviewWithMenu(BaseFragment fragment, ActionBarPopupWindow.ActionBarPopupWindowLayout menu) {
		return presentFragment(fragment, false, false, true, true, menu);
	}

	public boolean presentFragment(BaseFragment fragment) {
		return presentFragment(fragment, false, false, true, false, null);
	}

	public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
		return presentFragment(fragment, removeLast, false, true, false, null);
	}

	private void startLayoutAnimation(final boolean open, final boolean first, final boolean preview) {
		if (first) {
			animationProgress = 0.0f;
			lastFrameTime = System.nanoTime() / 1000000;
		}
		AndroidUtilities.runOnUIThread(animationRunnable = new Runnable() {
			@Override
			public void run() {
				if (animationRunnable != this) {
					return;
				}
				animationRunnable = null;
				if (first) {
					transitionAnimationStartTime = System.currentTimeMillis();
				}
				long newTime = System.nanoTime() / 1000000;
				long dt = newTime - lastFrameTime;
				if (dt > 18) {
					dt = 18;
				}
				lastFrameTime = newTime;
				float duration = preview && open ? 190.0f : 150.0f;
				animationProgress += dt / duration;
				if (animationProgress > 1.0f) {
					animationProgress = 1.0f;
				}
				if (newFragment != null) {
					newFragment.onTransitionAnimationProgress(true, animationProgress);
				}
				if (oldFragment != null) {
					oldFragment.onTransitionAnimationProgress(false, animationProgress);
				}
				Integer oldNavigationBarColor = oldFragment != null ? oldFragment.getNavigationBarColor() : null;
				Integer newNavigationBarColor = newFragment != null ? newFragment.getNavigationBarColor() : null;
				if (newFragment != null && oldNavigationBarColor != null) {
					float ratio = MathUtils.clamp(2f * animationProgress - (open ? 1f : 0f), 0f, 1f);
					newFragment.setNavigationBarColor(ColorUtils.blendARGB(oldNavigationBarColor, newNavigationBarColor, ratio));
				}
				float interpolated;
				if (preview) {
					if (open) {
						interpolated = overshootInterpolator.getInterpolation(animationProgress);
					}
					else {
						interpolated = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(animationProgress);
					}
				}
				else {
					interpolated = decelerateInterpolator.getInterpolation(animationProgress);
				}
				if (open) {
					float clampedInterpolated = MathUtils.clamp(interpolated, 0, 1);
					containerView.setAlpha(clampedInterpolated);
					if (preview) {
						containerView.setScaleX(0.7f + 0.3f * interpolated);
						containerView.setScaleY(0.7f + 0.3f * interpolated);
						if (previewMenu != null) {
							containerView.setTranslationY(AndroidUtilities.dp(40) * (1f - interpolated));
							previewMenu.setTranslationY(-AndroidUtilities.dp(40 + 30) * (1f - interpolated));
							previewMenu.setScaleX(0.95f + 0.05f * interpolated);
							previewMenu.setScaleY(0.95f + 0.05f * interpolated);
						}
						previewBackgroundDrawable.setAlpha((int)(0x2e * clampedInterpolated));
						Theme.moveUpDrawable.setAlpha((int)(255 * clampedInterpolated));
						containerView.invalidate();
						invalidate();
					}
					else {
						containerView.setTranslationX(AndroidUtilities.dp(48) * (1.0f - interpolated));
					}
				}
				else {
					float clampedReverseInterpolated = MathUtils.clamp(1f - interpolated, 0, 1);
					containerViewBack.setAlpha(clampedReverseInterpolated);
					if (preview) {
						containerViewBack.setScaleX(0.9f + 0.1f * (1.0f - interpolated));
						containerViewBack.setScaleY(0.9f + 0.1f * (1.0f - interpolated));
						previewBackgroundDrawable.setAlpha((int)(0x2e * clampedReverseInterpolated));
						if (previewMenu == null) {
							Theme.moveUpDrawable.setAlpha((int)(255 * clampedReverseInterpolated));
						}
						containerView.invalidate();
						invalidate();
					}
					else {
						containerViewBack.setTranslationX(AndroidUtilities.dp(48) * interpolated);
					}
				}
				if (animationProgress < 1) {
					startLayoutAnimation(open, false, preview);
				}
				else {
					onAnimationEndCheck(false);
				}
			}
		});
	}

	public void resumeDelayedFragmentAnimation() {
		delayedAnimationResumed = true;
		if (delayedOpenAnimationRunnable == null || waitingForKeyboardCloseRunnable != null) {
			return;
		}
		AndroidUtilities.cancelRunOnUIThread(delayedOpenAnimationRunnable);
		delayedOpenAnimationRunnable.run();
		delayedOpenAnimationRunnable = null;
	}

	public boolean isInPreviewMode() {
		return inPreviewMode || transitionAnimationPreviewMode;
	}

	public boolean isInPassivePreviewMode() {
		return (inPreviewMode && previewMenu == null) || transitionAnimationPreviewMode;
	}

	public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation, boolean check, final boolean preview) {
		return presentFragment(fragment, removeLast, forceWithoutAnimation, check, preview, null);
	}

	public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation, boolean check, final boolean preview, ActionBarPopupWindow.ActionBarPopupWindowLayout menu) {
		if (fragment == null || checkTransitionAnimation() || delegate != null && check && !delegate.needPresentFragment(fragment, removeLast, forceWithoutAnimation, this) || !fragment.onFragmentCreate()) {
			return false;
		}

		if (inPreviewMode && transitionAnimationPreviewMode) {
			if (delayedOpenAnimationRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(delayedOpenAnimationRunnable);
				delayedOpenAnimationRunnable = null;
			}
			closeLastFragment(false, true);
		}

		fragment.setInPreviewMode(preview);

		if (previewMenu != null) {
			if (previewMenu.getParent() != null) {
				((ViewGroup)previewMenu.getParent()).removeView(previewMenu);
			}
		}

		previewMenu = menu;

		fragment.setInMenuMode(previewMenu != null);

		if (parentActivity.getCurrentFocus() != null && fragment.hideKeyboardOnShow() && !preview) {
			AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
		}

		boolean needAnimation = preview || !forceWithoutAnimation && MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);

		final BaseFragment currentFragment = !fragmentsStack.isEmpty() ? fragmentsStack.get(fragmentsStack.size() - 1) : null;

		fragment.setParentLayout(this);
		View fragmentView = fragment.fragmentView;
		if (fragmentView == null) {
			fragmentView = fragment.createView(parentActivity);
		}
		else {
			ViewGroup parent = (ViewGroup)fragmentView.getParent();
			if (parent != null) {
				fragment.onRemoveFromParent();
				parent.removeView(fragmentView);
			}
		}
		View wrappedView = fragmentView;
		containerViewBack.addView(wrappedView);
		int menuHeight = 0;
		if (menu != null) {
			containerViewBack.addView(menu);
			menu.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
			menuHeight = menu.getMeasuredHeight() + AndroidUtilities.dp(24);
			FrameLayout.LayoutParams menuParams = (FrameLayout.LayoutParams)menu.getLayoutParams();
			menuParams.width = LayoutHelper.WRAP_CONTENT;
			menuParams.height = LayoutHelper.WRAP_CONTENT;
			menuParams.topMargin = getMeasuredHeight() - menuHeight - AndroidUtilities.dp(6);
			menu.setLayoutParams(menuParams);
		}
		FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)wrappedView.getLayoutParams();
		layoutParams.width = LayoutHelper.MATCH_PARENT;
		layoutParams.height = LayoutHelper.MATCH_PARENT;
		if (preview) {
			int height = fragment.getPreviewHeight();
			int statusBarHeight = AndroidUtilities.statusBarHeight;

			if (height > 0 && height < getMeasuredHeight() - statusBarHeight) {
				layoutParams.height = height;
				layoutParams.topMargin = statusBarHeight + (getMeasuredHeight() - statusBarHeight - height) / 2;
			}
			else {
				layoutParams.topMargin = layoutParams.bottomMargin = AndroidUtilities.dp(menu != null ? 0 : 24);
				layoutParams.topMargin += AndroidUtilities.statusBarHeight;
			}

			if (menu != null) {
				layoutParams.bottomMargin += menuHeight + AndroidUtilities.dp(8);
			}

			layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(8);
		}
		else {
			layoutParams.topMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;

			if (bottomNavigationPanel != null && fragment.shouldShowBottomNavigationPanel()) {
				layoutParams.bottomMargin = AndroidUtilities.dp(BottomNavigationPanel.height);

				ViewGroup panelParent = (ViewGroup)bottomNavigationPanel.getParent();

				if (panelParent != null) {
					panelParent.removeView(bottomNavigationPanel);
				}

				containerViewBack.addView(bottomNavigationPanel, BottomNavigationPanel.createLayoutParams());
			}
			else {
				if (bottomNavigationPanel != null && bottomNavigationPanel.getParent() == containerViewBack) {
					containerViewBack.removeView(bottomNavigationPanel);
				}

				layoutParams.bottomMargin = 0;
			}
		}

		wrappedView.setLayoutParams(layoutParams);
		if (fragment.actionBar != null && fragment.actionBar.shouldAddToContainer()) {
			if (removeActionBarExtraHeight) {
				fragment.actionBar.setOccupyStatusBar(false);
			}
			ViewGroup parent = (ViewGroup)fragment.actionBar.getParent();
			if (parent != null) {
				parent.removeView(fragment.actionBar);
			}
			containerViewBack.addView(fragment.actionBar);
			fragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, overlayAction);
		}
		fragmentsStack.add(fragment);
		onFragmentStackChanged();
		fragment.onResume();
		currentActionBar = fragment.actionBar;
		if (!fragment.hasOwnBackground && fragmentView.getBackground() == null) {
			fragmentView.setBackgroundResource(R.color.background);
		}

		LayoutContainer temp = containerView;
		containerView = containerViewBack;
		containerViewBack = temp;
		containerView.setVisibility(View.VISIBLE);
		setInnerTranslationX(0);
		containerView.setTranslationY(0);

		if (preview) {
			fragmentView.setOutlineProvider(new ViewOutlineProvider() {
				@Override
				public void getOutline(View view, Outline outline) {
					outline.setRoundRect(0, AndroidUtilities.statusBarHeight, view.getMeasuredWidth(), view.getMeasuredHeight(), AndroidUtilities.dp(6));
				}
			});
			fragmentView.setClipToOutline(true);
			fragmentView.setElevation(AndroidUtilities.dp(4));
			if (previewBackgroundDrawable == null) {
				previewBackgroundDrawable = new ColorDrawable(0x2e000000);
			}
			previewBackgroundDrawable.setAlpha(0);
			Theme.moveUpDrawable.setAlpha(0);
		}

		bringChildToFront(containerView);
		if (!needAnimation) {
			presentFragmentInternalRemoveOld(removeLast, currentFragment);
			if (backgroundView != null) {
				backgroundView.setVisibility(VISIBLE);
			}
		}

		if (needAnimation || preview) {
			if (useAlphaAnimations && fragmentsStack.size() == 1) {
				presentFragmentInternalRemoveOld(removeLast, currentFragment);

				transitionAnimationStartTime = System.currentTimeMillis();
				transitionAnimationInProgress = true;
				onOpenAnimationEndRunnable = () -> {
					if (currentFragment != null) {
						currentFragment.onTransitionAnimationEnd(false, false);
					}
					fragment.onTransitionAnimationEnd(true, false);
					fragment.onBecomeFullyVisible();
				};
				ArrayList<Animator> animators = new ArrayList<>();
				animators.add(ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f, 1.0f));
				if (backgroundView != null) {
					backgroundView.setVisibility(VISIBLE);
					animators.add(ObjectAnimator.ofFloat(backgroundView, View.ALPHA, 0.0f, 1.0f));
				}
				if (currentFragment != null) {
					currentFragment.onTransitionAnimationStart(false, false);
				}
				fragment.onTransitionAnimationStart(true, false);
				currentAnimation = new AnimatorSet();
				currentAnimation.playTogether(animators);
				currentAnimation.setInterpolator(accelerateDecelerateInterpolator);
				currentAnimation.setDuration(200);
				currentAnimation.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						onAnimationEndCheck(false);
					}
				});
				currentAnimation.start();
			}
			else {
				transitionAnimationPreviewMode = preview;
				transitionAnimationStartTime = System.currentTimeMillis();
				transitionAnimationInProgress = true;
				onOpenAnimationEndRunnable = () -> {
					if (preview) {
						inPreviewMode = true;
						previewMenu = menu;
						transitionAnimationPreviewMode = false;
						containerView.setScaleX(1.0f);
						containerView.setScaleY(1.0f);
					}
					else {
						presentFragmentInternalRemoveOld(removeLast, currentFragment);
						containerView.setTranslationX(0);
					}
					if (currentFragment != null) {
						currentFragment.onTransitionAnimationEnd(false, false);
					}
					fragment.onTransitionAnimationEnd(true, false);
					fragment.onBecomeFullyVisible();
				};

				boolean noDelay;

				if (noDelay = !fragment.needDelayOpenAnimation()) {
					if (currentFragment != null) {
						currentFragment.onTransitionAnimationStart(false, false);
					}
					fragment.onTransitionAnimationStart(true, false);
				}

				delayedAnimationResumed = false;
				oldFragment = currentFragment;
				newFragment = fragment;
				AnimatorSet animation = null;
				if (!preview) {
					animation = fragment.onCustomTransitionAnimation(true, () -> onAnimationEndCheck(false));
				}
				if (animation == null) {
					containerView.setAlpha(0.0f);
					if (preview) {
						containerView.setTranslationX(0.0f);
						containerView.setScaleX(0.9f);
						containerView.setScaleY(0.9f);
					}
					else {
						containerView.setTranslationX(48.0f);
						containerView.setScaleX(1.0f);
						containerView.setScaleY(1.0f);
					}
					if (containerView.isKeyboardVisible || containerViewBack.isKeyboardVisible) {
						if (currentFragment != null && !preview) {
							currentFragment.saveKeyboardPositionBeforeTransition();
						}
						waitingForKeyboardCloseRunnable = new Runnable() {
							@Override
							public void run() {
								if (waitingForKeyboardCloseRunnable != this) {
									return;
								}
								waitingForKeyboardCloseRunnable = null;
								if (noDelay) {
									if (currentFragment != null) {
										currentFragment.onTransitionAnimationStart(false, false);
									}
									fragment.onTransitionAnimationStart(true, false);
									startLayoutAnimation(true, true, preview);
								}
								else if (delayedOpenAnimationRunnable != null) {
									AndroidUtilities.cancelRunOnUIThread(delayedOpenAnimationRunnable);
									if (delayedAnimationResumed) {
										delayedOpenAnimationRunnable.run();
									}
									else {
										AndroidUtilities.runOnUIThread(delayedOpenAnimationRunnable, 200);
									}
								}
							}
						};
						if (fragment.needDelayOpenAnimation()) {
							delayedOpenAnimationRunnable = new Runnable() {
								@Override
								public void run() {
									if (delayedOpenAnimationRunnable != this) {
										return;
									}
									delayedOpenAnimationRunnable = null;
									if (currentFragment != null) {
										currentFragment.onTransitionAnimationStart(false, false);
									}
									fragment.onTransitionAnimationStart(true, false);
									startLayoutAnimation(true, true, preview);
								}
							};
						}
						AndroidUtilities.runOnUIThread(waitingForKeyboardCloseRunnable, SharedConfig.smoothKeyboard ? 250 : 200);
					}
					else if (fragment.needDelayOpenAnimation()) {
						delayedOpenAnimationRunnable = new Runnable() {
							@Override
							public void run() {
								if (delayedOpenAnimationRunnable != this) {
									return;
								}
								delayedOpenAnimationRunnable = null;
								fragment.onTransitionAnimationStart(true, false);
								startLayoutAnimation(true, true, preview);
							}
						};
						AndroidUtilities.runOnUIThread(delayedOpenAnimationRunnable, 200);
					}
					else {
						startLayoutAnimation(true, true, preview);
					}
				}
				else {
					if (!preview && (containerView.isKeyboardVisible || containerViewBack.isKeyboardVisible) && currentFragment != null) {
						currentFragment.saveKeyboardPositionBeforeTransition();
					}
					currentAnimation = animation;
				}
			}
		}
		else {
			if (backgroundView != null) {
				backgroundView.setAlpha(1.0f);
				backgroundView.setVisibility(VISIBLE);
			}
			if (currentFragment != null) {
				currentFragment.onTransitionAnimationStart(false, false);
				currentFragment.onTransitionAnimationEnd(false, false);
			}
			fragment.onTransitionAnimationStart(true, false);
			fragment.onTransitionAnimationEnd(true, false);
			fragment.onBecomeFullyVisible();
		}
		return true;
	}

	public void setFragmentStackChangedListener(Runnable onFragmentStackChanged) {
		this.onFragmentStackChangedListener = onFragmentStackChanged;
	}

	private void onFragmentStackChanged() {
		if (onFragmentStackChangedListener != null) {
			onFragmentStackChangedListener.run();
		}

		ImageLoader.getInstance().onFragmentStackChanged();
	}

	public boolean addFragmentToStack(BaseFragment fragment) {
		return addFragmentToStack(fragment, -1);
	}

	public boolean addFragmentToStack(BaseFragment fragment, int position) {
		if (delegate != null && !delegate.needAddFragmentToStack(fragment, this) || !fragment.onFragmentCreate()) {
			return false;
		}
		fragment.setParentLayout(this);
		if (position == -1) {
			if (!fragmentsStack.isEmpty()) {
				BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
				previousFragment.onPause();
				if (previousFragment.actionBar != null && previousFragment.actionBar.shouldAddToContainer()) {
					ViewGroup parent = (ViewGroup)previousFragment.actionBar.getParent();
					if (parent != null) {
						parent.removeView(previousFragment.actionBar);
					}
				}
				if (previousFragment.fragmentView != null) {
					ViewGroup parent = (ViewGroup)previousFragment.fragmentView.getParent();
					if (parent != null) {
						previousFragment.onRemoveFromParent();
						parent.removeView(previousFragment.fragmentView);
					}
				}
			}
			fragmentsStack.add(fragment);
			onFragmentStackChanged();
		}
		else {
			fragmentsStack.add(position, fragment);
			onFragmentStackChanged();
		}
		return true;
	}

	private void closeLastFragmentInternalRemoveOld(BaseFragment fragment) {
		fragment.finishing = true;
		fragment.onPause();
		fragment.onFragmentDestroy();
		fragment.setParentLayout(null);
		fragmentsStack.remove(fragment);
		containerViewBack.setVisibility(View.INVISIBLE);
		containerViewBack.setTranslationY(0);
		bringChildToFront(containerView);
		onFragmentStackChanged();
	}

	public void movePreviewFragment(float dy) {
		if (!inPreviewMode || previewMenu != null || transitionAnimationPreviewMode) {
			return;
		}
		float currentTranslation = containerView.getTranslationY();
		float nextTranslation = -dy;
		if (nextTranslation > 0) {
			nextTranslation = 0;
		}
		else if (nextTranslation < -AndroidUtilities.dp(60)) {
			nextTranslation = 0;
			expandPreviewFragment();
		}
		if (currentTranslation != nextTranslation) {
			containerView.setTranslationY(nextTranslation);
			invalidate();
		}
	}

	public void expandPreviewFragment() {
		previewOpenAnimationInProgress = true;
		inPreviewMode = false;

		BaseFragment prevFragment = fragmentsStack.get(fragmentsStack.size() - 2);
		BaseFragment fragment = fragmentsStack.get(fragmentsStack.size() - 1);

		fragment.fragmentView.setOutlineProvider(null);
		fragment.fragmentView.setClipToOutline(false);

		FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)fragment.fragmentView.getLayoutParams();
		layoutParams.topMargin = layoutParams.bottomMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;

		// TODO: maybe take bottom navigation panel into account

		layoutParams.height = LayoutHelper.MATCH_PARENT;
		fragment.fragmentView.setLayoutParams(layoutParams);

		presentFragmentInternalRemoveOld(false, prevFragment);

		AnimatorSet animatorSet = new AnimatorSet();
		animatorSet.playTogether(ObjectAnimator.ofFloat(fragment.fragmentView, View.SCALE_X, 1.0f, 1.05f, 1.0f), ObjectAnimator.ofFloat(fragment.fragmentView, View.SCALE_Y, 1.0f, 1.05f, 1.0f));
		animatorSet.setDuration(200);
		animatorSet.setInterpolator(new CubicBezierInterpolator(0.42, 0.0, 0.58, 1.0));
		animatorSet.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				previewOpenAnimationInProgress = false;
				fragment.onPreviewOpenAnimationEnd();
			}
		});
		animatorSet.start();
		performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

		fragment.setInPreviewMode(false);
		fragment.setInMenuMode(false);
	}

	public void finishPreviewFragment() {
		if (!inPreviewMode && !transitionAnimationPreviewMode) {
			return;
		}
		if (delayedOpenAnimationRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(delayedOpenAnimationRunnable);
			delayedOpenAnimationRunnable = null;
		}
		closeLastFragment(true);
	}

	public void closeLastFragment(boolean animated) {
		closeLastFragment(animated, false);
	}

	public void closeLastFragment(boolean animated, boolean forceNoAnimation) {
		if (delegate != null && !delegate.needCloseLastFragment(this) || checkTransitionAnimation() || fragmentsStack.isEmpty()) {
			return;
		}
		if (parentActivity.getCurrentFocus() != null) {
			AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
		}
		setInnerTranslationX(0);
		boolean needAnimation = !forceNoAnimation && (inPreviewMode || transitionAnimationPreviewMode || animated && MessagesController.getGlobalMainSettings().getBoolean("view_animations", true));
		final BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
		BaseFragment previousFragment = null;
		if (fragmentsStack.size() > 1) {
			previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
		}

		if (previousFragment != null) {
			AndroidUtilities.setLightStatusBar(parentActivity.getWindow(), !AndroidUtilities.isDarkTheme() || (previousFragment.hasForceLightStatusBar() && !AndroidUtilities.isDarkTheme()), previousFragment.hasForceLightStatusBar());
			LayoutContainer temp = containerView;
			containerView = containerViewBack;
			containerViewBack = temp;

			previousFragment.setParentLayout(this);
			View fragmentView = previousFragment.fragmentView;
			if (fragmentView == null) {
				fragmentView = previousFragment.createView(parentActivity);
			}

			if (!inPreviewMode) {
				containerView.setVisibility(View.VISIBLE);
				ViewGroup parent = (ViewGroup)fragmentView.getParent();
				if (parent != null) {
					previousFragment.onRemoveFromParent();
					try {
						parent.removeView(fragmentView);
					}
					catch (Exception e) {
						FileLog.e(e);
					}
				}
				containerView.addView(fragmentView);

				FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)fragmentView.getLayoutParams();
				layoutParams.width = LayoutHelper.MATCH_PARENT;
				layoutParams.height = LayoutHelper.MATCH_PARENT;
				layoutParams.topMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;

				if (bottomNavigationPanel != null && previousFragment.shouldShowBottomNavigationPanel()) {
					layoutParams.bottomMargin = AndroidUtilities.dp(BottomNavigationPanel.height);

					ViewGroup panelParent = (ViewGroup)bottomNavigationPanel.getParent();

					if (panelParent != null) {
						panelParent.removeView(bottomNavigationPanel);
					}

					containerView.addView(bottomNavigationPanel, BottomNavigationPanel.createLayoutParams());
				}
				else {
					if (bottomNavigationPanel != null && bottomNavigationPanel.getParent() == containerView) {
						containerView.removeView(bottomNavigationPanel);
					}

					layoutParams.bottomMargin = 0;
				}

				fragmentView.setLayoutParams(layoutParams);

				if (previousFragment.actionBar != null && previousFragment.actionBar.shouldAddToContainer()) {
					if (removeActionBarExtraHeight) {
						previousFragment.actionBar.setOccupyStatusBar(false);
					}
					parent = (ViewGroup)previousFragment.actionBar.getParent();
					if (parent != null) {
						parent.removeView(previousFragment.actionBar);
					}
					containerView.addView(previousFragment.actionBar);
					previousFragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, overlayAction);
				}
			}

			newFragment = previousFragment;
			oldFragment = currentFragment;
			previousFragment.onTransitionAnimationStart(true, true);
			currentFragment.onTransitionAnimationStart(false, true);
			previousFragment.onResume();

			currentActionBar = previousFragment.actionBar;
			if (!previousFragment.hasOwnBackground && fragmentView.getBackground() == null) {
				fragmentView.setBackgroundResource(R.color.background);
			}

			if (needAnimation) {
				transitionAnimationStartTime = System.currentTimeMillis();
				transitionAnimationInProgress = true;
				final BaseFragment previousFragmentFinal = previousFragment;
				currentFragment.setRemovingFromStack(true);
				onCloseAnimationEndRunnable = () -> {
					if (previewMenu != null) {
						ViewGroup parent = (ViewGroup)previewMenu.getParent();
						if (parent != null) {
							parent.removeView(previewMenu);
						}
					}
					if (inPreviewMode || transitionAnimationPreviewMode) {
						containerViewBack.setScaleX(1.0f);
						containerViewBack.setScaleY(1.0f);
						inPreviewMode = false;
						previewMenu = null;
						transitionAnimationPreviewMode = false;
					}
					else {
						containerViewBack.setTranslationX(0);
					}
					closeLastFragmentInternalRemoveOld(currentFragment);
					currentFragment.setRemovingFromStack(false);
					currentFragment.onTransitionAnimationEnd(false, true);
					previousFragmentFinal.onTransitionAnimationEnd(true, true);
					previousFragmentFinal.onBecomeFullyVisible();
				};
				AnimatorSet animation = null;
				if (!inPreviewMode && !transitionAnimationPreviewMode) {
					animation = currentFragment.onCustomTransitionAnimation(false, () -> onAnimationEndCheck(false));
				}
				if (animation == null) {
					if (!inPreviewMode && (containerView.isKeyboardVisible || containerViewBack.isKeyboardVisible)) {
						waitingForKeyboardCloseRunnable = new Runnable() {
							@Override
							public void run() {
								if (waitingForKeyboardCloseRunnable != this) {
									return;
								}
								waitingForKeyboardCloseRunnable = null;
								startLayoutAnimation(false, true, false);
							}
						};
						AndroidUtilities.runOnUIThread(waitingForKeyboardCloseRunnable, 200);
					}
					else {
						startLayoutAnimation(false, true, inPreviewMode || transitionAnimationPreviewMode);
					}
				}
				else {
					currentAnimation = animation;
					if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().isShowing()) {
						Bulletin.getVisibleBulletin().hide();
					}
				}
				onFragmentStackChanged();
			}
			else {
				closeLastFragmentInternalRemoveOld(currentFragment);
				currentFragment.onTransitionAnimationEnd(false, true);
				previousFragment.onTransitionAnimationEnd(true, true);
				previousFragment.onBecomeFullyVisible();
			}
		}
		else {
			if (useAlphaAnimations && !forceNoAnimation) {
				transitionAnimationStartTime = System.currentTimeMillis();
				transitionAnimationInProgress = true;

				onCloseAnimationEndRunnable = () -> {
					removeFragmentFromStackInternal(currentFragment);
					setVisibility(GONE);
					if (backgroundView != null) {
						backgroundView.setVisibility(GONE);
					}
					if (drawerLayoutContainer != null) {
						drawerLayoutContainer.setAllowOpenDrawer(true, false);
					}
				};

				ArrayList<Animator> animators = new ArrayList<>();
				animators.add(ObjectAnimator.ofFloat(this, View.ALPHA, 1.0f, 0.0f));
				if (backgroundView != null) {
					animators.add(ObjectAnimator.ofFloat(backgroundView, View.ALPHA, 1.0f, 0.0f));
				}

				currentAnimation = new AnimatorSet();
				currentAnimation.playTogether(animators);
				currentAnimation.setInterpolator(accelerateDecelerateInterpolator);
				currentAnimation.setDuration(200);
				currentAnimation.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationStart(Animator animation) {
						transitionAnimationStartTime = System.currentTimeMillis();
					}

					@Override
					public void onAnimationEnd(Animator animation) {
						onAnimationEndCheck(false);
					}
				});
				currentAnimation.start();
			}
			else {
				removeFragmentFromStackInternal(currentFragment);
				setVisibility(GONE);
				if (backgroundView != null) {
					backgroundView.setVisibility(GONE);
				}
			}
		}
	}

	public void showFragment(int i) {
		if (fragmentsStack.isEmpty()) {
			return;
		}

		for (int a = 0; a < i; a++) {
			BaseFragment previousFragment = fragmentsStack.get(a);

			if (previousFragment.actionBar != null && previousFragment.actionBar.shouldAddToContainer()) {
				ViewGroup parent = (ViewGroup)previousFragment.actionBar.getParent();

				if (parent != null) {
					parent.removeView(previousFragment.actionBar);
				}
			}

			if (previousFragment.fragmentView != null) {
				ViewGroup parent = (ViewGroup)previousFragment.fragmentView.getParent();

				if (parent != null) {
					previousFragment.onPause();
					previousFragment.onRemoveFromParent();
					parent.removeView(previousFragment.fragmentView);
				}
			}
		}

		BaseFragment previousFragment = fragmentsStack.get(i);
		previousFragment.setParentLayout(this);

		View fragmentView = previousFragment.fragmentView;

		if (fragmentView == null) {
			fragmentView = previousFragment.createView(parentActivity);
		}
		else {
			ViewGroup parent = (ViewGroup)fragmentView.getParent();

			if (parent != null) {
				previousFragment.onRemoveFromParent();
				parent.removeView(fragmentView);
			}
		}

		LayoutParams layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
		layoutParams.topMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;

		if (bottomNavigationPanel != null && previousFragment.shouldShowBottomNavigationPanel()) {
			layoutParams.bottomMargin = AndroidUtilities.dp(BottomNavigationPanel.height);

			ViewGroup panelParent = (ViewGroup)bottomNavigationPanel.getParent();

			if (panelParent != null) {
				panelParent.removeView(bottomNavigationPanel);
			}

			containerView.addView(bottomNavigationPanel, BottomNavigationPanel.createLayoutParams());
		}
		else {
			if (bottomNavigationPanel != null && bottomNavigationPanel.getParent() == containerView) {
				containerView.removeView(bottomNavigationPanel);
			}

			layoutParams.bottomMargin = 0;
		}

		containerView.addView(fragmentView, layoutParams);

		if (previousFragment.actionBar != null && previousFragment.actionBar.shouldAddToContainer()) {
			if (removeActionBarExtraHeight) {
				previousFragment.actionBar.setOccupyStatusBar(false);
			}

			ViewGroup parent = (ViewGroup)previousFragment.actionBar.getParent();

			if (parent != null) {
				parent.removeView(previousFragment.actionBar);
			}

			containerView.addView(previousFragment.actionBar);

			previousFragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, overlayAction);
		}

		previousFragment.onResume();

		currentActionBar = previousFragment.actionBar;

		if (!previousFragment.hasOwnBackground && fragmentView.getBackground() == null) {
			fragmentView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.background, null));
		}
	}

	public void showLastFragment() {
		if (fragmentsStack.isEmpty()) {
			return;
		}

		showFragment(fragmentsStack.size() - 1);
	}

	private void removeFragmentFromStackInternal(BaseFragment fragment) {
		fragment.onPause();
		fragment.onFragmentDestroy();
		fragment.setParentLayout(null);
		fragmentsStack.remove(fragment);
		onFragmentStackChanged();
	}

	/**
	 * Remove all fragments between the given indexes.
	 *
	 * @param startIndex First fragment to keep
	 * @param endIndex   Last fragment to keep
	 */
	public void removeFragmentsBetween(int startIndex, int endIndex) {
		int diff = endIndex - startIndex - 1;

		if (startIndex < 0 || endIndex < 0 || startIndex >= fragmentsStack.size() || endIndex >= fragmentsStack.size() || diff <= 0) {
			return;
		}

		for (int i = 0; i < diff; i++) {
			BaseFragment fragment = fragmentsStack.get(startIndex + 1);
			removeFragmentFromStackInternal(fragment);
		}
	}

	/**
	 * Remove all fragments up to the given index.
	 *
	 * @param index The index of the last fragment to keep
	 */
	public void removeFragmentsUpTo(int index) {
		if (index < 0 || index >= fragmentsStack.size()) {
			return;
		}

		while (fragmentsStack.size() > index + 1) {
			BaseFragment fragment = fragmentsStack.get(fragmentsStack.size() - 1);
			removeFragmentFromStackInternal(fragment);
		}
	}

	public void removeFragmentFromStack(int num) {
		if (num >= fragmentsStack.size()) {
			return;
		}
		removeFragmentFromStackInternal(fragmentsStack.get(num));
	}

	public void removeFragmentFromStack(BaseFragment fragment) {
		if (useAlphaAnimations && fragmentsStack.size() == 1 && AndroidUtilities.isTablet()) {
			closeLastFragment(true);
		}
		else {
			if (delegate != null && fragmentsStack.size() == 1 && AndroidUtilities.isTablet()) {
				delegate.needCloseLastFragment(this);
			}
			removeFragmentFromStackInternal(fragment);
		}
	}

	public void removeAllFragments() {
		for (int a = 0; a < fragmentsStack.size(); a++) {
			removeFragmentFromStackInternal(fragmentsStack.get(a));
			a--;
		}
	}

	public void rebuildLogout() {
		containerView.removeAllViews();
		containerViewBack.removeAllViews();
		currentActionBar = null;
		newFragment = null;
		oldFragment = null;
	}

	public void rebuildAllFragmentViews(boolean last, boolean showLastAfter) {
		if (transitionAnimationInProgress || startedTracking) {
			rebuildAfterAnimation = true;
			rebuildLastAfterAnimation = last;
			showLastAfterAnimation = showLastAfter;
			return;
		}
		int size = fragmentsStack.size();
		if (!last) {
			size--;
		}
		if (inPreviewMode) {
			size--;
		}
		for (int a = 0; a < size; a++) {
			fragmentsStack.get(a).clearViews();
			fragmentsStack.get(a).setParentLayout(this);
		}
		if (delegate != null) {
			delegate.onRebuildAllFragments(this, last);
		}
		if (showLastAfter) {
			showLastFragment();
		}
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU && !checkTransitionAnimation() && !startedTracking && currentActionBar != null) {
			currentActionBar.onMenuButtonPressed();
		}
		return super.onKeyUp(keyCode, event);
	}

	public void onActionModeStarted() {
		if (currentActionBar != null) {
			currentActionBar.setVisibility(GONE);
		}
		inActionMode = true;
	}

	public void onActionModeFinished() {
		if (currentActionBar != null) {
			currentActionBar.setVisibility(VISIBLE);
		}
		inActionMode = false;
	}

	private void onCloseAnimationEnd() {
		if (transitionAnimationInProgress && onCloseAnimationEndRunnable != null) {
			transitionAnimationInProgress = false;
			transitionAnimationPreviewMode = false;
			transitionAnimationStartTime = 0;
			newFragment = null;
			oldFragment = null;
			Runnable endRunnable = onCloseAnimationEndRunnable;
			onCloseAnimationEndRunnable = null;
			endRunnable.run();
			checkNeedRebuild();
			checkNeedRebuild();
		}
	}

	private void checkNeedRebuild() {
		if (rebuildAfterAnimation) {
			rebuildAllFragmentViews(rebuildLastAfterAnimation, showLastAfterAnimation);
			rebuildAfterAnimation = false;
		}
	}

	private void onOpenAnimationEnd() {
		if (transitionAnimationInProgress && onOpenAnimationEndRunnable != null) {
			transitionAnimationInProgress = false;
			transitionAnimationPreviewMode = false;
			transitionAnimationStartTime = 0;
			newFragment = null;
			oldFragment = null;
			Runnable endRunnable = onOpenAnimationEndRunnable;
			onOpenAnimationEndRunnable = null;
			endRunnable.run();
			checkNeedRebuild();
		}
	}

	public void startActivityForResult(final Intent intent, final int requestCode) {
		if (parentActivity == null) {
			return;
		}
		if (transitionAnimationInProgress) {
			if (currentAnimation != null) {
				currentAnimation.cancel();
				currentAnimation = null;
			}
			if (onCloseAnimationEndRunnable != null) {
				onCloseAnimationEnd();
			}
			else if (onOpenAnimationEndRunnable != null) {
				onOpenAnimationEnd();
			}
			containerView.invalidate();
		}
		if (intent != null) {
			parentActivity.startActivityForResult(intent, requestCode);
		}
	}

	public void setUseAlphaAnimations(boolean value) {
		useAlphaAnimations = value;
	}

	public void setBackgroundView(View view) {
		backgroundView = view;
	}

	public DrawerLayoutContainer getDrawerLayoutContainer() {
		return drawerLayoutContainer;
	}

	public void setDrawerLayoutContainer(DrawerLayoutContainer layout) {
		drawerLayoutContainer = layout;
	}

	public void setRemoveActionBarExtraHeight(boolean value) {
		removeActionBarExtraHeight = value;
	}

	public void setTitleOverlayText(String title, int titleId, Runnable action) {
		titleOverlayText = title;
		titleOverlayTextId = titleId;
		overlayAction = action;
		for (int a = 0; a < fragmentsStack.size(); a++) {
			BaseFragment fragment = fragmentsStack.get(a);
			if (fragment.actionBar != null) {
				fragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, action);
			}
		}
	}

	public boolean extendActionMode(Menu menu) {
		return !fragmentsStack.isEmpty() && fragmentsStack.get(fragmentsStack.size() - 1).extendActionMode(menu);
	}

	@Override
	public boolean hasOverlappingRendering() {
		return false;
	}

	public void setFragmentPanTranslationOffset(int offset) {
		if (containerView != null) {
			containerView.setFragmentPanTranslationOffset(offset);
		}
	}

	private View findScrollingChild(ViewGroup parent, float x, float y) {
		int n = parent.getChildCount();

		for (int i = 0; i < n; i++) {
			View child = parent.getChildAt(i);

			if (child.getVisibility() != View.VISIBLE) {
				continue;
			}

			child.getHitRect(rect);

			if (rect.contains((int)x, (int)y)) {
				if (child.canScrollHorizontally(-1)) {
					return child;
				}
				else if (child instanceof ViewGroup) {
					View v = findScrollingChild((ViewGroup)child, x - rect.left, y - rect.top);

					if (v != null) {
						return v;
					}
				}
			}
		}

		return null;
	}

	public interface ActionBarLayoutDelegate {
		boolean onPreIme();

		boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, ActionBarLayout layout);

		boolean needAddFragmentToStack(BaseFragment fragment, ActionBarLayout layout);

		boolean needCloseLastFragment(ActionBarLayout layout);

		void onRebuildAllFragments(ActionBarLayout layout, boolean last);
	}

	public class LayoutContainer extends FrameLayout {
		private final Rect rect = new Rect();
		private final Paint backgroundPaint = new Paint();
		private boolean isKeyboardVisible;
		private int fragmentPanTranslationOffset;
		private int backgroundColor;

		private boolean wasPortrait;
		// for menu buttons to be clicked by hover:
		private float pressX, pressY;
		private boolean allowToPressByHover;

		public LayoutContainer(Context context) {
			super(context);
			setWillNotDraw(false);
		}

		@Override
		protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
			if (child instanceof ActionBar || child instanceof BottomNavigationPanel) {
				return super.drawChild(canvas, child, drawingTime);
			}
			else {
				int actionBarHeight = 0;
				int actionBarY = 0;
				int childCount = getChildCount();

				for (int a = 0; a < childCount; a++) {
					View view = getChildAt(a);

					if (view == child) {
						continue;
					}

					if (view instanceof ActionBar && view.getVisibility() == VISIBLE) {
						if (((ActionBar)view).getCastShadows()) {
							actionBarHeight = view.getMeasuredHeight();
							actionBarY = (int)view.getY();
						}

						break;
					}
				}

				boolean result = super.drawChild(canvas, child, drawingTime);

				if (actionBarHeight != 0 && headerShadowDrawable != null) {
					headerShadowDrawable.setBounds(0, actionBarY + actionBarHeight, getMeasuredWidth(), actionBarY + actionBarHeight + headerShadowDrawable.getIntrinsicHeight());
					headerShadowDrawable.draw(canvas);
				}

				return result;
			}
		}

		@Override
		public boolean hasOverlappingRendering() {
			return Build.VERSION.SDK_INT >= 28;
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int width = MeasureSpec.getSize(widthMeasureSpec);
			int height = MeasureSpec.getSize(heightMeasureSpec);
			boolean isPortrait = height > width;
			if (wasPortrait != isPortrait && isInPreviewMode()) {
				finishPreviewFragment();
			}
			wasPortrait = isPortrait;

			int count = getChildCount();
			int actionBarHeight = 0;

			for (int a = 0; a < count; a++) {
				View child = getChildAt(a);

				if (child instanceof ActionBar) {
					child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
					actionBarHeight = child.getMeasuredHeight();
					break;
				}
			}

			for (int a = 0; a < count; a++) {
				View child = getChildAt(a);

				if (!(child instanceof ActionBar)) {
					if (child.getFitsSystemWindows()) {
						measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
					}
					else {
						measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, actionBarHeight);
					}
				}
			}

			setMeasuredDimension(width, height);
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			int count = getChildCount();
			int actionBarHeight = 0;

			for (int a = 0; a < count; a++) {
				View child = getChildAt(a);

				if (child instanceof ActionBar) {
					actionBarHeight = child.getMeasuredHeight();
					child.layout(0, 0, child.getMeasuredWidth(), actionBarHeight);
					break;
				}
			}

			for (int a = 0; a < count; a++) {
				View child = getChildAt(a);

				if (!(child instanceof ActionBar)) {
					if (child instanceof BottomNavigationPanel) {
						child.layout(0, b - t - child.getMeasuredHeight(), child.getMeasuredWidth(), b - t);
					}
					else {
						FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)child.getLayoutParams();

						if (child.getFitsSystemWindows()) {
							child.layout(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.leftMargin + child.getMeasuredWidth(), layoutParams.topMargin + child.getMeasuredHeight());
						}
						else {
							child.layout(layoutParams.leftMargin, layoutParams.topMargin + actionBarHeight, layoutParams.leftMargin + child.getMeasuredWidth(), layoutParams.topMargin + actionBarHeight + child.getMeasuredHeight());
						}
					}
				}
			}

			View rootView = getRootView();
			getWindowVisibleDisplayFrame(rect);

			int usableViewHeight = rootView.getHeight() - (rect.top != 0 ? AndroidUtilities.statusBarHeight : 0) - AndroidUtilities.getViewInset(rootView);

			isKeyboardVisible = usableViewHeight - (rect.bottom - rect.top) > 0;

			if (waitingForKeyboardCloseRunnable != null && !containerView.isKeyboardVisible && !containerViewBack.isKeyboardVisible) {
				AndroidUtilities.cancelRunOnUIThread(waitingForKeyboardCloseRunnable);
				waitingForKeyboardCloseRunnable.run();
				waitingForKeyboardCloseRunnable = null;
			}
		}

		@Override
		public boolean dispatchTouchEvent(MotionEvent ev) {
			processMenuButtonsTouch(ev);
			boolean passivePreview = inPreviewMode && previewMenu == null;
			if ((passivePreview || transitionAnimationPreviewMode) && (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)) {
				return false;
			}
			try {
				return (!passivePreview || this != containerView) && super.dispatchTouchEvent(ev);
			}
			catch (Throwable e) {
				FileLog.e(e);
			}
			return false;
		}

		@Override
		protected void onDraw(@NonNull Canvas canvas) {
			if (fragmentPanTranslationOffset != 0) {
				int color = getContext().getColor(R.color.background);
				if (backgroundColor != color) {
					backgroundPaint.setColor(backgroundColor = getContext().getColor(R.color.background));
				}
				canvas.drawRect(0, getMeasuredHeight() - fragmentPanTranslationOffset - 3, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
			}
			super.onDraw(canvas);
		}

		public void setFragmentPanTranslationOffset(int fragmentPanTranslationOffset) {
			this.fragmentPanTranslationOffset = fragmentPanTranslationOffset;
			invalidate();
		}

		public void processMenuButtonsTouch(MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				pressX = event.getX();
				pressY = event.getY();
				allowToPressByHover = false;
			}
			else if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
				if (previewMenu != null && highlightActionButtons) {
//                    movePreviewFragment(Math.min(pressY, AndroidUtilities.displaySize.y * .4f) - event.getY());
					if (!allowToPressByHover && Math.sqrt(Math.pow(pressX - event.getX(), 2) + Math.pow(pressY - event.getY(), 2)) > AndroidUtilities.dp(30)) {
						allowToPressByHover = true;
					}
					if (allowToPressByHover && (previewMenu.getSwipeBack() == null || !previewMenu.getSwipeBack().isForegroundOpen())) {
						for (int i = 0; i < previewMenu.getItemsCount(); ++i) {
							ActionBarMenuSubItem button = (ActionBarMenuSubItem)previewMenu.getItemAt(i);
							if (button != null) {
								Drawable ripple = button.getBackground();
								button.getGlobalVisibleRect(AndroidUtilities.rectTmp2);
								boolean shouldBeEnabled = AndroidUtilities.rectTmp2.contains((int)event.getX(), (int)event.getY()), enabled = ripple.getState().length == 2;
								if (event.getAction() == MotionEvent.ACTION_MOVE) {
									if (shouldBeEnabled != enabled) {
										ripple.setState(shouldBeEnabled ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[]{});
										if (shouldBeEnabled) {
											try {
												button.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
											}
											catch (Exception ignore) {
											}
										}
									}
								}
								else if (event.getAction() == MotionEvent.ACTION_UP) {
									if (shouldBeEnabled) {
										button.performClick();
									}
								}
							}
						}
					}
				}
			}
			if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
				if (previewMenu != null && highlightActionButtons) {
					int alpha = Theme.moveUpDrawable.getAlpha();
					ValueAnimator arrowAlphaUpdate = ValueAnimator.ofFloat(alpha, 0);
					arrowAlphaUpdate.addUpdateListener(a -> {
						Theme.moveUpDrawable.setAlpha(((Float)a.getAnimatedValue()).intValue());
						if (drawerLayoutContainer != null) {
							drawerLayoutContainer.invalidate();
						}
						if (containerView != null) {
							containerView.invalidate();
						}
						ActionBarLayout.this.invalidate();
					});
					arrowAlphaUpdate.setDuration(150);
					arrowAlphaUpdate.setInterpolator(CubicBezierInterpolator.DEFAULT);
					arrowAlphaUpdate.start();
					ObjectAnimator containerTranslationUpdate = ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0);
					containerTranslationUpdate.setDuration(150);
					containerTranslationUpdate.setInterpolator(CubicBezierInterpolator.DEFAULT);
					containerTranslationUpdate.start();
				}
				highlightActionButtons = false;
			}
		}
	}
}
