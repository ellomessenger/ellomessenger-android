/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger.messageobject

import android.graphics.Paint.FontMetricsInt
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Base64
import androidx.collection.LongSparseArray
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import org.telegram.PhoneFormat.PhoneFormat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BillingController
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.Emoji.EmojiSpan
import org.telegram.messenger.EmojiData
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.SvgHelper.SvgDrawable
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.messenger.Utilities
import org.telegram.messenger.VideoEditedInfo
import org.telegram.messenger.WebFile
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.ringtone.RingtoneDataStore
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInlineResult
import org.telegram.tgnet.TLRPC.ChannelParticipant
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatInvite
import org.telegram.tgnet.TLRPC.ChatReactions
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.Message
import org.telegram.tgnet.TLRPC.MessageEntity
import org.telegram.tgnet.TLRPC.MessageMedia
import org.telegram.tgnet.TLRPC.PageBlock
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.StickerSetCovered
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEvent
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangeAbout
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangeAvailableReactions
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangeHistoryTTL
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangeLinkedChat
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangeLocation
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangePhoto
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangeStickerSet
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangeTitle
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionChangeUsername
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionDefaultBannedRights
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionDeleteMessage
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionDiscardGroupCall
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionEditMessage
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionExportedInviteDelete
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionExportedInviteEdit
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionExportedInviteRevoke
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantInvite
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantJoin
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantJoinByInvite
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantJoinByRequest
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantLeave
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantMute
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantToggleAdmin
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantToggleBan
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantUnmute
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionParticipantVolume
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionSendMessage
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionStartGroupCall
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionStopPoll
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionToggleGroupCallSetting
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionToggleInvites
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionToggleNoForwards
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionTogglePreHistoryHidden
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionToggleSignatures
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionToggleSlowMode
import org.telegram.tgnet.TLRPC.TLChannelAdminLogEventActionUpdatePinned
import org.telegram.tgnet.TLRPC.TLChannelLocation
import org.telegram.tgnet.TLRPC.TLChannelLocationEmpty
import org.telegram.tgnet.TLRPC.TLChannelParticipant
import org.telegram.tgnet.TLRPC.TLChannelParticipantAdmin
import org.telegram.tgnet.TLRPC.TLChannelParticipantBanned
import org.telegram.tgnet.TLRPC.TLChannelParticipantCreator
import org.telegram.tgnet.TLRPC.TLChatAdminRights
import org.telegram.tgnet.TLRPC.TLChatBannedRights
import org.telegram.tgnet.TLRPC.TLChatInviteExported
import org.telegram.tgnet.TLRPC.TLChatInvitePublicJoinRequests
import org.telegram.tgnet.TLRPC.TLChatReactionsAll
import org.telegram.tgnet.TLRPC.TLChatReactionsSome
import org.telegram.tgnet.TLRPC.TLDocument
import org.telegram.tgnet.TLRPC.TLDocumentAttributeAnimated
import org.telegram.tgnet.TLRPC.TLDocumentAttributeAudio
import org.telegram.tgnet.TLRPC.TLDocumentAttributeCustomEmoji
import org.telegram.tgnet.TLRPC.TLDocumentAttributeHasStickers
import org.telegram.tgnet.TLRPC.TLDocumentAttributeImageSize
import org.telegram.tgnet.TLRPC.TLDocumentAttributeSticker
import org.telegram.tgnet.TLRPC.TLDocumentAttributeVideo
import org.telegram.tgnet.TLRPC.TLDocumentEmpty
import org.telegram.tgnet.TLRPC.TLFileLocationUnavailable
import org.telegram.tgnet.TLRPC.TLGame
import org.telegram.tgnet.TLRPC.TLInputMessageEntityMentionName
import org.telegram.tgnet.TLRPC.TLInputStickerSetEmpty
import org.telegram.tgnet.TLRPC.TLInputStickerSetID
import org.telegram.tgnet.TLRPC.TLInputStickerSetShortName
import org.telegram.tgnet.TLRPC.TLKeyboardButtonBuy
import org.telegram.tgnet.TLRPC.TLMessage
import org.telegram.tgnet.TLRPC.TLMessageActionBotAllowed
import org.telegram.tgnet.TLRPC.TLMessageActionChatAddUser
import org.telegram.tgnet.TLRPC.TLMessageActionChatCreate
import org.telegram.tgnet.TLRPC.TLMessageActionChatDeletePhoto
import org.telegram.tgnet.TLRPC.TLMessageActionChatDeleteUser
import org.telegram.tgnet.TLRPC.TLMessageActionChatEditPhoto
import org.telegram.tgnet.TLRPC.TLMessageActionChatEditTitle
import org.telegram.tgnet.TLRPC.TLMessageActionChatJoinedByLink
import org.telegram.tgnet.TLRPC.TLMessageActionChatJoinedByRequest
import org.telegram.tgnet.TLRPC.TLMessageActionContactSignUp
import org.telegram.tgnet.TLRPC.TLMessageActionCustomAction
import org.telegram.tgnet.TLRPC.TLMessageActionEmpty
import org.telegram.tgnet.TLRPC.TLMessageActionGeoProximityReached
import org.telegram.tgnet.TLRPC.TLMessageActionGiftPremium
import org.telegram.tgnet.TLRPC.TLMessageActionGroupCall
import org.telegram.tgnet.TLRPC.TLMessageActionGroupCallScheduled
import org.telegram.tgnet.TLRPC.TLMessageActionHistoryClear
import org.telegram.tgnet.TLRPC.TLMessageActionInviteToGroupCall
import org.telegram.tgnet.TLRPC.TLMessageActionPhoneCall
import org.telegram.tgnet.TLRPC.TLMessageActionSecureValuesSent
import org.telegram.tgnet.TLRPC.TLMessageActionSetChatTheme
import org.telegram.tgnet.TLRPC.TLMessageActionSetMessagesTTL
import org.telegram.tgnet.TLRPC.TLMessageActionWebViewDataSent
import org.telegram.tgnet.TLRPC.TLMessageEmpty
import org.telegram.tgnet.TLRPC.TLMessageEntityBankCard
import org.telegram.tgnet.TLRPC.TLMessageEntityBlockquote
import org.telegram.tgnet.TLRPC.TLMessageEntityBold
import org.telegram.tgnet.TLRPC.TLMessageEntityBotCommand
import org.telegram.tgnet.TLRPC.TLMessageEntityCashtag
import org.telegram.tgnet.TLRPC.TLMessageEntityCode
import org.telegram.tgnet.TLRPC.TLMessageEntityCustomEmoji
import org.telegram.tgnet.TLRPC.TLMessageEntityEmail
import org.telegram.tgnet.TLRPC.TLMessageEntityHashtag
import org.telegram.tgnet.TLRPC.TLMessageEntityItalic
import org.telegram.tgnet.TLRPC.TLMessageEntityMention
import org.telegram.tgnet.TLRPC.TLMessageEntityMentionName
import org.telegram.tgnet.TLRPC.TLMessageEntityPhone
import org.telegram.tgnet.TLRPC.TLMessageEntityPre
import org.telegram.tgnet.TLRPC.TLMessageEntitySpoiler
import org.telegram.tgnet.TLRPC.TLMessageEntityStrike
import org.telegram.tgnet.TLRPC.TLMessageEntityTextUrl
import org.telegram.tgnet.TLRPC.TLMessageEntityUnderline
import org.telegram.tgnet.TLRPC.TLMessageEntityUrl
import org.telegram.tgnet.TLRPC.TLMessageExtendedMedia
import org.telegram.tgnet.TLRPC.TLMessageExtendedMediaPreview
import org.telegram.tgnet.TLRPC.TLMessageMediaContact
import org.telegram.tgnet.TLRPC.TLMessageMediaDice
import org.telegram.tgnet.TLRPC.TLMessageMediaDocument
import org.telegram.tgnet.TLRPC.TLMessageMediaEmpty
import org.telegram.tgnet.TLRPC.TLMessageMediaGame
import org.telegram.tgnet.TLRPC.TLMessageMediaGeo
import org.telegram.tgnet.TLRPC.TLMessageMediaGeoLive
import org.telegram.tgnet.TLRPC.TLMessageMediaInvoice
import org.telegram.tgnet.TLRPC.TLMessageMediaPhoto
import org.telegram.tgnet.TLRPC.TLMessageMediaPoll
import org.telegram.tgnet.TLRPC.TLMessageMediaUnsupported
import org.telegram.tgnet.TLRPC.TLMessageMediaVenue
import org.telegram.tgnet.TLRPC.TLMessageMediaWebPage
import org.telegram.tgnet.TLRPC.TLMessagePeerReaction
import org.telegram.tgnet.TLRPC.TLMessageReactions
import org.telegram.tgnet.TLRPC.TLMessageService
import org.telegram.tgnet.TLRPC.TLPageBlockCollage
import org.telegram.tgnet.TLRPC.TLPageBlockPhoto
import org.telegram.tgnet.TLRPC.TLPageBlockSlideshow
import org.telegram.tgnet.TLRPC.TLPageBlockVideo
import org.telegram.tgnet.TLRPC.TLPeerChannel
import org.telegram.tgnet.TLRPC.TLPeerChat
import org.telegram.tgnet.TLRPC.TLPeerUser
import org.telegram.tgnet.TLRPC.TLPhoneCallDiscardReasonBusy
import org.telegram.tgnet.TLRPC.TLPhoneCallDiscardReasonMissed
import org.telegram.tgnet.TLRPC.TLPhoto
import org.telegram.tgnet.TLRPC.TLPhotoCachedSize
import org.telegram.tgnet.TLRPC.TLPhotoEmpty
import org.telegram.tgnet.TLRPC.TLPhotoSizeEmpty
import org.telegram.tgnet.TLRPC.TLPhotoStrippedSize
import org.telegram.tgnet.TLRPC.TLPollAnswer
import org.telegram.tgnet.TLRPC.TLPollResults
import org.telegram.tgnet.TLRPC.TLReactionCount
import org.telegram.tgnet.TLRPC.TLReactionCustomEmoji
import org.telegram.tgnet.TLRPC.TLReactionEmoji
import org.telegram.tgnet.TLRPC.TLReplyInlineMarkup
import org.telegram.tgnet.TLRPC.TLSecureValueTypeAddress
import org.telegram.tgnet.TLRPC.TLSecureValueTypeBankStatement
import org.telegram.tgnet.TLRPC.TLSecureValueTypeDriverLicense
import org.telegram.tgnet.TLRPC.TLSecureValueTypeEmail
import org.telegram.tgnet.TLRPC.TLSecureValueTypeIdentityCard
import org.telegram.tgnet.TLRPC.TLSecureValueTypeInternalPassport
import org.telegram.tgnet.TLRPC.TLSecureValueTypePassport
import org.telegram.tgnet.TLRPC.TLSecureValueTypePassportRegistration
import org.telegram.tgnet.TLRPC.TLSecureValueTypePersonalDetails
import org.telegram.tgnet.TLRPC.TLSecureValueTypePhone
import org.telegram.tgnet.TLRPC.TLSecureValueTypeRentalAgreement
import org.telegram.tgnet.TLRPC.TLSecureValueTypeTemporaryRegistration
import org.telegram.tgnet.TLRPC.TLSecureValueTypeUtilityBill
import org.telegram.tgnet.TLRPC.TLStickerSetFullCovered
import org.telegram.tgnet.TLRPC.TLWebPage
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.TLRPC.WebDocument
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.tgnet.action
import org.telegram.tgnet.channelId
import org.telegram.tgnet.chatId
import org.telegram.tgnet.dcId
import org.telegram.tgnet.description
import org.telegram.tgnet.entities
import org.telegram.tgnet.extendedMedia
import org.telegram.tgnet.fwdFrom
import org.telegram.tgnet.game
import org.telegram.tgnet.media
import org.telegram.tgnet.message
import org.telegram.tgnet.period
import org.telegram.tgnet.photo
import org.telegram.tgnet.reactions
import org.telegram.tgnet.replies
import org.telegram.tgnet.replyMarkup
import org.telegram.tgnet.sizes
import org.telegram.tgnet.thumbs
import org.telegram.tgnet.userId
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble
import org.telegram.ui.Components.Reactions.ReactionsUtils
import org.telegram.ui.Components.Reactions.VisibleReaction
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.TextStyleSpan.TextStyleRun
import org.telegram.ui.Components.TranscribeButton
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.Components.URLSpanBotCommand
import org.telegram.ui.Components.URLSpanBrowser
import org.telegram.ui.Components.URLSpanMono
import org.telegram.ui.Components.URLSpanNoUnderline
import org.telegram.ui.Components.URLSpanNoUnderlineBold
import org.telegram.ui.Components.URLSpanNoUnderlineBrowser
import org.telegram.ui.Components.URLSpanReplacement
import org.telegram.ui.Components.URLSpanUserMention
import org.telegram.ui.Components.spoilers.SpoilerEffect
import java.io.File
import java.net.URLEncoder
import java.util.AbstractMap
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalContracts::class)
open class MessageObject {
	var localGroupId: Long = 0
	var localSentGroupId: Long = 0
	private var localSupergroup: Boolean = false
	private var cachedIsSupergroup: Boolean? = null
	var localEdit: Boolean = false
	var emojiAnimatedSticker: TLRPC.Document? = null
	var emojiAnimatedStickerId: Long? = null
	var emojiAnimatedStickerColor: String? = null
	var linkDescription: CharSequence? = null
	var reactionsLastCheckTime: Long = 0
	var extendedMediaLastCheckTime: Long = 0
	var reactionsChanged: Boolean = false
	var isReactionPush: Boolean = false
	var dateKey: String? = null
	var gifState: Float = 0f
	var photoThumbsObject2: TLObject? = null
	var photoThumbs2: ArrayList<PhotoSize?>? = null
	var shouldRemoveVideoEditedInfo: Boolean = false
	var viewsReloaded: Boolean = false
	var pollVisibleOnScreen: Boolean = false
	var pollLastCheckTime: Long = 0
	var wantedBotKeyboardWidth: Int = 0
	var botButtonsLayout: StringBuilder? = null
	var isRestrictedMessage: Boolean = false
	var loadedFileSize: Long = 0
	var isSpoilersRevealed: Boolean = false
	var sponsoredId: ByteArray? = null
	var sponsoredChannelPost: Int = 0
	var sponsoredChatInvite: ChatInvite? = null
	var sponsoredChatInviteHash: String? = null
	var botStartParam: String? = null
	var animateComments: Boolean = false
	var loadingCancelled: Boolean = false
	var stableId: Int = 0
	var wasUnread: Boolean = false
	var playedGiftAnimation: Boolean = false
	var hadAnimationNotReadyLoading: Boolean = false
	var cancelEditing: Boolean = false
	var checkedVotes: MutableList<TLPollAnswer>? = null
	var editingMessageSearchWebPage: Boolean = false
	private var webPageDescriptionEntities: MutableList<MessageEntity>? = null
	var previousMessage: String? = null
	var previousMedia: MessageMedia? = null
	var previousMessageEntities: MutableList<MessageEntity>? = null
	var previousAttachPath: String? = null
	var pathThumb: SvgDrawable? = null
	var lastLineWidth: Int = 0
	var textWidth: Int = 0
	var textHeight: Int = 0
	var hasRtl: Boolean = false
	var linesCount: Int = 0
	var sendAnimationData: SendAnimationData? = null
	var vCardData: CharSequence? = null
	var messageTrimmedToHighlight: String? = null
	private var emojiAnimatedStickerLoading = false
	private var isRoundVideoCached = 0
	private var hasUnwrappedEmoji = false
	private var totalAnimatedEmojiCount = 0
	private var layoutCreated = false
	private var generatedWithMinSize = 0
	private var generatedWithDensity = 0f

	@JvmField
	var localType: Int = 0

	@JvmField
	var localName: String? = null

	@JvmField
	var localUserName: String? = null

	@JvmField
	var localChannel: Boolean = false

	@JvmField
	var messageOwner: Message? = null

	@JvmField
	var messageText: CharSequence? = null

	@JvmField
	var caption: CharSequence? = null

	@JvmField
	var youtubeDescription: CharSequence? = null

	@JvmField
	var replyMessageObject: MessageObject? = null

	@JvmField
	var type: Int = 1000

	@JvmField
	var customName: String? = null

	@JvmField
	var putInDownloadsStore: Boolean = false

	@JvmField
	var isDownloadingFile: Boolean = false

	@JvmField
	var forcePlayEffect: Boolean = false

	@JvmField
	var eventId: Long = 0

	@JvmField
	var contentType: Int = 0

	@JvmField
	var monthKey: String? = null

	@JvmField
	var deleted: Boolean = false

	@JvmField
	var audioProgress: Float = 0f

	@JvmField
	var forceSeekTo: Float = -1f

	@JvmField
	var audioProgressMs: Int = 0

	@JvmField
	var bufferedProgress: Float = 0f

	@JvmField
	var audioProgressSec: Int = 0

	@JvmField
	var audioPlayerDuration: Int = 0

	@JvmField
	var isDateObject: Boolean = false

	@JvmField
	var photoThumbsObject: TLObject? = null

	@JvmField
	var photoThumbs: ArrayList<PhotoSize?>? = null

	@JvmField
	var videoEditedInfo: VideoEditedInfo? = null

	@JvmField
	var attachPathExists: Boolean = false

	@JvmField
	var mediaExists: Boolean = false

	@JvmField
	var resendAsIs: Boolean = false

	@JvmField
	var customReplyName: String? = null

	@JvmField
	var useCustomPhoto: Boolean = false

	@JvmField
	var scheduled: Boolean = false

	@JvmField
	var preview: Boolean = false

	@JvmField
	var editingMessage: CharSequence? = null

	@JvmField
	var editingMessageEntities: List<MessageEntity>? = null

	@JvmField
	var strippedThumb: BitmapDrawable? = null

	@JvmField
	var currentAccount: Int = 0

	@JvmField
	var currentEvent: TLChannelAdminLogEvent? = null

	@JvmField
	var forceUpdate: Boolean = false

	@JvmField
	var textXOffset: Float = 0f

	@JvmField
	var wasJustSent: Boolean = false

	@JvmField
	var highlightedWords: ArrayList<String>? = null

	@JvmField
	var parentWidth: Int = 0

	@JvmField
	var mediaThumb: ImageLocation? = null

	@JvmField
	var mediaSmallThumb: ImageLocation? = null

	// forwarding preview params
	@JvmField
	var hideSendersName: Boolean = false

	@JvmField
	var sendAsPeer: Peer? = null

	@JvmField
	var customAvatarDrawable: Drawable? = null

	@JvmField
	var textLayoutBlocks: ArrayList<TextLayoutBlock>? = null

	var emojiOnlyCount: Int = 0
		private set

	override fun equals(other: Any?): Boolean {
		if (other !is MessageObject) {
			return false
		}

		if (this === other) {
			return true
		}

		return other.messageOwner == this.messageOwner
	}

	// MARK: if you uncomment this, then the grouping of messages will be broken
//	override fun hashCode(): Int {
//		return Objects.hash(messageOwner)
//	}

	constructor(accountNum: Int, message: Message, formattedMessage: String?, name: String?, userName: String?, localMessage: Boolean, isChannel: Boolean, supergroup: Boolean, edit: Boolean) {
		localType = if (localMessage) 2 else 1
		currentAccount = accountNum
		localName = name
		localUserName = userName
		messageText = formattedMessage
		messageOwner = message
		localChannel = isChannel
		localSupergroup = supergroup
		localEdit = edit
	}

	constructor(accountNum: Int, message: Message, users: AbstractMap<Long, User>?, generateLayout: Boolean, checkMediaExists: Boolean) : this(accountNum, message, users, null, generateLayout, checkMediaExists)

	constructor(accountNum: Int, message: Message, users: LongSparseArray<User>?, generateLayout: Boolean, checkMediaExists: Boolean) : this(accountNum, message, users, null, generateLayout, checkMediaExists)

	constructor(accountNum: Int, message: Message, generateLayout: Boolean, checkMediaExists: Boolean) : this(accountNum, message, null, null, null, null, null, generateLayout, checkMediaExists, 0)

	constructor(accountNum: Int, message: Message, replyToMessage: MessageObject?, generateLayout: Boolean, checkMediaExists: Boolean) : this(accountNum, message, replyToMessage, null, null, null, null, generateLayout, checkMediaExists, 0)

	constructor(accountNum: Int, message: Message, users: LongSparseArray<User>?, chats: LongSparseArray<Chat>?, generateLayout: Boolean, checkMediaExists: Boolean) : this(accountNum, message, null, null, null, users, chats, generateLayout, checkMediaExists, 0)

	@JvmOverloads
	constructor(accountNum: Int, message: Message, users: AbstractMap<Long, User>?, chats: AbstractMap<Long, Chat>?, generateLayout: Boolean, checkMediaExists: Boolean, eid: Long = 0) : this(accountNum, message, null, users, chats, null, null, generateLayout, checkMediaExists, eid)

	constructor(accountNum: Int, message: Message, replyToMessage: MessageObject?, users: AbstractMap<Long, User>?, chats: AbstractMap<Long, Chat>?, sUsers: LongSparseArray<User>?, sChats: LongSparseArray<Chat>?, generateLayout: Boolean, checkMediaExists: Boolean, eid: Long) {
		Theme.createCommonMessageResources()

		currentAccount = accountNum
		messageOwner = message

		if (replyToMessage != null) {
			replyMessageObject = replyToMessage
		}

		eventId = eid
		wasUnread = !message.out && message.unread

		if (message.replyMessage != null) {
			replyMessageObject = MessageObject(currentAccount, message.replyMessage!!, null, users, chats, sUsers, sChats, false, checkMediaExists, eid)
		}
		else if (message.replyTo != null) {
			val m = MessagesStorage.getInstance(currentAccount).getMessage(message.dialogId, (message.replyTo?.replyToMsgId ?: 0).toLong())

			if (m != null) {
				message.replyMessage = m
				replyMessageObject = MessageObject(currentAccount, m, null, users, chats, sUsers, sChats, false, checkMediaExists, eid)
			}
		}

		updateMessageText(users, chats, sUsers, sChats)
		setType()
		measureInlineBotButtons()

		val rightNow: Calendar = GregorianCalendar()
		rightNow.timeInMillis = message.date.toLong() * 1000

		val dateDay = rightNow[Calendar.DAY_OF_YEAR]
		val dateYear = rightNow[Calendar.YEAR]
		val dateMonth = rightNow[Calendar.MONTH]

		dateKey = String.format(Locale.getDefault(), "%d_%02d_%02d", dateYear, dateMonth, dateDay)
		monthKey = String.format(Locale.getDefault(), "%d_%02d", dateYear, dateMonth)

		createMessageSendInfo()
		generateCaption()

		if (generateLayout) {
			val paint = if (getMedia(message) is TLMessageMediaGame) {
				Theme.chat_msgGameTextPaint
			}
			else {
				Theme.chat_msgTextPaint
			}

			val emojiOnly = if (allowsBigEmoji()) IntArray(1) else null

			messageText = Emoji.replaceEmoji(messageText, paint.fontMetricsInt, false, emojiOnly)
			messageText = replaceAnimatedEmoji(messageText, (message as? TLMessage)?.entities, paint.fontMetricsInt)

			if (emojiOnly != null && emojiOnly[0] > 1) {
				replaceEmojiToLottieFrame(messageText, emojiOnly)
			}

			checkEmojiOnly(emojiOnly)
			checkBigAnimatedEmoji()
			setType()
			createPathThumb()
		}

		layoutCreated = generateLayout

		generateThumbs(false)

		if (checkMediaExists) {
			checkMediaExistence()
		}
	}

