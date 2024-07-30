/*
 * This is the source code of ElloApp
 *  for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.feed

import android.graphics.Color
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.R
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.addRipple
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper

class PhotoVideoAttachmentViewHolder(view: ConstraintLayout, private val contentWidth: Int, onClickListener: View.OnClickListener) : RecyclerView.ViewHolder(view) {
	private val videoContentIndicator = ImageView(view.context).apply {
		visibility = View.GONE
		setImageResource(R.drawable.feed_video_indicator)
		contentDescription = view.context.getString(R.string.cont_desc_video_content)
	}

	val imageView = ImageView(view.context).apply {
		scaleType = ImageView.ScaleType.FIT_CENTER
		id = imageViewId
	}

	private val imageReceiver = ImageReceiver(imageView).apply {
		setDelegate(object : ImageReceiver.ImageReceiverDelegate {
			override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
				imageReceiver.drawable?.let {
					imageView.setImageDrawable(it)

					if (isRoundVideo) {
						imageView.setBackgroundColor(Color.TRANSPARENT)
					}
					else {
						val bg = AndroidUtilities.calcDrawableColor(it)?.firstOrNull()

						if (bg != null) {
							imageView.setBackgroundColor(bg)
						}
						else {
							imageView.setBackgroundColor(Color.TRANSPARENT)
						}
					}
				}
			}
		})
	}

	fun setScaleType(scaleType: ImageView.ScaleType?) {
		imageView.scaleType = scaleType ?: ImageView.ScaleType.FIT_CENTER
	}

	private val isVideo: Boolean
		get() = messageObject?.isVideo == true || messageObject?.isRoundVideo == true || messageObject?.isYouTubeVideo == true

	private val isRoundVideo: Boolean
		get() = messageObject?.isRoundVideo == true

	var messageObject: MessageObject? = null
		set(value) {
			field = value

			imageReceiver.stopAnimation()

			imageReceiver.cancelLoadImage()
			imageReceiver.setImage(null, null, null, null, 0L, null, null, 1)

			imageView.gone()
			imageView.setImageDrawable(null)
			imageView.setBackgroundResource(R.color.light_background)

			videoContentIndicator.gone()

			val messageObject = value ?: return
			val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, contentWidth, true)

			if (isVideo) {
				videoContentIndicator.visible()
			}
			else {
				videoContentIndicator.gone()
			}

			imageReceiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), null, null, null, messageObject.document?.size ?: 0L, null, null, 1)

			imageView.visible()
		}

	init {
		imageView.addRipple(foreground = true)

		view.addView(imageView, LayoutHelper.createConstraint(0, 0).apply {
			startToStart = ConstraintLayout.LayoutParams.PARENT_ID
			endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
			topToTop = ConstraintLayout.LayoutParams.PARENT_ID
			bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
		})

		view.addView(videoContentIndicator, LayoutHelper.createConstraint(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT).apply {
			startToStart = ConstraintLayout.LayoutParams.PARENT_ID
			endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
			topToTop = ConstraintLayout.LayoutParams.PARENT_ID
			bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
		})

		view.foreground = Theme.createSimpleSelectorRoundRectDrawable(0, Color.TRANSPARENT, Theme.getColor(Theme.key_listSelector), Color.BLACK)

		view.setOnClickListener(onClickListener)
	}

	companion object {
		private val imageViewId = View.generateViewId()
	}
}
