/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tlrpc.User;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.GroupCreateCheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;

import java.util.Locale;

public class DrawerUserCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
	private final SimpleTextView textView;
	private final BackupImageView imageView;
	private final AvatarDrawable avatarDrawable;
	private final GroupCreateCheckBox checkBox;
	private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable status;

	private int accountNumber;
	private final RectF rect = new RectF();

	public DrawerUserCell(Context context) {
		super(context);

		avatarDrawable = new AvatarDrawable();
		avatarDrawable.setTextSize(AndroidUtilities.dp(12));

		imageView = new BackupImageView(context);
		imageView.setRoundRadius(AndroidUtilities.dp(18));
		addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 14, 6, 0, 0));

		textView = new SimpleTextView(context);
		textView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
		textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
		textView.setTextSize(15);
		textView.setTypeface(Theme.TYPEFACE_BOLD);
		textView.setMaxLines(1);
		textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
		addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 72, 0, 60, 0));

		status = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(textView, AndroidUtilities.dp(20));
		textView.setRightDrawable(status);

		checkBox = new GroupCreateCheckBox(context);
		checkBox.setChecked(true, false);
		checkBox.setCheckScale(0.9f);
		checkBox.setInnerRadDiff(AndroidUtilities.dp(1.5f));
		checkBox.setColorKeysOverrides(Theme.key_chats_unreadCounterText, Theme.key_chats_unreadCounter, Theme.key_chats_menuBackground);
		addView(checkBox, LayoutHelper.createFrame(18, 18, Gravity.LEFT | Gravity.TOP, 37, 27, 0, 0));

		setWillNotDraw(false);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
		for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
			NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
			NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.updateInterfaces);
		}
		NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
			NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
			NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.updateInterfaces);
		}
		NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);

		if (textView.getRightDrawable() instanceof AnimatedEmojiDrawable.WrapSizeDrawable) {
			Drawable drawable = ((AnimatedEmojiDrawable.WrapSizeDrawable)textView.getRightDrawable()).getDrawable();
			if (drawable instanceof AnimatedEmojiDrawable) {
				((AnimatedEmojiDrawable)drawable).removeView(textView);
			}
		}
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.currentUserPremiumStatusChanged) {
			if (account == accountNumber) {
				setAccount(accountNumber);
			}
		}
		else if (id == NotificationCenter.emojiLoaded) {
			textView.invalidate();
		}
		else if (id == NotificationCenter.updateInterfaces) {
			if (((int)args[0] & MessagesController.UPDATE_MASK_EMOJI_STATUS) > 0) {
				setAccount(accountNumber);
			}
		}
	}

	public void setAccount(int account) {
		accountNumber = account;
		User user = UserConfig.getInstance(accountNumber).getCurrentUser();
		if (user == null) {
			return;
		}
		avatarDrawable.setInfo(user);
		CharSequence text = ContactsController.formatName(user.getFirst_name(), user.getLast_name());
		try {
			text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
		}
		catch (Exception ignore) {
		}
		textView.setText(text);
		if (user.emoji_status instanceof TLRPC.TL_emojiStatusUntil && ((TLRPC.TL_emojiStatusUntil)user.emoji_status).until > (int)(System.currentTimeMillis() / 1000)) {
			textView.setDrawablePadding(AndroidUtilities.dp(4));
			status.set(((TLRPC.TL_emojiStatusUntil)user.emoji_status).document_id, true);
		}
		else if (user.emoji_status instanceof TLRPC.TL_emojiStatus) {
			textView.setDrawablePadding(AndroidUtilities.dp(4));
			status.set(((TLRPC.TL_emojiStatus)user.emoji_status).document_id, true);
		}
		else if (MessagesController.getInstance(account).isPremiumUser(user)) {
			textView.setDrawablePadding(AndroidUtilities.dp(6));
			status.set(PremiumGradient.getInstance().premiumStarDrawableMini, true);
		}
		else {
			status.set((Drawable)null, true);
		}
		status.setColor(getContext().getColor(R.color.brand));
		imageView.imageReceiver.setCurrentAccount(account);
		imageView.setForUserOrChat(user, avatarDrawable);
		checkBox.setVisibility(account == UserConfig.selectedAccount ? VISIBLE : INVISIBLE);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (UserConfig.getActivatedAccountsCount() <= 1 || !NotificationsController.getInstance(accountNumber).showBadgeNumber) {
			return;
		}
		int counter = MessagesStorage.getInstance(accountNumber).getMainUnreadCount();
		if (counter <= 0) {
			return;
		}

		String text = String.format(Locale.getDefault(), "%d", counter);
		int countTop = AndroidUtilities.dp(12.5f);
		int textWidth = (int)Math.ceil(Theme.dialogs_countTextPaint.measureText(text));
		int countWidth = Math.max(AndroidUtilities.dp(10), textWidth);
		int countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(25);

		int x = countLeft - AndroidUtilities.dp(5.5f);
		rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(14), countTop + AndroidUtilities.dp(23));
		canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.dialogs_countPaint);

		canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2, countTop + AndroidUtilities.dp(16), Theme.dialogs_countTextPaint);
	}

	@Override
	public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
		super.onInitializeAccessibilityNodeInfo(info);
		info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
	}
}