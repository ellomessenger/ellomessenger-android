/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import android.os.SystemClock
import org.telegram.messenger.FileLoadOperation.RequestInfo
import org.telegram.messenger.SendMessagesHelper.DelayedMessage
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.RequestDelegate
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInfo
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.InputFileLocation
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.StickerSetCovered
import org.telegram.tgnet.TLRPC.TL_account_getTheme
import org.telegram.tgnet.TLRPC.TL_account_getWallPaper
import org.telegram.tgnet.TLRPC.TL_account_getWallPapers
import org.telegram.tgnet.TLRPC.TL_account_wallPapers
import org.telegram.tgnet.TLRPC.TL_attachMenuBot
import org.telegram.tgnet.TLRPC.TL_attachMenuBotsBot
import org.telegram.tgnet.TLRPC.TL_channel
import org.telegram.tgnet.TLRPC.TL_channels_getChannels
import org.telegram.tgnet.TLRPC.TL_channels_getMessages
import org.telegram.tgnet.TLRPC.TL_chat
import org.telegram.tgnet.tlrpc.TL_error
import org.telegram.tgnet.TLRPC.TL_help_appUpdate
import org.telegram.tgnet.TLRPC.TL_help_getAppUpdate
import org.telegram.tgnet.TLRPC.TL_inputDocumentFileLocation
import org.telegram.tgnet.TLRPC.TL_inputFileLocation
import org.telegram.tgnet.TLRPC.TL_inputMediaDocument
import org.telegram.tgnet.TLRPC.TL_inputMediaPhoto
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterChatPhotos
import org.telegram.tgnet.TLRPC.TL_inputPeerChannel
import org.telegram.tgnet.TLRPC.TL_inputPeerChat
import org.telegram.tgnet.TLRPC.TL_inputPeerPhotoFileLocation
import org.telegram.tgnet.TLRPC.TL_inputPeerUser
import org.telegram.tgnet.TLRPC.TL_inputPhotoFileLocation
import org.telegram.tgnet.TLRPC.TL_inputSingleMedia
import org.telegram.tgnet.TLRPC.TL_inputStickerSetID
import org.telegram.tgnet.TLRPC.TL_inputStickeredMediaDocument
import org.telegram.tgnet.TLRPC.TL_inputStickeredMediaPhoto
import org.telegram.tgnet.TLRPC.TL_inputTheme
import org.telegram.tgnet.TLRPC.TL_inputWallPaper
import org.telegram.tgnet.TLRPC.TL_messageActionChatEditPhoto
import org.telegram.tgnet.TLRPC.TL_messages_availableReactions
import org.telegram.tgnet.TLRPC.TL_messages_chats
import org.telegram.tgnet.tlrpc.TL_messages_editMessage
import org.telegram.tgnet.TLRPC.TL_messages_faveSticker
import org.telegram.tgnet.TLRPC.TL_messages_favedStickers
import org.telegram.tgnet.TLRPC.TL_messages_getAttachMenuBot
import org.telegram.tgnet.TLRPC.TL_messages_getAttachedStickers
import org.telegram.tgnet.TLRPC.TL_messages_getAvailableReactions
import org.telegram.tgnet.TLRPC.TL_messages_getChats
import org.telegram.tgnet.TLRPC.TL_messages_getFavedStickers
import org.telegram.tgnet.TLRPC.TL_messages_getMessages
import org.telegram.tgnet.TLRPC.TL_messages_getRecentStickers
import org.telegram.tgnet.TLRPC.TL_messages_getSavedGifs
import org.telegram.tgnet.TLRPC.TL_messages_getScheduledMessages
import org.telegram.tgnet.TLRPC.TL_messages_getStickerSet
import org.telegram.tgnet.TLRPC.TL_messages_getWebPage
import org.telegram.tgnet.TLRPC.TL_messages_recentStickers
import org.telegram.tgnet.TLRPC.TL_messages_saveGif
import org.telegram.tgnet.TLRPC.TL_messages_saveRecentSticker
import org.telegram.tgnet.TLRPC.TL_messages_savedGifs
import org.telegram.tgnet.TLRPC.TL_messages_search
import org.telegram.tgnet.TLRPC.TL_messages_sendMedia
import org.telegram.tgnet.TLRPC.TL_messages_sendMultiMedia
import org.telegram.tgnet.TLRPC.TL_messages_stickerSet
import org.telegram.tgnet.TLRPC.TL_photos_getUserPhotos
import org.telegram.tgnet.TLRPC.TL_theme
import org.telegram.tgnet.TLRPC.TL_users_getFullUser
import org.telegram.tgnet.TLRPC.TL_users_getUsers
import org.telegram.tgnet.TLRPC.TL_wallPaper
import org.telegram.tgnet.TLRPC.WallPaper
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_availableReaction
import org.telegram.tgnet.tlrpc.TL_users_userFull
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.Vector
import org.telegram.tgnet.tlrpc.messages_Messages
import org.telegram.tgnet.tlrpc.photos_Photos
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
	private val multiMediaCache = mutableMapOf<TL_messages_sendMultiMedia, Array<Any>>()
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
			is TL_inputSingleMedia -> {
				when (firstArgument.media) {
					is TL_inputMediaDocument -> {
						val mediaDocument = firstArgument.media as TL_inputMediaDocument

						locationKey = "file_" + mediaDocument.id.id
						location = TL_inputDocumentFileLocation()
						location.id = mediaDocument.id.id
					}

					is TL_inputMediaPhoto -> {
						val mediaPhoto = firstArgument.media as TL_inputMediaPhoto

						locationKey = "photo_" + mediaPhoto.id.id
						location = TL_inputPhotoFileLocation()
						location.id = mediaPhoto.id.id
					}

					else -> {
						sendErrorToObject(args.toList().toTypedArray(), 0)
						return
					}
				}
			}

			is TL_messages_sendMultiMedia -> {
				val parentObjects = parentObject as? List<Any>

				multiMediaCache[firstArgument] = args.toList().filterNotNull().toTypedArray()

				var a = 0
				val size = firstArgument.multi_media.size

				while (a < size) {
					val media = firstArgument.multi_media[a]

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

			is TL_messages_sendMedia -> {
				when (firstArgument.media) {
					is TL_inputMediaDocument -> {
						val mediaDocument = firstArgument.media as TL_inputMediaDocument

						locationKey = "file_" + mediaDocument.id.id
						location = TL_inputDocumentFileLocation()
						location.id = mediaDocument.id.id
					}

					is TL_inputMediaPhoto -> {
						val mediaPhoto = firstArgument.media as TL_inputMediaPhoto

						locationKey = "photo_" + mediaPhoto.id.id
						location = TL_inputPhotoFileLocation()
						location.id = mediaPhoto.id.id
					}

					else -> {
						sendErrorToObject(args.toList().toTypedArray(), 0)
						return
					}
				}
			}

			is TL_messages_editMessage -> {
				when (firstArgument.media) {
					is TL_inputMediaDocument -> {
						val mediaDocument = firstArgument.media as TL_inputMediaDocument

						locationKey = "file_" + mediaDocument.id.id
						location = TL_inputDocumentFileLocation()
						location.id = mediaDocument.id.id
					}

					is TL_inputMediaPhoto -> {
						val mediaPhoto = firstArgument.media as TL_inputMediaPhoto

						locationKey = "photo_" + mediaPhoto.id.id
						location = TL_inputPhotoFileLocation()
						location.id = mediaPhoto.id.id
					}

					else -> {
						sendErrorToObject(args.toList().toTypedArray(), 0)
						return
					}
				}
			}

			is TL_messages_saveGif -> {
				locationKey = "file_" + firstArgument.id.id
				location = TL_inputDocumentFileLocation()
				location.id = firstArgument.id.id
			}

			is TL_messages_saveRecentSticker -> {
				locationKey = "file_" + firstArgument.id.id
				location = TL_inputDocumentFileLocation()
				location.id = firstArgument.id.id
			}

			is TL_messages_faveSticker -> {
				locationKey = "file_" + firstArgument.id.id
				location = TL_inputDocumentFileLocation()
				location.id = firstArgument.id.id
			}

			is TL_messages_getAttachedStickers -> {
				when (firstArgument.media) {
					is TL_inputStickeredMediaDocument -> {
						val mediaDocument = firstArgument.media as TL_inputStickeredMediaDocument

						locationKey = "file_" + mediaDocument.id.id
						location = TL_inputDocumentFileLocation()
						location.id = mediaDocument.id.id
					}

					is TL_inputStickeredMediaPhoto -> {
						val mediaPhoto = firstArgument.media as TL_inputStickeredMediaPhoto

						locationKey = "photo_" + mediaPhoto.id.id
						location = TL_inputPhotoFileLocation()
						location.id = mediaPhoto.id.id
					}

					else -> {
						sendErrorToObject(args.toList().toTypedArray(), 0)
						return
					}
				}
			}

			is TL_inputFileLocation -> {
				location = firstArgument
				locationKey = "loc_" + location.local_id + "_" + location.volume_id
			}

			is TL_inputDocumentFileLocation -> {
				location = firstArgument
				locationKey = "file_" + location.id
			}

			is TL_inputPhotoFileLocation -> {
				location = firstArgument
				locationKey = "photo_" + location.id
			}

			is TL_inputPeerPhotoFileLocation -> {
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
			is TL_availableReaction -> {
				val req = TL_messages_getAvailableReactions()
				req.hash = 0

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is BotInfo -> {
				val req = TL_users_getFullUser()
				req.id = messagesController.getInputUser(parentObject.user_id)

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is TL_attachMenuBot -> {
				val req = TL_messages_getAttachMenuBot()
				req.bot = messagesController.getInputUser(parentObject.bot_id)

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is MessageObject -> {
				val channelId = parentObject.channelId

				if (parentObject.scheduled) {
					val req = TL_messages_getScheduledMessages()
					req.peer = messagesController.getInputPeer(parentObject.dialogId)
					req.id.add(parentObject.realId)

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
				else if (channelId != 0L) {
					val req = TL_channels_getMessages()
					req.channel = messagesController.getInputChannel(channelId)
					req.id.add(parentObject.realId)

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
				else {
					val req = TL_messages_getMessages()
					req.id.add(parentObject.realId)

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
			}

			is TL_wallPaper -> {
				val req = TL_account_getWallPaper()

				val inputWallPaper = TL_inputWallPaper()
				inputWallPaper.id = parentObject.id
				inputWallPaper.access_hash = parentObject.access_hash

				req.wallpaper = inputWallPaper

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is TL_theme -> {
				val req = TL_account_getTheme()

				val inputTheme = TL_inputTheme()
				inputTheme.id = parentObject.id
				inputTheme.access_hash = parentObject.access_hash

				req.theme = inputTheme
				req.format = "android"

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is WebPage -> {
				val req = TL_messages_getWebPage()
				req.url = parentObject.url
				req.hash = 0

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is User -> {
				val req = TL_users_getUsers()
				req.id.add(messagesController.getInputUser(parentObject))

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is Chat -> {
				if (parentObject is TL_chat) {
					val req = TL_messages_getChats()
					req.id.add(parentObject.id)

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
				else if (parentObject is TL_channel) {
					val req = TL_channels_getChannels()
					req.id.add(MessagesController.getInputChannel(parentObject))

					connectionsManager.sendRequest(req) { response, _ ->
						onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
					}
				}
			}

			is String -> {
				if ("wallpaper" == parentObject) {
					if (wallpaperWaiters.isEmpty()) {
						val req = TL_account_getWallPapers()

						connectionsManager.sendRequest(req) { response, _ ->
							broadcastWaitersData(wallpaperWaiters, response)
						}
					}

					wallpaperWaiters.add(Waiter(locationKey, parentKey))
				}
				else if (parentObject.startsWith("gif")) {
					if (savedGifsWaiters.isEmpty()) {
						val req = TL_messages_getSavedGifs()

						connectionsManager.sendRequest(req) { response, _ ->
							broadcastWaitersData(savedGifsWaiters, response)
						}
					}

					savedGifsWaiters.add(Waiter(locationKey, parentKey))
				}
				else if ("recent" == parentObject) {
					if (recentStickersWaiter.isEmpty()) {
						val req = TL_messages_getRecentStickers()

						connectionsManager.sendRequest(req) { response, _ ->
							broadcastWaitersData(recentStickersWaiter, response)
						}
					}

					recentStickersWaiter.add(Waiter(locationKey, parentKey))
				}
				else if ("fav" == parentObject) {
					if (favStickersWaiter.isEmpty()) {
						val req = TL_messages_getFavedStickers()

						connectionsManager.sendRequest(req) { response, _ ->
							broadcastWaitersData(favStickersWaiter, response)
						}
					}

					favStickersWaiter.add(Waiter(locationKey, parentKey))
				}
				else if ("update" == parentObject) {
					val req = TL_help_getAppUpdate()

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
						val req = TL_photos_getUserPhotos()
						req.limit = 80
						req.offset = 0
						req.max_id = 0
						req.user_id = messagesController.getInputUser(id)

						connectionsManager.sendRequest(req) { response, _ ->
							onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
						}
					}
					else {
						val req = TL_messages_search()
						req.filter = TL_inputMessagesFilterChatPhotos()
						req.limit = 80
						req.offset_id = 0
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
							val req = TL_channels_getMessages()
							req.channel = messagesController.getInputChannel(channelId)
							req.id.add(Utilities.parseInt(params[2]))

							connectionsManager.sendRequest(req) { response, _ ->
								onRequestComplete(locationKey, parentKey, response, cache = false, fromCache = false)
							}
						}
						else {
							val req = TL_messages_getMessages()
							req.id.add(Utilities.parseInt(params[2]))

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

			is TL_messages_stickerSet -> {
				val req = TL_messages_getStickerSet()
				req.stickerset = TL_inputStickerSetID()
				req.stickerset.id = parentObject.set.id
				req.stickerset.access_hash = parentObject.set.access_hash

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is StickerSetCovered -> {
				val req = TL_messages_getStickerSet()
				req.stickerset = TL_inputStickerSetID()
				req.stickerset.id = parentObject.set.id
				req.stickerset.access_hash = parentObject.set.access_hash

				connectionsManager.sendRequest(req) { response, _ ->
					onRequestComplete(locationKey, parentKey, response, cache = true, fromCache = false)
				}
			}

			is InputStickerSet -> {
				val req = TL_messages_getStickerSet()
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

	private fun isSameReference(oldRef: ByteArray, newRef: ByteArray): Boolean {
		return oldRef.contentEquals(newRef)
	}

	private fun onUpdateObjectReference(requester: Requester, fileReference: ByteArray, locationReplacement: InputFileLocation?, fromCache: Boolean): Boolean {
		val firstArgument = requester.args?.firstOrNull()

		if (BuildConfig.DEBUG) {
			FileLog.d("fileref updated for " + firstArgument + " " + requester.locationKey)
		}

		if (firstArgument is TL_inputSingleMedia) {
			val multiMedia = requester.args?.get(1) as TL_messages_sendMultiMedia
			val objects = multiMediaCache[multiMedia] ?: return true

			if (firstArgument.media is TL_inputMediaDocument) {
				val mediaDocument = firstArgument.media as TL_inputMediaDocument

				if (fromCache && isSameReference(mediaDocument.id.file_reference, fileReference)) {
					return false
				}

				mediaDocument.id.file_reference = fileReference
			}
			else if (firstArgument.media is TL_inputMediaPhoto) {
				val mediaPhoto = firstArgument.media as TL_inputMediaPhoto

				if (fromCache && isSameReference(mediaPhoto.id.file_reference, fileReference)) {
					return false
				}

				mediaPhoto.id.file_reference = fileReference
			}

			val index = multiMedia.multi_media.indexOf(firstArgument)

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
		else if (firstArgument is TL_messages_sendMedia) {
			if (firstArgument.media is TL_inputMediaDocument) {
				val mediaDocument = firstArgument.media as TL_inputMediaDocument

				if (fromCache && isSameReference(mediaDocument.id.file_reference, fileReference)) {
					return false
				}

				mediaDocument.id.file_reference = fileReference
			}
			else if (firstArgument.media is TL_inputMediaPhoto) {
				val mediaPhoto = firstArgument.media as TL_inputMediaPhoto

				if (fromCache && isSameReference(mediaPhoto.id.file_reference, fileReference)) {
					return false
				}

				mediaPhoto.id.file_reference = fileReference
			}

			AndroidUtilities.runOnUIThread {
				sendMessagesHelper.performSendMessageRequest(firstArgument as TLObject, requester.args?.get(1) as MessageObject, requester.args?.get(2) as String, requester.args?.get(3) as DelayedMessage, (requester.args?.get(4) as Boolean), requester.args?.get(5) as DelayedMessage, null, (requester.args?.get(6) as Boolean))
			}
		}
		else if (firstArgument is TL_messages_editMessage) {
			if (firstArgument.media is TL_inputMediaDocument) {
				val mediaDocument = firstArgument.media as TL_inputMediaDocument

				if (fromCache && isSameReference(mediaDocument.id.file_reference, fileReference)) {
					return false
				}

				mediaDocument.id.file_reference = fileReference
			}
			else if (firstArgument.media is TL_inputMediaPhoto) {
				val mediaPhoto = firstArgument.media as TL_inputMediaPhoto

				if (fromCache && isSameReference(mediaPhoto.id.file_reference, fileReference)) {
					return false
				}

				mediaPhoto.id.file_reference = fileReference
			}

			AndroidUtilities.runOnUIThread {
				sendMessagesHelper.performSendMessageRequest(firstArgument as TLObject, requester.args!![1] as MessageObject, requester.args!![2] as String, requester.args!![3] as DelayedMessage, (requester.args!![4] as Boolean), requester.args!![5] as DelayedMessage, null, (requester.args!![6] as Boolean))
			}
		}
		else if (firstArgument is TL_messages_saveGif) {
			if (fromCache && isSameReference(firstArgument.id.file_reference, fileReference)) {
				return false
			}

			firstArgument.id.file_reference = fileReference

			connectionsManager.sendRequest(firstArgument)
		}
		else if (firstArgument is TL_messages_saveRecentSticker) {
			if (fromCache && isSameReference(firstArgument.id.file_reference, fileReference)) {
				return false
			}

			firstArgument.id.file_reference = fileReference

			connectionsManager.sendRequest(firstArgument)
		}
		else if (firstArgument is TL_messages_faveSticker) {
			if (fromCache && isSameReference(firstArgument.id.file_reference, fileReference)) {
				return false
			}

			firstArgument.id.file_reference = fileReference

			connectionsManager.sendRequest(firstArgument)
		}
		else if (firstArgument is TL_messages_getAttachedStickers) {
			if (firstArgument.media is TL_inputStickeredMediaDocument) {
				val mediaDocument = firstArgument.media as TL_inputStickeredMediaDocument

				if (fromCache && isSameReference(mediaDocument.id.file_reference, fileReference)) {
					return false
				}

				mediaDocument.id.file_reference = fileReference
			}
			else if (firstArgument.media is TL_inputStickeredMediaPhoto) {
				val mediaPhoto = firstArgument.media as TL_inputStickeredMediaPhoto

				if (fromCache && isSameReference(mediaPhoto.id.file_reference, fileReference)) {
					return false
				}

				mediaPhoto.id.file_reference = fileReference
			}

			connectionsManager.sendRequest(firstArgument, requester.args!![1] as RequestDelegate)
		}
		else if (requester.args?.get(1) is FileLoadOperation) {
			val fileLoadOperation = requester.args!![1] as FileLoadOperation

			if (locationReplacement != null) {
				if (fromCache && isSameReference(fileLoadOperation.location!!.file_reference, locationReplacement.file_reference)) {
					return false
				}

				fileLoadOperation.location = locationReplacement
			}
			else {
				if (fromCache && isSameReference(requester.location!!.file_reference, fileReference)) {
					return false
				}

				requester.location!!.file_reference = fileReference
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

		if (args[0] is TL_inputSingleMedia) {
			val req = args[1] as TL_messages_sendMultiMedia
			val objects = multiMediaCache[req]

			if (objects != null) {
				multiMediaCache.remove(req)

				AndroidUtilities.runOnUIThread {
					sendMessagesHelper.performSendMessageRequestMulti(req, objects[1] as List<MessageObject>, objects[2] as List<String?>, null, objects[4] as DelayedMessage, (objects[5] as Boolean))
				}
			}
		}
		else if (args[0] is TL_messages_sendMedia || args[0] is TL_messages_editMessage) {
			AndroidUtilities.runOnUIThread {
				sendMessagesHelper.performSendMessageRequest(args[0] as TLObject, args[1] as MessageObject, args[2] as String, args[3] as DelayedMessage, (args[4] as Boolean), args[5] as DelayedMessage, null, (args[6] as Boolean))
			}
		}
		else if (args[0] is TL_messages_saveGif) {
			// val req = args[0] as TL_messages_saveGif
			// do nothing
		}
		else if (args[0] is TL_messages_saveRecentSticker) {
			// val req = args[0] as TL_messages_saveRecentSticker
			// do nothing
		}
		else if (args[0] is TL_messages_faveSticker) {
			// val req = args[0] as TL_messages_faveSticker
			// do nothing
		}
		else if (args[0] is TL_messages_getAttachedStickers) {
			val req = args[0] as TL_messages_getAttachedStickers
			connectionsManager.sendRequest(req, args[1] as RequestDelegate)
		}
		else {
			if (reason == 0) {
				val error = TL_error()
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
			is TL_account_wallPapers -> cacheKey = "wallpaper"
			is TL_messages_savedGifs -> cacheKey = "gif"
			is TL_messages_recentStickers -> cacheKey = "recent"
			is TL_messages_favedStickers -> cacheKey = "fav"
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

			if (requester.location is TL_inputFileLocation || requester.location is TL_inputPeerPhotoFileLocation) {
				locationReplacement = arrayOfNulls(1)
				needReplacement = BooleanArray(1)
			}

			requester.completed = true

			when (response) {
				is messages_Messages -> {
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
									result = getFileReference(message.media!!.game.document, requester.location, needReplacement, locationReplacement)

									if (result == null) {
										result = getFileReference(message.media!!.game.photo, requester.location, needReplacement, locationReplacement)
									}
								}
								else if (message.media!!.photo != null) {
									result = getFileReference(message.media!!.photo, requester.location, needReplacement, locationReplacement)
								}
								else if (message.media!!.webpage != null) {
									result = getFileReference(message.media!!.webpage, requester.location, needReplacement, locationReplacement)
								}
							}
							else if (message.action is TL_messageActionChatEditPhoto) {
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

				is TL_messages_availableReactions -> {
					mediaDataController.processLoadedReactions(response.reactions, response.hash, (System.currentTimeMillis() / 1000).toInt(), false)

					for (reaction in response.reactions) {
						result = getFileReference(reaction.static_icon, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.appear_animation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.select_animation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.activate_animation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.effect_animation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.around_animation, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						result = getFileReference(reaction.center_icon, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}
					}
				}

				is TL_users_userFull -> {
					messagesController.putUsers(response.users, false)
					messagesController.putChats(response.chats, false)

					val userFull = response.fullUser
					val botInfo = userFull?.bot_info

					if (botInfo != null) {
						messagesStorage.updateUserInfo(userFull, true)

						result = getFileReference(botInfo.description_document, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							continue
						}

						result = getFileReference(botInfo.description_photo, requester.location, needReplacement, locationReplacement)
					}
				}

				is TL_attachMenuBotsBot -> {
					val bot = response.bot

					for (icon in bot.icons) {
						result = getFileReference(icon.icon, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}
					}

					if (cache) {
						val bots = mediaDataController.attachMenuBots
						val newBotsList = ArrayList(bots.bots)

						for (i in newBotsList.indices) {
							val wasBot = newBotsList[i]

							if (wasBot.bot_id == bot.bot_id) {
								newBotsList[i] = bot
								break
							}
						}

						bots.bots = newBotsList

						mediaDataController.processLoadedMenuBots(bots, bots.hash, (System.currentTimeMillis() / 1000).toInt(), false)
					}
				}

				is TL_help_appUpdate -> {
					result = getFileReference(response.document, requester.location, needReplacement, locationReplacement)

					if (result == null) {
						result = getFileReference(response.sticker, requester.location, needReplacement, locationReplacement)
					}
				}

				is WebPage -> {
					result = getFileReference(response, requester.location, needReplacement, locationReplacement)
				}

				is TL_account_wallPapers -> {
					var i = 0
					val size10 = response.wallpapers.size

					while (i < size10) {
						result = getFileReference((response.wallpapers[i] as WallPaper).document, requester.location, needReplacement, locationReplacement)

						if (result != null) {
							break
						}

						i++
					}

					if (result != null && cache) {
						messagesStorage.putWallpapers(response.wallpapers, 1)
					}
				}

				is TL_wallPaper -> {
					result = getFileReference(response.document, requester.location, needReplacement, locationReplacement)

					if (result != null && cache) {
						messagesStorage.putWallpapers(listOf(response), 0)
					}
				}

				is TL_theme -> {
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

				is TL_messages_chats -> {
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

				is TL_messages_savedGifs -> {
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

				is TL_messages_stickerSet -> {
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

				is TL_messages_recentStickers -> {
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

				is TL_messages_favedStickers -> {
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

				is photos_Photos -> {
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
		if (document == null || location == null) {
			return null
		}

		if (location is TL_inputDocumentFileLocation) {
			if (document.id == location.id) {
				return document.file_reference
			}
		}
		else {
			var a = 0
			val size = document.thumbs.size

			while (a < size) {
				val photoSize = document.thumbs[a]
				val result = getFileReference(photoSize, location, needReplacement)

				if (needReplacement != null && needReplacement[0]) {
					TL_inputDocumentFileLocation().apply {
						id = document.id
						volume_id = location.volume_id
						local_id = location.local_id
						access_hash = document.access_hash
						file_reference = document.file_reference
						thumb_size = photoSize.type
					}.let {
						replacement?.set(0, it)
					}

					return document.file_reference
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
			val inputPeerPhotoFileLocation = TL_inputPeerPhotoFileLocation()
			inputPeerPhotoFileLocation.id = location.volume_id
			inputPeerPhotoFileLocation.volume_id = location.volume_id
			inputPeerPhotoFileLocation.local_id = location.local_id
			inputPeerPhotoFileLocation.big = big

			val peer: InputPeer

			if (user != null) {
				val inputPeerUser = TL_inputPeerUser()
				inputPeerUser.user_id = user.id
				inputPeerUser.access_hash = user.access_hash
				inputPeerPhotoFileLocation.photo_id = user.photo!!.photo_id

				peer = inputPeerUser
			}
			else {
				if (ChatObject.isChannel(chat)) {
					val inputPeerChannel = TL_inputPeerChannel()
					inputPeerChannel.channel_id = chat.id
					inputPeerChannel.access_hash = chat.access_hash

					peer = inputPeerChannel
				}
				else {
					val inputPeerChat = TL_inputPeerChat()
					inputPeerChat.chat_id = chat?.id ?: 0
					peer = inputPeerChat
				}

				inputPeerPhotoFileLocation.photo_id = chat?.photo?.photo_id ?: 0
			}

			inputPeerPhotoFileLocation.peer = peer

			replacement?.set(0, inputPeerPhotoFileLocation)

			return true
		}

		return false
	}

	private fun getFileReference(user: User?, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
		val userPhoto = user?.photo

		if (userPhoto == null || location !is TL_inputFileLocation) {
			return null
		}

		var result = getFileReference(userPhoto.photo_small, location, needReplacement)

		if (getPeerReferenceReplacement(user, null, false, location, replacement, needReplacement)) {
			return ByteArray(0)
		}

		if (result == null) {
			result = getFileReference(userPhoto.photo_big, location, needReplacement)

			if (getPeerReferenceReplacement(user, null, true, location, replacement, needReplacement)) {
				return ByteArray(0)
			}
		}

		return result
	}

	private fun getFileReference(chat: Chat?, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
		if (chat?.photo == null || location !is TL_inputFileLocation && location !is TL_inputPeerPhotoFileLocation) {
			return null
		}

		if (location is TL_inputPeerPhotoFileLocation) {
			needReplacement!![0] = true

			if (getPeerReferenceReplacement(null, chat, false, location, replacement, needReplacement)) {
				return ByteArray(0)
			}
			return null
		}
		else {
			var result = getFileReference(chat.photo.photo_small, location, needReplacement)

			if (getPeerReferenceReplacement(null, chat, false, location, replacement, needReplacement)) {
				return ByteArray(0)
			}

			if (result == null) {
				result = getFileReference(chat.photo.photo_big, location, needReplacement)

				if (getPeerReferenceReplacement(null, chat, true, location, replacement, needReplacement)) {
					return ByteArray(0)
				}
			}

			return result
		}
	}

	private fun getFileReference(photo: TLRPC.Photo?, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
		if (photo == null) {
			return null
		}

		if (location is TL_inputPhotoFileLocation) {
			return if (photo.id == location.id) photo.file_reference else null
		}
		else if (location is TL_inputFileLocation) {
			var a = 0
			val size = photo.sizes.size

			while (a < size) {
				val photoSize = photo.sizes[a]
				val result = getFileReference(photoSize, location, needReplacement)

				if (needReplacement != null && needReplacement[0]) {
					TL_inputPhotoFileLocation().apply {
						id = photo.id
						volume_id = location.volume_id
						local_id = location.local_id
						access_hash = photo.access_hash
						file_reference = photo.file_reference
						thumb_size = photoSize.type
					}.let {
						replacement?.set(0, it)
					}

					return photo.file_reference
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
		if (photoSize == null || location !is TL_inputFileLocation) {
			return null
		}

		return getFileReference(photoSize.location, location, needReplacement)
	}

	private fun getFileReference(fileLocation: FileLocation?, location: InputFileLocation, needReplacement: BooleanArray?): ByteArray? {
		if (fileLocation == null || location !is TL_inputFileLocation) {
			return null
		}

		if (fileLocation.local_id == location.local_id && fileLocation.volume_id == location.volume_id) {
			if (fileLocation.file_reference == null && needReplacement != null) {
				needReplacement[0] = true
			}

			return fileLocation.file_reference
		}

		return null
	}

	private fun getFileReference(webpage: WebPage, location: InputFileLocation?, needReplacement: BooleanArray?, replacement: Array<InputFileLocation?>?): ByteArray? {
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

		if (webpage.cached_page != null) {
			for (document in webpage.cached_page.documents) {
				result = getFileReference(document, location, needReplacement, replacement)

				if (result != null) {
					return result
				}
			}

			for (photo in webpage.cached_page.photos) {
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
				is TL_availableReaction -> {
					return "available_reaction_" + parentObject.reaction
				}

				is BotInfo -> {
					return "bot_info_" + parentObject.user_id
				}

				is TL_attachMenuBot -> {
					val botId = parentObject.bot_id
					return "attach_menu_bot_$botId"
				}

				is MessageObject -> {
					val channelId = parentObject.channelId
					return "message" + parentObject.realId + "_" + channelId + "_" + parentObject.scheduled
				}

				is Message -> {
					val channelId = parentObject.peer_id?.channel_id ?: 0
					return "message" + parentObject.id + "_" + channelId + "_" + parentObject.from_scheduled
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

				is TL_messages_stickerSet -> {
					return "set" + parentObject.set.id
				}

				is StickerSetCovered -> {
					return "set" + parentObject.set.id
				}

				is InputStickerSet -> {
					return "set" + parentObject.id
				}

				is TL_wallPaper -> {
					return "wallpaper" + parentObject.id
				}

				is TL_theme -> {
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
