/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.InputPeer
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.TL_fileLocationToBeDeprecated
import org.telegram.tgnet.TLRPC.TL_inputPeerChannel
import org.telegram.tgnet.TLRPC.TL_inputPeerChat
import org.telegram.tgnet.TLRPC.TL_inputPeerUser
import org.telegram.tgnet.TLRPC.TL_photoPathSize
import org.telegram.tgnet.TLRPC.TL_photoStrippedSize
import org.telegram.tgnet.TLRPC.VideoSize
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme

class ImageLocation {
	var fileReference: ByteArray? = null
	var key: ByteArray? = null
	var iv: ByteArray? = null
	var accessHash = 0L
	var secureDocument: SecureDocument? = null
	var document: TLRPC.Document? = null
	var videoSeekTo = 0L
	var photoPeerType = 0
	var photoPeer: InputPeer? = null
	var stickerSet: InputStickerSet? = null
	var thumbVersion = 0
	var documentId = 0L
	var thumbSize: String? = null
	var webFile: WebFile? = null

	@JvmField
	var dcId = 0

	@JvmField
	var location: TL_fileLocationToBeDeprecated? = null

	@JvmField
	var path: String? = null

	@JvmField
	var photoSize: PhotoSize? = null

	@JvmField
	var photo: TLRPC.Photo? = null

	@JvmField
	var imageType = 0

	@JvmField
	var currentSize = 0L

	@JvmField
	var photoId = 0L

	fun getKey(parentObject: Any?, fullObject: Any?, url: Boolean): String? {
		val secureDocument = secureDocument
		val photoSize = photoSize
		val location = location
		val webFile = webFile
		val document = document

		if (secureDocument != null) {
			return secureDocument.secureFile.dc_id.toString() + "_" + secureDocument.secureFile.id
		}
		else if (photoSize is TL_photoStrippedSize || photoSize is TL_photoPathSize) {
			if (photoSize.bytes.isNotEmpty()) {
				return getStrippedKey(parentObject, fullObject, photoSize)
			}
		}
		else if (location != null) {
			return location.volume_id.toString() + "_" + location.local_id
		}
		else if (webFile != null) {
			return Utilities.MD5(webFile.url)
		}
		else if (document != null) {
			if (!url && document is DocumentObject.ThemeDocument) {
				val themeDocument = document
				return document.dc_id.toString() + "_" + document.id + "_" + Theme.getBaseThemeKey(themeDocument.themeSettings) + "_" + themeDocument.themeSettings.accent_color + "_" + (if (themeDocument.themeSettings.message_colors.size > 1) themeDocument.themeSettings.message_colors[1] else 0) + "_" + (if (themeDocument.themeSettings.message_colors.size > 0) themeDocument.themeSettings.message_colors[0] else 0)
			}
			else if (document.id != 0L && document.dc_id != 0) {
				return document.dc_id.toString() + "_" + document.id
			}
		}
		else if (path != null) {
			return Utilities.MD5(path)
		}

		return null
	}

	val isEncrypted: Boolean
		get() = key != null

	val size: Long
		get() {
			val photoSize = photoSize
			val secureDocument = secureDocument
			val document = document
			val webFile = webFile

			if (photoSize != null) {
				return photoSize.size.toLong()
			}
			else if (secureDocument != null) {
				if (secureDocument.secureFile != null) {
					return secureDocument.secureFile.size
				}
			}
			else if (document != null) {
				return document.size
			}
			else if (webFile != null) {
				return webFile.size.toLong()
			}

			return currentSize
		}

