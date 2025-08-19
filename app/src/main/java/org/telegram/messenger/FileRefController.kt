/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.messenger

import android.os.SystemClock
import org.telegram.messenger.FileLoadOperation.RequestInfo
import org.telegram.messenger.SendMessagesHelper.DelayedMessage
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.RequestDelegate
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.InputFileLocation
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.Message
import org.telegram.tgnet.TLRPC.MessagesMessages
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.PhotosPhotos
import org.telegram.tgnet.TLRPC.StickerSetCovered
import org.telegram.tgnet.TLRPC.TLAccountGetTheme
import org.telegram.tgnet.TLRPC.TLAccountGetWallPaper
import org.telegram.tgnet.TLRPC.TLAccountGetWallPapers
import org.telegram.tgnet.TLRPC.TLAccountWallPapers
import org.telegram.tgnet.TLRPC.TLAttachMenuBot
import org.telegram.tgnet.TLRPC.TLAttachMenuBotsBot
import org.telegram.tgnet.TLRPC.TLAvailableReaction
import org.telegram.tgnet.TLRPC.TLBotInfo
import org.telegram.tgnet.TLRPC.TLChannel
import org.telegram.tgnet.TLRPC.TLChannelsGetChannels
import org.telegram.tgnet.TLRPC.TLChannelsGetMessages
import org.telegram.tgnet.TLRPC.TLChat
import org.telegram.tgnet.TLRPC.TLError
import org.telegram.tgnet.TLRPC.TLHelpAppUpdate
import org.telegram.tgnet.TLRPC.TLHelpGetAppUpdate
import org.telegram.tgnet.TLRPC.TLInputDocumentFileLocation
import org.telegram.tgnet.TLRPC.TLInputFileLocation
import org.telegram.tgnet.TLRPC.TLInputMediaDocument
import org.telegram.tgnet.TLRPC.TLInputMediaPhoto
import org.telegram.tgnet.TLRPC.TLInputMessagesFilterChatPhotos
import org.telegram.tgnet.TLRPC.TLInputPeerChannel
import org.telegram.tgnet.TLRPC.TLInputPeerChat
import org.telegram.tgnet.TLRPC.TLInputPeerPhotoFileLocation
import org.telegram.tgnet.TLRPC.TLInputPeerUser
import org.telegram.tgnet.TLRPC.TLInputPhotoFileLocation
import org.telegram.tgnet.TLRPC.TLInputSingleMedia
import org.telegram.tgnet.TLRPC.TLInputStickerSetID
import org.telegram.tgnet.TLRPC.TLInputStickeredMediaDocument
import org.telegram.tgnet.TLRPC.TLInputStickeredMediaPhoto
import org.telegram.tgnet.TLRPC.TLInputTheme
import org.telegram.tgnet.TLRPC.TLInputWallPaper
import org.telegram.tgnet.TLRPC.TLMessageActionChatEditPhoto
import org.telegram.tgnet.TLRPC.TLMessagesAvailableReactions
import org.telegram.tgnet.TLRPC.TLMessagesChats
import org.telegram.tgnet.TLRPC.TLMessagesEditMessage
import org.telegram.tgnet.TLRPC.TLMessagesFaveSticker
import org.telegram.tgnet.TLRPC.TLMessagesFavedStickers
import org.telegram.tgnet.TLRPC.TLMessagesGetAttachMenuBot
import org.telegram.tgnet.TLRPC.TLMessagesGetAttachedStickers
import org.telegram.tgnet.TLRPC.TLMessagesGetAvailableReactions
import org.telegram.tgnet.TLRPC.TLMessagesGetChats
import org.telegram.tgnet.TLRPC.TLMessagesGetFavedStickers
import org.telegram.tgnet.TLRPC.TLMessagesGetMessages
import org.telegram.tgnet.TLRPC.TLMessagesGetRecentStickers
import org.telegram.tgnet.TLRPC.TLMessagesGetSavedGifs
import org.telegram.tgnet.TLRPC.TLMessagesGetScheduledMessages
import org.telegram.tgnet.TLRPC.TLMessagesGetStickerSet
import org.telegram.tgnet.TLRPC.TLMessagesGetWebPage
import org.telegram.tgnet.TLRPC.TLMessagesRecentStickers
import org.telegram.tgnet.TLRPC.TLMessagesSaveGif
import org.telegram.tgnet.TLRPC.TLMessagesSaveRecentSticker
import org.telegram.tgnet.TLRPC.TLMessagesSavedGifs
import org.telegram.tgnet.TLRPC.TLMessagesSearch
import org.telegram.tgnet.TLRPC.TLMessagesSendMedia
import org.telegram.tgnet.TLRPC.TLMessagesSendMultiMedia
import org.telegram.tgnet.TLRPC.TLMessagesStickerSet
import org.telegram.tgnet.TLRPC.TLPhotosGetUserPhotos
import org.telegram.tgnet.TLRPC.TLTheme
import org.telegram.tgnet.TLRPC.TLUsersGetFullUser
import org.telegram.tgnet.TLRPC.TLUsersGetUsers
import org.telegram.tgnet.TLRPC.TLUsersUserFull
import org.telegram.tgnet.TLRPC.TLWallPaper
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.tgnet.Vector
import org.telegram.tgnet.action
import org.telegram.tgnet.channelId
import org.telegram.tgnet.document
import org.telegram.tgnet.fileReference
import org.telegram.tgnet.game
import org.telegram.tgnet.id
import org.telegram.tgnet.media
import org.telegram.tgnet.photo
import org.telegram.tgnet.photoBig
import org.telegram.tgnet.photoSmall
import org.telegram.tgnet.webpage
import org.telegram.ui.ActionBar.Theme
import kotlin.math.abs

class FileRefController(instance: Int) : BaseController(instance) {
	private class Requester {
		var location: InputFileLocation? = null
		var args: Array<Any>? = null
		var locationKey: String? = null
		var completed: Boolean = false
	}

	private class CachedResult {
		var response: TLObject? = null
		var lastQueryTime: Long = 0
		var firstQueryTime: Long = 0
	}

	private data class Waiter(val locationKey: String, val parentKey: String)

