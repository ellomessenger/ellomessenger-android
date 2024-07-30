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
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class JoinSheetUserCell extends FrameLayout {
	private final BackupImageView imageView;
	private final TextView nameTextView;
	private final AvatarDrawable avatarDrawable = new AvatarDrawable();
	private final int[] result = new int[1];

	public JoinSheetUserCell(Context context) {
		super(context);

		imageView = new BackupImageView(context);
		imageView.setRoundRadius(AndroidUtilities.dp(27));
		addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

		nameTextView = new TextView(context);
		nameTextView.setTextColor(context.getColor(R.color.text));
		nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		nameTextView.setMaxLines(1);
		nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
		nameTextView.setLines(1);
		nameTextView.setSingleLine(true);
		nameTextView.setEllipsize(TextUtils.TruncateAt.END);
		addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 65, 6, 0));
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90), MeasureSpec.EXACTLY));
	}

	public void setUser(User user) {
		nameTextView.setText(ContactsController.formatName(user.getFirst_name(), user.getLast_name()));
		avatarDrawable.setInfo(user);
		imageView.setForUserOrChat(user, avatarDrawable);
	}

	public void setCount(int count) {
		nameTextView.setText("");
		avatarDrawable.setInfo(null, null, "+" + LocaleController.formatShortNumber(count, result));
		imageView.setImage(null, "50_50", avatarDrawable, null);
	}
}
