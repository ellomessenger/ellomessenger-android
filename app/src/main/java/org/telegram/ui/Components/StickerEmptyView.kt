/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.*
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_messages_stickerSet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear

open class StickerEmptyView @JvmOverloads constructor(context: Context, var progressView: View?, private var stickerType: Int, animationResource: Int = 0) : FrameLayout(context), NotificationCenterDelegate {
	private val linearLayout: LinearLayout
	private var progressBar: RadialProgressView? = null
	private var progressShowing = false
	private var color1 = 0
	var currentAccount = UserConfig.selectedAccount
	var keyboardSize = 0

	private val animationImageView = RLottieImageView(context).also {
		it.setAutoRepeat(true)
	}

	@JvmField
	val stickerView: BackupImageView

	@JvmField
	val title: TextView

	@JvmField
	val subtitle: TextView

	private var preventMoving = false
		set(value) {
			field = value

			if (!value) {
				linearLayout.translationY = 0f
				progressBar?.translationY = 0f
			}
		}

	private var showProgressRunnable = Runnable {
		progressView?.let { progressView ->
			if (progressView.visibility != VISIBLE) {
				progressView.visibility = VISIBLE
				progressView.alpha = 0f
			}

			progressView.animate().setListener(null).cancel()
			progressView.animate().alpha(1f).setDuration(150).start()
		} ?: run {
			progressBar?.animate()?.alpha(1f)?.scaleY(1f)?.scaleX(1f)?.setDuration(150)?.start()
		}
	}

	private var animateLayoutChange = false
	private var lastH = 0

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		if ((animateLayoutChange || preventMoving) && lastH > 0 && lastH != measuredHeight) {
			val y = (lastH - measuredHeight) / 2f

			linearLayout.translationY = linearLayout.translationY + y

			if (!preventMoving) {
				linearLayout.animate().translationY(0f).setInterpolator(CubicBezierInterpolator.DEFAULT).duration = 250
			}

			progressBar?.let { progressBar ->
				progressBar.translationY = progressBar.translationY + y

				if (!preventMoving) {
					progressBar.animate().translationY(0f).setInterpolator(CubicBezierInterpolator.DEFAULT).duration = 250
				}
			}
		}

