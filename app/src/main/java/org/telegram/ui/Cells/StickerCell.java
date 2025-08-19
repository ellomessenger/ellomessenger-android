/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.messenger.messageobject.SendAnimationData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPCExtensions;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumLockIconView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

public class StickerCell extends FrameLayout {
	private final BackupImageView imageView;
	private TLRPC.Document sticker;
	private Object parentObject;
	private long lastUpdateTime;
	private boolean scaled;
	private float scale;
	private boolean clearsInputField;
	private final PremiumLockIconView premiumIconView;
	private boolean isPremiumSticker;

	public StickerCell(Context context) {
		super(context);

		imageView = new BackupImageView(context);
		imageView.setAspectFit(true);
		imageView.setLayerNum(1);
		addView(imageView, LayoutHelper.createFrame(66, 66, Gravity.CENTER_HORIZONTAL, 0, 5, 0, 0));
		setFocusable(true);

		premiumIconView = new PremiumLockIconView(context, PremiumLockIconView.TYPE_STICKERS_PREMIUM_LOCKED);
		premiumIconView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
		premiumIconView.setImageReceiver(imageView.imageReceiver);
		addView(premiumIconView, LayoutHelper.createFrame(24, 24, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(76) + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(78), MeasureSpec.EXACTLY));
	}

	@Override
	public void setPressed(boolean pressed) {
		if (imageView.imageReceiver.getPressed() != pressed) {
			imageView.imageReceiver.setPressed(pressed ? 1 : 0);
			imageView.invalidate();
		}
		super.setPressed(pressed);
	}

	public void setClearsInputField(boolean value) {
		clearsInputField = value;
	}

	public boolean isClearsInputField() {
		return clearsInputField;
	}

	public void setSticker(@Nullable TLRPC.Document document, @Nullable Object parent) {
		parentObject = parent;
		isPremiumSticker = MessageObject.isPremiumSticker(document);
		if (isPremiumSticker) {
			premiumIconView.setColor(getContext().getColor(R.color.background));
			premiumIconView.setWaitingImage();
		}
		if (document != null) {
			TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(TLRPCExtensions.getThumbs(document), 90);
			SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, ResourcesCompat.getColor(getContext().getResources(), R.color.light_background, null), 1.0f);
			if (MessageObject.canAutoplayAnimatedSticker(document)) {
				if (svgThumb != null) {
					imageView.setImage(ImageLocation.getForDocument(document), "80_80", null, svgThumb, parentObject);
				}
				else if (thumb != null) {
					imageView.setImage(ImageLocation.getForDocument(document), "80_80", ImageLocation.getForDocument(thumb, document), null, 0, parentObject);
				}
				else {
					imageView.setImage(ImageLocation.getForDocument(document), "80_80", null, null, parentObject);
				}
			}
			else {
				if (svgThumb != null) {
					if (thumb != null) {
						imageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", svgThumb, parentObject);
					}
					else {
						imageView.setImage(ImageLocation.getForDocument(document), null, "webp", svgThumb, parentObject);
					}
				}
				else {
					imageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", null, parentObject);
				}
			}
		}
		sticker = document;
		Drawable background = getBackground();
		if (background != null) {
			background.setAlpha(230);
			background.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_stickersHintPanel), PorterDuff.Mode.MULTIPLY));
		}
		updatePremiumStatus(false);
	}

	public TLRPC.Document getSticker() {
		return sticker;
	}

	public Object getParentObject() {
		return parentObject;
	}

	public void setScaled(boolean value) {
		scaled = value;
		lastUpdateTime = System.currentTimeMillis();
		invalidate();
	}

	public boolean showingBitmap() {
		return imageView.imageReceiver.getBitmap() != null;
	}

	public SendAnimationData getSendAnimationData() {
		ImageReceiver imageReceiver = imageView.imageReceiver;
		if (!imageReceiver.hasNotThumb()) {
			return null;
		}
		SendAnimationData data = new SendAnimationData();
		int[] position = new int[2];
		imageView.getLocationInWindow(position);
		data.x = imageReceiver.getCenterX() + position[0];
		data.y = imageReceiver.getCenterY() + position[1];
		data.width = imageReceiver.getImageWidth();
		data.height = imageReceiver.getImageHeight();
		return data;
	}

	@Override
	protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
		boolean result = super.drawChild(canvas, child, drawingTime);
		if (child == imageView && (scaled && scale != 0.8f || !scaled && scale != 1.0f)) {
			long newTime = System.currentTimeMillis();
			long dt = (newTime - lastUpdateTime);
			lastUpdateTime = newTime;
			if (scaled && scale != 0.8f) {
				scale -= dt / 400.0f;
				if (scale < 0.8f) {
					scale = 0.8f;
				}
			}
			else {
				scale += dt / 400.0f;
				if (scale > 1.0f) {
					scale = 1.0f;
				}
			}
			imageView.setScaleX(scale);
			imageView.setScaleY(scale);
			imageView.invalidate();
			invalidate();
		}
		return result;
	}

	@Override
	public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
		super.onInitializeAccessibilityNodeInfo(info);

		if (sticker == null) {
			return;
		}

		String emoji = null;

		if (sticker instanceof TLRPC.TLDocument document) {
			for (int a = 0; a < document.attributes.size(); a++) {
				TLRPC.DocumentAttribute attribute = document.attributes.get(a);

				if (attribute instanceof TLRPC.TLDocumentAttributeSticker) {
					emoji = attribute.alt != null && attribute.alt.length() > 0 ? attribute.alt : null;
				}
			}
		}

		if (emoji != null) {
			info.setText(emoji + " " + LocaleController.getString("AttachSticker", R.string.AttachSticker));
		}
		else {
			info.setText(LocaleController.getString("AttachSticker", R.string.AttachSticker));
		}
		info.setEnabled(true);
	}

	private void updatePremiumStatus(boolean animated) {
		FrameLayout.LayoutParams layoutParams = (LayoutParams)premiumIconView.getLayoutParams();

		if (!UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
			layoutParams.height = layoutParams.width = AndroidUtilities.dp(24);
			layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
			layoutParams.bottomMargin = layoutParams.rightMargin = 0;
			premiumIconView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
		}
		else {
			layoutParams.height = layoutParams.width = AndroidUtilities.dp(16);
			layoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
			layoutParams.bottomMargin = AndroidUtilities.dp(8);
			layoutParams.rightMargin = AndroidUtilities.dp(8);
			premiumIconView.setPadding(AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1));
		}
		premiumIconView.setLocked(!UserConfig.getInstance(UserConfig.selectedAccount).isPremium());
		AndroidUtilities.updateViewVisibilityAnimated(premiumIconView, isPremiumSticker, 0.9f, animated);
		invalidate();
	}
}
