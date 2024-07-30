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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.tlrpc.User;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;

public class CheckBoxUserCell extends FrameLayout {
	private final TextView textView;
	private final BackupImageView imageView;
	private final CheckBoxSquare checkBox;
	private final AvatarDrawable avatarDrawable;
	private boolean needDivider;

	private User currentUser;

	public CheckBoxUserCell(Context context) {
		super(context);

		textView = new TextView(context);
		textView.setTextColor(context.getColor(R.color.text));
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		textView.setLines(1);
		textView.setMaxLines(1);
		textView.setSingleLine(true);
		textView.setEllipsize(TextUtils.TruncateAt.END);
		textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
		addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 94), 0, (LocaleController.isRTL ? 94 : 21), 0));

		avatarDrawable = new AvatarDrawable();
		imageView = new BackupImageView(context);
		imageView.setRoundRadius(AndroidUtilities.dp(36));
		addView(imageView, LayoutHelper.createFrame(36, 36, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 48, 7, 48, 0));

		checkBox = new CheckBoxSquare(context);
		addView(checkBox, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 21), 16, (LocaleController.isRTL ? 21 : 0), 0));
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
	}

	public void setTextColor(int color) {
		textView.setTextColor(color);
	}

	public User getCurrentUser() {
		return currentUser;
	}

	public void setUser(User user, boolean checked, boolean divider) {
		currentUser = user;
		textView.setText(ContactsController.formatName(user.getFirst_name(), user.getLast_name()));
		checkBox.setChecked(checked, false);
		avatarDrawable.setInfo(user);
		imageView.setForUserOrChat(user, avatarDrawable);
		needDivider = divider;
		setWillNotDraw(!divider);
	}

	public void setChecked(boolean checked, boolean animated) {
		checkBox.setChecked(checked, animated);
	}

	public boolean isChecked() {
		return checkBox.isChecked();
	}

	public TextView getTextView() {
		return textView;
	}

	public CheckBoxSquare getCheckBox() {
		return checkBox;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (needDivider) {
			canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
		}
	}
}
