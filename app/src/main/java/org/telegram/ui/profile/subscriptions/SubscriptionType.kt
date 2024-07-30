/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.profile.subscriptions

import org.telegram.tgnet.ElloRpc

enum class SubscriptionType(val value: Int) {
	ACTIVE_CHANNELS(ElloRpc.SUBSCRIPTION_TYPE_ACTIVE_CHANNELS), CANCELLED_CHANNELS(ElloRpc.SUBSCRIPTION_TYPE_CANCELLED_CHANNELS), ALL_CHANNELS(ElloRpc.SUBSCRIPTION_TYPE_ALL_CHANNELS), ALL(ElloRpc.SUBSCRIPTION_TYPE_ALL)
}
