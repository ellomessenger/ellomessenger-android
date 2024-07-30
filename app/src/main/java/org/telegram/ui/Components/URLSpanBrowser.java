/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components;

import android.net.Uri;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;

import org.telegram.messenger.browser.Browser;

import androidx.annotation.Nullable;

public class URLSpanBrowser extends URLSpan {
	@Nullable
	private final TextStyleSpan.TextStyleRun style;

	public URLSpanBrowser(String url) {
		this(url, null);
	}

	public URLSpanBrowser(String url, @Nullable TextStyleSpan.TextStyleRun run) {
		super(url != null ? url.replace('\u202E', ' ') : url);
		style = run;
	}

	@Nullable
	public TextStyleSpan.TextStyleRun getStyle() {
		return style;
	}

	@Override
	public void onClick(View widget) {
		Uri uri = Uri.parse(getURL());
		Browser.openUrl(widget.getContext(), uri);
	}

	@Override
	public void updateDrawState(TextPaint p) {
		super.updateDrawState(p);

		if (style != null) {
			style.applyStyle(p);
		}

		p.setUnderlineText(true);
	}
}
