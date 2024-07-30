package org.telegram.messenger.voip;

public interface StateListener {
	default void onStateChanged(int state) {
	}

	default void onSignalBarsCountChanged(int count) {
	}

	default void onAudioSettingsChanged() {
	}

	default void onMediaStateUpdated(int audioState, int videoState) {
	}

	default void onCameraSwitch(boolean isFrontFace) {
	}

	default void onCameraFirstFrameAvailable() {
	}

	default void onVideoAvailableChange(boolean isAvailable) {
	}

	default void onScreenOnChange(boolean screenOn) {
	}
}
