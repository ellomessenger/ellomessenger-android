/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.ui.Cells

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import android.text.style.URLSpan
import android.util.Property
import android.util.SparseArray
import android.util.StateSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewStructure
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.view.animation.Interpolator
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import androidx.core.util.size
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.DialogObject
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.DocumentObject.ThemeDocument
import org.telegram.messenger.DownloadController
import org.telegram.messenger.DownloadController.FileDownloadProgressListener
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.ImageReceiver.ImageReceiverDelegate
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject.getFirstName
import org.telegram.messenger.UserObject.getUserName
import org.telegram.messenger.UserObject.isUserSelf
import org.telegram.messenger.Utilities
import org.telegram.messenger.WebFile
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.messageobject.GroupedMessagePosition
import org.telegram.messenger.messageobject.GroupedMessages
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.messageobject.TextLayoutBlock
import org.telegram.messenger.utils.dp
import org.telegram.messenger.utils.isYouTubeShortsLink
import org.telegram.messenger.video.VideoPlayerRewinder
import org.telegram.tgnet.*
import org.telegram.tgnet.TLRPC.User
import org.telegram.ui.ActionBar.MessageDrawable
import org.telegram.ui.ActionBar.MessageDrawable.PathDrawParams
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextSelectionHelper.ChatListTextSelectionHelper
import org.telegram.ui.Cells.TextSelectionHelper.SelectableView
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AnimatedEmojiSpan.EmojiGroupedSpans
import org.telegram.ui.Components.AnimatedNumberLayout
import org.telegram.ui.Components.AnimationProperties
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.AvatarDrawable.Companion.getNameColorNameForId
import org.telegram.ui.Components.BackgroundGradientDrawable
import org.telegram.ui.Components.CheckBoxBase
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EmptyStubSpan
import org.telegram.ui.Components.FloatSeekBarAccessibilityDelegate
import org.telegram.ui.Components.InfiniteProgress
import org.telegram.ui.Components.LinkPath
import org.telegram.ui.Components.LinkSpanDrawable
import org.telegram.ui.Components.LinkSpanDrawable.LinkCollector
import org.telegram.ui.Components.MediaActionDrawable
import org.telegram.ui.Components.MessageBackgroundDrawable
import org.telegram.ui.Components.MotionBackgroundDrawable
import org.telegram.ui.Components.Point
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.RadialProgress2
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble.ReactionButton
import org.telegram.ui.Components.Reactions.VisibleReaction
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RoundVideoPlayingDrawable
import org.telegram.ui.Components.SeekBar
import org.telegram.ui.Components.SeekBarAccessibilityDelegate
import org.telegram.ui.Components.SeekBarWaveform
import org.telegram.ui.Components.SlotsDrawable
import org.telegram.ui.Components.StaticLayoutEx
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.TimerParticles
import org.telegram.ui.Components.TranscribeButton
import org.telegram.ui.Components.TranscribeButton.LoadingPointsSpan
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.Components.URLSpanBotCommand
import org.telegram.ui.Components.URLSpanBrowser
import org.telegram.ui.Components.URLSpanMono
import org.telegram.ui.Components.URLSpanNoUnderline
import org.telegram.ui.Components.VideoForwardDrawable
import org.telegram.ui.Components.VideoForwardDrawable.VideoForwardDrawableDelegate
import org.telegram.ui.Components.spoilers.SpoilerEffect
import org.telegram.ui.PhotoViewer
import org.telegram.ui.SecretMediaViewer
import java.util.Arrays
import java.util.Locale
import java.util.Stack
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

open class ChatMessageCell(context: Context) : BaseCell(context), SeekBar.SeekBarDelegate, ImageReceiverDelegate, FileDownloadProgressListener, SelectableView, NotificationCenterDelegate {
	val reactionsLayoutInBubble = ReactionsLayoutInBubble(this)
	private val classGuid = ConnectionsManager.generateClassGuid()
	private val alphaPropertyWorkaround = Build.VERSION.SDK_INT == 28
	val transitionParams = TransitionParams()
	private val scrollRect = Rect()
	val radialProgress = RadialProgress2(this)
	private val videoRadialProgress = RadialProgress2(this)
	val photoImage = ImageReceiver(this)
	private val contactAvatarDrawable = AvatarDrawable()
	private val selectorDrawable = arrayOfNulls<Drawable>(2)
	private val selectorDrawableMaskType = IntArray(2)
	private val instantButtonRect = RectF()
	private val pressedState = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
	private val roundVideoPlayingDrawable = RoundVideoPlayingDrawable(this)
	private val deleteProgressRect = RectF()
	private val rect = RectF()
	private val links = LinkCollector(this)
	private val urlPathCache = ArrayList<LinkPath>()
	private val urlPathSelection = ArrayList<LinkPath>()
	private val rectPath = Path()
	val seekBar = SeekBar(this)
	val seekBarWaveform = SeekBarWaveform()
	private val seekBarAccessibilityDelegate: SeekBarAccessibilityDelegate
	val pollButtons = ArrayList<PollButton>()
	private val botButtons = ArrayList<BotButton>()
	private val botButtonsByData = HashMap<String, BotButton>()
	private val botButtonsByPosition = HashMap<String, BotButton>()
	private val tag: Int
	private val currentAccount = UserConfig.selectedAccount
	private val commentButtonRect = Rect()
	private val avatarImage = ImageReceiver()
	private val avatarDrawable = AvatarDrawable()
	private val locationImageReceiver = ImageReceiver(this)
	private val spoilersPatchedReplyTextLayout = AtomicReference<Layout>()
	private val forwardedNameLayout = arrayOfNulls<StaticLayout>(2)
	private val forwardNameOffsetX = FloatArray(2)
	private val unlockSpoilerEffect = SpoilerEffect()
	private val unlockSpoilerPath = Path()
	private val unlockSpoilerRadii = FloatArray(8)
	val backgroundDrawable = MessageBackgroundDrawable(this)
	private val accessibilityVirtualViewBounds = SparseArray<Rect>()
	private val backgroundCacheParams = PathDrawParams()
	private val replySpoilersPool = Stack<SpoilerEffect>()
	private val captionSpoilers: MutableList<SpoilerEffect> = ArrayList()
	private val captionSpoilersPool = Stack<SpoilerEffect>()
	private val captionPatchedSpoilersLayout = AtomicReference<Layout>()
	private val sPath = Path()
	var clipToGroupBounds = false
	var drawForBlur = false
	var shouldCheckVisibleOnScreen = false
	var isPinnedTop = false
	var isPinnedBottom = false
	var drawPinnedBottom = false
	var parentViewTopOffset = 0f

	@JvmField
	var isChat = false

	var isBot = false
	var isMegagroup = false
	var isThreadChat = false
	var hasDiscussion = false
	var isPinned = false
	var linkedChatId: Long = 0
	var isRepliesChat = false
	var isPinnedChat = false
	var replyNameLayout: StaticLayout? = null
	var replyTextLayout: StaticLayout? = null
	var replyImageReceiver: ImageReceiver
	var replyStartX = 0
	var replyStartY = 0
	var needReplyImage = false
	var animatedEmojiStack: EmojiGroupedSpans? = null
	var animatedEmojiReplyStack: EmojiGroupedSpans? = null
	var animatedEmojiDescriptionStack: EmojiGroupedSpans? = null
	var drawFromPinchToZoom = false

	// Public for enter transition
	var replySpoilers: MutableList<SpoilerEffect> = ArrayList()

	var isBlurred = false
	var parentBoundsTop = 0f
	var parentBoundsBottom = 0

	var enterTransitionInProgress = false
		set(value) {
			field = value
			invalidate()
		}

	var accessibilityText: CharSequence? = null
	var seekbarRoundX = 0f
	var seekbarRoundY = 0f
	var roundSeekbarTouched = 0
	var roundSeekbarOutProgress = 0f
	var roundSeekbarOutAlpha = 0f
	var videoForwardDrawable: VideoForwardDrawable? = null
	var videoPlayerRewinder: VideoPlayerRewinder? = null
	var imageReceiversAttachState = false
	var lastSize = 0
	var transitionYOffsetForDrawables = 0f
	private var flipImage = false
	private var visibleOnScreen = false
	private var drawPinnedTop = false

	var currentMessagesGroup: GroupedMessages? = null
		private set

	var currentPosition: GroupedMessagePosition? = null
		private set

	private var groupPhotoInvisible = false
	private var invalidateSpoilersParent = false

	var textX = 0
		private set

	private var unmovedTextX = 0

	var textY = 0
		private set

	private var totalHeight = 0
	private var additionalTimeOffsetY = 0
	private var keyboardHeight = 0
	private var linkBlockNum = 0
	private var linkSelectionBlockNum = 0
	private var inLayout = false
	private var currentMapProvider = MessagesController.MAP_PROVIDER_NONE
	private var lastVisibleBlockNum = 0
	private var firstVisibleBlockNum = 0
	private var totalVisibleBlocksCount = 0
	private var needNewVisiblePart = false
	private var fullyDraw = false
	private var parentWidth = 0
	private var parentHeight = 0
	private var attachedToWindow = false
	private var isUpdating = false
	private var drawRadialCheckBackground = false
	private var disallowLongPress = false
	private var lastTouchX = 0f
	private var lastTouchY = 0f
	private var drawMediaCheckBox = false
	private var drawSelectionBackground = false
	private var mediaCheckBox: CheckBoxBase? = null
	private var checkBox: CheckBoxBase? = null
	private var checkBoxVisible = false
	private var checkBoxAnimationInProgress = false
	private var checkBoxAnimationProgress = 0f
	private var lastCheckBoxAnimationTime: Long = 0
	private var checkBoxTranslation = 0
	private var isSmallImage = false
	private var drawImageButton = false
	private var drawVideoImageButton = false
	private var lastLoadingSizeTotal: Long = 0
	private var drawVideoSize = false
	private var canStreamVideo = false
	private var animatingDrawVideoImageButton = 0
	private var animatingDrawVideoImageButtonProgress = 0f
	private var animatingNoSoundPlaying = false
	private var animatingNoSound = 0
	private var animatingNoSoundProgress = 0f

	var noSoundIconCenterX = 0
		private set

	private var forwardNameCenterX = 0
	private var lastAnimationTime: Long = 0
	private var lastNamesAnimationTime: Long = 0
	private var documentAttachType = 0
	private var documentAttach: TLRPC.Document? = null
	private var drawPhotoImage = false
	private var hasLinkPreview = false
	private var hasOldCaptionPreview = false
	private var hasGamePreview = false
	private var hasInvoicePreview = false
	private var linkPreviewHeight = 0
	private var mediaOffsetY = 0
	private var descriptionY = 0
	private var durationWidth = 0
	private var photosCountWidth = 0
	private var descriptionX = 0
	private var titleX = 0
	private var authorX = 0
	private var siteNameRtl = false
	private var siteNameWidth = 0
	private var siteNameLayout: StaticLayout? = null
	private var titleLayout: StaticLayout? = null
	private var descriptionLayout: StaticLayout? = null
	private var videoInfoLayout: StaticLayout? = null
	private var photosCountLayout: StaticLayout? = null
	private var authorLayout: StaticLayout? = null
	private var instantViewLayout: StaticLayout? = null
	private var drawInstantView = false
	private var drawInstantViewType = 0
	private var imageBackgroundColor = 0
	private var imageBackgroundIntensity = 0f
	private var imageBackgroundGradientColor1 = 0
	private var imageBackgroundGradientColor2 = 0
	private var imageBackgroundGradientColor3 = 0
	private var imageBackgroundGradientRotation = 45
	private var gradientShader: LinearGradient? = null
	private var motionBackgroundDrawable: MotionBackgroundDrawable? = null
	private var imageBackgroundSideColor = 0
	private var imageBackgroundSideWidth = 0
	private var instantTextX = 0
	private var instantTextLeftX = 0
	private var instantWidth = 0
	private var instantTextNewLine = false
	private var instantPressed = false
	private var instantButtonPressed = false
	private var animatingLoadingProgressProgress = 0f
	private var docTitleLayout: StaticLayout? = null
	private var docTitleWidth = 0
	private var docTitleOffsetX = 0
	private var locationExpired = false

	var captionLayout: StaticLayout? = null
		private set

	private var currentCaption: CharSequence? = null
	private var captionOffsetX = 0

	var captionX = 0f
		private set

	private var captionY = 0f
	private var captionHeight = 0
	private var captionWidth = 0
	private var addedCaptionHeight = 0
	private var infoLayout: StaticLayout? = null
	private var loadingProgressLayout: StaticLayout? = null
	private var infoX = 0
	private var infoWidth = 0
	private var currentUrl: String? = null
	private var currentWebFile: WebFile? = null
	private var lastWebFile: WebFile? = null
	private var addedForTest = false
	private var hasEmbed = false
	private var wasSending = false
	private var checkOnlyButtonPressed = false
	private var buttonX = 0
	private var buttonY = 0
	private var videoButtonX = 0
	private var videoButtonY = 0
	private var buttonState = 0
	private var buttonPressed = 0
	private var videoButtonPressed = 0
	private var miniButtonPressed = 0
	private var otherX = 0
	private var otherY = 0
	private var lastWidth = 0
	private var lastHeight = 0
	private var hasMiniProgress = 0
	private var miniButtonState = 0
	private var imagePressed = false
	private var otherPressed = false
	private var photoNotSet = false
	private var photoParentObject: TLObject? = null
	private var currentPhotoObject: TLRPC.PhotoSize? = null
	private var currentPhotoObjectThumb: TLRPC.PhotoSize? = null
	private var currentPhotoObjectThumbStripped: BitmapDrawable? = null
	private var currentPhotoFilter: String? = null
	private var currentPhotoFilterThumb: String? = null
	private var timePressed = false
	var timeAlpha = 1.0f
	private var controlsAlpha = 1.0f
	private var lastControlsAlphaChangeTime: Long = 0
	private var totalChangeTime: Long = 0
	private var mediaWasInvisible = false
	private var timeWasInvisible = false
	private var pressedEmoji: AnimatedEmojiSpan? = null
	private var pressedLink: LinkSpanDrawable<CharacterStyle>? = null
	private var pressedLinkType = 0
	private var linkPreviewPressed = false
	private var gamePreviewPressed = false
	private var useSeekBarWaveform = false
	private var seekBarX = 0
	private var seekBarY = 0
	private var useTranscribeButton = false
	private var transcribeButton: TranscribeButton? = null
	private var transcribeX = 0f
	private var transcribeY = 0f
	private var durationLayout: StaticLayout? = null
	private var lastTime = 0
	private var timeWidthAudio = 0
	private var timeAudioX = 0
	private var songLayout: StaticLayout? = null
	private var songX = 0
	private var performerLayout: StaticLayout? = null
	private var performerX = 0
	private var pollAnimationProgress = 0f
	private var pollAnimationProgressTime = 0f
	private var pollVoted = false
	private var pollClosed = false
	private var lastPollCloseTime: Long = 0
	private var closeTimeText: String? = null
	private var closeTimeWidth = 0
	private var pollVoteInProgress = false
	private var vibrateOnPollVote = false
	private var pollUnvoteInProgress = false
	private var animatePollAnswer = false
	private var animatePollAvatars = false

	var isAnimatingPollAnswer = false
		private set

	private var pollVoteInProgressNum = 0
	private var voteLastUpdateTime: Long = 0
	private var voteRadOffset = 0f
	private var voteCurrentCircleLength = 0f
	private var firstCircleLength = false
	private var voteRisingCircleLength = false
	private var voteCurrentProgressTime = 0f
	private var pressedVoteButton = 0
	private var lastPoll: TLRPC.TLPoll? = null
	private var timerTransitionProgress = 0f
	private var lastPollResults: List<TLRPC.TLPollAnswerVoters>? = null
	private var lastPollResultsVoters = 0
	private var timerParticles: TimerParticles? = null
	private var pollHintX = 0
	private var pollHintY = 0
	private var pollHintPressed = false
	private var hintButtonVisible = false
	private var hintButtonProgress = 0f
	private var lastPostAuthor: String? = null
	private var lastReplyMessage: TLRPC.Message? = null
	private var hasPsaHint = false
	private var psaHelpX = 0
	private var psaHelpY = 0
	private var psaHintPressed = false
	private var psaButtonVisible = false
	private var psaButtonProgress = 0f
	private var autoPlayingMedia = false
	private var botButtonsLayout: String? = null
	private var widthForButtons = 0
	private var pressedBotButton = 0
	private var spoilerPressed: SpoilerEffect? = null
	private var isCaptionSpoilerPressed = false
	private var isSpoilerRevealing = false
	private var currentMessageObject: MessageObject? = null
	private var messageObjectToSet: MessageObject? = null
	private var groupedMessagesToSet: GroupedMessages? = null
	private var topNearToSet = false
	private var bottomNearToSet = false
	private var shakeAnimation: AnimatorSet? = null
	private var invalidatesParent = false
	private var wasPinned = false
	private var isPressed = false
	private var isHighlighted = false

	var isHighlightedAnimated = false
		private set

	private var highlightProgress = 0
	private var currentSelectedBackgroundAlpha = 0f
	private var lastHighlightProgressTime: Long = 0
	private var mediaBackground = false
	private var isCheckPressed = true
	private var wasLayout = false
	private var isAvatarVisible = false
	private var isThreadPost = false
	private var drawBackground = true
	private var substractBackgroundHeight = 0
	private var allowAssistant = false
	private var currentBackgroundDrawable: MessageDrawable? = null
	private var currentBackgroundSelectedDrawable: MessageDrawable? = null
	private var backgroundDrawableLeft = 0
	private var backgroundDrawableRight = 0
	private var backgroundDrawableTop = 0
	private var backgroundDrawableBottom = 0
	private var viaWidth = 0
	private var viaNameWidth = 0
	private var viaSpan1: TypefaceSpan? = null
	private var viaSpan2: TypefaceSpan? = null
	private var availableTimeWidth = 0
	private var widthBeforeNewTimeLine = 0
	private var backgroundWidth = 100
	private var hasNewLineForTime = false
	private var layoutWidth = 0

	var layoutHeight = 0
		private set

	private var pollAvatarImages: Array<ImageReceiver?>? = null
	private var pollAvatarDrawables: Array<AvatarDrawable?>? = null
	private var pollAvatarImagesVisible: BooleanArray? = null
	private var pollCheckBox: Array<CheckBoxBase?>? = null
	private var commentProgress: InfiniteProgress? = null
	private var commentProgressAlpha = 0f
	private var commentProgressLastUpdateTime: Long = 0
	private var commentAvatarImages: Array<ImageReceiver?>? = null
	private var commentAvatarDrawables: Array<AvatarDrawable?>? = null
	private var commentAvatarImagesVisible: BooleanArray? = null
	private var commentLayout: StaticLayout? = null
	private var commentNumberLayout: AnimatedNumberLayout? = null
	private var drawCommentNumber = false
	private var commentArrowX = 0
	private var commentUnreadX = 0
	private var commentDrawUnread = false
	private var commentWidth = 0
	private var commentX = 0
	private var totalCommentWidth = 0
	private var commentNumberWidth = 0
	private var drawCommentButton = false
	private var commentButtonPressed = false
	private var avatarPressed = false
	private var forwardNamePressed = false
	private var forwardBotPressed = false
	private var replyNameWidth = 0
	private var replyNameOffset = 0
	private var replyTextWidth = 0
	private var replyTextOffset = 0
	private var replyPressed = false
	private var currentReplyPhoto: TLRPC.PhotoSize? = null
	private var drawSideButton = 0
	private var sideButtonPressed = false
	private var sideStartX = 0f
	private var sideStartY = 0f
	private var nameLayout: StaticLayout? = null
	private var nameLayoutWidth = 0
	private var adminLayout: StaticLayout? = null
	private var nameWidth = 0
	private var nameOffsetX = 0f
	private var nameX = 0f
	private var nameY = 0f
	private var drawName = false
	private var drawNameLayout = false
	private var forwardedNameWidth = 0
	private var drawForwardedName = false
	private var forwardNameX = 0f
	private var forwardNameY = 0
	private var drawTimeX = 0f
	private var drawTimeY = 0f
	private var timeLayout: StaticLayout? = null
	private var timeWidth = 0
	private var timeTextWidth = 0
	private var timeX = 0
	private var currentTimeString: String? = null
	private var drawTime = true
	private var forceNotDrawTime = false
	private var unlockX = 0f
	private var unlockY = 0f
	private var unlockLayout: StaticLayout? = null
	private var unlockTextWidth = 0
	private var currentUnlockString: String? = null
	private var viewsLayout: StaticLayout? = null
	private var viewsTextWidth = 0
	private var currentViewsString: String? = null
	private var repliesLayout: StaticLayout? = null
	private var repliesTextWidth = 0
	private var currentRepliesString: String? = null

	var currentUser: User? = null
		private set

	private var currentChat: TLRPC.Chat? = null
	private var currentPhoto: TLRPC.FileLocation? = null
	private var currentNameString: String? = null
	private var currentNameStatus: Any? = null
	private var currentNameStatusDrawable: SwapAnimatedEmojiDrawable? = null
	private var currentForwardUser: User? = null
	private var currentViaBotUser: User? = null
	private var currentForwardChannel: TLRPC.Chat? = null
	private var currentForwardName: String? = null
	private var currentForwardNameString: String? = null
	private var replyPanelIsForward = false
	private var animationRunning = false
	private var willRemoved = false
	var delegate: ChatMessageCellDelegate? = null

	private val diceFinishCallback = Runnable {
		delegate?.onDiceFinished()
	}

	private var namesOffset = 0
	private var lastSendState = 0
	private var lastDeleteDate = 0
	private var lastViewsCount = 0
	private var lastRepliesCount = 0
	private var selectedBackgroundProgress = 0f

	var viewTop = 0f
		private set

	var backgroundHeight = 0
		private set

	private var blurredViewTopOffset = 0
	private var blurredViewBottomOffset = 0
	private var scheduledInvalidate = false
	private var alphaInternal = 1f
	private var edited = false
	private var imageDrawn = false
	private var photoImageOutOfBounds = false
	private var unregisterFlagSecure: Runnable? = null
	private var animateToStatusDrawableParams = 0
	private var animateFromStatusDrawableParams = 0
	private var statusDrawableProgress = 0f
	private var statusDrawableAnimationInProgress = false
	private var statusDrawableAnimator: ValueAnimator? = null
	private var overrideShouldDrawTimeOnMedia = 0
	private var toSeekBarProgress = 0f
	private var isRoundVideo = false
	private var isPlayingRound = false
	private var roundProgressAlpha = 0f
	private var roundToPauseProgress = 0f
	private var roundToPauseProgress2 = 0f
	private var roundPlayingDrawableProgress = 0f
	private var lastSeekUpdateTime: Long = 0
	private var lastDrawingAudioProgress = 0f
	private var currentFocusedVirtualView = -1
	private var hadLongPress = false
	private var selectionOverlayPaint: Paint? = null

	private val botButtonDefaultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = context.getColor(R.color.dirty_blue_light)
	}
	private val botButtonSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = context.getColor(R.color.dirty_blue_dark)
	}

	private val botButtonTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		typeface = Theme.TYPEFACE_BOLD
		textSize = 15.dp.toFloat()
		color = context.getColor(R.color.dark)
	}

	var slidingOffsetX = 0f
		private set

	private var animationOffsetX = 0f

	@JvmField
	var ANIMATION_OFFSET_X = object : Property<ChatMessageCell, Float>(Float::class.java, "animationOffsetX") {
		override operator fun get(`object`: ChatMessageCell): Float {
			return `object`.animationOffsetX
		}

		override operator fun set(`object`: ChatMessageCell, value: Float) {
			`object`.setAnimationOffsetX(value)
		}
	}

	fun getReactionButton(visibleReaction: VisibleReaction): ReactionButton? {
		return reactionsLayoutInBubble.getReactionButton(visibleReaction)
	}

	// primary message for group
	// contains caption, reactions etc for all group
	val primaryMessageObject: MessageObject?
		get() {
			var messageObject: MessageObject? = null

			if (currentMessageObject != null && currentMessagesGroup != null && currentMessageObject?.hasValidGroupId() == true) {
				messageObject = currentMessagesGroup?.findPrimaryMessageObject()
			}

			return messageObject ?: currentMessageObject
		}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.startSpoilers -> {
				setSpoilersSuppressed(false)
			}

			NotificationCenter.stopSpoilers -> {
				setSpoilersSuppressed(true)
			}

			NotificationCenter.userInfoDidLoad -> {
				if (currentUser != null) {
					val uid = args[0] as Long

					if (currentUser?.id == uid) {
						setAvatar(currentMessageObject)
					}
					else {
						val messageObject = currentMessageObject
						currentMessageObject = null
						setMessageObject(messageObject, currentMessagesGroup, isPinnedBottom, isPinnedTop)
					}
				}
			}
		}
	}

	private fun setAvatar(messageObject: MessageObject?) {
		if (messageObject == null) {
			return
		}

		if (isAvatarVisible) {
			if (messageObject.customAvatarDrawable != null) {
				avatarImage.setImageBitmap(messageObject.customAvatarDrawable)
			}
			else if (currentUser != null) {
				currentPhoto = currentUser?.photo?.photoSmall
				avatarDrawable.setInfo(currentUser)
				avatarImage.setForUserOrChat(currentUser, avatarDrawable, null, true)
			}
			else if (currentChat != null) {
				currentPhoto = currentChat?.photo?.photoSmall
				avatarDrawable.setInfo(currentChat)
				avatarImage.setForUserOrChat(currentChat, avatarDrawable)
			}
			else if (messageObject.isSponsored) {
				if (messageObject.sponsoredChatInvite?.chat != null) {
					avatarDrawable.setInfo(messageObject.sponsoredChatInvite!!.chat)
					avatarImage.setForUserOrChat(messageObject.sponsoredChatInvite!!.chat, avatarDrawable)
				}
				else {
					avatarDrawable.setInfo(messageObject.sponsoredChatInvite)

					val photo = messageObject.sponsoredChatInvite?.photo

					if (photo != null) {
						avatarImage.setImage(ImageLocation.getForPhoto(photo.sizes?.firstOrNull(), photo), "50_50", avatarDrawable, null, null, 0)
					}
				}
			}
			else {
				currentPhoto = null
				avatarDrawable.setInfo(null, null)
				avatarImage.setImage(null, null, avatarDrawable, null, null, 0)
			}
		}
		else {
			currentPhoto = null
		}
	}

	fun setSpoilersSuppressed(s: Boolean) {
		for (eff in captionSpoilers) {
			eff.setSuppressUpdates(s)
		}

		for (eff in replySpoilers) {
			eff.setSuppressUpdates(s)
		}

		getMessageObject()?.textLayoutBlocks?.forEach { bl ->
			for (eff in bl.spoilers) {
				eff.setSuppressUpdates(s)
			}
		}
	}

	fun hasSpoilers(): Boolean {
		if (hasCaptionLayout() && captionSpoilers.isNotEmpty() || replyTextLayout != null && replySpoilers.isNotEmpty()) {
			return true
		}

		getMessageObject()?.textLayoutBlocks?.forEach { bl ->
			if (bl.spoilers.isNotEmpty()) {
				return true
			}
		}

		return false
	}

	private fun updateSpoilersVisiblePart(top: Int, bottom: Int) {
		if (hasCaptionLayout()) {
			val off = -captionY

			for (eff in captionSpoilers) {
				eff.setVisibleBounds(0f, top + off, width.toFloat(), bottom + off)
			}
		}

		replyTextLayout?.let {
			val off = (-replyStartY - it.height).toFloat()

			for (eff in replySpoilers) {
				eff.setVisibleBounds(0f, top + off, width.toFloat(), bottom + off)
			}
		}

		getMessageObject()?.textLayoutBlocks?.forEach { bl ->
			for (eff in bl.spoilers) {
				eff.setVisibleBounds(0f, top - bl.textYOffset - textY, width.toFloat(), bottom - bl.textYOffset - textY)
			}
		}
	}

	fun setScrimReaction(scrimViewReaction: String?) {
		reactionsLayoutInBubble.setScrimReaction(scrimViewReaction)
	}

	fun drawScrimReaction(canvas: Canvas, scrimViewReaction: String?) {
		if ((currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0) && currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0 && !reactionsLayoutInBubble.isSmall) {
			reactionsLayoutInBubble.draw(canvas, transitionParams.animateChangeProgress, scrimViewReaction)
		}
	}

	fun checkUnreadReactions(clipTop: Float, clipBottom: Int): Boolean {
		if (!reactionsLayoutInBubble.hasUnreadReactions) {
			return false
		}

		val y = y + reactionsLayoutInBubble.y
		return y > clipTop && y + reactionsLayoutInBubble.height - AndroidUtilities.dp(16f) < clipBottom
	}

	fun markReactionsAsRead() {
		reactionsLayoutInBubble.hasUnreadReactions = false
		currentMessageObject?.markReactionsAsRead()
	}

	fun setVisibleOnScreen(visibleOnScreen: Boolean) {
		if (this.visibleOnScreen != visibleOnScreen) {
			this.visibleOnScreen = visibleOnScreen
			checkImageReceiversAttachState()
		}
	}

	fun setParentBounds(chatListViewPaddingTop: Float, blurredViewBottomOffset: Int) {
		parentBoundsTop = chatListViewPaddingTop
		parentBoundsBottom = blurredViewBottomOffset

		if (photoImageOutOfBounds) {
			val top = y + photoImage.imageY
			val bottom = top + photoImage.imageHeight

			if (bottom >= parentBoundsTop && top <= parentBoundsBottom) {
				invalidate()
			}
		}
	}

	private fun createPollUI() {
		if (pollAvatarImages != null) {
			return
		}

		pollAvatarImages = arrayOfNulls(3)
		pollAvatarDrawables = arrayOfNulls(3)
		pollAvatarImagesVisible = BooleanArray(3)

		for (a in pollAvatarImages!!.indices) {
			pollAvatarImages!![a] = ImageReceiver(this)
			pollAvatarImages!![a]?.setRoundRadius(AndroidUtilities.dp(8f))

			pollAvatarDrawables!![a] = AvatarDrawable()
			pollAvatarDrawables!![a]?.setTextSize(AndroidUtilities.dp(6f))
		}

		pollCheckBox = arrayOfNulls(10)

		for (a in pollCheckBox!!.indices) {
			pollCheckBox!![a] = CheckBoxBase(this, 20)
			pollCheckBox!![a]?.setDrawUnchecked(false)
			pollCheckBox!![a]?.setBackgroundType(9)
		}
	}

	private fun createCommentUI() {
		if (commentAvatarImages != null) {
			return
		}

		commentAvatarImages = arrayOfNulls(3)
		commentAvatarDrawables = arrayOfNulls(3)
		commentAvatarImagesVisible = BooleanArray(3)

		for (a in commentAvatarImages!!.indices) {
			commentAvatarImages!![a] = ImageReceiver(this)
			commentAvatarImages!![a]?.setRoundRadius(AndroidUtilities.dp(12f))

			commentAvatarDrawables!![a] = AvatarDrawable()
			commentAvatarDrawables!![a]?.setTextSize(AndroidUtilities.dp(8f))
		}
	}

	fun resetPressedLink(type: Int) {
		if (type != -1) {
			links.removeLinks(type)
		}
		else {
			links.clear()
		}

		pressedEmoji = null

		if (pressedLink == null || pressedLinkType != type && type != -1) {
			return
		}

		pressedLink = null
		pressedLinkType = -1

		invalidate()
	}

	private fun resetUrlPaths() {
		if (urlPathSelection.isEmpty()) {
			return
		}

		urlPathCache.addAll(urlPathSelection)
		urlPathSelection.clear()
	}

	private fun obtainNewUrlPath(): LinkPath {
		val linkPath: LinkPath

		if (urlPathCache.isNotEmpty()) {
			linkPath = urlPathCache[0]
			urlPathCache.removeAt(0)
		}
		else {
			linkPath = LinkPath(true)
		}

		linkPath.reset()

		urlPathSelection.add(linkPath)

		return linkPath
	}

	private fun getRealSpanStartAndEnd(buffer: Spannable, link: CharacterStyle): IntArray {
		var start = 0
		var end = 0
		var ok = false

		if (link is URLSpanBrowser) {
			link.style?.urlEntity?.let {
				start = it.offset
				end = it.offset + it.length
				ok = true
			}
		}

		if (!ok) {
			start = buffer.getSpanStart(link)
			end = buffer.getSpanEnd(link)
		}

		return intArrayOf(start, end)
	}

	private fun checkTextBlockMotionEvent(event: MotionEvent): Boolean {
		val currentMessageObject = currentMessageObject ?: return false

		if (!(currentMessageObject.type == MessageObject.TYPE_COMMON || currentMessageObject.type == MessageObject.TYPE_EMOJIS) || currentMessageObject.textLayoutBlocks == null || currentMessageObject.textLayoutBlocks.isNullOrEmpty() || currentMessageObject.messageText !is Spannable) {
			return false
		}

		if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP && pressedLinkType == 1) {
			var x = event.x.toInt()
			var y = event.y.toInt()

			if (x >= textX && y >= textY && x <= textX + currentMessageObject.textWidth && y <= textY + currentMessageObject.textHeight) {
				y -= textY
				var blockNum = 0
				for (a in currentMessageObject.textLayoutBlocks!!.indices) {
					if (currentMessageObject.textLayoutBlocks!![a].textYOffset > y) {
						break
					}
					blockNum = a
				}
				try {
					val block = currentMessageObject.textLayoutBlocks!![blockNum]

					x -= (textX - if (block.isRtl) currentMessageObject.textXOffset else 0f).toInt()
					y -= block.textYOffset.toInt()

					val line = block.textLayout?.getLineForVertical(y) ?: 0
					val off = block.charactersOffset + (block.textLayout?.getOffsetForHorizontal(line, x.toFloat()) ?: 0)
					val left = block.textLayout?.getLineLeft(line) ?: 0f

					if (left <= x && left + (block.textLayout?.getLineWidth(line) ?: 0f) >= x) {
						val buffer = currentMessageObject.messageText as Spannable
						var link: Array<out CharacterStyle>? = buffer.getSpans(off, off, ClickableSpan::class.java)
						var isMono = false

						if (link.isNullOrEmpty()) {
							link = buffer.getSpans(off, off, URLSpanMono::class.java)
							isMono = true
						}

						if (link.isNullOrEmpty()) {
							link = buffer.getSpans(off, off, AnimatedEmojiSpan::class.java)
							isMono = false
						}

						val ignore = link.isNullOrEmpty() || link[0] is URLSpanBotCommand && !URLSpanBotCommand.enabled

						if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
							if (event.action == MotionEvent.ACTION_DOWN) {
								if (link!![0] is AnimatedEmojiSpan) {
									if (pressedEmoji == null || pressedEmoji !== link[0]) {
										resetPressedLink(1)
										pressedEmoji = link[0] as AnimatedEmojiSpan
										pressedLinkType = 1
									}
								}
								else if (pressedLink == null || pressedLink?.span !== link[0]) {
									links.removeLink(pressedLink)

									pressedLink = LinkSpanDrawable(link[0], x.toFloat(), y.toFloat(), spanSupportsLongPress(link[0]))
									pressedLink?.setColor(ResourcesCompat.getColor(resources, R.color.selected_link_background, null))

									linkBlockNum = blockNum
									pressedLinkType = 1

									try {
										var path = pressedLink?.obtainNewPath()

										val pos = getRealSpanStartAndEnd(buffer, pressedLink!!.span)
										pos[0] -= block.charactersOffset
										pos[1] -= block.charactersOffset

										path?.setCurrentLayout(block.textLayout, pos[0], 0f)

										block.textLayout?.getSelectionPath(pos[0], pos[1], path)

										if (pos[1] >= block.charactersEnd) {
											for (a in blockNum + 1 until currentMessageObject.textLayoutBlocks!!.size) {
												val nextBlock = currentMessageObject.textLayoutBlocks!![a]

												val nextLink = if (isMono) {
													buffer.getSpans(nextBlock.charactersOffset, nextBlock.charactersOffset, URLSpanMono::class.java)
												}
												else {
													buffer.getSpans(nextBlock.charactersOffset, nextBlock.charactersOffset, ClickableSpan::class.java)
												}

												if (nextLink.isNullOrEmpty() || nextLink[0] !== pressedLink!!.span) {
													break
												}

												path = pressedLink!!.obtainNewPath()
												path.setCurrentLayout(nextBlock.textLayout, 0, nextBlock.textYOffset - block.textYOffset)

												val p1 = pos[1] + block.charactersOffset - nextBlock.charactersOffset

												nextBlock.textLayout?.getSelectionPath(0, p1, path)

												if (p1 < nextBlock.charactersEnd - 1) {
													break
												}
											}
										}

										if (pos[0] <= block.charactersOffset) {
											var offsetY = 0

											for (a in blockNum - 1 downTo 0) {
												val nextBlock = currentMessageObject.textLayoutBlocks!![a]

												val nextLink = if (isMono) {
													buffer.getSpans(nextBlock.charactersEnd - 1, nextBlock.charactersEnd - 1, URLSpanMono::class.java)
												}
												else {
													buffer.getSpans(nextBlock.charactersEnd - 1, nextBlock.charactersEnd - 1, ClickableSpan::class.java)
												}

												if (nextLink.isNullOrEmpty() || nextLink[0] !== pressedLink?.span) {
													break
												}

												path = pressedLink?.obtainNewPath()
												offsetY -= nextBlock.height

												val p0 = pos[0] + block.charactersOffset - nextBlock.charactersOffset
												val p1 = pos[1] + block.charactersOffset - nextBlock.charactersOffset

												path?.setCurrentLayout(nextBlock.textLayout, p0, offsetY.toFloat())

												nextBlock.textLayout?.getSelectionPath(p0, p1, path)

												if (p0 > nextBlock.charactersOffset) {
													break
												}
											}
										}
									}
									catch (e: Exception) {
										FileLog.e(e)
									}

									links.addLink(pressedLink, 1)
								}

								invalidate()

								return true
							}
							else {
								if (link!![0] is AnimatedEmojiSpan && pressedEmoji === link[0]) {
									if (delegate?.didPressAnimatedEmoji(pressedEmoji) == true) {
										resetPressedLink(1)
										pressedEmoji = null
										return true
									}

									resetPressedLink(1)

									pressedEmoji = null
								}
								else if (link[0] === pressedLink?.span) {
									delegate?.didPressUrl(this, pressedLink!!.span, false)
									resetPressedLink(1)
									return true
								}
							}
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
			else {
				resetPressedLink(1)
			}
		}

		return false
	}

	private fun checkCaptionMotionEvent(event: MotionEvent): Boolean {
		val captionLayout = captionLayout ?: return false
		val currentCaption = currentCaption ?: return false

		if (currentCaption !is Spannable) {
			return false
		}

		if (event.action == MotionEvent.ACTION_DOWN || linkPreviewPressed || pressedLink != null || pressedEmoji != null && event.action == MotionEvent.ACTION_UP) {
			var x = event.x.toInt()
			var y = event.y.toInt()

			if (x >= captionX && x <= captionX + captionWidth && y >= captionY && y <= captionY + captionHeight) {
				if (event.action == MotionEvent.ACTION_DOWN) {
					try {
						x -= captionX.toInt()
						y -= captionY.toInt()

						val line = captionLayout.getLineForVertical(y)
						val off = captionLayout.getOffsetForHorizontal(line, x.toFloat())
						val left = captionLayout.getLineLeft(line)

						if (left <= x && left + captionLayout.getLineWidth(line) >= x) {
							val buffer = currentCaption
							var link: Array<out CharacterStyle>? = buffer.getSpans(off, off, ClickableSpan::class.java)

							if (link.isNullOrEmpty()) {
								link = buffer.getSpans(off, off, URLSpanMono::class.java)
							}

							if (link.isNullOrEmpty()) {
								link = buffer.getSpans(off, off, AnimatedEmojiSpan::class.java)
							}

							val ignore = link.isNullOrEmpty() || link[0] is URLSpanBotCommand && !URLSpanBotCommand.enabled

							if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
								if (link!![0] is AnimatedEmojiSpan) {
									resetPressedLink(3)
									pressedLinkType = 3
									pressedEmoji = link[0] as AnimatedEmojiSpan
								}
								else if (pressedLink == null || pressedLink?.span !== link[0]) {
									links.removeLink(pressedLink)

									pressedLink = LinkSpanDrawable(link[0], x.toFloat(), y.toFloat(), spanSupportsLongPress(link[0]))
									pressedLink?.setColor(ResourcesCompat.getColor(resources, R.color.selected_link_background, null))

									pressedLinkType = 3

									try {
										val path = pressedLink?.obtainNewPath()
										val pos = getRealSpanStartAndEnd(buffer, pressedLink!!.span)
										path?.setCurrentLayout(captionLayout, pos[0], 0f)
										captionLayout.getSelectionPath(pos[0], pos[1], path)
									}
									catch (e: Exception) {
										FileLog.e(e)
									}

									links.addLink(pressedLink, 3)
								}

								invalidateWithParent()

								return true
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				else if (pressedLinkType == 3) {
					return if (pressedEmoji != null) {
						if (delegate?.didPressAnimatedEmoji(pressedEmoji) == true) {
							resetPressedLink(3)
							pressedEmoji = null
							return true
						}

						resetPressedLink(3)

						pressedEmoji = null

						false
					}
					else {
						delegate?.didPressUrl(this, pressedLink!!.span, false)
						resetPressedLink(3)
						true
					}
				}
			}
			else {
				resetPressedLink(3)
			}
		}

		return false
	}

	private fun checkGameMotionEvent(event: MotionEvent): Boolean {
		if (!hasGamePreview) {
			return false
		}

		var x = event.x.toInt()
		var y = event.y.toInt()

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (drawPhotoImage && drawImageButton && buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48f) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48f) && radialProgress.icon != MediaActionDrawable.ICON_NONE) {
				buttonPressed = 1
				invalidate()
				return true
			}
			else if (drawPhotoImage && photoImage.isInsideImage(x.toFloat(), y.toFloat())) {
				gamePreviewPressed = true
				return true
			}
			else if (descriptionLayout != null && y >= descriptionY) {
				try {
					x -= unmovedTextX + AndroidUtilities.dp(10f) + descriptionX
					y -= descriptionY

					val line = descriptionLayout!!.getLineForVertical(y)
					val off = descriptionLayout!!.getOffsetForHorizontal(line, x.toFloat())
					val left = descriptionLayout!!.getLineLeft(line)

					if (left <= x && left + descriptionLayout!!.getLineWidth(line) >= x) {
						val buffer = currentMessageObject!!.linkDescription as Spannable
						val link = buffer.getSpans(off, off, ClickableSpan::class.java)
						val ignore = link.isEmpty() || link[0] is URLSpanBotCommand && !URLSpanBotCommand.enabled

						if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
							if (pressedLink == null || pressedLink!!.span !== link[0]) {
								links.removeLink(pressedLink)

								pressedLink = LinkSpanDrawable(link[0], x.toFloat(), y.toFloat(), spanSupportsLongPress(link[0]))
								pressedLink?.setColor(ResourcesCompat.getColor(resources, R.color.selected_link_background, null))

								linkBlockNum = -10
								pressedLinkType = 2

								try {
									val path = pressedLink?.obtainNewPath()
									val pos = getRealSpanStartAndEnd(buffer, pressedLink!!.span)
									path?.setCurrentLayout(descriptionLayout, pos[0], 0f)
									descriptionLayout?.getSelectionPath(pos[0], pos[1], path)
								}
								catch (e: Exception) {
									FileLog.e(e)
								}

								links.addLink(pressedLink, 2)
							}

							invalidate()

							return true
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
		else if (event.action == MotionEvent.ACTION_UP) {
			if (pressedLinkType == 2 || gamePreviewPressed || buttonPressed != 0) {
				if (buttonPressed != 0) {
					buttonPressed = 0
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressButton(animated = true, video = false)
					invalidate()
				}
				else if (pressedLink != null) {
					when (val span = pressedLink?.span) {
						is URLSpan -> {
							Browser.openUrl(context, span.url)
						}

						is ClickableSpan -> {
							span.onClick(this)
						}
					}

					resetPressedLink(2)
				}
				else {
					gamePreviewPressed = false

					for (a in botButtons.indices) {
						val button = botButtons[a]

						if (button.button is TLRPC.TLKeyboardButtonGame) {
							playSoundEffect(SoundEffectConstants.CLICK)
							delegate?.didPressBotButton(this, button.button)
							invalidate()
							break
						}
					}

					resetPressedLink(2)

					return true
				}
			}
			else {
				resetPressedLink(2)
			}
		}

		return false
	}

	private val invalidateRunnable = object : Runnable {
		override fun run() {
			checkLocationExpired()

			if (locationExpired) {
				invalidate()
				scheduledInvalidate = false
			}
			else {
				invalidate()
				// invalidate(rect.left.toInt() - 5, rect.top.toInt() - 5, rect.right.toInt() + 5, rect.bottom.toInt() + 5)

				if (scheduledInvalidate) {
					AndroidUtilities.runOnUIThread(this, 1000)
				}
			}
		}
	}

	init {
		avatarImage.setAllowLoadingOnAttachedOnly(true)
		avatarImage.setRoundRadius(AndroidUtilities.dp(19f))

		replyImageReceiver = ImageReceiver(this)
		replyImageReceiver.setAllowLoadingOnAttachedOnly(true)
		replyImageReceiver.setRoundRadius(AndroidUtilities.dp(2f))

		locationImageReceiver.setAllowLoadingOnAttachedOnly(true)
		locationImageReceiver.setRoundRadius(AndroidUtilities.dp(26.1f))

		tag = DownloadController.getInstance(currentAccount).generateObserverTag()

		photoImage.setAllowLoadingOnAttachedOnly(true)
		photoImage.setUseRoundForThumbDrawable(true)
		photoImage.setDelegate(this)

		radialProgress.setCircleRadius(AndroidUtilities.dp(21f))
		radialProgress.drawAsRoundedRect = true
		radialProgress.cornerRadius = AndroidUtilities.dp(17f)

		videoRadialProgress.setDrawBackground(false)
		videoRadialProgress.setCircleRadius(AndroidUtilities.dp(15f))

		seekBar.setDelegate(this)

		seekBarWaveform.setDelegate(this)
		seekBarWaveform.setParentView(this)

		seekBarAccessibilityDelegate = object : FloatSeekBarAccessibilityDelegate() {
			public override fun getProgress(): Float {
				return if (currentMessageObject?.isMusic == true) {
					seekBar.progress
				}
				else if (currentMessageObject?.isVoice == true) {
					if (useSeekBarWaveform) {
						seekBarWaveform.getProgress()
					}
					else {
						seekBar.progress
					}
				}
				else if (currentMessageObject?.isRoundVideo == true) {
					currentMessageObject?.audioProgress ?: 0f
				}
				else {
					0f
				}
			}

			public override fun setProgress(progress: Float) {
				if (currentMessageObject?.isMusic == true) {
					seekBar.progress = progress
				}
				else if (currentMessageObject?.isVoice == true) {
					if (useSeekBarWaveform) {
						seekBarWaveform.setProgress(progress)
					}
					else {
						seekBar.progress = progress
					}
				}
				else if (currentMessageObject?.isRoundVideo == true) {
					currentMessageObject?.audioProgress = progress
				}
				else {
					return
				}

				onSeekBarDrag(progress)
				invalidate()
			}
		}

		Theme.chat_audioTimePaint.typeface = Theme.TYPEFACE_BOLD

		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
	}

	private fun checkTranscribeButtonMotionEvent(event: MotionEvent): Boolean {
		return useTranscribeButton && (transcribeButton?.onTouch(event.action, event.x - transcribeX, event.y - transcribeY) == true)
	}

	private fun checkLinkPreviewMotionEvent(event: MotionEvent): Boolean {
		if (currentMessageObject?.type != MessageObject.TYPE_COMMON || !hasLinkPreview) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()

		if (x >= unmovedTextX && x <= unmovedTextX + backgroundWidth && y >= textY + currentMessageObject!!.textHeight && y <= textY + currentMessageObject!!.textHeight + linkPreviewHeight + AndroidUtilities.dp((8 + if (drawInstantView) 46 else 0).toFloat())) {
			if (event.action == MotionEvent.ACTION_DOWN) {
				if (descriptionLayout != null && y >= descriptionY) {
					try {
						val checkX = x - (unmovedTextX + AndroidUtilities.dp(10f) + descriptionX)
						val checkY = y - descriptionY

						if (checkY <= descriptionLayout!!.height) {
							val line = descriptionLayout!!.getLineForVertical(checkY)
							val off = descriptionLayout!!.getOffsetForHorizontal(line, checkX.toFloat())
							val left = descriptionLayout!!.getLineLeft(line)

							if (left <= checkX && left + descriptionLayout!!.getLineWidth(line) >= checkX) {
								val buffer = currentMessageObject!!.linkDescription as Spannable
								val link = buffer.getSpans(off, off, ClickableSpan::class.java)
								val ignore = link.isNullOrEmpty() || link[0] is URLSpanBotCommand && !URLSpanBotCommand.enabled

								if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
									if (pressedLink == null || pressedLink?.span !== link[0]) {
										links.removeLink(pressedLink)

										pressedLink = LinkSpanDrawable(link[0], x.toFloat(), y.toFloat(), spanSupportsLongPress(link[0]))
										pressedLink?.setColor(ResourcesCompat.getColor(resources, R.color.selected_link_background, null))

										linkBlockNum = -10
										pressedLinkType = 2

										startCheckLongPress()

										try {
											val path = pressedLink?.obtainNewPath()
											val pos = getRealSpanStartAndEnd(buffer, pressedLink!!.span)
											path?.setCurrentLayout(descriptionLayout, pos[0], 0f)
											descriptionLayout?.getSelectionPath(pos[0], pos[1], path)
										}
										catch (e: Exception) {
											FileLog.e(e)
										}

										links.addLink(pressedLink, 2)
									}

									invalidate()

									return true
								}
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				if (pressedLink == null) {
					val side = AndroidUtilities.dp(48f)
					var area2 = false

					if (miniButtonState >= 0) {
						val offset = AndroidUtilities.dp(27f)
						area2 = x >= buttonX + offset && x <= buttonX + offset + side && y >= buttonY + offset && y <= buttonY + offset + side
					}

					if (area2) {
						miniButtonPressed = 1
						invalidate()
						return true
					}
					else if (drawVideoImageButton && buttonState != -1 && x >= videoButtonX && x <= videoButtonX + AndroidUtilities.dp((26 + 8).toFloat()) + max(infoWidth, docTitleWidth) && y >= videoButtonY && y <= videoButtonY + AndroidUtilities.dp(30f)) {
						videoButtonPressed = 1
						invalidate()
						return true
					}
					else if (drawPhotoImage && drawImageButton && buttonState != -1 && (!checkOnlyButtonPressed && photoImage.isInsideImage(x.toFloat(), y.toFloat()) || x >= buttonX && x <= buttonX + AndroidUtilities.dp(48f) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48f) && radialProgress.icon != MediaActionDrawable.ICON_NONE)) {
						buttonPressed = 1
						invalidate()
						return true
					}
					else if (drawInstantView) {
						instantPressed = true
						selectorDrawableMaskType[0] = 0

						selectorDrawable[0]?.let {
							if (it.bounds.contains(x, y)) {
								it.setHotspot(x.toFloat(), y.toFloat())
								it.state = pressedState
								instantButtonPressed = true
							}
						}

						invalidate()

						return true
					}
					else if (documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && drawPhotoImage && photoImage.isInsideImage(x.toFloat(), y.toFloat())) {
						linkPreviewPressed = true

						val webPage = MessageObject.getMedia(currentMessageObject?.messageOwner)?.webpage

						if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && buttonState == -1 && SharedConfig.autoplayGifs && (photoImage.animation == null || !webPage?.embedUrl.isNullOrEmpty())) {
							linkPreviewPressed = false
							return false
						}

						return true
					}
				}
			}
			else if (event.action == MotionEvent.ACTION_UP) {
				if (instantPressed) {
					delegate?.didPressInstantButton(this, drawInstantViewType)

					playSoundEffect(SoundEffectConstants.CLICK)

					selectorDrawable[0]?.state = StateSet.NOTHING

					instantButtonPressed = false
					instantPressed = false

					invalidate()
				}
				else if (pressedLinkType == 2 || buttonPressed != 0 || miniButtonPressed != 0 || videoButtonPressed != 0 || linkPreviewPressed) {
					if (videoButtonPressed == 1) {
						videoButtonPressed = 0
						playSoundEffect(SoundEffectConstants.CLICK)
						didPressButton(animated = true, video = true)
						invalidate()
					}
					else if (buttonPressed != 0) {
						buttonPressed = 0

						playSoundEffect(SoundEffectConstants.CLICK)

						if (drawVideoImageButton) {
							didClickedImage()
						}
						else {
							didPressButton(animated = true, video = false)
						}

						invalidate()
					}
					else if (miniButtonPressed != 0) {
						miniButtonPressed = 0
						playSoundEffect(SoundEffectConstants.CLICK)
						didPressMiniButton()
						invalidate()
					}
					else if (pressedLink != null) {
						when (val span = pressedLink?.span) {
							is URLSpan -> delegate?.didPressUrl(this, span, false)
							is ClickableSpan -> span.onClick(this)
						}

						resetPressedLink(2)
					}
					else if (pressedEmoji != null && delegate?.didPressAnimatedEmoji(pressedEmoji) == true) {
						pressedEmoji = null
						resetPressedLink(2)
					}
					else {
						if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
							if (!MediaController.getInstance().isPlayingMessage(currentMessageObject) || MediaController.getInstance().isMessagePaused) {
								delegate?.needPlayMessage(currentMessageObject)
							}
							else {
								MediaController.getInstance().pauseMessage(currentMessageObject)
							}
						}
						else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && drawImageButton) {
							if (buttonState == -1) {
								if (SharedConfig.autoplayGifs) {
									delegate?.didPressImage(this, lastTouchX, lastTouchY)
								}
								else {
									buttonState = 2
									currentMessageObject?.gifState = 1f
									photoImage.allowStartAnimation = false
									photoImage.stopAnimation()
									radialProgress.setIcon(iconForCurrentState, false, true)
									invalidate()
									playSoundEffect(SoundEffectConstants.CLICK)
								}
							}
							else if (buttonState == 2 || buttonState == 0) {
								didPressButton(animated = true, video = false)
								playSoundEffect(SoundEffectConstants.CLICK)
							}
						}
						else {
							val webPage = MessageObject.getMedia(currentMessageObject?.messageOwner)?.webpage
							val url = webPage?.embedUrl

							if (!url.isNullOrEmpty()) {
								// MARK: remove this check to open YouTube Shorts inside the app
								if (url.isYouTubeShortsLink()) {
									Browser.openUrl(context, webPage.url)
								}
								else {
									delegate?.needOpenWebView(currentMessageObject, webPage.embedUrl, webPage.siteName, webPage.title, webPage.url, webPage.embedWidth, webPage.embedHeight)
								}
							}
							else if (buttonState == -1 || buttonState == 3) {
								delegate?.didPressImage(this, lastTouchX, lastTouchY)
								playSoundEffect(SoundEffectConstants.CLICK)
							}
							else if (webPage != null) {
								Browser.openUrl(context, webPage.url)
							}
						}

						resetPressedLink(2)

						return true
					}
				}
				else if (!hadLongPress) {
					resetPressedLink(2)
				}
			}
			else if (event.action == MotionEvent.ACTION_MOVE) {
				if (instantButtonPressed) {
					selectorDrawable[0]?.setHotspot(x.toFloat(), y.toFloat())
				}
			}
		}

		return false
	}

	private fun checkPollButtonMotionEvent(event: MotionEvent): Boolean {
		if (currentMessageObject?.eventId != 0L || pollVoteInProgress || pollUnvoteInProgress || pollButtons.isEmpty() || currentMessageObject?.type != MessageObject.TYPE_POLL || currentMessageObject?.isSent == false) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()
		var result = false

		if (event.action == MotionEvent.ACTION_DOWN) {
			pressedVoteButton = -1
			pollHintPressed = false

			if (hintButtonVisible && pollHintX != -1 && x >= pollHintX && x <= pollHintX + AndroidUtilities.dp(40f) && y >= pollHintY && y <= pollHintY + AndroidUtilities.dp(40f)) {
				pollHintPressed = true

				result = true

				selectorDrawableMaskType[0] = 3

				selectorDrawable[0]?.let {
					it.setBounds(pollHintX - AndroidUtilities.dp(8f), pollHintY - AndroidUtilities.dp(8f), pollHintX + AndroidUtilities.dp(32f), pollHintY + AndroidUtilities.dp(32f))
					it.setHotspot(x.toFloat(), y.toFloat())
					it.state = pressedState
				}

				invalidate()
			}
			else {
				for (a in pollButtons.indices) {
					val button = pollButtons[a]
					val y2 = button.y + namesOffset - AndroidUtilities.dp(13f)

					if (x >= button.x && x <= button.x + backgroundWidth - AndroidUtilities.dp(31f) && y >= y2 && y <= y2 + button.height + AndroidUtilities.dp(26f)) {
						pressedVoteButton = a

						if (!pollVoted && !pollClosed) {
							selectorDrawableMaskType[0] = 1

							selectorDrawable[0]?.let {
								it.setBounds(button.x - AndroidUtilities.dp(9f), y2, button.x + backgroundWidth - AndroidUtilities.dp(22f), y2 + button.height + AndroidUtilities.dp(26f))
								it.setHotspot(x.toFloat(), y.toFloat())
								it.state = pressedState
							}
						}

						invalidate()
					}

					result = true

					break
				}
			}
		}
		else {
			if (event.action == MotionEvent.ACTION_UP) {
				if (pollHintPressed) {
					playSoundEffect(SoundEffectConstants.CLICK)
					delegate?.didPressHint(this, 0)
					pollHintPressed = false
					selectorDrawable[0]?.state = StateSet.NOTHING
				}
				else if (pressedVoteButton != -1) {
					playSoundEffect(SoundEffectConstants.CLICK)

					selectorDrawable[0]?.state = StateSet.NOTHING

					if (currentMessageObject?.scheduled == true) {
						Toast.makeText(context, context.getString(R.string.MessageScheduledVote), Toast.LENGTH_LONG).show()
					}
					else {
						val button = pollButtons[pressedVoteButton]
						val answer = button.answer

						if (pollVoted || pollClosed) {
							val answers = ArrayList<TLRPC.TLPollAnswer>()

							if (answer != null) {
								answers.add(answer)
							}

							delegate?.didPressVoteButtons(this, answers, button.count, button.x + AndroidUtilities.dp(50f), button.y + namesOffset)
						}
						else {
							if (lastPoll?.multipleChoice == true) {
								if (currentMessageObject?.checkedVotes?.contains(answer) == true) {
									currentMessageObject?.checkedVotes?.remove(answer)
									pollCheckBox?.get(pressedVoteButton)?.setChecked(checked = false, animated = true)
								}
								else {
									if (answer != null) {
										currentMessageObject?.checkedVotes?.add(answer)
									}

									pollCheckBox?.get(pressedVoteButton)?.setChecked(checked = true, animated = true)
								}
							}
							else {
								pollVoteInProgressNum = pressedVoteButton
								pollVoteInProgress = true
								vibrateOnPollVote = true
								voteCurrentProgressTime = 0.0f
								firstCircleLength = true
								voteCurrentCircleLength = 360f
								voteRisingCircleLength = false

								val answers = ArrayList<TLRPC.TLPollAnswer>()

								if (answer != null) {
									answers.add(answer)
								}

								delegate?.didPressVoteButtons(this, answers, -1, 0, 0)
							}
						}
					}

					pressedVoteButton = -1

					invalidate()
				}
			}
			else if (event.action == MotionEvent.ACTION_MOVE) {
				if ((pressedVoteButton != -1 || pollHintPressed) && selectorDrawable[0] != null) {
					selectorDrawable[0]?.setHotspot(x.toFloat(), y.toFloat())
				}
			}
		}

		return result
	}

	private fun checkInstantButtonMotionEvent(event: MotionEvent): Boolean {
		if (currentMessageObject?.isSponsored == false && (!drawInstantView || currentMessageObject?.type == MessageObject.TYPE_COMMON)) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (drawInstantView && instantButtonRect.contains(x.toFloat(), y.toFloat())) {
				selectorDrawableMaskType[0] = if (lastPoll != null) 2 else 0
				instantPressed = true

				selectorDrawable[0]?.let {
					if (instantButtonRect.contains(x.toFloat(), y.toFloat())) {
						it.setHotspot(x.toFloat(), y.toFloat())
						it.state = pressedState
						instantButtonPressed = true
					}
				}

				invalidate()

				return true
			}
		}
		else if (event.action == MotionEvent.ACTION_UP) {
			if (instantPressed) {
				if (delegate != null) {
					if (lastPoll != null) {
						if (currentMessageObject?.scheduled == true) {
							Toast.makeText(context, context.getString(R.string.MessageScheduledVoteResults), Toast.LENGTH_LONG).show()
						}
						else {
							if (pollVoted || pollClosed) {
								delegate?.didPressInstantButton(this, drawInstantViewType)
							}
							else {
								if (!currentMessageObject?.checkedVotes.isNullOrEmpty()) {
									pollVoteInProgressNum = -1
									pollVoteInProgress = true
									vibrateOnPollVote = true
									voteCurrentProgressTime = 0.0f
									firstCircleLength = true
									voteCurrentCircleLength = 360f
									voteRisingCircleLength = false
								}

								delegate?.didPressVoteButtons(this, currentMessageObject?.checkedVotes, -1, 0, namesOffset)
							}
						}
					}
					else {
						delegate?.didPressInstantButton(this, drawInstantViewType)
					}
				}

				playSoundEffect(SoundEffectConstants.CLICK)

				selectorDrawable[0]?.state = StateSet.NOTHING

				instantButtonPressed = false
				instantPressed = false

				invalidate()
			}
		}
		else if (event.action == MotionEvent.ACTION_MOVE) {
			if (instantButtonPressed) {
				selectorDrawable[0]?.setHotspot(x.toFloat(), y.toFloat())
			}
		}
		return false
	}

	private fun invalidateWithParent() {
		if (currentMessagesGroup != null) {
			(parent as? ViewGroup)?.invalidate()
		}

		invalidate()
	}

	private fun checkCommentButtonMotionEvent(event: MotionEvent): Boolean {
		if (!drawCommentButton) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()

		if (currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0 && commentButtonRect.contains(x, y)) {
			val parent = parent as ViewGroup
			var a = 0
			val n = parent.childCount

			while (a < n) {
				val view = parent.getChildAt(a)

				if (view !== this && view is ChatMessageCell) {
					if (view.drawCommentButton && view.currentMessagesGroup === currentMessagesGroup && view.currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0) {
						val childEvent = MotionEvent.obtain(0, 0, event.actionMasked, event.x + left - view.left, event.y + top - view.top, 0)
						view.checkCommentButtonMotionEvent(childEvent)
						childEvent.recycle()
						break
					}
				}

				a++
			}

			return true
		}

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (commentButtonRect.contains(x, y)) {
				if (currentMessageObject?.isSent == true) {
					selectorDrawableMaskType[1] = 2

					commentButtonPressed = true

					selectorDrawable[1]?.let {
						it.setHotspot(x.toFloat(), y.toFloat())
						it.state = pressedState
					}

					invalidateWithParent()
				}

				return true
			}
		}
		else if (event.action == MotionEvent.ACTION_UP) {
			if (commentButtonPressed) {
				if (isRepliesChat) {
					delegate?.didPressSideButton(this)
				}
				else {
					delegate?.didPressCommentButton(this)
				}

				playSoundEffect(SoundEffectConstants.CLICK)

				selectorDrawable[1]?.state = StateSet.NOTHING
				commentButtonPressed = false
				invalidateWithParent()
			}
		}
		else if (event.action == MotionEvent.ACTION_MOVE) {
			if (commentButtonPressed && selectorDrawable[1] != null) {
				selectorDrawable[1]?.setHotspot(x.toFloat(), y.toFloat())
			}
		}

		return false
	}

	private fun checkOtherButtonMotionEvent(event: MotionEvent): Boolean {
		if ((documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) && currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP == 0) {
			return false
		}

		var allow = currentMessageObject!!.type == MessageObject.TYPE_CALL

		if (!allow) {
			allow = !(documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && currentMessageObject!!.type != 12 && documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_VIDEO && documentAttachType != DOCUMENT_ATTACH_TYPE_GIF && currentMessageObject!!.type != 8 || hasGamePreview || hasInvoicePreview)
		}

		if (!allow) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()
		var result = false

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (currentMessageObject?.type == MessageObject.TYPE_CALL) {
				val idx = if (currentMessageObject?.isVideoCall == true) 1 else 0

				if (x >= otherX && x <= otherX + AndroidUtilities.dp((30 + if (idx == 0) 202 else 200).toFloat()) && y >= otherY - AndroidUtilities.dp(14f) && y <= otherY + AndroidUtilities.dp(50f)) {
					otherPressed = true
					result = true
					selectorDrawableMaskType[0] = 4

					selectorDrawable[0]?.let {
						val cx = otherX + AndroidUtilities.dp(if (idx == 0) 202f else 200f) + Theme.chat_msgInCallDrawable[idx].intrinsicWidth / 2
						val cy = otherY + Theme.chat_msgInCallDrawable[idx].intrinsicHeight / 2
						it.setBounds(cx - AndroidUtilities.dp(20f), cy - AndroidUtilities.dp(20f), cx + AndroidUtilities.dp(20f), cy + AndroidUtilities.dp(20f))
						it.setHotspot(x.toFloat(), y.toFloat())
						it.state = pressedState
					}

					invalidate()
				}
			}
			else {
				if (x >= otherX - AndroidUtilities.dp(20f) && x <= otherX + AndroidUtilities.dp(20f) && y >= otherY - AndroidUtilities.dp(4f) && y <= otherY + AndroidUtilities.dp(30f)) {
					otherPressed = true
					result = true
					invalidate()
				}
			}
		}
		else {
			if (event.action == MotionEvent.ACTION_UP) {
				if (otherPressed) {
					if (currentMessageObject?.type == MessageObject.TYPE_CALL) {
						selectorDrawable[0]?.state = StateSet.NOTHING
					}

					otherPressed = false

					playSoundEffect(SoundEffectConstants.CLICK)
					delegate?.didPressOther(this, otherX.toFloat(), otherY.toFloat())
					invalidate()

					result = true
				}
			}
			else if (event.action == MotionEvent.ACTION_MOVE) {
				if (currentMessageObject?.type == MessageObject.TYPE_CALL && otherPressed) {
					selectorDrawable[0]?.setHotspot(x.toFloat(), y.toFloat())
				}
			}
		}

		return result
	}

	private fun checkDateMotionEvent(event: MotionEvent): Boolean {
		if (currentMessageObject?.isImportedForward != true) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()
		var result = false

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (x >= drawTimeX && x <= drawTimeX + timeWidth && y >= drawTimeY && y <= drawTimeY + AndroidUtilities.dp(20f)) {
				timePressed = true
				result = true
				invalidate()
			}
		}
		else {
			if (event.action == MotionEvent.ACTION_UP) {
				if (timePressed) {
					timePressed = false
					playSoundEffect(SoundEffectConstants.CLICK)
					delegate?.didPressTime(this)
					invalidate()
					result = true
				}
			}
		}

		return result
	}

	private fun checkRoundSeekbar(event: MotionEvent): Boolean {
		if (!MediaController.getInstance().isPlayingMessage(currentMessageObject) || !MediaController.getInstance().isMessagePaused) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()

		if (event.action == MotionEvent.ACTION_DOWN) {
			if (x >= seekbarRoundX - AndroidUtilities.dp(20f) && x <= seekbarRoundX + AndroidUtilities.dp(20f) && y >= seekbarRoundY - AndroidUtilities.dp(20f) && y <= seekbarRoundY + AndroidUtilities.dp(20f)) {
				parent.requestDisallowInterceptTouchEvent(true)
				cancelCheckLongPress()
				roundSeekbarTouched = 1
				invalidate()
			}
			else {
				val localX = x - photoImage.centerX
				val localY = y - photoImage.centerY
				val r2 = (photoImage.imageWidth - AndroidUtilities.dp(64f)) / 2

				if (localX * localX + localY * localY < photoImage.imageWidth / 2 * photoImage.imageWidth / 2 && localX * localX + localY * localY > r2 * r2) {
					parent.requestDisallowInterceptTouchEvent(true)
					cancelCheckLongPress()
					roundSeekbarTouched = 1
					invalidate()
				}
			}
		}
		else if (roundSeekbarTouched == 1 && event.action == MotionEvent.ACTION_MOVE) {
			val localX = x - photoImage.centerX
			val localY = y - photoImage.centerY
			var a = Math.toDegrees(atan2(localY.toDouble(), localX.toDouble())).toFloat() + 90

			if (a < 0) {
				a += 360f
			}

			val p = a / 360f

			if (abs(currentMessageObject!!.audioProgress - p) > 0.9f) {
				if (roundSeekbarOutAlpha == 0f) {
					performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
				}

				roundSeekbarOutAlpha = 1f
				roundSeekbarOutProgress = currentMessageObject!!.audioProgress
			}
			val currentTime = System.currentTimeMillis()

			if (currentTime - lastSeekUpdateTime > 100) {
				MediaController.getInstance().seekToProgress(currentMessageObject, p)
				lastSeekUpdateTime = currentTime
			}

			currentMessageObject?.audioProgress = p

			updatePlayingMessageProgress()
		}

		if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
			if (roundSeekbarTouched != 0) {
				if (event.action == MotionEvent.ACTION_UP) {
					val localX = x - photoImage.centerX
					val localY = y - photoImage.centerY
					var a = Math.toDegrees(atan2(localY.toDouble(), localX.toDouble())).toFloat() + 90

					if (a < 0) {
						a += 360f
					}

					val p = a / 360f

					currentMessageObject?.audioProgress = p

					MediaController.getInstance().seekToProgress(currentMessageObject, p)
					updatePlayingMessageProgress()
				}

				MediaController.getInstance().playMessage(currentMessageObject)

				roundSeekbarTouched = 0

				parent?.requestDisallowInterceptTouchEvent(false)
			}
		}

		return roundSeekbarTouched != 0
	}

	private fun checkPhotoImageMotionEvent(event: MotionEvent): Boolean {
		if (!drawPhotoImage && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT || currentMessageObject!!.isSending && buttonState != 1) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()
		var result = false

		if (event.action == MotionEvent.ACTION_DOWN) {
			var area2 = false
			val side = AndroidUtilities.dp(48f)

			if (miniButtonState >= 0) {
				val offset = AndroidUtilities.dp(27f)
				area2 = x >= buttonX + offset && x <= buttonX + offset + side && y >= buttonY + offset && y <= buttonY + offset + side
			}

			if (area2) {
				miniButtonPressed = 1
				invalidate()
				result = true
			}
			else if (buttonState != -1 && radialProgress.icon != MediaActionDrawable.ICON_NONE && x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side) {
				buttonPressed = 1
				invalidate()
				result = true
			}
			else if (drawVideoImageButton && buttonState != -1 && x >= videoButtonX && x <= videoButtonX + AndroidUtilities.dp((26 + 8).toFloat()) + max(infoWidth, docTitleWidth) && y >= videoButtonY && y <= videoButtonY + AndroidUtilities.dp(30f)) {
				videoButtonPressed = 1
				invalidate()
				result = true
			}
			else {
				if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
					if (x >= photoImage.imageX && x <= photoImage.imageX + backgroundWidth - AndroidUtilities.dp(50f) && y >= photoImage.imageY && y <= photoImage.imageY + photoImage.imageHeight) {
						imagePressed = true
						result = true
					}
				}
				else if (!currentMessageObject!!.isAnyKindOfSticker || currentMessageObject!!.inputStickerSet != null || currentMessageObject!!.isAnimatedEmoji || currentMessageObject!!.isDice) {
					if (x >= photoImage.imageX && x <= photoImage.imageX + photoImage.imageWidth && y >= photoImage.imageY && y <= photoImage.imageY + photoImage.imageHeight) {
						if (isRoundVideo) {
							if ((x - photoImage.centerX) * (x - photoImage.centerX) + (y - photoImage.centerY) * (y - photoImage.centerY) < photoImage.imageWidth / 2f * (photoImage.imageWidth / 2)) {
								imagePressed = true
								result = true
							}
						}
						else {
							imagePressed = true
							result = true
						}
					}

					if (currentMessageObject?.type == MessageObject.TYPE_CONTACT) {
						val uid = (MessageObject.getMedia(currentMessageObject?.messageOwner) as? TLRPC.TLMessageMediaContact)?.userId
						var user: User? = null

						if (uid != 0L) {
							user = MessagesController.getInstance(currentAccount).getUser(uid)
						}

						if (user == null) {
							imagePressed = false
							result = false
						}
					}
				}
			}

			if (imagePressed) {
				if (currentMessageObject?.isSendError == true) {
					imagePressed = false
					result = false
				}
				else if (currentMessageObject?.type == MessageObject.TYPE_GIF && buttonState == -1 && SharedConfig.autoplayGifs && photoImage.animation == null) {
					imagePressed = false
					result = false
				}
			}
		}
		else {
			if (event.action == MotionEvent.ACTION_UP) {
				if (videoButtonPressed == 1) {
					videoButtonPressed = 0
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressButton(animated = true, video = true)
					invalidate()
				}
				else if (buttonPressed == 1) {
					buttonPressed = 0

					playSoundEffect(SoundEffectConstants.CLICK)

					if (drawVideoImageButton) {
						didClickedImage()
					}
					else {
						didPressButton(animated = true, video = false)
					}

					invalidate()
				}
				else if (miniButtonPressed == 1) {
					miniButtonPressed = 0
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressMiniButton()
					invalidate()
				}
				else if (imagePressed) {
					imagePressed = false

					if (buttonState == -1 || buttonState == 2 || buttonState == 3 || drawVideoImageButton) {
						playSoundEffect(SoundEffectConstants.CLICK)
						didClickedImage()
					}
					else if (buttonState == 0) {
						playSoundEffect(SoundEffectConstants.CLICK)
						didPressButton(animated = true, video = false)
					}

					invalidate()
				}
			}
		}

		return result
	}

	private fun checkAudioMotionEvent(event: MotionEvent): Boolean {
		if (documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO && documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC) {
			return false
		}

		if (AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()

		var result = if (useSeekBarWaveform) {
			seekBarWaveform.onTouch(event.action, event.x - seekBarX - AndroidUtilities.dp(13f), event.y - seekBarY)
		}
		else {
			if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
				seekBar.onTouch(event.action, event.x - seekBarX, event.y - seekBarY)
			}
			else {
				false
			}
		}

		if (result) {
			if (!useSeekBarWaveform && event.action == MotionEvent.ACTION_DOWN) {
				parent.requestDisallowInterceptTouchEvent(true)
			}
			else if (useSeekBarWaveform && !seekBarWaveform.isStartDraging && event.action == MotionEvent.ACTION_UP) {
				didPressButton(animated = true, video = false)
			}

			disallowLongPress = true

			invalidate()
		}
		else {
			val side = AndroidUtilities.dp(36f)
			var area = false
			var area2 = false

			if (miniButtonState >= 0) {
				val offset = AndroidUtilities.dp(27f)
				area2 = x >= buttonX + offset && x <= buttonX + offset + side && y >= buttonY + offset && y <= buttonY + offset + side
			}

			if (!area2) {
				area = if (buttonState == 0 || buttonState == 1 || buttonState == 2) {
					x >= buttonX - AndroidUtilities.dp(12f) && x <= buttonX - AndroidUtilities.dp(12f) + backgroundWidth && y >= (if (drawInstantView) buttonY else namesOffset + mediaOffsetY) && y <= (if (drawInstantView) buttonY + side else namesOffset + mediaOffsetY + AndroidUtilities.dp(82f))
				}
				else {
					x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side
				}
			}

			if (event.action == MotionEvent.ACTION_DOWN) {
				if (area || area2) {
					if (area) {
						buttonPressed = 1
					}
					else {
						miniButtonPressed = 1
					}

					invalidate()
					result = true
				}
			}
			else if (buttonPressed != 0) {
				if (event.action == MotionEvent.ACTION_UP) {
					buttonPressed = 0
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressButton(animated = true, video = false)
					invalidate()
				}
				else if (event.action == MotionEvent.ACTION_CANCEL) {
					buttonPressed = 0
					invalidate()
				}
				else if (event.action == MotionEvent.ACTION_MOVE) {
					if (!area) {
						buttonPressed = 0
						invalidate()
					}
				}
			}
			else if (miniButtonPressed != 0) {
				if (event.action == MotionEvent.ACTION_UP) {
					miniButtonPressed = 0
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressMiniButton()
					invalidate()
				}
				else if (event.action == MotionEvent.ACTION_CANCEL) {
					miniButtonPressed = 0
					invalidate()
				}
				else if (event.action == MotionEvent.ACTION_MOVE) {
					if (!area2) {
						miniButtonPressed = 0
						invalidate()
					}
				}
			}
		}
		return result
	}

	fun checkSpoilersMotionEvent(event: MotionEvent): Boolean {
		if (currentMessageObject?.hasValidGroupId() == true && currentMessagesGroup?.isDocuments == false) {
			val parent = parent as ViewGroup

			for (i in 0 until parent.childCount) {
				val v = parent.getChildAt(i)

				if (v is ChatMessageCell) {
					val group = v.currentMessagesGroup
					val position = v.currentPosition

					if (group != null && group.groupId == currentMessagesGroup?.groupId && position!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0 && position.flags and MessageObject.POSITION_FLAG_LEFT != 0) {
						if (v !== this) {
							event.offsetLocation((this.left - v.left).toFloat(), (this.top - v.top).toFloat())
							val result = v.checkSpoilersMotionEvent(event)
							event.offsetLocation(-(this.left - v.left).toFloat(), -(this.top - v.top).toFloat())
							return result
						}
					}
				}
			}
		}

		if (isSpoilerRevealing) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()
		val act = event.actionMasked

		if (act == MotionEvent.ACTION_DOWN) {
			if (x >= textX && y >= textY && x <= textX + currentMessageObject!!.textWidth && y <= textY + currentMessageObject!!.textHeight) {
				currentMessageObject?.textLayoutBlocks?.let { blocks ->
					for (i in blocks.indices) {
						if (blocks[i].textYOffset > y) {
							break
						}

						val block = blocks[i]
						val offX = if (block.isRtl) currentMessageObject!!.textXOffset.toInt() else 0

						for (eff in block.spoilers) {
							if (eff.bounds.contains(x - textX + offX, (y - textY - block.textYOffset).toInt())) {
								spoilerPressed = eff
								isCaptionSpoilerPressed = false
								return true
							}
						}
					}
				}
			}

			if (hasCaptionLayout() && x >= captionX && y >= captionY && x <= captionX + captionLayout!!.width && y <= captionY + captionLayout!!.height) {
				for (eff in captionSpoilers) {
					if (eff.bounds.contains((x - captionX).toInt(), (y - captionY).toInt())) {
						spoilerPressed = eff
						isCaptionSpoilerPressed = true
						return true
					}
				}
			}
		}
		else if (act == MotionEvent.ACTION_UP && spoilerPressed != null) {
			playSoundEffect(SoundEffectConstants.CLICK)

			sPath.rewind()

			if (isCaptionSpoilerPressed) {
				for (eff in captionSpoilers) {
					val b = eff.bounds
					sPath.addRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), Path.Direction.CW)
				}
			}
			else {
				currentMessageObject?.textLayoutBlocks?.forEach { block ->
					for (eff in block.spoilers) {
						val b = eff.bounds
						sPath.addRect(b.left.toFloat(), b.top + block.textYOffset, b.right.toFloat(), b.bottom + block.textYOffset, Path.Direction.CW)
					}
				}
			}

			sPath.computeBounds(rect, false)

			val width = rect.width()
			val height = rect.height()
			val rad = sqrt(width.toDouble().pow(2.0) + height.toDouble().pow(2.0)).toFloat()

			isSpoilerRevealing = true

			spoilerPressed?.setOnRippleEndCallback {
				post {
					isSpoilerRevealing = false

					getMessageObject()?.isSpoilersRevealed = true

					if (isCaptionSpoilerPressed) {
						captionSpoilers.clear()
					}
					else {
						currentMessageObject?.textLayoutBlocks?.forEach {
							it.spoilers.clear()
						}
					}

					invalidate()
				}
			}

			if (isCaptionSpoilerPressed) {
				for (eff in captionSpoilers) {
					eff.startRipple(x - captionX, y - captionY, rad)
				}
			}
			else if (currentMessageObject?.textLayoutBlocks != null) {
				for (block in currentMessageObject!!.textLayoutBlocks!!) {
					val offX = if (block.isRtl) currentMessageObject!!.textXOffset.toInt() else 0

					for (eff in block.spoilers) {
						eff.startRipple((x - textX + offX).toFloat(), y - block.textYOffset - textY, rad)
					}
				}
			}

			if (parent is RecyclerListView) {
				val vg = parent as ViewGroup

				for (i in 0 until vg.childCount) {
					val ch = vg.getChildAt(i)

					if (ch is ChatMessageCell) {
						if (ch.getMessageObject() != null && ch.getMessageObject()!!.replyMsgId == getMessageObject()!!.id) {
							if (ch.replySpoilers.isNotEmpty()) {
								ch.replySpoilers[0].setOnRippleEndCallback {
									post {
										ch.getMessageObject()?.replyMessageObject?.isSpoilersRevealed = true
										ch.replySpoilers.clear()
										ch.invalidate()
									}
								}

								for (eff in ch.replySpoilers) {
									eff.startRipple(eff.bounds.centerX().toFloat(), eff.bounds.centerY().toFloat(), rad)
								}
							}
						}
					}
				}
			}

			spoilerPressed = null

			return true
		}

		return false
	}

	private fun checkBotButtonMotionEvent(event: MotionEvent): Boolean {
		if (botButtons.isEmpty() || currentMessageObject?.eventId != 0L) {
			return false
		}

		val x = event.x.toInt()
		val y = event.y.toInt()
		var result = false

		if (event.action == MotionEvent.ACTION_DOWN) {
			val addX = if (currentMessageObject!!.isOutOwner) {
				measuredWidth - widthForButtons - AndroidUtilities.dp(10f)
			}
			else {
				backgroundDrawableLeft + AndroidUtilities.dp(if (mediaBackground) 1f else 7f)
			}

			for (a in botButtons.indices) {
				val button = botButtons[a]
				val y2 = button.y + layoutHeight - AndroidUtilities.dp(2f)

				if (x >= button.x + addX && x <= button.x + addX + button.width && y >= y2 && y <= y2 + button.height) {
					pressedBotButton = a

					invalidate()

					result = true

					val longPressedBotButton = pressedBotButton

					postDelayed({
						if (longPressedBotButton == pressedBotButton) {

							if (currentMessageObject?.scheduled == false) {
								val button2 = botButtons[pressedBotButton]

								if (button2.button != null) {
									cancelCheckLongPress()
									delegate?.didLongPressBotButton(this, button2.button)
								}
							}

							pressedBotButton = -1

							invalidate()
						}
					}, (ViewConfiguration.getLongPressTimeout() - 300).toLong())

					break
				}
			}
		}
		else {
			if (event.action == MotionEvent.ACTION_UP) {
				if (pressedBotButton != -1) {
					playSoundEffect(SoundEffectConstants.CLICK)
					if (currentMessageObject?.scheduled == true) {
						Toast.makeText(context, context.getString(R.string.MessageScheduledBotAction), Toast.LENGTH_LONG).show()
					}
					else {
						val button = botButtons[pressedBotButton]

						if (button.button != null) {
							delegate?.didPressBotButton(this, button.button)
						}
					}

					postDelayed({
						pressedBotButton = -1
						invalidate()
					}, 200L)

					invalidate()
				}
			}
		}

		return result
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (currentMessageObject == null || delegate?.canPerformActions() != true || animationRunning) {
			checkTextSelection(event)
			return super.onTouchEvent(event)
		}

		if (checkTextSelection(event)) {
			return true
		}

		if (checkRoundSeekbar(event)) {
			return true
		}

		if (checkReactionsTouchEvent(event)) {
			return true
		}

		if (videoPlayerRewinder != null && videoPlayerRewinder!!.rewindCount > 0) {
			if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
				parent.requestDisallowInterceptTouchEvent(false)
				videoPlayerRewinder?.cancelRewind()
				return false
			}

			return true
		}

		disallowLongPress = false
		lastTouchX = event.x
		lastTouchY = event.y

		backgroundDrawable.setTouchCoords(lastTouchX, lastTouchY)

		var result = checkSpoilersMotionEvent(event)

		if (!result) {
			result = checkTextBlockMotionEvent(event)
		}
		if (!result) {
			result = checkPinchToZoom(event)
		}
		if (!result) {
			result = checkDateMotionEvent(event)
		}
		if (!result) {
			result = checkTextSelection(event)
		}
		if (!result) {
			result = checkOtherButtonMotionEvent(event)
		}
		if (!result) {
			result = checkCaptionMotionEvent(event)
		}
		if (!result) {
			result = checkTranscribeButtonMotionEvent(event)
		}
		if (!result) {
			result = checkAudioMotionEvent(event)
		}
		if (!result) {
			result = checkLinkPreviewMotionEvent(event)
		}
		if (!result) {
			result = checkInstantButtonMotionEvent(event)
		}
		if (!result) {
			result = checkCommentButtonMotionEvent(event)
		}
		if (!result) {
			result = checkGameMotionEvent(event)
		}
		if (!result) {
			result = checkPhotoImageMotionEvent(event)
		}
		if (!result) {
			result = checkBotButtonMotionEvent(event)
		}
		if (!result) {
			result = checkPollButtonMotionEvent(event)
		}

		if (event.action == MotionEvent.ACTION_CANCEL) {
			spoilerPressed = null
			isCaptionSpoilerPressed = false
			buttonPressed = 0
			miniButtonPressed = 0
			pressedBotButton = -1
			pressedVoteButton = -1
			pollHintPressed = false
			psaHintPressed = false
			linkPreviewPressed = false
			otherPressed = false
			sideButtonPressed = false
			imagePressed = false
			timePressed = false
			gamePreviewPressed = false
			commentButtonPressed = false
			instantButtonPressed = false
			instantPressed = false

			for (drawable in selectorDrawable) {
				drawable?.state = StateSet.NOTHING
			}

			result = false

			if (hadLongPress) {
				if (pressedLinkType != 2) {
					hadLongPress = false
				}

				pressedLink = null
				pressedEmoji = null
				pressedLinkType = -1
			}
			else {
				resetPressedLink(-1)
			}
		}

		updateRadialProgressBackground()

		if (!disallowLongPress && result && event.action == MotionEvent.ACTION_DOWN) {
			startCheckLongPress()
		}

		if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) {
			cancelCheckLongPress()
		}

		if (!result) {
			val x = event.x
			val y = event.y

			if (event.action == MotionEvent.ACTION_DOWN) {
				if (delegate == null || delegate!!.canPerformActions()) {
					if (isAvatarVisible && avatarImage.isInsideImage(x, y + top)) {
						avatarPressed = true
						result = true
					}
					else if (psaButtonVisible && hasPsaHint && x >= psaHelpX && x <= psaHelpX + AndroidUtilities.dp(40f) && y >= psaHelpY && y <= psaHelpY + AndroidUtilities.dp(40f)) {
						psaHintPressed = true
						createSelectorDrawable(0)
						selectorDrawableMaskType[0] = 3

						selectorDrawable[0]?.let {
							it.setBounds(psaHelpX - AndroidUtilities.dp(8f), psaHelpY - AndroidUtilities.dp(8f), psaHelpX + AndroidUtilities.dp(32f), psaHelpY + AndroidUtilities.dp(32f))
							it.setHotspot(x, y)
							it.state = pressedState
						}

						result = true

						invalidate()
					}
					else if (drawForwardedName && forwardedNameLayout[0] != null && x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32f)) {
						if (viaWidth != 0 && x >= forwardNameX + viaNameWidth + AndroidUtilities.dp(4f)) {
							forwardBotPressed = true
						}
						else {
							forwardNamePressed = true
						}

						result = true
					}
					else if (drawNameLayout && nameLayout != null && viaWidth != 0 && x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= nameY - AndroidUtilities.dp(4f) && y <= nameY + AndroidUtilities.dp(20f)) {
						forwardBotPressed = true
						result = true
					}
					else if (drawSideButton != 0 && x >= sideStartX && x <= sideStartX + AndroidUtilities.dp(40f) && y >= sideStartY && y <= sideStartY + AndroidUtilities.dp((32 + if (drawSideButton == 3 && commentLayout != null) 18 else 0).toFloat())) {
						if (currentMessageObject!!.isSent) {
							sideButtonPressed = true
						}

						result = true
						invalidate()
					}
					else if (replyNameLayout != null) {
						val replyEnd = if (currentMessageObject?.shouldDrawWithoutBackground() == true) {
							replyStartX + max(replyNameWidth, replyTextWidth)
						}
						else {
							replyStartX + backgroundDrawableRight
						}

						if (x >= replyStartX && x <= replyEnd && y >= replyStartY && y <= replyStartY + AndroidUtilities.dp(35f)) {
							replyPressed = true
							result = true
						}
					}

					if (result) {
						startCheckLongPress()
					}
				}
			}
			else {
				if (event.action != MotionEvent.ACTION_MOVE) {
					cancelCheckLongPress()
				}

				if (avatarPressed) {
					if (event.action == MotionEvent.ACTION_UP) {
						avatarPressed = false

						playSoundEffect(SoundEffectConstants.CLICK)

						if (delegate != null) {
							if (currentUser != null) {
								if (currentUser?.id == 0L) {
									delegate?.didPressHiddenForward(this)
								}
								else {
									delegate?.didPressUserAvatar(this, currentUser, lastTouchX, lastTouchY)
								}
							}
							else if (currentChat != null) {
								val id: Int
								var chat = currentChat

								if (currentMessageObject?.messageOwner?.fwdFrom != null) {
									if (currentMessageObject!!.messageOwner!!.fwdFrom!!.flags and 16 != 0) {
										id = currentMessageObject!!.messageOwner!!.fwdFrom!!.savedFromMsgId
									}
									else {
										id = currentMessageObject!!.messageOwner!!.fwdFrom?.channelPost ?: 0
										chat = currentForwardChannel
									}
								}
								else {
									id = 0
								}

								delegate?.didPressChannelAvatar(this, chat ?: currentChat, id, lastTouchX, lastTouchY)
							}
							else if (currentMessageObject?.sponsoredChatInvite != null) {
								delegate?.didPressInstantButton(this, drawInstantViewType)
							}
						}
					}
					else if (event.action == MotionEvent.ACTION_CANCEL) {
						avatarPressed = false
					}
					else if (event.action == MotionEvent.ACTION_MOVE) {
						if (isAvatarVisible && !avatarImage.isInsideImage(x, y + top)) {
							avatarPressed = false
						}
					}
				}
				else if (psaHintPressed) {
					if (event.action == MotionEvent.ACTION_UP) {
						playSoundEffect(SoundEffectConstants.CLICK)
						delegate?.didPressHint(this, 1)
						psaHintPressed = false
						selectorDrawable[0]?.state = StateSet.NOTHING
						invalidate()
					}
				}
				else if (forwardNamePressed) {
					if (event.action == MotionEvent.ACTION_UP) {
						forwardNamePressed = false

						playSoundEffect(SoundEffectConstants.CLICK)

						if (delegate != null) {
							if (currentForwardChannel != null) {
								delegate?.didPressChannelAvatar(this, currentForwardChannel, currentMessageObject!!.messageOwner!!.fwdFrom?.channelPost ?: 0, lastTouchX, lastTouchY)
							}
							else if (currentForwardUser != null) {
								delegate?.didPressUserAvatar(this, currentForwardUser, lastTouchX, lastTouchY)
							}
							else if (currentForwardName != null) {
								delegate?.didPressHiddenForward(this)
							}
						}
					}
					else if (event.action == MotionEvent.ACTION_CANCEL) {
						forwardNamePressed = false
					}
					else if (event.action == MotionEvent.ACTION_MOVE) {
						if (x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y > forwardNameY + AndroidUtilities.dp(32f)) {
							forwardNamePressed = false
						}
					}
				}
				else if (forwardBotPressed) {
					if (event.action == MotionEvent.ACTION_UP) {
						forwardBotPressed = false
						playSoundEffect(SoundEffectConstants.CLICK)

						if (delegate != null) {
							if ((currentViaBotUser as? TLRPC.TLUser)?.botInlinePlaceholder == null) {
								delegate?.didPressViaBotNotInline(this, currentViaBotUser?.id ?: 0)
							}
							else {
								delegate?.didPressViaBot(this, currentViaBotUser?.username) // MARK: uncomment to enable secret chats  ?: currentMessageObject?.messageOwner?.viaBotName)
							}
						}
					}
					else if (event.action == MotionEvent.ACTION_CANCEL) {
						forwardBotPressed = false
					}
					else if (event.action == MotionEvent.ACTION_MOVE) {
						if (drawForwardedName && forwardedNameLayout[0] != null) {
							if (x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y > forwardNameY + AndroidUtilities.dp(32f)) {
								forwardBotPressed = false
							}
						}
						else {
							if (x >= nameX + viaNameWidth && x <= nameX + viaNameWidth + viaWidth && y >= nameY - AndroidUtilities.dp(4f) && y > nameY + AndroidUtilities.dp(20f)) {
								forwardBotPressed = false
							}
						}
					}
				}
				else if (replyPressed) {
					if (event.action == MotionEvent.ACTION_UP) {
						replyPressed = false

						playSoundEffect(SoundEffectConstants.CLICK)

						if (replyPanelIsForward) {
							if (delegate != null) {
								if (currentForwardChannel != null) {
									delegate?.didPressChannelAvatar(this, currentForwardChannel, currentMessageObject!!.messageOwner!!.fwdFrom?.channelPost ?: 0, lastTouchX, lastTouchY)
								}
								else if (currentForwardUser != null) {
									delegate?.didPressUserAvatar(this, currentForwardUser, lastTouchX, lastTouchY)
								}
								else if (currentForwardName != null) {
									delegate?.didPressHiddenForward(this)
								}
							}
						}
						else {
							if (delegate != null && currentMessageObject?.hasValidReplyMessageObject() == true) {
								delegate?.didPressReplyMessage(this, currentMessageObject!!.replyMsgId)
							}
						}
					}
					else if (event.action == MotionEvent.ACTION_CANCEL) {
						replyPressed = false
					}
					else if (event.action == MotionEvent.ACTION_MOVE) {
						val replyEnd = if (currentMessageObject!!.shouldDrawWithoutBackground()) {
							replyStartX + max(replyNameWidth, replyTextWidth)
						}
						else {
							replyStartX + backgroundDrawableRight
						}

						if (x >= replyStartX && x <= replyEnd && y >= replyStartY && y > replyStartY + AndroidUtilities.dp(35f)) {
							replyPressed = false
						}
					}
				}
				else if (sideButtonPressed) {
					if (event.action == MotionEvent.ACTION_UP) {
						sideButtonPressed = false

						playSoundEffect(SoundEffectConstants.CLICK)

						if (drawSideButton == 3) {
							delegate?.didPressCommentButton(this)
						}
						else {
							delegate?.didPressSideButton(this)
						}
					}
					else if (event.action == MotionEvent.ACTION_CANCEL) {
						sideButtonPressed = false
					}
					else if (event.action == MotionEvent.ACTION_MOVE) {
						if (x >= sideStartX && x <= sideStartX + AndroidUtilities.dp(40f) && y >= sideStartY && y > sideStartY + AndroidUtilities.dp((32 + if (drawSideButton == 3 && commentLayout != null) 18 else 0).toFloat())) {
							sideButtonPressed = false
						}
					}

					invalidate()
				}
			}
		}
		return result
	}

	private fun checkReactionsTouchEvent(event: MotionEvent): Boolean {
		if (currentMessageObject!!.hasValidGroupId() && currentMessagesGroup != null && !currentMessagesGroup!!.isDocuments) {
			val parent = parent as? ViewGroup ?: return false

			for (i in 0 until parent.childCount) {
				val v = parent.getChildAt(i)

				if (v is ChatMessageCell) {
					val group = v.currentMessagesGroup
					val position = v.currentPosition

					if (group != null && group.groupId == currentMessagesGroup!!.groupId && position!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0 && position.flags and MessageObject.POSITION_FLAG_LEFT != 0) {
						return if (v === this) {
							reactionsLayoutInBubble.checkTouchEvent(event)
						}
						else {
							event.offsetLocation((this.left - v.left).toFloat(), (this.top - v.top).toFloat())
							val result = v.reactionsLayoutInBubble.checkTouchEvent(event)
							event.offsetLocation(-(this.left - v.left).toFloat(), -(this.top - v.top).toFloat())
							result
						}
					}
				}
			}

			return false
		}

		return reactionsLayoutInBubble.checkTouchEvent(event)
	}

	private fun checkPinchToZoom(ev: MotionEvent): Boolean {
		val pinchToZoomHelper = delegate?.pinchToZoomHelper

		return if (currentMessageObject == null || !photoImage.hasNotThumb() || pinchToZoomHelper == null || currentMessageObject!!.isSticker || currentMessageObject!!.isAnimatedEmoji || currentMessageObject!!.isVideo && !autoPlayingMedia || isRoundVideo || currentMessageObject!!.isAnimatedSticker || currentMessageObject!!.isDocument() && !currentMessageObject!!.isGif || currentMessageObject!!.needDrawBluredPreview()) {
			false
		}
		else {
			pinchToZoomHelper.checkPinchToZoom(ev, this, photoImage, currentMessageObject)
		}
	}

	private fun checkTextSelection(event: MotionEvent): Boolean {
		val textSelectionHelper = delegate?.textSelectionHelper

		if (textSelectionHelper == null || MessagesController.getInstance(currentAccount).isChatNoForwards(currentMessageObject!!.chatId) || currentMessageObject?.messageOwner?.noforwards == true) {
			return false
		}

		val hasTextBlocks = !currentMessageObject?.textLayoutBlocks.isNullOrEmpty()

		if (!hasTextBlocks && !hasCaptionLayout()) {
			return false
		}

		if (!drawSelectionBackground && currentMessagesGroup == null || currentMessagesGroup != null && delegate?.hasSelectedMessages() != true) {
			return false
		}

		if (currentMessageObject!!.hasValidGroupId() && currentMessagesGroup != null && !currentMessagesGroup!!.isDocuments) {
			val parent = parent as ViewGroup

			for (i in 0 until parent.childCount) {
				val v = parent.getChildAt(i)

				if (v is ChatMessageCell) {
					val group = v.currentMessagesGroup
					val position = v.currentPosition

					if (group != null && group.groupId == currentMessagesGroup!!.groupId && position!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0 && position.flags and MessageObject.POSITION_FLAG_LEFT != 0) {
						textSelectionHelper.setMaybeTextCord(v.captionX.toInt(), v.captionY.toInt())
						textSelectionHelper.setMessageObject(v)

						return if (v === this) {
							textSelectionHelper.onTouchEvent(event)
						}
						else {
							event.offsetLocation((this.left - v.left).toFloat(), (this.top - v.top).toFloat())
							val result = textSelectionHelper.onTouchEvent(event)
							event.offsetLocation(-(this.left - v.left).toFloat(), -(this.top - v.top).toFloat())
							result
						}
					}
				}
			}

			return false
		}
		else {
			if (hasCaptionLayout()) {
				textSelectionHelper.setIsDescription(false)
				textSelectionHelper.setMaybeTextCord(captionX.toInt(), captionY.toInt())
			}
			else {
				if (descriptionLayout != null && event.y > descriptionY) {
					textSelectionHelper.setIsDescription(true)

					val linkX = if (hasGamePreview) {
						unmovedTextX - AndroidUtilities.dp(10f)
					}
					else if (hasInvoicePreview) {
						unmovedTextX + AndroidUtilities.dp(1f)
					}
					else {
						unmovedTextX + AndroidUtilities.dp(1f)
					}

					textSelectionHelper.setMaybeTextCord(linkX + AndroidUtilities.dp(10f) + descriptionX, descriptionY)
				}
				else {
					textSelectionHelper.setIsDescription(false)
					textSelectionHelper.setMaybeTextCord(textX, textY)
				}
			}

			textSelectionHelper.setMessageObject(this)
		}

		return textSelectionHelper.onTouchEvent(event)
	}

	private fun updateSelectionTextPosition() {
		val textSelectionHelper = delegate?.textSelectionHelper ?: return

		if (!textSelectionHelper.isSelected(currentMessageObject)) {
			return
		}

		when (textSelectionHelper.getTextSelectionType(this)) {
			ChatListTextSelectionHelper.TYPE_DESCRIPTION -> {
				val linkX = if (hasGamePreview) {
					unmovedTextX - AndroidUtilities.dp(10f)
				}
				else if (hasInvoicePreview) {
					unmovedTextX + AndroidUtilities.dp(1f)
				}
				else {
					unmovedTextX + AndroidUtilities.dp(1f)
				}

				textSelectionHelper.updateTextPosition(linkX + AndroidUtilities.dp(10f) + descriptionX, descriptionY)
			}

			ChatListTextSelectionHelper.TYPE_CAPTION -> {
				textSelectionHelper.updateTextPosition(captionX.toInt(), captionY.toInt())
			}

			else -> {
				textSelectionHelper.updateTextPosition(textX, textY)
			}
		}
	}

	fun updatePlayingMessageProgress() {
		val currentMessageObject = currentMessageObject ?: return

		videoPlayerRewinder?.let {
			if (it.rewindCount != 0 && it.rewindByBackSeek) {
				currentMessageObject.audioProgress = it.videoProgress
			}
		}

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
			if (infoLayout != null && (PhotoViewer.isPlayingMessage(currentMessageObject) || MediaController.getInstance().isGoingToShowMessageObject(currentMessageObject))) {
				return
			}

			var duration = 0
			val animation = photoImage.animation

			if (animation != null) {
				currentMessageObject.audioPlayerDuration = animation.durationMs / 1000
				duration = currentMessageObject.audioPlayerDuration

				if (currentMessageObject.messageOwner!!.ttl > 0 && currentMessageObject.messageOwner?.destroyTime == 0 && !currentMessageObject.needDrawBluredPreview() && currentMessageObject.isVideo && animation.hasBitmap()) {
					delegate?.didStartVideoStream(currentMessageObject)
				}
			}

			if (duration == 0) {
				duration = currentMessageObject.duration
			}

			if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
				duration -= (duration * currentMessageObject.audioProgress).toInt()
			}
			else if (animation != null) {
				if (duration != 0) {
					duration -= animation.currentProgressMs / 1000
				}

				if (animation.currentProgressMs >= 3000) {
					delegate?.videoTimerReached()
				}
			}

			if (lastTime != duration) {
				val str = AndroidUtilities.formatShortDuration(duration)
				infoWidth = ceil(Theme.chat_infoPaint.measureText(str).toDouble()).toInt()
				infoLayout = StaticLayout(str, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				lastTime = duration
			}
		}
		else if (isRoundVideo) {
			var duration = (currentMessageObject.document as? TLRPC.TLDocument)?.attributes?.firstOrNull { it is TLRPC.TLDocumentAttributeVideo }?.duration ?: 0
//			val document = currentMessageObject.document as? TLRPC.TLDocument

//			for (a in document.attributes.indices) {
//				val attribute = document.attributes[a]
//
//				if (attribute is TLRPC.TLDocumentAttributeVideo) {
//					duration = attribute.duration
//					break
//				}
//			}

			if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
				duration = max(0, duration - currentMessageObject.audioProgressSec)
			}

			if (lastTime != duration) {
				lastTime = duration

				val timeString = AndroidUtilities.formatLongDuration(duration)

				timeWidthAudio = ceil(Theme.chat_timePaint.measureText(timeString).toDouble()).toInt()
				durationLayout = StaticLayout(timeString, Theme.chat_timePaint, timeWidthAudio, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}

			if (currentMessageObject.audioProgress != 0f) {
				lastDrawingAudioProgress = currentMessageObject.audioProgress

				if (lastDrawingAudioProgress > 0.9f) {
					lastDrawingAudioProgress = 1f
				}
			}

			invalidate()
		}
		else if (documentAttach != null) {
			if (useSeekBarWaveform) {
				if (!seekBarWaveform.isDragging) {
					seekBarWaveform.setProgress(currentMessageObject.audioProgress, true)
				}
			}
			else {
				if (!seekBar.isDragging) {
					seekBar.progress = currentMessageObject.audioProgress
					seekBar.setBufferedProgress(currentMessageObject.bufferedProgress)
				}
			}

			val duration: Int

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
				duration = if (!MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
					(documentAttach as? TLRPC.TLDocument)?.attributes?.firstOrNull { it is TLRPC.TLDocumentAttributeAudio }?.duration ?: 0
				}
				else {
					currentMessageObject.audioProgressSec
				}

				if (lastTime != duration) {
					lastTime = duration

					val timeString = AndroidUtilities.formatLongDuration(duration)

					timeWidthAudio = ceil(Theme.chat_audioTimePaint.measureText(timeString).toDouble()).toInt()
					durationLayout = StaticLayout(timeString, Theme.chat_audioTimePaint, timeWidthAudio, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				}
			}
			else {
				var currentProgress = 0

				duration = currentMessageObject.duration

				if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
					currentProgress = currentMessageObject.audioProgressSec
				}

				if (lastTime != currentProgress) {
					lastTime = currentProgress

					val timeString = AndroidUtilities.formatShortDuration(currentProgress, duration)
					val timeWidth = ceil(Theme.chat_audioTimePaint.measureText(timeString).toDouble()).toInt()

					durationLayout = StaticLayout(timeString, Theme.chat_audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				}
			}

			invalidate()
		}
	}

	fun setFullyDraw(draw: Boolean) {
		fullyDraw = draw
	}

	fun setParentViewSize(parentW: Int, parentH: Int) {
		parentWidth = parentW
		parentHeight = parentH
		backgroundHeight = parentH

		if (currentMessageObject != null && hasGradientService() && currentMessageObject?.shouldDrawWithoutBackground() == true || currentBackgroundDrawable != null) {
			invalidate()
		}
	}

	fun setVisiblePart(position: Int, height: Int, parent: Int, parentOffset: Float, visibleTop: Float, parentW: Int, parentH: Int, blurredViewTopOffset: Int, blurredViewBottomOffset: Int) {
		@Suppress("NAME_SHADOWING") var position = position

		parentWidth = parentW
		parentHeight = parentH
		backgroundHeight = parentH

		this.blurredViewTopOffset = blurredViewTopOffset
		this.blurredViewBottomOffset = blurredViewBottomOffset

		viewTop = visibleTop

		if (parent != parentHeight || parentOffset != parentViewTopOffset) {
			parentViewTopOffset = parentOffset
			parentHeight = parent
		}

		if (currentMessageObject != null && hasGradientService() && currentMessageObject!!.shouldDrawWithoutBackground()) {
			invalidate()
		}

		val currentMessageObject = currentMessageObject ?: return
		val textLayoutBlocks = currentMessageObject.textLayoutBlocks ?: return

		position -= textY

		var newFirst = -1
		var newLast = -1
		var newCount = 0
		var startBlock = 0

		for (a in textLayoutBlocks.indices) {
			if (textLayoutBlocks[a].textYOffset > position) {
				break
			}

			startBlock = a
		}

		for (a in startBlock until textLayoutBlocks.size) {
			val block = textLayoutBlocks[a]
			val y = block.textYOffset

			if (intersect(y, y + block.height, position.toFloat(), (position + height).toFloat())) {
				if (newFirst == -1) {
					newFirst = a
				}

				newLast = a
				newCount++
			}
			else if (y > position) {
				break
			}
		}

		if (lastVisibleBlockNum != newLast || firstVisibleBlockNum != newFirst || totalVisibleBlocksCount != newCount) {
			lastVisibleBlockNum = newLast
			firstVisibleBlockNum = newFirst
			totalVisibleBlocksCount = newCount
			invalidate()
		}
		else if (animatedEmojiStack != null) {
			for (i in animatedEmojiStack!!.holders.indices) {
				val holder = animatedEmojiStack!!.holders[i]

				if (holder != null && holder.skipDraw) {
					val top = parentBoundsTop - y - holder.drawingYOffset
					val bottom = parentBoundsBottom - y - holder.drawingYOffset
					if (!holder.outOfBounds(top, bottom)) {
						invalidate()
						break
					}
				}
			}
		}
	}

	private fun intersect(left1: Float, right1: Float, left2: Float, right2: Float): Boolean {
		return if (left1 <= left2) {
			right1 >= left2
		}
		else {
			left1 <= right2
		}
	}

	private fun didClickedImage() {
		val currentMessageObject = currentMessageObject ?: return

		if (currentMessageObject.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW) {
			if (currentMessageObject.messageOwner?.media?.extendedMedia != null && currentMessageObject.messageOwner?.replyMarkup != null) {
				for (row in currentMessageObject.messageOwner!!.replyMarkup!!.rows) {
					for (button in row.buttons) {
						delegate?.didPressExtendedMediaPreview(this, button)
						return
					}
				}
			}
		}
		else if (currentMessageObject.type == MessageObject.TYPE_PHOTO || currentMessageObject.isAnyKindOfSticker) {
			if (buttonState == -1) {
				delegate?.didPressImage(this, lastTouchX, lastTouchY)
			}
			else if (buttonState == 0) {
				didPressButton(animated = true, video = false)
			}
		}
		else if (currentMessageObject.type == MessageObject.TYPE_CONTACT) {
			val uid = MessageObject.getMedia(currentMessageObject.messageOwner)?.userId
			var user: User? = null

			if (uid != 0L) {
				user = MessagesController.getInstance(currentAccount).getUser(uid)
			}

			delegate?.didPressUserAvatar(this, user, lastTouchX, lastTouchY)
		}
		else if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
			if (buttonState != -1) {
				didPressButton(animated = true, video = false)
			}
			else {
				if (!MediaController.getInstance().isPlayingMessage(currentMessageObject) || MediaController.getInstance().isMessagePaused) {
					delegate?.needPlayMessage(currentMessageObject)
				}
				else {
					MediaController.getInstance().pauseMessage(currentMessageObject)
				}
			}
		}
		else if (currentMessageObject.type == MessageObject.TYPE_GIF) {
			if (buttonState == -1 || buttonState == 1 && canStreamVideo && autoPlayingMedia) {
				//if (SharedConfig.autoplayGifs) {

				delegate?.didPressImage(this, lastTouchX, lastTouchY)

				/*} else {
                    buttonState = 2;
                    currentMessageObject.gifState = 1;
                    photoImage.setAllowStartAnimation(false);
                    photoImage.stopAnimation();
                    radialProgress.setIcon(getIconForCurrentState(), false, true);
                    invalidate();
                }*/
			}
			else if (buttonState == 2 || buttonState == 0) {
				didPressButton(animated = true, video = false)
			}
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
			if (buttonState == -1 || drawVideoImageButton && (autoPlayingMedia || SharedConfig.streamMedia && canStreamVideo)) {
				delegate?.didPressImage(this, lastTouchX, lastTouchY)
			}
			else if (drawVideoImageButton) {
				didPressButton(animated = true, video = true)
			}
			else if (buttonState == 0 || buttonState == 3) {
				didPressButton(animated = true, video = false)
			}
		}
		else if (currentMessageObject.type == MessageObject.TYPE_GEO) {
			delegate?.didPressImage(this, lastTouchX, lastTouchY)
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
			if (buttonState == -1) {
				delegate?.didPressImage(this, lastTouchX, lastTouchY)
			}
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
			if (buttonState == -1) {
				val webPage = MessageObject.getMedia(currentMessageObject.messageOwner)?.webpage

				if (webPage != null) {
					if (!webPage.embedUrl.isNullOrEmpty()) {
						delegate?.needOpenWebView(currentMessageObject, webPage.embedUrl, webPage.siteName, webPage.description, webPage.url, webPage.embedWidth, webPage.embedHeight)
					}
					else {
						Browser.openUrl(context, webPage.url)
					}
				}
			}
		}
		else if (hasInvoicePreview) {
			if (buttonState == -1) {
				delegate?.didPressImage(this, lastTouchX, lastTouchY)
			}
		}
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// open message options then
			if (delegate != null) {
				if (currentMessageObject.type == MessageObject.TYPE_CALL) {
					delegate?.didLongPress(this, 0f, 0f)
				}
				else {
					delegate?.didPressOther(this, otherX.toFloat(), otherY.toFloat())
				}
			}
		}
	}

	private fun updateSecretTimeText(messageObject: MessageObject?) {
		if (messageObject == null || !messageObject.needDrawBluredPreview()) {
			return
		}

		val str = messageObject.secretTimeString ?: return

		infoWidth = ceil(Theme.chat_infoPaint.measureText(str).toDouble()).toInt()

		val str2 = TextUtils.ellipsize(str, Theme.chat_infoPaint, infoWidth.toFloat(), TextUtils.TruncateAt.END)

		infoLayout = StaticLayout(str2, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

		invalidate()
	}

	private fun isPhotoDataChanged(`object`: MessageObject?): Boolean {
		if (`object`?.type == MessageObject.TYPE_COMMON || `object`?.type == MessageObject.TYPE_MUSIC) {
			return false
		}

		if (`object`?.type == MessageObject.TYPE_GEO) {
			if (currentUrl == null) {
				return true
			}

			var lat = `object`.messageOwner?.media?.geo?.lat ?: 0.0
			val lon = `object`.messageOwner?.media?.geo?.lon ?: 0.0
			val url: String

			val provider = if (`object`.dialogId.toInt() == 0) {
				when (SharedConfig.mapPreviewType) {
					SharedConfig.MAP_PREVIEW_PROVIDER_ELLO -> MessagesController.MAP_PROVIDER_UNDEFINED
					SharedConfig.MAP_PREVIEW_PROVIDER_GOOGLE -> MessagesController.MAP_PROVIDER_GOOGLE
					SharedConfig.MAP_PREVIEW_PROVIDER_YANDEX -> MessagesController.MAP_PROVIDER_YANDEX_NO_ARGS
					else -> MessagesController.MAP_PROVIDER_UNDEFINED
				}
			}
			else {
				MessagesController.MAP_PROVIDER_UNDEFINED
			}

			if (`object`.messageOwner?.media is TLRPC.TLMessageMediaGeoLive) {
				val photoWidth = backgroundWidth - AndroidUtilities.dp(21f)
				val photoHeight = AndroidUtilities.dp(195f)
				val offset = 268435456
				val rad = offset / Math.PI
				val y = ((offset - rad * ln((1 + sin(lat * Math.PI / 180.0)) / (1 - sin(lat * Math.PI / 180.0))) / 2).roundToLong() - (AndroidUtilities.dp(10.3f).toLong() shl 21 - 15)).toDouble()

				lat = (Math.PI / 2.0 - 2 * atan(exp((y - offset) / rad))) * 180.0 / Math.PI
				url = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), false, 15, provider)
			}
			else if (!`object`.messageOwner?.media?.title.isNullOrEmpty()) {
				val photoWidth = backgroundWidth - AndroidUtilities.dp(21f)
				val photoHeight = AndroidUtilities.dp(195f)

				url = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), true, 15, provider)
			}
			else {
				val photoWidth = backgroundWidth - AndroidUtilities.dp(12f)
				val photoHeight = AndroidUtilities.dp(195f)

				url = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), true, 15, provider)
			}

			return url != currentUrl
		}
		else if (currentPhotoObject == null || currentPhotoObject?.location is TLRPC.TLFileLocationUnavailable) {
			return `object`?.type == MessageObject.TYPE_PHOTO || `object`?.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW || `object`?.type == MessageObject.TYPE_ROUND_VIDEO || `object`?.type == MessageObject.TYPE_VIDEO || `object`?.type == MessageObject.TYPE_GIF || `object`?.isAnyKindOfSticker == true
		}
		else if (currentMessageObject != null && photoNotSet) {
			val cacheFile = FileLoader.getInstance(currentAccount).getPathToMessage(currentMessageObject!!.messageOwner)
			return cacheFile.exists()
		}

		return false
	}

	private val repliesCount: Int
		get() = currentMessagesGroup?.messages?.firstOrNull()?.repliesCount ?: currentMessageObject?.repliesCount ?: 0

	private val recentRepliers: List<TLRPC.Peer>?
		get() = currentMessagesGroup?.messages?.firstOrNull()?.messageOwner?.replies?.recentRepliers ?: currentMessageObject?.messageOwner?.replies?.recentRepliers

	fun updateAnimatedEmojis() {
		if (!imageReceiversAttachState) {
			return
		}

		val cache = if (currentMessageObject?.wasJustSent == true) AnimatedEmojiDrawable.getCacheTypeForEnterView() else AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES

		animatedEmojiStack = if (captionLayout != null) {
			AnimatedEmojiSpan.update(cache, this, false, animatedEmojiStack, captionLayout)
		}
		else {
			AnimatedEmojiSpan.update(cache, this, delegate == null || !delegate!!.canDrawOutboundsContent(), animatedEmojiStack, currentMessageObject!!.textLayoutBlocks)
		}
	}

	private fun updateCaptionSpoilers() {
		captionSpoilersPool.addAll(captionSpoilers)
		captionSpoilers.clear()

		if (captionLayout != null && getMessageObject()?.isSpoilersRevealed != true) {
			SpoilerEffect.addSpoilers(this, captionLayout, captionSpoilersPool, captionSpoilers)
		}
	}

	private val isUserDataChanged: Boolean
		get() {
			if (currentMessageObject != null && !hasLinkPreview && MessageObject.getMedia(currentMessageObject!!.messageOwner) != null && MessageObject.getMedia(currentMessageObject!!.messageOwner)?.webpage is TLRPC.TLWebPage) {
				return true
			}

			if (currentMessageObject == null || currentUser == null && currentChat == null) {
				return false
			}

			if (lastSendState != currentMessageObject!!.messageOwner!!.sendState) {
				return true
			}

			if (lastDeleteDate != currentMessageObject!!.messageOwner!!.destroyTime) {
				return true
			}

			if (lastViewsCount != currentMessageObject!!.messageOwner!!.views) {
				return true
			}

			if (lastRepliesCount != repliesCount) {
				return true
			}

			updateCurrentUserAndChat()

			var newPhoto: TLRPC.FileLocation? = null

			if (isAvatarVisible) {
				newPhoto = currentUser?.photo?.photoSmall ?: currentChat?.photo?.photoSmall
			}

			if (replyTextLayout == null && currentMessageObject!!.replyMessageObject != null) {
				if (!isThreadChat || currentMessageObject!!.replyMessageObject?.messageOwner?.fwdFrom == null || currentMessageObject!!.replyMessageObject?.messageOwner?.fwdFrom?.channelPost == 0) {
					return true
				}
			}

			if (currentPhoto == null && newPhoto != null || currentPhoto != null && newPhoto == null || currentPhoto != null && (currentPhoto!!.localId != newPhoto!!.localId || currentPhoto!!.volumeId != newPhoto.volumeId)) {
				return true
			}

			var newReplyPhoto: TLRPC.PhotoSize? = null

			if (replyNameLayout != null && currentMessageObject?.replyMessageObject != null) {
				val photoSize = FileLoader.getClosestPhotoSizeWithSize(currentMessageObject?.replyMessageObject?.photoThumbs, 40)

				if (photoSize != null && currentMessageObject?.replyMessageObject?.isAnyKindOfSticker != true) {
					newReplyPhoto = photoSize
				}
			}

			if (currentReplyPhoto == null && newReplyPhoto != null) {
				return true
			}

			var newNameString = if (isNeedAuthorName) authorName else null

			if (currentNameString == null && newNameString != null || currentNameString != null && newNameString == null || currentNameString != null && currentNameString != newNameString) {
				return true
			}

			if (drawForwardedName && currentMessageObject!!.needDrawForwarded()) {
				newNameString = currentMessageObject!!.forwardedName
				return (currentForwardNameString == null && newNameString != null || currentForwardNameString != null) && newNameString == null || currentForwardNameString != null && currentForwardNameString != newNameString
			}

			return false
		}

	fun getForwardNameCenterX(): Int {
		return if (currentUser?.id == 0L) {
			avatarImage.centerX.toInt()
		}
		else {
			(forwardNameX + forwardNameCenterX).toInt()
		}
	}

	val checksX: Int
		get() = layoutWidth - AndroidUtilities.dp(if (SharedConfig.bubbleRadius >= 10) 27.3f else 25.3f)

	val checksY: Int
		get() = if (currentMessageObject?.shouldDrawWithoutBackground() == true) {
			(drawTimeY - getThemedDrawable(Theme.key_drawable_msgStickerCheck).intrinsicHeight).toInt()
		}
		else {
			(drawTimeY - Theme.chat_msgMediaCheckDrawable.intrinsicHeight).toInt()
		}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		NotificationCenter.globalInstance.let {
			it.removeObserver(this, NotificationCenter.startSpoilers)
			it.removeObserver(this, NotificationCenter.stopSpoilers)
		}

		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userInfoDidLoad)

		cancelShakeAnimation()

		if (animationRunning) {
			return
		}

		checkBox?.onDetachedFromWindow()
		mediaCheckBox?.onDetachedFromWindow()

		pollCheckBox?.forEach {
			it?.onDetachedFromWindow()
		}

		attachedToWindow = false

		avatarImage.onDetachedFromWindow()

		checkImageReceiversAttachState()

		if (addedForTest && currentUrl != null && currentWebFile != null) {
			ImageLoader.getInstance().removeTestWebFile(currentUrl)
			addedForTest = false
		}

		DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)

		delegate?.textSelectionHelper?.onChatMessageCellDetached(this)

		transitionParams.onDetach()

		if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
			Theme.getCurrentAudiVisualizerDrawable().parentView = null
		}

		statusDrawableAnimator?.removeAllListeners()
		statusDrawableAnimator?.cancel()

		reactionsLayoutInBubble.onDetachFromWindow()

		statusDrawableAnimationInProgress = false

		unregisterFlagSecure?.run()
		unregisterFlagSecure = null
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		NotificationCenter.globalInstance.let {
			it.addObserver(this, NotificationCenter.startSpoilers)
			it.addObserver(this, NotificationCenter.stopSpoilers)
		}

		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userInfoDidLoad)

		currentMessageObject?.animateComments = false

		if (messageObjectToSet != null) {
			messageObjectToSet?.animateComments = false

			setMessageContent(messageObjectToSet, groupedMessagesToSet, bottomNearToSet, topNearToSet)

			messageObjectToSet = null
			groupedMessagesToSet = null
		}

		checkBox?.onAttachedToWindow()

		mediaCheckBox?.onAttachedToWindow()

		pollCheckBox?.forEach {
			it?.onAttachedToWindow()
		}

		attachedToWindow = true
		animationOffsetX = 0f
		slidingOffsetX = 0f
		checkBoxTranslation = 0

		updateTranslation()

		avatarImage.setParentView(parent as View)
		avatarImage.onAttachedToWindow()

		checkImageReceiversAttachState()

		if (currentMessageObject != null) {
			setAvatar(currentMessageObject)
		}

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO && autoPlayingMedia) {
			animatingNoSoundPlaying = MediaController.getInstance().isPlayingMessage(currentMessageObject)
			animatingNoSoundProgress = if (animatingNoSoundPlaying) 0.0f else 1.0f
			animatingNoSound = 0
		}
		else {
			animatingNoSoundPlaying = false
			animatingNoSoundProgress = 0f
			animatingDrawVideoImageButtonProgress = if ((documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) && drawVideoSize) 1.0f else 0.0f
		}

		delegate?.textSelectionHelper?.onChatMessageCellAttached(this)

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			val showSeekbar = MediaController.getInstance().isPlayingMessage(currentMessageObject)
			toSeekBarProgress = if (showSeekbar) 1f else 0f
		}

		reactionsLayoutInBubble.onAttachToWindow()

		updateFlagSecure()

		if (currentMessageObject != null && currentMessageObject!!.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW && unlockLayout != null) {
			invalidate()
		}
	}

	private fun checkImageReceiversAttachState() {
		val newAttachState = attachedToWindow && (visibleOnScreen || !shouldCheckVisibleOnScreen)

		if (newAttachState == imageReceiversAttachState) {
			return
		}

		imageReceiversAttachState = newAttachState

		if (newAttachState) {
			radialProgress.onAttachedToWindow()
			videoRadialProgress.onAttachedToWindow()

			pollAvatarImages?.forEach {
				it?.onAttachedToWindow()
			}

			commentAvatarImages?.forEach {
				it?.onAttachedToWindow()
			}

			replyImageReceiver.onAttachedToWindow()
			locationImageReceiver.onAttachedToWindow()

			if (photoImage.onAttachedToWindow()) {
				if (drawPhotoImage) {
					updateButtonState(ifSame = false, animated = false, fromSet = false)
				}
			}
			else {
				updateButtonState(ifSame = false, animated = false, fromSet = false)
			}

			if (currentMessageObject != null && (isRoundVideo || currentMessageObject!!.isVideo)) {
				checkVideoPlayback(true, null)
			}

			if (currentMessageObject?.mediaExists == false) {
				val canDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject!!.messageOwner)
				val document = currentMessageObject!!.document
				val loadDocumentFromImageReceiver = MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true) || MessageObject.isGifDocument(document) || MessageObject.isRoundVideoDocument(document)

				if (!loadDocumentFromImageReceiver) {
					val photo = if (document == null) FileLoader.getClosestPhotoSizeWithSize(currentMessageObject!!.photoThumbs, AndroidUtilities.getPhotoSize()) else null

					if (canDownload == 2 || canDownload == 1 && currentMessageObject!!.isVideo) {
						if (document != null && !currentMessageObject!!.shouldEncryptPhotoOrVideo() && currentMessageObject!!.canStreamVideo()) {
							FileLoader.getInstance(currentAccount).loadFile(document, currentMessageObject, FileLoader.PRIORITY_NORMAL, 10)
						}
					}
					else if (canDownload != 0) {
						if (document != null) {
							FileLoader.getInstance(currentAccount).loadFile(document, currentMessageObject, FileLoader.PRIORITY_NORMAL, if (MessageObject.isVideoDocument(document) && currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
						}
						else if (photo != null) {
							FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForObject(photo, currentMessageObject!!.photoThumbsObject), currentMessageObject, null, FileLoader.PRIORITY_NORMAL, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
						}
					}

					updateButtonState(ifSame = false, animated = false, fromSet = false)
				}
			}

			animatedEmojiReplyStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, false, animatedEmojiReplyStack, replyTextLayout)
			animatedEmojiDescriptionStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, false, animatedEmojiDescriptionStack, descriptionLayout)

			updateAnimatedEmojis()
		}
		else {
			radialProgress.onDetachedFromWindow()
			videoRadialProgress.onDetachedFromWindow()

			pollAvatarImages?.forEach {
				it?.onDetachedFromWindow()
			}

			commentAvatarImages?.forEach {
				it?.onDetachedFromWindow()
			}

			replyImageReceiver.onDetachedFromWindow()
			locationImageReceiver.onDetachedFromWindow()
			photoImage.onDetachedFromWindow()

			if (currentMessageObject != null && !currentMessageObject!!.mediaExists && !currentMessageObject!!.putInDownloadsStore && !DownloadController.getInstance(currentAccount).isDownloading(currentMessageObject!!.messageOwner!!.id)) {
				val document = currentMessageObject?.document
				val loadDocumentFromImageReceiver = MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true) || MessageObject.isGifDocument(document) || MessageObject.isRoundVideoDocument(document)

				if (!loadDocumentFromImageReceiver) {
					if (document != null) {
						FileLoader.getInstance(currentAccount).cancelLoadFile(document)
					}
					else {
						val photo = FileLoader.getClosestPhotoSizeWithSize(currentMessageObject!!.photoThumbs, AndroidUtilities.getPhotoSize())

						if (photo != null) {
							FileLoader.getInstance(currentAccount).cancelLoadFile(photo)
						}
					}
				}
			}

			AnimatedEmojiSpan.release(animatedEmojiDescriptionStack)
			AnimatedEmojiSpan.release(animatedEmojiReplyStack)
			AnimatedEmojiSpan.release(animatedEmojiStack)
		}
	}

	private fun setMessageContent(messageObject: MessageObject?, groupedMessages: GroupedMessages?, bottomNear: Boolean, topNear: Boolean) {
		if (messageObject == null) {
			return
		}

		isGroup = groupedMessages != null

		if (messageObject.checkLayout() || currentPosition != null && lastHeight != AndroidUtilities.displaySize.y) {
			currentMessageObject = null
		}

		val widthChanged = lastWidth != getParentWidth()

		lastHeight = AndroidUtilities.displaySize.y
		lastWidth = getParentWidth()
		isRoundVideo = messageObject.isRoundVideo == true

		val newReply = if (messageObject.hasValidReplyMessageObject()) messageObject.replyMessageObject?.messageOwner else null
		val messageIdChanged = currentMessageObject == null || currentMessageObject?.id != messageObject.id
		val messageChanged = currentMessageObject !== messageObject || messageObject.forceUpdate || isRoundVideo && isPlayingRound != (MediaController.getInstance().isPlayingMessage(currentMessageObject) && delegate != null && !delegate!!.keyboardIsOpened())
		var dataChanged = currentMessageObject != null && currentMessageObject!!.id == messageObject.id && lastSendState == MessageObject.MESSAGE_SEND_STATE_EDITING && messageObject.isSent || currentMessageObject === messageObject && (isUserDataChanged || photoNotSet) || lastPostAuthor !== messageObject.messageOwner?.postAuthor || wasPinned != isPinned || newReply !== lastReplyMessage
		var groupChanged = groupedMessages !== currentMessagesGroup
		var pollChanged = false

		if (!messageIdChanged && currentMessageObject != null) {
			messageObject.copyStableParams(currentMessageObject!!)
		}

		accessibilityText = null

		if (drawCommentButton || useTranscribeButton || (drawSideButton == 3 && !((hasDiscussion && messageObject.isLinkedToChat(linkedChatId) || isRepliesChat) && ((currentPosition == null) || (currentPosition!!.siblingHeights == null && (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM) != 0) || (currentPosition!!.siblingHeights != null && (currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP) == 0))))) {
			dataChanged = true
		}

		if (!messageChanged && messageObject.isDice) {
			setCurrentDiceValue(isUpdating)
		}

		if (!messageChanged && messageObject.isPoll) {
			var newResults: List<TLRPC.TLPollAnswerVoters>? = null
			var newPoll: TLRPC.TLPoll? = null
			var newVoters = 0

			if (MessageObject.getMedia(messageObject.messageOwner) is TLRPC.TLMessageMediaPoll) {
				val mediaPoll = MessageObject.getMedia(messageObject.messageOwner) as TLRPC.TLMessageMediaPoll
				newResults = mediaPoll.results?.results
				newPoll = mediaPoll.poll
				newVoters = mediaPoll.results?.totalVoters ?: 0
			}

			if (newResults != null && lastPollResults != null && newVoters != lastPollResultsVoters) {
				pollChanged = true
			}

			if (!pollChanged && newResults !== lastPollResults) {
				pollChanged = true
			}

			if (lastPoll !== newPoll && lastPoll!!.closed != newPoll!!.closed) {
				pollChanged = true

				if (!pollVoted) {
					pollVoteInProgress = true
					vibrateOnPollVote = false
				}
			}

			animatePollAvatars = false

			if (pollChanged && attachedToWindow) {
				pollAnimationProgressTime = 0.0f

				if (pollVoted && !messageObject.isVoted) {
					pollUnvoteInProgress = true
				}

				animatePollAvatars = lastPollResultsVoters == 0 || lastPollResultsVoters != 0 && newVoters == 0
			}

			if (!messageIdChanged && newPoll != null && lastPoll!!.quiz && newPoll.quiz && currentMessageObject != null && !pollVoted && messageObject.isVoted) {
				val mediaPoll = MessageObject.getMedia(messageObject.messageOwner) as TLRPC.TLMessageMediaPoll

				if (!mediaPoll.results?.results.isNullOrEmpty()) {
					var chosenAnswer: TLRPC.TLPollAnswerVoters? = null
					val count = mediaPoll.results?.results?.size ?: 0
					var a = 0

					while (a < count) {
						val answer = mediaPoll.results?.results?.get(a)

						if (answer?.chosen == true) {
							chosenAnswer = answer
							break
						}

						a++
					}

					if (chosenAnswer != null) {
						sendAccessibilityEventForVirtualView(POLL_BUTTONS_START + a, AccessibilityEvent.TYPE_VIEW_SELECTED, if (chosenAnswer.correct) context.getString(R.string.AccDescrQuizCorrectAnswer) else context.getString(R.string.AccDescrQuizIncorrectAnswer))
					}
				}
			}
		}

		if (!groupChanged && groupedMessages != null) {
			val newPosition = if (groupedMessages.messages.size > 1) {
				currentMessagesGroup?.positions?.get(currentMessageObject)
			}
			else {
				null
			}

			groupChanged = newPosition !== currentPosition
		}

		if (messageChanged || dataChanged || groupChanged || pollChanged || widthChanged && messageObject.isPoll || isPhotoDataChanged(messageObject) || isPinnedBottom != bottomNear || isPinnedTop != topNear) {
			wasPinned = isPinned
			isPinnedBottom = bottomNear
			isPinnedTop = topNear
			currentMessageObject = messageObject
			currentMessagesGroup = groupedMessages
			lastTime = -2
			lastPostAuthor = messageObject.messageOwner?.postAuthor
			isHighlightedAnimated = false
			widthBeforeNewTimeLine = -1

			if (currentMessagesGroup != null && currentMessagesGroup!!.posArray.size > 1) {
				currentPosition = currentMessagesGroup!!.positions[currentMessageObject]

				if (currentPosition == null) {
					currentMessagesGroup = null
				}
			}
			else {
				currentMessagesGroup = null
				currentPosition = null
			}

			if (currentMessagesGroup == null || currentMessagesGroup!!.isDocuments) {
				drawPinnedTop = isPinnedTop
				drawPinnedBottom = isPinnedBottom
			}
			else {
				drawPinnedTop = isPinnedTop && (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP != 0)
				drawPinnedBottom = isPinnedBottom && (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0)
			}

			isPlayingRound = isRoundVideo && MediaController.getInstance().isPlayingMessage(currentMessageObject) && delegate != null && !delegate!!.keyboardIsOpened() && !delegate!!.isLandscape

			photoImage.setCrossfadeWithOldImage(false)
			photoImage.setCrossfadeDuration(ImageReceiver.DEFAULT_CROSSFADE_DURATION)
			photoImage.setGradientBitmap(null)

			lastSendState = messageObject.messageOwner!!.sendState
			lastDeleteDate = messageObject.messageOwner!!.destroyTime
			lastViewsCount = messageObject.messageOwner!!.views
			lastRepliesCount = repliesCount

			if (messageIdChanged) {
				isPressed = false
				isCheckPressed = true
			}

			gamePreviewPressed = false
			sideButtonPressed = false
			hasNewLineForTime = false
			flipImage = false
			isThreadPost = isThreadChat && messageObject.messageOwner?.fwdFrom != null && messageObject.messageOwner?.fwdFrom?.channelPost != 0
			isAvatarVisible = !isThreadPost && isChat && !messageObject.isOutOwner && messageObject.needDrawAvatar() && (currentPosition == null || currentPosition!!.edge)

			var drawAvatar = isChat && !isThreadPost && !messageObject.isOutOwner && messageObject.needDrawAvatar()

			if (messageObject.customAvatarDrawable != null) {
				isAvatarVisible = true
				drawAvatar = true
			}

			wasLayout = false
			groupPhotoInvisible = false
			animatingDrawVideoImageButton = 0
			drawVideoSize = false
			canStreamVideo = false
			animatingNoSound = 0

			if (MessagesController.getInstance(currentAccount).isChatNoForwards(messageObject.chatId) || messageObject.messageOwner != null && messageObject.messageOwner?.noforwards == true) {
				drawSideButton = 0
			}
			else {
				drawSideButton = if (!isRepliesChat && checkNeedDrawShareButton(messageObject) && (currentPosition == null || currentPosition!!.last)) 1 else 0

				if (isPinnedChat || drawSideButton == 1 && messageObject.messageOwner?.fwdFrom != null && !messageObject.isOutOwner && messageObject.messageOwner?.fwdFrom?.savedFromPeer != null && messageObject.dialogId == getInstance(currentAccount).getClientUserId()) {
					drawSideButton = 2
				}
			}

			replyNameLayout = null
			adminLayout = null
			checkOnlyButtonPressed = false
			replyTextLayout = null

			AnimatedEmojiSpan.release(animatedEmojiReplyStack)

			lastReplyMessage = null
			hasEmbed = false
			autoPlayingMedia = false
			replyNameWidth = 0
			replyTextWidth = 0
			viaWidth = 0
			viaNameWidth = 0
			addedCaptionHeight = 0
			currentReplyPhoto = null
			currentUser = null
			currentChat = null
			currentViaBotUser = null
			instantViewLayout = null
			drawNameLayout = false
			lastLoadingSizeTotal = 0

			if (scheduledInvalidate) {
				AndroidUtilities.cancelRunOnUIThread(invalidateRunnable)
				scheduledInvalidate = false
			}

			links.clear()
			pressedLink = null
			pressedEmoji = null
			pressedLinkType = -1
			messageObject.forceUpdate = false
			drawPhotoImage = false
			drawMediaCheckBox = false
			hasLinkPreview = false
			hasOldCaptionPreview = false
			hasGamePreview = false
			hasInvoicePreview = false
			commentButtonPressed = false
			instantButtonPressed = commentButtonPressed
			instantPressed = instantButtonPressed

			if (!pollChanged) {
				for (drawable in selectorDrawable) {
					if (drawable != null) {
						drawable.setVisible(false, false)
						drawable.state = StateSet.NOTHING
					}
				}
			}

			spoilerPressed = null
			isCaptionSpoilerPressed = false
			isSpoilerRevealing = false
			linkPreviewPressed = false
			buttonPressed = 0
			additionalTimeOffsetY = 0
			miniButtonPressed = 0
			pressedBotButton = -1
			pressedVoteButton = -1
			pollHintPressed = false
			psaHintPressed = false
			linkPreviewHeight = 0
			mediaOffsetY = 0
			documentAttachType = DOCUMENT_ATTACH_TYPE_NONE
			documentAttach = null
			descriptionLayout = null
			titleLayout = null
			videoInfoLayout = null
			photosCountLayout = null
			siteNameLayout = null
			authorLayout = null
			captionLayout = null
			captionWidth = 0
			captionHeight = 0
			captionOffsetX = 0
			currentCaption = null
			docTitleLayout = null
			drawImageButton = false
			drawVideoImageButton = false
			currentPhotoObject = null
			photoParentObject = null
			currentPhotoObjectThumb = null
			currentPhotoObjectThumbStripped = null

			if (messageChanged || messageIdChanged || dataChanged) {
				currentPhotoFilter = null
			}

			buttonState = -1
			miniButtonState = -1
			hasMiniProgress = 0

			if (addedForTest && currentUrl != null && currentWebFile != null) {
				ImageLoader.getInstance().removeTestWebFile(currentUrl)
			}

			addedForTest = false
			photoNotSet = false
			drawBackground = true
			drawName = false
			useSeekBarWaveform = false
			useTranscribeButton = false
			drawInstantView = false
			drawInstantViewType = 0
			drawForwardedName = false
			drawCommentButton = false
			photoImage.setSideClip(0f)
			photoImage.isAspectFit = false
			gradientShader = null
			motionBackgroundDrawable = null
			imageBackgroundColor = 0
			imageBackgroundGradientColor1 = 0
			imageBackgroundGradientColor2 = 0
			imageBackgroundIntensity = 0f
			imageBackgroundGradientColor3 = 0
			imageBackgroundGradientRotation = 45
			imageBackgroundSideColor = 0
			mediaBackground = false
			photoImage.animatedFileDrawableRepeatMaxCount = 0
			hasPsaHint = !messageObject.messageOwner?.fwdFrom?.psaType.isNullOrEmpty()

			if (hasPsaHint) {
				createSelectorDrawable(0)
			}

			photoImage.alpha = 1.0f

			if ((messageChanged || dataChanged) && !pollUnvoteInProgress) {
				pollButtons.clear()
			}

			var captionNewLine = 0

			availableTimeWidth = 0

			photoImage.isForceLoading = false
			photoImage.isNeedsQualityThumb = false
			photoImage.isShouldGenerateQualityThumb = false
			photoImage.setAllowDecodeSingleFrame(false)
			photoImage.setColorFilter(null)
			photoImage.setMediaStartEndTime(-1, -1)

			var canChangeRadius = true

			if (messageIdChanged || messageObject.reactionsChanged) {
				messageObject.reactionsChanged = false

				if (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0) {
					var isSmall = !messageObject.shouldDrawReactionsInLayout()
					val reactions = messageObject.messageOwner?.reactions
					val reactionsResults = reactions?.results

					if (isSmall && messageObject.messageOwner != null && reactions != null && reactionsResults != null) {
						var userCount = 0
						var user2Count = 0

						for (i in reactionsResults.indices) {
							val reactionCount = reactionsResults[i]

							if (reactionCount.count == 2) {
								userCount++
								user2Count++
							}
							else {
								if (reactionCount.chosen) {
									userCount++
								}
								else {
									user2Count++
								}
							}

							if (user2Count >= 2 || userCount >= 2) {
								isSmall = false
								break
							}
						}
					}

					if (currentPosition != null) {
						reactionsLayoutInBubble.setMessage(groupedMessages?.findPrimaryMessageObject(), !messageObject.shouldDrawReactionsInLayout())
					}
					else {
						reactionsLayoutInBubble.setMessage(messageObject, isSmall)
					}
				}
				else {
					reactionsLayoutInBubble.setMessage(null, false)
				}
			}

			if (messageChanged) {
				firstVisibleBlockNum = 0
				lastVisibleBlockNum = 0

				if (currentMessageObject != null && currentMessageObject!!.textLayoutBlocks != null && currentMessageObject!!.textLayoutBlocks!!.size > 1) {
					needNewVisiblePart = true
				}
			}

			var linked = false

			if (currentMessagesGroup != null && currentMessagesGroup!!.messages.size > 0) {
				val `object` = currentMessagesGroup!!.messages[0]

				if (`object`.isLinkedToChat(linkedChatId)) {
					linked = true
				}
			}
			else {
				linked = messageObject.isLinkedToChat(linkedChatId)
			}

			if ((hasDiscussion && linked || isRepliesChat && !messageObject.isOutOwner) && (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0)) {
				val commentCount = repliesCount

				if (!messageObject.shouldDrawWithoutBackground() && !messageObject.isAnimatedEmoji) {
					//MARK: If you want to show the comment button again, make drawCommentButton = true
					drawCommentButton = false

					var avatarsOffset = 0
					val comment: String

					if (commentProgress == null) {
						commentProgress = InfiniteProgress(AndroidUtilities.dp(7f))
					}

					if (isRepliesChat) {
						comment = context.getString(R.string.ViewInChat)
					}
					else {
						comment = if (LocaleController.isRTL) {
							if (commentCount == 0) context.getString(R.string.LeaveAComment) else LocaleController.formatPluralString("CommentsCount", commentCount)
						}
						else {
							if (commentCount == 0) context.getString(R.string.LeaveAComment) else LocaleController.getPluralString("CommentsNoNumber", commentCount)
						}

						val recentRepliers = recentRepliers

						if (commentCount != 0 && !recentRepliers.isNullOrEmpty()) {
							createCommentUI()

							val size = recentRepliers.size

							for (a in commentAvatarImages!!.indices) {
								if (a < size) {
									commentAvatarImages!![a]?.setImageCoordinates(0f, 0f, AndroidUtilities.dp(24f).toFloat(), AndroidUtilities.dp(24f).toFloat())

									val id = MessageObject.getPeerId(recentRepliers[a])
									var user: User? = null
									var chat: TLRPC.Chat? = null

									if (DialogObject.isUserDialog(id)) {
										user = MessagesController.getInstance(currentAccount).getUser(id)
									}
									else if (DialogObject.isChatDialog(id)) {
										chat = MessagesController.getInstance(currentAccount).getChat(-id)
									}

									if (user != null) {
										commentAvatarDrawables?.get(a)?.setInfo(user)
										commentAvatarImages?.get(a)?.setForUserOrChat(user, commentAvatarDrawables?.get(a))
									}
									else if (chat != null) {
										commentAvatarDrawables?.get(a)?.setInfo(chat)
										commentAvatarImages?.get(a)?.setForUserOrChat(chat, commentAvatarDrawables?.get(a))
									}
									else {
										commentAvatarDrawables?.get(a)?.setInfo("", "")
									}

									commentAvatarImagesVisible?.set(a, true)

									avatarsOffset += if (a == 0) 2 else 17
								}
								else if (size != 0) {
									commentAvatarImages?.get(a)?.setImageBitmap(null as Drawable?)
									commentAvatarImagesVisible?.set(a, false)
								}
							}
						}
						else if (commentAvatarImages != null) {
							for (a in commentAvatarImages!!.indices) {
								commentAvatarImages!![a]!!.setImageBitmap(null as Drawable?)
								commentAvatarImagesVisible!![a] = false
							}
						}
					}

					totalCommentWidth = ceil(Theme.chat_replyNamePaint.measureText(comment).toDouble()).toInt()
					commentWidth = totalCommentWidth
					commentLayout = StaticLayout(comment, Theme.chat_replyNamePaint, commentWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

					if (commentCount != 0 && !LocaleController.isRTL) {
						drawCommentNumber = true

						if (commentNumberLayout == null) {
							commentNumberLayout = AnimatedNumberLayout(this, Theme.chat_replyNamePaint)
							commentNumberLayout?.setNumber(commentCount, false)
						}
						else {
							commentNumberLayout?.setNumber(commentCount, messageObject.animateComments)
						}

						messageObject.animateComments = false

						commentNumberWidth = commentNumberLayout!!.width
						totalCommentWidth += commentNumberWidth + AndroidUtilities.dp(4f)
					}
					else {
						drawCommentNumber = false
						commentNumberLayout?.setNumber(1, false)
					}

					totalCommentWidth += AndroidUtilities.dp((70 + avatarsOffset).toFloat())
				}
				else {
					if (!isRepliesChat && commentCount > 0) {
						val comment = LocaleController.formatShortNumber(commentCount, null)
						totalCommentWidth = ceil(Theme.chat_stickerCommentCountPaint.measureText(comment).toDouble()).toInt()
						commentWidth = totalCommentWidth
						commentLayout = StaticLayout(comment, Theme.chat_stickerCommentCountPaint, commentWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
					}
					else {
						commentLayout = null
					}

					drawCommentNumber = false
					drawSideButton = if (isRepliesChat) 2 else 3
				}
			}
			else {
				commentLayout = null
				drawCommentNumber = false
			}

			if (messageObject.type == MessageObject.TYPE_COMMON) {
				drawForwardedName = !isRepliesChat

				var maxWidth: Int

				if (drawAvatar) {
					maxWidth = if (AndroidUtilities.isTablet()) {
						AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122f)
					}
					else {
						min(getParentWidth(), AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122f)
					}

					drawName = true
				}
				else {
					maxWidth = if (AndroidUtilities.isTablet()) {
						AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80f)
					}
					else {
						min(getParentWidth(), AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80f)
					}

					drawName = (isPinnedChat || messageObject.messageOwner?.peerId?.channelId != 0L && (!messageObject.isOutOwner || messageObject.isSupergroup) || messageObject.isImportedForward) && messageObject.messageOwner?.fwdFrom?.fromId == null
				}

				availableTimeWidth = maxWidth

				if (messageObject.isRoundVideo) {
					availableTimeWidth -= (ceil(Theme.chat_audioTimePaint.measureText("00:00").toDouble()) + if (messageObject.isOutOwner) 0 else AndroidUtilities.dp(64f)).toInt()
				}

				measureTime(messageObject)

				var timeMore = timeWidth + AndroidUtilities.dp(6f)

				if (messageObject.isOutOwner) {
					timeMore += AndroidUtilities.dp(20.5f)
				}

				timeMore += extraTimeX

				hasGamePreview = MessageObject.getMedia(messageObject.messageOwner) is TLRPC.TLMessageMediaGame && MessageObject.getMedia(messageObject.messageOwner)?.game is TLRPC.TLGame
				hasInvoicePreview = MessageObject.getMedia(messageObject.messageOwner) is TLRPC.TLMessageMediaInvoice
				hasLinkPreview = !messageObject.isRestrictedMessage && MessageObject.getMedia(messageObject.messageOwner) is TLRPC.TLMessageMediaWebPage && MessageObject.getMedia(messageObject.messageOwner)?.webpage is TLRPC.TLWebPage
				drawInstantView = hasLinkPreview && MessageObject.getMedia(messageObject.messageOwner)?.webpage?.cachedPage != null

				var siteName = if (hasLinkPreview) MessageObject.getMedia(messageObject.messageOwner)?.webpage?.siteName else null

				hasEmbed = hasLinkPreview && !TextUtils.isEmpty(MessageObject.getMedia(messageObject.messageOwner)?.webpage?.embedUrl) && !messageObject.isGif && !"instagram".equals(siteName, ignoreCase = true) // MARK: was `instangram`

				var slideshow = false
				val webpageType = if (hasLinkPreview) MessageObject.getMedia(messageObject.messageOwner)?.webpage?.type else null
				var androidThemeDocument: TLRPC.Document? = null
				var androidThemeSettings: TLRPC.TLThemeSettings? = null

				if (!drawInstantView) {
					if ("telegram_livestream" == webpageType) {
						drawInstantView = true
						drawInstantViewType = 11
					}
					else if ("telegram_voicechat" == webpageType) {
						drawInstantView = true
						drawInstantViewType = 9
					}
					else if ("telegram_channel" == webpageType) {
						drawInstantView = true
						drawInstantViewType = 1
					}
					else if ("telegram_user" == webpageType) {
						drawInstantView = true
						drawInstantViewType = 13
					}
					else if ("telegram_megagroup" == webpageType) {
						drawInstantView = true
						drawInstantViewType = 2
					}
					else if ("telegram_message" == webpageType) {
						drawInstantView = true
						drawInstantViewType = 3
					}
					else if ("telegram_theme" == webpageType) {
						var b = 0
						val n2 = MessageObject.getMedia(messageObject.messageOwner)?.webpage?.attributes?.size ?: 0

						while (b < n2) {
							val attribute = MessageObject.getMedia(messageObject.messageOwner)?.webpage?.attributes?.getOrNull(b)
							val documents = attribute?.documents
							var a = 0
							val n = documents?.size ?: 0

							while (a < n) {
								val document = documents?.get(a)

								if ("application/x-tgtheme-android" == document?.mimeType) {
									drawInstantView = true
									drawInstantViewType = 7
									androidThemeDocument = document
									break
								}

								a++
							}

							if (drawInstantView) {
								break
							}

							if (attribute?.settings != null) {
								drawInstantView = true
								drawInstantViewType = 7
								androidThemeSettings = attribute.settings
								break
							}

							b++
						}
					}
					else if ("telegram_background" == webpageType) {
						drawInstantView = true
						drawInstantViewType = 6

						try {
							val url = MessageObject.getMedia(messageObject.messageOwner)?.webpage?.url?.toUri()

							imageBackgroundIntensity = Utilities.parseInt(url?.getQueryParameter("intensity")).toFloat()

							var bgColor = url?.getQueryParameter("bg_color")
							val rotation = url?.getQueryParameter("rotation")

							if (rotation != null) {
								imageBackgroundGradientRotation = Utilities.parseInt(rotation)
							}

							if (bgColor.isNullOrEmpty()) {
								val document = messageObject.document

								if (document != null && "image/png" == document.mimeType) {
									bgColor = "ffffff"
								}

								if (imageBackgroundIntensity == 0f) {
									imageBackgroundIntensity = 50f
								}
							}

							if (!bgColor.isNullOrEmpty()) {
								imageBackgroundColor = bgColor.substring(0, 6).toInt(16) or -0x1000000

								var averageColor = imageBackgroundColor

								if (bgColor.length >= 13 && AndroidUtilities.isValidWallChar(bgColor[6])) {
									imageBackgroundGradientColor1 = bgColor.substring(7, 13).toInt(16) or -0x1000000
									averageColor = AndroidUtilities.getAverageColor(imageBackgroundColor, imageBackgroundGradientColor1)
								}

								if (bgColor.length >= 20 && AndroidUtilities.isValidWallChar(bgColor[13])) {
									imageBackgroundGradientColor2 = bgColor.substring(14, 20).toInt(16) or -0x1000000
								}

								if (bgColor.length == 27 && AndroidUtilities.isValidWallChar(bgColor[20])) {
									imageBackgroundGradientColor3 = bgColor.substring(21).toInt(16) or -0x1000000
								}

								imageBackgroundSideColor = if (imageBackgroundIntensity < 0) {
									-0xeeeeef
								}
								else {
									AndroidUtilities.getPatternSideColor(averageColor)
								}

								photoImage.setColorFilter(PorterDuffColorFilter(AndroidUtilities.getPatternColor(averageColor), PorterDuff.Mode.SRC_IN))
								photoImage.alpha = abs(imageBackgroundIntensity) / 100.0f
							}
							else {
								val color = url?.lastPathSegment

								if (color != null && color.length >= 6) {
									imageBackgroundColor = color.substring(0, 6).toInt(16) or -0x1000000

									if (color.length >= 13 && AndroidUtilities.isValidWallChar(color[6])) {
										imageBackgroundGradientColor1 = color.substring(7, 13).toInt(16) or -0x1000000
									}

									if (color.length >= 20 && AndroidUtilities.isValidWallChar(color[13])) {
										imageBackgroundGradientColor2 = color.substring(14, 20).toInt(16) or -0x1000000
									}

									if (color.length == 27 && AndroidUtilities.isValidWallChar(color[20])) {
										imageBackgroundGradientColor3 = color.substring(21).toInt(16) or -0x1000000
									}

									currentPhotoObject = TLRPC.TLPhotoSizeEmpty()
									currentPhotoObject?.type = "s"
									currentPhotoObject?.w = AndroidUtilities.dp(180f)
									currentPhotoObject?.h = AndroidUtilities.dp(150f)
									currentPhotoObject?.location = TLRPC.TLFileLocationUnavailable()
								}
							}
						}
						catch (ignore: Exception) {
						}
					}
				}
				else if (siteName != null) {
					siteName = siteName.lowercase()

					if (siteName == "instagram" || siteName == "twitter" || "telegram_album" == webpageType && MessageObject.getMedia(messageObject.messageOwner)?.webpage?.cachedPage is TLRPC.TLPage && (MessageObject.getMedia(messageObject.messageOwner)?.webpage?.photo is TLRPC.TLPhoto || MessageObject.isVideoDocument(MessageObject.getMedia(messageObject.messageOwner)?.webpage?.document))) {
						drawInstantView = false

						slideshow = true

						val blocks = MessageObject.getMedia(messageObject.messageOwner)?.webpage?.cachedPage?.blocks
						var count = 1

						if (blocks != null) {
							for (a in blocks.indices) {
								val block = blocks[a]

								if (block is TLRPC.TLPageBlockSlideshow) {
									count = block.items.size
								}
								else if (block is TLRPC.TLPageBlockCollage) {
									count = block.items.size
								}
							}
						}

						val str = LocaleController.formatString("Of", R.string.Of, 1, count)

						photosCountWidth = ceil(Theme.chat_durationPaint.measureText(str).toDouble()).toInt()
						photosCountLayout = StaticLayout(str, Theme.chat_durationPaint, photosCountWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
					}
				}

				backgroundWidth = maxWidth

				if (hasLinkPreview || hasGamePreview || hasInvoicePreview || maxWidth - messageObject.lastLineWidth < timeMore) {
					backgroundWidth = max(backgroundWidth, messageObject.lastLineWidth) + AndroidUtilities.dp(31f)
					backgroundWidth = max(backgroundWidth, timeWidth + AndroidUtilities.dp(31f))
				}
				else {
					val diff = backgroundWidth - messageObject.lastLineWidth

					backgroundWidth = if (diff in 0..timeMore) {
						backgroundWidth + timeMore - diff + AndroidUtilities.dp(31f)
					}
					else {
						max(backgroundWidth, messageObject.lastLineWidth + timeMore) + AndroidUtilities.dp(31f)
					}
				}

				availableTimeWidth = backgroundWidth - AndroidUtilities.dp(31f)

				if (messageObject.isRoundVideo) {
					availableTimeWidth -= (ceil(Theme.chat_audioTimePaint.measureText("00:00").toDouble()) + if (messageObject.isOutOwner) 0 else AndroidUtilities.dp(64f)).toInt()
				}

				setMessageObjectInternal(messageObject)

				backgroundWidth = messageObject.textWidth + extraTextX * 2 + if (hasGamePreview || hasInvoicePreview) AndroidUtilities.dp(10f) else 0
				totalHeight = messageObject.textHeight + AndroidUtilities.dp(19.5f) + namesOffset

				if (!shouldDrawTimeOnMedia()) {
					totalHeight += CELL_HEIGHT_INCREASE
				}

				if (!reactionsLayoutInBubble.isSmall) {
					reactionsLayoutInBubble.measure(maxWidth, Gravity.LEFT)

					if (!reactionsLayoutInBubble.isEmpty) {
						reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(8f)

						if (reactionsLayoutInBubble.width > backgroundWidth) {
							backgroundWidth = reactionsLayoutInBubble.width
						}

						totalHeight += reactionsLayoutInBubble.totalHeight
					}
				}

				if (drawPinnedTop) {
					namesOffset -= AndroidUtilities.dp(1f)
				}

				var maxChildWidth = max(backgroundWidth, nameWidth)
				maxChildWidth = max(maxChildWidth, forwardedNameWidth)
				maxChildWidth = max(maxChildWidth, replyNameWidth)
				maxChildWidth = max(maxChildWidth, replyTextWidth)

				if (commentLayout != null && drawSideButton != 3) {
					maxChildWidth = max(maxChildWidth, totalCommentWidth)
				}

				var maxWebWidth = 0

				if (hasLinkPreview || hasGamePreview || hasInvoicePreview) {
					var linkPreviewMaxWidth: Int

					linkPreviewMaxWidth = if (AndroidUtilities.isTablet()) {
						if (drawAvatar) {
							AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp((80 + 52).toFloat())
						}
						else {
							AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80f)
						}
					}
					else {
						if (drawAvatar) {
							getParentWidth() - AndroidUtilities.dp((80 + 52).toFloat())
						}
						else {
							getParentWidth() - AndroidUtilities.dp(80f)
						}
					}

					if (drawSideButton != 0) {
						linkPreviewMaxWidth -= AndroidUtilities.dp(20f)
					}

					var site_name: String?
					val title: String?
					var author: CharSequence?
					val description: String?
					val photo: TLRPC.Photo?
					val document: TLRPC.Document?
					var webDocument: WebFile?
					val duration: Int
					var smallImage: Boolean
					val type: String?
					val smallImageSide = AndroidUtilities.dp(48f)
					val smallSideMargin = AndroidUtilities.dp(10f)

					if (hasLinkPreview) {
						val webPage = MessageObject.getMedia(messageObject.messageOwner)!!.webpage as TLRPC.TLWebPage
						site_name = webPage.siteName
						title = if (drawInstantViewType != 6 && drawInstantViewType != 7) webPage.title else null
						author = if (drawInstantViewType != 6 && drawInstantViewType != 7) webPage.author else null
						description = if (drawInstantViewType != 6 && drawInstantViewType != 7) webPage.description else null
						photo = webPage.photo
						webDocument = null

						document = if (drawInstantViewType == 7) {
							androidThemeSettings?.let { ThemeDocument(it) } ?: androidThemeDocument
						}
						else {
							webPage.document
						}

						type = webPage.type
						duration = webPage.duration

						if (site_name != null && photo != null && site_name.equals("instagram", ignoreCase = true)) {
							linkPreviewMaxWidth = max(AndroidUtilities.displaySize.y / 3, currentMessageObject!!.textWidth)
						}

						val isSmallImageType = "app" == type || "profile" == type || "article" == type || "telegram_bot" == type || "telegram_user" == type || "telegram_channel" == type || "telegram_megagroup" == type || "telegram_voicechat" == type || "telegram_livestream" == type
						smallImage = !slideshow && (!drawInstantView || drawInstantViewType == 1 || drawInstantViewType == 9 || drawInstantViewType == 11 || drawInstantViewType == 13) && document == null && isSmallImageType

						isSmallImage = smallImage && type != null && currentMessageObject?.photoThumbs != null
					}
					else if (hasInvoicePreview) {
						val invoice = MessageObject.getMedia(messageObject.messageOwner) as TLRPC.TLMessageMediaInvoice
						site_name = MessageObject.getMedia(messageObject.messageOwner)?.title
						title = null
						description = null
						photo = null
						author = null
						document = null

						webDocument = if (invoice.photo is TLRPC.TLWebDocument) {
							WebFile.createWithWebDocument(invoice.photo)
						}
						else {
							null
						}

						duration = 0
						type = "invoice"

						isSmallImage = false
						smallImage = false
					}
					else {
						val game = MessageObject.getMedia(messageObject.messageOwner)?.game
						site_name = game?.title
						title = null
						webDocument = null
						description = if (TextUtils.isEmpty(messageObject.messageText)) game?.description else null
						photo = game?.photo
						author = null
						document = game?.document
						duration = 0
						type = "game"
						isSmallImage = false
						smallImage = false
					}

					if (drawInstantViewType == 11) {
						site_name = context.getString(R.string.VoipChannelVoiceChat)
					}
					else if (drawInstantViewType == 9) {
						site_name = context.getString(R.string.VoipGroupVoiceChat)
					}
					else if (drawInstantViewType == 6) {
						site_name = context.getString(R.string.ChatBackground)
					}
					else if ("telegram_theme" == webpageType) {
						site_name = context.getString(R.string.ColorTheme)
					}

					val additionalWidth = if (hasInvoicePreview) 0 else AndroidUtilities.dp(10f)
					var restLinesCount = 3

					linkPreviewMaxWidth -= additionalWidth

					if (currentMessageObject?.photoThumbs == null && photo != null) {
						currentMessageObject?.generateThumbs(true)
					}

					if (site_name != null) {
						try {
							var width = ceil((Theme.chat_replyNamePaint.measureText(site_name) + 1).toDouble()).toInt()
							var restLines = 0

							if (!isSmallImage) {
								siteNameLayout = StaticLayout(site_name, Theme.chat_replyNamePaint, min(width, linkPreviewMaxWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							}
							else {
								restLines = restLinesCount
								siteNameLayout = generateStaticLayout(site_name, Theme.chat_replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - smallImageSide - smallSideMargin, restLinesCount, 1)
								restLinesCount -= siteNameLayout!!.lineCount
							}

							siteNameRtl = max(siteNameLayout!!.getLineLeft(0), 0f) != 0f

							val height = siteNameLayout!!.getLineBottom(siteNameLayout!!.lineCount - 1)

							linkPreviewHeight += height
							totalHeight += height

							var layoutWidth = 0

							for (a in 0 until siteNameLayout!!.lineCount) {
								val lineLeft = max(0f, siteNameLayout!!.getLineLeft(a)).toInt()
								var lineWidth: Int

								if (lineLeft != 0) {
									lineWidth = siteNameLayout!!.width - lineLeft
								}
								else {
									var max = linkPreviewMaxWidth

									if (a < restLines || lineLeft != 0 && isSmallImage) {
										max -= smallImageSide + smallSideMargin
									}

									lineWidth = min(max.toDouble(), ceil(siteNameLayout!!.getLineWidth(a).toDouble())).toInt()
								}

								if (a < restLines || lineLeft != 0 && isSmallImage) {
									lineWidth += smallImageSide + smallSideMargin
								}

								layoutWidth = max(layoutWidth, lineWidth)
							}

							width = layoutWidth
							siteNameWidth = width
							maxChildWidth = max(maxChildWidth, width + additionalWidth)
							maxWebWidth = max(maxWebWidth, width + additionalWidth)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					var titleIsRTL = false

					if (title != null) {
						try {
							titleX = Int.MAX_VALUE

							if (linkPreviewHeight != 0) {
								linkPreviewHeight += AndroidUtilities.dp(2f)
								totalHeight += AndroidUtilities.dp(2f)
							}

							var restLines = 0

							if (!isSmallImage) {
								titleLayout = StaticLayoutEx.createStaticLayout(title, Theme.chat_replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1f).toFloat(), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 4)
							}
							else {
								restLines = restLinesCount
								titleLayout = generateStaticLayout(title, Theme.chat_replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - smallImageSide - smallSideMargin, restLinesCount, 4)
								restLinesCount -= titleLayout!!.lineCount
							}

							val height = titleLayout!!.getLineBottom(titleLayout!!.lineCount - 1)
							linkPreviewHeight += height
							totalHeight += height

							for (a in 0 until titleLayout!!.lineCount) {
								val lineLeft = max(0f, titleLayout!!.getLineLeft(a)).toInt()

								if (lineLeft != 0) {
									titleIsRTL = true
								}

								titleX = if (titleX == Int.MAX_VALUE) {
									-lineLeft
								}
								else {
									max(titleX, -lineLeft)
								}

								var width: Int

								if (lineLeft != 0) {
									width = titleLayout!!.width - lineLeft
								}
								else {
									var max = linkPreviewMaxWidth

									if (a < restLines || lineLeft != 0 && isSmallImage) {
										max -= smallImageSide + smallSideMargin
									}

									width = min(max.toDouble(), ceil(titleLayout!!.getLineWidth(a).toDouble())).toInt()
								}

								if (a < restLines || lineLeft != 0 && isSmallImage) {
									width += smallImageSide + smallSideMargin
								}

								maxChildWidth = max(maxChildWidth, width + additionalWidth)
								maxWebWidth = max(maxWebWidth, width + additionalWidth)
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}

						if (titleIsRTL && isSmallImage) {
							linkPreviewMaxWidth -= AndroidUtilities.dp(48f)
						}
					}

					var authorIsRTL = false

					if (author != null && title == null) {
						try {
							if (linkPreviewHeight != 0) {
								linkPreviewHeight += AndroidUtilities.dp(2f)
								totalHeight += AndroidUtilities.dp(2f)
							}

							try {
								author = Emoji.replaceEmoji(author, Theme.chat_replyNamePaint.fontMetricsInt, false)
							}
							catch (e: Exception) {
								// ignored
							}

							if (restLinesCount == 3 && (!isSmallImage || description == null)) {
								authorLayout = StaticLayout(author, Theme.chat_replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							}
							else {
								authorLayout = generateStaticLayout(author!!, Theme.chat_replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - smallImageSide - smallSideMargin, restLinesCount, 1)
								restLinesCount -= authorLayout!!.lineCount
							}

							val height = authorLayout!!.getLineBottom(authorLayout!!.lineCount - 1)

							linkPreviewHeight += height
							totalHeight += height

							val lineLeft = max(authorLayout!!.getLineLeft(0), 0f).toInt()

							authorX = -lineLeft

							val width: Int

							if (lineLeft != 0) {
								width = authorLayout!!.width - lineLeft
								authorIsRTL = true
							}
							else {
								width = ceil(authorLayout!!.getLineWidth(0).toDouble()).toInt()
							}

							maxChildWidth = max(maxChildWidth, width + additionalWidth)
							maxWebWidth = max(maxWebWidth, width + additionalWidth)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					if (description != null) {
						try {
							descriptionX = 0
							currentMessageObject?.generateLinkDescription()

							if (linkPreviewHeight != 0) {
								linkPreviewHeight += AndroidUtilities.dp(2f)
								totalHeight += AndroidUtilities.dp(2f)
							}

							var restLines = 0
							val allowAllLines = site_name != null && site_name.equals("twitter", ignoreCase = true)

							if (restLinesCount == 3 && !isSmallImage) {
								descriptionLayout = StaticLayoutEx.createStaticLayout(messageObject.linkDescription, Theme.chat_replyTextPaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1f).toFloat(), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, if (allowAllLines) 100 else 6)
							}
							else {
								restLines = restLinesCount
								descriptionLayout = generateStaticLayout(messageObject.linkDescription ?: "", Theme.chat_replyTextPaint, linkPreviewMaxWidth, linkPreviewMaxWidth - smallImageSide - smallSideMargin, restLinesCount, if (allowAllLines) 100 else 6)
							}

							animatedEmojiDescriptionStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, false, animatedEmojiDescriptionStack, descriptionLayout)

							val height = descriptionLayout!!.getLineBottom(descriptionLayout!!.lineCount - 1)

							linkPreviewHeight += height
							totalHeight += height

							var hasRTL = false

							for (a in 0 until descriptionLayout!!.lineCount) {
								val lineLeft = ceil(descriptionLayout!!.getLineLeft(a).toDouble()).toInt()

								if (lineLeft > 0) {
									hasRTL = true

									descriptionX = if (descriptionX == 0) {
										-lineLeft
									}
									else {
										max(descriptionX, -lineLeft)
									}
								}
							}

							val textWidth = descriptionLayout!!.width

							for (a in 0 until descriptionLayout!!.lineCount) {
								val lineLeft = ceil(descriptionLayout!!.getLineLeft(a).toDouble()).toInt()

								if (lineLeft == 0 && descriptionX != 0) {
									descriptionX = 0
								}

								var width = if (lineLeft > 0) {
									textWidth - lineLeft
								}
								else {
									if (hasRTL) {
										textWidth
									}
									else {
										min(ceil(descriptionLayout!!.getLineWidth(a).toDouble()).toInt(), textWidth)
									}
								}

								if (a < restLines || restLines != 0 && lineLeft != 0 && isSmallImage) {
									width += smallImageSide + smallSideMargin
								}

								if (maxWebWidth < width + additionalWidth) {
									if (titleIsRTL) {
										titleX += width + additionalWidth - maxWebWidth
									}

									if (authorIsRTL) {
										authorX += width + additionalWidth - maxWebWidth
									}

									maxWebWidth = width + additionalWidth
								}

								maxChildWidth = max(maxChildWidth, width + additionalWidth)
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					if (smallImage && descriptionLayout == null && titleLayout == null) {
						smallImage = false
						isSmallImage = false
					}

					var maxPhotoWidth = if (smallImage) smallImageSide else linkPreviewMaxWidth

					if (document is TLRPC.TLDocument) {
						if (MessageObject.isRoundVideoDocument(document)) {
							currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)
							photoParentObject = document
							documentAttach = document
							documentAttachType = DOCUMENT_ATTACH_TYPE_ROUND
						}
						else if (MessageObject.isGifDocument(document, messageObject.hasValidGroupId())) {
							if (!messageObject.isGame && !SharedConfig.autoplayGifs) {
								messageObject.gifState = 1f
							}

							photoImage.allowStartAnimation = messageObject.gifState != 1f
							currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)

							if (currentPhotoObject != null) {
								photoParentObject = document
							}
							else if (photo != null) {
								currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 90)
								photoParentObject = photo
							}

							if (currentPhotoObject != null && (currentPhotoObject!!.w == 0 || currentPhotoObject!!.h == 0)) {
								for (a in document.attributes.indices) {
									val attribute = document.attributes[a]

									if (attribute is TLRPC.TLDocumentAttributeImageSize || attribute is TLRPC.TLDocumentAttributeVideo) {
										currentPhotoObject?.w = attribute.w
										currentPhotoObject?.h = attribute.h
										break
									}
								}

								if (currentPhotoObject!!.w == 0 || currentPhotoObject!!.h == 0) {
									currentPhotoObject?.h = AndroidUtilities.dp(150f)
									currentPhotoObject?.w = currentPhotoObject!!.h
								}
							}

							documentAttach = document
							documentAttachType = DOCUMENT_ATTACH_TYPE_GIF
						}
						else if (MessageObject.isVideoDocument(document)) {
							if (photo != null) {
								currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize(), true)
								currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 40)
								photoParentObject = photo
							}

							if (currentPhotoObject == null) {
								currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320)
								currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 40)
								photoParentObject = document
							}

							if (currentPhotoObject === currentPhotoObjectThumb) {
								currentPhotoObjectThumb = null
							}

							if (currentMessageObject!!.strippedThumb != null) {
								currentPhotoObjectThumb = null
								currentPhotoObjectThumbStripped = currentMessageObject!!.strippedThumb
							}

							if (currentPhotoObject == null) {
								currentPhotoObject = TLRPC.TLPhotoSize()
								currentPhotoObject?.type = "s"
								currentPhotoObject?.location = TLRPC.TLFileLocationUnavailable()
							}

							if (currentPhotoObject != null && (currentPhotoObject?.w == 0 || currentPhotoObject?.h == 0 || currentPhotoObject is TLRPC.TLPhotoStrippedSize)) {
								for (a in document.attributes.indices) {
									val attribute = document.attributes[a]

									if (attribute is TLRPC.TLDocumentAttributeVideo) {
										if (currentPhotoObject is TLRPC.TLPhotoStrippedSize) {
											val scale = max(attribute.w, attribute.w) / 50.0f
											currentPhotoObject?.w = (attribute.w / scale).toInt()
											currentPhotoObject?.h = (attribute.h / scale).toInt()
										}
										else {
											currentPhotoObject?.w = attribute.w
											currentPhotoObject?.h = attribute.h
										}
										break
									}
								}

								if (currentPhotoObject?.w == 0 || currentPhotoObject?.h == 0) {
									currentPhotoObject?.h = AndroidUtilities.dp(150f)
									currentPhotoObject?.w = currentPhotoObject!!.h
								}
							}
							createDocumentLayout(0, messageObject)
						}
						else if (MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true)) {
							currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)
							photoParentObject = document

							if (currentPhotoObject != null && (currentPhotoObject!!.w == 0 || currentPhotoObject!!.h == 0)) {
								for (a in document.attributes.indices) {
									val attribute = document.attributes[a]

									if (attribute is TLRPC.TLDocumentAttributeImageSize) {
										currentPhotoObject?.w = attribute.w
										currentPhotoObject?.h = attribute.h
										break
									}
								}

								if (currentPhotoObject?.w == 0 || currentPhotoObject?.h == 0) {
									currentPhotoObject?.h = AndroidUtilities.dp(150f)
									currentPhotoObject?.w = currentPhotoObject!!.h
								}
							}

							documentAttach = document
							documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER
						}
						else if (drawInstantViewType == 6) {
							currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320)
							photoParentObject = document

							if (currentPhotoObject != null && (currentPhotoObject!!.w == 0 || currentPhotoObject!!.h == 0)) {
								for (a in document.attributes.indices) {
									val attribute = document.attributes[a]

									if (attribute is TLRPC.TLDocumentAttributeImageSize) {
										currentPhotoObject?.w = attribute.w
										currentPhotoObject?.h = attribute.h
										break
									}
								}

								if (currentPhotoObject?.w == 0 || currentPhotoObject?.h == 0) {
									currentPhotoObject?.h = AndroidUtilities.dp(150f)
									currentPhotoObject?.w = currentPhotoObject!!.h
								}
							}

							documentAttach = document
							documentAttachType = DOCUMENT_ATTACH_TYPE_WALLPAPER

							val str = AndroidUtilities.formatFileSize(documentAttach!!.size)

							durationWidth = ceil(Theme.chat_durationPaint.measureText(str).toDouble()).toInt()
							videoInfoLayout = StaticLayout(str, Theme.chat_durationPaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
						}
						else if (drawInstantViewType == 7) {
							currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 700)

							if (currentMessageObject?.strippedThumb == null) {
								currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 40)
							}
							else {
								currentPhotoObjectThumbStripped = currentMessageObject!!.strippedThumb
							}

							photoParentObject = document

							if (currentPhotoObject != null && (currentPhotoObject!!.w == 0 || currentPhotoObject!!.h == 0)) {
								for (a in document.attributes.indices) {
									val attribute = document.attributes[a]

									if (attribute is TLRPC.TLDocumentAttributeImageSize) {
										currentPhotoObject?.w = attribute.w
										currentPhotoObject?.h = attribute.h
										break
									}
								}

								if (currentPhotoObject?.w == 0 || currentPhotoObject?.h == 0) {
									currentPhotoObject?.h = AndroidUtilities.dp(150f)
									currentPhotoObject?.w = currentPhotoObject!!.h
								}
							}

							documentAttach = document
							documentAttachType = DOCUMENT_ATTACH_TYPE_THEME
						}
						else {
							calcBackgroundWidth(maxWidth, timeMore, maxChildWidth)

							if (backgroundWidth < maxWidth + AndroidUtilities.dp(20f)) {
								backgroundWidth = maxWidth + AndroidUtilities.dp(20f)
							}

							if (MessageObject.isVoiceDocument(document)) {
								createDocumentLayout(backgroundWidth - AndroidUtilities.dp(10f), messageObject)

								mediaOffsetY = currentMessageObject!!.textHeight + AndroidUtilities.dp(8f) + linkPreviewHeight
								totalHeight += AndroidUtilities.dp((30 + 14).toFloat())
								linkPreviewHeight += AndroidUtilities.dp(44f)
								maxWidth -= AndroidUtilities.dp(86f)

								maxChildWidth = if (AndroidUtilities.isTablet()) {
									max(maxChildWidth, min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 52f else 0f), AndroidUtilities.dp(220f)) - AndroidUtilities.dp(30f) + additionalWidth)
								}
								else {
									max(maxChildWidth, min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 52f else 0f), AndroidUtilities.dp(220f)) - AndroidUtilities.dp(30f) + additionalWidth)
								}

								calcBackgroundWidth(maxWidth, timeMore, maxChildWidth)
							}
							else if (MessageObject.isMusicDocument(document)) {
								val durationWidth = createDocumentLayout(backgroundWidth - AndroidUtilities.dp(10f), messageObject)

								mediaOffsetY = currentMessageObject!!.textHeight + AndroidUtilities.dp(8f) + linkPreviewHeight
								totalHeight += AndroidUtilities.dp((42 + 14).toFloat())
								linkPreviewHeight += AndroidUtilities.dp(56f)
								maxWidth -= AndroidUtilities.dp(86f)
								maxChildWidth = max(maxChildWidth, durationWidth + additionalWidth + AndroidUtilities.dp((86 + 8).toFloat()))

								if (songLayout != null && songLayout!!.lineCount > 0) {
									maxChildWidth = max(maxChildWidth.toFloat(), songLayout!!.getLineWidth(0) + additionalWidth + AndroidUtilities.dp(86f)).toInt()
								}

								if (performerLayout != null && performerLayout!!.lineCount > 0) {
									maxChildWidth = max(maxChildWidth.toFloat(), performerLayout!!.getLineWidth(0) + additionalWidth + AndroidUtilities.dp(86f)).toInt()
								}

								calcBackgroundWidth(maxWidth, timeMore, maxChildWidth)
							}
							else {
								createDocumentLayout(backgroundWidth - AndroidUtilities.dp((86 + 24 + 58).toFloat()), messageObject)

								drawImageButton = true

								if (drawPhotoImage) {
									totalHeight += AndroidUtilities.dp((86 + 14).toFloat())
									linkPreviewHeight += AndroidUtilities.dp(86f)
									photoImage.setImageCoordinates(0f, (totalHeight + namesOffset).toFloat(), AndroidUtilities.dp(86f).toFloat(), AndroidUtilities.dp(86f).toFloat())
								}
								else {
									mediaOffsetY = currentMessageObject!!.textHeight + AndroidUtilities.dp(8f) + linkPreviewHeight
									photoImage.setImageCoordinates(0f, (totalHeight + namesOffset - AndroidUtilities.dp(14f)).toFloat(), AndroidUtilities.dp(56f).toFloat(), AndroidUtilities.dp(56f).toFloat())
									totalHeight += AndroidUtilities.dp((50 + 14).toFloat())
									linkPreviewHeight += AndroidUtilities.dp(50f)

									docTitleLayout?.let {
										if (it.lineCount > 1) {
											val h = (it.lineCount - 1) * AndroidUtilities.dp(16f)
											totalHeight += h
											linkPreviewHeight += h
										}
									}
								}
							}
						}
					}
					else if (photo != null) {
						val isPhoto = type != null && type == "photo"

						currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, if (isPhoto || !smallImage) AndroidUtilities.getPhotoSize() else maxPhotoWidth, !isPhoto)
						photoParentObject = messageObject.photoThumbsObject
						checkOnlyButtonPressed = !isPhoto

						if (currentMessageObject?.strippedThumb == null) {
							currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40)
						}
						else {
							currentPhotoObjectThumbStripped = currentMessageObject!!.strippedThumb
						}

						if (currentPhotoObjectThumb === currentPhotoObject) {
							currentPhotoObjectThumb = null
						}
					}
					else if (webDocument != null) {
						if (webDocument.mimeType?.startsWith("image/") != true) {
							webDocument = null
						}

						drawImageButton = false
					}

					if (documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT) {
						if (currentPhotoObject != null || webDocument != null || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || documentAttachType == DOCUMENT_ATTACH_TYPE_THEME) {
							drawImageButton = photo != null && !smallImage || type != null && (type == "photo" || type == "document" && documentAttachType != DOCUMENT_ATTACH_TYPE_STICKER || type == "gif" || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER)

							if (linkPreviewHeight != 0) {
								linkPreviewHeight += AndroidUtilities.dp(2f)
								totalHeight += AndroidUtilities.dp(2f)
							}
							if (imageBackgroundSideColor != 0) {
								maxPhotoWidth = AndroidUtilities.dp(208f)
							}
							else if (currentPhotoObject is TLRPC.TLPhotoSizeEmpty && currentPhotoObject!!.w != 0) {
								maxPhotoWidth = currentPhotoObject!!.w
							}
							else if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || documentAttachType == DOCUMENT_ATTACH_TYPE_THEME) {
								maxPhotoWidth = if (AndroidUtilities.isTablet()) {
									(AndroidUtilities.getMinTabletSide() * 0.5f).toInt()
								}
								else {
									(getParentWidth() * 0.5f).toInt()
								}
							}
							else if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
								maxPhotoWidth = AndroidUtilities.roundMessageSize
								photoImage.setAllowDecodeSingleFrame(true)
							}

							if (hasInvoicePreview && maxPhotoWidth < messageObject.textWidth) {
								maxPhotoWidth = messageObject.textWidth + AndroidUtilities.dp(22f)
							}

							maxChildWidth = max(maxChildWidth, maxPhotoWidth - (if (hasInvoicePreview) AndroidUtilities.dp(12f) else 0) + additionalWidth)

							if (currentPhotoObject != null) {
								currentPhotoObject?.size = -1
								currentPhotoObjectThumb?.size = -1
							}
							else if (webDocument != null) {
								webDocument.size = -1
							}

							if (imageBackgroundSideColor != 0) {
								imageBackgroundSideWidth = maxChildWidth - AndroidUtilities.dp(13f)
							}

							var width: Int
							var height: Int

							if (smallImage || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
								height = maxPhotoWidth
								width = height
							}
							else {
								if (hasGamePreview || hasInvoicePreview) {
									if (hasInvoicePreview) {
										width = 640
										height = 360
										var a = 0
										val n = webDocument?.attributes?.size ?: 0

										while (a < n) {
											val attribute = webDocument?.attributes?.get(a)

											if (attribute is TLRPC.TLDocumentAttributeImageSize) {
												width = attribute.w
												height = attribute.h
												break
											}

											a++
										}
									}
									else {
										width = 640
										height = 360
									}

									val scale = width / (maxPhotoWidth - AndroidUtilities.dp(2f)).toFloat()
									width = (width.toFloat() / scale).toInt()
									height = (height.toFloat() / scale).toInt()
								}
								else {
									if (drawInstantViewType == 7) {
										width = 560
										height = 678
									}
									else if (currentPhotoObject != null) {
										width = currentPhotoObject!!.w
										height = currentPhotoObject!!.h
									}
									else {
										width = 30
										height = 50
									}

									var scale = width / (maxPhotoWidth - AndroidUtilities.dp(2f)).toFloat()

									width = (width.toFloat() / scale).toInt()
									height = (height.toFloat() / scale).toInt()

									if (site_name == null || !site_name.equals("instagram", ignoreCase = true) && documentAttachType == 0) {
										if (height > AndroidUtilities.displaySize.y / 3) {
											height = AndroidUtilities.displaySize.y / 3
										}
									}
									else {
										if (height > AndroidUtilities.displaySize.y / 2) {
											height = AndroidUtilities.displaySize.y / 2
										}
									}

									if (imageBackgroundSideColor != 0) {
										scale = height / AndroidUtilities.dp(160f).toFloat()
										width = (width.toFloat() / scale).toInt()
										height = (height.toFloat() / scale).toInt()
									}

									if (height < AndroidUtilities.dp(60f)) {
										height = AndroidUtilities.dp(60f)
									}
								}
							}

							if (isSmallImage) {
								if (AndroidUtilities.dp(50f) > linkPreviewHeight) {
									totalHeight += AndroidUtilities.dp(50f) - linkPreviewHeight + AndroidUtilities.dp(8f)
									linkPreviewHeight = AndroidUtilities.dp(50f)
								}

								linkPreviewHeight -= AndroidUtilities.dp(8f)
							}
							else {
								totalHeight += height + AndroidUtilities.dp(12f)
								linkPreviewHeight += height
							}

							if (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER && imageBackgroundSideColor == 0) {
								photoImage.setImageCoordinates(0f, 0f, max(maxChildWidth - AndroidUtilities.dp(13f), width).toFloat(), height.toFloat())
							}
							else {
								photoImage.setImageCoordinates(0f, 0f, width.toFloat(), height.toFloat())
							}

							val w = (width / AndroidUtilities.density).toInt()
							val h = (height / AndroidUtilities.density).toInt()

							currentPhotoFilter = String.format(Locale.US, "%d_%d", w, h)
							currentPhotoFilterThumb = String.format(Locale.US, "%d_%d_b", w, h)

							if (webDocument != null) {                                /*TODO*/
								photoImage.setImage(ImageLocation.getForWebFile(webDocument), currentPhotoFilter, null, null, webDocument.size.toLong(), null, messageObject, 1)
							}
							else {
								if (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) {
									if (messageObject.mediaExists) {
										photoImage.setImage(ImageLocation.getForDocument(documentAttach), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObject, document), "b1", 0, "jpg", messageObject, 1)
									}
									else {
										photoImage.setImage(null, null, ImageLocation.getForDocument(currentPhotoObject, document), "b1", 0, "jpg", messageObject, 1)
									}
								}
								else if (documentAttachType == DOCUMENT_ATTACH_TYPE_THEME) {
									if (document is ThemeDocument) {
										photoImage.setImage(ImageLocation.getForDocument(document), currentPhotoFilter, null, "b1", 0, "jpg", messageObject, 1)
									}
									else {
										photoImage.setImage(ImageLocation.getForDocument(currentPhotoObject, document), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObjectThumb, document), "b1", currentPhotoObjectThumbStripped, 0, "jpg", messageObject, 1)
									}
								}
								else if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) {
									val isWebpSticker = messageObject.isSticker

									if (!SharedConfig.loopStickers && messageObject.isVideoSticker) {
										photoImage.animatedFileDrawableRepeatMaxCount = 1
									}

									if (SharedConfig.loopStickers || isWebpSticker && !messageObject.isVideoSticker) {
										photoImage.setAutoRepeat(1)
									}
									else {
										currentPhotoFilter = String.format(Locale.US, "%d_%d_nr_messageId=%d", w, h, messageObject.stableId)
										photoImage.setAutoRepeat(if (delegate != null && delegate!!.shouldRepeatSticker(messageObject)) 2 else 3)
									}

									photoImage.setImage(ImageLocation.getForDocument(documentAttach), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObject, documentAttach), "b1", documentAttach!!.size, "webp", messageObject, 1)
								}
								else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
									photoImage.isNeedsQualityThumb = true
									photoImage.isShouldGenerateQualityThumb = true

									if (SharedConfig.autoplayVideo && (currentMessageObject!!.mediaExists || messageObject.canStreamVideo() && DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject))) {
										photoImage.setAllowDecodeSingleFrame(true)
										photoImage.allowStartAnimation = true
										photoImage.startAnimation()
										photoImage.setImage(ImageLocation.getForDocument(documentAttach), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObjectThumb, documentAttach), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, documentAttach!!.size, null, messageObject, 0)
										autoPlayingMedia = true
									}
									else {
										if (currentPhotoObjectThumb != null || currentPhotoObjectThumbStripped != null) {
											photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, messageObject, 0)
										}
										else {
											photoImage.setImage(null, null, ImageLocation.getForObject(currentPhotoObject, photoParentObject), if (currentPhotoObject is TLRPC.TLPhotoStrippedSize || "s" == currentPhotoObject!!.type) currentPhotoFilterThumb else currentPhotoFilter, currentPhotoObjectThumbStripped, 0, null, messageObject, 0)
										}
									}
								}
								else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
									photoImage.setAllowDecodeSingleFrame(true)

									var autoDownload = false

									if (MessageObject.isRoundVideoDocument(document)) {
										photoImage.setRoundRadius((AndroidUtilities.roundMessageSize * 0.45f).toInt())
										canChangeRadius = false
										autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject)
									}
									else if (MessageObject.isGifDocument(document, messageObject.hasValidGroupId())) {
										autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject)
									}

									val filter = if (currentPhotoObject is TLRPC.TLPhotoStrippedSize || "s" == currentPhotoObject!!.type) currentPhotoFilterThumb!! else currentPhotoFilter!!

									if (messageObject.mediaExists || autoDownload) {
										autoPlayingMedia = true

										val videoSize = MessageObject.getDocumentVideoThumb(document)

										if (!messageObject.mediaExists && videoSize != null && (currentPhotoObject == null || currentPhotoObjectThumb == null)) {
											photoImage.setImage(ImageLocation.getForDocument(document), if (document!!.size < 1024 * 32) null else ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(videoSize, documentAttach), null, ImageLocation.getForDocument(if (currentPhotoObject != null) currentPhotoObject else currentPhotoObjectThumb, documentAttach), if (currentPhotoObject != null) filter else currentPhotoFilterThumb, currentPhotoObjectThumbStripped, document.size, null, messageObject, 0)
										}
										else {
											photoImage.setImage(ImageLocation.getForDocument(document), if (document!!.size < 1024 * 32) null else ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(currentPhotoObject, documentAttach), filter, ImageLocation.getForDocument(currentPhotoObjectThumb, documentAttach), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, document.size, null, messageObject, 0)
										}
									}
									else {
										photoImage.setImage(null, null, ImageLocation.getForDocument(currentPhotoObject, documentAttach), filter, 0, null, currentMessageObject, 0)
									}
								}
								else {
									val photoExist = messageObject.mediaExists
									val fileName = FileLoader.getAttachFileName(currentPhotoObject)

									if (hasGamePreview || photoExist || DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject) || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
										photoNotSet = false
										photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, messageObject, 0)
									}
									else {
										photoNotSet = true

										if (currentPhotoObjectThumb != null || currentPhotoObjectThumbStripped != null) {
											photoImage.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), String.format(Locale.US, "%d_%d_b", w, h), currentPhotoObjectThumbStripped, 0, null, messageObject, 0)
										}
										else {
											photoImage.setImageBitmap(null as Drawable?)
										}
									}
								}
							}

							drawPhotoImage = true

							if (type != null && type == "video" && duration != 0) {
								val str = AndroidUtilities.formatShortDuration(duration)
								durationWidth = ceil(Theme.chat_durationPaint.measureText(str).toDouble()).toInt()
								videoInfoLayout = StaticLayout(str, Theme.chat_durationPaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							}
							else if (hasGamePreview) {
								var showGameOverlay = true

								try {
									val botId = if (messageObject.messageOwner?.viaBotId != 0L) messageObject.messageOwner!!.viaBotId else (messageObject.messageOwner?.fromId?.userId ?: 0L)

									if (botId != 0L) {
										val botUser = MessagesController.getInstance(currentAccount).getUser(botId)

										if (botUser?.username == "donate") {
											showGameOverlay = false
										}
									}
								}
								catch (e: Exception) {
									// ignored
								}

								if (showGameOverlay) {
									val str = context.getString(R.string.AttachGame).uppercase()
									durationWidth = ceil(Theme.chat_gamePaint.measureText(str).toDouble()).toInt()
									videoInfoLayout = StaticLayout(str, Theme.chat_gamePaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
								}
							}
						}
						else {
							photoImage.setImageBitmap(null as Drawable?)
							linkPreviewHeight -= AndroidUtilities.dp(6f)
							totalHeight += AndroidUtilities.dp(4f)
						}
						if (hasInvoicePreview) {
							val str = if (MessageObject.getMedia(messageObject.messageOwner)!!.flags and 4 != 0) {
								context.getString(R.string.PaymentReceipt).uppercase()
							}
							else {
								if (MessageObject.getMedia(messageObject.messageOwner)?.test == true) {
									context.getString(R.string.PaymentTestInvoice).uppercase()
								}
								else {
									context.getString(R.string.PaymentInvoice).uppercase()
								}
							}

							val price = LocaleController.getInstance().formatCurrencyString(MessageObject.getMedia(messageObject.messageOwner)!!.totalAmount, MessageObject.getMedia(messageObject.messageOwner)!!.currency ?: "")

							val stringBuilder = SpannableStringBuilder("$price $str")
							stringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), 0, price.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

							durationWidth = ceil(Theme.chat_shipmentPaint.measureText(stringBuilder, 0, stringBuilder.length).toDouble()).toInt()
							videoInfoLayout = StaticLayout(stringBuilder, Theme.chat_shipmentPaint, durationWidth + AndroidUtilities.dp(10f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

							if (!drawPhotoImage) {
								totalHeight += AndroidUtilities.dp(6f)

								val timeWidthTotal = timeWidth + AndroidUtilities.dp((14 + if (messageObject.isOutOwner) 20 else 0).toFloat())

								if (durationWidth + timeWidthTotal > maxWidth) {
									maxChildWidth = max(durationWidth, maxChildWidth)
									totalHeight += AndroidUtilities.dp(12f)
								}
								else {
									maxChildWidth = max(durationWidth + timeWidthTotal, maxChildWidth)
								}
							}
						}

						if (hasGamePreview && messageObject.textHeight != 0) {
							linkPreviewHeight += messageObject.textHeight + AndroidUtilities.dp(6f)
							totalHeight += AndroidUtilities.dp(4f)
						}

						calcBackgroundWidth(maxWidth, timeMore, maxChildWidth)
					}

					createInstantViewButton()
				}
				else {
					photoImage.setImageBitmap(null as Drawable?)
					calcBackgroundWidth(maxWidth, timeMore, maxChildWidth)
				}
			}
			else if (messageObject.type == MessageObject.TYPE_CALL) {
				createSelectorDrawable(0)
				drawName = false
				drawForwardedName = false
				drawPhotoImage = false

				backgroundWidth = if (AndroidUtilities.isTablet()) {
					min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(270f))
				}
				else {
					min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(270f))
				}

				availableTimeWidth = backgroundWidth - AndroidUtilities.dp(31f)

				var maxWidth = maxNameWidth - AndroidUtilities.dp(50f)

				if (maxWidth < 0) {
					maxWidth = AndroidUtilities.dp(10f)
				}

				var time = LocaleController.getInstance().formatterDay.format(messageObject.messageOwner!!.date.toLong() * 1000)
				val call = messageObject.messageOwner!!.action as TLRPC.TLMessageActionPhoneCall
				val isMissed = call.reason is TLRPC.TLPhoneCallDiscardReasonMissed

				val text = if (messageObject.isOutOwner) {
					if (isMissed) {
						if (call.video) {
							context.getString(R.string.CallMessageVideoOutgoingMissed)
						}
						else {
							context.getString(R.string.CallMessageOutgoingMissed)
						}
					}
					else {
						if (call.video) {
							context.getString(R.string.CallMessageVideoOutgoing)
						}
						else {
							context.getString(R.string.CallMessageOutgoing)
						}
					}
				}
				else {
					if (isMissed) {
						if (call.video) {
							context.getString(R.string.CallMessageVideoIncomingMissed)
						}
						else {
							context.getString(R.string.CallMessageIncomingMissed)
						}
					}
					else if (call.reason is TLRPC.TLPhoneCallDiscardReasonBusy) {
						if (call.video) {
							context.getString(R.string.CallMessageVideoIncomingDeclined)
						}
						else {
							context.getString(R.string.CallMessageIncomingDeclined)
						}
					}
					else {
						if (call.video) {
							context.getString(R.string.CallMessageVideoIncoming)
						}
						else {
							context.getString(R.string.CallMessageIncoming)
						}
					}
				}

				if (call.duration > 0) {
					time += ", " + LocaleController.formatCallDuration(call.duration)
				}

				titleLayout = StaticLayout(TextUtils.ellipsize(text, Theme.chat_audioTitlePaint, maxWidth.toFloat(), TextUtils.TruncateAt.END), Theme.chat_audioTitlePaint, maxWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				docTitleLayout = StaticLayout(TextUtils.ellipsize(time, Theme.chat_contactPhonePaint, maxWidth.toFloat(), TextUtils.TruncateAt.END), Theme.chat_contactPhonePaint, maxWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				setMessageObjectInternal(messageObject)

				totalHeight = AndroidUtilities.dp(65f) + namesOffset

				if (!shouldDrawTimeOnMedia()) {
					totalHeight += CELL_HEIGHT_INCREASE
				}

				if (drawPinnedTop) {
					namesOffset -= AndroidUtilities.dp(1f)
				}
			}
			else if (messageObject.type == MessageObject.TYPE_CONTACT) {
				drawName = messageObject.isFromGroup && messageObject.isSupergroup || messageObject.isImportedForward && messageObject.messageOwner?.fwdFrom?.fromId == null
				drawForwardedName = !isRepliesChat
				drawPhotoImage = true

				photoImage.setRoundRadius(AndroidUtilities.dp(22f))

				canChangeRadius = false

				backgroundWidth = if (AndroidUtilities.isTablet()) {
					min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(270f))
				}
				else {
					min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(270f))
				}

				availableTimeWidth = backgroundWidth - AndroidUtilities.dp(31f)

				val uid = MessageObject.getMedia(messageObject.messageOwner)?.userId ?: 0L
				var user: User? = null

				if (uid != 0L) {
					user = MessagesController.getInstance(currentAccount).getUser(uid)

					if (user == null) {
						MessagesController.getInstance(currentAccount).loadFullUser(TLRPC.TLUser().apply { id = uid }, classGuid, true)
					}
				}

				var maxWidth = maxNameWidth - AndroidUtilities.dp(80f)

				if (maxWidth < 0) {
					maxWidth = AndroidUtilities.dp(10f)
				}

				contactAvatarDrawable.shouldDrawStroke = true

				val hasName = if (user != null) {
					contactAvatarDrawable.setInfo(user)
					true
				}
				else if (!TextUtils.isEmpty(MessageObject.getMedia(messageObject.messageOwner)?.firstName) || !TextUtils.isEmpty(MessageObject.getMedia(messageObject.messageOwner)?.lastName)) {
					contactAvatarDrawable.setInfo(MessageObject.getMedia(messageObject.messageOwner)!!.firstName, MessageObject.getMedia(messageObject.messageOwner)!!.lastName)
					true
				}
				else {
					false
				}

				photoImage.setForUserOrChat(user, if (hasName) contactAvatarDrawable else Theme.chat_contactDrawable[if (messageObject.isOutOwner) 1 else 0], messageObject)

				var phone: CharSequence?

				if (!TextUtils.isEmpty(messageObject.vCardData)) {
					phone = messageObject.vCardData
					drawInstantView = true
					drawInstantViewType = 5
				}
				else {
					phone = MessageObject.getMedia(messageObject.messageOwner)?.phoneNumber

					if (phone.isNullOrEmpty()) {
						phone = context.getString(R.string.NumberUnknown)
					}
				}

				var currentNameString: String? = ContactsController.formatName(MessageObject.getMedia(messageObject.messageOwner)?.firstName, MessageObject.getMedia(messageObject.messageOwner)?.lastName).replace('\n', ' ')

				if (currentNameString.isNullOrEmpty()) {
					currentNameString = MessageObject.getMedia(messageObject.messageOwner)?.phoneNumber

					if (currentNameString == null) {
						currentNameString = ""
					}
				}

				titleLayout = StaticLayout(TextUtils.ellipsize(currentNameString, Theme.chat_contactNamePaint, maxWidth.toFloat(), TextUtils.TruncateAt.END), Theme.chat_contactNamePaint, maxWidth + AndroidUtilities.dp(4f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				docTitleLayout = StaticLayout(phone, Theme.chat_contactPhonePaint, maxWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1f).toFloat(), false)

				setMessageObjectInternal(messageObject)

				if (drawForwardedName && messageObject.needDrawForwarded() && (currentPosition == null || currentPosition!!.minY.toInt() == 0)) {
					namesOffset += AndroidUtilities.dp(5f)
				}
				else if (drawNameLayout && messageObject.replyMsgId == 0) {
					namesOffset += AndroidUtilities.dp(7f)
				}

				totalHeight = AndroidUtilities.dp((70 - 15).toFloat()) + namesOffset + docTitleLayout!!.height

				if (!shouldDrawTimeOnMedia()) {
					totalHeight += CELL_HEIGHT_INCREASE
				}

				if (drawPinnedTop) {
					namesOffset -= AndroidUtilities.dp(1f)
				}

				if (drawInstantView) {
					createInstantViewButton()
				}
				else {
					if (docTitleLayout!!.lineCount > 0) {
						val timeLeft = backgroundWidth - AndroidUtilities.dp((40 + 18 + 44 + 8).toFloat()) - ceil(docTitleLayout!!.getLineWidth(docTitleLayout!!.lineCount - 1).toDouble()).toInt()

						if (timeLeft < timeWidth) {
							totalHeight += AndroidUtilities.dp(8f)
						}
					}
				}

				if (!reactionsLayoutInBubble.isSmall) {
					if (!reactionsLayoutInBubble.isEmpty) {
						reactionsLayoutInBubble.measure(backgroundWidth - AndroidUtilities.dp(32f), Gravity.LEFT)
						reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(12f)
						reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(4f)

						if (backgroundWidth - AndroidUtilities.dp(32f) - reactionsLayoutInBubble.lastLineX < timeWidth) {
							reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12f)
							reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(12f)
						}

						totalHeight += reactionsLayoutInBubble.totalHeight
					}
				}
			}
			else if (messageObject.type == MessageObject.TYPE_VOICE) {
				drawForwardedName = !isRepliesChat
				drawName = messageObject.isFromGroup && messageObject.isSupergroup || messageObject.isImportedForward && messageObject.messageOwner?.fwdFrom?.fromId == null

				val maxWidth: Int

				if (AndroidUtilities.isTablet()) {
					maxWidth = min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(270f))
					backgroundWidth = maxWidth
				}
				else {
					maxWidth = min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(270f))
					backgroundWidth = maxWidth
				}

				createDocumentLayout(backgroundWidth, messageObject)
				setMessageObjectInternal(messageObject)

				totalHeight = AndroidUtilities.dp(70f) + namesOffset

				if (!shouldDrawTimeOnMedia()) {
					totalHeight += CELL_HEIGHT_INCREASE
				}

				if (drawPinnedTop) {
					namesOffset -= AndroidUtilities.dp(1f)
				}

				if (!reactionsLayoutInBubble.isSmall) {
					reactionsLayoutInBubble.measure(maxWidth - if (messageObject.isOutOwner) AndroidUtilities.dp(32f) else AndroidUtilities.dp(24f), Gravity.LEFT)

					if (!reactionsLayoutInBubble.isEmpty) {
						reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height

						if (TextUtils.isEmpty(messageObject.caption)) {
							reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12f)
						}
						else {
							reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(8f)
						}

						if (reactionsLayoutInBubble.width > backgroundWidth) {
							backgroundWidth = reactionsLayoutInBubble.width
						}

						var timeMore = timeWidth + AndroidUtilities.dp(6f)

						if (messageObject.isOutOwner) {
							timeMore += AndroidUtilities.dp(20.5f)
						}

						timeMore += extraTimeX

						if (reactionsLayoutInBubble.lastLineX + timeMore >= backgroundWidth) {
							reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12f)
							reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(12f)
						}

						totalHeight += reactionsLayoutInBubble.totalHeight
					}
				}
			}
			else if (messageObject.type == MessageObject.TYPE_MUSIC) {
				drawName = (messageObject.isFromGroup && messageObject.isSupergroup || messageObject.isImportedForward && messageObject.messageOwner?.fwdFrom?.fromId == null) && (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP != 0)

				val maxWidth: Int

				if (AndroidUtilities.isTablet()) {
					maxWidth = min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(270f))
					backgroundWidth = maxWidth
				}
				else {
					maxWidth = min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(270f))
					backgroundWidth = maxWidth
				}

				createDocumentLayout(backgroundWidth, messageObject)
				setMessageObjectInternal(messageObject)

				totalHeight = AndroidUtilities.dp(82f) + namesOffset

				if (!shouldDrawTimeOnMedia()) {
					totalHeight += CELL_HEIGHT_INCREASE
				}

				if (currentPosition != null && currentMessagesGroup != null && currentMessagesGroup!!.messages.size > 1) {
					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP == 0) {
						totalHeight -= AndroidUtilities.dp(6f)
						mediaOffsetY -= AndroidUtilities.dp(6f)
					}
					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0) {
						totalHeight -= AndroidUtilities.dp(6f)
					}
				}

				if (drawPinnedTop) {
					namesOffset -= AndroidUtilities.dp(1f)
				}

				if (!reactionsLayoutInBubble.isSmall) {
					reactionsLayoutInBubble.measure(maxWidth - AndroidUtilities.dp(24f), Gravity.LEFT)

					if (!reactionsLayoutInBubble.isEmpty) {
						reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(12f)

						measureTime(messageObject)

						if (reactionsLayoutInBubble.width > backgroundWidth) {
							backgroundWidth = reactionsLayoutInBubble.width
						}

						if (reactionsLayoutInBubble.lastLineX + timeWidth + AndroidUtilities.dp(24f) > backgroundWidth) {
							reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12f)
							reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(12f)
						}

						if (!messageObject.isRestrictedMessage && messageObject.caption != null) {
							reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(14f)
						}

						totalHeight += reactionsLayoutInBubble.totalHeight
					}
				}
			}
			else if (messageObject.type == MessageObject.TYPE_POLL) {
				if (timerParticles == null) {
					timerParticles = TimerParticles()
				}

				createSelectorDrawable(0)

				drawName = true
				drawForwardedName = !isRepliesChat
				drawPhotoImage = false

				val maxWidth = min(AndroidUtilities.dp(500f), messageObject.maxMessageTextWidth)

				backgroundWidth = maxWidth + AndroidUtilities.dp(31f)

				val media = MessageObject.getMedia(messageObject.messageOwner) as TLRPC.TLMessageMediaPoll

				timerTransitionProgress = if ((media.poll?.closeDate ?: 0) - ConnectionsManager.getInstance(currentAccount).currentTime < 60) 0.0f else 1.0f
				pollClosed = media.poll?.closed == true
				pollVoted = messageObject.isVoted

				if (pollVoted) {
					messageObject.checkedVotes?.clear()
				}

				titleLayout = StaticLayout(Emoji.replaceEmoji(media.poll?.question, Theme.chat_audioTitlePaint.fontMetricsInt, false), Theme.chat_audioTitlePaint, maxWidth + AndroidUtilities.dp(2f) - extraTextX * 2, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				var titleRtl = false

				if (titleLayout != null) {
					var a = 0
					val n = titleLayout!!.lineCount

					while (a < n) {
						if (titleLayout!!.getLineLeft(a) > 0) {
							titleRtl = true
							break
						}

						a++
					}
				}

				val title = if (pollClosed) {
					context.getString(R.string.FinalResults)
				}
				else {
					if (media.poll?.quiz == true) {
						if (media.poll?.publicVoters == true) {
							context.getString(R.string.QuizPoll)
						}
						else {
							context.getString(R.string.AnonymousQuizPoll)
						}
					}
					else if (media.poll?.publicVoters == true) {
						context.getString(R.string.PublicPoll)
					}
					else {
						context.getString(R.string.AnonymousPoll)
					}
				}

				docTitleLayout = StaticLayout(TextUtils.ellipsize(title, Theme.chat_timePaint, maxWidth.toFloat(), TextUtils.TruncateAt.END), Theme.chat_timePaint, maxWidth + AndroidUtilities.dp(2f) - extraTextX * 2, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				if (docTitleLayout != null && docTitleLayout!!.lineCount > 0) {
					docTitleOffsetX = if (titleRtl && !LocaleController.isRTL) {
						ceil((maxWidth - docTitleLayout!!.getLineWidth(0)).toDouble()).toInt()
					}
					else if (!titleRtl && LocaleController.isRTL) {
						-ceil(docTitleLayout!!.getLineLeft(0).toDouble()).toInt()
					}
					else {
						0
					}
				}

				val w = maxWidth - AndroidUtilities.dp(if (messageObject.isOutOwner) 28f else 8f)

				if (!isBot) {
					val textPaint = if (media.poll?.publicVoters != true && media.poll?.multipleChoice != true) Theme.chat_livePaint else Theme.chat_locationAddressPaint

					val votes = if (media.poll?.quiz == true) {
						TextUtils.ellipsize(if (media.results?.totalVoters == 0) context.getString(R.string.NoVotesQuiz) else LocaleController.formatPluralString("Answer", media.results?.totalVoters ?: 0), textPaint, w.toFloat(), TextUtils.TruncateAt.END)
					}
					else {
						TextUtils.ellipsize(if (media.results?.totalVoters == 0) context.getString(R.string.NoVotes) else LocaleController.formatPluralString("Vote", media.results?.totalVoters ?: 0), textPaint, w.toFloat(), TextUtils.TruncateAt.END)
					}

					infoLayout = StaticLayout(votes, textPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

					if (infoLayout != null) {
						if (media.poll?.publicVoters != true && media.poll?.multipleChoice != true) {
							infoX = ceil(if (infoLayout!!.lineCount > 0) -infoLayout!!.getLineLeft(0) else 0f).toInt()
							availableTimeWidth = (maxWidth - infoLayout!!.getLineWidth(0) - AndroidUtilities.dp(16f)).toInt()
						}
						else {
							infoX = ((backgroundWidth - AndroidUtilities.dp(28f) - ceil(infoLayout!!.getLineWidth(0).toDouble())) / 2 - infoLayout!!.getLineLeft(0)).toInt()
							availableTimeWidth = maxWidth
						}
					}
				}

				measureTime(messageObject)

				lastPoll = media.poll
				lastPollResults = media.results?.results
				lastPollResultsVoters = media.results?.totalVoters ?: 0

				if (media.poll?.multipleChoice == true && !pollVoted && !pollClosed || !isBot && (media.poll?.publicVoters == true && pollVoted || pollClosed && media.results != null && media.results?.totalVoters != 0 && media.poll?.publicVoters == true)) {
					drawInstantView = true
					drawInstantViewType = 8
					createInstantViewButton()
				}

				if (media.poll?.multipleChoice == true) {
					createPollUI()
				}

				if (media.results != null) {
					createPollUI()

					val size = media.results!!.recentVoters.size

					for (a in pollAvatarImages!!.indices) {
						if (!isBot && a < size) {
							pollAvatarImages!![a]!!.setImageCoordinates(0f, 0f, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat())

							val id = media.results!!.recentVoters[a]
							val user = MessagesController.getInstance(currentAccount).getUser(id)

							if (user != null) {
								pollAvatarDrawables?.get(a)?.setInfo(user)
								pollAvatarImages?.get(a)?.setForUserOrChat(user, pollAvatarDrawables!![a])
							}
							else {
								pollAvatarDrawables?.get(a)?.setInfo("", "")
							}

							pollAvatarImagesVisible?.set(a, true)
						}
						else if (!pollUnvoteInProgress || size != 0) {
							pollAvatarImages!![a]!!.setImageBitmap(null as Drawable?)
							pollAvatarImagesVisible?.set(a, false)
						}
					}
				}
				else if (pollAvatarImages != null) {
					for (a in pollAvatarImages!!.indices) {
						pollAvatarImages!![a]!!.setImageBitmap(null as Drawable?)
						pollAvatarImagesVisible!![a] = false
					}
				}

				var maxVote = 0

				if (!animatePollAnswer && pollVoteInProgress && vibrateOnPollVote) {
					performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
				}

				animatePollAnswer = attachedToWindow && (pollVoteInProgress || pollUnvoteInProgress)
				isAnimatingPollAnswer = animatePollAnswer

				var previousPollButtons: ArrayList<PollButton>? = null
				val sortedPollButtons = ArrayList<PollButton>()

				if (pollButtons.isNotEmpty()) {
					previousPollButtons = ArrayList(pollButtons)

					pollButtons.clear()

					if (!animatePollAnswer) {
						animatePollAnswer = attachedToWindow && (pollVoted || pollClosed)
					}

					if (pollAnimationProgress > 0 && pollAnimationProgress < 1.0f) {
						var b = 0
						val n = previousPollButtons.size

						while (b < n) {
							val button = previousPollButtons[b]
							button.percent = ceil((button.prevPercent + (button.percent - button.prevPercent) * pollAnimationProgress).toDouble()).toInt()
							button.percentProgress = button.prevPercentProgress + (button.percentProgress - button.prevPercentProgress) * pollAnimationProgress
							b++
						}
					}
				}

				pollAnimationProgress = if (animatePollAnswer) 0.0f else 1.0f

				var votingFor: ByteArray?

				if (!isAnimatingPollAnswer) {
					pollVoteInProgress = false
					pollVoteInProgressNum = -1
					votingFor = SendMessagesHelper.getInstance(currentAccount).isSendingVote(currentMessageObject)
				}
				else {
					votingFor = null
				}

				var height = if (titleLayout != null) titleLayout!!.height else 0
				var restPercent = 100
				var hasDifferent = false
				var previousPercent = 0
				var a = 0
				val n = media.poll?.answers?.size ?: 0

				while (a < n) {
					val button = PollButton()
					button.answer = media.poll?.answers?.get(a)
					button.title = StaticLayout(Emoji.replaceEmoji(button.answer!!.text, Theme.chat_audioPerformerPaint.fontMetricsInt, false), Theme.chat_audioPerformerPaint, maxWidth - AndroidUtilities.dp(33f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
					button.y = height + AndroidUtilities.dp(52f)
					button.height = button.title!!.height

					pollButtons.add(button)

					sortedPollButtons.add(button)

					height += button.height + AndroidUtilities.dp(26f)

					if (!media.results?.results.isNullOrEmpty()) {
						var b = 0
						val n2 = media.results!!.results.size

						while (b < n2) {
							val answer = media.results!!.results[b]

							if (button.answer?.option?.contentEquals(answer.option) == true) {
								button.chosen = answer.chosen
								button.count = answer.voters
								button.correct = answer.correct

								if ((pollVoted || pollClosed) && media.results!!.totalVoters > 0) {
									button.decimal = 100 * (answer.voters / media.results!!.totalVoters.toFloat())
									button.percent = button.decimal.toInt()
									button.decimal -= button.percent.toFloat()
								}
								else {
									button.percent = 0
									button.decimal = 0f
								}

								if (previousPercent == 0) {
									previousPercent = button.percent
								}
								else if (button.percent != 0 && previousPercent != button.percent) {
									hasDifferent = true
								}

								restPercent -= button.percent
								maxVote = max(button.percent, maxVote)

								break
							}

							b++
						}
					}

					if (previousPollButtons != null) {
						var b = 0
						val n2 = previousPollButtons.size

						while (b < n2) {
							val prevButton = previousPollButtons[b]

							if (button.answer?.option?.contentEquals(prevButton.answer!!.option) == true) {
								button.prevPercent = prevButton.percent
								button.prevPercentProgress = prevButton.percentProgress
								button.prevChosen = prevButton.chosen
								break
							}

							b++
						}
					}

					if (votingFor != null && button.answer?.option?.isNotEmpty() == true && Arrays.binarySearch(votingFor, button.answer!!.option!![0]) >= 0) {
						pollVoteInProgressNum = a
						pollVoteInProgress = true
						vibrateOnPollVote = true
						votingFor = null
					}

					pollCheckBox!![a]!!.setChecked(currentMessageObject!!.checkedVotes!!.contains(button.answer!!), false)

					a++
				}

				if (hasDifferent && restPercent != 0) {
					sortedPollButtons.sortWith { o1, o2 ->
						if (o1.decimal > o2.decimal) {
							return@sortWith -1
						}
						else if (o1.decimal < o2.decimal) {
							return@sortWith 1
						}
						if (o1.decimal == o2.decimal) {
							if (o1.percent > o2.percent) {
								return@sortWith 1
							}
							else if (o1.percent < o2.percent) {
								return@sortWith -1
							}
						}

						0
					}

					@Suppress("NAME_SHADOWING") var a = 0
					@Suppress("NAME_SHADOWING") val n = min(restPercent, sortedPollButtons.size)

					while (a < n) {
						sortedPollButtons[a].percent += 1
						a++
					}
				}

				val width = backgroundWidth - AndroidUtilities.dp(76f)
				var b = 0
				val n2 = pollButtons.size

				while (b < n2) {
					val button = pollButtons[b]
					button.percentProgress = max(AndroidUtilities.dp(5f) / width.toFloat(), if (maxVote != 0) button.percent / maxVote.toFloat() else 0f)
					b++
				}

				setMessageObjectInternal(messageObject)

				if (isBot && !drawInstantView) {
					height -= AndroidUtilities.dp(10f)
				}
				else if (media.poll?.publicVoters == true || media.poll?.multipleChoice == true) {
					height += AndroidUtilities.dp(13f)
				}

				totalHeight = AndroidUtilities.dp((46 + 27).toFloat()) + namesOffset + height

				if (!shouldDrawTimeOnMedia()) {
					totalHeight += CELL_HEIGHT_INCREASE
				}

				if (drawPinnedTop) {
					namesOffset -= AndroidUtilities.dp(1f)
				}

				instantTextNewLine = false

				if (media.poll?.publicVoters == true || media.poll?.multipleChoice == true) {
					var instantTextWidth = 0

					for (@Suppress("NAME_SHADOWING") a in 0..2) {
						val str = when (a) {
							0 -> context.getString(R.string.PollViewResults)
							1 -> context.getString(R.string.PollSubmitVotes)
							else -> context.getString(R.string.NoVotes)
						}

						instantTextWidth = max(instantTextWidth, ceil(Theme.chat_instantViewPaint.measureText(str).toDouble()).toInt())
					}

					val timeWidthTotal = timeWidth + (if (messageObject.isOutOwner) AndroidUtilities.dp(20f) else 0) + extraTimeX

					if (!reactionsLayoutInBubble.isSmall && reactionsLayoutInBubble.isEmpty && timeWidthTotal >= (backgroundWidth - AndroidUtilities.dp(76f) - instantTextWidth) / 2) {
						totalHeight += AndroidUtilities.dp(18f)
						instantTextNewLine = true
					}
				}

				if (!reactionsLayoutInBubble.isSmall) {
					if (!reactionsLayoutInBubble.isEmpty) {
						reactionsLayoutInBubble.measure(maxWidth, Gravity.LEFT)

						totalHeight += reactionsLayoutInBubble.height + AndroidUtilities.dp(12f)

						val timeWidthTotal = timeWidth + (if (messageObject.isOutOwner) AndroidUtilities.dp(20f) else 0) + extraTimeX

						if (timeWidthTotal >= backgroundWidth - AndroidUtilities.dp(24f) - reactionsLayoutInBubble.lastLineX) {
							totalHeight += AndroidUtilities.dp(16f)
							reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(16f)
						}
					}
				}
			}
			else {
				drawForwardedName = messageObject.messageOwner?.fwdFrom != null && !(messageObject.isAnyKindOfSticker && messageObject.isDice)

				if (!messageObject.isAnyKindOfSticker && messageObject.type != MessageObject.TYPE_ROUND_VIDEO) {
					drawName = (messageObject.isFromGroup && messageObject.isSupergroup || messageObject.isImportedForward && messageObject.messageOwner?.fwdFrom?.fromId == null) && (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP != 0)
				}

				mediaBackground = messageObject.type != MessageObject.TYPE_DOCUMENT

				if (mediaBackground) {
					// MARK: clear background
					if (messageObject.isOutOwner && messageObject.messageText.isNullOrEmpty() && !messageObject.isForwarded) {
						drawBackground = false
					}
				}

				drawImageButton = true
				drawPhotoImage = true

				var photoWidth = 0
				var photoHeight = 0
				var additionHeight = 0

				if (messageObject.gifState != 2f && !SharedConfig.autoplayGifs && (messageObject.type == MessageObject.TYPE_GIF || messageObject.type == MessageObject.TYPE_ROUND_VIDEO)) {
					messageObject.gifState = 1f
				}

				photoImage.setAllowDecodeSingleFrame(true)

				if (messageObject.isVideo) {
					photoImage.allowStartAnimation = true
				}
				else if (messageObject.isRoundVideo) {
					val playingMessage = MediaController.getInstance().playingMessageObject
					photoImage.allowStartAnimation = playingMessage == null || !playingMessage.isRoundVideo
				}
				else {
					photoImage.allowStartAnimation = messageObject.gifState == 0f
				}

				photoImage.isForcePreview = messageObject.needDrawBluredPreview()

				if (messageObject.type == MessageObject.TYPE_DOCUMENT) {
					backgroundWidth = if (AndroidUtilities.isTablet()) {
						min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(300f))
					}
					else {
						min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp(300f))
					}

					if (checkNeedDrawShareButton(messageObject)) {
						backgroundWidth -= AndroidUtilities.dp(20f)
					}

					var maxTextWidth = 0
					var maxWidth = backgroundWidth - AndroidUtilities.dp((86 + 52).toFloat())
					val widthForCaption: Int

					createDocumentLayout(maxWidth, messageObject)

					val width = backgroundWidth - AndroidUtilities.dp(31f)

					widthForCaption = width - AndroidUtilities.dp(10f) - extraTextX * 2

					if (!messageObject.isRestrictedMessage && !messageObject.caption.isNullOrEmpty()) {
						try {
							currentCaption = messageObject.caption

							captionLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
								StaticLayout.Builder.obtain(messageObject.caption!!, 0, messageObject.caption!!.length, Theme.chat_msgTextPaint, widthForCaption).setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
							}
							else {
								StaticLayout(messageObject.caption, Theme.chat_msgTextPaint, widthForCaption, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							}

							updateCaptionSpoilers()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					docTitleLayout?.let { docTitleLayout ->
						var a = 0
						val n = docTitleLayout.lineCount

						while (a < n) {
							maxTextWidth = max(maxTextWidth, ceil((docTitleLayout.getLineWidth(a) + docTitleLayout.getLineLeft(a)).toDouble()).toInt() + AndroidUtilities.dp((86 + if (drawPhotoImage) 52 else 22).toFloat()))
							a++
						}
					}

					infoLayout?.let { infoLayout ->
						var a = 0
						val n = infoLayout.lineCount

						while (a < n) {
							maxTextWidth = max(maxTextWidth, infoWidth + AndroidUtilities.dp((86 + if (drawPhotoImage) 52 else 22).toFloat()))
							a++
						}
					}

					captionLayout?.let { captionLayout ->
						var a = 0
						val n = captionLayout.lineCount

						while (a < n) {
							val w = ceil(min(widthForCaption.toFloat(), captionLayout.getLineWidth(a) + captionLayout.getLineLeft(a)).toDouble()).toInt() + AndroidUtilities.dp(31f)

							if (w > maxTextWidth) {
								maxTextWidth = w
							}

							a++
						}
					}

					if (!reactionsLayoutInBubble.isSmall) {
						reactionsLayoutInBubble.measure(widthForCaption, Gravity.LEFT)

						if (!reactionsLayoutInBubble.isEmpty && reactionsLayoutInBubble.width + AndroidUtilities.dp(31f) > maxTextWidth) {
							maxTextWidth = reactionsLayoutInBubble.width + AndroidUtilities.dp(31f)
						}
					}

					if (maxTextWidth > 0 && currentPosition == null) {
						backgroundWidth = maxTextWidth
						maxWidth = maxTextWidth - AndroidUtilities.dp(31f)
					}

					availableTimeWidth = maxWidth

					if (drawPhotoImage) {
						photoWidth = AndroidUtilities.dp(86f)
						photoHeight = AndroidUtilities.dp(86f)
						availableTimeWidth -= photoWidth
					}
					else {
						photoWidth = AndroidUtilities.dp(56f)
						photoHeight = AndroidUtilities.dp(56f)

						docTitleLayout?.let {
							if (it.lineCount > 1) {
								photoHeight += (it.lineCount - 1) * AndroidUtilities.dp(16f)
							}
						}

						if (TextUtils.isEmpty(messageObject.caption) && infoLayout != null) {
							val lineCount = infoLayout!!.lineCount

							measureTime(messageObject)

							val timeLeft = backgroundWidth - AndroidUtilities.dp((40 + 18 + 56 + 8).toFloat()) - infoWidth

							if (reactionsLayoutInBubble.isSmall || reactionsLayoutInBubble.isEmpty) {
								if (timeLeft < timeWidth) {
									photoHeight += AndroidUtilities.dp(12f)
								}
								else if (lineCount == 1) {
									photoHeight += AndroidUtilities.dp(4f)
								}
							}
						}
					}

					if (!reactionsLayoutInBubble.isSmall && !reactionsLayoutInBubble.isEmpty) {
						if (!drawPhotoImage) {
							reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(2f)
						}

						if (captionLayout != null && currentPosition != null && currentMessagesGroup != null && currentMessagesGroup!!.isDocuments) {
							reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(10f)
						}
						else if (!drawPhotoImage && !TextUtils.isEmpty(messageObject.caption) && (docTitleLayout != null && docTitleLayout!!.lineCount > 1 || currentMessageObject!!.hasValidReplyMessageObject())) {
							reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(10f)
						}
						else if (!drawPhotoImage && !TextUtils.isEmpty(messageObject.caption) && !currentMessageObject!!.isOutOwner) {
							reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(10f)
						}

						reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(8f)

						measureTime(messageObject)

						if (drawPhotoImage && captionLayout == null) {
							reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(8f)
						}

						if (captionLayout != null && currentMessageObject != null && currentMessageObject!!.isOutOwner && currentMessageObject!!.isDocument() && currentMessagesGroup == null && !currentMessageObject!!.isForwarded && !currentMessageObject!!.isReply) {
							reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(10f)
						}

						val timeLeft = backgroundWidth - reactionsLayoutInBubble.lastLineX - AndroidUtilities.dp(31f)

						if (timeLeft < timeWidth) {
							reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12f)
							reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(12f)
						}

						additionHeight += reactionsLayoutInBubble.totalHeight
					}
				}
				else if (messageObject.type == MessageObject.TYPE_GEO) {
					val point = MessageObject.getMedia(messageObject.messageOwner)?.geo
					var lat = point?.lat ?: 0.0
					val lon = point?.lon ?: 0.0

					val provider = if (messageObject.dialogId.toInt() == 0) {
						when (SharedConfig.mapPreviewType) {
							SharedConfig.MAP_PREVIEW_PROVIDER_ELLO -> MessagesController.MAP_PROVIDER_UNDEFINED
							SharedConfig.MAP_PREVIEW_PROVIDER_GOOGLE -> MessagesController.MAP_PROVIDER_GOOGLE
							SharedConfig.MAP_PREVIEW_PROVIDER_YANDEX -> MessagesController.MAP_PROVIDER_YANDEX_NO_ARGS
							else -> MessagesController.MAP_PROVIDER_UNDEFINED
						}
					}
					else {
						MessagesController.MAP_PROVIDER_UNDEFINED
					}

					if (MessageObject.getMedia(messageObject.messageOwner) is TLRPC.TLMessageMediaGeoLive) {
						backgroundWidth = if (AndroidUtilities.isTablet()) {
							min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp((252 + 37).toFloat()))
						}
						else {
							min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp((252 + 37).toFloat()))
						}

						backgroundWidth -= AndroidUtilities.dp(4f)

						if (checkNeedDrawShareButton(messageObject)) {
							backgroundWidth -= AndroidUtilities.dp(20f)
						}

						var maxWidth = backgroundWidth - AndroidUtilities.dp(37f)

						availableTimeWidth = maxWidth

						maxWidth -= AndroidUtilities.dp(54f)
						photoWidth = backgroundWidth - AndroidUtilities.dp(17f)
						photoHeight = AndroidUtilities.dp(195f)

						val offset = 268435456
						val rad = offset / Math.PI
						val y = ((offset - rad * ln((1 + sin(lat * Math.PI / 180.0)) / (1 - sin(lat * Math.PI / 180.0))) / 2).roundToLong() - (AndroidUtilities.dp(10.3f) shl 21 - 15)).toDouble()

						lat = (Math.PI / 2.0 - 2 * atan(exp((y - offset) / rad))) * 180.0 / Math.PI

						currentUrl = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), false, 15, provider)

						lastWebFile = currentWebFile

						currentWebFile = WebFile.createWithGeoPoint(lat, lon, point?.accessHash ?: 0, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), 15, min(2, ceil(AndroidUtilities.density.toDouble()).toInt()))

						if (!isCurrentLocationTimeExpired(messageObject).also { locationExpired = it }) {
							photoImage.setCrossfadeWithOldImage(true)
							mediaBackground = false
							additionHeight = AndroidUtilities.dp(56f)
							AndroidUtilities.runOnUIThread(invalidateRunnable, 1000)
							scheduledInvalidate = true
						}
						else {
							backgroundWidth -= AndroidUtilities.dp(9f)
						}

						docTitleLayout = StaticLayout(TextUtils.ellipsize(context.getString(R.string.AttachLiveLocation), Theme.chat_locationTitlePaint, maxWidth.toFloat(), TextUtils.TruncateAt.END), Theme.chat_locationTitlePaint, maxWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

						updateCurrentUserAndChat()

						if (currentUser != null) {
							contactAvatarDrawable.setInfo(currentUser)
							locationImageReceiver.setForUserOrChat(currentUser, contactAvatarDrawable)
						}
						else if (currentChat != null) {
							if (currentChat?.photo != null) {
								currentPhoto = currentChat?.photo?.photoSmall
							}

							contactAvatarDrawable.setInfo(currentChat)
							locationImageReceiver.setForUserOrChat(currentChat, contactAvatarDrawable)
						}
						else {
							locationImageReceiver.setImage(null, null, contactAvatarDrawable, null, null, 0)
						}

						infoLayout = StaticLayout(TextUtils.ellipsize(LocaleController.formatLocationUpdateDate(if (messageObject.messageOwner?.editDate != 0) messageObject.messageOwner!!.editDate.toLong() else messageObject.messageOwner!!.date.toLong()), Theme.chat_locationAddressPaint, (maxWidth + AndroidUtilities.dp(2f)).toFloat(), TextUtils.TruncateAt.END), Theme.chat_locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
					}
					else if (!TextUtils.isEmpty(MessageObject.getMedia(messageObject.messageOwner)?.title)) {
						backgroundWidth = if (AndroidUtilities.isTablet()) {
							min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp((252 + 37).toFloat()))
						}
						else {
							min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp((252 + 37).toFloat()))
						}

						backgroundWidth -= AndroidUtilities.dp(4f)

						if (checkNeedDrawShareButton(messageObject)) {
							backgroundWidth -= AndroidUtilities.dp(20f)
						}

						val maxWidth = backgroundWidth - AndroidUtilities.dp(34f)

						availableTimeWidth = maxWidth
						photoWidth = backgroundWidth - AndroidUtilities.dp(17f)
						photoHeight = AndroidUtilities.dp(195f)
						mediaBackground = false
						currentUrl = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), true, 15, provider)
						currentWebFile = WebFile.createWithGeoPoint(point, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), 15, min(2, ceil(AndroidUtilities.density.toDouble()).toInt()))
						docTitleLayout = StaticLayoutEx.createStaticLayout(MessageObject.getMedia(messageObject.messageOwner)!!.title, Theme.chat_locationTitlePaint, maxWidth + AndroidUtilities.dp(4f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, 1)
						additionHeight += AndroidUtilities.dp(50f)

						val address = MessageObject.getMedia(messageObject.messageOwner)?.address

						if (!address.isNullOrEmpty()) {
							infoLayout = StaticLayoutEx.createStaticLayout(address, Theme.chat_locationAddressPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth, 1)
							measureTime(messageObject)

							val timeLeft = backgroundWidth - ceil(infoLayout!!.getLineWidth(0).toDouble()).toInt() - AndroidUtilities.dp(24f)
							val isRtl = infoLayout!!.getLineLeft(0) > 0
							if (isRtl || timeLeft < timeWidth + AndroidUtilities.dp((20 + if (messageObject.isOutOwner) 20 else 0).toFloat())) {
								additionHeight += AndroidUtilities.dp(if (isRtl) 10f else 8f)
							}
						}
						else {
							infoLayout = null
						}
					}
					else {
						if (!hasDiscussion) {
							drawBackground = false
						}

						backgroundWidth = if (AndroidUtilities.isTablet()) {
							min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp((252 + 37).toFloat()))
						}
						else {
							min(getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f), AndroidUtilities.dp((252 + 37).toFloat()))
						}

						backgroundWidth -= AndroidUtilities.dp(4f)

						if (checkNeedDrawShareButton(messageObject)) {
							backgroundWidth -= AndroidUtilities.dp(20f)
						}

						availableTimeWidth = backgroundWidth - AndroidUtilities.dp(34f)
						photoWidth = backgroundWidth - AndroidUtilities.dp(8f)
						photoHeight = AndroidUtilities.dp(195f)
						currentUrl = AndroidUtilities.formatMapUrl(currentAccount, lat, lon, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), true, 15, provider)
						currentWebFile = WebFile.createWithGeoPoint(point, (photoWidth / AndroidUtilities.density).toInt(), (photoHeight / AndroidUtilities.density).toInt(), 15, min(2, ceil(AndroidUtilities.density.toDouble()).toInt()))
					}

					currentMapProvider = if (messageObject.dialogId.toInt() == 0) {
						when (SharedConfig.mapPreviewType) {
							SharedConfig.MAP_PREVIEW_PROVIDER_ELLO -> MessagesController.MAP_PROVIDER_ELLO
							SharedConfig.MAP_PREVIEW_PROVIDER_GOOGLE -> MessagesController.MAP_PROVIDER_GOOGLE // was GOOGLE
							SharedConfig.MAP_PREVIEW_PROVIDER_YANDEX -> MessagesController.MAP_PROVIDER_YANDEX_NO_ARGS // not sure that it's correct, maybe WITH_ARGS
							else -> MessagesController.MAP_PROVIDER_UNDEFINED
						}
					}
					else {
						MessagesController.getInstance(messageObject.currentAccount).mapProvider
					}

					if (currentMapProvider == MessagesController.MAP_PROVIDER_UNDEFINED) {
						photoImage.setImage(null, null, null, null, messageObject, 0)
					}
					else if (currentMapProvider == MessagesController.MAP_PROVIDER_ELLO) {
						if (currentWebFile != null) {
							val lastLocation = ImageLocation.getForWebFile(lastWebFile)
							photoImage.setImage(ImageLocation.getForWebFile(currentWebFile), null, lastLocation, null, null as Drawable?, messageObject, 0)
						}
					}
					else {
						if (currentMapProvider == MessagesController.MAP_PROVIDER_YANDEX_WITH_ARGS || currentMapProvider == MessagesController.MAP_PROVIDER_GOOGLE) {
							ImageLoader.getInstance().addTestWebFile(currentUrl, currentWebFile)
							addedForTest = true
						}

						if (currentUrl != null) {
							photoImage.setImage(currentUrl, null, null, null, 0)
						}
					}

					if (!reactionsLayoutInBubble.isSmall && !reactionsLayoutInBubble.isEmpty) {
						reactionsLayoutInBubble.measure(backgroundWidth - AndroidUtilities.dp(16f), Gravity.LEFT)
						reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(14f)

						measureTime(messageObject)

						if (reactionsLayoutInBubble.lastLineX + timeWidth + AndroidUtilities.dp(24f) > backgroundWidth) {
							reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12f)
							reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(12f)
						}

						additionHeight += reactionsLayoutInBubble.totalHeight
					}
				}
				else if (messageObject.type == MessageObject.TYPE_EMOJIS) {
					drawBackground = false
					photoWidth = messageObject.textWidth
					photoHeight = messageObject.textHeight + AndroidUtilities.dp(32f)
					backgroundWidth = photoWidth + AndroidUtilities.dp(14f)
					availableTimeWidth = photoWidth - AndroidUtilities.dp(12f)

					var maxWidth = if (AndroidUtilities.isTablet()) {
						(AndroidUtilities.getMinTabletSide() * 0.4f).toInt()
					}
					else {
						(min(getParentWidth(), AndroidUtilities.displaySize.y) * 0.5f).toInt()
					}

					maxWidth = max(backgroundWidth, maxWidth)

					if (!reactionsLayoutInBubble.isSmall) {
						reactionsLayoutInBubble.measure(maxWidth, if (currentMessageObject!!.isOutOwner) Gravity.RIGHT else Gravity.LEFT)
						reactionsLayoutInBubble.drawServiceShaderBackground = true
						reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height // + AndroidUtilities.dp(8);

						additionHeight += reactionsLayoutInBubble.totalHeight + AndroidUtilities.dp(8f)

						reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(8f)
					}

					additionHeight -= AndroidUtilities.dp(17f)
				}
				else if (messageObject.isAnyKindOfSticker) {
					drawBackground = false

					val isWebpSticker = messageObject.type == MessageObject.TYPE_STICKER
					val stickerDocument = messageObject.document as? TLRPC.TLDocument

					if (stickerDocument != null) {
						for (a in stickerDocument.attributes.indices) {
							val attribute = stickerDocument.attributes[a]

							if (attribute is TLRPC.TLDocumentAttributeImageSize) {
								photoWidth = attribute.w
								photoHeight = attribute.h
								break
							}

							if (attribute is TLRPC.TLDocumentAttributeVideo) {
								photoWidth = attribute.w
								photoHeight = attribute.h
								break
							}
						}
					}

					if ((messageObject.isAnimatedSticker || messageObject.isVideoSticker) && photoWidth == 0 && photoHeight == 0) {
						photoHeight = 512
						photoWidth = photoHeight
					}

					if (messageObject.isAnimatedAnimatedEmoji) {
						photoWidth = max(512, photoWidth)
						photoHeight = max(512, photoHeight)
					}

					val maxHeight: Float
					val maxWidth: Int

					if (AndroidUtilities.isTablet()) {
						maxWidth = (AndroidUtilities.getMinTabletSide() * 0.4f).toInt()
						maxHeight = maxWidth.toFloat()
					}
					else {
						maxWidth = (min(getParentWidth(), AndroidUtilities.displaySize.y) * 0.5f).toInt()
						maxHeight = maxWidth.toFloat()
					}

					val filter: String

					if (messageObject.isAnimatedEmoji || messageObject.isDice) {
						val zoom = MessagesController.getInstance(currentAccount).animatedEmojisZoom
						photoWidth = (photoWidth / 512.0f * maxWidth * zoom).toInt()
						photoHeight = (photoHeight / 512.0f * maxHeight * zoom).toInt()
					}
					else {
						if (photoWidth == 0) {
							photoHeight = maxHeight.toInt()
							photoWidth = photoHeight + AndroidUtilities.dp(100f)
						}

						photoHeight *= (maxWidth / photoWidth.toFloat()).toInt()
						photoWidth = maxWidth

						if (photoHeight > maxHeight) {
							photoWidth *= (maxHeight / photoHeight).toInt()
							photoHeight = maxHeight.toInt()
						}
					}

					var parentObject: Any? = messageObject
					val w = (photoWidth / AndroidUtilities.density).toInt()
					val h = (photoHeight / AndroidUtilities.density).toInt()
					val shouldRepeatSticker = delegate != null && delegate!!.shouldRepeatSticker(messageObject)

					if (currentMessageObject!!.strippedThumb == null) {
						currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40)
					}
					else {
						currentPhotoObjectThumbStripped = currentMessageObject!!.strippedThumb
					}

					photoParentObject = messageObject.photoThumbsObject

					var thumb: Drawable? = null

					if (messageObject.isDice) {
						filter = String.format(Locale.US, "%d_%d_dice_%s_%s", w, h, messageObject.diceEmoji, messageObject.toString())

						photoImage.setAutoRepeat(2)

						val emoji = currentMessageObject!!.diceEmoji
						val stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(emoji)

						if (stickerSet != null) {
							if (stickerSet.documents.size > 0) {
								val value = currentMessageObject!!.diceValue

								if (value <= 0) {
									val document = stickerSet.documents[0]

									currentPhotoObjectThumb = if ("\uD83C\uDFB0" == emoji) {
										null
									}
									else {
										FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 40)
									}

									photoParentObject = document
								}
							}
						}
					}
					else if (messageObject.isAnimatedEmoji) {
						if (messageObject.emojiAnimatedSticker == null && messageObject.emojiAnimatedStickerId != null) {
							filter = String.format(Locale.US, "%d_%d_nr_messageId=%d", w, h, messageObject.stableId)
							thumb = DocumentObject.getCircleThumb(.4f, ResourcesCompat.getColor(context.resources, R.color.light_background, null), 0.65f)
							photoImage.setAutoRepeat(1)
							messageObject.loadAnimatedEmojiDocument()
						}
						else {
							filter = String.format(Locale.US, "%d_%d_nr_messageId=%d" + messageObject.emojiAnimatedStickerColor, w, h, messageObject.stableId)

							if (MessageObject.isAnimatedEmoji(messageObject.emojiAnimatedSticker)) {
								photoImage.setAutoRepeat(1)
							}
							else {
								photoImage.setAutoRepeat(if (shouldRepeatSticker) 2 else 3)
							}

							parentObject = MessageObject.getInputStickerSet(messageObject.emojiAnimatedSticker)

							if (messageObject.emojiAnimatedStickerId != null) {
								photoImage.setCrossfadeWithOldImage(true)
							}
						}
					}
					else if (SharedConfig.loopStickers || isWebpSticker && !messageObject.isVideoSticker) {
						filter = String.format(Locale.US, "%d_%d", w, h)
						photoImage.setAutoRepeat(1)
					}
					else {
						filter = String.format(Locale.US, "%d_%d_nr_messageId=%d", w, h, messageObject.stableId)
						photoImage.setAutoRepeat(if (shouldRepeatSticker) 2 else 3)
					}

					documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER
					availableTimeWidth = photoWidth - AndroidUtilities.dp(14f)
					backgroundWidth = photoWidth + AndroidUtilities.dp(12f)
					photoImage.setRoundRadius(0)
					canChangeRadius = false

					if (!messageObject.isOutOwner && MessageObject.isPremiumSticker(messageObject.document)) {
						flipImage = true
					}

					if (messageObject.document != null) {
						if (messageObject.isVideoSticker) {
							if (!SharedConfig.loopStickers) {
								photoImage.animatedFileDrawableRepeatMaxCount = 1
							}

							photoImage.setImage(ImageLocation.getForDocument(messageObject.document), ImageLoader.AUTOPLAY_FILTER, null, null, messageObject.pathThumb, messageObject.document!!.size, if (isWebpSticker) "webp" else null, parentObject, 1)
						}
						else if (messageObject.pathThumb != null) {
							photoImage.setImage(ImageLocation.getForDocument(messageObject.document), filter, messageObject.pathThumb, messageObject.document!!.size, if (isWebpSticker) "webp" else null, parentObject, 1)
						}
						else if (messageObject.attachPathExists) {
							photoImage.setImage(ImageLocation.getForPath(messageObject.messageOwner!!.attachPath), filter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), "b1", thumb ?: currentPhotoObjectThumbStripped, messageObject.document!!.size, if (isWebpSticker) "webp" else null, parentObject, 1)
						}
						else if (messageObject.document!!.id != 0L) {
							photoImage.setImage(ImageLocation.getForDocument(messageObject.document), filter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), "b1", thumb ?: currentPhotoObjectThumbStripped, messageObject.document!!.size, if (isWebpSticker) "webp" else null, parentObject, 1)
						}
						else {
							photoImage.setImage(null, null, thumb, null, messageObject, 0)
						}
					}
					else {
						photoImage.setImage(null, null, thumb, null, messageObject, 0)
					}

					if (!reactionsLayoutInBubble.isSmall) {
						reactionsLayoutInBubble.measure(maxWidth, if (currentMessageObject!!.isOutOwner && (currentMessageObject!!.isAnimatedEmoji || currentMessageObject!!.isAnyKindOfSticker)) Gravity.RIGHT else Gravity.LEFT)
						reactionsLayoutInBubble.drawServiceShaderBackground = true
						reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(8f)

						additionHeight += reactionsLayoutInBubble.totalHeight

						if (!currentMessageObject!!.isAnimatedEmoji) {
							reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(4f)
						}
					}
				}
				else {
					currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize())
					photoParentObject = messageObject.photoThumbsObject

					var useFullWidth = false

					if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
						documentAttach = messageObject.document
						documentAttachType = DOCUMENT_ATTACH_TYPE_ROUND
					}
					else {
						if (AndroidUtilities.isTablet()) {
							photoWidth = (AndroidUtilities.getMinTabletSide() * 0.7f).toInt()
						}
						else {
							if (currentPhotoObject != null && (messageObject.type == MessageObject.TYPE_PHOTO || messageObject.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW || messageObject.type == MessageObject.TYPE_VIDEO || messageObject.type == MessageObject.TYPE_GIF) && currentPhotoObject!!.w >= currentPhotoObject!!.h) {
								photoWidth = min(getParentWidth(), AndroidUtilities.displaySize.y) - AndroidUtilities.dp((64 + if (checkNeedDrawShareButton(messageObject)) 10 else 0).toFloat())
								useFullWidth = true
							}
							else {
								photoWidth = (min(getParentWidth(), AndroidUtilities.displaySize.y) * 0.7f).toInt()
							}
						}
					}

					photoHeight = photoWidth + AndroidUtilities.dp(100f)

					if (!useFullWidth) {
						if (messageObject.type != 5 && checkNeedDrawShareButton(messageObject)) {
							photoWidth -= AndroidUtilities.dp(20f)
						}

						if (photoWidth > AndroidUtilities.getPhotoSize()) {
							photoWidth = AndroidUtilities.getPhotoSize()
						}

						if (photoHeight > AndroidUtilities.getPhotoSize()) {
							photoHeight = AndroidUtilities.getPhotoSize()
						}
					}
					else if (drawAvatar) {
						photoWidth -= AndroidUtilities.dp(52f)
					}

					var needQualityPreview = false

					when (messageObject.type) {
						MessageObject.TYPE_PHOTO, MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW -> { //photo
							updateSecretTimeText(messageObject)
							currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40)
						}

						MessageObject.TYPE_VIDEO, 8 -> { //video, gif
							createDocumentLayout(0, messageObject)
							currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40)
							updateSecretTimeText(messageObject)
							needQualityPreview = true
						}

						MessageObject.TYPE_ROUND_VIDEO -> {
							currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40)
							needQualityPreview = true
						}
					}

					if (currentMessageObject?.strippedThumb != null) {
						currentPhotoObjectThumb = null
						currentPhotoObjectThumbStripped = currentMessageObject?.strippedThumb
					}

					var w: Int
					var h: Int

					if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
						if (isPlayingRound) {
							h = AndroidUtilities.roundPlayingMessageSize
							w = h
						}
						else {
							h = AndroidUtilities.roundMessageSize
							w = h
						}
					}
					else {
						val size = if (currentPhotoObject != null) currentPhotoObject else currentPhotoObjectThumb
						var imageW = 0
						var imageH = 0

						if (messageObject.hasExtendedMediaPreview()) {
							val preview = messageObject.messageOwner?.media?.extendedMedia as TLRPC.TLMessageExtendedMediaPreview

							if (preview.w != 0 && preview.h != 0) {
								imageW = preview.w
								imageH = preview.h
							}
							else if (preview.thumb != null) {
								imageW = preview.thumb!!.w
								imageH = preview.thumb!!.h
							}
						}
						else if (size != null && size !is TLRPC.TLPhotoStrippedSize) {
							imageW = size.w
							imageH = size.h
						}
						else if (documentAttach != null) {
							(documentAttach as? TLRPC.TLDocument)?.attributes?.forEach {
								if (it is TLRPC.TLDocumentAttributeVideo) {
									imageW = it.w
									imageH = it.h
								}
							}
						}

						val point = getMessageSize(imageW, imageH, photoWidth, photoHeight)

						w = point.x.toInt()
						h = point.y.toInt()
					}

					if ("s" == currentPhotoObject?.type) {
						currentPhotoObject = null
					}

					if (currentPhotoObject != null && currentPhotoObject === currentPhotoObjectThumb) {
						if (messageObject.type == MessageObject.TYPE_PHOTO || messageObject.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW) {
							currentPhotoObjectThumb = null
							currentPhotoObjectThumbStripped = null
						}
						else {
							currentPhotoObject = null
						}
					}

					if (needQualityPreview) {
						if (!messageObject.needDrawBluredPreview() && (currentPhotoObject == null || currentPhotoObject === currentPhotoObjectThumb) && (currentPhotoObjectThumb == null || "m" != currentPhotoObjectThumb!!.type)) {
							photoImage.isNeedsQualityThumb = true
							photoImage.isShouldGenerateQualityThumb = true
						}
					}

					if (currentMessagesGroup == null && messageObject.caption != null) {
						mediaBackground = false
					}

					if ((w == 0 || h == 0) && messageObject.type == MessageObject.TYPE_GIF) {
						val document = messageObject.document as? TLRPC.TLDocument

						if (document != null) {
							for (a in document.attributes.indices) {
								val attribute = document.attributes[a]

								if (attribute is TLRPC.TLDocumentAttributeImageSize || attribute is TLRPC.TLDocumentAttributeVideo) {
									val scale = attribute.w.toFloat() / photoWidth.toFloat()

									w = (attribute.w / scale).toInt()
									h = (attribute.h / scale).toInt()

									if (h > photoHeight) {
										var scale2 = h.toFloat()
										h = photoHeight
										scale2 /= h.toFloat()
										w = (w / scale2).toInt()
									}
									else if (h < AndroidUtilities.dp(120f)) {
										h = AndroidUtilities.dp(120f)
										val hScale = attribute.h.toFloat() / h

										if (attribute.w / hScale < photoWidth) {
											w = (attribute.w / hScale).toInt()
										}
									}

									break
								}
							}
						}
					}

					if (w == 0 || h == 0) {
						h = AndroidUtilities.dp(150f)
						w = h
					}

					if (messageObject.type == MessageObject.TYPE_VIDEO) {
						if (w < infoWidth + AndroidUtilities.dp((16 + 24).toFloat())) {
							w = infoWidth + AndroidUtilities.dp((16 + 24).toFloat())
						}
					}

					if (commentLayout != null && drawSideButton != 3 && w < totalCommentWidth + AndroidUtilities.dp(10f)) {
						w = totalCommentWidth + AndroidUtilities.dp(10f)
					}

					if (currentMessagesGroup != null) {
						var firstLineWidth = 0
						val dWidth = groupPhotosWidth

						for (a in currentMessagesGroup!!.posArray.indices) {
							val position = currentMessagesGroup!!.posArray[a]

							firstLineWidth += if (position.minY.toInt() == 0) {
								ceil(((position.pw + position.leftSpanOffset) / 1000.0f * dWidth).toDouble()).toInt()
							}
							else {
								break
							}
						}

						availableTimeWidth = firstLineWidth - AndroidUtilities.dp(35f)
					}
					else {
						availableTimeWidth = photoWidth - AndroidUtilities.dp(14f)
					}

					if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
						availableTimeWidth = (AndroidUtilities.roundMessageSize - ceil(Theme.chat_audioTimePaint.measureText("00:00").toDouble()) - AndroidUtilities.dp(46f)).toInt()
					}

					measureTime(messageObject)

					val timeWidthTotal = timeWidth + AndroidUtilities.dp(((if (SharedConfig.bubbleRadius >= 10) 22 else 18) + if (messageObject.isOutOwner) 20 else 0).toFloat())

					if (w < timeWidthTotal) {
						w = timeWidthTotal
					}

					if (messageObject.isRoundVideo) {
						h = min(w, h)
						w = h
						drawBackground = false
						photoImage.setRoundRadius((w * 12.45f).toInt())
						canChangeRadius = false
					}
					else if (messageObject.needDrawBluredPreview() && !messageObject.hasExtendedMediaPreview()) {
						if (AndroidUtilities.isTablet()) {
							h = (AndroidUtilities.getMinTabletSide() * 0.5f).toInt()
							w = h
						}
						else {
							h = (min(getParentWidth(), AndroidUtilities.displaySize.y) * 0.5f).toInt()
							w = h
						}
					}

					var widthForCaption = 0
					var fixPhotoWidth = false

					if (currentMessagesGroup != null) {
						val maxHeight = max(getParentWidth(), AndroidUtilities.displaySize.y) * 0.5f
						val dWidth = groupPhotosWidth

						w = ceil((currentPosition!!.pw / 1000.0f * dWidth).toDouble()).toInt()

						if (currentPosition!!.minY.toInt() != 0 && (messageObject.isOutOwner && currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0 || !messageObject.isOutOwner && currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT != 0)) {
							var firstLineWidth = 0
							var currentLineWidth = 0

							for (a in currentMessagesGroup!!.posArray.indices) {
								val position = currentMessagesGroup!!.posArray[a]

								if (position.minY.toInt() == 0) {
									firstLineWidth += (ceil((position.pw / 1000.0f * dWidth).toDouble()) + if (position.leftSpanOffset != 0) ceil((position.leftSpanOffset / 1000.0f * dWidth).toDouble()) else 0.0).toInt()
								}
								else if (position.minY == currentPosition!!.minY) {
									currentLineWidth += (ceil((position.pw / 1000.0f * dWidth).toDouble()) + if (position.leftSpanOffset != 0) ceil((position.leftSpanOffset / 1000.0f * dWidth).toDouble()) else 0.0).toInt()
								}
								else if (position.minY > currentPosition!!.minY) {
									break
								}
							}

							w += firstLineWidth - currentLineWidth
						}

						w -= AndroidUtilities.dp(9f)

						if (isAvatarVisible) {
							w -= AndroidUtilities.dp(48f)
						}

						if (currentPosition?.siblingHeights != null) {
							h = 0

							currentPosition?.siblingHeights?.sumOf {
								ceil((maxHeight * it).toDouble()).toInt()
							}?.let {
								h += it
							}

							h += (currentPosition!!.maxY - currentPosition!!.minY) * (7 * AndroidUtilities.density).roundToInt() //TODO fix
						}
						else {
							h = if (messageObject.isRoundVideo) {
								w
							}
							else {
								ceil((maxHeight * currentPosition!!.ph).toDouble()).toInt()
							}
						}

						backgroundWidth = w

						w -= if (currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT != 0 && currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0) {
							AndroidUtilities.dp(8f)
						}
						else if (currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT == 0 && currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
							AndroidUtilities.dp(11f)
						}
						else if (currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT != 0) {
							AndroidUtilities.dp(10f)
						}
						else {
							AndroidUtilities.dp(9f)
						}

						photoWidth = w

						if (!currentPosition!!.edge) {
							photoWidth += AndroidUtilities.dp(10f)
						}

						photoHeight = h
						widthForCaption += photoWidth - AndroidUtilities.dp(10f)

						var checkCaption = true

						if (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0 || currentMessagesGroup!!.hasSibling && currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP == 0) {
							widthForCaption += getAdditionalWidthForPosition(currentPosition)

							val count = currentMessagesGroup!!.messages.size

							for (i in 0 until count) {
								val m = currentMessagesGroup!!.messages[i]

								currentMessagesGroup?.posArray?.get(i)?.let { rowPosition ->
									if (rowPosition !== currentPosition && rowPosition.flags and MessageObject.POSITION_FLAG_BOTTOM != 0) {
										w = ceil((rowPosition.pw / 1000.0f * dWidth).toDouble()).toInt()

										if (rowPosition.minY.toInt() != 0 && (messageObject.isOutOwner && rowPosition.flags and MessageObject.POSITION_FLAG_LEFT != 0 || !messageObject.isOutOwner && rowPosition.flags and MessageObject.POSITION_FLAG_RIGHT != 0)) {
											var firstLineWidth = 0
											var currentLineWidth = 0

											for (a in currentMessagesGroup!!.posArray.indices) {
												val position = currentMessagesGroup!!.posArray[a]

												if (position.minY.toInt() == 0) {
													firstLineWidth += (ceil((position.pw / 1000.0f * dWidth).toDouble()) + if (position.leftSpanOffset != 0) ceil((position.leftSpanOffset / 1000.0f * dWidth).toDouble()) else 0.0).toInt()
												}
												else if (position.minY == rowPosition.minY) {
													currentLineWidth += (ceil((position.pw / 1000.0f * dWidth).toDouble()) + if (position.leftSpanOffset != 0) ceil((position.leftSpanOffset / 1000.0f * dWidth).toDouble()) else 0.0).toInt()
												}
												else if (position.minY > rowPosition.minY) {
													break
												}
											}

											w += firstLineWidth - currentLineWidth
										}

										w -= AndroidUtilities.dp(9f)

										w -= if (rowPosition.flags and MessageObject.POSITION_FLAG_RIGHT != 0 && rowPosition.flags and MessageObject.POSITION_FLAG_LEFT != 0) {
											AndroidUtilities.dp(8f)
										}
										else if (rowPosition.flags and MessageObject.POSITION_FLAG_RIGHT == 0 && rowPosition.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
											AndroidUtilities.dp(11f)
										}
										else if (rowPosition.flags and MessageObject.POSITION_FLAG_RIGHT != 0) {
											AndroidUtilities.dp(10f)
										}
										else {
											AndroidUtilities.dp(9f)
										}

										if (isChat && !isThreadPost && !m.isOutOwner && m.needDrawAvatar() && rowPosition.edge) {
											w -= AndroidUtilities.dp(48f)
										}

										w += getAdditionalWidthForPosition(rowPosition)

										if (!rowPosition.edge) {
											w += AndroidUtilities.dp(10f)
										}

										widthForCaption += w

										if (rowPosition.minX < currentPosition!!.minX || currentMessagesGroup!!.hasSibling && rowPosition.minY != rowPosition.maxY) {
											captionOffsetX -= w
										}
									}
								}

								if (checkCaption) {
									if (m.caption != null) {
										if (currentCaption != null) {
											currentCaption = null
											checkCaption = false
										}
										else {
											currentCaption = m.caption
										}
									}
								}
							}
						}
					}
					else {
						photoHeight = h
						photoWidth = w
						currentCaption = messageObject.caption

						val minCaptionWidth = if (AndroidUtilities.isTablet()) {
							(AndroidUtilities.getMinTabletSide() * 0.65f).toInt()
						}
						else {
							(min(getParentWidth(), AndroidUtilities.displaySize.y) * 0.65f).toInt()
						}

						if (!messageObject.needDrawBluredPreview() && (currentCaption != null || !reactionsLayoutInBubble.isEmpty) && !reactionsLayoutInBubble.isSmall && photoWidth < minCaptionWidth) {
							widthForCaption = minCaptionWidth
							fixPhotoWidth = true
						}
						else {
							widthForCaption = photoWidth - AndroidUtilities.dp(10f)
						}

						backgroundWidth = photoWidth + AndroidUtilities.dp(8f)

						if (!mediaBackground) {
							backgroundWidth += AndroidUtilities.dp(9f)
						}
					}

					if (currentCaption != null) {
						try {
							widthForCaption -= extraTextX * 2

							captionLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
								StaticLayout.Builder.obtain(currentCaption!!, 0, currentCaption!!.length, Theme.chat_msgTextPaint, widthForCaption).setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
							}
							else {
								StaticLayout(currentCaption, Theme.chat_msgTextPaint, widthForCaption, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
							}

							updateCaptionSpoilers()

							val lineCount = captionLayout!!.lineCount

							if (lineCount > 0) {
								if (fixPhotoWidth) {
									captionWidth = 0

									for (a in 0 until lineCount) {
										captionWidth = max(captionWidth.toDouble(), ceil(captionLayout!!.getLineWidth(a).toDouble())).toInt()

										if (captionLayout!!.getLineLeft(a) != 0f) {
											captionWidth = widthForCaption
											break
										}
									}

									if (captionWidth > widthForCaption) {
										captionWidth = widthForCaption
									}
								}
								else {
									captionWidth = widthForCaption
								}

								captionHeight = captionLayout!!.height
								addedCaptionHeight = captionHeight + AndroidUtilities.dp(9f)

								if (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0) {
									additionHeight += addedCaptionHeight

									val widthToCheck = max(captionWidth, photoWidth - AndroidUtilities.dp(10f))
									val lastLineWidth = captionLayout!!.getLineWidth(captionLayout!!.lineCount - 1) + captionLayout!!.getLineLeft(captionLayout!!.lineCount - 1)

									if ((reactionsLayoutInBubble.isEmpty || reactionsLayoutInBubble.isSmall) && !shouldDrawTimeOnMedia() && widthToCheck + AndroidUtilities.dp(2f) - lastLineWidth < timeWidthTotal + extraTimeX) {
										additionHeight += AndroidUtilities.dp(14f)
										addedCaptionHeight += AndroidUtilities.dp(14f)
										captionNewLine = 1
									}
								}
								else {
									captionLayout = null
									updateCaptionSpoilers()
								}
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					if (!reactionsLayoutInBubble.isSmall) {
						val useBackgroundWidth = backgroundWidth - AndroidUtilities.dp(24f) > widthForCaption
						val maxWidth = max(backgroundWidth - AndroidUtilities.dp(36f), widthForCaption)

						reactionsLayoutInBubble.measure(maxWidth, Gravity.LEFT)

						if (!reactionsLayoutInBubble.isEmpty) {
							if (shouldDrawTimeOnMedia()) {
								reactionsLayoutInBubble.drawServiceShaderBackground = true
							}

							var heightLocal = reactionsLayoutInBubble.height

							if (captionLayout == null) {
								heightLocal += AndroidUtilities.dp(12f)
								heightLocal += AndroidUtilities.dp(4f)
							}
							else {
								heightLocal += AndroidUtilities.dp(12f)
								reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(12f)
							}

							reactionsLayoutInBubble.totalHeight = heightLocal
							additionHeight += reactionsLayoutInBubble.totalHeight

							if (!shouldDrawTimeOnMedia()) {
								val widthToCheck = min(maxWidth, reactionsLayoutInBubble.width + timeWidthTotal + extraTimeX + AndroidUtilities.dp(2f))
								val lastLineWidth = reactionsLayoutInBubble.lastLineX.toFloat()

								if (!shouldDrawTimeOnMedia() && widthToCheck - lastLineWidth < timeWidthTotal + extraTimeX) {
									additionHeight += AndroidUtilities.dp(14f)

									reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(14f)
									reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(14f)

									captionNewLine = 1

									if (!useBackgroundWidth && captionWidth < reactionsLayoutInBubble.width) {
										captionWidth = reactionsLayoutInBubble.width
									}
								}
								else if (!useBackgroundWidth) {
									if (reactionsLayoutInBubble.lastLineX + timeWidthTotal > captionWidth) {
										captionWidth = reactionsLayoutInBubble.lastLineX + timeWidthTotal
									}

									if (reactionsLayoutInBubble.width > captionWidth) {
										captionWidth = reactionsLayoutInBubble.width
									}
								}
							}
						}
					}

					val minWidth = (Theme.chat_infoPaint.measureText("100%") + AndroidUtilities.dp(100f /*48*/) /* + timeWidth*/).toInt()

					if (currentMessagesGroup == null && (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) && photoWidth < minWidth) {
						photoWidth = minWidth
						backgroundWidth = photoWidth + AndroidUtilities.dp(8f)

						if (!mediaBackground) {
							backgroundWidth += AndroidUtilities.dp(9f)
						}
					}

					if (fixPhotoWidth && photoWidth < captionWidth + AndroidUtilities.dp(10f)) {
						photoWidth = captionWidth + AndroidUtilities.dp(10f)
						backgroundWidth = photoWidth + AndroidUtilities.dp(8f)

						if (!mediaBackground) {
							backgroundWidth += AndroidUtilities.dp(9f)
						}
					}

					if (messageChanged || messageIdChanged || dataChanged) {
						currentPhotoFilterThumb = String.format(Locale.US, "%d_%d", (w / AndroidUtilities.density).toInt(), (h / AndroidUtilities.density).toInt())
						currentPhotoFilter = currentPhotoFilterThumb

						if (messageObject.photoThumbs != null && messageObject.photoThumbs!!.size > 1 || messageObject.type == MessageObject.TYPE_VIDEO || messageObject.type == MessageObject.TYPE_GIF || messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
							if (messageObject.needDrawBluredPreview()) {
								currentPhotoFilter += "_b2"
								currentPhotoFilterThumb += "_b2"
							}
							else {
								currentPhotoFilterThumb += "_b"
							}
						}
					}
					else {
						val filterNew = String.format(Locale.US, "%d_%d", (w / AndroidUtilities.density).toInt(), (h / AndroidUtilities.density).toInt())

						if (!messageObject.needDrawBluredPreview() && filterNew != currentPhotoFilter) {
							val location = ImageLocation.getForObject(currentPhotoObject, photoParentObject)

							if (location != null) {
								val key = location.getKey(photoParentObject, null, false) + "@" + currentPhotoFilter

								if (ImageLoader.getInstance().isInMemCache(key, false)) {
									currentPhotoObjectThumb = currentPhotoObject
									currentPhotoFilterThumb = currentPhotoFilter
									currentPhotoFilter = filterNew
								}
							}
						}
					}

					val noSize = messageObject.type == MessageObject.TYPE_VIDEO || messageObject.type == MessageObject.TYPE_GIF || messageObject.type == MessageObject.TYPE_ROUND_VIDEO

					if (currentPhotoObject != null && !noSize && currentPhotoObject!!.size == 0) {
						currentPhotoObject!!.size = -1
					}

					if (currentPhotoObjectThumb != null && !noSize && currentPhotoObjectThumb!!.size == 0) {
						currentPhotoObjectThumb!!.size = -1
					}

					if (SharedConfig.autoplayVideo && messageObject.type == MessageObject.TYPE_VIDEO && !messageObject.needDrawBluredPreview() && (currentMessageObject!!.mediaExists || messageObject.canStreamVideo() && DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject))) {
						autoPlayingMedia = if (currentPosition != null) {
							currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0 && currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT != 0
						}
						else {
							true
						}
					}

					if (autoPlayingMedia) {
						photoImage.allowStartAnimation = true
						photoImage.startAnimation()

						val document = messageObject.document

						if (currentMessageObject!!.videoEditedInfo != null && currentMessageObject!!.videoEditedInfo!!.canAutoPlaySourceVideo() && messageObject.document != null) {
							photoImage.setImage(ImageLocation.getForPath(currentMessageObject!!.videoEditedInfo!!.originalPath), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObjectThumb, document), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, messageObject.document!!.size, null, messageObject, 0)
							photoImage.setMediaStartEndTime(currentMessageObject!!.videoEditedInfo!!.startTime / 1000, currentMessageObject!!.videoEditedInfo!!.endTime / 1000)
						}
						else if (messageObject.document != null) {
							if (!messageIdChanged && !dataChanged) {
								photoImage.setCrossfadeWithOldImage(true)
							}

							photoImage.setImage(ImageLocation.getForDocument(document), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObjectThumb, document), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, messageObject.document!!.size, null, messageObject, 0)
						}
					}
					else if (messageObject.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW) {
						photoImage.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, currentMessageObject, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
					}
					else if (messageObject.type == MessageObject.TYPE_PHOTO) {
						if (messageObject.useCustomPhoto) {
							photoImage.setImageBitmap(ResourcesCompat.getDrawable(resources, R.drawable.theme_preview_image, null))
						}
						else {
							if (currentPhotoObject != null) {
								var photoExist = true
								val fileName = FileLoader.getAttachFileName(currentPhotoObject)

								if (messageObject.mediaExists) {
									DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
								}
								else {
									photoExist = false
								}

								if (photoExist || !currentMessageObject!!.loadingCancelled && DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject) || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
									photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, currentPhotoObject!!.size.toLong(), null, currentMessageObject, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
								}
								else {
									photoNotSet = true

									if (currentPhotoObjectThumb != null || currentPhotoObjectThumbStripped != null) {
										photoImage.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, currentMessageObject, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
									}
									else {
										photoImage.setImageBitmap(null as Drawable?)
									}
								}
							}
							else {
								photoImage.setImageBitmap(null as Drawable?)
							}
						}
					}
					else if (messageObject.type == MessageObject.TYPE_GIF || messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
						val fileName = FileLoader.getAttachFileName(messageObject.document)
						var localFile = 0

						if (messageObject.attachPathExists) {
							DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
							localFile = 1
						}
						else if (messageObject.mediaExists) {
							localFile = 2
						}

						var autoDownload = false
						val document = messageObject.document

						if (MessageObject.isGifDocument(document, messageObject.hasValidGroupId()) || messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
							autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject)
						}

						val videoSize = MessageObject.getDocumentVideoThumb(document)

						if (MessageObject.isGifDocument(document, messageObject.hasValidGroupId()) && messageObject.videoEditedInfo == null || !messageObject.isSending && !messageObject.isEditing && (localFile != 0 || FileLoader.getInstance(currentAccount).isLoadingFile(fileName) || autoDownload)) {
							if (localFile != 1 && !messageObject.needDrawBluredPreview() && (localFile != 0 || messageObject.canStreamVideo() && autoDownload)) {
								autoPlayingMedia = true

								if (!messageIdChanged) {
									photoImage.setCrossfadeWithOldImage(true)
									photoImage.setCrossfadeDuration(250)
								}

								if (localFile == 0 && videoSize != null && (currentPhotoObject == null || currentPhotoObjectThumb == null)) {
									photoImage.setImage(ImageLocation.getForDocument(document), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(videoSize, documentAttach), null, ImageLocation.getForDocument(if (currentPhotoObject != null) currentPhotoObject else currentPhotoObjectThumb, documentAttach), if (currentPhotoObject != null) currentPhotoFilter else currentPhotoFilterThumb, currentPhotoObjectThumbStripped, document!!.size, null, messageObject, 0)
								}
								else {
									if (isRoundVideo && !messageIdChanged && photoImage.hasStaticThumb()) {
										photoImage.setImage(ImageLocation.getForDocument(document), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, null, null, photoImage.staticThumb, document!!.size, null, messageObject, 0)
									}
									else {
										photoImage.setImage(ImageLocation.getForDocument(document), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, document!!.size, null, messageObject, 0)
									}
								}
							}
							else if (localFile == 1) {
								photoImage.setImage(ImageLocation.getForPath(if (messageObject.isSendError) null else messageObject.messageOwner?.attachPath), null, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, messageObject, 0)
							}
							else {
								if (videoSize != null && (currentPhotoObject == null || currentPhotoObjectThumb == null)) {
									photoImage.setImage(ImageLocation.getForDocument(document), null, ImageLocation.getForDocument(videoSize, documentAttach), null, ImageLocation.getForDocument(if (currentPhotoObject != null) currentPhotoObject else currentPhotoObjectThumb, documentAttach), if (currentPhotoObject != null) currentPhotoFilter else currentPhotoFilterThumb, currentPhotoObjectThumbStripped, document!!.size, null, messageObject, 0)
								}
								else {
									photoImage.setImage(ImageLocation.getForDocument(document), null, ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, document!!.size, null, messageObject, 0)
								}
							}
						}
						else {
							if (messageObject.videoEditedInfo != null && messageObject.type == MessageObject.TYPE_ROUND_VIDEO && !currentMessageObject!!.needDrawBluredPreview()) {
								photoImage.setImage(ImageLocation.getForPath(messageObject.videoEditedInfo!!.originalPath), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, messageObject, 0)
								photoImage.setMediaStartEndTime(currentMessageObject!!.videoEditedInfo!!.startTime / 1000, currentMessageObject!!.videoEditedInfo!!.endTime / 1000)
							}
							else {
								if (!messageIdChanged && !currentMessageObject!!.needDrawBluredPreview()) {
									photoImage.setCrossfadeWithOldImage(true)
									photoImage.setCrossfadeDuration(250)
								}

								photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, messageObject, 0)
							}
						}
					}
					else {
						if (messageObject.videoEditedInfo != null && messageObject.type == MessageObject.TYPE_ROUND_VIDEO && !currentMessageObject!!.needDrawBluredPreview()) {
							photoImage.setImage(ImageLocation.getForPath(messageObject.videoEditedInfo!!.originalPath), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, messageObject, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
							photoImage.setMediaStartEndTime(currentMessageObject!!.videoEditedInfo!!.startTime / 1000, currentMessageObject!!.videoEditedInfo!!.endTime / 1000)
						}
						else {
							if (!messageIdChanged && !currentMessageObject!!.needDrawBluredPreview()) {
								photoImage.setCrossfadeWithOldImage(true)
								photoImage.setCrossfadeDuration(250)
							}

							photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, messageObject, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
						}
					}
				}

				setMessageObjectInternal(messageObject)

				if (drawForwardedName && messageObject.needDrawForwarded() && (currentPosition == null || currentPosition!!.minY.toInt() == 0)) {
					if (messageObject.type != 5) {
						namesOffset += AndroidUtilities.dp(5f)
					}
				}
				else if (drawNameLayout && (messageObject.replyMsgId == 0 || isThreadChat && messageObject.replyTopMsgId == 0)) {
					namesOffset += AndroidUtilities.dp(7f)
				}

				totalHeight = photoHeight + AndroidUtilities.dp(14f) + namesOffset + additionHeight

				if (!shouldDrawTimeOnMedia()) {
					totalHeight += CELL_HEIGHT_INCREASE
				}

				if (currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0 && !currentMessageObject!!.isDocument() && currentMessageObject!!.type != MessageObject.TYPE_EMOJIS) {
					totalHeight -= AndroidUtilities.dp(3f)
				}

				if (currentMessageObject!!.isDice) {
					totalHeight += AndroidUtilities.dp(21f)
					additionalTimeOffsetY = AndroidUtilities.dp(21f)
				}

				var additionalTop = 0

				if (currentPosition != null && !currentMessageObject!!.isDocument()) {
					photoWidth += getAdditionalWidthForPosition(currentPosition)

					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP == 0) {
						photoHeight += AndroidUtilities.dp(4f)
						additionalTop -= AndroidUtilities.dp(4f)
					}

					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0) {
						photoHeight += AndroidUtilities.dp(1f)
					}
				}

				var y = 0

				if (currentMessageObject!!.type != MessageObject.TYPE_EMOJIS) {
					if (drawPinnedTop) {
						namesOffset -= AndroidUtilities.dp(1f)
					}

					if (namesOffset > 0) {
						y = AndroidUtilities.dp(7f)
						totalHeight -= AndroidUtilities.dp(2f)
					}
					else {
						y = AndroidUtilities.dp(5f)
						totalHeight -= AndroidUtilities.dp(4f)
					}
				}

				if (currentPosition != null && currentMessagesGroup!!.isDocuments && currentMessagesGroup!!.messages.size > 1) {
					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP == 0) {
						totalHeight -= AndroidUtilities.dp(if (drawPhotoImage) 3f else 6f)
						mediaOffsetY -= AndroidUtilities.dp(if (drawPhotoImage) 3f else 6f)

						y -= AndroidUtilities.dp(if (drawPhotoImage) 3f else 6f)
					}

					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0) {
						totalHeight -= AndroidUtilities.dp(if (drawPhotoImage) 3f else 6f)
					}
				}

				photoImage.setImageCoordinates(0f, (y + namesOffset + additionalTop).toFloat(), photoWidth.toFloat(), photoHeight.toFloat())

				invalidate()
			}

			if ((currentPosition == null || currentMessageObject!!.isMusic || currentMessageObject!!.isDocument()) && !messageObject.isAnyKindOfSticker && addedCaptionHeight == 0) {
				if (!messageObject.isRestrictedMessage && captionLayout == null && (messageObject.caption != null || messageObject.isVoiceTranscriptionOpen)) {
					currentCaption = if (messageObject.isVoiceTranscriptionOpen) messageObject.voiceTranscription else messageObject.caption

					if (currentCaption != null && !TextUtils.isEmpty(messageObject.messageOwner?.voiceTranscription) && currentMessageObject!!.isVoiceTranscriptionOpen && !currentMessageObject!!.messageOwner!!.voiceTranscriptionFinal) {
						// currentCaption +=  " "

						currentCaption = currentCaption?.run { StringBuilder(this).append(" ") }

						if (currentCaption !is Spannable) {
							currentCaption = SpannableString(currentCaption)
						}

						(currentCaption as SpannableString).setSpan(LoadingPointsSpan(), currentCaption!!.length - 1, currentCaption!!.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					try {
						var width = backgroundWidth

						if (messageObject.isVoiceTranscriptionOpen) {
							width = if (AndroidUtilities.isTablet()) {
								AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f)
							}
							else {
								getParentWidth() - AndroidUtilities.dp(if (drawAvatar) 102f else 50f)
							}
						}

						val widthForCaption = width - AndroidUtilities.dp(31f) - AndroidUtilities.dp(10f) - extraTextX * 2

						captionLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
							StaticLayout.Builder.obtain(currentCaption!!, 0, currentCaption!!.length, Theme.chat_msgTextPaint, widthForCaption).setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
						}
						else {
							StaticLayout(currentCaption, Theme.chat_msgTextPaint, widthForCaption, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
						}

						updateSeekBarWaveformWidth()
						updateCaptionSpoilers()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				if (captionLayout != null || currentMessageObject!!.type == MessageObject.TYPE_VOICE) {
					try {
						if (messageObject.isVoiceTranscriptionOpen && captionLayout != null) {
							val startMaxWidth = (backgroundWidth - AndroidUtilities.dp(31f) - AndroidUtilities.dp(10f) - extraTextX * 2).toFloat()
							var maxWidth = startMaxWidth

							for (i in 0 until captionLayout!!.lineCount) {
								val captionLineWidth = captionLayout!!.getLineWidth(i)

								if (captionLineWidth > maxWidth) {
									maxWidth = captionLineWidth
								}
							}

							backgroundWidth += (maxWidth - startMaxWidth).toInt()
						}

						val width = backgroundWidth - AndroidUtilities.dp(31f)
						var lastCaptionLineWidth: Float? = null

						if (captionLayout != null && captionLayout!!.lineCount > 0) {
							lastCaptionLineWidth = captionLayout!!.getLineWidth(captionLayout!!.lineCount - 1) + captionLayout!!.getLineLeft(captionLayout!!.lineCount - 1)
						}
						else if (currentMessageObject!!.type == MessageObject.TYPE_VOICE) {
							lastCaptionLineWidth = AndroidUtilities.dp(64f).toFloat()
						}

						if (lastCaptionLineWidth != null) {
							captionLayout?.let {
								captionWidth = width
								captionHeight = it.height
							}

							totalHeight += captionHeight + AndroidUtilities.dp(9f)

							if ((reactionsLayoutInBubble.isEmpty || reactionsLayoutInBubble.isSmall) && (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0)) {
								val timeWidthTotal = timeWidth + (if (messageObject.isOutOwner) AndroidUtilities.dp(20f) else 0) + extraTimeX

								if (width - AndroidUtilities.dp(8f) - lastCaptionLineWidth < timeWidthTotal) {
									totalHeight += AndroidUtilities.dp(14f)

									if (captionLayout != null) {
										captionHeight += AndroidUtilities.dp(14f)
										captionNewLine = 2
									}
								}
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

//			if ((currentPosition == null || (currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) && captionLayout == null && widthBeforeNewTimeLine != -1 && availableTimeWidth - widthBeforeNewTimeLine < timeWidth) {
//                totalHeight += AndroidUtilities.dp(14);
//			}

			if (currentMessageObject!!.eventId != 0L && !currentMessageObject!!.isMediaEmpty && MessageObject.getMedia(currentMessageObject!!.messageOwner)?.webpage != null) {
				val linkPreviewMaxWidth = backgroundWidth - AndroidUtilities.dp(41f)

				hasOldCaptionPreview = true
				linkPreviewHeight = 0

				val webPage = MessageObject.getMedia(currentMessageObject!!.messageOwner)!!.webpage

				try {
					siteNameWidth = ceil((Theme.chat_replyNamePaint.measureText(webPage?.siteName) + 1).toDouble()).toInt()

					val width = siteNameWidth

					siteNameLayout = StaticLayout(webPage?.siteName, Theme.chat_replyNamePaint, min(width, linkPreviewMaxWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
					siteNameRtl = siteNameLayout!!.getLineLeft(0) != 0f

					val height = siteNameLayout!!.getLineBottom(siteNameLayout!!.lineCount - 1)

					linkPreviewHeight += height
					totalHeight += height
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				try {
					descriptionX = 0

					if (linkPreviewHeight != 0) {
						totalHeight += AndroidUtilities.dp(2f)
					}

					descriptionLayout = StaticLayoutEx.createStaticLayout(webPage?.description, Theme.chat_replyTextPaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1f).toFloat(), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 6)

					val height = descriptionLayout!!.getLineBottom(descriptionLayout!!.lineCount - 1)

					linkPreviewHeight += height
					totalHeight += height

					var hasNonRtl = false

					for (a in 0 until descriptionLayout!!.lineCount) {
						val lineLeft = ceil(descriptionLayout!!.getLineLeft(a).toDouble()).toInt()

						if (lineLeft != 0) {
							descriptionX = if (descriptionX == 0) {
								-lineLeft
							}
							else {
								max(descriptionX, -lineLeft)
							}
						}
						else {
							hasNonRtl = true
						}
					}

					if (hasNonRtl) {
						descriptionX = 0
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				if (messageObject.type == MessageObject.TYPE_PHOTO || messageObject.type == MessageObject.TYPE_VIDEO || messageObject.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW) {
					totalHeight += AndroidUtilities.dp(6f)
				}

				totalHeight += AndroidUtilities.dp(17f)

				if (captionNewLine != 0) {
					totalHeight -= AndroidUtilities.dp(14f)

					if (captionNewLine == 2) {
						captionHeight -= AndroidUtilities.dp(14f)
					}
				}
			}

			if (messageObject.isSponsored) {
				drawInstantView = true

				drawInstantViewType = if (messageObject.sponsoredChannelPost != 0) 12 else 1

				val id = MessageObject.getPeerId(messageObject.messageOwner?.fromId)

				if (id > 0) {
					val user = MessagesController.getInstance(currentAccount).getUser(id) as? TLRPC.TLUser

					if (user != null && user.bot) {
						drawInstantViewType = 10
					}
				}

				createInstantViewButton()
			}

			botButtons.clear()

			if (messageIdChanged) {
				botButtonsByData.clear()
				botButtonsByPosition.clear()
				botButtonsLayout = null
			}

			if (!messageObject.isRestrictedMessage && (currentPosition == null) && messageObject.messageOwner?.replyMarkup is TLRPC.TLReplyInlineMarkup && !messageObject.hasExtendedMedia()) {
				val rows = if (messageObject.messageOwner?.replyMarkup is TLRPC.TLReplyInlineMarkup) {
					messageObject.messageOwner?.replyMarkup?.rows?.size ?: 0
				}
				else {
					1
				}

				keyboardHeight = AndroidUtilities.dp((44 + 4).toFloat()) * rows + AndroidUtilities.dp(1f)
				substractBackgroundHeight = keyboardHeight
				widthForButtons = backgroundWidth - AndroidUtilities.dp(if (mediaBackground) 0f else 9f)

				if (messageObject.wantedBotKeyboardWidth > widthForButtons) {
					var maxButtonWidth = -AndroidUtilities.dp(if (drawAvatar) 62f else 10f)

					maxButtonWidth += if (AndroidUtilities.isTablet()) {
						AndroidUtilities.getMinTabletSide()
					}
					else {
						min(getParentWidth(), AndroidUtilities.displaySize.y) - AndroidUtilities.dp(5f)
					}

					widthForButtons = max(backgroundWidth, min(messageObject.wantedBotKeyboardWidth, maxButtonWidth))
				}

				var maxButtonsWidth = 0
				val oldByData = HashMap(botButtonsByData)
				val oldByPosition: HashMap<String, BotButton>?

				if (messageObject.botButtonsLayout != null && botButtonsLayout != null && botButtonsLayout == messageObject.botButtonsLayout.toString()) {
					oldByPosition = HashMap(botButtonsByPosition)
				}
				else {
					if (messageObject.botButtonsLayout != null) {
						botButtonsLayout = messageObject.botButtonsLayout.toString()
					}

					oldByPosition = null
				}

				botButtonsByData.clear()

				if (messageObject.messageOwner?.replyMarkup is TLRPC.TLReplyInlineMarkup) {
					for (a in 0 until rows) {
						val row = messageObject.messageOwner?.replyMarkup?.rows?.get(a)

						val buttonsCount = row?.buttons?.size ?: continue

						if (buttonsCount == 0) {
							continue
						}

						val buttonWidth = (widthForButtons - AndroidUtilities.dp(5f) * (buttonsCount - 1) - AndroidUtilities.dp(2f)) / buttonsCount

						for (b in row.buttons.indices) {
							val botButton = BotButton()
							botButton.button = row.buttons[b]

							val key = Utilities.bytesToHex(botButton.button!!.data)
							val position = a.toString() + "" + b

							val oldButton = if (oldByPosition != null) {
								oldByPosition[position]
							}
							else {
								oldByData[key]
							}

							if (oldButton != null) {
								botButton.progressAlpha = oldButton.progressAlpha
								botButton.angle = oldButton.angle
								botButton.lastUpdateTime = oldButton.lastUpdateTime
							}
							else {
								botButton.lastUpdateTime = System.currentTimeMillis()
							}

							botButtonsByData[key] = botButton
							botButtonsByPosition[position] = botButton

							botButton.x = b * (buttonWidth + AndroidUtilities.dp(5f))
							botButton.y = a * AndroidUtilities.dp((44 + 4).toFloat()) + AndroidUtilities.dp(5f)
							botButton.width = buttonWidth
							botButton.height = AndroidUtilities.dp(44f)

							var buttonText: CharSequence?

							val botButtonPaint = botButtonTextPaint.apply {
								color = context.getColor(R.color.dark_fixed)
							}

							if (botButton.button is TLRPC.TLKeyboardButtonBuy && MessageObject.getMedia(messageObject.messageOwner)!!.flags and 4 != 0) {
								buttonText = context.getString(R.string.PaymentReceipt)
							}
							else {
								buttonText = botButton.button?.text ?: ""
								buttonText = Emoji.replaceEmoji(buttonText, botButtonPaint.fontMetricsInt, false)
								buttonText = TextUtils.ellipsize(buttonText, botButtonPaint, (buttonWidth - AndroidUtilities.dp(10f)).toFloat(), TextUtils.TruncateAt.END)
							}

							botButton.title = StaticLayout(buttonText, botButtonPaint, buttonWidth - AndroidUtilities.dp(10f), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

							botButtons.add(botButton)

							if (b == row.buttons.size - 1) {
								maxButtonsWidth = max(maxButtonsWidth, botButton.x + botButton.width)
							}

							if (messageObject.isFromUser && botButton.button is TLRPC.TLKeyboardButtonUrl) {
								runCatching {
									botButton.button?.url?.let {
										val uri = it.toUri()
										val host = uri.host?.lowercase()

										botButton.isInviteButton = uri.getQueryParameter("startgroup") != null && ("http" == uri.scheme || "https" == uri.scheme && ApplicationLoader.applicationContext.getString(R.string.domain) == host || "tg2" == uri.scheme && ((botButton.button?.url?.startsWith("tg2:resolve") == true) || (botButton.button?.url?.startsWith("tg2://resolve") == true)))
									}
								}
							}
						}
					}
				}

				widthForButtons = maxButtonsWidth
			}
			else {
				substractBackgroundHeight = 0
				keyboardHeight = 0
			}

			if (drawCommentButton) {
				totalHeight += AndroidUtilities.dp(if (shouldDrawTimeOnMedia()) 41.3f else 43f)
				createSelectorDrawable(1)
			}

			if (drawPinnedBottom && drawPinnedTop) {
				totalHeight -= AndroidUtilities.dp(2f)
			}
			else if (drawPinnedBottom) {
				totalHeight -= AndroidUtilities.dp(1f)
			}
			else if (drawPinnedTop && isPinnedBottom && currentPosition != null && currentPosition!!.siblingHeights == null) {
				totalHeight -= AndroidUtilities.dp(1f)
			}

			if (messageObject.type != MessageObject.TYPE_EMOJIS) {
				if (messageObject.isAnyKindOfSticker && totalHeight < AndroidUtilities.dp(70f)) {
					additionalTimeOffsetY = AndroidUtilities.dp(70f) - totalHeight
					totalHeight += additionalTimeOffsetY
				}
				else if (messageObject.isAnimatedEmoji) {
					additionalTimeOffsetY = AndroidUtilities.dp(16f)
					totalHeight += AndroidUtilities.dp(16f)
				}
			}

			if (!drawPhotoImage) {
				photoImage.setImageBitmap(null as Drawable?)
			}

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				if (MessageObject.isDocumentHasThumb(documentAttach)) {
					val thumb = FileLoader.getClosestPhotoSizeWithSize(documentAttach!!.thumbs, 90)
					radialProgress.setImageOverlay(thumb, documentAttach, messageObject)
				}
				else {
					val artworkUrl = messageObject.getArtworkUrl(true)

					if (!artworkUrl.isNullOrEmpty()) {
						radialProgress.setImageOverlay(artworkUrl)
					}
					else {
						radialProgress.setImageOverlay(null, null, null)
					}
				}
			}
			else {
				radialProgress.setImageOverlay(null, null, null)
			}

			calculateUnlockXY()

			if (canChangeRadius) {
				var tl: Int
				var tr: Int
				var bl: Int
				var br: Int
				var minRad = AndroidUtilities.dp(4f)

				val rad = if (SharedConfig.bubbleRadius > 2) {
					AndroidUtilities.dp((SharedConfig.bubbleRadius - 2).toFloat())
				}
				else {
					AndroidUtilities.dp(SharedConfig.bubbleRadius.toFloat())
				}

				val nearRad = min(AndroidUtilities.dp(3f), rad)

				br = rad
				bl = br
				tr = bl
				tl = tr

				if (minRad > tl) {
					minRad = tl
				}

				if (hasLinkPreview || hasGamePreview || hasInvoicePreview) {
					br = minRad
					bl = br
					tr = bl
					tl = tr
				}

				if (forwardedNameLayout[0] != null || replyNameLayout != null || drawNameLayout) {
					tr = minRad
					tl = tr
				}

				if (captionLayout != null || drawCommentButton) {
					br = minRad
					bl = br
				}

				if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
					br = minRad
					tr = br
				}

				if (currentPosition != null && currentMessagesGroup != null) {
					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT == 0) {
						br = minRad
						tr = br
					}

					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
						bl = minRad
						tl = bl
					}

					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0) {
						br = minRad
						bl = br
					}

					if (currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP == 0) {
						tr = minRad
						tl = tr
					}
				}

				if (isPinnedTop) {
					if (currentMessageObject!!.isOutOwner) {
						tr = nearRad
					}
					else {
						tl = nearRad
					}
				}

				if (isPinnedBottom) {
					if (currentMessageObject!!.isOutOwner) {
						br = nearRad
					}
					else {
						bl = nearRad
					}
				}

				if (!mediaBackground && !currentMessageObject!!.isOutOwner) {
					bl = nearRad
				}

				photoImage.setRoundRadius(tl, tr, br, bl)
			}

			updateAnimatedEmojis()
		}

		if (messageIdChanged) {
			currentUrl = null
			currentWebFile = null
			lastWebFile = null
			loadingProgressLayout = null
			animatingLoadingProgressProgress = 0f
			lastLoadingSizeTotal = 0
			selectedBackgroundProgress = 0f

			statusDrawableAnimator?.removeAllListeners()
			statusDrawableAnimator?.cancel()

			transitionParams.lastStatusDrawableParams = -1
			statusDrawableAnimationInProgress = false

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				val showSeekbar = MediaController.getInstance().isPlayingMessage(currentMessageObject)
				toSeekBarProgress = if (showSeekbar) 1f else 0f
			}

			seekBarWaveform.setProgress(0f)

			currentNameStatusDrawable?.play()
		}

		transcribeButton?.setOpen(currentMessageObject!!.messageOwner != null && currentMessageObject!!.messageOwner!!.voiceTranscriptionOpen && currentMessageObject!!.messageOwner!!.voiceTranscriptionFinal, !messageIdChanged)
		transcribeButton?.setLoading(TranscribeButton.isTranscribing(currentMessageObject), !messageIdChanged)

		updateWaveform()
		updateButtonState(false, !messageIdChanged && !messageObject.cancelEditing, true)

		if (!currentMessageObject!!.loadingCancelled && buttonState == 2 && documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO && DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
			FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL, 0)
			buttonState = 4
			radialProgress.setIcon(iconForCurrentState, false, false)
		}

		if (!messageIdChanged && messageChanged) {
			delegate?.textSelectionHelper?.checkDataChanged(messageObject)
		}

		accessibilityVirtualViewBounds.clear()
		transitionParams.updatePhotoImageX = true

		updateFlagSecure()

		if (noBackground(messageObject)) {
			// MARK: temporary solution to return background for single media so reactions are displayed correctly
			drawBackground = if (messageObject.isRoundVideo) {
				false
			}
			else {
				ChatObject.isChannelAndNotMegaGroup(currentChat)
			}
		}
	}

	private fun noBackground(messageObject: MessageObject): Boolean {
		if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
			return true
		}

		return (messageObject.type == MessageObject.TYPE_PHOTO || messageObject.type == MessageObject.TYPE_VIDEO) && messageObject.caption.isNullOrEmpty() && !hasDiscussion && !messageObject.isReply && !messageObject.isForwarded
	}

	private fun calculateUnlockXY() {
		if (currentMessageObject!!.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW && unlockLayout != null) {
			unlockX = backgroundDrawableLeft + (photoImage.imageWidth - unlockLayout!!.width) / 2f
			unlockY = backgroundDrawableTop + photoImage.imageY + (photoImage.imageHeight - unlockLayout!!.height) / 2f
		}
	}

	private fun updateFlagSecure() {
		val flagSecure = currentMessageObject != null && currentMessageObject!!.messageOwner != null && (currentMessageObject!!.messageOwner!!.noforwards || currentMessageObject!!.hasRevealedExtendedMedia())
		val activity = AndroidUtilities.findActivity(context)

		if (flagSecure && unregisterFlagSecure == null && activity != null) {
			unregisterFlagSecure = AndroidUtilities.registerFlagSecure(activity.window)
		}
		else if (!flagSecure && unregisterFlagSecure != null) {
			unregisterFlagSecure!!.run()
			unregisterFlagSecure = null
		}
	}

	fun checkVideoPlayback(allowStart: Boolean, thumb: Bitmap?) {
		val currentMessageObject = currentMessageObject ?: return

		@Suppress("NAME_SHADOWING") var allowStart = allowStart

		if (currentMessageObject.isVideo) {
			if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
				photoImage.allowStartAnimation = false
				photoImage.stopAnimation()
			}
			else {
				photoImage.allowStartAnimation = true
				photoImage.startAnimation()
			}
		}
		else {
			if (allowStart) {
				val playingMessage = MediaController.getInstance().playingMessageObject
				allowStart = playingMessage == null || !playingMessage.isRoundVideo
			}

			photoImage.allowStartAnimation = allowStart

			if (thumb != null) {
				photoImage.startCrossfadeFromStaticThumb(thumb)
			}

			if (allowStart) {
				photoImage.startAnimation()
			}
			else {
				photoImage.stopAnimation()
			}
		}
	}

	override fun onLongPress(): Boolean {
		if (isRoundVideo && isPlayingRound && MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
			val touchRadius = (lastTouchX - photoImage.centerX) * (lastTouchX - photoImage.centerX) + (lastTouchY - photoImage.centerY) * (lastTouchY - photoImage.centerY)
			val r1 = photoImage.imageWidth / 2f * (photoImage.imageWidth / 2f)

			if (touchRadius < r1 && (lastTouchX > photoImage.centerX + photoImage.imageWidth / 4f || lastTouchX < photoImage.centerX - photoImage.imageWidth / 4f)) {

				val forward = lastTouchX > photoImage.centerX
				if (videoPlayerRewinder == null) {
					videoForwardDrawable = VideoForwardDrawable(true)

					videoPlayerRewinder = object : VideoPlayerRewinder() {
						override fun onRewindCanceled() {
							onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))
							videoForwardDrawable?.setShowing(false)
						}

						override fun updateRewindProgressUi(timeDiff: Long, progress: Float, rewindByBackSeek: Boolean) {
							videoForwardDrawable?.setTime(abs(timeDiff))

							if (rewindByBackSeek) {
								currentMessageObject?.audioProgress = progress
								updatePlayingMessageProgress()
							}
						}

						override fun onRewindStart(rewindForward: Boolean) {
							videoForwardDrawable?.setDelegate(object : VideoForwardDrawableDelegate {
								override fun onAnimationEnd() {
									// unused
								}

								override fun invalidate() {
									this@ChatMessageCell.invalidate()
								}
							})

							videoForwardDrawable?.setOneShootAnimation(false)
							videoForwardDrawable?.setLeftSide(!rewindForward)
							videoForwardDrawable?.setShowing(true)

							invalidate()
						}
					}

					parent.requestDisallowInterceptTouchEvent(true)
				}

				videoPlayerRewinder?.startRewind(MediaController.getInstance().videoPlayer, forward, MediaController.getInstance().getPlaybackSpeed(false))

				return false
			}
		}

		if (pressedEmoji != null) {
//            hadLongPress = true;
//            if (delegate.didPressAnimatedEmoji(pressedEmoji)) {
//                pressedEmoji = null;
//                resetPressedLink(-1);
//                return true;
//            }
			pressedEmoji = null
		}

		pressedLink?.let { pressedLink ->
			if (pressedLink.span is URLSpanMono) {
				hadLongPress = true
				delegate?.didPressUrl(this, pressedLink.span, true)
				return true
			}
			else if (pressedLink.span is URLSpanNoUnderline) {
				val url = pressedLink.span as URLSpanNoUnderline

				if (ChatActivity.isClickableLink(url.url) || url.url.startsWith("/")) {
					hadLongPress = true
					delegate?.didPressUrl(this, pressedLink.span, true)
					return true
				}
			}
			else if (pressedLink.span is URLSpan) {
				hadLongPress = true
				delegate?.didPressUrl(this, pressedLink.span, true)
				return true
			}
		}

		resetPressedLink(-1)

		if (buttonPressed != 0 || miniButtonPressed != 0 || videoButtonPressed != 0 || pressedBotButton != -1) {
			buttonPressed = 0
			miniButtonPressed = 0
			videoButtonPressed = 0
			pressedBotButton = -1

			invalidate()
		}

		linkPreviewPressed = false
		sideButtonPressed = false
		imagePressed = false
		timePressed = false
		gamePreviewPressed = false

		if (pressedVoteButton != -1 || pollHintPressed || psaHintPressed || instantPressed || otherPressed || commentButtonPressed) {
			commentButtonPressed = false
			instantButtonPressed = false
			instantPressed = false
			pressedVoteButton = -1
			pollHintPressed = false
			psaHintPressed = false
			otherPressed = false

			for (drawable in selectorDrawable) {
				drawable?.state = StateSet.NOTHING
			}

			invalidate()
		}

		if (delegate != null) {
			var handled = false

			if (avatarPressed) {
				if (currentUser != null) {
					if (currentUser?.id != 0L) {
						handled = delegate?.didLongPressUserAvatar(this, currentUser, lastTouchX, lastTouchY) ?: false
					}
				}
				else if (currentChat != null) {
					val id = if (currentMessageObject?.messageOwner?.fwdFrom != null) {
						if (currentMessageObject!!.messageOwner!!.fwdFrom!!.flags and 16 != 0) {
							currentMessageObject!!.messageOwner!!.fwdFrom!!.savedFromMsgId
						}
						else {
							currentMessageObject!!.messageOwner!!.fwdFrom?.channelPost ?: 0
						}
					}
					else {
						0
					}

					handled = delegate?.didLongPressChannelAvatar(this, currentChat, id, lastTouchX, lastTouchY) ?: false
				}
			}

			if (!handled) {
				delegate?.didLongPress(this, lastTouchX, lastTouchY)
			}
		}

		return true
	}

	fun showHintButton(show: Boolean, animated: Boolean, type: Int) {
		if (type == -1 || type == 0) {
			if (hintButtonVisible == show) {
				return
			}

			hintButtonVisible = show

			if (!animated) {
				hintButtonProgress = if (show) 1.0f else 0.0f
			}
			else {
				invalidate()
			}
		}

		if (type == -1 || type == 1) {
			if (psaButtonVisible == show) {
				return
			}

			psaButtonVisible = show

			if (!animated) {
				psaButtonProgress = if (show) 1.0f else 0.0f
			}
			else {
				setInvalidatesParent(true)
				invalidate()
			}
		}
	}

	fun setCheckPressed(value: Boolean, pressed: Boolean) {
		isCheckPressed = value
		isPressed = pressed

		updateRadialProgressBackground()

		if (useSeekBarWaveform) {
			seekBarWaveform.setSelected(isDrawSelectionBackground())
		}
		else {
			seekBar.setSelected(isDrawSelectionBackground())
		}

		invalidate()
	}

	fun setInvalidateSpoilersParent(invalidateSpoilersParent: Boolean) {
		this.invalidateSpoilersParent = invalidateSpoilersParent
	}

	fun setInvalidatesParent(value: Boolean) {
		invalidatesParent = value
	}

	override fun invalidate() {
		if (currentMessageObject == null) {
			return
		}

		super.invalidate()

		if ((invalidatesParent || currentMessagesGroup != null && (!links.isEmpty || !reactionsLayoutInBubble.isEmpty)) && parent != null) {
			var parent = parent as? View

			if (parent?.parent != null) {
				parent.invalidate()
				parent = parent.parent as View
				parent.invalidate()
			}
		}

		if (isBlurred && delegate != null) {
			delegate?.invalidateBlur()
		}
	}

	@Deprecated("Deprecated in Java")
	override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
		if (currentMessageObject == null) {
			return
		}

		// super.invalidate(l, t, r, b)
		super.invalidate()

		if (invalidatesParent) {
			if (parent != null) {
				val parent = parent as View
				// parent.invalidate(x.toInt() + l, y.toInt() + t, x.toInt() + r, y.toInt() + b)
				parent.invalidate()
			}
		}

		if (isBlurred && delegate != null) {
			delegate?.invalidateBlur()
		}
	}

	fun setHighlightedAnimated() {
		isHighlightedAnimated = true
		highlightProgress = 1000
		lastHighlightProgressTime = System.currentTimeMillis()

		invalidate()

		(parent as? View)?.invalidate()
	}

	fun isHighlighted(): Boolean {
		return isHighlighted
	}

	fun setHighlighted(value: Boolean) {
		if (isHighlighted == value) {
			return
		}

		isHighlighted = value

		if (!isHighlighted) {
			lastHighlightProgressTime = System.currentTimeMillis()
			isHighlightedAnimated = true
			highlightProgress = 300
		}
		else {
			isHighlightedAnimated = false
			highlightProgress = 0
		}

		updateRadialProgressBackground()

		if (useSeekBarWaveform) {
			seekBarWaveform.setSelected(isDrawSelectionBackground())
		}
		else {
			seekBar.setSelected(isDrawSelectionBackground())
		}

		invalidate()

		(parent as? View)?.invalidate()
	}

	override fun setPressed(pressed: Boolean) {
		super.setPressed(pressed)

		updateRadialProgressBackground()

		if (useSeekBarWaveform) {
			seekBarWaveform.setSelected(isDrawSelectionBackground())
		}
		else {
			seekBar.setSelected(isDrawSelectionBackground())
		}

		invalidate()
	}

	private fun updateRadialProgressBackground() {
		if (drawRadialCheckBackground) {
			return
		}

		val forcePressed = (isHighlighted || isPressed || isPressed()) && (!drawPhotoImage || !photoImage.hasBitmapImage())

		radialProgress.setPressed(forcePressed || buttonPressed != 0, false)

		if (hasMiniProgress != 0) {
			radialProgress.setPressed(forcePressed || miniButtonPressed != 0, true)
		}

		videoRadialProgress.setPressed(forcePressed || videoButtonPressed != 0, false)
	}

	override fun onSeekBarDrag(progress: Float) {
		val currentMessageObject = currentMessageObject ?: return
		currentMessageObject.audioProgress = progress
		MediaController.getInstance().seekToProgress(currentMessageObject, progress)
		updatePlayingMessageProgress()
	}

	override fun onSeekBarContinuousDrag(progress: Float) {
		val currentMessageObject = currentMessageObject ?: return
		currentMessageObject.audioProgress = progress
		currentMessageObject.audioProgressSec = (currentMessageObject.duration * progress).toInt()
		updatePlayingMessageProgress()
	}

	private fun updateWaveform() {
		val currentMessageObject = currentMessageObject ?: return

		if (documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO) {
			return
		}

		val documentAttach = documentAttach

		if (documentAttach is TLRPC.TLDocument) {
			for (a in documentAttach.attributes.indices) {
				val attribute = documentAttach.attributes[a]

				if (attribute is TLRPC.TLDocumentAttributeAudio) {
					if (attribute.waveform == null || attribute.waveform?.isEmpty() == true) {
						MediaController.getInstance().generateWaveform(currentMessageObject)
					}

					useSeekBarWaveform = attribute.waveform != null
					seekBarWaveform.setWaveform(attribute.waveform)

					break
				}
			}
		}

		useTranscribeButton = currentMessageObject.isVoice && useSeekBarWaveform && currentMessageObject.messageOwner != null && MessageObject.getMedia(currentMessageObject.messageOwner) !is TLRPC.TLMessageMediaWebPage && getInstance(currentAccount).isPremium

		updateSeekBarWaveformWidth()
	}

	private fun updateSeekBarWaveformWidth() {
		val offset = -AndroidUtilities.dp((92 + if (hasLinkPreview) 10 else 0).toFloat()) - AndroidUtilities.dp(if (useTranscribeButton) 34f else 0f)

		if (transitionParams.animateBackgroundBoundsInner && documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
			val fromBackgroundWidth = backgroundWidth
			val toBackgroundWidth = (backgroundWidth - transitionParams.toDeltaLeft + transitionParams.toDeltaRight).toInt()
			val backgroundWidth = (backgroundWidth - transitionParams.deltaLeft + transitionParams.deltaRight).toInt()

			seekBarWaveform.setSize(backgroundWidth + offset, AndroidUtilities.dp(30f), fromBackgroundWidth + offset, toBackgroundWidth + offset)
		}
		else {
			seekBarWaveform.setSize(backgroundWidth + offset, AndroidUtilities.dp(30f))
		}
	}

	private fun createDocumentLayout(maxWidth: Int, messageObject: MessageObject): Int {
		@Suppress("NAME_SHADOWING") var maxWidth = maxWidth

		documentAttach = if (messageObject.type == MessageObject.TYPE_COMMON) {
			MessageObject.getMedia(messageObject.messageOwner)?.webpage?.document
		}
		else {
			messageObject.document
		}

		if (documentAttach == null) {
			return 0
		}

		return if (MessageObject.isVoiceDocument(documentAttach)) {
			documentAttachType = DOCUMENT_ATTACH_TYPE_AUDIO

			val duration = (documentAttach as? TLRPC.TLDocument)?.attributes?.firstOrNull { it is TLRPC.TLDocumentAttributeAudio }?.duration ?: 0

			widthBeforeNewTimeLine = maxWidth - AndroidUtilities.dp((76 + 18).toFloat()) - ceil(Theme.chat_audioTimePaint.measureText("00:00").toDouble()).toInt()
			availableTimeWidth = maxWidth - AndroidUtilities.dp(18f)

			measureTime(messageObject)

			val minSize = AndroidUtilities.dp((40 + 14 + 20 + 90 + 10).toFloat()) + timeWidth

			if (!hasLinkPreview) {
				val timeString = AndroidUtilities.formatLongDuration(duration)
				val w = ceil(Theme.chat_audioTimePaint.measureText(timeString).toDouble()).toInt()
				backgroundWidth = min(maxWidth, minSize + w)
			}

			seekBarWaveform.setMessageObject(messageObject)

			0
		}
		else if (MessageObject.isVideoDocument(documentAttach)) {
			documentAttachType = DOCUMENT_ATTACH_TYPE_VIDEO

			if (!messageObject.needDrawBluredPreview()) {
				updatePlayingMessageProgress()
				val str = String.format("%s", AndroidUtilities.formatFileSize(documentAttach!!.size))
				docTitleWidth = ceil(Theme.chat_infoPaint.measureText(str).toDouble()).toInt()
				docTitleLayout = StaticLayout(str, Theme.chat_infoPaint, docTitleWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}

			0
		}
		else if (MessageObject.isMusicDocument(documentAttach)) {
			documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC

			maxWidth -= AndroidUtilities.dp(92f)

			if (maxWidth < 0) {
				maxWidth = AndroidUtilities.dp(100f)
			}

			var stringFinal = TextUtils.ellipsize(messageObject.musicTitle?.replace('\n', ' '), Theme.chat_audioTitlePaint, (maxWidth - AndroidUtilities.dp(12f)).toFloat(), TextUtils.TruncateAt.END)

			songLayout = StaticLayout(stringFinal, Theme.chat_audioTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

			if (songLayout!!.lineCount > 0) {
				songX = -ceil(songLayout!!.getLineLeft(0).toDouble()).toInt()
			}

			stringFinal = TextUtils.ellipsize(messageObject.musicAuthor?.replace('\n', ' '), Theme.chat_audioPerformerPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)

			performerLayout = StaticLayout(stringFinal, Theme.chat_audioPerformerPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

			if (performerLayout!!.lineCount > 0) {
				performerX = -ceil(performerLayout!!.getLineLeft(0).toDouble()).toInt()
			}

			val duration = (documentAttach as? TLRPC.TLDocument)?.attributes?.firstOrNull { it is TLRPC.TLDocumentAttributeAudio }?.duration ?: 0
			val durationWidth = ceil(Theme.chat_audioTimePaint.measureText(AndroidUtilities.formatShortDuration(duration, duration)).toDouble()).toInt()

			widthBeforeNewTimeLine = backgroundWidth - AndroidUtilities.dp((10 + 76).toFloat()) - durationWidth
			availableTimeWidth = backgroundWidth - AndroidUtilities.dp(28f)

			durationWidth
		}
		else if (MessageObject.isGifDocument(documentAttach, messageObject.hasValidGroupId())) {
			documentAttachType = DOCUMENT_ATTACH_TYPE_GIF

			if (!messageObject.needDrawBluredPreview()) {
				var str = context.getString(R.string.AttachGif)

				infoWidth = ceil(Theme.chat_infoPaint.measureText(str).toDouble()).toInt()
				infoLayout = StaticLayout(str, Theme.chat_infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				str = String.format("%s", AndroidUtilities.formatFileSize(documentAttach!!.size))

				docTitleWidth = ceil(Theme.chat_infoPaint.measureText(str).toDouble()).toInt()
				docTitleLayout = StaticLayout(str, Theme.chat_infoPaint, docTitleWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}

			0
		}
		else {
			drawPhotoImage = documentAttach?.mimeType != null && (documentAttach?.mimeType?.lowercase()?.startsWith("image/") == true || documentAttach?.mimeType?.lowercase()?.startsWith("video/mp4") == true) || MessageObject.isDocumentHasThumb(documentAttach)

			if (!drawPhotoImage) {
				maxWidth += AndroidUtilities.dp(30f)
			}

			documentAttachType = DOCUMENT_ATTACH_TYPE_DOCUMENT

			var name = FileLoader.getDocumentFileName(documentAttach)

			if (name.isNullOrEmpty()) {
				name = context.getString(R.string.AttachDocument)
			}

			docTitleLayout = StaticLayoutEx.createStaticLayout(name, Theme.chat_docNamePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.MIDDLE, maxWidth, 2, false)
			docTitleOffsetX = Int.MIN_VALUE

			val width: Int

			if (docTitleLayout != null && docTitleLayout!!.lineCount > 0) {
				var maxLineWidth = 0

				for (a in 0 until docTitleLayout!!.lineCount) {
					maxLineWidth = max(maxLineWidth, ceil(docTitleLayout!!.getLineWidth(a).toDouble()).toInt())
					docTitleOffsetX = max(docTitleOffsetX, ceil(-docTitleLayout!!.getLineLeft(a).toDouble()).toInt())
				}

				width = min(maxWidth, maxLineWidth)
			}
			else {
				width = maxWidth
				docTitleOffsetX = 0
			}

			val str = AndroidUtilities.formatFileSize(documentAttach!!.size) + " " + FileLoader.getDocumentExtension(documentAttach!!)

			infoWidth = min(maxWidth - AndroidUtilities.dp(30f), ceil(Theme.chat_infoPaint.measureText("000.0 mm / " + AndroidUtilities.formatFileSize(documentAttach!!.size)).toDouble()).toInt())

			val str2 = TextUtils.ellipsize(str, Theme.chat_infoPaint, infoWidth.toFloat(), TextUtils.TruncateAt.END)

			try {
				if (infoWidth < 0) {
					infoWidth = AndroidUtilities.dp(10f)
				}

				infoLayout = StaticLayout(str2, Theme.chat_infoPaint, infoWidth + AndroidUtilities.dp(6f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (drawPhotoImage) {
				currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320)
				currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 40)

				if (DownloadController.getInstance(currentAccount).autodownloadMask and DownloadController.AUTODOWNLOAD_TYPE_PHOTO == 0) {
					currentPhotoObject = null
				}

				if (currentPhotoObject == null || currentPhotoObject === currentPhotoObjectThumb) {
					currentPhotoObject = null
					photoImage.isNeedsQualityThumb = true
					photoImage.isShouldGenerateQualityThumb = true
				}
				else if (currentMessageObject!!.strippedThumb != null) {
					currentPhotoObjectThumb = null
					currentPhotoObjectThumbStripped = currentMessageObject!!.strippedThumb
				}

				currentPhotoFilter = "86_86_b"

				photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "86_86", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), currentPhotoFilter, currentPhotoObjectThumbStripped, 0, null, messageObject, 1)
			}

			width
		}
	}

	private fun calcBackgroundWidth(maxWidth: Int, timeMore: Int, maxChildWidth: Int) {
		val newLineForTime: Boolean
		val lastLineWidth = if (reactionsLayoutInBubble.isEmpty || reactionsLayoutInBubble.isSmall) currentMessageObject!!.lastLineWidth else reactionsLayoutInBubble.lastLineX

		if (!reactionsLayoutInBubble.isEmpty && !reactionsLayoutInBubble.isSmall) {
			newLineForTime = maxWidth - lastLineWidth < timeMore || currentMessageObject!!.hasRtl

			if (hasInvoicePreview) {
				totalHeight += AndroidUtilities.dp(14f)
			}
		}
		else {
			newLineForTime = hasLinkPreview || hasOldCaptionPreview || hasGamePreview || hasInvoicePreview || maxWidth - lastLineWidth < timeMore || currentMessageObject!!.hasRtl
		}

		if (newLineForTime) {
			totalHeight += AndroidUtilities.dp(14f)
			hasNewLineForTime = true
			backgroundWidth = max(maxChildWidth, lastLineWidth) + AndroidUtilities.dp(31f)
			backgroundWidth = max(backgroundWidth, (if (currentMessageObject!!.isOutOwner) timeWidth + AndroidUtilities.dp(17f) else timeWidth) + AndroidUtilities.dp(31f))
		}
		else {
			val diff = maxChildWidth - extraTextX - lastLineWidth

			backgroundWidth = if (diff in 0..timeMore) {
				maxChildWidth + timeMore - diff + AndroidUtilities.dp(31f)
			}
			else {
				max(maxChildWidth, lastLineWidth + timeMore) + AndroidUtilities.dp(31f)
			}
		}
	}

	fun setHighlightedText(text: String?) {
		@Suppress("NAME_SHADOWING") var text = text

		val messageObject = if (messageObjectToSet != null) messageObjectToSet else currentMessageObject

		if (messageObject == null || messageObject.messageOwner?.message == null || text.isNullOrEmpty()) {
			if (urlPathSelection.isNotEmpty()) {
				linkSelectionBlockNum = -1
				resetUrlPaths()
				invalidate()
			}

			return
		}

		text = text.lowercase()

		val message = messageObject.messageOwner?.message?.lowercase() ?: ""
		var start = -1
		var length = -1
		val punctuationsChars = " !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~\n"

		run {
			var a = 0
			val n1 = message.length

			while (a < n1) {
				var currentLen = 0
				var b = 0
				val n2 = min(text.length, n1 - a)

				while (b < n2) {
					var match = message[a + b] == text[b]

					if (match) {
						if (currentLen != 0 || a == 0 || punctuationsChars.indexOf(message[a - 1]) >= 0) {
							currentLen++
						}
						else {
							match = false
						}
					}

					if (!match || b == n2 - 1) {
						if (currentLen > 0 && currentLen > length) {
							length = currentLen
							start = a
						}

						break
					}

					b++
				}

				a++
			}
		}

		if (start == -1) {
			if (urlPathSelection.isNotEmpty()) {
				linkSelectionBlockNum = -1
				resetUrlPaths()
				invalidate()
			}

			return
		}

		var a = start + length
		val n = message.length

		while (a < n) {
			if (punctuationsChars.indexOf(message[a]) < 0) {
				length++
			}
			else {
				break
			}

			a++
		}

		val end = start + length

		if (captionLayout != null && !TextUtils.isEmpty(messageObject.caption)) {
			resetUrlPaths()

			try {
				val path = obtainNewUrlPath()
				path.setCurrentLayout(captionLayout, start, 0f)
				captionLayout?.getSelectionPath(start, end, path)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			invalidate()
		}
		else if (messageObject.textLayoutBlocks != null) {
			for (c in messageObject.textLayoutBlocks!!.indices) {
				val block = messageObject.textLayoutBlocks!![c]

				if (start >= block.charactersOffset && start < block.charactersEnd) {
					linkSelectionBlockNum = c

					resetUrlPaths()

					try {
						var path = obtainNewUrlPath()
						path.setCurrentLayout(block.textLayout, start, 0f)

						block.textLayout?.getSelectionPath(start, end, path)

						if (end >= block.charactersOffset + length) {
							for (@Suppress("NAME_SHADOWING") a in c + 1 until messageObject.textLayoutBlocks!!.size) {
								val nextBlock = messageObject.textLayoutBlocks!![a]

								length = nextBlock.charactersEnd - nextBlock.charactersOffset

								path = obtainNewUrlPath()
								path.setCurrentLayout(nextBlock.textLayout, 0, nextBlock.height.toFloat())

								nextBlock.textLayout?.getSelectionPath(0, end - nextBlock.charactersOffset, path)

								if (end < block.charactersOffset + length - 1) {
									break
								}
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
					invalidate()
					break
				}
			}
		}
	}

	override fun verifyDrawable(who: Drawable): Boolean {
		return super.verifyDrawable(who) || who === selectorDrawable[0] || who === selectorDrawable[1]
	}

	override fun invalidateDrawable(drawable: Drawable) {
		super.invalidateDrawable(drawable)

		if (currentMessagesGroup != null) {
			invalidateWithParent()
		}
	}

	private fun isCurrentLocationTimeExpired(messageObject: MessageObject?): Boolean {
		val currentMedia = MessageObject.getMedia(currentMessageObject?.messageOwner) ?: return false
		val media = MessageObject.getMedia(messageObject?.messageOwner) ?: return false

		return if (currentMedia.period % 60 == 0) {
			abs(ConnectionsManager.getInstance(currentAccount).currentTime - messageObject!!.messageOwner!!.date) > media.period
		}
		else {
			abs(ConnectionsManager.getInstance(currentAccount).currentTime - messageObject!!.messageOwner!!.date) > media.period - 5
		}
	}

	private fun checkLocationExpired() {
		if (currentMessageObject == null) {
			return
		}

		val newExpired = isCurrentLocationTimeExpired(currentMessageObject)

		if (newExpired != locationExpired) {
			locationExpired = newExpired

			if (!locationExpired) {
				AndroidUtilities.runOnUIThread(invalidateRunnable, 1000)
				scheduledInvalidate = true
				val maxWidth = backgroundWidth - AndroidUtilities.dp((37 + 54).toFloat())
				docTitleLayout = StaticLayout(TextUtils.ellipsize(context.getString(R.string.AttachLiveLocation), Theme.chat_locationTitlePaint, maxWidth.toFloat(), TextUtils.TruncateAt.END), Theme.chat_locationTitlePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
			else {
				val messageObject = currentMessageObject
				currentMessageObject = null
				setMessageObject(messageObject, currentMessagesGroup, isPinnedBottom, isPinnedTop)
			}
		}
	}

	fun setIsUpdating(value: Boolean) {
		isUpdating = value
	}

	fun setMessageObject(messageObject: MessageObject?, groupedMessages: GroupedMessages?, bottomNear: Boolean, topNear: Boolean) {
		if (attachedToWindow) {
			setMessageContent(messageObject, groupedMessages, bottomNear, topNear)
		}
		else {
			messageObjectToSet = messageObject
			groupedMessagesToSet = groupedMessages
			bottomNearToSet = bottomNear
			topNearToSet = topNear
		}
	}

	private fun getAdditionalWidthForPosition(position: GroupedMessagePosition?): Int {
		var w = 0

		if (position != null) {
			if (position.flags and MessageObject.POSITION_FLAG_RIGHT == 0) {
				w += AndroidUtilities.dp(4f)
			}

			if (position.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
				w += AndroidUtilities.dp(4f)
			}
		}

		return w
	}

	fun createSelectorDrawable(num: Int) {
		val color = if (psaHintPressed) {
			if (currentMessageObject?.isOutOwner == true) {
				ResourcesCompat.getColor(context.resources, R.color.white, null)
			}
			else {
				ResourcesCompat.getColor(context.resources, R.color.brand, null)
			}
		}
		else {
			if (currentMessageObject?.isOutOwner == true) {
				ResourcesCompat.getColor(context.resources, R.color.white, null)
			}
			else {
				ResourcesCompat.getColor(context.resources, R.color.brand, null)
			}
		}

		if (selectorDrawable[num] == null) {
			val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			maskPaint.color = -0x1

			val maskDrawable: Drawable = object : Drawable() {
				val rect = RectF()
				val path = Path()

				override fun draw(canvas: Canvas) {
					val bounds = bounds

					rect.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())

					if (selectorDrawableMaskType[num] == 3 || selectorDrawableMaskType[num] == 4) {
						canvas.drawCircle(rect.centerX(), rect.centerY(), AndroidUtilities.dp(if (selectorDrawableMaskType[num] == 3) 16f else 20f).toFloat(), maskPaint)
					}
					else if (selectorDrawableMaskType[num] == 2) {
						path.reset()

						val out = currentMessageObject != null && currentMessageObject!!.isOutOwner

						for (a in 0..3) {
							if (!instantTextNewLine) {
								if (a == 2 && !out) {
									radii[a * 2 + 1] = AndroidUtilities.dp(SharedConfig.bubbleRadius.toFloat()).toFloat()
									radii[a * 2] = radii[a * 2 + 1]
									continue
								}
								else if (a == 3 && out) {
									radii[a * 2 + 1] = AndroidUtilities.dp(SharedConfig.bubbleRadius.toFloat()).toFloat()
									radii[a * 2] = radii[a * 2 + 1]
									continue
								}

								if ((mediaBackground || isPinnedBottom) && (a == 2 || a == 3)) {
									radii[a * 2 + 1] = AndroidUtilities.dp(if (isPinnedBottom) min(5, SharedConfig.bubbleRadius).toFloat() else SharedConfig.bubbleRadius.toFloat()).toFloat()
									radii[a * 2] = radii[a * 2 + 1]
									continue
								}
							}

							radii[a * 2 + 1] = 0f
							radii[a * 2] = radii[a * 2 + 1]
						}

						path.addRoundRect(rect, radii, Path.Direction.CW)
						path.close()

						canvas.drawPath(path, maskPaint)
					}
					else {
						canvas.drawRoundRect(rect, if (selectorDrawableMaskType[num] == 0) AndroidUtilities.dp(6f).toFloat() else 0f, if (selectorDrawableMaskType[num] == 0) AndroidUtilities.dp(6f).toFloat() else 0f, maskPaint)
					}
				}

				override fun setAlpha(alpha: Int) {
					// unused
				}

				override fun setColorFilter(colorFilter: ColorFilter?) {
					// unused
				}

				@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
				override fun getOpacity(): Int {
					return PixelFormat.TRANSPARENT
				}
			}

			val colorStateList = ColorStateList(arrayOf(StateSet.WILD_CARD), intArrayOf((if (currentMessageObject!!.isOutOwner) ResourcesCompat.getColor(context.resources, R.color.white, null) else ResourcesCompat.getColor(context.resources, R.color.brand, null)) and 0x19ffffff))

			selectorDrawable[num] = RippleDrawable(colorStateList, null, maskDrawable)
			selectorDrawable[num]?.callback = this
		}
		else {
			Theme.setSelectorDrawableColor(selectorDrawable[num], color and 0x19ffffff, true)
		}

		selectorDrawable[num]?.setVisible(true, false)
	}

	private fun createInstantViewButton() {
		if (drawInstantView) {
			createSelectorDrawable(0)
		}

		if (drawInstantView && instantViewLayout == null) {
			instantWidth = AndroidUtilities.dp((12 + 9 + 12).toFloat())

			val str = if (drawInstantViewType == 12) {
				context.getString(R.string.OpenChannelPost)
			}
			else if (drawInstantViewType == 1) {
				context.getString(R.string.OpenChannel)
			}
			else if (drawInstantViewType == 13) {
				context.getString(R.string.SendMessage).uppercase()
			}
			else if (drawInstantViewType == 10) {
				context.getString(R.string.OpenBot)
			}
			else if (drawInstantViewType == 2) {
				context.getString(R.string.OpenGroup)
			}
			else if (drawInstantViewType == 3) {
				context.getString(R.string.OpenMessage)
			}
			else if (drawInstantViewType == 5) {
				context.getString(R.string.ViewContact)
			}
			else if (drawInstantViewType == 6) {
				context.getString(R.string.OpenBackground)
			}
			else if (drawInstantViewType == 7) {
				context.getString(R.string.OpenTheme)
			}
			else if (drawInstantViewType == 8) {
				if (pollVoted || pollClosed) {
					context.getString(R.string.PollViewResults)
				}
				else {
					context.getString(R.string.PollSubmitVotes)
				}
			}
			else if (drawInstantViewType == 9 || drawInstantViewType == 11) {
				val webPage = MessageObject.getMedia(currentMessageObject!!.messageOwner)?.webpage as? TLRPC.TLWebPage

				if (webPage?.url?.contains("voicechat=") == true) {
					context.getString(R.string.VoipGroupJoinAsSpeaker)
				}
				else {
					context.getString(R.string.VoipGroupJoinAsLinstener)
				}
			}
			else {
				context.getString(R.string.InstantView)
			}

			if (currentMessageObject?.isSponsored == true) {
				val buttonWidth = (Theme.chat_instantViewPaint.measureText(str) + AndroidUtilities.dp((10 + 24 + 10 + 31).toFloat())).toInt()

				if (backgroundWidth < buttonWidth) {
					backgroundWidth = buttonWidth
				}
			}

			val mWidth = backgroundWidth - AndroidUtilities.dp((10 + 24 + 10 + 31).toFloat())

			instantViewLayout = StaticLayout(TextUtils.ellipsize(str, Theme.chat_instantViewPaint, mWidth.toFloat(), TextUtils.TruncateAt.END), Theme.chat_instantViewPaint, mWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

			instantWidth = if (drawInstantViewType == 8) {
				backgroundWidth - AndroidUtilities.dp(13f)
			}
			else {
				backgroundWidth - AndroidUtilities.dp(34f)
			}

			totalHeight += AndroidUtilities.dp(46f)

			if (currentMessageObject!!.type == MessageObject.TYPE_CONTACT) {
				totalHeight += AndroidUtilities.dp(14f)
			}

			if (currentMessageObject!!.isSponsored && hasNewLineForTime) {
				totalHeight += AndroidUtilities.dp(16f)
			}

			if (instantViewLayout != null && instantViewLayout!!.lineCount > 0) {
				instantTextX = (instantWidth - ceil(instantViewLayout!!.getLineWidth(0).toDouble())).toInt() / 2 + if (drawInstantViewType == 0) AndroidUtilities.dp(8f) else 0
				instantTextLeftX = instantViewLayout!!.getLineLeft(0).toInt()
				instantTextX -= instantTextLeftX
			}
		}
	}

	override fun requestLayout() {
		if (inLayout) {
			return
		}

		super.requestLayout()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (currentMessageObject != null && (currentMessageObject!!.checkLayout() || lastHeight != AndroidUtilities.displaySize.y)) {
			inLayout = true

			val messageObject = currentMessageObject

			currentMessageObject = null

			setMessageObject(messageObject, currentMessagesGroup, isPinnedBottom, isPinnedTop)

			inLayout = false
		}

		updateSelectionTextPosition()

		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight + keyboardHeight + getAdditionalHeight())
	}

	fun forceResetMessageObject() {
		val messageObject = messageObjectToSet ?: currentMessageObject
		val messagesGroup = currentMessagesGroup

		currentMessageObject = null
		currentMessagesGroup = null

		setMessageObject(messageObject, messagesGroup, isPinnedBottom, isPinnedTop)
	}

	private val groupPhotosWidth: Int
		get() {
			var width = getParentWidth()

			if (currentMessageObject != null && currentMessageObject!!.preview) {
				width = parentWidth
			}

			return if (!AndroidUtilities.isInMultiwindow && AndroidUtilities.isTablet() && (!AndroidUtilities.isSmallTablet() || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)) {
				var leftWidth = width / 100 * 35

				if (leftWidth < AndroidUtilities.dp(320f)) {
					leftWidth = AndroidUtilities.dp(320f)
				}

				width - leftWidth
			}
			else {
				width
			}
		}

	private val extraTextX: Int
		get() {
			if (SharedConfig.bubbleRadius >= 15) {
				return AndroidUtilities.dp(2f)
			}
			else if (SharedConfig.bubbleRadius >= 11) {
				return AndroidUtilities.dp(1f)
			}

			return 0
		}

	private val extraTimeX: Int
		get() {
			if (!currentMessageObject!!.isOutOwner && (!mediaBackground || captionLayout != null) && SharedConfig.bubbleRadius > 11) {
				return AndroidUtilities.dp((SharedConfig.bubbleRadius - 11) / 1.5f)
			}

			if (!currentMessageObject!!.isOutOwner && isPlayingRound && isAvatarVisible && currentMessageObject!!.type == MessageObject.TYPE_ROUND_VIDEO) {
				return ((AndroidUtilities.roundPlayingMessageSize - AndroidUtilities.roundMessageSize) * 0.7f).toInt()
			}

			return 0
		}

	@SuppressLint("DrawAllocation")
	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		if (currentMessageObject == null) {
			return
		}

		val currentSize = measuredHeight - getAdditionalHeight() + (measuredWidth shl 16)

		if (lastSize != currentSize || !wasLayout) {
			layoutWidth = measuredWidth
			layoutHeight = measuredHeight - getAdditionalHeight() - substractBackgroundHeight

			if (timeTextWidth < 0) {
				timeTextWidth = AndroidUtilities.dp(10f)
			}

			timeLayout = StaticLayout(currentTimeString, Theme.chat_timePaint, timeTextWidth + AndroidUtilities.dp(100f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

			if (mediaBackground) {
				if (currentMessageObject!!.isOutOwner) {
					timeX = layoutWidth - timeWidth - AndroidUtilities.dp(18f)
				}
				else {
					timeX = backgroundWidth - AndroidUtilities.dp(4f) - timeWidth

					if (currentMessageObject!!.isAnyKindOfSticker) {
						timeX = max(AndroidUtilities.dp(26f), timeX)
					}

					if (isAvatarVisible) {
						timeX += AndroidUtilities.dp(48f)
					}

					if (currentPosition != null && currentPosition!!.leftSpanOffset != 0) {
						timeX += ceil((currentPosition!!.leftSpanOffset / 1000.0f * groupPhotosWidth).toDouble()).toInt()
					}

					if (captionLayout != null && currentPosition != null) {
						timeX += AndroidUtilities.dp(4f)
					}
				}

				if (SharedConfig.bubbleRadius >= 10 && captionLayout == null && documentAttachType != DOCUMENT_ATTACH_TYPE_ROUND && documentAttachType != DOCUMENT_ATTACH_TYPE_STICKER) {
					timeX -= AndroidUtilities.dp(2f)
				}
			}
			else {
				if (currentMessageObject!!.isOutOwner) {
					timeX = layoutWidth - timeWidth - AndroidUtilities.dp(18f)
				}
				else {
					timeX = backgroundWidth - AndroidUtilities.dp(9f) - timeWidth

					if (currentMessageObject!!.isAnyKindOfSticker) {
						timeX = max(0, timeX)
					}

					if (isAvatarVisible) {
						timeX += AndroidUtilities.dp(48f)
					}

					if (shouldDrawTimeOnMedia()) {
						timeX -= AndroidUtilities.dp(7f)
					}
				}
			}

			timeX -= extraTimeX

			if (currentMessageObject?.isOutOwner == true) {
				timeX -= AndroidUtilities.dp(20f)
			}

			viewsLayout = if (currentMessageObject!!.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_HAS_VIEWS != 0) {
				StaticLayout(currentViewsString, Theme.chat_timePaint, viewsTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
			else {
				null
			}

			repliesLayout = if (currentRepliesString != null && !currentMessageObject!!.scheduled) {
				StaticLayout(currentRepliesString, Theme.chat_timePaint, repliesTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
			else {
				null
			}

			if (isAvatarVisible) {
				avatarImage.setImageCoordinates(AndroidUtilities.dp(6f).toFloat(), avatarImage.imageY, AndroidUtilities.dp(42f).toFloat(), AndroidUtilities.dp(42f).toFloat())
			}

			if (currentMessageObject!!.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW && currentUnlockString != null) {
				unlockLayout = StaticLayout(currentUnlockString, Theme.chat_unlockExtendedMediaTextPaint, unlockTextWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)

				val preview = currentMessageObject?.messageOwner?.media?.extendedMedia as? TLRPC.TLMessageExtendedMediaPreview

				if (preview != null && preview.videoDuration != 0) {
					val str = AndroidUtilities.formatDuration(preview.videoDuration, false)
					durationWidth = ceil(Theme.chat_durationPaint.measureText(str).toDouble()).toInt()
					videoInfoLayout = StaticLayout(str, Theme.chat_durationPaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
				}
				else {
					videoInfoLayout = null
				}
			}
			else {
				unlockLayout = null
			}

			wasLayout = true
		}

		lastSize = currentSize

		if (currentMessageObject!!.type == MessageObject.TYPE_COMMON) {
			textY = AndroidUtilities.dp(10f) + namesOffset
		}

		if (isRoundVideo) {
			updatePlayingMessageProgress()
		}

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
			if (currentMessageObject!!.isOutOwner) {
				seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(57f)
				buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14f)
				timeAudioX = layoutWidth - backgroundWidth + AndroidUtilities.dp(67f)
			}
			else {
				if (isChat && !isThreadPost && currentMessageObject!!.needDrawAvatar()) {
					seekBarX = AndroidUtilities.dp(114f)
					buttonX = AndroidUtilities.dp(71f)
					timeAudioX = AndroidUtilities.dp(124f)
				}
				else {
					seekBarX = AndroidUtilities.dp(66f)
					buttonX = AndroidUtilities.dp(23f)
					timeAudioX = AndroidUtilities.dp(76f)
				}
			}

			if (hasLinkPreview) {
				seekBarX += AndroidUtilities.dp(10f)
				buttonX += AndroidUtilities.dp(10f)
				timeAudioX += AndroidUtilities.dp(10f)
			}

			updateSeekBarWaveformWidth()

			seekBar.setSize(backgroundWidth - AndroidUtilities.dp((72 + if (hasLinkPreview) 10 else 0).toFloat()), AndroidUtilities.dp(30f))

			seekBarY = AndroidUtilities.dp(13f) + namesOffset + mediaOffsetY
			buttonY = AndroidUtilities.dp(13f) + namesOffset + mediaOffsetY

			radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(44f), buttonY + AndroidUtilities.dp(44f))

			updatePlayingMessageProgress()
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			if (currentMessageObject!!.isOutOwner) {
				seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(56f)
				buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14f)
				timeAudioX = layoutWidth - backgroundWidth + AndroidUtilities.dp(67f)
			}
			else {
				if (isChat && !isThreadPost && currentMessageObject!!.needDrawAvatar()) {
					seekBarX = AndroidUtilities.dp(113f)
					buttonX = AndroidUtilities.dp(71f)
					timeAudioX = AndroidUtilities.dp(124f)
				}
				else {
					seekBarX = AndroidUtilities.dp(65f)
					buttonX = AndroidUtilities.dp(23f)
					timeAudioX = AndroidUtilities.dp(76f)
				}
			}

			if (hasLinkPreview) {
				seekBarX += AndroidUtilities.dp(10f)
				buttonX += AndroidUtilities.dp(10f)
				timeAudioX += AndroidUtilities.dp(10f)
			}

			seekBar.setSize(backgroundWidth - AndroidUtilities.dp((65 + if (hasLinkPreview) 10 else 0).toFloat()), AndroidUtilities.dp(30f))

			seekBarY = AndroidUtilities.dp(29f) + namesOffset + mediaOffsetY
			buttonY = AndroidUtilities.dp(13f) + namesOffset + mediaOffsetY

			radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(44f), buttonY + AndroidUtilities.dp(44f))

			updatePlayingMessageProgress()
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
			buttonX = if (currentMessageObject!!.isOutOwner) {
				layoutWidth - backgroundWidth + AndroidUtilities.dp(14f)
			}
			else {
				if (isChat && !isThreadPost && currentMessageObject!!.needDrawAvatar()) {
					AndroidUtilities.dp(71f)
				}
				else {
					AndroidUtilities.dp(23f)
				}
			}

			if (hasLinkPreview) {
				buttonX += AndroidUtilities.dp(10f)
			}

			buttonY = AndroidUtilities.dp(13f) + namesOffset + mediaOffsetY

			radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(44f), buttonY + AndroidUtilities.dp(44f))
			photoImage.setImageCoordinates((buttonX - AndroidUtilities.dp(10f)).toFloat(), (buttonY - AndroidUtilities.dp(10f)).toFloat(), photoImage.imageWidth, photoImage.imageHeight)
		}
		else if (currentMessageObject?.type == MessageObject.TYPE_CONTACT) {
			val x = if (currentMessageObject!!.isOutOwner) {
				layoutWidth - backgroundWidth + AndroidUtilities.dp(14f)
			}
			else {
				if (isChat && !isThreadPost && currentMessageObject!!.needDrawAvatar()) {
					AndroidUtilities.dp(72f)
				}
				else {
					AndroidUtilities.dp(23f)
				}
			}

			photoImage.setImageCoordinates(x.toFloat(), (AndroidUtilities.dp(13f) + namesOffset).toFloat(), AndroidUtilities.dp(44f).toFloat(), AndroidUtilities.dp(44f).toFloat())
		}
		else {
			var x: Int
			if (currentMessageObject!!.type == MessageObject.TYPE_COMMON && (hasLinkPreview || hasGamePreview || hasInvoicePreview)) {
				val linkX = if (hasGamePreview) {
					unmovedTextX - AndroidUtilities.dp(10f)
				}
				else if (hasInvoicePreview) {
					unmovedTextX + AndroidUtilities.dp(1f)
				}
				else {
					unmovedTextX + AndroidUtilities.dp(1f)
				}

				x = if (isSmallImage) {
					linkX + backgroundWidth - AndroidUtilities.dp(81f)
				}
				else {
					linkX + if (hasInvoicePreview) -AndroidUtilities.dp(6.3f) else AndroidUtilities.dp(10f)
				}
			}
			else {
				if (currentMessageObject?.isOutOwner == true) {
					x = if (mediaBackground) {
						layoutWidth - backgroundWidth - AndroidUtilities.dp(3f)
					}
					else {
						layoutWidth - backgroundWidth + AndroidUtilities.dp(6f)
					}
				}
				else {
					x = if (isChat && isAvatarVisible && !isPlayingRound) {
						AndroidUtilities.dp(63f)
					}
					else {
						AndroidUtilities.dp(15f)
					}

					if (currentPosition != null && !currentPosition!!.edge) {
						x -= AndroidUtilities.dp(10f)
					}
				}
			}

			if (currentPosition != null) {
				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
					x -= AndroidUtilities.dp(2f)
				}

				if (currentPosition!!.leftSpanOffset != 0) {
					x += ceil((currentPosition!!.leftSpanOffset / 1000.0f * groupPhotosWidth).toDouble()).toInt()
				}
			}

			if (currentMessageObject!!.type != 0) {
				x -= AndroidUtilities.dp(2f)
			}

			if (!transitionParams.imageChangeBoundsTransition || transitionParams.updatePhotoImageX) {
				transitionParams.updatePhotoImageX = false
				photoImage.setImageCoordinates(x.toFloat(), photoImage.imageY, photoImage.imageWidth, photoImage.imageHeight)
			}

			buttonX = (x + (photoImage.imageWidth - AndroidUtilities.dp(48f)) / 2.0f).toInt()
			buttonY = (photoImage.imageY + (photoImage.imageHeight - AndroidUtilities.dp(48f)) / 2).toInt()

			radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48f), buttonY + AndroidUtilities.dp(48f))

			deleteProgressRect.set((buttonX + AndroidUtilities.dp(5f)).toFloat(), (buttonY + AndroidUtilities.dp(5f)).toFloat(), (buttonX + AndroidUtilities.dp(43f)).toFloat(), (buttonY + AndroidUtilities.dp(43f)).toFloat())

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
				videoButtonX = (photoImage.imageX + AndroidUtilities.dp(8f)).toInt()
				videoButtonY = (photoImage.imageY + AndroidUtilities.dp(8f)).toInt()
				videoRadialProgress.setProgressRect(videoButtonX, videoButtonY, videoButtonX + AndroidUtilities.dp(24f), videoButtonY + AndroidUtilities.dp(24f))
			}
		}
	}

	fun needDelayRoundProgressDraw(): Boolean {
		return (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) && currentMessageObject!!.type != 5 && MediaController.getInstance().isPlayingMessage(currentMessageObject)
	}

	fun drawRoundProgress(canvas: Canvas) {
		var inset = if (isPlayingRound) AndroidUtilities.dp(4f).toFloat() else 0.toFloat()
		val drawPause = MediaController.getInstance().isPlayingMessage(currentMessageObject) && MediaController.getInstance().isMessagePaused
		val drawTouchedSeekbar = drawPause && roundSeekbarTouched == 1

		if (drawPause && roundToPauseProgress != 1f) {
			roundToPauseProgress += 16 / 220f

			if (roundToPauseProgress > 1f) {
				roundToPauseProgress = 1f
			}
			else {
				invalidate()
			}
		}
		else if (!drawPause && roundToPauseProgress != 0f) {
			roundToPauseProgress -= 16 / 150f

			if (roundToPauseProgress < 0) {
				roundToPauseProgress = 0f
			}
			else {
				invalidate()
			}
		}

		if (drawTouchedSeekbar && roundToPauseProgress2 != 1f) {
			roundToPauseProgress2 += 16 / 150f

			if (roundToPauseProgress2 > 1f) {
				roundToPauseProgress2 = 1f
			}
			else {
				invalidate()
			}
		}
		else if (!drawTouchedSeekbar && roundToPauseProgress2 != 0f) {
			roundToPauseProgress2 -= 16 / 150f

			if (roundToPauseProgress2 < 0) {
				roundToPauseProgress2 = 0f
			}
			else {
				invalidate()
			}
		}

		val pauseProgress = if (drawPause) AndroidUtilities.overshootInterpolator.getInterpolation(roundToPauseProgress) else roundToPauseProgress

		if (transitionParams.animatePlayingRound) {
			inset = (if (isPlayingRound) transitionParams.animateChangeProgress else 1f - transitionParams.animateChangeProgress) * AndroidUtilities.dp(4f)
		}

		inset += AndroidUtilities.dp(16f) * pauseProgress

		if (roundToPauseProgress > 0) {
			val r = photoImage.imageWidth / 2f
			Theme.getRadialSeekbarShadowDrawable().draw(canvas, photoImage.centerX, photoImage.centerY, r, roundToPauseProgress)
		}

		rect.set(photoImage.imageX + AndroidUtilities.dpf2(1.5f) + inset, photoImage.imageY + AndroidUtilities.dpf2(1.5f) + inset, photoImage.imageX2 - AndroidUtilities.dpf2(1.5f) - inset, photoImage.imageY2 - AndroidUtilities.dpf2(1.5f) - inset)

		var oldAlpha = -1

		if (roundProgressAlpha != 1f) {
			oldAlpha = Theme.chat_radialProgressPaint.alpha
			Theme.chat_radialProgressPaint.alpha = (roundProgressAlpha * oldAlpha).toInt()
		}

		if (videoForwardDrawable?.isAnimating == true) {
			videoForwardDrawable?.setBounds(photoImage.imageX.toInt(), photoImage.imageY.toInt(), (photoImage.imageX + photoImage.imageWidth).toInt(), (photoImage.imageY + photoImage.imageHeight).toInt())
			videoForwardDrawable?.draw(canvas)
		}

		val paintAlpha = Theme.chat_radialProgressPaint.alpha
		val paintWidth = Theme.chat_radialProgressPaint.strokeWidth
		val audioProgress = if (roundProgressAlpha == 1f) currentMessageObject!!.audioProgress else lastDrawingAudioProgress

		if (pauseProgress > 0) {
			val radius = rect.width() / 2f

			Theme.chat_radialProgressPaint.strokeWidth = paintWidth + paintWidth * 0.5f * roundToPauseProgress
			Theme.chat_radialProgressPaint.alpha = (paintAlpha * roundToPauseProgress * 0.3f).toInt()

			canvas.drawCircle(rect.centerX(), rect.centerY(), radius, Theme.chat_radialProgressPaint)

			Theme.chat_radialProgressPaint.alpha = paintAlpha

			seekbarRoundX = (rect.centerX() + sin(Math.toRadians((-360 * audioProgress + 180).toDouble())) * radius).toFloat()
			seekbarRoundY = (rect.centerY() + cos(Math.toRadians((-360 * audioProgress + 180).toDouble())) * radius).toFloat()

			Theme.chat_radialProgressPausedSeekbarPaint.color = Color.WHITE
			Theme.chat_radialProgressPausedSeekbarPaint.alpha = (255 * min(1f, pauseProgress)).toInt()

			canvas.drawCircle(seekbarRoundX, seekbarRoundY, AndroidUtilities.dp(3f) + AndroidUtilities.dp(5f) * pauseProgress + AndroidUtilities.dp(3f) * roundToPauseProgress2, Theme.chat_radialProgressPausedSeekbarPaint)
		}

		if (roundSeekbarOutAlpha != 0f) {
			roundSeekbarOutAlpha -= 16f / 150f

			if (roundSeekbarOutAlpha < 0) {
				roundSeekbarOutAlpha = 0f
			}
			else {
				invalidate()
			}
		}

		if (roundSeekbarOutAlpha != 0f) {
			if (oldAlpha == -1) {
				oldAlpha = Theme.chat_radialProgressPaint.alpha
			}

			Theme.chat_radialProgressPaint.alpha = (paintAlpha * (1f - roundSeekbarOutAlpha)).toInt()

			canvas.drawArc(rect, -90f, 360 * audioProgress, false, Theme.chat_radialProgressPaint)

			Theme.chat_radialProgressPaint.alpha = (paintAlpha * roundSeekbarOutAlpha).toInt()

			canvas.drawArc(rect, -90f, 360 * roundSeekbarOutProgress, false, Theme.chat_radialProgressPaint)
		}
		else {
			canvas.drawArc(rect, -90f, 360 * audioProgress, false, Theme.chat_radialProgressPaint)
		}

		if (oldAlpha != -1) {
			Theme.chat_radialProgressPaint.alpha = oldAlpha
		}

		Theme.chat_radialProgressPaint.strokeWidth = paintWidth
	}

	private fun updatePollAnimations(dt: Long) {
		if (pollVoteInProgress) {
			voteRadOffset += 360 * dt / 2000.0f

			val count = (voteRadOffset / 360).toInt()

			voteRadOffset -= (count * 360).toFloat()
			voteCurrentProgressTime += dt.toFloat()

			if (voteCurrentProgressTime >= 500.0f) {
				voteCurrentProgressTime = 500.0f
			}

			voteCurrentCircleLength = if (voteRisingCircleLength) {
				4 + 266 * AndroidUtilities.accelerateInterpolator.getInterpolation(voteCurrentProgressTime / 500.0f)
			}
			else {
				4 - (if (firstCircleLength) 360 else 270) * (1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(voteCurrentProgressTime / 500.0f))
			}

			if (voteCurrentProgressTime == 500.0f) {
				if (voteRisingCircleLength) {
					voteRadOffset += 270f
					voteCurrentCircleLength = -266f
				}

				voteRisingCircleLength = !voteRisingCircleLength

				if (firstCircleLength) {
					firstCircleLength = false
				}

				voteCurrentProgressTime = 0f
			}

			invalidate()
		}
		if (hintButtonVisible && hintButtonProgress < 1.0f) {
			hintButtonProgress += dt / 180.0f

			if (hintButtonProgress > 1.0f) {
				hintButtonProgress = 1.0f
			}

			invalidate()
		}
		else if (!hintButtonVisible && hintButtonProgress > 0.0f) {
			hintButtonProgress -= dt / 180.0f

			if (hintButtonProgress < 0.0f) {
				hintButtonProgress = 0.0f
			}

			invalidate()
		}

		if (animatePollAnswer) {
			pollAnimationProgressTime += dt.toFloat()

			if (pollAnimationProgressTime >= 300.0f) {
				pollAnimationProgressTime = 300.0f
			}

			pollAnimationProgress = AndroidUtilities.decelerateInterpolator.getInterpolation(pollAnimationProgressTime / 300.0f)

			if (pollAnimationProgress >= 1.0f) {
				pollAnimationProgress = 1.0f
				animatePollAnswer = false
				isAnimatingPollAnswer = false
				pollVoteInProgress = false

				if (pollUnvoteInProgress && animatePollAvatars) {
					pollAvatarImages?.forEachIndexed { index, imageReceiver ->
						imageReceiver?.setImageBitmap(null as Drawable?)
						pollAvatarImagesVisible?.set(index, false)
					}
				}

				pollUnvoteInProgress = false

				for (button in pollButtons) {
					button.prevChosen = false
				}
			}

			invalidate()
		}
	}

	private fun drawContent(canvas: Canvas) {
		val newPart = needNewVisiblePart && currentMessageObject?.type == MessageObject.TYPE_COMMON
		val hasSpoilers = hasSpoilers()

		if (newPart || hasSpoilers) {
			getLocalVisibleRect(scrollRect)

			if (hasSpoilers) {
				updateSpoilersVisiblePart(scrollRect.top, scrollRect.bottom)
			}

			if (newPart) {
				setVisiblePart(scrollRect.top, scrollRect.bottom - scrollRect.top, parentHeight, parentViewTopOffset, viewTop, parentWidth, backgroundHeight, blurredViewTopOffset, blurredViewBottomOffset)
				needNewVisiblePart = false
			}
		}

		var buttonX = buttonX.toFloat()
		var buttonY = buttonY.toFloat()

		if (transitionParams.animateButton) {
			buttonX = transitionParams.animateFromButtonX * (1f - transitionParams.animateChangeProgress) + this.buttonX * transitionParams.animateChangeProgress
			buttonY = transitionParams.animateFromButtonY * (1f - transitionParams.animateChangeProgress) + this.buttonY * transitionParams.animateChangeProgress
			radialProgress.setProgressRect(buttonX.toInt(), buttonY.toInt(), buttonX.toInt() + AndroidUtilities.dp(44f), buttonY.toInt() + AndroidUtilities.dp(44f))
		}

		updateSeekBarWaveformWidth()

		forceNotDrawTime = currentMessagesGroup != null

		photoImage.setPressed(if ((isHighlightedAnimated || isHighlighted) && currentPosition != null) 2 else 0)
		photoImage.setVisible(!PhotoViewer.isShowingImage(currentMessageObject) && !SecretMediaViewer.getInstance().isShowingImage(currentMessageObject), false)

		if (!photoImage.visible) {
			mediaWasInvisible = true
			timeWasInvisible = true

			if (animatingNoSound == 1) {
				animatingNoSoundProgress = 0.0f
				animatingNoSound = 0
			}
			else if (animatingNoSound == 2) {
				animatingNoSoundProgress = 1.0f
				animatingNoSound = 0
			}
		}
		else if (groupPhotoInvisible) {
			timeWasInvisible = true
		}
		else if (mediaWasInvisible || timeWasInvisible) {
			if (mediaWasInvisible) {
				controlsAlpha = 0.0f
				mediaWasInvisible = false
			}

			if (timeWasInvisible) {
				timeAlpha = 0.0f
				timeWasInvisible = false
			}

			lastControlsAlphaChangeTime = System.currentTimeMillis()
			totalChangeTime = 0
		}

		radialProgress.setProgressColor(getThemedColor(Theme.key_chat_mediaProgress))
		videoRadialProgress.setProgressColor(getThemedColor(Theme.key_chat_mediaProgress))

		imageDrawn = false

		radialProgress.setCircleCrossfadeColor(null, 0.0f, 1.0f)

		if (currentMessageObject?.type == MessageObject.TYPE_COMMON || currentMessageObject?.type == MessageObject.TYPE_EMOJIS) {
			textX = if (currentMessageObject!!.isOutOwner) {
				getCurrentBackgroundLeft() + AndroidUtilities.dp(11f) + extraTextX
			}
			else {
				getCurrentBackgroundLeft() + AndroidUtilities.dp(if (!mediaBackground && drawPinnedBottom) 11f else 17f) + extraTextX
			}

			if (hasGamePreview) {
				textX += AndroidUtilities.dp(11f)
				textY = AndroidUtilities.dp(14f) + namesOffset

				if (siteNameLayout != null) {
					textY += siteNameLayout!!.getLineBottom(siteNameLayout!!.lineCount - 1)
				}
			}
			else if (hasInvoicePreview) {
				textY = AndroidUtilities.dp(14f) + namesOffset

				if (siteNameLayout != null) {
					textY += siteNameLayout!!.getLineBottom(siteNameLayout!!.lineCount - 1)
				}
			}
			else if (currentMessageObject!!.type == MessageObject.TYPE_EMOJIS) {
				textY = AndroidUtilities.dp(6f) + namesOffset

				if (!currentMessageObject!!.isOut) {
					textX = getCurrentBackgroundLeft()
				}
				else {
					textX -= AndroidUtilities.dp(4f)
				}
			}
			else {
				textY = AndroidUtilities.dp(8f) + namesOffset
			}

			unmovedTextX = textX

			if (currentMessageObject!!.textXOffset != 0f && replyNameLayout != null) {
				var diff = backgroundWidth - AndroidUtilities.dp(31f) - currentMessageObject!!.textWidth

				if (!hasNewLineForTime) {
					diff -= timeWidth + AndroidUtilities.dp((4 + if (currentMessageObject!!.isOutOwner) 20 else 0).toFloat())
				}

				if (diff > 0) {
					textX += diff - extraTimeX
				}
			}

			if (!enterTransitionInProgress && currentMessageObject != null && !currentMessageObject!!.preview) {
				if (!drawForBlur && animatedEmojiStack != null && currentMessageObject!!.textLayoutBlocks != null && currentMessageObject!!.textLayoutBlocks!!.isNotEmpty() || transitionParams.animateOutTextBlocks != null && transitionParams.animateOutTextBlocks!!.isNotEmpty()) {
					animatedEmojiStack?.clearPositions()
				}

				if (transitionParams.animateChangeProgress != 1.0f && transitionParams.animateMessageText) {
					canvas.withSave {
						if (currentBackgroundDrawable != null) {
							val r = currentBackgroundDrawable!!.bounds

							if (currentMessageObject!!.isOutOwner && !mediaBackground && !isPinnedBottom) {
								clipRect(r.left + AndroidUtilities.dp(4f), r.top + AndroidUtilities.dp(4f), r.right - AndroidUtilities.dp(10f), r.bottom - AndroidUtilities.dp(4f))
							}
							else {
								clipRect(r.left + AndroidUtilities.dp(4f), r.top + AndroidUtilities.dp(4f), r.right - AndroidUtilities.dp(4f), r.bottom - AndroidUtilities.dp(4f))
							}
						}

						drawMessageText(this, transitionParams.animateOutTextBlocks, false, 1.0f - transitionParams.animateChangeProgress, false)
						drawMessageText(this, currentMessageObject!!.textLayoutBlocks, true, transitionParams.animateChangeProgress, false)

					}
				}
				else {
					drawMessageText(canvas, currentMessageObject!!.textLayoutBlocks, true, 1.0f, false)
				}
			}

			if (!(enterTransitionInProgress && !currentMessageObject!!.isVoice)) {
				drawLinkPreview(canvas, 1f)
			}

			drawTime = true
		}
		else if (drawPhotoImage) {
			if (isRoundVideo && MediaController.getInstance().isPlayingMessage(currentMessageObject) && MediaController.getInstance().isVideoDrawingReady && canvas.isHardwareAccelerated) {
				imageDrawn = true
				drawTime = true
			}
			else {
				if (currentMessageObject!!.type == MessageObject.TYPE_ROUND_VIDEO && Theme.chat_roundVideoShadow != null) {
					val x = photoImage.imageX - AndroidUtilities.dp(3f)
					val y = photoImage.imageY - AndroidUtilities.dp(2f)

					Theme.chat_roundVideoShadow.alpha = 255
					Theme.chat_roundVideoShadow.setBounds(x.toInt(), y.toInt(), (x + photoImage.imageWidth + AndroidUtilities.dp(6f)).toInt(), (y + photoImage.imageHeight + AndroidUtilities.dp(6f)).toInt())
					Theme.chat_roundVideoShadow.draw(canvas)

					if (!photoImage.hasBitmapImage() || photoImage.currentAlpha != 1f) {
						Theme.chat_docBackPaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outBubble else Theme.key_chat_inBubble)
						canvas.drawCircle(photoImage.centerX, photoImage.centerY, photoImage.imageWidth / 2, Theme.chat_docBackPaint)
					}
				}
				else if (currentMessageObject!!.type == MessageObject.TYPE_GEO) {
					rect.set(photoImage.imageX, photoImage.imageY, photoImage.imageX2, photoImage.imageY2)

					Theme.chat_docBackPaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outLocationBackground else Theme.key_chat_inLocationBackground)

					val rad = photoImage.getRoundRadius()

					rectPath.reset()

					for (a in rad.indices) {
						radii[a * 2 + 1] = rad[a].toFloat()
						radii[a * 2] = radii[a * 2 + 1]
					}

					rectPath.addRoundRect(rect, radii, Path.Direction.CW)
					rectPath.close()

					canvas.drawPath(rectPath, Theme.chat_docBackPaint)

					val iconDrawable = Theme.chat_locationDrawable[if (currentMessageObject!!.isOutOwner) 1 else 0]

					setDrawableBounds(iconDrawable, rect.centerX() - iconDrawable.intrinsicWidth / 2, rect.centerY() - iconDrawable.intrinsicHeight / 2)

					iconDrawable.draw(canvas)
				}

				drawMediaCheckBox = mediaCheckBox != null && (checkBoxVisible || mediaCheckBox!!.getProgress() != 0f || checkBoxAnimationInProgress) && currentMessagesGroup != null

				if (drawMediaCheckBox && (mediaCheckBox!!.isChecked || mediaCheckBox!!.getProgress() != 0f || checkBoxAnimationInProgress) && !textIsSelectionMode()) {
					if (!currentMessagesGroup!!.isDocuments) {
						Theme.chat_replyLinePaint.color = ResourcesCompat.getColor(resources, if (currentMessageObject?.isOutOwner == true) R.color.white else R.color.brand_transparent, null)

						rect.set(photoImage.imageX, photoImage.imageY, photoImage.imageX2, photoImage.imageY2)

						val rad = photoImage.getRoundRadius()

						rectPath.reset()

						for (a in rad.indices) {
							radii[a * 2 + 1] = rad[a].toFloat()
							radii[a * 2] = radii[a * 2 + 1]
						}

						rectPath.addRoundRect(rect, radii, Path.Direction.CW)
						rectPath.close()

						canvas.drawPath(rectPath, Theme.chat_replyLinePaint)
					}

					photoImage.setSideClip(AndroidUtilities.dp(14f) * mediaCheckBox!!.getProgress())

					if (checkBoxAnimationInProgress) {
						mediaCheckBox?.backgroundAlpha = checkBoxAnimationProgress
					}
					else {
						mediaCheckBox?.backgroundAlpha = if (checkBoxVisible) 1.0f else mediaCheckBox!!.getProgress()
					}
				}
				else {
					photoImage.setSideClip(0f)
				}

				if (delegate?.pinchToZoomHelper?.isInOverlayModeFor(this) != true) {
					val top = y + photoImage.imageY
					val bottom = top + photoImage.imageHeight

					photoImageOutOfBounds = (parentBoundsTop != 0f || parentBoundsBottom != 0) && (bottom < parentBoundsTop || top > parentBoundsBottom)

					if (!photoImageOutOfBounds || drawForBlur) {
						photoImage.setSkipUpdateFrame(drawForBlur)

						if (flipImage) {
							canvas.withScale(-1f, 1f, photoImage.centerX, photoImage.centerY) {
								imageDrawn = photoImage.draw(this)
							}
						}
						else {
							imageDrawn = photoImage.draw(canvas)
						}

						photoImage.setSkipUpdateFrame(false)
					}
				}

				val drawTimeOld = drawTime
				val groupPhotoVisible = photoImage.visible

				drawTime = groupPhotoVisible || currentMessageObject!!.shouldDrawReactionsInLayout() && currentMessageObject!!.hasReactions()

				if (currentPosition != null && drawTimeOld != drawTime) {
					val viewGroup = parent as? ViewGroup

					if (viewGroup != null) {
						if (!currentPosition!!.last) {
							val count = viewGroup.childCount

							for (a in 0 until count) {
								val child = viewGroup.getChildAt(a)

								if (child === this || child !is ChatMessageCell) {
									continue
								}

								if (child.currentMessagesGroup === currentMessagesGroup) {
									val position = child.currentPosition

									if (position!!.last && position.maxY == currentPosition!!.maxY && child.timeX - AndroidUtilities.dp(4f) + child.left < right) {
										child.groupPhotoInvisible = !groupPhotoVisible
										child.invalidate()
										viewGroup.invalidate()
									}
								}
							}
						}
						else {
							viewGroup.invalidate()
						}
					}
				}
			}
		}
		else {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
				drawMediaCheckBox = mediaCheckBox != null && (checkBoxVisible || mediaCheckBox!!.getProgress() != 0f || checkBoxAnimationInProgress) && currentMessagesGroup != null

				if (drawMediaCheckBox) {
					radialProgress.setCircleCrossfadeColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outTimeText else Theme.key_chat_inTimeText, checkBoxAnimationProgress, 1.0f - mediaCheckBox!!.getProgress())
				}

				if (drawMediaCheckBox && !textIsSelectionMode() && (mediaCheckBox!!.isChecked || mediaCheckBox!!.getProgress() != 0f || checkBoxAnimationInProgress)) {
					if (checkBoxAnimationInProgress) {
						mediaCheckBox?.backgroundAlpha = checkBoxAnimationProgress

						if (radialProgress.miniIcon == MediaActionDrawable.ICON_NONE) {
							radialProgress.setMiniIconScale(checkBoxAnimationProgress)
						}
					}
					else {
						mediaCheckBox?.backgroundAlpha = if (checkBoxVisible) 1.0f else mediaCheckBox!!.getProgress()
					}
				}
				else if (mediaCheckBox != null) {
					mediaCheckBox?.backgroundAlpha = 1.0f
				}
			}
		}

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
			if (photoImage.visible && !hasGamePreview && !currentMessageObject!!.needDrawBluredPreview()) {
				val oldAlpha = (Theme.chat_msgMediaMenuDrawable as BitmapDrawable).paint.alpha

				Theme.chat_msgMediaMenuDrawable.alpha = (oldAlpha * controlsAlpha).toInt()

				setDrawableBounds(Theme.chat_msgMediaMenuDrawable, (photoImage.imageX + photoImage.imageWidth - AndroidUtilities.dp(14f)).toInt().also { otherX = it }, (photoImage.imageY + AndroidUtilities.dp(8.1f)).toInt().also { otherY = it })

				Theme.chat_msgMediaMenuDrawable.draw(canvas)
				Theme.chat_msgMediaMenuDrawable.alpha = oldAlpha
			}
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
			if (durationLayout != null) {
				val playing = MediaController.getInstance().isPlayingMessage(currentMessageObject)

				if (playing || roundProgressAlpha != 0f) {
					if (playing) {
						roundProgressAlpha = 1f
					}
					else {
						roundProgressAlpha -= 16 / 150f

						if (roundProgressAlpha < 0) {
							roundProgressAlpha = 0f
						}
						else {
							invalidate()
						}
					}

					drawRoundProgress(canvas)
				}
			}
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			if (currentMessageObject!!.isOutOwner) {
				Theme.chat_audioTitlePaint.color = ResourcesCompat.getColor(resources, R.color.white, null) // getThemedColor(Theme.key_chat_outAudioTitleText)
				Theme.chat_audioPerformerPaint.color = ResourcesCompat.getColor(resources, R.color.light_gray, null) // getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_outAudioPerformerSelectedText else Theme.key_chat_outAudioPerformerText)
				Theme.chat_audioTimePaint.color = ResourcesCompat.getColor(resources, R.color.white, null) // getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_outAudioDurationSelectedText else Theme.key_chat_outAudioDurationText)

				// radialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground() || buttonPressed != 0) Theme.key_chat_outAudioSelectedProgress else Theme.key_chat_outAudioProgress))
				radialProgress.setProgressColor(ResourcesCompat.getColor(resources, R.color.white, null))
			}
			else {
				Theme.chat_audioTitlePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null) // getThemedColor(Theme.key_chat_inAudioTitleText)
				Theme.chat_audioPerformerPaint.color = ResourcesCompat.getColor(resources, R.color.dark_gray, null) // getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inAudioPerformerSelectedText else Theme.key_chat_inAudioPerformerText)
				Theme.chat_audioTimePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null) // getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inAudioDurationSelectedText else Theme.key_chat_inAudioDurationText)

				// radialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground() || buttonPressed != 0) Theme.key_chat_inAudioSelectedProgress else Theme.key_chat_inAudioProgress))
				radialProgress.setProgressColor(ResourcesCompat.getColor(resources, R.color.brand, null))
			}

			radialProgress.setBackgroundDrawable(if (isDrawSelectionBackground()) currentBackgroundSelectedDrawable else currentBackgroundDrawable)
			radialProgress.draw(canvas)

			canvas.save()
			canvas.translate((timeAudioX + songX).toFloat(), (AndroidUtilities.dp(13f) + namesOffset + mediaOffsetY).toFloat())

			songLayout?.draw(canvas)

			canvas.restore()

			val showSeekbar = MediaController.getInstance().isPlayingMessage(currentMessageObject)

			if (showSeekbar && toSeekBarProgress != 1f) {
				toSeekBarProgress += 16f / 100f

				if (toSeekBarProgress > 1f) {
					toSeekBarProgress = 1f
				}

				invalidate()
			}
			else if (!showSeekbar && toSeekBarProgress != 0f) {
				toSeekBarProgress -= 16f / 100f

				if (toSeekBarProgress < 0) {
					toSeekBarProgress = 0f
				}

				invalidate()
			}
			if (toSeekBarProgress > 0) {
				if (toSeekBarProgress != 1f) {
					canvas.saveLayerAlpha(seekBarX.toFloat(), seekBarY.toFloat(), (seekBarX + seekBar.getWidth() + AndroidUtilities.dp(24f)).toFloat(), (seekBarY + AndroidUtilities.dp(24f)).toFloat(), (255 * toSeekBarProgress).toInt())
				}
				else {
					canvas.save()
				}

				canvas.translate(seekBarX.toFloat(), seekBarY.toFloat())

				seekBar.draw(canvas)

				canvas.restore()
			}
			if (toSeekBarProgress < 1f) {
				val x = (timeAudioX + performerX).toFloat()
				val y = (AndroidUtilities.dp(35f) + namesOffset + mediaOffsetY).toFloat()

				if (toSeekBarProgress != 0f) {
					canvas.saveLayerAlpha(x, y, x + performerLayout!!.width, y + performerLayout!!.height, (255 * (1f - toSeekBarProgress)).toInt())
				}
				else {
					canvas.save()
				}

				if (toSeekBarProgress != 0f) {
					val s = 0.7f + 0.3f * (1f - toSeekBarProgress)
					canvas.scale(s, s, x, y + performerLayout!!.height / 2f)
				}

				canvas.translate(x, y)

				performerLayout?.draw(canvas)

				canvas.restore()
			}

			canvas.withTranslation(timeAudioX.toFloat(), (AndroidUtilities.dp(57f) + namesOffset + mediaOffsetY).toFloat()) {
				durationLayout?.draw(this)
			}

			//MARK: Here I have hidden the menu for the audio format, if you ever need it, just uncomment the code
//			if (shouldDrawMenuDrawable()) {
//				val menuDrawable: Drawable

//				if (currentMessageObject!!.isOutOwner) {
//					menuDrawable = getThemedDrawable(if (isDrawSelectionBackground()) Theme.key_drawable_msgOutMenuSelected else Theme.key_drawable_msgOutMenu).mutate()
//					menuDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)
//				}
//				else {
//					menuDrawable = (if (isDrawSelectionBackground()) Theme.chat_msgInMenuSelectedDrawable else Theme.chat_msgInMenuDrawable).mutate()
//					menuDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
//				}
//
//				setDrawableBounds(menuDrawable, buttonX.toInt() + backgroundWidth - AndroidUtilities.dp(if (currentMessageObject!!.type == MessageObject.TYPE_COMMON) 58f else 48f).also { otherX = it }, buttonY.toInt() - AndroidUtilities.dp(2f).also { otherY = it })

//				if (transitionParams.animateChangeProgress != 1f && transitionParams.animateShouldDrawMenuDrawable) {
//					menuDrawable.alpha = (255 * transitionParams.animateChangeProgress).toInt()
//				}
//
//				menuDrawable.draw(canvas)
//
//				if (transitionParams.animateChangeProgress != 1f && transitionParams.animateShouldDrawMenuDrawable) {
//					menuDrawable.alpha = 255
//				}
//			}
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
			if (currentMessageObject!!.isOutOwner) {
				Theme.chat_audioTimePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
				radialProgress.setProgressColor(ResourcesCompat.getColor(resources, R.color.white, null))
			}
			else {
				Theme.chat_audioTimePaint.color = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
				radialProgress.setProgressColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
			}

			val audioVisualizerDrawable = if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
				Theme.getCurrentAudiVisualizerDrawable()
			}
			else {
				Theme.getAnimatedOutAudioVisualizerDrawable(currentMessageObject)
			}

			if (audioVisualizerDrawable != null) {
				audioVisualizerDrawable.parentView = this
				audioVisualizerDrawable.draw(canvas, buttonX + AndroidUtilities.dp(22f), buttonY + AndroidUtilities.dp(22f), currentMessageObject!!.isOutOwner)
			}

			if (!enterTransitionInProgress) {
				radialProgress.setBackgroundDrawable(if (isDrawSelectionBackground()) currentBackgroundSelectedDrawable else currentBackgroundDrawable)
				radialProgress.draw(canvas)
			}

			var seekBarX = seekBarX
			var timeAudioX = timeAudioX

			if (transitionParams.animateButton) {
				val offset = this.buttonX - (transitionParams.animateFromButtonX * (1f - transitionParams.animateChangeProgress) + this.buttonX * transitionParams.animateChangeProgress).toInt()
				seekBarX -= offset
				timeAudioX -= offset
			}

			canvas.withSave {
				if (useSeekBarWaveform) {
					translate((seekBarX + AndroidUtilities.dp(13f)).toFloat(), seekBarY.toFloat())
					seekBarWaveform.draw(this, this@ChatMessageCell)
				}
				else {
					translate(seekBarX.toFloat(), seekBarY.toFloat())
					seekBar.draw(this)
				}
			}

			if (useTranscribeButton) {
				canvas.withSave {
					var backgroundWidth = backgroundWidth

					if (transitionParams.animateBackgroundBoundsInner && documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
						backgroundWidth = (this@ChatMessageCell.backgroundWidth - transitionParams.deltaLeft + transitionParams.deltaRight).toInt()
					}

					val seekBarWidth = backgroundWidth - AndroidUtilities.dp((92 + (if (hasLinkPreview) 10 else 0) + 36).toFloat())

					translate((seekBarX + AndroidUtilities.dp((13 + 8).toFloat()) + seekBarWidth.also {
						transcribeX = it.toFloat()
					}).toFloat(), seekBarY.also { transcribeY = it.toFloat() }.toFloat())

					if (transcribeButton == null) {
						transcribeButton = TranscribeButton(this@ChatMessageCell, seekBarWaveform)
						transcribeButton?.setOpen(currentMessageObject!!.messageOwner != null && currentMessageObject!!.messageOwner!!.voiceTranscriptionOpen && currentMessageObject!!.messageOwner!!.voiceTranscriptionFinal, false)
						transcribeButton?.setLoading(TranscribeButton.isTranscribing(currentMessageObject), false)
					}

					transcribeButton?.setColor(currentMessageObject!!.isOut, ResourcesCompat.getColor(resources, if (currentMessageObject?.isOutOwner == true) R.color.white else R.color.brand, null), ResourcesCompat.getColor(resources, R.color.dark_gray, null))
					transcribeButton?.draw(this)

				}
			}

			canvas.withTranslation(timeAudioX.toFloat(), (AndroidUtilities.dp(44f) + namesOffset + mediaOffsetY).toFloat()) {
				durationLayout?.draw(this)
			}

			if (currentMessageObject!!.type != 0 && currentMessageObject!!.isContentUnread) {
				val dotColor = if (currentMessageObject!!.isOutOwner) {
					ResourcesCompat.getColor(resources, R.color.white, null)
				}
				else {
					ResourcesCompat.getColor(resources, R.color.brand, null)
				}

				Theme.chat_docBackPaint.color = dotColor

				canvas.drawCircle((timeAudioX + timeWidthAudio + AndroidUtilities.dp(6f)).toFloat(), (AndroidUtilities.dp(51f) + namesOffset + mediaOffsetY).toFloat(), AndroidUtilities.dp(3f).toFloat(), Theme.chat_docBackPaint)
			}
		}

		if (captionLayout != null) {
			updateCaptionLayout()
		}

		updateReactionLayoutPosition()

		if (shouldDrawCaptionLayout()) {
			drawCaptionLayout(canvas, false, 1f)
		}

		if (hasOldCaptionPreview) {
			val linkX = if (currentMessageObject!!.type == MessageObject.TYPE_PHOTO || currentMessageObject!!.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || currentMessageObject!!.type == MessageObject.TYPE_GIF) {
				(photoImage.imageX + AndroidUtilities.dp(5f)).toInt()
			}
			else {
				backgroundDrawableLeft + AndroidUtilities.dp(if (currentMessageObject?.isOutOwner == true) 11f else 17f)
			}

			val startY = totalHeight - AndroidUtilities.dp(if (drawPinnedTop) 9f else 10f) - linkPreviewHeight - AndroidUtilities.dp(8f)

			var linkPreviewY = startY

			Theme.chat_replyLinePaint.color = ResourcesCompat.getColor(resources, if (currentMessageObject?.isOutOwner == true) R.color.white else R.color.brand, null)

			canvas.drawRect(linkX.toFloat(), (linkPreviewY - AndroidUtilities.dp(3f)).toFloat(), (linkX + AndroidUtilities.dp(2f)).toFloat(), (linkPreviewY + linkPreviewHeight).toFloat(), Theme.chat_replyLinePaint)

			if (siteNameLayout != null) {
				Theme.chat_replyNamePaint.color = ResourcesCompat.getColor(resources, if (currentMessageObject?.isOutOwner == true) R.color.white else R.color.brand, null)

				canvas.withSave {
					val x = if (siteNameRtl) {
						backgroundWidth - siteNameWidth - AndroidUtilities.dp(32f)
					}
					else {
						if (hasInvoicePreview) 0 else AndroidUtilities.dp(10f)
					}

					translate((linkX + x).toFloat(), (linkPreviewY - AndroidUtilities.dp(3f)).toFloat())

					siteNameLayout?.draw(this)
				}

				linkPreviewY += siteNameLayout!!.getLineBottom(siteNameLayout!!.lineCount - 1)
			}

			if (currentMessageObject?.isOutOwner == true) {
				Theme.chat_replyTextPaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
			}
			else {
				Theme.chat_replyTextPaint.color = ResourcesCompat.getColor(resources, R.color.text, null)
			}

			if (descriptionLayout != null) {
				if (linkPreviewY != startY) {
					linkPreviewY += AndroidUtilities.dp(2f)
				}

				descriptionY = linkPreviewY - AndroidUtilities.dp(3f)

				canvas.withTranslation((linkX + AndroidUtilities.dp(10f) + descriptionX).toFloat(), descriptionY.toFloat()) {
					descriptionLayout?.draw(this)
					AnimatedEmojiSpan.drawAnimatedEmojis(this, descriptionLayout, animatedEmojiDescriptionStack, 0f, null, 0f, 0f, 0f, 1f)
				}
			}

			drawTime = true
		}

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
//			MARK: I hid the three dot menu for files, if you need it in the future, just uncomment what I commented out
//			val menuDrawable: Drawable

			if (currentMessageObject!!.isOutOwner) {
				Theme.chat_docNamePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
				Theme.chat_infoPaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
				Theme.chat_docBackPaint.color = getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_outFileBackgroundSelected else Theme.key_chat_outFileBackground)

//				menuDrawable = getThemedDrawable(if (isDrawSelectionBackground()) Theme.key_drawable_msgOutMenuSelected else Theme.key_drawable_msgOutMenu).mutate()
//				menuDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)
			}
			else {
				Theme.chat_docNamePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null)
				Theme.chat_infoPaint.color = ResourcesCompat.getColor(resources, R.color.dark_gray, null) // getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inFileInfoSelectedText else Theme.key_chat_inFileInfoText)
				Theme.chat_docBackPaint.color = getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inFileBackgroundSelected else Theme.key_chat_inFileBackground)

//				menuDrawable = (if (isDrawSelectionBackground()) Theme.chat_msgInMenuSelectedDrawable else Theme.chat_msgInMenuDrawable).mutate()
//				menuDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
			}

			val x: Float
			val titleY: Int
			var subtitleY: Int

			if (drawPhotoImage) {
//				if (currentMessageObject!!.type == MessageObject.TYPE_COMMON) {
//					setDrawableBounds(menuDrawable, (photoImage.imageX + backgroundWidth - AndroidUtilities.dp(56f)).toInt().also { otherX = it }, (photoImage.imageY + AndroidUtilities.dp(4f)).toInt().also { otherY = it })
//				}
//				else {
//					setDrawableBounds(menuDrawable, (photoImage.imageX + backgroundWidth - AndroidUtilities.dp(40f)).toInt().also { otherX = it }, (photoImage.imageY + AndroidUtilities.dp(4f)).toInt().also { otherY = it })
//				}

				x = (photoImage.imageX + photoImage.imageWidth + AndroidUtilities.dp(10f)).toInt().toFloat()

				titleY = (photoImage.imageY + AndroidUtilities.dp(8f)).toInt()

				subtitleY = (photoImage.imageY + if (docTitleLayout != null) docTitleLayout!!.getLineBottom(docTitleLayout!!.lineCount - 1) + AndroidUtilities.dp(13f) else AndroidUtilities.dp(8f)).toInt()

				if (!imageDrawn) {
					if (currentMessageObject!!.isOutOwner) {
						radialProgress.setColors(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected)
						radialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_outFileProgressSelected else Theme.key_chat_outFileProgress))
						videoRadialProgress.setColors(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected)
						videoRadialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_outFileProgressSelected else Theme.key_chat_outFileProgress))
					}
					else {
						radialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected)
						radialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inFileProgressSelected else Theme.key_chat_inFileProgress))
						videoRadialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected)
						videoRadialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inFileProgressSelected else Theme.key_chat_inFileProgress))
					}

					rect.set(photoImage.imageX, photoImage.imageY, photoImage.imageX + photoImage.imageWidth, photoImage.imageY + photoImage.imageHeight)

					val rad = photoImage.getRoundRadius()

					rectPath.reset()

					for (a in rad.indices) {
						radii[a * 2] = rad[a].toFloat()
						radii[a * 2 + 1] = rad[a].toFloat()
					}

					rectPath.addRoundRect(rect, radii, Path.Direction.CW)
					rectPath.close()

					canvas.drawPath(rectPath, Theme.chat_docBackPaint)
				}
				else {
					radialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected)
					radialProgress.setProgressColor(getThemedColor(Theme.key_chat_mediaProgress))

					videoRadialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected)
					videoRadialProgress.setProgressColor(getThemedColor(Theme.key_chat_mediaProgress))

					if (buttonState == -1 && radialProgress.icon != MediaActionDrawable.ICON_NONE) {
						radialProgress.setIcon(MediaActionDrawable.ICON_NONE, true, true)
					}
				}
			}
			else {
//				setDrawableBounds(menuDrawable, buttonX.toInt() + backgroundWidth - AndroidUtilities.dp(if (currentMessageObject?.type == MessageObject.TYPE_COMMON) 58f else 48f).also { otherX = it }, buttonY.toInt() - AndroidUtilities.dp(2f).also { otherY = it })

				x = buttonX + AndroidUtilities.dp(53f)
				titleY = buttonY.toInt() + AndroidUtilities.dp(4f)
				subtitleY = buttonY.toInt() + AndroidUtilities.dp(27f)

				if (docTitleLayout != null && docTitleLayout!!.lineCount > 1) {
					subtitleY += (docTitleLayout!!.lineCount - 1) * AndroidUtilities.dp(16f) + AndroidUtilities.dp(2f)
				}

				if (currentMessageObject?.isOutOwner == true) {
					radialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground() || buttonPressed != 0) Theme.key_chat_outAudioSelectedProgress else Theme.key_chat_outAudioProgress))
					videoRadialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground() || videoButtonPressed != 0) Theme.key_chat_outAudioSelectedProgress else Theme.key_chat_outAudioProgress))
				}
				else {
					radialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground() || buttonPressed != 0) Theme.key_chat_inAudioSelectedProgress else Theme.key_chat_inAudioProgress))
					videoRadialProgress.setProgressColor(getThemedColor(if (isDrawSelectionBackground() || videoButtonPressed != 0) Theme.key_chat_inAudioSelectedProgress else Theme.key_chat_inAudioProgress))
				}
			}

//			if (shouldDrawMenuDrawable()) {
//				if (transitionParams.animateChangeProgress != 1f && transitionParams.animateShouldDrawMenuDrawable) {
//					menuDrawable.alpha = (255 * transitionParams.animateChangeProgress).toInt()
//				}
//
//				menuDrawable.draw(canvas)
//
//				if (transitionParams.animateChangeProgress != 1f && transitionParams.animateShouldDrawMenuDrawable) {
//					menuDrawable.alpha = 255
//				}
//			}
			try {
				if (docTitleLayout != null) {
					canvas.withTranslation(x + docTitleOffsetX, titleY.toFloat()) {
						docTitleLayout?.draw(this)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			try {
				if (infoLayout != null) {
					canvas.withTranslation(x, subtitleY.toFloat()) {
						if (buttonState == 1 && loadingProgressLayout != null) {
							loadingProgressLayout?.draw(this)
						}
						else {
							infoLayout?.draw(this)
						}
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		if (currentMessageObject?.type == MessageObject.TYPE_GEO && MessageObject.getMedia(currentMessageObject!!.messageOwner) !is TLRPC.TLMessageMediaGeoLive && currentMapProvider == MessagesController.MAP_PROVIDER_ELLO && photoImage.hasNotThumb()) {
			val w = (Theme.chat_redLocationIcon.intrinsicWidth * 0.8f).toInt()
			val h = (Theme.chat_redLocationIcon.intrinsicHeight * 0.8f).toInt()
			val x = (photoImage.imageX + (photoImage.imageWidth - w) / 2).toInt()
			val y = (photoImage.imageY + (photoImage.imageHeight / 2 - h)).toInt()

			Theme.chat_redLocationIcon.alpha = (255 * photoImage.currentAlpha).toInt()
			Theme.chat_redLocationIcon.setBounds(x, y, x + w, y + h)
			Theme.chat_redLocationIcon.draw(canvas)
		}

		transitionParams.recordDrawingState()
	}

	private fun updateReactionLayoutPosition() {
		val currentPosition = currentPosition
		val currentMessageObject = currentMessageObject

		if (!reactionsLayoutInBubble.isEmpty && (currentPosition == null || ((currentPosition.flags and MessageObject.POSITION_FLAG_BOTTOM) != 0 && (currentPosition.flags and MessageObject.POSITION_FLAG_LEFT) != 0)) && !reactionsLayoutInBubble.isSmall) {
			if (currentMessageObject?.type == MessageObject.TYPE_EMOJIS || currentMessageObject!!.isAnimatedEmoji || currentMessageObject.isAnyKindOfSticker) {
				if (currentMessageObject?.isOutOwner == true) {
					reactionsLayoutInBubble.x = measuredWidth - reactionsLayoutInBubble.width - AndroidUtilities.dp(16f) //AndroidUtilities.displaySize.x - maxWidth - AndroidUtilities.dp(17);
				}
				else {
					reactionsLayoutInBubble.x = getCurrentBackgroundLeft()
				}
			}
			else {
				if (currentMessageObject.isOutOwner) {
					reactionsLayoutInBubble.x = getCurrentBackgroundLeft() + AndroidUtilities.dp(11f)
				}
				else {
					reactionsLayoutInBubble.x = getCurrentBackgroundLeft() + AndroidUtilities.dp(if (!mediaBackground && drawPinnedBottom) 11f else 17f)

					if (mediaBackground) {
						reactionsLayoutInBubble.x -= AndroidUtilities.dp(9f)
					}
				}
			}

			reactionsLayoutInBubble.y = getBackgroundDrawableBottom() - AndroidUtilities.dp(10f) - reactionsLayoutInBubble.height
			reactionsLayoutInBubble.y -= if (drawCommentButton) AndroidUtilities.dp(43f) else 0

			if (hasNewLineForTime) {
				reactionsLayoutInBubble.y -= AndroidUtilities.dp(16f)
			}

			if (captionLayout != null && (currentMessageObject?.type != 2 && !(currentMessageObject?.isOut == true && drawForwardedName && !drawPhotoImage) && !(currentMessageObject?.type == MessageObject.TYPE_DOCUMENT && drawPhotoImage) || currentPosition != null) && currentMessagesGroup != null) {
				reactionsLayoutInBubble.y -= AndroidUtilities.dp(14f)
			}

			reactionsLayoutInBubble.y += reactionsLayoutInBubble.positionOffsetY

			if (currentMessageObject?.isMediaEmpty != true && currentMessagesGroup == null && !currentMessageObject?.messageOwner?.message.isNullOrEmpty()) {
				reactionsLayoutInBubble.y -= AndroidUtilities.dp(12f)
			}
		}

		if (reactionsLayoutInBubble.isSmall && !reactionsLayoutInBubble.isEmpty) {
			var timeYOffset: Int

			if (shouldDrawTimeOnMedia()) {
				timeYOffset = -if (drawCommentButton) AndroidUtilities.dp(41.3f) else 0
			}
			else {
				if (currentMessageObject!!.isSponsored) {
					timeYOffset = -AndroidUtilities.dp(48f)

					if (hasNewLineForTime) {
						timeYOffset -= AndroidUtilities.dp(16f)
					}
				}
				else {
					timeYOffset = -if (drawCommentButton) AndroidUtilities.dp(43f) else 0
				}
			}

			reactionsLayoutInBubble.y = (if (shouldDrawTimeOnMedia()) photoImage.imageY2 + additionalTimeOffsetY - AndroidUtilities.dp(7.3f) - timeLayout!!.height else layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 7.5f else 6.5f) - timeLayout!!.height + timeYOffset).toInt()
			reactionsLayoutInBubble.y += (timeLayout!!.height / 2f - AndroidUtilities.dp(7f)).toInt()
			reactionsLayoutInBubble.x = timeX
		}
	}

	fun drawLinkPreview(canvas: Canvas, alpha: Float) {
		if (!currentMessageObject!!.isSponsored && !hasLinkPreview && !hasGamePreview && !hasInvoicePreview) {
			return
		}

		var startY: Int
		val linkX: Int

		if (hasGamePreview) {
			startY = AndroidUtilities.dp(14f) + namesOffset
			linkX = unmovedTextX - AndroidUtilities.dp(10f)
		}
		else if (hasInvoicePreview) {
			startY = AndroidUtilities.dp(14f) + namesOffset
			linkX = unmovedTextX + AndroidUtilities.dp(1f)
		}
		else if (currentMessageObject!!.isSponsored) {
			startY = textY + currentMessageObject!!.textHeight - AndroidUtilities.dp(2f)
			if (hasNewLineForTime) {
				startY += AndroidUtilities.dp(16f)
			}
			linkX = unmovedTextX + AndroidUtilities.dp(1f)
		}
		else {
			startY = textY + currentMessageObject!!.textHeight + AndroidUtilities.dp(8f)
			linkX = unmovedTextX + AndroidUtilities.dp(1f)
		}

		var linkPreviewY = startY
		var smallImageStartY = 0

		if (!hasInvoicePreview && !currentMessageObject!!.isSponsored) {
			Theme.chat_replyLinePaint.color = ResourcesCompat.getColor(resources, if (currentMessageObject?.isOutOwner == true) R.color.white else R.color.brand, null)

			if (alpha != 1f) {
				Theme.chat_replyLinePaint.alpha = (alpha * Theme.chat_replyLinePaint.alpha).toInt()
			}

			canvas.drawRect(linkX.toFloat(), (linkPreviewY - AndroidUtilities.dp(3f)).toFloat(), (linkX + AndroidUtilities.dp(2f)).toFloat(), (linkPreviewY + linkPreviewHeight + AndroidUtilities.dp(3f)).toFloat(), Theme.chat_replyLinePaint)
		}

		if (siteNameLayout != null) {
			smallImageStartY = linkPreviewY - AndroidUtilities.dp(1f)

			Theme.chat_replyNamePaint.color = ResourcesCompat.getColor(resources, if (currentMessageObject?.isOutOwner == true) R.color.white else R.color.brand, null)

			if (alpha != 1f) {
				Theme.chat_replyNamePaint.alpha = (alpha * Theme.chat_replyLinePaint.alpha).toInt()
			}

			canvas.withSave {
				var x: Int

				if (siteNameRtl) {
					x = backgroundWidth - siteNameWidth - AndroidUtilities.dp(32f)

					if (isSmallImage) {
						x -= AndroidUtilities.dp((48 + 6).toFloat())
					}
				}
				else {
					x = if (hasInvoicePreview) 0 else AndroidUtilities.dp(10f)
				}

				translate((linkX + x).toFloat(), (linkPreviewY - AndroidUtilities.dp(3f)).toFloat())

				siteNameLayout?.draw(this)
			}

			linkPreviewY += siteNameLayout!!.getLineBottom(siteNameLayout!!.lineCount - 1)
		}

		if ((hasGamePreview || hasInvoicePreview) && currentMessageObject!!.textHeight != 0) {
			startY += currentMessageObject!!.textHeight + AndroidUtilities.dp(4f)
			linkPreviewY += currentMessageObject!!.textHeight + AndroidUtilities.dp(4f)
		}

		if (drawPhotoImage && drawInstantView && drawInstantViewType != 9 && drawInstantViewType != 13 && drawInstantViewType != 11 && drawInstantViewType != 1 || drawInstantViewType == 6 && imageBackgroundColor != 0) {
			if (linkPreviewY != startY) {
				linkPreviewY += AndroidUtilities.dp(2f)
			}

			if (imageBackgroundSideColor != 0) {
				val x = linkX + AndroidUtilities.dp(10f)

				photoImage.setImageCoordinates(x + (imageBackgroundSideWidth - photoImage.imageWidth) / 2, linkPreviewY.toFloat(), photoImage.imageWidth, photoImage.imageHeight)

				rect.set(x.toFloat(), photoImage.imageY, (x + imageBackgroundSideWidth).toFloat(), photoImage.imageY2)

				Theme.chat_instantViewPaint.color = ColorUtils.setAlphaComponent(imageBackgroundSideColor, (255 * alpha).toInt())

				canvas.drawRoundRect(rect, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), Theme.chat_instantViewPaint)
			}
			else {
				photoImage.setImageCoordinates((linkX + AndroidUtilities.dp(10f)).toFloat(), linkPreviewY.toFloat(), photoImage.imageWidth, photoImage.imageHeight)
			}

			if (imageBackgroundColor != 0) {
				rect.set(photoImage.imageX, photoImage.imageY, photoImage.imageX2, photoImage.imageY2)

				if (imageBackgroundGradientColor1 != 0) {
					if (imageBackgroundGradientColor2 != 0) {
						if (motionBackgroundDrawable == null) {
							motionBackgroundDrawable = MotionBackgroundDrawable(imageBackgroundColor, imageBackgroundGradientColor1, imageBackgroundGradientColor2, imageBackgroundGradientColor3, true)

							if (imageBackgroundIntensity < 0) {
								photoImage.setGradientBitmap(motionBackgroundDrawable!!.bitmap)
							}

							if (!photoImage.hasImageSet()) {
								motionBackgroundDrawable?.setRoundRadius(AndroidUtilities.dp(4f))
							}
						}
					}
					else {
						if (gradientShader == null) {
							val r = BackgroundGradientDrawable.getGradientPoints(AndroidUtilities.getWallpaperRotation(imageBackgroundGradientRotation, false), rect.width().toInt(), rect.height().toInt())
							gradientShader = LinearGradient(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), intArrayOf(imageBackgroundColor, imageBackgroundGradientColor1), null, Shader.TileMode.CLAMP)
						}

						Theme.chat_instantViewPaint.shader = gradientShader

						if (alpha != 1f) {
							Theme.chat_instantViewPaint.alpha = (255 * alpha).toInt()
						}
					}
				}
				else {
					Theme.chat_instantViewPaint.shader = null
					Theme.chat_instantViewPaint.color = imageBackgroundColor

					if (alpha != 1f) {
						Theme.chat_instantViewPaint.alpha = (255 * alpha).toInt()
					}
				}

				if (motionBackgroundDrawable != null) {
					motionBackgroundDrawable?.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
					motionBackgroundDrawable?.draw(canvas)
				}
				else if (imageBackgroundSideColor != 0) {
					canvas.drawRect(photoImage.imageX, photoImage.imageY, photoImage.imageX2, photoImage.imageY2, Theme.chat_instantViewPaint)
				}
				else {
					canvas.drawRoundRect(rect, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), Theme.chat_instantViewPaint)
				}

				Theme.chat_instantViewPaint.shader = null
				Theme.chat_instantViewPaint.alpha = 255
			}
			if (drawPhotoImage && drawInstantView && drawInstantViewType != 9) {
				if (drawImageButton) {
					val size = AndroidUtilities.dp(48f)

					buttonX = (photoImage.imageX + (photoImage.imageWidth - size) / 2.0f).toInt()
					buttonY = (photoImage.imageY + (photoImage.imageHeight - size) / 2.0f).toInt()

					radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size)
				}
				if (delegate?.pinchToZoomHelper?.isInOverlayModeFor(this) != true) {
					if (alpha != 1f) {
						photoImage.alpha = alpha
						imageDrawn = photoImage.draw(canvas)
						photoImage.alpha = 1f
					}
					else {
						imageDrawn = photoImage.draw(canvas)
					}
				}
			}

			linkPreviewY += (photoImage.imageHeight + AndroidUtilities.dp(6f)).toInt()
		}
		if (currentMessageObject!!.isOutOwner) {
			val color = ResourcesCompat.getColor(resources, R.color.white, null)

			Theme.chat_replyNamePaint.color = ColorUtils.setAlphaComponent(color, (255 * alpha).toInt())
			Theme.chat_replyTextPaint.color = ColorUtils.setAlphaComponent(color, (255 * alpha).toInt())
		}
		else {
			val color = ResourcesCompat.getColor(resources, R.color.text, null)

			Theme.chat_replyNamePaint.color = ColorUtils.setAlphaComponent(color, (255 * alpha).toInt())
			Theme.chat_replyTextPaint.color = ColorUtils.setAlphaComponent(color, (255 * alpha).toInt())
		}
		if (titleLayout != null) {
			if (linkPreviewY != startY) {
				linkPreviewY += AndroidUtilities.dp(2f)
			}

			if (smallImageStartY == 0) {
				smallImageStartY = linkPreviewY - AndroidUtilities.dp(1f)
			}

			canvas.withTranslation((linkX + AndroidUtilities.dp(10f) + titleX).toFloat(), (linkPreviewY - AndroidUtilities.dp(3f)).toFloat()) {
				titleLayout?.draw(this)
			}

			linkPreviewY += titleLayout!!.getLineBottom(titleLayout!!.lineCount - 1)
		}

		if (authorLayout != null) {
			if (linkPreviewY != startY) {
				linkPreviewY += AndroidUtilities.dp(2f)
			}

			if (smallImageStartY == 0) {
				smallImageStartY = linkPreviewY - AndroidUtilities.dp(1f)
			}

			canvas.withTranslation((linkX + AndroidUtilities.dp(10f) + authorX).toFloat(), (linkPreviewY - AndroidUtilities.dp(3f)).toFloat()) {
				authorLayout?.draw(this)
			}

			linkPreviewY += authorLayout!!.getLineBottom(authorLayout!!.lineCount - 1)
		}

		if (descriptionLayout != null) {
			if (linkPreviewY != startY) {
				linkPreviewY += AndroidUtilities.dp(2f)
			}

			if (smallImageStartY == 0) {
				smallImageStartY = linkPreviewY - AndroidUtilities.dp(1f)
			}

			descriptionY = linkPreviewY - AndroidUtilities.dp(3f)

			canvas.withTranslation((linkX + (if (hasInvoicePreview) 0 else AndroidUtilities.dp(10f)) + descriptionX).toFloat(), descriptionY.toFloat()) {
				if (linkBlockNum == -10) {
					if (links.draw(this)) {
						invalidate()
					}
				}

				if (delegate != null && delegate?.textSelectionHelper != null && delegate?.textSelectionHelper?.isSelected(currentMessageObject) == true) {
					delegate?.textSelectionHelper?.drawDescription(currentMessageObject!!.isOutOwner, descriptionLayout, this)
				}

				descriptionLayout?.draw(this)

				AnimatedEmojiSpan.drawAnimatedEmojis(this, descriptionLayout, animatedEmojiDescriptionStack, 0f, null, 0f, 0f, 0f, 1f)
			}

			linkPreviewY += descriptionLayout!!.getLineBottom(descriptionLayout!!.lineCount - 1)
		}

		if (drawPhotoImage && (!drawInstantView || drawInstantViewType == 9 || drawInstantViewType == 11 || drawInstantViewType == 13 || drawInstantViewType == 1)) {
			if (linkPreviewY != startY) {
				linkPreviewY += AndroidUtilities.dp(2f)
			}

			if (isSmallImage) {
				photoImage.setImageCoordinates((linkX + backgroundWidth - AndroidUtilities.dp(81f)).toFloat(), smallImageStartY.toFloat(), photoImage.imageWidth, photoImage.imageHeight)
			}
			else {
				photoImage.setImageCoordinates((linkX + if (hasInvoicePreview) -AndroidUtilities.dp(6.3f) else AndroidUtilities.dp(10f)).toFloat(), linkPreviewY.toFloat(), photoImage.imageWidth, photoImage.imageHeight)

				if (drawImageButton) {
					val size = AndroidUtilities.dp(48f)

					buttonX = (photoImage.imageX + (photoImage.imageWidth - size) / 2.0f).toInt()
					buttonY = (photoImage.imageY + (photoImage.imageHeight - size) / 2.0f).toInt()

					radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size)
				}
			}

			if (isRoundVideo && MediaController.getInstance().isPlayingMessage(currentMessageObject) && MediaController.getInstance().isVideoDrawingReady && canvas.isHardwareAccelerated) {
				imageDrawn = true
				drawTime = true
			}
			else {
				if (delegate?.pinchToZoomHelper?.isInOverlayModeFor(this) != true) {
					if (alpha != 1f) {
						photoImage.alpha = alpha
						imageDrawn = photoImage.draw(canvas)
						photoImage.alpha = 1f
					}
					else {
						imageDrawn = photoImage.draw(canvas)
					}
				}
			}
		}

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
			videoButtonX = (photoImage.imageX + AndroidUtilities.dp(8f)).toInt()
			videoButtonY = (photoImage.imageY + AndroidUtilities.dp(8f)).toInt()
			videoRadialProgress.setProgressRect(videoButtonX, videoButtonY, videoButtonX + AndroidUtilities.dp(24f), videoButtonY + AndroidUtilities.dp(24f))
		}

		val timeBackgroundPaint = getThemedPaint(Theme.key_paint_chatTimeBackground)

		if (photosCountLayout != null && photoImage.visible) {
			val x = (photoImage.imageX + photoImage.imageWidth - AndroidUtilities.dp(8f) - photosCountWidth).toInt()
			val y = (photoImage.imageY + photoImage.imageHeight - AndroidUtilities.dp(19f)).toInt()

			rect.set((x - AndroidUtilities.dp(4f)).toFloat(), (y - AndroidUtilities.dp(1.5f)).toFloat(), (x + photosCountWidth + AndroidUtilities.dp(4f)).toFloat(), (y + AndroidUtilities.dp(14.5f)).toFloat())

			val oldAlpha = timeBackgroundPaint.alpha

			timeBackgroundPaint.alpha = (oldAlpha * controlsAlpha).toInt()

			Theme.chat_durationPaint.alpha = (255 * controlsAlpha).toInt()

			canvas.drawRoundRect(rect, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), timeBackgroundPaint)

			timeBackgroundPaint.alpha = oldAlpha

			canvas.withTranslation(x.toFloat(), y.toFloat()) {
				photosCountLayout?.draw(this)
			}

			Theme.chat_durationPaint.alpha = 255
		}

		if (videoInfoLayout != null && (!drawPhotoImage || photoImage.visible) && imageBackgroundSideColor == 0) {
			val x: Int
			val y: Int

			if (hasGamePreview || hasInvoicePreview || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) {
				if (drawPhotoImage) {
					x = (photoImage.imageX + AndroidUtilities.dp(8.5f)).toInt()
					y = (photoImage.imageY + AndroidUtilities.dp(6f)).toInt()

					val height = AndroidUtilities.dp(if (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) 14.5f else 16.5f)

					rect.set((x - AndroidUtilities.dp(4f)).toFloat(), (y - AndroidUtilities.dp(1.5f)).toFloat(), (x + durationWidth + AndroidUtilities.dp(4f)).toFloat(), (y + height).toFloat())

					canvas.drawRoundRect(rect, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), timeBackgroundPaint)
				}
				else {
					x = linkX
					y = linkPreviewY
				}
			}
			else {
				x = (photoImage.imageX + photoImage.imageWidth - AndroidUtilities.dp(8f) - durationWidth).toInt()
				y = (photoImage.imageY + photoImage.imageHeight - AndroidUtilities.dp(19f)).toInt()

				rect.set((x - AndroidUtilities.dp(4f)).toFloat(), (y - AndroidUtilities.dp(1.5f)).toFloat(), (x + durationWidth + AndroidUtilities.dp(4f)).toFloat(), (y + AndroidUtilities.dp(14.5f)).toFloat())

				canvas.drawRoundRect(rect, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), getThemedPaint(Theme.key_paint_chatTimeBackground))
			}

			canvas.withTranslation(x.toFloat(), y.toFloat()) {
				if (hasInvoicePreview) {
					if (drawPhotoImage) {
						Theme.chat_shipmentPaint.color = getThemedColor(Theme.key_chat_previewGameText)
					}
					else {
						if (currentMessageObject!!.isOutOwner) {
							Theme.chat_shipmentPaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
						}
						else {
							Theme.chat_shipmentPaint.color = ResourcesCompat.getColor(resources, R.color.text, null)
						}
					}
				}

				videoInfoLayout?.draw(this)
			}
		}

		if (drawInstantView) {
			val instantDrawable: Drawable
			val instantY = startY + linkPreviewHeight + AndroidUtilities.dp(10f)
			val backPaint = Theme.chat_instantViewRectPaint

			if (currentMessageObject!!.isOutOwner) {
				instantDrawable = getThemedDrawable(Theme.key_drawable_msgOutInstant)

				Theme.chat_instantViewPaint.color = ResourcesCompat.getColor(context.resources, R.color.white, null)

				backPaint.color = ResourcesCompat.getColor(context.resources, R.color.white, null)
			}
			else {
				instantDrawable = Theme.chat_msgInInstantDrawable

				Theme.chat_instantViewPaint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)

				backPaint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
			}

			instantButtonRect.set(linkX.toFloat(), instantY.toFloat(), (linkX + instantWidth).toFloat(), (instantY + AndroidUtilities.dp(36f)).toFloat())

			selectorDrawableMaskType[0] = 0
			selectorDrawable[0]?.setBounds(linkX, instantY, linkX + instantWidth, instantY + AndroidUtilities.dp(36f))
			selectorDrawable[0]?.draw(canvas)

			canvas.drawRoundRect(instantButtonRect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), backPaint)

			if (drawInstantViewType == 0) {
				setDrawableBounds(instantDrawable, instantTextLeftX + instantTextX + linkX - AndroidUtilities.dp(15f), instantY + AndroidUtilities.dp(11.5f), AndroidUtilities.dp(9f), AndroidUtilities.dp(13f))
				instantDrawable.draw(canvas)
			}

			if (instantViewLayout != null) {
				canvas.withTranslation((linkX + instantTextX).toFloat(), (instantY + AndroidUtilities.dp(10.5f)).toFloat()) {
					instantViewLayout?.draw(this)
				}
			}
		}
	}

	private fun shouldDrawMenuDrawable(): Boolean {
		return currentMessagesGroup == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP != 0
	}

	private fun drawBotButtons(canvas: Canvas, botButtons: ArrayList<BotButton>, alpha: Float) {
		val addX = if (currentMessageObject!!.isOutOwner) {
			measuredWidth - widthForButtons - AndroidUtilities.dp(10f)
		}
		else {
			backgroundDrawableLeft + AndroidUtilities.dp(if (mediaBackground || drawPinnedBottom) 1f else 7f)
		}

		val top = layoutHeight - AndroidUtilities.dp(2f) + transitionParams.deltaBottom
		var height = 0f

		for (a in botButtons.indices) {
			val button = botButtons[a]
			val bottom = button.y + button.height

			if (bottom > height) {
				height = bottom.toFloat()
			}
		}

		rect.set(0f, top, measuredWidth.toFloat(), top + height)

		if (alpha != 1f) {
			canvas.saveLayerAlpha(rect, (255 * alpha).toInt())
		}
		else {
			canvas.save()
		}

		for (a in botButtons.indices) {
			val button = botButtons[a]
			val y = button.y + layoutHeight - AndroidUtilities.dp(2f) + transitionParams.deltaBottom

			rect.set((button.x + addX).toFloat(), y, (button.x + addX + button.width).toFloat(), y + button.height)

			applyServiceShaderMatrix()

			canvas.drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), getThemedPaint(if (a == pressedBotButton) Theme.key_paint_chatActionBackgroundSelected else Theme.key_paint_chatActionBackground))

			if (hasGradientService()) {
				// TODO: animate color transition
				if (a == pressedBotButton) canvas.drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), botButtonSelectedPaint)
				else canvas.drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), botButtonDefaultPaint)
			}

			canvas.withTranslation((button.x + addX + AndroidUtilities.dp(5f)).toFloat(), y + (AndroidUtilities.dp(44f) - button.title!!.getLineBottom(button.title!!.lineCount - 1)) / 2) {
				button.title?.draw(this)
			}

			if (button.button is TLRPC.TLKeyboardButtonWebView) {
				val drawable = getThemedDrawable(Theme.key_drawable_botWebView)
				val x = button.x + button.width - AndroidUtilities.dp(3f) - drawable.intrinsicWidth + addX

				setDrawableBounds(drawable, x.toFloat(), y + AndroidUtilities.dp(3f))

				drawable.draw(canvas)
			}
			else if (button.button is TLRPC.TLKeyboardButtonUrl) {
				val drawable = if (button.isInviteButton) {
					getThemedDrawable(Theme.key_drawable_botInvite)
				}
				else {
					getThemedDrawable(Theme.key_drawable_botLink)
				}

				val x = button.x + button.width - AndroidUtilities.dp(3f) - drawable.intrinsicWidth + addX

				setDrawableBounds(drawable, x.toFloat(), y + AndroidUtilities.dp(3f))

				drawable.draw(canvas)
			}
			else if (button.button is TLRPC.TLKeyboardButtonSwitchInline) {
				val drawable = getThemedDrawable(Theme.key_drawable_botInline)
				val x = button.x + button.width - AndroidUtilities.dp(3f) - drawable.intrinsicWidth + addX

				setDrawableBounds(drawable, x.toFloat(), y + AndroidUtilities.dp(3f))

				drawable.draw(canvas)
			}
			else if (button.button is TLRPC.TLKeyboardButtonCallback || button.button is TLRPC.TLKeyboardButtonRequestGeoLocation || button.button is TLRPC.TLKeyboardButtonGame || button.button is TLRPC.TLKeyboardButtonBuy || button.button is TLRPC.TLKeyboardButtonUrlAuth) {
				if (button.button is TLRPC.TLKeyboardButtonBuy) {
					val x = button.x + button.width - AndroidUtilities.dp(5f) - Theme.chat_botCardDrawable.intrinsicWidth + addX

					setDrawableBounds(Theme.chat_botCardDrawable, x.toFloat(), y + AndroidUtilities.dp(4f))

					Theme.chat_botCardDrawable.draw(canvas)
				}

//				MARK: This condition draws a small progress bar in the upper right corner of the bot button, if you ever need it, just uncomment this code
//				val drawProgress = (button.button is TLRPC.TLKeyboardButtonCallback || button.button is TLRPC.TLKeyboardButtonGame || button.button is TLRPC.TLKeyboardButtonBuy || button.button is TLRPC.TLKeyboardButtonUrlAuth) && SendMessagesHelper.getInstance(currentAccount).isSendingCallback(currentMessageObject, button.button) || button.button is TLRPC.TLKeyboardButtonRequestGeoLocation && SendMessagesHelper.getInstance(currentAccount).isSendingCurrentLocation(currentMessageObject, button.button)

//				if (drawProgress || button.progressAlpha != 0f) {
//					Theme.chat_botProgressPaint.alpha = min(255, (button.progressAlpha * 255).toInt())
//
//					val x = button.x + button.width - AndroidUtilities.dp((9 + 3).toFloat()) + addX
//
//					if (button.button is TLRPC.TLKeyboardButtonBuy) {
//						y += AndroidUtilities.dp(26f).toFloat()
//					}
//
//					rect.set(x.toFloat(), y + AndroidUtilities.dp(4f), (x + AndroidUtilities.dp(8f)).toFloat(), y + AndroidUtilities.dp((8 + 4).toFloat()))
//
//					canvas.drawArc(rect, button.angle.toFloat(), 220f, false, Theme.chat_botProgressPaint)
//
//					invalidate()
//
//					val newTime = System.currentTimeMillis()
//
//					if (abs(button.lastUpdateTime - System.currentTimeMillis()) < 1000) {
//						val delta = newTime - button.lastUpdateTime
//						val dt = 360 * delta / 2000.0f
//
//						button.angle += dt.toInt()
//						button.angle -= 360 * (button.angle / 360)
//
//						if (drawProgress) {
//							if (button.progressAlpha < 1.0f) {
//								button.progressAlpha += delta / 200.0f
//
//								if (button.progressAlpha > 1.0f) {
//									button.progressAlpha = 1.0f
//								}
//							}
//						}
//						else {
//							if (button.progressAlpha > 0.0f) {
//								button.progressAlpha -= delta / 200.0f
//
//								if (button.progressAlpha < 0.0f) {
//									button.progressAlpha = 0.0f
//								}
//							}
//						}
//					}
//
//					button.lastUpdateTime = newTime
//				}
			}
		}

		canvas.restore()
	}

	fun drawMessageText(canvas: Canvas, textLayoutBlocks: ArrayList<TextLayoutBlock>?, origin: Boolean, alpha: Float, drawOnlyText: Boolean) {
		if (textLayoutBlocks.isNullOrEmpty() || alpha == 0f) {
			return
		}

		val firstVisibleBlockNum: Int
		val lastVisibleBlockNum: Int

		if (origin) {
			if (fullyDraw) {
				this.firstVisibleBlockNum = 0
				this.lastVisibleBlockNum = textLayoutBlocks.size
			}

			firstVisibleBlockNum = this.firstVisibleBlockNum
			lastVisibleBlockNum = this.lastVisibleBlockNum
		}
		else {
			firstVisibleBlockNum = 0
			lastVisibleBlockNum = textLayoutBlocks.size
		}

		var textY = textY.toFloat()

		if (transitionParams.animateText) {
			textY = transitionParams.animateFromTextY * (1f - transitionParams.animateChangeProgress) + this.textY * transitionParams.animateChangeProgress
		}

		if (firstVisibleBlockNum >= 0) {
			var restore = Int.MIN_VALUE
			var oldAlpha = 0
			var oldLinkAlpha = 0
			var needRestoreColor = false

			if (alpha != 1.0f) {
				if (drawOnlyText) {
					needRestoreColor = true
					oldAlpha = Theme.chat_msgTextPaint.alpha
					oldLinkAlpha = Color.alpha(Theme.chat_msgTextPaint.linkColor)

					Theme.chat_msgTextPaint.alpha = (oldAlpha * alpha).toInt()
					Theme.chat_msgTextPaint.linkColor = ColorUtils.setAlphaComponent(Theme.chat_msgTextPaint.linkColor, (oldLinkAlpha * alpha).toInt())
				}
				else {
					if (currentBackgroundDrawable != null) {
						var top = currentBackgroundDrawable!!.bounds.top
						var bottom = currentBackgroundDrawable!!.bounds.bottom

						if (y < 0) {
							top = -y.toInt()
						}

						if (y + measuredHeight - getAdditionalHeight() > parentHeight) {
							bottom = (parentHeight - y).toInt()
						}

						rect.set(getCurrentBackgroundLeft().toFloat(), top.toFloat(), currentBackgroundDrawable!!.bounds.right.toFloat(), bottom.toFloat())
					}
					else {
						rect.set(0f, 0f, measuredWidth.toFloat(), (measuredHeight - getAdditionalHeight()).toFloat())
					}

					restore = canvas.saveLayerAlpha(rect, (alpha * 255).toInt())
				}
			}

			val spoilersColor = if (currentMessageObject!!.isOut && !ChatObject.isChannelAndNotMegaGroup(currentMessageObject!!.chatId, currentAccount)) getThemedColor(Theme.key_chat_outTimeText) else Theme.chat_msgTextPaint.color

			for (a in firstVisibleBlockNum..lastVisibleBlockNum) {
				if (a >= textLayoutBlocks.size) {
					break
				}

				val block = textLayoutBlocks[a]

				canvas.withTranslation((textX - if (block.isRtl) ceil(currentMessageObject!!.textXOffset.toDouble()).toInt() else 0).toFloat(), textY + block.textYOffset + transitionYOffsetForDrawables) {
					if (a == linkBlockNum && !drawOnlyText) {
						if (links.draw(this)) {
							invalidate()
						}
					}

					if (a == linkSelectionBlockNum && urlPathSelection.isNotEmpty() && !drawOnlyText) {
						for (b in urlPathSelection.indices) {
							drawPath(urlPathSelection[b], Theme.chat_textSearchSelectionPaint)
						}
					}

					if (delegate?.textSelectionHelper != null && transitionParams.animateChangeProgress == 1f && !drawOnlyText) {
						delegate?.textSelectionHelper?.draw(currentMessageObject, block, this)
					}

					try {
						Emoji.emojiDrawingYOffset = -transitionYOffsetForDrawables
						SpoilerEffect.renderWithRipple(this@ChatMessageCell, invalidateSpoilersParent, spoilersColor, 0, block.spoilersPatchedTextLayout, block.textLayout, block.spoilers, this, false)
						Emoji.emojiDrawingYOffset = 0f
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			if (needRestoreColor) {
				Theme.chat_msgTextPaint.alpha = oldAlpha
				Theme.chat_msgTextPaint.linkColor = ColorUtils.setAlphaComponent(Theme.chat_msgTextPaint.linkColor, oldLinkAlpha)
			}

			if (restore != Int.MIN_VALUE) {
				canvas.restoreToCount(restore)
			}
		}
	}

	val animatedEmojiSpans: Array<AnimatedEmojiSpan?>?
		get() {
			val messageTextSpans = if (currentMessageObject != null && currentMessageObject!!.messageText is Spanned) (currentMessageObject!!.messageText as Spanned).getSpans(0, currentMessageObject!!.messageText!!.length, AnimatedEmojiSpan::class.java) else null
			val captionTextSpans = if (currentMessageObject != null && currentMessageObject!!.caption is Spanned) (currentMessageObject!!.caption as Spanned).getSpans(0, currentMessageObject!!.caption!!.length, AnimatedEmojiSpan::class.java) else null

			if (messageTextSpans.isNullOrEmpty() && captionTextSpans.isNullOrEmpty()) {
				return null
			}

			val array = arrayOfNulls<AnimatedEmojiSpan>((messageTextSpans?.size ?: 0) + (captionTextSpans?.size ?: 0))
			var j = 0

			if (messageTextSpans != null) {
				var i = 0

				while (i < messageTextSpans.size) {
					array[j] = messageTextSpans[i]
					++i
					++j
				}
			}

			if (captionTextSpans != null) {
				var i = 0

				while (i < captionTextSpans.size) {
					array[j] = captionTextSpans[i]
					++i
					++j
				}
			}

			return array
		}

	fun updateCaptionLayout() {
		if (currentMessageObject?.type == MessageObject.TYPE_PHOTO || currentMessageObject?.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || currentMessageObject?.type == MessageObject.TYPE_GIF) {
			val x: Float
			val y: Float
			val h: Float

			if (transitionParams.imageChangeBoundsTransition) {
				x = transitionParams.animateToImageX
				y = transitionParams.animateToImageY
				h = transitionParams.animateToImageH
			}
			else {
				x = photoImage.imageX
				y = photoImage.imageY
				h = photoImage.imageHeight
			}

			captionX = x + AndroidUtilities.dp(5f) + captionOffsetX
			captionY = y + h + AndroidUtilities.dp(6f)
		}
		else if (hasOldCaptionPreview) {
			captionX = (backgroundDrawableLeft + AndroidUtilities.dp(if (currentMessageObject!!.isOutOwner) 11f else 17f) + captionOffsetX).toFloat()
			captionY = (totalHeight - captionHeight - AndroidUtilities.dp(if (drawPinnedTop) 9f else 10f) - linkPreviewHeight - AndroidUtilities.dp(17f)).toFloat()

			if (drawCommentButton && drawSideButton != 3) {
				captionY -= AndroidUtilities.dp(if (shouldDrawTimeOnMedia()) 41.3f else 43f).toFloat()
			}
		}
		else {
			captionX = (backgroundDrawableLeft + AndroidUtilities.dp(if (currentMessageObject!!.isOutOwner || mediaBackground || drawPinnedBottom) 11f else 17f) + captionOffsetX).toFloat()
			captionY = (totalHeight - captionHeight - AndroidUtilities.dp(if (drawPinnedTop) 9f else 10f)).toFloat()

			if (drawCommentButton && drawSideButton != 3) {
				captionY -= AndroidUtilities.dp(if (shouldDrawTimeOnMedia()) 41.3f else 43f).toFloat()
			}

			if (!reactionsLayoutInBubble.isEmpty && !reactionsLayoutInBubble.isSmall) {
				captionY -= reactionsLayoutInBubble.totalHeight.toFloat()
			}
		}

		captionX += extraTextX.toFloat()
	}

	private fun textIsSelectionMode(): Boolean {
		return if (currentMessagesGroup != null) {
			false
		}
		else {
			delegate?.textSelectionHelper?.isSelected(currentMessageObject) == true
		}
	}

	private val miniIconForCurrentState: Int
		get() {
			if (miniButtonState < 0) {
				return MediaActionDrawable.ICON_NONE
			}

			return if (miniButtonState == 0) {
				MediaActionDrawable.ICON_DOWNLOAD
			}
			else {
				MediaActionDrawable.ICON_CANCEL
			}
		}

	private val iconForCurrentState: Int
		get() {
			if (currentMessageObject?.hasExtendedMedia() == true) {
				return MediaActionDrawable.ICON_NONE
			}

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				if (currentMessageObject!!.isOutOwner) {
					radialProgress.setColors(ResourcesCompat.getColor(resources, R.color.white, null), ResourcesCompat.getColor(resources, R.color.white, null), ResourcesCompat.getColor(resources, R.color.brand, null), ResourcesCompat.getColor(resources, R.color.brand, null))
				}
				else {
					radialProgress.setColors(ResourcesCompat.getColor(resources, R.color.brand, null), ResourcesCompat.getColor(resources, R.color.brand, null), ResourcesCompat.getColor(resources, R.color.white, null), ResourcesCompat.getColor(resources, R.color.white, null))
				}

				return when (buttonState) {
					1 -> MediaActionDrawable.ICON_PAUSE
					2 -> MediaActionDrawable.ICON_DOWNLOAD
					4 -> MediaActionDrawable.ICON_CANCEL
					else -> MediaActionDrawable.ICON_PLAY
				}
			}
			else {
				if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
					if (currentMessageObject!!.isOutOwner) {
						radialProgress.setColors(ResourcesCompat.getColor(resources, R.color.white, null), ResourcesCompat.getColor(resources, R.color.white, null), ResourcesCompat.getColor(resources, R.color.brand, null), ResourcesCompat.getColor(resources, R.color.brand, null))
					}
					else {
						radialProgress.setColors(ResourcesCompat.getColor(resources, R.color.brand, null), ResourcesCompat.getColor(resources, R.color.brand, null), ResourcesCompat.getColor(resources, R.color.white, null), ResourcesCompat.getColor(resources, R.color.white, null))
					}

					when (buttonState) {
						-1 -> return MediaActionDrawable.ICON_FILE
						0 -> return MediaActionDrawable.ICON_DOWNLOAD
						1 -> return MediaActionDrawable.ICON_CANCEL
					}
				}
				else {
					radialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected)
					videoRadialProgress.setColors(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected)

					if (buttonState in 0..3) {
						return if (buttonState == 0) {
							MediaActionDrawable.ICON_DOWNLOAD
						}
						else if (buttonState == 1) {
							MediaActionDrawable.ICON_CANCEL
						}
						else if (buttonState == 2) {
							MediaActionDrawable.ICON_PLAY
						}
						else {
							if (autoPlayingMedia) MediaActionDrawable.ICON_NONE else MediaActionDrawable.ICON_PLAY
						}
					}
					else if (buttonState == -1) {
						if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
							return if (drawPhotoImage && (currentPhotoObject != null || currentPhotoObjectThumb != null) && (photoImage.hasBitmapImage() || currentMessageObject!!.mediaExists || currentMessageObject!!.attachPathExists)) MediaActionDrawable.ICON_NONE else MediaActionDrawable.ICON_FILE
						}
						else if (currentMessageObject!!.needDrawBluredPreview()) {
							return if (currentMessageObject!!.messageOwner!!.destroyTime != 0) {
								if (currentMessageObject!!.isOutOwner) {
									MediaActionDrawable.ICON_SECRETCHECK
								}
								else {
									MediaActionDrawable.ICON_EMPTY_NOPROGRESS
								}
							}
							else {
								MediaActionDrawable.ICON_FIRE
							}
						}
						else if (hasEmbed) {
							return MediaActionDrawable.ICON_PLAY
						}
					}
				}
			}

			return MediaActionDrawable.ICON_NONE
		}

	private val maxNameWidth: Int
		get() {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || currentMessageObject!!.type == MessageObject.TYPE_ROUND_VIDEO) {
				val maxWidth = if (AndroidUtilities.isTablet()) {
					if (isChat && !isThreadPost && !currentMessageObject!!.isOutOwner && currentMessageObject!!.needDrawAvatar()) {
						AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(42f)
					}
					else {
						AndroidUtilities.getMinTabletSide()
					}
				}
				else {
					if (isChat && !isThreadPost && !currentMessageObject!!.isOutOwner && currentMessageObject!!.needDrawAvatar()) {
						min(getParentWidth(), AndroidUtilities.displaySize.y) - AndroidUtilities.dp(42f)
					}
					else {
						min(getParentWidth(), AndroidUtilities.displaySize.y)
					}
				}

				if (isPlayingRound) {
					val backgroundWidthLocal = backgroundWidth - (AndroidUtilities.roundPlayingMessageSize - AndroidUtilities.roundMessageSize)
					return maxWidth - backgroundWidthLocal - AndroidUtilities.dp(57f)
				}

				return maxWidth - backgroundWidth - AndroidUtilities.dp(57f)
			}

			return if (currentMessagesGroup != null && !currentMessagesGroup!!.isDocuments) {
				val dWidth = if (AndroidUtilities.isTablet()) {
					AndroidUtilities.getMinTabletSide()
				}
				else {
					getParentWidth()
				}

				var firstLineWidth = 0

				for (a in currentMessagesGroup!!.posArray.indices) {
					val position = currentMessagesGroup!!.posArray[a]

					firstLineWidth += if (position.minY.toInt() == 0) {
						ceil(((position.pw + position.leftSpanOffset) / 1000.0f * dWidth).toDouble()).toInt()
					}
					else {
						break
					}
				}

				firstLineWidth - AndroidUtilities.dp((31 + if (isAvatarVisible) 48 else 0).toFloat())
			}
			else if (currentMessageObject!!.type == MessageObject.TYPE_EMOJIS) {
				AndroidUtilities.displaySize.x - (currentMessageObject!!.textWidth + AndroidUtilities.dp(14f)) - AndroidUtilities.dp(52f) - if (isAvatarVisible) AndroidUtilities.dp(48f) else 0
			}
			else {
				backgroundWidth - AndroidUtilities.dp(if (mediaBackground) 22f else 31f)
			}
		}

	fun updateButtonState(ifSame: Boolean, animated: Boolean, fromSet: Boolean) {
		@Suppress("NAME_SHADOWING") var animated = animated

		if (currentMessageObject == null) {
			return
		}

		if (animated && (PhotoViewer.isShowingImage(currentMessageObject) || !attachedToWindow)) {
			animated = false
		}

		drawRadialCheckBackground = false

		var fileName: String? = null
		var fileExists = false

		if (currentMessageObject!!.type == MessageObject.TYPE_PHOTO || currentMessageObject!!.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW) {
			if (currentPhotoObject == null) {
				radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, animated)
				return
			}

			fileName = FileLoader.getAttachFileName(currentPhotoObject)
			fileExists = currentMessageObject!!.mediaExists
		}
		else if (currentMessageObject!!.type == MessageObject.TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || currentMessageObject!!.type == MessageObject.TYPE_DOCUMENT || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			if (currentMessageObject!!.useCustomPhoto) {
				buttonState = 1
				radialProgress.setIcon(iconForCurrentState, ifSame, animated)
				return
			}

			if (currentMessageObject!!.attachPathExists && !currentMessageObject?.messageOwner?.attachPath.isNullOrEmpty()) {
				fileName = currentMessageObject?.messageOwner?.attachPath
				fileExists = true
			}
			else if (!currentMessageObject!!.isSendError || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				fileName = currentMessageObject!!.fileName
				fileExists = currentMessageObject!!.mediaExists
			}
		}
		else if (documentAttachType != DOCUMENT_ATTACH_TYPE_NONE) {
			fileName = FileLoader.getAttachFileName(documentAttach)
			fileExists = currentMessageObject!!.mediaExists
		}
		else if (currentPhotoObject != null) {
			fileName = FileLoader.getAttachFileName(currentPhotoObject)
			fileExists = currentMessageObject!!.mediaExists
		}

		val autoDownload = if (documentAttach != null && documentAttach?.dcId == Int.MIN_VALUE) {
			false
		}
		else {
			DownloadController.getInstance(currentAccount).canDownloadMedia(currentMessageObject)
		}

		canStreamVideo = (currentMessageObject!!.isSent || currentMessageObject!!.isForwarded) && documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && autoDownload && currentMessageObject!!.canStreamVideo() && !currentMessageObject!!.needDrawBluredPreview()

		if (SharedConfig.streamMedia && currentMessageObject!!.dialogId.toInt() != 0 && !currentMessageObject!!.isSecretMedia && (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || canStreamVideo && currentPosition != null && (currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0 || currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT == 0))) {
			hasMiniProgress = if (fileExists) 1 else 2
			fileExists = true
		}

		if (currentMessageObject!!.isSendError || TextUtils.isEmpty(fileName) && (currentMessageObject!!.isAnyKindOfSticker || !currentMessageObject!!.isSending && !currentMessageObject!!.isEditing)) {
			radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
			radialProgress.setMiniIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
			videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
			videoRadialProgress.setMiniIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
			return
		}

		val fromBot = currentMessageObject?.messageOwner?.params?.containsKey("query_id") == true

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			if (currentMessageObject!!.isOut && (currentMessageObject!!.isSending && !currentMessageObject!!.isForwarded || currentMessageObject!!.isEditing && currentMessageObject!!.isEditingMedia) || currentMessageObject!!.isSendError && fromBot) {
				if (!currentMessageObject?.messageOwner?.attachPath.isNullOrEmpty()) {
					DownloadController.getInstance(currentAccount).addLoadingFileObserver(currentMessageObject!!.messageOwner!!.attachPath, currentMessageObject, this)

					wasSending = true
					buttonState = 4

					val sending = SendMessagesHelper.getInstance(currentAccount).isSendingMessage(currentMessageObject!!.id)

					if (currentPosition != null && sending && buttonState == 4) {
						drawRadialCheckBackground = true
						iconForCurrentState
						radialProgress.setIcon(MediaActionDrawable.ICON_CHECK, ifSame, animated)
					}
					else {
						radialProgress.setIcon(iconForCurrentState, ifSame, animated)
					}

					radialProgress.setMiniIcon(MediaActionDrawable.ICON_NONE, ifSame, animated)

					if (!fromBot) {
						val progress = ImageLoader.getInstance().getFileProgressSizes(currentMessageObject!!.messageOwner!!.attachPath)
						var loadingProgress = 0f

						if (progress == null && sending) {
							loadingProgress = 1.0f
						}
						else if (progress != null) {
							loadingProgress = DownloadController.getProgress(progress)
						}

						radialProgress.setProgress(loadingProgress, false)
					}
					else {
						radialProgress.setProgress(0f, false)
					}
				}
				else {
					buttonState = -1
					iconForCurrentState
					radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL_NOPROFRESS, ifSame, false)
					radialProgress.setProgress(0f, false)
					radialProgress.setMiniIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
				}
			}
			else {
				if (hasMiniProgress != 0) {
					radialProgress.setMiniProgressBackgroundColor(ResourcesCompat.getColor(resources, if (currentMessageObject?.isOutOwner == true) R.color.white else R.color.brand, null))

					val playing = MediaController.getInstance().isPlayingMessage(currentMessageObject)

					buttonState = if (!playing || MediaController.getInstance().isMessagePaused) 0 else 1

					radialProgress.setIcon(iconForCurrentState, ifSame, animated)

					if (hasMiniProgress == 1) {
						DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
						miniButtonState = -1
					}
					else {
						DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this)

						if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
							createLoadingProgressLayout(documentAttach)
							miniButtonState = 0
						}
						else {
							miniButtonState = 1

							val progress = ImageLoader.getInstance().getFileProgressSizes(fileName)

							if (progress != null) {
								radialProgress.setProgress(DownloadController.getProgress(progress), animated)
								createLoadingProgressLayout(progress[0], progress[1])
							}
							else {
								radialProgress.setProgress(0f, animated)
								createLoadingProgressLayout(0, currentMessageObject!!.size)
							}
						}
					}

					radialProgress.setMiniIcon(miniIconForCurrentState, ifSame, animated)
				}
				else if (fileExists) {
					DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
					val playing = MediaController.getInstance().isPlayingMessage(currentMessageObject)
					buttonState = if (!playing || MediaController.getInstance().isMessagePaused) 0 else 1
					radialProgress.setIcon(iconForCurrentState, ifSame, animated)
				}
				else {
					DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this)

					if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
						buttonState = 2
					}
					else {
						buttonState = 4

						val progress = ImageLoader.getInstance().getFileProgressSizes(fileName)

						if (progress != null) {
							radialProgress.setProgress(DownloadController.getProgress(progress), animated)
							createLoadingProgressLayout(progress[0], progress[1])
						}
						else {
							createLoadingProgressLayout(documentAttach)
							radialProgress.setProgress(0f, animated)
						}
					}

					radialProgress.setIcon(iconForCurrentState, ifSame, animated)
				}
			}

			updatePlayingMessageProgress()
		}
		else if (currentMessageObject!!.type == MessageObject.TYPE_COMMON && documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && documentAttachType != DOCUMENT_ATTACH_TYPE_GIF && documentAttachType != DOCUMENT_ATTACH_TYPE_ROUND && documentAttachType != DOCUMENT_ATTACH_TYPE_VIDEO && documentAttachType != DOCUMENT_ATTACH_TYPE_WALLPAPER && documentAttachType != DOCUMENT_ATTACH_TYPE_THEME) {
			if (currentPhotoObject == null || !drawImageButton) {
				return
			}

			if (!fileExists) {
				DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this)

				var setProgress = 0f

				if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
					buttonState = if (!currentMessageObject!!.loadingCancelled && (documentAttachType == 0 && autoDownload || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && MessageObject.isGifDocument(documentAttach, currentMessageObject!!.hasValidGroupId()) && autoDownload)) {
						1
					}
					else {
						0
					}
				}
				else {
					buttonState = 1

					val progress = ImageLoader.getInstance().getFileProgressSizes(fileName)

					setProgress = if (progress != null) DownloadController.getProgress(progress) else 0f

					if (progress != null && progress[0] == progress[1]) {
						createLoadingProgressLayout(progress[0], progress[1])
					}
					else {
						if (currentMessageObject!!.document != null) {
							createLoadingProgressLayout(currentMessageObject!!.loadedFileSize, currentMessageObject!!.size)
						}
					}
				}

				radialProgress.setProgress(setProgress, false)
			}
			else {
				DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)

				buttonState = if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && !photoImage.allowStartAnimation) 2 else -1
			}

			radialProgress.setIcon(iconForCurrentState, ifSame, animated)

			invalidate()
		}
		else {
			if (currentMessageObject!!.isOut && (currentMessageObject!!.isSending && !currentMessageObject!!.isForwarded || currentMessageObject!!.isEditing && currentMessageObject!!.isEditingMedia)) {
				if (!currentMessageObject?.messageOwner?.attachPath.isNullOrEmpty()) {
					DownloadController.getInstance(currentAccount).addLoadingFileObserver(currentMessageObject!!.messageOwner!!.attachPath, currentMessageObject, this)

					wasSending = true

					var needProgress = currentMessageObject!!.messageOwner?.attachPath == null || !currentMessageObject!!.messageOwner!!.attachPath!!.startsWith("http")
					val params = currentMessageObject!!.messageOwner?.params

					if (currentMessageObject!!.messageOwner?.message != null && params != null && (params.containsKey("url") || params.containsKey("bot"))) {
						needProgress = false
						buttonState = -1
					}
					else {
						buttonState = 1
					}

					val sending = SendMessagesHelper.getInstance(currentAccount).isSendingMessage(currentMessageObject!!.id)

					if (currentPosition != null && sending && buttonState == 1) {
						drawRadialCheckBackground = true
						iconForCurrentState
						radialProgress.setIcon(MediaActionDrawable.ICON_CHECK, ifSame, animated)
					}
					else {
						radialProgress.setIcon(iconForCurrentState, ifSame, animated)
					}

					if (needProgress) {
						val progress = ImageLoader.getInstance().getFileProgressSizes(currentMessageObject!!.messageOwner!!.attachPath)
						var loadingProgress = 0f

						if (progress == null && sending) {
							loadingProgress = 1.0f
						}
						else if (progress != null) {
							loadingProgress = DownloadController.getProgress(progress)
							createLoadingProgressLayout(progress[0], progress[1])
						}

						radialProgress.setProgress(loadingProgress, false)
					}
					else {
						radialProgress.setProgress(0f, false)
					}

					invalidate()
				}
				else {
					iconForCurrentState // I don't know why this is here, but it was in original Java code so maybe it is required (as a call to getter)

					if (currentMessageObject!!.isSticker || currentMessageObject!!.isAnimatedSticker || currentMessageObject!!.isLocation || currentMessageObject!!.isGif) {
						buttonState = -1
						radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
					}
					else {
						buttonState = 1
						radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL_NOPROFRESS, ifSame, false)
					}

					radialProgress.setProgress(0f, false)
				}

				videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false)
			}
			else {
				if (wasSending && !TextUtils.isEmpty(currentMessageObject!!.messageOwner!!.attachPath)) {
					DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
				}

				var isLoadingVideo = false

				if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND && autoPlayingMedia) {
					isLoadingVideo = FileLoader.getInstance(currentAccount).isLoadingVideo(documentAttach, MediaController.getInstance().isPlayingMessage(currentMessageObject))

					val animation = photoImage.animation

					if (animation != null) {
						if (currentMessageObject!!.hadAnimationNotReadyLoading) {
							if (animation.hasBitmap()) {
								currentMessageObject!!.hadAnimationNotReadyLoading = false
							}
						}
						else {
							currentMessageObject!!.hadAnimationNotReadyLoading = isLoadingVideo && !animation.hasBitmap()
						}
					}
					else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && !fileExists) {
						currentMessageObject!!.hadAnimationNotReadyLoading = true
					}
				}
				if (hasMiniProgress != 0) {
					radialProgress.setMiniProgressBackgroundColor(getThemedColor(Theme.key_chat_inLoaderPhoto))

					buttonState = 3

					radialProgress.setIcon(iconForCurrentState, ifSame, animated)

					if (hasMiniProgress == 1) {
						DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
						miniButtonState = -1
					}
					else {
						DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this)

						if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
							miniButtonState = 0
						}
						else {
							miniButtonState = 1

							val progress = ImageLoader.getInstance().getFileProgressSizes(fileName)

							if (progress != null) {
								createLoadingProgressLayout(progress[0], progress[1])
								radialProgress.setProgress(DownloadController.getProgress(progress), animated)
							}
							else {
								createLoadingProgressLayout(documentAttach)
								radialProgress.setProgress(0f, animated)
							}
						}
					}

					radialProgress.setMiniIcon(miniIconForCurrentState, ifSame, animated)
				}
				else if (fileExists || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND && autoPlayingMedia && !currentMessageObject!!.hadAnimationNotReadyLoading && !isLoadingVideo) {
					DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)

					if (drawVideoImageButton && animated) {
						if (animatingDrawVideoImageButton != 1 && animatingDrawVideoImageButtonProgress > 0) {
							if (animatingDrawVideoImageButton == 0) {
								animatingDrawVideoImageButtonProgress = 1.0f
							}

							animatingDrawVideoImageButton = 1
						}
					}
					else if (animatingDrawVideoImageButton == 0) {
						animatingDrawVideoImageButton = 1
					}

					drawVideoImageButton = false
					drawVideoSize = false

					if (currentMessageObject!!.needDrawBluredPreview()) {
						buttonState = -1
					}
					else {
						if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && currentMessageObject!!.gifState == 1f) {
							if (photoImage.isAnimationRunning) {
								currentMessageObject!!.gifState = 0f
								buttonState = -1
							}
							else {
								buttonState = 2
							}
						}
						else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO && !hasEmbed) {
							buttonState = 3
						}
						else {
							buttonState = -1
						}
					}

					videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, animatingDrawVideoImageButton != 0)
					radialProgress.setIcon(iconForCurrentState, ifSame, animated)

					if (!fromSet && photoNotSet) {
						setMessageObject(currentMessageObject, currentMessagesGroup, isPinnedBottom, isPinnedTop)
					}

					invalidate()
				}
				else {
					drawVideoSize = documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF

					if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND && canStreamVideo && !drawVideoImageButton && animated) {
						if (animatingDrawVideoImageButton != 2 && animatingDrawVideoImageButtonProgress < 1.0f) {
							if (animatingDrawVideoImageButton == 0) {
								animatingDrawVideoImageButtonProgress = 0.0f
							}

							animatingDrawVideoImageButton = 2
						}
					}
					else if (animatingDrawVideoImageButton == 0) {
						animatingDrawVideoImageButtonProgress = 1.0f
					}

					DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this)

					if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
						buttonState = if (!currentMessageObject!!.loadingCancelled && autoDownload) {
							1
						}
						else if (currentMessageObject!!.type == MessageObject.TYPE_GEO) {
							-1
						}
						else {
							0
						}

						val hasDocLayout = currentMessageObject!!.type == MessageObject.TYPE_VIDEO || currentMessageObject!!.type == MessageObject.TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO
						var fullWidth = true

						if (currentPosition != null) {
							val mask = MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT
							fullWidth = currentPosition!!.flags and mask == mask
						}

						if ((documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && autoDownload) && canStreamVideo && hasDocLayout && fullWidth) {
							drawVideoImageButton = true
							iconForCurrentState
							radialProgress.setIcon(if (autoPlayingMedia) MediaActionDrawable.ICON_NONE else MediaActionDrawable.ICON_PLAY, ifSame, animated)
							videoRadialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, ifSame, animated)
						}
						else {
							drawVideoImageButton = false
							radialProgress.setIcon(iconForCurrentState, ifSame, animated)
							videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false)

							if (!drawVideoSize && animatingDrawVideoImageButton == 0) {
								animatingDrawVideoImageButtonProgress = 0.0f
							}
						}
					}
					else {
						buttonState = 1

						val progress = ImageLoader.getInstance().getFileProgressSizes(fileName)

						if (progress != null) {
							createLoadingProgressLayout(progress[0], progress[1])
						}
						else {
							createLoadingProgressLayout(documentAttach)
						}

						val hasDocLayout = currentMessageObject!!.type == MessageObject.TYPE_VIDEO || currentMessageObject!!.type == MessageObject.TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO
						var fullWidth = true

						if (currentPosition != null) {
							val mask = MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT
							fullWidth = currentPosition!!.flags and mask == mask
						}

						if ((documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || MessageObject.isGifDocument(documentAttach, currentMessageObject!!.hasValidGroupId()) && autoDownload) && canStreamVideo && hasDocLayout && fullWidth) {
							drawVideoImageButton = true
							iconForCurrentState
							radialProgress.setIcon(if (autoPlayingMedia || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) MediaActionDrawable.ICON_NONE else MediaActionDrawable.ICON_PLAY, ifSame, animated)
							videoRadialProgress.setProgress(if (progress != null) DownloadController.getProgress(progress) else 0f, animated)
							videoRadialProgress.setIcon(MediaActionDrawable.ICON_CANCEL_FILL, ifSame, animated)
						}
						else {
							drawVideoImageButton = false
							radialProgress.setProgress(if (progress != null) DownloadController.getProgress(progress) else 0f, animated)
							radialProgress.setIcon(iconForCurrentState, ifSame, animated)
							videoRadialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, false)

							if (!drawVideoSize && animatingDrawVideoImageButton == 0) {
								animatingDrawVideoImageButtonProgress = 0.0f
							}
						}
					}

					invalidate()
				}
			}
		}

		if (hasMiniProgress == 0) {
			radialProgress.setMiniIcon(MediaActionDrawable.ICON_NONE, false, animated)
		}
	}

	private fun didPressMiniButton() {
		if (miniButtonState == 0) {
			miniButtonState = 1

			radialProgress.setProgress(0f, false)

			if (currentMessageObject != null && !currentMessageObject!!.isAnyKindOfSticker) {
				currentMessageObject?.putInDownloadsStore = true
			}

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL_UP, 0)
				currentMessageObject?.loadingCancelled = false
			}
			else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
				createLoadingProgressLayout(documentAttach)
				FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL_UP, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
				currentMessageObject!!.loadingCancelled = false
			}

			radialProgress.setMiniIcon(miniIconForCurrentState, false, true)

			invalidate()
		}
		else if (miniButtonState == 1) {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
					MediaController.getInstance().cleanupPlayer(true, true)
				}
			}

			miniButtonState = 0
			currentMessageObject?.loadingCancelled = true

			FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach)

			radialProgress.setMiniIcon(miniIconForCurrentState, false, true)

			invalidate()
		}
	}

	private fun didPressButton(animated: Boolean, video: Boolean) {
		if (currentMessageObject != null && !currentMessageObject!!.isAnyKindOfSticker) {
			currentMessageObject?.putInDownloadsStore = true
		}

		if (buttonState == 0 && (!drawVideoImageButton || video)) {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				if (miniButtonState == 0) {
					FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL_UP, 0)
					currentMessageObject!!.loadingCancelled = false
				}

				if (delegate?.needPlayMessage(currentMessageObject) == true) {
					if (hasMiniProgress == 2 && miniButtonState != 1) {
						miniButtonState = 1
						radialProgress.setProgress(0f, false)
						radialProgress.setMiniIcon(miniIconForCurrentState, false, true)
					}

					updatePlayingMessageProgress()

					buttonState = 1

					radialProgress.setIcon(iconForCurrentState, false, true)

					invalidate()
				}
			}
			else {
				if (video) {
					videoRadialProgress.setProgress(0f, false)
				}
				else {
					radialProgress.setProgress(0f, false)
				}

				val thumb: TLRPC.PhotoSize?
				val thumbFilter: String?

				if (currentPhotoObject != null && (photoImage.hasNotThumb() || currentPhotoObjectThumb == null)) {
					thumb = currentPhotoObject
					thumbFilter = if (thumb is TLRPC.TLPhotoStrippedSize || "s" == thumb!!.type) currentPhotoFilterThumb else currentPhotoFilter
				}
				else {
					thumb = currentPhotoObjectThumb
					thumbFilter = currentPhotoFilterThumb
				}

				if (currentMessageObject?.type == MessageObject.TYPE_PHOTO || currentMessageObject?.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW) {
					photoImage.isForceLoading = true
					photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, currentPhotoObject!!.size.toLong(), null, currentMessageObject, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)
				}
				else if (currentMessageObject!!.type == MessageObject.TYPE_GIF) {
					FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL_UP, 0)

					if (currentMessageObject!!.loadedFileSize > 0) {
						createLoadingProgressLayout(documentAttach)
					}
				}
				else if (isRoundVideo) {
					if (currentMessageObject!!.isSecretMedia) {
						FileLoader.getInstance(currentAccount).loadFile(currentMessageObject!!.document, currentMessageObject, FileLoader.PRIORITY_NORMAL_UP, 1)
					}
					else {
						currentMessageObject!!.gifState = 2f

						val document = currentMessageObject!!.document

						photoImage.isForceLoading = true
						photoImage.setImage(ImageLocation.getForDocument(document), null, ImageLocation.getForObject(thumb, document), thumbFilter, document!!.size, null, currentMessageObject, 0)
					}
				}
				else if (currentMessageObject!!.type == MessageObject.TYPE_DOCUMENT) {
					FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL_UP, 0)

					if (currentMessageObject!!.loadedFileSize > 0) {
						createLoadingProgressLayout(documentAttach)
					}
				}
				else if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
					FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL, if (currentMessageObject!!.shouldEncryptPhotoOrVideo()) 2 else 0)

					if (currentMessageObject!!.loadedFileSize > 0) {
						createLoadingProgressLayout(currentMessageObject!!.document)
					}
				}
				else if (currentMessageObject!!.type == MessageObject.TYPE_COMMON && documentAttachType != DOCUMENT_ATTACH_TYPE_NONE) {
					if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
						photoImage.isForceLoading = true
						photoImage.setImage(ImageLocation.getForDocument(documentAttach), null, ImageLocation.getForDocument(currentPhotoObject, documentAttach), currentPhotoFilterThumb, documentAttach!!.size, null, currentMessageObject, 0)

						currentMessageObject?.gifState = 2f

						if (currentMessageObject!!.loadedFileSize > 0) {
							createLoadingProgressLayout(currentMessageObject!!.document)
						}
					}
					else if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
						FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL_UP, 0)
					}
					else if (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) {
						photoImage.setImage(ImageLocation.getForDocument(documentAttach), currentPhotoFilter, ImageLocation.getForDocument(currentPhotoObject, documentAttach), "b1", 0, "jpg", currentMessageObject, 1)
					}
				}
				else {
					photoImage.isForceLoading = true
					photoImage.setImage(ImageLocation.getForObject(currentPhotoObject, photoParentObject), currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, 0, null, currentMessageObject, 0)
				}

				currentMessageObject?.loadingCancelled = false

				buttonState = 1

				if (video) {
					videoRadialProgress.setIcon(MediaActionDrawable.ICON_CANCEL_FILL, false, animated)
				}
				else {
					radialProgress.setIcon(iconForCurrentState, false, animated)
				}

				invalidate()
			}
		}
		else if (buttonState == 1 && (!drawVideoImageButton || video)) {
			photoImage.isForceLoading = false

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				val result = MediaController.getInstance().pauseMessage(currentMessageObject)

				if (result) {
					buttonState = 0
					radialProgress.setIcon(iconForCurrentState, false, animated)
					invalidate()
				}
			}
			else {
				if (currentMessageObject!!.isOut && !drawVideoImageButton && (currentMessageObject!!.isSending || currentMessageObject!!.isEditing)) {
					if (radialProgress.icon != MediaActionDrawable.ICON_CHECK) {
						delegate!!.didPressCancelSendButton(this)
					}
				}
				else {
					currentMessageObject!!.loadingCancelled = true

					if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT || documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER) {
						FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach)
					}
					else if (currentMessageObject!!.type == MessageObject.TYPE_COMMON || currentMessageObject!!.type == MessageObject.TYPE_PHOTO || currentMessageObject!!.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW || currentMessageObject!!.type == 8 || currentMessageObject!!.type == MessageObject.TYPE_ROUND_VIDEO) {
						ImageLoader.getInstance().cancelForceLoadingForImageReceiver(photoImage)
						photoImage.cancelLoadImage()
					}
					else if (currentMessageObject!!.type == MessageObject.TYPE_DOCUMENT) {
						FileLoader.getInstance(currentAccount).cancelLoadFile(currentMessageObject!!.document)
					}

					buttonState = 0

					if (video) {
						videoRadialProgress.setIcon(MediaActionDrawable.ICON_DOWNLOAD, false, animated)
					}
					else {
						radialProgress.setIcon(iconForCurrentState, false, animated)
					}

					invalidate()
				}
			}
		}
		else if (buttonState == 2) {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				radialProgress.setProgress(0f, false)

				FileLoader.getInstance(currentAccount).loadFile(documentAttach, currentMessageObject, FileLoader.PRIORITY_NORMAL_UP, 0)

				currentMessageObject?.loadingCancelled = false

				buttonState = 4

				radialProgress.setIcon(iconForCurrentState, true, animated)

				invalidate()
			}
			else {
				if (isRoundVideo) {
					val playingMessage = MediaController.getInstance().playingMessageObject

					if (playingMessage == null || !playingMessage.isRoundVideo) {
						photoImage.allowStartAnimation = true
						photoImage.startAnimation()
					}
				}
				else {
					photoImage.allowStartAnimation = true
					photoImage.startAnimation()
				}

				currentMessageObject?.gifState = 0f
				buttonState = -1
				radialProgress.setIcon(iconForCurrentState, false, animated)
			}
		}
		else if (buttonState == 3 || buttonState == 0) {
			if (hasMiniProgress == 2 && miniButtonState != 1) {
				miniButtonState = 1
				radialProgress.setProgress(0f, false)
				radialProgress.setMiniIcon(miniIconForCurrentState, false, animated)
			}

			delegate?.didPressImage(this, 0f, 0f)
		}
		else if (buttonState == 4) {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				if (currentMessageObject!!.isOut && (currentMessageObject!!.isSending || currentMessageObject!!.isEditing) || currentMessageObject!!.isSendError) {
					if (radialProgress.icon != MediaActionDrawable.ICON_CHECK) {
						delegate?.didPressCancelSendButton(this)
					}
				}
				else {
					currentMessageObject?.loadingCancelled = true

					FileLoader.getInstance(currentAccount).cancelLoadFile(documentAttach)

					buttonState = 2

					radialProgress.setIcon(iconForCurrentState, false, animated)

					invalidate()
				}
			}
		}
	}

	override fun onFailedDownload(fileName: String, canceled: Boolean) {
		updateButtonState(true, documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC, false)
	}

	override fun onSuccessDownload(fileName: String) {
		if (documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER && currentMessageObject!!.isDice) {
			DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
			setCurrentDiceValue(true)
		}
		else if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			updateButtonState(ifSame = false, animated = true, fromSet = false)
			updateWaveform()
		}
		else {
			if (drawVideoImageButton) {
				videoRadialProgress.setProgress(1f, true)
			}
			else {
				radialProgress.setProgress(1f, true)
			}

			if (!currentMessageObject!!.needDrawBluredPreview() && !autoPlayingMedia && documentAttach != null) {
				if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
					photoImage.setImage(ImageLocation.getForDocument(documentAttach), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), if (currentPhotoObject is TLRPC.TLPhotoStrippedSize || currentPhotoObject != null && "s" == currentPhotoObject!!.type) currentPhotoFilterThumb else currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, documentAttach!!.size, null, currentMessageObject, 0)
					photoImage.allowStartAnimation = true
					photoImage.startAnimation()
					autoPlayingMedia = true
				}
				else if (SharedConfig.autoplayVideo && documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO && (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0 && currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT != 0)) {
					animatingNoSound = 2

					photoImage.setImage(ImageLocation.getForDocument(documentAttach), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), if (currentPhotoObject is TLRPC.TLPhotoStrippedSize || currentPhotoObject != null && "s" == currentPhotoObject!!.type) currentPhotoFilterThumb else currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, documentAttach!!.size, null, currentMessageObject, 0)

					if (!PhotoViewer.isPlayingMessage(currentMessageObject)) {
						photoImage.allowStartAnimation = true
						photoImage.startAnimation()
					}
					else {
						photoImage.allowStartAnimation = false
					}

					autoPlayingMedia = true
				}
				else if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
					photoImage.setImage(ImageLocation.getForDocument(documentAttach), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(currentPhotoObject, photoParentObject), if (currentPhotoObject is TLRPC.TLPhotoStrippedSize || currentPhotoObject != null && "s" == currentPhotoObject!!.type) currentPhotoFilterThumb else currentPhotoFilter, ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject), currentPhotoFilterThumb, currentPhotoObjectThumbStripped, documentAttach!!.size, null, currentMessageObject, 0)

					if (SharedConfig.autoplayGifs) {
						photoImage.allowStartAnimation = true
						photoImage.startAnimation()
					}
					else {
						photoImage.allowStartAnimation = false
						photoImage.stopAnimation()
					}

					autoPlayingMedia = true
				}
			}

			if (currentMessageObject!!.type == MessageObject.TYPE_COMMON) {
				if (!autoPlayingMedia && documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && currentMessageObject!!.gifState != 1f) {
					buttonState = 2
					didPressButton(animated = true, video = false)
				}
				else if (!photoNotSet) {
					updateButtonState(ifSame = false, animated = true, fromSet = false)
				}
				else {
					setMessageObject(currentMessageObject, currentMessagesGroup, isPinnedBottom, isPinnedTop)
				}
			}
			else {
				if (!photoNotSet) {
					updateButtonState(ifSame = false, animated = true, fromSet = false)
				}

				if (photoNotSet) {
					setMessageObject(currentMessageObject, currentMessagesGroup, isPinnedBottom, isPinnedTop)
				}
			}
		}
	}

	override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
		val currentMessageObject = currentMessageObject ?: return

		if (set) {
			if (setCurrentDiceValue(!memCache && !currentMessageObject.wasUnread)) {
				return
			}

			if (thumb && currentMessageObject.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW && !currentMessageObject.mediaExists || !thumb && !currentMessageObject.mediaExists && !currentMessageObject.attachPathExists && (currentMessageObject.type == MessageObject.TYPE_COMMON && (documentAttachType == DOCUMENT_ATTACH_TYPE_WALLPAPER || documentAttachType == DOCUMENT_ATTACH_TYPE_NONE || documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER) || currentMessageObject.type == MessageObject.TYPE_PHOTO)) {
				currentMessageObject.mediaExists = true
				updateButtonState(ifSame = false, animated = true, fromSet = false)
			}
		}
	}

	fun setCurrentDiceValue(instant: Boolean): Boolean {
		if (currentMessageObject!!.isDice) {
			val drawable = photoImage.drawable

			if (drawable is RLottieDrawable) {
				val emoji = currentMessageObject!!.diceEmoji
				val stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(emoji)

				if (stickerSet != null) {
					val value = currentMessageObject!!.diceValue

					if ("\uD83C\uDFB0" == currentMessageObject!!.diceEmoji) {
						if (value in 0..64) {
							(drawable as SlotsDrawable).setDiceNumber(this, value, stickerSet, instant)

							if (currentMessageObject!!.isOut) {
								drawable.setOnFinishCallback(diceFinishCallback, Int.MAX_VALUE)
							}

							currentMessageObject?.wasUnread = false
						}

						if (!drawable.hasBaseDice() && stickerSet.documents.size > 0) {
							(drawable as SlotsDrawable).setBaseDice(this, stickerSet)
						}
					}
					else {
						if (!drawable.hasBaseDice() && stickerSet.documents.size > 0) {
							val document = stickerSet.documents[0]
							val path = FileLoader.getInstance(currentAccount).getPathToAttach(document, true)

							if (drawable.setBaseDice(path)) {
								DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
							}
							else {
								val fileName = FileLoader.getAttachFileName(document)
								DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this)
								FileLoader.getInstance(currentAccount).loadFile(document, stickerSet, FileLoader.PRIORITY_NORMAL, 1)
							}
						}
						if (value >= 0 && value < stickerSet.documents.size) {
							if (!instant && currentMessageObject!!.isOut) {
								val frameSuccess = MessagesController.getInstance(currentAccount).diceSuccess[emoji]

								if (frameSuccess != null && frameSuccess.num == value) {
									drawable.setOnFinishCallback(diceFinishCallback, frameSuccess.frame)
								}
							}

							val document = stickerSet.documents[max(value, 0)]
							val path = FileLoader.getInstance(currentAccount).getPathToAttach(document, true)

							if (drawable.setDiceNumber(path, instant)) {
								DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
							}
							else {
								val fileName = FileLoader.getAttachFileName(document)
								DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this)
								FileLoader.getInstance(currentAccount).loadFile(document, stickerSet, FileLoader.PRIORITY_NORMAL, 1)
							}

							currentMessageObject?.wasUnread = false
						}
					}
				}
				else {
					MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(emoji, isEmoji = true, cache = true)
				}
			}

			return true
		}

		return false
	}

	override fun onAnimationReady(imageReceiver: ImageReceiver) {
		if (currentMessageObject != null && imageReceiver === photoImage && currentMessageObject!!.isAnimatedSticker) {
			delegate?.setShouldNotRepeatSticker(currentMessageObject)
		}
	}

	override fun onProgressDownload(fileName: String, downloadedSize: Long, totalSize: Long) {
		val progress = if (totalSize == 0L) 0f else min(1f, downloadedSize / totalSize.toFloat())

		currentMessageObject?.loadedFileSize = downloadedSize

		createLoadingProgressLayout(downloadedSize, totalSize)

		if (drawVideoImageButton) {
			videoRadialProgress.setProgress(progress, true)
		}
		else {
			radialProgress.setProgress(progress, true)
		}

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
			if (hasMiniProgress != 0) {
				if (miniButtonState != 1) {
					updateButtonState(ifSame = false, animated = false, fromSet = false)
				}
			}
			else {
				if (buttonState != 4) {
					updateButtonState(ifSame = false, animated = false, fromSet = false)
				}
			}
		}
		else {
			if (hasMiniProgress != 0) {
				if (miniButtonState != 1) {
					updateButtonState(ifSame = false, animated = false, fromSet = false)
				}
			}
			else {
				if (buttonState != 1) {
					updateButtonState(ifSame = false, animated = false, fromSet = false)
				}
			}
		}
	}

	override fun onProgressUpload(fileName: String, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) {
		val progress = if (totalSize == 0L) 0f else min(1f, uploadedSize / totalSize.toFloat())

		currentMessageObject?.loadedFileSize = uploadedSize

		radialProgress.setProgress(progress, true)

		if (uploadedSize == totalSize && currentPosition != null) {
			val sending = SendMessagesHelper.getInstance(currentAccount).isSendingMessage(currentMessageObject!!.id)

			if (sending && (buttonState == 1 || buttonState == 4 && documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC)) {
				drawRadialCheckBackground = true
				iconForCurrentState
				radialProgress.setIcon(MediaActionDrawable.ICON_CHECK, false, true)
			}
		}

		createLoadingProgressLayout(uploadedSize, totalSize)
	}

	private fun createLoadingProgressLayout(document: TLRPC.Document?) {
		if (document == null) {
			return
		}

		val progresses = ImageLoader.getInstance().getFileProgressSizes(FileLoader.getDocumentFileName(document))

		if (progresses != null) {
			createLoadingProgressLayout(progresses[0], progresses[1])
		}
		else {
			createLoadingProgressLayout(currentMessageObject!!.loadedFileSize, document.size)
		}
	}

	private fun createLoadingProgressLayout(loadedSize: Long, totalSize: Long) {
		@Suppress("NAME_SHADOWING") var loadedSize = loadedSize
		@Suppress("NAME_SHADOWING") var totalSize = totalSize

		if (totalSize <= 0 || documentAttach == null) {
			loadingProgressLayout = null
			return
		}

		if (lastLoadingSizeTotal == 0L) {
			lastLoadingSizeTotal = totalSize
		}
		else {
			totalSize = lastLoadingSizeTotal

			if (loadedSize > lastLoadingSizeTotal) {
				loadedSize = lastLoadingSizeTotal
			}
		}

		val totalStr = AndroidUtilities.formatFileSize(totalSize)
		val maxAvailableString = String.format("000.0 mm / %s", totalStr)
		var str: String?
		var w = ceil(Theme.chat_infoPaint.measureText(maxAvailableString).toDouble()).toInt()
		var fullWidth = true

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
			val max = max(infoWidth, docTitleWidth)

			str = if (w <= max) {
				String.format("%s / %s", AndroidUtilities.formatFileSize(loadedSize), totalStr)
			}
			else {
				AndroidUtilities.formatFileSize(loadedSize)
			}
		}
		else {
			if (currentPosition != null) {
				val mask = MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT
				fullWidth = currentPosition!!.flags and mask == mask
			}

			str = if (!fullWidth) {
				val percent = (min(1f, loadedSize / totalSize.toFloat()) * 100).toInt()

				if (percent >= 100) {
					"100%"
				}
				else {
					String.format(Locale.US, "%2d%%", percent)
				}
			}
			else {
				String.format("%s / %s", AndroidUtilities.formatFileSize(loadedSize), totalStr)
			}
		}

		w = ceil(Theme.chat_infoPaint.measureText(str).toDouble()).toInt()

		if (fullWidth && w > backgroundWidth - AndroidUtilities.dp(48f)) {
			val percent = (min(1f, loadedSize / totalSize.toFloat()) * 100).toInt()

			str = if (percent >= 100) {
				"100%"
			}
			else {
				String.format(Locale.US, "%2d%%", percent)
			}

			w = ceil(Theme.chat_infoPaint.measureText(str).toDouble()).toInt()
		}

		loadingProgressLayout = StaticLayout(str, Theme.chat_infoPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
	}

	override fun onProvideStructure(structure: ViewStructure) {
		super.onProvideStructure(structure)

		if (allowAssistant) {
			if (!currentMessageObject?.messageText.isNullOrEmpty()) {
				structure.text = currentMessageObject?.messageText
			}
			else if (!currentMessageObject?.caption.isNullOrEmpty()) {
				structure.text = currentMessageObject?.caption
			}
		}
	}

	fun setAllowAssistant(value: Boolean) {
		allowAssistant = value
	}

	private fun measureTime(messageObject: MessageObject) {
		var signString: CharSequence?
		val fromId = messageObject.fromChatId

		signString = if (messageObject.scheduled) {
			null
		}
		else if (ChatObject.isChannel(currentChat)) {
			null
		}
		else if (messageObject.messageOwner?.postAuthor != null) {
			if (isMegagroup && messageObject.fromChatId == messageObject.dialogId) {
				null
			}
			else {
				messageObject.messageOwner?.postAuthor?.replace("\n", "")
			}
		}
		else if (messageObject.messageOwner?.fwdFrom?.postAuthor != null) {
			messageObject.messageOwner?.fwdFrom?.postAuthor?.replace("\n", "")
		}
		else if (messageObject.messageOwner?.fwdFrom?.imported == true) {
			if (messageObject.messageOwner?.fwdFrom?.date == messageObject.messageOwner?.date) {
				context.getString(R.string.ImportedMessage)
			}
			else {
				LocaleController.formatImportedDate(messageObject.messageOwner?.fwdFrom?.date?.toLong() ?: 0) + " " + context.getString(R.string.ImportedMessage)
			}
		}
		else if (!messageObject.isOutOwner && fromId > 0 && messageObject.messageOwner?.post == true) {
			val signUser = MessagesController.getInstance(currentAccount).getUser(fromId)

			if (signUser != null) {
				ContactsController.formatName(signUser.firstName, signUser.lastName).replace('\n', ' ')
			}
			else {
				null
			}
		}
		else {
			null
		}

		var author: User? = null

		if (currentMessageObject!!.isFromUser) {
			author = MessagesController.getInstance(currentAccount).getUser(fromId)
		}

		var hasReplies = messageObject.hasReplies()

		if (messageObject.scheduled || messageObject.isLiveLocation || messageObject.messageOwner?.editHide == true || messageObject.dialogId == BuildConfig.NOTIFICATIONS_BOT_ID || messageObject.messageOwner?.viaBotId != 0L || /* MARK: uncomment to enable secret chats  messageObject.messageOwner?.viaBotName != null || */ (author as? TLRPC.TLUser)?.bot == true) {
			edited = false
		}
		else if (currentPosition == null || currentMessagesGroup?.messages.isNullOrEmpty()) {
			edited = messageObject.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_EDITED != 0 || messageObject.isEditing
		}
		else {
			edited = false

			hasReplies = currentMessagesGroup!!.messages[0].hasReplies()

			if (!currentMessagesGroup!!.messages[0].messageOwner!!.editHide) {
				var a = 0
				val size = currentMessagesGroup!!.messages.size

				while (a < size) {
					val `object` = currentMessagesGroup!!.messages[a]

					if (`object`.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_EDITED != 0 || `object`.isEditing) {
						edited = true
						break
					}

					a++
				}
			}
		}

		edited = edited && !currentMessageObject!!.isVoiceTranscriptionOpen

		val timeString = if (currentMessageObject!!.isSponsored) {
			context.getString(R.string.SponsoredMessage)
		}
		else if (currentMessageObject!!.scheduled && currentMessageObject!!.messageOwner!!.date == 0x7FFFFFFE) {
			""
		}
		else if (edited) {
			context.getString(R.string.EditedMessage) + " " + LocaleController.getInstance().formatterDay.format(messageObject.messageOwner!!.date.toLong() * 1000)
		}
		else {
			LocaleController.getInstance().formatterDay.format(messageObject.messageOwner!!.date.toLong() * 1000)
		}

		currentTimeString = if (signString != null) {
			if (messageObject.messageOwner?.fwdFrom?.imported == true) {
				" $timeString"
			}
			else {
				", $timeString"
			}
		}
		else {
			timeString
		}

		timeWidth = ceil(Theme.chat_timePaint.measureText(currentTimeString).toDouble()).toInt()
		timeTextWidth = timeWidth

		if (currentMessageObject!!.scheduled && currentMessageObject!!.messageOwner!!.date == 0x7FFFFFFE) {
			timeWidth -= AndroidUtilities.dp(8f)
		}

		if (messageObject.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_HAS_VIEWS != 0) {
			currentViewsString = String.format("%s", LocaleController.formatShortNumber(max(1, messageObject.messageOwner!!.views), null))
			viewsTextWidth = ceil(Theme.chat_timePaint.measureText(currentViewsString).toDouble()).toInt()
			timeWidth += viewsTextWidth + Theme.chat_msgInViewsDrawable.intrinsicWidth + AndroidUtilities.dp(10f)
		}

		if (messageObject.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW) {
			val str = LocaleController.formatString(R.string.PaymentCheckoutPay, LocaleController.getInstance().formatCurrencyString(messageObject.messageOwner?.media?.totalAmount ?: 0, messageObject.messageOwner?.media?.currency ?: ""))
			currentUnlockString = if (str.length >= 2) str.substring(0, 1).uppercase() + str.substring(1).lowercase() else str
			unlockTextWidth = ceil(Theme.chat_unlockExtendedMediaTextPaint.measureText(currentUnlockString).toDouble()).toInt()
		}

		if (isChat && isMegagroup && !isThreadChat && hasReplies) {
			currentRepliesString = String.format("%s", LocaleController.formatShortNumber(repliesCount, null))
			repliesTextWidth = ceil(Theme.chat_timePaint.measureText(currentRepliesString).toDouble()).toInt()
			timeWidth += repliesTextWidth + Theme.chat_msgInRepliesDrawable.intrinsicWidth + AndroidUtilities.dp(10f)
		}
		else {
			currentRepliesString = null
		}

		if (isPinned) {
			timeWidth += Theme.chat_msgInPinnedDrawable.intrinsicWidth + AndroidUtilities.dp(3f)
		}

		if (messageObject.scheduled) {
			if (messageObject.isSendError) {
				timeWidth += AndroidUtilities.dp(18f)
			}
			else if (messageObject.isSending && messageObject.messageOwner?.peerId?.channelId != 0L && !messageObject.isSupergroup) {
				timeWidth += AndroidUtilities.dp(18f)
			}
		}

		if (reactionsLayoutInBubble.isSmall) {
			reactionsLayoutInBubble.measure(Int.MAX_VALUE, Gravity.LEFT)
			timeWidth += reactionsLayoutInBubble.width
		}

		if (signString != null) {
			if (availableTimeWidth == 0) {
				availableTimeWidth = AndroidUtilities.dp(1000f)
			}

			var widthForSign = availableTimeWidth - timeWidth

			if (messageObject.isOutOwner) {
				widthForSign -= if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
					AndroidUtilities.dp(20f)
				}
				else {
					AndroidUtilities.dp(96f)
				}
			}

			var width = ceil(Theme.chat_timePaint.measureText(signString, 0, signString.length).toDouble()).toInt()

			if (width > widthForSign) {
				if (widthForSign <= 0) {
					signString = ""
					width = 0
				}
				else {
					signString = TextUtils.ellipsize(signString, Theme.chat_timePaint, widthForSign.toFloat(), TextUtils.TruncateAt.END)
					width = widthForSign
				}
			}

			currentTimeString = signString.toString() + currentTimeString
			timeTextWidth += width
			timeWidth += width
		}
	}

	private fun shouldDrawSelectionOverlay(): Boolean {
		return hasSelectionOverlay() && (isPressed() && isCheckPressed || !isCheckPressed && isPressed || isHighlighted || isHighlightedAnimated) && !textIsSelectionMode() && (currentMessagesGroup == null || drawSelectionBackground) && currentBackgroundDrawable != null
	}

	private val selectionOverlayColor: Int?
		get() = null

	private fun hasSelectionOverlay(): Boolean {
		val selectionOverlayColor = selectionOverlayColor
		return selectionOverlayColor != null && selectionOverlayColor != -0x10000
	}

	private fun isDrawSelectionBackground(): Boolean {
		return (isPressed() && isCheckPressed || !isCheckPressed && isPressed || isHighlighted) && !textIsSelectionMode() && !hasSelectionOverlay()
	}

	fun setDrawSelectionBackground(value: Boolean) {
		if (drawSelectionBackground != value) {
			drawSelectionBackground = value
			invalidate()
		}
	}

	private fun isOpenChatByShare(messageObject: MessageObject): Boolean {
		return messageObject.messageOwner?.fwdFrom?.savedFromPeer != null
	}

	private fun checkNeedDrawShareButton(messageObject: MessageObject?): Boolean {
		if (currentMessageObject!!.deleted || currentMessageObject!!.isSponsored) {
			return false
		}

		if (currentPosition != null) {
			if (!currentMessagesGroup!!.isDocuments && !currentPosition!!.last) {
				return false
			}
		}

		return messageObject!!.needDrawShareButton()
	}

	fun isInsideBackground(x: Float, y: Float): Boolean {
		// TODO: check if this works properly
		return currentBackgroundDrawable != null && x >= backgroundDrawableLeft && x <= backgroundDrawableLeft + backgroundDrawableRight && y >= backgroundDrawableTop && y <= backgroundDrawableTop + backgroundDrawableBottom
	}

	private fun updateCurrentUserAndChat() {
		val messagesController = MessagesController.getInstance(currentAccount)
		val fwdFrom = currentMessageObject!!.messageOwner?.fwdFrom
		val currentUserId = getInstance(currentAccount).getClientUserId()

		if (fwdFrom != null && fwdFrom.fromId is TLRPC.TLPeerChannel && currentMessageObject!!.dialogId == currentUserId) {
			currentChat = MessagesController.getInstance(currentAccount).getChat(fwdFrom.fromId.channelId)
		}
		else if (fwdFrom?.savedFromPeer != null) {
			if (fwdFrom.savedFromPeer.userId != 0L) {
				currentUser = if (fwdFrom.fromId is TLRPC.TLPeerUser) {
					messagesController.getUser(fwdFrom.fromId.userId)
				}
				else {
					messagesController.getUser(fwdFrom.savedFromPeer.userId)
				}
			}
			else if (fwdFrom.savedFromPeer.channelId != 0L) {
				if (currentMessageObject!!.isSavedFromMegagroup && fwdFrom.fromId is TLRPC.TLPeerUser) {
					currentUser = messagesController.getUser(fwdFrom.fromId.userId)
				}
				else {
					currentChat = messagesController.getChat(fwdFrom.savedFromPeer.channelId)
				}
			}
			else if (fwdFrom.savedFromPeer.chatId != 0L) {
				if (fwdFrom.fromId is TLRPC.TLPeerUser) {
					currentUser = messagesController.getUser(fwdFrom.fromId.userId)
				}
				else {
					currentChat = messagesController.getChat(fwdFrom.savedFromPeer.chatId)
				}
			}
		}
		else if (fwdFrom != null && fwdFrom.fromId is TLRPC.TLPeerUser && (fwdFrom.imported || currentMessageObject!!.dialogId == currentUserId)) {
			currentUser = messagesController.getUser(fwdFrom.fromId.userId)
		}
		else if (fwdFrom != null && !TextUtils.isEmpty(fwdFrom.fromName) && (fwdFrom.imported || currentMessageObject!!.dialogId == currentUserId)) {
			currentUser = TLRPC.TLUser()
			currentUser?.firstName = fwdFrom.fromName
		}
		else {
			val fromId = currentMessageObject!!.fromChatId

			if (DialogObject.isUserDialog(fromId) && (!currentMessageObject!!.messageOwner!!.post || ChatObject.isMegagroup(messagesController.getChat(currentMessageObject?.messageOwner?.peerId?.channelId)))) {
				currentUser = messagesController.getUser(fromId)
			}
			else if (DialogObject.isChatDialog(fromId)) {
				currentChat = messagesController.getChat(-fromId)
			}
			else if (currentMessageObject!!.messageOwner!!.post) {
				currentChat = messagesController.getChat(currentMessageObject?.messageOwner?.peerId?.channelId)
			}
		}
	}

	private fun setMessageObjectInternal(messageObject: MessageObject?) {
		if ((messageObject!!.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_HAS_VIEWS != 0 || messageObject.messageOwner?.replies != null) && !currentMessageObject!!.scheduled && !currentMessageObject!!.isSponsored) {
			if (!currentMessageObject!!.viewsReloaded) {
				MessagesController.getInstance(currentAccount).addToViewsQueue(currentMessageObject!!)
				currentMessageObject!!.viewsReloaded = true
			}
		}

		updateCurrentUserAndChat()
		setAvatar(messageObject)
		measureTime(messageObject)

		namesOffset = 0

		var viaUsername: String? = null
		var viaString: CharSequence? = null

		if (messageObject.messageOwner?.viaBotId != 0L) {
			val botUser = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner?.viaBotId)

			if (botUser != null && !TextUtils.isEmpty(botUser.username)) {
				viaUsername = "@" + botUser.username
				viaString = AndroidUtilities.replaceTags(String.format(" %s <b>%s</b>", context.getString(R.string.ViaBot), viaUsername))
				viaWidth = ceil(Theme.chat_replyNamePaint.measureText(viaString, 0, viaString.length).toDouble()).toInt()
				currentViaBotUser = botUser
			}
		}
		// MARK: uncomment to enable secret chats
//		else if (!messageObject.messageOwner?.viaBotName.isNullOrEmpty()) {
//			viaUsername = "@" + messageObject.messageOwner!!.viaBotName
//			viaString = AndroidUtilities.replaceTags(String.format(" %s <b>%s</b>", context.getString(R.string.ViaBot), viaUsername))
//			viaWidth = ceil(Theme.chat_replyNamePaint.measureText(viaString, 0, viaString.length).toDouble()).toInt()
//		}

		val needAuthorName = isNeedAuthorName
		val viaBot = (messageObject.messageOwner?.fwdFrom == null || messageObject.type == MessageObject.TYPE_MUSIC) && viaUsername != null

		if (!hasPsaHint && (needAuthorName || viaBot)) {
			drawNameLayout = true
			nameWidth = maxNameWidth

			if (nameWidth < 0) {
				nameWidth = AndroidUtilities.dp(100f)
			}

			val adminWidth: Int
			val adminString: String?
			var adminLabel: String? = null

			if (isMegagroup && currentChat != null && messageObject.messageOwner?.postAuthor != null && currentChat!!.id == -currentMessageObject!!.fromChatId) {
				adminString = messageObject.messageOwner?.postAuthor?.replace("\n", "")
				adminWidth = ceil(Theme.chat_adminPaint.measureText(adminString).toDouble()).toInt()
				nameWidth -= adminWidth
			}
			else if (isMegagroup && currentChat != null && currentMessageObject!!.isForwardedChannelPost) {
				adminString = context.getString(R.string.DiscussChannel)
				adminWidth = ceil(Theme.chat_adminPaint.measureText(adminString).toDouble()).toInt()
				nameWidth -= adminWidth //TODO
			}
			else if (currentUser != null && !currentMessageObject!!.isOutOwner && !currentMessageObject!!.isAnyKindOfSticker && currentMessageObject!!.type != 5 && delegate != null && delegate!!.getAdminRank(currentUser!!.id).also { adminLabel = it } != null) {
				if (adminLabel.isNullOrEmpty()) {
					adminLabel = context.getString(R.string.ChatAdmin)
				}

				adminString = adminLabel
				adminWidth = ceil(Theme.chat_adminPaint.measureText(adminString).toDouble()).toInt()
				nameWidth -= adminWidth
			}
			else {
				adminString = null
				adminWidth = 0
			}

			currentNameStatus = null

			if (messageObject.customName != null) {
				currentNameString = messageObject.customName
			}
			else if (needAuthorName) {
				currentNameString = authorName
				currentNameStatus = authorStatus
			}
			else {
				currentNameString = ""
			}

			var nameStringFinal = TextUtils.ellipsize(currentNameString!!.replace('\n', ' ').replace('\u200F', ' '), Theme.chat_namePaint, (nameWidth - if (viaBot) viaWidth else 0).toFloat(), TextUtils.TruncateAt.END)

			if (viaBot) {
				viaNameWidth = ceil(Theme.chat_namePaint.measureText(nameStringFinal, 0, nameStringFinal.length).toDouble()).toInt()

				if (viaNameWidth != 0) {
					viaNameWidth += AndroidUtilities.dp(4f)
				}

				val color = if (currentMessageObject!!.shouldDrawWithoutBackground()) {
					getThemedColor(Theme.key_chat_stickerViaBotNameText)
				}
				else {
					getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outViaBotNameText else Theme.key_chat_inViaBotNameText)
				}

				val viaBotString = context.getString(R.string.ViaBot)

				if (!currentNameString.isNullOrEmpty()) {
					val stringBuilder = SpannableStringBuilder(String.format("%s %s %s", nameStringFinal, viaBotString, viaUsername))

					stringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_DEFAULT, 0, color).also {
						viaSpan1 = it
					}, nameStringFinal.length + 1, nameStringFinal.length + 1 + viaBotString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

					stringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD, 0, color).also { viaSpan2 = it }, nameStringFinal.length + 2 + viaBotString.length, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

					nameStringFinal = stringBuilder
				}
				else {
					val stringBuilder = SpannableStringBuilder(String.format("%s %s", viaBotString, viaUsername))

					stringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_DEFAULT, 0, color).also {
						viaSpan1 = it
					}, 0, viaBotString.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

					stringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD, 0, color).also { viaSpan2 = it }, 1 + viaBotString.length, stringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

					nameStringFinal = stringBuilder
				}

				nameStringFinal = TextUtils.ellipsize(nameStringFinal, Theme.chat_namePaint, nameWidth.toFloat(), TextUtils.TruncateAt.END)
			}

			try {
				nameStringFinal = Emoji.replaceEmoji(nameStringFinal, Theme.chat_namePaint.fontMetricsInt, false)
			}
			catch (e: Exception) {
				// ignored
			}

			try {
				nameLayout = StaticLayout(nameStringFinal, Theme.chat_namePaint, nameWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				if (nameLayout!!.lineCount > 0) {
					nameLayoutWidth = ceil(nameLayout!!.getLineWidth(0).toDouble()).toInt()
					nameWidth = nameLayoutWidth

					if (!messageObject.isAnyKindOfSticker) {
						namesOffset += AndroidUtilities.dp(19f)
					}

					nameOffsetX = nameLayout!!.getLineLeft(0)
				}
				else {
					nameLayoutWidth = 0
					nameWidth = nameLayoutWidth
				}

				if (currentNameStatus != null) {
					nameWidth += AndroidUtilities.dp((4 + 12 + 4).toFloat())
				}

				if (adminString != null) {
					adminLayout = StaticLayout(adminString, Theme.chat_adminPaint, adminWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
					nameWidth += (adminLayout!!.getLineWidth(0) + AndroidUtilities.dp(8f)).toInt()
				}
				else {
					adminLayout = null
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (currentNameStatusDrawable == null) {
				currentNameStatusDrawable = SwapAnimatedEmojiDrawable(if (parent is View) parent as View else null, AndroidUtilities.dp(20f))
			}

			when (currentNameStatus) {
				null -> {
					currentNameStatusDrawable?.set(null as Drawable?, false)
				}

				is Long -> {
					currentNameStatusDrawable!![currentNameStatus as Long] = false
				}

				is Drawable -> {
					currentNameStatusDrawable!![currentNameStatus as Drawable?] = false
				}
			}

			if (currentNameString!!.isEmpty()) {
				currentNameString = null
			}
		}
		else {
			currentNameString = null
			nameLayout = null
			nameWidth = 0
		}

		currentForwardUser = null
		currentForwardNameString = null
		currentForwardChannel = null
		currentForwardName = null

		forwardedNameLayout[0] = null
		forwardedNameLayout[1] = null

		replyPanelIsForward = false

		forwardedNameWidth = 0

		if (messageObject.isForwarded) {
			when (messageObject.messageOwner?.fwdFrom?.fromId) {
				is TLRPC.TLPeerChannel -> {
					currentForwardChannel = MessagesController.getInstance(currentAccount).getChat(messageObject.messageOwner?.fwdFrom?.fromId?.channelId)
				}

				is TLRPC.TLPeerChat -> {
					currentForwardChannel = MessagesController.getInstance(currentAccount).getChat(messageObject.messageOwner?.fwdFrom?.fromId?.chatId)
				}

				is TLRPC.TLPeerUser -> {
					currentForwardUser = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner?.fwdFrom?.fromId?.userId)
				}
			}
		}

		if (drawForwardedName && messageObject.needDrawForwarded() && (currentPosition == null || currentPosition!!.minY.toInt() == 0)) {
			if (messageObject.messageOwner?.fwdFrom?.fromName != null) {
				currentForwardName = messageObject.messageOwner?.fwdFrom?.fromName
			}

			if (currentForwardUser != null || currentForwardChannel != null || currentForwardName != null) {
				currentForwardNameString = if (currentForwardChannel != null) {
					if (currentForwardUser != null) {
						String.format("%s (%s)", currentForwardChannel!!.title, getUserName(currentForwardUser))
					}
					else if (!messageObject.messageOwner?.fwdFrom?.postAuthor.isNullOrEmpty()) {
						String.format("%s (%s)", currentForwardChannel!!.title, messageObject.messageOwner?.fwdFrom?.postAuthor)
					}
					else {
						currentForwardChannel!!.title
					}
				}
				else if (currentForwardUser != null) {
					getUserName(currentForwardUser)
				}
				else {
					currentForwardName
				}

				forwardedNameWidth = maxNameWidth

				val forwardedString = getForwardedMessageText(messageObject)

				if (hasPsaHint) {
					forwardedNameWidth -= AndroidUtilities.dp(36f)
				}

				val from = context.getString(R.string.From)
				val fromFormattedString = context.getString(R.string.FromFormatted)
				val idx = fromFormattedString.indexOf("%1\$s")
				val fromWidth = ceil(Theme.chat_forwardNamePaint.measureText("$from ").toDouble()).toInt()
				val name = TextUtils.ellipsize(currentForwardNameString!!.replace('\n', ' '), Theme.chat_replyNamePaint, (forwardedNameWidth - fromWidth - viaWidth).toFloat(), TextUtils.TruncateAt.END)

				val fromString = try {
					String.format(fromFormattedString, name)
				}
				catch (e: Exception) {
					name.toString()
				}

				var lastLine: CharSequence?
				val stringBuilder: SpannableStringBuilder

				if (viaString != null) {
					stringBuilder = SpannableStringBuilder(String.format("%s %s %s", fromString, context.getString(R.string.ViaBot), viaUsername))
					viaNameWidth = ceil(Theme.chat_forwardNamePaint.measureText(fromString).toDouble()).toInt()
					stringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), stringBuilder.length - viaUsername!!.length - 1, stringBuilder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				else {
					stringBuilder = SpannableStringBuilder(String.format(fromFormattedString, name))
				}

				forwardNameCenterX = fromWidth + ceil(Theme.chat_forwardNamePaint.measureText(name, 0, name.length).toDouble()).toInt() / 2

				if (idx >= 0 && (currentForwardName == null || messageObject.messageOwner?.fwdFrom?.fromId != null)) {
					stringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), idx, idx + name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
				}

				lastLine = stringBuilder
				lastLine = TextUtils.ellipsize(lastLine, Theme.chat_forwardNamePaint, forwardedNameWidth.toFloat(), TextUtils.TruncateAt.END)

				try {
					lastLine = Emoji.replaceEmoji(lastLine, Theme.chat_forwardNamePaint.fontMetricsInt, false)
				}
				catch (e: Exception) {
					// ignored
				}

				try {
					forwardedNameLayout[1] = StaticLayout(lastLine, Theme.chat_forwardNamePaint, forwardedNameWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

					lastLine = TextUtils.ellipsize(AndroidUtilities.replaceTags(forwardedString), Theme.chat_forwardNamePaint, forwardedNameWidth.toFloat(), TextUtils.TruncateAt.END)

					forwardedNameLayout[0] = StaticLayout(lastLine, Theme.chat_forwardNamePaint, forwardedNameWidth + AndroidUtilities.dp(2f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

					forwardedNameWidth = max(ceil(forwardedNameLayout[0]!!.getLineWidth(0).toDouble()).toInt(), ceil(forwardedNameLayout[1]!!.getLineWidth(0).toDouble()).toInt())

					if (hasPsaHint) {
						forwardedNameWidth += AndroidUtilities.dp(36f)
					}

					forwardNameOffsetX[0] = forwardedNameLayout[0]!!.getLineLeft(0)
					forwardNameOffsetX[1] = forwardedNameLayout[1]!!.getLineLeft(0)

					if (messageObject.type != 5 && !messageObject.isAnyKindOfSticker) {
						namesOffset += AndroidUtilities.dp(36f)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		if ((!isThreadChat || messageObject.replyTopMsgId != 0) && messageObject.hasValidReplyMessageObject() || messageObject.messageOwner?.fwdFrom != null && messageObject.isDice) {
			if (currentPosition == null || currentPosition!!.minY.toInt() == 0) {
				if (!messageObject.isAnyKindOfSticker && messageObject.type != 5) {
					namesOffset += AndroidUtilities.dp(42f)

					if (messageObject.type != 0) {
						namesOffset += AndroidUtilities.dp(5f)
					}
				}

				var maxWidth = maxNameWidth

				if (!messageObject.shouldDrawWithoutBackground()) {
					maxWidth -= AndroidUtilities.dp(10f)
				}
				else if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
					maxWidth += AndroidUtilities.dp(13f)
				}

				var stringFinalText: CharSequence? = null
				var name: String? = null

				if ((!isThreadChat || messageObject.replyTopMsgId != 0) && messageObject.hasValidReplyMessageObject()) {
					lastReplyMessage = messageObject.replyMessageObject?.messageOwner

					var cacheType = 1
					var size = 0
					var photoObject: TLObject?
					var photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject?.photoThumbs2, 320)
					var thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject?.photoThumbs2, 40)

					photoObject = messageObject.replyMessageObject?.photoThumbsObject2

					if (photoSize == null) {
						if (messageObject.replyMessageObject?.mediaExists == true) {
							photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject?.photoThumbs, AndroidUtilities.getPhotoSize())

							if (photoSize != null) {
								size = photoSize.size
							}

							cacheType = 0
						}
						else {
							photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject?.photoThumbs, 320)
						}

						thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.replyMessageObject?.photoThumbs, 40)
						photoObject = messageObject.replyMessageObject?.photoThumbsObject
					}

					if (thumbPhotoSize === photoSize) {
						thumbPhotoSize = null
					}

					if (photoSize == null || messageObject.replyMessageObject?.isAnyKindOfSticker == true || messageObject.isAnyKindOfSticker && !AndroidUtilities.isTablet() || messageObject.replyMessageObject?.isSecretMedia == true || messageObject.replyMessageObject?.isWebpageDocument == true) {
						replyImageReceiver.setImageBitmap(null as Drawable?)
						needReplyImage = false
					}
					else {
						if (messageObject.replyMessageObject?.isRoundVideo == true) {
							replyImageReceiver.setRoundRadius(AndroidUtilities.dp(22f))
						}
						else {
							replyImageReceiver.setRoundRadius(AndroidUtilities.dp(2f))
						}

						currentReplyPhoto = photoSize

						replyImageReceiver.setImage(ImageLocation.getForObject(photoSize, photoObject), "50_50", ImageLocation.getForObject(thumbPhotoSize, photoObject), "50_50_b", size.toLong(), null, messageObject.replyMessageObject, cacheType)

						needReplyImage = true

						maxWidth -= AndroidUtilities.dp(44f)
					}

					if (messageObject.hideSendersName) {
						if (messageObject.sendAsPeer != null) {
							if (messageObject.sendAsPeer?.channelId != 0L) {
								val chat = MessagesController.getInstance(currentAccount).getChat(messageObject.sendAsPeer?.channelId)

								if (chat != null) {
									name = chat.title
								}
							}
							else {
								val user = MessagesController.getInstance(currentAccount).getUser(messageObject.sendAsPeer?.userId)
								name = getUserName(user)
							}
						}
						else {
							name = getUserName(AccountInstance.getInstance(currentAccount).userConfig.getCurrentUser())
						}
					}
					else if (messageObject.customReplyName != null) {
						name = messageObject.customReplyName
					}
					else {
						name = messageObject.replyMessageObject?.forwardedName

						if (name == null) {
							val fromId = messageObject.replyMessageObject?.fromChatId ?: 0L

							if (fromId > 0) {
								val user = MessagesController.getInstance(currentAccount).getUser(fromId)

								if (user != null) {
									name = getUserName(user)
								}
							}
							else if (fromId < 0) {
								val chat = MessagesController.getInstance(currentAccount).getChat(-fromId)

								if (chat != null) {
									name = chat.title
								}
							}
							else {
								val chat = MessagesController.getInstance(currentAccount).getChat(messageObject.replyMessageObject?.messageOwner?.peerId?.channelId)

								if (chat != null) {
									name = chat.title
								}
							}
						}
					}

					if (name == null) {
						name = context.getString(R.string.Loading)
					}

					if (MessageObject.getMedia(messageObject.messageOwner) is TLRPC.TLMessageMediaGame) {
						stringFinalText = Emoji.replaceEmoji(MessageObject.getMedia(messageObject.messageOwner)?.game?.title, Theme.chat_replyTextPaint.fontMetricsInt, false)
						stringFinalText = TextUtils.ellipsize(stringFinalText, Theme.chat_replyTextPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)
					}
					else if (MessageObject.getMedia(messageObject.messageOwner) is TLRPC.TLMessageMediaInvoice) {
						stringFinalText = Emoji.replaceEmoji(MessageObject.getMedia(messageObject.messageOwner)!!.title, Theme.chat_replyTextPaint.fontMetricsInt, false)
						stringFinalText = TextUtils.ellipsize(stringFinalText, Theme.chat_replyTextPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)
					}
					else if (!messageObject.replyMessageObject?.caption.isNullOrEmpty()) {
						var mess = messageObject.replyMessageObject?.caption?.toString() ?: ""

						if (mess.length > 150) {
							mess = mess.substring(0, 150)
						}

						mess = mess.replace('\n', ' ')

						stringFinalText = Emoji.replaceEmoji(mess, Theme.chat_replyTextPaint.fontMetricsInt, false)

						if (messageObject.replyMessageObject?.messageOwner != null) {
							stringFinalText = MessageObject.replaceAnimatedEmoji(stringFinalText, messageObject.replyMessageObject?.messageOwner?.entities, Theme.chat_replyTextPaint.fontMetricsInt)
						}

						stringFinalText = TextUtils.ellipsize(stringFinalText, Theme.chat_replyTextPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)

						if (stringFinalText is Spannable && messageObject.replyMessageObject?.messageOwner != null) {
							MediaDataController.addTextStyleRuns(messageObject.replyMessageObject?.messageOwner?.entities, messageObject.replyMessageObject?.caption, stringFinalText)
						}
					}
					else if (!messageObject.replyMessageObject?.messageText.isNullOrEmpty()) {
						var mess = messageObject.replyMessageObject?.messageText?.toString() ?: ""

						if (mess.length > 150) {
							mess = mess.substring(0, 150)
						}

						mess = mess.replace('\n', ' ')

						stringFinalText = Emoji.replaceEmoji(mess, Theme.chat_replyTextPaint.fontMetricsInt, false)

						if (messageObject.replyMessageObject?.messageOwner != null) {
							stringFinalText = MessageObject.replaceAnimatedEmoji(stringFinalText, messageObject.replyMessageObject?.messageOwner?.entities, Theme.chat_replyTextPaint.fontMetricsInt)
						}

						stringFinalText = TextUtils.ellipsize(stringFinalText, Theme.chat_replyTextPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)

						if (stringFinalText is Spannable) {
							MediaDataController.addTextStyleRuns(messageObject.replyMessageObject, stringFinalText)
						}
					}
				}
				else {
					replyImageReceiver.setImageBitmap(null as Drawable?)
					needReplyImage = false
					replyPanelIsForward = true

					when (messageObject.messageOwner?.fwdFrom?.fromId) {
						is TLRPC.TLPeerChannel -> {
							currentForwardChannel = MessagesController.getInstance(currentAccount).getChat(messageObject.messageOwner?.fwdFrom?.fromId?.channelId)
						}

						is TLRPC.TLPeerChat -> {
							currentForwardChannel = MessagesController.getInstance(currentAccount).getChat(messageObject.messageOwner?.fwdFrom?.fromId?.chatId)
						}

						is TLRPC.TLPeerUser -> {
							currentForwardUser = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner?.fwdFrom?.fromId?.userId)
						}
					}

					if (messageObject.messageOwner?.fwdFrom?.fromName != null) {
						currentForwardName = messageObject.messageOwner?.fwdFrom?.fromName
					}

					if (currentForwardUser != null || currentForwardChannel != null || currentForwardName != null) {
						currentForwardNameString = if (currentForwardChannel != null) {
							if (currentForwardUser != null) {
								String.format("%s (%s)", currentForwardChannel!!.title, getUserName(currentForwardUser))
							}
							else {
								currentForwardChannel!!.title
							}
						}
						else if (currentForwardUser != null) {
							getUserName(currentForwardUser)
						}
						else {
							currentForwardName
						}

						name = getForwardedMessageText(messageObject)

						val from = context.getString(R.string.From)
						val fromFormattedString = context.getString(R.string.FromFormatted)
						val idx = fromFormattedString.indexOf("%1\$s")
						val fromWidth = ceil(Theme.chat_replyNamePaint.measureText("$from ").toDouble()).toInt()
						val text: CharSequence = currentForwardNameString?.replace('\n', ' ') ?: ""
						val ellipsizedText = TextUtils.ellipsize(text, Theme.chat_replyNamePaint, (maxWidth - fromWidth).toFloat(), TextUtils.TruncateAt.END)
						val stringBuilder = SpannableStringBuilder(String.format(fromFormattedString, ellipsizedText))

						if (idx >= 0 && (currentForwardName == null || messageObject.messageOwner?.fwdFrom?.fromId != null)) {
							stringBuilder.setSpan(TypefaceSpan(Theme.TYPEFACE_BOLD), idx, idx + ellipsizedText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						}

						stringFinalText = TextUtils.ellipsize(stringBuilder, Theme.chat_replyTextPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)

						forwardNameCenterX = fromWidth + ceil(Theme.chat_replyNamePaint.measureText(ellipsizedText, 0, ellipsizedText.length).toDouble()).toInt() / 2
					}
				}

				var stringFinalName = if (name == null) "" else TextUtils.ellipsize(name.replace('\n', ' '), Theme.chat_replyNamePaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)

				try {
					stringFinalName = Emoji.replaceEmoji(stringFinalName, Theme.chat_replyNamePaint.fontMetricsInt, false)
				}
				catch (e: Exception) {
					// ignored
				}

				try {
					replyNameWidth = AndroidUtilities.dp((4 + if (needReplyImage) 44 else 0).toFloat())

					if (stringFinalName != null) {
						replyNameLayout = StaticLayout(stringFinalName, Theme.chat_replyNamePaint, maxWidth + AndroidUtilities.dp(6f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

						if (replyNameLayout!!.lineCount > 0) {
							replyNameWidth += ceil(replyNameLayout!!.getLineWidth(0).toDouble()).toInt() + AndroidUtilities.dp(8f)
							replyNameOffset = replyNameLayout!!.getLineLeft(0).toInt()
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				try {
					replyTextWidth = AndroidUtilities.dp((4 + if (needReplyImage) 44 else 0).toFloat())

					if (stringFinalText != null) {
						val sb = SpannableStringBuilder(stringFinalText)
						var changed = false

						for (span in sb.getSpans(0, sb.length, TextStyleSpan::class.java)) {
							if (span.style.styleFlags and TextStyleSpan.FLAG_STYLE_MONO != 0) {
								changed = true
								sb.removeSpan(span)
							}
						}

						stringFinalText = if (changed) {
							TextUtils.ellipsize(sb, Theme.chat_replyTextPaint, maxWidth.toFloat(), TextUtils.TruncateAt.END)
						}
						else {
							sb
						}

						replyTextLayout = StaticLayout(stringFinalText, Theme.chat_replyTextPaint, maxWidth + AndroidUtilities.dp(10f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

						if (replyTextLayout!!.lineCount > 0) {
							replyTextWidth += ceil(replyTextLayout!!.getLineWidth(0).toDouble()).toInt() + AndroidUtilities.dp(8f)
							replyTextOffset = replyTextLayout!!.getLineLeft(0).toInt()
						}

						replySpoilers.clear()

						if (getMessageObject()!!.replyMessageObject != null && !getMessageObject()!!.replyMessageObject!!.isSpoilersRevealed) {
							SpoilerEffect.addSpoilers(this, replyTextLayout, replySpoilersPool, replySpoilers)
						}

						animatedEmojiReplyStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, false, animatedEmojiReplyStack, replyTextLayout)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
		else if (!isThreadChat && messageObject.replyMsgId != 0) {
			if (messageObject.replyMessageObject?.messageOwner !is TLRPC.TLMessageEmpty) {
				if (!messageObject.isAnyKindOfSticker && messageObject.type != 5) {
					namesOffset += AndroidUtilities.dp(42f)

					if (messageObject.type != 0) {
						namesOffset += AndroidUtilities.dp(5f)
					}
				}

				needReplyImage = false

				var maxWidth = maxNameWidth

				if (!messageObject.shouldDrawWithoutBackground()) {
					maxWidth -= AndroidUtilities.dp(10f)
				}
				else if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
					maxWidth += AndroidUtilities.dp(13f)
				}

				replyNameLayout = StaticLayout(context.getString(R.string.Loading), Theme.chat_replyNamePaint, maxWidth + AndroidUtilities.dp(6f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

				if (replyNameLayout!!.lineCount > 0) {
					replyNameWidth += ceil(replyNameLayout!!.getLineWidth(0).toDouble()).toInt() + AndroidUtilities.dp(8f)
					replyNameOffset = replyNameLayout!!.getLineLeft(0).toInt()
				}
			}
		}

		requestLayout()
	}

	private val isNeedAuthorName: Boolean
		get() = isPinnedChat && currentMessageObject?.type == MessageObject.TYPE_COMMON || (!isPinnedTop || ChatObject.isChannel(currentChat) && currentChat?.megagroup == false) && drawName && isChat && (currentMessageObject?.isOutOwner == false || currentMessageObject?.isSupergroup == true && currentMessageObject?.isFromGroup == true) || currentMessageObject?.isImportedForward == true && currentMessageObject?.messageOwner?.fwdFrom?.fromId == null

	private val authorName: String
		get() {
			if (currentUser != null) {
				return getUserName(currentUser)
			}
			else if (currentChat != null) {
				return currentChat?.title ?: ""
			}
			else if (currentMessageObject != null && currentMessageObject!!.isSponsored) {
				return currentMessageObject?.sponsoredChatInvite?.title ?: currentMessageObject?.sponsoredChatInvite?.chat?.title ?: ""
			}

			return "DELETED"
		}

	//		if (currentUser != null) {
//			if (currentUser.emoji_status instanceof TLRPC.TLEmojiStatusUntil && ((TLRPC.TLEmojiStatusUntil)currentUser.emoji_status).until > (int)(System.currentTimeMillis() / 1000)) {
//				return ((TLRPC.TLEmojiStatusUntil)currentUser.emoji_status).document_id;
//			}
//			else if (currentUser.emoji_status instanceof TLRPC.TLEmojiStatus) {
//				return ((TLRPC.TLEmojiStatus)currentUser.emoji_status).document_id;
//			}
//			else if (currentUser.premium) {
//				return ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_premium_liststar).mutate();
//			}
//		}

	val authorStatus: Any?
		get() =//		if (currentUser != null) {
//			if (currentUser.emoji_status instanceof TLRPC.TLEmojiStatusUntil && ((TLRPC.TLEmojiStatusUntil)currentUser.emoji_status).until > (int)(System.currentTimeMillis() / 1000)) {
//				return ((TLRPC.TLEmojiStatusUntil)currentUser.emoji_status).document_id;
//			}
//			else if (currentUser.emoji_status instanceof TLRPC.TLEmojiStatus) {
//				return ((TLRPC.TLEmojiStatus)currentUser.emoji_status).document_id;
//			}
//			else if (currentUser.premium) {
//				return ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_premium_liststar).mutate();
//			}
//		}
			null

	private fun getForwardedMessageText(messageObject: MessageObject?): String {
		return if (hasPsaHint) {
			var forwardedString = LocaleController.getString("PsaMessage_" + messageObject?.messageOwner?.fwdFrom?.psaType)

			if (forwardedString == null) {
				forwardedString = context.getString(R.string.PsaMessageDefault)
			}

			forwardedString
		}
		else {
			context.getString(R.string.ForwardedMessage)
		}
	}

	fun getExtraInsetHeight(): Int {
		var h = addedCaptionHeight

		if (drawCommentButton) {
			h += AndroidUtilities.dp(if (shouldDrawTimeOnMedia()) 41.3f else 43f)
		}

		if (!reactionsLayoutInBubble.isEmpty && currentMessageObject!!.shouldDrawReactionsInLayout()) {
			h += reactionsLayoutInBubble.totalHeight
		}

		return h
	}

	fun getAvatarImage(): ImageReceiver? {
		return if (isAvatarVisible) avatarImage else null
	}

	fun getCheckBoxTranslation(): Float {
		return checkBoxTranslation.toFloat()
	}

	fun shouldDrawAlphaLayer(): Boolean {
		return (currentMessagesGroup == null || !currentMessagesGroup!!.transitionParams.backgroundChangeBounds) && alpha != 1f
	}

	fun isDrawPinnedBottom(): Boolean {
		val forceMediaByGroup = currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0 && currentMessagesGroup!!.isDocuments
		return mediaBackground || drawPinnedBottom || forceMediaByGroup
	}

	fun drawCheckBox(canvas: Canvas) {
		if (currentMessageObject != null && !currentMessageObject!!.isSending && !currentMessageObject!!.isSendError && checkBox != null && (checkBoxVisible || checkBoxAnimationInProgress) && (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0 && currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0)) {
			canvas.withSave {
				var y = y

				if (currentMessagesGroup != null && currentMessagesGroup!!.messages.size > 1) {
					y = top + currentMessagesGroup!!.transitionParams.offsetTop - translationY
				}
				else {
					y += transitionParams.deltaTop
				}

				translate(0f, y + transitionYOffsetForDrawables)

				checkBox?.draw(this)
			}
		}
	}

	fun setBackgroundTopY(fromParent: Boolean) {
		for (a in 0..1) {
			if (a == 1 && !fromParent) {
				return
			}

			val drawable = (if (a == 0) currentBackgroundDrawable else currentBackgroundSelectedDrawable) ?: continue
			var w = parentWidth
			var h = parentHeight

			if (h == 0) {
				w = getParentWidth()
				h = AndroidUtilities.displaySize.y

				(parent as? View)?.let { view ->
					w = view.measuredWidth
					h = view.measuredHeight
				}
			}

			drawable.setTop(((if (fromParent) y else top).toFloat() + parentViewTopOffset).toInt(), w, h, parentViewTopOffset.toInt(), blurredViewTopOffset, isPinnedTop, isPinnedBottom || transitionParams.changePinnedBottomProgress != 1f)
		}
	}

	fun setBackgroundTopY(offset: Int) {
		val drawable = currentBackgroundDrawable
		var w = parentWidth
		var h = parentHeight

		if (h == 0) {
			w = getParentWidth()
			h = AndroidUtilities.displaySize.y

			(parent as? View)?.let { view ->
				w = view.measuredWidth
				h = view.measuredHeight
			}
		}

		drawable?.setTop((parentViewTopOffset + offset).toInt(), w, h, parentViewTopOffset.toInt(), blurredViewTopOffset, isPinnedTop, isPinnedBottom || transitionParams.changePinnedBottomProgress != 1f)
	}

	fun setDrawableBoundsInner(drawable: Drawable?, x: Int, y: Int, w: Int, h: Int) {
		if (drawable != null) {
			transitionYOffsetForDrawables = y + h + transitionParams.deltaBottom - (y + h + transitionParams.deltaBottom).toInt()
			drawable.setBounds((x + transitionParams.deltaLeft).toInt(), (y + transitionParams.deltaTop).toInt(), (x + w + transitionParams.deltaRight).toInt(), (y + h + transitionParams.deltaBottom).toInt())
		}
	}

	@SuppressLint("WrongCall")
	override fun onDraw(canvas: Canvas) {
		val currentMessageObject = currentMessageObject ?: return

		if (!wasLayout) {
			onLayout(false, left, top, right, bottom)
		}

		if (enterTransitionInProgress && currentMessageObject.isAnimatedEmojiStickers) {
			return
		}

		if (currentMessageObject.isOutOwner) {
			val textOutColor = ResourcesCompat.getColor(resources, R.color.white, null)
			val linkOutColor = ResourcesCompat.getColor(resources, R.color.white, null)

			Theme.chat_msgTextPaint.color = textOutColor //getThemedColor(Theme.key_chat_messageTextOut));
			Theme.chat_msgGameTextPaint.color = textOutColor //getThemedColor(Theme.key_chat_messageTextOut));
			Theme.chat_msgGameTextPaint.linkColor = linkOutColor // getThemedColor(Theme.key_chat_messageLinkOut);
			Theme.chat_replyTextPaint.linkColor = linkOutColor // getThemedColor(Theme.key_chat_messageLinkOut);
			Theme.chat_msgTextPaint.linkColor = linkOutColor // getThemedColor(Theme.key_chat_messageLinkOut);
		}
		else {
			val textInColor = ResourcesCompat.getColor(resources, R.color.text, null)
			val linkInColor = ResourcesCompat.getColor(resources, R.color.brand, null)

			Theme.chat_msgTextPaint.color = textInColor // getThemedColor(Theme.key_chat_messageTextIn));
			Theme.chat_msgGameTextPaint.color = textInColor // getThemedColor(Theme.key_chat_messageTextIn));
			Theme.chat_msgGameTextPaint.linkColor = linkInColor // getThemedColor(Theme.key_chat_messageLinkIn);
			Theme.chat_replyTextPaint.linkColor = linkInColor // getThemedColor(Theme.key_chat_messageLinkIn);
			Theme.chat_msgTextPaint.linkColor = linkInColor // getThemedColor(Theme.key_chat_messageLinkIn);
		}

		if (documentAttach != null) {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
				if (currentMessageObject.isOutOwner) {
					var fillColor = ResourcesCompat.getColor(resources, R.color.white, null)
					fillColor = ColorUtils.setAlphaComponent(fillColor, 127)

					seekBarWaveform.setColors(fillColor, ResourcesCompat.getColor(resources, R.color.white, null), ResourcesCompat.getColor(resources, R.color.white, null))

					seekBar.setColors(
							background = fillColor,
							cache = fillColor,
							progress = ResourcesCompat.getColor(resources, R.color.white, null),
							circle = ResourcesCompat.getColor(resources, R.color.white, null),
							selected = ResourcesCompat.getColor(resources, R.color.white, null),
					)
				}
				else {
					var fillColor = ResourcesCompat.getColor(resources, R.color.brand, null)
					fillColor = ColorUtils.setAlphaComponent(fillColor, 127)

					seekBarWaveform.setColors(fillColor, ResourcesCompat.getColor(resources, R.color.brand, null), ResourcesCompat.getColor(resources, R.color.brand, null))

					seekBar.setColors(
							background = ResourcesCompat.getColor(resources, R.color.avatar_tint, null),
							cache = ResourcesCompat.getColor(resources, R.color.avatar_tint, null),
							progress = ResourcesCompat.getColor(resources, R.color.brand, null),
							circle = ResourcesCompat.getColor(resources, R.color.brand, null),
							selected = ResourcesCompat.getColor(resources, R.color.brand, null),
					)
				}
			}
			else if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
				if (currentMessageObject.isOutOwner) {
					var fillColor = ResourcesCompat.getColor(resources, R.color.white, null)
					fillColor = ColorUtils.setAlphaComponent(fillColor, 127)

					seekBar.setColors(
							background = fillColor,
							cache = fillColor,
							progress = ResourcesCompat.getColor(resources, R.color.white, null),
							circle = ResourcesCompat.getColor(resources, R.color.white, null),
							selected = ResourcesCompat.getColor(resources, R.color.white, null),
					)
				}
				else {
					seekBar.setColors(
							background = ResourcesCompat.getColor(resources, R.color.avatar_tint, null),
							cache = ResourcesCompat.getColor(resources, R.color.avatar_tint, null),
							progress = ResourcesCompat.getColor(resources, R.color.brand, null),
							circle = ResourcesCompat.getColor(resources, R.color.brand, null),
							selected = ResourcesCompat.getColor(resources, R.color.brand, null),
					)
				}
			}
		}

		if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
			Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
		}
		else {
			if (mediaBackground) {
				if (currentMessageObject.shouldDrawWithoutBackground()) {
					Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
				}
				else {
					Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
				}
			}
			else {
				if (currentMessageObject.isOutOwner) {
					Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.light_gray_fixed, null)
				}
				else {
					Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
				}
			}
		}

		drawBackgroundInternal(canvas, false)

		if (isHighlightedAnimated) {
			val newTime = System.currentTimeMillis()
			var dt = abs(newTime - lastHighlightProgressTime)

			if (dt > 17) {
				dt = 17
			}

			highlightProgress -= dt.toInt()
			lastHighlightProgressTime = newTime

			if (highlightProgress <= 0) {
				highlightProgress = 0
				isHighlightedAnimated = false
			}

			invalidate()

			(parent as? View)?.invalidate()
		}

		var restore = Int.MIN_VALUE

		if (alphaInternal != 1.0f) {
			var top = 0
			var left = 0
			var bottom = measuredHeight - getAdditionalHeight()
			var right = measuredWidth

			if (currentBackgroundDrawable != null) {
				top = currentBackgroundDrawable!!.bounds.top
				bottom = currentBackgroundDrawable!!.bounds.bottom
				left = currentBackgroundDrawable!!.bounds.left
				right = currentBackgroundDrawable!!.bounds.right
			}

			if (drawSideButton != 0) {
				if (currentMessageObject.isOutOwner) {
					left -= AndroidUtilities.dp((8 + 32).toFloat())
				}
				else {
					right += AndroidUtilities.dp((8 + 32).toFloat())
				}
			}

			if (y < 0) {
				top = -y.toInt()
			}

			if (y + measuredHeight - getAdditionalHeight() > parentHeight) {
				bottom = (parentHeight - y).toInt()
			}

			rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

			restore = canvas.saveLayerAlpha(rect, (255 * alphaInternal).toInt())
		}

		var clipContent = false

		if (transitionParams.animateBackgroundBoundsInner && currentBackgroundDrawable != null && !isRoundVideo) {
			val r = currentBackgroundDrawable!!.bounds
			canvas.save()
			canvas.clipRect(r.left + AndroidUtilities.dp(4f), r.top + AndroidUtilities.dp(4f), r.right - AndroidUtilities.dp(4f), r.bottom - AndroidUtilities.dp(4f))
			clipContent = true
		}

		drawContent(canvas)

		if (clipContent) {
			canvas.restore()
		}

		if (delegate == null || delegate!!.canDrawOutboundsContent() || transitionParams.messageEntering || alpha != 1f) {
			drawOutboundContent(canvas)
		}

		if (replyNameLayout != null) {
			if (currentMessageObject.shouldDrawWithoutBackground()) {
				if (currentMessageObject.isOutOwner) {
					replyStartX = AndroidUtilities.dp(23f)

					if (isPlayingRound) {
						replyStartX -= AndroidUtilities.roundPlayingMessageSize - AndroidUtilities.roundMessageSize
					}
				}
				else if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
					replyStartX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(4f)
				}
				else {
					replyStartX = backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(17f)
				}

				replyStartY = if (drawForwardedName) {
					forwardNameY + AndroidUtilities.dp(38f)
				}
				else {
					AndroidUtilities.dp(12f)
				}
			}
			else {
				replyStartX = if (currentMessageObject.isOutOwner) {
					backgroundDrawableLeft + AndroidUtilities.dp(12f) + extraTextX
				}
				else {
					if (mediaBackground) {
						backgroundDrawableLeft + AndroidUtilities.dp(12f) + extraTextX
					}
					else {
						backgroundDrawableLeft + AndroidUtilities.dp(if (drawPinnedBottom) 12f else 18f) + extraTextX
					}
				}

				replyStartY = AndroidUtilities.dp((12 + (if (drawForwardedName && forwardedNameLayout[0] != null) 36 else 0) + if (drawNameLayout && nameLayout != null) 20 else 0).toFloat())
			}
		}

		if (currentPosition == null && !transitionParams.animateBackgroundBoundsInner && !(enterTransitionInProgress && !currentMessageObject.isVoice)) {
			drawNamesLayout(canvas, 1f)
		}

		if ((!autoPlayingMedia || !MediaController.getInstance().isPlayingMessageAndReadyToDraw(currentMessageObject) || isRoundVideo) && !transitionParams.animateBackgroundBoundsInner) {
			drawOverlays(canvas)
		}

		if ((drawTime || !mediaBackground) && !forceNotDrawTime && !transitionParams.animateBackgroundBoundsInner && !(enterTransitionInProgress && !currentMessageObject.isVoice)) {
			drawTime(canvas, 1f, false)
		}

		if ((controlsAlpha != 1.0f || timeAlpha != 1.0f) && currentMessageObject.type != 5) {
			val newTime = System.currentTimeMillis()
			var dt = abs(lastControlsAlphaChangeTime - newTime)

			if (dt > 17) {
				dt = 17
			}

			totalChangeTime += dt

			if (totalChangeTime > TIME_APPEAR_MS) {
				totalChangeTime = TIME_APPEAR_MS.toLong()
			}

			lastControlsAlphaChangeTime = newTime

			if (controlsAlpha != 1.0f) {
				controlsAlpha = AndroidUtilities.decelerateInterpolator.getInterpolation(totalChangeTime / TIME_APPEAR_MS.toFloat())
			}

			if (timeAlpha != 1.0f) {
				timeAlpha = AndroidUtilities.decelerateInterpolator.getInterpolation(totalChangeTime / TIME_APPEAR_MS.toFloat())
			}

			invalidate()

			if (forceNotDrawTime && currentPosition != null && currentPosition!!.last && parent != null) {
				val parent = parent as View
				parent.invalidate()
			}
		}

		if (drawBackground && shouldDrawSelectionOverlay() && currentMessagesGroup == null) {
			if (selectionOverlayPaint == null) {
				selectionOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			}

			selectionOverlayPaint!!.color = selectionOverlayColor!!

			val wasAlpha = selectionOverlayPaint!!.alpha

			selectionOverlayPaint!!.alpha = (wasAlpha * getHighlightAlpha() * alpha).toInt()

			if (selectionOverlayPaint!!.alpha > 0) {
				canvas.withClip(0, 0, measuredWidth, measuredHeight - getAdditionalHeight()) {
					currentBackgroundDrawable?.drawCached(this, backgroundCacheParams, selectionOverlayPaint)
				}
			}

			selectionOverlayPaint!!.alpha = wasAlpha
		}

		if (restore != Int.MIN_VALUE) {
			canvas.restoreToCount(restore)
		}

		updateSelectionTextPosition()
	}

	private fun shouldDrawTransparentBackground(): Boolean {
		// MARK: transparent background is detected here
		val currentMessagesGroup = currentMessagesGroup ?: return false
		val messages = currentMessagesGroup.messages

		if (messages.count { it.type == MessageObject.TYPE_ROUND_VIDEO } == messages.size) {
			return true
		}

		return messages.count { it.type == MessageObject.TYPE_PHOTO || it.type == MessageObject.TYPE_VIDEO } == messages.size && messages.find { !it.caption.isNullOrEmpty() } == null && messages.find { it.isForwarded } == null
	}

	@SuppressLint("WrongCall")
	fun drawBackgroundInternal(canvas: Canvas, fromParent: Boolean) {
		val currentMessageObject = currentMessageObject ?: return

		if (!wasLayout && !animationRunning) {
			forceLayout()
			return
		}

		if (!wasLayout) {
			onLayout(false, left, top, right, bottom)
		}

		var currentBackgroundShadowDrawable: Drawable?
		var additionalTop = 0
		var additionalBottom = 0
		val forceMediaByGroup = currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0 && currentMessagesGroup!!.isDocuments && !drawPinnedBottom

		if (currentMessageObject.isOutOwner) {
			if (transitionParams.changePinnedBottomProgress >= 1 && !mediaBackground && !drawPinnedBottom && !forceMediaByGroup) {
				currentBackgroundDrawable = getThemedDrawable(Theme.key_drawable_msgOut) as MessageDrawable
				currentBackgroundSelectedDrawable = getThemedDrawable(Theme.key_drawable_msgOutSelected) as MessageDrawable
				transitionParams.drawPinnedBottomBackground = false
			}
			else {
				currentBackgroundDrawable = getThemedDrawable(Theme.key_drawable_msgOutMedia) as MessageDrawable
				currentBackgroundSelectedDrawable = getThemedDrawable(Theme.key_drawable_msgOutMediaSelected) as MessageDrawable
				transitionParams.drawPinnedBottomBackground = true

				if (shouldDrawTransparentBackground()) {
					currentBackgroundDrawable?.setBackgroundColorId(android.R.color.transparent)
					currentBackgroundSelectedDrawable?.setBackgroundColorId(android.R.color.transparent)
				}
				else {
					currentBackgroundDrawable?.setBackgroundColorId(0)
					currentBackgroundSelectedDrawable?.setBackgroundColorId(0)
				}
			}

			setBackgroundTopY(true)

			currentBackgroundShadowDrawable = if (isDrawSelectionBackground() && (currentPosition == null || background != null)) {
				currentBackgroundSelectedDrawable?.getShadowDrawable()
			}
			else {
				currentBackgroundDrawable?.getShadowDrawable()
			}

			backgroundDrawableLeft = layoutWidth - backgroundWidth - if (!mediaBackground) 0 else AndroidUtilities.dp(9f)
			backgroundDrawableRight = backgroundWidth - if (mediaBackground) 0 else AndroidUtilities.dp(3f)

			if (currentMessagesGroup != null && !currentMessagesGroup!!.isDocuments) {
				if (!currentPosition!!.edge) {
					backgroundDrawableRight += AndroidUtilities.dp(10f)
				}
			}

			var backgroundLeft = backgroundDrawableLeft

			if (!forceMediaByGroup && transitionParams.changePinnedBottomProgress != 1f) {
				if (!mediaBackground) {
					backgroundDrawableRight -= AndroidUtilities.dp(6f)
				}
			}
			else if (!mediaBackground && drawPinnedBottom) {
				backgroundDrawableRight -= AndroidUtilities.dp(6f)
			}

			if (currentPosition != null) {
				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT == 0) {
					backgroundDrawableRight += AndroidUtilities.dp((SharedConfig.bubbleRadius + 2).toFloat())
				}

				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
					backgroundLeft -= AndroidUtilities.dp((SharedConfig.bubbleRadius + 2).toFloat())
					backgroundDrawableRight += AndroidUtilities.dp((SharedConfig.bubbleRadius + 2).toFloat())
				}

				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP == 0) {
					additionalTop -= AndroidUtilities.dp((SharedConfig.bubbleRadius + 3).toFloat())
					additionalBottom += AndroidUtilities.dp((SharedConfig.bubbleRadius + 3).toFloat())
				}

				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0) {
					additionalBottom += AndroidUtilities.dp((SharedConfig.bubbleRadius + 3).toFloat())
				}
			}

			val offsetBottom = if (drawPinnedBottom && drawPinnedTop) {
				0
			}
			else if (drawPinnedBottom) {
				AndroidUtilities.dp(1f)
			}
			else {
				AndroidUtilities.dp(2f)
			}

			backgroundDrawableTop = (additionalTop + if (drawPinnedTop) 0 else AndroidUtilities.dp(1f))

			val backgroundHeight = layoutHeight - offsetBottom + additionalBottom

			backgroundDrawableBottom = backgroundDrawableTop + backgroundHeight

			if (forceMediaByGroup) {
				setDrawableBoundsInner(currentBackgroundDrawable, backgroundLeft, backgroundDrawableTop - additionalTop, backgroundDrawableRight, backgroundHeight - additionalBottom + 10)
				setDrawableBoundsInner(currentBackgroundSelectedDrawable, backgroundDrawableLeft, backgroundDrawableTop, backgroundDrawableRight - AndroidUtilities.dp(6f), backgroundHeight)
			}
			else {
				setDrawableBoundsInner(currentBackgroundDrawable, backgroundLeft - 1, backgroundDrawableTop, backgroundDrawableRight, backgroundHeight + 1)
				setDrawableBoundsInner(currentBackgroundSelectedDrawable, backgroundLeft, backgroundDrawableTop, backgroundDrawableRight, backgroundHeight)
			}

			setDrawableBoundsInner(currentBackgroundShadowDrawable, backgroundLeft, backgroundDrawableTop, backgroundDrawableRight, backgroundHeight)
		}
		else {
			if (transitionParams.changePinnedBottomProgress >= 1 && !mediaBackground && !drawPinnedBottom && !forceMediaByGroup) {
				currentBackgroundDrawable = getThemedDrawable(Theme.key_drawable_msgIn) as MessageDrawable
				currentBackgroundSelectedDrawable = getThemedDrawable(Theme.key_drawable_msgInSelected) as MessageDrawable
				transitionParams.drawPinnedBottomBackground = false
			}
			else {
				currentBackgroundDrawable = getThemedDrawable(Theme.key_drawable_msgInMedia) as MessageDrawable
				currentBackgroundSelectedDrawable = getThemedDrawable(Theme.key_drawable_msgInMediaSelected) as MessageDrawable
				transitionParams.drawPinnedBottomBackground = true

				if (shouldDrawTransparentBackground()) {
					currentBackgroundDrawable?.setBackgroundColorId(android.R.color.transparent)
					currentBackgroundSelectedDrawable?.setBackgroundColorId(android.R.color.transparent)
				}
				else {
					currentBackgroundDrawable?.setBackgroundColorId(0)
					currentBackgroundSelectedDrawable?.setBackgroundColorId(0)
				}
			}

			setBackgroundTopY(true)

			currentBackgroundShadowDrawable = if (isDrawSelectionBackground() && (currentPosition == null || background != null)) {
				currentBackgroundSelectedDrawable?.getShadowDrawable()
			}
			else {
				currentBackgroundDrawable?.getShadowDrawable()
			}

			backgroundDrawableLeft = AndroidUtilities.dp(((if (isChat && isAvatarVisible) 48 else 0) + if (!mediaBackground) 3 else 9).toFloat())
			backgroundDrawableRight = backgroundWidth - if (mediaBackground) 0 else AndroidUtilities.dp(3f)

			if (currentMessagesGroup != null && !currentMessagesGroup!!.isDocuments) {
				if (!currentPosition!!.edge) {
					backgroundDrawableLeft -= AndroidUtilities.dp(10f)
					backgroundDrawableRight += AndroidUtilities.dp(10f)
				}

				if (currentPosition!!.leftSpanOffset != 0) {
					backgroundDrawableLeft += ceil((currentPosition!!.leftSpanOffset / 1000.0f * groupPhotosWidth).toDouble()).toInt()
				}
			}

			if (!mediaBackground && drawPinnedBottom || !forceMediaByGroup && transitionParams.changePinnedBottomProgress != 1f) {
				if (!(!drawPinnedBottom && mediaBackground)) {
					backgroundDrawableRight -= AndroidUtilities.dp(6f)
				}

				if (!mediaBackground) {
					backgroundDrawableLeft += AndroidUtilities.dp(6f)
				}
			}

			if (currentPosition != null) {
				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT == 0) {
					backgroundDrawableRight += AndroidUtilities.dp((SharedConfig.bubbleRadius + 2).toFloat())
				}

				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
					backgroundDrawableLeft -= AndroidUtilities.dp((SharedConfig.bubbleRadius + 2).toFloat())
					backgroundDrawableRight += AndroidUtilities.dp((SharedConfig.bubbleRadius + 2).toFloat())
				}

				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP == 0) {
					additionalTop -= AndroidUtilities.dp((SharedConfig.bubbleRadius + 3).toFloat())
					additionalBottom += AndroidUtilities.dp((SharedConfig.bubbleRadius + 3).toFloat())
				}

				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0) {
					additionalBottom += AndroidUtilities.dp((SharedConfig.bubbleRadius + 4).toFloat())
				}
			}

			val offsetBottom = if (drawPinnedBottom && drawPinnedTop) {
				0
			}
			else if (drawPinnedBottom) {
				AndroidUtilities.dp(1f)
			}
			else {
				AndroidUtilities.dp(2f)
			}

			backgroundDrawableTop = additionalTop + if (drawPinnedTop) 0 else AndroidUtilities.dp(1f)

			val backgroundHeight = layoutHeight - offsetBottom + additionalBottom

			backgroundDrawableBottom = backgroundDrawableTop + backgroundHeight

			setDrawableBoundsInner(currentBackgroundDrawable, backgroundDrawableLeft, backgroundDrawableTop, backgroundDrawableRight, backgroundHeight)

			if (forceMediaByGroup) {
				setDrawableBoundsInner(currentBackgroundSelectedDrawable, backgroundDrawableLeft + AndroidUtilities.dp(6f), backgroundDrawableTop, backgroundDrawableRight - AndroidUtilities.dp(6f), backgroundHeight)
			}
			else {
				setDrawableBoundsInner(currentBackgroundSelectedDrawable, backgroundDrawableLeft, backgroundDrawableTop, backgroundDrawableRight, backgroundHeight)
			}

			setDrawableBoundsInner(currentBackgroundShadowDrawable, backgroundDrawableLeft, backgroundDrawableTop, backgroundDrawableRight, backgroundHeight)
		}

		if (!currentMessageObject.isOutOwner && transitionParams.changePinnedBottomProgress != 1f && !mediaBackground && !drawPinnedBottom) {
			backgroundDrawableLeft -= AndroidUtilities.dp(6f)
			backgroundDrawableRight += AndroidUtilities.dp(6f)
		}

		if (hasPsaHint) {
			var x: Int

			if (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT != 0) {
				x = currentBackgroundDrawable!!.bounds.right
			}
			else {
				x = 0
				val dWidth = groupPhotosWidth

				for (a in currentMessagesGroup!!.posArray.indices) {
					val position = currentMessagesGroup!!.posArray[a]

					x += if (position.minY.toInt() == 0) {
						ceil(((position.pw + position.leftSpanOffset) / 1000.0f * dWidth).toDouble()).toInt()
					}
					else {
						break
					}
				}
			}

			val drawable = Theme.chat_psaHelpDrawable[if (currentMessageObject.isOutOwner) 1 else 0]

			val y = if (currentMessageObject.type == MessageObject.TYPE_ROUND_VIDEO) {
				AndroidUtilities.dp(12f)
			}
			else {
				AndroidUtilities.dp((10 + if (drawNameLayout) 19 else 0).toFloat())
			}

			psaHelpX = x - drawable.intrinsicWidth - AndroidUtilities.dp(if (currentMessageObject.isOutOwner) 20f else 14f)
			psaHelpY = y + AndroidUtilities.dp(4f)
		}

		if (checkBoxVisible || checkBoxAnimationInProgress) {
			if (checkBoxVisible && checkBoxAnimationProgress == 1.0f || !checkBoxVisible && checkBoxAnimationProgress == 0.0f) {
				checkBoxAnimationInProgress = false
			}

			val interpolator: Interpolator = if (checkBoxVisible) CubicBezierInterpolator.EASE_OUT else CubicBezierInterpolator.EASE_IN

			checkBoxTranslation = ceil((interpolator.getInterpolation(checkBoxAnimationProgress) * AndroidUtilities.dp(35f)).toDouble()).toInt()

			if (!currentMessageObject.isOutOwner) {
				updateTranslation()
			}

			val size = AndroidUtilities.dp(21f)

			checkBox?.setBounds(AndroidUtilities.dp((8 - 35).toFloat()) + checkBoxTranslation, currentBackgroundDrawable!!.bounds.bottom - AndroidUtilities.dp(8f) - size, size, size)

			if (checkBoxAnimationInProgress) {
				val newTime = SystemClock.elapsedRealtime()
				val dt = newTime - lastCheckBoxAnimationTime

				lastCheckBoxAnimationTime = newTime

				if (checkBoxVisible) {
					checkBoxAnimationProgress += dt / 200.0f

					if (checkBoxAnimationProgress > 1.0f) {
						checkBoxAnimationProgress = 1.0f
					}
				}
				else {
					checkBoxAnimationProgress -= dt / 200.0f

					if (checkBoxAnimationProgress <= 0.0f) {
						checkBoxAnimationProgress = 0.0f
					}
				}

				invalidate()

				(parent as? View)?.invalidate()
			}
		}

		if (!fromParent && drawBackgroundInParent()) {
			return
		}

		var needRestore = false

		if (transitionYOffsetForDrawables != 0f) {
			needRestore = true
			canvas.save()
			canvas.translate(0f, transitionYOffsetForDrawables)
		}

		if (drawBackground && currentBackgroundDrawable != null && (currentPosition == null || isDrawSelectionBackground() && (currentMessageObject.isMusic || currentMessageObject.isDocument())) && !(enterTransitionInProgress && !currentMessageObject.isVoice)) {
			var alphaInternal = alphaInternal

			if (fromParent) {
				alphaInternal *= alpha
			}

			if (hasSelectionOverlay()) {
//                if ((isPressed() && isCheckPressed || !isCheckPressed && isPressed) && !textIsSelectionMode()) {
//                    currentSelectedBackgroundAlpha = 1f;
//                    currentBackgroundSelectedDrawable.setAlpha((int) (255 * alphaInternal));
//                    currentBackgroundSelectedDrawable.drawCached(canvas, backgroundCacheParams);
//                } else {
				currentSelectedBackgroundAlpha = 0f
				currentBackgroundDrawable?.alpha = (255 * alphaInternal).toInt()
				currentBackgroundDrawable?.drawCached(canvas, backgroundCacheParams)
				//                }

				if (currentBackgroundShadowDrawable != null && currentPosition == null) {
					currentBackgroundShadowDrawable.alpha = (255 * alphaInternal).toInt()
					currentBackgroundShadowDrawable.draw(canvas)
				}
			}
			else {
				if (isHighlightedAnimated) {
					currentBackgroundDrawable?.alpha = (255 * alphaInternal).toInt()
					currentBackgroundDrawable?.drawCached(canvas, backgroundCacheParams)
					currentSelectedBackgroundAlpha = getHighlightAlpha()

					if (currentPosition == null) {
						currentBackgroundSelectedDrawable?.alpha = (alphaInternal * currentSelectedBackgroundAlpha * 255).toInt()
						currentBackgroundSelectedDrawable?.drawCached(canvas, backgroundCacheParams)
					}
				}
				else if (selectedBackgroundProgress != 0f && !(currentMessagesGroup != null && currentMessagesGroup!!.isDocuments)) {
					currentBackgroundDrawable?.alpha = (255 * alphaInternal).toInt()
					currentBackgroundDrawable?.drawCached(canvas, backgroundCacheParams)
					currentSelectedBackgroundAlpha = selectedBackgroundProgress
					currentBackgroundSelectedDrawable?.alpha = (currentSelectedBackgroundAlpha * alphaInternal * 255).toInt()
					currentBackgroundSelectedDrawable?.drawCached(canvas, backgroundCacheParams)
					currentBackgroundShadowDrawable = null
				}
				else {
					if (isDrawSelectionBackground() && (currentPosition == null || currentMessageObject.isMusic || currentMessageObject.isDocument() || background != null)) {
						if (currentPosition != null) {
							canvas.save()
							canvas.clipRect(0, 0, measuredWidth, measuredHeight - getAdditionalHeight())
						}

						currentSelectedBackgroundAlpha = 1f
						currentBackgroundSelectedDrawable?.alpha = (255 * alphaInternal).toInt()
						currentBackgroundSelectedDrawable?.drawCached(canvas, backgroundCacheParams)

						if (currentPosition != null) {
							canvas.restore()
						}
					}
					else {
						currentSelectedBackgroundAlpha = 0f
						currentBackgroundDrawable?.alpha = (255 * alphaInternal).toInt()
						currentBackgroundDrawable?.drawCached(canvas, backgroundCacheParams)
					}
				}

				if (currentBackgroundShadowDrawable != null && currentPosition == null) {
					currentBackgroundShadowDrawable.alpha = (255 * alphaInternal).toInt()
					currentBackgroundShadowDrawable.draw(canvas)
				}

				if (transitionParams.changePinnedBottomProgress != 1f && currentPosition == null) {
					if (currentMessageObject.isOutOwner) {
						val drawable = getThemedDrawable(Theme.key_drawable_msgOut) as MessageDrawable
						val rect = currentBackgroundDrawable!!.bounds

						drawable.setBounds(rect.left, rect.top, rect.right + AndroidUtilities.dp(6f), rect.bottom)

						canvas.withClip(rect.right - AndroidUtilities.dp(12f), rect.bottom - AndroidUtilities.dp(16f), rect.right + AndroidUtilities.dp(12f), rect.bottom) {
							var w = parentWidth
							var h = parentHeight

							if (h == 0) {
								w = getParentWidth()
								h = AndroidUtilities.displaySize.y

								if (parent is View) {
									val view = parent as View
									w = view.measuredWidth
									h = view.measuredHeight
								}
							}

							drawable.setTop((y + parentViewTopOffset).toInt(), w, h, parentViewTopOffset.toInt(), blurredViewTopOffset, isPinnedTop, isPinnedBottom)

							val alpha = if (!mediaBackground && !isPinnedBottom) transitionParams.changePinnedBottomProgress else 1f - transitionParams.changePinnedBottomProgress

							drawable.alpha = (255 * alpha).toInt()
							drawable.draw(this)
							drawable.alpha = 255
						}
					}
					else {
						val drawable = if (transitionParams.drawPinnedBottomBackground) {
							getThemedDrawable(Theme.key_drawable_msgIn) as MessageDrawable
						}
						else {
							getThemedDrawable(Theme.key_drawable_msgInMedia) as MessageDrawable
						}

						val alpha = if (!mediaBackground && !isPinnedBottom) transitionParams.changePinnedBottomProgress else 1f - transitionParams.changePinnedBottomProgress

						drawable.alpha = (255 * alpha).toInt()

						val rect = currentBackgroundDrawable!!.bounds

						drawable.setBounds(rect.left - AndroidUtilities.dp(6f), rect.top, rect.right, rect.bottom)

						canvas.withClip(rect.left - AndroidUtilities.dp(6f), rect.bottom - AndroidUtilities.dp(16f), rect.left + AndroidUtilities.dp(6f), rect.bottom) {
							drawable.draw(this)
							drawable.alpha = 255
						}
					}
				}
			}
		}

		if (needRestore) {
			canvas.restore()
		}
	}

	fun drawBackgroundInParent(): Boolean {
//		if (canDrawBackgroundInParent && currentMessageObject != null && currentMessageObject.isOutOwner()) {
//			if (resourcesProvider != null) {
//				return resourcesProvider.getCurrentColor(Theme.key_chat_outBubbleGradient1) != null;
//			}
//			else {
//				return Theme.getColorOrNull(Theme.key_chat_outBubbleGradient1) != null;
//			}
//		}
		return false
	}

	fun drawCommentButton(canvas: Canvas, alpha: Float) {
		if (drawSideButton != 3) {
			return
		}

		var height = AndroidUtilities.dp(32f)
		sideStartY -= AndroidUtilities.dp(32f).toFloat()

		if (commentLayout != null) {
			sideStartY -= AndroidUtilities.dp(18f).toFloat()
			height += AndroidUtilities.dp(18f)
		}

		rect.set(sideStartX, sideStartY, sideStartX + AndroidUtilities.dp(32f), sideStartY + height)

		applyServiceShaderMatrix()

		if (alpha != 1f) {
			val paint = getThemedPaint(Theme.key_paint_chatActionBackground)

			val oldAlpha = paint.alpha

			paint.alpha = (alpha * oldAlpha).toInt()

			canvas.drawRoundRect(rect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), paint)

			paint.alpha = oldAlpha
		}
		else {
			canvas.drawRoundRect(rect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), getThemedPaint(if (sideButtonPressed) Theme.key_paint_chatActionBackgroundSelected else Theme.key_paint_chatActionBackground))
		}

		if (hasGradientService()) {
			if (alpha != 1f) {
				val oldAlpha = Theme.chat_actionBackgroundGradientDarkenPaint.alpha
				Theme.chat_actionBackgroundGradientDarkenPaint.alpha = (alpha * oldAlpha).toInt()
				canvas.drawRoundRect(rect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
				Theme.chat_actionBackgroundGradientDarkenPaint.alpha = oldAlpha
			}
			else {
				canvas.drawRoundRect(rect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
			}
		}

		val commentStickerDrawable = Theme.getThemeDrawable(Theme.key_drawable_commentSticker)

		setDrawableBounds(commentStickerDrawable, sideStartX + AndroidUtilities.dp(4f), sideStartY + AndroidUtilities.dp(4f))

		if (alpha != 1f) {
			commentStickerDrawable.alpha = (255 * alpha).toInt()
			commentStickerDrawable.draw(canvas)
			commentStickerDrawable.alpha = 255
		}
		else {
			commentStickerDrawable.draw(canvas)
		}

		if (commentLayout != null) {
			Theme.chat_stickerCommentCountPaint.color = getThemedColor(Theme.key_chat_stickerReplyNameText)
			Theme.chat_stickerCommentCountPaint.alpha = (255 * alpha).toInt()

			if (transitionParams.animateComments) {
				if (transitionParams.animateCommentsLayout != null) {
					canvas.withSave {
						Theme.chat_stickerCommentCountPaint.alpha = (255 * (1.0 - transitionParams.animateChangeProgress) * alpha).toInt()
						translate(sideStartX + (AndroidUtilities.dp(32f) - transitionParams.animateTotalCommentWidth) / 2, sideStartY + AndroidUtilities.dp(30f))
						transitionParams.animateCommentsLayout?.draw(this)
					}
				}

				Theme.chat_stickerCommentCountPaint.alpha = (255 * transitionParams.animateChangeProgress).toInt()
			}

			canvas.withTranslation(sideStartX + (AndroidUtilities.dp(32f) - totalCommentWidth) / 2, sideStartY + AndroidUtilities.dp(30f)) {
				commentLayout?.draw(this)
			}
		}
	}

	fun applyServiceShaderMatrix() {
		applyServiceShaderMatrix(measuredWidth, backgroundHeight, x, viewTop)
	}

	private fun applyServiceShaderMatrix(measuredWidth: Int, backgroundHeight: Int, x: Float, viewTop: Float) {
		Theme.applyServiceShaderMatrix(measuredWidth, backgroundHeight, x, viewTop)
	}

	fun hasOutboundContent(): Boolean {
		if (alpha != 1f) {
			return false
		}

		return ((transitionParams.transitionBotButtons.isNotEmpty() && transitionParams.animateBotButtonsChanged) || botButtons.isNotEmpty() || drawSideButton != 0 || drawNameLayout && nameLayout != null && currentNameStatus != null || animatedEmojiStack != null && animatedEmojiStack!!.holders.isNotEmpty() || currentMessagesGroup == null && (transitionParams.animateReplaceCaptionLayout && transitionParams.animateChangeProgress != 1f || transitionParams.animateChangeProgress != 1.0f && transitionParams.animateMessageText) && transitionParams.animateOutAnimateEmoji != null && transitionParams.animateOutAnimateEmoji!!.holders.isNotEmpty())
	}

	fun drawOutboundContent(canvas: Canvas) {
		if (!enterTransitionInProgress) {
			drawAnimatedEmojis(canvas, 1f)
		}

		if (currentNameStatus != null && currentNameStatusDrawable != null && drawNameLayout && nameLayout != null) {
			val color: Int

			if (currentMessageObject!!.shouldDrawWithoutBackground()) {
				color = getThemedColor(Theme.key_chat_stickerNameText)

				nameX = if (currentMessageObject!!.isOutOwner) {
					AndroidUtilities.dp(28f).toFloat()
				}
				else {
					backgroundDrawableLeft + transitionParams.deltaLeft + backgroundDrawableRight + AndroidUtilities.dp(22f)
				}

				nameY = (layoutHeight - AndroidUtilities.dp(38f)).toFloat()
				nameX -= nameOffsetX
			}
			else {
				nameX = if (mediaBackground || currentMessageObject!!.isOutOwner) {
					backgroundDrawableLeft + transitionParams.deltaLeft + AndroidUtilities.dp(11f) - nameOffsetX + extraTextX
				}
				else {
					backgroundDrawableLeft + transitionParams.deltaLeft + AndroidUtilities.dp(if (!mediaBackground && drawPinnedBottom) 11f else 17f) - nameOffsetX + extraTextX
				}

				color = if (currentUser != null) {
					getThemedColor(getNameColorNameForId(currentUser!!.id))
				}
				else if (currentChat != null) {
					if (currentMessageObject!!.isOutOwner && ChatObject.isChannel(currentChat)) {
						getThemedColor(Theme.key_chat_outForwardedNameText)
					}
					else if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup) {
						Theme.changeColorAccent(getThemedColor(getNameColorNameForId(5)))
					}
					else if (currentMessageObject!!.isOutOwner) {
						getThemedColor(Theme.key_chat_outForwardedNameText)
					}
					else {
						getThemedColor(getNameColorNameForId(currentChat!!.id))
					}
				}
				else {
					getThemedColor(getNameColorNameForId(0))
				}

				nameY = AndroidUtilities.dp(if (drawPinnedTop) 9f else 10f).toFloat()
			}

			if (currentMessagesGroup != null && currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
				nameX += currentMessagesGroup!!.transitionParams.offsetLeft
				nameY += currentMessagesGroup!!.transitionParams.offsetTop - translationY
			}

			nameX += animationOffsetX
			nameY += transitionParams.deltaTop

			val nx = if (transitionParams.animateSign) {
				transitionParams.animateNameX + (nameX - transitionParams.animateNameX) * transitionParams.animateChangeProgress
			}
			else {
				nameX
			}

			currentNameStatusDrawable?.setBounds((abs(nx) + nameLayoutWidth + AndroidUtilities.dp(2f)).toInt(), nameY.toInt() + nameLayout!!.height / 2 - AndroidUtilities.dp(10f), (abs(nx) + nameLayoutWidth + AndroidUtilities.dp(22f)).toInt(), (nameY + nameLayout!!.height / 2 + AndroidUtilities.dp(10f)).toInt())
			currentNameStatusDrawable?.color = ColorUtils.setAlphaComponent(color, 115)
			currentNameStatusDrawable?.draw(canvas)
		}

		if (transitionParams.transitionBotButtons.isNotEmpty() && transitionParams.animateBotButtonsChanged) {
			drawBotButtons(canvas, transitionParams.transitionBotButtons, 1f - transitionParams.animateChangeProgress)
		}

		if (botButtons.isNotEmpty()) {
			drawBotButtons(canvas, botButtons, if (transitionParams.animateBotButtonsChanged) transitionParams.animateChangeProgress else 1f)
		}

		drawSideButton(canvas)
	}

	fun drawAnimatedEmojis(canvas: Canvas, alpha: Float) {
		drawAnimatedEmojiMessageText(canvas, alpha)
		//        if (shouldDrawCaptionLayout()) {
//            drawAnimatedEmojiCaption(canvas, 1f);
//        }
	}

	private fun drawAnimatedEmojiMessageText(canvas: Canvas, alpha: Float) {
		if (transitionParams.animateChangeProgress != 1.0f && transitionParams.animateMessageText) {
			canvas.withSave {
				if (currentBackgroundDrawable != null) {
					val r = currentBackgroundDrawable!!.bounds

					if (currentMessageObject!!.isOutOwner && !mediaBackground && !isPinnedBottom) {
						clipRect(r.left + AndroidUtilities.dp(4f), r.top + AndroidUtilities.dp(4f), r.right - AndroidUtilities.dp(10f), r.bottom - AndroidUtilities.dp(4f))
					}
					else {
						clipRect(r.left + AndroidUtilities.dp(4f), r.top + AndroidUtilities.dp(4f), r.right - AndroidUtilities.dp(4f), r.bottom - AndroidUtilities.dp(4f))
					}
				}

				drawAnimatedEmojiMessageText(this, transitionParams.animateOutTextBlocks, transitionParams.animateOutAnimateEmoji, false, alpha * (1.0f - transitionParams.animateChangeProgress))
				drawAnimatedEmojiMessageText(this, currentMessageObject!!.textLayoutBlocks, animatedEmojiStack, true, alpha * transitionParams.animateChangeProgress)
			}
		}
		else {
			drawAnimatedEmojiMessageText(canvas, currentMessageObject!!.textLayoutBlocks, animatedEmojiStack, true, alpha)
		}
	}

	private fun drawAnimatedEmojiMessageText(canvas: Canvas, textLayoutBlocks: ArrayList<TextLayoutBlock>?, stack: EmojiGroupedSpans?, origin: Boolean, alpha: Float) {
		if (textLayoutBlocks.isNullOrEmpty() || alpha == 0f) {
			return
		}

		val firstVisibleBlockNum: Int
		val lastVisibleBlockNum: Int

		if (origin) {
			if (fullyDraw) {
				this.firstVisibleBlockNum = 0
				this.lastVisibleBlockNum = textLayoutBlocks.size
			}

			firstVisibleBlockNum = this.firstVisibleBlockNum
			lastVisibleBlockNum = this.lastVisibleBlockNum
		}
		else {
			firstVisibleBlockNum = 0
			lastVisibleBlockNum = textLayoutBlocks.size
		}

		var textY = textY.toFloat()

		if (transitionParams.animateText) {
			textY = transitionParams.animateFromTextY * (1f - transitionParams.animateChangeProgress) + this.textY * transitionParams.animateChangeProgress
		}

		for (a in firstVisibleBlockNum..lastVisibleBlockNum) {
			if (a >= textLayoutBlocks.size) {
				break
			}

			if (a < 0) {
				continue
			}

			val block = textLayoutBlocks[a]

			canvas.withTranslation((textX - if (block.isRtl) ceil(currentMessageObject!!.textXOffset.toDouble()).toInt() else 0).toFloat(), textY + block.textYOffset + transitionYOffsetForDrawables) {
				val drawingYOffset = textY + block.textYOffset + transitionYOffsetForDrawables
				var top = 0f // parentBoundsTop - getY() - drawingYOffset + AndroidUtilities.dp(20);
				var bottom = 0f // parentBoundsBottom - getY() - drawingYOffset - AndroidUtilities.dp(20);

				if (transitionParams.messageEntering) {
					bottom = 0f
					top = bottom
				}

				AnimatedEmojiSpan.drawAnimatedEmojis(this, block.textLayout, stack, 0f, block.spoilers, top, bottom, drawingYOffset, alpha)

			}
		}
	}

	fun drawAnimatedEmojiCaption(canvas: Canvas, alpha: Float) {
		if (transitionParams.animateReplaceCaptionLayout && transitionParams.animateChangeProgress != 1f) {
			drawAnimatedEmojiCaption(canvas, transitionParams.animateOutCaptionLayout, transitionParams.animateOutAnimateEmoji, alpha * (1f - transitionParams.animateChangeProgress))
			drawAnimatedEmojiCaption(canvas, captionLayout, animatedEmojiStack, alpha * transitionParams.animateChangeProgress)
		}
		else {
			drawAnimatedEmojiCaption(canvas, captionLayout, animatedEmojiStack, alpha)
		}
	}

	private fun drawAnimatedEmojiCaption(canvas: Canvas, layout: Layout?, stack: EmojiGroupedSpans?, alpha: Float) {
		if ((layout == null || currentMessageObject!!.deleted) && currentPosition != null || alpha <= 0) {
			return
		}

		canvas.withSave {
			var renderingAlpha = alpha

			if (currentMessagesGroup != null) {
				renderingAlpha = currentMessagesGroup!!.transitionParams.captionEnterProgress * alpha
			}

			if (renderingAlpha == 0f) {
				return
			}

			var captionY = captionY
			var captionX = captionX

			if (transitionParams.animateBackgroundBoundsInner) {
				if (transitionParams.transformGroupToSingleMessage) {
					captionY -= translationY
					captionX += transitionParams.deltaLeft
				}
				else if (transitionParams.moveCaption) {
					captionX = this@ChatMessageCell.captionX * transitionParams.animateChangeProgress + transitionParams.captionFromX * (1f - transitionParams.animateChangeProgress)
					captionY = this@ChatMessageCell.captionY * transitionParams.animateChangeProgress + transitionParams.captionFromY * (1f - transitionParams.animateChangeProgress)
				}
				else if (!currentMessageObject!!.isVoice || !TextUtils.isEmpty(currentMessageObject!!.caption)) {
					captionX += transitionParams.deltaLeft
				}
			}

			translate(captionX, captionY)

			try {
				AnimatedEmojiSpan.drawAnimatedEmojis(this, layout, stack, 0f, captionSpoilers, 0f, 0f, captionY, renderingAlpha)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

		}
	}

	private fun drawSideButton(canvas: Canvas) {
		if (drawSideButton != 0) {
			if (currentMessageObject!!.isOutOwner) {
				sideStartX = (transitionParams.lastBackgroundLeft - AndroidUtilities.dp((8 + 32).toFloat())).toFloat()

				if (currentMessagesGroup != null) {
					sideStartX += currentMessagesGroup!!.transitionParams.offsetLeft - animationOffsetX
				}
			}
			else {
				sideStartX = (transitionParams.lastBackgroundRight + AndroidUtilities.dp(8f)).toFloat()

				if (currentMessagesGroup != null) {
					sideStartX += currentMessagesGroup!!.transitionParams.offsetRight - animationOffsetX
				}
			}

			sideStartY = layoutHeight - AndroidUtilities.dp(41f) + transitionParams.deltaBottom

			if (currentMessagesGroup != null) {
				sideStartY += currentMessagesGroup!!.transitionParams.offsetBottom

				if (currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
					sideStartY -= translationY
				}
			}

			if (!reactionsLayoutInBubble.isSmall && reactionsLayoutInBubble.drawServiceShaderBackground) {
				sideStartY -= reactionsLayoutInBubble.getCurrentTotalHeight(transitionParams.animateChangeProgress)
			}

			if (!currentMessageObject!!.isOutOwner && isRoundVideo) {
				val offsetSize = (AndroidUtilities.roundPlayingMessageSize - AndroidUtilities.roundMessageSize) * 0.7f
				var offsetX = if (isPlayingRound) offsetSize else 0f

				if (transitionParams.animatePlayingRound) {
					offsetX = (if (isPlayingRound) transitionParams.animateChangeProgress else 1f - transitionParams.animateChangeProgress) * offsetSize
				}

				val additionalOffset = 80f
				if (isPlayingRound) {
					offsetX -= additionalOffset
				}

				sideStartX -= offsetX
			}

			if (drawSideButton == 3) {
				if (!(enterTransitionInProgress && !currentMessageObject!!.isVoice)) {
					drawCommentButton(canvas, 1f)
				}
			}
			else {
				val rect = if (isRoundVideo) {
					RectF(sideStartX, (sideStartY - AndroidUtilities.dp(24f)), sideStartX + AndroidUtilities.dp(32f).toFloat(), (sideStartY + AndroidUtilities.dp(8f)))
				}
				else {
					RectF(sideStartX, sideStartY, sideStartX + AndroidUtilities.dp(32f).toFloat(), sideStartY + AndroidUtilities.dp(32f).toFloat())
				}

				applyServiceShaderMatrix()

				canvas.drawRoundRect(rect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), getThemedPaint(if (sideButtonPressed) Theme.key_paint_chatActionBackgroundSelected else Theme.key_paint_chatActionBackground))

				if (hasGradientService()) {
					canvas.drawRoundRect(rect, AndroidUtilities.dp(16f).toFloat(), AndroidUtilities.dp(16f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
				}

				if (drawSideButton == 2) {
					ResourcesCompat.getDrawable(context.resources, R.drawable.ic_redo, null)?.mutate()?.let { goIconDrawable -> // getThemedDrawable(Theme.key_drawable_goIcon)
						if (currentMessageObject?.isOutOwner == true) {
							setDrawableBounds(goIconDrawable, sideStartX + AndroidUtilities.dp(8f), if (isRoundVideo) sideStartY - AndroidUtilities.dp(18f) else sideStartY + AndroidUtilities.dp(6f))
							canvas.save()
							canvas.scale(-1f, 1f, goIconDrawable.bounds.centerX().toFloat(), goIconDrawable.bounds.centerY().toFloat())
						}
						else {
							setDrawableBounds(goIconDrawable, sideStartX + AndroidUtilities.dp(8f), if (isRoundVideo) sideStartY - AndroidUtilities.dp(18f) else sideStartY + AndroidUtilities.dp(6f))
						}

						goIconDrawable.draw(canvas)

						if (currentMessageObject?.isOutOwner == true) {
							canvas.restore()
						}
					}
				}
				else {
					val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_redo, null)?.mutate() // getThemedDrawable(Theme.key_drawable_shareIcon)

					setDrawableBounds(drawable, sideStartX + AndroidUtilities.dp(8f), if (isRoundVideo) sideStartY - AndroidUtilities.dp(18f) else sideStartY + AndroidUtilities.dp(6f))

					drawable?.draw(canvas)
				}
			}
		}
	}

	fun getBackgroundDrawableLeft(): Int {
		return if (currentMessageObject!!.isOutOwner) {
			layoutWidth - backgroundWidth - if (!mediaBackground) 0 else AndroidUtilities.dp(9f)
		}
		else {
			var r = AndroidUtilities.dp(((if (isChat && isAvatarVisible) 48 else 0) + if (!mediaBackground) 3 else 9).toFloat())

			if (currentMessagesGroup != null && !currentMessagesGroup!!.isDocuments) {
				if (currentPosition!!.leftSpanOffset != 0) {
					r += ceil((currentPosition!!.leftSpanOffset / 1000.0f * groupPhotosWidth).toDouble()).toInt()
				}
			}

			if (!mediaBackground && drawPinnedBottom) {
				r += AndroidUtilities.dp(6f)
			}

			r
		}
	}

	fun getBackgroundDrawableRight(): Int {
		var right = backgroundWidth - if (mediaBackground) 0 else AndroidUtilities.dp(3f)

		if (!mediaBackground && drawPinnedBottom && currentMessageObject?.isOutOwner == true) {
			right -= AndroidUtilities.dp(6f)
		}

		if (!mediaBackground && drawPinnedBottom && currentMessageObject?.isOutOwner != true) {
			right -= AndroidUtilities.dp(6f)
		}

		return getBackgroundDrawableLeft() + right
	}

	fun getBackgroundDrawableTop(): Int {
		var additionalTop = 0

		currentPosition?.let {
			if (it.flags and MessageObject.POSITION_FLAG_TOP == 0) {
				additionalTop -= AndroidUtilities.dp(3f)
			}
		}

		return additionalTop + if (drawPinnedTop) 0 else AndroidUtilities.dp(1f)
	}

	fun getBackgroundDrawableBottom(): Int {
		var additionalBottom = 0

		currentPosition?.let {
			if (it.flags and MessageObject.POSITION_FLAG_TOP == 0) {
				additionalBottom += AndroidUtilities.dp(3f)
			}

			if (it.flags and MessageObject.POSITION_FLAG_BOTTOM == 0) {
				additionalBottom += AndroidUtilities.dp((if (currentMessageObject?.isOutOwner == true) 3 else 4).toFloat())
			}
		}

		val offsetBottom = if (drawPinnedBottom && drawPinnedTop) {
			0
		}
		else if (drawPinnedBottom) {
			AndroidUtilities.dp(1f)
		}
		else {
			AndroidUtilities.dp(2f)
		}

		return getBackgroundDrawableTop() + layoutHeight - offsetBottom + additionalBottom
	}

	fun drawBackground(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int, pinnedTop: Boolean, pinnedBottom: Boolean, selected: Boolean, keyboardHeight: Int) {
		currentBackgroundDrawable = if (currentMessageObject!!.isOutOwner) {
			if (!mediaBackground && !pinnedBottom) {
				getThemedDrawable(if (selected) Theme.key_drawable_msgOutSelected else Theme.key_drawable_msgOut) as MessageDrawable
			}
			else {
				(getThemedDrawable(if (selected) Theme.key_drawable_msgOutMediaSelected else Theme.key_drawable_msgOutMedia) as MessageDrawable).also {
					if (shouldDrawTransparentBackground()) {
						it.setBackgroundColorId(android.R.color.transparent)
					}
					else {
						it.setBackgroundColorId(0)
					}
				}
			}
		}
		else {
			if (!mediaBackground && !pinnedBottom) {
				getThemedDrawable(if (selected) Theme.key_drawable_msgInSelected else Theme.key_drawable_msgIn) as MessageDrawable
			}
			else {
				(getThemedDrawable(if (selected) Theme.key_drawable_msgInMediaSelected else Theme.key_drawable_msgInMedia) as MessageDrawable).also {
					if (shouldDrawTransparentBackground()) {
						it.setBackgroundColorId(android.R.color.transparent)
					}
					else {
						it.setBackgroundColorId(0)
					}
				}
			}
		}

		var w = parentWidth
		var h = parentHeight

		if (h == 0) {
			w = getParentWidth()
			h = AndroidUtilities.displaySize.y

			(parent as? View)?.let {
				w = it.measuredWidth
				h = it.measuredHeight
			}
		}

		if (currentBackgroundDrawable != null) {
			currentBackgroundDrawable?.setTop(keyboardHeight, w, h, parentViewTopOffset.toInt(), blurredViewTopOffset, pinnedTop, pinnedBottom)

			val currentBackgroundShadowDrawable = currentBackgroundDrawable?.getShadowDrawable()

			if (currentBackgroundShadowDrawable != null) {
				currentBackgroundShadowDrawable.alpha = (alpha * 255).toInt()
				currentBackgroundShadowDrawable.setBounds(left, top, right, bottom)
				currentBackgroundShadowDrawable.draw(canvas)
				currentBackgroundShadowDrawable.alpha = 255
			}

			currentBackgroundDrawable?.alpha = (alpha * 255).toInt()
			currentBackgroundDrawable?.setBounds(left, top, right, bottom)
			currentBackgroundDrawable?.drawCached(canvas, backgroundCacheParams)
			currentBackgroundDrawable?.alpha = 255
		}
	}

	fun hasNameLayout(): Boolean {
		return drawNameLayout && nameLayout != null || drawForwardedName && forwardedNameLayout[0] != null && forwardedNameLayout[1] != null && (currentPosition == null || currentPosition!!.minY.toInt() == 0 && currentPosition!!.minX.toInt() == 0) || replyNameLayout != null
	}

	fun isDrawNameLayout(): Boolean {
		return drawNameLayout && nameLayout != null
	}

	fun isAdminLayoutChanged(): Boolean {
		return !TextUtils.equals(lastPostAuthor, currentMessageObject?.messageOwner?.postAuthor)
	}

	fun drawNamesLayout(canvas: Canvas, alpha: Float) {
		val newAnimationTime = SystemClock.elapsedRealtime()
		var dt = newAnimationTime - lastNamesAnimationTime

		if (dt > 17) {
			dt = 17
		}

		lastNamesAnimationTime = newAnimationTime

		if (currentMessageObject!!.deleted && currentMessagesGroup != null && currentMessagesGroup!!.messages.size >= 1) {
			return
		}

		var restore = Int.MIN_VALUE

		if (alpha != 1f) {
			rect.set(0f, 0f, maxNameWidth.toFloat(), (measuredHeight - getAdditionalHeight()).toFloat())
			restore = canvas.saveLayerAlpha(rect, (255 * alpha).toInt())
		}

		val replyForwardAlpha = max(0f, min(1f, if (currentMessageObject!!.type == MessageObject.TYPE_ROUND_VIDEO) 1f - (photoImage.imageWidth - AndroidUtilities.roundMessageSize) / (AndroidUtilities.roundPlayingMessageSize - AndroidUtilities.roundMessageSize) else 1f))

		if (drawNameLayout && nameLayout != null) {
			canvas.withSave {
				val oldAlpha: Int

				if (currentMessageObject!!.shouldDrawWithoutBackground()) {
					Theme.chat_namePaint.color = getThemedColor(Theme.key_chat_stickerNameText)

					nameX = if (currentMessageObject!!.isOutOwner) {
						AndroidUtilities.dp(28f).toFloat()
					}
					else {
						backgroundDrawableLeft + transitionParams.deltaLeft + backgroundDrawableRight + AndroidUtilities.dp(22f)
					}

					nameY = (layoutHeight - AndroidUtilities.dp(38f)).toFloat()

					val alphaProgress = if (currentMessageObject!!.isOut && (checkBoxVisible || checkBoxAnimationInProgress)) 1.0f - checkBoxAnimationProgress else 1.0f

					rect.set((nameX.toInt() - AndroidUtilities.dp(12f)).toFloat(), (nameY.toInt() - AndroidUtilities.dp(5f)).toFloat(), (nameX.toInt() + AndroidUtilities.dp(12f) + nameWidth).toFloat(), (nameY.toInt() + AndroidUtilities.dp(22f)).toFloat())

					val paint = getThemedPaint(Theme.key_paint_chatActionBackground)

					oldAlpha = paint.alpha

					paint.alpha = (alphaProgress * oldAlpha * replyForwardAlpha).toInt()

					applyServiceShaderMatrix()

					drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), paint)

					if (hasGradientService()) {
						val oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.alpha
						Theme.chat_actionBackgroundGradientDarkenPaint.alpha = (oldAlpha2 * timeAlpha * replyForwardAlpha).toInt()
						drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
						Theme.chat_actionBackgroundGradientDarkenPaint.alpha = oldAlpha2
					}

					if (viaSpan1 != null || viaSpan2 != null) {
						var color = getThemedColor(Theme.key_chat_stickerViaBotNameText)
						color = getThemedColor(Theme.key_chat_stickerViaBotNameText) and 0x00ffffff or ((Color.alpha(color) * alphaProgress).toInt() shl 24)

						viaSpan1?.setColor(color)
						viaSpan2?.setColor(color)
					}

					nameX -= nameOffsetX

					paint.alpha = oldAlpha
				}
				else {
					nameX = if (mediaBackground || currentMessageObject!!.isOutOwner) {
						backgroundDrawableLeft + transitionParams.deltaLeft + AndroidUtilities.dp(11f) - nameOffsetX + extraTextX
					}
					else {
						backgroundDrawableLeft + transitionParams.deltaLeft + AndroidUtilities.dp(if (!mediaBackground && drawPinnedBottom) 11f else 17f) - nameOffsetX + extraTextX
					}

					if (currentUser != null) {
						Theme.chat_namePaint.color = getThemedColor(getNameColorNameForId(currentUser!!.id))
					}
					else if (currentChat != null) {
						if (currentMessageObject!!.isOutOwner && ChatObject.isChannel(currentChat)) {
							Theme.chat_namePaint.color = getThemedColor(Theme.key_chat_outForwardedNameText)
						}
						else if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup) {
							Theme.chat_namePaint.color = Theme.changeColorAccent(getThemedColor(getNameColorNameForId(5)))
						}
						else if (currentMessageObject!!.isOutOwner) {
							Theme.chat_namePaint.color = getThemedColor(Theme.key_chat_outForwardedNameText)
						}
						else {
							Theme.chat_namePaint.color = getThemedColor(getNameColorNameForId(currentChat!!.id))
						}
					}
					else {
						Theme.chat_namePaint.color = getThemedColor(getNameColorNameForId(0))
					}

					nameY = AndroidUtilities.dp(if (drawPinnedTop) 9f else 10f).toFloat()

					if (viaSpan1 != null || viaSpan2 != null) {
						val color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outViaBotNameText else Theme.key_chat_inViaBotNameText)
						viaSpan1?.setColor(color)
						viaSpan2?.setColor(color)
					}
				}

				if (currentMessagesGroup != null && currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
					nameX += currentMessagesGroup!!.transitionParams.offsetLeft
					nameY += currentMessagesGroup!!.transitionParams.offsetTop - translationY
				}

				nameX += animationOffsetX
				nameY += transitionParams.deltaTop

				val nx = if (transitionParams.animateSign) {
					transitionParams.animateNameX + (nameX - transitionParams.animateNameX) * transitionParams.animateChangeProgress
				}
				else {
					nameX
				}

				translate(nx, nameY)

				nameLayout?.draw(this)

			}

			if (adminLayout != null) {
				val color = if (currentMessageObject!!.shouldDrawWithoutBackground()) {
					getThemedColor(Theme.key_chat_stickerReplyNameText)
				}
				else if (currentMessageObject!!.isOutOwner) {
					getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_outAdminSelectedText else Theme.key_chat_outAdminText)
				}
				else {
					getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inAdminSelectedText else Theme.key_chat_inAdminText)
				}

				Theme.chat_adminPaint.color = color

				canvas.withSave {
					var ax: Float

					if (currentMessagesGroup != null && !currentMessagesGroup!!.isDocuments) {
						val dWidth = groupPhotosWidth
						var firstLineWidth = 0

						for (a in currentMessagesGroup!!.posArray.indices) {
							val position = currentMessagesGroup!!.posArray[a]

							firstLineWidth += if (position.minY.toInt() == 0) {
								ceil(((position.pw + position.leftSpanOffset) / 1000.0f * dWidth).toDouble()).toInt()
							}
							else {
								break
							}
						}

						ax = if (!mediaBackground && currentMessageObject!!.isOutOwner) {
							backgroundDrawableLeft + firstLineWidth - AndroidUtilities.dp(17f) - adminLayout!!.getLineWidth(0)
						}
						else {
							backgroundDrawableLeft + firstLineWidth - AndroidUtilities.dp(11f) - adminLayout!!.getLineWidth(0)
						}

						ax -= (extraTextX + AndroidUtilities.dp(8f)).toFloat()

						if (!currentMessageObject!!.isOutOwner) {
							ax -= AndroidUtilities.dp(48f).toFloat()
						}
					}
					else {
						ax = if (currentMessageObject!!.shouldDrawWithoutBackground()) {
							if (currentMessageObject!!.isOutOwner) {
								AndroidUtilities.dp(28f) + nameWidth - adminLayout!!.getLineWidth(0)
							}
							else {
								backgroundDrawableLeft + transitionParams.deltaLeft + backgroundDrawableRight + AndroidUtilities.dp(22f) + nameWidth - adminLayout!!.getLineWidth(0)
							}
						}
						else if (!mediaBackground && currentMessageObject!!.isOutOwner) {
							backgroundDrawableLeft + backgroundDrawableRight - AndroidUtilities.dp(17f) - adminLayout!!.getLineWidth(0)
						}
						else {
							backgroundDrawableLeft + backgroundDrawableRight - AndroidUtilities.dp(11f) - adminLayout!!.getLineWidth(0)
						}
					}

					translate(ax, nameY + AndroidUtilities.dp(0.5f))

					if (transitionParams.animateSign) {
						Theme.chat_adminPaint.alpha = (Color.alpha(color) * transitionParams.animateChangeProgress).toInt()
					}

					adminLayout?.draw(this)

				}
			}
		}

		var drawForwardedNameLocal = drawForwardedName
		val hasReply = replyNameLayout != null
		var forwardedNameLayoutLocal = forwardedNameLayout
		var animatingAlpha = 1f
		var forwardedNameWidthLocal = forwardedNameWidth

		if (transitionParams.animateForwardedLayout) {
			if (!currentMessageObject!!.needDrawForwarded()) {
				drawForwardedNameLocal = true
				forwardedNameLayoutLocal = transitionParams.animatingForwardedNameLayout
				animatingAlpha = 1f - transitionParams.animateChangeProgress
				forwardedNameWidthLocal = transitionParams.animateForwardNameWidth
			}
			else {
				animatingAlpha = transitionParams.animateChangeProgress
			}
		}

		var forwardNameXLocal: Float
		var needDrawReplyBackground = true

		if (drawForwardedNameLocal && forwardedNameLayoutLocal[0] != null && forwardedNameLayoutLocal[1] != null && (currentPosition == null || currentPosition!!.minY.toInt() == 0 && currentPosition!!.minX.toInt() == 0)) {
			if (currentMessageObject!!.type == MessageObject.TYPE_ROUND_VIDEO || currentMessageObject!!.isAnyKindOfSticker) {
				// Theme.chat_forwardNamePaint.setColor(getThemedColor(Theme.key_chat_stickerReplyNameText));
				Theme.chat_forwardNamePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)

				if (currentMessageObject!!.needDrawForwarded()) {
					if (currentMessageObject!!.isOutOwner) {
						forwardNameX = AndroidUtilities.dp(23f).toFloat()
						forwardNameXLocal = forwardNameX
					}
					else {
						forwardNameX = (backgroundDrawableLeft + backgroundDrawableRight + AndroidUtilities.dp(17f)).toFloat()
						forwardNameXLocal = forwardNameX
					}
				}
				else {
					forwardNameXLocal = transitionParams.animateForwardNameX
				}

				if (currentMessageObject!!.isOutOwner && currentMessageObject!!.type == MessageObject.TYPE_ROUND_VIDEO && transitionParams.animatePlayingRound || isPlayingRound) {
					forwardNameXLocal -= AndroidUtilities.dp(78f) * if (isPlayingRound) transitionParams.animateChangeProgress else 1f - transitionParams.animateChangeProgress
				}

				forwardNameY = AndroidUtilities.dp(12f)

				val backWidth = forwardedNameWidthLocal + AndroidUtilities.dp(14f)

				if (hasReply) {
					needDrawReplyBackground = false
					val replyBackWidth = max(replyNameWidth, replyTextWidth) + AndroidUtilities.dp(14f)
					rect.set((forwardNameXLocal.toInt() - AndroidUtilities.dp(7f)).toFloat(), (forwardNameY - AndroidUtilities.dp(6f)).toFloat(), (forwardNameXLocal.toInt() - AndroidUtilities.dp(7f) + max(backWidth, replyBackWidth)).toFloat(), (forwardNameY + AndroidUtilities.dp(38f) + AndroidUtilities.dp(41f)).toFloat())
				}
				else {
					rect.set((forwardNameXLocal.toInt() - AndroidUtilities.dp(7f)).toFloat(), (forwardNameY - AndroidUtilities.dp(6f)).toFloat(), (forwardNameXLocal.toInt() - AndroidUtilities.dp(7f) + backWidth).toFloat(), (forwardNameY + AndroidUtilities.dp(38f)).toFloat())
				}

				applyServiceShaderMatrix()

				var oldAlpha1 = -1
				var oldAlpha2 = -1

				val paint = getThemedPaint(Theme.key_paint_chatActionBackground)

				if (animatingAlpha != 1f || replyForwardAlpha != 1f) {
					oldAlpha1 = paint.alpha
					paint.alpha = (oldAlpha1 * animatingAlpha * replyForwardAlpha).toInt()
				}

				canvas.drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), paint)

				if (hasGradientService()) {
					if (animatingAlpha != 1f || replyForwardAlpha != 1f) {
						oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.alpha
						Theme.chat_actionBackgroundGradientDarkenPaint.alpha = (oldAlpha2 * animatingAlpha * replyForwardAlpha).toInt()
					}

					canvas.drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
				}

				if (oldAlpha1 >= 0) {
					paint.alpha = oldAlpha1
				}

				if (oldAlpha2 >= 0) {
					Theme.chat_actionBackgroundGradientDarkenPaint.alpha = oldAlpha2
				}
			}
			else {
				forwardNameY = AndroidUtilities.dp((10 + if (drawNameLayout) 19 else 0).toFloat())

				if (currentMessageObject!!.isOutOwner) {
					if (hasPsaHint) {
						Theme.chat_forwardNamePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null)
					}
					else {
						Theme.chat_forwardNamePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
					}

					if (currentMessageObject!!.needDrawForwarded()) {
						forwardNameX = (backgroundDrawableLeft + AndroidUtilities.dp(11f) + extraTextX).toFloat()
						forwardNameXLocal = forwardNameX
						forwardNameXLocal += transitionParams.deltaLeft
					}
					else {
						forwardNameXLocal = transitionParams.animateForwardNameX
					}
				}
				else {
					if (hasPsaHint) {
						Theme.chat_forwardNamePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null)
					}
					else {
						Theme.chat_forwardNamePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null)
					}

					if (currentMessageObject!!.needDrawForwarded()) {
						if (mediaBackground) {
							forwardNameX = (backgroundDrawableLeft + AndroidUtilities.dp(11f) + extraTextX).toFloat()
							forwardNameXLocal = forwardNameX
						}
						else {
							forwardNameX = (backgroundDrawableLeft + AndroidUtilities.dp(if (drawPinnedBottom) 11f else 17f) + extraTextX).toFloat()
							forwardNameXLocal = forwardNameX
						}
					}
					else {
						forwardNameXLocal = transitionParams.animateForwardNameX
					}
				}
			}

			var clipContent = false

			if (transitionParams.animateForwardedLayout) {
				if (currentBackgroundDrawable != null && currentMessagesGroup == null && currentMessageObject!!.type != MessageObject.TYPE_ROUND_VIDEO && !currentMessageObject!!.isAnyKindOfSticker) {
					val r = currentBackgroundDrawable!!.bounds

					canvas.save()

					if (currentMessageObject!!.isOutOwner && !mediaBackground && !isPinnedBottom) {
						canvas.clipRect(r.left + AndroidUtilities.dp(4f), r.top + AndroidUtilities.dp(4f), r.right - AndroidUtilities.dp(10f), r.bottom - AndroidUtilities.dp(4f))
					}
					else {
						canvas.clipRect(r.left + AndroidUtilities.dp(4f), r.top + AndroidUtilities.dp(4f), r.right - AndroidUtilities.dp(4f), r.bottom - AndroidUtilities.dp(4f))
					}

					clipContent = true
				}
			}

			for (a in 0..1) {
				canvas.withTranslation(forwardNameXLocal - forwardNameOffsetX[a], (forwardNameY + AndroidUtilities.dp(16f) * a).toFloat()) {
					if (animatingAlpha != 1f || replyForwardAlpha != 1f) {
						val oldAlpha = forwardedNameLayoutLocal[a]!!.paint.alpha
						forwardedNameLayoutLocal[a]!!.paint.alpha = (oldAlpha * animatingAlpha * replyForwardAlpha).toInt()
						forwardedNameLayoutLocal[a]!!.draw(this)
						forwardedNameLayoutLocal[a]!!.paint.alpha = oldAlpha
					}
					else {
						forwardedNameLayoutLocal[a]!!.draw(this)
					}

				}
			}

			if (clipContent) {
				canvas.restore()
			}

			if (hasPsaHint) {
				if (psaButtonVisible || psaButtonProgress > 0) {
					val drawable = Theme.chat_psaHelpDrawable[if (currentMessageObject!!.isOutOwner) 1 else 0]
					val cx = psaHelpX + drawable.intrinsicWidth / 2
					val cy = psaHelpY + drawable.intrinsicHeight / 2
					val scale = if (psaButtonVisible && psaButtonProgress < 1) AnimationProperties.overshootInterpolator.getInterpolation(psaButtonProgress) else psaButtonProgress
					val w = (drawable.intrinsicWidth * scale).toInt()
					val h = (drawable.intrinsicHeight * scale).toInt()

					drawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
					drawable.draw(canvas)

					if (selectorDrawable[0] != null && selectorDrawableMaskType[0] == 3) {
						canvas.withScale(psaButtonProgress, psaButtonProgress, selectorDrawable[0]!!.bounds.centerX().toFloat(), selectorDrawable[0]!!.bounds.centerY().toFloat()) {
							selectorDrawable[0]!!.draw(this)
						}
					}
				}

				if (psaButtonVisible && psaButtonProgress < 1.0f) {
					psaButtonProgress += dt / 180.0f

					invalidate()

					if (psaButtonProgress > 1.0f) {
						psaButtonProgress = 1.0f
						setInvalidatesParent(false)
					}
				}
				else if (!psaButtonVisible && psaButtonProgress > 0.0f) {
					psaButtonProgress -= dt / 180.0f

					invalidate()

					if (psaButtonProgress < 0.0f) {
						psaButtonProgress = 0.0f
						setInvalidatesParent(false)
					}
				}
			}
		}

		if (hasReply) {
			var replyStartX = replyStartX.toFloat()
			var replyStartY = replyStartY.toFloat()

			if (currentMessagesGroup != null && currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
				replyStartX += currentMessagesGroup!!.transitionParams.offsetLeft
			}

			if (transitionParams.animateBackgroundBoundsInner) {
				replyStartX += if (isRoundVideo) {
					transitionParams.deltaLeft + transitionParams.deltaRight
				}
				else {
					transitionParams.deltaLeft
				}

				replyStartY = this.replyStartY * transitionParams.animateChangeProgress + transitionParams.animateFromReplyY * (1f - transitionParams.animateChangeProgress)
			}

			if (currentMessageObject!!.shouldDrawWithoutBackground()) {
				Theme.chat_replyLinePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)

				var oldAlpha = Theme.chat_replyLinePaint.alpha

				Theme.chat_replyLinePaint.alpha = (oldAlpha * timeAlpha * replyForwardAlpha).toInt()
				Theme.chat_replyNamePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)

				oldAlpha = Theme.chat_replyNamePaint.alpha

				Theme.chat_replyNamePaint.alpha = (oldAlpha * timeAlpha * replyForwardAlpha).toInt()
				Theme.chat_replyTextPaint.color = ResourcesCompat.getColor(resources, R.color.white, null)

				oldAlpha = Theme.chat_replyTextPaint.alpha

				Theme.chat_replyTextPaint.alpha = (oldAlpha * timeAlpha * replyForwardAlpha).toInt()

				if (needDrawReplyBackground) {
					val backWidth = max(replyNameWidth, replyTextWidth) + AndroidUtilities.dp(14f)

					rect.set((replyStartX.toInt() - AndroidUtilities.dp(7f)).toFloat(), replyStartY - AndroidUtilities.dp(6f), (replyStartX.toInt() - AndroidUtilities.dp(7f) + backWidth).toFloat(), replyStartY + AndroidUtilities.dp(41f))

					applyServiceShaderMatrix()

					val paint = getThemedPaint(Theme.key_paint_chatActionBackground)

					oldAlpha = paint.alpha

					paint.alpha = (oldAlpha * timeAlpha * replyForwardAlpha).toInt()

					canvas.drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), paint)

					paint.alpha = oldAlpha

					if (hasGradientService()) {
						oldAlpha = Theme.chat_actionBackgroundGradientDarkenPaint.alpha

						Theme.chat_actionBackgroundGradientDarkenPaint.alpha = (oldAlpha * timeAlpha * replyForwardAlpha).toInt()

						canvas.drawRoundRect(rect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)

						Theme.chat_actionBackgroundGradientDarkenPaint.alpha = oldAlpha
					}
				}
			}
			else {
				if (currentMessageObject!!.isOutOwner) {
					Theme.chat_replyLinePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
					Theme.chat_replyNamePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)

					if (currentMessageObject!!.hasValidReplyMessageObject() && (currentMessageObject!!.replyMessageObject?.type == MessageObject.TYPE_COMMON || !TextUtils.isEmpty(currentMessageObject!!.replyMessageObject?.caption)) && !(MessageObject.getMedia(currentMessageObject!!.replyMessageObject?.messageOwner) is TLRPC.TLMessageMediaGame || MessageObject.getMedia(currentMessageObject!!.replyMessageObject?.messageOwner) is TLRPC.TLMessageMediaInvoice)) {
						Theme.chat_replyTextPaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
					}
					else {
						Theme.chat_replyTextPaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
					}
				}
				else {
					Theme.chat_replyLinePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null)
					Theme.chat_replyNamePaint.color = ResourcesCompat.getColor(resources, R.color.brand, null)

					if (currentMessageObject!!.hasValidReplyMessageObject() && (currentMessageObject!!.replyMessageObject?.type == MessageObject.TYPE_COMMON || !TextUtils.isEmpty(currentMessageObject!!.replyMessageObject?.caption)) && !(MessageObject.getMedia(currentMessageObject!!.replyMessageObject?.messageOwner) is TLRPC.TLMessageMediaGame || MessageObject.getMedia(currentMessageObject!!.replyMessageObject?.messageOwner) is TLRPC.TLMessageMediaInvoice)) {
						Theme.chat_replyTextPaint.color = ResourcesCompat.getColor(resources, R.color.text, null)
					}
					else {
						Theme.chat_replyTextPaint.color = getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inReplyMediaMessageSelectedText else Theme.key_chat_inReplyMediaMessageText)
					}
				}
			}

			forwardNameX = replyStartX - replyTextOffset + AndroidUtilities.dp((10 + if (needReplyImage) 44 else 0).toFloat())

			if ((currentPosition == null || currentPosition!!.minY.toInt() == 0 && currentPosition!!.minX.toInt() == 0) && !(enterTransitionInProgress && !currentMessageObject!!.isVoice)) {
				AndroidUtilities.rectTmp[replyStartX, replyStartY, replyStartX + AndroidUtilities.dp(2f)] = replyStartY + AndroidUtilities.dp(35f)

				canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), Theme.chat_replyLinePaint)

				if (needReplyImage) {
					replyImageReceiver.alpha = replyForwardAlpha
					replyImageReceiver.setImageCoordinates(replyStartX + AndroidUtilities.dp(10f), replyStartY, AndroidUtilities.dp(35f).toFloat(), AndroidUtilities.dp(35f).toFloat())
					replyImageReceiver.draw(canvas)
				}

				if (replyNameLayout != null) {
					canvas.withTranslation(replyStartX - replyNameOffset + AndroidUtilities.dp((10 + if (needReplyImage) 44 else 0).toFloat()), replyStartY) {
						replyNameLayout?.draw(this)
					}
				}

				if (replyTextLayout != null) {
					canvas.withTranslation(forwardNameX, replyStartY + AndroidUtilities.dp(19f)) {
						val spoilersColor = if (currentMessageObject!!.isOut && !ChatObject.isChannelAndNotMegaGroup(currentMessageObject!!.chatId, currentAccount)) getThemedColor(Theme.key_chat_outTimeText) else replyTextLayout!!.paint.color
						SpoilerEffect.renderWithRipple(this@ChatMessageCell, invalidateSpoilersParent, spoilersColor, -AndroidUtilities.dp(2f), spoilersPatchedReplyTextLayout, replyTextLayout, replySpoilers, this, false)
						AnimatedEmojiSpan.drawAnimatedEmojis(this, replyTextLayout, animatedEmojiReplyStack, 0f, replySpoilers, 0f, 0f, 0f, alpha)
					}
				}
			}
		}

		if (restore != Int.MIN_VALUE) {
			canvas.restoreToCount(restore)
		}
	}

	fun hasCaptionLayout(): Boolean {
		return captionLayout != null
	}

	fun hasCommentLayout(): Boolean {
		return drawCommentButton
	}

	fun isDrawingSelectionBackground(): Boolean {
		return drawSelectionBackground || isHighlightedAnimated || isHighlighted
	}

	fun getHighlightAlpha(): Float {
		return if (!drawSelectionBackground && isHighlightedAnimated) {
			if (highlightProgress >= 300) 1.0f else highlightProgress / 300.0f
		}
		else {
			1.0f
		}
	}

	fun setCheckBoxVisible(visible: Boolean, animated: Boolean) {
		if (visible && checkBox == null) {
			checkBox = CheckBoxBase(this, 22)

			if (attachedToWindow) {
				checkBox?.onAttachedToWindow()
			}
		}

		if (visible && mediaCheckBox == null && ((currentMessagesGroup != null && currentMessagesGroup!!.messages.size > 1) || (groupedMessagesToSet != null && groupedMessagesToSet!!.messages.size > 1))) {
			mediaCheckBox = CheckBoxBase(this, 22).also {
				it.useDefaultCheck = true

				if (attachedToWindow) {
					it.onAttachedToWindow()
				}
			}
		}

		if (checkBoxVisible == visible) {
			if (animated != checkBoxAnimationInProgress && !animated) {
				checkBoxAnimationProgress = if (visible) 1.0f else 0.0f
				invalidate()
			}

			return
		}

		checkBoxAnimationInProgress = animated
		checkBoxVisible = visible

		if (animated) {
			lastCheckBoxAnimationTime = SystemClock.elapsedRealtime()
		}
		else {
			checkBoxAnimationProgress = if (visible) 1.0f else 0.0f
		}

		invalidate()
	}

	fun setChecked(checked: Boolean, allChecked: Boolean, animated: Boolean) {
		checkBox?.setChecked(allChecked, animated)
		mediaCheckBox?.setChecked(checked, animated)
		// MARK: uncomment to enable selected message background
		// backgroundDrawable.setSelected(allChecked, animated);
	}

	fun setLastTouchCoordinates(x: Float, y: Float) {
		lastTouchX = x
		lastTouchY = y
		backgroundDrawable.setTouchCoords(lastTouchX + translationX, lastTouchY)
	}

	fun getCurrentBackgroundDrawable(update: Boolean): MessageDrawable? {
		if (update) {
			val forceMediaByGroup = currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM == 0 && currentMessagesGroup!!.isDocuments && !drawPinnedBottom

			currentBackgroundDrawable = if (currentMessageObject!!.isOutOwner) {
				if (!mediaBackground && !drawPinnedBottom && !forceMediaByGroup) {
					getThemedDrawable(Theme.key_drawable_msgOut) as MessageDrawable
				}
				else {
					(getThemedDrawable(Theme.key_drawable_msgOutMedia) as MessageDrawable).also {
						if (shouldDrawTransparentBackground()) {
							it.setBackgroundColorId(android.R.color.transparent)
						}
						else {
							it.setBackgroundColorId(0)
						}
					}
				}
			}
			else {
				if (!mediaBackground && !drawPinnedBottom && !forceMediaByGroup) {
					getThemedDrawable(Theme.key_drawable_msgIn) as MessageDrawable
				}
				else {
					(getThemedDrawable(Theme.key_drawable_msgInMedia) as MessageDrawable).also {
						if (shouldDrawTransparentBackground()) {
							it.setBackgroundColorId(android.R.color.transparent)
						}
						else {
							it.setBackgroundColorId(0)
						}
					}
				}
			}
		}

		currentBackgroundDrawable?.getBackgroundDrawable()

		return currentBackgroundDrawable
	}

	private fun shouldDrawCaptionLayout(): Boolean {
		return !currentMessageObject!!.preview && (currentPosition == null || (currentMessagesGroup != null && currentMessagesGroup!!.isDocuments && (currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM) == 0)) && !transitionParams.animateBackgroundBoundsInner && !(enterTransitionInProgress && currentMessageObject!!.isVoice)
	}

	fun drawCaptionLayout(canvas: Canvas, selectionOnly: Boolean, alpha: Float) {
		if (animatedEmojiStack != null && (captionLayout != null || transitionParams.animateOutCaptionLayout != null)) {
			animatedEmojiStack!!.clearPositions()
		}

		if (transitionParams.animateReplaceCaptionLayout && transitionParams.animateChangeProgress != 1f) {
			drawCaptionLayout(canvas, transitionParams.animateOutCaptionLayout, selectionOnly, alpha * (1f - transitionParams.animateChangeProgress))
			drawCaptionLayout(canvas, captionLayout, selectionOnly, alpha * transitionParams.animateChangeProgress)
		}
		else {
			drawCaptionLayout(canvas, captionLayout, selectionOnly, alpha)
		}

		if (!selectionOnly) {
			drawAnimatedEmojiCaption(canvas, alpha)
		}

		if (currentMessageObject != null && currentMessageObject!!.messageOwner != null && currentMessageObject!!.isVoiceTranscriptionOpen && !currentMessageObject!!.messageOwner!!.voiceTranscriptionFinal && TranscribeButton.isTranscribing(currentMessageObject)) {
			invalidate()
		}

		if (!selectionOnly && currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0 && currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0 && !reactionsLayoutInBubble.isSmall) {
			if (reactionsLayoutInBubble.drawServiceShaderBackground) {
				applyServiceShaderMatrix()
			}

			if (reactionsLayoutInBubble.drawServiceShaderBackground || !transitionParams.animateBackgroundBoundsInner || currentPosition != null) {
				reactionsLayoutInBubble.draw(canvas, if (transitionParams.animateChange) transitionParams.animateChangeProgress else 1f, null)
			}
			else {
				canvas.withClip(0f, 0f, measuredWidth.toFloat(), getBackgroundDrawableBottom() + transitionParams.deltaBottom) {
					reactionsLayoutInBubble.draw(this, if (transitionParams.animateChange) transitionParams.animateChangeProgress else 1f, null)
				}
			}
		}
	}

	private fun drawCaptionLayout(canvas: Canvas, captionLayout: StaticLayout?, selectionOnly: Boolean, alpha: Float) {
		val leaveCommentIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_leave_comment, null)

		if (currentBackgroundDrawable != null && drawCommentButton && timeLayout != null) {
			var y = layoutHeight + transitionParams.deltaBottom - AndroidUtilities.dp(18f) - timeLayout!!.height

			if (currentMessagesGroup != null) {
				y += currentMessagesGroup!!.transitionParams.offsetBottom

				if (currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
					y -= translationY
				}
			}

			val x = if (mediaBackground) {
				backgroundDrawableLeft + AndroidUtilities.dp(12f) + extraTextX
			}
			else {
				backgroundDrawableLeft + AndroidUtilities.dp(if (drawPinnedBottom) 12f else 18f) + extraTextX
			}

			var endX = x - extraTextX

			if (currentMessagesGroup != null && !currentMessageObject!!.isMusic && !currentMessageObject!!.isDocument()) {
				val dWidth = groupPhotosWidth

				if (currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0) {
					endX += ceil((currentPosition!!.pw / 1000.0f * dWidth).toDouble()).toInt()
				}
				else {
					var firstLineWidth = 0

					for (a in currentMessagesGroup!!.posArray.indices) {
						val position = currentMessagesGroup!!.posArray[a]

						firstLineWidth += if (position.minY.toInt() == 0) {
							ceil(((position.pw + position.leftSpanOffset) / 1000.0f * dWidth).toDouble()).toInt()
						}
						else {
							break
						}
					}

					endX += firstLineWidth - AndroidUtilities.dp(9f)
				}
			}
			else {
				endX += backgroundWidth - if (mediaBackground) 0 else AndroidUtilities.dp(9f)
			}

			val h: Int
			val h2: Int

			if (isPinnedBottom) {
				h = 2
				h2 = 3
			}
			else if (isPinnedTop) {
				h = 4
				h2 = 1
			}
			else {
				h = 3
				h2 = 0
			}

			var buttonX = getCurrentBackgroundLeft() + AndroidUtilities.dp(if (currentMessageObject!!.isOutOwner || mediaBackground || drawPinnedBottom) 2f else 8f)
			val buttonY = (layoutHeight - AndroidUtilities.dp(45.1f - h2)).toFloat()

			if (currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT == 0 && !currentMessagesGroup!!.hasSibling) {
				endX += AndroidUtilities.dp(14f)
				buttonX -= AndroidUtilities.dp(10f)
			}

			commentButtonRect.set(buttonX, buttonY.toInt(), endX - AndroidUtilities.dp(14f), layoutHeight - AndroidUtilities.dp(h.toFloat()))

			if (selectorDrawable[1] != null && selectorDrawableMaskType[1] == 2) {
				selectorDrawable[1]?.bounds = commentButtonRect
				selectorDrawable[1]?.draw(canvas)
			}

			if (currentPosition == null || currentPosition!!.flags and MessageObject.POSITION_FLAG_LEFT != 0 && currentPosition!!.minX.toInt() == 0 && currentPosition!!.maxX.toInt() == 0) {
				Theme.chat_instantViewPaint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)

				var drawnAvatars = false
				var avatarsOffset = 2

				if (commentAvatarImages != null) {
					val toAdd = AndroidUtilities.dp(17f)
					val ax = x + extraTextX

					for (a in commentAvatarImages!!.indices.reversed()) {
						if (!commentAvatarImagesVisible!![a] || !commentAvatarImages!![a]!!.hasImageSet()) {
							continue
						}

						commentAvatarImages!![a]!!.setImageX(ax + toAdd * a)
						commentAvatarImages!![a]!!.imageY = y - AndroidUtilities.dp(4f) + if (isPinnedBottom) AndroidUtilities.dp(2f) else 0

						if (a != commentAvatarImages!!.size - 1) {
							//MARK: this part is responsible for drawing a circular frame around your avatar, if you need it in the future, just increase the value of AndroidUtilities.dp(0f).toFloat()
							canvas.drawCircle(commentAvatarImages!![a]!!.centerX, commentAvatarImages!![a]!!.centerY, AndroidUtilities.dp(0f).toFloat(), currentBackgroundDrawable!!.paint)
						}

						commentAvatarImages!![a]!!.draw(canvas)

						drawnAvatars = true

						if (a != 0) {
							avatarsOffset += 17
						}
					}
				}

				if (!mediaBackground || captionLayout != null || !reactionsLayoutInBubble.isEmpty && !reactionsLayoutInBubble.isSmall) {
					if (isDrawSelectionBackground()) {
						Theme.chat_replyLinePaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outVoiceSeekbarSelected else Theme.key_chat_inVoiceSeekbarSelected)
					}
					else {
						Theme.chat_replyLinePaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outVoiceSeekbar else Theme.key_chat_inVoiceSeekbar)
					}

					var ly = (layoutHeight - AndroidUtilities.dp(45.1f - h2)).toFloat()
					ly += transitionParams.deltaBottom

					if (currentMessagesGroup != null) {
						ly += currentMessagesGroup!!.transitionParams.offsetBottom

						if (currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
							ly -= translationY
						}
					}
					else {
						val backgroundWidth = (backgroundWidth - transitionParams.deltaLeft + transitionParams.deltaRight).toInt()
						endX = x + backgroundWidth - AndroidUtilities.dp(12f)
					}

					canvas.drawLine(x.toFloat(), ly, (endX - AndroidUtilities.dp(14f)).toFloat(), ly, Theme.chat_replyLinePaint)
				}

				if (commentLayout != null && drawSideButton != 3) {
					Theme.chat_replyNamePaint.color = if (currentMessageObject!!.isOutOwner) ResourcesCompat.getColor(context.resources, R.color.white, null) else ResourcesCompat.getColor(context.resources, R.color.brand, null)

					commentX = x + AndroidUtilities.dp((33 + avatarsOffset).toFloat())

					if (drawCommentNumber) {
						commentX += commentNumberWidth + AndroidUtilities.dp(4f)
					}

					val prevAlpha = Theme.chat_replyNamePaint.alpha

					if (transitionParams.animateComments) {
						if (transitionParams.animateCommentsLayout != null) {
							canvas.withSave {
								Theme.chat_replyNamePaint.alpha = (prevAlpha * (1.0 - transitionParams.animateChangeProgress)).toInt()

								val cx = transitionParams.animateCommentX + (commentX - transitionParams.animateCommentX) * transitionParams.animateChangeProgress

								translate(cx, y - AndroidUtilities.dp(0.1f) + if (isPinnedBottom) AndroidUtilities.dp(2f) else 0)

								transitionParams.animateCommentsLayout!!.draw(this)

							}
						}
					}

					canvas.withTranslation((x + AndroidUtilities.dp((33 + avatarsOffset).toFloat())).toFloat(), y - AndroidUtilities.dp(0.1f) + if (isPinnedBottom) AndroidUtilities.dp(2f) else 0) {
						if (!currentMessageObject!!.isSent) {
							Theme.chat_replyNamePaint.alpha = 127
							Theme.chat_commentArrowDrawable.alpha = 127
							leaveCommentIcon?.alpha = 127
						}
						else {
							Theme.chat_commentArrowDrawable.alpha = 255
							leaveCommentIcon?.alpha = 255
						}

						if (drawCommentNumber || transitionParams.animateComments && transitionParams.animateDrawCommentNumber) {
							if (drawCommentNumber && transitionParams.animateComments) {
								if (transitionParams.animateDrawCommentNumber) {
									Theme.chat_replyNamePaint.alpha = prevAlpha
								}
								else {
									Theme.chat_replyNamePaint.alpha = (prevAlpha * transitionParams.animateChangeProgress).toInt()
								}
							}

							commentNumberLayout!!.draw(this)

							if (drawCommentNumber) {
								translate((commentNumberWidth + AndroidUtilities.dp(4f)).toFloat(), 0f)
							}
						}

						if (transitionParams.animateComments && transitionParams.animateCommentsLayout != null) {
							Theme.chat_replyNamePaint.alpha = (prevAlpha * transitionParams.animateChangeProgress).toInt()
						}
						else {
							Theme.chat_replyNamePaint.alpha = (prevAlpha * alpha).toInt()
						}

						commentLayout?.draw(this)

					}

					commentUnreadX = x + commentWidth + AndroidUtilities.dp((33 + avatarsOffset).toFloat()) + AndroidUtilities.dp(9f)

					if (drawCommentNumber) {
						commentUnreadX += commentNumberWidth + AndroidUtilities.dp(4f)
					}

					var replies: TLRPC.TLMessageReplies? = null

					if (currentMessagesGroup != null && currentMessagesGroup!!.messages.isNotEmpty()) {
						val messageObject = currentMessagesGroup!!.messages[0]

						if (messageObject.hasReplies()) {
							replies = messageObject.messageOwner?.replies
						}
					}
					else {
						if (currentMessageObject!!.hasReplies()) {
							replies = currentMessageObject?.messageOwner?.replies
						}
					}

					if ((replies != null && replies.readMaxId != 0 && replies.readMaxId < replies.maxId).also { commentDrawUnread = it }) {
						val color = getThemedColor(Theme.key_chat_inInstant)

						Theme.chat_docBackPaint.color = color

						val unreadX = if (transitionParams.animateComments) {
							if (!transitionParams.animateCommentDrawUnread) {
								Theme.chat_docBackPaint.alpha = (Color.alpha(color) * transitionParams.animateChangeProgress).toInt()
							}

							(transitionParams.animateCommentUnreadX + (commentUnreadX - transitionParams.animateCommentUnreadX) * transitionParams.animateChangeProgress).toInt()
						}
						else {
							commentUnreadX
						}

						canvas.drawCircle(unreadX.toFloat(), y + AndroidUtilities.dp(8f) + if (isPinnedBottom) AndroidUtilities.dp(2f) else 0, AndroidUtilities.dp(2.5f).toFloat(), Theme.chat_docBackPaint)
					}
				}

				if (!drawnAvatars) {
					setDrawableBounds(leaveCommentIcon, x.toFloat(), y - AndroidUtilities.dp(4f) + if (isPinnedBottom) AndroidUtilities.dp(2f) else 0)

					if (alpha != 1f) {
						leaveCommentIcon?.alpha = (255 * alpha).toInt()
						leaveCommentIcon?.draw(canvas)
						leaveCommentIcon?.alpha = 255
					}
					else {
						leaveCommentIcon?.draw(canvas)
					}
				}

				commentArrowX = endX - AndroidUtilities.dp(44f)

				val commentX: Int

				commentX = if (transitionParams.animateComments) {
					(transitionParams.animateCommentArrowX + (commentArrowX - transitionParams.animateCommentArrowX) * transitionParams.animateChangeProgress).toInt()
				}
				else {
					commentArrowX
				}

				val commentY = y - AndroidUtilities.dp(4f) + if (isPinnedBottom) AndroidUtilities.dp(2f) else 0
				val drawProgress = delegate!!.shouldDrawThreadProgress(this)
				val newTime = SystemClock.elapsedRealtime()
				var dt = newTime - commentProgressLastUpdateTime

				commentProgressLastUpdateTime = newTime

				if (dt > 17) {
					dt = 17
				}

				if (drawProgress) {
					if (commentProgressAlpha < 1.0f) {
						commentProgressAlpha += dt / 180.0f

						if (commentProgressAlpha > 1.0f) {
							commentProgressAlpha = 1.0f
						}
					}
				}
				else {
					if (commentProgressAlpha > 0.0f) {
						commentProgressAlpha -= dt / 180.0f

						if (commentProgressAlpha < 0.0f) {
							commentProgressAlpha = 0.0f
						}
					}
				}

				if ((drawProgress || commentProgressAlpha > 0.0f) && commentProgress != null) {
					commentProgress?.setColor(getThemedColor(Theme.key_chat_inInstant))
					commentProgress?.setAlpha(commentProgressAlpha)
					commentProgress?.draw(canvas, (commentX + AndroidUtilities.dp(11f)).toFloat(), commentY + AndroidUtilities.dp(12f), commentProgressAlpha)

					invalidate()
				}

				if (!drawProgress || commentProgressAlpha < 1.0f) {
					val aw = Theme.chat_commentArrowDrawable.intrinsicWidth
					val ah = Theme.chat_commentArrowDrawable.intrinsicHeight
					val acx = (commentX + aw / 2).toFloat()
					val acy = commentY + ah / 2

					Theme.chat_commentArrowDrawable.setBounds((acx - aw / 2 * (1.0f - commentProgressAlpha)).toInt(), (acy - ah / 2 * (1.0f - commentProgressAlpha)).toInt(), (acx + aw / 2 * (1.0f - commentProgressAlpha)).toInt(), (acy + ah / 2 * (1.0f - commentProgressAlpha)).toInt())
					Theme.chat_commentArrowDrawable.alpha = (255 * (1.0f - commentProgressAlpha) * alpha).toInt()
					Theme.chat_commentArrowDrawable.draw(canvas)
				}
			}
		}

		if (captionLayout == null || selectionOnly && links.isEmpty || currentMessageObject!!.deleted && currentPosition != null || alpha == 0f) {
			return
		}

		val textInColor = ResourcesCompat.getColor(resources, R.color.text, null)
		val linkInColor = ResourcesCompat.getColor(resources, R.color.brand, null)
		val textOutColor = ResourcesCompat.getColor(resources, R.color.white, null)
		val linkOutColor = ResourcesCompat.getColor(resources, R.color.white, null)

		if (currentMessageObject!!.isOutOwner) {
			Theme.chat_msgTextPaint.color = textOutColor // getThemedColor(Theme.key_chat_messageTextOut));
			Theme.chat_msgTextPaint.linkColor = linkOutColor // getThemedColor(Theme.key_chat_messageLinkOut);
		}
		else {
			Theme.chat_msgTextPaint.color = textInColor // getThemedColor(Theme.key_chat_messageTextIn));
			Theme.chat_msgTextPaint.linkColor = linkInColor // getThemedColor(Theme.key_chat_messageLinkIn);
		}

		canvas.withSave {
			var renderingAlpha = alpha

			if (currentMessagesGroup != null) {
				renderingAlpha = currentMessagesGroup!!.transitionParams.captionEnterProgress * alpha
			}
			if (renderingAlpha == 0f) {
				return
			}

			var captionY = captionY
			var captionX = captionX

			if (transitionParams.animateBackgroundBoundsInner) {
				if (transitionParams.transformGroupToSingleMessage) {
					captionY -= translationY
					captionX += transitionParams.deltaLeft
				}
				else if (transitionParams.moveCaption) {
					captionX = this@ChatMessageCell.captionX * transitionParams.animateChangeProgress + transitionParams.captionFromX * (1f - transitionParams.animateChangeProgress)
					captionY = this@ChatMessageCell.captionY * transitionParams.animateChangeProgress + transitionParams.captionFromY * (1f - transitionParams.animateChangeProgress)
				}
				else if (!currentMessageObject!!.isVoice || !TextUtils.isEmpty(currentMessageObject!!.caption)) {
					captionX += transitionParams.deltaLeft
				}
			}

			var restore = Int.MIN_VALUE

			if (renderingAlpha != 1.0f) {
				rect.set(captionX, captionY, captionX + captionLayout.width, captionY + captionLayout.height)
				restore = saveLayerAlpha(rect, (255 * renderingAlpha).toInt())
			}

			if (transitionParams.animateBackgroundBoundsInner && currentBackgroundDrawable != null && currentMessagesGroup == null) {
				val bottomOffset = (if (drawCommentButton) commentButtonRect.height() else 0) + if (!reactionsLayoutInBubble.isSmall) reactionsLayoutInBubble.height else 0

				if (currentMessageObject!!.isOutOwner && !mediaBackground && !isPinnedBottom) {
					clipRect(getBackgroundDrawableLeft() + transitionParams.deltaLeft + AndroidUtilities.dp(4f), getBackgroundDrawableTop() + transitionParams.deltaTop + AndroidUtilities.dp(4f), getBackgroundDrawableRight() + transitionParams.deltaRight - AndroidUtilities.dp(10f), getBackgroundDrawableBottom() + transitionParams.deltaBottom - AndroidUtilities.dp(4f) - bottomOffset)
				}
				else {
					clipRect(getBackgroundDrawableLeft() + transitionParams.deltaLeft + AndroidUtilities.dp(4f), getBackgroundDrawableTop() + transitionParams.deltaTop + AndroidUtilities.dp(4f), getBackgroundDrawableRight() + transitionParams.deltaRight - AndroidUtilities.dp(4f), getBackgroundDrawableBottom() + transitionParams.deltaBottom - AndroidUtilities.dp(4f) - bottomOffset)
				}
			}

			translate(captionX, captionY)

			if (links.draw(this)) {
				invalidate()
			}

			if (urlPathSelection.isNotEmpty()) {
				for (b in urlPathSelection.indices) {
					drawPath(urlPathSelection[b], Theme.chat_textSearchSelectionPaint)
				}
			}

			if (!selectionOnly) {
				try {
					if (delegate?.textSelectionHelper?.isSelected(currentMessageObject) == true) {
						delegate?.textSelectionHelper?.drawCaption(currentMessageObject!!.isOutOwner, captionLayout, this)
					}

					Emoji.emojiDrawingYOffset = -transitionYOffsetForDrawables

					val spoilersColor = if (currentMessageObject!!.isOut && !ChatObject.isChannelAndNotMegaGroup(currentMessageObject!!.chatId, currentAccount)) getThemedColor(Theme.key_chat_outTimeText) else captionLayout.paint.color

					SpoilerEffect.renderWithRipple(this@ChatMessageCell, invalidateSpoilersParent, spoilersColor, 0, captionPatchedSpoilersLayout, captionLayout, captionSpoilers, this, currentMessagesGroup != null)

					Emoji.emojiDrawingYOffset = 0f
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			if (restore != Int.MIN_VALUE) {
				restoreToCount(restore)
			}
		}
	}

	fun needDrawTime(): Boolean {
		return !forceNotDrawTime
	}

	fun shouldDrawTimeOnMedia(): Boolean {
		return if (overrideShouldDrawTimeOnMedia != 0) {
			overrideShouldDrawTimeOnMedia == 1
		}
		else {
			mediaBackground && captionLayout == null && (reactionsLayoutInBubble.isEmpty || reactionsLayoutInBubble.isSmall || currentMessageObject!!.isAnyKindOfSticker || currentMessageObject!!.isRoundVideo)
		}
	}

	fun drawTime(canvas: Canvas, alpha: Float, fromParent: Boolean) {
		if (!drawFromPinchToZoom && delegate?.pinchToZoomHelper?.isInOverlayModeFor(this) == true && shouldDrawTimeOnMedia()) {
			return
		}

		for (i in 0..1) {
			var currentAlpha = alpha

			if (i == 0 && isDrawSelectionBackground() && currentSelectedBackgroundAlpha == 1f && !shouldDrawTimeOnMedia()) {
				continue
			}
			else if (i == 1 && (!isDrawSelectionBackground() && currentSelectedBackgroundAlpha == 0f || shouldDrawTimeOnMedia())) {
				break
			}

			val drawSelectionBackground = i == 1

			if (i == 1) {
				currentAlpha *= currentSelectedBackgroundAlpha
			}
			else if (!shouldDrawTimeOnMedia()) {
				currentAlpha *= 1f - currentSelectedBackgroundAlpha
			}

			if (transitionParams.animateShouldDrawTimeOnMedia && transitionParams.animateChangeProgress != 1f) {
				if (shouldDrawTimeOnMedia()) {
					overrideShouldDrawTimeOnMedia = 1
					drawTimeInternal(canvas, currentAlpha * transitionParams.animateChangeProgress, fromParent, timeX.toFloat(), timeLayout, timeWidth.toFloat(), drawSelectionBackground)
					overrideShouldDrawTimeOnMedia = 2
					drawTimeInternal(canvas, currentAlpha * (1f - transitionParams.animateChangeProgress), fromParent, transitionParams.animateFromTimeX.toFloat(), transitionParams.animateTimeLayout, transitionParams.animateTimeWidth.toFloat(), drawSelectionBackground)
				}
				else {
					overrideShouldDrawTimeOnMedia = 2
					drawTimeInternal(canvas, currentAlpha * transitionParams.animateChangeProgress, fromParent, timeX.toFloat(), timeLayout, timeWidth.toFloat(), drawSelectionBackground)
					overrideShouldDrawTimeOnMedia = 1
					drawTimeInternal(canvas, currentAlpha * (1f - transitionParams.animateChangeProgress), fromParent, transitionParams.animateFromTimeX.toFloat(), transitionParams.animateTimeLayout, transitionParams.animateTimeWidth.toFloat(), drawSelectionBackground)
				}

				overrideShouldDrawTimeOnMedia = 0
			}
			else {
				var timeX: Float
				var timeWidth: Float

				if (transitionParams.shouldAnimateTimeX) {
					timeX = this.timeX * transitionParams.animateChangeProgress + transitionParams.animateFromTimeX * (1f - transitionParams.animateChangeProgress)
					timeWidth = this.timeWidth * transitionParams.animateChangeProgress + transitionParams.animateTimeWidth * (1f - transitionParams.animateChangeProgress)
				}
				else {
					timeX = this.timeX + transitionParams.deltaRight
					timeWidth = this.timeWidth.toFloat()
				}

				drawTimeInternal(canvas, currentAlpha, fromParent, timeX, timeLayout, timeWidth, drawSelectionBackground)
			}
		}

		if (transitionParams.animateBackgroundBoundsInner) {
			drawOverlays(canvas)
		}
	}

	private fun drawTimeInternal(canvas: Canvas, alpha: Float, fromParent: Boolean, timeX: Float, timeLayout: StaticLayout?, timeWidth: Float, drawSelectionBackground: Boolean) {
		@Suppress("NAME_SHADOWING") var alpha = alpha
		@Suppress("NAME_SHADOWING") var timeX = timeX

		if (((!drawTime || groupPhotoInvisible) && shouldDrawTimeOnMedia() || timeLayout == null || currentMessageObject!!.deleted) && currentPosition != null || currentMessageObject!!.type == MessageObject.TYPE_CALL) {
			return
		}

		if (currentMessageObject?.type == MessageObject.TYPE_ROUND_VIDEO) {
			Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
		}
		else {
			if (shouldDrawTimeOnMedia()) {
				if (currentMessageObject!!.shouldDrawWithoutBackground()) {

					Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
				}
				else {
					Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
				}
			}
			else {
				if (currentMessageObject!!.isOutOwner) {
					Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.light_gray_fixed, null)
				}
				else {
					Theme.chat_timePaint.color = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
				}
			}
		}

		if (transitionParams.animateDrawingTimeAlpha) {
			alpha *= transitionParams.animateChangeProgress
		}

		if (alpha != 1f) {
			Theme.chat_timePaint.alpha = (Theme.chat_timePaint.alpha * alpha).toInt()
		}

		canvas.save()

		if (drawPinnedBottom && !shouldDrawTimeOnMedia()) {
			canvas.translate(0f, AndroidUtilities.dp(2f).toFloat())
		}

		var bigRadius = false
		var layoutHeight = layoutHeight + transitionParams.deltaBottom
		var timeTitleTimeX = timeX

		if (transitionParams.shouldAnimateTimeX) {
			timeTitleTimeX = transitionParams.animateFromTimeX * (1f - transitionParams.animateChangeProgress) + this.timeX * transitionParams.animateChangeProgress
		}

		if (currentMessagesGroup != null && currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
			layoutHeight -= translationY
			timeX += currentMessagesGroup!!.transitionParams.offsetRight
			timeTitleTimeX += currentMessagesGroup!!.transitionParams.offsetRight
		}

		if (drawPinnedBottom && shouldDrawTimeOnMedia()) {
			layoutHeight += AndroidUtilities.dp(1f).toFloat()
		}

		if (transitionParams.animateBackgroundBoundsInner) {
			timeX += animationOffsetX
			timeTitleTimeX += animationOffsetX
		}

		if (reactionsLayoutInBubble.isSmall) {
			timeTitleTimeX += if (transitionParams.animateBackgroundBoundsInner && transitionParams.deltaRight != 0f) {
				reactionsLayoutInBubble.getCurrentWidth(1f)
			}
			else {
				reactionsLayoutInBubble.getCurrentWidth(transitionParams.animateChangeProgress)
			}
		}

		if (transitionParams.animateEditedEnter) {
			timeTitleTimeX -= transitionParams.animateEditedWidthDiff * (1f - transitionParams.animateChangeProgress)
		}

		var timeYOffset: Int

		if (shouldDrawTimeOnMedia()) {
			timeYOffset = -if (drawCommentButton) AndroidUtilities.dp(41.3f) else 0

			val paint = if (currentMessageObject!!.shouldDrawWithoutBackground()) {
				Paint().apply { color = context.getColor(R.color.dark_transparent) }
			}
			else {
				getThemedPaint(Theme.key_paint_chatTimeBackground)
			}

			val oldAlpha = paint.alpha

			paint.alpha = (oldAlpha * timeAlpha * alpha).toInt()

			Theme.chat_timePaint.alpha = (255 * timeAlpha * alpha).toInt()

			val r: Int

			if (documentAttachType != DOCUMENT_ATTACH_TYPE_ROUND && documentAttachType != DOCUMENT_ATTACH_TYPE_STICKER && currentMessageObject!!.type != MessageObject.TYPE_EMOJIS) {
				val rad = photoImage.getRoundRadius()
				r = min(AndroidUtilities.dp(12f), max(rad[2], rad[3]))
				bigRadius = SharedConfig.bubbleRadius >= 10
			}
			else {
				r = AndroidUtilities.dp(16f)
			}

			val x1 = timeX - AndroidUtilities.dp(if (bigRadius) 8f else 6f)
			val timeY = photoImage.imageY2 + additionalTimeOffsetY
			val y1 = timeY - AndroidUtilities.dp(23f)

			// MARK: time background is drawn here
			val dec = AndroidUtilities.dp(3f)

			//MARK: the distance for the container in which the time is located on the sides is set here
			val additionalSize = AndroidUtilities.dp(2f)

			val widthIncrease = AndroidUtilities.dp(4f)

			rect.set(x1 + dec - additionalSize, y1, x1 - dec + timeWidth + AndroidUtilities.dp(if (bigRadius) 14f else 10f) + (if (currentMessageObject?.isOutOwner == true) 45f else 0f) + widthIncrease, y1 + AndroidUtilities.dp(17f))
			applyServiceShaderMatrix()

			canvas.drawRoundRect(rect, r.toFloat(), r.toFloat(), paint)

			if (paint === getThemedPaint(Theme.key_paint_chatActionBackground) && hasGradientService()) {
				val oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.alpha
				Theme.chat_actionBackgroundGradientDarkenPaint.alpha = (oldAlpha2 * timeAlpha * alpha).toInt()
				canvas.drawRoundRect(rect, r.toFloat(), r.toFloat(), Theme.chat_actionBackgroundGradientDarkenPaint)
				Theme.chat_actionBackgroundGradientDarkenPaint.alpha = oldAlpha2
			}

			paint.alpha = oldAlpha

			var additionalX = -timeLayout!!.getLineLeft(0)

			if (reactionsLayoutInBubble.isSmall) {
				updateReactionLayoutPosition()
				reactionsLayoutInBubble.draw(canvas, transitionParams.animateChangeProgress, null)
			}

			if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup || currentMessageObject!!.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_HAS_VIEWS != 0 || repliesLayout != null || isPinned) {
				additionalX += this.timeWidth - timeLayout.getLineWidth(0)

				if (reactionsLayoutInBubble.isSmall && !reactionsLayoutInBubble.isEmpty) {
					additionalX -= reactionsLayoutInBubble.width.toFloat()
				}

				var currentStatus = transitionParams.createStatusDrawableParams()

				if (transitionParams.lastStatusDrawableParams >= 0 && transitionParams.lastStatusDrawableParams != currentStatus && !statusDrawableAnimationInProgress) {
					createStatusDrawableAnimator(transitionParams.lastStatusDrawableParams, currentStatus, fromParent)
				}

				if (statusDrawableAnimationInProgress) {
					currentStatus = animateToStatusDrawableParams
				}

				val drawClock = currentStatus and 4 != 0
				val drawError = currentStatus and 8 != 0

				if (statusDrawableAnimationInProgress) {
					val outDrawClock = animateFromStatusDrawableParams and 4 != 0
					val outDrawError = animateFromStatusDrawableParams and 8 != 0

					drawClockOrErrorLayout(canvas, outDrawClock, outDrawError, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f - statusDrawableProgress, drawSelectionBackground)
					drawClockOrErrorLayout(canvas, drawClock, drawError, layoutHeight, alpha, timeYOffset.toFloat(), timeX, statusDrawableProgress, drawSelectionBackground)

					if (!currentMessageObject!!.isOutOwner) {
						if (!outDrawClock && !outDrawError) {
							drawViewsAndRepliesLayout(canvas, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f - statusDrawableProgress, drawSelectionBackground)
						}

						if (!drawClock && !drawError) {
							drawViewsAndRepliesLayout(canvas, layoutHeight, alpha, timeYOffset.toFloat(), timeX, statusDrawableProgress, drawSelectionBackground)
						}
					}
				}
				else {
					if (!currentMessageObject!!.isOutOwner) {
						if (!drawClock && !drawError) {
							drawViewsAndRepliesLayout(canvas, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f, drawSelectionBackground)
						}
					}

					drawClockOrErrorLayout(canvas, drawClock, drawError, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f, drawSelectionBackground)
				}

				if (currentMessageObject!!.isOutOwner) {
					drawViewsAndRepliesLayout(canvas, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f, drawSelectionBackground)
				}

				transitionParams.lastStatusDrawableParams = transitionParams.createStatusDrawableParams()

				if (drawClock && fromParent) {
					(parent as? View)?.invalidate()
				}
			}

			canvas.withTranslation(timeTitleTimeX + additionalX.also { drawTimeX = it }, timeY - AndroidUtilities.dp(7.3f) - timeLayout.height.also { drawTimeY = it.toFloat() }) {
				timeLayout.draw(this)
			}

			Theme.chat_timePaint.alpha = 255
		}
		else {
			if (currentMessageObject!!.isSponsored) {
				timeYOffset = -AndroidUtilities.dp(48f)

				if (hasNewLineForTime) {
					timeYOffset -= AndroidUtilities.dp(16f)
				}
			}
			else {
				timeYOffset = -if (drawCommentButton) AndroidUtilities.dp(43f) else 0
			}

			var additionalX = -timeLayout!!.getLineLeft(0)

			if (reactionsLayoutInBubble.isSmall) {
				updateReactionLayoutPosition()
				reactionsLayoutInBubble.draw(canvas, transitionParams.animateChangeProgress, null)
			}

			if (ChatObject.isChannel(currentChat) && !currentChat!!.megagroup || currentMessageObject!!.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_HAS_VIEWS != 0 || repliesLayout != null || transitionParams.animateReplies || isPinned || transitionParams.animatePinned) {
				additionalX += this.timeWidth - timeLayout.getLineWidth(0)

				if (reactionsLayoutInBubble.isSmall && !reactionsLayoutInBubble.isEmpty) {
					additionalX -= reactionsLayoutInBubble.width.toFloat()
				}

				var currentStatus = transitionParams.createStatusDrawableParams()

				if (transitionParams.lastStatusDrawableParams >= 0 && transitionParams.lastStatusDrawableParams != currentStatus && !statusDrawableAnimationInProgress) {
					createStatusDrawableAnimator(transitionParams.lastStatusDrawableParams, currentStatus, fromParent)
				}

				if (statusDrawableAnimationInProgress) {
					currentStatus = animateToStatusDrawableParams
				}

				val drawClock = currentStatus and 4 != 0
				val drawError = currentStatus and 8 != 0

				if (statusDrawableAnimationInProgress) {
					val outDrawClock = animateFromStatusDrawableParams and 4 != 0
					val outDrawError = animateFromStatusDrawableParams and 8 != 0

					drawClockOrErrorLayout(canvas, outDrawClock, outDrawError, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f - statusDrawableProgress, drawSelectionBackground)
					drawClockOrErrorLayout(canvas, drawClock, drawError, layoutHeight, alpha, timeYOffset.toFloat(), timeX, statusDrawableProgress, drawSelectionBackground)

					if (!currentMessageObject!!.isOutOwner) {
						if (!outDrawClock && !outDrawError) {
							drawViewsAndRepliesLayout(canvas, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f - statusDrawableProgress, drawSelectionBackground)
						}

						if (!drawClock && !drawError) {
							drawViewsAndRepliesLayout(canvas, layoutHeight, alpha, timeYOffset.toFloat(), timeX, statusDrawableProgress, drawSelectionBackground)
						}
					}
				}
				else {
					if (!currentMessageObject!!.isOutOwner) {
						if (!drawClock && !drawError) {
							drawViewsAndRepliesLayout(canvas, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f, drawSelectionBackground)
						}
					}

					drawClockOrErrorLayout(canvas, drawClock, drawError, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f, drawSelectionBackground)
				}

				if (currentMessageObject!!.isOutOwner) {
					drawViewsAndRepliesLayout(canvas, layoutHeight, alpha, timeYOffset.toFloat(), timeX, 1f, drawSelectionBackground)
				}

				transitionParams.lastStatusDrawableParams = transitionParams.createStatusDrawableParams()

				if (drawClock && fromParent && parent != null) {
					(parent as View).invalidate()
				}
			}

			canvas.withSave {
				if (transitionParams.animateEditedEnter && transitionParams.animateChangeProgress != 1f) {
					if (transitionParams.animateEditedLayout != null) {
						translate(timeTitleTimeX + additionalX, layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 7.5f else 6.5f) - timeLayout.height + timeYOffset)

						val oldAlpha = Theme.chat_timePaint.alpha

						Theme.chat_timePaint.alpha = (oldAlpha * transitionParams.animateChangeProgress).toInt()

						transitionParams.animateEditedLayout!!.draw(this)

						Theme.chat_timePaint.alpha = oldAlpha

						transitionParams.animateTimeLayout!!.draw(this)
					}
					else {
						val oldAlpha = Theme.chat_timePaint.alpha

						withTranslation(transitionParams.animateFromTimeX + additionalX, layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 7.5f else 6.5f) - timeLayout.height + timeYOffset) {
							Theme.chat_timePaint.alpha = (oldAlpha * (1f - transitionParams.animateChangeProgress)).toInt()
							transitionParams.animateTimeLayout?.draw(this)
						}

						translate(timeTitleTimeX + additionalX, layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 7.5f else 6.5f) - timeLayout.height + timeYOffset)

						Theme.chat_timePaint.alpha = (oldAlpha * transitionParams.animateChangeProgress).toInt()

						timeLayout.draw(this)

						Theme.chat_timePaint.alpha = oldAlpha
					}
				}
				else {
					val timeTopOffset = if (currentMessageObject?.isMusic == true) AndroidUtilities.dp(4f) else 0f
					translate(timeTitleTimeX + additionalX.also { drawTimeX = it }, layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 7.5f else 6.5f) - timeLayout.height + timeYOffset + timeTopOffset.toFloat().also { drawTimeY = it })
					timeLayout.draw(this)
				}

			}
		}

		if (currentMessageObject!!.isOutOwner) {
			var currentStatus = transitionParams.createStatusDrawableParams()

			if (transitionParams.lastStatusDrawableParams >= 0 && transitionParams.lastStatusDrawableParams != currentStatus && !statusDrawableAnimationInProgress) {
				createStatusDrawableAnimator(transitionParams.lastStatusDrawableParams, currentStatus, fromParent)
			}

			if (statusDrawableAnimationInProgress) {
				currentStatus = animateToStatusDrawableParams
			}

			val drawCheck1 = currentStatus and 1 != 0
			val drawCheck2 = currentStatus and 2 != 0
			val drawClock = currentStatus and 4 != 0
			val drawError = currentStatus and 8 != 0
			var needRestore = false

			if (transitionYOffsetForDrawables != 0f) {
				needRestore = true
				canvas.save()
				canvas.translate(0f, transitionYOffsetForDrawables)
			}

			if (statusDrawableAnimationInProgress) {
				val outDrawCheck1 = animateFromStatusDrawableParams and 1 != 0
				val outDrawCheck2 = animateFromStatusDrawableParams and 2 != 0
				val outDrawClock = animateFromStatusDrawableParams and 4 != 0
				val outDrawError = animateFromStatusDrawableParams and 8 != 0

				if (!outDrawClock && outDrawCheck2 && drawCheck2 && !outDrawCheck1 && drawCheck1) {
					drawStatusDrawable(canvas, drawCheck1 = true, drawCheck2 = true, drawClock = drawClock, drawError = drawError, alpha = alpha, timeYOffset = timeYOffset.toFloat(), layoutHeight = layoutHeight, progress = statusDrawableProgress, moveCheck = true, drawSelectionBackground = drawSelectionBackground)
				}
				else {
					drawStatusDrawable(canvas, outDrawCheck1, outDrawCheck2, outDrawClock, outDrawError, alpha, timeYOffset.toFloat(), layoutHeight, 1f - statusDrawableProgress, false, drawSelectionBackground)
					drawStatusDrawable(canvas, drawCheck1, drawCheck2, drawClock, drawError, alpha, timeYOffset.toFloat(), layoutHeight, statusDrawableProgress, false, drawSelectionBackground)
				}
			}
			else {
				drawStatusDrawable(canvas, drawCheck1, drawCheck2, drawClock, drawError, alpha, timeYOffset.toFloat(), layoutHeight, 1f, false, drawSelectionBackground)
			}

			if (needRestore) {
				canvas.restore()
			}

			transitionParams.lastStatusDrawableParams = transitionParams.createStatusDrawableParams()

			if (fromParent && drawClock) {
				(parent as? View)?.invalidate()
			}
		}

		canvas.restore()

		if (unlockLayout != null) {
			if (unlockX == 0f || unlockY == 0f) {
				calculateUnlockXY()
			}

			unlockSpoilerPath.rewind()

			AndroidUtilities.rectTmp.set(photoImage.imageX, photoImage.imageY, photoImage.imageX2, photoImage.imageY2)

			val photoRadius = photoImage.getRoundRadius()

			unlockSpoilerRadii[1] = photoRadius[0].toFloat()
			unlockSpoilerRadii[0] = unlockSpoilerRadii[1]
			unlockSpoilerRadii[3] = photoRadius[1].toFloat()
			unlockSpoilerRadii[2] = unlockSpoilerRadii[3]
			unlockSpoilerRadii[5] = photoRadius[2].toFloat()
			unlockSpoilerRadii[4] = unlockSpoilerRadii[5]
			unlockSpoilerRadii[7] = photoRadius[3].toFloat()
			unlockSpoilerRadii[6] = unlockSpoilerRadii[7]
			unlockSpoilerPath.addRoundRect(AndroidUtilities.rectTmp, unlockSpoilerRadii, Path.Direction.CW)

			canvas.save()
			canvas.clipPath(unlockSpoilerPath)

			unlockSpoilerPath.rewind()

			AndroidUtilities.rectTmp.set(unlockX - AndroidUtilities.dp(12f), unlockY - AndroidUtilities.dp(8f), unlockX + Theme.chat_msgUnlockDrawable.intrinsicWidth + AndroidUtilities.dp(14f) + unlockLayout!!.width + AndroidUtilities.dp(12f), unlockY + unlockLayout!!.height + AndroidUtilities.dp(8f))

			unlockSpoilerPath.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(32f).toFloat(), AndroidUtilities.dp(32f).toFloat(), Path.Direction.CW)

			canvas.clipPath(unlockSpoilerPath, Region.Op.DIFFERENCE)

			val sColor = ResourcesCompat.getColor(resources, R.color.background, null)

			unlockSpoilerEffect.setColor(ColorUtils.setAlphaComponent(sColor, (Color.alpha(sColor) * 0.325f).toInt()))
			unlockSpoilerEffect.setBounds(photoImage.imageX.toInt(), photoImage.imageY.toInt(), photoImage.imageX2.toInt(), photoImage.imageY2.toInt())
			unlockSpoilerEffect.draw(canvas)

			invalidate()

			canvas.restore()

			val unlockAlpha = 1f
			canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (unlockAlpha * 0xFF).toInt())

			val wasAlpha = Theme.chat_timeBackgroundPaint.alpha

			Theme.chat_timeBackgroundPaint.alpha = (wasAlpha * 0.7f).toInt()

			canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(32f).toFloat(), AndroidUtilities.dp(32f).toFloat(), Theme.chat_timeBackgroundPaint)

			Theme.chat_timeBackgroundPaint.alpha = wasAlpha

			canvas.translate(unlockX + AndroidUtilities.dp(4f), unlockY)

			Theme.chat_msgUnlockDrawable.setBounds(0, 0, Theme.chat_msgUnlockDrawable.intrinsicWidth, Theme.chat_msgUnlockDrawable.intrinsicHeight)
			Theme.chat_msgUnlockDrawable.draw(canvas)

			canvas.translate((AndroidUtilities.dp(6f) + Theme.chat_msgUnlockDrawable.intrinsicWidth).toFloat(), 0f)

			unlockLayout?.draw(canvas)

			canvas.restore()

			if (videoInfoLayout != null && photoImage.visible && imageBackgroundSideColor == 0) {
				val rad: Float

				if (SharedConfig.bubbleRadius > 2) {
					rad = AndroidUtilities.dp((SharedConfig.bubbleRadius - 2).toFloat()).toFloat()
					bigRadius = SharedConfig.bubbleRadius >= 10
				}
				else {
					rad = AndroidUtilities.dp(SharedConfig.bubbleRadius.toFloat()).toFloat()
				}

				val x = (photoImage.imageX + AndroidUtilities.dp(9f)).toInt()
				val y = (photoImage.imageY + AndroidUtilities.dp(6f)).toInt()

				rect.set((x - AndroidUtilities.dp(4f)).toFloat(), (y - AndroidUtilities.dp(1.5f)).toFloat(), (x + durationWidth + AndroidUtilities.dp(4f) + AndroidUtilities.dp(if (bigRadius) 2f else 0f)).toFloat(), (y + videoInfoLayout!!.height + AndroidUtilities.dp(1.5f)).toFloat())

				canvas.drawRoundRect(rect, rad, rad, getThemedPaint(Theme.key_paint_chatTimeBackground))

				canvas.withTranslation((x + if (bigRadius) 2 else 0).toFloat(), y.toFloat()) {
					videoInfoLayout?.draw(this)
				}
			}
		}
	}

	private fun createStatusDrawableAnimator(lastStatusDrawableParams: Int, currentStatus: Int, fromParent: Boolean) {
		val drawCheck1 = currentStatus and 1 != 0
		val drawCheck2 = currentStatus and 2 != 0
		val outDrawCheck1 = lastStatusDrawableParams and 1 != 0
		val outDrawCheck2 = lastStatusDrawableParams and 2 != 0
		val outDrawClock = lastStatusDrawableParams and 4 != 0
		val moveCheckTransition = !outDrawClock && outDrawCheck2 && drawCheck2 && !outDrawCheck1 && drawCheck1

		if (transitionParams.messageEntering && !moveCheckTransition) {
			return
		}

		statusDrawableProgress = 0f
		statusDrawableAnimator = ValueAnimator.ofFloat(0f, 1f)

		if (moveCheckTransition) {
			statusDrawableAnimator?.duration = 220
		}
		else {
			statusDrawableAnimator?.duration = 150
		}

		statusDrawableAnimator?.interpolator = CubicBezierInterpolator.DEFAULT

		animateFromStatusDrawableParams = lastStatusDrawableParams
		animateToStatusDrawableParams = currentStatus

		statusDrawableAnimator?.addUpdateListener {
			statusDrawableProgress = it.animatedValue as Float

			invalidate()

			if (fromParent) {
				(parent as? View)?.invalidate()
			}
		}

		statusDrawableAnimator?.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				@Suppress("NAME_SHADOWING") val currentStatus = transitionParams.createStatusDrawableParams()

				if (animateToStatusDrawableParams != currentStatus) {
					createStatusDrawableAnimator(animateToStatusDrawableParams, currentStatus, fromParent)
				}
				else {
					statusDrawableAnimationInProgress = false
					transitionParams.lastStatusDrawableParams = animateToStatusDrawableParams
				}
			}
		})

		statusDrawableAnimationInProgress = true

		statusDrawableAnimator?.start()
	}

	private fun drawClockOrErrorLayout(canvas: Canvas, drawTime: Boolean, drawError: Boolean, layoutHeight: Float, alpha: Float, timeYOffset: Float, timeX: Float, progress: Float, drawSelectionBackground: Boolean) {
		@Suppress("NAME_SHADOWING") var alpha = alpha
		val useScale = progress != 1f
		val scale = 0.5f + 0.5f * progress

		alpha *= progress

		if (drawTime) {
			if (!currentMessageObject!!.isOutOwner) {
				val clockDrawable = Theme.chat_msgClockDrawable

				val clockColor = if (shouldDrawTimeOnMedia()) {
					getThemedColor(Theme.key_chat_mediaSentClock)
				}
				else {
					getThemedColor(if (drawSelectionBackground) Theme.key_chat_outSentClockSelected else Theme.key_chat_mediaSentClock)
				}

				clockDrawable.setColor(clockColor)

				val timeY = if (shouldDrawTimeOnMedia()) photoImage.imageY2 + additionalTimeOffsetY - AndroidUtilities.dp(9.0f) else layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 9.5f else 8.5f) + timeYOffset

				setDrawableBounds(clockDrawable, timeX + if (currentMessageObject!!.scheduled) 0 else AndroidUtilities.dp(11f), timeY - clockDrawable.intrinsicHeight)

				clockDrawable.alpha = (255 * alpha).toInt()

				if (useScale) {
					canvas.save()
					canvas.scale(scale, scale, clockDrawable.bounds.centerX().toFloat(), clockDrawable.bounds.centerY().toFloat())
				}

				clockDrawable.draw(canvas)
				clockDrawable.alpha = 255

				invalidate()

				if (useScale) {
					canvas.restore()
				}
			}
		}
		else if (drawError) {
			if (!currentMessageObject!!.isOutOwner) {
				val x = timeX + if (currentMessageObject!!.scheduled) 0 else AndroidUtilities.dp(11f)
				val y = if (shouldDrawTimeOnMedia()) photoImage.imageY2 + additionalTimeOffsetY - AndroidUtilities.dp(21.5f) else layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 21.5f else 20.5f) + timeYOffset

				rect.set(x, y, x + AndroidUtilities.dp(14f), y + AndroidUtilities.dp(14f))

				val oldAlpha = Theme.chat_msgErrorPaint.alpha

				Theme.chat_msgErrorPaint.alpha = (255 * alpha).toInt()

				if (useScale) {
					canvas.save()
					canvas.scale(scale, scale, rect.centerX(), rect.centerY())
				}

				canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), Theme.chat_msgErrorPaint)

				Theme.chat_msgErrorPaint.alpha = oldAlpha

				val errorDrawable = getThemedDrawable(Theme.key_drawable_msgError)

				setDrawableBounds(errorDrawable, x + AndroidUtilities.dp(6f), y + AndroidUtilities.dp(2f))

				errorDrawable.alpha = (255 * alpha).toInt()
				errorDrawable.draw(canvas)
				errorDrawable.alpha = 255

				if (useScale) {
					canvas.restore()
				}
			}
		}
	}

	private fun drawViewsAndRepliesLayout(canvas: Canvas, layoutHeight: Float, alpha: Float, timeYOffset: Float, timeX: Float, progress: Float, drawSelectionBackground: Boolean) {
		@Suppress("NAME_SHADOWING") var alpha = alpha
		val useScale = progress != 1f
		val scale = 0.5f + 0.5f * progress

		alpha *= progress

		var offsetX: Float = if (reactionsLayoutInBubble.isSmall) reactionsLayoutInBubble.getCurrentWidth(1f) else 0f
		val timeAlpha = Theme.chat_timePaint.alpha
		val topOffsetViewsCount = if (currentMessageObject?.isMusic == true) AndroidUtilities.dp(4f) else 0f
		val timeY = if (shouldDrawTimeOnMedia()) photoImage.imageY2 + additionalTimeOffsetY - AndroidUtilities.dp(7.3f) - timeLayout!!.height else layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 7.5f else 6.5f) - timeLayout!!.height + timeYOffset + topOffsetViewsCount.toFloat()

		if (repliesLayout != null || transitionParams.animateReplies) {
			var repliesX = (if (transitionParams.shouldAnimateTimeX) this.timeX.toFloat() else timeX) + offsetX
			val inAnimation = transitionParams.animateReplies && transitionParams.animateRepliesLayout == null && repliesLayout != null
			val outAnimation = transitionParams.animateReplies && transitionParams.animateRepliesLayout != null && repliesLayout == null
			val replaceAnimation = transitionParams.animateReplies && transitionParams.animateRepliesLayout != null && repliesLayout != null

			if (transitionParams.shouldAnimateTimeX && !inAnimation) {
				repliesX = if (outAnimation) {
					transitionParams.animateFromTimeXReplies
				}
				else {
					transitionParams.animateFromTimeXReplies * (1f - transitionParams.animateChangeProgress) + repliesX * transitionParams.animateChangeProgress
				}
			}
			else {
				repliesX += transitionParams.deltaRight
			}

			if (currentMessagesGroup != null && currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
				repliesX += currentMessagesGroup!!.transitionParams.offsetRight
			}

			if (transitionParams.animateBackgroundBoundsInner) {
				repliesX += animationOffsetX
			}

			val repliesDrawable = if (shouldDrawTimeOnMedia()) {
				if (currentMessageObject!!.shouldDrawWithoutBackground()) {
					getThemedDrawable(Theme.key_drawable_msgStickerReplies)
				}
				else {
					Theme.chat_msgMediaRepliesDrawable
				}
			}
			else {
				if (!currentMessageObject!!.isOutOwner) {
					if (drawSelectionBackground) Theme.chat_msgInRepliesSelectedDrawable else Theme.chat_msgInRepliesDrawable
				}
				else {
					getThemedDrawable(if (drawSelectionBackground) Theme.key_drawable_msgOutRepliesSelected else Theme.key_drawable_msgOutReplies)
				}
			}

			setDrawableBounds(repliesDrawable, repliesX, timeY)

			var repliesAlpha = alpha

			if (inAnimation) {
				repliesAlpha *= transitionParams.animateChangeProgress
			}
			else if (outAnimation) {
				repliesAlpha *= 1f - transitionParams.animateChangeProgress
			}

			repliesDrawable.alpha = (255 * repliesAlpha).toInt()

			if (useScale) {
				canvas.save()

				val cx = repliesX + (repliesDrawable.intrinsicWidth + AndroidUtilities.dp(3f) + repliesTextWidth) / 2f

				canvas.scale(scale, scale, cx, repliesDrawable.bounds.centerY().toFloat())
			}

			repliesDrawable.draw(canvas)
			repliesDrawable.alpha = 255

			if (transitionParams.animateReplies) {
				if (replaceAnimation) {
					canvas.withSave {
						Theme.chat_timePaint.alpha = (timeAlpha * (1.0 - transitionParams.animateChangeProgress)).toInt()
						translate(repliesX + repliesDrawable.intrinsicWidth + AndroidUtilities.dp(3f), timeY)
						transitionParams.animateRepliesLayout!!.draw(this)
					}
				}

				Theme.chat_timePaint.alpha = (timeAlpha * repliesAlpha).toInt()
			}

			canvas.save()
			canvas.translate(repliesX + repliesDrawable.intrinsicWidth + AndroidUtilities.dp(3f), timeY)

			repliesLayout?.draw(canvas) ?: transitionParams.animateRepliesLayout?.draw(canvas)

			canvas.restore()

			if (repliesLayout != null) {
				offsetX += (repliesDrawable.intrinsicWidth + repliesTextWidth + AndroidUtilities.dp(10f)).toFloat()
			}

			if (useScale) {
				canvas.restore()
			}

			if (transitionParams.animateReplies) {
				Theme.chat_timePaint.alpha = timeAlpha
			}

			transitionParams.lastTimeXReplies = repliesX
		}

		if (viewsLayout != null) {
			var viewsX = (if (transitionParams.shouldAnimateTimeX) this.timeX.toFloat() else timeX) + offsetX

			if (transitionParams.shouldAnimateTimeX) {
				viewsX = transitionParams.animateFromTimeXViews * (1f - transitionParams.animateChangeProgress) + viewsX * transitionParams.animateChangeProgress
			}
			else {
				viewsX += transitionParams.deltaRight
			}

			if (currentMessagesGroup != null && currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
				viewsX += currentMessagesGroup!!.transitionParams.offsetRight
			}

			if (transitionParams.animateBackgroundBoundsInner) {
				viewsX += animationOffsetX
			}

			val viewsDrawable = if (shouldDrawTimeOnMedia()) {
				if (currentMessageObject?.shouldDrawWithoutBackground() == true) {
					getThemedDrawable(Theme.key_drawable_msgStickerViews)
				}
				else {
					Theme.chat_msgMediaViewsDrawable
				}
			}
			else {
				if (currentMessageObject?.isOutOwner != true) {
					if (drawSelectionBackground) {
						Theme.chat_msgInViewsSelectedDrawable
					}
					else {
						ResourcesCompat.getDrawable(context.resources, R.drawable.msg_views, null)!!.mutate().apply {
							colorFilter = PorterDuffColorFilter(context.getColor(R.color.dark_gray), PorterDuff.Mode.MULTIPLY)
						}
					}
				}
				else {
					ResourcesCompat.getDrawable(context.resources, R.drawable.msg_views, null)!!.mutate().apply {
						colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY)
					}

					// getThemedDrawable(if (drawSelectionBackground) Theme.key_drawable_msgOutViewsSelected else Theme.key_drawable_msgOutViews)
				}
			}

			val topOffset = if (currentMessageObject?.isMusic == true) AndroidUtilities.dp(5f) else 0f
			val y = if (shouldDrawTimeOnMedia()) photoImage.imageY2 + additionalTimeOffsetY - AndroidUtilities.dp(5.5f) - timeLayout!!.height + topOffset.toFloat() else layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 5.5f else 4.5f) - timeLayout!!.height + timeYOffset + topOffset.toFloat()

			setDrawableBounds(viewsDrawable, viewsX, y)

			if (useScale) {
				canvas.save()
				val cx = viewsX + (viewsDrawable.intrinsicWidth + AndroidUtilities.dp(3f) + viewsTextWidth) / 2f
				canvas.scale(scale, scale, cx, viewsDrawable.bounds.centerY().toFloat())
			}

			viewsDrawable.alpha = (255 * alpha).toInt()
			viewsDrawable.draw(canvas)
			viewsDrawable.alpha = 255

			if (transitionParams.animateViewsLayout != null) {
				canvas.withSave {
					Theme.chat_timePaint.alpha = (timeAlpha * (1.0 - transitionParams.animateChangeProgress)).toInt()
					translate(viewsX + viewsDrawable.intrinsicWidth + AndroidUtilities.dp(3f), timeY)
					transitionParams.animateViewsLayout?.draw(this)
				}

				Theme.chat_timePaint.alpha = (timeAlpha * transitionParams.animateChangeProgress).toInt()
			}

			canvas.save()
			canvas.translate(viewsX + viewsDrawable.intrinsicWidth + AndroidUtilities.dp(3f), timeY)

			viewsLayout?.draw(canvas)

			canvas.restore()

			if (useScale) {
				canvas.restore()
			}

			offsetX += (viewsTextWidth + Theme.chat_msgInViewsDrawable.intrinsicWidth + AndroidUtilities.dp(10f)).toFloat()

			if (transitionParams.animateViewsLayout != null) {
				Theme.chat_timePaint.alpha = timeAlpha
			}

			transitionParams.lastTimeXViews = viewsX
		}

		if (isPinned || transitionParams.animatePinned) {
			var pinnedX = (if (transitionParams.shouldAnimateTimeX) this.timeX.toFloat() else timeX) + offsetX
			val inAnimation = transitionParams.animatePinned && isPinned
			val outAnimation = transitionParams.animatePinned && !isPinned

			if (transitionParams.shouldAnimateTimeX && !inAnimation) {
				pinnedX = if (outAnimation) {
					transitionParams.animateFromTimeXPinned
				}
				else {
					transitionParams.animateFromTimeXPinned * (1f - transitionParams.animateChangeProgress) + pinnedX * transitionParams.animateChangeProgress
				}
			}
			else {
				pinnedX += transitionParams.deltaRight
			}

			if (currentMessagesGroup != null && currentMessagesGroup!!.transitionParams.backgroundChangeBounds) {
				pinnedX += currentMessagesGroup!!.transitionParams.offsetRight
			}

			if (transitionParams.animateBackgroundBoundsInner) {
				pinnedX += animationOffsetX
			}

			val pinnedDrawable = if (shouldDrawTimeOnMedia()) {
				if (currentMessageObject!!.shouldDrawWithoutBackground()) {
					getThemedDrawable(Theme.key_drawable_msgStickerPinned)
				}
				else {
					Theme.chat_msgMediaPinnedDrawable
				}
			}
			else {
				if (!currentMessageObject!!.isOutOwner) {
					if (drawSelectionBackground) Theme.chat_msgInPinnedSelectedDrawable else Theme.chat_msgInPinnedDrawable
				}
				else {
					getThemedDrawable(if (drawSelectionBackground) Theme.key_drawable_msgOutPinnedSelected else Theme.key_drawable_msgOutPinned)
				}
			}

			if (transitionParams.animatePinned) {
				if (isPinned) {
					pinnedDrawable.alpha = (255 * alpha * transitionParams.animateChangeProgress).toInt()
					setDrawableBounds(pinnedDrawable, pinnedX, timeY)
				}
				else {
					pinnedDrawable.alpha = (255 * alpha * (1.0f - transitionParams.animateChangeProgress)).toInt()
					setDrawableBounds(pinnedDrawable, pinnedX, timeY)
				}
			}
			else {
				pinnedDrawable.alpha = (255 * alpha).toInt()
				setDrawableBounds(pinnedDrawable, pinnedX, timeY)
			}

			if (useScale) {
				canvas.save()
				val cx = pinnedX + pinnedDrawable.intrinsicWidth / 2f
				canvas.scale(scale, scale, cx, pinnedDrawable.bounds.centerY().toFloat())
			}

			pinnedDrawable.draw(canvas)
			pinnedDrawable.alpha = 255

			if (useScale) {
				canvas.restore()
			}

			transitionParams.lastTimeXPinned = pinnedX
		}
	}

	private fun drawStatusDrawable(canvas: Canvas, drawCheck1: Boolean, drawCheck2: Boolean, drawClock: Boolean, drawError: Boolean, alpha: Float, timeYOffset: Float, layoutHeight: Float, progress: Float, moveCheck: Boolean, drawSelectionBackground: Boolean) {
		@Suppress("NAME_SHADOWING") var alpha = alpha
		val useScale = progress != 1f && !moveCheck
		val scale = 0.5f + 0.5f * progress

		if (useScale) {
			alpha *= progress
		}

		val timeY = photoImage.imageY2 + additionalTimeOffsetY - AndroidUtilities.dp(8.5f)

		if (drawClock) {
			val drawable = Theme.chat_msgClockDrawable
			val color: Int

			if (shouldDrawTimeOnMedia()) {
				color = context.getColor(R.color.white)
				setDrawableBounds(drawable, (timeX + timeWidth + AndroidUtilities.dp(4f)).toFloat(), timeY - drawable.intrinsicHeight + timeYOffset)

				if (currentMessageObject!!.shouldDrawWithoutBackground()) {
					drawable.alpha = (255 * timeAlpha * alpha).toInt()
				}
				else {
					drawable.alpha = (255 * alpha).toInt()
				}
			}
			else {
				color = context.getColor(R.color.brand)
				setDrawableBounds(drawable, (timeX + timeWidth + AndroidUtilities.dp(4f)).toFloat(), layoutHeight - AndroidUtilities.dp(8.5f) - drawable.intrinsicHeight + timeYOffset)
				drawable.alpha = (255 * alpha).toInt()
			}

			drawable.setColor(color)

			if (useScale) {
				canvas.save()
				canvas.scale(scale, scale, drawable.bounds.centerX().toFloat(), drawable.bounds.centerY().toFloat())
			}

			drawable.draw(canvas)
			drawable.alpha = 255

			if (useScale) {
				canvas.restore()
			}

			invalidate()
		}

		// MARK: delivered/viewed statuses are drawn here
		if (drawCheck2) {
			if (shouldDrawTimeOnMedia()) {
				val drawable: Drawable

				if (moveCheck) {
					canvas.save()
				}

				if (currentMessageObject!!.shouldDrawWithoutBackground()) {
					drawable = getThemedDrawable(Theme.key_drawable_msgStickerCheck)

					if (drawCheck1) {
						if (moveCheck) {
							canvas.translate(AndroidUtilities.dp(4.8f) * (1f - progress), 0f)
						}

						setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(26.3f).toFloat() - drawable.intrinsicWidth, timeY - drawable.intrinsicHeight + timeYOffset)
					}
					else {
						setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(21.5f).toFloat() - drawable.intrinsicWidth, timeY - drawable.intrinsicHeight + timeYOffset)
					}

					drawable.alpha = (255 * timeAlpha * alpha).toInt()
				}
				else {
					if (drawCheck1) {
						if (moveCheck) {
							canvas.translate(AndroidUtilities.dp(4.8f) * (1f - progress), 0f)
						}

						setDrawableBounds(Theme.chat_msgMediaCheckDrawable, layoutWidth - AndroidUtilities.dp(26.3f).toFloat() - Theme.chat_msgMediaCheckDrawable.intrinsicWidth, timeY - Theme.chat_msgMediaCheckDrawable.intrinsicHeight + timeYOffset)
					}
					else {
						setDrawableBounds(Theme.chat_msgMediaCheckDrawable, layoutWidth - AndroidUtilities.dp(21.5f).toFloat() - Theme.chat_msgMediaCheckDrawable.intrinsicWidth, timeY - Theme.chat_msgMediaCheckDrawable.intrinsicHeight + timeYOffset)
					}

					Theme.chat_msgMediaCheckDrawable.alpha = (255 * timeAlpha * alpha).toInt()

					drawable = Theme.chat_msgMediaCheckDrawable
				}

				if (useScale) {
					canvas.save()
					canvas.scale(scale, scale, drawable.bounds.centerX().toFloat(), drawable.bounds.centerY().toFloat())
				}

				drawable.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN) // MARK: Color white or brand? That's the question
				drawable.draw(canvas)

				if (useScale) {
					canvas.restore()
				}

				if (moveCheck) {
					canvas.restore()
				}

				drawable.alpha = 255
			}
			else {
				val drawable: Drawable

				if (moveCheck) {
					canvas.save()
				}

				if (drawCheck1) {
					if (moveCheck) {
						canvas.translate(AndroidUtilities.dp(4f) * (1f - progress), 0f)
					}

					drawable = getThemedDrawable(if (drawSelectionBackground) Theme.key_drawable_msgOutCheckReadSelected else Theme.key_drawable_msgOutCheckRead)

					setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(22.5f).toFloat() - drawable.intrinsicWidth, layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 9f else 8f) - drawable.intrinsicHeight + timeYOffset)
				}
				else {
					drawable = getThemedDrawable(if (drawSelectionBackground) Theme.key_drawable_msgOutCheckSelected else Theme.key_drawable_msgOutCheck)

					setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(18.5f).toFloat() - drawable.intrinsicWidth, layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 9f else 8f) - drawable.intrinsicHeight + timeYOffset)
				}

				drawable.alpha = (255 * alpha).toInt()

				if (useScale) {
					canvas.save()
					canvas.scale(scale, scale, drawable.bounds.centerX().toFloat(), drawable.bounds.centerY().toFloat())
				}

				drawable.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN)
				drawable.draw(canvas)

				if (useScale) {
					canvas.restore()
				}

				if (moveCheck) {
					canvas.restore()
				}

				drawable.alpha = 255
			}
		}

		if (drawCheck1) {
			if (shouldDrawTimeOnMedia()) {
				val drawable = if (currentMessageObject!!.shouldDrawWithoutBackground()) getThemedDrawable(Theme.key_drawable_msgStickerHalfCheck) else Theme.chat_msgMediaHalfCheckDrawable

				setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(23.5f).toFloat() - drawable.intrinsicWidth, timeY - drawable.intrinsicHeight + timeYOffset)

				drawable.alpha = (255 * timeAlpha * alpha).toInt()

				if (useScale || moveCheck) {
					canvas.save()
					canvas.scale(scale, scale, drawable.bounds.centerX().toFloat(), drawable.bounds.centerY().toFloat())
				}

				drawable.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN)
				drawable.draw(canvas)

				if (useScale || moveCheck) {
					canvas.restore()
				}

				drawable.alpha = 255
			}
			else {
				val drawable = getThemedDrawable(if (drawSelectionBackground) Theme.key_drawable_msgOutHalfCheckSelected else Theme.key_drawable_msgOutHalfCheck)

				setDrawableBounds(drawable, layoutWidth - AndroidUtilities.dp(18f).toFloat() - drawable.intrinsicWidth, layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 9f else 8f) - drawable.intrinsicHeight + timeYOffset)

				drawable.alpha = (255 * alpha).toInt()

				if (useScale || moveCheck) {
					canvas.save()
					canvas.scale(scale, scale, drawable.bounds.centerX().toFloat(), drawable.bounds.centerY().toFloat())
				}

				drawable.colorFilter = PorterDuffColorFilter(context.getColor(R.color.white), PorterDuff.Mode.SRC_IN)
				drawable.draw(canvas)

				if (useScale || moveCheck) {
					canvas.restore()
				}

				drawable.alpha = 255
			}
		}

		if (drawError) {
			// MARK: sending messages errors are shown here

			val x = backgroundDrawableLeft - AndroidUtilities.dp(15f)
			val y: Float = layoutHeight - AndroidUtilities.dp(18f)

//			if (shouldDrawTimeOnMedia()) {
//				x = layoutWidth - AndroidUtilities.dp(34.5f)
//				y = layoutHeight - AndroidUtilities.dp(26.5f) + timeYOffset
//			}
//			else {
//				x = layoutWidth - AndroidUtilities.dp(32f)
//				y = layoutHeight - AndroidUtilities.dp(if (isPinnedBottom || isPinnedTop) 22f else 21f) + timeYOffset
//			}

			rect.set(x.toFloat(), y, (x + AndroidUtilities.dp(14f)).toFloat(), y + AndroidUtilities.dp(14f))

			val oldAlpha = Theme.chat_msgErrorPaint.alpha

			Theme.chat_msgErrorPaint.alpha = (oldAlpha * alpha).toInt()

			canvas.drawCircle(rect.centerX(), rect.centerY(), AndroidUtilities.dp(7f).toFloat(), Theme.chat_msgErrorPaint)
			// canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), Theme.chat_msgErrorPaint)

			Theme.chat_msgErrorPaint.alpha = oldAlpha

			setDrawableBounds(Theme.chat_msgErrorDrawable, (x + AndroidUtilities.dp(6f)).toFloat(), y + AndroidUtilities.dp(2f))

			Theme.chat_msgErrorDrawable.alpha = (255 * alpha).toInt()

			if (useScale) {
				canvas.save()
				canvas.scale(scale, scale, Theme.chat_msgErrorDrawable.bounds.centerX().toFloat(), Theme.chat_msgErrorDrawable.bounds.centerY().toFloat())
			}

			Theme.chat_msgErrorDrawable.draw(canvas)
			Theme.chat_msgErrorDrawable.alpha = 255

			if (useScale) {
				canvas.restore()
			}
		}
	}

	fun drawOverlays(canvas: Canvas) {
		if (!drawFromPinchToZoom && delegate?.pinchToZoomHelper?.isInOverlayModeFor(this) == true) {
			return
		}

		val newAnimationTime = SystemClock.elapsedRealtime()
		var animationDt = newAnimationTime - lastAnimationTime

		if (animationDt > 17) {
			animationDt = 17
		}

		lastAnimationTime = newAnimationTime

		if (currentMessageObject!!.hadAnimationNotReadyLoading && photoImage.visible && !currentMessageObject!!.needDrawBluredPreview() && (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF)) {
			val animation = photoImage.animation

			if (animation != null && animation.hasBitmap()) {
				currentMessageObject?.hadAnimationNotReadyLoading = false
				updateButtonState(ifSame = false, animated = true, fromSet = false)
			}
		}

		if (hasGamePreview) {
			// unused
		}
		else if (currentMessageObject!!.type == MessageObject.TYPE_VIDEO || currentMessageObject!!.type == MessageObject.TYPE_PHOTO || currentMessageObject!!.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
			if (photoImage.visible) {
				if (!currentMessageObject!!.needDrawBluredPreview()) {
					if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
						val oldAlpha = (Theme.chat_msgMediaMenuDrawable as BitmapDrawable).paint.alpha

						if (drawMediaCheckBox) {
							Theme.chat_msgMediaMenuDrawable.alpha = (oldAlpha * controlsAlpha * (1.0f - checkBoxAnimationProgress)).toInt()
						}
						else {
							Theme.chat_msgMediaMenuDrawable.alpha = (oldAlpha * controlsAlpha).toInt()
						}

						setDrawableBounds(Theme.chat_msgMediaMenuDrawable, (photoImage.imageX + photoImage.imageWidth - AndroidUtilities.dp(14f)).toInt().also { otherX = it }, (photoImage.imageY + AndroidUtilities.dp(8.1f)).toInt().also { otherY = it })

						Theme.chat_msgMediaMenuDrawable.draw(canvas)
						Theme.chat_msgMediaMenuDrawable.alpha = oldAlpha
					}
				}

				val playing = MediaController.getInstance().isPlayingMessage(currentMessageObject)

				if (animatingNoSoundPlaying != playing) {
					animatingNoSoundPlaying = playing
					animatingNoSound = if (playing) 1 else 2
					animatingNoSoundProgress = if (playing) 1.0f else 0.0f
				}

				var fullWidth = true

				if (currentPosition != null) {
					val mask = MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT
					fullWidth = currentPosition!!.flags and mask == mask
				}

				if ((documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) && (buttonState == 1 || buttonState == 2 || buttonState == 0 || buttonState == 3 || buttonState == -1 || currentMessageObject!!.needDrawBluredPreview())) {
					if (autoPlayingMedia) {
						updatePlayingMessageProgress()
					}

					if ((infoLayout != null || loadingProgressLayout != null) && ((!forceNotDrawTime || autoPlayingMedia || drawVideoImageButton || animatingLoadingProgressProgress != 0f || fullWidth) && docTitleLayout != null || loadingProgressLayout != null && currentPosition != null) && (buttonState == 1 || buttonState == 3) && miniButtonState == 1) {
						val drawLoadingProgress: Boolean
						var alpha = 0f
						val drawDocTitleLayout: Boolean
						var loadingProgressAlpha = 1f

						if (!fullWidth) {
							drawLoadingProgress = true
							drawDocTitleLayout = false
							loadingProgressAlpha = animatingLoadingProgressProgress
						}
						else {
							drawLoadingProgress = (buttonState == 1 || miniButtonState == 1 || animatingLoadingProgressProgress != 0f) && !currentMessageObject!!.isSecretMedia && (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT)

							if (currentMessageObject!!.type == MessageObject.TYPE_VIDEO || currentMessageObject!!.type == MessageObject.TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
								alpha = if (currentMessageObject!!.needDrawBluredPreview() && docTitleLayout == null) 0f else animatingDrawVideoImageButtonProgress
							}

							drawDocTitleLayout = alpha > 0 && docTitleLayout != null

							if (!drawDocTitleLayout && (drawLoadingProgress || infoLayout == null)) {
								loadingProgressAlpha = animatingLoadingProgressProgress
							}
						}

						var bigRadius = false

						if (documentAttachType != DOCUMENT_ATTACH_TYPE_ROUND && documentAttachType != DOCUMENT_ATTACH_TYPE_STICKER && currentMessageObject!!.type != MessageObject.TYPE_EMOJIS) {
							bigRadius = SharedConfig.bubbleRadius >= 10
						}

						Theme.chat_infoPaint.color = ResourcesCompat.getColor(resources, R.color.white, null) // getThemedColor(Theme.key_chat_mediaInfoText)

						val x1 = (photoImage.imageX + AndroidUtilities.dp(4f)).toInt()
						val y1 = (photoImage.imageY + AndroidUtilities.dp(4f)).toInt()
						var imageW: Int
						val infoW: Int

						imageW = if (autoPlayingMedia && (!playing || animatingNoSound != 0)) {
							((Theme.chat_msgNoSoundDrawable.intrinsicWidth + AndroidUtilities.dp(4f)) * animatingNoSoundProgress).toInt()
						}
						else {
							0
						}

						if (drawLoadingProgress && loadingProgressLayout != null) {
							imageW = 0
							infoW = loadingProgressLayout!!.getLineWidth(0).toInt()
						}
						else {
							infoW = infoWidth
						}

						val w = ceil((infoW + AndroidUtilities.dp(if (bigRadius) 12f else 8f) + imageW + (max(infoW + if (infoWidth == infoW) imageW else 0, docTitleWidth) + if (canStreamVideo) AndroidUtilities.dp(32f) else 0 - infoW - imageW) * alpha).toDouble()).toInt()

						if (alpha != 0f && docTitleLayout == null) {
							alpha = 0f
						}

						canvas.save()
						canvas.scale(loadingProgressAlpha, loadingProgressAlpha, x1.toFloat(), y1.toFloat())

						val oldAlpha = getThemedPaint(Theme.key_paint_chatTimeBackground).alpha

						getThemedPaint(Theme.key_paint_chatTimeBackground).alpha = (oldAlpha * controlsAlpha * loadingProgressAlpha).toInt()

						if ((drawDocTitleLayout || drawLoadingProgress && loadingProgressLayout != null || !drawLoadingProgress) && infoLayout != null) {
							rect.set(x1.toFloat(), y1.toFloat(), (x1 + w).toFloat(), (y1 + AndroidUtilities.dp(16.5f + 15.5f * alpha)).toFloat())

							val rad = photoImage.getRoundRadius()
							val r = min(AndroidUtilities.dp(8f), max(rad[0], rad[1]))

							canvas.drawRoundRect(rect, r.toFloat(), r.toFloat(), getThemedPaint(Theme.key_paint_chatTimeBackground))
						}

						Theme.chat_infoPaint.alpha = (255 * controlsAlpha * loadingProgressAlpha).toInt()

						canvas.translate((photoImage.imageX + AndroidUtilities.dp((if (bigRadius) 10f else 8f) + if (canStreamVideo) 30 * alpha else 0f)).toInt().also { noSoundIconCenterX = it }.toFloat(), photoImage.imageY + AndroidUtilities.dp(5.5f + 0.2f * alpha))

						if (infoLayout != null && (!drawLoadingProgress || drawDocTitleLayout)) {
							infoLayout?.draw(canvas)
						}

						if (imageW != 0 && (!drawLoadingProgress || drawDocTitleLayout)) {
							canvas.withSave {
								Theme.chat_msgNoSoundDrawable.alpha = (255 * animatingNoSoundProgress * animatingNoSoundProgress * controlsAlpha).toInt()

								val size = AndroidUtilities.dp(14 * animatingNoSoundProgress)
								val y = (AndroidUtilities.dp(14f) - size) / 2
								val offset = infoWidth + AndroidUtilities.dp(4f)

								translate(offset.toFloat(), 0f)

								Theme.chat_msgNoSoundDrawable.setBounds(0, y, size, y + size)
								Theme.chat_msgNoSoundDrawable.draw(this)

								noSoundIconCenterX += offset + size / 2
							}
						}

						if (drawLoadingProgress && loadingProgressLayout != null) {
							canvas.withSave() {
								if (drawDocTitleLayout) {
									Theme.chat_infoPaint.alpha = (255 * controlsAlpha * alpha).toInt()
									translate(0f, AndroidUtilities.dp(14.3f * alpha).toFloat())
								}

								loadingProgressLayout?.draw(this)
							}
						}
						else if (drawDocTitleLayout) {
							Theme.chat_infoPaint.alpha = (255 * controlsAlpha * alpha).toInt()
							canvas.translate(0f, AndroidUtilities.dp(14.3f * alpha).toFloat())
							docTitleLayout?.draw(canvas)
						}


						canvas.restore()

						Theme.chat_infoPaint.alpha = 255

						getThemedPaint(Theme.key_paint_chatTimeBackground).alpha = oldAlpha
					}
				}

				if (animatingDrawVideoImageButton == 1) {
					animatingDrawVideoImageButtonProgress -= animationDt / 160.0f

					if (animatingDrawVideoImageButtonProgress <= 0) {
						animatingDrawVideoImageButtonProgress = 0f
						animatingDrawVideoImageButton = 0
					}

					invalidate()
				}
				else if (animatingDrawVideoImageButton == 2) {
					animatingDrawVideoImageButtonProgress += animationDt / 160.0f

					if (animatingDrawVideoImageButtonProgress >= 1) {
						animatingDrawVideoImageButtonProgress = 1f
						animatingDrawVideoImageButton = 0
					}

					invalidate()
				}
				if (animatingNoSound == 1) {
					animatingNoSoundProgress -= animationDt / 180.0f

					if (animatingNoSoundProgress <= 0.0f) {
						animatingNoSoundProgress = 0.0f
						animatingNoSound = 0
					}

					invalidate()
				}
				else if (animatingNoSound == 2) {
					animatingNoSoundProgress += animationDt / 180.0f

					if (animatingNoSoundProgress >= 1.0f) {
						animatingNoSoundProgress = 1.0f
						animatingNoSound = 0
					}

					invalidate()
				}

				val animatingToLoadingProgress = if ((buttonState == 1 || miniButtonState == 1) && loadingProgressLayout != null) 1f else 0f

				if (animatingToLoadingProgress == 0f && infoLayout != null && fullWidth) {
					animatingLoadingProgressProgress = 0f
				}

				if (animatingLoadingProgressProgress < animatingToLoadingProgress) {
					animatingLoadingProgressProgress += animationDt / 160.0f

					if (animatingLoadingProgressProgress > animatingToLoadingProgress) {
						animatingLoadingProgressProgress = animatingToLoadingProgress
					}

					invalidate()
				}
				else if (animatingLoadingProgressProgress != animatingToLoadingProgress) {
					animatingLoadingProgressProgress -= animationDt / 160.0f

					if (animatingLoadingProgressProgress < animatingToLoadingProgress) {
						animatingLoadingProgressProgress = animatingToLoadingProgress
					}

					invalidate()
				}
			}
		}
		else if (currentMessageObject?.type == MessageObject.TYPE_GEO) {
			if (docTitleLayout != null) {
				if (currentMessageObject?.isOutOwner == true) {
					Theme.chat_locationTitlePaint.color = ResourcesCompat.getColor(resources, R.color.white, null)
					Theme.chat_locationAddressPaint.color = ResourcesCompat.getColor(resources, R.color.white, null) // getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_outVenueInfoSelectedText else Theme.key_chat_outVenueInfoText)
				}
				else {
					Theme.chat_locationTitlePaint.color = ResourcesCompat.getColor(resources, R.color.text, null) // getThemedColor(Theme.key_chat_messageTextIn)
					Theme.chat_locationAddressPaint.color = ResourcesCompat.getColor(resources, R.color.text, null) // getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inVenueInfoSelectedText else Theme.key_chat_inVenueInfoText)
				}

				if (MessageObject.getMedia(currentMessageObject!!.messageOwner) is TLRPC.TLMessageMediaGeoLive) {
					var cy = (photoImage.imageY2 + AndroidUtilities.dp(30f)).toInt()

					if (!locationExpired || transitionParams.animateLocationIsExpired) {
						forceNotDrawTime = true

						val progress: Float
						val text: String?
						var docTitleLayout = docTitleLayout
						var infoLayout = infoLayout
						var alpha = 1f

						if (transitionParams.animateLocationIsExpired) {
							progress = transitionParams.lastDrawLocationExpireProgress
							text = transitionParams.lastDrawLocationExpireText
							docTitleLayout = transitionParams.lastDrawDocTitleLayout
							infoLayout = transitionParams.lastDrawInfoLayout
							alpha = 1f - transitionParams.animateChangeProgress
						}
						else {
							progress = 1.0f - abs(ConnectionsManager.getInstance(currentAccount).currentTime - currentMessageObject!!.messageOwner!!.date) / MessageObject.getMedia(currentMessageObject!!.messageOwner)!!.period.toFloat()
							text = LocaleController.formatLocationLeftTime(abs(MessageObject.getMedia(currentMessageObject!!.messageOwner)!!.period - (ConnectionsManager.getInstance(currentAccount).currentTime - currentMessageObject!!.messageOwner!!.date)))
						}

						rect.set(photoImage.imageX2 - AndroidUtilities.dp(43f), (cy - AndroidUtilities.dp(15f)).toFloat(), photoImage.imageX2 - AndroidUtilities.dp(13f), (cy + AndroidUtilities.dp(15f)).toFloat())

						if (currentMessageObject?.isOutOwner == true) {
							Theme.chat_radialProgress2Paint.color = ResourcesCompat.getColor(resources, R.color.white, null) //  getThemedColor(Theme.key_chat_outInstant)
							Theme.chat_livePaint.color = ResourcesCompat.getColor(resources, R.color.white, null) // getThemedColor(Theme.key_chat_outInstant)
						}
						else {
							Theme.chat_radialProgress2Paint.color = ResourcesCompat.getColor(resources, R.color.brand_day_night, null) // getThemedColor(Theme.key_chat_inInstant)
							Theme.chat_livePaint.color = ResourcesCompat.getColor(resources, R.color.brand_day_night, null) // getThemedColor(Theme.key_chat_inInstant)
						}

						val docTitleAlpha = Theme.chat_locationTitlePaint.alpha
						val infoAlpha = Theme.chat_locationAddressPaint.alpha
						val liveAlpha = Theme.chat_livePaint.alpha

						if (alpha != 1f) {
							Theme.chat_locationTitlePaint.alpha = (docTitleAlpha * alpha).toInt()
							Theme.chat_locationAddressPaint.alpha = (infoAlpha * alpha).toInt()
							Theme.chat_livePaint.alpha = (liveAlpha * alpha).toInt()

							canvas.save()
							canvas.translate(0f, -AndroidUtilities.dp(50f) * transitionParams.animateChangeProgress)
						}

						Theme.chat_radialProgress2Paint.alpha = (50 * alpha).toInt()

						canvas.drawCircle(rect.centerX(), rect.centerY(), AndroidUtilities.dp(15f).toFloat(), Theme.chat_radialProgress2Paint)

						Theme.chat_radialProgress2Paint.alpha = (255 * alpha).toInt()

						canvas.drawArc(rect, -90f, -360 * progress, false, Theme.chat_radialProgress2Paint)

						val w = Theme.chat_livePaint.measureText(text)

						canvas.drawText(text!!, rect.centerX() - w / 2, (cy + AndroidUtilities.dp(4f)).toFloat(), Theme.chat_livePaint)

						if (docTitleLayout != null && infoLayout != null) {
							canvas.withTranslation(photoImage.imageX + AndroidUtilities.dp(10f), photoImage.imageY2 + AndroidUtilities.dp(10f)) {
								docTitleLayout.draw(this)
								translate(0f, AndroidUtilities.dp(23f).toFloat())
								infoLayout.draw(this)
							}
						}

						if (alpha != 1f) {
							Theme.chat_locationTitlePaint.alpha = docTitleAlpha
							Theme.chat_locationAddressPaint.alpha = infoAlpha
							Theme.chat_livePaint.alpha = liveAlpha

							canvas.restore()
						}

						transitionParams.lastDrawLocationExpireProgress = progress
						transitionParams.lastDrawLocationExpireText = text
						transitionParams.lastDrawDocTitleLayout = docTitleLayout
						transitionParams.lastDrawInfoLayout = infoLayout
					}
					else {
						transitionParams.lastDrawLocationExpireText = null
						transitionParams.lastDrawDocTitleLayout = null
						transitionParams.lastDrawInfoLayout = null
					}

					val cx = (photoImage.imageX + photoImage.imageWidth / 2 - AndroidUtilities.dp(31f)).toInt()
					cy = (photoImage.imageY + photoImage.imageHeight / 2 - AndroidUtilities.dp(38f)).toInt()

					setDrawableBounds(Theme.chat_msgAvatarLiveLocationDrawable, cx, cy)

					Theme.chat_msgAvatarLiveLocationDrawable.draw(canvas)

					locationImageReceiver.setImageCoordinates((cx + AndroidUtilities.dp(5.0f)).toFloat(), (cy + AndroidUtilities.dp(5.0f)).toFloat(), AndroidUtilities.dp(52f).toFloat(), AndroidUtilities.dp(52f).toFloat())
					locationImageReceiver.draw(canvas)
				}
				else {
					canvas.withTranslation(photoImage.imageX + AndroidUtilities.dp(6f), photoImage.imageY2 + AndroidUtilities.dp(8f)) {
						docTitleLayout?.draw(this)

						if (infoLayout != null) {
							translate(0f, AndroidUtilities.dp(21f).toFloat())
							infoLayout?.draw(this)
						}

					}
				}
			}
		}
		else if (currentMessageObject!!.type == MessageObject.TYPE_CALL) {
			if (currentMessageObject!!.isOutOwner) {
				Theme.chat_audioTitlePaint.color = ResourcesCompat.getColor(context.resources, R.color.white, null)
				Theme.chat_contactPhonePaint.color = ResourcesCompat.getColor(context.resources, R.color.totals_blue_text, null)
			}
			else {
				Theme.chat_audioTitlePaint.color = context.getColor(R.color.brand)
				Theme.chat_contactPhonePaint.color = ResourcesCompat.getColor(context.resources, R.color.dark_gray, null)
			}

			forceNotDrawTime = true

			val x = if (currentMessageObject!!.isOutOwner) {
				layoutWidth - backgroundWidth + AndroidUtilities.dp(16f)
			}
			else {
				if (isChat && !isThreadPost && currentMessageObject!!.needDrawAvatar()) {
					AndroidUtilities.dp(74f)
				}
				else {
					AndroidUtilities.dp(25f)
				}
			}

			otherX = x

			if (titleLayout != null) {
				canvas.withTranslation(x.toFloat(), (AndroidUtilities.dp(12f) + namesOffset).toFloat()) {
					titleLayout?.draw(this)
				}
			}

			if (docTitleLayout != null) {
				canvas.withTranslation((x + AndroidUtilities.dp(19f)).toFloat(), (AndroidUtilities.dp(37f) + namesOffset).toFloat()) {
					docTitleLayout?.draw(this)
				}
			}

			val icon: Drawable
			val phone: Drawable
			val idx = if (currentMessageObject!!.isVideoCall) 1 else 0

			// MARK: call status is drawn here

			if (currentMessageObject!!.isOutOwner) {
				icon = Theme.chat_msgCallUpGreenDrawable

				phone = if (currentMessageObject!!.isVideoCall) {
					ResourcesCompat.getDrawable(context.resources, R.drawable.msg_video_call_out, null)!!
				}
				else {
					ResourcesCompat.getDrawable(context.resources, R.drawable.msg_call_out, null)!!
				}
			}
			else {
				val reason = (currentMessageObject?.messageOwner?.action as? TLRPC.TLMessageActionPhoneCall)?.reason

				icon = if (reason is TLRPC.TLPhoneCallDiscardReasonMissed || reason is TLRPC.TLPhoneCallDiscardReasonBusy) {
					Theme.chat_msgCallDownRedDrawable
				}
				else {
					Theme.chat_msgCallDownGreenDrawable
				}
				phone = if (currentMessageObject!!.isVideoCall) {
					ResourcesCompat.getDrawable(context.resources, R.drawable.msg_video_call_in, null)!!
				}
				else {
					ResourcesCompat.getDrawable(context.resources, R.drawable.msg_call_in, null)!!
				}
			}

			setDrawableBounds(icon, x - AndroidUtilities.dp(1f), AndroidUtilities.dp(37f) + namesOffset)

			icon.draw(canvas)

			if (selectorDrawable[0] != null && selectorDrawableMaskType[0] == 4) {
				selectorDrawable[0]!!.draw(canvas)
			}

			otherY = if (!isPinnedBottom && !isPinnedTop) {
				AndroidUtilities.dp(18.5f)
			}
			else if (isPinnedBottom && isPinnedTop) {
				AndroidUtilities.dp(18f)
			}
			else if (!isPinnedBottom) {
				AndroidUtilities.dp(17f)
			}
			else {
				AndroidUtilities.dp(19f)
			}

			setDrawableBounds(phone, (x + if (idx == 0) 187f.dp else 186f.dp), otherY - 6.5f.dp)

			phone.draw(canvas)
		}
		else if (currentMessageObject!!.type == MessageObject.TYPE_POLL) {
			val newTime = System.currentTimeMillis()
			var dt = newTime - voteLastUpdateTime

			if (dt > 17) {
				dt = 17
			}

			voteLastUpdateTime = newTime

			val color1: Int
			val color2: Int

			if (currentMessageObject!!.isOutOwner) {
				color1 = ResourcesCompat.getColor(resources, R.color.white, null)
				color2 = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
			}
			else {
				color1 = ResourcesCompat.getColor(resources, R.color.text, null)
				color2 = ResourcesCompat.getColor(resources, R.color.dark_gray, null)
			}

			Theme.chat_audioTitlePaint.color = color1
			Theme.chat_audioPerformerPaint.color = color1
			Theme.chat_instantViewPaint.color = color1
			Theme.chat_timePaint.color = color2
			Theme.chat_livePaint.color = color2
			Theme.chat_locationAddressPaint.color = color2

			canvas.save()

			if (transitionParams.animateForwardedLayout) {
				var y = namesOffset * transitionParams.animateChangeProgress + transitionParams.animateForwardedNamesOffset * (1f - transitionParams.animateChangeProgress)

				if (currentMessageObject!!.needDrawForwarded()) {
					y -= namesOffset.toFloat()
				}

				canvas.translate(0f, y)
			}

			val x = if (currentMessageObject!!.isOutOwner) {
				layoutWidth - backgroundWidth + AndroidUtilities.dp(11f)
			}
			else {
				if (isChat && !isThreadPost && currentMessageObject!!.needDrawAvatar()) {
					AndroidUtilities.dp(68f)
				}
				else {
					AndroidUtilities.dp(20f)
				}
			}

			if (titleLayout != null) {
				canvas.withTranslation((x + extraTextX).toFloat(), (AndroidUtilities.dp(15f) + namesOffset).toFloat()) {
					titleLayout?.draw(this)
				}
			}

			val y = (if (titleLayout != null) titleLayout!!.height else 0) + AndroidUtilities.dp(20f) + namesOffset

			if (docTitleLayout != null) {
				canvas.withTranslation((x + docTitleOffsetX + extraTextX).toFloat(), y.toFloat()) {
					docTitleLayout?.draw(this)
				}

				val media = MessageObject.getMedia(currentMessageObject!!.messageOwner) as TLRPC.TLMessageMediaPoll

				if (lastPoll!!.quiz && (pollVoted || pollClosed) && !TextUtils.isEmpty(media.results?.solution)) {
					val drawable = getThemedDrawable(if (currentMessageObject!!.isOutOwner) Theme.key_drawable_chat_pollHintDrawableOut else Theme.key_drawable_chat_pollHintDrawableIn)

					if (pollVoteInProgress) {
						drawable.alpha = (255 * pollAnimationProgress).toInt()
					}
					else {
						drawable.alpha = 255
					}

					pollHintX = if (docTitleOffsetX < 0 || docTitleOffsetX == 0 && docTitleLayout!!.getLineLeft(0) == 0f) {
						currentBackgroundDrawable!!.bounds.right - drawable.intrinsicWidth - if (currentMessageObject!!.isOutOwner) 17.dp else 11.dp
					}
					else {
						getCurrentBackgroundLeft() + 11.dp
					}

					pollHintY = y - 6.dp

					val cx = pollHintX + drawable.intrinsicWidth / 2
					val cy = pollHintY + drawable.intrinsicHeight / 2
					val scale = if (hintButtonVisible && hintButtonProgress < 1) AnimationProperties.overshootInterpolator.getInterpolation(hintButtonProgress) else hintButtonProgress
					val w = (drawable.intrinsicWidth * scale).toInt()
					val h = (drawable.intrinsicHeight * scale).toInt()

					drawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
					drawable.draw(canvas)
				}
				else {
					pollHintX = -1
				}

				if (pollAvatarImages != null && !isBot) {
					val toAdd: Int
					val ax: Int
					val lineLeft = ceil(docTitleLayout!!.getLineLeft(0).toDouble()).toInt()

					if (docTitleOffsetX != 0 || lineLeft != 0) {
						toAdd = -AndroidUtilities.dp(13f)

						ax = if (docTitleOffsetX != 0) {
							x + docTitleOffsetX - AndroidUtilities.dp((7 + 16).toFloat()) - extraTextX
						}
						else {
							x + lineLeft - AndroidUtilities.dp((7 + 16).toFloat()) - extraTextX
						}
					}
					else {
						toAdd = AndroidUtilities.dp(13f)
						ax = x + ceil(docTitleLayout!!.getLineWidth(0).toDouble()).toInt() + AndroidUtilities.dp(7f) + extraTextX
					}

					for (a in pollAvatarImages!!.indices.reversed()) {
						if (!pollAvatarImagesVisible!![a] || !pollAvatarImages!![a]!!.hasImageSet()) {
							continue
						}

						pollAvatarImages!![a]!!.setImageX(ax + toAdd * a)
						pollAvatarImages!![a]!!.imageY = (y - AndroidUtilities.dp(1f)).toFloat()

						if (a != pollAvatarImages!!.size - 1) {
							canvas.drawCircle(pollAvatarImages!![a]!!.centerX, pollAvatarImages!![a]!!.centerY, AndroidUtilities.dp(9f).toFloat(), currentBackgroundDrawable!!.paint)
						}

						if (animatePollAvatars && isAnimatingPollAnswer) {
							val alpha = min(if (pollUnvoteInProgress) (1.0f - pollAnimationProgress) / 0.3f else pollAnimationProgress, 1.0f)
							pollAvatarImages!![a]!!.alpha = alpha
						}

						pollAvatarImages!![a]!!.draw(canvas)
					}
				}
			}

			if ((!pollClosed && !pollVoted || pollVoteInProgress) && lastPoll!!.quiz && lastPoll!!.closePeriod != 0) {
				val currentTime = ConnectionsManager.getInstance(currentAccount).currentTimeMillis
				val time = max(0, lastPoll!!.closeDate.toLong() * 1000 - currentTime)

				if (closeTimeText == null || lastPollCloseTime != time) {
					closeTimeText = AndroidUtilities.formatDurationNoHours(ceil((time / 1000.0f).toDouble()).toInt(), false)
					closeTimeWidth = ceil(Theme.chat_timePaint.measureText(closeTimeText).toDouble()).toInt()
					lastPollCloseTime = time
				}

				if (time <= 0 && !pollClosed) {
					if (currentMessageObject!!.pollLastCheckTime + 1000 < SystemClock.elapsedRealtime()) {
						currentMessageObject!!.pollLastCheckTime = 0
					}

					delegate?.needReloadPolls()
				}

				var tx = currentBackgroundDrawable!!.bounds.right - closeTimeWidth - AndroidUtilities.dp(if (currentMessageObject!!.isOutOwner) 40f else 34f)

				if (time <= 5000) {
					Theme.chat_timePaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outPollWrongAnswer else Theme.key_chat_inPollWrongAnswer)
				}

				if (animatePollAnswer) {
					Theme.chat_timePaint.alpha = (255 * (1.0f - pollAnimationProgress)).toInt()
				}

				canvas.drawText(closeTimeText!!, tx.toFloat(), (y + AndroidUtilities.dp(11f)).toFloat(), Theme.chat_timePaint)

				Theme.chat_pollTimerPaint.color = Theme.chat_timePaint.color

				tx += closeTimeWidth + AndroidUtilities.dp(13f)

				val rad = AndroidUtilities.dp(5.1f)
				val ty = y + AndroidUtilities.dp(6f)

				if (time <= 60000) {
					rect.set((tx - rad).toFloat(), (ty - rad).toFloat(), (tx + rad).toFloat(), (ty + rad).toFloat())
					val radProgress = -360 * (time / (min(60, lastPoll!!.closePeriod) * 1000.0f))
					canvas.drawArc(rect, -90f, radProgress, false, Theme.chat_pollTimerPaint)
					timerParticles?.draw(canvas, Theme.chat_pollTimerPaint, rect, radProgress, if (pollVoteInProgress) 1.0f - pollAnimationProgress else 1.0f)
				}
				else {
					canvas.drawCircle(tx.toFloat(), ty.toFloat(), rad.toFloat(), Theme.chat_pollTimerPaint)
				}

				if (time > 60000 || timerTransitionProgress != 0.0f) {
					Theme.chat_pollTimerPaint.alpha = (255 * timerTransitionProgress).toInt()

					canvas.drawLine(tx - AndroidUtilities.dp(2.1f) * timerTransitionProgress, (ty - AndroidUtilities.dp(7.5f)).toFloat(), tx + AndroidUtilities.dp(2.1f) * timerTransitionProgress, (ty - AndroidUtilities.dp(7.5f)).toFloat(), Theme.chat_pollTimerPaint)
					canvas.drawLine(tx.toFloat(), ty - AndroidUtilities.dp(3f) * timerTransitionProgress, tx.toFloat(), ty.toFloat(), Theme.chat_pollTimerPaint)

					if (time <= 60000) {
						timerTransitionProgress -= dt / 180.0f

						if (timerTransitionProgress < 0) {
							timerTransitionProgress = 0f
						}
					}
				}

				invalidate()
			}

			if (selectorDrawable[0] != null && (selectorDrawableMaskType[0] == 1 || selectorDrawableMaskType[0] == 3)) {
				if (selectorDrawableMaskType[0] == 3) {
					canvas.save()
					canvas.scale(hintButtonProgress, hintButtonProgress, selectorDrawable[0]!!.bounds.centerX().toFloat(), selectorDrawable[0]!!.bounds.centerY().toFloat())
				}

				selectorDrawable[0]!!.draw(canvas)

				if (selectorDrawableMaskType[0] == 3) {
					canvas.restore()
				}
			}

			var lastVoteY = 0
			var a = 0
			val n = pollButtons.size

			while (a < n) {
				val button = pollButtons[a]
				button.x = x

				canvas.withTranslation((x + AndroidUtilities.dp(35f)).toFloat(), (button.y + namesOffset).toFloat()) {
					button.title?.draw(this)

					val alpha = (if (isAnimatingPollAnswer) 255 * min((if (pollUnvoteInProgress) 1.0f - pollAnimationProgress else pollAnimationProgress) / 0.3f, 1.0f) else 255).toInt()

					if (pollVoted || pollClosed || isAnimatingPollAnswer) {
						if (lastPoll!!.quiz && pollVoted && button.chosen) {
							val key = if (button.correct) {
								if (currentMessageObject!!.isOutOwner) Theme.key_chat_outPollCorrectAnswer else Theme.key_chat_inPollCorrectAnswer
							}
							else {
								if (currentMessageObject!!.isOutOwner) Theme.key_chat_outPollWrongAnswer else Theme.key_chat_inPollWrongAnswer
							}

							if (Theme.hasThemeKey(key)) {
								Theme.chat_docBackPaint.color = getThemedColor(key)
							}
							else {
								Theme.chat_docBackPaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outAudioSeekbarFill else Theme.key_chat_inAudioSeekbarFill)
							}
						}
						else {
							Theme.chat_docBackPaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outAudioSeekbarFill else Theme.key_chat_inAudioSeekbarFill)
						}

						if (isAnimatingPollAnswer) {
							var oldAlpha = Theme.chat_instantViewPaint.alpha / 255.0f
							Theme.chat_instantViewPaint.alpha = (alpha * oldAlpha).toInt()
							oldAlpha = Theme.chat_docBackPaint.alpha / 255.0f
							Theme.chat_docBackPaint.alpha = (alpha * oldAlpha).toInt()
						}

						val currentPercent = ceil((button.prevPercent + (button.percent - button.prevPercent) * pollAnimationProgress).toDouble()).toInt()
						val text = String.format(Locale.getDefault(), "%d%%", currentPercent)
						var width = ceil(Theme.chat_instantViewPaint.measureText(text).toDouble()).toInt()

						drawText(text, (-AndroidUtilities.dp(6.5f) - width).toFloat(), AndroidUtilities.dp(14f).toFloat(), Theme.chat_instantViewPaint)

						width = backgroundWidth - AndroidUtilities.dp(76f)

						val currentPercentProgress = button.prevPercentProgress + (button.percentProgress - button.prevPercentProgress) * pollAnimationProgress

						rect.set(0f, (button.height + AndroidUtilities.dp(6f)).toFloat(), width * currentPercentProgress, (button.height + AndroidUtilities.dp(11f)).toFloat())

						drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.chat_docBackPaint)

						if (button.chosen || button.prevChosen || lastPoll!!.quiz && button.correct && (pollVoted || pollClosed)) {
							val cx = rect.left - AndroidUtilities.dp(13.5f)
							val cy = rect.centerY()

							drawCircle(cx, cy, AndroidUtilities.dp(7f).toFloat(), Theme.chat_docBackPaint)

							val drawable = if (lastPoll!!.quiz && button.chosen && !button.correct) {
								Theme.chat_pollCrossDrawable[if (currentMessageObject!!.isOutOwner) 1 else 0]
							}
							else {
								Theme.chat_pollCheckDrawable[if (currentMessageObject!!.isOutOwner) 1 else 0]
							}

							drawable.alpha = alpha

							setDrawableBounds(drawable, cx - drawable.intrinsicWidth / 2, cy - drawable.intrinsicHeight / 2)

							drawable.draw(this)
						}
					}

					if (!pollVoted && !pollClosed || isAnimatingPollAnswer) {
						if (isDrawSelectionBackground()) {
							Theme.chat_replyLinePaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outVoiceSeekbarSelected else Theme.key_chat_inVoiceSeekbarSelected)
						}
						else {
							Theme.chat_replyLinePaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outVoiceSeekbar else Theme.key_chat_inVoiceSeekbar)
						}

						if (isAnimatingPollAnswer) {
							val oldAlpha = Theme.chat_replyLinePaint.alpha / 255.0f
							Theme.chat_replyLinePaint.alpha = ((255 - alpha) * oldAlpha).toInt()
						}

						drawLine(-AndroidUtilities.dp(2f).toFloat(), (button.height + AndroidUtilities.dp(13f)).toFloat(), (backgroundWidth - AndroidUtilities.dp(58f)).toFloat(), (button.height + AndroidUtilities.dp(13f)).toFloat(), Theme.chat_replyLinePaint)

						if (pollVoteInProgress && a == pollVoteInProgressNum) {
							Theme.chat_instantViewRectPaint.color = getThemedColor(if (currentMessageObject!!.isOutOwner) Theme.key_chat_outAudioSeekbarFill else Theme.key_chat_inAudioSeekbarFill)

							if (isAnimatingPollAnswer) {
								val oldAlpha = Theme.chat_instantViewRectPaint.alpha / 255.0f
								Theme.chat_instantViewRectPaint.alpha = ((255 - alpha) * oldAlpha).toInt()
							}

							rect.set((-AndroidUtilities.dp(22f) - AndroidUtilities.dp(8.5f)).toFloat(), (AndroidUtilities.dp(9f) - AndroidUtilities.dp(8.5f)).toFloat(), (-AndroidUtilities.dp(23f) + AndroidUtilities.dp(8.5f)).toFloat(), (AndroidUtilities.dp(9f) + AndroidUtilities.dp(8.5f)).toFloat())

							drawArc(rect, voteRadOffset, voteCurrentCircleLength, false, Theme.chat_instantViewRectPaint)
						}
						else {
							if (currentMessageObject!!.isOutOwner) {
								Theme.chat_instantViewRectPaint.color = getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_outMenuSelected else Theme.key_chat_outMenu)
							}
							else {
								Theme.chat_instantViewRectPaint.color = getThemedColor(if (isDrawSelectionBackground()) Theme.key_chat_inMenuSelected else Theme.key_chat_inMenu)
							}

							if (isAnimatingPollAnswer) {
								val oldAlpha = Theme.chat_instantViewRectPaint.alpha / 255.0f
								Theme.chat_instantViewRectPaint.alpha = ((255 - alpha) * oldAlpha).toInt()
							}

							drawCircle(-AndroidUtilities.dp(22f).toFloat(), AndroidUtilities.dp(9f).toFloat(), AndroidUtilities.dp(8.5f).toFloat(), Theme.chat_instantViewRectPaint)

							if (lastPoll!!.multipleChoice) {
								val size = AndroidUtilities.dp(8.5f)
								var color = context.getColor(R.color.brand)

								if (currentMessageObject?.isOutOwner == true) {
									color = context.getColor(R.color.white)
								}

								pollCheckBox!![a]!!.setColor(0, if (currentMessageObject?.isOutOwner == true) context.getColor(R.color.white) else context.getColor(R.color.brand), color)
								pollCheckBox!![a]!!.setBounds(-AndroidUtilities.dp(22f) - size / 2, AndroidUtilities.dp(9f) - size / 2, size, size)
								pollCheckBox!![a]!!.draw(this)
							}
						}
					}

				}

				if (a == n - 1) {
					lastVoteY = button.y + namesOffset + button.height
				}

				a++
			}

			if (drawInstantView) {
				val textX = getCurrentBackgroundLeft() + AndroidUtilities.dp(if (currentMessageObject!!.isOutOwner || mediaBackground || drawPinnedBottom) 2f else 8f)
				val instantY = lastVoteY + AndroidUtilities.dp(13f)

				if (currentMessageObject!!.isOutOwner) {
					Theme.chat_instantViewPaint.color = ResourcesCompat.getColor(context.resources, R.color.white, null)
				}
				else {
					Theme.chat_instantViewPaint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
				}

				instantButtonRect[textX.toFloat(), instantY.toFloat(), (textX + instantWidth).toFloat()] = (instantY + AndroidUtilities.dp(44f)).toFloat()

				if (selectorDrawable[0] != null && selectorDrawableMaskType[0] == 2) {
					selectorDrawable[0]!!.setBounds(textX, instantY, textX + instantWidth, instantY + AndroidUtilities.dp(44f))
					selectorDrawable[0]!!.draw(canvas)
				}

				if (instantViewLayout != null) {
					canvas.withTranslation((textX + instantTextX).toFloat(), (instantY + AndroidUtilities.dp(14.5f)).toFloat()) {
						instantViewLayout?.draw(this)
					}
				}
			}
			else if (infoLayout != null) {
				if (lastPoll!!.publicVoters || lastPoll!!.multipleChoice) {
					lastVoteY += AndroidUtilities.dp(6f)
				}

				canvas.withTranslation((x + infoX).toFloat(), (lastVoteY + AndroidUtilities.dp(22f)).toFloat()) {
					infoLayout?.draw(this)
				}
			}

			updatePollAnimations(dt)

			canvas.restore()
		}
		else if (currentMessageObject!!.type == MessageObject.TYPE_CONTACT) {
			if (currentMessageObject!!.isOutOwner) {
				Theme.chat_contactNamePaint.color = ResourcesCompat.getColor(context.resources, R.color.white, null)
				Theme.chat_contactPhonePaint.color = ResourcesCompat.getColor(context.resources, R.color.white, null)
			}
			else {
				Theme.chat_contactNamePaint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
				Theme.chat_contactPhonePaint.color = ResourcesCompat.getColor(context.resources, R.color.text, null)
			}

			if (titleLayout != null) {
				canvas.withTranslation(photoImage.imageX + photoImage.imageWidth + AndroidUtilities.dp(9f), (AndroidUtilities.dp(16f) + namesOffset).toFloat()) {
					titleLayout?.draw(this)
				}
			}

			if (docTitleLayout != null) {
				canvas.withTranslation(photoImage.imageX + photoImage.imageWidth + AndroidUtilities.dp(9f), (AndroidUtilities.dp(39f) + namesOffset).toFloat()) {
					docTitleLayout?.draw(this)
				}
			}

			val menuDrawable: Drawable

			if (currentMessageObject!!.isOutOwner) {
				menuDrawable = getThemedDrawable(if (isDrawSelectionBackground()) Theme.key_drawable_msgOutMenuSelected else Theme.key_drawable_msgOutMenu).mutate()
				menuDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)
			}
			else {
				menuDrawable = (if (isDrawSelectionBackground()) Theme.chat_msgInMenuSelectedDrawable else Theme.chat_msgInMenuDrawable).mutate()
				menuDrawable.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.brand, null), PorterDuff.Mode.SRC_IN)
			}

			setDrawableBounds(menuDrawable, (photoImage.imageX + backgroundWidth - AndroidUtilities.dp(48f)).toInt().also { otherX = it }, (photoImage.imageY - AndroidUtilities.dp(2f)).toInt().also { otherY = it })

			menuDrawable.draw(canvas)

			if (drawInstantView) {
				val textX = (photoImage.imageX - AndroidUtilities.dp(2f)).toInt()
				var instantY = layoutHeight - AndroidUtilities.dp((36 + 30).toFloat())

				if (!reactionsLayoutInBubble.isEmpty && !reactionsLayoutInBubble.isSmall) {
					instantY -= reactionsLayoutInBubble.totalHeight
				}

				if (drawCommentButton) {
					instantY -= AndroidUtilities.dp(if (shouldDrawTimeOnMedia()) 39.3f else 41f)
				}

				val backPaint = Theme.chat_instantViewRectPaint

				if (currentMessageObject!!.isOutOwner) {
					Theme.chat_instantViewPaint.color = ResourcesCompat.getColor(context.resources, R.color.white, null)
					backPaint.color = ResourcesCompat.getColor(context.resources, R.color.white, null)
				}
				else {
					Theme.chat_instantViewPaint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
					backPaint.color = ResourcesCompat.getColor(context.resources, R.color.brand, null)
				}

				instantButtonRect[textX.toFloat(), instantY.toFloat(), (textX + instantWidth).toFloat()] = (instantY + AndroidUtilities.dp(36f)).toFloat()

				selectorDrawableMaskType[0] = 0

				selectorDrawable[0]!!.setBounds(textX, instantY, textX + instantWidth, instantY + AndroidUtilities.dp(36f))
				selectorDrawable[0]!!.draw(canvas)

				canvas.drawRoundRect(instantButtonRect, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), backPaint)

				if (instantViewLayout != null) {
					canvas.withTranslation((textX + instantTextX).toFloat(), (instantY + AndroidUtilities.dp(10.5f)).toFloat()) {
						instantViewLayout?.draw(this)
					}
				}
			}
		}

		if (drawImageButton && photoImage.visible) {
			if (controlsAlpha != 1.0f) {
				radialProgress.overrideAlpha = controlsAlpha
			}

			if (photoImage.hasImageSet()) {
				radialProgress.setBackgroundDrawable(null)
			}
			else {
				radialProgress.setBackgroundDrawable(if (isDrawSelectionBackground()) currentBackgroundSelectedDrawable else currentBackgroundDrawable)
			}

			if (!currentMessageObject!!.needDrawBluredPreview() || !MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
				radialProgress.draw(canvas)
			}
		}

		if (buttonState == -1 && currentMessageObject!!.needDrawBluredPreview() && !MediaController.getInstance().isPlayingMessage(currentMessageObject) && photoImage.visible && currentMessageObject!!.messageOwner!!.destroyTime != 0) {
			if (!currentMessageObject!!.isOutOwner) {
				val msTime = System.currentTimeMillis() + ConnectionsManager.getInstance(currentAccount).timeDifference * 1000
				val progress = max(0, currentMessageObject!!.messageOwner!!.destroyTime.toLong() * 1000 - msTime) / (currentMessageObject!!.messageOwner!!.ttl * 1000.0f)

				Theme.chat_deleteProgressPaint.alpha = (255 * controlsAlpha).toInt()

				canvas.drawArc(deleteProgressRect, -90f, -360 * progress, true, Theme.chat_deleteProgressPaint)

				if (progress != 0f) {
					// val offset = AndroidUtilities.dp(2f)
					// invalidate(deleteProgressRect.left.toInt() - offset, deleteProgressRect.top.toInt() - offset, deleteProgressRect.right.toInt() + offset * 2, deleteProgressRect.bottom.toInt() + offset * 2)
					invalidate()
				}
			}
			updateSecretTimeText(currentMessageObject)
		}

		if ((drawVideoImageButton || animatingDrawVideoImageButton != 0) && photoImage.visible) {
			if (controlsAlpha != 1.0f) {
				videoRadialProgress.overrideAlpha = controlsAlpha
			}

			videoRadialProgress.draw(canvas)
		}

		if (drawMediaCheckBox) {
			if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage) {
				val size = AndroidUtilities.dp(20f)
				mediaCheckBox?.setBackgroundType(if (radialProgress.miniIcon != MediaActionDrawable.ICON_NONE) 12 else 13)
				mediaCheckBox?.setBounds(buttonX + AndroidUtilities.dp(28f), buttonY + AndroidUtilities.dp(28f), size, size)
				mediaCheckBox?.setColor(if (currentMessageObject?.isOutOwner == true) context.getColor(R.color.white) else context.getColor(R.color.dark_gray), if (currentMessageObject?.isOutOwner == true) context.getColor(R.color.white) else context.getColor(R.color.brand), if (currentMessageObject?.isOutOwner == true) context.getColor(R.color.brand) else context.getColor(R.color.light_background))
			}
			else {
				val size = AndroidUtilities.dp(21f)
				mediaCheckBox?.setBackgroundType(0)
				mediaCheckBox?.setBounds(photoImage.imageX2.toInt() - AndroidUtilities.dp((21 + 4).toFloat()), photoImage.imageY.toInt() + AndroidUtilities.dp(4f), size, size)
				mediaCheckBox?.setColor(0, 0, if (currentMessageObject?.isOutOwner == true) context.getColor(R.color.white) else context.getColor(R.color.brand))
			}

			mediaCheckBox?.draw(canvas)
		}

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND) {
			var x1: Float
			var y1: Float
			val playing = MediaController.getInstance().isPlayingMessage(currentMessageObject)

			if (currentMessageObject!!.type == MessageObject.TYPE_ROUND_VIDEO) {
				var offsetX = 0f

				if (currentMessageObject!!.isOutOwner) {
					val offsetSize = (AndroidUtilities.roundPlayingMessageSize - AndroidUtilities.roundMessageSize) * 0.2f

					offsetX = if (isPlayingRound) offsetSize else 0f

					if (transitionParams.animatePlayingRound) {
						offsetX = (if (isPlayingRound) transitionParams.animateChangeProgress else 1f - transitionParams.animateChangeProgress) * offsetSize
					}
				}

				x1 = backgroundDrawableLeft + transitionParams.deltaLeft + AndroidUtilities.dp(8f) + roundPlayingDrawableProgress + offsetX
				y1 = (layoutHeight - AndroidUtilities.dp((28 - if (drawPinnedBottom) 2 else 0).toFloat())).toFloat()

				if (!reactionsLayoutInBubble.isEmpty) {
					y1 -= reactionsLayoutInBubble.totalHeight.toFloat()
				}

				transitionParams.lastDrawRoundVideoDotY = y1

				if (transitionParams.animateRoundVideoDotY) {
					y1 = transitionParams.animateFromRoundVideoDotY * (1f - transitionParams.animateChangeProgress) + y1 * transitionParams.animateChangeProgress
				}

				rect.set(x1, y1, x1 + timeWidthAudio + AndroidUtilities.dp((8 + 12 + 2).toFloat()), y1 + AndroidUtilities.dp(17f))

				val paint = if (hasGradientService()) {
					Paint().apply { color = context.getColor(android.R.color.transparent) }
				}
				else {
					getThemedPaint(Theme.key_paint_chatActionBackground)
				}

				val oldAlpha = paint.alpha

				paint.alpha = (oldAlpha * timeAlpha).toInt()

				applyServiceShaderMatrix()

				canvas.drawRoundRect(rect, AndroidUtilities.dp(26f).toFloat(), AndroidUtilities.dp(26f).toFloat(), paint)

				if (hasGradientService()) {
					val oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.alpha
					Theme.chat_actionBackgroundGradientDarkenPaint.alpha = (oldAlpha2 * timeAlpha).toInt()
					canvas.drawRoundRect(rect, AndroidUtilities.dp(26f).toFloat(), AndroidUtilities.dp(26f).toFloat(), Paint().apply { color = context.getColor(R.color.dark_transparent) })
					Theme.chat_actionBackgroundGradientDarkenPaint.alpha = oldAlpha2
				}

				paint.alpha = oldAlpha

				val showPlayingDrawable = playing || !currentMessageObject!!.isContentUnread

				if (showPlayingDrawable && roundPlayingDrawableProgress != 1f) {
					roundPlayingDrawableProgress += 16f / 150f

					if (roundPlayingDrawableProgress > 1f) {
						roundPlayingDrawableProgress = 1f
					}
					else {
						invalidate()
					}
				}
				else if (!showPlayingDrawable && roundPlayingDrawableProgress != 0f) {
					roundPlayingDrawableProgress -= 16f / 150f

					if (roundPlayingDrawableProgress < 0f) {
						roundPlayingDrawableProgress = 0f
					}
					else {
						invalidate()
					}
				}

				if (showPlayingDrawable) {
					if (playing && !MediaController.getInstance().isMessagePaused) {
						roundVideoPlayingDrawable.start()
					}
					else {
						roundVideoPlayingDrawable.stop()
					}
				}

				if (roundPlayingDrawableProgress < 1f) {
					val cx = x1 + timeWidthAudio + AndroidUtilities.dp(12f)
					val cy = y1 + AndroidUtilities.dp(8.3f)

					canvas.withScale(1f - roundPlayingDrawableProgress, 1f - roundPlayingDrawableProgress, cx, cy) {
						Theme.chat_docBackPaint.color = getThemedColor(Theme.key_chat_serviceText)
						Theme.chat_docBackPaint.alpha = (255 * timeAlpha * (1f - roundPlayingDrawableProgress)).toInt()

						drawCircle(cx, cy, AndroidUtilities.dp(3f).toFloat(), Theme.chat_docBackPaint)
					}
				}

				if (roundPlayingDrawableProgress > 0f) {
					setDrawableBounds(roundVideoPlayingDrawable, x1 + timeWidthAudio + AndroidUtilities.dp(6f), y1 + AndroidUtilities.dp(2.3f))

					canvas.withScale(roundPlayingDrawableProgress, roundPlayingDrawableProgress, roundVideoPlayingDrawable.bounds.centerX().toFloat(), roundVideoPlayingDrawable.bounds.centerY().toFloat()) {
						roundVideoPlayingDrawable.alpha = (255 * roundPlayingDrawableProgress).toInt()
						roundVideoPlayingDrawable.draw(this)
					}
				}

				x1 += AndroidUtilities.dp(4f).toFloat()
				y1 += AndroidUtilities.dp(1.7f).toFloat()
			}
			else {
				x1 = (backgroundDrawableLeft + AndroidUtilities.dp(if (currentMessageObject!!.isOutOwner || drawPinnedBottom) 12f else 18f)).toFloat()
				y1 = (layoutHeight - AndroidUtilities.dp(6.3f - if (drawPinnedBottom) 2 else 0) - timeLayout!!.height).toFloat()
			}

			if (durationLayout != null) {
				Theme.chat_timePaint.alpha = (255 * timeAlpha).toInt()

				canvas.withTranslation(x1, y1) {
					durationLayout?.draw(this)
				}

				Theme.chat_timePaint.alpha = 255
			}
		}
	}

	override fun getObserverTag(): Int {
		return tag
	}

	fun getMessageObject(): MessageObject? {
		return messageObjectToSet ?: currentMessageObject
	}

	fun getStreamingMedia(): TLRPC.Document? {
		return if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_ROUND || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) documentAttach else null
	}

	fun drawPinnedBottom(): Boolean {
		return if (currentMessagesGroup?.isDocuments == true) {
			if (currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM != 0) {
				isPinnedBottom
			}
			else {
				true
			}
		}
		else {
			isPinnedBottom
		}
	}

	fun drawPinnedTop(): Boolean {
		return if (currentMessagesGroup?.isDocuments == true) {
			if (currentPosition != null && currentPosition!!.flags and MessageObject.POSITION_FLAG_TOP != 0) {
				isPinnedTop
			}
			else true
		}
		else isPinnedTop
	}

	override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
		if (delegate != null && delegate!!.onAccessibilityAction(action, arguments)) {
			return false
		}

		if (action == AccessibilityNodeInfo.ACTION_CLICK) {
			val icon = iconForCurrentState

			if (icon != MediaActionDrawable.ICON_NONE && icon != MediaActionDrawable.ICON_FILE) {
				didPressButton(animated = true, video = false)
			}
			else if (currentMessageObject!!.type == MessageObject.TYPE_CALL) {
				delegate?.didPressOther(this, otherX.toFloat(), otherY.toFloat())
			}
			else {
				didClickedImage()
			}

			return true
		}
		else if (action == R.id.acc_action_small_button) {
			didPressMiniButton()
		}
		else if (action == R.id.acc_action_msg_options) {
			if (delegate != null) {
				if (currentMessageObject!!.type == MessageObject.TYPE_CALL) {
					delegate?.didLongPress(this, 0f, 0f)
				}
				else {
					delegate?.didPressOther(this, otherX.toFloat(), otherY.toFloat())
				}
			}
		}
		else if (action == R.id.acc_action_open_forwarded_origin) {
			if (delegate != null) {
				if (currentForwardChannel != null) {
					delegate?.didPressChannelAvatar(this@ChatMessageCell, currentForwardChannel, currentMessageObject?.messageOwner?.fwdFrom?.channelPost ?: 0, lastTouchX, lastTouchY)
				}
				else if (currentForwardUser != null) {
					delegate?.didPressUserAvatar(this@ChatMessageCell, currentForwardUser, lastTouchX, lastTouchY)
				}
				else if (currentForwardName != null) {
					delegate?.didPressHiddenForward(this@ChatMessageCell)
				}
			}
		}

		if (currentMessageObject!!.isVoice || currentMessageObject!!.isRoundVideo || currentMessageObject!!.isMusic && MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
			if (seekBarAccessibilityDelegate.performAccessibilityActionInternal(action, arguments)) {
				return true
			}
		}

		return super.performAccessibilityAction(action, arguments)
	}

	fun setAnimationRunning(animationRunning: Boolean, willRemoved: Boolean) {
		this.animationRunning = animationRunning

		if (animationRunning) {
			this.willRemoved = willRemoved
		}
		else {
			this.willRemoved = false
		}

		if (parent == null && attachedToWindow) {
			onDetachedFromWindow()
		}
	}

	override fun onHoverEvent(event: MotionEvent): Boolean {
		val x = event.x.toInt()
		val y = event.y.toInt()

		if (event.action == MotionEvent.ACTION_HOVER_ENTER || event.action == MotionEvent.ACTION_HOVER_MOVE) {
			for (i in 0 until accessibilityVirtualViewBounds.size) {
				val rect = accessibilityVirtualViewBounds.valueAt(i)

				if (rect!!.contains(x, y)) {
					val id = accessibilityVirtualViewBounds.keyAt(i)

					if (id != currentFocusedVirtualView) {
						currentFocusedVirtualView = id
						sendAccessibilityEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
					}

					return true
				}
			}
		}
		else if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
			currentFocusedVirtualView = 0
		}

		return super.onHoverEvent(event)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
	}

	override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider {
		return MessageAccessibilityNodeProvider()
	}

	private fun sendAccessibilityEventForVirtualView(viewId: Int, eventType: Int, text: String? = null) {
		val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

		if (am.isTouchExplorationEnabled) {
			val event = AccessibilityEvent.obtain(eventType)
			event.packageName = context.packageName
			event.setSource(this@ChatMessageCell, viewId)

			if (text != null) {
				event.text.add(text)
			}

			parent?.requestSendAccessibilityEvent(this@ChatMessageCell, event)
		}
	}

	fun getDescriptionLayout(): StaticLayout? {
		return descriptionLayout
	}

	fun setSelectedBackgroundProgress(value: Float) {
		selectedBackgroundProgress = value
		invalidate()
	}

	fun computeHeight(`object`: MessageObject?, groupedMessages: GroupedMessages?): Int {
		photoImage.setIgnoreImageSet(true)
		avatarImage.setIgnoreImageSet(true)
		replyImageReceiver.setIgnoreImageSet(true)
		locationImageReceiver.setIgnoreImageSet(true)

		if (groupedMessages != null && groupedMessages.messages.size != 1) {
			var h = 0

			for (i in groupedMessages.messages.indices) {
				val o = groupedMessages.messages[i]
				val position = groupedMessages.positions[o]

				if (position != null && position.flags and MessageObject.POSITION_FLAG_LEFT != 0) {
					setMessageContent(o, groupedMessages, bottomNear = false, topNear = false)
					h += totalHeight + keyboardHeight
				}
			}

			return h
		}

		setMessageContent(`object`, groupedMessages, bottomNear = false, topNear = false)

		photoImage.setIgnoreImageSet(false)
		avatarImage.setIgnoreImageSet(false)
		replyImageReceiver.setIgnoreImageSet(false)
		locationImageReceiver.setIgnoreImageSet(false)

		return totalHeight + keyboardHeight
	}

	fun shakeView() {
		val kf0 = Keyframe.ofFloat(0f, 0f)
		val kf1 = Keyframe.ofFloat(0.2f, 3f)
		val kf2 = Keyframe.ofFloat(0.4f, -3f)
		val kf3 = Keyframe.ofFloat(0.6f, 3f)
		val kf4 = Keyframe.ofFloat(0.8f, -3f)
		val kf5 = Keyframe.ofFloat(1f, 0f)
		val pvhRotation = PropertyValuesHolder.ofKeyframe(ROTATION, kf0, kf1, kf2, kf3, kf4, kf5)
		val kfs0 = Keyframe.ofFloat(0f, 1.0f)
		val kfs1 = Keyframe.ofFloat(0.5f, 0.97f)
		val kfs2 = Keyframe.ofFloat(1.0f, 1.0f)
		val pvhScaleX = PropertyValuesHolder.ofKeyframe(SCALE_X, kfs0, kfs1, kfs2)
		val pvhScaleY = PropertyValuesHolder.ofKeyframe(SCALE_Y, kfs0, kfs1, kfs2)

		shakeAnimation = AnimatorSet()
		shakeAnimation?.playTogether(ObjectAnimator.ofPropertyValuesHolder(this, pvhRotation), ObjectAnimator.ofPropertyValuesHolder(this, pvhScaleX), ObjectAnimator.ofPropertyValuesHolder(this, pvhScaleY))
		shakeAnimation?.duration = 500
		shakeAnimation?.start()
	}

	private fun cancelShakeAnimation() {
		if (shakeAnimation != null) {
			shakeAnimation?.cancel()
			shakeAnimation = null
			scaleX = 1.0f
			scaleY = 1.0f
			rotation = 0f
		}
	}

	fun setSlidingOffset(offsetX: Float) {
		if (slidingOffsetX != offsetX) {
			slidingOffsetX = offsetX
			updateTranslation()
		}
	}

	private fun updateTranslation() {
		if (currentMessageObject == null) {
			return
		}

		val checkBoxOffset = if (!currentMessageObject!!.isOutOwner) checkBoxTranslation else 0

		translationX = slidingOffsetX + animationOffsetX + checkBoxOffset
	}

	fun getNonAnimationTranslationX(update: Boolean): Float {
		return if (currentMessageObject != null && !currentMessageObject!!.isOutOwner) {
			if (update && (checkBoxVisible || checkBoxAnimationInProgress)) {
				val interpolator: Interpolator = if (checkBoxVisible) CubicBezierInterpolator.EASE_OUT else CubicBezierInterpolator.EASE_IN
				checkBoxTranslation = ceil((interpolator.getInterpolation(checkBoxAnimationProgress) * AndroidUtilities.dp(35f)).toDouble()).toInt()
			}
			slidingOffsetX + checkBoxTranslation
		}
		else {
			slidingOffsetX
		}
	}

	fun willRemovedAfterAnimation(): Boolean {
		return willRemoved
	}

	fun getAnimationOffsetX(): Float {
		return animationOffsetX
	}

	fun setAnimationOffsetX(offsetX: Float) {
		if (animationOffsetX != offsetX) {
			animationOffsetX = offsetX
			updateTranslation()
		}
	}

	fun setImageCoordinates(x: Float, y: Float, w: Float, h: Float) {
		photoImage.setImageCoordinates(x, y, w, h)

		if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
			videoButtonX = (photoImage.imageX + AndroidUtilities.dp(8f)).toInt()
			videoButtonY = (photoImage.imageY + AndroidUtilities.dp(8f)).toInt()

			videoRadialProgress.setProgressRect(videoButtonX, videoButtonY, videoButtonX + AndroidUtilities.dp(24f), videoButtonY + AndroidUtilities.dp(24f))

			buttonX = (x + (photoImage.imageWidth - AndroidUtilities.dp(48f)) / 2.0f).toInt()
			buttonY = (photoImage.imageY + (photoImage.imageHeight - AndroidUtilities.dp(48f)) / 2).toInt()

			radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48f), buttonY + AndroidUtilities.dp(48f))
		}
	}

	override fun getAlpha(): Float {
		return if (alphaPropertyWorkaround) {
			alphaInternal
		}
		else super.getAlpha()
	}

	override fun setAlpha(alpha: Float) {
		if (alpha == 1f != (getAlpha() == 1f)) {
			invalidate()
		}

		if (alphaPropertyWorkaround) {
			alphaInternal = alpha
			invalidate()
		}
		else {
			super.setAlpha(alpha)
		}
	}

	fun getCurrentBackgroundLeft(): Int {
		var left = currentBackgroundDrawable!!.bounds.left

		if (!currentMessageObject!!.isOutOwner && transitionParams.changePinnedBottomProgress != 1f && !mediaBackground && !drawPinnedBottom) {
			left -= AndroidUtilities.dp(6f)
		}

		return left
	}

	fun getTopMediaOffset(): Int {
		return if (currentMessageObject != null && currentMessageObject?.type == MessageObject.TYPE_MUSIC) {
			mediaOffsetY + namesOffset
		}
		else 0
	}

	fun isPlayingRound(): Boolean {
		return isRoundVideo && isPlayingRound
	}

	fun getParentWidth(): Int {
		val `object` = if (currentMessageObject == null) messageObjectToSet else currentMessageObject

		return if (`object` != null && `object`.preview && parentWidth > 0) {
			parentWidth
		}
		else {
			AndroidUtilities.displaySize.x
		}
	}

	private fun getThemedColor(key: String): Int {
		return Theme.getColor(key)
	}

	private fun getThemedDrawable(key: String): Drawable {
		val themeDrawable = Theme.getThemeDrawable(key)

		if (themeDrawable is MessageDrawable) {
			if (isBot && key == Theme.key_drawable_msgOut) {
				themeDrawable.setBackgroundColorId(if(currentMessageObject?.dialogId == BuildConfig.SUPPORT_BOT_ID) R.color.avatar_light_blue else R.color.brand)
			}
			else {
				themeDrawable.setBackgroundColorId(0)
			}
		}

		return themeDrawable
	}

	private fun getThemedPaint(paintKey: String): Paint {
		return Theme.getThemePaint(paintKey)
	}

	private fun hasGradientService(): Boolean {
		return Theme.hasGradientService()
	}

	class BotButton {
		var x = 0
		var y = 0
		var width = 0
		var height = 0
		var title: StaticLayout? = null
		var button: TLRPC.KeyboardButton? = null
		var angle = 0
		var progressAlpha = 0f
		var lastUpdateTime: Long = 0
		var isInviteButton = false
	}

	class PollButton {
		var x = 0
		var y = 0
		var height = 0
		var percent = 0
		var decimal = 0f
		var prevPercent = 0
		var percentProgress = 0f
		var prevPercentProgress = 0f
		var chosen = false
		var count = 0
		var prevChosen = false
		var correct = false
		var title: StaticLayout? = null
		var answer: TLRPC.TLPollAnswer? = null
	}

	private inner class MessageAccessibilityNodeProvider : AccessibilityNodeProvider() {
		private val linkPath = Path()
		private val rectF = RectF()
		private val rect = Rect()

		override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
			val pos = intArrayOf(0, 0)

			getLocationOnScreen(pos)

			return if (virtualViewId == HOST_VIEW_ID) {
				val info = AccessibilityNodeInfo.obtain(this@ChatMessageCell)

				onInitializeAccessibilityNodeInfo(info)

				if (accessibilityText == null) {
					val sb = SpannableStringBuilder()

					if (isChat && currentUser != null && !currentMessageObject!!.isOut) {
						sb.append(getUserName(currentUser))
						sb.setSpan(ProfileSpan(currentUser!!), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.append('\n')
					}

					if (drawForwardedName) {
						for (a in 0..1) {
							if (forwardedNameLayout[a] != null) {
								sb.append(forwardedNameLayout[a]!!.text)
								sb.append(if (a == 0) " " else "\n")
							}
						}
					}

					if (!TextUtils.isEmpty(currentMessageObject!!.messageText)) {
						sb.append(currentMessageObject!!.messageText)
					}

					if (documentAttach != null && (documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF || documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO)) {
						if (buttonState == 1 && loadingProgressLayout != null) {
							sb.append("\n")

							val sending = currentMessageObject!!.isSending
							val key = if (sending) "AccDescrUploadProgress" else "AccDescrDownloadProgress"
							val resId = if (sending) R.string.AccDescrUploadProgress else R.string.AccDescrDownloadProgress

							sb.append(LocaleController.formatString(key, resId, AndroidUtilities.formatFileSize(currentMessageObject!!.loadedFileSize), AndroidUtilities.formatFileSize(lastLoadingSizeTotal)))
						}
					}

					if (currentMessageObject!!.isMusic) {
						sb.append("\n")
						sb.append(LocaleController.formatString("AccDescrMusicInfo", R.string.AccDescrMusicInfo, currentMessageObject!!.musicAuthor, currentMessageObject!!.musicTitle))
						sb.append(", ")
						sb.append(LocaleController.formatDuration(currentMessageObject!!.duration))
					}
					else if (currentMessageObject!!.isVoice || isRoundVideo) {
						sb.append(", ")
						sb.append(LocaleController.formatDuration(currentMessageObject!!.duration))
						sb.append(", ")

						if (currentMessageObject!!.isContentUnread) {
							sb.append(context.getString(R.string.AccDescrMsgNotPlayed))
						}
						else {
							sb.append(context.getString(R.string.AccDescrMsgPlayed))
						}
					}

					if (lastPoll != null) {
						sb.append(", ")
						sb.append(lastPoll!!.question)
						sb.append(", ")

						val title = if (pollClosed) {
							context.getString(R.string.FinalResults)
						}
						else {
							if (lastPoll!!.quiz) {
								if (lastPoll!!.publicVoters) {
									context.getString(R.string.QuizPoll)
								}
								else {
									context.getString(R.string.AnonymousQuizPoll)
								}
							}
							else if (lastPoll!!.publicVoters) {
								context.getString(R.string.PublicPoll)
							}
							else {
								context.getString(R.string.AnonymousPoll)
							}
						}

						sb.append(title)
					}

					if (currentMessageObject!!.isVoiceTranscriptionOpen) {
						sb.append("\n")
						sb.append(currentMessageObject!!.voiceTranscription)
					}
					else {
						if (MessageObject.getMedia(currentMessageObject!!.messageOwner) != null && !TextUtils.isEmpty(currentMessageObject!!.caption)) {
							sb.append("\n")
							sb.append(currentMessageObject!!.caption)
						}
					}

					if (documentAttach != null) {
						if (documentAttachType == DOCUMENT_ATTACH_TYPE_VIDEO) {
							sb.append(", ")
							sb.append(LocaleController.formatDuration(currentMessageObject!!.duration))
						}

						if (buttonState == 0 || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
							sb.append(", ")
							sb.append(AndroidUtilities.formatFileSize(documentAttach!!.size))
						}
					}

					if (currentMessageObject!!.isOut) {
						if (currentMessageObject!!.isSent) {
							sb.append("\n")

							if (currentMessageObject!!.scheduled) {
								sb.append(LocaleController.formatString("AccDescrScheduledDate", R.string.AccDescrScheduledDate, currentTimeString))
							}
							else {
								sb.append(LocaleController.formatString("AccDescrSentDate", R.string.AccDescrSentDate, context.getString(R.string.TodayAt) + " " + currentTimeString))
								sb.append(", ")
								sb.append(if (currentMessageObject!!.isUnread) context.getString(R.string.AccDescrMsgUnread) else context.getString(R.string.AccDescrMsgRead))
							}
						}
						else if (currentMessageObject!!.isSending) {
							sb.append("\n")
							sb.append(context.getString(R.string.AccDescrMsgSending))

							val sendingProgress = radialProgress.progress

							if (sendingProgress > 0f) {
								sb.append(", ").append((sendingProgress * 100).roundToLong().toString()).append("%")
							}
						}
						else if (currentMessageObject!!.isSendError) {
							sb.append("\n")
							sb.append(context.getString(R.string.AccDescrMsgSendingError))
						}
					}
					else {
						sb.append("\n")
						sb.append(LocaleController.formatString("AccDescrReceivedDate", R.string.AccDescrReceivedDate, context.getString(R.string.TodayAt) + " " + currentTimeString))
					}

					if (repliesCount > 0 && !hasCommentLayout()) {
						sb.append("\n")
						sb.append(LocaleController.formatPluralString("AccDescrNumberOfReplies", repliesCount))
					}

					if (currentMessageObject?.messageOwner?.reactions?.results != null) {
						if (currentMessageObject?.messageOwner?.reactions?.results?.size == 1) {
							val reaction = currentMessageObject!!.messageOwner!!.reactions!!.results[0]
							val emoticon = (reaction.reaction as? TLRPC.TLReactionEmoji)?.emoticon ?: ""

							if (reaction.count == 1) {
								sb.append("\n")

								var isMe = false
								var userName = ""

								if (currentMessageObject?.messageOwner?.reactions?.recentReactions != null && currentMessageObject?.messageOwner?.reactions?.recentReactions?.size == 1) {
									val recentReaction = currentMessageObject?.messageOwner?.reactions?.recentReactions?.firstOrNull()

									if (recentReaction != null) {
										val user = MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(recentReaction.peerId))

										isMe = isUserSelf(user)

										if (user != null) {
											userName = getFirstName(user)
										}
									}
								}

								if (isMe) {
									sb.append(LocaleController.formatString("AccDescrYouReactedWith", R.string.AccDescrYouReactedWith, emoticon))
								}
								else {
									sb.append(LocaleController.formatString("AccDescrReactedWith", R.string.AccDescrReactedWith, userName, emoticon))
								}
							}
							else if (reaction.count > 1) {
								sb.append("\n")
								sb.append(LocaleController.formatPluralString("AccDescrNumberOfPeopleReactions", reaction.count, emoticon))
							}
						}
						else {
							sb.append(context.getString(R.string.Reactions)).append(": ")

							val count = currentMessageObject?.messageOwner?.reactions?.results?.size ?: 0

							for (i in 0 until count) {
								val reactionCount = currentMessageObject?.messageOwner?.reactions?.results?.get(i)

								if (reactionCount != null) {
									val emoticon = (reactionCount.reaction as? TLRPC.TLReactionEmoji)?.emoticon ?: ""

									sb.append(emoticon).append(" ").append(reactionCount.count.toString() + "")

									if (i + 1 < count) {
										sb.append(", ")
									}
								}
							}

							sb.append("\n")
						}
					}

					if (currentMessageObject!!.messageOwner!!.flags and TLRPC.MESSAGE_FLAG_HAS_VIEWS != 0) {
						sb.append("\n")
						sb.append(LocaleController.formatPluralString("AccDescrNumberOfViews", currentMessageObject!!.messageOwner!!.views))
					}

					sb.append("\n")

					val links: Array<out CharacterStyle> = sb.getSpans(0, sb.length, ClickableSpan::class.java)

					for (link in links) {
						val start = sb.getSpanStart(link)
						val end = sb.getSpanEnd(link)

						sb.removeSpan(link)

						val underlineSpan = object : ClickableSpan() {
							override fun onClick(view: View) {
								if (link is ProfileSpan) {
									link.onClick(view)
								}
								else {
									delegate?.didPressUrl(this@ChatMessageCell, link, false)
								}
							}
						}

						sb.setSpan(underlineSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}

					accessibilityText = sb
				}

				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
					info.contentDescription = accessibilityText?.toString()
				}
				else {
					info.text = accessibilityText
				}

				info.isEnabled = true

				val itemInfo = info.collectionItemInfo

				if (itemInfo != null) {
					info.collectionItemInfo = CollectionItemInfo.obtain(itemInfo.rowIndex, 1, 0, 1, false)
				}

				info.addAction(AccessibilityAction(R.id.acc_action_msg_options, context.getString(R.string.AccActionMessageOptions)))

				val icon: Int = iconForCurrentState
				var actionLabel: CharSequence? = null

				when (icon) {
					MediaActionDrawable.ICON_PLAY -> actionLabel = context.getString(R.string.AccActionPlay)
					MediaActionDrawable.ICON_PAUSE -> actionLabel = context.getString(R.string.AccActionPause)
					MediaActionDrawable.ICON_FILE -> actionLabel = context.getString(R.string.AccActionOpenFile)
					MediaActionDrawable.ICON_DOWNLOAD -> actionLabel = context.getString(R.string.AccActionDownload)
					MediaActionDrawable.ICON_CANCEL -> actionLabel = context.getString(R.string.AccActionCancelDownload)
					else -> if (currentMessageObject?.type == MessageObject.TYPE_CALL) {
						actionLabel = context.getString(R.string.CallAgain)
					}
				}

				info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, actionLabel))
				info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, context.getString(R.string.AccActionEnterSelectionMode)))

				val smallIcon = miniIconForCurrentState

				if (smallIcon == MediaActionDrawable.ICON_DOWNLOAD) {
					info.addAction(AccessibilityAction(R.id.acc_action_small_button, context.getString(R.string.AccActionDownload)))
				}

				if ((currentMessageObject!!.isVoice || currentMessageObject!!.isRoundVideo || currentMessageObject!!.isMusic) && MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
					seekBarAccessibilityDelegate.onInitializeAccessibilityNodeInfoInternal(info)
				}

				if (useTranscribeButton && transcribeButton != null) {
					info.addChild(this@ChatMessageCell, Companion.TRANSCRIBE)
				}

				var i: Int

				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
					if (isChat && currentUser != null && !currentMessageObject!!.isOut) {
						info.addChild(this@ChatMessageCell, Companion.PROFILE)
					}

					if (currentMessageObject!!.messageText is Spannable) {
						val buffer = currentMessageObject!!.messageText as Spannable
						val links: Array<out CharacterStyle> = buffer.getSpans(0, buffer.length, ClickableSpan::class.java)

						i = 0

						for (link in links) {
							info.addChild(this@ChatMessageCell, Companion.LINK_IDS_START + i)
							i++
						}
					}

					if (currentMessageObject!!.caption is Spannable && captionLayout != null) {
						val buffer = currentMessageObject!!.caption as Spannable
						val links: Array<out CharacterStyle> = buffer.getSpans(0, buffer.length, ClickableSpan::class.java)

						i = 0

						for (link in links) {
							info.addChild(this@ChatMessageCell, Companion.LINK_CAPTION_IDS_START + i)
							i++
						}
					}
				}

				i = 0

				for (button in botButtons) {
					info.addChild(this@ChatMessageCell, Companion.BOT_BUTTONS_START + i)
					i++
				}

				if (hintButtonVisible && pollHintX != -1 && currentMessageObject!!.isPoll) {
					info.addChild(this@ChatMessageCell, Companion.POLL_HINT)
				}

				i = 0

				for (button in pollButtons) {
					info.addChild(this@ChatMessageCell, Companion.POLL_BUTTONS_START + i)
					i++
				}

				if (drawInstantView && !instantButtonRect.isEmpty) {
					info.addChild(this@ChatMessageCell, Companion.INSTANT_VIEW)
				}

				if (commentLayout != null) {
					info.addChild(this@ChatMessageCell, Companion.COMMENT)
				}

				if (drawSideButton == 1) {
					info.addChild(this@ChatMessageCell, Companion.SHARE)
				}

				if (replyNameLayout != null) {
					info.addChild(this@ChatMessageCell, Companion.REPLY)
				}

				if (forwardedNameLayout[0] != null && forwardedNameLayout[1] != null) {
					info.addAction(AccessibilityAction(R.id.acc_action_open_forwarded_origin, context.getString(R.string.AccActionOpenForwardedOrigin)))
				}

				if (drawSelectionBackground || background != null) {
					info.isSelected = true
				}

				info
			}
			else {
				val info = AccessibilityNodeInfo.obtain()
				info.setSource(this@ChatMessageCell, virtualViewId)
				info.setParent(this@ChatMessageCell)
				info.packageName = context.packageName

				if (virtualViewId == Companion.PROFILE) {
					if (currentUser == null) {
						return null
					}

					val content = getUserName(currentUser)

					info.text = content

					rect.set(nameX.toInt(), nameY.toInt(), (nameX + nameWidth).toInt(), (nameY + if (nameLayout != null) nameLayout!!.height else 10).toInt())

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.className = "android.widget.TextView"
					info.isEnabled = true
					info.isClickable = true
					info.isLongClickable = true
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
				}
				else if (virtualViewId >= Companion.LINK_CAPTION_IDS_START) {
					if (currentMessageObject?.caption !is Spannable || captionLayout == null) {
						return null
					}

					val buffer = currentMessageObject!!.caption as Spannable
					val link = getLinkById(virtualViewId, true) ?: return null
					val linkPos = getRealSpanStartAndEnd(buffer, link)
					val content = buffer.subSequence(linkPos[0], linkPos[1]).toString()

					info.text = content

					captionLayout?.getSelectionPath(linkPos[0], linkPos[1], linkPath)

					linkPath.computeBounds(rectF, true)

					rect.set(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt())
					rect.offset(captionX.toInt(), captionY.toInt())

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.className = "android.widget.TextView"
					info.isEnabled = true
					info.isClickable = true
					info.isLongClickable = true
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
				}
				else if (virtualViewId >= Companion.LINK_IDS_START) {
					if (currentMessageObject?.messageText !is Spannable) {
						return null
					}

					val buffer = currentMessageObject!!.messageText as Spannable
					val link = getLinkById(virtualViewId, false) ?: return null
					val linkPos = getRealSpanStartAndEnd(buffer, link)
					val content = buffer.subSequence(linkPos[0], linkPos[1]).toString()

					info.text = content

					for (block in currentMessageObject!!.textLayoutBlocks!!) {
						val length = block.textLayout?.text?.length ?: 0

						if (block.charactersOffset <= linkPos[0] && block.charactersOffset + length >= linkPos[1]) {
							block.textLayout?.getSelectionPath(linkPos[0] - block.charactersOffset, linkPos[1] - block.charactersOffset, linkPath)

							linkPath.computeBounds(rectF, true)

							rect.set(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt())
							rect.offset(0, block.textYOffset.toInt())
							rect.offset(textX, textY)

							info.setBoundsInParent(rect)

							if (accessibilityVirtualViewBounds[virtualViewId] == null) {
								accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
							}

							rect.offset(pos[0], pos[1])

							info.setBoundsInScreen(rect)

							break
						}
					}

					info.className = "android.widget.TextView"
					info.isEnabled = true
					info.isClickable = true
					info.isLongClickable = true
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
				}
				else if (virtualViewId >= Companion.BOT_BUTTONS_START) {
					val buttonIndex = virtualViewId - Companion.BOT_BUTTONS_START

					if (buttonIndex >= botButtons.size) {
						return null
					}

					val button = botButtons[buttonIndex]

					info.text = button.title!!.text
					info.className = "android.widget.Button"
					info.isEnabled = true
					info.isClickable = true
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					rect.set(button.x, button.y, button.x + button.width, button.y + button.height)

					val addX = if (currentMessageObject!!.isOutOwner) {
						measuredWidth - widthForButtons - AndroidUtilities.dp(10f)
					}
					else {
						backgroundDrawableLeft + AndroidUtilities.dp(if (mediaBackground) 1f else 7f)
					}

					rect.offset(addX, layoutHeight)

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
				}
				else if (virtualViewId >= Companion.POLL_BUTTONS_START) {
					val buttonIndex = virtualViewId - Companion.POLL_BUTTONS_START

					if (buttonIndex >= pollButtons.size) {
						return null
					}

					val button = pollButtons[buttonIndex]
					val sb = StringBuilder(button.title!!.text)

					if (!pollVoted) {
						info.className = "android.widget.Button"
					}
					else {
						info.isSelected = button.chosen

						sb.append(", ").append(button.percent).append("%")

						if (lastPoll != null && lastPoll!!.quiz && (button.chosen || button.correct)) {
							sb.append(", ").append(if (button.correct) context.getString(R.string.AccDescrQuizCorrectAnswer) else context.getString(R.string.AccDescrQuizIncorrectAnswer))
						}
					}

					info.text = sb
					info.isEnabled = true
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					val y = button.y + namesOffset
					val w = backgroundWidth - AndroidUtilities.dp(76f)

					rect.set(button.x, y, button.x + w, y + button.height)

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.isClickable = true
				}
				else if (virtualViewId == Companion.POLL_HINT) {
					info.className = "android.widget.Button"
					info.isEnabled = true
					info.text = context.getString(R.string.AccDescrQuizExplanation)
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					rect.set(pollHintX - AndroidUtilities.dp(8f), pollHintY - AndroidUtilities.dp(8f), pollHintX + AndroidUtilities.dp(32f), pollHintY + AndroidUtilities.dp(32f))

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null || accessibilityVirtualViewBounds[virtualViewId] != rect) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.isClickable = true
				}
				else if (virtualViewId == Companion.INSTANT_VIEW) {
					info.className = "android.widget.Button"
					info.isEnabled = true

					if (instantViewLayout != null) {
						info.text = instantViewLayout?.text
					}

					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					instantButtonRect.round(rect)

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null || accessibilityVirtualViewBounds[virtualViewId] != rect) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.isClickable = true
				}
				else if (virtualViewId == Companion.SHARE) {
					info.className = "android.widget.ImageButton"
					info.isEnabled = true

					if (isOpenChatByShare(currentMessageObject!!)) {
						info.contentDescription = context.getString(R.string.AccDescrOpenChat)
					}
					else {
						info.contentDescription = context.getString(R.string.ShareFile)
					}

					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					rect.set(sideStartX.toInt(), sideStartY.toInt(), sideStartX.toInt() + AndroidUtilities.dp(40f), sideStartY.toInt() + AndroidUtilities.dp(32f))

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null || accessibilityVirtualViewBounds[virtualViewId] != rect) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.isClickable = true
				}
				else if (virtualViewId == Companion.REPLY) {
					info.isEnabled = true

					val sb = StringBuilder()
					sb.append(context.getString(R.string.Reply))
					sb.append(", ")

					if (replyNameLayout != null) {
						sb.append(replyNameLayout!!.text)
						sb.append(", ")
					}

					if (replyTextLayout != null) {
						sb.append(replyTextLayout!!.text)
					}

					info.contentDescription = sb.toString()
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					rect.set(replyStartX, replyStartY, replyStartX + max(replyNameWidth, replyTextWidth), replyStartY + AndroidUtilities.dp(35f))

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null || accessibilityVirtualViewBounds[virtualViewId] != rect) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.isClickable = true
				}
				else if (virtualViewId == Companion.FORWARD) {
					info.isEnabled = true

					val sb = StringBuilder()

					if (forwardedNameLayout[0] != null && forwardedNameLayout[1] != null) {
						for (a in 0..1) {
							sb.append(forwardedNameLayout[a]!!.text)
							sb.append(if (a == 0) " " else "\n")
						}
					}

					info.contentDescription = sb.toString()
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					val x = min(forwardNameX - forwardNameOffsetX[0], forwardNameX - forwardNameOffsetX[1]).toInt()

					rect.set(x, forwardNameY, x + forwardedNameWidth, forwardNameY + AndroidUtilities.dp(32f))

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null || accessibilityVirtualViewBounds[virtualViewId] != rect) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.isClickable = true
				}
				else if (virtualViewId == Companion.COMMENT) {
					info.className = "android.widget.Button"
					info.isEnabled = true

					val commentCount: Int = repliesCount
					var comment: String? = null

					if (currentMessageObject != null && !currentMessageObject!!.shouldDrawWithoutBackground() && !currentMessageObject!!.isAnimatedEmoji) {
						comment = if (isRepliesChat) {
							context.getString(R.string.ViewInChat)
						}
						else {
							if (commentCount == 0) context.getString(R.string.LeaveAComment) else LocaleController.formatPluralString("CommentsCount", commentCount)
						}
					}
					else if (!isRepliesChat && commentCount > 0) {
						comment = LocaleController.formatShortNumber(commentCount, null)
					}

					if (comment != null) {
						info.text = comment
					}

					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					rect.set(commentButtonRect)

					info.setBoundsInParent(rect)

					if (accessibilityVirtualViewBounds[virtualViewId] == null || accessibilityVirtualViewBounds[virtualViewId] != rect) {
						accessibilityVirtualViewBounds.put(virtualViewId, Rect(rect))
					}

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.isClickable = true
				}
				else if (virtualViewId == Companion.TRANSCRIBE) {
					info.className = "android.widget.Button"
					info.isEnabled = true
					info.text = if (currentMessageObject!!.isVoiceTranscriptionOpen) context.getString(R.string.AccActionCloseTranscription) else context.getString(R.string.AccActionOpenTranscription)
					info.addAction(AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

					rect.set(transcribeX.toInt(), transcribeY.toInt(), (transcribeX + AndroidUtilities.dp(30f)).toInt(), (transcribeY + AndroidUtilities.dp(30f)).toInt())

					info.setBoundsInParent(rect)

					rect.offset(pos[0], pos[1])

					info.setBoundsInScreen(rect)
					info.isClickable = true
				}

				info.isFocusable = true
				info.isVisibleToUser = true
				info
			}
		}

		override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
			if (virtualViewId == HOST_VIEW_ID) {
				performAccessibilityAction(action, arguments)
			}
			else {
				if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
					sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
				}
				else if (action == AccessibilityNodeInfo.ACTION_CLICK) {
					if (virtualViewId == Companion.PROFILE) {
						delegate?.didPressUserAvatar(this@ChatMessageCell, currentUser, 0f, 0f)
					}
					else if (virtualViewId >= Companion.LINK_CAPTION_IDS_START) {
						val link = getLinkById(virtualViewId, true)

						if (link != null) {
							delegate?.didPressUrl(this@ChatMessageCell, link, false)
							sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
						}
					}
					else if (virtualViewId >= Companion.LINK_IDS_START) {
						val link = getLinkById(virtualViewId, false)

						if (link != null) {
							delegate?.didPressUrl(this@ChatMessageCell, link, false)
							sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
						}
					}
					else if (virtualViewId >= Companion.BOT_BUTTONS_START) {
						val buttonIndex = virtualViewId - Companion.BOT_BUTTONS_START

						if (buttonIndex >= botButtons.size) {
							return false
						}

						val button = botButtons[buttonIndex]

						if (button.button != null) {
							delegate?.didPressBotButton(this@ChatMessageCell, button.button)
						}

						sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
					}
					else if (virtualViewId >= Companion.POLL_BUTTONS_START) {
						val buttonIndex = virtualViewId - Companion.POLL_BUTTONS_START

						if (buttonIndex >= pollButtons.size) {
							return false
						}

						val button = pollButtons[buttonIndex]

						if (delegate != null) {
							val answers = ArrayList<TLRPC.TLPollAnswer>()

							button.answer?.let {
								answers.add(it)
							}

							delegate?.didPressVoteButtons(this@ChatMessageCell, answers, -1, 0, 0)
						}

						sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
					}
					else if (virtualViewId == Companion.POLL_HINT) {
						delegate?.didPressHint(this@ChatMessageCell, 0)
					}
					else if (virtualViewId == Companion.INSTANT_VIEW) {
						delegate?.didPressInstantButton(this@ChatMessageCell, drawInstantViewType)
					}
					else if (virtualViewId == Companion.SHARE) {
						delegate?.didPressSideButton(this@ChatMessageCell)
					}
					else if (virtualViewId == Companion.REPLY) {
						if (delegate != null && (!isThreadChat || currentMessageObject!!.replyTopMsgId != 0) && currentMessageObject!!.hasValidReplyMessageObject()) {
							delegate?.didPressReplyMessage(this@ChatMessageCell, currentMessageObject!!.replyMsgId)
						}
					}
					else if (virtualViewId == Companion.FORWARD) {
						if (delegate != null) {
							if (currentForwardChannel != null) {
								delegate?.didPressChannelAvatar(this@ChatMessageCell, currentForwardChannel, currentMessageObject!!.messageOwner?.fwdFrom?.channelPost ?: 0, lastTouchX, lastTouchY)
							}
							else if (currentForwardUser != null) {
								delegate?.didPressUserAvatar(this@ChatMessageCell, currentForwardUser, lastTouchX, lastTouchY)
							}
							else if (currentForwardName != null) {
								delegate?.didPressHiddenForward(this@ChatMessageCell)
							}
						}
					}
					else if (virtualViewId == Companion.COMMENT) {
						if (delegate != null) {
							if (isRepliesChat) {
								delegate?.didPressSideButton(this@ChatMessageCell)
							}
							else {
								delegate?.didPressCommentButton(this@ChatMessageCell)
							}
						}
					}
					else if (virtualViewId == Companion.TRANSCRIBE && transcribeButton != null) {
						transcribeButton?.onTap()
					}
				}
				else if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
					val link = getLinkById(virtualViewId, virtualViewId >= Companion.LINK_CAPTION_IDS_START)

					if (link != null) {
						delegate?.didPressUrl(this@ChatMessageCell, link, true)
						sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
					}
				}
			}

			return true
		}

		private fun getLinkById(id: Int, caption: Boolean): ClickableSpan? {
			@Suppress("NAME_SHADOWING") var id = id

			if (id == Companion.PROFILE) {
				return null
			}

			return if (caption) {
				id -= Companion.LINK_CAPTION_IDS_START

				if (currentMessageObject!!.caption !is Spannable || id < 0) {
					return null
				}

				val buffer = currentMessageObject!!.caption as Spannable
				val links = buffer.getSpans(0, buffer.length, ClickableSpan::class.java)

				if (links.size <= id) {
					null
				}
				else {
					links[id]
				}
			}
			else {
				id -= Companion.LINK_IDS_START

				if (currentMessageObject!!.messageText !is Spannable || id < 0) {
					return null
				}

				val buffer = currentMessageObject!!.messageText as Spannable
				val links = buffer.getSpans(0, buffer.length, ClickableSpan::class.java)

				if (links.size <= id) {
					null
				}
				else {
					links[id]
				}
			}
		}

		private inner class ProfileSpan(private val user: User) : ClickableSpan() {
			override fun onClick(view: View) {
				delegate?.didPressUserAvatar(this@ChatMessageCell, user, 0f, 0f)
			}
		}
	}

	inner class TransitionParams {
		private val lastDrawBotButtons = ArrayList<BotButton>()
		val transitionBotButtons = ArrayList<BotButton>()
		var lastDrawingImageX = 0f
		var lastDrawingImageY = 0f
		var lastDrawingImageW = 0f
		var lastDrawingImageH = 0f
		var lastDrawingCaptionX = 0f
		var lastDrawingCaptionY = 0f

		@JvmField
		var animateChange = false

		var animateFromRepliesTextWidth = 0
		var messageEntering = false
		var animateLocationIsExpired = false
		var lastLocationIsExpired = false
		var lastDrawLocationExpireText: String? = null
		var lastDrawLocationExpireProgress = 0f
		var lastDrawDocTitleLayout: StaticLayout? = null
		var lastDrawInfoLayout: StaticLayout? = null
		var updatePhotoImageX = false
		var animateRoundVideoDotY = false
		var lastDrawRoundVideoDotY = 0f
		var animateFromRoundVideoDotY = 0f
		var animateReplyY = false
		var lastDrawReplyY = 0f
		var animateFromReplyY = 0f
		var lastTimeXPinned = 0f
		var animateFromTimeXPinned = 0f
		var imageChangeBoundsTransition = false
		var deltaLeft = 0f
		var deltaRight = 0f
		var deltaBottom = 0f
		var deltaTop = 0f

		// in animation, describe to what values deltaLeft and deltaRight moves to
		var toDeltaLeft = 0f

		var toDeltaRight = 0f
		var animateToImageX = 0f
		var animateToImageY = 0f
		var animateToImageW = 0f
		var animateToImageH = 0f
		var captionFromX = 0f
		var captionFromY = 0f
		var imageRoundRadius = IntArray(4)
		var captionEnterProgress = 1f
		var wasDraw = false

		@JvmField
		var animateBackgroundBoundsInner = false

		var animateBackgroundWidth = false
		var ignoreAlpha = false
		var drawPinnedBottomBackground = false
		var changePinnedBottomProgress = 1f
		var animateToRadius: IntArray? = null
		var animateRadius = false
		var transformGroupToSingleMessage = false
		var lastDrawingBackgroundRect = Rect()
		var lastDrawTime = false
		var lastTimeX = 0
		var animateFromTimeX = 0
		var shouldAnimateTimeX = false
		var lastBackgroundLeft = 0
		var lastBackgroundRight = 0
		var animateDrawingTimeAlpha = false

		@JvmField
		var animateChangeProgress = 1f

		var lastStatusDrawableParams = -1
		var animatePlayingRound = false
		var animateText = false
		var lastDrawingTextY = 0f
		var lastDrawingTextX = 0f
		var animateFromTextY = 0f
		var lastTopOffset = 0
		var animateForwardedLayout = false
		var animateForwardedNamesOffset = 0
		var lastForwardedNamesOffset = 0
		var lastDrawnForwardedName = false
		var lastDrawnForwardedNameLayout = arrayOfNulls<StaticLayout>(2)
		var animatingForwardedNameLayout = arrayOfNulls<StaticLayout>(2)
		var animateMessageText = false
		var animateReplaceCaptionLayout = false
		var animateForwardNameX = 0f
		var lastForwardNameX = 0f
		var animateForwardNameWidth = 0
		var lastForwardNameWidth = 0
		var animateBotButtonsChanged = false
		private var lastIsPinned = false
		var animatePinned = false
		private var lastRepliesCount = 0
		var animateReplies = false
		private var lastRepliesLayout: StaticLayout? = null
		var animateRepliesLayout: StaticLayout? = null
		var animateFromTimeXReplies = 0f
		var lastTimeXReplies = 0f
		var animateFromTimeXViews = 0f
		var lastTimeXViews = 0f
		private var lastCommentsCount = 0
		private var lastTotalCommentWidth = 0
		private var lastCommentArrowX = 0
		private var lastCommentUnreadX = 0
		private var lastCommentDrawUnread = false
		private var lastCommentX = 0f
		private var lastDrawCommentNumber = false
		private var lastCommentLayout: StaticLayout? = null
		var animateComments = false
		var animateCommentsLayout: StaticLayout? = null
		var animateCommentX = 0f
		var animateTotalCommentWidth = 0
		var animateCommentArrowX = 0
		var animateCommentUnreadX = 0
		var animateCommentDrawUnread = false
		var animateDrawCommentNumber = false
		var animateSign = false
		var animateNameX = 0f
		private var lastSignMessage: String? = null
		var moveCaption = false
		var animateOutTextBlocks: ArrayList<TextLayoutBlock>? = null
		private var lastDrawingTextBlocks: ArrayList<TextLayoutBlock>? = null
		var animateOutAnimateEmoji: EmojiGroupedSpans? = null
		var animateEditedEnter = false
		var animateEditedLayout: StaticLayout? = null
		var animateTimeLayout: StaticLayout? = null
		var animateTimeWidth = 0
		private var lastTimeWidth = 0
		var animateEditedWidthDiff = 0
		private var lastDrawingEdited = false
		var animateOutCaptionLayout: StaticLayout? = null
		private var lastDrawingCaptionLayout: StaticLayout? = null
		private var lastButtonX = 0f
		private var lastButtonY = 0f
		var animateFromButtonX = 0f
		var animateFromButtonY = 0f
		var animateButton = false
		private var lastViewsCount = 0
		private var lastViewsLayout: StaticLayout? = null
		var animateViewsLayout: StaticLayout? = null
		private var lastShouldDrawTimeOnMedia = false
		var animateShouldDrawTimeOnMedia = false
		private var lastShouldDrawMenuDrawable = false
		var animateShouldDrawMenuDrawable = false
		private var lastTimeLayout: StaticLayout? = null
		private var lastIsPlayingRound = false

		fun recordDrawingState() {
			wasDraw = true

			lastDrawingImageX = photoImage.imageX
			lastDrawingImageY = photoImage.imageY
			lastDrawingImageW = photoImage.imageWidth
			lastDrawingImageH = photoImage.imageHeight

			val r = photoImage.getRoundRadius()

			System.arraycopy(r, 0, imageRoundRadius, 0, 4)

			if (currentBackgroundDrawable != null) {
				lastDrawingBackgroundRect.set(currentBackgroundDrawable!!.bounds)
			}

			lastDrawingTextBlocks = currentMessageObject!!.textLayoutBlocks
			lastDrawingEdited = edited
			lastDrawingCaptionX = captionX
			lastDrawingCaptionY = captionY
			lastDrawingCaptionLayout = captionLayout
			lastDrawBotButtons.clear()

			if (botButtons.isNotEmpty()) {
				lastDrawBotButtons.addAll(botButtons)
			}

			if (commentLayout != null) {
				lastCommentsCount = repliesCount
				lastTotalCommentWidth = totalCommentWidth
				lastCommentLayout = commentLayout
				lastCommentArrowX = commentArrowX
				lastCommentUnreadX = commentUnreadX
				lastCommentDrawUnread = commentDrawUnread
				lastCommentX = commentX.toFloat()
				lastDrawCommentNumber = drawCommentNumber
			}

			lastRepliesCount = repliesCount

			this.lastViewsCount = getMessageObject()?.messageOwner?.views ?: 0

			lastRepliesLayout = repliesLayout
			lastViewsLayout = viewsLayout
			lastIsPinned = isPinned
			lastSignMessage = lastPostAuthor
			lastButtonX = buttonX.toFloat()
			lastButtonY = buttonY.toFloat()
			lastDrawTime = !forceNotDrawTime
			lastTimeX = timeX
			lastTimeLayout = timeLayout
			lastTimeWidth = timeWidth
			lastShouldDrawTimeOnMedia = shouldDrawTimeOnMedia()
			lastTopOffset = getTopMediaOffset()
			lastShouldDrawMenuDrawable = shouldDrawMenuDrawable()
			lastLocationIsExpired = locationExpired
			lastIsPlayingRound = isPlayingRound
			lastDrawingTextY = textY.toFloat()
			lastDrawingTextX = textX.toFloat()
			lastDrawnForwardedNameLayout[0] = forwardedNameLayout[0]
			lastDrawnForwardedNameLayout[1] = forwardedNameLayout[1]
			lastDrawnForwardedName = currentMessageObject!!.needDrawForwarded()
			lastForwardNameX = forwardNameX
			lastForwardedNamesOffset = namesOffset
			lastForwardNameWidth = forwardedNameWidth
			lastBackgroundLeft = getCurrentBackgroundLeft()
			lastBackgroundRight = currentBackgroundDrawable!!.bounds.right
			reactionsLayoutInBubble.recordDrawingState()

			lastDrawReplyY = if (replyNameLayout != null) {
				replyStartY.toFloat()
			}
			else {
				0f
			}
		}

		fun recordDrawingStatePreview() {
			lastDrawnForwardedNameLayout[0] = forwardedNameLayout[0]
			lastDrawnForwardedNameLayout[1] = forwardedNameLayout[1]
			lastDrawnForwardedName = currentMessageObject!!.needDrawForwarded()
			lastForwardNameX = forwardNameX
			lastForwardedNamesOffset = namesOffset
			lastForwardNameWidth = forwardedNameWidth
		}

		fun animateChange(): Boolean {
			if (!wasDraw) {
				return false
			}

			var changed = false

			animateMessageText = false

			if (currentMessageObject!!.textLayoutBlocks !== lastDrawingTextBlocks) {
				var sameText = true

				if (currentMessageObject!!.textLayoutBlocks != null && lastDrawingTextBlocks != null && currentMessageObject!!.textLayoutBlocks!!.size == lastDrawingTextBlocks!!.size) {
					for (i in lastDrawingTextBlocks!!.indices) {
						val newText = currentMessageObject?.textLayoutBlocks?.get(i)?.textLayout?.text?.toString()
						val oldText = lastDrawingTextBlocks?.get(i)?.textLayout?.text?.toString()

						if (newText == null && oldText != null || newText != null && oldText == null || newText != oldText) {
							sameText = false
							break
						}
						else {
							animatedEmojiStack?.replaceLayout(currentMessageObject!!.textLayoutBlocks!![i].textLayout, lastDrawingTextBlocks!![i].textLayout)
						}
					}
				}
				else {
					sameText = false
				}

				if (!sameText) {
					animateMessageText = true
					animateOutTextBlocks = lastDrawingTextBlocks
					animateOutAnimateEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this@ChatMessageCell, animateOutAnimateEmoji, lastDrawingTextBlocks, true)
					animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this@ChatMessageCell, animatedEmojiStack, currentMessageObject!!.textLayoutBlocks)
					changed = true
				}
				else {
					animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this@ChatMessageCell, animatedEmojiStack, currentMessageObject!!.textLayoutBlocks)
				}
			}

			if (edited && !lastDrawingEdited && timeLayout != null) {
				val editedStr = context.getString(R.string.EditedMessage)
				val text = timeLayout!!.text.toString()
				val i = text.indexOf(editedStr)

				if (i >= 0) {
					if (i == 0) {
						animateEditedLayout = StaticLayout(editedStr, Theme.chat_timePaint, timeTextWidth + AndroidUtilities.dp(100f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

						val spannableStringBuilder = SpannableStringBuilder()
						spannableStringBuilder.append(editedStr)
						spannableStringBuilder.append(text.substring(editedStr.length))
						spannableStringBuilder.setSpan(EmptyStubSpan(), 0, editedStr.length, 0)

						animateTimeLayout = StaticLayout(spannableStringBuilder, Theme.chat_timePaint, timeTextWidth + AndroidUtilities.dp(100f), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

						animateEditedWidthDiff = timeWidth - lastTimeWidth
					}
					else {
						animateEditedWidthDiff = 0
						animateEditedLayout = null
						animateTimeLayout = lastTimeLayout
					}

					animateEditedEnter = true
					animateTimeWidth = lastTimeWidth
					animateFromTimeX = lastTimeX
					shouldAnimateTimeX = true

					changed = true
				}
			}
			else if (!edited && lastDrawingEdited && timeLayout != null) {
				animateTimeLayout = lastTimeLayout
				animateEditedWidthDiff = timeWidth - lastTimeWidth
				animateEditedEnter = true
				animateTimeWidth = lastTimeWidth
				animateFromTimeX = lastTimeX
				shouldAnimateTimeX = true
				changed = true
			}

			if (captionLayout !== lastDrawingCaptionLayout) {
				val oldCaption = if (lastDrawingCaptionLayout == null) null else lastDrawingCaptionLayout!!.text.toString()
				val currentCaption = if (captionLayout == null) null else captionLayout!!.text.toString()

				if (currentCaption == null != (oldCaption == null) || oldCaption != null && oldCaption != currentCaption) {
					animateReplaceCaptionLayout = true
					animateOutCaptionLayout = lastDrawingCaptionLayout
					animateOutAnimateEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this@ChatMessageCell, null, animateOutCaptionLayout)
					animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this@ChatMessageCell, animatedEmojiStack, captionLayout)
					changed = true
				}
				else {
					updateCaptionLayout()

					if (lastDrawingCaptionX != captionX || lastDrawingCaptionY != captionY) {
						moveCaption = true
						captionFromX = lastDrawingCaptionX
						captionFromY = lastDrawingCaptionY
						changed = true
					}
				}
			}
			else if (captionLayout != null && lastDrawingCaptionLayout != null) {
				updateCaptionLayout()

				if (lastDrawingCaptionX != captionX || lastDrawingCaptionY != captionY) {
					moveCaption = true
					captionFromX = lastDrawingCaptionX
					captionFromY = lastDrawingCaptionY
					changed = true
				}
			}
			if (lastDrawBotButtons.isNotEmpty() || botButtons.isNotEmpty()) {
				if (lastDrawBotButtons.size != botButtons.size) {
					animateBotButtonsChanged = true
				}

				if (!animateBotButtonsChanged) {
					for (i in botButtons.indices) {
						val button1 = botButtons[i]
						val button2 = lastDrawBotButtons[i]

						if (button1.x != button2.x || button1.width != button2.width) {
							animateBotButtonsChanged = true
							break
						}
					}
				}

				if (animateBotButtonsChanged) {
					transitionBotButtons.addAll(lastDrawBotButtons)
				}
			}

			if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_DOCUMENT) {
				if (buttonX.toFloat() != lastButtonX || buttonY.toFloat() != lastButtonY) {
					animateFromButtonX = lastButtonX
					animateFromButtonY = lastButtonY
					animateButton = true
					changed = true
				}
			}

			var timeDrawablesIsChanged = false

			if (lastIsPinned != isPinned) {
				animatePinned = true
				changed = true
				timeDrawablesIsChanged = true
			}

			if ((lastRepliesLayout != null || repliesLayout != null) && lastRepliesCount != repliesCount) {
				animateRepliesLayout = lastRepliesLayout
				animateReplies = true
				changed = true
				timeDrawablesIsChanged = true
			}

			if (lastViewsLayout != null && this.lastViewsCount != getMessageObject()!!.messageOwner!!.views) {
				animateViewsLayout = lastViewsLayout
				changed = true
				timeDrawablesIsChanged = true
			}

			if (commentLayout != null && lastCommentsCount != repliesCount) {
				animateCommentsLayout = if (lastCommentLayout != null && !TextUtils.equals(lastCommentLayout!!.text, commentLayout!!.text)) {
					lastCommentLayout
				}
				else {
					null
				}
				animateTotalCommentWidth = lastTotalCommentWidth
				animateCommentX = lastCommentX
				animateCommentArrowX = lastCommentArrowX
				animateCommentUnreadX = lastCommentUnreadX
				animateCommentDrawUnread = lastCommentDrawUnread
				animateDrawCommentNumber = lastDrawCommentNumber
				animateComments = true
				changed = true
			}

			if (!TextUtils.equals(lastSignMessage, lastPostAuthor)) {
				animateSign = true
				animateNameX = nameX
				changed = true
			}

			if (lastDrawTime == forceNotDrawTime) {
				animateDrawingTimeAlpha = true
				animateViewsLayout = null
				changed = true
			}
			else if (lastShouldDrawTimeOnMedia != shouldDrawTimeOnMedia()) {
				animateEditedEnter = false
				animateShouldDrawTimeOnMedia = true
				animateFromTimeX = lastTimeX
				animateTimeLayout = lastTimeLayout
				animateTimeWidth = lastTimeWidth
				changed = true
			}
			else if (timeDrawablesIsChanged || abs(timeX - lastTimeX) > 1) {
				shouldAnimateTimeX = true
				animateTimeWidth = lastTimeWidth
				animateFromTimeX = lastTimeX
				animateFromTimeXViews = lastTimeXViews
				animateFromTimeXReplies = lastTimeXReplies
				animateFromTimeXPinned = lastTimeXPinned
			}

			if (lastShouldDrawMenuDrawable != shouldDrawMenuDrawable()) {
				animateShouldDrawMenuDrawable = true
			}

			if (lastLocationIsExpired != locationExpired) {
				animateLocationIsExpired = true
			}

			if (lastIsPlayingRound != isPlayingRound) {
				animatePlayingRound = true
				changed = true
			}

			if (lastDrawingTextY != textY.toFloat()) {
				animateText = true
				animateFromTextY = lastDrawingTextY
				changed = true
			}

			if (currentMessageObject != null) {
				if (lastDrawnForwardedName != currentMessageObject!!.needDrawForwarded()) {
					animateForwardedLayout = true
					animatingForwardedNameLayout[0] = lastDrawnForwardedNameLayout[0]
					animatingForwardedNameLayout[1] = lastDrawnForwardedNameLayout[1]
					animateForwardNameX = lastForwardNameX
					animateForwardedNamesOffset = lastForwardedNamesOffset
					animateForwardNameWidth = lastForwardNameWidth
					changed = true
				}
			}

			updateReactionLayoutPosition()

			if (reactionsLayoutInBubble.animateChange()) {
				changed = true
			}

			if (currentMessageObject!!.isRoundVideo) {
				var y1 = (layoutHeight - AndroidUtilities.dp((28 - if (drawPinnedBottom) 2 else 0).toFloat())).toFloat()

				if (!reactionsLayoutInBubble.isEmpty) {
					y1 -= reactionsLayoutInBubble.totalHeight.toFloat()
				}

				if (y1 != lastDrawRoundVideoDotY) {
					animateRoundVideoDotY = true
					animateFromRoundVideoDotY = lastDrawRoundVideoDotY
					changed = true
				}
			}

			if (replyNameLayout != null && replyStartX.toFloat() != lastDrawReplyY && lastDrawReplyY != 0f) {
				animateReplyY = true
				animateFromReplyY = lastDrawReplyY
				changed = true
			}

			return changed
		}

		fun onDetach() {
			wasDraw = false
		}

		fun resetAnimation() {
			animateChange = false
			animatePinned = false
			animateBackgroundBoundsInner = false
			animateBackgroundWidth = false
			deltaLeft = 0f
			deltaRight = 0f
			deltaBottom = 0f
			deltaTop = 0f
			toDeltaLeft = 0f
			toDeltaRight = 0f

			if (imageChangeBoundsTransition && animateToImageW != 0f && animateToImageH != 0f) {
				photoImage.setImageCoordinates(animateToImageX, animateToImageY, animateToImageW, animateToImageH)
			}

			if (animateRadius) {
				animateToRadius?.let {
					photoImage.setRoundRadius(it)
				}
			}

			animateToImageX = 0f
			animateToImageY = 0f
			animateToImageW = 0f
			animateToImageH = 0f

			imageChangeBoundsTransition = false
			changePinnedBottomProgress = 1f
			captionEnterProgress = 1f
			animateRadius = false
			animateChangeProgress = 1f
			animateMessageText = false
			animateOutTextBlocks = null
			animateEditedLayout = null
			animateTimeLayout = null
			animateEditedEnter = false
			animateReplaceCaptionLayout = false
			transformGroupToSingleMessage = false
			animateOutCaptionLayout = null

			AnimatedEmojiSpan.release(animateOutAnimateEmoji)

			animateOutAnimateEmoji = null
			moveCaption = false
			animateDrawingTimeAlpha = false
			transitionBotButtons.clear()
			animateButton = false
			animateReplies = false
			animateRepliesLayout = null
			animateComments = false
			animateCommentsLayout = null
			animateViewsLayout = null
			animateShouldDrawTimeOnMedia = false
			animateShouldDrawMenuDrawable = false
			shouldAnimateTimeX = false
			animateSign = false
			animateLocationIsExpired = false
			animatePlayingRound = false
			animateText = false
			animateForwardedLayout = false
			animatingForwardedNameLayout[0] = null
			animatingForwardedNameLayout[1] = null
			animateRoundVideoDotY = false
			animateReplyY = false
			reactionsLayoutInBubble.resetAnimation()
		}

		fun supportChangeAnimation(): Boolean {
			return true
		}

		fun createStatusDrawableParams(): Int {
			return if (currentMessageObject!!.isOutOwner) {
				var drawCheck1 = false
				var drawCheck2 = false
				var drawClock = false
				var drawError = false

				if (currentMessageObject!!.isSending || currentMessageObject!!.isEditing) {
					drawClock = true
				}
				else if (currentMessageObject!!.isSendError) {
					drawError = true
				}
				else if (currentMessageObject!!.isSent) {
					if (!currentMessageObject!!.scheduled && !currentMessageObject!!.isUnread) {
						drawCheck1 = true
					}

					drawCheck2 = true
				}

				(if (drawCheck1) 1 else 0) or (if (drawCheck2) 2 else 0) or (if (drawClock) 4 else 0) or if (drawError) 8 else 0
			}
			else {
				val drawClock = currentMessageObject!!.isSending || currentMessageObject!!.isEditing
				val drawError = currentMessageObject!!.isSendError
				(if (drawClock) 4 else 0) or if (drawError) 8 else 0
			}
		}
	}

	private var isGroup = false

	private fun getAdditionalHeight(): Int {
		if (isGroup) {
			return 0
		}

		return ADDITIONAL_HEIGHT
	}

	companion object {
		private val ADDITIONAL_HEIGHT = AndroidUtilities.dp(4f)
		private val CELL_HEIGHT_INCREASE = AndroidUtilities.dp(2f)
		private const val TIME_APPEAR_MS = 200
		private const val DOCUMENT_ATTACH_TYPE_NONE = 0
		private const val DOCUMENT_ATTACH_TYPE_DOCUMENT = 1
		private const val DOCUMENT_ATTACH_TYPE_GIF = 2
		private const val DOCUMENT_ATTACH_TYPE_AUDIO = 3
		private const val DOCUMENT_ATTACH_TYPE_VIDEO = 4
		private const val DOCUMENT_ATTACH_TYPE_MUSIC = 5
		private const val DOCUMENT_ATTACH_TYPE_STICKER = 6
		private const val DOCUMENT_ATTACH_TYPE_ROUND = 7
		private const val DOCUMENT_ATTACH_TYPE_WALLPAPER = 8
		private const val DOCUMENT_ATTACH_TYPE_THEME = 9
		private val radii = FloatArray(8)
		const val PROFILE = 5000
		const val LINK_IDS_START = 2000
		const val LINK_CAPTION_IDS_START = 3000
		const val BOT_BUTTONS_START = 1000
		const val POLL_BUTTONS_START = 500
		const val INSTANT_VIEW = 499
		const val SHARE = 498
		const val REPLY = 497
		const val COMMENT = 496
		const val POLL_HINT = 495
		const val FORWARD = 494
		const val TRANSCRIBE = 493

		@JvmStatic
		fun generateStaticLayout(text: CharSequence, paint: TextPaint?, maxWidth: Int, smallWidth: Int, linesCount: Int, maxLines: Int): StaticLayout {
			@Suppress("NAME_SHADOWING") var maxWidth = maxWidth
			val stringBuilder = SpannableStringBuilder(text)
			var addedChars = 0
			val layout = StaticLayout(text, paint, smallWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)

			for (a in 0 until linesCount) {
				if (layout.getLineLeft(a) != 0f || layout.isRtlCharAt(layout.getLineStart(a)) || layout.isRtlCharAt(layout.getLineEnd(a))) {
					maxWidth = smallWidth
				}

				var pos = layout.getLineEnd(a)

				if (pos == text.length) {
					break
				}

				pos--

				if (stringBuilder[pos + addedChars] == ' ') {
					stringBuilder.replace(pos + addedChars, pos + addedChars + 1, "\n")
				}
				else if (stringBuilder[pos + addedChars] != '\n') {
					stringBuilder.insert(pos + addedChars, "\n")
					addedChars++
				}

				if (a == layout.lineCount - 1 || a == maxLines - 1) {
					break
				}
			}

			return StaticLayoutEx.createStaticLayout(stringBuilder, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1f, AndroidUtilities.dp(1f).toFloat(), false, TextUtils.TruncateAt.END, maxWidth, maxLines, true)
		}

		private fun spanSupportsLongPress(span: CharacterStyle): Boolean {
			return span is URLSpanMono || span is URLSpan
		}

		@JvmStatic
		fun getMessageSize(imageW: Int, imageH: Int): Point {
			return getMessageSize(imageW, imageH, 0, 0)
		}

		private fun getMessageSize(imageW: Int, imageH: Int, photoWidth: Int, photoHeight: Int): Point {
			@Suppress("NAME_SHADOWING") var photoWidth = photoWidth
			@Suppress("NAME_SHADOWING") var photoHeight = photoHeight

			if (photoHeight == 0 || photoWidth == 0) {
				photoWidth = if (AndroidUtilities.isTablet()) {
					(AndroidUtilities.getMinTabletSide() * 0.7f).toInt()
				}
				else {
					if (imageW >= imageH) {
						min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(64f)
					}
					else {
						(min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f).toInt()
					}
				}

				photoHeight = photoWidth + AndroidUtilities.dp(100f)

				val photoSize = AndroidUtilities.getPhotoSize()

				if (photoWidth > photoSize) {
					photoWidth = photoSize
				}

				if (photoHeight > photoSize) {
					photoHeight = photoSize
				}
			}

			val scale = imageW.toFloat() / photoWidth.toFloat()
			var w = (imageW / scale).toInt()
			var h = (imageH / scale).toInt()

			if (w == 0) {
				w = AndroidUtilities.dp(150f)
			}

			if (h == 0) {
				h = AndroidUtilities.dp(150f)
			}

			if (h > photoHeight) {
				var scale2 = h.toFloat()
				h = photoHeight
				scale2 /= h.toFloat()
				w = (w / scale2).toInt()
			}
			else if (h < AndroidUtilities.dp(120f)) {
				h = AndroidUtilities.dp(120f)
				val hScale = imageH.toFloat() / h

				if (imageW / hScale < photoWidth) {
					w = (imageW / hScale).toInt()
				}
			}

			return Point(w.toFloat(), h.toFloat())
		}
	}
}
