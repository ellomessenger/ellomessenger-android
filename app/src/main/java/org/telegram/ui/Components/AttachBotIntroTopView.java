package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

public class AttachBotIntroTopView extends View {
	private final static int ICONS_SIZE_DP = 42;
	private final static int ICONS_SIDE_PADDING = 24;
	private final ImageReceiver imageReceiver;
	private final Drawable attachDrawable;
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	public AttachBotIntroTopView(Context context) {
		super(context);

		imageReceiver = new ImageReceiver(this);
		imageReceiver.setAlpha(0);
		imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
			@Override
			public void onAnimationReady(@NonNull ImageReceiver imageReceiver) {
				// unused
			}

			@Override
			public void didSetImage(@NonNull ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
				ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(150);
				anim.addUpdateListener(animation -> {
					imageReceiver.setAlpha((Float)animation.getAnimatedValue());
					invalidate();
				});
				anim.start();
			}
		});

		attachDrawable = ContextCompat.getDrawable(context, R.drawable.input_attach).mutate().getConstantState().newDrawable();
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(AndroidUtilities.dp(3));
		paint.setStrokeCap(Paint.Cap.ROUND);
	}

	public void setAttachBot(TLRPC.TL_attachMenuBot bot) {
		TLRPC.TL_attachMenuBotIcon icon = MediaDataController.getStaticAttachMenuBotIcon(bot);
		if (icon != null) {
			imageReceiver.setImage(ImageLocation.getForDocument(icon.icon), "42_42", DocumentObject.getSvgThumb(icon.icon, ResourcesCompat.getColor(getContext().getResources(), R.color.dark_gray, null), 1.0f), "svg", bot, 0);
		}
	}

	public void setBackgroundColor(int color) {
		backgroundPaint.setColor(color);
	}

	public void setColor(int color) {
		attachDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
		paint.setColor(color);
		imageReceiver.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		imageReceiver.onAttachedToWindow();
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		imageReceiver.onDetachedFromWindow();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight() + AndroidUtilities.dp(6));
		canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), backgroundPaint);

		imageReceiver.setImageCoordinates(getWidth() / 2f - AndroidUtilities.dp(ICONS_SIDE_PADDING + ICONS_SIZE_DP), getHeight() / 2f - AndroidUtilities.dp(ICONS_SIZE_DP) / 2f, AndroidUtilities.dp(ICONS_SIZE_DP), AndroidUtilities.dp(ICONS_SIZE_DP));
		imageReceiver.draw(canvas);

		canvas.drawLine(getWidth() / 2f - AndroidUtilities.dp(8), getHeight() / 2f, getWidth() / 2f + AndroidUtilities.dp(8), getHeight() / 2f, paint);
		canvas.drawLine(getWidth() / 2f, getHeight() / 2f - AndroidUtilities.dp(8), getWidth() / 2f, getHeight() / 2f + AndroidUtilities.dp(8), paint);

		attachDrawable.setBounds(getWidth() / 2 + AndroidUtilities.dp(ICONS_SIDE_PADDING), getHeight() / 2 - AndroidUtilities.dp(ICONS_SIZE_DP) / 2, getWidth() / 2 + AndroidUtilities.dp(ICONS_SIDE_PADDING + ICONS_SIZE_DP), getHeight() / 2 + AndroidUtilities.dp(ICONS_SIZE_DP) / 2);
		attachDrawable.draw(canvas);
	}
}