	companion object {
		const val TYPE_BIG: Int = 0
		const val TYPE_SMALL: Int = 1
		const val TYPE_STRIPPED: Int = 2
		const val TYPE_VIDEO_THUMB: Int = 3

		@JvmStatic
		fun getForPath(path: String?): ImageLocation? {
			if (path == null) {
				return null
			}

			val imageLocation = ImageLocation()
			imageLocation.path = path
			return imageLocation
		}

		@JvmStatic
		fun getForSecureDocument(secureDocument: SecureDocument?): ImageLocation? {
			if (secureDocument == null) {
				return null
			}

			val imageLocation = ImageLocation()
			imageLocation.secureDocument = secureDocument
			return imageLocation
		}

		@JvmStatic
		fun getForDocument(document: TLRPC.Document?): ImageLocation? {
			if (document == null) {
				return null
			}

			val imageLocation = ImageLocation()
			imageLocation.document = document
			imageLocation.key = document.key
			imageLocation.iv = document.iv
			imageLocation.currentSize = document.size
			return imageLocation
		}

		@JvmStatic
		fun getForWebFile(webFile: WebFile?): ImageLocation? {
			if (webFile == null) {
				return null
			}

			val imageLocation = ImageLocation()
			imageLocation.webFile = webFile
			imageLocation.currentSize = webFile.size.toLong()
			return imageLocation
		}

		@JvmStatic
		fun getForObject(photoSize: PhotoSize?, `object`: TLObject?): ImageLocation? {
			if (`object` is TLRPC.Photo) {
				return getForPhoto(photoSize, `object` as TLRPC.Photo?)
			}
			else if (`object` is TLRPC.Document) {
				return getForDocument(photoSize, `object` as TLRPC.Document?)
			}

			return null
		}

		@JvmStatic
		fun getForPhoto(photoSize: PhotoSize?, photo: TLRPC.Photo?): ImageLocation? {
			if (photoSize is TL_photoStrippedSize || photoSize is TL_photoPathSize) {
				val imageLocation = ImageLocation()
				imageLocation.photoSize = photoSize
				return imageLocation
			}
			else if (photoSize == null || photo == null) {
				return null
			}

			val dcId = if (photo.dc_id != 0) {
				photo.dc_id
			}
			else {
				photoSize.location.dc_id
			}

			return getForPhoto(photoSize.location, photoSize.size, photo, null, null, TYPE_SMALL, dcId, null, photoSize.type)
		}

		@JvmStatic
		fun getForUserOrChat(`object`: TLObject?, type: Int): ImageLocation? {
			if (`object` is User) {
				return getForUser(`object`, type)
			}
			else if (`object` is Chat) {
				return getForChat(`object`, type)
			}

			return null
		}

		@JvmStatic
		fun getForUser(user: User?, type: Int): ImageLocation? {
			if (user == null || user.access_hash == 0L || user.photo == null) {
				return null
			}

			if (type == TYPE_VIDEO_THUMB) {
				val currentAccount = UserConfig.selectedAccount

				if (MessagesController.getInstance(currentAccount).isPremiumUser(user) && user.photo!!.has_video) {
					val userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id)

					return userFull?.profile_photo?.video_sizes?.find { it.type == "p" }?.let {
						getForPhoto(it, userFull.profile_photo)
					}
				}

				return null
			}

			if (type == TYPE_STRIPPED) {
				if (user.photo?.stripped_thumb == null) {
					return null
				}
				val imageLocation = ImageLocation()
				imageLocation.photoSize = TL_photoStrippedSize()
				imageLocation.photoSize?.type = "s"
				imageLocation.photoSize?.bytes = user.photo?.stripped_thumb
				return imageLocation
			}

			val fileLocation = if (type == TYPE_BIG) user.photo?.photo_big else user.photo?.photo_small

			if (fileLocation == null) {
				return null
			}

			val inputPeer = TL_inputPeerUser()
			inputPeer.user_id = user.id
			inputPeer.access_hash = user.access_hash

			val dcId = if (user.photo?.dc_id != 0) {
				user.photo?.dc_id
			}
			else {
				fileLocation.dc_id
			} ?: 0

			val location = getForPhoto(fileLocation, 0, null, null, inputPeer, type, dcId, null, null)
			location?.photoId = user.photo?.photo_id ?: 0
			return location
		}

		@JvmStatic
		fun getForChat(chat: Chat?, type: Int): ImageLocation? {
			if (chat?.photo == null) {
				return null
			}

			if (type == TYPE_STRIPPED) {
				if (chat.photo.stripped_thumb == null) {
					return null
				}

				val imageLocation = ImageLocation()
				imageLocation.photoSize = TL_photoStrippedSize()
				imageLocation.photoSize?.type = "s"
				imageLocation.photoSize?.bytes = chat.photo.stripped_thumb
				return imageLocation
			}

			val fileLocation = if (type == TYPE_BIG) chat.photo.photo_big else chat.photo.photo_small

			if (fileLocation == null) {
				return null
			}

			val inputPeer: InputPeer

			if (ChatObject.isChannel(chat)) {
				if (chat.access_hash == 0L) {
					return null
				}

				inputPeer = TL_inputPeerChannel()
				inputPeer.channel_id = chat.id
				inputPeer.access_hash = chat.access_hash
			}
			else {
				inputPeer = TL_inputPeerChat()
				inputPeer.chat_id = chat.id
			}

			val dcId = if (chat.photo.dc_id != 0) {
				chat.photo.dc_id
			}
			else {
				fileLocation.dc_id
			}

			val location = getForPhoto(fileLocation, 0, null, null, inputPeer, type, dcId, null, null)
			location?.photoId = chat.photo.photo_id
			return location
		}

		@JvmStatic
		fun getForSticker(photoSize: PhotoSize?, sticker: TLRPC.Document?, thumbVersion: Int): ImageLocation? {
			if (photoSize is TL_photoStrippedSize || photoSize is TL_photoPathSize) {
				val imageLocation = ImageLocation()
				imageLocation.photoSize = photoSize
				return imageLocation
			}
			else if (photoSize == null || sticker == null) {
				return null
			}

			val stickerSet = MessageObject.getInputStickerSet(sticker) ?: return null
			val imageLocation = getForPhoto(photoSize.location, photoSize.size, null, null, null, TYPE_SMALL, sticker.dc_id, stickerSet, photoSize.type)

			if (MessageObject.isAnimatedStickerDocument(sticker, true)) {
				imageLocation?.imageType = FileLoader.IMAGE_TYPE_LOTTIE
			}

			imageLocation?.thumbVersion = thumbVersion

			return imageLocation
		}

