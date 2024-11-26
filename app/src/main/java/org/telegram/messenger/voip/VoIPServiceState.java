/*
 * This is the source code of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Telegram, 2024.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger.voip;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tlrpc.User;

import javax.annotation.Nullable;

public interface VoIPServiceState {
	@Nullable
	User getUser();

	boolean isOutgoing();

	int getCallState();

	@Nullable
	TLRPC.PhoneCall getPrivateCall();

	default long getCallDuration() {
		return 0;
	}

	void acceptIncomingCall();

	void declineIncomingCall();

	void stopRinging();
}
