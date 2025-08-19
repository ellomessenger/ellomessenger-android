/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
@file:JvmName("TLRPCExtensions")

package org.telegram.tgnet

import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toDrawable
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC.TLUserProfilePhotoLayer127

val TLRPC.Peer?.channelId: Long
	get() = when (this) {
		is TLRPC.TLPeerChannel -> channelId
		else -> 0L
	}

val TLRPC.Peer?.chatId: Long
	get() = when (this) {
		is TLRPC.TLPeerChat -> chatId
		else -> 0L
	}

var TLRPC.Peer?.userId: Long
	get() = when (this) {
		is TLRPC.TLPeerUser -> userId
		else -> 0L
	}
	set(value) {
		if (this is TLRPC.TLPeerUser) {
			userId = value
		}
	}

val TLRPC.InputPeer?.chatId: Long
	get() = when (this) {
		is TLRPC.TLInputPeerChat -> chatId
		else -> 0L
	}

fun TLRPC.Message.readAttachPath(stream: AbstractSerializedData, currentUserId: Long) {
	if (this !is TLRPC.TLMessage) {
		return
	}

	val hasMedia = media != null && (media !is TLRPC.TLMessageMediaEmpty) && (media !is TLRPC.TLMessageMediaWebPage)
	val fixCaption = !message.isNullOrEmpty() && message?.startsWith("-1") == true

	if ((out || (peerId != null && fromId != null && (peerId as? TLRPC.TLPeerUser)?.userId != 0L && (peerId as? TLRPC.TLPeerUser)?.userId == (fromId as? TLRPC.TLPeerUser)?.userId && (fromId as? TLRPC.TLPeerUser)?.userId == currentUserId)) && (id < 0 || hasMedia || sendState == 3) || legacy) {
		if (hasMedia && fixCaption) {
			if (message != null && (message?.length ?: 0) > 6 && message?.toCharArray()?.get(2) == '_') {
				params = mutableMapOf()
				params?.put("ve", message ?: "")
			}

			if (params != null || (message != null && message?.length == 2)) {
				message = ""
			}
		}

		if (stream.remaining() > 0) {
			attachPath = stream.readString(false)

			if (attachPath != null) {
				if ((id < 0 || sendState == 3 || legacy) && attachPath?.startsWith("||") == true) {
					val args = attachPath?.split("\\|\\|")
					if (!args.isNullOrEmpty()) {
						if (params == null) {
							params = mutableMapOf()
						}

						for (a in 1..<args.size - 1) {
							val args2 = args[a].split("\\|=\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

							if (args2.size == 2) {
								params?.put(args2[0], args2[1])
							}
						}

						attachPath = args[args.size - 1].trim()

						if (legacy) {
							layer = Utilities.parseInt(params?.get("legacy_layer"))
						}
					}
				}
				else {
					attachPath = attachPath?.trim()
				}
			}
		}

		if ((flags and TLRPC.MESSAGE_FLAG_FWD) != 0 && id < 0) {
			fwdMsgId = stream.readInt32(false)
		}
	}
}

val TLRPC.Message.fromScheduled: Boolean
	get() {
		if (this is TLRPC.TLMessage) {
			return fromScheduled
		}

		return false
	}

val TLRPC.ChatPhoto.photoId: Long
	get() = when (this) {
		is TLRPC.TLChatPhoto -> photoId
		else -> 0L
	}

var TLRPC.ChatPhoto?.photoBig: TLRPC.FileLocation?
	get() {
		when (this) {
			is TLRPC.TLChatPhotoLayer127 -> {
				return photoBig
			}

			is TLRPC.TLChatPhoto -> {
				if (photoId == 0L) {
					return null
				}

				val location = TLRPC.TLFileLocation()
				location.volumeId = -photoId
				location.localId = 'c'.code
				return location
			}

			else -> {
				return null
			}
		}
	}
	set(value) {
		if (this is TLRPC.TLChatPhotoLayer127) {
			photoBig = value
		}
	}

var TLRPC.ChatPhoto?.photoSmall: TLRPC.FileLocation?
	get() {
		when (this) {
			is TLRPC.TLChatPhotoLayer127 -> {
				return photoSmall
			}

			is TLRPC.TLChatPhoto -> {
				if (photoId == 0L) {
					return null
				}

				val location = TLRPC.TLFileLocation()
				location.volumeId = -photoId
				location.localId = 'a'.code
				return location
			}

			else -> {
				return null
			}
		}
	}
	set(value) {
		if (this is TLRPC.TLChatPhotoLayer127) {
			photoSmall = value
		}
	}

val TLRPC.TLChatPhoto.strippedBitmap: BitmapDrawable?
	get() {
		val strippedThumb = strippedThumb

		if (strippedThumb != null) {
			try {
				return ImageLoader.getStrippedPhotoBitmap(strippedThumb, "b")?.toDrawable(ApplicationLoader.applicationContext.resources)
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		return null
	}

var TLRPC.UserProfilePhoto?.photoBig: TLRPC.FileLocation?
	get() {
		when (this) {
			is TLUserProfilePhotoLayer127 -> {
				return photoBig
			}

			is TLRPC.TLUserProfilePhoto -> {
				if (photoId == 0L) {
					return null
				}

				val location = TLRPC.TLFileLocation()
				location.volumeId = -photoId
				location.localId = 'c'.code
				return location
			}

			else -> {
				return null
			}
		}
	}
	set(value) {
		if (this is TLUserProfilePhotoLayer127) {
			photoBig = value
		}
	}

val TLRPC.UserProfilePhoto.strippedBitmap: BitmapDrawable?
	get() {
		if (this is TLRPC.TLUserProfilePhoto && strippedThumb != null) {
			try {
				return ImageLoader.getStrippedPhotoBitmap(strippedThumb, "b")?.toDrawable(ApplicationLoader.applicationContext.resources)
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		return null
	}

val TLRPC.ChatPhoto.strippedBitmap: BitmapDrawable?
	get() {
		if (this is TLRPC.TLChatPhoto && strippedThumb != null) {
			try {
				return ImageLoader.getStrippedPhotoBitmap(strippedThumb, "b")?.toDrawable(ApplicationLoader.applicationContext.resources)
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		return null
	}

var TLRPC.UserProfilePhoto?.photoSmall: TLRPC.FileLocation?
	get() {
		when (this) {
			is TLUserProfilePhotoLayer127 -> {
				return photoSmall
			}

			is TLRPC.TLUserProfilePhoto -> {
				if (photoId == 0L) {
					return null
				}

				val location = TLRPC.TLFileLocation()
				location.volumeId = -photoId
				location.localId = 'a'.code
				return location
			}

			else -> {
				return null
			}
		}
	}
	set(value) {
		if (this is TLUserProfilePhotoLayer127) {
			photoSmall = value
		}
	}

val TLRPC.TLUserProfilePhoto.strippedBitmap: BitmapDrawable?
	get() {
		val strippedThumb = strippedThumb

		if (strippedThumb != null) {
			try {
				return ImageLoader.getStrippedPhotoBitmap(strippedThumb, "b")?.toDrawable(ApplicationLoader.applicationContext.resources)
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		return null
	}

var TLRPC.Message.groupedId: Long
	get() {
		if (this is TLRPC.TLMessage) {
			return groupedId
		}

		return 0L
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			groupedId = value
		}
	}

val TLRPC.MessageMedia?.userId: Long
	get() {
		if (this is TLRPC.TLMessageMediaContact) {
			return userId
		}

		return 0L
	}

val TLRPC.MessageMedia?.vcard: String?
	get() {
		if (this is TLRPC.TLMessageMediaContact) {
			return vcard
		}

		return null
	}

val TLRPC.MessageMedia?.firstName: String?
	get() {
		if (this is TLRPC.TLMessageMediaContact) {
			return firstName
		}

		return null
	}

val TLRPC.MessageMedia?.lastName: String?
	get() {
		if (this is TLRPC.TLMessageMediaContact) {
			return lastName
		}

		return null
	}

var TLRPC.Message.replyMarkup: TLRPC.ReplyMarkup?
	get() {
		if (this is TLRPC.TLMessage) {
			return replyMarkup
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			replyMarkup = value
		}
	}

val TLRPC.MessageAction.userId: Long
	get() {
		if (this is TLRPC.TLMessageActionChatDeleteUser) {
			return userId
		}

		return 0L
	}

val TLRPC.MessageAction.channelId: Long
	get() {
		if (this is TLRPC.TLMessageActionChatMigrateTo) {
			return channelId
		}

		return 0L
	}

val TLRPC.MessageAction.chatId: Long
	get() {
		if (this is TLRPC.TLMessageActionChannelMigrateFrom) {
			return chatId
		}

		return 0L
	}

val TLRPC.Message.action: TLRPC.MessageAction?
	get() {
		if (this is TLRPC.TLMessageService) {
			return action
		}

		return null
	}

var TLRPC.Message.views: Int
	get() {
		if (this is TLRPC.TLMessage) {
			return views
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			views = value
		}
	}

var TLRPC.MessageMedia?.webpage: TLRPC.WebPage?
	get() {
		if (this is TLRPC.TLMessageMediaWebPage) {
			return webpage
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessageMediaWebPage) {
			webpage = value
		}
	}

val TLRPC.MessageMedia.game: TLRPC.TLGame?
	get() {
		if (this is TLRPC.TLMessageMediaGame) {
			return game
		}

		return null
	}

var TLRPC.MessageMedia.extendedMedia: TLRPC.MessageExtendedMedia?
	get() {
		if (this is TLRPC.TLMessageMediaInvoice) {
			return extendedMedia
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessageMediaInvoice) {
			extendedMedia = value
		}
	}

val TLRPC.MessageMedia?.description: String?
	get() {
		if (this is TLRPC.TLMessageMediaInvoice) {
			return description
		}

		return null
	}

var TLRPC.MessageMedia.period: Int
	get() {
		if (this is TLRPC.TLMessageMediaGeoLive) {
			return period
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLMessageMediaGeoLive) {
			period = value
		}
	}

var TLRPC.Message.reactions: TLRPC.TLMessageReactions?
	get() {
		if (this is TLRPC.TLMessage) {
			return reactions
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			reactions = value
		}
	}

var TLRPC.Message?.message: String?
	get() {
		if (this is TLRPC.TLMessage) {
			return message
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			message = value
		}
	}

var TLRPC.Message?.media: TLRPC.MessageMedia?
	get() {
		if (this is TLRPC.TLMessage) {
			return media
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			media = value
		}
	}

var TLRPC.MessageMedia?.document: TLRPC.Document?
	get() {
		if (this is TLRPC.TLMessageMediaDocument) {
			return document
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessageMediaDocument) {
			document = value
		}
	}

var TLRPC.MessageMedia?.photo: TLRPC.Photo?
	get() {
		if (this is TLRPC.TLMessageMediaPhoto) {
			return photo
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessageMediaPhoto) {
			photo = value
		}
	}

var TLRPC.Message.entities: MutableList<TLRPC.MessageEntity>?
	get() {
		if (this is TLRPC.TLMessage) {
			return entities
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			entities.clear()

			if (!value.isNullOrEmpty()) {
				entities.addAll(value)
			}
		}
	}

var TLRPC.Message?.fwdFrom: TLRPC.TLMessageFwdHeader?
	get() = (this as? TLRPC.TLMessage)?.fwdFrom
	set(value) {
		if (this is TLRPC.TLMessage) {
			fwdFrom = value
		}
	}

var TLRPC.Message.replies: TLRPC.TLMessageReplies?
	get() {
		if (this is TLRPC.TLMessage) {
			return replies
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			replies = value
		}
	}

var TLRPC.Message.postAuthor: String?
	get() {
		if (this is TLRPC.TLMessage) {
			return postAuthor
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			postAuthor = value
		}
	}

val TLRPC.MessageAction.photo: TLRPC.Photo?
	get() {
		if (this is TLRPC.TLMessageActionChatEditPhoto) {
			return photo
		}

		return null
	}

val TLRPC.Document?.thumbs: MutableList<TLRPC.PhotoSize>?
	get() {
		if (this is TLRPC.TLDocument) {
			return thumbs
		}

		return null
	}

var TLRPC.Photo.dcId: Int
	get() {
		if (this is TLRPC.TLPhoto) {
			return dcId
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLPhoto) {
			dcId = value
		}
	}

var TLRPC.Photo.accessHash: Long
	get() {
		if (this is TLRPC.TLPhoto) {
			return accessHash
		}

		return 0L
	}
	set(value) {
		if (this is TLRPC.TLPhoto) {
			accessHash = value
		}
	}

val TLRPC.Photo.fileReference: ByteArray?
	get() {
		if (this is TLRPC.TLPhoto) {
			return fileReference
		}

		return null
	}

val TLRPC.Photo?.sizes: MutableList<TLRPC.PhotoSize>?
	get() {
		if (this is TLRPC.TLPhoto) {
			return sizes
		}

		return null
	}

@Deprecated("Will be removed in future versions")
var TLRPC.FileLocation.dcId: Int
	get() {
//		if (this is TLRPC.TLFileLocation) {
//			return dcId
//		}

		return 0
	}
	set(value) {
		// unused
	}

val TLRPC.InputDocument.id: Long
	get() {
		if (this is TLRPC.TLInputDocument) {
			return id
		}

		return 0L
	}

var TLRPC.InputDocument.fileReference: ByteArray?
	get() {
		if (this is TLRPC.TLInputDocument) {
			return fileReference
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLInputDocument) {
			fileReference = value
		}
	}

val TLRPC.InputPhoto.id: Long
	get() {
		if (this is TLRPC.TLInputPhoto) {
			return id
		}

		return 0L
	}

var TLRPC.InputPhoto.fileReference: ByteArray?
	get() {
		if (this is TLRPC.TLInputPhoto) {
			return fileReference
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLInputPhoto) {
			fileReference = value
		}
	}

var TLRPC.Document.dcId: Int
	get() {
		if (this is TLRPC.TLDocument) {
			return dcId
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLDocument) {
			dcId = value
		}
	}

val TLRPC.Document.fileReference: ByteArray?
	get() {
		if (this is TLRPC.TLDocument) {
			return fileReference
		}

		return null
	}

var TLRPC.Document.accessHash: Long
	get() {
		if (this is TLRPC.TLDocument) {
			return accessHash
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLDocument) {
			accessHash = value
		}
	}

val TLRPC.Chat.migratedTo: TLRPC.InputChannel?
	get() {
		if (this is TLRPC.TLChat) {
			return migratedTo
		}

		return null
	}

val TLRPC.Chat.version: Int
	get() {
		if (this is TLRPC.TLChat) {
			return version
		}

		return 0
	}

var TLRPC.Dialog.readInboxMaxId: Int
	get() {
		if (this is TLRPC.TLDialog) {
			return readInboxMaxId
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLDialog) {
			readInboxMaxId = value
		}
	}

var TLRPC.Dialog.notifySettings: TLRPC.PeerNotifySettings?
	get() {
		if (this is TLRPC.TLDialog) {
			return notifySettings
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLDialog) {
			notifySettings = value
		}
	}

val TLRPC.Dialog.readOutboxMaxId: Int
	get() {
		if (this is TLRPC.TLDialog) {
			return readOutboxMaxId
		}

		return 0
	}

val TLRPC.ChatParticipants?.participants: MutableList<TLRPC.ChatParticipant>?
	get() {
		if (this is TLRPC.TLChatParticipants) {
			return participants
		}

		return null
	}

var TLRPC.Dialog.unreadCount: Int
	get() {
		if (this is TLRPC.TLDialog) {
			return unreadCount
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLDialog) {
			unreadCount = value
		}
	}

var TLRPC.Dialog.folderId: Int
	get() {
		if (this is TLRPC.TLDialog) {
			return folderId
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLDialog) {
			folderId = value
		}
	}

var TLRPC.Dialog.unreadMentionsCount: Int
	get() {
		if (this is TLRPC.TLDialog) {
			return unreadMentionsCount
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLDialog) {
			unreadMentionsCount = value
		}
	}

var TLRPC.Dialog.unreadMark: Boolean
	get() {
		if (this is TLRPC.TLDialog) {
			return unreadMark
		}

		return false
	}
	set(value) {
		if (this is TLRPC.TLDialog) {
			unreadMark = value
		}
	}

val TLRPC.User.contact: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return contact
		}

		return false
	}

var TLRPC.Dialog.unreadReactionsCount: Int
	get() {
		if (this is TLRPC.TLDialog) {
			return unreadReactionsCount
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLDialog) {
			unreadReactionsCount = value
		}
	}

const val USER_STATUS_RECENTLY = -100
const val USER_STATUS_LAST_WEEK = -101
const val USER_STATUS_LAST_MONTH = -102
const val USER_STATUS_OFFLINE = 0
const val USER_STATUS_INVISIBLE = -1

val TLRPC.UserStatus.expires: Int
	get() {
		when (this) {
			is TLRPC.TLUserStatusRecently -> return USER_STATUS_RECENTLY
			is TLRPC.TLUserStatusLastWeek -> return USER_STATUS_LAST_WEEK
			is TLRPC.TLUserStatusLastMonth -> return USER_STATUS_LAST_MONTH
			is TLRPC.TLUserStatusOffline -> return USER_STATUS_OFFLINE
			is TLRPC.TLUserStatusOnline -> return expires
		}

		return 0
	}

fun TLRPC.User.setStatusFromExpires(expires: Int) {
	if (this !is TLRPC.TLUser) {
		return
	}

	when (expires) {
		USER_STATUS_RECENTLY -> {
			status = TLRPC.TLUserStatusRecently()
		}

		USER_STATUS_LAST_WEEK -> {
			status = TLRPC.TLUserStatusLastWeek()
		}

		USER_STATUS_LAST_MONTH -> {
			status = TLRPC.TLUserStatusLastMonth()
		}

		USER_STATUS_OFFLINE -> {
			status = TLRPC.TLUserStatusOffline()
		}

		else -> {
			status = TLRPC.TLUserStatusOnline().also {
				it.expires = expires
			}
		}
	}
}

var TLRPC.InputMedia.thumb: TLRPC.InputFile?
	get() {
		if (this is TLRPC.TLInputMediaUploadedDocument) {
			return thumb
		}

		return null
	}
	set(value) {
		if (this is TLRPC.TLInputMediaUploadedDocument) {
			thumb = value
		}
	}

val TLRPC.BotInlineResult.content: TLRPC.WebDocument?
	get() {
		if (this is TLRPC.TLBotInlineResult) {
			return content
		}

		return null
	}

val TLRPC.BotInlineResult.photo: TLRPC.Photo?
	get() {
		if (this is TLRPC.TLBotInlineMediaResult) {
			return photo
		}

		return null
	}

val TLRPC.BotInlineResult.url: String?
	get() {
		if (this is TLRPC.TLBotInlineResult) {
			return url
		}

		return null
	}

val TLRPC.BotInlineResult.thumb: TLRPC.WebDocument?
	get() {
		if (this is TLRPC.TLBotInlineResult) {
			return thumb
		}

		return null
	}

val TLRPC.BotInlineResult.document: TLRPC.Document?
	get() {
		if (this is TLRPC.TLBotInlineMediaResult) {
			return document
		}

		return null
	}

val TLRPC.User.photo: TLRPC.UserProfilePhoto?
	get() {
		if (this is TLRPC.TLUser) {
			return photo
		}

		return null
	}

val TLRPC.User.restrictionReason: MutableList<TLRPC.TLRestrictionReason>?
	get() {
		if (this is TLRPC.TLUser) {
			return restrictionReason
		}

		return null
	}

val TLRPC.Message?.restrictionReason: MutableList<TLRPC.TLRestrictionReason>?
	get() {
		if (this is TLRPC.TLMessage) {
			return restrictionReason
		}

		return null
	}

var TLRPC.Message.viaBotId: Long
	get() {
		if (this is TLRPC.TLMessage) {
			return viaBotId
		}

		return 0L
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			viaBotId = value
		}
	}

val TLRPC.KeyboardButton.data: ByteArray?
	get() {
		if (this is TLRPC.TLKeyboardButtonCallback) {
			return data
		}

		return null
	}

var TLRPC.Message.likes: Int
	get() {
		if (this is TLRPC.TLMessage) {
			return likes
		}

		return 0
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			likes = value
		}
	}

var TLRPC.Message.isLiked: Boolean
	get() {
		if (this is TLRPC.TLMessage) {
			return isLiked
		}

		return false
	}
	set(value) {
		if (this is TLRPC.TLMessage) {
			isLiked = value
		}
	}

val TLRPC.KeyboardButton.requiresPassword: Boolean
	get() {
		if (this is TLRPC.TLKeyboardButtonCallback) {
			return requiresPassword
		}

		return false
	}

val TLRPC.WebPage.embedUrl: String?
	get() {
		if (this is TLRPC.TLWebPage) {
			return embedUrl
		}

		return null
	}

val TLRPC.WebPage?.cachedPage: TLRPC.TLPage?
	get() {
		if (this is TLRPC.TLWebPage) {
			return cachedPage
		}

		return null
	}

val TLRPC.WebPage.description: String?
	get() {
		if (this is TLRPC.TLWebPage) {
			return description
		}

		return null
	}

val TLRPC.WebPage.embedWidth: Int
	get() {
		if (this is TLRPC.TLWebPage) {
			return embedWidth
		}

		return 0
	}

val TLRPC.WebPage.embedHeight: Int
	get() {
		if (this is TLRPC.TLWebPage) {
			return embedHeight
		}

		return 0
	}

val TLRPC.WebPage.type: String?
	get() {
		if (this is TLRPC.TLWebPage) {
			return type
		}

		return null
	}

val TLRPC.WebPage.attributes: MutableList<TLRPC.TLWebPageAttribute>?
	get() {
		if (this is TLRPC.TLWebPage) {
			return attributes
		}

		return null
	}

val TLRPC.WebPage.siteName: String?
	get() {
		if (this is TLRPC.TLWebPage) {
			return siteName
		}

		return null
	}

val TLRPC.WebPage.title: String?
	get() {
		if (this is TLRPC.TLWebPage) {
			return title
		}

		return null
	}

val TLRPC.WebPage.photo: TLRPC.TLPhoto?
	get() {
		if (this is TLRPC.TLWebPage) {
			return photo as? TLRPC.TLPhoto
		}

		return null
	}

val TLRPC.WebPage.document: TLRPC.Document?
	get() {
		if (this is TLRPC.TLWebPage) {
			return document
		}

		return null
	}

val TLRPC.GeoPoint.lat: Double
	get() {
		if (this is TLRPC.TLGeoPoint) {
			return lat
		}

		return 0.0
	}

val TLRPC.GeoPoint.lon: Double
	get() {
		if (this is TLRPC.TLGeoPoint) {
			return lon
		}

		return 0.0
	}

val TLRPC.GeoPoint.accessHash: Long
	get() {
		if (this is TLRPC.TLGeoPoint) {
			return accessHash
		}

		return 0L
	}

val TLRPC.MessageMedia.heading: Int
	get() {
		if (this is TLRPC.TLMessageMediaGeoLive) {
			return heading
		}

		return 0
	}

val TLRPC.User.botInlineGeo: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return botInlineGeo
		}

		return false
	}

val TLRPC.Message.noforwards: Boolean
	get() {
		if (this is TLRPC.TLMessage) {
			return noforwards
		}

		return false
	}

val TLRPC.MessageMedia.address: String?
	get() {
		if (this is TLRPC.TLMessageMediaVenue) {
			return address
		}

		return null
	}

val TLRPC.MessageMedia.test: Boolean
	get() {
		if (this is TLRPC.TLMessageMediaInvoice) {
			return test
		}

		return false
	}

val TLRPC.MessageMedia.totalAmount: Long
	get() {
		if (this is TLRPC.TLMessageMediaInvoice) {
			return totalAmount
		}

		return 0L
	}

val TLRPC.MessageMedia.currency: String?
	get() {
		if (this is TLRPC.TLMessageMediaInvoice) {
			return currency
		}

		return null
	}

val TLRPC.MessageMedia?.phoneNumber: String?
	get() {
		if (this is TLRPC.TLMessageMediaContact) {
			return phoneNumber
		}

		return null
	}

val TLRPC.Message.editDate: Int
	get() {
		if (this is TLRPC.TLMessage) {
			return editDate
		}

		return 0
	}

val TLRPC.Message.editHide: Boolean
	get() {
		if (this is TLRPC.TLMessage) {
			return editHide
		}

		return false
	}

val TLRPC.User.botChatHistory: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return botChatHistory
		}

		return false
	}

