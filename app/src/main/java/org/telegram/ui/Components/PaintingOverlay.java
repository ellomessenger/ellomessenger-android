/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Paint.Views.EditTextOutline;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;

public class PaintingOverlay extends FrameLayout {
	private Bitmap paintBitmap;
	private HashMap<View, VideoEditedInfo.MediaEntity> mediaEntityViews;
	private boolean ignoreLayout;
	private Drawable backgroundDrawable;

	public PaintingOverlay(Context context) {
		super(context);
	}

	public void setData(String paintPath, ArrayList<VideoEditedInfo.MediaEntity> entities, boolean isVideo, boolean startAfterSet) {
		setEntities(entities, isVideo, startAfterSet);
		if (paintPath != null) {
			paintBitmap = BitmapFactory.decodeFile(paintPath);
			setBackground(backgroundDrawable = new BitmapDrawable(getResources(), paintBitmap));
		}
		else {
			paintBitmap = null;
			setBackground(backgroundDrawable = null);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		ignoreLayout = true;
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
		if (mediaEntityViews != null) {
			int width = getMeasuredWidth();
			int height = getMeasuredHeight();
			for (int a = 0, N = getChildCount(); a < N; a++) {
				View child = getChildAt(a);
				VideoEditedInfo.MediaEntity entity = mediaEntityViews.get(child);
				if (entity == null) {
					continue;
				}
				if (child instanceof EditTextOutline) {
					child.measure(MeasureSpec.makeMeasureSpec(entity.viewWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
					float sc = entity.textViewWidth * width / entity.viewWidth;
					child.setScaleX(entity.scale * sc);
					child.setScaleY(entity.scale * sc);
				}
				else {
					child.measure(MeasureSpec.makeMeasureSpec((int)(width * entity.width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int)(height * entity.height), MeasureSpec.EXACTLY));
				}
			}
		}
		ignoreLayout = false;
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return false;
	}

	@Override
	public void requestLayout() {
		if (ignoreLayout) {
			return;
		}
		super.requestLayout();
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		if (mediaEntityViews != null) {
			int width = getMeasuredWidth();
			int height = getMeasuredHeight();
			for (int a = 0, N = getChildCount(); a < N; a++) {
				View child = getChildAt(a);
				VideoEditedInfo.MediaEntity entity = mediaEntityViews.get(child);
				if (entity == null) {
					continue;
				}
				int x, y;
				if (child instanceof EditTextOutline) {
					x = (int)(width * entity.textViewX) - child.getMeasuredWidth() / 2;
					y = (int)(height * entity.textViewY) - child.getMeasuredHeight() / 2;
				}
				else {
					x = (int)(width * entity.x);
					y = (int)(height * entity.y);
				}
				child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
			}
		}
	}

	public void reset() {
		paintBitmap = null;
		setBackground(backgroundDrawable = null);
		if (mediaEntityViews != null) {
			mediaEntityViews.clear();
		}
		removeAllViews();
	}

	public void showAll() {
		int count = getChildCount();
		for (int a = 0; a < count; a++) {
			getChildAt(a).setVisibility(VISIBLE);
		}
		setBackground(backgroundDrawable);
	}

	public void hideEntities() {
		int count = getChildCount();
		for (int a = 0; a < count; a++) {
			getChildAt(a).setVisibility(INVISIBLE);
		}
	}

	public void hideBitmap() {
		setBackground(null);
	}

	public void setEntities(ArrayList<VideoEditedInfo.MediaEntity> entities, boolean isVideo, boolean startAfterSet) {
		reset();
		mediaEntityViews = new HashMap<>();
		if (entities != null && !entities.isEmpty()) {
			for (int a = 0, N = entities.size(); a < N; a++) {
				VideoEditedInfo.MediaEntity entity = entities.get(a);
				View child = null;
				if (entity.type == 0) {
					BackupImageView imageView = new BackupImageView(getContext());
					imageView.setAspectFit(true);
					ImageReceiver imageReceiver = imageView.imageReceiver;
					if (isVideo) {
						imageReceiver.setAllowDecodeSingleFrame(true);
						imageReceiver.setAllowStartLottieAnimation(false);
						if (startAfterSet) {
							imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
								@Override
								public void onAnimationReady(@NonNull ImageReceiver imageReceiver) {
									// unused
								}

								@Override
								public void didSetImage(@NonNull ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
									if (set && !thumb) {
										RLottieDrawable drawable = imageReceiver.getLottieAnimation();
										if (drawable != null) {
											drawable.start();
										}
									}
								}
							});
						}
					}
					TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(entity.document.thumbs, 90);
					imageReceiver.setImage(ImageLocation.getForDocument(entity.document), null, ImageLocation.getForDocument(thumb, entity.document), null, "webp", entity.parentObject, 1);
					if ((entity.subType & 2) != 0) {
						imageView.setScaleX(-1);
					}
					entity.view = child = imageView;
				}
				else if (entity.type == 1) {
					EditTextOutline editText = new EditTextOutline(getContext()) {
						@Override
						public boolean dispatchTouchEvent(MotionEvent event) {
							return false;
						}

						@Override
						public boolean onTouchEvent(MotionEvent event) {
							return false;
						}
					};
					editText.setBackgroundColor(Color.TRANSPARENT);
					editText.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
					editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, entity.fontSize);
					editText.setText(entity.text);
					editText.setTypeface(Theme.TYPEFACE_BOLD);
					editText.setGravity(Gravity.CENTER);
					editText.setHorizontallyScrolling(false);
					editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
					editText.setFocusableInTouchMode(true);
					editText.setEnabled(false);
					editText.setInputType(editText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
					editText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
					if ((entity.subType & 1) != 0) {
						editText.setTextColor(0xffffffff);
						editText.setStrokeColor(entity.color);
						editText.setFrameColor(0);
						editText.setShadowLayer(0, 0, 0, 0);
					}
					else if ((entity.subType & 4) != 0) {
						editText.setTextColor(0xff000000);
						editText.setStrokeColor(0);
						editText.setFrameColor(entity.color);
						editText.setShadowLayer(0, 0, 0, 0);
					}
					else {
						editText.setTextColor(entity.color);
						editText.setStrokeColor(0);
						editText.setFrameColor(0);
						editText.setShadowLayer(5, 0, 1, 0x66000000);
					}
					entity.view = child = editText;
				}
				if (child != null) {
					addView(child);
					child.setRotation((float)(-entity.rotation / Math.PI * 180));
					mediaEntityViews.put(child, entity);
				}
			}
		}
	}

	public void setBitmap(Bitmap bitmap) {
		setBackground(backgroundDrawable = new BitmapDrawable(getResources(), paintBitmap = bitmap));
	}

	public Bitmap getBitmap() {
		return paintBitmap;
	}

	@Override
	public void setAlpha(float alpha) {
		super.setAlpha(alpha);
		if (backgroundDrawable != null) {
			backgroundDrawable.setAlpha((int)(255 * alpha));
		}
		final int count = getChildCount();
		for (int i = 0; i < count; ++i) {
			View child = getChildAt(i);
			if (child != null && child.getParent() == this) {
				child.setAlpha(alpha);
			}
		}
	}

	public Bitmap getThumb() {
		float w = getMeasuredWidth();
		float h = getMeasuredHeight();
		float scale = Math.max(w / AndroidUtilities.dp(120), h / AndroidUtilities.dp(120));
		Bitmap bitmap = Bitmap.createBitmap((int)(w / scale), (int)(h / scale), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.scale(1.0f / scale, 1.0f / scale);
		draw(canvas);
		return bitmap;
	}
}
