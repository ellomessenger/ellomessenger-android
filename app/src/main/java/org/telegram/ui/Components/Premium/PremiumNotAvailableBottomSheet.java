/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components.Premium;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class PremiumNotAvailableBottomSheet extends BottomSheet {
	public PremiumNotAvailableBottomSheet(BaseFragment fragment) {
		super(fragment.getParentActivity(), false);
		Context context = fragment.getParentActivity();
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);

		TextView title = new TextView(context);
		title.setGravity(Gravity.START);
		title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
		title.setTypeface(Theme.TYPEFACE_BOLD);

		linearLayout.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 16, 21, 0));

		TextView description = new TextView(context);
		description.setGravity(Gravity.START);
		description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		linearLayout.addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 15, 21, 16));

		TextView buttonTextView = new TextView(context);
		buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
		buttonTextView.setGravity(Gravity.CENTER);
		buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		buttonTextView.setTypeface(Theme.TYPEFACE_BOLD);
		buttonTextView.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 8));
		buttonTextView.setText(context.getString(R.string.InstallOfficialApp));

		buttonTextView.setOnClickListener(v -> {
			try {
				v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildVars.PLAYSTORE_APP_URL)));
			}
			catch (ActivityNotFoundException e) {
				FileLog.e(e);
			}
		});

		FrameLayout buttonContainer = new FrameLayout(context);
		buttonContainer.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
		buttonContainer.setBackgroundColor(context.getColor(R.color.background));
		linearLayout.addView(buttonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));

		title.setText(AndroidUtilities.replaceTags(context.getString(R.string.SubscribeToPremiumOfficialAppNeeded)));
		description.setText(AndroidUtilities.replaceTags(context.getString(R.string.SubscribeToPremiumOfficialAppNeededDescription)));
		ScrollView scrollView = new ScrollView(context);
		scrollView.addView(linearLayout);
		setCustomView(scrollView);
	}
}