	constructor(accountNum: Int, event: TLChannelAdminLogEvent, messageObjects: ArrayList<MessageObject>, messagesByDays: HashMap<String, ArrayList<MessageObject>>, chat: Chat, mid: IntArray, addToEnd: Boolean) {
		currentEvent = event
		currentAccount = accountNum

		val context = ApplicationLoader.applicationContext
		var fromUser: User? = null

		if (event.userId > 0) {
			fromUser = MessagesController.getInstance(currentAccount).getUser(event.userId)
		}

		val rightNow: Calendar = GregorianCalendar()
		rightNow.timeInMillis = event.date.toLong() * 1000

		val dateDay = rightNow[Calendar.DAY_OF_YEAR]
		val dateYear = rightNow[Calendar.YEAR]
		val dateMonth = rightNow[Calendar.MONTH]

		dateKey = String.format(Locale.getDefault(), "%d_%02d_%02d", dateYear, dateMonth, dateDay)
		monthKey = String.format(Locale.getDefault(), "%d_%02d", dateYear, dateMonth)

		val peer_id: Peer = TLPeerChannel().also {
			it.channelId = chat.id
		}

		var message: Message? = null
		var webPageDescriptionEntities: MutableList<MessageEntity>? = null
		val action = event.action

		if (action is TLChannelAdminLogEventActionChangeTitle) {
			val title = action.newValue

			messageText = if (chat.megagroup) {
				replaceWithLink(LocaleController.formatString("EventLogEditedGroupTitle", R.string.EventLogEditedGroupTitle, title), "un1", fromUser)
			}
			else {
				replaceWithLink(LocaleController.formatString("EventLogEditedChannelTitle", R.string.EventLogEditedChannelTitle, title), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionChangePhoto) {
			val messageOwner = TLMessageService().also { this.messageOwner = it }

			if (action.newPhoto is TLPhotoEmpty) {
				messageOwner.action = TLMessageActionChatDeletePhoto()

				messageText = if (chat.megagroup) {
					replaceWithLink(context.getString(R.string.EventLogRemovedWGroupPhoto), "un1", fromUser)
				}
				else {
					replaceWithLink(context.getString(R.string.EventLogRemovedChannelPhoto), "un1", fromUser)
				}
			}
			else {
				messageOwner.action = TLMessageActionChatEditPhoto().also {
					it.photo = action.newPhoto
				}

				messageText = if (chat.megagroup) {
					if (isVideoAvatar) {
						replaceWithLink(context.getString(R.string.EventLogEditedGroupVideo), "un1", fromUser)
					}
					else {
						replaceWithLink(context.getString(R.string.EventLogEditedGroupPhoto), "un1", fromUser)
					}
				}
				else {
					if (isVideoAvatar) {
						replaceWithLink(context.getString(R.string.EventLogEditedChannelVideo), "un1", fromUser)
					}
					else {
						replaceWithLink(context.getString(R.string.EventLogEditedChannelPhoto), "un1", fromUser)
					}
				}
			}
		}
		else if (action is TLChannelAdminLogEventActionParticipantJoin) {
			messageText = if (chat.megagroup) {
				replaceWithLink(context.getString(R.string.EventLogGroupJoined), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogChannelJoined), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionParticipantLeave) {
			val messageOwner = TLMessageService().also { this.messageOwner = it }

			messageOwner.action = TLMessageActionChatDeleteUser().also {
				it.userId = event.userId
			}

			messageText = if (chat.megagroup) {
				replaceWithLink(context.getString(R.string.EventLogLeftGroup), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogLeftChannel), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionParticipantInvite) {
			val messageOwner = TLMessageService().also { this.messageOwner = it }
			messageOwner.action = TLMessageActionChatAddUser()

			var peerId = getPeerId(action.participant?.peer)

			if (peerId == 0L) {
				peerId = action.participant?.userId ?: 0L
			}

			val whoUser = if (peerId > 0) {
				MessagesController.getInstance(currentAccount).getUser(peerId)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-peerId)
			}

			if (messageOwner.fromId is TLPeerUser && peerId == (messageOwner.fromId as? TLPeerUser)?.userId) {
				messageText = if (chat.megagroup) {
					replaceWithLink(context.getString(R.string.EventLogGroupJoined), "un1", fromUser)
				}
				else {
					replaceWithLink(context.getString(R.string.EventLogChannelJoined), "un1", fromUser)
				}
			}
			else {
				messageText = replaceWithLink(context.getString(R.string.EventLogAdded), "un2", whoUser)
				messageText = replaceWithLink(messageText, "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionParticipantToggleAdmin || action is TLChannelAdminLogEventActionParticipantToggleBan && action.prevParticipant is TLChannelParticipantAdmin && action.newParticipant is TLChannelParticipant) {
			val prev_participant: ChannelParticipant?
			val new_participant: ChannelParticipant?

			when (action) {
				is TLChannelAdminLogEventActionParticipantToggleAdmin -> {
					prev_participant = action.prevParticipant
					new_participant = action.newParticipant
				}

				is TLChannelAdminLogEventActionParticipantToggleBan -> {
					prev_participant = action.prevParticipant
					new_participant = action.newParticipant
				}

				else -> {
					prev_participant = null
					new_participant = null
				}
			}

			val messageOwner = TLMessage().also { this.messageOwner = it }

			var peerId = getPeerId(prev_participant?.peer)

			if (peerId == 0L) {
				peerId = prev_participant?.userId ?: 0L
			}

			val whoUser: TLObject? = if (peerId > 0) {
				MessagesController.getInstance(currentAccount).getUser(peerId)
			}
			else {
				MessagesController.getInstance(currentAccount).getUser(-peerId)
			}

			val rights: StringBuilder

			if (prev_participant !is TLChannelParticipantCreator && new_participant is TLChannelParticipantCreator) {
				val str = context.getString(R.string.EventLogChangedOwnership)
				val offset = str.indexOf("%1\$s")
				rights = StringBuilder(String.format(str, getUserName(whoUser, messageOwner.entities, offset)))
			}
			else {
				val o = prev_participant?.adminRights ?: TLChatAdminRights()
				val n = new_participant?.adminRights ?: TLChatAdminRights()

				val str = if (n.other) {
					context.getString(R.string.EventLogPromotedNoRights)
				}
				else {
					context.getString(R.string.EventLogPromoted)
				}

				val offset = str.indexOf("%1\$s")

				rights = StringBuilder(String.format(str, getUserName(whoUser, messageOwner.entities, offset)))
				rights.append("\n")

				if (!TextUtils.equals(prev_participant?.rank, new_participant?.rank)) {
					if (new_participant?.rank.isNullOrEmpty()) {
						rights.append('\n').append('-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedRemovedTitle))
					}
					else {
						rights.append('\n').append('+').append(' ')
						rights.append(LocaleController.formatString("EventLogPromotedTitle", R.string.EventLogPromotedTitle, new_participant?.rank))
					}
				}

				if (o.changeInfo != n.changeInfo) {
					rights.append('\n').append(if (n.changeInfo) '+' else '-').append(' ')
					rights.append(if (chat.megagroup) context.getString(R.string.EventLogPromotedChangeGroupInfo) else context.getString(R.string.EventLogPromotedChangeChannelInfo))
				}

				if (!chat.megagroup) {
					if (o.postMessages != n.postMessages) {
						rights.append('\n').append(if (n.postMessages) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedPostMessages))
					}

					if (o.editMessages != n.editMessages) {
						rights.append('\n').append(if (n.editMessages) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedEditMessages))
					}
				}

				if (o.deleteMessages != n.deleteMessages) {
					rights.append('\n').append(if (n.deleteMessages) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogPromotedDeleteMessages))
				}

				if (o.addAdmins != n.addAdmins) {
					rights.append('\n').append(if (n.addAdmins) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogPromotedAddAdmins))
				}

				if (o.anonymous != n.anonymous) {
					rights.append('\n').append(if (n.anonymous) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogPromotedSendAnonymously))
				}

				if (chat.megagroup) {
					if (o.banUsers != n.banUsers) {
						rights.append('\n').append(if (n.banUsers) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedBanUsers))
					}

					if (o.manageCall != n.manageCall) {
						rights.append('\n').append(if (n.manageCall) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedManageCall))
					}
				}

				if (o.inviteUsers != n.inviteUsers) {
					rights.append('\n').append(if (n.inviteUsers) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogPromotedAddUsers))
				}

				if (chat.megagroup) {
					if (o.pinMessages != n.pinMessages) {
						rights.append('\n').append(if (n.pinMessages) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedPinMessages))
					}
				}
			}

			messageText = rights.toString()
		}
		else if (action is TLChannelAdminLogEventActionDefaultBannedRights) {
			messageOwner = TLMessage()

			val o = action.prevBannedRights ?: TLChatBannedRights()
			val n = action.newBannedRights ?: TLChatBannedRights()
			val rights = StringBuilder(context.getString(R.string.EventLogDefaultPermissions))
			var added = false

			if (o.sendMessages != n.sendMessages) {
				rights.append('\n')
				added = true
				rights.append('\n').append(if (!n.sendMessages) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendMessages))
			}

			if (o.sendStickers != n.sendStickers || o.sendInline != n.sendInline || o.sendGifs != n.sendGifs || o.sendGames != n.sendGames) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.sendStickers) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendStickers))
			}

			if (o.sendMedia != n.sendMedia) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.sendMedia) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendMedia))
			}

			if (o.sendPolls != n.sendPolls) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.sendPolls) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendPolls))
			}

			if (o.embedLinks != n.embedLinks) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.embedLinks) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendEmbed))
			}

			if (o.changeInfo != n.changeInfo) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.changeInfo) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedChangeInfo))
			}

			if (o.inviteUsers != n.inviteUsers) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.inviteUsers) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedInviteUsers))
			}

			if (o.pinMessages != n.pinMessages) {
				if (!added) {
					rights.append('\n')
				}

				rights.append('\n').append(if (!n.pinMessages) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedPinMessages))
			}

			messageText = rights.toString()
		}
		else if (action is TLChannelAdminLogEventActionParticipantToggleBan) {
			val messageOwner = TLMessage().also { this.messageOwner = it }

			var peerId = getPeerId(action.prevParticipant?.peer)

			if (peerId == 0L) {
				peerId = action.prevParticipant?.userId ?: 0L
			}

			val whoUser = if (peerId > 0) {
				MessagesController.getInstance(currentAccount).getUser(peerId)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-peerId)
			}

			var o = (action.prevParticipant as? TLChannelParticipantBanned)?.bannedRights
			var n = (action.newParticipant as? TLChannelParticipantBanned)?.bannedRights

			if (chat.megagroup && (n == null || !n.viewMessages || o != null && n.untilDate != o.untilDate)) {
				val rights: StringBuilder
				val bannedDuration: StringBuilder

				if (n != null && !AndroidUtilities.isBannedForever(n)) {
					bannedDuration = StringBuilder()

					var duration = n.untilDate - event.date
					val days = duration / 60 / 60 / 24

					duration -= days * 60 * 60 * 24

					val hours = duration / 60 / 60

					duration -= hours * 60 * 60

					val minutes = duration / 60
					var count = 0

					for (a in 0..2) {
						var addStr: String? = null

						if (a == 0) {
							if (days != 0) {
								addStr = LocaleController.formatPluralString("Days", days)
								count++
							}
						}
						else if (a == 1) {
							if (hours != 0) {
								addStr = LocaleController.formatPluralString("Hours", hours)
								count++
							}
						}
						else {
							if (minutes != 0) {
								addStr = LocaleController.formatPluralString("Minutes", minutes)
								count++
							}
						}

						if (addStr != null) {
							if (bannedDuration.isNotEmpty()) {
								bannedDuration.append(", ")
							}

							bannedDuration.append(addStr)
						}

						if (count == 2) {
							break
						}
					}
				}
				else {
					bannedDuration = StringBuilder(context.getString(R.string.UserRestrictionsUntilForever))
				}

				val str = context.getString(R.string.EventLogRestrictedUntil)
				val offset = str.indexOf("%1\$s")

				rights = StringBuilder(String.format(str, getUserName(whoUser, messageOwner.entities, offset), bannedDuration))

				var added = false

				if (o == null) {
					o = TLChatBannedRights()
				}

				if (n == null) {
					n = TLChatBannedRights()
				}

				if (o.viewMessages != n.viewMessages) {
					rights.append('\n')
					added = true
					rights.append('\n').append(if (!n.viewMessages) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedReadMessages))
				}

				if (o.sendMessages != n.sendMessages) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.sendMessages) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendMessages))
				}

				if (o.sendStickers != n.sendStickers || o.sendInline != n.sendInline || o.sendGifs != n.sendGifs || o.sendGames != n.sendGames) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.sendStickers) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendStickers))
				}

				if (o.sendMedia != n.sendMedia) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.sendMedia) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendMedia))
				}

				if (o.sendPolls != n.sendPolls) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.sendPolls) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendPolls))
				}

				if (o.embedLinks != n.embedLinks) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.embedLinks) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendEmbed))
				}

				if (o.changeInfo != n.changeInfo) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.changeInfo) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedChangeInfo))
				}

				if (o.inviteUsers != n.inviteUsers) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.inviteUsers) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedInviteUsers))
				}

				if (o.pinMessages != n.pinMessages) {
					if (!added) {
						rights.append('\n')
					}

					rights.append('\n').append(if (!n.pinMessages) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedPinMessages))
				}

				messageText = rights.toString()
			}
			else {
				val str = if (n != null && (o == null || n.viewMessages)) {
					context.getString(R.string.EventLogChannelRestricted)
				}
				else {
					context.getString(R.string.EventLogChannelUnrestricted)
				}

				val offset = str.indexOf("%1\$s")

				messageText = String.format(str, getUserName(whoUser, messageOwner.entities, offset))
			}
		}
		else if (action is TLChannelAdminLogEventActionUpdatePinned) {
			message = action.message

			val fromId = (message as? TLMessage)?.fwdFrom?.fromId
			val pinned = (message as? TLMessage)?.pinned ?: false

			if (fromUser != null && fromUser.id == 136817688L && fromId is TLPeerChannel) {
				val channel = MessagesController.getInstance(currentAccount).getChat(fromId.channelId)

				messageText = if (action.message is TLMessageEmpty || !pinned) {
					replaceWithLink(context.getString(R.string.EventLogUnpinnedMessages), "un1", channel)
				}
				else {
					replaceWithLink(context.getString(R.string.EventLogPinnedMessages), "un1", channel)
				}
			}
			else {
				messageText = if (action.message is TLMessageEmpty || !pinned) {
					replaceWithLink(context.getString(R.string.EventLogUnpinnedMessages), "un1", fromUser)
				}
				else {
					replaceWithLink(context.getString(R.string.EventLogPinnedMessages), "un1", fromUser)
				}
			}
		}
		else if (action is TLChannelAdminLogEventActionStopPoll) {
			message = action.message

			val media = getMedia(message)

			messageText = if (media is TLMessageMediaPoll && media.poll?.quiz == true) {
				replaceWithLink(context.getString(R.string.EventLogStopQuiz), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogStopPoll), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionToggleSignatures) {
			messageText = if (action.newValue) {
				replaceWithLink(context.getString(R.string.EventLogToggledSignaturesOn), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogToggledSignaturesOff), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionToggleInvites) {
			messageText = if (action.newValue) {
				replaceWithLink(context.getString(R.string.EventLogToggledInvitesOn), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogToggledInvitesOff), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionDeleteMessage) {
			message = action.message
			messageText = replaceWithLink(context.getString(R.string.EventLogDeletedMessages), "un1", fromUser)
		}
		else if (action is TLChannelAdminLogEventActionChangeLinkedChat) {
			val newChatId = action.newValue
			val oldChatId = action.prevValue

			if (chat.megagroup) {
				if (newChatId == 0L) {
					val oldChat = MessagesController.getInstance(currentAccount).getChat(oldChatId)

					messageText = replaceWithLink(context.getString(R.string.EventLogRemovedLinkedChannel), "un1", fromUser)
					messageText = replaceWithLink(messageText, "un2", oldChat)
				}
				else {
					val newChat = MessagesController.getInstance(currentAccount).getChat(newChatId)

					messageText = replaceWithLink(context.getString(R.string.EventLogChangedLinkedChannel), "un1", fromUser)
					messageText = replaceWithLink(messageText, "un2", newChat)
				}
			}
			else {
				if (newChatId == 0L) {
					val oldChat = MessagesController.getInstance(currentAccount).getChat(oldChatId)

					messageText = replaceWithLink(context.getString(R.string.EventLogRemovedLinkedGroup), "un1", fromUser)
					messageText = replaceWithLink(messageText, "un2", oldChat)
				}
				else {
					val newChat = MessagesController.getInstance(currentAccount).getChat(newChatId)

					messageText = replaceWithLink(context.getString(R.string.EventLogChangedLinkedGroup), "un1", fromUser)
					messageText = replaceWithLink(messageText, "un2", newChat)
				}
			}
		}
		else if (action is TLChannelAdminLogEventActionTogglePreHistoryHidden) {
			messageText = if (action.newValue) {
				replaceWithLink(context.getString(R.string.EventLogToggledInvitesHistoryOff), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogToggledInvitesHistoryOn), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionChangeAbout) {
			messageText = replaceWithLink(if (chat.megagroup) context.getString(R.string.EventLogEditedGroupDescription) else context.getString(R.string.EventLogEditedChannelDescription), "un1", fromUser)

			message = TLMessage()
			message.out = false
			message.unread = false
			message.fromId = TLPeerUser().also { it.userId = event.userId }
			message.peerId = peer_id
			message.date = event.date

			message.message = action.newValue

			if (!action.prevValue.isNullOrEmpty()) {
				message.media = TLMessageMediaWebPage().also {
					it.webpage = TLWebPage().also {
						it.flags = 10
						it.displayUrl = ""
						it.url = ""
						it.siteName = context.getString(R.string.EventLogPreviousGroupDescription)
						it.description = action.prevValue
					}
				}
			}
			else {
				message.media = TLMessageMediaEmpty()
			}
		}
//		else if (action is TLChannelAdminLogEventActionChangeTheme) {
//			messageText = replaceWithLink(if (chat.megagroup) context.getString(R.string.EventLogEditedGroupTheme) else context.getString(R.string.EventLogEditedChannelTheme), "un1", fromUser)
//
//			message = TLMessage()
//			message.out = false
//			message.unread = false
//			message.fromId = TLPeerUser()
//			message.fromId?.userId = event.userId
//			message.peerId = peer_id
//			message.date = event.date
//			message.message = (action as TLChannelAdminLogEventActionChangeTheme).newValue
//
//			if (!(action as TLChannelAdminLogEventActionChangeTheme).prev_value.isNullOrEmpty()) {
//				message.media = TLMessageMediaWebPage()
//				message.media?.webpage = TLWebPage()
//				message.media?.webpage?.flags = 10
//				message.media?.webpage?.display_url = ""
//				message.media?.webpage?.url = ""
//				message.media?.webpage?.site_name = context.getString(R.string.EventLogPreviousGroupTheme)
//				message.media?.webpage?.description = (action as TLChannelAdminLogEventActionChangeTheme).prev_value
//			}
//			else {
//				message.media = TLMessageMediaEmpty()
//			}
//		}
		else if (action is TLChannelAdminLogEventActionChangeUsername) {
			val newLink = action.newValue

			messageText = if (!newLink.isNullOrEmpty()) {
				replaceWithLink(if (chat.megagroup) context.getString(R.string.EventLogChangedGroupLink) else context.getString(R.string.EventLogChangedChannelLink), "un1", fromUser)
			}
			else {
				replaceWithLink(if (chat.megagroup) context.getString(R.string.EventLogRemovedGroupLink) else context.getString(R.string.EventLogRemovedChannelLink), "un1", fromUser)
			}

			message = TLMessage()
			message.out = false
			message.unread = false
			message.fromId = TLPeerUser().also { it.userId = event.userId }
			message.peerId = peer_id
			message.date = event.date

			if (!newLink.isNullOrEmpty()) {
				message.message = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + newLink
			}
			else {
				message.message = ""
			}

			val url = TLMessageEntityUrl()
			url.offset = 0
			url.length = message.message!!.length

			message.entities.add(url)

			if (!action.prevValue.isNullOrEmpty()) {
				message.media = TLMessageMediaWebPage().also {
					it.webpage = TLWebPage().also {
						it.flags = 10
						it.displayUrl = ""
						it.url = ""
						it.siteName = context.getString(R.string.EventLogPreviousLink)
						it.description = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + action.prevValue
					}
				}
			}
			else {
				message.media = TLMessageMediaEmpty()
			}
		}
		else if (action is TLChannelAdminLogEventActionEditMessage) {
			message = TLMessage()
			message.out = false
			message.unread = false
			message.peerId = peer_id
			message.date = event.date

			val newMessage = action.newMessage
			val oldMessage = action.prevMessage

			if (newMessage?.fromId != null) {
				message.fromId = newMessage.fromId
			}
			else {
				message.fromId = TLPeerUser().also { it.userId = event.userId }
			}

			val newMessageMedia = getMedia(newMessage)

			if (newMessage is TLMessage && oldMessage is TLMessage && newMessageMedia != null && newMessageMedia !is TLMessageMediaEmpty && newMessageMedia !is TLMessageMediaWebPage) {
				val changedCaption = !TextUtils.equals(newMessage.message, oldMessage.message)
				val newPhoto = (newMessageMedia as? TLMessageMediaPhoto)?.photo
				val oldPhoto = (oldMessage.media as? TLMessageMediaPhoto)?.photo
				val newDocument = (newMessageMedia as? TLMessageMediaDocument)?.document
				val oldDocument = (oldMessage.media as? TLMessageMediaDocument)?.document
				val changedMedia = newMessageMedia.javaClass != oldMessage.media?.javaClass || newPhoto != null && oldPhoto != null && newPhoto.id != oldPhoto.id || newDocument != null && oldDocument != null && newDocument.id != oldDocument.id

				messageText = if (changedMedia && changedCaption) {
					replaceWithLink(context.getString(R.string.EventLogEditedMediaCaption), "un1", fromUser)
				}
				else if (changedCaption) {
					replaceWithLink(context.getString(R.string.EventLogEditedCaption), "un1", fromUser)
				}
				else {
					replaceWithLink(context.getString(R.string.EventLogEditedMedia), "un1", fromUser)
				}

				message.media = getMedia(newMessage)

				if (changedCaption) {
					val media = message.media

					if (media is TLMessageMediaWebPage) {
						media.webpage = TLWebPage().also {
							it.siteName = context.getString(R.string.EventLogOriginalCaption)

							if (oldMessage.message.isNullOrEmpty()) {
								it.description = context.getString(R.string.EventLogOriginalCaptionEmpty)
							}
							else {
								it.description = oldMessage.message

								webPageDescriptionEntities = oldMessage.entities
							}
						}
					}
				}
			}
			else {
				messageText = replaceWithLink(context.getString(R.string.EventLogEditedMessages), "un1", fromUser)

				if ((newMessage as? TLMessageService)?.action is TLMessageActionGroupCall) {
					message = newMessage
					// message.media = TLMessageMediaEmpty()
				}
				else {
					if (newMessage is TLMessage) {
						@Suppress("NAME_SHADOWING") val oldMessage = oldMessage as? TLMessage

						message.message = newMessage.message

						message.entities.clear()
						message.entities.addAll(newMessage.entities)

						var webpage: TLWebPage?

						message.media = TLMessageMediaWebPage().also {
							it.webpage = TLWebPage().also {
								it.siteName = context.getString(R.string.EventLogOriginalMessages)
								webpage = it
							}
						}

						if (oldMessage?.message.isNullOrEmpty()) {
							webpage?.description = context.getString(R.string.EventLogOriginalCaptionEmpty)
						}
						else {
							webpage?.description = oldMessage?.message
							webPageDescriptionEntities = oldMessage?.entities
						}
					}
				}
			}

			(message as? TLMessage)?.replyMarkup = newMessage?.replyMarkup

			((message as? TLMessage)?.media as? TLMessageMediaWebPage)?.let {
				(it.webpage as? TLWebPage)?.let {
					it.flags = 10
					it.displayUrl = ""
					it.url = ""
				}
			}
		}
		else if (action is TLChannelAdminLogEventActionChangeStickerSet) {
			val newStickerset = action.newStickerset
			// TLRPC.InputStickerSet oldStickerset = ((TLRPC.TLChannelAdminLogEventActionChangeStickerSet)action).new_stickerset;

			messageText = if (newStickerset == null || newStickerset is TLInputStickerSetEmpty) {
				replaceWithLink(context.getString(R.string.EventLogRemovedStickersSet), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogChangedStickersSet), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionChangeLocation) {
			if (action.newValue is TLChannelLocationEmpty) {
				messageText = replaceWithLink(context.getString(R.string.EventLogRemovedLocation), "un1", fromUser)
			}
			else {
				val channelLocation = action.newValue as TLChannelLocation
				messageText = replaceWithLink(LocaleController.formatString("EventLogChangedLocation", R.string.EventLogChangedLocation, channelLocation.address), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionToggleSlowMode) {
			if (action.newValue == 0) {
				messageText = replaceWithLink(context.getString(R.string.EventLogToggledSlowmodeOff), "un1", fromUser)
			}
			else {
				val string = if (action.newValue < 60) {
					LocaleController.formatPluralString("Seconds", action.newValue)
				}
				else if (action.newValue < 60 * 60) {
					LocaleController.formatPluralString("Minutes", action.newValue / 60)
				}
				else {
					LocaleController.formatPluralString("Hours", action.newValue / 60 / 60)
				}

				messageText = replaceWithLink(LocaleController.formatString("EventLogToggledSlowmodeOn", R.string.EventLogToggledSlowmodeOn, string), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionStartGroupCall) {
			messageText = if (ChatObject.isChannel(chat) && (!chat.megagroup || chat.gigagroup)) {
				replaceWithLink(context.getString(R.string.EventLogStartedLiveStream), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogStartedVoiceChat), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionDiscardGroupCall) {
			messageText = if (ChatObject.isChannel(chat) && (!chat.megagroup || chat.gigagroup)) {
				replaceWithLink(context.getString(R.string.EventLogEndedLiveStream), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogEndedVoiceChat), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionParticipantMute) {
			val id = getPeerId(action.participant?.peer)

			val `object` = if (id > 0) {
				MessagesController.getInstance(currentAccount).getUser(id)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-id)
			}

			messageText = replaceWithLink(context.getString(R.string.EventLogVoiceChatMuted), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", `object`)
		}
		else if (action is TLChannelAdminLogEventActionParticipantUnmute) {
			val id = getPeerId(action.participant?.peer)

			val `object` = if (id > 0) {
				MessagesController.getInstance(currentAccount).getUser(id)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-id)
			}

			messageText = replaceWithLink(context.getString(R.string.EventLogVoiceChatUnmuted), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", `object`)
		}
		else if (action is TLChannelAdminLogEventActionToggleGroupCallSetting) {
			messageText = if (action.joinMuted) {
				replaceWithLink(context.getString(R.string.EventLogVoiceChatNotAllowedToSpeak), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogVoiceChatAllowedToSpeak), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionParticipantJoinByInvite) {
			messageText = replaceWithLink(context.getString(R.string.ActionInviteUser), "un1", fromUser)
		}
		else if (action is TLChannelAdminLogEventActionToggleNoForwards) {
			val isChannel = ChatObject.isChannel(chat) && !chat.megagroup

			messageText = if (action.newValue) {
				if (isChannel) {
					replaceWithLink(context.getString(R.string.ActionForwardsRestrictedChannel), "un1", fromUser)
				}
				else {
					replaceWithLink(context.getString(R.string.ActionForwardsRestrictedGroup), "un1", fromUser)
				}
			}
			else {
				if (isChannel) {
					replaceWithLink(context.getString(R.string.ActionForwardsEnabledChannel), "un1", fromUser)
				}
				else {
					replaceWithLink(context.getString(R.string.ActionForwardsEnabledGroup), "un1", fromUser)
				}
			}
		}
		else if (action is TLChannelAdminLogEventActionExportedInviteDelete) {
			messageText = replaceWithLink(LocaleController.formatString("ActionDeletedInviteLinkClickable", R.string.ActionDeletedInviteLinkClickable), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", action.invite)
		}
		else if (action is TLChannelAdminLogEventActionExportedInviteRevoke) {
			messageText = replaceWithLink(LocaleController.formatString("ActionRevokedInviteLinkClickable", R.string.ActionRevokedInviteLinkClickable, (action.invite as? TLChatInviteExported)?.link), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", action.invite)
		}
		else if (action is TLChannelAdminLogEventActionExportedInviteEdit) {
			messageText = if ((action.prevInvite as? TLChatInviteExported)?.link != null && (action.prevInvite as? TLChatInviteExported)?.link == (action.newInvite as? TLChatInviteExported)?.link) {
				replaceWithLink(LocaleController.formatString("ActionEditedInviteLinkToSameClickable", R.string.ActionEditedInviteLinkToSameClickable), "un1", fromUser)
			}
			else {
				replaceWithLink(LocaleController.formatString("ActionEditedInviteLinkClickable", R.string.ActionEditedInviteLinkClickable), "un1", fromUser)
			}

			messageText = replaceWithLink(messageText, "un2", action.prevInvite)
			messageText = replaceWithLink(messageText, "un3", action.newInvite)
		}
		else if (action is TLChannelAdminLogEventActionParticipantVolume) {
			val id = getPeerId(action.participant?.peer)

			val `object` = if (id > 0) {
				MessagesController.getInstance(currentAccount).getUser(id)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-id)
			}

			val vol = ChatObject.getParticipantVolume(action.participant) / 100.0

			messageText = replaceWithLink(LocaleController.formatString("ActionVolumeChanged", R.string.ActionVolumeChanged, (if (vol > 0) max(vol, 1.0) else 0).toInt()), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", `object`)
		}
		else if (action is TLChannelAdminLogEventActionChangeHistoryTTL) {
			if (!chat.megagroup) {
				messageText = if (action.newValue != 0) {
					LocaleController.formatString("ActionTTLChannelChanged", R.string.ActionTTLChannelChanged, LocaleController.formatTTLString(action.newValue))
				}
				else {
					context.getString(R.string.ActionTTLChannelDisabled)
				}
			}
			else if (action.newValue == 0) {
				messageText = replaceWithLink(context.getString(R.string.ActionTTLDisabled), "un1", fromUser)
			}
			else {
				val time = if (action.newValue > 24 * 60 * 60) {
					LocaleController.formatPluralString("Days", action.newValue / (24 * 60 * 60))
				}
				else if (action.newValue >= 60 * 60) {
					LocaleController.formatPluralString("Hours", action.newValue / (60 * 60))
				}
				else if (action.newValue >= 60) {
					LocaleController.formatPluralString("Minutes", action.newValue / 60)
				}
				else {
					LocaleController.formatPluralString("Seconds", action.newValue)
				}

				messageText = replaceWithLink(LocaleController.formatString("ActionTTLChanged", R.string.ActionTTLChanged, time), "un1", fromUser)
			}
		}
		else if (action is TLChannelAdminLogEventActionParticipantJoinByRequest) {
			val url = String.format(Locale.getDefault(), "https://%s/+PublicChat", ApplicationLoader.applicationContext.getString(R.string.domain))

			if ((action.invite is TLChatInviteExported) && url == (action.invite as TLChatInviteExported).link || action.invite is TLChatInvitePublicJoinRequests) {
				messageText = replaceWithLink(context.getString(R.string.JoinedViaRequestApproved), "un1", fromUser)
				messageText = replaceWithLink(messageText, "un2", MessagesController.getInstance(currentAccount).getUser(action.approvedBy))
			}
			else {
				messageText = replaceWithLink(context.getString(R.string.JoinedViaInviteLinkApproved), "un1", fromUser)
				messageText = replaceWithLink(messageText, "un2", action.invite)
				messageText = replaceWithLink(messageText, "un3", MessagesController.getInstance(currentAccount).getUser(action.approvedBy))
			}
		}
		else if (action is TLChannelAdminLogEventActionSendMessage) {
			message = action.message

			messageText = replaceWithLink(context.getString(R.string.EventLogSendMessages), "un1", fromUser)
		}
		else if (action is TLChannelAdminLogEventActionChangeAvailableReactions) {
			val oldReactions = getStringFrom(action.prevValue)
			val newReactions = getStringFrom(action.newValue)
			val spannableStringBuilder = SpannableStringBuilder(replaceWithLink(LocaleController.formatString("ActionReactionsChanged", R.string.ActionReactionsChanged, "**old**", "**new**"), "un1", fromUser))

			var i = spannableStringBuilder.toString().indexOf("**old**")

			if (i > 0) {
				spannableStringBuilder.replace(i, i + "**old**".length, oldReactions)
			}

			i = spannableStringBuilder.toString().indexOf("**new**")

			if (i > 0) {
				spannableStringBuilder.replace(i, i + "**new**".length, newReactions)
			}

			messageText = spannableStringBuilder
		}
		else {
			messageText = "unsupported $action"
		}

		if (messageOwner == null) {
			messageOwner = TLMessageService()
		}

		(messageOwner as? TLMessage)?.message = messageText.toString()
		messageOwner?.fromId = TLPeerUser().also { it.userId = event.userId }
		messageOwner?.date = event.date
		messageOwner?.id = mid[0]++

		eventId = event.id

		messageOwner?.out = false
		messageOwner?.peerId = TLPeerChannel().also { it.channelId = chat.id }
		messageOwner?.unread = false

		val mediaController = MediaController.getInstance()

		if (message is TLMessageEmpty) {
			message = null
		}

		if (message != null) {
			message.out = false
			message.id = mid[0]++
			message.flags = message.flags and TLRPC.MESSAGE_FLAG_REPLY.inv()
			message.replyTo = null
			message.flags = message.flags and TLRPC.MESSAGE_FLAG_EDITED.inv()

			val messageObject = MessageObject(currentAccount, message, null, null, generateLayout = true, checkMediaExists = true, eid = eventId)

			if (messageObject.contentType >= 0) {
				if (mediaController.isPlayingMessage(messageObject)) {
					val player = mediaController.playingMessageObject

					messageObject.audioProgress = player?.audioProgress ?: 0f
					messageObject.audioProgressSec = player?.audioProgressSec ?: 0
				}

				createDateArray(currentAccount, event, messageObjects, messagesByDays, addToEnd)

				if (addToEnd) {
					messageObjects.add(0, messageObject)
				}
				else {
					messageObjects.add(messageObjects.size - 1, messageObject)
				}
			}
			else {
				contentType = -1
			}

			if (webPageDescriptionEntities != null) {
				messageObject.webPageDescriptionEntities = webPageDescriptionEntities
				messageObject.linkDescription = null
				messageObject.generateLinkDescription()
			}
		}

		if (contentType >= 0) {
			createDateArray(currentAccount, event, messageObjects, messagesByDays, addToEnd)

			if (addToEnd) {
				messageObjects.add(0, this)
			}
			else {
				messageObjects.add(messageObjects.size - 1, this)
			}
		}
		else {
			return
		}

		if (messageText == null) {
			messageText = ""
		}

		val paint = if (getMedia(messageOwner) is TLMessageMediaGame) {
			Theme.chat_msgGameTextPaint
		}
		else {
			Theme.chat_msgTextPaint
		}

		val emojiOnly = if (allowsBigEmoji()) IntArray(1) else null

		messageText = Emoji.replaceEmoji(messageText, paint.fontMetricsInt, false, emojiOnly)
		messageText = replaceAnimatedEmoji(messageText, (messageOwner as? TLMessage)?.entities, paint.fontMetricsInt)

		if (emojiOnly != null && emojiOnly[0] > 1) {
			replaceEmojiToLottieFrame(messageText, emojiOnly)
		}

		checkEmojiOnly(emojiOnly)

		setType()
		measureInlineBotButtons()
		generateCaption()

		if (mediaController.isPlayingMessage(this)) {
			val player = mediaController.playingMessageObject

			audioProgress = player?.audioProgress ?: 0f
			audioProgressSec = player?.audioProgressSec ?: 0
		}

		generateLayout()

		layoutCreated = true

		generateThumbs(false)

		checkMediaExistence()
	}

	fun shouldDrawReactionsInLayout(): Boolean {
		return dialogId < 0 // MARK: allow likes for channels only

		//		if (getDialogId() < 0 || UserConfig.getInstance(currentAccount).isPremium()) {
//			return true;
//		}
//		TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(getDialogId());
//		return user != null && user.premium;//getDialogId() < 0 || UserConfig.getInstance(currentAccount).isPremium();
	}

	val randomUnreadReaction: TLMessagePeerReaction?
		get() = (messageOwner as? TLMessage)?.reactions?.recentReactions?.firstOrNull()

	fun markReactionsAsRead() {
		var changed = false

		(messageOwner as? TLMessage)?.reactions?.recentReactions?.forEach {
			if (it.unread) {
				it.unread = false
				changed = true
			}
		}

		if (changed) {
			MessagesStorage.getInstance(currentAccount).markMessageReactionsAsRead(messageOwner?.dialogId ?: 0, messageOwner?.id ?: 0, true)
		}
	}

	val isPremiumSticker: Boolean
		get() {
			val media = getMedia(messageOwner) as? TLMessageMediaDocument

			if (media != null && media.nopremium) {
				return false
			}

			return isPremiumSticker(document)
		}

	val premiumStickerAnimation: TLRPC.VideoSize?
		get() = getPremiumStickerAnimation(document)

	fun copyStableParams(old: MessageObject) {
		stableId = old.stableId
		messageOwner?.premiumEffectWasPlayed = old.messageOwner?.premiumEffectWasPlayed ?: false
		forcePlayEffect = old.forcePlayEffect
		wasJustSent = old.wasJustSent

		val reactions = (messageOwner as? TLMessage)?.reactions
		val oldReactions = (old.messageOwner as? TLMessage)?.reactions

		if (!reactions?.results.isNullOrEmpty() && oldReactions?.results != null) {
			for (i in reactions!!.results.indices) {
				val reactionCount = reactions.results[i]

				for (j in oldReactions.results.indices) {
					val oldReaction = oldReactions.results[j]

					if (ReactionsLayoutInBubble.equalsTLReaction(reactionCount.reaction, oldReaction.reaction)) {
						reactionCount.lastDrawnPosition = oldReaction.lastDrawnPosition
					}
				}
			}
		}

		isSpoilersRevealed = old.isSpoilersRevealed

		if (isSpoilersRevealed) {
			textLayoutBlocks?.forEach {
				it.spoilers.clear()
			}
		}
	}

	val chosenReactions: List<VisibleReaction>
		get() {
			val chosenReactions = mutableListOf<VisibleReaction>()
			val reactions = (messageOwner as? TLMessage)?.reactions

			if (reactions != null) {
				for (i in reactions.results.indices) {
					if (reactions.results[i].chosen) {
						chosenReactions.add(VisibleReaction.fromTLReaction(reactions.results[i].reaction))
					}
				}
			}

			return chosenReactions
		}

	private fun checkBigAnimatedEmoji() {
		emojiAnimatedSticker = null
		emojiAnimatedStickerId = null

		if (emojiOnlyCount == 1 && getMedia(messageOwner) !is TLMessageMediaWebPage && getMedia(messageOwner) !is TLMessageMediaInvoice && (getMedia(messageOwner) is TLMessageMediaEmpty || getMedia(messageOwner) == null) && (messageOwner as? TLMessage)?.groupedId == 0L) {
			if ((messageOwner as? TLMessage)?.entities.isNullOrEmpty()) {
				var emoji = messageText
				var index: Int

				if ((TextUtils.indexOf(emoji, "\uD83C\uDFFB").also { index = it }) >= 0) {
					emojiAnimatedStickerColor = "_c1"
					emoji = emoji!!.subSequence(0, index)
				}
				else if ((TextUtils.indexOf(emoji, "\uD83C\uDFFC").also { index = it }) >= 0) {
					emojiAnimatedStickerColor = "_c2"
					emoji = emoji!!.subSequence(0, index)
				}
				else if ((TextUtils.indexOf(emoji, "\uD83C\uDFFD").also { index = it }) >= 0) {
					emojiAnimatedStickerColor = "_c3"
					emoji = emoji!!.subSequence(0, index)
				}
				else if ((TextUtils.indexOf(emoji, "\uD83C\uDFFE").also { index = it }) >= 0) {
					emojiAnimatedStickerColor = "_c4"
					emoji = emoji!!.subSequence(0, index)
				}
				else if ((TextUtils.indexOf(emoji, "\uD83C\uDFFF").also { index = it }) >= 0) {
					emojiAnimatedStickerColor = "_c5"
					emoji = emoji!!.subSequence(0, index)
				}
				else {
					emojiAnimatedStickerColor = ""
				}

				if (!emojiAnimatedStickerColor.isNullOrEmpty() && index + 2 < messageText!!.length) {
					emoji = emoji.toString() + messageText!!.subSequence(index + 2, messageText!!.length)
				}

				if (emojiAnimatedStickerColor.isNullOrEmpty() || EmojiData.emojiColoredMap.contains(emoji.toString())) {
					emojiAnimatedSticker = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(emoji)
				}
			}
			else if ((messageOwner as? TLMessage)?.entities?.size == 1 && (messageOwner as? TLMessage)?.entities?.first() is TLMessageEntityCustomEmoji) {
				try {
					emojiAnimatedStickerId = ((messageOwner as? TLMessage)?.entities?.first() as TLMessageEntityCustomEmoji).documentId
					emojiAnimatedSticker = AnimatedEmojiDrawable.findDocument(currentAccount, emojiAnimatedStickerId!!)

					if (emojiAnimatedSticker == null && messageText is Spanned) {
						val animatedEmojiSpans = (messageText as Spanned).getSpans(0, messageText!!.length, AnimatedEmojiSpan::class.java)

						if (animatedEmojiSpans != null && animatedEmojiSpans.size == 1) {
							emojiAnimatedSticker = animatedEmojiSpans[0].document
						}
					}
				}
				catch (e: Exception) {
					// ignored
				}
			}
		}

		if (emojiAnimatedSticker == null && emojiAnimatedStickerId == null) {
			generateLayout()
		}
		else {
			type = 1000

			if (isSticker) {
				type = TYPE_STICKER
			}
			else if (isAnimatedSticker) {
				type = TYPE_ANIMATED_STICKER
			}
		}
	}

	private fun createPathThumb() {
		val document = document ?: return
		pathThumb = DocumentObject.getSvgThumb(document, ApplicationLoader.applicationContext.getColor(R.color.light_background), 1.0f)
	}

	fun createStrippedThumb() {
		val photoThumbs = photoThumbs

		if (photoThumbs == null || SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_HIGH && !hasExtendedMediaPreview()) {
			return
		}

		try {
			for (photoSize in photoThumbs) {
				if (photoSize is TLPhotoStrippedSize) {
					strippedThumb = ImageLoader.getStrippedPhotoBitmap(photoSize.bytes, "b")?.toDrawable(ApplicationLoader.applicationContext.resources)
					break
				}
			}
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}
	}

	private fun createDateArray(accountNum: Int, event: TLChannelAdminLogEvent, messageObjects: ArrayList<MessageObject>, messagesByDays: HashMap<String, ArrayList<MessageObject>>, addToEnd: Boolean) {
		val dateKey = dateKey ?: return
		var dayArray = messagesByDays[dateKey]

		if (dayArray == null) {
			dayArray = ArrayList()

			messagesByDays[dateKey] = dayArray

			val dateMsg = TLMessage()
			dateMsg.message = LocaleController.formatDateChat(event.date.toLong())
			dateMsg.id = 0
			dateMsg.date = event.date

			val dateObj = MessageObject(accountNum, dateMsg, generateLayout = false, checkMediaExists = false)
			dateObj.type = 10
			dateObj.contentType = 1
			dateObj.isDateObject = true

			if (addToEnd) {
				messageObjects.add(0, dateObj)
			}
			else {
				messageObjects.add(dateObj)
			}
		}
	}

	private fun checkEmojiOnly(emojiOnly: IntArray?) {
		checkEmojiOnly(emojiOnly?.get(0))
	}

	private fun checkEmojiOnly(emojiOnly: Int?) {
		val messageText = messageText

		if (messageText.isNullOrEmpty()) {
			return
		}

		if (emojiOnly != null && emojiOnly >= 1) {
			val spans = (messageText as? Spannable)?.getSpans(0, messageText.length, EmojiSpan::class.java)
			val aspans = (messageText as? Spannable)?.getSpans(0, messageText.length, AnimatedEmojiSpan::class.java)

			emojiOnlyCount = max(emojiOnly.toDouble(), ((spans?.size ?: 0) + (aspans?.size ?: 0)).toDouble()).toInt()
			totalAnimatedEmojiCount = aspans?.size ?: 0

			var animatedEmojiCount = 0

			if (aspans != null) {
				for (aspan in aspans) {
					if (!aspan.standard) {
						animatedEmojiCount++
					}
				}
			}

			hasUnwrappedEmoji = emojiOnlyCount - (spans?.size ?: 0) - (aspans?.size ?: 0) > 0

			if (emojiOnlyCount == 0 || hasUnwrappedEmoji) {
				if (!aspans.isNullOrEmpty()) {
					for (aspan in aspans) {
						aspan.replaceFontMetrics(Theme.chat_msgTextPaint.fontMetricsInt, (Theme.chat_msgTextPaint.textSize + AndroidUtilities.dp(4f)).toInt(), -1)
						aspan.full = false
					}
				}

				return
			}

			val large = emojiOnlyCount == animatedEmojiCount
			var cacheType = -1
			val emojiPaint: TextPaint

			when (emojiOnlyCount) {
				0, 1, 2 -> {
					cacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE
					emojiPaint = if (large) Theme.chat_msgTextPaintEmoji[0] else Theme.chat_msgTextPaintEmoji[2]
				}

				3 -> {
					cacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE
					emojiPaint = if (large) Theme.chat_msgTextPaintEmoji[1] else Theme.chat_msgTextPaintEmoji[3]
				}

				4 -> {
					cacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE
					emojiPaint = if (large) Theme.chat_msgTextPaintEmoji[2] else Theme.chat_msgTextPaintEmoji[4]
				}

				5 -> {
					cacheType = AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD
					emojiPaint = if (large) Theme.chat_msgTextPaintEmoji[3] else Theme.chat_msgTextPaintEmoji[5]
				}

				6 -> {
					cacheType = AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD
					emojiPaint = if (large) Theme.chat_msgTextPaintEmoji[4] else Theme.chat_msgTextPaintEmoji[5]
				}

				7, 8, 9 -> {
					if (emojiOnlyCount > 9) {
						cacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES
					}

					emojiPaint = Theme.chat_msgTextPaintEmoji[5]
				}

				else -> {
					if (emojiOnlyCount > 9) {
						cacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES
					}

					emojiPaint = Theme.chat_msgTextPaintEmoji[5]
				}
			}

			val size = (emojiPaint.textSize + AndroidUtilities.dp(4f)).toInt()

			if (!spans.isNullOrEmpty()) {
				for (span in spans) {
					span.replaceFontMetrics(emojiPaint.fontMetricsInt, size)
				}
			}

			if (!aspans.isNullOrEmpty()) {
				for (aspan in aspans) {
					aspan.replaceFontMetrics(emojiPaint.fontMetricsInt, size, cacheType)
					aspan.full = true
				}
			}
		}
		else {
			val aspans = (messageText as? Spannable)?.getSpans(0, messageText.length, AnimatedEmojiSpan::class.java)

			if (!aspans.isNullOrEmpty()) {
				totalAnimatedEmojiCount = aspans.size

				for (aspan in aspans) {
					aspan.replaceFontMetrics(Theme.chat_msgTextPaint.fontMetricsInt, (Theme.chat_msgTextPaint.textSize + AndroidUtilities.dp(4f)).toInt(), -1)
					aspan.full = false
				}
			}
			else {
				totalAnimatedEmojiCount = 0
			}
		}
	}

	private fun getStringFrom(reactions: ChatReactions?): CharSequence {
		if (reactions is TLChatReactionsAll) {
			return ApplicationLoader.applicationContext.getString(R.string.AllReactions)
		}

		if (reactions is TLChatReactionsSome) {
			val spannableStringBuilder = SpannableStringBuilder()

			for (i in reactions.reactions.indices) {
				if (i != 0) {
					spannableStringBuilder.append(", ")
				}

				spannableStringBuilder.append(ReactionsUtils.reactionToCharSequence(reactions.reactions[i]))
			}
		}

		return ApplicationLoader.applicationContext.getString(R.string.NoReactions)
	}

	private fun getUserName(`object`: TLObject?, entities: MutableList<MessageEntity>, offset: Int): String {
		val name: String
		val username: String?
		val id: Long

		when (`object`) {
			null -> {
				name = ""
				username = null
				id = 0
			}

			is User -> {
				name = if ((`object` as? TLRPC.TLUser)?.deleted == true) {
					ApplicationLoader.applicationContext.getString(R.string.HiddenName)
				}
				else {
					ContactsController.formatName(`object`.firstName, `object`.lastName)
				}

				username = `object`.username
				id = `object`.id
			}

			else -> {
				val chat = `object` as Chat
				name = chat.title ?: ""
				username = chat.username
				id = -chat.id
			}
		}

		if (offset >= 0) {
			val entity = TLMessageEntityMentionName()
			entity.userId = id
			entity.offset = offset
			entity.length = name.length

			entities.add(entity)
		}

		if (!username.isNullOrEmpty()) {
			if (offset >= 0) {
				val entity = TLMessageEntityMentionName()
				entity.userId = id
				entity.offset = offset + name.length + 2
				entity.length = username.length + 1

				entities.add(entity)
			}

			return String.format("%1\$s (@%2\$s)", name, username)
		}

		return name
	}

	@JvmOverloads
	fun applyNewText(text: CharSequence? = (messageOwner as? TLMessage)?.message) {
		if (text.isNullOrEmpty()) {
			return
		}

		messageText = text

		val paint = if (getMedia(messageOwner) is TLMessageMediaGame) {
			Theme.chat_msgGameTextPaint
		}
		else {
			Theme.chat_msgTextPaint
		}

		val emojiOnly = if (allowsBigEmoji()) IntArray(1) else null
		messageText = Emoji.replaceEmoji(messageText, paint.fontMetricsInt, false, emojiOnly)
		messageText = replaceAnimatedEmoji(messageText, (messageOwner as? TLMessage)?.entities, paint.fontMetricsInt)

		if (emojiOnly != null && emojiOnly[0] > 1) {
			replaceEmojiToLottieFrame(messageText, emojiOnly)
		}

		checkEmojiOnly(emojiOnly)
		generateLayout()
		setType()
	}

	private fun allowsBigEmoji(): Boolean {
		if (!SharedConfig.allowBigEmoji) {
			return false
		}

		if (messageOwner == null || messageOwner?.peerId == null || messageOwner?.peerId?.channelId == 0L && messageOwner?.peerId?.chatId == 0L) {
			return true
		}

		val chat = MessagesController.getInstance(currentAccount).getChat(if (messageOwner?.peerId?.channelId != 0L) messageOwner?.peerId?.channelId else messageOwner?.peerId?.chatId)

		return chat != null && chat.gigagroup || (!ChatObject.isActionBanned(chat, ChatObject.ACTION_SEND_STICKERS) || ChatObject.hasAdminRights(chat))
	}

	fun generateGameMessageText(fromUser: User?) {
		@Suppress("NAME_SHADOWING") var fromUser = fromUser

		if (fromUser == null && isFromUser) {
			fromUser = MessagesController.getInstance(currentAccount).getUser(messageOwner?.fromId?.userId)
		}

		var game: TLGame? = null

		if (replyMessageObject != null && getMedia(messageOwner)?.game != null) {
			game = getMedia(messageOwner)?.game
		}

		val score = (messageOwner?.action as? TLRPC.TLMessageActionGameScore)?.score ?: 0

		if (game == null) {
			messageText = if (fromUser != null && fromUser.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
				LocaleController.formatString("ActionYouScored", R.string.ActionYouScored, LocaleController.formatPluralString("Points", score))
			}
			else {
				replaceWithLink(LocaleController.formatString("ActionUserScored", R.string.ActionUserScored, LocaleController.formatPluralString("Points", score)), "un1", fromUser)
			}
		}
		else {
			messageText = if (fromUser != null && fromUser.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
				LocaleController.formatString("ActionYouScoredInGame", R.string.ActionYouScoredInGame, LocaleController.formatPluralString("Points", score))
			}
			else {
				replaceWithLink(LocaleController.formatString("ActionUserScoredInGame", R.string.ActionUserScoredInGame, LocaleController.formatPluralString("Points", score)), "un1", fromUser)
			}

			messageText = replaceWithLink(messageText, "un2", game)
		}
	}

	fun hasValidReplyMessageObject(): Boolean {
		return !(replyMessageObject == null || replyMessageObject?.messageOwner is TLMessageEmpty || replyMessageObject?.messageOwner?.action is TLMessageActionHistoryClear)
	}

	fun generatePaymentSentMessageText(fromUser: User?) {
		@Suppress("NAME_SHADOWING") var fromUser = fromUser

		if (fromUser == null) {
			fromUser = MessagesController.getInstance(currentAccount).getUser(dialogId)
		}

		val name = if (fromUser != null) {
			UserObject.getFirstName(fromUser)
		}
		else {
			""
		}

		var currency: String?

		try {
			currency = LocaleController.getInstance().formatCurrencyString(messageOwner?.action?.totalAmount ?: 0L, messageOwner?.action?.currency ?: "")
		}
		catch (e: Exception) {
			currency = "<error>"
			FileLog.e(e)
		}

		messageText = if (replyMessageObject != null && getMedia(messageOwner) is TLMessageMediaInvoice) {
			if (messageOwner?.action?.recurringInit == true) {
				LocaleController.formatString(R.string.PaymentSuccessfullyPaidRecurrent, currency, name, getMedia(messageOwner)?.title)
			}
			else {
				LocaleController.formatString("PaymentSuccessfullyPaid", R.string.PaymentSuccessfullyPaid, currency, name, getMedia(messageOwner)?.title)
			}
		}
		else {
			if (messageOwner?.action?.recurringInit == true) {
				LocaleController.formatString(R.string.PaymentSuccessfullyPaidNoItemRecurrent, currency, name)
			}
			else {
				LocaleController.formatString("PaymentSuccessfullyPaidNoItem", R.string.PaymentSuccessfullyPaidNoItem, currency, name)
			}
		}
	}

	fun generatePinMessageText(fromUser: User?, chat: Chat?) {
		@Suppress("NAME_SHADOWING") var fromUser = fromUser
		@Suppress("NAME_SHADOWING") var chat = chat

		if (fromUser == null && chat == null) {
			if (isFromUser) {
				fromUser = MessagesController.getInstance(currentAccount).getUser(messageOwner?.fromId?.userId)
			}

			if (fromUser == null) {
				if (messageOwner?.peerId is TLPeerChannel) {
					chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.peerId?.channelId)
				}
				else if (messageOwner?.peerId is TLPeerChat) {
					chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.peerId?.chatId)
				}
			}
		}

		val context = ApplicationLoader.applicationContext
		val replyMessageObject = replyMessageObject

		if (replyMessageObject == null || replyMessageObject.messageOwner is TLMessageEmpty || replyMessageObject.messageOwner?.action is TLMessageActionHistoryClear) {
			messageText = replaceWithLink(context.getString(R.string.ActionPinnedNoText), "un1", fromUser ?: chat)
		}
		else {
			if (replyMessageObject.isMusic) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedMusic), "un1", fromUser ?: chat)
			}
			else if (replyMessageObject.isVideo) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedVideo), "un1", fromUser ?: chat)
			}
			else if (replyMessageObject.isGif) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedGif), "un1", fromUser ?: chat)
			}
			else if (replyMessageObject.isVoice) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedVoice), "un1", fromUser ?: chat)
			}
			else if (replyMessageObject.isRoundVideo) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedRound), "un1", fromUser ?: chat)
			}
			else if ((replyMessageObject.isSticker || replyMessageObject.isAnimatedSticker) && !replyMessageObject.isAnimatedEmoji) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedSticker), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TLMessageMediaDocument) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedFile), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TLMessageMediaGeo) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedGeo), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TLMessageMediaGeoLive) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedGeoLive), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TLMessageMediaContact) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedContact), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TLMessageMediaPoll) {
				messageText = if ((getMedia(messageOwner) as? TLMessageMediaPoll?)?.poll?.quiz == true) {
					replaceWithLink(context.getString(R.string.ActionPinnedQuiz), "un1", fromUser ?: chat)
				}
				else {
					replaceWithLink(context.getString(R.string.ActionPinnedPoll), "un1", fromUser ?: chat)
				}
			}
			else if (getMedia(messageOwner) is TLMessageMediaPhoto) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedPhoto), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TLMessageMediaGame) {
				messageText = replaceWithLink(LocaleController.formatString("ActionPinnedGame", R.string.ActionPinnedGame, "\uD83C\uDFAE " + getMedia(messageOwner)?.game?.title), "un1", fromUser ?: chat)
				messageText = Emoji.replaceEmoji(messageText, Theme.chat_msgTextPaint.fontMetricsInt, false)
			}
			else if (!replyMessageObject.messageText.isNullOrEmpty()) {
				var mess = AnimatedEmojiSpan.cloneSpans(replyMessageObject.messageText)
				var ellipsize = false

				if ((mess?.length ?: 0) > 20) {
					mess = mess?.subSequence(0, 20)
					ellipsize = true
				}

				mess = Emoji.replaceEmoji(mess, Theme.chat_msgTextPaint.fontMetricsInt, false)

				if (replyMessageObject.messageOwner != null) {
					mess = replaceAnimatedEmoji(mess, (replyMessageObject.messageOwner as? TLMessage)?.entities, Theme.chat_msgTextPaint.fontMetricsInt)
				}

				MediaDataController.addTextStyleRuns(replyMessageObject, mess as? Spannable ?: SpannableString(""))

				if (ellipsize) {
					if (mess is SpannableStringBuilder) {
						mess.append("...")
					}
					else if (mess != null) {
						mess = SpannableStringBuilder(mess).append("...")
					}
				}

				messageText = replaceWithLink(AndroidUtilities.formatSpannable(context.getString(R.string.ActionPinnedText), mess), "un1", fromUser ?: chat)
			}
			else {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedNoText), "un1", fromUser ?: chat)
			}
		}
	}

	fun hasReactions(): Boolean {
		return !(messageOwner as? TLMessage)?.reactions?.results.isNullOrEmpty()
	}

	fun loadAnimatedEmojiDocument() {
		if (emojiAnimatedSticker != null || emojiAnimatedStickerId == null || emojiAnimatedStickerLoading) {
			return
		}

		emojiAnimatedStickerLoading = true

		AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).fetchDocument(emojiAnimatedStickerId!!) { document ->
			AndroidUtilities.runOnUIThread {
				this.emojiAnimatedSticker = document
				NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.animatedEmojiDocumentLoaded, this)
			}
		}
	}

	val isPollClosed: Boolean
		get() {
			if (type != TYPE_POLL) {
				return false
			}

			return (getMedia(messageOwner) as? TLMessageMediaPoll)?.poll?.closed == true
		}

	val isQuiz: Boolean
		get() {
			if (type != TYPE_POLL) {
				return false
			}

			return (getMedia(messageOwner) as? TLMessageMediaPoll)?.poll?.quiz == true
		}

	val isPublicPoll: Boolean
		get() {
			if (type != TYPE_POLL) {
				return false
			}

			return (getMedia(messageOwner) as? TLMessageMediaPoll)?.poll?.publicVoters == true
		}

	val isPoll: Boolean
		get() = type == TYPE_POLL

	fun canUnvote(): Boolean {
		if (type != TYPE_POLL) {
			return false
		}

		val mediaPoll = getMedia(messageOwner) as? TLMessageMediaPoll

		if (mediaPoll?.results == null || mediaPoll.results?.results.isNullOrEmpty() || mediaPoll.poll?.quiz == true) {
			return false
		}

		for (answer in mediaPoll.results!!.results) {
			if (answer.chosen) {
				return true
			}
		}

		return false
	}

	val isVoted: Boolean
		get() {
			if (type != TYPE_POLL) {
				return false
			}

			val mediaPoll = getMedia(messageOwner) as? TLMessageMediaPoll

			if (mediaPoll?.results == null || mediaPoll.results?.results.isNullOrEmpty()) {
				return false
			}

			for (answer in mediaPoll.results!!.results) {
				if (answer.chosen) {
					return true
				}
			}

			return false
		}

	val isSponsored: Boolean
		get() = sponsoredId != null

	val pollId: Long
		get() {
			if (type != TYPE_POLL) {
				return 0L
			}

			return (getMedia(messageOwner) as? TLMessageMediaPoll)?.poll?.id ?: 0L
		}

	private fun getPhotoWithId(webPage: WebPage?, id: Long): TLRPC.Photo? {
		contract {
			returnsNotNull() implies (webPage != null)
		}

		@Suppress("NAME_SHADOWING") val webPage = webPage as? TLWebPage

		if (webPage?.cachedPage == null) {
			return null
		}

		if (webPage.photo?.id == id) {
			return webPage.photo
		}

		return webPage.cachedPage?.photos?.firstOrNull { it.id == id }
	}

	private fun getDocumentWithId(webPage: WebPage?, id: Long): TLRPC.Document? {
		contract {
			returnsNotNull() implies (webPage != null)
		}

		@Suppress("NAME_SHADOWING") val webPage = webPage as? TLWebPage

		if (webPage?.cachedPage == null) {
			return null
		}

		if (webPage.document?.id == id) {
			return webPage.document
		}

		return webPage.cachedPage?.documents?.firstOrNull { it.id == id }
	}

	val isSupergroup: Boolean
		get() {
			if (localSupergroup) {
				return true
			}

			cachedIsSupergroup?.let {
				return it
			}

			if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) {
				val chat = getChat(null, null, messageOwner?.peerId?.channelId)
				return chat?.megagroup?.also { cachedIsSupergroup = it } ?: false
			}
			else {
				cachedIsSupergroup = false
			}

			return false
		}

	private fun getMessageObjectForBlock(webPage: WebPage, pageBlock: PageBlock): MessageObject {
		var message: TLMessage? = null

		if (pageBlock is TLPageBlockPhoto) {
			val photo = getPhotoWithId(webPage, pageBlock.photoId)

			if (photo === (webPage as? TLWebPage)?.photo) {
				return this
			}

			message = TLMessage()
			message.media = TLMessageMediaPhoto().also { it.photo = photo }
		}
		else if (pageBlock is TLPageBlockVideo) {
			val document = getDocumentWithId(webPage, pageBlock.videoId)

			if (document === (webPage as? TLWebPage)?.document) {
				return this
			}

			message = TLMessage()
			message.media = TLMessageMediaDocument().also { it.document = getDocumentWithId(webPage, pageBlock.videoId) }
		}

		message?.message = ""
		message?.realId = id
		message?.id = Utilities.random.nextInt()
		message?.date = messageOwner?.date ?: 0
		message?.peerId = messageOwner?.peerId
		message?.out = messageOwner?.out ?: false
		message?.fromId = messageOwner?.fromId

		return MessageObject(currentAccount, message!!, generateLayout = false, checkMediaExists = true)
	}

	fun getWebPagePhotos(array: ArrayList<MessageObject>?, blocksToSearch: List<PageBlock>?): ArrayList<MessageObject> {
		val messageObjects = array ?: ArrayList()
		val media = getMedia(messageOwner)
		val webPage = (((media as? TLMessageMediaWebPage)?.webpage) as? TLWebPage) ?: return messageObjects

		if (webPage.cachedPage == null) {
			return messageObjects
		}

		val blocks = blocksToSearch ?: webPage.cachedPage?.blocks

		blocks?.forEach { block ->
			if (block is TLPageBlockSlideshow) {
				for (blockItem in block.items) {
					messageObjects.add(getMessageObjectForBlock(webPage, blockItem))
				}
			}
			else if (block is TLPageBlockCollage) {
				for (blockItem in block.items) {
					messageObjects.add(getMessageObjectForBlock(webPage, blockItem))
				}
			}
		}

		return messageObjects
	}

	fun createMessageSendInfo() {
		val messageOwner = messageOwner as? TLMessage
		val params = messageOwner?.params

		if (messageOwner?.message != null && (messageOwner.id < 0 || isEditing) && params != null) {
			var param: String?

			if ((params["ve"].also { param = it }) != null && (isVideo || isNewGif || isRoundVideo)) {
				videoEditedInfo = VideoEditedInfo()

				if (!videoEditedInfo!!.parseString(param)) {
					videoEditedInfo = null
				}
				else {
					videoEditedInfo!!.roundVideo = isRoundVideo
				}
			}

			if (messageOwner.sendState == MESSAGE_SEND_STATE_EDITING && (params["prevMedia"].also { param = it }) != null) {
				val serializedData = SerializedData(Base64.decode(param, Base64.DEFAULT))
				var constructor = serializedData.readInt32(false)

				previousMedia = MessageMedia.deserialize(serializedData, constructor, false)
				previousMessage = serializedData.readString(false)
				previousAttachPath = serializedData.readString(false)

				val count = serializedData.readInt32(false)

				previousMessageEntities = ArrayList(count)

				for (a in 0 until count) {
					constructor = serializedData.readInt32(false)

					val entity = MessageEntity.deserialize(serializedData, constructor, false)

					if (entity != null) {
						previousMessageEntities?.add(entity)
					}
				}

				serializedData.cleanup()
			}
		}
	}

	fun measureInlineBotButtons() {
		if (isRestrictedMessage) {
			return
		}

		wantedBotKeyboardWidth = 0

		if (messageOwner?.replyMarkup is TLReplyInlineMarkup && !hasExtendedMedia() || messageOwner?.reactions != null && !messageOwner?.reactions?.results.isNullOrEmpty()) {
			Theme.createCommonMessageResources()

			if (botButtonsLayout == null) {
				botButtonsLayout = StringBuilder()
			}
			else {
				botButtonsLayout?.setLength(0)
			}
		}

		val replyMarkup = messageOwner?.replyMarkup

		if (replyMarkup is TLReplyInlineMarkup && !hasExtendedMedia()) {
			val context = ApplicationLoader.applicationContext

			for (a in replyMarkup.rows.indices) {
				val row = replyMarkup.rows[a]
				var maxButtonSize = 0
				val size = row.buttons.size

				for (b in 0 until size) {
					val button = row.buttons[b]

					botButtonsLayout?.append(a)?.append(b)

					var text: CharSequence?

					if (button is TLKeyboardButtonBuy && (getMedia(messageOwner)!!.flags and 4) != 0) {
						text = context.getString(R.string.PaymentReceipt)
					}
					else {
						var str = button.text

						if (str == null) {
							str = ""
						}

						text = Emoji.replaceEmoji(str, Theme.chat_msgBotButtonPaint.fontMetricsInt, false)
					}

					val staticLayout = StaticLayout(text, Theme.chat_msgBotButtonPaint, AndroidUtilities.dp(2000f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

					if (staticLayout.lineCount > 0) {
						var width = staticLayout.getLineWidth(0)
						val left = staticLayout.getLineLeft(0)

						if (left < width) {
							width -= left
						}

						maxButtonSize = max(maxButtonSize.toDouble(), (ceil(width.toDouble()).toInt() + AndroidUtilities.dp(4f)).toDouble()).toInt()
					}
				}

				wantedBotKeyboardWidth = max(wantedBotKeyboardWidth.toDouble(), ((maxButtonSize + AndroidUtilities.dp(12f)) * size + AndroidUtilities.dp(5f) * (size - 1)).toDouble()).toInt()
			}
		}
		else if (messageOwner?.reactions != null) {
			val reactions = messageOwner!!.reactions!!

			val size = reactions.results.size

			for (a in 0 until size) {
				val reactionCount = reactions.results[a]
				var maxButtonSize = 0

				botButtonsLayout?.append(0)?.append(a)

				val text = Emoji.replaceEmoji(String.format(Locale.getDefault(), "%d %s", reactionCount.count, reactionCount.reaction), Theme.chat_msgBotButtonPaint.fontMetricsInt, false)
				val staticLayout = StaticLayout(text, Theme.chat_msgBotButtonPaint, AndroidUtilities.dp(2000f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				if (staticLayout.lineCount > 0) {
					var width = staticLayout.getLineWidth(0)
					val left = staticLayout.getLineLeft(0)

					if (left < width) {
						width -= left
					}

					maxButtonSize = max(maxButtonSize.toDouble(), (ceil(width.toDouble()).toInt() + AndroidUtilities.dp(4f)).toDouble()).toInt()
				}

				wantedBotKeyboardWidth = max(wantedBotKeyboardWidth.toDouble(), ((maxButtonSize + AndroidUtilities.dp(12f)) * size + AndroidUtilities.dp(5f) * (size - 1)).toDouble()).toInt()
			}
		}
	}

	val isVideoAvatar: Boolean
		get() = !(messageOwner?.action?.photo as? TLPhoto)?.videoSizes.isNullOrEmpty()

	val isFcmMessage: Boolean
		get() = localType != 0

	private fun getUser(users: AbstractMap<Long, User>?, sUsers: LongSparseArray<User>?, userId: Long): User? {
		var user: User? = null

		if (users != null) {
			user = users[userId]
		}
		else if (sUsers != null) {
			user = sUsers[userId]
		}

		if (user == null) {
			user = MessagesController.getInstance(currentAccount).getUser(userId)
		}

		return user
	}

	private fun getChat(chats: AbstractMap<Long, Chat>?, sChats: LongSparseArray<Chat>?, chatId: Long?): Chat? {
		if (chatId == null) {
			return null
		}

		var chat: Chat? = null

		if (chats != null) {
			chat = chats[chatId]
		}
		else if (sChats != null) {
			chat = sChats[chatId]
		}

		if (chat == null) {
			chat = MessagesController.getInstance(currentAccount).getChat(chatId)
		}

		return chat
	}

	private fun updateMessageText(users: AbstractMap<Long, User>?, chats: AbstractMap<Long, Chat>?, sUsers: LongSparseArray<User>?, sChats: LongSparseArray<Chat>?) {
		var fromUser: User? = null
		var fromChat: Chat? = null

		if (messageOwner?.fromId is TLPeerUser) {
			fromUser = getUser(users, sUsers, messageOwner?.fromId?.userId ?: 0L)
		}
		else if (messageOwner?.fromId is TLPeerChannel) {
			fromChat = getChat(chats, sChats, messageOwner?.fromId?.channelId)
		}

		val fromObject = fromUser ?: fromChat
		val context = ApplicationLoader.applicationContext

		if (messageOwner is TLMessageService) {
			when (val action = messageOwner?.action) {
				is TLMessageActionGroupCallScheduled -> {
					messageText = if (messageOwner?.peerId is TLPeerChat || isSupergroup) {
						LocaleController.formatString("ActionGroupCallScheduled", R.string.ActionGroupCallScheduled, LocaleController.formatStartsTime(action.scheduleDate.toLong(), 3, false))
					}
					else {
						LocaleController.formatString("ActionChannelCallScheduled", R.string.ActionChannelCallScheduled, LocaleController.formatStartsTime(action.scheduleDate.toLong(), 3, false))
					}
				}

				is TLMessageActionGroupCall -> {
					if (action.duration != 0) {
						val time: String
						val days = action.duration / (3600 * 24)

						if (days > 0) {
							time = LocaleController.formatPluralString("Days", days)
						}
						else {
							val hours = duration / 3600

							if (hours > 0) {
								time = LocaleController.formatPluralString("Hours", hours)
							}
							else {
								val minutes = action.duration / 60

								time = if (minutes > 0) {
									LocaleController.formatPluralString("Minutes", minutes)
								}
								else {
									LocaleController.formatPluralString("Seconds", action.duration)
								}
							}
						}

						messageText = if (messageOwner?.peerId is TLPeerChat || isSupergroup) {
							if (isOut) {
								LocaleController.formatString("ActionGroupCallEndedByYou", R.string.ActionGroupCallEndedByYou, time)
							}
							else {
								replaceWithLink(LocaleController.formatString("ActionGroupCallEndedBy", R.string.ActionGroupCallEndedBy, time), "un1", fromObject)
							}
						}
						else {
							LocaleController.formatString("ActionChannelCallEnded", R.string.ActionChannelCallEnded, time)
						}
					}
					else {
						messageText = if (messageOwner?.peerId is TLPeerChat || isSupergroup) {
							if (isOut) {
								context.getString(R.string.ActionGroupCallStartedByYou)
							}
							else {
								replaceWithLink(context.getString(R.string.ActionGroupCallStarted), "un1", fromObject)
							}
						}
						else {
							context.getString(R.string.ActionChannelCallJustStarted)
						}
					}
				}

				is TLMessageActionInviteToGroupCall -> {
					var singleUserId = action.userId

					if (singleUserId == 0L && action.users.size == 1) {
						singleUserId = action.users[0]
					}

					if (singleUserId != 0L) {
						val whoUser = getUser(users, sUsers, singleUserId)

						if (isOut) {
							messageText = replaceWithLink(context.getString(R.string.ActionGroupCallYouInvited), "un2", whoUser)
						}
						else if (singleUserId == UserConfig.getInstance(currentAccount).getClientUserId()) {
							messageText = replaceWithLink(context.getString(R.string.ActionGroupCallInvitedYou), "un1", fromObject)
						}
						else {
							messageText = replaceWithLink(context.getString(R.string.ActionGroupCallInvited), "un2", whoUser)
							messageText = replaceWithLink(messageText, "un1", fromObject)
						}
					}
					else {
						if (isOut) {
							messageText = replaceWithLink(context.getString(R.string.ActionGroupCallYouInvited), "un2", action.users, users, sUsers)
						}
						else {
							messageText = replaceWithLink(context.getString(R.string.ActionGroupCallInvited), "un2", action.users, users, sUsers)
							messageText = replaceWithLink(messageText, "un1", fromObject)
						}
					}
				}

				is TLMessageActionGeoProximityReached -> {
					val fromId = getPeerId(action.fromId)

					val from = if (fromId > 0) {
						getUser(users, sUsers, fromId)
					}
					else {
						getChat(chats, sChats, -fromId)
					}

					val toId = getPeerId(action.toId)
					val selfUserId = UserConfig.getInstance(currentAccount).getClientUserId()

					if (toId == selfUserId) {
						messageText = replaceWithLink(LocaleController.formatString("ActionUserWithinRadius", R.string.ActionUserWithinRadius, LocaleController.formatDistance(action.distance.toFloat(), 2)), "un1", from)
					}
					else {
						val to = if (toId > 0) {
							getUser(users, sUsers, toId)
						}
						else {
							getChat(chats, sChats, -toId)
						}

						if (fromId == selfUserId) {
							messageText = replaceWithLink(LocaleController.formatString("ActionUserWithinYouRadius", R.string.ActionUserWithinYouRadius, LocaleController.formatDistance(action.distance.toFloat(), 2)), "un1", to)
						}
						else {
							messageText = replaceWithLink(LocaleController.formatString("ActionUserWithinOtherRadius", R.string.ActionUserWithinOtherRadius, LocaleController.formatDistance(action.distance.toFloat(), 2)), "un2", to)
							messageText = replaceWithLink(messageText, "un1", from)
						}
					}
				}

				is TLMessageActionCustomAction -> {
					messageText = action.message
				}

				is TLMessageActionChatCreate -> {
					messageText = if (isOut) {
						context.getString(R.string.ActionYouCreateGroup)
					}
					else {
						replaceWithLink(context.getString(R.string.ActionCreateGroup), "un1", fromObject)
					}
				}

				is TLMessageActionChatDeleteUser -> {
					if (isFromUser && action.userId == messageOwner?.fromId?.userId) {
						messageText = if (isOut) {
							context.getString(R.string.ActionYouLeftUser)
						}
						else {
							replaceWithLink(context.getString(R.string.ActionLeftUser), "un1", fromObject)
						}
					}
					else {
						val whoUser = getUser(users, sUsers, action.userId)

						if (isOut) {
							messageText = replaceWithLink(context.getString(R.string.ActionYouKickUser), "un2", whoUser)
						}
						else if (action.userId == UserConfig.getInstance(currentAccount).getClientUserId()) {
							messageText = replaceWithLink(context.getString(R.string.ActionKickUserYou), "un1", fromObject)
						}
						else {
							messageText = replaceWithLink(context.getString(R.string.ActionKickUser), "un2", whoUser)
							messageText = replaceWithLink(messageText, "un1", fromObject)
						}
					}
				}

				is TLMessageActionChatAddUser -> {
					var singleUserId = action.userId

					if (singleUserId == 0L && action.users.size == 1) {
						singleUserId = action.users[0]
					}

					if (singleUserId != 0L) {
						val whoUser = getUser(users, sUsers, singleUserId)
						var chat: Chat? = null

						if (messageOwner?.peerId?.channelId != 0L) {
							chat = getChat(chats, sChats, messageOwner?.peerId?.channelId)
						}

						if (messageOwner?.fromId != null && singleUserId == messageOwner?.fromId?.userId) {
							messageText = if (ChatObject.isChannel(chat) && !chat.megagroup) {
								context.getString(R.string.ChannelJoined)
							}
							else {
								if (messageOwner?.peerId?.channelId != 0L) {
									if (singleUserId == UserConfig.getInstance(currentAccount).getClientUserId()) {
										context.getString(R.string.ChannelMegaJoined)
									}
									else {
										replaceWithLink(context.getString(R.string.ActionAddUserSelfMega), "un1", fromObject)
									}
								}
								else if (isOut) {
									context.getString(R.string.ActionAddUserSelfYou)
								}
								else {
									replaceWithLink(context.getString(R.string.ActionAddUserSelf), "un1", fromObject)
								}
							}
						}
						else {
							if (isOut) {
								messageText = replaceWithLink(context.getString(R.string.ActionYouAddUser), "un2", whoUser)
							}
							else if (singleUserId == UserConfig.getInstance(currentAccount).getClientUserId()) {
								messageText = if (messageOwner?.peerId?.channelId != 0L) {
									if (chat != null && chat.megagroup) {
										replaceWithLink(context.getString(R.string.MegaAddedBy), "un1", fromObject)
									}
									else {
										replaceWithLink(context.getString(R.string.ChannelAddedBy), "un1", fromObject)
									}
								}
								else {
									replaceWithLink(context.getString(R.string.ActionAddUserYou), "un1", fromObject)
								}
							}
							else {
								messageText = replaceWithLink(context.getString(R.string.ActionAddUser), "un2", whoUser)
								messageText = replaceWithLink(messageText, "un1", fromObject)
							}
						}
					}
					else {
						if (isOut) {
							messageText = replaceWithLink(context.getString(R.string.ActionYouAddUser), "un2", action.users, users, sUsers)
						}
						else {
							messageText = replaceWithLink(context.getString(R.string.ActionAddUser), "un2", action.users, users, sUsers)
							messageText = replaceWithLink(messageText, "un1", fromObject)
						}
					}
				}

				is TLMessageActionChatJoinedByLink -> {
					messageText = if (isOut) {
						context.getString(R.string.ActionInviteYou)
					}
					else {
						replaceWithLink(context.getString(R.string.ActionInviteUser), "un1", fromObject)
					}
				}

				is TLMessageActionGiftPremium -> {
					if (fromObject is TLRPC.TLUser && fromObject.isSelf) {
						val user = getUser(users, sUsers, messageOwner?.peerId?.userId ?: 0L)
						messageText = replaceWithLink(AndroidUtilities.replaceTags(context.getString(R.string.ActionGiftOutbound)), "un1", user)
					}
					else {
						messageText = replaceWithLink(AndroidUtilities.replaceTags(context.getString(R.string.ActionGiftInbound)), "un1", fromObject)
					}

					val i = messageText?.toString()?.indexOf("un2") ?: -1

					if (i != -1) {
						val sb = SpannableStringBuilder.valueOf(messageText)

						messageText = sb.replace(i, i + 3, BillingController.getInstance().formatCurrency(action.amount, action.currency))
					}
				}

				is TLMessageActionChatEditPhoto -> {
					val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(chats, sChats, messageOwner?.peerId?.channelId) else null

					messageText = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						if (isVideoAvatar) {
							context.getString(R.string.ActionChannelChangedVideo)
						}
						else {
							if (ChatObject.isMasterclass(chat)) {
								context.getString(R.string.ActionMasterclassChangedPhoto)
							}
							else {
								context.getString(R.string.ActionChannelChangedPhoto)
							}
						}
					}
					else {
						if (isOut) {
							if (isVideoAvatar) {
								context.getString(R.string.ActionYouChangedVideo)
							}
							else {
								context.getString(R.string.ActionYouChangedPhoto)
							}
						}
						else {
							if (isVideoAvatar) {
								replaceWithLink(context.getString(R.string.ActionChangedVideo), "un1", fromObject)
							}
							else {
								replaceWithLink(context.getString(R.string.ActionChangedPhoto), "un1", fromObject)
							}
						}
					}
				}

				is TLMessageActionChatEditTitle -> {
					val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(chats, sChats, messageOwner?.peerId?.channelId) else null

					messageText = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						if (ChatObject.isMasterclass(chat)) {
							context.getString(R.string.ActionMasterclassChangedTitle).replace("un2", action.title ?: "")
						}
						else {
							context.getString(R.string.ActionChannelChangedTitle).replace("un2", action.title ?: "")
						}
					}
					else {
						if (isOut) {
							context.getString(R.string.ActionYouChangedTitle).replace("un2", action.title ?: "")
						}
						else {
							replaceWithLink(context.getString(R.string.ActionChangedTitle).replace("un2", action.title ?: ""), "un1", fromObject)
						}
					}
				}

				is TLMessageActionChatDeletePhoto -> {
					val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(chats, sChats, messageOwner?.peerId?.channelId) else null

					messageText = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						if (ChatObject.isMasterclass(chat)) {
							context.getString(R.string.ActionMasterclassRemovedPhoto)
						}
						else {
							context.getString(R.string.ActionChannelRemovedPhoto)
						}
					}
					else {
						if (isOut) {
							context.getString(R.string.ActionYouRemovedPhoto)
						}
						else {
							replaceWithLink(context.getString(R.string.ActionRemovedPhoto), "un1", fromObject)
						}
					}
				}

//				is TLMessageActionTTLChange -> {
//					messageText = if (action.ttl != 0) {
//						if (isOut) {
//							LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, LocaleController.formatTTLString(action.ttl))
//						}
//						else {
//							LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, UserObject.getFirstName(fromUser), LocaleController.formatTTLString(action.ttl))
//						}
//					}
//					else {
//						if (isOut) {
//							context.getString(R.string.MessageLifetimeYouRemoved)
//						}
//						else {
//							LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, UserObject.getFirstName(fromUser))
//						}
//					}
//				}

				is TLMessageActionSetMessagesTTL -> {
					val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(chats, sChats, messageOwner?.peerId?.channelId) else null

					messageText = if (chat != null && !chat.megagroup) {
						if (action.period != 0) {
							LocaleController.formatString("ActionTTLChannelChanged", R.string.ActionTTLChannelChanged, LocaleController.formatTTLString(action.period))
						}
						else {
							context.getString(R.string.ActionTTLChannelDisabled)
						}
					}
					else if (action.period != 0) {
						if (isOut) {
							LocaleController.formatString("ActionTTLYouChanged", R.string.ActionTTLYouChanged, LocaleController.formatTTLString(action.period))
						}
						else {
							replaceWithLink(LocaleController.formatString("ActionTTLChanged", R.string.ActionTTLChanged, LocaleController.formatTTLString(action.period)), "un1", fromObject)
						}
					}
					else {
						if (isOut) {
							context.getString(R.string.ActionTTLYouDisabled)
						}
						else {
							replaceWithLink(context.getString(R.string.ActionTTLDisabled), "un1", fromObject)
						}
					}
				}

//				is TLMessageActionLoginUnknownLocation -> {
//					val date: String
//					val time = ((messageOwner?.date ?: 0).toLong()) * 1000
//
//					date = if (LocaleController.getInstance().formatterDay != null && LocaleController.getInstance().formatterYear != null) {
//						LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(time), LocaleController.getInstance().formatterDay.format(time))
//					}
//					else {
//						"" + (messageOwner?.date ?: 0)
//					}
//
//					var toUser = UserConfig.getInstance(currentAccount).getCurrentUser()
//
//					if (toUser == null) {
//						toUser = getUser(users, sUsers, messageOwner?.peerId?.userId ?: 0L)
//					}
//
//					val name = if (toUser != null) UserObject.getFirstName(toUser) else ""
//
//					messageText = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, name, date, action.title, action.address)
//				}

				// is TLRPC.TLMessageActionUserJoined
				is TLMessageActionContactSignUp -> {
					messageText = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, UserObject.getUserName(fromUser))
				}

//				is TLMessageActionUserUpdatedPhoto -> {
//					messageText = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, UserObject.getUserName(fromUser))
//				}

//				is TLMessageEncryptedAction -> {
//					if (action.encryptedAction is TLDecryptedMessageActionScreenshotMessages) {
//						messageText = if (isOut) {
//							LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou)
//						}
//						else {
//							replaceWithLink(context.getString(R.string.ActionTakeScreenshoot), "un1", fromObject)
//						}
//					}
//					else if (action.encryptedAction is TLDecryptedMessageActionSetMessageTTL) {
//						@Suppress("NAME_SHADOWING") val action = action.encryptedAction as TLDecryptedMessageActionSetMessageTTL
//
//						messageText = if (action.ttlSeconds != 0) {
//							if (isOut) {
//								LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, LocaleController.formatTTLString(action.ttlSeconds))
//							}
//							else {
//								LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, UserObject.getFirstName(fromUser), LocaleController.formatTTLString(action.ttlSeconds))
//							}
//						}
//						else {
//							if (isOut) {
//								context.getString(R.string.MessageLifetimeYouRemoved)
//							}
//							else {
//								LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, UserObject.getFirstName(fromUser))
//							}
//						}
//					}
//				}

				is TLRPC.TLMessageActionScreenshotTaken -> {
					messageText = if (isOut) {
						LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou)
					}
					else {
						replaceWithLink(context.getString(R.string.ActionTakeScreenshoot), "un1", fromObject)
					}
				}

//				is TLRPC.TLMessageActionCreatedBroadcastList -> {
//					messageText = LocaleController.formatString("YouCreatedBroadcastList", R.string.YouCreatedBroadcastList)
//				}

				is TLRPC.TLMessageActionChannelCreate -> {
					val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(chats, sChats, messageOwner?.peerId?.channelId) else null

					messageText = if (ChatObject.isChannel(chat) && chat.megagroup) {
						context.getString(R.string.ActionCreateMega)
					}
					else if (ChatObject.isMasterclass(chat)) {
						context.getString(R.string.ActionCreateMasterclass)
					}
					else {
						context.getString(R.string.ActionCreateChannel)
					}
				}

				is TLRPC.TLMessageActionChatMigrateTo -> {
					messageText = context.getString(R.string.ActionMigrateFromGroup)
				}

				is TLRPC.TLMessageActionChannelMigrateFrom -> {
					messageText = context.getString(R.string.ActionMigrateFromGroup)
				}

				is TLRPC.TLMessageActionPinMessage -> {
					val chat = if (fromUser == null) {
						getChat(chats, sChats, messageOwner?.peerId?.channelId)
					}
					else {
						null
					}

					generatePinMessageText(fromUser, chat)
				}

				is TLMessageActionHistoryClear -> {
					messageText = context.getString(R.string.HistoryCleared)
				}

				is TLRPC.TLMessageActionGameScore -> {
					generateGameMessageText(fromUser)
				}

				is TLMessageActionPhoneCall -> {
					val isMissed = action.reason is TLPhoneCallDiscardReasonMissed

					messageText = if (isFromUser && messageOwner?.fromId?.userId == UserConfig.getInstance(currentAccount).getClientUserId()) {
						if (isMissed) {
							if (action.video) {
								context.getString(R.string.CallMessageVideoOutgoingMissed)
							}
							else {
								context.getString(R.string.CallMessageOutgoingMissed)
							}
						}
						else {
							if (action.video) {
								context.getString(R.string.CallMessageVideoOutgoing)
							}
							else {
								context.getString(R.string.CallMessageOutgoing)
							}
						}
					}
					else {
						if (isMissed) {
							if (action.video) {
								context.getString(R.string.CallMessageVideoIncomingMissed)
							}
							else {
								context.getString(R.string.CallMessageIncomingMissed)
							}
						}
						else if (action.reason is TLPhoneCallDiscardReasonBusy) {
							if (action.video) {
								context.getString(R.string.CallMessageVideoIncomingDeclined)
							}
							else {
								context.getString(R.string.CallMessageIncomingDeclined)
							}
						}
						else {
							if (action.video) {
								context.getString(R.string.CallMessageVideoIncoming)
							}
							else {
								context.getString(R.string.CallMessageIncoming)
							}
						}
					}

					if (action.duration > 0) {
						val duration = LocaleController.formatCallDuration(action.duration)

						messageText = LocaleController.formatString("CallMessageWithDuration", R.string.CallMessageWithDuration, messageText, duration)

						val _messageText = messageText?.toString() ?: ""

						var start = _messageText.indexOf(duration)

						if (start != -1) {
							val sp = SpannableString(messageText)

							var end = start + duration.length

							if (start > 0 && _messageText[start - 1] == '(') {
								start--
							}

							if (end < _messageText.length && _messageText[end] == ')') {
								end++
							}

							sp.setSpan(TypefaceSpan(Theme.TYPEFACE_DEFAULT), start, end, 0)

							messageText = sp
						}
					}
				}

				is TLRPC.TLMessageActionPaymentSent -> {
					val user = getUser(users, sUsers, dialogId)
					generatePaymentSentMessageText(user)
				}

				is TLMessageActionBotAllowed -> {
					val domain = action.domain ?: ""
					val text = context.getString(R.string.ActionBotAllowed)
					val start = text.indexOf("%1\$s")
					val str = SpannableString(String.format(text, domain))

					if (start >= 0) {
						str.setSpan(URLSpanNoUnderlineBold("http://$domain"), start, start + domain.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}

					messageText = str
				}

				is TLMessageActionSecureValuesSent -> {
					val str = StringBuilder()

					for (type in action.types) {
						if (str.isNotEmpty()) {
							str.append(", ")
						}

						when (type) {
							is TLSecureValueTypePhone -> {
								str.append(context.getString(R.string.ActionBotDocumentPhone))
							}

							is TLSecureValueTypeEmail -> {
								str.append(context.getString(R.string.ActionBotDocumentEmail))
							}

							is TLSecureValueTypeAddress -> {
								str.append(context.getString(R.string.ActionBotDocumentAddress))
							}

							is TLSecureValueTypePersonalDetails -> {
								str.append(context.getString(R.string.ActionBotDocumentIdentity))
							}

							is TLSecureValueTypePassport -> {
								str.append(context.getString(R.string.ActionBotDocumentPassport))
							}

							is TLSecureValueTypeDriverLicense -> {
								str.append(context.getString(R.string.ActionBotDocumentDriverLicence))
							}

							is TLSecureValueTypeIdentityCard -> {
								str.append(context.getString(R.string.ActionBotDocumentIdentityCard))
							}

							is TLSecureValueTypeUtilityBill -> {
								str.append(context.getString(R.string.ActionBotDocumentUtilityBill))
							}

							is TLSecureValueTypeBankStatement -> {
								str.append(context.getString(R.string.ActionBotDocumentBankStatement))
							}

							is TLSecureValueTypeRentalAgreement -> {
								str.append(context.getString(R.string.ActionBotDocumentRentalAgreement))
							}

							is TLSecureValueTypeInternalPassport -> {
								str.append(context.getString(R.string.ActionBotDocumentInternalPassport))
							}

							is TLSecureValueTypePassportRegistration -> {
								str.append(context.getString(R.string.ActionBotDocumentPassportRegistration))
							}

							is TLSecureValueTypeTemporaryRegistration -> {
								str.append(context.getString(R.string.ActionBotDocumentTemporaryRegistration))
							}
						}
					}

					var user: User? = null

					if (messageOwner?.peerId != null) {
						user = getUser(users, sUsers, messageOwner?.peerId?.userId ?: 0L)
					}

					messageText = LocaleController.formatString("ActionBotDocuments", R.string.ActionBotDocuments, UserObject.getFirstName(user), str.toString())
				}

				is TLMessageActionWebViewDataSent -> {
					messageText = LocaleController.formatString("ActionBotWebViewData", R.string.ActionBotWebViewData, action.text)
				}

				is TLMessageActionSetChatTheme -> {
					val emoticon = action.emoticon
					val userName = UserObject.getFirstName(fromUser)
					val isUserSelf = isUserSelf(fromUser)

					messageText = if (TextUtils.isEmpty(emoticon)) {
						if (isUserSelf) LocaleController.formatString("ChatThemeDisabledYou", R.string.ChatThemeDisabledYou) else LocaleController.formatString("ChatThemeDisabled", R.string.ChatThemeDisabled, userName, emoticon)
					}
					else {
						if (isUserSelf) LocaleController.formatString("ChatThemeChangedYou", R.string.ChatThemeChangedYou, emoticon) else LocaleController.formatString("ChatThemeChangedTo", R.string.ChatThemeChangedTo, userName, emoticon)
					}
				}

				is TLMessageActionChatJoinedByRequest -> {
					if (isUserSelf(fromUser)) {
						val isChannel = ChatObject.isChannelAndNotMegaGroup(messageOwner?.peerId?.channelId ?: 0L, currentAccount)
						messageText = if (isChannel) context.getString(R.string.RequestToJoinChannelApproved) else context.getString(R.string.RequestToJoinGroupApproved)
					}
					else {
						messageText = replaceWithLink(context.getString(R.string.UserAcceptedToGroupAction), "un1", fromObject)
					}
				}
			}
		}
		else {
			isRestrictedMessage = false

			val restrictionReason = MessagesController.getRestrictionReason((messageOwner as? TLMessage)?.restrictionReason)

			if (!restrictionReason.isNullOrEmpty()) {
				messageText = restrictionReason
				isRestrictedMessage = true
			}
			else if (!isMediaEmpty) {
				val media = getMedia(messageOwner)

				if (media is TLMessageMediaDice) {
					messageText = diceEmoji
				}
				else if (media is TLMessageMediaPoll) {
					messageText = if (media.poll?.quiz == true) {
						context.getString(R.string.QuizPoll)
					}
					else {
						context.getString(R.string.Poll)
					}
				}
				else if (media is TLMessageMediaPhoto) {
					messageText = if (media.ttlSeconds != 0) { // && messageOwner !is TLMessageSecret) {
						context.getString(R.string.AttachDestructingPhoto)
					}
					else {
						context.getString(R.string.AttachPhoto)
					}
				}
				else if (isVideo || media is TLMessageMediaDocument && document is TLDocumentEmpty && media.ttlSeconds != 0) {
					messageText = if (media?.ttlSeconds != 0) { // && messageOwner !is TLMessage_secret) {
						context.getString(R.string.AttachDestructingVideo)
					}
					else {
						context.getString(R.string.AttachVideo)
					}
				}
				else if (isVoice) {
					messageText = context.getString(R.string.AttachAudio)
				}
				else if (isRoundVideo) {
					messageText = context.getString(R.string.AttachRound)
				}
				else if (media is TLMessageMediaGeo || media is TLMessageMediaVenue) {
					messageText = context.getString(R.string.AttachLocation)
				}
				else if (media is TLMessageMediaGeoLive) {
					messageText = context.getString(R.string.AttachLiveLocation)
				}
				else if (media is TLMessageMediaContact) {
					messageText = context.getString(R.string.AttachContact)

					if (!media.vcard.isNullOrEmpty()) {
						vCardData = VCardData.parse(media.vcard)
					}
				}
				else if (media is TLMessageMediaGame) {
					messageText = messageOwner?.message
				}
				else if (media is TLMessageMediaInvoice) {
					messageText = media.description
				}
				else if (media is TLMessageMediaUnsupported) {
					messageText = context.getString(R.string.UnsupportedMedia)
				}
				else if (media is TLMessageMediaDocument) {
					if (isSticker || isAnimatedStickerDocument(document, true)) {
						val sch = stickerChar

						messageText = if (!sch.isNullOrEmpty()) {
							String.format("%s %s", sch, context.getString(R.string.AttachSticker))
						}
						else {
							context.getString(R.string.AttachSticker)
						}
					}
					else if (isMusic) {
						messageText = context.getString(R.string.AttachMusic)
					}
					else if (isGif) {
						messageText = context.getString(R.string.AttachGif)
					}
					else {
						val name = FileLoader.getDocumentFileName(document)

						messageText = if (!name.isNullOrEmpty()) {
							name
						}
						else {
							context.getString(R.string.AttachDocument)
						}
					}
				}
			}
			else {
				messageText = if (messageOwner?.message != null) {
					try {
						if ((messageOwner?.message?.length ?: 0) > 200) {
							AndroidUtilities.BAD_CHARS_MESSAGE_LONG_PATTERN.matcher(messageOwner?.message ?: "").replaceAll("\u200C")
						}
						else {
							AndroidUtilities.BAD_CHARS_MESSAGE_PATTERN.matcher(messageOwner?.message ?: "").replaceAll("\u200C")
						}
					}
					catch (e: Throwable) {
						messageOwner?.message
					}
				}
				else {
					null
				}
			}
		}

		if (messageText == null) {
			messageText = ""
		}
	}

	fun hasRevealedExtendedMedia(): Boolean {
		return messageOwner?.media?.extendedMedia is TLMessageExtendedMedia
	}

	fun hasExtendedMedia(): Boolean {
		return messageOwner?.media?.extendedMedia != null
	}

	fun hasExtendedMediaPreview(): Boolean {
		return messageOwner?.media?.extendedMedia is TLMessageExtendedMediaPreview
	}

	fun setType() {
		val oldType = type

		type = 1000
		isRoundVideoCached = 0

		if (messageOwner is TLMessage) {
			val media = getMedia(messageOwner)

			if (isRestrictedMessage) {
				type = TYPE_COMMON
			}
			else if (emojiAnimatedSticker != null || emojiAnimatedStickerId != null) {
				type = if (isSticker) {
					TYPE_STICKER
				}
				else {
					TYPE_ANIMATED_STICKER
				}
			}
			else if (!isDice && emojiOnlyCount >= 1 && !hasUnwrappedEmoji) {
				type = TYPE_EMOJIS
			}
			else if (isMediaEmpty) {
				type = TYPE_COMMON

				if (messageText.isNullOrEmpty() && eventId == 0L) {
					messageText = "Empty message"
				}
			}
			else if (hasExtendedMediaPreview()) {
				type = TYPE_EXTENDED_MEDIA_PREVIEW
			}
			else if (media?.ttlSeconds != 0 && ((media as? TLMessageMediaPhoto)?.photo is TLPhotoEmpty || document is TLDocumentEmpty)) {
				contentType = 1
				type = 10
			}
			else if (media is TLMessageMediaDice) {
				type = TYPE_ANIMATED_STICKER

//				if (media.document == null) {
//					media.document = TLDocument()
//					media.document.file_reference = ByteArray(0)
//					media.document.mimeType = "application/x-tgsdice"
//					media.document.dc_id = Int.MIN_VALUE
//					media.document.id = Int.MIN_VALUE.toLong()
//
//					val attributeImageSize = TLDocumentAttributeImageSize()
//					attributeImageSize.w = 512
//					attributeImageSize.h = 512
//
//					media.document.attributes.add(attributeImageSize)
//				}
			}
			else if (media is TLMessageMediaPhoto) {
				type = TYPE_PHOTO
			}
			else if (media is TLMessageMediaGeo || media is TLMessageMediaVenue || media is TLMessageMediaGeoLive) {
				type = TYPE_GEO
			}
			else if (isRoundVideo) {
				type = TYPE_ROUND_VIDEO
			}
			else if (isVideo) {
				type = TYPE_VIDEO
			}
			else if (isVoice) {
				type = TYPE_VOICE
			}
			else if (isMusic) {
				type = TYPE_MUSIC
			}
			else if (media is TLMessageMediaContact) {
				type = TYPE_CONTACT
			}
			else if (media is TLMessageMediaPoll) {
				type = TYPE_POLL
				checkedVotes = ArrayList()
			}
			else if (media is TLMessageMediaUnsupported) {
				type = TYPE_COMMON
			}
			else if (media is TLMessageMediaDocument) {
				val document = document

				type = if (document?.mimeType != null) {
					if (isGifDocument(document, hasValidGroupId())) {
						TYPE_GIF
					}
					else if (isSticker) {
						TYPE_STICKER
					}
					else if (isAnimatedSticker) {
						TYPE_ANIMATED_STICKER
					}
					else {
						TYPE_DOCUMENT
					}
				}
				else {
					TYPE_DOCUMENT
				}
			}
			else if (media is TLMessageMediaGame) {
				type = TYPE_COMMON
			}
			else if (media is TLMessageMediaInvoice) {
				type = TYPE_COMMON
			}
		}
		else if (messageOwner is TLMessageService) {
//			if (messageOwner?.action is TLMessageActionLoginUnknownLocation) {
//				type = TYPE_COMMON
//			}
//			else
			when (messageOwner?.action) {
				is TLMessageActionGiftPremium -> {
					contentType = 1
					type = TYPE_GIFT_PREMIUM
				}

				is TLMessageActionChatEditPhoto -> { // || messageOwner?.action is TLMessageActionUserUpdatedPhoto) {
					contentType = 1
					type = 11
				}
				//			else if (messageOwner?.action is TLMessageEncryptedAction) {
				//				if (messageOwner?.action?.encryptedAction is TLDecryptedMessageActionScreenshotMessages || messageOwner?.action?.encryptedAction is TLDecryptedMessageActionSetMessageTTL) {
				//					contentType = 1
				//					type = 10
				//				}
				//				else {
				//					contentType = -1
				//					type = -1
				//				}
				//			}
				is TLMessageActionHistoryClear -> {
					contentType = -1
					type = -1
				}

				is TLMessageActionPhoneCall -> {
					type = TYPE_CALL
				}

				else -> {
					contentType = 1
					type = 10
				}
			}
		}

		if (oldType != 1000 && oldType != type && type != TYPE_EMOJIS) {
			updateMessageText(MessagesController.getInstance(currentAccount).users, MessagesController.getInstance(currentAccount).chats, null, null)
			generateThumbs(false)
		}
	}

	fun checkLayout(): Boolean {
		if (type != TYPE_COMMON && type != TYPE_EMOJIS || messageOwner?.peerId == null || messageText.isNullOrEmpty()) {
			return false
		}

		if (layoutCreated) {
			val newMinSize = if (AndroidUtilities.isTablet()) AndroidUtilities.getMinTabletSide() else AndroidUtilities.displaySize.x

			if (abs((generatedWithMinSize - newMinSize).toDouble()) > AndroidUtilities.dp(52f) || generatedWithDensity != AndroidUtilities.density) {
				layoutCreated = false
			}
		}

		if (!layoutCreated) {
			layoutCreated = true

			val paint = if (getMedia(messageOwner) is TLMessageMediaGame) {
				Theme.chat_msgGameTextPaint
			}
			else {
				Theme.chat_msgTextPaint
			}

			val emojiOnly = if (allowsBigEmoji()) IntArray(1) else null

			messageText = Emoji.replaceEmoji(messageText, paint.fontMetricsInt, false, emojiOnly)
			messageText = replaceAnimatedEmoji(messageText, messageOwner?.entities, paint.fontMetricsInt)

			if (emojiOnly != null && emojiOnly[0] > 1) {
				replaceEmojiToLottieFrame(messageText, emojiOnly)
			}

			checkEmojiOnly(emojiOnly)
			checkBigAnimatedEmoji()
			setType()

			return true
		}

		return false
	}

	fun resetLayout() {
		layoutCreated = false
	}

	val mimeType: String
		get() {
			val document = document
			val media = getMedia(messageOwner)

			if (document != null) {
				return document.mimeType ?: ""
			}
			else if (media is TLMessageMediaInvoice) {
				val photo = media.photo

				if (photo != null) {
					return photo.mimeType ?: ""
				}
			}
			else if (media is TLMessageMediaPhoto) {
				return "image/jpeg"
			}
			else if (media is TLMessageMediaWebPage) {
				if ((media.webpage as? TLWebPage)?.photo != null) {
					return "image/jpeg"
				}
			}

			return ""
		}

	fun generateThumbs(update: Boolean) {
		if (hasExtendedMediaPreview()) {
			val preview = messageOwner?.media?.extendedMedia as TLMessageExtendedMediaPreview

			if (!update) {
				photoThumbs = ArrayList(listOf(preview.thumb))
			}
			else {
				updatePhotoSizeLocations(photoThumbs, listOf(preview.thumb))
			}

			photoThumbsObject = messageOwner

			if (strippedThumb == null) {
				createStrippedThumb()
			}
		}
		else if (messageOwner is TLMessageService) {
			if (messageOwner?.action is TLMessageActionChatEditPhoto) {
				val photo = messageOwner?.action?.photo as? TLPhoto

				if (photo != null) {
					if (!update) {
						photoThumbs = ArrayList(photo.sizes)
					}
					else if (!photoThumbs.isNullOrEmpty()) {
						for (a in photoThumbs!!.indices) {
							val photoObject = photoThumbs!![a]

							for (b in photo.sizes.indices) {
								val size = photo.sizes[b]

								if (size is TLPhotoSizeEmpty) {
									continue
								}

								if (size.type == photoObject?.type) {
									photoObject?.location = size.location
									break
								}
							}
						}
					}

					if (photo.dcId != 0 && photoThumbs != null) {
						for (thumb in photoThumbs!!) {
							thumb?.location?.dcId = photo.dcId
							// thumb.location?.fileReference = photo.fileReference
						}
					}
				}

				photoThumbsObject = messageOwner?.action?.photo
			}
		}
		else if (emojiAnimatedSticker != null || emojiAnimatedStickerId != null) {
			if (emojiAnimatedStickerColor.isNullOrEmpty() && isDocumentHasThumb(emojiAnimatedSticker)) {
				if (!update || photoThumbs == null) {
					photoThumbs = ArrayList()

					emojiAnimatedSticker?.thumbs?.let {
						photoThumbs?.addAll(it)
					}
				}
				else if (!photoThumbs.isNullOrEmpty()) {
					updatePhotoSizeLocations(photoThumbs, emojiAnimatedSticker?.thumbs)
				}

				photoThumbsObject = emojiAnimatedSticker
			}
		}
		else if (getMedia(messageOwner) != null && getMedia(messageOwner) !is TLMessageMediaEmpty) {
			val media = getMedia(messageOwner)

			if (media is TLMessageMediaPhoto) {
				val photo = media.photo

				if (!update || photoThumbs != null && photoThumbs?.size != photo?.sizes?.size) {
					photoThumbs = ArrayList(photo?.sizes ?: emptyList())
				}
				else if (!photoThumbs.isNullOrEmpty()) {
					for (a in photoThumbs!!.indices) {
						val photoObject = photoThumbs!![a] ?: continue
						val photoSizes = photo?.sizes

						if (!photoSizes.isNullOrEmpty()) {
							for (b in photoSizes.indices) {
								val size = photoSizes[b]

								if (size is TLPhotoSizeEmpty) {
									continue
								}

								if (size.type == photoObject.type) {
									photoObject.location = size.location
									break
								}
								else if ("s" == photoObject.type && size is TLPhotoStrippedSize) {
									photoThumbs!![a] = size
									break
								}
							}
						}
					}
				}

				photoThumbsObject = media.photo
			}
			else if (media is TLMessageMediaDocument) {
				val document = document

				if (isDocumentHasThumb(document)) {
					if (!update || photoThumbs == null) {
						photoThumbs = ArrayList()

						document.thumbs?.let {
							photoThumbs?.addAll(it)
						}
					}
					else if (photoThumbs!!.isNotEmpty()) {
						updatePhotoSizeLocations(photoThumbs, document.thumbs)
					}

					photoThumbsObject = document
				}
			}
			else if (media is TLMessageMediaGame) {
				val document = media.game?.document

				if (document != null) {
					if (isDocumentHasThumb(document)) {
						if (!update) {
							photoThumbs = ArrayList()

							document.thumbs?.let {
								photoThumbs?.addAll(it)
							}
						}
						else if (!photoThumbs.isNullOrEmpty()) {
							updatePhotoSizeLocations(photoThumbs, document.thumbs)
						}

						photoThumbsObject = document
					}
				}

				val photo = media.game?.photo

				if (photo != null) {
					if (!update || photoThumbs2 == null) {
						photoThumbs2 = ArrayList(photo.sizes ?: emptyList())
					}
					else if (!photoThumbs2.isNullOrEmpty()) {
						updatePhotoSizeLocations(photoThumbs2, photo.sizes)
					}

					photoThumbsObject2 = photo
				}

				if (photoThumbs == null && photoThumbs2 != null) {
					photoThumbs = photoThumbs2
					photoThumbs2 = null
					photoThumbsObject = photoThumbsObject2
					photoThumbsObject2 = null
				}
			}
			else if (media is TLMessageMediaWebPage) {
				val photo = (media.webpage as? TLWebPage)?.photo
				val document = (media.webpage as? TLWebPage)?.document

				if (photo != null) {
					if (!update || photoThumbs == null) {
						photoThumbs = ArrayList(photo.sizes ?: emptyList())
					}
					else if (photoThumbs!!.isNotEmpty()) {
						updatePhotoSizeLocations(photoThumbs, photo.sizes)
					}

					photoThumbsObject = photo
				}
				else if (document != null) {
					if (isDocumentHasThumb(document)) {
						if (!update) {
							photoThumbs = ArrayList()

							document.thumbs?.let {
								photoThumbs?.addAll(it)
							}
						}
						else if (photoThumbs != null && photoThumbs!!.isNotEmpty()) {
							updatePhotoSizeLocations(photoThumbs, document.thumbs)
						}

						photoThumbsObject = document
					}
				}
			}
		}
	}

	fun replaceWithLink(source: CharSequence, param: String, uids: List<Long>, usersDict: AbstractMap<Long, User>?, sUsersDict: LongSparseArray<User>?): CharSequence {
		var start = TextUtils.indexOf(source, param)

		if (start >= 0) {
			val names = SpannableStringBuilder("")

			for (a in uids.indices) {
				var user: User? = null

				if (usersDict != null) {
					user = usersDict[uids[a]]
				}
				else if (sUsersDict != null) {
					user = sUsersDict[uids[a]]
				}

				if (user == null) {
					user = MessagesController.getInstance(currentAccount).getUser(uids[a])
				}

				if (user != null) {
					val name = UserObject.getUserName(user)

					start = names.length

					if (names.isNotEmpty()) {
						names.append(", ")
					}

					names.append(name)
					names.setSpan(URLSpanNoUnderlineBold("" + user.id), start, start + name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
			}

			return TextUtils.replace(source, arrayOf(param), arrayOf<CharSequence>(names))
		}

		return source
	}

	val extension: String
		get() {
			val fileName = fileName
			val idx = fileName.lastIndexOf('.')
			var ext: String? = null

			if (idx != -1) {
				ext = fileName.substring(idx + 1)
			}

			if (ext.isNullOrEmpty()) {
				ext = document?.mimeType
			}

			if (ext == null) {
				ext = ""
			}

			ext = ext.uppercase(Locale.getDefault())

			return ext
		}

	val fileName: String
		get() = getFileName(messageOwner)

	val mediaType: Int
		get() {
			if (isVideo) {
				return FileLoader.MEDIA_DIR_VIDEO
			}
			else if (isVoice) {
				return FileLoader.MEDIA_DIR_AUDIO
			}
			else if (getMedia(messageOwner) is TLMessageMediaDocument) {
				return FileLoader.MEDIA_DIR_DOCUMENT
			}
			else if (getMedia(messageOwner) is TLMessageMediaPhoto) {
				return FileLoader.MEDIA_DIR_IMAGE
			}

			return FileLoader.MEDIA_DIR_CACHE
		}

	fun generateLinkDescription() {
		if (linkDescription != null) {
			return
		}

		var hashtagsType = 0
		val media = getMedia(messageOwner)

		if (media is TLMessageMediaWebPage && (media.webpage as? TLWebPage)?.description != null) {
			linkDescription = Spannable.Factory.getInstance().newSpannable((media.webpage as? TLWebPage)?.description ?: "")

			val siteName = (media.webpage as? TLWebPage)?.siteName?.lowercase()

			if ("instagram" == siteName) {
				hashtagsType = 1
			}
			else if ("twitter" == siteName) {
				hashtagsType = 2
			}
		}
		else if (media is TLMessageMediaGame && media.game?.description != null) {
			linkDescription = Spannable.Factory.getInstance().newSpannable(media.game?.description)
		}
		else if (media is TLMessageMediaInvoice && media.description != null) {
			linkDescription = Spannable.Factory.getInstance().newSpannable(media.description)
		}

		if (!linkDescription.isNullOrEmpty()) {
			if (containsUrls(linkDescription)) {
				try {
					AndroidUtilities.addLinks(linkDescription as Spannable?, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			linkDescription = Emoji.replaceEmoji(linkDescription, Theme.chat_msgTextPaint.fontMetricsInt, false)

			if (webPageDescriptionEntities != null) {
				addEntitiesToText(linkDescription, webPageDescriptionEntities!!, isOut, usernames = false, photoViewer = false, useManualParse = true)
				replaceAnimatedEmoji(linkDescription, webPageDescriptionEntities, Theme.chat_msgTextPaint.fontMetricsInt)
			}

			if (hashtagsType != 0) {
				if (linkDescription !is Spannable) {
					linkDescription = SpannableStringBuilder(linkDescription)
				}

				addUrlsByPattern(isOutOwner, linkDescription, false, hashtagsType, 0, false)
			}
		}
	}

	val voiceTranscription: CharSequence?
		get() {
			val transcription = messageOwner?.voiceTranscription ?: return null

			if (transcription.isEmpty()) {
				val ssb = SpannableString(ApplicationLoader.applicationContext.getString(R.string.NoWordsRecognized))

				ssb.setSpan(object : CharacterStyle() {
					override fun updateDrawState(textPaint: TextPaint) {
						textPaint.textSize *= .8f
						textPaint.color = Theme.chat_timePaint.color
					}
				}, 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

				return ssb
			}

			var text: CharSequence? = transcription

			if (!text.isNullOrEmpty()) {
				text = Emoji.replaceEmoji(text, Theme.chat_msgTextPaint.fontMetricsInt, false)
			}

			return text
		}

	fun measureVoiceTranscriptionHeight(): Float {
		val voiceTranscription = voiceTranscription ?: return 0f
		val width = AndroidUtilities.displaySize.x - AndroidUtilities.dp((if (this.needDrawAvatar()) 147 else 95).toFloat())

		val captionLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			StaticLayout.Builder.obtain(voiceTranscription, 0, voiceTranscription.length, Theme.chat_msgTextPaint, width).setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
		}
		else {
			StaticLayout(voiceTranscription, Theme.chat_msgTextPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
		}

		return captionLayout.height.toFloat()
	}

	val isVoiceTranscriptionOpen: Boolean
		get() = isVoice && messageOwner != null && messageOwner?.voiceTranscriptionOpen == true && messageOwner?.voiceTranscription != null && (messageOwner?.voiceTranscriptionFinal == true || TranscribeButton.isTranscribing(this)) && UserConfig.getInstance(currentAccount).isPremium

	fun generateCaption() {
		if (caption != null || isRoundVideo) {
			return
		}

		val messageOwner = messageOwner ?: return

		if (hasExtendedMedia()) {
			messageOwner.message = messageOwner.media?.description
		}

		val media = getMedia(messageOwner)

		if (!isMediaEmpty && media !is TLMessageMediaGame && !messageOwner.message.isNullOrEmpty()) {
			caption = Emoji.replaceEmoji(messageOwner.message, Theme.chat_msgTextPaint.fontMetricsInt, false)
			caption = replaceAnimatedEmoji(caption, messageOwner.entities, Theme.chat_msgTextPaint.fontMetricsInt)

			val hasEntities = if (messageOwner.sendState != MESSAGE_SEND_STATE_SENT) {
				false
			}
			else {
				!messageOwner.entities.isNullOrEmpty()
			}

			val useManualParse = !hasEntities && (eventId != 0L || isOut && messageOwner.sendState != MESSAGE_SEND_STATE_SENT || messageOwner.id < 0)

			if (useManualParse) {
				if (containsUrls(caption)) {
					try {
						AndroidUtilities.addLinks(caption as Spannable?, Linkify.WEB_URLS or Linkify.PHONE_NUMBERS or Linkify.EMAIL_ADDRESSES)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				addUrlsByPattern(isOutOwner, caption, true, 0, 0, true)
			}

			addEntitiesToText(caption, useManualParse)

			if (isVideo) {
				addUrlsByPattern(isOutOwner, caption, true, 3, duration, false)
			}
			else if (isMusic || isVoice) {
				addUrlsByPattern(isOutOwner, caption, true, 4, duration, false)
			}
		}
	}

	fun hasValidGroupId(): Boolean {
		return groupId != 0L && (!photoThumbs.isNullOrEmpty() || this.isMusic || isDocument())
	}

	val groupIdForUse: Long
		get() {
			if (localSentGroupId != 0L) {
				return localSentGroupId
			}

			return (messageOwner as? TLMessage)?.groupedId ?: 0L
		}

	val groupId: Long
		get() = if (localGroupId != 0L) localGroupId else groupIdForUse

	fun resetPlayingProgress() {
		audioProgress = 0.0f
		audioProgressSec = 0
		bufferedProgress = 0.0f
	}

	private fun addEntitiesToText(text: CharSequence?, useManualParse: Boolean): Boolean {
		return addEntitiesToText(text, false, useManualParse)
	}

	fun addEntitiesToText(text: CharSequence?, photoViewer: Boolean, useManualParse: Boolean): Boolean {
		if (text == null) {
			return false
		}

		if (isRestrictedMessage) {
			val entities = ArrayList<MessageEntity>()

			val entityItalic = TLMessageEntityItalic()
			entityItalic.offset = 0
			entityItalic.length = text.length
			entities.add(entityItalic)

			return addEntitiesToText(text, entities, isOutOwner, true, photoViewer, useManualParse)
		}
		else {
			return addEntitiesToText(text, messageOwner?.entities ?: arrayListOf(), isOutOwner, true, photoViewer, useManualParse, messageOwner?.dialogId ?: 0L)
		}
	}

	private fun replaceEmojiToLottieFrame(text: CharSequence?, emojiOnly: IntArray?) {
		if (text !is Spannable) {
			return
		}

		val spans = text.getSpans(0, text.length, EmojiSpan::class.java)
		val aspans = text.getSpans(0, text.length, AnimatedEmojiSpan::class.java)

		if (spans == null || (emojiOnly?.get(0) ?: 0) - spans.size - (aspans?.size ?: 0) > 0) {
			return
		}

		for (emojiSpan in spans) {
			val lottieDocument = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(emojiSpan.emoji)

			if (lottieDocument != null) {
				val start = text.getSpanStart(emojiSpan)
				val end = text.getSpanEnd(emojiSpan)

				text.removeSpan(emojiSpan)

				val span = AnimatedEmojiSpan(lottieDocument, emojiSpan.fontMetrics)

				span.standard = true

				text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
		}
	}

	fun needDrawShareButton(): Boolean {
		val messageOwner = messageOwner ?: return false

		if (preview) {
			return false
		}
		else if (scheduled) {
			return false
		}
		else if (eventId != 0L) {
			return false
		}
		else if ((messageOwner as? TLMessage)?.noforwards == true) {
			return false
		}
		else if ((messageOwner as? TLMessage)?.fwdFrom != null && !isOutOwner && (messageOwner as? TLMessage)?.fwdFrom?.savedFromPeer != null && dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
			return true
		}
		else if (type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER || type == TYPE_EMOJIS) {
			return false
		}
		else if ((messageOwner as? TLMessage)?.fwdFrom?.fromId is TLPeerChannel && !isOutOwner) {
			return true
		}
		else if (isFromUser) {
			if (getMedia(messageOwner) is TLMessageMediaEmpty || getMedia(messageOwner) == null || getMedia(messageOwner) is TLMessageMediaWebPage && (getMedia(messageOwner) as? TLMessageMediaWebPage)?.webpage !is TLWebPage) {
				return false
			}

			val user = MessagesController.getInstance(currentAccount).getUser(messageOwner.fromId?.userId)

			if (user != null && (user as? TLRPC.TLUser)?.bot == true && !hasExtendedMedia()) {
				return true
			}

			if (!isOut) {
				if (getMedia(messageOwner) is TLMessageMediaGame || getMedia(messageOwner) is TLMessageMediaInvoice && !hasExtendedMedia()) {
					return true
				}
				val chat = if (messageOwner.peerId != null && messageOwner.peerId?.channelId != 0L) getChat(null, null, messageOwner.peerId?.channelId) else null

				if (ChatObject.isChannel(chat) && chat.megagroup) {
					return chat.username != null && !chat.username.isNullOrEmpty() && getMedia(messageOwner) !is TLMessageMediaContact && getMedia(messageOwner) !is TLMessageMediaGeo
				}
			}
		}
		else if (messageOwner.fromId is TLPeerChannel || messageOwner.post) {
			if (isSupergroup) {
				return false
			}

			return messageOwner.peerId?.channelId != 0L && ((messageOwner as? TLMessage)?.viaBotId == 0L && messageOwner.replyTo == null || type != TYPE_STICKER && type != TYPE_ANIMATED_STICKER)
		}

		return false
	}

	val isYouTubeVideo: Boolean
		get() {
			val media = getMedia(messageOwner) as? TLMessageMediaWebPage ?: return false
			val webpage = media.webpage as? TLWebPage ?: return false
			return !webpage.embedUrl.isNullOrEmpty() && "YouTube" == webpage.siteName
		}

	val maxMessageTextWidth: Int
		get() {
			var maxWidth = 0

			generatedWithMinSize = if (AndroidUtilities.isTablet() && eventId != 0L) {
				AndroidUtilities.dp(530f)
			}
			else {
				if (AndroidUtilities.isTablet()) AndroidUtilities.getMinTabletSide() else getParentWidth()
			}

			generatedWithDensity = AndroidUtilities.density

			val media = getMedia(messageOwner)

			if (media is TLMessageMediaWebPage && media.webpage != null && "telegram_background" == (media.webpage as? TLWebPage)?.type) {
				try {
					val uri = media.webpage?.url?.toUri()
					val segment = uri?.lastPathSegment

					if (uri?.getQueryParameter("bg_color") != null) {
						maxWidth = AndroidUtilities.dp(220f)
					}
					else if (segment?.length == 6 || (segment?.length == 13 && segment[6] == '-')) {
						maxWidth = AndroidUtilities.dp(200f)
					}
				}
				catch (e: Exception) {
					// ignored
				}
			}
			else if (isAndroidTheme) {
				maxWidth = AndroidUtilities.dp(200f)
			}

			if (maxWidth == 0) {
				maxWidth = generatedWithMinSize - AndroidUtilities.dp((if (needDrawAvatarInternal() && !isOutOwner && messageOwner?.isThreadMessage != true) 132 else 80).toFloat())

				if (needDrawShareButton() && !isOutOwner) {
					maxWidth -= AndroidUtilities.dp(10f)
				}

				if (media is TLMessageMediaGame) {
					maxWidth -= AndroidUtilities.dp(10f)
				}
			}

			if (emojiOnlyCount >= 1 && totalAnimatedEmojiCount <= 100 && (emojiOnlyCount - totalAnimatedEmojiCount) < (if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) 100 else 50) && (hasValidReplyMessageObject() || isForwarded)) {
				maxWidth = min(maxWidth.toDouble(), (generatedWithMinSize * .65f).toInt().toDouble()).toInt()
			}

			return maxWidth
		}

	fun generateLayout() {
		if (type != 0 && type != TYPE_EMOJIS || messageOwner?.peerId == null || messageText.isNullOrEmpty()) {
			return
		}

		generateLinkDescription()

		textLayoutBlocks = ArrayList()
		textWidth = 0

		val hasEntities = if (messageOwner?.sendState != MESSAGE_SEND_STATE_SENT) {
			false
		}
		else {
			!messageOwner?.entities.isNullOrEmpty()
		}

		val useManualParse = !hasEntities && (eventId != 0L || getMedia(messageOwner) is TLMessageMediaInvoice || isOut && messageOwner?.sendState != MESSAGE_SEND_STATE_SENT || (messageOwner?.id ?: 0) < 0 || getMedia(messageOwner) is TLMessageMediaUnsupported)

		if (useManualParse) {
			addLinks(isOutOwner, messageText, botCommands = true, check = true)
		}
		else {
			// MARK: phone numbers are parsed here
//            if (messageText instanceof Spannable && messageText.length() < 1000) {
//                try {
//                    AndroidUtilities.addLinks((Spannable) messageText, Linkify.PHONE_NUMBERS);
//                } catch (Throwable e) {
//                    FileLog.e(e);
//                }
//            }
		}

		if (isYouTubeVideo || replyMessageObject?.isYouTubeVideo == true) {
			addUrlsByPattern(isOutOwner, messageText, false, 3, Int.MAX_VALUE, false)
		}
		else if (replyMessageObject != null) {
			if (replyMessageObject!!.isVideo) {
				addUrlsByPattern(isOutOwner, messageText, false, 3, replyMessageObject!!.duration, false)
			}
			else if (replyMessageObject!!.isMusic || replyMessageObject!!.isVoice) {
				addUrlsByPattern(isOutOwner, messageText, false, 4, replyMessageObject!!.duration, false)
			}
		}

		val hasUrls = addEntitiesToText(messageText, useManualParse)
		val maxWidth = maxMessageTextWidth
		val textLayout: StaticLayout

		val paint = if (getMedia(messageOwner) is TLMessageMediaGame) {
			Theme.chat_msgGameTextPaint
		}
		else {
			Theme.chat_msgTextPaint
		}

		val lineSpacing = 1f
		val lineAdd = (if (totalAnimatedEmojiCount >= 4) -1 else 0).toFloat()
		val align = Layout.Alignment.ALIGN_NORMAL //type == TYPE_EMOJIS && isOut() ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL;

		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				val builder = StaticLayout.Builder.obtain(messageText!!, 0, messageText!!.length, paint, maxWidth).setLineSpacing(lineAdd, lineSpacing).setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(align)

				if (emojiOnlyCount > 0) {
					builder.setIncludePad(false)

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
						builder.setUseLineSpacingFromFallbacks(false)
					}
				}

				textLayout = builder.build()
			}
			else {
				textLayout = StaticLayout(messageText, paint, maxWidth, align, lineSpacing, lineAdd, false)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
			return
		}

		textHeight = textLayout.height
		linesCount = textLayout.lineCount

		val linesPreBlock = if (totalAnimatedEmojiCount >= 50) LINES_PER_BLOCK_WITH_EMOJI else LINES_PER_BLOCK

		val blocksCount: Int
		val singleLayout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && totalAnimatedEmojiCount < 50

		blocksCount = if (singleLayout) {
			1
		}
		else {
			ceil((linesCount.toFloat() / linesPreBlock).toDouble()).toInt()
		}

		var linesOffset = 0
		var prevOffset = 0f

		for (a in 0 until blocksCount) {
			var currentBlockLinesCount: Int

			currentBlockLinesCount = if (singleLayout) {
				linesCount
			}
			else {
				min(linesPreBlock.toDouble(), (linesCount - linesOffset).toDouble()).toInt()
			}

			val block = TextLayoutBlock()

			if (blocksCount == 1) {
				block.textLayout = textLayout
				block.textYOffset = 0f
				block.charactersOffset = 0
				block.charactersEnd = textLayout.text.length

				if (emojiOnlyCount != 0) {
					when (emojiOnlyCount) {
						1 -> {
							textHeight -= AndroidUtilities.dp(5.3f)
							block.textYOffset -= AndroidUtilities.dp(5.3f).toFloat()
						}

						2 -> {
							textHeight -= AndroidUtilities.dp(4.5f)
							block.textYOffset -= AndroidUtilities.dp(4.5f).toFloat()
						}

						3 -> {
							textHeight -= AndroidUtilities.dp(4.2f)
							block.textYOffset -= AndroidUtilities.dp(4.2f).toFloat()
						}
					}
				}

				block.height = textHeight
			}
			else {
				val startCharacter = textLayout.getLineStart(linesOffset)
				val endCharacter = textLayout.getLineEnd(linesOffset + currentBlockLinesCount - 1)

				if (endCharacter < startCharacter) {
					continue
				}

				block.charactersOffset = startCharacter
				block.charactersEnd = endCharacter

				try {
					val sb = SpannableStringBuilder.valueOf(messageText!!.subSequence(startCharacter, endCharacter))

					if (hasUrls && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
						val builder = StaticLayout.Builder.obtain(sb, 0, sb.length, paint, maxWidth + AndroidUtilities.dp(2f)).setLineSpacing(lineAdd, lineSpacing).setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(align)

						if (emojiOnlyCount > 0) {
							builder.setIncludePad(false)

							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
								builder.setUseLineSpacingFromFallbacks(false)
							}
						}
						block.textLayout = builder.build()
					}
					else {
						block.textLayout = StaticLayout(sb, 0, sb.length, paint, maxWidth, align, lineSpacing, lineAdd, false)
					}

					block.textYOffset = textLayout.getLineTop(linesOffset).toFloat()

					if (a != 0 && emojiOnlyCount <= 0) {
						block.height = (block.textYOffset - prevOffset).toInt()
					}

					block.height = max(block.height.toDouble(), block.textLayout!!.getLineBottom(block.textLayout!!.lineCount - 1).toDouble()).toInt()

					prevOffset = block.textYOffset
				}
				catch (e: Exception) {
					FileLog.e(e)
					continue
				}

				if (a == blocksCount - 1) {
					currentBlockLinesCount = max(currentBlockLinesCount.toDouble(), block.textLayout!!.lineCount.toDouble()).toInt()

					try {
						textHeight = max(textHeight.toDouble(), (block.textYOffset + block.textLayout!!.height).toInt().toDouble()).toInt()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			block.spoilers.clear()

			if (!isSpoilersRevealed) {
				SpoilerEffect.addSpoilers(null, block.textLayout, null, block.spoilers)
			}

			textLayoutBlocks?.add(block)

			var lastLeft: Float

			try {
				lastLeft = block.textLayout!!.getLineLeft(currentBlockLinesCount - 1)

				if (a == 0 && lastLeft >= 0) {
					textXOffset = lastLeft
				}
			}
			catch (e: Exception) {
				lastLeft = 0f

				if (a == 0) {
					textXOffset = 0f
				}

				FileLog.e(e)
			}

			var lastLine: Float

			try {
				lastLine = block.textLayout!!.getLineWidth(currentBlockLinesCount - 1)
			}
			catch (e: Exception) {
				lastLine = 0f
				FileLog.e(e)
			}

			var linesMaxWidth = ceil(lastLine.toDouble()).toInt()

			if (linesMaxWidth > maxWidth + 80) {
				linesMaxWidth = maxWidth
			}

			var linesMaxWidthWithLeft: Int

			if (a == blocksCount - 1) {
				lastLineWidth = linesMaxWidth
			}

			val lastLineWidthWithLeft = ceil((linesMaxWidth + max(0.0, lastLeft.toDouble()))).toInt()

			linesMaxWidthWithLeft = lastLineWidthWithLeft

			if (currentBlockLinesCount > 1) {
				var hasNonRTL = false
				var textRealMaxWidth = 0f
				var textRealMaxWidthWithLeft = 0f
				var lineWidth: Float
				var lineLeft: Float

				for (n in 0 until currentBlockLinesCount) {
					try {
						lineWidth = block.textLayout!!.getLineWidth(n)
					}
					catch (e: Exception) {
						FileLog.e(e)
						lineWidth = 0f
					}

					try {
						lineLeft = block.textLayout!!.getLineLeft(n)
					}
					catch (e: Exception) {
						FileLog.e(e)
						lineLeft = 0f
					}

					if (lineWidth > maxWidth + 20) {
						lineWidth = maxWidth.toFloat()
						lineLeft = 0f
					}

					if (lineLeft > 0) {
						textXOffset = min(textXOffset.toDouble(), lineLeft.toDouble()).toFloat()
						block.directionFlags = (block.directionFlags.toInt() or TextLayoutBlock.FLAG_RTL).toByte()
						hasRtl = true
					}
					else {
						block.directionFlags = (block.directionFlags.toInt() or TextLayoutBlock.FLAG_NOT_RTL).toByte()
					}

					try {
						if (!hasNonRTL && lineLeft == 0f && block.textLayout!!.getParagraphDirection(n) == Layout.DIR_LEFT_TO_RIGHT) {
							hasNonRTL = true
						}
					}
					catch (ignore: Exception) {
						hasNonRTL = true
					}

					textRealMaxWidth = max(textRealMaxWidth.toDouble(), lineWidth.toDouble()).toFloat()
					textRealMaxWidthWithLeft = max(textRealMaxWidthWithLeft.toDouble(), (lineWidth + lineLeft).toDouble()).toFloat()
					linesMaxWidth = max(linesMaxWidth.toDouble(), ceil(lineWidth.toDouble()).toInt().toDouble()).toInt()
					linesMaxWidthWithLeft = max(linesMaxWidthWithLeft.toDouble(), ceil((lineWidth + lineLeft).toDouble()).toInt().toDouble()).toInt()
				}

				if (hasNonRTL) {
					textRealMaxWidth = textRealMaxWidthWithLeft

					if (a == blocksCount - 1) {
						lastLineWidth = lastLineWidthWithLeft
					}
				}
				else if (a == blocksCount - 1) {
					lastLineWidth = linesMaxWidth
				}

				textWidth = max(textWidth.toDouble(), ceil(textRealMaxWidth.toDouble()).toInt().toDouble()).toInt()
			}
			else {
				if (lastLeft > 0) {
					textXOffset = min(textXOffset.toDouble(), lastLeft.toDouble()).toFloat()

					if (textXOffset == 0f) {
						linesMaxWidth = (linesMaxWidth + lastLeft).toInt()
					}

					hasRtl = blocksCount != 1

					block.directionFlags = (block.directionFlags.toInt() or TextLayoutBlock.FLAG_RTL).toByte()
				}
				else {
					block.directionFlags = (block.directionFlags.toInt() or TextLayoutBlock.FLAG_NOT_RTL).toByte()
				}

				textWidth = max(textWidth.toDouble(), min(maxWidth.toDouble(), linesMaxWidth.toDouble())).toInt()
			}

			linesOffset += currentBlockLinesCount
		}
	}

	val isOut: Boolean
		get() = messageOwner?.out == true

	val isOutOwner: Boolean
		get() {
			if (preview) {
				return true
			}

			val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(null, null, messageOwner?.peerId?.channelId) else null

			if (messageOwner?.out != true || (messageOwner?.fromId !is TLPeerUser) && (messageOwner?.fromId !is TLPeerChannel || ChatObject.isChannel(chat) && !chat.megagroup) || ((ChatObject.isChannel(chat) && !chat.megagroup) && messageOwner?.post == true)) {
				return false
			}

			if (messageOwner?.fwdFrom == null) {
				return true
			}

			val selfUserId = UserConfig.getInstance(currentAccount).getClientUserId()

			if (dialogId == selfUserId) {
				return messageOwner?.fwdFrom?.fromId is TLPeerUser && messageOwner?.fwdFrom?.fromId?.userId == selfUserId && (messageOwner?.fwdFrom?.savedFromPeer == null || messageOwner?.fwdFrom?.savedFromPeer?.userId == selfUserId) || messageOwner?.fwdFrom?.savedFromPeer != null && messageOwner?.fwdFrom?.savedFromPeer?.userId == selfUserId && (messageOwner?.fwdFrom?.fromId == null || messageOwner?.fwdFrom?.fromId?.userId == selfUserId)
			}

			return messageOwner?.fwdFrom?.savedFromPeer == null || messageOwner?.fwdFrom?.savedFromPeer?.userId == selfUserId
		}

	fun needDrawAvatar(): Boolean {
		if (customAvatarDrawable != null) {
			return true
		}

		if (isSponsored && isFromChat) {
			return true
		}

		if (isFromChannel && messageOwner?.fwdFrom?.savedFromPeer != null) {
			return false
		}

		return !isSponsored && (isFromUser || isFromGroup || eventId != 0L || messageOwner?.fwdFrom?.savedFromPeer != null)
	}

	fun needDrawAvatarInternal(): Boolean {
		if (customAvatarDrawable != null) {
			return true
		}

		return !isSponsored && (isFromChat && isFromUser || isFromGroup || eventId != 0L || messageOwner?.fwdFrom != null && messageOwner?.fwdFrom?.savedFromPeer != null)
	}

	val isFromChat: Boolean
		get() {
			if (dialogId == UserConfig.getInstance(currentAccount).clientUserId) {
				return true
			}

			val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(null, null, messageOwner?.peerId?.channelId) else null

			if (ChatObject.isChannel(chat) && chat.megagroup || messageOwner?.peerId != null && messageOwner?.peerId!!.chatId != 0L) {
				return true
			}

			if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) {
				return chat != null && chat.megagroup
			}

			return false
		}

	val fromChatId: Long
		get() = getFromChatId(messageOwner)

	val chatId: Long
		get() {
			if (messageOwner?.peerId is TLPeerChat) {
				return messageOwner?.peerId?.chatId ?: 0L
			}
			else if (messageOwner?.peerId is TLPeerChannel) {
				return messageOwner?.peerId?.channelId ?: 0L
			}

			return 0L
		}

	private val isFromChannel: Boolean
		get() {
			val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(null, null, messageOwner?.peerId?.channelId) else null
			return ChatObject.isChannel(chat) && !chat.megagroup
		}

	val isFromUser: Boolean
		// get() = messageOwner?.fromId is TLPeerUser // MARK: this prevents avatars from drawing for grouped messages: && messageOwner?.post != true
		get() = messageOwner?.fromId is TLPeerUser && !isFromChannel

	val isFromGroup: Boolean
		get() {
			val chat = if (messageOwner?.peerId != null && messageOwner?.peerId?.channelId != 0L) getChat(null, null, messageOwner?.peerId?.channelId) else null
			return messageOwner?.fromId is TLPeerChannel && ChatObject.isChannel(chat) && chat.megagroup
		}

	val isForwardedChannelPost: Boolean
		get() = messageOwner?.fromId is TLPeerChannel && messageOwner?.fwdFrom != null && messageOwner?.fwdFrom?.channelPost != 0 && messageOwner?.fwdFrom?.savedFromPeer is TLPeerChannel && messageOwner?.fromId?.channelId == messageOwner?.fwdFrom?.savedFromPeer?.channelId

	val isUnread: Boolean
		get() = messageOwner?.unread == true

	val isContentUnread: Boolean
		get() = messageOwner?.mediaUnread == true

	fun setIsRead() {
		messageOwner?.unread = false
	}

	val unradFlags: Int
		get() = getUnreadFlags(messageOwner)

	fun setContentIsRead() {
		messageOwner?.mediaUnread = false
	}

	val id: Int
		get() = messageOwner?.id ?: 0

	val realId: Int
		get() = (if (messageOwner?.realId != 0) messageOwner?.realId else messageOwner?.id) ?: 0

	val size: Long
		get() = getMessageSize(messageOwner)

	val channelId: Long
		get() = getChannelId(messageOwner)

	fun needDrawBluredPreview(): Boolean {
		if (hasExtendedMediaPreview()) {
			return true
		}
//		else if (messageOwner is TLMessageSecret) {
//			val ttl = max(messageOwner?.ttl?.toDouble() ?: 0.0, (getMedia(messageOwner)?.ttlSeconds?.toDouble() ?: 0.0)).toInt()
//			return ttl > 0 && ((getMedia(messageOwner) is TLMessageMediaPhoto || isVideo || isGif) && ttl <= 60 || this.isRoundVideo)
//		}
		else if (messageOwner is TLMessage) {
			return (getMedia(messageOwner) != null && getMedia(messageOwner)?.ttlSeconds != 0) && (getMedia(messageOwner) is TLMessageMediaPhoto || getMedia(messageOwner) is TLMessageMediaDocument)
		}

		return false
	}

	val isSecretMedia: Boolean
		get() {
//			if (messageOwner is TLMessage_secret) {
//				return (((getMedia(messageOwner) is TLMessageMediaPhoto) || this.isGif) && ((messageOwner?.ttl ?: 0) > 0) && ((messageOwner?.ttl ?: 0) <= 60) || this.isVoice || this.isRoundVideo || this.isVideo)
//			}
//			else
			if (messageOwner is TLMessage) {
				return (getMedia(messageOwner) != null && getMedia(messageOwner)?.ttlSeconds != 0) && (getMedia(messageOwner) is TLMessageMediaPhoto || getMedia(messageOwner) is TLMessageMediaDocument)
			}

			return false
		}

	val isSavedFromMegagroup: Boolean
		get() {
			if (messageOwner?.fwdFrom != null && messageOwner?.fwdFrom?.savedFromPeer != null && messageOwner?.fwdFrom?.savedFromPeer?.channelId != 0L) {
				val chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwdFrom?.savedFromPeer?.channelId)
				return ChatObject.isMegagroup(chat)
			}

			return false
		}

	val dialogId: Long
		get() = getDialogId(messageOwner)

	fun canStreamVideo(): Boolean {
		val document = document as? TLDocument ?: return false

		if (SharedConfig.streamAllVideo) {
			return true
		}

		for (attribute in document.attributes) {
			if (attribute is TLDocumentAttributeVideo) {
				return attribute.supportsStreaming
			}
		}

		return SharedConfig.streamMkv && "video/x-matroska" == document.mimeType
	}

	val isSending: Boolean
		get() = messageOwner?.sendState == MESSAGE_SEND_STATE_SENDING && (messageOwner?.id ?: 0) < 0

	val isEditing: Boolean
		get() = messageOwner?.sendState == MESSAGE_SEND_STATE_EDITING && (messageOwner?.id ?: 0) > 0

	val isEditingMedia: Boolean
		get() {
			val media = getMedia(messageOwner)

			if (media is TLMessageMediaPhoto) {
				return media.photo?.id == 0L
			}
			else if (media is TLMessageMediaDocument) {
				return (media.document as? TLDocument)?.dcId == 0
			}

			return false
		}

	val isSendError: Boolean
		get() = (messageOwner?.sendState == MESSAGE_SEND_STATE_SEND_ERROR) && ((messageOwner?.id ?: 0 < 0) || (scheduled && ((messageOwner?.id ?: 0) > 0) && ((messageOwner?.date ?: 0) < ConnectionsManager.getInstance(currentAccount).currentTime - 60)))

	val isSent: Boolean
		get() = messageOwner?.sendState == MESSAGE_SEND_STATE_SENT || (messageOwner?.id ?: 0) > 0

	private val secretTimeLeft: Int
		get() {
			var secondsLeft = messageOwner?.ttl ?: 0

			if (messageOwner?.destroyTime != 0) {
				secondsLeft = max(0.0, ((messageOwner?.destroyTime ?: 0) - ConnectionsManager.getInstance(currentAccount).currentTime).toDouble()).toInt()
			}

			return secondsLeft
		}

	val secretTimeString: String?
		get() {
			if (!isSecretMedia) {
				return null
			}

			val secondsLeft = secretTimeLeft

			val str = if (secondsLeft < 60) {
				secondsLeft.toString() + "s"
			}
			else {
				(secondsLeft / 60).toString() + "m"
			}

			return str
		}

	val documentName: String?
		get() = FileLoader.getDocumentFileName(document)

	val isVideoSticker: Boolean
		get() = isVideoStickerDocument(document)

	val document: TLRPC.Document?
		get() = emojiAnimatedSticker ?: getDocument(messageOwner)

	private val stickerChar: String?
		get() {
			val document = document as? TLDocument

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeSticker) {
						return attribute.alt
					}
				}
			}

			return null
		}

	val approximateHeight: Int
		get() {
			if (type == TYPE_COMMON) {
				val media = getMedia(messageOwner)

				var height = textHeight + (if (media is TLMessageMediaWebPage && media.webpage is TLWebPage) AndroidUtilities.dp(100f) else 0)

				if (isReply) {
					height += AndroidUtilities.dp(42f)
				}

				return height
			}
			else if (type == TYPE_EXTENDED_MEDIA_PREVIEW) {
				return AndroidUtilities.getPhotoSize()
			}
			else if (type == TYPE_VOICE) {
				return AndroidUtilities.dp(72f)
			}
			else if (type == 12) {
				return AndroidUtilities.dp(71f)
			}
			else if (type == 9) {
				return AndroidUtilities.dp(100f)
			}
			else if (type == TYPE_GEO) {
				return AndroidUtilities.dp(114f)
			}
			else if (type == TYPE_MUSIC) {
				return AndroidUtilities.dp(82f)
			}
			else if (type == 10) {
				return AndroidUtilities.dp(30f)
			}
			else if (type == 11 || type == TYPE_GIFT_PREMIUM) {
				return AndroidUtilities.dp(50f)
			}
			else if (type == TYPE_ROUND_VIDEO) {
				return AndroidUtilities.roundMessageSize
			}
			else if (type == TYPE_EMOJIS) {
				return textHeight + AndroidUtilities.dp(30f)
			}
			else if (type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER) {
				val maxHeight = AndroidUtilities.displaySize.y * 0.4f

				val maxWidth = if (AndroidUtilities.isTablet()) {
					AndroidUtilities.getMinTabletSide() * 0.5f
				}
				else {
					AndroidUtilities.displaySize.x * 0.5f
				}

				var photoHeight = 0
				var photoWidth = 0
				val document = document as? TLDocument

				if (document != null) {
					for (attribute in document.attributes) {
						if (attribute is TLDocumentAttributeImageSize) {
							photoWidth = attribute.w
							photoHeight = attribute.h
							break
						}
					}
				}

				if (photoWidth == 0) {
					photoHeight = maxHeight.toInt()
					photoWidth = photoHeight + AndroidUtilities.dp(100f)
				}

				if (photoHeight > maxHeight) {
					photoWidth = (photoWidth * (maxHeight / photoHeight)).toInt()
					photoHeight = maxHeight.toInt()
				}

				if (photoWidth > maxWidth) {
					photoHeight = (photoHeight * (maxWidth / photoWidth)).toInt()
				}

				return photoHeight + AndroidUtilities.dp(14f)
			}
			else {
				var photoHeight: Int
				var photoWidth: Int

				photoWidth = if (AndroidUtilities.isTablet()) {
					(AndroidUtilities.getMinTabletSide() * 0.7f).toInt()
				}
				else {
					(min(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) * 0.7f).toInt()
				}

				photoHeight = photoWidth + AndroidUtilities.dp(100f)

				if (photoWidth > AndroidUtilities.getPhotoSize()) {
					photoWidth = AndroidUtilities.getPhotoSize()
				}

				if (photoHeight > AndroidUtilities.getPhotoSize()) {
					photoHeight = AndroidUtilities.getPhotoSize()
				}

				val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize())

				if (currentPhotoObject != null) {
					val scale = currentPhotoObject.w.toFloat() / photoWidth.toFloat()
					var h = (currentPhotoObject.h / scale).toInt()

					if (h == 0) {
						h = AndroidUtilities.dp(100f)
					}

					if (h > photoHeight) {
						h = photoHeight
					}
					else if (h < AndroidUtilities.dp(120f)) {
						h = AndroidUtilities.dp(120f)
					}

					if (needDrawBluredPreview()) {
						h = if (AndroidUtilities.isTablet()) {
							(AndroidUtilities.getMinTabletSide() * 0.5f).toInt()
						}
						else {
							(min(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) * 0.5f).toInt()
						}
					}

					photoHeight = h
				}

				return photoHeight + AndroidUtilities.dp(14f)
			}
		}

	private fun getParentWidth(): Int {
		return if ((preview && parentWidth > 0)) parentWidth else AndroidUtilities.displaySize.x
	}

	val stickerEmoji: String?
		get() {
			val document = document as? TLDocument ?: return null

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeSticker || attribute is TLDocumentAttributeCustomEmoji) {
					return attribute.alt
				}
			}

			return null
		}

	val isVideoCall: Boolean
		get() = (messageOwner?.action as? TLMessageActionPhoneCall)?.video == true

	val isAnimatedEmoji: Boolean
		get() = emojiAnimatedSticker != null || emojiAnimatedStickerId != null

	val isAnimatedAnimatedEmoji: Boolean
		get() = isAnimatedEmoji && isAnimatedEmoji(document)

	val isDice: Boolean
		get() = getMedia(messageOwner) is TLMessageMediaDice

	val diceEmoji: String?
		get() {
			if (!isDice) {
				return null
			}

			val messageMediaDice = getMedia(messageOwner) as? TLMessageMediaDice

			if (messageMediaDice?.emoticon.isNullOrEmpty()) {
				return "\uD83C\uDFB2"
			}

			return messageMediaDice?.emoticon?.replace("\ufe0f", "")
		}

	val diceValue: Int
		get() = (getMedia(messageOwner) as? TLMessageMediaDice)?.value ?: -1

	val isSticker: Boolean
		get() {
			if (type != 1000) {
				return type == TYPE_STICKER
			}

			return isStickerDocument(document) || isVideoSticker(document)
		}

	val isAnimatedSticker: Boolean
		get() {
			if (type != 1000) {
				return type == TYPE_ANIMATED_STICKER
			}

			val isSecretChat = DialogObject.isEncryptedDialog(dialogId)

			if (isSecretChat && messageOwner?.stickerVerified != 1) {
				return false
			}

			if (emojiAnimatedStickerId != null && emojiAnimatedSticker == null) {
				return true
			}

			return isAnimatedStickerDocument(document, emojiAnimatedSticker != null || !isSecretChat || isOut)
		}

	val isAnyKindOfSticker: Boolean
		get() = type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER || type == TYPE_EMOJIS

	fun shouldDrawWithoutBackground(): Boolean {
		return type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER || type == TYPE_ROUND_VIDEO || type == TYPE_EMOJIS
	}

	val isAnimatedEmojiStickers: Boolean
		get() = type == TYPE_EMOJIS

	val isAnimatedEmojiStickerSingle: Boolean
		get() = emojiAnimatedStickerId != null

	val isLocation: Boolean
		get() = isLocationMessage(messageOwner)

	val isMask: Boolean
		get() = isMaskMessage(messageOwner)

	val isMusic: Boolean
		get() = isMusicMessage(messageOwner) && !isVideo

	fun isDocument(): Boolean {
		return document != null && !isVideo && !isMusic && !isVoice && !isAnyKindOfSticker
	}

	val isVoice: Boolean
		get() = isVoiceMessage(messageOwner)

	val isVideo: Boolean
		get() = isVideoMessage(messageOwner)

	val isPhoto: Boolean
		get() = isPhoto(messageOwner)

	val isLiveLocation: Boolean
		get() = isLiveLocationMessage(messageOwner)

	fun isExpiredLiveLocation(date: Int): Boolean {
		return (messageOwner?.date ?: 0) + (getMedia(messageOwner)?.period ?: 0) <= date
	}

	val isGame: Boolean
		get() = isGameMessage(messageOwner)

	val isInvoice: Boolean
		get() = isInvoiceMessage(messageOwner)

	val isRoundVideo: Boolean
		get() {
			if (isRoundVideoCached == 0) {
				isRoundVideoCached = if (type == TYPE_ROUND_VIDEO || isRoundVideoMessage(messageOwner)) TYPE_PHOTO else TYPE_VOICE
			}

			return isRoundVideoCached == 1
		}

	fun shouldAnimateSending(): Boolean {
		return isSending && (type == TYPE_ROUND_VIDEO || isVoice || (isAnyKindOfSticker && sendAnimationData != null) || (messageText != null && sendAnimationData != null))
	}

	fun hasAttachedStickers(): Boolean {
		val media = getMedia(messageOwner)

		if (media is TLMessageMediaPhoto) {
			return (media.photo as? TLPhoto)?.hasStickers == true
		}
		else if (media is TLMessageMediaDocument) {
			return isDocumentHasAttachedStickers(media.document)
		}

		return false
	}

	val isGif: Boolean
		get() = isGifMessage(messageOwner)

	val isWebpageDocument: Boolean
		get() {
			val media = getMedia(messageOwner) as? TLMessageMediaWebPage
			val webpage = media?.webpage as? TLWebPage
			return webpage?.document != null && !isGifDocument(webpage.document)
		}

	val isWebpage: Boolean
		get() = getMedia(messageOwner) is TLMessageMediaWebPage

	val isNewGif: Boolean
		get() = getMedia(messageOwner) != null && isNewGifDocument(document)

	private val isAndroidTheme: Boolean
		get() {
			val media = getMedia(messageOwner) as? TLMessageMediaWebPage
			val webpage = media?.webpage as? TLWebPage

			if (webpage != null && webpage.attributes.isNotEmpty()) {
				for (attribute in webpage.attributes) {
					for (document in attribute.documents) {
						if ("application/x-tgtheme-android" == document.mimeType) {
							return true
						}
					}

					if (attribute.settings != null) {
						return true
					}
				}
			}

			return false
		}

	val musicTitle: String?
		get() = getMusicTitle(true)

	fun getMusicTitle(unknown: Boolean): String? {
		val document = document as? TLDocument

		if (document != null) {
			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeAudio) {
					if (attribute.voice) {
						if (!unknown) {
							return null
						}

						return LocaleController.formatDateAudio(messageOwner?.date?.toLong() ?: 0L, true)
					}

					var title = attribute.title

					if (title.isNullOrEmpty()) {
						title = FileLoader.getDocumentFileName(document)

						if (title.isNullOrEmpty() && unknown) {
							title = ApplicationLoader.applicationContext.getString(R.string.AudioUnknownTitle)
						}
					}

					return title
				}
				else if (attribute is TLDocumentAttributeVideo) {
					if (attribute.roundMessage) {
						return LocaleController.formatDateAudio(messageOwner?.date?.toLong() ?: 0L, true)
					}
				}
			}

			val fileName = FileLoader.getDocumentFileName(document)

			if (!fileName.isNullOrEmpty()) {
				return fileName
			}
		}

		return ApplicationLoader.applicationContext.getString(R.string.AudioUnknownTitle)
	}

	val duration: Int
		get() {
			val document = document as? TLDocument ?: return 0

			if (audioPlayerDuration > 0) {
				return audioPlayerDuration
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeAudio) {
					return attribute.duration
				}
				else if (attribute is TLDocumentAttributeVideo) {
					return attribute.duration
				}
			}

			return audioPlayerDuration
		}

	fun getArtworkUrl(small: Boolean): String? {
		val document = document as? TLDocument

		if (document != null) {
			if ("audio/ogg" == document.mimeType) {
				return null
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeAudio) {
					if (attribute.voice) {
						return null
					}
					else {
						var performer = attribute.performer
						val title = attribute.title

						if (!performer.isNullOrEmpty()) {
							for (excludeWord in excludeWords) {
								performer = performer?.replace(excludeWord, " ")
							}
						}

						if (performer.isNullOrEmpty() && title.isNullOrEmpty()) {
							return null
						}

						try {
							return "athumb://itunes.apple.com/search?term=" + URLEncoder.encode("$performer - $title", "UTF-8") + "&entity=song&limit=4" + (if (small) "&s=1" else "")
						}
						catch (e: Exception) {
							// ignored
						}
					}
				}
			}
		}

		return null
	}

	val musicAuthor: String?
		get() = getMusicAuthor(true)

	fun getMusicAuthor(unknown: Boolean): String? {
		val document = document as? TLDocument

		if (document != null) {
			var isVoice = false

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeAudio) {
					if (attribute.voice) {
						isVoice = true
					}
					else {
						var performer = attribute.performer

						if (performer.isNullOrEmpty() && unknown) {
							performer = ApplicationLoader.applicationContext.getString(R.string.AudioUnknownArtist)
						}

						return performer
					}
				}
				else if (attribute is TLDocumentAttributeVideo) {
					if (attribute.roundMessage) {
						isVoice = true
					}
				}

				if (isVoice) {
					if (!unknown) {
						return null
					}

					if (isOutOwner || messageOwner?.fwdFrom != null && messageOwner?.fwdFrom?.fromId is TLPeerUser && messageOwner?.fwdFrom?.fromId?.userId == UserConfig.getInstance(currentAccount).getClientUserId()) {
						return ApplicationLoader.applicationContext.getString(R.string.FromYou)
					}

					var user: User? = null
					var chat: Chat? = null

					if (messageOwner?.fwdFrom != null && messageOwner?.fwdFrom?.fromId is TLPeerChannel) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwdFrom?.fromId?.channelId)
					}
					else if (messageOwner?.fwdFrom != null && messageOwner?.fwdFrom?.fromId is TLPeerChat) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwdFrom?.fromId?.chatId)
					}
					else if (messageOwner?.fwdFrom != null && messageOwner?.fwdFrom?.fromId is TLPeerUser) {
						user = MessagesController.getInstance(currentAccount).getUser(messageOwner?.fwdFrom?.fromId?.userId)
					}
					else if (messageOwner?.fwdFrom != null && messageOwner?.fwdFrom?.fromName != null) {
						return messageOwner?.fwdFrom?.fromName
					}
					else if (messageOwner?.fromId is TLPeerChat) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fromId?.chatId)
					}
					else if (messageOwner?.fromId is TLPeerChannel) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fromId?.channelId)
					}
					else if (messageOwner?.fromId == null && messageOwner?.peerId?.channelId != 0L) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.peerId?.channelId)
					}
					else {
						user = MessagesController.getInstance(currentAccount).getUser(messageOwner?.fromId?.userId)
					}

					if (user != null) {
						return UserObject.getUserName(user)
					}
					else if (chat != null) {
						return chat.title
					}
				}
			}
		}

		return ApplicationLoader.applicationContext.getString(R.string.AudioUnknownArtist)
	}

	val inputStickerSet: InputStickerSet?
		get() = getInputStickerSet(messageOwner)

	val isForwarded: Boolean
		get() = isForwardedMessage(messageOwner)

	open fun needDrawForwarded(): Boolean {
		val messageOwner = messageOwner ?: return false
		return (messageOwner.flags and TLRPC.MESSAGE_FLAG_FWD) != 0 && (messageOwner.fwdFrom != null) && !messageOwner.fwdFrom!!.imported && (messageOwner.fwdFrom!!.savedFromPeer == null || messageOwner.fwdFrom!!.fromId !is TLPeerChannel || messageOwner.fwdFrom!!.savedFromPeer.channelId != messageOwner.fwdFrom!!.fromId.channelId) && (UserConfig.getInstance(currentAccount).getClientUserId() != dialogId)
	}

	val isReply: Boolean
		get() {
			val replyMessageObject = replyMessageObject
			return (!(replyMessageObject != null && replyMessageObject.messageOwner is TLMessageEmpty) && messageOwner?.replyTo != null && (messageOwner?.replyTo?.replyToMsgId != 0 || messageOwner?.replyTo?.replyToRandomId != 0L)) && (messageOwner!!.flags and TLRPC.MESSAGE_FLAG_REPLY) != 0
		}

	val isMediaEmpty: Boolean
		get() = isMediaEmpty(messageOwner)

	val isMediaEmptyWebpage: Boolean
		get() = isMediaEmptyWebpage(messageOwner)

	fun hasReplies(): Boolean {
		return (messageOwner?.replies?.replies ?: 0) > 0
	}

	fun canViewThread(): Boolean {
		if (messageOwner?.action != null) {
			return false
		}

		return hasReplies() || replyMessageObject != null && replyMessageObject?.messageOwner?.replies != null || replyTopMsgId != 0
	}

	val isComments: Boolean
		get() = messageOwner?.replies?.comments == true

	fun isLinkedToChat(chatId: Long): Boolean {
		return messageOwner?.replies != null && (chatId == 0L || messageOwner?.replies?.channelId == chatId)
	}

	val repliesCount: Int
		get() = messageOwner?.replies?.replies ?: 0

	fun canEditMessage(chat: Chat?): Boolean {
		return canEditMessage(currentAccount, messageOwner, chat, scheduled)
	}

	fun canEditMessageScheduleTime(chat: Chat?): Boolean {
		return canEditMessageScheduleTime(currentAccount, messageOwner, chat)
	}

	fun canForwardMessage(): Boolean {
		return /*messageOwner !is TLMessageSecret && */ !needDrawBluredPreview() && !isLiveLocation && type != 16 && !isSponsored && (messageOwner as? TLMessage)?.noforwards != true
	}

	fun canEditMedia(): Boolean {
		if (isSecretMedia) {
			return false
		}
		else if (getMedia(messageOwner) is TLMessageMediaPhoto) {
			return true
		}
		else if (getMedia(messageOwner) is TLMessageMediaDocument) {
			return !isVoice && !isSticker && !isAnimatedSticker && !isRoundVideo
		}

		return false
	}

	fun canEditMessageAnytime(chat: Chat?): Boolean {
		return canEditMessageAnytime(currentAccount, messageOwner, chat)
	}

	fun canDeleteMessage(inScheduleMode: Boolean, chat: Chat?): Boolean {
		return eventId == 0L && sponsoredId == null && canDeleteMessage(currentAccount, inScheduleMode, messageOwner, chat)
	}

	val forwardedName: String?
		get() {
			if (messageOwner?.fwdFrom != null) {
				if (messageOwner?.fwdFrom?.fromId is TLPeerChannel) {
					val chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwdFrom?.fromId?.channelId)
					if (chat != null) {
						return chat.title
					}
				}
				else if (messageOwner?.fwdFrom?.fromId is TLPeerChat) {
					val chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwdFrom?.fromId?.chatId)

					if (chat != null) {
						return chat.title
					}
				}
				else if (messageOwner?.fwdFrom?.fromId is TLPeerUser) {
					val user = MessagesController.getInstance(currentAccount).getUser(messageOwner?.fwdFrom?.fromId?.userId)

					if (user != null) {
						return UserObject.getUserName(user)
					}
				}
				else if (messageOwner?.fwdFrom?.fromName != null) {
					return messageOwner?.fwdFrom?.fromName
				}
			}

			return null
		}

	val replyMsgId: Int
		get() = messageOwner?.replyTo?.replyToMsgId ?: 0

	val replyTopMsgId: Int
		get() = messageOwner?.replyTo?.replyToTopId ?: 0

	val replyAnyMsgId: Int
		get() {
			if (messageOwner?.replyTo != null) {
				return if (messageOwner?.replyTo?.replyToTopId != 0) {
					messageOwner?.replyTo?.replyToTopId ?: 0
				}
				else {
					messageOwner?.replyTo?.replyToMsgId ?: 0
				}
			}

			return 0
		}

	val isPrivateForward: Boolean
		get() = !messageOwner?.fwdFrom?.fromName.isNullOrEmpty()

	val isImportedForward: Boolean
		get() = messageOwner?.fwdFrom?.imported == true

	val senderId: Long
		get() {
			val messageOwner = messageOwner

			if (messageOwner?.fwdFrom != null && messageOwner.fwdFrom!!.savedFromPeer != null) {
				if (messageOwner.fwdFrom!!.savedFromPeer.userId != 0L) {
					return if (messageOwner.fwdFrom!!.fromId is TLPeerUser) {
						messageOwner.fwdFrom!!.fromId.userId
					}
					else {
						messageOwner.fwdFrom!!.savedFromPeer.userId
					}
				}
				else if (messageOwner.fwdFrom!!.savedFromPeer.channelId != 0L) {
					return if (isSavedFromMegagroup && messageOwner.fwdFrom!!.fromId is TLPeerUser) {
						messageOwner.fwdFrom!!.fromId.userId
					}
					else if (messageOwner.fwdFrom!!.fromId is TLPeerChannel) {
						-messageOwner.fwdFrom!!.fromId.channelId
					}
					else if (messageOwner.fwdFrom!!.fromId is TLPeerChat) {
						-messageOwner.fwdFrom!!.fromId.chatId
					}
					else {
						-messageOwner.fwdFrom!!.savedFromPeer.channelId
					}
				}
				else if (messageOwner.fwdFrom!!.savedFromPeer.chatId != 0L) {
					return when (messageOwner.fwdFrom!!.fromId) {
						is TLPeerUser -> messageOwner.fwdFrom!!.fromId.userId
						is TLPeerChannel -> -messageOwner.fwdFrom!!.fromId.channelId
						is TLPeerChat -> -messageOwner.fwdFrom!!.fromId.chatId
						else -> -messageOwner.fwdFrom!!.savedFromPeer.chatId
					}
				}
			}
			else if (messageOwner?.fromId is TLPeerUser) {
				return messageOwner.fromId!!.userId
			}
			else if (messageOwner?.fromId is TLPeerChannel) {
				return -messageOwner.fromId!!.channelId
			}
			else if (messageOwner?.fromId is TLPeerChat) {
				return -messageOwner.fromId!!.chatId
			}
			else if (messageOwner?.post == true) {
				return messageOwner.peerId?.channelId ?: 0
			}

			return 0
		}

	val isWallpaper: Boolean
		get() {
			val media = getMedia(messageOwner) as? TLMessageMediaWebPage
			val webpage = media?.webpage as? TLWebPage
			return "telegram_background" == webpage?.type
		}

	val isTheme: Boolean
		get() {
			val media = getMedia(messageOwner) as? TLMessageMediaWebPage
			val webpage = media?.webpage as? TLWebPage
			return "telegram_theme" == webpage?.type
		}

	val mediaExistanceFlags: Int
		get() {
			var flags = 0

			if (attachPathExists) {
				flags = flags or 1
			}

			if (mediaExists) {
				flags = flags or 2
			}

			return flags
		}

	fun applyMediaExistanceFlags(flags: Int) {
		if (flags == -1) {
			checkMediaExistence()
		}
		else {
			attachPathExists = (flags and 1) != 0
			mediaExists = (flags and 2) != 0
		}
	}

	@JvmOverloads
	fun checkMediaExistence(useFileDatabaseQueue: Boolean = true) {
		attachPathExists = false
		mediaExists = false

		if (type == TYPE_EXTENDED_MEDIA_PREVIEW) {
			val preview = messageOwner?.media?.extendedMedia as? TLMessageExtendedMediaPreview

			if (preview?.thumb != null) {
				val file = FileLoader.getInstance(currentAccount).getPathToAttach(preview.thumb)

				if (!mediaExists) {
					mediaExists = file.exists() || preview.thumb is TLPhotoStrippedSize
				}
			}
		}
		else if (type == TYPE_PHOTO) {
			val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize())

			if (currentPhotoObject != null) {
				val file = FileLoader.getInstance(currentAccount).getPathToMessage(messageOwner, useFileDatabaseQueue)

				if (needDrawBluredPreview()) {
					mediaExists = File(file.absolutePath + ".enc").exists()
				}

				if (!mediaExists) {
					mediaExists = file.exists()
				}
			}
		}
		if (!mediaExists && type == TYPE_GIF || type == TYPE_VIDEO || type == 9 || type == TYPE_VOICE || type == TYPE_MUSIC || type == TYPE_ROUND_VIDEO) {
			if (!messageOwner?.attachPath.isNullOrEmpty()) {
				val f = File(messageOwner!!.attachPath!!)
				attachPathExists = f.exists()
			}

			if (!attachPathExists) {
				val file = FileLoader.getInstance(currentAccount).getPathToMessage(messageOwner, useFileDatabaseQueue)

				if (type == TYPE_VIDEO && needDrawBluredPreview()) {
					mediaExists = File(file.absolutePath + ".enc").exists()
				}

				if (!mediaExists) {
					mediaExists = file.exists()
				}
			}
		}

		if (!mediaExists) {
			val document = document

			if (document != null) {
				mediaExists = if (isWallpaper) {
					FileLoader.getInstance(currentAccount).getPathToAttach(document, null, true, useFileDatabaseQueue).exists()
				}
				else {
					FileLoader.getInstance(currentAccount).getPathToAttach(document, null, false, useFileDatabaseQueue).exists()
				}
			}
			else if (type == TYPE_COMMON) {
				val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize()) ?: return
				mediaExists = FileLoader.getInstance(currentAccount).getPathToAttach(currentPhotoObject, null, true, useFileDatabaseQueue).exists()
			}
			else if (type == 11) {
				val photo = messageOwner?.action?.photo as? TLPhoto

				if (photo == null || photo.videoSizes.isEmpty()) {
					return
				}

				mediaExists = FileLoader.getInstance(currentAccount).getPathToAttach(photo.videoSizes[0], null, true, useFileDatabaseQueue).exists()
			}
		}
	}

	fun setQuery(query: String?) {
		@Suppress("NAME_SHADOWING") var query = query

		if (query.isNullOrEmpty()) {
			highlightedWords = null
			messageTrimmedToHighlight = null
			return
		}

		val foundWords = ArrayList<String>()

		query = query.trim().lowercase()

		val queryWord = query.split("\\P{L}+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

		val searchForWords = ArrayList<String>()

		if (!messageOwner?.message.isNullOrEmpty()) {
			val message = messageOwner?.message?.trim()?.lowercase()

			if (message?.contains(query) == true && !foundWords.contains(query)) {
				foundWords.add(query)
				handleFoundWords(foundWords, queryWord)
				return
			}

			val words = message?.split("\\P{L}+".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray() ?: arrayOf()

			searchForWords.addAll(words)
		}

		if (document != null) {
			val fileName = FileLoader.getDocumentFileName(document)?.lowercase()

			if (fileName?.contains(query) == true && !foundWords.contains(query)) {
				foundWords.add(query)
			}

			val words = fileName?.split("\\P{L}+".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray() ?: arrayOf()

			searchForWords.addAll(words)
		}

		val media = getMedia(messageOwner)

		if (media is TLMessageMediaWebPage && media.webpage is TLWebPage) {
			val webPage = media.webpage as? TLWebPage
			val title = (webPage?.title ?: webPage?.siteName)?.lowercase()

			if (title != null) {
				if (title.contains(query) && !foundWords.contains(query)) {
					foundWords.add(query)
				}

				val words = title.split("\\P{L}+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

				searchForWords.addAll(words)
			}
		}

		var musicAuthor = musicAuthor

		if (musicAuthor != null) {
			musicAuthor = musicAuthor.lowercase()

			if (musicAuthor.contains(query) && !foundWords.contains(query)) {
				foundWords.add(query)
			}

			val words = musicAuthor.split("\\P{L}+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

			searchForWords.addAll(words)
		}

		for (currentQuery in queryWord) {
			if (currentQuery.length < 2) {
				continue
			}

			for (i in searchForWords.indices) {
				if (foundWords.contains(searchForWords[i])) {
					continue
				}

				var word = searchForWords[i]
				val startIndex = word.indexOf(currentQuery[0])

				if (startIndex < 0) {
					continue
				}

				val l = max(currentQuery.length.toDouble(), word.length.toDouble()).toInt()

				if (startIndex != 0) {
					word = word.substring(startIndex)
				}

				val min = min(currentQuery.length.toDouble(), word.length.toDouble()).toInt()
				var count = 0

				for (j in 0 until min) {
					if (word[j] == currentQuery[j]) {
						count++
					}
					else {
						break
					}
				}

				if (count / l.toFloat() >= 0.5) {
					foundWords.add(searchForWords[i])
				}
			}
		}

		handleFoundWords(foundWords, queryWord)
	}

	private fun handleFoundWords(foundWords: ArrayList<String>, queryWord: Array<String>) {
		if (foundWords.isNotEmpty()) {
			var foundExactly = false

			for (i in foundWords.indices) {
				for (s in queryWord) {
					if (foundWords[i].contains(s)) {
						foundExactly = true
						break
					}
				}

				if (foundExactly) {
					break
				}
			}

			if (foundExactly) {
				var i = 0

				while (i < foundWords.size) {
					var findMatch = false

					for (s in queryWord) {
						if (foundWords[i].contains(s)) {
							findMatch = true
							break
						}
					}

					if (!findMatch) {
						foundWords.removeAt(i--)
					}

					i++
				}

				if (foundWords.size > 0) {
					foundWords.sortWith { s, s1 ->
						s1.length - s.length
					}

					val s = foundWords[0]
					foundWords.clear()
					foundWords.add(s)
				}
			}

			highlightedWords = foundWords

			if (messageOwner?.message != null) {
				var str = messageOwner?.message?.replace('\n', ' ')?.replace(" +".toRegex(), " ")?.trim() ?: ""
				val lastIndex = str.length
				var startHighlightedIndex = str.lowercase().indexOf(foundWords[0])
				val maxSymbols = 200

				if (startHighlightedIndex < 0) {
					startHighlightedIndex = 0
				}

				if (lastIndex > maxSymbols) {
					val newStart = max(0.0, (startHighlightedIndex - maxSymbols / 2).toDouble()).toInt()
					str = str.substring(newStart, min(lastIndex.toDouble(), (startHighlightedIndex - newStart + startHighlightedIndex + maxSymbols / 2).toDouble()).toInt())
				}

				messageTrimmedToHighlight = str
			}
		}
	}

	fun createMediaThumbs() {
		if (isVideo) {
			val document = document
			val thumb = FileLoader.getClosestPhotoSizeWithSize(document?.thumbs, 50)
			val qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document?.thumbs, 320)
			mediaThumb = ImageLocation.getForDocument(qualityThumb, document)
			mediaSmallThumb = ImageLocation.getForDocument(thumb, document)
		}
		else if ((getMedia(messageOwner) as? TLMessageMediaPhoto)?.photo != null && !photoThumbs.isNullOrEmpty()) {
			val currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 50)
			val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 320, false, currentPhotoObjectThumb, false)

			mediaThumb = ImageLocation.getForObject(currentPhotoObject, photoThumbsObject)
			mediaSmallThumb = ImageLocation.getForObject(currentPhotoObjectThumb, photoThumbsObject)
		}
	}

	fun hasHighlightedWords(): Boolean {
		return !highlightedWords.isNullOrEmpty()
	}

	fun equals(obj: MessageObject): Boolean {
		return id == obj.id && dialogId == obj.dialogId
	}

	val isReactionsAvailable: Boolean
		get() = !isEditing && !isSponsored && isSent && messageOwner?.action == null

	fun selectReaction(visibleReaction: VisibleReaction?, big: Boolean, fromDoubleTap: Boolean): Boolean {
		val messageOwner = messageOwner

		if (messageOwner?.reactions == null) {
			val r = TLMessageReactions()
			r.canSeeList = isFromGroup || isFromUser

			messageOwner?.reactions = r
		}

		val chosenReactions = mutableListOf<TLReactionCount>()
		var newReaction: TLReactionCount? = null
		var maxChosenOrder = 0

		val reactions = messageOwner?.reactions

		if (reactions != null) {
			for (i in reactions.results.indices) {
				val reactionCount = reactions.results[i]

				if (reactionCount.chosen) {
					chosenReactions.add(reactionCount)

					if (reactionCount.chosenOrder > maxChosenOrder) {
						maxChosenOrder = reactionCount.chosenOrder
					}
				}

				val tlReaction = reactionCount.reaction

				if (tlReaction is TLReactionEmoji) {
					if (visibleReaction?.emojicon == null) {
						continue
					}

					if (tlReaction.emoticon == visibleReaction.emojicon) {
						newReaction = reactionCount
					}
				}

				if (tlReaction is TLReactionCustomEmoji) {
					if (visibleReaction?.documentId == 0L) {
						continue
					}

					if (tlReaction.documentId == visibleReaction?.documentId) {
						newReaction = reactionCount
					}
				}
			}
		}

		if (chosenReactions.isNotEmpty() && chosenReactions.contains(newReaction) && big) {
			return true
		}

		val maxReactionsCount = MessagesController.getInstance(currentAccount).maxUserReactionsCount

		if (newReaction != null && chosenReactions.isNotEmpty() && (chosenReactions.contains(newReaction) || fromDoubleTap)) {
			newReaction.chosen = false
			newReaction.count--

			if (newReaction.count <= 0) {
				reactions?.results?.remove(newReaction)
			}

			if (reactions?.canSeeList == true) {
				var i = 0

				while (i < reactions.recentReactions.size) {
					if (getPeerId(reactions.recentReactions[i].peerId) == UserConfig.getInstance(currentAccount).getClientUserId() && ReactionsUtils.compare(messageOwner.reactions?.recentReactions?.get(i)?.reaction, visibleReaction)) {
						reactions.recentReactions.removeAt(i)
						i--
					}

					i++
				}
			}

			reactionsChanged = true

			return false
		}

		while (chosenReactions.isNotEmpty() && chosenReactions.size >= maxReactionsCount) {
			var minIndex = 0

			for (i in 1 until chosenReactions.size) {
				if (chosenReactions[i].chosenOrder < chosenReactions[minIndex].chosenOrder) {
					minIndex = i
				}
			}

			val chosenReaction = chosenReactions[minIndex]
			chosenReaction.chosen = false
			chosenReaction.count--

			if (chosenReaction.count <= 0) {
				messageOwner?.reactions?.results?.remove(chosenReaction)
			}

			chosenReactions.remove(chosenReaction)

			if (messageOwner?.reactions?.canSeeList == true) {
				var i = 0

				while (i < messageOwner.reactions!!.recentReactions.size) {
					if (getPeerId(messageOwner.reactions!!.recentReactions[i].peerId) == UserConfig.getInstance(currentAccount).getClientUserId() && ReactionsUtils.compare(messageOwner.reactions?.recentReactions?.get(i)?.reaction, visibleReaction)) {
						messageOwner.reactions!!.recentReactions.removeAt(i)
						i--
					}

					i++
				}
			}
		}

		if (newReaction == null) {
			newReaction = TLReactionCount()

			if (visibleReaction?.emojicon != null) {
				newReaction.reaction = TLReactionEmoji()
				(newReaction.reaction as? TLReactionEmoji)?.emoticon = visibleReaction.emojicon

				messageOwner?.reactions?.results?.add(newReaction)
			}
			else {
				newReaction.reaction = TLReactionCustomEmoji()
				(newReaction.reaction as? TLReactionCustomEmoji)?.documentId = visibleReaction?.documentId ?: 0L

				messageOwner?.reactions?.results?.add(newReaction)
			}
		}

		newReaction.chosen = true
		newReaction.count++
		newReaction.chosenOrder = maxChosenOrder + 1

		if (messageOwner?.reactions?.canSeeList == true || ((messageOwner?.dialogId ?: 0) > 0 && maxReactionsCount > 1)) {
			val action = TLMessagePeerReaction()

			messageOwner?.reactions?.recentReactions?.add(0, action)

			action.peerId = TLPeerUser().also { it.userId = UserConfig.getInstance(currentAccount).getClientUserId() }

			if (visibleReaction?.emojicon != null) {
				action.reaction = TLReactionEmoji().also {
					it.emoticon = visibleReaction.emojicon
				}
			}
			else {
				action.reaction = TLReactionCustomEmoji().also {
					it.documentId = visibleReaction?.documentId ?: 0L
				}
			}
		}

		reactionsChanged = true

		return true
	}

	fun probablyRingtone(): Boolean {
		val document = document as? TLDocument

		if (document != null && RingtoneDataStore.ringtoneSupportedMimeType.contains(document.mimeType) && document.size < MessagesController.getInstance(currentAccount).ringtoneSizeMax * 2L) {
			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeAudio) {
					if (attribute.duration < 60) {
						return true
					}
				}
			}
		}

		return false
	}

	fun shouldEncryptPhotoOrVideo(): Boolean {
		return shouldEncryptPhotoOrVideo(messageOwner)
	}

	fun canPreviewDocument(): Boolean {
		return canPreviewDocument(document)
	}

	companion object {
		const val MESSAGE_SEND_STATE_SENT: Int = 0
		const val MESSAGE_SEND_STATE_SENDING: Int = 1
		const val MESSAGE_SEND_STATE_SEND_ERROR: Int = 2
		const val MESSAGE_SEND_STATE_EDITING: Int = 3
		const val TYPE_COMMON: Int = 0
		const val TYPE_PHOTO: Int = 1
		const val TYPE_VOICE: Int = 2
		const val TYPE_VIDEO: Int = 3
		const val TYPE_GEO: Int = 4 // TLMessageMediaGeo, TLMessageMediaVenue, TLMessageMediaGeoLive
		const val TYPE_ROUND_VIDEO: Int = 5
		const val TYPE_GIF: Int = 8
		const val TYPE_DOCUMENT: Int = 9

		// public static final int TYPE_SOMETHING_ABOUT_EMPTY_PHOTO_OR_DOCUMENT_AND_TTL = 10;
		// public static final int TYPE_SOMETHING_ABOUT_CHAT_EDIT_OR_UPDATED_PHOTO = 11;
		const val TYPE_CONTACT: Int = 12
		const val TYPE_STICKER: Int = 13
		const val TYPE_MUSIC: Int = 14
		const val TYPE_ANIMATED_STICKER: Int = 15
		const val TYPE_CALL: Int = 16
		const val TYPE_POLL: Int = 17
		const val TYPE_GIFT_PREMIUM: Int = 18
		const val TYPE_EMOJIS: Int = 19
		const val TYPE_EXTENDED_MEDIA_PREVIEW: Int = 20
		const val POSITION_FLAG_LEFT: Int = 1
		const val POSITION_FLAG_RIGHT: Int = 2
		const val POSITION_FLAG_TOP: Int = 4
		const val POSITION_FLAG_BOTTOM: Int = 8
		val excludeWords: Array<String> = arrayOf(" vs. ", " vs ", " versus ", " ft. ", " ft ", " featuring ", " feat. ", " feat ", " presents ", " pres. ", " pres ", " and ", " & ", " . ")
		private const val LINES_PER_BLOCK = 10
		private const val LINES_PER_BLOCK_WITH_EMOJI = 5

		private val urlPattern: Pattern by lazy {
			Pattern.compile("(^|\\s)/[a-zA-Z@\\d_]{1,255}|(^|\\s|\\()@[a-zA-Z\\d_]{1,32}|(^|\\s|\\()#[^0-9][\\w.]+|(^|\\s)\\$[A-Z]{3,8}([ ,.]|$)")
		}

		private val instagramUrlPattern: Pattern by lazy {
			Pattern.compile("(^|\\s|\\()@[a-zA-Z\\d_.]{1,32}|(^|\\s|\\()#[\\w.]+")
		}

		private val videoTimeUrlPattern: Pattern by lazy {
			Pattern.compile("\\b(?:(\\d{1,2}):)?(\\d{1,3}):([0-5][0-9])\\b(?: - |)([^\\n]*)")
		}

		fun hasUnreadReactions(message: Message?): Boolean {
			if (message == null) {
				return false
			}

			return hasUnreadReactions(message.reactions)
		}

		fun hasUnreadReactions(reactions: TLMessageReactions?): Boolean {
			if (reactions == null) {
				return false
			}

			for (reaction in reactions.recentReactions) {
				if (reaction.unread) {
					return true
				}
			}

			return false
		}

		@JvmStatic
		fun isPremiumSticker(document: TLRPC.Document?): Boolean {
			if (document !is TLDocument) {
				return false
			}

			for (thumb in document.videoThumbs) {
				if ("f" == thumb.type) {
					return true
				}
			}

			return false
		}

		@JvmStatic
		fun getPremiumStickerAnimation(document: TLRPC.Document?): TLRPC.VideoSize? {
			if (document !is TLDocument) {
				return null
			}

			for (thumb in document.videoThumbs) {
				if ("f" == thumb.type) {
					return thumb
				}
			}

			return null
		}

		@JvmStatic
		fun updateReactions(message: Message?, reactions: TLMessageReactions?) {
			if (message == null || reactions == null) {
				return
			}

			var chosenReactionFound = false

			if (message.reactions != null) {
				var a = 0
				val n = message.reactions?.results?.size ?: 0

				while (a < n) {
					val reaction = message.reactions?.results?.get(a)
					var b = 0
					val n2 = reactions.results.size

					while (b < n2) {
						val newReaction = reactions.results[b]

						if (ReactionsLayoutInBubble.equalsTLReaction(reaction?.reaction, newReaction.reaction)) {
							if (!chosenReactionFound && reactions.min && reaction?.chosen == true) {
								newReaction.chosen = true
								chosenReactionFound = true
							}

							newReaction.lastDrawnPosition = reaction?.lastDrawnPosition ?: 0
						}

						b++
					}

					if (reaction?.chosen == true) {
						chosenReactionFound = true
					}

					a++
				}
			}

			message.reactions = reactions
			message.flags = message.flags or 1048576
		}

		@JvmStatic
		fun updatePollResults(media: TLMessageMediaPoll?, results: TLPollResults?) {
			if (media == null || results == null) {
				return
			}

			if ((results.flags and 2) != 0) {
				var chosen: ArrayList<ByteArray?>? = null
				var correct: ByteArray? = null

				if (results.min && media.results?.results != null) {
					for (answerVoters in media.results!!.results) {
						if (answerVoters.chosen) {
							if (chosen == null) {
								chosen = ArrayList()
							}

							chosen.add(answerVoters.option)
						}

						if (answerVoters.correct) {
							correct = answerVoters.option
						}
					}
				}

				media.results?.results?.clear()
				media.results?.results?.addAll(results.results)

				if (chosen != null || correct != null) {
					var b = 0
					val n2 = media.results?.results?.size ?: 0

					while (b < n2) {
						val answerVoters = media.results?.results?.get(b)

						if (chosen != null) {
							var a = 0
							val n = chosen.size

							while (a < n) {
								if (answerVoters?.option?.contentEquals(chosen[a]) == true) {
									answerVoters.chosen = true
									chosen.removeAt(a)
									break
								}

								a++
							}

							if (chosen.isEmpty()) {
								chosen = null
							}
						}

						if (correct != null && answerVoters?.option?.contentEquals(correct) == true) {
							answerVoters.correct = true
							correct = null
						}

						if (chosen == null && correct == null) {
							break
						}

						b++
					}
				}

				media.results?.flags = media.results!!.flags or 2
			}

			if ((results.flags and 4) != 0) {
				media.results?.totalVoters = results.totalVoters
				media.results?.flags = media.results!!.flags or 4
			}

			if ((results.flags and 8) != 0) {
				media.results?.recentVoters?.clear()
				media.results?.recentVoters?.addAll(results.recentVoters)
				media.results?.flags = media.results!!.flags or 8
			}

			if ((results.flags and 16) != 0) {
				media.results?.solution = results.solution
				media.results?.solutionEntities?.clear()
				media.results?.solutionEntities?.addAll(results.solutionEntities)
				media.results?.flags = media.results!!.flags or 16
			}
		}

		@JvmStatic
		fun getMedia(messageOwner: Message?): MessageMedia? {
			contract {
				returnsNotNull() implies (messageOwner != null)
			}

			if (messageOwner == null) {
				return null
			}

			if (messageOwner.media != null && messageOwner.media!!.extendedMedia is TLMessageExtendedMedia) {
				return (messageOwner.media!!.extendedMedia as TLMessageExtendedMedia).media
			}

			return messageOwner.media
		}

		fun isAnimatedStickerDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return document != null && document.mimeType == "video/webm"
		}

		@JvmStatic
		fun isGifDocument(document: WebFile?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return document != null && (document.mimeType == "image/gif" || isNewGifDocument(document))
		}

		@JvmStatic
		fun isGifDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null && document is TLDocument)
			}

			return isGifDocument(document, false)
		}

		fun isGifDocument(document: TLRPC.Document?, hasGroup: Boolean): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return document?.mimeType != null && (document.mimeType == "image/gif" && !hasGroup || isNewGifDocument(document))
		}

		@JvmStatic
		fun isDocumentHasThumb(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document !is TLDocument) {
				return false
			}

			if (document.thumbs.isEmpty()) {
				return false
			}

			for (photoSize in document.thumbs) {
				if (photoSize !is TLPhotoSizeEmpty && photoSize.location !is TLFileLocationUnavailable) {
					return true
				}
			}

			return false
		}

		@JvmStatic
		fun canPreviewDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document !is TLDocument) {
				return false
			}

			if (document.mimeType != null) {
				val mime = document.mimeType?.lowercase()

				if (isDocumentHasThumb(document) && (mime == "image/png" || mime == "image/jpg" || mime == "image/jpeg") || (Build.VERSION.SDK_INT >= 26 && (mime == "image/heic"))) {
					for (attribute in document.attributes) {
						if (attribute is TLDocumentAttributeImageSize) {
							return attribute.w < 6000 && attribute.h < 6000
						}
					}
				}
				else if (BuildConfig.DEBUG_PRIVATE_VERSION) {
					val fileName = FileLoader.getDocumentFileName(document)

					return if (fileName?.startsWith("elloapp_secret_sticker") == true && fileName.endsWith("json")) {
						true
					}
					else {
						fileName?.endsWith(".svg") == true
					}
				}
			}

			return false
		}

		@JvmStatic
		fun isRoundVideoDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document !is TLDocument) {
				return false
			}

			if ("video/mp4" == document.mimeType) {
				var width = 0
				var height = 0
				var round = false

				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeVideo) {
						width = attribute.w
						height = attribute.h
						round = attribute.roundMessage
					}
				}

				return round && width <= 1280 && height <= 1280
			}

			return false
		}

		fun isNewGifDocument(document: WebFile?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document != null && "video/mp4" == document.mimeType) {
				var width = 0
				var height = 0

				document.attributes?.forEach {
					if (it is TLDocumentAttributeVideo) {
						width = it.w
						height = it.h
					}
				}

				return width <= 1280 && height <= 1280
			}

			return false
		}

		@JvmStatic
		fun isNewGifDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument && "video/mp4" == document.mimeType) {
				var width = 0
				var height = 0
				var animated = false

				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeAnimated) {
						animated = true
					}
					else if (attribute is TLDocumentAttributeVideo) {
						width = attribute.w
						height = attribute.h
					}
				}

				return animated && width <= 1280 && height <= 1280
			}

			return false
		}

		fun isSystemSignUp(message: MessageObject?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return message != null && message.messageOwner is TLMessageService && message.messageOwner?.action is TLMessageActionContactSignUp
		}

		private fun updatePhotoSizeLocations(o: ArrayList<PhotoSize?>?, n: List<PhotoSize?>?) {
			if (o == null || n == null) {
				return
			}

			for (photoObject in o) {
				if (photoObject == null) {
					continue
				}

				for (size in n) {
					if (size == null || size is TLPhotoSizeEmpty || size is TLPhotoCachedSize) {
						continue
					}

					if (size.type == photoObject.type) {
						photoObject.location = size.location
						break
					}
				}
			}
		}

		fun replaceWithLink(source: CharSequence?, param: String, `object`: TLObject?): CharSequence? {
			val start = TextUtils.indexOf(source, param)

			if (start >= 0) {
				var name: String
				val id: String
				var spanObject: TLObject? = null

				when (`object`) {
					is User -> {
						name = UserObject.getUserName(`object`)
						id = "" + `object`.id
					}

					is Chat -> {
						name = `object`.title ?: ""
						id = "" + -`object`.id
					}

					is TLGame -> {
						name = `object`.title ?: ""
						id = "game"
					}

					is TLChatInviteExported -> {
						name = `object`.link ?: ""
						id = "invite"
						spanObject = `object`
					}

					else -> {
						name = ""
						id = "0"
					}
				}

				name = name.replace('\n', ' ')

				val builder = SpannableStringBuilder(TextUtils.replace(source, arrayOf(param), arrayOf(name)))

				val span = URLSpanNoUnderlineBold("" + id)
				span.setObject(spanObject)

				builder.setSpan(span, start, start + name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

				return builder
			}
			return source
		}

		@JvmStatic
		fun getFileName(messageOwner: Message?): String {
			val media = getMedia(messageOwner)

			if (media is TLMessageMediaDocument) {
				return FileLoader.getAttachFileName(getDocument(messageOwner))
			}
			else if (media is TLMessageMediaPhoto) {
				val sizes = media.photo?.sizes

				if (!sizes.isNullOrEmpty()) {
					val sizeFull = FileLoader.getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize())

					if (sizeFull != null) {
						return FileLoader.getAttachFileName(sizeFull)
					}
				}
			}
			else if (media is TLMessageMediaWebPage) {
				return FileLoader.getAttachFileName((media.webpage as? TLWebPage)?.document)
			}

			return ""
		}

		private fun containsUrls(message: CharSequence?): Boolean {
			if (message == null || message.length < 2 || message.length > 1024 * 20) {
				return false
			}

			val length = message.length
			var digitsInRow = 0
			var schemeSequence = 0
			var dotSequence = 0

			var lastChar = 0.toChar()

			for (i in 0 until length) {
				val c = message[i]

				if (c in '0'..'9') {
					digitsInRow++

					if (digitsInRow >= 6) {
						return true
					}

					schemeSequence = 0
					dotSequence = 0
				}
				else if (!(c != ' ' && digitsInRow > 0)) {
					digitsInRow = 0
				}

				if ((c == '@' || c == '#' || c == '/' || c == '$') && i == 0 || i != 0 && (message[i - 1] == ' ' || message[i - 1] == '\n')) {
					return true
				}

				if (c == ':') {
					schemeSequence = if (schemeSequence == 0) {
						1
					}
					else {
						0
					}
				}
				else if (c == '/') {
					if (schemeSequence == 2) {
						return true
					}

					if (schemeSequence == 1) {
						schemeSequence++
					}
					else {
						schemeSequence = 0
					}
				}
				else if (c == '.') {
					if (dotSequence == 0 && lastChar != ' ') {
						dotSequence++
					}
					else {
						dotSequence = 0
					}
				}
				else if (c != ' ' && lastChar == '.' && dotSequence == 1) {
					return true
				}
				else {
					dotSequence = 0
				}

				lastChar = c
			}

			return false
		}

		@JvmStatic
		fun addUrlsByPattern(isOut: Boolean, charSequence: CharSequence?, botCommands: Boolean, patternType: Int, duration: Int, check: Boolean) {
			if (charSequence == null) {
				return
			}

			try {
				val matcher = when (patternType) {
					3, 4 -> {
						videoTimeUrlPattern.matcher(charSequence)
					}

					1 -> {
						instagramUrlPattern.matcher(charSequence)
					}

					else -> {
						urlPattern.matcher(charSequence)
					}
				}

				val spannable = charSequence as Spannable

				while (matcher.find()) {
					var start = matcher.start()
					var end = matcher.end()
					var url: URLSpanNoUnderline? = null

					if (patternType == 3 || patternType == 4) {
						val s1 = matcher.start(1)
						val e1 = matcher.end(1)
						val s2 = matcher.start(2)
						val e2 = matcher.end(2)
						val s3 = matcher.start(3)
						val e3 = matcher.end(3)
						val s4 = matcher.start(4)
						val e4 = matcher.end(4)
						val minutes = Utilities.parseInt(charSequence.subSequence(s2, e2))
						var seconds = Utilities.parseInt(charSequence.subSequence(s3, e3))
						val hours = if (s1 >= 0 && e1 >= 0) Utilities.parseInt(charSequence.subSequence(s1, e1)) else -1
						val label = if (s4 < 0 || e4 < 0) null else charSequence.subSequence(s4, e4).toString()

						if (s4 >= 0 || e4 >= 0) {
							end = e3
						}

						val spans = spannable.getSpans(start, end, URLSpan::class.java)

						if (spans != null && spans.isNotEmpty()) {
							continue
						}

						seconds += minutes * 60

						if (hours > 0) {
							seconds += hours * 60 * 60
						}

						if (seconds > duration) {
							continue
						}

						url = if (patternType == 3) {
							URLSpanNoUnderline("video?$seconds")
						}
						else {
							URLSpanNoUnderline("audio?$seconds")
						}

						url.label = label
					}
					else {
						var ch = charSequence[start]

						if (patternType != 0) {
							if (ch != '@' && ch != '#') {
								start++
							}

							ch = charSequence[start]

							if (ch != '@' && ch != '#') {
								continue
							}
						}
						else {
							if (ch != '@' && ch != '#' && ch != '/' && ch != '$') {
								start++
							}
						}
						if (patternType == 1) {
							url = if (ch == '@') {
								URLSpanNoUnderline("https://instagram.com/" + charSequence.subSequence(start + 1, end))
							}
							else {
								URLSpanNoUnderline("https://www.instagram.com/explore/tags/" + charSequence.subSequence(start + 1, end))
							}
						}
						else if (patternType == 2) {
							url = if (ch == '@') {
								URLSpanNoUnderline("https://twitter.com/" + charSequence.subSequence(start + 1, end))
							}
							else {
								URLSpanNoUnderline("https://twitter.com/hashtag/" + charSequence.subSequence(start + 1, end))
							}
						}
						else {
							if (charSequence[start] == '/') {
								if (botCommands) {
									url = URLSpanBotCommand(charSequence.subSequence(start, end).toString(), if (isOut) 1 else 0)
								}
							}
							else {
								url = URLSpanNoUnderline(charSequence.subSequence(start, end).toString())
							}
						}
					}

					if (url != null) {
						if (check) {
							val spans = spannable.getSpans(start, end, ClickableSpan::class.java)

							if (spans != null && spans.isNotEmpty()) {
								spannable.removeSpan(spans[0])
							}
						}

						spannable.setSpan(url, start, end, 0)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		private fun getWebDocumentWidthAndHeight(document: WebDocument?): IntArray? {
			if (document == null) {
				return null
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeImageSize) {
					return intArrayOf(attribute.w, attribute.h)
				}
				else if (attribute is TLDocumentAttributeVideo) {
					return intArrayOf(attribute.w, attribute.h)
				}
			}

			return null
		}

		private fun getWebDocumentDuration(document: WebDocument?): Int {
			if (document == null) {
				return 0
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeVideo) {
					return attribute.duration
				}
				else if (attribute is TLDocumentAttributeAudio) {
					return attribute.duration
				}
			}

			return 0
		}

		@JvmStatic
		fun getInlineResultWidthAndHeight(inlineResult: BotInlineResult): IntArray {
			var result = getWebDocumentWidthAndHeight((inlineResult as? TLRPC.TLBotInlineResult)?.content)

			if (result == null) {
				result = getWebDocumentWidthAndHeight((inlineResult as? TLRPC.TLBotInlineResult)?.thumb)

				if (result == null) {
					result = intArrayOf(0, 0)
				}
			}

			return result
		}

		@JvmStatic
		fun getInlineResultDuration(inlineResult: BotInlineResult): Int {
			var result = getWebDocumentDuration((inlineResult as? TLRPC.TLBotInlineResult)?.content)

			if (result == 0) {
				result = getWebDocumentDuration((inlineResult as? TLRPC.TLBotInlineResult)?.thumb)
			}

			return result
		}

		@JvmStatic
		@JvmOverloads
		fun addLinks(isOut: Boolean, messageText: CharSequence?, botCommands: Boolean = true, check: Boolean = false, internalOnly: Boolean = false) {
			if (messageText is Spannable && containsUrls(messageText)) {
				if (messageText.length < 1000) {
					try {
						AndroidUtilities.addLinks(messageText as Spannable?, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS, internalOnly)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				else {
					try {
						AndroidUtilities.addLinks(messageText as Spannable?, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES, internalOnly)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				addUrlsByPattern(isOut, messageText, botCommands, 0, 0, check)
			}
		}

		fun replaceAnimatedEmoji(text: CharSequence?, entities: List<MessageEntity>?, fontMetricsInt: FontMetricsInt?): Spannable {
			val spannable = if (text is Spannable) text else SpannableString(text)

			if (entities == null) {
				return spannable
			}

			val emojiSpans = spannable.getSpans(0, spannable.length, EmojiSpan::class.java)

			for (messageEntity in entities) {
				if (messageEntity is TLMessageEntityCustomEmoji) {
					for (j in emojiSpans.indices) {
						val span = emojiSpans[j]

						if (span != null) {
							val start = spannable.getSpanStart(span)
							val end = spannable.getSpanEnd(span)

							if (messageEntity.offset == start && messageEntity.length == (end - start)) {
								spannable.removeSpan(span)
								emojiSpans[j] = null
							}
						}
					}

					if (messageEntity.offset + messageEntity.length <= spannable.length) {
						val animatedSpans = spannable.getSpans(messageEntity.offset, messageEntity.offset + messageEntity.length, AnimatedEmojiSpan::class.java)

						if (animatedSpans != null && animatedSpans.isNotEmpty()) {
							for (animatedSpan in animatedSpans) {
								spannable.removeSpan(animatedSpan)
							}
						}

						val span = AnimatedEmojiSpan(messageEntity.documentId, fontMetricsInt)

						spannable.setSpan(span, messageEntity.offset, messageEntity.offset + messageEntity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
				}
			}

			return spannable
		}

		@JvmStatic
		@JvmOverloads
		fun addEntitiesToText(text: CharSequence?, entities: List<MessageEntity>, out: Boolean, usernames: Boolean, photoViewer: Boolean, useManualParse: Boolean, messageOwnerId: Long = 0L): Boolean {
			if (text !is Spannable) {
				return false
			}

			val pattern = Pattern.compile("№\\d+")
			val matcher = pattern.matcher(text)

			if (matcher.find() && messageOwnerId == BuildConfig.SUPPORT_BOT_ID && !out) {
				text.setSpan(ForegroundColorSpan(ApplicationLoader.applicationContext.getColor(R.color.brand)), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			val spans = text.getSpans(0, text.length, URLSpan::class.java)
			var hasUrls = spans != null && spans.isNotEmpty()

			if (entities.isEmpty()) {
				return hasUrls
			}

			val t: Byte = if (photoViewer) {
				2
			}
			else if (out) {
				1
			}
			else {
				0
			}

			val runs = ArrayList<TextStyleRun>()
			val entitiesCopy = ArrayList(entities)

			entitiesCopy.sortWith { o1, o2 ->
				if (o1.offset > o2.offset) {
					return@sortWith 1
				}
				else if (o1.offset < o2.offset) {
					return@sortWith -1
				}

				0
			}

			for (entity in entitiesCopy) {
				if (entity.length <= 0 || entity.offset < 0 || entity.offset >= text.length) {
					continue
				}
				else if (entity.offset + entity.length > text.length) {
					entity.length = text.length - entity.offset
				}

				if (!useManualParse || entity is TLMessageEntityBold || entity is TLMessageEntityItalic || entity is TLMessageEntityStrike || entity is TLMessageEntityUnderline || entity is TLMessageEntityBlockquote || entity is TLMessageEntityCode || entity is TLMessageEntityPre || entity is TLMessageEntityMentionName || entity is TLInputMessageEntityMentionName || entity is TLMessageEntityTextUrl || entity is TLMessageEntitySpoiler || entity is TLMessageEntityCustomEmoji) {
					if (spans != null && spans.isNotEmpty()) {
						for (b in spans.indices) {
							if (spans[b] == null) {
								continue
							}

							val start = text.getSpanStart(spans[b])
							val end = text.getSpanEnd(spans[b])

							if (entity.offset <= start && entity.offset + entity.length >= start || entity.offset <= end && entity.offset + entity.length >= end) {
								text.removeSpan(spans[b])
								spans[b] = null
							}
						}
					}
				}

				if (entity is TLMessageEntityCustomEmoji) {
					continue
				}

				val newRun = TextStyleRun()
				newRun.start = entity.offset
				newRun.end = newRun.start + entity.length

				if (entity is TLMessageEntitySpoiler) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_SPOILER
				}
				else if (entity is TLMessageEntityStrike) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_STRIKE
				}
				else if (entity is TLMessageEntityUnderline) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_UNDERLINE
				}
				else if (entity is TLMessageEntityBlockquote) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_QUOTE
				}
				else if (entity is TLMessageEntityBold) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_BOLD
				}
				else if (entity is TLMessageEntityItalic) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_ITALIC
				}
				else if (entity is TLMessageEntityCode || entity is TLMessageEntityPre) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MONO
				}
				else if (entity is TLMessageEntityMentionName) {
					if (!usernames) {
						continue
					}

					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MENTION
					newRun.urlEntity = entity
				}
				else if (entity is TLInputMessageEntityMentionName) {
					if (!usernames) {
						continue
					}

					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MENTION
					newRun.urlEntity = entity
				}
				else {
					if (useManualParse && entity !is TLMessageEntityTextUrl) {
						continue
					}

					if (entity is TLMessageEntityTextUrl && Browser.isPassportUrl(entity.url)) {
						continue
					}

					if (entity is TLMessageEntityMention && !usernames) {
						continue
					}

					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_URL
					newRun.urlEntity = entity
				}

				var b = 0
				var n2 = runs.size

				while (b < n2) {
					val run = runs[b]

					if ((run.styleFlags and TextStyleSpan.FLAG_STYLE_SPOILER) != 0 && (newRun.start >= run.start) && (newRun.end <= run.end)) {
						b++
						continue
					}

					if (newRun.start > run.start) {
						if (newRun.start >= run.end) {
							b++
							continue
						}

						if (newRun.end < run.end) {
							var r = TextStyleRun(newRun)
							r.merge(run)

							b++
							n2++
							runs.add(b, r)

							r = TextStyleRun(run)
							r.start = newRun.end
							b++
							n2++
							runs.add(b, r)
						}
						else {
							val r = TextStyleRun(newRun)
							r.merge(run)
							r.end = run.end
							b++
							n2++
							runs.add(b, r)
						}

						val temp = newRun.start
						newRun.start = run.end
						run.end = temp
					}
					else {
						if (run.start >= newRun.end) {
							b++
							continue
						}

						val temp = run.start

						if (newRun.end == run.end) {
							run.merge(newRun)
						}
						else if (newRun.end < run.end) {
							val r = TextStyleRun(run)
							r.merge(newRun)
							r.end = newRun.end
							b++
							n2++
							runs.add(b, r)

							run.start = newRun.end
						}
						else {
							val r = TextStyleRun(newRun)
							r.start = run.end
							b++
							n2++
							runs.add(b, r)

							run.merge(newRun)
						}

						newRun.end = temp
					}

					b++
				}

				if (newRun.start < newRun.end) {
					runs.add(newRun)
				}
			}

			for (run in runs) {
				val runUrlEntity = run.urlEntity
				var setRun = false
				val url = if (runUrlEntity != null) TextUtils.substring(text, runUrlEntity.offset, runUrlEntity.offset + runUrlEntity.length) else null

				if (runUrlEntity is TLMessageEntityBotCommand) {
					text.setSpan(URLSpanBotCommand(url, t.toInt(), run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TLMessageEntityHashtag || runUrlEntity is TLMessageEntityMention || runUrlEntity is TLMessageEntityCashtag) {
					text.setSpan(URLSpanNoUnderline(url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TLMessageEntityEmail) {
					text.setSpan(URLSpanReplacement("mailto:$url", run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TLMessageEntityUrl) {
					hasUrls = true

					val lowerCase = url?.lowercase()

					if (lowerCase?.contains("://") != true) {
						text.setSpan(URLSpanNoUnderlineBrowser("http://$url", run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					else {
						text.setSpan(URLSpanNoUnderlineBrowser(url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
				}
				else if (runUrlEntity is TLMessageEntityBankCard) {
					hasUrls = true
					text.setSpan(URLSpanNoUnderline("card:$url", run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TLMessageEntityPhone) {
					hasUrls = true

					var tel = PhoneFormat.stripExceptNumbers(url)

					if (url?.startsWith("+") == true) {
						tel = "+$tel"
					}

					text.setSpan(URLSpanBrowser("tel:$tel", run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TLMessageEntityTextUrl) {
					text.setSpan(URLSpanReplacement(runUrlEntity.url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TLMessageEntityMentionName) {
					text.setSpan(URLSpanUserMention("" + runUrlEntity.userId, t.toInt(), run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TLInputMessageEntityMentionName) {
					text.setSpan(URLSpanUserMention("" + runUrlEntity.userId?.userId, t.toInt(), run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if ((run.styleFlags and TextStyleSpan.FLAG_STYLE_MONO) != 0) {
					text.setSpan(URLSpanMono(text, run.start, run.end, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else {
					setRun = true

					val textSpan = TextStyleSpan(run)

					if (messageOwnerId == BuildConfig.AI_BOT_ID) {
						textSpan.setColor(ApplicationLoader.applicationContext.getColor(R.color.brand))
					}
					else {
						textSpan.setColor(0)
					}

					text.setSpan(textSpan, run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}

				if (!setRun && (run.styleFlags and TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
					text.setSpan(TextStyleSpan(run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
			}

			return hasUrls
		}

		@JvmStatic
		fun getFromChatId(message: Message?): Long {
			return getPeerId(message?.fromId)
		}

		@JvmStatic
		fun getPeerId(peer: Peer?): Long {
			if (peer == null) {
				return 0L
			}

			return when (peer) {
				is TLPeerChat -> -peer.chatId
				is TLPeerChannel -> -peer.channelId
				else -> peer.userId
			}
		}

		@JvmStatic
		fun getUnreadFlags(message: Message?): Int {
			if (message == null) {
				return 0
			}

			var flags = 0

			if (!message.unread) {
				flags = flags or 1
			}

			if (!message.mediaUnread) {
				flags = flags or 2
			}

			return flags
		}

		@JvmStatic
		fun getMessageSize(message: Message?): Long {
			val document = when (val media = getMedia(message)) {
				is TLMessageMediaWebPage -> {
					(media.webpage as? TLWebPage)?.document
				}

				is TLMessageMediaGame -> {
					media.game?.document
				}

				else -> {
					(media as? TLMessageMediaDocument)?.document
				}
			}

			return document?.size ?: 0
		}

		@JvmStatic
		fun fixMessagePeer(messages: List<Message>?, channelId: Long) {
			if (messages.isNullOrEmpty() || channelId == 0L) {
				return
			}

			for (message in messages) {
				if (message is TLMessageEmpty) {
					message.peerId = TLPeerChannel().also { it.channelId = channelId }
				}
			}
		}

		@JvmStatic
		fun getChannelId(message: Message?): Long {
			return message?.peerId?.channelId ?: 0L
		}

		@JvmStatic
		fun shouldEncryptPhotoOrVideo(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

//			return if (message is TLMessageSecret) {
//				(getMedia(message) is TLMessageMediaPhoto || isVideoMessage(message)) && message.ttl > 0 && message.ttl <= 60
//			}
//			else {
			return (getMedia(message) is TLMessageMediaPhoto || getMedia(message) is TLMessageMediaDocument) && getMedia(message)?.ttlSeconds != 0
//			}
		}

		fun isSecretPhotoOrVideo(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

//			if (message is TLMessageSecret) {
//				return (getMedia(message) is TLMessageMediaPhoto || isRoundVideoMessage(message) || isVideoMessage(message)) && (message.ttl > 0) && (message.ttl <= 60)
//			}
//			else
			if (message is TLMessage) {
				return (getMedia(message) is TLMessageMediaPhoto || getMedia(message) is TLMessageMediaDocument) && getMedia(message)!!.ttlSeconds != 0
			}

			return false
		}

		@JvmStatic
		fun isSecretMedia(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

//			if (message is TLMessage_secret) {
//				return (getMedia(message) is TLMessageMediaPhoto || isRoundVideoMessage(message) || isVideoMessage(message)) && getMedia(message)?.ttlSeconds != 0
//			}
//			else
			if (message is TLMessage) {
				return (getMedia(message) is TLMessageMediaPhoto || getMedia(message) is TLMessageMediaDocument) && getMedia(message)?.ttlSeconds != 0
			}

			return false
		}

		@JvmStatic
		fun setUnreadFlags(message: Message, flag: Int) {
			message.unread = (flag and 1) == 0
			message.mediaUnread = (flag and 2) == 0
		}

		@JvmStatic
		fun isUnread(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return message?.unread == true
		}

		fun isContentUnread(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return message?.mediaUnread == true
		}

		@JvmStatic
		fun isOut(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return message?.out == true
		}

		@JvmStatic
		fun getDialogId(message: Message?): Long {
			if (message == null) {
				return 0L
			}

			if (message.dialogId == 0L && message.peerId != null) {
				if (message.peerId!!.chatId != 0L) {
					message.dialogId = -message.peerId!!.chatId
				}
				else if (message.peerId!!.channelId != 0L) {
					message.dialogId = -message.peerId!!.channelId
				}
				else if (message.fromId == null || isOut(message)) {
					message.dialogId = message.peerId!!.userId
				}
				else {
					message.dialogId = message.fromId!!.userId
				}
			}

			return message.dialogId
		}

		@JvmStatic
		fun isWebM(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return "video/webm" == document?.mimeType
		}

		@JvmStatic
		fun isVideoSticker(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return isVideoStickerDocument(document)
		}

		@JvmStatic
		fun isStickerDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument) {
				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeSticker) {
						return "image/webp" == document.mimeType || "video/webm" == document.mimeType
					}
				}
			}

			return false
		}

		@JvmStatic
		fun isVideoStickerDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument) {
				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeSticker || attribute is TLDocumentAttributeCustomEmoji) {
						return "video/webm" == document.mimeType
					}
				}
			}

			return false
		}

		@JvmStatic
		fun isStickerHasSet(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument) {
				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeSticker && attribute.stickerset != null && attribute.stickerset !is TLInputStickerSetEmpty) {
						return true
					}
				}
			}

			return false
		}

		@JvmStatic
		fun isAnimatedStickerDocument(document: TLRPC.Document?, allowWithoutSet: Boolean): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument && ("application/x-tgsticker" == document.mimeType && document.thumbs.isNotEmpty() || "application/x-tgsdice" == document.mimeType)) {
				if (allowWithoutSet) {
					return true
				}

				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeSticker) {
						return attribute.stickerset is TLInputStickerSetShortName
					}
					else if (attribute is TLDocumentAttributeCustomEmoji) {
						return true
					}
				}
			}

			return false
		}

		@JvmStatic
		fun canAutoplayAnimatedSticker(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return (isAnimatedStickerDocument(document, true) || isVideoStickerDocument(document)) && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW
		}

		@JvmStatic
		fun isMaskDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument) {
				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeSticker && attribute.mask) {
						return true
					}
				}
			}

			return false
		}

		@JvmStatic
		fun isVoiceDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument) {
				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeAudio) {
						return attribute.voice
					}
				}
			}

			return false
		}

		fun isVoiceWebDocument(webDocument: WebFile?): Boolean {
			contract {
				returns(true) implies (webDocument != null)
			}

			return webDocument?.mimeType == "audio/ogg"
		}

		fun isImageWebDocument(webDocument: WebFile?): Boolean {
			contract {
				returns(true) implies (webDocument != null)
			}

			return !isGifDocument(webDocument) && webDocument?.mimeType?.startsWith("image/") == true
		}

		fun isVideoWebDocument(webDocument: WebFile?): Boolean {
			contract {
				returns(true) implies (webDocument != null)
			}

			return webDocument?.mimeType?.startsWith("video/") == true
		}

		@JvmStatic
		fun isMusicDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument) {
				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeAudio) {
						return !attribute.voice
					}
				}

				if (!document.mimeType.isNullOrEmpty()) {
					val mime = document.mimeType?.lowercase()

					return if (mime == "audio/flac" || mime == "audio/ogg" || mime == "audio/opus" || mime == "audio/x-opus+ogg") {
						true
					}
					else {
						mime == "application/octet-stream" && FileLoader.getDocumentFileName(document)?.endsWith(".opus") == true
					}
				}
			}

			return false
		}

		@JvmStatic
		fun getDocumentVideoThumb(document: TLRPC.Document?): TLRPC.VideoSize? {
			contract {
				returnsNotNull() implies (document != null)
			}

			return (document as? TLDocument)?.videoThumbs?.firstOrNull()
		}

		@JvmStatic
		fun isVideoDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document !is TLDocument) {
				return false
			}

			var isAnimated = false
			var isVideo = false
			var width = 0
			var height = 0

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeVideo) {
					if (attribute.roundMessage) {
						return false
					}

					isVideo = true
					width = attribute.w
					height = attribute.h
				}
				else if (attribute is TLDocumentAttributeAnimated) {
					isAnimated = true
				}
			}

			if (isAnimated && (width > 1280 || height > 1280)) {
				isAnimated = false
			}

			if (SharedConfig.streamMkv && !isVideo && "video/x-matroska" == document.mimeType) {
				isVideo = true
			}

			return isVideo && !isAnimated
		}

		@JvmStatic
		fun getDocument(message: Message?): TLRPC.Document? {
			contract {
				returnsNotNull() implies (message != null)
			}

			val media = getMedia(message)

			if (media is TLMessageMediaWebPage) {
				return (media.webpage as? TLWebPage)?.document
			}
			else if (media is TLMessageMediaGame) {
				return media.game?.document
			}

			return (media as? TLMessageMediaDocument)?.document
		}

		@JvmStatic
		fun getPhoto(message: Message?): TLRPC.Photo? {
			contract {
				returnsNotNull() implies (message != null)
			}

			val media = getMedia(message)

			if (media is TLMessageMediaWebPage) {
				return (media.webpage as? TLWebPage)?.photo
			}

			return (media as? TLMessageMediaPhoto)?.photo
		}

		@JvmStatic
		fun isStickerMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return isStickerDocument(getDocument(message))
		}

		@JvmStatic
		fun isAnimatedStickerMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val isSecretChat = DialogObject.isEncryptedDialog(message?.dialogId)

			if (isSecretChat && message?.stickerVerified != 1) {
				return false
			}

			return isAnimatedStickerDocument(getDocument(message), !isSecretChat || message?.out == true)
		}

		fun isLocationMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			return media is TLMessageMediaGeo || media is TLMessageMediaGeoLive || media is TLMessageMediaVenue
		}

		fun isMaskMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return isMaskDocument(getDocument(message))
		}

		@JvmStatic
		fun isMusicMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return isMusicDocument(getDocument(message))
		}

		@JvmStatic
		fun isGifMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (media is TLMessageMediaWebPage) {
				return isGifDocument((media.webpage as? TLWebPage)?.document)
			}

			return media != null && isGifDocument(getDocument(message), (message as? TLMessage)?.groupedId != 0L)
		}

		@JvmStatic
		fun isRoundVideoMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return isRoundVideoDocument(getDocument(message))
		}

		@JvmStatic
		fun isPhoto(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (media is TLMessageMediaWebPage) {
				return (media.webpage as? TLWebPage)?.photo is TLPhoto && (media.webpage as? TLWebPage)?.document !is TLDocument
			}

			return media is TLMessageMediaPhoto
		}

		@JvmStatic
		fun isVoiceMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return isVoiceDocument(getDocument(message))
		}

		@JvmStatic
		fun isNewGifMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return isNewGifDocument(getDocument(message))
		}

		fun isLiveLocationMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return getMedia(message) is TLMessageMediaGeoLive
		}

		@JvmStatic
		fun isVideoMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val document = getDocument(message)

			if (isVideoSticker(document)) {
				return false
			}

			return isVideoDocument(document)
		}

		@JvmStatic
		fun isGameMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return getMedia(message) is TLMessageMediaGame
		}

		fun isInvoiceMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return getMedia(message) is TLMessageMediaInvoice
		}

		@JvmStatic
		fun getInputStickerSet(message: Message?): InputStickerSet? {
			contract {
				returnsNotNull() implies (message != null)
			}

			val document = getDocument(message)
			return getInputStickerSet(document)
		}

		@JvmStatic
		fun getInputStickerSet(document: TLRPC.Document?): InputStickerSet? {
			contract {
				returns(true) implies (document != null)
			}

			if (document !is TLDocument) {
				return null
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeSticker || attribute is TLDocumentAttributeCustomEmoji) {
					if (attribute.stickerset is TLInputStickerSetEmpty) {
						return null
					}

					return attribute.stickerset
				}
			}

			return null
		}

		@JvmStatic
		@JvmOverloads
		fun findAnimatedEmojiEmoticon(document: TLRPC.Document?, fallback: String? = "\uD83D\uDE00"): String? {
			if (document !is TLDocument) {
				return fallback
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeCustomEmoji || attribute is TLDocumentAttributeSticker) {
					return attribute.alt
				}
			}

			return fallback
		}

		fun isAnimatedEmoji(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document !is TLDocument) {
				return false
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeCustomEmoji) {
					return true
				}
			}

			return false
		}

		@JvmStatic
		fun isFreeEmoji(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document !is TLDocument) {
				return false
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeCustomEmoji) {
					return attribute.free
				}
			}

			return false
		}

		@JvmStatic
		fun isPremiumEmojiPack(set: TLRPC.TLMessagesStickerSet?): Boolean {
			contract {
				returns(true) implies (set != null)
			}

			if (set?.set != null && set.set?.emojis != true) {
				return false
			}

			if (set?.documents != null) {
				for (document in set.documents) {
					if (!isFreeEmoji(document)) {
						return true
					}
				}
			}

			return false
		}

		@JvmStatic
		fun isPremiumEmojiPack(covered: StickerSetCovered?): Boolean {
			contract {
				returns(true) implies (covered != null)
			}

			if (covered?.set != null && covered.set?.emojis != true) {
				return false
			}

			val documents = if (covered is TLStickerSetFullCovered) covered.documents else (covered as? TLRPC.TLStickerSetMultiCovered)?.covers

			if (covered != null && documents != null) {
				for (document in documents) {
					if (!isFreeEmoji(document)) {
						return true
					}
				}
			}

			return false
		}

		fun getStickerSetId(document: TLRPC.Document?): Long {
			if (document !is TLDocument) {
				return -1
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeSticker) {
					if (attribute.stickerset is TLInputStickerSetEmpty) {
						return -1
					}
					else if (attribute.stickerset is TLInputStickerSetID) {
						return (attribute.stickerset as TLInputStickerSetID).id
					}
				}
			}

			return -1
		}

		@JvmStatic
		fun getStickerSetName(document: TLRPC.Document?): String? {
			contract {
				returnsNotNull() implies (document != null)
			}

			if (document !is TLDocument) {
				return null
			}

			for (attribute in document.attributes) {
				if (attribute is TLDocumentAttributeSticker) {
					if (attribute.stickerset is TLInputStickerSetEmpty) {
						return null
					}
					else if (attribute.stickerset is TLInputStickerSetShortName) {
						return (attribute.stickerset as TLInputStickerSetShortName).shortName
					}
				}
			}

			return null
		}

		fun isDocumentHasAttachedStickers(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document is TLDocument) {
				for (attribute in document.attributes) {
					if (attribute is TLDocumentAttributeHasStickers) {
						return true
					}
				}
			}

			return false
		}

		fun isForwardedMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			if (message == null) {
				return false
			}

			return (message.flags and TLRPC.MESSAGE_FLAG_FWD) != 0 && message.fwdFrom != null
		}

		fun isMediaEmpty(message: Message?): Boolean {
			return message == null || getMedia(message) == null || getMedia(message) is TLMessageMediaEmpty || getMedia(message) is TLMessageMediaWebPage
		}

		fun isMediaEmptyWebpage(message: Message?): Boolean {
			return message == null || getMedia(message) == null || getMedia(message) is TLMessageMediaEmpty
		}

		fun canEditMessageAnytime(currentAccount: Int, message: Message?, chat: Chat?): Boolean {
			@Suppress("NAME_SHADOWING") var chat = chat

			val document = getDocument(message)

			if (message?.peerId == null || getMedia(message) != null && (isRoundVideoDocument(document) || isStickerDocument(document) || isAnimatedStickerDocument(document, true)) || message.action != null && message.action !is TLMessageActionEmpty || isForwardedMessage(message) || (message as? TLMessage)?.viaBotId != 0L || message.id < 0) {
				return false
			}

			if (message.fromId is TLPeerUser && message.fromId?.userId == message.peerId?.userId && message.fromId?.userId == UserConfig.getInstance(currentAccount).getClientUserId() && !isLiveLocationMessage(message)) {
				return true
			}

			if (chat == null && message.peerId?.channelId != 0L) {
				chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(message.peerId?.channelId)

				if (chat == null) {
					return false
				}
			}

			if (ChatObject.isChannel(chat) && !chat.megagroup && (chat.creator || chat.adminRights?.editMessages == true)) {
				return true
			}

			return message.out && chat != null && chat.megagroup && (chat.creator || chat.adminRights?.pinMessages == true || chat.defaultBannedRights?.pinMessages == false)
		}

		fun canEditMessageScheduleTime(currentAccount: Int, message: Message?, chat: Chat?): Boolean {
			@Suppress("NAME_SHADOWING") var chat = chat

			if (chat == null && message?.peerId?.channelId != 0L) {
				chat = MessagesController.getInstance(currentAccount).getChat(message?.peerId?.channelId)

				if (chat == null) {
					return false
				}
			}

			if (!ChatObject.isChannel(chat) || chat.megagroup || chat.creator) {
				return true
			}

			return chat.adminRights != null && (chat.adminRights?.editMessages == true || message?.out == true)
		}

		fun canEditMessage(currentAccount: Int, message: Message?, chat: Chat?, scheduled: Boolean): Boolean {
			@Suppress("NAME_SHADOWING") var chat = chat

			if (scheduled && message!!.date < ConnectionsManager.getInstance(currentAccount).currentTime - 60) {
				return false
			}

			if (chat != null && chat.left && (!chat.megagroup || !chat.hasLink)) {
				return false
			}

			val document = getDocument(message)

			if (message?.peerId == null || getMedia(message) != null && (isRoundVideoDocument(document) || isStickerDocument(document) || isAnimatedStickerDocument(document, true) || isLocationMessage(message)) || message.action != null && message.action !is TLMessageActionEmpty || isForwardedMessage(message) || (message as? TLMessage)?.viaBotId != 0L || message.id < 0) {
				return false
			}

			if (message.fromId is TLPeerUser && message.fromId?.userId == message.peerId?.userId && message.fromId?.userId == UserConfig.getInstance(currentAccount).getClientUserId() && !isLiveLocationMessage(message) && getMedia(message) !is TLMessageMediaContact) {
				return true
			}

			if (chat == null && message.peerId?.channelId != 0L) {
				chat = MessagesController.getInstance(currentAccount).getChat(message.peerId?.channelId)

				if (chat == null) {
					return false
				}
			}

			if (getMedia(message) != null && getMedia(message) !is TLMessageMediaEmpty && getMedia(message) !is TLMessageMediaPhoto && getMedia(message) !is TLMessageMediaDocument && getMedia(message) !is TLMessageMediaWebPage) {
				return false
			}

			if (ChatObject.isChannel(chat) && !chat.megagroup && (chat.creator || chat.adminRights?.editMessages == true)) {
				return true
			}

			if (message.out && chat != null && chat.megagroup && (chat.creator || chat.adminRights?.pinMessages == true || chat.defaultBannedRights?.pinMessages == false)) {
				return true
			}

			if (!scheduled && abs((message.date - ConnectionsManager.getInstance(currentAccount).currentTime).toDouble()) > MessagesController.getInstance(currentAccount).maxEditTime) {
				return false
			}

			if (message.peerId?.channelId == 0L) {
				return (message.out || message.fromId is TLPeerUser && message.fromId?.userId == UserConfig.getInstance(currentAccount).getClientUserId()) && (getMedia(message) is TLMessageMediaPhoto || getMedia(message) is TLMessageMediaDocument && !isStickerMessage(message) && !isAnimatedStickerMessage(message) || getMedia(message) is TLMessageMediaEmpty || getMedia(message) is TLMessageMediaWebPage || getMedia(message) == null)
			}

			if (chat != null && chat.megagroup && message.out || chat != null && !chat.megagroup && (chat.creator || chat.adminRights != null && (chat.adminRights?.editMessages == true || message.out && chat.adminRights?.postMessages == true)) && message.post) {
				return getMedia(message) is TLMessageMediaPhoto || getMedia(message) is TLMessageMediaDocument && !isStickerMessage(message) && !isAnimatedStickerMessage(message) || getMedia(message) is TLMessageMediaEmpty || getMedia(message) is TLMessageMediaWebPage || getMedia(message) == null
			}

			return false
		}

		@JvmStatic
		fun canDeleteMessage(currentAccount: Int, inScheduleMode: Boolean, message: Message?, chat: Chat?): Boolean {
			@Suppress("NAME_SHADOWING") var chat = chat

			if (message == null) {
				return false
			}

			if (ChatObject.isChannelAndNotMegaGroup(chat) && message.action is TLMessageActionChatJoinedByRequest) {
				return false
			}

			if (message.id < 0) {
				return true
			}

			if (chat == null && message.peerId?.channelId != 0L) {
				chat = MessagesController.getInstance(currentAccount).getChat(message.peerId?.channelId)
			}

			if (ChatObject.isChannel(chat)) {
				if (inScheduleMode && !chat.megagroup) {
					return chat.creator || chat.adminRights != null && (chat.adminRights?.deleteMessages == true || message.out)
				}

				if (message.out && message is TLMessageService) {
					return message.id != 1 && ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES)
				}

				return inScheduleMode || message.id != 1 && (chat.creator || chat.adminRights != null && (chat.adminRights?.deleteMessages == true || message.out && (chat.megagroup || chat.adminRights?.postMessages == true)) || chat.megagroup && message.out)
			}

			return inScheduleMode || isOut(message) || !ChatObject.isChannel(chat)
		}

		@JvmStatic
		fun getReplyToDialogId(message: Message): Long {
			if (message.replyTo == null) {
				return 0L
			}

			if (message.replyTo?.replyToPeerId != null) {
				return getPeerId(message.replyTo?.replyToPeerId)
			}

			return getDialogId(message)
		}
	}
}
