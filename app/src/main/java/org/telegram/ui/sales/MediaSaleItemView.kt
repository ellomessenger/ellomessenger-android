/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.sales

import android.annotation.SuppressLint
import android.widget.FrameLayout
import android.widget.ImageView
import com.beint.elloapp.FileHelper
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.R
import org.telegram.messenger.databinding.MediaSaleItemViewBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.Components.LayoutHelper

@SuppressLint("ViewConstructor")
class MediaSaleItemView(val binding: MediaSaleItemViewBinding, delegate: MediaSaleItemViewDelegate) : FrameLayout(binding.root.context) {
	private val imageReceiver = ImageReceiver(binding.icon).apply {
		setDelegate(object : ImageReceiver.ImageReceiverDelegate {
			override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
				val drawable = imageReceiver.drawable

				if (drawable == null) {
					fillPlaceholderImage()
				}
				else {
					binding.extLabel.gone()
					binding.extLabel.text = null
					binding.icon.scaleType = ImageView.ScaleType.CENTER_CROP
					binding.icon.setImageDrawable(drawable)
					binding.iconContainer.radius = AndroidUtilities.dp(8f).toFloat()
				}
			}
		})
	}

	private fun fillPlaceholderImage() {
		val isAudioFile = media?.let { FileHelper.isAudioFile(it) } ?: false

		binding.icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
		binding.iconContainer.radius = 0f

		if (isAudioFile) {
			binding.extLabel.gone()
			binding.extLabel.text = null
			binding.icon.setImageResource(R.drawable.media_sale_music)
		}
		else {
			val fileName = media?.substringAfterLast("/")?.trim()
			val mime = media?.let { FileHelper.getMimeType(it) }

			binding.icon.setImageResource(AndroidUtilities.getThumbForNameOrMime(fileName, mime, true))

			binding.extLabel.text = fileName?.substringAfterLast(".")?.lowercase()
			binding.extLabel.visible()
		}
	}

	var media: String? = null
		set(value) {
			field = value

			imageReceiver.cancelLoadImage()

			binding.titleLabel.text = value?.substringAfterLast("/")?.trim()

			if (value != null) {
				if (FileHelper.isImageFile(value)) {
					imageReceiver.setImage(ImageLocation.getForPath(value), null, null, null, 0L, null, null, 1)
				}
				else {
					fillPlaceholderImage()
				}
			}
			else {
				binding.icon.setImageResource(0)
			}
		}

	init {
		binding.deleteButton.setOnClickListener {
			media?.let { media ->
				delegate.onMediaSaleItemDeleteClicked(media)
			}
		}

		addView(binding.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50f))
	}

	fun interface MediaSaleItemViewDelegate {
		fun onMediaSaleItemDeleteClicked(path: String)
	}
}