val TLRPC.User?.bot: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return bot
		}

		return false
	}

val TLRPC.User?.deleted: Boolean
	get() = (this as? TLRPC.TLUser)?.deleted == true

val TLRPC.User.botAttachMenu: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return botAttachMenu
		}

		return false
	}

val TLRPC.User.support: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return support
		}

		return false
	}

val TLRPC.User.isSelf: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return isSelf
		}

		return false
	}

val TLRPC.User.scam: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return scam
		}

		return false
	}

val TLRPC.User.fake: Boolean
	get() {
		if (this is TLRPC.TLUser) {
			return fake
		}

		return false
	}

var TLRPC.User.emojiStatus: TLRPC.EmojiStatus?
	get() = (this as? TLRPC.TLUser)?.emojiStatus
	set(value) {
		if (this is TLRPC.TLUser) {
			emojiStatus = value
		}
	}

val TLRPC.User.mutualContact: Boolean
	get() = (this is TLRPC.TLUser && mutualContact)

val TLRPC.DraftMessage.message: String?
	get() {
		if (this is TLRPC.TLDraftMessage) {
			return message
		}

		return null
	}

val TLRPC.DraftMessage.entities: MutableList<TLRPC.MessageEntity>?
	get() {
		if (this is TLRPC.TLDraftMessage) {
			return entities
		}

		return null
	}

