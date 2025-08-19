/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components.ListView;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.telegram.ui.Components.RecyclerListView;

import androidx.annotation.NonNull;

public class RecyclerListViewWithOverlayDraw extends RecyclerListView {
	boolean invalidated;

	public RecyclerListViewWithOverlayDraw(Context context) {
		super(context);
	}

	@Override
	protected void dispatchDraw(@NonNull Canvas canvas) {
		invalidated = false;
		for (int i = 0; i < getChildCount(); i++) {
			if (getChildAt(i) instanceof OverlayView view) {
				canvas.save();
				canvas.translate(view.getX(), view.getY());
				view.preDraw(this, canvas);
				canvas.restore();
			}
		}
		super.dispatchDraw(canvas);
	}

	@Override
	public void invalidate() {
		if (invalidated) {
			return;
		}

		super.invalidate();

		invalidated = true;
	}

	public interface OverlayView {
		void preDraw(View view, Canvas canvas);

		float getX();

		float getY();
	}
}
