/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger

import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.accessHash
import org.telegram.tgnet.dcId
import org.telegram.tgnet.fileReference
import org.telegram.tgnet.photo
import org.telegram.tgnet.photoBig
import org.telegram.tgnet.photoId
import org.telegram.tgnet.photoSmall
import org.telegram.tgnet.videoSizes
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
	var photoPeer: TLRPC.InputPeer? = null
	var stickerSet: TLRPC.InputStickerSet? = null
	var thumbVersion = 0
	var documentId = 0L
	var thumbSize: String? = null
	var webFile: WebFile? = null

	@JvmField
	var dcId = 0

	@JvmField
	var location: TLRPC.FileLocation? = null

	@JvmField
	var path: String? = null

	@JvmField
	var photoSize: TLRPC.PhotoSize? = null

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
			return secureDocument.secureFile.dcId.toString() + "_" + secureDocument.secureFile.id
		}
		else if (photoSize is TLRPC.TLPhotoStrippedSize || photoSize is TLRPC.TLPhotoPathSize) {
			if (photoSize.bytes?.isNotEmpty() == true) {
				return getStrippedKey(parentObject, fullObject, photoSize)
			}
		}
		else if (location != null) {
			return location.volumeId.toString() + "_" + location.localId
		}
		else if (webFile != null) {
			return Utilities.MD5(webFile.url)
		}
		else if (document != null) {
			if (!url && document is DocumentObject.ThemeDocument) {
				val themeDocument = document
				return document.dcId.toString() + "_" + document.id + "_" + Theme.getBaseThemeKey(themeDocument.themeSettings) + "_" + themeDocument.themeSettings.accentColor + "_" + (if (themeDocument.themeSettings.messageColors.size > 1) themeDocument.themeSettings.messageColors[1] else 0) + "_" + (if (themeDocument.themeSettings.messageColors.size > 0) themeDocument.themeSettings.messageColors[0] else 0)
			}
			else if (document.id != 0L && document.dcId != 0) {
				return document.dcId.toString() + "_" + document.id
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
			// imageLocation.key = document.key
			// imageLocation.iv = document.iv
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
		fun getForObject(photoSize: TLRPC.PhotoSize?, `object`: TLObject?): ImageLocation? {
			if (`object` is TLRPC.Photo) {
				return getForPhoto(photoSize, `object` as TLRPC.Photo?)
			}
			else if (`object` is TLRPC.Document) {
				return getForDocument(photoSize, `object` as TLRPC.Document?)
			}

			return null
		}

		@JvmStatic
		fun getForPhoto(photoSize: TLRPC.PhotoSize?, photo: TLRPC.Photo?): ImageLocation? {
			if (photoSize is TLRPC.TLPhotoStrippedSize || photoSize is TLRPC.TLPhotoPathSize) {
				val imageLocation = ImageLocation()
				imageLocation.photoSize = photoSize
				return imageLocation
			}
			else if (photoSize == null || photo == null) {
				return null
			}

			val dcId = if (photo.dcId != 0) {
				photo.dcId
			}
			else {
				photoSize.location?.dcId ?: 0
			}

			return getForPhoto(photoSize.location, photoSize.size, photo, null, null, TYPE_SMALL, dcId, null, photoSize.type)
		}

		@JvmStatic
		fun getForUserOrChat(`object`: TLObject?, type: Int): ImageLocation? {
			if (`object` is TLRPC.User) {
				return getForUser(`object`, type)
			}
			else if (`object` is TLRPC.Chat) {
				return getForChat(`object`, type)
			}

			return null
		}

		@JvmStatic
		fun getForUser(user: TLRPC.User?, type: Int): ImageLocation? {
			if (user == null || user.accessHash == 0L || user.photo == null) {
				return null
			}

			if (type == TYPE_VIDEO_THUMB) {
				val currentAccount = UserConfig.selectedAccount

				if (MessagesController.getInstance(currentAccount).isPremiumUser(user) && user.photo?.hasVideo == true) {
					val userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id)

					return userFull?.profilePhoto?.videoSizes?.find { it.type == "p" }?.let {
						getForPhoto(it, userFull.profilePhoto)
					}
				}

				return null
			}

			if (type == TYPE_STRIPPED) {
				if (user.photo?.strippedThumb == null) {
					return null
				}
				val imageLocation = ImageLocation()
				imageLocation.photoSize = TLRPC.TLPhotoStrippedSize()
				imageLocation.photoSize?.type = "s"
				imageLocation.photoSize?.bytes = user.photo?.strippedThumb
				return imageLocation
			}

			val fileLocation = if (type == TYPE_BIG) user.photo?.photoBig else user.photo?.photoSmall

			if (fileLocation == null) {
				return null
			}

			val inputPeer = TLRPC.TLInputPeerUser()
			inputPeer.userId = user.id
			inputPeer.accessHash = user.accessHash

			val dcId = if (user.photo?.dcId != 0) {
				user.photo?.dcId
			}
			else {
				fileLocation.dcId
			} ?: 0

			val location = getForPhoto(fileLocation, 0, null, null, inputPeer, type, dcId, null, null)
			location?.photoId = user.photo?.photoId ?: 0
			return location
		}

		@JvmStatic
		fun getForChat(chat: TLRPC.Chat?, type: Int): ImageLocation? {
			if (chat?.photo == null) {
				return null
			}

			if (type == TYPE_STRIPPED) {
				if (chat.photo?.strippedThumb == null) {
					return null
				}

				val imageLocation = ImageLocation()
				imageLocation.photoSize = TLRPC.TLPhotoStrippedSize()
				imageLocation.photoSize?.type = "s"
				imageLocation.photoSize?.bytes = chat.photo?.strippedThumb
				return imageLocation
			}

			val fileLocation = if (type == TYPE_BIG) chat.photo?.photoBig else chat.photo?.photoSmall

			if (fileLocation == null) {
				return null
			}

			val inputPeer: TLRPC.InputPeer

			if (ChatObject.isChannel(chat)) {
				if (chat.accessHash == 0L) {
					return null
				}

				inputPeer = TLRPC.TLInputPeerChannel()
				inputPeer.channelId = chat.id
				inputPeer.accessHash = chat.accessHash
			}
			else {
				inputPeer = TLRPC.TLInputPeerChat()
				inputPeer.chatId = chat.id
			}

			val dcId = if (chat.photo?.dcId != 0) {
				chat.photo?.dcId
			}
			else {
				fileLocation.dcId
			} ?: 0

			val location = getForPhoto(fileLocation, 0, null, null, inputPeer, type, dcId, null, null)
			location?.photoId = chat.photo?.photoId ?: 0L
			return location
		}

		@JvmStatic
		fun getForSticker(photoSize: TLRPC.PhotoSize?, sticker: TLRPC.Document?, thumbVersion: Int): ImageLocation? {
			if (photoSize is TLRPC.TLPhotoStrippedSize || photoSize is TLRPC.TLPhotoPathSize) {
				val imageLocation = ImageLocation()
				imageLocation.photoSize = photoSize
				return imageLocation
			}
			else if (photoSize == null || sticker == null) {
				return null
			}

			val stickerSet = MessageObject.getInputStickerSet(sticker) ?: return null
			val imageLocation = getForPhoto(photoSize.location, photoSize.size, null, null, null, TYPE_SMALL, sticker.dcId, stickerSet, photoSize.type)

			if (MessageObject.isAnimatedStickerDocument(sticker, true)) {
				imageLocation?.imageType = FileLoader.IMAGE_TYPE_LOTTIE
			}

			imageLocation?.thumbVersion = thumbVersion

			return imageLocation
		}

		@JvmStatic
		fun getForDocument(videoSize: TLRPC.VideoSize?, document: TLRPC.Document?): ImageLocation? {
			if (videoSize == null || document == null) {
				return null
			}

			val location = getForPhoto(videoSize.location, videoSize.size, null, document, null, TYPE_SMALL, document.dcId, null, videoSize.type)

			if ("f" == videoSize.type) {
				location?.imageType = FileLoader.IMAGE_TYPE_LOTTIE
			}
			else {
				location?.imageType = FileLoader.IMAGE_TYPE_ANIMATION
			}

			return location
		}

		@JvmStatic
		fun getForPhoto(videoSize: TLRPC.VideoSize?, photo: TLRPC.Photo?): ImageLocation? {
			if (videoSize == null || photo == null) {
				return null
			}

			val location = getForPhoto(videoSize.location, videoSize.size, photo, null, null, TYPE_SMALL, photo.dcId, null, videoSize.type)
			location?.imageType = FileLoader.IMAGE_TYPE_ANIMATION

			if ((videoSize.flags and 1) != 0) {
				location?.videoSeekTo = (videoSize.videoStartTs * 1000).toLong()
			}

			return location
		}

		@JvmStatic
		fun getForDocument(photoSize: TLRPC.PhotoSize?, document: TLRPC.Document?): ImageLocation? {
			if (photoSize is TLRPC.TLPhotoStrippedSize || photoSize is TLRPC.TLPhotoPathSize) {
				val imageLocation = ImageLocation()
				imageLocation.photoSize = photoSize
				return imageLocation
			}
			else if (photoSize == null || document == null) {
				return null
			}

			return getForPhoto(photoSize.location, photoSize.size, null, document, null, TYPE_SMALL, document.dcId, null, photoSize.type)
		}

		@JvmStatic
		fun getForLocal(location: TLRPC.FileLocation?): ImageLocation? {
			if (location == null) {
				return null
			}

			val imageLocation = ImageLocation()
			imageLocation.location = TLRPC.TLFileLocation()
			imageLocation.location?.localId = location.localId
			imageLocation.location?.volumeId = location.volumeId
			// imageLocation.location?.secret = location.secret
			imageLocation.location?.dcId = location.dcId
			return imageLocation
		}

		private fun getForPhoto(location: TLRPC.FileLocation?, size: Int, photo: TLRPC.Photo?, document: TLRPC.Document?, photoPeer: TLRPC.InputPeer?, photoPeerType: Int, dcId: Int, stickerSet: TLRPC.InputStickerSet?, thumbSize: String?): ImageLocation? {
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

			if (location is TLRPC.TLFileLocation) {
				imageLocation.location = location

				if (photo != null) {
					imageLocation.fileReference = photo.fileReference
					imageLocation.accessHash = photo.accessHash
					imageLocation.photoId = photo.id
					imageLocation.thumbSize = thumbSize
				}
				else if (document != null) {
					imageLocation.fileReference = document.fileReference
					imageLocation.accessHash = document.accessHash
					imageLocation.documentId = document.id
					imageLocation.thumbSize = thumbSize
				}
			}
			else {
				imageLocation.location = TLRPC.TLFileLocation()
				imageLocation.location?.localId = location.localId
				imageLocation.location?.volumeId = location.volumeId
				// imageLocation.location?.secret = location.secret
				imageLocation.dcId = location.dcId
				//imageLocation.fileReference = location.fileReference
				//imageLocation.key = location.key
				//imageLocation.iv = location.iv
				// imageLocation.accessHash = location.secret
			}

			return imageLocation
		}

		fun getStrippedKey(parentObject: Any?, fullObject: Any?, strippedObject: Any): String {
			@Suppress("NAME_SHADOWING") var fullObject = fullObject

			if (parentObject is TLRPC.WebPage) {
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

					is TLRPC.PhotoSize -> {
						val size = fullObject

						return if (size.location != null) {
							"stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + size.location?.localId + "_" + size.location?.volumeId
						}
						else {
							"stripped" + FileRefController.getKeyForParentObject(parentObject)
						}
					}

					is TLRPC.FileLocation -> {
						val loc = fullObject
						return "stripped" + FileRefController.getKeyForParentObject(parentObject) + "_" + loc.localId + "_" + loc.volumeId
					}
				}
			}

			return "stripped" + FileRefController.getKeyForParentObject(parentObject)
		}
	}
}
