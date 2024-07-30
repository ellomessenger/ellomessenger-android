/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Oleksandr Nahornyi, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import android.text.TextUtils
import org.telegram.messenger.Utilities
import org.telegram.messenger.utils.toLongHash
import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.MessageAction
import org.telegram.tgnet.tlrpc.MessageEntity
import org.telegram.tgnet.TLRPC.MessageFwdHeader
import org.telegram.tgnet.TLRPC.MessageMedia
import org.telegram.tgnet.TLRPC.MessageReplies
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.TLRPC.ReplyMarkup
import org.telegram.tgnet.TLRPC.TL_messageEmpty
import org.telegram.tgnet.TLRPC.TL_messageEmpty_layer122
import org.telegram.tgnet.TLRPC.TL_messageForwarded_old
import org.telegram.tgnet.TLRPC.TL_messageForwarded_old2
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument_layer68
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument_layer74
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument_old
import org.telegram.tgnet.TLRPC.TL_messageMediaEmpty
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto_layer68
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto_layer74
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto_old
import org.telegram.tgnet.TLRPC.TL_messageMediaWebPage
import org.telegram.tgnet.TLRPC.TL_messageReplyHeader
import org.telegram.tgnet.TLRPC.TL_messageService
import org.telegram.tgnet.TLRPC.TL_messageService_layer118
import org.telegram.tgnet.TLRPC.TL_messageService_layer123
import org.telegram.tgnet.TLRPC.TL_messageService_layer48
import org.telegram.tgnet.TLRPC.TL_messageService_old
import org.telegram.tgnet.TLRPC.TL_messageService_old2
import org.telegram.tgnet.TLRPC.TL_message_layer104
import org.telegram.tgnet.TLRPC.TL_message_layer104_2
import org.telegram.tgnet.TLRPC.TL_message_layer104_3
import org.telegram.tgnet.TLRPC.TL_message_layer117
import org.telegram.tgnet.TLRPC.TL_message_layer118
import org.telegram.tgnet.TLRPC.TL_message_layer123
import org.telegram.tgnet.TLRPC.TL_message_layer131
import org.telegram.tgnet.TLRPC.TL_message_layer135
import org.telegram.tgnet.TLRPC.TL_message_layer47
import org.telegram.tgnet.TLRPC.TL_message_layer68
import org.telegram.tgnet.TLRPC.TL_message_layer72
import org.telegram.tgnet.TLRPC.TL_message_old
import org.telegram.tgnet.TLRPC.TL_message_old2
import org.telegram.tgnet.TLRPC.TL_message_old3
import org.telegram.tgnet.TLRPC.TL_message_old4
import org.telegram.tgnet.TLRPC.TL_message_old5
import org.telegram.tgnet.TLRPC.TL_message_old6
import org.telegram.tgnet.TLRPC.TL_message_old7
import org.telegram.tgnet.TLRPC.TL_message_secret
import org.telegram.tgnet.TLRPC.TL_message_secret_layer72
import org.telegram.tgnet.TLRPC.TL_message_secret_old
import org.telegram.tgnet.TLRPC.TL_restrictionReason
import java.util.Objects

@Suppress("PropertyName")
abstract class Message : TLObject() {
	@JvmField
	var id = 0

	@JvmField
	var from_id: Peer? = null

	@JvmField
	var peer_id: Peer? = null

	@JvmField
	var date = 0

	var expire_date = 0

	@JvmField
	var action: MessageAction? = null

	@JvmField
	var message: String? = null

	@JvmField
	var media: MessageMedia? = null

	@JvmField
	var flags = 0

	@JvmField
	var mentioned = false

	@JvmField
	var media_unread = false

	@JvmField
	var out = false

	@JvmField
	var unread = false

	@JvmField
	var entities = ArrayList<MessageEntity>()

	@JvmField
	var via_bot_name: String? = null

	@JvmField
	var reply_markup: ReplyMarkup? = null

	@JvmField
	var views = 0

	@JvmField
	var forwards = 0

	@JvmField
	var replies: MessageReplies? = null

	@JvmField
	var edit_date = 0

	@JvmField
	var silent = false

	@JvmField
	var post = false

	@JvmField
	var from_scheduled = false

	@JvmField
	var legacy = false

	@JvmField
	var edit_hide = false

	@JvmField
	var pinned = false

	@JvmField
	var fwd_from: MessageFwdHeader? = null

	@JvmField
	var via_bot_id: Long = 0

	@JvmField
	var reply_to: TL_messageReplyHeader? = null

	@JvmField
	var post_author: String? = null

	var realGroupId: Long = 0 // MARK: set as protected to use it along with media_hash
	// protected set

