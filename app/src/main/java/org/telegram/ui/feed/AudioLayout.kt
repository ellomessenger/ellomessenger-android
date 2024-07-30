/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.widget.FrameLayout
import android.widget.SeekBar
import org.telegram.messenger.AndroidUtilities
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

	var messageObject: MessageObject? = null
		private set

	fun updateMediaStatus() {
		if (MediaController.getInstance().isPlayingMessage(messageObject)) {
			if (MediaController.getInstance().isMessagePaused) {
				binding.audioPlayButton.setImageResource(R.drawable.feed_play_button)
			}
			else {
				binding.audioPlayButton.setImageResource(R.drawable.feed_pause_button)
			}

			val (duration, progress) = MediaController.getInstance().playingMessageObject.let {
				(it?.audioPlayerDuration ?: 0) to (it?.audioProgressSec ?: 0)
			}

			binding.seekbar.max = duration

			if (!isSeeking) {
				binding.seekbar.progress = progress
			}

			binding.elapsedTimeLabel.text = AndroidUtilities.formatShortDuration(progress)
			binding.remainingTimeLabel.text = context.getString(R.string.neg_x, AndroidUtilities.formatShortDuration(abs(progress - duration)))

			binding.trackLabel.gone()
			binding.artistYearLabel.gone()
			binding.elapsedTimeLabel.visible()
			binding.remainingTimeLabel.visible()
			binding.seekbar.visible()
		}
		else {
			binding.audioPlayButton.setImageResource(R.drawable.feed_play_button)
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
