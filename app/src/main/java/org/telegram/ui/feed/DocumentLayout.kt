/*
 * This is the source code of ElloApp
 *  for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.os.Build
import android.widget.FrameLayout
import android.widget.ImageView
import com.beint.elloapp.MimeTypeMap
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.databinding.DocumentLayoutBinding
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper

@SuppressLint("ViewConstructor")
class DocumentLayout(val binding: DocumentLayoutBinding) : FrameLayout(binding.root.context) {
	var messageObject: MessageObject? = null
		set(value) {
			field = value

			val document = value?.document

			var imageReceiver = binding.thumb.tag as? ImageReceiver
			imageReceiver?.cancelLoadImage()

			binding.thumb.setImageDrawable(null)
			binding.fileNameLabel.text = null
			binding.fileSizeLabel.text = null

			if (document == null) {
				return
			}

			val filename = (document.file_name ?: document.file_name_fixed)?.trim()

			binding.fileNameLabel.text = filename ?: context.getString(R.string.document)
			binding.fileSizeLabel.text = AndroidUtilities.formatFileSize(document.size)

			if (imageReceiver == null) {
				imageReceiver = ImageReceiver(binding.thumb).apply {
					setDelegate(object : ImageReceiver.ImageReceiverDelegate {
						override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
							binding.thumb.setImageDrawable(imageReceiver.drawable)
						}
					})
				}.also {
					binding.thumb.tag = it
				}
			}

			val mimeType = document.mime_type?.trim()

			if (mimeType?.startsWith("image/") == true) {
				binding.typeLabel.gone()

				binding.thumb.scaleType = ImageView.ScaleType.CENTER_CROP

				val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(value.photoThumbs, AndroidUtilities.dp(118f))
				imageReceiver.setImage(ImageLocation.getForObject(currentPhotoObject, value.photoThumbsObject), null, null, null, 0L, null, null, 1)
			}
			else {
				binding.thumb.scaleType = ImageView.ScaleType.FIT_CENTER

				var extension: String? = null

				if (filename.isNullOrEmpty()) {
					if (!mimeType.isNullOrEmpty()) {
						extension = MimeTypeMap.singleton.getExtensionFromMimeType(mimeType)
					}
				}
				else {
					extension = MimeTypeMap.getFileExtensionFromUrl(filename)

					if (extension.isEmpty()) {
						extension = filename.substringAfterLast('.', "")
					}
				}

				if (extension.isNullOrEmpty()) {
					binding.typeLabel.gone()
				}
				else {
					binding.typeLabel.text = extension.trim('.').lowercase()
					binding.typeLabel.visible()
				}

				val res = AndroidUtilities.getThumbForNameOrMime(filename, mimeType, false)

				if (res != 0) {
					binding.thumb.setImageResource(res)
				}
				else {
					binding.thumb.setImageResource(R.drawable.media_doc_blue)
				}
			}
		}

	init {
		addView(binding.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			binding.typeLabel.typeface = Theme.TYPEFACE_LIGHT
		}
	}
}
