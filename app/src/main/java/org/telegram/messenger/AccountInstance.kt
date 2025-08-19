/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger

import android.content.SharedPreferences
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.WalletHelper

class AccountInstance(@JvmField val currentAccount: Int) {
	val messagesController: MessagesController
		get() = MessagesController.getInstance(currentAccount)
	val messagesStorage: MessagesStorage
		get() = MessagesStorage.getInstance(currentAccount)
	val contactsController: ContactsController
		get() = ContactsController.getInstance(currentAccount)
	val mediaDataController: MediaDataController
		get() = MediaDataController.getInstance(currentAccount)
	val connectionsManager: ConnectionsManager
		get() = ConnectionsManager.getInstance(currentAccount)
	val notificationsController: NotificationsController
		get() = NotificationsController.getInstance(currentAccount)
	val notificationCenter: NotificationCenter
		get() = NotificationCenter.getInstance(currentAccount)
	val locationController: LocationController
		get() = LocationController.getInstance(currentAccount)
	val userConfig: UserConfig
		get() = UserConfig.getInstance(currentAccount)
	val downloadController: DownloadController
		get() = DownloadController.getInstance(currentAccount)
	val sendMessagesHelper: SendMessagesHelper
		get() = SendMessagesHelper.getInstance(currentAccount)
//	val secretChatHelper: SecretChatHelper
//		get() = SecretChatHelper.getInstance(currentAccount)
	val statsController: StatsController
		get() = StatsController.getInstance(currentAccount)
	val fileLoader: FileLoader
		get() = FileLoader.getInstance(currentAccount)
	val fileRefController: FileRefController
		get() = FileRefController.getInstance(currentAccount)
	val notificationsSettings: SharedPreferences
		get() = MessagesController.getNotificationsSettings(currentAccount)
	val memberRequestsController: MemberRequestsController
		get() = MemberRequestsController.getInstance(currentAccount)
	val chatBotController: ChatBotController
		get() = ChatBotController.getInstance(currentAccount)
	val walletHelper: WalletHelper
		get() = WalletHelper.getInstance(currentAccount)

	companion object {
		private val instances = arrayOfNulls<AccountInstance>(UserConfig.MAX_ACCOUNT_COUNT)

		@JvmStatic
		@Synchronized
		fun getInstance(num: Int): AccountInstance {
			var localInstance = instances[num]

			if (localInstance == null) {
				synchronized(AccountInstance::class.java) {
					localInstance = instances[num]

					if (localInstance == null) {
						localInstance = AccountInstance(num)
						instances[num] = localInstance
					}
				}
			}

			return localInstance!!
		}
	}
}
