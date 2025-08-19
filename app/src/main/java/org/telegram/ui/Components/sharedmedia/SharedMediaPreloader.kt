/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Components.sharedmedia

import org.telegram.messenger.ChatObject
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.migratedFromChatId
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.MediaActivity
import org.telegram.ui.ProfileActivity
import kotlin.math.max

class SharedMediaPreloader(private val parentFragment: BaseFragment) : NotificationCenter.NotificationCenterDelegate {
	private var mediaCount = intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)
	private var mediaMergeCount = intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)
	val lastMediaCount = intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)
	private val lastLoadMediaCount = intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)
	val sharedMediaData: List<SharedMediaData>
	private var dialogId: Long = 0
	private var mergeDialogId: Long = 0
	private val delegates = mutableListOf<SharedMediaLayout.SharedMediaPreloaderDelegate>()

	var isMediaWasLoaded = false
		private set

	init {
		when (parentFragment) {
			is ChatActivity -> {
				val chatActivity = parentFragment
				dialogId = chatActivity.dialogId
				mergeDialogId = chatActivity.mergeDialogId
			}

			is ProfileActivity -> {
				dialogId = parentFragment.getDialogId()
			}

			is MediaActivity -> {
				dialogId = parentFragment.dialogId
			}
		}

		sharedMediaData = (0..6).map {
			SharedMediaData().apply {
				setMaxId(0, if (DialogObject.isEncryptedDialog(dialogId)) Int.MIN_VALUE else Int.MAX_VALUE)
			}
		}

		loadMediaCounts()

		val notificationCenter = parentFragment.notificationCenter
		notificationCenter.addObserver(this, NotificationCenter.mediaCountsDidLoad)
		notificationCenter.addObserver(this, NotificationCenter.mediaCountDidLoad)
		notificationCenter.addObserver(this, NotificationCenter.didReceiveNewMessages)
		notificationCenter.addObserver(this, NotificationCenter.messageReceivedByServer)
		notificationCenter.addObserver(this, NotificationCenter.mediaDidLoad)
		notificationCenter.addObserver(this, NotificationCenter.messagesDeleted)
		notificationCenter.addObserver(this, NotificationCenter.replaceMessagesObjects)
		notificationCenter.addObserver(this, NotificationCenter.chatInfoDidLoad)
		notificationCenter.addObserver(this, NotificationCenter.fileLoaded)
	}

	fun addDelegate(delegate: SharedMediaLayout.SharedMediaPreloaderDelegate) {
		delegates.add(delegate)
	}

	fun removeDelegate(delegate: SharedMediaLayout.SharedMediaPreloaderDelegate) {
		delegates.remove(delegate)
	}

	fun onDestroy(fragment: BaseFragment) {
		if (fragment !== parentFragment) {
			return
		}

		delegates.clear()

		val notificationCenter = parentFragment.notificationCenter
		notificationCenter.removeObserver(this, NotificationCenter.mediaCountsDidLoad)
		notificationCenter.removeObserver(this, NotificationCenter.mediaCountDidLoad)
		notificationCenter.removeObserver(this, NotificationCenter.didReceiveNewMessages)
		notificationCenter.removeObserver(this, NotificationCenter.messageReceivedByServer)
		notificationCenter.removeObserver(this, NotificationCenter.mediaDidLoad)
		notificationCenter.removeObserver(this, NotificationCenter.messagesDeleted)
		notificationCenter.removeObserver(this, NotificationCenter.replaceMessagesObjects)
		notificationCenter.removeObserver(this, NotificationCenter.chatInfoDidLoad)
		notificationCenter.removeObserver(this, NotificationCenter.fileLoaded)
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.mediaCountsDidLoad -> {
				val did = args[0] as Long

				if (did == dialogId || did == mergeDialogId) {
					val counts = args[1] as IntArray

					if (did == dialogId) {
						mediaCount = counts
					}
					else {
						mediaMergeCount = counts
					}

					for (a in counts.indices) {
						if (mediaCount[a] >= 0 && mediaMergeCount[a] >= 0) {
							lastMediaCount[a] = mediaCount[a] + mediaMergeCount[a]
						}
						else if (mediaCount[a] >= 0) {
							lastMediaCount[a] = mediaCount[a]
						}
						else {
							lastMediaCount[a] = max(mediaMergeCount[a], 0)
						}

						if (did == dialogId && lastMediaCount[a] != 0 && lastLoadMediaCount[a] != mediaCount[a]) {
							var type = a

							if (type == 0) {
								if (sharedMediaData[0].filterType == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
									type = MediaDataController.MEDIA_PHOTOS_ONLY
								}
								else if (sharedMediaData[0].filterType == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
									type = MediaDataController.MEDIA_VIDEOS_ONLY
								}
							}

							parentFragment.mediaDataController.loadMedia(did, if (lastLoadMediaCount[a] == -1) 30 else 20, 0, 0, type, 2, parentFragment.classGuid, 0)
							lastLoadMediaCount[a] = mediaCount[a]
						}
					}

					isMediaWasLoaded = true

					for (delegate in delegates) {
						delegate.mediaCountUpdated()
					}
				}
			}

			NotificationCenter.mediaCountDidLoad -> {
				val did = args[0] as Long

				if (did == dialogId || did == mergeDialogId) {
					val type = args[3] as Int
					val mCount = args[1] as Int

					if (did == dialogId) {
						mediaCount[type] = mCount
					}
					else {
						mediaMergeCount[type] = mCount
					}

					if (mediaCount[type] >= 0 && mediaMergeCount[type] >= 0) {
						lastMediaCount[type] = mediaCount[type] + mediaMergeCount[type]
					}
					else if (mediaCount[type] >= 0) {
						lastMediaCount[type] = mediaCount[type]
					}
					else {
						lastMediaCount[type] = max(mediaMergeCount[type], 0)
					}

					for (delegate in delegates) {
						delegate.mediaCountUpdated()
					}
				}
			}

			NotificationCenter.didReceiveNewMessages -> {
				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				if (dialogId == args[0] as Long) {
					val enc = DialogObject.isEncryptedDialog(dialogId)
					val arr = args[1] as List<MessageObject>

					for (a in arr.indices) {
						val obj = arr[a]

						if (MessageObject.getMedia(obj.messageOwner) == null || obj.needDrawBluredPreview()) {
							continue
						}

						val type = MediaDataController.getMediaType(obj.messageOwner)

						if (type == -1) {
							continue
						}

						if (type == 0 && sharedMediaData[0].filterType == SharedMediaLayout.FILTER_VIDEOS_ONLY && !obj.isVideo) {
							continue
						}

						if (type == 0 && sharedMediaData[0].filterType == SharedMediaLayout.FILTER_PHOTOS_ONLY && obj.isVideo) {
							continue
						}

						if (sharedMediaData[type].startReached) {
							sharedMediaData[type].addMessage(obj, 0, true, enc)
						}

						sharedMediaData[type].totalCount++

						for (i in sharedMediaData[type].fastScrollPeriods.indices) {
							sharedMediaData[type].fastScrollPeriods[i].startOffset++
						}
					}

					loadMediaCounts()
				}
			}

			NotificationCenter.messageReceivedByServer -> {
				val scheduled = args[6] as Boolean

				if (scheduled) {
					return
				}

				val msgId = args[0] as Int
				val newMsgId = args[1] as Int

				for (sharedMediaDatum in sharedMediaData) {
					sharedMediaDatum.replaceMid(msgId, newMsgId)
				}
			}

			NotificationCenter.mediaDidLoad -> {
				val did = args[0] as Long
				val guid = args[3] as Int

				if (guid == parentFragment.classGuid) {
					var type = args[4] as Int

					if (type != 0 && type != 6 && type != 7 && type != 1 && type != 2 && type != 4) {
						sharedMediaData[type].totalCount = args[1] as Int
					}

					val arr = args[2] as List<MessageObject>
					val enc = DialogObject.isEncryptedDialog(did)
					val loadIndex = if (did == dialogId) 0 else 1

					if (type == 0 || type == 6 || type == 7) {
						if (type != sharedMediaData[0].filterType) {
							return
						}

						type = 0
					}

					if (arr.isNotEmpty()) {
						sharedMediaData[type].setEndReached(loadIndex, args[5] as Boolean)
					}

					for (message in arr) {
						sharedMediaData[type].addMessage(message, loadIndex, false, enc)
					}
				}
			}

			NotificationCenter.messagesDeleted -> {
				val scheduled = args[2] as Boolean

				if (scheduled) {
					return
				}

				val channelId = args[1] as Long

				val currentChat = if (DialogObject.isChatDialog(dialogId)) {
					parentFragment.messagesController.getChat(-dialogId)
				}
				else {
					null
				}

				if (ChatObject.isChannel(currentChat)) {
					if (!(channelId == 0L && mergeDialogId != 0L || channelId == currentChat.id)) {
						return
					}
				}
				else if (channelId != 0L) {
					return
				}

				var changed = false
				val markAsDeletedMessages = args[0] as List<Int>
				var a = 0
				val n = markAsDeletedMessages.size

				while (a < n) {
					for (b in sharedMediaData.indices) {
						val messageObject = sharedMediaData[b].deleteMessage(markAsDeletedMessages[a], 0)

						if (messageObject != null) {
							if (messageObject.dialogId == dialogId) {
								if (mediaCount[b] > 0) {
									mediaCount[b]--
								}
							}
							else {
								if (mediaMergeCount[b] > 0) {
									mediaMergeCount[b]--
								}
							}

							changed = true
						}
					}

					a++
				}

				if (changed) {
					for (@Suppress("NAME_SHADOWING") a in mediaCount.indices) {
						if (mediaCount[a] >= 0 && mediaMergeCount[a] >= 0) {
							lastMediaCount[a] = mediaCount[a] + mediaMergeCount[a]
						}
						else if (mediaCount[a] >= 0) {
							lastMediaCount[a] = mediaCount[a]
						}
						else {
							lastMediaCount[a] = max(mediaMergeCount[a], 0)
						}
					}

					for (delegate in delegates) {
						delegate.mediaCountUpdated()
					}
				}

				loadMediaCounts()
			}

			NotificationCenter.replaceMessagesObjects -> {
				val did = args[0] as Long

				if (did != dialogId && did != mergeDialogId) {
					return
				}

				val loadIndex = if (did == dialogId) 0 else 1
				val messageObjects = args[1] as List<MessageObject>
				var b = 0
				val n = messageObjects.size

				while (b < n) {
					val messageObject = messageObjects[b]
					val mid = messageObject.id
					val type = MediaDataController.getMediaType(messageObject.messageOwner)

					for (a in sharedMediaData.indices) {
						val old = sharedMediaData[a].messagesDict[loadIndex][mid]

						if (old != null) {
							val oldType = MediaDataController.getMediaType(messageObject.messageOwner)

							if (type == -1 || oldType != type) {
								sharedMediaData[a].deleteMessage(mid, loadIndex)

								if (loadIndex == 0) {
									if (mediaCount[a] > 0) {
										mediaCount[a]--
									}
								}
								else {
									if (mediaMergeCount[a] > 0) {
										mediaMergeCount[a]--
									}
								}
							}
							else {
								val idx = sharedMediaData[a].messages.indexOf(old)

								if (idx >= 0) {
									sharedMediaData[a].messagesDict[loadIndex].put(mid, messageObject)
									sharedMediaData[a].messages[idx] = messageObject
								}
							}

							break
						}
					}

					b++
				}
			}

			NotificationCenter.chatInfoDidLoad -> {
				val chatFull = args[0] as TLRPC.ChatFull

				if (dialogId < 0 && chatFull.id == -dialogId) {
					setChatInfo(chatFull)
				}
			}

			NotificationCenter.fileLoaded -> {
				val allMessages = mutableListOf<MessageObject>()

				for (sharedMediaDatum in sharedMediaData) {
					allMessages.addAll(sharedMediaDatum.messages)
				}

				Utilities.globalQueue.postRunnable {
					FileLoader.getInstance(account).checkMediaExistence(allMessages)
				}
			}
		}
	}

	private fun loadMediaCounts() {
		parentFragment.mediaDataController.getMediaCounts(dialogId, parentFragment.classGuid)

		if (mergeDialogId != 0L) {
			parentFragment.mediaDataController.getMediaCounts(mergeDialogId, parentFragment.classGuid)
		}
	}

	private fun setChatInfo(chatInfo: TLRPC.ChatFull?) {
		if (chatInfo != null && chatInfo.migratedFromChatId != 0L && mergeDialogId == 0L) {
			mergeDialogId = -chatInfo.migratedFromChatId
			parentFragment.mediaDataController.getMediaCounts(mergeDialogId, parentFragment.classGuid)
		}
	}
}
