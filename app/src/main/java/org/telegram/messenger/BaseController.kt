/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger

import org.telegram.messenger.AccountInstance.Companion.getInstance
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.WalletHelper

open class BaseController(@JvmField protected val currentAccount: Int) {
	protected val accountInstance: AccountInstance = getInstance(currentAccount)

	protected val messagesController: MessagesController
		get() = accountInstance.messagesController
	protected val contactsController: ContactsController
		get() = accountInstance.contactsController
	protected val mediaDataController: MediaDataController
		get() = accountInstance.mediaDataController
	protected val connectionsManager: ConnectionsManager
		get() = accountInstance.connectionsManager
	protected val locationController: LocationController
		get() = accountInstance.locationController
	protected val notificationsController: NotificationsController
		get() = accountInstance.notificationsController
	val notificationCenter: NotificationCenter
		get() = accountInstance.notificationCenter
	protected val userConfig: UserConfig
		get() = accountInstance.userConfig
	protected val messagesStorage: MessagesStorage
		get() = accountInstance.messagesStorage
	protected val downloadController: DownloadController
		get() = accountInstance.downloadController
	protected val sendMessagesHelper: SendMessagesHelper
		get() = accountInstance.sendMessagesHelper
	protected val statsController: StatsController
		get() = accountInstance.statsController
	protected val fileLoader: FileLoader
		get() = accountInstance.fileLoader
	protected val fileRefController: FileRefController
		get() = accountInstance.fileRefController
	protected val memberRequestsController: MemberRequestsController
		get() = accountInstance.memberRequestsController
	protected val chatBotController: ChatBotController
		get() = accountInstance.chatBotController
	protected val walletHelper: WalletHelper
		get() = accountInstance.walletHelper
}
