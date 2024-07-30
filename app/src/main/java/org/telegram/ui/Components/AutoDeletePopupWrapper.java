/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;

public class AutoDeletePopupWrapper {
	private final ActionBarMenuSubItem disableItem;
	public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;
	View backItem;
	Callback callback;
	long lastDismissTime;

	public AutoDeletePopupWrapper(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback, boolean createBackground) {
		windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, createBackground ? R.drawable.popup_fixed_alert : 0);
		windowLayout.setFitItems(true);
		this.callback = callback;

		if (swipeBackLayout != null) {
			backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, LocaleController.getString("Back", R.string.Back), false);
			backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
		}

		ActionBarMenuSubItem item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_autodelete_1d, LocaleController.getString("AutoDelete1Day", R.string.AutoDelete1Day), false);
		item.setOnClickListener(view -> {
			dismiss();
			callback.setAutoDeleteHistory(24 * 60 * 60, UndoView.ACTION_AUTO_DELETE_ON);
		});
		item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_autodelete_1w, LocaleController.getString("AutoDelete7Days", R.string.AutoDelete7Days), false);
		item.setOnClickListener(view -> {
			dismiss();
			callback.setAutoDeleteHistory(7 * 24 * 60 * 60, UndoView.ACTION_AUTO_DELETE_ON);
		});
		item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_autodelete_1m, LocaleController.getString("AutoDelete1Month", R.string.AutoDelete1Month), false);
		item.setOnClickListener(view -> {
			dismiss();
			callback.setAutoDeleteHistory(31 * 24 * 60 * 60, UndoView.ACTION_AUTO_DELETE_ON);
		});
		item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_customize, LocaleController.getString("AutoDeleteCustom", R.string.AutoDeleteCustom), false);
		item.setOnClickListener(view -> {
			dismiss();
			AlertsCreator.createAutoDeleteDatePickerDialog(context, null, (notify, timeInMinutes) -> callback.setAutoDeleteHistory(timeInMinutes * 60, timeInMinutes == 0 ? UndoView.ACTION_AUTO_DELETE_OFF : UndoView.ACTION_AUTO_DELETE_ON));
		});
		disableItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_disable, LocaleController.getString("AutoDeleteDisable", R.string.AutoDeleteDisable), false);
		disableItem.setOnClickListener(view -> {
			dismiss();
			callback.setAutoDeleteHistory(0, UndoView.ACTION_AUTO_DELETE_OFF);
		});
		disableItem.setColors(Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogTextRed2));

		View gap = new FrameLayout(context);
		gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator));
		gap.setTag(R.id.fit_width_tag, 1);
		windowLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

		TextView textView = new TextView(context);
		textView.setTag(R.id.fit_width_tag, 1);
		textView.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(8), AndroidUtilities.dp(13), AndroidUtilities.dp(8));
		textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
		textView.setText(LocaleController.getString("AutoDeletePopupDescription", R.string.AutoDeletePopupDescription));
		windowLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
	}

	private void dismiss() {
		callback.dismiss();
		lastDismissTime = System.currentTimeMillis();
	}

	public void updateItems(int ttl) {
		if (System.currentTimeMillis() - lastDismissTime < 200) {
			AndroidUtilities.runOnUIThread(() -> updateItems(ttl));
			return;
		}
		if (ttl == 0) {
			disableItem.setVisibility(View.GONE);
		}
		else {
			disableItem.setVisibility(View.VISIBLE);
		}
	}

	public interface Callback {
		void dismiss();

		void setAutoDeleteHistory(int time, int action);
	}
}