	var originalReactions: TL_messageReactions? = null
	// protected set

	@JvmField
	var restriction_reason = ArrayList<TL_restrictionReason>()

	@JvmField
	var ttl_period = 0

	@JvmField
	var noforwards = false

	@JvmField
	var send_state = 0 //custom

	var fwd_msg_id = 0 //custom

	@JvmField
	var attachPath: String? = "" //custom

	@JvmField
	var params: HashMap<String, String>? = null //custom

	@JvmField
	var random_id: Long = 0 //custom

	@JvmField
	var local_id = 0 //custom

	@JvmField
	var dialog_id: Long = 0 //custom

	@JvmField
	var ttl = 0 //custom

	@JvmField
	var destroyTime = 0 //custom

	@JvmField
	var layer = 0 //custom

	@JvmField
	var seq_in = 0 //custom

	@JvmField
	var seq_out = 0 //custom

	@JvmField
	var with_my_score = false

	@JvmField
	var replyMessage: Message? = null //custom

	var reqId = 0 //custom

	@JvmField
	var realId = 0 //custom

	@JvmField
	var stickerVerified = 1 //custom

	@JvmField
	var isThreadMessage = false //custom

	@JvmField
	var voiceTranscription: String? = null //custom

	@JvmField
	var voiceTranscriptionOpen = false //custom

	@JvmField
	var voiceTranscriptionRated = false //custom

	@JvmField
	var voiceTranscriptionFinal = false //custom

	@JvmField
	var voiceTranscriptionId: Long = 0 //custom

	@JvmField
	var premiumEffectWasPlayed = false //custom

	@JvmField
	var is_media_sale = false

	@JvmField
	var title: String? = null

	@JvmField
	var price = 0.0

	@JvmField
	var quantity = 0

	@JvmField
	var is_paid = false

	@JvmField
	var is_media_sale_info = false

	var mediaHash: String? = null
	// protected set

	@JvmField
	var likes = 0

	@JvmField
	var is_liked = false

	private var likeReactionCount: ReactionCount? = null

	var reactions: TL_messageReactions?
		get() {
			// MARK: uncomment to enable likes in chat
//			if (peer_id !is TL_peerChannel) {
//				return originalReactions
//			}
//
//			if (originalReactions == null) {
//				originalReactions = TL_messageReactions()
//			}
//
//			originalReactions?.can_see_list = true
//
//			if (likeReactionCount == null) {
//				likeReactionCount = TL_reactionCount()
//
//				val r = TL_reactionEmoji()
//				r.emoticon = "❤️" //Emoji.fixEmoji("❤️")
//
//				likeReactionCount?.reaction = r
//			}
//
//			likeReactionCount?.chosen = is_liked
//			likeReactionCount?.count = likes
//
//			if (originalReactions?.results?.contains(likeReactionCount) == false) {
//				originalReactions?.results?.add(likeReactionCount)
//			}

			return originalReactions
		}
		set(value) {
			originalReactions = value
		}

	var groupId: Long
		get() = mediaHash?.toLongHash() ?: realGroupId
		set(groupId) {
			realGroupId = groupId
		}

