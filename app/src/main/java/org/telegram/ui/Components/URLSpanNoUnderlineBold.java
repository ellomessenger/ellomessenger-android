/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.text.TextPaint;

import org.telegram.ui.ActionBar.Theme;

import androidx.annotation.Nullable;

public class URLSpanNoUnderlineBold extends URLSpanNoUnderline {
	public URLSpanNoUnderlineBold(@Nullable String url) {
		super(url != null ? url.replace('\u202E', ' ') : null);
	}

	@Override
	public void updateDrawState(TextPaint ds) {
		super.updateDrawState(ds);
		ds.setTypeface(Theme.TYPEFACE_BOLD);
		ds.setUnderlineText(false);
	}
}