val TLRPC.User.status: TLRPC.UserStatus?
	get() {
		if (this is TLRPC.TLUser) {
			return status
		}

		return null
	}

val TLRPC.MessageEntity.url: String?
	get() {
		if (this is TLRPC.TLMessageEntityTextUrl) {
			return url
		}

		return null
	}

var TLRPC.ChannelParticipant.bannedRights: TLRPC.TLChatBannedRights?
	get() = (this as? TLRPC.TLChannelParticipantBanned)?.bannedRights
	set(value) {
		if (this is TLRPC.TLChannelParticipantBanned) {
			bannedRights = value
		}
	}

val TLRPC.ExportedChatInvite.link: String?
	get() = (this as? TLRPC.TLChatInviteExported)?.link

val TLRPC.ChatFull.migratedFromChatId: Long
	get() = (this as? TLRPC.TLChannelFullLayer3)?.migratedFromChatId ?: 0L

val TLRPC.ChatFull.migratedFromMaxId: Int
	get() = (this as? TLRPC.TLChannelFullLayer3)?.migratedFromMaxId ?: 0

val TLRPC.GroupCall?.rtmpStream: Boolean
	get() = (this as? TLRPC.TLGroupCall)?.rtmpStream ?: false

val TLRPC.StickerSetCovered.cover: TLRPC.Document?
	get() = (this as? TLRPC.TLStickerSetCovered)?.cover