	private val locationRequester = mutableMapOf<String, ArrayList<Requester>>()
	private val parentRequester = mutableMapOf<String, ArrayList<Requester>>()
	private val responseCache = mutableMapOf<String, CachedResult>()
	private val multiMediaCache = mutableMapOf<TLMessagesSendMultiMedia, Array<Any>>()
	private var lastCleanupTime = SystemClock.elapsedRealtime()
	private val wallpaperWaiters = mutableListOf<Waiter>()
	private val savedGifsWaiters = mutableListOf<Waiter>()
	private val recentStickersWaiter = mutableListOf<Waiter>()
	private val favStickersWaiter = mutableListOf<Waiter>()

	fun requestReference(parentObject: Any?, vararg args: Any?) {
		@Suppress("NAME_SHADOWING") var parentObject = parentObject
		val locationKey: String
		val location: InputFileLocation

		if (BuildConfig.DEBUG) {
			FileLog.d("start loading request reference for parent = " + parentObject + " args = " + args[0])
		}

		when (val firstArgument = args[0]) {
			is TLInputSingleMedia -> {
				when (firstArgument.media) {
					is TLInputMediaDocument -> {
						val mediaDocument = firstArgument.media as TLInputMediaDocument

						locationKey = "file_" + mediaDocument.id?.id
						location = TLInputDocumentFileLocation()
						location.id = mediaDocument.id?.id ?: 0
					}

					is TLInputMediaPhoto -> {
						val mediaPhoto = firstArgument.media as TLInputMediaPhoto

						locationKey = "photo_" + mediaPhoto.id?.id
						location = TLInputPhotoFileLocation()
						location.id = mediaPhoto.id?.id ?: 0
					}

					else -> {
						sendErrorToObject(args.toList().toTypedArray(), 0)
						return
					}
				}
			}

			is TLMessagesSendMultiMedia -> {
				val parentObjects = parentObject as? List<Any>

				multiMediaCache[firstArgument] = args.toList().filterNotNull().toTypedArray()

				var a = 0
				val size = firstArgument.multiMedia.size

				while (a < size) {
					val media = firstArgument.multiMedia[a]

					parentObject = parentObjects?.getOrNull(a)

					if (parentObject == null) {
						a++
						continue
					}

					requestReference(parentObject, media, firstArgument)

					a++
				}

				return
			}

			is TLMessagesSendMedia -> {
				when (firstArgument.media) {
					is TLInputMediaDocument -> {
						val mediaDocument = firstArgument.media as TLInputMediaDocument

						locationKey = "file_" + mediaDocument.id?.id
						location = TLInputDocumentFileLocation()
						location.id = mediaDocument.id?.id ?: 0
					}

					is TLInputMediaPhoto -> {
						val mediaPhoto = firstArgument.media as TLInputMediaPhoto

						locationKey = "photo_" + mediaPhoto.id?.id
						location = TLInputPhotoFileLocation()
						location.id = mediaPhoto.id?.id ?: 0
					}

					else -> {
						sendErrorToObject(args.toList().toTypedArray(), 0)
						return
					}
				}
			}

			is TLMessagesEditMessage -> {
				when (firstArgument.media) {
					is TLInputMediaDocument -> {
						val mediaDocument = firstArgument.media as TLInputMediaDocument

						locationKey = "file_" + mediaDocument.id?.id
						location = TLInputDocumentFileLocation()
						location.id = mediaDocument.id?.id ?: 0
					}

					is TLInputMediaPhoto -> {
						val mediaPhoto = firstArgument.media as TLInputMediaPhoto

						locationKey = "photo_" + mediaPhoto.id?.id
						location = TLInputPhotoFileLocation()
						location.id = mediaPhoto.id?.id ?: 0
					}

					else -> {
						sendErrorToObject(args.toList().toTypedArray(), 0)
						return
					}
				}
			}

			is TLMessagesSaveGif -> {
				locationKey = "file_" + firstArgument.id?.id
				location = TLInputDocumentFileLocation()
				location.id = firstArgument.id?.id ?: 0
			}

			is TLMessagesSaveRecentSticker -> {
				locationKey = "file_" + firstArgument.id?.id
				location = TLInputDocumentFileLocation()
				location.id = firstArgument.id?.id ?: 0
			}

			is TLMessagesFaveSticker -> {
				locationKey = "file_" + firstArgument.id?.id
				location = TLInputDocumentFileLocation()
				location.id = firstArgument.id?.id ?: 0
			}

			is TLMessagesGetAttachedStickers -> {
				when (firstArgument.media) {
					is TLInputStickeredMediaDocument -> {
						val mediaDocument = firstArgument.media as TLInputStickeredMediaDocument

						locationKey = "file_" + mediaDocument.id?.id
						location = TLInputDocumentFileLocation()
						location.id = mediaDocument.id?.id ?: 0
					}

					is TLInputStickeredMediaPhoto -> {
						val mediaPhoto = firstArgument.media as TLInputStickeredMediaPhoto

						locationKey = "photo_" + mediaPhoto.id?.id
						location = TLInputPhotoFileLocation()
						location.id = mediaPhoto.id?.id ?: 0
					}

					else -> {
						sendErrorToObject(args.toList().toTypedArray(), 0)
						return
					}
				}
			}

			is TLInputFileLocation -> {
				location = firstArgument
				locationKey = "loc_" + location.localId + "_" + location.volumeId
			}

			is TLInputDocumentFileLocation -> {
				location = firstArgument
				locationKey = "file_" + location.id
			}

			is TLInputPhotoFileLocation -> {
				location = firstArgument
				locationKey = "photo_" + location.id
			}

			is TLInputPeerPhotoFileLocation -> {
				location = firstArgument
				locationKey = "avatar_" + location.id
			}

			else -> {
				sendErrorToObject(args.toList().toTypedArray(), 0)
				return
			}
		}

		if (parentObject is MessageObject) {
			val messageObject = parentObject

			if (messageObject.realId < 0 && messageObject.messageOwner?.media?.webpage != null) {
				parentObject = messageObject.messageOwner?.media?.webpage
			}
		}

		val parentKey = getKeyForParentObject(parentObject)

		if (parentKey == null) {
			sendErrorToObject(args.toList().toTypedArray(), 0)
			return
		}

		val requester = Requester()
		requester.args = args.toList().filterNotNull().toTypedArray()
		requester.location = location
		requester.locationKey = locationKey

		var added = 0
		var arrayList = locationRequester[locationKey]

		if (arrayList == null) {
			arrayList = ArrayList()
			locationRequester[locationKey] = arrayList
			added++
		}

		arrayList.add(requester)

		arrayList = parentRequester[parentKey]

		if (arrayList == null) {
			arrayList = ArrayList()
			parentRequester[parentKey] = arrayList
			added++
		}

		arrayList.add(requester)

		if (added != 2) {
			return
		}

		var cacheKey = locationKey

		if (parentObject is String) {
			val string = parentObject

			if ("wallpaper" == string) {
				cacheKey = "wallpaper"
			}
			else if (string.startsWith("gif")) {
				cacheKey = "gif"
			}
			else if ("recent" == string) {
				cacheKey = "recent"
			}
			else if ("fav" == string) {
				cacheKey = "fav"
			}
			else if ("update" == string) {
				cacheKey = "update"
			}
		}

		cleanupCache()

		var cachedResult = getCachedResponse(cacheKey)

		if (cachedResult != null) {
			if (!onRequestComplete(locationKey, parentKey, cachedResult.response, cache = false, fromCache = true)) {
				responseCache.remove(locationKey)
			}
			else {
				return
			}
		}
		else {
			cachedResult = getCachedResponse(parentKey)

			if (cachedResult != null) {
				if (!onRequestComplete(locationKey, parentKey, cachedResult.response, cache = false, fromCache = true)) {
					responseCache.remove(parentKey)
				}
				else {
					return
				}
			}
		}

		requestReferenceFromServer(parentObject, locationKey, parentKey, args.toList().filterNotNull().toTypedArray())
	}

