/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.feed

import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.databinding.ExploreViewHolderBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible

class ExploreViewHolder(private val binding: ExploreViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
	var delegate: ExploreAdapter.FeedExploreAdapterDelegate? = null

	private val imageReceiver = ImageReceiver(binding.imageView).apply {
		setDelegate(object : ImageReceiver.ImageReceiverDelegate {
			override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
				imageReceiver.drawable?.let {
					 binding.imageView.setImageDrawable(it)

					val bg = AndroidUtilities.calcDrawableColor(it)?.firstOrNull()

					if (bg != null) {
						binding.imageView.setBackgroundColor(bg)
					}
					else {
						binding.imageView.setBackgroundColor(Color.TRANSPARENT)
					}
				}
			}
		})
	}

	private var contentWidth: Int = 0
		get() {
			if (field == 0) {
				field = AndroidUtilities.getRealScreenSize().x / 3
			}

			return field
		}

	var messageObject: MessageObject? = null
		set(value) {
			field = value

			imageReceiver.cancelLoadImage()

			binding.imageView.setImageDrawable(null)
			binding.imageView.setBackgroundColor(Color.TRANSPARENT)

			val messageObject = value ?: return
			val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, contentWidth, true)

			if (isVideo) {
				binding.durationLabel.text = AndroidUtilities.formatShortDuration(messageObject.duration)
				binding.durationLabel.visible()
			}
			else {
				binding.durationLabel.gone()
			}

			imageReceiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), null, null, null, messageObject.document?.size ?: 0L, null, null, 1)
		}

	private val isVideo: Boolean
		get() = messageObject?.isVideo == true || messageObject?.isRoundVideo == true || messageObject?.isYouTubeVideo == true

	init {
		binding.root.setOnClickListener {
			messageObject?.let { message ->
				delegate?.onExploreItemClick(message)
			}
		}
	}
}