val TLRPC.StickerSetCovered.covers: MutableList<TLRPC.Document>?
	get() = (this as? TLRPC.TLStickerSetMultiCovered)?.covers

val TLRPC.InputStickerSet.id: Long
	get() = (this as? TLRPC.TLInputStickerSetID)?.id ?: 0L

val TLRPC.InputStickerSet.shortName: String?
	get() = (this as? TLRPC.TLInputStickerSetShortName)?.shortName

val TLRPC.GroupCall.scheduleDate: Int
	get() = (this as? TLRPC.TLGroupCall)?.scheduleDate ?: 0

val TLRPC.GroupCall?.participantsCount: Int
	get() = (this as? TLRPC.TLGroupCall)?.participantsCount ?: 0

val TLRPC.GroupCall?.title: String?
	get() = (this as? TLRPC.TLGroupCall)?.title

val TLRPC.User.botNochats: Boolean
	get() = (this as? TLRPC.TLUser)?.botNochats ?: false

val TLRPC.ExportedChatInvite.usage: Int
	get() = (this as? TLRPC.TLChatInviteExported)?.usage ?: 0

var TLRPC.ExportedChatInvite.importers: MutableList<TLRPC.User>?
	get() = (this as? TLRPC.TLChatInviteExported)?.importers
	set(value) {
		if (this is TLRPC.TLChatInviteExported) {
			importers = value
		}
	}

