/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components;

import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import androidx.core.content.res.ResourcesCompat;

public class URLSpanMono extends MetricAffectingSpan {
	private final CharSequence currentMessage;
	private final int currentStart;
	private final int currentEnd;
	private final byte currentType;
	private final TextStyleSpan.TextStyleRun style;

	public URLSpanMono(CharSequence message, int start, int end, byte type) {
		this(message, start, end, type, null);
	}

	public URLSpanMono(CharSequence message, int start, int end, byte type, TextStyleSpan.TextStyleRun run) {
		currentMessage = message;
		currentStart = start;
		currentEnd = end;
		currentType = type;
		style = run;
	}

	public void copyToClipboard() {
		AndroidUtilities.addToClipboard(currentMessage.subSequence(currentStart, currentEnd).toString());
	}

	@Override
	public void updateMeasureState(TextPaint p) {
		p.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize - 1));
		p.setFlags(p.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);

		if (style != null) {
			style.applyStyle(p);
		}
		else {
			p.setTypeface(Theme.TYPEFACE_MONOSPACE);
		}
	}

	@Override
	public void updateDrawState(TextPaint p) {
		p.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize - 1));

		if (currentType == 2) {
			p.setColor(0xffffffff);
		}
		else if (currentType == 1) {
			p.setColor(ResourcesCompat.getColor(ApplicationLoader.applicationContext.getResources(), R.color.white, null));
		}
		else {
			p.setColor(ResourcesCompat.getColor(ApplicationLoader.applicationContext.getResources(), R.color.text, null));
		}

		if (style != null) {
			style.applyStyle(p);
		}
		else {
			p.setTypeface(Theme.TYPEFACE_MONOSPACE);
			p.setUnderlineText(false);
		}
	}
}
