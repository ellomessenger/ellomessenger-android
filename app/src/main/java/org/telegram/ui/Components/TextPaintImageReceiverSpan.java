/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPCExtensions;

import java.util.Locale;

import androidx.annotation.NonNull;

public class TextPaintImageReceiverSpan extends ReplacementSpan {
	private final ImageReceiver imageReceiver;
	private final int width;
	private final int height;
	private final boolean alignTop;

	public TextPaintImageReceiverSpan(View parentView, TLRPC.Document document, Object parentObject, int w, int h, boolean top, boolean invert) {
		String filter = String.format(Locale.US, "%d_%d_i", w, h);
		width = w;
		height = h;
		imageReceiver = new ImageReceiver(parentView);
		if (invert) {
			imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
				@Override
				public void onAnimationReady(@NonNull ImageReceiver imageReceiver) {
					// unused
				}

				@Override
				public void didSetImage(@NonNull ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
					if (!imageReceiver.canInvertBitmap()) {
						return;
					}
					float[] NEGATIVE = {-1.0f, 0, 0, 0, 255, 0, -1.0f, 0, 0, 255, 0, 0, -1.0f, 0, 255, 0, 0, 0, 1.0f, 0};
					imageReceiver.setColorFilter(new ColorMatrixColorFilter(NEGATIVE));
				}
			});
		}
		TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(TLRPCExtensions.getThumbs(document), 90);
		imageReceiver.setImage(ImageLocation.getForDocument(document), filter, ImageLocation.getForDocument(thumb, document), filter, -1, null, parentObject, 1);
		alignTop = top;
	}

	@Override
	public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
		if (fm != null) {
			if (alignTop) {
				int h = fm.descent - fm.ascent - AndroidUtilities.dp(4);
				fm.bottom = fm.descent = height - h;
				fm.top = fm.ascent = -h;
			}
			else {
				fm.top = fm.ascent = (-height / 2) - AndroidUtilities.dp(4);
				fm.bottom = fm.descent = height - (height / 2) - AndroidUtilities.dp(4);
			}
		}
		return width;
	}

	@Override
	public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
		canvas.save();
		if (alignTop) {
			imageReceiver.setImageCoordinates((int)x, top - 1, width, height);
		}
		else {
			int h = (bottom - AndroidUtilities.dp(4)) - top;
			imageReceiver.setImageCoordinates((int)x, top + (h - height) / 2, width, height);
		}
		imageReceiver.draw(canvas);
		canvas.restore();
	}
}