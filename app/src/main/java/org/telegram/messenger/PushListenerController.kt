/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger

import android.os.SystemClock
import android.text.TextUtils
import android.util.Base64
import android.util.SparseBooleanArray
import androidx.annotation.IntDef
import androidx.collection.LongSparseArray
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Update
import org.telegram.ui.Components.ForegroundDetector
import java.util.Locale
import java.util.concurrent.CountDownLatch

object PushListenerController {
	const val PUSH_TYPE_FIREBASE = 2
	private val countDownLatch = CountDownLatch(1)

	@JvmStatic
	fun sendRegistrationToServer(@PushType pushType: Int, token: String?) {
		Utilities.stageQueue.postRunnable {
			ConnectionsManager.setRegId(token, pushType, SharedConfig.pushStringStatus)

			if (token == null) {
				return@postRunnable
			}

			var sendStat = false

			if (SharedConfig.pushStringGetTimeStart != 0L && SharedConfig.pushStringGetTimeEnd != 0L && (!SharedConfig.pushStatSent || !TextUtils.equals(SharedConfig.pushString, token))) {
				sendStat = true
				SharedConfig.pushStatSent = false
			}

			SharedConfig.pushString = token
			SharedConfig.pushType = pushType

			for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
				val userConfig = UserConfig.getInstance(a)
				userConfig.registeredForPush = false
				userConfig.saveConfig(false)

				if (userConfig.getClientUserId() != 0L) {
					if (sendStat) {
						val tag = if (pushType == PUSH_TYPE_FIREBASE) "fcm" else "hcm"
						val req = TLRPC.TLHelpSaveAppLog()

						var event = TLRPC.TLInputAppEvent()
						event.time = SharedConfig.pushStringGetTimeStart.toDouble()
						event.type = tag + "_token_request"
						event.peer = 0
						event.data = TLRPC.TLJsonNull()

						req.events.add(event)

						event = TLRPC.TLInputAppEvent()
						event.time = SharedConfig.pushStringGetTimeEnd.toDouble()
						event.type = tag + "_token_response"
						event.peer = SharedConfig.pushStringGetTimeEnd - SharedConfig.pushStringGetTimeStart
						event.data = TLRPC.TLJsonNull()
						req.events.add(event)

						sendStat = false

						ConnectionsManager.getInstance(a).sendRequest(req) { _, error ->
							if (error != null) {
								AndroidUtilities.runOnUIThread {
									SharedConfig.pushStatSent = true
									SharedConfig.saveConfig()
								}
							}
						}
					}

					AndroidUtilities.runOnUIThread {
						MessagesController.getInstance(a).registerForPush(pushType, token)
					}
				}
			}
		}
	}

	@JvmStatic
	fun processRemoteMessage(@PushType pushType: Int, data: String?, time: Long) {
		val tag = if (pushType == PUSH_TYPE_FIREBASE) "FCM" else "HCM"

		FileLog.d("$tag PRE START PROCESSING")

		val receiveTime = SystemClock.elapsedRealtime()

		AndroidUtilities.runOnUIThread {
			FileLog.d("$tag PRE INIT APP")

			ApplicationLoader.postInitApplication()

			FileLog.d("$tag POST INIT APP")

			Utilities.stageQueue.postRunnable {
				FileLog.d("$tag START PROCESSING")

				var currentAccount = -1
				var locKey: String? = null
				var jsonString: String? = null

				try {
					val bytes = Base64.decode(data, Base64.URL_SAFE)

					val buffer = NativeByteBuffer(bytes.size)
					buffer.writeBytes(bytes)
					buffer.position(0)

					if (SharedConfig.pushAuthKeyId == null) {
						SharedConfig.pushAuthKeyId = ByteArray(8)
						val authKeyHash = Utilities.computeSHA1(SharedConfig.pushAuthKey)
						System.arraycopy(authKeyHash, authKeyHash.size - 8, SharedConfig.pushAuthKeyId, 0, 8)
					}

					val inAuthKeyId = ByteArray(8)

					buffer.readBytes(inAuthKeyId, true)

					if (!SharedConfig.pushAuthKeyId.contentEquals(inAuthKeyId)) {
						onDecryptError()

						FileLog.d(String.format(Locale.US, "$tag DECRYPT ERROR 2 k1=%s k2=%s, key=%s", Utilities.bytesToHex(SharedConfig.pushAuthKeyId), Utilities.bytesToHex(inAuthKeyId), Utilities.bytesToHex(SharedConfig.pushAuthKey)))

						return@postRunnable
					}

					val messageKey = ByteArray(16)

					buffer.readBytes(messageKey, true)

					val messageKeyData = MessageKeyData.generateMessageKeyData(SharedConfig.pushAuthKey, messageKey, true, 2)

					Utilities.aesIgeEncryption(buffer.buffer, messageKeyData.aesKey, messageKeyData.aesIv, false, false, 24, bytes.size - 24)

					val messageKeyFull = Utilities.computeSHA256(SharedConfig.pushAuthKey, 88 + 8, 32, buffer.buffer, 24, buffer.buffer.limit())

					if (!Utilities.arraysEquals(messageKey, 0, messageKeyFull, 8)) {
						onDecryptError()

						FileLog.d(String.format("$tag DECRYPT ERROR 3, key = %s", Utilities.bytesToHex(SharedConfig.pushAuthKey)))

						return@postRunnable
					}

					val len = buffer.readInt32(true)

					val strBytes = ByteArray(len)

					buffer.readBytes(strBytes, true)

					jsonString = String(strBytes)

					val json = JSONObject(jsonString)

					locKey = if (json.has("loc_key")) {
						json.getString("loc_key")
					}
					else {
						""
					}

					val `object` = json["custom"]

					val custom = if (`object` is JSONObject) {
						json.getJSONObject("custom")
					}
					else {
						JSONObject()
					}

					val userIdObject = if (json.has("user_id")) {
						json["user_id"]
					}
					else {
						null
					}

					val accountUserId = if (userIdObject == null) {
						UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()
					}
					else {
						when (userIdObject) {
							is Long -> userIdObject
							is Int -> userIdObject.toLong()
							is String -> Utilities.parseInt(userIdObject as? String).toLong()
							else -> UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()
						}
					}

					var account = UserConfig.selectedAccount
					var foundAccount = false

					for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
						if (UserConfig.getInstance(a).getClientUserId() == accountUserId) {
							account = a
							foundAccount = true
							break
						}
					}

					if (!foundAccount) {
						FileLog.d("$tag ACCOUNT NOT FOUND")
						countDownLatch.countDown()
						return@postRunnable
					}

					currentAccount = account

					if (!UserConfig.getInstance(currentAccount).isClientActivated) {
						FileLog.d("$tag ACCOUNT NOT ACTIVATED")
						countDownLatch.countDown()
						return@postRunnable
					}

					when (locKey) {
						"PHONE_CALL_REQUEST" -> {
							if (ForegroundDetector.instance?.isBackground != false) {
								val callBytes = Base64.decode(custom.getString("updates"), Base64.URL_SAFE)

								if (callBytes != null) {
									val updates = SerializedData(callBytes).use {
										TLRPC.Updates.deserialize(it, it.readInt32(false), false)
									}

									if (updates != null && updates.updates.isNotEmpty()) {
										MessagesController.getInstance(currentAccount).processUpdateArray(updates.updates, updates.users, updates.chats, false, updates.date)
									}
								}
							}

							countDownLatch.countDown()

							return@postRunnable
						}

						"DC_UPDATE" -> {
							val dc = custom.getInt("dc")
							val addr = custom.getString("addr")
							val parts = addr.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

							if (parts.size != 2) {
								countDownLatch.countDown()
								return@postRunnable
							}

							val ip = parts[0]
							val port = parts[1].toInt()

							with(ConnectionsManager.getInstance(currentAccount)) {
								applyDatacenterAddress(dc, ip, port)
								resumeNetworkMaybe()
							}

							countDownLatch.countDown()

							return@postRunnable
						}

						"MESSAGE_ANNOUNCEMENT" -> {
							val update = TLRPC.TLUpdateServiceNotification()
							update.popup = false
							update.flags = 2
							update.inboxDate = (time / 1000).toInt()
							update.message = json.getString("message")
							update.type = "announcement"
							update.media = TLRPC.TLMessageMediaEmpty()

							val updates = TLRPC.TLUpdates()
							updates.updates.add(update)

							Utilities.stageQueue.postRunnable {
								MessagesController.getInstance(currentAccount).processUpdates(updates, false)
							}

							ConnectionsManager.getInstance(currentAccount).resumeNetworkMaybe()

							countDownLatch.countDown()

							return@postRunnable
						}

						"SESSION_REVOKE" -> {
							AndroidUtilities.runOnUIThread {
								if (UserConfig.getInstance(currentAccount).getClientUserId() != 0L) {
									UserConfig.getInstance(currentAccount).clearConfig()
									MessagesController.getInstance(currentAccount).performLogout(0)
								}
							}

							countDownLatch.countDown()

							return@postRunnable
						}

						"GEO_LIVE_PENDING" -> {
							Utilities.stageQueue.postRunnable {
								LocationController.getInstance(currentAccount).setNewLocationEndWatchTime()
							}

							countDownLatch.countDown()

							return@postRunnable
						}
					}

					val channelId: Long
					val chatId: Long
					val userId: Long
					var dialogId: Long = 0

					if (custom.has("channel_id")) {
						channelId = custom.getLong("channel_id")
						dialogId = -channelId
					}
					else {
						channelId = 0
					}

					if (custom.has("from_id")) {
						userId = custom.getLong("from_id")
						dialogId = userId
					}
					else {
						userId = 0
					}

					if (custom.has("chat_id")) {
						chatId = custom.getLong("chat_id")
						dialogId = -chatId
					}
					else {
						chatId = 0
					}

					if (custom.has("encryption_id")) {
						dialogId = DialogObject.makeEncryptedDialogId(custom.getInt("encryption_id").toLong())
					}

					val scheduled = if (custom.has("schedule")) {
						custom.getInt("schedule") == 1
					}
					else {
						false
					}

					if (dialogId == 0L && "ENCRYPTED_MESSAGE" == locKey) {
						dialogId = NotificationsController.globalSecretChatId
					}

					var canRelease = true

					if (dialogId != 0L) {
						if ("READ_HISTORY" == locKey) {
							val maxId = custom.getInt("max_id")
							val updates = ArrayList<Update>()

							FileLog.d("$tag received read notification max_id = $maxId for dialogId = $dialogId")

							if (channelId != 0L) {
								val update = TLRPC.TLUpdateReadChannelInbox()
								update.channelId = channelId
								update.maxId = maxId
								update.stillUnreadCount = 0
								updates.add(update)
							}
							else {
								val update = TLRPC.TLUpdateReadHistoryInbox()

								if (userId != 0L) {
									update.peer = TLRPC.TLPeerUser().also {
										it.userId = userId
									}
								}
								else {
									update.peer = TLRPC.TLPeerChat().also {
										it.chatId = chatId
									}
								}

								update.maxId = maxId

								updates.add(update)
							}

							MessagesController.getInstance(currentAccount).processUpdateArray(updates, null, null, false, 0)
						}
						else if ("MESSAGE_DELETED" == locKey) {
							val messages = custom.getString("messages")
							val messagesArgs = messages.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
							val deletedMessages = LongSparseArray<ArrayList<Int>>()
							val ids = ArrayList<Int>()

							for (messagesArg in messagesArgs) {
								ids.add(Utilities.parseInt(messagesArg))
							}

							deletedMessages.put(-channelId, ids)

							NotificationsController.getInstance(currentAccount).removeDeletedMessagesFromNotifications(deletedMessages, false)

							MessagesController.getInstance(currentAccount).deleteMessagesByPush(dialogId, ids, channelId)

							FileLog.d(tag + " received " + locKey + " for dialogId = " + dialogId + " mids = " + TextUtils.join(",", ids))
						}
						else if ("READ_REACTION" == locKey) {
							val messages = custom.getString("messages")
							val messagesArgs = messages.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
							val deletedMessages = LongSparseArray<ArrayList<Int>>()
							val ids = ArrayList<Int>()
							val sparseBooleanArray = SparseBooleanArray()

							for (messagesArg in messagesArgs) {
								val messageId = Utilities.parseInt(messagesArg)
								ids.add(messageId)
								sparseBooleanArray.put(messageId, false)
							}

							deletedMessages.put(-channelId, ids)

							NotificationsController.getInstance(currentAccount).removeDeletedMessagesFromNotifications(deletedMessages, true)

							MessagesController.getInstance(currentAccount).checkUnreadReactions(dialogId, sparseBooleanArray)

							FileLog.d(tag + " received " + locKey + " for dialogId = " + dialogId + " mids = " + TextUtils.join(",", ids))
						}
						else if (!locKey.isNullOrEmpty()) {
							val msgId = if (custom.has("msg_id")) {
								custom.getInt("msg_id")
							}
							else {
								0
							}

							val randomId = if (custom.has("random_id")) {
								Utilities.parseLong(custom.getString("random_id"))
							}
							else {
								0
							}

							var processNotification = false

							if (msgId != 0) {
								var currentReadValue = MessagesController.getInstance(currentAccount).dialogs_read_inbox_max[dialogId]

								if (currentReadValue == null) {
									currentReadValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialogId)
									MessagesController.getInstance(currentAccount).dialogs_read_inbox_max[dialogId] = currentReadValue
								}

								if (msgId > currentReadValue) {
									processNotification = true
								}
							}
							else if (randomId != 0L) {
								if (!MessagesStorage.getInstance(account).checkMessageByRandomId(randomId)) {
									processNotification = true
								}
							}

							if (locKey.startsWith("REACT_") || locKey.startsWith("CHAT_REACT_")) {
								processNotification = true
							}

							if (processNotification) {
								val chatFromId = custom.optLong("chat_from_id", 0)
								val chatFromBroadcastId = custom.optLong("chat_from_broadcast_id", 0)
								val chatFromGroupId = custom.optLong("chat_from_group_id", 0)
								val isGroup = chatFromId != 0L || chatFromGroupId != 0L
								val mention = custom.has("mention") && custom.getInt("mention") != 0
								val silent = custom.has("silent") && custom.getInt("silent") != 0
								val args: Array<String?>?

								if (json.has("loc_args")) {
									val locArgs = json.getJSONArray("loc_args")

									args = arrayOfNulls(locArgs.length())

									for (a in args.indices) {
										args[a] = locArgs.getString(a)
									}
								}
								else {
									args = null
								}

								var messageText: String? = null
								var message1: String? = null
								var name = args!![0]
								var userName: String? = null
								var localMessage = false
								var supergroup = false
								var pinned = false
								var channel = false
								val edited = custom.has("edit_date")
								val context = ApplicationLoader.applicationContext

								if (locKey.startsWith("CHAT_")) {
									if (UserObject.isReplyUser(dialogId)) {
										name += " @ " + args[1]
									}
									else {
										supergroup = channelId != 0L
										userName = name
										name = args[1]
									}
								}
								else if (locKey.startsWith("PINNED_")) {
									supergroup = channelId != 0L
									pinned = true
								}
								else if (locKey.startsWith("CHANNEL_")) {
									channel = true
								}

								FileLog.d("$tag received message notification $locKey for dialogId = $dialogId mid = $msgId")

								if (locKey.startsWith("REACT_") || locKey.startsWith("CHAT_REACT_")) {
									messageText = getReactedText(locKey, args)
								}
								else {
									when (locKey) {
										"MESSAGE_RECURRING_PAY" -> {
											messageText = LocaleController.formatString("NotificationMessageRecurringPay", R.string.NotificationMessageRecurringPay, args[0], args[1])
											message1 = context.getString(R.string.PaymentInvoice)
										}

										"MESSAGE_TEXT", "CHANNEL_MESSAGE_TEXT" -> {
											messageText = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, args[0], args[1])
											message1 = args[1]
										}

										"MESSAGE_NOTEXT" -> {
											messageText = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, args[0])
											message1 = context.getString(R.string.Message)
										}

										"MESSAGE_PHOTO" -> {
											messageText = LocaleController.formatString("NotificationMessagePhoto", R.string.NotificationMessagePhoto, args[0])
											message1 = context.getString(R.string.AttachPhoto)
										}

										"MESSAGE_PHOTO_SECRET" -> {
											messageText = LocaleController.formatString("NotificationMessageSDPhoto", R.string.NotificationMessageSDPhoto, args[0])
											message1 = context.getString(R.string.AttachDestructingPhoto)
										}

										"MESSAGE_VIDEO" -> {
											messageText = LocaleController.formatString("NotificationMessageVideo", R.string.NotificationMessageVideo, args[0])
											message1 = context.getString(R.string.AttachVideo)
										}

										"MESSAGE_VIDEO_SECRET" -> {
											messageText = LocaleController.formatString("NotificationMessageSDVideo", R.string.NotificationMessageSDVideo, args[0])
											message1 = context.getString(R.string.AttachDestructingVideo)
										}

										"MESSAGE_SCREENSHOT" -> {
											messageText = context.getString(R.string.ActionTakeScreenshoot).replace("un1", args[0]!!)
										}

										"MESSAGE_ROUND" -> {
											messageText = LocaleController.formatString("NotificationMessageRound", R.string.NotificationMessageRound, args[0])
											message1 = context.getString(R.string.AttachRound)
										}

										"MESSAGE_DOC" -> {
											messageText = LocaleController.formatString("NotificationMessageDocument", R.string.NotificationMessageDocument, args[0])
											message1 = context.getString(R.string.AttachDocument)
										}

										"MESSAGE_STICKER" -> {
											if (args.size > 1 && !args[1].isNullOrEmpty()) {
												messageText = LocaleController.formatString("NotificationMessageStickerEmoji", R.string.NotificationMessageStickerEmoji, args[0], args[1])
												message1 = args[1] + " " + context.getString(R.string.AttachSticker)
											}
											else {
												messageText = LocaleController.formatString("NotificationMessageSticker", R.string.NotificationMessageSticker, args[0])
												message1 = context.getString(R.string.AttachSticker)
											}
										}

										"MESSAGE_AUDIO" -> {
											messageText = LocaleController.formatString("NotificationMessageAudio", R.string.NotificationMessageAudio, args[0])
											message1 = context.getString(R.string.AttachAudio)
										}

										"MESSAGE_CONTACT" -> {
											messageText = LocaleController.formatString("NotificationMessageContact2", R.string.NotificationMessageContact2, args[0], args[1])
											message1 = context.getString(R.string.AttachContact)
										}

										"MESSAGE_QUIZ" -> {
											messageText = LocaleController.formatString("NotificationMessageQuiz2", R.string.NotificationMessageQuiz2, args[0], args[1])
											message1 = context.getString(R.string.QuizPoll)
										}

										"MESSAGE_POLL" -> {
											messageText = LocaleController.formatString("NotificationMessagePoll2", R.string.NotificationMessagePoll2, args[0], args[1])
											message1 = context.getString(R.string.Poll)
										}

										"MESSAGE_GEO" -> {
											messageText = LocaleController.formatString("NotificationMessageMap", R.string.NotificationMessageMap, args[0])
											message1 = context.getString(R.string.AttachLocation)
										}

										"MESSAGE_GEOLIVE" -> {
											messageText = LocaleController.formatString("NotificationMessageLiveLocation", R.string.NotificationMessageLiveLocation, args[0])
											message1 = context.getString(R.string.AttachLiveLocation)
										}

										"MESSAGE_GIF" -> {
											messageText = LocaleController.formatString("NotificationMessageGif", R.string.NotificationMessageGif, args[0])
											message1 = context.getString(R.string.AttachGif)
										}

										"MESSAGE_GAME" -> {
											messageText = LocaleController.formatString("NotificationMessageGame", R.string.NotificationMessageGame, args[0], args[1])
											message1 = context.getString(R.string.AttachGame)
										}

										"MESSAGE_GAME_SCORE", "CHANNEL_MESSAGE_GAME_SCORE" -> {
											messageText = LocaleController.formatString("NotificationMessageGameScored", R.string.NotificationMessageGameScored, args[0], args[1], args[2])
										}

										"MESSAGE_INVOICE" -> {
											messageText = LocaleController.formatString("NotificationMessageInvoice", R.string.NotificationMessageInvoice, args[0], args[1])
											message1 = context.getString(R.string.PaymentInvoice)
										}

										"MESSAGE_FWDS" -> {
											messageText = LocaleController.formatString("NotificationMessageForwardFew", R.string.NotificationMessageForwardFew, args[0], LocaleController.formatPluralString("messages", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"MESSAGE_PHOTOS" -> {
											messageText = LocaleController.formatString("NotificationMessageFew", R.string.NotificationMessageFew, args[0], LocaleController.formatPluralString("Photos", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"MESSAGE_VIDEOS" -> {
											messageText = LocaleController.formatString("NotificationMessageFew", R.string.NotificationMessageFew, args[0], LocaleController.formatPluralString("Videos", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"MESSAGE_PLAYLIST" -> {
											messageText = LocaleController.formatString("NotificationMessageFew", R.string.NotificationMessageFew, args[0], LocaleController.formatPluralString("MusicFiles", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"MESSAGE_DOCS" -> {
											messageText = LocaleController.formatString("NotificationMessageFew", R.string.NotificationMessageFew, args[0], LocaleController.formatPluralString("Files", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"MESSAGES" -> {
											messageText = LocaleController.formatString("NotificationMessageAlbum", R.string.NotificationMessageAlbum, args[0])
											localMessage = true
										}

										"CHANNEL_MESSAGE_NOTEXT" -> {
											messageText = LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, args[0])
											message1 = context.getString(R.string.Message)
										}

										"CHANNEL_MESSAGE_PHOTO" -> {
											messageText = LocaleController.formatString("ChannelMessagePhoto", R.string.ChannelMessagePhoto, args[0])
											message1 = context.getString(R.string.AttachPhoto)
										}

										"CHANNEL_MESSAGE_VIDEO" -> {
											messageText = LocaleController.formatString("ChannelMessageVideo", R.string.ChannelMessageVideo, args[0])
											message1 = context.getString(R.string.AttachVideo)
										}

										"CHANNEL_MESSAGE_ROUND" -> {
											messageText = LocaleController.formatString("ChannelMessageRound", R.string.ChannelMessageRound, args[0])
											message1 = context.getString(R.string.AttachRound)
										}

										"CHANNEL_MESSAGE_DOC" -> {
											messageText = LocaleController.formatString("ChannelMessageDocument", R.string.ChannelMessageDocument, args[0])
											message1 = context.getString(R.string.AttachDocument)
										}

										"CHANNEL_MESSAGE_STICKER" -> {
											if (args.size > 1 && !args[1].isNullOrEmpty()) {
												messageText = LocaleController.formatString("ChannelMessageStickerEmoji", R.string.ChannelMessageStickerEmoji, args[0], args[1])
												message1 = args[1] + " " + context.getString(R.string.AttachSticker)
											}
											else {
												messageText = LocaleController.formatString("ChannelMessageSticker", R.string.ChannelMessageSticker, args[0])
												message1 = context.getString(R.string.AttachSticker)
											}
										}

										"CHANNEL_MESSAGE_AUDIO" -> {
											messageText = LocaleController.formatString("ChannelMessageAudio", R.string.ChannelMessageAudio, args[0])
											message1 = context.getString(R.string.AttachAudio)
										}

										"CHANNEL_MESSAGE_CONTACT" -> {
											messageText = LocaleController.formatString("ChannelMessageContact2", R.string.ChannelMessageContact2, args[0], args[1])
											message1 = context.getString(R.string.AttachContact)
										}

										"CHANNEL_MESSAGE_QUIZ" -> {
											messageText = LocaleController.formatString("ChannelMessageQuiz2", R.string.ChannelMessageQuiz2, args[0], args[1])
											message1 = context.getString(R.string.QuizPoll)
										}

										"CHANNEL_MESSAGE_POLL" -> {
											messageText = LocaleController.formatString("ChannelMessagePoll2", R.string.ChannelMessagePoll2, args[0], args[1])
											message1 = context.getString(R.string.Poll)
										}

										"CHANNEL_MESSAGE_GEO" -> {
											messageText = LocaleController.formatString("ChannelMessageMap", R.string.ChannelMessageMap, args[0])
											message1 = context.getString(R.string.AttachLocation)
										}

										"CHANNEL_MESSAGE_GEOLIVE" -> {
											messageText = LocaleController.formatString("ChannelMessageLiveLocation", R.string.ChannelMessageLiveLocation, args[0])
											message1 = context.getString(R.string.AttachLiveLocation)
										}

										"CHANNEL_MESSAGE_GIF" -> {
											messageText = LocaleController.formatString("ChannelMessageGIF", R.string.ChannelMessageGIF, args[0])
											message1 = context.getString(R.string.AttachGif)
										}

										"CHANNEL_MESSAGE_GAME" -> {
											messageText = LocaleController.formatString("NotificationMessageGame", R.string.NotificationMessageGame, args[0])
											message1 = context.getString(R.string.AttachGame)
										}

										"CHANNEL_MESSAGE_FWDS" -> {
											messageText = LocaleController.formatString("ChannelMessageFew", R.string.ChannelMessageFew, args[0], LocaleController.formatPluralString("ForwardedMessageCount", Utilities.parseInt(args[1])).lowercase())
											localMessage = true
										}

										"CHANNEL_MESSAGE_PHOTOS" -> {
											messageText = LocaleController.formatString("ChannelMessageFew", R.string.ChannelMessageFew, args[0], LocaleController.formatPluralString("Photos", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"CHANNEL_MESSAGE_VIDEOS" -> {
											messageText = LocaleController.formatString("ChannelMessageFew", R.string.ChannelMessageFew, args[0], LocaleController.formatPluralString("Videos", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"CHANNEL_MESSAGE_PLAYLIST" -> {
											messageText = LocaleController.formatString("ChannelMessageFew", R.string.ChannelMessageFew, args[0], LocaleController.formatPluralString("MusicFiles", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"CHANNEL_MESSAGE_DOCS" -> {
											messageText = LocaleController.formatString("ChannelMessageFew", R.string.ChannelMessageFew, args[0], LocaleController.formatPluralString("Files", Utilities.parseInt(args[1])))
											localMessage = true
										}

										"CHANNEL_MESSAGES" -> {
											messageText = LocaleController.formatString("ChannelMessageAlbum", R.string.ChannelMessageAlbum, args[0])
											localMessage = true
										}

										"CHAT_MESSAGE_TEXT" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, args[0], args[1], args[2])
											message1 = args[2]
										}

										"CHAT_MESSAGE_NOTEXT" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, args[0], args[1])
											message1 = context.getString(R.string.Message)
										}

										"CHAT_MESSAGE_PHOTO" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupPhoto", R.string.NotificationMessageGroupPhoto, args[0], args[1])
											message1 = context.getString(R.string.AttachPhoto)
										}

										"CHAT_MESSAGE_VIDEO" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupVideo", R.string.NotificationMessageGroupVideo, args[0], args[1])
											message1 = context.getString(R.string.AttachVideo)
										}

										"CHAT_MESSAGE_ROUND" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupRound", R.string.NotificationMessageGroupRound, args[0], args[1])
											message1 = context.getString(R.string.AttachRound)
										}

										"CHAT_MESSAGE_DOC" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupDocument", R.string.NotificationMessageGroupDocument, args[0], args[1])
											message1 = context.getString(R.string.AttachDocument)
										}

										"CHAT_MESSAGE_STICKER" -> {
											if (args.size > 2 && !args[2].isNullOrEmpty()) {
												messageText = LocaleController.formatString("NotificationMessageGroupStickerEmoji", R.string.NotificationMessageGroupStickerEmoji, args[0], args[1], args[2])
												message1 = args[2] + " " + context.getString(R.string.AttachSticker)
											}
											else {
												messageText = LocaleController.formatString("NotificationMessageGroupSticker", R.string.NotificationMessageGroupSticker, args[0], args[1])
												message1 = args[1] + " " + context.getString(R.string.AttachSticker)
											}
										}

										"CHAT_MESSAGE_AUDIO" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupAudio", R.string.NotificationMessageGroupAudio, args[0], args[1])
											message1 = context.getString(R.string.AttachAudio)
										}

										"CHAT_MESSAGE_CONTACT" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupContact2", R.string.NotificationMessageGroupContact2, args[0], args[1], args[2])
											message1 = context.getString(R.string.AttachContact)
										}

										"CHAT_MESSAGE_QUIZ" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupQuiz2", R.string.NotificationMessageGroupQuiz2, args[0], args[1], args[2])
											message1 = context.getString(R.string.PollQuiz)
										}

										"CHAT_MESSAGE_POLL" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupPoll2", R.string.NotificationMessageGroupPoll2, args[0], args[1], args[2])
											message1 = context.getString(R.string.Poll)
										}

										"CHAT_MESSAGE_GEO" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupMap", R.string.NotificationMessageGroupMap, args[0], args[1])
											message1 = context.getString(R.string.AttachLocation)
										}

										"CHAT_MESSAGE_GEOLIVE" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupLiveLocation", R.string.NotificationMessageGroupLiveLocation, args[0], args[1])
											message1 = context.getString(R.string.AttachLiveLocation)
										}

										"CHAT_MESSAGE_GIF" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupGif", R.string.NotificationMessageGroupGif, args[0], args[1])
											message1 = context.getString(R.string.AttachGif)
										}

										"CHAT_MESSAGE_GAME" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupGame", R.string.NotificationMessageGroupGame, args[0], args[1], args[2])
											message1 = context.getString(R.string.AttachGame)
										}

										"CHAT_MESSAGE_GAME_SCORE" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupGameScored", R.string.NotificationMessageGroupGameScored, args[0], args[1], args[2], args[3])
										}

										"CHAT_MESSAGE_INVOICE" -> {
											messageText = LocaleController.formatString("NotificationMessageGroupInvoice", R.string.NotificationMessageGroupInvoice, args[0], args[1], args[2])
											message1 = context.getString(R.string.PaymentInvoice)
										}

										"CHAT_CREATED", "CHAT_ADD_YOU" -> {
											messageText = LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, args[0], args[1])
										}

										"CHAT_TITLE_EDITED" -> {
											messageText = LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, args[0], args[1])
										}

										"CHAT_PHOTO_EDITED" -> {
											messageText = LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, args[0], args[1])
										}

										"CHAT_ADD_MEMBER" -> {
											messageText = LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, args[0], args[1], args[2])
										}

										"CHAT_VOICECHAT_START" -> {
											messageText = LocaleController.formatString("NotificationGroupCreatedCall", R.string.NotificationGroupCreatedCall, args[0], args[1])
										}

										"CHAT_VOICECHAT_INVITE" -> {
											messageText = LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, args[0], args[1], args[2])
										}

										"CHAT_VOICECHAT_END" -> {
											messageText = LocaleController.formatString("NotificationGroupEndedCall", R.string.NotificationGroupEndedCall, args[0], args[1])
										}

										"CHAT_VOICECHAT_INVITE_YOU" -> {
											messageText = LocaleController.formatString("NotificationGroupInvitedYouToCall", R.string.NotificationGroupInvitedYouToCall, args[0], args[1])
										}

										"CHAT_DELETE_MEMBER" -> {
											messageText = LocaleController.formatString("NotificationGroupKickMember", R.string.NotificationGroupKickMember, args[0], args[1])
										}

										"CHAT_DELETE_YOU" -> {
											messageText = LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, args[0], args[1])
										}

										"CHAT_LEFT" -> {
											messageText = LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, args[0], args[1])
										}

										"CHAT_RETURNED" -> {
											messageText = LocaleController.formatString("NotificationGroupAddSelf", R.string.NotificationGroupAddSelf, args[0], args[1])
										}

										"CHAT_JOINED" -> {
											messageText = LocaleController.formatString("NotificationGroupAddSelfMega", R.string.NotificationGroupAddSelfMega, args[0], args[1])
										}

										"CHAT_REQ_JOINED" -> {
											messageText = LocaleController.formatString("UserAcceptedToGroupPushWithGroup", R.string.UserAcceptedToGroupPushWithGroup, args[0], args[1])
										}

										"CHAT_MESSAGE_FWDS" -> {
											messageText = LocaleController.formatString("NotificationGroupForwardedFew", R.string.NotificationGroupForwardedFew, args[0], args[1], LocaleController.formatPluralString("messages", Utilities.parseInt(args[2])))
											localMessage = true
										}

										"CHAT_MESSAGE_PHOTOS" -> {
											messageText = LocaleController.formatString("NotificationGroupFew", R.string.NotificationGroupFew, args[0], args[1], LocaleController.formatPluralString("Photos", Utilities.parseInt(args[2])))
											localMessage = true
										}

										"CHAT_MESSAGE_VIDEOS" -> {
											messageText = LocaleController.formatString("NotificationGroupFew", R.string.NotificationGroupFew, args[0], args[1], LocaleController.formatPluralString("Videos", Utilities.parseInt(args[2])))
											localMessage = true
										}

										"CHAT_MESSAGE_PLAYLIST" -> {
											messageText = LocaleController.formatString("NotificationGroupFew", R.string.NotificationGroupFew, args[0], args[1], LocaleController.formatPluralString("MusicFiles", Utilities.parseInt(args[2])))
											localMessage = true
										}

										"CHAT_MESSAGE_DOCS" -> {
											messageText = LocaleController.formatString("NotificationGroupFew", R.string.NotificationGroupFew, args[0], args[1], LocaleController.formatPluralString("Files", Utilities.parseInt(args[2])))
											localMessage = true
										}

										"CHAT_MESSAGES" -> {
											messageText = LocaleController.formatString("NotificationGroupAlbum", R.string.NotificationGroupAlbum, args[0], args[1])
											localMessage = true
										}

										"PINNED_TEXT" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, args[0], args[1], args[2])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, args[0], args[1])
												}
											}
										}

										"PINNED_NOTEXT" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedNoTextUser", R.string.NotificationActionPinnedNoTextUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, args[0])
												}
											}
										}

										"PINNED_PHOTO" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedPhotoUser", R.string.NotificationActionPinnedPhotoUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedPhoto", R.string.NotificationActionPinnedPhoto, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedPhotoChannel", R.string.NotificationActionPinnedPhotoChannel, args[0])
												}
											}
										}

										"PINNED_VIDEO" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedVideoUser", R.string.NotificationActionPinnedVideoUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedVideo", R.string.NotificationActionPinnedVideo, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedVideoChannel", R.string.NotificationActionPinnedVideoChannel, args[0])
												}
											}
										}

										"PINNED_ROUND" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedRoundUser", R.string.NotificationActionPinnedRoundUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedRound", R.string.NotificationActionPinnedRound, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedRoundChannel", R.string.NotificationActionPinnedRoundChannel, args[0])
												}
											}
										}

										"PINNED_DOC" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedFileUser", R.string.NotificationActionPinnedFileUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedFile", R.string.NotificationActionPinnedFile, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedFileChannel", R.string.NotificationActionPinnedFileChannel, args[0])
												}
											}
										}

										"PINNED_STICKER" -> {
											messageText = if (dialogId > 0) {
												if (args.size > 1 && !TextUtils.isEmpty(args[1])) {
													LocaleController.formatString("NotificationActionPinnedStickerEmojiUser", R.string.NotificationActionPinnedStickerEmojiUser, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedStickerUser", R.string.NotificationActionPinnedStickerUser, args[0])
												}
											}
											else {
												if (isGroup) {
													if (args.size > 2 && !TextUtils.isEmpty(args[2])) {
														LocaleController.formatString("NotificationActionPinnedStickerEmoji", R.string.NotificationActionPinnedStickerEmoji, args[0], args[2], args[1])
													}
													else {
														LocaleController.formatString("NotificationActionPinnedSticker", R.string.NotificationActionPinnedSticker, args[0], args[1])
													}
												}
												else {
													if (args.size > 1 && !TextUtils.isEmpty(args[1])) {
														LocaleController.formatString("NotificationActionPinnedStickerEmojiChannel", R.string.NotificationActionPinnedStickerEmojiChannel, args[0], args[1])
													}
													else {
														LocaleController.formatString("NotificationActionPinnedStickerChannel", R.string.NotificationActionPinnedStickerChannel, args[0])
													}
												}
											}
										}

										"PINNED_AUDIO" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedVoiceUser", R.string.NotificationActionPinnedVoiceUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedVoice", R.string.NotificationActionPinnedVoice, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedVoiceChannel", R.string.NotificationActionPinnedVoiceChannel, args[0])
												}
											}
										}

										"PINNED_CONTACT" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedContactUser", R.string.NotificationActionPinnedContactUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedContact2", R.string.NotificationActionPinnedContact2, args[0], args[2], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedContactChannel2", R.string.NotificationActionPinnedContactChannel2, args[0], args[1])
												}
											}
										}

										"PINNED_QUIZ" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedQuizUser", R.string.NotificationActionPinnedQuizUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedQuiz2", R.string.NotificationActionPinnedQuiz2, args[0], args[2], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedQuizChannel2", R.string.NotificationActionPinnedQuizChannel2, args[0], args[1])
												}
											}
										}

										"PINNED_POLL" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedPollUser", R.string.NotificationActionPinnedPollUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedPoll2", R.string.NotificationActionPinnedPoll2, args[0], args[2], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedPollChannel2", R.string.NotificationActionPinnedPollChannel2, args[0], args[1])
												}
											}
										}

										"PINNED_GEO" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedGeoUser", R.string.NotificationActionPinnedGeoUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedGeo", R.string.NotificationActionPinnedGeo, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedGeoChannel", R.string.NotificationActionPinnedGeoChannel, args[0])
												}
											}
										}

										"PINNED_GEOLIVE" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedGeoLiveUser", R.string.NotificationActionPinnedGeoLiveUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedGeoLive", R.string.NotificationActionPinnedGeoLive, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedGeoLiveChannel", R.string.NotificationActionPinnedGeoLiveChannel, args[0])
												}
											}
										}

										"PINNED_GAME" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedGameUser", R.string.NotificationActionPinnedGameUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedGame", R.string.NotificationActionPinnedGame, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedGameChannel", R.string.NotificationActionPinnedGameChannel, args[0])
												}
											}
										}

										"PINNED_GAME_SCORE" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedGameScoreUser", R.string.NotificationActionPinnedGameScoreUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedGameScore", R.string.NotificationActionPinnedGameScore, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedGameScoreChannel", R.string.NotificationActionPinnedGameScoreChannel, args[0])
												}
											}
										}

										"PINNED_INVOICE" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedInvoiceUser", R.string.NotificationActionPinnedInvoiceUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedInvoice", R.string.NotificationActionPinnedInvoice, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedInvoiceChannel", R.string.NotificationActionPinnedInvoiceChannel, args[0])
												}
											}
										}

										"PINNED_GIF" -> {
											messageText = if (dialogId > 0) {
												LocaleController.formatString("NotificationActionPinnedGifUser", R.string.NotificationActionPinnedGifUser, args[0], args[1])
											}
											else {
												if (isGroup) {
													LocaleController.formatString("NotificationActionPinnedGif", R.string.NotificationActionPinnedGif, args[0], args[1])
												}
												else {
													LocaleController.formatString("NotificationActionPinnedGifChannel", R.string.NotificationActionPinnedGifChannel, args[0])
												}
											}
										}

										"ENCRYPTED_MESSAGE" -> {
											messageText = context.getString(R.string.YouHaveNewMessage)
											name = context.getString(R.string.SecretChatName)
											localMessage = true
										}

										"PHONE_CALL_REQUEST", "REACT_TEXT", "CONTACT_JOINED", "AUTH_UNKNOWN", "AUTH_REGION", "LOCKED_MESSAGE", "ENCRYPTION_REQUEST", "ENCRYPTION_ACCEPT", "MESSAGE_MUTED", "PHONE_CALL_MISSED" -> {
											// unused
										}

										else -> {
											FileLog.w("unhandled loc_key = $locKey")
										}
									}
								}

								if (messageText != null) {
									val messageOwner = TLRPC.TLMessage()
									messageOwner.id = msgId
									messageOwner.randomId = randomId
									messageOwner.message = message1 ?: messageText
									messageOwner.date = (time / 1000).toInt()

									if (pinned) {
										messageOwner.pinned = true
										// messageOwner.action = TLMessageActionPinMessage()
									}

									if (supergroup) {
										messageOwner.flags = messageOwner.flags or -0x80000000
									}

									messageOwner.dialogId = dialogId

									if (channelId != 0L) {
										messageOwner.peerId = TLRPC.TLPeerChannel().also {
											it.channelId = channelId
										}
									}
									else if (chatId != 0L) {
										messageOwner.peerId = TLRPC.TLPeerChat().also {
											it.chatId = chatId
										}
									}
									else {
										messageOwner.peerId = TLRPC.TLPeerUser().also {
											it.userId = userId
										}
									}

									messageOwner.flags = messageOwner.flags or 256

									if (chatFromGroupId != 0L) {
										messageOwner.fromId = TLRPC.TLPeerChat().also {
											it.chatId = chatId
										}
									}
									else if (chatFromBroadcastId != 0L) {
										messageOwner.fromId = TLRPC.TLPeerChannel().also {
											it.channelId = chatFromBroadcastId
										}
									}
									else if (chatFromId != 0L) {
										messageOwner.fromId = TLRPC.TLPeerUser().also {
											it.userId = chatFromId
										}
									}
									else {
										messageOwner.fromId = messageOwner.peerId
									}

									messageOwner.mentioned = mention || pinned
									messageOwner.silent = silent
									messageOwner.fromScheduled = scheduled

									val messageObject = MessageObject(currentAccount, messageOwner, messageText, name, userName, localMessage, channel, supergroup, edited)
									messageObject.isReactionPush = locKey.startsWith("REACT_") || locKey.startsWith("CHAT_REACT_")

									canRelease = false

									NotificationsController.getInstance(currentAccount).processNewMessages(listOf(messageObject), isLast = true, isFcm = true, countDownLatch = countDownLatch)
								}
							}
						}
					}

					if (canRelease) {
						countDownLatch.countDown()
					}

					ConnectionsManager.onInternalPushReceived(currentAccount)

					ConnectionsManager.getInstance(currentAccount).resumeNetworkMaybe()
				}
				catch (e: Throwable) {
					if (currentAccount != -1) {
						ConnectionsManager.onInternalPushReceived(currentAccount)
						ConnectionsManager.getInstance(currentAccount).resumeNetworkMaybe()
						countDownLatch.countDown()
					}
					else {
						onDecryptError()
					}

					FileLog.e("error in loc_key = $locKey json $jsonString")
					FileLog.e(e)
				}
			}
		}

		runCatching {
			countDownLatch.await()
		}

		FileLog.d("finished " + tag + " service, time = " + (SystemClock.elapsedRealtime() - receiveTime))
	}

	private fun getReactedText(locKey: String?, args: Array<String?>): String? {
		return when (locKey) {
			"REACT_TEXT" -> LocaleController.formatString("PushReactText", R.string.PushReactText, *args)
			"REACT_NOTEXT" -> LocaleController.formatString("PushReactNoText", R.string.PushReactNoText, *args)
			"REACT_PHOTO" -> LocaleController.formatString("PushReactPhoto", R.string.PushReactPhoto, *args)
			"REACT_VIDEO" -> LocaleController.formatString("PushReactVideo", R.string.PushReactVideo, *args)
			"REACT_ROUND" -> LocaleController.formatString("PushReactRound", R.string.PushReactRound, *args)
			"REACT_DOC" -> LocaleController.formatString("PushReactDoc", R.string.PushReactDoc, *args)
			"REACT_STICKER" -> LocaleController.formatString("PushReactSticker", R.string.PushReactSticker, *args)
			"REACT_AUDIO" -> LocaleController.formatString("PushReactAudio", R.string.PushReactAudio, *args)
			"REACT_CONTACT" -> LocaleController.formatString("PushReactContect", R.string.PushReactContect, *args)
			"REACT_GEO" -> LocaleController.formatString("PushReactGeo", R.string.PushReactGeo, *args)
			"REACT_GEOLIVE" -> LocaleController.formatString("PushReactGeoLocation", R.string.PushReactGeoLocation, *args)
			"REACT_POLL" -> LocaleController.formatString("PushReactPoll", R.string.PushReactPoll, *args)
			"REACT_QUIZ" -> LocaleController.formatString("PushReactQuiz", R.string.PushReactQuiz, *args)
			"REACT_GAME" -> LocaleController.formatString("PushReactGame", R.string.PushReactGame, *args)
			"REACT_INVOICE" -> LocaleController.formatString("PushReactInvoice", R.string.PushReactInvoice, *args)
			"REACT_GIF" -> LocaleController.formatString("PushReactGif", R.string.PushReactGif, *args)
			"CHAT_REACT_TEXT" -> LocaleController.formatString("PushChatReactText", R.string.PushChatReactText, *args)
			"CHAT_REACT_NOTEXT" -> LocaleController.formatString("PushChatReactNotext", R.string.PushChatReactNotext, *args)
			"CHAT_REACT_PHOTO" -> LocaleController.formatString("PushChatReactPhoto", R.string.PushChatReactPhoto, *args)
			"CHAT_REACT_VIDEO" -> LocaleController.formatString("PushChatReactVideo", R.string.PushChatReactVideo, *args)
			"CHAT_REACT_ROUND" -> LocaleController.formatString("PushChatReactRound", R.string.PushChatReactRound, *args)
			"CHAT_REACT_DOC" -> LocaleController.formatString("PushChatReactDoc", R.string.PushChatReactDoc, *args)
			"CHAT_REACT_STICKER" -> LocaleController.formatString("PushChatReactSticker", R.string.PushChatReactSticker, *args)
			"CHAT_REACT_AUDIO" -> LocaleController.formatString("PushChatReactAudio", R.string.PushChatReactAudio, *args)
			"CHAT_REACT_CONTACT" -> LocaleController.formatString("PushChatReactContact", R.string.PushChatReactContact, *args)
			"CHAT_REACT_GEO" -> LocaleController.formatString("PushChatReactGeo", R.string.PushChatReactGeo, *args)
			"CHAT_REACT_GEOLIVE" -> LocaleController.formatString("PushChatReactGeoLive", R.string.PushChatReactGeoLive, *args)
			"CHAT_REACT_POLL" -> LocaleController.formatString("PushChatReactPoll", R.string.PushChatReactPoll, *args)
			"CHAT_REACT_QUIZ" -> LocaleController.formatString("PushChatReactQuiz", R.string.PushChatReactQuiz, *args)
			"CHAT_REACT_GAME" -> LocaleController.formatString("PushChatReactGame", R.string.PushChatReactGame, *args)
			"CHAT_REACT_INVOICE" -> LocaleController.formatString("PushChatReactInvoice", R.string.PushChatReactInvoice, *args)
			"CHAT_REACT_GIF" -> LocaleController.formatString("PushChatReactGif", R.string.PushChatReactGif, *args)
			else -> null
		}
	}

	private fun onDecryptError() {
		for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
			if (UserConfig.getInstance(a).isClientActivated) {
				ConnectionsManager.onInternalPushReceived(a)
				ConnectionsManager.getInstance(a).resumeNetworkMaybe()
			}
		}

		countDownLatch.countDown()
	}

	@Retention(AnnotationRetention.SOURCE)
	@IntDef(PUSH_TYPE_FIREBASE)
	annotation class PushType

	interface IPushListenerServiceProvider {
		fun hasServices(): Boolean

		val logTitle: String

		fun onRequestPushToken()

		@get:PushType
		val pushType: Int
	}

	class GooglePushListenerServiceProvider private constructor() : IPushListenerServiceProvider {
		private var hasServices: Boolean? = null

		override val logTitle: String
			get() = "Google Play Services"
		override val pushType: Int
			get() = PUSH_TYPE_FIREBASE

		override fun onRequestPushToken() {
			val currentPushString = SharedConfig.pushString

			if (!currentPushString.isNullOrEmpty()) {
				FileLog.d("FCM regId = $currentPushString")
			}
			else {
				FileLog.d("FCM Registration not found.")
			}

			Utilities.globalQueue.postRunnable {
				try {
					SharedConfig.pushStringGetTimeStart = SystemClock.elapsedRealtime()

					FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
						SharedConfig.pushStringGetTimeEnd = SystemClock.elapsedRealtime()

						if (!task.isSuccessful) {
							FileLog.d("Failed to get regid")
							SharedConfig.pushStringStatus = "__FIREBASE_FAILED__"
							sendRegistrationToServer(pushType, null)
							return@addOnCompleteListener
						}

						val token = task.result

						if (!token.isNullOrEmpty()) {
							sendRegistrationToServer(pushType, token)
						}
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
		}

		override fun hasServices(): Boolean {
			if (hasServices == null) {
				hasServices = try {
					val resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ApplicationLoader.applicationContext)
					resultCode == ConnectionResult.SUCCESS
				}
				catch (e: Exception) {
					FileLog.e(e)
					false
				}
			}

			return hasServices!!
		}

		companion object {
			@JvmField
			val INSTANCE = GooglePushListenerServiceProvider()
		}
	}
}
