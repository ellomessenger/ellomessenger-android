/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.messenger

import androidx.annotation.IntRange
import org.telegram.messenger.ChatBotController.LastResultStore
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.ElloRpc.readData
import org.telegram.tgnet.TLRPC
import java.util.concurrent.ConcurrentHashMap

class ChatBotController(account: Int) : BaseController(account) {
	private val requestIds = mapOf(START_ID to 0, STOP_ID to 0, SUBSCRIBE_ID to 0, UNSUBSCRIBE_ID to 0, INFO_ID to 0, MODE_ID to 0).toMutableMap()

	var userSettingsUpdated = false
		private set

	var lastSubscriptionInfo: ElloRpc.SubscriptionInfoAiBot? = null
		private set

	private fun interface LastResultStore : (ElloRpc.SubscriptionInfoAiBot?) -> Unit

	private val storeLast = LastResultStore {
		lastSubscriptionInfo = it
	}

	/**
	 * @param isSubscription true - subscribe, false - buy a pack
	 * @param itemType 0 - text, 1 - image
	 */
	fun buyAiItem(isSubscription: Boolean, @IntRange(from = 0, to = 2) itemType: Int) {
		val requestParams = mapOf((true to 0) to 1, (true to 1) to 2, (false to 0) to 3, (false to 1) to 4, (false to 2) to 5)

		val request = ElloRpc.subscribeChatBotRequest(requestParams[(isSubscription to itemType)]!!)

		val reqId = connectionsManager.sendRequest(request, { resp, err ->
			AndroidUtilities.runOnUIThread {
				requestIds[SUBSCRIBE_ID] = 0

				if (err != null) {
					notificationCenter.postNotificationName(NotificationCenter.aiSubscriptionError, err)
				}
				else {
					if (resp is TLRPC.TL_biz_dataRaw) {
						val data = resp.readData<ElloRpc.SubscriptionInfoAiBot>()?.storeLastResponse(storeLast)
						userSettingsUpdated = true
						notificationCenter.postNotificationName(NotificationCenter.aiSubscriptionSuccess, data)
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		requestIds[SUBSCRIBE_ID] = reqId
	}

	fun cancelStartChatBot() {
		val id = requestIds[START_ID] ?: return

		if (id != 0) {
			connectionsManager.cancelRequest(id, false)
			requestIds.remove(START_ID)
		}
	}

	fun startChatBot(botId: Long) = getSubscriptionInfo(START_ID, botId)

	fun updateSubscriptionsInfo(botId: Long) = getSubscriptionInfo(INFO_ID, botId)

	// fun stopChatBot() = getSubscriptionInfo(STOP_ID)

	private fun getSubscriptionInfo(methodId: Int, botId: Long? = null) {
		val (req, id) = when (methodId) {
			START_ID -> Pair(ElloRpc.startChatBot(botId ?: 0L), NotificationCenter.aiBotStarted)
			INFO_ID -> Pair(ElloRpc.getSubscriptionsChatBotRequest(botId ?: 0L), NotificationCenter.aiSubscriptionStatusReceived)
			STOP_ID -> Pair(ElloRpc.stopChatBot(), NotificationCenter.aiBotStopped)
			else -> throw IllegalArgumentException()
		}

		val reqId = connectionsManager.sendRequest(req, { resp, error ->
			AndroidUtilities.runOnUIThread {
				requestIds[methodId] = 0

				if (error != null) {
					notificationCenter.postNotificationName(NotificationCenter.aiBotRequestFailed, error)
				}
				else {
					if (resp is TLRPC.TL_biz_dataRaw) {
						var hasUpdates = false
						val data = resp.readData<ElloRpc.SubscriptionInfoAiBot>()?.also { hasUpdates = it != lastSubscriptionInfo }?.storeLastResponse(storeLast)

						if (data != null) {
							if (hasUpdates) {
								val isNewBotPeer = !(data.isImgSubscriptionActive || data.isTextSubscriptionActive || data.imgExpireAt > 0 || data.textExpireAt > 0)
								userSettingsUpdated = !isNewBotPeer
							}

							notificationCenter.postNotificationName(id, data)
						}
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		requestIds[methodId] = reqId
	}

	companion object {
		const val START_ID = 0
		const val INFO_ID = 1
		const val STOP_ID = 2
		const val SUBSCRIBE_ID = 3
		const val UNSUBSCRIBE_ID = 4
		const val MODE_ID = 5

		private val instances = ConcurrentHashMap<Int, ChatBotController>(UserConfig.MAX_ACCOUNT_COUNT)

		@Synchronized
		fun getInstance(account: Int): ChatBotController {
			return instances[account] ?: ChatBotController(account).also {
				instances[account] = it
			}
		}
	}
}

private fun ElloRpc.SubscriptionInfoAiBot.storeLastResponse(delegate: (rest: ElloRpc.SubscriptionInfoAiBot?) -> Unit): ElloRpc.SubscriptionInfoAiBot {
	delegate(this)
	return this
}