	private fun broadcastWaitersData(waiters: MutableList<Waiter>, response: TLObject?) {
		var a = 0
		val n = waiters.size

		while (a < n) {
			val waiter = waiters[a]
			onRequestComplete(waiter.locationKey, waiter.parentKey, response, a == n - 1, false)
			a++
		}

		waiters.clear()
	}

	private fun requestReferenceFromServer(parentObject: Any?, locationKey: String, parentKey: String, args: Array<Any?>?) {
		when (parentObject) {
			is TLAvailableReaction -> {
				val req = TLMessagesGetAvailableReactions()
				req.hash = 0

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is TLBotInfo -> {
				val req = TLUsersGetFullUser()
				req.id = messagesController.getInputUser(parentObject.userId)

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is TLAttachMenuBot -> {
				val req = TLMessagesGetAttachMenuBot()
				req.bot = messagesController.getInputUser(parentObject.botId)

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is MessageObject -> {
				val channelId = parentObject.channelId

				if (parentObject.scheduled) {
					val req = TLMessagesGetScheduledMessages()
					req.peer = messagesController.getInputPeer(parentObject.dialogId)
					req.id.add(parentObject.realId)

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
				else if (channelId != 0L) {
					val req = TLChannelsGetMessages()
					req.channel = messagesController.getInputChannel(channelId)
					req.id.add(TLRPC.TLInputMessageID().also { it.id = parentObject.realId })

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
				else {
					val req = TLMessagesGetMessages()
					req.id.add(TLRPC.TLInputMessageID().also { it.id = parentObject.realId })

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
			}

			is TLWallPaper -> {
				val req = TLAccountGetWallPaper()

				val inputWallPaper = TLInputWallPaper()
				inputWallPaper.id = parentObject.id
				inputWallPaper.accessHash = parentObject.accessHash

				req.wallpaper = inputWallPaper

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is TLTheme -> {
				val req = TLAccountGetTheme()

				val inputTheme = TLInputTheme()
				inputTheme.id = parentObject.id
				inputTheme.accessHash = parentObject.accessHash

				req.theme = inputTheme
				req.format = "android"

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is WebPage -> {
				val req = TLMessagesGetWebPage()
				req.url = parentObject.url
				req.hash = 0

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is User -> {
				val req = TLUsersGetUsers()
				req.id.add(messagesController.getInputUser(parentObject))

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is Chat -> {
				if (parentObject is TLChat) {
					val req = TLMessagesGetChats()
					req.id.add(parentObject.id)

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
				else if (parentObject is TLChannel) {
					val req = TLChannelsGetChannels()
					req.id.add(MessagesController.getInputChannel(parentObject))

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
			}

			is String -> {
				if ("wallpaper" == parentObject) {
					if (wallpaperWaiters.isEmpty()) {
						val req = TLAccountGetWallPapers()

						connectionsManager.sendRequest(req) { response, _ ->
							broadcastWaitersData(wallpaperWaiters, response)
						}
					}

					wallpaperWaiters.add(Waiter(locationKey, parentKey))
				}
				else if (parentObject.startsWith("gif")) {
					if (savedGifsWaiters.isEmpty()) {
						val req = TLMessagesGetSavedGifs()

						connectionsManager.sendRequest(req) { response, _ ->
							broadcastWaitersData(savedGifsWaiters, response)
						}
					}

					savedGifsWaiters.add(Waiter(locationKey, parentKey))
				}
				else if ("recent" == parentObject) {
					if (recentStickersWaiter.isEmpty()) {
						val req = TLMessagesGetRecentStickers()

						connectionsManager.sendRequest(req) { response, _ ->
							broadcastWaitersData(recentStickersWaiter, response)
						}
					}

					recentStickersWaiter.add(Waiter(locationKey, parentKey))
				}
				else if ("fav" == parentObject) {
					if (favStickersWaiter.isEmpty()) {
						val req = TLMessagesGetFavedStickers()

						connectionsManager.sendRequest(req) { response, _ ->
							broadcastWaitersData(favStickersWaiter, response)
						}
					}

					favStickersWaiter.add(Waiter(locationKey, parentKey))
				}
				else if ("update" == parentObject) {
					val req = TLHelpGetAppUpdate()

					runCatching {
						req.source = ApplicationLoader.applicationContext.packageManager.getInstallerPackageName(ApplicationLoader.applicationContext.packageName)
					}

					if (req.source == null) {
						req.source = ""
					}

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
				else if (parentObject.startsWith("avatar_")) {
					val id = Utilities.parseLong(parentObject)

					if (id > 0) {
						val req = TLPhotosGetUserPhotos()
						req.limit = 80
						req.offset = 0
						req.maxId = 0
						req.userId = messagesController.getInputUser(id)

						connectionsManager.sendRequest(req) { response, _ ->
							onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
						}
					}
					else {
						val req = TLMessagesSearch()
						req.filter = TLInputMessagesFilterChatPhotos()
						req.limit = 80
						req.offsetId = 0
						req.q = ""
						req.peer = messagesController.getInputPeer(id)

						connectionsManager.sendRequest(req) { response, _ ->
							onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
						}
					}
				}
				else if (parentObject.startsWith("sent_")) {
					val params = parentObject.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

					if (params.size == 3) {
						val channelId = Utilities.parseLong(params[1])

						if (channelId != 0L) {
							val req = TLChannelsGetMessages()
							req.channel = messagesController.getInputChannel(channelId)
							req.id.add(TLRPC.TLInputMessageID().also { it.id = Utilities.parseInt(params[2]) })

							connectionsManager.sendRequest(req) { response, _ ->
								onRequestComplete(locationKey, parentKey, response, cache = false, fromCache = false)
							}
						}
						else {
							val req = TLMessagesGetMessages()
							req.id.add(TLRPC.TLInputMessageID().also { it.id = Utilities.parseInt(params[2]) })

							connectionsManager.sendRequest(req) { response, _ ->
								onRequestComplete(locationKey, parentKey, response, cache = false, fromCache = false)
							}
						}
					}
					else {
						sendErrorToObject(args, 0)
					}
				}
				else {
					sendErrorToObject(args, 0)
				}
			}

			is TLMessagesStickerSet -> {
				val req = TLMessagesGetStickerSet()

				req.stickerset = TLInputStickerSetID().also {
					it.id = parentObject.set?.id ?: 0
					it.accessHash = parentObject.set?.accessHash ?: 0
				}

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is StickerSetCovered -> {
				val req = TLMessagesGetStickerSet()

				req.stickerset = TLInputStickerSetID().also {
					it.id = parentObject.set?.id ?: 0
					it.accessHash = parentObject.set?.accessHash ?: 0
				}

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is InputStickerSet -> {
				val req = TLMessagesGetStickerSet()
				req.stickerset = parentObject

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			else -> {
				sendErrorToObject(args, 0)
			}
		}
	}

	private fun isSameReference(oldRef: ByteArray?, newRef: ByteArray?): Boolean {
		if (oldRef == null || newRef == null) {
			return false
		}

		return oldRef.contentEquals(newRef)
	}

	private fun onUpdateObjectReference(requester: Requester, fileReference: ByteArray, locationReplacement: InputFileLocation?, fromCache: Boolean): Boolean {
		val firstArgument = requester.args?.firstOrNull()

		if (BuildConfig.DEBUG) {
			FileLog.d("fileref updated for " + firstArgument + " " + requester.locationKey)
		}

		if (firstArgument is TLInputSingleMedia) {
			val multiMedia = requester.args?.get(1) as TLMessagesSendMultiMedia
			val objects = multiMediaCache[multiMedia] ?: return true

			if (firstArgument.media is TLInputMediaDocument) {
				val mediaDocument = firstArgument.media as TLInputMediaDocument

				if (fromCache && isSameReference(mediaDocument.id?.fileReference, fileReference)) {
					return false
				}

				mediaDocument.id?.fileReference = fileReference
			}
			else if (firstArgument.media is TLInputMediaPhoto) {
				val mediaPhoto = firstArgument.media as TLInputMediaPhoto

				if (fromCache && isSameReference(mediaPhoto.id?.fileReference, fileReference)) {
					return false
				}

				mediaPhoto.id?.fileReference = fileReference
			}

			val index = multiMedia.multiMedia.indexOf(firstArgument)

			if (index < 0) {
				return true
			}

			val parentObjects = (objects[3] as List<Any?>).toMutableList()
			parentObjects[index] = null

			var done = true
			var a = 0

			while (a < parentObjects.size) {
				if (parentObjects[a] != null) {
					done = false
				}

				a++
			}

			if (done) {
				multiMediaCache.remove(multiMedia)

				AndroidUtilities.runOnUIThread {
					sendMessagesHelper.performSendMessageRequestMulti(multiMedia, objects[1] as List<MessageObject>, objects[2] as List<String?>, null, objects[4] as DelayedMessage, (objects[5] as Boolean))
				}
			}
		}
		else if (firstArgument is TLMessagesSendMedia) {
			if (firstArgument.media is TLInputMediaDocument) {
				val mediaDocument = firstArgument.media as TLInputMediaDocument

				if (fromCache && isSameReference(mediaDocument.id?.fileReference, fileReference)) {
					return false
				}

				mediaDocument.id?.fileReference = fileReference
			}
			else if (firstArgument.media is TLInputMediaPhoto) {
				val mediaPhoto = firstArgument.media as TLInputMediaPhoto

				if (fromCache && isSameReference(mediaPhoto.id?.fileReference, fileReference)) {
					return false
				}

				mediaPhoto.id?.fileReference = fileReference
			}

			AndroidUtilities.runOnUIThread {
				sendMessagesHelper.performSendMessageRequest(firstArgument as TLObject, requester.args?.get(1) as MessageObject, requester.args?.get(2) as String, requester.args?.get(3) as DelayedMessage, (requester.args?.get(4) as Boolean), requester.args?.get(5) as DelayedMessage, null, (requester.args?.get(6) as Boolean))
			}
		}
		else if (firstArgument is TLMessagesEditMessage) {
			if (firstArgument.media is TLInputMediaDocument) {
				val mediaDocument = firstArgument.media as TLInputMediaDocument

				if (fromCache && isSameReference(mediaDocument.id?.fileReference, fileReference)) {
					return false
				}

				mediaDocument.id?.fileReference = fileReference
			}
			else if (firstArgument.media is TLInputMediaPhoto) {
				val mediaPhoto = firstArgument.media as TLInputMediaPhoto

				if (fromCache && isSameReference(mediaPhoto.id?.fileReference, fileReference)) {
					return false
				}

				mediaPhoto.id?.fileReference = fileReference
			}

			AndroidUtilities.runOnUIThread {
				sendMessagesHelper.performSendMessageRequest(firstArgument as TLObject, requester.args!![1] as MessageObject, requester.args!![2] as String, requester.args!![3] as DelayedMessage, (requester.args!![4] as Boolean), requester.args!![5] as DelayedMessage, null, (requester.args!![6] as Boolean))
			}
		}
		else if (firstArgument is TLMessagesSaveGif) {
			if (fromCache && isSameReference(firstArgument.id?.fileReference, fileReference)) {
				return false
			}

			firstArgument.id?.fileReference = fileReference

			connectionsManager.sendRequest(firstArgument)
		}
		else if (firstArgument is TLMessagesSaveRecentSticker) {
			if (fromCache && isSameReference(firstArgument.id?.fileReference, fileReference)) {
				return false
			}

			firstArgument.id?.fileReference = fileReference

			connectionsManager.sendRequest(firstArgument)
		}
		else if (firstArgument is TLMessagesFaveSticker) {
			if (fromCache && isSameReference(firstArgument.id?.fileReference, fileReference)) {
				return false
			}

			firstArgument.id?.fileReference = fileReference

			connectionsManager.sendRequest(firstArgument)
		}
		else if (firstArgument is TLMessagesGetAttachedStickers) {
			if (firstArgument.media is TLInputStickeredMediaDocument) {
				val mediaDocument = firstArgument.media as TLInputStickeredMediaDocument

				if (fromCache && isSameReference(mediaDocument.id?.fileReference, fileReference)) {
					return false
				}

				mediaDocument.id?.fileReference = fileReference
			}
			else if (firstArgument.media is TLInputStickeredMediaPhoto) {
				val mediaPhoto = firstArgument.media as TLInputStickeredMediaPhoto

				if (fromCache && isSameReference(mediaPhoto.id?.fileReference, fileReference)) {
					return false
				}

				mediaPhoto.id?.fileReference = fileReference
			}

			connectionsManager.sendRequest(firstArgument, requester.args!![1] as RequestDelegate)
		}
		else if (requester.args?.get(1) is FileLoadOperation) {
			val fileLoadOperation = requester.args!![1] as FileLoadOperation

			if (locationReplacement != null) {
				if (fromCache && isSameReference(fileLoadOperation.location!!.fileReference, locationReplacement.fileReference)) {
					return false
				}

				fileLoadOperation.location = locationReplacement
			}
			else {
				if (fromCache && isSameReference(requester.location!!.fileReference, fileReference)) {
					return false
				}

				requester.location!!.fileReference = fileReference
			}

			fileLoadOperation.requestingReference = false
			fileLoadOperation.startDownloadRequest()
		}

		return true
	}

	private fun sendErrorToObject(args: Array<Any?>?, reason: Int) {
		if (args.isNullOrEmpty()) {
			return
		}

		if (args[0] is TLInputSingleMedia) {
			val req = args[1] as TLMessagesSendMultiMedia
			val objects = multiMediaCache[req]

			if (objects != null) {
				multiMediaCache.remove(req)

				AndroidUtilities.runOnUIThread {
					sendMessagesHelper.performSendMessageRequestMulti(req, objects[1] as List<MessageObject>, objects[2] as List<String?>, null, objects[4] as DelayedMessage, (objects[5] as Boolean))
				}
			}
		}
		else if (args[0] is TLMessagesSendMedia || args[0] is TLMessagesEditMessage) {
			AndroidUtilities.runOnUIThread {
				sendMessagesHelper.performSendMessageRequest(args[0] as TLObject, args[1] as MessageObject, args[2] as String, args[3] as DelayedMessage, (args[4] as Boolean), args[5] as DelayedMessage, null, (args[6] as Boolean))
			}
		}
		else if (args[0] is TLMessagesSaveGif) {
			// val req = args[0] as TLMessagesSaveGif
			// do nothing
		}
		else if (args[0] is TLMessagesSaveRecentSticker) {
			// val req = args[0] as TLMessagesSaveRecentSticker
			// do nothing
		}
		else if (args[0] is TLMessagesFaveSticker) {
			// val req = args[0] as TLMessagesFaveSticker
			// do nothing
		}
		else if (args[0] is TLMessagesGetAttachedStickers) {
			val req = args[0] as TLMessagesGetAttachedStickers
			connectionsManager.sendRequest(req, args[1] as RequestDelegate)
		}
		else {
			if (reason == 0) {
				val error = TLError()
				error.text = "not found parent object to request reference"
				error.code = 400

				if (args[1] is FileLoadOperation) {
					val fileLoadOperation = args[1] as FileLoadOperation
					fileLoadOperation.requestingReference = false
					fileLoadOperation.processRequestResult((args[2] as RequestInfo), error)
				}
			}
			else if (reason == 1) {
				if (args[1] is FileLoadOperation) {
					val fileLoadOperation = args[1] as FileLoadOperation
					fileLoadOperation.requestingReference = false
					fileLoadOperation.onFail(false, 0)
				}
			}
		}
	}

	private fun onRequestComplete(locationKey: String?, parentKey: String?, response: TLObject?, cache: Boolean, fromCache: Boolean): Boolean {
		var found = false
		var cacheKey = parentKey

		when (response) {
			is TLAccountWallPapers -> cacheKey = "wallpaper"
			is TLMessagesSavedGifs -> cacheKey = "gif"
			is TLMessagesRecentStickers -> cacheKey = "recent"
			is TLMessagesFavedStickers -> cacheKey = "fav"
		}

		if (parentKey != null) {
			val arrayList = parentRequester[parentKey]

			if (arrayList != null) {
				for (requester in arrayList) {
					if (requester.completed) {
						continue
					}

					if (onRequestComplete(requester.locationKey, null, response, cache && !found, fromCache)) {
						found = true
					}
				}

				if (found) {
					cacheKey?.let {
						putResponseToCache(it, response)
					}
				}

				parentRequester.remove(parentKey)
			}
		}

		var result: ByteArray? = null
		var locationReplacement: Array<InputFileLocation?>? = null
		var needReplacement: BooleanArray? = null
		val arrayList = locationRequester[locationKey] ?: return found

		cacheKey = locationKey

		for (requester in arrayList) {
			if (requester.completed) {
				continue
			}

			if (requester.location is TLInputFileLocation || requester.location is TLInputPeerPhotoFileLocation) {
				locationReplacement = arrayOfNulls(1)
				needReplacement = BooleanArray(1)
			}

			requester.completed = true

			when (response) {
				is MessagesMessages -> {
					if (response.messages.isNotEmpty()) {
						var i = 0
						val size3 = response.messages.size

						while (i < size3) {
							val message = response.messages[i]

							if (message.media != null) {
								if (message.media!!.document != null) {
									result = getFileReference(message.media!!.document, requester.location, needReplacement, locationReplacement)
								}
								else if (message.media!!.game != null) {
									result = getFileReference(message.media!!.game?.document, requester.location, needReplacement, locationReplacement)

									if (result == null) {
										result = getFileReference(message.media!!.game?.photo, requester.location, needReplacement, locationReplacement)
									}
								}
								else if (message.media!!.photo != null) {
									result = getFileReference(message.media!!.photo, requester.location, needReplacement, locationReplacement)
								}
								else if (message.media!!.webpage != null) {
									result = getFileReference(message.media!!.webpage, requester.location, needReplacement, locationReplacement)
								}
							}
							else if (message.action is TLMessageActionChatEditPhoto) {
								result = getFileReference(message.action!!.photo, requester.location, needReplacement, locationReplacement)
							}

							if (result != null) {
								if (cache) {
									messagesStorage.replaceMessageIfExists(message, response.users, response.chats, false)
								}

								break
							}

							i++
						}

						if (result == null) {
							messagesStorage.replaceMessageIfExists(response.messages[0], response.users, response.chats, true)

							if (BuildConfig.DEBUG) {
								FileLog.d("file ref not found in messages, replacing message")
							}
						}
					}
				}

				is TLMessagesAvailableReactions -> {
					mediaDataController.processLoadedReactions(response.reactions, response.hash, (System.currentTimeMillis() / 1000).toInt(), false)

					for (reaction in response.reactions) {
						result = getFileReference(reaction.staticIcon, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.appearAnimation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.selectAnimation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.activateAnimation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.effectAnimation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.aroundAnimation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.centerIcon, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}
					}
				}

				is TLUsersUserFull -> {
					messagesController.putUsers(response.users, false)
					messagesController.putChats(response.chats, false)

					val userFull = response.fullUser
					val botInfo = userFull?.botInfo

					if (botInfo != null) {
						messagesStorage.updateUserInfo(userFull, true)

						result = getFileReference(botInfo.descriptionDocument, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							continue
						}

						result = getFileReference(botInfo.descriptionPhoto, requester.location, needReplacement, locationReplacement)
					}
				}

				is TLAttachMenuBotsBot -> {
					val bot = response.bot

					if (bot != null) {
						for (icon in bot.icons) {
							result = getFileReference(icon.icon, requester.location, needReplacement, locationReplacement)

							if (result != null) {
								break
							}
						}

						if (cache) {
							val bots = mediaDataController.attachMenuBots
							val newBotsList = bots.bots.toMutableList()

							for (i in newBotsList.indices) {
								val wasBot = newBotsList[i]

								if (wasBot.botId == bot.botId) {
									newBotsList[i] = bot
									break
								}
							}

							bots.bots.clear()
							bots.bots.addAll(newBotsList)

							mediaDataController.processLoadedMenuBots(bots, bots.hash, (System.currentTimeMillis() / 1000).toInt(), false)
						}
					}
				}

				is TLHelpAppUpdate -> {
					result = getFileReference(response.document, requester.location, needReplacement, locationReplacement)

					if (result == null) {
						result = getFileReference(response.sticker, requester.location, needReplacement, locationReplacement)
					}
				}

				is WebPage -> {
					result = getFileReference(response, requester.location, needReplacement, locationReplacement)
				}

				is TLAccountWallPapers -> {
					var i = 0
					val size10 = response.wallpapers.size

					while (i < size10) {
						result = getFileReference((response.wallpapers[i] as? TLWallPaper)?.document, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						i++
					}

					if (result != null && cache) {
						messagesStorage.putWallpapers(response.wallpapers, 1)
					}
				}

				is TLWallPaper -> {
					result = getFileReference(response.document, requester.location, needReplacement, locationReplacement)

					if (result != null && cache) {
						messagesStorage.putWallpapers(listOf(response), 0)
					}
				}

				is TLTheme -> {
					result = getFileReference(response.document, requester.location, needReplacement, locationReplacement)

					if (result != null && cache) {
						AndroidUtilities.runOnUIThread {
							Theme.setThemeFileReference(response)
						}
					}
				}

				is Vector -> {
					if (response.objects.isNotEmpty()) {
						var i = 0
						val size10 = response.objects.size

						while (i < size10) {
							val `object` = response.objects[i]

							if (`object` is User) {
								result = getFileReference(`object`, requester.location, needReplacement, locationReplacement)

								if (cache && result != null) {
									messagesStorage.putUsersAndChats(listOf(`object`), null, true, true)

									AndroidUtilities.runOnUIThread {
										messagesController.putUser(`object`, false)
									}
								}
							}
							else if (`object` is Chat) {
								result = getFileReference(`object`, requester.location, needReplacement, locationReplacement)

								if (cache && result != null) {
									messagesStorage.putUsersAndChats(null, listOf(`object`), true, true)

									AndroidUtilities.runOnUIThread {
										messagesController.putChat(`object`, false)
									}
								}
							}

							if (result != null) {
								break
							}

							i++
						}
					}
				}

				is TLMessagesChats -> {
					if (response.chats.isNotEmpty()) {
						var i = 0
						val size10 = response.chats.size

						while (i < size10) {
							val chat = response.chats[i]

							result = getFileReference(chat, requester.location, needReplacement, locationReplacement)

							if (result != null) {
								if (cache) {
									messagesStorage.putUsersAndChats(null, listOf(chat), true, true)

									AndroidUtilities.runOnUIThread {
										messagesController.putChat(chat, false)
									}
								}

								break
							}

							i++
						}
					}
				}

				is TLMessagesSavedGifs -> {
					var b = 0
					val size2 = response.gifs.size

					while (b < size2) {
						result = getFileReference(response.gifs[b], requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						b++
					}

					if (cache) {
						mediaDataController.processLoadedRecentDocuments(MediaDataController.TYPE_IMAGE, response.gifs, true, 0, true)
					}
				}

				is TLMessagesStickerSet -> {
					if (result == null) {
						var b = 0
						val size2 = response.documents.size

						while (b < size2) {
							result = getFileReference(response.documents[b], requester.location, needReplacement, locationReplacement)

							if (result != null) {
								break
							}

							b++
						}
					}

					if (cache) {
						AndroidUtilities.runOnUIThread {
							mediaDataController.replaceStickerSet(response)
						}
					}
				}

				is TLMessagesRecentStickers -> {
					var b = 0
					val size2 = response.stickers.size

					while (b < size2) {
						result = getFileReference(response.stickers[b], requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						b++
					}

					if (cache) {
						mediaDataController.processLoadedRecentDocuments(MediaDataController.TYPE_IMAGE, response.stickers, false, 0, true)
					}
				}

				is TLMessagesFavedStickers -> {
					var b = 0
					val size2 = response.stickers.size

					while (b < size2) {
						result = getFileReference(response.stickers[b], requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						b++
					}

					if (cache) {
						mediaDataController.processLoadedRecentDocuments(MediaDataController.TYPE_FAVE, response.stickers, false, 0, true)
					}
				}

				is PhotosPhotos -> {
					var b = 0
					val size = response.photos.size

					while (b < size) {
						result = getFileReference(response.photos[b], requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						b++
					}
				}
			}

			if (result != null) {
				if (onUpdateObjectReference(requester, result, locationReplacement?.get(0), fromCache)) {
					found = true
				}
			}
			else {
				sendErrorToObject(requester.args?.toList()?.toTypedArray() ?: arrayOf(), 1)
			}
		}

		locationRequester.remove(locationKey)

		if (found) {
			cacheKey?.let {
				putResponseToCache(it, response)
			}
		}

		return found
	}

	private fun cleanupCache() {
		if (abs((SystemClock.elapsedRealtime() - lastCleanupTime).toDouble()) < 60 * 10 * 1000) {
			return
		}

		lastCleanupTime = SystemClock.elapsedRealtime()

		var keysToDelete: MutableList<String?>? = null

		for ((key, cachedResult) in responseCache) {
			if (abs((SystemClock.elapsedRealtime() - cachedResult.firstQueryTime).toDouble()) >= 60 * 10 * 1000) {
				if (keysToDelete == null) {
					keysToDelete = mutableListOf()
				}

				keysToDelete.add(key)
			}
		}

		if (keysToDelete != null) {
			for (key in keysToDelete) {
				responseCache.remove(key)
			}
		}
	}

	private fun getCachedResponse(key: String): CachedResult? {
		var cachedResult = responseCache[key]

		if (cachedResult != null && abs((SystemClock.elapsedRealtime() - cachedResult.firstQueryTime).toDouble()) >= 60 * 10 * 1000) {
			responseCache.remove(key)
			cachedResult = null
		}

		return cachedResult
	}

	private fun putResponseToCache(key: String, response: TLObject?) {
		var cachedResult = responseCache[key]

		if (cachedResult == null) {
			cachedResult = CachedResult()
			cachedResult.response = response
			cachedResult.firstQueryTime = SystemClock.uptimeMillis()
			responseCache[key] = cachedResult
		}

		cachedResult.lastQueryTime = SystemClock.uptimeMillis()
	}

	private fun getFileReference(document: TLRPC.Document?, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
		if (document == null || location == null || document !is TLRPC.TLDocument) {
			return null
		}

		if (location is TLInputDocumentFileLocation) {
			if (document.id == location.id) {
				return document.fileReference
			}
		}
		else {
			var a = 0
			val size = document.thumbs.size

			while (a < size) {
				val photoSize = document.thumbs[a]
				val result = getFileReference(photoSize, location, needReplacement)

				if (needReplacement != null && needReplacement[0]) {
					TLInputDocumentFileLocation().apply {
						id = document.id
						volumeId = location.volumeId
						localId = location.localId
						accessHash = document.accessHash
						fileReference = document.fileReference
						thumbSize = photoSize.type
					}.let {
						replacement?.set(0, it)
					}

					return document.fileReference
				}

				if (result != null) {
					return result
				}

				a++
			}
		}

		return null
	}

	private fun getPeerReferenceReplacement(user: User?, chat: Chat?, big: Boolean, location: InputFileLocation, replacement: Array<InputFileLocation?>?, needReplacement: BooleanArray?): Boolean {
		if (needReplacement != null && needReplacement[0]) {
			val inputPeerPhotoFileLocation = TLInputPeerPhotoFileLocation()
			inputPeerPhotoFileLocation.id = location.volumeId
			inputPeerPhotoFileLocation.volumeId = location.volumeId
			inputPeerPhotoFileLocation.localId = location.localId
			inputPeerPhotoFileLocation.big = big

			val peer: InputPeer

			if (user != null) {
				if (user !is TLRPC.TLUser) {
					return false
				}

				val inputPeerUser = TLInputPeerUser()
				inputPeerUser.userId = user.id
				inputPeerUser.accessHash = user.accessHash
				inputPeerPhotoFileLocation.photoId = user.photo?.photoId ?: 0

				peer = inputPeerUser
			}
			else {
				if (ChatObject.isChannel(chat)) {
					val inputPeerChannel = TLInputPeerChannel()
					inputPeerChannel.channelId = chat.id
					inputPeerChannel.accessHash = chat.accessHash

					peer = inputPeerChannel
				}
				else {
					val inputPeerChat = TLInputPeerChat()
					inputPeerChat.chatId = chat?.id ?: 0
					peer = inputPeerChat
				}

				inputPeerPhotoFileLocation.photoId = (chat?.photo as? TLRPC.TLChatPhoto)?.photoId ?: 0
			}

			inputPeerPhotoFileLocation.peer = peer

			replacement?.set(0, inputPeerPhotoFileLocation)

			return true
		}

		return false
	}

	private fun getFileReference(user: User?, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
		val userPhoto = (user as? TLRPC.TLUser)?.photo

		if (userPhoto == null || location !is TLInputFileLocation) {
			return null
		}

		var result = getFileReference(userPhoto.photoSmall, location, needReplacement)

		if (getPeerReferenceReplacement(user, null, false, location, replacement, needReplacement)) {
			return ByteArray(0)
		}

		if (result == null) {
			result = getFileReference(userPhoto.photoBig, location, needReplacement)

			if (getPeerReferenceReplacement(user, null, true, location, replacement, needReplacement)) {
				return ByteArray(0)
			}
		}

		return result
	}

	private fun getFileReference(chat: Chat?, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
		if (chat?.photo == null || location !is TLInputFileLocation && location !is TLInputPeerPhotoFileLocation) {
			return null
		}

		if (location is TLInputPeerPhotoFileLocation) {
			needReplacement!![0] = true

			if (getPeerReferenceReplacement(null, chat, false, location, replacement, needReplacement)) {
				return ByteArray(0)
			}
			return null
		}
		else {
			var result = getFileReference(chat.photo?.photoSmall, location, needReplacement)

			if (getPeerReferenceReplacement(null, chat, false, location, replacement, needReplacement)) {
				return ByteArray(0)
			}

			if (result == null) {
				result = getFileReference(chat.photo?.photoBig, location, needReplacement)

				if (getPeerReferenceReplacement(null, chat, true, location, replacement, needReplacement)) {
					return ByteArray(0)
				}
			}

			return result
		}
	}

	private fun getFileReference(photo: TLRPC.Photo?, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
		if (photo !is TLRPC.TLPhoto) {
			return null
		}

		if (location is TLInputPhotoFileLocation) {
			return if (photo.id == location.id) photo.fileReference else null
		}
		else if (location is TLInputFileLocation) {
			var a = 0
			val size = photo.sizes.size

			while (a < size) {
				val photoSize = photo.sizes[a]
				val result = getFileReference(photoSize, location, needReplacement)

				if (needReplacement != null && needReplacement[0]) {
					TLInputPhotoFileLocation().apply {
						id = photo.id
						volumeId = location.volumeId
						localId = location.localId
						accessHash = photo.accessHash
						fileReference = photo.fileReference
						thumbSize = photoSize.type
					}.let {
						replacement?.set(0, it)
					}

					return photo.fileReference
				}

				if (result != null) {
					return result
				}

				a++
			}
		}

		return null
	}

	private fun getFileReference(photoSize: PhotoSize?, location: InputFileLocation, needReplacement: BooleanArray?): ByteArray? {
		if (photoSize == null || location !is TLInputFileLocation) {
			return null
		}

		return getFileReference(photoSize.location, location, needReplacement)
	}

	private fun getFileReference(fileLocation: FileLocation?, location: InputFileLocation, needReplacement: BooleanArray?): ByteArray? {
		if (fileLocation == null || location !is TLInputFileLocation) {
			return null
		}

		if (fileLocation.localId == location.localId && fileLocation.volumeId == location.volumeId) {
			// if (fileLocation.fileReference == null && needReplacement != null) {
			if (needReplacement != null) {
				needReplacement[0] = true
			}

			// return fileLocation.fileReference
		}

		return null
	}

	private fun getFileReference(webpage: WebPage?, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
		if (webpage !is TLRPC.TLWebPage) {
			return null
		}

		var result = getFileReference(webpage.document, location, needReplacement, replacement)

		if (result != null) {
			return result
		}

		result = getFileReference(webpage.photo, location, needReplacement, replacement)

		if (result != null) {
			return result
		}

		if (webpage.attributes.isNotEmpty()) {
			for (attribute in webpage.attributes) {
				for (document in attribute.documents) {
					result = getFileReference(document, location, needReplacement, replacement)

					if (result != null) {
						return result
					}
				}
			}
		}

		if (webpage.cachedPage != null) {
			for (document in webpage.cachedPage!!.documents) {
				result = getFileReference(document, location, needReplacement, replacement)

				if (result != null) {
					return result
				}
			}

			for (photo in webpage.cachedPage!!.photos) {
				result = getFileReference(photo, location, needReplacement, replacement)

				if (result != null) {
					return result
				}
			}
		}

		return null
	}

	companion object {
		private val Instance = arrayOfNulls<FileRefController>(UserConfig.MAX_ACCOUNT_COUNT)

		@JvmStatic
		fun getInstance(num: Int): FileRefController {
			var localInstance = Instance[num]

			if (localInstance == null) {
				synchronized(FileRefController::class.java) {
					localInstance = Instance[num]

					if (localInstance == null) {
						localInstance = FileRefController(num)
						Instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		@JvmStatic
		fun getKeyForParentObject(parentObject: Any?): String? {
			when (parentObject) {
				is TLAvailableReaction -> {
					return "available_reaction_" + parentObject.reaction
				}

				is TLBotInfo -> {
					return "bot_info_" + parentObject.userId
				}

				is TLAttachMenuBot -> {
					val botId = parentObject.botId
					return "attach_menu_bot_$botId"
				}

				is MessageObject -> {
					val channelId = parentObject.channelId
					return "message" + parentObject.realId + "_" + channelId + "_" + parentObject.scheduled
				}

				is Message -> {
					val channelId = parentObject.peerId?.channelId ?: 0
					return "message" + parentObject.id + "_" + channelId + "_" + (parentObject as? TLRPC.TLMessage)?.fromScheduled
				}

				is WebPage -> {
					return "webpage" + parentObject.id
				}

				is User -> {
					return "user" + parentObject.id
				}

				is Chat -> {
					return "chat" + parentObject.id
				}

				is String -> {
					return "str$parentObject"
				}

				is TLMessagesStickerSet -> {
					return "set" + parentObject.set?.id
				}

				is StickerSetCovered -> {
					return "set" + parentObject.set?.id
				}

				is TLInputStickerSetID -> {
					return "set" + parentObject.id
				}

				is TLWallPaper -> {
					return "wallpaper" + parentObject.id
				}

				is TLTheme -> {
					return "theme" + parentObject.id
				}

				else -> {
					return if (parentObject != null) "" + parentObject else null
				}
			}
		}

		@JvmStatic
		fun isFileRefError(error: String?): Boolean {
			return "FILEREF_EXPIRED" == error || "FILE_REFERENCE_EXPIRED" == error || "FILE_REFERENCE_EMPTY" == error || error?.startsWith("FILE_REFERENCE_") == true
		}
	}
}