		@JvmStatic
		fun getForDocument(videoSize: VideoSize?, document: TLRPC.Document?): ImageLocation? {
			if (videoSize == null || document == null) {
				return null
			}

			val location = getForPhoto(videoSize.location, videoSize.size, null, document, null, TYPE_SMALL, document.dc_id, null, videoSize.type)

			if ("f" == videoSize.type) {
				location?.imageType = FileLoader.IMAGE_TYPE_LOTTIE
			}
			else {
				location?.imageType = FileLoader.IMAGE_TYPE_ANIMATION
			}

			return location
		}

		@JvmStatic
		fun getForPhoto(videoSize: VideoSize?, photo: TLRPC.Photo?): ImageLocation? {
			if (videoSize == null || photo == null) {
				return null
			}

			val location = getForPhoto(videoSize.location, videoSize.size, photo, null, null, TYPE_SMALL, photo.dc_id, null, videoSize.type)
			location?.imageType = FileLoader.IMAGE_TYPE_ANIMATION

			if ((videoSize.flags and 1) != 0) {
				location?.videoSeekTo = (videoSize.video_start_ts * 1000).toLong()
			}

			return location
		}

		@JvmStatic
		fun getForDocument(photoSize: PhotoSize?, document: TLRPC.Document?): ImageLocation? {
			if (photoSize is TL_photoStrippedSize || photoSize is TL_photoPathSize) {
				val imageLocation = ImageLocation()
				imageLocation.photoSize = photoSize
				return imageLocation
			}
			else if (photoSize == null || document == null) {
				return null
			}

			return getForPhoto(photoSize.location, photoSize.size, null, document, null, TYPE_SMALL, document.dc_id, null, photoSize.type)
		}

		@JvmStatic
		fun getForLocal(location: FileLocation?): ImageLocation? {
			if (location == null) {
				return null
			}

			val imageLocation = ImageLocation()
			imageLocation.location = TL_fileLocationToBeDeprecated()
			imageLocation.location!!.local_id = location.local_id
			imageLocation.location!!.volume_id = location.volume_id
			imageLocation.location!!.secret = location.secret
			imageLocation.location!!.dc_id = location.dc_id
			return imageLocation
		}

		private fun getForPhoto(location: FileLocation?, size: Int, photo: TLRPC.Photo?, document: TLRPC.Document?, photoPeer: InputPeer?, photoPeerType: Int, dcId: Int, stickerSet: InputStickerSet?, thumbSize: String?): ImageLocation? {
			if (location == null || photo == null && photoPeer == null && stickerSet == null && document == null) {
				return null
			}

			val imageLocation = ImageLocation()
			imageLocation.dcId = dcId
			imageLocation.photo = photo
			imageLocation.currentSize = size.toLong()
			imageLocation.photoPeer = photoPeer
			imageLocation.photoPeerType = photoPeerType
			imageLocation.stickerSet = stickerSet

			if (location is TL_fileLocationToBeDeprecated) {
				imageLocation.location = location

				if (photo != null) {
					imageLocation.fileReference = photo.file_reference
					imageLocation.accessHash = photo.access_hash
					imageLocation.photoId = photo.id
					imageLocation.thumbSize = thumbSize
				}
				else if (document != null) {
					imageLocation.fileReference = document.file_reference
					imageLocation.accessHash = document.access_hash
					imageLocation.documentId = document.id
					imageLocation.thumbSize = thumbSize
				}
			}
			else {
				imageLocation.location = TL_fileLocationToBeDeprecated()
				imageLocation.location?.local_id = location.local_id
				imageLocation.location?.volume_id = location.volume_id
				imageLocation.location?.secret = location.secret
				imageLocation.dcId = location.dc_id
				imageLocation.fileReference = location.file_reference
				imageLocation.key = location.key
				imageLocation.iv = location.iv
				imageLocation.accessHash = location.secret
			}

			return imageLocation
		}

		fun getStrippedKey(parentObject: Any?, fullObject: Any?, strippedObject: Any): String {
			@Suppress("NAME_SHADOWING") var fullObject = fullObject

			if (parentObject is WebPage) {
				if (fullObject is ImageLocation) {
					val imageLocation = fullObject

					if (imageLocation.document != null) {
						fullObject = imageLocation.document
					}
					else if (imageLocation.photoSize != null) {
						fullObject = imageLocation.photoSize
					}
					else if (imageLocation.photo != null) {
						fullObject = imageLocation.photo
					}
				}

				when (fullObject) {
					null -> {
						return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + strippedObject
					}

					is TLRPC.Document -> {
						return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + fullObject.id
					}

					is TLRPC.Photo -> {
						return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + fullObject.id
					}

					is PhotoSize -> {
						val size = fullObject

						return if (size.location != null) {
							"stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + size.location.local_id + "_" + size.location.volume_id
						}
						else {
							"stripped" + FileRefController.getKeyForParentObject(parentObject)
						}
					}

					is FileLocation -> {
						val loc = fullObject
						return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + loc.local_id + "_" + loc.volume_id
					}
				}
			}

			return "stripped" + FileRefController.getKeyForParentObject(parentObject)
		}
	}
}