val TLRPC.Photo.videoSizes: MutableList<TLRPC.VideoSize>?
	get() = (this as? TLRPC.TLPhoto)?.videoSizes

var TLRPC.Message.forwards: Int
	get() = (this as? TLRPC.TLMessage)?.forwards ?: 0
	set(value) {
		if (this is TLRPC.TLMessage) {
			forwards = value
		}
	}

val TLRPC.GroupCall?.recordStartDate: Int
	get() = (this as? TLRPC.TLGroupCall)?.recordStartDate ?: 0

val TLRPC.GroupCall?.canChangeJoinMuted: Boolean
	get() = (this as? TLRPC.TLGroupCall)?.canChangeJoinMuted ?: false

var TLRPC.GroupCall?.joinMuted: Boolean
	get() = (this as? TLRPC.TLGroupCall)?.joinMuted ?: false
	set(value) {
		(this as? TLRPC.TLGroupCall)?.joinMuted = value
	}

val TLRPC.GroupCall?.recordVideoActive: Boolean
	get() = (this as? TLRPC.TLGroupCall)?.recordVideoActive ?: false

var TLRPC.GroupCall?.scheduleStartSubscribed: Boolean
	get() = (this as? TLRPC.TLGroupCall)?.scheduleStartSubscribed ?: false
	set(value) {
		(this as? TLRPC.TLGroupCall)?.scheduleStartSubscribed = value
	}

