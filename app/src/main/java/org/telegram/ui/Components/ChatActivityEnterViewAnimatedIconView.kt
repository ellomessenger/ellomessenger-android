package org.telegram.ui.Components

import android.content.Context
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R

open class ChatActivityEnterViewAnimatedIconView(context: Context) : RLottieImageView(context) {
	private val stateMap = object : HashMap<TransitState?, RLottieDrawable?>() {
		override fun get(key: TransitState?): RLottieDrawable? {
			if (key == null) {
				return null
			}

			val obj = super.get(key)

			if (obj == null) {
				val res = key.resource
				return RLottieDrawable(res, res.toString(), AndroidUtilities.dp(32f), AndroidUtilities.dp(32f))
			}

			return obj
		}
	}

	private var currentState: State? = null
	private var animatingState: TransitState? = null

	fun setState(state: State, animate: Boolean) {
		if (animate && state == currentState) {
			return
		}

		val fromState = currentState

		currentState = state

		if (!animate || fromState == null || getState(fromState, currentState) == null) {
			val drawable = stateMap[getAnyState(currentState)]

			if (drawable != null) {
				drawable.stop()
				drawable.setProgress(0f, false)
				setAnimation(drawable)
			}
		}
		else {
			val transitState = getState(fromState, currentState)

			if (transitState == animatingState) {
				return
			}

			animatingState = transitState

			val drawable = stateMap[transitState]

			if (drawable != null) {
				drawable.stop()
				drawable.setProgress(0f, false)
				drawable.setAutoRepeat(0)
				drawable.setOnAnimationEndListener { animatingState = null }

				setAnimation(drawable)

				AndroidUtilities.runOnUIThread {
					drawable.start()
				}
			}
		}

		when (state) {
			State.VOICE -> {
				contentDescription = context.getString(R.string.AccDescrVoiceMessage)
			}
			State.VIDEO -> {
				contentDescription = context.getString(R.string.AccDescrVideoMessage)
			}
			else -> {
				// unused
			}
		}
	}

	private fun getAnyState(from: State?): TransitState? {
		for (transitState in TransitState.values()) {
			if (transitState.firstState == from) {
				return transitState
			}
		}

		return null
	}

	private fun getState(from: State, to: State?): TransitState? {
		for (transitState in TransitState.values()) {
			if (transitState.firstState == from && transitState.secondState == to) {
				return transitState
			}
		}

		return null
	}

	private enum class TransitState(val firstState: State, val secondState: State, val resource: Int) {
		VOICE_TO_VIDEO(State.VOICE, State.VIDEO, R.raw.voice_to_video), STICKER_TO_KEYBOARD(State.STICKER, State.KEYBOARD, R.raw.sticker_to_keyboard), SMILE_TO_KEYBOARD(State.SMILE, State.KEYBOARD, R.raw.smile_to_keyboard), VIDEO_TO_VOICE(State.VIDEO, State.VOICE, R.raw.video_to_voice), KEYBOARD_TO_STICKER(State.KEYBOARD, State.STICKER, R.raw.keyboard_to_sticker), KEYBOARD_TO_GIF(State.KEYBOARD, State.GIF, R.raw.keyboard_to_gif), KEYBOARD_TO_SMILE(State.KEYBOARD, State.SMILE, R.raw.keyboard_to_smile), GIF_TO_KEYBOARD(State.GIF, State.KEYBOARD, R.raw.gif_to_keyboard), GIF_TO_SMILE(State.GIF, State.SMILE, R.raw.gif_to_smile), SMILE_TO_GIF(State.SMILE, State.GIF, R.raw.smile_to_gif), SMILE_TO_STICKER(State.SMILE, State.STICKER, R.raw.smile_to_sticker), STICKER_TO_SMILE(State.STICKER, State.SMILE, R.raw.sticker_to_smile);
	}

	enum class State {
		VOICE, VIDEO, STICKER, KEYBOARD, SMILE, GIF
	}
}
