/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components.Premium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tlrpc.User;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PremiumFeatureCell;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

public class PremiumPreviewBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

	public float startEnterFromX;
	public float startEnterFromY;
	public float startEnterFromX1;
	public float startEnterFromY1;
	public float startEnterFromScale;
	public SimpleTextView startEnterFromView;
	public View overrideTitleIcon;
	public TLRPC.InputStickerSet statusStickerSet;
	public boolean isEmojiStatus;
	ArrayList<PremiumPreviewFragment.PremiumFeatureData> premiumFeatures = new ArrayList<>();
	int currentAccount;
	User user;
	GiftPremiumBottomSheet.GiftTier giftTier;
	boolean isOutboundGift;
	PremiumFeatureCell dummyCell;
	int totalGradientHeight;
	int rowCount;
	int paddingRow;
	int featuresStartRow;
	int featuresEndRow;
	int sectionRow;
	int helpUsRow;
	int buttonRow;
	FireworksOverlay fireworksOverlay;
	PremiumGradient.GradientTools gradientTools;
	StarParticlesView starParticlesView;
	GLIconTextureView iconTextureView;
	ViewGroup iconContainer;
	BaseFragment fragment;
	int[] coords = new int[2];
	float enterTransitionProgress = 0;
	boolean enterTransitionInProgress;
	ValueAnimator enterAnimator;

	boolean animateConfetti;
	FrameLayout buttonContainer;
	FrameLayout bulletinContainer;
	private LinkSpanDrawable.LinksTextView titleView;
	private TextView subtitleView;

	public PremiumPreviewBottomSheet(BaseFragment fragment, int currentAccount, User user, @Nullable GiftPremiumBottomSheet.GiftTier gift) {
		super(fragment, false, false);
		fixNavigationBar();
		this.fragment = fragment;
		topPadding = 0.26f;
		this.user = user;
		this.currentAccount = currentAccount;
		this.giftTier = gift;
		dummyCell = new PremiumFeatureCell(getContext());
		PremiumPreviewFragment.fillPremiumFeaturesList(premiumFeatures, currentAccount);

		if (giftTier != null || UserConfig.getInstance(currentAccount).isPremium()) {
			buttonContainer.setVisibility(View.GONE);
		}

		gradientTools = new PremiumGradient.GradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, Theme.key_premiumGradient3, Theme.key_premiumGradient4);
		gradientTools.exactly = true;
		gradientTools.x1 = 0;
		gradientTools.y1 = 1f;
		gradientTools.x2 = 0;
		gradientTools.y2 = 0f;
		gradientTools.cx = 0;
		gradientTools.cy = 0;

		paddingRow = rowCount++;
		featuresStartRow = rowCount;
		rowCount += premiumFeatures.size();
		featuresEndRow = rowCount;
		sectionRow = rowCount++;
		if (!UserConfig.getInstance(currentAccount).isPremium() && gift == null) {
			buttonRow = rowCount++;
		}
		recyclerListView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
		recyclerListView.setOnItemClickListener((view, position) -> {
			if (view instanceof PremiumFeatureCell) {
				PremiumFeatureCell cell = (PremiumFeatureCell)view;
				PremiumPreviewFragment.sentShowFeaturePreview(currentAccount, cell.data.type);
//                if (cell.data.type == PremiumPreviewFragment.PREMIUM_FEATURE_LIMITS) {
//                    DoubledLimitsBottomSheet bottomSheet = new DoubledLimitsBottomSheet(fragment, currentAccount);
//                    showDialog(bottomSheet);
//                } else {
				showDialog(new PremiumFeatureBottomSheet(fragment, cell.data.type, false));
				//  }
			}
		});

		MediaDataController.getInstance(currentAccount).preloadPremiumPreviewStickers();
		PremiumPreviewFragment.sentShowScreenStat("profile");

		fireworksOverlay = new FireworksOverlay(getContext());
		container.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

		bulletinContainer = new FrameLayout(getContext());
		containerView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
	}

	public PremiumPreviewBottomSheet setOutboundGift(boolean outboundGift) {
		isOutboundGift = outboundGift;
		return this;
	}

	public PremiumPreviewBottomSheet setAnimateConfetti(boolean animateConfetti) {
		this.animateConfetti = animateConfetti;
		return this;
	}

	private void showDialog(Dialog dialog) {
		if (iconTextureView != null) {
			iconTextureView.setDialogVisible(true);
		}
		starParticlesView.setPaused(true);
		dialog.setOnDismissListener(dialog1 -> {
			if (iconTextureView != null) {
				iconTextureView.setDialogVisible(false);
			}
			starParticlesView.setPaused(false);
		});
		dialog.show();
	}

	@Override
	public void onViewCreated(FrameLayout containerView) {
		super.onViewCreated(containerView);
		currentAccount = UserConfig.selectedAccount;

		PremiumButtonView premiumButtonView = new PremiumButtonView(getContext(), false);
		premiumButtonView.setButton(PremiumPreviewFragment.getPremiumButtonText(currentAccount, null), v -> {
			PremiumPreviewFragment.sentPremiumButtonClick();
			PremiumPreviewFragment.buyPremium(fragment, "profile");
		});

		buttonContainer = new FrameLayout(getContext());

		View buttonDivider = new View(getContext());
		buttonDivider.setBackgroundColor(getContext().getColor(R.color.divider));
		buttonContainer.addView(buttonDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));
		buttonDivider.getLayoutParams().height = 1;
		AndroidUtilities.updateViewVisibilityAnimated(buttonDivider, true, 1f, false);

		if (!UserConfig.getInstance(currentAccount).isPremium()) {
			buttonContainer.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
			buttonContainer.setBackgroundColor(getContext().getColor(R.color.background));
			containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));
		}
	}

	@Override
	protected void onPreMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onPreMeasure(widthMeasureSpec, heightMeasureSpec);
		measureGradient(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec));
		container.getLocationOnScreen(coords);
	}

	public void setTitle() {
		if (titleView == null || subtitleView == null) {
			return;
		}
		if (statusStickerSet != null) {
			final String stickerSetPlaceholder = "<STICKERSET>";
			String string = LocaleController.formatString(R.string.TelegramPremiumUserStatusDialogTitle, ContactsController.formatName(user.getFirst_name(), user.getLast_name()), stickerSetPlaceholder);
			CharSequence charSequence = AndroidUtilities.replaceSingleTag(string, Theme.key_windowBackgroundWhiteBlueButton, null);
			SpannableStringBuilder title = charSequence instanceof SpannableStringBuilder ? ((SpannableStringBuilder)charSequence) : new SpannableStringBuilder(charSequence);
			int index = charSequence.toString().indexOf(stickerSetPlaceholder);
			if (index >= 0) {
				TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSet(statusStickerSet, false);
				TLRPC.Document sticker = null;
				if (stickerSet != null && !stickerSet.documents.isEmpty()) {
					sticker = stickerSet.documents.get(0);
					if (stickerSet.set != null) {
						for (int i = 0; i < stickerSet.documents.size(); ++i) {
							if (stickerSet.documents.get(i).id == stickerSet.set.thumb_document_id) {
								sticker = stickerSet.documents.get(i);
								break;
							}
						}
					}
				}
				SpannableStringBuilder replaceWith;
				if (sticker != null) {
					SpannableStringBuilder animatedEmoji = new SpannableStringBuilder("x");
					animatedEmoji.setSpan(new AnimatedEmojiSpan(sticker, titleView.getPaint().getFontMetricsInt()), 0, animatedEmoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					if (stickerSet != null && stickerSet.set != null) {
						animatedEmoji.append("\u00A0").append(stickerSet.set.title);
					}
					replaceWith = animatedEmoji;
				}
				else {
					SpannableStringBuilder loading = new SpannableStringBuilder("xxxxxx");
					loading.setSpan(new LoadingSpan(titleView, AndroidUtilities.dp(100)), 0, loading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					replaceWith = loading;
				}
				title.replace(index, index + stickerSetPlaceholder.length(), replaceWith);
				title.setSpan(new ClickableSpan() {
					@Override
					public void updateDrawState(@NonNull TextPaint ds) {
						super.updateDrawState(ds);
						ds.setUnderlineText(false);
					}

					@Override
					public void onClick(@NonNull View view) {
					}
				}, index, index + replaceWith.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				titleView.setOnLinkPressListener(l -> {
					ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>();
					inputStickerSets.add(statusStickerSet);
					BaseFragment overridenFragment = new BaseFragment() {
						@Override
						public Activity getParentActivity() {
							if (fragment == null) {
								return null;
							}
							return fragment.getParentActivity();
						}

						@Override
						public int getCurrentAccount() {
							return currentAccount;
						}

						@Override
						public FrameLayout getLayoutContainer() {
							return bulletinContainer;
						}

						@Override
						public View getFragmentView() {
							return containerView;
						}

						@Override
						public Dialog showDialog(Dialog dialog) {
							dialog.show();
							return dialog;
						}
					};
					if (fragment != null) {
						overridenFragment.setParentFragment(fragment);
					}

					new EmojiPacksAlert(overridenFragment, getContext(), inputStickerSets) {
						@Override
						protected void onCloseByLink() {
							PremiumPreviewBottomSheet.this.dismiss();
						}
					}.show();
				});
			}
			titleView.setText(title, null);
			subtitleView.setText(AndroidUtilities.replaceTags(getContext().getString(R.string.TelegramPremiumUserStatusDialogSubtitle)));
		}
		else if (isEmojiStatus) {
			titleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.TelegramPremiumUserStatusDefaultDialogTitle, ContactsController.formatName(user.getFirst_name(), user.getLast_name()))));
			subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.TelegramPremiumUserStatusDialogSubtitle, ContactsController.formatName(user.getFirst_name(), user.getLast_name()))));
		}
		else if (giftTier != null) {
			if (isOutboundGift) {
				titleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumOutboundDialogTitleWithPlural, user != null ? user.getFirst_name() : "", LocaleController.formatPluralString("GiftMonths", giftTier.getMonths())), Theme.key_windowBackgroundWhiteBlueButton, null));
				subtitleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumOutboundDialogSubtitle, user != null ? user.getFirst_name() : ""), Theme.key_windowBackgroundWhiteBlueButton, null));
			}
			else {
				titleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserGiftedPremiumDialogTitleWithPlural, user != null ? user.getFirst_name() : "", LocaleController.formatPluralString("GiftMonths", giftTier.getMonths())), Theme.key_windowBackgroundWhiteBlueButton, null));
				subtitleView.setText(AndroidUtilities.replaceTags(getContext().getString(R.string.TelegramPremiumUserGiftedPremiumDialogSubtitle)));
			}
		}
		else {
			titleView.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.TelegramPremiumUserDialogTitle, ContactsController.formatName(user.getFirst_name(), user.getLast_name())), Theme.key_windowBackgroundWhiteBlueButton, null));
			subtitleView.setText(AndroidUtilities.replaceTags(getContext().getString(R.string.TelegramPremiumUserDialogSubtitle)));
		}
	}

	@Override
	protected CharSequence getTitle() {
		return getContext().getString(R.string.TelegramPremium);
	}

	@Override
	protected RecyclerListView.SelectionAdapter createAdapter() {
		return new Adapter();
	}

	private void measureGradient(int w, int h) {
		int yOffset = 0;
		for (int i = 0; i < premiumFeatures.size(); i++) {
			dummyCell.setData(premiumFeatures.get(i), false);
			dummyCell.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
			premiumFeatures.get(i).yOffset = yOffset;
			yOffset += dummyCell.getMeasuredHeight();
		}

		totalGradientHeight = yOffset;
	}

	@Override
	public void show() {
		super.show();
		NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 4);
		if (animateConfetti) {
			AndroidUtilities.runOnUIThread(() -> {
				try {
					container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
				}
				catch (Exception ignored) {
				}
				fireworksOverlay.start();
			}, 200);
		}
	}

	@Override
	public void dismiss() {
		super.dismiss();
		NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4);
		if (enterAnimator != null) {
			enterAnimator.cancel();
		}

		if (fireworksOverlay.isStarted()) {
			fireworksOverlay.animate().alpha(0).setDuration(150).start();
		}
	}

	@Override
	protected void mainContainerDispatchDraw(Canvas canvas) {
		if (overrideTitleIcon != null) {
			overrideTitleIcon.setVisibility(enterTransitionInProgress ? View.INVISIBLE : View.VISIBLE);
		}
		super.mainContainerDispatchDraw(canvas);
		if (startEnterFromView != null && enterTransitionInProgress) {
			View titleIcon = overrideTitleIcon == null ? iconTextureView : overrideTitleIcon;
			if (titleIcon == overrideTitleIcon) {
				overrideTitleIcon.setVisibility(View.VISIBLE);
			}

			canvas.save();

			float[] points = new float[]{startEnterFromX, startEnterFromY};
			startEnterFromView.getMatrix().mapPoints(points);
			Drawable startEnterFromDrawable = startEnterFromView.getRightDrawable();
			float cxFrom = -coords[0] + startEnterFromX1 + points[0];
			float cyFrom = -coords[1] + startEnterFromY1 + points[1];

			float fromSize = startEnterFromScale * startEnterFromDrawable.getIntrinsicWidth();
			float toSize = titleIcon.getMeasuredHeight() * 0.8f;
			float toSclale = toSize / fromSize;
			float bigIconFromScale = fromSize / toSize;

			float cxTo = titleIcon.getMeasuredWidth() / 2f;
			View view = titleIcon;
			while (view != container) {
				if (view == null) {
					break;
				}
				cxTo += view.getX();
				view = (View)view.getParent();
			}
			float cy = 0;
			float cyTo = cy + titleIcon.getY() + ((View)titleIcon.getParent()).getY() + ((View)titleIcon.getParent().getParent()).getY() + titleIcon.getMeasuredHeight() / 2f;

			float x = AndroidUtilities.lerp(cxFrom, cxTo, CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(enterTransitionProgress));
			float y = AndroidUtilities.lerp(cyFrom, cyTo, enterTransitionProgress);

			if (startEnterFromDrawable != null) {
				float s = startEnterFromScale * (1f - enterTransitionProgress) + toSclale * enterTransitionProgress;
				canvas.save();
				canvas.scale(s, s, x, y);
				startEnterFromDrawable.setBounds((int)x - startEnterFromDrawable.getIntrinsicWidth() / 2, (int)y - startEnterFromDrawable.getIntrinsicHeight() / 2, (int)x + startEnterFromDrawable.getIntrinsicWidth() / 2, (int)y + startEnterFromDrawable.getIntrinsicHeight() / 2);
				startEnterFromDrawable.setAlpha((int)(255 * (1f - Utilities.clamp(enterTransitionProgress, 1, 0))));
				startEnterFromDrawable.draw(canvas);
				startEnterFromDrawable.setAlpha(0);
				canvas.restore();

				s = AndroidUtilities.lerp(bigIconFromScale, 1, enterTransitionProgress);
				canvas.scale(s, s, x, y);
				canvas.translate(x - titleIcon.getMeasuredWidth() / 2f, y - titleIcon.getMeasuredHeight() / 2f);
				//  canvas.saveLayerAlpha(0, 0, titleIcon.getMeasuredWidth(), titleIcon.getMeasuredHeight(), (int) (255 * enterTransitionProgress), Canvas.ALL_SAVE_FLAG);
				titleIcon.draw(canvas);
				// canvas.restore();
			}
			canvas.restore();
		}
	}

	@Override
	protected boolean onCustomOpenAnimation() {
		if (startEnterFromView == null) {
			return false;
		}
		enterAnimator = ValueAnimator.ofFloat(0, 1f);
		enterTransitionProgress = 0f;
		enterTransitionInProgress = true;
		iconContainer.invalidate();
		startEnterFromView.getRightDrawable().setAlpha(0);
		startEnterFromView.invalidate();
		if (iconTextureView != null) {
			iconTextureView.startEnterAnimation(-360, 100);
		}
		enterAnimator.addUpdateListener(animation -> {
			enterTransitionProgress = (float)animation.getAnimatedValue();
			container.invalidate();
		});
		enterAnimator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				enterTransitionInProgress = false;
				enterTransitionProgress = 1f;
				iconContainer.invalidate();
				ValueAnimator iconAlphaBack = ValueAnimator.ofInt(0, 255);
				Drawable drawable = startEnterFromView.getRightDrawable();
				iconAlphaBack.addUpdateListener(animation1 -> {
					drawable.setAlpha((Integer)animation1.getAnimatedValue());
					startEnterFromView.invalidate();
				});
				iconAlphaBack.start();
				super.onAnimationEnd(animation);
			}
		});
		enterAnimator.setDuration(600);
		enterAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
		enterAnimator.start();
		return super.onCustomOpenAnimation();
	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.groupStickersDidLoad) {
			if (statusStickerSet != null && statusStickerSet.id == (long)args[0]) {
				setTitle();
			}
		}
	}

	private class Adapter extends RecyclerListView.SelectionAdapter {

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view;
			Context context = parent.getContext();
			switch (viewType) {
				case 0:
					LinearLayout linearLayout = new LinearLayout(context) {
						@Override
						protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
							if (child == iconTextureView && enterTransitionInProgress) {
								return true;
							}
							return super.drawChild(canvas, child, drawingTime);
						}
					};
					iconContainer = linearLayout;
					linearLayout.setOrientation(LinearLayout.VERTICAL);
					if (overrideTitleIcon == null) {
						iconTextureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE) {
							@Override
							protected void onAttachedToWindow() {
								super.onAttachedToWindow();
								setPaused(false);
							}

							@Override
							protected void onDetachedFromWindow() {
								super.onDetachedFromWindow();
								setPaused(true);
							}
						};
						Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
						Canvas canvas = new Canvas(bitmap);
						canvas.drawColor(ColorUtils.blendARGB(getContext().getColor(R.color.brand), getContext().getColor(R.color.background), 0.5f));
						iconTextureView.setBackgroundBitmap(bitmap);
						// iconTextureView.mRenderer.colorKey1 = Theme.key_premiumGradient2;
						// iconTextureView.mRenderer.colorKey2 = Theme.key_premiumGradient1;
						iconTextureView.mRenderer.updateColors();
						linearLayout.addView(iconTextureView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL));
					}
					else {
						if (overrideTitleIcon.getParent() != null) {
							((ViewGroup)overrideTitleIcon.getParent()).removeView(overrideTitleIcon);
						}
						linearLayout.addView(overrideTitleIcon, LayoutHelper.createLinear(140, 140, Gravity.CENTER_HORIZONTAL, Gravity.CENTER, 10, 10, 10, 10));
					}

					if (titleView == null) {
						final ColorFilter colorFilter = new PorterDuffColorFilter(ColorUtils.setAlphaComponent(context.getColor(R.color.brand), 178), PorterDuff.Mode.MULTIPLY);
						titleView = new LinkSpanDrawable.LinksTextView(context) {
							AnimatedEmojiSpan.EmojiGroupedSpans stack;
							private Layout lastLayout;

							@Override
							protected void onDetachedFromWindow() {
								super.onDetachedFromWindow();
								AnimatedEmojiSpan.release(stack);
								lastLayout = null;
							}

							@Override
							protected void dispatchDraw(Canvas canvas) {
								super.dispatchDraw(canvas);
								if (lastLayout != getLayout()) {
									stack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, this, stack, lastLayout = getLayout());
								}
								AnimatedEmojiSpan.drawAnimatedEmojis(canvas, getLayout(), stack, 0, null, 0, 0, 0, 1f, colorFilter);
							}
						};
						titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
						titleView.setTypeface(Theme.TYPEFACE_BOLD);
						titleView.setGravity(Gravity.CENTER_HORIZONTAL);
						titleView.setTextColor(context.getColor(R.color.text));
						titleView.setLinkTextColor(context.getColor(R.color.brand));
					}
					if (titleView.getParent() != null) {
						((ViewGroup)titleView.getParent()).removeView(titleView);
					}
					linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_HORIZONTAL, 40, 0, 40, 0));

					if (subtitleView == null) {
						subtitleView = new TextView(context);
						subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
						subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
						subtitleView.setTextColor(context.getColor(R.color.text));
						subtitleView.setLinkTextColor(context.getColor(R.color.brand));
					}
					if (subtitleView.getParent() != null) {
						((ViewGroup)subtitleView.getParent()).removeView(subtitleView);
					}
					linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 9, 16, 20));

					setTitle();

					starParticlesView = new StarParticlesView(context) {
						@Override
						protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
							super.onMeasure(widthMeasureSpec, heightMeasureSpec);

							drawable.rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(52));
						}
					};
					FrameLayout frameLayout = new FrameLayout(context) {
						@Override
						protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
							super.onMeasure(widthMeasureSpec, heightMeasureSpec);
							float y = 0;
							if (iconTextureView != null) {
								y = iconTextureView.getTop() + iconTextureView.getMeasuredHeight() / 2f;
							}
							else if (overrideTitleIcon != null) {
								y = overrideTitleIcon.getTop() + overrideTitleIcon.getMeasuredHeight() / 2f;
							}
							starParticlesView.setTranslationY(y - starParticlesView.getMeasuredHeight() / 2f);
						}
					};
					frameLayout.setClipChildren(false);
					frameLayout.addView(starParticlesView);
					frameLayout.addView(linearLayout);

					starParticlesView.drawable.useGradient = true;
					starParticlesView.drawable.useBlur = false;
					starParticlesView.drawable.forceMaxAlpha = true;
					starParticlesView.drawable.checkBounds = true;
					starParticlesView.drawable.init();
					if (iconTextureView != null) {
						iconTextureView.setStarParticlesView(starParticlesView);
					}

					view = frameLayout;
					break;
				default:
				case 1:
					view = new PremiumFeatureCell(context) {
						@Override
						protected void dispatchDraw(Canvas canvas) {
							AndroidUtilities.rectTmp.set(imageView.getLeft(), imageView.getTop(), imageView.getRight(), imageView.getBottom());
							gradientTools.gradientMatrix(0, 0, getMeasuredWidth(), totalGradientHeight, 0, -data.yOffset);
							canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), gradientTools.paint);
							super.dispatchDraw(canvas);
						}
					};
					break;
				case 2:
					view = new ShadowSectionCell(context, 12, context.getColor(R.color.light_background));
					break;
				case 3:
					view = new View(context) {
						@Override
						protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
							super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(68), MeasureSpec.EXACTLY));
						}
					};
					break;
				case 4:
					view = new AboutPremiumView(context);
					break;
			}
			view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			if (position >= featuresStartRow && position < featuresEndRow) {
				((PremiumFeatureCell)holder.itemView).setData(premiumFeatures.get(position - featuresStartRow), position != featuresEndRow - 1);
			}
		}

		@Override
		public int getItemCount() {
			return rowCount;
		}

		@Override
		public int getItemViewType(int position) {
			if (position == paddingRow) {
				return 0;
			}
			else if (position >= featuresStartRow && position < featuresEndRow) {
				return 1;
			}
			else if (position == sectionRow) {
				return 2;
			}
			else if (position == buttonRow) {
				return 3;
			}
			else if (position == helpUsRow) {
				return 4;
			}
			return super.getItemViewType(position);
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder) {
			return holder.getItemViewType() == 1;
		}
	}
}