val TLRPC.User.accessHash: Long
	get() = (this as? TLRPC.TLUser)?.accessHash ?: 0L

val TLRPC.ChatParticipants.selfParticipant: TLRPC.ChatParticipant?
	get() = (this as? TLRPC.TLChatParticipantsForbidden)?.selfParticipant

val TLRPC.User.isPublic: Boolean
	get() = (this as? TLRPC.TLUser)?.isPublic ?: false

val TLRPC.WebPage.displayUrl: String?
	get() = (this as? TLRPC.TLWebPage)?.displayUrl

val TLRPC.MessageMedia.nopremium: Boolean
	get() = (this as? TLRPC.TLMessageMediaDocument)?.nopremium ?: false

val TLRPC.WebPage.author: String?
	get() = (this as? TLRPC.TLWebPage)?.author

val TLRPC.ChatFull.bannedCount: Int
	get() = (this as? TLRPC.TLChannelFullLayer3)?.bannedCount ?: 0

val TLRPC.ChannelParticipant.kickedBy: Long
	get() = (this as? TLRPC.TLChannelParticipantBanned)?.kickedBy ?: 0L

var TLRPC.ChannelParticipant.promotedBy: Long
	get() = (this as? TLRPC.TLChannelParticipantAdmin)?.promotedBy ?: 0L
	set(value) {
		if (this is TLRPC.TLChannelParticipantAdmin) {
			promotedBy = value
		}
	}

