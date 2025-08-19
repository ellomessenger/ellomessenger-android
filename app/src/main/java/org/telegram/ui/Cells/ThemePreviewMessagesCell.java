/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.Reactions.ReactionsEffectOverlay;
import org.telegram.ui.Components.Reactions.VisibleReaction;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class ThemePreviewMessagesCell extends LinearLayout {
	public final static int TYPE_REACTIONS_DOUBLE_TAP = 2;
	private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
	private BackgroundGradientDrawable.Disposable oldBackgroundGradientDisposable;
	private Drawable backgroundDrawable;
	private Drawable oldBackgroundDrawable;
	private final ChatMessageCell[] cells = new ChatMessageCell[2];
	private final Drawable shadowDrawable;
	private final int type;
	public BaseFragment fragment;

	@SuppressLint("ClickableViewAccessibility")
	public ThemePreviewMessagesCell(Context context, int type) {
		super(context);
		this.type = type;
		int currentAccount = UserConfig.selectedAccount;

		setWillNotDraw(false);
		setOrientation(LinearLayout.VERTICAL);
		setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11));

		shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);

		int date = (int)(System.currentTimeMillis() / 1000) - 60 * 60;

		MessageObject message1 = null;
		MessageObject message2 = null;
		if (type == TYPE_REACTIONS_DOUBLE_TAP) {
			var message = new TLRPC.TLMessage();
			message.message = LocaleController.getString("DoubleTapPreviewMessage", R.string.DoubleTapPreviewMessage);
			message.date = date + 60;
			message.dialogId = 1;
			message.flags = 259;

			var peerUser = new TLRPC.TLPeerUser();
			peerUser.userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();

			message.fromId = peerUser;
			message.id = 1;
			message.media = new TLRPC.TLMessageMediaEmpty();
			message.out = false;

			var peerId = new TLRPC.TLPeerUser();
			peerId.userId = 0;

			message.peerId = peerId;

			message1 = new MessageObject(UserConfig.selectedAccount, message, true, false);
			message1.resetLayout();
			message1.eventId = 1;
			message1.customName = LocaleController.getString("DoubleTapPreviewSenderName", R.string.DoubleTapPreviewSenderName);
			message1.customAvatarDrawable = ContextCompat.getDrawable(context, R.drawable.dino_pic);
		}
		else {
			var message = new TLRPC.TLMessage();
			if (type == 0) {
				message.message = LocaleController.getString("FontSizePreviewReply", R.string.FontSizePreviewReply);
			}
			else {
				message.message = LocaleController.getString("NewThemePreviewReply", R.string.NewThemePreviewReply);
			}
			String greeting = "\uD83D\uDC4B";
			int index = message.message.indexOf(greeting);
			if (index >= 0) {
				var entity = new TLRPC.TLMessageEntityCustomEmoji();
				entity.offset = index;
				entity.length = greeting.length();
				entity.documentId = 5386654653003864312L;
				message.entities.add(entity);
			}
			message.date = date + 60;
			message.dialogId = 1;
			message.flags = 259;

			var fromId = new TLRPC.TLPeerUser();
			fromId.userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();

			message.fromId = fromId;

			message.id = 1;
			message.media = new TLRPC.TLMessageMediaEmpty();
			message.out = true;

			var peerId = new TLRPC.TLPeerUser();
			peerId.userId = 0;

			message.peerId = peerId;
			MessageObject replyMessageObject = new MessageObject(UserConfig.selectedAccount, message, true, false);

			message = new TLRPC.TLMessage();

			if (type == 0) {
				message.message = LocaleController.getString("FontSizePreviewLine2", R.string.FontSizePreviewLine2);
			}
			else {
				String text = LocaleController.getString("NewThemePreviewLine3", R.string.NewThemePreviewLine3);
				StringBuilder builder = new StringBuilder(text);
				int index1 = text.indexOf('*');
				int index2 = text.lastIndexOf('*');
				if (index1 != -1 && index2 != -1) {
					builder.replace(index2, index2 + 1, "");
					builder.replace(index1, index1 + 1, "");
					var entityUrl = new TLRPC.TLMessageEntityTextUrl();
					entityUrl.offset = index1;
					entityUrl.length = index2 - index1 - 1;
					entityUrl.url = "https://ello.team";
					message.entities.add(entityUrl);
				}
				message.message = builder.toString();
			}
			String cool = "\uD83D\uDE0E";
			int index1 = message.message.indexOf(cool);
			if (index1 >= 0) {
				var entity = new TLRPC.TLMessageEntityCustomEmoji();
				entity.offset = index1;
				entity.length = cool.length();
				entity.documentId = 5373141891321699086L;
				message.entities.add(entity);
			}
			message.date = date + 960;
			message.dialogId = 1;
			message.flags = 259;

			fromId = new TLRPC.TLPeerUser();
			fromId.userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();

			message.fromId = fromId;
			message.id = 1;
			message.media = new TLRPC.TLMessageMediaEmpty();
			message.out = true;

			peerId = new TLRPC.TLPeerUser();
			peerId.userId = 0;

			message.peerId = peerId;
			message1 = new MessageObject(UserConfig.selectedAccount, message, true, false);
			message1.resetLayout();
			message1.eventId = 1;

			message = new TLRPC.TLMessage();
			if (type == 0) {
				message.message = LocaleController.getString("FontSizePreviewLine1", R.string.FontSizePreviewLine1);
			}
			else {
				message.message = LocaleController.getString("NewThemePreviewLine1", R.string.NewThemePreviewLine1);
			}
			message.date = date + 60;
			message.dialogId = 1;
			message.flags = 257 + 8;
			message.fromId = new TLRPC.TLPeerUser();
			message.id = 1;
			message.replyTo = new TLRPC.TLMessageReplyHeader();
			message.replyTo.replyToMsgId = 5;
			message.media = new TLRPC.TLMessageMediaEmpty();
			message.out = false;

			peerId = new TLRPC.TLPeerUser();
			peerId.userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();

			message.peerId = peerId;

			message2 = new MessageObject(UserConfig.selectedAccount, message, true, false);
			if (type == 0) {
				message2.customReplyName = LocaleController.getString("FontSizePreviewName", R.string.FontSizePreviewName);
			}
			else {
				message2.customReplyName = LocaleController.getString("NewThemePreviewName", R.string.NewThemePreviewName);
			}
			message2.eventId = 1;
			message2.resetLayout();
			message2.replyMessageObject = replyMessageObject;
		}

		for (int a = 0; a < cells.length; a++) {
			cells[a] = new ChatMessageCell(context) {
				private final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
					@Override
					public boolean onDoubleTap(@NonNull MotionEvent e) {
						boolean added = getMessageObject().selectReaction(VisibleReaction.fromEmojicon(MediaDataController.getInstance(currentAccount).getDoubleTapReaction()), false, false);
						setMessageObject(getMessageObject(), null, false, false);
						requestLayout();
						ReactionsEffectOverlay.removeCurrent(false);
						if (added) {
							ReactionsEffectOverlay.show(fragment, null, cells[1], null, e.getX(), e.getY(), VisibleReaction.fromEmojicon(MediaDataController.getInstance(currentAccount).getDoubleTapReaction()), currentAccount, ReactionsEffectOverlay.LONG_ANIMATION);
							ReactionsEffectOverlay.startAnimation();
						}
						getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
							@Override
							public boolean onPreDraw() {
								getViewTreeObserver().removeOnPreDrawListener(this);
								getTransitionParams().resetAnimation();
								getTransitionParams().animateChange();
								getTransitionParams().animateChange = true;
								getTransitionParams().animateChangeProgress = 0f;
								ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
								valueAnimator.addUpdateListener(valueAnimator1 -> {
									getTransitionParams().animateChangeProgress = (float)valueAnimator1.getAnimatedValue();
									invalidate();
								});
								valueAnimator.addListener(new AnimatorListenerAdapter() {
									@Override
									public void onAnimationEnd(Animator animation) {
										super.onAnimationEnd(animation);
										getTransitionParams().resetAnimation();
										getTransitionParams().animateChange = false;
										getTransitionParams().animateChangeProgress = 1f;
									}
								});
								valueAnimator.start();
								return false;
							}
						});

						return true;
					}
				});

				@Override
				public boolean onTouchEvent(@NonNull MotionEvent event) {
					gestureDetector.onTouchEvent(event);
					return true;
				}

				@Override
				protected void dispatchDraw(@NonNull Canvas canvas) {
					if (getAvatarImage() != null && getAvatarImage().getImageHeight() != 0) {
						getAvatarImage().setImageCoordinates(getAvatarImage().getImageX(), getMeasuredHeight() - getAvatarImage().getImageHeight() - AndroidUtilities.dp(4), getAvatarImage().getImageWidth(), getAvatarImage().getImageHeight());
						getAvatarImage().setRoundRadius((int)(getAvatarImage().getImageHeight() / 2f));
						getAvatarImage().draw(canvas);
					}
					else if (type == TYPE_REACTIONS_DOUBLE_TAP) {
						invalidate();
					}
					super.dispatchDraw(canvas);
				}
			};
			cells[a].setDelegate(new ChatMessageCellDelegate() {

			});
			cells[a].isChat = type == TYPE_REACTIONS_DOUBLE_TAP;
			cells[a].setFullyDraw(true);
			MessageObject messageObject = a == 0 ? message2 : message1;
			if (messageObject == null) {
				continue;
			}
			cells[a].setMessageObject(messageObject, null, false, false);
			addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
		}
	}

	public ChatMessageCell[] getCells() {
		return cells;
	}

	@Override
	public void invalidate() {
		super.invalidate();

		for (ChatMessageCell cell : cells) {
			cell.invalidate();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Drawable newDrawable = Theme.getCachedWallpaperNonBlocking();
		if (Theme.wallpaperLoadTask != null) {
			invalidate();
		}
		if (newDrawable != backgroundDrawable && newDrawable != null) {
			if (Theme.isAnimatingColor()) {
				oldBackgroundDrawable = backgroundDrawable;
				oldBackgroundGradientDisposable = backgroundGradientDisposable;
			}
			else if (backgroundGradientDisposable != null) {
				backgroundGradientDisposable.dispose();
				backgroundGradientDisposable = null;
			}
			backgroundDrawable = newDrawable;
		}
		for (int a = 0; a < 2; a++) {
			Drawable drawable = a == 0 ? oldBackgroundDrawable : backgroundDrawable;
			if (drawable == null) {
				continue;
			}
			int alpha;
			alpha = 255;
			if (alpha <= 0) {
				continue;
			}
			drawable.setAlpha(alpha);
			if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
				drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
				if (drawable instanceof BackgroundGradientDrawable) {
					final BackgroundGradientDrawable backgroundGradientDrawable = (BackgroundGradientDrawable)drawable;
					backgroundGradientDisposable = backgroundGradientDrawable.drawExactBoundsSize(canvas, this);
				}
				else {
					drawable.draw(canvas);
				}
			}
			else if (drawable instanceof BitmapDrawable) {
				BitmapDrawable bitmapDrawable = (BitmapDrawable)drawable;
				bitmapDrawable.setFilterBitmap(true);
				if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
					canvas.save();
					float scale = 2.0f / AndroidUtilities.density;
					canvas.scale(scale, scale);
					drawable.setBounds(0, 0, (int)Math.ceil(getMeasuredWidth() / scale), (int)Math.ceil(getMeasuredHeight() / scale));
				}
				else {
					int viewHeight = getMeasuredHeight();
					float scaleX = (float)getMeasuredWidth() / (float)drawable.getIntrinsicWidth();
					float scaleY = (float)(viewHeight) / (float)drawable.getIntrinsicHeight();
					float scale = Math.max(scaleX, scaleY);
					int width = (int)Math.ceil(drawable.getIntrinsicWidth() * scale);
					int height = (int)Math.ceil(drawable.getIntrinsicHeight() * scale);
					int x = (getMeasuredWidth() - width) / 2;
					int y = (viewHeight - height) / 2;
					canvas.save();
					canvas.clipRect(0, 0, width, getMeasuredHeight());
					drawable.setBounds(x, y, x + width, y + height);
				}
				drawable.draw(canvas);
				canvas.restore();
			}
			if (a == 0 && oldBackgroundDrawable != null) {
				if (oldBackgroundGradientDisposable != null) {
					oldBackgroundGradientDisposable.dispose();
					oldBackgroundGradientDisposable = null;
				}
				oldBackgroundDrawable = null;
				invalidate();
			}
		}
		shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
		shadowDrawable.draw(canvas);
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if (backgroundGradientDisposable != null) {
			backgroundGradientDisposable.dispose();
			backgroundGradientDisposable = null;
		}
		if (oldBackgroundGradientDisposable != null) {
			oldBackgroundGradientDisposable.dispose();
			oldBackgroundGradientDisposable = null;
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (type == TYPE_REACTIONS_DOUBLE_TAP) {
			return super.onInterceptTouchEvent(ev);
		}
		return false;
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (type == TYPE_REACTIONS_DOUBLE_TAP) {
			return super.dispatchTouchEvent(ev);
		}
		return false;
	}

	@Override
	protected void dispatchSetPressed(boolean pressed) {

	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (type == TYPE_REACTIONS_DOUBLE_TAP) {
			return super.onTouchEvent(event);
		}
		return false;
	}
}
