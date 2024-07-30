/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController.PhotoEntry
import org.telegram.messenger.MediaController.SearchImage
import org.telegram.messenger.R
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.PhotoViewer

class PhotoAttachPhotoCell(context: Context) : FrameLayout(context) {
	private val backgroundPaint = Paint()
	private val container: FrameLayout
	private val videoInfoContainer: FrameLayout
	private val videoTextView: TextView
	private val zoomOnSelect = true
	private var animator: AnimatorSet? = null
	private var animatorSet: AnimatorSet? = null
	private var delegate: PhotoAttachPhotoCellDelegate? = null
	private var isLast = false
	private var isVertical = false
	private var itemSize: Int
	private var itemSizeChanged = false
	private var pressed = false
	private var searchEntry: SearchImage? = null

	var photoEntry: PhotoEntry? = null
		private set

	@JvmField
	val imageView: BackupImageView

	@JvmField
	val checkFrame: FrameLayout

	@JvmField
	val checkBox: CheckBox2

	init {
		setWillNotDraw(false)

		container = FrameLayout(context)

		addView(container, LayoutHelper.createFrame(80, 80f))

		imageView = BackupImageView(context)

		container.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		videoInfoContainer = object : FrameLayout(context) {
			private val rect = RectF()

			override fun onDraw(canvas: Canvas) {
				rect.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
				canvas.drawRoundRect(rect, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), Theme.chat_timeBackgroundPaint)
			}
		}

		videoInfoContainer.setWillNotDraw(false)
		videoInfoContainer.setPadding(AndroidUtilities.dp(5f), 0, AndroidUtilities.dp(5f), 0)