		lastH = measuredHeight
	}

	init {
		stickerView = BackupImageView(context)
		stickerView.gone()

		stickerView.setOnClickListener {
			stickerView.imageReceiver.startAnimation()
		}

		linearLayout = object : LinearLayout(context) {
			override fun setVisibility(visibility: Int) {
				if (getVisibility() == GONE && visibility == VISIBLE) {
					setSticker()
					stickerView.imageReceiver.startAnimation()
					animationImageView.playAnimation()
				}
				else if (visibility == GONE) {
					stickerView.imageReceiver.clearImage()
				}

				super.setVisibility(visibility)
			}
		}

		linearLayout.setOrientation(LinearLayout.VERTICAL)

		title = TextView(context)
		title.typeface = Theme.TYPEFACE_BOLD
		title.setTextColor(context.getColor(R.color.text))
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
		title.gravity = Gravity.CENTER

		subtitle = TextView(context)
		subtitle.setTextColor(context.getColor(R.color.dark_gray))
		subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
		subtitle.gravity = Gravity.CENTER

		if (animationResource == 0) {
			linearLayout.addView(stickerView, createLinear(117, 117, Gravity.CENTER_HORIZONTAL))
		}
		else {
			animationImageView.setAnimation(animationResource, 160, 180)
			linearLayout.addView(animationImageView, createLinear(160, 180, Gravity.CENTER_HORIZONTAL))
		}

		linearLayout.addView(title, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0))
		linearLayout.addView(subtitle, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0))

		addView(linearLayout, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 12f, 0f, 12f, 30f))

		if (progressView == null) {
			progressBar = RadialProgressView(context)
			progressBar?.alpha = 0f
			progressBar?.scaleY = 0.5f
			progressBar?.scaleX = 0.5f

			addView(progressBar!!, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER))
		}
	}

	fun setColors(titleColor: Int, subtitleColor: Int, key1: Int) {
		title.setTextColor(titleColor)
		subtitle.setTextColor(subtitleColor)
		color1 = key1
	}

	override fun setVisibility(visibility: Int) {
		if (getVisibility() != visibility) {
			if (visibility == VISIBLE) {
				if (progressShowing) {
					linearLayout.animate().alpha(0f).scaleY(0.8f).scaleX(0.8f).setDuration(150).start()
					progressView?.visibility = VISIBLE
					progressView?.alpha = 1f
				}
				else {
					linearLayout.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(150).start()

					if (progressView != null) {
						progressView?.animate()?.setListener(null)?.cancel()

						progressView?.animate()?.setListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								progressView?.visibility = GONE
							}
						})?.alpha(0f)?.setDuration(150)?.start()
					}
					else {
						progressBar?.animate()?.alpha(0f)?.scaleY(0.5f)?.scaleX(0.5f)?.setDuration(150)?.start()
					}

					stickerView.imageReceiver.startAnimation()
					animationImageView.playAnimation()
				}
			}
		}

		super.setVisibility(visibility)

		if (getVisibility() == VISIBLE) {
			setSticker()
		}
		else {
			lastH = 0
			linearLayout.alpha = 0f
			linearLayout.scaleX = 0.8f
			linearLayout.scaleY = 0.8f

			if (progressView != null) {
				progressView?.animate()?.setListener(null)?.cancel()
				progressView?.animate()?.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						progressView?.visibility = GONE
					}
				})?.alpha(0f)?.setDuration(150)?.start()
			}
			else {
				progressBar?.alpha = 0f
				progressBar?.scaleX = 0.5f
				progressBar?.scaleY = 0.5f
			}

			stickerView.imageReceiver.stopAnimation()
			stickerView.imageReceiver.clearImage()
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (visibility == VISIBLE) {
			setSticker()
			animationImageView.playAnimation()
		}

		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad)
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad)
	}

	private fun setSticker() {
		var imageFilter: String? = null
		var document: TLRPC.Document? = null
		var set: TL_messages_stickerSet? = null

		if (stickerType == STICKER_TYPE_DONE) {
			document = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker("\uD83D\uDC4D")
		}
		else {
			set = MediaDataController.getInstance(currentAccount).getStickerSetByName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME)

			if (set == null) {
				set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME)
			}

			if (set != null && stickerType >= 0 && stickerType < set.documents.size) {
				document = set.documents[stickerType]
			}

			imageFilter = "130_130"
		}

		if (document != null) {
			val svgThumb = DocumentObject.getSvgThumb(document.thumbs, color1, 0.2f)
			svgThumb?.overrideWidthAndHeight(512, 512)

			val imageLocation = ImageLocation.getForDocument(document)

			stickerView.setImage(imageLocation, imageFilter, "tgs", svgThumb, set)

			if (stickerType == 9) {
				stickerView.imageReceiver.setAutoRepeat(1)
			}
			else {
				stickerView.imageReceiver.setAutoRepeat(2)
			}

			stickerView.visible()
		}
		else {
			stickerView.gone()
			MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, set == null)
			stickerView.imageReceiver.clearImage()
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.diceStickersDidLoad) {
			val name = args[0] as String

			if (AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME == name && visibility == VISIBLE) {
				setSticker()
			}
		}
	}

	fun setKeyboardHeight(keyboardSize: Int, animated: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (this.keyboardSize != keyboardSize) {
			if (visibility != VISIBLE) {
				animated = false
			}

			this.keyboardSize = keyboardSize

			val y = (-(keyboardSize shr 1) + if (keyboardSize > 0) AndroidUtilities.dp(20f) else 0).toFloat()

			if (animated) {
				linearLayout.animate().translationY(y).setInterpolator(CubicBezierInterpolator.DEFAULT).duration = 250
				progressBar?.animate()?.translationY(y)?.setInterpolator(CubicBezierInterpolator.DEFAULT)?.duration = 250
			}
			else {
				linearLayout.translationY = y
				progressBar?.translationY = y
			}
		}
	}

	@JvmOverloads
	fun showProgress(show: Boolean, animated: Boolean = true) {
		if (progressShowing != show) {
			progressShowing = show

			if (visibility != VISIBLE) {
				return
			}

			if (animated) {
				if (show) {
					linearLayout.animate().alpha(0f).scaleY(0.8f).scaleX(0.8f).setDuration(150).start()
					showProgressRunnable.run()
				}
				else {
					linearLayout.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(150).start()

					if (progressView != null) {
						progressView?.animate()?.setListener(null)?.cancel()

						progressView?.animate()?.setListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animation: Animator) {
								progressView?.visibility = GONE
							}
						})?.alpha(0f)?.setDuration(150)?.start()
					}
					else {
						progressBar?.animate()?.alpha(0f)?.scaleY(0.5f)?.scaleX(0.5f)?.setDuration(150)?.start()
					}

					stickerView.imageReceiver.startAnimation()
					animationImageView.playAnimation()
				}
			}
			else {
				if (show) {
					linearLayout.animate().cancel()
					linearLayout.alpha = 0f
					linearLayout.scaleX = 0.8f
					linearLayout.scaleY = 0.8f

					if (progressView != null) {
						progressView?.animate()?.setListener(null)?.cancel()
						progressView?.alpha = 1f
						progressView?.visibility = VISIBLE
					}
					else {
						progressBar?.alpha = 1f
						progressBar?.scaleX = 1f
						progressBar?.scaleY = 1f
					}
				}
				else {
					linearLayout.animate().cancel()
					linearLayout.alpha = 1f
					linearLayout.scaleX = 1f
					linearLayout.scaleY = 1f

					if (progressView != null) {
						progressView?.animate()?.setListener(null)?.cancel()
						progressView?.visibility = GONE
					}
					else {
						progressBar?.alpha = 0f
						progressBar?.scaleX = 0.5f
						progressBar?.scaleY = 0.5f
					}
				}
			}
		}
	}

	fun setAnimateLayoutChange(animate: Boolean) {
		animateLayoutChange = animate
	}

	fun setStickerType(stickerType: Int) {
		if (this.stickerType != stickerType) {
			this.stickerType = stickerType
			setSticker()
		}
	}

	companion object {
		const val STICKER_TYPE_NO_CONTACTS = 0
		const val STICKER_TYPE_SEARCH = 1
		const val STICKER_TYPE_DONE = 2
	}
}
