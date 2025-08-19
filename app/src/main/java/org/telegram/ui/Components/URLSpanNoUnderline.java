/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components;

import android.net.Uri;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;

import androidx.annotation.Nullable;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;

import java.util.Locale;

public class URLSpanNoUnderline extends URLSpan {
	private boolean forceNoUnderline = false;
	private final TextStyleSpan.TextStyleRun style;
	private TLObject object;
	public String label; // Used to label video timestamps

	public URLSpanNoUnderline(@Nullable String url) {
		this(url, null);
	}

	public URLSpanNoUnderline(@Nullable String url, boolean forceNoUnderline) {
		this(url, null);
		this.forceNoUnderline = forceNoUnderline;
	}

	public URLSpanNoUnderline(@Nullable String url, TextStyleSpan.TextStyleRun run) {
		super(url != null ? url.replace('\u202E', ' ') : null);
		style = run;
	}

	@Override
	public void onClick(View widget) {
		String url = getURL();

		if (url.startsWith("@")) {
			Uri uri = Uri.parse(String.format(Locale.getDefault(), "https://%s/%s", ApplicationLoader.applicationContext.getString(R.string.domain), url.substring(1)));
			Browser.openUrl(widget.getContext(), uri);
		}
		else {
			Browser.openUrl(widget.getContext(), url);
		}
	}

	@Override
	public void updateDrawState(TextPaint p) {
		int l = p.linkColor;
		int c = p.getColor();

		super.updateDrawState(p);

		if (style != null) {
			style.applyStyle(p);
		}

		p.setUnderlineText(l == c && !forceNoUnderline);
	}

	public void setObject(TLObject spanObject) {
		this.object = spanObject;
	}

	public TLObject getObject() {
		return object;
	}
}