		container.addView(videoInfoContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 17f, Gravity.BOTTOM or Gravity.LEFT, 4f, 0f, 0f, 4f))

		val imageView1 = ImageView(context)
		imageView1.setImageResource(R.drawable.play_mini_video)

		videoInfoContainer.addView(imageView1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.CENTER_VERTICAL))

		videoTextView = TextView(context)
		videoTextView.setTextColor(-0x1)
		videoTextView.typeface = Theme.TYPEFACE_BOLD
		videoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
		videoTextView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

		videoInfoContainer.addView(videoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.CENTER_VERTICAL, 13f, -0.7f, 0f, 0f))

		checkBox = CheckBox2(context, 24)
		checkBox.setColor(0, context.getColor(R.color.brand), context.getColor(R.color.white))
		checkBox.visible()

		addView(checkBox, LayoutHelper.createFrame(26, 26f, Gravity.LEFT or Gravity.TOP, 52f, 4f, 0f, 0f))

		isFocusable = true

		checkFrame = FrameLayout(context)

		addView(checkFrame, LayoutHelper.createFrame(42, 42f, Gravity.LEFT or Gravity.TOP, 38f, 0f, 0f, 0f))

		itemSize = AndroidUtilities.dp(80f)
	}

	fun setIsVertical(value: Boolean) {
		isVertical = value
	}

	fun setItemSize(size: Int) {
		itemSize = size

		var layoutParams = container.layoutParams as LayoutParams
		layoutParams.height = itemSize
		layoutParams.width = layoutParams.height
		layoutParams = checkFrame.layoutParams as LayoutParams
		layoutParams.gravity = Gravity.LEFT or Gravity.TOP
		layoutParams.leftMargin = 0
		layoutParams = checkBox.layoutParams as LayoutParams
		layoutParams.gravity = Gravity.LEFT or Gravity.TOP
		layoutParams.topMargin = AndroidUtilities.dp(5f)
		layoutParams.leftMargin = layoutParams.topMargin

		itemSizeChanged = true
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (itemSizeChanged) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(5f), MeasureSpec.EXACTLY))
		}
		else {
			if (isVertical) {
				super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((80 + if (isLast) 0 else 6).toFloat()), MeasureSpec.EXACTLY))
			}
			else {
				super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((80 + if (isLast) 0 else 6).toFloat()), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80f), MeasureSpec.EXACTLY))
			}
		}
	}

	val scale: Float
		get() = container.scaleX

	fun getVideoInfoContainer(): View {
		return videoInfoContainer
	}

	fun setPhotoEntry(entry: PhotoEntry?, needCheckShow: Boolean, last: Boolean) {
		pressed = false

		val photoEntry = entry.also { this.photoEntry = it }

		isLast = last

		if (photoEntry?.isVideo == true) {
			imageView.setOrientation(0, true)
			videoInfoContainer.visible()
			videoTextView.text = AndroidUtilities.formatShortDuration(photoEntry.duration)
		}
		else {
			videoInfoContainer.invisible()
		}

		if (photoEntry?.thumbPath != null) {
			imageView.setImage(photoEntry.thumbPath, null, Theme.chat_attachEmptyDrawable)
		}
		else if (photoEntry?.path != null) {
			if (photoEntry.isVideo) {
				imageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable)
			}
			else {
				imageView.setOrientation(photoEntry.orientation, true)
				imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable)
			}
		}
		else {
			imageView.setImageDrawable(Theme.chat_attachEmptyDrawable)
		}

		val showing = needCheckShow && PhotoViewer.isShowingImage(photoEntry?.path)

		imageView.imageReceiver.setVisible(!showing, true)
		checkBox.alpha = if (showing) 0f else 1f

		videoInfoContainer.alpha = if (showing) 0f else 1f

		requestLayout()
	}

	fun setPhotoEntry(searchImage: SearchImage, needCheckShow: Boolean, last: Boolean) {
		pressed = false
		searchEntry = searchImage
		isLast = last

		val thumb = if (zoomOnSelect) Theme.chat_attachEmptyDrawable else ResourcesCompat.getDrawable(resources, R.drawable.nophotos, null)

		if (searchImage.thumbPhotoSize != null) {
			imageView.setImage(ImageLocation.getForPhoto(searchImage.thumbPhotoSize, searchImage.photo), null, thumb, searchImage)
		}
		else if (searchImage.photoSize != null) {
			imageView.setImage(ImageLocation.getForPhoto(searchImage.photoSize, searchImage.photo), "80_80", thumb, searchImage)
		}
		else if (searchImage.thumbPath != null) {
			imageView.setImage(searchImage.thumbPath, null, thumb)
		}
		else if (!searchImage.thumbUrl.isNullOrEmpty()) {
			val location = ImageLocation.getForPath(searchImage.thumbUrl)

			if (searchImage.type == 1 && searchImage.thumbUrl.endsWith("mp4")) {
				location?.imageType = FileLoader.IMAGE_TYPE_ANIMATION
			}

			imageView.setImage(location, null, thumb, searchImage)
		}
		else if (searchImage.document != null) {
			MessageObject.getDocumentVideoThumb(searchImage.document)
			val videoSize = MessageObject.getDocumentVideoThumb(searchImage.document)

			if (videoSize != null) {
				val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(searchImage.document.thumbs, 90)
				imageView.setImage(ImageLocation.getForDocument(videoSize, searchImage.document), null, ImageLocation.getForDocument(currentPhotoObject, searchImage.document), "52_52", null, -1, 1, searchImage)
			}
			else {
				val photoSize = FileLoader.getClosestPhotoSizeWithSize(searchImage.document.thumbs, 320)
				imageView.setImage(ImageLocation.getForDocument(photoSize, searchImage.document), null, thumb, searchImage)
			}
		}
		else {
			imageView.setImageDrawable(thumb)
		}

		val showing = needCheckShow && PhotoViewer.isShowingImage(searchImage.pathToAttach)

		imageView.imageReceiver.setVisible(!showing, true)
		checkBox.alpha = if (showing) 0.0f else 1.0f
		videoInfoContainer.alpha = if (showing) 0.0f else 1.0f

		requestLayout()
	}

	val isChecked: Boolean
		get() = checkBox.isChecked

	fun setChecked(num: Int, checked: Boolean, animated: Boolean) {
		checkBox.setChecked(num, checked, animated)

		if (itemSizeChanged) {
			animator?.cancel()
			animator = null

			if (animated) {
				animator = AnimatorSet()
				animator?.playTogether(ObjectAnimator.ofFloat(container, SCALE_X, if (checked) 0.787f else 1.0f), ObjectAnimator.ofFloat(container, SCALE_Y, if (checked) 0.787f else 1.0f))
				animator?.duration = 200

				animator?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						if (animator != null && animator == animation) {
							animator = null

							if (!checked) {
								setBackgroundColor(0)
							}
						}
					}

					override fun onAnimationCancel(animation: Animator) {
						if (animator != null && animator == animation) {
							animator = null
						}
					}
				})

				animator?.start()
			}
			else {
				container.scaleX = if (checked) 0.787f else 1.0f
				container.scaleY = if (checked) 0.787f else 1.0f
			}
		}
	}

	fun setNum(num: Int) {
		checkBox.setNum(num)
	}

	fun setOnCheckClickListener(onCheckClickListener: OnClickListener?) {
		checkFrame.setOnClickListener(onCheckClickListener)
	}

	fun setDelegate(delegate: PhotoAttachPhotoCellDelegate?) {
		this.delegate = delegate
	}

	fun callDelegate() {
		delegate?.onCheckClick(this)
	}

	fun showImage() {
		imageView.imageReceiver.setVisible(value = true, invalidate = true)
	}

	fun showCheck(show: Boolean) {
		if (show && checkBox.alpha == 1f || !show && checkBox.alpha == 0f) {
			return
		}

		animatorSet?.cancel()
		animatorSet = null

		animatorSet = AnimatorSet()
		animatorSet?.interpolator = DecelerateInterpolator()
		animatorSet?.duration = 180
		animatorSet?.playTogether(ObjectAnimator.ofFloat(videoInfoContainer, ALPHA, if (show) 1.0f else 0.0f), ObjectAnimator.ofFloat(checkBox, ALPHA, if (show) 1.0f else 0.0f))

		animatorSet?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				if (animation == animatorSet) {
					animatorSet = null
				}
			}
		})

		animatorSet?.start()
	}

	override fun clearAnimation() {
		super.clearAnimation()

		if (animator != null) {
			animator?.cancel()
			animator = null

			container.scaleX = if (checkBox.isChecked) 0.787f else 1.0f
			container.scaleY = if (checkBox.isChecked) 0.787f else 1.0f
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		var result = false

		checkFrame.getHitRect(rect)

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (rect.contains(event.x.toInt(), event.y.toInt())) {
				pressed = true
				invalidate()
				result = true
			}
		}
		else if (pressed) {
			if (event.action == MotionEvent.ACTION_UP) {
				parent.requestDisallowInterceptTouchEvent(true)

				pressed = false

				playSoundEffect(SoundEffectConstants.CLICK)
				sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)

				delegate?.onCheckClick(this)

				invalidate()
			}
			else if (event.action == MotionEvent.ACTION_CANCEL) {
				pressed = false
				invalidate()
			}
			else if (event.action == MotionEvent.ACTION_MOVE) {
				if (!rect.contains(event.x.toInt(), event.y.toInt())) {
					pressed = false
					invalidate()
				}
			}
		}

		if (!result) {
			result = super.onTouchEvent(event)
		}

		return result
	}

	override fun onDraw(canvas: Canvas) {
		if (checkBox.isChecked || container.scaleX != 1.0f || !imageView.imageReceiver.hasNotThumb() || imageView.imageReceiver.currentAlpha != 1.0f || photoEntry != null && PhotoViewer.isShowingImage(photoEntry!!.path) || searchEntry != null && PhotoViewer.isShowingImage(searchEntry!!.pathToAttach)) {
			backgroundPaint.color = context.getColor(R.color.light_background)
			canvas.drawRect(0f, 0f, imageView.measuredWidth.toFloat(), imageView.measuredHeight.toFloat(), backgroundPaint)
		}
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.isEnabled = true

		if (photoEntry?.isVideo == true) {
			info.text = context.getString(R.string.AttachVideo) + ", " + LocaleController.formatDuration(photoEntry?.duration ?: 0)
		}
		else {
			info.text = context.getString(R.string.AttachPhoto)
		}

		if (checkBox.isChecked) {
			info.isSelected = true
		}

		info.addAction(AccessibilityAction(R.id.acc_action_open_photo, context.getString(R.string.Open)))
	}

	override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
		if (action == R.id.acc_action_open_photo) {
			val parent = parent as View

			parent.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, left.toFloat(), (top + height - 1).toFloat(), 0))
			parent.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, left.toFloat(), (top + height - 1).toFloat(), 0))
		}

		return super.performAccessibilityAction(action, arguments)
	}

	fun interface PhotoAttachPhotoCellDelegate {
		fun onCheckClick(v: PhotoAttachPhotoCell)
	}

	companion object {
		private val rect = Rect()
	}
}
