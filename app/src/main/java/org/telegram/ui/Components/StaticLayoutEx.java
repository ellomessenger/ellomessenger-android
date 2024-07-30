/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022.
 */

package org.telegram.ui.Components;

import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.lang.reflect.Constructor;

public class StaticLayoutEx {
	public static Layout.Alignment[] alignments = Layout.Alignment.values();
	private static boolean initialized;

	public static Layout.Alignment ALIGN_RIGHT() {
		return alignments.length >= 5 ? alignments[4] : Layout.Alignment.ALIGN_OPPOSITE;
	}

	public static Layout.Alignment ALIGN_LEFT() {
		return alignments.length >= 5 ? alignments[3] : Layout.Alignment.ALIGN_NORMAL;
	}

	public static void init() {
		if (initialized) {
			return;
		}

		try {
			final Class<?> textDirClass;
			textDirClass = TextDirectionHeuristic.class;
			final Class<?>[] signature = new Class[]{CharSequence.class, int.class, int.class, TextPaint.class, int.class, Layout.Alignment.class, textDirClass, float.class, float.class, boolean.class, TextUtils.TruncateAt.class, int.class, int.class};
			Constructor<StaticLayout> sConstructor = StaticLayout.class.getDeclaredConstructor(signature);
			sConstructor.setAccessible(true);
			initialized = true;
		}
		catch (Throwable e) {
			FileLog.e(e);
		}
	}

	public static StaticLayout createStaticLayout2(CharSequence source, TextPaint paint, Layout.Alignment align, float spacingmult, float spacingadd, boolean includepad, int ellipsisWidth, int maxLines) {
		StaticLayout.Builder builder = StaticLayout.Builder.obtain(source, 0, source.length(), paint, ellipsisWidth).setAlignment(align).setLineSpacing(spacingadd, spacingmult).setIncludePad(includepad).setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(ellipsisWidth).setMaxLines(maxLines).setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE);
		return builder.build();
	}

	public static StaticLayout createStaticLayout(CharSequence source, TextPaint paint, int width, Layout.Alignment align, float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize, int ellipsisWidth, int maxLines) {
		return createStaticLayout(source, paint, width, align, spacingmult, spacingadd, includepad, ellipsize, ellipsisWidth, maxLines, true);
	}

	public static StaticLayout createStaticLayout(CharSequence source, TextPaint paint, int outerWidth, Layout.Alignment align, float spacingMult, float spacingAdd, boolean includePad, TextUtils.TruncateAt ellipsize, int ellipsisWidth, int maxLines, boolean canContainUrl) {
		try {
			if (maxLines == 1) {
				CharSequence text = TextUtils.ellipsize(source, paint, ellipsisWidth, TextUtils.TruncateAt.END);
				return new StaticLayout(text, 0, text.length(), paint, outerWidth, align, spacingMult, spacingAdd, includePad);
			}
			else {
				StaticLayout.Builder builder = StaticLayout.Builder.obtain(source, 0, source.length(), paint, outerWidth).setAlignment(align).setLineSpacing(spacingAdd, spacingMult).setIncludePad(includePad).setEllipsize(null).setEllipsizedWidth(ellipsisWidth).setMaxLines(maxLines).setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE);
				StaticLayout layout = builder.build();

				if (layout.getLineCount() <= maxLines) {
					return layout;
				}
				else {
					int off;
					float left = layout.getLineLeft(maxLines - 1);
					float lineWidth = layout.getLineWidth(maxLines - 1);

					if (left != 0) {
						off = layout.getOffsetForHorizontal(maxLines - 1, left);
					}
					else {
						off = layout.getOffsetForHorizontal(maxLines - 1, lineWidth);
					}

					if (lineWidth < ellipsisWidth - AndroidUtilities.dp(10)) {
						off += 3;
					}

					SpannableStringBuilder stringBuilder = new SpannableStringBuilder(source.subSequence(0, Math.max(0, off - 3)));
					stringBuilder.append("\u2026");

					StaticLayout.Builder builder1 = StaticLayout.Builder.obtain(stringBuilder, 0, stringBuilder.length(), paint, outerWidth).setAlignment(align).setLineSpacing(spacingAdd, spacingMult).setIncludePad(includePad).setEllipsize(stringBuilder.getSpans(0, stringBuilder.length(), AnimatedEmojiSpan.class).length > 0 ? null : ellipsize).setEllipsizedWidth(ellipsisWidth).setMaxLines(maxLines).setBreakStrategy(canContainUrl ? StaticLayout.BREAK_STRATEGY_HIGH_QUALITY : StaticLayout.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE);
					return builder1.build();
				}
			}
		}
		catch (Exception e) {
			FileLog.e(e);
		}
		return null;
	}
}
