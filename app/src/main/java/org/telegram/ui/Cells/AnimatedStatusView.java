/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Reactions.AnimatedEmojiEffect;
import org.telegram.ui.Components.Reactions.VisibleReaction;

import java.util.ArrayList;

import androidx.annotation.NonNull;

public class AnimatedStatusView extends View {
	private final int stateSize;
	private final int effectsSize;
	private final int renderedEffectsSize;
	private int animationUniq;
	private final ArrayList<Object> animations = new ArrayList<>();

	public AnimatedStatusView(Context context, int stateSize, int effectsSize) {
		super(context);
		this.stateSize = stateSize;
		this.effectsSize = effectsSize;
		this.renderedEffectsSize = effectsSize;
	}

	public AnimatedStatusView(Context context, int stateSize, int effectsSize, int renderedEffectsSize) {
		super(context);
		this.stateSize = stateSize;
		this.effectsSize = effectsSize;
		this.renderedEffectsSize = renderedEffectsSize;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int max = Math.max(renderedEffectsSize, Math.max(stateSize, effectsSize));
		super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(max), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(max), MeasureSpec.EXACTLY));
	}

	private float y1, y2;

	public void translate(float x, float y) {
		setTranslationX(x - getMeasuredWidth() / 2f);
		setTranslationY((this.y1 = y - getMeasuredHeight() / 2f) + this.y2);
	}

	public void translateY2(float y) {
		setTranslationY(this.y1 + (this.y2 = y));
	}

	@Override
	public void dispatchDraw(@NonNull Canvas canvas) {
		final int renderedEffectsSize = AndroidUtilities.dp(this.renderedEffectsSize);
		final int effectsSize = AndroidUtilities.dp(this.effectsSize);

		for (int i = 0; i < animations.size(); ++i) {
			Object animation = animations.get(i);

			if (animation instanceof ImageReceiver imageReceiver) {
				imageReceiver.setImageCoordinates((getMeasuredWidth() - effectsSize) / 2f, (getMeasuredHeight() - effectsSize) / 2f, effectsSize, effectsSize);
				imageReceiver.draw(canvas);
//                    if (imageReceiver.getLottieAnimation() != null && imageReceiver.getLottieAnimation().isRunning() && imageReceiver.getLottieAnimation().isLastFrame()) {
//                        imageReceiver.onDetachedFromWindow();
//                        animations.remove(imageReceiver);
//                    }
			}
			else if (animation instanceof AnimatedEmojiEffect effect) {
				effect.setBounds((int)((getMeasuredWidth() - renderedEffectsSize) / 2f), (int)((getMeasuredHeight() - renderedEffectsSize) / 2f), (int)((getMeasuredWidth() + renderedEffectsSize) / 2f), (int)((getMeasuredHeight() + renderedEffectsSize) / 2f));
				effect.draw(canvas);

				if (effect.done()) {
					effect.removeView(this);
					animations.remove(effect);
				}
			}
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		detach();
	}

	private void detach() {
		if (!animations.isEmpty()) {
			for (Object obj : animations) {
				if (obj instanceof ImageReceiver) {
					((ImageReceiver)obj).onDetachedFromWindow();
				}
				else if (obj instanceof AnimatedEmojiEffect) {
					((AnimatedEmojiEffect)obj).removeView(this);
				}
			}
		}
		animations.clear();
	}

	public void animateChange(VisibleReaction react) {
		if (react == null) {
			detach();
			return;
		}

		TLRPC.Document document = null;
		TLRPC.TLAvailableReaction r = null;

		if (react.emojicon != null) {
			r = MediaDataController.getInstance(UserConfig.selectedAccount).reactionsMap.get(react.emojicon);
		}

		if (r == null) {
			document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, react.documentId);

			if (document != null) {
				String emojicon = MessageObject.findAnimatedEmojiEmoticon(document, null);

				if (emojicon != null) {
					r = MediaDataController.getInstance(UserConfig.selectedAccount).reactionsMap.get(emojicon);
				}
			}
		}

		if (document == null && r != null) {
			ImageReceiver imageReceiver = new ImageReceiver();
			imageReceiver.setParentView(this);
			imageReceiver.setUniqueKeyPrefix(Integer.toString(animationUniq++));
			imageReceiver.setImage(ImageLocation.getForDocument(r.aroundAnimation), effectsSize + "_" + effectsSize + "_nolimit", null, "tgs", r, 1);
			imageReceiver.setAutoRepeat(0);
			imageReceiver.onAttachedToWindow();

			animations.add(imageReceiver);

			invalidate();
		}
		else {
			AnimatedEmojiDrawable drawable;
			if (document == null) {
				drawable = AnimatedEmojiDrawable.make(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, UserConfig.selectedAccount, react.documentId);
			}
			else {
				drawable = AnimatedEmojiDrawable.make(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, UserConfig.selectedAccount, document);
			}
			if (color != null) {
				drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
			}
			AnimatedEmojiEffect effect = AnimatedEmojiEffect.createFrom(drawable, false, !drawable.canOverrideColor());
			effect.setView(this);
			animations.add(effect);
			invalidate();
		}
	}

	private Integer color;

	public void setColor(int color) {
		this.color = color;
		final ColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
		for (int i = 0; i < animations.size(); ++i) {
			Object animation = animations.get(i);
			if (animation instanceof ImageReceiver) {
				((ImageReceiver)animation).setColorFilter(colorFilter);
			}
			else if (animation instanceof AnimatedEmojiEffect) {
				((AnimatedEmojiEffect)animation).animatedEmojiDrawable.setColorFilter(colorFilter);
			}
		}
	}
}