	// TODO: improve comparison logic to properly compare attachments
	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is Message) {
			return false
		}

		return id == other.id && date == other.date && flags == other.flags && views == other.views && forwards == other.forwards && post == other.post && via_bot_id == other.via_bot_id && realGroupId == other.realGroupId && noforwards == other.noforwards && realId == other.realId && isThreadMessage == other.isThreadMessage && from_id == other.from_id && peer_id == other.peer_id && message == other.message && media == other.media && post_author == other.post_author && mediaHash == other.mediaHash
	}

	override fun hashCode(): Int {
		return Objects.hash(id, from_id, peer_id, date, message, media, flags, views, forwards, post, via_bot_id, post_author, realGroupId, ttl_period, noforwards, realId, isThreadMessage, mediaHash)
	}

	fun readAttachPath(stream: AbstractSerializedData, currentUserId: Long) {
		val hasMedia = media != null && media !is TL_messageMediaEmpty && media !is TL_messageMediaWebPage
		val fixCaption = !message.isNullOrEmpty() && (media is TL_messageMediaPhoto_old || media is TL_messageMediaPhoto_layer68 || media is TL_messageMediaPhoto_layer74 || media is TL_messageMediaDocument_old || media is TL_messageMediaDocument_layer68 || media is TL_messageMediaDocument_layer74) && (message?.startsWith("-1") == true)

		if ((out || peer_id != null && from_id != null && peer_id!!.user_id != 0L && peer_id!!.user_id == from_id!!.user_id && from_id!!.user_id == currentUserId) && (id < 0 || hasMedia || send_state == 3) || legacy) {
			if (hasMedia && fixCaption) {
				if (message!!.length > 6 && message!![2] == '_') {
					params = HashMap()
					params!!["ve"] = message!!
				}

				if (params != null || message!!.length == 2) {
					message = ""
				}
			}

			if (stream.remaining() > 0) {
				attachPath = stream.readString(false)

				if (attachPath != null) {
					if ((id < 0 || send_state == 3 || legacy) && attachPath!!.startsWith("||")) {
						val args = attachPath?.split("\\|\\|".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()

						if (!args.isNullOrEmpty()) {
							if (params == null) {
								params = HashMap()
							}

							for (a in 1 until args.size - 1) {
								val args2 = args[a].split("\\|=\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

								if (args2.size == 2) {
									params!![args2[0]] = args2[1]
								}
							}

							attachPath = args[args.size - 1].trim()

							if (legacy) {
								layer = Utilities.parseInt(params!!["legacy_layer"])
							}
						}
					}
					else {
						attachPath = attachPath?.trim()
					}
				}
			}
		}

		if (flags and TLRPC.MESSAGE_FLAG_FWD != 0 && id < 0) {
			fwd_msg_id = stream.readInt32(false)
		}
	}

	protected fun writeAttachPath(stream: AbstractSerializedData) {
		if (this is TL_message_secret || this is TL_message_secret_layer72) {
			var path = if (attachPath != null) attachPath!! else ""

			if (send_state == 1 && params != null && params!!.size > 0) {
				for ((key, value) in params!!) {
					path = "$key|=|$value||$path"
				}

				path = "||$path"
			}

			stream.writeString(path)
		}
		else {
			var path = if (!TextUtils.isEmpty(attachPath)) attachPath else " "

			if (legacy) {
				if (params == null) {
					params = HashMap()
				}

				layer = TLRPC.LAYER

				params!!["legacy_layer"] = "" + TLRPC.LAYER
			}

			if ((id < 0 || send_state == 3 || legacy) && params != null && params!!.size > 0) {
				for ((key, value) in params!!) {
					path = "$key|=|$value||$path"
				}

				path = "||$path"
			}

			stream.writeString(path)

			if (flags and TLRPC.MESSAGE_FLAG_FWD != 0 && id < 0) {
				stream.writeInt32(fwd_msg_id)
			}
		}
	}

	companion object {
		@JvmStatic
		fun TLdeserialize(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): Message? {
			val result = when (constructor) {
				0x1d86f70e -> TL_messageService_old2()
				-0x5854e66f -> TL_message_old3()
				-0x3cf9fcdb -> TL_message_old4()
				0x555555fa -> TL_message_secret()
				0x555555f9 -> TL_message_secret_layer72()
				-0x6f2223ef -> TL_message_layer72()
				-0x3f641ba1 -> TL_message_layer68()
				-0x366d1ea4 -> TL_message_layer47()
				0x5ba66c13 -> TL_message_old7()
				-0x3f9469f9 -> TL_messageService_layer48()
				-0x7c1a21ac -> TL_messageEmpty_layer122()
				0x2bebfa86 -> TL_message_old6()
				0x44f9b43d -> TL_message_layer104()
				-0x6f59357c -> TL_messageEmpty()
				0x1c9b1027 -> TL_message_layer104_2()
				-0x5c9818ea -> TL_messageForwarded_old2()
				0x5f46804 -> TL_messageForwarded_old()
				0x567699b3 -> TL_message_old2()
				-0x60729f45 -> TL_messageService_old()
				0x22eb6aba -> TL_message_old()
				0x555555F8 -> TL_message_secret_old()
				-0x6876253c -> TL_message_layer104_3()
				0x452c0e65 -> TL_message_layer117()
				-0xad19481 -> TL_message_layer118()
				0x58ae39c9 -> TL_message_layer123()
				-0x431c7c2e -> TL_message_layer131()
				-0x7a29341e -> TL_message_layer135()
				0x38116ee0 -> TL_message()
				-0x61e65e0a -> TL_messageService_layer118()
				0x286fa604 -> TL_messageService_layer123()
				0x2b085862 -> TL_messageService()
				-0xf87eb38 -> TL_message_old5()
				else -> null
			}

			if (result == null && exception) {
				throw RuntimeException(String.format("can't parse magic %x in Message", constructor))
			}

			if (result != null) {
				result.readParams(stream, exception)

				if (result.from_id == null) {
					result.from_id = result.peer_id
				}
			}

			return result
		}
	}
}
