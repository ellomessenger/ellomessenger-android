/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.messenger.messageobject

import android.graphics.Paint.FontMetricsInt
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.text.LineBreaker
import android.net.Uri
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
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInlineResult
import org.telegram.tgnet.TLRPC.ChannelParticipant
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.ChatInvite
import org.telegram.tgnet.TLRPC.ChatReactions
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.tlrpc.MessageEntity
import org.telegram.tgnet.TLRPC.MessageMedia
import org.telegram.tgnet.TLRPC.MessagePeerReaction
import org.telegram.tgnet.TLRPC.PageBlock
import org.telegram.tgnet.TLRPC.Peer
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.PollResults
import org.telegram.tgnet.TLRPC.StickerSetCovered
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEvent
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeAbout
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeAvailableReactions
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeHistoryTTL
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeLinkedChat
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeLocation
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangePhoto
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeStickerSet
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeTheme
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeTitle
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionChangeUsername
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionDefaultBannedRights
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionDeleteMessage
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionDiscardGroupCall
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionEditMessage
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionExportedInviteDelete
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionExportedInviteEdit
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionExportedInviteRevoke
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantInvite
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantJoin
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantJoinByInvite
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantJoinByRequest
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantLeave
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantMute
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantToggleBan
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantUnmute
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionParticipantVolume
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionSendMessage
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionStartGroupCall
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionStopPoll
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionToggleGroupCallSetting
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionToggleInvites
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionToggleNoForwards
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionTogglePreHistoryHidden
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionToggleSignatures
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionToggleSlowMode
import org.telegram.tgnet.TLRPC.TL_channelAdminLogEventActionUpdatePinned
import org.telegram.tgnet.TLRPC.TL_channelLocation
import org.telegram.tgnet.TLRPC.TL_channelLocationEmpty
import org.telegram.tgnet.TLRPC.TL_channelParticipant
import org.telegram.tgnet.TLRPC.TL_channelParticipantAdmin
import org.telegram.tgnet.TLRPC.TL_channelParticipantCreator
import org.telegram.tgnet.TLRPC.TL_chatAdminRights
import org.telegram.tgnet.TLRPC.TL_chatInviteExported
import org.telegram.tgnet.TLRPC.TL_chatInvitePublicJoinRequests
import org.telegram.tgnet.TLRPC.TL_chatReactionsAll
import org.telegram.tgnet.TLRPC.TL_chatReactionsSome
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionScreenshotMessages
import org.telegram.tgnet.TLRPC.TL_decryptedMessageActionSetMessageTTL
import org.telegram.tgnet.TLRPC.TL_document
import org.telegram.tgnet.TLRPC.TL_documentAttributeAnimated
import org.telegram.tgnet.TLRPC.TL_documentAttributeAudio
import org.telegram.tgnet.TLRPC.TL_documentAttributeCustomEmoji
import org.telegram.tgnet.TLRPC.TL_documentAttributeHasStickers
import org.telegram.tgnet.TLRPC.TL_documentAttributeImageSize
import org.telegram.tgnet.TLRPC.TL_documentAttributeSticker
import org.telegram.tgnet.TLRPC.TL_documentAttributeVideo
import org.telegram.tgnet.TLRPC.TL_documentEmpty
import org.telegram.tgnet.TLRPC.TL_documentEncrypted
import org.telegram.tgnet.TLRPC.TL_fileLocationUnavailable
import org.telegram.tgnet.TLRPC.TL_game
import org.telegram.tgnet.tlrpc.TL_inputMessageEntityMentionName
import org.telegram.tgnet.TLRPC.TL_inputStickerSetEmpty
import org.telegram.tgnet.TLRPC.TL_inputStickerSetShortName
import org.telegram.tgnet.TLRPC.TL_keyboardButtonBuy
import org.telegram.tgnet.TLRPC.TL_messageActionBotAllowed
import org.telegram.tgnet.TLRPC.TL_messageActionChatAddUser
import org.telegram.tgnet.TLRPC.TL_messageActionChatCreate
import org.telegram.tgnet.TLRPC.TL_messageActionChatDeletePhoto
import org.telegram.tgnet.TLRPC.TL_messageActionChatDeleteUser
import org.telegram.tgnet.TLRPC.TL_messageActionChatEditPhoto
import org.telegram.tgnet.TLRPC.TL_messageActionChatEditTitle
import org.telegram.tgnet.TLRPC.TL_messageActionChatJoinedByLink
import org.telegram.tgnet.TLRPC.TL_messageActionChatJoinedByRequest
import org.telegram.tgnet.TLRPC.TL_messageActionContactSignUp
import org.telegram.tgnet.TLRPC.TL_messageActionCustomAction
import org.telegram.tgnet.TLRPC.TL_messageActionEmpty
import org.telegram.tgnet.TLRPC.TL_messageActionGeoProximityReached
import org.telegram.tgnet.TLRPC.TL_messageActionGiftPremium
import org.telegram.tgnet.TLRPC.TL_messageActionGroupCall
import org.telegram.tgnet.TLRPC.TL_messageActionGroupCallScheduled
import org.telegram.tgnet.TLRPC.TL_messageActionHistoryClear
import org.telegram.tgnet.TLRPC.TL_messageActionInviteToGroupCall
import org.telegram.tgnet.TLRPC.TL_messageActionLoginUnknownLocation
import org.telegram.tgnet.TLRPC.TL_messageActionPhoneCall
import org.telegram.tgnet.TLRPC.TL_messageActionSecureValuesSent
import org.telegram.tgnet.TLRPC.TL_messageActionSetChatTheme
import org.telegram.tgnet.TLRPC.TL_messageActionSetMessagesTTL
import org.telegram.tgnet.TLRPC.TL_messageActionTTLChange
import org.telegram.tgnet.TLRPC.TL_messageActionUserUpdatedPhoto
import org.telegram.tgnet.TLRPC.TL_messageActionWebViewDataSent
import org.telegram.tgnet.TLRPC.TL_messageEmpty
import org.telegram.tgnet.TLRPC.TL_messageEncryptedAction
import org.telegram.tgnet.tlrpc.TL_messageEntityBankCard
import org.telegram.tgnet.tlrpc.TL_messageEntityBlockquote
import org.telegram.tgnet.tlrpc.TL_messageEntityBold
import org.telegram.tgnet.tlrpc.TL_messageEntityBotCommand
import org.telegram.tgnet.tlrpc.TL_messageEntityCashtag
import org.telegram.tgnet.tlrpc.TL_messageEntityCode
import org.telegram.tgnet.tlrpc.TL_messageEntityCustomEmoji
import org.telegram.tgnet.tlrpc.TL_messageEntityEmail
import org.telegram.tgnet.tlrpc.TL_messageEntityHashtag
import org.telegram.tgnet.tlrpc.TL_messageEntityItalic
import org.telegram.tgnet.tlrpc.TL_messageEntityMention
import org.telegram.tgnet.tlrpc.TL_messageEntityMentionName
import org.telegram.tgnet.tlrpc.TL_messageEntityPhone
import org.telegram.tgnet.tlrpc.TL_messageEntityPre
import org.telegram.tgnet.tlrpc.TL_messageEntitySpoiler
import org.telegram.tgnet.tlrpc.TL_messageEntityStrike
import org.telegram.tgnet.tlrpc.TL_messageEntityTextUrl
import org.telegram.tgnet.tlrpc.TL_messageEntityUnderline
import org.telegram.tgnet.tlrpc.TL_messageEntityUrl
import org.telegram.tgnet.TLRPC.TL_messageExtendedMedia
import org.telegram.tgnet.TLRPC.TL_messageExtendedMediaPreview
import org.telegram.tgnet.TLRPC.TL_messageForwarded_old
import org.telegram.tgnet.TLRPC.TL_messageForwarded_old2
import org.telegram.tgnet.TLRPC.TL_messageMediaContact
import org.telegram.tgnet.TLRPC.TL_messageMediaDice
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument_layer68
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument_layer74
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument_old
import org.telegram.tgnet.TLRPC.TL_messageMediaEmpty
import org.telegram.tgnet.TLRPC.TL_messageMediaGame
import org.telegram.tgnet.TLRPC.TL_messageMediaGeo
import org.telegram.tgnet.TLRPC.TL_messageMediaGeoLive
import org.telegram.tgnet.TLRPC.TL_messageMediaInvoice
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto_layer68
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto_layer74
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto_old
import org.telegram.tgnet.TLRPC.TL_messageMediaPoll
import org.telegram.tgnet.TLRPC.TL_messageMediaUnsupported
import org.telegram.tgnet.TLRPC.TL_messageMediaVenue
import org.telegram.tgnet.TLRPC.TL_messageMediaWebPage
import org.telegram.tgnet.TLRPC.TL_messagePeerReaction
import org.telegram.tgnet.TLRPC.TL_messageService
import org.telegram.tgnet.TLRPC.TL_message_old
import org.telegram.tgnet.TLRPC.TL_message_old2
import org.telegram.tgnet.TLRPC.TL_message_old3
import org.telegram.tgnet.TLRPC.TL_message_old4
import org.telegram.tgnet.TLRPC.TL_message_secret
import org.telegram.tgnet.TLRPC.TL_messages_stickerSet
import org.telegram.tgnet.TLRPC.TL_pageBlockCollage
import org.telegram.tgnet.TLRPC.TL_pageBlockPhoto
import org.telegram.tgnet.TLRPC.TL_pageBlockSlideshow
import org.telegram.tgnet.TLRPC.TL_pageBlockVideo
import org.telegram.tgnet.TLRPC.TL_peerChannel
import org.telegram.tgnet.TLRPC.TL_peerChat
import org.telegram.tgnet.TLRPC.TL_peerUser
import org.telegram.tgnet.TLRPC.TL_phoneCallDiscardReasonBusy
import org.telegram.tgnet.TLRPC.TL_phoneCallDiscardReasonMissed
import org.telegram.tgnet.tlrpc.TL_photo
import org.telegram.tgnet.TLRPC.TL_photoCachedSize
import org.telegram.tgnet.TLRPC.TL_photoEmpty
import org.telegram.tgnet.TLRPC.TL_photoSizeEmpty
import org.telegram.tgnet.TLRPC.TL_photoStrippedSize
import org.telegram.tgnet.TLRPC.TL_pollAnswer
import org.telegram.tgnet.TLRPC.TL_replyInlineMarkup
import org.telegram.tgnet.TLRPC.TL_secureValueTypeAddress
import org.telegram.tgnet.TLRPC.TL_secureValueTypeBankStatement
import org.telegram.tgnet.TLRPC.TL_secureValueTypeDriverLicense
import org.telegram.tgnet.TLRPC.TL_secureValueTypeEmail
import org.telegram.tgnet.TLRPC.TL_secureValueTypeIdentityCard
import org.telegram.tgnet.TLRPC.TL_secureValueTypeInternalPassport
import org.telegram.tgnet.TLRPC.TL_secureValueTypePassport
import org.telegram.tgnet.TLRPC.TL_secureValueTypePassportRegistration
import org.telegram.tgnet.TLRPC.TL_secureValueTypePersonalDetails
import org.telegram.tgnet.TLRPC.TL_secureValueTypePhone
import org.telegram.tgnet.TLRPC.TL_secureValueTypeRentalAgreement
import org.telegram.tgnet.TLRPC.TL_secureValueTypeTemporaryRegistration
import org.telegram.tgnet.TLRPC.TL_secureValueTypeUtilityBill
import org.telegram.tgnet.TLRPC.TL_stickerSetFullCovered
import org.telegram.tgnet.TLRPC.TL_webPage
import org.telegram.tgnet.TLRPC.VideoSize
import org.telegram.tgnet.TLRPC.WebDocument
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.ReactionCount
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_chatBannedRights
import org.telegram.tgnet.tlrpc.TL_message
import org.telegram.tgnet.tlrpc.TL_messageReactions
import org.telegram.tgnet.tlrpc.TL_reactionCount
import org.telegram.tgnet.tlrpc.TL_reactionCustomEmoji
import org.telegram.tgnet.tlrpc.TL_reactionEmoji
import org.telegram.tgnet.tlrpc.User
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
import org.telegram.ui.Components.URLSpanReplacement
import org.telegram.ui.Components.URLSpanUserMention
import org.telegram.ui.Components.spoilers.SpoilerEffect
import java.io.File
import java.net.URLEncoder
import java.util.AbstractMap
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.Objects
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
	var checkedVotes: ArrayList<TL_pollAnswer>? = null
	var editingMessageSearchWebPage: Boolean = false
	private var webPageDescriptionEntities: ArrayList<MessageEntity>? = null
	var previousMessage: String? = null
	var previousMedia: MessageMedia? = null
	var previousMessageEntities: ArrayList<MessageEntity>? = null
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
	var isMediaSale: Boolean = false
	var isMediaSaleInfo: Boolean = false
	private var mediaSalePrice: Double = 0.0
	private var mediaSaleQuantity: Int = 0
	var mediaSaleHash: String? = null
	private var mediaSaleTitle: String? = null
	private var mediaSaleDescription: String? = null

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
	var currentEvent: TL_channelAdminLogEvent? = null

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
		isMediaSale = message.is_media_sale
		mediaSaleTitle = message.title
		// mediaSaleDescription = message.description // FIXME: uncomment
		mediaSalePrice = message.price
		mediaSaleQuantity = message.quantity
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

		isMediaSale = message.is_media_sale
		mediaSaleTitle = message.title
		// mediaSaleDescription = message?.description // FIXME: uncomment
		mediaSalePrice = message.price
		mediaSaleQuantity = message.quantity

		if (replyToMessage != null) {
			replyMessageObject = replyToMessage
		}

		eventId = eid
		wasUnread = !message.out && message.unread

		if (message.replyMessage != null) {
			replyMessageObject = MessageObject(currentAccount, message.replyMessage!!, null, users, chats, sUsers, sChats, false, checkMediaExists, eid)
		}
		else if (message.reply_to != null) {
			val m = MessagesStorage.getInstance(currentAccount).getMessage(message.dialog_id, (message.reply_to?.reply_to_msg_id ?: 0).toLong())

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
			val paint = if (getMedia(message) is TL_messageMediaGame) {
				Theme.chat_msgGameTextPaint
			}
			else {
				Theme.chat_msgTextPaint
			}

			val emojiOnly = if (allowsBigEmoji()) IntArray(1) else null

			messageText = Emoji.replaceEmoji(messageText, paint.fontMetricsInt, false, emojiOnly)
			messageText = replaceAnimatedEmoji(messageText, message.entities, paint.fontMetricsInt)

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

	constructor(accountNum: Int, event: TL_channelAdminLogEvent, messageObjects: ArrayList<MessageObject>, messagesByDays: HashMap<String, ArrayList<MessageObject>>, chat: Chat, mid: IntArray, addToEnd: Boolean) {
		currentEvent = event
		currentAccount = accountNum

		for (messageObject in messageObjects) {
			if (messageObject.isMediaSale) {
				isMediaSale = true
				mediaSaleTitle = messageObject.mediaSaleTitle
				mediaSaleDescription = messageObject.mediaSaleDescription
				mediaSalePrice = messageObject.mediaSalePrice
				mediaSaleQuantity = messageObject.mediaSaleQuantity
			}
		}

		val context = ApplicationLoader.applicationContext

		var fromUser: User? = null

		if (event.user_id > 0) {
			fromUser = MessagesController.getInstance(currentAccount).getUser(event.user_id)
		}

		val rightNow: Calendar = GregorianCalendar()
		rightNow.timeInMillis = event.date.toLong() * 1000

		val dateDay = rightNow[Calendar.DAY_OF_YEAR]
		val dateYear = rightNow[Calendar.YEAR]
		val dateMonth = rightNow[Calendar.MONTH]

		dateKey = String.format(Locale.getDefault(), "%d_%02d_%02d", dateYear, dateMonth, dateDay)
		monthKey = String.format(Locale.getDefault(), "%d_%02d", dateYear, dateMonth)

		val peer_id: Peer = TL_peerChannel()
		peer_id.channel_id = chat.id

		var message: Message? = null
		var webPageDescriptionEntities: ArrayList<MessageEntity>? = null

		if (event.action is TL_channelAdminLogEventActionChangeTitle) {
			val title = (event.action as TL_channelAdminLogEventActionChangeTitle).new_value

			messageText = if (chat.megagroup) {
				replaceWithLink(LocaleController.formatString("EventLogEditedGroupTitle", R.string.EventLogEditedGroupTitle, title), "un1", fromUser)
			}
			else {
				replaceWithLink(LocaleController.formatString("EventLogEditedChannelTitle", R.string.EventLogEditedChannelTitle, title), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionChangePhoto) {
			val action = event.action as TL_channelAdminLogEventActionChangePhoto

			messageOwner = TL_messageService()

			if (action.new_photo is TL_photoEmpty) {
				messageOwner?.action = TL_messageActionChatDeletePhoto()

				messageText = if (chat.megagroup) {
					replaceWithLink(context.getString(R.string.EventLogRemovedWGroupPhoto), "un1", fromUser)
				}
				else {
					replaceWithLink(context.getString(R.string.EventLogRemovedChannelPhoto), "un1", fromUser)
				}
			}
			else {
				messageOwner?.action = TL_messageActionChatEditPhoto()
				messageOwner?.action?.photo = action.new_photo

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
		else if (event.action is TL_channelAdminLogEventActionParticipantJoin) {
			messageText = if (chat.megagroup) {
				replaceWithLink(context.getString(R.string.EventLogGroupJoined), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogChannelJoined), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionParticipantLeave) {
			messageOwner = TL_messageService()
			messageOwner?.action = TL_messageActionChatDeleteUser()
			messageOwner?.action?.user_id = event.user_id

			messageText = if (chat.megagroup) {
				replaceWithLink(context.getString(R.string.EventLogLeftGroup), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogLeftChannel), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionParticipantInvite) {
			val action = event.action as TL_channelAdminLogEventActionParticipantInvite
			messageOwner = TL_messageService()
			messageOwner?.action = TL_messageActionChatAddUser()

			val peerId = getPeerId(action.participant.peer)

			val whoUser = if (peerId > 0) {
				MessagesController.getInstance(currentAccount).getUser(peerId)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-peerId)
			}

			if (messageOwner?.from_id is TL_peerUser && peerId == messageOwner?.from_id?.user_id) {
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
		else if (event.action is TL_channelAdminLogEventActionParticipantToggleAdmin || event.action is TL_channelAdminLogEventActionParticipantToggleBan && (event.action as TL_channelAdminLogEventActionParticipantToggleBan).prev_participant is TL_channelParticipantAdmin && (event.action as TL_channelAdminLogEventActionParticipantToggleBan).new_participant is TL_channelParticipant) {
			val prev_participant: ChannelParticipant
			val new_participant: ChannelParticipant

			if (event.action is TL_channelAdminLogEventActionParticipantToggleAdmin) {
				val action = event.action as TL_channelAdminLogEventActionParticipantToggleAdmin
				prev_participant = action.prev_participant
				new_participant = action.new_participant
			}
			else {
				val action = event.action as TL_channelAdminLogEventActionParticipantToggleBan
				prev_participant = action.prev_participant
				new_participant = action.new_participant
			}

			messageOwner = TL_message()

			val peerId = getPeerId(prev_participant.peer)

			val whoUser: TLObject? = if (peerId > 0) {
				MessagesController.getInstance(currentAccount).getUser(peerId)
			}
			else {
				MessagesController.getInstance(currentAccount).getUser(-peerId)
			}

			val rights: StringBuilder

			if (prev_participant !is TL_channelParticipantCreator && new_participant is TL_channelParticipantCreator) {
				val str = context.getString(R.string.EventLogChangedOwnership)
				val offset = str.indexOf("%1\$s")
				rights = StringBuilder(String.format(str, getUserName(whoUser, messageOwner!!.entities, offset)))
			}
			else {
				var o = prev_participant.admin_rights
				var n = new_participant.admin_rights

				if (o == null) {
					o = TL_chatAdminRights()
				}

				if (n == null) {
					n = TL_chatAdminRights()
				}
				val str = if (n.other) {
					context.getString(R.string.EventLogPromotedNoRights)
				}
				else {
					context.getString(R.string.EventLogPromoted)
				}

				val offset = str.indexOf("%1\$s")

				rights = StringBuilder(String.format(str, getUserName(whoUser, messageOwner!!.entities, offset)))
				rights.append("\n")

				if (!TextUtils.equals(prev_participant.rank, new_participant.rank)) {
					if (new_participant.rank.isNullOrEmpty()) {
						rights.append('\n').append('-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedRemovedTitle))
					}
					else {
						rights.append('\n').append('+').append(' ')
						rights.append(LocaleController.formatString("EventLogPromotedTitle", R.string.EventLogPromotedTitle, new_participant.rank))
					}
				}

				if (o.change_info != n.change_info) {
					rights.append('\n').append(if (n.change_info) '+' else '-').append(' ')
					rights.append(if (chat.megagroup) context.getString(R.string.EventLogPromotedChangeGroupInfo) else context.getString(R.string.EventLogPromotedChangeChannelInfo))
				}

				if (!chat.megagroup) {
					if (o.post_messages != n.post_messages) {
						rights.append('\n').append(if (n.post_messages) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedPostMessages))
					}

					if (o.edit_messages != n.edit_messages) {
						rights.append('\n').append(if (n.edit_messages) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedEditMessages))
					}
				}

				if (o.delete_messages != n.delete_messages) {
					rights.append('\n').append(if (n.delete_messages) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogPromotedDeleteMessages))
				}

				if (o.add_admins != n.add_admins) {
					rights.append('\n').append(if (n.add_admins) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogPromotedAddAdmins))
				}

				if (o.anonymous != n.anonymous) {
					rights.append('\n').append(if (n.anonymous) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogPromotedSendAnonymously))
				}

				if (chat.megagroup) {
					if (o.ban_users != n.ban_users) {
						rights.append('\n').append(if (n.ban_users) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedBanUsers))
					}

					if (o.manage_call != n.manage_call) {
						rights.append('\n').append(if (n.manage_call) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedManageCall))
					}
				}

				if (o.invite_users != n.invite_users) {
					rights.append('\n').append(if (n.invite_users) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogPromotedAddUsers))
				}

				if (chat.megagroup) {
					if (o.pin_messages != n.pin_messages) {
						rights.append('\n').append(if (n.pin_messages) '+' else '-').append(' ')
						rights.append(context.getString(R.string.EventLogPromotedPinMessages))
					}
				}
			}

			messageText = rights.toString()
		}
		else if (event.action is TL_channelAdminLogEventActionDefaultBannedRights) {
			val bannedRights = event.action as TL_channelAdminLogEventActionDefaultBannedRights

			messageOwner = TL_message()

			var o = bannedRights.prev_banned_rights
			var n = bannedRights.new_banned_rights
			val rights = StringBuilder(context.getString(R.string.EventLogDefaultPermissions))
			var added = false

			if (o == null) {
				o = TL_chatBannedRights()
			}

			if (n == null) {
				n = TL_chatBannedRights()
			}

			if (o.send_messages != n.send_messages) {
				rights.append('\n')
				added = true
				rights.append('\n').append(if (!n.send_messages) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendMessages))
			}

			if (o.send_stickers != n.send_stickers || o.send_inline != n.send_inline || o.send_gifs != n.send_gifs || o.send_games != n.send_games) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.send_stickers) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendStickers))
			}

			if (o.send_media != n.send_media) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.send_media) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendMedia))
			}

			if (o.send_polls != n.send_polls) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.send_polls) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendPolls))
			}

			if (o.embed_links != n.embed_links) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.embed_links) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedSendEmbed))
			}

			if (o.change_info != n.change_info) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.change_info) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedChangeInfo))
			}

			if (o.invite_users != n.invite_users) {
				if (!added) {
					rights.append('\n')
					added = true
				}

				rights.append('\n').append(if (!n.invite_users) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedInviteUsers))
			}

			if (o.pin_messages != n.pin_messages) {
				if (!added) {
					rights.append('\n')
				}

				rights.append('\n').append(if (!n.pin_messages) '+' else '-').append(' ')
				rights.append(context.getString(R.string.EventLogRestrictedPinMessages))
			}

			messageText = rights.toString()
		}
		else if (event.action is TL_channelAdminLogEventActionParticipantToggleBan) {
			val action = event.action as TL_channelAdminLogEventActionParticipantToggleBan

			messageOwner = TL_message()

			val peerId = getPeerId(action.prev_participant.peer)

			val whoUser = if (peerId > 0) {
				MessagesController.getInstance(currentAccount).getUser(peerId)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-peerId)
			}

			var o = action.prev_participant.banned_rights
			var n = action.new_participant.banned_rights

			if (chat.megagroup && (n == null || !n.view_messages || o != null && n.until_date != o.until_date)) {
				val rights: StringBuilder
				val bannedDuration: StringBuilder

				if (n != null && !AndroidUtilities.isBannedForever(n)) {
					bannedDuration = StringBuilder()

					var duration = n.until_date - event.date
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

				rights = StringBuilder(String.format(str, getUserName(whoUser, messageOwner!!.entities, offset), bannedDuration))

				var added = false

				if (o == null) {
					o = TL_chatBannedRights()
				}

				if (n == null) {
					n = TL_chatBannedRights()
				}

				if (o.view_messages != n.view_messages) {
					rights.append('\n')
					added = true
					rights.append('\n').append(if (!n.view_messages) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedReadMessages))
				}

				if (o.send_messages != n.send_messages) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.send_messages) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendMessages))
				}

				if (o.send_stickers != n.send_stickers || o.send_inline != n.send_inline || o.send_gifs != n.send_gifs || o.send_games != n.send_games) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.send_stickers) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendStickers))
				}

				if (o.send_media != n.send_media) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.send_media) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendMedia))
				}

				if (o.send_polls != n.send_polls) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.send_polls) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendPolls))
				}

				if (o.embed_links != n.embed_links) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.embed_links) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedSendEmbed))
				}

				if (o.change_info != n.change_info) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.change_info) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedChangeInfo))
				}

				if (o.invite_users != n.invite_users) {
					if (!added) {
						rights.append('\n')
						added = true
					}

					rights.append('\n').append(if (!n.invite_users) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedInviteUsers))
				}

				if (o.pin_messages != n.pin_messages) {
					if (!added) {
						rights.append('\n')
					}

					rights.append('\n').append(if (!n.pin_messages) '+' else '-').append(' ')
					rights.append(context.getString(R.string.EventLogRestrictedPinMessages))
				}

				messageText = rights.toString()
			}
			else {
				val str = if (n != null && (o == null || n.view_messages)) {
					context.getString(R.string.EventLogChannelRestricted)
				}
				else {
					context.getString(R.string.EventLogChannelUnrestricted)
				}

				val offset = str.indexOf("%1\$s")

				messageText = String.format(str, getUserName(whoUser, messageOwner!!.entities, offset))
			}
		}
		else if (event.action is TL_channelAdminLogEventActionUpdatePinned) {
			val action = event.action as TL_channelAdminLogEventActionUpdatePinned

			message = action.message

			if (fromUser != null && fromUser.id == 136817688L && action.message.fwd_from?.from_id is TL_peerChannel) {
				val channel = MessagesController.getInstance(currentAccount).getChat(action.message.fwd_from?.from_id?.channel_id)

				messageText = if (action.message is TL_messageEmpty || !action.message.pinned) {
					replaceWithLink(context.getString(R.string.EventLogUnpinnedMessages), "un1", channel)
				}
				else {
					replaceWithLink(context.getString(R.string.EventLogPinnedMessages), "un1", channel)
				}
			}
			else {
				messageText = if (action.message is TL_messageEmpty || !action.message.pinned) {
					replaceWithLink(context.getString(R.string.EventLogUnpinnedMessages), "un1", fromUser)
				}
				else {
					replaceWithLink(context.getString(R.string.EventLogPinnedMessages), "un1", fromUser)
				}
			}
		}
		else if (event.action is TL_channelAdminLogEventActionStopPoll) {
			val action = event.action as TL_channelAdminLogEventActionStopPoll

			message = action.message

			messageText = if (getMedia(message) is TL_messageMediaPoll && (getMedia(message) as TL_messageMediaPoll?)!!.poll.quiz) {
				replaceWithLink(context.getString(R.string.EventLogStopQuiz), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogStopPoll), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionToggleSignatures) {
			messageText = if ((event.action as TL_channelAdminLogEventActionToggleSignatures).new_value) {
				replaceWithLink(context.getString(R.string.EventLogToggledSignaturesOn), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogToggledSignaturesOff), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionToggleInvites) {
			messageText = if ((event.action as TL_channelAdminLogEventActionToggleInvites).new_value) {
				replaceWithLink(context.getString(R.string.EventLogToggledInvitesOn), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogToggledInvitesOff), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionDeleteMessage) {
			message = (event.action as TL_channelAdminLogEventActionDeleteMessage).message
			messageText = replaceWithLink(context.getString(R.string.EventLogDeletedMessages), "un1", fromUser)
		}
		else if (event.action is TL_channelAdminLogEventActionChangeLinkedChat) {
			val newChatId = (event.action as TL_channelAdminLogEventActionChangeLinkedChat).new_value
			val oldChatId = (event.action as TL_channelAdminLogEventActionChangeLinkedChat).prev_value

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
		else if (event.action is TL_channelAdminLogEventActionTogglePreHistoryHidden) {
			messageText = if ((event.action as TL_channelAdminLogEventActionTogglePreHistoryHidden).new_value) {
				replaceWithLink(context.getString(R.string.EventLogToggledInvitesHistoryOff), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogToggledInvitesHistoryOn), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionChangeAbout) {
			messageText = replaceWithLink(if (chat.megagroup) context.getString(R.string.EventLogEditedGroupDescription) else context.getString(R.string.EventLogEditedChannelDescription), "un1", fromUser)

			message = TL_message()
			message.out = false
			message.unread = false
			message.from_id = TL_peerUser()
			message.from_id?.user_id = event.user_id
			message.peer_id = peer_id
			message.date = event.date

			message.message = (event.action as TL_channelAdminLogEventActionChangeAbout).new_value

			if (!(event.action as TL_channelAdminLogEventActionChangeAbout).prev_value.isNullOrEmpty()) {
				message.media = TL_messageMediaWebPage()
				message.media?.webpage = TL_webPage()
				message.media?.webpage?.flags = 10
				message.media?.webpage?.display_url = ""
				message.media?.webpage?.url = ""
				message.media?.webpage?.site_name = context.getString(R.string.EventLogPreviousGroupDescription)
				message.media?.webpage?.description = (event.action as TL_channelAdminLogEventActionChangeAbout).prev_value
			}
			else {
				message.media = TL_messageMediaEmpty()
			}
		}
		else if (event.action is TL_channelAdminLogEventActionChangeTheme) {
			messageText = replaceWithLink(if (chat.megagroup) context.getString(R.string.EventLogEditedGroupTheme) else context.getString(R.string.EventLogEditedChannelTheme), "un1", fromUser)

			message = TL_message()
			message.out = false
			message.unread = false
			message.from_id = TL_peerUser()
			message.from_id?.user_id = event.user_id
			message.peer_id = peer_id
			message.date = event.date
			message.message = (event.action as TL_channelAdminLogEventActionChangeTheme).new_value

			if (!(event.action as TL_channelAdminLogEventActionChangeTheme).prev_value.isNullOrEmpty()) {
				message.media = TL_messageMediaWebPage()
				message.media?.webpage = TL_webPage()
				message.media?.webpage?.flags = 10
				message.media?.webpage?.display_url = ""
				message.media?.webpage?.url = ""
				message.media?.webpage?.site_name = context.getString(R.string.EventLogPreviousGroupTheme)
				message.media?.webpage?.description = (event.action as TL_channelAdminLogEventActionChangeTheme).prev_value
			}
			else {
				message.media = TL_messageMediaEmpty()
			}
		}
		else if (event.action is TL_channelAdminLogEventActionChangeUsername) {
			val newLink = (event.action as TL_channelAdminLogEventActionChangeUsername).new_value

			messageText = if (!newLink.isNullOrEmpty()) {
				replaceWithLink(if (chat.megagroup) context.getString(R.string.EventLogChangedGroupLink) else context.getString(R.string.EventLogChangedChannelLink), "un1", fromUser)
			}
			else {
				replaceWithLink(if (chat.megagroup) context.getString(R.string.EventLogRemovedGroupLink) else context.getString(R.string.EventLogRemovedChannelLink), "un1", fromUser)
			}

			message = TL_message()
			message.out = false
			message.unread = false
			message.from_id = TL_peerUser()
			message.from_id?.user_id = event.user_id
			message.peer_id = peer_id
			message.date = event.date

			if (!newLink.isNullOrEmpty()) {
				message.message = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + newLink
			}
			else {
				message.message = ""
			}

			val url = TL_messageEntityUrl()
			url.offset = 0
			url.length = message.message!!.length

			message.entities.add(url)

			if (!(event.action as TL_channelAdminLogEventActionChangeUsername).prev_value.isNullOrEmpty()) {
				message.media = TL_messageMediaWebPage()
				message.media?.webpage = TL_webPage()
				message.media?.webpage?.flags = 10
				message.media?.webpage?.display_url = ""
				message.media?.webpage?.url = ""
				message.media?.webpage?.site_name = context.getString(R.string.EventLogPreviousLink)
				message.media?.webpage?.description = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + (event.action as TL_channelAdminLogEventActionChangeUsername).prev_value
			}
			else {
				message.media = TL_messageMediaEmpty()
			}
		}
		else if (event.action is TL_channelAdminLogEventActionEditMessage) {
			message = TL_message()
			message.out = false
			message.unread = false
			message.peer_id = peer_id
			message.date = event.date

			val newMessage = (event.action as TL_channelAdminLogEventActionEditMessage).new_message
			val oldMessage = (event.action as TL_channelAdminLogEventActionEditMessage).prev_message

			if (newMessage?.from_id != null) {
				message.from_id = newMessage.from_id
			}
			else {
				message.from_id = TL_peerUser()
				message.from_id?.user_id = event.user_id
			}

			val newMessageMedia = getMedia(newMessage)

			if (newMessageMedia != null && newMessageMedia !is TL_messageMediaEmpty && newMessageMedia !is TL_messageMediaWebPage) {
				val changedCaption = !TextUtils.equals(newMessage.message, oldMessage.message)
				val changedMedia = newMessageMedia.javaClass != oldMessage.media?.javaClass || newMessageMedia.photo != null && oldMessage.media?.photo != null && newMessageMedia.photo.id != oldMessage.media?.photo?.id || newMessageMedia.document != null && oldMessage.media?.document != null && newMessageMedia.document.id != oldMessage.media?.document?.id

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
					message.media?.webpage = TL_webPage()
					message.media?.webpage?.site_name = context.getString(R.string.EventLogOriginalCaption)

					if (oldMessage.message.isNullOrEmpty()) {
						message.media?.webpage?.description = context.getString(R.string.EventLogOriginalCaptionEmpty)
					}
					else {
						message.media?.webpage?.description = oldMessage.message
						webPageDescriptionEntities = oldMessage.entities
					}
				}
			}
			else {
				messageText = replaceWithLink(context.getString(R.string.EventLogEditedMessages), "un1", fromUser)

				if (newMessage.action is TL_messageActionGroupCall) {
					message = newMessage
					message.media = TL_messageMediaEmpty()
				}
				else {
					message.message = newMessage.message
					message.entities = newMessage.entities
					message.media = TL_messageMediaWebPage()
					message.media?.webpage = TL_webPage()
					message.media?.webpage?.site_name = context.getString(R.string.EventLogOriginalMessages)

					if (oldMessage.message.isNullOrEmpty()) {
						message.media?.webpage?.description = context.getString(R.string.EventLogOriginalCaptionEmpty)
					}
					else {
						message.media?.webpage?.description = oldMessage.message
						webPageDescriptionEntities = oldMessage.entities
					}
				}
			}

			message?.reply_markup = newMessage.reply_markup

			message?.media?.webpage?.flags = 10
			message?.media?.webpage?.display_url = ""
			message?.media?.webpage?.url = ""
		}
		else if (event.action is TL_channelAdminLogEventActionChangeStickerSet) {
			val newStickerset = (event.action as TL_channelAdminLogEventActionChangeStickerSet).new_stickerset
			// TLRPC.InputStickerSet oldStickerset = ((TLRPC.TL_channelAdminLogEventActionChangeStickerSet)event.action).new_stickerset;

			messageText = if (newStickerset == null || newStickerset is TL_inputStickerSetEmpty) {
				replaceWithLink(context.getString(R.string.EventLogRemovedStickersSet), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogChangedStickersSet), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionChangeLocation) {
			val location = event.action as TL_channelAdminLogEventActionChangeLocation

			if (location.new_value is TL_channelLocationEmpty) {
				messageText = replaceWithLink(context.getString(R.string.EventLogRemovedLocation), "un1", fromUser)
			}
			else {
				val channelLocation = location.new_value as TL_channelLocation
				messageText = replaceWithLink(LocaleController.formatString("EventLogChangedLocation", R.string.EventLogChangedLocation, channelLocation.address), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionToggleSlowMode) {
			val slowMode = event.action as TL_channelAdminLogEventActionToggleSlowMode

			if (slowMode.new_value == 0) {
				messageText = replaceWithLink(context.getString(R.string.EventLogToggledSlowmodeOff), "un1", fromUser)
			}
			else {
				val string = if (slowMode.new_value < 60) {
					LocaleController.formatPluralString("Seconds", slowMode.new_value)
				}
				else if (slowMode.new_value < 60 * 60) {
					LocaleController.formatPluralString("Minutes", slowMode.new_value / 60)
				}
				else {
					LocaleController.formatPluralString("Hours", slowMode.new_value / 60 / 60)
				}

				messageText = replaceWithLink(LocaleController.formatString("EventLogToggledSlowmodeOn", R.string.EventLogToggledSlowmodeOn, string), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionStartGroupCall) {
			messageText = if (ChatObject.isChannel(chat) && (!chat.megagroup || chat.gigagroup)) {
				replaceWithLink(context.getString(R.string.EventLogStartedLiveStream), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogStartedVoiceChat), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionDiscardGroupCall) {
			messageText = if (ChatObject.isChannel(chat) && (!chat.megagroup || chat.gigagroup)) {
				replaceWithLink(context.getString(R.string.EventLogEndedLiveStream), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogEndedVoiceChat), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionParticipantMute) {
			val action = event.action as TL_channelAdminLogEventActionParticipantMute
			val id = getPeerId(action.participant.peer)

			val `object` = if (id > 0) {
				MessagesController.getInstance(currentAccount).getUser(id)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-id)
			}

			messageText = replaceWithLink(context.getString(R.string.EventLogVoiceChatMuted), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", `object`)
		}
		else if (event.action is TL_channelAdminLogEventActionParticipantUnmute) {
			val action = event.action as TL_channelAdminLogEventActionParticipantUnmute
			val id = getPeerId(action.participant.peer)

			val `object` = if (id > 0) {
				MessagesController.getInstance(currentAccount).getUser(id)
			}
			else {
				MessagesController.getInstance(currentAccount).getChat(-id)
			}

			messageText = replaceWithLink(context.getString(R.string.EventLogVoiceChatUnmuted), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", `object`)
		}
		else if (event.action is TL_channelAdminLogEventActionToggleGroupCallSetting) {
			val action = event.action as TL_channelAdminLogEventActionToggleGroupCallSetting

			messageText = if (action.join_muted) {
				replaceWithLink(context.getString(R.string.EventLogVoiceChatNotAllowedToSpeak), "un1", fromUser)
			}
			else {
				replaceWithLink(context.getString(R.string.EventLogVoiceChatAllowedToSpeak), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionParticipantJoinByInvite) {
			messageText = replaceWithLink(context.getString(R.string.ActionInviteUser), "un1", fromUser)
		}
		else if (event.action is TL_channelAdminLogEventActionToggleNoForwards) {
			val action = event.action as TL_channelAdminLogEventActionToggleNoForwards
			val isChannel = ChatObject.isChannel(chat) && !chat.megagroup

			messageText = if (action.new_value) {
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
		else if (event.action is TL_channelAdminLogEventActionExportedInviteDelete) {
			val action = event.action as TL_channelAdminLogEventActionExportedInviteDelete

			messageText = replaceWithLink(LocaleController.formatString("ActionDeletedInviteLinkClickable", R.string.ActionDeletedInviteLinkClickable), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", action.invite)
		}
		else if (event.action is TL_channelAdminLogEventActionExportedInviteRevoke) {
			val action = event.action as TL_channelAdminLogEventActionExportedInviteRevoke

			messageText = replaceWithLink(LocaleController.formatString("ActionRevokedInviteLinkClickable", R.string.ActionRevokedInviteLinkClickable, action.invite.link), "un1", fromUser)
			messageText = replaceWithLink(messageText, "un2", action.invite)
		}
		else if (event.action is TL_channelAdminLogEventActionExportedInviteEdit) {
			val action = event.action as TL_channelAdminLogEventActionExportedInviteEdit

			messageText = if (action.prev_invite.link != null && action.prev_invite.link == action.new_invite.link) {
				replaceWithLink(LocaleController.formatString("ActionEditedInviteLinkToSameClickable", R.string.ActionEditedInviteLinkToSameClickable), "un1", fromUser)
			}
			else {
				replaceWithLink(LocaleController.formatString("ActionEditedInviteLinkClickable", R.string.ActionEditedInviteLinkClickable), "un1", fromUser)
			}

			messageText = replaceWithLink(messageText, "un2", action.prev_invite)
			messageText = replaceWithLink(messageText, "un3", action.new_invite)
		}
		else if (event.action is TL_channelAdminLogEventActionParticipantVolume) {
			val action = event.action as TL_channelAdminLogEventActionParticipantVolume
			val id = getPeerId(action.participant.peer)

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
		else if (event.action is TL_channelAdminLogEventActionChangeHistoryTTL) {
			val action = event.action as TL_channelAdminLogEventActionChangeHistoryTTL

			if (!chat.megagroup) {
				messageText = if (action.new_value != 0) {
					LocaleController.formatString("ActionTTLChannelChanged", R.string.ActionTTLChannelChanged, LocaleController.formatTTLString(action.new_value))
				}
				else {
					context.getString(R.string.ActionTTLChannelDisabled)
				}
			}
			else if (action.new_value == 0) {
				messageText = replaceWithLink(context.getString(R.string.ActionTTLDisabled), "un1", fromUser)
			}
			else {
				val time = if (action.new_value > 24 * 60 * 60) {
					LocaleController.formatPluralString("Days", action.new_value / (24 * 60 * 60))
				}
				else if (action.new_value >= 60 * 60) {
					LocaleController.formatPluralString("Hours", action.new_value / (60 * 60))
				}
				else if (action.new_value >= 60) {
					LocaleController.formatPluralString("Minutes", action.new_value / 60)
				}
				else {
					LocaleController.formatPluralString("Seconds", action.new_value)
				}

				messageText = replaceWithLink(LocaleController.formatString("ActionTTLChanged", R.string.ActionTTLChanged, time), "un1", fromUser)
			}
		}
		else if (event.action is TL_channelAdminLogEventActionParticipantJoinByRequest) {
			val action = event.action as TL_channelAdminLogEventActionParticipantJoinByRequest

			val url = String.format(Locale.getDefault(), "https://%s/+PublicChat", ApplicationLoader.applicationContext.getString(R.string.domain))

			if ((action.invite is TL_chatInviteExported) && url == (action.invite as TL_chatInviteExported).link || action.invite is TL_chatInvitePublicJoinRequests) {
				messageText = replaceWithLink(context.getString(R.string.JoinedViaRequestApproved), "un1", fromUser)
				messageText = replaceWithLink(messageText, "un2", MessagesController.getInstance(currentAccount).getUser(action.approved_by))
			}
			else {
				messageText = replaceWithLink(context.getString(R.string.JoinedViaInviteLinkApproved), "un1", fromUser)
				messageText = replaceWithLink(messageText, "un2", action.invite)
				messageText = replaceWithLink(messageText, "un3", MessagesController.getInstance(currentAccount).getUser(action.approved_by))
			}
		}
		else if (event.action is TL_channelAdminLogEventActionSendMessage) {
			message = (event.action as TL_channelAdminLogEventActionSendMessage).message

			messageText = replaceWithLink(context.getString(R.string.EventLogSendMessages), "un1", fromUser)
		}
		else if (event.action is TL_channelAdminLogEventActionChangeAvailableReactions) {
			val eventActionChangeAvailableReactions = event.action as TL_channelAdminLogEventActionChangeAvailableReactions
			val oldReactions = getStringFrom(eventActionChangeAvailableReactions.prev_value)
			val newReactions = getStringFrom(eventActionChangeAvailableReactions.new_value)
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
			messageText = "unsupported " + event.action
		}

		if (messageOwner == null) {
			messageOwner = TL_messageService()
		}

		messageOwner?.message = messageText.toString()
		messageOwner?.from_id = TL_peerUser()
		messageOwner?.from_id?.user_id = event.user_id
		messageOwner?.date = event.date
		messageOwner?.id = mid[0]++

		eventId = event.id

		messageOwner?.out = false
		messageOwner?.peer_id = TL_peerChannel()
		messageOwner?.peer_id?.channel_id = chat.id
		messageOwner?.unread = false

		val mediaController = MediaController.getInstance()

		if (message is TL_messageEmpty) {
			message = null
		}

		if (message != null) {
			message.out = false
			message.id = mid[0]++
			message.flags = message.flags and TLRPC.MESSAGE_FLAG_REPLY.inv()
			message.reply_to = null
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

		val paint = if (getMedia(messageOwner) is TL_messageMediaGame) {
			Theme.chat_msgGameTextPaint
		}
		else {
			Theme.chat_msgTextPaint
		}

		val emojiOnly = if (allowsBigEmoji()) IntArray(1) else null

		messageText = Emoji.replaceEmoji(messageText, paint.fontMetricsInt, false, emojiOnly)
		messageText = replaceAnimatedEmoji(messageText, messageOwner!!.entities, paint.fontMetricsInt)

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

	val randomUnreadReaction: MessagePeerReaction?
		get() = messageOwner?.originalReactions?.recentReactions?.firstOrNull()

	fun markReactionsAsRead() {
		var changed = false

		messageOwner?.originalReactions?.recentReactions?.forEach {
			if (it.unread) {
				it.unread = false
				changed = true
			}
		}

		if (changed) {
			MessagesStorage.getInstance(currentAccount).markMessageReactionsAsRead(messageOwner?.dialog_id ?: 0, messageOwner?.id ?: 0, true)
		}
	}

	val isPremiumSticker: Boolean
		get() {
			val media = getMedia(messageOwner)

			if (media != null && media.nopremium) {
				return false
			}

			return isPremiumSticker(document)
		}

	val premiumStickerAnimation: VideoSize?
		get() = getPremiumStickerAnimation(document)

	fun copyStableParams(old: MessageObject) {
		stableId = old.stableId
		messageOwner?.premiumEffectWasPlayed = old.messageOwner?.premiumEffectWasPlayed ?: false
		forcePlayEffect = old.forcePlayEffect
		wasJustSent = old.wasJustSent

		val reactions = messageOwner?.originalReactions
		val oldReactions = old.messageOwner?.originalReactions

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
			val reactions = messageOwner?.originalReactions

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

		if (emojiOnlyCount == 1 && getMedia(messageOwner) !is TL_messageMediaWebPage && getMedia(messageOwner) !is TL_messageMediaInvoice && (getMedia(messageOwner) is TL_messageMediaEmpty || getMedia(messageOwner) == null) && messageOwner?.realGroupId == 0L) {
			if (messageOwner?.entities.isNullOrEmpty()) {
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
			else if (messageOwner?.entities?.size == 1 && messageOwner?.entities?.first() is TL_messageEntityCustomEmoji) {
				try {
					emojiAnimatedStickerId = (messageOwner?.entities?.first() as TL_messageEntityCustomEmoji).documentId
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
				if (photoSize is TL_photoStrippedSize) {
					strippedThumb = BitmapDrawable(ApplicationLoader.applicationContext.resources, ImageLoader.getStrippedPhotoBitmap(photoSize.bytes, "b"))
					break
				}
			}
		}
		catch (e: Throwable) {
			FileLog.e(e)
		}
	}

	private fun createDateArray(accountNum: Int, event: TL_channelAdminLogEvent, messageObjects: ArrayList<MessageObject>, messagesByDays: HashMap<String, ArrayList<MessageObject>>, addToEnd: Boolean) {
		val dateKey = dateKey ?: return
		var dayArray = messagesByDays[dateKey]

		if (dayArray == null) {
			dayArray = ArrayList()

			messagesByDays[dateKey] = dayArray

			val dateMsg = TL_message()
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

	private fun getStringFrom(reactions: ChatReactions): CharSequence {
		if (reactions is TL_chatReactionsAll) {
			return ApplicationLoader.applicationContext.getString(R.string.AllReactions)
		}

		if (reactions is TL_chatReactionsSome) {
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

	private fun getUserName(`object`: TLObject?, entities: ArrayList<MessageEntity>, offset: Int): String {
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
				name = if (`object`.deleted) {
					ApplicationLoader.applicationContext.getString(R.string.HiddenName)
				}
				else {
					ContactsController.formatName(`object`.first_name, `object`.last_name)
				}

				username = `object`.username
				id = `object`.id
			}

			else -> {
				val chat = `object` as Chat
				name = chat.title
				username = chat.username
				id = -chat.id
			}
		}

		if (offset >= 0) {
			val entity = TL_messageEntityMentionName()
			entity.userId = id
			entity.offset = offset
			entity.length = name.length

			entities.add(entity)
		}

		if (!username.isNullOrEmpty()) {
			if (offset >= 0) {
				val entity = TL_messageEntityMentionName()
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
	fun applyNewText(text: CharSequence? = messageOwner?.message) {
		if (text.isNullOrEmpty()) {
			return
		}

		messageText = text

		val paint = if (getMedia(messageOwner) is TL_messageMediaGame) {
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
		generateLayout()
		setType()
	}

	private fun allowsBigEmoji(): Boolean {
		if (!SharedConfig.allowBigEmoji) {
			return false
		}

		if (messageOwner == null || messageOwner?.peer_id == null || messageOwner?.peer_id?.channel_id == 0L && messageOwner?.peer_id?.chat_id == 0L) {
			return true
		}

		val chat = MessagesController.getInstance(currentAccount).getChat(if (messageOwner?.peer_id?.channel_id != 0L) messageOwner?.peer_id?.channel_id else messageOwner?.peer_id?.chat_id)

		return chat != null && chat.gigagroup || (!ChatObject.isActionBanned(chat, ChatObject.ACTION_SEND_STICKERS) || ChatObject.hasAdminRights(chat))
	}

	fun generateGameMessageText(fromUser: User?) {
		@Suppress("NAME_SHADOWING") var fromUser = fromUser

		if (fromUser == null && isFromUser) {
			fromUser = MessagesController.getInstance(currentAccount).getUser(messageOwner?.from_id?.user_id)
		}

		var game: TL_game? = null

		if (replyMessageObject != null && getMedia(messageOwner)?.game != null) {
			game = getMedia(messageOwner)?.game
		}

		if (game == null) {
			messageText = if (fromUser != null && fromUser.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
				LocaleController.formatString("ActionYouScored", R.string.ActionYouScored, LocaleController.formatPluralString("Points", messageOwner?.action?.score ?: 0))
			}
			else {
				replaceWithLink(LocaleController.formatString("ActionUserScored", R.string.ActionUserScored, LocaleController.formatPluralString("Points", messageOwner?.action?.score ?: 0)), "un1", fromUser)
			}
		}
		else {
			messageText = if (fromUser != null && fromUser.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
				LocaleController.formatString("ActionYouScoredInGame", R.string.ActionYouScoredInGame, LocaleController.formatPluralString("Points", messageOwner?.action?.score ?: 0))
			}
			else {
				replaceWithLink(LocaleController.formatString("ActionUserScoredInGame", R.string.ActionUserScoredInGame, LocaleController.formatPluralString("Points", messageOwner?.action?.score ?: 0)), "un1", fromUser)
			}

			messageText = replaceWithLink(messageText, "un2", game)
		}
	}

	fun hasValidReplyMessageObject(): Boolean {
		return !(replyMessageObject == null || replyMessageObject?.messageOwner is TL_messageEmpty || replyMessageObject?.messageOwner?.action is TL_messageActionHistoryClear)
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
			currency = LocaleController.getInstance().formatCurrencyString(messageOwner?.action?.total_amount ?: 0L, messageOwner?.action?.currency ?: "")
		}
		catch (e: Exception) {
			currency = "<error>"
			FileLog.e(e)
		}

		messageText = if (replyMessageObject != null && getMedia(messageOwner) is TL_messageMediaInvoice) {
			if (messageOwner?.action?.recurring_init == true) {
				LocaleController.formatString(R.string.PaymentSuccessfullyPaidRecurrent, currency, name, getMedia(messageOwner)?.title)
			}
			else {
				LocaleController.formatString("PaymentSuccessfullyPaid", R.string.PaymentSuccessfullyPaid, currency, name, getMedia(messageOwner)?.title)
			}
		}
		else {
			if (messageOwner?.action?.recurring_init == true) {
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
				fromUser = MessagesController.getInstance(currentAccount).getUser(messageOwner?.from_id?.user_id)
			}

			if (fromUser == null) {
				if (messageOwner?.peer_id is TL_peerChannel) {
					chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.peer_id?.channel_id)
				}
				else if (messageOwner?.peer_id is TL_peerChat) {
					chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.peer_id?.chat_id)
				}
			}
		}

		val context = ApplicationLoader.applicationContext
		val replyMessageObject = replyMessageObject

		if (replyMessageObject == null || replyMessageObject.messageOwner is TL_messageEmpty || replyMessageObject.messageOwner?.action is TL_messageActionHistoryClear) {
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
			else if (getMedia(messageOwner) is TL_messageMediaDocument) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedFile), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TL_messageMediaGeo) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedGeo), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TL_messageMediaGeoLive) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedGeoLive), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TL_messageMediaContact) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedContact), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TL_messageMediaPoll) {
				messageText = if ((getMedia(messageOwner) as? TL_messageMediaPoll?)?.poll?.quiz == true) {
					replaceWithLink(context.getString(R.string.ActionPinnedQuiz), "un1", fromUser ?: chat)
				}
				else {
					replaceWithLink(context.getString(R.string.ActionPinnedPoll), "un1", fromUser ?: chat)
				}
			}
			else if (getMedia(messageOwner) is TL_messageMediaPhoto) {
				messageText = replaceWithLink(context.getString(R.string.ActionPinnedPhoto), "un1", fromUser ?: chat)
			}
			else if (getMedia(messageOwner) is TL_messageMediaGame) {
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
					mess = replaceAnimatedEmoji(mess, replyMessageObject.messageOwner?.entities, Theme.chat_msgTextPaint.fontMetricsInt)
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
		return !messageOwner?.originalReactions?.results.isNullOrEmpty()
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

			return (getMedia(messageOwner) as? TL_messageMediaPoll)?.poll?.closed == true
		}

	val isQuiz: Boolean
		get() {
			if (type != TYPE_POLL) {
				return false
			}

			return (getMedia(messageOwner) as? TL_messageMediaPoll)?.poll?.quiz == true
		}

	val isPublicPoll: Boolean
		get() {
			if (type != TYPE_POLL) {
				return false
			}

			return (getMedia(messageOwner) as? TL_messageMediaPoll)?.poll?.public_voters == true
		}

	val isPoll: Boolean
		get() = type == TYPE_POLL

	fun canUnvote(): Boolean {
		if (type != TYPE_POLL) {
			return false
		}

		val mediaPoll = getMedia(messageOwner) as? TL_messageMediaPoll

		if (mediaPoll?.results == null || mediaPoll.results.results.isEmpty() || mediaPoll.poll.quiz) {
			return false
		}

		for (answer in mediaPoll.results.results) {
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

			val mediaPoll = getMedia(messageOwner) as? TL_messageMediaPoll

			if (mediaPoll?.results == null || mediaPoll.results.results.isEmpty()) {
				return false
			}

			for (answer in mediaPoll.results.results) {
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

			return (getMedia(messageOwner) as? TL_messageMediaPoll)?.poll?.id ?: 0L
		}

	private fun getPhotoWithId(webPage: WebPage?, id: Long): TLRPC.Photo? {
		contract {
			returnsNotNull() implies (webPage != null)
		}

		if (webPage?.cached_page == null) {
			return null
		}

		if (webPage.photo != null && webPage.photo.id == id) {
			return webPage.photo
		}

		for (a in webPage.cached_page.photos.indices) {
			val photo = webPage.cached_page.photos[a]

			if (photo.id == id) {
				return photo
			}
		}

		return null
	}

	private fun getDocumentWithId(webPage: WebPage?, id: Long): TLRPC.Document? {
		contract {
			returnsNotNull() implies (webPage != null)
		}

		if (webPage?.cached_page == null) {
			return null
		}

		if (webPage.document != null && webPage.document.id == id) {
			return webPage.document
		}

		for (a in webPage.cached_page.documents.indices) {
			val document = webPage.cached_page.documents[a]

			if (document.id == id) {
				return document
			}
		}

		return null
	}

	val isSupergroup: Boolean
		get() {
			if (localSupergroup) {
				return true
			}

			cachedIsSupergroup?.let {
				return it
			}

			if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) {
				val chat = getChat(null, null, messageOwner?.peer_id?.channel_id)
				return chat?.megagroup?.also { cachedIsSupergroup = it } ?: false
			}
			else {
				cachedIsSupergroup = false
			}

			return false
		}

	private fun getMessageObjectForBlock(webPage: WebPage, pageBlock: PageBlock): MessageObject {
		var message: TL_message? = null

		if (pageBlock is TL_pageBlockPhoto) {
			val photo = getPhotoWithId(webPage, pageBlock.photo_id)

			if (photo === webPage.photo) {
				return this
			}

			message = TL_message()
			message.media = TL_messageMediaPhoto()
			message.media?.photo = photo
		}
		else if (pageBlock is TL_pageBlockVideo) {
			val document = getDocumentWithId(webPage, pageBlock.video_id)

			if (document === webPage.document) {
				return this
			}

			message = TL_message()
			message.media = TL_messageMediaDocument()
			message.media?.document = getDocumentWithId(webPage, pageBlock.video_id)
		}

		message?.message = ""
		message?.realId = id
		message?.id = Utilities.random.nextInt()
		message?.date = messageOwner?.date ?: 0
		message?.peer_id = messageOwner?.peer_id
		message?.out = messageOwner?.out ?: false
		message?.from_id = messageOwner?.from_id

		return MessageObject(currentAccount, message!!, generateLayout = false, checkMediaExists = true)
	}

	fun getWebPagePhotos(array: ArrayList<MessageObject>?, blocksToSearch: ArrayList<PageBlock>?): ArrayList<MessageObject> {
		val messageObjects = array ?: ArrayList()
		val media = getMedia(messageOwner)
		val webPage = media?.webpage ?: return messageObjects

		if (webPage.cached_page == null) {
			return messageObjects
		}

		val blocks = blocksToSearch ?: webPage.cached_page.blocks

		for (a in blocks.indices) {
			val block = blocks[a]

			if (block is TL_pageBlockSlideshow) {
				for (blockItem in block.items) {
					messageObjects.add(getMessageObjectForBlock(webPage, blockItem))
				}
			}
			else if (block is TL_pageBlockCollage) {
				for (blockItem in block.items) {
					messageObjects.add(getMessageObjectForBlock(webPage, blockItem))
				}
			}
		}

		return messageObjects
	}

	fun createMessageSendInfo() {
		val messageOwner = messageOwner
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

			if (messageOwner.send_state == MESSAGE_SEND_STATE_EDITING && (params["prevMedia"].also { param = it }) != null) {
				val serializedData = SerializedData(Base64.decode(param, Base64.DEFAULT))
				var constructor = serializedData.readInt32(false)

				previousMedia = MessageMedia.TLdeserialize(serializedData, constructor, false)
				previousMessage = serializedData.readString(false)
				previousAttachPath = serializedData.readString(false)

				val count = serializedData.readInt32(false)

				previousMessageEntities = ArrayList(count)

				for (a in 0 until count) {
					constructor = serializedData.readInt32(false)

					val entity = MessageEntity.TLdeserialize(serializedData, constructor, false)

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

		if (messageOwner?.reply_markup is TL_replyInlineMarkup && !hasExtendedMedia() || messageOwner?.originalReactions != null && !messageOwner?.originalReactions?.results.isNullOrEmpty()) {
			Theme.createCommonMessageResources()

			if (botButtonsLayout == null) {
				botButtonsLayout = StringBuilder()
			}
			else {
				botButtonsLayout?.setLength(0)
			}
		}

		val replyMarkup = messageOwner?.reply_markup

		if (replyMarkup is TL_replyInlineMarkup && !hasExtendedMedia()) {
			val context = ApplicationLoader.applicationContext

			for (a in replyMarkup.rows.indices) {
				val row = replyMarkup.rows[a]
				var maxButtonSize = 0
				val size = row.buttons.size

				for (b in 0 until size) {
					val button = row.buttons[b]

					botButtonsLayout?.append(a)?.append(b)

					var text: CharSequence?

					if (button is TL_keyboardButtonBuy && (getMedia(messageOwner)!!.flags and 4) != 0) {
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
		else if (messageOwner?.originalReactions != null) {
			val reactions = messageOwner!!.originalReactions!!

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
		get() = !messageOwner?.action?.photo?.video_sizes.isNullOrEmpty()

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

		if (messageOwner?.from_id is TL_peerUser) {
			fromUser = getUser(users, sUsers, messageOwner?.from_id?.user_id ?: 0L)
		}
		else if (messageOwner?.from_id is TL_peerChannel) {
			fromChat = getChat(chats, sChats, messageOwner?.from_id?.channel_id)
		}

		val fromObject = fromUser ?: fromChat
		val context = ApplicationLoader.applicationContext

		if (messageOwner is TL_messageService) {
			when (val action = messageOwner?.action) {
				is TL_messageActionGroupCallScheduled -> {
					messageText = if (messageOwner?.peer_id is TL_peerChat || isSupergroup) {
						LocaleController.formatString("ActionGroupCallScheduled", R.string.ActionGroupCallScheduled, LocaleController.formatStartsTime(action.schedule_date.toLong(), 3, false))
					}
					else {
						LocaleController.formatString("ActionChannelCallScheduled", R.string.ActionChannelCallScheduled, LocaleController.formatStartsTime(action.schedule_date.toLong(), 3, false))
					}
				}

				is TL_messageActionGroupCall -> {
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

						messageText = if (messageOwner?.peer_id is TL_peerChat || isSupergroup) {
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
						messageText = if (messageOwner?.peer_id is TL_peerChat || isSupergroup) {
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

				is TL_messageActionInviteToGroupCall -> {
					var singleUserId = action.user_id

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

				is TL_messageActionGeoProximityReached -> {
					val fromId = getPeerId(action.from_id)

					val from = if (fromId > 0) {
						getUser(users, sUsers, fromId)
					}
					else {
						getChat(chats, sChats, -fromId)
					}

					val toId = getPeerId(action.to_id)
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

				is TL_messageActionCustomAction -> {
					messageText = action.message
				}

				is TL_messageActionChatCreate -> {
					messageText = if (isOut) {
						context.getString(R.string.ActionYouCreateGroup)
					}
					else {
						replaceWithLink(context.getString(R.string.ActionCreateGroup), "un1", fromObject)
					}
				}

				is TL_messageActionChatDeleteUser -> {
					if (isFromUser && action.user_id == messageOwner?.from_id?.user_id) {
						messageText = if (isOut) {
							context.getString(R.string.ActionYouLeftUser)
						}
						else {
							replaceWithLink(context.getString(R.string.ActionLeftUser), "un1", fromObject)
						}
					}
					else {
						val whoUser = getUser(users, sUsers, action.user_id)

						if (isOut) {
							messageText = replaceWithLink(context.getString(R.string.ActionYouKickUser), "un2", whoUser)
						}
						else if (action.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
							messageText = replaceWithLink(context.getString(R.string.ActionKickUserYou), "un1", fromObject)
						}
						else {
							messageText = replaceWithLink(context.getString(R.string.ActionKickUser), "un2", whoUser)
							messageText = replaceWithLink(messageText, "un1", fromObject)
						}
					}
				}

				is TL_messageActionChatAddUser -> {
					var singleUserId = action.user_id

					if (singleUserId == 0L && action.users.size == 1) {
						singleUserId = action.users[0]
					}

					if (singleUserId != 0L) {
						val whoUser = getUser(users, sUsers, singleUserId)
						var chat: Chat? = null

						if (messageOwner?.peer_id?.channel_id != 0L) {
							chat = getChat(chats, sChats, messageOwner?.peer_id?.channel_id)
						}

						if (messageOwner?.from_id != null && singleUserId == messageOwner?.from_id?.user_id) {
							messageText = if (ChatObject.isChannel(chat) && !chat.megagroup) {
								context.getString(R.string.ChannelJoined)
							}
							else {
								if (messageOwner?.peer_id?.channel_id != 0L) {
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
								messageText = if (messageOwner?.peer_id?.channel_id != 0L) {
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

				is TL_messageActionChatJoinedByLink -> {
					messageText = if (isOut) {
						context.getString(R.string.ActionInviteYou)
					}
					else {
						replaceWithLink(context.getString(R.string.ActionInviteUser), "un1", fromObject)
					}
				}

				is TL_messageActionGiftPremium -> {
					if (fromObject is User && fromObject.self) {
						val user = getUser(users, sUsers, messageOwner?.peer_id?.user_id ?: 0L)
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

				is TL_messageActionChatEditPhoto -> {
					val chat = if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) getChat(chats, sChats, messageOwner?.peer_id?.channel_id) else null

					messageText = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						if (isVideoAvatar) {
							context.getString(R.string.ActionChannelChangedVideo)
						}
						else {
							context.getString(R.string.ActionChannelChangedPhoto)
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

				is TL_messageActionChatEditTitle -> {
					val chat = if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) getChat(chats, sChats, messageOwner?.peer_id?.channel_id) else null

					messageText = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						context.getString(R.string.ActionChannelChangedTitle).replace("un2", action.title)
					}
					else {
						if (isOut) {
							context.getString(R.string.ActionYouChangedTitle).replace("un2", action.title)
						}
						else {
							replaceWithLink(context.getString(R.string.ActionChangedTitle).replace("un2", action.title), "un1", fromObject)
						}
					}
				}

				is TL_messageActionChatDeletePhoto -> {
					val chat = if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) getChat(chats, sChats, messageOwner?.peer_id?.channel_id) else null

					messageText = if (ChatObject.isChannel(chat) && !chat.megagroup) {
						context.getString(R.string.ActionChannelRemovedPhoto)
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

				is TL_messageActionTTLChange -> {
					messageText = if (action.ttl != 0) {
						if (isOut) {
							LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, LocaleController.formatTTLString(action.ttl))
						}
						else {
							LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, UserObject.getFirstName(fromUser), LocaleController.formatTTLString(action.ttl))
						}
					}
					else {
						if (isOut) {
							context.getString(R.string.MessageLifetimeYouRemoved)
						}
						else {
							LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, UserObject.getFirstName(fromUser))
						}
					}
				}

				is TL_messageActionSetMessagesTTL -> {
					val chat = if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) getChat(chats, sChats, messageOwner?.peer_id?.channel_id) else null

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

				is TL_messageActionLoginUnknownLocation -> {
					val date: String
					val time = ((messageOwner?.date ?: 0).toLong()) * 1000

					date = if (LocaleController.getInstance().formatterDay != null && LocaleController.getInstance().formatterYear != null) {
						LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(time), LocaleController.getInstance().formatterDay.format(time))
					}
					else {
						"" + (messageOwner?.date ?: 0)
					}

					var toUser = UserConfig.getInstance(currentAccount).getCurrentUser()

					if (toUser == null) {
						toUser = getUser(users, sUsers, messageOwner?.peer_id?.user_id ?: 0L)
					}

					val name = if (toUser != null) UserObject.getFirstName(toUser) else ""

					messageText = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, name, date, action.title, action.address)
				}

				is TLRPC.TL_messageActionUserJoined, is TL_messageActionContactSignUp -> {
					messageText = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, UserObject.getUserName(fromUser))
				}

				is TL_messageActionUserUpdatedPhoto -> {
					messageText = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, UserObject.getUserName(fromUser))
				}

				is TL_messageEncryptedAction -> {
					if (action.encryptedAction is TL_decryptedMessageActionScreenshotMessages) {
						messageText = if (isOut) {
							LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou)
						}
						else {
							replaceWithLink(context.getString(R.string.ActionTakeScreenshoot), "un1", fromObject)
						}
					}
					else if (action.encryptedAction is TL_decryptedMessageActionSetMessageTTL) {
						@Suppress("NAME_SHADOWING") val action = action.encryptedAction as TL_decryptedMessageActionSetMessageTTL

						messageText = if (action.ttl_seconds != 0) {
							if (isOut) {
								LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, LocaleController.formatTTLString(action.ttl_seconds))
							}
							else {
								LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, UserObject.getFirstName(fromUser), LocaleController.formatTTLString(action.ttl_seconds))
							}
						}
						else {
							if (isOut) {
								context.getString(R.string.MessageLifetimeYouRemoved)
							}
							else {
								LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, UserObject.getFirstName(fromUser))
							}
						}
					}
				}

				is TLRPC.TL_messageActionScreenshotTaken -> {
					messageText = if (isOut) {
						LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou)
					}
					else {
						replaceWithLink(context.getString(R.string.ActionTakeScreenshoot), "un1", fromObject)
					}
				}

				is TLRPC.TL_messageActionCreatedBroadcastList -> {
					messageText = LocaleController.formatString("YouCreatedBroadcastList", R.string.YouCreatedBroadcastList)
				}

				is TLRPC.TL_messageActionChannelCreate -> {
					val chat = if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) getChat(chats, sChats, messageOwner?.peer_id?.channel_id) else null

					messageText = if (ChatObject.isChannel(chat) && chat.megagroup) {
						context.getString(R.string.ActionCreateMega)
					}
					else {
						context.getString(R.string.ActionCreateChannel)
					}
				}

				is TLRPC.TL_messageActionChatMigrateTo -> {
					messageText = context.getString(R.string.ActionMigrateFromGroup)
				}

				is TLRPC.TL_messageActionChannelMigrateFrom -> {
					messageText = context.getString(R.string.ActionMigrateFromGroup)
				}

				is TLRPC.TL_messageActionPinMessage -> {
					val chat = if (fromUser == null) {
						getChat(chats, sChats, messageOwner?.peer_id?.channel_id)
					}
					else {
						null
					}

					generatePinMessageText(fromUser, chat)
				}

				is TL_messageActionHistoryClear -> {
					messageText = context.getString(R.string.HistoryCleared)
				}

				is TLRPC.TL_messageActionGameScore -> {
					generateGameMessageText(fromUser)
				}

				is TL_messageActionPhoneCall -> {
					val isMissed = action.reason is TL_phoneCallDiscardReasonMissed

					messageText = if (isFromUser && messageOwner?.from_id?.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
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
						else if (action.reason is TL_phoneCallDiscardReasonBusy) {
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

				is TLRPC.TL_messageActionPaymentSent -> {
					val user = getUser(users, sUsers, dialogId)
					generatePaymentSentMessageText(user)
				}

				is TL_messageActionBotAllowed -> {
					val domain = action.domain
					val text = context.getString(R.string.ActionBotAllowed)
					val start = text.indexOf("%1\$s")
					val str = SpannableString(String.format(text, domain))

					if (start >= 0) {
						str.setSpan(URLSpanNoUnderlineBold("http://$domain"), start, start + domain.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}

					messageText = str
				}

				is TL_messageActionSecureValuesSent -> {
					val str = StringBuilder()

					for (type in action.types) {
						if (str.isNotEmpty()) {
							str.append(", ")
						}

						when (type) {
							is TL_secureValueTypePhone -> {
								str.append(context.getString(R.string.ActionBotDocumentPhone))
							}

							is TL_secureValueTypeEmail -> {
								str.append(context.getString(R.string.ActionBotDocumentEmail))
							}

							is TL_secureValueTypeAddress -> {
								str.append(context.getString(R.string.ActionBotDocumentAddress))
							}

							is TL_secureValueTypePersonalDetails -> {
								str.append(context.getString(R.string.ActionBotDocumentIdentity))
							}

							is TL_secureValueTypePassport -> {
								str.append(context.getString(R.string.ActionBotDocumentPassport))
							}

							is TL_secureValueTypeDriverLicense -> {
								str.append(context.getString(R.string.ActionBotDocumentDriverLicence))
							}

							is TL_secureValueTypeIdentityCard -> {
								str.append(context.getString(R.string.ActionBotDocumentIdentityCard))
							}

							is TL_secureValueTypeUtilityBill -> {
								str.append(context.getString(R.string.ActionBotDocumentUtilityBill))
							}

							is TL_secureValueTypeBankStatement -> {
								str.append(context.getString(R.string.ActionBotDocumentBankStatement))
							}

							is TL_secureValueTypeRentalAgreement -> {
								str.append(context.getString(R.string.ActionBotDocumentRentalAgreement))
							}

							is TL_secureValueTypeInternalPassport -> {
								str.append(context.getString(R.string.ActionBotDocumentInternalPassport))
							}

							is TL_secureValueTypePassportRegistration -> {
								str.append(context.getString(R.string.ActionBotDocumentPassportRegistration))
							}

							is TL_secureValueTypeTemporaryRegistration -> {
								str.append(context.getString(R.string.ActionBotDocumentTemporaryRegistration))
							}
						}
					}

					var user: User? = null

					if (messageOwner?.peer_id != null) {
						user = getUser(users, sUsers, messageOwner?.peer_id?.user_id ?: 0L)
					}

					messageText = LocaleController.formatString("ActionBotDocuments", R.string.ActionBotDocuments, UserObject.getFirstName(user), str.toString())
				}

				is TL_messageActionWebViewDataSent -> {
					messageText = LocaleController.formatString("ActionBotWebViewData", R.string.ActionBotWebViewData, action.text)
				}

				is TL_messageActionSetChatTheme -> {
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

				is TL_messageActionChatJoinedByRequest -> {
					if (isUserSelf(fromUser)) {
						val isChannel = ChatObject.isChannelAndNotMegaGroup(messageOwner?.peer_id?.channel_id ?: 0L, currentAccount)
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

			val restrictionReason = MessagesController.getRestrictionReason(messageOwner?.restriction_reason)

			if (!restrictionReason.isNullOrEmpty()) {
				messageText = restrictionReason
				isRestrictedMessage = true
			}
			else if (!isMediaEmpty) {
				val media = getMedia(messageOwner)

				if (media is TL_messageMediaDice) {
					messageText = diceEmoji
				}
				else if (media is TL_messageMediaPoll) {
					messageText = if (media.poll.quiz) {
						context.getString(R.string.QuizPoll)
					}
					else {
						context.getString(R.string.Poll)
					}
				}
				else if (media is TL_messageMediaPhoto) {
					messageText = if (media.ttl_seconds != 0 && messageOwner !is TL_message_secret) {
						context.getString(R.string.AttachDestructingPhoto)
					}
					else {
						context.getString(R.string.AttachPhoto)
					}
				}
				else if (isVideo || media is TL_messageMediaDocument && document is TL_documentEmpty && media.ttl_seconds != 0) {
					messageText = if (media?.ttl_seconds != 0 && messageOwner !is TL_message_secret) {
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
				else if (media is TL_messageMediaGeo || media is TL_messageMediaVenue) {
					messageText = context.getString(R.string.AttachLocation)
				}
				else if (media is TL_messageMediaGeoLive) {
					messageText = context.getString(R.string.AttachLiveLocation)
				}
				else if (media is TL_messageMediaContact) {
					messageText = context.getString(R.string.AttachContact)

					if (!media.vcard.isNullOrEmpty()) {
						vCardData = VCardData.parse(media.vcard)
					}
				}
				else if (media is TL_messageMediaGame) {
					messageText = messageOwner?.message
				}
				else if (media is TL_messageMediaInvoice) {
					messageText = media.description
				}
				else if (media is TL_messageMediaUnsupported) {
					messageText = context.getString(R.string.UnsupportedMedia)
				}
				else if (media is TL_messageMediaDocument) {
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
		return messageOwner?.media?.extended_media is TL_messageExtendedMedia
	}

	fun hasExtendedMedia(): Boolean {
		return messageOwner?.media?.extended_media != null
	}

	fun hasExtendedMediaPreview(): Boolean {
		return messageOwner?.media?.extended_media is TL_messageExtendedMediaPreview
	}

	fun setType() {
		val oldType = type

		type = 1000
		isRoundVideoCached = 0

		if (messageOwner is TL_message || messageOwner is TL_messageForwarded_old2) {
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
			else if (media?.ttl_seconds != 0 && (media?.photo is TL_photoEmpty || document is TL_documentEmpty)) {
				contentType = 1
				type = 10
			}
			else if (media is TL_messageMediaDice) {
				type = TYPE_ANIMATED_STICKER

				if (media.document == null) {
					media.document = TL_document()
					media.document.file_reference = ByteArray(0)
					media.document.mime_type = "application/x-tgsdice"
					media.document.dc_id = Int.MIN_VALUE
					media.document.id = Int.MIN_VALUE.toLong()

					val attributeImageSize = TL_documentAttributeImageSize()
					attributeImageSize.w = 512
					attributeImageSize.h = 512

					media.document.attributes.add(attributeImageSize)
				}
			}
			else if (media is TL_messageMediaPhoto) {
				type = TYPE_PHOTO
			}
			else if (media is TL_messageMediaGeo || media is TL_messageMediaVenue || media is TL_messageMediaGeoLive) {
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
			else if (media is TL_messageMediaContact) {
				type = TYPE_CONTACT
			}
			else if (media is TL_messageMediaPoll) {
				type = TYPE_POLL
				checkedVotes = ArrayList()
			}
			else if (media is TL_messageMediaUnsupported) {
				type = TYPE_COMMON
			}
			else if (media is TL_messageMediaDocument) {
				val document = document

				type = if (document?.mime_type != null) {
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
			else if (media is TL_messageMediaGame) {
				type = TYPE_COMMON
			}
			else if (media is TL_messageMediaInvoice) {
				type = TYPE_COMMON
			}
		}
		else if (messageOwner is TL_messageService) {
			if (messageOwner?.action is TL_messageActionLoginUnknownLocation) {
				type = TYPE_COMMON
			}
			else if (messageOwner?.action is TL_messageActionGiftPremium) {
				contentType = 1
				type = TYPE_GIFT_PREMIUM
			}
			else if (messageOwner?.action is TL_messageActionChatEditPhoto || messageOwner?.action is TL_messageActionUserUpdatedPhoto) {
				contentType = 1
				type = 11
			}
			else if (messageOwner?.action is TL_messageEncryptedAction) {
				if (messageOwner?.action?.encryptedAction is TL_decryptedMessageActionScreenshotMessages || messageOwner?.action?.encryptedAction is TL_decryptedMessageActionSetMessageTTL) {
					contentType = 1
					type = 10
				}
				else {
					contentType = -1
					type = -1
				}
			}
			else if (messageOwner?.action is TL_messageActionHistoryClear) {
				contentType = -1
				type = -1
			}
			else if (messageOwner?.action is TL_messageActionPhoneCall) {
				type = TYPE_CALL
			}
			else {
				contentType = 1
				type = 10
			}
		}

		if (oldType != 1000 && oldType != type && type != TYPE_EMOJIS) {
			updateMessageText(MessagesController.getInstance(currentAccount).users, MessagesController.getInstance(currentAccount).chats, null, null)
			generateThumbs(false)
		}
	}

	fun checkLayout(): Boolean {
		if (type != TYPE_COMMON && type != TYPE_EMOJIS || messageOwner?.peer_id == null || messageText.isNullOrEmpty()) {
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

			val paint = if (getMedia(messageOwner) is TL_messageMediaGame) {
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
				return document.mime_type
			}
			else if (media is TL_messageMediaInvoice) {
				val photo = media.photo

				if (photo != null) {
					return photo.mime_type
				}
			}
			else if (media is TL_messageMediaPhoto) {
				return "image/jpeg"
			}
			else if (media is TL_messageMediaWebPage) {
				if (media.webpage.photo != null) {
					return "image/jpeg"
				}
			}

			return ""
		}

	fun generateThumbs(update: Boolean) {
		if (hasExtendedMediaPreview()) {
			val preview = messageOwner?.media?.extended_media as TL_messageExtendedMediaPreview

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
		else if (messageOwner is TL_messageService) {
			if (messageOwner?.action is TL_messageActionChatEditPhoto) {
				val photo = messageOwner?.action?.photo!!

				if (!update) {
					photoThumbs = ArrayList(photo.sizes)
				}
				else if (photoThumbs != null && photoThumbs!!.isNotEmpty()) {
					for (a in photoThumbs!!.indices) {
						val photoObject = photoThumbs!![a]

						for (b in photo.sizes.indices) {
							val size = photo.sizes[b]

							if (size is TL_photoSizeEmpty) {
								continue
							}

							if (size.type == photoObject?.type) {
								photoObject?.location = size.location
								break
							}
						}
					}
				}

				if (photo.dc_id != 0 && photoThumbs != null) {
					for (thumb in photoThumbs!!) {
						val location = thumb?.location ?: continue
						location.dc_id = photo.dc_id
						location.file_reference = photo.file_reference
					}
				}

				photoThumbsObject = messageOwner?.action?.photo
			}
		}
		else if (emojiAnimatedSticker != null || emojiAnimatedStickerId != null) {
			if (emojiAnimatedStickerColor.isNullOrEmpty() && isDocumentHasThumb(emojiAnimatedSticker)) {
				if (!update || photoThumbs == null) {
					photoThumbs = ArrayList()
					photoThumbs?.addAll(emojiAnimatedSticker!!.thumbs)
				}
				else if (photoThumbs!!.isNotEmpty()) {
					updatePhotoSizeLocations(photoThumbs, emojiAnimatedSticker!!.thumbs)
				}

				photoThumbsObject = emojiAnimatedSticker
			}
		}
		else if (getMedia(messageOwner) != null && getMedia(messageOwner) !is TL_messageMediaEmpty) {
			val media = getMedia(messageOwner)

			if (media is TL_messageMediaPhoto) {
				val photo = media.photo
				if (!update || photoThumbs != null && photoThumbs!!.size != photo.sizes.size) {
					photoThumbs = ArrayList(photo.sizes)
				}
				else if (photoThumbs != null && photoThumbs!!.isNotEmpty()) {
					for (a in photoThumbs!!.indices) {
						val photoObject = photoThumbs!![a] ?: continue

						for (b in photo.sizes.indices) {
							val size = photo.sizes[b]

							if (size == null || size is TL_photoSizeEmpty) {
								continue
							}

							if (size.type == photoObject.type) {
								photoObject.location = size.location
								break
							}
							else if ("s" == photoObject.type && size is TL_photoStrippedSize) {
								photoThumbs!![a] = size
								break
							}
						}
					}
				}

				photoThumbsObject = media.photo
			}
			else if (media is TL_messageMediaDocument) {
				val document = document

				if (isDocumentHasThumb(document)) {
					if (!update || photoThumbs == null) {
						photoThumbs = ArrayList()
						photoThumbs?.addAll(document.thumbs)
					}
					else if (photoThumbs!!.isNotEmpty()) {
						updatePhotoSizeLocations(photoThumbs, document.thumbs)
					}

					photoThumbsObject = document
				}
			}
			else if (media is TL_messageMediaGame) {
				val document = media.game.document

				if (document != null) {
					if (isDocumentHasThumb(document)) {
						if (!update) {
							photoThumbs = ArrayList()
							photoThumbs?.addAll(document.thumbs)
						}
						else if (photoThumbs != null && photoThumbs!!.isNotEmpty()) {
							updatePhotoSizeLocations(photoThumbs, document.thumbs)
						}

						photoThumbsObject = document
					}
				}

				val photo = media.game.photo

				if (photo != null) {
					if (!update || photoThumbs2 == null) {
						photoThumbs2 = ArrayList(photo.sizes)
					}
					else if (photoThumbs2!!.isNotEmpty()) {
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
			else if (media is TL_messageMediaWebPage) {
				val photo = media.webpage.photo
				val document = media.webpage.document

				if (photo != null) {
					if (!update || photoThumbs == null) {
						photoThumbs = ArrayList(photo.sizes)
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
							photoThumbs?.addAll(document.thumbs)
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

	fun replaceWithLink(source: CharSequence, param: String, uids: ArrayList<Long>, usersDict: AbstractMap<Long, User>?, sUsersDict: LongSparseArray<User>?): CharSequence {
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
				ext = document?.mime_type
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
			else if (getMedia(messageOwner) is TL_messageMediaDocument) {
				return FileLoader.MEDIA_DIR_DOCUMENT
			}
			else if (getMedia(messageOwner) is TL_messageMediaPhoto) {
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

		if (media is TL_messageMediaWebPage && media.webpage is TL_webPage && media.webpage.description != null) {
			linkDescription = Spannable.Factory.getInstance().newSpannable(media.webpage.description)

			var siteName = media.webpage.site_name

			if (siteName != null) {
				siteName = siteName.lowercase()
			}

			if ("instagram" == siteName) {
				hashtagsType = 1
			}
			else if ("twitter" == siteName) {
				hashtagsType = 2
			}
		}
		else if (media is TL_messageMediaGame && media.game.description != null) {
			linkDescription = Spannable.Factory.getInstance().newSpannable(media.game.description)
		}
		else if (media is TL_messageMediaInvoice && media.description != null) {
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

		if (!isMediaEmpty && media !is TL_messageMediaGame && !messageOwner.message.isNullOrEmpty()) {
			caption = Emoji.replaceEmoji(messageOwner.message, Theme.chat_msgTextPaint.fontMetricsInt, false)
			caption = replaceAnimatedEmoji(caption, messageOwner.entities, Theme.chat_msgTextPaint.fontMetricsInt)

			val hasEntities = if (messageOwner.send_state != MESSAGE_SEND_STATE_SENT) {
				false
			}
			else {
				messageOwner.entities.isNotEmpty()
			}

			val useManualParse = !hasEntities && (eventId != 0L || media is TL_messageMediaPhoto_old || media is TL_messageMediaPhoto_layer68 || media is TL_messageMediaPhoto_layer74 || media is TL_messageMediaDocument_old || media is TL_messageMediaDocument_layer68 || media is TL_messageMediaDocument_layer74 || isOut && messageOwner.send_state != MESSAGE_SEND_STATE_SENT || messageOwner.id < 0)

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

			return messageOwner?.groupId ?: 0L
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

			val entityItalic = TL_messageEntityItalic()
			entityItalic.offset = 0
			entityItalic.length = text.length
			entities.add(entityItalic)

			return addEntitiesToText(text, entities, isOutOwner, true, photoViewer, useManualParse)
		}
		else {
			return addEntitiesToText(text, messageOwner?.entities ?: arrayListOf(), isOutOwner, true, photoViewer, useManualParse, messageOwner?.dialog_id ?: 0L)
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
		else if (messageOwner.noforwards) {
			return false
		}
		else if (messageOwner.fwd_from != null && !isOutOwner && messageOwner.fwd_from?.saved_from_peer != null && dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
			return true
		}
		else if (type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER || type == TYPE_EMOJIS) {
			return false
		}
		else if (messageOwner.fwd_from != null && messageOwner.fwd_from?.from_id is TL_peerChannel && !isOutOwner) {
			return true
		}
		else if (isFromUser) {
			if (getMedia(messageOwner) is TL_messageMediaEmpty || getMedia(messageOwner) == null || getMedia(messageOwner) is TL_messageMediaWebPage && getMedia(messageOwner)?.webpage !is TL_webPage) {
				return false
			}

			val user = MessagesController.getInstance(currentAccount).getUser(messageOwner.from_id?.user_id)

			if (user != null && user.bot && !hasExtendedMedia()) {
				return true
			}

			if (!isOut) {
				if (getMedia(messageOwner) is TL_messageMediaGame || getMedia(messageOwner) is TL_messageMediaInvoice && !hasExtendedMedia()) {
					return true
				}
				val chat = if (messageOwner.peer_id != null && messageOwner.peer_id?.channel_id != 0L) getChat(null, null, messageOwner.peer_id?.channel_id) else null

				if (ChatObject.isChannel(chat) && chat.megagroup) {
					return chat.username != null && chat.username.isNotEmpty() && getMedia(messageOwner) !is TL_messageMediaContact && getMedia(messageOwner) !is TL_messageMediaGeo
				}
			}
		}
		else if (messageOwner.from_id is TL_peerChannel || messageOwner.post) {
			if (isSupergroup) {
				return false
			}

			return messageOwner.peer_id?.channel_id != 0L && (messageOwner.via_bot_id == 0L && messageOwner.reply_to == null || type != TYPE_STICKER && type != TYPE_ANIMATED_STICKER)
		}

		return false
	}

	val isYouTubeVideo: Boolean
		get() {
			val media = getMedia(messageOwner)

			return media is TL_messageMediaWebPage && media.webpage != null && !media.webpage?.embed_url.isNullOrEmpty() && "YouTube" == media.webpage?.site_name
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

			if (media is TL_messageMediaWebPage && media.webpage != null && "telegram_background" == media.webpage.type) {
				try {
					val uri = Uri.parse(media.webpage.url)
					val segment = uri.lastPathSegment

					if (uri.getQueryParameter("bg_color") != null) {
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

				if (media is TL_messageMediaGame) {
					maxWidth -= AndroidUtilities.dp(10f)
				}
			}

			if (emojiOnlyCount >= 1 && totalAnimatedEmojiCount <= 100 && (emojiOnlyCount - totalAnimatedEmojiCount) < (if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) 100 else 50) && (hasValidReplyMessageObject() || isForwarded)) {
				maxWidth = min(maxWidth.toDouble(), (generatedWithMinSize * .65f).toInt().toDouble()).toInt()
			}

			return maxWidth
		}

	fun generateLayout() {
		if (type != 0 && type != TYPE_EMOJIS || messageOwner?.peer_id == null || messageText.isNullOrEmpty()) {
			return
		}

		generateLinkDescription()

		textLayoutBlocks = ArrayList()
		textWidth = 0

		val hasEntities = if (messageOwner?.send_state != MESSAGE_SEND_STATE_SENT) {
			false
		}
		else {
			!messageOwner?.entities.isNullOrEmpty()
		}

		val useManualParse = !hasEntities && (eventId != 0L || messageOwner is TL_message_old || messageOwner is TL_message_old2 || messageOwner is TL_message_old3 || messageOwner is TL_message_old4 || messageOwner is TL_messageForwarded_old || messageOwner is TL_messageForwarded_old2 || messageOwner is TL_message_secret || getMedia(messageOwner) is TL_messageMediaInvoice || isOut && messageOwner?.send_state != MESSAGE_SEND_STATE_SENT || (messageOwner?.id ?: 0) < 0 || getMedia(messageOwner) is TL_messageMediaUnsupported)

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

		val paint = if (getMedia(messageOwner) is TL_messageMediaGame) {
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
			val chat = if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) getChat(null, null, messageOwner?.peer_id?.channel_id) else null

			if (messageOwner?.out != true || (messageOwner?.from_id !is TL_peerUser) && (messageOwner?.from_id !is TL_peerChannel || ChatObject.isChannel(chat) && !chat.megagroup) || messageOwner?.post == true) {
				return false
			}

			if (messageOwner?.fwd_from == null) {
				return true
			}

			val selfUserId = UserConfig.getInstance(currentAccount).getClientUserId()

			if (dialogId == selfUserId) {
				return messageOwner?.fwd_from?.from_id is TL_peerUser && messageOwner?.fwd_from?.from_id?.user_id == selfUserId && (messageOwner?.fwd_from?.saved_from_peer == null || messageOwner?.fwd_from?.saved_from_peer?.user_id == selfUserId) || messageOwner?.fwd_from?.saved_from_peer != null && messageOwner?.fwd_from?.saved_from_peer?.user_id == selfUserId && (messageOwner?.fwd_from?.from_id == null || messageOwner?.fwd_from?.from_id?.user_id == selfUserId)
			}

			return messageOwner?.fwd_from?.saved_from_peer == null || messageOwner?.fwd_from?.saved_from_peer?.user_id == selfUserId
		}

	fun needDrawAvatar(): Boolean {
		if (customAvatarDrawable != null) {
			return true
		}

		if (isSponsored && isFromChat) {
			return true
		}

		return !isSponsored && (isFromUser || isFromGroup || eventId != 0L || messageOwner?.fwd_from != null && messageOwner?.fwd_from?.saved_from_peer != null)
	}

	fun needDrawAvatarInternal(): Boolean {
		if (customAvatarDrawable != null) {
			return true
		}

		return !isSponsored && (isFromChat && isFromUser || isFromGroup || eventId != 0L || messageOwner?.fwd_from != null && messageOwner?.fwd_from?.saved_from_peer != null)
	}

	val isFromChat: Boolean
		get() {
			if (dialogId == UserConfig.getInstance(currentAccount).clientUserId) {
				return true
			}

			val chat = if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) getChat(null, null, messageOwner?.peer_id?.channel_id) else null

			if (ChatObject.isChannel(chat) && chat.megagroup || messageOwner?.peer_id != null && messageOwner?.peer_id!!.chat_id != 0L) {
				return true
			}

			if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) {
				return chat != null && chat.megagroup
			}

			return false
		}

	val fromChatId: Long
		get() = getFromChatId(messageOwner)

	val chatId: Long
		get() {
			if (messageOwner?.peer_id is TL_peerChat) {
				return messageOwner?.peer_id?.chat_id ?: 0L
			}
			else if (messageOwner?.peer_id is TL_peerChannel) {
				return messageOwner?.peer_id?.channel_id ?: 0L
			}

			return 0L
		}

	val isFromUser: Boolean
		get() = messageOwner?.from_id is TL_peerUser && messageOwner?.post != true

	val isFromGroup: Boolean
		get() {
			val chat = if (messageOwner?.peer_id != null && messageOwner?.peer_id?.channel_id != 0L) getChat(null, null, messageOwner?.peer_id?.channel_id) else null
			return messageOwner?.from_id is TL_peerChannel && ChatObject.isChannel(chat) && chat.megagroup
		}

	val isForwardedChannelPost: Boolean
		get() = messageOwner?.from_id is TL_peerChannel && messageOwner?.fwd_from != null && messageOwner?.fwd_from?.channel_post != 0 && messageOwner?.fwd_from?.saved_from_peer is TL_peerChannel && messageOwner?.from_id?.channel_id == messageOwner?.fwd_from?.saved_from_peer?.channel_id

	val isUnread: Boolean
		get() = messageOwner?.unread == true

	val isContentUnread: Boolean
		get() = messageOwner?.media_unread == true

	fun setIsRead() {
		messageOwner?.unread = false
	}

	val unradFlags: Int
		get() = getUnreadFlags(messageOwner)

	fun setContentIsRead() {
		messageOwner?.media_unread = false
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
		else if (messageOwner is TL_message_secret) {
			val ttl = max(messageOwner?.ttl?.toDouble() ?: 0.0, (getMedia(messageOwner)?.ttl_seconds?.toDouble() ?: 0.0)).toInt()
			return ttl > 0 && ((getMedia(messageOwner) is TL_messageMediaPhoto || isVideo || isGif) && ttl <= 60 || this.isRoundVideo)
		}
		else if (messageOwner is TL_message) {
			return (getMedia(messageOwner) != null && getMedia(messageOwner)?.ttl_seconds != 0) && (getMedia(messageOwner) is TL_messageMediaPhoto || getMedia(messageOwner) is TL_messageMediaDocument)
		}

		return false
	}

	val isSecretMedia: Boolean
		get() {
			if (messageOwner is TL_message_secret) {
				return (((getMedia(messageOwner) is TL_messageMediaPhoto) || this.isGif) && ((messageOwner?.ttl ?: 0) > 0) && ((messageOwner?.ttl ?: 0) <= 60) || this.isVoice || this.isRoundVideo || this.isVideo)
			}
			else if (messageOwner is TL_message) {
				return (getMedia(messageOwner) != null && getMedia(messageOwner)?.ttl_seconds != 0) && (getMedia(messageOwner) is TL_messageMediaPhoto || getMedia(messageOwner) is TL_messageMediaDocument)
			}

			return false
		}

	val isSavedFromMegagroup: Boolean
		get() {
			if (messageOwner?.fwd_from != null && messageOwner?.fwd_from?.saved_from_peer != null && messageOwner?.fwd_from?.saved_from_peer?.channel_id != 0L) {
				val chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwd_from?.saved_from_peer?.channel_id)
				return ChatObject.isMegagroup(chat)
			}

			return false
		}

	val dialogId: Long
		get() = getDialogId(messageOwner)

	fun canStreamVideo(): Boolean {
		val document = document

		if (document == null || document is TL_documentEncrypted) {
			return false
		}

		if (SharedConfig.streamAllVideo) {
			return true
		}

		for (attribute in document.attributes) {
			if (attribute is TL_documentAttributeVideo) {
				return attribute.supports_streaming
			}
		}

		return SharedConfig.streamMkv && "video/x-matroska" == document.mime_type
	}

	val isSending: Boolean
		get() = messageOwner?.send_state == MESSAGE_SEND_STATE_SENDING && (messageOwner?.id ?: 0) < 0

	val isEditing: Boolean
		get() = messageOwner?.send_state == MESSAGE_SEND_STATE_EDITING && (messageOwner?.id ?: 0) > 0

	val isEditingMedia: Boolean
		get() {
			if (getMedia(messageOwner) is TL_messageMediaPhoto) {
				return getMedia(messageOwner)?.photo?.id == 0L
			}
			else if (getMedia(messageOwner) is TL_messageMediaDocument) {
				return getMedia(messageOwner)?.document?.dc_id == 0
			}

			return false
		}

	val isSendError: Boolean
		get() = (messageOwner?.send_state == MESSAGE_SEND_STATE_SEND_ERROR) && ((messageOwner?.id ?: 0 < 0) || (scheduled && ((messageOwner?.id ?: 0) > 0) && ((messageOwner?.date ?: 0) < ConnectionsManager.getInstance(currentAccount).currentTime - 60)))

	val isSent: Boolean
		get() = messageOwner?.send_state == MESSAGE_SEND_STATE_SENT || (messageOwner?.id ?: 0) > 0

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
			val document = document

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeSticker) {
						return attribute.alt
					}
				}
			}

			return null
		}

	val approximateHeight: Int
		get() {
			if (type == TYPE_COMMON) {
				var height = textHeight + (if (getMedia(messageOwner) is TL_messageMediaWebPage && getMedia(messageOwner)?.webpage is TL_webPage) AndroidUtilities.dp(100f) else 0)

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
				val document = document

				if (document != null) {
					for (attribute in document.attributes) {
						if (attribute is TL_documentAttributeImageSize) {
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
			val document = document ?: return null

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeSticker || attribute is TL_documentAttributeCustomEmoji) {
					return if (attribute.alt != null && attribute.alt.isNotEmpty()) attribute.alt else null
				}
			}

			return null
		}

	val isVideoCall: Boolean
		get() = messageOwner?.action is TL_messageActionPhoneCall && messageOwner?.action?.video == true

	val isAnimatedEmoji: Boolean
		get() = emojiAnimatedSticker != null || emojiAnimatedStickerId != null

	val isAnimatedAnimatedEmoji: Boolean
		get() = isAnimatedEmoji && isAnimatedEmoji(document)

	val isDice: Boolean
		get() = getMedia(messageOwner) is TL_messageMediaDice

	val diceEmoji: String?
		get() {
			if (!isDice) {
				return null
			}

			val messageMediaDice = getMedia(messageOwner) as? TL_messageMediaDice

			if (messageMediaDice?.emoticon.isNullOrEmpty()) {
				return "\uD83C\uDFB2"
			}

			return messageMediaDice?.emoticon?.replace("\ufe0f", "")
		}

	val diceValue: Int
		get() = (getMedia(messageOwner) as? TL_messageMediaDice)?.value ?: -1

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

		if (media is TL_messageMediaPhoto) {
			return media.photo != null && media.photo.has_stickers
		}
		else if (media is TL_messageMediaDocument) {
			return isDocumentHasAttachedStickers(media.document)
		}

		return false
	}

	val isGif: Boolean
		get() = isGifMessage(messageOwner)

	val isWebpageDocument: Boolean
		get() = getMedia(messageOwner) is TL_messageMediaWebPage && getMedia(messageOwner)?.webpage?.document != null && !isGifDocument(getMedia(messageOwner)?.webpage?.document)

	val isWebpage: Boolean
		get() = getMedia(messageOwner) is TL_messageMediaWebPage

	val isNewGif: Boolean
		get() = getMedia(messageOwner) != null && isNewGifDocument(document)

	private val isAndroidTheme: Boolean
		get() {
			if (getMedia(messageOwner) != null && getMedia(messageOwner)?.webpage != null && !getMedia(messageOwner)?.webpage?.attributes.isNullOrEmpty()) {
				val mediaAttributes = getMedia(messageOwner)?.webpage?.attributes

				if (mediaAttributes != null) {
					for (attribute in mediaAttributes) {
						for (document in attribute.documents) {
							if ("application/x-tgtheme-android" == document.mime_type) {
								return true
							}
						}

						if (attribute.settings != null) {
							return true
						}
					}
				}
			}

			return false
		}

	val musicTitle: String?
		get() = getMusicTitle(true)

	fun getMusicTitle(unknown: Boolean): String? {
		val document = document

		if (document != null) {
			for (a in document.attributes.indices) {
				val attribute = document.attributes[a]

				if (attribute is TL_documentAttributeAudio) {
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
				else if (attribute is TL_documentAttributeVideo) {
					if (attribute.round_message) {
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
			val document = document ?: return 0

			if (audioPlayerDuration > 0) {
				return audioPlayerDuration
			}

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeAudio) {
					return attribute.duration
				}
				else if (attribute is TL_documentAttributeVideo) {
					return attribute.duration
				}
			}

			return audioPlayerDuration
		}

	fun getArtworkUrl(small: Boolean): String? {
		val document = document

		if (document != null) {
			if ("audio/ogg" == document.mime_type) {
				return null
			}

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeAudio) {
					if (attribute.voice) {
						return null
					}
					else {
						var performer = attribute.performer
						val title = attribute.title

						if (!performer.isNullOrEmpty()) {
							for (excludeWord in excludeWords) {
								performer = performer.replace(excludeWord, " ")
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
		val document = document

		if (document != null) {
			var isVoice = false

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeAudio) {
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
				else if (attribute is TL_documentAttributeVideo) {
					if (attribute.round_message) {
						isVoice = true
					}
				}

				if (isVoice) {
					if (!unknown) {
						return null
					}

					if (isOutOwner || messageOwner?.fwd_from != null && messageOwner?.fwd_from?.from_id is TL_peerUser && messageOwner?.fwd_from?.from_id?.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
						return ApplicationLoader.applicationContext.getString(R.string.FromYou)
					}

					var user: User? = null
					var chat: Chat? = null

					if (messageOwner?.fwd_from != null && messageOwner?.fwd_from?.from_id is TL_peerChannel) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwd_from?.from_id?.channel_id)
					}
					else if (messageOwner?.fwd_from != null && messageOwner?.fwd_from?.from_id is TL_peerChat) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwd_from?.from_id?.chat_id)
					}
					else if (messageOwner?.fwd_from != null && messageOwner?.fwd_from?.from_id is TL_peerUser) {
						user = MessagesController.getInstance(currentAccount).getUser(messageOwner?.fwd_from?.from_id?.user_id)
					}
					else if (messageOwner?.fwd_from != null && messageOwner?.fwd_from?.from_name != null) {
						return messageOwner?.fwd_from?.from_name
					}
					else if (messageOwner?.from_id is TL_peerChat) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.from_id?.chat_id)
					}
					else if (messageOwner?.from_id is TL_peerChannel) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.from_id?.channel_id)
					}
					else if (messageOwner?.from_id == null && messageOwner?.peer_id?.channel_id != 0L) {
						chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.peer_id?.channel_id)
					}
					else {
						user = MessagesController.getInstance(currentAccount).getUser(messageOwner?.from_id?.user_id)
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
		return (messageOwner.flags and TLRPC.MESSAGE_FLAG_FWD) != 0 && (messageOwner.fwd_from != null) && !messageOwner.fwd_from!!.imported && (messageOwner.fwd_from!!.saved_from_peer == null || messageOwner.fwd_from!!.from_id !is TL_peerChannel || messageOwner.fwd_from!!.saved_from_peer.channel_id != messageOwner.fwd_from!!.from_id.channel_id) && (UserConfig.getInstance(currentAccount).getClientUserId() != dialogId)
	}

	val isReply: Boolean
		get() {
			val replyMessageObject = replyMessageObject
			return (!(replyMessageObject != null && replyMessageObject.messageOwner is TL_messageEmpty) && messageOwner?.reply_to != null && (messageOwner?.reply_to?.reply_to_msg_id != 0 || messageOwner?.reply_to?.reply_to_random_id != 0L)) && (messageOwner!!.flags and TLRPC.MESSAGE_FLAG_REPLY) != 0
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
		return messageOwner?.replies != null && (chatId == 0L || messageOwner?.replies?.channel_id == chatId)
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
		return messageOwner !is TL_message_secret && !needDrawBluredPreview() && !isLiveLocation && type != 16 && !isSponsored && messageOwner?.noforwards != true
	}

	fun canEditMedia(): Boolean {
		if (isSecretMedia) {
			return false
		}
		else if (getMedia(messageOwner) is TL_messageMediaPhoto) {
			return true
		}
		else if (getMedia(messageOwner) is TL_messageMediaDocument) {
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
			if (messageOwner?.fwd_from != null) {
				if (messageOwner?.fwd_from?.from_id is TL_peerChannel) {
					val chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwd_from?.from_id?.channel_id)
					if (chat != null) {
						return chat.title
					}
				}
				else if (messageOwner?.fwd_from?.from_id is TL_peerChat) {
					val chat = MessagesController.getInstance(currentAccount).getChat(messageOwner?.fwd_from?.from_id?.chat_id)

					if (chat != null) {
						return chat.title
					}
				}
				else if (messageOwner?.fwd_from?.from_id is TL_peerUser) {
					val user = MessagesController.getInstance(currentAccount).getUser(messageOwner?.fwd_from?.from_id?.user_id)

					if (user != null) {
						return UserObject.getUserName(user)
					}
				}
				else if (messageOwner?.fwd_from?.from_name != null) {
					return messageOwner?.fwd_from?.from_name
				}
			}

			return null
		}

	val replyMsgId: Int
		get() = messageOwner?.reply_to?.reply_to_msg_id ?: 0

	val replyTopMsgId: Int
		get() = messageOwner?.reply_to?.reply_to_top_id ?: 0

	val replyAnyMsgId: Int
		get() {
			if (messageOwner?.reply_to != null) {
				return if (messageOwner?.reply_to?.reply_to_top_id != 0) {
					messageOwner?.reply_to?.reply_to_top_id ?: 0
				}
				else {
					messageOwner?.reply_to?.reply_to_msg_id ?: 0
				}
			}

			return 0
		}

	val isPrivateForward: Boolean
		get() = !messageOwner?.fwd_from?.from_name.isNullOrEmpty()

	val isImportedForward: Boolean
		get() = messageOwner?.fwd_from?.imported == true

	val senderId: Long
		get() {
			val messageOwner = messageOwner

			if (messageOwner?.fwd_from != null && messageOwner.fwd_from!!.saved_from_peer != null) {
				if (messageOwner.fwd_from!!.saved_from_peer.user_id != 0L) {
					return if (messageOwner.fwd_from!!.from_id is TL_peerUser) {
						messageOwner.fwd_from!!.from_id.user_id
					}
					else {
						messageOwner.fwd_from!!.saved_from_peer.user_id
					}
				}
				else if (messageOwner.fwd_from!!.saved_from_peer.channel_id != 0L) {
					return if (isSavedFromMegagroup && messageOwner.fwd_from!!.from_id is TL_peerUser) {
						messageOwner.fwd_from!!.from_id.user_id
					}
					else if (messageOwner.fwd_from!!.from_id is TL_peerChannel) {
						-messageOwner.fwd_from!!.from_id.channel_id
					}
					else if (messageOwner.fwd_from!!.from_id is TL_peerChat) {
						-messageOwner.fwd_from!!.from_id.chat_id
					}
					else {
						-messageOwner.fwd_from!!.saved_from_peer.channel_id
					}
				}
				else if (messageOwner.fwd_from!!.saved_from_peer.chat_id != 0L) {
					return when (messageOwner.fwd_from!!.from_id) {
						is TL_peerUser -> messageOwner.fwd_from!!.from_id.user_id
						is TL_peerChannel -> -messageOwner.fwd_from!!.from_id.channel_id
						is TL_peerChat -> -messageOwner.fwd_from!!.from_id.chat_id
						else -> -messageOwner.fwd_from!!.saved_from_peer.chat_id
					}
				}
			}
			else if (messageOwner?.from_id is TL_peerUser) {
				return messageOwner.from_id!!.user_id
			}
			else if (messageOwner?.from_id is TL_peerChannel) {
				return -messageOwner.from_id!!.channel_id
			}
			else if (messageOwner?.from_id is TL_peerChat) {
				return -messageOwner.from_id!!.chat_id
			}
			else if (messageOwner?.post == true) {
				return messageOwner.peer_id?.channel_id ?: 0
			}

			return 0
		}

	val isWallpaper: Boolean
		get() = getMedia(messageOwner) is TL_messageMediaWebPage && "telegram_background" == getMedia(messageOwner)?.webpage?.type

	val isTheme: Boolean
		get() = getMedia(messageOwner) is TL_messageMediaWebPage && "telegram_theme" == getMedia(messageOwner)?.webpage?.type

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
			val preview = messageOwner?.media?.extended_media as? TL_messageExtendedMediaPreview

			if (preview?.thumb != null) {
				val file = FileLoader.getInstance(currentAccount).getPathToAttach(preview.thumb)

				if (!mediaExists) {
					mediaExists = file.exists() || preview.thumb is TL_photoStrippedSize
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
				val photo = messageOwner?.action?.photo

				if (photo == null || photo.video_sizes.isEmpty()) {
					return
				}

				mediaExists = FileLoader.getInstance(currentAccount).getPathToAttach(photo.video_sizes[0], null, true, useFileDatabaseQueue).exists()
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

		if (media is TL_messageMediaWebPage && media.webpage is TL_webPage) {
			val webPage = media.webpage

			var title = webPage.title

			if (title == null) {
				title = webPage.site_name
			}

			if (title != null) {
				title = title.lowercase()

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
		else if (getMedia(messageOwner) is TL_messageMediaPhoto && getMedia(messageOwner)?.photo != null && !photoThumbs.isNullOrEmpty()) {
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

		if (messageOwner?.originalReactions == null) {
			val r = TL_messageReactions()
			r.canSeeList = isFromGroup || isFromUser

			messageOwner?.reactions = r
		}

		val chosenReactions = mutableListOf<ReactionCount>()
		var newReaction: ReactionCount? = null
		var maxChosenOrder = 0

		val originalReactions = messageOwner?.originalReactions

		if (originalReactions != null) {
			for (i in originalReactions.results.indices) {
				val reactionCount = originalReactions.results[i]

				if (reactionCount.chosen) {
					chosenReactions.add(reactionCount)

					if (reactionCount.chosenOrder > maxChosenOrder) {
						maxChosenOrder = reactionCount.chosenOrder
					}
				}

				val tlReaction = reactionCount.reaction

				if (tlReaction is TL_reactionEmoji) {
					if (visibleReaction?.emojicon == null) {
						continue
					}

					if (tlReaction.emoticon == visibleReaction.emojicon) {
						newReaction = reactionCount
					}
				}

				if (tlReaction is TL_reactionCustomEmoji) {
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
				originalReactions?.results?.remove(newReaction)
			}

			if (originalReactions?.canSeeList == true) {
				var i = 0

				while (i < originalReactions.recentReactions.size) {
					if (getPeerId(originalReactions.recentReactions[i].peer_id) == UserConfig.getInstance(currentAccount).getClientUserId() && ReactionsUtils.compare(messageOwner.originalReactions?.recentReactions?.get(i)?.reaction, visibleReaction)) {
						originalReactions.recentReactions.removeAt(i)
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
				messageOwner?.originalReactions?.results?.remove(chosenReaction)
			}

			chosenReactions.remove(chosenReaction)

			if (messageOwner?.originalReactions?.canSeeList == true) {
				var i = 0

				while (i < messageOwner.originalReactions!!.recentReactions.size) {
					if (getPeerId(messageOwner.originalReactions!!.recentReactions[i].peer_id) == UserConfig.getInstance(currentAccount).getClientUserId() && ReactionsUtils.compare(messageOwner.originalReactions?.recentReactions?.get(i)?.reaction, visibleReaction)) {
						messageOwner.originalReactions!!.recentReactions.removeAt(i)
						i--
					}

					i++
				}
			}
		}

		if (newReaction == null) {
			newReaction = TL_reactionCount()

			if (visibleReaction?.emojicon != null) {
				newReaction.reaction = TL_reactionEmoji()
				(newReaction.reaction as? TL_reactionEmoji)?.emoticon = visibleReaction.emojicon

				messageOwner?.originalReactions?.results?.add(newReaction)
			}
			else {
				newReaction.reaction = TL_reactionCustomEmoji()
				(newReaction.reaction as? TL_reactionCustomEmoji)?.documentId = visibleReaction?.documentId ?: 0L

				messageOwner?.originalReactions?.results?.add(newReaction)
			}
		}

		newReaction.chosen = true
		newReaction.count++
		newReaction.chosenOrder = maxChosenOrder + 1

		if (messageOwner?.originalReactions?.canSeeList == true || ((messageOwner?.dialog_id ?: 0) > 0 && maxReactionsCount > 1)) {
			val action = TL_messagePeerReaction()

			messageOwner?.originalReactions?.recentReactions?.add(0, action)

			action.peer_id = TL_peerUser()
			action.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId()

			if (visibleReaction?.emojicon != null) {
				action.reaction = TL_reactionEmoji()
				(action.reaction as TL_reactionEmoji).emoticon = visibleReaction.emojicon
			}
			else {
				action.reaction = TL_reactionCustomEmoji()
				(action.reaction as TL_reactionCustomEmoji).documentId = visibleReaction?.documentId ?: 0L
			}
		}

		reactionsChanged = true

		return true
	}

	fun probablyRingtone(): Boolean {
		val document = document

		if (document != null && RingtoneDataStore.ringtoneSupportedMimeType.contains(document.mime_type) && document.size < MessagesController.getInstance(currentAccount).ringtoneSizeMax * 2L) {
			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeAudio) {
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
		const val TYPE_GEO: Int = 4 // TL_messageMediaGeo, TL_messageMediaVenue, TL_messageMediaGeoLive
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

			return hasUnreadReactions(message.originalReactions)
		}

		fun hasUnreadReactions(reactions: TL_messageReactions?): Boolean {
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
			if (document?.thumbs == null) {
				return false
			}

			for (i in document.video_thumbs.indices) {
				if ("f" == document.video_thumbs[i].type) {
					return true
				}
			}

			return false
		}

		@JvmStatic
		fun getPremiumStickerAnimation(document: TLRPC.Document?): VideoSize? {
			if (document?.thumbs == null) {
				return null
			}

			for (i in document.video_thumbs.indices) {
				if ("f" == document.video_thumbs[i].type) {
					return document.video_thumbs[i]
				}
			}

			return null
		}

		@JvmStatic
		fun updateReactions(message: Message?, reactions: TL_messageReactions?) {
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
		fun updatePollResults(media: TL_messageMediaPoll?, results: PollResults?) {
			if (media == null || results == null) {
				return
			}

			if ((results.flags and 2) != 0) {
				var chosen: ArrayList<ByteArray?>? = null
				var correct: ByteArray? = null

				if (results.min && media.results.results != null) {
					for (answerVoters in media.results.results) {
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

				media.results.results = results.results

				if (chosen != null || correct != null) {
					var b = 0
					val n2 = media.results.results.size

					while (b < n2) {
						val answerVoters = media.results.results[b]

						if (chosen != null) {
							var a = 0
							val n = chosen.size

							while (a < n) {
								if (answerVoters.option.contentEquals(chosen[a])) {
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

						if (correct != null && answerVoters.option.contentEquals(correct)) {
							answerVoters.correct = true
							correct = null
						}

						if (chosen == null && correct == null) {
							break
						}

						b++
					}
				}

				media.results.flags = media.results.flags or 2
			}

			if ((results.flags and 4) != 0) {
				media.results.total_voters = results.total_voters
				media.results.flags = media.results.flags or 4
			}

			if ((results.flags and 8) != 0) {
				media.results.recent_voters = results.recent_voters
				media.results.flags = media.results.flags or 8
			}

			if ((results.flags and 16) != 0) {
				media.results.solution = results.solution
				media.results.solution_entities = results.solution_entities
				media.results.flags = media.results.flags or 16
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

			if (messageOwner.media != null && messageOwner.media!!.extended_media is TL_messageExtendedMedia) {
				return (messageOwner.media!!.extended_media as TL_messageExtendedMedia).media
			}

			return messageOwner.media
		}

		fun isAnimatedStickerDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return document != null && document.mime_type == "video/webm"
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
				returns(true) implies (document != null)
			}

			return isGifDocument(document, false)
		}

		fun isGifDocument(document: TLRPC.Document?, hasGroup: Boolean): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return document?.mime_type != null && (document.mime_type == "image/gif" && !hasGroup || isNewGifDocument(document))
		}

		@JvmStatic
		fun isDocumentHasThumb(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document == null || document.thumbs.isEmpty()) {
				return false
			}

			for (photoSize in document.thumbs) {
				if (photoSize != null && photoSize !is TL_photoSizeEmpty && photoSize.location !is TL_fileLocationUnavailable) {
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

			if (document?.mime_type != null) {
				val mime = document.mime_type.lowercase()

				if (isDocumentHasThumb(document) && (mime == "image/png" || mime == "image/jpg" || mime == "image/jpeg") || (Build.VERSION.SDK_INT >= 26 && (mime == "image/heic"))) {
					for (attribute in document.attributes) {
						if (attribute is TL_documentAttributeImageSize) {
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

			if (document != null && "video/mp4" == document.mime_type) {
				var width = 0
				var height = 0
				var round = false

				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeVideo) {
						width = attribute.w
						height = attribute.h
						round = attribute.round_message
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
					if (it is TL_documentAttributeVideo) {
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

			if (document != null && "video/mp4" == document.mime_type) {
				var width = 0
				var height = 0
				var animated = false

				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeAnimated) {
						animated = true
					}
					else if (attribute is TL_documentAttributeVideo) {
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

			return message != null && message.messageOwner is TL_messageService && message.messageOwner?.action is TL_messageActionContactSignUp
		}

		private fun updatePhotoSizeLocations(o: ArrayList<PhotoSize?>?, n: List<PhotoSize?>) {
			if (o == null) {
				return
			}

			for (photoObject in o) {
				if (photoObject == null) {
					continue
				}

				for (size in n) {
					if (size == null || size is TL_photoSizeEmpty || size is TL_photoCachedSize) {
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
						name = `object`.title
						id = "" + -`object`.id
					}

					is TL_game -> {
						name = `object`.title
						id = "game"
					}

					is TL_chatInviteExported -> {
						name = `object`.link
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

			if (media is TL_messageMediaDocument) {
				return FileLoader.getAttachFileName(getDocument(messageOwner))
			}
			else if (media is TL_messageMediaPhoto) {
				val sizes = media.photo.sizes

				if (sizes.size > 0) {
					val sizeFull = FileLoader.getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize())

					if (sizeFull != null) {
						return FileLoader.getAttachFileName(sizeFull)
					}
				}
			}
			else if (media is TL_messageMediaWebPage) {
				return FileLoader.getAttachFileName(media.webpage.document)
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
				if (attribute is TL_documentAttributeImageSize) {
					return intArrayOf(attribute.w, attribute.h)
				}
				else if (attribute is TL_documentAttributeVideo) {
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
				if (attribute is TL_documentAttributeVideo) {
					return attribute.duration
				}
				else if (attribute is TL_documentAttributeAudio) {
					return attribute.duration
				}
			}

			return 0
		}

		@JvmStatic
		fun getInlineResultWidthAndHeight(inlineResult: BotInlineResult): IntArray {
			var result = getWebDocumentWidthAndHeight(inlineResult.content)

			if (result == null) {
				result = getWebDocumentWidthAndHeight(inlineResult.thumb)

				if (result == null) {
					result = intArrayOf(0, 0)
				}
			}

			return result
		}

		@JvmStatic
		fun getInlineResultDuration(inlineResult: BotInlineResult): Int {
			var result = getWebDocumentDuration(inlineResult.content)

			if (result == 0) {
				result = getWebDocumentDuration(inlineResult.thumb)
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

		fun replaceAnimatedEmoji(text: CharSequence?, entities: ArrayList<MessageEntity>?, fontMetricsInt: FontMetricsInt?): Spannable {
			val spannable = if (text is Spannable) text else SpannableString(text)

			if (entities == null) {
				return spannable
			}

			val emojiSpans = spannable.getSpans(0, spannable.length, EmojiSpan::class.java)

			for (messageEntity in entities) {
				if (messageEntity is TL_messageEntityCustomEmoji) {
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

						val span = if (messageEntity.document != null) {
							AnimatedEmojiSpan(messageEntity.document!!, fontMetricsInt)
						}
						else {
							AnimatedEmojiSpan(messageEntity.documentId, fontMetricsInt)
						}

						spannable.setSpan(span, messageEntity.offset, messageEntity.offset + messageEntity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
				}
			}

			return spannable
		}

		@JvmStatic
		@JvmOverloads
		fun addEntitiesToText(text: CharSequence?, entities: ArrayList<MessageEntity>, out: Boolean, usernames: Boolean, photoViewer: Boolean, useManualParse: Boolean, messageOwnerId: Long = 0L): Boolean {
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

				if (!useManualParse || entity is TL_messageEntityBold || entity is TL_messageEntityItalic || entity is TL_messageEntityStrike || entity is TL_messageEntityUnderline || entity is TL_messageEntityBlockquote || entity is TL_messageEntityCode || entity is TL_messageEntityPre || entity is TL_messageEntityMentionName || entity is TL_inputMessageEntityMentionName || entity is TL_messageEntityTextUrl || entity is TL_messageEntitySpoiler || entity is TL_messageEntityCustomEmoji) {
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

				if (entity is TL_messageEntityCustomEmoji) {
					continue
				}

				val newRun = TextStyleRun()
				newRun.start = entity.offset
				newRun.end = newRun.start + entity.length

				if (entity is TL_messageEntitySpoiler) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_SPOILER
				}
				else if (entity is TL_messageEntityStrike) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_STRIKE
				}
				else if (entity is TL_messageEntityUnderline) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_UNDERLINE
				}
				else if (entity is TL_messageEntityBlockquote) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_QUOTE
				}
				else if (entity is TL_messageEntityBold) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_BOLD
				}
				else if (entity is TL_messageEntityItalic) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_ITALIC
				}
				else if (entity is TL_messageEntityCode || entity is TL_messageEntityPre) {
					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MONO
				}
				else if (entity is TL_messageEntityMentionName) {
					if (!usernames) {
						continue
					}

					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MENTION
					newRun.urlEntity = entity
				}
				else if (entity is TL_inputMessageEntityMentionName) {
					if (!usernames) {
						continue
					}

					newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MENTION
					newRun.urlEntity = entity
				}
				else {
					if (useManualParse && entity !is TL_messageEntityTextUrl) {
						continue
					}

					if ((entity is TL_messageEntityUrl || entity is TL_messageEntityTextUrl) && Browser.isPassportUrl(entity.url)) {
						continue
					}

					if (entity is TL_messageEntityMention && !usernames) {
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

				if (runUrlEntity is TL_messageEntityBotCommand) {
					text.setSpan(URLSpanBotCommand(url, t.toInt(), run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TL_messageEntityHashtag || runUrlEntity is TL_messageEntityMention || runUrlEntity is TL_messageEntityCashtag) {
					text.setSpan(URLSpanNoUnderline(url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TL_messageEntityEmail) {
					text.setSpan(URLSpanReplacement("mailto:$url", run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TL_messageEntityUrl) {
					hasUrls = true

					val lowerCase = url?.lowercase()

					if (lowerCase?.contains("://") != true) {
						text.setSpan(URLSpanBrowser("http://$url", run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					else {
						text.setSpan(URLSpanBrowser(url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
				}
				else if (runUrlEntity is TL_messageEntityBankCard) {
					hasUrls = true
					text.setSpan(URLSpanNoUnderline("card:$url", run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TL_messageEntityPhone) {
					hasUrls = true

					var tel = PhoneFormat.stripExceptNumbers(url)

					if (url?.startsWith("+") == true) {
						tel = "+$tel"
					}

					text.setSpan(URLSpanBrowser("tel:$tel", run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TL_messageEntityTextUrl) {
					text.setSpan(URLSpanReplacement(runUrlEntity.url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TL_messageEntityMentionName) {
					text.setSpan(URLSpanUserMention("" + runUrlEntity.userId, t.toInt(), run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else if (runUrlEntity is TL_inputMessageEntityMentionName) {
					text.setSpan(URLSpanUserMention("" + runUrlEntity.userId?.user_id, t.toInt(), run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
			return getPeerId(message?.from_id)
		}

		@JvmStatic
		fun getPeerId(peer: Peer?): Long {
			if (peer == null) {
				return 0L
			}

			return when (peer) {
				is TL_peerChat -> -peer.chat_id
				is TL_peerChannel -> -peer.channel_id
				else -> peer.user_id
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

			if (!message.media_unread) {
				flags = flags or 2
			}

			return flags
		}

		@JvmStatic
		fun getMessageSize(message: Message?): Long {
			val document = when (val media = getMedia(message)) {
				is TL_messageMediaWebPage -> {
					media.webpage.document
				}

				is TL_messageMediaGame -> {
					media.game.document
				}

				else -> {
					media?.document
				}
			}

			return document?.size ?: 0
		}

		@JvmStatic
		fun fixMessagePeer(messages: ArrayList<Message>?, channelId: Long) {
			if (messages.isNullOrEmpty() || channelId == 0L) {
				return
			}

			for (message in messages) {
				if (message is TL_messageEmpty) {
					message.peer_id = TL_peerChannel()
					message.peer_id?.channel_id = channelId
				}
			}
		}

		@JvmStatic
		fun getChannelId(message: Message?): Long {
			return message?.peer_id?.channel_id ?: 0L
		}

		@JvmStatic
		fun shouldEncryptPhotoOrVideo(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return if (message is TL_message_secret) {
				(getMedia(message) is TL_messageMediaPhoto || isVideoMessage(message)) && message.ttl > 0 && message.ttl <= 60
			}
			else {
				(getMedia(message) is TL_messageMediaPhoto || getMedia(message) is TL_messageMediaDocument) && getMedia(message)?.ttl_seconds != 0
			}
		}

		fun isSecretPhotoOrVideo(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			if (message is TL_message_secret) {
				return (getMedia(message) is TL_messageMediaPhoto || isRoundVideoMessage(message) || isVideoMessage(message)) && (message.ttl > 0) && (message.ttl <= 60)
			}
			else if (message is TL_message) {
				return (getMedia(message) is TL_messageMediaPhoto || getMedia(message) is TL_messageMediaDocument) && getMedia(message)!!.ttl_seconds != 0
			}

			return false
		}

		@JvmStatic
		fun isSecretMedia(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			if (message is TL_message_secret) {
				return (getMedia(message) is TL_messageMediaPhoto || isRoundVideoMessage(message) || isVideoMessage(message)) && getMedia(message)?.ttl_seconds != 0
			}
			else if (message is TL_message) {
				return (getMedia(message) is TL_messageMediaPhoto || getMedia(message) is TL_messageMediaDocument) && getMedia(message)?.ttl_seconds != 0
			}

			return false
		}

		@JvmStatic
		fun setUnreadFlags(message: Message, flag: Int) {
			message.unread = (flag and 1) == 0
			message.media_unread = (flag and 2) == 0
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

			return message?.media_unread == true
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

			if (message.dialog_id == 0L && message.peer_id != null) {
				if (message.peer_id!!.chat_id != 0L) {
					message.dialog_id = -message.peer_id!!.chat_id
				}
				else if (message.peer_id!!.channel_id != 0L) {
					message.dialog_id = -message.peer_id!!.channel_id
				}
				else if (message.from_id == null || isOut(message)) {
					message.dialog_id = message.peer_id!!.user_id
				}
				else {
					message.dialog_id = message.from_id!!.user_id
				}
			}

			return message.dialog_id
		}

		@JvmStatic
		fun isWebM(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			return "video/webm" == document?.mime_type
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

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeSticker) {
						return "image/webp" == document.mime_type || "video/webm" == document.mime_type
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

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeSticker || attribute is TL_documentAttributeCustomEmoji) {
						return "video/webm" == document.mime_type
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

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeSticker && attribute.stickerset != null && attribute.stickerset !is TL_inputStickerSetEmpty) {
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

			if (document != null && ("application/x-tgsticker" == document.mime_type && document.thumbs.isNotEmpty() || "application/x-tgsdice" == document.mime_type)) {
				if (allowWithoutSet) {
					return true
				}

				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeSticker) {
						return attribute.stickerset is TL_inputStickerSetShortName
					}
					else if (attribute is TL_documentAttributeCustomEmoji) {
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

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeSticker && attribute.mask) {
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

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeAudio) {
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

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeAudio) {
						return !attribute.voice
					}
				}

				if (!document.mime_type.isNullOrEmpty()) {
					val mime = document.mime_type.lowercase()

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
		fun getDocumentVideoThumb(document: TLRPC.Document?): VideoSize? {
			contract {
				returnsNotNull() implies (document != null)
			}

			return document?.video_thumbs?.firstOrNull()
		}

		@JvmStatic
		fun isVideoDocument(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document == null) {
				return false
			}

			var isAnimated = false
			var isVideo = false
			var width = 0
			var height = 0

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeVideo) {
					if (attribute.round_message) {
						return false
					}

					isVideo = true
					width = attribute.w
					height = attribute.h
				}
				else if (attribute is TL_documentAttributeAnimated) {
					isAnimated = true
				}
			}

			if (isAnimated && (width > 1280 || height > 1280)) {
				isAnimated = false
			}

			if (SharedConfig.streamMkv && !isVideo && "video/x-matroska" == document.mime_type) {
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

			if (media is TL_messageMediaWebPage) {
				return media.webpage.document
			}
			else if (media is TL_messageMediaGame) {
				return media.game.document
			}

			return media?.document
		}

		@JvmStatic
		fun getPhoto(message: Message?): TLRPC.Photo? {
			contract {
				returnsNotNull() implies (message != null)
			}

			val media = getMedia(message)

			if (media is TL_messageMediaWebPage) {
				return media.webpage.photo
			}

			return media?.photo
		}

		@JvmStatic
		fun isStickerMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return isStickerDocument(getMedia(message)?.document)
		}

		@JvmStatic
		fun isAnimatedStickerMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val isSecretChat = DialogObject.isEncryptedDialog(message?.dialog_id)

			if (isSecretChat && message?.stickerVerified != 1) {
				return false
			}

			val media = getMedia(message)

			return isAnimatedStickerDocument(media?.document, !isSecretChat || message?.out == true)
		}

		fun isLocationMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			return media is TL_messageMediaGeo || media is TL_messageMediaGeoLive || media is TL_messageMediaVenue
		}

		fun isMaskMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return isMaskDocument(getMedia(message)?.document)
		}

		@JvmStatic
		fun isMusicMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (media is TL_messageMediaWebPage) {
				return isMusicDocument(media.webpage.document)
			}

			return isMusicDocument(media?.document)
		}

		@JvmStatic
		fun isGifMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (media is TL_messageMediaWebPage) {
				return isGifDocument(media.webpage.document)
			}

			return media != null && isGifDocument(media.document, message?.realGroupId != 0L)
		}

		@JvmStatic
		fun isRoundVideoMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (media is TL_messageMediaWebPage) {
				return isRoundVideoDocument(media.webpage.document)
			}

			return isRoundVideoDocument(media?.document)
		}

		@JvmStatic
		fun isPhoto(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (media is TL_messageMediaWebPage) {
				return media.webpage.photo is TL_photo && media.webpage.document !is TL_document
			}

			return media is TL_messageMediaPhoto
		}

		@JvmStatic
		fun isVoiceMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (media is TL_messageMediaWebPage) {
				return isVoiceDocument(media.webpage.document)
			}

			return isVoiceDocument(media?.document)
		}

		@JvmStatic
		fun isNewGifMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (media is TL_messageMediaWebPage) {
				return isNewGifDocument(media.webpage.document)
			}

			return isNewGifDocument(media?.document)
		}

		fun isLiveLocationMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return getMedia(message) is TL_messageMediaGeoLive
		}

		@JvmStatic
		fun isVideoMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			val media = getMedia(message)

			if (isVideoSticker(media?.document)) {
				return false
			}

			if (media is TL_messageMediaWebPage) {
				return isVideoDocument(media.webpage.document)
			}

			return isVideoDocument(media?.document)
		}

		@JvmStatic
		fun isGameMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return getMedia(message) is TL_messageMediaGame
		}

		fun isInvoiceMessage(message: Message?): Boolean {
			contract {
				returns(true) implies (message != null)
			}

			return getMedia(message) is TL_messageMediaInvoice
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

			if (document == null) {
				return null
			}

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeSticker || attribute is TL_documentAttributeCustomEmoji) {
					if (attribute.stickerset is TL_inputStickerSetEmpty) {
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
			if (document == null) {
				return fallback
			}

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeCustomEmoji || attribute is TL_documentAttributeSticker) {
					return attribute.alt
				}
			}

			return fallback
		}

		fun isAnimatedEmoji(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document == null) {
				return false
			}

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeCustomEmoji) {
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

			if (document == null) {
				return false
			}

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeCustomEmoji) {
					return attribute.free
				}
			}

			return false
		}

		@JvmStatic
		fun isPremiumEmojiPack(set: TL_messages_stickerSet?): Boolean {
			contract {
				returns(true) implies (set != null)
			}

			if (set?.set != null && !set.set.emojis) {
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

			if (covered?.set != null && !covered.set.emojis) {
				return false
			}

			val documents = if (covered is TL_stickerSetFullCovered) covered.documents else covered?.covers

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
			if (document == null) {
				return -1
			}

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeSticker) {
					if (attribute.stickerset is TL_inputStickerSetEmpty) {
						return -1
					}

					return attribute.stickerset.id
				}
			}

			return -1
		}

		@JvmStatic
		fun getStickerSetName(document: TLRPC.Document?): String? {
			contract {
				returnsNotNull() implies (document != null)
			}

			if (document == null) {
				return null
			}

			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeSticker) {
					if (attribute.stickerset is TL_inputStickerSetEmpty) {
						return null
					}

					return attribute.stickerset.short_name
				}
			}

			return null
		}

		fun isDocumentHasAttachedStickers(document: TLRPC.Document?): Boolean {
			contract {
				returns(true) implies (document != null)
			}

			if (document != null) {
				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeHasStickers) {
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

			return (message.flags and TLRPC.MESSAGE_FLAG_FWD) != 0 && message.fwd_from != null
		}

		fun isMediaEmpty(message: Message?): Boolean {
			return message == null || getMedia(message) == null || getMedia(message) is TL_messageMediaEmpty || getMedia(message) is TL_messageMediaWebPage
		}

		fun isMediaEmptyWebpage(message: Message?): Boolean {
			return message == null || getMedia(message) == null || getMedia(message) is TL_messageMediaEmpty
		}

		fun canEditMessageAnytime(currentAccount: Int, message: Message?, chat: Chat?): Boolean {
			@Suppress("NAME_SHADOWING") var chat = chat

			if (message?.peer_id == null || getMedia(message) != null && (isRoundVideoDocument(getMedia(message)?.document) || isStickerDocument(getMedia(message)?.document) || isAnimatedStickerDocument(getMedia(message)?.document, true)) || message.action != null && message.action !is TL_messageActionEmpty || isForwardedMessage(message) || message.via_bot_id != 0L || message.id < 0) {
				return false
			}

			if (message.from_id is TL_peerUser && message.from_id?.user_id == message.peer_id?.user_id && message.from_id?.user_id == UserConfig.getInstance(currentAccount).getClientUserId() && !isLiveLocationMessage(message)) {
				return true
			}

			if (chat == null && message.peer_id?.channel_id != 0L) {
				chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(message.peer_id?.channel_id)

				if (chat == null) {
					return false
				}
			}

			if (ChatObject.isChannel(chat) && !chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.edit_messages)) {
				return true
			}

			return message.out && chat != null && chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.pin_messages || chat.default_banned_rights != null && !chat.default_banned_rights.pin_messages)
		}

		fun canEditMessageScheduleTime(currentAccount: Int, message: Message?, chat: Chat?): Boolean {
			@Suppress("NAME_SHADOWING") var chat = chat

			if (chat == null && message?.peer_id?.channel_id != 0L) {
				chat = MessagesController.getInstance(currentAccount).getChat(message?.peer_id?.channel_id)

				if (chat == null) {
					return false
				}
			}

			if (!ChatObject.isChannel(chat) || chat.megagroup || chat.creator) {
				return true
			}

			return chat.admin_rights != null && (chat.admin_rights.edit_messages || message?.out == true)
		}

		fun canEditMessage(currentAccount: Int, message: Message?, chat: Chat?, scheduled: Boolean): Boolean {
			@Suppress("NAME_SHADOWING") var chat = chat

			if (message != null && (message.is_media_sale || message.is_media_sale_info || message.mediaHash != null)) {
				return false
			}

			if (scheduled && message!!.date < ConnectionsManager.getInstance(currentAccount).currentTime - 60) {
				return false
			}

			if (chat != null && (chat.left || chat.kicked) && (!chat.megagroup || !chat.has_link)) {
				return false
			}

			if (message?.peer_id == null || getMedia(message) != null && (isRoundVideoDocument(getMedia(message)?.document) || isStickerDocument(getMedia(message)?.document) || isAnimatedStickerDocument(getMedia(message)?.document, true) || isLocationMessage(message)) || message.action != null && message.action !is TL_messageActionEmpty || isForwardedMessage(message) || message.via_bot_id != 0L || message.id < 0) {
				return false
			}

			if (message.from_id is TL_peerUser && message.from_id?.user_id == message.peer_id?.user_id && message.from_id?.user_id == UserConfig.getInstance(currentAccount).getClientUserId() && !isLiveLocationMessage(message) && getMedia(message) !is TL_messageMediaContact) {
				return true
			}

			if (chat == null && message.peer_id?.channel_id != 0L) {
				chat = MessagesController.getInstance(currentAccount).getChat(message.peer_id?.channel_id)

				if (chat == null) {
					return false
				}
			}

			if (getMedia(message) != null && getMedia(message) !is TL_messageMediaEmpty && getMedia(message) !is TL_messageMediaPhoto && getMedia(message) !is TL_messageMediaDocument && getMedia(message) !is TL_messageMediaWebPage) {
				return false
			}

			if (ChatObject.isChannel(chat) && !chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.edit_messages)) {
				return true
			}

			if (message.out && chat != null && chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.pin_messages || chat.default_banned_rights != null && !chat.default_banned_rights.pin_messages)) {
				return true
			}

			if (!scheduled && abs((message.date - ConnectionsManager.getInstance(currentAccount).currentTime).toDouble()) > MessagesController.getInstance(currentAccount).maxEditTime) {
				return false
			}

			if (message.peer_id?.channel_id == 0L) {
				return (message.out || message.from_id is TL_peerUser && message.from_id?.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) && (getMedia(message) is TL_messageMediaPhoto || getMedia(message) is TL_messageMediaDocument && !isStickerMessage(message) && !isAnimatedStickerMessage(message) || getMedia(message) is TL_messageMediaEmpty || getMedia(message) is TL_messageMediaWebPage || getMedia(message) == null)
			}

			if (chat != null && chat.megagroup && message.out || chat != null && !chat.megagroup && (chat.creator || chat.admin_rights != null && (chat.admin_rights.edit_messages || message.out && chat.admin_rights.post_messages)) && message.post) {
				return getMedia(message) is TL_messageMediaPhoto || getMedia(message) is TL_messageMediaDocument && !isStickerMessage(message) && !isAnimatedStickerMessage(message) || getMedia(message) is TL_messageMediaEmpty || getMedia(message) is TL_messageMediaWebPage || getMedia(message) == null
			}

			return false
		}

		@JvmStatic
		fun canDeleteMessage(currentAccount: Int, inScheduleMode: Boolean, message: Message?, chat: Chat?): Boolean {
			@Suppress("NAME_SHADOWING") var chat = chat

			if (message == null) {
				return false
			}

			if (ChatObject.isChannelAndNotMegaGroup(chat) && message.action is TL_messageActionChatJoinedByRequest) {
				return false
			}

			if (message.id < 0) {
				return true
			}

			if (chat == null && message.peer_id?.channel_id != 0L) {
				chat = MessagesController.getInstance(currentAccount).getChat(message.peer_id?.channel_id)
			}

			if (ChatObject.isChannel(chat)) {
				if (inScheduleMode && !chat.megagroup) {
					return chat.creator || chat.admin_rights != null && (chat.admin_rights.delete_messages || message.out)
				}

				if (message.out && message is TL_messageService) {
					return message.id != 1 && ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES)
				}

				return inScheduleMode || message.id != 1 && (chat.creator || chat.admin_rights != null && (chat.admin_rights.delete_messages || message.out && (chat.megagroup || chat.admin_rights.post_messages)) || chat.megagroup && message.out)
			}

			return inScheduleMode || isOut(message) || !ChatObject.isChannel(chat)
		}

		@JvmStatic
		fun getReplyToDialogId(message: Message): Long {
			if (message.reply_to == null) {
				return 0L
			}

			if (message.reply_to?.reply_to_peer_id != null) {
				return getPeerId(message.reply_to?.reply_to_peer_id)
			}

			return getDialogId(message)
		}
	}
}
