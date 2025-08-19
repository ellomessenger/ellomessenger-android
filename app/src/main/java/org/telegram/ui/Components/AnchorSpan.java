/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components;

import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;

public class AnchorSpan extends MetricAffectingSpan {
	private final String name;

	public AnchorSpan(String n) {
		name = n.toLowerCase();
	}

	public String getName() {
		return name;
	}

	@Override
	public void updateMeasureState(@NonNull TextPaint p) {

	}

	@Override
	public void updateDrawState(TextPaint tp) {

	}
}