val TLRPC.ChannelParticipant.canEdit: Boolean
	get() = (this as? TLRPC.TLChannelParticipantAdmin)?.canEdit ?: false

val TLRPC.User.business: Boolean
	get() = (this as? TLRPC.TLUser)?.business ?: false

val TLRPC.DialogFilter.includePeers: MutableList<TLRPC.InputPeer>?
	get() = (this as? TLRPC.TLDialogFilter)?.includePeers

val TLRPC.DialogFilter.excludePeers: MutableList<TLRPC.InputPeer>?
	get() = (this as? TLRPC.TLDialogFilter)?.excludePeers

val TLRPC.User.botDescription: String?
	get() = (this as? TLRPC.TLUser)?.botDescription

val TLRPC.WallPaper.accessHash: Long
	get() = (this as? TLRPC.TLWallPaper)?.accessHash ?: 0L

val TLRPC.WallPaper.slug: String?
	get() = (this as? TLRPC.TLWallPaper)?.slug

val TLRPC.TLError.Companion.UNKNOWN_ERROR: Int
	get() = 0

fun TLObject.postDeserialize() {
	when (this) {
		is TLRPC.TLStickerSet -> {
			for (size in thumbs) {
				if (size.location == null) {
					if (id != 0L && !size.type.isNullOrEmpty()) {
						val loc = TLRPC.TLFileLocation()
						loc.volumeId = -id
						loc.localId = 2000 + size.type!![0].code

						size.location = loc
					}
					else {
						size.location = TLRPC.TLFileLocationUnavailable()
					}
				}
			}
		}

		is TLRPC.TLDocument -> {
			for (size in thumbs) {
				if (size.location == null) {
					if (id != 0L && !size.type.isNullOrEmpty()) {
						val loc = TLRPC.TLFileLocation()
						loc.volumeId = -id
						loc.localId = 1000 + size.type!![0].code

						size.location = loc
					}
					else {
						size.location = TLRPC.TLFileLocationUnavailable()
					}
				}
			}

			for (size in videoThumbs) {
				if (size.location == null) {
					if (id != 0L && !size.type.isNullOrEmpty()) {
						val loc = TLRPC.TLFileLocation()
						loc.volumeId = -id
						loc.localId = 1000 + size.type!![0].code

						size.location = loc
					}
					else {
						size.location = TLRPC.TLFileLocationUnavailable()
					}
				}
			}
		}

		is TLRPC.TLPhoto -> {
			for (size in sizes) {
				if (size.location == null) {
					if (id != 0L && !size.type.isNullOrEmpty()) {
						val loc = TLRPC.TLFileLocation()
						loc.volumeId = -id
						loc.localId = size.type!![0].code

						size.location = loc
					}
					else {
						size.location = TLRPC.TLFileLocationUnavailable()
					}
				}
			}

			for (size in videoSizes) {
				if (size.location == null) {
					if (id != 0L && !size.type.isNullOrEmpty()) {
						val loc = TLRPC.TLFileLocation()
						loc.volumeId = -id
						loc.localId = size.type!![0].code

						size.location = loc
					}
					else {
						size.location = TLRPC.TLFileLocationUnavailable()
					}
				}
			}
		}

		is TLRPC.TLPhotoSizeProgressiveLayer127 -> {
			if (sizes.isNotEmpty()) {
				size = sizes.last()
			}
		}

		is TLRPC.TLPhotoSizeProgressive -> {
			if (sizes.isNotEmpty()) {
				size = sizes.last()
			}
		}
	}
}
