/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.SeekBar
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MediaController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.AudioLayoutBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.ui.Components.LayoutHelper
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class AudioLayout(val binding: AudioLayoutBinding) : FrameLayout(binding.root.context) {
	private var isSeeking = false

	private val imageReceiver = ImageReceiver(binding.audioImage).apply {
		setDelegate(object : ImageReceiver.ImageReceiverDelegate {
			override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
				updatePlayButton()
			}
		})
	}

	var messageObject: MessageObject? = null
		private set

	private fun updatePlayButton() {
		val artwork = imageReceiver.drawable

		if (artwork != null) {
			binding.audioImage.setImageDrawable(artwork)
			binding.audioPlayButton.setBackgroundColor(Color.parseColor("#44000000"))
		}

		if (MediaController.getInstance().isPlayingMessage(messageObject)) {
			if (MediaController.getInstance().isMessagePaused) {
				if (artwork == null) {
					binding.audioPlayButton.setBackgroundColor(context.getColor(R.color.brand))
				}

				binding.audioPlayButton.setImageResource(R.drawable.feed_play_button)
			}
			else {
				if (artwork == null) {
					binding.audioPlayButton.setBackgroundColor(Color.parseColor("#010101"))
				}

				binding.audioPlayButton.setImageResource(R.drawable.feed_pause_button)
			}
		}
		else {
			if (artwork == null) {
				binding.audioPlayButton.setBackgroundColor(context.getColor(R.color.brand))
			}

			binding.audioPlayButton.setImageResource(R.drawable.feed_play_button)
		}
	}

	fun updateMediaStatus() {
		updatePlayButton()

		if (MediaController.getInstance().isPlayingMessage(messageObject)) {
			val (duration, progress) = MediaController.getInstance().playingMessageObject.let {
				(it?.audioPlayerDuration ?: 0) to (it?.audioProgressSec ?: 0)
			}

			binding.seekbar.max = duration

			if (!isSeeking) {
				binding.seekbar.progress = progress
			}

			binding.elapsedTimeLabel.text = AndroidUtilities.formatShortDuration(progress)
			binding.remainingTimeLabel.text = context.getString(R.string.neg_x, AndroidUtilities.formatShortDuration(abs(progress - duration)))

			binding.elapsedTimeLabel.visible()
			binding.remainingTimeLabel.visible()
			binding.seekbar.visible()
		}
		else {
			binding.trackLabel.visible()
			binding.artistYearLabel.visible()
			binding.elapsedTimeLabel.gone()
			binding.remainingTimeLabel.gone()
			binding.seekbar.gone()
		}
	}

	fun setMessageObject(messageObject: MessageObject?, type: Int) {
		this.messageObject = messageObject

		if (messageObject == null) {
			isSeeking = false
			binding.seekbar.setOnSeekBarChangeListener(null)
			return
		}

		val artworkUrl = messageObject.getArtworkUrl(true)

		if (!artworkUrl.isNullOrEmpty()) {
			imageReceiver.setImage(artworkUrl, null, null, null, 0)
		}
		else {
			imageReceiver.setImage(null, null, null, null, 0)
		}

		updatePlayButton()

		binding.artistYearLabel.text = messageObject.musicAuthor?.trim()

		when (type) {
			FeedViewHolder.MEDIA_TYPE_VOICE -> {
				binding.trackLabel.text = binding.root.context.getString(R.string.voice_message)
			}

			else -> {
				binding.trackLabel.text = messageObject.musicTitle?.trim()
			}
		}

		binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				if (fromUser) {
					MediaController.getInstance().seekToProgress(messageObject, progress.toFloat() / seekBar.max.toFloat())
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {
				isSeeking = true
			}

			override fun onStopTrackingTouch(seekBar: SeekBar) {
				isSeeking = false
			}
		})

		updateMediaStatus()
	}

	init {
		clipChildren = false
		clipToPadding = false

		addView(binding.root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		binding.audioImageContainer.clipToOutline = true
		binding.audioImageContainer.outlineProvider = com.beint.elloapp.getOutlineProvider(AndroidUtilities.dp(35f / 2f).toFloat(), topCorners = true, bottomCorners = true)

		binding.audioPlayButton.setOnClickListener {
			messageObject?.let { messageObject ->
				if (MediaController.getInstance().isPlayingMessage(messageObject)) {
					if (MediaController.getInstance().isMessagePaused) {
						MediaController.getInstance().playMessage(messageObject)
					}
					else {
						MediaController.getInstance().pauseMessage(messageObject)
					}
				}
				else {
					MediaController.getInstance().playMessage(messageObject)
				}
			}
		}
	}
}
